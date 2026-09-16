'use client';

import React, { useEffect, useState } from 'react';
import SellerSidebar from '../../components/SellerSidebar';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import { Layers, Search, CheckCircle2, RefreshCw, RotateCcw, AlertCircle } from 'lucide-react';
import SellerAuthGuard from '../../components/SellerAuthGuard';

interface InventoryItem {
  id: string;
  sku: string;
  productId: string;
  name: string;
  category?: string;
  packSize?: string;
  price: number;
  onHand: number;
  reserved: number;
  available: number;
  stockCount: number;
}

interface LastAdjustment {
  adjustmentId: string;
  sku: string;
  name: string;
  delta: number;
  reason: string;
  timestamp: string;
}

export default function DedicatedInventoryPage() {
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [customAddInputs, setCustomAddInputs] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [inventoryQuery, setInventoryQuery] = useState('');
  const [statusMessage, setStatusMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Ledger-Based Undo System State
  const [lastAdjustment, setLastAdjustment] = useState<LastAdjustment | null>(null);
  const [isUndoing, setIsUndoing] = useState(false);

  const { session, storeName } = useSellerSession();

  const fetchInventory = async () => {
    if (!session?.token) return;
    setIsLoading(true);
    try {
      const res = await sellerApi.get('/api/v1/catalog/seller/inventory');
      if (res.ok && res.data) {
        const items = Array.isArray(res.data) ? res.data : (res.data.items || res.data.content || []);
        const normalized = items.map((i: any) => ({
          id: i.id || i.sku || i.productId,
          sku: i.sku || i.id,
          productId: i.productId || i.product_id || i.id || i.sku,
          name: i.name || 'Product',
          category: i.category || 'General',
          packSize: i.packSize || '1 Unit',
          price: Number(i.discountedPrice ?? i.price ?? 0),
          onHand: Number(i.onHand ?? i.stockCount ?? 0),
          reserved: Number(i.reserved ?? i.reservedCount ?? 0),
          available: Number(i.available ?? (Math.max(0, (i.onHand ?? i.stockCount ?? 0) - (i.reserved ?? 0)))),
          stockCount: Number(i.stockCount ?? i.onHand ?? 0),
        }));
        setInventory(normalized);
      } else {
        showToast(res.error || 'Failed to retrieve inventory ledger.', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error loading inventory', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (session?.token) {
      fetchInventory();
    }
  }, [session?.token]);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setStatusMessage({ text, type });
    setTimeout(() => setStatusMessage(null), 4000);
  };

  // Stage quick quantity (+10, +50) into the input field without immediately mutating stock
  const handleAddQuickQty = (itemId: string, amount: number) => {
    setCustomAddInputs(prev => {
      const raw = prev[itemId] || '';
      const current = parseInt(raw, 10);
      const next = (isNaN(current) ? 0 : current) + amount;
      return { ...prev, [itemId]: String(next) };
    });
  };

  // Transactional Ledger Stock Adjustment on "Apply"
  const handleApplyAdjustment = async (item: InventoryItem) => {
    const raw = customAddInputs[item.id];
    if (!raw || raw.trim() === '') {
      showToast('Enter or select a quantity first using +10, +50 or the input field.', 'error');
      return;
    }
    const delta = parseInt(raw, 10);
    if (isNaN(delta) || delta === 0) {
      showToast('Enter a valid non-zero adjustment delta.', 'error');
      return;
    }

    const previousInventory = [...inventory];
    const reason = delta > 0 ? 'SELLER_RESTOCK' : 'SELLER_ADJUSTMENT';

    // 1. INSTANT OPTIMISTIC UI UPDATE (0ms latency)
    setInventory(prev =>
      prev.map(p =>
        (p.id === item.id || p.sku === item.sku)
          ? {
              ...p,
              onHand: Math.max(0, p.onHand + delta),
              available: Math.max(0, p.onHand + delta - p.reserved),
              stockCount: Math.max(0, p.stockCount + delta),
            }
          : p
      )
    );

    // 2. Clear staged input immediately
    setCustomAddInputs(prev => ({ ...prev, [item.id]: '' }));

    // 3. Show instant feedback toast
    showToast(`Adjusting stock for ${item.name} (${delta > 0 ? '+' : ''}${delta} units)…`, 'success');

    // 4. Asynchronous backend persistence
    try {
      const res = await sellerApi.post('/api/v1/catalog/inventory/adjust', {
        productId: item.productId,
        sku: item.sku,
        delta,
        reason,
        storeId: session?.storeId || 'store_rewari_hub_01',
      });

      if (res.ok && res.data) {
        const adjustmentId = res.data.adjustmentId || 'adj_' + Date.now();
        setLastAdjustment({
          adjustmentId,
          sku: item.sku,
          name: item.name,
          delta,
          reason,
          timestamp: new Date().toLocaleTimeString(),
        });
        showToast(`Stock for ${item.name} adjusted by ${delta > 0 ? '+' : ''}${delta}`, 'success');
      } else {
        // Rollback optimistic update on error
        setInventory(previousInventory);
        setCustomAddInputs(prev => ({ ...prev, [item.id]: String(delta) }));
        showToast(res.error || (res as any).message || 'Failed to apply inventory mutation.', 'error');
      }
    } catch (e: any) {
      // Rollback optimistic update on network exception
      setInventory(previousInventory);
      setCustomAddInputs(prev => ({ ...prev, [item.id]: String(delta) }));
      showToast(e.message || 'Network error adjusting stock.', 'error');
    }
  };

  const handleUndoAdjustment = async () => {
    if (!lastAdjustment || isUndoing) return;
    setIsUndoing(true);
    try {
      const targetItem = inventory.find(i => i.sku === lastAdjustment.sku);
      if (!targetItem) {
        showToast('SKU not found for undo action.', 'error');
        setIsUndoing(false);
        return;
      }

      const reverseDelta = -lastAdjustment.delta;
      const previousInventory = [...inventory];

      // Instant optimistic UI update for undo rollback
      setInventory(prev =>
        prev.map(p =>
          p.sku === lastAdjustment.sku
            ? {
                ...p,
                onHand: Math.max(0, p.onHand + reverseDelta),
                available: Math.max(0, p.onHand + reverseDelta - p.reserved),
                stockCount: Math.max(0, p.stockCount + reverseDelta),
              }
            : p
        )
      );

      const res = await sellerApi.post('/api/v1/catalog/inventory/adjust/undo', {
        adjustmentId: lastAdjustment.adjustmentId,
      });

      if (res.ok) {
        showToast(`Undid adjustment for ${lastAdjustment.name} (${reverseDelta > 0 ? '+' : ''}${reverseDelta})`, 'success');
        setLastAdjustment(null);
      } else {
        // Revert optimistic undo
        setInventory(previousInventory);
        showToast(res.error || 'Failed to rollback ledger transaction.', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error executing rollback.', 'error');
    } finally {
      setIsUndoing(false);
    }
  };

  const filteredInventory = inventory.filter(item =>
    item.name.toLowerCase().includes(inventoryQuery.toLowerCase()) ||
    item.sku.toLowerCase().includes(inventoryQuery.toLowerCase()) ||
    (item.category && item.category.toLowerCase().includes(inventoryQuery.toLowerCase()))
  );

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-surface-canvas text-content-primary font-sans antialiased overflow-hidden">
        {/* Sidebar Navigation */}
        <SellerSidebar
          activeTab="inventory"
          inventoryCount={inventory.length}
          onRefresh={fetchInventory}
          isLoading={isLoading}
        />

        {/* Main Content Area */}
        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Top Header */}
          <header className="h-16 bg-white border-b border-border-default px-8 flex items-center justify-between shrink-0">
            <div className="flex items-center space-x-3">
              <span className="px-3 py-1 rounded-full bg-surface-brandSubtle text-content-brand text-xs font-black border border-border-brandSubtle flex items-center space-x-1.5">
                <Layers className="w-3.5 h-3.5" />
                <span>Stock Inventory</span>
              </span>
              <span className="text-content-muted">/</span>
              <span className="text-xs font-bold text-content-primary" suppressHydrationWarning>Rewari Central Hub</span>
              <span className="text-2xs text-content-muted">({inventory.length} stocked SKUs)</span>
            </div>

            <div className="flex items-center space-x-3">
              <HeaderQuickSearch onSelectOrder={() => {}} />
              <button
                onClick={fetchInventory}
                disabled={isLoading}
                className="p-2 bg-white hover:bg-surface-subtle border border-border-default rounded-xl text-content-muted transition shadow-sm"
                title="Refresh Inventory"
              >
                <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-content-accent' : ''}`} />
              </button>
            </div>
          </header>

          <main className="p-8 space-y-6 flex-1">
            {/* Status Toast */}
            {statusMessage && (
              <div className={`p-4 rounded-2xl flex items-center space-x-2 text-xs font-semibold shadow-sm ${
                statusMessage.type === 'success'
                  ? 'bg-surface-brandSubtle border border-border-brandSubtle text-content-brand'
                  : 'bg-surface-dangerSubtle border border-border-danger text-content-danger'
              }`}>
                {statusMessage.type === 'success' ? (
                  <CheckCircle2 className="w-4 h-4 shrink-0" />
                ) : (
                  <AlertCircle className="w-4 h-4 shrink-0" />
                )}
                <span>{statusMessage.text}</span>
              </div>
            )}

            {/* Undo Notification Bar */}
            {lastAdjustment && (
              <div className="p-4 bg-surface-accentSubtle border border-border-accent rounded-2xl flex items-center justify-between shadow-sm">
                <div className="flex items-center space-x-3 text-xs text-content-accent">
                  <span className="font-bold">Last Adjustment:</span>
                  <span>{lastAdjustment.sku} ({lastAdjustment.delta > 0 ? '+' : ''}{lastAdjustment.delta} units)</span>
                  <span className="text-2xs text-content-muted font-mono">at {lastAdjustment.timestamp}</span>
                </div>
                <button
                  onClick={handleUndoAdjustment}
                  disabled={isUndoing}
                  className="flex items-center space-x-1.5 px-3.5 py-1.5 bg-action-speedBg hover:bg-action-speedHover text-white rounded-xl text-xs font-bold shadow transition"
                >
                  <RotateCcw className={`w-3.5 h-3.5 ${isUndoing ? 'animate-spin' : ''}`} />
                  <span>{isUndoing ? 'Reverting…' : 'Undo Adjustment'}</span>
                </button>
              </div>
            )}

            {/* Search & Filter Bar */}
            <div className="flex items-center justify-between">
              <div className="relative flex-1 max-w-md">
                <Search className="w-4 h-4 absolute left-3.5 top-3 text-content-secondary" />
                <input
                  type="text"
                  placeholder="Filter by SKU or medicine title…"
                  value={inventoryQuery}
                  onChange={e => setInventoryQuery(e.target.value)}
                  className="w-full bg-white border border-border-default rounded-xl pl-10 pr-4 py-2 text-xs text-content-primary placeholder:text-content-muted focus:outline-none focus:border-border-accent shadow-sm"
                />
              </div>
              <div className="text-xs text-content-muted font-medium">
                Showing {filteredInventory.length} of {inventory.length} SKUs in stock
              </div>
            </div>

            {/* Inventory Table */}
            <div className="bg-white border border-border-default rounded-2xl overflow-hidden shadow-sm">
              <table className="w-full text-left text-sm text-content-secondary">
                <thead className="bg-surface-subtle text-content-muted uppercase text-2xs font-bold tracking-wider border-b border-border-default">
                  <tr>
                    <th className="px-6 py-4">SKU / Item</th>
                    <th className="px-4 py-4">Category</th>
                    <th className="px-4 py-4">Unit Price</th>
                    <th className="px-4 py-4">On Hand</th>
                    <th className="px-4 py-4">Reserved</th>
                    <th className="px-4 py-4">Available</th>
                    <th className="px-6 py-4 text-right">Quick Stock Adjustment</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-default">
                  {isLoading ? (
                    <tr>
                      <td colSpan={7} className="px-6 py-12 text-center text-content-muted">
                        <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-content-accent" />
                        <span>Loading authoritative inventory records…</span>
                      </td>
                    </tr>
                  ) : filteredInventory.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-6 py-12 text-center text-content-muted">
                        <Layers className="w-8 h-8 mx-auto mb-2 text-content-secondary" />
                        <span>No inventory items match your search.</span>
                      </td>
                    </tr>
                  ) : (
                    filteredInventory.map(item => (
                      <tr key={item.id} className="hover:bg-surface-subtle/50 transition">
                        <td className="px-6 py-4">
                          <div className="font-mono text-xs font-bold text-content-accent">{item.sku}</div>
                          <div className="font-bold text-content-primary text-sm">{item.name}</div>
                          <div className="text-2xs text-content-muted">{item.packSize}</div>
                        </td>
                        <td className="px-4 py-4 text-xs text-content-muted">{item.category}</td>
                        <td className="px-4 py-4 font-bold text-content-primary">₹{Number(item.price || 0).toFixed(2)}</td>
                        <td className="px-4 py-4 font-mono font-bold text-content-primary">{item.onHand}</td>
                        <td className="px-4 py-4 font-mono text-content-warning font-semibold">
                          {item.reserved > 0 ? `${item.reserved} held` : '0'}
                        </td>
                        <td className="px-4 py-4">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-mono font-bold ${
                            item.available > 10
                              ? 'bg-surface-brandSubtle text-content-brand border border-border-brandSubtle'
                              : item.available > 0
                              ? 'bg-surface-warningSubtle text-content-warning border border-border-warning'
                              : 'bg-surface-dangerSubtle text-content-danger border border-border-danger'
                          }`}>
                            {item.available} units
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <div className="flex items-center justify-end space-x-2">
                            <button
                              type="button"
                              onClick={() => handleAddQuickQty(item.id, 10)}
                              className="px-2.5 py-1 bg-surface-subtle hover:bg-surface-accentSubtle border border-border-default rounded-lg text-xs font-bold text-content-accent transition active:scale-95 shadow-2xs"
                              title="Stage +10 units (Click Apply to save)"
                            >
                              +10
                            </button>
                            <button
                              type="button"
                              onClick={() => handleAddQuickQty(item.id, 50)}
                              className="px-2.5 py-1 bg-surface-subtle hover:bg-surface-accentSubtle border border-border-default rounded-lg text-xs font-bold text-content-accent transition active:scale-95 shadow-2xs"
                              title="Stage +50 units (Click Apply to save)"
                            >
                              +50
                            </button>

                            <div className="flex items-center space-x-1.5">
                              <div className="relative">
                                <input
                                  type="number"
                                  placeholder="±Qty"
                                  value={customAddInputs[item.id] || ''}
                                  onChange={e => setCustomAddInputs({ ...customAddInputs, [item.id]: e.target.value })}
                                  onKeyDown={e => {
                                    if (e.key === 'Enter') {
                                      handleApplyAdjustment(item);
                                    }
                                  }}
                                  className={`w-18 bg-white border rounded-lg px-2 py-1 text-xs text-center font-mono shadow-sm transition ${
                                    customAddInputs[item.id]
                                      ? 'border-border-accent ring-1 ring-border-accent text-content-primary font-bold'
                                      : 'border-border-default text-content-primary'
                                  }`}
                                />
                                {customAddInputs[item.id] && (
                                  <button
                                    type="button"
                                    onClick={() => setCustomAddInputs(prev => ({ ...prev, [item.id]: '' }))}
                                    className="absolute -top-1.5 -right-1.5 w-4 h-4 rounded-full bg-surface-subtle border border-border-default text-content-muted hover:text-content-danger flex items-center justify-center text-3xs font-bold shadow"
                                    title="Clear staged quantity"
                                  >
                                    ×
                                  </button>
                                )}
                              </div>
                              <button
                                type="button"
                                onClick={() => handleApplyAdjustment(item)}
                                className={`px-3 py-1 rounded-lg text-xs font-bold transition shadow-sm ${
                                  customAddInputs[item.id] && parseInt(customAddInputs[item.id] || '0', 10) !== 0
                                    ? 'bg-action-speedBg hover:bg-action-speedHover text-white shadow-md'
                                    : 'bg-surface-subtle text-content-muted border border-border-default hover:bg-surface-subtle/80'
                                }`}
                              >
                                Apply
                              </button>
                            </div>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </main>
        </div>
      </div>
    </SellerAuthGuard>
  );
}
