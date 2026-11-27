# frontend

SPA телемедицинской платформы: каталог врачей, календарь, запись, чат, видеозвонки, медицинские записи, профили и админка.

## Стек

- React 19
- TypeScript
- Vite 6
- React Router
- Tailwind CSS
- Radix UI
- React Hook Form + Zod
- LiveKit Client
- Lucide Icons

## Порты

| Режим | Порт |
| --- | ---: |
| Docker/nginx | 3000 |
| Vite dev | 5173 |

Vite проксирует `/api` и `/ws` на API Gateway.

## Запуск

```powershell
cd frontend
npm ci
npm run dev
```

Docker:

```powershell
docker compose up -d --build frontend
```

Переменные окружения:

- `VITE_GOOGLE_CLIENT_ID`
- `VITE_GOOGLE_REDIRECT_URI`
- `VITE_API_BASE_URL` — необязательно
- `VITE_PROXY_TARGET` — необязательно

Пример — в [.env.example](.env.example).

## Сборка и проверки

```powershell
npm run typecheck
npm run build
```

`npm run build` выполняет `tsc --noEmit` и production-сборку Vite.

## Основные разделы

- `/` — главная и ближайшие приёмы.
- `/doctors` — каталог проверенных врачей.
- `/doctors/:userId` — публичный профиль врача.
- `/calendar` — календарь и запись.
- `/history` — история приёмов.
- `/medical-records` — медицинские записи.
- `/profile` — личные данные и аккаунт.
- `/doctor/profile` — кабинет врача и график работы.
- `/admin/users`, `/admin/doctors`, `/admin/audit` — администрирование.

## Интеграции

- API Gateway: REST и WebSocket.
- LiveKit: видеозвонки.
- JWT access/refresh с автоматическим обновлением.
- Глобальные toast-уведомления.
- Глобальный видеозвонок с мини-версией на всех страницах.

## Связанные сервисы

- [api-gateway](../services/api-gateway/README.md)
- [auth-service](../services/auth-service/README.md)
- [user-service](../services/user-service/README.md)
- [appointment-service](../services/appointment-service/README.md)
- [schedule-service](../services/schedule-service/README.md)
- [chat-service](../services/chat-service/README.md)
- [file-service](../services/file-service/README.md)
- [video-service](../services/video_service/README.md)
- [notification-service](../services/notification-service/README.md)
- [emr-service](../services/emr-service/README.md)

## Ссылки

- [Корневой README](../README.md)
