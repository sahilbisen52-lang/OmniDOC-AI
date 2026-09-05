import { useState, useCallback } from 'react';
import type { ChatMessage } from '../types';
import { sendChatMessage, sendMultiChatMessage } from '../api/client';
import { useAuth } from '../context/AuthContext';

interface UseChatReturn {
  messages: ChatMessage[];
  sending: boolean;
  error: string | null;
  send: (documentIdOrIds: string | string[], message: string) => Promise<void>;
  clearChat: () => void;
}

export function useChat(): UseChatReturn {
  const { refreshUser } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const send = useCallback(async (documentIdOrIds: string | string[], message: string) => {
    const isMulti = Array.isArray(documentIdOrIds);
    const documentId = isMulti ? undefined : documentIdOrIds;
    const documentIds = isMulti ? documentIdOrIds : undefined;

    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: message,
      timestamp: new Date().toISOString(),
      documentId,
      documentIds,
    };

    setMessages((prev) => [...prev, userMessage]);
    setSending(true);
    setError(null);

    try {
      const response = isMulti
        ? await sendMultiChatMessage(documentIds!, message)
        : await sendChatMessage(documentId!, message);

      const assistantMessage: ChatMessage = {
        id: response.id || `assistant-${Date.now()}`,
        role: 'assistant',
        content: response.content,
        timestamp: response.timestamp || new Date().toISOString(),
        documentId,
        documentIds,
      };
      setMessages((prev) => [...prev, assistantMessage]);
      refreshUser(); // Refresh daily queries statistics
    } catch (err) {
      const errMessage = err instanceof Error ? err.message : 'Failed to send message';
      setError(errMessage);
      const errorMessage: ChatMessage = {
        id: `error-${Date.now()}`,
        role: 'assistant',
        content: `Sorry, I encountered an error: ${errMessage}. Please try again.`,
        timestamp: new Date().toISOString(),
        documentId,
        documentIds,
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setSending(false);
    }
  }, [refreshUser]);

  const clearChat = useCallback(() => {
    setMessages([]);
    setError(null);
  }, []);

  return { messages, sending, error, send, clearChat };
}
