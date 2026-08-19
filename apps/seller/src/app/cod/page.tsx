'use client';

import React, { useEffect, useMemo, useState } from 'react';
import SellerSidebar from '../../components/SellerSidebar';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { formatAddress } from '../../lib/formatAddress';
import { IndianRupee, RefreshCw, CheckCircle2 } from 'lucide-react';

import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';

export default function CodPage() {
  const [orders, setOrders] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [codModalOrder, setCodModalOrder] = useState<any | null>(null);
  const [collectedCashInput, setCollectedCashInput] = useState('');
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

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

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setStatusMessage({ text, type });
    setTimeout(() => setStatusMessage(null), 4000);
  };

  const codOrders = useMemo(() => orders.filter(o => o.paymentMethod === 'COD'), [orders]);
  const pendingCodAmount = useMemo(() => {
    return codOrders
      .filter(o => o.paymentStatus === 'COD_PENDING_COLLECTION' && o.orderStatus !== 'CANCELLED')
      .reduce((acc, o) => acc + (o.cod?.amountToCollect || o.totalAmount || 0), 0);
  }, [codOrders]);
  const collectedCodAmount = useMemo(() => {
    return codOrders
      .filter(o => o.paymentStatus === 'COD_COLLECTED')
      .reduce((acc, o) => acc + (o.cod?.collectedAmount || o.totalAmount || 0), 0);
  }, [codOrders]);

  const handleConfirmCodCollection = async () => {
    if (!codModalOrder) return;
    try {
      const amount = parseFloat(collectedCashInput) || codModalOrder.totalAmount;
      const res = await sellerApi.post(`/api/v1/orders/${codModalOrder.id}/collect-cod`, {
        collectedAmount: amount,
        shortageAmount: 0,
        notes: 'Cash verified and settled by Store Merchant',
      });

      if (res.ok) {
        showToast(`COD Payment ₹${amount} collected for ${codModalOrder.id}!`);
        setCodModalOrder(null);
        fetchOrders();
      } else {
        showToast(res.error || 'Failed to record cash collection', 'error');
      }
    } catch (e) {
      showToast('Error recording COD collection', 'error');
    }
  };

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex font-sans antialiased">
      {statusMessage && (
        <div className={`fixed top-4 right-4 z-50 px-5 py-3 rounded-2xl shadow-2xl font-bold text-xs flex items-center space-x-2 transition-all border ${statusMessage.type === 'success' ? 'bg-action-primaryBg text-white border-border-brand' : 'bg-action-dangerBg text-white border-border-danger'}`}>
          <CheckCircle2 className="w-4 h-4" />
          <span>{statusMessage.text}</span>
        </div>
      )}

      <SellerSidebar activeTab="cod" pendingCodAmount={pendingCodAmount} onRefresh={fetchOrders} isLoading={isLoading} />

      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0 gap-4">
          <div className="flex items-center space-x-3">
            <span className="px-2.5 py-1 rounded-full bg-surface-warningSubtle text-content-warning text-xs font-bold border border-border-warning">
              Cash on Delivery
            </span>
            <span className="text-content-muted">/</span>
            <span className="text-xs font-bold text-content-primary" suppressHydrationWarning>{storeName}</span>
          </div>

          <div className="flex items-center space-x-4">
            <HeaderQuickSearch />
            <button onClick={fetchOrders} className="p-2 text-content-secondary hover:text-content-accent hover:bg-surface-subtle rounded-lg transition-all" aria-label="Refresh COD ledger">
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </header>

        <main className="p-8 space-y-6">
          {/* Summary */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Pending Cash Collection (Rider Handoff)</p>
              <h3 className="text-3xl font-black text-content-warning mt-2">₹{pendingCodAmount.toFixed(2)}</h3>
              <p className="text-2xs text-content-muted mt-1">Awaiting Doorstep Cash Reconciliation</p>
            </div>
            <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Total COD Collected & Settled</p>
              <h3 className="text-3xl font-black text-content-brand mt-2">₹{collectedCodAmount.toFixed(2)}</h3>
              <p className="text-2xs text-content-muted mt-1">Reconciled Cash In Hand</p>
            </div>
          </div>

          {/* Table */}
          <div className="bg-white border border-border-default rounded-2xl shadow-sm overflow-hidden">
            <div className="p-6 border-b border-border-subtle flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <IndianRupee className="w-5 h-5 text-content-warning" />
                <h2 className="text-lg font-black text-content-primary">Cash on Delivery Orders</h2>
              </div>
              <span className="text-xs font-bold text-content-muted">{codOrders.length} COD Orders Total</span>
            </div>

            <div className="divide-y divide-border-subtle">
              {codOrders.length === 0 ? (
                <div className="p-12 text-center text-content-muted text-xs">
                  No Cash on Delivery orders recorded for this store.
                </div>
              ) : (
                codOrders.map(order => (
                  <div key={order.id} className="p-6 hover:bg-surface-subtle transition-all flex flex-col md:flex-row md:items-center justify-between gap-6">
                    <div className="space-y-1.5">
                      <div className="flex items-center space-x-3">
                        <span className="font-mono text-sm font-black text-content-accent">{order.id}</span>
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${
                          order.paymentStatus === 'COD_COLLECTED'
                            ? 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'
                            : 'bg-surface-warningSubtle text-content-warning border border-border-warning'
                        }`}>
                          {order.paymentStatus}
                        </span>
                      </div>
                      <p className="text-xs text-content-secondary">Customer: <strong className="text-content-primary">{order.customerName || order.customerId || '—'}</strong> • Phone: <strong className="text-content-primary">{order.customerPhone || '—'}</strong></p>
                      <p className="text-xs font-semibold text-content-secondary">{formatAddress(order.deliveryAddress)}</p>
                      <p className="text-xs font-bold text-content-primary">Amount: ₹{order.totalAmount}</p>
                    </div>

                    <div className="shrink-0 flex items-center space-x-3">
                      {order.paymentStatus === 'COD_PENDING_COLLECTION' && (
                        <button
                          onClick={() => {
                            setCodModalOrder(order);
                            setCollectedCashInput(String(order.totalAmount));
                          }}
                          className="px-4 py-2 bg-action-warningBg hover:opacity-90 text-white text-xs font-bold rounded-xl shadow-md transition"
                        >
                          Collect Cash
                        </button>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </main>
      </div>

      {/* Modal */}
      {codModalOrder && (
        <div className="fixed inset-0 bg-surface-inverse/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-white border border-border-default rounded-3xl p-8 max-w-md w-full shadow-2xl space-y-6">
            <h3 className="text-lg font-black text-content-primary">Confirm Cash Collection</h3>
            <p className="text-xs text-content-secondary">Order: <code className="font-mono text-content-accent">{codModalOrder.id}</code></p>
            
            <div className="space-y-2">
              <label className="text-xs font-bold text-content-secondary">Amount Collected (₹)</label>
              <input
                type="number"
                value={collectedCashInput}
                onChange={e => setCollectedCashInput(e.target.value)}
                className="w-full px-4 py-3 bg-surface-subtle border border-border-default rounded-xl text-sm font-bold text-content-primary outline-none focus:border-border-accent"
              />
            </div>

            <div className="flex items-center justify-end space-x-3">
              <button
                onClick={() => setCodModalOrder(null)}
                className="px-5 py-2.5 bg-surface-subtle hover:bg-surface-muted text-content-secondary text-xs font-bold rounded-xl transition"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmCodCollection}
                className="px-5 py-2.5 bg-action-warningBg hover:opacity-90 text-white text-xs font-bold rounded-xl shadow-md transition"
              >
                Confirm Cash
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
