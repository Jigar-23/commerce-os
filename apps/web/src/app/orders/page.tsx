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
  ChevronRight,
  ShieldCheck,
  RefreshCw,
  Pill,
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
  Navigation,
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
import MobileBottomNav from '@/components/navigation/MobileBottomNav';

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
}

export default function CustomerOrderHistoryPage() {
  const router = useRouter();
  const [orders, setOrders] = useState<DisplayOrder[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [apiError, setApiError] = useState<string | null>(null);
  const [cancelInProgress, setCancelInProgress] = useState<string | null>(null);
  const [cancelMsg, setCancelMsg] = useState<Record<string, string>>({});
  const [isHelpOpen, setIsHelpOpen] = useState(false);
  const [helpOrderId, setHelpOrderId] = useState<string | null>(null);

  const addToCart = useCart((s) => s.addItem);
  const cartLines = useCart((s) => s.lines);
  const cartCount = cartLines.reduce((sum, l) => sum + l.quantity, 0);

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
          date: o.createdAt
            ? new Date(o.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
            : 'Recent order',
          status: (o.status || o.orderStatus || 'PLACED').toUpperCase(),
          items: o.items || [],
          deliveryAddress: addrStr,
          otp: o.deliveryOtp || '',
          deliverySlaMins: o.deliverySlaMins || o.slaMins || 10,
          paymentMethod: o.paymentMethod || 'COD',
          paymentStatus: o.paymentStatus || 'PAID',
          totalAmount: o.totalAmount || 0,
          riderName: o.riderName || 'Rider Assigned on Dispatch',
          riderPhone: o.riderPhone,
          cancellation: o.cancellation,
        };
      });

      setOrders(mapped);
    } catch (err: any) {
      setApiError(err?.message || 'Failed to fetch order history from authoritative order service.');
      setOrders([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadOrders();
  }, []);

  const handleCancelOrder = async (orderId: string) => {
    setCancelInProgress(orderId);
    try {
      const res = await cancelOrder(orderId, 'Cancelled by customer');
      if (res && (res.status === 'CANCELLED' || res.cancellationRequest)) {
        setCancelMsg((prev) => ({
          ...prev,
          [orderId]: 'Order cancelled. Any payment will be refunded to source.',
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

  const handleReorder = async (order: DisplayOrder) => {
    for (const item of order.items) {
      const sku = (item as any).sku || (item as any).productId || item.name;
      await addToCart({
        sku,
        productId: (item as any).productId || sku,
        name: item.name,
        brand: (item as any).brand || 'CERTIFIED PHARMA',
        packSize: item.packSize || 'Strip of 10 tablets',
        unitPrice: item.unitPrice,
        mrp: Math.round(item.unitPrice * 1.25),
        rxRequired: Boolean((item as any).rxRequired),
        coldChain: Boolean((item as any).coldChain),
        image: (item as any).image || '',
        expressDeliverySlaMins: order.deliverySlaMins || 10,
      }, item.quantity);
    }
    router.push('/checkout');
  };

  const isOrderActive = (status: string) =>
    !['DELIVERED', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(status);

  const [filterTab, setFilterTab] = useState<'ALL' | 'ACTIVE' | 'DELIVERED'>('ALL');

  const filteredOrders = orders.filter((o) => {
    if (filterTab === 'ACTIVE') return isOrderActive(o.status);
    if (filterTab === 'DELIVERED') return o.status === 'DELIVERED' || o.status === 'COMPLETED';
    return true;
  });

  return (
    <div className="min-h-screen bg-[#F8FAFC] text-slate-900 flex flex-col font-sans antialiased pb-28">
      {/* 1. DEEP EMERALD CANOPY HEADER (MATCHING ANDROID) */}
      <header className="bg-gradient-to-b from-[#094640] to-[#0E675E] text-white px-4 py-4 sticky top-0 z-30 shadow-md">
        <div className="max-w-3xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              className="w-9 h-9 rounded-full bg-white/15 flex items-center justify-center text-white hover:bg-white/25 transition-colors"
            >
              <ArrowLeft className="h-5 w-5" />
            </Link>
            <div>
              <h1 className="text-lg sm:text-xl font-extrabold tracking-tight">Your Orders</h1>
              <p className="text-[11.5px] text-white/85">Live order tracking &amp; 1-tap reorders</p>
            </div>
          </div>

          <button
            onClick={loadOrders}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/15 hover:bg-white/25 text-white text-xs font-bold transition-all"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            <span className="hidden sm:inline">Refresh</span>
          </button>
        </div>

        {/* Filter Pills matching Android OrderHistoryScreen */}
        <div className="max-w-3xl mx-auto flex items-center gap-2 mt-3 pt-2 border-t border-white/15">
          {[
            { id: 'ALL', label: 'All Orders' },
            { id: 'ACTIVE', label: 'Active Deliveries' },
            { id: 'DELIVERED', label: 'Delivered' },
          ].map((tab) => {
            const isSelected = filterTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setFilterTab(tab.id as any)}
                className={`px-3 py-1 rounded-full text-xs font-bold transition-all cursor-pointer ${
                  isSelected
                    ? 'bg-white text-[#0E675E] shadow-xs'
                    : 'bg-white/15 text-white/85 hover:bg-white/25'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-4 w-full space-y-4">
        {/* LOADING & ERROR STATES */}
        {isLoading && orders.length === 0 ? (
          <div className="space-y-4 pt-2">
            {[1, 2, 3].map((n) => (
              <div key={n} className="bg-white rounded-2xl p-4 border border-slate-200 animate-pulse space-y-3">
                <div className="h-6 w-1/3 bg-slate-100 rounded" />
                <div className="h-12 bg-slate-100 rounded" />
                <div className="h-8 w-1/4 bg-slate-100 rounded" />
              </div>
            ))}
          </div>
        ) : apiError ? (
          <div className="p-6 bg-white rounded-2xl border border-red-200 text-center space-y-3 my-6">
            <AlertCircle className="h-10 w-10 text-red-500 mx-auto" />
            <h3 className="text-sm font-extrabold text-slate-900">Failed to Load Orders</h3>
            <p className="text-xs text-slate-500">{apiError}</p>
            <button
              onClick={loadOrders}
              className="px-4 py-2 bg-[#0E675E] text-white rounded-xl text-xs font-bold"
            >
              Try Again
            </button>
          </div>
        ) : filteredOrders.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-8 text-center space-y-4 my-8 shadow-xs">
            <div className="w-16 h-16 rounded-full bg-emerald-50 border border-emerald-100 text-[#0E675E] flex items-center justify-center mx-auto">
              <ShoppingBag className="h-8 w-8" />
            </div>
            <div>
              <h3 className="text-base font-extrabold text-slate-900">
                {filterTab === 'ACTIVE'
                  ? 'No Active Deliveries Right Now'
                  : filterTab === 'DELIVERED'
                  ? 'No Delivered Orders Found'
                  : 'No Orders Placed Yet'}
              </h3>
              <p className="text-xs text-slate-500 max-w-xs mx-auto mt-1 leading-relaxed">
                When you order medicines and health essentials, you can track real-time dispatch and reorder in 1-tap here.
              </p>
            </div>
            <Link
              href="/"
              className="inline-flex items-center gap-1.5 px-6 py-2.5 bg-[#0E675E] hover:bg-[#094640] text-white rounded-xl text-xs font-bold shadow-sm transition-all"
            >
              <span>Start Shopping</span>
              <ChevronRight className="h-4 w-4" />
            </Link>
          </div>
        ) : (
          <div className="space-y-4 pt-1">
            {filteredOrders.map((ord) => {
              const isActive = isOrderActive(ord.status);
              const isDelivered = ord.status === 'DELIVERED' || ord.status === 'COMPLETED';
              const isCancelled = ord.status === 'CANCELLED' || ord.status === 'FAILED';

              // Tata 1mg Benchmark Ribbon Styling
              const ribbonBg = isDelivered
                ? 'bg-[#F0F7FF] text-[#1D4ED8] border-blue-100'
                : isCancelled
                ? 'bg-[#FFF5F5] text-[#DC2626] border-red-100'
                : 'bg-[#F0FDF4] text-[#15803D] border-emerald-100';

              const ribbonIcon = isDelivered ? (
                <CheckCircle2 className="h-4 w-4 stroke-[2.5]" />
              ) : isCancelled ? (
                <AlertCircle className="h-4 w-4 stroke-[2.5]" />
              ) : (
                <Zap className="h-4 w-4 fill-emerald-600" />
              );

              const ribbonText = isDelivered
                ? `Delivered on ${ord.date}`
                : isCancelled
                ? 'Order Cancelled'
                : `⚡ In Transit • Arriving in ~${ord.deliverySlaMins || 10} Mins`;

              return (
                <div
                  key={ord.orderId}
                  className="bg-white rounded-2xl border border-slate-200/90 shadow-xs overflow-hidden transition-all hover:border-slate-300"
                >
                  {/* TATA 1MG TOP STATUS RIBBON */}
                  <div className={`px-4 py-2.5 flex items-center justify-between border-b ${ribbonBg}`}>
                    <div className="flex items-center gap-2 font-extrabold text-xs">
                      {ribbonIcon}
                      <span>{ribbonText}</span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      <span className="font-mono text-[11px] font-bold text-slate-500">
                        #{ord.orderId.slice(-8).toUpperCase()}
                      </span>
                      <ChevronRight className="h-4 w-4 text-slate-400" />
                    </div>
                  </div>

                  {/* CARD BODY: MEDICINE DETAILS */}
                  <div className="p-4 space-y-3">
                    {/* Item Thumbnails & Summary */}
                    <div className="space-y-2">
                      {ord.items.map((item, idx) => (
                        <div key={idx} className="flex items-center justify-between gap-3 text-xs">
                          <div className="flex items-center gap-2.5 min-w-0">
                            <div className="w-9 h-9 rounded-lg bg-slate-50 border border-slate-200/80 p-1 flex items-center justify-center shrink-0">
                              <Pill className="h-4 w-4 text-[#0E675E]" />
                            </div>
                            <div className="min-w-0">
                              <h4 className="font-bold text-slate-900 truncate">{item.name}</h4>
                              <p className="text-[11px] text-slate-500">
                                {item.quantity} × {item.packSize || 'Strip of 10 tablets'}
                              </p>
                            </div>
                          </div>

                          <span className="font-extrabold text-slate-900 shrink-0">
                            ₹{item.unitPrice * item.quantity}
                          </span>
                        </div>
                      ))}
                    </div>

                    {/* ACTIVE ORDER: OTP CALLOUT */}
                    {isActive && ord.otp ? (
                      <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="text-base">🔑</span>
                          <div>
                            <span className="text-[11px] font-bold text-slate-900 block">Delivery PIN / OTP</span>
                            <span className="text-[10px] text-slate-600">Share with rider at doorstep</span>
                          </div>
                        </div>
                        <span className="text-lg font-black tracking-widest text-[#0E675E] font-mono bg-white px-2.5 py-1 rounded-lg border border-emerald-200">
                          {ord.otp}
                        </span>
                      </div>
                    ) : null}

                    {/* SUMMARY & ACTION ROW */}
                    <div className="pt-3 border-t border-slate-100 flex items-center justify-between">
                      <div>
                        <span className="text-[11px] text-slate-500 font-bold block">Total Amount</span>
                        <span className="text-sm sm:text-base font-black text-slate-900">
                          ₹{ord.totalAmount?.toFixed(0) || '0'}
                        </span>
                      </div>

                      {/* CLEAN CENTERED ACTIONS */}
                      <div className="flex items-center gap-2">
                        {/* View & Track Live Map Button */}
                        <Link
                          href={`/orders/${ord.orderId}`}
                          className="px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-extrabold text-xs shadow-2xs flex items-center gap-1.5 transition-all active:scale-95 cursor-pointer"
                        >
                          <Navigation className="h-3.5 w-3.5 text-emerald-400 rotate-45" />
                          <span>Track Map</span>
                        </Link>

                        {isDelivered && (
                          <button
                            onClick={() => handleReorder(ord)}
                            className="px-4 py-2 rounded-xl bg-[#0E675E] hover:bg-[#094640] text-white font-extrabold text-xs shadow-2xs flex items-center gap-1.5 transition-all active:scale-95 cursor-pointer"
                          >
                            <RotateCcw className="h-3.5 w-3.5" />
                            <span>Re-order</span>
                          </button>
                        )}

                        {isActive && (
                          <div className="flex items-center gap-2">
                            <span className="px-3 py-1.5 rounded-xl bg-emerald-50 text-[#0E675E] border border-emerald-200 text-xs font-extrabold flex items-center gap-1">
                              <Truck className="h-3.5 w-3.5" />
                              <span>{ord.riderName || 'Rider Assigned'}</span>
                            </span>
                            <button
                              onClick={() => {
                                setHelpOrderId(ord.orderId);
                                setIsHelpOpen(true);
                              }}
                              className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-xs"
                            >
                              Need Help?
                            </button>
                          </div>
                        )}

                        {isCancelled && (
                          <button
                            onClick={() => {
                              setHelpOrderId(ord.orderId);
                              setIsHelpOpen(true);
                            }}
                            className="px-3.5 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-xs flex items-center gap-1"
                          >
                            <HelpCircle className="h-3.5 w-3.5 text-slate-500" />
                            <span>Need Help?</span>
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </main>

      <MobileBottomNav />

      {/* NEED HELP BOTTOM SHEET / MODAL */}
      {isHelpOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="bg-white rounded-t-3xl sm:rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl border border-slate-200 animate-slide-up">
            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-full bg-emerald-50 text-[#0E675E] flex items-center justify-center">
                  <HelpCircle className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-sm font-extrabold text-slate-900">Pharmacy Helpdesk</h3>
                  <p className="text-[11px] text-slate-500">24x7 Assistance for Order #{helpOrderId?.slice(-8).toUpperCase()}</p>
                </div>
              </div>
              <button
                onClick={() => setIsHelpOpen(false)}
                className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center text-slate-500 hover:bg-slate-200"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <p className="text-xs text-slate-600 leading-relaxed">
              Have questions regarding your medicine dispatch, dosage instructions, or refund status? Contact our licensed pharmacy team immediately.
            </p>

            <div className="space-y-2.5 pt-2">
              <a
                href={`https://wa.me/919817916180?text=Hello%20CommerceOS%20Pharmacy%2C%20I%20need%20help%20with%20my%20order%20%23${helpOrderId || ''}`}
                target="_blank"
                rel="noopener noreferrer"
                className="w-full py-3 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-xs flex items-center justify-center gap-2 shadow-sm transition-all"
              >
                <MessageCircle className="h-4 w-4" />
                <span>Chat with Pharmacist on WhatsApp</span>
              </a>

              <a
                href="tel:+919817916180"
                className="w-full py-3 px-4 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 font-extrabold text-xs flex items-center justify-center gap-2 transition-all"
              >
                <Phone className="h-4 w-4 text-[#0E675E]" />
                <span>Call 24x7 Hotline (+91 98179 16180)</span>
              </a>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
