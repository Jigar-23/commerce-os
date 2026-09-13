'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  Search,
  ShoppingCart,
  Clock,
  ShieldCheck,
  Zap,
  Package,
  Check,
  RefreshCw,
  ChevronRight,
  Filter,
  Sparkles,
  HeartPulse,
  Truck,
  FileText,
  AlertCircle,
  TrendingDown,
  Droplets,
  ThermometerSnowflake,
  Flame,
  ArrowUpDown,
} from 'lucide-react';
import {
  fetchCatalogMedicines,
  MedicineProduct,
  fetchCustomerAddresses,
  SavedAddress,
} from '@/lib/api-client';
import { useCart } from '@/lib/cart-store';
import DeliveryAddressMapModal from '@/components/DeliveryAddressMapModal';
import FlagshipSearchModal from '@/components/FlagshipSearchModal';
import {
  CommerceNavbar,
  CommerceProductCard,
  CommerceCategoryShowcase,
  CommerceSectionHeader,
  CommerceCartDrawer,
  CommerceEmptyState,
  CommerceErrorState,
  CommerceBadge,
  CommerceButton,
} from '@commerce-os/ui';

export default function StorefrontHomePage() {
  const router = useRouter();
  const [medicines, setMedicines] = useState<MedicineProduct[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [apiError, setApiError] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [isMapModalOpen, setIsMapModalOpen] = useState(false);
  const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [currentAddress, setCurrentAddress] = useState<SavedAddress | null>(null);
  const [isCartOpen, setIsCartOpen] = useState(false);
  const [filterType, setFilterType] = useState<'ALL' | 'EXPRESS' | 'COLD_CHAIN' | 'OTC'>('ALL');
  const [sortBy, setSortBy] = useState<'POPULAR' | 'PRICE_LOW' | 'PRICE_HIGH'>('POPULAR');

  const cartLines = useCart((s) => s.lines);
  const addToCart = useCart((s) => s.addItem);
  const updateQuantity = useCart((s) => s.updateQuantity);
  const removeItem = useCart((s) => s.removeItem);
  const cartCount = cartLines.reduce((sum, l) => sum + l.quantity, 0);
  const cartSubtotal = cartLines.reduce((sum, l) => sum + (l.unitPrice || 0) * l.quantity, 0);

  const loadData = async (searchQuery?: string) => {
    setIsLoading(true);
    setApiError(null);
    try {
      const data = await fetchCatalogMedicines(searchQuery, selectedCategory || undefined);
      setMedicines(data || []);
    } catch (err: any) {
      setApiError(err?.message || 'Failed to connect to authoritative catalog service.');
      setMedicines([]);
    } finally {
      setIsLoading(false);
    }
  };

  const loadAddresses = async () => {
    try {
      const addrs = await fetchCustomerAddresses();
      if (addrs && addrs.length > 0) {
        setSavedAddresses(addrs);
        const def = addrs.find((a) => a.isDefault) || addrs[0];
        setCurrentAddress(def);
      } else {
        setSavedAddresses([]);
        setCurrentAddress(null);
      }
    } catch (e) {
      setSavedAddresses([]);
      setCurrentAddress(null);
    }
  };

  useEffect(() => {
    loadAddresses();
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      loadData(query);
    }, 250);
    return () => clearTimeout(timer);
  }, [query, selectedCategory]);

  const handleAddToCart = (product: MedicineProduct) => {
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

  const handleIncrement = (product: MedicineProduct) => {
    const sku = product.sku || product.id;
    const line = cartLines.find((l) => l.sku === sku || l.productId === product.id);
    if (line) {
      updateQuantity(line.sku, line.quantity + 1);
    } else {
      handleAddToCart(product);
    }
  };

  const handleDecrement = (product: MedicineProduct) => {
    const sku = product.sku || product.id;
    const line = cartLines.find((l) => l.sku === sku || l.productId === product.id);
    if (line) {
      if (line.quantity <= 1) {
        removeItem(line.sku);
      } else {
        updateQuantity(line.sku, line.quantity - 1);
      }
    }
  };

  const getItemQuantity = (product: MedicineProduct) => {
    const sku = product.sku || product.id;
    return cartLines.find((l) => l.sku === sku || l.productId === product.id)?.quantity || 0;
  };

  // Filter & Sort Logic
  const processedMedicines = medicines
    .filter((med) => {
      if (selectedCategory && med.therapeuticCategory !== selectedCategory) {
        return false;
      }
      if (filterType === 'COLD_CHAIN' && !med.coldChainRequired) {
        return false;
      }
      if (filterType === 'OTC' && med.rxRequirement && med.rxRequirement !== 'NONE' && med.rxRequirement !== 'OTC') {
        return false;
      }
      if (filterType === 'EXPRESS' && (med.expressDeliverySlaMins || 10) > 10) {
        return false;
      }
      if (query.trim()) {
        const q = query.toLowerCase();
        return (
          med.name.toLowerCase().includes(q) ||
          (med.brandName && med.brandName.toLowerCase().includes(q)) ||
          (med.therapeuticCategory && med.therapeuticCategory.toLowerCase().includes(q))
        );
      }
      return true;
    })
    .sort((a, b) => {
      const priceA = a.discountedPrice || a.price;
      const priceB = b.discountedPrice || b.price;
      if (sortBy === 'PRICE_LOW') return priceA - priceB;
      if (sortBy === 'PRICE_HIGH') return priceB - priceA;
      return 0; // POPULAR
    });

  const locationDisplay = currentAddress
    ? `${currentAddress.contactName || currentAddress.tag || 'Delivery'} • ${currentAddress.addressLine}`
    : 'Select delivery location';

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary font-sans antialiased pb-24 selection:bg-surface-brandSubtle selection:text-content-brand">
      {/* 1. CANONICAL COMMERCE NAVBAR */}
      <CommerceNavbar
        locationAddress={locationDisplay}
        onOpenLocationModal={() => setIsMapModalOpen(true)}
        searchQuery={query}
        onSearchChange={(q) => {
          setQuery(q);
          if (q.trim().length > 0) setIsSearchModalOpen(true);
        }}
        showSearchBar={true}
        cartItemCount={cartCount}
        cartTotalAmount={cartSubtotal}
        onOpenCart={() => setIsCartOpen(true)}
        ordersHref="/orders"
        profileHref="/profile"
      />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-4 sm:pt-6 space-y-6 sm:space-y-8">
        {/* 2. INSTANT DELIVERY & TRUST HERO */}
        {!query && (
          <section className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-navy-950 via-surface-inverse to-navy-950 text-white p-6 sm:p-8 shadow-xl border border-border-strong">
            <div className="relative z-10 max-w-2xl space-y-3">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-black border border-border-brand">
                  <Clock className="h-3.5 w-3.5 text-content-brand" />
                  <span>Guaranteed 10-Minute Dispatch</span>
                </span>
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-inverse text-content-muted text-xs font-bold border border-border-strong">
                  <ShieldCheck className="h-3.5 w-3.5 text-content-brand" />
                  <span>100% Genuine Certified</span>
                </span>
              </div>

              <h1 className="text-2xl sm:text-3xl md:text-4xl font-black tracking-tight text-white leading-tight">
                Instant Healthcare &amp; Daily Essentials Delivered in Minutes
              </h1>

              <p className="text-xs sm:text-sm text-content-muted max-w-xl font-normal leading-relaxed">
                Direct fulfillment from licensed temperature-controlled dark stores with strict batch verification, 2–8°C cold chain safety, and registered pharmacist supervision.
              </p>

              <div className="pt-2 flex items-center gap-4 sm:gap-6 text-xs text-content-muted flex-wrap">
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded-lg bg-surface-brandSubtle flex items-center justify-center text-content-brand">
                    <Truck className="h-3.5 w-3.5" />
                  </div>
                  <span className="font-bold text-content-subtle">Live GPS Radar</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded-lg bg-surface-accentSubtle flex items-center justify-center text-content-accent">
                    <ThermometerSnowflake className="h-3.5 w-3.5" />
                  </div>
                  <span className="font-bold text-content-subtle">Cold Chain (2–8°C)</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded-lg bg-surface-brandSubtle flex items-center justify-center text-content-brand">
                    <HeartPulse className="h-3.5 w-3.5" />
                  </div>
                  <span className="font-bold text-content-subtle">Rx Verified</span>
                </div>
              </div>
            </div>
          </section>
        )}

        {/* 3. CATEGORY SHOWCASE STRIP */}
        <section>
          <CommerceSectionHeader
            title="Shop by Category"
            subtitle="Explore curated therapeutic categories and daily essentials"
          />
          <CommerceCategoryShowcase
            selectedCategory={selectedCategory}
            onSelectCategory={setSelectedCategory}
          />
        </section>

        {/* 4. FILTER & SORT CONTROLS BAR */}
        <section className="flex items-center justify-between gap-3 flex-wrap bg-surface-card p-3 rounded-2xl border border-border-subtle shadow-card">
          <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
            <button
              onClick={() => setFilterType('ALL')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer ${
                filterType === 'ALL'
                  ? 'bg-action-primaryBg text-action-primaryText shadow-subtle'
                  : 'bg-surface-subtle text-content-secondary hover:bg-surface-muted'
              }`}
            >
              All Items
            </button>
            <button
              onClick={() => setFilterType('EXPRESS')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer flex items-center gap-1.5 ${
                filterType === 'EXPRESS'
                  ? 'bg-action-primaryBg text-action-primaryText shadow-subtle'
                  : 'bg-surface-subtle text-content-secondary hover:bg-surface-muted'
              }`}
            >
              <Clock className="h-3.5 w-3.5" />
              ⚡ 10-Min Express
            </button>
            <button
              onClick={() => setFilterType('COLD_CHAIN')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer flex items-center gap-1.5 ${
                filterType === 'COLD_CHAIN'
                  ? 'bg-action-primaryBg text-action-primaryText shadow-subtle'
                  : 'bg-surface-subtle text-content-secondary hover:bg-surface-muted'
              }`}
            >
              <ThermometerSnowflake className="h-3.5 w-3.5" />
              Cold Chain (2–8°C)
            </button>
            <button
              onClick={() => setFilterType('OTC')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 cursor-pointer flex items-center gap-1.5 ${
                filterType === 'OTC'
                  ? 'bg-action-primaryBg text-action-primaryText shadow-subtle'
                  : 'bg-surface-subtle text-content-secondary hover:bg-surface-muted'
              }`}
            >
              <ShieldCheck className="h-3.5 w-3.5" />
              OTC / No Rx Needed
            </button>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <span className="text-2xs font-extrabold text-content-muted uppercase tracking-wider hidden sm:inline">Sort by:</span>
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as any)}
              className="bg-surface-subtle text-content-primary text-xs font-bold px-3 py-1.5 rounded-xl border border-border-default outline-none cursor-pointer hover:bg-surface-muted transition-colors"
            >
              <option value="POPULAR">Most Popular</option>
              <option value="PRICE_LOW">Price: Low to High</option>
              <option value="PRICE_HIGH">Price: High to Low</option>
            </select>
          </div>
        </section>

        {/* 5. PRODUCT CATALOG GRID */}
        <section className="space-y-4">
          <CommerceSectionHeader
            title={query ? `Search results for "${query}"` : selectedCategory ? 'Filtered Products' : 'Popular Health Essentials'}
            subtitle={query ? `Found ${processedMedicines.length} matching products` : 'Lightning 10-min delivery to your doorstep'}
            badge={
              <span className="bg-surface-brandSubtle text-content-brand text-xs font-extrabold px-2.5 py-0.5 rounded-full border border-border-brandSubtle">
                {processedMedicines.length} items
              </span>
            }
          />

          {isLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3.5 sm:gap-4">
              {[...Array(10)].map((_, i) => (
                <CommerceProductCard key={i} product={{ id: `loading-${i}`, name: '', price: 0 }} isLoading={true} />
              ))}
            </div>
          ) : apiError ? (
            <CommerceErrorState
              title="Catalog Service Unavailable"
              message={apiError}
              onRetry={() => loadData(query)}
            />
          ) : processedMedicines.length === 0 ? (
            <CommerceEmptyState
              title="No products available"
              description={query ? `No medicines matching "${query}". Try searching for salt names or generic brands.` : 'No products found in this category.'}
              actionText={query || selectedCategory || filterType !== 'ALL' ? 'Clear All Filters' : undefined}
              onAction={() => {
                setQuery('');
                setSelectedCategory(null);
                setFilterType('ALL');
              }}
            />
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3.5 sm:gap-4">
              {processedMedicines.map((med) => (
                <CommerceProductCard
                  key={med.id}
                  product={med}
                  quantity={getItemQuantity(med)}
                  onAddToCart={handleAddToCart}
                  onIncrement={handleIncrement}
                  onDecrement={handleDecrement}
                  onCardClick={(p) => router.push(`/medicines/${p.id}`)}
                />
              ))}
            </div>
          )}
        </section>

        {/* 6. PHARMACY SAFETY & COLD-CHAIN NOTICE */}
        <section className="bg-surface-brandSubtle border border-border-brandSubtle/80 rounded-3xl p-5 sm:p-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-2xl bg-action-primaryBg flex items-center justify-center text-white shrink-0 shadow-card">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <div>
              <h3 className="text-sm sm:text-base font-extrabold text-content-primary">
                Government Certified Healthcare Platform
              </h3>
              <p className="text-xs text-content-secondary font-medium mt-0.5">
                Every order is fulfilled from compliant dark stores with batch QR tracking and licensed pharmacist oversight.
              </p>
            </div>
          </div>

          <Link
            href="/orders"
            className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-white border border-border-brand/40 hover:bg-surface-brandSubtle text-content-brand text-xs font-bold shadow-xs transition-colors shrink-0"
          >
            <span>Track Active Orders</span>
            <ChevronRight className="h-4 w-4" />
          </Link>
        </section>
      </main>

      {/* 7. FLOATING QUICK-CART BAR (WHEN ITEMS IN CART) */}
      {cartCount > 0 && (
        <aside aria-label="Cart Summary" className="fixed bottom-4 inset-x-4 max-w-md mx-auto z-40 animate-slide-up">
          <div className="bg-surface-inverse text-white rounded-2xl p-3 px-4 shadow-floating flex items-center justify-between gap-3 border border-border-strong">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-action-primaryBg flex items-center justify-center text-white font-extrabold text-sm shadow-xs">
                {cartCount}
              </div>
              <div>
                <p className="text-2xs font-bold text-content-muted uppercase tracking-wider">Cart Total</p>
                <p className="text-base font-extrabold text-white">₹{cartSubtotal.toFixed(0)}</p>
              </div>
            </div>

            <button
              onClick={() => setIsCartOpen(true)}
              className="flex items-center gap-2 bg-action-primaryBg hover:bg-action-primaryHover active:bg-action-primaryBg text-action-primaryText font-black text-xs sm:text-sm px-4 py-2.5 rounded-xl transition-all active:scale-95 shadow-md shadow-subtle cursor-pointer"
            >
              <span>View Cart</span>
              <ChevronRight className="h-4 w-4 stroke-[3]" />
            </button>
          </div>
        </aside>
      )}

      {/* 8. FLAGSHIP SEARCH MODAL */}
      <FlagshipSearchModal
        isOpen={isSearchModalOpen}
        onClose={() => setIsSearchModalOpen(false)}
        products={medicines}
        onSelectProduct={(p) => router.push(`/medicines/${p.id}`)}
      />

      {/* 9. ADDRESS SELECTION MAP MODAL */}
      <DeliveryAddressMapModal
        isOpen={isMapModalOpen}
        onClose={() => setIsMapModalOpen(false)}
        savedAddresses={savedAddresses}
        currentAddress={currentAddress}
        onSelectAddress={(selected) => {
          setCurrentAddress(selected);
          setIsMapModalOpen(false);
        }}
        onAddressSaved={loadAddresses}
      />

      {/* 10. SLIDE-OUT CART DRAWER */}
      <CommerceCartDrawer
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        items={cartLines}
        onIncrement={(item) => {
          const id = item.sku || item.productId || item.id || '';
          updateQuantity(id, item.quantity + 1);
        }}
        onDecrement={(item) => {
          const id = item.sku || item.productId || item.id || '';
          if (item.quantity <= 1) {
            removeItem(id);
          } else {
            updateQuantity(id, item.quantity - 1);
          }
        }}
        onCheckout={() => {
          setIsCartOpen(false);
          router.push('/checkout');
        }}
      />
    </div>
  );
}