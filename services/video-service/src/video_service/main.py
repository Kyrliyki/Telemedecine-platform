"""Точка входа: ``uvicorn video_service.main:app``."""

from __future__ import annotations

from video_service.presentation.app import create_app

app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("video_service.main:app", host="0.0.0.0", port=8000, reload=False)
