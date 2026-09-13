'use client';

import React, { useState } from 'react';
import { RefreshCw, CheckCircle2, Plus, ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { CommerceNavbar, CommerceEmptyState } from '@commerce-os/ui';
import { useSession } from '@/lib/session-store';

interface RefillSubscription {
  id: string;
  medicineName: string;
  dosage: string;
  frequency: string;
  nextRefillDate: string;
  autoOrderEnabled: boolean;
  prescribingDoctor: string;
}

export default function RefillSubscriptionsPage() {
  const sessionUser = useSession((s) => s.user);
  const [subscriptions, setSubscriptions] = useState<RefillSubscription[]>([]);
  const [showAddForm, setShowAddForm] = useState(false);
  const [newMedicine, setNewMedicine] = useState('');
  const [newDosage, setNewDosage] = useState('');
  const [newFrequency, setNewFrequency] = useState('Every 30 Days');
  const [newDoctor, setNewDoctor] = useState('');

  const handleAddSchedule = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMedicine.trim()) return;

    const newSub: RefillSubscription = {
      id: `sub_${Date.now().toString(36)}`,
      medicineName: newMedicine.trim(),
      dosage: newDosage.trim() || 'As directed',
      frequency: newFrequency,
      nextRefillDate: new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0],
      autoOrderEnabled: true,
      prescribingDoctor: newDoctor.trim() || 'Verified Physician',
    };

    setSubscriptions([...subscriptions, newSub]);
    setNewMedicine('');
    setNewDosage('');
    setNewDoctor('');
    setShowAddForm(false);
  };

  const handleToggleAutoOrder = (id: string) => {
    setSubscriptions(
      subscriptions.map((s) =>
        s.id === id ? { ...s, autoOrderEnabled: !s.autoOrderEnabled } : s
      )
    );
  };

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex flex-col font-sans antialiased pb-24">
      {/* NAVBAR */}
      <CommerceNavbar
        locationAddress="Auto-Refills"
        showSearchBar={false}
        ordersHref="/orders"
        profileHref="/profile"
      />

      <main className="max-w-4xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
        {/* BACK LINK */}
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 text-xs font-bold text-content-secondary hover:text-content-primary transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>Back to Storefront</span>
        </Link>

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <span className="text-2xs font-extrabold text-content-brand uppercase tracking-wider bg-surface-brandSubtle px-2.5 py-1 rounded-full border border-border-brandSubtle">
              Chronic Care Engine
            </span>
            <h1 className="text-xl sm:text-2xl font-black text-content-primary tracking-tight mt-1">
              Prescription Refill Schedules
            </h1>
            <p className="text-xs text-content-secondary mt-0.5">
              Automate routine chronic medications with verified doctor prescriptions.
            </p>
          </div>

          <button
            onClick={() => setShowAddForm(!showAddForm)}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-action-primaryBg hover:bg-action-primaryHover text-action-primaryText font-bold text-xs shadow-subtle transition-all active:scale-98 cursor-pointer shrink-0"
          >
            <Plus className="h-4 w-4" />
            <span>{showAddForm ? 'Cancel' : 'Add Refill Schedule'}</span>
          </button>
        </div>

        {showAddForm && (
          <form onSubmit={handleAddSchedule} className="rounded-3xl border border-border-default bg-surface-card p-6 shadow-card space-y-4">
            <h3 className="text-sm font-extrabold">Schedule New Refill</h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold text-content-muted mb-1">Medication Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Metformin 500mg"
                  value={newMedicine}
                  onChange={(e) => setNewMedicine(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-xs font-bold"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-content-muted mb-1">Dosage Instructions</label>
                <input
                  type="text"
                  placeholder="e.g. 1 Tablet Twice Daily"
                  value={newDosage}
                  onChange={(e) => setNewDosage(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-xs font-bold"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-content-muted mb-1">Frequency</label>
                <select
                  value={newFrequency}
                  onChange={(e) => setNewFrequency(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-xs font-bold"
                >
                  <option value="Every 15 Days">Every 15 Days</option>
                  <option value="Every 30 Days">Every 30 Days</option>
                  <option value="Every 60 Days">Every 60 Days</option>
                  <option value="Every 90 Days">Every 90 Days</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-content-muted mb-1">Prescribing Doctor</label>
                <input
                  type="text"
                  placeholder="e.g. Dr. Sharma (Reg #MED-1234)"
                  value={newDoctor}
                  onChange={(e) => setNewDoctor(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-border-default bg-surface-subtle text-xs font-bold"
                />
              </div>
            </div>
            <div className="flex justify-end">
              <button
                type="submit"
                className="px-5 py-2.5 bg-action-primaryBg text-action-primaryText font-bold text-xs rounded-xl"
              >
                Save Schedule
              </button>
            </div>
          </form>
        )}

        {/* REFILL CARDS */}
        {subscriptions.length === 0 ? (
          <CommerceEmptyState
            title="No Active Refill Schedules"
            description="Configure automated monthly deliveries for chronic medications to ensure uninterrupted care."
          />
        ) : (
          <div className="space-y-4">
            {subscriptions.map((sub) => (
              <div
                key={sub.id}
                className="rounded-3xl border border-border-default bg-surface-card p-6 shadow-card space-y-4"
              >
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                  <div className="space-y-1.5">
                    <span className="inline-flex items-center gap-1 rounded-md bg-surface-brandSubtle px-2.5 py-1 text-2xs font-extrabold text-content-brand border border-border-brandSubtle">
                      <RefreshCw className="h-3 w-3" />
                      <span>{sub.frequency}</span>
                    </span>
                    <h3 className="text-base font-extrabold text-content-primary">{sub.medicineName}</h3>
                    <p className="text-xs text-content-secondary font-medium">
                      Dosage: <strong className="text-content-primary">{sub.dosage}</strong> • Prescribed by {sub.prescribingDoctor}
                    </p>
                  </div>

                  <div className="text-left sm:text-right space-y-1">
                    <span className="text-xs font-black text-content-brand flex items-center gap-1 sm:justify-end">
                      <CheckCircle2 className="h-4 w-4" />
                      <span>{sub.autoOrderEnabled ? 'Auto-Dispatch Active' : 'Paused'}</span>
                    </span>
                    <p className="text-2xs text-content-muted">Next Scheduled Delivery: {sub.nextRefillDate}</p>
                  </div>
                </div>

                <div className="pt-3 border-t border-border-subtle flex items-center justify-between text-2xs text-content-muted">
                  <span>Fulfilled with temperature-controlled cold chain logistics</span>
                  <button
                    onClick={() => handleToggleAutoOrder(sub.id)}
                    className="font-bold text-content-secondary hover:text-content-danger transition-colors cursor-pointer"
                  >
                    {sub.autoOrderEnabled ? 'Pause Schedule' : 'Resume Schedule'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
