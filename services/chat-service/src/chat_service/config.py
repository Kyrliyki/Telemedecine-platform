from functools import lru_cache
from uuid import UUID

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+asyncpg://chat:chat@localhost:5432/chat"
    db_schema: str = ""
    redis_url: str = "redis://localhost:6379/0"

    rabbitmq_url: str = ""
    rabbitmq_events_exchange: str = "chat.events"

    jwt_public_key: str = ""
    jwt_jwks_url: str = ""
    jwt_algorithm: str = "RS256"
    jwt_secret: str = ""
    
    jwt_issuer: str = ""
    jwt_audience: str = ""
    auth_redis_url: str = "redis://localhost:6379/0"
    auth_revocation_enabled: bool = True

    
    internal_service_token: str = ""

    
    
    
    auth_disabled: bool = False
    test_user_id: UUID = UUID("00000000-0000-0000-0000-000000000001")

    request_service_url: str | None = None
    appointment_service_url: str | None = None
    request_gateway_enabled: bool = False

    
    
    cors_origins: str = "*"

    @property
    def cors_origins_list(self) -> list[str]:
        if self.cors_origins.strip() == "*":
            return ["*"]
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    log_level: str = "INFO"
    rate_limit_per_min: int = 30
    max_message_length: int = 4096
    edit_window_min: int = 15
    ws_ping_interval: int = 30

    history_default_limit: int = 50
    history_max_limit: int = 200

@lru_cache
def get_settings() -> Settings:
    return Settings()