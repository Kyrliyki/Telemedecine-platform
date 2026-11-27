"""FastAPI dependencies: container, use cases and the current actor."""

from __future__ import annotations

from typing import Annotated, Protocol
from uuid import UUID

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from file_service.application.dto import Actor
from file_service.application.use_cases import UseCases
from file_service.domain.exceptions import AuthenticationError
from file_service.infrastructure.config import Settings
from file_service.infrastructure.security.jwt import JwtVerifier

_bearer = HTTPBearer(auto_error=False, description="JWT issued by AuthService")


class ContainerProtocol(Protocol):
    use_cases: UseCases
    verifier: JwtVerifier

    async def start(self) -> None: ...

    async def stop(self) -> None: ...

    async def health(self) -> dict: ...


def get_container(request: Request) -> ContainerProtocol:
    return request.app.state.container


def get_settings(request: Request) -> Settings:
    return request.app.state.settings


def get_use_cases(container: Annotated[ContainerProtocol, Depends(get_container)]) -> UseCases:
    return container.use_cases


async def get_actor(
    request: Request,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
) -> Actor:
    settings = get_settings(request)
    if settings.auth_disabled:
        raw_user_id = request.headers.get("X-User-Id", settings.dev_user_id)
        try:
            user_id = UUID(raw_user_id)
        except ValueError as exc:
            raise AuthenticationError("X-User-Id must be a UUID") from exc
        raw_roles = request.headers.get("X-User-Roles", settings.dev_user_roles)
        roles = frozenset(role.strip() for role in raw_roles.split(",") if role.strip())
        return Actor(id=user_id, roles=roles)

    if credentials is None or not credentials.credentials:
        raise AuthenticationError("Authorization header with a bearer token is required")
    container = get_container(request)
    return await container.verifier.verify(credentials.credentials)


CurrentActor = Annotated[Actor, Depends(get_actor)]
UseCasesDep = Annotated[UseCases, Depends(get_use_cases)]
