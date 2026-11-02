'use client';

import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const TRUST_MESSAGES = [
  'Checking 10-minute dark store stock in Rewari...',
  'Searching 50,000+ certified medicines & wellness...',
  'Matching 100% genuine batches & licensed pharmacies...',
  'Checking generic alternatives (save up to 40%)...',
];

/**
 * Smart Search Micro-Loading Interstitial benchmarked against PharmEasy & Zepto.
 * Combines zero-shift skeleton card placeholders with dynamic rotating trust copy
 * and 3 bouncing teal dots to eliminate perceived latency.
 */
export default function SearchMicroLoading() {
  const [messageIndex, setMessageIndex] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setMessageIndex((prev) => (prev + 1) % TRUST_MESSAGES.length);
    }, 850);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="relative w-full py-4 flex flex-col gap-4 select-none">
      {/* 1. Shimmer Filter Pills Skeleton Strip */}
      <div className="flex items-center gap-2 overflow-hidden px-1">
        {[80, 110, 95, 120, 90].map((width, i) => (
          <div
            key={i}
            style={{ width }}
            className="h-8 rounded-full bg-slate-200/80 animate-pulse shrink-0"
          />
        ))}
      </div>

      {/* 2. Shimmer Product Card Wireframes (Zero CLS) */}
      <div className="flex flex-col gap-3">
        {[1, 2, 3, 4].map((item) => (
          <div
            key={item}
            className="w-full bg-white rounded-2xl p-3.5 border border-slate-100 flex items-center justify-between shadow-xs"
          >
            <div className="flex items-center gap-3">
              {/* Product Thumbnail Skeleton */}
              <div className="w-16 h-16 rounded-xl bg-slate-200/80 animate-pulse shrink-0" />

              {/* Title & Subtitle Skeleton */}
              <div className="flex flex-col gap-2">
                <div className="w-36 sm:w-56 h-4 rounded-md bg-slate-200/80 animate-pulse" />
                <div className="w-24 sm:w-36 h-3 rounded-md bg-slate-200/60 animate-pulse" />
                <div className="w-16 h-4 rounded-md bg-slate-200/80 animate-pulse mt-1" />
              </div>
            </div>

            {/* Action Button Skeleton */}
            <div className="w-18 h-8 rounded-xl bg-slate-200/80 animate-pulse shrink-0" />
          </div>
        ))}
      </div>

      {/* 3. Floating Reassurance Trust Card (Center Overlaid) */}
      <div className="absolute inset-x-4 top-28 flex justify-center pointer-events-none">
        <motion.div
          initial={{ opacity: 0, scale: 0.9, y: 10 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          className="bg-white/95 backdrop-blur-md rounded-2xl px-5 py-4 border border-emerald-200 shadow-xl shadow-emerald-950/10 flex flex-col items-center gap-2 max-w-sm w-full text-center"
        >
          {/* Bouncing 3-Dot Pulse (PharmEasy Signature) */}
          <div className="flex items-center gap-1.5 py-1">
            {[0, 0.15, 0.3].map((delay, index) => (
              <motion.div
                key={index}
                animate={{ y: [0, -6, 0] }}
                transition={{
                  repeat: Infinity,
                  duration: 0.6,
                  delay,
                  ease: 'easeInOut',
                }}
                className="w-2.5 h-2.5 rounded-full bg-[#0E675E]"
              />
            ))}
          </div>

          {/* Rotating Reassurance Copy */}
          <div className="h-9 flex items-center justify-center">
            <AnimatePresence mode="wait">
              <motion.p
                key={messageIndex}
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.2 }}
                className="text-xs sm:text-sm font-bold text-slate-800"
              >
                {TRUST_MESSAGES[messageIndex]}
              </motion.p>
            </AnimatePresence>
          </div>

          <span className="text-[10px] font-extrabold uppercase tracking-wider text-emerald-700 bg-emerald-50 px-2.5 py-0.5 rounded-full">
            Verified Pharmacy Stock
          </span>
        </motion.div>
      </div>
    </div>
  );
}
