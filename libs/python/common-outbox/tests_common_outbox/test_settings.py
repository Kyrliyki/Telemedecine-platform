from common_outbox import OutboxSettings


def test_backoff_is_exponential_and_capped() -> None:
    settings = OutboxSettings(
        initial_backoff_seconds=1,
        max_backoff_seconds=8,
    )

    assert settings.backoff_seconds(0) == 1
    assert settings.backoff_seconds(2) == 4
    assert settings.backoff_seconds(10) == 8
