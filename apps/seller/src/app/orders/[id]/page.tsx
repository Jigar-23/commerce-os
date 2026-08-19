'use client';

import React, { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import SellerSidebar from '../../../components/SellerSidebar';
import {
  Package, ArrowLeft, CheckCircle2, Box, Truck, IndianRupee, RotateCcw, AlertTriangle, ShieldCheck, MapPin, User, Phone, Tag, Calendar
} from 'lucide-react';

import { formatAddress } from '../../../lib/formatAddress';
import { sellerApi } from '@/lib/apiClient';

export default function DedicatedSingleOrderPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const orderId = params?.id;

  const [order, setOrder] = useState<any | null>(null);
  const [trackingData, setTrackingData] = useState<any | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Undo System State
  const [lastOrderAction, setLastOrderAction] = useState<{
    orderId: string;
    description: string;
    previousStatus: string;
    previousPaymentStatus: string;
  } | null>(null);
  const [showUndoModal, setShowUndoModal] = useState(false);

  // Modals
  const [codModal, setCodModal] = useState(false);
  const [collectedCashInput, setCollectedCashInput] = useState('');
  const [cancelModal, setCancelModal] = useState(false);
  const [cancelReasonInput, setCancelReasonInput] = useState('');

  const fetchOrderDetails = async () => {
    setIsLoading(true);
    try {
      const res = await sellerApi.get(`/api/v1/orders/${orderId}`);
      if (res.ok && res.data) {
        setOrder(res.data);
        fetchTracking(orderId);
      } else {
        showToast(res.error || `Order ${orderId} not found`, 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error fetching order details', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  const fetchTracking = async (id: string) => {
    try {
      const res = await sellerApi.get(`/api/v1/orders/${id}/india-post-tracking`);
      if (res.ok && res.data) setTrackingData(res.data);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    if (orderId) {
      fetchOrderDetails();
      const timer = setInterval(() => {
        fetchOrderDetails();
      }, 3000);
      return () => clearInterval(timer);
    }
  }, [orderId]);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setStatusMessage({ text, type });
    setTimeout(() => setStatusMessage(null), 4000);
  };

  const handleDomainTransition = async (action: 'accept' | 'pack' | 'ready-pickup') => {
    try {
      let endpoint = `/api/v1/orders/${orderId}/accept-by-seller`;
      if (action === 'pack') endpoint = `/api/v1/orders/${orderId}/pack`;
      if (action === 'ready-pickup') endpoint = `/api/v1/orders/${orderId}/ready-for-pickup`;

      const res = await sellerApi.post(endpoint);
      if (res.ok) {
        showToast(`Order updated successfully: ${action.toUpperCase()}`);
        fetchOrderDetails();
      } else {
        showToast(res.error || 'Failed to update order state', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Network error updating order', 'error');
    }
  };

  const handleTransition = async (targetStatus: string) => {
    try {
      let endpoint = `/api/v1/orders/${orderId}/status`;
      if (targetStatus === 'SELLER_ACCEPTED') endpoint = `/api/v1/orders/${orderId}/accept-by-seller`;
      else if (targetStatus === 'PACKED') endpoint = `/api/v1/orders/${orderId}/pack`;
      else if (targetStatus === 'OUT_FOR_DELIVERY') endpoint = `/api/v1/orders/${orderId}/ready-for-pickup`;

      const res = await sellerApi.post(endpoint, { targetStatus, actor: 'SELLER' });
      if (res.ok) {
        showToast(`Order transitioned to ${targetStatus}`);
        fetchOrderDetails();
      } else {
        showToast(res.error || `Failed to transition to ${targetStatus}`, 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error updating order status', 'error');
    }
  };

  const handleConfirmCodCollection = async () => {
    try {
      const amount = parseFloat(collectedCashInput) || order.totalAmount;
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/collect-cod`, {
        collectedAmount: amount,
        notes: 'Cash received & verified by Merchant',
      });

      if (res.ok) {
        showToast(`COD ₹${amount} cash collected & reconciled in DB!`);
        setCodModal(false);
        fetchOrderDetails();
      } else {
        showToast(res.error || 'Failed to record COD collection', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error recording COD collection', 'error');
    }
  };

  const handleShipWithConsignment = async () => {
    try {
      const consignmentNumber = `IN-POST-${Date.now().toString().slice(-6)}`;
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/ship-consignment`, {
        consignmentNumber,
        carrier: 'INDIA_POST',
        actor: 'SELLER',
      });
      if (res.ok) {
        showToast(`Order shipped with Consignment #${consignmentNumber}`);
        fetchOrderDetails();
      } else {
        showToast(res.error || 'Failed to ship order with consignment', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error shipping order', 'error');
    }
  };

  const handleExecuteCancel = async () => {
    try {
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/cancel`, {
        reason: cancelReasonInput.trim() || 'Seller decision',
        cancelledBy: 'SELLER',
      });

      if (res.ok) {
        showToast(`Order ${orderId} cancelled & stock restored to DB!`);
        setCancelModal(false);
        fetchOrderDetails();
      } else {
        showToast(res.error || 'Failed to cancel order', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error cancelling order', 'error');
    }
  };

  const handleExecuteUndo = async () => {
    if (!lastOrderAction) return;
    try {
      const res = await fetch(`/api/v1/orders/${orderId}/undo`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          targetStatus: lastOrderAction.previousStatus,
          targetPaymentStatus: lastOrderAction.previousPaymentStatus,
          actor: 'SELLER',
        }),
      });

      if (res.ok) {
        showToast(`UNDONE! Order ${orderId} reverted to ${lastOrderAction.previousStatus} in DB!`);
        setLastOrderAction(null);
        setShowUndoModal(false);
        fetchOrderDetails();
      } else {
        showToast('Failed to execute UNDO in DB', 'error');
      }
    } catch (e) {
      showToast('Error executing UNDO', 'error');
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

      <SellerSidebar activeTab="orders" ordersCount={0} onRefresh={fetchOrderDetails} isLoading={isLoading} />

      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0">
          <div className="flex items-center space-x-3">
            <button onClick={() => router.push('/orders')} className="p-2 bg-surface-subtle hover:bg-surface-muted rounded-xl text-content-secondary transition-all flex items-center space-x-1 font-bold text-xs">
              <ArrowLeft className="w-4 h-4" />
              <span>Back to Orders List</span>
            </button>
            <span className="text-content-muted">/</span>
            <span className="px-2.5 py-1 rounded-full bg-surface-accentSubtle text-content-accent text-xs font-bold font-mono border border-border-accent">
              {orderId}
            </span>
          </div>

          {lastOrderAction && (
            <button onClick={() => setShowUndoModal(true)} className="px-3.5 py-2 bg-action-warningBg hover:opacity-90 text-white font-bold rounded-xl text-xs shadow-md flex items-center space-x-1.5 animate-pulse">
              <RotateCcw className="w-3.5 h-3.5" />
              <span>UNDO Last Change</span>
            </button>
          )}
        </header>

        {isLoading ? (
          <div className="flex-1 flex items-center justify-center p-12">
            <div className="w-8 h-8 border-4 border-border-accent border-t-transparent rounded-full animate-spin" />
          </div>
        ) : !order ? (
          <div className="p-8 text-center text-content-secondary font-bold text-sm">Order not found in Database.</div>
        ) : (
          <main className="p-8 space-y-6 max-w-5xl">
            {/* Header Banner */}
            <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <span className="text-2xs font-bold bg-surface-accentSubtle text-content-accent px-2.5 py-1 rounded-full uppercase border border-border-accent">Dedicated Order Control Page (/orders/[id])</span>
                <h1 className="text-2xl font-black text-content-primary font-mono mt-1">{order.id}</h1>
                <p className="text-xs text-content-muted">Created: {new Date(order.createdAt || Date.now()).toLocaleString()}</p>
              </div>

              <div className="flex items-center space-x-3">
                <span className={`px-4 py-1.5 rounded-full text-xs font-extrabold ${order.orderStatus === 'CANCELLED' ? 'bg-surface-dangerSubtle text-content-danger border border-border-danger' : 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'}`}>
                  Status: {order.orderStatus}
                </span>
                <span className="px-4 py-1.5 rounded-full text-xs font-extrabold bg-surface-warningSubtle text-content-warning border border-border-warning">
                  {order.paymentMethod}: {order.paymentStatus}
                </span>
              </div>
            </div>

            {/* CUSTOMER IDENTITY & MAPPING PANEL */}
            <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm space-y-4">
              <div className="flex items-center space-x-2 text-content-accent border-b border-border-subtle pb-3">
                <User className="w-5 h-5" />
                <h3 className="text-base font-black text-content-primary">Customer Identity & Unique Database Mapping</h3>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                <div className="bg-surface-subtle p-4 rounded-xl border border-border-default space-y-1">
                  <span className="text-content-muted font-bold uppercase text-2xs">Customer Unique ID</span>
                  <p className="font-mono font-bold text-content-accent text-sm truncate">{order.customerId || '—'}</p>
                </div>

                <div className="bg-surface-subtle p-4 rounded-xl border border-border-default space-y-1">
                  <span className="text-content-muted font-bold uppercase text-2xs">Registered Phone Number</span>
                  <p className="font-bold text-content-primary text-sm flex items-center space-x-1">
                    <Phone className="w-3.5 h-3.5 text-content-brand" />
                    <span>{order.customerPhone || '—'}</span>
                  </p>
                </div>

                <div className="bg-surface-subtle p-4 rounded-xl border border-border-default space-y-1">
                  <span className="text-content-muted font-bold uppercase text-2xs">Handoff Verification OTP</span>
                  <p className="font-mono font-black text-content-accent text-base">{order.deliveryOtp || '—'}</p>
                </div>
              </div>

              <div className="bg-surface-subtle p-4 rounded-xl border border-border-default text-xs">
                <span className="text-content-muted font-bold uppercase text-2xs">Delivery Address</span>
                <p className="font-semibold text-content-primary mt-1 flex items-center space-x-1">
                  <MapPin className="w-4 h-4 text-content-danger shrink-0" />
                  <span>{formatAddress(order.deliveryAddress)}</span>
                </p>
              </div>
            </div>

            {/* ORDER ITEMS TABLE */}
            <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm space-y-4">
              <h3 className="text-base font-black text-content-primary">Order Purchased Items</h3>
              <div className="space-y-2 text-xs">
                {order.items.map((item: any, idx: number) => (
                  <div key={idx} className="flex justify-between items-center bg-surface-subtle p-3.5 rounded-xl border border-border-default">
                    <div>
                      <p className="font-bold text-content-primary text-sm">{item.name}</p>
                      <p className="text-2xs text-content-muted">SKU: <code className="font-mono text-content-accent font-bold">{item.sku}</code> • Pack: {item.packSize || 'Standard'}</p>
                    </div>
                    <div className="text-right">
                      <p className="font-black text-content-primary text-sm">₹{item.unitPrice * item.quantity}</p>
                      <p className="text-2xs text-content-muted">{item.quantity} x ₹{item.unitPrice}</p>
                    </div>
                  </div>
                ))}

                <div className="flex justify-between items-center p-4 bg-surface-accentSubtle border border-border-accent rounded-xl font-black text-content-primary text-base pt-3">
                  <span>Total Amount Payable:</span>
                  <span className="text-content-accent text-xl font-mono">₹{order.totalAmount}</span>
                </div>
              </div>
            </div>

            {/* INDIA POST LIVE TRACKING TIMELINE (Commented out for instant direct rider dispatch) */}
            {/*
            {trackingData && (
              <div className="bg-surface-brandSubtle border border-border-brandSubtle rounded-2xl p-6 shadow-sm space-y-4 text-xs">
                <div className="flex items-center justify-between border-b border-border-brandSubtle pb-3">
                  <div className="flex items-center space-x-2">
                    <Truck className="w-5 h-5 text-content-brand" />
                    <h3 className="text-base font-black text-content-brand">India Post Consignment Live Checkpoints</h3>
                  </div>
                  <span className="font-mono text-xs bg-surface-brandSubtle text-content-brand px-3 py-1 rounded-full font-bold border border-border-brandSubtle">
                    Tracking #: {trackingData.consignmentNumber}
                  </span>
                </div>

                <div className="space-y-2.5">
                  {trackingData.checkpoints.map((cp: any, idx: number) => (
                    <div key={idx} className="flex items-start space-x-3 bg-white p-3 rounded-xl border border-border-brandSubtle shadow-sm">
                      <span className="w-3 h-3 rounded-full bg-action-primaryBg mt-1 shrink-0" />
                      <div className="flex-1">
                        <p className="font-extrabold text-content-primary text-xs">{cp.status}: {cp.details}</p>
                        <p className="text-2xs text-content-muted font-mono mt-0.5">{cp.location} • {new Date(cp.timestamp).toLocaleString()}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
            */}

            {/* DEDICATED DATABASE OPERATIONS CONTROL PANEL */}
            <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm space-y-4">
              <h3 className="text-base font-black text-content-primary border-b border-border-subtle pb-3">Full Database Operations Control Panel</h3>
              <div className="flex flex-wrap items-center gap-3">
                {order.orderStatus === 'PLACED' && (
                  <button onClick={() => handleTransition('SELLER_ACCEPTED')} className="px-5 py-3 bg-action-speedBg hover:bg-action-speedHover text-white rounded-xl font-bold text-xs shadow-md">
                    Accept Order & Dispatch Delivery Partner
                  </button>
                )}
                {/* Pack and India Post steps commented out per single local store workflow
                {order.orderStatus === 'SELLER_ACCEPTED' && (
                  <button onClick={() => handleTransition('PACKED')} className="px-5 py-3 bg-action-editorialBg hover:bg-action-editorialHover text-white rounded-xl font-bold text-xs shadow-md">
                    Pack Order
                  </button>
                )}
                {order.orderStatus === 'PACKED' && (
                  <button onClick={handleShipWithConsignment} className="px-5 py-3 bg-action-primaryBg hover:bg-action-primaryHover text-white rounded-xl font-bold text-xs shadow-md">
                    Ship via India Post & Set Consignment #
                  </button>
                )}
                */}
                {order.orderStatus === 'SHIPPED' && (
                  <button onClick={() => handleTransition('OUT_FOR_DELIVERY')} className="px-5 py-3 bg-action-speedBg hover:bg-action-speedHover text-white rounded-xl font-bold text-xs shadow-md">
                    Mark Out for Delivery
                  </button>
                )}
                {order.paymentMethod === 'COD' && order.paymentStatus === 'COD_PENDING_COLLECTION' && (
                  <button onClick={() => { setCodModal(true); setCollectedCashInput(String(order.totalAmount)); }} className="px-5 py-3 bg-action-warningBg hover:opacity-90 text-white rounded-xl font-bold text-xs shadow-md">
                    Collect COD Cash & Reconcile
                  </button>
                )}
                {order.orderStatus !== 'DELIVERED' && order.orderStatus !== 'CANCELLED' && (
                  <button onClick={() => setCancelModal(true)} className="px-5 py-3 border border-border-danger hover:bg-surface-dangerSubtle text-content-danger rounded-xl font-bold text-xs">
                    Cancel Order & Release Stock
                  </button>
                )}
              </div>
            </div>
          </main>
        )}
      </div>

      {/* DOUBLE CONFIRMATION UNDO MODAL */}
      {showUndoModal && lastOrderAction && (
        <div className="fixed inset-0 z-50 bg-surface-inverse/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-border-default max-w-md w-full p-6 space-y-4">
            <div className="flex items-center space-x-3 text-content-warning border-b border-border-subtle pb-3">
              <AlertTriangle className="w-6 h-6 shrink-0" />
              <div>
                <h3 className="text-base font-extrabold text-content-primary">Double Confirm: Revert Order Decision</h3>
                <p className="text-2xs text-content-muted">Database Change Undo Confirmation</p>
              </div>
            </div>

            <div className="text-xs space-y-2 text-content-secondary bg-surface-subtle p-3.5 rounded-xl border border-border-default">
              <p>Are you sure you want to <strong>UNDO</strong> this order decision in DB?</p>
              <p className="font-mono text-content-accent font-bold">{lastOrderAction.description}</p>
            </div>

            <div className="flex justify-end space-x-2 pt-2">
              <button onClick={() => setShowUndoModal(false)} className="px-4 py-2 border border-border-default text-content-secondary rounded-xl font-bold text-xs">Cancel Keep</button>
              <button onClick={handleExecuteUndo} className="px-5 py-2 bg-action-warningBg text-white rounded-xl font-bold text-xs shadow-md">Confirm & Revert Order DB Change</button>
            </div>
          </div>
        </div>
      )}

      {/* COD Modal */}
      {codModal && (
        <div className="fixed inset-0 z-50 bg-surface-inverse/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-border-default max-w-md w-full p-6 space-y-4">
            <h3 className="text-base font-extrabold text-content-primary">Confirm Cash Collection</h3>
            <input type="number" value={collectedCashInput} onChange={e => setCollectedCashInput(e.target.value)} className="w-full p-3 bg-surface-subtle border rounded-xl font-black text-base outline-none" />
            <div className="flex justify-end space-x-2">
              <button onClick={() => setCodModal(false)} className="px-4 py-2 border rounded-xl text-xs font-bold">Cancel</button>
              <button onClick={handleConfirmCodCollection} className="px-5 py-2 bg-action-primaryBg text-white rounded-xl font-bold text-xs">Confirm COD Cash</button>
            </div>
          </div>
        </div>
      )}

      {/* Cancel Modal */}
      {cancelModal && (
        <div className="fixed inset-0 z-50 bg-surface-inverse/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-border-default max-w-md w-full p-6 space-y-4">
            <h3 className="text-base font-extrabold text-content-danger">Cancel Order {orderId}</h3>
            <input type="text" placeholder="Reason for cancellation" value={cancelReasonInput} onChange={e => setCancelReasonInput(e.target.value)} className="w-full p-3 bg-surface-subtle border rounded-xl outline-none text-xs" />
            <div className="flex justify-end space-x-2">
              <button onClick={() => setCancelModal(false)} className="px-4 py-2 border rounded-xl text-xs font-bold">Close</button>
              <button onClick={handleExecuteCancel} className="px-5 py-2 bg-action-dangerBg text-white rounded-xl font-bold text-xs">Execute Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
