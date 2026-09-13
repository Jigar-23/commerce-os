import React, { useState } from 'react';
import { Clock, ImageOff, Zap, AlertCircle } from 'lucide-react';
import { cn } from '@commerce-os/design-system';
import { CommerceCard } from './CommerceCard';
import { CommercePriceBlock } from './CommercePriceBlock';
import { CommerceQuantityControl } from './CommerceQuantityControl';
import { CommerceBadge } from './CommerceBadge';

export interface CommerceProductCardData {
  id: string;
  sku?: string;
  name: string;
  brandName?: string;
  category?: string;
  packSize?: string;
  therapeuticCategory?: string;
  price: number;
  discountedPrice?: number;
  mrp?: number;
  image?: string;
  badge?: string;
  rxRequirement?: string;
  coldChainRequired?: boolean;
  inStock?: boolean;
  stockCount?: number;
  expressDeliverySlaMins?: number;
}

export interface CommerceProductCardProps {
  product: CommerceProductCardData;
  quantity?: number;
  isLoading?: boolean;
  onAddToCart?: (product: CommerceProductCardData) => void;
  onIncrement?: (product: CommerceProductCardData) => void;
  onDecrement?: (product: CommerceProductCardData) => void;
  onCardClick?: (product: CommerceProductCardData) => void;
  className?: string;
}

export const CommerceProductCard: React.FC<CommerceProductCardProps> = ({
  product,
  quantity = 0,
  isLoading = false,
  onAddToCart,
  onIncrement,
  onDecrement,
  onCardClick,
  className = '',
}) => {
  const [imageError, setImageError] = useState(false);

  // 1. LOADING SKELETON STATE
  if (isLoading) {
    return (
      <div className={cn('bg-surface-card rounded-xl border border-border-subtle p-3 space-y-3 animate-pulse shadow-subtle', className)}>
        <div className="aspect-square w-full bg-surface-subtle rounded-lg" />
        <div className="space-y-1.5 pt-1">
          <div className="h-3 w-16 bg-surface-muted rounded-xs" />
          <div className="h-4 w-full bg-surface-muted rounded-xs" />
          <div className="h-3 w-20 bg-surface-subtle rounded-xs" />
        </div>
        <div className="pt-2 border-t border-border-subtle flex justify-between items-center">
          <div className="h-5 w-16 bg-surface-muted rounded-xs" />
          <div className="h-8 w-16 bg-surface-muted rounded-md" />
        </div>
      </div>
    );
  }

  const isOutOfStock = product.inStock === false || (product.stockCount !== undefined && product.stockCount <= 0);
  const price = product.discountedPrice || product.price || 100;
  const mrp = product.mrp || (product.price && product.price > price ? product.price : Math.round(price * 1.25));
  const slaMins = product.expressDeliverySlaMins || 10;
  const brand = product.brandName || product.category || product.therapeuticCategory || 'COMMERCE';

  return (
    <CommerceCard
      variant="default"
      padding="none"
      radius="xl"
      className={cn(
        'group relative flex flex-col justify-between p-3.5 bg-surface-card border border-border-subtle shadow-card hover:shadow-cardHover transition-all duration-200',
        isOutOfStock ? 'opacity-75 bg-surface-subtle' : 'hover:border-border-brand/40',
        quantity > 0 ? 'border-border-brand ring-1 ring-border-brand/40' : '',
        className
      )}
    >
      <div>
        {/* 1. PRODUCT IMAGE CONTAINER WITH INTEGRATED CORNER BADGES */}
        <div
          onClick={() => onCardClick && onCardClick(product)}
          className={cn(
            'relative mb-3 overflow-hidden rounded-lg bg-surface-subtle aspect-square flex items-center justify-center p-3 cursor-pointer select-none',
            isOutOfStock ? 'grayscale-[0.5]' : ''
          )}
        >
          {product.image && !imageError ? (
            <img
              src={product.image}
              alt={product.name}
              onError={() => setImageError(true)}
              className="h-full w-full object-contain transition-transform duration-300 group-hover:scale-105"
              loading="lazy"
            />
          ) : (
            <div className="flex flex-col items-center justify-center text-content-muted gap-1">
              <ImageOff className="h-8 w-8 stroke-[1.5]" />
              <span className="text-2xs font-bold text-content-muted">Product</span>
            </div>
          )}

          {/* SLA OVERLAY PILL (Top-Left) */}
          <div className="absolute top-2 left-2 flex items-center gap-1 text-2xs font-extrabold text-content-primary bg-surface-card/95 backdrop-blur-xs px-2 py-0.5 rounded-xs shadow-subtle border border-border-subtle">
            <Clock className="h-3 w-3 text-content-brand shrink-0" />
            <span>{slaMins}m</span>
          </div>

          {/* BADGE OVERLAY (Top-Right) */}
          {product.badge ? (
            <div className="absolute top-2 right-2">
              <CommerceBadge variant="brand" size="xs">
                {product.badge}
              </CommerceBadge>
            </div>
          ) : product.rxRequirement && product.rxRequirement !== 'NONE' && product.rxRequirement !== 'OTC' ? (
            <div className="absolute top-2 right-2">
              <CommerceBadge variant="danger" size="xs">
                Rx
              </CommerceBadge>
            </div>
          ) : null}

          {/* OUT OF STOCK OVERLAY */}
          {isOutOfStock && (
            <div className="absolute inset-0 bg-surface-inverse/60 backdrop-blur-[2px] flex items-center justify-center">
              <span className="bg-action-dangerBg text-action-dangerText font-extrabold text-2xs uppercase tracking-wider px-2.5 py-1 rounded-xs shadow-subtle">
                Out of Stock
              </span>
            </div>
          )}
        </div>

        {/* 2. BRAND & PRODUCT TITLE */}
        <div className="space-y-1 mb-3">
          <p className="text-2xs font-extrabold text-content-muted uppercase tracking-wider line-clamp-1">
            {brand}
          </p>

          <h3
            onClick={() => onCardClick && onCardClick(product)}
            className="text-xs sm:text-sm font-bold text-content-primary line-clamp-2 leading-snug group-hover:text-content-brand transition-colors cursor-pointer"
            title={product.name}
          >
            {product.name}
          </h3>

          {product.packSize && (
            <p className="text-2xs text-content-secondary font-medium line-clamp-1">{product.packSize}</p>
          )}
        </div>
      </div>

      {/* 3. FOOTER: PRICE & DOMINANT PURCHASE ACTION */}
      <div className="pt-2.5 border-t border-border-subtle flex items-center justify-between gap-2">
        <CommercePriceBlock price={price} mrp={mrp} size="sm" />

        <div className="shrink-0">
          {isOutOfStock ? (
            <button
              disabled
              className="h-8 px-2.5 text-2xs font-bold text-content-muted bg-surface-subtle rounded-md cursor-not-allowed border border-border-default"
            >
              Unavailable
            </button>
          ) : (
            <CommerceQuantityControl
              quantity={quantity}
              onIncrement={() => (onIncrement ? onIncrement(product) : onAddToCart && onAddToCart(product))}
              onDecrement={() => onDecrement && onDecrement(product)}
              onAdd={() => onAddToCart && onAddToCart(product)}
              size="md"
            />
          )}
        </div>
      </div>
    </CommerceCard>
  );
};
