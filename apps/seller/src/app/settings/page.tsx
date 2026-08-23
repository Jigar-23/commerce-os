'use client';

import React, { useState, useEffect } from 'react';
import SellerSidebar from '../../components/SellerSidebar';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { CheckCircle2, Save, Zap, Store, ShieldCheck, AlertCircle, RefreshCw, Sliders } from 'lucide-react';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';

export default function SettingsPage() {
  const { session, storeName: defaultStoreName } = useSellerSession();
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
    <div className="flex h-screen bg-surface-canvas text-content-primary font-sans antialiased overflow-hidden">
      {/* 1. SELLER SIDEBAR */}
      <SellerSidebar
        activeTab="settings"
        ordersCount={0}
        inventoryCount={0}
        pendingCodAmount={0}
        cancelledCount={0}
        onRefresh={() => {}}
        isLoading={isLoading}
      />

      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* HEADER BAR */}
        <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0 gap-4">
          <div className="flex items-center space-x-3">
            <span className="px-3 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-black border border-border-brandSubtle flex items-center space-x-1.5">
              <Sliders className="w-3.5 h-3.5" />
              <span>Store Configuration &amp; Fulfillment Options</span>
            </span>
            <span className="text-content-muted">/</span>
            <span className="text-xs font-bold text-content-primary" suppressHydrationWarning>{storeName || defaultStoreName}</span>
          </div>

          <div className="flex items-center space-x-4">
            <HeaderQuickSearch />
            <button
              onClick={() => {}}
              className="p-2 text-content-secondary hover:text-content-brand hover:bg-surface-subtle rounded-xl transition-all"
              title="Refresh Settings"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-content-brand' : ''}`} />
            </button>
          </div>
        </header>

        {/* MAIN CONTENT AREA */}
        <main className="flex-1 p-8 space-y-6 max-w-4xl">
          <form onSubmit={handleSave} className="bg-white border border-border-default rounded-2xl p-6 space-y-6 shadow-sm">
            <div className="flex items-center justify-between border-b border-border-default pb-3">
              <h2 className="text-lg font-bold text-content-primary flex items-center gap-2">
                <Store className="h-5 w-5 text-content-brand" />
                <span>Merchant Node &amp; Dispatch Settings</span>
              </h2>
              {isLoading && (
                <span className="text-xs text-content-muted flex items-center gap-1.5">
                  <RefreshCw className="h-3.5 w-3.5 animate-spin" /> Syncing with backend...
                </span>
              )}
            </div>

            {/* DISPATCH WORKFLOW CONFIGURATION */}
            <div className={`p-5 rounded-xl border transition-all duration-200 ${
              sellerApprovalRequired 
                ? 'bg-surface-warningSubtle border-border-warningSubtle text-content-warning' 
                : 'bg-surface-brandSubtle border-border-brandSubtle text-content-brand'
            }`}>
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-1.5">
                  <div className="flex items-center gap-2">
                    {sellerApprovalRequired ? (
                      <ShieldCheck className="h-5 w-5 text-content-warning" />
                    ) : (
                      <Zap className="h-5 w-5 text-content-brand" />
                    )}
                    <h3 className="text-sm font-bold text-content-primary">
                      {sellerApprovalRequired 
                        ? 'Manual Order Review (Marketplace Mode)' 
                        : 'Instant Auto-Dispatch (Dark Store Mode)'}
                    </h3>
                    <span className={`text-xs uppercase font-black px-2 py-0.5 rounded-full border ${
                      sellerApprovalRequired 
                        ? 'bg-surface-warningSubtle text-content-warning border-border-warningSubtle' 
                        : 'bg-surface-brandSubtle text-content-brand border-border-brandSubtle'
                    }`}>
                      {sellerApprovalRequired ? 'Manual Accept Required' : 'Instant 1-Sec Broadcast'}
                    </span>
                  </div>
                  <p className="text-xs text-content-secondary leading-relaxed">
                    {sellerApprovalRequired ? (
                      <>
                        <strong>Marketplace Mode Active:</strong> New customer orders enter the <code className="text-content-warning font-mono bg-surface-warningSubtle px-1 py-0.5 rounded">SELLER_PENDING</code> queue. Dispatch to delivery riders is strictly held until a store operator reviews and taps <strong>&quot;Accept Order&quot;</strong>. Ideal for 3rd-party merchants, pharmacies, and restaurants.
                      </>
                    ) : (
                      <>
                        <strong>Dark Store Express Active:</strong> New customer orders immediately emit <code className="text-content-brand font-mono bg-surface-brandSubtle px-1 py-0.5 rounded">DISPATCH_REQUESTED</code> and broadcast offers to the nearest riders within 1 second. No seller acceptance tap required. Ideal for automated quick-commerce warehouses.
                      </>
                    )}
                  </p>
                </div>

                {/* Interactive Toggle Switch */}
                <button
                  type="button"
                  onClick={handleToggleDispatchMode}
                  className={`w-14 h-8 rounded-full transition-colors relative shrink-0 cursor-pointer p-0.5 ${
                    sellerApprovalRequired ? 'bg-surface-warning' : 'bg-surface-brand'
                  }`}
                  title="Toggle between Manual Review (Marketplace) and Auto-Dispatch (Dark Store)"
                >
                  <div
                    className={`w-7 h-7 rounded-full bg-white shadow-md transition-transform duration-200 flex items-center justify-center ${
                      sellerApprovalRequired ? 'translate-x-6' : 'translate-x-0'
                    }`}
                  >
                    {sellerApprovalRequired ? (
                      <ShieldCheck className="h-4 w-4 text-content-warning" />
                    ) : (
                      <Zap className="h-4 w-4 text-content-brand" />
                    )}
                  </div>
                </button>
              </div>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-content-secondary mb-1">Store / Warehouse Name</label>
                <input
                  type="text"
                  value={storeName}
                  onChange={e => setStoreName(e.target.value)}
                  className="w-full bg-surface-canvas border border-border-default rounded-xl px-4 py-2.5 text-sm text-content-primary focus:outline-none focus:border-border-brand"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-content-secondary mb-1">Merchant Support Contact</label>
                <input
                  type="text"
                  value={supportPhone}
                  onChange={e => setSupportPhone(e.target.value)}
                  className="w-full bg-surface-canvas border border-border-default rounded-xl px-4 py-2.5 text-sm text-content-primary focus:outline-none focus:border-border-brand"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-content-secondary mb-1">10-Min SLA Fulfillment Coverage Radius</label>
                <input
                  type="text"
                  value={fulfillmentRadius}
                  onChange={e => setFulfillmentRadius(e.target.value)}
                  className="w-full bg-surface-canvas border border-border-default rounded-xl px-4 py-2.5 text-sm text-content-primary focus:outline-none focus:border-border-brand"
                />
              </div>

              <div className="flex items-center justify-between pt-2 border-t border-border-default">
                <div>
                  <h3 className="font-bold text-sm text-content-primary">Enable Cash on Delivery (COD)</h3>
                  <p className="text-xs text-content-muted">Allow customers to pay cash at door upon SLA verification</p>
                </div>
                <button
                  type="button"
                  onClick={() => setCodEnabled(!codEnabled)}
                  className={`w-12 h-6 rounded-full transition-colors relative cursor-pointer ${
                    codEnabled ? 'bg-surface-brand' : 'bg-surface-subtle'
                  }`}
                >
                  <div className={`w-5 h-5 rounded-full bg-white absolute top-0.5 transition-transform ${codEnabled ? 'right-0.5' : 'left-0.5'}`} />
                </button>
              </div>
            </div>

            {isSaved && (
              <div className="p-3 rounded-xl bg-surface-brandSubtle border border-border-brandSubtle text-xs font-bold text-content-brand flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>Store settings and dispatch workflow saved successfully.</span>
              </div>
            )}

            {errorMsg && (
              <div className="p-3 rounded-xl bg-surface-dangerSubtle border border-border-dangerSubtle text-xs font-bold text-content-danger flex items-center gap-2">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <div className="pt-4 border-t border-border-default flex items-center justify-between">
              <p className="text-xs text-content-muted">
                Changes take effect immediately across all active customer &amp; rider sessions.
              </p>
              <button
                type="submit"
                className="px-6 py-2.5 bg-surface-brand hover:opacity-90 rounded-xl font-bold text-sm text-content-inverse transition flex items-center gap-2 cursor-pointer shadow-sm"
              >
                <Save className="h-4 w-4" />
                <span>Save Merchant Settings</span>
              </button>
            </div>
          </form>
        </main>
      </div>
    </div>
  );
}
