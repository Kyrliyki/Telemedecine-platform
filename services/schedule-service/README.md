# schedule-service

Расписание врачей и доступность для записи: рабочие дни, интервалы приёма, исключения, временные слоты и резервирование.

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
| HTTP | 8084 |
| Management | 8094 |
| Схема | `schedule_service` |
| Пользователь БД | `schedule_svc` |
| Flyway | `src/main/resources/db/migration` |

## Ответственность

- Шаблоны рабочих часов по дням недели.
- Периоды действия расписания.
- Несколько интервалов в один день.
- Исключения и отпуска.
- Расчёт свободных слотов.
- Hold, подтверждение и освобождение слота.
- Проверка бронирования по рабочим часам.
- Административное управление расписанием.

## Основные endpoints

- `GET /api/v1/schedule/working-hours`
- `PUT /api/v1/schedule/working-hours`
- `GET /api/v1/schedule/doctors/{doctorId}/availability`
- `GET /api/v1/schedule/doctors/{doctorId}/next-available`
- `POST /api/v1/schedule/doctors/availability`
- `GET /api/v1/schedule/exceptions`
- `POST /api/v1/schedule/exceptions`
- `DELETE /api/v1/schedule/exceptions/{id}`
- `GET /api/v1/schedule/slots/{slotId}`
- `GET /api/v1/schedule/appointments/{appointmentId}/slot`
- `POST /internal/schedule/slots/hold`
- `POST /internal/schedule/slots/confirm`
- `POST /internal/schedule/slots/release`
- `GET /api/v1/admin/schedule/doctors/{doctorId}/working-hours`
- `PUT /api/v1/admin/schedule/doctors/{doctorId}/working-hours`
- `GET /api/v1/admin/schedule/doctors/{doctorId}/exceptions`
- `POST /api/v1/admin/schedule/doctors/{doctorId}/exceptions`
- `DELETE /api/v1/admin/schedule/doctors/{doctorId}/exceptions/{exceptionId}`
- `GET /api/v1/admin/schedule/doctors/{doctorId}/availability`

## Конфигурация

Основные переменные:

- `SERVER_PORT`, `MANAGEMENT_PORT`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA`
- `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`
- `SCHEDULE_MAX_RANGE_DAYS`
- `SCHEDULE_HOLD_MINUTES`
- `SCHEDULE_MAX_SLOTS_PER_RESPONSE`
- `SCHEDULE_SLOT_RETENTION`
- `SCHEDULE_LEAD_TIME`
- `SCHEDULE_MAX_ADVANCE`
- `SCHEDULE_USER_VALIDATION_ENABLED`
- `SCHEDULE_APPOINTMENT_RECONCILE_ENABLED`
- `APPOINTMENT_SERVICE_URL`
- `USER_SERVICE_URL`
- `INTERNAL_SERVICE_TOKEN`

Полный набор значений — в [.env.example](../../.env.example).

## Запуск

```powershell
docker compose up -d --build schedule-service
```

Локально:

```powershell
cd services/schedule-service
..\..\gradlew.bat bootRun
```

## Тесты

```powershell
cd services/schedule-service
..\..\gradlew.bat test
```

## Правила доступности

- Слоты строятся по `dayOfWeek`, `startTime`, `endTime`, `slotDurationMinutes` и `bufferMinutes`.
- Слоты вне окна `SCHEDULE_LEAD_TIME` и `SCHEDULE_MAX_ADVANCE` не выдаются.
- Исключения блокируют пересекающиеся слоты.
- Пустой набор рабочих часов означает отсутствие доступности.

## Миграции

Начальная миграция `V1__init_schedule_schema.sql` создаёт схему.

## Связанные сервисы

- [appointment-service](../appointment-service/README.md) — бронирование и hold.
- [user-service](../user-service/README.md) — проверка врача.
- [emr-service](../emr-service/README.md) — приём и медицинская запись.
- [api-gateway](../api-gateway/README.md) — маршрутизация.

## Ссылки

- [Корневой README](../../README.md)
