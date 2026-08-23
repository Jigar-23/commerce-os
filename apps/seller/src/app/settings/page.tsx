'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { CheckCircle2, Save, Zap, Store, ShieldCheck, AlertCircle, RefreshCw } from 'lucide-react';
import { sellerApi } from '@/lib/apiClient';

export default function SettingsPage() {
  const [storeName, setStoreName] = useState('Central Warehouse Outlet');
  const [supportPhone, setSupportPhone] = useState('+1-800-MERCHANT');
  const [fulfillmentRadius, setFulfillmentRadius] = useState('15 km');
  const [codEnabled, setCodEnabled] = useState(true);
  const [sellerApprovalRequired, setSellerApprovalRequired] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaved, setIsSaved] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    async function loadSettings() {
      try {
        setIsLoading(true);
        const res = await sellerApi.get('/api/v1/seller/store/settings');
        if (res.ok && res.data) {
          if (res.data.storeName) setStoreName(res.data.storeName);
          if (res.data.sellerApprovalRequired !== undefined) {
            setSellerApprovalRequired(Boolean(res.data.sellerApprovalRequired));
          }
        }
      } catch (err: any) {
        console.error('Failed to load store settings:', err);
      } finally {
        setIsLoading(false);
      }
    }
    loadSettings();
  }, []);

  const handleToggleDispatchMode = async () => {
    const nextVal = !sellerApprovalRequired;
    setSellerApprovalRequired(nextVal);
    try {
      const res = await sellerApi.patch('/api/v1/seller/store/settings', {
        sellerApprovalRequired: nextVal,
      });
      if (res.ok) {
        setIsSaved(true);
        setErrorMsg(null);
        setTimeout(() => setIsSaved(false), 3000);
      } else {
        setErrorMsg(res.error || 'Failed to update store dispatch mode');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Network error updating settings');
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await sellerApi.patch('/api/v1/seller/store/settings', {
        storeName,
        sellerApprovalRequired,
      });
      if (res.ok) {
        setIsSaved(true);
        setErrorMsg(null);
        setTimeout(() => setIsSaved(false), 3000);
      } else {
        setErrorMsg(res.error || 'Failed to save settings');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Network error saving settings');
    }
  };

  return (
    <div className="min-h-screen bg-[#0a0d14] text-white p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Navigation */}
        <div className="flex items-center justify-between border-b border-[#1e293b] pb-4">
          <div>
            <h1 className="text-2xl font-black text-white">Commerce OS — Seller Merchant OS</h1>
            <p className="text-xs text-gray-400">Merchant Settings, Roles &amp; Fulfillment Node Options</p>
          </div>
          <div className="flex items-center space-x-3 text-sm">
            <Link href="/" className="px-3 py-1.5 rounded-lg bg-[#111827] hover:bg-[#1f2937] text-gray-300">Dashboard</Link>
            <Link href="/orders" className="px-3 py-1.5 rounded-lg bg-[#111827] hover:bg-[#1f2937] text-gray-300">Orders</Link>
            <Link href="/products" className="px-3 py-1.5 rounded-lg bg-[#111827] hover:bg-[#1f2937] text-gray-300">Products</Link>
            <Link href="/inventory" className="px-3 py-1.5 rounded-lg bg-[#111827] hover:bg-[#1f2937] text-gray-300">Inventory</Link>
            <Link href="/pricing" className="px-3 py-1.5 rounded-lg bg-[#111827] hover:bg-[#1f2937] text-gray-300">Pricing</Link>
            <Link href="/promotions" className="px-3 py-1.5 rounded-lg bg-[#111827] hover:bg-[#1f2937] text-gray-300">Promotions</Link>
            <Link href="/settlements" className="px-3 py-1.5 rounded-lg bg-[#111827] hover:bg-[#1f2937] text-gray-300">Settlements</Link>
            <Link href="/settings" className="px-3 py-1.5 rounded-lg bg-emerald-600 text-white font-bold">Settings</Link>
          </div>
        </div>

        {/* Settings Form */}
        <form onSubmit={handleSave} className="bg-[#111827] border border-[#1e293b] rounded-2xl p-6 space-y-6 max-w-3xl shadow-2xl">
          <div className="flex items-center justify-between border-b border-[#1e293b] pb-3">
            <h2 className="text-lg font-bold text-white flex items-center gap-2">
              <Store className="h-5 w-5 text-emerald-400" />
              <span>Merchant Node &amp; Dispatch Settings</span>
            </h2>
            {isLoading && (
              <span className="text-xs text-gray-400 flex items-center gap-1.5">
                <RefreshCw className="h-3.5 w-3.5 animate-spin" /> Syncing with backend...
              </span>
            )}
          </div>

          {/* ⚡ DISPATCH WORKFLOW CONFIGURATION (ChatGPT Dual-Mode Architecture) */}
          <div className={`p-5 rounded-xl border transition-all duration-200 ${
            sellerApprovalRequired 
              ? 'bg-amber-950/20 border-amber-500/40 text-amber-200' 
              : 'bg-emerald-950/20 border-emerald-500/40 text-emerald-200'
          }`}>
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-1.5">
                <div className="flex items-center gap-2">
                  {sellerApprovalRequired ? (
                    <ShieldCheck className="h-5 w-5 text-amber-400" />
                  ) : (
                    <Zap className="h-5 w-5 text-emerald-400" />
                  )}
                  <h3 className="text-sm font-bold text-white">
                    {sellerApprovalRequired 
                      ? 'Manual Order Review (Marketplace Mode)' 
                      : 'Instant Auto-Dispatch (Dark Store Mode)'}
                  </h3>
                  <span className={`text-[10px] uppercase font-black px-2 py-0.5 rounded-full border ${
                    sellerApprovalRequired 
                      ? 'bg-amber-500/10 text-amber-300 border-amber-500/30' 
                      : 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30'
                  }`}>
                    {sellerApprovalRequired ? 'Manual Accept Required' : 'Instant 1-Sec Broadcast'}
                  </span>
                </div>
                <p className="text-xs text-gray-300 leading-relaxed">
                  {sellerApprovalRequired ? (
                    <>
                      <strong>Marketplace Mode Active:</strong> New customer orders enter the <code className="text-amber-300 font-mono bg-amber-950/60 px-1 py-0.5 rounded">SELLER_PENDING</code> queue. Dispatch to delivery riders is strictly held until a store operator reviews and taps <strong>&quot;Accept Order&quot;</strong>. Ideal for 3rd-party merchants, pharmacies, and restaurants.
                    </>
                  ) : (
                    <>
                      <strong>Dark Store Express Active:</strong> New customer orders immediately emit <code className="text-emerald-300 font-mono bg-emerald-950/60 px-1 py-0.5 rounded">DISPATCH_REQUESTED</code> and broadcast offers to the nearest riders within 1 second. No seller acceptance tap required. Ideal for automated quick-commerce warehouses.
                    </>
                  )}
                </p>
              </div>

              {/* Interactive Toggle Switch */}
              <button
                type="button"
                onClick={handleToggleDispatchMode}
                className={`w-14 h-8 rounded-full transition-colors relative shrink-0 cursor-pointer p-0.5 ${
                  sellerApprovalRequired ? 'bg-amber-600' : 'bg-emerald-600'
                }`}
                title="Toggle between Manual Review (Marketplace) and Auto-Dispatch (Dark Store)"
              >
                <div
                  className={`w-7 h-7 rounded-full bg-white shadow-md transition-transform duration-200 flex items-center justify-center ${
                    sellerApprovalRequired ? 'translate-x-6' : 'translate-x-0'
                  }`}
                >
                  {sellerApprovalRequired ? (
                    <ShieldCheck className="h-4 w-4 text-amber-600" />
                  ) : (
                    <Zap className="h-4 w-4 text-emerald-600" />
                  )}
                </div>
              </button>
            </div>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-gray-400 mb-1">Store / Warehouse Name</label>
              <input
                type="text"
                value={storeName}
                onChange={e => setStoreName(e.target.value)}
                className="w-full bg-[#1f2937] border border-[#374151] rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-400 mb-1">Merchant Support Contact</label>
              <input
                type="text"
                value={supportPhone}
                onChange={e => setSupportPhone(e.target.value)}
                className="w-full bg-[#1f2937] border border-[#374151] rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-400 mb-1">10-Min SLA Fulfillment Coverage Radius</label>
              <input
                type="text"
                value={fulfillmentRadius}
                onChange={e => setFulfillmentRadius(e.target.value)}
                className="w-full bg-[#1f2937] border border-[#374151] rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-emerald-500"
              />
            </div>

            <div className="flex items-center justify-between pt-2 border-t border-[#1e293b]">
              <div>
                <h3 className="font-bold text-sm text-white">Enable Cash on Delivery (COD)</h3>
                <p className="text-xs text-gray-400">Allow customers to pay cash at door upon SLA verification</p>
              </div>
              <button
                type="button"
                onClick={() => setCodEnabled(!codEnabled)}
                className={`w-12 h-6 rounded-full transition-colors relative cursor-pointer ${
                  codEnabled ? 'bg-emerald-600' : 'bg-gray-600'
                }`}
              >
                <div className={`w-5 h-5 rounded-full bg-white absolute top-0.5 transition-transform ${codEnabled ? 'right-0.5' : 'left-0.5'}`} />
              </button>
            </div>
          </div>

          {isSaved && (
            <div className="p-3 rounded-xl bg-emerald-950/40 border border-emerald-500/30 text-xs font-bold text-emerald-300 flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 shrink-0" />
              <span>Store settings and dispatch workflow saved successfully.</span>
            </div>
          )}

          {errorMsg && (
            <div className="p-3 rounded-xl bg-rose-950/40 border border-rose-500/30 text-xs font-bold text-rose-300 flex items-center gap-2">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <div className="pt-4 border-t border-[#1e293b] flex items-center justify-between">
            <p className="text-xs text-gray-400">
              Changes take effect immediately across all active customer &amp; rider sessions.
            </p>
            <button
              type="submit"
              className="px-6 py-2.5 bg-emerald-600 hover:bg-emerald-500 rounded-xl font-bold text-sm text-white transition flex items-center gap-2 cursor-pointer shadow-lg shadow-emerald-900/30"
            >
              <Save className="h-4 w-4" />
              <span>Save Merchant Settings</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
