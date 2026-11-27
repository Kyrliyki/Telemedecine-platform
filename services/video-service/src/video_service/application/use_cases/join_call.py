"""Сценарий выдачи токена на подключение к звонку."""

from __future__ import annotations

import logging
from datetime import timedelta

from video_service.application.dto import (
    Actor,
    CallView,
    JoinCallCommand,
    JoinCallResult,
)
from video_service.application.ports import (
    Clock,
    LiveKitGateway,
    LiveKitGatewayError,
    UnitOfWorkFactory,
)
from video_service.domain.errors import CallNotFoundError
from video_service.domain.value_objects import ParticipantIdentity

logger = logging.getLogger(__name__)


class JoinCallUseCase:
    """Проверяет право на участие и выдаёт JWT для подключения к комнате LiveKit."""

    def __init__(
        self,
        *,
        uow_factory: UnitOfWorkFactory,
        livekit: LiveKitGateway,
        clock: Clock,
        token_ttl: timedelta,
        public_url: str,
    ) -> None:
        self._uow_factory = uow_factory
        self._livekit = livekit
        self._clock = clock
        self._token_ttl = token_ttl
        self._public_url = public_url

    async def execute(self, actor: Actor, command: JoinCallCommand) -> JoinCallResult:
        ParticipantIdentity(actor.identity)

        async with self._uow_factory() as uow:
            call = await uow.calls.get(command.call_id)
            if call is None:
                raise CallNotFoundError(f"Звонок {command.call_id} не найден")

            call.ensure_can_join(actor.identity, actor.roles)

            if call.mark_started(self._clock.now()):
                await uow.calls.update(call)
                await uow.commit()

            view = CallView.from_entity(call)
            room_name = call.room_name

        participant = self._participant_role(actor.identity, view)
        try:
            token = await self._livekit.create_participant_token(
                room_name=room_name,
                identity=actor.identity,
                display_name=command.display_name or actor.display_name or actor.identity,
                ttl=self._token_ttl,
                metadata={
                    "call_id": view.id,
                    "appointment_id": view.appointment_id,
                    "role": participant,
                },
            )
        except LiveKitGatewayError:
            raise
        except Exception as exc:  # pragma: no cover - защитная обёртка адаптера
            raise LiveKitGatewayError("Не удалось выдать токен LiveKit") from exc

        expires_at = self._clock.now() + self._token_ttl
        logger.info(
            "call.token.issued",
            extra={"call_id": view.id, "identity": actor.identity},
        )
        return JoinCallResult(
            call=view,
            token=token,
            livekit_url=self._public_url,
            expires_at=expires_at,
        )

    @staticmethod
    def _participant_role(identity: str, call: CallView) -> str:
        if identity == call.doctor_id:
            return "doctor"
        if identity == call.patient_id:
            return "patient"
        return "admin"
