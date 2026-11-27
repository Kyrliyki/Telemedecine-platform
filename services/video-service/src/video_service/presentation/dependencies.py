"""FastAPI-зависимости: контейнер и текущий пользователь."""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from video_service.application.dto import Actor
from video_service.application.ports import InvalidTokenError
from video_service.composition import Container
from video_service.domain.enums import ActorRole

bearer_scheme = HTTPBearer(auto_error=False, description="JWT от AuthService")

def get_container(request: Request) -> Container:
    container: Container = request.app.state.container
    return container

ContainerDep = Annotated[Container, Depends(get_container)]

async def get_actor(
    container: ContainerDep,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    request: Request,
) -> Actor:
    if not container.settings.auth_enabled:
        
        identity = request.headers.get("X-Debug-User") or "dev-doctor"
        raw_roles = request.headers.get("X-Debug-Roles") or "doctor"
        roles = {
            role
            for value in raw_roles.split(",")
            if (role := ActorRole.from_claim(value)) is not None
        }
        return Actor(
            identity=identity,
            roles=frozenset(roles),
            display_name=request.headers.get("X-Debug-Name") or identity,
        )

    if credentials is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Требуется Bearer-токен",
            headers={"WWW-Authenticate": "Bearer"},
        )

    try:
        return await container.token_verifier.verify(credentials.credentials)
    except InvalidTokenError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Невалидный или истёкший токен",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc

ActorDep = Annotated[Actor, Depends(get_actor)]
