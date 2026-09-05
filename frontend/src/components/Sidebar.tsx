import { FileText, Upload, Trash2, Clock, Loader, MessageSquare } from 'lucide-react';
import type { Document } from '../types';

interface SidebarProps {
  documents: Document[];
  selectedDocId: string | null;
  onSelectDoc: (doc: Document) => void;
  onDeleteDoc: (id: string) => void;
  onUploadClick: () => void;
  isWorkspaceMode: boolean;
  onWorkspaceClick: () => void;
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

function StatusBadge({ status }: { status: Document['status'] }) {
  if (status === 'processing') {
    return <span className="badge badge-amber badge-pulse">Processing</span>;
  }
  if (status === 'ready') {
    return <span className="badge badge-green">Ready</span>;
  }
  return <span className="badge badge-red">Error</span>;
}

export default function Sidebar({
  documents,
  selectedDocId,
  onSelectDoc,
  onDeleteDoc,
  onUploadClick,
  isWorkspaceMode,
  onWorkspaceClick,
  loading,
}: SidebarProps) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span className="sidebar-title">Documents</span>
          <span className="badge badge-default">{documents.length}</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
          <button className="btn btn-primary btn-lg" onClick={onUploadClick} style={{ width: '100%' }}>
            <Upload size={16} />
            Upload Document
          </button>
          {documents.length > 0 && (
            <button
              className={`btn btn-lg ${isWorkspaceMode ? 'btn-accent' : 'btn-ghost'}`}
              onClick={onWorkspaceClick}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 'var(--space-2)',
                border: isWorkspaceMode ? 'none' : '1px solid var(--border-medium)',
                background: isWorkspaceMode ? 'var(--gradient-cyan-blue)' : 'transparent',
                color: isWorkspaceMode ? 'white' : 'var(--text-primary)',
              }}
            >
              <MessageSquare size={16} />
              Workspace Chat
            </button>
          )}
        </div>
      </div>

      <div className="sidebar-content">
        {loading ? (
          <div className="sidebar-empty">
            <Loader size={24} className="spinner" />
            <span style={{ fontSize: 'var(--font-sm)' }}>Loading documents…</span>
          </div>
        ) : documents.length === 0 ? (
          <div className="sidebar-empty">
            <div className="sidebar-empty-icon">
              <FileText size={24} />
            </div>
            <span style={{ fontSize: 'var(--font-sm)' }}>No documents yet</span>
            <span style={{ fontSize: 'var(--font-xs)' }}>Upload a PDF to get started</span>
          </div>
        ) : (
          <>
            <div className="sidebar-title" style={{ padding: 'var(--space-2) var(--space-3)', marginBottom: 'var(--space-1)' }}>
              Recent Documents
            </div>
            {documents.map((doc) => (
              <div
                key={doc.id}
                className={`doc-card ${selectedDocId === doc.id ? 'doc-card-active' : ''}`}
                onClick={() => onSelectDoc(doc)}
              >
                <div className="doc-card-icon">
                  <FileText size={18} />
                </div>
                <div className="doc-card-info">
                  <div className="doc-card-filename" title={doc.filename}>
                    {doc.filename}
                  </div>
                  <div className="doc-card-meta">
                    <StatusBadge status={doc.status} />
                    <span>{doc.pageCount} pg</span>
                    <span>·</span>
                    <span>{formatFileSize(doc.fileSize)}</span>
                  </div>
                  <div className="doc-card-meta" style={{ marginTop: '2px' }}>
                    <Clock size={10} />
                    <span>{formatRelativeTime(doc.uploadedAt)}</span>
                  </div>
                </div>

                <button
                  className="doc-card-delete btn btn-ghost btn-icon btn-sm"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDeleteDoc(doc.id);
                  }}
                  title="Delete document"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            ))}
          </>
        )}
      </div>
    </aside>
  );
}
