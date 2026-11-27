# chat-service

Чат между пациентом и врачом по записи: диалоги, история сообщений, прочтение, печать, WebSocket и события.

## Стек

- Python 3.14
- FastAPI
- SQLAlchemy 2 и asyncpg
- Alembic
- PostgreSQL
- Redis
- RabbitMQ
- WebSocket
- Testcontainers

## Порты и БД

| Параметр | Значение |
| --- | --- |
| HTTP | 8085 |
| WebSocket | `/ws/chat` |
| Схема | `chat_service` |
| Пользователь БД | `chat_svc` |
| Alembic | `migrations/versions/0001_initial.py` |

## Ответственность

- Создание диалога по `request_id` записи.
- Список диалогов пользователя.
- История сообщений.
- Отправка сообщений через REST или WebSocket.
- Подтверждение доставки через `message.ack`.
- Прочтение и unread count.
- Typing/presence в Redis.
- Rate limit сообщений.
- Проверка доступа к диалогу.
- События чата в RabbitMQ.
- Интеграция с file-service для вложений.

## Основные endpoints

- `POST /api/v1/dialogs`
- `GET /api/v1/dialogs`
- `GET /api/v1/dialogs/by-request/{requestId}`
- `PATCH /api/v1/dialogs/{dialogId}`
- `DELETE /api/v1/dialogs/{dialogId}`
- `GET /api/v1/dialogs/{dialogId}/messages`
- `POST /api/v1/dialogs/{dialogId}/read`
- `GET /api/v1/dialogs/{dialogId}/files`
- `WebSocket /ws/chat`

## Конфигурация

Основные переменные:

- `SERVER_PORT`
- `DATABASE_URL`
- `DB_SCHEMA`
- `REDIS_URL`
- `RABBITMQ_URL`
- `JWT_JWKS_URL`
- `CHAT_RATE_LIMIT_PER_MIN`
- `CHAT_MAX_MESSAGE_LENGTH`
- `APPOINTMENT_SERVICE_URL`
- `FILE_SERVICE_URL`
- `CHAT_ACCESS_ALLOW_ALL`
- `LOG_LEVEL`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build chat-service
```

Локально:

```powershell
uv sync --all-packages --all-extras
uv run uvicorn chat_service.main:app --reload --port 8085
```

## Тесты

```powershell
uv run pytest services/chat-service/tests_chat_service -q
```

WebSocket-тесты и интеграционные тесты требуют окружения. Часть тестов использует Testcontainers.

## Миграции

Единственная миграция `0001_initial.py` создаёт таблицы, enum-типы и индексы. Изменяющих операций нет.

## Связанные сервисы

- [appointment-service](../appointment-service/README.md) — диалоги по записям.
- [file-service](../file-service/README.md) — вложения.
- [notification-service](../notification-service/README.md) — уведомления.
- [video-service](../video_service/README.md) — звонки из чата.
- [api-gateway](../api-gateway/README.md) — маршрутизация HTTP и WebSocket.

## Ссылки

- [Корневой README](../../README.md)
