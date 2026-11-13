'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import SellerSidebar from '../components/SellerSidebar';
import HeaderQuickSearch from '../components/HeaderQuickSearch';
import {
  Package,
  Layers,
  IndianRupee,
  RefreshCw,
  ArrowRight,
  ShieldCheck,
  Store,
  Plus,
  CheckCircle2,
  Bike,
  Sparkles,
  Tag,
  Clock,
  Check,
  MapPin,
} from 'lucide-react';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import SellerAuthGuard from '../components/SellerAuthGuard';

export default function MerchantOperationsPage() {
  const router = useRouter();
  const { session, storeName } = useSellerSession();
  const [orders, setOrders] = useState<any[]>([]);
  const [inventory, setInventory] = useState<any[]>([]);
  const [codLedger, setCodLedger] = useState<any[]>([]);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const handleAcceptOrder = async (e: React.MouseEvent, orderId: string) => {
    e.stopPropagation();
    setActionLoadingId(orderId);
    try {
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/accept-by-seller`);
      if (res.ok) {
        setToastMessage(`Order #${orderId} accepted! Rider broadcast dispatched.`);
        setTimeout(() => setToastMessage(null), 4000);
        await fetchOverviewData(false);
      } else {
        alert(res.error || 'Failed to accept order.');
      }
    } catch (err: any) {
      alert(err.message || 'Error accepting order.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const fetchOverviewData = async (showSpinner = true) => {
    let s = session || sellerApi.getSession();
    if (!s?.token) {
      s = await sellerApi.ensureSession();
    }
    if (!s?.token) {
      if (showSpinner) setIsLoading(false);
      return;
    }
    if (showSpinner) setIsLoading(true);
    try {
      const [ordRes, catRes, codRes, auditRes] = await Promise.all([
        sellerApi.get('/api/v1/orders/seller'),
        sellerApi.get('/api/v1/catalog/seller/inventory'),
        sellerApi.get('/api/v1/orders/cod-ledger'),
        sellerApi.get('/api/v1/orders/audit'),
      ]);

      if (ordRes.ok && ordRes.data) {
        const rawList = Array.isArray(ordRes.data) ? ordRes.data : ordRes.data?.orders || [];
        const normalized = rawList.map((o: any) => ({
          ...o,
          id: o.id || o.orderId || o.order_id || '',
          orderId: o.orderId || o.id || o.order_id || '',
          orderStatus: o.orderStatus || o.status,
          status: o.status || o.orderStatus,
          totalAmount: o.totalAmount || o.total_amount || '0.00',
          paymentMethod: o.paymentMethod || o.payment_method || (o.is_cod ? 'COD' : 'ONLINE'),
          paymentStatus: o.paymentStatus || o.payment_status || 'PENDING',
          deliveryAddress: o.deliveryAddress || o.delivery_address,
          customerId: o.customerId || o.customer_id,
          createdAt: o.createdAt || o.created_at,
          sellerApprovalStatus: o.sellerApprovalStatus || o.seller_approval_status,
        }));
        setOrders(normalized);
      }
      if (catRes.ok && catRes.data) {
        const items = Array.isArray(catRes.data?.content)
          ? catRes.data.content
          : Array.isArray(catRes.data)
          ? catRes.data
          : [];
        setInventory(items);
      }
      if (codRes.ok && codRes.data) {
        const records = Array.isArray(codRes.data?.records)
          ? codRes.data.records
          : Array.isArray(codRes.data)
          ? codRes.data
          : [];
        setCodLedger(records);
      }
      if (auditRes.ok && auditRes.data) {
        const logs = Array.isArray(auditRes.data?.logs)
          ? auditRes.data.logs
          : Array.isArray(auditRes.data)
          ? auditRes.data
          : [];
        setAuditLogs(logs);
      }
    } catch (e) {
      console.warn('Failed to load merchant overview data:', e);
    } finally {
      if (showSpinner) setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchOverviewData(true);

    // Heartbeat Fast Polling Reconciliation (5 seconds)
    const timer = setInterval(() => {
      fetchOverviewData(false);
    }, 5000);

    return () => {
      clearInterval(timer);
    };
  }, [session?.token]);

  const activeOrders = orders.filter((o) => !['DELIVERED', 'CANCELLED', 'FAILED'].includes((o.orderStatus || o.status || '').toUpperCase()));
  const pendingApprovalOrders = orders.filter(
    (o) =>
      (o.orderStatus || o.status || '').toUpperCase() !== 'CANCELLED' &&
      (o.orderStatus || o.status || '').toUpperCase() !== 'DELIVERED' &&
      (o.sellerApprovalStatus === 'PENDING' ||
        o.orderStatus === 'PLACED' ||
        o.status === 'PLACED' ||
        o.orderStatus === 'PENDING_APPROVAL')
  );
  const totalStockCount = inventory.reduce((acc, item) => acc + (Number(item.stockCount) || 0), 0);
  const totalCodPending = codLedger
    .filter((l) => l.status === 'PENDING' || l.status === 'COLLECTED_BY_RIDER')
    .reduce((acc, l) => acc + (Number(l.amount) || 0), 0);
  const cancelledOrders = orders.filter((o) => (o.orderStatus || o.status || '').toUpperCase() === 'CANCELLED');

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-[#F8FAFC] text-slate-900 font-sans antialiased overflow-hidden">
        {/* 1. SELLER SIDEBAR */}
        <SellerSidebar
          activeTab="dashboard"
          ordersCount={activeOrders.length}
          inventoryCount={inventory.length}
          pendingCodAmount={totalCodPending}
          cancelledCount={cancelledOrders.length}
          onRefresh={fetchOverviewData}
          isLoading={isLoading}
        />

        {/* 2. MAIN CONTENT AREA */}
        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* TOP HEADER BAR */}
          <header className="h-16 border-b border-slate-200/80 bg-white/90 backdrop-blur-md px-8 flex items-center justify-between shrink-0 gap-4 sticky top-0 z-10">
            <div className="flex items-center space-x-3 min-w-0">
              <span className="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-medium border border-emerald-200/60 shrink-0">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                <span>Live Hub</span>
              </span>
              <span className="text-slate-300">/</span>
              <span className="text-xs font-semibold text-slate-800 truncate" suppressHydrationWarning>
                {storeName}
              </span>
            </div>

            <div className="flex items-center space-x-3 shrink-0">
              <HeaderQuickSearch />
              <button
                onClick={() => fetchOverviewData()}
                className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all"
                title="Sync store data"
              >
                <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-slate-900' : ''}`} />
              </button>
            </div>
          </header>

          {/* TOAST NOTIFICATION */}
          {toastMessage && (
            <div className="fixed top-5 right-5 z-50 px-4 py-2.5 rounded-full shadow-lg font-medium text-xs flex items-center space-x-2 bg-slate-900 text-white border border-slate-800 animate-in fade-in slide-in-from-top-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>{toastMessage}</span>
            </div>
          )}

          {/* MAIN DASHBOARD BODY */}
          <main className="p-6 md:p-8 space-y-6 max-w-7xl w-full mx-auto">
            {/* HERO STORE STATUS CARD */}
            <div className="bg-white border border-slate-200/80 rounded-2xl p-6 sm:p-7 shadow-[0_1px_3px_rgba(0,0,0,0.03)] flex flex-col md:flex-row md:items-center justify-between gap-6">
              <div className="space-y-1.5 max-w-2xl">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center space-x-1.5 px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[11px] font-medium border border-emerald-200/60">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                    <span>Store Open &amp; Accepting Orders</span>
                  </span>
                  <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 text-[11px] font-medium border border-slate-200/60">
                    <Sparkles className="w-3 h-3 text-slate-500" />
                    <span>10-Minute Instant Dispatch</span>
                  </span>
                  <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 text-[11px] font-medium border border-slate-200/60">
                    <Check className="w-3 h-3 text-emerald-600" />
                    <span>All 9 Service Nodes Healthy</span>
                  </span>
                </div>

                <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900 pt-1">
                  Welcome to {storeName}
                </h1>
                <p className="text-xs sm:text-sm text-slate-500 leading-relaxed">
                  Real-time operations center for quick-commerce fulfillment, live inventory levels, and doorstep cash reconciliation.
                </p>
              </div>

              <div className="flex items-center space-x-2.5 shrink-0">
                <Link
                  href="/inventory"
                  className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white font-medium text-xs rounded-xl shadow-xs transition-all flex items-center space-x-1.5 active:scale-98"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Manage Stock</span>
                </Link>
                <Link
                  href="/orders"
                  className="px-4 py-2 bg-white hover:bg-slate-50 text-slate-700 font-medium text-xs rounded-xl border border-slate-200 transition-all flex items-center space-x-1.5"
                >
                  <span>All Orders</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </Link>
              </div>
            </div>

            {/* EXECUTIVE KPI SUMMARY CARDS */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {/* Card 1: Orders */}
              <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 transition-all flex flex-col justify-between space-y-3 group">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Active Orders
                  </span>
                  <div className="w-8 h-8 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
                    <Package className="w-4 h-4" />
                  </div>
                </div>
                <div>
                  <div className="flex items-baseline space-x-2">
                    <span className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
                      {activeOrders.length}
                    </span>
                    {pendingApprovalOrders.length > 0 && (
                      <span className="text-xs font-semibold text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200/50">
                        {pendingApprovalOrders.length} pending approval
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-500 mt-1">Total {orders.length} orders recorded today</p>
                </div>
                <Link
                  href="/orders"
                  className="text-xs font-medium text-slate-700 group-hover:text-slate-900 flex items-center justify-between pt-2 border-t border-slate-100 transition-colors"
                >
                  <span>Open Orders Queue</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                </Link>
              </div>

              {/* Card 2: Inventory */}
              <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 transition-all flex flex-col justify-between space-y-3 group">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Catalog SKUs
                  </span>
                  <div className="w-8 h-8 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
                    <Layers className="w-4 h-4" />
                  </div>
                </div>
                <div>
                  <div className="flex items-baseline space-x-2">
                    <span className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
                      {inventory.length}
                    </span>
                    <span className="text-xs font-medium text-slate-500">SKUs listed</span>
                  </div>
                  <p className="text-xs text-slate-500 mt-1">{totalStockCount} total units in store</p>
                </div>
                <Link
                  href="/inventory"
                  className="text-xs font-medium text-slate-700 group-hover:text-slate-900 flex items-center justify-between pt-2 border-t border-slate-100 transition-colors"
                >
                  <span>Manage Shelf Stock</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                </Link>
              </div>

              {/* Card 3: COD Pending */}
              <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 transition-all flex flex-col justify-between space-y-3 group">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Doorstep Cash (COD)
                  </span>
                  <div className="w-8 h-8 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
                    <IndianRupee className="w-4 h-4" />
                  </div>
                </div>
                <div>
                  <div className="flex items-baseline space-x-2">
                    <span className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
                      ₹{Number(totalCodPending || 0).toFixed(0)}
                    </span>
                    <span className="text-xs font-medium text-slate-500">pending</span>
                  </div>
                  <p className="text-xs text-slate-500 mt-1">{codLedger.length} ledger transactions</p>
                </div>
                <Link
                  href="/cod"
                  className="text-xs font-medium text-slate-700 group-hover:text-slate-900 flex items-center justify-between pt-2 border-t border-slate-100 transition-colors"
                >
                  <span>Reconcile Cash Ledger</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                </Link>
              </div>

              {/* Card 4: Cancelled & Restocked */}
              <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 transition-all flex flex-col justify-between space-y-3 group">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Stock Restored
                  </span>
                  <div className="w-8 h-8 rounded-xl bg-slate-100 text-slate-600 flex items-center justify-center">
                    <ShieldCheck className="w-4 h-4" />
                  </div>
                </div>
                <div>
                  <div className="flex items-baseline space-x-2">
                    <span className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
                      {cancelledOrders.length}
                    </span>
                    <span className="text-xs font-medium text-slate-500">cancelled</span>
                  </div>
                  <p className="text-xs text-slate-500 mt-1">100% stock automatically restored</p>
                </div>
                <Link
                  href="/cancelled"
                  className="text-xs font-medium text-slate-700 group-hover:text-slate-900 flex items-center justify-between pt-2 border-t border-slate-100 transition-colors"
                >
                  <span>View Cancellation Logs</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                </Link>
              </div>
            </div>

            {/* LIVE ORDERS DISPATCH QUEUE */}
            <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-4">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <div className="flex items-center space-x-2.5">
                  <div className="w-7 h-7 rounded-lg bg-slate-100 flex items-center justify-center text-slate-700">
                    <Package className="w-4 h-4" />
                  </div>
                  <div>
                    <h2 className="text-base font-semibold text-slate-900">Live Orders &amp; Dispatch Queue</h2>
                    <p className="text-xs text-slate-500">Orders requiring store preparation and rider handover</p>
                  </div>
                </div>
                <Link
                  href="/orders"
                  className="text-xs font-medium text-slate-700 hover:text-slate-900 flex items-center space-x-1"
                >
                  <span>View all orders ({orders.length})</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </Link>
              </div>

              {activeOrders.length === 0 ? (
                <div className="py-12 px-4 text-center space-y-3">
                  <div className="w-12 h-12 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center mx-auto border border-emerald-100">
                    <CheckCircle2 className="w-6 h-6" />
                  </div>
                  <div className="space-y-1">
                    <p className="text-sm font-semibold text-slate-900">All caught up! No active dispatch orders.</p>
                    <p className="text-xs text-slate-500 max-w-sm mx-auto">
                      Incoming orders placed by customers will automatically appear here with real-time updates.
                    </p>
                  </div>
                </div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {activeOrders.slice(0, 5).map((order) => {
                    const status = (order.orderStatus || order.status || '').toUpperCase();
                    const isCancelled = status === 'CANCELLED' || order.sellerApprovalStatus === 'CANCELLED';
                    const isDelivered = status === 'DELIVERED';
                    const isPending =
                      !isCancelled &&
                      !isDelivered &&
                      (order.sellerApprovalStatus === 'PENDING' ||
                        status === 'PLACED' ||
                        status === 'PENDING_APPROVAL');
                    const isTransit =
                      !isCancelled &&
                      !isDelivered &&
                      (status === 'OUT_FOR_DELIVERY' || status === 'PICKED_UP' || status === 'RIDER_ASSIGNED');

                    const badgeStyle = isCancelled
                      ? 'bg-rose-50 text-rose-700 border-rose-200/60'
                      : isDelivered
                      ? 'bg-emerald-50 text-emerald-700 border-emerald-200/60'
                      : isPending
                      ? 'bg-amber-50 text-amber-700 border-amber-200/60'
                      : isTransit
                      ? 'bg-blue-50 text-blue-700 border-blue-200/60'
                      : 'bg-slate-100 text-slate-700 border-slate-200';

                    const badgeText = isCancelled
                      ? '✕ Cancelled'
                      : isPending
                      ? '⏳ Awaiting Store Approval'
                      : isTransit
                      ? '🏍️ Out for Delivery'
                      : isDelivered
                      ? '✓ Delivered'
                      : order.orderStatus || order.status;

                    return (
                      <div
                        key={order.id}
                        onClick={() => router.push(`/orders/${order.id}`)}
                        className="py-3.5 hover:bg-slate-50/80 transition-colors flex flex-col md:flex-row md:items-center justify-between gap-3 cursor-pointer group px-3 rounded-xl"
                        role="button"
                      >
                        <div className="space-y-1">
                          <div className="flex items-center space-x-2.5">
                            <span className="font-mono font-semibold text-xs text-slate-900 group-hover:text-indigo-600 transition-colors">
                              #{order.id}
                            </span>
                            <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-medium border ${badgeStyle}`}>
                              {badgeText}
                            </span>
                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wide border flex items-center space-x-1 ${
                              order.storeName?.includes('Kathua') || order.storeId === 'store_hub_02' || order.storeId === 'STORE_02'
                                ? 'bg-purple-50 text-purple-700 border-purple-200/80 shadow-xs'
                                : 'bg-indigo-50 text-indigo-700 border-indigo-200/80 shadow-xs'
                            }`}>
                              <MapPin className="w-2.5 h-2.5 shrink-0" />
                              <span>{order.storeName?.includes('Kathua') || order.storeId === 'store_hub_02' || order.storeId === 'STORE_02' ? 'Kathua Hub' : 'Rewari Hub'}</span>
                            </span>
                          </div>
                          <p className="text-xs text-slate-500">
                            Customer:{' '}
                            <span className="font-medium text-slate-800">
                              {order.customerName || order.customerId || 'Direct Customer'}
                            </span>{' '}
                            • Total: <span className="font-semibold text-slate-900">₹{order.totalAmount}</span>{' '}
                            <span className="text-[11px] text-slate-400">({order.paymentMethod})</span>
                          </p>
                        </div>

                        <div className="flex items-center space-x-2 shrink-0">
                          {isPending && !isCancelled && (
                            <button
                              type="button"
                              onClick={(e) => handleAcceptOrder(e, order.id)}
                              disabled={actionLoadingId === order.id}
                              className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-medium shadow-xs flex items-center space-x-1.5 transition-all active:scale-95 disabled:opacity-50"
                            >
                              {actionLoadingId === order.id ? (
                                <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                              ) : (
                                <CheckCircle2 className="w-3.5 h-3.5" />
                              )}
                              <span>Accept &amp; Dispatch</span>
                            </button>
                          )}

                          <div className="flex items-center space-x-1 text-xs font-medium text-slate-600 group-hover:text-slate-900 px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200/80 transition-colors">
                            <span>Details</span>
                            <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" />
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* OPERATIONAL WORKFLOWS GRID */}
            <div className="space-y-3">
              <div>
                <h2 className="text-base font-semibold text-slate-900">Operational Shortcuts</h2>
                <p className="text-xs text-slate-500">Direct access to core store management workflows</p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {/* 1. Orders */}
                <Link
                  href="/orders"
                  className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 hover:shadow-sm transition-all group space-y-2.5"
                >
                  <div className="w-9 h-9 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
                    <Package className="w-4 h-4" />
                  </div>
                  <h3 className="text-sm font-semibold text-slate-900 group-hover:text-indigo-600 transition-colors">
                    Order Fulfillment
                  </h3>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    Accept incoming customer orders, pack parcels, and verify rider OTPs for delivery handoff.
                  </p>
                  <div className="text-xs font-medium text-slate-700 group-hover:text-indigo-600 flex items-center space-x-1 pt-1">
                    <span>Open Orders</span>
                    <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                </Link>

                {/* 2. Inventory */}
                <Link
                  href="/inventory"
                  className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 hover:shadow-sm transition-all group space-y-2.5"
                >
                  <div className="w-9 h-9 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
                    <Layers className="w-4 h-4" />
                  </div>
                  <h3 className="text-sm font-semibold text-slate-900 group-hover:text-emerald-600 transition-colors">
                    Stock Inventory
                  </h3>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    Update shelf stock quantities, monitor low-stock warnings, and reserve batch units in real time.
                  </p>
                  <div className="text-xs font-medium text-slate-700 group-hover:text-emerald-600 flex items-center space-x-1 pt-1">
                    <span>Manage Stock</span>
                    <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                </Link>

                {/* 3. COD */}
                <Link
                  href="/cod"
                  className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 hover:shadow-sm transition-all group space-y-2.5"
                >
                  <div className="w-9 h-9 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
                    <IndianRupee className="w-4 h-4" />
                  </div>
                  <h3 className="text-sm font-semibold text-slate-900 group-hover:text-amber-600 transition-colors">
                    Cash on Delivery
                  </h3>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    Reconcile cash collected by delivery riders at customer doors and confirm daily bank settlements.
                  </p>
                  <div className="text-xs font-medium text-slate-700 group-hover:text-amber-600 flex items-center space-x-1 pt-1">
                    <span>Reconcile Cash</span>
                    <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                </Link>

                {/* 4. Riders & Fleet */}
                <Link
                  href="/riders"
                  className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 hover:shadow-sm transition-all group space-y-2.5"
                >
                  <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
                    <Bike className="w-4 h-4" />
                  </div>
                  <h3 className="text-sm font-semibold text-slate-900 group-hover:text-blue-600 transition-colors">
                    Fleet &amp; Riders
                  </h3>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    View active courier locations on the map and monitor instant 10-minute delivery SLAs.
                  </p>
                  <div className="text-xs font-medium text-slate-700 group-hover:text-blue-600 flex items-center space-x-1 pt-1">
                    <span>View Fleet</span>
                    <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                </Link>

                {/* 5. Products */}
                <Link
                  href="/products"
                  className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 hover:shadow-sm transition-all group space-y-2.5"
                >
                  <div className="w-9 h-9 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center">
                    <Store className="w-4 h-4" />
                  </div>
                  <h3 className="text-sm font-semibold text-slate-900 group-hover:text-purple-600 transition-colors">
                    Product Master Catalog
                  </h3>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    Manage active listings, medicine information, temperature storage requirements, and MRPs.
                  </p>
                  <div className="text-xs font-medium text-slate-700 group-hover:text-purple-600 flex items-center space-x-1 pt-1">
                    <span>Browse Products</span>
                    <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                </Link>

                {/* 6. Pricing & Rules */}
                <Link
                  href="/pricing"
                  className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] hover:border-slate-300 hover:shadow-sm transition-all group space-y-2.5"
                >
                  <div className="w-9 h-9 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
                    <Tag className="w-4 h-4" />
                  </div>
                  <h3 className="text-sm font-semibold text-slate-900 group-hover:text-slate-700 transition-colors">
                    Pricing &amp; Discounts
                  </h3>
                  <p className="text-xs text-slate-500 leading-relaxed">
                    Set up store promotional discounts, tiered pricing rules, and special hub rates.
                  </p>
                  <div className="text-xs font-medium text-slate-700 group-hover:text-slate-900 flex items-center space-x-1 pt-1">
                    <span>Manage Pricing</span>
                    <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                </Link>
              </div>
            </div>
          </main>
        </div>
      </div>
    </SellerAuthGuard>
  );
}
