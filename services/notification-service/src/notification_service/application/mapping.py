"""Map integration events to notification drafts.

This module is deliberately free of I/O. The use case combines it with the
contact projection (email addresses) and the repository.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any
from uuid import UUID

from common_events import (
    AppointmentCancelledPayload,
    AppointmentCompletedPayload,
    AppointmentConfirmedPayload,
    AppointmentCreatedPayload,
    AppointmentEventTypes,
    AppointmentNoShowPayload,
    AppointmentRescheduledPayload,
    AuthEventTypes,
    DoctorRejectedPayload,
    DoctorVerifiedPayload,
    EmailVerificationRequestedPayload,
    EventEnvelope,
    OAuthLinkConfirmationRequestedPayload,
    PasswordChangedPayload,
    UserBlockedPayload,
    UserCreatedByAdminPayload,
    UserEmailChangedPayload,
    UserEmailVerifiedPayload,
    UserEventTypes,
    UserInvitedPayload,
    UserProfileUpdatedPayload,
    UserRegisteredPayload,
    UserRoleChangedPayload,
    UserUnblockedPayload,
    VideoEventTypes,
    VideoSessionCreatedPayload,
    VideoSessionEndedPayload,
)

from notification_service.domain.entities import Contact, NotificationDraft
from notification_service.domain.enums import NotificationChannel, NotificationKind

LEGACY_APPOINTMENT_EVENTS = {
    "appointment.requested": NotificationKind.APPOINTMENT_CREATED,
    "appointment.confirmed": NotificationKind.APPOINTMENT_CONFIRMED,
    "appointment.rescheduled": NotificationKind.APPOINTMENT_RESCHEDULED,
    "appointment.cancelled": NotificationKind.APPOINTMENT_CANCELLED,
    "appointment.completed": NotificationKind.APPOINTMENT_COMPLETED,
    "appointment.no_show": NotificationKind.APPOINTMENT_NO_SHOW,
}

LEGACY_VIDEO_EVENTS = {
    "video.call.created": NotificationKind.VIDEO_CALL_STARTED,
    "video.call.ended": NotificationKind.VIDEO_CALL_ENDED,
}

def _as_uuid(value: Any) -> UUID | None:
    try:
        return UUID(str(value))
    except (TypeError, ValueError, AttributeError):
        return None

def _fmt(value: Any) -> str:
    if value is None:
        return "—"
    if isinstance(value, str):
        try:
            value = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return value
    if isinstance(value, datetime):
        return value.strftime("%d.%m.%Y %H:%M UTC")
    return str(value)

def _json_safe(value: Any) -> Any:
    if isinstance(value, (datetime, UUID)):
        return value.isoformat() if isinstance(value, datetime) else str(value)
    if isinstance(value, dict):
        return {key: _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    return value

def _draft(
    envelope: EventEnvelope,
    *,
    user_id: str | None,
    kind: NotificationKind,
    channel: NotificationChannel,
    title: str,
    body: str,
    payload: dict[str, Any],
    email_enabled: bool,
    in_app_enabled: bool,
    recipient_email: str | None = None,
) -> NotificationDraft | None:
    if not user_id:
        return None
    if channel is NotificationChannel.EMAIL and not email_enabled:
        return None
    if channel is NotificationChannel.IN_APP and not in_app_enabled:
        return None

    dedup_key = f"{envelope.event_id}:{user_id}:{channel.value}:{kind.value}"
    return NotificationDraft(
        user_id=user_id,
        kind=kind.value,
        channel=channel,
        title=title,
        body=body,
        payload=_json_safe(payload),
        recipient_email=recipient_email,
        dedup_key=dedup_key,
        correlation_id=envelope.correlation_id,
    )

def _in_app(
    envelope: EventEnvelope,
    *,
    user_id: str | None,
    kind: NotificationKind,
    title: str,
    body: str,
    payload: dict[str, Any],
    in_app_enabled: bool,
) -> NotificationDraft | None:
    return _draft(
        envelope,
        user_id=user_id,
        kind=kind,
        channel=NotificationChannel.IN_APP,
        title=title,
        body=body,
        payload=payload,
        email_enabled=False,
        in_app_enabled=in_app_enabled,
    )

def _email(
    envelope: EventEnvelope,
    *,
    user_id: str | None,
    kind: NotificationKind,
    title: str,
    body: str,
    payload: dict[str, Any],
    email_enabled: bool,
    recipient_email: str | None = None,
) -> NotificationDraft | None:
    return _draft(
        envelope,
        user_id=user_id,
        kind=kind,
        channel=NotificationChannel.EMAIL,
        title=title,
        body=body,
        payload=payload,
        email_enabled=email_enabled,
        in_app_enabled=False,
        recipient_email=recipient_email,
    )

def contact_from_event(envelope: EventEnvelope) -> Contact | None:
    """Update the local email projection from auth events that carry an email."""

    event_type = envelope.event_type
    payload = envelope.payload

    if event_type == AuthEventTypes.USER_REGISTERED:
        data = UserRegisteredPayload.model_validate(payload)
        return Contact(user_id=str(data.user_id), email=str(data.email))
    if event_type == AuthEventTypes.USER_CREATED_BY_ADMIN:
        data = UserCreatedByAdminPayload.model_validate(payload)
        return Contact(user_id=str(data.user_id), email=str(data.email))
    if event_type == AuthEventTypes.USER_INVITED:
        data = UserInvitedPayload.model_validate(payload)
        return Contact(user_id=str(data.user_id), email=str(data.email))
    if event_type == AuthEventTypes.USER_EMAIL_VERIFIED:
        data = UserEmailVerifiedPayload.model_validate(payload)
        return Contact(user_id=str(data.user_id), email=str(data.email))
    if event_type in {
        AuthEventTypes.USER_EMAIL_VERIFICATION_REQUESTED,
        AuthEventTypes.OAUTH_LINK_CONFIRMATION_REQUESTED,
    }:
        user_id = payload.get("userId") or payload.get("user_id")
        email = payload.get("email")
        if user_id and email:
            return Contact(user_id=str(user_id), email=str(email))
    if event_type == AuthEventTypes.USER_EMAIL_CHANGED:
        data = UserEmailChangedPayload.model_validate(payload)
        return Contact(user_id=str(data.user_id), email=str(data.new_email))
    if event_type in {
        UserEventTypes.PROFILE_UPDATED,
        UserEventTypes.DOCTOR_VERIFIED,
        UserEventTypes.DOCTOR_REJECTED,
    }:
        user_id = payload.get("userId") or payload.get("user_id")
        email = payload.get("email")
        if user_id and email:
            return Contact(user_id=str(user_id), email=str(email))
    return None

def build_drafts(
    envelope: EventEnvelope,
    *,
    frontend_url: str,
    email_enabled: bool,
    in_app_enabled: bool,
) -> list[NotificationDraft]:
    """Return all notification drafts for one integration event."""

    drafts: list[NotificationDraft | None] = []
    event_type = envelope.event_type

    
    if event_type == AuthEventTypes.USER_REGISTERED:
        data = UserRegisteredPayload.model_validate(envelope.payload)
        drafts += [
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_WELCOME,
                title="Добро пожаловать в Telemed",
                body=(
                    "Аккаунт создан. Записывайтесь на приёмы, общайтесь в чате "
                    "и подключайтесь к видеозвонкам."
                ),
                payload={"userId": str(data.user_id)},
                in_app_enabled=in_app_enabled,
            ),
            _email(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_WELCOME,
                title="Добро пожаловать в Telemed",
                body="Здравствуйте! Ваш аккаунт Telemed создан.",
                payload={"userId": str(data.user_id)},
                email_enabled=email_enabled,
                recipient_email=data.email,
            ),
        ]
    elif event_type == AuthEventTypes.USER_EMAIL_VERIFICATION_REQUESTED:
        data = EmailVerificationRequestedPayload.model_validate(envelope.payload)
        link = f"{frontend_url.rstrip('/')}/verify-email?token={data.raw_token}"
        drafts.append(
            _email(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_EMAIL_VERIFICATION,
                title="Подтвердите email",
                body=(
                    f"Подтвердите адрес электронной почты: {link}\n"
                    f"Ссылка действует до {_fmt(data.expires_at)}."
                ),
                payload={"userId": str(data.user_id), "expiresAt": data.expires_at.isoformat()},
                email_enabled=email_enabled,
                recipient_email=data.email,
            ),
        )
    elif event_type == AuthEventTypes.OAUTH_LINK_CONFIRMATION_REQUESTED:
        data = OAuthLinkConfirmationRequestedPayload.model_validate(envelope.payload)
        link = (
            f"{frontend_url.rstrip('/')}/oauth/confirm"
            f"?token={data.raw_token}&provider={data.provider}"
        )
        drafts.append(
            _email(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_OAUTH_LINK,
                title="Подтвердите привязку аккаунта",
                body=(
                    f"Подтвердите привязку {data.provider} к аккаунту Telemed: {link}\n"
                    f"Ссылка действует до {_fmt(data.expires_at)}."
                ),
                payload={
                    "userId": str(data.user_id),
                    "provider": data.provider,
                    "expiresAt": data.expires_at.isoformat(),
                },
                email_enabled=email_enabled,
                recipient_email=data.email,
            ),
        )
    elif event_type == AuthEventTypes.USER_BLOCKED:
        data = UserBlockedPayload.model_validate(envelope.payload)
        reason = f" Причина: {data.reason}." if data.reason else ""
        drafts += [
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_BLOCKED,
                title="Аккаунт заблокирован",
                body=f"Доступ к аккаунту ограничен.{reason}",
                payload={"userId": str(data.user_id), "reason": data.reason},
                in_app_enabled=in_app_enabled,
            ),
            _email(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_BLOCKED,
                title="Аккаунт Telemed заблокирован",
                body=f"Доступ к аккаунту ограничен.{reason}",
                payload={"userId": str(data.user_id), "reason": data.reason},
                email_enabled=email_enabled,
            ),
        ]
    elif event_type == AuthEventTypes.USER_UNBLOCKED:
        data = UserUnblockedPayload.model_validate(envelope.payload)
        drafts += [
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_UNBLOCKED,
                title="Аккаунт разблокирован",
                body="Доступ к аккаунту восстановлен.",
                payload={"userId": str(data.user_id)},
                in_app_enabled=in_app_enabled,
            ),
            _email(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_UNBLOCKED,
                title="Аккаунт Telemed разблокирован",
                body="Доступ к аккаунту восстановлен.",
                payload={"userId": str(data.user_id)},
                email_enabled=email_enabled,
            ),
        ]
    elif event_type == AuthEventTypes.USER_ROLE_CHANGED:
        data = UserRoleChangedPayload.model_validate(envelope.payload)
        drafts.append(
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_ROLE_CHANGED,
                title="Роли обновлены",
                body=f"Ваши роли: {', '.join(data.new_roles) or '—'}.",
                payload={"userId": str(data.user_id), "roles": data.new_roles},
                in_app_enabled=in_app_enabled,
            ),
        )
    elif event_type == AuthEventTypes.USER_PASSWORD_CHANGED:
        data = PasswordChangedPayload.model_validate(envelope.payload)
        drafts += [
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_PASSWORD_CHANGED,
                title="Пароль изменён",
                body="Если это были не вы, срочно свяжитесь с поддержкой.",
                payload={"userId": str(data.user_id)},
                in_app_enabled=in_app_enabled,
            ),
            _email(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_PASSWORD_CHANGED,
                title="Пароль Telemed изменён",
                body=(
                    "Пароль вашего аккаунта был изменён. "
                    "Если это были не вы, свяжитесь с поддержкой."
                ),
                payload={"userId": str(data.user_id)},
                email_enabled=email_enabled,
            ),
        ]
    elif event_type == AuthEventTypes.USER_EMAIL_VERIFIED:
        data = UserEmailVerifiedPayload.model_validate(envelope.payload)
        drafts.append(
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_EMAIL_VERIFIED,
                title="Email подтверждён",
                body=f"Адрес {data.email} успешно подтверждён.",
                payload={"userId": str(data.user_id), "email": data.email},
                in_app_enabled=in_app_enabled,
            ),
        )
    elif event_type == AuthEventTypes.USER_EMAIL_CHANGED:
        data = UserEmailChangedPayload.model_validate(envelope.payload)
        drafts.append(
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_EMAIL_CHANGED,
                title="Email изменён",
                body=f"Новый адрес: {data.new_email}.",
                payload={"userId": str(data.user_id), "email": data.new_email},
                in_app_enabled=in_app_enabled,
            ),
        )
    elif event_type == AuthEventTypes.USER_INVITED:
        data = UserInvitedPayload.model_validate(envelope.payload)
        login_url = f"{frontend_url.rstrip('/')}/login"
        if data.raw_token:
            invite_url = (
                f"{frontend_url.rstrip('/')}/accept-invitation?token={data.raw_token}"
            )
            body = f"Примите приглашение в Telemed: {invite_url}"
        else:
            body = f"Войдите в систему: {login_url}"
        drafts += [
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_INVITED,
                title="Вас пригласили в Telemed",
                body=body,
                payload={"userId": str(data.user_id)},
                in_app_enabled=in_app_enabled,
            ),
            _email(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_INVITED,
                title="Приглашение в Telemed",
                body=f"Вас пригласили в Telemed. {body}",
                payload={"userId": str(data.user_id)},
                email_enabled=email_enabled,
                recipient_email=data.email,
            ),
        ]
    elif event_type == AuthEventTypes.USER_CREATED_BY_ADMIN:
        data = UserCreatedByAdminPayload.model_validate(envelope.payload)
        drafts += [
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_WELCOME,
                title="Аккаунт создан администратором",
                body="Для вас создан аккаунт Telemed.",
                payload={"userId": str(data.user_id)},
                in_app_enabled=in_app_enabled,
            ),
            _email(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.AUTH_WELCOME,
                title="Аккаунт Telemed создан",
                body="Администратор создал для вас аккаунт Telemed.",
                payload={"userId": str(data.user_id)},
                email_enabled=email_enabled,
                recipient_email=data.email,
            ),
        ]
    
    elif event_type == UserEventTypes.DOCTOR_VERIFIED:
        data = DoctorVerifiedPayload.model_validate(envelope.payload)
        drafts.append(
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.USER_DOCTOR_VERIFIED,
                title="Профиль врача подтверждён",
                body="Ваш профиль прошёл проверку. Пациенты могут записываться на приём.",
                payload={"userId": str(data.user_id), "specialization": data.specialization},
                in_app_enabled=in_app_enabled,
            ),
        )
        if data.email:
            drafts.append(
                _email(
                    envelope,
                    user_id=str(data.user_id),
                    kind=NotificationKind.USER_DOCTOR_VERIFIED,
                    title="Профиль врача Telemed подтверждён",
                    body="Ваш профиль прошёл проверку. Пациенты могут записываться на приём.",
                    payload={"userId": str(data.user_id)},
                    email_enabled=email_enabled,
                    recipient_email=data.email,
                ),
            )
    elif event_type == UserEventTypes.DOCTOR_REJECTED:
        data = DoctorRejectedPayload.model_validate(envelope.payload)
        reason = f" Причина: {data.reason}." if data.reason else ""
        drafts.append(
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.USER_DOCTOR_REJECTED,
                title="Профиль врача отклонён",
                body=f"Проверка профиля не пройдена.{reason}",
                payload={"userId": str(data.user_id), "reason": data.reason},
                in_app_enabled=in_app_enabled,
            ),
        )
        if data.email:
            drafts.append(
                _email(
                    envelope,
                    user_id=str(data.user_id),
                    kind=NotificationKind.USER_DOCTOR_REJECTED,
                    title="Профиль врача Telemed отклонён",
                    body=f"Проверка профиля не пройдена.{reason}",
                    payload={"userId": str(data.user_id), "reason": data.reason},
                    email_enabled=email_enabled,
                    recipient_email=data.email,
                ),
            )
    elif event_type == UserEventTypes.PROFILE_UPDATED:
        data = UserProfileUpdatedPayload.model_validate(envelope.payload)
        drafts.append(
            _in_app(
                envelope,
                user_id=str(data.user_id),
                kind=NotificationKind.USER_PROFILE_UPDATED,
                title="Профиль обновлён",
                body="Данные вашего профиля изменены.",
                payload={"userId": str(data.user_id), "roles": data.roles},
                in_app_enabled=in_app_enabled,
            ),
        )

    
    elif event_type == AppointmentEventTypes.CREATED:
        data = AppointmentCreatedPayload.model_validate(envelope.payload)
        drafts += _appointment_drafts(
            envelope,
            kind=NotificationKind.APPOINTMENT_CREATED,
            patient_id=str(data.patient_id),
            doctor_id=str(data.doctor_id),
            starts_at=data.starts_at,
            ends_at=data.ends_at,
            email_enabled=email_enabled,
            in_app_enabled=in_app_enabled,
        )
    elif event_type == AppointmentEventTypes.CANCELLED:
        data = AppointmentCancelledPayload.model_validate(envelope.payload)
        drafts += _appointment_drafts(
            envelope,
            kind=NotificationKind.APPOINTMENT_CANCELLED,
            patient_id=str(data.patient_id),
            doctor_id=str(data.doctor_id),
            reason=data.reason,
            email_enabled=email_enabled,
            in_app_enabled=in_app_enabled,
        )
    elif event_type == AppointmentEventTypes.CONFIRMED:
        data = AppointmentConfirmedPayload.model_validate(envelope.payload)
        drafts += _appointment_drafts(
            envelope,
            kind=NotificationKind.APPOINTMENT_CONFIRMED,
            patient_id=str(data.patient_id),
            doctor_id=str(data.doctor_id),
            starts_at=data.starts_at,
            ends_at=data.ends_at,
            email_enabled=email_enabled,
            in_app_enabled=in_app_enabled,
        )
    elif event_type == AppointmentEventTypes.NO_SHOW:
        data = AppointmentNoShowPayload.model_validate(envelope.payload)
        drafts += _appointment_drafts(
            envelope,
            kind=NotificationKind.APPOINTMENT_NO_SHOW,
            patient_id=str(data.patient_id),
            doctor_id=str(data.doctor_id),
            starts_at=data.starts_at,
            ends_at=data.ends_at,
            email_enabled=email_enabled,
            in_app_enabled=in_app_enabled,
        )
    elif event_type == AppointmentEventTypes.RESCHEDULED:
        data = AppointmentRescheduledPayload.model_validate(envelope.payload)
        drafts += _appointment_drafts(
            envelope,
            kind=NotificationKind.APPOINTMENT_RESCHEDULED,
            patient_id=str(data.patient_id),
            doctor_id=str(data.doctor_id),
            starts_at=data.starts_at,
            ends_at=data.ends_at,
            previous_starts_at=data.previous_starts_at,
            reason=data.reason,
            email_enabled=email_enabled,
            in_app_enabled=in_app_enabled,
        )
    elif event_type == AppointmentEventTypes.COMPLETED:
        data = AppointmentCompletedPayload.model_validate(envelope.payload)
        drafts += _appointment_drafts(
            envelope,
            kind=NotificationKind.APPOINTMENT_COMPLETED,
            patient_id=str(data.patient_id),
            doctor_id=str(data.doctor_id),
            completed_at=data.completed_at,
            email_enabled=email_enabled,
            in_app_enabled=in_app_enabled,
        )

    
    elif event_type in LEGACY_APPOINTMENT_EVENTS:
        drafts += _appointment_drafts(
            envelope,
            kind=LEGACY_APPOINTMENT_EVENTS[event_type],
            patient_id=str(envelope.payload.get("patient_id") or ""),
            doctor_id=str(envelope.payload.get("doctor_id") or ""),
            appointment_id=str(envelope.payload.get("appointment_id") or ""),
            starts_at=envelope.payload.get("starts_at"),
            ends_at=envelope.payload.get("ends_at"),
            previous_starts_at=envelope.payload.get("previous_starts_at"),
            reason=envelope.payload.get("reason") or envelope.payload.get("cancellation_reason"),
            email_enabled=email_enabled,
            in_app_enabled=in_app_enabled,
        )

    
    elif event_type == VideoEventTypes.SESSION_CREATED:
        data = VideoSessionCreatedPayload.model_validate(envelope.payload)
        start_payload = {
            "sessionId": str(data.session_id),
            "appointmentId": str(data.appointment_id or ""),
            "roomName": data.room_name,
        }
        drafts.append(
            _in_app(
                envelope,
                user_id=str(data.patient_id),
                kind=NotificationKind.VIDEO_CALL_STARTED,
                title="Врач начал видеозвонок",
                body="Подключитесь к видеозвонку из карточки приёма или чата.",
                payload=start_payload,
                in_app_enabled=in_app_enabled,
            ),
        )
        drafts.append(
            _email(
                envelope,
                user_id=str(data.patient_id),
                kind=NotificationKind.VIDEO_CALL_STARTED,
                title="Врач начал видеозвонок",
                body=(
                    "Врач начал видеозвонок. Подключитесь из карточки приёма "
                    f"или чата: {frontend_url.rstrip('/')}/"
                ),
                payload=start_payload,
                email_enabled=email_enabled,
            ),
        )
    elif event_type == VideoEventTypes.SESSION_ENDED:
        data = VideoSessionEndedPayload.model_validate(envelope.payload)
        drafts.append(
            _in_app(
                envelope,
                user_id=str(data.patient_id or ""),
                kind=NotificationKind.VIDEO_CALL_ENDED,
                title="Видеозвонок завершён",
                body=f"Длительность: {data.duration_seconds} с.",
                payload={
                    "sessionId": str(data.session_id),
                    "durationSeconds": data.duration_seconds,
                },
                in_app_enabled=in_app_enabled,
            ),
        )

    
    elif event_type in LEGACY_VIDEO_EVENTS:
        payload = envelope.payload
        is_started = event_type == "video.call.created"
        title = "Врач начал видеозвонок" if is_started else "Видеозвонок завершён"
        body = (
            "Подключитесь к видеозвонку из карточки приёма или чата."
            if is_started
            else "Видеозвонок завершён."
        )
        video_payload = {
            "callId": str(payload.get("call_id") or ""),
            "appointmentId": str(payload.get("appointment_id") or ""),
            "roomName": payload.get("room_name") or "",
        }
        drafts.append(
            _in_app(
                envelope,
                user_id=str(payload.get("patient_id") or ""),
                kind=LEGACY_VIDEO_EVENTS[event_type],
                title=title,
                body=body,
                payload=video_payload,
                in_app_enabled=in_app_enabled,
            ),
        )
        if is_started:
            drafts.append(
                _email(
                    envelope,
                    user_id=str(payload.get("patient_id") or ""),
                    kind=NotificationKind.VIDEO_CALL_STARTED,
                    title=title,
                    body=(
                        "Врач начал видеозвонок. Подключитесь из карточки приёма "
                        f"или чата: {frontend_url.rstrip('/')}/"
                    ),
                    payload=video_payload,
                    email_enabled=email_enabled,
                ),
            )

    return [draft for draft in drafts if draft is not None]

def _appointment_drafts(
    envelope: EventEnvelope,
    *,
    kind: NotificationKind,
    patient_id: str,
    doctor_id: str,
    email_enabled: bool,
    in_app_enabled: bool,
    appointment_id: str | None = None,
    starts_at: Any = None,
    ends_at: Any = None,
    previous_starts_at: Any = None,
    reason: str | None = None,
    completed_at: Any = None,
) -> list[NotificationDraft | None]:
    payload = {
        "appointmentId": (
            appointment_id
            or envelope.payload.get("appointmentId")
            or envelope.payload.get("appointment_id")
        ),
        "startsAt": starts_at,
        "endsAt": ends_at,
        "reason": reason,
    }

    if kind is NotificationKind.APPOINTMENT_CREATED:
        patient_title, patient_body = "Приём назначен", f"Ваш приём назначен на {_fmt(starts_at)}."
        doctor_title, doctor_body = "Новая запись", f"Пациент записан на {_fmt(starts_at)}."
    elif kind is NotificationKind.APPOINTMENT_CONFIRMED:
        patient_title = "Приём подтверждён"
        patient_body = f"Врач подтвердил приём на {_fmt(starts_at)}."
        doctor_title = "Запись подтверждена"
        doctor_body = f"Приём на {_fmt(starts_at)} подтверждён."
    elif kind is NotificationKind.APPOINTMENT_RESCHEDULED:
        patient_title = "Приём перенесён"
        patient_body = (
            f"Новое время: {_fmt(starts_at)}."
            + (f" Предыдущее: {_fmt(previous_starts_at)}." if previous_starts_at else "")
        )
        doctor_title, doctor_body = "Запись перенесена", f"Новое время: {_fmt(starts_at)}."
    elif kind is NotificationKind.APPOINTMENT_CANCELLED:
        suffix = f" Причина: {reason}." if reason else ""
        patient_title, patient_body = "Приём отменён", f"Приём отменён.{suffix}"
        doctor_title, doctor_body = "Запись отменена", f"Пациент отменил приём.{suffix}"
    elif kind is NotificationKind.APPOINTMENT_COMPLETED:
        patient_title, patient_body = "Приём завершён", "Спасибо за визит."
        doctor_title, doctor_body = "Приём завершён", f"Приём завершён {_fmt(completed_at)}."
    elif kind is NotificationKind.APPOINTMENT_NO_SHOW:
        patient_title, patient_body = "Неявка на приём", "Приём отмечен как неявка."
        doctor_title, doctor_body = "Неявка пациента", "Пациент не пришёл на приём."
    else:
        patient_title = doctor_title = "Обновление приёма"
        patient_body = doctor_body = "Статус приёма изменился."

    return [
        _in_app(
            envelope,
            user_id=patient_id,
            kind=kind,
            title=patient_title,
            body=patient_body,
            payload=payload,
            in_app_enabled=in_app_enabled,
        ),
        _in_app(
            envelope,
            user_id=doctor_id,
            kind=kind,
            title=doctor_title,
            body=doctor_body,
            payload=payload,
            in_app_enabled=in_app_enabled,
        ),
        _email(
            envelope,
            user_id=patient_id,
            kind=kind,
            title=patient_title,
            body=patient_body,
            payload=payload,
            email_enabled=email_enabled,
        ),
        _email(
            envelope,
            user_id=doctor_id,
            kind=kind,
            title=doctor_title,
            body=doctor_body,
            payload=payload,
            email_enabled=email_enabled,
        ),
    ]
