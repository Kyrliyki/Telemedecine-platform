export interface ChatParticipant {
  user_id: string;
  role: string;
}

export interface ChatDialog {
  id: string;
  request_id: string;
  request_status: string;
  participants: ChatParticipant[];
  created_at: string | null;
  updated_at: string | null;
}

export interface ChatMessage {
  id: string;
  dialog_id: string;
  author_id: string;
  content: string;
  created_at: string;
  edited_at?: string | null;
  deleted_at?: string | null;
}

export interface DialogWithHistory extends ChatDialog {
  messages: {
    items: ChatMessage[];
    next_cursor: string | null;
  };
}
