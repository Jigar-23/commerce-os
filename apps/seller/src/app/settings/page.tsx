'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { CheckCircle2, Save } from 'lucide-react';

export default function SettingsPage() {
  const [storeName, setStoreName] = useState('Central Warehouse Outlet');
  const [supportPhone, setSupportPhone] = useState('+1-800-MERCHANT');
  const [fulfillmentRadius, setFulfillmentRadius] = useState('15 km');
  const [codEnabled, setCodEnabled] = useState(true);
  const [isSaved, setIsSaved] = useState(false);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaved(true);
    setTimeout(() => setIsSaved(false), 3000);
  };

  return (
    <div className="min-h-screen bg-surface-inverse text-white p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Navigation */}
        <div className="flex items-center justify-between border-b border-border-strong pb-4">
          <div>
            <h1 className="text-2xl font-black text-white">Commerce OS — Seller Merchant OS</h1>
            <p className="text-xs text-content-muted">Merchant Settings, Roles &amp; Fulfillment Node Options</p>
          </div>
          <div className="flex items-center space-x-3 text-sm">
            <Link href="/" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Dashboard</Link>
            <Link href="/orders" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Orders</Link>
            <Link href="/products" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Products</Link>
            <Link href="/inventory" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Inventory</Link>
            <Link href="/pricing" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Pricing</Link>
            <Link href="/promotions" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Promotions</Link>
            <Link href="/settlements" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Settlements</Link>
            <Link href="/settings" className="px-3 py-1.5 rounded-lg bg-action-speedBg font-bold">Settings</Link>
          </div>
        </div>

        {/* Settings Form */}
        <form onSubmit={handleSave} className="bg-surface-inverse border border-border-strong rounded-2xl p-6 space-y-6 max-w-3xl shadow-xl">
          <h2 className="text-lg font-bold border-b border-border-strong pb-3">Merchant Node Profile</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-content-muted mb-1">Store / Warehouse Name</label>
              <input
                type="text"
                value={storeName}
                onChange={e => setStoreName(e.target.value)}
                className="w-full bg-surface-inverse border border-border-strong rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-border-focus"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-content-muted mb-1">Merchant Support Contact</label>
              <input
                type="text"
                value={supportPhone}
                onChange={e => setSupportPhone(e.target.value)}
                className="w-full bg-surface-inverse border border-border-strong rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-border-focus"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-content-muted mb-1">10-Min SLA Fulfillment Coverage Radius</label>
              <input
                type="text"
                value={fulfillmentRadius}
                onChange={e => setFulfillmentRadius(e.target.value)}
                className="w-full bg-surface-inverse border border-border-strong rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-border-focus"
              />
            </div>

            <div className="flex items-center justify-between pt-2">
              <div>
                <h3 className="font-bold text-sm text-white">Enable Cash on Delivery (COD)</h3>
                <p className="text-xs text-content-muted">Allow customers to pay at door upon SLA verification</p>
              </div>
              <button
                type="button"
                onClick={() => setCodEnabled(!codEnabled)}
                className={`w-12 h-6 rounded-full transition-colors relative ${codEnabled ? 'bg-action-primaryBg' : 'bg-surface-muted'}`}
              >
                <div className={`w-5 h-5 rounded-full bg-white absolute top-0.5 transition-transform ${codEnabled ? 'right-0.5' : 'left-0.5'}`} />
              </button>
            </div>
          </div>

          {isSaved && (
            <div className="p-3 rounded-xl bg-surface-brandSubtle border border-border-brandSubtle text-xs font-bold text-content-brand flex items-center gap-2">
              <CheckCircle2 className="h-4 w-4 shrink-0" />
              <span>Merchant node settings updated.</span>
            </div>
          )}

          <div className="pt-4 border-t border-border-strong">
            <button
              type="submit"
              className="px-6 py-2.5 bg-action-speedBg hover:bg-action-speedHover rounded-xl font-bold text-sm text-white transition flex items-center gap-2 cursor-pointer"
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
