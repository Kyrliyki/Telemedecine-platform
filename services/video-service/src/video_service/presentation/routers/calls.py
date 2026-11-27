"""API звонков."""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Query, Response, status

from video_service.application.dto import (
    CreateCallCommand,
    JoinCallCommand,
    ListCallsQuery,
)
from video_service.domain.enums import CallStatus
from video_service.domain.value_objects import CallId
from video_service.presentation.dependencies import ActorDep, ContainerDep
from video_service.presentation.schemas import (
    CallListResponse,
    CallResponse,
    CreateCallRequest,
    ErrorResponse,
    JoinCallRequest,
    JoinCallResponse,
)

router = APIRouter(tags=["calls"])


@router.post(
    "",
    response_model=CallResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Создать звонок (комнату LiveKit)",
    description=(
        "Создаёт комнату в LiveKit и сохраняет звонок. Идемпотентно по appointment_id: "
        "повторный запрос вернёт существующий звонок со статусом 200."
    ),
    responses={
        200: {"description": "Звонок уже существует (idempotent replay)", "model": CallResponse},
        409: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def create_call(
    payload: CreateCallRequest,
    actor: ActorDep,
    container: ContainerDep,
    response: Response,
) -> CallResponse:
    result = await container.create_call.execute(
        actor,
        CreateCallCommand(
            appointment_id=payload.appointment_id,
            patient_id=payload.patient_id,
            doctor_id=payload.doctor_id,
            max_participants=payload.max_participants,
        ),
    )
    if not result.created:
        response.status_code = status.HTTP_200_OK
        response.headers["Idempotent-Replayed"] = "true"
    return CallResponse.from_view(result.call)


@router.get(
    "",
    response_model=CallListResponse,
    summary="Список звонков",
    description="Администратор видит все звонки, остальные — только свои.",
)
async def list_calls(
    actor: ActorDep,
    container: ContainerDep,
    appointment_id: uuid.UUID | None = None,
    status_filter: Annotated[CallStatus | None, Query(alias="status")] = None,
    doctor_id: str | None = None,
    patient_id: str | None = None,
    limit: Annotated[int, Query(ge=1, le=200)] = 50,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> CallListResponse:
    page = await container.list_calls.execute(
        actor,
        ListCallsQuery(
            appointment_id=appointment_id,
            status=status_filter,
            doctor_id=doctor_id,
            patient_id=patient_id,
            limit=limit,
            offset=offset,
        ),
    )
    return CallListResponse(
        items=[CallResponse.from_view(call) for call in page.items],
        limit=page.limit,
        offset=page.offset,
    )


@router.get(
    "/{call_id}",
    response_model=CallResponse,
    summary="Получить звонок",
    responses={403: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
async def get_call(
    call_id: uuid.UUID,
    actor: ActorDep,
    container: ContainerDep,
) -> CallResponse:
    call = await container.get_call.execute(actor, CallId(call_id))
    return CallResponse.from_view(call)


@router.post(
    "/{call_id}/token",
    response_model=JoinCallResponse,
    summary="Получить токен на подключение",
    description=(
        "Проверяет, что пользователь является участником звонка, и выдаёт "
        "короткоживущий JWT для подключения к LiveKit."
    ),
    responses={
        403: {"model": ErrorResponse},
        404: {"model": ErrorResponse},
        409: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
)
async def join_call(
    call_id: uuid.UUID,
    actor: ActorDep,
    container: ContainerDep,
    payload: JoinCallRequest | None = None,
) -> JoinCallResponse:
    result = await container.join_call.execute(
        actor,
        JoinCallCommand(
            call_id=CallId(call_id),
            display_name=payload.display_name if payload else None,
        ),
    )
    return JoinCallResponse(
        call=CallResponse.from_view(result.call),
        token=result.token,
        livekit_url=result.livekit_url,
        expires_at=result.expires_at,
    )


@router.post(
    "/{call_id}/end",
    response_model=CallResponse,
    summary="Завершить звонок",
    description="Идемпотентно завершает звонок и удаляет комнату LiveKit.",
    responses={403: {"model": ErrorResponse}, 404: {"model": ErrorResponse}},
)
async def end_call(
    call_id: uuid.UUID,
    actor: ActorDep,
    container: ContainerDep,
) -> CallResponse:
    call = await container.end_call.execute(actor, CallId(call_id))
    return CallResponse.from_view(call)
