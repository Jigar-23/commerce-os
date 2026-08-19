import React from 'react';
import { ChevronRight } from 'lucide-react';
import { cn } from '@commerce-os/design-system';

export interface CommerceSectionHeaderProps {
  title: string;
  subtitle?: string;
  badge?: React.ReactNode;
  actionText?: string;
  actionHref?: string;
  onAction?: () => void;
  className?: string;
}

export const CommerceSectionHeader: React.FC<CommerceSectionHeaderProps> = ({
  title,
  subtitle,
  badge,
  actionText,
  actionHref,
  onAction,
  className = '',
}) => {
  return (
    <div className={cn('flex items-end justify-between gap-4 mb-3.5', className)}>
      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-base sm:text-lg md:text-xl font-extrabold text-content-primary tracking-tight">{title}</h2>
          {badge}
        </div>
        {subtitle && <p className="text-xs text-content-secondary font-medium mt-0.5">{subtitle}</p>}
      </div>

      {(actionText || actionHref || onAction) && (
        <button
          type="button"
          onClick={onAction}
          className="inline-flex items-center gap-0.5 text-xs font-bold text-content-brand hover:text-brand-700 transition-colors shrink-0 group cursor-pointer"
        >
          <span>{actionText || 'See All'}</span>
          <ChevronRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
        </button>
      )}
    </div>
  );
};
