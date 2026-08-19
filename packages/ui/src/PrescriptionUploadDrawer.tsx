import React, { useState } from 'react';
import { cn } from '@commerce-os/design-system';
import { UploadCloud, Camera, CheckCircle2, AlertCircle, FileText, X, AlertTriangle } from 'lucide-react';

export interface PrescriptionUploadDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onUploadSuccess?: (fileUrl: string, ocrText?: string) => void;
  onUploadFile?: (file: File) => Promise<{ fileUrl: string; ocrText?: string }>;
  className?: string;
}

export const PrescriptionUploadDrawer: React.FC<PrescriptionUploadDrawerProps> = ({
  isOpen,
  onClose,
  onUploadSuccess,
  onUploadFile,
  className,
}) => {
  const [isUploading, setIsUploading] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleFileUpload = async (file: File) => {
    setUploadedFile(file);
    setUploadError(null);
    setIsUploading(true);

    try {
      if (onUploadFile) {
        const result = await onUploadFile(file);
        setIsUploading(false);
        if (onUploadSuccess && result) {
          onUploadSuccess(result.fileUrl, result.ocrText);
        }
      } else {
        // Real object URL creation for client attachment without fake OCR data
        const objectUrl = URL.createObjectURL(file);
        setIsUploading(false);
        if (onUploadSuccess) {
          onUploadSuccess(objectUrl);
        }
      }
    } catch (err: any) {
      setIsUploading(false);
      setUploadError(err.message || 'Prescription upload failed. Please try again.');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-surface-inverse/60 backdrop-blur-xs sm:items-center">
      <div
        className={cn(
          'w-full max-w-lg rounded-t-2xl bg-surface-elevated p-6 shadow-modal transition-all sm:rounded-2xl border border-border-subtle',
          className
        )}
      >
        {/* HEADER */}
        <div className="flex items-center justify-between border-b border-border-subtle pb-4">
          <div className="flex items-center gap-2">
            <div className="rounded-md bg-surface-accentSubtle p-2">
              <FileText className="h-5 w-5 text-content-accent" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-content-primary">
                Upload Valid Prescription
              </h2>
              <p className="text-xs text-content-secondary">Required by Pharmacist for Schedule H items</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1.5 text-content-muted hover:bg-surface-subtle transition-colors cursor-pointer"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* UPLOAD AREA */}
        <div className="mt-4">
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setDragActive(true);
            }}
            onDragLeave={() => setDragActive(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragActive(false);
              if (e.dataTransfer.files && e.dataTransfer.files[0]) {
                handleFileUpload(e.dataTransfer.files[0]);
              }
            }}
            className={cn(
              'flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-6 text-center transition-all',
              dragActive
                ? 'border-border-brand bg-surface-brandSubtle'
                : 'border-border-strong bg-surface-subtle'
            )}
          >
            <UploadCloud className="h-10 w-10 text-content-muted mb-2" />
            <p className="text-sm font-semibold text-content-primary">
              Drag & Drop Prescription (PDF, JPG, PNG)
            </p>
            <p className="mt-1 text-2xs text-content-secondary">Max file size 10MB</p>

            <div className="mt-4 flex gap-3">
              <label className="inline-flex items-center gap-1.5 rounded-md bg-action-primaryBg px-4 py-2 text-xs font-bold text-action-primaryText hover:bg-action-primaryHover transition-colors cursor-pointer">
                <UploadCloud className="h-4 w-4" />
                Browse Files
                <input
                  type="file"
                  accept="image/*,.pdf"
                  className="hidden"
                  onChange={(e) => {
                    if (e.target.files && e.target.files[0]) {
                      handleFileUpload(e.target.files[0]);
                    }
                  }}
                />
              </label>

              <button
                type="button"
                onClick={() => {
                  const input = document.createElement('input');
                  input.type = 'file';
                  input.accept = 'image/*';
                  input.capture = 'environment';
                  input.onchange = (e: any) => {
                    if (e.target?.files?.[0]) {
                      handleFileUpload(e.target.files[0]);
                    }
                  };
                  input.click();
                }}
                className="inline-flex items-center gap-1.5 rounded-md border border-border-default bg-surface-card px-4 py-2 text-xs font-bold text-content-primary hover:bg-surface-subtle transition-colors cursor-pointer"
              >
                <Camera className="h-4 w-4" />
                Take Photo
              </button>
            </div>
          </div>
        </div>

        {/* STATUS INFO */}
        {isUploading && (
          <div className="mt-4 flex items-center gap-2 rounded-md bg-surface-accentSubtle p-3 text-xs text-content-accent">
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-content-accent border-t-transparent" />
            <span>Verifying prescription document format...</span>
          </div>
        )}

        {uploadError && (
          <div className="mt-4 flex items-center gap-2 rounded-md bg-surface-dangerSubtle p-3 text-xs text-content-danger border border-border-danger">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>{uploadError}</span>
          </div>
        )}

        {uploadedFile && !isUploading && !uploadError && (
          <div className="mt-4 flex items-center justify-between rounded-md bg-surface-successSubtle p-3 text-xs text-content-success border border-border-brandSubtle">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4" />
              <span className="font-semibold">{uploadedFile.name}</span>
            </div>
            <span className="font-bold">Attached</span>
          </div>
        )}

        {/* COMPLIANCE NOTE */}
        <div className="mt-4 flex items-start gap-2 rounded-md bg-surface-subtle p-3 text-2xs text-content-secondary border border-border-subtle">
          <AlertCircle className="h-4 w-4 flex-shrink-0 text-content-muted" />
          <p>
            As per the Drugs & Cosmetics Act, prescription must clearly display Doctor's Name,
            Registration Number, Patient Name, Date, and Signature.
          </p>
        </div>
      </div>
    </div>
  );
};
