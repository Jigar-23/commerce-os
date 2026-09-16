'use client';

import React, { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import SellerSidebar from '../../../components/SellerSidebar';
import {
  Package, ArrowLeft, CheckCircle2, Box, Truck, IndianRupee, RotateCcw,
  AlertTriangle, ShieldCheck, MapPin, User, Phone, Tag, Calendar, Clock,
  Check, Navigation, Bike, RefreshCw, AlertCircle
} from 'lucide-react';

import { formatAddress } from '../../../lib/formatAddress';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import SellerAuthGuard from '../../../components/SellerAuthGuard';

export default function DedicatedSingleOrderPage({ params }: { params?: { id?: string } }) {
  const router = useRouter();
  const routeParams = useParams();
  const orderId = ((routeParams?.id as string) || params?.id || '');
  const { session } = useSellerSession();

  const [order, setOrder] = useState<any | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Modals
  const [codModal, setCodModal] = useState(false);
  const [collectedCashInput, setCollectedCashInput] = useState('');
  const [cancelModal, setCancelModal] = useState(false);
  const [cancelReasonInput, setCancelReasonInput] = useState('');
  const [showRebroadcastModal, setShowRebroadcastModal] = useState(false);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setStatusMessage({ text, type });
    setTimeout(() => setStatusMessage(null), 4000);
  };

  const fetchOrderDetails = async (showSpinner = false) => {
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
      const res = await sellerApi.get(`/api/v1/orders/${orderId}`);
      if (res.ok && res.data) {
        const raw = res.data;
        const normalized = {
          ...raw,
          id: raw.id || raw.order_id,
          orderStatus: raw.orderStatus || raw.status,
          status: raw.status || raw.orderStatus,
          totalAmount: raw.totalAmount || raw.total_amount || '0.00',
          paymentMethod: raw.paymentMethod || raw.payment_method || (raw.is_cod ? 'COD' : 'ONLINE'),
          paymentStatus: raw.paymentStatus || raw.payment_status || 'PENDING',
          deliveryAddress: raw.deliveryAddress || raw.delivery_address,
          customerId: raw.customerId || raw.customer_id,
          customerPhone: raw.customerPhone || raw.customer_phone || raw.delivery_address?.contactPhone || raw.delivery_address?.phone,
          customerName: raw.customerName || raw.customer_name,
          createdAt: raw.createdAt || raw.created_at,
          sellerApprovalStatus: raw.sellerApprovalStatus || raw.seller_approval_status,
          riderId: raw.riderId || raw.rider_id,
        };
        setOrder(normalized);
      } else {
        showToast(res.error || `Order ${orderId} not found`, 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error fetching order details', 'error');
    } finally {
      if (showSpinner) setIsLoading(false);
    }
  };

  useEffect(() => {
    if (orderId) {
      fetchOrderDetails(true);

      // Realtime SSE Stream Connection
      let eventSource: EventSource | null = null;
      let isSubscribed = true;

      const initRealtimeStream = async () => {
        try {
          const token = session?.token;
          if (!token || !isSubscribed) return;
          const gatewayUrl = sellerApi.getBaseUrl();
          let streamUrl = `${gatewayUrl}/api/v1/realtime/stream?token=${encodeURIComponent(token)}`;

          try {
            const res = await fetch(`${gatewayUrl}/api/v1/realtime/ticket`, {
              method: 'POST',
              headers: { Authorization: `Bearer ${token}` }
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
          eventSource.onmessage = () => fetchOrderDetails(false);
          eventSource.addEventListener('ORDER_STATUS_CHANGED', () => fetchOrderDetails(false));
          eventSource.addEventListener('RIDER_ASSIGNED', () => fetchOrderDetails(false));
          eventSource.addEventListener('ORDER_DELIVERED', () => fetchOrderDetails(false));
          eventSource.onerror = () => {};
        } catch (_) {}
      }

      initRealtimeStream();

      // Heartbeat Poll (5s)
      const timer = setInterval(() => {
        fetchOrderDetails(false);
      }, 5000);

      return () => {
        isSubscribed = false;
        clearInterval(timer);
        if (eventSource) eventSource.close();
      };
    }
  }, [orderId, session?.token]);

  const handleDomainTransition = async (action: 'accept' | 'pack' | 'ready-pickup') => {
    try {
      let endpoint = `/api/v1/orders/${orderId}/accept-by-seller`;
      if (action === 'pack') endpoint = `/api/v1/orders/${orderId}/pack`;
      if (action === 'ready-pickup') endpoint = `/api/v1/orders/${orderId}/ready-for-pickup`;

      const res = await sellerApi.post(endpoint);
      if (res.ok) {
        showToast(`Order updated successfully: ${action.toUpperCase()}`);
        fetchOrderDetails(false);
      } else {
        showToast(res.error || 'Failed to update order state', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Network error updating order', 'error');
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
        fetchOrderDetails(false);
      } else {
        showToast(res.error || 'Failed to record COD collection', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error recording COD collection', 'error');
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
        fetchOrderDetails(false);
      } else {
        showToast(res.error || 'Failed to cancel order', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error cancelling order', 'error');
    }
  };

  // Helper to resolve stage rank
  const getStageRank = (status?: string): number => {
    if (!status) return 0;
    const s = String(status).toUpperCase();
    if (['DELIVERED', 'COMPLETED'].includes(s)) return 7;
    if (['ARRIVED_CUSTOMER', 'HANDOFF_STARTED'].includes(s)) return 6;
    if (['OUT_FOR_DELIVERY', 'PICKED_UP', 'EN_ROUTE_CUSTOMER', 'IN_TRANSIT'].includes(s)) return 5;
    if (['ARRIVED_STORE', 'ARRIVED_PICKUP', 'ARRIVED_MERCHANT'].includes(s)) return 4;
    if (['RIDER_ASSIGNED', 'ASSIGNED'].includes(s)) return 3;
    if (['SELLER_ACCEPTED', 'ACCEPTED', 'AUTO_ACCEPTED', 'PACKED', 'READY_FOR_PICKUP'].includes(s)) return 2;
    if (['PLACED', 'PENDING_APPROVAL', 'CREATED'].includes(s)) return 1;
    return 1;
  };

  // Helper to resolve stage timestamp from checkpoints or deliverySession
  const getStageInfo = () => {
    const checkpoints = order?.trackingCheckpoints || [];
    const history = order?.deliverySession?.history || order?.riderHistory || [];
    const session = order?.deliverySession || {};

    const placedCp = checkpoints.find((c: any) => c.status === 'PLACED') || { createdAt: order?.createdAt };
    const sellerAcceptedCp = checkpoints.find((c: any) => c.status === 'SELLER_ACCEPTED') || (order?.sellerApprovedAt ? { createdAt: new Date(order.sellerApprovedAt).toISOString() } : null);
    const riderAssignedHist = history.find((h: any) => h.state === 'ASSIGNED') || checkpoints.find((c: any) => c.status === 'RIDER_ASSIGNED');
    const arrivedStoreHist = history.find((h: any) => h.state === 'ARRIVED_PICKUP') || checkpoints.find((c: any) => c.status === 'ARRIVED_PICKUP');
    const pickedUpHist = history.find((h: any) => h.state === 'EN_ROUTE_CUSTOMER') || checkpoints.find((c: any) => c.status === 'OUT_FOR_DELIVERY');
    const arrivedCustomerHist = history.find((h: any) => h.state === 'HANDOFF_STARTED' || h.state === 'ARRIVED_CUSTOMER') || checkpoints.find((c: any) => c.status === 'ARRIVED_CUSTOMER');
    const deliveredHist = history.find((h: any) => h.state === 'DELIVERED') || checkpoints.find((c: any) => c.status === 'DELIVERED') || (session.deliveredAt ? { timestamp: session.deliveredAt } : null);

    return {
      placed: placedCp?.createdAt || order?.createdAt,
      sellerAccepted: sellerAcceptedCp?.createdAt,
      riderAssigned: riderAssignedHist?.timestamp || riderAssignedHist?.createdAt,
      arrivedStore: arrivedStoreHist?.timestamp || arrivedStoreHist?.createdAt,
      pickedUp: pickedUpHist?.timestamp || pickedUpHist?.createdAt,
      arrivedCustomer: arrivedCustomerHist?.timestamp || arrivedCustomerHist?.createdAt,
      delivered: deliveredHist?.timestamp || deliveredHist?.createdAt || (order?.orderStatus === 'DELIVERED' ? order?.updatedAt : null)
    };
  };

  const stages = getStageInfo();
  const rider = order?.rider || (order?.deliverySession?.riderId ? {
    riderId: order.deliverySession.riderId,
    name: order.deliverySession.riderName || 'Assigned Delivery Partner',
    phone: order.deliverySession.riderPhone || '+91 98765 43210',
    vehicle: order.deliverySession.riderVehicle || 'Electric Delivery Vehicle',
    latitude: order.deliverySession?.telemetry?.latitude || order.deliverySession?.current_lat || null,
    longitude: order.deliverySession?.telemetry?.longitude || order.deliverySession?.current_lng || null,
    speedKmh: order.deliverySession?.telemetry?.speedKmh || order.deliverySession?.speed_kmh || null,
    heading: order.deliverySession?.telemetry?.heading || order.deliverySession?.heading || null
  } : null);

  const effectiveStatus = order?.orderStatus || order?.status || '';
  const sessionStatus = order?.deliverySession?.status || order?.deliverySession?.state || '';
  const currentRank = Math.max(
    getStageRank(effectiveStatus),
    getStageRank(sessionStatus),
    (stages.delivered || effectiveStatus === 'DELIVERED') ? 7 : 0,
    (stages.arrivedCustomer || sessionStatus === 'ARRIVED_CUSTOMER' || sessionStatus === 'HANDOFF_STARTED') ? 6 : 0,
    (stages.pickedUp || sessionStatus === 'EN_ROUTE_CUSTOMER' || sessionStatus === 'PICKED_UP' || effectiveStatus === 'OUT_FOR_DELIVERY') ? 5 : 0,
    (stages.arrivedStore || sessionStatus === 'ARRIVED_PICKUP' || sessionStatus === 'ARRIVED_STORE') ? 4 : 0,
    (stages.riderAssigned || rider || order?.riderId || sessionStatus === 'ASSIGNED') ? 3 : 0,
    (stages.sellerAccepted || order?.sellerApprovalStatus === 'ACCEPTED' || order?.sellerApprovalStatus === 'AUTO_ACCEPTED') ? 2 : 0,
    1 // Placed is always complete
  );

  const isStageDone = (stageNum: number) => currentRank >= stageNum;

  return (
    <SellerAuthGuard>
      <div className="min-h-screen bg-surface-canvas text-content-primary flex font-sans antialiased">
        {statusMessage && (
          <div className={`fixed top-4 right-4 z-50 px-5 py-3 rounded-2xl shadow-2xl font-bold text-xs flex items-center space-x-2 transition-all border ${statusMessage.type === 'success' ? 'bg-action-primaryBg text-white border-border-brand' : 'bg-action-dangerBg text-white border-border-danger'}`}>
            <CheckCircle2 className="w-4 h-4" />
            <span>{statusMessage.text}</span>
          </div>
        )}

        <SellerSidebar activeTab="orders" ordersCount={0} onRefresh={() => fetchOrderDetails(true)} isLoading={isLoading} />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* HEADER BAR */}
          <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0">
            <div className="flex items-center space-x-3">
              <button onClick={() => router.push('/orders')} className="p-2 bg-surface-subtle hover:bg-surface-muted rounded-xl text-content-secondary transition-all flex items-center space-x-1 font-bold text-xs cursor-pointer">
                <ArrowLeft className="w-4 h-4" />
                <span>Back to Orders List</span>
              </button>
              <span className="text-content-muted">/</span>
              <span className="px-2.5 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-bold font-mono border border-border-brandSubtle">
                {orderId}
              </span>
            </div>

            <div className="flex items-center space-x-3">
              <button
                onClick={() => fetchOrderDetails(true)}
                className="p-2 text-content-secondary hover:text-content-brand hover:bg-surface-subtle rounded-xl transition-all cursor-pointer"
                title="Refresh Order Data"
              >
                <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-content-brand' : ''}`} />
              </button>
            </div>
          </header>

          {isLoading ? (
            <div className="flex-1 flex items-center justify-center p-12">
              <div className="w-8 h-8 border-4 border-border-brand border-t-transparent rounded-full animate-spin" />
            </div>
          ) : !order ? (
            <div className="p-8 text-center text-content-secondary font-bold text-sm">Order not found in Database.</div>
          ) : (
            <main className="p-8 space-y-6 max-w-5xl">
              {/* Top Banner */}
              <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                  <span className="text-2xs font-bold bg-surface-brandSubtle text-content-brand px-2.5 py-1 rounded-full uppercase border border-border-brandSubtle">
                    Live Order Control &amp; Lifecycle Dispatch
                  </span>
                  <h1 className="text-2xl font-black text-content-primary font-mono mt-1">{order.id}</h1>
                  <p className="text-xs text-content-muted">Placed: {new Date(order.createdAt || Date.now()).toLocaleString()}</p>
                </div>

                <div className="flex items-center space-x-3">
                  <span className={`px-4 py-1.5 rounded-full text-xs font-extrabold ${
                    order.orderStatus === 'DELIVERED'
                      ? 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'
                      : order.orderStatus === 'CANCELLED'
                      ? 'bg-surface-dangerSubtle text-content-danger border border-border-danger'
                      : 'bg-surface-warningSubtle text-content-warning border border-border-warning'
                  }`}>
                    Status: {order.orderStatus || order.status}
                  </span>
                  <span className="px-4 py-1.5 rounded-full text-xs font-extrabold bg-surface-subtle text-content-primary border border-border-default">
                    {order.paymentMethod}: {order.paymentStatus}
                  </span>
                </div>
              </div>

              {/* 1. COMPLETE 7-STAGE LIFECYCLE & RIDER TIMELINE */}
              <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm space-y-5">
                <div className="flex items-center justify-between border-b border-border-default pb-3">
                  <h3 className="text-base font-black text-content-primary flex items-center gap-2">
                    <Clock className="w-5 h-5 text-content-brand" />
                    <span>Real-Time Order Lifecycle &amp; Delivery Milestones</span>
                  </h3>
                  <span className="text-xs font-mono font-bold bg-surface-brandSubtle text-content-brand px-3 py-1 rounded-full border border-border-brandSubtle">
                    Live Updates Active
                  </span>
                </div>

                <div className="relative pl-6 space-y-6">
                  {/* Step 1: Order Placed */}
                  <div className="relative flex items-start space-x-3">
                    {/* Connecting line to Step 2 */}
                    <div className={`absolute -left-[15px] top-5 bottom-[-24px] w-0.5 transition-colors duration-300 ${
                      isStageDone(2) ? 'bg-action-primaryBg' : 'bg-border-default'
                    }`} />
                    <span className="absolute -left-6 top-0.5 w-5 h-5 rounded-full bg-action-primaryBg text-white flex items-center justify-center text-xs font-bold shadow-sm z-10">
                      ✓
                    </span>
                    <div className="flex-1 bg-surface-subtle p-3.5 rounded-xl border border-border-default">
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="font-extrabold text-xs text-content-primary">1. Order Placed by Customer</p>
                          <p className="text-2xs text-content-secondary mt-0.5">
                            Customer: <strong>{order.customerName || order.customerId}</strong> • Payment Mode: {order.paymentMethod}
                          </p>
                        </div>
                        <span className="text-2xs font-mono font-bold text-content-muted">
                          {stages.placed ? new Date(stages.placed).toLocaleTimeString() : '—'}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Step 2: Merchant Acceptance */}
                  <div className="relative flex items-start space-x-3">
                    {/* Connecting line to Step 3 */}
                    <div className={`absolute -left-[15px] top-5 bottom-[-24px] w-0.5 transition-colors duration-300 ${
                      isStageDone(3) ? 'bg-action-primaryBg' : 'bg-border-default'
                    }`} />
                    <span className={`absolute -left-6 top-0.5 w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold shadow-sm z-10 ${
                      isStageDone(2)
                        ? 'bg-action-primaryBg text-white'
                        : order.sellerApprovalStatus === 'PENDING'
                        ? 'bg-surface-warning text-white animate-pulse'
                        : 'bg-surface-muted text-content-muted'
                    }`}>
                      {isStageDone(2) ? '✓' : order.sellerApprovalStatus === 'PENDING' ? '•' : '2'}
                    </span>
                    <div className={`flex-1 p-3.5 rounded-xl border ${
                      order.sellerApprovalStatus === 'PENDING'
                        ? 'bg-surface-warningSubtle border-border-warning'
                        : 'bg-surface-subtle border-border-default'
                    }`}>
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="font-extrabold text-xs text-content-primary">2. Merchant Acceptance</p>
                          <p className="text-2xs text-content-secondary mt-0.5">
                            {order.sellerApprovalStatus === 'PENDING'
                              ? '⏳ Awaiting Merchant Acceptance (Dispatch Held)'
                              : order.sellerApprovalStatus === 'AUTO_ACCEPTED'
                              ? '⚡ Auto-Accepted into Fulfillment (Dark Store Mode)'
                              : '✓ Accepted by Store Operator'}
                          </p>
                        </div>
                        <span className="text-2xs font-mono font-bold text-content-muted">
                          {stages.sellerAccepted ? new Date(stages.sellerAccepted).toLocaleTimeString() : (isStageDone(2) ? '✓ Completed' : (order.sellerApprovalStatus === 'PENDING' ? 'Action Needed' : '—'))}
                        </span>
                      </div>

                      {order.sellerApprovalStatus === 'PENDING' && (
                        <div className="mt-3 flex items-center gap-2 pt-2 border-t border-border-warning">
                          <button
                            onClick={() => handleDomainTransition('accept')}
                            className="px-4 py-2 bg-action-speedBg hover:bg-action-speedHover text-white rounded-xl font-bold text-xs shadow-sm flex items-center gap-1.5 cursor-pointer"
                          >
                            <CheckCircle2 className="w-3.5 h-3.5" />
                            <span>Accept Order &amp; Broadcast to Riders</span>
                          </button>
                          <button
                            onClick={() => setCancelModal(true)}
                            className="px-4 py-2 border border-border-danger hover:bg-surface-dangerSubtle text-content-danger rounded-xl font-bold text-xs cursor-pointer"
                          >
                            <span>Reject</span>
                          </button>
                        </div>
                      )}

                      {(isStageDone(2) && !rider && !isStageDone(3)) && (
                        <div className="mt-3 flex items-center gap-2 pt-2 border-t border-border-default">
                          <button
                            onClick={() => setShowRebroadcastModal(true)}
                            className="px-4 py-2 bg-amber-500 hover:bg-amber-600 text-white rounded-xl font-bold text-xs shadow-sm flex items-center gap-1.5 cursor-pointer"
                            title="Re-broadcast alert to all active riders without changing order ID"
                          >
                            <Bike className="w-3.5 h-3.5" />
                            <span>📢 Re-Broadcast to Riders</span>
                          </button>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Step 3: Rider Assigned / Accepted Job */}
                  <div className="relative flex items-start space-x-3">
                    {/* Connecting line to Step 4 */}
                    <div className={`absolute -left-[15px] top-5 bottom-[-24px] w-0.5 transition-colors duration-300 ${
                      isStageDone(4) ? 'bg-action-primaryBg' : 'bg-border-default'
                    }`} />
                    <span className={`absolute -left-6 top-0.5 w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold shadow-sm z-10 ${
                      isStageDone(3)
                        ? 'bg-action-primaryBg text-white'
                        : 'bg-surface-muted text-content-muted'
                    }`}>
                      {isStageDone(3) ? '✓' : '3'}
                    </span>
                    <div className="flex-1 bg-surface-subtle p-3.5 rounded-xl border border-border-default">
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="font-extrabold text-xs text-content-primary">3. Delivery Partner Assigned</p>
                          {rider ? (
                            <div className="mt-1 space-y-0.5 text-2xs text-content-secondary">
                              <p className="font-bold text-content-brand flex items-center gap-1">
                                <Bike className="w-3.5 h-3.5" />
                                <span>{rider.name} ({rider.phone})</span>
                              </p>
                              <p className="font-mono text-content-muted">Vehicle: {rider.vehicle} • ID: {rider.riderId}</p>
                            </div>
                          ) : (
                            <p className="text-2xs text-content-muted mt-0.5">
                              {order.sellerApprovalStatus === 'PENDING'
                                ? 'Broadcast will be sent once merchant accepts order'
                                : 'Broadcasting job offer to nearest active riders...'}
                            </p>
                          )}
                        </div>
                        <span className="text-2xs font-mono font-bold text-content-muted">
                          {stages.riderAssigned ? new Date(stages.riderAssigned).toLocaleTimeString() : (isStageDone(3) ? '✓ Assigned' : 'Pending')}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Step 4: Rider Arrived at Store */}
                  <div className="relative flex items-start space-x-3">
                    {/* Connecting line to Step 5 */}
                    <div className={`absolute -left-[15px] top-5 bottom-[-24px] w-0.5 transition-colors duration-300 ${
                      isStageDone(5) ? 'bg-action-primaryBg' : 'bg-border-default'
                    }`} />
                    <span className={`absolute -left-6 top-0.5 w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold shadow-sm z-10 ${
                      isStageDone(4)
                        ? 'bg-action-primaryBg text-white'
                        : 'bg-surface-muted text-content-muted'
                    }`}>
                      {isStageDone(4) ? '✓' : '4'}
                    </span>
                    <div className="flex-1 bg-surface-subtle p-3.5 rounded-xl border border-border-default">
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="font-extrabold text-xs text-content-primary">4. Rider Arrived at Store (Pickup Point)</p>
                          <p className="text-2xs text-content-secondary mt-0.5">
                            {isStageDone(4)
                              ? `Rider reached ${order.merchantAddress || 'Central Hub'} for package pickup`
                              : 'Rider is en route to merchant store'}
                          </p>
                        </div>
                        <span className="text-2xs font-mono font-bold text-content-muted">
                          {stages.arrivedStore ? new Date(stages.arrivedStore).toLocaleTimeString() : (isStageDone(4) ? '✓ Arrived' : '—')}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Step 5: Order Picked Up / Dispatched */}
                  <div className="relative flex items-start space-x-3">
                    {/* Connecting line to Step 6 */}
                    <div className={`absolute -left-[15px] top-5 bottom-[-24px] w-0.5 transition-colors duration-300 ${
                      isStageDone(6) ? 'bg-action-primaryBg' : 'bg-border-default'
                    }`} />
                    <span className={`absolute -left-6 top-0.5 w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold shadow-sm z-10 ${
                      isStageDone(5)
                        ? 'bg-action-primaryBg text-white'
                        : 'bg-surface-muted text-content-muted'
                    }`}>
                      {isStageDone(5) ? '✓' : '5'}
                    </span>
                    <div className="flex-1 bg-surface-subtle p-3.5 rounded-xl border border-border-default">
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="font-extrabold text-xs text-content-primary">5. Package Picked Up &amp; Dispatched</p>
                          <p className="text-2xs text-content-secondary mt-0.5">
                            {isStageDone(5)
                              ? 'Package handed over to rider • Out for delivery to customer'
                              : 'Waiting for merchant handoff'}
                          </p>
                        </div>
                        <span className="text-2xs font-mono font-bold text-content-muted">
                          {stages.pickedUp ? new Date(stages.pickedUp).toLocaleTimeString() : (isStageDone(5) ? '✓ Dispatched' : '—')}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Step 6: Rider Arrived at Customer */}
                  <div className="relative flex items-start space-x-3">
                    {/* Connecting line to Step 7 */}
                    <div className={`absolute -left-[15px] top-5 bottom-[-24px] w-0.5 transition-colors duration-300 ${
                      isStageDone(7) ? 'bg-action-primaryBg' : 'bg-border-default'
                    }`} />
                    <span className={`absolute -left-6 top-0.5 w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold shadow-sm z-10 ${
                      isStageDone(6)
                        ? 'bg-action-primaryBg text-white'
                        : 'bg-surface-muted text-content-muted'
                    }`}>
                      {isStageDone(6) ? '✓' : '6'}
                    </span>
                    <div className="flex-1 bg-surface-subtle p-3.5 rounded-xl border border-border-default">
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="font-extrabold text-xs text-content-primary">6. Rider Arrived at Customer Doorstep</p>
                          <p className="text-2xs text-content-secondary mt-0.5">
                            {isStageDone(6)
                              ? 'Rider is at customer doorstep • Verifying OTP handoff'
                              : 'Rider is travelling to delivery address'}
                          </p>
                        </div>
                        <span className="text-2xs font-mono font-bold text-content-muted">
                          {stages.arrivedCustomer ? new Date(stages.arrivedCustomer).toLocaleTimeString() : (isStageDone(6) ? '✓ At Doorstep' : '—')}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Step 7: Delivered */}
                  <div className="relative flex items-start space-x-3">
                    <span className={`absolute -left-6 top-0.5 w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold shadow-sm z-10 ${
                      isStageDone(7)
                        ? 'bg-action-primaryBg text-white'
                        : 'bg-surface-muted text-content-muted'
                    }`}>
                      {isStageDone(7) ? '✓' : '7'}
                    </span>
                    <div className="flex-1 bg-surface-subtle p-3.5 rounded-xl border border-border-default">
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="font-extrabold text-xs text-content-primary">7. Delivered &amp; Verified with OTP</p>
                          <p className="text-2xs text-content-secondary mt-0.5">
                            {isStageDone(7)
                              ? '✓ Order successfully delivered and verified with Secure OTP'
                              : 'Pending final OTP delivery handoff'}
                          </p>
                        </div>
                        <span className="text-2xs font-mono font-bold text-content-muted">
                          {stages.delivered ? new Date(stages.delivered).toLocaleTimeString() : (isStageDone(7) ? '✓ Delivered' : '—')}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* 2. CUSTOMER & DELIVERY LOCATION DETAILS */}
              <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm space-y-4">
                <div className="flex items-center space-x-2 text-content-brand border-b border-border-default pb-3">
                  <User className="w-5 h-5" />
                  <h3 className="text-base font-black text-content-primary">Customer &amp; Delivery Destination</h3>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                  <div className="bg-surface-subtle p-4 rounded-xl border border-border-default space-y-1">
                    <span className="text-content-muted font-bold uppercase text-2xs">Customer Name</span>
                    <p className="font-bold text-content-primary text-sm">{order.customerName || 'Customer'}</p>
                  </div>

                  <div className="bg-surface-subtle p-4 rounded-xl border border-border-default space-y-1">
                    <span className="text-content-muted font-bold uppercase text-2xs">Contact Phone</span>
                    <p className="font-bold text-content-primary text-sm flex items-center space-x-1">
                      <Phone className="w-3.5 h-3.5 text-content-brand" />
                      <span>{order.customerPhone || '—'}</span>
                    </p>
                  </div>

                  <div className="bg-surface-subtle p-4 rounded-xl border border-border-default space-y-1">
                    <span className="text-content-muted font-bold uppercase text-2xs">Delivery Verification OTP</span>
                    <p className="font-mono font-black text-content-brand text-base">{order.deliveryOtp || '••••••'}</p>
                  </div>
                </div>

                <div className="bg-surface-subtle p-4 rounded-xl border border-border-default text-xs">
                  <span className="text-content-muted font-bold uppercase text-2xs">Delivery Address &amp; Pinpoint</span>
                  <p className="font-semibold text-content-primary mt-1 flex items-center space-x-1.5">
                    <MapPin className="w-4 h-4 text-content-danger shrink-0" />
                    <span>{formatAddress(order.deliveryAddress)}</span>
                  </p>
                  {order.deliveryAddress?.latitude && (
                    <p className="text-2xs font-mono text-content-muted mt-1">
                      GPS Pin: {order.deliveryAddress.latitude}, {order.deliveryAddress.longitude}
                    </p>
                  )}
                </div>
              </div>

              {/* 3. ITEMIZED FINANCIAL BILL BREAKDOWN */}
              <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm space-y-4">
                <div className="flex items-center justify-between border-b border-border-default pb-3">
                  <h3 className="text-base font-black text-content-primary flex items-center gap-2">
                    <IndianRupee className="w-5 h-5 text-content-brand" />
                    <span>Itemized Purchased Items &amp; Complete Bill Breakdown</span>
                  </h3>
                  <span className="text-xs font-bold text-content-secondary">
                    {order.items?.length || 0} Items
                  </span>
                </div>

                {/* Items List */}
                <div className="space-y-2 text-xs">
                  {order.items?.map((item: any, idx: number) => (
                    <div key={idx} className="flex justify-between items-center bg-surface-subtle p-3.5 rounded-xl border border-border-default">
                      <div>
                        <p className="font-bold text-content-primary text-sm">{item.name}</p>
                        <p className="text-2xs text-content-muted">
                          SKU: <code className="font-mono text-content-brand font-bold">{item.sku}</code> • Pack: {item.packSize || 'Standard'}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="font-black text-content-primary text-sm">₹{item.total || (item.unitPrice * item.quantity)}</p>
                        <p className="text-2xs text-content-muted">{item.quantity} × ₹{item.unitPrice || item.price}</p>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Financial Summary Table */}
                <div className="bg-surface-subtle rounded-xl p-4 border border-border-default space-y-2 text-xs">
                  <div className="flex justify-between text-content-secondary">
                    <span>Items Subtotal:</span>
                    <span className="font-semibold text-content-primary">
                      ₹{order.items?.reduce((acc: number, it: any) => acc + (it.total || (it.unitPrice * it.quantity)), 0) || order.totalAmount}
                    </span>
                  </div>

                  <div className="flex justify-between text-content-secondary">
                    <span>GST / Estimated Tax (5%):</span>
                    <span className="font-semibold text-content-primary">
                      ₹{order.taxAmount || Math.round((order.totalAmount * 0.05) * 100) / 100}
                    </span>
                  </div>

                  <div className="flex justify-between text-content-secondary">
                    <span>Express Delivery &amp; Logistics:</span>
                    <span className="font-semibold text-content-primary">
                      ₹{order.deliveryFee || 25}
                    </span>
                  </div>

                  <div className="flex justify-between text-content-secondary">
                    <span>Handling &amp; Packaging:</span>
                    <span className="font-semibold text-content-primary">₹5</span>
                  </div>

                  <div className="border-t border-border-default pt-2 flex justify-between items-center text-sm font-black text-content-primary">
                    <span>Total Amount:</span>
                    <span className="text-content-brand text-xl font-mono">₹{order.totalAmount}</span>
                  </div>
                </div>
              </div>

              {/* 4. ACTIONS & CONTROLS */}
              <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm space-y-4">
                <h3 className="text-base font-black text-content-primary border-b border-border-default pb-3">
                  Merchant Action Controls
                </h3>
                <div className="flex flex-wrap items-center gap-3">
                  {(order.orderStatus === 'PLACED' || order.sellerApprovalStatus === 'PENDING') && (
                    <>
                      <button
                        onClick={() => handleDomainTransition('accept')}
                        className="px-5 py-3 bg-action-speedBg hover:bg-action-speedHover text-white rounded-xl font-bold text-xs shadow-md flex items-center gap-2 cursor-pointer"
                      >
                        <CheckCircle2 className="w-4 h-4" />
                        <span>Accept Order &amp; Dispatch Delivery Partner</span>
                      </button>
                      <button
                        onClick={() => setCancelModal(true)}
                        className="px-5 py-3 border border-border-danger hover:bg-surface-dangerSubtle text-content-danger rounded-xl font-bold text-xs cursor-pointer"
                      >
                        <span>Reject Order</span>
                      </button>
                    </>
                  )}

                  {(order.sellerApprovalStatus === 'ACCEPTED' || order.orderStatus === 'SELLER_ACCEPTED' || order.orderStatus === 'READY_FOR_PICKUP') && !order.riderId && order.orderStatus !== 'RIDER_ASSIGNED' && (
                    <button
                      onClick={() => handleDomainTransition('accept')}
                      className="px-5 py-3 bg-amber-500 hover:bg-amber-600 text-white rounded-xl font-bold text-xs shadow-md flex items-center gap-2 cursor-pointer"
                      title="Re-broadcast notification to all active riders"
                    >
                      <Bike className="w-4 h-4" />
                      <span>📢 Re-Broadcast to Riders</span>
                    </button>
                  )}

                  {order.paymentMethod === 'COD' && order.paymentStatus === 'COD_PENDING_COLLECTION' && (
                    <button
                      onClick={() => { setCodModal(true); setCollectedCashInput(String(order.totalAmount)); }}
                      className="px-5 py-3 bg-action-warningBg hover:opacity-90 text-white rounded-xl font-bold text-xs shadow-md cursor-pointer"
                    >
                      Collect COD Cash &amp; Reconcile
                    </button>
                  )}

                  {order.orderStatus !== 'DELIVERED' && order.orderStatus !== 'CANCELLED' && (
                    <button
                      onClick={() => setCancelModal(true)}
                      className="px-5 py-3 border border-border-danger hover:bg-surface-dangerSubtle text-content-danger rounded-xl font-bold text-xs cursor-pointer"
                    >
                      Cancel Order
                    </button>
                  )}
                </div>
              </div>
            </main>
          )}
        </div>

        {/* COD Modal */}
        {codModal && (
          <div className="fixed inset-0 z-50 bg-surface-inverse/80 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl border border-border-default max-w-md w-full p-6 space-y-4">
              <h3 className="text-base font-extrabold text-content-primary">Confirm Cash Collection</h3>
              <input
                type="number"
                value={collectedCashInput}
                onChange={e => setCollectedCashInput(e.target.value)}
                className="w-full p-3 bg-surface-subtle border rounded-xl font-black text-base outline-none"
              />
              <div className="flex justify-end space-x-2">
                <button onClick={() => setCodModal(false)} className="px-4 py-2 border rounded-xl text-xs font-bold cursor-pointer">Cancel</button>
                <button onClick={handleConfirmCodCollection} className="px-5 py-2 bg-action-primaryBg text-white rounded-xl font-bold text-xs cursor-pointer">Confirm COD Cash</button>
              </div>
            </div>
          </div>
        )}

        {/* Cancel Modal */}
        {cancelModal && (
          <div className="fixed inset-0 z-50 bg-surface-inverse/80 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl border border-border-default max-w-md w-full p-6 space-y-4">
              <h3 className="text-base font-extrabold text-content-danger">Cancel Order #{orderId.slice(0, 8)}</h3>
              <input
                type="text"
                placeholder="Reason for cancellation"
                value={cancelReasonInput}
                onChange={e => setCancelReasonInput(e.target.value)}
                className="w-full p-3 bg-surface-subtle border rounded-xl outline-none text-xs"
              />
              <div className="flex justify-end space-x-2">
                <button onClick={() => setCancelModal(false)} className="px-4 py-2 border rounded-xl text-xs font-bold cursor-pointer">Close</button>
                <button onClick={handleExecuteCancel} className="px-5 py-2 bg-action-dangerBg text-white rounded-xl font-bold text-xs cursor-pointer">Execute Cancel</button>
              </div>
            </div>
          </div>
        )}

        {/* Re-Broadcast Confirmation Modal */}
        {showRebroadcastModal && (
          <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl border border-border-default max-w-md w-full p-6 space-y-4">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-full bg-amber-100 flex items-center justify-center text-amber-600 font-bold">
                  <Bike className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-extrabold text-content-primary">Re-Broadcast Order</h3>
                  <p className="text-xs text-content-secondary font-mono">{orderId}</p>
                </div>
              </div>
              <p className="text-sm text-content-primary">
                Do you want to re-broadcast this order to all available fleet riders?
              </p>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  onClick={() => setShowRebroadcastModal(false)}
                  className="px-4 py-2 border border-border-default hover:bg-surface-subtle rounded-xl text-xs font-bold cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  onClick={async () => {
                    setShowRebroadcastModal(false);
                    await handleDomainTransition('accept');
                  }}
                  className="px-5 py-2 bg-amber-500 hover:bg-amber-600 text-white rounded-xl font-bold text-xs shadow-md cursor-pointer flex items-center gap-1.5"
                >
                  <Bike className="w-4 h-4" />
                  <span>Confirm Re-Broadcast</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
