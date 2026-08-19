'use client';

import React from 'react';
import { Store, Home, Phone, IndianRupee, ArrowRight, ShieldAlert, CheckCircle2, RefreshCw, Navigation, AlertCircle } from 'lucide-react';

interface ActiveDeliveryCardProps {
  order: {
    id: string;
    deliveryId?: string;
    orderStatus: string;
    deliveryState: string;
    totalAmount: number;
    paymentMethod: string;
    paymentStatus: string;
    storeName?: string;
    storeAddress?: string;
    storePhone?: string;
    customerName?: string;
    customerPhone?: string;
    deliveryAddress: string;
    distanceKm?: number;
    estimatedMinutes?: number;
  };
  onTransitionState: (targetState: string) => Promise<void>;
  onOpenHelpModal: () => void;
  onOpenCodSheet: () => void;
  onOpenOtpSheet: () => void;
  isSubmitting: boolean;
  otpVerified: boolean;
  codReconciled: boolean;
}

export const ActiveDeliveryCard: React.FC<ActiveDeliveryCardProps> = ({
  order,
  onTransitionState,
  onOpenHelpModal,
  onOpenCodSheet,
  onOpenOtpSheet,
  isSubmitting,
  otpVerified,
  codReconciled,
}) => {
  const currentState = order.deliveryState || 'ACCEPTED';
  const isCod = order.paymentMethod === 'COD';

  // State Machine Phase Resolver
  const getPhaseDetails = () => {
    switch (currentState) {
      case 'ACCEPTED':
        return {
          stepName: 'Step 1/5: Accepted',
          stageTitle: 'Head to Store for Pickup',
          ctaText: 'NAVIGATE TO STORE',
          nextState: 'EN_ROUTE_PICKUP',
          badgeColor: 'bg-action-speedBg text-white',
        };
      case 'EN_ROUTE_PICKUP':
        return {
          stepName: 'Step 2/5: En Route Store',
          stageTitle: 'Traveling to Merchant Store',
          ctaText: "I'VE ARRIVED AT STORE",
          nextState: 'ARRIVED_PICKUP',
          badgeColor: 'bg-action-speedBg text-white',
        };
      case 'ARRIVED_PICKUP':
        return {
          stepName: 'Step 3/5: At Store',
          stageTitle: 'Verify & Collect Parcel',
          ctaText: 'CONFIRM PICKUP & PACK AUDIT',
          nextState: 'PICKED_UP',
          badgeColor: 'bg-action-primaryBg text-white',
        };
      case 'PICKED_UP':
        return {
          stepName: 'Step 4/5: Picked Up',
          stageTitle: 'Deliver to Customer',
          ctaText: 'NAVIGATE TO CUSTOMER',
          nextState: 'EN_ROUTE_CUSTOMER',
          badgeColor: 'bg-action-primaryBg text-white',
        };
      case 'EN_ROUTE_CUSTOMER':
        return {
          stepName: 'Step 4/5: En Route Customer',
          stageTitle: 'Traveling to Delivery Address',
          ctaText: "I'VE ARRIVED AT LOCATION",
          nextState: 'ARRIVED_CUSTOMER',
          badgeColor: 'bg-action-primaryBg text-white',
        };
      case 'ARRIVED_CUSTOMER':
        return {
          stepName: 'Step 5/5: At Doorstep',
          stageTitle: 'Customer Handoff',
          ctaText: 'START CUSTOMER HANDOFF',
          nextState: 'HANDOFF_STARTED',
          badgeColor: 'bg-action-primaryBg text-white',
        };
      case 'HANDOFF_STARTED':
        if (isCod && !codReconciled) {
          return {
            stepName: 'Step 5/5: Cash Collection',
            stageTitle: 'Collect Cash Payment',
            ctaText: `COLLECT COD CASH (₹${order.totalAmount})`,
            action: onOpenCodSheet,
            badgeColor: 'bg-action-warningBg text-white',
          };
        }
        if (!otpVerified) {
          return {
            stepName: 'Step 5/5: OTP Verification',
            stageTitle: 'Verify Customer PIN',
            ctaText: 'VERIFY CUSTOMER OTP PIN',
            action: onOpenOtpSheet,
            badgeColor: 'bg-action-primaryBg text-white',
          };
        }
        return {
          stepName: 'Step 5/5: Complete',
          stageTitle: 'Ready to Complete',
          ctaText: 'COMPLETE DELIVERY',
          nextState: 'DELIVERED',
          badgeColor: 'bg-action-primaryBg text-content-primary font-black',
        };
      case 'RETURN_INITIATED':
        return {
          stepName: 'Return Active',
          stageTitle: 'Return to Store',
          ctaText: 'CONFIRM PARCEL RETURNED',
          nextState: 'RETURNED',
          badgeColor: 'bg-action-dangerBg text-white',
        };
      default:
        return {
          stepName: 'Active Task',
          stageTitle: 'Proceed with Delivery',
          ctaText: 'PROCEED TO NEXT STEP',
          nextState: 'EN_ROUTE_PICKUP',
          badgeColor: 'bg-action-speedBg text-white',
        };
    }
  };

  const phase = getPhaseDetails();

  const handlePrimaryClick = async () => {
    if (phase.action) {
      phase.action();
      return;
    }
    if (phase.nextState) {
      await onTransitionState(phase.nextState);
    }
  };

  const isHeadingToCustomer = ['PICKED_UP', 'EN_ROUTE_CUSTOMER', 'ARRIVED_CUSTOMER', 'HANDOFF_STARTED'].includes(currentState);

  return (
    <div className="bg-surface-inverse border border-border-strong rounded-3xl p-4 text-white shadow-2xl space-y-3.5">
      {/* 1. Header: Current State & Stage Info + Help Button */}
      <div className="flex items-center justify-between border-b border-border-strong pb-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className={`px-2 py-0.5 rounded-full text-2xs font-black uppercase tracking-wider ${phase.badgeColor}`}>
              {phase.stepName}
            </span>
            <span className="text-xs font-extrabold text-content-muted">Order #{order.id}</span>
          </div>
          <h3 className="text-sm font-black text-white mt-0.5 truncate">{phase.stageTitle}</h3>
        </div>

        <button
          onClick={onOpenHelpModal}
          className="px-2.5 py-1.5 bg-surface-inverse hover:bg-surface-muted text-content-danger border border-border-strong rounded-xl text-2xs font-bold flex items-center gap-1 shrink-0 transition-all"
        >
          <ShieldAlert className="w-3.5 h-3.5" />
          <span>Help</span>
        </button>
      </div>

      {/* 2. PRIMARY ACTION CTA — FIRST VIEWPORT PROMINENCE */}
      <div>
        <button
          onClick={handlePrimaryClick}
          disabled={isSubmitting}
          className="w-full py-3.5 bg-action-primaryBg hover:bg-action-primaryHover disabled:opacity-50 text-content-primary font-black text-sm sm:text-base rounded-2xl shadow-xl shadow-xl transition-all flex items-center justify-center space-x-2 active:scale-98"
        >
          {isSubmitting ? (
            <>
              <RefreshCw className="w-4 h-4 animate-spin text-content-primary" />
              <span>UPDATING DELIVERY STATE...</span>
            </>
          ) : (
            <>
              <span>{phase.ctaText}</span>
              <ArrowRight className="w-4 h-4" />
            </>
          )}
        </button>
      </div>

      {/* 3. COD Banner (If Applicable) */}
      {isCod && (
        <div className="bg-surface-warningSubtle border border-border-warning rounded-xl px-3 py-2 flex items-center justify-between text-xs text-content-warning">
          <div className="flex items-center space-x-1.5">
            <IndianRupee className="w-3.5 h-3.5 text-content-warning shrink-0" />
            <span className="font-bold">COD Cash to Collect: ₹{order.totalAmount}</span>
          </div>
          <span className={`text-2xs font-black px-2 py-0.5 rounded-full ${codReconciled ? 'bg-surface-inverse text-content-brand border border-border-brand' : 'bg-action-warningBg text-content-primary'}`}>
            {codReconciled ? 'CASH COLLECTED' : 'PENDING'}
          </span>
        </div>
      )}

      {/* 4. Active Destination Focus */}
      <div className="bg-surface-inverse/80 border border-border-strong rounded-2xl p-3 space-y-2 text-xs">
        {/* Next immediate target highlight */}
        {!isHeadingToCustomer ? (
          <div className="flex items-start justify-between">
            <div className="flex items-start space-x-2.5 min-w-0">
              <div className="p-1.5 rounded-lg bg-surface-brandSubtle text-content-brand shrink-0 mt-0.5">
                <Store className="w-3.5 h-3.5" />
              </div>
              <div className="min-w-0">
                <span className="text-2xs font-bold text-content-brand uppercase tracking-wider">Pickup From Store</span>
                <h4 className="text-xs font-black text-white truncate">{order.storeName || 'Merchant Store Hub'}</h4>
                <p className="text-2xs text-content-muted truncate">{order.storeAddress || 'Fulfillment Node Address'}</p>
              </div>
            </div>
            {order.storePhone && (
              <a
                href={`tel:${order.storePhone}`}
                className="p-1.5 rounded-lg bg-surface-inverse hover:bg-surface-muted text-content-muted shrink-0 border border-border-strong"
              >
                <Phone className="w-3 h-3" />
              </a>
            )}
          </div>
        ) : (
          <div className="flex items-start justify-between">
            <div className="flex items-start space-x-2.5 min-w-0">
              <div className="p-1.5 rounded-lg bg-surface-dangerSubtle text-content-danger shrink-0 mt-0.5">
                <Home className="w-3.5 h-3.5" />
              </div>
              <div className="min-w-0">
                <span className="text-2xs font-bold text-content-danger uppercase tracking-wider">Deliver to Customer</span>
                <h4 className="text-xs font-black text-white truncate">{order.customerName || 'Customer'}</h4>
                <p className="text-2xs text-content-muted truncate">{order.deliveryAddress}</p>
              </div>
            </div>
            {order.customerPhone && (
              <a
                href={`tel:${order.customerPhone}`}
                className="p-1.5 rounded-lg bg-surface-inverse hover:bg-surface-muted text-content-muted shrink-0 border border-border-strong"
              >
                <Phone className="w-3 h-3" />
              </a>
            )}
          </div>
        )}
      </div>

      {/* 5. Doorstep Verification Checklist (During Handoff) */}
      {currentState === 'HANDOFF_STARTED' && (
        <div className="bg-surface-inverse/80 border border-border-strong rounded-xl p-3 space-y-1.5 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-content-muted font-bold">1. Cash Collection</span>
            {codReconciled || !isCod ? (
              <span className="text-content-brand font-bold flex items-center gap-1 text-2xs">
                <CheckCircle2 className="w-3 h-3" /> Done
              </span>
            ) : (
              <button onClick={onOpenCodSheet} className="text-content-warning font-bold underline text-2xs">Collect Cash</button>
            )}
          </div>
          <div className="flex items-center justify-between">
            <span className="text-content-muted font-bold">2. Customer OTP PIN</span>
            {otpVerified ? (
              <span className="text-content-brand font-bold flex items-center gap-1 text-2xs">
                <CheckCircle2 className="w-3 h-3" /> Verified
              </span>
            ) : (
              <button onClick={onOpenOtpSheet} className="text-content-brand font-bold underline text-2xs">Enter OTP</button>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
