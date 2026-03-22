import json
import os
from dataclasses import dataclass, field
from typing import Any, Optional

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
        self.connection = await aio_pika.connect_robust(url)
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
            body=json.dumps(payload).encode(),
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
