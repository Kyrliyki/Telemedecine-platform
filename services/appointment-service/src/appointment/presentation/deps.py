"""FastAPI dependencies: container and authenticated actor."""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from appointment.container import Container
from appointment.domain.access import Actor
from appointment.domain.errors import AuthenticationError

bearer_scheme = HTTPBearer(
    auto_error=False,
    description="JWT access token issued by AuthService",
)


def get_container(request: Request) -> Container:
    container: Container = request.app.state.container
    return container


ContainerDep = Annotated[Container, Depends(get_container)]


async def get_actor(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    container: ContainerDep,
) -> Actor:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise AuthenticationError("missing bearer token")
    claims = await container.authenticator.authenticate(credentials.credentials)
    return claims.to_actor()


ActorDep = Annotated[Actor, Depends(get_actor)]
