"""Domain enumerations."""

from enum import StrEnum


class Role(StrEnum):
    """Roles issued by AuthService inside the JWT."""

    PATIENT = "patient"
    DOCTOR = "doctor"
    ADMIN = "admin"

class AppointmentStatus(StrEnum):
    """Lifecycle of an appointment."""

    REQUESTED = "requested"
    CONFIRMED = "confirmed"
    CANCELLED = "cancelled"
    COMPLETED = "completed"
    NO_SHOW = "no_show"

TERMINAL_STATUSES: frozenset[AppointmentStatus] = frozenset(
    {
        AppointmentStatus.CANCELLED,
        AppointmentStatus.COMPLETED,
        AppointmentStatus.NO_SHOW,
    }
)

BLOCKING_STATUSES: frozenset[AppointmentStatus] = frozenset(
    {
        AppointmentStatus.REQUESTED,
        AppointmentStatus.CONFIRMED,
    }
)
