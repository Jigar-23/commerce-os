'use client';

import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import {
  sellerApi,
  CustomerFullDetail,
  RegisteredUser,
  CustomerAddress,
  CustomerOrderHistory,
  CustomerCart,
  CustomerPrescription,
  CustomerAnalytics
} from '@/lib/apiClient';
import {
  User, Phone, Mail, MapPin, Calendar, Clock,
  ArrowLeft, RefreshCw, Edit3, Shield, CheckCircle2,
  XCircle, AlertCircle, ShoppingBag, ShoppingCart,
  FileText, Copy, Check, ExternalLink, ChevronRight,
  TrendingUp, Award, Layers, Hash, CreditCard, Key,
  Sparkles, Save, X, PhoneCall, ArrowUpRight, ShieldCheck,
  UserCheck, UserX
} from 'lucide-react';

export default function UserDetailPage() {
  return (
    <SellerAuthGuard>
      <UserDetailContent />
    </SellerAuthGuard>
  );
}

function UserDetailContent() {
  const params = useParams();
  const router = useRouter();
  const customerId = typeof params?.id === 'string' ? decodeURIComponent(params.id) : '';

  const [detail, setDetail] = useState<CustomerFullDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [orderFilter, setOrderFilter] = useState<'ALL' | 'DELIVERED' | 'ACTIVE' | 'CANCELLED'>('ALL');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [statusToast, setStatusToast] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Edit Customer Modal State
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editForm, setEditForm] = useState({
    fullName: '',
    phone: '',
    email: '',
    tier: 'STANDARD',
    isActive: true
  });
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState<string | null>(null);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setStatusToast({ text, type });
    setTimeout(() => setStatusToast(null), 3500);
  };

  const fetchDetail = useCallback(async () => {
    if (!customerId) return;
    setIsLoading(true);
    setError(null);
    try {
      const res = await sellerApi.getUserDetail(customerId);
      if (res.ok && res.data && res.data.ok) {
        setDetail(res.data);
      } else {
        setError(res.error || 'Failed to fetch customer profile data.');
      }
    } catch (err: any) {
      setError(err?.message || 'Network error fetching customer details.');
    } finally {
      setIsLoading(false);
    }
  }, [customerId]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  const copyToClipboard = (text: string, key: string) => {
    navigator.clipboard?.writeText(text);
    setCopiedKey(key);
    showToast(`Copied to clipboard!`);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const handleOpenEdit = () => {
    if (!detail?.customer) return;
    setEditForm({
      fullName: detail.customer.full_name || '',
      phone: detail.customer.phone || '',
      email: detail.customer.email || '',
      tier: detail.customer.tier || 'STANDARD',
      isActive: detail.customer.is_active ?? true
    });
    setSaveError(null);
    setSaveSuccess(null);
    setIsEditModalOpen(true);
  };

  const handleSaveCustomer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customerId) return;
    setIsSaving(true);
    setSaveError(null);
    setSaveSuccess(null);

    try {
      const res = await sellerApi.updateUser(customerId, {
        fullName: editForm.fullName.trim() || undefined,
        phone: editForm.phone.trim() || undefined,
        email: editForm.email.trim() || undefined,
        tier: editForm.tier,
        isActive: editForm.isActive
      });

      if (res.ok && res.data?.customer) {
        setSaveSuccess('Customer details updated successfully!');
        setDetail((prev) => prev ? {
          ...prev,
          customer: {
            ...prev.customer,
            ...res.data.customer
          }
        } : null);
        showToast('Customer profile updated successfully!');
        setTimeout(() => setIsEditModalOpen(false), 900);
      } else {
        setSaveError(res.error || 'Failed to update customer profile.');
      }
    } catch (err: any) {
      setSaveError(err?.message || 'Error occurred while saving customer profile.');
    } finally {
      setIsSaving(false);
    }
  };

  const customer = detail?.customer;
  const addresses = detail?.addresses || [];
  const orders = detail?.orders || [];
  const cart = detail?.cart;
  const prescriptions = detail?.prescriptions || [];
  const analytics = detail?.analytics;

  const filteredOrders = useMemo(() => {
    return orders.filter((o) => {
      const s = (o.status || '').toUpperCase();
      if (orderFilter === 'ALL') return true;
      if (orderFilter === 'DELIVERED') return s === 'DELIVERED' || s === 'COMPLETED';
      if (orderFilter === 'CANCELLED') return s === 'CANCELLED';
      if (orderFilter === 'ACTIVE') return !['DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUNDED'].includes(s);
      return true;
    });
  }, [orders, orderFilter]);

  const getOrderStatusBadge = (status: string) => {
    const s = (status || '').toUpperCase();
    if (s === 'DELIVERED' || s === 'COMPLETED') {
      return 'bg-emerald-50 text-emerald-700 border-emerald-200';
    }
    if (s === 'CANCELLED' || s === 'FAILED' || s === 'REJECTED') {
      return 'bg-rose-50 text-rose-700 border-rose-200';
    }
    if (['PLACED', 'RECEIVED', 'CONFIRMED', 'SELLER_ACCEPTED', 'PACKED', 'OUT_FOR_DELIVERY'].includes(s)) {
      return 'bg-blue-50 text-blue-700 border-blue-200';
    }
    return 'bg-amber-50 text-amber-700 border-amber-200';
  };

  const getTierBadge = (tier?: string) => {
    const t = (tier || 'STANDARD').toUpperCase();
    if (t === 'VIP') return 'bg-purple-50 text-purple-700 border-purple-200';
    if (t === 'PLATINUM') return 'bg-sky-50 text-sky-700 border-sky-200';
    if (t === 'GOLD') return 'bg-amber-50 text-amber-700 border-amber-200';
    return 'bg-surface-subtle text-content-secondary border-border-default';
  };

  return (
    <div className="flex h-screen bg-surface-canvas font-sans overflow-hidden">
      {/* Toast Alert */}
      {statusToast && (
        <div className={`fixed top-4 right-4 z-50 px-5 py-3 rounded-2xl shadow-xl font-bold text-xs flex items-center space-x-2 transition-all border ${
          statusToast.type === 'success'
            ? 'bg-action-primaryBg text-white border-border-brand'
            : 'bg-rose-600 text-white border-rose-700'
        }`}>
          <CheckCircle2 className="w-4 h-4" />
          <span>{statusToast.text}</span>
        </div>
      )}

      {/* Seller Sidebar with activeTab='users' */}
      <SellerSidebar
        activeTab="users"
        onRefresh={fetchDetail}
        isLoading={isLoading}
      />

      {/* Main View Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* Sticky Header Bar */}
        <header className="sticky top-0 z-10 bg-white/95 backdrop-blur-md border-b border-border-default px-8 py-3.5 flex items-center justify-between shadow-xs shrink-0">
          <div className="flex items-center space-x-3">
            <button
              onClick={() => router.push('/users')}
              className="p-2 bg-surface-subtle hover:bg-surface-muted rounded-xl text-content-secondary transition-all flex items-center space-x-1.5 font-bold text-xs cursor-pointer"
            >
              <ArrowLeft className="w-4 h-4" />
              <span className="hidden sm:inline">Back to Registered Users</span>
            </button>
            <span className="text-content-muted">/</span>
            <span className="px-2.5 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-bold font-mono border border-border-brandSubtle">
              {customerId}
            </span>
            {customer?.full_name && (
              <>
                <span className="text-content-muted hidden md:inline">/</span>
                <span className="text-xs font-bold text-content-primary hidden md:inline truncate max-w-xs">
                  {customer.full_name}
                </span>
              </>
            )}
          </div>

          <div className="flex items-center space-x-3">
            <HeaderQuickSearch placeholder="Quick search orders..." />
            <button
              onClick={fetchDetail}
              disabled={isLoading}
              title="Refresh Customer Details"
              className="p-2.5 text-content-secondary hover:text-content-brand hover:bg-surface-subtle rounded-xl border border-border-default transition-all cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-content-brand' : ''}`} />
            </button>
            {customer && (
              <button
                onClick={handleOpenEdit}
                className="px-4 py-2 bg-action-speedBg hover:bg-action-speedHover text-white text-xs font-bold rounded-xl flex items-center gap-1.5 shadow-sm transition-all cursor-pointer"
              >
                <Edit3 className="w-3.5 h-3.5" />
                <span>Edit Profile</span>
              </button>
            )}
          </div>
        </header>

        {/* Content Container */}
        <main className="p-8 space-y-6 max-w-7xl mx-auto w-full">
          {/* Error Banner */}
          {error && !isLoading && (
            <div className="p-4 rounded-2xl border border-rose-200 bg-rose-50 text-rose-700 flex items-center justify-between gap-3 text-sm">
              <div className="flex items-center gap-2.5">
                <AlertCircle className="w-5 h-5 text-rose-500 shrink-0" />
                <span className="font-semibold">{error}</span>
              </div>
              <button
                onClick={fetchDetail}
                className="px-4 py-1.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-xs font-bold text-white transition-all cursor-pointer"
              >
                Retry
              </button>
            </div>
          )}

          {/* SECTION 1: Top Hero Customer Overview Banner */}
          <div className="bg-white border border-border-default rounded-2xl p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-6">
            {isLoading ? (
              <div className="flex items-center gap-4 animate-pulse w-full">
                <div className="w-14 h-14 rounded-2xl bg-surface-subtle shrink-0" />
                <div className="space-y-2 flex-1">
                  <div className="h-6 w-48 bg-surface-subtle rounded-lg" />
                  <div className="h-4 w-32 bg-surface-subtle rounded-md" />
                </div>
              </div>
            ) : customer ? (
              <>
                <div className="flex items-center gap-4">
                  <div className="w-14 h-14 rounded-2xl bg-action-speedBg text-white font-black text-xl flex items-center justify-center shadow-sm shrink-0">
                    {customer.full_name ? customer.full_name.charAt(0).toUpperCase() : 'C'}
                  </div>
                  <div>
                    <div className="flex items-center gap-2.5 flex-wrap">
                      <h1 className="text-xl sm:text-2xl font-black text-content-primary tracking-tight">
                        {customer.full_name || `Customer (${customer.phone?.slice(-4) || 'User'})`}
                      </h1>
                      <span className={`px-2.5 py-0.5 rounded-full text-2xs font-extrabold uppercase tracking-wider border ${
                        customer.is_active
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : 'bg-rose-50 text-rose-700 border-rose-200'
                      }`}>
                        {customer.is_active ? 'Active Account' : 'Suspended'}
                      </span>
                    </div>

                    <div className="flex items-center gap-3 mt-1.5 text-xs text-content-secondary flex-wrap">
                      <span className="font-mono font-bold text-content-brand">
                        {customer.phone || 'No phone recorded'}
                      </span>
                      {customer.email && (
                        <>
                          <span className="text-content-muted">•</span>
                          <span className="text-content-muted">{customer.email}</span>
                        </>
                      )}
                      <span className="text-content-muted">•</span>
                      <span className="text-content-muted">
                        Member since {formatDate(customer.created_at)}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2.5 flex-wrap">
                  <span className={`px-3 py-1.5 rounded-xl text-xs font-extrabold uppercase tracking-wider border flex items-center gap-1.5 ${getTierBadge(customer.tier)}`}>
                    <Award className="w-3.5 h-3.5 text-amber-500" />
                    <span>{customer.tier || 'STANDARD'} Tier</span>
                  </span>

                  {customer.phone && (
                    <a
                      href={`tel:${customer.phone}`}
                      className="px-3 py-1.5 rounded-xl bg-surface-subtle hover:bg-surface-muted text-content-primary border border-border-default text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer"
                      title="Call customer directly"
                    >
                      <PhoneCall className="w-3.5 h-3.5 text-content-brand" />
                      <span>Call</span>
                    </a>
                  )}

                  <button
                    onClick={() => copyToClipboard(customer.id, 'cust_id_banner')}
                    className="px-3 py-1.5 rounded-xl bg-surface-subtle hover:bg-surface-muted text-content-primary border border-border-default text-xs font-mono font-bold flex items-center gap-1.5 transition-all cursor-pointer"
                    title="Copy Customer ID"
                  >
                    {copiedKey === 'cust_id_banner' ? (
                      <Check className="w-3.5 h-3.5 text-content-brand" />
                    ) : (
                      <Copy className="w-3.5 h-3.5 text-content-muted" />
                    )}
                    <span>{customer.id}</span>
                  </button>
                </div>
              </>
            ) : (
              <div className="py-4 text-content-secondary text-sm font-bold">
                Customer record not found.
              </div>
            )}
          </div>

          {/* SECTION 2: Financial & Order Analytics */}
          <section className="bg-white border border-border-default rounded-2xl p-6 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-border-default pb-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-blue-50 border border-blue-200 text-blue-600 flex items-center justify-center font-bold">
                  <TrendingUp className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-base font-extrabold text-content-primary">
                    Financial &amp; Order Analytics
                  </h2>
                  <p className="text-2xs text-content-muted mt-0.5">
                    Real-time transactional lifetime value and historical delivery metrics
                  </p>
                </div>
              </div>
              <span className="text-2xs font-mono font-bold px-2.5 py-1 rounded-full bg-surface-subtle text-content-secondary border border-border-default">
                LTV Intelligence
              </span>
            </div>

            {isLoading ? (
              <AnalyticsSectionSkeleton />
            ) : (
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="p-4 rounded-xl bg-surface-subtle border border-border-default">
                  <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">
                    Realized Spend
                  </span>
                  <span className="text-2xl font-black text-content-brand mt-1 block font-mono">
                    ₹{Number(analytics?.realizedSpend ?? customer?.total_spend ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </span>
                  <span className="text-2xs text-content-secondary mt-1 block">
                    Delivered &amp; settled revenue
                  </span>
                </div>

                <div className="p-4 rounded-xl bg-surface-subtle border border-border-default">
                  <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">
                    Gross Order Value
                  </span>
                  <span className="text-2xl font-black text-content-primary mt-1 block font-mono">
                    ₹{Number(analytics?.grossOrderValue ?? customer?.total_spend ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </span>
                  <span className="text-2xs text-content-secondary mt-1 block">
                    Total order volume booked
                  </span>
                </div>

                <div className="p-4 rounded-xl bg-surface-subtle border border-border-default">
                  <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">
                    Total Orders
                  </span>
                  <span className="text-2xl font-black text-content-primary mt-1 block font-mono">
                    {analytics?.totalOrders ?? orders.length}
                  </span>
                  <span className="text-2xs text-content-secondary mt-1 block">
                    <strong className="text-content-brand">{analytics?.deliveredOrders ?? 0}</strong> delivered • <strong className="text-rose-600">{analytics?.cancelledOrders ?? 0}</strong> cancelled
                  </span>
                </div>

                <div className="p-4 rounded-xl bg-surface-subtle border border-border-default">
                  <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">
                    Average Order Value
                  </span>
                  <span className="text-2xl font-black text-content-primary mt-1 block font-mono">
                    ₹{Number(analytics?.avgOrderValue ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </span>
                  <span className="text-2xs text-content-secondary mt-1 block">
                    Per successful order
                  </span>
                </div>
              </div>
            )}
          </section>

          {/* SECTION 3: Account & Identity Details */}
          <section className="bg-white border border-border-default rounded-2xl p-6 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-border-default pb-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-600 flex items-center justify-center font-bold">
                  <User className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-base font-extrabold text-content-primary">
                    Identity &amp; Account Profile
                  </h2>
                  <p className="text-2xs text-content-muted mt-0.5">
                    Verified contact information and credentials
                  </p>
                </div>
              </div>
              <span className="text-2xs font-mono font-bold px-2.5 py-1 rounded-full bg-surface-subtle text-content-secondary border border-border-default">
                ID: {customer?.id || customerId}
              </span>
            </div>

            {isLoading ? (
              <ProfileSectionSkeleton />
            ) : customer ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
                {/* Customer ID */}
                <div className="p-4 rounded-xl bg-surface-canvas border border-border-default flex items-center justify-between">
                  <div>
                    <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">Customer ID</span>
                    <span className="text-xs font-mono font-bold text-content-primary break-all mt-0.5 block">{customer.id}</span>
                  </div>
                  <button
                    onClick={() => copyToClipboard(customer.id, 'cust_id')}
                    title="Copy Customer ID"
                    className="p-1.5 rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-subtle transition-colors cursor-pointer"
                  >
                    {copiedKey === 'cust_id' ? <Check className="w-3.5 h-3.5 text-content-brand" /> : <Copy className="w-3.5 h-3.5" />}
                  </button>
                </div>

                {/* Contact Mobile */}
                <div className="p-4 rounded-xl bg-surface-canvas border border-border-default flex items-center justify-between">
                  <div>
                    <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">Contact Mobile</span>
                    <span className="text-xs font-mono font-bold text-content-brand mt-0.5 block">{customer.phone || '—'}</span>
                  </div>
                  {customer.phone && (
                    <div className="flex items-center gap-1">
                      <a
                        href={`tel:${customer.phone}`}
                        title="Call Customer"
                        className="p-1.5 rounded-lg text-content-muted hover:text-content-brand hover:bg-surface-subtle transition-colors cursor-pointer"
                      >
                        <PhoneCall className="w-3.5 h-3.5" />
                      </a>
                      <button
                        onClick={() => copyToClipboard(customer.phone, 'cust_phone')}
                        title="Copy Mobile"
                        className="p-1.5 rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-subtle transition-colors cursor-pointer"
                      >
                        {copiedKey === 'cust_phone' ? <Check className="w-3.5 h-3.5 text-content-brand" /> : <Copy className="w-3.5 h-3.5" />}
                      </button>
                    </div>
                  )}
                </div>

                {/* Email Address */}
                <div className="p-4 rounded-xl bg-surface-canvas border border-border-default">
                  <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">Email Address</span>
                  <span className="text-xs font-medium text-content-primary truncate block mt-0.5">
                    {customer.email || 'None on file'}
                  </span>
                </div>

                {/* Member Since */}
                <div className="p-4 rounded-xl bg-surface-canvas border border-border-default">
                  <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">Account Created</span>
                  <span className="text-xs font-medium text-content-primary block mt-0.5">
                    {formatDateTime(customer.created_at)}
                  </span>
                </div>

                {/* Last Active */}
                <div className="p-4 rounded-xl bg-surface-canvas border border-border-default">
                  <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">Last Active / Order</span>
                  <span className="text-xs font-medium text-content-primary block mt-0.5">
                    {analytics?.lastOrderDate ? formatDateTime(analytics.lastOrderDate) : 'No orders recorded yet'}
                  </span>
                </div>

                {/* Addresses Count */}
                <div className="p-4 rounded-xl bg-surface-canvas border border-border-default flex items-center justify-between">
                  <div>
                    <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">Saved Delivery Addresses</span>
                    <span className="text-xs font-bold text-content-primary block mt-0.5">{addresses.length} registered</span>
                  </div>
                  <span className="px-2.5 py-1 rounded-lg bg-surface-subtle text-content-secondary text-xs font-mono font-bold border border-border-default">
                    {addresses.length}
                  </span>
                </div>
              </div>
            ) : null}
          </section>

          {/* SECTION 4: Registered Delivery Addresses */}
          <section className="bg-white border border-border-default rounded-2xl p-6 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-border-default pb-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-amber-50 border border-amber-200 text-amber-600 flex items-center justify-center font-bold">
                  <MapPin className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-base font-extrabold text-content-primary">
                    Registered Delivery Addresses
                  </h2>
                  <p className="text-2xs text-content-muted mt-0.5">
                    Geo-coordinates and delivery locations saved by the customer
                  </p>
                </div>
              </div>
              <span className="px-2.5 py-1 rounded-full bg-surface-subtle border border-border-default text-xs font-mono font-bold text-content-secondary">
                {addresses.length} saved
              </span>
            </div>

            {isLoading ? (
              <AddressesSectionSkeleton />
            ) : addresses.length === 0 ? (
              <div className="p-8 text-center rounded-xl bg-surface-subtle border border-border-default">
                <MapPin className="w-8 h-8 text-content-muted mx-auto mb-2" />
                <p className="text-sm font-bold text-content-primary">No delivery addresses registered yet</p>
                <p className="text-xs text-content-secondary mt-1">
                  Addresses added by the customer in their mobile application will appear here instantly.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {addresses.map((addr, idx) => (
                  <div
                    key={addr.id || idx}
                    className={`p-4 rounded-xl border transition-all ${
                      addr.is_default
                        ? 'bg-surface-brandSubtle border-border-brandSubtle ring-1 ring-border-brandSubtle'
                        : 'bg-white border-border-default hover:border-border-muted hover:shadow-xs'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <span className="px-2.5 py-0.5 rounded-full text-2xs font-extrabold uppercase tracking-wider bg-surface-subtle border border-border-default text-content-secondary">
                          {addr.address_type || 'HOME'}
                        </span>
                        {addr.is_default && (
                          <span className="px-2 py-0.5 rounded-full text-2xs font-extrabold uppercase tracking-wider bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1">
                            <Check className="w-3 h-3" />
                            <span>Default</span>
                          </span>
                        )}
                      </div>
                      {addr.contact_phone && (
                        <span className="text-2xs font-mono font-bold text-content-secondary">
                          {addr.contact_phone}
                        </span>
                      )}
                    </div>

                    <p className="text-sm font-bold text-content-primary leading-snug">
                      {addr.address_line}
                    </p>

                    <p className="text-xs text-content-secondary mt-1">
                      {[addr.city || 'Rewari', addr.postal_code].filter(Boolean).join(' • PIN ')}
                    </p>

                    {addr.latitude && addr.longitude && (
                      <div className="mt-3 pt-2.5 border-t border-border-default flex items-center justify-between text-2xs font-mono text-content-secondary">
                        <div className="flex items-center gap-1.5">
                          <MapPin className="w-3.5 h-3.5 text-amber-500" />
                          <span>{Number(addr.latitude).toFixed(4)}, {Number(addr.longitude).toFixed(4)}</span>
                        </div>
                        <a
                          href={`https://maps.google.com/?q=${addr.latitude},${addr.longitude}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-content-brand hover:underline font-sans font-bold flex items-center gap-1 cursor-pointer"
                        >
                          <span>Open Map</span>
                          <ExternalLink className="w-3 h-3" />
                        </a>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* SECTION 5: Order History & Fulfillment Log */}
          <section className="bg-white border border-border-default rounded-2xl p-6 shadow-xs space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border-default pb-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-purple-50 border border-purple-200 text-purple-600 flex items-center justify-center font-bold">
                  <ShoppingBag className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-base font-extrabold text-content-primary">
                    Order History &amp; Fulfillment Log
                  </h2>
                  <p className="text-2xs text-content-muted mt-0.5">
                    Orders placed, live fulfillment states, and itemized receipts
                  </p>
                </div>
              </div>

              {/* Status Filter Buttons */}
              <div className="flex items-center gap-1 bg-surface-subtle p-1 rounded-xl border border-border-default text-xs">
                {(['ALL', 'DELIVERED', 'ACTIVE', 'CANCELLED'] as const).map((status) => (
                  <button
                    key={status}
                    onClick={() => setOrderFilter(status)}
                    className={`px-3 py-1 rounded-lg font-bold text-2xs transition-all cursor-pointer ${
                      orderFilter === status
                        ? 'bg-white text-content-primary shadow-xs border border-border-default'
                        : 'text-content-secondary hover:text-content-primary'
                    }`}
                  >
                    {status}
                  </button>
                ))}
              </div>
            </div>

            {isLoading ? (
              <OrdersSectionSkeleton />
            ) : filteredOrders.length === 0 ? (
              <div className="p-8 text-center rounded-xl bg-surface-subtle border border-border-default">
                <ShoppingBag className="w-8 h-8 text-content-muted mx-auto mb-2" />
                <p className="text-sm font-bold text-content-primary">No orders found</p>
                <p className="text-xs text-content-secondary mt-1">
                  {orderFilter === 'ALL'
                    ? 'This customer has not placed any orders yet.'
                    : `No orders matching filter "${orderFilter}".`}
                </p>
              </div>
            ) : (
              <div className="space-y-3.5">
                {filteredOrders.map((order) => {
                  const itemsCount = order.items?.reduce((s, i) => s + (i.quantity || i.qty || 1), 0) || order.items?.length || 0;
                  const orderIdKey = order.order_id || order.id;

                  return (
                    <div
                      key={order.id}
                      className="p-4 sm:p-5 rounded-xl border border-border-default bg-surface-canvas hover:bg-surface-subtle/50 transition-all space-y-3.5"
                    >
                      {/* Header Row */}
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 pb-3 border-b border-border-default">
                        <div className="flex items-center gap-2.5 flex-wrap">
                          <Link
                            href={`/orders/${order.id}`}
                            className="font-mono text-xs font-black text-content-brand hover:underline flex items-center gap-1"
                          >
                            <span>#{orderIdKey}</span>
                            <ArrowUpRight className="w-3.5 h-3.5" />
                          </Link>
                          <button
                            onClick={() => copyToClipboard(orderIdKey, `order_${order.id}`)}
                            className="p-1 rounded text-content-muted hover:text-content-primary cursor-pointer"
                            title="Copy Order ID"
                          >
                            {copiedKey === `order_${order.id}` ? (
                              <Check className="w-3.5 h-3.5 text-content-brand" />
                            ) : (
                              <Copy className="w-3.5 h-3.5" />
                            )}
                          </button>
                          <span className="text-xs text-content-muted">
                            {formatDateTime(order.created_at)}
                          </span>
                        </div>

                        <div className="flex items-center gap-2 flex-wrap">
                          <span className={`px-2.5 py-0.5 rounded-full text-2xs font-extrabold uppercase tracking-wider border ${getOrderStatusBadge(order.status)}`}>
                            {order.status}
                          </span>

                          <span className="px-2.5 py-0.5 rounded-full text-2xs font-bold uppercase tracking-wider bg-surface-subtle border border-border-default text-content-secondary">
                            {order.payment_method || (order.is_cod ? 'COD' : 'ONLINE')}
                          </span>

                          <Link
                            href={`/orders/${order.id}`}
                            className="px-3 py-1 rounded-lg bg-white border border-border-default hover:border-border-brandSubtle text-2xs font-bold text-content-primary shadow-xs transition-all flex items-center gap-1 cursor-pointer"
                          >
                            <span>View Dispatch</span>
                            <ChevronRight className="w-3 h-3 text-content-muted" />
                          </Link>
                        </div>
                      </div>

                      {/* Items List */}
                      {order.items && order.items.length > 0 && (
                        <div className="space-y-2">
                          <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider block">
                            Ordered Items ({itemsCount})
                          </span>
                          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                            {order.items.map((item, iIdx) => (
                              <div
                                key={iIdx}
                                className="p-2.5 rounded-lg bg-white border border-border-default flex items-center justify-between text-xs"
                              >
                                <div className="truncate mr-2">
                                  <p className="font-bold text-content-primary truncate">{item.title || item.name || 'Product Item'}</p>
                                  <p className="text-2xs text-content-muted font-mono">Qty: {item.quantity || item.qty || 1}</p>
                                </div>
                                <span className="font-mono font-bold text-content-primary shrink-0">
                                  ₹{Number(item.total || ((item.price || item.unit_price || 0) * (item.quantity || item.qty || 1))).toFixed(0)}
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Footer: Delivery PIN & Total */}
                      <div className="pt-2 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs border-t border-border-default">
                        <div className="flex items-center gap-3 flex-wrap">
                          {order.delivery_pin && (
                            <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-700 text-xs font-mono font-bold">
                              <Key className="w-3.5 h-3.5 text-emerald-600" />
                              <span>PIN: {order.delivery_pin}</span>
                            </div>
                          )}
                          {order.delivery_address?.address_line && (
                            <span className="text-content-secondary truncate max-w-sm text-2xs">
                              📍 {order.delivery_address.address_line}
                            </span>
                          )}
                        </div>

                        <div className="flex items-center gap-2 self-end sm:self-auto">
                          <span className="text-content-muted text-xs">Total Amount:</span>
                          <span className="text-base font-black text-content-primary font-mono">
                            ₹{Number(order.total_amount).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          {/* SECTION 6: Active Cart & Live Intent */}
          <section className="bg-white border border-border-default rounded-2xl p-6 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-border-default pb-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-pink-50 border border-pink-200 text-pink-600 flex items-center justify-center font-bold">
                  <ShoppingCart className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-base font-extrabold text-content-primary">
                    Active Cart &amp; Live Intent
                  </h2>
                  <p className="text-2xs text-content-muted mt-0.5">
                    Uncommitted items currently residing in customer basket
                  </p>
                </div>
              </div>
              {cart?.updated_at && (
                <span className="text-2xs text-content-muted font-mono">
                  Synced: {formatDateTime(cart.updated_at)}
                </span>
              )}
            </div>

            {isLoading ? (
              <CartSectionSkeleton />
            ) : !cart || !cart.items || cart.items.length === 0 ? (
              <div className="p-8 text-center rounded-xl bg-surface-subtle border border-border-default">
                <ShoppingCart className="w-8 h-8 text-content-muted mx-auto mb-2" />
                <p className="text-sm font-bold text-content-primary">Customer cart is currently empty</p>
                <p className="text-xs text-content-secondary mt-1">
                  When this customer adds items in their app, they will appear here in real time.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {cart.items.map((item, idx) => (
                  <div
                    key={idx}
                    className="p-3.5 rounded-xl bg-surface-canvas border border-border-default flex items-center justify-between text-xs"
                  >
                    <div className="truncate mr-3">
                      <p className="font-bold text-content-primary truncate">{item.title || item.name || 'Cart Item'}</p>
                      <p className="text-2xs text-content-muted font-mono mt-0.5">Quantity: {item.quantity || 1}</p>
                    </div>
                    {item.price != null && (
                      <span className="font-mono font-bold text-content-primary shrink-0">
                        ₹{Number(item.price * (item.quantity || 1)).toFixed(0)}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* SECTION 7: Prescriptions & Health Records */}
          <section className="bg-white border border-border-default rounded-2xl p-6 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-border-default pb-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-teal-50 border border-teal-200 text-teal-600 flex items-center justify-center font-bold">
                  <FileText className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-base font-extrabold text-content-primary">
                    Prescriptions &amp; Clinical Records
                  </h2>
                  <p className="text-2xs text-content-muted mt-0.5">
                    Uploaded medical prescriptions for pharmacist verification
                  </p>
                </div>
              </div>
              <span className="px-2.5 py-1 rounded-full bg-surface-subtle border border-border-default text-xs font-mono font-bold text-content-secondary">
                {prescriptions.length} records
              </span>
            </div>

            {isLoading ? (
              <PrescriptionsSectionSkeleton />
            ) : prescriptions.length === 0 ? (
              <div className="p-8 text-center rounded-xl bg-surface-subtle border border-border-default">
                <FileText className="w-8 h-8 text-content-muted mx-auto mb-2" />
                <p className="text-sm font-bold text-content-primary">No prescriptions uploaded by customer</p>
                <p className="text-xs text-content-secondary mt-1">Prescription uploads and doctor notes will be recorded here.</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {prescriptions.map((rx) => (
                  <div
                    key={rx.id}
                    className="p-4 rounded-xl bg-surface-canvas border border-border-default space-y-2 text-xs"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-mono font-bold text-content-primary">#{rx.id.slice(-8)}</span>
                      <span className={`px-2 py-0.5 rounded-full text-2xs font-extrabold uppercase tracking-wider border ${
                        rx.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' :
                        rx.status === 'REJECTED' ? 'bg-rose-50 text-rose-700 border-rose-200' :
                        'bg-amber-50 text-amber-700 border-amber-200'
                      }`}>
                        {rx.status}
                      </span>
                    </div>

                    <p className="font-bold text-content-primary">Patient: {rx.patient_name || 'Customer'}</p>
                    {rx.doctor_name && <p className="text-content-secondary">Doctor: {rx.doctor_name}</p>}
                    {rx.note && <p className="text-content-muted italic">"{rx.note}"</p>}
                    <p className="text-2xs text-content-muted font-mono">Uploaded: {formatDateTime(rx.created_at)}</p>
                  </div>
                ))}
              </div>
            )}
          </section>
        </main>
      </div>

      {/* Edit Customer Modal */}
      {isEditModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="w-full max-w-lg bg-white border border-border-default rounded-2xl shadow-2xl p-6 space-y-5 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-3 border-b border-border-default">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-xl bg-action-speedBg/10 text-action-speed flex items-center justify-center">
                  <Edit3 className="w-4 h-4" />
                </div>
                <h3 className="text-base font-extrabold text-content-primary">Edit Customer Information</h3>
              </div>
              <button
                onClick={() => setIsEditModalOpen(false)}
                className="p-1 rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-subtle transition-colors cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {saveError && (
              <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold">
                {saveError}
              </div>
            )}

            {saveSuccess && (
              <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-700 text-xs font-semibold flex items-center gap-2">
                <Check className="w-4 h-4 text-emerald-600" />
                <span>{saveSuccess}</span>
              </div>
            )}

            <form onSubmit={handleSaveCustomer} className="space-y-4">
              <div>
                <label className="text-xs font-bold text-content-primary block mb-1">Full Name</label>
                <input
                  type="text"
                  value={editForm.fullName}
                  onChange={(e) => setEditForm({ ...editForm, fullName: e.target.value })}
                  placeholder="e.g. Rahul Sharma"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-surface-subtle border border-border-default text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-border-brand"
                />
              </div>

              <div>
                <label className="text-xs font-bold text-content-primary block mb-1">Phone Number</label>
                <input
                  type="text"
                  value={editForm.phone}
                  onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
                  placeholder="e.g. +919817916180"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-surface-subtle border border-border-default text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-border-brand font-mono"
                />
              </div>

              <div>
                <label className="text-xs font-bold text-content-primary block mb-1">Email Address</label>
                <input
                  type="email"
                  value={editForm.email}
                  onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
                  placeholder="e.g. user@commerceos.io"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-surface-subtle border border-border-default text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-border-brand"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-bold text-content-primary block mb-1">Customer Tier</label>
                  <select
                    value={editForm.tier}
                    onChange={(e) => setEditForm({ ...editForm, tier: e.target.value })}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-surface-subtle border border-border-default text-sm text-content-primary focus:outline-none focus:border-border-brand"
                  >
                    <option value="STANDARD">STANDARD</option>
                    <option value="GOLD">GOLD</option>
                    <option value="PLATINUM">PLATINUM</option>
                    <option value="VIP">VIP</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-bold text-content-primary block mb-1">Account Status</label>
                  <select
                    value={editForm.isActive ? 'ACTIVE' : 'SUSPENDED'}
                    onChange={(e) => setEditForm({ ...editForm, isActive: e.target.value === 'ACTIVE' })}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-surface-subtle border border-border-default text-sm text-content-primary focus:outline-none focus:border-border-brand"
                  >
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="SUSPENDED">SUSPENDED</option>
                  </select>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-border-default">
                <button
                  type="button"
                  onClick={() => setIsEditModalOpen(false)}
                  className="px-4 py-2 rounded-xl bg-surface-subtle hover:bg-surface-muted text-xs font-bold text-content-secondary transition-all cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="px-5 py-2 rounded-xl bg-action-speedBg hover:bg-action-speedHover text-xs font-bold text-white flex items-center gap-1.5 transition-all shadow-sm disabled:opacity-50 cursor-pointer"
                >
                  {isSaving ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
                  <span>{isSaving ? 'Saving Changes...' : 'Save Changes'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

// -------------------------------------------------------------
// SKELETON LOADERS MATCHING CANONICAL LIGHT THEME DESIGN SYSTEM
// -------------------------------------------------------------

function ProfileSectionSkeleton() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5 animate-pulse">
      {[1, 2, 3, 4, 5, 6].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-surface-subtle border border-border-default space-y-2">
          <div className="h-3 w-20 bg-neutral-200/80 rounded" />
          <div className="h-4 w-36 bg-neutral-300/80 rounded" />
        </div>
      ))}
    </div>
  );
}

function AnalyticsSectionSkeleton() {
  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 animate-pulse">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-surface-subtle border border-border-default space-y-2.5">
          <div className="h-3 w-24 bg-neutral-200/80 rounded" />
          <div className="h-6 w-28 bg-neutral-300/80 rounded-md" />
          <div className="h-3 w-32 bg-neutral-200/60 rounded" />
        </div>
      ))}
    </div>
  );
}

function AddressesSectionSkeleton() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 animate-pulse">
      {[1, 2].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-surface-subtle border border-border-default space-y-2.5">
          <div className="flex items-center justify-between">
            <div className="h-4 w-16 bg-neutral-200/80 rounded-full" />
            <div className="h-3 w-24 bg-neutral-200/60 rounded" />
          </div>
          <div className="h-4 w-full bg-neutral-300/80 rounded" />
          <div className="h-3 w-40 bg-neutral-200/70 rounded" />
        </div>
      ))}
    </div>
  );
}

function OrdersSectionSkeleton() {
  return (
    <div className="space-y-3.5 animate-pulse">
      {[1, 2, 3].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-surface-subtle border border-border-default space-y-3">
          <div className="flex items-center justify-between">
            <div className="h-4 w-32 bg-neutral-300/80 rounded" />
            <div className="h-4 w-20 bg-neutral-200/80 rounded-full" />
          </div>
          <div className="h-3 w-48 bg-neutral-200/70 rounded" />
          <div className="h-5 w-24 bg-neutral-300/80 rounded self-end" />
        </div>
      ))}
    </div>
  );
}

function CartSectionSkeleton() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 animate-pulse">
      {[1, 2, 3].map((i) => (
        <div key={i} className="p-3.5 rounded-xl bg-surface-subtle border border-border-default space-y-2">
          <div className="h-3.5 w-32 bg-neutral-300/80 rounded" />
          <div className="h-3 w-16 bg-neutral-200/70 rounded" />
        </div>
      ))}
    </div>
  );
}

function PrescriptionsSectionSkeleton() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 animate-pulse">
      {[1, 2].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-surface-subtle border border-border-default space-y-2">
          <div className="h-4 w-28 bg-neutral-300/80 rounded" />
          <div className="h-3 w-40 bg-neutral-200/70 rounded" />
        </div>
      ))}
    </div>
  );
}

function formatDate(isoString?: string | null): string {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return d.toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    });
  } catch {
    return isoString;
  }
}

function formatDateTime(isoString?: string | null): string {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return d.toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  } catch {
    return isoString;
  }
}
