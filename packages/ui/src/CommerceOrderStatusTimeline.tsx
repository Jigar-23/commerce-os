import React from 'react';
import { CheckCircle2, Clock, Package, Truck, Home, AlertCircle } from 'lucide-react';
import { cn } from '@commerce-os/design-system';

export interface TimelineStep {
  id: string;
  label: string;
  description?: string;
  timestamp?: string;
  status: 'completed' | 'current' | 'upcoming' | 'failed';
}

export interface CommerceOrderStatusTimelineProps {
  status: string;
  createdAt?: string;
  className?: string;
}

export const CommerceOrderStatusTimeline: React.FC<CommerceOrderStatusTimelineProps> = ({
  status = 'PLACED',
  createdAt,
  className = '',
}) => {
  const norm = (status || '').toUpperCase();

  const isDelivered = norm === 'DELIVERED' || norm === 'COMPLETED';
  const isOutForDelivery = norm === 'OUT_FOR_DELIVERY' || isDelivered;
  const isPacked = norm === 'PACKED' || isOutForDelivery;
  const isAccepted = norm === 'SELLER_ACCEPTED' || norm === 'PROCESSING' || isPacked;
  const isCancelled = norm === 'CANCELLED' || norm === 'FAILED';

  if (isCancelled) {
    return (
      <div className={cn('bg-surface-dangerSubtle border border-border-danger rounded-xl p-4 flex items-start gap-3', className)}>
        <AlertCircle className="h-5 w-5 text-content-danger shrink-0 mt-0.5" />
        <div>
          <h4 className="text-xs font-bold text-content-danger">Order Cancelled</h4>
          <p className="text-2xs text-content-danger font-medium mt-0.5">
            This order has been cancelled and any reserved inventory has been restored.
          </p>
        </div>
      </div>
    );
  }

  const steps: TimelineStep[] = [
    {
      id: 'placed',
      label: 'Order Placed',
      description: 'Received & routed to nearest dispatch hub',
      status: 'completed',
      timestamp: createdAt ? new Date(createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : undefined,
    },
    {
      id: 'accepted',
      label: 'Store Accepted & Items Verified',
      description: 'Merchant confirmed item availability',
      status: isAccepted ? (isPacked ? 'completed' : 'current') : 'upcoming',
    },
    {
      id: 'packed',
      label: 'Order Packed & Sealed',
      description: 'Bag sealed & barcode assigned for dispatch',
      status: isPacked ? (isOutForDelivery ? 'completed' : 'current') : 'upcoming',
    },
    {
      id: 'out_for_delivery',
      label: 'Out for Instant Delivery',
      description: 'Delivery partner en route with GPS beacon',
      status: isOutForDelivery ? (isDelivered ? 'completed' : 'current') : 'upcoming',
    },
    {
      id: 'delivered',
      label: 'Delivered to Doorstep',
      description: 'Handover verified via 4-digit OTP',
      status: isDelivered ? 'completed' : 'upcoming',
    },
  ];

  return (
    <div className={cn('space-y-4 relative pl-4 border-l-2 border-border-strong ml-3.5', className)}>
      {steps.map((step) => {
        const isDone = step.status === 'completed';
        const isCurrent = step.status === 'current';

        return (
          <div key={step.id} className="relative group">
            {/* TIMELINE NODE */}
            <div
              className={cn(
                'absolute -left-[23px] top-0.5 w-5 h-5 rounded-full flex items-center justify-center border-2 transition-all',
                isDone
                  ? 'bg-action-primaryBg border-surface-card text-action-primaryText shadow-subtle'
                  : isCurrent
                  ? 'bg-surface-card border-border-brand text-content-brand shadow-card ring-4 ring-border-brand/20 animate-pulse'
                  : 'bg-surface-subtle border-border-strong text-content-muted'
              )}
            >
              {isDone ? (
                <CheckCircle2 className="h-3 w-3 stroke-[3]" />
              ) : (
                <div className={cn('w-2 h-2 rounded-full', isCurrent ? 'bg-action-primaryBg' : 'bg-surface-muted')} />
              )}
            </div>

            {/* CONTENT */}
            <div className="ml-2">
              <div className="flex items-baseline justify-between gap-2">
                <h4
                  className={cn(
                    'text-xs font-bold tracking-tight',
                    isDone || isCurrent ? 'text-content-primary' : 'text-content-muted'
                  )}
                >
                  {step.label}
                </h4>
                {step.timestamp && (
                  <span className="text-2xs text-content-muted font-medium">{step.timestamp}</span>
                )}
              </div>
              {step.description && (
                <p className="text-2xs text-content-secondary mt-0.5 leading-relaxed">{step.description}</p>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};
