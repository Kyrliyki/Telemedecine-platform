"""Prometheus metrics for the transactional outbox relay."""

from __future__ import annotations

from prometheus_client import Counter, Gauge

OUTBOX_PENDING = Gauge(
    "outbox_pending_total",
    "Outbox events claimed in the last relay batch",
    ["service"],
)
OUTBOX_SENT = Counter(
    "outbox_sent_total",
    "Outbox events published",
    ["service"],
)
OUTBOX_FAILED = Counter(
    "outbox_failed_total",
    "Outbox events failed to publish (will retry)",
    ["service"],
)
OUTBOX_DEAD = Counter(
    "outbox_dead_total",
    "Outbox events moved to DEAD after max attempts",
    ["service"],
)


def record_pending(service: str, count: int) -> None:
    OUTBOX_PENDING.labels(service).set(count)


def record_sent(service: str, count: int) -> None:
    if count > 0:
        OUTBOX_SENT.labels(service).inc(count)


def record_failed(service: str, count: int) -> None:
    if count > 0:
        OUTBOX_FAILED.labels(service).inc(count)


def record_dead(service: str, count: int) -> None:
    if count > 0:
        OUTBOX_DEAD.labels(service).inc(count)
