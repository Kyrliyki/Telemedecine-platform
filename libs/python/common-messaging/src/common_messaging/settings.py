from __future__ import annotations

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class RabbitMQSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="RABBITMQ_",
        extra="ignore",
    )

    url: str = Field(
        default="amqp://telemedicine:telemedicine@localhost:5672/",
        validation_alias="RABBITMQ_URL",
    )
    exchange: str = "events"
    dlx: str | None = None
    publisher_confirms: bool = True
    confirm_timeout: float = 5.0
    prefetch_count: int = 10
    queue: str | None = None
    routing_key: str = "#"
    mandatory: bool = True
