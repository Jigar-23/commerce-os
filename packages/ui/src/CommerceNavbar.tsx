import React from 'react';
import {
  Zap,
  MapPin,
  ChevronDown,
  ShoppingBag,
  Package,
  User,
  Search,
} from 'lucide-react';
import { cn } from '@commerce-os/design-system';
import { CommerceSearchField } from './CommerceInput';

export interface CommerceNavbarProps {
  brandName?: string;
  brandTagline?: string;
  brandIcon?: React.ReactNode;
  locationAddress?: string;
  onOpenLocationModal?: () => void;
  searchQuery?: string;
  onSearchChange?: (query: string) => void;
  showSearchBar?: boolean;
  cartItemCount?: number;
  cartTotalAmount?: number;
  onOpenCart?: () => void;
  ordersHref?: string;
  profileHref?: string;
  brandHref?: string;
  className?: string;
}

export const CommerceNavbar: React.FC<CommerceNavbarProps> = ({
  brandName = 'CommerceOS',
  brandTagline = 'Instant Commerce Platform',
  brandIcon,
  locationAddress = 'Select delivery location',
  onOpenLocationModal,
  searchQuery = '',
  onSearchChange,
  showSearchBar = true,
  cartItemCount = 0,
  cartTotalAmount = 0,
  onOpenCart,
  ordersHref = '/orders',
  profileHref = '/profile',
  brandHref = '/',
  className = '',
}) => {
  return (
    <header className={cn('sticky top-0 z-40 bg-surface-card/95 backdrop-blur-md border-b border-border-subtle shadow-subtle', className)}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 sm:h-20 gap-3 sm:gap-6">
          {/* BRAND LOGO & LOCATION BLOCK */}
          <div className="flex items-center gap-3 sm:gap-6 min-w-0">
            <a href={brandHref} className="flex items-center gap-2.5 shrink-0 group select-none">
              <div className="w-10 h-10 rounded-xl bg-action-primaryBg flex items-center justify-center text-action-primaryText font-black text-lg shadow-subtle group-hover:scale-105 transition-transform">
                {brandIcon || <Zap className="h-5 w-5 fill-current" />}
              </div>
              <div className="hidden sm:block">
                <span className="text-base font-black tracking-tight text-content-primary leading-none flex items-center gap-0.5">
                  {brandName}
                </span>
                <span className="text-2xs font-bold text-content-muted uppercase tracking-widest block mt-0.5">
                  {brandTagline}
                </span>
              </div>
            </a>

            {/* LOCATION SELECTOR PILL */}
            <button
              type="button"
              onClick={onOpenLocationModal}
              className="flex items-center gap-2 p-1.5 sm:px-3 sm:py-1.5 rounded-lg border border-border-default hover:border-border-brand hover:bg-surface-brandSubtle transition-all text-left max-w-[180px] sm:max-w-[260px] active:scale-98 cursor-pointer select-none"
            >
              <div className="w-7 h-7 rounded-md bg-surface-brandSubtle flex items-center justify-center text-content-brand shrink-0">
                <MapPin className="h-4 w-4" />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-1">
                  <span className="text-2xs font-extrabold text-content-brand uppercase tracking-wider">
                    ⚡ 10 MINS
                  </span>
                  <ChevronDown className="h-3 w-3 text-content-muted" />
                </div>
                <p className="text-xs font-bold text-content-primary truncate leading-tight">
                  {locationAddress}
                </p>
              </div>
            </button>
          </div>

          {/* DESKTOP SEARCH BAR */}
          {showSearchBar && onSearchChange && (
            <div className="hidden md:flex flex-1 max-w-lg">
              <CommerceSearchField
                value={searchQuery}
                onChange={onSearchChange}
                placeholder="Search products, brands & daily essentials..."
                size="md"
              />
            </div>
          )}

          {/* RIGHT ACTION CONTROLS */}
          <div className="flex items-center gap-2 sm:gap-3 shrink-0">
            {/* ORDERS SHORTCUT */}
            <a
              href={ordersHref}
              className="flex items-center gap-1.5 px-3 py-2 text-xs font-bold text-content-secondary hover:text-content-brand hover:bg-surface-subtle rounded-md transition-all"
            >
              <Package className="h-4 w-4 text-content-muted" />
              <span className="hidden sm:inline">Orders</span>
            </a>

            {/* ACCOUNT SHORTCUT */}
            <a
              href={profileHref}
              className="flex items-center gap-1.5 px-3 py-2 text-xs font-bold text-content-secondary hover:text-content-brand hover:bg-surface-subtle rounded-md transition-all"
            >
              <User className="h-4 w-4 text-content-muted" />
              <span className="hidden sm:inline">Account</span>
            </a>

            {/* CART BUTTON WITH LIVE COUNTER */}
            {onOpenCart && (
              <button
                type="button"
                onClick={onOpenCart}
                className="relative flex items-center gap-2 px-3.5 sm:px-4 py-2 sm:py-2.5 bg-action-primaryBg hover:bg-action-primaryHover active:bg-action-primaryActive text-action-primaryText rounded-lg font-extrabold text-xs sm:text-sm shadow-subtle active:scale-95 transition-all cursor-pointer select-none"
              >
                <ShoppingBag className="h-4 w-4 shrink-0" />
                <div className="flex items-center gap-1.5">
                  <span className="hidden xs:inline">Cart</span>
                  {cartItemCount > 0 && (
                    <span className="bg-white text-content-primary px-1.5 py-0.2 rounded-full text-2xs font-black min-w-[20px] text-center">
                      {cartItemCount}
                    </span>
                  )}
                </div>
                {cartTotalAmount > 0 && (
                  <span className="border-l border-white/20 pl-2 text-xs font-black">
                    ₹{cartTotalAmount.toFixed(0)}
                  </span>
                )}
              </button>
            )}
          </div>
        </div>

        {/* MOBILE SEARCH BAR (VISIBLE ON SMALL SCREENS) */}
        {showSearchBar && onSearchChange && (
          <div className="pb-3 md:hidden">
            <CommerceSearchField
              value={searchQuery}
              onChange={onSearchChange}
              placeholder="Search products & essentials..."
              size="sm"
            />
          </div>
        )}
      </div>
    </header>
  );
};
