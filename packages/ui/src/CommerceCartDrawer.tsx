import React from 'react';
import {
  X,
  ShoppingBag,
  ArrowRight,
  ShieldCheck,
  ThermometerSnowflake,
  FileText,
  Trash2,
} from 'lucide-react';
import { cn } from '@commerce-os/design-system';
import { CommerceQuantityControl } from './CommerceQuantityControl';
import { CommerceButton } from './CommerceButton';
import { CommercePriceBlock } from './CommercePriceBlock';

export interface CartDrawerItem {
  id?: string;
  sku?: string;
  productId?: string;
  name?: string;
  price?: number;
  unitPrice?: number;
  discountedPrice?: number;
  mrp?: number;
  quantity: number;
  image?: string;
  packSize?: string;
  rxRequired?: boolean;
  rxRequirement?: string;
  coldChain?: boolean;
  coldChainRequired?: boolean;
}

export interface CommerceCartDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  items: CartDrawerItem[];
  onIncrement: (item: CartDrawerItem) => void;
  onDecrement: (item: CartDrawerItem) => void;
  onClearCart?: () => void;
  onCheckout: () => void;
  isCheckingOut?: boolean;
  className?: string;
}

export const CommerceCartDrawer: React.FC<CommerceCartDrawerProps> = ({
  isOpen,
  onClose,
  items,
  onIncrement,
  onDecrement,
  onClearCart,
  onCheckout,
  isCheckingOut = false,
  className = '',
}) => {
  if (!isOpen) return null;

  const getItemPrice = (item: CartDrawerItem) => {
    if (item.discountedPrice !== undefined) return item.discountedPrice;
    if (item.unitPrice !== undefined) return item.unitPrice;
    if (item.price !== undefined) return item.price;
    return 100;
  };

  const getItemMrp = (item: CartDrawerItem) => {
    if (item.mrp !== undefined) return item.mrp;
    const price = getItemPrice(item);
    return Math.round(price * 1.25);
  };

  const getItemId = (item: CartDrawerItem) => {
    return item.sku || item.id || item.productId || item.name;
  };

  const isRx = (item: CartDrawerItem) => {
    if (item.rxRequired) return true;
    if (item.rxRequirement && item.rxRequirement !== 'NONE' && item.rxRequirement !== 'OTC') return true;
    return false;
  };

  const isCold = (item: CartDrawerItem) => {
    return Boolean(item.coldChain || item.coldChainRequired);
  };

  const subtotal = items.reduce((sum, item) => sum + getItemPrice(item) * item.quantity, 0);
  const totalMrp = items.reduce((sum, item) => sum + getItemMrp(item) * item.quantity, 0);
  const totalSavings = Math.max(0, totalMrp - subtotal);
  const deliveryFee = subtotal >= 499 ? 0 : 25;
  const coldChainFee = items.some(isCold) ? 15 : 0;
  const grandTotal = subtotal + deliveryFee + coldChainFee;
  const hasSpecialRequirement = items.some(isRx);

  return (
    <div className="fixed inset-0 z-50 overflow-hidden select-none">
      {/* BACKDROP */}
      <div
        onClick={onClose}
        className="absolute inset-0 bg-surface-inverse/60 backdrop-blur-xs transition-opacity animate-fade-in"
      />

      {/* DRAWER CONTAINER */}
      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <div className="w-screen max-w-md bg-surface-elevated shadow-modal flex flex-col justify-between animate-slide-left border-l border-border-subtle">
          {/* HEADER */}
          <div className="p-5 border-b border-border-subtle flex items-center justify-between bg-surface-subtle">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-md bg-surface-brandSubtle text-content-brand flex items-center justify-center">
                <ShoppingBag className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-base font-extrabold text-content-primary tracking-tight">Your Cart</h2>
                <p className="text-xs text-content-secondary font-medium">{items.length} items selected</p>
              </div>
            </div>

            <button
              type="button"
              onClick={onClose}
              className="p-1.5 rounded-md text-content-muted hover:text-content-primary hover:bg-surface-muted transition-colors cursor-pointer"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* ITEM LIST / EMPTY STATE */}
          <div className="flex-1 overflow-y-auto p-5 space-y-4">
            {items.length === 0 ? (
              <div className="py-16 text-center space-y-3">
                <div className="w-16 h-16 rounded-xl bg-surface-subtle flex items-center justify-center text-content-muted mx-auto">
                  <ShoppingBag className="h-8 w-8" />
                </div>
                <h3 className="text-base font-bold text-content-primary">Your cart is empty</h3>
                <p className="text-xs text-content-secondary max-w-xs mx-auto">
                  Explore products and essentials for 10-minute instant delivery.
                </p>
              </div>
            ) : (
              <>
                {/* SPECIAL ITEM NOTIFICATION IF APPLICABLE */}
                {hasSpecialRequirement && (
                  <div className="bg-surface-dangerSubtle border border-border-danger rounded-xl p-3.5 flex items-start gap-3">
                    <FileText className="h-5 w-5 text-content-danger shrink-0 mt-0.5" />
                    <div>
                      <p className="text-xs font-bold text-content-danger">Verification Required</p>
                      <p className="text-2xs text-content-danger font-medium mt-0.5">
                        This order contains regulated items that will be verified before final dispatch.
                      </p>
                    </div>
                  </div>
                )}

                {/* LINE ITEMS */}
                <div className="space-y-3">
                  {items.map((item) => {
                    const itemPrice = getItemPrice(item);
                    const itemMrp = getItemMrp(item);
                    const itemId = getItemId(item);

                    return (
                      <div
                        key={itemId}
                        className="flex items-center justify-between gap-3 p-3 rounded-xl border border-border-subtle bg-surface-card shadow-subtle"
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="w-14 h-14 rounded-md bg-surface-subtle flex items-center justify-center p-1.5 shrink-0 border border-border-subtle">
                            {item.image ? (
                              <img src={item.image} alt={item.name} className="h-full w-full object-contain" />
                            ) : (
                              <ShoppingBag className="h-5 w-5 text-content-muted" />
                            )}
                          </div>

                          <div className="min-w-0">
                            <h4 className="text-xs font-bold text-content-primary truncate" title={item.name}>
                              {item.name}
                            </h4>
                            {item.packSize && (
                              <p className="text-2xs text-content-muted font-medium">{item.packSize}</p>
                            )}
                            <div className="flex items-center gap-1.5 mt-1">
                              <span className="text-xs font-extrabold text-content-primary">
                                ₹{itemPrice.toFixed(0)}
                              </span>
                              {itemMrp > itemPrice && (
                                <span className="text-2xs text-content-muted line-through">
                                  ₹{itemMrp.toFixed(0)}
                                </span>
                              )}
                            </div>
                          </div>
                        </div>

                        <div className="shrink-0">
                          <CommerceQuantityControl
                            quantity={item.quantity}
                            onIncrement={() => onIncrement(item)}
                            onDecrement={() => onDecrement(item)}
                            size="xs"
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* BILL BREAKDOWN */}
                <div className="bg-surface-subtle rounded-xl p-4 space-y-2 border border-border-subtle text-xs">
                  <div className="flex justify-between text-content-secondary font-medium">
                    <span>Items Total (MRP)</span>
                    <span className="line-through text-content-muted">₹{totalMrp.toFixed(0)}</span>
                  </div>

                  {totalSavings > 0 && (
                    <div className="flex justify-between text-content-brand font-bold">
                      <span>Total Savings</span>
                      <span>-₹{totalSavings.toFixed(0)}</span>
                    </div>
                  )}

                  <div className="flex justify-between text-content-secondary font-medium">
                    <span>Delivery Fee</span>
                    <span>{deliveryFee === 0 ? <span className="text-content-brand font-bold">FREE</span> : `₹${deliveryFee}`}</span>
                  </div>

                  {coldChainFee > 0 && (
                    <div className="flex justify-between text-content-accent font-medium">
                      <span className="flex items-center gap-1">
                        <ThermometerSnowflake className="h-3.5 w-3.5" /> Specialized Temperature Handling
                      </span>
                      <span>₹{coldChainFee}</span>
                    </div>
                  )}

                  <div className="pt-2 border-t border-border-strong flex justify-between text-sm font-extrabold text-content-primary">
                    <span>To Pay</span>
                    <span>₹{grandTotal.toFixed(0)}</span>
                  </div>
                </div>
              </>
            )}
          </div>

          {/* FOOTER CTA */}
          {items.length > 0 && (
            <div className="p-5 border-t border-border-subtle bg-surface-card space-y-3">
              <div className="flex items-center justify-between text-xs font-extrabold text-content-primary">
                <span>Total Amount</span>
                <span className="text-base text-content-brand font-black">₹{grandTotal.toFixed(0)}</span>
              </div>

              <CommerceButton
                size="lg"
                fullWidth
                onClick={onCheckout}
                isLoading={isCheckingOut}
                icon={<ArrowRight className="h-4 w-4" />}
                iconPosition="right"
              >
                Proceed to Checkout
              </CommerceButton>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
