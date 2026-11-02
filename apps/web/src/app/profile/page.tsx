'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  User,
  MapPin,
  HeartPulse,
  Save,
  CheckCircle2,
  Trash2,
  ArrowLeft,
  AlertCircle,
  Loader2,
  Bell,
  Package,
  Headphones,
  Wallet,
  FileText,
  Clock,
  Phone,
  MessageCircle,
  ChevronRight,
  LogOut,
  ShieldCheck,
  Edit2,
  X,
  UploadCloud,
} from 'lucide-react';
import {
  fetchCustomerAddresses,
  fetchCustomerProfile,
  updateCustomerProfile,
  SavedAddress,
} from '@/lib/api-client';
import { useSession } from '@/lib/session-store';
import DeliveryAddressMapModal from '@/components/DeliveryAddressMapModal';
import MobileBottomNav from '@/components/navigation/MobileBottomNav';

export default function CustomerProfilePage() {
  const router = useRouter();
  const sessionUser = useSession((s) => s.user);
  const setUser = useSession((s) => s.setUser);
  const logout = useSession((s) => s.logout);

  // Profile data state
  const [fullName, setFullName] = useState(sessionUser?.fullName || 'Verified Patient');
  const [email, setEmail] = useState(sessionUser?.email || '');
  const [phone, setPhone] = useState('+91 98179 16180');
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Address modal state
  const [addresses, setAddresses] = useState<SavedAddress[]>([]);
  const [isMapModalOpen, setIsMapModalOpen] = useState(false);
  const [selectedAddress, setSelectedAddress] = useState<SavedAddress | null>(null);

  // Support / Help state
  const [isHelpOpen, setIsHelpOpen] = useState(false);

  const loadData = async () => {
    try {
      if (sessionUser?.userId) {
        const [addrs, profile] = await Promise.all([
          fetchCustomerAddresses(sessionUser.userId).catch(() => []),
          fetchCustomerProfile(sessionUser.userId).catch(() => null),
        ]);
        setAddresses(addrs || []);
        if (addrs && addrs.length > 0) setSelectedAddress(addrs[0]);
        if (profile) {
          if (profile.fullName) setFullName(profile.fullName);
          if (profile.phone) setPhone(profile.phone);
          if (profile.email) setEmail(profile.email);
        }
      } else {
        const addrs = await fetchCustomerAddresses().catch(() => []);
        setAddresses(addrs || []);
        if (addrs && addrs.length > 0) setSelectedAddress(addrs[0]);
      }
    } catch (_) {
      setAddresses([]);
    }
  };

  useEffect(() => {
    loadData();
  }, [sessionUser?.userId]);

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setSaveSuccess(false);
    try {
      if (sessionUser?.userId) {
        await updateCustomerProfile({ fullName, email, phone }, sessionUser.userId);
        setUser({
          ...sessionUser,
          fullName,
          email,
        });
      }
      setIsEditing(false);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch (_) {
    } finally {
      setIsSaving(false);
    }
  };

  const handleLogout = () => {
    logout();
    router.push('/');
  };

  return (
    <div className="min-h-screen bg-[#F8FAFC] text-slate-900 flex flex-col font-sans antialiased pb-28">
      {/* 1. DEEP EMERALD CANOPY HEADER (MATCHING ANDROID ACCOUNTSCREEN) */}
      <header className="bg-gradient-to-b from-[#094640] to-[#0E675E] text-white px-4 py-4 sticky top-0 z-30 shadow-md">
        <div className="max-w-2xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              className="w-9 h-9 rounded-full bg-white/15 flex items-center justify-center text-white hover:bg-white/25 transition-colors"
            >
              <ArrowLeft className="h-5 w-5" />
            </Link>
            <div>
              <h1 className="text-lg sm:text-xl font-extrabold tracking-tight">My Account</h1>
              <p className="text-[11.5px] text-white/85">Profile, orders &amp; healthcare vault</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Link
              href="/orders"
              className="w-9 h-9 rounded-full bg-white/15 hover:bg-white/25 flex items-center justify-center text-white transition-all relative"
            >
              <Bell className="h-4 w-4" />
              <span className="w-2 h-2 rounded-full bg-red-500 absolute top-2 right-2 ring-1 ring-[#0E675E]" />
            </Link>
          </div>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-4 py-4 w-full space-y-4">
        {/* 2. USER PROFILE CARD */}
        <div className="bg-white rounded-2xl p-5 border border-slate-200 shadow-xs flex items-center justify-between gap-4">
          <div className="flex items-center gap-3.5 min-w-0">
            <div className="w-14 h-14 rounded-full bg-[#0E675E] text-white font-extrabold text-xl flex items-center justify-center shrink-0 shadow-xs border-2 border-emerald-100">
              {fullName.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h2 className="text-base font-extrabold text-slate-900 truncate">{fullName}</h2>
                <span className="text-[10px] font-black bg-emerald-50 text-[#0E675E] border border-emerald-200 px-2 py-0.5 rounded-full flex items-center gap-1 shrink-0">
                  <CheckCircle2 className="h-3 w-3" />
                  Verified
                </span>
              </div>
              <p className="text-xs text-slate-500 font-medium mt-0.5">{phone}</p>
              {email ? <p className="text-[11px] text-slate-400 truncate">{email}</p> : null}
            </div>
          </div>

          <button
            onClick={() => setIsEditing(true)}
            className="p-2.5 rounded-xl bg-slate-50 hover:bg-slate-100 text-slate-700 border border-slate-200 transition-all shrink-0"
            title="Edit Profile"
          >
            <Edit2 className="h-4 w-4 text-[#0E675E]" />
          </button>
        </div>

        {saveSuccess && (
          <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-xs font-bold text-[#0E675E] flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4" />
            <span>Profile details updated successfully!</span>
          </div>
        )}

        {/* 3. SPLIT HUB QUICK CARDS (MY ORDERS & NEED HELP) */}
        <div className="grid grid-cols-2 gap-3">
          <Link
            href="/orders"
            className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs hover:border-[#0E675E] transition-all flex flex-col justify-between space-y-3"
          >
            <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <Package className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-sm font-extrabold text-slate-900">My Orders</h3>
              <p className="text-[11px] text-slate-500 mt-0.5">Track live delivery &amp; reorder</p>
            </div>
          </Link>

          <button
            type="button"
            onClick={() => setIsHelpOpen(true)}
            className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs hover:border-[#0E675E] transition-all text-left flex flex-col justify-between space-y-3 cursor-pointer"
          >
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-[#0E675E] flex items-center justify-center">
              <Headphones className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-sm font-extrabold text-slate-900">Need Help?</h3>
              <p className="text-[11px] text-slate-500 mt-0.5">24x7 WhatsApp &amp; Helpline</p>
            </div>
          </button>
        </div>

        {/* 4. WALLET & PRESCRIPTIONS VAULT CARDS */}
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Wallet</span>
              <Wallet className="h-4 w-4 text-emerald-600" />
            </div>
            <p className="text-lg font-black text-slate-900">₹0.00</p>
            <p className="text-[10px] text-slate-400">Instant refund credits</p>
          </div>

          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-xs space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Rx Vault</span>
              <FileText className="h-4 w-4 text-[#0E675E]" />
            </div>
            <p className="text-lg font-black text-slate-900">3 Saved</p>
            <p className="text-[10px] text-slate-400">Active doctor slips</p>
          </div>
        </div>

        {/* 5. MANAGE ACCOUNT & PREFERENCES LIST */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs divide-y divide-slate-100 overflow-hidden">
          <button
            type="button"
            onClick={() => setIsMapModalOpen(true)}
            className="w-full px-4 py-3.5 flex items-center justify-between text-left hover:bg-slate-50 transition-colors cursor-pointer"
          >
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
                <MapPin className="h-4 w-4 text-[#0E675E]" />
              </div>
              <div>
                <h4 className="text-xs sm:text-sm font-bold text-slate-900">Delivery Addresses</h4>
                <p className="text-[11px] text-slate-500">{addresses.length} saved locations in Rewari</p>
              </div>
            </div>
            <ChevronRight className="h-4 w-4 text-slate-400" />
          </button>

          <Link
            href="/refills"
            className="w-full px-4 py-3.5 flex items-center justify-between text-left hover:bg-slate-50 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                <Clock className="h-4 w-4" />
              </div>
              <div>
                <h4 className="text-xs sm:text-sm font-bold text-slate-900">Chronic Refill Reminders</h4>
                <p className="text-[11px] text-slate-500">Smart 3-day advance reorder alerts</p>
              </div>
            </div>
            <ChevronRight className="h-4 w-4 text-slate-400" />
          </Link>

          <button
            type="button"
            onClick={() => setIsHelpOpen(true)}
            className="w-full px-4 py-3.5 flex items-center justify-between text-left hover:bg-slate-50 transition-colors cursor-pointer"
          >
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-emerald-50 text-[#0E675E] flex items-center justify-center">
                <Phone className="h-4 w-4" />
              </div>
              <div>
                <h4 className="text-xs sm:text-sm font-bold text-slate-900">Pharmacy 24x7 Helpdesk</h4>
                <p className="text-[11px] text-slate-500">Direct hotline to registered pharmacists</p>
              </div>
            </div>
            <ChevronRight className="h-4 w-4 text-slate-400" />
          </button>
        </div>

        {/* 6. LOGOUT BUTTON */}
        <button
          onClick={handleLogout}
          className="w-full py-3.5 px-4 rounded-2xl bg-white hover:bg-red-50 text-red-600 border border-red-200 text-xs sm:text-sm font-extrabold flex items-center justify-center gap-2 shadow-xs transition-all active:scale-98 cursor-pointer"
        >
          <LogOut className="h-4 w-4" />
          <span>Log Out</span>
        </button>

        {/* 7. PHARMACY REGULATORY LICENSE FOOTER */}
        <div className="text-center pt-2 pb-4 space-y-1 text-slate-400 text-[11px]">
          <div className="flex items-center justify-center gap-1.5 font-bold text-slate-600">
            <ShieldCheck className="h-4 w-4 text-[#0E675E]" />
            <span>100% Genuine Certified Medicines</span>
          </div>
          <p>Pharmacy License #DL-2024-HR-00492 • Rewari Dark Store Hub</p>
          <p>CommerceOS Healthcare Platform • Version 2.4.0</p>
        </div>
      </main>

      <MobileBottomNav />

      {/* EDIT PROFILE MODAL */}
      {isEditing && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl border border-slate-200 animate-scale-in">
            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
              <h3 className="text-sm font-extrabold text-slate-900">Edit Patient Profile</h3>
              <button
                onClick={() => setIsEditing(false)}
                className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center text-slate-500 hover:bg-slate-200"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleSaveProfile} className="space-y-3.5">
              <div>
                <label className="text-[11px] font-bold text-slate-500 block mb-1">Full Name</label>
                <input
                  type="text"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-xs font-semibold focus:outline-none focus:border-[#0E675E]"
                  required
                />
              </div>

              <div>
                <label className="text-[11px] font-bold text-slate-500 block mb-1">Phone Number</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-xs font-semibold focus:outline-none focus:border-[#0E675E]"
                  required
                />
              </div>

              <div>
                <label className="text-[11px] font-bold text-slate-500 block mb-1">Email (Optional)</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 text-xs font-semibold focus:outline-none focus:border-[#0E675E]"
                  placeholder="name@example.com"
                />
              </div>

              <div className="pt-2 flex gap-2">
                <button
                  type="button"
                  onClick={() => setIsEditing(false)}
                  className="flex-1 py-2.5 rounded-xl border border-slate-200 text-xs font-bold text-slate-600 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSaving}
                  className="flex-1 py-2.5 rounded-xl bg-[#0E675E] hover:bg-[#094640] text-white text-xs font-bold shadow-sm disabled:opacity-50"
                >
                  {isSaving ? 'Saving...' : 'Save Profile'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* NEED HELP BOTTOM SHEET / MODAL */}
      {isHelpOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="bg-white rounded-t-3xl sm:rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl border border-slate-200 animate-slide-up">
            <div className="flex items-center justify-between pb-2 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-full bg-emerald-50 text-[#0E675E] flex items-center justify-center">
                  <Headphones className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-sm font-extrabold text-slate-900">Pharmacy Helpdesk</h3>
                  <p className="text-[11px] text-slate-500">24x7 Assistance for Orders &amp; Medicine Advice</p>
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
              Have questions regarding medicine dosages, order status, or prescriptions? Connect directly with our certified pharmacists.
            </p>

            <div className="space-y-2.5 pt-2">
              <a
                href="https://wa.me/919817916180?text=Hello%20CommerceOS%20Pharmacy%2C%20I%20would%20like%20to%20consult%20a%20pharmacist."
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
                <span>Call 24x7 Helpline (+91 98179 16180)</span>
              </a>
            </div>
          </div>
        </div>
      )}

      {/* LOCATION PICKER MODAL */}
      <DeliveryAddressMapModal
        isOpen={isMapModalOpen}
        onClose={() => setIsMapModalOpen(false)}
        savedAddresses={addresses}
        currentAddress={selectedAddress}
        onSelectAddress={(selected) => {
          setSelectedAddress(selected);
          setIsMapModalOpen(false);
        }}
        onAddressSaved={loadData}
      />
    </div>
  );
}
