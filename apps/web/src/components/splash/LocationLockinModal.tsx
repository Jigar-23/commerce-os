'use client';

import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Navigation, Zap } from 'lucide-react';

interface LocationLockinModalProps {
  locality?: string;
  onDismiss: () => void;
}

/**
 * High-performing Location Lock-in Interstitial benchmarked directly against Tata 1mg.
 * Visualizes instantaneous GPS acquisition with procedural vector map geometry
 * and a 2-phase radar lock-in animation.
 */
export default function LocationLockinModal({
  locality = 'Rewari',
  onDismiss,
}: LocationLockinModalProps) {
  const [isLocked, setIsLocked] = useState(false);

  useEffect(() => {
    // Phase 1 -> Phase 2 transition at 600ms
    const lockTimer = setTimeout(() => {
      setIsLocked(true);
    }, 600);

    // Auto-dismiss smoothly at 1350ms
    const dismissTimer = setTimeout(() => {
      onDismiss();
    }, 1350);

    return () => {
      clearTimeout(lockTimer);
      clearTimeout(dismissTimer);
    };
  }, [onDismiss]);

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.25 }}
        onClick={onDismiss}
        className="fixed inset-0 z-50 flex items-center justify-center bg-[#FAFAF8] cursor-pointer overflow-hidden select-none"
      >
        {/* Procedural Vector Map Canvas Background */}
        <svg
          className="absolute inset-0 w-full h-full pointer-events-none opacity-40"
          xmlns="http://www.w3.org/2000/svg"
        >
          {/* Secondary Street Grid */}
          <line x1="5%" y1="0%" x2="45%" y2="100%" stroke="#E5E5DE" strokeWidth="2.5" />
          <line x1="25%" y1="0%" x2="65%" y2="100%" stroke="#E5E5DE" strokeWidth="2" />
          <line x1="75%" y1="0%" x2="35%" y2="100%" stroke="#E5E5DE" strokeWidth="2.5" />
          <line x1="0%" y1="20%" x2="100%" y2="40%" stroke="#E5E5DE" strokeWidth="3" />
          <line x1="0%" y1="65%" x2="100%" y2="85%" stroke="#E5E5DE" strokeWidth="2.5" />
          <line x1="0%" y1="85%" x2="100%" y2="45%" stroke="#E5E5DE" strokeWidth="2" />

          {/* Primary Arterial Highway (Warm Cream / Orange) */}
          <line x1="0%" y1="48%" x2="100%" y2="52%" stroke="#F6D365" strokeWidth="6" strokeLinecap="round" />

          {/* Meandering Water Canal (Soft Blue) */}
          <path
            d="M 20 600 Q 120 750 60 900 T 140 1200"
            fill="none"
            stroke="#D6E4FF"
            strokeWidth="12"
            strokeLinecap="round"
          />

          {/* Faint Landmark Points */}
          <circle cx="68%" cy="42%" r="6" fill="#FCA5A5" opacity="0.6" />
          <circle cx="65%" cy="82%" r="6" fill="#FCA5A5" opacity="0.6" />
        </svg>

        {/* Center Radar Spotlight & Target Node */}
        <div className="relative flex flex-col items-center justify-center -mt-16">
          {/* Radar Spotlight Cone */}
          <motion.div
            animate={{
              scale: isLocked ? [1, 1.15, 1] : [0.95, 1.05, 0.95],
              rotate: [0, 360],
            }}
            transition={{
              rotate: { repeat: Infinity, duration: 6, ease: 'linear' },
              scale: { duration: 0.5, ease: 'easeInOut' },
            }}
            className={`w-44 h-44 rounded-full flex items-center justify-center transition-all duration-500 ${
              isLocked
                ? 'bg-emerald-500/15 border-2 border-emerald-600 shadow-xl shadow-emerald-500/20'
                : 'bg-slate-200/40 border border-slate-300'
            }`}
          >
            {/* Concentric Pulse Rings */}
            {!isLocked && (
              <motion.div
                animate={{ scale: [1, 1.6], opacity: [0.6, 0] }}
                transition={{ repeat: Infinity, duration: 1.2, ease: 'easeOut' }}
                className="absolute inset-0 rounded-full border border-slate-400"
              />
            )}

            {/* Inner Center Circle */}
            <div
              className={`w-28 h-28 rounded-full flex items-center justify-center transition-all duration-500 ${
                isLocked ? 'bg-emerald-50' : 'bg-white/80 shadow-md'
              }`}
            >
              {/* Navigation Needle */}
              <motion.div
                animate={{
                  rotate: isLocked ? 45 : 30,
                  scale: isLocked ? 1.2 : 1,
                }}
                transition={{ type: 'spring', stiffness: 300, damping: 20 }}
              >
                <Navigation
                  className={`w-8 h-8 transition-colors duration-300 ${
                    isLocked ? 'text-emerald-600 fill-emerald-600' : 'text-slate-800 fill-slate-800'
                  }`}
                />
              </motion.div>
            </div>
          </motion.div>

          {/* Micro-Reassurance Text */}
          <div className="mt-8 text-center min-h-[70px] flex flex-col items-center justify-center px-4">
            <AnimatePresence mode="wait">
              {!isLocked ? (
                <motion.p
                  key="searching"
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -6 }}
                  className="text-base font-semibold text-slate-700 tracking-tight"
                >
                  Searching your location...
                </motion.p>
              ) : (
                <motion.div
                  key="locked"
                  initial={{ opacity: 0, scale: 0.95, y: 6 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  className="flex flex-col items-center"
                >
                  <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Delivering to
                  </span>
                  <h2 className="text-xl font-black text-slate-900 tracking-tight mt-0.5">
                    {locality}
                  </h2>
                  <div className="inline-flex items-center gap-1.5 px-3 py-1 mt-2.5 rounded-full bg-emerald-100/90 border border-emerald-300/60 shadow-sm">
                    <Zap className="w-3.5 h-3.5 text-emerald-700 fill-emerald-700" />
                    <span className="text-xs font-extrabold text-emerald-800 tracking-wide uppercase">
                      10 Mins Delivery
                    </span>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        {/* Bottom Hint */}
        <div className="absolute bottom-6 text-2xs text-slate-400 font-medium">
          Tap anywhere to skip
        </div>
      </motion.div>
    </AnimatePresence>
  );
}
