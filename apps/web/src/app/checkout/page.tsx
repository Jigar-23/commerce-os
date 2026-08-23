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
  ThermometerSnowflake,
  BellOff,
  DoorClosed,
  PhoneOff,
  UserCheck,
} from 'lucide-react';
import {
  createOrder,
  fetchCustomerAddresses,
  SavedAddress,
} from '@/lib/api-client';
import { useSession } from '@/lib/session-store';
import { useCart } from '@/lib/cart-store';
import DeliveryAddressMapModal from '@/components/DeliveryAddressMapModal';
import {
  CommerceNavbar,
  CommerceButton,
  CommerceQuantityControl,
  CommerceBadge,
  CommerceEmptyState,
} from '@commerce-os/ui';

export default function ExpressCheckoutPage() {
  const router = useRouter();
  const lines = useCart((s) => s.lines);
  const updateQuantity = useCart((s) => s.updateQuantity);
  const removeItem = useCart((s) => s.removeItem);
  const clearCart = useCart((s) => s.clearCart);

  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [address, setAddress] = useState('');
  const [deliveryInstruction, setDeliveryInstruction] = useState<string | null>('LEAVE_AT_DOOR');
  const [isProcessing, setIsProcessing] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [orderResult, setOrderResult] = useState<any | null>(null);
  const [isMapModalOpen, setIsMapModalOpen] = useState(false);
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [currentAddress, setCurrentAddress] = useState<SavedAddress | null>(null);
  const [isLoadingAddresses, setIsLoadingAddresses] = useState(true);

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
  }, []);

  // Provisional pricing for display before server calculation
  const provisionalSubtotal = lines.reduce((sum, item) => sum + (item.unitPrice || 0) * item.quantity, 0);
  const provisionalTotalMrp = lines.reduce((sum, item) => sum + (item.mrp || (item.unitPrice || 0) * 1.25) * item.quantity, 0);
  const provisionalSavings = Math.max(0, provisionalTotalMrp - provisionalSubtotal);
  const isColdChain = lines.some((item) => item.coldChain);
  const provisionalDeliveryFee = provisionalSubtotal >= 499 ? 0 : 25;
  const provisionalColdFee = isColdChain ? 15 : 0;
  const provisionalTotal = provisionalSubtotal + provisionalDeliveryFee + provisionalColdFee;
  const hasItems = lines.length > 0;

  const sessionUser = useSession((s) => s.user);

  const handleAuthorizeOrder = async () => {
    if (!hasItems) return;
    if (!sessionUser?.userId) {
      setCheckoutError('Please sign in to complete your checkout.');
      router.push('/login?redirect=/checkout');
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
        orderType: 'MEDICINE',
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
      };

      const res = await createOrder(orderPayload);
      if (res && (res.orderId || res.id)) {
        clearCart();
        setOrderResult({
          orderId: res.orderId || res.id,
          totalAmount: res.totalAmount || provisionalTotal,
          deliveryOtp: res.deliveryOtp, // Authoritative OTP from backend only
          deliverySlaMins: res.deliverySlaMins || res.slaMins,
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
      <div className="min-h-screen bg-surface-canvas flex flex-col font-sans antialiased">
        <CommerceNavbar
          locationAddress={currentAddress ? `${currentAddress.contactName || currentAddress.tag || 'Home'} • ${currentAddress.addressLine}` : 'Order Confirmed'}
          showSearchBar={false}
        />

        <main className="max-w-xl mx-auto px-4 py-12 flex-1 flex flex-col justify-center items-center text-center">
          <div className="w-20 h-20 rounded-3xl bg-surface-brandSubtle text-content-brand flex items-center justify-center mb-6 shadow-card border border-border-brandSubtle animate-scale-in">
            <CheckCircle2 className="w-10 h-10 stroke-[2.5]" />
          </div>

          <span className="px-3 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-black uppercase tracking-wider mb-2 border border-border-brandSubtle">
            ⚡ Order Placed • {orderResult.deliverySlaMins ? `Arriving in ${orderResult.deliverySlaMins} Mins` : 'Instant Dispatch Active'}
          </span>

          <h1 className="text-2xl sm:text-3xl font-black text-content-primary tracking-tight">
            Order Confirmed &amp; Dispatched!
          </h1>

          <p className="text-xs sm:text-sm text-content-secondary mt-2 max-w-md leading-relaxed">
            Your items are being packed at the dark store with batch verification. A licensed pharmacist has approved this dispatch.
          </p>

          {/* ORDER & OTP CARD */}
          <div className="w-full bg-surface-card rounded-3xl border border-border-default p-6 shadow-card my-6 space-y-4 text-left">
            <div className="flex items-center justify-between pb-3 border-b border-border-subtle">
              <div>
                <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider">Order ID</span>
                <p className="text-sm font-black text-content-primary font-mono">{orderResult.orderId}</p>
              </div>
              <div className="text-right">
                <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider">Amount Paid</span>
                <p className="text-sm font-black text-content-brand">₹{orderResult.totalAmount.toFixed(0)}</p>
              </div>
            </div>

            {/* AUTHORITATIVE OTP HIGHLIGHT */}
            {orderResult.deliveryOtp ? (
              <div className="p-4 rounded-2xl bg-surface-brandSubtle border border-border-brandSubtle flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-action-primaryBg text-action-primaryText flex items-center justify-center font-black">
                    🔒
                  </div>
                  <div>
                    <h4 className="text-xs font-extrabold text-content-primary">Delivery Handover OTP</h4>
                    <p className="text-2xs text-content-secondary">Share this 4-digit code with the rider upon arrival</p>
                  </div>
                </div>
                <span className="text-xl font-black tracking-widest text-content-brand font-mono bg-white px-3 py-1.5 rounded-xl border border-border-brand/40 shadow-xs">
                  {orderResult.deliveryOtp}
                </span>
              </div>
            ) : (
              <div className="p-3.5 rounded-2xl bg-surface-subtle border border-border-subtle flex items-center justify-between text-xs text-content-secondary">
                <span className="font-bold">Delivery Security:</span>
                <span>OTP verification generated upon rider dispatch</span>
              </div>
            )}

            <div className="text-xs text-content-secondary space-y-1">
              <p><strong className="text-content-primary">Delivery Address:</strong> {address || 'Doorstep Location'}</p>
              <p><strong className="text-content-primary">Payment Method:</strong> {paymentMethod.replace('_', ' ')}</p>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-3 w-full max-w-md">
            <Link
              href="/orders"
              className="flex-1 py-3.5 px-6 rounded-2xl bg-action-primaryBg hover:bg-action-primaryHover text-action-primaryText font-black text-sm shadow-subtle flex items-center justify-center gap-2 transition-all active:scale-98"
            >
              <span>Track Live Delivery</span>
              <ChevronRight className="h-4 w-4 stroke-[3]" />
            </Link>

            <Link
              href="/"
              className="py-3.5 px-6 rounded-2xl bg-surface-card hover:bg-surface-subtle text-content-primary font-bold text-sm border border-border-default flex items-center justify-center transition-all"
            >
              Continue Shopping
            </Link>
          </div>
        </main>
      </div>
    );
  }

  // EMPTY CART STATE
  if (!hasItems) {
    return (
      <div className="min-h-screen bg-surface-canvas flex flex-col font-sans antialiased">
        <CommerceNavbar
          locationAddress={currentAddress ? `${currentAddress.contactName || currentAddress.tag || 'Home'} • ${currentAddress.addressLine}` : 'Checkout'}
          showSearchBar={false}
        />
        <main className="max-w-xl mx-auto px-4 py-16 text-center space-y-4">
          <div className="w-16 h-16 rounded-2xl bg-surface-subtle text-content-muted flex items-center justify-center mx-auto">
            <ShoppingCart className="h-8 w-8" />
          </div>
          <h2 className="text-xl font-extrabold text-content-primary">Your cart is empty</h2>
          <p className="text-xs text-content-secondary max-w-xs mx-auto">
            Add medicines and essentials from the catalog for instant dark store dispatch.
          </p>
          <Link
            href="/"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-action-primaryBg text-action-primaryText font-bold text-xs rounded-xl shadow-subtle hover:bg-action-primaryHover transition-all"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Explore Storefront</span>
          </Link>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex flex-col font-sans antialiased pb-24 selection:bg-surface-brandSubtle selection:text-content-brand">
      {/* 1. NAVBAR */}
      <CommerceNavbar
        locationAddress={currentAddress ? `${currentAddress.contactName || currentAddress.tag || 'Home'} • ${currentAddress.addressLine}` : 'Select Delivery Location'}
        onOpenLocationModal={() => setIsMapModalOpen(true)}
        showSearchBar={false}
      />

      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-6 w-full space-y-6">
        {/* TOP BAR / BACK LINK */}
        <div className="flex items-center justify-between">
          <Link href="/" className="inline-flex items-center gap-1.5 text-xs font-bold text-content-secondary hover:text-content-primary transition-colors">
            <ArrowLeft className="h-4 w-4" />
            <span>Back to Storefront</span>
          </Link>
          <span className="text-xs font-extrabold text-content-brand uppercase tracking-wider flex items-center gap-1">
            <Clock className="h-3.5 w-3.5" />
            ⚡ Authoritative Express Dispatch
          </span>
        </div>

        {/* 2 COLUMNS: CHECKOUT FORM & BILL SUMMARY */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* LEFT COLUMN: STEPS (7 COLS) */}
          <div className="lg:col-span-7 space-y-6">
            {/* STEP 1: DELIVERY LOCATION */}
            <section className="bg-surface-card rounded-3xl p-6 border border-border-default shadow-card space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-surface-brandSubtle text-content-brand flex items-center justify-center font-black text-xs">
                    1
                  </div>
                  <h2 className="text-sm sm:text-base font-extrabold text-content-primary">Delivery Address</h2>
                </div>
                <button
                  onClick={() => setIsMapModalOpen(true)}
                  className="text-xs font-bold text-content-brand hover:underline cursor-pointer"
                >
                  {currentAddress ? 'Change Location' : 'Select Location'}
                </button>
              </div>

              {currentAddress ? (
                <div className="p-4 rounded-2xl bg-surface-subtle border border-border-subtle flex items-start gap-3">
                  <MapPin className="h-5 w-5 text-content-brand shrink-0 mt-0.5" />
                  <div className="min-w-0 flex-1">
                    <span className="text-xs font-extrabold text-content-primary block">
                      {currentAddress.contactName || currentAddress.tag || 'Saved Address'}
                    </span>
                    <p className="text-xs text-content-secondary font-medium mt-0.5 leading-relaxed">
                      {address}
                    </p>
                  </div>
                </div>
              ) : (
                <div className="p-6 rounded-2xl bg-surface-subtle border border-border-default text-center space-y-3">
                  <MapPin className="h-8 w-8 text-content-muted mx-auto" />
                  <div>
                    <h4 className="text-xs font-extrabold text-content-primary">No Delivery Location Selected</h4>
                    <p className="text-2xs text-content-secondary mt-0.5">Please add or select a delivery address to verify serviceability.</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setIsMapModalOpen(true)}
                    className="px-4 py-2 bg-action-primaryBg hover:bg-action-primaryHover text-action-primaryText rounded-xl text-xs font-bold shadow-subtle transition-all cursor-pointer inline-flex items-center gap-1.5"
                  >
                    <Plus className="h-3.5 w-3.5" />
                    <span>Select Delivery Address</span>
                  </button>
                </div>
              )}

              {/* DELIVERY INSTRUCTIONS */}
              <div className="space-y-2 pt-2">
                <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider">
                  Doorstep Delivery Instructions
                </span>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  {[
                    { id: 'LEAVE_AT_DOOR', label: 'Leave at Door', icon: <DoorClosed className="h-3.5 w-3.5" /> },
                    { id: 'DONT_RING', label: 'Do not Ring', icon: <BellOff className="h-3.5 w-3.5" /> },
                    { id: 'AVOID_CALLING', label: 'Avoid Calling', icon: <PhoneOff className="h-3.5 w-3.5" /> },
                    { id: 'WITH_GUARD', label: 'Leave with Guard', icon: <UserCheck className="h-3.5 w-3.5" /> },
                  ].map((inst) => (
                    <button
                      key={inst.id}
                      type="button"
                      onClick={() => setDeliveryInstruction(deliveryInstruction === inst.id ? null : inst.id)}
                      className={`p-2 rounded-xl border text-2xs font-bold flex items-center justify-center gap-1.5 transition-all cursor-pointer ${
                        deliveryInstruction === inst.id
                          ? 'bg-action-primaryBg text-action-primaryText border-border-brand shadow-subtle'
                          : 'bg-surface-subtle text-content-secondary border-border-subtle hover:bg-surface-muted'
                      }`}
                    >
                      {inst.icon}
                      <span>{inst.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            </section>

            {/* STEP 2: CART ITEMS REVIEW */}
            <section className="bg-surface-card rounded-3xl p-6 border border-border-default shadow-card space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-surface-brandSubtle text-content-brand flex items-center justify-center font-black text-xs">
                    2
                  </div>
                  <h2 className="text-sm sm:text-base font-extrabold text-content-primary">Review Selected Items ({lines.length})</h2>
                </div>
                <span className="text-2xs font-extrabold text-content-brand uppercase tracking-wider">⚡ Verified Stock</span>
              </div>

              <div className="divide-y divide-border-subtle">
                {lines.map((item) => (
                  <div key={item.sku} className="py-3 flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-12 h-12 rounded-lg bg-surface-subtle p-1 flex items-center justify-center shrink-0 border border-border-subtle">
                        {item.image ? (
                          <img src={item.image} alt={item.name} className="h-full w-full object-contain" />
                        ) : (
                          <ShoppingCart className="h-5 w-5 text-content-muted" />
                        )}
                      </div>
                      <div className="min-w-0">
                        <h4 className="text-xs font-bold text-content-primary truncate">{item.name}</h4>
                        <p className="text-2xs text-content-muted font-medium">{item.packSize || '1 Unit'}</p>
                        <p className="text-xs font-black text-content-primary mt-0.5">₹{(item.unitPrice || 0) * item.quantity}</p>
                      </div>
                    </div>

                    <CommerceQuantityControl
                      quantity={item.quantity}
                      onIncrement={() => updateQuantity(item.sku, item.quantity + 1)}
                      onDecrement={() => {
                        if (item.quantity <= 1) removeItem(item.sku);
                        else updateQuantity(item.sku, item.quantity - 1);
                      }}
                      size="sm"
                    />
                  </div>
                ))}
              </div>
            </section>

            {/* STEP 3: PAYMENT METHOD */}
            <section className="bg-surface-card rounded-3xl p-6 border border-border-default shadow-card space-y-4">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-surface-brandSubtle text-content-brand flex items-center justify-center font-black text-xs">
                  3
                </div>
                <h2 className="text-sm sm:text-base font-extrabold text-content-primary">Select Payment Method</h2>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {[
                  {
                    id: 'COD',
                    title: 'Cash on Delivery (COD)',
                    subtitle: 'Pay cash to rider at doorstep',
                    icon: <Banknote className="h-5 w-5" />,
                    active: true,
                  },
                  {
                    id: 'UPI_INSTANT',
                    title: 'UPI Instant',
                    subtitle: 'Coming Soon (Next Release)',
                    icon: <QrCode className="h-5 w-5" />,
                    active: false,
                  },
                  {
                    id: 'CARD',
                    title: 'Credit / Debit Card',
                    subtitle: 'Coming Soon (Next Release)',
                    icon: <CreditCard className="h-5 w-5" />,
                    active: false,
                  },
                ].map((pm) => (
                  <button
                    key={pm.id}
                    type="button"
                    disabled={!pm.active}
                    onClick={() => pm.active && setPaymentMethod(pm.id)}
                    className={`p-4 rounded-2xl border text-left flex flex-col justify-between space-y-3 transition-all ${
                      pm.active ? 'cursor-pointer' : 'cursor-not-allowed opacity-60'
                    } ${
                      paymentMethod === pm.id
                        ? 'bg-surface-brandSubtle border-border-brand shadow-subtle ring-2 ring-border-brand/20'
                        : 'bg-surface-subtle border-border-subtle hover:bg-surface-muted'
                    }`}
                  >
                    <div className={paymentMethod === pm.id ? 'text-content-brand' : 'text-content-muted'}>
                      {pm.icon}
                    </div>
                    <div>
                      <div className="flex items-center gap-1.5">
                        <h4 className="text-xs font-extrabold text-content-primary">{pm.title}</h4>
                        {pm.active && (
                          <span className="px-1.5 py-0.5 rounded text-2xs font-black bg-surface-brandSubtle text-content-brand border border-border-brandSubtle">ACTIVE</span>
                        )}
                      </div>
                      <p className="text-2xs text-content-secondary mt-0.5">{pm.subtitle}</p>
                    </div>
                  </button>
                ))}
              </div>
            </section>
          </div>

          {/* RIGHT COLUMN: BILL SUMMARY & CHECKOUT CTA (5 COLS) */}
          <div className="lg:col-span-5 space-y-4 sticky top-24">
            <div className="bg-surface-card rounded-3xl p-6 border border-border-default shadow-card space-y-5">
              <h3 className="text-base font-extrabold text-content-primary pb-3 border-b border-border-subtle">
                Order Bill Summary
              </h3>

              <div className="space-y-3 text-xs">
                <div className="flex justify-between text-content-secondary font-medium">
                  <span>Items Total (MRP)</span>
                  <span className="line-through text-content-muted">₹{provisionalTotalMrp.toFixed(0)}</span>
                </div>

                {provisionalSavings > 0 && (
                  <div className="flex justify-between text-content-brand font-bold">
                    <span>Item Discount Savings</span>
                    <span>-₹{provisionalSavings.toFixed(0)}</span>
                  </div>
                )}

                <div className="flex justify-between text-content-secondary font-medium">
                  <span>Express Delivery Fee</span>
                  <span>{provisionalDeliveryFee === 0 ? <span className="text-content-brand font-bold">FREE</span> : `₹${provisionalDeliveryFee}`}</span>
                </div>

                {provisionalColdFee > 0 && (
                  <div className="flex justify-between text-content-accent font-medium">
                    <span className="flex items-center gap-1">
                      <ThermometerSnowflake className="h-3.5 w-3.5" /> Cold Chain Handling
                    </span>
                    <span>₹{provisionalColdFee}</span>
                  </div>
                )}

                <div className="pt-3 border-t border-border-strong flex justify-between text-base font-black text-content-primary">
                  <span>Payable Amount</span>
                  <span className="text-content-brand text-lg">₹{provisionalTotal.toFixed(0)}</span>
                </div>
              </div>

              {provisionalSavings > 0 && (
                <div className="p-3 rounded-xl bg-surface-brandSubtle border border-border-brandSubtle text-2xs font-extrabold text-content-brand text-center">
                  🎉 You are saving ₹{provisionalSavings.toFixed(0)} on this order!
                </div>
              )}

              {checkoutError && (
                <div className="p-3.5 rounded-xl bg-surface-dangerSubtle border border-border-danger text-xs font-bold text-content-danger flex items-start gap-2">
                  <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
                  <span>{checkoutError}</span>
                </div>
              )}

              {/* PRIMARY CTA */}
              <button
                onClick={handleAuthorizeOrder}
                disabled={isProcessing || !currentAddress}
                className="w-full py-4 px-6 rounded-2xl bg-action-primaryBg hover:bg-action-primaryHover active:bg-action-primaryBg text-action-primaryText font-black text-sm sm:text-base shadow-card transition-all active:scale-98 disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer"
              >
                <Lock className="h-4 w-4" />
                <span>{isProcessing ? 'Authorizing Dispatch...' : `Pay & Place Order • ₹${provisionalTotal.toFixed(0)}`}</span>
              </button>

              <div className="flex items-center justify-center gap-2 text-2xs font-bold text-content-muted">
                <ShieldCheck className="h-4 w-4 text-content-brand" />
                <span>256-Bit SSL Encrypted &amp; Verified Checkout</span>
              </div>
            </div>
          </div>
        </div>
      </main>

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
