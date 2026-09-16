'use client';

import React, { useEffect, useState, useCallback } from 'react';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import { sellerApi } from '@/lib/apiClient';
import {
  MapPin, Store, Navigation, Crosshair, CheckCircle2, Clock, Plus,
  ExternalLink, ShieldCheck, AlertTriangle, RefreshCw, X, Radio,
  ArrowRight, Layers, Compass, Check, Search
} from 'lucide-react';

export interface StoreHub {
  id: string;
  storeName: string;
  address: string;
  latitude: number;
  longitude: number;
  slaMinutes: number;
  sellerApprovalRequired: boolean;
  isActive: boolean;
  serviceRadiusKm: number;
  createdAt?: string;
}

interface NearbyStoreResult {
  storeId: string;
  storeName: string;
  address: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
  slaMinutes: number;
  isServing: boolean;
  serviceRadiusKm: number;
}

// Initial authoritative stores across key operational fulfillment zones
const DEFAULT_STORES: StoreHub[] = [
  {
    id: 'STORE_REWARI_01',
    storeName: 'Commerce OS Rewari Central Store Hub',
    address: '3126/21D Company Bagh, Circular Road, Rewari, Haryana 123401',
    latitude: 28.202224,
    longitude: 76.615418,
    slaMinutes: 8,
    sellerApprovalRequired: false,
    isActive: true,
    serviceRadiusKm: 10.0,
    createdAt: new Date().toISOString(),
  },
  {
    id: 'STORE_GURGAON_01',
    storeName: 'Cyber City Quick Fulfillment Hub',
    address: 'DLF Cyber City, Phase 2, Sector 24, Gurugram, Haryana 122002',
    latitude: 28.4906,
    longitude: 77.0898,
    slaMinutes: 10,
    sellerApprovalRequired: false,
    isActive: true,
    serviceRadiusKm: 12.0,
    createdAt: new Date().toISOString(),
  },
  {
    id: 'STORE_DELHI_01',
    storeName: 'South Delhi Dark Store & Hub',
    address: 'A-24 Hauz Khas Enclave, New Delhi, Delhi 110016',
    latitude: 28.5494,
    longitude: 77.2001,
    slaMinutes: 10,
    sellerApprovalRequired: false,
    isActive: true,
    serviceRadiusKm: 12.0,
    createdAt: new Date().toISOString(),
  },
  {
    id: 'STORE_BANGALORE_01',
    storeName: 'Koramangala Super Dark Store',
    address: '12, 80 Feet Road, 4th Block, Koramangala, Bengaluru, Karnataka 560034',
    latitude: 12.9352,
    longitude: 77.6245,
    slaMinutes: 8,
    sellerApprovalRequired: false,
    isActive: true,
    serviceRadiusKm: 8.0,
    createdAt: new Date().toISOString(),
  },
];

// Predefined test customer locations for quick proximity simulation
const SIMULATION_PRESETS = [
  { name: 'Rewari Model Town (Near Rewari Hub)', lat: 28.1989, lng: 76.6186 },
  { name: 'Gurgaon Golf Course Road', lat: 28.4682, lng: 77.0984 },
  { name: 'Green Park Delhi (Near Hauz Khas)', lat: 28.5588, lng: 77.2028 },
  { name: 'Indiranagar Bangalore (Near Koramangala)', lat: 12.9719, lng: 77.6412 },
  { name: 'Faridabad Sector 15 (Out of Hub Reach)', lat: 28.4089, lng: 77.3178 },
];

function calculateHaversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371; // Earth radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  // Straight line * 1.25 urban winding road factor
  return Math.round(R * c * 1.25 * 10) / 10;
}

export default function StoresManagementPage() {
  const [stores, setStores] = useState<StoreHub[]>(DEFAULT_STORES);
  const [isLoading, setIsLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingStore, setEditingStore] = useState<StoreHub | null>(null);

  // Add / Edit Form State
  const [formStoreName, setFormStoreName] = useState('');
  const [formAddress, setFormAddress] = useState('');
  const [formLatitude, setFormLatitude] = useState('');
  const [formLongitude, setFormLongitude] = useState('');
  const [formSlaMinutes, setFormSlaMinutes] = useState('10');
  const [formServiceRadiusKm, setFormServiceRadiusKm] = useState('10');
  const [formIsActive, setFormIsActive] = useState(true);
  const [formSellerApproval, setFormSellerApproval] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isLocatingUser, setIsLocatingUser] = useState(false);

  // Proximity Store Capture Simulator State
  const [simLat, setSimLat] = useState<string>('28.1989');
  const [simLng, setSimLng] = useState<string>('76.6186');
  const [capturedStore, setCapturedStore] = useState<NearbyStoreResult | null>(null);
  const [nearbyRankings, setNearbyRankings] = useState<NearbyStoreResult[]>([]);
  const [simMessage, setSimMessage] = useState<string | null>(null);

  const fetchStores = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await sellerApi.get<{ ok: boolean; count: number; stores: StoreHub[] }>('/api/v1/seller/stores');
      if (res && res.data && Array.isArray(res.data.stores) && res.data.stores.length > 0) {
        setStores(res.data.stores);
      } else {
        // Safe fallback to authoritative default network
        setStores(DEFAULT_STORES);
      }
    } catch {
      setStores(DEFAULT_STORES);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStores();
  }, [fetchStores]);

  // Run Proximity Store-Capture Resolution
  const handleResolveNearestStore = (inputLat?: number, inputLng?: number) => {
    const lat = inputLat !== undefined ? inputLat : parseFloat(simLat);
    const lng = inputLng !== undefined ? inputLng : parseFloat(simLng);

    if (isNaN(lat) || isNaN(lng)) {
      setSimMessage('Please enter valid numerical latitude and longitude coordinates.');
      return;
    }

    // Rank all active stores by road distance
    const ranked: NearbyStoreResult[] = stores
      .filter((s) => s.isActive)
      .map((s) => {
        const dist = calculateHaversineKm(lat, lng, s.latitude, s.longitude);
        const radius = s.serviceRadiusKm || 10;
        return {
          storeId: s.id,
          storeName: s.storeName,
          address: s.address,
          latitude: s.latitude,
          longitude: s.longitude,
          distanceKm: dist,
          slaMinutes: s.slaMinutes,
          isServing: dist <= radius,
          serviceRadiusKm: radius,
        };
      })
      .sort((a, b) => a.distanceKm - b.distanceKm);

    setNearbyRankings(ranked);

    const nearestServing = ranked.find((r) => r.isServing) || (ranked.length > 0 ? ranked[0] : null);
    setCapturedStore(nearestServing);

    if (nearestServing && nearestServing.isServing) {
      setSimMessage(`✅ Customer automatically captured by "${nearestServing.storeName}" (${nearestServing.distanceKm} km away • ~${nearestServing.slaMinutes}m SLA).`);
    } else if (nearestServing) {
      setSimMessage(`⚠️ Nearest store "${nearestServing.storeName}" is ${nearestServing.distanceKm} km away, exceeding service radius of ${nearestServing.serviceRadiusKm} km.`);
    } else {
      setSimMessage('No active fulfillment hubs found in system.');
    }
  };

  // Initial trigger for default simulation
  useEffect(() => {
    if (stores.length > 0 && !capturedStore) {
      handleResolveNearestStore(28.1989, 76.6186);
    }
  }, [stores]);

  const handleUseCurrentLocationForSimulation = () => {
    if (typeof window === 'undefined' || !navigator.geolocation) {
      alert('Geolocation is not supported by your browser.');
      return;
    }
    setIsLocatingUser(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = parseFloat(pos.coords.latitude.toFixed(6));
        const lng = parseFloat(pos.coords.longitude.toFixed(6));
        setSimLat(lat.toString());
        setSimLng(lng.toString());
        setIsLocatingUser(false);
        handleResolveNearestStore(lat, lng);
      },
      (err) => {
        setIsLocatingUser(false);
        alert(`Location access denied or unavailable: ${err.message}`);
      },
      { enableHighAccuracy: true, timeout: 8000 }
    );
  };

  const handleUseCurrentLocationForForm = () => {
    if (typeof window === 'undefined' || !navigator.geolocation) {
      alert('Geolocation is not supported by your browser.');
      return;
    }
    setIsLocatingUser(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setFormLatitude(pos.coords.latitude.toFixed(6));
        setFormLongitude(pos.coords.longitude.toFixed(6));
        setIsLocatingUser(false);
      },
      (err) => {
        setIsLocatingUser(false);
        alert(`Unable to fetch device GPS: ${err.message}`);
      },
      { enableHighAccuracy: true, timeout: 8000 }
    );
  };

  const openAddStoreModal = () => {
    setEditingStore(null);
    setFormStoreName('');
    setFormAddress('');
    setFormLatitude('');
    setFormLongitude('');
    setFormSlaMinutes('10');
    setFormServiceRadiusKm('10');
    setFormIsActive(true);
    setFormSellerApproval(false);
    setFormError(null);
    setShowAddModal(true);
  };

  const openEditStoreModal = (store: StoreHub) => {
    setEditingStore(store);
    setFormStoreName(store.storeName);
    setFormAddress(store.address);
    setFormLatitude(store.latitude.toString());
    setFormLongitude(store.longitude.toString());
    setFormSlaMinutes((store.slaMinutes || 10).toString());
    setFormServiceRadiusKm((store.serviceRadiusKm || 10).toString());
    setFormIsActive(store.isActive);
    setFormSellerApproval(store.sellerApprovalRequired);
    setFormError(null);
    setShowAddModal(true);
  };

  const handleSaveStore = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!formStoreName.trim()) {
      setFormError('Store or Dark Store Hub name is required.');
      return;
    }
    if (!formAddress.trim()) {
      setFormError('Street address is required.');
      return;
    }
    const lat = parseFloat(formLatitude);
    const lng = parseFloat(formLongitude);
    if (isNaN(lat) || isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      setFormError('Enter valid pinpoint latitude (-90 to 90) and longitude (-180 to 180).');
      return;
    }

    setIsSaving(true);
    try {
      const storeId = editingStore ? editingStore.id : `STORE_${formStoreName.replace(/[^a-zA-Z0-9]/g, '_').toUpperCase().slice(0, 14)}_${Date.now().toString().slice(-4)}`;
      const payload: StoreHub = {
        id: storeId,
        storeName: formStoreName.trim(),
        address: formAddress.trim(),
        latitude: lat,
        longitude: lng,
        slaMinutes: parseInt(formSlaMinutes, 10) || 10,
        serviceRadiusKm: parseFloat(formServiceRadiusKm) || 10.0,
        sellerApprovalRequired: formSellerApproval,
        isActive: formIsActive,
      };

      // Call API
      await sellerApi.post('/api/v1/seller/stores', payload);

      // Local update
      setStores((prev) => {
        const idx = prev.findIndex((s) => s.id === storeId);
        if (idx >= 0) {
          const updated = [...prev];
          updated[idx] = payload;
          return updated;
        }
        return [payload, ...prev];
      });

      setShowAddModal(false);
      // Re-run simulation
      handleResolveNearestStore();
    } catch (err: any) {
      setFormError(err?.message || 'Failed to save store details.');
    } finally {
      setIsSaving(false);
    }
  };

  const filteredStores = stores.filter(
    (s) =>
      s.storeName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.address.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.id.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-subtle overflow-hidden">
        {/* Left Sidebars */}
        <SellerSidebar activeTab="stores" onRefresh={fetchStores} isLoading={isLoading} />

        {/* Main Content Area */}
        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          <HeaderQuickSearch />

          <main className="p-8 max-w-7xl w-full mx-auto space-y-8">
            {/* Page Header */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <div className="flex items-center space-x-2">
                  <span className="px-2.5 py-0.5 rounded-full text-2xs font-bold bg-action-primaryBg text-white uppercase tracking-wider">
                    Network Hubs
                  </span>
                  <span className="text-content-muted text-xs">• Instant Geo-Routing</span>
                </div>
                <h1 className="text-2xl font-black text-content-primary mt-1 flex items-center gap-2">
                  <Store className="w-7 h-7 text-action-primaryBg" />
                  Stores & Fulfillment Hubs
                </h1>
                <p className="text-xs text-content-secondary mt-1">
                  Manage dark stores, fulfillment centers, exact GPS pinpoint locations, and automatic customer proximity-capture routing.
                </p>
              </div>

              <div className="flex items-center gap-3">
                <button
                  onClick={fetchStores}
                  className="px-4 py-2 text-xs font-bold text-content-secondary bg-white border border-border-default rounded-xl hover:bg-surface-subtle transition-all flex items-center gap-2 shadow-sm"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-action-primaryBg' : ''}`} />
                  Refresh
                </button>
                <button
                  onClick={openAddStoreModal}
                  className="px-4 py-2 text-xs font-bold text-white bg-action-primaryBg rounded-xl hover:bg-action-primaryBg/90 transition-all flex items-center gap-2 shadow-md shadow-action-primaryBg/20"
                >
                  <Plus className="w-4 h-4" />
                  Add Fulfillment Hub
                </button>
              </div>
            </div>

            {/* Metric Banner */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase">Total Hubs</span>
                  <div className="w-8 h-8 rounded-xl bg-surface-accentSubtle text-content-accent flex items-center justify-center">
                    <Store className="w-4 h-4" />
                  </div>
                </div>
                <p className="text-2xl font-black text-content-primary mt-2">{stores.length}</p>
                <p className="text-2xs text-content-muted mt-1">Dark stores & fulfillment centers</p>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase">Active Serving</span>
                  <div className="w-8 h-8 rounded-xl bg-surface-subtle text-action-successBg flex items-center justify-center">
                    <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                  </div>
                </div>
                <p className="text-2xl font-black text-emerald-600 mt-2">{stores.filter((s) => s.isActive).length}</p>
                <p className="text-2xs text-content-muted mt-1">Ready for instant dispatch</p>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase">Avg Delivery SLA</span>
                  <div className="w-8 h-8 rounded-xl bg-surface-accentSubtle text-action-speedBg flex items-center justify-center">
                    <Clock className="w-4 h-4 text-sky-500" />
                  </div>
                </div>
                <p className="text-2xl font-black text-sky-600 mt-2">
                  {stores.length > 0 ? Math.round(stores.reduce((acc, s) => acc + (s.slaMinutes || 10), 0) / stores.length) : 8} min
                </p>
                <p className="text-2xs text-content-muted mt-1">Express fulfillment promise</p>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-border-default shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase">Coverage Radius</span>
                  <div className="w-8 h-8 rounded-xl bg-surface-subtle text-amber-500 flex items-center justify-center">
                    <Compass className="w-4 h-4 text-amber-500" />
                  </div>
                </div>
                <p className="text-2xl font-black text-amber-600 mt-2">10 - 12 km</p>
                <p className="text-2xs text-content-muted mt-1">Per store service corridor</p>
              </div>
            </div>

            {/* AUTOMATIC CUSTOMER PROXIMITY-CAPTURE SIMULATOR (KEY USER REQUIREMENT) */}
            <div className="bg-gradient-to-br from-surface-inverse to-[#1A1D26] text-white p-6 sm:p-7 rounded-3xl shadow-xl border border-border-default/20 relative overflow-hidden">
              <div className="relative z-10 space-y-5">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-action-primaryBg/20 border border-action-primaryBg/40 text-action-primaryBg text-2xs font-black uppercase tracking-wider">
                      <Crosshair className="w-3.5 h-3.5 animate-pulse" />
                      Automatic Proximity-Capture Engine
                    </div>
                    <h2 className="text-lg font-black text-white mt-1.5 flex items-center gap-2">
                      Customer Location Store-Matching Simulator
                    </h2>
                    <p className="text-xs text-slate-300">
                      When a customer inputs their location or delivery address, the system automatically captures and binds to the nearest active fulfillment store.
                    </p>
                  </div>

                  <button
                    onClick={handleUseCurrentLocationForSimulation}
                    disabled={isLocatingUser}
                    className="px-3.5 py-2 text-xs font-bold bg-white/10 hover:bg-white/20 text-white rounded-xl border border-white/20 transition-all flex items-center gap-2 self-start sm:self-auto shrink-0"
                  >
                    <Crosshair className={`w-3.5 h-3.5 ${isLocatingUser ? 'animate-spin text-sky-400' : 'text-sky-400'}`} />
                    {isLocatingUser ? 'Acquiring GPS...' : 'Use My Current Location'}
                  </button>
                </div>

                {/* Simulation Input Controls */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
                  <div>
                    <label className="block text-2xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
                      Customer Latitude
                    </label>
                    <input
                      type="number"
                      step="any"
                      value={simLat}
                      onChange={(e) => setSimLat(e.target.value)}
                      placeholder="e.g. 28.1989"
                      className="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-white text-xs font-mono focus:outline-none focus:border-action-primaryBg"
                    />
                  </div>

                  <div>
                    <label className="block text-2xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
                      Customer Longitude
                    </label>
                    <input
                      type="number"
                      step="any"
                      value={simLng}
                      onChange={(e) => setSimLng(e.target.value)}
                      placeholder="e.g. 76.6186"
                      className="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-white text-xs font-mono focus:outline-none focus:border-action-primaryBg"
                    />
                  </div>

                  <div className="flex items-end">
                    <button
                      onClick={() => handleResolveNearestStore()}
                      className="w-full py-2.5 px-4 bg-action-primaryBg hover:bg-action-primaryBg/90 text-white text-xs font-bold rounded-xl transition-all flex items-center justify-center gap-2 shadow-lg shadow-action-primaryBg/30"
                    >
                      <Navigation className="w-4 h-4" />
                      Resolve Nearest Store
                    </button>
                  </div>
                </div>

                {/* Quick Preset Chips */}
                <div className="flex flex-wrap items-center gap-2 pt-1">
                  <span className="text-2xs font-bold text-slate-400 uppercase">Quick Presets:</span>
                  {SIMULATION_PRESETS.map((p) => (
                    <button
                      key={p.name}
                      onClick={() => {
                        setSimLat(p.lat.toString());
                        setSimLng(p.lng.toString());
                        handleResolveNearestStore(p.lat, p.lng);
                      }}
                      className="px-2.5 py-1 text-2xs font-semibold rounded-lg bg-white/10 hover:bg-white/20 text-slate-200 border border-white/10 transition-colors"
                    >
                      {p.name}
                    </button>
                  ))}
                </div>

                {/* Captured Store Result Display */}
                {capturedStore && (
                  <div className="mt-4 p-5 rounded-2xl bg-white/10 border border-white/20 backdrop-blur-md">
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className={`px-2.5 py-0.5 rounded-full text-2xs font-black uppercase tracking-wider ${capturedStore.isServing ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40' : 'bg-red-500/20 text-red-300 border border-red-500/40'}`}>
                            {capturedStore.isServing ? '⚡ STORE AUTOMATICALLY CAPTURED' : '⚠️ OUT OF SERVICE CORRIDOR'}
                          </span>
                          <span className="text-2xs font-mono text-slate-300">ID: {capturedStore.storeId}</span>
                        </div>
                        <h3 className="text-base font-black text-white">{capturedStore.storeName}</h3>
                        <p className="text-xs text-slate-300 flex items-center gap-1.5">
                          <MapPin className="w-3.5 h-3.5 text-sky-400 shrink-0" />
                          {capturedStore.address}
                        </p>
                      </div>

                      <div className="flex items-center gap-3 shrink-0">
                        <div className="bg-black/30 px-4 py-2 rounded-xl border border-white/10 text-center">
                          <p className="text-2xs uppercase text-slate-400 font-bold">Trip Distance</p>
                          <p className="text-lg font-black text-sky-300">{capturedStore.distanceKm} km</p>
                        </div>
                        <div className="bg-black/30 px-4 py-2 rounded-xl border border-white/10 text-center">
                          <p className="text-2xs uppercase text-slate-400 font-bold">Delivery SLA</p>
                          <p className="text-lg font-black text-emerald-300">~{capturedStore.slaMinutes} min</p>
                        </div>
                      </div>
                    </div>

                    {simMessage && (
                      <p className="text-xs text-slate-200 mt-3 pt-3 border-t border-white/10 font-medium">
                        {simMessage}
                      </p>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* STORES LIST & MANAGEMENT */}
            <div className="bg-white rounded-2xl border border-border-default shadow-sm overflow-hidden">
              <div className="p-6 border-b border-border-default flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h2 className="text-base font-black text-content-primary">Fulfillment Stores & Dark Store Hubs</h2>
                  <p className="text-xs text-content-secondary mt-0.5">
                    Exact latitude/longitude pinpoint coordinates are used by the automated routing engine.
                  </p>
                </div>

                <div className="relative w-full sm:w-72">
                  <Search className="w-4 h-4 text-content-muted absolute left-3.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    placeholder="Search stores or addresses..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-9 pr-3.5 py-2 text-xs rounded-xl border border-border-default bg-surface-subtle text-content-primary focus:outline-none focus:border-action-primaryBg"
                  />
                </div>
              </div>

              {filteredStores.length === 0 ? (
                <div className="p-12 text-center">
                  <Store className="w-12 h-12 text-content-muted mx-auto mb-3 opacity-40" />
                  <p className="text-sm font-bold text-content-primary">No stores found</p>
                  <p className="text-xs text-content-muted mt-1">Try adjusting your search query or add a new fulfillment hub.</p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-surface-subtle text-content-muted uppercase tracking-wider font-bold text-2xs border-b border-border-default">
                      <tr>
                        <th className="py-3 px-5">Store Hub & ID</th>
                        <th className="py-3 px-5">Exact Pinpoint Location (Lat, Lng)</th>
                        <th className="py-3 px-5">Address</th>
                        <th className="py-3 px-5 text-center">SLA Target</th>
                        <th className="py-3 px-5 text-center">Radius</th>
                        <th className="py-3 px-5 text-center">Status</th>
                        <th className="py-3 px-5 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border-default">
                      {filteredStores.map((store) => {
                        const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}`;
                        return (
                          <tr key={store.id} className="hover:bg-surface-subtle/60 transition-colors">
                            <td className="py-4 px-5">
                              <div className="flex items-center space-x-3">
                                <div className="w-9 h-9 rounded-xl bg-action-primaryBg/10 text-action-primaryBg flex items-center justify-center font-black shrink-0">
                                  <Store className="w-4 h-4" />
                                </div>
                                <div>
                                  <p className="font-bold text-content-primary text-xs">{store.storeName}</p>
                                  <p className="text-2xs font-mono text-content-muted">{store.id}</p>
                                </div>
                              </div>
                            </td>

                            <td className="py-4 px-5">
                              <div className="space-y-1">
                                <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-surface-accentSubtle border border-border-accent text-content-accent font-mono text-2xs font-bold">
                                  <MapPin className="w-3 h-3 text-action-speedBg" />
                                  {Number(store.latitude || 0).toFixed(6)}, {Number(store.longitude || 0).toFixed(6)}
                                </div>
                                <div>
                                  <a
                                    href={mapsUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-2xs text-action-primaryBg hover:underline inline-flex items-center gap-1 font-semibold"
                                  >
                                    View in Maps <ExternalLink className="w-2.5 h-2.5" />
                                  </a>
                                </div>
                              </div>
                            </td>

                            <td className="py-4 px-5 max-w-xs">
                              <p className="text-content-secondary line-clamp-2 leading-relaxed text-xs">
                                {store.address}
                              </p>
                            </td>

                            <td className="py-4 px-5 text-center">
                              <span className="px-2.5 py-1 rounded-full text-2xs font-black bg-surface-subtle text-action-speedBg border border-border-default">
                                {store.slaMinutes || 10} MINS
                              </span>
                            </td>

                            <td className="py-4 px-5 text-center">
                              <span className="text-xs font-bold text-content-primary">
                                {store.serviceRadiusKm || 10} km
                              </span>
                            </td>

                            <td className="py-4 px-5 text-center">
                              <span
                                className={`px-2.5 py-0.5 rounded-full text-2xs font-black uppercase tracking-wider ${
                                  store.isActive
                                    ? 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                                    : 'bg-red-100 text-red-800 border border-red-200'
                                }`}
                              >
                                {store.isActive ? 'Active' : 'Offline'}
                              </span>
                            </td>

                            <td className="py-4 px-5 text-right space-x-2">
                              <button
                                onClick={() => openEditStoreModal(store)}
                                className="px-3 py-1.5 text-xs font-bold text-content-accent bg-surface-accentSubtle hover:bg-action-primaryBg hover:text-white rounded-lg transition-colors"
                              >
                                Edit Pinpoint
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </main>
        </div>

        {/* ADD / EDIT STORE MODAL */}
        {showAddModal && (
          <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
            <div className="bg-white rounded-3xl max-w-lg w-full p-6 shadow-2xl border border-border-default space-y-5 animate-in fade-in zoom-in-95 duration-150">
              <div className="flex items-center justify-between border-b border-border-default pb-4">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-xl bg-action-primaryBg text-white flex items-center justify-center">
                    <MapPin className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-base font-black text-content-primary">
                      {editingStore ? 'Edit Store Pinpoint & Details' : 'Add New Fulfillment Hub'}
                    </h3>
                    <p className="text-2xs text-content-muted">Authoritative GPS coordinate registry</p>
                  </div>
                </div>
                <button
                  onClick={() => setShowAddModal(false)}
                  className="p-1.5 rounded-lg text-content-muted hover:bg-surface-subtle transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {formError && (
                <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 shrink-0" />
                  <span>{formError}</span>
                </div>
              )}

              <form onSubmit={handleSaveStore} className="space-y-4 text-xs">
                <div>
                  <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                    Store / Hub Name *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Rewari Model Town Dark Store"
                    value={formStoreName}
                    onChange={(e) => setFormStoreName(e.target.value)}
                    className="w-full px-3.5 py-2 rounded-xl border border-border-default bg-surface-subtle text-content-primary focus:outline-none focus:border-action-primaryBg"
                  />
                </div>

                <div>
                  <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                    Complete Street Address *
                  </label>
                  <textarea
                    required
                    rows={2}
                    placeholder="Full street address, landmark, city, and pincode"
                    value={formAddress}
                    onChange={(e) => setFormAddress(e.target.value)}
                    className="w-full px-3.5 py-2 rounded-xl border border-border-default bg-surface-subtle text-content-primary focus:outline-none focus:border-action-primaryBg"
                  />
                </div>

                {/* Pinpoint GPS Coordinates */}
                <div className="p-4 rounded-2xl bg-surface-subtle border border-border-default space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-2xs font-black uppercase tracking-wider text-content-primary flex items-center gap-1.5">
                      <Crosshair className="w-3.5 h-3.5 text-action-primaryBg" />
                      Pinpoint GPS Coordinates *
                    </span>
                    <button
                      type="button"
                      onClick={handleUseCurrentLocationForForm}
                      disabled={isLocatingUser}
                      className="text-2xs font-bold text-action-primaryBg hover:underline flex items-center gap-1"
                    >
                      <Crosshair className={`w-3 h-3 ${isLocatingUser ? 'animate-spin' : ''}`} />
                      {isLocatingUser ? 'Acquiring...' : 'Pinpoint My Current GPS'}
                    </button>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-2xs font-bold text-content-muted mb-1">Latitude</label>
                      <input
                        type="number"
                        step="any"
                        required
                        placeholder="e.g. 28.202224"
                        value={formLatitude}
                        onChange={(e) => setFormLatitude(e.target.value)}
                        className="w-full px-3 py-2 rounded-lg border border-border-default bg-white text-content-primary font-mono text-xs focus:outline-none focus:border-action-primaryBg"
                      />
                    </div>
                    <div>
                      <label className="block text-2xs font-bold text-content-muted mb-1">Longitude</label>
                      <input
                        type="number"
                        step="any"
                        required
                        placeholder="e.g. 76.615418"
                        value={formLongitude}
                        onChange={(e) => setFormLongitude(e.target.value)}
                        className="w-full px-3 py-2 rounded-lg border border-border-default bg-white text-content-primary font-mono text-xs focus:outline-none focus:border-action-primaryBg"
                      />
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                      Delivery SLA (Minutes)
                    </label>
                    <input
                      type="number"
                      min={5}
                      max={60}
                      value={formSlaMinutes}
                      onChange={(e) => setFormSlaMinutes(e.target.value)}
                      className="w-full px-3.5 py-2 rounded-xl border border-border-default bg-surface-subtle text-content-primary focus:outline-none focus:border-action-primaryBg"
                    />
                  </div>

                  <div>
                    <label className="block text-2xs font-bold uppercase tracking-wider text-content-muted mb-1">
                      Service Radius (km)
                    </label>
                    <input
                      type="number"
                      min={1}
                      max={50}
                      value={formServiceRadiusKm}
                      onChange={(e) => setFormServiceRadiusKm(e.target.value)}
                      className="w-full px-3.5 py-2 rounded-xl border border-border-default bg-surface-subtle text-content-primary focus:outline-none focus:border-action-primaryBg"
                    />
                  </div>
                </div>

                <div className="space-y-2 pt-1">
                  <label className="flex items-center gap-2.5 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={formIsActive}
                      onChange={(e) => setFormIsActive(e.target.checked)}
                      className="w-4 h-4 rounded text-action-primaryBg focus:ring-action-primaryBg"
                    />
                    <span className="font-semibold text-content-primary">
                      Active Store (Receives live customer orders)
                    </span>
                  </label>

                  <label className="flex items-center gap-2.5 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={formSellerApproval}
                      onChange={(e) => setFormSellerApproval(e.target.checked)}
                      className="w-4 h-4 rounded text-action-primaryBg focus:ring-action-primaryBg"
                    />
                    <span className="font-semibold text-content-primary">
                      Require manual seller approval before dispatch
                    </span>
                  </label>
                </div>

                <div className="flex items-center justify-end gap-3 pt-4 border-t border-border-default">
                  <button
                    type="button"
                    onClick={() => setShowAddModal(false)}
                    className="px-4 py-2 font-bold text-content-secondary bg-surface-subtle hover:bg-surface-subtle/80 rounded-xl transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-5 py-2 font-bold text-white bg-action-primaryBg hover:bg-action-primaryBg/90 rounded-xl transition-all shadow-md shadow-action-primaryBg/20 disabled:opacity-50"
                  >
                    {isSaving ? 'Saving...' : editingStore ? 'Update Store Pinpoint' : 'Create Fulfillment Hub'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
