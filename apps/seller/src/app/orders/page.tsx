'use client';

import React, { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import SellerSidebar from '../../components/SellerSidebar';
import {
  Package, Search, CheckCircle2, RefreshCw, Eye, ArrowRight, ShieldCheck, MapPin, Bike, AlertCircle
} from 'lucide-react';

import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { formatAddress } from '../../lib/formatAddress';
import SellerAuthGuard from '../../components/SellerAuthGuard';

export default function OrdersPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [orderQuery, setOrderQuery] = useState('');
  const [orderFilter, setOrderFilter] = useState('ALL');
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
  const { session, storeName } = useSellerSession();

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setToastMessage({ text, type });
    setTimeout(() => setToastMessage(null), 4000);
  };

  const handleAcceptOrder = async (e: React.MouseEvent, orderId: string) => {
    e.stopPropagation();
    setActionLoadingId(orderId);
    try {
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/accept-by-seller`);
      if (res.ok) {
        showToast(`Order #${orderId.slice(-8)} Accepted! Rider broadcast dispatched to fleet.`, 'success');
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
        showToast(`Order #${orderId.slice(-8)} Marked Ready for Pickup!`, 'success');
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
    const s = session || sellerApi.getSession();
    if (!s?.token) return;
    if (showSpinner) setIsLoading(true);
    try {
      const res = await sellerApi.get('/api/v1/orders/seller');
      if (res.ok && res.data) {
        setFetchError(null);
        const rawList = Array.isArray(res.data) ? res.data : (res.data?.orders || []);
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
          customerPhone: o.customerPhone || o.customer_phone || o.delivery_address?.contactPhone || o.delivery_address?.phone,
          customerName: o.customerName || o.customer_name,
          createdAt: o.createdAt || o.created_at,
          sellerApprovalStatus: o.sellerApprovalStatus || o.seller_approval_status,
        }));
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

    // Heartbeat Fast Polling Reconciliation (5 seconds)
    const timer = setInterval(() => {
      fetchOrders(false);
    }, 5000);

    return () => {
      clearInterval(timer);
    };
  }, [session?.token]);

  const filteredOrders = useMemo(() => {
    if (!Array.isArray(orders)) return [];
    return orders.filter(o => {
      if (!o) return false;
      const q = orderQuery.toLowerCase();
      const matchSearch =
        (o.id && String(o.id).toLowerCase().includes(q)) ||
        (o.customerPhone && String(o.customerPhone).toLowerCase().includes(q)) ||
        (o.customerId && String(o.customerId).toLowerCase().includes(q)) ||
        (o.deliveryAddress && (typeof o.deliveryAddress === 'string' ? o.deliveryAddress : JSON.stringify(o.deliveryAddress)).toLowerCase().includes(q)) ||
        (o.consignmentNumber && String(o.consignmentNumber).toLowerCase().includes(q)) ||
        (Array.isArray(o.items) && o.items.some((i: any) => i?.name && String(i.name).toLowerCase().includes(q)));

      const st = String(o.orderStatus || o.status || '').toUpperCase();
      let matchFilter = true;
      if (orderFilter === 'ALL') {
        matchFilter = true;
      } else if (orderFilter === 'READY_FOR_PICKUP') {
        matchFilter = st === 'READY_FOR_PICKUP';
      } else if (orderFilter === 'PLACED') {
        matchFilter = st === 'PLACED' || st === 'PENDING_APPROVAL';
      } else if (orderFilter === 'SELLER_ACCEPTED') {
        matchFilter = st === 'SELLER_ACCEPTED' || st === 'ACCEPTED';
      } else if (orderFilter === 'PACKED') {
        matchFilter = st === 'PACKED';
      } else if (orderFilter === 'OUT_FOR_DELIVERY') {
        matchFilter = st === 'OUT_FOR_DELIVERY' || st === 'RIDER_ASSIGNED' || st === 'DISPATCH_REQUESTED' || st === 'SHIPPED';
      } else if (orderFilter === 'DELIVERED') {
        matchFilter = st === 'DELIVERED';
      } else if (orderFilter === 'CANCELLED') {
        matchFilter = st === 'CANCELLED';
      } else if (orderFilter === 'COD_PENDING') {
        matchFilter = (o.paymentMethod === 'COD' || o.is_cod) && o.paymentStatus !== 'PAID';
      } else {
        matchFilter = st === orderFilter;
      }

      return matchSearch && matchFilter;
    });
  }, [orders, orderQuery, orderFilter]);

  return (
    <SellerAuthGuard>
      <div className="min-h-screen bg-surface-canvas text-content-primary flex font-sans antialiased">
        <SellerSidebar activeTab="orders" ordersCount={orders.length} onRefresh={fetchOrders} isLoading={isLoading} />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0 gap-4">
            <div className="flex items-center space-x-3">
              <span className="px-2.5 py-1 rounded-full bg-surface-accentSubtle text-content-accent text-xs font-bold border border-border-accent">
                Orders
              </span>
              <span className="text-content-muted">/</span>
              <span className="text-xs font-bold text-content-primary" suppressHydrationWarning>{storeName}</span>
            </div>

          <div className="flex items-center space-x-4">
            <HeaderQuickSearch />
            <button onClick={() => fetchOrders()} className="p-2 text-content-secondary hover:text-content-accent hover:bg-surface-subtle rounded-lg transition-all" aria-label="Refresh orders">
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </header>

        {toastMessage && (
          <div className={`fixed top-4 right-4 z-50 px-5 py-3 rounded-2xl shadow-2xl font-bold text-xs flex items-center space-x-2 transition-all border ${toastMessage.type === 'success' ? 'bg-action-speedBg text-white border-border-brand' : 'bg-action-dangerBg text-white border-border-danger'}`}>
            <CheckCircle2 className="w-4 h-4" />
            <span>{toastMessage.text}</span>
          </div>
        )}

        <main className="p-8 space-y-6">
          <div className="bg-white border border-border-default rounded-2xl shadow-sm overflow-hidden">
            <div className="p-6 border-b border-border-subtle flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <div className="flex items-center space-x-2">
                  <Package className="w-5 h-5 text-content-accent" />
                  <h2 className="text-lg font-black text-content-primary">Orders Management</h2>
                </div>
                <p className="text-xs text-content-muted mt-0.5">Manage live store orders, fulfillment status, and delivery handoffs.</p>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <div className="relative">
                  <Search className="w-4 h-4 absolute left-3 top-2.5 text-content-muted" />
                  <input
                    type="text"
                    placeholder="Search Order ID, Phone..."
                    value={orderQuery}
                    onChange={e => setOrderQuery(e.target.value)}
                    className="pl-9 pr-4 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs outline-none focus:border-border-accent w-60"
                  />
                </div>

                <select
                  value={orderFilter}
                  onChange={e => setOrderFilter(e.target.value)}
                  className="px-3 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs font-bold text-content-secondary outline-none"
                >
                  <option value="ALL">ALL STATUSES</option>
                  <option value="READY_FOR_PICKUP">READY FOR PICKUP</option>
                  <option value="PLACED">PLACED</option>
                  <option value="SELLER_ACCEPTED">SELLER ACCEPTED</option>
                  <option value="PACKED">PACKED</option>
                  <option value="SHIPPED">SHIPPED</option>
                  <option value="OUT_FOR_DELIVERY">OUT FOR DELIVERY</option>
                  <option value="DELIVERED">DELIVERED</option>
                  <option value="COD_PENDING">COD PENDING CASH</option>
                  <option value="CANCELLED">CANCELLED</option>
                </select>
              </div>
            </div>

            {fetchError && (
              <div className="p-4 bg-surface-dangerSubtle border-b border-border-danger text-content-danger text-xs font-bold flex items-center justify-between">
                <span>⚠️ {fetchError}</span>
                <button onClick={() => fetchOrders(true)} className="px-3 py-1 bg-white rounded-lg text-xs font-black shadow-sm">Retry</button>
              </div>
            )}

            <div className="divide-y divide-border-subtle">
              {filteredOrders.length === 0 ? (
                <div className="p-12 text-center text-content-muted text-xs">
                  {isLoading ? 'Loading orders from server...' : (fetchError ? 'Unable to load orders.' : 'No orders found matching criteria.')}
                </div>
              ) : (
                filteredOrders.map(order => (
                  <div
                    key={order.id}
                    onClick={() => router.push(`/orders/${order.id}`)}
                    className="p-6 hover:bg-surface-subtle transition-all flex flex-col md:flex-row md:items-center justify-between gap-6 cursor-pointer group"
                    role="button"
                    tabIndex={0}
                  >
                    <div className="space-y-2 flex-1">
                      <div className="flex flex-wrap items-center gap-3">
                        <span className="text-base font-black text-content-accent group-hover:text-content-brand font-mono underline flex items-center space-x-1">
                          <span>{order.id}</span>
                        </span>

                        <span className={`px-3 py-1 rounded-full text-xs font-black ${
                          order.orderStatus === 'DELIVERED'
                            ? 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'
                            : order.orderStatus === 'CANCELLED'
                            ? 'bg-surface-dangerSubtle text-content-danger border border-border-danger'
                            : order.sellerApprovalStatus === 'PENDING'
                            ? 'bg-surface-warningSubtle text-content-warning border border-border-warning animate-pulse'
                            : 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'
                        }`}>
                          {order.sellerApprovalStatus === 'PENDING' ? '⏳ PENDING SELLER ACCEPTANCE' : `Status: ${order.orderStatus || order.status}`}
                        </span>

                        <span className="px-2.5 py-0.5 rounded-full text-2xs font-bold bg-surface-subtle text-content-secondary border border-border-default">
                          {order.paymentMethod}: {order.paymentStatus}
                        </span>
                      </div>

                      <p className="text-xs text-content-secondary">
                        Customer: <strong className="text-content-primary">{order.customerName || order.customerId || 'Customer'}</strong> • Phone: <strong className="text-content-primary">{order.customerPhone || '—'}</strong>
                      </p>
                      <p className="text-xs font-semibold text-content-secondary flex items-center gap-1">
                        <MapPin className="w-3.5 h-3.5 text-content-danger shrink-0" />
                        <span>{formatAddress(order.deliveryAddress)}</span>
                      </p>
                      <div className="flex flex-wrap items-center gap-4 text-xs font-bold text-content-primary pt-1">
                        <span>Items: {order.items?.length || 1}</span>
                        <span>•</span>
                        <span className="text-content-brand text-sm font-black">Total: ₹{order.totalAmount}</span>
                        {order.deliveryOtp && (
                          <>
                            <span>•</span>
                            <span className="font-mono text-2xs bg-surface-subtle px-2 py-0.5 rounded text-content-secondary">OTP: {order.deliveryOtp}</span>
                          </>
                        )}
                      </div>
                    </div>

                    <div className="shrink-0 flex items-center space-x-2.5">
                      {/* 1. Direct Accept Order Button (Triggers Rider Notification) */}
                      {(order.sellerApprovalStatus === 'PENDING' || order.status === 'PLACED' || order.status === 'PENDING_APPROVAL') && (
                        <button
                          type="button"
                          onClick={(e) => handleAcceptOrder(e, order.id)}
                          disabled={actionLoadingId === order.id}
                          className="px-4 py-2.5 bg-action-speedBg hover:bg-action-speedHover text-white rounded-xl text-xs font-black shadow-md flex items-center space-x-2 transition-all transform hover:scale-105 active:scale-95 cursor-pointer disabled:opacity-50"
                        >
                          {actionLoadingId === order.id ? (
                            <>
                              <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                              <span>Broadcasting to Riders…</span>
                            </>
                          ) : (
                            <>
                              <CheckCircle2 className="w-4 h-4" />
                              <span>Accept &amp; Broadcast to Riders</span>
                            </>
                          )}
                        </button>
                      )}

                      {/* 2. Ready for Pickup Action (if Accepted or Packed) */}
                      {(order.status === 'SELLER_ACCEPTED' || order.status === 'PACKED') && (
                        <button
                          type="button"
                          onClick={(e) => handleReadyForPickup(e, order.id)}
                          disabled={actionLoadingId === order.id}
                          className="px-4 py-2.5 bg-action-primaryBg hover:bg-action-primaryHover text-white rounded-xl text-xs font-black shadow-md flex items-center space-x-2 transition-all transform hover:scale-105 active:scale-95 cursor-pointer disabled:opacity-50"
                        >
                          {actionLoadingId === order.id ? (
                            <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                          ) : (
                            <Package className="w-4 h-4" />
                          )}
                          <span>Ready for Pickup</span>
                        </button>
                      )}

                      {/* 3. Rider Broadcast Status Badge */}
                      {order.status === 'READY_FOR_PICKUP' && (
                        <div className="flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-surface-brandSubtle text-content-brand text-xs font-bold border border-border-brandSubtle">
                          <Bike className="w-4 h-4 text-content-brand" />
                          <span>Broadcasted to Riders</span>
                        </div>
                      )}

                      {/* 4. Details Navigation */}
                      <div
                        className="px-3.5 py-2.5 bg-surface-subtle hover:bg-surface-muted text-content-primary rounded-xl text-xs font-bold flex items-center space-x-1.5 transition-all border border-border-default cursor-pointer"
                      >
                        <Eye className="w-3.5 h-3.5 text-content-secondary" />
                        <span>Details</span>
                        <ArrowRight className="w-3.5 h-3.5 text-content-muted group-hover:translate-x-1 transition-transform" />
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </main>
      </div>
    </div>
    </SellerAuthGuard>
  );
}
