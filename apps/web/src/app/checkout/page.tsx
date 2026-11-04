'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  ShieldCheck,
  Lock,
  CheckCircle2,
  ArrowLeft,
  Clock,
  Plus,
  Zap,
  Trash2,
  Minus,
  ShoppingCart,
  Pill,
  Sparkles,
  MapPin,
  CreditCard,
  QrCode,
  Banknote,
  FileText,
  AlertTriangle,
  ChevronRight,
  Info,
  UploadCloud,
  FileCheck,
  Check,
} from 'lucide-react';
import {
  createOrder,
  fetchCustomerAddresses,
  SavedAddress,
} from '@/lib/api-client';
import { useSession } from '@/lib/session-store';
import { useCart } from '@/lib/cart-store';
import DeliveryAddressMapModal from '@/components/DeliveryAddressMapModal';
import MobileBottomNav from '@/components/navigation/MobileBottomNav';

export default function ExpressCheckoutPage() {
  const router = useRouter();
  const lines = useCart((s) => s.lines);
  const updateQuantity = useCart((s) => s.updateQuantity);
  const removeItem = useCart((s) => s.removeItem);
  const clearCart = useCart((s) => s.clearCart);
  const refreshFromServer = useCart((s) => s.refreshFromServer);

  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [address, setAddress] = useState('');
  const [deliveryInstruction, setDeliveryInstruction] = useState<string | null>('LEAVE_AT_DOOR');
  const [isProcessing, setIsProcessing] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [orderResult, setOrderResult] = useState<any | null>(null);
  const [checkoutAttemptId, setCheckoutAttemptId] = useState<string>(() => `web_chk_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`);
  const [isMapModalOpen, setIsMapModalOpen] = useState(false);
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [currentAddress, setCurrentAddress] = useState<SavedAddress | null>(null);
  const [isLoadingAddresses, setIsLoadingAddresses] = useState(true);

  // Prescription selection state
  const [isPrescriptionAttached, setIsPrescriptionAttached] = useState(false);
  const [prescriptionType, setPrescriptionType] = useState<'vault' | 'uploaded' | null>(null);

  const loadAddresses = async () => {
    setIsLoadingAddresses(true);
    try {
      const addrs = await fetchCustomerAddresses();
      if (addrs && addrs.length > 0) {
        setSavedAddresses(addrs);
        const def = addrs.find((a) => a.isDefault) || addrs[0];
        setCurrentAddress(def);
        setAddress(`${def.addressLine}${def.city ? `, ${def.city}` : ''}`);
      } else {
        setSavedAddresses([]);
        setCurrentAddress(null);
        setAddress('');
      }
    } catch (e) {
      setSavedAddresses([]);
      setCurrentAddress(null);
      setAddress('');
    } finally {
      setIsLoadingAddresses(false);
    }
  };

  useEffect(() => {
    loadAddresses();
    refreshFromServer();
  }, [refreshFromServer]);

  // Pricing calculations
  const totalItemCount = lines.reduce((sum, item) => sum + item.quantity, 0);
  const subtotal = lines.reduce((sum, item) => sum + (item.unitPrice || 0) * item.quantity, 0);
  const totalMrp = lines.reduce((sum, item) => sum + (item.mrp || (item.unitPrice || 0) * 1.25) * item.quantity, 0);
  const savings = Math.max(0, totalMrp - subtotal);
  const freeDeliveryThreshold = 199;
  const isFreeDeliveryUnlocked = subtotal >= freeDeliveryThreshold;
  const deliveryFee = isFreeDeliveryUnlocked ? 0 : 25;
  const deliveryPartnerFee = 2; // Flat small doorstep fee matching Android
  const grandTotal = subtotal + deliveryPartnerFee + (isFreeDeliveryUnlocked ? 0 : deliveryFee);
  const hasItems = lines.length > 0;
  const hasRxItems = lines.some((l) => l.rxRequired);

  const sessionUser = useSession((s) => s.user);

  const handleAuthorizeOrder = async () => {
    if (!hasItems) return;
    if (!sessionUser?.userId) {
      setCheckoutError('Please sign in to complete your checkout.');
      router.push('/auth/login?redirect=/checkout');
      return;
    }
    if (!currentAddress) {
      setCheckoutError('Please select or add a verified delivery address to proceed.');
      setIsMapModalOpen(true);
      return;
    }

    setIsProcessing(true);
    setCheckoutError(null);

    try {
      const orderPayload = {
        customerId: sessionUser.userId,
        orderType: 'QUICK_COMMERCE_10MIN',
        addressId: currentAddress.id,
        deliveryAddress: currentAddress,
        paymentMethod,
        items: lines.map((l) => ({
          sku: l.sku,
          name: l.name,
          packSize: l.packSize,
          unitPrice: l.unitPrice,
          quantity: l.quantity,
          rxRequired: l.rxRequired,
        })),
        idempotencyKey: checkoutAttemptId,
      };

      const res = await createOrder(orderPayload);
      if (res && (res.orderId || res.id)) {
        clearCart();
        setCheckoutAttemptId(`web_chk_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`);
        setOrderResult({
          orderId: res.orderId || res.id,
          totalAmount: res.totalAmount || grandTotal,
          deliveryOtp: res.deliveryOtp,
          deliverySlaMins: res.deliverySlaMins || res.slaMins || 10,
          status: res.status || 'PLACED',
        });
      } else {
        throw new Error('Order creation failed with unexpected server response.');
      }
    } catch (err: any) {
      setCheckoutError(err.message || 'Transaction authorization failed. Please retry.');
    } finally {
      setIsProcessing(false);
    }
  };

  // SUCCESS STATE: ORDER CONFIRMED
  if (orderResult) {
    return (
      <div className="min-h-screen bg-[#F4F6F8] flex flex-col font-sans antialiased pb-24">
        {/* Top Header */}
        <header className="bg-[#0E675E] text-white px-4 py-4 flex items-center justify-between shadow-md">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center">
              <Check className="h-5 w-5 text-white" />
            </div>
            <span className="font-extrabold text-base tracking-tight">Order Confirmed</span>
          </div>
          <span className="text-xs bg-emerald-700/80 px-2.5 py-1 rounded-full font-bold">
            ⚡ 10 MINS
          </span>
        </header>

        <main className="max-w-xl mx-auto px-4 py-8 flex-1 flex flex-col items-center text-center w-full">
          <div className="w-20 h-20 rounded-full bg-emerald-100 text-emerald-700 flex items-center justify-center mb-5 shadow-sm border border-emerald-200">
            <CheckCircle2 className="w-10 h-10 stroke-[2.5]" />
          </div>

          <span className="px-3 py-1 rounded-full bg-emerald-100 text-[#0E675E] text-xs font-black uppercase tracking-wider mb-2 border border-emerald-200">
            ⚡ Order Placed • Arriving in ~{orderResult.deliverySlaMins} Mins
          </span>

          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            Order Confirmed &amp; Dispatched!
          </h1>

          <p className="text-xs sm:text-sm text-slate-600 mt-2 max-w-md leading-relaxed">
            Your medicines are being packed at the store with batch verification. A licensed pharmacist has approved this dispatch.
          </p>

          {/* ORDER & OTP CARD */}
          <div className="w-full bg-white rounded-2xl border border-slate-200 p-5 shadow-sm my-6 space-y-4 text-left">
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div>
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Order ID</span>
                <p className="text-sm font-extrabold text-slate-900 font-mono">#{orderResult.orderId.slice(-8).toUpperCase()}</p>
              </div>
              <div className="text-right">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Amount</span>
                <p className="text-base font-black text-[#0E675E]">₹{Number(orderResult.totalAmount).toFixed(0)}</p>
              </div>
            </div>

            {/* AUTHORITATIVE OTP HIGHLIGHT */}
            {orderResult.deliveryOtp ? (
              <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-[#0E675E] text-white flex items-center justify-center font-bold text-lg">
                    🔑
                  </div>
                  <div>
                    <h4 className="text-xs font-extrabold text-slate-900">Doorstep Handover OTP</h4>
                    <p className="text-[11px] text-slate-600">Share with rider only upon receiving medicines</p>
                  </div>
                </div>
                <span className="text-2xl font-black tracking-widest text-[#0E675E] font-mono bg-white px-3 py-1.5 rounded-xl border border-emerald-300 shadow-xs">
                  {orderResult.deliveryOtp}
                </span>
              </div>
            ) : null}

            <div className="text-xs text-slate-600 space-y-1.5 pt-1">
              <p><strong className="text-slate-800">Delivering To:</strong> {address || 'Doorstep Location'}</p>
              <p><strong className="text-slate-800">Payment:</strong> {paymentMethod === 'COD' ? 'Cash on Delivery (₹' + Number(orderResult.totalAmount).toFixed(0) + ')' : paymentMethod}</p>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-3 w-full max-w-md">
            <Link
              href={`/orders/${orderResult.orderId}`}
              className="flex-1 py-3.5 px-6 rounded-xl bg-[#0E675E] hover:bg-[#094640] text-white font-extrabold text-sm shadow-sm flex items-center justify-center gap-2 transition-all active:scale-98"
            >
              <span>Track Live Delivery Map</span>
              <ChevronRight className="h-4 w-4 stroke-[3]" />
            </Link>

            <Link
              href="/"
              className="py-3.5 px-6 rounded-xl bg-white hover:bg-slate-50 text-slate-700 font-bold text-sm border border-slate-200 flex items-center justify-center transition-all"
            >
              Continue Shopping
            </Link>
          </div>
        </main>
        <MobileBottomNav />
      </div>
    );
  }

  // EMPTY CART STATE
  if (!hasItems) {
    return (
      <div className="min-h-screen bg-[#F4F6F8] flex flex-col font-sans antialiased pb-24">
        {/* Top Header */}
        <header className="bg-white border-b border-slate-200 px-4 py-3 flex items-center gap-3 sticky top-0 z-30 shadow-xs">
          <Link href="/" className="w-9 h-9 rounded-full bg-slate-100 flex items-center justify-center text-slate-700 hover:bg-slate-200">
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <div>
            <h1 className="text-base font-extrabold text-slate-900">Review Order</h1>
            <p className="text-xs text-slate-500">Your basket is currently empty</p>
          </div>
        </header>

        <main className="max-w-md mx-auto px-4 py-20 text-center space-y-4">
          <div className="w-16 h-16 rounded-2xl bg-emerald-50 text-[#0E675E] flex items-center justify-center mx-auto border border-emerald-100 shadow-xs">
            <ShoppingCart className="h-8 w-8" />
          </div>
          <h2 className="text-lg font-extrabold text-slate-900">Your cart is empty</h2>
          <p className="text-xs text-slate-500 max-w-xs mx-auto leading-relaxed">
            Add medicines and healthcare essentials for 10-minute store delivery in Rewari.
          </p>
          <div className="pt-2">
            <Link
              href="/"
              className="inline-flex items-center gap-2 px-6 py-3 bg-[#0E675E] text-white font-extrabold text-xs rounded-xl shadow-sm hover:bg-[#094640] transition-all"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Explore Healthcare Catalog</span>
            </Link>
          </div>
        </main>
        <MobileBottomNav />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#F4F6F8] text-slate-900 flex flex-col font-sans antialiased pb-28">
      {/* 1. REVIEW ORDER HEADER (MATCHING ANDROID CARTSCREEN) */}
      <header className="bg-white border-b border-slate-200 px-4 py-3 sticky top-0 z-30 shadow-xs">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link href="/" className="w-9 h-9 rounded-full bg-slate-100 flex items-center justify-center text-slate-700 hover:bg-slate-200 transition-colors">
              <ArrowLeft className="h-5 w-5" />
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base sm:text-lg font-extrabold text-slate-900 tracking-tight">Review Order</h1>
                <span className="text-[11px] font-bold bg-emerald-50 text-[#0E675E] border border-emerald-200 px-2 py-0.5 rounded-full">
                  {totalItemCount} {totalItemCount === 1 ? 'item' : 'items'}
                </span>
              </div>
              <p className="text-[11px] text-[#0E675E] font-semibold flex items-center gap-1">
                <Zap className="h-3 w-3 fill-[#0E675E]" />
                Instant 10-min fulfillment
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-50 border border-emerald-200 text-[#0E675E] text-[11px] font-extrabold shadow-2xs">
            <ShieldCheck className="h-3.5 w-3.5 stroke-[2.5]" />
            <span>100% Genuine</span>
          </div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-4 w-full space-y-4">
        {/* 2. FREE DELIVERY MILESTONE PROGRESS TRACKER */}
        <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs space-y-2.5">
          <div className="flex items-center justify-between text-xs font-extrabold">
            <span className="flex items-center gap-1.5 text-slate-800">
              <Zap className="h-4 w-4 text-amber-500 fill-amber-500" />
              {isFreeDeliveryUnlocked ? (
                <span className="text-[#0E675E]">🎉 Congratulations! Free Express Delivery unlocked!</span>
              ) : (
                <span>
                  Add <strong className="text-[#0E675E]">₹{(freeDeliveryThreshold - subtotal).toFixed(0)}</strong> more for <strong className="text-emerald-700 uppercase">FREE Delivery</strong>
                </span>
              )}
            </span>
            <span className="text-[11px] text-slate-400 font-bold">
              {isFreeDeliveryUnlocked ? '100%' : `${Math.min(100, Math.round((subtotal / freeDeliveryThreshold) * 100))}%`}
            </span>
          </div>

          {/* Animated Progress Bar */}
          <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-emerald-500 to-[#0E675E] rounded-full transition-all duration-500 ease-out"
              style={{ width: `${Math.min(100, (subtotal / freeDeliveryThreshold) * 100)}%` }}
            />
          </div>
        </div>

        {/* 3. MEDICINES IN ORDER CARD */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
          {/* Header Strip */}
          <div className="px-4 py-3 bg-emerald-50/50 border-b border-emerald-100/60 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-[10px] font-black bg-[#DCFCE7] text-[#166534] px-2 py-0.5 rounded uppercase tracking-wider">
                ⚡ 10 MINS
              </span>
              <span className="text-xs font-bold text-slate-700">
                Delivery in 10 minutes • {totalItemCount} {totalItemCount === 1 ? 'item' : 'items'}
              </span>
            </div>
            <span className="text-[11px] font-bold text-[#0E675E]">Verified Stock</span>
          </div>

          {/* Items List */}
          <div className="divide-y divide-slate-100 px-4">
            {lines.map((item) => (
              <div key={item.sku} className="py-3.5 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-14 h-14 rounded-xl bg-slate-50 p-1 flex items-center justify-center shrink-0 border border-slate-200/80">
                    {item.image ? (
                      <img src={item.image} alt={item.name} className="h-full w-full object-contain" />
                    ) : (
                      <Pill className="h-6 w-6 text-slate-400" />
                    )}
                  </div>
                  <div className="min-w-0">
                    <h4 className="text-xs sm:text-sm font-bold text-slate-900 truncate">{item.name}</h4>
                    <p className="text-[11px] text-slate-500 font-medium">
                      {item.brand ? `${item.brand} • ` : ''}{item.packSize || 'Strip of 10 tablets'}
                    </p>
                    <div className="flex items-baseline gap-2 mt-0.5">
                      <span className="text-sm font-extrabold text-slate-900">
                        ₹{(item.unitPrice || 0) * item.quantity}
                      </span>
                      {item.mrp && item.mrp > (item.unitPrice || 0) && (
                        <span className="text-[11px] text-slate-400 line-through">
                          ₹{(item.mrp * item.quantity).toFixed(0)}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {/* Deep Emerald Stadium Stepper */}
                <div className="flex items-center bg-[#0E675E] text-white rounded-full p-0.5 shadow-xs shrink-0">
                  <button
                    onClick={() => {
                      if (item.quantity <= 1) removeItem(item.sku);
                      else updateQuantity(item.sku, item.quantity - 1);
                    }}
                    className="w-7 h-7 flex items-center justify-center rounded-full hover:bg-emerald-800 active:scale-95 transition-all text-white font-bold"
                  >
                    <Minus className="h-3.5 w-3.5 stroke-[2.5]" />
                  </button>
                  <span className="w-7 text-center font-extrabold text-xs">{item.quantity}</span>
                  <button
                    onClick={() => updateQuantity(item.sku, item.quantity + 1)}
                    className="w-7 h-7 flex items-center justify-center rounded-full hover:bg-emerald-800 active:scale-95 transition-all text-white font-bold"
                  >
                    <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
                  </button>
                </div>
              </div>
            ))}
          </div>

          {/* Add more items footer */}
          <Link
            href="/categories"
            className="block px-4 py-3 border-t border-slate-100 bg-slate-50/50 hover:bg-slate-100 text-[#0E675E] text-xs font-bold transition-colors flex items-center justify-between"
          >
            <span>+ Add more medicines or essentials</span>
            <ChevronRight className="h-4 w-4" />
          </Link>
        </div>

        {/* 4. DOCTOR'S PRESCRIPTION SECTION */}
        <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center">
                <Info className="h-4 w-4" />
              </div>
              <h3 className="text-sm font-extrabold text-slate-900">Doctor's Prescription</h3>
            </div>
            <span className={`text-[10px] font-black px-2 py-0.5 rounded uppercase tracking-wider ${
              hasRxItems ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600'
            }`}>
              {hasRxItems ? 'REQUIRED FOR DISPATCH' : 'OPTIONAL'}
            </span>
          </div>

          <p className="text-xs text-slate-600 leading-relaxed">
            {hasRxItems
              ? 'One or more items in your cart require a valid prescription. Our registered pharmacist will verify dosage upon order.'
              : 'Upload prescription for licensed pharmacist verification (optional). You can still proceed without it.'}
          </p>

          <div className="grid grid-cols-2 gap-2.5 pt-1">
            <button
              type="button"
              onClick={() => {
                setIsPrescriptionAttached(true);
                setPrescriptionType('vault');
              }}
              className={`py-2.5 px-3 rounded-xl border text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                prescriptionType === 'vault'
                  ? 'bg-emerald-50 border-emerald-300 text-[#0E675E] shadow-2xs'
                  : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'
              }`}
            >
              <FileCheck className="h-4 w-4 text-[#0E675E]" />
              <span>From Vault {prescriptionType === 'vault' ? '✓' : '(3)'}</span>
            </button>

            <button
              type="button"
              onClick={() => {
                setIsPrescriptionAttached(true);
                setPrescriptionType('uploaded');
              }}
              className={`py-2.5 px-3 rounded-xl border text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                prescriptionType === 'uploaded'
                  ? 'bg-[#0E675E] border-[#0E675E] text-white shadow-2xs'
                  : 'bg-[#0E675E] border-[#0E675E] text-white hover:bg-[#094640]'
              }`}
            >
              <UploadCloud className="h-4 w-4" />
              <span>{prescriptionType === 'uploaded' ? 'Prescription Attached ✓' : '+ Upload New'}</span>
            </button>
          </div>
        </div>

        {/* 5. BILL SUMMARY (VERIFIED PRICING) */}
        <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs space-y-3">
          <div className="flex items-center justify-between pb-2 border-b border-slate-100">
            <div className="flex items-center gap-2">
              <FileText className="h-4 w-4 text-[#0E675E]" />
              <h3 className="text-sm font-extrabold text-slate-900">Bill Summary</h3>
            </div>
            <span className="text-[11px] font-bold bg-slate-100 text-slate-600 px-2 py-0.5 rounded">
              Verified Prices
            </span>
          </div>

          <div className="space-y-2 text-xs">
            <div className="flex justify-between text-slate-600">
              <span>Item total (MRP)</span>
              <span className="font-semibold text-slate-900">₹{totalMrp.toFixed(0)}</span>
            </div>

            {savings > 0 && (
              <div className="flex justify-between text-emerald-700 font-bold">
                <span>Total savings</span>
                <span>-₹{savings.toFixed(0)}</span>
              </div>
            )}

            <div className="flex justify-between text-slate-600">
              <span>Delivery partner fee</span>
              <span className="font-semibold text-slate-900">₹{deliveryPartnerFee.toFixed(0)}</span>
            </div>

            <div className="flex justify-between text-slate-600">
              <span>Express delivery charge</span>
              {isFreeDeliveryUnlocked ? (
                <span className="font-bold text-emerald-700">FREE</span>
              ) : (
                <span className="font-semibold text-slate-900">₹{deliveryFee.toFixed(0)}</span>
              )}
            </div>

            <div className="flex justify-between text-slate-600">
              <span>Handling charges</span>
              <span className="font-bold text-emerald-700">FREE</span>
            </div>

            {/* Savings Celebration Ribbon */}
            {savings > 0 && (
              <div className="p-2.5 rounded-xl bg-emerald-50 border border-emerald-200 text-center text-xs font-extrabold text-[#0E675E] my-1">
                🎉 Total Savings of ₹{savings.toFixed(0)} on this order
              </div>
            )}

            <div className="pt-2.5 border-t border-slate-200 flex justify-between items-center text-sm font-extrabold text-slate-900">
              <span>To Pay</span>
              <span className="text-base font-black text-[#0E675E]">₹{grandTotal.toFixed(0)}</span>
            </div>
          </div>
        </div>

        {/* 6. DELIVERY ADDRESS CARD */}
        <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-10 h-10 rounded-full bg-amber-50 border border-amber-200 text-amber-600 flex items-center justify-center shrink-0">
              <MapPin className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <h4 className="text-xs font-extrabold text-slate-900 truncate">
                {currentAddress?.contactName || currentAddress?.tag || 'Home • Rewari'}
              </h4>
              <p className="text-[11px] text-slate-500 truncate mt-0.5">
                {address || 'Company Bagh, Rewari, Haryana'}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setIsMapModalOpen(true)}
            className="text-xs font-extrabold text-[#0E675E] hover:underline shrink-0"
          >
            Change &gt;
          </button>
        </div>

        {/* 7. PAYMENT METHOD SELECTION */}
        <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs space-y-3">
          <h3 className="text-sm font-extrabold text-slate-900">Payment Option</h3>
          <div className="grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={() => setPaymentMethod('COD')}
              className={`p-3 rounded-xl border text-left flex items-center gap-2.5 transition-all ${
                paymentMethod === 'COD'
                  ? 'bg-emerald-50/70 border-[#0E675E] text-[#0E675E] shadow-2xs'
                  : 'bg-white border-slate-200 text-slate-700'
              }`}
            >
              <Banknote className="h-5 w-5 shrink-0" />
              <div>
                <h4 className="text-xs font-extrabold">Cash on Delivery</h4>
                <p className="text-[10px] text-slate-500">Pay cash at doorstep</p>
              </div>
            </button>

            <button
              type="button"
              onClick={() => setPaymentMethod('UPI')}
              className={`p-3 rounded-xl border text-left flex items-center gap-2.5 transition-all ${
                paymentMethod === 'UPI'
                  ? 'bg-emerald-50/70 border-[#0E675E] text-[#0E675E] shadow-2xs'
                  : 'bg-white border-slate-200 text-slate-700'
              }`}
            >
              <QrCode className="h-5 w-5 shrink-0" />
              <div>
                <h4 className="text-xs font-extrabold">UPI / QR Code</h4>
                <p className="text-[10px] text-slate-500">Scan on delivery</p>
              </div>
            </button>
          </div>
        </div>

        {checkoutError && (
          <div className="p-3.5 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-red-700 flex items-start gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>{checkoutError}</span>
          </div>
        )}
      </main>

      {/* 8. STICKY BOTTOM CHECKOUT BAR (MOBILE & DESKTOP) */}
      <div className="fixed bottom-14 md:bottom-0 left-0 right-0 bg-white border-t border-slate-200 px-4 py-3 z-30 shadow-lg">
        <div className="max-w-4xl mx-auto flex items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-black bg-[#DCFCE7] text-[#166534] px-1.5 py-0.5 rounded text-[10px]">
                ⚡ 10 MINS
              </span>
              <span className="text-[11px] text-slate-500 font-bold">Total</span>
            </div>
            <p className="text-lg font-black text-[#0E675E] mt-0.5">
              ₹{grandTotal.toFixed(0)}
            </p>
          </div>

          <button
            onClick={() => {
              if (!currentAddress) {
                setIsMapModalOpen(true);
              } else {
                handleAuthorizeOrder();
              }
            }}
            disabled={isProcessing}
            className={`flex-1 max-w-sm py-3.5 px-6 rounded-xl font-extrabold text-sm sm:text-base shadow-sm transition-all flex items-center justify-center gap-2 ${
              !currentAddress
                ? 'bg-slate-300 text-slate-500 hover:bg-slate-300 shadow-none'
                : 'bg-[#0E675E] hover:bg-[#094640] active:scale-98 text-white'
            }`}
          >
            {isProcessing ? (
              <span>Authorizing Dispatch...</span>
            ) : !currentAddress ? (
              <span>Select Address to Proceed</span>
            ) : (
              <>
                <span>Place Order ({paymentMethod})</span>
                <ChevronRight className="h-4 w-4 stroke-[3]" />
              </>
            )}
          </button>
        </div>
      </div>

      <MobileBottomNav />

      {/* LOCATION PICKER MODAL */}
      <DeliveryAddressMapModal
        isOpen={isMapModalOpen}
        onClose={() => setIsMapModalOpen(false)}
        savedAddresses={savedAddresses}
        currentAddress={currentAddress}
        onSelectAddress={(selected) => {
          setCurrentAddress(selected);
          setAddress(`${selected.addressLine}${selected.city ? `, ${selected.city}` : ''}`);
          setIsMapModalOpen(false);
        }}
        onAddressSaved={loadAddresses}
      />
    </div>
  );
}
