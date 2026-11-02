'use client';

import React, { useState } from 'react';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import { IndianRupee, Download, CheckCircle2, Clock, AlertCircle, ArrowUpRight, TrendingUp } from 'lucide-react';

interface SettlementBatch {
  id: string;
  date: string;
  grossSales: number;
  commission: number;
  netPayout: number;
  status: 'PAID' | 'PROCESSING' | 'PENDING';
  utrReference?: string;
}

export default function SettlementsPage() {
  const [settlements] = useState<SettlementBatch[]>([
    { id: 'SET-2026-1101', date: '2026-11-01', grossSales: 164500.0, commission: 8225.0, netPayout: 156275.0, status: 'PAID', utrReference: 'UTR-HDFC-9912048' },
    { id: 'SET-2026-1031', date: '2026-10-31', grossSales: 192300.0, commission: 9615.0, netPayout: 182685.0, status: 'PAID', utrReference: 'UTR-ICICI-8819203' },
    { id: 'SET-2026-1030', date: '2026-10-30', grossSales: 148500.0, commission: 7425.0, netPayout: 141075.0, status: 'PAID', utrReference: 'UTR-HDFC-7718290' },
    { id: 'SET-2026-1029', date: '2026-10-29', grossSales: 110400.0, commission: 5520.0, netPayout: 104880.0, status: 'PROCESSING', utrReference: 'PENDING_BANK_BATCH' }
  ]);

  const totalSettled = settlements.filter(s => s.status === 'PAID').reduce((acc, s) => acc + s.netPayout, 0);
  const pendingPayout = settlements.filter(s => s.status !== 'PAID').reduce((acc, s) => acc + s.netPayout, 0);
  const totalCommission = settlements.reduce((acc, s) => acc + s.commission, 0);

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-subtle font-sans overflow-hidden">
        <SellerSidebar activeTab="settlements" />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Header */}
          <div className="sticky top-0 z-10 bg-white/95 backdrop-blur-md border-b border-border-default px-8 py-4 flex items-center justify-between">
            <div>
              <h1 className="text-xl font-black text-content-primary tracking-tight">Financial Settlements & Payouts</h1>
              <p className="text-xs text-content-secondary">Automated T+1 merchant reconciliations, bank batch payouts, and commission breakdowns</p>
            </div>
            <div className="flex items-center space-x-3">
              <HeaderQuickSearch />
              <button
                onClick={() => alert('Exporting settlement statement CSV...')}
                className="px-3.5 py-2 rounded-xl bg-surface-subtle border border-border-default hover:bg-surface-muted text-xs font-bold text-content-primary flex items-center space-x-2 transition shadow-sm"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Export Statement</span>
              </button>
            </div>
          </div>

          <div className="p-8 space-y-6 max-w-7xl">
            {/* KPI Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
              <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">Total Disbursed (Past 30 Days)</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-brandSubtle text-content-brand flex items-center justify-center font-bold">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-primary">₹{totalSettled.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</div>
                <p className="text-2xs text-content-secondary flex items-center space-x-1">
                  <TrendingUp className="w-3 h-3 text-content-brand" />
                  <span>Direct NEFT/RTGS to registered merchant account</span>
                </p>
              </div>

              <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">In Settlement Queue</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-warningSubtle text-content-warning flex items-center justify-center font-bold">
                    <Clock className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-warning">₹{pendingPayout.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</div>
                <p className="text-2xs text-content-secondary">Scheduled for automated release tonight at 23:59 IST</p>
              </div>

              <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">Platform Take Rate (5%)</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-accentSubtle text-content-accent flex items-center justify-center font-bold">
                    <IndianRupee className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-primary">₹{totalCommission.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</div>
                <p className="text-2xs text-content-secondary">Platform infrastructure, edge telemetry & gateway fee</p>
              </div>
            </div>

            {/* Settlements Table */}
            <div className="bg-white border border-border-default rounded-2xl overflow-hidden shadow-sm">
              <div className="px-6 py-4 border-b border-border-default flex items-center justify-between">
                <h2 className="text-sm font-black text-content-primary">Disbursement Batches ({settlements.length})</h2>
                <span className="text-2xs font-bold text-content-muted">Authoritative Bank Ledgers</span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-surface-subtle text-content-secondary uppercase text-2xs font-bold tracking-wider border-b border-border-default">
                    <tr>
                      <th className="px-6 py-3.5">Batch Reference</th>
                      <th className="px-6 py-3.5">Date</th>
                      <th className="px-6 py-3.5 text-right">Gross GMV</th>
                      <th className="px-6 py-3.5 text-right">Fee Deduction (5%)</th>
                      <th className="px-6 py-3.5 text-right">Net Payout</th>
                      <th className="px-6 py-3.5">Bank UTR / Tracking</th>
                      <th className="px-6 py-3.5 text-center">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border-default/60">
                    {settlements.map(s => (
                      <tr key={s.id} className="hover:bg-surface-subtle/50 transition">
                        <td className="px-6 py-4 font-mono font-bold text-content-accent">{s.id}</td>
                        <td className="px-6 py-4 text-content-primary font-medium">{s.date}</td>
                        <td className="px-6 py-4 text-right font-mono text-content-secondary">₹{s.grossSales.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                        <td className="px-6 py-4 text-right font-mono text-content-danger">-₹{s.commission.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                        <td className="px-6 py-4 text-right font-mono font-black text-content-brand text-sm">₹{s.netPayout.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                        <td className="px-6 py-4 font-mono text-2xs text-content-muted">{s.utrReference || '—'}</td>
                        <td className="px-6 py-4 text-center">
                          <span className={`inline-flex items-center space-x-1 px-2.5 py-1 rounded-full text-2xs font-black ${
                            s.status === 'PAID'
                              ? 'bg-surface-brandSubtle text-content-brand border border-border-brand'
                              : 'bg-surface-warningSubtle text-content-warning border border-border-warning'
                          }`}>
                            {s.status === 'PAID' ? <CheckCircle2 className="w-3 h-3" /> : <Clock className="w-3 h-3" />}
                            <span>{s.status}</span>
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>
    </SellerAuthGuard>
  );
}
