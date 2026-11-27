"""Доменные ошибки. Presentation-слой маппит их в HTTP-коды."""

from __future__ import annotations


class DomainError(Exception):
    """Базовая ошибка домена."""

    code = "domain_error"
    http_status = 400

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


class ValidationError(DomainError):
    code = "validation_error"
    http_status = 422


class ForbiddenError(DomainError):
    code = "forbidden"
    http_status = 403


class CallNotFoundError(DomainError):
    code = "call_not_found"
    http_status = 404


class CallAlreadyExistsError(DomainError):
    code = "call_already_exists"
    http_status = 409


class CallEndedError(DomainError):
    code = "call_ended"
    http_status = 409


class AppointmentNotFoundError(DomainError):
    code = "appointment_not_found"
    http_status = 404


class AppointmentNotConfirmedError(DomainError):
    code = "appointment_not_confirmed"
    http_status = 409


class AppointmentServiceUnavailableError(DomainError):
    code = "appointment_service_unavailable"
    http_status = 503
