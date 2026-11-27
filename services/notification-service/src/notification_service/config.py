"""NotificationService settings (12-factor, environment variables)."""

from __future__ import annotations

from functools import lru_cache

from pydantic import AliasChoices, Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    
    service_name: str = "notification-service"
    environment: str = "local"
    log_level: str = "INFO"
    api_prefix: str = "/api/v1"
    cors_origins: str = "*"
    dev_endpoints_enabled: bool = False

    
    database_url: str = Field(
        default="postgresql+asyncpg://telemedicine:telemedicine@localhost:55432/notification",
        validation_alias=AliasChoices("DB_URL", "DATABASE_URL", "NOTIFICATION_DB_URL"),
    )
    db_schema: str = Field(
        default="public",
        validation_alias=AliasChoices("DB_SCHEMA", "DATABASE_SCHEMA"),
    )
    db_echo: bool = Field(default=False, validation_alias=AliasChoices("DB_ECHO"))
    db_pool_size: int = Field(default=10, validation_alias=AliasChoices("DB_POOL_SIZE"))
    db_max_overflow: int = Field(
        default=20,
        validation_alias=AliasChoices("DB_MAX_OVERFLOW"),
    )

    
    rabbitmq_url: str = Field(
        default="amqp://telemedicine:telemedicine@localhost:5672/",
        validation_alias=AliasChoices("RABBITMQ_URL", "RABBITMQ_DSN"),
    )
    rabbitmq_auth_exchange: str = "auth.events"
    rabbitmq_user_exchange: str = "user.events"
    rabbitmq_appointment_exchange: str = "appointment.events"
    rabbitmq_video_exchange: str = "video.events"
    rabbitmq_notification_exchange: str = "notification.events"
    rabbitmq_auth_queue: str = "notification-service.auth.events"
    rabbitmq_user_queue: str = "notification-service.user.events"
    rabbitmq_appointment_queue: str = "notification-service.appointment.events"
    rabbitmq_video_queue: str = "notification-service.video.events"
    rabbitmq_prefetch_count: int = 10

    
    redis_url: str = "redis://localhost:6379/5"
    in_app_enabled: bool = True

    
    
    email_enabled: bool = False
    smtp_host: str = "smtp.gmail.com"
    smtp_port: int = 587
    smtp_username: str = ""
    smtp_password: str = ""
    
    smtp_from: str = ""
    smtp_from_name: str = "Telemed"
    smtp_starttls: bool = True
    smtp_use_tls: bool = False
    smtp_timeout_seconds: int = 15

    
    frontend_url: str = "http://localhost:3000"
    auth_public_url: str = "http://localhost:8080"

    
    jwt_issuer: str = "auth-service"
    jwt_audience: str = "telemedicine"
    jwt_algorithm: str = "HS256"
    
    jwt_secret: str = "dev-secret-change-me-0123456789abcdef"
    jwks_url: str = ""
    jwt_leeway_seconds: int = 30
    auth_redis_url: str = "redis://localhost:6379/0"
    auth_revocation_enabled: bool = True
    internal_service_token: str = ""

    
    dispatcher_interval_seconds: float = 2.0
    dispatcher_batch_size: int = 50
    dispatcher_max_attempts: int = 5
    dispatcher_retry_backoff_seconds: float = 5.0
    dispatcher_processing_timeout_seconds: int = 300

    @property
    def cors_origin_list(self) -> list[str]:
        if self.cors_origins.strip() == "*":
            return ["*"]
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def is_dev(self) -> bool:
        return self.environment.lower() in {"local", "dev", "docker", "development", "test"}

    @property
    def sender_address(self) -> str:
        """Gmail requires From to be the authenticated account (or an alias)."""
        return (self.smtp_from or self.smtp_username).strip()

    @model_validator(mode="after")
    def _validate_email_settings(self) -> Settings:
        if self.email_enabled:
            if not self.smtp_username.strip() or not self.smtp_password.strip():
                raise ValueError(
                    "SMTP_USERNAME and SMTP_PASSWORD must be set when EMAIL_ENABLED=true",
                )
            if not self.sender_address:
                raise ValueError("SMTP_FROM or SMTP_USERNAME must be set")
        return self

@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
