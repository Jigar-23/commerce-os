'use client';

import React, { useState, useEffect, useMemo } from 'react';
import {
  ShieldCheck, FileText, AlertTriangle, CheckCircle2, XCircle, Clock, Pill,
  Users, Building, Layers, Search, RefreshCw, PackageX, ChevronRight,
  Settings, LogOut, Video, Download, Calendar, Filter, IndianRupee, BarChart3
} from 'lucide-react';

interface VerificationItem {
  id: string;
  orderId: string;
  patientName: string;
  rxItems: { name: string; sku: string; quantity: number }[];
  uploadedAt: string;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED';
  ocr?: {
    doctorName: string;
    doctorRegistrationNo: string;
    confidenceScore: number;
    extractedMedicines: { name: string; dosage: string; durationDays: number }[];
    extractedText: string;
  };
}

const isProduction = process.env.NODE_ENV === 'production';
const GATEWAY_URL = (process.env.NEXT_PUBLIC_API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '');
const ORDER_API = (process.env.NEXT_PUBLIC_ORDER_API_URL && process.env.NEXT_PUBLIC_ORDER_API_URL.trim().length > 0)
  ? process.env.NEXT_PUBLIC_ORDER_API_URL.replace(/\/$/, '')
  : (GATEWAY_URL || (isProduction ? '' : 'http://localhost:8083'));

const AI_API = (process.env.NEXT_PUBLIC_AI_API_URL && process.env.NEXT_PUBLIC_AI_API_URL.trim().length > 0)
  ? process.env.NEXT_PUBLIC_AI_API_URL.replace(/\/$/, '')
  : (GATEWAY_URL || (isProduction ? '' : 'http://localhost:8083'));

export default function PharmacistAdminDashboard() {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'rx' | 'sellers' | 'moderation' | 'cod' | 'audit'>('dashboard');
  const [queue, setQueue] = useState<VerificationItem[]>([]);
  const [selectedRx, setSelectedRx] = useState<VerificationItem | null>(null);
  const [pharmacistLicenseNo, setPharmacistLicenseNo] = useState('PHARM-LIC-2026-9912');
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [message, setMessage] = useState('');

  const loadQueue = async () => {
    if (!ORDER_API) {
      setLoadError('CRITICAL_CONFIGURATION_ERROR: Mandatory NEXT_PUBLIC_ORDER_API_URL is not configured.');
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setLoadError('');
    try {
      const res = await fetch(`${ORDER_API}/api/v1/orders/prescription-verification-queue`);
      if (!res.ok) throw new Error('Queue API unavailable');
      const raw = await res.json();
      const withOcr = await Promise.all(
        raw.map(async (item: VerificationItem) => {
          try {
            if (!AI_API) return item;
            const ocrRes = await fetch(`${AI_API}/ocr`);
            if (ocrRes.ok) {
              const ocr = await ocrRes.json();
              return { ...item, ocr: { ...ocr, confidenceScore: Number(ocr.confidenceScore || 0) } };
            }
          } catch {
            // fallback
          }
          return item;
        })
      );
      setQueue(withOcr);
      if (withOcr.length > 0 && !selectedRx) {
        setSelectedRx(withOcr[0]);
      }
    } catch (err: any) {
      setLoadError(err?.message || 'Failed to load verification queue');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadQueue();
  }, []);

  const handleDecision = async (verificationId: string, status: 'VERIFIED' | 'REJECTED') => {
    if (!ORDER_API) {
      setMessage('Error: NEXT_PUBLIC_ORDER_API_URL is not configured.');
      return;
    }
    try {
      const res = await fetch(`${ORDER_API}/api/v1/orders/verify-prescription`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          verificationId,
          status,
          pharmacistLicenseNo,
          notes: status === 'VERIFIED' ? 'Approved by Licensed Pharmacist' : 'Rejected: Rx invalid/missing details',
        }),
      });

      if (!res.ok) throw new Error('Verification decision update failed');
      setMessage(`Rx #${verificationId} set to ${status}`);
      loadQueue();
    } catch (err: any) {
      setMessage(`Error: ${err?.message || 'Update failed'}`);
    }
  };

  return (
    <div className="min-h-screen bg-surface-canvas text-content-primary flex font-sans antialiased">
      {/* 1. PRIMARY DARK NAVY ICON SIDEBAR */}
      <aside className="w-16 bg-surface-inverse flex flex-col items-center py-5 justify-between shrink-0 z-30 shadow-2xl">
        <div className="flex flex-col items-center space-y-6">
          <div className="w-10 h-10 rounded-xl bg-action-dangerBg flex items-center justify-center text-white font-black text-lg shadow-lg shadow-subtle cursor-pointer">
            A
          </div>
          <div className="w-8 h-[1px] bg-surface-inverse my-2" />
          
          <button onClick={() => setActiveTab('dashboard')} className={`p-3 rounded-xl transition-all ${activeTab === 'dashboard' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`} title="Dashboard">
            <BarChart3 className="w-5 h-5" />
          </button>
          <button onClick={() => setActiveTab('rx')} className={`p-3 rounded-xl transition-all ${activeTab === 'rx' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`} title="Rx Verification Queue">
            <Pill className="w-5 h-5" />
          </button>
          <button onClick={() => setActiveTab('sellers')} className={`p-3 rounded-xl transition-all ${activeTab === 'sellers' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`} title="Seller Approvals">
            <Building className="w-5 h-5" />
          </button>
          <button onClick={() => setActiveTab('cod')} className={`p-3 rounded-xl transition-all ${activeTab === 'cod' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`} title="COD Reconciliation">
            <IndianRupee className="w-5 h-5" />
          </button>
          <button onClick={() => setActiveTab('audit')} className={`p-3 rounded-xl transition-all ${activeTab === 'audit' ? 'bg-surface-accentSubtle text-content-accent border border-border-accent shadow-inner' : 'text-content-muted hover:text-white hover:bg-surface-inverse'}`} title="Audit Log">
            <ShieldCheck className="w-5 h-5" />
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
              <span className="inline-flex items-center px-2 py-0.5 rounded text-2xs font-bold bg-surface-dangerSubtle text-content-danger border border-border-danger">
                Compliance & Admin Portal
              </span>
              <h1 className="text-base font-extrabold text-content-primary mt-1">Commerce OS</h1>
            </div>
          </div>

          <nav className="p-4 space-y-1">
            <div className="text-2xs font-bold uppercase tracking-wider text-content-muted px-3 pb-2 pt-1">Compliance & Oversight</div>
            
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
              onClick={() => setActiveTab('rx')}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'rx' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}
            >
              <div className="flex items-center space-x-3">
                <Pill className="w-4 h-4" />
                <span>Rx Verification Queue</span>
              </div>
              {queue.length > 0 && (
                <span className="px-2 py-0.5 text-2xs font-bold rounded-full bg-action-dangerBg text-white">
                  {queue.length}
                </span>
              )}
            </button>

            <button
              onClick={() => setActiveTab('sellers')}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'sellers' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}
            >
              <div className="flex items-center space-x-3">
                <Building className="w-4 h-4" />
                <span>Seller & KYC Approvals</span>
              </div>
            </button>

            <div className="text-2xs font-bold uppercase tracking-wider text-content-muted px-3 pb-2 pt-5">Moderation & Audit</div>

            <button
              onClick={() => setActiveTab('cod')}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'cod' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}
            >
              <div className="flex items-center space-x-3">
                <IndianRupee className="w-4 h-4" />
                <span>COD Reconciliation</span>
              </div>
            </button>

            <button
              onClick={() => setActiveTab('audit')}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${activeTab === 'audit' ? 'bg-surface-accentSubtle text-content-accent font-bold' : 'text-content-secondary hover:bg-surface-subtle'}`}
            >
              <div className="flex items-center space-x-3">
                <ShieldCheck className="w-4 h-4" />
                <span>System Audit Logs</span>
              </div>
            </button>
          </nav>
        </div>

        {/* Pharmacist License Pod */}
        <div className="p-4 border-t border-border-subtle bg-surface-subtle flex items-center space-x-3">
          <div className="w-9 h-9 rounded-xl bg-action-dangerBg text-white font-bold flex items-center justify-center text-sm shadow-md">
            P
          </div>
          <div className="overflow-hidden">
            <p className="text-xs font-bold text-content-primary truncate">Dr. Pharmacist Admin</p>
            <p className="text-2xs text-content-muted truncate">{pharmacistLicenseNo}</p>
          </div>
        </div>
      </aside>

      {/* 3. MAIN CONTENT AREA */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        <header className="h-16 border-b border-border-default bg-white px-8 flex items-center justify-between shrink-0">
          <div className="flex items-center space-x-3">
            <span className="px-2.5 py-1 rounded-full bg-surface-subtle text-content-secondary text-xs font-medium border border-border-default">
              Admin & Pharmacist Console
            </span>
            <span className="text-content-muted">/</span>
            <span className="text-xs font-bold text-content-primary">Compliance Dashboard</span>
          </div>

          <div className="flex items-center space-x-4">
            <button onClick={loadQueue} className="p-2 text-content-secondary hover:text-content-accent hover:bg-surface-subtle rounded-lg transition-all" title="Refresh">
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
            <div className="h-4 w-[1px] bg-surface-muted" />
            <div className="flex items-center space-x-2">
              <div className="w-8 h-8 rounded-full bg-surface-dangerSubtle text-content-danger font-bold flex items-center justify-center text-xs">
                A
              </div>
              <div className="text-left text-xs">
                <p className="font-bold text-content-primary leading-tight">Admin Master</p>
                <p className="text-2xs text-content-muted">Chief Pharmacist</p>
              </div>
            </div>
          </div>
        </header>

        <main className="p-8 space-y-6">
          {/* Top Info Banner Pod */}
          <div className="bg-surface-dangerSubtle border border-border-danger rounded-xl p-3.5 flex items-center justify-between text-xs text-content-danger shadow-sm">
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-action-dangerBg animate-pulse" />
              <p>Pharmacist Verification Gate Active: All Rx Schedule H/H1 orders require licensed sign-off before warehouse dispatch.</p>
            </div>
          </div>

          {/* 3 Step Onboarding Pod Bar matching screenshot */}
          <div className="bg-surface-brandSubtle border border-border-default rounded-2xl p-6 shadow-sm">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="bg-white p-4 rounded-xl border border-border-default shadow-sm flex items-start space-x-3">
                <div className="p-2.5 rounded-lg bg-surface-inverse text-white">
                  <FileText className="w-5 h-5" />
                </div>
                <div>
                  <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Step 1</span>
                  <h4 className="text-xs font-extrabold text-content-primary mt-0.5">Rx AI OCR Audit</h4>
                  <p className="text-2xs text-content-secondary mt-1">Review extracted doctor registration & drug interaction scores.</p>
                </div>
              </div>

              <div className="bg-white p-4 rounded-xl border border-border-default shadow-sm flex items-start space-x-3">
                <div className="p-2.5 rounded-lg bg-surface-inverse text-white">
                  <Building className="w-5 h-5" />
                </div>
                <div>
                  <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Step 2</span>
                  <h4 className="text-xs font-extrabold text-content-primary mt-0.5">Seller KYC Approval</h4>
                  <p className="text-2xs text-content-secondary mt-1">Verify pharmacy licenses & onboarding documents.</p>
                </div>
              </div>

              <div className="bg-white p-4 rounded-xl border border-border-default shadow-sm flex items-start space-x-3">
                <div className="p-2.5 rounded-lg bg-surface-inverse text-white">
                  <ShieldCheck className="w-5 h-5" />
                </div>
                <div>
                  <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Step 3</span>
                  <h4 className="text-xs font-extrabold text-content-primary mt-0.5">Safety & Audit Ledger</h4>
                  <p className="text-2xs text-content-secondary mt-1">Real-time immutable ledger tracking all approvals.</p>
                </div>
              </div>
            </div>
          </div>

          {/* Section Header */}
          <div className="flex items-center justify-between border-b border-border-default pb-4">
            <div className="flex items-center space-x-3">
              <div className="w-1 h-6 bg-action-dangerBg rounded-full" />
              <h2 className="text-xl font-extrabold text-content-primary">Dashboard</h2>
            </div>

            <div className="flex items-center space-x-3">
              <div className="flex items-center bg-white border border-border-default rounded-xl px-3 py-1.5 text-xs text-content-secondary shadow-sm space-x-2">
                <Calendar className="w-3.5 h-3.5 text-content-muted" />
                <span>11-08-2026</span>
                <span className="text-content-muted">to</span>
                <span>11-08-2026</span>
              </div>
              <button onClick={loadQueue} className="p-2 bg-white border border-border-default rounded-xl text-content-secondary hover:bg-surface-subtle shadow-sm">
                <RefreshCw className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* 4 KPI METRIC CARDS matching screenshot */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Pending Rx Verifications</p>
              <div className="flex items-baseline justify-between mt-2">
                <h3 className="text-3xl font-black text-content-danger">{queue.length}</h3>
                <span className="text-xs font-bold text-content-danger bg-surface-dangerSubtle px-2 py-0.5 rounded-full">Require Sign-off</span>
              </div>
              <p className="text-2xs text-content-muted mt-2">Pharmacist Gate</p>
            </div>

            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Approved Merchants</p>
              <div className="flex items-baseline justify-between mt-2">
                <h3 className="text-3xl font-black text-content-primary">12</h3>
                <span className="text-xs font-bold text-content-brand bg-surface-brandSubtle px-2 py-0.5 rounded-full">100% Licensed</span>
              </div>
              <p className="text-2xs text-content-muted mt-2">KYC Verified Partners</p>
            </div>

            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-sm">
              <p className="text-xs font-bold text-content-muted">Verified Orders Today</p>
              <div className="flex items-baseline justify-between mt-2">
                <h3 className="text-3xl font-black text-content-primary">48</h3>
              </div>
              <p className="text-2xs text-content-brand font-medium mt-2">Dispatched to Dark Stores</p>
            </div>

            <div className="bg-white border border-border-default rounded-2xl p-5 shadow-sm">
              <p className="text-xs font-bold text-content-muted">System Audit Score</p>
              <div className="flex items-baseline justify-between mt-2">
                <h3 className="text-3xl font-black text-content-brand">99.8%</h3>
              </div>
              <p className="text-2xs text-content-muted mt-2">Schedule H Compliance</p>
            </div>
          </div>

          {/* SVG ANALYTICS AREA GRAPH */}
          <div className="bg-white border border-border-default rounded-2xl p-6 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-base font-extrabold text-content-primary">Prescription Sign-off Velocity</h3>
                <p className="text-xs text-content-muted">Pharmacist decision throughput and AI confidence trend</p>
              </div>
            </div>

            <div className="h-48 w-full relative pt-4">
              {/* commerce-os:allow-vector-color */}
              <svg className="w-full h-full" viewBox="0 0 800 160" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="roseGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#F43F5E" stopOpacity="0.4" />
                    <stop offset="100%" stopColor="#F43F5E" stopOpacity="0.0" />
                  </linearGradient>
                </defs>
                <line x1="0" y1="20" x2="800" y2="20" stroke="#F1F5F9" strokeWidth="1" strokeDasharray="4 4" />
                <line x1="0" y1="60" x2="800" y2="60" stroke="#F1F5F9" strokeWidth="1" strokeDasharray="4 4" />
                <line x1="0" y1="100" x2="800" y2="100" stroke="#F1F5F9" strokeWidth="1" strokeDasharray="4 4" />
                <line x1="0" y1="140" x2="800" y2="140" stroke="#F1F5F9" strokeWidth="1" strokeDasharray="4 4" />

                <path
                  d="M0,130 Q150,110 300,70 T600,30 T800,10 L800,160 L0,160 Z"
                  fill="url(#roseGrad)"
                />
                <path
                  d="M0,130 Q150,110 300,70 T600,30 T800,10"
                  fill="none"
                  stroke="#E11D48"
                  strokeWidth="3"
                  strokeLinecap="round"
                />
              </svg>
            </div>
          </div>

          {/* TAB 1: RX VERIFICATION QUEUE */}
          {activeTab === 'rx' && (
            <div className="bg-white border border-border-default rounded-2xl shadow-sm overflow-hidden">
              <div className="p-6 border-b border-border-subtle">
                <h3 className="text-base font-extrabold text-content-primary">Pharmacist Prescription Sign-off Queue</h3>
                <p className="text-xs text-content-muted">Review OCR confidence score, doctor registration number, and approve Schedule H drugs.</p>
              </div>

              {queue.length === 0 ? (
                <div className="p-12 text-center text-content-muted text-sm">
                  No pending prescriptions in the verification queue.
                </div>
              ) : (
                <div className="divide-y divide-border-subtle">
                  {queue.map(item => (
                    <div key={item.id} className="p-6 hover:bg-surface-subtle transition-all flex flex-col md:flex-row md:items-center justify-between gap-6">
                      <div className="space-y-2">
                        <div className="flex items-center space-x-3">
                          <span className="text-sm font-extrabold text-content-danger font-mono">{item.orderId}</span>
                          <span className="px-2.5 py-0.5 rounded-full text-2xs font-bold bg-surface-warningSubtle text-content-warning border border-border-warning">
                            {item.status}
                          </span>
                        </div>

                        <p className="text-xs font-bold text-content-primary">Patient: {item.patientName}</p>
                        
                        {item.ocr && (
                          <div className="p-3 bg-surface-subtle border border-border-default rounded-xl text-xs space-y-1">
                            <p className="font-bold text-content-secondary">Doctor: {item.ocr.doctorName} (Reg #{item.ocr.doctorRegistrationNo})</p>
                            <p className="text-content-brand font-bold">AI OCR Confidence: {item.ocr.confidenceScore * 100}%</p>
                            <p className="text-content-secondary font-mono text-2xs">{item.ocr.extractedText}</p>
                          </div>
                        )}
                      </div>

                      <div className="flex items-center space-x-3 shrink-0">
                        <button
                          onClick={() => handleDecision(item.id, 'VERIFIED')}
                          className="px-4 py-2 bg-action-primaryBg hover:bg-action-primaryHover text-white rounded-xl text-xs font-bold shadow-sm transition-all flex items-center space-x-1.5"
                        >
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          <span>Approve & Sign-off</span>
                        </button>
                        <button
                          onClick={() => handleDecision(item.id, 'REJECTED')}
                          className="px-4 py-2 bg-action-dangerBg hover:bg-action-dangerHover text-white rounded-xl text-xs font-bold shadow-sm transition-all flex items-center space-x-1.5"
                        >
                          <XCircle className="w-3.5 h-3.5" />
                          <span>Reject Rx</span>
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
