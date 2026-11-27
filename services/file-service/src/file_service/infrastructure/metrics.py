"""Prometheus metrics."""

from __future__ import annotations

from prometheus_client import Counter

FILES_UPLOADED_TOTAL = Counter(
    "file_service_files_uploaded_total",
    "Files successfully uploaded",
    ["kind"],
)

FILES_DELETED_TOTAL = Counter(
    "file_service_files_deleted_total",
    "Files soft deleted",
)
