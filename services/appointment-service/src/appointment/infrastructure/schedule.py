"""HTTP client for the schedule-service internal slot API."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

import httpx

from appointment.domain.errors import ScheduleUnavailable, SlotUnavailable
from appointment.infrastructure.logging import get_logger

logger = get_logger(__name__)


class NoopScheduleGateway:
    """Test/local gateway that accepts every slot operation."""

    async def hold(
        self,
        *,
        appointment_id: UUID,
        doctor_id: UUID,
        patient_id: UUID,
        starts_at: datetime,
    ) -> None:
        return None

    async def confirm(self, *, appointment_id: UUID) -> None:
        return None

    async def release(self, *, appointment_id: UUID) -> None:
        return None

    async def aclose(self) -> None:
        return None


class UnavailableScheduleGateway:
    """Fail-closed gateway used when schedule integration is not configured."""

    async def hold(
        self,
        *,
        appointment_id: UUID,
        doctor_id: UUID,
        patient_id: UUID,
        starts_at: datetime,
    ) -> None:
        raise ScheduleUnavailable("ScheduleService URL is not configured")

    async def confirm(self, *, appointment_id: UUID) -> None:
        raise ScheduleUnavailable("ScheduleService URL is not configured")

    async def release(self, *, appointment_id: UUID) -> None:
        raise ScheduleUnavailable("ScheduleService URL is not configured")

    async def aclose(self) -> None:
        return None


class HttpScheduleGateway:
    def __init__(self, *, base_url: str, internal_token: str) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=httpx.Timeout(3.0, connect=2.0),
            headers={"X-Internal-Token": internal_token} if internal_token else {},
        )

    async def hold(
        self,
        *,
        appointment_id: UUID,
        doctor_id: UUID,
        patient_id: UUID,
        starts_at: datetime,
    ) -> None:
        await self._post(
            "/internal/schedule/slots/hold",
            {
                "appointmentId": str(appointment_id),
                "doctorId": str(doctor_id),
                "patientId": str(patient_id),
                "startsAt": starts_at.isoformat(),
            },
        )

    async def confirm(self, *, appointment_id: UUID) -> None:
        await self._post(
            "/internal/schedule/slots/confirm",
            {"appointmentId": str(appointment_id)},
        )

    async def release(self, *, appointment_id: UUID) -> None:
        await self._post(
            "/internal/schedule/slots/release",
            {"appointmentId": str(appointment_id)},
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def _post(self, path: str, payload: dict[str, str]) -> None:
        try:
            response = await self._client.post(path, json=payload)
        except httpx.HTTPError as exc:
            logger.warning("schedule_request_failed", path=path, error=str(exc))
            raise ScheduleUnavailable("ScheduleService is unavailable") from exc

        if response.is_success:
            return

        detail = _detail(response)
        if response.status_code in {400, 403, 404, 409, 422}:
            raise SlotUnavailable(detail)
        logger.warning(
            "schedule_request_rejected",
            path=path,
            status=response.status_code,
            detail=detail,
        )
        raise ScheduleUnavailable("ScheduleService returned an error")


def _detail(response: httpx.Response) -> str:
    try:
        body = response.json()
    except ValueError:
        return "ScheduleService rejected the requested slot"
    if isinstance(body, dict):
        detail = body.get("detail") or body.get("message") or body.get("title")
        if isinstance(detail, str) and detail.strip():
            return detail
    return "ScheduleService rejected the requested slot"
