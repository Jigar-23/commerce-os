'use client';

import React, { useState } from 'react';
import { IndianRupee, AlertTriangle, CheckCircle2, RefreshCw, XCircle } from 'lucide-react';

interface CodCollectionSheetProps {
  orderId: string;
  requiredAmount: number;
  onConfirmCod: (collectedAmount: number) => Promise<void>;
  onReportMismatch: (collectedAmount: number, reason: string) => void;
  isSubmitting: boolean;
}

export const CodCollectionSheet: React.FC<CodCollectionSheetProps> = ({
  orderId,
  requiredAmount,
  onConfirmCod,
  onReportMismatch,
  isSubmitting,
}) => {
  const [cashInput, setCashInput] = useState<string>(String(requiredAmount));
  const [mismatchReason, setMismatchReason] = useState('Customer shortage');
  const [errorMsg, setErrorMsg] = useState('');

  const tenderedAmount = parseFloat(cashInput) || 0;
  const changeToReturn = tenderedAmount > requiredAmount ? tenderedAmount - requiredAmount : 0;
  const isShortage = tenderedAmount < requiredAmount;

  const handleConfirm = async () => {
    setErrorMsg('');
    if (isShortage) {
      setErrorMsg(`Collected cash (₹${tenderedAmount}) is less than required ₹${requiredAmount}. Full cash collection is required or report mismatch to dispatch.`);
      return;
    }

    try {
      await onConfirmCod(tenderedAmount);
    } catch (err: any) {
      setErrorMsg(err?.message || 'Server error reconciling COD cash. Please retry.');
    }
  };

  return (
    <div className="bg-surface-inverse border border-border-strong rounded-3xl p-6 text-white shadow-2xl space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border-strong pb-4">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-2xl bg-action-warningBg text-content-primary flex items-center justify-center font-black text-xl shadow-lg">
            <IndianRupee className="w-6 h-6" />
          </div>
          <div>
            <span className="text-2xs font-black uppercase tracking-wider text-content-warning">Doorstep Cash Collection</span>
            <h3 className="text-base font-extrabold text-white">Order #{orderId}</h3>
          </div>
        </div>
        <span className="px-3 py-1 rounded-full text-xs font-black bg-surface-warningSubtle text-content-warning border border-border-warning">
          COD REQUIRED
        </span>
      </div>

      {/* Main Cash Target Banner */}
      <div className="bg-surface-warningSubtle border border-border-warning rounded-2xl p-5 text-center">
        <span className="text-xs font-bold text-content-warning uppercase tracking-widest">Exact Cash To Collect</span>
        <div className="text-4xl font-black text-content-warning mt-1">₹{requiredAmount.toFixed(2)}</div>
        <p className="text-xs text-content-warning/80 mt-1">Hand physical cash before proceeding to OTP verification</p>
      </div>

      {errorMsg && (
        <div className="p-3 bg-surface-dangerSubtle border border-border-danger text-content-danger text-xs font-bold rounded-xl flex items-center space-x-2">
          <XCircle className="w-4 h-4 text-content-danger shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Tendered Input & Keypad Chips */}
      <div className="space-y-3">
        <label className="text-xs font-bold text-content-muted block">Tendered Cash Handed by Customer</label>
        <div className="relative">
          <span className="absolute left-4 top-3 text-lg font-black text-content-muted">₹</span>
          <input
            type="number"
            value={cashInput}
            onChange={(e) => {
              setCashInput(e.target.value);
              setErrorMsg('');
            }}
            className="w-full pl-10 pr-4 py-3 bg-surface-inverse border border-border-strong rounded-2xl font-black text-xl text-white outline-none focus:border-border-warning transition-all"
            placeholder="0.00"
          />
        </div>

        {/* Quick Amount Chips */}
        <div className="flex items-center space-x-2 pt-1">
          <button
            type="button"
            onClick={() => setCashInput(String(requiredAmount))}
            className="px-3 py-1.5 bg-surface-inverse hover:bg-surface-muted rounded-xl text-xs font-bold text-content-warning border border-border-strong"
          >
            Exact (₹{requiredAmount})
          </button>
          <button
            type="button"
            onClick={() => setCashInput(String(Math.ceil(requiredAmount / 100) * 100))}
            className="px-3 py-1.5 bg-surface-inverse hover:bg-surface-muted rounded-xl text-xs font-bold text-content-muted border border-border-strong"
          >
            ₹{Math.ceil(requiredAmount / 100) * 100}
          </button>
          <button
            type="button"
            onClick={() => setCashInput('1000')}
            className="px-3 py-1.5 bg-surface-inverse hover:bg-surface-muted rounded-xl text-xs font-bold text-content-muted border border-border-strong"
          >
            ₹1000
          </button>
        </div>
      </div>

      {/* Change Calculation Box */}
      {tenderedAmount > 0 && (
        <div className="bg-surface-inverse border border-border-strong rounded-2xl p-4 flex items-center justify-between text-xs">
          <div>
            <span className="text-content-muted font-medium">Change to return customer:</span>
            <p className={`text-base font-black ${changeToReturn > 0 ? 'text-content-warning' : 'text-content-brand'}`}>
              ₹{changeToReturn.toFixed(2)}
            </p>
          </div>
          {isShortage && (
            <span className="text-content-danger font-bold bg-surface-dangerSubtle px-2.5 py-1 rounded-lg border border-border-danger">
              Shortage: ₹{(requiredAmount - tenderedAmount).toFixed(2)}
            </span>
          )}
        </div>
      )}

      {/* Mismatch Exception Flow */}
      {isShortage && (
        <div className="bg-surface-dangerSubtle border border-border-danger rounded-2xl p-4 space-y-3">
          <div className="flex items-center space-x-2 text-content-danger text-xs font-bold">
            <AlertTriangle className="w-4 h-4 text-content-danger" />
            <span>Cash shortage detected! Select exception report:</span>
          </div>
          <select
            value={mismatchReason}
            onChange={(e) => setMismatchReason(e.target.value)}
            className="w-full p-2.5 bg-surface-inverse border border-border-danger rounded-xl text-xs font-bold text-white outline-none"
          >
            <option value="Customer shortage">Customer missing exact cash</option>
            <option value="Customer refused COD">Customer refused full payment</option>
          </select>
          <button
            type="button"
            onClick={() => onReportMismatch(tenderedAmount, mismatchReason)}
            className="w-full py-2 bg-action-dangerHover hover:bg-action-dangerBg text-white rounded-xl text-xs font-bold shadow-md"
          >
            Create Server Cash Exception Ticket
          </button>
        </div>
      )}

      {/* Dominant Confirmation CTA */}
      <button
        onClick={handleConfirm}
        disabled={isSubmitting || isShortage}
        className="w-full py-4 bg-action-warningBg hover:opacity-90 disabled:opacity-50 text-content-primary font-black text-base rounded-2xl shadow-xl shadow-xl transition-all flex items-center justify-center space-x-2 active:scale-98"
      >
        {isSubmitting ? (
          <>
            <RefreshCw className="w-5 h-5 animate-spin text-content-primary" />
            <span>RECONCILING CASH WITH SERVER...</span>
          </>
        ) : (
          <>
            <CheckCircle2 className="w-5 h-5 text-content-primary" />
            <span>CONFIRM CASH RECEIVED (₹{tenderedAmount || requiredAmount})</span>
          </>
        )}
      </button>
    </div>
  );
};
