'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
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
  Banknote,
  Construction,
  XCircle,
} from 'lucide-react';

export default function SettlementsPage() {
  const router = useRouter();
  const [showModal, setShowModal] = useState(true);

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-[#F8FAFC] text-slate-900 font-sans antialiased overflow-hidden">
        <SellerSidebar activeTab="settlements" />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Header */}
          <header className="h-16 border-b border-slate-200/80 bg-white/90 backdrop-blur-md px-8 flex items-center justify-between shrink-0 gap-4 sticky top-0 z-10">
            <div className="flex items-center space-x-3">
              <h1 className="text-base font-bold text-slate-900">Settlements &amp; Payouts</h1>
              <span className="px-2 py-0.5 text-[10px] font-bold uppercase rounded-md bg-amber-50 text-amber-700 border border-amber-200/60">
                Under Construction
              </span>
            </div>
            <div className="flex items-center space-x-3">
              <HeaderQuickSearch />
            </div>
          </header>

          <main className="p-8 max-w-4xl mx-auto w-full space-y-6">
            <div className="bg-white border border-slate-200/80 rounded-3xl p-8 text-center space-y-4 shadow-[0_1px_3px_rgba(0,0,0,0.03)]">
              <div className="w-14 h-14 rounded-2xl bg-amber-50 border border-amber-200/60 text-amber-600 flex items-center justify-center mx-auto shadow-xs">
                <Construction className="w-7 h-7 animate-pulse" />
              </div>

              <div className="space-y-1.5 max-w-md mx-auto">
                <span className="px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-amber-100 text-amber-800 rounded-full">
                  Under Construction
                </span>
                <h2 className="text-xl font-bold text-slate-900 pt-1">
                  Settlements Module Under Construction
                </h2>
                <p className="text-xs text-slate-500 leading-relaxed">
                  Automated direct bank account payouts and settlement cycles are currently in active development.
                </p>
                <p className="text-xs text-slate-500 leading-relaxed">
                  All active store cash collections, driver handovers, and doorstep reconciliations are tracked live in the COD Ledger.
                </p>
              </div>

              <div className="pt-3 flex flex-wrap items-center justify-center gap-3">
                <Link
                  href="/cod"
                  className="px-5 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold flex items-center space-x-1.5 shadow-xs transition-all"
                >
                  <Banknote className="w-4 h-4" />
                  <span>Open Live COD Ledger</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </Link>

                <Link
                  href="/"
                  className="px-5 py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-medium transition-all"
                >
                  Return to Dashboard
                </Link>
              </div>
            </div>
          </main>
        </div>

        {/* Pop-up Modal */}
        {showModal && (
          <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
            <div className="bg-white rounded-3xl shadow-xl border border-slate-200/80 max-w-sm w-full p-6 text-center space-y-4 animate-in fade-in zoom-in-95">
              <div className="w-12 h-12 rounded-2xl bg-amber-50 border border-amber-200/60 text-amber-600 flex items-center justify-center mx-auto shadow-xs">
                <Construction className="w-6 h-6 animate-pulse" />
              </div>

              <div className="space-y-1.5">
                <span className="px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-amber-100 text-amber-800 rounded-full">
                  Under Construction
                </span>
                <h3 className="text-base font-bold text-slate-900 pt-1">
                  Settlements Module Coming Soon
                </h3>
                <p className="text-xs text-slate-500 leading-relaxed">
                  Direct automated bank account payouts and settlement cycles are currently in active development.
                </p>
                <p className="text-[11px] text-slate-400 leading-relaxed">
                  All daily store cash collections are tracked and reconciled in real-time through your Cash on Delivery Ledger.
                </p>
              </div>

              <div className="pt-2 flex flex-col sm:flex-row items-center gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setShowModal(false);
                    router.push('/cod');
                  }}
                  className="w-full px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-semibold shadow-xs transition-all flex items-center justify-center space-x-1.5 active:scale-98"
                >
                  <span>Open COD Ledger</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowModal(false);
                    router.push('/');
                  }}
                  className="w-full px-4 py-2.5 border border-slate-200 hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-medium transition-all"
                >
                  Dismiss
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
