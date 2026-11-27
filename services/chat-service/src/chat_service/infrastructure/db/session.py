from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from chat_service.config import get_settings

_engine = None
_session_factory = None


def init_engine() -> None:
    global _engine, _session_factory
    s = get_settings()
    connect_args = (
        {"server_settings": {"search_path": s.db_schema}} if s.db_schema else {}
    )
    _engine = create_async_engine(
        s.database_url,
        pool_size=20,
        max_overflow=10,
        future=True,
        connect_args=connect_args,
    )
    _session_factory = async_sessionmaker(_engine, expire_on_commit=False)


async def get_session() -> AsyncIterator[AsyncSession]:
    assert _session_factory is not None, "engine not initialized"
    async with _session_factory() as session:
        yield session