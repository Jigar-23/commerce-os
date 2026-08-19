'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import { Package, Plus, Search, RefreshCw, AlertCircle, CheckCircle } from 'lucide-react';

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
  inStock?: boolean;
  rxRequirement?: string;
  coldChainRequired?: boolean;
}

export default function ProductsPage() {
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('ALL');

  // Modal State
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formSuccess, setFormSuccess] = useState(false);

  // Form Fields
  const [newSku, setNewSku] = useState('');
  const [newName, setNewName] = useState('');
  const [newPackSize, setNewPackSize] = useState('');
  const [newCategory, setNewCategory] = useState('Pharmacy & OTC');
  const [newPrice, setNewPrice] = useState('');
  const [newMrp, setNewMrp] = useState('');
  const [newStock, setNewStock] = useState('50');
  const [newRx, setNewRx] = useState('OTC');

  const { session, storeName } = useSellerSession();

  const loadProducts = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await sellerApi.get<ProductItem[]>('/api/v1/catalog/products');
      if (res.ok && Array.isArray(res.data)) {
        setProducts(res.data);
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
    loadProducts();
  }, []);

  const handleCreateProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setFormSuccess(false);

    if (!newSku.trim() || !newName.trim() || !newPrice.trim()) {
      setFormError('SKU, Product Name, and Price are strictly required.');
      return;
    }

    const priceNum = parseFloat(newPrice);
    const mrpNum = newMrp.trim() ? parseFloat(newMrp) : priceNum;
    const stockNum = parseInt(newStock, 10) || 0;

    if (isNaN(priceNum) || priceNum <= 0) {
      setFormError('Please enter a valid price greater than 0.');
      return;
    }

    setIsSubmitting(true);
    try {
      const payload = {
        sku: newSku.trim(),
        name: newName.trim(),
        packSize: newPackSize.trim() || '1 Unit',
        category: newCategory,
        price: priceNum,
        mrp: mrpNum,
        discountedPrice: priceNum,
        stockCount: stockNum,
        rxRequirement: newRx,
        storeId: session.storeId
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
          setNewPrice('');
          setNewMrp('');
          setNewStock('50');
          loadProducts();
        }, 1000);
      } else {
        setFormError(res.error || 'Failed to create product in catalog.');
      }
    } catch (err: any) {
      setFormError(err.message || 'Network error while adding product.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const categories = ['ALL', ...Array.from(new Set(products.map(p => p.category).filter(Boolean)))];

  const filteredProducts = products.filter(p => {
    const matchesSearch = p.name.toLowerCase().includes(searchQuery.toLowerCase()) || p.sku.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = categoryFilter === 'ALL' || p.category === categoryFilter;
    return matchesSearch && matchesCategory;
  });

  return (
    <div className="min-h-screen bg-surface-inverse text-white p-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Navigation & Header */}
        <div className="flex items-center justify-between border-b border-border-strong pb-4">
          <div>
            <div className="flex items-center space-x-2">
              <span className="inline-flex items-center px-2 py-0.5 rounded text-2xs font-extrabold bg-surface-accentSubtle text-content-accent border border-border-accent" suppressHydrationWarning>
                {storeName}
              </span>
            </div>
            <h1 className="text-2xl font-black text-white mt-1">Catalog & Products</h1>
            <p className="text-xs text-content-muted">Authoritative Catalog Repository • {products.length} registered SKUs</p>
          </div>
          <div className="flex items-center space-x-3 text-sm">
            <Link href="/" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Dashboard</Link>
            <Link href="/orders" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Orders</Link>
            <Link href="/products" className="px-3 py-1.5 rounded-lg bg-action-speedBg font-bold">Catalog</Link>
            <Link href="/inventory" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">Inventory</Link>
            <Link href="/cod" className="px-3 py-1.5 rounded-lg bg-surface-inverse hover:bg-surface-inverse">COD</Link>
          </div>
        </div>

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
                className="w-full bg-surface-inverse border border-border-strong rounded-xl pl-10 pr-4 py-2 text-xs text-white placeholder:text-content-muted focus:outline-none focus:border-border-accent"
              />
            </div>
            <select
              value={categoryFilter}
              onChange={e => setCategoryFilter(e.target.value)}
              className="bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-content-muted focus:outline-none focus:border-border-accent"
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
              className="p-2.5 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-xl text-content-muted transition"
              title="Refresh Catalog"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
            <button
              onClick={() => setIsAddModalOpen(true)}
              className="flex items-center space-x-2 px-4 py-2 bg-action-primaryBg hover:bg-action-primaryHover rounded-xl text-sm font-bold text-white shadow-lg shadow-subtle transition"
            >
              <Plus className="w-4 h-4" />
              <span>Add New Product</span>
            </button>
          </div>
        </div>

        {error && (
          <div className="p-4 bg-surface-dangerSubtle border border-border-danger rounded-2xl flex items-center space-x-3 text-content-danger text-xs">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Products Table */}
        <div className="bg-surface-inverse border border-border-strong rounded-2xl overflow-hidden shadow-xl">
          <table className="w-full text-left text-sm text-content-muted">
            <thead className="bg-surface-inverse text-content-muted uppercase text-2xs font-bold tracking-wider border-b border-border-strong">
              <tr>
                <th className="px-6 py-4">SKU / Item</th>
                <th className="px-6 py-4">Category</th>
                <th className="px-6 py-4">Pack Size</th>
                <th className="px-6 py-4">Unit Price</th>
                <th className="px-6 py-4">Stock</th>
                <th className="px-6 py-4">Rx Required</th>
                <th className="px-6 py-4 text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-strong/60">
              {isLoading ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-content-muted">
                    <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-content-accent" />
                    <span>Loading products from repository…</span>
                  </td>
                </tr>
              ) : filteredProducts.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-content-muted">
                    <Package className="w-8 h-8 mx-auto mb-2 text-content-secondary" />
                    <span>No products found matching your filter criteria.</span>
                  </td>
                </tr>
              ) : (
                filteredProducts.map(p => (
                  <tr key={p.id || p.sku} className="hover:bg-surface-inverse/30 transition">
                    <td className="px-6 py-4">
                      <div className="font-mono text-xs font-bold text-content-accent">{p.sku}</div>
                      <div className="font-bold text-white text-sm">{p.name}</div>
                    </td>
                    <td className="px-6 py-4 text-xs text-content-muted">{p.category || 'General'}</td>
                    <td className="px-6 py-4 text-xs text-content-muted">{p.packSize || '1 Unit'}</td>
                    <td className="px-6 py-4">
                      <div className="font-bold text-content-brand">₹{(p.discountedPrice ?? p.price ?? 0).toFixed(2)}</div>
                      {p.mrp && p.mrp > (p.discountedPrice ?? p.price) && (
                        <div className="text-2xs text-content-secondary line-through">₹{p.mrp.toFixed(2)}</div>
                      )}
                    </td>
                    <td className="px-6 py-4 font-mono text-xs">
                      {p.stockCount ?? 0} units
                    </td>
                    <td className="px-6 py-4">
                      {p.rxRequirement && p.rxRequirement !== 'OTC' ? (
                        <span className="px-2 py-0.5 rounded text-2xs font-bold bg-surface-warningSubtle text-content-warning border border-border-warning">
                          {p.rxRequirement}
                        </span>
                      ) : (
                        <span className="text-2xs text-content-secondary">OTC</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <span className={`px-2.5 py-1 rounded-full text-2xs font-extrabold ${
                        (p.stockCount ?? 0) > 0
                          ? 'bg-surface-brandSubtle text-content-brand border border-border-brand'
                          : 'bg-surface-dangerSubtle text-content-danger border border-border-danger'
                      }`}>
                        {(p.stockCount ?? 0) > 0 ? 'AVAILABLE' : 'OUT OF STOCK'}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Add Product Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-surface-inverse border border-border-strong rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl">
            <div className="px-6 py-4 border-b border-border-strong flex items-center justify-between">
              <h3 className="font-bold text-white text-base">Add New Product to Catalog</h3>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="text-content-muted hover:text-white text-xl font-bold"
              >
                ×
              </button>
            </div>

            <form onSubmit={handleCreateProduct} className="p-6 space-y-4">
              {formError && (
                <div className="p-3 bg-surface-dangerSubtle border border-border-danger rounded-xl text-content-danger text-xs flex items-center space-x-2">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{formError}</span>
                </div>
              )}
              {formSuccess && (
                <div className="p-3 bg-surface-brandSubtle border border-border-brand rounded-xl text-content-brand text-xs flex items-center space-x-2">
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
                    className="w-full bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-border-accent font-mono"
                  />
                </div>
                <div>
                  <label className="block text-2xs font-bold uppercase text-content-muted mb-1">Category</label>
                  <input
                    type="text"
                    placeholder="Pharmacy & OTC"
                    value={newCategory}
                    onChange={e => setNewCategory(e.target.value)}
                    className="w-full bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-border-accent"
                  />
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
                  className="w-full bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-border-accent"
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
                    className="w-full bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-border-accent"
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
                    className="w-full bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-border-accent"
                  />
                </div>
                <div>
                  <label className="block text-2xs font-bold uppercase text-content-muted mb-1">Initial Stock</label>
                  <input
                    type="number"
                    placeholder="50"
                    value={newStock}
                    onChange={e => setNewStock(e.target.value)}
                    className="w-full bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-border-accent font-mono"
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
                    className="w-full bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-border-accent"
                  />
                </div>
                <div>
                  <label className="block text-2xs font-bold uppercase text-content-muted mb-1">Prescription Rule</label>
                  <select
                    value={newRx}
                    onChange={e => setNewRx(e.target.value)}
                    className="w-full bg-surface-inverse border border-border-strong rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-border-accent"
                  >
                    <option value="OTC">OTC (No Rx required)</option>
                    <option value="RX_REQUIRED">Rx Required (Pharmacist approval)</option>
                    <option value="SCHEDULE_H">Schedule H (Strict Rx)</option>
                  </select>
                </div>
              </div>

              <div className="pt-4 flex items-center justify-end space-x-3 border-t border-border-strong">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-content-muted hover:bg-surface-inverse transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-5 py-2 bg-action-primaryBg hover:bg-action-primaryHover rounded-xl text-xs font-bold text-white shadow-lg transition"
                >
                  {isSubmitting ? 'Registering SKU…' : 'Save Product'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
