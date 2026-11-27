"""Guards for the dependency rule of Clean Architecture."""

from __future__ import annotations

from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[2] / "src" / "file_service"

FORBIDDEN_IN_DOMAIN = (
    "file_service.application",
    "file_service.infrastructure",
    "file_service.presentation",
    "fastapi",
    "sqlalchemy",
    "minio",
    "aio_pika",
    "redis",
)

FORBIDDEN_IN_APPLICATION = (
    "file_service.infrastructure",
    "file_service.presentation",
    "fastapi",
    "sqlalchemy",
    "minio",
    "aio_pika",
    "redis",
)


def imports(layer: str) -> list[tuple[Path, str]]:
    result: list[tuple[Path, str]] = []
    for path in (PACKAGE / layer).rglob("*.py"):
        for line in path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if stripped.startswith(("import ", "from ")):
                result.append((path, stripped))
    return result


def test_domain_does_not_depend_on_outer_layers() -> None:
    violations = [
        (path, line)
        for path, line in imports("domain")
        if any(token in line for token in FORBIDDEN_IN_DOMAIN)
    ]
    assert violations == [], f"Domain layer depends on outer layers: {violations}"


def test_application_depends_only_on_domain_and_ports() -> None:
    violations = [
        (path, line)
        for path, line in imports("application")
        if any(token in line for token in FORBIDDEN_IN_APPLICATION)
    ]
    assert violations == [], f"Application layer depends on infrastructure: {violations}"
