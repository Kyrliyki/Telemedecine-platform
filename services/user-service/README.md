# user-service

Профили пользователей, роли и каталог врачей. Сервис хранит зеркало идентичности из auth-service, проверяет врачей и отдаёт публичные профили.

## Стек

- Java 25
- Spring Boot 4.1.1
- Spring Data JPA
- PostgreSQL + Flyway
- RabbitMQ
- SpringDoc/OpenAPI

## Порты и БД

| Параметр | Значение |
| --- | --- |
| HTTP | 8082 |
| Management | 8093 |
| Схема | `user_service` |
| Пользователь БД | `user_svc` |
| Flyway | `src/main/resources/db/migration` |

## Ответственность

- Профиль пользователя: имя, фамилия, email, статус, роли.
- Синхронизация ролей и статусов из auth-service.
- Профиль врача: специализация, стаж, лицензия, био.
- Верификация врача: `PENDING`, `VERIFIED`, `REJECTED`.
- Публичный каталог только проверенных врачей.
- Флаг `acceptingPatients` и видимость врача в каталоге.
- Внутренний справочник пользователей для schedule и appointment.

## Основные endpoints

- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me`
- `GET /api/v1/users/{userId}`
- `GET /api/v1/users/me/doctor-profile`
- `PUT /api/v1/users/me/doctor-profile`
- `GET /api/v1/users/doctors`
- `GET /api/v1/users/doctors/{userId}`
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/users/doctors`
- `POST /api/v1/admin/users/doctors/{userId}/approve`
- `POST /api/v1/admin/users/doctors/{userId}/reject`
- `PUT /api/v1/admin/users/doctors/{userId}`
- `GET /internal/users/{userId}`

## Конфигурация

Основные переменные:

- `SERVER_PORT`, `MANAGEMENT_PORT`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA`
- `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`
- `INTERNAL_SERVICE_TOKEN`
- `OUTBOX_ENABLED`
- `PROCESSED_EVENTS_RETENTION`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build user-service
```

Локально:

```powershell
cd services/user-service
..\..\gradlew.bat bootRun
```

## Тесты

```powershell
cd services/user-service
..\..\gradlew.bat test
```

## Поведение врачей

- Публичный каталог отдаёт только `VERIFIED` врачей с `acceptingPatients = true`.
- Отклонение врача скрывает профиль.
- Повторное одобрение снова включает `acceptingPatients`.
- Врач с ролями `DOCTOR + PATIENT` остаётся в каталоге и может принимать пациентов.

## Миграции

Начальная миграция `V1__init_user_schema.sql` создаёт схему с нуля и не содержит изменяющих операций.

## Связанные сервисы

- [auth-service](../auth-service/README.md) — источник ролей и статусов.
- [schedule-service](../schedule-service/README.md) — рабочие часы врача.
- [appointment-service](../appointment-service/README.md) — проверка участников записи.
- [api-gateway](../api-gateway/README.md) — маршрутизация.

## Ссылки

- [Корневой README](../../README.md)
