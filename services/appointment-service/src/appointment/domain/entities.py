"""Appointment aggregate.

Pure Python: no FastAPI, no SQLAlchemy, no I/O. All invariants live here so they
can be unit tested without a database or an HTTP client.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

from appointment.domain.enums import TERMINAL_STATUSES, AppointmentStatus
from appointment.domain.errors import InvalidTransition, ValidationError

MIN_DURATION = timedelta(minutes=15)
MAX_DURATION = timedelta(hours=4)

MAX_REASON_LENGTH = 500

def ensure_utc(value: datetime) -> datetime:
    """Return a timezone-aware UTC datetime or raise a domain error."""
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValidationError(
            "datetime must include a timezone (ISO 8601, e.g. 2026-12-01T10:00:00Z)"
        )
    return value.astimezone(UTC)

def _validate_window(
    starts_at: datetime,
    ends_at: datetime,
) -> tuple[datetime, datetime]:
    starts_at = ensure_utc(starts_at)
    ends_at = ensure_utc(ends_at)

    if ends_at <= starts_at:
        raise ValidationError("ends_at must be after starts_at")

    duration = ends_at - starts_at
    if duration < MIN_DURATION:
        raise ValidationError(
            f"appointment must last at least {MIN_DURATION.total_seconds() // 60} minutes"
        )
    if duration > MAX_DURATION:
        raise ValidationError(
            f"appointment cannot last more than {MAX_DURATION.total_seconds() // 3600} hours"
        )

    return starts_at, ends_at

def _validate_reason(reason: str) -> str:
    reason = " ".join(reason.split())
    if len(reason) < 3:
        raise ValidationError("reason must contain at least 3 characters")
    if len(reason) > MAX_REASON_LENGTH:
        raise ValidationError(f"reason must not exceed {MAX_REASON_LENGTH} characters")
    return reason

@dataclass(slots=True)
class Appointment:
    """A booked consultation slot between a patient and a doctor."""

    id: UUID
    patient_id: UUID
    doctor_id: UUID
    starts_at: datetime
    ends_at: datetime
    status: AppointmentStatus
    reason: str
    created_at: datetime
    updated_at: datetime
    version: int = 1
    cancellation_reason: str | None = None

    @classmethod
    def schedule(
        cls,
        *,
        patient_id: UUID,
        doctor_id: UUID,
        starts_at: datetime,
        ends_at: datetime,
        reason: str,
        now: datetime,
    ) -> Appointment:
        """Create a new appointment in the ``requested`` status."""
        if patient_id == doctor_id:
            raise ValidationError("patient and doctor must be different users")
        now = ensure_utc(now)
        starts_at, ends_at = _validate_window(starts_at, ends_at)
        return cls(
            id=uuid4(),
            patient_id=patient_id,
            doctor_id=doctor_id,
            starts_at=starts_at,
            ends_at=ends_at,
            status=AppointmentStatus.REQUESTED,
            reason=_validate_reason(reason),
            created_at=now,
            updated_at=now,
        )

    

    def confirm(self, *, now: datetime) -> None:
        """Doctor accepts the requested appointment."""
        if self.status is not AppointmentStatus.REQUESTED:
            raise InvalidTransition(f"cannot confirm appointment in status '{self.status}'")
        self.status = AppointmentStatus.CONFIRMED
        self._touch(now)

    def reschedule(self, *, starts_at: datetime, ends_at: datetime, now: datetime) -> None:
        """Move an active appointment to a different time window."""
        if self.status not in (AppointmentStatus.REQUESTED, AppointmentStatus.CONFIRMED):
            raise InvalidTransition(f"cannot reschedule appointment in status '{self.status}'")
        starts_at, ends_at = _validate_window(starts_at, ends_at)
        self.starts_at = starts_at
        self.ends_at = ends_at
        self._touch(now)

    def cancel(self, *, reason: str, now: datetime) -> None:
        """Cancel a future appointment, keeping the reason for the audit trail."""
        if self.status in TERMINAL_STATUSES:
            raise InvalidTransition(f"cannot cancel appointment in status '{self.status}'")
        now = ensure_utc(now)
        if now >= self.starts_at:
            raise InvalidTransition("cannot cancel an appointment that has already started")
        self.status = AppointmentStatus.CANCELLED
        self.cancellation_reason = _validate_reason(reason)
        self._touch(now)

    def complete(self, *, now: datetime, force: bool = False) -> None:
        """Mark a confirmed appointment as completed after it started.

        Administrators may complete an appointment early (``force=True``),
        for example to close a visit that happened ahead of schedule.
        """
        if self.status is not AppointmentStatus.CONFIRMED:
            raise InvalidTransition(f"cannot complete appointment in status '{self.status}'")
        now = ensure_utc(now)
        if not force and now < self.starts_at:
            raise InvalidTransition("cannot complete an appointment before it starts")
        self.status = AppointmentStatus.COMPLETED
        self._touch(now)

    def mark_no_show(self, *, now: datetime) -> None:
        """Patient did not join the consultation."""
        if self.status not in (AppointmentStatus.REQUESTED, AppointmentStatus.CONFIRMED):
            raise InvalidTransition(
                f"cannot mark no-show for appointment in status '{self.status}'"
            )
        now = ensure_utc(now)
        if now < self.starts_at:
            raise InvalidTransition("cannot mark no-show before the appointment starts")
        self.status = AppointmentStatus.NO_SHOW
        self._touch(now)

    

    def _touch(self, now: datetime) -> None:
        self.updated_at = ensure_utc(now)
        self.version += 1

    def belongs_to(self, *, patient_id: UUID | None = None, doctor_id: UUID | None = None) -> bool:
        return (patient_id is not None and self.patient_id == patient_id) or (
            doctor_id is not None and self.doctor_id == doctor_id
        )
