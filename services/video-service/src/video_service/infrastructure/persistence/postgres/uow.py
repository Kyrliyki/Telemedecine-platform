"""SQLAlchemy Unit of Work."""

from __future__ import annotations

from types import TracebackType

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from video_service.domain.errors import CallAlreadyExistsError
from video_service.infrastructure.persistence.postgres.repository import (
    SqlAlchemyCallRepository,
)


class SqlAlchemyUnitOfWork:
    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._session_factory = session_factory
        self._session: AsyncSession | None = None
        self.calls: SqlAlchemyCallRepository

    async def __aenter__(self) -> SqlAlchemyUnitOfWork:
        self._session = self._session_factory()
        self.calls = SqlAlchemyCallRepository(self._session)
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None:
        assert self._session is not None  # noqa: S101
        try:
            if exc_type is not None:
                await self._session.rollback()
        finally:
            await self._session.close()
            self._session = None

    async def commit(self) -> None:
        assert self._session is not None  # noqa: S101
        try:
            await self._session.commit()
        except IntegrityError as exc:
            await self._session.rollback()
            raise CallAlreadyExistsError(
                "Звонок с такими уникальными атрибутами уже существует",
            ) from exc
