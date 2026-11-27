"""Authorization rules for the appointment aggregate."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from uuid import UUID

from appointment.domain.entities import Appointment, ensure_utc
from appointment.domain.enums import Role
from appointment.domain.errors import PermissionDenied, ValidationError

PATIENT_MIN_BOOKING_LEAD = timedelta(hours=1)

@dataclass(frozen=True, slots=True)
class Actor:
    """Authenticated caller extracted from the JWT."""

    id: UUID
    roles: frozenset[Role]

    def __post_init__(self) -> None:
        object.__setattr__(self, "roles", frozenset(self.roles))
        if not self.roles:
            raise ValueError("actor must have at least one role")

    @property
    def is_admin(self) -> bool:
        return Role.ADMIN in self.roles

    @property
    def is_doctor(self) -> bool:
        return Role.DOCTOR in self.roles

    @property
    def is_patient(self) -> bool:
        return Role.PATIENT in self.roles

    def has_role(self, role: Role) -> bool:
        return role in self.roles

    def booking_mode(self, *, patient_id: UUID, doctor_id: UUID) -> Role:
        """Pick the mode in which the caller creates an appointment.

        A dual-role user acts as a patient when booking for themselves and as a
        doctor when creating an appointment for a patient.  Appointments where
        the patient and the doctor are the same user are never allowed.
        """
        if patient_id == doctor_id:
            raise ValidationError("patient and doctor must be different users")
        if self.is_admin:
            return Role.ADMIN
        if patient_id == self.id and self.is_patient:
            return Role.PATIENT
        if doctor_id == self.id and self.is_doctor:
            return Role.DOCTOR
        raise PermissionDenied("you are not allowed to create this appointment")

    def mode_for(self, appointment: Appointment) -> Role:
        """Pick the mode in which the caller manages an existing appointment."""
        if not self.is_admin and appointment.patient_id == appointment.doctor_id:
            raise PermissionDenied("appointments with yourself are not allowed")
        if self.is_admin:
            return Role.ADMIN
        if appointment.patient_id == self.id and self.is_patient:
            return Role.PATIENT
        if appointment.doctor_id == self.id and self.is_doctor:
            return Role.DOCTOR
        raise PermissionDenied("you are not allowed to manage this appointment")

    def ensure_can_book(self, *, patient_id: UUID, doctor_id: UUID) -> Role:
        """Backward-compatible helper: validate and return the booking mode."""
        return self.booking_mode(patient_id=patient_id, doctor_id=doctor_id)

    def ensure_can_schedule(
        self, *, starts_at: datetime, now: datetime, mode: Role
    ) -> None:
        """Role-based lead time for creating and rescheduling appointments.

        - patient: at least one hour in the future;
        - doctor: not in the past;
        - admin: no time restrictions (e.g. retroactive data entry).
        """
        starts_at = ensure_utc(starts_at)
        now = ensure_utc(now)

        if mode is Role.ADMIN:
            return
        if mode is Role.PATIENT:
            if starts_at < now + PATIENT_MIN_BOOKING_LEAD:
                raise ValidationError("appointment must start at least 1 hour from now")
            return
        if mode is Role.DOCTOR:
            if starts_at < now:
                raise ValidationError("appointment cannot start in the past")
            return
        raise ValidationError("unsupported booking mode")

    def ensure_can_view(self, appointment: Appointment) -> None:
        if not self.is_admin and appointment.patient_id == appointment.doctor_id:
            raise PermissionDenied("appointments with yourself are not allowed")
        if self.is_admin:
            return
        if self.is_patient and appointment.patient_id == self.id:
            return
        if self.is_doctor and appointment.doctor_id == self.id:
            return
        raise PermissionDenied("appointment belongs to another user")

    def ensure_can_manage(self, appointment: Appointment) -> None:
        """Patient owner, doctor owner or admin: cancel/reschedule/get."""
        if not self.is_admin and appointment.patient_id == appointment.doctor_id:
            raise PermissionDenied("appointments with yourself are not allowed")
        if self.is_admin:
            return
        if self.is_patient and appointment.patient_id == self.id:
            return
        if self.is_doctor and appointment.doctor_id == self.id:
            return
        raise PermissionDenied("you are not allowed to manage this appointment")

    def ensure_can_manage_clinically(self, appointment: Appointment) -> None:
        """Only the treating doctor (or admin) can confirm/complete/no-show."""
        if not self.is_admin and appointment.patient_id == appointment.doctor_id:
            raise PermissionDenied("appointments with yourself are not allowed")
        if self.is_admin:
            return
        if self.is_doctor and appointment.doctor_id == self.id:
            return
        raise PermissionDenied("only the treating doctor or an admin can perform this action")

    def ensure_can_filter(self, *, patient_id: UUID | None, doctor_id: UUID | None) -> None:
        if self.is_admin:
            return
        if patient_id is not None and (not self.is_patient or patient_id != self.id):
            raise PermissionDenied("you cannot view appointments of another patient")
        if doctor_id is not None and (not self.is_doctor or doctor_id != self.id):
            raise PermissionDenied("you cannot view appointments of another doctor")
