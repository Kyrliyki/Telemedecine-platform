"""Перечисления домена."""

from __future__ import annotations

from enum import StrEnum


class CallStatus(StrEnum):
    """Жизненный цикл звонка."""

    CREATED = "created"
    ACTIVE = "active"
    ENDED = "ended"


class ActorRole(StrEnum):
    """Роли пользователей, приходящие из AuthService (JWT claims)."""

    DOCTOR = "doctor"
    PATIENT = "patient"
    ADMIN = "admin"

    @classmethod
    def from_claim(cls, value: str) -> ActorRole | None:
        try:
            return cls(value.strip().lower())
        except ValueError:
            return None
