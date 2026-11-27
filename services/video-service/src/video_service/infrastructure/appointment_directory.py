"""HTTP client for the appointment-service internal API."""

from __future__ import annotations

import logging
from uuid import UUID

import httpx
from pydantic import BaseModel, ConfigDict, Field

from video_service.application.ports import AppointmentInfo
from video_service.domain.errors import AppointmentServiceUnavailableError

logger = logging.getLogger(__name__)


class _AppointmentResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    appointment_id: UUID = Field(alias="appointmentId")
    patient_id: UUID = Field(alias="patientId")
    doctor_id: UUID = Field(alias="doctorId")
    status: str


class HttpAppointmentDirectory:
    def __init__(self, *, base_url: str, internal_token: str) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=httpx.Timeout(3.0, connect=2.0),
            headers={"X-Internal-Token": internal_token} if internal_token else {},
        )

    async def get(self, appointment_id: UUID) -> AppointmentInfo | None:
        try:
            response = await self._client.get(f"/internal/appointments/{appointment_id}")
        except httpx.HTTPError as exc:
            logger.warning(
                "appointment_request_failed",
                extra={"appointment_id": str(appointment_id), "error": str(exc)},
            )
            raise AppointmentServiceUnavailableError("AppointmentService is unavailable") from exc

        if response.status_code == 404:
            return None
        if not response.is_success:
            logger.warning(
                "appointment_request_rejected",
                extra={"appointment_id": str(appointment_id), "status": response.status_code},
            )
            raise AppointmentServiceUnavailableError("AppointmentService returned an error")

        try:
            payload = _AppointmentResponse.model_validate(response.json())
        except ValueError as exc:
            logger.warning(
                "appointment_invalid_response",
                extra={"appointment_id": str(appointment_id), "error": str(exc)},
            )
            raise AppointmentServiceUnavailableError(
                "AppointmentService returned an invalid response"
            ) from exc

        return AppointmentInfo(
            appointment_id=payload.appointment_id,
            patient_id=payload.patient_id,
            doctor_id=payload.doctor_id,
            status=payload.status,
        )

    async def aclose(self) -> None:
        await self._client.aclose()


class UnavailableAppointmentDirectory:
    """Fail-closed directory used when the integration URL is not configured."""

    async def get(self, appointment_id: UUID) -> AppointmentInfo | None:
        raise AppointmentServiceUnavailableError("AppointmentService URL is not configured")

    async def aclose(self) -> None:
        return None
