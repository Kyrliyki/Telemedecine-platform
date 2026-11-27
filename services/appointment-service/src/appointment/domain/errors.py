"""Domain-level exceptions.

Every exception carries a stable machine-readable ``code`` and the HTTP status the
presentation layer should map it to. Keeping this next to the error avoids a long
``if isinstance(...)`` chain in the API layer.
"""

from __future__ import annotations


class DomainError(Exception):
    """Base class for all expected business errors."""

    code: str = "domain_error"
    status_code: int = 400

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


class ValidationError(DomainError):
    """Input does not satisfy a business invariant."""

    code = "validation_error"
    status_code = 422


class InvalidTransition(DomainError):
    """Requested status transition is not allowed."""

    code = "invalid_transition"
    status_code = 409


class OverlappingAppointment(DomainError):
    """Patient or doctor already has an appointment in this time window."""

    code = "appointment_overlap"
    status_code = 409


class PermissionDenied(DomainError):
    """Authenticated user is not allowed to perform this action."""

    code = "permission_denied"
    status_code = 403


class AppointmentNotFound(DomainError):
    """Appointment does not exist."""

    code = "appointment_not_found"
    status_code = 404


class ConcurrencyConflict(DomainError):
    """The appointment was modified by another request (optimistic lock)."""

    code = "concurrency_conflict"
    status_code = 409


class SlotUnavailable(DomainError):
    """ScheduleService rejected the requested slot."""

    code = "slot_unavailable"
    status_code = 409


class ScheduleUnavailable(DomainError):
    """ScheduleService is not reachable or returned an unexpected error."""

    code = "schedule_unavailable"
    status_code = 503


class InvalidParticipant(DomainError):
    """A patient/doctor referenced by the appointment is missing or invalid."""

    code = "invalid_participant"
    status_code = 422


class UserDirectoryUnavailable(DomainError):
    """UserService is not reachable, so participants cannot be validated."""

    code = "user_directory_unavailable"
    status_code = 503


class AuthenticationError(DomainError):
    """JWT is missing, malformed or expired."""

    code = "unauthorized"
    status_code = 401


class AuthBackendUnavailable(DomainError):
    """Token revocation store is unavailable."""

    code = "auth_backend_unavailable"
    status_code = 503
