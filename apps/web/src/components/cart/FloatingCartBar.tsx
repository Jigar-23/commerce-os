'use client';

import React from 'react';
import Link from 'next/link';
import { motion, AnimatePresence } from 'framer-motion';
import { ShoppingBag, ArrowRight, Zap } from 'lucide-react';
import { useCart } from '@/lib/cart-store';

interface FloatingCartBarProps {
  onOpenCart?: () => void;
  checkoutHref?: string;
}

/**
 * Floating Capsule Cart Bar benchmarked directly against the CommerceOS Android App.
 * Persistently surfaces real-time cart state with 10-minute SLA and 1-tap checkout transition.
 */
export default function FloatingCartBar({
  onOpenCart,
  checkoutHref = '/checkout',
}: FloatingCartBarProps) {
  const lines = useCart((s) => s.lines);
  const itemCount = lines.reduce((sum, l) => sum + l.quantity, 0);
  const subtotal = lines.reduce((sum, l) => sum + (l.unitPrice || 0) * l.quantity, 0);

  if (itemCount === 0) return null;

  return (
    <AnimatePresence>
      <div className="fixed bottom-20 sm:bottom-6 inset-x-4 sm:inset-x-auto sm:left-1/2 sm:-translate-x-1/2 z-40 max-w-md w-full pointer-events-none">
        <motion.div
          initial={{ y: 60, opacity: 0, scale: 0.95 }}
          animate={{ y: 0, opacity: 1, scale: 1 }}
          exit={{ y: 60, opacity: 0, scale: 0.95 }}
          transition={{ type: 'spring', stiffness: 350, damping: 25 }}
          className="pointer-events-auto"
        >
          <Link
            href={checkoutHref}
            onClick={(e) => {
              if (onOpenCart) {
                e.preventDefault();
                onOpenCart();
              }
            }}
            className="w-full bg-[#005844] hover:bg-[#004737] active:scale-[0.98] text-white rounded-2xl p-3.5 shadow-xl shadow-emerald-950/20 border border-emerald-700/50 flex items-center justify-between cursor-pointer transition-all"
          >
            {/* Left: Cart Icon & Order Summary */}
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-white/20 flex items-center justify-center shrink-0">
                <ShoppingBag className="w-5 h-5 text-white" />
              </div>

              <div className="flex flex-col">
                <span className="text-sm font-extrabold tracking-tight">
                  {itemCount} {itemCount === 1 ? 'ITEM' : 'ITEMS'} • ₹{subtotal.toFixed(0)}
                </span>
                <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-300 uppercase tracking-wider">
                  <Zap className="w-3 h-3 fill-current text-yellow-300" />
                  10 Mins Delivery
                </span>
              </div>
            </div>

            {/* Right: View Cart Action */}
            <div className="flex items-center gap-1.5 text-xs font-black tracking-wide text-white uppercase pr-1 group">
              <span>View Cart</span>
              <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </div>
          </Link>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
