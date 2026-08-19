'use client';

import React from 'react';
import { Navigation, Store, Home, ExternalLink, AlertTriangle } from 'lucide-react';

interface NavigationMapProps {
  deliveryState: string;
  storeName?: string;
  storeAddress?: string;
  customerName?: string;
  customerAddress?: string;
  gpsStaleSeconds: number;
  gpsStatus: string;
  riderLat?: number;
  riderLng?: number;
  heading?: number;
  onManualArrival?: () => void;
}

export const NavigationMap: React.FC<NavigationMapProps> = ({
  deliveryState,
  storeName = 'Merchant Store',
  storeAddress = 'Store Address',
  customerName = 'Customer',
  customerAddress = 'Customer Address',
  gpsStaleSeconds,
  gpsStatus,
  riderLat,
  riderLng,
  heading = 0,
  onManualArrival,
}) => {
  const isHeadingToStore = ['ACCEPTED', 'EN_ROUTE_PICKUP', 'ARRIVED_PICKUP'].includes(deliveryState);
  const isHeadingToCustomer = ['PICKED_UP', 'EN_ROUTE_CUSTOMER', 'ARRIVED_CUSTOMER', 'HANDOFF_STARTED'].includes(deliveryState);

  const targetTitle = isHeadingToStore ? storeName : isHeadingToCustomer ? customerName : 'Destination';
  const targetAddress = isHeadingToStore ? storeAddress : isHeadingToCustomer ? customerAddress : '';

  const turnInstruction = isHeadingToStore
    ? 'Proceed to Merchant Store'
    : isHeadingToCustomer
    ? 'Proceed to Customer Address'
    : 'Arrived at Destination';

  const isStale = gpsStaleSeconds > 15 || gpsStatus === 'UNAVAILABLE' || !riderLat || !riderLng;

  // SVG coordinates for vector map rendering
  const storePos = { x: 80, y: 180 };
  const customerPos = { x: 320, y: 80 };
  const riderPos = isHeadingToStore
    ? { x: 60, y: 200 }
    : { x: 200, y: 135 };

  return (
    <div className="relative w-full h-[220px] sm:h-[260px] bg-surface-inverse overflow-hidden rounded-2xl border border-border-strong shadow-inner group">
      {/* 1. TOP TURN-BY-TURN INSTRUCTION BANNER */}
      <div className="absolute top-3 left-3 right-3 z-20 bg-surface-inverse/90 backdrop-blur-md border border-border-strong rounded-xl p-3 shadow-xl flex items-center justify-between text-white">
        <div className="flex items-center space-x-3 min-w-0">
          <div className="p-2 rounded-lg bg-action-primaryBg text-white shrink-0 shadow-md">
            <Navigation className="w-5 h-5" />
          </div>
          <div className="min-w-0">
            <p className="text-xs font-black truncate">{turnInstruction}</p>
            <p className="text-2xs text-content-muted truncate">Towards {targetTitle} • {targetAddress}</p>
          </div>
        </div>
      </div>

      {/* 2. STALE TELEMETRY OVERLAY BANNER */}
      {isStale && (
        <div className="absolute top-16 left-3 right-3 z-30 bg-surface-warningSubtle border border-border-warning text-content-warning px-3 py-2 rounded-xl text-xs font-extrabold flex items-center space-x-2 shadow-xl backdrop-blur-sm animate-pulse">
          <AlertTriangle className="w-4 h-4 text-content-warning shrink-0" />
          <span>Rider location temporarily unavailable — Waiting for live GPS update</span>
        </div>
      )}

      {/* 3. VECTOR CANVAS MAP BACKGROUND */}
      {/* commerce-os:allow-vector-color */}
      <svg className="w-full h-full bg-surface-inverse" viewBox="0 0 400 280">
        <defs>
          <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
            <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#1E293B" strokeWidth="1" />
          </pattern>
          <linearGradient id="routeGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#10B981" />
            <stop offset="100%" stopColor="#06B6D4" />
          </linearGradient>
        </defs>

        <rect width="100%" height="100%" fill="url(#grid)" />

        {/* Road Polylines */}
        <path d="M 10 230 L 120 180 L 220 190 L 320 80 L 390 60" fill="none" stroke="#334155" strokeWidth="14" strokeLinecap="round" />
        <path d="M 10 230 L 120 180 L 220 190 L 320 80 L 390 60" fill="none" stroke="#1E293B" strokeWidth="10" strokeLinecap="round" />

        {/* Active Route Line */}
        <path
          d={isHeadingToStore ? "M 20 235 L 80 180" : "M 80 180 L 220 190 L 320 80"}
          fill="none"
          stroke="url(#routeGradient)"
          strokeWidth="6"
          strokeDasharray="6 3"
          className="animate-pulse"
        />

        {/* Store Marker Pin */}
        <g transform={`translate(${storePos.x}, ${storePos.y})`}>
          <circle r="14" fill="#10B981" fillOpacity="0.2" />
          <circle r="10" fill="#10B981" />
          <Store className="w-3.5 h-3.5 text-white -translate-x-1.5 -translate-y-1.5" />
        </g>

        {/* Customer Marker Pin */}
        <g transform={`translate(${customerPos.x}, ${customerPos.y})`}>
          <circle r="16" fill="#F43F5E" fillOpacity="0.2" />
          <circle r="10" fill="#F43F5E" />
          <Home className="w-3.5 h-3.5 text-white -translate-x-1.5 -translate-y-1.5" />
        </g>

        {/* Live Rider Marker Pin with Bearing Rotation */}
        {!isStale && (
          <g transform={`translate(${riderPos.x}, ${riderPos.y}) rotate(${heading})`}>
            <circle r="16" fill="#6366F1" fillOpacity="0.3" className="animate-ping" />
            <circle r="12" fill="#4F46E5" stroke="#FFFFFF" strokeWidth="2" />
            <Navigation className="w-4 h-4 text-white -translate-x-2 -translate-y-2" />
          </g>
        )}
      </svg>

      {/* 4. GPS SIGNAL / STALENESS INDICATOR (BOTTOM LEFT) */}
      <div className="absolute bottom-3 left-3 z-20 flex items-center space-x-2">
        <div
          className={`px-2.5 py-1 rounded-lg text-2xs font-bold backdrop-blur-md border flex items-center gap-1.5 shadow-md ${
            isStale
              ? 'bg-surface-warningSubtle text-content-warning border-border-warning'
              : 'bg-surface-inverse text-content-brand border-border-brand'
          }`}
        >
          <span
            className={`w-2 h-2 rounded-full ${
              isStale ? 'bg-action-warningBg animate-ping' : 'bg-action-primaryBg animate-pulse'
            }`}
          />
          <span>
            {isStale
              ? `Location Unavailable (${gpsStaleSeconds}s stale)`
              : `GPS Active • Updated ${gpsStaleSeconds}s ago`}
          </span>
        </div>
      </div>

      {/* 5. GOOGLE MAPS NAVIGATION LAUNCHER (BOTTOM RIGHT) */}
      <div className="absolute bottom-3 right-3 z-20 flex items-center space-x-1.5">
        <a
          href={`https://maps.google.com/?q=${encodeURIComponent(targetAddress)}`}
          target="_blank"
          rel="noopener noreferrer"
          className="p-2 rounded-lg bg-action-primaryBg hover:bg-action-primaryBg text-white shadow-lg text-xs font-bold flex items-center gap-1 border border-border-brand/40"
          title="Open in Google Maps"
        >
          <ExternalLink className="w-3.5 h-3.5" />
        </a>
      </div>
    </div>
  );
};
