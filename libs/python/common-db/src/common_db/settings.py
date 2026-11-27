from __future__ import annotations

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class DatabaseSettings(BaseSettings):
    model_config = SettingsConfigDict(extra="ignore")

    url: str = Field(
        default="postgresql+asyncpg://telemedicine:telemedicine@localhost:55432/telemedicine",
        validation_alias=AliasChoices("DB_URL", "DATABASE_URL"),
    )
    schema_name: str = Field(
        default="public",
        validation_alias=AliasChoices("DB_SCHEMA", "DATABASE_SCHEMA"),
    )
    pool_size: int = Field(
        default=10,
        validation_alias=AliasChoices("DB_POOL_SIZE", "POOL_SIZE"),
    )
    max_overflow: int = Field(
        default=20,
        validation_alias=AliasChoices("DB_MAX_OVERFLOW", "MAX_OVERFLOW"),
    )
    echo: bool = Field(
        default=False,
        validation_alias=AliasChoices("DB_ECHO", "ECHO"),
    )
