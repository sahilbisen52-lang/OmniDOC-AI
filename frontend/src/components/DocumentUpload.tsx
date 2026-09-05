import { useCallback, useState } from 'react';
import { useDropzone } from 'react-dropzone';
import { UploadCloud, FileText, CheckCircle, AlertCircle, Loader } from 'lucide-react';

interface DocumentUploadProps {
  onUpload: (file: File) => Promise<unknown>;
  uploading: boolean;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function DocumentUpload({ onUpload, uploading }: DocumentUploadProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadState, setUploadState] = useState<'idle' | 'uploading' | 'success' | 'error'>('idle');
  const [errorMsg, setErrorMsg] = useState<string>('');

  const onDrop = useCallback(
    async (acceptedFiles: File[]) => {
      if (acceptedFiles.length === 0) return;

      const file = acceptedFiles[0];
      setSelectedFile(file);
      setUploadState('uploading');
      setErrorMsg('');

      try {
        const doc = await onUpload(file);
        if (!doc) throw new Error('Network error or server unavailable.');
        setUploadState('success');
        setTimeout(() => {
          setUploadState('idle');
          setSelectedFile(null);
        }, 2500);
      } catch (err) {
        setUploadState('error');
        setErrorMsg(err instanceof Error ? err.message : 'Upload failed');
      }
    },
    [onUpload]
  );

  const { getRootProps, getInputProps, isDragActive, fileRejections } = useDropzone({
    onDrop,
    accept: { 'application/pdf': ['.pdf'] },
    maxFiles: 1,
    maxSize: 50 * 1024 * 1024, // 50MB
    disabled: uploading,
  });

  const rejected = fileRejections.length > 0;

  return (
    <div className="content-center">
      <div style={{ width: '100%', maxWidth: 520 }}>
        <div
          {...getRootProps()}
          className={`upload-zone ${isDragActive ? 'upload-zone-active' : ''}`}
        >
          <input {...getInputProps()} />

          {uploadState === 'uploading' || uploading ? (
            <>
              <div className="upload-zone-icon">
                <Loader size={32} className="spinner" />
              </div>
              <div className="upload-zone-title">Uploading…</div>
              <div className="upload-zone-subtitle">
                Processing your document, please wait.
              </div>
              <div className="upload-progress">
                <div className="upload-progress-bar">
                  <div className="upload-progress-fill" />
                </div>
              </div>
            </>
          ) : uploadState === 'success' ? (
            <>
              <div className="upload-zone-icon" style={{ color: 'var(--accent-green)' }}>
                <CheckCircle size={32} />
              </div>
              <div className="upload-zone-title" style={{ color: 'var(--accent-green)' }}>
                Upload Successful!
              </div>
              <div className="upload-zone-subtitle">
                Your document is ready for analysis.
              </div>
            </>
          ) : uploadState === 'error' ? (
            <>
              <div className="upload-zone-icon" style={{ color: 'var(--accent-red)' }}>
                <AlertCircle size={32} />
              </div>
              <div className="upload-zone-title" style={{ color: 'var(--accent-red)' }}>
                Upload Failed
              </div>
              <div className="upload-zone-subtitle">
                {errorMsg || 'Something went wrong. Please try again.'}
              </div>
              <button
                className="btn btn-secondary"
                onClick={(e) => {
                  e.stopPropagation();
                  setUploadState('idle');
                  setSelectedFile(null);
                  setErrorMsg('');
                }}
              >
                Try Again
              </button>
            </>
          ) : (
            <>
              <div className="upload-zone-icon">
                <UploadCloud size={32} />
              </div>
              <div className="upload-zone-title">
                {isDragActive ? 'Drop your file here' : 'Upload a Document'}
              </div>
              <div className="upload-zone-subtitle">
                {isDragActive
                  ? 'Release to upload your PDF document'
                  : 'Drag and drop a PDF file here, or click to browse'}
              </div>
              <div className="upload-zone-hint">PDF files up to 50MB</div>
            </>
          )}
        </div>

        {rejected && uploadState === 'idle' && (
          <div className="error-banner" style={{ marginTop: 'var(--space-3)' }}>
            <AlertCircle size={16} />
            <span>Only PDF files are accepted. Please select a valid file.</span>
          </div>
        )}

        {selectedFile && (uploadState === 'uploading' || uploading) && (
          <div className="upload-file-info">
            <FileText size={20} className="upload-file-info-icon" />
            <div className="upload-file-info-details">
              <div className="upload-file-info-name">{selectedFile.name}</div>
              <div className="upload-file-info-size">{formatFileSize(selectedFile.size)}</div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
