"""Prometheus metrics for the transactional outbox relay."""

from __future__ import annotations

from prometheus_client import Counter

OUTBOX_PUBLISHED = Counter(
    "appointment_outbox_published_total",
    "Domain events successfully published to RabbitMQ",
    labelnames=("event_type",),
)

OUTBOX_FAILURES = Counter(
    "appointment_outbox_publish_failures_total",
    "Failed attempts to publish a domain event",
)
