"""Unit of work: one SQLAlchemy session and transaction per use case."""

from __future__ import annotations

from typing import Self

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from appointment.infrastructure.db.outbox import SqlAlchemyOutboxRepository
from appointment.infrastructure.db.repository import SqlAlchemyAppointmentRepository


class SqlAlchemyUnitOfWork:
    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._session_factory = session_factory
        self._session: AsyncSession | None = None
        self.appointments: SqlAlchemyAppointmentRepository
        self.outbox: SqlAlchemyOutboxRepository

    async def __aenter__(self) -> Self:
        self._session = self._session_factory()
        self.appointments = SqlAlchemyAppointmentRepository(self._session)
        self.outbox = SqlAlchemyOutboxRepository(self._session)
        return self

    async def __aexit__(self, exc_type: object, exc: object, tb: object) -> None:
        if self._session is None:
            return
        try:
            if exc_type is not None:
                await self._session.rollback()
        finally:
            await self._session.close()
            self._session = None

    async def commit(self) -> None:
        if self._session is None:
            raise RuntimeError("unit of work is not entered")
        await self._session.commit()

    async def rollback(self) -> None:
        if self._session is None:
            raise RuntimeError("unit of work is not entered")
        await self._session.rollback()
