'use client';

import React, { useState } from 'react';
import { RefreshCw, CheckCircle2, Plus, ArrowLeft, ShoppingBag, Clock, Calendar, Zap, AlertCircle } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCart } from '@/lib/cart-store';
import UniversalHeader from '@/components/navigation/UniversalHeader';
import MobileBottomNav from '@/components/navigation/MobileBottomNav';

interface RefillSubscription {
  id: string;
  sku: string;
  medicineName: string;
  dosage: string;
  frequency: string;
  daysSupplyLeft: number;
  nextRefillDate: string;
  autoOrderEnabled: boolean;
  prescribingDoctor: string;
  unitPrice: number;
}

const INITIAL_SCHEDULES: RefillSubscription[] = [
  {
    id: 'refill_01',
    sku: 'TELMI-40',
    medicineName: 'Telmisartan 40mg (Micardis Equivalent)',
    dosage: '1 Tablet daily after breakfast',
    frequency: 'Every 30 Days',
    daysSupplyLeft: 4,
    nextRefillDate: 'in 4 days (8 Nov 2026)',
    autoOrderEnabled: true,
    prescribingDoctor: 'Dr. Neha Kapoor, Cardiologist',
    unitPrice: 85,
  },
  {
    id: 'refill_02',
    sku: 'GLYCO-GP1',
    medicineName: 'Glycomet-GP 1 Tablet PR',
    dosage: '1 Tablet twice daily with meals',
    frequency: 'Every 30 Days',
    daysSupplyLeft: 6,
    nextRefillDate: 'in 6 days (10 Nov 2026)',
    autoOrderEnabled: true,
    prescribingDoctor: 'Dr. Suresh Verma, MD',
    unitPrice: 110,
  },
  {
    id: 'refill_03',
    sku: 'PAN-40',
    medicineName: 'Pan 40mg Gastro-Resistant Tablet',
    dosage: '1 Tablet empty stomach in morning',
    frequency: 'Every 60 Days',
    daysSupplyLeft: 18,
    nextRefillDate: '22 Nov 2026',
    autoOrderEnabled: false,
    prescribingDoctor: 'Dr. R. K. Gupta, Gastroenterologist',
    unitPrice: 145,
  },
];

export default function RefillSubscriptionsPage() {
  const router = useRouter();
  const addToCart = useCart((s) => s.addItem);
  const [subscriptions, setSubscriptions] = useState<RefillSubscription[]>(INITIAL_SCHEDULES);
  const [showAddForm, setShowAddForm] = useState(false);
  const [newMedicine, setNewMedicine] = useState('');
  const [newDosage, setNewDosage] = useState('');
  const [newFrequency, setNewFrequency] = useState('Every 30 Days');
  const [newDoctor, setNewDoctor] = useState('');
  const [refillSuccessMsg, setRefillSuccessMsg] = useState<string | null>(null);

  const handleAddSchedule = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMedicine.trim()) return;

    const newSub: RefillSubscription = {
      id: `sub_${Date.now().toString(36)}`,
      sku: `MED-${Date.now().toString(36).toUpperCase()}`,
      medicineName: newMedicine.trim(),
      dosage: newDosage.trim() || 'As directed',
      frequency: newFrequency,
      daysSupplyLeft: 30,
      nextRefillDate: 'in 30 days',
      autoOrderEnabled: true,
      prescribingDoctor: newDoctor.trim() || 'Verified Physician',
      unitPrice: 95,
    };

    setSubscriptions([newSub, ...subscriptions]);
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

  const handleRefillNow = async (sub: RefillSubscription) => {
    await addToCart(
      {
        sku: sub.sku,
        name: sub.medicineName,
        unitPrice: sub.unitPrice,
        packSize: 'Strip of 10-15 Tablets',
      },
      1
    );
    setRefillSuccessMsg(`Added ${sub.medicineName} to cart!`);
    setTimeout(() => setRefillSuccessMsg(null), 3000);
    router.push('/checkout');
  };

  return (
    <div className="min-h-screen bg-[#F4F6F8] text-slate-900 flex flex-col font-sans antialiased pb-28">
      <UniversalHeader locationLabel="Rewari Hub" etaMinutes={10} />

      <main className="max-w-4xl w-full mx-auto px-4 sm:px-6 pt-5 space-y-4">
        {/* TOP TITLE */}
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <Link href="/account" className="text-slate-400 hover:text-slate-600 transition-colors">
                <ArrowLeft className="w-5 h-5" />
              </Link>
              <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                Medicine Refill Reminders
              </h1>
            </div>
            <p className="text-xs sm:text-sm text-slate-500 font-medium ml-7">
              Automatic replenishment schedules for chronic diabetes, BP & cardiac medicines
            </p>
          </div>

          <button
            onClick={() => setShowAddForm(!showAddForm)}
            className="px-4 py-2 rounded-xl bg-[#0E675E] hover:bg-[#0A4F48] text-white text-xs font-bold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
          >
            <Plus className="w-4 h-4" />
            <span>{showAddForm ? 'Cancel' : 'New Schedule'}</span>
          </button>
        </div>

        {/* FEEDBACK MSG */}
        {refillSuccessMsg && (
          <div className="p-3 bg-emerald-100 text-[#0E675E] text-xs font-bold rounded-xl flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4" />
            <span>{refillSuccessMsg}</span>
          </div>
        )}

        {/* ADD SCHEDULE FORM */}
        {showAddForm && (
          <form onSubmit={handleAddSchedule} className="bg-white rounded-2xl border border-slate-200/90 p-5 shadow-sm space-y-4">
            <h3 className="text-sm font-extrabold text-slate-900">Add Chronic Medication Schedule</h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Medication Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Metformin 500mg SR"
                  value={newMedicine}
                  onChange={(e) => setNewMedicine(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:ring-2 focus:ring-[#0E675E]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Dosage Instructions</label>
                <input
                  type="text"
                  placeholder="e.g. 1 Tablet Twice Daily"
                  value={newDosage}
                  onChange={(e) => setNewDosage(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:ring-2 focus:ring-[#0E675E]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Refill Cycle</label>
                <select
                  value={newFrequency}
                  onChange={(e) => setNewFrequency(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:ring-2 focus:ring-[#0E675E]"
                >
                  <option value="Every 15 Days">Every 15 Days</option>
                  <option value="Every 30 Days">Every 30 Days (Standard 1 Month)</option>
                  <option value="Every 60 Days">Every 60 Days (2 Months)</option>
                  <option value="Every 90 Days">Every 90 Days (Quarterly)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">Prescribing Doctor</label>
                <input
                  type="text"
                  placeholder="e.g. Dr. Rajesh Sharma, MD"
                  value={newDoctor}
                  onChange={(e) => setNewDoctor(e.target.value)}
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:ring-2 focus:ring-[#0E675E]"
                />
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowAddForm(false)}
                className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-bold text-slate-700"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-5 py-2 bg-[#0E675E] text-white font-bold text-xs rounded-xl"
              >
                Save Refill Schedule
              </button>
            </div>
          </form>
        )}

        {/* REFILL SCHEDULE CARDS */}
        <div className="space-y-3.5">
          {subscriptions.map((sub) => {
            const isUrgent = sub.daysSupplyLeft <= 5;
            return (
              <div
                key={sub.id}
                className={`bg-white rounded-2xl border p-5 shadow-2xs space-y-3 transition-all ${
                  isUrgent ? 'border-amber-300 ring-1 ring-amber-300/50' : 'border-slate-200/80'
                }`}
              >
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="px-2.5 py-0.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200 text-[10px] font-black uppercase flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        <span>{sub.frequency}</span>
                      </span>
                      {isUrgent && (
                        <span className="px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-800 border border-amber-200 text-[10px] font-black uppercase">
                          Refill Soon ({sub.daysSupplyLeft} days left)
                        </span>
                      )}
                    </div>
                    <h3 className="text-base font-black text-slate-900 leading-tight">
                      {sub.medicineName}
                    </h3>
                    <p className="text-xs text-slate-600 font-medium">
                      Dosage: <strong className="text-slate-800">{sub.dosage}</strong> • {sub.prescribingDoctor}
                    </p>
                  </div>

                  <div className="flex flex-col sm:items-end">
                    <span className="text-sm font-black text-slate-900">
                      ₹{sub.unitPrice}
                    </span>
                    <span className="text-[11px] text-slate-500 font-medium">
                      Next Delivery: {sub.nextRefillDate}
                    </span>
                  </div>
                </div>

                {/* Refill Now CTA & Auto-Order Switch */}
                <div className="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3">
                  <button
                    onClick={() => handleToggleAutoOrder(sub.id)}
                    className="text-xs font-bold text-slate-500 hover:text-slate-800 flex items-center gap-1.5"
                  >
                    <CheckCircle2 className={`w-4 h-4 ${sub.autoOrderEnabled ? 'text-[#0E675E]' : 'text-slate-400'}`} />
                    <span>{sub.autoOrderEnabled ? 'Auto-Order Active' : 'Auto-Order Paused'}</span>
                  </button>

                  <button
                    onClick={() => handleRefillNow(sub)}
                    className="px-4 py-2 rounded-xl bg-[#0E675E] hover:bg-[#0A4F48] text-white text-xs font-black uppercase tracking-wider flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
                  >
                    <ShoppingBag className="w-3.5 h-3.5" />
                    <span>Refill Now into Cart</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </main>

      <MobileBottomNav />
    </div>
  );
}
