import React from 'react';
import { Plus, Minus, Trash2 } from 'lucide-react';
import { cn } from '@commerce-os/design-system';

export interface CommerceQuantityControlProps {
  quantity: number;
  onIncrement: () => void;
  onDecrement: () => void;
  onAdd?: () => void;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  max?: number;
  disabled?: boolean;
  className?: string;
}

export const CommerceQuantityControl: React.FC<CommerceQuantityControlProps> = ({
  quantity,
  onIncrement,
  onDecrement,
  onAdd,
  size = 'md',
  max = 99,
  disabled = false,
  className = '',
}) => {
  if (quantity <= 0) {
    const addStyles = {
      xs: 'h-6 px-2 text-2xs rounded-xs',
      sm: 'h-7 px-2.5 text-xs rounded-sm',
      md: 'h-8 px-3.5 text-xs rounded-md',
      lg: 'h-10 px-5 text-sm rounded-lg',
    };

    return (
      <button
        type="button"
        disabled={disabled}
        onClick={onAdd || onIncrement}
        className={cn(
          'flex items-center justify-center font-bold tracking-tight font-sans transition-all active:scale-95 select-none cursor-pointer',
          'bg-surface-brandSubtle hover:bg-surface-brandSubtle text-content-brand border border-border-brand/40 hover:border-border-brand shadow-subtle',
          'disabled:opacity-50 disabled:cursor-not-allowed',
          addStyles[size],
          className
        )}
      >
        <Plus className="h-3.5 w-3.5 mr-1 stroke-[3]" />
        <span>ADD</span>
      </button>
    );
  }

  const containerSizes = {
    xs: 'h-6 min-w-[64px] rounded-xs',
    sm: 'h-7 min-w-[76px] rounded-sm',
    md: 'h-8 min-w-[88px] rounded-md',
    lg: 'h-10 min-w-[104px] rounded-lg',
  };

  const textSizes = {
    xs: 'text-2xs',
    sm: 'text-xs',
    md: 'text-xs',
    lg: 'text-sm',
  };

  return (
    <div
      className={cn(
        'inline-flex items-center justify-between bg-action-primaryBg text-action-primaryText font-bold select-none shadow-subtle overflow-hidden',
        containerSizes[size],
        disabled ? 'opacity-50 pointer-events-none' : '',
        className
      )}
    >
      <button
        type="button"
        onClick={onDecrement}
        className="h-full px-2 flex items-center justify-center hover:bg-action-primaryHover active:bg-action-primaryActive transition-colors active:scale-95 cursor-pointer"
        title="Decrease quantity"
      >
        {quantity === 1 ? (
          <Trash2 className="h-3 w-3" />
        ) : (
          <Minus className="h-3 w-3 stroke-[3]" />
        )}
      </button>

      <span className={cn('px-1.5 text-center font-extrabold tracking-tight', textSizes[size])}>
        {quantity}
      </span>

      <button
        type="button"
        onClick={onIncrement}
        disabled={quantity >= max}
        className="h-full px-2 flex items-center justify-center hover:bg-action-primaryHover active:bg-action-primaryActive transition-colors active:scale-95 disabled:opacity-50 cursor-pointer"
        title="Increase quantity"
      >
        <Plus className="h-3 w-3 stroke-[3]" />
      </button>
    </div>
  );
};
