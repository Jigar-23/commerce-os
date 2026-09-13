import React from 'react';
import { MedicineProduct } from '@commerce-os/types';
import { cn } from '@commerce-os/design-system';
import { ShieldCheck, FileText, Clock, Plus, Check, ArrowLeftRight } from 'lucide-react';

export interface MedicineCardProps {
  medicine: MedicineProduct;
  onAddToCart?: (medicine: MedicineProduct) => void;
  onViewSubstitutes?: (medicine: MedicineProduct) => void;
  isAdded?: boolean;
  className?: string;
}

export const MedicineCard: React.FC<MedicineCardProps> = ({
  medicine,
  onAddToCart,
  onViewSubstitutes,
  isAdded = false,
  className,
}) => {
  const isRxRequired = medicine.rxRequirement !== 'OTC';
  const composition = medicine.saltCompositions?.[0]?.saltName || medicine.brandName || 'Pharmaceutical';

  return (
    <div
      className={cn(
        'group relative flex flex-col justify-between rounded-xl border border-border-default bg-surface-card p-4 transition-all hover:border-border-brand/40 hover:shadow-cardHover',
        className
      )}
    >
      <div>
        {/* TOP BADGES: Rx Requirement + 10-Min Delivery SLA */}
        <div className="flex items-center justify-between gap-2 mb-3">
          {isRxRequired ? (
            <span className="inline-flex items-center gap-1 rounded-xs bg-surface-dangerSubtle px-2 py-1 text-2xs font-semibold text-content-danger">
              <FileText className="h-3.5 w-3.5" />
              Rx Required
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 rounded-xs bg-surface-successSubtle px-2 py-1 text-2xs font-semibold text-content-success">
              <ShieldCheck className="h-3.5 w-3.5" />
              OTC Safe
            </span>
          )}

          <span className="inline-flex items-center gap-1 text-2xs font-medium text-content-secondary">
            <Clock className="h-3.5 w-3.5 text-content-accent" />
            {medicine.expressDeliverySlaMins} mins
          </span>
        </div>

        {/* MEDICINE IMAGE & DETAILS */}
        <div className="mb-3 flex gap-3">
          <div className="h-20 w-20 flex-shrink-0 overflow-hidden rounded-md bg-surface-subtle">
            <img
              src={medicine.images?.[0] || 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300'}
              alt={medicine.name}
              className="h-full w-full object-contain p-1 transition-transform group-hover:scale-105"
            />
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-2xs font-bold uppercase tracking-wider text-content-muted">
              {composition}
            </div>
            <h3 className="line-clamp-2 text-sm font-bold text-content-primary">
              {medicine.name}
            </h3>
            <p className="mt-0.5 text-2xs text-content-secondary">{medicine.packSize}</p>
          </div>
        </div>
      </div>

      {/* FOOTER: PRICE & ACTIONS */}
      <div className="flex items-center justify-between border-t border-border-subtle pt-3">
        <div>
          <div className="flex items-baseline gap-1.5">
            <span className="text-base font-extrabold text-content-primary">
              ₹{medicine.discountedPrice}
            </span>
            {medicine.price > medicine.discountedPrice && (
              <span className="text-2xs text-content-muted line-through">
                ₹{medicine.price}
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-2">
          {onViewSubstitutes && (
            <button
              onClick={() => onViewSubstitutes(medicine)}
              className="inline-flex items-center gap-1 rounded-sm border border-border-default px-2 py-1 text-2xs font-semibold text-content-secondary hover:bg-surface-subtle transition-colors cursor-pointer"
            >
              <ArrowLeftRight className="h-3 w-3" />
              Substitutes
            </button>
          )}

          {onAddToCart && (
            <button
              onClick={() => onAddToCart(medicine)}
              className={cn(
                'inline-flex items-center gap-1 rounded-sm px-3 py-1.5 text-xs font-bold transition-all cursor-pointer',
                isAdded
                  ? 'bg-action-primaryBg text-action-primaryText'
                  : 'bg-surface-brandSubtle text-content-brand hover:bg-brand-100'
              )}
            >
              {isAdded ? (
                <>
                  <Check className="h-3.5 w-3.5" /> Added
                </>
              ) : (
                <>
                  <Plus className="h-3.5 w-3.5" /> Add
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
