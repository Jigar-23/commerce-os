'use client';

import React, { useEffect, useState, useCallback } from 'react';
import {
  BarChart3, Truck, IndianRupee, ShieldAlert, CheckCircle2, RefreshCw,
  Smartphone, Monitor, MapPin, Key, Wifi, WifiOff, Star, ShieldCheck, Zap, User, Clock
} from 'lucide-react';

import { RiderHeader } from '../components/RiderHeader';
import { NavigationMap } from '../components/NavigationMap';
import { OrderOfferModal } from '../components/OrderOfferModal';
import { ActiveDeliveryCard } from '../components/ActiveDeliveryCard';
import { CodCollectionSheet } from '../components/CodCollectionSheet';
import { OtpVerificationSheet } from '../components/OtpVerificationSheet';
import { HelpIssueModal } from '../components/HelpIssueModal';
import { DeliveryHistory } from '../components/DeliveryHistory';
import { EarningsView } from '../components/EarningsView';

const isProduction = process.env.NODE_ENV === 'production';
const ORDER_API = process.env.NEXT_PUBLIC_ORDER_API_URL && process.env.NEXT_PUBLIC_ORDER_API_URL.trim().length > 0
  ? process.env.NEXT_PUBLIC_ORDER_API_URL.replace(/\/$/, '')
  : isProduction ? '' : 'http://localhost:8090';

const STORAGE_KEY = 'commerce_rider_delivery_session_v2';
const COMMAND_QUEUE_KEY = 'commerce_rider_offline_command_queue';

interface DeliverySession {
  deliveryId: string;
  orderId: string;
  state: string;
  isCod: boolean;
  codAmount: number;
  codCollectedAmount: number;
  codReconciled: boolean;
  otpVerified: boolean;
  otpAttemptsLeft: number;
  customerName: string;
  customerPhone: string;
  customerAddress: string;
  merchantName: string;
  merchantAddress: string;
}

interface QueuedCommand {
  id: string;
  type: 'TRANSITION' | 'COD' | 'OTP';
  deliveryId: string;
  payload: any;
  timestamp: number;
}

export default function DeliveryRiderApp() {
  const [viewMode, setViewMode] = useState<'mobile' | 'fullscreen'>('mobile');
  const [activeTab, setActiveTab] = useState<'duty' | 'earnings' | 'history' | 'profile'>('duty');

  // Duty & Rider Context State
  const [isOnline, setIsOnline] = useState(true);
  const [networkStatus, setNetworkStatus] = useState<'ONLINE' | 'RECONNECTING' | 'OFFLINE'>('ONLINE');
  const [gpsStatus, setGpsStatus] = useState<'HIGH_PRECISION' | 'WEAK_SIGNAL' | 'UNAVAILABLE' | 'PERMISSION_DENIED'>('HIGH_PRECISION');
  const [gpsStaleSeconds, setGpsStaleSeconds] = useState(0);
  const [pendingSyncQueue, setPendingSyncQueue] = useState<QueuedCommand[]>([]);

  // Delivery Session & Overlays
  const [session, setSession] = useState<DeliverySession | null>(null);
  const [activeOffer, setActiveOffer] = useState<any | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [showCodSheet, setShowCodSheet] = useState(false);
  const [showOtpSheet, setShowOtpSheet] = useState(false);
  const [showHelpModal, setShowHelpModal] = useState(false);
  const [notificationMsg, setNotificationMsg] = useState<string | null>(null);

  const [riderInfo, setRiderInfo] = useState<{ name: string; id: string; vehicle: string; rating: number | null; zone: string } | null>(null);

  const getAuthToken = () => {
    try {
      return localStorage.getItem('commerce_rider_jwt_token') || sessionStorage.getItem('commerce_rider_jwt_token') || '';
    } catch {
      return '';
    }
  };

  const getAuthHeader = (): Record<string, string> => {
    const token = getAuthToken();
    return token ? { 'Authorization': `Bearer ${token}` } : {};
  };

  // Helper: Persist and restore queued offline commands
  const saveQueueToDisk = (queue: QueuedCommand[]) => {
    setPendingSyncQueue(queue);
    try {
      localStorage.setItem(COMMAND_QUEUE_KEY, JSON.stringify(queue));
    } catch {
      // Storage fallback
    }
  };

  const loadQueueFromDisk = useCallback(() => {
    try {
      const raw = localStorage.getItem(COMMAND_QUEUE_KEY);
      if (raw) {
        setPendingSyncQueue(JSON.parse(raw));
      }
    } catch {
      // Storage fallback
    }
  }, []);

  const loadRiderProfile = useCallback(async () => {
    const token = getAuthToken();
    if (!token) {
      setRiderInfo(null);
      return;
    }
    try {
      const res = await fetch(`${ORDER_API}/api/v1/delivery/rider/profile`, {
        headers: getAuthHeader(),
      });
      if (res.ok) {
        const data = await res.json();
        setRiderInfo({
          name: data.name || 'Rider Partner',
          id: data.riderId || '',
          vehicle: data.vehicleNumber || data.vehicleType || 'Vehicle',
          rating: data.rating ?? null,
          zone: data.zone || 'Zone Unavailable',
        });
      } else {
        setRiderInfo(null);
      }
    } catch {
      setRiderInfo(null);
    }
  }, []);

  // 1. Fetch Authoritative Delivery Session on Mount & Restore Storage
  const loadDeliverySession = useCallback(async () => {
    setIsLoading(true);
    const token = getAuthToken();
    try {
      const cached = localStorage.getItem(STORAGE_KEY);
      if (cached) {
        setSession(JSON.parse(cached));
      }

      if (!token) {
        setSession(null);
        return;
      }

      const res = await fetch(`${ORDER_API}/api/v1/delivery/rider/active-session`, {
        headers: getAuthHeader(),
      });
      if (res.ok) {
        const data = await res.json();
        setSession(data);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
        setNetworkStatus('ONLINE');
      } else if (res.status === 404) {
        // Authoritative server response: No active session -> clear stale local cache
        setSession(null);
        localStorage.removeItem(STORAGE_KEY);
      } else {
        setSession(null);
      }
    } catch {
      setNetworkStatus('OFFLINE');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRiderProfile();
    loadDeliverySession();
    loadQueueFromDisk();
  }, [loadRiderProfile, loadDeliverySession, loadQueueFromDisk]);

  // 2. REAL-TIME SSE EVENT STREAM SUBSCRIPTION VIA SECURE SINGLE-USE TICKET
  useEffect(() => {
    if (!session?.orderId && !session?.deliveryId) return;

    let eventSource: EventSource | null = null;
    let isCancelled = false;

    const setupSseStream = async () => {
      const idParam = session.deliveryId || session.orderId;
      let ticketParam = '';
      try {
        const ticketRes = await fetch(`${ORDER_API}/api/v1/delivery/sse-ticket`, {
          method: 'POST',
          headers: {
            ...getAuthHeader(),
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ targetId: idParam }),
        });
        if (ticketRes.ok) {
          const ticketData = await ticketRes.json();
          if (ticketData.ticket) {
            ticketParam = `?ticket=${encodeURIComponent(ticketData.ticket)}`;
          }
        }
      } catch {
        // Continue if ticket acquisition fails
      }

      if (isCancelled) return;

      const streamUrl = `${ORDER_API}/api/v1/delivery/session/${idParam}/stream${ticketParam}`;
      eventSource = new EventSource(streamUrl);

      eventSource.onopen = () => {
        setNetworkStatus('ONLINE');
      };

      eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.session) {
            setSession(data.session);
            localStorage.setItem(STORAGE_KEY, JSON.stringify(data.session));
          }
          if (data.eventType === 'STATE_TRANSITION') {
            setNotificationMsg(`Realtime Update: Delivery state is now ${data.deliveryState}`);
          }
        } catch {
          // Parse error fallback
        }
      };

      eventSource.onerror = () => {
        setNetworkStatus('RECONNECTING');
      };
    };

    setupSseStream();

    return () => {
      isCancelled = true;
      eventSource?.close();
    };
  }, [session?.orderId, session?.deliveryId]);

  // 3. BACKGROUND REPLAY WORKER FOR QUEUED OFFLINE COMMANDS
  useEffect(() => {
    if (pendingSyncQueue.length === 0 || networkStatus !== 'ONLINE') return;

    const replayQueue = async () => {
      const queueCopy = [...pendingSyncQueue];
      const remaining: QueuedCommand[] = [];

      for (const cmd of queueCopy) {
        try {
          let res: Response;
          const headers = { 'Content-Type': 'application/json', ...getAuthHeader() };
          if (cmd.type === 'TRANSITION') {
            res = await fetch(`${ORDER_API}/api/v1/delivery/${cmd.deliveryId}/transition`, {
              method: 'POST',
              headers,
              body: JSON.stringify(cmd.payload),
            });
          } else if (cmd.type === 'COD') {
            res = await fetch(`${ORDER_API}/api/v1/delivery/${cmd.deliveryId}/complete-cod`, {
              method: 'POST',
              headers,
              body: JSON.stringify(cmd.payload),
            });
          } else {
            res = await fetch(`${ORDER_API}/api/v1/delivery/${cmd.deliveryId}/verify-otp`, {
              method: 'POST',
              headers,
              body: JSON.stringify(cmd.payload),
            });
          }

          if (!res.ok) {
            remaining.push(cmd);
          }
        } catch {
          remaining.push(cmd);
        }
      }

      saveQueueToDisk(remaining);
    };

    replayQueue();
  }, [networkStatus, pendingSyncQueue]);

  // 4. Server State Machine Transition Handler with Durable Idempotency
  const handleTransitionState = async (targetState: string) => {
    if (!session || isSubmitting) return;
    setIsSubmitting(true);
    setNotificationMsg(null);

    const idempotencyKey = `idem_${targetState}_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`;
    const prevState = session.state;

    // Optimistic UI update
    const updatedSession = { ...session, state: targetState };
    setSession(updatedSession);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updatedSession));

    try {
      const res = await fetch(`${ORDER_API}/api/v1/delivery/${session.deliveryId || session.orderId}/transition`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeader(),
        },
        body: JSON.stringify({ targetState, idempotencyKey }),
      });

      if (!res.ok) {
        const errorData = await res.json();
        // Rollback optimistic state on server rejection
        setSession({ ...session, state: prevState });
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...session, state: prevState }));
        setNotificationMsg(`Server rejection: ${errorData.message || 'Invalid transition'}`);
      } else {
        const data = await res.json();
        setSession(data);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
      }
    } catch {
      // Durable Offline Queue Fallback
      const queuedCmd: QueuedCommand = {
        id: idempotencyKey,
        type: 'TRANSITION',
        deliveryId: session.deliveryId || session.orderId,
        payload: { targetState, idempotencyKey },
        timestamp: Date.now(),
      };
      saveQueueToDisk([...pendingSyncQueue, queuedCmd]);
      setNotificationMsg('Network offline. Action persisted to disk queue & will replay on reconnect.');
    } finally {
      setIsSubmitting(false);
    }
  };

  // 5. COD Cash Reconciliation Handler
  const handleConfirmCod = async (collectedAmount: number) => {
    if (!session) return;
    setIsSubmitting(true);
    setNotificationMsg(null);

    try {
      const res = await fetch(`${ORDER_API}/api/v1/delivery/${session.deliveryId || session.orderId}/complete-cod`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeader(),
        },
        body: JSON.stringify({ collectedAmount }),
      });

      if (res.ok) {
        const updated = { ...session, codReconciled: true, codCollectedAmount: collectedAmount };
        setSession(updated);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
        setShowCodSheet(false);
        setNotificationMsg('Doorstep Cash Collection Confirmed & Reconciled!');
      } else {
        const err = await res.json();
        throw new Error(err.message || 'COD reconciliation failed');
      }
    } catch (e: any) {
      throw e;
    } finally {
      setIsSubmitting(false);
    }
  };

  // 6. OTP PIN Verification Handler
  const handleVerifyOtp = async (otp: string) => {
    if (!session) return;
    setIsSubmitting(true);
    setNotificationMsg(null);

    try {
      const res = await fetch(`${ORDER_API}/api/v1/delivery/${session.deliveryId || session.orderId}/verify-otp`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeader(),
        },
        body: JSON.stringify({ otp }),
      });

      if (res.ok) {
        const updated = { ...session, otpVerified: true };
        setSession(updated);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
        setShowOtpSheet(false);
        setNotificationMsg('OTP PIN Verified Successfully!');

        // Complete delivery call
        await fetch(`${ORDER_API}/api/v1/delivery/${session.deliveryId || session.orderId}/complete`, {
          method: 'POST',
          headers: getAuthHeader(),
        });
      } else {
        const err = await res.json();
        throw new Error(err.message || 'Incorrect OTP code');
      }
    } catch (e: any) {
      throw e;
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-inverse text-content-inverse flex flex-col font-sans antialiased">
      {/* DEVICE VIEWPORT TOGGLE BAR (TOP RIGHT) */}
      <div className="bg-surface-inverse px-6 py-2 border-b border-border-strong flex items-center justify-between text-xs">
        <div className="flex items-center space-x-2 text-content-muted font-bold">
          <span className="w-2 h-2 rounded-full bg-action-primaryBg animate-pulse" />
          <span>Commerce OS Rider Partner App (Realtime SSE Engine)</span>
        </div>

        <div className="flex items-center space-x-2">
          <span className="text-content-muted font-bold text-2xs">View Container:</span>
          <button
            onClick={() => setViewMode('mobile')}
            className={`px-3 py-1 rounded-lg font-black text-2xs flex items-center space-x-1.5 transition-all ${
              viewMode === 'mobile'
                ? 'bg-action-primaryBg text-content-primary shadow-md'
                : 'bg-surface-inverse text-content-muted hover:text-white'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Mobile Device Frame</span>
          </button>
          <button
            onClick={() => setViewMode('fullscreen')}
            className={`px-3 py-1 rounded-lg font-black text-2xs flex items-center space-x-1.5 transition-all ${
              viewMode === 'fullscreen'
                ? 'bg-action-primaryBg text-content-primary shadow-md'
                : 'bg-surface-inverse text-content-muted hover:text-white'
            }`}
          >
            <Monitor className="w-3.5 h-3.5" />
            <span>Full Desktop View</span>
          </button>
        </div>
      </div>

      {/* MAIN CONTAINER WRAPPER */}
      <div className="flex-1 flex items-center justify-center p-0 sm:p-4 md:p-6 overflow-y-auto">
        <div
          className={`w-full bg-surface-inverse border border-border-strong flex flex-col transition-all overflow-hidden relative ${
            viewMode === 'mobile'
              ? 'max-w-[440px] min-h-[840px] rounded-3xl shadow-2xl ring-8 ring-surface-inverse/80'
              : 'max-w-4xl min-h-[800px] rounded-3xl shadow-2xl'
          }`}
        >
          {/* Rider Header Bar */}
          <RiderHeader
            isOnline={isOnline}
            setIsOnline={(online) => {
              setIsOnline(online);
              if (!online) setActiveOffer(null);
            }}
            gpsStatus={gpsStatus}
            gpsStaleSeconds={gpsStaleSeconds}
            networkStatus={networkStatus}
            pendingSyncCount={pendingSyncQueue.length}
            riderInfo={riderInfo}
          />

          {/* Toast / Notification Banner */}
          {notificationMsg && (
            <div className="bg-action-speedBg text-white px-4 py-2 text-xs font-bold text-center border-b border-border-accent flex items-center justify-between">
              <span>{notificationMsg}</span>
              <button onClick={() => setNotificationMsg(null)} className="text-content-accent">✕</button>
            </div>
          )}

          {/* MAIN TAB CONTENT AREA */}
          <main className="flex-1 p-4 overflow-y-auto space-y-4">
            {activeTab === 'duty' && (
              <>
                {!isOnline ? (
                  /* Offline State Display */
                  <div className="py-16 text-center space-y-4">
                    <div className="w-16 h-16 rounded-3xl bg-surface-inverse border border-border-strong text-content-secondary flex items-center justify-center mx-auto">
                      <Zap className="w-8 h-8 text-content-secondary" />
                    </div>
                    <h3 className="text-base font-black text-white">You Are Currently Duty Off</h3>
                    <p className="text-xs text-content-muted max-w-xs mx-auto">
                      Toggle 'DUTY ON' in the header bar above to start receiving instant delivery assignments in your zone.
                    </p>
                    <button
                      onClick={() => setIsOnline(true)}
                      className="px-6 py-3 bg-action-primaryBg hover:bg-action-primaryHover text-content-primary font-black text-xs rounded-xl shadow-lg"
                    >
                      GO ONLINE NOW
                    </button>
                  </div>
                ) : !session || session.state === 'DELIVERED' ? (
                  /* Online Idle / Waiting Screen: State-Driven Calm Experience */
                  <div className="space-y-6 py-2">
                    {/* Greeting & Online Status */}
                    <div className="flex items-center justify-between">
                      <div>
                        <h2 className="text-xl sm:text-2xl font-black text-white">
                          Good {new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 17 ? 'afternoon' : 'evening'}, Rahul 👋
                        </h2>
                        <div className="flex items-center space-x-2 mt-1">
                          <span className="w-2.5 h-2.5 rounded-full bg-action-primaryBg animate-pulse" />
                          <span className="text-xs font-black text-content-brand uppercase tracking-wider">Online</span>
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Zone</span>
                        <p className="text-xs font-extrabold text-white">Sector 18 Hub</p>
                      </div>
                    </div>

                    {/* Today's Shift Earnings Card */}
                    <div className="bg-surface-inverse border border-border-strong rounded-3xl p-5 shadow-xl">
                      <span className="text-2xs font-bold text-content-muted uppercase tracking-wider">Today's Earnings</span>
                      <div className="flex items-baseline justify-between mt-2">
                        <div>
                          <span className="text-3xl font-black text-content-brand">₹1,240</span>
                          <span className="text-xs font-bold text-content-muted ml-2">today</span>
                        </div>
                        <div className="text-right">
                          <span className="text-lg font-black text-white">8</span>
                          <span className="text-xs font-bold text-content-muted ml-1.5">deliveries</span>
                        </div>
                      </div>
                    </div>

                    {/* Waiting Message with generous whitespace */}
                    <div className="py-6 text-center space-y-1.5">
                      <p className="text-sm font-black text-white">You're ready.</p>
                      <p className="text-xs text-content-muted max-w-xs mx-auto">
                        Waiting for your next delivery…
                      </p>
                    </div>

                    {/* Default Map Preview */}
                    <NavigationMap
                      deliveryState="IDLE"
                      gpsStaleSeconds={gpsStaleSeconds}
                      gpsStatus={gpsStatus}
                    />
                  </div>
                ) : (
                  /* Active Delivery Mode: Interactive Map + Active Delivery Card */
                  <div className="space-y-4">
                    <NavigationMap
                      deliveryState={session.state}
                      storeName={session.merchantName}
                      storeAddress={session.merchantAddress}
                      customerName={session.customerName}
                      customerAddress={session.customerAddress}
                      gpsStaleSeconds={gpsStaleSeconds}
                      gpsStatus={gpsStatus}
                    />

                    <ActiveDeliveryCard
                      order={{
                        id: session.orderId,
                        deliveryId: session.deliveryId,
                        orderStatus: session.state,
                        deliveryState: session.state,
                        totalAmount: session.codAmount,
                        paymentMethod: session.isCod ? 'COD' : 'PREPAID',
                        paymentStatus: session.codReconciled ? 'COD_COLLECTED' : 'PENDING',
                        storeName: session.merchantName,
                        storeAddress: session.merchantAddress,
                        customerName: session.customerName,
                        customerPhone: session.customerPhone,
                        deliveryAddress: session.customerAddress,
                      }}
                      onTransitionState={handleTransitionState}
                      onOpenHelpModal={() => setShowHelpModal(true)}
                      onOpenCodSheet={() => setShowCodSheet(true)}
                      onOpenOtpSheet={() => setShowOtpSheet(true)}
                      isSubmitting={isSubmitting}
                      otpVerified={session.otpVerified}
                      codReconciled={session.codReconciled}
                    />
                  </div>
                )}
              </>
            )}

            {activeTab === 'earnings' && <EarningsView />}

            {activeTab === 'history' && <DeliveryHistory />}

            {activeTab === 'profile' && (
              <div className="space-y-4 text-white">
                {riderInfo ? (
                  <>
                    <div className="bg-surface-inverse border border-border-strong rounded-2xl p-5 flex items-center space-x-4">
                      <div className="w-14 h-14 rounded-2xl bg-action-primaryBg text-content-primary font-black text-2xl flex items-center justify-center">
                        {riderInfo.name ? riderInfo.name.charAt(0) : 'R'}
                      </div>
                      <div>
                        <h3 className="text-base font-extrabold">{riderInfo.name || 'Rider Partner'}</h3>
                        <p className="text-xs text-content-muted">{riderInfo.id || 'ID Unavailable'} • {riderInfo.zone || 'Zone Unavailable'}</p>
                        <span className="text-2xs px-2 py-0.5 rounded bg-surface-inverse text-content-brand border border-border-brand mt-1 inline-block">
                          Verified Rider Partner {riderInfo.rating != null ? `★ ${riderInfo.rating}` : ''}
                        </span>
                      </div>
                    </div>

                    <div className="bg-surface-inverse border border-border-strong rounded-2xl p-4 space-y-3 text-xs">
                      <div className="flex justify-between py-2 border-b border-border-strong">
                        <span className="text-content-muted font-bold">Vehicle Info</span>
                        <span className="font-extrabold text-white">{riderInfo.vehicle || 'Vehicle Unavailable'}</span>
                      </div>
                    </div>
                  </>
                ) : (
                  <div className="bg-surface-inverse border border-border-strong rounded-2xl p-5 text-center text-content-muted text-sm">
                    Profile Data Unavailable (Authentication Required)
                  </div>
                )}
              </div>
            )}
          </main>

          {/* OVERLAY MODALS & SHEETS */}
          {activeOffer && (
            <OrderOfferModal
              offer={activeOffer}
              onAccept={() => {
                handleTransitionState('ACCEPTED');
                setActiveOffer(null);
                setActiveTab('duty');
              }}
              onDecline={() => setActiveOffer(null)}
            />
          )}

          {showCodSheet && session && (
            <div className="fixed inset-0 z-50 bg-surface-inverse/90 backdrop-blur-sm flex items-end sm:items-center justify-center p-4">
              <div className="w-full max-w-lg">
                <CodCollectionSheet
                  orderId={session.orderId}
                  requiredAmount={session.codAmount}
                  onConfirmCod={handleConfirmCod}
                  onReportMismatch={(collected, reason) => {
                    setShowCodSheet(false);
                    setShowHelpModal(true);
                  }}
                  isSubmitting={isSubmitting}
                />
              </div>
            </div>
          )}

          {showOtpSheet && session && (
            <div className="fixed inset-0 z-50 bg-surface-inverse/90 backdrop-blur-sm flex items-end sm:items-center justify-center p-4">
              <div className="w-full max-w-lg">
                <OtpVerificationSheet
                  orderId={session.orderId}
                  customerPhone={session.customerPhone}
                  attemptsLeft={session.otpAttemptsLeft}
                  onVerifyOtp={handleVerifyOtp}
                  onResendOtp={() => {
                    fetch(`${ORDER_API}/api/v1/delivery/${session.deliveryId || session.orderId}/resend-otp`, {
                      method: 'POST',
                    });
                    setNotificationMsg('New OTP code generated & sent to customer phone');
                  }}
                  isSubmitting={isSubmitting}
                />
              </div>
            </div>
          )}

          {showHelpModal && session && (
            <HelpIssueModal
              orderId={session.orderId}
              customerPhone={session.customerPhone}
              storePhone=""
              onClose={() => setShowHelpModal(false)}
              onInitiateReturn={(reason) => {
                handleTransitionState('RETURN_INITIATED');
              }}
              onReportIssue={(type, details) => {
                setNotificationMsg(`Issue reported: ${details}`);
              }}
            />
          )}

          {/* BOTTOM NAVIGATION BAR */}
          <footer className="bg-surface-inverse border-t border-border-strong p-2 flex items-center justify-around shrink-0 z-30">
            <button
              onClick={() => setActiveTab('duty')}
              className={`flex flex-col items-center py-1 px-4 rounded-xl transition-all ${
                activeTab === 'duty' ? 'text-content-brand font-extrabold' : 'text-content-muted hover:text-content-subtle'
              }`}
            >
              <Truck className="w-5 h-5" />
              <span className="text-2xs mt-0.5">Duty & Map</span>
            </button>

            <button
              onClick={() => setActiveTab('earnings')}
              className={`flex flex-col items-center py-1 px-4 rounded-xl transition-all ${
                activeTab === 'earnings' ? 'text-content-brand font-extrabold' : 'text-content-muted hover:text-content-subtle'
              }`}
            >
              <IndianRupee className="w-5 h-5" />
              <span className="text-2xs mt-0.5">Earnings</span>
            </button>

            <button
              onClick={() => setActiveTab('history')}
              className={`flex flex-col items-center py-1 px-4 rounded-xl transition-all ${
                activeTab === 'history' ? 'text-content-brand font-extrabold' : 'text-content-muted hover:text-content-subtle'
              }`}
            >
              <BarChart3 className="w-5 h-5" />
              <span className="text-2xs mt-0.5">History</span>
            </button>

            <button
              onClick={() => setActiveTab('profile')}
              className={`flex flex-col items-center py-1 px-4 rounded-xl transition-all ${
                activeTab === 'profile' ? 'text-content-brand font-extrabold' : 'text-content-muted hover:text-content-subtle'
              }`}
            >
              <User className="w-5 h-5" />
              <span className="text-2xs mt-0.5">Profile</span>
            </button>
          </footer>
        </div>
      </div>
    </div>
  );
}
