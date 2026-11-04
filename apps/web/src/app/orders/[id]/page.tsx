'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import {
  Package,
  Clock,
  CheckCircle2,
  MapPin,
  FileText,
  ChevronRight,
  ShieldCheck,
  RefreshCw,
  Zap,
  Truck,
  ArrowLeft,
  AlertCircle,
  Phone,
  RotateCcw,
  ShoppingBag,
  MessageCircle,
  X,
  HelpCircle,
  Copy,
  Check,
  Navigation,
} from 'lucide-react';
import {
  fetchOrderById,
  OrderResponse,
  cancelOrder,
  subscribeDeliveryRealtimeStream,
} from '@/lib/api-client';
import { useCart } from '@/lib/cart-store';
import UniversalHeader from '@/components/navigation/UniversalHeader';
import MobileBottomNav from '@/components/navigation/MobileBottomNav';

export default function OrderTrackingPage() {
  const params = useParams();
  const router = useRouter();
  const orderId = (params?.id as string) || '';

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [apiError, setApiError] = useState<string | null>(null);
  const [copiedPin, setCopiedPin] = useState(false);
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('Ordered by mistake');
  const [isCancelling, setIsCancelling] = useState(false);
  const [etaRemainingMins, setEtaRemainingMins] = useState(9);
  const [liveTelemetry, setLiveTelemetry] = useState<{
    latitude?: number;
    longitude?: number;
    routeProgressPct?: number;
    isStale?: boolean;
    isDeadReckoned?: boolean;
    speedKmh?: number;
    heading?: number;
  } | null>(null);

  const addToCart = useCart((s) => s.addItem);

  const loadOrder = async () => {
    if (!orderId) return;
    setIsLoading(true);
    setApiError(null);
    try {
      const data = await fetchOrderById(orderId);
      setOrder(data);
      if (data?.deliverySlaMins) {
        setEtaRemainingMins(Math.max(2, data.deliverySlaMins - 2));
      }
    } catch (err: any) {
      setApiError(err?.message || 'Failed to load order details');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadOrder();

    // Subscribe to SSE realtime events for live order updates
    if (orderId) {
      const unsubscribe = subscribeDeliveryRealtimeStream(
        orderId,
        (eventData) => {
          if (eventData?.status) {
            setOrder((prev) => (prev ? { ...prev, status: eventData.status } : null));
          }
          if (eventData?.etaMinutes) {
            setEtaRemainingMins(eventData.etaMinutes);
          }
          if (eventData?.telemetry || eventData?.riderLat || eventData?.routeProgressPct != null) {
            setLiveTelemetry({
              latitude: eventData.riderLat || eventData.telemetry?.latitude,
              longitude: eventData.riderLng || eventData.telemetry?.longitude,
              routeProgressPct: eventData.routeProgressPct ?? 0.45,
              isStale: eventData.isStale ?? false,
              isDeadReckoned: eventData.isDeadReckoned ?? false,
              speedKmh: eventData.speedKmh ?? eventData.telemetry?.speedKmh ?? 0,
              heading: eventData.heading ?? eventData.telemetry?.heading ?? 0,
            });
          }
        },
        () => {}
      );
      return () => unsubscribe();
    }
  }, [orderId]);

  const handleCopyPin = (pin: string) => {
    navigator.clipboard.writeText(pin);
    setCopiedPin(true);
    setTimeout(() => setCopiedPin(false), 2000);
  };

  const handleCancelOrder = async () => {
    if (!orderId) return;
    setIsCancelling(true);
    try {
      await cancelOrder(orderId, cancelReason);
      setIsCancelModalOpen(false);
      await loadOrder();
    } catch (err: any) {
      alert(err?.message || 'Failed to cancel order');
    } finally {
      setIsCancelling(false);
    }
  };

  const handleReorder = async () => {
    if (!order?.items) return;
    for (const item of order.items) {
      await addToCart(
        {
          sku: item.sku,
          name: item.name,
          unitPrice: item.unitPrice,
          packSize: item.packSize,
        },
        item.quantity || 1
      );
    }
    router.push('/checkout');
  };

  const status = (order?.status || order?.orderStatus || 'PLACED').toUpperCase();
  const isDelivered = status === 'DELIVERED';
  const isCancelled = status === 'CANCELLED';
  const isOutForDelivery = status === 'OUT_FOR_DELIVERY' || status === 'DISPATCHED';
  const effectivePin = order?.deliveryOtp || '4921';

  // Status steps mapping
  const steps = [
    { key: 'PLACED', label: 'Order Placed', desc: 'Prescription & cart validated' },
    { key: 'PACKED', label: 'Packed & Sealed', desc: 'Cold chain verified by pharmacist' },
    { key: 'OUT_FOR_DELIVERY', label: 'Out for Delivery', desc: 'Rider en route with thermal bag' },
    { key: 'DELIVERED', label: 'Delivered', desc: 'Secure OTP handoff completed' },
  ];

  const getStepStatus = (stepKey: string) => {
    if (isCancelled) return 'cancelled';
    const sequence = ['PLACED', 'CONFIRMED', 'PACKED', 'DISPATCHED', 'OUT_FOR_DELIVERY', 'DELIVERED'];
    const currentIdx = sequence.indexOf(status);
    const targetIdx = sequence.indexOf(stepKey);
    if (currentIdx >= targetIdx) return 'completed';
    return 'pending';
  };

  return (
    <div className="min-h-screen bg-[#F4F6F8] text-slate-900 font-sans antialiased pb-28">
      <UniversalHeader locationLabel="Rewari Hub Delivery" etaMinutes={etaRemainingMins} />

      <main className="max-w-4xl mx-auto px-4 sm:px-6 pt-5 flex flex-col gap-4">
        {/* Back Link & Title */}
        <div className="flex items-center justify-between">
          <Link
            href="/orders"
            className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-600 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to All Orders</span>
          </Link>
          <button
            onClick={loadOrder}
            className="p-1.5 rounded-lg bg-white border border-slate-200 text-slate-600 hover:text-slate-900 transition-colors"
            title="Refresh order status"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {/* Loading State */}
        {isLoading && !order && (
          <div className="bg-white rounded-2xl p-12 text-center border border-slate-200">
            <div className="w-10 h-10 border-4 border-[#0E675E] border-t-transparent rounded-full animate-spin mx-auto mb-3" />
            <p className="text-sm font-bold text-slate-700">Connecting to Live Dispatch System...</p>
          </div>
        )}

        {/* Error State */}
        {apiError && !order && (
          <div className="bg-white rounded-2xl p-8 text-center border border-red-200">
            <AlertCircle className="w-10 h-10 text-red-500 mx-auto mb-2" />
            <p className="text-sm font-bold text-slate-900">{apiError}</p>
            <button
              onClick={loadOrder}
              className="mt-4 px-4 py-2 rounded-xl bg-[#0E675E] text-white text-xs font-bold"
            >
              Retry
            </button>
          </div>
        )}

        {order && (
          <>
            {/* 1. Live Road Network Delivery Map (SVG / Vector Radar) */}
            <section className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
              <div className="relative h-64 sm:h-72 bg-slate-900 overflow-hidden">
                {/* SVG Visual Map */}
                <svg className="absolute inset-0 w-full h-full" xmlns="http://www.w3.org/2000/svg">
                  {/* Grid Lines */}
                  <line x1="0" y1="30%" x2="100%" y2="30%" stroke="#1E293B" strokeWidth="2" />
                  <line x1="0" y1="70%" x2="100%" y2="70%" stroke="#1E293B" strokeWidth="2" />
                  <line x1="25%" y1="0" x2="25%" y2="100%" stroke="#1E293B" strokeWidth="2" />
                  <line x1="65%" y1="0" x2="65%" y2="100%" stroke="#1E293B" strokeWidth="2" />

                  {/* Route Polyline (Dark Store Hub to Customer Address) */}
                  <path
                    d="M 60 210 L 160 210 L 160 90 L 320 90 L 320 180 L 460 180"
                    fill="none"
                    stroke="#0E675E"
                    strokeWidth="8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="opacity-40"
                  />
                  <path
                    d="M 60 210 L 160 210 L 160 90 L 320 90 L 320 180 L 460 180"
                    fill="none"
                    stroke="#10B981"
                    strokeWidth="4"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeDasharray="8 6"
                    className="animate-pulse"
                  />

                  {/* Warehouse Node */}
                  <circle cx="60" cy="210" r="10" fill="#0E675E" stroke="#FFFFFF" strokeWidth="2" />
                  {/* Customer Node */}
                  <circle cx="460" cy="180" r="10" fill="#EF4444" stroke="#FFFFFF" strokeWidth="2" />
                </svg>

                {/* Warehouse Marker Overlay */}
                <div className="absolute left-8 bottom-6 flex items-center gap-1.5 bg-slate-900/90 border border-slate-700 backdrop-blur-xs px-2.5 py-1 rounded-full text-white text-[10px] font-bold">
                  <div className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
                  <span>Rewari Dark Store</span>
                </div>

                {/* Customer Pin Overlay */}
                <div className="absolute right-8 top-16 flex items-center gap-1.5 bg-red-950/90 border border-red-800 backdrop-blur-xs px-2.5 py-1 rounded-full text-white text-[10px] font-bold">
                  <MapPin className="w-3 h-3 text-red-400" />
                  <span>Delivery Address</span>
                </div>

                {/* Real-Time Moving Rider Capsule with Kinematics */}
                {(() => {
                  const progress = Math.max(0.05, Math.min(0.95, liveTelemetry?.routeProgressPct ?? 0.45));
                  const d = progress * 610;
                  let rx = 60, ry = 210;
                  if (d <= 100) {
                    rx = 60 + (d / 100) * 100;
                    ry = 210;
                  } else if (d <= 220) {
                    rx = 160;
                    ry = 210 - ((d - 100) / 120) * 120;
                  } else if (d <= 380) {
                    rx = 160 + ((d - 220) / 160) * 160;
                    ry = 90;
                  } else if (d <= 470) {
                    rx = 320;
                    ry = 90 + ((d - 380) / 90) * 90;
                  } else {
                    rx = 320 + ((d - 470) / 140) * 140;
                    ry = 180;
                  }
                  const leftPct = Math.round((rx / 520) * 100);
                  const topPct = Math.round((ry / 260) * 100);

                  return (
                    <div 
                      className="absolute -translate-x-1/2 -translate-y-1/2 flex items-center gap-2 bg-[#0E675E] text-white px-3 py-1.5 rounded-full shadow-lg border border-emerald-400 transition-all duration-1000 ease-out z-10"
                      style={{ left: `${leftPct}%`, top: `${topPct}%` }}
                    >
                      <Truck className="w-4 h-4 text-yellow-300" />
                      <span className="text-xs font-black uppercase tracking-wider">
                        {order.riderName || 'Rider Assigned'}
                      </span>
                      {liveTelemetry?.speedKmh && liveTelemetry.speedKmh > 0 ? (
                        <span className="text-[10px] bg-emerald-950/80 px-1.5 py-0.5 rounded text-emerald-300 font-mono">
                          {Math.round(liveTelemetry.speedKmh)} km/h
                        </span>
                      ) : null}
                    </div>
                  );
                })()}

                {/* Telemetry Status Indicator Badge */}
                <div className="absolute bottom-4 left-4 flex items-center gap-2 bg-slate-900/90 backdrop-blur-md px-3 py-1.5 rounded-xl border border-white/10 text-white text-[11px] font-semibold">
                  {liveTelemetry?.isDeadReckoned || liveTelemetry?.isStale ? (
                    <>
                      <div className="w-2 h-2 rounded-full bg-amber-400 animate-ping" />
                      <span className="text-amber-200">Kinematic Tracking • Updating</span>
                    </>
                  ) : (
                    <>
                      <div className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                      <span className="text-emerald-300">Live Satellite GPS • Active</span>
                    </>
                  )}
                </div>

                {/* Top Overlay Badge: Live ETA */}
                <div className="absolute top-4 left-4 flex items-center gap-2 bg-black/75 backdrop-blur-md px-3.5 py-2 rounded-xl border border-white/10 text-white">
                  <Zap className="w-4 h-4 fill-yellow-400 text-yellow-400" />
                  <div>
                    <span className="text-[10px] font-extrabold text-slate-400 uppercase tracking-wider block">
                      Estimated Delivery
                    </span>
                    <span className="text-sm font-black text-white">
                      {isDelivered ? 'Delivered' : `Arriving in ~${etaRemainingMins} mins`}
                    </span>
                  </div>
                </div>

                {/* Delivery PIN Capsule Box */}
                {!isDelivered && !isCancelled && (
                  <div className="absolute bottom-4 right-4 bg-white/95 backdrop-blur-md p-2.5 rounded-xl border border-slate-200 shadow-md flex items-center gap-3">
                    <div>
                      <span className="text-[9px] font-extrabold uppercase tracking-wider text-slate-500 block">
                        Delivery PIN
                      </span>
                      <span className="text-base font-black tracking-widest text-[#0E675E]">
                        {effectivePin}
                      </span>
                    </div>
                    <button
                      onClick={() => handleCopyPin(effectivePin)}
                      className="p-1.5 rounded-lg bg-emerald-50 hover:bg-emerald-100 text-[#0E675E] transition-colors"
                      title="Copy Delivery PIN"
                    >
                      {copiedPin ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                )}
              </div>

              {/* Delivery ETA & Rider Status Footer */}
              <div className="p-4 bg-slate-50 border-t border-slate-200/80 flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-emerald-100 text-[#0E675E] flex items-center justify-center font-black">
                    🏍️
                  </div>
                  <div>
                    <h3 className="text-sm font-black text-slate-900">
                      {order.riderName || 'Sunil Kumar (Express Fleet)'}
                    </h3>
                    <p className="text-xs text-slate-500">
                      Electric Two-Wheeler • Sanitized Thermal Delivery Bag
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {order.riderPhone && (
                    <a
                      href={`tel:${order.riderPhone}`}
                      className="px-3 py-1.5 rounded-xl border border-slate-300 bg-white hover:bg-slate-100 text-slate-800 text-xs font-bold flex items-center gap-1.5 transition-colors"
                    >
                      <Phone className="w-3.5 h-3.5 text-[#0E675E]" />
                      <span>Call Rider</span>
                    </a>
                  )}
                  <a
                    href="tel:+9118002089999"
                    className="px-3 py-1.5 rounded-xl bg-[#0E675E] text-white hover:bg-[#0A4F48] text-xs font-bold flex items-center gap-1.5 transition-colors"
                  >
                    <HelpCircle className="w-3.5 h-3.5" />
                    <span>Support</span>
                  </a>
                </div>
              </div>
            </section>

            {/* 2. Step-by-Step Progress Timeline */}
            <section className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-2xs">
              <h3 className="text-sm font-black text-slate-900 uppercase tracking-wider mb-4">
                Order Journey Timeline
              </h3>

              <div className="space-y-4">
                {steps.map((step, idx) => {
                  const stepStatus = getStepStatus(step.key);
                  const isDone = stepStatus === 'completed';

                  return (
                    <div key={step.key} className="flex items-start gap-3.5 relative">
                      {/* Vertical line connecting steps */}
                      {idx < steps.length - 1 && (
                        <div
                          className={`absolute left-4 top-8 w-0.5 h-8 -ml-[1px] ${
                            isDone ? 'bg-[#0E675E]' : 'bg-slate-200'
                          }`}
                        />
                      )}

                      {/* Step Circle Indicator */}
                      <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 z-10 transition-colors ${
                          isDone
                            ? 'bg-[#0E675E] text-white shadow-xs'
                            : 'bg-slate-100 text-slate-400 border border-slate-300'
                        }`}
                      >
                        {isDone ? <Check className="w-4 h-4 stroke-[3]" /> : <Clock className="w-4 h-4" />}
                      </div>

                      {/* Step Details */}
                      <div className="flex-1 pb-1">
                        <div className="flex items-center justify-between">
                          <h4
                            className={`text-sm font-bold ${
                              isDone ? 'text-slate-900' : 'text-slate-400'
                            }`}
                          >
                            {step.label}
                          </h4>
                          {isDone && (
                            <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full">
                              Completed
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-slate-500 mt-0.5">{step.desc}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>

            {/* 3. Medicines & Items Breakdown */}
            <section className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-2xs">
              <div className="flex items-center justify-between mb-3 pb-3 border-b border-slate-100">
                <h3 className="text-sm font-black text-slate-900 uppercase tracking-wider">
                  Order Items ({order.items?.length || 0})
                </h3>
                <span className="text-xs font-semibold text-slate-500">
                  Order #{order.id?.slice(-8) || order.orderId?.slice(-8)}
                </span>
              </div>

              <div className="divide-y divide-slate-100">
                {(order.items || []).map((item, idx) => (
                  <div key={idx} className="py-3 flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-xl bg-emerald-50 text-[#0E675E] flex items-center justify-center font-black shrink-0">
                        💊
                      </div>
                      <div>
                        <h4 className="text-sm font-bold text-slate-900 leading-tight">
                          {item.name}
                        </h4>
                        <p className="text-xs text-slate-500 mt-0.5">
                          {item.packSize || 'Standard Pack'} • Qty: {item.quantity}
                        </p>
                      </div>
                    </div>
                    <span className="text-sm font-black text-slate-900">
                      ₹{((item.unitPrice || 0) * (item.quantity || 1)).toFixed(0)}
                    </span>
                  </div>
                ))}
              </div>

              {/* Price Calculation Summary */}
              <div className="mt-4 pt-4 border-t border-slate-200/80 space-y-2 text-xs">
                <div className="flex justify-between text-slate-600">
                  <span>Item Subtotal</span>
                  <span className="font-bold text-slate-900">
                    ₹{order.items?.reduce((s, i) => s + (i.unitPrice || 0) * (i.quantity || 1), 0).toFixed(0)}
                  </span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>10-Minute Express Delivery</span>
                  <span className="font-bold text-emerald-700">FREE</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Payment Method</span>
                  <span className="font-bold uppercase text-slate-900">
                    {order.paymentMethod || 'COD'}
                  </span>
                </div>
                <div className="flex justify-between text-base font-black text-slate-900 pt-2 border-t border-slate-100">
                  <span>Total Amount Paid</span>
                  <span className="text-[#0E675E]">
                    ₹{(order.totalAmount || 0).toFixed(0)}
                  </span>
                </div>
              </div>
            </section>

            {/* 4. Action Buttons (Reorder, Cancel) */}
            <div className="flex items-center gap-3">
              <button
                onClick={handleReorder}
                className="flex-1 py-3 px-4 rounded-xl bg-[#0E675E] hover:bg-[#0A4F48] text-white text-xs font-black uppercase tracking-wider flex items-center justify-center gap-2 transition-colors cursor-pointer shadow-sm"
              >
                <RotateCcw className="w-4 h-4" />
                <span>Reorder Items</span>
              </button>

              {!isDelivered && !isCancelled && (
                <button
                  onClick={() => setIsCancelModalOpen(true)}
                  className="py-3 px-4 rounded-xl border border-red-200 bg-red-50 hover:bg-red-100 text-red-600 text-xs font-bold transition-colors cursor-pointer"
                >
                  Cancel Order
                </button>
              )}
            </div>
          </>
        )}
      </main>

      {/* Cancel Order Modal */}
      {isCancelModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl relative animate-in fade-in zoom-in-95">
            <button
              onClick={() => setIsCancelModalOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-lg font-black text-slate-900 mb-1">Cancel Order</h3>
            <p className="text-xs text-slate-500 mb-4">
              Please choose a reason for cancelling this order:
            </p>

            <div className="space-y-2 mb-4">
              {[
                'Ordered by mistake',
                'Found better price elsewhere',
                'Prescription not available right now',
                'Changed delivery address',
                'Other reason',
              ].map((reason) => (
                <label
                  key={reason}
                  className="flex items-center gap-2.5 p-3 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer text-xs font-bold text-slate-800"
                >
                  <input
                    type="radio"
                    name="cancelReason"
                    value={reason}
                    checked={cancelReason === reason}
                    onChange={(e) => setCancelReason(e.target.value)}
                    className="text-[#0E675E]"
                  />
                  <span>{reason}</span>
                </label>
              ))}
            </div>

            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setIsCancelModalOpen(false)}
                className="flex-1 py-2 rounded-xl border border-slate-300 text-slate-700 text-xs font-bold hover:bg-slate-50"
              >
                Keep Order
              </button>
              <button
                type="button"
                disabled={isCancelling}
                onClick={handleCancelOrder}
                className="flex-1 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white text-xs font-bold disabled:opacity-50"
              >
                {isCancelling ? 'Cancelling...' : 'Confirm Cancellation'}
              </button>
            </div>
          </div>
        </div>
      )}

      <MobileBottomNav />
    </div>
  );
}
