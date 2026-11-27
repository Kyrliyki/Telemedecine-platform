"""VideoService — микросервис видеозвонков на базе LiveKit.

Пакет построен по принципам Clean Architecture:

* ``domain``         — сущности, value objects, доменные события и ошибки;
* ``application``    — use cases, DTO и порты (интерфейсы) для инфраструктуры;
* ``infrastructure`` — адаптеры LiveKit, PostgreSQL, логирования событий;
* ``presentation``   — HTTP API (FastAPI), схемы, авторизация, middleware.

Зависимости направлены внутрь: ``presentation -> application -> domain``.
"""

__version__ = "0.1.0"
