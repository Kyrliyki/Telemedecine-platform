# video-service

Видеозвонки на LiveKit: создание комнаты, выдача токена участнику, список звонков, завершение и обработка webhook-событий.

## Стек

- Python 3.14
- FastAPI
- SQLAlchemy 2 и asyncpg
- Alembic
- PostgreSQL
- LiveKit
- RabbitMQ
- Redis
- JWT/JWKS
- Testcontainers

## Порты и БД

| Параметр | Значение |
| --- | --- |
| HTTP | 8087 |
| Схема | `video_service` |
| Пользователь БД | `video_svc` |
| Alembic | `migrations/versions/0001_initial.py` |

## Ответственность

- Создание звонка по `appointment_id`.
- Идемпотентность активного звонка на запись.
- Создание и удаление LiveKit-комнаты.
- Выдача LiveKit JWT участнику.
- Проверка прав пациента, врача и администратора.
- Список и чтение звонков.
- Завершение звонка.
- Обработка LiveKit webhook.
- Публикация событий `call.created` и `call.ended`.
- Уведомление второй стороны о начале звонка.

## Основные endpoints

- `POST /api/v1/video`
- `GET /api/v1/video`
- `GET /api/v1/video/{callId}`
- `POST /api/v1/video/{callId}/token`
- `POST /api/v1/video/{callId}/end`
- `POST /api/v1/webhooks/livekit`

## Конфигурация

Основные переменные:

- `SERVER_PORT`
- `DATABASE_URL`
- `DB_SCHEMA`
- `REDIS_URL`
- `RABBITMQ_URL`
- `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`
- `LIVEKIT_TOKEN_TTL_SECONDS`
- `LIVEKIT_ROOM_EMPTY_TIMEOUT_SECONDS`
- `APPOINTMENT_SERVICE_URL`
- `JWT_JWKS_URL`
- `VIDEO_ENVIRONMENT`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build video-service
```

Локально:

```powershell
uv sync --all-packages --all-extras
uv run uvicorn video_service.main:app --reload --port 8087
```

Для локального запуска нужны PostgreSQL, Redis, RabbitMQ и LiveKit.

## Тесты

```powershell
uv run pytest services/video-service/tests_video_service -q
```

Интеграционные тесты LiveKit требуют доступный LiveKit и Docker.

## Миграции

Единственная миграция `0001_initial.py` создаёт таблицу звонков и уникальные индексы.

## Связанные сервисы

- [appointment-service](../appointment-service/README.md) — проверка записи.
- [chat-service](../chat-service/README.md) — чат и уведомление о звонке.
- [notification-service](../notification-service/README.md) — уведомления.
- [api-gateway](../api-gateway/README.md) — маршрутизация.
- [frontend](../../frontend/README.md) — LiveKit-клиент.

## Ссылки

- [Корневой README](../../README.md)
