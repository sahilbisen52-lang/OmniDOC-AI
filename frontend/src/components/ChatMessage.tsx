import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Bot, User, FileText } from 'lucide-react';
import type { ChatMessage as ChatMessageType, Document } from '../types';

interface ChatMessageProps {
  message: ChatMessageType;
  documents?: Document[];
}

function formatTime(timestamp: string): string {
  const date = new Date(timestamp);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export default function ChatMessage({ message, documents }: ChatMessageProps) {
  const isUser = message.role === 'user';

  const sources = !isUser && message.documentIds && documents
    ? documents.filter((d) => message.documentIds?.includes(d.id))
    : [];

  return (
    <div className={`message ${isUser ? 'message-user' : 'message-assistant'}`}>
      <div className={`message-avatar ${isUser ? 'message-avatar-user' : 'message-avatar-assistant'}`}>
        {isUser ? <User size={14} /> : <Bot size={14} />}
      </div>
      <div>
        <div
          className={`message-bubble ${
            isUser ? 'message-bubble-user' : 'message-bubble-assistant'
          }`}
        >
          {isUser ? (
            message.content
          ) : (
            <div className="markdown-content">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {message.content}
              </ReactMarkdown>
            </div>
          )}
        </div>
        {sources.length > 0 && (
          <div className="message-sources">
            <span className="sources-label">Sources:</span>
            {sources.map((src) => (
              <span key={src.id} className="source-badge" title={src.filename}>
                <FileText size={10} style={{ marginRight: '2px' }} />
                {src.filename}
              </span>
            ))}
          </div>
        )}
        <div className="message-timestamp">{formatTime(message.timestamp)}</div>
      </div>
    </div>
  );
}

export function TypingIndicator() {
  return (
    <div className="message message-assistant">
      <div className="message-avatar message-avatar-assistant">
        <Bot size={14} />
      </div>
      <div className="message-bubble message-bubble-assistant">
        <div className="typing-indicator">
          <div className="typing-dot" />
          <div className="typing-dot" />
          <div className="typing-dot" />
        </div>
      </div>
    </div>
  );
}
