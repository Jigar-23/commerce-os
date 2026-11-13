'use client';

import React, { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { Search, X, Package, User, Phone, Eye, ArrowRight, AlertCircle } from 'lucide-react';
import { formatAddress } from '../lib/formatAddress';
import { sellerApi } from '@/lib/apiClient';

interface SearchResult {
  id: string;
  orderId?: string;
  order_id?: string;
  customerId?: string;
  customerPhone?: string;
  orderStatus: string;
  totalAmount: number;
  paymentMethod: string;
  paymentStatus: string;
  deliveryAddress: any;
  items: { name: string; quantity: number; price: number }[];
  createdAt?: string;
}

export default function HeaderQuickSearch({
  onSelectOrder,
  placeholder,
  onSearchChange,
}: {
  onSelectOrder?: (order: SearchResult) => void;
  placeholder?: string;
  onSearchChange?: (q: string) => void;
}) {
  const [query, setQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [allOrders, setAllOrders] = useState<SearchResult[]>([]);
  const [selectedResult, setSelectedResult] = useState<SearchResult | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const loadOrders = async () => {
    if (isLoading || allOrders.length > 0) return;
    setIsLoading(true);
    try {
      const res = await sellerApi.get('/api/v1/orders/seller');
      if (res.ok && res.data) {
        const raw = Array.isArray(res.data) ? res.data : (res.data?.orders || []);
        const normalized = raw.map((o: any) => ({
          ...o,
          id: o.id || o.orderId || o.order_id || '',
          orderId: o.orderId || o.id || o.order_id || '',
        }));
        setAllOrders(normalized);
      }
    } catch (e) {
      console.error('Quick search order load error:', e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    const timer = setTimeout(() => {
      loadOrders();
    }, 1500);
    return () => clearTimeout(timer);
  }, []);

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const cleanQuery = query.trim().toLowerCase();

  const filteredResults =
    cleanQuery.length > 0
      ? allOrders.filter((order) => {
          const orderIdStr = String(order.id || order.orderId || order.order_id || '').toLowerCase();
          const matchesId = orderIdStr.includes(cleanQuery);
          const matchesPhone = order.customerPhone
            ? String(order.customerPhone).toLowerCase().includes(cleanQuery) ||
              String(order.customerPhone).replaceAll(/[^0-9]/g, '').includes(cleanQuery.replaceAll(/[^0-9]/g, ''))
            : false;
          const matchesUser = order.customerId ? String(order.customerId).toLowerCase().includes(cleanQuery) : false;
          return matchesId || matchesPhone || matchesUser;
        })
      : [];

  return (
    <div className="relative w-full max-w-sm lg:max-w-md" ref={dropdownRef}>
      {/* Search Input Box */}
      <div className="relative flex items-center">
        <Search className="w-3.5 h-3.5 absolute left-3.5 text-slate-400 pointer-events-none" />
        <input
          type="text"
          placeholder={placeholder || 'Search orders, phone, customer...'}
          value={query}
          onFocus={() => {
            setIsOpen(true);
            loadOrders();
          }}
          onChange={(e) => {
            const val = e.target.value;
            setQuery(val);
            setIsOpen(true);
            if (onSearchChange) onSearchChange(val);
          }}
          className="w-full pl-9 pr-8 py-1.5 bg-slate-100/80 hover:bg-slate-100 focus:bg-white border border-slate-200/80 focus:border-slate-300 rounded-xl text-xs font-normal text-slate-900 placeholder:text-slate-400 outline-none transition-all focus:ring-2 focus:ring-slate-900/5 shadow-2xs"
        />
        {query ? (
          <button
            onClick={() => {
              setQuery('');
              setSelectedResult(null);
            }}
            className="absolute right-2.5 p-0.5 text-slate-400 hover:text-slate-600 rounded-full"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        ) : (
          <kbd className="hidden sm:inline-block absolute right-2.5 px-1.5 py-0.5 text-[10px] font-medium text-slate-400 bg-white border border-slate-200 rounded">
            /
          </kbd>
        )}
      </div>

      {/* Live Results Dropdown Container */}
      {isOpen && cleanQuery.length > 0 && (
        <div className="absolute top-full left-0 right-0 mt-2 bg-white rounded-2xl shadow-xl border border-slate-200/80 z-50 max-h-[440px] overflow-y-auto p-2 space-y-1">
          <div className="px-3 py-1.5 border-b border-slate-100 flex items-center justify-between text-[11px] text-slate-500 font-medium">
            <span>Matching Orders ({filteredResults.length})</span>
            <span className="text-[10px] text-slate-400">Order ID • Phone • Customer</span>
          </div>

          {filteredResults.length === 0 ? (
            <div className="p-6 text-center text-xs text-slate-500 space-y-1.5">
              <AlertCircle className="w-5 h-5 text-slate-400 mx-auto" />
              <p className="font-medium text-slate-700">No matching orders found</p>
              <p className="text-[11px] text-slate-400">
                Try typing full Order ID (e.g. ORD-101), phone number, or customer name.
              </p>
            </div>
          ) : (
            <div className="space-y-1">
              {filteredResults.map((order) => {
                const status = (order.orderStatus || '').toUpperCase();
                const isDelivered = status === 'DELIVERED';
                const isCancelled = status === 'CANCELLED';

                const badgeClass = isDelivered
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200/50'
                  : isCancelled
                  ? 'bg-rose-50 text-rose-700 border-rose-200/50'
                  : 'bg-indigo-50 text-indigo-700 border-indigo-200/50';

                return (
                  <div
                    key={order.id}
                    onClick={() => {
                      if (onSelectOrder) {
                        onSelectOrder(order);
                        setIsOpen(false);
                      } else {
                        setSelectedResult(order);
                      }
                    }}
                    className="p-3 rounded-xl hover:bg-slate-50 border border-transparent hover:border-slate-200/60 cursor-pointer transition-all space-y-1.5"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2">
                        <Package className="w-3.5 h-3.5 text-slate-500" />
                        <span className="font-mono font-semibold text-xs text-slate-900">{order.id}</span>
                      </div>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium border ${badgeClass}`}>
                        {order.orderStatus}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
                      {order.customerPhone && (
                        <span className="flex items-center space-x-1 px-1.5 py-0.5 rounded bg-slate-100 text-slate-700">
                          <Phone className="w-3 h-3 text-slate-400" />
                          <span>{order.customerPhone}</span>
                        </span>
                      )}
                      {order.customerId && (
                        <span className="flex items-center space-x-1 px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 font-mono text-[10px]">
                          <User className="w-3 h-3 text-slate-400" />
                          <span>{order.customerId}</span>
                        </span>
                      )}
                      <span className="font-semibold text-slate-900 ml-auto">₹{order.totalAmount}</span>
                    </div>

                    <div className="flex items-center justify-between text-[11px] text-slate-400 pt-1 border-t border-slate-100">
                      <span className="truncate max-w-[220px]">{formatAddress(order.deliveryAddress)}</span>
                      <span className="text-slate-700 font-medium hover:underline flex items-center space-x-1">
                        <span>Details</span>
                        <Eye className="w-3 h-3" />
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* QUICK VIEW POPUP MODAL */}
      {selectedResult && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl border border-slate-200/80 max-w-lg w-full p-6 space-y-5 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between border-b border-slate-100 pb-4">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-800">
                  <Package className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center space-x-2">
                    <h3 className="text-base font-semibold text-slate-900 font-mono">{selectedResult.id}</h3>
                    <span className="px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 text-[10px] font-medium border border-slate-200">
                      Order Overview
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-500">Fast Fulfillment Summary</p>
                </div>
              </div>

              <button
                onClick={() => setSelectedResult(null)}
                className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Quick Details Grid */}
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60 space-y-1">
                <span className="text-[10px] font-medium text-slate-400 uppercase tracking-wider block">Customer Phone</span>
                <p className="font-semibold text-slate-800 flex items-center space-x-1.5">
                  <Phone className="w-3.5 h-3.5 text-slate-500" />
                  <span>{selectedResult.customerPhone || '—'}</span>
                </p>
              </div>

              <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60 space-y-1">
                <span className="text-[10px] font-medium text-slate-400 uppercase tracking-wider block">Customer ID</span>
                <p className="font-mono text-[11px] font-semibold text-slate-800 truncate flex items-center space-x-1.5">
                  <User className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                  <span className="truncate">{selectedResult.customerId || '—'}</span>
                </p>
              </div>

              <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60 space-y-1">
                <span className="text-[10px] font-medium text-slate-400 uppercase tracking-wider block">Status</span>
                <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200/50">
                  {selectedResult.orderStatus}
                </span>
              </div>

              <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60 space-y-1">
                <span className="text-[10px] font-medium text-slate-400 uppercase tracking-wider block">Total Amount</span>
                <p className="font-semibold text-slate-900 text-sm">
                  ₹{selectedResult.totalAmount}{' '}
                  <span className="text-[11px] text-slate-500 font-normal">({selectedResult.paymentMethod})</span>
                </p>
              </div>
            </div>

            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60 text-xs space-y-1">
              <span className="text-[10px] font-medium text-slate-400 uppercase tracking-wider">Delivery Address</span>
              <p className="font-medium text-slate-800 text-[11px]">{formatAddress(selectedResult.deliveryAddress)}</p>
            </div>

            {/* Order Items */}
            <div className="space-y-2">
              <span className="text-[10px] font-medium text-slate-400 uppercase tracking-wider block">Line Items</span>
              <div className="bg-slate-50 border border-slate-200/60 rounded-xl p-3 space-y-1.5 max-h-36 overflow-y-auto">
                {selectedResult.items && selectedResult.items.length > 0 ? (
                  selectedResult.items.map((item, idx) => (
                    <div key={idx} className="flex items-center justify-between text-xs font-medium">
                      <span className="text-slate-800">
                        • {item.name} <strong className="text-indigo-600 font-semibold">x{item.quantity}</strong>
                      </span>
                      <span className="font-semibold text-slate-900">₹{item.price * item.quantity}</span>
                    </div>
                  ))
                ) : (
                  <p className="text-xs text-slate-500">Standard Catalog Item Order</p>
                )}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center justify-between pt-2">
              <button
                onClick={() => setSelectedResult(null)}
                className="px-4 py-2 border border-slate-200 rounded-xl text-xs font-medium text-slate-600 hover:bg-slate-50 transition-colors"
              >
                Close
              </button>

              <Link
                href={`/orders/${selectedResult.id}`}
                onClick={() => setSelectedResult(null)}
                className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-medium shadow-sm flex items-center space-x-1.5 transition-colors"
              >
                <span>View Full Order</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
