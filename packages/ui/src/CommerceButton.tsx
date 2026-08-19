import React from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '@commerce-os/design-system';

export interface CommerceButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode;
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'speed';
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  fullWidth?: boolean;
  isLoading?: boolean;
  icon?: React.ReactNode;
  iconPosition?: 'left' | 'right';
  className?: string;
}

const variantStyles = {
  primary: 'bg-action-primaryBg hover:bg-action-primaryHover active:bg-action-primaryActive text-action-primaryText shadow-subtle focus:ring-2 focus:ring-border-brand/30',
  secondary: 'bg-surface-card hover:bg-surface-subtle active:bg-surface-muted text-content-primary border border-border-default shadow-subtle focus:ring-2 focus:ring-border-strong',
  outline: 'bg-transparent hover:bg-surface-brandSubtle active:bg-brand-100 text-content-brand border border-border-brand/40 focus:ring-2 focus:ring-border-brand/20',
  ghost: 'bg-transparent hover:bg-surface-subtle active:bg-surface-muted text-content-secondary focus:ring-2 focus:ring-border-default',
  danger: 'bg-action-dangerBg hover:bg-action-dangerHover active:bg-action-dangerHover text-action-dangerText shadow-subtle focus:ring-2 focus:ring-border-danger/30',
  speed: 'bg-action-speedBg hover:bg-action-speedHover active:bg-action-speedHover text-action-speedText shadow-subtle focus:ring-2 focus:ring-border-accent/30',
};

const sizeStyles = {
  xs: 'h-7 px-2.5 text-xs font-semibold rounded-sm gap-1',
  sm: 'h-8 px-3 text-xs font-bold rounded-md gap-1.5',
  md: 'h-10 px-4 text-sm font-bold rounded-lg gap-2',
  lg: 'h-12 px-5 text-base font-extrabold rounded-xl gap-2.5',
  xl: 'h-14 px-6 text-lg font-black rounded-2xl gap-3',
};

export const CommerceButton: React.FC<CommerceButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  isLoading = false,
  icon,
  iconPosition = 'left',
  disabled,
  className = '',
  ...props
}) => {
  return (
    <button
      disabled={disabled || isLoading}
      className={cn(
        'inline-flex items-center justify-center font-sans tracking-tight transition-all active:scale-[0.98] select-none outline-none disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 cursor-pointer',
        variantStyles[variant],
        sizeStyles[size],
        fullWidth ? 'w-full' : '',
        className
      )}
      {...props}
    >
      {isLoading ? (
        <Loader2 className="h-4 w-4 animate-spin shrink-0" />
      ) : (
        <>
          {icon && iconPosition === 'left' && <span className="shrink-0">{icon}</span>}
          <span>{children}</span>
          {icon && iconPosition === 'right' && <span className="shrink-0">{icon}</span>}
        </>
      )}
    </button>
  );
};

export const CommerceIconButton: React.FC<React.ButtonHTMLAttributes<HTMLButtonElement> & {
  icon: React.ReactNode;
  variant?: 'primary' | 'secondary' | 'ghost' | 'speed';
  size?: 'xs' | 'sm' | 'md' | 'lg';
  className?: string;
}> = ({ icon, variant = 'secondary', size = 'md', className = '', ...props }) => {
  const iconSizeStyles = {
    xs: 'h-7 w-7 rounded-sm',
    sm: 'h-8 w-8 rounded-md',
    md: 'h-10 w-10 rounded-lg',
    lg: 'h-12 w-12 rounded-xl',
  };

  return (
    <button
      className={cn(
        'inline-flex items-center justify-center transition-all active:scale-95 select-none outline-none disabled:opacity-50 cursor-pointer',
        variantStyles[variant],
        iconSizeStyles[size],
        className
      )}
      {...props}
    >
      {icon}
    </button>
  );
};
