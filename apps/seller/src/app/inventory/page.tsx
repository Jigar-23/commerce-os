'use client';

import React, { useEffect, useState } from 'react';
import SellerSidebar from '../../components/SellerSidebar';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import { Layers, Search, Plus, CheckCircle2, RefreshCw, RotateCcw, AlertTriangle, ArrowUpDown } from 'lucide-react';

interface InventoryItem {
  id: string;
  sku: string;
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
    setIsLoading(true);
    try {
      const res = await sellerApi.get('/api/v1/catalog/seller/inventory');
      if (res.ok && res.data) {
        const items = Array.isArray(res.data) ? res.data : (res.data.items || res.data.content || []);
        const normalized = items.map((i: any) => ({
          id: i.id || i.sku,
          sku: i.sku || i.id,
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
    fetchInventory();
  }, []);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setStatusMessage({ text, type });
    setTimeout(() => setStatusMessage(null), 4000);
  };

  // Transactional Ledger Stock Adjustment
  const handleAdjustStock = async (item: InventoryItem, delta: number, reason: string = 'MANUAL_REPLENISHMENT') => {
    if (delta === 0) return;

    try {
      const res = await sellerApi.post('/api/v1/catalog/inventory/adjust', {
        sku: item.sku,
        delta,
        reason,
        storeId: session.storeId,
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
        showToast(`Stock for ${item.sku} adjusted by ${delta > 0 ? '+' : ''}${delta} units.`);
        fetchInventory();
      } else {
        showToast(res.error || 'Failed to record stock adjustment', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Error recording inventory adjustment', 'error');
    }
  };

  // Reverse Specific Adjustment by ID (Transactional Reverse Adjustment)
  const handleUndoAdjustment = async () => {
    if (!lastAdjustment || isUndoing) return;

    setIsUndoing(true);
    try {
      const res = await sellerApi.post('/api/v1/catalog/inventory/adjust/undo', {
        adjustmentId: lastAdjustment.adjustmentId,
        sku: lastAdjustment.sku,
        reverseDelta: -lastAdjustment.delta,
        reason: `UNDO_ADJUSTMENT_${lastAdjustment.adjustmentId}`,
        storeId: session.storeId,
      });

      if (res.ok) {
        showToast(`Adjustment reverted: ${lastAdjustment.sku} adjusted by ${-lastAdjustment.delta > 0 ? '+' : ''}${-lastAdjustment.delta}`);
        setLastAdjustment(null);
        fetchInventory();
      } else {
        showToast(res.error || 'Failed to revert adjustment', 'error');
      }
    } catch (e: any) {
      showToast(e.message || 'Network error reverting adjustment', 'error');
    } finally {
      setIsUndoing(false);
    }
  };

  const handleManualInputSubmit = (item: InventoryItem) => {
    const rawVal = customAddInputs[item.id] || '0';
    const amount = parseInt(rawVal, 10);
    if (isNaN(amount) || amount === 0) {
      showToast('Please enter a valid stock increment or decrement number', 'error');
      return;
    }
    handleAdjustStock(item, amount, 'MANUAL_BATCH_ENTRY');
    setCustomAddInputs({ ...customAddInputs, [item.id]: '' });
  };

  const filteredInventory = inventory.filter(item =>
    item.name.toLowerCase().includes(inventoryQuery.toLowerCase()) ||
    item.sku.toLowerCase().includes(inventoryQuery.toLowerCase()) ||
    (item.category && item.category.toLowerCase().includes(inventoryQuery.toLowerCase()))
  );

  return (
    <div className="flex h-screen bg-surface-inverse text-content-inverse font-sans overflow-hidden">
      {/* Sidebar Navigation */}
      <SellerSidebar activeTab="inventory" inventoryCount={inventory.length} onRefresh={fetchInventory} isLoading={isLoading} />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* Top Header */}
        <header className="h-16 bg-surface-inverse border-b border-border-strong px-6 flex items-center justify-between shrink-0">
          <div className="flex items-center space-x-3">
            <Layers className="w-5 h-5 text-content-accent" />
            <div>
              <h2 className="text-sm font-bold text-white">Inventory & Stock Ledger</h2>
              <p className="text-2xs text-content-muted" suppressHydrationWarning>{storeName} • Real-time stock reservation sync</p>
            </div>
          </div>

          <div className="flex items-center space-x-3">
            <HeaderQuickSearch onSelectOrder={() => {}} />
            <button
              onClick={fetchInventory}
              disabled={isLoading}
              className="p-2 bg-surface-inverse hover:bg-surface-inverse rounded-xl text-content-muted transition"
              title="Refresh Inventory"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </header>

        {/* Status Toast */}
        {statusMessage && (
          <div className={`mx-6 mt-4 p-3 rounded-xl flex items-center space-x-2 text-xs font-semibold ${
            statusMessage.type === 'success'
              ? 'bg-surface-brandSubtle border border-border-brand text-content-brand'
              : 'bg-surface-dangerSubtle border border-border-danger text-content-danger'
          }`}>
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{statusMessage.text}</span>
          </div>
        )}

        {/* Undo Notification Bar */}
        {lastAdjustment && (
          <div className="mx-6 mt-4 p-3 bg-surface-accentSubtle border border-border-accent rounded-xl flex items-center justify-between shadow-lg">
            <div className="flex items-center space-x-3 text-xs text-content-accent">
              <span className="font-bold">Last Action:</span>
              <span>{lastAdjustment.sku} ({lastAdjustment.delta > 0 ? '+' : ''}{lastAdjustment.delta} units)</span>
              <span className="text-2xs text-content-muted font-mono">at {lastAdjustment.timestamp}</span>
            </div>
            <button
              onClick={handleUndoAdjustment}
              disabled={isUndoing}
              className="flex items-center space-x-1.5 px-3 py-1 bg-action-speedBg hover:bg-action-speedHover text-white rounded-lg text-xs font-bold shadow transition"
            >
              <RotateCcw className={`w-3.5 h-3.5 ${isUndoing ? 'animate-spin' : ''}`} />
              <span>{isUndoing ? 'Reverting…' : 'Undo Adjustment'}</span>
            </button>
          </div>
        )}

        {/* Search & Filter Bar */}
        <div className="p-6 pb-2">
          <div className="flex items-center justify-between mb-4">
            <div className="relative flex-1 max-w-md">
              <Search className="w-4 h-4 absolute left-3.5 top-3 text-content-secondary" />
              <input
                type="text"
                placeholder="Filter by SKU or medicine title…"
                value={inventoryQuery}
                onChange={e => setInventoryQuery(e.target.value)}
                className="w-full bg-surface-inverse border border-border-strong rounded-xl pl-10 pr-4 py-2 text-xs text-white placeholder:text-content-muted focus:outline-none focus:border-border-accent"
              />
            </div>
            <div className="text-xs text-content-muted font-medium">
              Showing {filteredInventory.length} of {inventory.length} SKUs
            </div>
          </div>

          {/* Inventory Table */}
          <div className="bg-surface-inverse border border-border-strong rounded-2xl overflow-hidden shadow-xl">
            <table className="w-full text-left text-sm text-content-muted">
              <thead className="bg-surface-inverse text-content-muted uppercase text-2xs font-bold tracking-wider border-b border-border-strong">
                <tr>
                  <th className="px-6 py-4">SKU / Item</th>
                  <th className="px-4 py-4">Category</th>
                  <th className="px-4 py-4">Unit Price</th>
                  <th className="px-4 py-4">On Hand</th>
                  <th className="px-4 py-4">Reserved</th>
                  <th className="px-4 py-4">Available</th>
                  <th className="px-6 py-4 text-right">Adjust Stock</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-strong/60">
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
                    <tr key={item.id} className="hover:bg-surface-inverse transition">
                      <td className="px-6 py-4">
                        <div className="font-mono text-xs font-bold text-content-accent">{item.sku}</div>
                        <div className="font-bold text-white text-sm">{item.name}</div>
                        <div className="text-2xs text-content-muted">{item.packSize}</div>
                      </td>
                      <td className="px-4 py-4 text-xs text-content-muted">{item.category}</td>
                      <td className="px-4 py-4 font-bold text-content-brand">₹{item.price.toFixed(2)}</td>
                      <td className="px-4 py-4 font-mono font-bold text-content-subtle">{item.onHand}</td>
                      <td className="px-4 py-4 font-mono text-content-warning">
                        {item.reserved > 0 ? `${item.reserved} held` : '0'}
                      </td>
                      <td className="px-4 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-mono font-bold ${
                          item.available > 10
                            ? 'bg-surface-brandSubtle text-content-brand border border-border-brand'
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
                            onClick={() => handleAdjustStock(item, 10, 'QUICK_REPLENISHMENT')}
                            className="px-2.5 py-1 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-lg text-xs font-bold text-content-brand transition"
                            title="Add 10 units"
                          >
                            +10
                          </button>
                          <button
                            onClick={() => handleAdjustStock(item, 50, 'QUICK_REPLENISHMENT')}
                            className="px-2.5 py-1 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-lg text-xs font-bold text-content-brand transition"
                            title="Add 50 units"
                          >
                            +50
                          </button>

                          <div className="flex items-center space-x-1">
                            <input
                              type="number"
                              placeholder="±Qty"
                              value={customAddInputs[item.id] || ''}
                              onChange={e => setCustomAddInputs({ ...customAddInputs, [item.id]: e.target.value })}
                              className="w-16 bg-surface-inverse border border-border-strong rounded-lg px-2 py-1 text-xs text-center text-white focus:outline-none focus:border-border-accent font-mono"
                            />
                            <button
                              onClick={() => handleManualInputSubmit(item)}
                              className="px-2.5 py-1 bg-action-speedBg hover:bg-action-speedHover text-white rounded-lg text-xs font-bold transition shadow"
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
        </div>
      </div>
    </div>
  );
}
