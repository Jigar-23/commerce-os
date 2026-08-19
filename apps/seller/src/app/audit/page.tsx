'use client';

import React, { useEffect, useState } from 'react';
import SellerSidebar from '../../components/SellerSidebar';
import HeaderQuickSearch from '../../components/HeaderQuickSearch';
import { sellerApi } from '@/lib/apiClient';
import { useSellerSession } from '@/lib/useSellerSession';
import { ShieldCheck, RefreshCw } from 'lucide-react';

export default function DedicatedAuditPage() {
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { session, storeName } = useSellerSession();

  const fetchAuditLogs = async () => {
    setIsLoading(true);
    try {
      const res = await sellerApi.get('/api/v1/orders/audit');
      if (res.ok && res.data) {
        setAuditLogs(Array.isArray(res.data) ? res.data : []);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchAuditLogs();
  }, []);

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex font-sans antialiased">
      <SellerSidebar activeTab="audit" onRefresh={fetchAuditLogs} isLoading={isLoading} />

      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0 gap-4">
          <div className="flex items-center space-x-3">
            <span className="px-2.5 py-1 rounded-full bg-surface-editorialSubtle text-content-editorial text-xs font-bold border border-border-editorial">
              Audit Logs
            </span>
            <span className="text-content-muted">/</span>
            <span className="text-xs font-bold text-content-primary" suppressHydrationWarning>{storeName}</span>
          </div>

          <div className="flex items-center space-x-4">
            <HeaderQuickSearch onSelectOrder={() => {}} />
            <button onClick={fetchAuditLogs} className="p-2 text-content-secondary hover:text-content-accent hover:bg-surface-subtle rounded-lg transition-all">
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </header>

        <main className="p-8 space-y-6 max-w-7xl w-full mx-auto">
          <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm">
            <div className="flex items-center space-x-2 mb-4">
              <ShieldCheck className="w-5 h-5 text-content-accent" />
              <h2 className="text-lg font-black text-content-primary">Security & Operational Audit Log</h2>
            </div>
            <p className="text-xs text-content-muted mb-6">Immutable audit record of store fulfillment, inventory adjustments, and status transitions.</p>

            {auditLogs.length === 0 ? (
              <div className="text-center py-12 text-content-muted text-xs">
                {isLoading ? 'Loading audit records…' : 'No audit entries recorded for this store.'}
              </div>
            ) : (
              <div className="divide-y divide-border-subtle font-mono text-xs">
                {auditLogs.map((log, idx) => (
                  <div key={log.id || idx} className="py-3 flex items-start justify-between">
                    <div className="space-y-1">
                      <div className="flex items-center space-x-2">
                        <span className="font-bold text-content-primary">{log.action || log.eventType || 'EVENT'}</span>
                        <span className="text-2xs text-content-muted bg-surface-subtle px-2 py-0.5 rounded">{log.actor || 'SYSTEM'}</span>
                      </div>
                      <p className="text-content-secondary font-sans text-xs">{log.details || log.message || JSON.stringify(log.payload || '')}</p>
                    </div>
                    <div className="text-right text-2xs text-content-muted font-mono">
                      {log.timestamp ? new Date(log.timestamp).toLocaleString() : new Date().toLocaleTimeString()}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
