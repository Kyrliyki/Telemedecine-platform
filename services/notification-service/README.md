# notification-service

Уведомления платформы: приём событий, email через SMTP, in-app уведомления, контакты, ретраи и DLQ.

## Стек

- Python 3.14
- FastAPI
- SQLAlchemy 2 и asyncpg
- Alembic
- PostgreSQL
- RabbitMQ
- SMTP
- Redis
- Testcontainers

## Порты и БД

| Параметр | Значение |
| --- | --- |
| HTTP | 8088 |
| Схема | `notification_service` |
| Пользователь БД | `notification_svc` |
| Alembic | `migrations/versions/0001_initial.py` |

## Ответственность

- Приём доменных событий из RabbitMQ.
- Формирование email и in-app уведомлений.
- SMTP-отправка.
- Хранение контактов пользователей.
- Дедупликация по `dedup_key`.
- Ретраи и next attempt.
- In-app unread/read.
- Список уведомлений пользователя.
- Outbox-события.
- Dev-endpoints для ручной проверки.

## Основные endpoints

- `GET /api/v1/notifications`
- `GET /api/v1/notifications/unread-count`
- `GET /api/v1/notifications/{id}`
- `POST /api/v1/notifications/{id}/read`
- `POST /api/v1/notifications/read-all`
- `POST /api/v1/dev/notifications` — при `DEV_ENDPOINTS_ENABLED=true`
- `POST /api/v1/dev/contacts` — при `DEV_ENDPOINTS_ENABLED=true`
- `POST /api/v1/dev/dispatch` — при `DEV_ENDPOINTS_ENABLED=true`

## Конфигурация

Основные переменные:

- `SERVER_PORT`
- `DATABASE_URL`
- `DB_SCHEMA`
- `REDIS_URL`
- `RABBITMQ_URL`
- `EMAIL_ENABLED`
- `SMTP_HOST`, `SMTP_PORT`
- `SMTP_USERNAME`, `SMTP_PASSWORD`
- `SMTP_FROM`, `SMTP_FROM_NAME`
- `SMTP_STARTTLS`, `SMTP_USE_TLS`
- `SMTP_TIMEOUT_SECONDS`
- `JWT_JWKS_URL`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build notification-service
```

Локально:

```powershell
uv sync --all-packages --all-extras
uv run uvicorn notification_service.main:app --reload --port 8088
```

## Тесты

```powershell
uv run pytest services/notification-service/tests_notification_service -q
```

Интеграционные тесты PostgreSQL используют Testcontainers.

## Миграции

Единственная миграция `0001_initial.py` создаёт таблицы, индексы и служебные объекты. Изменяющих операций нет.

## Связанные сервисы

- [auth-service](../auth-service/README.md) — события регистрации и приглашений.
- [user-service](../user-service/README.md) — контакты и роли.
- [appointment-service](../appointment-service/README.md) — события записей.
- [chat-service](../chat-service/README.md) — события чата.
- [api-gateway](../api-gateway/README.md) — маршрутизация.

## Ссылки

- [Корневой README](../../README.md)
