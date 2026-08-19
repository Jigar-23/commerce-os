'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { sellerApi, SellerSession } from '@/lib/apiClient';
import {
  BarChart3, Package, Layers, IndianRupee, XCircle, RefreshCw,
  Settings, ChevronRight, Plus, ShieldCheck, Store, LogOut
} from 'lucide-react';

interface SellerSidebarProps {
  activeTab: 'all' | 'orders' | 'inventory' | 'cod' | 'cancelled' | 'audit' | 'dashboard' | 'products' | 'pricing' | 'promotions' | 'settlements' | 'settings';
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
  const [session, setSession] = useState<SellerSession | null>(null);

  useEffect(() => {
    setSession(sellerApi.getSession());
    const t = setInterval(() => {
      setSession(sellerApi.getSession());
    }, 1000);
    return () => clearInterval(t);
  }, []);

  return (
    <>
      {/* 1. PRIMARY DARK NAVY ICON SIDEBAR */}
      <aside className="w-16 bg-surface-inverse flex flex-col items-center py-5 justify-between shrink-0 z-30 shadow-2xl">
        <div className="flex flex-col items-center space-y-6">
          <Link href="/">
            <div className="w-10 h-10 rounded-xl bg-action-speedBg flex items-center justify-center text-white font-black text-lg shadow-lg shadow-subtle cursor-pointer hover:scale-105 transition-transform">
              S
            </div>
          </Link>
          <div className="w-8 h-[1px] bg-surface-inverse my-2" />

          <Link href="/" title="Dashboard">
            <div className={`p-3 rounded-xl transition-all ${activeTab === 'all' || activeTab === 'dashboard' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`}>
              <BarChart3 className="w-5 h-5" />
            </div>
          </Link>

          <Link href="/orders" title="Orders">
            <div className={`p-3 rounded-xl transition-all ${activeTab === 'orders' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`}>
              <Package className="w-5 h-5" />
            </div>
          </Link>

          <Link href="/products" title="Catalog">
            <div className={`p-3 rounded-xl transition-all ${activeTab === 'products' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`}>
              <Store className="w-5 h-5" />
            </div>
          </Link>

          <Link href="/inventory" title="Inventory">
            <div className={`p-3 rounded-xl transition-all ${activeTab === 'inventory' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`}>
              <Layers className="w-5 h-5" />
            </div>
          </Link>

          <Link href="/cod" title="COD Ledger">
            <div className={`p-3 rounded-xl transition-all ${activeTab === 'cod' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`}>
              <IndianRupee className="w-5 h-5" />
            </div>
          </Link>

          <Link href="/cancelled" title="Cancelled">
            <div className={`p-3 rounded-xl transition-all ${activeTab === 'cancelled' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`}>
              <XCircle className="w-5 h-5" />
            </div>
          </Link>
        </div>

        <div className="flex flex-col items-center space-y-4">
          {onRefresh && (
            <button
              onClick={onRefresh}
              disabled={isLoading}
              title="Refresh Data"
              className="p-3 rounded-xl text-content-muted hover:text-white hover:bg-surface-inverse transition-all disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-content-accent' : ''}`} />
            </button>
          )}

          <Link href="/settings" title="Settings">
            <div className={`p-3 rounded-xl transition-all ${activeTab === 'settings' ? 'bg-surface-accentSubtle text-content-accent' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`}>
              <Settings className="w-5 h-5" />
            </div>
          </Link>
        </div>
      </aside>

      {/* 2. SECONDARY METRIC / WORKFLOW SUB-PANEL */}
      <aside className="w-64 bg-white border-r border-border-default flex flex-col justify-between shrink-0 shadow-sm">
        <div className="p-6 space-y-6">
          <div>
            <div className="flex items-center space-x-2">
              <div className="w-2.5 h-2.5 rounded-full bg-action-primaryBg animate-pulse" />
              <h1 className="text-xs font-black uppercase tracking-wider text-content-muted">Commerce OS</h1>
            </div>
            <p className="text-base font-black text-content-primary mt-1">Merchant Hub</p>
          </div>

          <nav className="space-y-1.5">
            <Link href="/orders" className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'orders' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}>
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

            <Link href="/products" className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'products' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}>
              <div className="flex items-center space-x-3">
                <Store className="w-4 h-4" />
                <span>Products & SKUs</span>
              </div>
              <ChevronRight className="w-3.5 h-3.5 opacity-40" />
            </Link>

            <Link href="/inventory" className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'inventory' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}>
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

            <Link href="/cod" className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'cod' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}>
              <div className="flex items-center space-x-3">
                <IndianRupee className="w-4 h-4" />
                <span>Cash on Delivery</span>
              </div>
              {pendingCodAmount > 0 && (
                <span className="px-2 py-0.5 text-2xs font-bold rounded-full bg-action-warningBg text-white">
                  ₹{pendingCodAmount.toFixed(0)}
                </span>
              )}
            </Link>

            <Link href="/cancelled" className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'cancelled' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}>
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

            <Link href="/audit" className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'audit' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}>
              <div className="flex items-center space-x-3">
                <ShieldCheck className="w-4 h-4" />
                <span>Audit Trail</span>
              </div>
              <ChevronRight className="w-3.5 h-3.5 opacity-40" />
            </Link>
          </nav>
        </div>

        {/* Dynamic Authenticated Merchant Footer */}
        <div className="p-4 border-t border-border-subtle bg-surface-subtle flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-action-speedBg text-white font-bold flex items-center justify-center text-sm shadow-md">
            {session?.merchantName ? session.merchantName.charAt(0) : 'M'}
          </div>
          <div className="overflow-hidden flex-1">
            <p className="text-xs font-bold text-content-primary truncate" suppressHydrationWarning>
              {session ? session.merchantName : 'Rewari Operations Hub'}
            </p>
            <p className="text-2xs text-content-muted truncate" suppressHydrationWarning>
              {session ? session.storeName : 'Rewari Central Hub'}
            </p>
          </div>
        </div>
      </aside>
    </>
  );
}
