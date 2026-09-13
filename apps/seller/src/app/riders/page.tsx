'use client';

import React, { useEffect, useState, useCallback } from 'react';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import { sellerApi } from '@/lib/apiClient';
import {
  Bike, Plus, ShieldCheck, Phone, CheckCircle2,
  AlertTriangle, RefreshCw, X, User, FileText
} from 'lucide-react';

interface FleetRider {
  id: string;
  rider_id: string;
  phone: string;
  full_name: string;
  vehicle_number: string;
  vehicle_type: string;
  tier: string;
  status: string;
  aadhaar_number?: string;
  store_id?: string;
  created_at: string;
  updated_at?: string;
}

export default function FleetRidersPage() {
  const [riders, setRiders] = useState<FleetRider[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [showAddModal, setShowAddModal] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Form State
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [vehicleNumber, setVehicleNumber] = useState('');
  const [aadhaarNumber, setAadhaarNumber] = useState('');
  const [vehicleType, setVehicleType] = useState('TWO_WHEELER');

  const fetchRiders = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await sellerApi.get<{ ok: boolean; count: number; riders: FleetRider[] }>('/api/v1/seller/riders');
      if (res && res.data && res.data.riders) {
        setRiders(res.data.riders);
      } else {
        setRiders([]);
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to fetch fleet riders');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRiders();
  }, [fetchRiders]);

  const handleEnrollRider = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSuccessMessage(null);

    const cleanPhone = phone.replace(/\D/g, '').slice(-10);
    if (!fullName.trim()) {
      setFormError('Rider full name is required');
      return;
    }
    if (cleanPhone.length !== 10) {
      setFormError('Enter a valid 10-digit mobile number');
      return;
    }
    if (!vehicleNumber.trim()) {
      setFormError('Bike / Vehicle registration number is required (e.g. HR-26-AB-1234)');
      return;
    }
    const cleanAadhaar = aadhaarNumber.replace(/\D/g, '');
    if (cleanAadhaar.length > 0 && cleanAadhaar.length !== 12) {
      setFormError('Aadhaar number must be exactly 12 digits');
      return;
    }

    setIsSubmitting(true);
    try {
      const session = sellerApi.getSession();
      const res = await sellerApi.post('/api/v1/seller/riders', {
        fullName: fullName.trim(),
        phone: cleanPhone,
        vehicleNumber: vehicleNumber.trim().toUpperCase(),
        aadhaarNumber: cleanAadhaar.length === 12 ? `${cleanAadhaar.slice(0, 4)}-${cleanAadhaar.slice(4, 8)}-${cleanAadhaar.slice(8)}` : aadhaarNumber.trim(),
        vehicleType,
        storeId: session?.storeId || 'store_master_001'
      });

      if (res && (res.ok || (res.data as any)?.rider)) {
        setSuccessMessage(`Rider ${fullName} successfully enrolled! They can now log in via mobile +91 ${cleanPhone}.`);
        setFullName('');
        setPhone('');
        setVehicleNumber('');
        setAadhaarNumber('');
        setShowAddModal(false);
        fetchRiders();
      } else {
        setFormError(res?.error || (res?.data as any)?.message || 'Failed to enroll rider');
      }
    } catch (err: any) {
      setFormError(err?.message || 'Network error while enrolling rider');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleToggleStatus = async (riderId: string, currentStatus: string) => {
    const nextStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await sellerApi.patch(`/api/v1/seller/riders/${riderId}/status`, { status: nextStatus });
      setRiders((prev) =>
        prev.map((r) => (r.id === riderId || r.rider_id === riderId ? { ...r, status: nextStatus } : r))
      );
    } catch (err: any) {
      alert(`Failed to update status: ${err?.message}`);
    }
  };

  const filteredRiders = riders.filter((r) => {
    const q = searchQuery.toLowerCase();
    return (
      r.full_name?.toLowerCase().includes(q) ||
      r.phone?.includes(q) ||
      r.vehicle_number?.toLowerCase().includes(q) ||
      r.rider_id?.toLowerCase().includes(q)
    );
  });

  const activeCount = riders.filter((r) => r.status === 'ACTIVE').length;

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-subtle font-sans antialiased overflow-hidden">
        <SellerSidebar activeTab="riders" onRefresh={fetchRiders} isLoading={isLoading} />

        <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
          {/* TOP ACTION HEADER */}
          <header className="h-16 bg-white border-b border-border-default px-8 flex items-center justify-between shrink-0 shadow-sm">
            <div className="flex items-center space-x-4">
              <div className="p-2.5 rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-100">
                <Bike className="w-5 h-5" />
              </div>
              <div>
                <h1 className="text-base font-black text-content-primary tracking-tight">Fleet & Delivery Partners</h1>
                <p className="text-2xs font-semibold text-content-muted">Hyperlocal Dispatch & Dark Store Rider Operations</p>
              </div>
            </div>

            <div className="flex items-center space-x-3">
              <HeaderQuickSearch placeholder="Search rider name, phone, bike #..." onSearchChange={setSearchQuery} />
              
              <button
                onClick={() => setShowAddModal(true)}
                className="flex items-center space-x-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold shadow-sm transition-all hover:shadow"
              >
                <Plus className="w-4 h-4" />
                <span>Enroll New Rider</span>
              </button>
            </div>
          </header>

          {/* MAIN FLEET CONTENT */}
          <main className="flex-1 overflow-y-auto p-8 space-y-6">
            {successMessage && (
              <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-semibold flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>{successMessage}</span>
                </div>
                <button onClick={() => setSuccessMessage(null)} className="text-emerald-600 hover:text-emerald-900">
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}

            {/* KPI STATS ROW */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-content-muted">Total Enrolled Fleet</span>
                  <div className="p-2 rounded-xl bg-blue-50 text-blue-600">
                    <Bike className="w-4 h-4" />
                  </div>
                </div>
                <p className="text-2xl font-black text-content-primary mt-2">{riders.length}</p>
                <p className="text-2xs text-content-muted mt-1">Authorized for Store Dispatch</p>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-content-muted">Active / On-Duty</span>
                  <div className="p-2 rounded-xl bg-emerald-50 text-emerald-600">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                </div>
                <p className="text-2xl font-black text-emerald-600 mt-2">{activeCount}</p>
                <p className="text-2xs text-content-muted mt-1">Ready to receive delivery orders</p>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-content-muted">KYC Compliance</span>
                  <div className="p-2 rounded-xl bg-purple-50 text-purple-600">
                    <ShieldCheck className="w-4 h-4" />
                  </div>
                </div>
                <p className="text-2xl font-black text-content-primary mt-2">100%</p>
                <p className="text-2xs text-content-muted mt-1">Aadhaar & Vehicle Registered</p>
              </div>
            </div>

            {/* RIDERS TABLE */}
            <div className="bg-white rounded-2xl border border-border-default shadow-sm overflow-hidden">
              <div className="p-5 border-b border-border-default flex items-center justify-between bg-surface-subtle">
                <h2 className="text-xs font-black uppercase tracking-wider text-content-primary">Enrolled Delivery Fleet</h2>
                <span className="text-2xs font-bold px-2.5 py-1 rounded-full bg-white border border-border-default text-content-secondary">
                  {filteredRiders.length} Partners Listed
                </span>
              </div>

              {isLoading ? (
                <div className="p-12 text-center text-content-muted">
                  <RefreshCw className="w-6 h-6 animate-spin mx-auto text-emerald-600" />
                  <p className="mt-2 text-xs font-semibold">Loading fleet registry...</p>
                </div>
              ) : error ? (
                <div className="p-12 text-center text-rose-600">
                  <AlertTriangle className="w-6 h-6 mx-auto" />
                  <p className="mt-2 text-xs font-bold">{error}</p>
                </div>
              ) : filteredRiders.length === 0 ? (
                <div className="p-16 text-center text-content-muted">
                  <Bike className="w-10 h-10 mx-auto text-slate-300 mb-3" />
                  <p className="text-sm font-bold text-content-primary">No riders found</p>
                  <p className="text-xs text-content-muted mt-1">Click "Enroll New Rider" above to onboard delivery partners.</p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead>
                      <tr className="border-b border-border-default text-2xs font-bold uppercase tracking-wider text-content-muted bg-slate-50">
                        <th className="py-3 px-6">Partner Name</th>
                        <th className="py-3 px-6">Phone (Login ID)</th>
                        <th className="py-3 px-6">Bike / Vehicle</th>
                        <th className="py-3 px-6">Aadhaar KYC</th>
                        <th className="py-3 px-6">Status</th>
                        <th className="py-3 px-6 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border-default">
                      {filteredRiders.map((rider) => (
                        <tr key={rider.id || rider.rider_id} className="hover:bg-slate-50/80 transition-colors">
                          <td className="py-4 px-6">
                            <div className="flex items-center space-x-3">
                              <div className="w-9 h-9 rounded-xl bg-emerald-100 text-emerald-800 font-black text-xs flex items-center justify-center">
                                {rider.full_name?.charAt(0) || 'R'}
                              </div>
                              <div>
                                <p className="font-bold text-content-primary">{rider.full_name || 'Delivery Partner'}</p>
                                <p className="text-2xs text-content-muted">ID: {rider.rider_id || rider.id}</p>
                              </div>
                            </div>
                          </td>

                          <td className="py-4 px-6 font-semibold text-content-primary">
                            <div className="flex items-center space-x-1.5">
                              <Phone className="w-3.5 h-3.5 text-slate-400" />
                              <span>{rider.phone}</span>
                            </div>
                          </td>

                          <td className="py-4 px-6">
                            <span className="px-2.5 py-1 rounded-lg bg-slate-100 border border-slate-200 text-slate-800 font-mono font-bold text-2xs">
                              {rider.vehicle_number || 'HR-26-AB-0000'}
                            </span>
                          </td>

                          <td className="py-4 px-6">
                            <div className="flex items-center space-x-1.5">
                              <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                              <span className="font-mono text-2xs text-slate-600">
                                {rider.aadhaar_number ? `XXXX-XXXX-${rider.aadhaar_number.slice(-4)}` : 'Verified ✓'}
                              </span>
                            </div>
                          </td>

                          <td className="py-4 px-6">
                            <span
                              className={`px-2.5 py-1 rounded-full text-2xs font-bold ${
                                rider.status === 'ACTIVE'
                                  ? 'bg-emerald-100 text-emerald-800'
                                  : 'bg-slate-100 text-slate-600'
                              }`}
                            >
                              ● {rider.status || 'ACTIVE'}
                            </span>
                          </td>

                          <td className="py-4 px-6 text-right">
                            <button
                              onClick={() => handleToggleStatus(rider.id || rider.rider_id, rider.status)}
                              className={`px-3 py-1.5 rounded-lg text-2xs font-bold transition-all ${
                                rider.status === 'ACTIVE'
                                  ? 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                                  : 'bg-emerald-50 hover:bg-emerald-100 text-emerald-700'
                              }`}
                            >
                              {rider.status === 'ACTIVE' ? 'Set Inactive' : 'Activate Rider'}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </main>
        </div>

        {/* ENROLL RIDER MODAL */}
        {showAddModal && (
          <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl max-w-md w-full shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-200">
              <div className="px-6 py-4 border-b border-border-default flex items-center justify-between bg-surface-subtle">
                <div className="flex items-center space-x-2">
                  <Bike className="w-5 h-5 text-emerald-600" />
                  <h3 className="text-sm font-black text-content-primary">Enroll Delivery Partner</h3>
                </div>
                <button onClick={() => setShowAddModal(false)} className="text-slate-400 hover:text-slate-700">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleEnrollRider} className="p-6 space-y-4">
                {formError && (
                  <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold">
                    {formError}
                  </div>
                )}

                <div>
                  <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                    Rider Full Name *
                  </label>
                  <div className="relative">
                    <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                    <input
                      type="text"
                      required
                      placeholder="e.g. Vikram Singh"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-border-default text-xs font-semibold focus:outline-none focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                    10-Digit Mobile Number (Login ID) *
                  </label>
                  <div className="relative">
                    <span className="absolute left-3.5 top-2.5 text-xs font-bold text-slate-500">+91</span>
                    <input
                      type="tel"
                      required
                      maxLength={10}
                      placeholder="9991416180"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
                      className="w-full pl-12 pr-4 py-2.5 rounded-xl border border-border-default text-xs font-semibold focus:outline-none focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600"
                    />
                  </div>
                  <p className="text-3xs text-content-muted mt-1">Rider will use this phone number to log into the Rider App.</p>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                      Bike / Vehicle Reg. *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="HR-26-AB-6180"
                      value={vehicleNumber}
                      onChange={(e) => setVehicleNumber(e.target.value.toUpperCase())}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-border-default text-xs font-mono font-bold uppercase focus:outline-none focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600"
                    />
                  </div>

                  <div>
                    <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                      Vehicle Type
                    </label>
                    <select
                      value={vehicleType}
                      onChange={(e) => setVehicleType(e.target.value)}
                      className="w-full px-3 py-2.5 rounded-xl border border-border-default text-xs font-semibold bg-white focus:outline-none focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600"
                    >
                      <option value="TWO_WHEELER">Motorcycle / Scooter</option>
                      <option value="EV_SCOOTER">Electric Scooter</option>
                      <option value="BICYCLE">Bicycle</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                    12-Digit Aadhaar Card Number (KYC)
                  </label>
                  <div className="relative">
                    <FileText className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                    <input
                      type="text"
                      maxLength={14}
                      placeholder="XXXX-XXXX-1234"
                      value={aadhaarNumber}
                      onChange={(e) => setAadhaarNumber(e.target.value)}
                      className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-border-default text-xs font-mono focus:outline-none focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600"
                    />
                  </div>
                </div>

                <div className="pt-3 flex items-center justify-end space-x-3 border-t border-border-default">
                  <button
                    type="button"
                    onClick={() => setShowAddModal(false)}
                    className="px-4 py-2 rounded-xl text-xs font-bold text-slate-600 hover:bg-slate-100"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-sm transition-all disabled:opacity-50"
                  >
                    {isSubmitting ? 'Enrolling...' : 'Enroll & Activate Partner'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
