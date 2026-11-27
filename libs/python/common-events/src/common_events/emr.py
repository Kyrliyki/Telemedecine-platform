"""EMR-service events shared with Python consumers.

Producer: ``emr-service`` (Java).
Exchange: ``emr.events``.

The payload deliberately contains no clinical text: only identifiers, status
and timestamps, matching the Java ``EmrEventPayloads`` contract.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import Field

from .base import EventPayload


class EmrEventTypes:
    RECORD_CREATED = "emr.record.created.v1"
    RECORD_UPDATED = "emr.record.updated.v1"
    RECORD_FINALIZED = "emr.record.finalized.v1"
    RECORD_AMENDED = "emr.record.amended.v1"
    RECORD_DELETED = "emr.record.deleted.v1"


class EmrRecordPayload(EventPayload):
    record_id: UUID
    patient_id: UUID
    doctor_id: UUID | None = None
    appointment_id: UUID | None = None
    status: str | None = None
    version: int | None = None
    occurred_at: datetime | None = None
    actor_id: UUID | None = None
    reason: str | None = Field(default=None, max_length=512)


EMR_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    EmrEventTypes.RECORD_CREATED: EmrRecordPayload,
    EmrEventTypes.RECORD_UPDATED: EmrRecordPayload,
    EmrEventTypes.RECORD_FINALIZED: EmrRecordPayload,
    EmrEventTypes.RECORD_AMENDED: EmrRecordPayload,
    EmrEventTypes.RECORD_DELETED: EmrRecordPayload,
}
