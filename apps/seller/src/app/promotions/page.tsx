'use client';

import React, { useState } from 'react';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import { Tag, Plus, CheckCircle2, Percent, Sparkles, TrendingUp, Users, PauseCircle, PlayCircle } from 'lucide-react';

interface Promotion {
  id: string;
  code: string;
  type: 'PERCENTAGE' | 'FLAT_DISCOUNT' | 'FREE_SHIPPING';
  discountText: string;
  minOrderValue: number;
  usageCount: number;
  maxUsage: number;
  status: 'ACTIVE' | 'PAUSED' | 'EXPIRED';
  validUntil: string;
}

export default function PromotionsPage() {
  const [promotions, setPromotions] = useState<Promotion[]>([
    { id: 'promo1', code: 'WELCOME20', type: 'PERCENTAGE', discountText: '20% OFF (Up to ₹100)', minOrderValue: 299, usageCount: 1420, maxUsage: 5000, status: 'ACTIVE', validUntil: '2026-12-31' },
    { id: 'promo2', code: 'HEALTH30', type: 'FLAT_DISCOUNT', discountText: '₹150 Flat Off Medicines', minOrderValue: 799, usageCount: 890, maxUsage: 2000, status: 'ACTIVE', validUntil: '2026-11-30' },
    { id: 'promo3', code: 'FREESHIP', type: 'FREE_SHIPPING', discountText: 'Free 10-Min SLA Priority Delivery', minOrderValue: 199, usageCount: 3450, maxUsage: 10000, status: 'ACTIVE', validUntil: '2026-12-15' }
  ]);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newCode, setNewCode] = useState('');
  const [newType, setNewType] = useState<'PERCENTAGE' | 'FLAT_DISCOUNT' | 'FREE_SHIPPING'>('PERCENTAGE');
  const [newDiscount, setNewDiscount] = useState('');
  const [newMinOrder, setNewMinOrder] = useState<number>(299);
  const [newMaxUsage, setNewMaxUsage] = useState<number>(1000);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const toggleStatus = (id: string) => {
    setPromotions(prev => prev.map(p => {
      if (p.id !== id) return p;
      const nextStatus = p.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
      return { ...p, status: nextStatus };
    }));
  };

  const handleCreatePromotion = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCode.trim()) return;

    const newPromo: Promotion = {
      id: `promo_${Date.now()}`,
      code: newCode.trim().toUpperCase(),
      type: newType,
      discountText: newDiscount.trim() || (newType === 'PERCENTAGE' ? '10% OFF' : '₹50 OFF'),
      minOrderValue: Number(newMinOrder) || 0,
      usageCount: 0,
      maxUsage: Number(newMaxUsage) || 1000,
      status: 'ACTIVE',
      validUntil: '2026-12-31'
    };

    setPromotions(prev => [newPromo, ...prev]);
    setShowCreateModal(false);
    setNewCode('');
    setNewDiscount('');
    setToastMessage(`Promotion ${newPromo.code} created successfully`);
    setTimeout(() => setToastMessage(null), 3000);
  };

  const totalRedemptions = promotions.reduce((acc, p) => acc + p.usageCount, 0);
  const activeCampaigns = promotions.filter(p => p.status === 'ACTIVE').length;

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-subtle font-sans overflow-hidden">
        <SellerSidebar activeTab="promotions" />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Header */}
          <div className="sticky top-0 z-10 bg-white/95 backdrop-blur-md border-b border-border-default px-8 py-4 flex items-center justify-between">
            <div>
              <h1 className="text-xl font-black text-content-primary tracking-tight">Promotions & Campaigns Engine</h1>
              <p className="text-xs text-content-secondary">High-conversion coupon vouchers, minimum basket incentives, and delivery fee waivers</p>
            </div>
            <div className="flex items-center space-x-3">
              <HeaderQuickSearch />
              <button
                onClick={() => setShowCreateModal(true)}
                className="px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-xs font-bold text-white flex items-center space-x-2 transition shadow-sm"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Create Campaign</span>
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
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">Active Campaigns</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-brandSubtle text-content-brand flex items-center justify-center font-bold">
                    <Sparkles className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-primary">{activeCampaigns} Live</div>
                <p className="text-2xs text-content-secondary">Automated application at user checkout</p>
              </div>

              <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">Total Redemptions</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-accentSubtle text-content-accent flex items-center justify-center font-bold">
                    <Users className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-primary">{totalRedemptions.toLocaleString()} Used</div>
                <p className="text-2xs text-content-secondary">Strict 1-per-customer fraud prevention gate</p>
              </div>

              <div className="p-5 rounded-2xl bg-white border border-border-default shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-content-muted uppercase tracking-wider">Estimated Basket Lift</span>
                  <div className="w-8 h-8 rounded-lg bg-surface-warningSubtle text-content-warning flex items-center justify-center font-bold">
                    <TrendingUp className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-black text-content-primary">+28.4% AOV</div>
                <p className="text-2xs text-content-secondary">Driven by min-basket qualification criteria</p>
              </div>
            </div>

            {/* Promotions Grid */}
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-black text-content-primary">Active Promotional Vouchers ({promotions.length})</h2>
                <span className="text-2xs font-bold text-content-muted">Server Authoritative Pricing Enforced</span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {promotions.map((p) => {
                  const percentUsed = Math.min(100, Math.round((p.usageCount / p.maxUsage) * 100));
                  return (
                    <div
                      key={p.id}
                      className="bg-white border border-border-default rounded-2xl p-6 space-y-4 shadow-sm hover:shadow-md transition relative flex flex-col justify-between"
                    >
                      <div className="space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="px-3 py-1 bg-surface-brandSubtle text-content-brand font-mono font-black rounded-lg text-sm border border-border-brand">
                            {p.code}
                          </span>
                          <span
                            className={`px-2.5 py-0.5 rounded-full text-2xs font-black border ${
                              p.status === 'ACTIVE'
                                ? 'bg-surface-brandSubtle text-content-brand border-border-brand'
                                : 'bg-surface-muted text-content-muted border-border-default'
                            }`}
                          >
                            {p.status}
                          </span>
                        </div>

                        <div>
                          <h3 className="text-base font-black text-content-primary">{p.discountText}</h3>
                          <p className="text-2xs text-content-secondary mt-0.5">Min. Basket Order: ₹{p.minOrderValue} • Valid until {p.validUntil}</p>
                        </div>

                        {/* Progress Bar */}
                        <div className="space-y-1">
                          <div className="flex items-center justify-between text-2xs text-content-muted">
                            <span>Claimed</span>
                            <span className="font-bold text-content-primary">{p.usageCount} / {p.maxUsage} ({percentUsed}%)</span>
                          </div>
                          <div className="w-full h-1.5 bg-surface-subtle rounded-full overflow-hidden border border-border-default">
                            <div
                              className="h-full bg-slate-900 rounded-full transition-all duration-300"
                              style={{ width: `${percentUsed}%` }}
                            />
                          </div>
                        </div>
                      </div>

                      <div className="border-t border-border-default pt-3 flex items-center justify-between">
                        <span className="text-2xs font-medium text-content-muted">Type: {p.type}</span>
                        <button
                          onClick={() => toggleStatus(p.id)}
                          className="flex items-center space-x-1 text-2xs font-bold text-content-secondary hover:text-content-primary"
                        >
                          {p.status === 'ACTIVE' ? (
                            <>
                              <PauseCircle className="w-3.5 h-3.5" />
                              <span>Pause</span>
                            </>
                          ) : (
                            <>
                              <PlayCircle className="w-3.5 h-3.5" />
                              <span>Resume</span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>

        {/* Create Campaign Modal */}
        {showCreateModal && (
          <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-border-default space-y-4">
              <div className="flex items-center justify-between border-b border-border-default pb-3">
                <h3 className="text-base font-black text-content-primary">Create New Promotion</h3>
                <button onClick={() => setShowCreateModal(false)} className="text-content-muted hover:text-content-primary text-sm font-bold">✕</button>
              </div>

              <form onSubmit={handleCreatePromotion} className="space-y-3">
                <div>
                  <label className="text-xs font-bold text-content-secondary">Coupon Voucher Code</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. FESTIVE50"
                    value={newCode}
                    onChange={(e) => setNewCode(e.target.value.toUpperCase())}
                    className="w-full mt-1 px-3 py-2 border border-border-default rounded-xl text-sm font-mono font-bold focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-content-secondary">Discount Type</label>
                  <select
                    value={newType}
                    onChange={(e) => setNewType(e.target.value as any)}
                    className="w-full mt-1 px-3 py-2 border border-border-default rounded-xl text-sm font-bold focus:outline-none focus:ring-2 focus:ring-slate-900"
                  >
                    <option value="PERCENTAGE">Percentage Discount (%)</option>
                    <option value="FLAT_DISCOUNT">Flat Rupee Amount (₹)</option>
                    <option value="FREE_SHIPPING">Free Priority Delivery (100% SLA fee waiver)</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-bold text-content-secondary">Display Offer Text</label>
                  <input
                    type="text"
                    placeholder="e.g. 25% OFF Up to ₹150"
                    value={newDiscount}
                    onChange={(e) => setNewDiscount(e.target.value)}
                    className="w-full mt-1 px-3 py-2 border border-border-default rounded-xl text-sm font-bold focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs font-bold text-content-secondary">Min Order (₹)</label>
                    <input
                      type="number"
                      value={newMinOrder}
                      onChange={(e) => setNewMinOrder(Number(e.target.value))}
                      className="w-full mt-1 px-3 py-2 border border-border-default rounded-xl text-sm font-bold focus:outline-none focus:ring-2 focus:ring-slate-900"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-content-secondary">Max Redemptions</label>
                    <input
                      type="number"
                      value={newMaxUsage}
                      onChange={(e) => setNewMaxUsage(Number(e.target.value))}
                      className="w-full mt-1 px-3 py-2 border border-border-default rounded-xl text-sm font-bold focus:outline-none focus:ring-2 focus:ring-slate-900"
                    />
                  </div>
                </div>

                <div className="flex items-center justify-end space-x-3 pt-3 border-t border-border-default">
                  <button
                    type="button"
                    onClick={() => setShowCreateModal(false)}
                    className="px-4 py-2 rounded-xl border border-border-default text-xs font-bold text-content-secondary hover:bg-surface-subtle"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-xs font-bold text-white transition shadow-sm"
                  >
                    Create Promotion
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
