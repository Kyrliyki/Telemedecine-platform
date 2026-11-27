# appointment-service

Запись на приём: создание, подтверждение, перенос, отмена и завершение консультаций. Сервис проверяет роли и лид-тайм, резервирует слот в schedule-service и публикует события через outbox.

## Стек

- Python 3.14
- FastAPI
- SQLAlchemy 2 и asyncpg
- Alembic
- PostgreSQL
- Redis
- RabbitMQ
- httpx
- Testcontainers

## Порты и БД

| Параметр | Значение |
| --- | --- |
| HTTP | 8083 |
| Схема | `appointment_service` |
| Пользователь БД | `appointment_svc` |
| Alembic | `migrations/versions/0001_initial.py` |

## Ответственность

- Создание записи в статусе `requested`.
- Проверка участников через user-service.
- Пациент бронирует минимум за 1 час.
- Врач может создать запись не в прошлом.
- Администратор не ограничен по времени.
- Самозапись пациента к самому себе запрещена.
- Проверка пересечений и блокировка врача на время создания.
- Hold слота в schedule-service и release при ошибке.
- Подтверждение, перенос, отмена, завершение и no-show.
- Кеширование чтения в Redis.
- Публикация событий в RabbitMQ через outbox.

## Основные endpoints

- `POST /api/v1/appointments`
- `GET /api/v1/appointments`
- `GET /api/v1/appointments/{appointmentId}`
- `POST /api/v1/appointments/{appointmentId}/confirm`
- `POST /api/v1/appointments/{appointmentId}/reschedule`
- `POST /api/v1/appointments/{appointmentId}/cancel`
- `POST /api/v1/appointments/{appointmentId}/complete`
- `POST /api/v1/appointments/{appointmentId}/no-show`

Внутренние endpoints используются video/chat/emr и другими сервисами.

## Конфигурация

Основные переменные:

- `APP_PORT`
- `APP_DATABASE_URL` или `APP_DB_*`
- `APP_DB_SCHEMA`
- `APP_REDIS_URL`
- `APP_RABBITMQ_URL`
- `APP_SCHEDULE_SERVICE_URL`
- `APP_USER_SERVICE_URL`
- `APP_JWKS_URL`
- `APP_CORS_ORIGINS`
- `LOG_LEVEL`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build appointment-service
```

Локально:

```powershell
uv sync --all-packages --all-extras
uv run uvicorn appointment.main:app --reload --port 8083
```

Для локального запуска нужны PostgreSQL, Redis, RabbitMQ, schedule-service и user-service.

## Тесты

```powershell
uv run pytest services/appointment-service/tests_appointment_service -q
```

Интеграционные тесты требуют Docker.

## Миграции

Единственная миграция `0001_initial.py` создаёт таблицы и индексы. Она не изменяет и не удаляет данные.

## Связанные сервисы

- [schedule-service](../schedule-service/README.md) — слоты и доступность.
- [user-service](../user-service/README.md) — проверка врача и пациента.
- [chat-service](../chat-service/README.md) — чат по записи.
- [video-service](../video-service/README.md) — видеозвонок по записи.
- [emr-service](../emr-service/README.md) — медицинская запись по приёму.
- [api-gateway](../api-gateway/README.md) — маршрутизация.

## Ссылки

- [Корневой README](../../README.md)
