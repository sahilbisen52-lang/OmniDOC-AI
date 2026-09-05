import { useState, useCallback, useEffect } from 'react';
import type { Document } from './types';
import { useDocuments } from './hooks/useDocuments';
import { useAuth } from './context/AuthContext';
import { FileText, Check } from 'lucide-react';
import Header from './components/Header';
import Sidebar from './components/Sidebar';
import DocumentUpload from './components/DocumentUpload';
import SummaryView from './components/SummaryView';
import ChatPanel from './components/ChatPanel';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function WorkspaceSelector({
  documents,
  selectedDocIds,
  onToggleDoc,
}: {
  documents: Document[];
  selectedDocIds: string[];
  onToggleDoc: (id: string) => void;
}) {
  const readyDocs = documents.filter((d) => d.status === 'ready');

  return (
    <div className="workspace-selector-container">
      <div className="workspace-card">
        <div className="workspace-header-badge">
          <span className="badge badge-accent">Multi-Document Query</span>
        </div>
        <h2 className="workspace-title">Workspace Chat Configuration</h2>
        <p className="workspace-subtitle">
          Select the documents you want to query. OmniDoc AI will search across all selected sources to answer your questions and cite specific files.
        </p>

        {readyDocs.length === 0 ? (
          <div className="workspace-empty">
            <p>No documents are ready for chat yet. Please wait for processing to finish or upload new documents.</p>
          </div>
        ) : (
          <div className="workspace-doc-grid">
            {readyDocs.map((doc) => {
              const isChecked = selectedDocIds.includes(doc.id);
              return (
                <div
                  key={doc.id}
                  className={`workspace-doc-card-interactive ${isChecked ? 'active' : ''}`}
                  onClick={() => onToggleDoc(doc.id)}
                >
                  <div className="workspace-doc-card-header">
                    <div className="workspace-doc-card-icon">
                      <FileText size={18} />
                    </div>
                    <div className={`workspace-doc-card-checkbox ${isChecked ? 'checked' : ''}`}>
                      {isChecked && <Check size={10} strokeWidth={3} />}
                    </div>
                  </div>
                  <div className="workspace-doc-card-body">
                    <div className="workspace-doc-card-name" title={doc.filename}>
                      {doc.filename}
                    </div>
                    <div className="workspace-doc-card-meta">
                      <span>{doc.pageCount} pages</span>
                      <span>·</span>
                      <span>{formatFileSize(doc.fileSize)}</span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {readyDocs.length > 0 && (
          <div className="workspace-summary">
            Selected <strong>{selectedDocIds.length}</strong> of {readyDocs.length} ready documents
          </div>
        )}
      </div>
    </div>
  );
}

function AuthenticatedApp() {
  const { documents, loading, upload, remove, uploading } = useDocuments();
  const [selectedDoc, setSelectedDoc] = useState<Document | null>(null);
  const [showUpload, setShowUpload] = useState(false);
  const [isWorkspaceMode, setIsWorkspaceMode] = useState(false);
  const [selectedWorkspaceDocIds, setSelectedWorkspaceDocIds] = useState<string[]>([]);

  useEffect(() => {
    const readyIds = documents.filter((d) => d.status === 'ready').map((d) => d.id);
    setSelectedWorkspaceDocIds((prev) => {
      const validPrev = prev.filter((id) => readyIds.includes(id));
      const newIds = readyIds.filter((id) => !validPrev.includes(id));
      return [...validPrev, ...newIds];
    });
  }, [documents]);

  const handleSelectDoc = useCallback((doc: Document) => {
    setSelectedDoc(doc);
    setShowUpload(false);
    setIsWorkspaceMode(false);
  }, []);

  const handleDeleteDoc = useCallback(
    (id: string) => {
      remove(id);
      if (selectedDoc?.id === id) {
        setSelectedDoc(null);
      }
      setSelectedWorkspaceDocIds((prev) => prev.filter((docId) => docId !== id));
    },
    [remove, selectedDoc]
  );

  const handleUpload = useCallback(
    async (file: File) => {
      const doc = await upload(file);
      if (doc) {
        setSelectedDoc(doc);
        setShowUpload(false);
        setIsWorkspaceMode(false);
      }
      return doc;
    },
    [upload]
  );

  const handleUploadClick = useCallback(() => {
    setShowUpload(true);
    setSelectedDoc(null);
    setIsWorkspaceMode(false);
  }, []);

  const handleWorkspaceClick = useCallback(() => {
    setIsWorkspaceMode(true);
    setSelectedDoc(null);
    setShowUpload(false);
  }, []);

  const handleToggleDoc = useCallback((id: string) => {
    setSelectedWorkspaceDocIds((prev) =>
      prev.includes(id) ? prev.filter((docId) => docId !== id) : [...prev, id]
    );
  }, []);

  const showUploadView = showUpload || (!selectedDoc && documents.length === 0 && !loading && !isWorkspaceMode);
  const showSummaryView = selectedDoc && !showUpload && !isWorkspaceMode;
  const showWorkspaceView = isWorkspaceMode && !selectedDoc && !showUpload;

  return (
    <div className="app-layout">
      <div className="auth-bg">
        <div className="auth-bg-orb auth-bg-orb-1" />
        <div className="auth-bg-orb auth-bg-orb-2" />
        <div className="auth-bg-orb auth-bg-orb-3" />
      </div>
      <Header />
      <div className="app-body">
        <Sidebar
          documents={documents}
          selectedDocId={selectedDoc?.id ?? null}
          onSelectDoc={handleSelectDoc}
          onDeleteDoc={handleDeleteDoc}
          onUploadClick={handleUploadClick}
          isWorkspaceMode={isWorkspaceMode}
          onWorkspaceClick={handleWorkspaceClick}
          loading={loading}
        />

        <main className="main-content">

          {showUploadView && (
            <DocumentUpload onUpload={handleUpload} uploading={uploading} />
          )}

          {showSummaryView && <SummaryView document={selectedDoc} />}

          {showWorkspaceView && (
            <WorkspaceSelector
              documents={documents}
              selectedDocIds={selectedWorkspaceDocIds}
              onToggleDoc={handleToggleDoc}
            />
          )}

          {!showUploadView && !showSummaryView && !showWorkspaceView && !loading && documents.length > 0 && (
            <div className="content-center">
              <div className="empty-state">
                <div className="empty-state-icon">
                  <svg
                    width="36"
                    height="36"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                    <path d="M14 2v6h6" />
                    <path d="M16 13H8" />
                    <path d="M16 17H8" />
                    <path d="M10 9H8" />
                  </svg>
                </div>
                <div className="empty-state-title">Select a Document</div>
                <div className="empty-state-text">
                  Choose a document from the sidebar to view its summary and start chatting with AI, or click Workspace Chat.
                </div>
              </div>
            </div>
          )}
        </main>

        <ChatPanel
          document={selectedDoc}
          selectedDocIds={isWorkspaceMode ? selectedWorkspaceDocIds : undefined}
          documents={documents}
        />
      </div>
    </div>
  );
}

export default function App() {
  const { isAuthenticated, isLoading } = useAuth();
  const [authPage, setAuthPage] = useState<'login' | 'register'>('login');

  if (isLoading) {
    return (
      <div className="auth-page">
        <div className="auth-bg">
          <div className="auth-bg-orb auth-bg-orb-1" />
          <div className="auth-bg-orb auth-bg-orb-2" />
          <div className="auth-bg-orb auth-bg-orb-3" />
        </div>
        <div className="auth-container">
          <div className="auth-loading">
            <span className="auth-spinner auth-spinner-lg" />
          </div>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    if (authPage === 'register') {
      return <RegisterPage onSwitchToLogin={() => setAuthPage('login')} />;
    }
    return <LoginPage onSwitchToRegister={() => setAuthPage('register')} />;
  }

  return <AuthenticatedApp />;
}
