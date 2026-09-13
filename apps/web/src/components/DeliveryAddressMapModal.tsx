'use client';

import React, { useState, useEffect } from 'react';
import {
  MapPin, Search, Navigation, X, Check, Home, Briefcase, Heart,
  Sparkles, BellOff, DoorOpen, Shield, PhoneCall, Dog, Layers, Plus, Minus
} from 'lucide-react';
import { SavedAddress, createCustomerAddress, setDefaultCustomerAddress } from '@/lib/api-client';

interface DeliveryAddressMapModalProps {
  isOpen: boolean;
  onClose: () => void;
  savedAddresses: SavedAddress[];
  currentAddress: SavedAddress | null;
  onSelectAddress: (address: SavedAddress) => void;
  onAddressSaved?: () => void;
}

const PRESET_LOCATIONS = [
  { name: 'Sector 18, Panipat', lat: 29.3909, lng: 76.9635, area: 'Sector 18, Urban Estate', city: 'Panipat', postalCode: '132103', eta: '8-12 mins' },
  { name: 'Tech Park Phase II, Panipat', lat: 29.3980, lng: 76.9720, area: 'Plot 881, Tech Park Phase II', city: 'Panipat', postalCode: '132103', eta: '10-15 mins' },
  { name: 'Green Park Colony, Panipat', lat: 29.3820, lng: 76.9550, area: 'House 14B, Green Park Colony', city: 'Panipat', postalCode: '132104', eta: '12-18 mins' },
  { name: 'Model Town, Panipat', lat: 29.3950, lng: 76.9690, area: 'Model Town Main Market', city: 'Panipat', postalCode: '132103', eta: '6-10 mins' },
  { name: 'DLF Cyber City, Gurgaon', lat: 28.4950, lng: 77.0890, area: 'Building 10, DLF Phase 2', city: 'Gurgaon', postalCode: '122002', eta: '15-22 mins' },
];

const INSTRUCTION_OPTIONS = [
  { id: 'no_bell', label: 'Don\'t ring bell', icon: BellOff },
  { id: 'leave_door', label: 'Leave at door', icon: DoorOpen },
  { id: 'leave_guard', label: 'Leave with guard', icon: Shield },
  { id: 'call_before', label: 'Call before arriving', icon: PhoneCall },
  { id: 'pets', label: 'Beware of pets', icon: Dog },
];

export default function DeliveryAddressMapModal({
  isOpen,
  onClose,
  savedAddresses,
  currentAddress,
  onSelectAddress,
  onAddressSaved,
}: DeliveryAddressMapModalProps) {
  const [activeTab, setActiveTab] = useState<'map' | 'form' | 'saved'>('map');
  const [mapPos, setMapPos] = useState({ lat: 29.3909, lng: 76.9635 });
  const [mapZoom, setMapZoom] = useState(15);
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [searchQuery, setSearchQuery] = useState('');
  const [showSearchResults, setShowSearchResults] = useState(false);
  const [isGeolocating, setIsGeolocating] = useState(false);

  // Address Form State
  const [tag, setTag] = useState<'Home' | 'Work' | 'Parents' | 'Other'>('Home');
  const [flatNo, setFlatNo] = useState('');
  const [buildingName, setBuildingName] = useState('');
  const [landmark, setLandmark] = useState('');
  const [contactName, setContactName] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [selectedInstructions, setSelectedInstructions] = useState<string[]>(['no_bell']);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [mapStyle, setMapStyle] = useState<'street' | 'dark' | 'satellite'>('street');

  // Dynamic geocoded address based on map position
  const [geocodedArea, setGeocodedArea] = useState(
    currentAddress?.addressLine || 'Select location on map'
  );

  useEffect(() => {
    if (currentAddress) {
      if (currentAddress.latitude && currentAddress.longitude) {
        setMapPos({ lat: currentAddress.latitude, lng: currentAddress.longitude });
      }
      if (currentAddress.tag as any) {
        setTag(currentAddress.tag as any);
      }
    }
  }, [currentAddress]);

  if (!isOpen) return null;

  // Handle map panning simulation
  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true);
    setDragStart({ x: e.clientX, y: e.clientY });
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    const dx = e.clientX - dragStart.x;
    const dy = e.clientY - dragStart.y;
    setDragStart({ x: e.clientX, y: e.clientY });
    
    // Convert drag pixel deltas to simulated lat/lng shifts
    const factor = 0.00005 * (16 / mapZoom);
    setMapPos((prev) => ({
      lat: prev.lat - dy * factor,
      lng: prev.lng - dx * factor,
    }));
  };

  const handleMouseUp = () => {
    if (isDragging) {
      setIsDragging(false);
      // Simulate reverse geocoding update after drag finishes
      const nearest = PRESET_LOCATIONS[Math.floor(Math.abs(mapPos.lat * 100) % PRESET_LOCATIONS.length)];
      setGeocodedArea(`${nearest.area}, ${nearest.city}, ${nearest.postalCode}`);
    }
  };

  const handleLocateMe = () => {
    setIsGeolocating(true);
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const lat = pos.coords.latitude;
          const lng = pos.coords.longitude;
          setMapPos({ lat, lng });
          const nearest = PRESET_LOCATIONS[Math.floor(Math.abs(lat * 100) % PRESET_LOCATIONS.length)];
          setGeocodedArea(`${nearest.area}, ${nearest.city}`);
          setIsGeolocating(false);
        },
        () => {
          const defaultLoc = PRESET_LOCATIONS[0];
          setMapPos({ lat: defaultLoc.lat, lng: defaultLoc.lng });
          setGeocodedArea(`${defaultLoc.area}, ${defaultLoc.city}, ${defaultLoc.postalCode}`);
          setIsGeolocating(false);
        },
        { timeout: 3000 }
      );
    } else {
      const defaultLoc = PRESET_LOCATIONS[0];
      setMapPos({ lat: defaultLoc.lat, lng: defaultLoc.lng });
      setGeocodedArea(`${defaultLoc.area}, ${defaultLoc.city}, ${defaultLoc.postalCode}`);
      setIsGeolocating(false);
    }
  };

  const handleSelectPreset = (preset: typeof PRESET_LOCATIONS[0]) => {
    setMapPos({ lat: preset.lat, lng: preset.lng });
    setGeocodedArea(`${preset.area}, ${preset.city}, ${preset.postalCode}`);
    setSearchQuery(preset.name);
    setShowSearchResults(false);
  };

  const toggleInstruction = (id: string) => {
    setSelectedInstructions((prev) =>
      prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id]
    );
  };

  const handleSaveAddress = async () => {
    setIsSubmitting(true);
    try {
      const addressLine = [flatNo, buildingName].filter(Boolean).join(', ') || geocodedArea.split(',')[0];
      const newAddress = await createCustomerAddress({
        tag,
        addressLine,
        city: 'Panipat',
        state: 'Haryana',
        postalCode: '132103',
        landmark,
        contactName,
        contactPhone,
        isDefault: true,
        latitude: mapPos.lat,
        longitude: mapPos.lng,
        deliveryInstructions: selectedInstructions.join(','),
      });

      onAddressSaved();
      onSelectAddress(newAddress);
      onClose();
    } catch (err) {
      console.error('Failed to save address:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-surface-inverse/80 backdrop-blur-md transition-opacity animate-in fade-in duration-200">
      <div className="bg-white rounded-3xl shadow-2xl w-full max-w-4xl max-h-[92vh] overflow-hidden flex flex-col md:flex-row border border-border-default">
        
        {/* LEFT COLUMN: INTERACTIVE BLINKIT/ZOMATO MAP VIEWPORT */}
        <div className="relative flex-1 bg-surface-subtle min-h-[360px] md:min-h-[540px] flex flex-col overflow-hidden select-none">
          
          {/* TOP SEARCH OVERLAY */}
          <div className="absolute top-4 left-4 right-4 z-20 flex flex-col space-y-2">
            <div className="relative flex items-center bg-white/95 backdrop-blur-md rounded-2xl shadow-lg border border-border-default px-3.5 py-2.5">
              <Search className="w-4 h-4 text-content-accent shrink-0 mr-2.5" />
              <input
                type="text"
                placeholder="Search area, apartment, street, landmark..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setShowSearchResults(true);
                }}
                onFocus={() => setShowSearchResults(true)}
                className="w-full text-xs font-semibold text-content-primary placeholder:text-content-muted bg-transparent focus:outline-none"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="p-1 text-content-muted hover:text-content-secondary rounded-full"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
              <button
                onClick={handleLocateMe}
                disabled={isGeolocating}
                className="ml-2 flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-surface-accentSubtle text-content-accent text-2xs font-bold hover:bg-surface-accentSubtle transition-all border border-border-accent shrink-0"
              >
                <Navigation className={`w-3.5 h-3.5 ${isGeolocating ? 'animate-spin' : ''}`} />
                <span>Locate</span>
              </button>
            </div>

            {/* AUTOSEARCH DROPDOWN SUGGESTIONS */}
            {showSearchResults && searchQuery && (
              <div className="bg-white rounded-2xl shadow-xl border border-border-default p-2 max-h-56 overflow-y-auto animate-in slide-in-from-top-2 duration-150">
                {PRESET_LOCATIONS.filter((p) => p.name.toLowerCase().includes(searchQuery.toLowerCase())).map((preset) => (
                  <button
                    key={preset.name}
                    onClick={() => handleSelectPreset(preset)}
                    className="w-full text-left px-3.5 py-2.5 rounded-xl hover:bg-surface-subtle transition-all flex items-start space-x-3"
                  >
                    <MapPin className="w-4 h-4 text-content-brand mt-0.5 shrink-0" />
                    <div>
                      <div className="text-xs font-bold text-content-primary">{preset.name}</div>
                      <div className="text-2xs text-content-secondary font-medium">{preset.area}</div>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* MAP CANVAS (Interactive Drag & Styled Tiles) */}
          <div
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            className={`w-full h-full relative cursor-grab active:cursor-grabbing overflow-hidden ${
              mapStyle === 'dark' ? 'bg-surface-inverse' : mapStyle === 'satellite' ? 'bg-navy-900' : 'bg-surface-subtle'
            }`}
          >
            {/* SIMULATED MAP ROADS & DARK STORE POLYGON */}
            {/* commerce-os:allow-vector-color */}
            <svg className="absolute inset-0 w-full h-full opacity-30 pointer-events-none">
              <defs>
                <pattern id="mapGrid" width="64" height="64" patternUnits="userSpaceOnUse">
                  <path d="M 64 0 L 0 0 0 64" fill="none" stroke={mapStyle === 'dark' ? '#1E293B' : '#E2E8F0'} strokeWidth="1" />
                </pattern>
              </defs>
              <rect width="100%" height="100%" fill="url(#mapGrid)" />
              <circle cx="50%" cy="50%" r="180" fill="#10B981" fillOpacity="0.08" stroke="#10B981" strokeDasharray="6 4" strokeWidth="2" />
              <path d="M 0 120 Q 200 180 400 150 T 800 300" fill="none" stroke={mapStyle === 'dark' ? '#38BDF8' : '#94A3B8'} strokeWidth="12" />
              <path d="M 150 0 Q 180 300 250 600" fill="none" stroke={mapStyle === 'dark' ? '#38BDF8' : '#CBD5E1'} strokeWidth="8" />
              <path d="M 300 50 L 500 500" fill="none" stroke={mapStyle === 'dark' ? '#F43F5E' : '#E2E8F0'} strokeWidth="6" />
            </svg>

            {/* BLINKIT / ZOMATO STYLE CENTER PIN WITH FLOATING CARD */}
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-10">
              
              {/* Floating Address Tag Bubble above the pin */}
              <div className="absolute -translate-y-24 flex flex-col items-center animate-bounce duration-1000">
                <div className="bg-surface-inverse text-white px-3.5 py-2 rounded-2xl shadow-2xl flex items-center space-x-2 border border-border-strong">
                  <span className="flex h-2 w-2 relative">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-action-primaryBg opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-action-primaryBg"></span>
                  </span>
                  <div className="flex flex-col">
                    <span className="text-2xs font-black tracking-wider text-content-brand uppercase">⚡ 8–15 MINS SLA</span>
                    <span className="text-2xs font-bold max-w-[200px] truncate">{geocodedArea.split(',')[0]}</span>
                  </div>
                </div>
                {/* Tail arrow */}
                <div className="w-2.5 h-2.5 bg-surface-inverse rotate-45 -mt-1 border-r border-b border-border-strong" />
              </div>

              {/* Pin Radar Wave Ripple */}
              <div className="absolute w-16 h-16 bg-surface-brandSubtle rounded-full animate-ping pointer-events-none" />
              <div className="absolute w-6 h-3 bg-surface-inverse/80 rounded-full blur-xs translate-y-7 pointer-events-none" />

              {/* Pin Icon */}
              <div className="relative -translate-y-6 flex items-center justify-center">
                <div className="w-11 h-11 bg-action-primaryBg rounded-full flex items-center justify-center text-white shadow-xl ring-4 ring-white">
                  <MapPin className="w-6 h-6 fill-white stroke-content-brand" />
                </div>
              </div>
            </div>

            {/* MAP ZOOM & CONTROLS */}
            <div className="absolute bottom-4 right-4 z-20 flex flex-col space-y-1.5">
              <button
                onClick={() => setMapZoom((z) => Math.min(z + 1, 18))}
                className="w-8 h-8 rounded-xl bg-white/95 text-content-secondary font-bold shadow-md hover:bg-surface-subtle flex items-center justify-center text-sm border border-border-default"
              >
                <Plus className="w-4 h-4" />
              </button>
              <button
                onClick={() => setMapZoom((z) => Math.max(z - 1, 12))}
                className="w-8 h-8 rounded-xl bg-white/95 text-content-secondary font-bold shadow-md hover:bg-surface-subtle flex items-center justify-center text-sm border border-border-default"
              >
                <Minus className="w-4 h-4" />
              </button>
              <button
                onClick={() => setMapStyle((s) => (s === 'street' ? 'dark' : s === 'dark' ? 'satellite' : 'street'))}
                className="w-8 h-8 rounded-xl bg-white/95 text-content-secondary shadow-md hover:bg-surface-subtle flex items-center justify-center border border-border-default mt-1"
                title="Toggle Map Style"
              >
                <Layers className="w-4 h-4 text-content-accent" />
              </button>
            </div>

            {/* DARK STORE COVERAGE SLA RIBBON */}
            <div className="absolute bottom-4 left-4 z-20 bg-surface-inverse text-content-brand backdrop-blur-md px-3.5 py-1.5 rounded-xl shadow-lg border border-border-brand flex items-center space-x-2 text-2xs font-semibold">
              <Sparkles className="w-3.5 h-3.5 text-content-brand animate-pulse" />
              <span>Instant 10-Min <strong>Verified Delivery Coverage</strong></span>
            </div>
          </div>
        </div>

        {/* RIGHT COLUMN: SAVED ADDRESSES & HOUSE DETAILS FORM */}
        <div className="w-full md:w-[420px] bg-white flex flex-col justify-between p-5 border-l border-border-subtle overflow-y-auto max-h-[500px] md:max-h-[540px]">
          <div>
            {/* MODAL HEADER */}
            <div className="flex items-center justify-between pb-4 border-b border-border-subtle">
              <div>
                <span className="text-2xs font-bold uppercase tracking-wider text-content-brand bg-surface-brandSubtle px-2 py-0.5 rounded border border-border-brandSubtle">
                  Instant Serviceability
                </span>
                <h3 className="text-base font-extrabold text-content-primary mt-0.5">Select Delivery Location</h3>
              </div>
              <button
                onClick={onClose}
                className="p-2 text-content-muted hover:text-content-secondary hover:bg-surface-subtle rounded-full transition-all"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* TAB SELECTOR: Map Pin vs Saved Addresses */}
            <div className="flex bg-surface-subtle p-1 rounded-2xl my-4 text-xs font-bold">
              <button
                onClick={() => setActiveTab('map')}
                className={`flex-1 py-2 rounded-xl transition-all ${
                  activeTab === 'map' ? 'bg-white text-content-accent shadow-sm' : 'text-content-secondary hover:text-content-primary'
                }`}
              >
                1. Set Pin Location
              </button>
              <button
                onClick={() => setActiveTab('saved')}
                className={`flex-1 py-2 rounded-xl transition-all ${
                  activeTab === 'saved' ? 'bg-white text-content-accent shadow-sm' : 'text-content-secondary hover:text-content-primary'
                }`}
              >
                2. Saved Addresses ({savedAddresses.length})
              </button>
            </div>

            {/* TAB CONTENT 1: MAP PIN SUMMARY & ADDRESS FORM */}
            {(activeTab === 'map' || activeTab === 'form') && (
              <div className="space-y-4 animate-in fade-in duration-150">
                {/* Location banner */}
                <div className="p-3.5 bg-surface-subtle rounded-2xl border border-border-default flex items-start space-x-3">
                  <div className="w-8 h-8 rounded-xl bg-surface-brandSubtle text-content-brand flex items-center justify-center shrink-0 mt-0.5">
                    <MapPin className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="text-xs font-extrabold text-content-primary">{geocodedArea.split(',')[0]}</div>
                    <div className="text-2xs text-content-secondary font-medium leading-tight mt-0.5">{geocodedArea}</div>
                  </div>
                </div>

                {/* ADDRESS CATEGORY TAG BUTTONS */}
                <div>
                  <label className="text-2xs font-bold text-content-secondary uppercase tracking-wider block mb-2">Save Address As</label>
                  <div className="grid grid-cols-4 gap-2">
                    {[
                      { id: 'Home', label: 'Home', icon: Home },
                      { id: 'Work', label: 'Work', icon: Briefcase },
                      { id: 'Parents', label: 'Parents', icon: Heart },
                      { id: 'Other', label: 'Other', icon: MapPin },
                    ].map((t) => {
                      const Icon = t.icon;
                      const active = tag === t.id;
                      return (
                        <button
                          key={t.id}
                          type="button"
                          onClick={() => setTag(t.id as any)}
                          className={`flex flex-col items-center justify-center py-2.5 rounded-2xl border text-xs font-bold transition-all ${
                            active
                              ? 'bg-surface-accentSubtle border-border-accent text-content-accent ring-2 ring-border-accent/20'
                              : 'bg-white border-border-default text-content-secondary hover:bg-surface-subtle'
                          }`}
                        >
                          <Icon className="w-4 h-4 mb-1" />
                          <span>{t.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* HOUSE & BUILDING INPUTS */}
                <div className="grid grid-cols-2 gap-2.5">
                  <div>
                    <label className="text-2xs font-bold text-content-secondary block mb-1">House / Flat / Floor *</label>
                    <input
                      type="text"
                      placeholder="e.g. Flat 402, 4th Floor"
                      value={flatNo}
                      onChange={(e) => setFlatNo(e.target.value)}
                      className="w-full text-xs font-semibold px-3 py-2.5 rounded-xl border border-border-default focus:outline-none focus:ring-2 focus:ring-border-focus"
                    />
                  </div>
                  <div>
                    <label className="text-2xs font-bold text-content-secondary block mb-1">Apartment / Building</label>
                    <input
                      type="text"
                      placeholder="e.g. Skyline Towers"
                      value={buildingName}
                      onChange={(e) => setBuildingName(e.target.value)}
                      className="w-full text-xs font-semibold px-3 py-2.5 rounded-xl border border-border-default focus:outline-none focus:ring-2 focus:ring-border-focus"
                    />
                  </div>
                </div>

                {/* LANDMARK */}
                <div>
                  <label className="text-2xs font-bold text-content-secondary block mb-1">Nearby Landmark (Optional)</label>
                  <input
                    type="text"
                    placeholder="e.g. Opposite City Hospital Gate 2"
                    value={landmark}
                    onChange={(e) => setLandmark(e.target.value)}
                    className="w-full text-xs font-semibold px-3 py-2.5 rounded-xl border border-border-default focus:outline-none focus:ring-2 focus:ring-border-focus"
                  />
                </div>

                {/* BLINKIT/ZOMATO DELIVERY INSTRUCTION CHIPS */}
                <div>
                  <label className="text-2xs font-bold text-content-secondary uppercase tracking-wider block mb-2">Delivery Instructions</label>
                  <div className="flex flex-wrap gap-1.5">
                    {INSTRUCTION_OPTIONS.map((inst) => {
                      const Icon = inst.icon;
                      const active = selectedInstructions.includes(inst.id);
                      return (
                        <button
                          key={inst.id}
                          type="button"
                          onClick={() => toggleInstruction(inst.id)}
                          className={`inline-flex items-center space-x-1.5 px-2.5 py-1.5 rounded-xl border text-2xs font-semibold transition-all ${
                            active
                              ? 'bg-surface-brandSubtle border-border-brand text-content-brand font-bold ring-1 ring-border-brand'
                              : 'bg-surface-subtle border-border-default text-content-secondary hover:bg-surface-subtle'
                          }`}
                        >
                          <Icon className="w-3.5 h-3.5" />
                          <span>{inst.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>
            )}

            {/* TAB CONTENT 2: SAVED ADDRESSES QUICK LIST */}
            {activeTab === 'saved' && (
              <div className="space-y-2.5 animate-in fade-in duration-150">
                {savedAddresses.length === 0 ? (
                  <div className="text-center py-8 text-content-muted text-xs font-medium">
                    No saved addresses found. Set pin on map to add one.
                  </div>
                ) : (
                  savedAddresses.map((addr) => {
                    const isSelected = currentAddress?.id === addr.id;
                    return (
                      <div
                        key={addr.id}
                        onClick={async () => {
                          await setDefaultCustomerAddress(addr.id);
                          onSelectAddress(addr);
                          onClose();
                        }}
                        className={`p-3.5 rounded-2xl border cursor-pointer transition-all flex items-start justify-between ${
                          isSelected
                            ? 'bg-surface-accentSubtle border-border-accent ring-2 ring-border-accent/20'
                            : 'bg-white border-border-default hover:border-border-strong hover:bg-surface-subtle'
                        }`}
                      >
                        <div className="flex items-start space-x-3">
                          <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 mt-0.5 ${
                            isSelected ? 'bg-action-speedBg text-white' : 'bg-surface-subtle text-content-secondary'
                          }`}>
                            {addr.tag === 'Work' ? <Briefcase className="w-4 h-4" /> : <Home className="w-4 h-4" />}
                          </div>
                          <div>
                            <div className="flex items-center space-x-2">
                              <span className="text-xs font-extrabold text-content-primary">{addr.tag}</span>
                              {addr.isDefault && (
                                <span className="px-1.5 py-0.5 text-2xs font-extrabold bg-surface-muted text-content-secondary rounded">DEFAULT</span>
                              )}
                            </div>
                            <div className="text-2xs font-medium text-content-secondary mt-0.5 line-clamp-1">{addr.addressLine}</div>
                            <div className="text-2xs text-content-muted mt-0.5">{addr.city}, {addr.postalCode}</div>
                          </div>
                        </div>
                        {isSelected && <Check className="w-4 h-4 text-content-accent mt-1 shrink-0" />}
                      </div>
                    );
                  })
                )}
              </div>
            )}
          </div>

          {/* ACTION BUTTON FOOTER */}
          <div className="pt-4 border-t border-border-subtle mt-4">
            {activeTab !== 'saved' ? (
              <button
                onClick={handleSaveAddress}
                disabled={isSubmitting}
                className="w-full py-3.5 px-4 rounded-2xl bg-action-primaryBg hover:bg-action-primaryHover text-white font-extrabold text-xs shadow-lg transition-all flex items-center justify-center space-x-2"
              >
                {isSubmitting ? (
                  <span>Saving location...</span>
                ) : (
                  <>
                    <Check className="w-4 h-4" />
                    <span>Save Address & Deliver Here</span>
                  </>
                )}
              </button>
            ) : (
              <button
                onClick={() => setActiveTab('map')}
                className="w-full py-3 px-4 rounded-2xl bg-surface-inverse text-white font-extrabold text-xs hover:bg-surface-inverse transition-all flex items-center justify-center space-x-2"
              >
                <Plus className="w-4 h-4" />
                <span>Add New Address on Map</span>
              </button>
            )}
          </div>

        </div>
      </div>
    </div>
  );
}
