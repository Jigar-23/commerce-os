'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  Navigation,
  ChevronDown,
  Search,
  ShoppingCart,
  User,
  ShoppingBag,
  LayoutGrid,
  FileText,
  Clock,
  Zap,
  ShieldCheck,
  Bell,
  Camera,
} from 'lucide-react';
import { useCart } from '@/lib/cart-store';
import { useSession } from '@/lib/session-store';
import FlagshipSearchModal from '@/components/FlagshipSearchModal';
import DeliveryAddressMapModal from '@/components/DeliveryAddressMapModal';

interface UniversalHeaderProps {
  locationLabel?: string;
  etaMinutes?: number;
  onLocationClick?: () => void;
  onUploadRxClick?: () => void;
}

export default function UniversalHeader({
  locationLabel = 'Rewari Central',
  etaMinutes = 10,
  onLocationClick,
  onUploadRxClick,
}: UniversalHeaderProps) {
  const pathname = usePathname();
  const router = useRouter();
  const sessionUser = useSession((s) => s.user);
  const lines = useCart((s) => s.lines);
  const cartCount = lines.reduce((sum, l) => sum + l.quantity, 0);
  const cartSubtotal = lines.reduce((sum, l) => sum + (l.unitPrice || 0) * l.quantity, 0);

  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isMapModalOpen, setIsMapModalOpen] = useState(false);

  const navLinks = [
    { label: 'Home', href: '/', icon: null },
    { label: 'Categories', href: '/categories', icon: LayoutGrid },
    { label: 'Orders', href: '/orders', icon: ShoppingBag },
    { label: 'Prescriptions', href: '/prescriptions', icon: FileText },
    { label: 'Refills', href: '/refills', icon: Clock },
    { label: 'Dashboard', href: '/account', icon: User, highlight: true },
  ];

  const handleLocationClick = () => {
    if (onLocationClick) {
      onLocationClick();
    } else {
      setIsMapModalOpen(true);
    }
  };

  const handleUploadRx = () => {
    if (onUploadRxClick) {
      onUploadRxClick();
    } else {
      router.push('/prescriptions');
    }
  };

  return (
    <>
      <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-xs">
        {/* Top Announcement Bar */}
        <div className="bg-[#0E675E] text-white px-4 py-1 text-[11px] font-semibold flex items-center justify-between">
          <div className="max-w-7xl mx-auto w-full flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="inline-flex items-center gap-1 bg-white/20 px-2 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider text-emerald-100">
                <Zap className="w-2.5 h-2.5 fill-yellow-300 text-yellow-300" />
                10-Min Hyperlocal
              </span>
              <span className="hidden sm:inline text-white/90">
                Instant Medicine Delivery • WHO-GMP Certified Quality • Rewari Hub
              </span>
            </div>
            <div className="flex items-center gap-4 text-white/80">
              <span className="hidden md:inline">24×7 Pharmacist Helpline: +91 1800-208-9999</span>
              <Link href="/account" className="hover:text-white transition-colors">
                {sessionUser ? `Hi, ${sessionUser.fullName?.split(' ')[0] || 'Customer'}` : 'Sign In'}
              </Link>
            </div>
          </div>
        </div>

        {/* Main Desktop & Tablet Header Row */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-2.5 flex items-center justify-between gap-4">
          {/* Logo & Brand */}
          <div className="flex items-center gap-6 shrink-0">
            <Link href="/" className="flex items-center gap-2 group">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#0E675E] to-[#0A4F48] flex items-center justify-center text-white font-black text-lg shadow-sm group-hover:scale-105 transition-transform">
                +
              </div>
              <div className="flex flex-col">
                <span className="text-base sm:text-lg font-black tracking-tight text-slate-900 group-hover:text-[#0E675E] transition-colors leading-none">
                  Commerce<span className="text-[#0E675E]">OS</span>
                </span>
                <span className="text-[10px] font-extrabold uppercase tracking-wider text-slate-400">
                  Universal Health
                </span>
              </div>
            </Link>

            {/* Delivery Location Trigger */}
            <button
              onClick={handleLocationClick}
              className="hidden lg:flex items-center gap-2 px-3 py-1.5 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200/80 transition-all text-left cursor-pointer"
            >
              <div className="w-7 h-7 rounded-lg bg-emerald-100 text-[#0E675E] flex items-center justify-center shrink-0">
                <Navigation className="w-3.5 h-3.5 fill-current rotate-45" />
              </div>
              <div className="flex flex-col">
                <div className="flex items-center gap-1">
                  <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Deliver to</span>
                  <span className="inline-flex items-center gap-0.5 px-1 py-0.2 rounded bg-emerald-50 text-[#0E675E] text-[9px] font-black uppercase">
                    <Zap className="w-2 h-2 fill-current" />
                    {etaMinutes}m
                  </span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-xs font-bold text-slate-800 max-w-[130px] truncate">{locationLabel}</span>
                  <ChevronDown className="w-3 h-3 text-slate-400 shrink-0" />
                </div>
              </div>
            </button>
          </div>

          {/* Search Bar Capsule (Desktop & Tablet) */}
          <div className="flex-1 max-w-xl mx-2">
            <div
              onClick={() => setIsSearchOpen(true)}
              className="w-full h-10 px-3.5 bg-slate-50 hover:bg-slate-100/80 border border-slate-200/90 rounded-full flex items-center gap-2.5 cursor-pointer text-slate-400 shadow-2xs transition-all hover:border-slate-300"
            >
              <Search className="w-4 h-4 text-[#0E675E] shrink-0" />
              <span className="text-xs sm:text-sm text-slate-500 font-medium truncate flex-1">
                Search medicines, salts, health products (e.g. Dolo 650, Pan 40)...
              </span>
              <kbd className="hidden sm:inline-block px-1.5 py-0.5 text-[10px] font-semibold text-slate-400 bg-white border border-slate-200 rounded-md">
                ⌘K
              </kbd>
            </div>
          </div>

          {/* Quick Actions & Navigation (Desktop) */}
          <div className="flex items-center gap-2 sm:gap-3 shrink-0">
            {/* Upload Rx Button */}
            <button
              onClick={handleUploadRx}
              className="hidden md:flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-red-200 bg-red-50/60 hover:bg-red-50 text-red-700 text-xs font-bold transition-all cursor-pointer"
            >
              <Camera className="w-3.5 h-3.5 text-red-600" />
              <span>Upload Rx</span>
            </button>

            {/* Desktop Nav Links */}
            <nav className="hidden xl:flex items-center gap-1 text-xs font-bold">
              <Link
                href="/categories"
                className={`px-2.5 py-1.5 rounded-lg transition-colors ${
                  pathname === '/categories' ? 'text-[#0E675E] bg-emerald-50' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Categories
              </Link>
              <Link
                href="/orders"
                className={`px-2.5 py-1.5 rounded-lg transition-colors ${
                  pathname === '/orders' ? 'text-[#0E675E] bg-emerald-50' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Orders
              </Link>
              <Link
                href="/prescriptions"
                className={`px-2.5 py-1.5 rounded-lg transition-colors ${
                  pathname === '/prescriptions' ? 'text-[#0E675E] bg-emerald-50' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Prescriptions
              </Link>
              <Link
                href="/refills"
                className={`px-2.5 py-1.5 rounded-lg transition-colors ${
                  pathname === '/refills' ? 'text-[#0E675E] bg-emerald-50' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Refills
              </Link>
            </nav>

            {/* Account / Dashboard Pill */}
            <Link
              href="/account"
              className={`flex items-center gap-2 px-3 py-1.5 rounded-xl border transition-all ${
                pathname === '/account' || pathname === '/profile'
                  ? 'bg-emerald-50 text-[#0E675E] border-emerald-300 font-bold'
                  : 'bg-white hover:bg-slate-50 text-slate-700 border-slate-200 font-semibold'
              }`}
            >
              <div className="w-6 h-6 rounded-full bg-[#0E675E] text-white flex items-center justify-center text-xs font-black">
                {sessionUser?.fullName ? sessionUser.fullName[0].toUpperCase() : 'U'}
              </div>
              <span className="text-xs hidden sm:inline">
                {sessionUser?.fullName ? sessionUser.fullName.split(' ')[0] : 'Dashboard'}
              </span>
            </Link>

            {/* Cart Trigger */}
            <Link
              href="/checkout"
              className="relative flex items-center gap-2 px-3 py-1.5 rounded-xl bg-[#0E675E] hover:bg-[#0A4F48] text-white text-xs font-bold transition-all shadow-xs"
            >
              <ShoppingCart className="w-4 h-4" />
              <span className="hidden sm:inline">
                {cartCount > 0 ? `₹${cartSubtotal.toFixed(0)}` : 'Cart'}
              </span>
              {cartCount > 0 && (
                <span className="w-5 h-5 rounded-full bg-yellow-400 text-slate-900 text-[11px] font-black flex items-center justify-center">
                  {cartCount}
                </span>
              )}
            </Link>
          </div>
        </div>
      </header>

      {/* Global Modals */}
      <FlagshipSearchModal isOpen={isSearchOpen} onClose={() => setIsSearchOpen(false)} />
      <DeliveryAddressMapModal
        isOpen={isMapModalOpen}
        onClose={() => setIsMapModalOpen(false)}
        onSelectAddress={() => setIsMapModalOpen(false)}
      />
    </>
  );
}
