"""Alembic environment (synchronous engine, psycopg 3)."""

from __future__ import annotations

from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from file_service.infrastructure.config import Settings
from file_service.infrastructure.db.models import Base

config = context.config
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

settings = Settings()

sync_database_url = settings.database_url.replace("+asyncpg", "+psycopg")
config.set_main_option("sqlalchemy.url", sync_database_url)
target_metadata = Base.metadata
_schema = settings.db_schema or None

def run_migrations_offline() -> None:
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
        version_table_schema=_schema,
        include_schemas=_schema is not None,
    )
    with context.begin_transaction():
        context.run_migrations()

def run_migrations_online() -> None:
    connect_args = (
        {"options": f"-c search_path={settings.db_schema}"}
        if settings.db_schema
        else {}
    )
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
        connect_args=connect_args,
    )
    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            compare_type=True,
            version_table_schema=_schema,
            include_schemas=_schema is not None,
        )
        with context.begin_transaction():
            context.run_migrations()

if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
