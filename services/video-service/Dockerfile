

FROM python:3.14-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy

WORKDIR /app

RUN pip install --no-cache-dir uv

COPY pyproject.toml uv.lock ./
COPY libs/python ./libs/python
COPY services/video_service ./services/video_service

RUN uv sync --package video-service --no-dev --frozen

WORKDIR /app/services/video_service

EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8087/health/live', timeout=2)"

CMD ["/app/.venv/bin/uvicorn", "video_service.main:app", "--host", "0.0.0.0", "--port", "8087"]
