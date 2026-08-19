'use client';

import React from 'react';
import { ShieldCheck, Wifi, WifiOff, MapPin, Star, Truck, Battery, Signal, Zap } from 'lucide-react';

interface RiderHeaderProps {
  isOnline: boolean;
  setIsOnline: (online: boolean) => void;
  gpsStatus: 'HIGH_PRECISION' | 'WEAK_SIGNAL' | 'UNAVAILABLE' | 'PERMISSION_DENIED';
  gpsStaleSeconds: number;
  networkStatus: 'ONLINE' | 'RECONNECTING' | 'OFFLINE';
  pendingSyncCount: number;
  riderInfo: {
    name: string;
    id: string;
    vehicle: string;
    rating: number;
    zone: string;
  };
}

export const RiderHeader: React.FC<RiderHeaderProps> = ({
  isOnline,
  setIsOnline,
  gpsStatus,
  gpsStaleSeconds,
  networkStatus,
  pendingSyncCount,
  riderInfo,
}) => {
  return (
    <div className="bg-surface-inverse text-white border-b border-border-strong shadow-lg shrink-0">
      {/* Mobile Top Status Bar Simulation */}
      <div className="px-4 py-1.5 flex items-center justify-between text-2xs font-mono text-content-muted border-b border-border-strong bg-black/20">
        <div className="flex items-center space-x-2">
          <span className="font-bold text-white">12:44</span>
          <span className="text-2xs px-1.5 py-0.2 bg-surface-inverse text-content-brand rounded border border-border-brand">5G HIGH-SPEED</span>
        </div>
        <div className="flex items-center space-x-3 text-2xs">
          <span className="flex items-center gap-1 text-content-muted">
            <Signal className="w-3 h-3 text-content-brand" />
            Full
          </span>
          <span className="flex items-center gap-1 text-content-muted">
            <Battery className="w-3 h-3 text-content-brand" />
            92%
          </span>
        </div>
      </div>

      {/* Network / Unsynced Offline Banner */}
      {networkStatus !== 'ONLINE' && (
        <div className="bg-action-warningBg text-white px-4 py-2 flex items-center justify-between text-xs font-semibold animate-pulse">
          <div className="flex items-center space-x-2">
            <WifiOff className="w-4 h-4 shrink-0" />
            <span>
              {networkStatus === 'OFFLINE'
                ? 'Network Offline — Actions saved locally'
                : 'Reconnecting to Rider Server...'}
            </span>
          </div>
          {pendingSyncCount > 0 && (
            <span className="px-2 py-0.5 bg-black/30 rounded text-2xs font-bold">
              {pendingSyncCount} Pending Sync
            </span>
          )}
        </div>
      )}

      {/* GPS Warning Banner */}
      {gpsStatus !== 'HIGH_PRECISION' && (
        <div className="bg-surface-dangerSubtle text-content-danger px-4 py-1.5 flex items-center justify-between text-xs border-b border-border-danger">
          <div className="flex items-center space-x-2">
            <MapPin className="w-3.5 h-3.5 text-content-danger animate-bounce" />
            <span>
              {gpsStatus === 'UNAVAILABLE'
                ? 'GPS Location Unavailable — Verify Phone Settings'
                : gpsStatus === 'WEAK_SIGNAL'
                ? `GPS Signal Weak (${gpsStaleSeconds}s stale)`
                : 'Location Access Denied'}
            </span>
          </div>
          <button className="text-2xs underline font-bold hover:text-white">Fix GPS</button>
        </div>
      )}

      {/* Primary Header Info */}
      <div className="p-4 flex items-center justify-between">
        {/* Rider Avatar + Info */}
        <div className="flex items-center space-x-3">
          <div className="relative">
            <div className="w-11 h-11 rounded-2xl bg-action-primaryBg flex items-center justify-center text-white font-black text-lg shadow-md border border-border-brand/30">
              {riderInfo?.name ? riderInfo.name.charAt(0) : 'R'}
            </div>
            <div
              className={`absolute -bottom-1 -right-1 w-4 h-4 rounded-full border-2 border-surface-inverse ${
                isOnline ? 'bg-action-primaryBg' : 'bg-surface-muted'
              }`}
            />
          </div>

          <div>
            <div className="flex items-center space-x-2">
              <h2 className="text-sm font-extrabold text-white tracking-tight">{riderInfo?.name || 'Rider Partner'}</h2>
              {riderInfo?.rating != null && (
                <span className="px-1.5 py-0.5 rounded text-2xs font-bold bg-surface-warningSubtle text-content-warning border border-border-warning flex items-center gap-0.5">
                  <Star className="w-2.5 h-2.5 fill-content-warning" />
                  {riderInfo.rating}
                </span>
              )}
            </div>
            <div className="flex items-center space-x-2 text-2xs text-content-muted mt-0.5">
              <span className="flex items-center gap-1 text-content-muted">
                <Truck className="w-3 h-3 text-content-accent" />
                {riderInfo?.vehicle || 'Vehicle'}
              </span>
              <span>•</span>
              <span className="truncate max-w-[110px]">{riderInfo?.zone || 'Zone'}</span>
            </div>
          </div>
        </div>

        {/* Duty Status Switch */}
        <div className="flex flex-col items-end space-y-1">
          <button
            onClick={() => setIsOnline(!isOnline)}
            className={`px-4 py-2 rounded-xl text-xs font-black transition-all flex items-center space-x-2 shadow-lg active:scale-95 ${
              isOnline
                ? 'bg-action-primaryBg hover:bg-action-primaryBg text-white shadow-subtle ring-2 ring-border-brandSubtle'
                : 'bg-surface-inverse hover:bg-surface-muted text-content-muted border border-border-strong'
            }`}
          >
            <Zap className={`w-3.5 h-3.5 ${isOnline ? 'fill-white animate-pulse' : 'text-content-secondary'}`} />
            <span>{isOnline ? 'DUTY ON' : 'GO ONLINE'}</span>
          </button>
          <span className="text-2xs font-medium text-content-muted">
            {isOnline ? 'Ready for order assignments' : 'You are currently offline'}
          </span>
        </div>
      </div>
    </div>
  );
};
