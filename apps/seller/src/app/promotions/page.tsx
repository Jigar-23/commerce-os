'use client';

import React, { useState } from 'react';
import Link from 'next/link';

export default function PromotionsPage() {
  const [promotions] = useState([
    { id: 'promo1', code: 'WELCOME20', type: 'PERCENTAGE', discount: '20% OFF', usageCount: 1420, maxUsage: 5000, status: 'ACTIVE' },
    { id: 'promo2', code: 'HEALTH30', type: 'FLAT_DISCOUNT', discount: '₹150 OFF', usageCount: 890, maxUsage: 2000, status: 'ACTIVE' },
    { id: 'promo3', code: 'FREESHIP', type: 'FREE_SHIPPING', discount: 'Free 10-Min SLA', usageCount: 3450, maxUsage: 10000, status: 'ACTIVE' }
  ]);

  return (
    <div className="min-h-screen bg-surface-inverse text-white p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Navigation */}
        <div className="flex items-center justify-between border-b border-border-strong pb-4">
          <div>
            <h1 className="text-2xl font-black text-white">Commerce OS — Seller Merchant OS</h1>
            <p className="text-xs text-content-muted">Promotions, Coupons & Campaign Rules Engine</p>
          </div>
          <div className="flex items-center space-x-3 text-sm">
            <Link href="/" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Dashboard</Link>
            <Link href="/orders" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Orders</Link>
            <Link href="/products" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Products</Link>
            <Link href="/inventory" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Inventory</Link>
            <Link href="/pricing" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Pricing</Link>
            <Link href="/promotions" className="px-3 py-1.5 rounded-lg bg-action-speedBg font-bold">Promotions</Link>
            <Link href="/settlements" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Settlements</Link>
            <Link href="/settings" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Settings</Link>
          </div>
        </div>

        {/* Promotions Header & List */}
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold">Active Coupon Codes & Deals ({promotions.length})</h2>
          <button className="px-4 py-2 bg-action-primaryBg hover:bg-action-primaryHover rounded-xl text-sm font-bold text-white transition">
            + Create Promotion
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {promotions.map(p => (
            <div key={p.id} className="bg-surface-inverse border border-border-strong rounded-2xl p-6 space-y-4 shadow-xl">
              <div className="flex items-center justify-between">
                <span className="px-3 py-1 bg-surface-warningSubtle text-content-warning font-mono font-bold rounded-lg text-sm border border-border-warning">
                  {p.code}
                </span>
                <span className="px-2.5 py-1 rounded-full text-2xs font-black bg-surface-brandSubtle text-content-brand border border-border-brand">
                  {p.status}
                </span>
              </div>
              <div>
                <h3 className="text-xl font-black text-white">{p.discount}</h3>
                <p className="text-xs text-content-muted mt-1">Rule Type: {p.type}</p>
              </div>
              <div className="border-t border-border-strong pt-3 flex items-center justify-between text-xs text-content-muted">
                <span>Redemptions:</span>
                <span className="font-mono font-bold text-content-brand">{p.usageCount} / {p.maxUsage}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
