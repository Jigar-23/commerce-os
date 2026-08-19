'use client';

import React, { useEffect, useState } from 'react';
import { IndianRupee, Award } from 'lucide-react';

const isProduction = process.env.NODE_ENV === 'production';
const ORDER_API = process.env.NEXT_PUBLIC_ORDER_API_URL && process.env.NEXT_PUBLIC_ORDER_API_URL.trim().length > 0
  ? process.env.NEXT_PUBLIC_ORDER_API_URL.replace(/\/$/, '')
  : isProduction ? '' : 'http://localhost:8090';

interface EarningsData {
  todayEarnings: number;
  weekEarnings: number;
  tripsToday: number;
  surgeBonus: number;
  codCollectedTotal: number;
}

export const EarningsView: React.FC = () => {
  const [earnings, setEarnings] = useState<EarningsData | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function fetchEarnings() {
      try {
        const res = await fetch(`${ORDER_API}/api/v1/delivery/rider/earnings`);
        if (res.ok) {
          const data = await res.json();
          setEarnings(data);
        }
      } catch {
        // Fallback
      } finally {
        setIsLoading(false);
      }
    }
    fetchEarnings();
  }, []);

  const todayTotal = earnings ? earnings.todayEarnings : 0;
  const tripsCount = earnings ? earnings.tripsToday : 0;
  const weekTotal = earnings ? earnings.weekEarnings : 0;
  const surgeBonus = earnings ? earnings.surgeBonus : 0;
  const codTotal = earnings ? earnings.codCollectedTotal : 0;

  return (
    <div className="space-y-5 text-white">
      {/* Hero Today's Earnings Card */}
      <div className="bg-gradient-to-br from-navy-950 via-surface-inverse to-navy-950 border border-border-brand rounded-3xl p-6 shadow-2xl relative overflow-hidden">
        <div className="flex items-center justify-between relative z-10">
          <div>
            <span className="text-xs font-bold text-content-brand uppercase tracking-widest">Authoritative Today's Earnings</span>
            <div className="flex items-baseline space-x-1 mt-1">
              <span className="text-4xl font-black text-white">₹{todayTotal.toFixed(2)}</span>
            </div>
            <p className="text-xs text-content-muted mt-2">{tripsCount} Completed Deliveries</p>
          </div>

          <div className="w-14 h-14 rounded-2xl bg-action-primaryBg text-content-primary flex items-center justify-center font-black text-2xl shadow-xl shadow-subtle">
            <IndianRupee className="w-7 h-7" />
          </div>
        </div>

        {/* Breakdown Row */}
        <div className="grid grid-cols-3 gap-3 pt-5 border-t border-border-strong mt-5 relative z-10">
          <div className="bg-surface-inverse/80 p-3 rounded-2xl border border-border-strong">
            <span className="text-2xs font-bold text-content-muted uppercase">Week Total</span>
            <p className="text-base font-black text-white mt-0.5">₹{weekTotal.toFixed(0)}</p>
          </div>
          <div className="bg-surface-inverse/80 p-3 rounded-2xl border border-border-strong">
            <span className="text-2xs font-bold text-content-muted uppercase">Surge Bonus</span>
            <p className="text-base font-black text-white mt-0.5">₹{surgeBonus.toFixed(0)}</p>
          </div>
          <div className="bg-surface-inverse/80 p-3 rounded-2xl border border-border-strong">
            <span className="text-2xs font-bold text-content-warning uppercase">COD Collected</span>
            <p className="text-base font-black text-content-warning mt-0.5">₹{codTotal.toFixed(0)}</p>
          </div>
        </div>
      </div>

      {/* Daily Incentive Challenge Card */}
      <div className="bg-surface-inverse border border-border-strong rounded-3xl p-5 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-2xl bg-surface-accentSubtle border border-border-accent text-content-accent flex items-center justify-center">
              <Award className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-xs font-black text-white">Peak Hour Daily Bonus</h4>
              <p className="text-2xs text-content-muted">Complete 10 orders to earn ₹150 extra</p>
            </div>
          </div>
          <span className="text-xs font-black text-content-accent bg-surface-inverse px-2.5 py-1 rounded-full border border-border-accent">
            ₹150 Bonus
          </span>
        </div>

        {/* Progress Bar */}
        <div className="space-y-1.5">
          <div className="flex justify-between text-xs font-bold">
            <span className="text-content-muted">{tripsCount} of 10 Orders Completed</span>
            <span className="text-content-accent">{Math.min(100, Math.round((tripsCount / 10) * 100))}%</span>
          </div>
          <div className="w-full h-3 bg-surface-inverse rounded-full overflow-hidden border border-border-strong p-0.5">
            <div
              style={{ width: `${Math.min(100, Math.round((tripsCount / 10) * 100))}%` }}
              className="h-full bg-action-speedBg rounded-full transition-all"
            />
          </div>
        </div>
      </div>
    </div>
  );
};
