'use client';

import React from 'react';
import { ArrowLeftRight, CheckCircle2, Sparkles } from 'lucide-react';

interface GenericSubstituteCardProps {
  originalName?: string;
  genericName?: string;
  originalPrice?: number;
  genericPrice?: number;
  savingsPercentage?: number;
  onApplySubstitute?: () => void;
}

/**
 * Generic Substitute Recommendation Card benchmarked directly against the Android App.
 * Empowers consumers to swap branded medicines for certified generic alternatives
 * with identical active salts and up to 40% savings.
 */
export default function GenericSubstituteCard({
  originalName = 'Dolo 650mg Tablet',
  genericName = 'Paracip 650mg Tablet',
  originalPrice = 30,
  genericPrice = 18,
  savingsPercentage = 40,
  onApplySubstitute,
}: GenericSubstituteCardProps) {
  return (
    <div className="w-full bg-emerald-50/70 border border-emerald-200/90 rounded-2xl p-4 shadow-sm flex flex-col gap-3">
      {/* Top Banner Tag */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <div className="w-7 h-7 rounded-xl bg-emerald-700 text-white flex items-center justify-center">
            <ArrowLeftRight className="w-4 h-4" />
          </div>
          <div>
            <span className="text-xs font-black text-slate-900 tracking-tight">
              Save Up to {savingsPercentage}% on Medicines
            </span>
            <div className="flex items-center gap-1 text-[10px] text-emerald-800 font-bold">
              <CheckCircle2 className="w-3 h-3 text-emerald-600" />
              <span>WHO-GMP Certified Salt Match</span>
            </div>
          </div>
        </div>

        <span className="px-2 py-0.5 rounded-full bg-emerald-600 text-white text-[10px] font-black uppercase tracking-wider">
          Save ₹{(originalPrice - genericPrice).toFixed(0)}
        </span>
      </div>

      {/* Comparison Grid */}
      <div className="grid grid-cols-2 gap-2 text-xs">
        <div className="bg-white/80 p-2.5 rounded-xl border border-slate-200">
          <span className="text-[10px] text-slate-500 font-medium">Prescribed / Searched</span>
          <p className="font-bold text-slate-800 line-clamp-1">{originalName}</p>
          <span className="text-xs text-slate-500 font-semibold line-through">₹{originalPrice}</span>
        </div>

        <div className="bg-white p-2.5 rounded-xl border border-emerald-300 shadow-xs relative">
          <span className="text-[10px] text-emerald-700 font-bold flex items-center gap-0.5">
            <Sparkles className="w-2.5 h-2.5 text-emerald-600" /> Recommended Generic
          </span>
          <p className="font-bold text-emerald-950 line-clamp-1">{genericName}</p>
          <span className="text-sm font-black text-emerald-700">₹{genericPrice}</span>
        </div>
      </div>

      {/* 1-Click Swap Button */}
      {onApplySubstitute && (
        <button
          onClick={onApplySubstitute}
          className="w-full py-2 px-3 rounded-xl bg-emerald-700 hover:bg-emerald-800 active:scale-[0.98] text-white text-xs font-bold transition-all flex items-center justify-center gap-1.5 cursor-pointer"
        >
          <span>Switch to Generic &amp; Save ₹{(originalPrice - genericPrice).toFixed(0)}</span>
        </button>
      )}
    </div>
  );
}
