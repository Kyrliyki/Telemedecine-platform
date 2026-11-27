"""Сценарий создания звонка (комнаты LiveKit)."""

from __future__ import annotations

import logging
import uuid

from video_service.application.dto import (
    Actor,
    CallView,
    CreateCallCommand,
    CreateCallResult,
)
from video_service.application.ports import (
    AppointmentDirectory,
    CallNotifier,
    Clock,
    EventPublisher,
    IdGenerator,
    LiveKitGateway,
    LiveKitGatewayError,
    UnitOfWorkFactory,
)
from video_service.domain.entities import Call
from video_service.domain.enums import ActorRole
from video_service.domain.errors import (
    AppointmentNotConfirmedError,
    AppointmentNotFoundError,
    CallAlreadyExistsError,
    ForbiddenError,
    ValidationError,
)
from video_service.domain.events import CallCreated
from video_service.domain.value_objects import RoomName

logger = logging.getLogger(__name__)

class CreateCallUseCase:
    """Создаёт комнату в LiveKit и сохраняет звонок.

    На один приём можно провести несколько звонков (история сохраняется), но
    одновременно активным может быть только один: при повторном нажатии врач
    получает текущий незавершённый звонок, а после завершения создаётся новый.
    """

    def __init__(
        self,
        *,
        uow_factory: UnitOfWorkFactory,
        livekit: LiveKitGateway,
        clock: Clock,
        ids: IdGenerator,
        publisher: EventPublisher,
        notifier: CallNotifier | None = None,
        appointments: AppointmentDirectory | None = None,
    ) -> None:
        self._uow_factory = uow_factory
        self._livekit = livekit
        self._clock = clock
        self._ids = ids
        self._publisher = publisher
        self._notifier = notifier
        self._appointments = appointments

    async def execute(self, actor: Actor, command: CreateCallCommand) -> CreateCallResult:
        doctor_id = self._resolve_doctor_id(actor, command)
        await self._validate_appointment(
            appointment_id=command.appointment_id,
            patient_id=command.patient_id,
            doctor_id=doctor_id,
        )

        existing = await self._find_current(actor, command.appointment_id, doctor_id)
        if existing is not None:
            logger.info(
                "call.create.idempotent_replay",
                extra={
                    "call_id": str(existing.id),
                    "appointment_id": str(existing.appointment_id),
                },
            )
            return CreateCallResult(call=CallView.from_entity(existing), created=False)

        call = Call.create(
            call_id=self._ids.new_call_id(),
            appointment_id=command.appointment_id,
            doctor_id=doctor_id,
            patient_id=command.patient_id,
            max_participants=command.max_participants,
            now=self._clock.now(),
        )

        await self._create_livekit_room(call)

        try:
            async with self._uow_factory() as uow:
                await uow.calls.add(call)
                await uow.commit()
        except CallAlreadyExistsError:
            
            
            
            await self._delete_room_best_effort(call.room_name)
            existing = await self._find_current(actor, command.appointment_id, doctor_id)
            if existing is not None:
                return CreateCallResult(
                    call=CallView.from_entity(existing),
                    created=False,
                )
            raise
        except Exception:
            
            
            await self._delete_room_best_effort(call.room_name)
            raise

        view = CallView.from_entity(call)
        await self._publish_created(call)
        await self._notify_started(view)

        logger.info(
            "call.created",
            extra={"call_id": str(call.id), "room_name": call.room_name.value},
        )
        return CreateCallResult(call=view, created=True)

    async def _validate_appointment(
        self,
        *,
        appointment_id: uuid.UUID,
        patient_id: str,
        doctor_id: str,
    ) -> None:
        if self._appointments is None:
            return
        appointment = await self._appointments.get(appointment_id)
        if appointment is None:
            raise AppointmentNotFoundError(f"Приём {appointment_id} не найден")
        if appointment.status.upper() != "CONFIRMED":
            raise AppointmentNotConfirmedError(
                f"Приём {appointment_id} должен быть подтверждён для видеозвонка"
            )
        if str(appointment.patient_id) != patient_id:
            raise ForbiddenError("Приём принадлежит другому пациенту")
        if str(appointment.doctor_id) != doctor_id:
            raise ForbiddenError("Приём обслуживается другим врачом")

    async def _find_current(
        self,
        actor: Actor,
        appointment_id: uuid.UUID,
        doctor_id: str,
    ) -> Call | None:
        async with self._uow_factory() as uow:
            existing = await uow.calls.get_current_by_appointment(appointment_id)

        if existing is None:
            return None
        if not actor.is_admin and existing.doctor_id != doctor_id:
            raise ForbiddenError("Приём уже обслуживается другим врачом")
        return existing

    def _resolve_doctor_id(self, actor: Actor, command: CreateCallCommand) -> str:
        if actor.is_admin:
            if not command.doctor_id:
                raise ValidationError("Для администратора поле doctor_id обязательно")
            return command.doctor_id

        actor.require_any_role(ActorRole.DOCTOR)
        if command.doctor_id and command.doctor_id != actor.identity:
            raise ForbiddenError("Нельзя создать звонок от имени другого врача")
        return actor.identity

    async def _create_livekit_room(self, call: Call) -> None:
        try:
            await self._livekit.create_room(
                room_name=call.room_name,
                metadata={
                    "call_id": str(call.id),
                    "appointment_id": str(call.appointment_id),
                    "doctor_id": call.doctor_id,
                    "patient_id": call.patient_id,
                },
                max_participants=call.max_participants,
            )
        except LiveKitGatewayError:
            raise
        except Exception as exc:  # pragma: no cover - защитная обёртка адаптера
            raise LiveKitGatewayError("Не удалось создать комнату LiveKit") from exc

    async def _delete_room_best_effort(self, room_name: RoomName) -> None:
        try:
            await self._livekit.delete_room(room_name=room_name)
        except Exception:
            logger.exception(
                "call.create.compensation_failed",
                extra={"room_name": room_name.value},
            )

    async def _notify_started(self, call: CallView) -> None:
        if self._notifier is None:
            return
        try:
            await self._notifier.notify_started(call)
        except Exception:
            
            logger.exception("call.create.notify_failed", extra={"call_id": call.id})

    async def _publish_created(self, call: Call) -> None:
        event = CallCreated(
            call_id=str(call.id),
            appointment_id=str(call.appointment_id),
            room_name=call.room_name.value,
            doctor_id=call.doctor_id,
            patient_id=call.patient_id,
            occurred_at=call.created_at,
        )
        try:
            await self._publisher.publish(event)
        except Exception:
            
            
            logger.exception("call.created.event_publish_failed", extra={"call_id": str(call.id)})
