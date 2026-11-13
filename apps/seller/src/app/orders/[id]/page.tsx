'use client';

import React, { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import SellerSidebar from '../../../components/SellerSidebar';
import {
  Package,
  ArrowLeft,
  CheckCircle2,
  Box,
  Truck,
  IndianRupee,
  RotateCcw,
  AlertTriangle,
  ShieldCheck,
  MapPin,
  User,
  Phone,
  Tag,
  Calendar,
  Clock,
  Check,
  Navigation,
  Bike,
  RefreshCw,
  AlertCircle,
  XCircle,
  Copy,
  Receipt,
  CheckCheck,
} from 'lucide-react';

import { formatAddress, formatDateTime } from '../../../lib/formatAddress';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import SellerAuthGuard from '../../../components/SellerAuthGuard';

export default function DedicatedSingleOrderPage({ params }: { params?: { id?: string } }) {
  const router = useRouter();
  const routeParams = useParams();
  const orderId = ((routeParams?.id as string) || params?.id || '');
  const { session, storeName } = useSellerSession();

  const [order, setOrder] = useState<any | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);
  const [copiedId, setCopiedId] = useState(false);

  // Modals
  const [codModal, setCodModal] = useState(false);
  const [collectedCashInput, setCollectedCashInput] = useState('');
  const [cancelModal, setCancelModal] = useState(false);
  const [cancelReasonInput, setCancelReasonInput] = useState('');
  const [isCancelling, setIsCancelling] = useState(false);
  const [showRebroadcastModal, setShowRebroadcastModal] = useState(false);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setStatusMessage({ text, type });
    setTimeout(() => setStatusMessage(null), 4000);
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard?.writeText(text);
    setCopiedId(true);
    showToast(`Order ID copied: ${text}`);
    setTimeout(() => setCopiedId(false), 2000);
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
      if (!orderId || orderId === 'undefined' || orderId === 'null') {
        if (showSpinner) setIsLoading(false);
        showToast('Invalid Order ID', 'error');
        return;
      }
      if (showSpinner) setIsLoading(true);
      const res = await sellerApi.get(`/api/v1/orders/${encodeURIComponent(orderId)}`);
      if (res.ok && res.data) {
        const raw = res.data;
        const id = raw.id || raw.order_id || raw.orderId || orderId;
        const phoneMatch = raw.customerId && String(raw.customerId).match(/\d{10}/);
        const fallbackPhone = phoneMatch ? `+91 ${phoneMatch[0]}` : null;
        const phone = raw.customerPhone || raw.customer_phone || raw.deliveryAddress?.contactPhone || raw.deliveryAddress?.phone || fallbackPhone;
        const status = (raw.orderStatus || raw.status || '').toUpperCase();
        const isCancelled = status === 'CANCELLED' || raw.sellerApprovalStatus === 'CANCELLED';

        const normalized = {
          ...raw,
          id,
          orderStatus: isCancelled ? 'CANCELLED' : (raw.orderStatus || raw.status),
          status: isCancelled ? 'CANCELLED' : (raw.status || raw.orderStatus),
          totalAmount: raw.totalAmount || raw.total_amount || '0.00',
          paymentMethod: raw.paymentMethod || raw.payment_method || (raw.is_cod ? 'COD' : 'ONLINE'),
          paymentStatus: isCancelled && raw.paymentStatus !== 'PAID' && raw.paymentStatus !== 'COD_COLLECTED' ? 'VOID' : (raw.paymentStatus || raw.payment_status || 'PENDING'),
          deliveryAddress: raw.deliveryAddress || raw.delivery_address,
          customerId: raw.customerId || raw.customer_id,
          customerPhone: phone,
          customerName: raw.customerName || raw.customer_name || (phoneMatch ? `Customer ${phoneMatch[0].slice(-4)}` : 'Customer'),
          createdAt: raw.createdAt || raw.created_at,
          sellerApprovalStatus: isCancelled ? 'CANCELLED' : (raw.sellerApprovalStatus || raw.seller_approval_status),
          riderId: raw.riderId || raw.rider_id,
          timelineTimestamps: raw.timelineTimestamps || {},
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

      const timer = setInterval(() => {
        fetchOrderDetails(false);
      }, 5000);

      return () => {
        clearInterval(timer);
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

  const handleExecuteCancel = async () => {
    if (!orderId) return;
    setIsCancelling(true);
    try {
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/cancel`, {
        reason: cancelReasonInput.trim() || 'Cancelled by store operator',
        cancelledBy: 'SELLER',
      });
      if (res.ok) {
        showToast('Order cancelled and reserved stock returned to store inventory!', 'success');
        setCancelModal(false);
        setCancelReasonInput('');
        await fetchOrderDetails(false);
      } else {
        showToast(res.error || 'Failed to cancel order', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Error cancelling order', 'error');
    } finally {
      setIsCancelling(false);
    }
  };

  const handleConfirmCodCollection = async () => {
    try {
      const parsed = parseFloat(collectedCashInput);
      if (isNaN(parsed) || parsed <= 0) {
        showToast('Please enter a valid cash amount', 'error');
        return;
      }
      const res = await sellerApi.post(`/api/v1/orders/${orderId}/reconcile-cod`, {
        collectedAmount: parsed,
        notes: `Cash collected and reconciled by merchant (${storeName})`,
      });
      if (res.ok) {
        showToast(`COD collection of ₹${parsed.toFixed(2)} recorded & settled!`);
        setCodModal(false);
        fetchOrderDetails(false);
      } else {
        showToast(res.error || 'Failed to reconcile COD cash', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error during reconciliation', 'error');
    }
  };

  const status = (order?.status || order?.orderStatus || '').toUpperCase();
  const isCancelled = status === 'CANCELLED' || order?.sellerApprovalStatus === 'CANCELLED';
  const isDelivered = status === 'DELIVERED';
  const isPending = !isCancelled && !isDelivered && (
    order?.sellerApprovalStatus === 'PENDING' ||
    status === 'PLACED' ||
    status === 'PENDING_APPROVAL'
  );
  const isTransit = !isCancelled && !isDelivered && (
    status === 'OUT_FOR_DELIVERY' || status === 'PICKED_UP' || status === 'RIDER_ASSIGNED'
  );

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-[#F8FAFC] text-slate-900 font-sans antialiased overflow-hidden">
        {statusMessage && (
          <div className={`fixed top-5 right-5 z-50 px-4 py-2.5 rounded-full shadow-lg font-medium text-xs flex items-center space-x-2 border animate-in fade-in slide-in-from-top-2 ${
            statusMessage.type === 'success'
              ? 'bg-slate-900 text-white border-slate-800'
              : 'bg-rose-600 text-white border-rose-700'
          }`}>
            {statusMessage.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            ) : (
              <AlertCircle className="w-4 h-4 text-rose-200 shrink-0" />
            )}
            <span>{statusMessage.text}</span>
          </div>
        )}

        <SellerSidebar
          activeTab="orders"
          ordersCount={0}
          onRefresh={() => fetchOrderDetails(true)}
          isLoading={isLoading}
        />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Top Header */}
          <header className="h-16 border-b border-slate-200/80 bg-white/90 backdrop-blur-md px-8 flex items-center justify-between shrink-0 gap-4 sticky top-0 z-10">
            <div className="flex items-center space-x-3 min-w-0">
              <button
                type="button"
                onClick={() => router.push('/orders')}
                className="p-1.5 hover:bg-slate-100 rounded-xl text-slate-600 hover:text-slate-900 transition-colors flex items-center space-x-1.5 text-xs font-medium"
              >
                <ArrowLeft className="w-4 h-4" />
                <span className="hidden sm:inline">Orders</span>
              </button>
              <span className="text-slate-300">/</span>
              <div className="flex items-center space-x-1.5 font-mono text-xs font-semibold text-slate-900 truncate">
                <span>#{orderId}</span>
                <button
                  type="button"
                  onClick={() => copyToClipboard(orderId)}
                  className="p-1 text-slate-400 hover:text-slate-700 rounded-md transition-colors"
                  title="Copy ID"
                >
                  {copiedId ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>

            <div className="flex items-center space-x-2">
              <button
                type="button"
                onClick={() => fetchOrderDetails(true)}
                className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all"
                title="Refresh order"
              >
                <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-slate-900' : ''}`} />
              </button>
            </div>
          </header>

          {isLoading ? (
            <div className="flex-1 flex items-center justify-center p-12">
              <div className="w-8 h-8 border-3 border-slate-900 border-t-transparent rounded-full animate-spin" />
            </div>
          ) : !order ? (
            <div className="flex-1 flex flex-col items-center justify-center p-12 space-y-4 max-w-md mx-auto text-center">
              <div className="w-12 h-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center">
                <AlertCircle className="w-6 h-6" />
              </div>
              <div className="space-y-1">
                <h3 className="text-base font-bold text-slate-900">Order Not Found</h3>
                <p className="text-xs text-slate-500">
                  Order <span className="font-mono font-semibold">{orderId}</span> could not be loaded from this store account.
                </p>
              </div>
              <button
                type="button"
                onClick={() => router.push('/orders')}
                className="px-4 py-2 bg-slate-900 text-white rounded-xl text-xs font-medium hover:bg-slate-800 transition-all"
              >
                Return to Orders
              </button>
            </div>
          ) : (
            <main className="p-6 md:p-8 space-y-6 max-w-6xl w-full mx-auto">
              {/* CANCELLATION STATE HERO BANNER (When Cancelled) */}
              {isCancelled && (
                <div className="bg-rose-50/80 border border-rose-200/80 rounded-2xl p-6 shadow-xs space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-3">
                      <div className="w-9 h-9 rounded-xl bg-rose-100 text-rose-700 flex items-center justify-center font-bold shrink-0">
                        <XCircle className="w-5 h-5" />
                      </div>
                      <div>
                        <h2 className="text-base font-bold text-rose-900">Order is Cancelled</h2>
                        <p className="text-xs text-rose-700/90">
                          This order was cancelled and is terminated from fulfillment dispatch.
                        </p>
                      </div>
                    </div>
                    <span className="px-3 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800 border border-rose-200">
                      ✕ VOID / CANCELLED
                    </span>
                  </div>

                  <div className="pt-3 border-t border-rose-200/60 grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs text-rose-800">
                    <div>
                      <span className="text-[11px] text-rose-600 block uppercase font-medium">Cancellation Reason</span>
                      <span className="font-semibold">{order.cancellationReason || order.cancellation?.reason || 'Store / customer requested cancellation'}</span>
                    </div>
                    <div>
                      <span className="text-[11px] text-rose-600 block uppercase font-medium">Inventory State</span>
                      <span className="font-semibold text-emerald-800 flex items-center space-x-1">
                        <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                        <span>100% Stock Restored to Shelf</span>
                      </span>
                    </div>
                    <div>
                      <span className="text-[11px] text-rose-600 block uppercase font-medium">Payment Settlement</span>
                      <span className="font-semibold">Marked VOID (₹0.00 Due)</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Order Status & Key Highlights */}
              <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-[0_1px_3px_rgba(0,0,0,0.03)] flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                      Quick-Commerce 10-Min Delivery
                    </span>
                    <span className="text-slate-300">•</span>
                    <span className="text-xs text-slate-500">
                      Placed: {formatDateTime(order.createdAt)}
                    </span>
                  </div>

                  <h1 className="text-xl sm:text-2xl font-bold text-slate-900 font-mono tracking-tight">
                    #{order.id}
                  </h1>
                </div>

                <div className="flex flex-wrap items-center gap-2.5 shrink-0">
                  {isCancelled ? (
                    <span className="px-3 py-1 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200/70 flex items-center space-x-1">
                      <XCircle className="w-3.5 h-3.5" />
                      <span>Cancelled</span>
                    </span>
                  ) : isDelivered ? (
                    <span className="px-3 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200/70 flex items-center space-x-1">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Delivered</span>
                    </span>
                  ) : isPending ? (
                    <span className="px-3 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200/70 animate-pulse flex items-center space-x-1">
                      <Clock className="w-3.5 h-3.5" />
                      <span>Awaiting Store Approval</span>
                    </span>
                  ) : isTransit ? (
                    <span className="px-3 py-1 rounded-full text-xs font-semibold bg-blue-50 text-blue-700 border border-blue-200/70 flex items-center space-x-1">
                      <Bike className="w-3.5 h-3.5" />
                      <span>Out for Delivery</span>
                    </span>
                  ) : (
                    <span className="px-3 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-800 border border-slate-200">
                      {order.status}
                    </span>
                  )}

                  <span className="px-3 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200/70">
                    {order.paymentMethod} • {order.paymentStatus}
                  </span>

                  {/* Store Hub Badge */}
                  <span className={`px-3 py-1 rounded-full text-xs font-semibold border flex items-center space-x-1.5 ${
                    order.storeName?.includes('Kathua') || order.storeId === 'store_hub_02' || order.storeId === 'STORE_02' || order.store_id === 'store_hub_02'
                      ? 'bg-purple-50 text-purple-700 border-purple-200/80 shadow-xs'
                      : 'bg-indigo-50 text-indigo-700 border-indigo-200/80 shadow-xs'
                  }`}>
                    <MapPin className="w-3.5 h-3.5 shrink-0" />
                    <span>{order.storeName?.includes('Kathua') || order.storeId === 'store_hub_02' || order.storeId === 'STORE_02' || order.store_id === 'store_hub_02' ? 'Kathua Hub' : 'Rewari Hub'}</span>
                  </span>
                </div>
              </div>

              {/* 2-Column Grid: Left Column (Items & Bill) / Right Column (Customer & Timeline) */}
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                {/* Left Column: Items Breakdown & Bill (7 cols) */}
                <div className="lg:col-span-7 space-y-6">
                  {/* Items Card */}
                  <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                      <div className="flex items-center space-x-2">
                        <Package className="w-4 h-4 text-slate-600" />
                        <h2 className="text-sm font-semibold text-slate-900">Ordered Items ({order.items?.length || 0})</h2>
                      </div>
                      <span className="text-xs font-medium text-slate-500">Store: {order.storeId || storeName}</span>
                    </div>

                    <div className="divide-y divide-slate-100">
                      {order.items?.map((item: any, idx: number) => {
                        const unitPrice = Number(item.unitPrice || item.price || 0);
                        const quantity = Number(item.quantity || 1);
                        const total = Number(item.total || (unitPrice * quantity));

                        return (
                          <div key={idx} className="py-3 flex items-center justify-between text-xs">
                            <div className="space-y-0.5">
                              <p className="font-medium text-slate-900">{item.name || item.title || 'Product Item'}</p>
                              <p className="text-[11px] text-slate-400 font-mono">
                                SKU: {item.sku || 'SKU-00'} • Pack: {item.packSize || '1 Unit'}
                              </p>
                            </div>
                            <div className="text-right">
                              <p className="font-semibold text-slate-900">₹{total.toFixed(2)}</p>
                              <p className="text-[11px] text-slate-400">
                                {quantity} × ₹{unitPrice.toFixed(2)}
                              </p>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {/* Financial Bill Card */}
                  <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-4">
                    <div className="flex items-center space-x-2 border-b border-slate-100 pb-3">
                      <Receipt className="w-4 h-4 text-slate-600" />
                      <h2 className="text-sm font-semibold text-slate-900">Bill Breakdown</h2>
                    </div>

                    {(() => {
                      const bill = order.billSummary || order.bill_summary;
                      const calculatedSubtotal = bill?.itemsSubtotal != null
                        ? Number(bill.itemsSubtotal)
                        : (order.items?.reduce((acc: number, it: any) => acc + (Number(it.total) || (Number(it.unitPrice || it.price || 0) * Number(it.quantity || 1))), 0) || Number(order.totalAmount || 0));
                      const grandTotal = bill?.grandTotal != null
                        ? Number(bill.grandTotal)
                        : Number(order.totalAmount || calculatedSubtotal);
                      const deliveryFee = bill?.deliveryFee != null ? Number(bill.deliveryFee) : Number(order.deliveryFee || 0);
                      const discount = bill?.discountAmount != null ? Number(bill.discountAmount) : 0;
                      const tax = bill?.taxAmount != null ? Number(bill.taxAmount) : Number(order.taxAmount || 0);
                      const handling = bill?.handlingFee != null ? Number(bill.handlingFee) : Number(order.packagingFee || 0);

                      return (
                        <div className="space-y-2.5 text-xs text-slate-600">
                          <div className="flex justify-between">
                            <span>Items Subtotal</span>
                            <span className="font-medium text-slate-900">₹{calculatedSubtotal.toFixed(2)}</span>
                          </div>

                          {discount > 0 && (
                            <div className="flex justify-between text-emerald-600">
                              <span>Promotion Discount</span>
                              <span className="font-medium">-₹{discount.toFixed(2)}</span>
                            </div>
                          )}

                          <div className="flex justify-between">
                            <span>Delivery Partner Fee</span>
                            <span className="font-medium text-slate-900">
                              {deliveryFee === 0 ? 'FREE' : `₹${deliveryFee.toFixed(2)}`}
                            </span>
                          </div>

                          {tax > 0 && (
                            <div className="flex justify-between">
                              <span>Taxes &amp; GST</span>
                              <span className="font-medium text-slate-900">₹{tax.toFixed(2)}</span>
                            </div>
                          )}

                          {handling > 0 && (
                            <div className="flex justify-between">
                              <span>Packaging &amp; Handling</span>
                              <span className="font-medium text-slate-900">₹{handling.toFixed(2)}</span>
                            </div>
                          )}

                          <div className="pt-3 border-t border-slate-100 flex justify-between items-baseline">
                            <span className="font-semibold text-slate-900 text-sm">Grand Total</span>
                            <span className="font-bold text-slate-900 text-lg">
                              ₹{grandTotal.toFixed(2)}
                            </span>
                          </div>
                        </div>
                      );
                    })()}
                  </div>

                  {/* Actions & Controls Box */}
                  <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-4">
                    <h2 className="text-sm font-semibold text-slate-900 border-b border-slate-100 pb-3">
                      Order Management Actions
                    </h2>

                    {isCancelled ? (
                      <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-500 text-center">
                        This order is cancelled. No further fulfillment actions are allowed.
                      </div>
                    ) : (
                      <div className="flex flex-wrap items-center gap-3">
                        {isPending && (
                          <>
                            <button
                              type="button"
                              onClick={() => handleDomainTransition('accept')}
                              className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-medium shadow-xs flex items-center space-x-1.5 transition-all active:scale-95"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                              <span>Accept &amp; Broadcast to Riders</span>
                            </button>
                            <button
                              type="button"
                              onClick={() => setCancelModal(true)}
                              className="px-4 py-2 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200/60 rounded-xl text-xs font-medium transition-all"
                            >
                              <span>Reject / Cancel</span>
                            </button>
                          </>
                        )}

                        {(order.status === 'SELLER_ACCEPTED' || order.status === 'PACKED') && (
                          <button
                            type="button"
                            onClick={() => handleDomainTransition('ready-pickup')}
                            className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-medium shadow-xs flex items-center space-x-1.5 transition-all active:scale-95"
                          >
                            <Package className="w-3.5 h-3.5" />
                            <span>Mark Ready for Pickup</span>
                          </button>
                        )}

                        {(order.status === 'READY_FOR_PICKUP' || order.status === 'SELLER_ACCEPTED') && !order.riderId && (
                          <button
                            type="button"
                            onClick={() => setShowRebroadcastModal(true)}
                            className="px-4 py-2 bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200/80 rounded-xl text-xs font-medium flex items-center space-x-1.5 transition-all"
                          >
                            <Bike className="w-3.5 h-3.5 text-amber-700" />
                            <span>Re-Broadcast to Riders</span>
                          </button>
                        )}

                        {(order.paymentMethod === 'COD' || order.is_cod) &&
                          (order.paymentStatus === 'COD_PENDING' || order.paymentStatus === 'PENDING') &&
                          !isCancelled && (
                            <button
                              type="button"
                              onClick={() => {
                                setCollectedCashInput(String(order.totalAmount));
                                setCodModal(true);
                              }}
                              className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-medium shadow-xs flex items-center space-x-1.5 transition-all"
                            >
                              <IndianRupee className="w-3.5 h-3.5" />
                              <span>Reconcile Cash</span>
                            </button>
                          )}

                        {!isDelivered && !isCancelled && !isPending && (
                          <button
                            type="button"
                            onClick={() => setCancelModal(true)}
                            className="px-4 py-2 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200/60 rounded-xl text-xs font-medium transition-all"
                          >
                            <span>Cancel Order</span>
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>

                {/* Right Column: Customer Info & Timeline (5 cols) */}
                <div className="lg:col-span-5 space-y-6">
                  {/* Customer & Address Card */}
                  <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-4">
                    <div className="flex items-center space-x-2 border-b border-slate-100 pb-3">
                      <User className="w-4 h-4 text-slate-600" />
                      <h2 className="text-sm font-semibold text-slate-900">Customer &amp; Delivery</h2>
                    </div>

                    <div className="space-y-3 text-xs">
                      <div className="flex justify-between items-start">
                        <div>
                          <p className="text-[11px] text-slate-400 font-medium">Customer</p>
                          <p className="font-semibold text-slate-900">{order.customerName}</p>
                          <p className="text-[11px] text-slate-400 font-mono">{order.customerId}</p>
                        </div>
                        {order.deliveryOtp && !isCancelled && (
                          <div className="text-right">
                            <p className="text-[11px] text-slate-400 font-medium">Delivery OTP</p>
                            <p className="font-mono font-bold text-sm text-slate-900 bg-slate-100 px-2.5 py-0.5 rounded-lg border border-slate-200/70 inline-block">
                              {order.deliveryOtp}
                            </p>
                          </div>
                        )}
                      </div>

                      {order.customerPhone && (
                        <div>
                          <p className="text-[11px] text-slate-400 font-medium">Phone</p>
                          <a
                            href={`tel:${order.customerPhone}`}
                            className="inline-flex items-center space-x-1.5 font-mono text-slate-800 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 px-2.5 py-1 rounded-lg transition-colors mt-0.5 font-medium"
                          >
                            <Phone className="w-3.5 h-3.5 text-slate-400" />
                            <span>{order.customerPhone}</span>
                          </a>
                        </div>
                      )}

                      <div>
                        <p className="text-[11px] text-slate-400 font-medium">Destination Address</p>
                        <p className="font-medium text-slate-700 leading-relaxed mt-0.5 flex items-start space-x-1.5">
                          <MapPin className="w-3.5 h-3.5 text-rose-500 shrink-0 mt-0.5" />
                          <span>{formatAddress(order.deliveryAddress)}</span>
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Lifecycle Milestones Timeline */}
                  <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                      <div className="flex items-center space-x-2">
                        <Clock className="w-4 h-4 text-slate-600" />
                        <h2 className="text-sm font-semibold text-slate-900">Order Milestones</h2>
                      </div>
                      <span className="text-[11px] font-medium text-slate-400">
                        {isCancelled ? 'Terminated' : isDelivered ? 'Complete' : 'In Progress'}
                      </span>
                    </div>

                    {isCancelled ? (
                      /* Cancelled Order Timeline (2 Steps Only) */
                      <div className="space-y-4 text-xs">
                        <div className="flex items-start space-x-3">
                          <div className="w-6 h-6 rounded-full bg-slate-900 text-white flex items-center justify-center font-bold text-[10px] shrink-0">
                            ✓
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900">1. Order Placed</p>
                            <p className="text-[11px] text-slate-400">{formatDateTime(order.createdAt)}</p>
                          </div>
                        </div>

                        <div className="flex items-start space-x-3">
                          <div className="w-6 h-6 rounded-full bg-rose-600 text-white flex items-center justify-center font-bold text-[10px] shrink-0">
                            ✕
                          </div>
                          <div className="space-y-1">
                            <p className="font-semibold text-rose-900">2. Order Cancelled &amp; Voided</p>
                            <p className="text-[11px] text-rose-700/80">
                              Order fulfillment stopped. Reserved stock automatically restocked to inventory.
                            </p>
                          </div>
                        </div>
                      </div>
                    ) : (
                      /* Active / Delivered Order Timeline */
                      <div className="space-y-3.5 text-xs">
                        {/* 1. Placed */}
                        <div className="flex items-start space-x-3">
                          <div className="w-6 h-6 rounded-full bg-slate-900 text-white flex items-center justify-center font-bold text-[10px] shrink-0">
                            ✓
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900">1. Order Placed</p>
                            <p className="text-[11px] text-slate-500 font-mono">
                              {formatDateTime(order.timelineTimestamps?.placedAt || order.createdAt)}
                            </p>
                          </div>
                        </div>

                        {/* 2. Store Approval */}
                        <div className="flex items-start space-x-3">
                          <div className={`w-6 h-6 rounded-full flex items-center justify-center font-bold text-[10px] shrink-0 ${
                            isPending ? 'bg-amber-500 text-white animate-pulse' : 'bg-slate-900 text-white'
                          }`}>
                            {isPending ? '•' : '✓'}
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900">2. Store Approval</p>
                            <p className="text-[11px] text-slate-500">
                              {isPending ? (
                                <span className="text-amber-700 font-medium">⏳ Awaiting Store Acceptance</span>
                              ) : (
                                <span>
                                  ✓ Accepted by Store
                                  {order.timelineTimestamps?.approvedAt && (
                                    <span className="font-mono text-slate-400 block sm:inline sm:ml-1.5">
                                      {formatDateTime(order.timelineTimestamps.approvedAt)}
                                    </span>
                                  )}
                                </span>
                              )}
                            </p>
                          </div>
                        </div>

                        {/* 3. Rider Assigned */}
                        <div className="flex items-start space-x-3">
                          <div className={`w-6 h-6 rounded-full flex items-center justify-center font-bold text-[10px] shrink-0 ${
                            order.riderId ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-400'
                          }`}>
                            {order.riderId ? '✓' : '3'}
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900">3. Rider Assigned</p>
                            <p className="text-[11px] text-slate-500">
                              {order.riderId ? (
                                <span>
                                  Assigned to <strong className="text-slate-800">{order.riderName || order.riderId}</strong>
                                  {order.timelineTimestamps?.assignedAt && (
                                    <span className="font-mono text-slate-400 block sm:inline sm:ml-1.5">
                                      {formatDateTime(order.timelineTimestamps.assignedAt)}
                                    </span>
                                  )}
                                </span>
                              ) : (
                                <span className="text-slate-400">Broadcasting offer to fleet riders…</span>
                              )}
                            </p>
                          </div>
                        </div>

                        {/* 4. Delivered */}
                        <div className="flex items-start space-x-3">
                          <div className={`w-6 h-6 rounded-full flex items-center justify-center font-bold text-[10px] shrink-0 ${
                            isDelivered ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-400'
                          }`}>
                            {isDelivered ? '✓' : '4'}
                          </div>
                          <div>
                            <p className="font-semibold text-slate-900">4. Delivered to Doorstep</p>
                            <p className="text-[11px] text-slate-500">
                              {isDelivered ? (
                                <span className="text-emerald-700 font-medium">
                                  ✓ Verified with OTP
                                  {order.timelineTimestamps?.deliveredAt && (
                                    <span className="font-mono text-slate-500 block sm:inline sm:ml-1.5">
                                      {formatDateTime(order.timelineTimestamps.deliveredAt)}
                                    </span>
                                  )}
                                </span>
                              ) : (
                                <span className="text-slate-400">Pending rider delivery</span>
                              )}
                            </p>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </main>
          )}
        </div>

        {/* Cancel Confirmation Modal */}
        {cancelModal && (
          <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-xl border border-slate-200/80 max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-xl bg-rose-50 border border-rose-200/60 flex items-center justify-center text-rose-600 font-bold shrink-0">
                  <XCircle className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Cancel Order #{orderId.slice(-8).toUpperCase()}</h3>
                  <p className="text-xs text-slate-400 font-mono truncate max-w-[260px]">{orderId}</p>
                </div>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Cancelling this order revokes delivery broadcasts and automatically restores reserved stock back to store inventory.
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
                  onClick={() => setCancelModal(false)}
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
        {showRebroadcastModal && (
          <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-xl border border-slate-200/80 max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-xl bg-amber-50 border border-amber-200/60 flex items-center justify-center text-amber-600 font-bold shrink-0">
                  <Bike className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Re-Broadcast Delivery Offer</h3>
                  <p className="text-xs text-slate-400 font-mono truncate max-w-[260px]">{orderId}</p>
                </div>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Dispatch an updated high-priority push notification to all available riders in your fulfillment radius.
              </p>
              <div className="flex justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowRebroadcastModal(false)}
                  className="px-4 py-2 border border-slate-200 hover:bg-slate-50 rounded-xl text-xs font-medium text-slate-700 transition-all"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    setShowRebroadcastModal(false);
                    await handleDomainTransition('accept');
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

        {/* COD Cash Reconciliation Modal */}
        {codModal && (
          <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-xl border border-slate-200/80 max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-50 border border-emerald-200/60 flex items-center justify-center text-emerald-600 font-bold shrink-0">
                  <IndianRupee className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Confirm Cash Reconciliation</h3>
                  <p className="text-xs text-slate-400 font-mono truncate max-w-[260px]">{orderId}</p>
                </div>
              </div>
              <p className="text-xs text-slate-600 leading-relaxed">
                Enter the exact cash amount collected from the delivery rider to reconcile this COD transaction.
              </p>
              <div className="space-y-1">
                <label className="text-[11px] font-medium text-slate-500">Collected Amount (₹)</label>
                <input
                  type="number"
                  step="0.01"
                  value={collectedCashInput}
                  onChange={(e) => setCollectedCashInput(e.target.value)}
                  className="w-full p-3 bg-slate-50 border border-slate-200/80 rounded-xl outline-none text-base font-bold text-slate-900 focus:bg-white focus:border-slate-400 transition-all"
                />
              </div>
              <div className="flex justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setCodModal(false)}
                  className="px-4 py-2 border border-slate-200 hover:bg-slate-50 rounded-xl text-xs font-medium text-slate-700 transition-all"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleConfirmCodCollection}
                  className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl font-medium text-xs shadow-xs transition-all flex items-center space-x-1.5"
                >
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                  <span>Confirm Cash</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
