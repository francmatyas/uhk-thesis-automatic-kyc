import asyncio
import dataclasses
import logging
import os
from datetime import datetime, timezone
from typing import Any, Callable, Optional

import aio_pika

from .amqp import (
    EXCHANGE_CONTROL,
    EXCHANGE_DLX,
    EXCHANGE_JOBS,
    AmqpCtx,
    JobCommand,
    ResultStatus,
    parse_msg,
)
from .errors import WorkerError
from .document.czech_id import read_czech_id
from .document.passport import read_passport
from .biometrics.face_comparison import compare_faces
from .biometrics.liveness_check import check_liveness
from .aml.screener import AmlScreener

logger = logging.getLogger(__name__)


class WorkerRuntime:
    def __init__(self, amqp: AmqpCtx, worker_id: str) -> None:
        self.amqp = amqp
        self.worker_id = worker_id
        self.worker_type: str = os.getenv("WORKER_TYPE", "kyc_worker")
        self.prefetch: int = int(os.getenv("PREFETCH", "1"))

        self._q_jobs = f"q.worker.{self.worker_type}"
        self._q_cancel = f"q.cancel.{self.worker_id}"

        self._cancelled: set[str] = set()
        self._abort_events: dict[str, asyncio.Event] = {}

    # ------------------------------------------------------------------
    # Inicializace
    # ------------------------------------------------------------------

    async def start(self) -> None:
        ch = self.amqp.channel
        assert ch is not None

        await ch.set_qos(prefetch_count=self.prefetch)

        # Fronta úloh
        dlx = self.amqp.exchange(EXCHANGE_DLX)
        jobs_queue = await ch.declare_queue(
            self._q_jobs,
            durable=True,
            arguments={
                "x-dead-letter-exchange": EXCHANGE_DLX,
                "x-max-priority": 10,
            },
        )
        await jobs_queue.bind(self.amqp.exchange(EXCHANGE_JOBS), f"jobs.{self.worker_type}.#")

        # Fronta rušení (pro každý pracovní proces, exclusive/auto-delete)
        cancel_queue = await ch.declare_queue(
            self._q_cancel,
            durable=False,
            exclusive=True,
            auto_delete=True,
        )
        control_ex = self.amqp.exchange(EXCHANGE_CONTROL)
        await cancel_queue.bind(control_ex, "cancel.job.*")
        await cancel_queue.bind(control_ex, f"cancel.worker.{self.worker_id}")

        # Odběr zpráv o rušení
        await cancel_queue.consume(self._handle_cancel, no_ack=False)

        # Odběr úloh
        await jobs_queue.consume(self._handle_job_message, no_ack=False)

        logger.info(
            "WorkerRuntime started",
            extra={
                "workerId": self.worker_id,
                "workerType": self.worker_type,
                "qJobs": self._q_jobs,
                "qCancel": self._q_cancel,
            },
        )
        print(
            f"WorkerRuntime started | worker_id={self.worker_id} "
            f"type={self.worker_type} q_jobs={self._q_jobs}"
        )

    # ------------------------------------------------------------------
    # Obsluha rušení
    # ------------------------------------------------------------------

    async def _handle_cancel(self, msg: aio_pika.abc.AbstractIncomingMessage) -> None:
        async with msg.process():
            body = parse_msg(msg)
            job_id: Optional[str] = body.get("jobId")
            if job_id:
                self._cancelled.add(job_id)
                event = self._abort_events.get(job_id)
                if event:
                    event.set()

    # ------------------------------------------------------------------
    # Obsluha úloh
    # ------------------------------------------------------------------

    async def _handle_job_message(self, msg: aio_pika.abc.AbstractIncomingMessage) -> None:
        body = parse_msg(msg)
        cmd = JobCommand(
            jobId=body["jobId"],
            type=body["type"],
            version=body["version"],
            payload=body["payload"],
            timeoutMs=body["timeoutMs"],
            requestedAt=body["requestedAt"],
            attempt=body["attempt"],
            idempotencyKey=body.get("idempotencyKey"),
        )
        job_id = cmd.jobId
        print(cmd)

        reply_base: Optional[str] = None
        if msg.headers:
            reply_base = msg.headers.get("x-reply-to")

        if not reply_base:
            logger.error("missing x-reply-to header; nacking without requeue", extra={"jobId": job_id})
            await msg.reject(requeue=False)
            return

        started_at = datetime.now(timezone.utc).isoformat()
        abort_event = asyncio.Event()
        self._abort_events[job_id] = abort_event

        def is_cancelled() -> bool:
            return job_id in self._cancelled

        def send_result(status: ResultStatus, extra: dict) -> None:
            rk = f"{reply_base}.{job_id}.{status}"
            self.amqp.publish_result(
                rk,
                {
                    "workerId": self.worker_id,
                    "jobId": job_id,
                    "status": status,
                    "startedAt": started_at,
                    **extra,
                },
                correlation_id=job_id,
            )

        def on_progress(pct: float, msg_text: Optional[str] = None) -> None:
            pct_clamped = max(0, min(100, int(pct)))
            send_result("progress", {"progress": {"pct": pct_clamped, "msg": msg_text}})

        try:
            on_progress(0, "started")

            result = await self.do_work(
                cmd,
                on_progress=on_progress,
                is_cancelled=is_cancelled,
                abort_event=abort_event,
            )

            send_result("succeeded", {"finishedAt": datetime.now(timezone.utc).isoformat(), "result": result})
            await msg.ack()

        except asyncio.CancelledError:
            send_result(
                "cancelled",
                {
                    "finishedAt": datetime.now(timezone.utc).isoformat(),
                    "error": {"code": "CANCELLED", "message": "User requested"},
                },
            )
            await msg.ack()

        except Exception as e:
            if is_cancelled() or getattr(e, "name", None) == "AbortError" or str(e) == "Cancelled":
                send_result(
                    "cancelled",
                    {
                        "finishedAt": datetime.now(timezone.utc).isoformat(),
                        "error": {"code": "CANCELLED", "message": "User requested"},
                    },
                )
                await msg.ack()
            else:
                logger.exception("job failed", extra={"jobId": job_id})
                error_code = e.code if isinstance(e, WorkerError) else "WORKER_ERR"
                send_result(
                    "failed",
                    {
                        "finishedAt": datetime.now(timezone.utc).isoformat(),
                        "error": {"code": error_code},
                    },
                )
                await msg.reject(requeue=False)  # putuje do DLX

        finally:
            self._cancelled.discard(job_id)
            self._abort_events.pop(job_id, None)

    # ------------------------------------------------------------------
    #  Dispatch jednotlivých typů úloh
    # ------------------------------------------------------------------

    async def do_work(
        self,
        cmd: JobCommand,
        *,
        on_progress: Callable[[float, Optional[str]], None],
        is_cancelled: Callable[[], bool],
        abort_event: asyncio.Event,
    ) -> Any:
        job_type = cmd.type
        payload = cmd.payload or {}

        if job_type == "verify_czech_id":
            return await self._do_verify_czech_id(payload, on_progress, is_cancelled)
        elif job_type == "verify_passport":
            return await self._do_verify_passport(payload, on_progress, is_cancelled)
        elif job_type == "compare_faces":
            return await self._do_compare_faces(payload, on_progress, is_cancelled)
        elif job_type == "liveness_check":
            return await self._do_liveness_check(payload, on_progress, is_cancelled)
        elif job_type == "aml_screen":
            return await self._do_aml_screen(payload, on_progress, is_cancelled)
        else:
            raise WorkerError("UNKNOWN_JOB_TYPE")

    # ------------------------------------------------------------------
    # Implementace jednotlivých typů úloh
    # (běží v asyncio event loop, může být zrušena přes abort_event)
    # ------------------------------------------------------------------

    async def _do_verify_czech_id(
        self,
        payload: dict,
        on_progress: Callable[[float, Optional[str]], None],
        is_cancelled: Callable[[], bool],
    ) -> dict:
        back_path: Optional[str] = payload.get("backImagePath")
        front_path: Optional[str] = payload.get("frontImagePath")

        if not back_path:
            raise WorkerError("MISSING_BACK_IMAGE_PATH")

        on_progress(10, "reading document")
        if is_cancelled():
            raise asyncio.CancelledError

        loop = asyncio.get_running_loop()
        result = await loop.run_in_executor(
            None, lambda: read_czech_id(back_path, front_path)
        )

        on_progress(90, "done")
        if result is None:
            raise WorkerError("MRZ_NOT_FOUND")

        return dataclasses.asdict(result)

    async def _do_verify_passport(
        self,
        payload: dict,
        on_progress: Callable[[float, Optional[str]], None],
        is_cancelled: Callable[[], bool],
    ) -> dict:
        image_path: Optional[str] = payload.get("imagePath")

        if not image_path:
            raise WorkerError("MISSING_IMAGE_PATH")

        on_progress(10, "reading document")
        if is_cancelled():
            raise asyncio.CancelledError

        loop = asyncio.get_running_loop()
        result = await loop.run_in_executor(
            None, lambda: read_passport(image_path)
        )

        on_progress(90, "done")
        if result is None:
            raise WorkerError("MRZ_NOT_FOUND")

        return dataclasses.asdict(result)

    async def _do_compare_faces(
        self,
        payload: dict,
        on_progress: Callable[[float, Optional[str]], None],
        is_cancelled: Callable[[], bool],
    ) -> dict:
        document_path: Optional[str] = payload.get("documentPath")
        selfie_path: Optional[str] = payload.get("selfiePath")

        if not document_path:
            raise WorkerError("MISSING_DOCUMENT_PATH")
        if not selfie_path:
            raise WorkerError("MISSING_SELFIE_PATH")

        on_progress(10, "comparing faces")
        if is_cancelled():
            raise asyncio.CancelledError

        loop = asyncio.get_running_loop()
        result = await loop.run_in_executor(
            None, lambda: compare_faces(document_path, selfie_path)
        )

        on_progress(90, "done")
        return dataclasses.asdict(result)

    async def _do_liveness_check(
        self,
        payload: dict,
        on_progress: Callable[[float, Optional[str]], None],
        is_cancelled: Callable[[], bool],
    ) -> dict:
        image_paths: Optional[list] = payload.get("imagePaths")

        if not image_paths:
            raise WorkerError("MISSING_IMAGE_PATHS")

        on_progress(10, "analyzing liveness")
        if is_cancelled():
            raise asyncio.CancelledError

        loop = asyncio.get_running_loop()
        result = await loop.run_in_executor(
            None, lambda: check_liveness(image_paths)
        )

        on_progress(90, "done")
        return dataclasses.asdict(result)

    async def _do_aml_screen(
        self,
        payload: dict,
        on_progress: Callable[[float, Optional[str]], None],
        is_cancelled: Callable[[], bool],
    ) -> dict:
        full_name: Optional[str] = payload.get("fullName")
        dob: Optional[str] = payload.get("dob")

        if not full_name:
            raise WorkerError("MISSING_FULL_NAME")

        db_path = os.getenv("AML_DB_PATH", "data/aml.db")

        on_progress(10, "screening name")
        if is_cancelled():
            raise asyncio.CancelledError

        loop = asyncio.get_running_loop()

        def _run_scan() -> list:
            with AmlScreener(db_path) as screener:
                hits = screener.scan(full_name, dob)
            return [dataclasses.asdict(h) for h in hits]

        hits = await loop.run_in_executor(None, _run_scan)

        on_progress(90, "done")
        return {"hits": hits, "hitCount": len(hits)}
