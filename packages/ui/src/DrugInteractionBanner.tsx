import React from 'react';
import { DrugInteractionCheckResult } from '@commerce-os/types';
import { cn } from '@commerce-os/design-system';
import { ShieldAlert, AlertTriangle, Info } from 'lucide-react';

export interface DrugInteractionBannerProps {
  result: DrugInteractionCheckResult;
  className?: string;
}

export const DrugInteractionBanner: React.FC<DrugInteractionBannerProps> = ({
  result,
  className,
}) => {
  if (!result.hasInteraction) return null;

  const isSevere = result.highestSeverity === 'SEVERE_CONTRAINDICATION';

  return (
    <div
      className={cn(
        'rounded-xl border p-4 shadow-subtle transition-all',
        isSevere
          ? 'border-border-danger bg-surface-dangerSubtle text-content-danger'
          : 'border-border-warning bg-surface-warningSubtle text-content-warning',
        className
      )}
    >
      <div className="flex items-start gap-3">
        {isSevere ? (
          <ShieldAlert className="h-6 w-6 flex-shrink-0 text-content-danger" />
        ) : (
          <AlertTriangle className="h-6 w-6 flex-shrink-0 text-content-warning" />
        )}
        <div className="flex-1">
          <h4 className="font-bold text-sm">
            {isSevere ? 'Severe Drug Interaction Warning' : 'Potential Drug Interaction Alert'}
          </h4>
          <p className="text-xs mt-0.5 opacity-90">
            Our AI Safety Engine detected potential interactions between items in your cart:
          </p>

          <div className="mt-2 space-y-2">
            {result.interactions.map((interaction, idx) => (
              <div
                key={idx}
                className="rounded-md bg-surface-card p-2.5 text-xs shadow-subtle border border-border-subtle"
              >
                <div className="font-semibold text-content-primary">
                  {interaction.saltA} ↔ {interaction.saltB}
                </div>
                <div className="mt-1 text-content-secondary">
                  {interaction.clinicalEffect}
                </div>
                <div className="mt-1 flex items-center gap-1 font-medium text-content-accent">
                  <Info className="h-3.5 w-3.5" />
                  Recommendation: {interaction.recommendation}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
