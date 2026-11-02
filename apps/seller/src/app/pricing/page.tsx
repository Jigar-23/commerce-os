'use client';

import React, { useState } from 'react';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import { IndianRupee, TrendingUp, Sliders, CheckCircle2, Plus, Percent, Edit2, ShieldAlert } from 'lucide-react';

interface PricingRule {
  id: string;
  sku: string;
  product: string;
  category: string;
  basePrice: number;
  sellingPrice: number;
  minMargin: number;
  dynamicPricing: boolean;
  status: 'OPTIMAL' | 'BELOW_MARGIN' | 'PEAK_SURGE';
}

export default function PricingPage() {
  const [pricingRules, setPricingRules] = useState<PricingRule[]>([
    { id: 'pr1', sku: 'MED-PARA-500', product: 'Paracetamol 500mg Extra', category: 'Pharmacy', basePrice: 35.0, sellingPrice: 28.0, minMargin: 20, dynamicPricing: true, status: 'OPTIMAL' },
    { id: 'pr2', sku: 'GRO-OLIVE-1L', product: 'Organic Extra Virgin Olive Oil 1L', category: 'Gourmet', basePrice: 850.0, sellingPrice: 720.0, minMargin: 15, dynamicPricing: true, status: 'OPTIMAL' },
    { id: 'pr3', sku: 'APP-HOODIE-XL', product: 'Urban Streetwear Oversized Hoodie', category: 'Apparel', basePrice: 2999.0, sellingPrice: 2499.0, minMargin: 30, dynamicPricing: false, status: 'OPTIMAL' },
    { id: 'pr4', sku: 'ELE-HEADPHONE-BT', product: 'Smart Wireless Noise-Cancelling Headphones', category: 'Electronics', basePrice: 14999.0, sellingPrice: 12999.0, minMargin: 25, dynamicPricing: true, status: 'OPTIMAL' }
  ]);

  const [selectedRule, setSelectedRule] = useState<PricingRule | null>(null);
  const [editPrice, setEditPrice] = useState<number>(0);
  const [editMargin, setEditMargin] = useState<number>(0);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const handleEditClick = (rule: PricingRule) => {
    setSelectedRule(rule);
    setEditPrice(rule.sellingPrice);
    setEditMargin(rule.minMargin);
  };

  const handleSavePrice = () => {
    if (!selectedRule) return;
    setPricingRules(prev => prev.map(r => r.id === selectedRule.id ? { ...r, sellingPrice: editPrice, minMargin: editMargin } : r));
    setSelectedRule(null);
    setToastMessage('Pricing rule updated successfully');
    setTimeout(() => setToastMessage(null), 3000);
  };

  const toggleDynamicPricing = (id: string) => {
    setPricingRules(prev => prev.map(r => r.id === id ? { ...r, dynamicPricing: !r.dynamicPricing } : r));
  };

  const avgMargin = Math.round(pricingRules.reduce((acc, r) => acc + r.minMargin, 0) / pricingRules.length);
  const activeDynamicCount = pricingRules.filter(r => r.dynamicPricing).length;

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-subtle font-sans overflow-hidden">
        <SellerSidebar activeTab="pricing" />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Header */}
          <div className="sticky top-0 z-10 bg-white/95 backdrop-blur-md border-b border-border-default px-8 py-4 flex items-center justify-between">
            <div>
              <h1 className="text-xl font-black text-content-primary tracking-tight">Pricing & Dynamic Margin Engine</h1>
              <p className="text-xs text-content-secondary">Automated markdown limits, price elasticity safeguards, and margin floor protection</p>
            </div>
            <div className="flex items-center space-x-3">
              <HeaderQuickSearch />
              <button
                onClick={() => alert('New Dynamic Pricing Rule Wizard will open here.')}
                className="px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-xs font-bold text-white flex items-center space-x-2 transition shadow-sm"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Pricing Rule</span>
              </button>
            </div>
          </div>

          <div className="p-8 space-y-6 max-w-7xl">
            {toastMessage && (
              <div className="p-4 rounded-xl bg-surface-brandSubtle border border-border-brand text-content-brand text-sm font-bold flex items-center space-x-2">
                <CheckCircle2 className="w-4 h-4" />
                <span>{toastMessage}</span>
              </div>
            )}

            {/* KPI Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
              <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">Dynamic Engine State</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-brandSubtle text-content-brand flex items-center justify-center font-bold">
                    <TrendingUp className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-primary">{activeDynamicCount} / {pricingRules.length} SKUs</div>
                <p className="text-2xs text-content-secondary">Active algorithmic surge & near-expiry markdowns</p>
              </div>

              <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">Average Margin Floor</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-accentSubtle text-content-accent flex items-center justify-center font-bold">
                    <Percent className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-primary">{avgMargin}% Floor</div>
                <p className="text-2xs text-content-secondary">Hard stop prevents selling below procurement costs</p>
              </div>

              <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">Regulatory Safeguards</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-warningSubtle text-content-warning flex items-center justify-center font-bold">
                    <ShieldAlert className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-primary">DPCO Enforced</div>
                <p className="text-2xs text-content-secondary">Drug Price Control Order ceiling caps strictly observed</p>
              </div>
            </div>

            {/* Pricing Rules Table */}
            <div className="bg-white border border-border-default rounded-2xl overflow-hidden shadow-sm">
              <div className="px-6 py-4 border-b border-border-default flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-black text-content-primary">Configured SKUs ({pricingRules.length})</h2>
                  <p className="text-2xs text-content-muted">Base MSRP vs active live catalog selling price</p>
                </div>
                <span className="text-2xs font-bold text-content-muted">Real-time sync to Checkout Engine</span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-surface-subtle border-b border-border-default text-2xs font-black text-content-secondary uppercase tracking-wider">
                      <th className="py-3 px-6">Product / SKU</th>
                      <th className="py-3 px-4">Category</th>
                      <th className="py-3 px-4 text-right">MSRP (Base)</th>
                      <th className="py-3 px-4 text-right">Selling Price</th>
                      <th className="py-3 px-4 text-center">Min Margin</th>
                      <th className="py-3 px-4 text-center">Dynamic Pricing</th>
                      <th className="py-3 px-6 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border-default text-xs">
                    {pricingRules.map((r) => (
                      <tr key={r.id} className="hover:bg-surface-subtle/50 transition">
                        <td className="py-4 px-6">
                          <div className="font-bold text-content-primary">{r.product}</div>
                          <div className="text-2xs font-mono text-content-muted">{r.sku}</div>
                        </td>
                        <td className="py-4 px-4">
                          <span className="px-2 py-0.5 rounded-full bg-surface-subtle border border-border-default text-2xs font-bold text-content-secondary">
                            {r.category}
                          </span>
                        </td>
                        <td className="py-4 px-4 text-right font-medium text-content-secondary">
                          ₹{r.basePrice.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                        </td>
                        <td className="py-4 px-4 text-right font-black text-content-primary">
                          ₹{r.sellingPrice.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                        </td>
                        <td className="py-4 px-4 text-center">
                          <span className="px-2 py-0.5 rounded-md bg-surface-brandSubtle text-content-brand font-bold text-2xs">
                            {r.minMargin}%
                          </span>
                        </td>
                        <td className="py-4 px-4 text-center">
                          <button
                            onClick={() => toggleDynamicPricing(r.id)}
                            className={`px-3 py-1 rounded-full text-2xs font-bold transition border ${
                              r.dynamicPricing
                                ? 'bg-surface-brandSubtle text-content-brand border-border-brand hover:opacity-80'
                                : 'bg-surface-muted text-content-muted border-border-default hover:opacity-80'
                            }`}
                          >
                            {r.dynamicPricing ? 'Active' : 'Disabled'}
                          </button>
                        </td>
                        <td className="py-4 px-6 text-right">
                          <button
                            onClick={() => handleEditClick(r)}
                            className="p-1.5 rounded-lg border border-border-default hover:bg-surface-muted text-content-secondary hover:text-content-primary transition"
                            title="Edit Pricing"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        {/* Edit Modal */}
        {selectedRule && (
          <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-border-default space-y-4">
              <div className="flex items-center justify-between border-b border-border-default pb-3">
                <h3 className="text-base font-black text-content-primary">Adjust Pricing Rule</h3>
                <button onClick={() => setSelectedRule(null)} className="text-content-muted hover:text-content-primary text-sm font-bold">✕</button>
              </div>

              <div className="space-y-3">
                <div>
                  <label className="text-xs font-bold text-content-secondary">Product</label>
                  <p className="text-sm font-black text-content-primary">{selectedRule.product}</p>
                </div>

                <div>
                  <label className="text-xs font-bold text-content-secondary">Selling Price (₹)</label>
                  <input
                    type="number"
                    value={editPrice}
                    onChange={(e) => setEditPrice(Number(e.target.value))}
                    className="w-full mt-1 px-3 py-2 border border-border-default rounded-xl text-sm font-bold focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-content-secondary">Minimum Margin Target (%)</label>
                  <input
                    type="number"
                    value={editMargin}
                    onChange={(e) => setEditMargin(Number(e.target.value))}
                    className="w-full mt-1 px-3 py-2 border border-border-default rounded-xl text-sm font-bold focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end space-x-3 pt-3 border-t border-border-default">
                <button
                  onClick={() => setSelectedRule(null)}
                  className="px-4 py-2 rounded-xl border border-border-default text-xs font-bold text-content-secondary hover:bg-surface-subtle"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSavePrice}
                  className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-xs font-bold text-white transition shadow-sm"
                >
                  Save Changes
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
