'use client';

import React, { useEffect, useState } from 'react';
import {
  Package, QrCode, CheckCircle2, Snowflake, AlertOctagon, ArrowRight, Barcode,
  ShieldAlert, RefreshCw, Clock, ChevronRight, Settings, LogOut, Video,
  Download, Calendar, Filter, BarChart3, Layers, IndianRupee
} from 'lucide-react';

const isProduction = process.env.NODE_ENV === 'production';
const GATEWAY_URL = (process.env.NEXT_PUBLIC_API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '');

function getApiUrl(configuredUrl?: string, defaultPort?: number): string {
  if (configuredUrl && configuredUrl.trim().length > 0) {
    return configuredUrl.replace(/\/$/, '');
  }
  if (GATEWAY_URL) {
    return GATEWAY_URL;
  }
  if (isProduction) {
    return ''; // Fail closed in production if environment configuration is omitted
  }
  return defaultPort ? `http://localhost:${defaultPort}` : 'http://localhost:8083';
}

const ORDER_API = getApiUrl(process.env.NEXT_PUBLIC_ORDER_API_URL, 8083);
const INVENTORY_API = getApiUrl(process.env.NEXT_PUBLIC_INVENTORY_API_URL, 8083);

interface PickItem {
  sku: string;
  name: string;
  quantity: number;
  binLocation: string;
  allocatedBatchNo: string;
  expiryDate: string;
  coldChainRequired: boolean;
  scanned: boolean;
}

interface PickTask {
  orderId: string;
  storeName: string;
  orderStatus: string;
  targetSlaMins: number;
  items: PickItem[];
  loadError: string;
  isLoading: boolean;
}

export default function DarkStorePickerDashboard() {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'picking' | 'batches' | 'coldchain'>('dashboard');
  const [task, setTask] = useState<PickTask>({
    orderId: '',
    storeName: '',
    orderStatus: '',
    targetSlaMins: 10,
    items: [],
    loadError: '',
    isLoading: true,
  });
  const [message, setMessage] = useState('');

  const loadActiveOrder = async () => {
    setTask((t) => ({ ...t, isLoading: true, loadError: '' }));
    if (!ORDER_API) {
      setTask((t) => ({
        ...t,
        isLoading: false,
        loadError: 'Production configuration error: ORDER_API is not configured.',
      }));
      return;
    }
    try {
      const res = await fetch(`${ORDER_API}/api/v1/orders/active-delivery`);
      if (!res.ok) {
        setTask((t) => ({
          ...t,
          orderId: '',
          storeName: '',
          orderStatus: '',
          items: [],
          isLoading: false,
          loadError: 'No active pick task assigned. Generate a pick wave from the Seller Control Centre.',
        }));
        return;
      }
      const order = await res.json();
      const items: PickItem[] = await Promise.all(
        (order.items || []).map(async (item: any) => {
          let batchInfo: any = null;
          if (INVENTORY_API) {
            try {
              const batchRes = await fetch(`${INVENTORY_API}/api/v1/inventory/batches/${item.sku}`);
              if (batchRes.ok) {
                batchInfo = await batchRes.json();
              }
            } catch {
              // Inventory batch metadata lookup pending/unavailable
            }
          }
          return {
            sku: item.sku,
            name: item.name,
            quantity: item.quantity,
            binLocation: batchInfo?.binLocation || item.binLocation || 'Bin Unassigned',
            allocatedBatchNo: batchInfo?.allocatedBatchNo || item.batchNumber || 'Batch Unavailable',
            expiryDate: batchInfo?.expiryDate || item.expiryDate || 'N/A',
            coldChainRequired: Boolean(batchInfo?.coldChainRequired || item.coldChainRequired),
            scanned: false,
          };
        })
      );

      setTask({
        orderId: order.id,
        storeName: order.storeName || 'Fulfillment Hub',
        orderStatus: order.orderStatus,
        targetSlaMins: order.deliverySlaMins || 10,
        items,
        loadError: '',
        isLoading: false,
      });
    } catch {
      setTask((t) => ({
        ...t,
        isLoading: false,
        loadError: 'Inventory data unavailable. Check network connection and retry.',
      }));
    }
  };

  useEffect(() => {
    loadActiveOrder();
  }, []);

  const toggleScan = (index: number) => {
    setTask((prev) => {
      const nextItems = [...prev.items];
      nextItems[index] = { ...nextItems[index], scanned: !nextItems[index].scanned };
      return { ...prev, items: nextItems };
    });
  };

  const handlePackOrder = async () => {
    if (!task.orderId || !ORDER_API) return;
    try {
      const res = await fetch(`${ORDER_API}/api/v1/orders/${task.orderId}/transition`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'PACKED', actor: 'WAREHOUSE_PICKER' }),
      });
      if (res.ok) {
        setMessage('Order packed and staged at handoff bay!');
        loadActiveOrder();
      }
    } catch {
      setMessage('Error packing order');
    }
  };

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex font-sans antialiased">
      {/* 1. PRIMARY DARK NAVY ICON SIDEBAR */}
      <aside className="w-16 bg-surface-inverse flex flex-col items-center py-5 justify-between shrink-0 z-30 shadow-2xl">
        <div className="flex flex-col items-center space-y-6">
          <div className="w-10 h-10 rounded-xl bg-action-speedBg flex items-center justify-center text-white font-black text-lg shadow-lg shadow-subtle cursor-pointer">
            W
          </div>
          <div className="w-8 h-[1px] bg-surface-inverse my-2" />
          
          <button onClick={() => setActiveTab('dashboard')} className={`p-3 rounded-xl transition-all ${activeTab === 'dashboard' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`} title="Dashboard">
            <BarChart3 className="w-5 h-5" />
          </button>
          <button onClick={() => setActiveTab('picking')} className={`p-3 rounded-xl transition-all ${activeTab === 'picking' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`} title="FEFO Wave Picking">
            <QrCode className="w-5 h-5" />
          </button>
          <button onClick={() => setActiveTab('coldchain')} className={`p-3 rounded-xl transition-all ${activeTab === 'coldchain' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`} title="Cold Chain 2-8°C">
            <Snowflake className="w-5 h-5" />
          </button>
        </div>

        <div className="flex flex-col items-center space-y-4">
          <button className="p-2.5 text-content-muted hover:text-white rounded-lg hover:bg-surface-inverse">
            <Settings className="w-5 h-5" />
          </button>
          <button className="p-2.5 text-content-danger hover:text-content-danger rounded-lg hover:bg-surface-dangerSubtle">
            <LogOut className="w-5 h-5" />
          </button>
        </div>
      </aside>

      {/* 2. SECONDARY LIGHT MENU SIDEBAR */}
      <aside className="w-64 bg-white border-r border-border-default shrink-0 flex flex-col justify-between hidden md:flex">
        <div>
          <div className="p-5 border-b border-border-subtle flex items-center justify-between">
            <div>
              <span className="inline-flex items-center px-2 py-0.5 rounded text-2xs font-bold bg-surface-operationalSubtle text-content-operational border border-border-operational">
                Dark Store Warehouse
              </span>
              <h1 className="text-base font-extrabold text-content-primary mt-1">Commerce OS</h1>
            </div>
          </div>

          <nav className="p-4 space-y-1">
            <div className="text-2xs font-bold uppercase tracking-wider text-content-muted px-3 pb-2 pt-1">Wave Operations</div>
            
            <button
              onClick={() => setActiveTab('dashboard')}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'dashboard' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}
            >
              <div className="flex items-center space-x-3">
                <BarChart3 className="w-4 h-4" />
                <span>Dashboard Overview</span>
              </div>
              <ChevronRight className="w-3.5 h-3.5 opacity-40" />
            </button>

            <button
              onClick={() => setActiveTab('picking')}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'picking' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}
            >
              <div className="flex items-center space-x-3">
                <QrCode className="w-4 h-4" />
                <span>FEFO Wave Picking Scanner</span>
              </div>
              {task.items.length > 0 && (
                <span className="px-2 py-0.5 text-2xs font-bold rounded-full bg-action-operationalBg text-white">
                  {task.items.length} SKUs
                </span>
              )}
            </button>

            <button
              onClick={() => setActiveTab('coldchain')}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'coldchain' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}
            >
              <div className="flex items-center space-x-3">
                <Snowflake className="w-4 h-4" />
                <span>Cold Chain 2-8°C Monitor</span>
              </div>
            </button>
          </nav>
        </div>

        <div className="p-4 border-t border-border-subtle bg-surface-subtle flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-action-operationalBg text-white font-bold flex items-center justify-center text-sm shadow-md">
            W
          </div>
          <div className="overflow-hidden">
            <p className="text-xs font-bold text-content-primary truncate">Dark Store Picker</p>
            <p className="text-2xs text-content-muted truncate">FEFO Wave Operations</p>
          </div>
        </div>
      </aside>

      {/* 3. MAIN CONTENT AREA */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0">
          <div className="flex items-center space-x-3">
            <span className="px-2.5 py-1 rounded-full bg-surface-subtle text-content-secondary text-xs font-medium border border-border-default">
              Warehouse Operations
            </span>
            <span className="text-content-muted">/</span>
            <span className="text-xs font-bold text-content-primary">FEFO Wave Picking Portal</span>
          </div>

          <div className="flex items-center space-x-4">
            <button onClick={loadActiveOrder} className="p-2 text-content-secondary hover:text-content-accent hover:bg-surface-subtle rounded-lg transition-all" title="Refresh">
              <RefreshCw className={`w-4 h-4 ${task.isLoading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </header>

        <main className="p-8 space-y-6">
          {/* Top Info Banner Pod */}
          <div className="bg-surface-operationalSubtle border border-border-operational rounded-xl p-3.5 flex items-center justify-between text-xs text-content-operational shadow-sm">
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-action-operationalBg animate-pulse" />
              <p>FEFO (First-Expire-First-Out) Inventory Allocation Engine active across 10-Minute Dark Stores.</p>
            </div>
          </div>

          {/* 3 Step Onboarding Pod Bar */}
          <div className="bg-surface-subtle border border-border-default rounded-2xl p-6 shadow-sm">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="bg-white p-4 rounded-xl border border-border-default shadow-sm flex items-start space-x-3">
                <div className="p-2.5 rounded-lg bg-surface-inverse text-white">
                  <Barcode className="w-5 h-5" />
                </div>
                <div>
                  <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Step 1</span>
                  <h4 className="text-xs font-extrabold text-content-primary mt-0.5">Scan Bin Locations</h4>
                  <p className="text-2xs text-content-secondary mt-1">Navigate to assigned shelf and bin locations.</p>
                </div>
              </div>

              <div className="bg-white p-4 rounded-xl border border-border-default shadow-sm flex items-start space-x-3">
                <div className="p-2.5 rounded-lg bg-surface-inverse text-white">
                  <Snowflake className="w-5 h-5" />
                </div>
                <div>
                  <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Step 2</span>
                  <h4 className="text-xs font-extrabold text-content-primary mt-0.5">Cold Chain Ice Pack</h4>
                  <p className="text-2xs text-content-secondary mt-1">Pack 2-8°C items in insulated thermal sleeves.</p>
                </div>
              </div>

              <div className="bg-white p-4 rounded-xl border border-border-default shadow-sm flex items-start space-x-3">
                <div className="p-2.5 rounded-lg bg-surface-inverse text-white">
                  <CheckCircle2 className="w-5 h-5" />
                </div>
                <div>
                  <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Step 3</span>
                  <h4 className="text-xs font-extrabold text-content-primary mt-0.5">Stage for Handoff</h4>
                  <p className="text-2xs text-content-secondary mt-1">Mark wave packed & hand off to rider bay.</p>
                </div>
              </div>
            </div>
          </div>

          {/* Section Header */}
          <div className="flex items-center justify-between border-b border-border-default pb-4">
            <div className="flex items-center space-x-3">
              <div className="w-1 h-6 bg-action-operationalBg rounded-full" />
              <h2 className="text-xl font-extrabold text-content-primary">Dashboard</h2>
            </div>
          </div>

          {/* 4 KPI METRIC CARDS */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Active Wave Tasks</p>
              <div className="flex items-baseline justify-between mt-2">
                <h3 className="text-3xl font-black text-content-primary">{task.orderId ? 1 : 0}</h3>
                <span className="text-xs font-bold text-content-operational bg-surface-operationalSubtle px-2 py-0.5 rounded-full">FEFO Match</span>
              </div>
              <p className="text-2xs text-content-muted mt-2">10-Min Wave SLA</p>
            </div>

            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Active Order SKUs</p>
              <div className="flex items-baseline justify-between mt-2">
                <h3 className="text-3xl font-black text-content-primary">{task.items.length}</h3>
              </div>
              <p className="text-2xs text-content-muted mt-2">FEFO Expiry Order Preserved</p>
            </div>

            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Cold Chain Integrity</p>
              <div className="flex items-baseline justify-between mt-2">
                <h3 className="text-3xl font-black text-content-operational">
                  {task.items.some((i) => i.coldChainRequired) ? 'Active 2-8°C' : 'Standard'}
                </h3>
              </div>
              <p className="text-2xs text-content-operational font-medium mt-2">Insulated Verification</p>
            </div>

            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Target SLA</p>
              <div className="flex items-baseline justify-between mt-2">
                <h3 className="text-3xl font-black text-content-primary">{task.targetSlaMins} Min</h3>
              </div>
              <p className="text-2xs text-content-muted mt-2">Optimal Bin Pathing</p>
            </div>
          </div>

          {/* SVG ANALYTICS AREA GRAPH */}
          <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-base font-extrabold text-content-primary">Picker Velocity & Wave Velocity</h3>
                <p className="text-xs text-content-muted">Hourly picking throughput and staging metrics</p>
              </div>
            </div>

            <div className="h-48 w-full relative pt-4">
              {/* commerce-os:allow-vector-color */}
              <svg className="w-full h-full" viewBox="0 0 800 160" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="cyanGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#0891B2" stopOpacity="0.4" />
                    <stop offset="100%" stopColor="#0891B2" stopOpacity="0.0" />
                  </linearGradient>
                </defs>
                <line x1="0" y1="20" x2="800" y2="20" stroke="#F1F5F9" strokeWidth="1" strokeDasharray="4 4" />
                <line x1="0" y1="60" x2="800" y2="60" stroke="#F1F5F9" strokeWidth="1" strokeDasharray="4 4" />
                <line x1="0" y1="100" x2="800" y2="100" stroke="#F1F5F9" strokeWidth="1" strokeDasharray="4 4" />
                <line x1="0" y1="140" x2="800" y2="140" stroke="#F1F5F9" strokeWidth="1" strokeDasharray="4 4" />

                <path
                  d="M0,140 Q200,100 400,60 T600,40 T800,15 L800,160 L0,160 Z"
                  fill="url(#cyanGrad)"
                />
                <path
                  d="M0,140 Q200,100 400,60 T600,40 T800,15"
                  fill="none"
                  stroke="#0891B2"
                  strokeWidth="3"
                  strokeLinecap="round"
                />
              </svg>
            </div>
          </div>

          {/* ACTIVE WAVE TASK */}
          <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm max-w-3xl">
            <h3 className="text-base font-extrabold text-content-primary mb-4">FEFO Wave Picking Scanner</h3>

            {task.loadError ? (
              <div className="p-6 bg-surface-subtle border border-border-default rounded-xl space-y-3">
                <p className="text-xs text-content-secondary font-medium">{task.loadError}</p>
                <button
                  onClick={loadActiveOrder}
                  className="px-4 py-2 bg-action-primaryBg hover:bg-action-primaryHover text-white rounded-xl text-xs font-bold transition"
                >
                  Retry Wave Lookup
                </button>
              </div>
            ) : task.items.length === 0 ? (
              <p className="text-xs text-content-muted">No active pick items found.</p>
            ) : (
              <div className="space-y-4 text-xs">
                <div className="p-4 bg-surface-subtle border border-border-default rounded-xl flex items-center justify-between">
                  <div>
                    <span className="font-mono font-extrabold text-content-accent text-sm">{task.orderId}</span>
                    <p className="text-content-secondary mt-0.5">{task.storeName}</p>
                  </div>
                  <span className="px-2.5 py-1 bg-surface-operationalSubtle text-content-operational font-bold rounded-full text-2xs border border-border-operational">
                    {task.targetSlaMins}-Min SLA Active
                  </span>
                </div>

                {message && (
                  <div className="p-3 bg-surface-brandSubtle border border-border-brandSubtle text-content-brand font-bold rounded-xl">
                    {message}
                  </div>
                )}

                <div className="space-y-2">
                  {task.items.map((item, idx) => (
                    <div
                      key={idx}
                      onClick={() => toggleScan(idx)}
                      className={`p-4 border rounded-xl flex items-center justify-between cursor-pointer transition-all ${item.scanned ? 'bg-surface-brandSubtle border-border-brand/40' : 'bg-white border-border-default hover:border-border-operational'}`}
                    >
                      <div className="space-y-1">
                        <div className="flex items-center space-x-2">
                          <span className="font-bold text-content-primary">{item.name}</span>
                          <span className="font-mono text-2xs bg-surface-subtle px-2 py-0.5 rounded font-bold text-content-accent">{item.binLocation}</span>
                        </div>
                        <p className="text-2xs text-content-secondary">
                          SKU: {item.sku} • FEFO Batch: <strong className="text-content-primary">{item.allocatedBatchNo}</strong> (Exp: {item.expiryDate})
                        </p>
                      </div>

                      <button className={`px-3 py-1.5 rounded-lg text-xs font-bold ${item.scanned ? 'bg-action-primaryBg text-white' : 'bg-surface-subtle text-content-secondary'}`}>
                        {item.scanned ? 'Scanned ✓' : 'Scan SKU'}
                      </button>
                    </div>
                  ))}
                </div>

                <button
                  onClick={handlePackOrder}
                  className="w-full py-3 bg-action-operationalBg hover:bg-action-operationalHover text-white font-bold rounded-xl shadow-md transition-all"
                >
                  Mark Wave Packed & Stage for Courier Handshake
                </button>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
