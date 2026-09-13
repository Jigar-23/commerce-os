import React from 'react';
import { cn } from '@commerce-os/design-system';

export interface CommercePriceBlockProps {
  price: number;
  mrp?: number;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  showSavings?: boolean;
  className?: string;
}

export const CommercePriceBlock: React.FC<CommercePriceBlockProps> = ({
  price,
  mrp,
  size = 'md',
  showSavings = false,
  className = '',
}) => {
  const hasDiscount = mrp && mrp > price;
  const discountPercent = hasDiscount ? Math.round(((mrp - price) / mrp) * 100) : 0;
  const savingsAmount = hasDiscount ? mrp - price : 0;

  const sizeStyles = {
    sm: { price: 'text-sm font-extrabold', mrp: 'text-2xs', badge: 'text-2xs px-1 py-0.5' },
    md: { price: 'text-base font-extrabold', mrp: 'text-xs', badge: 'text-2xs px-1.5 py-0.5' },
    lg: { price: 'text-xl font-black', mrp: 'text-xs sm:text-sm', badge: 'text-xs px-2 py-0.5' },
    xl: { price: 'text-2xl sm:text-3xl font-black', mrp: 'text-sm sm:text-base', badge: 'text-xs px-2.5 py-1' },
  };

  return (
    <div className={cn('flex flex-col', className)}>
      <div className="flex items-baseline gap-1.5 flex-wrap">
        <span className={cn('text-content-primary tracking-tight font-sans', sizeStyles[size].price)}>
          ₹{price.toFixed(price % 1 === 0 ? 0 : 2)}
        </span>

        {hasDiscount && (
          <span className={cn('text-content-muted line-through font-medium', sizeStyles[size].mrp)}>
            ₹{mrp.toFixed(mrp % 1 === 0 ? 0 : 2)}
          </span>
        )}

        {discountPercent > 0 && (
          <span
            className={cn(
              'bg-surface-brandSubtle text-content-brand font-extrabold rounded-xs tracking-tight',
              sizeStyles[size].badge
            )}
          >
            {discountPercent}% OFF
          </span>
        )}
      </div>

      {showSavings && savingsAmount > 0 && (
        <span className="text-2xs font-bold text-content-brand mt-0.5">
          Save ₹{savingsAmount.toFixed(0)}
        </span>
      )}
    </div>
  );
};
