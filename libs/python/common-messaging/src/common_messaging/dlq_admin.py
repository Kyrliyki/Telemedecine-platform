"""Reusable admin API for service-owned RabbitMQ dead-letter queues.

Provides ``/internal/dlq``, ``/internal/dlq/{key}/replay`` and
``/internal/dlq/{key}/purge`` in the shape expected by the Java API Gateway.
"""

from __future__ import annotations

import hmac
from collections.abc import Iterable
from dataclasses import dataclass

import aio_pika
from aio_pika import ExchangeType, Message
from fastapi import APIRouter, Header, HTTPException, Query, status


@dataclass(frozen=True, slots=True)
class DlqQueueSpec:
    key: str
    queue: str
    exchange: str
    description: str

def build_dlq_router(
    *,
    rabbitmq_url: str,
    internal_token: str,
    specs: Iterable[DlqQueueSpec],
) -> APIRouter:
    queue_specs = {spec.key: spec for spec in specs}
    router = APIRouter(prefix="/internal/dlq", tags=["internal"])

    async def guard(x_internal_token: str | None = Header(default=None)) -> None:
        if not internal_token:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Internal API is not configured",
            )
        if not x_internal_token or not hmac.compare_digest(
            x_internal_token.encode("utf-8"), internal_token.encode("utf-8")
        ):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid internal service token",
            )

    def spec_for(key: str) -> DlqQueueSpec:
        spec = queue_specs.get(key)
        if spec is None:
            raise HTTPException(status_code=404, detail="Unknown DLQ key")
        return spec

    async def _connect():
        if not rabbitmq_url:
            raise HTTPException(status_code=503, detail="RabbitMQ is not configured")
        return await aio_pika.connect_robust(rabbitmq_url, timeout=5)

    @router.get("", dependencies=[])
    async def list_queues(
        x_internal_token: str | None = Header(default=None),
    ) -> list[dict]:
        await guard(x_internal_token)
        connection = await _connect()
        result: list[dict] = []
        try:
            channel = await connection.channel()
            for spec in queue_specs.values():
                available = True
                messages = 0
                error: str | None = None
                try:
                    declaration = await channel.declare_queue(spec.queue, passive=True, durable=True)
                    messages = _message_count(declaration)
                except Exception as exc:  
                    available = False
                    error = str(exc)
                result.append(
                    {
                        "key": spec.key,
                        "queue": spec.queue,
                        "exchange": spec.exchange,
                        "description": spec.description,
                        "messages": messages,
                        "available": available,
                        "error": error,
                    }
                )
        finally:
            await connection.close()
        return result

    @router.post("/{key}/replay")
    async def replay(
        key: str,
        limit: int = Query(default=100, ge=1, le=1000),
        dry_run: bool = Query(default=False, alias="dryRun"),
        x_internal_token: str | None = Header(default=None),
    ) -> dict:
        await guard(x_internal_token)
        spec = spec_for(key)
        connection = await _connect()
        try:
            channel = await connection.channel()
            try:
                declaration = await channel.declare_queue(spec.queue, passive=True, durable=True)
            except Exception as exc:
                raise HTTPException(status_code=503, detail=f"DLQ is unavailable: {exc}") from exc

            candidates = _message_count(declaration)
            if dry_run:
                return {
                    "queue": spec.queue,
                    "replayed": 0,
                    "failed": 0,
                    "dryRun": True,
                    "candidates": candidates,
                }

            replayed = 0
            failed = 0
            for _ in range(min(limit, candidates)):
                message = await declaration.get(no_ack=False)
                if message is None:
                    break
                target = _target(message.headers, spec)
                if target is None:
                    failed += 1
                    await message.nack(requeue=True)
                    break
                exchange_name, routing_key = target
                if exchange_name:
                    exchange = await channel.declare_exchange(
                        exchange_name, ExchangeType.TOPIC, passive=True
                    )
                else:
                    exchange = channel.default_exchange
                headers = dict(message.headers or {})
                headers.pop("x-death", None)
                await exchange.publish(
                    Message(
                        body=message.body,
                        headers=headers,
                        content_type=message.content_type,
                        message_id=message.message_id,
                        correlation_id=message.correlation_id,
                    ),
                    routing_key=routing_key,
                )
                await message.ack()
                replayed += 1

            return {
                "queue": spec.queue,
                "replayed": replayed,
                "failed": failed,
                "dryRun": False,
                "candidates": candidates,
            }
        finally:
            await connection.close()

    @router.post("/{key}/purge")
    async def purge(
        key: str,
        x_internal_token: str | None = Header(default=None),
    ) -> dict:
        await guard(x_internal_token)
        spec = spec_for(key)
        connection = await _connect()
        try:
            channel = await connection.channel()
            try:
                declaration = await channel.declare_queue(spec.queue, passive=True, durable=True)
            except Exception as exc:
                raise HTTPException(status_code=503, detail=f"DLQ is unavailable: {exc}") from exc
            purged = await declaration.purge()
            return {"queue": spec.queue, "purged": purged}
        finally:
            await connection.close()

    return router

def _message_count(queue: object) -> int:
    result = getattr(queue, 'declaration_result', None)
    return int(getattr(result, 'message_count', 0) or 0)

def _target(headers: dict | None, spec: DlqQueueSpec) -> tuple[str, str] | None:
    deaths = (headers or {}).get("x-death")
    if isinstance(deaths, list) and deaths:
        death = deaths[0]
        if isinstance(death, dict):
            exchange = str(death.get("exchange") or "")
            routing_keys = death.get("routing-keys") or death.get("routing_key")
            if isinstance(routing_keys, list) and routing_keys:
                return exchange, str(routing_keys[0])
            if isinstance(routing_keys, str) and routing_keys:
                return exchange, routing_keys
    return None

