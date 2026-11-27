# emr-service

Электронные медицинские записи: приёмы, диагнозы, назначения, жизненные показатели, вложения, ревизии, legal hold и хранение.

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
| HTTP | 8089 |
| Management | 8095 |
| Схема | `emr_service` |
| Пользователь БД | `emr_svc` |
| Flyway | `src/main/resources/db/migration` |

## Ответственность

- Создание медицинской записи по приёму.
- Черновики, финализация и поправки.
- Диагноз и рекомендации.
- Рецепты и назначения.
- Витальные показатели.
- Вложения из file-service.
- Ревизии и история изменений.
- Legal hold и retention.
- Аудит доступа и действий.
- Экспорт записей пациента.
- Очистка истёкших записей.

## Основные endpoints

- `POST /api/v1/emr/records`
- `GET /api/v1/emr/records/{id}`
- `PATCH /api/v1/emr/records/{id}`
- `POST /api/v1/emr/records/{id}/finalize`
- `POST /api/v1/emr/records/{id}/amend`
- `PUT /api/v1/emr/records/{id}/prescriptions`
- `PUT /api/v1/emr/records/{id}/vitals`
- `POST /api/v1/emr/records/{id}/attachments`
- `GET /api/v1/emr/records/{id}/revisions`
- `GET /api/v1/emr/records/{id}/revisions/{revisionNumber}`
- `GET /api/v1/emr/patients/{patientId}/records`
- `GET /api/v1/emr/me/records`
- `POST /api/v1/admin/emr/records/{id}/legal-hold`
- `POST /api/v1/admin/emr/records/{id}/retention`
- `GET /api/v1/admin/emr/patients/{patientId}/export`
- `POST /api/v1/admin/emr/purge-expired`
- `GET /api/v1/admin/emr/records/{id}/audit`

## Конфигурация

Основные переменные:

- `SERVER_PORT`, `MANAGEMENT_PORT`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA`
- `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`
- `EMR_PAGE_SIZE_MAX`
- `EMR_REQUIRE_APPOINTMENT`
- `EMR_RETENTION_DAYS`
- `EMR_APPOINTMENT_VALIDATION_ENABLED`
- `EMR_FILE_VALIDATION_ENABLED`
- `APPOINTMENT_SERVICE_URL`
- `FILE_SERVICE_URL`
- `INTERNAL_SERVICE_TOKEN`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build emr-service
```

Локально:

```powershell
cd services/emr-service
..\..\gradlew.bat bootRun
```

## Тесты

```powershell
cd services/emr-service
..\..\gradlew.bat test
```

## Миграции

Начальная миграция `V1__init_emr_schema.sql` создаёт схему с нуля и не содержит изменяющих операций.

## Связанные сервисы

- [appointment-service](../appointment-service/README.md) — источник приёмов.
- [file-service](../file-service/README.md) — вложения.
- [user-service](../user-service/README.md) — проверка участников.
- [api-gateway](../api-gateway/README.md) — маршрутизация.

## Ссылки

- [Корневой README](../../README.md)
