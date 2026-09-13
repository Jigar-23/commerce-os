'use client';

import React, { useState } from 'react';
import { Key, ShieldCheck, Phone, RefreshCw, XCircle, CheckCircle2 } from 'lucide-react';

interface OtpVerificationSheetProps {
  orderId: string;
  customerPhone: string;
  attemptsLeft: number;
  onVerifyOtp: (otp: string) => Promise<void>;
  onResendOtp: () => void;
  isSubmitting: boolean;
}

export const OtpVerificationSheet: React.FC<OtpVerificationSheetProps> = ({
  orderId,
  customerPhone,
  attemptsLeft,
  onVerifyOtp,
  onResendOtp,
  isSubmitting,
}) => {
  const [pin, setPin] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [resendSent, setResendSent] = useState(false);

  const handleVerify = async () => {
    if (pin.trim().length < 4) {
      setErrorMsg('Please enter full 4-digit OTP code');
      return;
    }
    setErrorMsg('');
    try {
      await onVerifyOtp(pin.trim());
    } catch (err: any) {
      setErrorMsg(err?.message || 'Invalid OTP code. Please check SMS on customer phone.');
    }
  };

  const handleResend = () => {
    onResendOtp();
    setResendSent(true);
    setTimeout(() => setResendSent(false), 5000);
  };

  return (
    <div className="bg-surface-inverse border border-border-strong rounded-3xl p-6 text-white shadow-2xl space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border-strong pb-4">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-2xl bg-action-primaryBg text-content-primary flex items-center justify-center font-black text-xl shadow-lg">
            <Key className="w-6 h-6" />
          </div>
          <div>
            <span className="text-2xs font-black uppercase tracking-wider text-content-brand">Final Step: Customer OTP Handoff</span>
            <h3 className="text-base font-extrabold text-white">Order #{orderId}</h3>
          </div>
        </div>
        <span className="px-3 py-1 rounded-full text-xs font-black bg-surface-inverse text-content-brand border border-border-brand">
          STRICT VERIFICATION
        </span>
      </div>

      {/* Info Instruction */}
      <div className="bg-surface-inverse border border-border-strong rounded-2xl p-4 text-center space-y-1">
        <p className="text-xs font-bold text-content-muted">Ask Customer for 4-Digit Delivery PIN</p>
        <p className="text-2xs text-content-muted">PIN sent via SMS to customer's phone ({customerPhone})</p>
      </div>

      {errorMsg && (
        <div className="p-3.5 bg-surface-dangerSubtle border border-border-danger text-content-danger text-xs font-bold rounded-2xl flex items-center space-x-2">
          <XCircle className="w-4 h-4 text-content-danger shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {resendSent && (
        <div className="p-3 bg-surface-inverse border border-border-brand text-content-brand text-xs font-bold rounded-2xl text-center">
          New OTP code dispatched via SMS to customer!
        </div>
      )}

      {/* 4-Digit PIN Input Field */}
      <div className="space-y-3">
        <div className="flex justify-center">
          <input
            type="text"
            maxLength={4}
            value={pin}
            onChange={(e) => {
              setPin(e.target.value.replace(/\D/g, ''));
              setErrorMsg('');
            }}
            placeholder="• • • •"
            className="w-full max-w-[240px] py-3.5 text-center font-mono text-3xl tracking-[0.5em] font-black bg-surface-inverse border-2 border-border-strong focus:border-border-brand rounded-2xl text-content-brand outline-none transition-all shadow-inner"
          />
        </div>

        <div className="flex items-center justify-between text-2xs font-bold text-content-muted px-2">
          <span>{attemptsLeft} attempts remaining</span>
        </div>
      </div>

      {/* Auxiliary Help Trigger Buttons */}
      <div className="grid grid-cols-2 gap-3 pt-1">
        <button
          type="button"
          onClick={handleResend}
          className="py-2.5 bg-surface-inverse hover:bg-surface-muted text-content-muted rounded-xl text-xs font-bold border border-border-strong flex items-center justify-center space-x-1.5"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Resend OTP SMS</span>
        </button>

        {customerPhone && (
          <a
            href={`tel:${customerPhone}`}
            className="py-2.5 bg-surface-inverse hover:bg-surface-muted text-content-muted rounded-xl text-xs font-bold border border-border-strong flex items-center justify-center space-x-1.5"
          >
            <Phone className="w-3.5 h-3.5 text-content-brand" />
            <span>Call Customer</span>
          </a>
        )}
      </div>

      {/* Dominant Verification CTA */}
      <button
        onClick={handleVerify}
        disabled={isSubmitting || pin.length < 4}
        className="w-full py-4 bg-action-primaryBg hover:bg-action-primaryHover disabled:opacity-50 text-content-primary font-black text-base rounded-2xl shadow-xl shadow-xl transition-all flex items-center justify-center space-x-2 active:scale-98"
      >
        {isSubmitting ? (
          <>
            <RefreshCw className="w-5 h-5 animate-spin text-content-primary" />
            <span>VERIFYING OTP PIN...</span>
          </>
        ) : (
          <>
            <CheckCircle2 className="w-5 h-5 text-content-primary" />
            <span>VERIFY OTP & COMPLETE DELIVERY</span>
          </>
        )}
      </button>
    </div>
  );
};
