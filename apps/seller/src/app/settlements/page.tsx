'use client';

import React, { useState } from 'react';
import Link from 'next/link';

export default function SettlementsPage() {
  const [settlements] = useState([
    { id: 'SET-2026-0801', date: '2026-08-12', grossSales: 148500.0, commission: 7425.0, netPayout: 141075.0, status: 'PAID' },
    { id: 'SET-2026-0802', date: '2026-08-11', grossSales: 192300.0, commission: 9615.0, netPayout: 182685.0, status: 'PAID' },
    { id: 'SET-2026-0803', date: '2026-08-10', grossSales: 110400.0, commission: 5520.0, netPayout: 104880.0, status: 'PROCESSING' }
  ]);

  return (
    <div className="min-h-screen bg-surface-inverse text-white p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Navigation */}
        <div className="flex items-center justify-between border-b border-border-strong pb-4">
          <div>
            <h1 className="text-2xl font-black text-white">Commerce OS — Seller Merchant OS</h1>
            <p className="text-xs text-content-muted">Financial Settlements & Payout Reconciliations</p>
          </div>
          <div className="flex items-center space-x-3 text-sm">
            <Link href="/" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Dashboard</Link>
            <Link href="/orders" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Orders</Link>
            <Link href="/products" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Products</Link>
            <Link href="/inventory" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Inventory</Link>
            <Link href="/pricing" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Pricing</Link>
            <Link href="/promotions" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Promotions</Link>
            <Link href="/settlements" className="px-3 py-1.5 rounded-lg bg-action-speedBg font-bold">Settlements</Link>
            <Link href="/settings" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Settings</Link>
          </div>
        </div>

        {/* Payout Table */}
        <div className="bg-surface-inverse border border-border-strong rounded-2xl overflow-hidden shadow-xl">
          <table className="w-full text-left text-sm text-content-muted">
            <thead className="bg-surface-inverse text-content-muted uppercase text-2xs font-bold tracking-wider border-b border-border-strong">
              <tr>
                <th className="px-6 py-4">Settlement Batch ID</th>
                <th className="px-6 py-4">Payout Date</th>
                <th className="px-6 py-4">Gross Sales</th>
                <th className="px-6 py-4">Platform Fee (5%)</th>
                <th className="px-6 py-4">Net Merchant Payout</th>
                <th className="px-6 py-4">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-strong/60">
              {settlements.map(s => (
                <tr key={s.id} className="hover:bg-surface-inverse/30 transition">
                  <td className="px-6 py-4 font-mono text-xs text-content-accent font-bold">{s.id}</td>
                  <td className="px-6 py-4">{s.date}</td>
                  <td className="px-6 py-4 font-mono">₹{s.grossSales.toFixed(2)}</td>
                  <td className="px-6 py-4 font-mono text-content-muted">-₹{s.commission.toFixed(2)}</td>
                  <td className="px-6 py-4 font-black text-content-brand">₹{s.netPayout.toFixed(2)}</td>
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-2xs font-black ${
                      s.status === 'PAID' ? 'bg-surface-brandSubtle text-content-brand border border-border-brand' : 'bg-surface-warningSubtle text-content-warning border border-border-warning'
                    }`}>
                      {s.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
