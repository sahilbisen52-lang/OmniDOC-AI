import { useState, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { FileText, Sparkles, Copy, Check, Clock, Loader, Zap } from 'lucide-react';
import type { Document, SummaryMode, SummaryResponse } from '../types';
import { summarizeDocument } from '../api/client';

interface SummaryViewProps {
  document: Document;
}

const MODES: { value: SummaryMode; label: string }[] = [
  { value: 'brief', label: 'Brief' },
  { value: 'detailed', label: 'Detailed' },
  { value: 'key-points', label: 'Key Points' },
  { value: 'action-items', label: 'Action Items' },
];

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function SummaryView({ document: doc }: SummaryViewProps) {
  const [mode, setMode] = useState<SummaryMode>('brief');
  const [summary, setSummary] = useState<SummaryResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const handleGenerate = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await summarizeDocument({ documentId: doc.id, mode });
      setSummary(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate summary');
    } finally {
      setLoading(false);
    }
  }, [doc.id, mode]);

  const handleCopy = useCallback(async () => {
    if (!summary) return;
    try {
      await navigator.clipboard.writeText(summary.summary);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback — ignore clipboard errors
    }
  }, [summary]);

  return (
    <div className="summary-view">
      {/* Header */}
      <div className="summary-header">
        <div className="summary-doc-info">
          <div className="summary-doc-icon">
            <FileText size={22} />
          </div>
          <div>
            <div className="summary-doc-name">{doc.filename}</div>
            <div className="summary-doc-meta">
              <span>{doc.pageCount} pages</span>
              <span>·</span>
              <span>{formatFileSize(doc.fileSize)}</span>
              <span>·</span>
              <span>
                {doc.status === 'ready' ? (
                  <span style={{ color: 'var(--accent-green)' }}>Ready</span>
                ) : doc.status === 'processing' ? (
                  <span style={{ color: 'var(--accent-amber)' }}>Processing</span>
                ) : (
                  <span style={{ color: 'var(--accent-red)' }}>Error</span>
                )}
              </span>
            </div>
          </div>
        </div>

        {/* Mode Tabs */}
        <div className="summary-tabs">
          {MODES.map((m) => (
            <button
              key={m.value}
              className={`summary-tab ${mode === m.value ? 'summary-tab-active' : ''}`}
              onClick={() => {
                setMode(m.value);
                setSummary(null);
              }}
            >
              {m.label}
            </button>
          ))}
        </div>
      </div>

      {/* Content */}
      <div className="summary-content">
        {loading ? (
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginBottom: 'var(--space-6)' }}>
              <Loader size={18} className="spinner" />
              <span style={{ color: 'var(--text-secondary)', fontSize: 'var(--font-sm)' }}>
                Generating {mode} summary…
              </span>
            </div>
            <div className="shimmer-line shimmer" />
            <div className="shimmer-line shimmer" />
            <div className="shimmer-line shimmer" />
            <div className="shimmer-line shimmer" />
            <div className="shimmer-line shimmer" />
          </div>
        ) : error ? (
          <div className="error-banner">
            <span>{error}</span>
          </div>
        ) : summary ? (
          <div className="summary-body markdown-content">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {summary.summary}
            </ReactMarkdown>
          </div>
        ) : (
          <div className="empty-state" style={{ padding: 'var(--space-12)' }}>
            <div className="empty-state-icon" style={{ width: 64, height: 64 }}>
              <Sparkles size={28} />
            </div>
            <div className="empty-state-title" style={{ fontSize: 'var(--font-xl)' }}>
              Generate a Summary
            </div>
            <div className="empty-state-text" style={{ fontSize: 'var(--font-sm)' }}>
              Select a summary mode above and click Generate to analyze this document with AI.
            </div>
            <button
              className="btn btn-primary btn-lg"
              onClick={handleGenerate}
              disabled={doc.status !== 'ready'}
            >
              <Sparkles size={16} />
              Generate Summary
            </button>
          </div>
        )}
      </div>

      {/* Footer Actions */}
      {(summary || loading) && (
        <div className="summary-actions">
          <button
            className="btn btn-primary"
            onClick={handleGenerate}
            disabled={loading || doc.status !== 'ready'}
          >
            {loading ? <Loader size={14} className="spinner" /> : <Sparkles size={14} />}
            {loading ? 'Generating…' : 'Regenerate'}
          </button>

          {summary && (
            <button className="btn btn-secondary" onClick={handleCopy}>
              {copied ? <Check size={14} /> : <Copy size={14} />}
              {copied ? 'Copied!' : 'Copy'}
            </button>
          )}

          <div className="summary-meta-badges">
            {summary && (
              <>
                <span className="badge badge-default">
                  <Clock size={10} />
                  {summary.processingTimeMs}ms
                </span>
                {summary.cached && (
                  <span className="badge badge-cyan">
                    <Zap size={10} />
                    Cached
                  </span>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
