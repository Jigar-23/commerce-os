'use client';

import React, { useEffect, useState, useCallback, useMemo } from 'react';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import { sellerApi, RegisteredUser } from '@/lib/apiClient';
import {
  Users, Search, RefreshCw, Phone, Mail, MapPin,
  Calendar, ShoppingBag, IndianRupee, ShieldCheck, CheckCircle2,
  XCircle, Filter, ArrowUpDown, ChevronRight, UserCheck, UserX,
  ExternalLink, Eye, Copy, Check
} from 'lucide-react';

export default function RegisteredUsersPage() {
  const [users, setUsers] = useState<RegisteredUser[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [tierFilter, setTierFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [orderFilter, setOrderFilter] = useState<string>('ALL');
  const [selectedUser, setSelectedUser] = useState<RegisteredUser | null>(null);
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

  // High-level KPI analytics
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

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-canvas font-sans overflow-hidden">
        {/* Dynamic Navigation Sidebar */}
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
                  <p className="text-2xs uppercase tracking-wider font-extrabold text-content-muted">
                    Total Registered
                  </p>
                  <p className="text-2xl font-black text-content-primary tracking-tight mt-0.5">
                    {kpis.totalUsers}
                  </p>
                  <p className="text-3xs text-content-muted font-medium">Database Authoritative</p>
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-xs flex items-center space-x-4">
                <div className="w-12 h-12 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                  <UserCheck className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-2xs uppercase tracking-wider font-extrabold text-content-muted">
                    Active Accounts
                  </p>
                  <p className="text-2xl font-black text-content-primary tracking-tight mt-0.5">
                    {kpis.activeUsers}
                  </p>
                  <p className="text-3xs text-emerald-600 font-semibold">Ready for Delivery</p>
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-xs flex items-center space-x-4">
                <div className="w-12 h-12 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
                  <ShoppingBag className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-2xs uppercase tracking-wider font-extrabold text-content-muted">
                    Total Orders Placed
                  </p>
                  <p className="text-2xl font-black text-content-primary tracking-tight mt-0.5">
                    {kpis.totalOrders}
                  </p>
                  <p className="text-3xs text-content-muted font-medium">Platform Aggregate</p>
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-xs flex items-center space-x-4">
                <div className="w-12 h-12 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
                  <IndianRupee className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-2xs uppercase tracking-wider font-extrabold text-content-muted">
                    Customer GMV / Spend
                  </p>
                  <p className="text-2xl font-black text-content-primary tracking-tight mt-0.5">
                    ₹{kpis.totalSpend.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                  </p>
                  <p className="text-3xs text-content-muted font-medium">Cumulative Lifetime Value</p>
                </div>
              </div>
            </div>

            {/* Filters & Search Control Bar */}
            <div className="bg-white p-4 rounded-2xl border border-border-default shadow-xs flex flex-wrap items-center justify-between gap-3">
              <div className="flex-1 min-w-[240px] relative">
                <Search className="w-4 h-4 text-content-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search by customer name, mobile, email, ID, or city..."
                  className="w-full pl-9 pr-4 py-2.5 bg-surface-subtle border border-border-default rounded-xl text-xs font-semibold text-content-primary placeholder:text-content-muted outline-none focus:border-border-accent transition"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery('')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-content-muted hover:text-content-primary text-xs"
                  >
                    Clear
                  </button>
                )}
              </div>

              <div className="flex items-center flex-wrap gap-2">
                {/* Tier Filter */}
                <select
                  value={tierFilter}
                  onChange={(e) => setTierFilter(e.target.value)}
                  className="px-3 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs font-bold text-content-secondary outline-none focus:border-border-accent transition cursor-pointer"
                >
                  <option value="ALL">All Tiers</option>
                  <option value="STANDARD">Standard</option>
                  <option value="GOLD">Gold</option>
                  <option value="VIP">VIP</option>
                </select>

                {/* Status Filter */}
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="px-3 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs font-bold text-content-secondary outline-none focus:border-border-accent transition cursor-pointer"
                >
                  <option value="ALL">All Status</option>
                  <option value="ACTIVE">Active Only</option>
                  <option value="INACTIVE">Inactive Only</option>
                </select>

                {/* Order Filter */}
                <select
                  value={orderFilter}
                  onChange={(e) => setOrderFilter(e.target.value)}
                  className="px-3 py-2 bg-surface-subtle border border-border-default rounded-xl text-xs font-bold text-content-secondary outline-none focus:border-border-accent transition cursor-pointer"
                >
                  <option value="ALL">All Order History</option>
                  <option value="WITH_ORDERS">With Orders (&gt;0)</option>
                  <option value="NO_ORDERS">Zero Orders (0)</option>
                </select>
              </div>
            </div>

            {/* Error Banner */}
            {error && (
              <div className="p-4 bg-red-50 border border-red-200 rounded-2xl flex items-center space-x-3 text-red-700 text-xs font-bold">
                <XCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {/* Users Table */}
            <div className="bg-white rounded-2xl border border-border-default shadow-xs overflow-hidden">
              <div className="p-4 border-b border-border-subtle flex items-center justify-between bg-surface-subtle/50">
                <span className="text-xs font-black uppercase tracking-wider text-content-muted">
                  Customer Directory ({filteredUsers.length} shown)
                </span>
                <span className="text-3xs text-content-muted font-bold">
                  PostgreSQL Authoritative Identity
                </span>
              </div>

              {isLoading ? (
                <div className="p-16 flex flex-col items-center justify-center space-y-3">
                  <RefreshCw className="w-8 h-8 text-action-speedBg animate-spin" />
                  <p className="text-xs font-bold text-content-secondary">
                    Loading registered customer database…
                  </p>
                </div>
              ) : filteredUsers.length === 0 ? (
                <div className="p-16 text-center space-y-3">
                  <Users className="w-12 h-12 text-content-muted mx-auto opacity-40" />
                  <p className="text-sm font-bold text-content-primary">No customers found</p>
                  <p className="text-xs text-content-muted max-w-sm mx-auto">
                    {searchQuery
                      ? `No registered user matches the search "${searchQuery}". Try clearing filters.`
                      : 'No registered customer accounts found in the database.'}
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="border-b border-border-subtle bg-surface-subtle/30 text-3xs font-extrabold uppercase tracking-wider text-content-muted">
                        <th className="py-3 px-4">Customer</th>
                        <th className="py-3 px-4">Contact Info</th>
                        <th className="py-3 px-4">Account Tier</th>
                        <th className="py-3 px-4">Primary Address</th>
                        <th className="py-3 px-4 text-center">Orders</th>
                        <th className="py-3 px-4 text-right">Total Spend</th>
                        <th className="py-3 px-4">Registered Date</th>
                        <th className="py-3 px-4 text-center">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border-subtle text-xs">
                      {filteredUsers.map((user) => {
                        const displayName = user.full_name || 'Valued Customer';
                        const initials = displayName
                          .split(' ')
                          .map((n) => n[0])
                          .slice(0, 2)
                          .join('')
                          .toUpperCase() || 'CU';

                        return (
                          <tr
                            key={user.id}
                            className="hover:bg-surface-subtle/50 transition-colors group cursor-pointer"
                            onClick={() => setSelectedUser(user)}
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
                                {user.email ? (
                                  <div className="flex items-center space-x-1.5 text-3xs text-content-secondary">
                                    <Mail className="w-3 h-3 text-content-muted shrink-0" />
                                    <span className="truncate max-w-[150px]">{user.email}</span>
                                  </div>
                                ) : (
                                  <span className="text-3xs text-content-muted italic">
                                    Phone Auth
                                  </span>
                                )}
                              </div>
                            </td>

                            {/* Account Tier */}
                            <td className="py-3.5 px-4">
                              <span
                                className={`inline-flex items-center px-2 py-0.5 rounded-lg text-3xs font-extrabold uppercase tracking-wider ${
                                  user.tier === 'VIP'
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
                                onClick={() => setSelectedUser(user)}
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

        {/* Customer Profile Modal */}
        {selectedUser && (
          <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-white rounded-3xl max-w-lg w-full border border-border-default shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
              {/* Modal Header */}
              <div className="p-6 bg-surface-subtle border-b border-border-default flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <div className="w-12 h-12 rounded-2xl bg-action-speedBg text-white font-black text-base flex items-center justify-center shadow-md">
                    {(selectedUser.full_name || 'U').charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <h2 className="text-base font-black text-content-primary">
                      {selectedUser.full_name || 'Registered Customer'}
                    </h2>
                    <p className="text-3xs text-content-muted font-mono flex items-center space-x-1">
                      <span>ID: {selectedUser.id}</span>
                      <button
                        onClick={() => copyToClipboard(selectedUser.id, 'id')}
                        className="p-0.5 hover:text-content-primary transition"
                        title="Copy ID"
                      >
                        {copiedId === 'id' ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                      </button>
                    </p>
                  </div>
                </div>

                <button
                  onClick={() => setSelectedUser(null)}
                  className="p-2 text-content-muted hover:text-content-primary hover:bg-white rounded-xl transition"
                >
                  <XCircle className="w-5 h-5" />
                </button>
              </div>

              {/* Modal Body */}
              <div className="p-6 space-y-5 max-h-[75vh] overflow-y-auto text-xs">
                {/* Status & Tier Pill Row */}
                <div className="flex items-center justify-between p-3.5 bg-surface-canvas rounded-2xl border border-border-default">
                  <div>
                    <p className="text-3xs font-extrabold uppercase text-content-muted tracking-wider">Account Tier</p>
                    <p className="text-xs font-black text-content-primary mt-0.5">{selectedUser.tier || 'STANDARD'}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-3xs font-extrabold uppercase text-content-muted tracking-wider">Status</p>
                    <p className="text-xs font-black text-emerald-600 mt-0.5 flex items-center justify-end space-x-1">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>{selectedUser.is_active ? 'ACTIVE' : 'INACTIVE'}</span>
                    </p>
                  </div>
                </div>

                {/* Contact Information */}
                <div className="space-y-3">
                  <h3 className="text-3xs font-extrabold uppercase tracking-wider text-content-muted">
                    Contact &amp; Credentials
                  </h3>
                  <div className="space-y-2 bg-surface-subtle p-4 rounded-2xl border border-border-default">
                    <div className="flex items-center justify-between">
                      <span className="text-content-secondary font-medium">Phone Number</span>
                      <div className="flex items-center space-x-1 font-bold text-content-primary">
                        <span>{selectedUser.phone}</span>
                        <button
                          onClick={() => copyToClipboard(selectedUser.phone, 'phone')}
                          className="p-1 text-content-muted hover:text-content-primary transition"
                          title="Copy Phone"
                        >
                          {copiedId === 'phone' ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                        </button>
                      </div>
                    </div>

                    <div className="flex items-center justify-between">
                      <span className="text-content-secondary font-medium">Email Address</span>
                      <span className="font-bold text-content-primary">
                        {selectedUser.email || 'Not linked (Phone Auth)'}
                      </span>
                    </div>

                    <div className="flex items-center justify-between">
                      <span className="text-content-secondary font-medium">Registered On</span>
                      <span className="font-bold text-content-primary">
                        {formatDate(selectedUser.created_at)}
                      </span>
                    </div>

                    {selectedUser.updated_at && (
                      <div className="flex items-center justify-between">
                        <span className="text-content-secondary font-medium">Last Profile Update</span>
                        <span className="font-bold text-content-primary">
                          {formatDate(selectedUser.updated_at)}
                        </span>
                      </div>
                    )}
                  </div>
                </div>

                {/* Purchase & Order Analytics */}
                <div className="space-y-3">
                  <h3 className="text-3xs font-extrabold uppercase tracking-wider text-content-muted">
                    Order Analytics &amp; Value
                  </h3>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="p-3.5 bg-surface-subtle rounded-2xl border border-border-default">
                      <p className="text-3xs font-extrabold uppercase text-content-muted">Completed Orders</p>
                      <p className="text-lg font-black text-content-primary mt-1">
                        {selectedUser.total_orders || 0}
                      </p>
                    </div>
                    <div className="p-3.5 bg-surface-subtle rounded-2xl border border-border-default">
                      <p className="text-3xs font-extrabold uppercase text-content-muted">Total Spend (LTV)</p>
                      <p className="text-lg font-black text-action-speedBg mt-1">
                        ₹{Number(selectedUser.total_spend || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                      </p>
                    </div>
                  </div>
                </div>

                {/* Delivery Address */}
                <div className="space-y-3">
                  <h3 className="text-3xs font-extrabold uppercase tracking-wider text-content-muted">
                    Primary Delivery Address ({selectedUser.addresses_count || 0} on file)
                  </h3>
                  <div className="p-4 bg-surface-subtle rounded-2xl border border-border-default flex items-start space-x-3">
                    <MapPin className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-bold text-content-primary">
                        {selectedUser.primary_city || 'Rewari Hub Region'}
                      </p>
                      <p className="text-3xs text-content-secondary mt-0.5 leading-relaxed">
                        {selectedUser.primary_address || 'Customer has not registered a delivery address yet.'}
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              {/* Modal Footer */}
              <div className="p-4 bg-surface-subtle border-t border-border-default flex justify-end">
                <button
                  onClick={() => setSelectedUser(null)}
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
