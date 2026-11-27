"""Domain errors. They are transport agnostic; the HTTP mapping lives in presentation."""

from __future__ import annotations


class DomainError(Exception):
    """Base class for all expected business errors."""

    code = "domain_error"

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


class ValidationError(DomainError):
    code = "validation_error"


class FileTooLargeError(ValidationError):
    code = "file_too_large"


class NotFoundError(DomainError):
    code = "not_found"


class ForbiddenError(DomainError):
    code = "forbidden"


class ConflictError(DomainError):
    code = "conflict"


class AuthenticationError(DomainError):
    code = "unauthenticated"


class StorageUnavailableError(DomainError):
    code = "storage_unavailable"


class AccessCheckUnavailableError(DomainError):
    code = "access_check_unavailable"
