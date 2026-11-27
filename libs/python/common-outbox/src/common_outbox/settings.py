from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class OutboxSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="OUTBOX_", extra="ignore")

    batch_size: int = 100
    max_attempts: int = 5
    initial_backoff_seconds: float = 1.0
    max_backoff_seconds: float = 300.0
    processing_timeout_seconds: int = 300
    relay_interval_seconds: float = 1.0
    retention_seconds: int = 7 * 24 * 60 * 60

    def backoff_seconds(self, retry_count: int) -> float:
        return min(
            self.initial_backoff_seconds * (2 ** min(retry_count, 20)),
            self.max_backoff_seconds,
        )
