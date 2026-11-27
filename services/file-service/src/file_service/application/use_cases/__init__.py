"""Use case bundle used by the presentation layer."""

from __future__ import annotations

from dataclasses import dataclass

from file_service.application.use_cases.common import AppDeps
from file_service.application.use_cases.delete_file import DeleteFileUseCase
from file_service.application.use_cases.get_file import GetFileUseCase
from file_service.application.use_cases.list_chat_files import ListChatFilesUseCase
from file_service.application.use_cases.purge_chat_files import PurgeChatFilesUseCase
from file_service.application.use_cases.upload_file import UploadFileUseCase
from file_service.application.use_cases.upload_lifecycle import CompleteUploadUseCase, InitUploadUseCase


@dataclass(frozen=True, slots=True)
class UseCases:
    upload_file: UploadFileUseCase
    init_upload: InitUploadUseCase
    complete_upload: CompleteUploadUseCase
    list_chat_files: ListChatFilesUseCase
    get_file: GetFileUseCase
    delete_file: DeleteFileUseCase
    purge_chat_files: PurgeChatFilesUseCase

    def __iter__(self):  # pragma: no cover - convenience
        return iter(
            (
                self.upload_file,
                self.init_upload,
                self.complete_upload,
                self.list_chat_files,
                self.get_file,
                self.delete_file,
                self.purge_chat_files,
            )
        )


def build_use_cases(deps: AppDeps) -> UseCases:
    return UseCases(
        upload_file=UploadFileUseCase(deps),
        init_upload=InitUploadUseCase(deps),
        complete_upload=CompleteUploadUseCase(deps),
        list_chat_files=ListChatFilesUseCase(deps),
        get_file=GetFileUseCase(deps),
        delete_file=DeleteFileUseCase(deps),
        purge_chat_files=PurgeChatFilesUseCase(deps),
    )
