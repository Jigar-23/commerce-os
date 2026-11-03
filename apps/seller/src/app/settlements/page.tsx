'use client';

import React from 'react';
import Link from 'next/link';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import {
  IndianRupee,
  Clock,
  ArrowRight,
  ShieldCheck,
  Building2,
  FileCheck2,
  Sparkles,
  Banknote
} from 'lucide-react';

export default function SettlementsPage() {
  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-subtle font-sans overflow-hidden">
        <SellerSidebar activeTab="settlements" />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Header */}
          <div className="sticky top-0 z-10 bg-white/95 backdrop-blur-md border-b border-border-default px-8 py-4 flex items-center justify-between">
            <div>
              <div className="flex items-center space-x-2.5">
                <h1 className="text-xl font-black text-content-primary tracking-tight">Financial Settlements & Payouts</h1>
                <span className="px-2 py-0.5 text-2xs font-extrabold rounded-md bg-amber-50 text-amber-700 border border-amber-200 uppercase tracking-wider">
                  Under Development
                </span>
              </div>
              <p className="text-xs text-content-secondary">Dedicated single-merchant direct bank account settlement and automated payout engine</p>
            </div>
            <div className="flex items-center space-x-3">
              <HeaderQuickSearch />
            </div>
          </div>

          <div className="p-8 space-y-8 max-w-5xl mx-auto w-full">
            {/* Primary Under Development Hero Card */}
            <div className="bg-white border border-border-default rounded-3xl p-8 md:p-10 shadow-sm text-center relative overflow-hidden">
              <div className="absolute top-0 right-0 w-64 h-64 bg-amber-500/5 rounded-full blur-3xl pointer-events-none" />
              <div className="absolute bottom-0 left-0 w-64 h-64 bg-emerald-500/5 rounded-full blur-3xl pointer-events-none" />

              <div className="relative z-10 flex flex-col items-center max-w-xl mx-auto space-y-4">
                <div className="w-16 h-16 rounded-2xl bg-amber-50 border border-amber-200 text-amber-700 flex items-center justify-center shadow-sm">
                  <Clock className="w-8 h-8 animate-pulse" />
                </div>

                <div className="space-y-1.5">
                  <span className="text-2xs font-bold text-amber-700 uppercase tracking-wider bg-amber-100/70 px-2.5 py-1 rounded-full">
                    Feature In Progress
                  </span>
                  <h2 className="text-2xl font-black text-content-primary pt-2">
                    Settlements Module Under Development
                  </h2>
                </div>

                <p className="text-sm text-content-secondary leading-relaxed">
                  All placeholder mock data has been removed. Because CommerceOS operates as a dedicated single-merchant store, all live orders, daily sales totals, and customer doorstep cash collections are tracked in real-time in your Cash on Delivery ledger.
                </p>

                <div className="pt-2 flex flex-wrap items-center justify-center gap-3">
                  <Link
                    href="/cod"
                    className="px-5 py-2.5 rounded-xl bg-action-speedBg hover:bg-emerald-700 text-white text-xs font-bold flex items-center space-x-2 transition shadow-sm"
                  >
                    <Banknote className="w-4 h-4" />
                    <span>Open Live COD Ledger</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </Link>

                  <Link
                    href="/"
                    className="px-5 py-2.5 rounded-xl bg-surface-subtle border border-border-default hover:bg-surface-muted text-content-primary text-xs font-bold transition"
                  >
                    Return to Dashboard
                  </Link>
                </div>
              </div>
            </div>

            {/* Upcoming Capabilities Roadmap */}
            <div className="space-y-4">
              <div className="flex items-center space-x-2">
                <Sparkles className="w-4 h-4 text-content-brand" />
                <h3 className="text-sm font-black text-content-primary uppercase tracking-wider">
                  Upcoming Payout Capabilities
                </h3>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* Feature 1 */}
                <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2.5">
                  <div className="w-10 h-10 rounded-xl bg-surface-brandSubtle text-content-brand flex items-center justify-center">
                    <Building2 className="w-5 h-5" />
                  </div>
                  <h4 className="text-sm font-bold text-content-primary">Direct Bank Account Linkage</h4>
                  <p className="text-xs text-content-secondary leading-relaxed">
                    Automated NEFT / RTGS transfers directly into your registered corporate current account.
                  </p>
                </div>

                {/* Feature 2 */}
                <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2.5">
                  <div className="w-10 h-10 rounded-xl bg-surface-warningSubtle text-content-warning flex items-center justify-center">
                    <IndianRupee className="w-5 h-5" />
                  </div>
                  <h4 className="text-sm font-bold text-content-primary">Custom Settlement Cycles</h4>
                  <p className="text-xs text-content-secondary leading-relaxed">
                    Configure daily T+1 batch closing, weekly transfers, or on-demand manual payouts.
                  </p>
                </div>

                {/* Feature 3 */}
                <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2.5">
                  <div className="w-10 h-10 rounded-xl bg-surface-accentSubtle text-content-accent flex items-center justify-center">
                    <FileCheck2 className="w-5 h-5" />
                  </div>
                  <h4 className="text-sm font-bold text-content-primary">GST Invoices & Statements</h4>
                  <p className="text-xs text-content-secondary leading-relaxed">
                    One-click export of compliant settlement tax statements, breakdown reports, and bank UTR logs.
                  </p>
                </div>
              </div>
            </div>

            {/* Reassurance Banner */}
            <div className="p-4 rounded-2xl bg-emerald-50 border border-emerald-200/80 flex items-start space-x-3 text-emerald-900">
              <ShieldCheck className="w-5 h-5 text-emerald-700 shrink-0 mt-0.5" />
              <div className="text-xs space-y-0.5">
                <p className="font-bold text-emerald-950">Zero Impact on Order Fulfillment</p>
                <p className="text-emerald-800 leading-relaxed">
                  Your store's live order acceptance, dispatching, rider assignment, and customer checkout operate seamlessly.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </SellerAuthGuard>
  );
}
