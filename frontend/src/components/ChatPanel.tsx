import { useState, useRef, useEffect, useCallback } from 'react';
import { MessageSquare, Send, Trash2, Bot } from 'lucide-react';
import type { Document } from '../types';
import { useChat } from '../hooks/useChat';
import ChatMessage, { TypingIndicator } from './ChatMessage';

interface ChatPanelProps {
  document: Document | null;
  selectedDocIds?: string[];
  documents?: Document[];
}

const SUGGESTIONS = [
  'Summarize this document',
  'What are the key findings?',
  'List action items',
  'Explain the main concepts',
];

const WORKSPACE_SUGGESTIONS = [
  'Compare these documents',
  'What are the common themes?',
  'Summarize the differences',
  'Synthesize action items across all files',
];

export default function ChatPanel({ document: doc, selectedDocIds, documents }: ChatPanelProps) {
  const { messages, sending, send, clearChat } = useChat();
  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const isWorkspace = selectedDocIds !== undefined;
  const activeDocCount = selectedDocIds?.length ?? 0;

  // Filter messages based on active mode
  const filteredMessages = isWorkspace
    ? messages.filter((m) => m.documentIds !== undefined)
    : doc
    ? messages.filter((m) => m.documentId === doc.id)
    : [];

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [filteredMessages.length, sending]);

  const handleSend = useCallback(async () => {
    if (!input.trim() || sending) return;
    const message = input.trim();
    if (isWorkspace) {
      if (!selectedDocIds || selectedDocIds.length === 0) return;
      setInput('');
      await send(selectedDocIds, message);
    } else if (doc) {
      setInput('');
      await send(doc.id, message);
    }
  }, [input, doc, isWorkspace, selectedDocIds, sending, send]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleSuggestionClick = (suggestion: string) => {
    if (sending) return;
    setInput('');
    if (isWorkspace) {
      if (selectedDocIds && selectedDocIds.length > 0) {
        send(selectedDocIds, suggestion);
      }
    } else if (doc) {
      send(doc.id, suggestion);
    }
  };

  // Auto-resize textarea
  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    const el = e.target;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 120) + 'px';
  };

  const suggestions = isWorkspace ? WORKSPACE_SUGGESTIONS : SUGGESTIONS;
  const hasActiveTarget = isWorkspace ? activeDocCount > 0 : doc !== null;
  
  const showEmptyState = isWorkspace
    ? activeDocCount === 0
    : !doc;

  const placeholderText = isWorkspace
    ? activeDocCount > 0
      ? 'Ask about selected documents…'
      : 'Select documents in the workspace panel to start…'
    : doc
    ? doc.status === 'ready'
      ? 'Ask about this document…'
      : 'Document is still processing…'
    : 'Select a document to start…';

  const isInputDisabled = isWorkspace
    ? sending || activeDocCount === 0
    : sending || !doc || doc.status !== 'ready';

  return (
    <div className="chat-panel">
      {/* Header */}
      <div className="chat-header">
        <div className="chat-header-left">
          <div className="chat-header-icon">
            <MessageSquare size={16} />
          </div>
          <div>
            <div className="chat-header-title">
              {isWorkspace ? 'Workspace Chat' : 'AI Chat'}
            </div>
            {isWorkspace ? (
              <div className="chat-header-doc">
                Querying {activeDocCount} {activeDocCount === 1 ? 'document' : 'documents'}
              </div>
            ) : doc ? (
              <div className="chat-header-doc" title={doc.filename}>
                {doc.filename}
              </div>
            ) : null}
          </div>
        </div>
        {filteredMessages.length > 0 && (
          <button
            className="btn btn-ghost btn-icon btn-sm"
            onClick={clearChat}
            title="Clear chat"
          >
            <Trash2 size={14} />
          </button>
        )}
      </div>

      {/* Messages or Empty State */}
      {showEmptyState ? (
        <div className="chat-empty">
          <div className="chat-empty-icon">
            <Bot size={28} />
          </div>
          <div className="chat-empty-title">
            {isWorkspace ? 'No Documents Selected' : 'No Document Selected'}
          </div>
          <div className="chat-empty-text">
            {isWorkspace
              ? 'Select one or more documents in the workspace panel on the left to start querying them.'
              : 'Select or upload a document to start chatting with your AI assistant.'}
          </div>
        </div>
      ) : filteredMessages.length === 0 && !sending ? (
        <div className="chat-empty">
          <div className="chat-empty-icon">
            <MessageSquare size={28} />
          </div>
          <div className="chat-empty-title">Start a Conversation</div>
          <div className="chat-empty-text">
            {isWorkspace
              ? 'Ask questions comparing or analyzing all selected documents at once, or try a suggestion below.'
              : 'Ask questions about your document or use one of the suggestions below.'}
          </div>
        </div>
      ) : (
        <div className="chat-messages">
          {filteredMessages.map((msg) => (
            <ChatMessage key={msg.id} message={msg} documents={documents} />
          ))}
          {sending && <TypingIndicator />}
          <div ref={messagesEndRef} />
        </div>
      )}

      {/* Input Area */}
      {hasActiveTarget && (
        <div className="chat-input-area">
          {filteredMessages.length === 0 && !sending && (
            <div className="chat-suggestions">
              {suggestions.map((s) => (
                <button
                  key={s}
                  className="chat-suggestion-chip"
                  onClick={() => handleSuggestionClick(s)}
                  disabled={isInputDisabled}
                >
                  {s}
                </button>
              ))}
            </div>
          )}
          <div className="chat-input-row">
            <MessageSquare size={16} className="chat-input-icon" style={{ marginBottom: '0.75rem', flexShrink: 0, color: 'var(--text-muted)' }} />
            <textarea
              ref={inputRef}
              className="chat-input"
              value={input}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
              placeholder={placeholderText}
              disabled={isInputDisabled}
              rows={1}
            />
            <button
              className="chat-send-btn"
              onClick={handleSend}
              disabled={!input.trim() || isInputDisabled}
              title="Send message"
            >
              <Send size={18} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
