'use client';

import React, { useEffect, useState } from 'react';
import SellerSidebar from '@/components/SellerSidebar';
import HeaderQuickSearch from '@/components/HeaderQuickSearch';
import SellerAuthGuard from '@/components/SellerAuthGuard';
import sellerApi, { BroadcastNotification, NotificationStats } from '@/lib/apiClient';
import {
  Bell,
  Send,
  Smartphone,
  Users,
  Bike,
  CheckCircle2,
  AlertCircle,
  Sparkles,
  RefreshCw,
  Image as ImageIcon,
  ArrowRight,
  Eye,
  X,
  Radio,
} from 'lucide-react';

export default function NotificationsPage() {
  const [stats, setStats] = useState<NotificationStats>({
    totalDevices: 2,
    customerDevices: 0,
    riderDevices: 2,
    totalBroadcastsSent: 4,
    fcmProvider: 'Google Firebase Cloud Messaging (FCM HTTP v1 / Topics)',
    isFreeTier: true,
    costModel: 'Free & Unlimited',
    topics: [
      { id: 'all_devices', name: 'All App Installs (Everyone)', topic: '/topics/all_devices' },
      { id: 'customers', name: 'All Customers Only', topic: '/topics/customers' },
      { id: 'riders', name: 'All Delivery Fleet Riders Only', topic: '/topics/riders' },
    ],
  });

  const [broadcasts, setBroadcasts] = useState<BroadcastNotification[]>([
    {
      id: 'bcast_demo_1',
      title: '⚡ 10-Minute Medicine Delivery is Live!',
      body: 'Order genuine medicines and wellness essentials with instant doorstep delivery across Rewari.',
      targetAudience: 'ALL',
      category: 'ANNOUNCEMENT',
      imageUrl: 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=800',
      deepLink: 'commerceos://home',
      recipientsCount: 2,
      fcmStatus: 'DELIVERED',
      sentBy: 'Rewari Central Hub',
      createdAt: new Date(Date.now() - 7200000).toISOString(),
    },
    {
      id: 'bcast_demo_2',
      title: '🎉 Flat 20% OFF on All Healthcare Essentials',
      body: 'Use coupon code HEALTH20 at checkout today. Free express delivery on orders over ₹199!',
      targetAudience: 'CUSTOMERS',
      category: 'PROMOTION',
      imageUrl: 'https://images.unsplash.com/photo-1471864190281-a93a3070b6de?w=800',
      deepLink: 'commerceos://offers',
      recipientsCount: 2,
      fcmStatus: 'DELIVERED',
      sentBy: 'Rewari Central Hub',
      createdAt: new Date(Date.now() - 86400000).toISOString(),
    },
  ]);

  const [isLoading, setIsLoading] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastType, setToastType] = useState<'success' | 'error'>('success');
  const [previewImageModal, setPreviewImageModal] = useState<string | null>(null);

  // Form State
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [targetAudience, setTargetAudience] = useState<'ALL' | 'CUSTOMERS' | 'RIDERS'>('ALL');
  const [category, setCategory] = useState<'PROMOTION' | 'ANNOUNCEMENT' | 'URGENT' | 'HEALTH_TIP'>('PROMOTION');
  const [imageUrl, setImageUrl] = useState('');
  const [deepLink, setDeepLink] = useState('commerceos://home');
  const [previewStyle, setPreviewStyle] = useState<'banner' | 'lockscreen'>('banner');

  const fetchData = async () => {
    setIsLoading(true);
    try {
      const [statsRes, listRes] = await Promise.all([
        sellerApi.getNotificationStats(),
        sellerApi.getBroadcasts(),
      ]);

      if (statsRes && statsRes.data) {
        setStats(statsRes.data);
      }
      if (listRes && listRes.data && listRes.data.broadcasts && listRes.data.broadcasts.length > 0) {
        setBroadcasts(listRes.data.broadcasts);
      }
    } catch (e) {
      console.warn('[NotificationsPage] Fetch fallback:', e);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const showToast = (msg: string, type: 'success' | 'error' = 'success') => {
    setToastMessage(msg);
    setToastType(type);
    setTimeout(() => setToastMessage(null), 4000);
  };

  const handleSendBroadcast = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !body.trim()) {
      showToast('Please provide both notification title and message body', 'error');
      return;
    }

    setIsSending(true);
    try {
      const res = await sellerApi.sendBroadcast({
        title: title.trim(),
        body: body.trim(),
        targetAudience,
        category,
        imageUrl: imageUrl.trim() || undefined,
        deepLink: deepLink.trim() || undefined,
      });

      if (res && res.ok) {
        showToast(
          `Broadcast sent successfully to ${
            targetAudience === 'ALL' ? 'all devices' : targetAudience.toLowerCase()
          }!`,
          'success'
        );

        const newRecord: BroadcastNotification = {
          id: res.data?.broadcastId || `bcast_${Date.now()}`,
          title: title.trim(),
          body: body.trim(),
          targetAudience,
          category,
          imageUrl: imageUrl.trim() || null,
          deepLink: deepLink.trim() || null,
          recipientsCount:
            res.data?.recipientsCount ||
            (targetAudience === 'ALL'
              ? stats.totalDevices
              : targetAudience === 'CUSTOMERS'
              ? stats.customerDevices
              : stats.riderDevices),
          fcmStatus: 'DELIVERED',
          sentBy: 'Rewari Central Hub',
          createdAt: new Date().toISOString(),
        };
        setBroadcasts((prev) => [newRecord, ...prev]);
        setStats((prev) => ({ ...prev, totalBroadcastsSent: prev.totalBroadcastsSent + 1 }));

        setTitle('');
        setBody('');
        setImageUrl('');
      } else {
        showToast(res.error || 'Failed to dispatch broadcast notification.', 'error');
      }
    } catch (err: any) {
      showToast(err.message || 'Error occurred while communicating with notification dispatch gateway.', 'error');
    } finally {
      setIsSending(false);
    }
  };

  const applyTemplate = (t: {
    title: string;
    body: string;
    cat: 'PROMOTION' | 'ANNOUNCEMENT' | 'URGENT' | 'HEALTH_TIP';
    link: string;
    img?: string;
  }) => {
    setTitle(t.title);
    setBody(t.body);
    setCategory(t.cat);
    setDeepLink(t.link);
    setImageUrl(t.img || '');
    showToast('Template loaded into composer.', 'success');
  };

  return (
    <SellerAuthGuard>
      <div className="flex h-screen bg-[#F8FAFC] text-slate-900 font-sans antialiased overflow-hidden">
        <SellerSidebar activeTab="notifications" onRefresh={fetchData} isLoading={isLoading} />

        <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
          {/* Header Bar */}
          <header className="h-16 border-b border-slate-200/80 bg-white/90 backdrop-blur-md px-8 flex items-center justify-between shrink-0 gap-4 sticky top-0 z-10">
            <div className="flex items-center space-x-3">
              <span className="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full bg-slate-100 text-slate-700 text-xs font-medium border border-slate-200/60">
                <Bell className="w-3.5 h-3.5 text-slate-600" />
                <span>Broadcast Notifications</span>
              </span>
              <span className="text-slate-300">/</span>
              <span className="text-xs font-semibold text-slate-800">Rewari Central Hub</span>
            </div>

            <div className="flex items-center space-x-3">
              <HeaderQuickSearch />
              <button
                onClick={fetchData}
                disabled={isLoading}
                className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-all disabled:opacity-50"
                title="Sync device stats"
              >
                <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-slate-900' : ''}`} />
              </button>
            </div>
          </header>

          {/* Toast */}
          {toastMessage && (
            <div
              className={`fixed top-5 right-5 z-50 px-4 py-2.5 rounded-full shadow-lg font-medium text-xs flex items-center space-x-2 transition-all border ${
                toastType === 'success'
                  ? 'bg-slate-900 text-white border-slate-800'
                  : 'bg-rose-900 text-white border-rose-800'
              }`}
            >
              {toastType === 'success' ? (
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              ) : (
                <AlertCircle className="w-4 h-4 text-rose-400" />
              )}
              <span>{toastMessage}</span>
            </div>
          )}

          <main className="p-6 md:p-8 space-y-6 max-w-7xl w-full mx-auto">
            {/* Page Title */}
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-slate-900">Push Notifications &amp; Alerts</h1>
              <p className="text-xs sm:text-sm text-slate-500 mt-1">
                Dispatch instant push alerts with rich images and deep links to all installed customer and courier devices.
              </p>
            </div>

            {/* Metrics Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] flex items-center justify-between">
                <div>
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Broadcast Channel
                  </p>
                  <p className="text-xl font-bold text-slate-900 mt-1">/topics/all_devices</p>
                  <p className="text-[11px] text-emerald-600 font-medium mt-0.5">Reaches every install</p>
                </div>
                <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-700">
                  <Radio className="w-5 h-5" />
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] flex items-center justify-between">
                <div>
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Customer Topic
                  </p>
                  <p className="text-xl font-bold text-slate-900 mt-1">/topics/customers</p>
                  <p className="text-[11px] text-slate-500 mt-0.5">Direct app audience</p>
                </div>
                <div className="w-10 h-10 rounded-xl bg-blue-50 flex items-center justify-center text-blue-600">
                  <Users className="w-5 h-5" />
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] flex items-center justify-between">
                <div>
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Active Fleet Couriers
                  </p>
                  <p className="text-xl font-bold text-slate-900 mt-1">{stats.riderDevices} Riders</p>
                  <p className="text-[11px] text-amber-600 font-medium mt-0.5">Verified FCM devices</p>
                </div>
                <div className="w-10 h-10 rounded-xl bg-amber-50 flex items-center justify-center text-amber-600">
                  <Bike className="w-5 h-5" />
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] flex items-center justify-between">
                <div>
                  <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Total Broadcasts
                  </p>
                  <p className="text-xl font-bold text-slate-900 mt-1">{stats.totalBroadcastsSent}</p>
                  <p className="text-[11px] text-slate-500 mt-0.5">Delivered to network</p>
                </div>
                <div className="w-10 h-10 rounded-xl bg-purple-50 flex items-center justify-center text-purple-600">
                  <Send className="w-5 h-5" />
                </div>
              </div>
            </div>

            {/* Quick 1-Click Templates */}
            <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <Sparkles className="w-4 h-4 text-slate-700" />
                  <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-700">
                    Quick Broadcast Templates
                  </h2>
                </div>
                <span className="text-[11px] text-slate-400">Click to fill composer</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <button
                  type="button"
                  onClick={() =>
                    applyTemplate({
                      title: '⚡ 10-Minute Medicine Delivery Now Live!',
                      body: 'Need relief fast? Genuine medicines delivered to your door in 10 minutes across Rewari. Order now!',
                      cat: 'ANNOUNCEMENT',
                      link: 'commerceos://home',
                      img: 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=800',
                    })
                  }
                  className="p-3.5 rounded-xl border border-slate-200 hover:border-slate-400 hover:bg-slate-50 text-left transition group"
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-700">
                      Launch Alert
                    </span>
                    <ArrowRight className="w-3.5 h-3.5 text-slate-400 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                  <p className="text-xs font-semibold text-slate-900 truncate">⚡ 10-Min Delivery Live</p>
                  <p className="text-[11px] text-slate-500 line-clamp-2 mt-0.5">
                    Speed fulfillment announcement across all Rewari zones.
                  </p>
                </button>

                <button
                  type="button"
                  onClick={() =>
                    applyTemplate({
                      title: '🎉 Flat 20% OFF Everything Today!',
                      body: 'Stock up on pain relief, diabetes care, and first-aid kits. Use code HEALTH20 for flat 20% savings + free delivery!',
                      cat: 'PROMOTION',
                      link: 'commerceos://offers',
                      img: 'https://images.unsplash.com/photo-1471864190281-a93a3070b6de?w=800',
                    })
                  }
                  className="p-3.5 rounded-xl border border-slate-200 hover:border-slate-400 hover:bg-slate-50 text-left transition group"
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700">
                      Promo Offer
                    </span>
                    <ArrowRight className="w-3.5 h-3.5 text-slate-400 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                  <p className="text-xs font-semibold text-slate-900 truncate">🎉 Flat 20% OFF Sale</p>
                  <p className="text-[11px] text-slate-500 line-clamp-2 mt-0.5">
                    Special voucher incentive with free express shipping.
                  </p>
                </button>

                <button
                  type="button"
                  onClick={() =>
                    applyTemplate({
                      title: '💊 Time to Refill Your Monthly Prescriptions?',
                      body: 'Ensure your chronic medicines are always stocked. Upload your doctor prescription or reorder your regular meds with 1 tap.',
                      cat: 'HEALTH_TIP',
                      link: 'commerceos://prescriptions',
                      img: 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=800',
                    })
                  }
                  className="p-3.5 rounded-xl border border-slate-200 hover:border-slate-400 hover:bg-slate-50 text-left transition group"
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-blue-50 text-blue-700">
                      Refill Prompt
                    </span>
                    <ArrowRight className="w-3.5 h-3.5 text-slate-400 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                  <p className="text-xs font-semibold text-slate-900 truncate">💊 Monthly Refill Prompt</p>
                  <p className="text-[11px] text-slate-500 line-clamp-2 mt-0.5">
                    Targeted reminder for chronic medicine renewals.
                  </p>
                </button>

                <button
                  type="button"
                  onClick={() =>
                    applyTemplate({
                      title: '🌧️ Heavy Rain Alert: Extended 20-Min Deliveries',
                      body: 'Due to severe weather conditions, our delivery riders are driving safely. All express orders guaranteed within 20 mins.',
                      cat: 'URGENT',
                      link: 'commerceos://home',
                    })
                  }
                  className="p-3.5 rounded-xl border border-slate-200 hover:border-slate-400 hover:bg-slate-50 text-left transition group"
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-rose-50 text-rose-700">
                      Operational
                    </span>
                    <ArrowRight className="w-3.5 h-3.5 text-slate-400 group-hover:translate-x-0.5 transition-transform" />
                  </div>
                  <p className="text-xs font-semibold text-slate-900 truncate">🌧️ Weather &amp; SLA Notice</p>
                  <p className="text-[11px] text-slate-500 line-clamp-2 mt-0.5">
                    Transparent notice for rain or traffic delays.
                  </p>
                </button>
              </div>
            </div>

            {/* Composer and Live Preview */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              {/* Form Composer */}
              <div className="lg:col-span-7 bg-white rounded-2xl border border-slate-200/80 p-6 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-4">
                <div className="border-b border-slate-100 pb-3">
                  <h2 className="text-base font-bold text-slate-900">Broadcast Composer</h2>
                  <p className="text-xs text-slate-500">Send an instant heads-up message with image</p>
                </div>

                <form onSubmit={handleSendBroadcast} className="space-y-4">
                  {/* Target Audience */}
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                      Target Audience
                    </label>
                    <div className="grid grid-cols-3 gap-2">
                      <button
                        type="button"
                        onClick={() => setTargetAudience('ALL')}
                        className={`p-3 rounded-xl border text-center transition flex flex-col items-center justify-center ${
                          targetAudience === 'ALL'
                            ? 'border-slate-900 bg-slate-900 text-white font-semibold shadow-xs'
                            : 'border-slate-200 text-slate-700 hover:bg-slate-50'
                        }`}
                      >
                        <Smartphone className="w-4 h-4 mb-1" />
                        <span className="text-xs">Everyone</span>
                        <span className={`text-[10px] mt-0.5 ${targetAudience === 'ALL' ? 'text-slate-300' : 'text-slate-400'}`}>
                          All Devices
                        </span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setTargetAudience('CUSTOMERS')}
                        className={`p-3 rounded-xl border text-center transition flex flex-col items-center justify-center ${
                          targetAudience === 'CUSTOMERS'
                            ? 'border-slate-900 bg-slate-900 text-white font-semibold shadow-xs'
                            : 'border-slate-200 text-slate-700 hover:bg-slate-50'
                        }`}
                      >
                        <Users className="w-4 h-4 mb-1" />
                        <span className="text-xs">Customers</span>
                        <span className={`text-[10px] mt-0.5 ${targetAudience === 'CUSTOMERS' ? 'text-slate-300' : 'text-slate-400'}`}>
                          Buyers Only
                        </span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setTargetAudience('RIDERS')}
                        className={`p-3 rounded-xl border text-center transition flex flex-col items-center justify-center ${
                          targetAudience === 'RIDERS'
                            ? 'border-slate-900 bg-slate-900 text-white font-semibold shadow-xs'
                            : 'border-slate-200 text-slate-700 hover:bg-slate-50'
                        }`}
                      >
                        <Bike className="w-4 h-4 mb-1" />
                        <span className="text-xs">Fleet Riders</span>
                        <span className={`text-[10px] mt-0.5 ${targetAudience === 'RIDERS' ? 'text-slate-300' : 'text-slate-400'}`}>
                          Couriers Only
                        </span>
                      </button>
                    </div>
                  </div>

                  {/* Category */}
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                      Notification Category
                    </label>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                      {[
                        { id: 'PROMOTION', label: '🏷️ Promotion' },
                        { id: 'ANNOUNCEMENT', label: '📢 Announcement' },
                        { id: 'URGENT', label: '⚡ Urgent Alert' },
                        { id: 'HEALTH_TIP', label: '💊 Health Tip' },
                      ].map((cat) => (
                        <button
                          key={cat.id}
                          type="button"
                          onClick={() => setCategory(cat.id as any)}
                          className={`py-2 px-2.5 rounded-xl border text-xs font-medium transition text-center ${
                            category === cat.id
                              ? 'border-slate-900 bg-slate-900 text-white shadow-xs'
                              : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                          }`}
                        >
                          {cat.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Title */}
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-xs font-semibold text-slate-700">
                        Title <span className="text-rose-500">*</span>
                      </label>
                      <span className="text-[11px] text-slate-400 font-mono">{title.length}/65</span>
                    </div>
                    <input
                      type="text"
                      maxLength={65}
                      required
                      placeholder="e.g. ⚡ Flat 20% OFF on All Medicines Today!"
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-900 text-xs font-medium text-slate-900 placeholder:text-slate-400 transition"
                    />
                  </div>

                  {/* Body */}
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-xs font-semibold text-slate-700">
                        Message Body <span className="text-rose-500">*</span>
                      </label>
                      <span className="text-[11px] text-slate-400 font-mono">{body.length}/220</span>
                    </div>
                    <textarea
                      rows={3}
                      maxLength={220}
                      required
                      placeholder="e.g. Order pain relief, chronic care, or wellness products now for guaranteed 10-minute delivery to your doorstep."
                      value={body}
                      onChange={(e) => setBody(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-900 text-xs text-slate-900 placeholder:text-slate-400 transition resize-none"
                    />
                  </div>

                  {/* Banner Image URL */}
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-xs font-semibold text-slate-700">
                        Banner Image URL (Optional)
                      </label>
                      {imageUrl && (
                        <button
                          type="button"
                          onClick={() => setImageUrl('')}
                          className="text-[11px] text-rose-600 hover:underline"
                        >
                          Clear image
                        </button>
                      )}
                    </div>
                    <div className="relative">
                      <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                        <ImageIcon className="w-4 h-4" />
                      </div>
                      <input
                        type="url"
                        placeholder="https://example.com/banner.jpg"
                        value={imageUrl}
                        onChange={(e) => setImageUrl(e.target.value)}
                        className="w-full pl-9 pr-3.5 py-2.5 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-900 text-xs font-mono text-slate-800 placeholder:text-slate-400 transition"
                      />
                    </div>
                    <div className="flex items-center space-x-2 mt-1.5">
                      <span className="text-[11px] text-slate-400">Quick image presets:</span>
                      <button
                        type="button"
                        onClick={() => setImageUrl('https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=800')}
                        className="text-[11px] font-medium px-2 py-0.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition"
                      >
                        Medicine Pack
                      </button>
                      <button
                        type="button"
                        onClick={() => setImageUrl('https://images.unsplash.com/photo-1471864190281-a93a3070b6de?w=800')}
                        className="text-[11px] font-medium px-2 py-0.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition"
                      >
                        Wellness Banner
                      </button>
                      <button
                        type="button"
                        onClick={() => setImageUrl('https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=800')}
                        className="text-[11px] font-medium px-2 py-0.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition"
                      >
                        Prescriptions
                      </button>
                    </div>
                  </div>

                  {/* Deep Link */}
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      App Screen on Tap
                    </label>
                    <select
                      value={deepLink}
                      onChange={(e) => setDeepLink(e.target.value)}
                      className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-900 text-xs font-medium text-slate-800 bg-white transition"
                    >
                      <option value="commerceos://home">App Home Screen</option>
                      <option value="commerceos://offers">Offers &amp; Coupons Screen</option>
                      <option value="commerceos://cart">Cart &amp; Checkout Screen</option>
                      <option value="commerceos://prescriptions">Prescription Vault Screen</option>
                      <option value="commerceos://orders">Live Orders Screen</option>
                    </select>
                  </div>

                  {/* Submit */}
                  <div className="pt-2">
                    <button
                      type="submit"
                      disabled={isSending || !title.trim() || !body.trim()}
                      className="w-full py-3 px-4 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-medium text-xs flex items-center justify-center space-x-2 shadow-xs disabled:opacity-40 disabled:cursor-not-allowed transition active:scale-98"
                    >
                      {isSending ? (
                        <>
                          <RefreshCw className="w-4 h-4 animate-spin" />
                          <span>Dispatching Broadcast...</span>
                        </>
                      ) : (
                        <>
                          <Send className="w-4 h-4" />
                          <span>Send Broadcast Notification</span>
                        </>
                      )}
                    </button>
                  </div>
                </form>
              </div>

              {/* Smartphone Simulator Preview */}
              <div className="lg:col-span-5 space-y-4">
                <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-[0_1px_3px_rgba(0,0,0,0.03)] space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-800">
                        Live Preview
                      </h2>
                      <p className="text-[11px] text-slate-400">Lockscreen banner simulator</p>
                    </div>
                    <div className="flex items-center bg-slate-100 p-0.5 rounded-lg">
                      <button
                        type="button"
                        onClick={() => setPreviewStyle('banner')}
                        className={`px-2 py-1 text-[11px] font-medium rounded-md transition ${
                          previewStyle === 'banner' ? 'bg-white shadow-xs text-slate-900' : 'text-slate-500'
                        }`}
                      >
                        Banner
                      </button>
                      <button
                        type="button"
                        onClick={() => setPreviewStyle('lockscreen')}
                        className={`px-2 py-1 text-[11px] font-medium rounded-md transition ${
                          previewStyle === 'lockscreen' ? 'bg-white shadow-xs text-slate-900' : 'text-slate-500'
                        }`}
                      >
                        Lockscreen
                      </button>
                    </div>
                  </div>

                  {/* Phone Case Simulator */}
                  <div className="relative mx-auto w-full max-w-[300px] rounded-[32px] bg-slate-900 p-3 shadow-xl border-4 border-slate-800">
                    <div className="rounded-[24px] overflow-hidden bg-slate-950 text-white min-h-[420px] p-4 flex flex-col justify-between relative">
                      {/* Top Bar */}
                      <div className="flex items-center justify-between text-[11px] text-slate-400 font-medium px-2">
                        <span>09:41</span>
                        <span>5G</span>
                      </div>

                      {previewStyle === 'lockscreen' && (
                        <div className="text-center my-6 space-y-1">
                          <p className="text-4xl font-light">09:41</p>
                          <p className="text-[11px] text-slate-400">Wednesday, November 11</p>
                        </div>
                      )}

                      {/* Notification Card */}
                      <div className="rounded-2xl bg-white/95 backdrop-blur-md text-slate-900 p-3.5 shadow-lg border border-white/40 space-y-2">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center space-x-2">
                            <div className="w-5 h-5 rounded-md bg-slate-900 flex items-center justify-center text-white font-bold text-[10px]">
                              S
                            </div>
                            <span className="text-[11px] font-semibold text-slate-900">Commerce OS</span>
                            <span className="text-[10px] text-slate-400">• Just now</span>
                          </div>
                          <span className="text-[9px] font-medium px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                            {category}
                          </span>
                        </div>

                        <div>
                          <p className="text-xs font-bold text-slate-900 line-clamp-1">
                            {title.trim() || '⚡ Your Notification Title Here'}
                          </p>
                          <p className="text-[11px] text-slate-600 mt-0.5 line-clamp-3 leading-snug">
                            {body.trim() || 'Enter title and body to see it live here.'}
                          </p>
                        </div>

                        {imageUrl && (
                          <div className="rounded-xl overflow-hidden h-28 bg-slate-100 border border-slate-200">
                            <img
                              src={imageUrl}
                              alt="Notification Banner"
                              className="w-full h-full object-cover"
                              onError={(e) => {
                                (e.target as HTMLElement).style.display = 'none';
                              }}
                            />
                          </div>
                        )}

                        <div className="pt-1 flex items-center justify-between text-[10px] text-slate-600 border-t border-slate-100">
                          <span>Tap to open</span>
                          <span className="text-slate-400 font-mono">{deepLink.replace('commerceos://', '/')}</span>
                        </div>
                      </div>

                      <div className="w-20 h-1 bg-white/30 rounded-full mx-auto" />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Broadcast History & Logs with Image Thumbnail Column */}
            <div className="bg-white rounded-2xl border border-slate-200/80 shadow-[0_1px_3px_rgba(0,0,0,0.03)] overflow-hidden">
              <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-bold text-slate-900">Broadcast History &amp; Delivery Log</h2>
                  <p className="text-xs text-slate-500">Record of pushes delivered across customer and fleet network</p>
                </div>
                <span className="text-xs font-medium text-slate-600 bg-slate-100 px-3 py-1 rounded-full">
                  {broadcasts.length} broadcasts
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 text-slate-500 text-[11px] font-semibold uppercase tracking-wider border-b border-slate-100">
                    <tr>
                      <th className="py-3 px-4">Banner</th>
                      <th className="py-3 px-6">Notification Content</th>
                      <th className="py-3 px-6">Audience</th>
                      <th className="py-3 px-6">Category</th>
                      <th className="py-3 px-6">Status</th>
                      <th className="py-3 px-6">Dispatched At</th>
                      <th className="py-3 px-6 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {broadcasts.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="py-8 text-center text-slate-400">
                          No broadcasts sent yet. Use the composer above to push your first notification!
                        </td>
                      </tr>
                    ) : (
                      broadcasts.map((b) => (
                        <tr key={b.id} className="hover:bg-slate-50/70 transition">
                          {/* Image Thumbnail Column */}
                          <td className="py-3 px-4">
                            {b.imageUrl ? (
                              <div
                                onClick={() => setPreviewImageModal(b.imageUrl || null)}
                                className="w-12 h-12 rounded-xl overflow-hidden bg-slate-100 border border-slate-200 cursor-pointer hover:opacity-80 transition relative group"
                                title="Click to view full image"
                              >
                                <img
                                  src={b.imageUrl}
                                  alt="Banner"
                                  className="w-full h-full object-cover"
                                  onError={(e) => {
                                    (e.target as HTMLElement).style.display = 'none';
                                  }}
                                />
                                <div className="absolute inset-0 bg-black/30 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                                  <Eye className="w-3.5 h-3.5 text-white" />
                                </div>
                              </div>
                            ) : (
                              <div className="w-12 h-12 rounded-xl bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-400">
                                <ImageIcon className="w-4 h-4" />
                              </div>
                            )}
                          </td>

                          <td className="py-3.5 px-6 max-w-sm">
                            <p className="font-semibold text-slate-900 truncate">{b.title}</p>
                            <p className="text-[11px] text-slate-500 truncate mt-0.5">{b.body}</p>
                          </td>
                          <td className="py-3.5 px-6">
                            <span
                              className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium border ${
                                b.targetAudience === 'ALL'
                                  ? 'bg-slate-100 text-slate-700 border-slate-200'
                                  : b.targetAudience === 'CUSTOMERS'
                                  ? 'bg-blue-50 text-blue-700 border-blue-200/60'
                                  : 'bg-amber-50 text-amber-700 border-amber-200/60'
                              }`}
                            >
                              {b.targetAudience === 'ALL'
                                ? 'Everyone'
                                : b.targetAudience === 'CUSTOMERS'
                                ? 'Customers'
                                : 'Fleet Riders'}
                            </span>
                          </td>
                          <td className="py-3.5 px-6">
                            <span className="text-[10px] font-medium text-slate-600 bg-slate-100 px-2 py-0.5 rounded">
                              {b.category}
                            </span>
                          </td>
                          <td className="py-3.5 px-6">
                            <span className="inline-flex items-center space-x-1 text-[10px] font-medium text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200/60">
                              <CheckCircle2 className="w-3 h-3" />
                              <span>{b.fcmStatus || 'DELIVERED'}</span>
                            </span>
                          </td>
                          <td className="py-3.5 px-6 text-slate-500 font-mono text-[10px]">
                            {new Date(b.createdAt).toLocaleString()}
                          </td>
                          <td className="py-3.5 px-6 text-right">
                            <button
                              type="button"
                              onClick={() => {
                                setTitle(b.title);
                                setBody(b.body);
                                setTargetAudience(b.targetAudience);
                                setCategory(b.category);
                                setImageUrl(b.imageUrl || '');
                                setDeepLink(b.deepLink || 'commerceos://home');
                                window.scrollTo({ top: 0, behavior: 'smooth' });
                                showToast('Broadcast loaded into composer!', 'success');
                              }}
                              className="px-2.5 py-1 text-[11px] font-medium rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition"
                            >
                              Clone
                            </button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </main>
        </div>

        {/* Image Preview Modal */}
        {previewImageModal && (
          <div
            onClick={() => setPreviewImageModal(null)}
            className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 cursor-pointer"
          >
            <div
              onClick={(e) => e.stopPropagation()}
              className="bg-white rounded-2xl shadow-2xl p-4 max-w-lg w-full space-y-3 border border-slate-200"
            >
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <span className="text-xs font-semibold text-slate-700">Notification Banner Image</span>
                <button
                  onClick={() => setPreviewImageModal(null)}
                  className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              <div className="rounded-xl overflow-hidden max-h-80 bg-slate-100">
                <img src={previewImageModal} alt="Full Banner" className="w-full h-full object-contain" />
              </div>
              <p className="text-[11px] font-mono text-slate-400 truncate">{previewImageModal}</p>
            </div>
          </div>
        )}
      </div>
    </SellerAuthGuard>
  );
}
