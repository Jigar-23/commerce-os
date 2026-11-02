'use client';

import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Navigation,
  ChevronDown,
  User,
  Search,
  Camera,
  Zap,
} from 'lucide-react';

interface CanopyHeaderProps {
  locationLabel?: string;
  etaMinutes?: number;
  onLocationClick: () => void;
  onProfileClick: () => void;
  onSearchClick: (initialQuery?: string) => void;
  onUploadRxClick: () => void;
}

const SEARCH_HINTS = [
  'Search medicines, e.g. Dolo 650...',
  'Search for Paracetamol, Crocin...',
  'Search for Augmentin 625 Duo...',
  'Search for Benadryl Cough syrup...',
  'Search for Glycomet, Insulin...',
  'Search for Pan 40, Digene...',
];

/**
 * Deep Medical Teal Canopy Header benchmarked directly against the CommerceOS Android App.
 * Features synchronized 10-minute ETA badge, animated search hint carousel,
 * and quick-action prescription upload trigger.
 */
export default function CanopyHeader({
  locationLabel = 'Home • Rewari',
  etaMinutes = 10,
  onLocationClick,
  onProfileClick,
  onSearchClick,
  onUploadRxClick,
}: CanopyHeaderProps) {
  const [hintIndex, setHintIndex] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setHintIndex((prev) => (prev + 1) % SEARCH_HINTS.length);
    }, 3000);
    return () => clearInterval(timer);
  }, []);

  return (
    <header className="w-full bg-gradient-to-b from-[#0E675E] via-[#0C5B53] to-[#0A4F48] rounded-b-[24px] shadow-md px-4 sm:px-6 pt-3 pb-5 text-white">
      <div className="max-w-4xl mx-auto flex flex-col gap-3.5">
        {/* Top Location & Profile Navigation Bar */}
        <div className="flex items-center justify-between">
          {/* Location Trigger */}
          <button
            onClick={onLocationClick}
            className="flex items-center gap-2.5 text-left group cursor-pointer focus:outline-none"
          >
            <div className="w-9 h-9 rounded-full bg-white/15 border border-white/20 flex items-center justify-center shrink-0 group-hover:bg-white/25 transition-all">
              <Navigation className="w-4 h-4 text-white fill-white rotate-45" />
            </div>

            <div className="flex flex-col">
              <span className="text-[10px] font-extrabold uppercase tracking-wider text-white/75">
                Delivering to
              </span>
              <div className="flex items-center gap-1.5">
                <span className="text-sm sm:text-base font-extrabold text-white tracking-tight line-clamp-1">
                  {locationLabel}
                </span>
                <ChevronDown className="w-4 h-4 text-white/80 shrink-0" />
                <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-[#DCFCE7] text-[#166534] text-[9px] font-black uppercase tracking-wider">
                  <Zap className="w-2.5 h-2.5 fill-current" />
                  {etaMinutes} Mins
                </span>
              </div>
            </div>
          </button>

          {/* Profile Button */}
          <button
            onClick={onProfileClick}
            className="w-9 h-9 rounded-full bg-white/15 border border-white/25 flex items-center justify-center hover:bg-white/25 active:scale-95 transition-all cursor-pointer"
            aria-label="Account Profile"
          >
            <User className="w-4 h-4 text-white" />
          </button>
        </div>

        {/* High-Utility Stadium Search Bar */}
        <div
          onClick={() => onSearchClick()}
          className="w-full h-12 bg-white rounded-full shadow-md flex items-center px-4 gap-3 cursor-pointer select-none hover:shadow-lg transition-all"
        >
          <Search className="w-5 h-5 text-[#0E675E] shrink-0" />

          {/* Animated Hint Carosel */}
          <div className="flex-1 overflow-hidden relative h-5">
            <AnimatePresence mode="wait">
              <motion.span
                key={hintIndex}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.25 }}
                className="absolute inset-0 text-xs sm:text-sm font-medium text-slate-500 truncate"
              >
                {SEARCH_HINTS[hintIndex]}
              </motion.span>
            </AnimatePresence>
          </div>

          {/* Vertical Hairline Divider */}
          <div className="w-px h-5 bg-slate-200 shrink-0" />

          {/* Quick Rx Upload Pill */}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onUploadRxClick();
            }}
            className="flex items-center gap-1.5 py-1 px-2 text-[#0E675E] hover:bg-emerald-50 rounded-lg transition-colors cursor-pointer"
          >
            <Camera className="w-4 h-4 shrink-0 text-[#0E675E]" />
            <span className="text-xs font-bold whitespace-nowrap">Upload Rx</span>
          </button>
        </div>
      </div>
    </header>
  );
}
