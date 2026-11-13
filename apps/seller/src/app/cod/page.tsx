'use client';

import React, { useEffect, useMemo, useState } from 'react';
import SellerSidebar from '../../components/SellerSidebar';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { formatAddress } from '../../lib/formatAddress';
import {
  IndianRupee,
  RefreshCw,
  CheckCircle2,
  XCircle,
  Clock,
  Phone,
  User,
  MapPin,
  Check,
  AlertCircle,
  Filter,
} from 'lucide-react';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import SellerAuthGuard from '../../components/SellerAuthGuard';

export default function CodPage() {
  const [orders, setOrders] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [codModalOrder, setCodModalOrder] = useState<any | null>(null);
  const [collectedCashInput, setCollectedCashInput] = useState('');
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
  const [activeFilter, setActiveFilter] = useState<'pending' | 'collected' | 'cancelled' | 'all'>('pending');

  const { session, storeName } = useSellerSession();

  const fetchOrders = async () => {
    if (!session?.token) return;
    setIsLoading(true);
    try {
      const res = await sellerApi.get('/api/v1/orders/seller');
      if (res.ok && res.data) {
        const raw = Array.isArray(res.data) ? res.data : res.data?.orders || [];
        const normalized = raw.map((o: any) => ({
          ...o,
          id: o.id || o.orderId || o.order_id || '',
          orderId: o.orderId || o.id || o.order_id || '',
        }));
        setOrders(normalized);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (session?.token) {
      fetchOrders();
    }
  }, [session?.token]);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setStatusMessage({ text, type });
    setTimeout(() => setStatusMessage(null), 4000);
  };

  // Filter only COD orders
  const codOrders = useMemo(() => orders.filter((o) => o.paymentMethod === 'COD' || o.is_cod || o.isCod), [orders]);

  // Active Pending Collection: strictly non-cancelled orders that need cash collection
  const pendingOrders = useMemo(() => {
    return codOrders.filter((o) => {
      const isCancelled = (o.status || o.orderStatus || '').toUpperCase() === 'CANCELLED';
      const isPaid = o.paymentStatus === 'COD_COLLECTED' || o.paymentStatus === 'PAID';
      return !isCancelled && !isPaid;
    });
  }, [codOrders]);

  // Collected & Reconciled Orders
  const collectedOrders = useMemo(() => {
    return codOrders.filter((o) => o.paymentStatus === 'COD_COLLECTED' || o.paymentStatus === 'PAID');
  }, [codOrders]);

  // Cancelled / Void Orders
  const cancelledOrders = useMemo(() => {
    return codOrders.filter((o) => (o.status || o.orderStatus || '').toUpperCase() === 'CANCELLED');
  }, [codOrders]);

  // Amounts
  const pendingCodAmount = useMemo(() => {
    return pendingOrders.reduce((acc, o) => acc + Number(o.totalAmount || 0), 0);
  }, [pendingOrders]);

  const collectedCodAmount = useMemo(() => {
    return collectedOrders.reduce((acc, o) => acc + Number(o.totalAmount || 0), 0);
  }, [collectedOrders]);

  const cancelledCodAmount = useMemo(() => {
    return cancelledOrders.reduce((acc, o) => acc + Number(o.totalAmount || 0), 0);
  }, [cancelledOrders]);

  // Filtered list based on active tab
  const displayedOrders = useMemo(() => {
    if (activeFilter === 'pending') return pendingOrders;
    if (activeFilter === 'collected') return collectedOrders;
    if (activeFilter === 'cancelled') return cancelledOrders;
    return codOrders;
  }, [activeFilter, pendingOrders, collectedOrders, cancelledOrders, codOrders]);

  const handleConfirmCodCollection = async () => {
    if (!codModalOrder) return;
    try {
      const amount = parseFloat(collectedCashInput) || Number(codModalOrder.totalAmount || 0);
      const res = await sellerApi.post(`/api/v1/orders/${codModalOrder.id}/collect-cod`, {
        collectedAmount: amount,
        shortageAmount: 0,
        notes: 'Doorstep cash verified and handed over by delivery rider',
      });

      if (res.ok) {
        showToast(`Cash payment of ₹${amount} confirmed for Order #${codModalOrder.id}!`);
        setCodModalOrder(null);
        await fetchOrders();
      } else {
        showToast(res.error || 'Failed to record cash collection', 'error');
      }
    } catch (e) {
      showToast('Error recording COD collection', 'error');
    }
  };

  // Helper to extract customer phone cleanly
  const resolvePhone = (order: any) => {
    if (order.customerPhone && order.customerPhone !== '—') return order.customerPhone;
    const cid = String(order.customerId || '');
    const match = cid.match(/\d{10}/);
    if (match) return `+91 ${match[0]}`;
    return 'Direct Customer';
  };

  return (
    <SellerAuthGuard>
      <div className="min-h-screen bg-[#F8FAFC] text-slate-900 flex font-sans antialiased overflow-hidden">
        {statusMessage && (
          <div
            className={`fixed top-5 right-5 z-50 px-4 py-2.5 rounded-full shadow-lg font-medium text-xs flex items-center space-x-2 transition-all border ${
              statusMessage.type === 'success'
                ? 'bg-slate-900 text-white border-slate-800'
                : 'bg-rose-900 text-white border-rose-800'
            }`}
          >
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            <span>{statusMessage.text}</span>
          </div>
        )}

        <SellerSidebar
          activeTab="cod"
          pendingCodAmount={pendingCodAmount}
          onRefresh={fetchOrders}
          isLoading={isLoading}
        />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Top Bar */}
          <header className="h-16 border-b border-slate-200/80 bg-white/90 backdrop-blur-md px-8 flex items-center justify-between shrink-0 gap-4 sticky top-0 z-10">
            <div className="flex items-center space-x-3">
              <span className="inline-flex items-center space-x-1 px-2.5 py-1 rounded-full bg-amber-50 text-amber-700 text-xs font-medium border border-amber-200/60">
                <IndianRupee className="w-3.5 h-3.5" />
                <span>Cash on Delivery Ledger</span>
              </span>
              <span className="text-slate-300">/</span>
              <span className="text-xs font-semibold text-slate-800 truncate" suppressHydrationWarning>
                {storeName}
              </span>
            </div>

            <div className="flex items-center space-x-3">
              <HeaderQuickSearch />
              <button
                onClick={fetchOrders}
                disabled={isLoading}
                className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all disabled:opacity-50"
                title="Sync COD ledger"
              >
                <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-slate-900' : ''}`} />
              </button>
            </div>
          </header>

          <main className="p-6 md:p-8 space-y-6 max-w-7xl w-full mx-auto">
            {/* Header Title */}
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-slate-900">Doorstep Cash Reconciliation</h1>
              <p className="text-xs sm:text-sm text-slate-500 mt-1">
                Verify cash collected by riders at customer doorsteps and reconcile daily physical register amounts.
              </p>
            </div>

            {/* 3 Executive Metric Pods */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {/* Card 1: Pending Rider Collection */}
              <div
                onClick={() => setActiveFilter('pending')}
                className={`bg-white border rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] cursor-pointer transition-all ${
                  activeFilter === 'pending'
                    ? 'border-amber-400 ring-2 ring-amber-400/20'
                    : 'border-slate-200/80 hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Awaiting Rider Handover
                  </span>
                  <div className="w-8 h-8 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
                    <Clock className="w-4 h-4" />
                  </div>
                </div>
                <div className="mt-3">
                  <h3 className="text-2xl sm:text-3xl font-bold tracking-tight text-amber-600">
                    ₹{pendingCodAmount.toFixed(2)}
                  </h3>
                  <p className="text-xs text-slate-500 mt-1">
                    {pendingOrders.length} active {pendingOrders.length === 1 ? 'order' : 'orders'} out for delivery
                  </p>
                </div>
              </div>

              {/* Card 2: Reconciled & Settled */}
              <div
                onClick={() => setActiveFilter('collected')}
                className={`bg-white border rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] cursor-pointer transition-all ${
                  activeFilter === 'collected'
                    ? 'border-emerald-400 ring-2 ring-emerald-400/20'
                    : 'border-slate-200/80 hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Collected &amp; Settled
                  </span>
                  <div className="w-8 h-8 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                </div>
                <div className="mt-3">
                  <h3 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">
                    ₹{collectedCodAmount.toFixed(2)}
                  </h3>
                  <p className="text-xs text-slate-500 mt-1">
                    {collectedOrders.length} orders verified &amp; in register
                  </p>
                </div>
              </div>

              {/* Card 3: Cancelled / Void */}
              <div
                onClick={() => setActiveFilter('cancelled')}
                className={`bg-white border rounded-2xl p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] cursor-pointer transition-all ${
                  activeFilter === 'cancelled'
                    ? 'border-slate-400 ring-2 ring-slate-400/20'
                    : 'border-slate-200/80 hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Cancelled Orders (Void)
                  </span>
                  <div className="w-8 h-8 rounded-xl bg-slate-100 text-slate-500 flex items-center justify-center">
                    <XCircle className="w-4 h-4" />
                  </div>
                </div>
                <div className="mt-3">
                  <h3 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-400">
                    ₹{cancelledCodAmount.toFixed(2)}
                  </h3>
                  <p className="text-xs text-slate-500 mt-1">
                    {cancelledOrders.length} orders cancelled • Zero cash owed
                  </p>
                </div>
              </div>
            </div>

            {/* Filter Tabs & Orders Table */}
            <div className="bg-white border border-slate-200/80 rounded-2xl shadow-[0_1px_3px_rgba(0,0,0,0.03)] overflow-hidden">
              {/* Tab Navigation */}
              <div className="px-6 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center space-x-1.5 p-1 bg-slate-100 rounded-xl">
                  <button
                    onClick={() => setActiveFilter('pending')}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                      activeFilter === 'pending'
                        ? 'bg-white text-slate-900 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Awaiting Rider Cash ({pendingOrders.length})
                  </button>
                  <button
                    onClick={() => setActiveFilter('collected')}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                      activeFilter === 'collected'
                        ? 'bg-white text-slate-900 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Reconciled ({collectedOrders.length})
                  </button>
                  <button
                    onClick={() => setActiveFilter('cancelled')}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                      activeFilter === 'cancelled'
                        ? 'bg-white text-slate-900 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Cancelled / Void ({cancelledOrders.length})
                  </button>
                  <button
                    onClick={() => setActiveFilter('all')}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                      activeFilter === 'all'
                        ? 'bg-white text-slate-900 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    All ({codOrders.length})
                  </button>
                </div>

                <span className="text-xs text-slate-400">
                  Showing {displayedOrders.length} of {codOrders.length} records
                </span>
              </div>

              {/* Order Rows */}
              <div className="divide-y divide-slate-100">
                {displayedOrders.length === 0 ? (
                  <div className="p-12 text-center space-y-2">
                    <CheckCircle2 className="w-10 h-10 text-slate-300 mx-auto" />
                    <p className="text-sm font-semibold text-slate-700">No orders in this view</p>
                    <p className="text-xs text-slate-400">
                      {activeFilter === 'pending'
                        ? 'All doorstep cash collections are currently up to date!'
                        : 'No orders match the selected filter.'}
                    </p>
                  </div>
                ) : (
                  displayedOrders.map((order) => {
                    const status = (order.status || order.orderStatus || '').toUpperCase();
                    const isCancelled = status === 'CANCELLED';
                    const isCollected = order.paymentStatus === 'COD_COLLECTED' || order.paymentStatus === 'PAID';
                    const isPending = !isCancelled && !isCollected;

                    return (
                      <div
                        key={order.id}
                        className="p-5 hover:bg-slate-50/70 transition-colors flex flex-col md:flex-row md:items-center justify-between gap-4"
                      >
                        <div className="space-y-1.5 min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-mono font-semibold text-xs text-slate-900">
                              #{order.id}
                            </span>

                            {isCancelled ? (
                              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-medium bg-slate-100 text-slate-500 border border-slate-200">
                                ✕ CANCELLED • NO CASH DUE
                              </span>
                            ) : isCollected ? (
                              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200/60">
                                ✓ RECONCILED (PAID IN CASH)
                              </span>
                            ) : (
                              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-medium bg-amber-50 text-amber-700 border border-amber-200/60">
                                ⏳ AWAITING RIDER HANDOVER
                              </span>
                            )}

                            {order.riderId && (
                              <span className="px-2 py-0.5 rounded text-[10px] bg-slate-100 text-slate-600 font-mono">
                                Rider: {order.riderName || order.riderId}
                              </span>
                            )}
                          </div>

                          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500 pt-0.5">
                            <span className="flex items-center space-x-1">
                              <User className="w-3.5 h-3.5 text-slate-400" />
                              <span className="font-medium text-slate-700">
                                {order.customerName || 'Direct Customer'}
                              </span>
                            </span>

                            <span className="flex items-center space-x-1">
                              <Phone className="w-3.5 h-3.5 text-slate-400" />
                              <span className="font-medium text-slate-700">{resolvePhone(order)}</span>
                            </span>

                            <span className="flex items-center space-x-1 truncate max-w-sm">
                              <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                              <span className="truncate">{formatAddress(order.deliveryAddress)}</span>
                            </span>
                          </div>
                        </div>

                        {/* Amount & Actions */}
                        <div className="flex items-center justify-between md:justify-end space-x-4 shrink-0 pt-2 md:pt-0 border-t md:border-t-0 border-slate-100">
                          <div className="text-left md:text-right">
                            <p className="text-[10px] uppercase font-semibold text-slate-400 tracking-wider">
                              {isCancelled ? 'Order Value (Void)' : 'Cash Amount'}
                            </p>
                            <p
                              className={`text-base font-bold ${
                                isCancelled ? 'text-slate-400 line-through' : 'text-slate-900'
                              }`}
                            >
                              ₹{Number(order.totalAmount || 0).toFixed(2)}
                            </p>
                          </div>

                          {isPending && (
                            <button
                              onClick={() => {
                                setCodModalOrder(order);
                                setCollectedCashInput(String(order.totalAmount));
                              }}
                              className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-xs font-medium rounded-xl shadow-xs transition-all active:scale-95 flex items-center space-x-1.5"
                            >
                              <Check className="w-3.5 h-3.5 text-emerald-400" />
                              <span>Confirm Cash</span>
                            </button>
                          )}

                          {isCollected && (
                            <span className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-xl bg-emerald-50 text-emerald-700 text-xs font-medium border border-emerald-200/50">
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>Settled in Register</span>
                            </span>
                          )}

                          {isCancelled && (
                            <span className="text-xs text-slate-400 italic">No action required</span>
                          )}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </main>
        </div>

        {/* Modal: Confirm Cash Collection */}
        {codModalOrder && (
          <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
            <div className="bg-white border border-slate-200/80 rounded-2xl p-6 sm:p-7 max-w-md w-full shadow-2xl space-y-5 animate-in fade-in zoom-in-95 duration-150">
              <div className="space-y-1">
                <div className="flex items-center space-x-2">
                  <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                    <IndianRupee className="w-4 h-4" />
                  </div>
                  <h3 className="text-base font-bold text-slate-900">Confirm Cash Handover</h3>
                </div>
                <p className="text-xs text-slate-500">
                  Verify cash delivered by the courier for order #{codModalOrder.id}.
                </p>
              </div>

              <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60 text-xs space-y-1">
                <div className="flex justify-between">
                  <span className="text-slate-500">Customer:</span>
                  <span className="font-medium text-slate-900">
                    {codModalOrder.customerName || 'Direct Customer'} ({resolvePhone(codModalOrder)})
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Delivery Address:</span>
                  <span className="font-medium text-slate-900 truncate max-w-[200px]">
                    {formatAddress(codModalOrder.deliveryAddress)}
                  </span>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">Cash Amount Collected (₹)</label>
                <div className="relative flex items-center">
                  <span className="absolute left-3.5 text-slate-400 font-semibold text-sm">₹</span>
                  <input
                    type="number"
                    value={collectedCashInput}
                    onChange={(e) => setCollectedCashInput(e.target.value)}
                    className="w-full pl-8 pr-4 py-2 bg-white border border-slate-300 rounded-xl text-sm font-bold text-slate-900 outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-900 transition"
                  />
                </div>
                <p className="text-[11px] text-slate-400">
                  Default: ₹{codModalOrder.totalAmount} (Exact order total)
                </p>
              </div>

              <div className="flex items-center justify-end space-x-2.5 pt-2">
                <button
                  type="button"
                  onClick={() => setCodModalOrder(null)}
                  className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-medium rounded-xl transition"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleConfirmCodCollection}
                  className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white text-xs font-medium rounded-xl shadow-xs transition flex items-center space-x-1.5"
                >
                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                  <span>Confirm &amp; Reconcile</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
