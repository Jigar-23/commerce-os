'use client';

import React from 'react';
import { ShieldCheck, Upload, MessageSquare, Phone } from 'lucide-react';

interface AssistedCommerceRibbonProps {
  onUploadRx: () => void;
  pharmacistPhone?: string;
}

/**
 * Assisted Commerce Strip benchmarked directly against the CommerceOS Android App.
 * Converts prescription holders and uncertain customers via WhatsApp, Direct Call, and 1-Click Upload.
 */
export default function AssistedCommerceRibbon({
  onUploadRx,
  pharmacistPhone = '919817916180',
}: AssistedCommerceRibbonProps) {
  const whatsappUrl = `https://wa.me/${pharmacistPhone}?text=Hello%20CommerceOS%20Pharmacy%2C%20I%20would%20like%20to%20order%20medicines.`;
  const callUrl = `tel:+${pharmacistPhone}`;

  return (
    <div className="w-full bg-emerald-50/50 border border-emerald-200/90 rounded-2xl p-3.5 sm:p-4 flex flex-col gap-3">
      {/* Header Row */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <div className="w-5 h-5 rounded-full bg-emerald-100 flex items-center justify-center text-emerald-700">
            <ShieldCheck className="w-3.5 h-3.5" />
          </div>
          <span className="text-xs font-black tracking-wide text-slate-900 uppercase">
            Order With Assistance
          </span>
        </div>
        <span className="px-2 py-0.5 rounded-full bg-white border border-emerald-300 text-emerald-800 text-[10px] font-bold">
          Rx Required
        </span>
      </div>

      {/* Reassurance Copy */}
      <p className="text-xs text-slate-600 leading-relaxed">
        Don&apos;t know exact medicine name? Upload prescription or message our certified pharmacist directly.
      </p>

      {/* 3 Action Buttons */}
      <div className="grid grid-cols-3 gap-2 mt-0.5">
        <button
          onClick={onUploadRx}
          className="flex items-center justify-center gap-1.5 py-2 px-2.5 rounded-xl bg-[#0E675E] text-white text-xs font-bold hover:bg-[#0C5B53] active:scale-[0.97] transition-all cursor-pointer shadow-sm"
        >
          <Upload className="w-3.5 h-3.5" />
          <span>Upload Rx</span>
        </button>

        <a
          href={whatsappUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center justify-center gap-1.5 py-2 px-2.5 rounded-xl bg-white border border-emerald-300 text-slate-800 text-xs font-bold hover:bg-emerald-50 active:scale-[0.97] transition-all cursor-pointer shadow-sm"
        >
          <MessageSquare className="w-3.5 h-3.5 text-emerald-600" />
          <span>WhatsApp</span>
        </a>

        <a
          href={callUrl}
          className="flex items-center justify-center gap-1.5 py-2 px-2.5 rounded-xl bg-white border border-emerald-300 text-slate-800 text-xs font-bold hover:bg-emerald-50 active:scale-[0.97] transition-all cursor-pointer shadow-sm"
        >
          <Phone className="w-3.5 h-3.5 text-emerald-600" />
          <span>Call 24x7</span>
        </a>
      </div>
    </div>
  );
}
