import axios from 'axios';
import type { Document, SummaryRequest, SummaryResponse, ChatMessage } from '../types';

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.data?.message) {
      error.message = error.response.data.message;
    }
    return Promise.reject(error);
  }
);

export async function uploadDocument(file: File): Promise<Document> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await api.post<Document>('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

export async function getDocuments(): Promise<Document[]> {
  const response = await api.get<Document[]>('/documents');
  return response.data;
}

export async function getDocument(id: string): Promise<Document> {
  const response = await api.get<Document>(`/documents/${id}`);
  return response.data;
}

export async function deleteDocument(id: string): Promise<void> {
  await api.delete(`/documents/${id}`);
}

export async function summarizeDocument(req: SummaryRequest): Promise<SummaryResponse> {
  const response = await api.post<SummaryResponse>(
    `/documents/${req.documentId}/summarize`,
    { mode: req.mode }
  );
  return response.data;
}

export async function sendChatMessage(
  documentId: string,
  message: string
): Promise<ChatMessage> {
  const response = await api.post<ChatMessage>('/chat', {
    documentId,
    message,
  });
  return response.data;
}

export async function sendMultiChatMessage(
  documentIds: string[],
  message: string
): Promise<ChatMessage> {
  const response = await api.post<ChatMessage>('/chat/multi', {
    documentIds,
    message,
  });
  return response.data;
}

export async function healthCheck(): Promise<{ status: string }> {
  const response = await api.get<{ status: string }>('/health');
  return response.data;
}

export default api;
