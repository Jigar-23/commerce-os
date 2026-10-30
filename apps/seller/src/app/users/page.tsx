'use client';

import React, { useEffect, useState, useCallback, useMemo } from 'react';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import {
  sellerApi,
  RegisteredUser,
  CustomerFullDetail,
  CustomerAddress,
  CustomerOrderHistory,
  CustomerCart,
  CustomerPrescription
} from '@/lib/apiClient';
import {
  Users, Search, RefreshCw, Phone, Mail, MapPin,
  Calendar, ShoppingBag, IndianRupee, ShieldCheck, CheckCircle2,
  XCircle, Filter, ArrowUpDown, ChevronRight, UserCheck, UserX,
  ExternalLink, Eye, Copy, Check, ShoppingCart, Package, FileText,
  Clock, Navigation, AlertCircle, Hash, ArrowUpRight, DollarSign,
  Activity, Tag, Compass, AlertTriangle, Layers, Sparkles
} from 'lucide-react';

export default function RegisteredUsersPage() {
  const [users, setUsers] = useState<RegisteredUser[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [tierFilter, setTierFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [orderFilter, setOrderFilter] = useState<string>('ALL');

  // Customer 360° Detail Drawer/Modal State
  const [selectedUser, setSelectedUser] = useState<RegisteredUser | null>(null);
  const [userDetail, setUserDetail] = useState<CustomerFullDetail | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [detailTab, setDetailTab] = useState<'OVERVIEW' | 'ORDERS' | 'ADDRESSES' | 'CART' | 'PRESCRIPTIONS'>('OVERVIEW');
  const [orderStatusFilter, setOrderStatusFilter] = useState<string>('ALL');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await sellerApi.getUsers();
      if (res && res.data && res.data.users) {
        setUsers(res.data.users);
      } else {
        setUsers([]);
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to fetch registered users');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleOpenUserDetail = async (user: RegisteredUser) => {
    setSelectedUser(user);
    setUserDetail(null);
    setIsLoadingDetail(true);
    setDetailTab('OVERVIEW');
    setOrderStatusFilter('ALL');

    try {
      const res = await sellerApi.getUserDetail(user.id);
      if (res && res.data && res.data.ok) {
        setUserDetail(res.data);
      }
    } catch (err) {
      console.error('Failed to load full user details:', err);
    } finally {
      setIsLoadingDetail(false);
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard?.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      const q = searchQuery.toLowerCase().trim();
      const matchesSearch =
        !q ||
        (u.full_name && u.full_name.toLowerCase().includes(q)) ||
        (u.phone && u.phone.toLowerCase().includes(q)) ||
        (u.email && u.email.toLowerCase().includes(q)) ||
        (u.id && u.id.toLowerCase().includes(q)) ||
        (u.primary_city && u.primary_city.toLowerCase().includes(q));

      const matchesTier =
        tierFilter === 'ALL' ||
        (u.tier && u.tier.toUpperCase() === tierFilter.toUpperCase());

      const matchesStatus =
        statusFilter === 'ALL' ||
        (statusFilter === 'ACTIVE' && u.is_active) ||
        (statusFilter === 'INACTIVE' && !u.is_active);

      const matchesOrder =
        orderFilter === 'ALL' ||
        (orderFilter === 'WITH_ORDERS' && Number(u.total_orders || 0) > 0) ||
        (orderFilter === 'NO_ORDERS' && Number(u.total_orders || 0) === 0);

      return matchesSearch && matchesTier && matchesStatus && matchesOrder;
    });
  }, [users, searchQuery, tierFilter, statusFilter, orderFilter]);

  // High-level KPI analytics for the list
  const kpis = useMemo(() => {
    const totalUsers = users.length;
    const activeUsers = users.filter((u) => u.is_active).length;
    const totalOrders = users.reduce((sum, u) => sum + Number(u.total_orders || 0), 0);
    const totalSpend = users.reduce((sum, u) => sum + Number(u.total_spend || 0), 0);
    return { totalUsers, activeUsers, totalOrders, totalSpend };
  }, [users]);

  const formatDate = (dateStr?: string | null) => {
    if (!dateStr) return 'N/A';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
      });
    } catch {
      return dateStr;
    }
  };

  const formatDateTime = (dateStr?: string | null) => {
    if (!dateStr) return 'N/A';
    try {
      const d = new Date(dateStr);
      return d.toLocaleString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
      });
    } catch {
      return dateStr;
    }
  };

  const getStatusBadge = (status: string) => {
    const s = (status || '').toUpperCase();
    if (s === 'DELIVERED' || s === 'COMPLETED') {
      return {
        bg: 'bg-emerald-50 text-emerald-700 border-emerald-200',
        dot: 'bg-emerald-500',
        label: s
      };
    }
    if (s === 'CANCELLED' || s === 'FAILED' || s === 'REJECTED') {
      return {
        bg: 'bg-rose-50 text-rose-700 border-rose-200',
        dot: 'bg-rose-500',
        label: s
      };
    }
    if (['PLACED', 'RECEIVED', 'CONFIRMED', 'SELLER_ACCEPTED'].includes(s)) {
      return {
        bg: 'bg-blue-50 text-blue-700 border-blue-200',
        dot: 'bg-blue-500',
        label: s
      };
    }
    return {
      bg: 'bg-purple-50 text-purple-700 border-purple-200',
      dot: 'bg-purple-500',
      label: s
    };
  };

  // Orders displayed in the 360 view based on status filter
  const displayedOrders = useMemo(() => {
    const list = userDetail?.orders || [];
    if (orderStatusFilter === 'ALL') return list;
    return list.filter((o) => {
      const st = (o.status || '').toUpperCase();
      if (orderStatusFilter === 'DELIVERED') return st === 'DELIVERED' || st === 'COMPLETED';
      if (orderStatusFilter === 'CANCELLED') return st === 'CANCELLED';
      if (orderStatusFilter === 'ACTIVE') return !['DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUNDED'].includes(st);
      return true;
    });
  }, [userDetail?.orders, orderStatusFilter]);

  // Derived counts for tabs
  const addressesList = userDetail?.addresses || [];
  const ordersList = userDetail?.orders || [];
  const cartItems = userDetail?.cart?.items || [];
  const prescriptionsList = userDetail?.prescriptions || [];

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-canvas font-sans overflow-hidden">
        {/* Navigation Sidebar */}
        <SellerSidebar activeTab="users" onRefresh={fetchUsers} isLoading={isLoading} />

        {/* Main Content Area */}
        <main className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Top Sticky Header */}
          <header className="sticky top-0 z-10 bg-white/95 backdrop-blur-md border-b border-border-default px-6 py-4 flex items-center justify-between shadow-xs">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-2xl bg-action-speedBg text-white flex items-center justify-center shadow-md shadow-subtle">
                <Users className="w-5 h-5" />
              </div>
              <div>
                <h1 className="text-lg font-black text-content-primary tracking-tight">
                  Registered Customers &amp; Users
                </h1>
                <p className="text-2xs text-content-muted font-medium">
                  Authoritative customer directory, purchase history, delivery profiles, and account tier intelligence
                </p>
              </div>
            </div>

            <div className="flex items-center space-x-3">
              <HeaderQuickSearch placeholder="Quick search customer, order, SKU..." />
              <button
                onClick={fetchUsers}
                disabled={isLoading}
                className="px-3.5 py-2 bg-surface-subtle hover:bg-surface-accentSubtle text-content-secondary hover:text-content-accent rounded-xl text-xs font-bold transition flex items-center space-x-2 border border-border-default disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
                <span>Refresh</span>
              </button>
            </div>
          </header>

          <div className="p-6 space-y-6">
            {/* KPI Cards Row */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-xs flex items-center space-x-4">
                <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                  <Users className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-2xs uppercase tracking-wider font-extrabold text-content-muted">Total Customers</p>
                  <p className="text-xl font-black text-content-primary mt-0.5">{kpis.totalUsers}</p>
                  <p className="text-3xs text-content-muted mt-0.5">Verified user accounts</p>
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-xs flex items-center space-x-4">
                <div className="w-12 h-12 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                  <UserCheck className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-2xs uppercase tracking-wider font-extrabold text-content-muted">Active Profiles</p>
                  <p className="text-xl font-black text-emerald-600 mt-0.5">{kpis.activeUsers}</p>
                  <p className="text-3xs text-content-muted mt-0.5">Eligible for instant ordering</p>
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-xs flex items-center space-x-4">
                <div className="w-12 h-12 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
                  <ShoppingBag className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-2xs uppercase tracking-wider font-extrabold text-content-muted">Orders Placed</p>
                  <p className="text-xl font-black text-purple-600 mt-0.5">{kpis.totalOrders}</p>
                  <p className="text-3xs text-content-muted mt-0.5">Cumulative lifetime orders</p>
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-xs flex items-center space-x-4">
                <div className="w-12 h-12 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
                  <IndianRupee className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-2xs uppercase tracking-wider font-extrabold text-content-muted">Gross Spend</p>
                  <p className="text-xl font-black text-amber-600 mt-0.5">
                    ₹{kpis.totalSpend.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </p>
                  <p className="text-3xs text-content-muted mt-0.5">Total customer GMV</p>
                </div>
              </div>
            </div>

            {/* Filter and Search Bar */}
            <div className="bg-white p-4 rounded-2xl border border-border-default shadow-xs flex flex-wrap items-center justify-between gap-4">
              <div className="relative flex-1 min-w-[260px]">
                <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-content-muted" />
                <input
                  type="text"
                  placeholder="Filter by customer name, phone, email, ID, or city..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs font-medium text-content-primary placeholder:text-content-muted focus:outline-hidden focus:ring-2 focus:ring-action-speedBg/20 focus:border-action-speedBg transition"
                />
              </div>

              <div className="flex flex-wrap items-center gap-2.5">
                {/* Tier Filter */}
                <select
                  value={tierFilter}
                  onChange={(e) => setTierFilter(e.target.value)}
                  className="px-3 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs font-bold text-content-secondary focus:outline-hidden"
                >
                  <option value="ALL">All Tiers</option>
                  <option value="STANDARD">Standard</option>
                  <option value="SILVER">Silver</option>
                  <option value="GOLD">Gold</option>
                  <option value="PLATINUM">Platinum</option>
                </select>

                {/* Status Filter */}
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="px-3 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs font-bold text-content-secondary focus:outline-hidden"
                >
                  <option value="ALL">All Statuses</option>
                  <option value="ACTIVE">Active Only</option>
                  <option value="INACTIVE">Inactive Only</option>
                </select>

                {/* Orders Filter */}
                <select
                  value={orderFilter}
                  onChange={(e) => setOrderFilter(e.target.value)}
                  className="px-3 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs font-bold text-content-secondary focus:outline-hidden"
                >
                  <option value="ALL">All Customer Activity</option>
                  <option value="WITH_ORDERS">Customers with Orders</option>
                  <option value="NO_ORDERS">Registered (0 Orders)</option>
                </select>
              </div>
            </div>

            {/* Error Notification */}
            {error && (
              <div className="p-4 bg-rose-50 border border-rose-200 rounded-2xl flex items-center justify-between text-rose-700 text-xs font-bold">
                <div className="flex items-center space-x-2">
                  <XCircle className="w-4 h-4 shrink-0" />
                  <span>{error}</span>
                </div>
                <button onClick={fetchUsers} className="underline text-xs hover:text-rose-900">
                  Retry
                </button>
              </div>
            )}

            {/* Customers Data Table */}
            <div className="bg-white rounded-3xl border border-border-default shadow-xs overflow-hidden">
              <div className="px-6 py-4 border-b border-border-default flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-black text-content-primary">Customer Registry</h2>
                  <p className="text-3xs text-content-muted">Showing {filteredUsers.length} of {users.length} registered customers</p>
                </div>
              </div>

              {isLoading && users.length === 0 ? (
                <div className="py-20 text-center space-y-3">
                  <RefreshCw className="w-8 h-8 animate-spin mx-auto text-action-speedBg" />
                  <p className="text-xs font-bold text-content-muted">Loading authoritative customer records...</p>
                </div>
              ) : filteredUsers.length === 0 ? (
                <div className="py-20 text-center space-y-3">
                  <Users className="w-10 h-10 mx-auto text-content-muted opacity-40" />
                  <p className="text-sm font-bold text-content-secondary">No matching customers found</p>
                  <p className="text-2xs text-content-muted max-w-sm mx-auto">
                    Try adjusting your search terms or filter criteria to find the customer you are looking for.
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="border-b border-border-default bg-surface-subtle text-3xs font-extrabold uppercase text-content-muted tracking-wider">
                        <th className="py-3 px-4">Customer</th>
                        <th className="py-3 px-4">Contact</th>
                        <th className="py-3 px-4">Tier</th>
                        <th className="py-3 px-4">Primary Address</th>
                        <th className="py-3 px-4 text-center">Orders</th>
                        <th className="py-3 px-4 text-right">Spend</th>
                        <th className="py-3 px-4">Joined</th>
                        <th className="py-3 px-4 text-center">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border-subtle">
                      {filteredUsers.map((user) => {
                        const displayName = user.full_name || 'Customer ' + (user.phone ? user.phone.slice(-4) : 'User');
                        const initials =
                          displayName
                            .split(' ')
                            .map((n) => n[0])
                            .slice(0, 2)
                            .join('')
                            .toUpperCase() || 'CU';

                        return (
                          <tr
                            key={user.id}
                            className="hover:bg-surface-subtle/50 transition-colors group cursor-pointer"
                            onClick={() => handleOpenUserDetail(user)}
                          >
                            {/* Customer Avatar & Name */}
                            <td className="py-3.5 px-4">
                              <div className="flex items-center space-x-3">
                                <div className="w-9 h-9 rounded-xl bg-action-speedBg/10 text-action-speedBg font-bold text-xs flex items-center justify-center shrink-0 border border-action-speedBg/20">
                                  {initials}
                                </div>
                                <div className="min-w-0">
                                  <div className="flex items-center space-x-1.5">
                                    <p className="font-bold text-content-primary truncate">
                                      {displayName}
                                    </p>
                                    {user.is_active ? (
                                      <span className="w-2 h-2 rounded-full bg-emerald-500 shrink-0" title="Active" />
                                    ) : (
                                      <span className="w-2 h-2 rounded-full bg-slate-300 shrink-0" title="Inactive" />
                                    )}
                                  </div>
                                  <p className="text-3xs text-content-muted font-mono truncate">
                                    {user.id}
                                  </p>
                                </div>
                              </div>
                            </td>

                            {/* Contact Info */}
                            <td className="py-3.5 px-4 font-medium">
                              <div className="space-y-0.5">
                                <div className="flex items-center space-x-1.5 text-content-primary font-bold">
                                  <Phone className="w-3 h-3 text-content-muted shrink-0" />
                                  <span>{user.phone}</span>
                                </div>
                                {user.email && (
                                  <div className="flex items-center space-x-1.5 text-content-muted text-3xs truncate max-w-[180px]">
                                    <Mail className="w-3 h-3 text-content-muted shrink-0" />
                                    <span className="truncate">{user.email}</span>
                                  </div>
                                )}
                              </div>
                            </td>

                            {/* Tier */}
                            <td className="py-3.5 px-4">
                              <span
                                className={`inline-block px-2.5 py-0.5 rounded-full text-3xs font-extrabold tracking-wider uppercase ${
                                  user.tier === 'PLATINUM'
                                    ? 'bg-amber-100 text-amber-800 border border-amber-300'
                                    : user.tier === 'GOLD'
                                    ? 'bg-yellow-100 text-yellow-800 border border-yellow-300'
                                    : 'bg-surface-subtle text-content-secondary border border-border-default'
                                }`}
                              >
                                {user.tier || 'STANDARD'}
                              </span>
                            </td>

                            {/* Primary Address */}
                            <td className="py-3.5 px-4">
                              {user.primary_address ? (
                                <div className="space-y-0.5 max-w-[200px]">
                                  <div className="flex items-center space-x-1 text-content-primary font-semibold truncate">
                                    <MapPin className="w-3 h-3 text-red-500 shrink-0" />
                                    <span className="truncate">{user.primary_city || 'Rewari'}</span>
                                  </div>
                                  <p className="text-3xs text-content-muted truncate" title={user.primary_address}>
                                    {user.primary_address}
                                  </p>
                                </div>
                              ) : (
                                <span className="text-3xs text-content-muted italic">
                                  No address saved
                                </span>
                              )}
                            </td>

                            {/* Orders Count */}
                            <td className="py-3.5 px-4 text-center">
                              <span
                                className={`inline-block px-2.5 py-1 rounded-xl text-xs font-black ${
                                  Number(user.total_orders || 0) > 0
                                    ? 'bg-action-speedBg/10 text-action-speedBg'
                                    : 'bg-surface-subtle text-content-muted'
                                }`}
                              >
                                {user.total_orders || 0}
                              </span>
                            </td>

                            {/* Total Spend */}
                            <td className="py-3.5 px-4 text-right font-black text-content-primary">
                              ₹{Number(user.total_spend || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                            </td>

                            {/* Registered Date */}
                            <td className="py-3.5 px-4 text-3xs text-content-muted font-medium whitespace-nowrap">
                              <div className="flex items-center space-x-1">
                                <Calendar className="w-3 h-3 text-content-muted shrink-0" />
                                <span>{formatDate(user.created_at)}</span>
                              </div>
                            </td>

                            {/* Actions */}
                            <td className="py-3.5 px-4 text-center" onClick={(e) => e.stopPropagation()}>
                              <button
                                onClick={() => handleOpenUserDetail(user)}
                                className="px-2.5 py-1.5 rounded-lg text-content-secondary hover:text-content-accent hover:bg-surface-accentSubtle border border-border-default transition text-2xs font-bold inline-flex items-center space-x-1"
                              >
                                <Eye className="w-3 h-3" />
                                <span>Details</span>
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </main>

        {/* Customer 360° Comprehensive Modal */}
        {selectedUser && (
          <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-3 sm:p-6 overflow-y-auto">
            <div className="bg-white rounded-3xl max-w-4xl w-full border border-border-default shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150 my-auto flex flex-col max-h-[90vh]">
              {/* Modal Header */}
              <div className="p-6 bg-surface-subtle border-b border-border-default flex flex-wrap items-center justify-between gap-4">
                <div className="flex items-center space-x-4">
                  <div className="w-14 h-14 rounded-2xl bg-action-speedBg text-white font-black text-lg flex items-center justify-center shadow-lg shadow-subtle shrink-0">
                    {(selectedUser.full_name || 'U').charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <div className="flex items-center space-x-2">
                      <h2 className="text-lg font-black text-content-primary">
                        {selectedUser.full_name || 'Registered Customer'}
                      </h2>
                      <span
                        className={`inline-block px-2.5 py-0.5 rounded-full text-3xs font-extrabold uppercase tracking-wider ${
                          selectedUser.is_active
                            ? 'bg-emerald-100 text-emerald-800 border border-emerald-300'
                            : 'bg-slate-200 text-slate-700'
                        }`}
                      >
                        {selectedUser.is_active ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-3 mt-1 text-2xs text-content-muted">
                      <span className="flex items-center space-x-1 font-mono">
                        <span>ID: {selectedUser.id}</span>
                        <button
                          onClick={() => copyToClipboard(selectedUser.id, 'id')}
                          className="p-1 hover:text-content-primary transition"
                          title="Copy Customer ID"
                        >
                          {copiedId === 'id' ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                        </button>
                      </span>

                      <span className="flex items-center space-x-1 font-bold text-content-primary">
                        <Phone className="w-3 h-3 text-content-muted shrink-0" />
                        <span>{selectedUser.phone}</span>
                        <button
                          onClick={() => copyToClipboard(selectedUser.phone, 'phone')}
                          className="p-1 hover:text-content-primary transition"
                          title="Copy Phone"
                        >
                          {copiedId === 'phone' ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                        </button>
                      </span>

                      <a
                        href={`tel:${selectedUser.phone}`}
                        className="px-2 py-0.5 bg-blue-50 text-blue-700 hover:bg-blue-100 rounded-md font-bold text-3xs transition inline-flex items-center space-x-1 border border-blue-200"
                      >
                        <Phone className="w-2.5 h-2.5" />
                        <span>Call</span>
                      </a>
                    </div>
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  {isLoadingDetail && (
                    <span className="flex items-center space-x-1.5 px-3 py-1 bg-blue-50 text-blue-700 rounded-xl text-2xs font-bold animate-pulse border border-blue-200">
                      <RefreshCw className="w-3 h-3 animate-spin" />
                      <span>Syncing live 360° data...</span>
                    </span>
                  )}
                  <button
                    onClick={() => {
                      setSelectedUser(null);
                      setUserDetail(null);
                    }}
                    className="p-2 text-content-muted hover:text-content-primary hover:bg-white rounded-xl transition"
                    title="Close"
                  >
                    <XCircle className="w-6 h-6" />
                  </button>
                </div>
              </div>

              {/* Navigation Tabs Bar */}
              <div className="bg-white border-b border-border-default px-6 flex items-center space-x-1 overflow-x-auto">
                <button
                  onClick={() => setDetailTab('OVERVIEW')}
                  className={`px-4 py-3 text-xs font-black border-b-2 transition flex items-center space-x-2 whitespace-nowrap ${
                    detailTab === 'OVERVIEW'
                      ? 'border-action-speedBg text-action-speedBg'
                      : 'border-transparent text-content-secondary hover:text-content-primary'
                  }`}
                >
                  <Activity className="w-3.5 h-3.5" />
                  <span>Overview</span>
                </button>

                <button
                  onClick={() => setDetailTab('ORDERS')}
                  className={`px-4 py-3 text-xs font-black border-b-2 transition flex items-center space-x-2 whitespace-nowrap ${
                    detailTab === 'ORDERS'
                      ? 'border-action-speedBg text-action-speedBg'
                      : 'border-transparent text-content-secondary hover:text-content-primary'
                  }`}
                >
                  <Package className="w-3.5 h-3.5" />
                  <span>Order History</span>
                  <span className="px-2 py-0.5 rounded-full text-3xs font-extrabold bg-surface-subtle text-content-secondary">
                    {ordersList.length}
                  </span>
                </button>

                <button
                  onClick={() => setDetailTab('ADDRESSES')}
                  className={`px-4 py-3 text-xs font-black border-b-2 transition flex items-center space-x-2 whitespace-nowrap ${
                    detailTab === 'ADDRESSES'
                      ? 'border-action-speedBg text-action-speedBg'
                      : 'border-transparent text-content-secondary hover:text-content-primary'
                  }`}
                >
                  <MapPin className="w-3.5 h-3.5" />
                  <span>Saved Addresses</span>
                  <span className="px-2 py-0.5 rounded-full text-3xs font-extrabold bg-surface-subtle text-content-secondary">
                    {addressesList.length}
                  </span>
                </button>

                <button
                  onClick={() => setDetailTab('CART')}
                  className={`px-4 py-3 text-xs font-black border-b-2 transition flex items-center space-x-2 whitespace-nowrap ${
                    detailTab === 'CART'
                      ? 'border-action-speedBg text-action-speedBg'
                      : 'border-transparent text-content-secondary hover:text-content-primary'
                  }`}
                >
                  <ShoppingCart className="w-3.5 h-3.5" />
                  <span>Active Cart</span>
                  {cartItems.length > 0 && (
                    <span className="px-2 py-0.5 rounded-full text-3xs font-extrabold bg-action-speedBg text-white">
                      {cartItems.length}
                    </span>
                  )}
                </button>

                {prescriptionsList.length > 0 && (
                  <button
                    onClick={() => setDetailTab('PRESCRIPTIONS')}
                    className={`px-4 py-3 text-xs font-black border-b-2 transition flex items-center space-x-2 whitespace-nowrap ${
                      detailTab === 'PRESCRIPTIONS'
                        ? 'border-action-speedBg text-action-speedBg'
                        : 'border-transparent text-content-secondary hover:text-content-primary'
                    }`}
                  >
                    <FileText className="w-3.5 h-3.5" />
                    <span>Prescriptions</span>
                    <span className="px-2 py-0.5 rounded-full text-3xs font-extrabold bg-surface-subtle text-content-secondary">
                      {prescriptionsList.length}
                    </span>
                  </button>
                )}
              </div>

              {/* Tab Contents Scrollable Area */}
              <div className="p-6 space-y-6 overflow-y-auto flex-1 text-xs">
                {/* 1. OVERVIEW TAB */}
                {detailTab === 'OVERVIEW' && (
                  <div className="space-y-6">
                    {/* Financial Metrics Cards */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3.5">
                      <div className="p-4 bg-emerald-50/70 border border-emerald-200 rounded-2xl">
                        <p className="text-3xs font-extrabold uppercase text-emerald-800 tracking-wider">Realized Net Spend</p>
                        <p className="text-xl font-black text-emerald-700 mt-1">
                          ₹{Number(userDetail?.analytics?.realizedSpend ?? selectedUser.total_spend ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                        </p>
                        <p className="text-3xs text-emerald-600 mt-0.5">Delivered &amp; completed orders</p>
                      </div>

                      <div className="p-4 bg-blue-50/70 border border-blue-200 rounded-2xl">
                        <p className="text-3xs font-extrabold uppercase text-blue-800 tracking-wider">Gross Order Value</p>
                        <p className="text-xl font-black text-blue-700 mt-1">
                          ₹{Number(userDetail?.analytics?.grossOrderValue ?? selectedUser.total_spend ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                        </p>
                        <p className="text-3xs text-blue-600 mt-0.5">Total across all orders placed</p>
                      </div>

                      <div className="p-4 bg-purple-50/70 border border-purple-200 rounded-2xl">
                        <p className="text-3xs font-extrabold uppercase text-purple-800 tracking-wider">Total Orders</p>
                        <p className="text-xl font-black text-purple-700 mt-1">
                          {userDetail?.analytics?.totalOrders ?? selectedUser.total_orders ?? 0}
                        </p>
                        <p className="text-3xs text-purple-600 mt-0.5">
                          {userDetail?.analytics?.deliveredOrders ?? 0} delivered / {userDetail?.analytics?.cancelledOrders ?? 0} cancelled
                        </p>
                      </div>

                      <div className="p-4 bg-amber-50/70 border border-amber-200 rounded-2xl">
                        <p className="text-3xs font-extrabold uppercase text-amber-800 tracking-wider">Average Order (AOV)</p>
                        <p className="text-xl font-black text-amber-700 mt-1">
                          ₹{Number(userDetail?.analytics?.avgOrderValue ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                        </p>
                        <p className="text-3xs text-amber-600 mt-0.5">Average ticket size</p>
                      </div>
                    </div>

                    {/* Account Attributes & Saved Location Cards */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {/* Customer Credentials */}
                      <div className="bg-surface-subtle p-5 rounded-2xl border border-border-default space-y-3">
                        <h3 className="text-3xs font-extrabold uppercase tracking-wider text-content-muted flex items-center space-x-1.5">
                          <ShieldCheck className="w-3.5 h-3.5 text-action-speedBg" />
                          <span>Account Credentials &amp; Status</span>
                        </h3>

                        <div className="space-y-2 text-2xs">
                          <div className="flex items-center justify-between py-1 border-b border-border-subtle">
                            <span className="text-content-secondary font-medium">Customer ID</span>
                            <span className="font-mono font-bold text-content-primary">{selectedUser.id}</span>
                          </div>

                          <div className="flex items-center justify-between py-1 border-b border-border-subtle">
                            <span className="text-content-secondary font-medium">Account Tier</span>
                            <span className="font-bold text-content-primary uppercase">{selectedUser.tier || 'STANDARD'}</span>
                          </div>

                          <div className="flex items-center justify-between py-1 border-b border-border-subtle">
                            <span className="text-content-secondary font-medium">Auth Mechanism</span>
                            <span className="font-bold text-content-primary">
                              {selectedUser.email ? 'Email & Phone Auth' : 'Phone OTP Authentication'}
                            </span>
                          </div>

                          <div className="flex items-center justify-between py-1 border-b border-border-subtle">
                            <span className="text-content-secondary font-medium">Email Address</span>
                            <span className="font-bold text-content-primary">{selectedUser.email || 'None on record'}</span>
                          </div>

                          <div className="flex items-center justify-between py-1 border-b border-border-subtle">
                            <span className="text-content-secondary font-medium">Member Since</span>
                            <span className="font-bold text-content-primary">{formatDateTime(selectedUser.created_at)}</span>
                          </div>

                          {selectedUser.updated_at && (
                            <div className="flex items-center justify-between py-1">
                              <span className="text-content-secondary font-medium">Last Profile Sync</span>
                              <span className="font-bold text-content-primary">{formatDateTime(selectedUser.updated_at)}</span>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Primary Delivery Address Highlights */}
                      <div className="bg-surface-subtle p-5 rounded-2xl border border-border-default space-y-3">
                        <div className="flex items-center justify-between">
                          <h3 className="text-3xs font-extrabold uppercase tracking-wider text-content-muted flex items-center space-x-1.5">
                            <MapPin className="w-3.5 h-3.5 text-red-500" />
                            <span>Primary Delivery Address</span>
                          </h3>
                          <button
                            onClick={() => setDetailTab('ADDRESSES')}
                            className="text-action-speedBg hover:underline text-3xs font-bold"
                          >
                            View all ({addressesList.length}) ➔
                          </button>
                        </div>

                        {addressesList.length > 0 ? (
                          (() => {
                            const primary = addressesList.find((a) => a.is_default) || addressesList[0];
                            return (
                              <div className="space-y-2 text-2xs">
                                <div className="flex items-center space-x-2">
                                  <span className="px-2 py-0.5 rounded-md bg-blue-100 text-blue-800 text-3xs font-extrabold uppercase">
                                    {primary.address_type || 'HOME'}
                                  </span>
                                  {primary.is_default && (
                                    <span className="px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800 text-3xs font-extrabold uppercase">
                                      Default Location
                                    </span>
                                  )}
                                </div>

                                <p className="font-bold text-content-primary text-xs leading-snug">
                                  {primary.address_line}
                                </p>
                                <p className="text-content-secondary text-2xs">
                                  {primary.city} {primary.postal_code ? `- ${primary.postal_code}` : ''}
                                </p>

                                {primary.contact_phone && (
                                  <p className="text-3xs text-content-muted">
                                    Contact Phone: <span className="font-bold text-content-primary">{primary.contact_phone}</span>
                                  </p>
                                )}

                                {primary.latitude && primary.longitude && (
                                  <div className="pt-2 flex items-center justify-between border-t border-border-subtle">
                                    <span className="text-3xs font-mono text-content-muted">
                                      GPS: {primary.latitude.toFixed(5)}, {primary.longitude.toFixed(5)}
                                    </span>
                                    <a
                                      href={`https://maps.google.com/?q=${primary.latitude},${primary.longitude}`}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="text-action-speedBg hover:underline font-bold text-3xs inline-flex items-center space-x-1"
                                    >
                                      <span>Open Google Maps</span>
                                      <ExternalLink className="w-2.5 h-2.5" />
                                    </a>
                                  </div>
                                )}
                              </div>
                            );
                          })()
                        ) : (
                          <div className="py-6 text-center text-content-muted text-2xs italic">
                            No delivery address registered yet.
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Quick Order Snapshot & Cart Preview */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {/* Recent Orders Preview */}
                      <div className="bg-white p-5 rounded-2xl border border-border-default space-y-3">
                        <div className="flex items-center justify-between">
                          <h3 className="text-3xs font-extrabold uppercase tracking-wider text-content-muted flex items-center space-x-1.5">
                            <Clock className="w-3.5 h-3.5 text-action-speedBg" />
                            <span>Recent Orders Snapshot</span>
                          </h3>
                          <button
                            onClick={() => setDetailTab('ORDERS')}
                            className="text-action-speedBg hover:underline text-3xs font-bold"
                          >
                            Full History ({ordersList.length}) ➔
                          </button>
                        </div>

                        {ordersList.length > 0 ? (
                          <div className="space-y-2">
                            {ordersList.slice(0, 3).map((ord) => {
                              const badge = getStatusBadge(ord.status);
                              return (
                                <div
                                  key={ord.id}
                                  className="p-3 bg-surface-subtle hover:bg-surface-accentSubtle/50 rounded-xl border border-border-default transition flex items-center justify-between"
                                >
                                  <div>
                                    <div className="flex items-center space-x-2">
                                      <span className="font-mono font-bold text-content-primary">
                                        #{ord.order_id || ord.id.slice(-8)}
                                      </span>
                                      <span
                                        className={`px-2 py-0.5 rounded-full text-3xs font-black border ${badge.bg}`}
                                      >
                                        {badge.label}
                                      </span>
                                    </div>
                                    <p className="text-3xs text-content-muted mt-0.5">
                                      {formatDateTime(ord.created_at)} • {ord.items ? ord.items.length : 0} items
                                    </p>
                                  </div>
                                  <div className="text-right">
                                    <p className="font-black text-content-primary">
                                      ₹{Number(ord.total_amount || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                                    </p>
                                    <span className="text-3xs font-bold text-content-muted uppercase">
                                      {ord.payment_method || 'COD'}
                                    </span>
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <div className="py-6 text-center text-content-muted text-2xs italic">
                            No orders placed by this customer yet.
                          </div>
                        )}
                      </div>

                      {/* Active Cart Preview */}
                      <div className="bg-white p-5 rounded-2xl border border-border-default space-y-3">
                        <div className="flex items-center justify-between">
                          <h3 className="text-3xs font-extrabold uppercase tracking-wider text-content-muted flex items-center space-x-1.5">
                            <ShoppingCart className="w-3.5 h-3.5 text-action-speedBg" />
                            <span>Active Unpurchased Cart</span>
                          </h3>
                          {cartItems.length > 0 && (
                            <button
                              onClick={() => setDetailTab('CART')}
                              className="text-action-speedBg hover:underline text-3xs font-bold"
                            >
                              View Cart ({cartItems.length}) ➔
                            </button>
                          )}
                        </div>

                        {cartItems.length > 0 ? (
                          <div className="space-y-2">
                            <p className="text-3xs text-emerald-600 font-bold flex items-center space-x-1">
                              <Sparkles className="w-3 h-3" />
                              <span>Customer has {cartItems.length} item(s) pending in their active cart</span>
                            </p>
                            <div className="divide-y divide-border-subtle bg-surface-subtle p-3 rounded-xl border border-border-default">
                              {cartItems.slice(0, 3).map((item: any, i: number) => (
                                <div key={i} className="py-2 flex items-center justify-between first:pt-0 last:pb-0">
                                  <div className="min-w-0 pr-2">
                                    <p className="font-bold text-content-primary truncate">
                                      {item.title || item.name || 'Catalog Product'}
                                    </p>
                                    <p className="text-3xs text-content-muted">Qty: {item.quantity || item.qty || 1}</p>
                                  </div>
                                  <p className="font-black text-content-primary shrink-0">
                                    ₹{Number((item.price || item.unit_price || 0) * (item.quantity || item.qty || 1)).toLocaleString('en-IN')}
                                  </p>
                                </div>
                              ))}
                            </div>
                          </div>
                        ) : (
                          <div className="py-6 text-center text-content-muted text-2xs italic">
                            Cart is currently empty.
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                )}

                {/* 2. ORDER HISTORY TAB */}
                {detailTab === 'ORDERS' && (
                  <div className="space-y-4">
                    {/* Status Filter Bar */}
                    <div className="flex flex-wrap items-center justify-between gap-3 p-3 bg-surface-subtle rounded-2xl border border-border-default">
                      <div className="flex items-center space-x-1.5 overflow-x-auto">
                        {['ALL', 'ACTIVE', 'DELIVERED', 'CANCELLED'].map((st) => (
                          <button
                            key={st}
                            onClick={() => setOrderStatusFilter(st)}
                            className={`px-3 py-1.5 rounded-xl text-2xs font-extrabold transition ${
                              orderStatusFilter === st
                                ? 'bg-action-speedBg text-white shadow-xs'
                                : 'bg-white text-content-secondary hover:text-content-primary border border-border-default'
                            }`}
                          >
                            {st === 'ALL' ? `All Orders (${ordersList.length})` : st}
                          </button>
                        ))}
                      </div>

                      <div className="text-2xs text-content-muted font-bold">
                        Showing {displayedOrders.length} order(s)
                      </div>
                    </div>

                    {displayedOrders.length === 0 ? (
                      <div className="py-16 text-center space-y-2 bg-surface-subtle rounded-3xl border border-border-default">
                        <ShoppingBag className="w-8 h-8 mx-auto text-content-muted opacity-40" />
                        <p className="text-xs font-bold text-content-secondary">No orders found in this filter</p>
                        <p className="text-3xs text-content-muted">Try selecting a different status filter</p>
                      </div>
                    ) : (
                      <div className="space-y-4">
                        {displayedOrders.map((order) => {
                          const badge = getStatusBadge(order.status);
                          const items = Array.isArray(order.items) ? order.items : [];
                          const deliveryAddr = order.delivery_address || {};

                          return (
                            <div
                              key={order.id}
                              className="bg-white rounded-2xl border border-border-default shadow-xs overflow-hidden"
                            >
                              {/* Order Card Header */}
                              <div className="p-4 bg-surface-subtle border-b border-border-default flex flex-wrap items-center justify-between gap-3">
                                <div className="flex items-center space-x-3">
                                  <div className="w-8 h-8 rounded-xl bg-action-speedBg/10 text-action-speedBg flex items-center justify-center font-bold text-xs shrink-0">
                                    <Package className="w-4 h-4" />
                                  </div>
                                  <div>
                                    <div className="flex items-center space-x-2">
                                      <span className="font-mono font-bold text-content-primary text-xs">
                                        #{order.order_id || order.id}
                                      </span>
                                      <button
                                        onClick={() => copyToClipboard(order.order_id || order.id, `ord_${order.id}`)}
                                        className="p-0.5 text-content-muted hover:text-content-primary transition"
                                        title="Copy Order ID"
                                      >
                                        {copiedId === `ord_${order.id}` ? (
                                          <Check className="w-3 h-3 text-emerald-500" />
                                        ) : (
                                          <Copy className="w-3 h-3" />
                                        )}
                                      </button>
                                      <span
                                        className={`inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-3xs font-black border ${badge.bg}`}
                                      >
                                        <span className={`w-1.5 h-1.5 rounded-full ${badge.dot}`} />
                                        <span>{badge.label}</span>
                                      </span>
                                    </div>
                                    <p className="text-3xs text-content-muted mt-0.5">
                                      Placed on {formatDateTime(order.created_at)}
                                    </p>
                                  </div>
                                </div>

                                <div className="text-right">
                                  <p className="text-base font-black text-content-primary">
                                    ₹{Number(order.total_amount || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                                  </p>
                                  <div className="flex items-center justify-end space-x-1.5 text-3xs font-extrabold uppercase text-content-muted">
                                    <span>{order.payment_method || (order.is_cod ? 'COD' : 'ONLINE')}</span>
                                    <span>•</span>
                                    <span className={order.payment_status === 'PAID' ? 'text-emerald-600' : 'text-amber-600'}>
                                      {order.payment_status || 'PENDING'}
                                    </span>
                                  </div>
                                </div>
                              </div>

                              {/* Order Card Body */}
                              <div className="p-4 space-y-4">
                                {/* Delivery Address Used Snapshot */}
                                {deliveryAddr && (deliveryAddr.address_line || deliveryAddr.address || deliveryAddr.city) && (
                                  <div className="p-3 bg-surface-subtle rounded-xl border border-border-default flex items-start space-x-2.5 text-2xs">
                                    <MapPin className="w-3.5 h-3.5 text-red-500 shrink-0 mt-0.5" />
                                    <div className="flex-1 min-w-0">
                                      <div className="flex items-center justify-between">
                                        <span className="font-extrabold text-content-primary">
                                          Delivery Destination: {deliveryAddr.city || 'Rewari'} {deliveryAddr.postal_code ? `- ${deliveryAddr.postal_code}` : ''}
                                        </span>
                                        {order.delivery_pin && (
                                          <span className="px-2 py-0.5 bg-amber-100 text-amber-800 rounded font-mono font-black text-3xs border border-amber-300">
                                            Delivery PIN: {order.delivery_pin}
                                          </span>
                                        )}
                                      </div>
                                      <p className="text-content-secondary text-3xs mt-0.5">
                                        {deliveryAddr.address_line || deliveryAddr.address}
                                      </p>
                                      {deliveryAddr.contact_phone && (
                                        <p className="text-3xs text-content-muted mt-0.5">
                                          Recipient Phone: <span className="font-bold">{deliveryAddr.contact_phone}</span>
                                        </p>
                                      )}
                                    </div>
                                  </div>
                                )}

                                {/* Cancellation Note if Cancelled */}
                                {String(order.status).toUpperCase() === 'CANCELLED' && (
                                  <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-2xs">
                                    <div className="flex items-center space-x-1.5 font-bold">
                                      <AlertTriangle className="w-3.5 h-3.5" />
                                      <span>Order Cancelled</span>
                                    </div>
                                    {order.cancellation && (
                                      <p className="text-3xs mt-1 text-rose-600">
                                        Reason: {order.cancellation.reason || JSON.stringify(order.cancellation)}
                                      </p>
                                    )}
                                  </div>
                                )}

                                {/* Itemized List */}
                                <div className="space-y-2">
                                  <p className="text-3xs font-extrabold uppercase tracking-wider text-content-muted">
                                    Items ({items.length})
                                  </p>
                                  <div className="divide-y divide-border-subtle bg-surface-canvas rounded-xl border border-border-default">
                                    {items.length === 0 ? (
                                      <p className="p-3 text-3xs text-content-muted italic">Order items summary not recorded.</p>
                                    ) : (
                                      items.map((item: any, idx: number) => {
                                        const qty = item.quantity || item.qty || 1;
                                        const unitPrice = item.price || item.unit_price || 0;
                                        const total = item.total || unitPrice * qty;

                                        return (
                                          <div key={idx} className="p-3 flex items-center justify-between text-2xs">
                                            <div className="min-w-0 pr-3">
                                              <p className="font-bold text-content-primary">
                                                {item.title || item.name || 'Product Item'}
                                              </p>
                                              <div className="flex items-center space-x-2 text-3xs text-content-muted mt-0.5">
                                                {item.sku && <span>SKU: {item.sku}</span>}
                                                <span>Qty: {qty}</span>
                                                <span>₹{Number(unitPrice).toLocaleString('en-IN')} each</span>
                                              </div>
                                            </div>
                                            <p className="font-black text-content-primary shrink-0">
                                              ₹{Number(total).toLocaleString('en-IN')}
                                            </p>
                                          </div>
                                        );
                                      })
                                    )}
                                  </div>
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}

                {/* 3. SAVED ADDRESSES TAB */}
                {detailTab === 'ADDRESSES' && (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <h3 className="text-xs font-black text-content-primary">
                        Registered Delivery Addresses ({addressesList.length})
                      </h3>
                      <p className="text-3xs text-content-muted">
                        Authoritative geocoded drop-points registered on this customer account
                      </p>
                    </div>

                    {addressesList.length === 0 ? (
                      <div className="py-16 text-center space-y-2 bg-surface-subtle rounded-3xl border border-border-default">
                        <MapPin className="w-8 h-8 mx-auto text-content-muted opacity-40" />
                        <p className="text-xs font-bold text-content-secondary">No addresses registered yet</p>
                        <p className="text-3xs text-content-muted">The user has not saved any delivery location</p>
                      </div>
                    ) : (
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {addressesList.map((addr) => (
                          <div
                            key={addr.id}
                            className={`p-5 rounded-2xl border transition shadow-xs space-y-3 ${
                              addr.is_default
                                ? 'bg-emerald-50/40 border-emerald-300'
                                : 'bg-white border-border-default hover:border-action-speedBg/40'
                            }`}
                          >
                            <div className="flex items-center justify-between">
                              <span className="px-2.5 py-0.5 rounded-md bg-blue-100 text-blue-800 text-3xs font-extrabold uppercase">
                                {addr.address_type || 'HOME'}
                              </span>

                              {addr.is_default && (
                                <span className="px-2.5 py-0.5 rounded-md bg-emerald-100 text-emerald-800 text-3xs font-extrabold uppercase flex items-center space-x-1">
                                  <Check className="w-2.5 h-2.5" />
                                  <span>Default Address</span>
                                </span>
                              )}
                            </div>

                            <div className="space-y-1">
                              <p className="font-bold text-content-primary text-xs leading-snug">
                                {addr.address_line}
                              </p>
                              <p className="text-content-secondary text-2xs">
                                {addr.city} {addr.postal_code ? `- ${addr.postal_code}` : ''}
                              </p>
                              {addr.contact_phone && (
                                <p className="text-3xs text-content-muted">
                                  Contact: <span className="font-bold text-content-primary">{addr.contact_phone}</span>
                                </p>
                              )}
                            </div>

                            {/* GPS Geocoordinates */}
                            {addr.latitude && addr.longitude && (
                              <div className="pt-3 border-t border-border-subtle flex items-center justify-between">
                                <div className="space-y-0.5">
                                  <p className="text-3xs uppercase font-extrabold text-content-muted">GPS Coordinates</p>
                                  <p className="text-3xs font-mono font-bold text-content-primary">
                                    {addr.latitude.toFixed(6)}, {addr.longitude.toFixed(6)}
                                  </p>
                                </div>
                                <a
                                  href={`https://maps.google.com/?q=${addr.latitude},${addr.longitude}`}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="px-3 py-1.5 bg-surface-subtle hover:bg-surface-accentSubtle text-action-speedBg rounded-xl font-bold text-3xs transition border border-border-default inline-flex items-center space-x-1"
                                >
                                  <span>Google Maps</span>
                                  <ExternalLink className="w-2.5 h-2.5" />
                                </a>
                              </div>
                            )}

                            {addr.created_at && (
                              <p className="text-3xs text-content-muted text-right">
                                Added on {formatDate(addr.created_at)}
                              </p>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 4. ACTIVE CART TAB */}
                {detailTab === 'CART' && (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <h3 className="text-xs font-black text-content-primary">
                        Live Active Cart
                      </h3>
                      {userDetail?.cart?.updated_at && (
                        <p className="text-3xs text-content-muted">
                          Last cart sync: {formatDateTime(userDetail.cart.updated_at)}
                        </p>
                      )}
                    </div>

                    {cartItems.length === 0 ? (
                      <div className="py-16 text-center space-y-2 bg-surface-subtle rounded-3xl border border-border-default">
                        <ShoppingCart className="w-8 h-8 mx-auto text-content-muted opacity-40" />
                        <p className="text-xs font-bold text-content-secondary">User Cart is Empty</p>
                        <p className="text-3xs text-content-muted">
                          This customer does not currently have any unpurchased items in their cart.
                        </p>
                      </div>
                    ) : (
                      <div className="bg-white rounded-2xl border border-border-default shadow-xs overflow-hidden">
                        <div className="p-4 bg-emerald-50 border-b border-emerald-200 flex items-center justify-between">
                          <div className="flex items-center space-x-2 text-emerald-800 text-xs font-bold">
                            <Sparkles className="w-4 h-4 text-emerald-600" />
                            <span>{cartItems.length} item(s) pending customer checkout</span>
                          </div>
                          <span className="font-black text-emerald-800 text-sm">
                            Subtotal: ₹{cartItems.reduce((sum: number, it: any) => sum + (Number(it.price || 0) * (it.quantity || it.qty || 1)), 0).toLocaleString('en-IN')}
                          </span>
                        </div>

                        <div className="divide-y divide-border-subtle">
                          {cartItems.map((item: any, idx: number) => {
                            const qty = item.quantity || item.qty || 1;
                            const price = Number(item.price || item.unit_price || 0);

                            return (
                              <div key={idx} className="p-4 flex items-center justify-between">
                                <div className="space-y-0.5">
                                  <p className="font-bold text-content-primary text-xs">
                                    {item.title || item.name || 'Catalog Item'}
                                  </p>
                                  <div className="flex items-center space-x-2 text-3xs text-content-muted">
                                    {item.sku && <span>SKU: {item.sku}</span>}
                                    <span>Quantity: {qty}</span>
                                    <span>Rate: ₹{price.toLocaleString('en-IN')}</span>
                                  </div>
                                </div>
                                <div className="text-right">
                                  <p className="text-sm font-black text-content-primary">
                                    ₹{(price * qty).toLocaleString('en-IN')}
                                  </p>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* 5. PRESCRIPTIONS TAB */}
                {detailTab === 'PRESCRIPTIONS' && (
                  <div className="space-y-4">
                    <h3 className="text-xs font-black text-content-primary">
                      Customer Prescriptions ({prescriptionsList.length})
                    </h3>

                    {prescriptionsList.length === 0 ? (
                      <div className="py-16 text-center space-y-2 bg-surface-subtle rounded-3xl border border-border-default">
                        <FileText className="w-8 h-8 mx-auto text-content-muted opacity-40" />
                        <p className="text-xs font-bold text-content-secondary">No Prescriptions Uploaded</p>
                        <p className="text-3xs text-content-muted">
                          Customer has not submitted any Rx prescription records.
                        </p>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {prescriptionsList.map((rx: any) => (
                          <div
                            key={rx.id}
                            className="bg-white p-5 rounded-2xl border border-border-default shadow-xs space-y-3"
                          >
                            <div className="flex items-center justify-between">
                              <span className="font-mono font-bold text-xs text-content-primary">
                                Rx #{rx.id.slice(-8)}
                              </span>
                              <span
                                className={`px-2.5 py-0.5 rounded-full text-3xs font-extrabold uppercase ${
                                  rx.status === 'APPROVED'
                                    ? 'bg-emerald-100 text-emerald-800'
                                    : rx.status === 'REJECTED'
                                    ? 'bg-rose-100 text-rose-800'
                                    : 'bg-amber-100 text-amber-800'
                                }`}
                              >
                                {rx.status || 'PENDING'}
                              </span>
                            </div>

                            <div className="grid grid-cols-2 gap-2 text-2xs">
                              <div>
                                <span className="text-content-muted">Patient: </span>
                                <span className="font-bold text-content-primary">{rx.patient_name || 'Not specified'}</span>
                              </div>
                              <div>
                                <span className="text-content-muted">Doctor: </span>
                                <span className="font-bold text-content-primary">{rx.doctor_name || 'Not specified'}</span>
                              </div>
                            </div>

                            {rx.note && (
                              <p className="text-3xs text-content-secondary bg-surface-subtle p-3 rounded-xl border border-border-default">
                                {rx.note}
                              </p>
                            )}

                            <p className="text-3xs text-content-muted text-right">
                              Submitted: {formatDateTime(rx.created_at)}
                            </p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Modal Footer */}
              <div className="p-4 bg-surface-subtle border-t border-border-default flex items-center justify-between">
                <div className="text-3xs text-content-muted">
                  Customer ID: <span className="font-mono font-bold text-content-primary">{selectedUser.id}</span>
                </div>
                <button
                  onClick={() => {
                    setSelectedUser(null);
                    setUserDetail(null);
                  }}
                  className="px-5 py-2.5 bg-action-speedBg hover:bg-action-speedHover text-white font-bold rounded-xl text-xs transition shadow-md shadow-subtle"
                >
                  Close Profile
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
