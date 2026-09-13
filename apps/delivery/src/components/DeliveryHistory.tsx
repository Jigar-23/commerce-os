'use client';

import React, { useEffect, useState } from 'react';
import { Search, Star } from 'lucide-react';

const isProduction = process.env.NODE_ENV === 'production';
const ORDER_API = process.env.NEXT_PUBLIC_ORDER_API_URL && process.env.NEXT_PUBLIC_ORDER_API_URL.trim().length > 0
  ? process.env.NEXT_PUBLIC_ORDER_API_URL.replace(/\/$/, '')
  : isProduction ? '' : 'http://localhost:8090';

export interface HistoryItem {
  orderId: string;
  customerName: string;
  merchantName: string;
  payoutFormatted: string;
  status: string;
  completedAt: string;
}

export const DeliveryHistory: React.FC = () => {
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'DELIVERED' | 'RETURNED'>('ALL');

  useEffect(() => {
    async function fetchHistory() {
      try {
        const res = await fetch(`${ORDER_API}/api/v1/delivery/rider/history`);
        if (res.ok) {
          const data = await res.json();
          setHistory(data);
        }
      } catch {
        // Network fallback
      } finally {
        setIsLoading(false);
      }
    }
    fetchHistory();
  }, []);

  const filteredHistory = history.filter((item) => {
    const matchesSearch =
      item.orderId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.customerName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.merchantName.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  return (
    <div className="space-y-4 text-white">
      {/* Title & Filter Bar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-surface-inverse border border-border-strong p-4 rounded-2xl">
        <h3 className="text-base font-extrabold text-white">Authoritative Trip History</h3>

        <div className="flex items-center space-x-2 w-full sm:w-auto">
          {/* Search Bar */}
          <div className="relative flex-1 sm:w-48">
            <Search className="w-3.5 h-3.5 absolute left-3 top-3 text-content-muted" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search Order ID / Name..."
              className="w-full pl-8 pr-3 py-1.5 bg-surface-inverse border border-border-strong rounded-xl text-xs text-white outline-none focus:border-border-brand"
            />
          </div>

          {/* Status Filter */}
          <select
            value={statusFilter}
            onChange={(e: any) => setStatusFilter(e.target.value)}
            className="px-3 py-1.5 bg-surface-inverse border border-border-strong rounded-xl text-xs font-bold text-content-muted outline-none"
          >
            <option value="ALL">All Trips</option>
            <option value="DELIVERED">Delivered</option>
            <option value="RETURNED">Returned</option>
          </select>
        </div>
      </div>

      {/* History List */}
      <div className="space-y-3">
        {isLoading ? (
          <div className="p-8 bg-surface-inverse/80 border border-border-strong rounded-2xl text-center text-content-muted text-xs">
            Loading trip history from server...
          </div>
        ) : filteredHistory.length === 0 ? (
          <div className="p-8 bg-surface-inverse/80 border border-border-strong rounded-2xl text-center text-content-muted text-xs">
            No delivery history records found.
          </div>
        ) : (
          filteredHistory.map((item) => (
            <div
              key={item.orderId}
              className="bg-surface-inverse/80 border border-border-strong hover:border-border-strong p-4 rounded-2xl transition-all space-y-3"
            >
              <div className="flex items-center justify-between border-b border-border-strong pb-2.5">
                <div className="flex items-center space-x-2">
                  <span className="font-mono font-black text-xs text-content-accent">{item.orderId}</span>
                  <span className="text-2xs text-content-muted">• {new Date(item.completedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                </div>
                <span
                  className={`px-2.5 py-0.5 rounded-full text-2xs font-black border ${
                    item.status === 'DELIVERED'
                      ? 'bg-surface-inverse text-content-brand border-border-brand'
                      : 'bg-surface-inverse text-content-danger border-border-danger'
                  }`}
                >
                  {item.status}
                </span>
              </div>

              <div className="flex items-start justify-between">
                <div className="space-y-1">
                  <p className="text-xs font-extrabold text-white">{item.merchantName} → {item.customerName}</p>
                </div>

                <div className="text-right shrink-0">
                  <span className="text-lg font-black text-content-brand">{item.payoutFormatted}</span>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
