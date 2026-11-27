"""MinIO / S3 adapter. Synchronous MinIO SDK calls are offloaded to a worker thread."""

from __future__ import annotations

import hashlib
import logging
import tempfile
from collections.abc import AsyncIterator
from datetime import timedelta
from urllib.parse import quote, urlparse

import anyio
import urllib3
from minio import Minio
from minio.error import MinioException, S3Error

from file_service.application.ports import StoredObject
from file_service.domain.exceptions import FileTooLargeError, StorageUnavailableError, ValidationError

logger = logging.getLogger(__name__)

_NOT_FOUND_CODES = {"NoSuchKey", "NoSuchObject", "NoSuchBucket", "NotFound"}

class MinioObjectStorage:
    def __init__(
        self,
        *,
        endpoint: str,
        public_endpoint: str,
        access_key: str,
        secret_key: str,
        bucket: str,
        region: str = "us-east-1",
        secure: bool = False,
    ) -> None:
        parsed = urlparse(endpoint if "://" in endpoint else f"http://{endpoint}")
        host = parsed.netloc or parsed.path
        scheme_secure = parsed.scheme == "https"
        http_client = urllib3.PoolManager(
            timeout=urllib3.Timeout(connect=3.0, read=120.0),
            retries=urllib3.Retry(
                total=2,
                connect=2,
                read=2,
                backoff_factor=0.2,
                status_forcelist=(500, 502, 503, 504),
                allowed_methods=frozenset({"GET", "HEAD", "PUT", "POST", "DELETE"}),
            ),
            maxsize=20,
        )
        self._client = Minio(
            host,
            access_key=access_key,
            secret_key=secret_key,
            secure=secure or scheme_secure,
            region=region,
            http_client=http_client,
        )
        
        
        
        public_base = public_endpoint or endpoint
        public_parsed = urlparse(public_base if "://" in public_base else f"http://{public_base}")
        public_host = public_parsed.netloc or public_parsed.path
        public_secure = public_parsed.scheme == "https"
        internal_secure = secure or scheme_secure
        if public_host and (public_host != host or public_secure != internal_secure):
            self._signer = Minio(
                public_host,
                access_key=access_key,
                secret_key=secret_key,
                secure=public_secure,
                region=region,
                http_client=http_client,
            )
        else:
            self._signer = self._client
        self._bucket = bucket

    @property
    def bucket(self) -> str:
        return self._bucket

    async def ensure_bucket(self) -> None:
        def _ensure() -> None:
            if not self._client.bucket_exists(self._bucket):
                self._client.make_bucket(self._bucket)

        try:
            await anyio.to_thread.run_sync(_ensure)
        except S3Error as exc:
            if exc.code in {"BucketAlreadyOwnedByYou", "BucketAlreadyExists"}:
                return
            raise StorageUnavailableError(f"Cannot create bucket: {exc}") from exc
        except (MinioException, OSError) as exc:
            raise StorageUnavailableError(f"Storage is unavailable: {exc}") from exc

    async def presigned_put(self, storage_key: str, *, content_type: str, ttl_seconds: int) -> str:
        try:
            url = await anyio.to_thread.run_sync(
                self._signer.presigned_put_object,
                self._bucket,
                storage_key,
                timedelta(seconds=ttl_seconds),
            )
        except (MinioException, OSError) as exc:
            raise StorageUnavailableError(f"Cannot create upload URL: {exc}") from exc
        return url

    async def presigned_get(
        self,
        storage_key: str,
        *,
        ttl_seconds: int,
        download_name: str | None = None,
    ) -> str:
        response_headers: dict[str, str] | None = None
        if download_name:
            ascii_fallback = download_name.encode("ascii", "replace").decode("ascii").replace('"', "")
            encoded = quote(download_name)
            response_headers = {
                "response-content-disposition": (
                    f"attachment; filename=\"{ascii_fallback}\"; filename*=UTF-8''{encoded}"
                )
            }
        try:
            url = await anyio.to_thread.run_sync(
                self._signer.presigned_get_object,
                self._bucket,
                storage_key,
                timedelta(seconds=ttl_seconds),
                response_headers,
            )
        except (MinioException, OSError) as exc:
            raise StorageUnavailableError(f"Cannot create download URL: {exc}") from exc
        return url

    async def put_stream(
        self,
        storage_key: str,
        stream: AsyncIterator[bytes],
        *,
        content_type: str,
        max_bytes: int,
    ) -> StoredObject:
        hasher = hashlib.sha256()
        size = 0
        spool = tempfile.SpooledTemporaryFile(max_size=8 * 1024 * 1024, mode="w+b")
        try:
            try:
                async for chunk in stream:
                    if not chunk:
                        continue
                    size += len(chunk)
                    if size > max_bytes:
                        raise FileTooLargeError(f"File exceeds the maximum size of {max_bytes} bytes")
                    hasher.update(chunk)
                    spool.write(chunk)
            except FileTooLargeError:
                raise
            except (MinioException, OSError) as exc:
                raise StorageUnavailableError(f"Cannot read upload stream: {exc}") from exc

            if size == 0:
                raise ValidationError("File is empty")

            checksum = hasher.hexdigest()
            spool.seek(0)

            def _put() -> None:
                self._client.put_object(
                    self._bucket,
                    storage_key,
                    spool,
                    size,
                    content_type=content_type,
                    metadata={"sha256": checksum},
                )

            try:
                await anyio.to_thread.run_sync(_put)
            except (MinioException, OSError) as exc:
                raise StorageUnavailableError(f"Cannot upload object: {exc}") from exc
            return StoredObject(size_bytes=size, checksum_sha256=checksum)
        finally:
            spool.close()

    async def stat(self, storage_key: str) -> StoredObject | None:
        def _stat():
            return self._client.stat_object(self._bucket, storage_key)

        try:
            stat = await anyio.to_thread.run_sync(_stat)
        except S3Error as exc:
            if exc.code in _NOT_FOUND_CODES:
                return None
            raise StorageUnavailableError(f"Cannot stat object: {exc}") from exc
        except (MinioException, OSError) as exc:
            raise StorageUnavailableError(f"Storage is unavailable: {exc}") from exc

        metadata = {str(key).lower(): value for key, value in (stat.metadata or {}).items()}
        checksum = metadata.get("x-amz-meta-sha256") or metadata.get("sha256")
        return StoredObject(size_bytes=stat.size, etag=stat.etag, checksum_sha256=checksum)

    async def delete(self, storage_key: str) -> None:
        def _delete() -> None:
            self._client.remove_object(self._bucket, storage_key)

        try:
            await anyio.to_thread.run_sync(_delete)
        except S3Error as exc:
            if exc.code in _NOT_FOUND_CODES:
                return
            raise StorageUnavailableError(f"Cannot delete object: {exc}") from exc
        except (MinioException, OSError) as exc:
            raise StorageUnavailableError(f"Storage is unavailable: {exc}") from exc

    async def healthy(self) -> bool:
        try:
            return bool(await anyio.to_thread.run_sync(self._client.bucket_exists, self._bucket))
        except (MinioException, OSError):
            logger.warning("MinIO health check failed", exc_info=True)
            return False
