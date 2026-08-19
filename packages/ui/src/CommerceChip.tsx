import React from 'react';
import { cn } from '@commerce-os/design-system';

export interface CommerceChipProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode;
  active?: boolean;
  count?: string | number;
  icon?: React.ReactNode;
  variant?: 'brand' | 'neutral';
  className?: string;
}

export const CommerceChip: React.FC<CommerceChipProps> = ({
  children,
  active = false,
  count,
  icon,
  variant = 'brand',
  className = '',
  ...props
}) => {
  return (
    <button
      className={cn(
        'inline-flex items-center gap-2 px-3.5 py-2 rounded-lg text-xs font-bold transition-all active:scale-95 border select-none cursor-pointer outline-none shrink-0',
        active
          ? 'bg-action-primaryActive text-action-primaryText border-border-brand shadow-subtle'
          : 'bg-surface-card text-content-primary border-border-default hover:border-border-strong hover:bg-surface-subtle shadow-subtle',
        className
      )}
      {...props}
    >
      {icon && <span className="shrink-0">{icon}</span>}
      <span>{children}</span>
      {count !== undefined && (
        <span
          className={cn(
            'text-2xs px-1.5 py-0.5 rounded-full font-extrabold',
            active ? 'bg-brand-800 text-brand-100' : 'bg-surface-subtle text-content-muted'
          )}
        >
          {count}
        </span>
      )}
    </button>
  );
};
