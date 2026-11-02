'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search,
  Zap,
  Plus,
  Minus,
  Sparkles,
  CheckCircle2,
  ChevronRight,
  Navigation,
  Pill,
  HeartPulse,
  Leaf,
  Smile,
  Shield,
} from 'lucide-react';
import {
  fetchCatalogMedicines,
  MedicineProduct,
  fetchCustomerAddresses,
  SavedAddress,
} from '@/lib/api-client';
import { useCart } from '@/lib/cart-store';
import FloatingCartBar from '@/components/cart/FloatingCartBar';
import MobileBottomNav from '@/components/navigation/MobileBottomNav';

interface Department {
  id: string;
  name: string;
  color: string;
  accent: string;
  icon: React.ElementType;
  categories: { id: string; name: string }[];
}

const DEPARTMENTS: Department[] = [
  {
    id: 'medicines',
    name: 'Medicines',
    color: '#005844',
    accent: '#DCFCE7',
    icon: Pill,
    categories: [
      { id: 'pain-fever', name: 'Pain & Fever' },
      { id: 'cough-cold', name: 'Cough & Cold' },
      { id: 'antibiotics', name: 'Antibiotics' },
      { id: 'stomach-care', name: 'Acidity & Digestion' },
      { id: 'first-aid', name: 'First Aid & Wound' },
    ],
  },
  {
    id: 'chronic',
    name: 'Chronic Care',
    color: '#0A3977',
    accent: '#DBEAFE',
    icon: HeartPulse,
    categories: [
      { id: 'diabetes', name: 'Diabetes Care' },
      { id: 'cardiac', name: 'Heart & Blood Pressure' },
      { id: 'respiratory', name: 'Asthma & Inhalers' },
      { id: 'thyroid', name: 'Thyroid Management' },
    ],
  },
  {
    id: 'wellness',
    name: 'Daily Wellness',
    color: '#8D4E00',
    accent: '#FEF3C7',
    icon: Sparkles,
    categories: [
      { id: 'vitamins', name: 'Vitamins & Supplements' },
      { id: 'calcium', name: 'Calcium & Minerals' },
      { id: 'immunity', name: 'Immunity Boosters' },
      { id: 'protein', name: 'Health Drinks & Protein' },
    ],
  },
  {
    id: 'ayurveda',
    name: 'Ayurveda',
    color: '#1E5128',
    accent: '#DCFCE7',
    icon: Leaf,
    categories: [
      { id: 'herbal-juices', name: 'Herbal Juices' },
      { id: 'chyawanprash', name: 'Chyawanprash' },
      { id: 'ashwagandha', name: 'Pure Herbs' },
      { id: 'digestive-churn', name: 'Digestive Churn' },
    ],
  },
  {
    id: 'personal',
    name: 'Personal Care',
    color: '#861948',
    accent: '#FCE7F3',
    icon: Smile,
    categories: [
      { id: 'skin-care', name: 'Dermatological Care' },
      { id: 'oral-care', name: 'Oral Care' },
      { id: 'baby-care', name: 'Baby & Infant Care' },
      { id: 'sanitizers', name: 'Antiseptics & Sanitizers' },
    ],
  },
];

export default function CategoriesHubPage() {
  const [activeDepartment, setActiveDepartment] = useState<Department>(DEPARTMENTS[0]);
  const [activeSubcategory, setActiveSubcategory] = useState<string>(DEPARTMENTS[0].categories[0].id);
  const [searchQuery, setSearchQuery] = useState('');
  const [medicines, setMedicines] = useState<MedicineProduct[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [addresses, setAddresses] = useState<SavedAddress[]>([]);

  const cartLines = useCart((s) => s.lines);
  const addToCart = useCart((s) => s.addItem);
  const updateQuantity = useCart((s) => s.updateQuantity);
  const removeItem = useCart((s) => s.removeItem);

  useEffect(() => {
    async function loadInitial() {
      try {
        const addrs = await fetchCustomerAddresses();
        if (addrs) setAddresses(addrs);
      } catch (_) {}

      try {
        setIsLoading(true);
        const data = await fetchCatalogMedicines();
        setMedicines(data || []);
      } catch (_) {
        setMedicines([]);
      } finally {
        setIsLoading(false);
      }
    }
    loadInitial();
  }, []);

  const handleSelectDepartment = (dept: Department) => {
    setActiveDepartment(dept);
    setActiveSubcategory(dept.categories[0].id);
  };

  const activeAddress = addresses.find((a) => a.isDefault) || addresses[0];
  const localityName = activeAddress?.city || activeAddress?.tag || 'Rewari';

  const getItemQuantity = (id: string, sku?: string) => {
    return cartLines.find((l) => l.productId === id || (sku && l.sku === sku))?.quantity || 0;
  };

  const handleAdd = (product: MedicineProduct) => {
    addToCart({
      sku: product.sku || product.id,
      productId: product.id,
      name: product.name,
      brand: product.brandName || activeDepartment.name,
      packSize: product.packSize || '1 Unit',
      unitPrice: product.discountedPrice || product.price,
      mrp: product.mrp || Math.round(product.price * 1.25),
      image: product.image || '',
      rxRequired: Boolean(product.rxRequirement && product.rxRequirement !== 'NONE'),
      coldChain: Boolean(product.coldChainRequired),
      expressDeliverySlaMins: product.expressDeliverySlaMins || 10,
    });
  };

  return (
    <div className="min-h-screen bg-[#F4F6F8] text-slate-900 font-sans pb-28">
      {/* 1. DYNAMIC DEPARTMENT COLOR HEADER */}
      <motion.div
        animate={{ backgroundColor: activeDepartment.color }}
        transition={{ duration: 0.3 }}
        className="text-white px-4 pt-3 pb-4 rounded-b-[24px] shadow-md sticky top-0 z-30"
      >
        <div className="max-w-4xl mx-auto flex flex-col gap-3">
          {/* Top Address & SLA Row */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Navigation className="w-4 h-4 text-white fill-white rotate-45" />
              <span className="text-xs font-black uppercase tracking-wider text-white">
                Deliver to {localityName}
              </span>
            </div>
            <div className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-white/20 text-[10px] font-black uppercase tracking-wide">
              <Zap className="w-3 h-3 text-yellow-300 fill-yellow-300" />
              10 Mins
            </div>
          </div>

          {/* Search Input with Clear Button */}
          <div className="relative w-full">
            <div className="w-full h-11 bg-white rounded-2xl flex items-center px-3.5 gap-2.5 shadow-sm text-slate-800">
              <Search className="w-4 h-4 text-slate-400 shrink-0" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={`Search in ${activeDepartment.name}...`}
                className="w-full bg-transparent text-xs font-semibold placeholder:text-slate-400 focus:outline-none"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="text-2xs font-bold text-slate-400 hover:text-slate-600 px-1"
                >
                  Clear
                </button>
              )}
            </div>
          </div>
        </div>
      </motion.div>

      {/* 2. SPLIT LAYOUT: STICKY VERTICAL RAIL + SCROLLABLE SHELF */}
      <div className="max-w-4xl mx-auto flex gap-3 px-3 pt-3">
        {/* Left Sticky Department Rail */}
        <div className="w-24 sm:w-32 flex flex-col gap-2 shrink-0">
          {DEPARTMENTS.map((dept) => {
            const isSelected = activeDepartment.id === dept.id;
            const Icon = dept.icon;

            return (
              <button
                key={dept.id}
                onClick={() => handleSelectDepartment(dept)}
                style={{
                  backgroundColor: isSelected ? `${dept.color}15` : '#FFFFFF',
                  borderColor: isSelected ? dept.color : '#E2E8F0',
                }}
                className={`p-2.5 rounded-2xl border text-center flex flex-col items-center gap-1.5 transition-all cursor-pointer shadow-2xs ${
                  isSelected ? 'border-2 scale-[1.02]' : 'hover:bg-slate-50 opacity-80'
                }`}
              >
                <div
                  style={{
                    backgroundColor: isSelected ? dept.color : '#F1F5F9',
                    color: isSelected ? '#FFFFFF' : '#475569',
                  }}
                  className="w-9 h-9 rounded-xl flex items-center justify-center transition-colors"
                >
                  <Icon className="w-4 h-4" />
                </div>
                <span
                  style={{ color: isSelected ? dept.color : '#334155' }}
                  className="text-[11px] font-extrabold leading-tight tracking-tight"
                >
                  {dept.name}
                </span>
              </button>
            );
          })}
        </div>

        {/* Right Scrollable Subcategory Chips & Product Grid */}
        <div className="flex-1 flex flex-col gap-3 min-w-0">
          {/* Subcategory Pills Row */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
            {activeDepartment.categories.map((sub) => {
              const isSelected = activeSubcategory === sub.id;
              return (
                <button
                  key={sub.id}
                  onClick={() => setActiveSubcategory(sub.id)}
                  style={{
                    backgroundColor: isSelected ? activeDepartment.color : '#FFFFFF',
                    color: isSelected ? '#FFFFFF' : '#475569',
                    borderColor: isSelected ? activeDepartment.color : '#E2E8F0',
                  }}
                  className="px-3 py-1.5 rounded-full border text-xs font-bold whitespace-nowrap shrink-0 transition-all cursor-pointer shadow-2xs"
                >
                  {sub.name}
                </button>
              );
            })}
          </div>

          {/* Product Items Shelf */}
          <div className="flex flex-col gap-2.5">
            {isLoading ? (
              <div className="flex flex-col gap-2.5">
                {[1, 2, 3, 4].map((n) => (
                  <div key={n} className="bg-white rounded-2xl p-3 h-24 animate-pulse border border-slate-200/60" />
                ))}
              </div>
            ) : medicines.length === 0 ? (
              <div className="bg-white rounded-2xl p-8 text-center border border-slate-200">
                <p className="text-xs font-bold text-slate-500">No products found in this category.</p>
              </div>
            ) : (
              medicines.map((med) => {
                const qty = getItemQuantity(med.id, med.sku);
                const discount = med.discountPercentage || 20;

                return (
                  <div
                    key={med.id}
                    className="bg-white rounded-2xl p-3 border border-slate-100 shadow-xs flex items-center justify-between gap-3 hover:shadow-sm transition-all"
                  >
                    {/* Thumbnail & Details */}
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-16 h-16 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-center shrink-0 overflow-hidden relative">
                        {med.image ? (
                          <img src={med.image} alt={med.name} className="w-full h-full object-contain p-1" />
                        ) : (
                          <Pill className="w-6 h-6 text-slate-300" />
                        )}
                        <span className="absolute top-1 left-1 px-1 rounded bg-emerald-600 text-white text-[8px] font-black">
                          {discount}% OFF
                        </span>
                      </div>

                      <div className="flex flex-col min-w-0">
                        <span className="text-xs font-extrabold text-slate-900 truncate">
                          {med.name}
                        </span>
                        <span className="text-[10px] text-slate-500 truncate">
                          {med.packSize || '10 Tablets'} • {med.brandName || activeDepartment.name}
                        </span>

                        <div className="flex items-center gap-1.5 mt-1.5">
                          <span className="text-sm font-black text-slate-900">
                            ₹{(med.discountedPrice || med.price).toFixed(0)}
                          </span>
                          {med.mrp && (
                            <span className="text-2xs text-slate-400 line-through">
                              ₹{med.mrp.toFixed(0)}
                            </span>
                          )}
                          <span className="text-[9px] font-extrabold text-emerald-700 bg-emerald-50 px-1 rounded">
                            ⚡ 10m
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Quantity Add Button */}
                    <div className="shrink-0">
                      {qty === 0 ? (
                        <button
                          onClick={() => handleAdd(med)}
                          className="px-4 py-1.5 rounded-xl border border-emerald-600 text-emerald-700 font-extrabold text-xs hover:bg-emerald-50 active:scale-95 transition-all cursor-pointer shadow-2xs"
                        >
                          ADD
                        </button>
                      ) : (
                        <div className="flex items-center gap-2 bg-emerald-700 text-white rounded-xl px-2 py-1 shadow-sm">
                          <button
                            onClick={() => {
                              const sku = med.sku || med.id;
                              if (qty === 1) removeItem(sku);
                              else updateQuantity(sku, qty - 1);
                            }}
                            className="w-5 h-5 flex items-center justify-center active:scale-90"
                          >
                            <Minus className="w-3.5 h-3.5" />
                          </button>
                          <span className="text-xs font-black">{qty}</span>
                          <button
                            onClick={() => updateQuantity(med.sku || med.id, qty + 1)}
                            className="w-5 h-5 flex items-center justify-center active:scale-90"
                          >
                            <Plus className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>

      {/* Floating Bottom Cart Bar */}
      <FloatingCartBar />

      {/* Mobile Bottom Navigation */}
      <MobileBottomNav />
    </div>
  );
}
