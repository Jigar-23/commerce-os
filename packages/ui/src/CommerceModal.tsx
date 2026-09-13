import React, { useEffect } from 'react';
import { X } from 'lucide-react';
import { cn } from '@commerce-os/design-system';

export interface CommerceModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl';
  className?: string;
}

export const CommerceModal: React.FC<CommerceModalProps> = ({
  isOpen,
  onClose,
  title,
  children,
  maxWidth = 'md',
  className = '',
}) => {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const maxWidthStyles = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-xl',
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto flex items-center justify-center p-4">
      {/* BACKDROP */}
      <div
        onClick={onClose}
        className="fixed inset-0 bg-surface-inverse/60 backdrop-blur-xs transition-opacity animate-fade-in"
      />

      {/* DIALOG SURFACE */}
      <div
        className={cn(
          'relative w-full bg-surface-elevated rounded-2xl shadow-modal border border-border-subtle overflow-hidden z-10 animate-slide-up sm:animate-scale-in',
          maxWidthStyles[maxWidth],
          className
        )}
      >
        {title && (
          <div className="p-5 border-b border-border-subtle flex items-center justify-between bg-surface-subtle">
            <h3 className="text-base font-extrabold text-content-primary tracking-tight">{title}</h3>
            <button
              onClick={onClose}
              className="p-1.5 rounded-md text-content-muted hover:text-content-primary hover:bg-surface-muted transition-colors cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
};
