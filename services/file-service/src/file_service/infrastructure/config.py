"""Application settings loaded from environment variables / .env."""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    app_name: str = "file-service"
    app_env: str = "local"
    log_level: str = "INFO"
    log_json: bool = True
    api_prefix: str = "/api/v1"
    cors_origins: str = "*"

    database_url: str = "postgresql+asyncpg://file_service:file_service@localhost:5432/file_service"
    db_schema: str = ""
    db_echo: bool = False
    db_pool_size: int = 10

    s3_endpoint: str = "http://localhost:9000"
    s3_public_endpoint: str = "http://localhost:9000"
    s3_access_key: str = "minioadmin"
    s3_secret_key: str = "minioadmin"
    s3_bucket: str = "chat-files"
    s3_region: str = "us-east-1"
    s3_secure: bool = False
    s3_presign_ttl_seconds: int = 900

    rabbitmq_url: str = "amqp://file_service:file_service@localhost:5672/"
    rabbitmq_events_exchange: str = "file.events"
    rabbitmq_chat_events_exchange: str = "chat.events"
    rabbitmq_consumer_queue: str = "file-service.chat-events"
    rabbitmq_dead_letter_exchange: str = "file.dlx"

    redis_url: str = "redis://localhost:6379/0"
    file_cache_ttl_seconds: int = 60
    chat_access_cache_ttl_seconds: int = 60

    max_file_size_mb: int = 100
    allowed_content_types: str = ""

    jwt_algorithm: str = "HS256"
    jwt_public_key: str = ""
    jwt_jwks_url: str = ""
    jwt_secret: str = "dev-secret-change-me"
    jwt_issuer: str = ""
    jwt_audience: str = ""
    jwt_leeway_seconds: int = 30
    auth_redis_url: str = "redis://localhost:6379/0"
    auth_revocation_enabled: bool = True
    auth_disabled: bool = False
    dev_user_id: str = "00000000-0000-0000-0000-000000000001"
    dev_user_roles: str = "patient"

    chat_service_url: str = "http://chat-service:8085"
    internal_api_token: str = ""
    
    chat_access_allow_all: bool = False

    @property
    def max_file_size_bytes(self) -> int:
        return self.max_file_size_mb * 1024 * 1024

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def allowed_content_type_set(self) -> frozenset[str] | None:
        values = {value.strip().lower() for value in self.allowed_content_types.split(",") if value.strip()}
        return frozenset(values) if values else None

@lru_cache
def get_settings() -> Settings:
    return Settings()
