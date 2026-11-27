"""RabbitMQ integration-events support for Python services."""

from .consumer import RabbitEventConsumer
from .dlq import (
    DlqAdminService,
    DlqNotFoundError,
    DlqQueueCatalog,
    DlqQueueDefinition,
    DlqUnavailableError,
)
from .dlq_admin import DlqQueueSpec, build_dlq_router
from .idempotency import (
    InMemoryProcessedEventStore,
    PostgresProcessedEventStore,
    ProcessedEventStore,
    handle_once,
    idempotent,
)
from .publisher import EventPublisher, RabbitEventPublisher
from .router import EventRouter
from .settings import RabbitMQSettings

__all__ = [
    "DlqAdminService",
    "DlqNotFoundError",
    "DlqQueueCatalog",
    "DlqQueueDefinition",
    "DlqQueueSpec",
    "DlqUnavailableError",
    "EventPublisher",
    "EventRouter",
    "InMemoryProcessedEventStore",
    "PostgresProcessedEventStore",
    "ProcessedEventStore",
    "RabbitEventConsumer",
    "RabbitEventPublisher",
    "RabbitMQSettings",
    "build_dlq_router",
    "handle_once",
    "idempotent",
]
