"""Run Alembic migrations programmatically (used on startup)."""

from __future__ import annotations

import asyncio
from pathlib import Path

from alembic import command
from alembic.config import Config

from appointment.infrastructure.logging import get_logger

logger = get_logger(__name__)

def _locate_ini(ini_path: str) -> Path:
    candidate = Path(ini_path)
    if candidate.is_file():
        return candidate.resolve()
    
    project_root = Path(__file__).resolve().parents[4]
    fallback = project_root / ini_path
    if fallback.is_file():
        return fallback
    raise FileNotFoundError(f"alembic config not found: {ini_path}")

def _upgrade_sync(database_url: str, ini_path: str) -> None:
    ini = _locate_ini(ini_path)
    config = Config(str(ini))
    config.set_main_option("script_location", str(ini.parent / "migrations"))
    config.set_main_option("sqlalchemy.url", database_url)
    command.upgrade(config, "head")

async def run_migrations(database_url: str, ini_path: str = "alembic.ini") -> None:
    logger.info("migrations_started")
    await asyncio.to_thread(_upgrade_sync, database_url, ini_path)
    logger.info("migrations_applied")
