from dataclasses import dataclass
from typing import Protocol
from uuid import UUID

from chat_service.domain.value_objects import RequestStatus


@dataclass(slots=True)
class RequestInfo:
    request_id: str
    status: RequestStatus
    patient_id: UUID | None = None
    doctor_id: UUID | None = None


class RequestGateway(Protocol):
    async def validate(self, request_id: str) -> RequestInfo: ...
    async def get_status(self, request_id: str) -> RequestStatus: ...