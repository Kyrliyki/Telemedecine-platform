"""Appointment REST API (versioned under ``/api/v1``)."""

from __future__ import annotations

from datetime import datetime
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Query, status

from appointment.application.dto import ListAppointmentsQuery
from appointment.domain.entities import Appointment
from appointment.domain.enums import AppointmentStatus
from appointment.domain.errors import ValidationError
from appointment.presentation.deps import ActorDep, ContainerDep
from appointment.presentation.schemas import (
    AppointmentCancelRequest,
    AppointmentCreateRequest,
    AppointmentListResponse,
    AppointmentRescheduleRequest,
    AppointmentResponse,
    ErrorEnvelope,
)

router = APIRouter(prefix="/appointments", tags=["appointments"])

_ERROR_RESPONSES: dict[int | str, dict[str, object]] = {
    401: {"model": ErrorEnvelope, "description": "Missing or invalid access token"},
    403: {"model": ErrorEnvelope, "description": "Insufficient permissions"},
    404: {"model": ErrorEnvelope, "description": "Appointment not found"},
    409: {"model": ErrorEnvelope, "description": "Conflict (transition or overlap)"},
    422: {"model": ErrorEnvelope, "description": "Validation error"},
}


def _to_response(appointment: Appointment) -> AppointmentResponse:
    return AppointmentResponse.from_entity(appointment)


@router.post(
    "",
    status_code=status.HTTP_201_CREATED,
    response_model=AppointmentResponse,
    summary="Book a consultation",
    responses=_ERROR_RESPONSES,
)
async def create_appointment(
    body: AppointmentCreateRequest,
    actor: ActorDep,
    container: ContainerDep,
) -> AppointmentResponse:
    """Patients book at least 1 hour ahead, doctors — not in the past, admins — anytime."""
    if body.patient_id is None:
        if not actor.is_patient:
            raise ValidationError("patient_id is required for doctors and admins")
        patient_id = actor.id
    else:
        patient_id = body.patient_id
    if patient_id == body.doctor_id:
        raise ValidationError("you cannot create an appointment with yourself")

    appointment = await container.create_appointment(
        actor=actor,
        patient_id=patient_id,
        doctor_id=body.doctor_id,
        starts_at=body.starts_at,
        ends_at=body.ends_at,
        reason=body.reason,
    )
    return _to_response(appointment)


@router.get(
    "",
    response_model=AppointmentListResponse,
    summary="List appointments",
    responses=_ERROR_RESPONSES,
)
async def list_appointments(
    actor: ActorDep,
    container: ContainerDep,
    status_filter: Annotated[
        AppointmentStatus | None, Query(alias="status", description="Filter by status")
    ] = None,
    patient_id: Annotated[UUID | None, Query(description="Filter by patient")] = None,
    doctor_id: Annotated[UUID | None, Query(description="Filter by doctor")] = None,
    view: Annotated[
        str | None,
        Query(
            description=(
                "For dual-role users: 'patient' shows their own patient appointments, "
                "'doctor' shows their doctor schedule"
            )
        ),
    ] = None,
    starts_after: Annotated[
        datetime | None, Query(description="ISO 8601 lower bound of starts_at")
    ] = None,
    starts_before: Annotated[
        datetime | None, Query(description="ISO 8601 upper bound of starts_at")
    ] = None,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> AppointmentListResponse:
    """Patients see their own appointments, doctors see their schedule, admins see all."""
    query = ListAppointmentsQuery(
        status=status_filter,
        patient_id=patient_id,
        doctor_id=doctor_id,
        view=view,
        starts_after=starts_after,
        starts_before=starts_before,
        limit=limit,
        offset=offset,
    )
    items, total = await container.list_appointments(actor=actor, query=query)
    return AppointmentListResponse(
        items=[_to_response(item) for item in items],
        total=total,
        limit=limit,
        offset=offset,
    )


@router.get(
    "/{appointment_id}",
    response_model=AppointmentResponse,
    summary="Get one appointment",
    responses=_ERROR_RESPONSES,
)
async def get_appointment(
    appointment_id: UUID,
    actor: ActorDep,
    container: ContainerDep,
) -> AppointmentResponse:
    appointment = await container.get_appointment(actor=actor, appointment_id=appointment_id)
    return _to_response(appointment)


@router.post(
    "/{appointment_id}/confirm",
    response_model=AppointmentResponse,
    summary="Confirm a requested appointment (doctor)",
    responses=_ERROR_RESPONSES,
)
async def confirm_appointment(
    appointment_id: UUID,
    actor: ActorDep,
    container: ContainerDep,
) -> AppointmentResponse:
    appointment = await container.confirm_appointment(actor=actor, appointment_id=appointment_id)
    return _to_response(appointment)


@router.post(
    "/{appointment_id}/reschedule",
    response_model=AppointmentResponse,
    summary="Move an appointment to another time window",
    responses=_ERROR_RESPONSES,
)
async def reschedule_appointment(
    appointment_id: UUID,
    body: AppointmentRescheduleRequest,
    actor: ActorDep,
    container: ContainerDep,
) -> AppointmentResponse:
    """Same lead-time rules as creation: patient ≥ 1 hour, doctor not past, admin anytime."""
    appointment = await container.reschedule_appointment(
        actor=actor,
        appointment_id=appointment_id,
        starts_at=body.starts_at,
        ends_at=body.ends_at,
    )
    return _to_response(appointment)


@router.post(
    "/{appointment_id}/cancel",
    response_model=AppointmentResponse,
    summary="Cancel an appointment",
    responses=_ERROR_RESPONSES,
)
async def cancel_appointment(
    appointment_id: UUID,
    body: AppointmentCancelRequest,
    actor: ActorDep,
    container: ContainerDep,
) -> AppointmentResponse:
    appointment = await container.cancel_appointment(
        actor=actor,
        appointment_id=appointment_id,
        reason=body.reason,
    )
    return _to_response(appointment)


@router.post(
    "/{appointment_id}/complete",
    response_model=AppointmentResponse,
    summary="Complete an appointment (doctor)",
    responses=_ERROR_RESPONSES,
)
async def complete_appointment(
    appointment_id: UUID,
    actor: ActorDep,
    container: ContainerDep,
) -> AppointmentResponse:
    appointment = await container.complete_appointment(actor=actor, appointment_id=appointment_id)
    return _to_response(appointment)


@router.post(
    "/{appointment_id}/no-show",
    response_model=AppointmentResponse,
    summary="Mark the patient as a no-show (doctor)",
    responses=_ERROR_RESPONSES,
)
async def mark_no_show(
    appointment_id: UUID,
    actor: ActorDep,
    container: ContainerDep,
) -> AppointmentResponse:
    appointment = await container.mark_no_show(actor=actor, appointment_id=appointment_id)
    return _to_response(appointment)
