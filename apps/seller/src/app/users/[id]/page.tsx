'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
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
import SellerAuthGuard from '@/components/SellerAuthGuard';
import {
  User, Phone, Mail, MapPin, Calendar, Clock,
  ArrowLeft, RefreshCw, Edit3, Shield, CheckCircle2,
  XCircle, AlertCircle, ShoppingBag, ShoppingCart,
  FileText, Copy, Check, ExternalLink, ChevronRight,
  TrendingUp, Award, Layers, Hash, CreditCard, Key,
  Sparkles, Save, X
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
  const [orderFilter, setOrderFilter] = useState<string>('ALL');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

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
        setTimeout(() => setIsEditModalOpen(false), 800);
      } else {
        setSaveError(res.error || 'Failed to update customer.');
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

  const filteredOrders = orders.filter((o) => {
    if (orderFilter === 'ALL') return true;
    if (orderFilter === 'DELIVERED') return o.status?.toUpperCase() === 'DELIVERED';
    if (orderFilter === 'CANCELLED') return o.status?.toUpperCase() === 'CANCELLED';
    if (orderFilter === 'ACTIVE') return !['DELIVERED', 'CANCELLED'].includes(o.status?.toUpperCase());
    return true;
  });

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 font-sans antialiased pb-24">
      {/* Top Sticky Header */}
      <div className="sticky top-0 z-30 bg-slate-900/90 backdrop-blur-md border-b border-slate-800 px-4 sm:px-6 lg:px-8 py-3.5 transition-all">
        <div className="max-w-7xl mx-auto flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Link
              href="/users"
              className="p-2 -ml-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors flex items-center gap-1.5 text-xs font-semibold"
            >
              <ArrowLeft className="w-4 h-4" />
              <span className="hidden sm:inline">Back to Users</span>
            </Link>
            <div className="h-4 w-px bg-slate-800" />
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Link href="/users" className="hover:text-slate-200 transition-colors">Users</Link>
              <ChevronRight className="w-3.5 h-3.5 text-slate-600" />
              <span className="text-slate-200 font-medium truncate max-w-[200px]">
                {customer?.full_name || customerId}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={fetchDetail}
              disabled={isLoading}
              title="Refresh Customer Profile"
              className="px-3 py-1.5 rounded-lg border border-slate-700 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 flex items-center gap-1.5 transition-all disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-emerald-400' : ''}`} />
              <span className="hidden sm:inline">Refresh</span>
            </button>

            {customer && (
              <button
                onClick={handleOpenEdit}
                className="px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold flex items-center gap-1.5 transition-all shadow-sm shadow-emerald-950/40"
              >
                <Edit3 className="w-3.5 h-3.5" />
                <span>Edit Profile</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Main Container */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 space-y-6">
        {/* Error State Banner */}
        {error && !isLoading && (
          <div className="p-4 rounded-xl border border-red-800/60 bg-red-950/40 text-red-300 flex items-center justify-between gap-3 text-sm">
            <div className="flex items-center gap-2.5">
              <AlertCircle className="w-5 h-5 text-red-400 shrink-0" />
              <span>{error}</span>
            </div>
            <button
              onClick={fetchDetail}
              className="px-3 py-1 rounded-lg bg-red-900/60 hover:bg-red-900 text-xs font-bold text-red-200"
            >
              Retry
            </button>
          </div>
        )}

        {/* SECTION 1: Personal Details & Identity */}
        <section className="bg-slate-800/80 rounded-2xl border border-slate-700/80 p-5 sm:p-6 shadow-xl backdrop-blur-sm">
          <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-700/60">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold">
                <User className="w-4 h-4" />
              </div>
              <h2 className="text-sm sm:text-base font-bold text-white tracking-wide">
                Personal Details & Account Profile
              </h2>
            </div>
            {isLoading ? (
              <div className="h-5 w-24 bg-slate-700 rounded-md animate-pulse" />
            ) : (
              <span className="text-xs text-slate-400 font-mono">
                ID: {customer?.id || customerId}
              </span>
            )}
          </div>

          {isLoading ? (
            <ProfileSectionSkeleton />
          ) : customer ? (
            <div className="space-y-6">
              {/* Header Hero Row */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 rounded-xl bg-slate-900/50 border border-slate-700/40">
                <div className="flex items-center gap-3.5">
                  <div className="w-12 h-12 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-500 text-white font-black text-lg flex items-center justify-center shadow-lg shadow-emerald-950/30">
                    {customer.full_name ? customer.full_name.charAt(0).toUpperCase() : 'U'}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h1 className="text-lg sm:text-xl font-black text-white">
                        {customer.full_name || 'Customer ' + (customer.phone?.slice(-4) || 'User')}
                      </h1>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider ${
                        customer.is_active ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30' : 'bg-red-500/15 text-red-400 border border-red-500/30'
                      }`}>
                        {customer.is_active ? 'Active' : 'Suspended'}
                      </span>
                    </div>
                    <p className="text-xs text-slate-400 font-mono mt-0.5">
                      {customer.phone || 'No phone recorded'}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <span className={`px-3 py-1 rounded-lg text-xs font-bold uppercase tracking-wider flex items-center gap-1.5 ${
                    customer.tier === 'VIP' ? 'bg-purple-500/20 text-purple-300 border border-purple-500/30' :
                    customer.tier === 'PLATINUM' ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30' :
                    customer.tier === 'GOLD' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30' :
                    'bg-slate-700 text-slate-300 border border-slate-600'
                  }`}>
                    <Award className="w-3.5 h-3.5" />
                    <span>{customer.tier || 'STANDARD'} Tier</span>
                  </span>
                </div>
              </div>

              {/* Information Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
                {/* Customer ID */}
                <div className="p-3.5 rounded-xl bg-slate-900/40 border border-slate-700/40 flex items-center justify-between">
                  <div>
                    <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">Customer ID</span>
                    <span className="text-xs font-mono font-bold text-slate-200 break-all">{customer.id}</span>
                  </div>
                  <button
                    onClick={() => copyToClipboard(customer.id, 'cust_id')}
                    title="Copy Customer ID"
                    className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                  >
                    {copiedKey === 'cust_id' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  </button>
                </div>

                {/* Primary Phone */}
                <div className="p-3.5 rounded-xl bg-slate-900/40 border border-slate-700/40 flex items-center justify-between">
                  <div>
                    <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">Contact Mobile</span>
                    <span className="text-xs font-mono font-bold text-emerald-400">{customer.phone || '—'}</span>
                  </div>
                  {customer.phone && (
                    <button
                      onClick={() => copyToClipboard(customer.phone, 'cust_phone')}
                      title="Copy Mobile"
                      className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                    >
                      {copiedKey === 'cust_phone' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                  )}
                </div>

                {/* Registered Email */}
                <div className="p-3.5 rounded-xl bg-slate-900/40 border border-slate-700/40">
                  <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">Email Address</span>
                  <span className="text-xs font-medium text-slate-200 truncate block mt-0.5">
                    {customer.email || 'None on file'}
                  </span>
                </div>

                {/* Account Created Date */}
                <div className="p-3.5 rounded-xl bg-slate-900/40 border border-slate-700/40">
                  <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">Member Since</span>
                  <span className="text-xs font-medium text-slate-200 block mt-0.5">
                    {formatDateTime(customer.created_at)}
                  </span>
                </div>

                {/* Last Order Date */}
                <div className="p-3.5 rounded-xl bg-slate-900/40 border border-slate-700/40">
                  <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">Last Active / Order</span>
                  <span className="text-xs font-medium text-slate-200 block mt-0.5">
                    {analytics?.lastOrderDate ? formatDateTime(analytics.lastOrderDate) : 'No orders recorded yet'}
                  </span>
                </div>

                {/* Total Address Count */}
                <div className="p-3.5 rounded-xl bg-slate-900/40 border border-slate-700/40 flex items-center justify-between">
                  <div>
                    <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">Saved Delivery Addresses</span>
                    <span className="text-xs font-bold text-slate-200 block mt-0.5">{addresses.length} registered</span>
                  </div>
                  <span className="p-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs font-mono font-bold">
                    {addresses.length}
                  </span>
                </div>
              </div>
            </div>
          ) : (
            <div className="py-6 text-center text-slate-400 text-xs">Customer profile not found</div>
          )}
        </section>

        {/* SECTION 2: Customer Financial & Order Analytics */}
        <section className="bg-slate-800/80 rounded-2xl border border-slate-700/80 p-5 sm:p-6 shadow-xl backdrop-blur-sm">
          <div className="flex items-center gap-2.5 mb-4 pb-3 border-b border-slate-700/60">
            <div className="w-8 h-8 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400 flex items-center justify-center font-bold">
              <TrendingUp className="w-4 h-4" />
            </div>
            <h2 className="text-sm sm:text-base font-bold text-white tracking-wide">
              Financial & Order Analytics
            </h2>
          </div>

          {isLoading ? (
            <AnalyticsSectionSkeleton />
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5">
              <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/40">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">Realized Spend</span>
                <span className="text-xl sm:text-2xl font-black text-emerald-400 mt-1 block">
                  ₹{Number(analytics?.realizedSpend ?? customer?.total_spend ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                </span>
                <span className="text-[11px] text-slate-400 mt-1 block">Delivered revenue collected</span>
              </div>

              <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/40">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">Gross Order Value</span>
                <span className="text-xl sm:text-2xl font-black text-slate-100 mt-1 block">
                  ₹{Number(analytics?.grossOrderValue ?? customer?.total_spend ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                </span>
                <span className="text-[11px] text-slate-400 mt-1 block">Total intent booked</span>
              </div>

              <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/40">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">Total Orders</span>
                <span className="text-xl sm:text-2xl font-black text-slate-100 mt-1 block">
                  {analytics?.totalOrders ?? orders.length}
                </span>
                <span className="text-[11px] text-emerald-400 mt-1 block font-medium">
                  {analytics?.deliveredOrders ?? 0} delivered / {analytics?.cancelledOrders ?? 0} cancelled
                </span>
              </div>

              <div className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/40">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">Average Order Value</span>
                <span className="text-xl sm:text-2xl font-black text-cyan-400 mt-1 block">
                  ₹{Number(analytics?.avgOrderValue ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                </span>
                <span className="text-[11px] text-slate-400 mt-1 block">Per successful delivery</span>
              </div>
            </div>
          )}
        </section>

        {/* SECTION 3: Registered Delivery Addresses */}
        <section className="bg-slate-800/80 rounded-2xl border border-slate-700/80 p-5 sm:p-6 shadow-xl backdrop-blur-sm">
          <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-700/60">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-400 flex items-center justify-center font-bold">
                <MapPin className="w-4 h-4" />
              </div>
              <h2 className="text-sm sm:text-base font-bold text-white tracking-wide">
                Registered Delivery Addresses
              </h2>
            </div>
            <span className="px-2.5 py-1 rounded-full bg-slate-900 border border-slate-700 text-xs font-mono font-bold text-slate-300">
              {addresses.length} saved
            </span>
          </div>

          {isLoading ? (
            <AddressesSectionSkeleton />
          ) : addresses.length === 0 ? (
            <div className="p-8 text-center rounded-xl bg-slate-900/40 border border-slate-700/40">
              <MapPin className="w-8 h-8 text-slate-600 mx-auto mb-2" />
              <p className="text-sm font-bold text-slate-300">No delivery addresses registered yet</p>
              <p className="text-xs text-slate-400 mt-1">
                Addresses added by the customer in their mobile application will immediately appear here.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {addresses.map((addr, idx) => (
                <div
                  key={addr.id || idx}
                  className={`p-4 rounded-xl border transition-all ${
                    addr.is_default
                      ? 'bg-slate-900/80 border-emerald-500/40 ring-1 ring-emerald-500/20'
                      : 'bg-slate-900/40 border-slate-700/50 hover:border-slate-600'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2.5">
                    <div className="flex items-center gap-2">
                      <span className="px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-slate-800 border border-slate-700 text-slate-200">
                        {addr.address_type || 'HOME'}
                      </span>
                      {addr.is_default && (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 flex items-center gap-1">
                          <Check className="w-3 h-3" />
                          <span>Default</span>
                        </span>
                      )}
                    </div>
                    {addr.contact_phone && (
                      <span className="text-[11px] font-mono text-slate-400">
                        {addr.contact_phone}
                      </span>
                    )}
                  </div>

                  <p className="text-sm font-bold text-slate-100 leading-snug">
                    {addr.address_line}
                  </p>

                  <p className="text-xs text-slate-400 mt-1">
                    {[addr.city || 'Rewari', addr.postal_code].filter(Boolean).join(' • PIN ')}
                  </p>

                  {addr.latitude && addr.longitude && (
                    <div className="mt-3 pt-2.5 border-t border-slate-800 flex items-center justify-between text-[11px] font-mono text-slate-400">
                      <div className="flex items-center gap-1.5">
                        <MapPin className="w-3.5 h-3.5 text-amber-400" />
                        <span>{Number(addr.latitude).toFixed(4)}, {Number(addr.longitude).toFixed(4)}</span>
                      </div>
                      <a
                        href={`https://maps.google.com/?q=${addr.latitude},${addr.longitude}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-emerald-400 hover:text-emerald-300 font-sans font-semibold flex items-center gap-1"
                      >
                        <span>Map</span>
                        <ExternalLink className="w-3 h-3" />
                      </a>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>

        {/* SECTION 4: Order History & Fulfillment Log */}
        <section className="bg-slate-800/80 rounded-2xl border border-slate-700/80 p-5 sm:p-6 shadow-xl backdrop-blur-sm">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-700/60">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold">
                <ShoppingBag className="w-4 h-4" />
              </div>
              <div>
                <h2 className="text-sm sm:text-base font-bold text-white tracking-wide">
                  Order History & Fulfillment Log
                </h2>
                <p className="text-[11px] text-slate-400">Authoritative orders placed by this customer</p>
              </div>
            </div>

            {/* Filter Tabs */}
            <div className="flex items-center gap-1 bg-slate-900/80 p-1 rounded-xl border border-slate-700 text-xs">
              {(['ALL', 'DELIVERED', 'ACTIVE', 'CANCELLED'] as const).map((status) => (
                <button
                  key={status}
                  onClick={() => setOrderFilter(status)}
                  className={`px-3 py-1 rounded-lg font-bold transition-all ${
                    orderFilter === status
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
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
            <div className="p-8 text-center rounded-xl bg-slate-900/40 border border-slate-700/40">
              <ShoppingBag className="w-8 h-8 text-slate-600 mx-auto mb-2" />
              <p className="text-sm font-bold text-slate-300">No orders found</p>
              <p className="text-xs text-slate-400 mt-1">
                {orderFilter === 'ALL'
                  ? 'This customer has not placed any orders yet.'
                  : `No orders matching filter "${orderFilter}".`}
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              {filteredOrders.map((order) => {
                const isDelivered = order.status?.toUpperCase() === 'DELIVERED';
                const isCancelled = order.status?.toUpperCase() === 'CANCELLED';
                const itemsCount = order.items?.reduce((s, i) => s + (i.quantity || i.qty || 1), 0) || order.items?.length || 0;

                return (
                  <div
                    key={order.id}
                    className="p-4 sm:p-5 rounded-xl bg-slate-900/60 border border-slate-700/60 hover:border-slate-600 transition-all space-y-3.5"
                  >
                    {/* Header Row */}
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 pb-3 border-b border-slate-800">
                      <div className="flex items-center gap-2.5">
                        <span className="font-mono text-xs font-bold text-white">
                          #{order.order_id || order.id}
                        </span>
                        <button
                          onClick={() => copyToClipboard(order.order_id || order.id, `order_${order.id}`)}
                          className="p-1 rounded text-slate-400 hover:text-white"
                        >
                          {copiedKey === `order_${order.id}` ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                        </button>
                        <span className="text-xs text-slate-400">
                          {formatDateTime(order.created_at)}
                        </span>
                      </div>

                      <div className="flex items-center gap-2">
                        <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider ${
                          isDelivered ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30' :
                          isCancelled ? 'bg-red-500/15 text-red-400 border border-red-500/30' :
                          'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                        }`}>
                          {order.status}
                        </span>

                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-slate-800 border border-slate-700 text-slate-300">
                          {order.payment_method || (order.is_cod ? 'COD' : 'ONLINE')}
                        </span>
                      </div>
                    </div>

                    {/* Middle: Items List */}
                    {order.items && order.items.length > 0 && (
                      <div className="space-y-2">
                        <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
                          Items Ordered ({itemsCount})
                        </span>
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                          {order.items.map((item, iIdx) => (
                            <div
                              key={iIdx}
                              className="p-2.5 rounded-lg bg-slate-800/40 border border-slate-700/40 flex items-center justify-between text-xs"
                            >
                              <div className="truncate mr-2">
                                <p className="font-medium text-slate-200 truncate">{item.title || item.name || 'Product Item'}</p>
                                <p className="text-[10px] text-slate-400 font-mono">Qty: {item.quantity || item.qty || 1}</p>
                              </div>
                              <span className="font-mono font-bold text-slate-100 shrink-0">
                                ₹{Number(item.total || ((item.price || item.unit_price || 0) * (item.quantity || item.qty || 1))).toFixed(0)}
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Footer Row: Security PIN & Financial Total */}
                    <div className="pt-2 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs border-t border-slate-800/80">
                      <div className="flex items-center gap-3">
                        {order.delivery_pin && (
                          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-emerald-950/60 border border-emerald-600/30 text-emerald-400 text-xs font-mono font-bold">
                            <Key className="w-3.5 h-3.5" />
                            <span>PIN: {order.delivery_pin}</span>
                          </div>
                        )}
                        {order.delivery_address?.address_line && (
                          <span className="text-slate-400 truncate max-w-xs">
                            📍 {order.delivery_address.address_line}
                          </span>
                        )}
                      </div>

                      <div className="flex items-center gap-2 self-end sm:self-auto">
                        <span className="text-slate-400 text-xs">Total Amount:</span>
                        <span className="text-base font-black text-emerald-400 font-mono">
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

        {/* SECTION 5: Active Cart & Live Intent */}
        <section className="bg-slate-800/80 rounded-2xl border border-slate-700/80 p-5 sm:p-6 shadow-xl backdrop-blur-sm">
          <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-700/60">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-purple-500/10 border border-purple-500/20 text-purple-400 flex items-center justify-center font-bold">
                <ShoppingCart className="w-4 h-4" />
              </div>
              <div>
                <h2 className="text-sm sm:text-base font-bold text-white tracking-wide">
                  Active Cart & Live Intent
                </h2>
                <p className="text-[11px] text-slate-400">Current in-progress items in customer cart</p>
              </div>
            </div>
            {cart?.updated_at && (
              <span className="text-[11px] text-slate-400 font-mono">
                Synced: {formatDateTime(cart.updated_at)}
              </span>
            )}
          </div>

          {isLoading ? (
            <CartSectionSkeleton />
          ) : !cart || !cart.items || cart.items.length === 0 ? (
            <div className="p-8 text-center rounded-xl bg-slate-900/40 border border-slate-700/40">
              <ShoppingCart className="w-8 h-8 text-slate-600 mx-auto mb-2" />
              <p className="text-sm font-bold text-slate-300">Customer cart is currently empty</p>
              <p className="text-xs text-slate-400 mt-1">
                When this customer adds items in their app, they will appear here in real time.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {cart.items.map((item, idx) => (
                  <div
                    key={idx}
                    className="p-3 rounded-xl bg-slate-900/60 border border-slate-700/60 flex items-center justify-between text-xs"
                  >
                    <div className="truncate mr-3">
                      <p className="font-bold text-slate-100 truncate">{item.title || item.name || 'Cart Item'}</p>
                      <p className="text-[10px] text-slate-400 font-mono mt-0.5">Quantity: {item.quantity || 1}</p>
                    </div>
                    {item.price != null && (
                      <span className="font-mono font-bold text-emerald-400 shrink-0">
                        ₹{Number(item.price * (item.quantity || 1)).toFixed(0)}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>

        {/* SECTION 6: Uploaded Prescriptions & Health Records */}
        <section className="bg-slate-800/80 rounded-2xl border border-slate-700/80 p-5 sm:p-6 shadow-xl backdrop-blur-sm">
          <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-700/60">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-pink-500/10 border border-pink-500/20 text-pink-400 flex items-center justify-center font-bold">
                <FileText className="w-4 h-4" />
              </div>
              <div>
                <h2 className="text-sm sm:text-base font-bold text-white tracking-wide">
                  Prescriptions & Health Documents
                </h2>
                <p className="text-[11px] text-slate-400">Uploaded medical prescriptions requiring pharmacist verification</p>
              </div>
            </div>
            <span className="px-2.5 py-1 rounded-full bg-slate-900 border border-slate-700 text-xs font-mono font-bold text-slate-300">
              {prescriptions.length} records
            </span>
          </div>

          {isLoading ? (
            <PrescriptionsSectionSkeleton />
          ) : prescriptions.length === 0 ? (
            <div className="p-8 text-center rounded-xl bg-slate-900/40 border border-slate-700/40">
              <FileText className="w-8 h-8 text-slate-600 mx-auto mb-2" />
              <p className="text-sm font-bold text-slate-300">No prescriptions uploaded by customer</p>
              <p className="text-xs text-slate-400 mt-1">Prescription uploads and doctor notes will be recorded here.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {prescriptions.map((rx) => (
                <div
                  key={rx.id}
                  className="p-4 rounded-xl bg-slate-900/60 border border-slate-700/60 space-y-2 text-xs"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-mono font-bold text-slate-200">#{rx.id.slice(-8)}</span>
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider ${
                      rx.status === 'APPROVED' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30' :
                      rx.status === 'REJECTED' ? 'bg-red-500/15 text-red-400 border border-red-500/30' :
                      'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                    }`}>
                      {rx.status}
                    </span>
                  </div>

                  <p className="font-bold text-slate-100">Patient: {rx.patient_name || 'Customer'}</p>
                  {rx.doctor_name && <p className="text-slate-400">Doctor: {rx.doctor_name}</p>}
                  {rx.note && <p className="text-slate-300 italic">"{rx.note}"</p>}
                  <p className="text-[10px] text-slate-400 font-mono">Uploaded: {formatDateTime(rx.created_at)}</p>
                </div>
              ))}
            </div>
          )}
        </section>
      </main>

      {/* Edit Customer Modal */}
      {isEditModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-lg bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl p-6 space-y-5 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <Edit3 className="w-5 h-5 text-emerald-400" />
                <h3 className="text-base font-bold text-white">Edit Customer Information</h3>
              </div>
              <button
                onClick={() => setIsEditModalOpen(false)}
                className="p-1 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {saveError && (
              <div className="p-3 rounded-lg bg-red-950/60 border border-red-800/80 text-red-300 text-xs">
                {saveError}
              </div>
            )}

            {saveSuccess && (
              <div className="p-3 rounded-lg bg-emerald-950/60 border border-emerald-800/80 text-emerald-300 text-xs flex items-center gap-2">
                <Check className="w-4 h-4 text-emerald-400" />
                <span>{saveSuccess}</span>
              </div>
            )}

            <form onSubmit={handleSaveCustomer} className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Full Name</label>
                <input
                  type="text"
                  value={editForm.fullName}
                  onChange={(e) => setEditForm({ ...editForm, fullName: e.target.value })}
                  placeholder="e.g. Rahul Sharma"
                  className="w-full px-3.5 py-2 rounded-xl bg-slate-800 border border-slate-700 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Phone Number</label>
                <input
                  type="text"
                  value={editForm.phone}
                  onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
                  placeholder="e.g. +919817916180"
                  className="w-full px-3.5 py-2 rounded-xl bg-slate-800 border border-slate-700 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Email Address</label>
                <input
                  type="email"
                  value={editForm.email}
                  onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
                  placeholder="e.g. user@commerceos.io"
                  className="w-full px-3.5 py-2 rounded-xl bg-slate-800 border border-slate-700 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Customer Tier</label>
                  <select
                    value={editForm.tier}
                    onChange={(e) => setEditForm({ ...editForm, tier: e.target.value })}
                    className="w-full px-3.5 py-2 rounded-xl bg-slate-800 border border-slate-700 text-sm text-white focus:outline-none focus:border-emerald-500"
                  >
                    <option value="STANDARD">STANDARD</option>
                    <option value="GOLD">GOLD</option>
                    <option value="PLATINUM">PLATINUM</option>
                    <option value="VIP">VIP</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Account Status</label>
                  <select
                    value={editForm.isActive ? 'ACTIVE' : 'SUSPENDED'}
                    onChange={(e) => setEditForm({ ...editForm, isActive: e.target.value === 'ACTIVE' })}
                    className="w-full px-3.5 py-2 rounded-xl bg-slate-800 border border-slate-700 text-sm text-white focus:outline-none focus:border-emerald-500"
                  >
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="SUSPENDED">SUSPENDED</option>
                  </select>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setIsEditModalOpen(false)}
                  className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-bold text-slate-300"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-xs font-bold text-white flex items-center gap-1.5 transition-all disabled:opacity-50"
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
// SKELETON LOADERS FOR EACH INDIVIDUAL SECTION
// -------------------------------------------------------------

function ProfileSectionSkeleton() {
  return (
    <div className="space-y-4 animate-pulse">
      <div className="flex items-center gap-3.5 p-4 rounded-xl bg-slate-900/40 border border-slate-700/40">
        <div className="w-12 h-12 rounded-xl bg-slate-700/70 shrink-0" />
        <div className="space-y-2 flex-1">
          <div className="h-5 w-48 bg-slate-700/70 rounded-md" />
          <div className="h-3 w-32 bg-slate-700/50 rounded-md" />
        </div>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <div key={i} className="p-3.5 rounded-xl bg-slate-900/40 border border-slate-700/40 space-y-2">
            <div className="h-3 w-20 bg-slate-700/50 rounded" />
            <div className="h-4 w-36 bg-slate-700/70 rounded" />
          </div>
        ))}
      </div>
    </div>
  );
}

function AnalyticsSectionSkeleton() {
  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5 animate-pulse">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-slate-900/50 border border-slate-700/40 space-y-2.5">
          <div className="h-3 w-24 bg-slate-700/50 rounded" />
          <div className="h-6 w-28 bg-slate-700/70 rounded-md" />
          <div className="h-3 w-32 bg-slate-700/40 rounded" />
        </div>
      ))}
    </div>
  );
}

function AddressesSectionSkeleton() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 animate-pulse">
      {[1, 2].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-slate-900/40 border border-slate-700/40 space-y-2.5">
          <div className="flex items-center justify-between">
            <div className="h-4 w-16 bg-slate-700/60 rounded-full" />
            <div className="h-3 w-24 bg-slate-700/40 rounded" />
          </div>
          <div className="h-4 w-full bg-slate-700/70 rounded" />
          <div className="h-3 w-40 bg-slate-700/50 rounded" />
        </div>
      ))}
    </div>
  );
}

function OrdersSectionSkeleton() {
  return (
    <div className="space-y-3.5 animate-pulse">
      {[1, 2, 3].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-slate-900/60 border border-slate-700/60 space-y-3">
          <div className="flex items-center justify-between">
            <div className="h-4 w-32 bg-slate-700/70 rounded" />
            <div className="h-4 w-20 bg-slate-700/60 rounded-full" />
          </div>
          <div className="h-3 w-48 bg-slate-700/50 rounded" />
          <div className="h-5 w-24 bg-slate-700/70 rounded self-end" />
        </div>
      ))}
    </div>
  );
}

function CartSectionSkeleton() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 animate-pulse">
      {[1, 2, 3].map((i) => (
        <div key={i} className="p-3 rounded-xl bg-slate-900/60 border border-slate-700/60 space-y-2">
          <div className="h-3.5 w-32 bg-slate-700/70 rounded" />
          <div className="h-3 w-16 bg-slate-700/50 rounded" />
        </div>
      ))}
    </div>
  );
}

function PrescriptionsSectionSkeleton() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 animate-pulse">
      {[1, 2].map((i) => (
        <div key={i} className="p-4 rounded-xl bg-slate-900/60 border border-slate-700/60 space-y-2">
          <div className="h-4 w-28 bg-slate-700/70 rounded" />
          <div className="h-3 w-40 bg-slate-700/50 rounded" />
        </div>
      ))}
    </div>
  );
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
