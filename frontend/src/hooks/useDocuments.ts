import { useState, useCallback, useEffect } from 'react';
import type { Document } from '../types';
import { getDocuments, uploadDocument, deleteDocument } from '../api/client';
import { useAuth } from '../context/AuthContext';

interface UseDocumentsReturn {
  documents: Document[];
  loading: boolean;
  error: string | null;
  upload: (file: File) => Promise<Document | null>;
  remove: (id: string) => Promise<void>;
  refresh: () => Promise<void>;
  uploading: boolean;
}

export function useDocuments(): UseDocumentsReturn {
  const { refreshUser } = useAuth();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const docs = await getDocuments();
      setDocuments(docs);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load documents';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  const upload = useCallback(async (file: File): Promise<Document | null> => {
    try {
      setUploading(true);
      setError(null);
      const doc = await uploadDocument(file);
      setDocuments((prev) => [doc, ...prev]);
      refreshUser(); // Refresh quota counts
      return doc;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to upload document';
      setError(message);
      return null;
    } finally {
      setUploading(false);
    }
  }, [refreshUser]);

  const remove = useCallback(async (id: string) => {
    try {
      setError(null);
      await deleteDocument(id);
      setDocuments((prev) => prev.filter((d) => d.id !== id));
      refreshUser(); // Refresh quota counts
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to delete document';
      setError(message);
    }
  }, [refreshUser]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const hasProcessing = documents.some((d) => d.status === 'processing');
    if (!hasProcessing) return;

    const interval = setInterval(() => {
      refresh();
    }, 3000);

    return () => clearInterval(interval);
  }, [documents, refresh]);

  return { documents, loading, error, upload, remove, refresh, uploading };
}
