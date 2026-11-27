# file-service

Файлы чата и вложения: загрузка в MinIO/S3, список файлов, скачивание, удаление и события.

## Стек

- Python 3.14
- FastAPI
- SQLAlchemy 2 и asyncpg
- Alembic
- PostgreSQL
- MinIO/S3
- RabbitMQ
- Redis
- Testcontainers

## Порты и БД

| Параметр | Значение |
| --- | --- |
| HTTP | 8086 |
| Схема | `file_service` |
| Пользователь БД | `file_svc` |
| Alembic | `migrations/versions/0001_initial.py` |

## Ответственность

- Прямая multipart-загрузка файла.
- Двухфазная загрузка через presigned URL.
- Проверка доступа к чату через chat-service.
- Ограничение размера и типа файла.
- Хранение метаданных в PostgreSQL, байтов — в MinIO/S3.
- Список файлов чата.
- Скачивание и preview.
- Удаление файла.
- Outbox-события `file.uploaded` и `file.deleted`.
- Очистка истёкших файлов.

## Основные endpoints

- `POST /api/v1/chats/{chatId}/files`
- `POST /api/v1/chats/{chatId}/uploads`
- `POST /api/v1/files/{fileId}/complete`
- `GET /api/v1/chats/{chatId}/files`
- `GET /api/v1/files/{fileId}`
- `GET /api/v1/files/{fileId}/download`
- `DELETE /api/v1/files/{fileId}`
- `GET /api/v1/me`
- `GET /internal/files/{fileId}`

## Конфигурация

Основные переменные:

- `SERVER_PORT`
- `DATABASE_URL`
- `DB_SCHEMA`
- `REDIS_URL`
- `RABBITMQ_URL`
- `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`, `S3_REGION`
- `S3_PRESIGN_TTL_SECONDS`
- `MAX_FILE_SIZE_MB`
- `CHAT_SERVICE_URL`
- `CHAT_ACCESS_ALLOW_ALL`
- `JWT_JWKS_URL`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build file-service
```

Локально:

```powershell
uv sync --all-packages --all-extras
uv run uvicorn file_service.main:app --reload --port 8086
```

## Тесты

```powershell
uv run pytest services/file-service/tests_file_service -q
```

Интеграционные тесты MinIO/PostgreSQL используют Testcontainers и требуют Docker.

## Миграции

Единственная миграция `0001_initial.py` только создаёт таблицы и индексы.

## Связанные сервисы

- [chat-service](../chat-service/README.md) — проверка доступа к диалогу.
- [emr-service](../emr-service/README.md) — медицинские вложения.
- [api-gateway](../api-gateway/README.md) — маршрутизация.
- [notification-service](../notification-service/README.md) — события файлов.

## Ссылки

- [Корневой README](../../README.md)
