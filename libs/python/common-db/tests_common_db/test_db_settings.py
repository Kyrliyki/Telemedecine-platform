from common_db import DatabaseSettings


def test_database_settings_read_service_env(monkeypatch) -> None:
    monkeypatch.setenv("DB_URL", "postgresql+asyncpg://u:p@db:5432/app")
    monkeypatch.setenv("DB_SCHEMA", "appointment")
    monkeypatch.setenv("DB_POOL_SIZE", "7")
    monkeypatch.setenv("DB_MAX_OVERFLOW", "11")

    settings = DatabaseSettings()

    assert settings.url == "postgresql+asyncpg://u:p@db:5432/app"
    assert settings.schema_name == "appointment"
    assert settings.pool_size == 7
    assert settings.max_overflow == 11


def test_database_settings_accept_legacy_names(monkeypatch) -> None:
    monkeypatch.delenv("DB_URL", raising=False)
    monkeypatch.delenv("DB_SCHEMA", raising=False)
    monkeypatch.setenv("DATABASE_URL", "postgresql+asyncpg://u:p@db:5432/app")
    monkeypatch.setenv("DATABASE_SCHEMA", "chat")

    settings = DatabaseSettings()

    assert settings.url.endswith("/app")
    assert settings.schema_name == "chat"
