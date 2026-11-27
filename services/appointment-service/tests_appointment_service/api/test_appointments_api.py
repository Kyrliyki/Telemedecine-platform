"""API tests: real FastAPI app + in-memory infrastructure (no external services)."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid4

import pytest

from tests_appointment_service.fakes import ADMIN_ID, DOCTOR_ID, PATIENT_ID

API = "/api/v1/appointments"
START = datetime(2026, 12, 1, 10, 0, tzinfo=UTC)
END = START + timedelta(minutes=30)

BODY: dict[str, Any] = {
    "doctor_id": str(DOCTOR_ID),
    "starts_at": START.isoformat(),
    "ends_at": END.isoformat(),
    "reason": "High blood pressure",
}


def payload(**overrides: Any) -> dict[str, Any]:
    return {**BODY, **overrides}


async def create(client, headers, **overrides) -> dict[str, Any]:
    response = await client.post(API, json=payload(**overrides), headers=headers)
    assert response.status_code == 201, response.text
    return response.json()


async def test_requires_bearer_token(client):
    response = await client.get(API)
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"
    assert response.headers["X-Request-Id"]


async def test_invalid_token_is_rejected(client):
    response = await client.get(API, headers={"Authorization": "Bearer not-a-jwt"})
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"


async def test_patient_can_create_get_and_list(client, patient_headers):
    created = await create(client, patient_headers)
    assert created["status"] == "requested"
    assert created["patient_id"] == str(PATIENT_ID)
    assert created["doctor_id"] == str(DOCTOR_ID)
    assert created["version"] == 1

    response = await client.get(f"{API}/{created['id']}", headers=patient_headers)
    assert response.status_code == 200
    assert response.json()["id"] == created["id"]

    response = await client.get(API, headers=patient_headers)
    assert response.status_code == 200
    body = response.json()
    assert body["total"] == 1
    assert len(body["items"]) == 1


async def test_doctor_must_specify_patient(client, doctor_headers):
    response = await client.post(API, json=payload(), headers=doctor_headers)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "validation_error"


async def test_patient_cannot_book_for_someone_else(client, patient_headers):
    response = await client.post(
        API, json=payload(patient_id=str(ADMIN_ID)), headers=patient_headers
    )
    assert response.status_code == 403
    assert response.json()["error"]["code"] == "permission_denied"


async def test_dual_role_books_another_doctor_with_patient_lead_time(
    client, dual_headers, clock
):
    other_doctor = uuid4()
    soon = clock.now() + timedelta(minutes=30)

    rejected = await client.post(
        API,
        json=payload(
            doctor_id=str(other_doctor),
            starts_at=soon.isoformat(),
            ends_at=(soon + timedelta(minutes=30)).isoformat(),
        ),
        headers=dual_headers,
    )
    assert rejected.status_code == 422
    assert rejected.json()["error"]["code"] == "validation_error"

    later = clock.now() + timedelta(hours=2)
    accepted = await client.post(
        API,
        json=payload(
            doctor_id=str(other_doctor),
            starts_at=later.isoformat(),
            ends_at=(later + timedelta(minutes=30)).isoformat(),
        ),
        headers=dual_headers,
    )
    assert accepted.status_code == 201, accepted.text
    assert accepted.json()["patient_id"] == str(DOCTOR_ID)


async def test_dual_role_creates_for_patient_at_current_time(client, dual_headers, clock):
    start = clock.now()

    created = await create(
        client,
        dual_headers,
        patient_id=str(PATIENT_ID),
        doctor_id=str(DOCTOR_ID),
        starts_at=start.isoformat(),
        ends_at=(start + timedelta(minutes=30)).isoformat(),
    )

    assert created["doctor_id"] == str(DOCTOR_ID)
    assert created["patient_id"] == str(PATIENT_ID)


async def test_dual_role_cannot_book_an_appointment_with_themselves(
    client, dual_headers, clock
):
    start = clock.now() + timedelta(hours=2)

    response = await client.post(
        API,
        json=payload(
            patient_id=str(DOCTOR_ID),
            doctor_id=str(DOCTOR_ID),
            starts_at=start.isoformat(),
            ends_at=(start + timedelta(minutes=30)).isoformat(),
        ),
        headers=dual_headers,
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "validation_error"


async def test_dual_role_list_view_selects_patient_or_doctor_side(
    client, dual_headers, clock
):
    other_doctor = uuid4()
    patient_side = clock.now() + timedelta(hours=2)
    doctor_side = clock.now() + timedelta(hours=3)

    await create(
        client,
        dual_headers,
        doctor_id=str(other_doctor),
        starts_at=patient_side.isoformat(),
        ends_at=(patient_side + timedelta(minutes=30)).isoformat(),
    )
    await create(
        client,
        dual_headers,
        patient_id=str(PATIENT_ID),
        doctor_id=str(DOCTOR_ID),
        starts_at=doctor_side.isoformat(),
        ends_at=(doctor_side + timedelta(minutes=30)).isoformat(),
    )

    patient_view = await client.get(f"{API}?view=patient", headers=dual_headers)
    assert patient_view.status_code == 200
    patient_items = patient_view.json()["items"]
    assert len(patient_items) == 1
    assert patient_items[0]["patient_id"] == str(DOCTOR_ID)

    doctor_view = await client.get(f"{API}?view=doctor", headers=dual_headers)
    assert doctor_view.status_code == 200
    doctor_items = doctor_view.json()["items"]
    assert len(doctor_items) == 1
    assert doctor_items[0]["doctor_id"] == str(DOCTOR_ID)


async def test_overlapping_slot_is_rejected(client, patient_headers):
    await create(client, patient_headers)
    response = await client.post(
        API,
        json=payload(
            starts_at=(START + timedelta(minutes=15)).isoformat(),
            ends_at=(END + timedelta(minutes=15)).isoformat(),
        ),
        headers=patient_headers,
    )
    assert response.status_code == 409
    assert response.json()["error"]["code"] == "appointment_overlap"


async def test_full_lifecycle(client, patient_headers, doctor_headers, clock):
    created = await create(client, patient_headers)
    appointment_id = created["id"]

    forbidden = await client.post(f"{API}/{appointment_id}/confirm", headers=patient_headers)
    assert forbidden.status_code == 403

    confirmed = await client.post(f"{API}/{appointment_id}/confirm", headers=doctor_headers)
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"

    new_start = START + timedelta(days=1)
    rescheduled = await client.post(
        f"{API}/{appointment_id}/reschedule",
        json={
            "starts_at": new_start.isoformat(),
            "ends_at": (new_start + timedelta(hours=1)).isoformat(),
        },
        headers=patient_headers,
    )
    assert rescheduled.status_code == 200
    assert rescheduled.json()["status"] == "confirmed"

    early_complete = await client.post(f"{API}/{appointment_id}/complete", headers=doctor_headers)
    assert early_complete.status_code == 409

    clock.set(new_start + timedelta(minutes=5))
    completed = await client.post(f"{API}/{appointment_id}/complete", headers=doctor_headers)
    assert completed.status_code == 200
    assert completed.json()["status"] == "completed"

    late_cancel = await client.post(
        f"{API}/{appointment_id}/cancel",
        json={"reason": "Too late"},
        headers=patient_headers,
    )
    assert late_cancel.status_code == 409


async def test_cancel_records_reason(client, patient_headers):
    created = await create(client, patient_headers)
    response = await client.post(
        f"{API}/{created['id']}/cancel",
        json={"reason": "Resolved on my own"},
        headers=patient_headers,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "cancelled"
    assert body["cancellation_reason"] == "Resolved on my own"


async def test_admin_sees_all_appointments(client, admin_headers):
    await create(client, admin_headers, patient_id=str(PATIENT_ID))
    await create(
        client,
        admin_headers,
        patient_id=str(ADMIN_ID),
        starts_at=(START + timedelta(hours=2)).isoformat(),
        ends_at=(END + timedelta(hours=2)).isoformat(),
    )

    response = await client.get(API, headers=admin_headers)
    assert response.status_code == 200
    assert response.json()["total"] == 2


async def test_validation_errors_use_error_envelope(client, patient_headers):
    response = await client.post(
        API,
        json=payload(ends_at=START.isoformat()),
        headers=patient_headers,
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "validation_error"

    naive = await client.post(
        API,
        json=payload(starts_at="2026-12-01T10:00:00", ends_at="2026-12-01T10:30:00"),
        headers=patient_headers,
    )
    assert naive.status_code == 422
    assert "timezone" in naive.json()["error"]["message"]


async def test_patient_must_book_at_least_one_hour_ahead(client, patient_headers, clock):
    start = clock.now() + timedelta(minutes=30)
    response = await client.post(
        API,
        json=payload(
            starts_at=start.isoformat(), ends_at=(start + timedelta(minutes=30)).isoformat()
        ),
        headers=patient_headers,
    )
    assert response.status_code == 422
    assert "1 hour" in response.json()["error"]["message"]

    boundary = clock.now() + timedelta(hours=1)
    allowed = await client.post(
        API,
        json=payload(
            starts_at=boundary.isoformat(),
            ends_at=(boundary + timedelta(minutes=30)).isoformat(),
        ),
        headers=patient_headers,
    )
    assert allowed.status_code == 201


async def test_doctor_may_book_soon_but_not_in_the_past(client, doctor_headers, clock):
    start = clock.now() + timedelta(minutes=20)
    allowed = await client.post(
        API,
        json=payload(
            patient_id=str(PATIENT_ID),
            starts_at=start.isoformat(),
            ends_at=(start + timedelta(minutes=30)).isoformat(),
        ),
        headers=doctor_headers,
    )
    assert allowed.status_code == 201

    past = clock.now() - timedelta(hours=1)
    rejected = await client.post(
        API,
        json=payload(
            patient_id=str(PATIENT_ID),
            starts_at=past.isoformat(),
            ends_at=(past + timedelta(minutes=30)).isoformat(),
        ),
        headers=doctor_headers,
    )
    assert rejected.status_code == 422
    assert "past" in rejected.json()["error"]["message"]


async def test_admin_has_no_time_restrictions(client, admin_headers, clock):
    start = clock.now() - timedelta(days=2)
    response = await client.post(
        API,
        json=payload(
            patient_id=str(PATIENT_ID),
            starts_at=start.isoformat(),
            ends_at=(start + timedelta(minutes=30)).isoformat(),
        ),
        headers=admin_headers,
    )
    assert response.status_code == 201
    assert response.json()["status"] == "requested"


async def test_admin_may_reschedule_into_the_past(client, patient_headers, admin_headers, clock):
    created = await create(client, patient_headers)
    past = clock.now() - timedelta(days=3)
    response = await client.post(
        f"{API}/{created['id']}/reschedule",
        json={
            "starts_at": past.isoformat(),
            "ends_at": (past + timedelta(minutes=30)).isoformat(),
        },
        headers=admin_headers,
    )
    assert response.status_code == 200
    assert response.json()["starts_at"].startswith(past.date().isoformat())


async def test_patient_cannot_reschedule_within_one_hour(client, patient_headers, clock):
    created = await create(client, patient_headers)
    too_soon = clock.now() + timedelta(minutes=20)
    response = await client.post(
        f"{API}/{created['id']}/reschedule",
        json={
            "starts_at": too_soon.isoformat(),
            "ends_at": (too_soon + timedelta(minutes=30)).isoformat(),
        },
        headers=patient_headers,
    )
    assert response.status_code == 422
    assert "1 hour" in response.json()["error"]["message"]


async def test_health_and_metrics(client):
    live = await client.get("/health/live")
    assert live.status_code == 200

    ready = await client.get("/health/ready")
    assert ready.status_code == 200
    body = ready.json()
    assert body["status"] == "ok"
    assert body["checks"]["database"] == "ok"

    metrics = await client.get("/metrics")
    assert metrics.status_code == 200
    assert "http_requests_total" in metrics.text


async def test_root_redirects_to_docs(client):
    response = await client.get("/", follow_redirects=False)
    assert response.status_code in (302, 307)
    assert response.headers["location"] == "/docs"


@pytest.mark.parametrize("role", ["patient", "doctor", "admin"])
async def test_all_roles_can_list(client, make_headers, role):
    response = await client.get(API, headers=make_headers(PATIENT_ID, role))
    assert response.status_code == 200

async def test_internal_appointment_endpoint(client, app, patient_headers):
    created = await create(client, patient_headers)

    unauthorized = await client.get(f"/internal/appointments/{created['id']}")
    assert unauthorized.status_code == 503

    app.state.settings.internal_service_token = "test-internal-token"
    response = await client.get(
        f"/internal/appointments/{created['id']}",
        headers={"X-Internal-Token": "test-internal-token"},
    )
    assert response.status_code == 200, response.text
    data = response.json()
    assert data["appointmentId"] == created["id"]
    assert data["patientId"] == str(PATIENT_ID)
    assert data["status"] == "REQUESTED"

    status_response = await client.get(
        f"/internal/appointments/{created['id']}/status",
        headers={"X-Internal-Token": "test-internal-token"},
    )
    assert status_response.json() == {"status": "REQUESTED"}
