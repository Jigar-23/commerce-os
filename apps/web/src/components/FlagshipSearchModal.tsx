'use client';

import React, { useState, useEffect, useRef } from 'react';
import {
  Search,
  X,
  Clock,
  TrendingUp,
  Sparkles,
  ArrowRight,
  ShoppingBag,
  ShieldCheck,
  ThermometerSnowflake,
  History,
  Trash2,
} from 'lucide-react';
import { MedicineProduct } from '@/lib/api-client';
import { useCart } from '@/lib/cart-store';
import { CommerceQuantityControl } from '@commerce-os/ui';

interface FlagshipSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  products: MedicineProduct[];
  onSelectProduct?: (product: MedicineProduct) => void;
}

const POPULAR_SEARCHES = [
  'Paracetamol 650',
  'Augmentin 625',
  'Vitamin C & Zinc',
  'Diabetes Care',
  'Dolo 650',
  'ORS Electrolytes',
  'Cetirizine 10mg',
  'Bandages & First Aid',
];

const RECENT_SEARCHES_KEY = 'commerce_os_recent_searches';

export default function FlagshipSearchModal({
  isOpen,
  onClose,
  products,
  onSelectProduct,
}: FlagshipSearchModalProps) {
  const [query, setQuery] = useState('');
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);

  const cartLines = useCart((s) => s.lines);
  const addToCart = useCart((s) => s.addItem);
  const updateQuantity = useCart((s) => s.updateQuantity);
  const removeItem = useCart((s) => s.removeItem);

  // Load recent searches from localStorage on mount
  useEffect(() => {
    try {
      const saved = localStorage.getItem(RECENT_SEARCHES_KEY);
      if (saved) {
        setRecentSearches(JSON.parse(saved));
      }
    } catch {
      // ignore
    }
  }, []);

  // Auto focus input when opened
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setQuery('');
    }
  }, [isOpen]);

  // Handle ESC key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const saveRecentSearch = (term: string) => {
    const trimmed = term.trim();
    if (!trimmed) return;
    const updated = [trimmed, ...recentSearches.filter((s) => s.toLowerCase() !== trimmed.toLowerCase())].slice(0, 6);
    setRecentSearches(updated);
    try {
      localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(updated));
    } catch {
      // ignore
    }
  };

  const clearRecentSearches = () => {
    setRecentSearches([]);
    try {
      localStorage.removeItem(RECENT_SEARCHES_KEY);
    } catch {
      // ignore
    }
  };

  const filteredProducts = query.trim()
    ? products.filter((p) => {
        const q = query.toLowerCase();
        return (
          p.name.toLowerCase().includes(q) ||
          (p.brandName && p.brandName.toLowerCase().includes(q)) ||
          (p.therapeuticCategory && p.therapeuticCategory.toLowerCase().includes(q)) ||
          (p.packSize && p.packSize.toLowerCase().includes(q))
        );
      })
    : [];

  const handleAddToCart = (product: MedicineProduct) => {
    saveRecentSearch(product.name);
    const sku = product.sku || product.id;
    addToCart({
      sku,
      productId: product.id,
      name: product.name,
      brand: product.brandName || product.therapeuticCategory || 'HEALTH',
      packSize: product.packSize || '',
      unitPrice: product.discountedPrice || product.price,
      mrp: product.mrp || product.price * 1.25,
      image: product.image || '',
      rxRequired: Boolean(product.rxRequirement && product.rxRequirement !== 'NONE' && product.rxRequirement !== 'OTC'),
      coldChain: Boolean(product.coldChainRequired),
      expressDeliverySlaMins: product.expressDeliverySlaMins || 10,
    });
  };

  const getItemQuantity = (product: MedicineProduct) => {
    const sku = product.sku || product.id;
    return cartLines.find((l) => l.sku === sku || l.productId === product.id)?.quantity || 0;
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden flex items-start justify-center pt-12 sm:pt-20 p-4 select-none">
      {/* BACKDROP */}
      <div
        onClick={onClose}
        className="fixed inset-0 bg-surface-inverse/70 backdrop-blur-sm transition-opacity"
      />

      {/* SEARCH CONTAINER */}
      <div className="relative w-full max-w-2xl bg-surface-card rounded-3xl shadow-modal border border-border-default overflow-hidden flex flex-col max-h-[85vh] z-10 animate-scale-in">
        {/* TOP SEARCH INPUT BAR */}
        <div className="p-4 sm:p-5 border-b border-border-subtle flex items-center gap-3 bg-surface-subtle">
          <div className="w-10 h-10 rounded-xl bg-surface-brandSubtle flex items-center justify-center text-content-brand shrink-0">
            <Search className="h-5 w-5" />
          </div>

          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && query.trim()) {
                saveRecentSearch(query);
              }
            }}
            placeholder="Search medicines, brands, salt compositions..."
            className="w-full bg-transparent text-sm sm:text-base font-bold text-content-primary placeholder:text-content-muted outline-none"
          />

          {query && (
            <button
              onClick={() => setQuery('')}
              className="p-1.5 rounded-full text-content-muted hover:text-content-primary hover:bg-surface-muted transition-colors cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          )}

          <button
            onClick={onClose}
            className="px-2.5 py-1 rounded-lg bg-surface-muted hover:bg-surface-subtle text-content-secondary text-xs font-bold transition-colors cursor-pointer shrink-0 border border-border-subtle"
          >
            ESC
          </button>
        </div>

        {/* CONTENT AREA */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-6">
          {/* STATE 1: IDLE / NO QUERY -> SHOW RECENT & POPULAR SEARCHES */}
          {!query.trim() && (
            <div className="space-y-6">
              {/* RECENT SEARCHES */}
              {recentSearches.length > 0 && (
                <div className="space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-extrabold text-content-muted uppercase tracking-wider flex items-center gap-1.5">
                      <History className="h-3.5 w-3.5" />
                      Recent Searches
                    </span>
                    <button
                      onClick={clearRecentSearches}
                      className="text-2xs font-bold text-content-muted hover:text-content-danger transition-colors cursor-pointer flex items-center gap-1"
                    >
                      <Trash2 className="h-3 w-3" />
                      Clear
                    </button>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {recentSearches.map((term) => (
                      <button
                        key={term}
                        onClick={() => {
                          setQuery(term);
                          saveRecentSearch(term);
                        }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-surface-subtle hover:bg-surface-brandSubtle text-content-primary hover:text-content-brand text-xs font-bold border border-border-subtle transition-all cursor-pointer"
                      >
                        <Clock className="h-3 w-3 text-content-muted" />
                        <span>{term}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* POPULAR SEARCHES */}
              <div className="space-y-2.5">
                <span className="text-xs font-extrabold text-content-muted uppercase tracking-wider flex items-center gap-1.5">
                  <TrendingUp className="h-3.5 w-3.5 text-content-brand" />
                  Trending Health Essentials
                </span>

                <div className="flex flex-wrap gap-2">
                  {POPULAR_SEARCHES.map((term) => (
                    <button
                      key={term}
                      onClick={() => {
                        setQuery(term);
                        saveRecentSearch(term);
                      }}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-surface-subtle hover:bg-surface-brandSubtle text-content-primary hover:text-content-brand text-xs font-bold border border-border-subtle transition-all cursor-pointer"
                    >
                      <Sparkles className="h-3 w-3 text-content-brand" />
                      <span>{term}</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* SERVICE PROMISE BANNER */}
              <div className="p-4 rounded-2xl bg-surface-brandSubtle border border-border-brandSubtle flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-action-primaryBg text-action-primaryText flex items-center justify-center font-black">
                    ⚡
                  </div>
                  <div>
                    <h4 className="text-xs font-extrabold text-content-primary">10-Minute Dark Store Dispatch</h4>
                    <p className="text-2xs text-content-secondary">Temperature controlled & verified by licensed pharmacists</p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* STATE 2: SEARCH RESULTS */}
          {query.trim() && (
            <div className="space-y-3">
              <div className="flex items-center justify-between text-xs font-bold text-content-secondary">
                <span>{filteredProducts.length} items found for &quot;{query}&quot;</span>
                <span className="text-2xs text-content-brand uppercase font-extrabold">⚡ 10-Min Delivery</span>
              </div>

              {filteredProducts.length === 0 ? (
                <div className="py-12 text-center space-y-3">
                  <div className="w-14 h-14 rounded-2xl bg-surface-subtle flex items-center justify-center text-content-muted mx-auto">
                    <Search className="h-6 w-6" />
                  </div>
                  <h4 className="text-sm font-extrabold text-content-primary">No products matching &quot;{query}&quot;</h4>
                  <p className="text-xs text-content-secondary max-w-sm mx-auto leading-relaxed">
                    Try searching by generic salt composition (e.g. <em>Paracetamol</em>, <em>Amoxicillin</em>) or symptom.
                  </p>
                </div>
              ) : (
                <div className="divide-y divide-border-subtle">
                  {filteredProducts.map((product) => {
                    const price = product.discountedPrice || product.price;
                    const mrp = product.mrp || Math.round(price * 1.25);
                    const qty = getItemQuantity(product);
                    const isRx = Boolean(product.rxRequirement && product.rxRequirement !== 'NONE' && product.rxRequirement !== 'OTC');

                    return (
                      <div
                        key={product.id}
                        className="py-3 flex items-center justify-between gap-3 hover:bg-surface-subtle p-2 rounded-xl transition-colors"
                      >
                        {/* PRODUCT THUMBNAIL & INFO */}
                        <div
                          onClick={() => {
                            saveRecentSearch(product.name);
                            if (onSelectProduct) onSelectProduct(product);
                            onClose();
                          }}
                          className="flex items-center gap-3 min-w-0 cursor-pointer flex-1"
                        >
                          <div className="w-12 h-12 rounded-lg bg-surface-subtle p-1 flex items-center justify-center shrink-0 border border-border-subtle overflow-hidden">
                            {product.image ? (
                              <img src={product.image} alt={product.name} className="h-full w-full object-contain" />
                            ) : (
                              <ShoppingBag className="h-5 w-5 text-content-muted" />
                            )}
                          </div>

                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider">
                                {product.brandName || product.therapeuticCategory || 'HEALTH'}
                              </span>
                              {isRx && (
                                <span className="px-1.5 py-0.2 rounded text-2xs font-black bg-surface-dangerSubtle text-content-danger border border-border-danger">
                                  Rx
                                </span>
                              )}
                              {product.coldChainRequired && (
                                <span className="px-1.5 py-0.2 rounded text-2xs font-black bg-surface-accentSubtle text-content-accent border border-border-accent">
                                  ❄️ Cold
                                </span>
                              )}
                            </div>

                            <h4 className="text-xs sm:text-sm font-bold text-content-primary truncate">{product.name}</h4>
                            <p className="text-2xs text-content-secondary font-medium">{product.packSize || '1 Unit'}</p>
                          </div>
                        </div>

                        {/* PRICE & ADD ACTION */}
                        <div className="flex items-center gap-3 shrink-0">
                          <div className="text-right">
                            <div className="text-xs font-black text-content-primary">₹{price}</div>
                            {mrp > price && (
                              <div className="text-2xs text-content-muted line-through">₹{mrp}</div>
                            )}
                          </div>

                          <CommerceQuantityControl
                            quantity={qty}
                            onIncrement={() => handleAddToCart(product)}
                            onDecrement={() => {
                              const sku = product.sku || product.id;
                              if (qty <= 1) removeItem(sku);
                              else updateQuantity(sku, qty - 1);
                            }}
                            onAdd={() => handleAddToCart(product)}
                            size="sm"
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
