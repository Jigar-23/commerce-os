'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { sellerApi, SellerSession } from '@/lib/apiClient';
import {
  LayoutDashboard,
  Package,
  Layers,
  IndianRupee,
  XCircle,
  RefreshCw,
  Settings,
  ShieldCheck,
  Store,
  LogOut,
  Bike,
  MapPin,
  Users,
  Percent,
  Tag,
  Bell,
  Wallet,
  CheckCircle2,
  Construction,
  ArrowRight,
} from 'lucide-react';

interface SellerSidebarProps {
  activeTab:
    | 'all'
    | 'orders'
    | 'inventory'
    | 'cod'
    | 'cancelled'
    | 'audit'
    | 'dashboard'
    | 'products'
    | 'pricing'
    | 'promotions'
    | 'settlements'
    | 'settings'
    | 'riders'
    | 'stores'
    | 'users'
    | 'notifications';
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
  const [showSettlementsModal, setShowSettlementsModal] = useState(false);

  useEffect(() => {
    setSession(sellerApi.getSession());
    const t = setInterval(() => {
      setSession(sellerApi.getSession());
    }, 1000);
    return () => clearInterval(t);
  }, []);

  const handleLogout = () => {
    if (confirm('Are you sure you want to sign out of CommerceOS Seller Hub?')) {
      sellerApi.clearSession();
      router.push('/login');
    }
  };

  const isTabActive = (tab: string) => {
    if (tab === 'dashboard') return activeTab === 'all' || activeTab === 'dashboard';
    return activeTab === tab;
  };

  const navItemClass = (tab: string) => {
    const active = isTabActive(tab);
    return `group flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium transition-all ${
      active
        ? 'bg-slate-900 text-white shadow-sm'
        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/70'
    }`;
  };

  const iconClass = (tab: string) => {
    const active = isTabActive(tab);
    return `w-4 h-4 shrink-0 transition-colors ${
      active ? 'text-white' : 'text-slate-400 group-hover:text-slate-700'
    }`;
  };

  return (
    <aside className="w-64 bg-white border-r border-slate-200/80 flex flex-col justify-between shrink-0 shadow-sm z-20 select-none">
      {/* Scrollable Nav Content */}
      <div className="flex-1 overflow-y-auto px-4 py-5 space-y-6 scrollbar-thin">
        {/* Hub Header & Status */}
        <div className="flex items-center justify-between px-1">
          <Link href="/" className="flex items-center space-x-3 group">
            <div className="w-9 h-9 rounded-xl bg-slate-900 text-white flex items-center justify-center font-bold text-sm shadow-sm group-hover:scale-105 transition-transform">
              S
            </div>
            <div>
              <p className="text-xs font-semibold text-slate-900 leading-tight">Rewari Central Hub</p>
              <div className="flex items-center space-x-1.5 mt-0.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                <span className="text-[11px] font-medium text-emerald-600">Online • 10m SLA</span>
              </div>
            </div>
          </Link>

          {onRefresh && (
            <button
              onClick={onRefresh}
              disabled={isLoading}
              title="Sync store data"
              className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all disabled:opacity-40"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-slate-900' : ''}`} />
            </button>
          )}
        </div>

        {/* Grouped Navigation */}
        <nav className="space-y-5">
          {/* Section 1: Operations */}
          <div className="space-y-1">
            <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-3 pb-1">
              Operations
            </p>

            <Link href="/" className={navItemClass('dashboard')}>
              <div className="flex items-center space-x-2.5">
                <LayoutDashboard className={iconClass('dashboard')} />
                <span>Dashboard</span>
              </div>
            </Link>

            <Link href="/orders" className={navItemClass('orders')}>
              <div className="flex items-center space-x-2.5">
                <Package className={iconClass('orders')} />
                <span>Live Orders</span>
              </div>
              {ordersCount > 0 && (
                <span
                  className={`px-2 py-0.5 text-[10px] font-semibold rounded-full ${
                    isTabActive('orders')
                      ? 'bg-white/20 text-white'
                      : 'bg-indigo-50 text-indigo-700 border border-indigo-200/50'
                  }`}
                >
                  {ordersCount}
                </span>
              )}
            </Link>

            <Link href="/riders" className={navItemClass('riders')}>
              <div className="flex items-center space-x-2.5">
                <Bike className={iconClass('riders')} />
                <span>Fleet & Riders</span>
              </div>
            </Link>
          </div>

          {/* Section 2: Catalog & Store */}
          <div className="space-y-1">
            <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-3 pb-1">
              Catalog & Store
            </p>

            <Link href="/inventory" className={navItemClass('inventory')}>
              <div className="flex items-center space-x-2.5">
                <Layers className={iconClass('inventory')} />
                <span>Stock Levels</span>
              </div>
              {inventoryCount > 0 && (
                <span
                  className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${
                    isTabActive('inventory') ? 'bg-white/20 text-white' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {inventoryCount}
                </span>
              )}
            </Link>

            <Link href="/products" className={navItemClass('products')}>
              <div className="flex items-center space-x-2.5">
                <Store className={iconClass('products')} />
                <span>Product Master</span>
              </div>
            </Link>

            <Link href="/stores" className={navItemClass('stores')}>
              <div className="flex items-center space-x-2.5">
                <MapPin className={iconClass('stores')} />
                <span>Hubs & Stores</span>
              </div>
            </Link>
          </div>

          {/* Section 3: Finances */}
          <div className="space-y-1">
            <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-3 pb-1">
              Finances
            </p>

            <Link href="/cod" className={navItemClass('cod')}>
              <div className="flex items-center space-x-2.5">
                <IndianRupee className={iconClass('cod')} />
                <span>Cash on Delivery</span>
              </div>
              {Number(pendingCodAmount || 0) > 0 && (
                <span
                  className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${
                    isTabActive('cod')
                      ? 'bg-white/20 text-white'
                      : 'bg-amber-50 text-amber-700 border border-amber-200/50'
                  }`}
                >
                  ₹{Number(pendingCodAmount || 0).toFixed(0)}
                </span>
              )}
            </Link>

            <button
              type="button"
              onClick={() => setShowSettlementsModal(true)}
              className={`${navItemClass('settlements')} w-full text-left`}
            >
              <div className="flex items-center space-x-2.5">
                <Wallet className={iconClass('settlements')} />
                <span>Settlements</span>
              </div>
              <span className="px-1.5 py-0.5 text-[9px] font-semibold rounded bg-amber-50 text-amber-700 border border-amber-200/60 uppercase">
                Soon
              </span>
            </button>
          </div>

          {/* Section 4: Rules & Growth */}
          <div className="space-y-1">
            <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-3 pb-1">
              Marketing & Rules
            </p>

            <Link href="/pricing" className={navItemClass('pricing')}>
              <div className="flex items-center space-x-2.5">
                <Tag className={iconClass('pricing')} />
                <span>Pricing Rules</span>
              </div>
            </Link>

            <Link href="/promotions" className={navItemClass('promotions')}>
              <div className="flex items-center space-x-2.5">
                <Percent className={iconClass('promotions')} />
                <span>Promotions</span>
              </div>
            </Link>

            <Link href="/notifications" className={navItemClass('notifications')}>
              <div className="flex items-center space-x-2.5">
                <Bell className={iconClass('notifications')} />
                <span>Push Alerts</span>
              </div>
            </Link>
          </div>

          {/* Section 5: Administration & System */}
          <div className="space-y-1">
            <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-3 pb-1">
              System & Logs
            </p>

            <Link href="/users" className={navItemClass('users')}>
              <div className="flex items-center space-x-2.5">
                <Users className={iconClass('users')} />
                <span>Registered Users</span>
              </div>
            </Link>

            <Link href="/cancelled" className={navItemClass('cancelled')}>
              <div className="flex items-center space-x-2.5">
                <XCircle className={iconClass('cancelled')} />
                <span>Cancelled Orders</span>
              </div>
              {cancelledCount > 0 && (
                <span
                  className={`px-2 py-0.5 text-[10px] font-medium rounded-full ${
                    isTabActive('cancelled')
                      ? 'bg-white/20 text-white'
                      : 'bg-rose-50 text-rose-700 border border-rose-200/50'
                  }`}
                >
                  {cancelledCount}
                </span>
              )}
            </Link>

            <Link href="/audit" className={navItemClass('audit')}>
              <div className="flex items-center space-x-2.5">
                <ShieldCheck className={iconClass('audit')} />
                <span>Audit Trail</span>
              </div>
            </Link>

            <Link href="/settings" className={navItemClass('settings')}>
              <div className="flex items-center space-x-2.5">
                <Settings className={iconClass('settings')} />
                <span>Settings</span>
              </div>
            </Link>
          </div>
        </nav>
      </div>

      {/* Merchant Profile Footer */}
      <div className="p-3 border-t border-slate-200/80 bg-slate-50/50 flex items-center justify-between gap-3">
        <div className="flex items-center space-x-2.5 min-w-0">
          <div className="w-8 h-8 rounded-lg bg-slate-900 text-white font-semibold flex items-center justify-center text-xs shrink-0 shadow-xs">
            {session?.merchantName ? session.merchantName.charAt(0).toUpperCase() : 'M'}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold text-slate-900 truncate" suppressHydrationWarning>
              {session?.merchantName || 'Rewari Merchant'}
            </p>
            <p className="text-[11px] text-slate-500 truncate" suppressHydrationWarning>
              Store Administrator
            </p>
          </div>
        </div>

        <button
          onClick={handleLogout}
          title="Sign out of Seller Hub"
          className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors shrink-0"
        >
          <LogOut className="w-4 h-4" />
        </button>
      </div>

      {/* Settlements Under Construction Pop-up Modal */}
      {showSettlementsModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl shadow-xl border border-slate-200/80 max-w-sm w-full p-6 text-center space-y-4 animate-in fade-in zoom-in-95">
            <div className="w-12 h-12 rounded-2xl bg-amber-50 border border-amber-200/60 text-amber-600 flex items-center justify-center mx-auto shadow-xs">
              <Construction className="w-6 h-6 animate-pulse" />
            </div>

            <div className="space-y-1.5">
              <span className="px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-amber-100 text-amber-800 rounded-full">
                Under Construction
              </span>
              <h3 className="text-base font-bold text-slate-900 pt-1">
                Settlements Module Coming Soon
              </h3>
              <p className="text-xs text-slate-500 leading-relaxed">
                Direct automated bank account payouts and settlement cycles are currently in active development.
              </p>
              <p className="text-[11px] text-slate-400 leading-relaxed">
                All daily revenue and doorstep cash collections are tracked in real-time through your Cash on Delivery Ledger.
              </p>
            </div>

            <div className="pt-2 flex flex-col sm:flex-row items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  setShowSettlementsModal(false);
                  router.push('/cod');
                }}
                className="w-full px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-semibold shadow-xs transition-all flex items-center justify-center space-x-1.5 active:scale-98"
              >
                <span>Open COD Ledger</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => setShowSettlementsModal(false)}
                className="w-full px-4 py-2.5 border border-slate-200 hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-medium transition-all"
              >
                Dismiss
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
}
