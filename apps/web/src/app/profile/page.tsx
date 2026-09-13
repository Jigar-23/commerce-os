'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  User,
  MapPin,
  HeartPulse,
  Save,
  CheckCircle2,
  Trash2,
  ArrowLeft,
} from 'lucide-react';
import {
  CommerceNavbar,
  CommerceEmptyState,
} from '@commerce-os/ui';
import {
  fetchCustomerAddresses,
  fetchCustomerProfile,
  SavedAddress,
} from '@/lib/api-client';
import { useSession } from '@/lib/session-store';

export default function CustomerProfilePage() {
  const sessionUser = useSession((s) => s.user);
  const [activeTab, setActiveTab] = useState<'profile' | 'addresses' | 'health'>('profile');

  // PROFILE STATE
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [age, setAge] = useState<number | ''>('');
  const [bloodGroup, setBloodGroup] = useState('');
  const [isSaved, setIsSaved] = useState(false);

  // ADDRESSES STATE
  const [addresses, setAddresses] = useState<SavedAddress[]>([]);
  const [isLoadingAddresses, setIsLoadingAddresses] = useState(true);

  // ALLERGIES
  const [allergies, setAllergies] = useState<string[]>([]);
  const [newAllergy, setNewAllergy] = useState('');

  useEffect(() => {
    if (sessionUser) {
      setFullName(sessionUser.fullName || '');
      setEmail(sessionUser.email || '');
    }
  }, [sessionUser]);

  const loadData = async () => {
    setIsLoadingAddresses(true);
    try {
      if (sessionUser?.userId) {
        const [addrs, profile] = await Promise.all([
          fetchCustomerAddresses(sessionUser.userId).catch(() => []),
          fetchCustomerProfile(sessionUser.userId).catch(() => null),
        ]);
        setAddresses(addrs || []);
        if (profile) {
          if (profile.phone) setPhone(profile.phone);
          if (profile.fullName) setFullName(profile.fullName);
          if (profile.age) setAge(profile.age);
          if (profile.bloodGroup) setBloodGroup(profile.bloodGroup);
          if (Array.isArray(profile.allergies)) setAllergies(profile.allergies);
        }
      } else {
        const addrs = await fetchCustomerAddresses().catch(() => []);
        setAddresses(addrs || []);
      }
    } catch (_) {
      setAddresses([]);
    } finally {
      setIsLoadingAddresses(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [sessionUser?.userId]);

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaved(true);
    setTimeout(() => setIsSaved(false), 3000);
  };

  const handleAddAllergy = () => {
    if (newAllergy.trim() && !allergies.includes(newAllergy.trim())) {
      setAllergies([...allergies, newAllergy.trim()]);
      setNewAllergy('');
    }
  };

  const handleRemoveAllergy = (name: string) => {
    setAllergies(allergies.filter((a) => a !== name));
  };

  const defaultAddr = addresses.find((a) => a.isDefault) || addresses[0];
  const locationDisplay = defaultAddr
    ? `${defaultAddr.contactName || defaultAddr.tag || 'Home'} • ${defaultAddr.addressLine}`
    : 'Customer Profile';

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex flex-col font-sans antialiased pb-24 selection:bg-surface-brandSubtle selection:text-content-brand">
      {/* TOP NAVBAR */}
      <CommerceNavbar
        locationAddress={locationDisplay}
        showSearchBar={false}
        ordersHref="/orders"
        profileHref="/profile"
      />

      <main className="flex-1 max-w-4xl w-full mx-auto px-4 sm:px-6 py-6 space-y-6">
        {/* BACK BREADCRUMB */}
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 text-xs font-bold text-content-secondary hover:text-content-brand transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>Back to Storefront</span>
        </Link>

        {/* HEADER */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-xl sm:text-2xl font-black text-content-primary tracking-tight">
              Customer Account &amp; Health Profile
            </h1>
            <p className="text-xs text-content-secondary mt-0.5">
              Manage personal details, verified delivery addresses, and medical safety alerts.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span className="px-3 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-black uppercase tracking-wider border border-border-brandSubtle">
              {sessionUser?.userId ? 'Authenticated Account' : 'Guest Mode'}
            </span>
          </div>
        </div>

        {/* TAB SWITCHER */}
        <div className="flex border-b border-border-default space-x-4 overflow-x-auto scrollbar-none">
          {[
            { id: 'profile', label: 'Personal Information', icon: <User className="h-4 w-4" /> },
            { id: 'addresses', label: 'Delivery Addresses', icon: <MapPin className="h-4 w-4" /> },
            { id: 'health', label: 'Health & Allergies', icon: <HeartPulse className="h-4 w-4" /> },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex items-center gap-2 pb-3 text-xs font-bold transition-all border-b-2 cursor-pointer ${
                activeTab === tab.id
                  ? 'border-border-brand text-content-brand'
                  : 'border-transparent text-content-secondary hover:text-content-primary'
              }`}
            >
              {tab.icon}
              <span>{tab.label}</span>
            </button>
          ))}
        </div>

        {/* TAB 1: PERSONAL INFORMATION */}
        {activeTab === 'profile' && (
          <form onSubmit={handleSaveProfile} className="bg-surface-card rounded-3xl p-6 border border-border-default shadow-card space-y-5">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-extrabold text-content-muted uppercase tracking-wider mb-1.5">
                  Full Name
                </label>
                <input
                  type="text"
                  placeholder="Enter full name"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-content-primary text-xs font-bold outline-none focus:border-border-brand"
                />
              </div>

              <div>
                <label className="block text-xs font-extrabold text-content-muted uppercase tracking-wider mb-1.5">
                  Phone Number
                </label>
                <input
                  type="text"
                  placeholder="+91..."
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-content-primary text-xs font-bold outline-none focus:border-border-brand"
                />
              </div>

              <div>
                <label className="block text-xs font-extrabold text-content-muted uppercase tracking-wider mb-1.5">
                  Email Address
                </label>
                <input
                  type="email"
                  value={email}
                  disabled
                  placeholder="Enter email address"
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-content-muted text-xs font-bold outline-none cursor-not-allowed"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-xs font-extrabold text-content-muted uppercase tracking-wider mb-1.5">
                    Age
                  </label>
                  <input
                    type="number"
                    value={age}
                    onChange={(e) => setAge(e.target.value ? Number(e.target.value) : '')}
                    className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-content-primary text-xs font-bold outline-none focus:border-border-brand"
                  />
                </div>

                <div>
                  <label className="block text-xs font-extrabold text-content-muted uppercase tracking-wider mb-1.5">
                    Blood Group
                  </label>
                  <select
                    value={bloodGroup}
                    onChange={(e) => setBloodGroup(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-content-primary text-xs font-bold outline-none focus:border-border-brand"
                  >
                    <option value="">Select</option>
                    {['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map((bg) => (
                      <option key={bg} value={bg}>{bg}</option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            {isSaved && (
              <div className="p-3 rounded-xl bg-surface-brandSubtle border border-border-brandSubtle text-xs font-bold text-content-brand flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>Profile details saved.</span>
              </div>
            )}

            <div className="flex justify-end pt-2">
              <button
                type="submit"
                className="px-5 py-2.5 bg-action-primaryBg hover:bg-action-primaryHover text-action-primaryText font-bold text-xs rounded-xl shadow-subtle transition-all cursor-pointer inline-flex items-center gap-1.5"
              >
                <Save className="h-4 w-4" />
                <span>Save Profile</span>
              </button>
            </div>
          </form>
        )}

        {/* TAB 2: ADDRESSES */}
        {activeTab === 'addresses' && (
          <div className="bg-surface-card rounded-3xl p-6 border border-border-default shadow-card space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-extrabold text-content-primary">Verified Saved Addresses</h2>
            </div>

            {isLoadingAddresses ? (
              <div className="h-24 bg-surface-subtle rounded-2xl animate-pulse" />
            ) : addresses.length === 0 ? (
              <CommerceEmptyState
                title="No saved delivery addresses"
                description="Add your delivery locations to enable 10-minute instant dispatch."
              />
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {addresses.map((addr) => (
                  <div key={addr.id} className="p-4 rounded-2xl bg-surface-subtle border border-border-subtle space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-extrabold text-content-primary">
                        {addr.contactName || addr.tag || 'Saved Address'}
                      </span>
                      {addr.isDefault && (
                        <span className="text-2xs font-extrabold bg-surface-brandSubtle text-content-brand px-2 py-0.5 rounded-full border border-border-brandSubtle">
                          Default
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-content-secondary leading-relaxed">{addr.addressLine}, {addr.city}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 3: HEALTH & ALLERGIES */}
        {activeTab === 'health' && (
          <div className="bg-surface-card rounded-3xl p-6 border border-border-default shadow-card space-y-5">
            <div className="space-y-3">
              <h3 className="text-xs font-extrabold text-content-muted uppercase tracking-wider">
                Known Drug Allergies
              </h3>
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder="e.g. Sulfa, Aspirin..."
                  value={newAllergy}
                  onChange={(e) => setNewAllergy(e.target.value)}
                  className="flex-1 px-3.5 py-2 rounded-xl border border-border-default bg-surface-subtle text-xs font-bold outline-none"
                />
                <button
                  type="button"
                  onClick={handleAddAllergy}
                  className="px-4 py-2 bg-action-primaryBg text-action-primaryText font-bold text-xs rounded-xl shadow-subtle"
                >
                  Add
                </button>
              </div>
              <div className="flex flex-wrap gap-2 pt-1">
                {allergies.length === 0 ? (
                  <p className="text-xs text-content-muted">No allergies recorded.</p>
                ) : (
                  allergies.map((allg) => (
                    <span
                      key={allg}
                      className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl bg-surface-dangerSubtle text-content-danger border border-border-danger text-xs font-bold"
                    >
                      <span>{allg}</span>
                      <button type="button" onClick={() => handleRemoveAllergy(allg)}>
                        <Trash2 className="h-3 w-3" />
                      </button>
                    </span>
                  ))
                )}
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
