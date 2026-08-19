'use client';

import React, { useEffect, useState } from 'react';
import { IndianRupee, MapPin, Store, Clock, ArrowRight, ShieldCheck, CheckCircle2, XCircle } from 'lucide-react';

interface OrderOfferModalProps {
  offer: {
    id: string;
    totalAmount: number;
    estimatedPayout: number;
    tipAmount: number;
    distanceKm: number;
    estimatedMinutes: number;
    pickupStore: string;
    pickupAddress: string;
    customerName: string;
    customerAddress: string;
    paymentMethod: string;
    codAmount: number;
  };
  onAccept: () => void;
  onDecline: () => void;
}

export const OrderOfferModal: React.FC<OrderOfferModalProps> = ({
  offer,
  onAccept,
  onDecline,
}) => {
  const [secondsLeft, setSecondsLeft] = useState(30);

  useEffect(() => {
    if (secondsLeft <= 0) {
      onDecline();
      return;
    }
    const timer = setInterval(() => {
      setSecondsLeft((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [secondsLeft, onDecline]);

  const progressPercent = (secondsLeft / 30) * 100;

  return (
    <div className="fixed inset-0 z-50 bg-surface-inverse/90 backdrop-blur-sm flex items-end sm:items-center justify-center p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-lg bg-surface-inverse border border-border-strong rounded-3xl overflow-hidden shadow-2xl text-white">
        {/* Top Header & Countdown Timer */}
        <div className="relative bg-gradient-to-r from-navy-950 via-surface-inverse to-navy-950 p-5 border-b border-border-strong flex items-center justify-between">
          <div>
            <span className="px-2.5 py-0.5 rounded-full text-2xs font-black uppercase tracking-wider bg-action-primaryBg text-white shadow-md">
              New Delivery Assignment
            </span>
            <h3 className="text-lg font-black text-white mt-1">Order #{offer.id}</h3>
          </div>

          {/* Animated Circular Timer Badge */}
          <div className="relative w-14 h-14 flex items-center justify-center">
            {/* commerce-os:allow-vector-color */}
            <svg className="w-full h-full transform -rotate-90">
              <circle cx="28" cy="28" r="22" stroke="#1E293B" strokeWidth="4" fill="transparent" />
              <circle
                cx="28"
                cy="28"
                r="22"
                stroke="#10B981"
                strokeWidth="4"
                fill="transparent"
                strokeDasharray="138"
                strokeDashoffset={138 - (138 * progressPercent) / 100}
                className="transition-all duration-1000 ease-linear"
              />
            </svg>
            <span className="absolute font-black text-sm text-content-brand">{secondsLeft}s</span>
          </div>
        </div>

        <div className="p-6 space-y-5">
          {/* Earnings & Trip Stats Pod */}
          <div className="bg-surface-inverse/80 border border-border-strong rounded-2xl p-4 flex items-center justify-between">
            <div>
              <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Estimated Payout</span>
              <div className="flex items-baseline space-x-1.5 mt-0.5">
                <span className="text-3xl font-black text-content-brand">₹{offer.estimatedPayout}</span>
                {offer.tipAmount > 0 && (
                  <span className="text-xs font-extrabold text-content-warning bg-surface-warningSubtle px-2 py-0.5 rounded-full border border-border-warning">
                    Incl. ₹{offer.tipAmount} Tip
                  </span>
                )}
              </div>
            </div>

            <div className="text-right">
              <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Distance & Time</span>
              <p className="text-sm font-black text-white mt-0.5">
                {offer.distanceKm} km • ~{offer.estimatedMinutes} mins
              </p>
            </div>
          </div>

          {/* COD Banner if applicable */}
          {offer.paymentMethod === 'COD' ? (
            <div className="bg-surface-warningSubtle border border-border-warning rounded-2xl p-3.5 flex items-center space-x-3 text-content-warning">
              <div className="w-10 h-10 rounded-xl bg-action-warningBg text-content-primary flex items-center justify-center font-black text-lg shrink-0 shadow-lg">
                <IndianRupee className="w-5 h-5" />
              </div>
              <div>
                <p className="text-xs font-black uppercase tracking-wide text-content-warning">Doorstep COD Cash Collection</p>
                <p className="text-sm font-extrabold text-white">Collect ₹{offer.codAmount} Cash from Customer</p>
              </div>
            </div>
          ) : (
            <div className="bg-surface-inverse border border-border-brand rounded-2xl p-3 flex items-center space-x-2 text-content-brand text-xs font-bold">
              <CheckCircle2 className="w-4 h-4 text-content-brand shrink-0" />
              <span>Prepaid Order — Online Payment Confirmed (No Cash Collection)</span>
            </div>
          )}

          {/* Pickup & Customer Route Details */}
          <div className="space-y-3 relative before:absolute before:left-[19px] before:top-6 before:bottom-6 before:w-0.5 before:bg-surface-muted">
            {/* Pickup Location */}
            <div className="flex items-start space-x-3 relative z-10">
              <div className="w-10 h-10 rounded-xl bg-surface-brandSubtle border border-border-brand text-content-brand flex items-center justify-center shrink-0">
                <Store className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <span className="text-2xs font-bold text-content-brand uppercase tracking-wider">1. Pickup Store</span>
                <p className="text-xs font-extrabold text-white truncate">{offer.pickupStore}</p>
                <p className="text-2xs text-content-muted truncate">{offer.pickupAddress}</p>
              </div>
            </div>

            {/* Customer Drop Location */}
            <div className="flex items-start space-x-3 relative z-10">
              <div className="w-10 h-10 rounded-xl bg-surface-dangerSubtle border border-border-danger text-content-danger flex items-center justify-center shrink-0">
                <MapPin className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <span className="text-2xs font-bold text-content-danger uppercase tracking-wider">2. Delivery Customer</span>
                <p className="text-xs font-extrabold text-white truncate">{offer.customerName}</p>
                <p className="text-2xs text-content-muted truncate">{offer.customerAddress}</p>
              </div>
            </div>
          </div>

          {/* Action CTAs: Single Primary Dominant Accept Button */}
          <div className="pt-2 flex flex-col space-y-3">
            <button
              onClick={onAccept}
              className="w-full py-4 bg-action-primaryBg hover:bg-action-primaryHover text-content-primary font-black text-base rounded-2xl shadow-xl shadow-xl transition-all flex items-center justify-center space-x-2 active:scale-98"
            >
              <span>ACCEPT DELIVERY (EARN ₹{offer.estimatedPayout})</span>
              <ArrowRight className="w-5 h-5" />
            </button>

            <button
              onClick={onDecline}
              className="w-full py-3 bg-surface-inverse hover:bg-surface-muted text-content-muted hover:text-white font-bold text-xs rounded-xl transition-all"
            >
              Decline Offer
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
