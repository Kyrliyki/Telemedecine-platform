class DomainError(Exception):
    code: str = "DOMAIN_ERROR"


class DialogNotFoundError(DomainError):
    code = "DIALOG_NOT_FOUND"


class NotAParticipantError(DomainError):
    code = "FORBIDDEN"


class DialogClosedError(DomainError):
    code = "DIALOG_CLOSED"


class ValidationError(DomainError):
    code = "VALIDATION_ERROR"


class RateLimitedError(DomainError):
    code = "RATE_LIMITED"


class RequestNotFoundError(DomainError):
    code = "REQUEST_NOT_FOUND"


class RequestServiceUnavailableError(DomainError):
    code = "REQUEST_SERVICE_UNAVAILABLE"


class EditWindowExpiredError(DomainError):
    code = "EDIT_WINDOW_EXPIRED"