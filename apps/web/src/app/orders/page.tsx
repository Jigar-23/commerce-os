'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  Package,
  Clock,
  CheckCircle2,
  MapPin,
  FileText,
  Download,
  ChevronRight,
  ShieldCheck,
  RefreshCw,
  Pill,
  X,
  Zap,
  Truck,
  ArrowLeft,
  AlertCircle,
  AlertTriangle,
  Phone,
  RotateCcw,
  ShoppingBag,
  ExternalLink,
} from 'lucide-react';
import {
  fetchOrders,
  fetchOrderById,
  OrderResponse,
  cancelOrder,
  fetchCustomerAddresses,
  SavedAddress,
} from '@/lib/api-client';
import { useCart } from '@/lib/cart-store';
import {
  CommerceNavbar,
  CommerceStatusBadge,
  CommerceOrderStatusTimeline,
  CommerceButton,
  CommerceEmptyState,
  CommerceErrorState,
} from '@commerce-os/ui';

interface DisplayOrder {
  orderId: string;
  date: string;
  status: string;
  items: { name: string; packSize?: string; unitPrice: number; quantity: number }[];
  deliveryAddress: string;
  otp: string;
  deliverySlaMins?: number;
  paymentMethod: string;
  paymentStatus?: string;
  totalAmount?: number;
  riderName?: string;
  riderPhone?: string;
  cancellation?: OrderResponse['cancellation'];
  cancellationRequest?: OrderResponse['cancellationRequest'];
  trackingCheckpoints?: OrderResponse['trackingCheckpoints'];
}

export default function CustomerOrderHistoryPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<DisplayOrder[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [apiError, setApiError] = useState<string | null>(null);
  const [cancelInProgress, setCancelInProgress] = useState<string | null>(null);
  const [cancelMsg, setCancelMsg] = useState<Record<string, string>>({});
  const [currentAddress, setCurrentAddress] = useState<SavedAddress | null>(null);
  const [activeOrderId, setActiveOrderId] = useState<string | null>(null);

  const addToCart = useCart((s) => s.addItem);
  const cartLines = useCart((s) => s.lines);
  const cartCount = cartLines.reduce((sum, l) => sum + l.quantity, 0);
  const cartSubtotal = cartLines.reduce((sum, l) => sum + (l.unitPrice || 0) * l.quantity, 0);

  const loadAddresses = async () => {
    try {
      const addrs = await fetchCustomerAddresses();
      if (addrs && addrs.length > 0) {
        const def = addrs.find((a) => a.isDefault) || addrs[0];
        setCurrentAddress(def);
      } else {
        setCurrentAddress(null);
      }
    } catch (e) {
      setCurrentAddress(null);
    }
  };

  const loadOrders = async () => {
    setIsLoading(true);
    setApiError(null);
    try {
      const rawOrders = await fetchOrders();
      const mapped: DisplayOrder[] = (rawOrders || []).map((o) => {
        let addrStr = 'Doorstep Delivery';
        if (typeof o.deliveryAddress === 'string') {
          addrStr = o.deliveryAddress;
        } else if (o.deliveryAddress && typeof o.deliveryAddress === 'object') {
          addrStr = (o.deliveryAddress as any).formattedAddress || (o.deliveryAddress as any).addressLine || 'Doorstep Delivery';
        }

        return {
          orderId: o.orderId || o.id,
          date: o.createdAt ? new Date(o.createdAt).toLocaleString() : 'Recent order',
          status: o.status || o.orderStatus || 'PLACED',
          items: o.items || [],
          deliveryAddress: addrStr,
          otp: o.deliveryOtp || '', // Server authoritative: no hardcoded fake OTP
          deliverySlaMins: o.deliverySlaMins || o.slaMins,
          paymentMethod: o.paymentMethod || 'UPI_INSTANT',
          paymentStatus: o.paymentStatus || 'PAID',
          totalAmount: o.totalAmount || 0,
          riderName: o.riderName || 'Rider Assigned on Dispatch',
          riderPhone: o.riderPhone,
          cancellation: o.cancellation,
          cancellationRequest: o.cancellationRequest,
          trackingCheckpoints: o.trackingCheckpoints,
        };
      });

      setOrders(mapped);
      if (mapped.length > 0 && !activeOrderId) {
        const firstActive = mapped.find((o) => !['DELIVERED', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(o.status));
        setActiveOrderId(firstActive ? firstActive.orderId : mapped[0].orderId);
      }
    } catch (err: any) {
      setApiError(err?.message || 'Failed to fetch order history from authoritative order service.');
      setOrders([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadAddresses();
    loadOrders();
  }, []);

  const handleCancelOrder = async (orderId: string) => {
    setCancelInProgress(orderId);
    try {
      const res = await cancelOrder(orderId, 'Cancelled by customer from tracking console');
      if (res && (res.status === 'CANCELLED' || res.cancellationRequest)) {
        setCancelMsg((prev) => ({
          ...prev,
          [orderId]: res.cancellation?.restorationNote || 'Order cancelled. Reserved inventory released immediately.',
        }));
        await loadOrders();
      } else {
        setCancelMsg((prev) => ({
          ...prev,
          [orderId]: res?.error || 'Cancellation could not be completed.',
        }));
      }
    } catch (err: any) {
      setCancelMsg((prev) => ({
        ...prev,
        [orderId]: err.message || 'Network error while requesting cancellation.',
      }));
    } finally {
      setCancelInProgress(null);
    }
  };

  const handleReorder = (order: DisplayOrder) => {
    order.items.forEach((item) => {
      const sku = (item as any).sku || (item as any).productId || item.name;
      addToCart({
        sku,
        productId: (item as any).productId || sku,
        name: item.name,
        brand: (item as any).brand || 'HEALTHCARE',
        packSize: item.packSize || '1 Unit',
        unitPrice: item.unitPrice,
        mrp: Math.round(item.unitPrice * 1.25),
        rxRequired: Boolean((item as any).rxRequired),
        coldChain: Boolean((item as any).coldChain),
        image: (item as any).image || '',
        expressDeliverySlaMins: order.deliverySlaMins || 10,
      });
    });
    router.push('/checkout');
  };

  const activeOrders = orders.filter((o) => !['DELIVERED', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(o.status));
  const pastOrders = orders.filter((o) => ['DELIVERED', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(o.status));
  const selectedOrder = orders.find((o) => o.orderId === activeOrderId) || orders[0];

  const locationDisplay = currentAddress
    ? `${currentAddress.contactName || currentAddress.tag || 'Home'} • ${currentAddress.addressLine}`
    : 'Orders';

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex flex-col font-sans antialiased pb-24 selection:bg-surface-brandSubtle selection:text-content-brand">
      {/* 1. TOP NAVBAR */}
      <CommerceNavbar
        locationAddress={locationDisplay}
        cartItemCount={cartCount}
        cartTotalAmount={cartSubtotal}
        ordersHref="/orders"
        profileHref="/profile"
      />

      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-6 w-full space-y-8">
        {/* HEADER */}
        <div className="flex items-center justify-between">
          <div>
            <span className="text-2xs font-extrabold text-content-brand uppercase tracking-wider bg-surface-brandSubtle px-2.5 py-1 rounded-full border border-border-brandSubtle">
              Live Order Console
            </span>
            <h1 className="text-xl sm:text-2xl font-black text-content-primary tracking-tight mt-1">
              Your Orders &amp; Real-Time Tracking
            </h1>
          </div>

          <button
            onClick={loadOrders}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-surface-card hover:bg-surface-subtle border border-border-default text-xs font-bold transition-all cursor-pointer"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            <span className="hidden sm:inline">Refresh</span>
          </button>
        </div>

        {/* LOADING & ERROR STATES */}
        {isLoading && orders.length === 0 ? (
          <div className="space-y-4 animate-pulse">
            <div className="h-64 bg-surface-card rounded-3xl border border-border-subtle" />
            <div className="h-32 bg-surface-card rounded-3xl border border-border-subtle" />
          </div>
        ) : apiError ? (
          <CommerceErrorState
            title="Unable to Load Orders"
            message={apiError}
            onRetry={loadOrders}
          />
        ) : orders.length === 0 ? (
          <CommerceEmptyState
            title="No orders placed yet"
            description="When you order medicines and essentials, you can track live dispatch and rider progress here."
            actionText="Start Shopping"
            onAction={() => router.push('/')}
          />
        ) : (
          <div className="space-y-8">
            {/* 2. ACTIVE ORDERS HIGHLIGHT (IF ANY) */}
            {activeOrders.length > 0 && (
              <section className="space-y-4">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm sm:text-base font-extrabold text-content-primary flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-action-primaryBg animate-ping" />
                    <span>Active Express Deliveries ({activeOrders.length})</span>
                  </h2>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                  {/* ACTIVE ORDER MAIN CARD (8 COLS) */}
                  {selectedOrder && (
                    <div className="lg:col-span-8 bg-surface-card rounded-3xl p-6 sm:p-8 border border-border-brand/40 shadow-card space-y-6">
                      {/* HEADER STATUS STRIP */}
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-border-subtle">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-black text-content-primary font-mono">{selectedOrder.orderId}</span>
                            <span className="px-2.5 py-0.5 rounded-full text-2xs font-extrabold bg-surface-brandSubtle text-content-brand border border-border-brandSubtle">
                              ⚡ {selectedOrder.status.replace('_', ' ')}
                            </span>
                          </div>
                          <p className="text-2xs text-content-muted mt-1">{selectedOrder.date}</p>
                        </div>

                        <div className="text-right">
                          <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider">Estimated Dispatch SLA</span>
                          <p className="text-sm sm:text-base font-black text-content-brand flex items-center gap-1 sm:justify-end">
                            <Clock className="h-4 w-4" />
                            <span>{selectedOrder.deliverySlaMins ? `${selectedOrder.deliverySlaMins} Mins` : 'Instant Dispatch'}</span>
                          </p>
                        </div>
                      </div>

                      {/* DELIVERY HANDOVER OTP CALLOUT (AUTHORITATIVE) */}
                      {selectedOrder.otp ? (
                        <div className="p-4 rounded-2xl bg-surface-brandSubtle border border-border-brandSubtle flex items-center justify-between">
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-xl bg-action-primaryBg text-action-primaryText flex items-center justify-center font-black">
                              🔒
                            </div>
                            <div>
                              <h4 className="text-xs font-extrabold text-content-primary">Delivery Handover OTP</h4>
                              <p className="text-2xs text-content-secondary">Share code with rider at your doorstep</p>
                            </div>
                          </div>
                          <span className="text-xl font-black tracking-widest text-content-brand font-mono bg-white px-3.5 py-1.5 rounded-xl border border-border-brand/40 shadow-xs">
                            {selectedOrder.otp}
                          </span>
                        </div>
                      ) : (
                        <div className="p-3.5 rounded-2xl bg-surface-subtle border border-border-subtle text-xs text-content-secondary flex items-center gap-2">
                          <ShieldCheck className="h-4 w-4 text-content-brand" />
                          <span>OTP security verification generated upon rider dispatch</span>
                        </div>
                      )}

                      {/* LIVE STATUS TIMELINE */}
                      <div className="pt-2">
                        <CommerceOrderStatusTimeline status={selectedOrder.status} />
                      </div>

                      {/* RIDER & DESTINATION DETAILS */}
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-4 border-t border-border-subtle">
                        {/* RIDER CARD */}
                        <div className="p-4 rounded-2xl bg-surface-subtle border border-border-subtle flex items-center justify-between">
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-xl bg-action-primaryBg text-action-primaryText flex items-center justify-center font-black">
                              <Truck className="h-5 w-5" />
                            </div>
                            <div>
                              <h4 className="text-xs font-bold text-content-primary">{selectedOrder.riderName}</h4>
                              <p className="text-2xs text-content-brand font-extrabold">⚡ Dispatch Status Active</p>
                            </div>
                          </div>

                          {selectedOrder.riderPhone && (
                            <a
                              href={`tel:${selectedOrder.riderPhone}`}
                              className="p-2.5 rounded-xl bg-surface-card hover:bg-surface-muted text-content-brand border border-border-default transition-colors shadow-xs"
                              title="Call Rider"
                            >
                              <Phone className="h-4 w-4" />
                            </a>
                          )}
                        </div>

                        {/* DESTINATION CARD */}
                        <div className="p-4 rounded-2xl bg-surface-subtle border border-border-subtle flex items-start gap-3">
                          <MapPin className="h-5 w-5 text-content-brand shrink-0 mt-0.5" />
                          <div className="min-w-0">
                            <h4 className="text-xs font-bold text-content-primary">Delivery Destination</h4>
                            <p className="text-2xs text-content-secondary line-clamp-2 mt-0.5">{selectedOrder.deliveryAddress}</p>
                          </div>
                        </div>
                      </div>

                      {/* CANCELLATION NOTICE OR ACTION */}
                      {cancelMsg[selectedOrder.orderId] ? (
                        <div className="p-3.5 rounded-xl bg-surface-dangerSubtle border border-border-danger text-xs font-bold text-content-danger">
                          {cancelMsg[selectedOrder.orderId]}
                        </div>
                      ) : (
                        ['PLACED', 'SELLER_ACCEPTED'].includes(selectedOrder.status) && (
                          <div className="pt-2 flex justify-end">
                            <button
                              onClick={() => handleCancelOrder(selectedOrder.orderId)}
                              disabled={cancelInProgress === selectedOrder.orderId}
                              className="text-xs font-bold text-content-danger hover:underline disabled:opacity-50 cursor-pointer"
                            >
                              {cancelInProgress === selectedOrder.orderId ? 'Cancelling...' : 'Cancel Order (Instant Inventory Release)'}
                            </button>
                          </div>
                        )
                      )}
                    </div>
                  )}

                  {/* ACTIVE ORDERS SELECTOR (4 COLS) */}
                  <div className="lg:col-span-4 space-y-3">
                    <span className="text-xs font-extrabold text-content-muted uppercase tracking-wider block">
                      Active Orders List
                    </span>

                    {activeOrders.map((ord) => (
                      <div
                        key={ord.orderId}
                        onClick={() => setActiveOrderId(ord.orderId)}
                        className={`p-4 rounded-2xl border transition-all cursor-pointer ${
                          selectedOrder?.orderId === ord.orderId
                            ? 'bg-surface-brandSubtle border-border-brand shadow-subtle ring-2 ring-border-brand/20'
                            : 'bg-surface-card border-border-default hover:bg-surface-subtle shadow-card'
                        }`}
                      >
                        <div className="flex items-center justify-between mb-1.5">
                          <span className="text-xs font-black text-content-primary font-mono">{ord.orderId}</span>
                          <span className="text-2xs font-extrabold text-content-brand uppercase">{ord.status}</span>
                        </div>
                        <p className="text-2xs text-content-secondary font-medium">
                          {ord.items.length} items • ₹{(ord.totalAmount || 0).toFixed(0)}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              </section>
            )}

            {/* 3. PAST ORDERS HISTORY */}
            <section className="space-y-4">
              <h2 className="text-sm sm:text-base font-extrabold text-content-primary">
                Past Deliveries &amp; Invoices
              </h2>

              {pastOrders.length === 0 && activeOrders.length > 0 ? (
                <p className="text-xs text-content-secondary">No completed deliveries yet.</p>
              ) : (
                <div className="space-y-4">
                  {pastOrders.map((ord) => (
                    <div
                      key={ord.orderId}
                      className="bg-surface-card rounded-3xl p-6 border border-border-default shadow-card space-y-4"
                    >
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-border-subtle">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-black text-content-primary font-mono">{ord.orderId}</span>
                            <span
                              className={`px-2 py-0.5 rounded-full text-2xs font-extrabold ${
                                ord.status === 'DELIVERED' || ord.status === 'COMPLETED'
                                  ? 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'
                                  : 'bg-surface-dangerSubtle text-content-danger border border-border-danger'
                              }`}
                            >
                              {ord.status}
                            </span>
                          </div>
                          <p className="text-2xs text-content-muted mt-0.5">{ord.date}</p>
                        </div>

                        <div className="flex items-center gap-3">
                          <span className="text-sm font-black text-content-primary">
                            ₹{(ord.totalAmount || 0).toFixed(0)}
                          </span>

                          <button
                            onClick={() => handleReorder(ord)}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-action-primaryBg hover:bg-action-primaryHover text-action-primaryText text-xs font-bold shadow-subtle transition-all active:scale-95 cursor-pointer"
                          >
                            <RotateCcw className="h-3.5 w-3.5" />
                            <span>Re-order</span>
                          </button>
                        </div>
                      </div>

                      {/* ITEM DETAILS */}
                      <div className="text-xs space-y-1.5">
                        {ord.items.map((item, idx) => (
                          <div key={idx} className="flex items-center justify-between text-content-secondary">
                            <span>
                              {item.quantity}x {item.name} {item.packSize ? `(${item.packSize})` : ''}
                            </span>
                            <span className="font-bold text-content-primary">₹{(item.unitPrice * item.quantity).toFixed(0)}</span>
                          </div>
                        ))}
                      </div>

                      <div className="text-2xs text-content-muted pt-2 border-t border-border-subtle flex items-center justify-between">
                        <span>Delivered to: {ord.deliveryAddress}</span>
                        <span>Paid via {ord.paymentMethod.replace('_', ' ')}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </main>
    </div>
  );
}
