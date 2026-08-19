'use client';

import React, { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { Search, X, Package, User, Phone, Eye, ArrowRight, CheckCircle2, Clock, AlertCircle } from 'lucide-react';
import { formatAddress } from '../lib/formatAddress';

import { sellerApi } from '@/lib/apiClient';

interface SearchResult {
  id: string;
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

export default function HeaderQuickSearch({ onSelectOrder }: { onSelectOrder?: (order: SearchResult) => void }) {
  const [query, setQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [allOrders, setAllOrders] = useState<SearchResult[]>([]);
  const [selectedResult, setSelectedResult] = useState<SearchResult | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const loadOrders = async () => {
      setIsLoading(true);
      try {
        const res = await sellerApi.get('/api/v1/orders/seller');
        if (res.ok && res.data) {
          setAllOrders(Array.isArray(res.data) ? res.data : (res.data?.orders || []));
        }
      } catch (e) {
        console.error('Quick search order load error:', e);
      } finally {
        setIsLoading(false);
      }
    };
    loadOrders();
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

  const filteredResults = cleanQuery.length > 0 ? allOrders.filter(order => {
    const matchesId = order.id.toLowerCase().includes(cleanQuery);
    const matchesPhone = order.customerPhone ? order.customerPhone.toLowerCase().includes(cleanQuery) || order.customerPhone.replaceAll(/[^0-9]/g, '').includes(cleanQuery.replaceAll(/[^0-9]/g, '')) : false;
    const matchesUser = order.customerId ? order.customerId.toLowerCase().includes(cleanQuery) : false;
    return matchesId || matchesPhone || matchesUser;
  }) : [];

  return (
    <div className="relative w-full max-w-md" ref={dropdownRef}>
      {/* Search Input Box */}
      <div className="relative flex items-center">
        <Search className="w-4 h-4 absolute left-3.5 text-content-muted pointer-events-none" />
        <input
          type="text"
          placeholder="Quick View Search (Order ID, Phone #, or User ID)..."
          value={query}
          onFocus={() => setIsOpen(true)}
          onChange={(e) => {
            setQuery(e.target.value);
            setIsOpen(true);
          }}
          className="w-full pl-10 pr-9 py-2 bg-surface-subtle hover:bg-surface-subtle focus:bg-white border border-border-default focus:border-border-accent rounded-xl text-xs font-medium text-content-primary placeholder:text-content-muted outline-none transition-all shadow-inner"
        />
        {query && (
          <button
            onClick={() => {
              setQuery('');
              setSelectedResult(null);
            }}
            className="absolute right-3 p-0.5 text-content-muted hover:text-content-secondary rounded-full"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* Live Results Dropdown Container */}
      {isOpen && cleanQuery.length > 0 && (
        <div className="absolute top-full left-0 right-0 mt-2 bg-white rounded-2xl shadow-2xl border border-border-default z-50 max-h-[480px] overflow-y-auto p-2 space-y-2">
          <div className="px-3 py-2 border-b border-border-subtle flex items-center justify-between text-2xs font-bold text-content-muted">
            <span>Search Results ({filteredResults.length})</span>
            <span className="text-content-accent font-mono">Matched by Order ID / Phone / User ID</span>
          </div>

          {filteredResults.length === 0 ? (
            <div className="p-6 text-center text-xs text-content-muted space-y-1">
              <AlertCircle className="w-6 h-6 text-content-muted mx-auto" />
              <p className="font-semibold text-content-secondary">No matching orders found</p>
              <p className="text-2xs">Try typing full Order ID (e.g. ORD-8812), Phone Number (+91-9876543210), or User ID.</p>
            </div>
          ) : (
            <div className="space-y-1.5">
              {filteredResults.map((order) => {
                const isMatchId = order.id.toLowerCase().includes(cleanQuery);
                const isMatchPhone = order.customerPhone && order.customerPhone.toLowerCase().includes(cleanQuery);
                const isMatchUser = order.customerId && order.customerId.toLowerCase().includes(cleanQuery);

                return (
                  <div
                    key={order.id}
                    onClick={() => setSelectedResult(order)}
                    className="p-3 rounded-xl hover:bg-surface-subtle border border-transparent hover:border-border-default cursor-pointer transition-all space-y-1.5"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-2">
                        <Package className="w-4 h-4 text-content-accent" />
                        <span className="font-mono font-black text-xs text-content-accent">{order.id}</span>
                      </div>
                      <span className={`px-2 py-0.5 rounded-full text-2xs font-black ${
                        order.orderStatus === 'DELIVERED' ? 'bg-surface-brandSubtle text-content-brand' :
                        order.orderStatus === 'CANCELLED' ? 'bg-surface-dangerSubtle text-content-danger' : 'bg-surface-accentSubtle text-content-accent'
                      }`}>
                        {order.orderStatus}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 text-2xs text-content-secondary">
                      <span className={`flex items-center space-x-1 px-1.5 py-0.5 rounded text-2xs font-bold ${isMatchPhone ? 'bg-surface-warningSubtle text-content-warning border border-border-warning' : 'bg-surface-subtle'}`}>
                        <Phone className="w-3 h-3 text-content-muted" />
                        <span>{order.customerPhone || '—'}</span>
                      </span>

                      <span className={`flex items-center space-x-1 px-1.5 py-0.5 rounded text-2xs font-bold ${isMatchUser ? 'bg-surface-editorialSubtle text-content-editorial border border-border-editorial' : 'bg-surface-subtle'}`}>
                        <User className="w-3 h-3 text-content-muted" />
                        <span className="font-mono">{order.customerId || '—'}</span>
                      </span>

                      <span className="font-bold text-content-primary ml-auto">₹{order.totalAmount}</span>
                    </div>

                    <div className="flex items-center justify-between text-2xs text-content-muted pt-1 border-t border-border-subtle">
                      <span className="truncate max-w-[200px]">{formatAddress(order.deliveryAddress)}</span>
                      <span className="text-content-accent font-bold hover:underline flex items-center space-x-0.5">
                        <span>Quick View</span>
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
        <div className="fixed inset-0 z-50 bg-surface-inverse/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl shadow-2xl border border-border-default max-w-lg w-full p-6 space-y-5 animate-in fade-in zoom-in duration-150">
            <div className="flex items-center justify-between border-b border-border-subtle pb-4">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-2xl bg-surface-accentSubtle border border-border-accent flex items-center justify-center text-content-accent">
                  <Package className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center space-x-2">
                    <h3 className="text-base font-black text-content-primary font-mono">{selectedResult.id}</h3>
                    <span className="px-2.5 py-0.5 rounded-full bg-surface-brandSubtle text-content-brand text-2xs font-black border border-border-brandSubtle">
                      Quick View Card
                    </span>
                  </div>
                  <p className="text-2xs text-content-muted">Order & Customer Entity Breakdown</p>
                </div>
              </div>

              <button
                onClick={() => setSelectedResult(null)}
                className="p-2 text-content-muted hover:text-content-secondary hover:bg-surface-subtle rounded-xl"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Quick Details Pods */}
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-3 bg-surface-subtle rounded-2xl border border-border-default space-y-1">
                <span className="text-2xs font-bold text-content-muted uppercase tracking-wider block">Customer Phone</span>
                <p className="font-bold text-content-primary flex items-center space-x-1.5">
                  <Phone className="w-3.5 h-3.5 text-content-accent" />
                  <span>{selectedResult.customerPhone || '—'}</span>
                </p>
              </div>

              <div className="p-3 bg-surface-subtle rounded-2xl border border-border-default space-y-1">
                <span className="text-2xs font-bold text-content-muted uppercase tracking-wider block">User ID (Customer ID)</span>
                <p className="font-mono text-2xs font-bold text-content-editorial truncate flex items-center space-x-1.5">
                  <User className="w-3.5 h-3.5 text-content-editorial shrink-0" />
                  <span className="truncate">{selectedResult.customerId || '—'}</span>
                </p>
              </div>

              <div className="p-3 bg-surface-subtle rounded-2xl border border-border-default space-y-1">
                <span className="text-2xs font-bold text-content-muted uppercase tracking-wider block">Order Status</span>
                <span className="inline-block px-2.5 py-0.5 rounded-full text-2xs font-black bg-surface-accentSubtle text-content-accent">
                  {selectedResult.orderStatus}
                </span>
              </div>

              <div className="p-3 bg-surface-subtle rounded-2xl border border-border-default space-y-1">
                <span className="text-2xs font-bold text-content-muted uppercase tracking-wider block">Total Payment</span>
                <p className="font-black text-content-primary text-sm">₹{selectedResult.totalAmount} <span className="text-2xs text-content-secondary font-normal">({selectedResult.paymentMethod})</span></p>
              </div>
            </div>

            <div className="p-3.5 bg-surface-accentSubtle border border-border-accent rounded-2xl text-xs space-y-1">
              <span className="text-2xs font-bold text-content-accent uppercase tracking-wider">Delivery Address</span>
              <p className="font-semibold text-content-primary text-2xs">{formatAddress(selectedResult.deliveryAddress)}</p>
            </div>

            {/* Order Items */}
            <div className="space-y-2">
              <span className="text-2xs font-bold text-content-muted uppercase tracking-wider block">Order Line Items</span>
              <div className="bg-surface-subtle border border-border-default rounded-2xl p-3 space-y-1.5 max-h-36 overflow-y-auto">
                {selectedResult.items && selectedResult.items.length > 0 ? (
                  selectedResult.items.map((item, idx) => (
                    <div key={idx} className="flex items-center justify-between text-xs font-medium">
                      <span className="text-content-primary">• {item.name} <strong className="text-content-accent">x{item.quantity}</strong></span>
                      <span className="font-bold text-content-primary">₹{item.price * item.quantity}</span>
                    </div>
                  ))
                ) : (
                  <p className="text-xs text-content-muted">Standard Catalog Item Order</p>
                )}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center justify-between pt-2">
              <button
                onClick={() => setSelectedResult(null)}
                className="px-4 py-2.5 border border-border-default rounded-2xl text-xs font-bold text-content-secondary hover:bg-surface-subtle"
              >
                Close Preview
              </button>

              <Link
                href={`/orders/${selectedResult.id}`}
                onClick={() => setSelectedResult(null)}
                className="px-5 py-2.5 bg-action-speedBg hover:bg-action-speedHover text-white rounded-2xl text-xs font-bold shadow-lg flex items-center space-x-2"
              >
                <span>Open Full Order Management</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
