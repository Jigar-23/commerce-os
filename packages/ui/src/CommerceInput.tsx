import React, { useState } from 'react';
import { Search, X, AlertCircle } from 'lucide-react';
import { cn } from '@commerce-os/design-system';

export interface CommerceInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  helperText?: string;
  errorMessage?: string;
  leadingIcon?: React.ReactNode;
  trailingIcon?: React.ReactNode;
  inputSize?: 'sm' | 'md' | 'lg';
  containerClassName?: string;
}

export const CommerceInput: React.FC<CommerceInputProps> = ({
  label,
  helperText,
  errorMessage,
  leadingIcon,
  trailingIcon,
  inputSize = 'md',
  className = '',
  containerClassName = '',
  disabled,
  ...props
}) => {
  const sizeStyles = {
    sm: 'h-8 px-3 text-xs rounded-sm',
    md: 'h-10 px-3.5 text-sm rounded-md',
    lg: 'h-12 px-4 text-base rounded-lg',
  };

  const hasError = Boolean(errorMessage);

  return (
    <div className={cn('w-full space-y-1.5', containerClassName)}>
      {label && (
        <label className="block text-xs font-bold text-content-primary tracking-tight">
          {label}
        </label>
      )}

      <div className="relative flex items-center">
        {leadingIcon && (
          <span className="absolute left-3 text-content-muted pointer-events-none flex items-center justify-center shrink-0">
            {leadingIcon}
          </span>
        )}

        <input
          disabled={disabled}
          className={cn(
            'w-full bg-surface-subtle hover:bg-surface-muted focus:bg-surface-card text-content-primary border font-medium transition-all outline-none placeholder:text-content-muted',
            sizeStyles[inputSize],
            leadingIcon ? 'pl-9' : '',
            trailingIcon || hasError ? 'pr-9' : '',
            hasError
              ? 'border-border-danger focus:border-action-dangerBg focus:ring-2 focus:ring-border-danger bg-surface-dangerSubtle'
              : 'border-border-default focus:border-border-brand focus:ring-2 focus:ring-border-brand/20',
            disabled ? 'opacity-50 cursor-not-allowed bg-surface-muted' : '',
            className
          )}
          {...props}
        />

        {hasError ? (
          <span className="absolute right-3 text-content-danger pointer-events-none flex items-center justify-center">
            <AlertCircle className="h-4 w-4" />
          </span>
        ) : trailingIcon ? (
          <span className="absolute right-3 text-content-muted flex items-center justify-center">
            {trailingIcon}
          </span>
        ) : null}
      </div>

      {hasError ? (
        <p className="text-2xs font-semibold text-content-danger tracking-tight flex items-center gap-1">
          {errorMessage}
        </p>
      ) : helperText ? (
        <p className="text-2xs text-content-secondary tracking-tight">{helperText}</p>
      ) : null}
    </div>
  );
};

export interface CommerceSearchFieldProps {
  value: string;
  onChange: (value: string) => void;
  onClear?: () => void;
  placeholder?: string;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
  autoFocus?: boolean;
}

export const CommerceSearchField: React.FC<CommerceSearchFieldProps> = ({
  value,
  onChange,
  onClear,
  placeholder = 'Search products & essentials...',
  className = '',
  size = 'md',
  autoFocus = false,
}) => {
  const [isFocused, setIsFocused] = useState(false);

  const sizeClasses = {
    sm: 'h-8 text-xs pl-8 pr-7 rounded-sm',
    md: 'h-10 text-sm pl-10 pr-9 rounded-md',
    lg: 'h-12 text-base pl-11 pr-10 rounded-lg',
  };

  const iconSizes = {
    sm: 'h-3.5 w-3.5 left-2.5',
    md: 'h-4 w-4 left-3.5',
    lg: 'h-5 w-5 left-4',
  };

  return (
    <div className={cn('relative w-full', className)}>
      <Search
        className={cn(
          'absolute top-1/2 -translate-y-1/2 text-content-muted pointer-events-none transition-colors',
          iconSizes[size],
          isFocused ? 'text-content-brand' : 'text-content-muted'
        )}
      />

      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setIsFocused(true)}
        onBlur={() => setIsFocused(false)}
        placeholder={placeholder}
        autoFocus={autoFocus}
        className={cn(
          'w-full bg-surface-subtle hover:bg-surface-muted focus:bg-surface-card text-content-primary border border-transparent focus:border-border-brand focus:ring-2 focus:ring-border-brand/20 font-medium transition-all outline-none placeholder:text-content-muted',
          sizeClasses[size]
        )}
      />

      {value && (
        <button
          type="button"
          onClick={() => {
            onChange('');
            if (onClear) onClear();
          }}
          className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 rounded-full text-content-muted hover:text-content-secondary hover:bg-surface-muted transition-colors cursor-pointer"
          title="Clear search"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  );
};
