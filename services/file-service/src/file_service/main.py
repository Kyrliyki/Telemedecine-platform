"""Process entrypoint."""

from __future__ import annotations

import uvicorn

from file_service.presentation.app import app, create_app

__all__ = ["app", "create_app", "run"]


def run() -> None:
    uvicorn.run(
        "file_service.presentation.app:app",
        host="0.0.0.0",
        port=8000,
        proxy_headers=True,
        forwarded_allow_ips="*",
    )


if __name__ == "__main__":
    run()
