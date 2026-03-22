import asyncio
import logging
import os
import signal
import uuid

from dotenv import load_dotenv

from .amqp import AmqpCtx
from .worker_runtime import WorkerRuntime

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


async def main() -> None:
    amqp_ctx = AmqpCtx()
    worker_runtime: WorkerRuntime | None = None

    print("🐰 RabbitMQ enabled - initializing worker runtime...")
    await amqp_ctx.init()

    worker_id = f"{os.getpid()}-{uuid.uuid4().hex[:8]}"
    worker_runtime = WorkerRuntime(amqp_ctx, worker_id)
    await worker_runtime.start()

    stop_event = asyncio.Event()

    def _shutdown(sig: signal.Signals) -> None:
        print(f"Received {sig.name}, shutting down...")
        stop_event.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, _shutdown, sig)

    await stop_event.wait()

    print("Shutting down services...")
    try:
        await amqp_ctx.close()
        print("✅ RabbitMQ connection closed")
    except Exception as e:
        logger.error("Error closing RabbitMQ: %s", e)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        logger.exception("Fatal error: %s", e)
        raise SystemExit(1)
