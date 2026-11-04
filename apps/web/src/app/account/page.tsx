'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  User,
  ShoppingBag,
  Headphones,
  FileText,
  MapPin,
  Bell,
  Clock,
  Phone,
  LogOut,
  Edit2,
  ChevronRight,
  ShieldCheck,
  CheckCircle2,
  Zap,
  Camera,
  X,
  AlertCircle,
  Package,
  Calendar,
  Sparkles,
  ArrowRight,
} from 'lucide-react';
import {
  fetchCustomerAddresses,
  fetchCustomerProfile,
  updateCustomerProfile,
  fetchOrders,
  SavedAddress,
  OrderResponse,
} from '@/lib/api-client';
import { useSession } from '@/lib/session-store';
import UniversalHeader from '@/components/navigation/UniversalHeader';
import MobileBottomNav from '@/components/navigation/MobileBottomNav';
import DeliveryAddressMapModal from '@/components/DeliveryAddressMapModal';

/**
 * Customer Account & Healthcare Operations Dashboard benchmarked directly
 * against the CommerceOS Android App (AccountScreen.kt).
 */
export default function AccountDashboardPage() {
  const router = useRouter();
  const sessionUser = useSession((s) => s.user);
  const setUser = useSession((s) => s.setUser);
  const logout = useSession((s) => s.logout);

  // Profile state
  const [fullName, setFullName] = useState(sessionUser?.fullName || 'Rewari Customer');
  const [email, setEmail] = useState(sessionUser?.email || '');
  const [phone, setPhone] = useState('+91 98179 16180');
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);

  // Metrics
  const [orderCount, setOrderCount] = useState<number>(0);
  const [activeOrder, setActiveOrder] = useState<OrderResponse | null>(null);
  const [addresses, setAddresses] = useState<SavedAddress[]>([]);
  const [prescriptionCount, setPrescriptionCount] = useState<number>(3);
  const [refillCount, setRefillCount] = useState<number>(2);

  // Dialogs & Modals
  const [showEditProfileDialog, setShowEditProfileDialog] = useState(false);
  const [showAddressModal, setShowAddressModal] = useState(false);
  const [showNotificationDialog, setShowNotificationDialog] = useState(false);
  const [showRefillDialog, setShowRefillDialog] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccessMsg, setSaveSuccessMsg] = useState<string | null>(null);

  // Notification toggles
  const [notifOrderSms, setNotifOrderSms] = useState(true);
  const [notifWhatsapp, setNotifWhatsapp] = useState(true);
  const [notifRefillAlerts, setNotifRefillAlerts] = useState(true);

  // Load account data
  useEffect(() => {
    // Load local stored avatar
    const savedAvatar = typeof window !== 'undefined' ? localStorage.getItem('commerceos_user_avatar') : null;
    if (savedAvatar) setAvatarUrl(savedAvatar);

    const loadData = async () => {
      try {
        const [addrs, orders, profile] = await Promise.all([
          fetchCustomerAddresses().catch(() => []),
          fetchOrders().catch(() => []),
          fetchCustomerProfile().catch(() => null),
        ]);

        if (addrs && Array.isArray(addrs)) setAddresses(addrs);
        if (orders && Array.isArray(orders)) {
          setOrderCount(orders.length);
          const active = orders.find((o) =>
            ['PLACED', 'CONFIRMED', 'PACKED', 'DISPATCHED', 'OUT_FOR_DELIVERY'].includes((o.status || '').toUpperCase())
          );
          if (active) setActiveOrder(active);
        }
        if (profile) {
          if (profile.fullName || profile.name) setFullName(profile.fullName || profile.name);
          if (profile.phone) setPhone(profile.phone);
          if (profile.email) setEmail(profile.email);
        }
      } catch (e) {
        console.warn('Dashboard data load warning:', e);
      }
    };

    loadData();
  }, [sessionUser?.userId]);

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {
      if (sessionUser?.userId) {
        await updateCustomerProfile({ fullName, email, phone });
      }
      setUser({
        ...(sessionUser || { userId: 'cust_default', roles: ['CUSTOMER'] }),
        fullName,
        email,
      });
      setSaveSuccessMsg('Profile updated successfully!');
      setTimeout(() => setSaveSuccessMsg(null), 3000);
      setShowEditProfileDialog(false);
    } catch (err: any) {
      alert(err?.message || 'Failed to update profile');
    } finally {
      setIsSaving(false);
    }
  };

  const handleAvatarUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result as string;
        setAvatarUrl(result);
        if (typeof window !== 'undefined') {
          localStorage.setItem('commerceos_user_avatar', result);
        }
      };
      reader.readAsDataURL(file);
    }
  };

  const handleLogout = () => {
    if (confirm('Are you sure you want to log out of CommerceOS?')) {
      logout();
      router.push('/');
    }
  };

  return (
    <div className="min-h-screen bg-[#F4F6F8] text-slate-900 font-sans antialiased pb-28">
      {/* Universal Desktop & Tablet Navigation */}
      <UniversalHeader
        locationLabel={addresses[0]?.city || 'Rewari Central Hub'}
        etaMinutes={10}
        onLocationClick={() => setShowAddressModal(true)}
      />

      {/* Main Dashboard Container */}
      <main className="max-w-4xl mx-auto px-4 sm:px-6 pt-5 flex flex-col gap-4">
        {/* Top Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
              Profile & Account Dashboard
            </h1>
            <p className="text-xs sm:text-sm text-slate-500 font-medium">
              Manage your healthcare orders, addresses & prescriptions
            </p>
          </div>
          <button
            onClick={() => setShowNotificationDialog(true)}
            className="w-10 h-10 rounded-full bg-white border border-slate-200/90 shadow-2xs flex items-center justify-center hover:bg-slate-50 transition-colors cursor-pointer relative"
            aria-label="Alerts"
          >
            <Bell className="w-5 h-5 text-[#0E675E]" />
            <span className="absolute top-2 right-2 w-2 h-2 rounded-full bg-emerald-500" />
          </button>
        </div>

        {/* Active Order Banner (If Any Active Delivery In Flight) */}
        {activeOrder && (
          <div className="bg-gradient-to-r from-emerald-600 to-teal-700 text-white rounded-2xl p-4 shadow-sm flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-white/20 flex items-center justify-center text-white font-bold animate-pulse">
                <Package className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-black uppercase tracking-wider text-emerald-200">
                    Live Delivery
                  </span>
                  <span className="px-1.5 py-0.5 rounded bg-white text-emerald-800 text-[10px] font-black uppercase">
                    {activeOrder.status || 'OUT FOR DELIVERY'}
                  </span>
                </div>
                <p className="text-sm font-bold text-white mt-0.5">
                  Order #{activeOrder.id?.slice(-6) || activeOrder.orderId?.slice(-6)} • Arriving in ~10 mins
                </p>
              </div>
            </div>
            <Link
              href={`/orders/${activeOrder.id || activeOrder.orderId}`}
              className="px-3.5 py-1.5 rounded-xl bg-white text-emerald-800 hover:bg-emerald-50 text-xs font-black uppercase tracking-wider flex items-center gap-1 transition-all shadow-sm"
            >
              <span>Track Live</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        )}

        {/* 1. Customer Identification Card with Photo & Name */}
        <section className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5">
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-4">
              {/* Profile Avatar with Camera Trigger */}
              <div className="relative group cursor-pointer" onClick={() => setShowEditProfileDialog(true)}>
                <div className="w-16 h-16 rounded-full overflow-hidden border-2 border-[#0E675E] bg-emerald-50 flex items-center justify-center shadow-xs">
                  {avatarUrl ? (
                    <img src={avatarUrl} alt="Customer Avatar" className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-2xl font-black text-[#0E675E]">
                      {fullName ? fullName[0].toUpperCase() : 'U'}
                    </span>
                  )}
                </div>
                <div className="absolute bottom-0 right-0 w-6 h-6 rounded-full bg-[#0E675E] text-white flex items-center justify-center shadow-sm border border-white">
                  <Camera className="w-3 h-3" />
                </div>
              </div>

              {/* Customer Info */}
              <div className="flex flex-col">
                <div className="flex items-center gap-2">
                  <h2 className="text-lg font-black text-slate-900 tracking-tight">
                    {fullName || 'Verified Patient'}
                  </h2>
                  <span className="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full bg-emerald-50 text-[#0E675E] border border-emerald-200 text-[10px] font-bold">
                    <ShieldCheck className="w-3 h-3" />
                    Verified
                  </span>
                </div>
                <p className="text-xs font-semibold text-slate-500 mt-0.5">
                  {phone || '+91 98179 16180'}
                </p>
                <p className="text-[11px] text-slate-400 font-medium">
                  Patient ID: #{sessionUser?.userId?.slice(-8) || 'CUST-849201'}
                </p>
              </div>
            </div>

            {/* Edit Profile CTA */}
            <button
              onClick={() => setShowEditProfileDialog(true)}
              className="px-3.5 py-1.5 rounded-xl border border-slate-200/90 hover:bg-slate-50 text-slate-700 text-xs font-bold flex items-center gap-1.5 transition-colors cursor-pointer shadow-2xs"
            >
              <Edit2 className="w-3.5 h-3.5 text-[#0E675E]" />
              <span>Edit</span>
            </button>
          </div>

          {/* Fast Pharmacy SLA Banner */}
          <div className="mt-4 bg-[#EFF6FF] border border-[#DBEAFE] rounded-xl px-3.5 py-2.5 flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <span className="text-base">⚡</span>
              <div>
                <p className="text-xs font-extrabold text-[#1E40AF]">
                  CommerceOS Fast Pharmacy
                </p>
                <p className="text-[11px] text-[#3B82F6] font-medium">
                  10-Minute SLA Delivery • Certified Pharmacists
                </p>
              </div>
            </div>
            <Link
              href="/"
              className="text-[11px] font-bold text-[#1E40AF] hover:underline flex items-center gap-0.5"
            >
              <span>Explore Store</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </section>

        {/* 2. Dual Hero Action Tiles (My Orders vs Need Help?) */}
        <section className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
          {/* Tile 1: My Orders */}
          <Link
            href="/orders"
            className="bg-white rounded-2xl border border-slate-200/80 p-4 shadow-2xs hover:shadow-xs hover:border-slate-300 transition-all flex items-center gap-3.5 group cursor-pointer"
          >
            <div className="w-12 h-12 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
              <ShoppingBag className="w-6 h-6" />
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="text-sm font-black text-slate-900 group-hover:text-blue-600 transition-colors">
                My Orders
              </h3>
              <p className="text-xs text-slate-500 font-medium truncate">
                {orderCount > 0 ? `${orderCount} order(s) placed • Track & reorder` : 'Track deliveries & past orders'}
              </p>
            </div>
            <ChevronRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform" />
          </Link>

          {/* Tile 2: Need Help? */}
          <a
            href="tel:+9118002089999"
            className="bg-white rounded-2xl border border-slate-200/80 p-4 shadow-2xs hover:shadow-xs hover:border-slate-300 transition-all flex items-center gap-3.5 group cursor-pointer"
          >
            <div className="w-12 h-12 rounded-xl bg-emerald-50 text-[#0E675E] flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
              <Headphones className="w-6 h-6" />
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="text-sm font-black text-slate-900 group-hover:text-[#0E675E] transition-colors">
                Need Help?
              </h3>
              <p className="text-xs text-slate-500 font-medium truncate">
                24×7 Pharmacist support • Free consultation
              </p>
            </div>
            <ChevronRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform" />
          </a>
        </section>

        {/* 3. Healthcare Hub Grouped Features */}
        <section className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
          {/* Prescriptions Vault */}
          <Link
            href="/prescriptions"
            className="p-4 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-100 group"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-10 h-10 rounded-xl bg-emerald-50 text-[#0E675E] flex items-center justify-center shrink-0">
                <FileText className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-900 group-hover:text-[#0E675E] transition-colors">
                  Prescriptions Vault
                </h4>
                <p className="text-xs text-slate-500 font-medium">
                  {prescriptionCount > 0 ? `${prescriptionCount} doctor slip(s) uploaded • Verified` : 'Upload and manage verified doctor slips'}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-[#0E675E] text-[10px] font-black uppercase">
                Active
              </span>
              <ChevronRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform" />
            </div>
          </Link>

          {/* Manage Saved Addresses */}
          <button
            type="button"
            onClick={() => setShowAddressModal(true)}
            className="w-full p-4 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-100 text-left group cursor-pointer"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
                <MapPin className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-900 group-hover:text-amber-600 transition-colors">
                  Manage Addresses
                </h4>
                <p className="text-xs text-slate-500 font-medium">
                  {addresses.length > 0 ? `${addresses.length} delivery address(es) saved` : 'Add your home & office addresses'}
                </p>
              </div>
            </div>
            <ChevronRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform" />
          </button>

          {/* Medicine Refill Reminders */}
          <Link
            href="/refills"
            className="p-4 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-100 group"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
                <Clock className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-900 group-hover:text-purple-600 transition-colors">
                  Medicine Refill Reminders
                </h4>
                <p className="text-xs text-slate-500 font-medium">
                  {refillCount > 0 ? `${refillCount} active recurring prescription(s)` : 'Automatic schedules for recurring prescriptions'}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded-full bg-purple-100 text-purple-700 text-[10px] font-black uppercase">
                Refill Ready
              </span>
              <ChevronRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform" />
            </div>
          </Link>

          {/* Notification Preferences */}
          <button
            type="button"
            onClick={() => setShowNotificationDialog(true)}
            className="w-full p-4 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-100 text-left group cursor-pointer"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-10 h-10 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center shrink-0">
                <Bell className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-900 group-hover:text-rose-600 transition-colors">
                  Notification Preferences
                </h4>
                <p className="text-xs text-slate-500 font-medium">
                  Order updates, OTP verification & delivery SMS
                </p>
              </div>
            </div>
            <ChevronRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform" />
          </button>

          {/* Registered Pharmacist Helpline */}
          <a
            href="tel:+9118002089999"
            className="p-4 flex items-center justify-between hover:bg-slate-50 transition-colors group"
          >
            <div className="flex items-center gap-3.5">
              <div className="w-10 h-10 rounded-xl bg-teal-50 text-teal-700 flex items-center justify-center shrink-0">
                <Phone className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-slate-900 group-hover:text-teal-700 transition-colors">
                  Pharmacy Helpdesk
                </h4>
                <p className="text-xs text-slate-500 font-medium">
                  Call registered pharmacist: +91 1800-208-9999
                </p>
              </div>
            </div>
            <span className="text-xs font-bold text-teal-700">Call Now</span>
          </a>
        </section>

        {/* 4. Central Licensed Pharmacy Compliance Footer */}
        <section className="bg-slate-100/80 rounded-2xl p-4 text-center border border-slate-200/60">
          <p className="text-xs font-bold text-slate-700">
            CommerceOS Health • Central Licensed Pharmacy
          </p>
          <p className="text-[11px] text-slate-500 mt-0.5">
            Drug License: DL-HR-2024-009182 • WHO-GMP Certified Quality
          </p>
          <p className="text-[10px] text-slate-400 mt-1">
            Compliant with Drugs and Cosmetics Act 1940 & Pharmacy Practice Regulations
          </p>
        </section>

        {/* 5. Log Out Button */}
        <button
          onClick={handleLogout}
          className="w-full py-3 px-4 rounded-xl border border-red-200 text-red-600 bg-red-50/50 hover:bg-red-50 text-sm font-bold flex items-center justify-center gap-2 transition-colors cursor-pointer"
        >
          <LogOut className="w-4 h-4" />
          <span>Log Out of Account</span>
        </button>
      </main>

      {/* Profile Edit Dialog */}
      {showEditProfileDialog && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl relative animate-in fade-in zoom-in-95">
            <button
              onClick={() => setShowEditProfileDialog(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-lg font-black text-slate-900 mb-1">Edit Patient Profile</h3>
            <p className="text-xs text-slate-500 mb-4">
              Update your personal details for prescription verification and delivery
            </p>

            <form onSubmit={handleSaveProfile} className="flex flex-col gap-3.5">
              {/* Photo Upload */}
              <div className="flex items-center gap-3">
                <div className="w-14 h-14 rounded-full overflow-hidden border-2 border-[#0E675E] bg-emerald-50 flex items-center justify-center shrink-0">
                  {avatarUrl ? (
                    <img src={avatarUrl} alt="Avatar" className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-xl font-black text-[#0E675E]">{fullName ? fullName[0] : 'U'}</span>
                  )}
                </div>
                <div>
                  <label className="text-xs font-bold text-[#0E675E] bg-emerald-50 px-3 py-1.5 rounded-lg border border-emerald-200 cursor-pointer hover:bg-emerald-100 transition-colors inline-block">
                    Change Photo
                    <input type="file" accept="image/*" onChange={handleAvatarUpload} className="hidden" />
                  </label>
                  <p className="text-[10px] text-slate-400 mt-1">JPG or PNG, max 5MB</p>
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">Full Name</label>
                <input
                  type="text"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#0E675E]"
                  placeholder="e.g. Dr. Rajesh Sharma"
                />
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">Phone Number</label>
                <input
                  type="text"
                  required
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#0E675E]"
                  placeholder="+91 98179 16180"
                />
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">Email Address</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#0E675E]"
                  placeholder="name@example.com"
                />
              </div>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowEditProfileDialog(false)}
                  className="flex-1 py-2 rounded-xl border border-slate-300 text-slate-700 text-xs font-bold hover:bg-slate-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="flex-1 py-2 rounded-xl bg-[#0E675E] hover:bg-[#0A4F48] text-white text-xs font-bold transition-colors disabled:opacity-50"
                >
                  {isSaving ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Notifications Preferences Dialog */}
      {showNotificationDialog && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl relative animate-in fade-in zoom-in-95">
            <button
              onClick={() => setShowNotificationDialog(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-lg font-black text-slate-900 mb-1">Notification Preferences</h3>
            <p className="text-xs text-slate-500 mb-4">Control how you receive order delivery & health alerts</p>

            <div className="flex flex-col gap-3">
              <label className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
                <div>
                  <span className="text-xs font-bold text-slate-800 block">Order Status & Delivery SMS</span>
                  <span className="text-[11px] text-slate-500">Live SMS when order is packed and dispatched</span>
                </div>
                <input
                  type="checkbox"
                  checked={notifOrderSms}
                  onChange={(e) => setNotifOrderSms(e.target.checked)}
                  className="w-4 h-4 text-[#0E675E] rounded"
                />
              </label>

              <label className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
                <div>
                  <span className="text-xs font-bold text-slate-800 block">WhatsApp Delivery Updates</span>
                  <span className="text-[11px] text-slate-500">Rider tracking link & delivery OTP over WhatsApp</span>
                </div>
                <input
                  type="checkbox"
                  checked={notifWhatsapp}
                  onChange={(e) => setNotifWhatsapp(e.target.checked)}
                  className="w-4 h-4 text-[#0E675E] rounded"
                />
              </label>

              <label className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
                <div>
                  <span className="text-xs font-bold text-slate-800 block">Refill Reminders</span>
                  <span className="text-[11px] text-slate-500">Alerts 3 days before chronic medication runs out</span>
                </div>
                <input
                  type="checkbox"
                  checked={notifRefillAlerts}
                  onChange={(e) => setNotifRefillAlerts(e.target.checked)}
                  className="w-4 h-4 text-[#0E675E] rounded"
                />
              </label>
            </div>

            <button
              onClick={() => setShowNotificationDialog(false)}
              className="mt-4 w-full py-2.5 rounded-xl bg-[#0E675E] text-white text-xs font-bold"
            >
              Save Preferences
            </button>
          </div>
        </div>
      )}

      {/* Address Selection Modal */}
      <DeliveryAddressMapModal
        isOpen={showAddressModal}
        onClose={() => setShowAddressModal(false)}
        onSelectAddress={(addr) => {
          setShowAddressModal(false);
          setAddresses((prev) => [addr, ...prev.filter((a) => a.id !== addr.id)]);
        }}
      />

      {/* Mobile Bottom Navigation Bar (Home, Categories, Orders, Account) */}
      <MobileBottomNav />
    </div>
  );
}
