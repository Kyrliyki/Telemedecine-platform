# auth-service

Аутентификация и авторизация платформы: регистрация, вход, refresh-токены, приглашения, подтверждение email, Google OAuth, JWKS и административное управление пользователями.

## Стек

- Java 25
- Spring Boot 4.1.1
- Spring Security
- PostgreSQL + Flyway
- Redis
- RabbitMQ
- RS256/JWKS
- SpringDoc/OpenAPI

## Порты и БД

| Параметр | Значение |
| --- | --- |
| HTTP | 8080 |
| Management | 8091 |
| Схема | `auth_service` |
| Пользователь БД | `auth_svc` |
| Flyway | `src/main/resources/db/migration` |

## Ответственность

- Регистрация и вход по email/паролю.
- Хеширование паролей и защита учётных записей.
- Access/refresh-токены и ротация refresh-токенов.
- Отзыв токенов и blacklist в Redis.
- Подтверждение email.
- Приглашения пользователей и принятие приглашений.
- Вход и привязка Google OAuth.
- Публикация JWKS для остальных сервисов.
- Роли, блокировка и разблокировка пользователей.
- Аудит входов и административных действий.

## Основные endpoints

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/verify-email`
- `POST /api/v1/auth/accept-invitation`
- `POST /api/v1/auth/oauth/google`
- `POST /api/v1/auth/oauth/confirm`
- `GET /api/v1/auth/oauth/google/callback`
- `GET /oauth2/jwks`
- `POST /api/v1/admin/users/invite`
- `POST /api/v1/admin/users/{userId}/roles`
- `DELETE /api/v1/admin/users/{userId}/roles/{role}`
- `POST /api/v1/admin/users/{userId}/block`
- `POST /api/v1/admin/users/{userId}/unblock`
- `GET /api/v1/admin/auth/audit`
- `GET /internal/users`

## Конфигурация

Основные переменные:

- `SERVER_PORT`, `MANAGEMENT_PORT`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA`
- `REDIS_HOST`, `REDIS_PORT`
- `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`
- `JWT_ISSUER`, `JWT_AUDIENCE`, `AUTH_ACCESS_TTL`
- `BOOTSTRAP_ADMIN_ENABLED`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `INTERNAL_SERVICE_TOKEN`
- `RECONCILE_INITIAL_DELAY`, `RECONCILE_INTERVAL`
- `PROCESSED_EVENTS_RETENTION`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build auth-service
```

Локально:

```powershell
cd services/auth-service
..\..\gradlew.bat bootRun
```

Для локального запуска нужны PostgreSQL, Redis и RabbitMQ. Параметры подключения задаются переменными окружения.

## Тесты

```powershell
cd services/auth-service
..\..\gradlew.bat test
```

## Миграции

Начальная миграция `V1__init_auth_schema.sql` создаёт схему с нуля. Она не содержит `ALTER`, `DROP` и изменений данных.

## Связанные сервисы

- [api-gateway](../api-gateway/README.md) — маршрутизация и проверка JWT.
- [user-service](../user-service/README.md) — профиль пользователя и роли.
- [notification-service](../notification-service/README.md) — письма и приглашения.

## Ссылки

- [Корневой README](../../README.md)
