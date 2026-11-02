'use client';

import React from 'react';
import { ArrowRight } from 'lucide-react';

interface HeroDualActionCardsProps {
  onMedicinesClick: () => void;
  onLabTestsClick: () => void;
}

/**
 * Dual Action Hero Cards benchmarked directly against the CommerceOS Android App.
 * Clear separation between immediate 10-minute medicine delivery and diagnostic lab bookings.
 */
export default function HeroDualActionCards({
  onMedicinesClick,
  onLabTestsClick,
}: HeroDualActionCardsProps) {
  return (
    <div className="grid grid-cols-2 gap-3 w-full">
      {/* 1. Medicines Hero Tile */}
      <div
        onClick={onMedicinesClick}
        className="bg-white rounded-2xl p-3.5 border border-slate-100 shadow-sm hover:shadow-md active:scale-[0.98] transition-all cursor-pointer flex flex-col justify-between"
      >
        <div>
          <span className="inline-block px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-700 text-[10px] font-extrabold uppercase tracking-wider mb-2">
            Flat 20% Off
          </span>
          <h3 className="text-base font-extrabold text-slate-900 leading-snug">
            Medicines
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Delivered in 10 mins
          </p>
        </div>

        <div className="flex items-center gap-1 text-xs font-bold text-slate-900 mt-3 group">
          <span>Order Now</span>
          <ArrowRight className="w-3.5 h-3.5 text-slate-700 group-hover:translate-x-0.5 transition-transform" />
        </div>
      </div>

      {/* 2. Lab Tests Hero Tile */}
      <div
        onClick={onLabTestsClick}
        className="bg-white rounded-2xl p-3.5 border border-slate-100 shadow-sm hover:shadow-md active:scale-[0.98] transition-all cursor-pointer flex flex-col justify-between"
      >
        <div>
          <span className="inline-block px-2 py-0.5 rounded-md bg-blue-50 text-blue-700 text-[10px] font-extrabold uppercase tracking-wider mb-2">
            Up to 70% Off
          </span>
          <h3 className="text-base font-extrabold text-slate-900 leading-snug">
            Lab Tests
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Free sample pickup
          </p>
        </div>

        <div className="flex items-center gap-1 text-xs font-bold text-blue-600 mt-3 group">
          <span>Book Test</span>
          <ArrowRight className="w-3.5 h-3.5 text-blue-600 group-hover:translate-x-0.5 transition-transform" />
        </div>
      </div>
    </div>
  );
}
