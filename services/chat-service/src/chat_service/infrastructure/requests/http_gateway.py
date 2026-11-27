import logging
from uuid import UUID

import httpx

from chat_service.application.ports.request_gateway import RequestInfo
from chat_service.domain.exceptions import RequestNotFoundError, RequestServiceUnavailableError
from chat_service.domain.value_objects import RequestStatus

logger = logging.getLogger(__name__)

CLOSED_APPOINTMENT_STATUSES = {"CANCELLED", "COMPLETED", "NO_SHOW"}


class NoopRequestGateway:
    async def validate(self, request_id: str) -> RequestInfo | None:
        return None

    async def get_status(self, request_id: str) -> RequestStatus:
        return RequestStatus.OPEN


class HttpRequestGateway:
    def __init__(self, base_url: str, internal_token: str = "") -> None:
        self._base_url = base_url.rstrip("/")
        self._client = httpx.AsyncClient(
            base_url=self._base_url,
            timeout=5.0,
            headers={"X-Internal-Token": internal_token} if internal_token else {},
        )

    async def validate(self, request_id: str) -> RequestInfo | None:
        try:
            response = await self._client.get(f"/internal/appointments/{request_id}")
        except httpx.HTTPError as exc:
            logger.warning("request_gateway_unavailable", extra={"request_id": request_id})
            raise RequestServiceUnavailableError("AppointmentService is unavailable") from exc

        if response.status_code == 404:
            raise RequestNotFoundError(f"appointment {request_id} was not found")
        if not response.is_success:
            logger.warning(
                "request_gateway_rejected",
                extra={"request_id": request_id, "status": response.status_code},
            )
            raise RequestServiceUnavailableError("AppointmentService returned an error")

        data = response.json()
        status = (
            RequestStatus.CLOSED
            if str(data.get("status", "")).upper() in CLOSED_APPOINTMENT_STATUSES
            else RequestStatus.OPEN
        )
        return RequestInfo(
            request_id=request_id,
            status=status,
            patient_id=UUID(str(data["patientId"])) if data.get("patientId") else None,
            doctor_id=UUID(str(data["doctorId"])) if data.get("doctorId") else None,
        )

    async def get_status(self, request_id: str) -> RequestStatus:
        info = await self.validate(request_id)
        return info.status if info else RequestStatus.OPEN

    async def aclose(self) -> None:
        await self._client.aclose()
