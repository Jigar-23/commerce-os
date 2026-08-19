'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  FileText,
  ShieldCheck,
  Clock,
  Plus,
  Check,
  AlertTriangle,
  Star,
  Info,
  ChevronRight,
  ArrowLeft,
  ShieldAlert,
  Sparkles,
  Zap,
  TrendingDown,
  PackageX,
  ThermometerSnowflake,
  Activity,
  Pill,
  Droplets,
  HeartPulse,
  ShoppingBag,
  Share2,
  Building,
} from 'lucide-react';
import {
  fetchMedicineById,
  fetchCatalogMedicines,
  MedicineProduct,
  SavedAddress,
  fetchCustomerAddresses,
} from '@/lib/api-client';
import { useCart } from '@/lib/cart-store';
import {
  CommerceNavbar,
  CommerceProductCard,
  CommerceQuantityControl,
  CommercePriceBlock,
  CommerceBadge,
  CommerceCartDrawer,
  CommerceSectionHeader,
  CommerceButton,
} from '@commerce-os/ui';
import DeliveryAddressMapModal from '@/components/DeliveryAddressMapModal';
import FlagshipSearchModal from '@/components/FlagshipSearchModal';

const REFERENCE_DETAILS: Record<
  string,
  Partial<{
    saltCompositions: { saltName: string; strength: string }[];
    dosageForm: string;
    uses: string[];
    sideEffects: string[];
    warnings: { category: string; rating: string; description: string }[];
  }>
> = {
  'SKU-AUG-625': {
    saltCompositions: [
      { saltName: 'Amoxicillin Trihydrate', strength: '500 mg' },
      { saltName: 'Potassium Clavulanate', strength: '125 mg' },
    ],
    dosageForm: 'Film-Coated Tablet',
    uses: [
      'Bacterial infections of the respiratory tract (bronchitis, pneumonia)',
      'Severe ear, nose, and throat infections (sinusitis, otitis media)',
      'Skin and soft tissue bacterial infections',
      'Urinary tract infections (UTI)',
    ],
    sideEffects: ['Mild nausea', 'Diarrhea', 'Skin rash or itching', 'Vomiting'],
    warnings: [
      { category: 'Alcohol', rating: 'Caution', description: 'Avoid alcohol consumption as it may increase dizziness and stomach discomfort.' },
      { category: 'Pregnancy', rating: 'Consult Physician', description: 'Generally considered safe under prescription. Consult your OB/GYN.' },
      { category: 'Kidney / Liver', rating: 'Dose Adjustment Required', description: 'Requires creatinine clearance monitoring for renal impairment patients.' },
    ],
  },
};

export default function MedicineDetailsPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const [product, setProduct] = useState<MedicineProduct | null>(null);
  const [allMedicines, setAllMedicines] = useState<MedicineProduct[]>([]);
  const [similarProducts, setSimilarProducts] = useState<MedicineProduct[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isCartOpen, setIsCartOpen] = useState(false);
  const [isMapModalOpen, setIsMapModalOpen] = useState(false);
  const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [currentAddress, setCurrentAddress] = useState<SavedAddress | null>(null);
  const [activeTab, setActiveTab] = useState<'uses' | 'composition' | 'safety' | 'storage'>('uses');
  const [copiedLink, setCopiedLink] = useState(false);

  const cartLines = useCart((s) => s.lines);
  const addToCart = useCart((s) => s.addItem);
  const updateQuantity = useCart((s) => s.updateQuantity);
  const removeItem = useCart((s) => s.removeItem);
  const cartCount = cartLines.reduce((sum, l) => sum + l.quantity, 0);
  const cartSubtotal = cartLines.reduce((sum, l) => sum + (l.unitPrice || 0) * l.quantity, 0);

  useEffect(() => {
    async function loadAddresses() {
      try {
        const addrs = await fetchCustomerAddresses();
        if (addrs && addrs.length > 0) {
          setSavedAddresses(addrs);
          const def = addrs.find((a) => a.isDefault) || addrs[0];
          setCurrentAddress(def);
        }
      } catch (e) {
        setSavedAddresses([]);
        setCurrentAddress(null);
      }
    }
    loadAddresses();
  }, []);

  useEffect(() => {
    async function loadData() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const item = await fetchMedicineById(params.id);
        if (item) {
          setProduct(item);
          const allCatalog = await fetchCatalogMedicines(undefined, item.therapeuticCategory);
          setAllMedicines(allCatalog);
          setSimilarProducts(allCatalog.filter((c) => c.id !== item.id).slice(0, 4));
        } else {
          setLoadError('Medicine details unavailable.');
        }
      } catch (err: any) {
        setLoadError(err.message || 'Failed to load medicine information.');
      } finally {
        setIsLoading(false);
      }
    }
    loadData();
  }, [params.id]);

  const handleAddToCart = () => {
    if (!product) return;
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

  const handleIncrement = () => {
    if (!product) return;
    const sku = product.sku || product.id;
    const line = cartLines.find((l) => l.sku === sku || l.productId === product.id);
    if (line) {
      updateQuantity(line.sku, line.quantity + 1);
    } else {
      handleAddToCart();
    }
  };

  const handleDecrement = () => {
    if (!product) return;
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

  const currentQty = product ? cartLines.find((l) => l.sku === (product.sku || product.id) || l.productId === product.id)?.quantity || 0 : 0;
  const isRx = Boolean(product?.rxRequirement && product.rxRequirement !== 'NONE' && product.rxRequirement !== 'OTC');
  const details = product ? REFERENCE_DETAILS[product.sku || ''] || {} : {};

  const handleShare = () => {
    navigator.clipboard.writeText(window.location.href);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-surface-canvas text-content-primary flex flex-col font-sans antialiased pb-24">
        <CommerceNavbar
          locationAddress={`${currentAddress.contactName || 'Home'} • ${currentAddress.addressLine}`}
          cartItemCount={cartCount}
          cartTotalAmount={cartSubtotal}
          onOpenCart={() => setIsCartOpen(true)}
        />
        <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 animate-pulse">
            <div className="aspect-square bg-surface-subtle rounded-3xl" />
            <div className="space-y-4">
              <div className="h-4 w-24 bg-surface-muted rounded-xs" />
              <div className="h-8 w-3/4 bg-surface-muted rounded-md" />
              <div className="h-4 w-1/2 bg-surface-subtle rounded-xs" />
              <div className="h-12 w-48 bg-surface-muted rounded-xl mt-6" />
            </div>
          </div>
        </main>
      </div>
    );
  }

  if (loadError || !product) {
    return (
      <div className="min-h-screen bg-surface-canvas text-content-primary flex flex-col font-sans antialiased pb-24">
        <CommerceNavbar
          locationAddress={`${currentAddress.contactName || 'Home'} • ${currentAddress.addressLine}`}
          cartItemCount={cartCount}
          cartTotalAmount={cartSubtotal}
          onOpenCart={() => setIsCartOpen(true)}
        />
        <main className="max-w-2xl mx-auto px-4 py-16 text-center space-y-4">
          <div className="w-16 h-16 rounded-2xl bg-surface-dangerSubtle text-content-danger flex items-center justify-center mx-auto border border-border-danger">
            <PackageX className="h-8 w-8" />
          </div>
          <h2 className="text-xl font-extrabold text-content-primary">Medicine Not Found</h2>
          <p className="text-sm text-content-secondary">{loadError || 'This item is no longer active in our catalog.'}</p>
          <Link
            href="/"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-action-primaryBg text-action-primaryText font-bold text-xs rounded-xl shadow-subtle hover:bg-action-primaryHover transition-all"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Return to Storefront</span>
          </Link>
        </main>
      </div>
    );
  }

  const price = product.discountedPrice || product.price;
  const mrp = product.mrp || Math.round(price * 1.25);
  const savings = Math.max(0, mrp - price);
  const discountPercent = mrp > price ? Math.round(((mrp - price) / mrp) * 100) : 0;

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex flex-col font-sans antialiased pb-24 selection:bg-surface-brandSubtle selection:text-content-brand">
      {/* 1. NAVBAR */}
      <CommerceNavbar
        locationAddress={`${currentAddress.contactName || 'Home'} • ${currentAddress.addressLine}`}
        onOpenLocationModal={() => setIsMapModalOpen(true)}
        cartItemCount={cartCount}
        cartTotalAmount={cartSubtotal}
        onOpenCart={() => setIsCartOpen(true)}
        onSearchChange={() => setIsSearchModalOpen(true)}
      />

      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 space-y-8 w-full">
        {/* 2. BREADCRUMBS & TOP NAV */}
        <div className="flex items-center justify-between text-xs font-bold text-content-muted">
          <div className="flex items-center gap-2">
            <Link href="/" className="hover:text-content-primary transition-colors flex items-center gap-1">
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back</span>
            </Link>
            <span>/</span>
            <span className="text-content-secondary uppercase tracking-wider">{product.therapeuticCategory || 'Medicines'}</span>
            <span>/</span>
            <span className="text-content-primary truncate max-w-[200px] sm:max-w-xs">{product.name}</span>
          </div>

          <button
            onClick={handleShare}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-surface-card hover:bg-surface-subtle border border-border-default text-content-secondary hover:text-content-primary text-xs font-bold transition-all cursor-pointer"
          >
            <Share2 className="h-3.5 w-3.5" />
            <span>{copiedLink ? 'Link Copied!' : 'Share'}</span>
          </button>
        </div>

        {/* 3. HERO PRODUCT SHOWCASE (2 COLUMNS) */}
        <section className="grid grid-cols-1 md:grid-cols-2 gap-8 lg:gap-12 bg-surface-card p-6 sm:p-8 rounded-3xl border border-border-default shadow-card">
          {/* LEFT: IMAGE & TAGS */}
          <div className="flex flex-col items-center justify-center">
            <div className="relative w-full aspect-square max-w-md bg-surface-subtle rounded-2xl p-6 flex items-center justify-center border border-border-subtle overflow-hidden group">
              {product.image ? (
                <img
                  src={product.image}
                  alt={product.name}
                  className="h-full w-full object-contain transition-transform duration-300 group-hover:scale-105"
                />
              ) : (
                <ShoppingBag className="h-16 w-16 text-content-muted" />
              )}

              {/* OVERLAY BADGES */}
              <div className="absolute top-3 left-3 flex flex-col gap-2">
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface-card/95 text-content-brand text-2xs font-extrabold shadow-subtle border border-border-brandSubtle">
                  <Clock className="h-3.5 w-3.5" />
                  <span>⚡ 10-Min Delivery</span>
                </span>
                {product.coldChainRequired && (
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface-accentSubtle text-content-accent text-2xs font-extrabold shadow-subtle border border-border-accent">
                    <ThermometerSnowflake className="h-3.5 w-3.5" />
                    <span>Cold Chain (2–8°C)</span>
                  </span>
                )}
              </div>

              {isRx && (
                <div className="absolute top-3 right-3">
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface-dangerSubtle text-content-danger text-2xs font-black shadow-subtle border border-border-danger">
                    <FileText className="h-3.5 w-3.5" />
                    <span>Prescription Required</span>
                  </span>
                </div>
              )}
            </div>

            {/* DARK STORE INTEGRITY BANNER */}
            <div className="mt-4 w-full p-3 rounded-xl bg-surface-brandSubtle border border-border-brandSubtle flex items-center justify-between text-2xs font-bold text-content-brand">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-content-brand" />
                <span>100% Genuine Pharmacy Stock • Batch QR Verified</span>
              </div>
            </div>
          </div>

          {/* RIGHT: DETAILS, PRICING & ACTIONS */}
          <div className="flex flex-col justify-between space-y-6">
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <span className="px-2.5 py-0.5 rounded-md bg-surface-subtle text-content-muted text-2xs font-black uppercase tracking-wider border border-border-subtle">
                  {product.brandName || 'HEALTHCARE'}
                </span>
                <span className="text-2xs text-content-muted">•</span>
                <span className="text-2xs font-bold text-content-brand uppercase tracking-wider">
                  {product.therapeuticCategory || 'General'}
                </span>
              </div>

              <h1 className="text-xl sm:text-2xl font-black text-content-primary tracking-tight leading-tight">
                {product.name}
              </h1>

              {product.packSize && (
                <p className="text-xs font-semibold text-content-secondary">
                  Packaging: <span className="text-content-primary font-bold">{product.packSize}</span>
                </p>
              )}

              {/* PRICE & SAVINGS BLOCK */}
              <div className="pt-3 border-t border-border-subtle flex items-baseline gap-3">
                <span className="text-3xl font-black text-content-primary">₹{price}</span>
                {mrp > price && (
                  <>
                    <span className="text-sm font-bold text-content-muted line-through">₹{mrp}</span>
                    <span className="px-2 py-0.5 rounded-md bg-surface-brandSubtle text-content-brand text-xs font-black border border-border-brandSubtle">
                      {discountPercent}% OFF
                    </span>
                  </>
                )}
              </div>
              {savings > 0 && (
                <p className="text-xs font-bold text-content-brand">
                  🎉 You save ₹{savings} on this item
                </p>
              )}

              {/* DELIVERY SLA PROMISE */}
              <div className="p-3.5 rounded-2xl bg-surface-subtle border border-border-subtle flex items-start gap-3 mt-4">
                <div className="w-8 h-8 rounded-lg bg-action-primaryBg text-action-primaryText flex items-center justify-center shrink-0">
                  ⚡
                </div>
                <div>
                  <h4 className="text-xs font-extrabold text-content-primary">Lightning 10-Minute Dispatch</h4>
                  <p className="text-2xs text-content-secondary font-medium mt-0.5">
                    Fulfilled directly from Sector 18 Dark Store. Temperature mapped and handled by licensed personnel.
                  </p>
                </div>
              </div>
            </div>

            {/* DOMINANT PURCHASE BUTTON */}
            <div className="pt-4 border-t border-border-subtle flex items-center gap-4">
              {currentQty > 0 ? (
                <div className="flex items-center gap-3">
                  <CommerceQuantityControl
                    quantity={currentQty}
                    onIncrement={handleIncrement}
                    onDecrement={handleDecrement}
                    size="lg"
                  />
                  <button
                    onClick={() => setIsCartOpen(true)}
                    className="px-5 py-3 rounded-xl bg-action-primaryBg hover:bg-action-primaryHover text-action-primaryText text-xs font-extrabold shadow-subtle transition-all active:scale-95 cursor-pointer flex items-center gap-2"
                  >
                    <ShoppingBag className="h-4 w-4" />
                    <span>View in Cart ({currentQty})</span>
                  </button>
                </div>
              ) : (
                <button
                  onClick={handleAddToCart}
                  className="flex-1 py-3.5 px-6 rounded-2xl bg-action-primaryBg hover:bg-action-primaryHover active:bg-action-primaryBg text-action-primaryText font-black text-sm shadow-subtle hover:shadow-card transition-all active:scale-98 flex items-center justify-center gap-2 cursor-pointer"
                >
                  <Plus className="h-5 w-5 stroke-[3]" />
                  <span>ADD TO CART • ₹{price}</span>
                </button>
              )}
            </div>
          </div>
        </section>

        {/* 4. CLINICAL SPECIFICATION & SAFETY TABS */}
        <section className="bg-surface-card rounded-3xl border border-border-default shadow-card p-6 sm:p-8 space-y-6">
          <div className="flex border-b border-border-subtle gap-4 sm:gap-8 overflow-x-auto pb-1 scrollbar-none text-xs sm:text-sm font-extrabold">
            <button
              onClick={() => setActiveTab('uses')}
              className={`pb-3 border-b-2 transition-all shrink-0 cursor-pointer ${
                activeTab === 'uses' ? 'border-border-brand text-content-brand' : 'border-transparent text-content-secondary hover:text-content-primary'
              }`}
            >
              Uses & Key Benefits
            </button>
            <button
              onClick={() => setActiveTab('composition')}
              className={`pb-3 border-b-2 transition-all shrink-0 cursor-pointer ${
                activeTab === 'composition' ? 'border-border-brand text-content-brand' : 'border-transparent text-content-secondary hover:text-content-primary'
              }`}
            >
              Salt Composition
            </button>
            <button
              onClick={() => setActiveTab('safety')}
              className={`pb-3 border-b-2 transition-all shrink-0 cursor-pointer ${
                activeTab === 'safety' ? 'border-border-brand text-content-brand' : 'border-transparent text-content-secondary hover:text-content-primary'
              }`}
            >
              Safety & Drug Warnings
            </button>
            <button
              onClick={() => setActiveTab('storage')}
              className={`pb-3 border-b-2 transition-all shrink-0 cursor-pointer ${
                activeTab === 'storage' ? 'border-border-brand text-content-brand' : 'border-transparent text-content-secondary hover:text-content-primary'
              }`}
            >
              Storage & Cold Chain
            </button>
          </div>

          {/* TAB 1: USES */}
          {activeTab === 'uses' && (
            <div className="space-y-4">
              <h3 className="text-sm font-extrabold text-content-primary">Therapeutic Indications</h3>
              <ul className="space-y-2 text-xs text-content-secondary font-medium">
                {(details.uses || [
                  'Fast relief from symptoms and bacterial conditions as advised by your physician',
                  'Maintains optimal physiological balance during recovery',
                  'Clinically tested and verified by DCGI standards',
                ]).map((use, i) => (
                  <li key={i} className="flex items-start gap-2">
                    <Check className="h-4 w-4 text-content-brand shrink-0 mt-0.5" />
                    <span>{use}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* TAB 2: COMPOSITION */}
          {activeTab === 'composition' && (
            <div className="space-y-4">
              <h3 className="text-sm font-extrabold text-content-primary">Active Pharmaceutical Ingredients (APIs)</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {(details.saltCompositions || [
                  { saltName: product.name, strength: product.packSize || 'Standard Therapeutic Dose' },
                ]).map((salt, i) => (
                  <div key={i} className="p-3.5 rounded-xl bg-surface-subtle border border-border-subtle flex items-center justify-between">
                    <span className="text-xs font-bold text-content-primary">{salt.saltName}</span>
                    <span className="text-2xs font-extrabold text-content-brand bg-surface-brandSubtle px-2 py-0.5 rounded border border-border-brandSubtle">
                      {salt.strength}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 3: SAFETY WARNINGS */}
          {activeTab === 'safety' && (
            <div className="space-y-3">
              <h3 className="text-sm font-extrabold text-content-primary">Safety Advice & Warnings</h3>
              <div className="space-y-2.5">
                {(details.warnings || [
                  { category: 'Alcohol', rating: 'Caution', description: 'Consult your doctor before consuming alcohol with this medication.' },
                  { category: 'Pregnancy', rating: 'Consult Doctor', description: 'Safety during pregnancy must be evaluated by a healthcare practitioner.' },
                  { category: 'Driving', rating: 'Safe with Caution', description: 'May cause mild drowsiness in sensitive individuals.' },
                ]).map((w, i) => (
                  <div key={i} className="p-3.5 rounded-xl bg-surface-subtle border border-border-subtle flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <AlertTriangle className="h-4 w-4 text-content-warning shrink-0" />
                      <span className="text-xs font-bold text-content-primary">{w.category}:</span>
                      <span className="text-xs text-content-secondary">{w.description}</span>
                    </div>
                    <span className="px-2 py-0.5 rounded text-2xs font-black bg-surface-warningSubtle text-content-warning border border-border-warning shrink-0">
                      {w.rating}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 4: STORAGE */}
          {activeTab === 'storage' && (
            <div className="space-y-3">
              <h3 className="text-sm font-extrabold text-content-primary">Storage & Quality Assurance</h3>
              <div className="p-4 rounded-2xl bg-surface-subtle border border-border-subtle space-y-2 text-xs text-content-secondary">
                <p className="font-bold text-content-primary">
                  Temperature: {product.coldChainRequired ? 'Store between 2°C and 8°C in refrigerator' : 'Store in a cool, dry place away from direct sunlight (below 25°C)'}
                </p>
                <p>Keep out of reach of children. Do not freeze cold chain biologics.</p>
              </div>
            </div>
          )}
        </section>

        {/* 5. SIMILAR PRODUCTS CAROUSEL */}
        {similarProducts.length > 0 && (
          <section className="space-y-4">
            <CommerceSectionHeader
              title="Similar Alternatives & Essentials"
              subtitle="Other verified products in this category"
            />
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3.5 sm:gap-4">
              {similarProducts.map((p) => (
                <CommerceProductCard
                  key={p.id}
                  product={p}
                  quantity={cartLines.find((l) => l.sku === (p.sku || p.id) || l.productId === p.id)?.quantity || 0}
                  onAddToCart={() => {
                    const sku = p.sku || p.id;
                    addToCart({
                      sku,
                      productId: p.id,
                      name: p.name,
                      brand: p.brandName || p.therapeuticCategory || 'HEALTH',
                      packSize: p.packSize || '',
                      unitPrice: p.discountedPrice || p.price,
                      mrp: p.mrp || p.price * 1.25,
                      image: p.image || '',
                      rxRequired: Boolean(p.rxRequirement && p.rxRequirement !== 'NONE' && p.rxRequirement !== 'OTC'),
                      coldChain: Boolean(p.coldChainRequired),
                      expressDeliverySlaMins: p.expressDeliverySlaMins || 10,
                    });
                  }}
                  onCardClick={(item) => router.push(`/medicines/${item.id}`)}
                />
              ))}
            </div>
          </section>
        )}
      </main>

      {/* 6. MODALS & DRAWERS */}
      <FlagshipSearchModal
        isOpen={isSearchModalOpen}
        onClose={() => setIsSearchModalOpen(false)}
        products={allMedicines}
        onSelectProduct={(p) => router.push(`/medicines/${p.id}`)}
      />

      <DeliveryAddressMapModal
        isOpen={isMapModalOpen}
        onClose={() => setIsMapModalOpen(false)}
        savedAddresses={savedAddresses}
        currentAddress={currentAddress}
        onSelectAddress={(addr) => {
          setCurrentAddress(addr);
          setIsMapModalOpen(false);
        }}
      />

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
