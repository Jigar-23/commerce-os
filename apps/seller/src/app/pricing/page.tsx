'use client';

import React, { useState } from 'react';
import Link from 'next/link';

export default function PricingPage() {
  const [pricingRules] = useState([
    { id: 'pr1', product: 'Paracetamol 500mg Extra', basePrice: 35.0, sellingPrice: 28.0, minMargin: '20%', dynamicPricing: 'ENABLED' },
    { id: 'pr2', product: 'Organic Extra Virgin Olive Oil 1L', basePrice: 850.0, sellingPrice: 720.0, minMargin: '15%', dynamicPricing: 'ENABLED' },
    { id: 'pr3', product: 'Urban Streetwear Oversized Hoodie', basePrice: 2999.0, sellingPrice: 2499.0, minMargin: '30%', dynamicPricing: 'DISABLED' },
    { id: 'pr4', product: 'Smart Wireless Headphones', basePrice: 14999.0, sellingPrice: 12999.0, minMargin: '25%', dynamicPricing: 'ENABLED' }
  ]);

  return (
    <div className="min-h-screen bg-surface-inverse text-white p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Navigation */}
        <div className="flex items-center justify-between border-b border-border-strong pb-4">
          <div>
            <h1 className="text-2xl font-black text-white">Commerce OS — Seller Merchant OS</h1>
            <p className="text-xs text-content-muted">Pricing & Dynamic Margin Engine</p>
          </div>
          <div className="flex items-center space-x-3 text-sm">
            <Link href="/" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Dashboard</Link>
            <Link href="/orders" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Orders</Link>
            <Link href="/products" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Products</Link>
            <Link href="/inventory" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Inventory</Link>
            <Link href="/pricing" className="px-3 py-1.5 rounded-lg bg-action-speedBg font-bold">Pricing</Link>
            <Link href="/promotions" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Promotions</Link>
            <Link href="/settlements" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Settlements</Link>
            <Link href="/settings" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Settings</Link>
          </div>
        </div>

        {/* Pricing List */}
        <div className="bg-surface-inverse border border-border-strong rounded-2xl overflow-hidden shadow-xl p-6">
          <h2 className="text-lg font-bold mb-4">Pricing Rules & Automated Markdown</h2>
          <div className="space-y-4">
            {pricingRules.map(r => (
              <div key={r.id} className="flex items-center justify-between bg-surface-inverse/80 p-4 rounded-xl border border-border-strong">
                <div>
                  <h3 className="font-bold text-white text-base">{r.product}</h3>
                  <p className="text-xs text-content-muted">Base MSRP: ₹{r.basePrice.toFixed(2)} • Target Min Margin: {r.minMargin}</p>
                </div>
                <div className="flex items-center space-x-6 text-right">
                  <div>
                    <p className="text-xs text-content-muted">Selling Price</p>
                    <p className="font-black text-content-brand text-lg">₹{r.sellingPrice.toFixed(2)}</p>
                  </div>
                  <span className="px-3 py-1 bg-surface-accentSubtle text-content-accent border border-border-accent rounded-full text-xs font-bold">
                    {r.dynamicPricing}
                  </span>
                  <button className="px-3 py-1.5 bg-surface-muted hover:bg-surface-muted rounded-lg text-xs font-bold">Adjust</button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
