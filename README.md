# Telemed

Телемедицинская платформа: запись на приём, расписание врачей, видеозвонки, чат, медицинские карты, уведомления и администрирование.

## Архитектура

- Frontend — React SPA, работает через API Gateway.
- APIGateway — единая точка входа: маршрутизация, проверка JWT и CORS.
- Java-сервисы — `auth-service`, `user-service`, `schedule-service`, `emr-service`.
- Python-сервисы — `appointment-service`, `chat-service`, `file-service`, `video-service`, `notification-service`.
- PostgreSQL — отдельная схема у каждого сервиса.
- Redis — кеш, presence, rate limit и служебные состояния.
- RabbitMQ — доменные события и outbox-паттерн.
- MinIO/S3 — файлы чата и вложения.
- LiveKit — видеозвонки.
- Prometheus, Grafana, Loki, Alloy — наблюдаемость.

## Сервисы

| Сервис | Стек | HTTP | Management | Схема БД | Пользователь БД |
| --- | --- | ---: | ---: | --- | --- |
| [api-gateway](services/api-gateway/README.md) | Java 25, Spring Cloud Gateway | 8081 | 8091 | — | — |
| [auth-service](services/auth-service/README.md) | Java 25, Spring Boot | 8080 | 8091 | `auth_service` | `auth_svc` |
| [user-service](services/user-service/README.md) | Java 25, Spring Boot | 8082 | 8093 | `user_service` | `user_svc` |
| [appointment-service](services/appointment-service/README.md) | Python, FastAPI | 8083 | — | `appointment_service` | `appointment_svc` |
| [schedule-service](services/schedule-service/README.md) | Java 25, Spring Boot | 8084 | 8094 | `schedule_service` | `schedule_svc` |
| [chat-service](services/chat-service/README.md) | Python, FastAPI | 8085 | — | `chat_service` | `chat_svc` |
| [file-service](services/file-service/README.md) | Python, FastAPI | 8086 | — | `file_service` | `file_svc` |
| [video-service](services/video-service/README.md) | Python, FastAPI | 8087 | — | `video_service` | `video_svc` |
| [notification-service](services/notification-service/README.md) | Python, FastAPI | 8088 | — | `notification_service` | `notification_svc` |
| [emr-service](services/emr-service/README.md) | Java 25, Spring Boot | 8089 | 8095 | `emr_service` | `emr_svc` |
| [frontend](frontend/README.md) | React 19, Vite, Tailwind | 3000 / 5173 | — | — | — |

Порты в таблице — внутри Docker-сети. Наружу опубликованы только:

- `3000` — frontend;
- `8081` — API Gateway;
- `8092` — management API Gateway.

Остальные HTTP/Management-порты доступны только изнутри compose-сети.

## Быстрый старт

Требуются Docker Desktop и Docker Compose.

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

После запуска:

- UI: http://localhost:3000
- API Gateway: http://localhost:8081
- Grafana: http://localhost:3001
- Prometheus: http://localhost:9090
- RabbitMQ Management: http://localhost:15672
- MinIO Console: http://localhost:9001
- LiveKit WebSocket: ws://localhost:7880

Данные администратора по умолчанию задаются в `.env`:

```text
BOOTSTRAP_ADMIN_EMAIL=admin@telemedicine.local
BOOTSTRAP_ADMIN_PASSWORD=Root123456789!
```

Перед публикацией окружения замените пароли, JWT-секреты, `INTERNAL_SERVICE_TOKEN` и ключи LiveKit.

## Локальная разработка

### Python-сервисы

```powershell
uv sync --all-packages --all-extras
uv run pytest -q
```

Запуск отдельного сервиса:

```powershell
uv run uvicorn appointment.main:app --reload --port 8083
```

Нужные переменные окружения перечислены в [.env.example](.env.example) и в README соответствующего сервиса.

### Java-сервисы

```powershell
cd services/auth-service
..\..\gradlew.bat bootRun
```

### Frontend

```powershell
cd frontend
npm ci
npm run dev
```

Dev-сервер работает на http://localhost:5173 и проксирует `/api` и `/ws` на API Gateway.

## Тесты

```powershell
uv run pytest -q
```

```powershell
cd services/auth-service
..\..\gradlew.bat test
```

```powershell
cd frontend
npm run typecheck
npm run build
```

Интеграционные тесты Python-сервисов используют Testcontainers и требуют доступный Docker.

## Миграции

- миграции создают таблицы, индексы и другие объекты схемы;
- Java-сервисы используют Flyway, Python-сервисы — Alembic;
- у каждого сервиса с БД есть одна начальная миграция;
- изменения схемы до релиза вносятся в начальную миграцию, а не отдельными изменяющими файлами.

## События и отказоустойчивость

Сервисы публикуют доменные события через outbox, а  `chat` и `video` публикуют интеграционные
события напрямую в RabbitMQ. RabbitMQ используется для асинхронной интеграции, повторной доставки и DLQ. API Gateway содержит fallback-ответы для недоступных сервисов, а сервисы предоставляют liveness/readiness-пробы и метрики Prometheus.

## Структура репозитория

- `services/` — микросервисы.
- `frontend/` — SPA.
- `libs/java/` — общие Java-библиотеки.
- `libs/python/` — общие Python-библиотеки.
- `infra/` — конфигурации PostgreSQL, LiveKit и наблюдаемости.
- `.github/workflows/` — CI/CD для каждого сервиса.
- `build-logic/`, `gradle/` — сборка Java-сервисов.
- `docker-compose.yml` — локальный стек.

## CI/CD

Отдельный workflow на каждый сервис и общие reusable workflow для Java, Python и frontend: [.github/workflows/](.github/workflows/). Pipeline выполняет линтеры, тесты, сборку Docker-образа и demo-запуск черех docker compose без внешнего сервера.


## Документация сервисов

- [api-gateway](services/api-gateway/README.md)
- [auth-service](services/auth-service/README.md)
- [user-service](services/user-service/README.md)
- [appointment-service](services/appointment-service/README.md)
- [schedule-service](services/schedule-service/README.md)
- [chat-service](services/chat-service/README.md)
- [file-service](services/file-service/README.md)
- [video-service](services/video-service/README.md)
- [notification-service](services/notification-service/README.md)
- [emr-service](services/emr-service/README.md)
- [frontend](frontend/README.md)
