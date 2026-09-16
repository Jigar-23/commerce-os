'use client';

import React, { useState, useEffect, useMemo } from 'react';
import Link from 'next/link';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import { Package, Plus, Search, RefreshCw, AlertCircle, AlertTriangle, CheckCircle, Store, X, Image as ImageIcon, ExternalLink, Calendar, Trash2, FileText } from 'lucide-react';
import SellerAuthGuard from '../../components/SellerAuthGuard';
import SellerSidebar from '../../components/SellerSidebar';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import DatePickerDDMMYYYY from '../../components/DatePickerDDMMYYYY';

function formatDateToDDMMYYYY(dateStr?: string | null): string {
  if (!dateStr) return '';
  const trimmed = dateStr.trim();
  // If already DD/MM/YYYY or DD-MM-YYYY
  if (/^\d{1,2}[/-]\d{1,2}[/-]\d{4}$/.test(trimmed)) {
    return trimmed.replace(/-/g, '/');
  }
  // If YYYY-MM-DD or YYYY/MM/DD
  const matchYMD = trimmed.match(/^(\d{4})[/-](\d{1,2})[/-](\d{1,2})$/);
  if (matchYMD) {
    const [, y, m, d] = matchYMD;
    return `${d.padStart(2, '0')}/${m.padStart(2, '0')}/${y}`;
  }
  const d = new Date(trimmed);
  if (!isNaN(d.getTime())) {
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  }
  return trimmed;
}

interface ProductItem {
  id: string;
  sku: string;
  name: string;
  packSize?: string;
  category?: string;
  price: number;
  mrp?: number;
  discountedPrice?: number;
  stockCount?: number;
  stock_count?: number;
  availableCount?: number;
  available_count?: number;
  inStock?: boolean;
  rxRequirement?: string;
  coldChainRequired?: boolean;
  imageUrl?: string;
  image_url?: string;
  images?: string[];
  description?: string;
  expiryDate?: string;
  expiry_date?: string;
  manufacturingDate?: string;
  manufacturing_date?: string;
}

const STANDARD_CATEGORIES = [
  'Pharmacy & OTC',
  'Vitamins & Daily Wellness',
  'Pain Relief & Fever',
  'Cold, Cough & Immunity',
  'Personal Care',
  'Skin Care',
  'Baby & Mother Care',
  'Ayurveda & Herbal',
  'Diabetes Care',
  'Cardiac Care',
  'Medical Devices & First Aid',
];

export default function ProductsPage() {
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');

  // Add Modal State
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formSuccess, setFormSuccess] = useState(false);
  const [isCustomCategory, setIsCustomCategory] = useState(false);

  // Delete SKU Modal State (Double Confirmation)
  const [deletingProduct, setDeletingProduct] = useState<ProductItem | null>(null);
  const [deleteConfirmationInput, setDeleteConfirmationInput] = useState('');
  const [isDeleteConfirmed, setIsDeleteConfirmed] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleteSuccess, setDeleteSuccess] = useState(false);

  // Form Fields
  const [newSku, setNewSku] = useState('');
  const [newName, setNewName] = useState('');
  const [newPackSize, setNewPackSize] = useState('');
  const [newCategory, setNewCategory] = useState('Pharmacy & OTC');
  const [newPrice, setNewPrice] = useState('');
  const [newMrp, setNewMrp] = useState('');
  const [newStock, setNewStock] = useState('50');
  const [newRx, setNewRx] = useState('OTC');
  const [newImages, setNewImages] = useState<string[]>(['']);
  const [newDescription, setNewDescription] = useState('');
  const [newMfgDate, setNewMfgDate] = useState('');
  const [newExpiryDate, setNewExpiryDate] = useState('');

  const { session, storeName } = useSellerSession();

  const loadProducts = async () => {
    if (!session?.token) return;
    setIsLoading(true);
    setError(null);
    try {
      const res = await sellerApi.get<any>('/api/v1/catalog/products');
      if (res.ok && res.data) {
        const list = Array.isArray(res.data)
          ? res.data
          : (Array.isArray(res.data.content)
              ? res.data.content
              : (Array.isArray(res.data.products)
                  ? res.data.products
                  : (Array.isArray(res.data.items) ? res.data.items : [])));
        const normalized = list.map((item: any) => {
          const sc = Number(item.stockCount ?? item.stock_count ?? item.availableCount ?? item.available_count ?? 0);
          return {
            ...item,
            stockCount: sc,
            stock_count: sc,
            availableCount: sc,
            available_count: sc,
            inStock: sc > 0 || item.inStock === true
          };
        });
        setProducts(normalized);
      } else {
        setError(res.error || 'Failed to retrieve catalog products.');
      }
    } catch (err: any) {
      setError(err.message || 'Network error fetching catalog.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (session?.token) {
      loadProducts();
    }
  }, [session?.token]);

  const handleImageChange = (index: number, val: string) => {
    setNewImages(prev => {
      const copy = [...prev];
      copy[index] = val;
      return copy;
    });
  };

  const handleAddImage = () => {
    setNewImages(prev => [...prev, '']);
  };

  const handleRemoveImage = (index: number) => {
    setNewImages(prev => prev.filter((_, i) => i !== index));
  };

  const handleCreateProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setFormSuccess(false);

    if (!newSku.trim() || !newName.trim() || !newPrice.trim()) {
      setFormError('SKU, Product Name, and Price are strictly required.');
      return;
    }

    if (!newCategory.trim()) {
      setFormError('Category is required. Please select or enter a category.');
      return;
    }

    const priceNum = parseFloat(newPrice);
    const mrpNum = newMrp.trim() ? parseFloat(newMrp) : priceNum;
    const stockNum = parseInt(newStock, 10) || 0;

    if (isNaN(priceNum) || priceNum <= 0) {
      setFormError('Please enter a valid price greater than 0.');
      return;
    }

    const cleanedImages = newImages.map(s => s.trim()).filter(Boolean);
    const primaryImg = cleanedImages.length > 0 ? cleanedImages[0] : undefined;

    setIsSubmitting(true);
    try {
      const payload = {
        sku: newSku.trim(),
        name: newName.trim(),
        packSize: newPackSize.trim() || '1 Unit',
        category: newCategory.trim(),
        price: priceNum,
        mrp: mrpNum,
        initialStock: stockNum,
        stockCount: stockNum,
        reason: 'INITIAL_STOCK_LINK',
        rxRequirement: newRx,
        imageUrl: primaryImg,
        images: cleanedImages,
        description: newDescription.trim() || undefined,
        manufacturingDate: newMfgDate.trim() || undefined,
        expiryDate: newExpiryDate.trim() || undefined,
        storeId: session?.storeId || 'store_rewari_hub_01',
      };

      const res = await sellerApi.post('/api/v1/catalog/products', payload);
      if (res.ok) {
        setFormSuccess(true);
        setTimeout(() => {
          setIsAddModalOpen(false);
          setFormSuccess(false);
          setNewSku('');
          setNewName('');
          setNewPackSize('');
          setNewCategory(existingProductCategories[0] || 'Pharmacy & OTC');
          setIsCustomCategory(false);
          setNewPrice('');
          setNewMrp('');
          setNewStock('50');
          setNewImages(['']);
          setNewDescription('');
          setNewMfgDate('');
          setNewExpiryDate('');
          loadProducts();
        }, 1200);
      } else {
        setFormError(res.error || 'Failed to register product in catalog.');
      }
    } catch (err: any) {
      setFormError(err.message || 'Error occurred while saving product.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const openDeleteModal = (item: ProductItem) => {
    setDeletingProduct(item);
    setDeleteConfirmationInput('');
    setIsDeleteConfirmed(false);
    setDeleteError(null);
    setDeleteSuccess(false);
  };

  const closeDeleteModal = () => {
    if (isDeleting) return;
    setDeletingProduct(null);
    setDeleteConfirmationInput('');
    setIsDeleteConfirmed(false);
    setDeleteError(null);
    setDeleteSuccess(false);
  };

  const canExecuteDelete =
    !!deletingProduct &&
    isDeleteConfirmed &&
    deleteConfirmationInput.trim().toLowerCase() === deletingProduct.sku.trim().toLowerCase();

  const handleDeleteProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!deletingProduct || !canExecuteDelete || isDeleting) return;

    setIsDeleting(true);
    setDeleteError(null);
    try {
      const targetIdentifier = deletingProduct.id || deletingProduct.sku;
      const res = await sellerApi.delete(`/api/v1/catalog/products/${encodeURIComponent(targetIdentifier)}`);
      if (res.ok) {
        setDeleteSuccess(true);
        setTimeout(() => {
          closeDeleteModal();
          loadProducts();
        }, 1200);
      } else {
        setDeleteError(res.error || 'Failed to delete SKU from catalog.');
      }
    } catch (err: any) {
      setDeleteError(err.message || 'An unexpected error occurred while deleting SKU.');
    } finally {
      setIsDeleting(false);
    }
  };

  const existingProductCategories = useMemo(() => {
    return Array.from(
      new Set(
        products
          .map(p => p.category?.trim())
          .filter((c): c is string => Boolean(c))
      )
    ).sort((a, b) => a.localeCompare(b));
  }, [products]);

  const availableCategories = useMemo(() => {
    const set = new Set<string>();
    existingProductCategories.forEach(c => set.add(c));
    STANDARD_CATEGORIES.forEach(c => set.add(c));
    return Array.from(set);
  }, [existingProductCategories]);

  const categories = ['ALL', ...Array.from(new Set(products.map(p => p.category).filter(Boolean)))];

  const filteredProducts = products.filter(p => {
    const matchesSearch = p.name.toLowerCase().includes(searchQuery.toLowerCase()) || p.sku.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = categoryFilter === 'ALL' || p.category === categoryFilter;
    return matchesSearch && matchesCategory;
  });

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-canvas text-content-primary font-sans antialiased overflow-hidden">
        {/* Sidebar Navigation */}
        <SellerSidebar
          activeTab="products"
          onRefresh={loadProducts}
          isLoading={isLoading}
        />

        {/* Main Content Area */}
        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Top Header */}
          <header className="h-16 bg-white border-b border-border-default px-8 flex items-center justify-between shrink-0">
            <div className="flex items-center space-x-3">
              <span className="px-3 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-black border border-border-brandSubtle flex items-center space-x-1.5">
                <Store className="w-3.5 h-3.5" />
                <span>Products & SKUs</span>
              </span>
              <span className="text-content-muted">/</span>
              <span className="text-xs font-bold text-content-primary" suppressHydrationWarning>Rewari Central Hub</span>
              <span className="text-2xs text-content-muted">({products.length} registered SKUs)</span>
            </div>

            <div className="flex items-center space-x-3">
              <HeaderQuickSearch onSelectOrder={() => {}} />
              <button
                onClick={() => setIsAddModalOpen(true)}
                className="flex items-center space-x-2 px-4 py-2 bg-action-speedBg hover:bg-action-speedHover rounded-xl text-xs font-bold text-white shadow-md shadow-subtle transition"
              >
                <Plus className="w-4 h-4" />
                <span>Add Product</span>
              </button>
            </div>
          </header>

          <main className="p-8 space-y-6 flex-1">
            {/* Action Controls & Filters */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div className="flex items-center space-x-3 flex-1 max-w-md">
                <div className="relative flex-1">
                  <Search className="w-4 h-4 absolute left-3.5 top-3 text-content-secondary" />
                  <input
                    type="text"
                    placeholder="Search SKU or product title…"
                    value={searchQuery}
                    onChange={e => setSearchQuery(e.target.value)}
                    className="w-full bg-white border border-border-default rounded-xl pl-10 pr-4 py-2 text-xs text-content-primary placeholder:text-content-muted focus:outline-none focus:border-border-accent shadow-sm"
                  />
                </div>
                <select
                  value={categoryFilter}
                  onChange={e => setCategoryFilter(e.target.value)}
                  className="bg-white border border-border-default rounded-xl px-3 py-2 text-xs text-content-secondary focus:outline-none focus:border-border-accent shadow-sm"
                >
                  {categories.map(cat => (
                    <option key={cat} value={cat}>{cat}</option>
                  ))}
                </select>
              </div>

              <div className="flex items-center space-x-3">
                <button
                  onClick={loadProducts}
                  disabled={isLoading}
                  className="p-2 bg-white hover:bg-surface-subtle border border-border-default rounded-xl text-content-muted transition shadow-sm"
                  title="Refresh Catalog"
                >
                  <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-content-accent' : ''}`} />
                </button>
              </div>
            </div>

            {error && (
              <div className="p-4 bg-surface-dangerSubtle border border-border-danger rounded-2xl flex items-center space-x-3 text-content-danger text-xs font-semibold">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {/* Products Table */}
            <div className="bg-white border border-border-default rounded-2xl overflow-hidden shadow-sm">
              <table className="w-full text-left text-sm text-content-secondary">
                <thead className="bg-surface-subtle text-content-muted uppercase text-2xs font-bold tracking-wider border-b border-border-default">
                  <tr>
                    <th className="px-6 py-4">SKU / Item</th>
                    <th className="px-6 py-4">Category</th>
                    <th className="px-6 py-4">Pack Size</th>
                    <th className="px-6 py-4">Unit Price</th>
                    <th className="px-6 py-4">Stock</th>
                    <th className="px-6 py-4">Rx Required</th>
                    <th className="px-6 py-4 text-right">Status</th>
                    <th className="px-6 py-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-default">
                  {isLoading ? (
                    <tr>
                      <td colSpan={8} className="px-6 py-12 text-center text-content-muted">
                        <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-content-accent" />
                        <span>Loading products from repository…</span>
                      </td>
                    </tr>
                  ) : filteredProducts.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="px-6 py-12 text-center text-content-muted">
                        <Package className="w-8 h-8 mx-auto mb-2 text-content-secondary" />
                        <span>No products found matching your filter criteria.</span>
                      </td>
                    </tr>
                  ) : (
                    filteredProducts.map(p => (
                      <tr key={p.id || p.sku} className="hover:bg-surface-subtle/50 transition">
                        <td className="px-6 py-4">
                          <div className="flex items-start space-x-3">
                            <div className="relative w-11 h-11 rounded-xl bg-surface-subtle border border-border-default flex items-center justify-center overflow-hidden shrink-0 shadow-sm mt-0.5">
                              {(p.imageUrl || p.image_url || (Array.isArray(p.images) && p.images[0])) ? (
                                <img
                                  src={p.imageUrl || p.image_url || (Array.isArray(p.images) ? p.images[0] : '')}
                                  alt={p.name}
                                  className="w-full h-full object-cover"
                                  onError={(e) => {
                                    (e.target as HTMLElement).style.display = 'none';
                                  }}
                                />
                              ) : (
                                <Package className="w-5 h-5 text-content-muted" />
                              )}
                              {Array.isArray(p.images) && p.images.length > 1 && (
                                <span className="absolute bottom-0 right-0 bg-black/75 text-white font-mono text-3xs font-bold px-1 rounded-tl-md">
                                  +{p.images.length - 1}
                                </span>
                              )}
                            </div>
                            <div className="min-w-0">
                              <div className="font-mono text-xs font-bold text-content-accent truncate">{p.sku}</div>
                              <div className="font-bold text-content-primary text-sm truncate">{p.name}</div>
                              {p.description && (
                                <p className="text-2xs text-content-muted line-clamp-1 max-w-[220px] mt-0.5" title={p.description}>
                                  {p.description}
                                </p>
                              )}
                              {((p.images && p.images.length > 0) || p.imageUrl || p.image_url) && (
                                <div className="flex flex-wrap items-center gap-1.5 mt-1">
                                  {Array.isArray(p.images) && p.images.length > 0 ? (
                                    p.images.slice(0, 3).map((img, i) => (
                                      <a
                                        key={i}
                                        href={img}
                                        target="_blank"
                                        rel="noreferrer"
                                        className="inline-flex items-center space-x-0.5 text-3xs text-content-brand hover:underline bg-surface-brandSubtle px-1.5 py-0.5 rounded border border-border-brandSubtle"
                                        title={img}
                                      >
                                        <span>Img {i + 1}</span>
                                        <ExternalLink className="w-2 h-2" />
                                      </a>
                                    ))
                                  ) : (
                                    <a
                                      href={p.imageUrl || p.image_url}
                                      target="_blank"
                                      rel="noreferrer"
                                      className="inline-flex items-center space-x-1 text-2xs text-content-brand hover:underline"
                                    >
                                      <span>Image Link</span>
                                      <ExternalLink className="w-2.5 h-2.5" />
                                    </a>
                                  )}
                                </div>
                              )}
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-4 text-xs">
                          <div className="font-semibold text-content-primary">{p.category || 'General'}</div>
                          {(p.manufacturingDate || p.manufacturing_date) && (
                            <div className="text-3xs text-content-muted mt-1 flex items-center space-x-1">
                              <span className="font-medium">Mfg:</span>
                              <span className="font-mono">{formatDateToDDMMYYYY(p.manufacturingDate || p.manufacturing_date)}</span>
                            </div>
                          )}
                          {(p.expiryDate || p.expiry_date) && (
                            <div className="text-3xs text-content-warning font-semibold flex items-center space-x-1">
                              <span>Exp:</span>
                              <span className="font-mono">{formatDateToDDMMYYYY(p.expiryDate || p.expiry_date)}</span>
                            </div>
                          )}
                        </td>
                        <td className="px-6 py-4 text-xs text-content-muted">{p.packSize || '1 Unit'}</td>
                        <td className="px-6 py-4">
                          <div className="font-bold text-content-primary">₹{Number(p.discountedPrice ?? p.price ?? 0).toFixed(2)}</div>
                          {p.mrp && Number(p.mrp) > Number(p.discountedPrice ?? p.price ?? 0) && (
                            <div className="text-2xs text-content-muted line-through">₹{Number(p.mrp || 0).toFixed(2)}</div>
                          )}
                        </td>
                        <td className="px-6 py-4 font-mono text-xs font-bold text-content-primary">
                          {p.stockCount ?? 0} units
                        </td>
                        <td className="px-6 py-4">
                          {p.rxRequirement && p.rxRequirement !== 'OTC' ? (
                            <span className="px-2 py-0.5 rounded text-2xs font-bold bg-surface-warningSubtle text-content-warning border border-border-warning">
                              {p.rxRequirement}
                            </span>
                          ) : (
                            <span className="text-2xs text-content-muted font-bold">OTC</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span className={`px-2.5 py-1 rounded-full text-2xs font-extrabold ${
                            (p.stockCount ?? 0) > 0 || p.inStock
                              ? 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'
                              : 'bg-surface-dangerSubtle text-content-danger border border-border-danger'
                          }`}>
                            {(p.stockCount ?? 0) > 0 || p.inStock ? 'AVAILABLE' : 'OUT OF STOCK'}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <button
                            type="button"
                            onClick={() => openDeleteModal(p)}
                            className="p-1.5 text-content-muted hover:text-content-danger hover:bg-surface-dangerSubtle rounded-xl transition inline-flex items-center space-x-1 border border-transparent hover:border-border-danger"
                            title={`Delete SKU ${p.sku}`}
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </main>
        </div>

        {/* Add Product Modal */}
        {isAddModalOpen && (
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white border border-border-default rounded-2xl w-full max-w-lg max-h-[90vh] flex flex-col overflow-hidden shadow-2xl animate-in fade-in zoom-in duration-150">
              <div className="px-6 py-4 border-b border-border-default flex items-center justify-between shrink-0">
                <h3 className="font-bold text-content-primary text-base">Add New Product to Catalog</h3>
                <button
                  onClick={() => setIsAddModalOpen(false)}
                  className="text-content-muted hover:text-content-primary p-1 rounded-lg"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleCreateProduct} className="p-6 space-y-4 overflow-y-auto flex-1">
                {formError && (
                  <div className="p-3 bg-surface-dangerSubtle border border-border-danger rounded-xl text-content-danger text-xs flex items-center space-x-2 font-semibold">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{formError}</span>
                  </div>
                )}
                {formSuccess && (
                  <div className="p-3 bg-surface-brandSubtle border border-border-brand rounded-xl text-content-brand text-xs flex items-center space-x-2 font-semibold">
                    <CheckCircle className="w-4 h-4 shrink-0" />
                    <span>Product created successfully in catalog!</span>
                  </div>
                )}

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-2xs font-bold uppercase text-content-muted mb-1">SKU *</label>
                    <input
                      type="text"
                      required
                      placeholder="e.g. SKU-MED-PARA-500"
                      value={newSku}
                      onChange={e => setNewSku(e.target.value)}
                      className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent font-mono"
                    />
                  </div>
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-2xs font-bold uppercase text-content-muted">Category *</label>
                      {isCustomCategory ? (
                        <button
                          type="button"
                          onClick={() => {
                            setIsCustomCategory(false);
                            setNewCategory(existingProductCategories[0] || availableCategories[0] || 'Pharmacy & OTC');
                          }}
                          className="text-3xs text-content-brand hover:underline font-semibold"
                        >
                          Pick Existing
                        </button>
                      ) : null}
                    </div>
                    {!isCustomCategory ? (
                      <select
                        value={newCategory}
                        onChange={e => {
                          if (e.target.value === '__CUSTOM__') {
                            setIsCustomCategory(true);
                            setNewCategory('');
                          } else {
                            setNewCategory(e.target.value);
                          }
                        }}
                        className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent"
                      >
                        {existingProductCategories.length > 0 && (
                          <optgroup label="Existing Store Categories">
                            {existingProductCategories.map(cat => (
                              <option key={`existing-${cat}`} value={cat}>
                                {cat}
                              </option>
                            ))}
                          </optgroup>
                        )}
                        <optgroup label={existingProductCategories.length > 0 ? "Standard Categories" : "Catalog Categories"}>
                          {STANDARD_CATEGORIES.filter(cat => !existingProductCategories.includes(cat)).map(cat => (
                            <option key={`std-${cat}`} value={cat}>
                              {cat}
                            </option>
                          ))}
                        </optgroup>
                        <option value="__CUSTOM__">+ Add New Category...</option>
                      </select>
                    ) : (
                      <div className="space-y-1">
                        <input
                          type="text"
                          required
                          autoFocus
                          placeholder="e.g. Skin Care, Ayurvedic Powders..."
                          value={newCategory}
                          onChange={e => setNewCategory(e.target.value)}
                          className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent"
                        />
                        <p className="text-3xs text-content-muted">Type custom category name or click &apos;Pick Existing&apos; above.</p>
                      </div>
                    )}
                  </div>
                </div>

                <div>
                  <label className="block text-2xs font-bold uppercase text-content-muted mb-1">Product Title *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Paracetamol 500mg Strip of 10"
                    value={newName}
                    onChange={e => setNewName(e.target.value)}
                    className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent"
                  />
                </div>

                <div>
                  <label className="block text-2xs font-bold uppercase text-content-muted mb-1 flex items-center justify-between">
                    <span className="flex items-center space-x-1.5">
                      <FileText className="w-3.5 h-3.5 text-content-muted" />
                      <span>Product Details / Description</span>
                    </span>
                    <span className="text-2xs font-normal text-content-muted lowercase">(optional)</span>
                  </label>
                  <textarea
                    rows={2}
                    placeholder="e.g. Composition, indications, therapeutic usage, dosage instructions..."
                    value={newDescription}
                    onChange={e => setNewDescription(e.target.value)}
                    className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent resize-none"
                  />
                </div>

                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="block text-2xs font-bold uppercase text-content-muted mb-1">Price (₹) *</label>
                    <input
                      type="number"
                      step="0.01"
                      required
                      placeholder="28.50"
                      value={newPrice}
                      onChange={e => setNewPrice(e.target.value)}
                      className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent"
                    />
                  </div>
                  <div>
                    <label className="block text-2xs font-bold uppercase text-content-muted mb-1">MRP (₹)</label>
                    <input
                      type="number"
                      step="0.01"
                      placeholder="35.00"
                      value={newMrp}
                      onChange={e => setNewMrp(e.target.value)}
                      className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent"
                    />
                  </div>
                  <div>
                    <label className="block text-2xs font-bold uppercase text-content-muted mb-1">Initial Stock</label>
                    <input
                      type="number"
                      placeholder="50"
                      value={newStock}
                      onChange={e => setNewStock(e.target.value)}
                      className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent font-mono"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-2xs font-bold uppercase text-content-muted mb-1">Pack Size</label>
                    <input
                      type="text"
                      placeholder="10 Tablets / 100ml"
                      value={newPackSize}
                      onChange={e => setNewPackSize(e.target.value)}
                      className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent"
                    />
                  </div>
                  <div>
                    <label className="block text-2xs font-bold uppercase text-content-muted mb-1">Prescription Rule</label>
                    <select
                      value={newRx}
                      onChange={e => setNewRx(e.target.value)}
                      className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent"
                    >
                      <option value="OTC">OTC (No Rx required)</option>
                      <option value="RX_REQUIRED">Rx Required (Pharmacist approval)</option>
                      <option value="SCHEDULE_H">Schedule H (Strict Rx)</option>
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-2xs font-bold uppercase text-content-muted mb-1 flex items-center justify-between">
                      <span className="flex items-center space-x-1.5">
                        <Calendar className="w-3.5 h-3.5 text-content-muted" />
                        <span>Manufacturing Date</span>
                      </span>
                      <span className="text-3xs text-content-brand font-mono lowercase">dd/mm/yyyy</span>
                    </label>
                    <DatePickerDDMMYYYY
                      value={newMfgDate}
                      onChange={setNewMfgDate}
                      placeholder="DD/MM/YYYY"
                    />
                  </div>
                  <div>
                    <label className="block text-2xs font-bold uppercase text-content-muted mb-1 flex items-center justify-between">
                      <span className="flex items-center space-x-1.5">
                        <Calendar className="w-3.5 h-3.5 text-content-muted" />
                        <span>Expiry Date</span>
                      </span>
                      <span className="text-3xs text-content-brand font-mono lowercase">dd/mm/yyyy</span>
                    </label>
                    <DatePickerDDMMYYYY
                      value={newExpiryDate}
                      onChange={setNewExpiryDate}
                      placeholder="DD/MM/YYYY"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <label className="block text-2xs font-bold uppercase text-content-muted flex items-center space-x-1.5">
                      <ImageIcon className="w-3.5 h-3.5 text-content-brand" />
                      <span>Product Images (Multiple Supported)</span>
                    </label>
                    <button
                      type="button"
                      onClick={handleAddImage}
                      className="inline-flex items-center space-x-1 text-2xs font-bold text-content-brand hover:underline"
                    >
                      <Plus className="w-3 h-3" />
                      <span>Add Another Image</span>
                    </button>
                  </div>
                  <div className="space-y-2 max-h-36 overflow-y-auto pr-1">
                    {newImages.map((imgUrl, idx) => (
                      <div key={idx} className="flex items-center space-x-2">
                        <input
                          type="url"
                          placeholder={idx === 0 ? "Primary image URL (e.g. https://...)" : `Image #${idx + 1} URL`}
                          value={imgUrl}
                          onChange={e => handleImageChange(idx, e.target.value)}
                          className="flex-1 bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs text-content-primary focus:outline-none focus:border-border-accent"
                        />
                        {imgUrl.trim() && (
                          <div className="w-9 h-9 rounded-xl border border-border-default overflow-hidden bg-surface-subtle shrink-0 flex items-center justify-center shadow-sm">
                            <img
                              src={imgUrl.trim()}
                              alt={`Preview ${idx + 1}`}
                              className="w-full h-full object-cover"
                              onError={(e) => {
                                (e.target as HTMLElement).style.display = 'none';
                              }}
                            />
                          </div>
                        )}
                        {newImages.length > 1 && (
                          <button
                            type="button"
                            onClick={() => handleRemoveImage(idx)}
                            className="p-1.5 text-content-muted hover:text-content-danger rounded-lg transition"
                            title="Remove image"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>

                <div className="pt-4 flex items-center justify-end space-x-3 border-t border-border-default shrink-0">
                  <button
                    type="button"
                    onClick={() => setIsAddModalOpen(false)}
                    className="px-4 py-2 rounded-xl text-xs font-semibold text-content-muted hover:bg-surface-subtle transition"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="px-5 py-2 bg-action-speedBg hover:bg-action-speedHover rounded-xl text-xs font-bold text-white shadow-md transition disabled:opacity-50"
                  >
                    {isSubmitting ? 'Registering SKU…' : 'Save Product'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Delete SKU Double Confirmation Modal */}
        {deletingProduct && (
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
            <div className="bg-white border border-border-default rounded-2xl w-full max-w-md overflow-hidden shadow-2xl animate-in fade-in zoom-in duration-150">
              <div className="px-6 py-4 border-b border-border-default flex items-center justify-between bg-surface-subtle/50">
                <div className="flex items-center space-x-2 text-content-danger">
                  <AlertTriangle className="w-5 h-5 shrink-0" />
                  <h3 className="font-bold text-sm">Confirm SKU Deletion</h3>
                </div>
                <button
                  type="button"
                  disabled={isDeleting}
                  onClick={closeDeleteModal}
                  className="text-content-muted hover:text-content-primary p-1 rounded-lg"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleDeleteProduct} className="p-6 space-y-4">
                {deleteError && (
                  <div className="p-3 bg-surface-dangerSubtle border border-border-danger rounded-xl text-content-danger text-xs flex items-center space-x-2 font-semibold">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{deleteError}</span>
                  </div>
                )}
                {deleteSuccess && (
                  <div className="p-3 bg-surface-brandSubtle border border-border-brand rounded-xl text-content-brand text-xs flex items-center space-x-2 font-semibold">
                    <CheckCircle className="w-4 h-4 shrink-0" />
                    <span>SKU successfully deleted from catalog!</span>
                  </div>
                )}

                {/* SKU Info Card */}
                <div className="p-3.5 bg-surface-subtle border border-border-default rounded-xl space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-xs font-black text-content-danger">{deletingProduct.sku}</span>
                    <span className="text-3xs font-bold px-2 py-0.5 rounded-full bg-surface-dangerSubtle text-content-danger border border-border-danger">
                      {deletingProduct.category || 'General'}
                    </span>
                  </div>
                  <div className="font-bold text-xs text-content-primary truncate">{deletingProduct.name}</div>
                  <div className="text-2xs text-content-muted">
                    Pack: {deletingProduct.packSize || '1 Unit'} • Price: ₹{Number(deletingProduct.price).toFixed(2)} • Stock: {deletingProduct.stockCount ?? 0} units
                  </div>
                </div>

                <div className="p-3 bg-surface-dangerSubtle/60 border border-border-danger/60 rounded-xl text-2xs text-content-danger leading-relaxed">
                  <strong>Warning:</strong> This will immediately deactivate this SKU and permanently remove its stock records from the store catalog.
                </div>

                {/* Double Confirmation Steps */}
                <div className="space-y-3 pt-1">
                  {/* Step 1: Checkbox confirmation */}
                  <label className="flex items-start space-x-2.5 p-3 rounded-xl border border-border-default hover:bg-surface-subtle cursor-pointer transition select-none">
                    <input
                      type="checkbox"
                      checked={isDeleteConfirmed}
                      onChange={e => setIsDeleteConfirmed(e.target.checked)}
                      disabled={isDeleting}
                      className="mt-0.5 w-4 h-4 rounded text-action-speedBg focus:ring-border-danger cursor-pointer"
                    />
                    <span className="text-xs text-content-primary font-medium">
                      <span className="font-bold text-content-danger">Confirmation 1:</span> I understand this action permanently removes this SKU and all associated store inventory.
                    </span>
                  </label>

                  {/* Step 2: Type SKU code */}
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between text-2xs text-content-muted">
                      <label className="font-bold uppercase">
                        <span className="text-content-danger">Confirmation 2:</span> Type <span className="font-mono font-bold text-content-danger select-all bg-surface-subtle px-1.5 py-0.5 rounded border border-border-default">{deletingProduct.sku}</span> to verify:
                      </label>
                      <button
                        type="button"
                        onClick={() => setDeleteConfirmationInput(deletingProduct.sku)}
                        className="text-3xs font-semibold text-content-brand hover:underline"
                      >
                        (auto-fill)
                      </button>
                    </div>
                    <input
                      type="text"
                      disabled={isDeleting}
                      placeholder={`Type "${deletingProduct.sku}" to confirm`}
                      value={deleteConfirmationInput}
                      onChange={e => setDeleteConfirmationInput(e.target.value)}
                      className="w-full bg-surface-subtle border border-border-default rounded-xl px-3 py-2 text-xs font-mono text-content-primary focus:outline-none focus:border-border-danger"
                    />
                  </div>
                </div>

                <div className="pt-3 flex items-center justify-end space-x-3 border-t border-border-default">
                  <button
                    type="button"
                    disabled={isDeleting}
                    onClick={closeDeleteModal}
                    className="px-4 py-2 rounded-xl text-xs font-semibold text-content-muted hover:bg-surface-subtle transition"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={!canExecuteDelete || isDeleting}
                    className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-xl text-xs font-bold shadow-sm transition disabled:opacity-40 disabled:cursor-not-allowed flex items-center space-x-1.5"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>{isDeleting ? 'Deleting SKU…' : 'Permanently Delete SKU'}</span>
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
