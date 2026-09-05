import { useState } from 'react';
import { FileText, Trash2, Clock, AlertCircle, Loader } from 'lucide-react';
import type { Document } from '../types';

interface DocumentListProps {
  documents: Document[];
  selectedDocId: string | null;
  onSelect: (doc: Document) => void;
  onDelete: (id: string) => void;
  loading: boolean;
}

function formatRelativeTime(dateStr: string): string {
  const now = Date.now();
  const date = new Date(dateStr).getTime();
  const diff = now - date;
  const seconds = Math.floor(diff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (seconds < 60) return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  if (hours < 24) return `${hours}h ago`;
  if (days < 7) return `${days}d ago`;
  return new Date(dateStr).toLocaleDateString();
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function DocumentList({
  documents,
  selectedDocId,
  onSelect,
  onDelete,
  loading,
}: DocumentListProps) {
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  if (loading) {
    return (
      <div className="content-center">
        <Loader size={32} className="spinner" />
      </div>
    );
  }

  if (documents.length === 0) {
    return null;
  }

  return (
    <div style={{ padding: 'var(--space-6)' }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
        {documents.map((doc) => (
          <div
            key={doc.id}
            className={`glass-card ${selectedDocId === doc.id ? 'doc-card-active' : ''}`}
            style={{
              padding: 'var(--space-4)',
              cursor: 'pointer',
              width: '260px',
              position: 'relative',
            }}
            onClick={() => onSelect(doc)}
          >
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 'var(--space-3)' }}>
              <div className="doc-card-icon">
                <FileText size={18} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="doc-card-filename" title={doc.filename}>
                  {doc.filename}
                </div>
                <div className="doc-card-meta" style={{ marginTop: 'var(--space-2)' }}>
                  {doc.status === 'processing' && (
                    <span className="badge badge-amber badge-pulse">Processing</span>
                  )}
                  {doc.status === 'ready' && (
                    <span className="badge badge-green">Ready</span>
                  )}
                  {doc.status === 'error' && (
                    <span className="badge badge-red">Error</span>
                  )}
                </div>
                <div className="doc-card-meta" style={{ marginTop: 'var(--space-2)', flexWrap: 'wrap' }}>
                  <span>{doc.pageCount} pages</span>
                  <span>·</span>
                  <span>{formatFileSize(doc.fileSize)}</span>
                </div>
                <div className="doc-card-meta" style={{ marginTop: 'var(--space-1)' }}>
                  <Clock size={10} />
                  <span>{formatRelativeTime(doc.uploadedAt)}</span>
                </div>
              </div>
            </div>

            {doc.status === 'error' && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-2)',
                  marginTop: 'var(--space-3)',
                  padding: 'var(--space-2) var(--space-3)',
                  background: 'var(--accent-red-muted)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: 'var(--font-xs)',
                  color: 'var(--accent-red)',
                }}
              >
                <AlertCircle size={12} />
                <span>Processing failed</span>
              </div>
            )}

            <div style={{ marginTop: 'var(--space-3)', display: 'flex', justifyContent: 'flex-end' }}>
              {confirmDeleteId === doc.id ? (
                <div className="confirm-actions">
                  <button
                    className="btn btn-danger btn-sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      onDelete(doc.id);
                      setConfirmDeleteId(null);
                    }}
                  >
                    Confirm
                  </button>
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      setConfirmDeleteId(null);
                    }}
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  className="btn btn-ghost btn-sm"
                  onClick={(e) => {
                    e.stopPropagation();
                    setConfirmDeleteId(doc.id);
                  }}
                  title="Delete document"
                >
                  <Trash2 size={14} />
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
