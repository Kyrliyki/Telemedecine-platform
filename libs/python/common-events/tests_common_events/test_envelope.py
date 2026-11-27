from uuid import uuid4

from common_events import (
    AuthEventTypes,
    EventEnvelope,
    UserRegisteredPayload,
    parse_auth_payload,
)


def test_envelope_roundtrip_preserves_java_headers() -> None:
    event = EventEnvelope(
        event_type=AuthEventTypes.USER_REGISTERED,
        correlation_id="corr-1",
        payload={"hello": "world"},
    )

    headers = event.headers()
    restored = EventEnvelope.from_message(headers, event.body())

    assert headers["eventType"] == AuthEventTypes.USER_REGISTERED
    assert headers["eventVersion"] == 1
    assert restored.event_id == event.event_id
    assert restored.correlation_id == "corr-1"
    assert restored.payload == {"hello": "world"}


def test_auth_payload_accepts_camel_case_from_java() -> None:
    user_id = uuid4()
    payload = parse_auth_payload(
        AuthEventTypes.USER_REGISTERED,
        {
            "userId": str(user_id),
            "email": "patient@example.com",
            "roles": ["PATIENT"],
            "emailVerified": True,
            "source": "PASSWORD",
        },
    )

    assert isinstance(payload, UserRegisteredPayload)
    assert payload.user_id == user_id
    assert payload.email_verified is True
