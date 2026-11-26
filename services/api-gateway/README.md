# api-gateway

Единая точка входа для frontend и внешних клиентов. Маршрутизирует HTTP и WebSocket-запросы, проверяет JWT, применяет CORS и отдаёт fallback-ответы при недоступности сервисов.

## Стек

- Java 25
- Spring Boot 4.1.1
- Spring Cloud Gateway Server WebFlux
- Redis для blacklist/revocation
- SpringDoc/OpenAPI

## Порты

| Назначение | Контейнер | Хост |
| --- | ---: | ---: |
| HTTP API | 8081 | 8081 |
| Management/Actuator | 8091 | 8092 |

## Маршруты

| Путь | Сервис |
| --- | --- |
| `/api/v1/auth/**`, `/oauth2/jwks`, `/api/v1/admin/**` | auth-service |
| `/api/v1/users/**`, `/api/v1/admin/users/doctors/**` | user-service |
| `/api/v1/appointments/**` | appointment-service |
| `/api/v1/schedule/**`, `/api/v1/admin/schedule/**` | schedule-service |
| `/api/v1/chat/**` | chat-service |
| `/ws/chat/**` | chat-service |
| `/api/v1/chats/**`, `/api/v1/files/**` | file-service |
| `/api/v1/video/**` | video-service |
| `/api/v1/notifications/**` | notification-service |
| `/api/v1/emr/**`, `/api/v1/admin/emr/**` | emr-service |
| `/v3/api-docs/{service}` | OpenAPI соответствующего сервиса |

## Конфигурация

Основные переменные:

- `SERVER_PORT`, `MANAGEMENT_PORT`
- `AUTH_SERVICE_URL`, `USER_SERVICE_URL`, `APPOINTMENT_SERVICE_URL`
- `SCHEDULE_SERVICE_URL`, `CHAT_SERVICE_URL`, `FILE_SERVICE_URL`
- `VIDEO_SERVICE_URL`, `NOTIFICATION_SERVICE_URL`, `EMR_SERVICE_URL`
- `JWK_SET_URI`, `JWT_ISSUER`, `JWT_AUDIENCE`
- `INTERNAL_SERVICE_TOKEN`
- `GATEWAY_CONNECT_TIMEOUT_MS`, `GATEWAY_RESPONSE_TIMEOUT`
- `SPRINGDOC_ENABLED`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

Через Docker Compose из корня репозитория:

```powershell
docker compose up -d --build api-gateway
```

## Тесты

```powershell
cd services/api-gateway
..\..\gradlew.bat test
```

## Особенности

- Gateway проверяет access token по JWKS auth-service.
- Для внутренних вызовов используется `X-Internal-Token`.
- При недоступности сервиса клиент получает контролируемый fallback-ответ.
- Административные endpoints DLQ доступны через `/api/v1/admin/dlq`.

## Ссылки

- [Корневой README](../../README.md)
- [auth-service](../auth-service/README.md)
- [user-service](../user-service/README.md)
- [appointment-service](../appointment-service/README.md)
- [schedule-service](../schedule-service/README.md)
- [chat-service](../chat-service/README.md)
- [file-service](../file-service/README.md)
- [video-service](../video_service/README.md)
- [notification-service](../notification-service/README.md)
- [emr-service](../emr-service/README.md)
- [frontend](../../frontend/README.md)
