import base64
import json
import os
import ssl
from dataclasses import dataclass, field
from typing import Any, Optional


class _BytesEncoder(json.JSONEncoder):
    def default(self, obj: Any) -> Any:
        if isinstance(obj, (bytes, bytearray)):
            return base64.b64encode(obj).decode("ascii")
        return super().default(obj)

import aio_pika
from aio_pika import ExchangeType, Message, DeliveryMode

EXCHANGE_JOBS = "x.jobs"
EXCHANGE_RESULTS = "x.results"
EXCHANGE_CONTROL = "x.control"
EXCHANGE_DLX = "x.dlx"

ResultStatus = str  # "progress" | "succeeded" | "failed" | "cancelled"


@dataclass
class JobCommand:
    jobId: str
    type: str
    version: int
    payload: Any
    timeoutMs: int
    requestedAt: str
    attempt: int
    idempotencyKey: Optional[str] = None


class AmqpCtx:
    def __init__(self) -> None:
        self.connection: aio_pika.abc.AbstractConnection | None = None
        self.channel: aio_pika.abc.AbstractChannel | None = None
        self._exchanges: dict[str, aio_pika.abc.AbstractExchange] = {}

    async def init(self) -> None:
        url = os.getenv("AMQP_URL", "amqp://guest:guest@localhost:5672")
        ssl_context = _build_amqp_tls_context(url)
        connect_kwargs: dict[str, Any] = {}
        if ssl_context is not None:
            connect_kwargs["ssl"] = True
            connect_kwargs["ssl_context"] = ssl_context

        self.connection = await aio_pika.connect_robust(url, **connect_kwargs)
        self.channel = await self.connection.channel()

        # Vzor deklarace topic exchange:
        # https://docs.aio-pika.com/rabbitmq-tutorial/5-topics.html
        for name, ex_type in [
            (EXCHANGE_JOBS, ExchangeType.TOPIC),
            (EXCHANGE_RESULTS, ExchangeType.TOPIC),
            (EXCHANGE_CONTROL, ExchangeType.TOPIC),
            (EXCHANGE_DLX, ExchangeType.TOPIC),
        ]:
            self._exchanges[name] = await self.channel.declare_exchange(
                name, ex_type, durable=True
            )

    def exchange(self, name: str) -> aio_pika.abc.AbstractExchange:
        return self._exchanges[name]

    def publish_result(
        self, routing_key: str, payload: dict, correlation_id: Optional[str] = None
    ) -> None:
        """Fire-and-forget publikace do x.results (neblokuje, naplánuje coroutine)."""
        import asyncio

        asyncio.ensure_future(self._publish_result_async(routing_key, payload, correlation_id))

    async def _publish_result_async(
        self, routing_key: str, payload: dict, correlation_id: Optional[str] = None
    ) -> None:
        msg = Message(
            body=json.dumps(payload, cls=_BytesEncoder).encode(),
            # Příklad persistentního režimu doručení:
            # https://docs.aio-pika.com/rabbitmq-tutorial/5-topics.html
            delivery_mode=DeliveryMode.PERSISTENT,
            correlation_id=correlation_id,
        )
        await self._exchanges[EXCHANGE_RESULTS].publish(msg, routing_key=routing_key)

    async def close(self) -> None:
        if self.channel:
            try:
                await self.channel.close()
            except Exception:
                pass
        if self.connection:
            try:
                await self.connection.close()
            except Exception:
                pass


def parse_msg(msg: aio_pika.abc.AbstractIncomingMessage) -> dict:
    return json.loads(msg.body.decode())


def _env_bool(name: str, default: bool = False) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _build_amqp_tls_context(amqp_url: str) -> Optional[ssl.SSLContext]:
    """
    Volitelně vytvoří SSL kontext pro AMQP mTLS.

    mTLS se zapíná přes AMQP_MTLS_ENABLED=true. Kvůli konzistenci je vyžadováno:
      - AMQP_URL začínající na amqps://
      - AMQP_CLIENT_CERT_FILE
      - AMQP_CLIENT_KEY_FILE
    """
    mtls_enabled = _env_bool("AMQP_MTLS_ENABLED", default=False)
    if not mtls_enabled:
        return None

    if not amqp_url.startswith("amqps://"):
        raise RuntimeError("AMQP_MTLS_ENABLED=true requires AMQP_URL to use amqps://")

    cert_file = os.getenv("AMQP_CLIENT_CERT_FILE")
    key_file = os.getenv("AMQP_CLIENT_KEY_FILE")
    key_password = os.getenv("AMQP_CLIENT_KEY_PASSWORD")
    ca_file = os.getenv("AMQP_CA_FILE")
    tls_insecure = _env_bool("AMQP_TLS_INSECURE", default=False)

    if not cert_file or not key_file:
        raise RuntimeError(
            "AMQP mTLS requires AMQP_CLIENT_CERT_FILE and AMQP_CLIENT_KEY_FILE"
        )

    ctx = ssl.create_default_context(
        ssl.Purpose.SERVER_AUTH,
        cafile=ca_file if ca_file else None,
    )
    ctx.load_cert_chain(
        certfile=cert_file,
        keyfile=key_file,
        password=key_password if key_password else None,
    )

    if tls_insecure:
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE

    return ctx
