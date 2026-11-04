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
import SearchMicroLoading from '@/components/search/SearchMicroLoading';
import GenericSubstituteCard from '@/components/search/GenericSubstituteCard';

interface FlagshipSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  products?: MedicineProduct[];
  onSelectProduct?: (product: MedicineProduct) => void;
}

const TRENDING_SEARCHES = [
  { rank: 1, text: 'Dolo 650mg Tablet', badge: '🔥 Trending', salt: 'Paracetamol 650mg' },
  { rank: 2, text: 'Pan-D Capsule', badge: '⚡ Acidity', salt: 'Pantoprazole + Domperidone' },
  { rank: 3, text: 'Shelcal 500 Tablet', badge: '🦴 Calcium', salt: 'Calcium + Vitamin D3' },
  { rank: 4, text: 'Augmentin 625 Duo', badge: '💊 Antibiotic', salt: 'Amoxyclav 625mg' },
  { rank: 5, text: 'Electral ORS Powder', badge: '💧 Hydration', salt: 'Oral Rehydration Salts' },
  { rank: 6, text: 'Becosules Z Capsule', badge: '🛡️ Immunity', salt: 'B-Complex + Zinc' },
];

const CYCLING_HINTS = [
  'Search medicines, brands, salt compositions...',
  'Search "Dolo 650" or "Paracetamol"...',
  'Search "Pan-D" for acidity & gas...',
  'Search "Shelcal 500" for calcium...',
  'Search "Augmentin 625 Duo"...',
  'Search "Electral ORS Powder"...',
];

const RECENT_SEARCHES_KEY = 'commerce_os_recent_searches';

export default function FlagshipSearchModal({
  isOpen,
  onClose,
  products = [],
  onSelectProduct,
}: FlagshipSearchModalProps) {
  const [query, setQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [hintIndex, setHintIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const timer = setInterval(() => {
      setHintIndex((prev) => (prev + 1) % CYCLING_HINTS.length);
    }, 2600);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!query.trim()) {
      setIsSearching(false);
      return;
    }
    setIsSearching(true);
    const timer = setTimeout(() => {
      setIsSearching(false);
    }, 550);
    return () => clearTimeout(timer);
  }, [query]);

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
    const isOutOfStock = product.inStock === false || (product.stockCount !== undefined && product.stockCount <= 0);
    if (isOutOfStock) return;
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
            placeholder={CYCLING_HINTS[hintIndex]}
            className="w-full bg-transparent text-sm sm:text-base font-bold text-content-primary placeholder:text-content-muted outline-none transition-all"
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
              {/* TRUEMEDS-STYLE PROMOTIONAL DISCOUNT TICKET */}
              <div className="p-3.5 rounded-2xl bg-emerald-50 border border-emerald-200 flex items-center justify-between gap-3 shadow-xs">
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-emerald-600 text-white flex items-center justify-center font-black text-xs shrink-0">
                    %
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-black text-emerald-900">FLAT 27% OFF</span>
                      <span className="text-2xs font-extrabold text-emerald-700 bg-emerald-100 px-1.5 py-0.5 rounded">⚡ 10-Min Delivery</span>
                    </div>
                    <p className="text-2xs text-emerald-700">Licensed pharmacist verified genuine batches</p>
                  </div>
                </div>
                <span className="px-2.5 py-1 rounded-lg bg-white border border-emerald-300 text-emerald-700 font-extrabold text-xs">
                  FIRST27
                </span>
              </div>

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

              {/* TRENDING SEARCHES (TATA 1MG & BLINKIT BENCHMARK WITH RANKINGS) */}
              <div className="space-y-2.5">
                <span className="text-xs font-extrabold text-content-muted uppercase tracking-wider flex items-center gap-1.5">
                  <TrendingUp className="h-3.5 w-3.5 text-content-brand" />
                  Trending Health Searches
                </span>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {TRENDING_SEARCHES.map((item) => (
                    <button
                      key={item.text}
                      onClick={() => {
                        setQuery(item.text);
                        saveRecentSearch(item.text);
                      }}
                      className="flex items-center gap-2.5 p-2.5 rounded-xl bg-surface-subtle hover:bg-surface-brandSubtle border border-border-subtle transition-all text-left cursor-pointer group"
                    >
                      <span className={`w-5 h-5 rounded-md flex items-center justify-center text-2xs font-black shrink-0 ${
                        item.rank === 1 ? 'bg-amber-100 text-amber-800' :
                        item.rank === 2 ? 'bg-indigo-100 text-indigo-800' :
                        item.rank === 3 ? 'bg-emerald-100 text-emerald-800' :
                        'bg-slate-100 text-slate-700'
                      }`}>
                        #{item.rank}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="text-xs font-bold text-content-primary group-hover:text-content-brand truncate">
                          {item.text}
                        </div>
                        <div className="text-2xs text-content-muted truncate">
                          {item.salt}
                        </div>
                      </div>
                      <ArrowRight className="h-3.5 w-3.5 text-content-muted group-hover:text-content-brand opacity-0 group-hover:opacity-100 transition-opacity shrink-0" />
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
                    <h4 className="text-xs font-extrabold text-content-primary">10-Minute Store Dispatch</h4>
                    <p className="text-2xs text-content-secondary">Temperature controlled & verified by licensed pharmacists</p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* STATE 2: SEARCH RESULTS OR MICRO-LOADING */}
          {query.trim() && (
            <div className="space-y-3">
              {isSearching ? (
                <SearchMicroLoading />
              ) : (
                <>
                  <div className="flex items-center justify-between text-xs font-bold text-content-secondary">
                    <span>{filteredProducts.length} items found for &quot;{query}&quot;</span>
                    <span className="text-2xs text-[#0E675E] uppercase font-extrabold">⚡ 10-Min Delivery</span>
                  </div>

                  {/* Generic Substitute Strip */}
                  {(query.toLowerCase().includes('dolo') || query.toLowerCase().includes('paracetamol')) && (
                    <GenericSubstituteCard
                      onApplySubstitute={() => {
                        addToCart({
                          sku: 'paracip-650',
                          productId: 'paracip-650',
                          name: 'Paracip 650mg Tablet',
                          brand: 'Cipla Ltd',
                          packSize: '15 Tablets',
                          unitPrice: 18,
                          mrp: 30,
                          rxRequired: false,
                          coldChain: false,
                          expressDeliverySlaMins: 10,
                        });
                        onClose();
                      }}
                    />
                  )}

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
                    const isOutOfStock = product.inStock === false || (product.stockCount !== undefined && product.stockCount <= 0);

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

                          {isOutOfStock ? (
                            <button
                              disabled
                              className="h-8 px-2.5 text-2xs font-bold text-content-muted bg-surface-subtle rounded-md cursor-not-allowed border border-border-default select-none"
                            >
                              Unavailable
                            </button>
                          ) : (
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
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
