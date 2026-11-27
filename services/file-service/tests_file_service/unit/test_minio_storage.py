"""MinIO adapter unit tests that do not require a running server."""

from __future__ import annotations

from urllib.parse import parse_qs, urlparse

from file_service.infrastructure.storage.minio_storage import MinioObjectStorage


def make_storage(internal: str = "http://minio:9000", public: str = "http://localhost:9000"):
    return MinioObjectStorage(
        endpoint=internal,
        public_endpoint=public,
        access_key="minioadmin",
        secret_key="minioadmin",
        bucket="chat-files",
    )


async def test_presigned_urls_use_public_host() -> None:
    storage = make_storage()

    upload_url = await storage.presigned_put(
        "chats/1/file.pdf", content_type="application/pdf", ttl_seconds=60
    )
    download_url = await storage.presigned_get("chats/1/file.pdf", ttl_seconds=60)

    assert urlparse(upload_url).netloc == "localhost:9000"
    assert urlparse(download_url).netloc == "localhost:9000"
    assert "X-Amz-Signature" in parse_qs(urlparse(upload_url).query)
    assert "X-Amz-Signature" in parse_qs(urlparse(download_url).query)


async def test_download_url_has_attachment_disposition() -> None:
    storage = make_storage()
    url = await storage.presigned_get("chats/1/file.pdf", ttl_seconds=60, download_name="МРТ колено.pdf")
    query = parse_qs(urlparse(url).query)
    disposition = query["response-content-disposition"][0]
    assert disposition.startswith("attachment;")
    assert "filename*=UTF-8''" in disposition


async def test_same_host_uses_single_client() -> None:
    storage = make_storage(internal="http://localhost:9000", public="http://localhost:9000")
    url = await storage.presigned_put("chats/1/file.pdf", content_type="application/pdf", ttl_seconds=60)
    assert urlparse(url).netloc == "localhost:9000"
