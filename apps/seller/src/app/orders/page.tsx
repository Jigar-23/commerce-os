'use client';

import React, { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import SellerSidebar from '../../components/SellerSidebar';
import {
  Package, Search, CheckCircle2, RefreshCw, Eye, ArrowRight, ShieldCheck
} from 'lucide-react';

import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { formatAddress } from '../../lib/formatAddress';

export default function OrdersPage() {
  const [orders, setOrders] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [orderQuery, setOrderQuery] = useState('');
  const [orderFilter, setOrderFilter] = useState('ALL');
  const { session, storeName } = useSellerSession();

  const fetchOrders = async (showSpinner = true) => {
    if (showSpinner) setIsLoading(true);
    try {
      const res = await sellerApi.get('/api/v1/orders/seller');
      if (res.ok && res.data) {
        setFetchError(null);
        setOrders(Array.isArray(res.data) ? res.data : (res.data?.orders || []));
      } else {
        setFetchError(res.error || 'Failed to fetch seller orders.');
      }
    } catch (e: any) {
      setFetchError(e.message || 'Network error fetching orders.');
    } finally {
      if (showSpinner) setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders(true);

    // 1. Realtime SSE Stream Subscription
    let eventSource: EventSource | null = null;
    try {
      const token = typeof window !== 'undefined' ? localStorage.getItem('seller_token') || sessionStorage.getItem('seller_token') : null;
      const gatewayUrl = (process.env.NEXT_PUBLIC_API_GATEWAY_URL || '').replace(/\/$/, '') || (process.env.NODE_ENV === 'production' ? '' : 'http://localhost:8083');
      const streamUrl = `${gatewayUrl}/api/v1/realtime/stream${token ? `?token=${encodeURIComponent(token)}` : ''}`;
      
      eventSource = new EventSource(streamUrl);
      eventSource.onmessage = () => fetchOrders(false);
      eventSource.addEventListener('ORDER_PLACED', () => fetchOrders(false));
      eventSource.addEventListener('ORDER_STATUS_CHANGED', () => fetchOrders(false));
      eventSource.addEventListener('DISPATCH_REQUESTED', () => fetchOrders(false));
      eventSource.addEventListener('SELLER_ORDER_ACCEPTED', () => fetchOrders(false));
      eventSource.onerror = () => {};
    } catch (_) {}

    // 2. Heartbeat Reconciliation Fallback
    const timer = setInterval(() => {
      fetchOrders(false);
    }, 20000);

    return () => {
      clearInterval(timer);
      if (eventSource) eventSource.close();
    };
  }, []);

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

      let matchFilter = true;
      if (orderFilter === 'COD_PENDING') matchFilter = o.paymentMethod === 'COD' && o.paymentStatus === 'COD_PENDING_COLLECTION';
      else if (orderFilter === 'COD_COLLECTED') matchFilter = o.paymentStatus === 'COD_COLLECTED';
      else if (orderFilter !== 'ALL') matchFilter = o.orderStatus === orderFilter;

      return matchSearch && matchFilter;
    });
  }, [orders, orderQuery, orderFilter]);

  return (
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
            <button onClick={fetchOrders} className="p-2 text-content-secondary hover:text-content-accent hover:bg-surface-subtle rounded-lg transition-all" aria-label="Refresh orders">
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </header>

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
                  <div key={order.id} className="p-6 hover:bg-surface-subtle transition-all flex flex-col md:flex-row md:items-center justify-between gap-6">
                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-3">
                        <Link href={`/orders/${order.id}`} className="text-base font-black text-content-accent hover:text-content-accent underline font-mono flex items-center space-x-1">
                          <span>{order.id}</span>
                        </Link>

                        <span className={`px-3 py-1 rounded-full text-xs font-black ${order.orderStatus === 'CANCELLED' ? 'bg-surface-dangerSubtle text-content-danger border border-border-danger' : 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'}`}>
                          Status: {order.orderStatus}
                        </span>
                      </div>

                      <p className="text-xs text-content-secondary">Customer ID: <code className="font-mono text-content-accent font-bold">{order.customerId || '—'}</code> • Phone: <strong className="text-content-primary">{order.customerPhone || '—'}</strong></p>
                      <p className="text-xs font-semibold text-content-secondary">{formatAddress(order.deliveryAddress)}</p>
                      <p className="text-xs font-extrabold text-content-primary">Total: ₹{order.totalAmount} ({order.paymentMethod}: {order.paymentStatus})</p>
                    </div>

                    <div className="shrink-0">
                      <Link
                        href={`/orders/${order.id}`}
                        className="px-5 py-2.5 bg-action-speedBg hover:bg-action-speedHover text-white rounded-xl text-xs font-bold shadow-md flex items-center space-x-2"
                      >
                        <Eye className="w-4 h-4" />
                        <span>Manage Order</span>
                        <ArrowRight className="w-4 h-4" />
                      </Link>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
