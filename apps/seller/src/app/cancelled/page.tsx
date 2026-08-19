'use client';

import React, { useEffect, useMemo, useState } from 'react';
import SellerSidebar from '../../components/SellerSidebar';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { formatAddress } from '../../lib/formatAddress';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import { XCircle, RefreshCw } from 'lucide-react';

export default function DedicatedCancelledPage() {
  const [orders, setOrders] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { session, storeName } = useSellerSession();

  const fetchOrders = async () => {
    setIsLoading(true);
    try {
      const res = await sellerApi.get('/api/v1/orders/seller');
      if (res.ok && res.data) {
        setOrders(Array.isArray(res.data) ? res.data : (res.data.orders || []));
      }
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, []);

  const cancelledOrders = useMemo(() => orders.filter(o => o.orderStatus === 'CANCELLED' || o.status === 'CANCELLED'), [orders]);

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex font-sans antialiased">
      <SellerSidebar activeTab="cancelled" cancelledCount={cancelledOrders.length} onRefresh={fetchOrders} isLoading={isLoading} />

      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0 gap-4">
          <div className="flex items-center space-x-3">
            <span className="px-2.5 py-1 rounded-full bg-surface-dangerSubtle text-content-danger text-xs font-bold border border-border-danger">
              Cancelled Orders
            </span>
            <span className="text-content-muted">/</span>
            <span className="text-xs font-bold text-content-primary" suppressHydrationWarning>{storeName}</span>
          </div>

          <div className="flex items-center space-x-3">
            <HeaderQuickSearch onSelectOrder={() => {}} />
            <button
              onClick={fetchOrders}
              disabled={isLoading}
              className="p-2 bg-surface-subtle hover:bg-surface-muted rounded-xl text-content-secondary transition"
              title="Refresh Orders"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </header>

        <main className="p-8 max-w-7xl w-full mx-auto space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold text-content-primary">Cancelled Orders & Inventory Reversals</h1>
              <p className="text-xs text-content-secondary">Orders where inventory has been restored back to available stock</p>
            </div>
            <div className="text-xs font-bold text-content-secondary bg-white px-3 py-1.5 rounded-lg border border-border-default">
              {cancelledOrders.length} Cancelled
            </div>
          </div>

          <div className="bg-white border border-border-default rounded-2xl overflow-hidden shadow-sm">
            <table className="w-full text-left text-xs text-content-secondary">
              <thead className="bg-surface-subtle text-content-secondary uppercase text-2xs font-bold tracking-wider border-b border-border-default">
                <tr>
                  <th className="px-6 py-4">Order ID</th>
                  <th className="px-6 py-4">Customer Address</th>
                  <th className="px-6 py-4">Amount</th>
                  <th className="px-6 py-4">Cancellation Reason</th>
                  <th className="px-6 py-4 text-right">Inventory Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle">
                {isLoading ? (
                  <tr>
                    <td colSpan={5} className="px-6 py-12 text-center text-content-muted">
                      <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2 text-content-accent" />
                      Loading cancelled orders…
                    </td>
                  </tr>
                ) : cancelledOrders.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-6 py-12 text-center text-content-muted">
                      <XCircle className="w-8 h-8 mx-auto mb-2 text-content-muted" />
                      No cancelled orders found.
                    </td>
                  </tr>
                ) : (
                  cancelledOrders.map(o => (
                    <tr key={o.id} className="hover:bg-surface-subtle">
                      <td className="px-6 py-4 font-mono font-bold text-content-primary">#{o.id}</td>
                      <td className="px-6 py-4 text-content-secondary">{formatAddress(o.deliveryAddress)}</td>
                      <td className="px-6 py-4 font-bold text-content-primary">₹{(o.totalAmount || 0).toFixed(2)}</td>
                      <td className="px-6 py-4 text-content-danger font-medium">{o.cancellationReason || o.cancellation?.reason || 'Customer / Merchant Request'}</td>
                      <td className="px-6 py-4 text-right">
                        <span className="px-2.5 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-2xs font-bold border border-border-brandSubtle">
                          Stock Restored
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </main>
      </div>
    </div>
  );
}
