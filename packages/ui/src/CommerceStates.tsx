import React from 'react';
import { PackageX, AlertCircle, RefreshCw, CheckCircle2, Info, X } from 'lucide-react';
import { cn } from '@commerce-os/design-system';
import { CommerceButton } from './CommerceButton';

export interface CommerceEmptyStateProps {
  title: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
  icon?: React.ReactNode;
  className?: string;
}

export const CommerceEmptyState: React.FC<CommerceEmptyStateProps> = ({
  title,
  description,
  actionText,
  onAction,
  icon = <PackageX className="h-10 w-10 text-content-muted" />,
  className = '',
}) => {
  return (
    <div className={cn('py-12 px-4 flex flex-col items-center justify-center text-center space-y-3.5 max-w-sm mx-auto', className)}>
      <div className="w-16 h-16 rounded-xl bg-surface-subtle flex items-center justify-center mb-1 shadow-subtle">
        {icon}
      </div>
      <div className="space-y-1">
        <h3 className="text-base font-extrabold text-content-primary tracking-tight">{title}</h3>
        {description && <p className="text-xs text-content-secondary max-w-xs font-medium leading-relaxed">{description}</p>}
      </div>
      {actionText && onAction && (
        <div className="pt-2">
          <CommerceButton size="sm" onClick={onAction}>
            {actionText}
          </CommerceButton>
        </div>
      )}
    </div>
  );
};

export interface CommerceErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  className?: string;
}

export const CommerceErrorState: React.FC<CommerceErrorStateProps> = ({
  title = 'Service Temporarily Unavailable',
  message = 'We could not complete your request. Please check your network connection and try again.',
  onRetry,
  className = '',
}) => {
  return (
    <div className={cn('bg-surface-dangerSubtle border border-border-danger rounded-xl p-6 text-center max-w-md mx-auto my-6 space-y-3 shadow-subtle', className)}>
      <div className="w-12 h-12 rounded-lg bg-surface-dangerSubtle flex items-center justify-center text-content-danger mx-auto">
        <AlertCircle className="h-6 w-6" />
      </div>
      <div className="space-y-1">
        <h3 className="text-sm font-extrabold text-content-primary tracking-tight">{title}</h3>
        <p className="text-xs text-content-danger font-medium leading-relaxed">{message}</p>
      </div>
      {onRetry && (
        <div className="pt-1">
          <CommerceButton
            variant="secondary"
            size="xs"
            onClick={onRetry}
            icon={<RefreshCw className="h-3 w-3" />}
          >
            Retry
          </CommerceButton>
        </div>
      )}
    </div>
  );
};

export interface CommerceToastProps {
  message: string;
  type?: 'success' | 'error' | 'info';
  onClose?: () => void;
  className?: string;
}

export const CommerceToast: React.FC<CommerceToastProps> = ({
  message,
  type = 'success',
  onClose,
  className = '',
}) => {
  const typeStyles = {
    success: 'bg-surface-inverse text-content-inverse border-border-strong',
    error: 'bg-action-dangerBg text-action-dangerText border-border-danger',
    info: 'bg-surface-inverse text-content-inverse border-border-strong',
  };

  const Icons = {
    success: CheckCircle2,
    error: AlertCircle,
    info: Info,
  };

  const Icon = Icons[type];

  return (
    <div
      className={cn(
        'fixed bottom-6 right-6 z-50 flex items-center gap-3 px-4 py-3 rounded-lg border shadow-floatingBar animate-slide-up max-w-md',
        typeStyles[type],
        className
      )}
    >
      <Icon className="h-5 w-5 shrink-0" />
      <span className="text-xs font-bold flex-1">{message}</span>
      {onClose && (
        <button
          onClick={onClose}
          className="p-1 rounded hover:opacity-75 transition-opacity cursor-pointer"
        >
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
};
