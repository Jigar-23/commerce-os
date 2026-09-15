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
  XCircle,
  FileText,
  CheckCircle2,
  RefreshCw,
  ArrowRight,
  ShieldCheck,
  Zap,
  Activity,
  Store,
  Plus,
  Clock,
  Server,
  TrendingUp,
  AlertTriangle,
} from 'lucide-react';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import SellerAuthGuard from '../components/SellerAuthGuard';

export default function MerchantOperationsPage() {
  const router = useRouter();
  const { session, storeName, isMounted, isAuthenticated } = useSellerSession();
  const [orders, setOrders] = useState<any[]>([]);
  const [inventory, setInventory] = useState<any[]>([]);
  const [codLedger, setCodLedger] = useState<any[]>([]);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchOverviewData = async (showSpinner = true) => {
    if (!session?.token) return;
    if (showSpinner) setIsLoading(true);
    try {
      const [ordRes, catRes, codRes, auditRes] = await Promise.all([
        sellerApi.get('/api/v1/orders/seller'),
        sellerApi.get('/api/v1/catalog/seller/inventory'),
        sellerApi.get('/api/v1/orders/cod-ledger'),
        sellerApi.get('/api/v1/orders/audit'),
      ]);

      if (ordRes.ok && ordRes.data) {
        const rawList = Array.isArray(ordRes.data) ? ordRes.data : (ordRes.data?.orders || []);
        const normalized = rawList.map((o: any) => ({
          ...o,
          id: o.id || o.order_id,
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
        const items = Array.isArray(catRes.data?.content) ? catRes.data.content : (Array.isArray(catRes.data) ? catRes.data : []);
        setInventory(items);
      }
      if (codRes.ok && codRes.data) {
        const records = Array.isArray(codRes.data?.records) ? codRes.data.records : (Array.isArray(codRes.data) ? codRes.data : []);
        setCodLedger(records);
      }
      if (auditRes.ok && auditRes.data) {
        const logs = Array.isArray(auditRes.data?.logs) ? auditRes.data.logs : (Array.isArray(auditRes.data) ? auditRes.data : []);
        setAuditLogs(logs);
      }
    } catch (e) {
      console.warn('Failed to load merchant overview data:', e);
    } finally {
      if (showSpinner) setIsLoading(false);
    }
  };

  useEffect(() => {
    if (!session?.token) return;
    const playOrderChime = () => {
      try {
        const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
        if (!AudioCtx) return;
        const ctx = new AudioCtx();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(587.33, ctx.currentTime);
        osc.frequency.setValueAtTime(880, ctx.currentTime + 0.15);
        gain.gain.setValueAtTime(0.3, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.6);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start();
        osc.stop(ctx.currentTime + 0.6);
      } catch (_) {}
    };

    fetchOverviewData(true);

    // 1. Realtime SSE Stream Subscription with Scoped Ticket
    let eventSource: EventSource | null = null;
    let isSubscribed = true;

    async function initRealtimeStream() {
      try {
        const token = session?.token;
        if (!token || !isSubscribed) return;
        const gatewayUrl = sellerApi.getBaseUrl();
        
        let streamUrl = `${gatewayUrl}/api/v1/realtime/stream?token=${encodeURIComponent(token)}`;
        try {
          const res = await fetch(`${gatewayUrl}/api/v1/realtime/ticket`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
          });
          if (res.ok) {
            const data = await res.json();
            if (data?.ticket) {
              streamUrl = `${gatewayUrl}/api/v1/realtime/stream?ticket=${encodeURIComponent(data.ticket)}`;
            }
          }
        } catch (_) {}

        if (!isSubscribed) return;
        eventSource = new EventSource(streamUrl);
        eventSource.onmessage = () => fetchOverviewData(false);
        eventSource.addEventListener('ORDER_PLACED', () => {
          playOrderChime();
          fetchOverviewData(false);
        });
        eventSource.addEventListener('ORDER_STATUS_CHANGED', () => fetchOverviewData(false));
        eventSource.addEventListener('DISPATCH_REQUESTED', () => fetchOverviewData(false));
        eventSource.addEventListener('SELLER_ORDER_ACCEPTED', () => fetchOverviewData(false));
        eventSource.onerror = () => {};
      } catch (_) {}
    }

    initRealtimeStream();

    // 2. Heartbeat Fast Polling Reconciliation Fallback (5 seconds)
    const timer = setInterval(() => {
      fetchOverviewData(false);
    }, 5000);

    return () => {
      isSubscribed = false;
      clearInterval(timer);
      if (eventSource) eventSource.close();
    };
  }, [session?.token]);

  const activeOrders = orders.filter((o) => !['DELIVERED', 'CANCELLED', 'FAILED'].includes(o.orderStatus || o.status));
  const totalStockCount = inventory.reduce((acc, item) => acc + (Number(item.stockCount) || 0), 0);
  const totalCodPending = codLedger
    .filter((l) => l.status === 'PENDING' || l.status === 'COLLECTED_BY_RIDER')
    .reduce((acc, l) => acc + (Number(l.amount) || 0), 0);
  const cancelledOrders = orders.filter((o) => (o.orderStatus || o.status) === 'CANCELLED');

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-canvas text-content-primary font-sans antialiased overflow-hidden">
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

      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* HEADER BAR */}
        <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0 gap-4">
          <div className="flex items-center space-x-3">
            <span className="px-3 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-black border border-border-brandSubtle flex items-center space-x-1.5">
              <Activity className="w-3.5 h-3.5" />
              <span>Store Operations & Overview</span>
            </span>
            <span className="text-content-muted">/</span>
            <span className="text-xs font-bold text-content-primary" suppressHydrationWarning>{storeName}</span>
          </div>

          <div className="flex items-center space-x-4">
            <HeaderQuickSearch />
            <button
              onClick={() => fetchOverviewData()}
              className="p-2 text-content-secondary hover:text-content-brand hover:bg-surface-subtle rounded-xl transition-all"
              title="Refresh Dashboard"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-content-brand' : ''}`} />
            </button>
          </div>
        </header>

        <main className="p-8 space-y-8 max-w-7xl">
          {/* TOP OPERATIONS BANNER */}
          <div className="bg-gradient-to-r from-navy-950 via-surface-inverse to-navy-950 text-white rounded-3xl p-8 shadow-xl relative overflow-hidden flex flex-col md:flex-row md:items-center justify-between gap-6 border border-border-strong">
            <div className="space-y-2 relative z-10">
              <div className="flex items-center space-x-2">
                <span className="px-2.5 py-0.5 rounded-full bg-surface-brandSubtle text-content-brand border border-border-brand text-2xs font-black uppercase tracking-wider">
                  Store Status: Online
                </span>
                <span className="text-content-muted text-xs">• 10-Minute Instant Fulfilment Engine Ready</span>
              </div>
              <h1 className="text-2xl font-black tracking-tight text-white">Merchant Operational Overview</h1>
              <p className="text-xs text-content-muted max-w-xl">
                Real-time operational metrics for order fulfillment, cold-chain verification, inventory ledger, and cash reconciliation.
              </p>
            </div>

            <div className="flex items-center space-x-3 relative z-10 shrink-0">
              <Link
                href="/inventory"
                className="px-5 py-3 bg-action-primaryBg hover:bg-action-primaryHover text-white font-extrabold text-xs rounded-2xl shadow-md transition-all flex items-center space-x-2 active:scale-95"
              >
                <Plus className="w-4 h-4" />
                <span>Manage Inventory</span>
              </Link>
            </div>
          </div>

          {/* EXECUTIVE KPI PODS */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {/* Pod 1: Orders */}
            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-card space-y-3 hover:border-border-brandSubtle transition-all">
              <div className="flex items-center justify-between">
                <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Live Active Orders</span>
                <div className="p-2 bg-surface-accentSubtle text-content-accent rounded-xl">
                  <Package className="w-4 h-4" />
                </div>
              </div>
              <div className="flex items-baseline justify-between">
                <span className="text-3xl font-black text-content-primary">{activeOrders.length}</span>
                <span className="text-xs font-bold text-content-secondary">Total: {orders.length}</span>
              </div>
              <Link href="/orders" className="text-xs font-bold text-content-brand hover:underline flex items-center justify-between pt-2 border-t border-border-subtle">
                <span>View Order Pipeline</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>

            {/* Pod 2: Inventory */}
            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-card space-y-3 hover:border-border-brandSubtle transition-all">
              <div className="flex items-center justify-between">
                <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Catalog Inventory</span>
                <div className="p-2 bg-surface-brandSubtle text-content-brand rounded-xl">
                  <Layers className="w-4 h-4" />
                </div>
              </div>
              <div className="flex items-baseline justify-between">
                <span className="text-3xl font-black text-content-primary">{inventory.length} SKU</span>
                <span className="text-xs font-bold text-content-brand">Stock Qty: {totalStockCount}</span>
              </div>
              <Link href="/inventory" className="text-xs font-bold text-content-brand hover:underline flex items-center justify-between pt-2 border-t border-border-subtle">
                <span>View Stock Ledger</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>

            {/* Pod 3: COD Ledger */}
            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-card space-y-3 hover:border-border-warning transition-all">
              <div className="flex items-center justify-between">
                <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">COD Cash Pending</span>
                <div className="p-2 bg-surface-warningSubtle text-content-warning rounded-xl">
                  <IndianRupee className="w-4 h-4" />
                </div>
              </div>
              <div className="flex items-baseline justify-between">
                <span className="text-3xl font-black text-content-primary">₹{totalCodPending}</span>
                <span className="text-xs font-bold text-content-secondary">Ledger Records: {codLedger.length}</span>
              </div>
              <Link href="/cod" className="text-xs font-bold text-content-warning hover:underline flex items-center justify-between pt-2 border-t border-border-subtle">
                <span>Reconcile Cash Ledger</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>

            {/* Pod 4: Cancelled */}
            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-card space-y-3 hover:border-border-danger transition-all">
              <div className="flex items-center justify-between">
                <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Cancelled & Released</span>
                <div className="p-2 bg-surface-dangerSubtle text-content-danger rounded-xl">
                  <XCircle className="w-4 h-4" />
                </div>
              </div>
              <div className="flex items-baseline justify-between">
                <span className="text-3xl font-black text-content-primary">{cancelledOrders.length}</span>
                <span className="text-xs font-bold text-content-danger">Stock Restored</span>
              </div>
              <Link href="/cancelled" className="text-xs font-bold text-content-danger hover:underline flex items-center justify-between pt-2 border-t border-border-subtle">
                <span>View Cancellation Logs</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>

          {/* RECENT LIVE ORDERS PIPELINE */}
          {orders.length > 0 && (
            <div className="bg-white border border-border-default rounded-2xl p-6 shadow-card space-y-4">
              <div className="flex items-center justify-between border-b border-border-default pb-3">
                <div className="flex items-center space-x-2">
                  <Package className="w-5 h-5 text-content-brand" />
                  <h2 className="text-lg font-black text-content-primary">Live Incoming Orders &amp; Dispatch Queue</h2>
                </div>
                <Link href="/orders" className="text-xs font-bold text-content-brand hover:underline flex items-center gap-1">
                  <span>View All Orders ({orders.length})</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </Link>
              </div>

              <div className="divide-y divide-border-subtle">
                {orders.slice(0, 5).map((order) => (
                  <div
                    key={order.id}
                    onClick={() => router.push(`/orders/${order.id}`)}
                    className="py-4 hover:bg-surface-subtle transition-all flex flex-col md:flex-row md:items-center justify-between gap-4 cursor-pointer group px-3 rounded-xl"
                    role="button"
                  >
                    <div className="space-y-1">
                      <div className="flex items-center space-x-3">
                        <span className="font-mono font-black text-sm text-content-brand group-hover:underline">{order.id}</span>
                        <span className={`px-2.5 py-0.5 rounded-full text-2xs font-black ${
                          order.sellerApprovalStatus === 'PENDING'
                            ? 'bg-surface-warningSubtle text-content-warning border border-border-warning animate-pulse'
                            : 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'
                        }`}>
                          {order.sellerApprovalStatus === 'PENDING' ? '⏳ PENDING REVIEW' : order.orderStatus || order.status}
                        </span>
                      </div>
                      <p className="text-xs text-content-secondary">
                        Customer: <strong className="text-content-primary">{order.customerName || order.customerId}</strong> • Total: <strong className="text-content-brand">₹{order.totalAmount}</strong> ({order.paymentMethod})
                      </p>
                    </div>

                    <div className="flex items-center gap-2 text-xs font-bold text-content-brand group-hover:translate-x-1 transition-transform shrink-0">
                      <span>Manage Order Details</span>
                      <ArrowRight className="w-4 h-4" />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* DEDICATED SECTIONS HUB GRID */}
          <div className="space-y-4">
            <h2 className="text-lg font-black text-content-primary">Operational Workflows</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {/* Card 1: Orders */}
              <Link href="/orders" className="bg-white border border-border-default rounded-2xl p-6 shadow-card hover:shadow-card-hover hover:border-border-brand/40 transition-all group space-y-3">
                <div className="p-3 bg-surface-accentSubtle text-content-accent rounded-2xl w-fit">
                  <Package className="w-6 h-6" />
                </div>
                <h3 className="text-base font-extrabold text-content-primary group-hover:text-content-brand transition-colors">Order Fulfilment Pipeline</h3>
                <p className="text-xs text-content-secondary font-medium leading-relaxed">
                  Accept incoming orders, verify cold-chain items, pack bags, and dispatch with real-time OTP tracking.
                </p>
                <div className="text-xs font-bold text-content-brand flex items-center space-x-1 pt-2">
                  <span>Open Orders</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
                </div>
              </Link>

              {/* Card 2: Inventory */}
              <Link href="/inventory" className="bg-white border border-border-default rounded-2xl p-6 shadow-card hover:shadow-card-hover hover:border-border-brand/40 transition-all group space-y-3">
                <div className="p-3 bg-surface-brandSubtle text-content-brand rounded-2xl w-fit">
                  <Layers className="w-6 h-6" />
                </div>
                <h3 className="text-base font-extrabold text-content-primary group-hover:text-content-brand transition-colors">Stock Inventory & Sync</h3>
                <p className="text-xs text-content-secondary font-medium leading-relaxed">
                  Adjust stock levels, audit low-stock thresholds, register new SKUs, and manage real-time inventory reservations.
                </p>
                <div className="text-xs font-bold text-content-brand flex items-center space-x-1 pt-2">
                  <span>Open Inventory</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
                </div>
              </Link>

              {/* Card 3: COD Ledger */}
              <Link href="/cod" className="bg-white border border-border-default rounded-2xl p-6 shadow-card hover:shadow-card-hover hover:border-border-brand/40 transition-all group space-y-3">
                <div className="p-3 bg-surface-warningSubtle text-content-warning rounded-2xl w-fit">
                  <IndianRupee className="w-6 h-6" />
                </div>
                <h3 className="text-base font-extrabold text-content-primary group-hover:text-content-brand transition-colors">Cash on Delivery Reconciliation</h3>
                <p className="text-xs text-content-secondary font-medium leading-relaxed">
                  Reconcile cash collected by delivery partners at customer doorsteps and track bank settlement status.
                </p>
                <div className="text-xs font-bold text-content-warning flex items-center space-x-1 pt-2">
                  <span>Open Cash Ledger</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
                </div>
              </Link>

              {/* Card 4: Cancelled */}
              <Link href="/cancelled" className="bg-white border border-border-default rounded-2xl p-6 shadow-card hover:shadow-card-hover hover:border-border-brand/40 transition-all group space-y-3">
                <div className="p-3 bg-surface-dangerSubtle text-content-danger rounded-2xl w-fit">
                  <XCircle className="w-6 h-6" />
                </div>
                <h3 className="text-base font-extrabold text-content-primary group-hover:text-content-brand transition-colors">Cancellation & Stock Release Logs</h3>
                <p className="text-xs text-content-secondary font-medium leading-relaxed">
                  Review cancelled orders, verify reason codes, and confirm immediate DB inventory restoration.
                </p>
                <div className="text-xs font-bold text-content-danger flex items-center space-x-1 pt-2">
                  <span>Open Cancellation Logs</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
                </div>
              </Link>

              {/* Card 5: Audit Trail */}
              <Link href="/audit" className="bg-white border border-border-default rounded-2xl p-6 shadow-card hover:shadow-card-hover hover:border-border-brand/40 transition-all group space-y-3">
                <div className="p-3 bg-surface-editorialSubtle text-content-editorial rounded-2xl w-fit">
                  <ShieldCheck className="w-6 h-6" />
                </div>
                <h3 className="text-base font-extrabold text-content-primary group-hover:text-content-brand transition-colors">Security & System Audit Trail</h3>
                <p className="text-xs text-content-secondary font-medium leading-relaxed">
                  Trace all atomic inventory movements, price overrides, stock reservations, and pharmacist approvals.
                </p>
                <div className="text-xs font-bold text-content-editorial flex items-center space-x-1 pt-2">
                  <span>Open Audit Trail</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
                </div>
              </Link>

              {/* Card 6: Products & SKUs */}
              <Link href="/products" className="bg-white border border-border-default rounded-2xl p-6 shadow-card hover:shadow-card-hover hover:border-border-brand/40 transition-all group space-y-3">
                <div className="p-3 bg-surface-accentSubtle text-content-accent rounded-2xl w-fit">
                  <Store className="w-6 h-6" />
                </div>
                <h3 className="text-base font-extrabold text-content-primary group-hover:text-content-brand transition-colors">Catalog & Master SKUs</h3>
                <p className="text-xs text-content-secondary font-medium leading-relaxed">
                  Manage active listings, Schedule H clinical requirements, cold-chain temperature flags, and MRPs.
                </p>
                <div className="text-xs font-bold text-content-accent flex items-center space-x-1 pt-2">
                  <span>Open Product Catalog</span>
                  <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
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
