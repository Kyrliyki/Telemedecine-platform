export interface ChatFile {
  id: string;
  chat_id: string;
  uploader_id: string;
  file_name: string;
  content_type: string;
  kind: string;
  size_bytes: number;
  status: string;
  checksum_sha256: string | null;
  message_id: string | null;
  created_at: string;
  download_url: string | null;
  download_url_expires_at: string | null;
}

export interface ChatFileListResponse {
  items: ChatFile[];
  next_cursor: string | null;
}
