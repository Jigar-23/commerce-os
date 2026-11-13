'use client';

import React, { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import SellerSidebar from '../../components/SellerSidebar';
import {
  Package,
  Search,
  CheckCircle2,
  RefreshCw,
  Eye,
  ArrowRight,
  ShieldCheck,
  MapPin,
  Bike,
  AlertCircle,
  XCircle,
  Clock,
  Phone,
  Copy,
  Check,
  Filter,
  CheckCheck,
} from 'lucide-react';

import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { formatAddress, formatDateTime } from '../../lib/formatAddress';
import SellerAuthGuard from '../../components/SellerAuthGuard';

export default function OrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [orderQuery, setOrderQuery] = useState('');
  const [activeTab, setActiveTab] = useState<'ALL' | 'PENDING' | 'TRANSIT' | 'DELIVERED' | 'CANCELLED'>('ALL');
  const [selectedHub, setSelectedHub] = useState<'ALL' | 'REWARI' | 'KATHUA'>('ALL');
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
  const [rebroadcastOrderId, setRebroadcastOrderId] = useState<string | null>(null);
  const [cancelModalOrderId, setCancelModalOrderId] = useState<string | null>(null);
  const [cancelReasonInput, setCancelReasonInput] = useState('');
  const [isCancelling, setIsCancelling] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const { session, storeName } = useSellerSession();

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setToastMessage({ text, type });
    setTimeout(() => setToastMessage(null), 4000);
  };

  const copyToClipboard = (text: string, e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard?.writeText(text);
    setCopiedId(text);
    showToast(`Order ID copied: ${text}`);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleExecuteCancel = async () => {
    if (!cancelModalOrderId) return;
    setIsCancelling(true);
    try {
      const res = await sellerApi.post(`/api/v1/orders/${cancelModalOrderId}/cancel`, {
        reason: cancelReasonInput.trim() || 'Cancelled by store operator',
        cancelledBy: 'SELLER',
      });
      if (res.ok) {
        showToast(`Order #${cancelModalOrderId.slice(-8).toUpperCase()} cancelled & inventory restored!`, 'success');
        setCancelModalOrderId(null);
        setCancelReasonInput('');
        await fetchOrders(false);
      } else {
        showToast(res.error || 'Failed to cancel order', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Error cancelling order', 'error');
    } finally {
      setIsCancelling(false);
    }
  };

  const handleAcceptOrder = async (e: React.MouseEvent, orderId: string) => {
    e.stopPropagation();
    setActionLoadingId(orderId);
    try {
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/accept-by-seller`);
      if (res.ok) {
        showToast(`Order accepted! Broadcast sent to delivery fleet.`, 'success');
        playOrderChime();
        await fetchOrders(false);
      } else {
        showToast(res.error || 'Failed to accept order.', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Error accepting order.', 'error');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleReadyForPickup = async (e: React.MouseEvent, orderId: string) => {
    e.stopPropagation();
    setActionLoadingId(orderId);
    try {
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/ready-for-pickup`);
      if (res.ok) {
        showToast(`Order #${orderId.slice(-8).toUpperCase()} marked Ready for Pickup!`, 'success');
        playOrderChime();
        await fetchOrders(false);
      } else {
        showToast(res.error || 'Failed to mark ready for pickup.', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Error updating order.', 'error');
    } finally {
      setActionLoadingId(null);
    }
  };

  const fetchOrders = async (showSpinner = true) => {
    try {
      let s = session || sellerApi.getSession();
      if (!s?.token) {
        s = await sellerApi.ensureSession();
      }
      if (!s?.token) {
        if (showSpinner) setIsLoading(false);
        return;
      }
      if (showSpinner) setIsLoading(true);
      const res = await sellerApi.get('/api/v1/orders/seller');
      if (res.ok && res.data) {
        setFetchError(null);
        const rawList = Array.isArray(res.data) ? res.data : (res.data?.orders || []);
        const normalized = rawList.map((o: any) => {
          const id = o.id || o.orderId || o.order_id || '';
          const phoneMatch = o.customerId && String(o.customerId).match(/\d{10}/);
          const fallbackPhone = phoneMatch ? `+91 ${phoneMatch[0]}` : null;
          const phone = o.customerPhone || o.customer_phone || o.deliveryAddress?.contactPhone || o.deliveryAddress?.phone || fallbackPhone;
          const status = (o.status || o.orderStatus || '').toUpperCase();
          const isCancelled = status === 'CANCELLED' || o.sellerApprovalStatus === 'CANCELLED';

          return {
            ...o,
            id,
            orderId: id,
            orderStatus: isCancelled ? 'CANCELLED' : (o.orderStatus || o.status),
            status: isCancelled ? 'CANCELLED' : (o.status || o.orderStatus),
            totalAmount: o.totalAmount || o.total_amount || '0.00',
            paymentMethod: o.paymentMethod || o.payment_method || (o.is_cod ? 'COD' : 'ONLINE'),
            paymentStatus: isCancelled && o.paymentStatus !== 'PAID' && o.paymentStatus !== 'COD_COLLECTED' ? 'VOID' : (o.paymentStatus || o.payment_status || 'PENDING'),
            deliveryAddress: o.deliveryAddress || o.delivery_address,
            customerId: o.customerId || o.customer_id,
            customerPhone: phone,
            customerName: o.customerName || o.customer_name || (phoneMatch ? `Customer ${phoneMatch[0].slice(-4)}` : 'Direct Customer'),
            createdAt: o.createdAt || o.created_at,
            sellerApprovalStatus: isCancelled ? 'CANCELLED' : (o.sellerApprovalStatus || o.seller_approval_status),
            riderId: o.riderId || o.rider_id,
            riderName: o.riderName || o.rider_name,
            storeId: o.storeId || o.store_id,
            storeName: o.storeName || o.store_name || (o.storeId === 'store_hub_02' || o.store_id === 'store_hub_02' ? 'Kathua Hub' : 'Rewari Hub'),
          };
        });
        setOrders(normalized);
      } else {
        setFetchError(res.error || 'Failed to fetch seller orders.');
      }
    } catch (e: any) {
      setFetchError(e.message || 'Network error fetching orders.');
    } finally {
      if (showSpinner) setIsLoading(false);
    }
  };

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

  useEffect(() => {
    fetchOrders(true);

    const timer = setInterval(() => {
      fetchOrders(false);
    }, 5000);

    return () => {
      clearInterval(timer);
    };
  }, [session?.token]);

  // Counts by bucket
  const pendingOrders = useMemo(() => {
    return orders.filter(o => {
      const st = (o.status || '').toUpperCase();
      return st !== 'CANCELLED' && st !== 'DELIVERED' && (
        o.sellerApprovalStatus === 'PENDING' ||
        st === 'PLACED' ||
        st === 'PENDING_APPROVAL'
      );
    });
  }, [orders]);

  const transitOrders = useMemo(() => {
    return orders.filter(o => {
      const st = (o.status || '').toUpperCase();
      return st !== 'CANCELLED' && (
        st === 'SELLER_ACCEPTED' ||
        st === 'READY_FOR_PICKUP' ||
        st === 'PACKED' ||
        st === 'RIDER_ASSIGNED' ||
        st === 'ARRIVED_STORE' ||
        st === 'OUT_FOR_DELIVERY' ||
        st === 'PICKED_UP'
      );
    });
  }, [orders]);

  const deliveredOrders = useMemo(() => {
    return orders.filter(o => (o.status || '').toUpperCase() === 'DELIVERED');
  }, [orders]);

  const cancelledOrders = useMemo(() => {
    return orders.filter(o => (o.status || '').toUpperCase() === 'CANCELLED');
  }, [orders]);

  const filteredOrders = useMemo(() => {
    if (!Array.isArray(orders)) return [];
    return orders.filter(o => {
      if (!o) return false;
      const q = orderQuery.trim().toLowerCase();
      const matchSearch = !q || (
        (o.id && String(o.id).toLowerCase().includes(q)) ||
        (o.customerPhone && String(o.customerPhone).toLowerCase().includes(q)) ||
        (o.customerName && String(o.customerName).toLowerCase().includes(q)) ||
        (o.customerId && String(o.customerId).toLowerCase().includes(q)) ||
        (o.deliveryAddress && JSON.stringify(o.deliveryAddress).toLowerCase().includes(q))
      );

      const st = String(o.status || '').toUpperCase();
      const isCancelled = st === 'CANCELLED' || o.sellerApprovalStatus === 'CANCELLED';
      const isDelivered = st === 'DELIVERED';
      const isPending = !isCancelled && !isDelivered && (
        o.sellerApprovalStatus === 'PENDING' ||
        st === 'PLACED' ||
        st === 'PENDING_APPROVAL'
      );
      const isTransit = !isCancelled && !isDelivered && !isPending;

      let matchTab = true;
      if (activeTab === 'PENDING') matchTab = isPending;
      else if (activeTab === 'TRANSIT') matchTab = isTransit;
      else if (activeTab === 'DELIVERED') matchTab = isDelivered;
      else if (activeTab === 'CANCELLED') matchTab = isCancelled;

      const isKathua = o.storeName?.includes('Kathua') || o.storeId === 'store_hub_02' || o.storeId === 'STORE_02';
      const matchHub = selectedHub === 'ALL' ? true : selectedHub === 'KATHUA' ? isKathua : !isKathua;

      return matchSearch && matchTab && matchHub;
    });
  }, [orders, orderQuery, activeTab, selectedHub]);

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-[#F8FAFC] text-slate-900 font-sans antialiased overflow-hidden">
        <SellerSidebar
          activeTab="orders"
          ordersCount={pendingOrders.length + transitOrders.length}
          cancelledCount={cancelledOrders.length}
          onRefresh={fetchOrders}
          isLoading={isLoading}
        />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Header Bar */}
          <header className="h-16 border-b border-slate-200/80 bg-white/90 backdrop-blur-md px-8 flex items-center justify-between shrink-0 gap-4 sticky top-0 z-10">
            <div className="flex items-center space-x-3 min-w-0">
              <span className="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full bg-slate-100 text-slate-700 text-xs font-medium border border-slate-200/60 shrink-0">
                <Package className="w-3.5 h-3.5 text-slate-600" />
                <span>Orders</span>
              </span>
              <span className="text-slate-300">/</span>
              <span className="text-xs font-semibold text-slate-800 truncate" suppressHydrationWarning>
                {storeName}
              </span>
            </div>

            <div className="flex items-center space-x-3 shrink-0">
              <HeaderQuickSearch />
              <button
                onClick={() => fetchOrders()}
                className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all"
                title="Sync orders"
              >
                <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-slate-900' : ''}`} />
              </button>
            </div>
          </header>

          {/* Toast Message */}
          {toastMessage && (
            <div className={`fixed top-5 right-5 z-50 px-4 py-2.5 rounded-full shadow-lg font-medium text-xs flex items-center space-x-2 border animate-in fade-in slide-in-from-top-2 ${
              toastMessage.type === 'success'
                ? 'bg-slate-900 text-white border-slate-800'
                : 'bg-rose-600 text-white border-rose-700'
            }`}>
              {toastMessage.type === 'success' ? (
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              ) : (
                <AlertCircle className="w-4 h-4 text-rose-200 shrink-0" />
              )}
              <span>{toastMessage.text}</span>
            </div>
          )}

          <main className="p-6 md:p-8 space-y-6 max-w-7xl w-full mx-auto">
            {/* Top Overview Cards */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              <button
                type="button"
                onClick={() => setActiveTab('ALL')}
                className={`p-4 rounded-2xl border text-left transition-all ${
                  activeTab === 'ALL'
                    ? 'bg-white border-slate-900 shadow-sm ring-1 ring-slate-900/10'
                    : 'bg-white border-slate-200/80 hover:border-slate-300'
                }`}
              >
                <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Total Orders</p>
                <p className="text-2xl font-bold text-slate-900 mt-1">{orders.length}</p>
                <p className="text-xs text-slate-400 mt-0.5">All historic records</p>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab('PENDING')}
                className={`p-4 rounded-2xl border text-left transition-all ${
                  activeTab === 'PENDING'
                    ? 'bg-amber-50/50 border-amber-500 shadow-sm ring-1 ring-amber-500/10'
                    : 'bg-white border-slate-200/80 hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <p className="text-[11px] font-semibold text-amber-700 uppercase tracking-wider">Awaiting Approval</p>
                  {pendingOrders.length > 0 && (
                    <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
                  )}
                </div>
                <p className="text-2xl font-bold text-amber-900 mt-1">{pendingOrders.length}</p>
                <p className="text-xs text-amber-700/80 mt-0.5">Needs store action</p>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab('DELIVERED')}
                className={`p-4 rounded-2xl border text-left transition-all ${
                  activeTab === 'DELIVERED'
                    ? 'bg-emerald-50/50 border-emerald-500 shadow-sm ring-1 ring-emerald-500/10'
                    : 'bg-white border-slate-200/80 hover:border-slate-300'
                }`}
              >
                <p className="text-[11px] font-semibold text-emerald-700 uppercase tracking-wider">Delivered</p>
                <p className="text-2xl font-bold text-emerald-900 mt-1">{deliveredOrders.length}</p>
                <p className="text-xs text-emerald-700/80 mt-0.5">Successfully handed over</p>
              </button>

              <button
                type="button"
                onClick={() => setActiveTab('CANCELLED')}
                className={`p-4 rounded-2xl border text-left transition-all ${
                  activeTab === 'CANCELLED'
                    ? 'bg-rose-50/50 border-rose-500 shadow-sm ring-1 ring-rose-500/10'
                    : 'bg-white border-slate-200/80 hover:border-slate-300'
                }`}
              >
                <p className="text-[11px] font-semibold text-rose-700 uppercase tracking-wider">Cancelled / Void</p>
                <p className="text-2xl font-bold text-rose-900 mt-1">{cancelledOrders.length}</p>
                <p className="text-xs text-rose-700/80 mt-0.5">100% stock restored</p>
              </button>
            </div>

            {/* Filter Tabs & Search Bar */}
            <div className="bg-white border border-slate-200/80 rounded-2xl p-4 shadow-[0_1px_3px_rgba(0,0,0,0.03)] flex flex-col md:flex-row md:items-center justify-between gap-4">
              {/* Segmented Filter Pills */}
              <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 md:pb-0">
                <button
                  type="button"
                  onClick={() => setActiveTab('ALL')}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-medium transition-all shrink-0 ${
                    activeTab === 'ALL'
                      ? 'bg-slate-900 text-white shadow-xs'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  All Orders ({orders.length})
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab('PENDING')}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-medium transition-all shrink-0 flex items-center space-x-1.5 ${
                    activeTab === 'PENDING'
                      ? 'bg-amber-600 text-white shadow-xs'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  <span>Awaiting Action ({pendingOrders.length})</span>
                  {pendingOrders.length > 0 && (
                    <span className={`w-1.5 h-1.5 rounded-full ${activeTab === 'PENDING' ? 'bg-white' : 'bg-amber-500'}`} />
                  )}
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab('TRANSIT')}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-medium transition-all shrink-0 ${
                    activeTab === 'TRANSIT'
                      ? 'bg-slate-900 text-white shadow-xs'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  In Transit ({transitOrders.length})
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab('DELIVERED')}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-medium transition-all shrink-0 ${
                    activeTab === 'DELIVERED'
                      ? 'bg-slate-900 text-white shadow-xs'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Delivered ({deliveredOrders.length})
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab('CANCELLED')}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-medium transition-all shrink-0 ${
                    activeTab === 'CANCELLED'
                      ? 'bg-rose-600 text-white shadow-xs'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  Cancelled ({cancelledOrders.length})
                </button>
              </div>

              {/* Controls: Hub Filter & Search */}
              <div className="flex flex-wrap items-center gap-3 shrink-0">
                {/* Store Hub Switcher Pills */}
                <div className="flex items-center p-1 bg-slate-100 rounded-xl text-xs border border-slate-200/60">
                  <button
                    type="button"
                    onClick={() => setSelectedHub('ALL')}
                    className={`px-3 py-1 rounded-lg font-medium transition-all ${
                      selectedHub === 'ALL'
                        ? 'bg-white text-slate-900 shadow-xs font-semibold'
                        : 'text-slate-500 hover:text-slate-800'
                    }`}
                  >
                    All Hubs
                  </button>
                  <button
                    type="button"
                    onClick={() => setSelectedHub('REWARI')}
                    className={`px-3 py-1 rounded-lg font-medium transition-all ${
                      selectedHub === 'REWARI'
                        ? 'bg-white text-indigo-700 shadow-xs font-semibold'
                        : 'text-slate-500 hover:text-slate-800'
                    }`}
                  >
                    Rewari Hub
                  </button>
                  <button
                    type="button"
                    onClick={() => setSelectedHub('KATHUA')}
                    className={`px-3 py-1 rounded-lg font-medium transition-all ${
                      selectedHub === 'KATHUA'
                        ? 'bg-white text-purple-700 shadow-xs font-semibold'
                        : 'text-slate-500 hover:text-slate-800'
                    }`}
                  >
                    Kathua Hub
                  </button>
                </div>

                {/* Search Bar */}
                <div className="relative">
                  <Search className="w-3.5 h-3.5 absolute left-3 top-3 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Search order ID, phone, customer…"
                    value={orderQuery}
                    onChange={(e) => setOrderQuery(e.target.value)}
                    className="pl-8 pr-4 py-2 bg-slate-50 border border-slate-200/80 rounded-xl text-xs text-slate-800 placeholder-slate-400 outline-none focus:bg-white focus:border-slate-400 transition-all w-full md:w-56"
                  />
                </div>
              </div>
            </div>

            {/* Error Banner */}
            {fetchError && (
              <div className="p-4 bg-rose-50 border border-rose-200 rounded-2xl text-rose-700 text-xs font-medium flex items-center justify-between">
                <span>⚠️ {fetchError}</span>
                <button
                  onClick={() => fetchOrders(true)}
                  className="px-3 py-1 bg-white border border-rose-200 rounded-lg text-xs font-semibold hover:bg-rose-100 transition-all"
                >
                  Retry
                </button>
              </div>
            )}

            {/* Orders Feed */}
            <div className="space-y-3">
              {filteredOrders.length === 0 ? (
                <div className="bg-white border border-slate-200/80 rounded-2xl p-12 text-center space-y-3">
                  <div className="w-12 h-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto">
                    <Package className="w-6 h-6" />
                  </div>
                  <div className="space-y-1">
                    <p className="text-sm font-semibold text-slate-900">
                      {isLoading ? 'Loading orders from server…' : 'No orders in this view'}
                    </p>
                    <p className="text-xs text-slate-500 max-w-sm mx-auto">
                      {orderQuery
                        ? `No orders matching "${orderQuery}". Try checking the query or clear filters.`
                        : `There are currently no orders under the "${activeTab}" tab.`}
                    </p>
                  </div>
                </div>
              ) : (
                filteredOrders.map((order) => {
                  const status = (order.status || order.orderStatus || '').toUpperCase();
                  const isCancelled = status === 'CANCELLED' || order.sellerApprovalStatus === 'CANCELLED';
                  const isDelivered = status === 'DELIVERED';
                  const isPending = !isCancelled && !isDelivered && (
                    order.sellerApprovalStatus === 'PENDING' ||
                    status === 'PLACED' ||
                    status === 'PENDING_APPROVAL'
                  );
                  const isTransit = !isCancelled && !isDelivered && (
                    status === 'OUT_FOR_DELIVERY' || status === 'PICKED_UP' || status === 'RIDER_ASSIGNED'
                  );
                  const isReady = !isCancelled && !isDelivered && (status === 'READY_FOR_PICKUP' || status === 'SELLER_ACCEPTED' || status === 'PACKED');

                  return (
                    <div
                      key={order.id}
                      onClick={() => router.push(`/orders/${order.id}`)}
                      className="bg-white border border-slate-200/80 hover:border-slate-300 rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.02)] transition-all cursor-pointer group space-y-4"
                      role="button"
                    >
                      {/* Top Row: ID, Badges, Price */}
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
                        <div className="flex flex-wrap items-center gap-2.5">
                          <div className="flex items-center space-x-1 font-mono text-xs font-semibold text-slate-900 group-hover:text-indigo-600 transition-colors">
                            <span>#{order.id}</span>
                            <button
                              type="button"
                              onClick={(e) => copyToClipboard(order.id, e)}
                              className="p-1 text-slate-400 hover:text-slate-700 rounded-md transition-colors"
                              title="Copy Order ID"
                            >
                              {copiedId === order.id ? (
                                <Check className="w-3 h-3 text-emerald-600" />
                              ) : (
                                <Copy className="w-3 h-3" />
                              )}
                            </button>
                          </div>

                          <span className="text-[11px] text-slate-400">
                            {formatDateTime(order.createdAt)}
                          </span>

                          {/* Store Hub Badge */}
                          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-semibold tracking-wide border flex items-center space-x-1 ${
                            order.storeName?.includes('Kathua') || order.storeId === 'store_hub_02' || order.storeId === 'STORE_02'
                              ? 'bg-purple-50 text-purple-700 border-purple-200/80 shadow-xs'
                              : 'bg-indigo-50 text-indigo-700 border-indigo-200/80 shadow-xs'
                          }`}>
                            <MapPin className="w-2.5 h-2.5 shrink-0" />
                            <span>{order.storeName?.includes('Kathua') || order.storeId === 'store_hub_02' || order.storeId === 'STORE_02' ? 'Kathua Hub' : 'Rewari Hub'}</span>
                          </span>

                          {/* Status Pill */}
                          {isCancelled ? (
                            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-rose-50 text-rose-700 border border-rose-200/60 flex items-center space-x-1">
                              <XCircle className="w-3 h-3" />
                              <span>Cancelled</span>
                            </span>
                          ) : isDelivered ? (
                            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200/60 flex items-center space-x-1">
                              <CheckCircle2 className="w-3 h-3" />
                              <span>Delivered</span>
                            </span>
                          ) : isPending ? (
                            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200/60 animate-pulse flex items-center space-x-1">
                              <Clock className="w-3 h-3" />
                              <span>Awaiting Approval</span>
                            </span>
                          ) : isTransit ? (
                            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-blue-50 text-blue-700 border border-blue-200/60 flex items-center space-x-1">
                              <Bike className="w-3 h-3" />
                              <span>Out for Delivery</span>
                            </span>
                          ) : isReady ? (
                            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-indigo-50 text-indigo-700 border border-indigo-200/60 flex items-center space-x-1">
                              <Package className="w-3 h-3" />
                              <span>Store Ready</span>
                            </span>
                          ) : (
                            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-slate-100 text-slate-700 border border-slate-200">
                              {order.status}
                            </span>
                          )}

                          {/* Payment Pill */}
                          <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium border ${
                            isCancelled
                              ? 'bg-slate-50 text-slate-400 border-slate-200'
                              : 'bg-slate-100 text-slate-700 border-slate-200/60'
                          }`}>
                            {order.paymentMethod} • {order.paymentStatus}
                          </span>
                        </div>

                        {/* Amount */}
                        <div className="flex items-baseline space-x-1.5 shrink-0">
                          <span className="text-xs text-slate-400">Total:</span>
                          <span className="text-base font-bold text-slate-900">
                            ₹{Number(order.totalAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                          </span>
                        </div>
                      </div>

                      {/* Middle Row: Customer Info & Address */}
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs text-slate-600">
                        <div className="space-y-1">
                          <div className="flex items-center space-x-2">
                            <span className="font-medium text-slate-900">{order.customerName}</span>
                            {order.customerPhone && (
                              <a
                                href={`tel:${order.customerPhone}`}
                                onClick={(e) => e.stopPropagation()}
                                className="inline-flex items-center space-x-1 text-slate-500 hover:text-slate-900 font-mono text-[11px] px-2 py-0.5 rounded-md bg-slate-100 hover:bg-slate-200 transition-colors"
                              >
                                <Phone className="w-3 h-3 text-slate-400" />
                                <span>{order.customerPhone}</span>
                              </a>
                            )}
                          </div>
                          <p className="text-slate-400 text-[11px]">
                            Items: {order.items?.length || 1} • Store ID: {order.storeId || storeName}
                          </p>
                        </div>

                        <div className="flex items-start space-x-1.5 text-slate-600">
                          <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
                          <span className="line-clamp-2 leading-relaxed text-[11px]">
                            {formatAddress(order.deliveryAddress)}
                          </span>
                        </div>
                      </div>

                      {/* Bottom Row: Actions */}
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2 border-t border-slate-100">
                        <div>
                          {isCancelled ? (
                            <p className="text-[11px] text-rose-600 font-medium flex items-center space-x-1.5">
                              <ShieldCheck className="w-3.5 h-3.5 text-rose-500" />
                              <span>Order is cancelled. Reserved inventory was restocked.</span>
                            </p>
                          ) : isDelivered ? (
                            <p className="text-[11px] text-emerald-600 font-medium flex items-center space-x-1.5">
                              <CheckCheck className="w-3.5 h-3.5 text-emerald-500" />
                              <span>Fulfilled and handed over to customer.</span>
                            </p>
                          ) : (
                            <p className="text-[11px] text-slate-400">
                              Click Details to inspect complete item breakdown &amp; timeline.
                            </p>
                          )}
                        </div>

                        <div className="flex items-center space-x-2 shrink-0">
                          {/* Accept & Broadcast Action */}
                          {isPending && !isCancelled && (
                            <button
                              type="button"
                              onClick={(e) => handleAcceptOrder(e, order.id)}
                              disabled={actionLoadingId === order.id}
                              className="px-3.5 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-medium shadow-xs flex items-center space-x-1.5 transition-all active:scale-95 disabled:opacity-50"
                            >
                              {actionLoadingId === order.id ? (
                                <div className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                              ) : (
                                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                              )}
                              <span>Accept &amp; Broadcast</span>
                            </button>
                          )}

                          {/* Ready for Pickup Action */}
                          {(order.status === 'SELLER_ACCEPTED' || order.status === 'PACKED') && !isCancelled && (
                            <button
                              type="button"
                              onClick={(e) => handleReadyForPickup(e, order.id)}
                              disabled={actionLoadingId === order.id}
                              className="px-3.5 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-medium shadow-xs flex items-center space-x-1.5 transition-all active:scale-95 disabled:opacity-50"
                            >
                              {actionLoadingId === order.id ? (
                                <div className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                              ) : (
                                <Package className="w-3.5 h-3.5" />
                              )}
                              <span>Ready for Pickup</span>
                            </button>
                          )}

                          {/* Re-broadcast Action */}
                          {(order.status === 'READY_FOR_PICKUP' || order.status === 'SELLER_ACCEPTED') && !order.riderId && !isCancelled && (
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setRebroadcastOrderId(order.id);
                              }}
                              disabled={actionLoadingId === order.id}
                              className="px-3 py-1.5 bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200/80 rounded-xl text-xs font-medium flex items-center space-x-1 transition-all"
                              title="Re-broadcast alert to available riders"
                            >
                              <Bike className="w-3.5 h-3.5 text-amber-700" />
                              <span>Re-Broadcast</span>
                            </button>
                          )}

                          {/* Cancel Order Action */}
                          {!isDelivered && !isCancelled && (
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setCancelModalOrderId(order.id);
                                setCancelReasonInput('');
                              }}
                              disabled={actionLoadingId === order.id}
                              className="px-3 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200/60 rounded-xl text-xs font-medium flex items-center space-x-1 transition-all"
                            >
                              <XCircle className="w-3.5 h-3.5 text-rose-600" />
                              <span>Cancel</span>
                            </button>
                          )}

                          {/* Details Button */}
                          <div className="flex items-center space-x-1 text-xs font-medium text-slate-600 group-hover:text-slate-900 px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200/80 transition-colors">
                            <span>Details</span>
                            <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform" />
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </main>
        </div>

        {/* Cancel Confirmation Modal */}
        {cancelModalOrderId && (
          <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-xl border border-slate-200/80 max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-xl bg-rose-50 border border-rose-200/60 flex items-center justify-center text-rose-600 font-bold shrink-0">
                  <XCircle className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Cancel Order #{cancelModalOrderId.slice(-8).toUpperCase()}</h3>
                  <p className="text-xs text-slate-400 font-mono truncate max-w-[260px]">{cancelModalOrderId}</p>
                </div>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Cancelling this order immediately revokes rider broadcast alerts and returns all reserved stock back to active store inventory.
              </p>
              <input
                type="text"
                placeholder="Reason for cancellation (optional)"
                value={cancelReasonInput}
                onChange={(e) => setCancelReasonInput(e.target.value)}
                className="w-full p-3 bg-slate-50 border border-slate-200/80 rounded-xl outline-none text-xs text-slate-800 placeholder-slate-400 focus:bg-white focus:border-rose-400 transition-all"
              />
              <div className="flex justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setCancelModalOrderId(null)}
                  disabled={isCancelling}
                  className="px-4 py-2 border border-slate-200 hover:bg-slate-50 rounded-xl text-xs font-medium text-slate-700 transition-all"
                >
                  Keep Order
                </button>
                <button
                  type="button"
                  onClick={handleExecuteCancel}
                  disabled={isCancelling}
                  className="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-medium text-xs shadow-xs transition-all flex items-center space-x-1.5 disabled:opacity-50"
                >
                  {isCancelling ? (
                    <>
                      <div className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      <span>Cancelling…</span>
                    </>
                  ) : (
                    <>
                      <XCircle className="w-3.5 h-3.5" />
                      <span>Confirm Cancellation</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Re-Broadcast Modal */}
        {rebroadcastOrderId && (
          <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-xl border border-slate-200/80 max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-xl bg-amber-50 border border-amber-200/60 flex items-center justify-center text-amber-600 font-bold shrink-0">
                  <Bike className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Re-Broadcast Delivery Offer</h3>
                  <p className="text-xs text-slate-400 font-mono truncate max-w-[260px]">{rebroadcastOrderId}</p>
                </div>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Dispatch an updated high-priority push notification to all available riders in your fulfillment radius.
              </p>
              <div className="flex justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setRebroadcastOrderId(null)}
                  className="px-4 py-2 border border-slate-200 hover:bg-slate-50 rounded-xl text-xs font-medium text-slate-700 transition-all"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    const targetId = rebroadcastOrderId;
                    setRebroadcastOrderId(null);
                    if (targetId) {
                      const mockEvent = { stopPropagation: () => {} } as any;
                      await handleAcceptOrder(mockEvent, targetId);
                    }
                  }}
                  className="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-xl font-medium text-xs shadow-xs transition-all flex items-center space-x-1.5"
                >
                  <Bike className="w-3.5 h-3.5" />
                  <span>Confirm Dispatch</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
