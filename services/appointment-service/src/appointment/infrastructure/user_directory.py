"""HTTP client for UserService internal profile lookups."""

from __future__ import annotations

from uuid import UUID

import httpx
from pydantic import BaseModel, ConfigDict, Field

from appointment.domain.errors import InvalidParticipant, UserDirectoryUnavailable
from appointment.infrastructure.logging import get_logger

logger = get_logger(__name__)


class _DoctorProfile(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    verification_status: str | None = Field(default=None, alias="verificationStatus")


class _UserProfile(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    user_id: UUID = Field(alias="userId")
    roles: set[str] = Field(default_factory=set)
    status: str = ""
    doctor_profile: _DoctorProfile | None = Field(default=None, alias="doctorProfile")

    def has_role(self, role: str) -> bool:
        wanted = role.lower()
        return any(item.lower() == wanted for item in self.roles)


class NoopUserDirectoryGateway:
    """Test gateway that accepts every participant."""

    async def require_patient(self, patient_id: UUID) -> None:
        return None

    async def require_doctor(self, doctor_id: UUID) -> None:
        return None

    async def aclose(self) -> None:
        return None


class UnavailableUserDirectoryGateway:
    """Fail-closed gateway used when UserService is not configured."""

    async def require_patient(self, patient_id: UUID) -> None:
        raise UserDirectoryUnavailable("UserService URL is not configured")

    async def require_doctor(self, doctor_id: UUID) -> None:
        raise UserDirectoryUnavailable("UserService URL is not configured")

    async def aclose(self) -> None:
        return None


class HttpUserDirectoryGateway:
    def __init__(self, *, base_url: str, internal_token: str) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=httpx.Timeout(3.0, connect=2.0),
            headers={"X-Internal-Token": internal_token} if internal_token else {},
        )

    async def require_patient(self, patient_id: UUID) -> None:
        profile = await self._load(patient_id, label="Patient")
        _require_active_role(profile, role="PATIENT", label="Patient")

    async def require_doctor(self, doctor_id: UUID) -> None:
        profile = await self._load(doctor_id, label="Doctor")
        _require_active_role(profile, role="DOCTOR", label="Doctor")
        doctor_profile = profile.doctor_profile
        if doctor_profile is None:
            raise InvalidParticipant("Doctor profile is missing")
        if (doctor_profile.verification_status or "").upper() != "VERIFIED":
            raise InvalidParticipant("Doctor profile is not verified")

    async def aclose(self) -> None:
        await self._client.aclose()

    async def _load(self, user_id: UUID, *, label: str) -> _UserProfile:
        try:
            response = await self._client.get(f"/internal/users/{user_id}")
        except httpx.HTTPError as exc:
            logger.warning("user_directory_request_failed", user_id=str(user_id), error=str(exc))
            raise UserDirectoryUnavailable("UserService is unavailable") from exc

        if response.status_code == 404:
            raise InvalidParticipant(f"{label} account was not found")
        if not response.is_success:
            logger.warning(
                "user_directory_request_rejected",
                user_id=str(user_id),
                status=response.status_code,
            )
            raise UserDirectoryUnavailable("UserService returned an error")

        try:
            return _UserProfile.model_validate(response.json())
        except ValueError as exc:
            logger.warning("user_directory_invalid_response", user_id=str(user_id), error=str(exc))
            raise UserDirectoryUnavailable("UserService returned an invalid response") from exc


def _require_active_role(profile: _UserProfile, *, role: str, label: str) -> None:
    if profile.status.upper() != "ACTIVE":
        raise InvalidParticipant(f"{label} account is not active")
    if not profile.has_role(role):
        raise InvalidParticipant(f"User does not have the {role} role")
