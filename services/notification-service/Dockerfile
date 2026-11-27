

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
COPY services/notification-service ./services/notification-service

RUN uv sync --package notification-service --no-dev --frozen

WORKDIR /app/services/notification-service

EXPOSE 8088

HEALTHCHECK --interval=20s --timeout=5s --start-period=25s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8088/health/live', timeout=3)"

CMD ["/app/.venv/bin/uvicorn", "notification_service.main:app", \
     "--host", "0.0.0.0", "--port", "8088"]
