import React from 'react';
import { cn } from '@commerce-os/design-system';

export interface CommerceCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  variant?: 'default' | 'elevated' | 'subtle' | 'highlight' | 'dark' | 'outline';
  padding?: 'none' | 'sm' | 'md' | 'lg' | 'xl';
  radius?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl';
  interactive?: boolean;
  className?: string;
}

const variantStyles = {
  default: 'bg-surface-card border border-border-subtle shadow-card text-content-primary',
  elevated: 'bg-surface-elevated border border-border-subtle shadow-cardHover text-content-primary',
  subtle: 'bg-surface-subtle border border-border-default shadow-subtle text-content-primary',
  highlight: 'bg-surface-brandSubtle border border-border-brand/40 text-content-primary',
  dark: 'bg-surface-inverse border border-border-strong text-content-inverse shadow-floatingBar',
  outline: 'bg-transparent border border-border-default text-content-primary',
};

const paddingStyles = {
  none: 'p-0',
  sm: 'p-3',
  md: 'p-4 sm:p-5',
  lg: 'p-6 sm:p-7',
  xl: 'p-8 sm:p-10',
};

const radiusStyles = {
  sm: 'rounded-sm',
  md: 'rounded-md',
  lg: 'rounded-lg',
  xl: 'rounded-xl',
  '2xl': 'rounded-2xl',
  '3xl': 'rounded-3xl',
};

export const CommerceCard: React.FC<CommerceCardProps> = ({
  children,
  variant = 'default',
  padding = 'md',
  radius = 'xl',
  interactive = false,
  className = '',
  ...props
}) => {
  return (
    <div
      className={cn(
        'transition-all duration-200',
        variantStyles[variant],
        paddingStyles[padding],
        radiusStyles[radius],
        interactive ? 'hover:border-border-brand hover:shadow-cardHover active:scale-[0.99] cursor-pointer' : '',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
};
