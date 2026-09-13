'use client';

import React, { useState, useEffect } from 'react';
import { Phone, AlertTriangle, Clock, RefreshCw, XCircle, ShieldAlert, ArrowLeft, PackageX, MapPinOff } from 'lucide-react';

interface HelpIssueModalProps {
  orderId: string;
  customerPhone: string;
  storePhone: string;
  onClose: () => void;
  onInitiateReturn: (reason: string) => void;
  onReportIssue: (type: string, details: string) => void;
}

export const HelpIssueModal: React.FC<HelpIssueModalProps> = ({
  orderId,
  customerPhone,
  storePhone,
  onClose,
  onInitiateReturn,
  onReportIssue,
}) => {
  const [activeTab, setActiveTab] = useState<
    'menu' | 'unreachable' | 'store_issue' | 'wrong_address' | 'damaged' | 'missing' | 'return'
  >('menu');
  const [timerSeconds, setTimerSeconds] = useState(180); // 3-minute waiting timer
  const [isTimerRunning, setIsTimerRunning] = useState(false);
  const [issueNotes, setIssueNotes] = useState('');

  useEffect(() => {
    let interval: any = null;
    if (isTimerRunning && timerSeconds > 0) {
      interval = setInterval(() => {
        setTimerSeconds((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [isTimerRunning, timerSeconds]);

  const timerMin = Math.floor(timerSeconds / 60);
  const timerSec = timerSeconds % 60;

  return (
    <div className="fixed inset-0 z-50 bg-surface-inverse/90 backdrop-blur-sm flex items-end sm:items-center justify-center p-4">
      <div className="w-full max-w-lg bg-surface-inverse border border-border-strong rounded-3xl overflow-hidden shadow-2xl text-white">
        {/* Header */}
        <div className="bg-surface-inverse px-5 py-4 border-b border-border-strong flex items-center justify-between">
          <div className="flex items-center space-x-2">
            {activeTab !== 'menu' && (
              <button onClick={() => setActiveTab('menu')} className="p-1 hover:bg-surface-inverse rounded-lg text-content-muted">
                <ArrowLeft className="w-5 h-5" />
              </button>
            )}
            <h3 className="text-sm font-extrabold text-white">
              {activeTab === 'menu'
                ? 'Rider Support & Exception Center'
                : activeTab === 'unreachable'
                ? 'Customer Unreachable Workflow'
                : activeTab === 'store_issue'
                ? 'Store Pickup Issue'
                : activeTab === 'wrong_address'
                ? 'Wrong Customer Address'
                : activeTab === 'damaged'
                ? 'Damaged Package Exception'
                : activeTab === 'missing'
                ? 'Missing Item / Package'
                : 'Initiate Order Return'}
            </h3>
          </div>
          <button onClick={onClose} className="p-1 text-content-muted hover:text-white rounded-lg">
            ✕
          </button>
        </div>

        <div className="p-6 space-y-4 max-h-[80vh] overflow-y-auto">
          {activeTab === 'menu' && (
            <div className="space-y-3">
              {/* Quick Contact Desk */}
              <div className="grid grid-cols-2 gap-3">
                {customerPhone ? (
                  <a
                    href={`tel:${customerPhone}`}
                    className="p-3.5 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-2xl flex items-center space-x-3 text-xs font-bold"
                  >
                    <div className="p-2 rounded-xl bg-surface-brandSubtle text-content-brand">
                      <Phone className="w-4 h-4" />
                    </div>
                    <div>
                      <p className="text-white">Call Customer</p>
                      <p className="text-2xs text-content-muted">Masked Phone</p>
                    </div>
                  </a>
                ) : (
                  <div className="p-3.5 bg-surface-inverse/80 border border-border-strong rounded-2xl text-xs font-bold text-content-secondary">
                    No Customer Phone
                  </div>
                )}

                {storePhone ? (
                  <a
                    href={`tel:${storePhone}`}
                    className="p-3.5 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-2xl flex items-center space-x-3 text-xs font-bold"
                  >
                    <div className="p-2 rounded-xl bg-surface-accentSubtle text-content-accent">
                      <Phone className="w-4 h-4" />
                    </div>
                    <div>
                      <p className="text-white">Call Store</p>
                      <p className="text-2xs text-content-muted">Merchant Desk</p>
                    </div>
                  </a>
                ) : (
                  <div className="p-3.5 bg-surface-inverse/80 border border-border-strong rounded-2xl text-xs font-bold text-content-secondary">
                    No Store Phone
                  </div>
                )}
              </div>

              {/* Exception Workflows Menu */}
              <div className="space-y-2 pt-2">
                {/* 1. Customer Unreachable */}
                <button
                  onClick={() => {
                    setActiveTab('unreachable');
                    setIsTimerRunning(true);
                  }}
                  className="w-full p-3.5 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-2xl text-left flex items-center justify-between"
                >
                  <div className="flex items-center space-x-3">
                    <div className="p-2 rounded-xl bg-surface-warningSubtle text-content-warning">
                      <Clock className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-xs font-extrabold text-white">1. Customer Unreachable / Door Locked</h4>
                      <p className="text-2xs text-content-muted">Mandatory 3-min wait timer before return</p>
                    </div>
                  </div>
                </button>

                {/* 2. Store Issue / Closed */}
                <button
                  onClick={() => setActiveTab('store_issue')}
                  className="w-full p-3.5 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-2xl text-left flex items-center justify-between"
                >
                  <div className="flex items-center space-x-3">
                    <div className="p-2 rounded-xl bg-surface-accentSubtle text-content-accent">
                      <AlertTriangle className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-xs font-extrabold text-white">2. Store Closed / Order Delay</h4>
                      <p className="text-2xs text-content-muted">Dark store shut or long prep delay</p>
                    </div>
                  </div>
                </button>

                {/* 3. Wrong Customer Address */}
                <button
                  onClick={() => setActiveTab('wrong_address')}
                  className="w-full p-3.5 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-2xl text-left flex items-center justify-between"
                >
                  <div className="flex items-center space-x-3">
                    <div className="p-2 rounded-xl bg-surface-dangerSubtle text-content-danger">
                      <MapPinOff className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-xs font-extrabold text-white">3. Wrong / Out of Zone Address</h4>
                      <p className="text-2xs text-content-muted">Address pin mismatch or wrong landmark</p>
                    </div>
                  </div>
                </button>

                {/* 4. Damaged Package */}
                <button
                  onClick={() => setActiveTab('damaged')}
                  className="w-full p-3.5 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-2xl text-left flex items-center justify-between"
                >
                  <div className="flex items-center space-x-3">
                    <div className="p-2 rounded-xl bg-surface-dangerSubtle text-content-danger">
                      <PackageX className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-xs font-extrabold text-white">4. Damaged or Leaking Package</h4>
                      <p className="text-2xs text-content-muted">Item damaged prior to or during transit</p>
                    </div>
                  </div>
                </button>

                {/* 5. Initiate Return to Store */}
                <button
                  onClick={() => setActiveTab('return')}
                  className="w-full p-3.5 bg-surface-inverse hover:bg-surface-inverse border border-border-strong rounded-2xl text-left flex items-center justify-between"
                >
                  <div className="flex items-center space-x-3">
                    <div className="p-2 rounded-xl bg-surface-dangerSubtle text-content-danger border border-border-danger">
                      <ShieldAlert className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-xs font-extrabold text-white">5. Cancel & Return Items to Store</h4>
                      <p className="text-2xs text-content-muted">Initiate immediate return trip to merchant</p>
                    </div>
                  </div>
                </button>
              </div>
            </div>
          )}

          {/* 1. Customer Unreachable Tab */}
          {activeTab === 'unreachable' && (
            <div className="space-y-4 text-center">
              <div className="bg-surface-warningSubtle border border-border-warning rounded-2xl p-5 space-y-2">
                <span className="text-xs font-bold text-content-warning uppercase tracking-widest">Mandatory Wait Timer</span>
                <div className="text-4xl font-black text-content-warning font-mono">
                  0{timerMin}:{timerSec < 10 ? `0${timerSec}` : timerSec}
                </div>
                <p className="text-xs text-content-muted">
                  {timerSeconds > 0
                    ? 'Please call customer at least twice while timer is running'
                    : 'Wait timer completed. You may now submit customer unreachable exception to server.'}
                </p>
              </div>

              {customerPhone && (
                <div className="flex items-center justify-center space-x-3">
                  <a
                    href={`tel:${customerPhone}`}
                    className="px-4 py-2.5 bg-action-primaryBg hover:bg-action-primaryBg text-white rounded-xl text-xs font-bold shadow-md flex items-center space-x-2"
                  >
                    <Phone className="w-4 h-4" />
                    <span>Call Customer Now</span>
                  </a>
                </div>
              )}

              <button
                disabled={timerSeconds > 0}
                onClick={() => {
                  onReportIssue('CUSTOMER_UNREACHABLE', 'Customer unreachable after 3-minute wait timer');
                  onClose();
                }}
                className="w-full py-3.5 bg-action-dangerBg hover:bg-action-dangerBg disabled:opacity-40 text-white font-black text-xs rounded-xl transition-all"
              >
                Submit Server Exception & Start Return
              </button>
            </div>
          )}

          {/* 2. Store Issue Tab */}
          {activeTab === 'store_issue' && (
            <div className="space-y-4">
              <textarea
                value={issueNotes}
                onChange={(e) => setIssueNotes(e.target.value)}
                placeholder="Describe store issue (e.g., Dark store closed, item missing)..."
                className="w-full p-3 bg-surface-inverse border border-border-strong rounded-xl text-xs text-white outline-none h-24"
              />
              <button
                onClick={() => {
                  onReportIssue('STORE_CLOSED', issueNotes || 'Store closed / unavailable');
                  onClose();
                }}
                className="w-full py-3.5 bg-action-speedBg hover:bg-action-speedBg text-white font-black text-xs rounded-xl shadow-lg"
              >
                Submit Store Exception to Server
              </button>
            </div>
          )}

          {/* 3. Wrong Address Tab */}
          {activeTab === 'wrong_address' && (
            <div className="space-y-4">
              <textarea
                value={issueNotes}
                onChange={(e) => setIssueNotes(e.target.value)}
                placeholder="Describe address error (e.g., Pin is 15 km away in wrong city)..."
                className="w-full p-3 bg-surface-inverse border border-border-strong rounded-xl text-xs text-white outline-none h-24"
              />
              <button
                onClick={() => {
                  onReportIssue('WRONG_ADDRESS', issueNotes || 'Customer address pin wrong');
                  onClose();
                }}
                className="w-full py-3.5 bg-action-dangerBg hover:bg-action-dangerBg text-white font-black text-xs rounded-xl shadow-lg"
              >
                Submit Wrong Address Exception
              </button>
            </div>
          )}

          {/* 4. Damaged Package Tab */}
          {activeTab === 'damaged' && (
            <div className="space-y-4">
              <textarea
                value={issueNotes}
                onChange={(e) => setIssueNotes(e.target.value)}
                placeholder="Describe damage (e.g., Liquid spillage, crushed box)..."
                className="w-full p-3 bg-surface-inverse border border-border-strong rounded-xl text-xs text-white outline-none h-24"
              />
              <button
                onClick={() => {
                  onReportIssue('DAMAGED_PACKAGE', issueNotes || 'Package damaged');
                  onClose();
                }}
                className="w-full py-3.5 bg-action-dangerBg hover:bg-action-dangerBg text-white font-black text-xs rounded-xl shadow-lg"
              >
                Submit Damaged Item Exception
              </button>
            </div>
          )}

          {/* 5. Initiate Return Tab */}
          {activeTab === 'return' && (
            <div className="space-y-4">
              <p className="text-xs text-content-muted">
                Confirm return of items back to merchant store. This will update server state to RETURNED and redirect route to store.
              </p>
              <textarea
                value={issueNotes}
                onChange={(e) => setIssueNotes(e.target.value)}
                placeholder="Reason for return (Customer refusal, damaged package, etc.)..."
                className="w-full p-3 bg-surface-inverse border border-border-strong rounded-xl text-xs text-white outline-none h-24"
              />
              <button
                onClick={() => {
                  onReportIssue('RETURN_TO_STORE', issueNotes || 'Rider initiated return');
                  onClose();
                }}
                className="w-full py-3.5 bg-action-dangerBg hover:bg-action-dangerBg text-white font-black text-xs rounded-xl shadow-lg"
              >
                Confirm & Start Store Return Trip
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
