"""Event router registration for NotificationService."""

from __future__ import annotations

import logging

from common_events import (
    AppointmentEventTypes,
    AuthEventTypes,
    EventEnvelope,
    UserEventTypes,
    VideoEventTypes,
)
from common_messaging import EventRouter

from notification_service.application.use_cases.ingest import IngestEventUseCase

logger = logging.getLogger(__name__)

LEGACY_APPOINTMENT_EVENTS = (
    "appointment.requested",
    "appointment.confirmed",
    "appointment.rescheduled",
    "appointment.cancelled",
    "appointment.completed",
    "appointment.no_show",
)

LEGACY_VIDEO_EVENTS = ("video.call.created", "video.call.ended")

AUTH_EVENTS = (
    AuthEventTypes.USER_REGISTERED,
    AuthEventTypes.USER_CREATED_BY_ADMIN,
    AuthEventTypes.USER_INVITED,
    AuthEventTypes.USER_ROLE_CHANGED,
    AuthEventTypes.USER_BLOCKED,
    AuthEventTypes.USER_UNBLOCKED,
    AuthEventTypes.USER_EMAIL_CHANGED,
    AuthEventTypes.USER_EMAIL_VERIFIED,
    AuthEventTypes.USER_EMAIL_VERIFICATION_REQUESTED,
    AuthEventTypes.USER_PASSWORD_CHANGED,
    AuthEventTypes.OAUTH_LINK_CONFIRMATION_REQUESTED,
)

APPOINTMENT_EVENTS = (
    AppointmentEventTypes.CREATED,
    AppointmentEventTypes.CONFIRMED,
    AppointmentEventTypes.CANCELLED,
    AppointmentEventTypes.RESCHEDULED,
    AppointmentEventTypes.COMPLETED,
    AppointmentEventTypes.NO_SHOW,
)

VIDEO_EVENTS = (
    VideoEventTypes.SESSION_CREATED,
    VideoEventTypes.SESSION_ENDED,
)

USER_EVENTS = (
    UserEventTypes.PROFILE_UPDATED,
    UserEventTypes.DOCTOR_VERIFIED,
    UserEventTypes.DOCTOR_REJECTED,
)

def register_handlers(router: EventRouter, ingest: IngestEventUseCase) -> None:
    async def handle(envelope: EventEnvelope) -> None:
        
        logger.info(
            "notification.event_received type=%s event_id=%s",
            envelope.event_type,
            envelope.event_id,
        )
        await ingest(envelope)

    event_types = (
        *AUTH_EVENTS,
        *USER_EVENTS,
        *APPOINTMENT_EVENTS,
        *LEGACY_APPOINTMENT_EVENTS,
        *VIDEO_EVENTS,
        *LEGACY_VIDEO_EVENTS,
    )
    for event_type in event_types:
        router.register(event_type, handle)
