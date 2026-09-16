'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { sellerApi, SellerSession } from '@/lib/apiClient';
import {
  BarChart3, Package, Layers, IndianRupee, XCircle, RefreshCw,
  Settings, ChevronRight, ShieldCheck, Store, LogOut, Bike, MapPin
} from 'lucide-react';

interface SellerSidebarProps {
  activeTab: 'all' | 'orders' | 'inventory' | 'cod' | 'cancelled' | 'audit' | 'dashboard' | 'products' | 'pricing' | 'promotions' | 'settlements' | 'settings' | 'riders' | 'stores';
  ordersCount?: number;
  inventoryCount?: number;
  pendingCodAmount?: number;
  cancelledCount?: number;
  onRefresh?: () => void;
  isLoading?: boolean;
}

export default function SellerSidebar({
  activeTab,
  ordersCount = 0,
  inventoryCount = 0,
  pendingCodAmount = 0,
  cancelledCount = 0,
  onRefresh,
  isLoading = false,
}: SellerSidebarProps) {
  const router = useRouter();
  const [session, setSession] = useState<SellerSession | null>(null);

  useEffect(() => {
    setSession(sellerApi.getSession());
    const t = setInterval(() => {
      setSession(sellerApi.getSession());
    }, 1000);
    return () => clearInterval(t);
  }, []);

  const handleLogout = () => {
    sellerApi.clearSession();
    router.push('/login');
  };

  return (
    <aside className="w-64 bg-white border-r border-border-default flex flex-col justify-between shrink-0 shadow-sm z-20">
      <div className="p-5 space-y-6 overflow-y-auto">
        <div className="flex items-center justify-between">
          <Link href="/" className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-action-speedBg flex items-center justify-center text-white font-black text-base shadow-md shadow-subtle cursor-pointer hover:scale-105 transition-transform">
              S
            </div>
            <div>
              <div className="flex items-center space-x-1.5">
                <div className="w-2 h-2 rounded-full bg-action-speedBg animate-pulse" />
                <h1 className="text-2xs font-black uppercase tracking-wider text-content-muted">Commerce OS</h1>
              </div>
              <p className="text-sm font-black text-content-primary">Rewari Central Hub</p>
            </div>
          </Link>

          {onRefresh && (
            <button
              onClick={onRefresh}
              disabled={isLoading}
              title="Refresh Data"
              className="p-2 rounded-xl text-content-muted hover:text-content-primary hover:bg-surface-subtle transition-all disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-content-accent' : ''}`} />
            </button>
          )}
        </div>

        <nav className="space-y-1">
          <Link
            href="/"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'all' || activeTab === 'dashboard'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <BarChart3 className="w-4 h-4" />
              <span>Dashboard</span>
            </div>
            <ChevronRight className="w-3.5 h-3.5 opacity-40" />
          </Link>

          <Link
            href="/orders"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'orders'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <Package className="w-4 h-4" />
              <span>Orders</span>
            </div>
            {ordersCount > 0 && (
              <span className="px-2 py-0.5 text-2xs font-bold rounded-full bg-action-speedBg text-white">
                {ordersCount}
              </span>
            )}
          </Link>

          <Link
            href="/riders"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'riders'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <Bike className="w-4 h-4" />
              <span>Fleet & Riders</span>
            </div>
            <ChevronRight className="w-3.5 h-3.5 opacity-40" />
          </Link>

          <Link
            href="/products"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'products'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <Store className="w-4 h-4" />
              <span>Products & SKUs</span>
            </div>
            <ChevronRight className="w-3.5 h-3.5 opacity-40" />
          </Link>

          <Link
            href="/inventory"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'inventory'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <Layers className="w-4 h-4" />
              <span>Stock Inventory</span>
            </div>
            {inventoryCount > 0 && (
              <span className="px-2 py-0.5 text-2xs font-bold rounded-full bg-surface-subtle text-content-secondary">
                {inventoryCount}
              </span>
            )}
          </Link>

          <Link
            href="/cod"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'cod'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <IndianRupee className="w-4 h-4" />
              <span>Cash on Delivery</span>
            </div>
            {Number(pendingCodAmount || 0) > 0 && (
              <span className="px-2 py-0.5 text-2xs font-bold rounded-full bg-action-warningBg text-white">
                ₹{Number(pendingCodAmount || 0).toFixed(0)}
              </span>
            )}
          </Link>

          <Link
            href="/cancelled"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'cancelled'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <XCircle className="w-4 h-4" />
              <span>Cancelled Orders</span>
            </div>
            {cancelledCount > 0 && (
              <span className="px-2 py-0.5 text-2xs font-bold rounded-full bg-action-dangerBg text-white">
                {cancelledCount}
              </span>
            )}
          </Link>

          <Link
            href="/audit"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'audit'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <ShieldCheck className="w-4 h-4" />
              <span>Audit Trail</span>
            </div>
            <ChevronRight className="w-3.5 h-3.5 opacity-40" />
          </Link>

          <Link
            href="/settings"
            className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
              activeTab === 'settings'
                ? 'bg-surface-accentSubtle text-content-accent font-bold'
                : 'text-content-secondary hover:bg-surface-subtle'
            }`}
          >
            <div className="flex items-center space-x-3">
              <Settings className="w-4 h-4" />
              <span>Settings</span>
            </div>
            <ChevronRight className="w-3.5 h-3.5 opacity-40" />
          </Link>
        </nav>
      </div>

      {/* Dynamic Authenticated Merchant Footer */}
      <div className="p-4 border-t border-border-subtle bg-surface-subtle flex items-center justify-between space-x-3">
        <div className="flex items-center space-x-3 overflow-hidden">
          <div className="w-9 h-9 rounded-xl bg-action-speedBg text-white font-bold flex items-center justify-center text-sm shadow-md shrink-0">
            {session?.merchantName ? session.merchantName.charAt(0).toUpperCase() : 'M'}
          </div>
          <div className="overflow-hidden flex-1">
            <p className="text-xs font-bold text-content-primary truncate" suppressHydrationWarning>
              {session?.merchantName || 'Rewari Merchant'}
            </p>
            <p className="text-2xs text-content-muted truncate" suppressHydrationWarning>
              Rewari Central Hub (Single Store)
            </p>
          </div>
        </div>

        <button
          onClick={handleLogout}
          title="Sign Out"
          className="p-2 text-content-muted hover:text-content-danger hover:bg-white rounded-lg transition-colors shrink-0"
        >
          <LogOut className="w-4 h-4" />
        </button>
      </div>
    </aside>
  );
}
