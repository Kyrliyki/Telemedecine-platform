"""Internal endpoints consumed by Java services (EMR, Gateway admin, etc.).

All routes require ``X-Internal-Token`` unless the service is configured
without a token, in which case the guard fails closed with 503.
"""

from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from appointment.presentation.deps import ContainerDep
from appointment.presentation.internal_auth import internal_token_guard

router = APIRouter(
    prefix="/internal",
    tags=["internal"],
    dependencies=[Depends(internal_token_guard())],
)


async def _load(container: ContainerDep, appointment_id: UUID):
    async with container.uow_factory() as uow:
        appointment = await uow.appointments.get(appointment_id)
    if appointment is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Appointment not found",
        )
    return appointment


@router.get(
    "/appointments/{appointment_id}",
    summary="Internal appointment lookup (Java services)",
)
async def get_internal_appointment(
    appointment_id: UUID,
    container: ContainerDep,
):
    appointment = await _load(container, appointment_id)
    return {
        "appointmentId": str(appointment.id),
        "patientId": str(appointment.patient_id),
        "doctorId": str(appointment.doctor_id),
        "status": appointment.status.value.upper(),
        "startsAt": appointment.starts_at.isoformat(),
        "endsAt": appointment.ends_at.isoformat(),
    }


@router.get(
    "/appointments/{appointment_id}/status",
    summary="Internal appointment status (Java services)",
)
async def get_internal_appointment_status(
    appointment_id: UUID,
    container: ContainerDep,
):
    appointment = await _load(container, appointment_id)
    return {"status": appointment.status.value.upper()}
