import React from 'react';
import { Zap, Clock, ShieldCheck, FileText, CheckCircle2, AlertTriangle, XCircle, Truck, Package } from 'lucide-react';
import { cn } from '@commerce-os/design-system';

export interface CommerceBadgeProps {
  children?: React.ReactNode;
  variant?: 'brand' | 'speed' | 'success' | 'warning' | 'danger' | 'neutral' | 'indigo' | 'outline';
  size?: 'xs' | 'sm' | 'md';
  icon?: React.ReactNode;
  className?: string;
}

const variantStyles = {
  brand: 'bg-surface-brandSubtle text-content-brand border-border-brandSubtle',
  speed: 'bg-action-speedBg text-action-speedText border-transparent shadow-subtle',
  success: 'bg-surface-successSubtle text-content-success border-border-success',
  warning: 'bg-surface-warningSubtle text-content-warning border-border-warning',
  danger: 'bg-surface-dangerSubtle text-content-danger border-border-danger',
  neutral: 'bg-surface-subtle text-content-secondary border-border-default',
  indigo: 'bg-surface-accentSubtle text-content-accent border-border-accent',
  outline: 'bg-transparent text-content-primary border-border-strong',
};

const sizeStyles = {
  xs: 'text-2xs font-extrabold px-1.5 py-0.5 rounded-xs gap-1',
  sm: 'text-xs font-bold px-2 py-0.5 rounded-sm gap-1',
  md: 'text-sm font-bold px-2.5 py-1 rounded-md gap-1.5',
};

export const CommerceBadge: React.FC<CommerceBadgeProps> = ({
  children,
  variant = 'neutral',
  size = 'sm',
  icon,
  className = '',
}) => {
  return (
    <span
      className={cn(
        'inline-flex items-center tracking-tight border font-sans select-none',
        variantStyles[variant],
        sizeStyles[size],
        className
      )}
    >
      {icon && <span className="shrink-0">{icon}</span>}
      {children && <span>{children}</span>}
    </span>
  );
};

export const CommerceStatusBadge: React.FC<{ status: string; className?: string }> = ({
  status,
  className = '',
}) => {
  const norm = (status || '').toUpperCase();

  let variant: CommerceBadgeProps['variant'] = 'neutral';
  let Icon = Package;
  let label = norm.replace(/_/g, ' ');

  if (['DELIVERED', 'COMPLETED', 'CONFIRMED'].includes(norm)) {
    variant = 'success';
    Icon = CheckCircle2;
  } else if (['OUT_FOR_DELIVERY', 'SHIPPED', 'DISPATCHED'].includes(norm)) {
    variant = 'indigo';
    Icon = Truck;
  } else if (['PACKED', 'SELLER_ACCEPTED', 'PROCESSING'].includes(norm)) {
    variant = 'warning';
    Icon = Clock;
  } else if (['CANCELLED', 'FAILED', 'REJECTED'].includes(norm)) {
    variant = 'danger';
    Icon = XCircle;
  }

  return (
    <CommerceBadge variant={variant} size="sm" icon={<Icon className="h-3.5 w-3.5" />} className={className}>
      {label}
    </CommerceBadge>
  );
};
