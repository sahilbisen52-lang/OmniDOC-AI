export interface Document {
  id: string;
  filename: string;
  fileSize: number;
  pageCount: number;
  uploadedAt: string;
  status: 'processing' | 'ready' | 'error';
  summary?: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  documentId?: string;
  documentIds?: string[];
}

export interface SummaryRequest {
  documentId: string;
  mode: 'brief' | 'detailed' | 'key-points' | 'action-items';
}

export interface SummaryResponse {
  summary: string;
  mode: string;
  processingTimeMs: number;
  cached: boolean;
}

export type SummaryMode = SummaryRequest['mode'];
