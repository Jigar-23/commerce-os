const http = require('http');
const url = require('url');

const PORT = 3003;
const API_URL = process.env.API_GATEWAY_URL || 'https://commerce-os-api.onrender.com';
const LOCAL_API_URL = 'http://127.0.0.1:8090';

const HTML_CONTENT = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Commerce OS — Pharmacy Partner Merchant Portal</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&display=swap" rel="stylesheet">
  <script>
    tailwind.config = {
      theme: {
        extend: {
          fontFamily: { sans: ['Inter', 'system-ui', 'sans-serif'] },
          colors: {
            brand: { 50: '#F0FDF4', 100: '#DCFCE7', 500: '#16A34A', 600: '#15803D', 700: '#166534' },
            navy: { 800: '#0F172A', 900: '#0B132B', 950: '#030712' },
            accent: { 500: '#4F46E5', 600: '#4338CA' }
          }
        }
      }
    }
  </script>
  <style>
    body { font-family: 'Inter', sans-serif; }
    .custom-scroll::-webkit-scrollbar { width: 6px; height: 6px; }
    .custom-scroll::-webkit-scrollbar-track { background: rgba(0,0,0,0.05); }
    .custom-scroll::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.15); border-radius: 4px; }
  </style>
</head>
<body class="bg-[#0B132B] text-slate-100 min-h-screen flex flex-col antialiased select-none">

  <!-- TOP APP BAR -->
  <header class="h-16 border-b border-slate-800/80 bg-slate-900/90 backdrop-blur px-6 flex items-center justify-between sticky top-0 z-40">
    <div class="flex items-center space-x-3">
      <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 to-emerald-400 flex items-center justify-center text-white font-black text-xl shadow-lg shadow-emerald-500/20">
        S
      </div>
      <div>
        <div class="flex items-center space-x-2">
          <span class="font-black text-lg tracking-tight text-white">Commerce<span class="text-emerald-400">OS</span></span>
          <span class="px-2 py-0.5 text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-md">MERCHANT HUB</span>
        </div>
        <p id="store-subtitle" class="text-xs text-slate-400 font-medium">Rewari Central Hub (STORE_REWARI_01)</p>
      </div>
    </div>

    <!-- SEARCH & STATUS -->
    <div class="flex items-center space-x-4">
      <div class="relative w-80 hidden md:block">
        <i class="fa-solid fa-magnifying-glass absolute left-3.5 top-3 text-xs text-slate-400"></i>
        <input id="quick-search" type="text" placeholder="Search orders, SKU, customer..." class="w-full bg-slate-800/80 border border-slate-700/80 rounded-xl pl-9 pr-4 py-2 text-xs text-white placeholder-slate-400 focus:outline-none focus:border-emerald-500 transition-colors">
      </div>

      <div class="flex items-center space-x-2 bg-slate-800/60 border border-slate-700/60 rounded-xl px-3 py-1.5 text-xs">
        <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
        <span class="text-slate-300 font-medium" id="cloud-status">Cloud API Connected</span>
      </div>

      <button onclick="logout()" class="p-2 rounded-xl bg-slate-800 hover:bg-rose-500/10 hover:text-rose-400 text-slate-400 border border-slate-700/80 transition-all text-xs flex items-center space-x-1.5">
        <i class="fa-solid fa-arrow-right-from-bracket"></i>
        <span class="hidden sm:inline">Logout</span>
      </button>
    </div>
  </header>

  <!-- MAIN BODY LAYOUT -->
  <div class="flex flex-1 overflow-hidden">

    <!-- SIDEBAR NAVIGATION -->
    <aside class="w-64 border-r border-slate-800/80 bg-slate-900/60 flex flex-col justify-between p-4 shrink-0 hidden md:flex">
      <div class="space-y-1">
        <button onclick="switchTab('dashboard')" id="nav-dashboard" class="w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold text-white bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 transition-all">
          <i class="fa-solid fa-chart-pie w-4 text-center"></i>
          <span>Dashboard Overview</span>
        </button>
        <button onclick="switchTab('orders')" id="nav-orders" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:bg-slate-800/80 hover:text-white transition-all">
          <div class="flex items-center space-x-3">
            <i class="fa-solid fa-box-open w-4 text-center"></i>
            <span>Orders & Dispatch</span>
          </div>
          <span id="badge-orders" class="px-1.5 py-0.5 rounded-md bg-slate-800 text-[10px] font-bold text-slate-300">0</span>
        </button>
        <button onclick="switchTab('inventory')" id="nav-inventory" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:bg-slate-800/80 hover:text-white transition-all">
          <div class="flex items-center space-x-3">
            <i class="fa-solid fa-warehouse w-4 text-center"></i>
            <span>Live Stock & Inventory</span>
          </div>
          <span id="badge-stock" class="px-1.5 py-0.5 rounded-md bg-slate-800 text-[10px] font-bold text-slate-300">0</span>
        </button>
        <button onclick="switchTab('cod')" id="nav-cod" class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:bg-slate-800/80 hover:text-white transition-all">
          <div class="flex items-center space-x-3">
            <i class="fa-solid fa-indian-rupee-sign w-4 text-center"></i>
            <span>COD Cash Ledger</span>
          </div>
          <span id="badge-cod" class="px-1.5 py-0.5 rounded-md bg-amber-500/10 text-amber-400 border border-amber-500/20 text-[10px] font-bold">₹0</span>
        </button>
        <button onclick="switchTab('products')" id="nav-products" class="w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:bg-slate-800/80 hover:text-white transition-all">
          <i class="fa-solid fa-pills w-4 text-center"></i>
          <span>Catalog Products</span>
        </button>
        <button onclick="switchTab('audit')" id="nav-audit" class="w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:bg-slate-800/80 hover:text-white transition-all">
          <i class="fa-solid fa-shield-halved w-4 text-center"></i>
          <span>Audit & Compliance</span>
        </button>
        <button onclick="switchTab('settings')" id="nav-settings" class="w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:bg-slate-800/80 hover:text-white transition-all">
          <i class="fa-solid fa-sliders w-4 text-center"></i>
          <span>Store Settings</span>
        </button>
      </div>

      <!-- SELLER IDENTITY CARD -->
      <div class="p-3 bg-slate-800/40 border border-slate-700/50 rounded-2xl">
        <div class="flex items-center space-x-2.5">
          <div class="w-8 h-8 rounded-full bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center font-bold text-xs">
            <i class="fa-solid fa-store"></i>
          </div>
          <div class="truncate">
            <p id="seller-name" class="text-xs font-bold text-white truncate">Commerce OS Retail</p>
            <p id="seller-id" class="text-[10px] text-slate-400 font-mono">seller_rewari_01</p>
          </div>
        </div>
      </div>
    </aside>

    <!-- CONTENT VIEW AREA -->
    <main class="flex-1 overflow-y-auto custom-scroll p-6 space-y-6">

      <!-- 1. DASHBOARD VIEW -->
      <div id="tab-dashboard" class="space-y-6">
        <!-- TOP KPI METRICS -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5 shadow-lg">
            <div class="flex items-center justify-between text-slate-400 text-xs font-semibold">
              <span>Total Orders</span>
              <i class="fa-solid fa-truck-fast text-emerald-400"></i>
            </div>
            <p id="kpi-orders" class="text-2xl font-black text-white mt-2">0</p>
            <p class="text-[10px] text-emerald-400 mt-1 font-medium"><i class="fa-solid fa-arrow-trend-up mr-1"></i>Live fulfillment active</p>
          </div>

          <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5 shadow-lg">
            <div class="flex items-center justify-between text-slate-400 text-xs font-semibold">
              <span>Active Stock Units</span>
              <i class="fa-solid fa-boxes-stacked text-blue-400"></i>
            </div>
            <p id="kpi-stock" class="text-2xl font-black text-white mt-2">0</p>
            <p class="text-[10px] text-slate-400 mt-1 font-medium">Across all medicine SKUs</p>
          </div>

          <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5 shadow-lg">
            <div class="flex items-center justify-between text-slate-400 text-xs font-semibold">
              <span>Pending COD Cash</span>
              <i class="fa-solid fa-indian-rupee-sign text-amber-400"></i>
            </div>
            <p id="kpi-cod" class="text-2xl font-black text-amber-400 mt-2">₹0</p>
            <p class="text-[10px] text-slate-400 mt-1 font-medium">To be collected & settled</p>
          </div>

          <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5 shadow-lg">
            <div class="flex items-center justify-between text-slate-400 text-xs font-semibold">
              <span>Platform Health</span>
              <i class="fa-solid fa-heart-pulse text-emerald-400"></i>
            </div>
            <p class="text-2xl font-black text-emerald-400 mt-2">99.9%</p>
            <p class="text-[10px] text-slate-400 mt-1 font-medium">Real-time sync operational</p>
          </div>
        </div>

        <!-- RECENT ORDERS & INVENTORY PREVIEW -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div class="lg:col-span-2 bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5 space-y-4">
            <div class="flex items-center justify-between">
              <h2 class="text-sm font-bold text-white flex items-center space-x-2">
                <i class="fa-solid fa-list-check text-emerald-400"></i>
                <span>Active Store Orders</span>
              </h2>
              <button onclick="switchTab('orders')" class="text-xs text-emerald-400 hover:text-emerald-300 font-semibold flex items-center space-x-1">
                <span>View All</span>
                <i class="fa-solid fa-arrow-right text-[10px]"></i>
              </button>
            </div>
            <div class="overflow-x-auto">
              <table class="w-full text-left text-xs">
                <thead>
                  <tr class="text-slate-400 border-b border-slate-800">
                    <th class="pb-2.5 font-semibold">Order ID</th>
                    <th class="pb-2.5 font-semibold">Items</th>
                    <th class="pb-2.5 font-semibold">Total</th>
                    <th class="pb-2.5 font-semibold">Status</th>
                    <th class="pb-2.5 font-semibold text-right">Action</th>
                  </tr>
                </thead>
                <tbody id="dashboard-orders-table" class="divide-y divide-slate-800/60">
                  <tr><td colspan="5" class="py-6 text-center text-slate-500">Loading live orders...</td></tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5 space-y-4">
            <div class="flex items-center justify-between">
              <h2 class="text-sm font-bold text-white flex items-center space-x-2">
                <i class="fa-solid fa-pills text-emerald-400"></i>
                <span>Inventory Quick Check</span>
              </h2>
              <button onclick="switchTab('inventory')" class="text-xs text-emerald-400 hover:text-emerald-300 font-semibold">Manage</button>
            </div>
            <div id="dashboard-inventory-list" class="space-y-3">
              <p class="text-slate-500 text-xs text-center py-6">Loading catalog...</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 2. ORDERS VIEW -->
      <div id="tab-orders" class="space-y-6 hidden">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 class="text-lg font-black text-white">Merchant Order Management</h1>
            <p class="text-xs text-slate-400">Process, pack, dispatch, and track active prescriptions and medicines</p>
          </div>
          <div class="flex items-center space-x-2">
            <button onclick="loadAllData()" class="px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-white border border-slate-700 transition-all flex items-center space-x-1.5">
              <i class="fa-solid fa-rotate-right"></i>
              <span>Refresh Orders</span>
            </button>
          </div>
        </div>

        <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs">
              <thead>
                <tr class="text-slate-400 border-b border-slate-800">
                  <th class="pb-3 font-semibold">Order ID</th>
                  <th class="pb-3 font-semibold">Customer & Delivery Details</th>
                  <th class="pb-3 font-semibold">Medicines Ordered</th>
                  <th class="pb-3 font-semibold">Amount & Mode</th>
                  <th class="pb-3 font-semibold">Fulfillment State</th>
                  <th class="pb-3 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody id="full-orders-table" class="divide-y divide-slate-800/60">
                <tr><td colspan="6" class="py-8 text-center text-slate-500">Loading orders...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 3. INVENTORY VIEW -->
      <div id="tab-inventory" class="space-y-6 hidden">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 class="text-lg font-black text-white">Store Inventory & Stock Levels</h1>
            <p class="text-xs text-slate-400">Real-time inventory levels, reorder alerts, and quick stock adjustments</p>
          </div>
        </div>

        <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs">
              <thead>
                <tr class="text-slate-400 border-b border-slate-800">
                  <th class="pb-3 font-semibold">SKU / Medicine Name</th>
                  <th class="pb-3 font-semibold">Brand / Manufacturer</th>
                  <th class="pb-3 font-semibold">Category</th>
                  <th class="pb-3 font-semibold">MRP / Price</th>
                  <th class="pb-3 font-semibold">Current Stock</th>
                  <th class="pb-3 font-semibold text-right">Quick Stock Adjustment</th>
                </tr>
              </thead>
              <tbody id="full-inventory-table" class="divide-y divide-slate-800/60">
                <tr><td colspan="6" class="py-8 text-center text-slate-500">Loading stock...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 4. COD LEDGER VIEW -->
      <div id="tab-cod" class="space-y-6 hidden">
        <div>
          <h1 class="text-lg font-black text-white">Cash on Delivery (COD) Reconciliation</h1>
          <p class="text-xs text-slate-400">Track and deposit collected cash deliveries directly into merchant settlement account</p>
        </div>

        <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5">
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs">
              <thead>
                <tr class="text-slate-400 border-b border-slate-800">
                  <th class="pb-3 font-semibold">Order ID</th>
                  <th class="pb-3 font-semibold">Customer Phone</th>
                  <th class="pb-3 font-semibold">COD Amount</th>
                  <th class="pb-3 font-semibold">Status</th>
                  <th class="pb-3 font-semibold text-right">Reconcile Action</th>
                </tr>
              </thead>
              <tbody id="cod-table" class="divide-y divide-slate-800/60">
                <tr><td colspan="5" class="py-8 text-center text-slate-500">Loading COD ledger...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 5. PRODUCTS VIEW -->
      <div id="tab-products" class="space-y-6 hidden">
        <div>
          <h1 class="text-lg font-black text-white">Medicine Catalog</h1>
          <p class="text-xs text-slate-400">All registered medicine items and specifications</p>
        </div>
        <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5">
          <div id="products-grid" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <p class="text-slate-500 text-xs py-8">Loading products...</p>
          </div>
        </div>
      </div>

      <!-- 6. AUDIT LOGS VIEW -->
      <div id="tab-audit" class="space-y-6 hidden">
        <div>
          <h1 class="text-lg font-black text-white">Audit & Compliance Log</h1>
          <p class="text-xs text-slate-400">Immutable record of all merchant state transitions and actions</p>
        </div>
        <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-5">
          <div id="audit-list" class="space-y-3 text-xs">
            <p class="text-slate-500 py-6 text-center">Loading audit events...</p>
          </div>
        </div>
      </div>

      <!-- 7. SETTINGS VIEW -->
      <div id="tab-settings" class="space-y-6 hidden">
        <div>
          <h1 class="text-lg font-black text-white">Fulfillment Hub Settings</h1>
          <p class="text-xs text-slate-400">Configuration and compliance licensing details</p>
        </div>
        <div class="bg-slate-900/80 border border-slate-800/80 rounded-2xl p-6 space-y-4 max-w-2xl">
          <div class="space-y-1.5">
            <label class="text-xs font-semibold text-slate-400">Store Hub Name</label>
            <input type="text" value="Rewari Central Hub" disabled class="w-full bg-slate-800 border border-slate-700 rounded-xl px-3.5 py-2 text-xs text-white">
          </div>
          <div class="space-y-1.5">
            <label class="text-xs font-semibold text-slate-400">Drug Retail License (Form 20/21)</label>
            <input type="text" value="DL-HR-REW-2026-98102" disabled class="w-full bg-slate-800 border border-slate-700 rounded-xl px-3.5 py-2 text-xs text-emerald-400 font-mono">
          </div>
          <div class="space-y-1.5">
            <label class="text-xs font-semibold text-slate-400">Authoritative Cloud Gateway</label>
            <input type="text" value="https://commerce-os-api.onrender.com" disabled class="w-full bg-slate-800 border border-slate-700 rounded-xl px-3.5 py-2 text-xs text-slate-300 font-mono">
          </div>
        </div>
      </div>

    </main>
  </div>

  <!-- JAVASCRIPT APP LOGIC -->
  <script>
    const API_BASE = 'https://commerce-os-api.onrender.com';
    let currentTab = 'dashboard';
    let appState = {
      orders: [],
      inventory: [],
      products: [],
      codLedger: [],
      audit: []
    };

    function switchTab(tabId) {
      currentTab = tabId;
      document.querySelectorAll('[id^="tab-"]').forEach(el => el.classList.add('hidden'));
      const active = document.getElementById('tab-' + tabId);
      if (active) active.classList.remove('hidden');

      document.querySelectorAll('[id^="nav-"]').forEach(btn => {
        btn.className = 'w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:bg-slate-800/80 hover:text-white transition-all';
      });
      const activeNav = document.getElementById('nav-' + tabId);
      if (activeNav) {
        activeNav.className = 'w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold text-white bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 transition-all';
      }
    }

    async function fetchFromApi(endpoint, options = {}) {
      try {
        const res = await fetch(API_BASE + endpoint, {
          headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
          ...options
        });
        if (!res.ok) return null;
        return await res.json();
      } catch (err) {
        console.error('Fetch error:', err);
        return null;
      }
    }

    async function loadAllData() {
      try {
        const [ordData, invData, prodData] = await Promise.all([
          fetchFromApi('/api/v1/orders/seller'),
          fetchFromApi('/api/v1/catalog/products'),
          fetchFromApi('/api/v1/catalog/products')
        ]);

        appState.orders = Array.isArray(ordData) ? ordData : (ordData?.orders || []);
        appState.products = Array.isArray(prodData?.content) ? prodData.content : (Array.isArray(prodData) ? prodData : []);
        appState.inventory = appState.products;

        renderUI();
      } catch (err) {
        console.error('Error loading data:', err);
      }
    }

    function renderUI() {
      // 1. KPI Badges
      const ordersCount = appState.orders.length;
      const totalStock = appState.products.reduce((acc, p) => acc + (Number(p.stockCount) || 0), 0);
      
      document.getElementById('kpi-orders').textContent = ordersCount;
      document.getElementById('badge-orders').textContent = ordersCount;
      document.getElementById('kpi-stock').textContent = totalStock;
      document.getElementById('badge-stock').textContent = appState.products.length;

      // 2. Dashboard Orders Table
      const dOrdersTable = document.getElementById('dashboard-orders-table');
      if (appState.orders.length === 0) {
        dOrdersTable.innerHTML = '<tr><td colspan="5" class="py-6 text-center text-slate-500">No recent orders in this fulfillment hub</td></tr>';
      } else {
        dOrdersTable.innerHTML = appState.orders.slice(0, 5).map(o => {
          const itemsText = (o.items || []).map(i => i.name || 'Medicine').join(', ') || 'Prescription Medicines';
          return '<tr class="hover:bg-slate-800/40 transition-colors">' +
            '<td class="py-3 font-mono text-emerald-400 font-bold">' + o.id + '</td>' +
            '<td class="py-3 max-w-[200px] truncate text-slate-300">' + itemsText + '</td>' +
            '<td class="py-3 font-bold text-white">₹' + (o.totalAmount || 0) + '</td>' +
            '<td class="py-3">' + renderStatusBadge(o.orderStatus || o.status) + '</td>' +
            '<td class="py-3 text-right"><button onclick="switchTab(\\'orders\\')" class="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-[10px] font-semibold text-slate-300">Inspect</button></td>' +
          '</tr>';
        }).join('');
      }

      // 3. Full Orders Table
      const fOrdersTable = document.getElementById('full-orders-table');
      if (appState.orders.length === 0) {
        fOrdersTable.innerHTML = '<tr><td colspan="6" class="py-8 text-center text-slate-500">No store orders created yet. Place an order from the mobile app to see it live here!</td></tr>';
      } else {
        fOrdersTable.innerHTML = appState.orders.map(o => {
          const itemsList = (o.items || []).map(i => '<span class="inline-block px-2 py-0.5 bg-slate-800 rounded text-[10px] text-slate-300 mr-1 mb-1">' + (i.name || 'Item') + ' x' + (i.quantity || 1) + '</span>').join('');
          const addr = typeof o.deliveryAddress === 'object' ? (o.deliveryAddress.addressLine || o.deliveryAddress.city || 'Address Saved') : (o.deliveryAddress || 'Delivery Address');
          return '<tr class="hover:bg-slate-800/40 transition-colors">' +
            '<td class="py-3.5 font-mono text-emerald-400 font-bold">' + o.id + '</td>' +
            '<td class="py-3.5 text-slate-300"><div>' + (o.customerPhone || '9991416180') + '</div><div class="text-[10px] text-slate-400 truncate max-w-[180px]">' + addr + '</div></td>' +
            '<td class="py-3.5">' + itemsList + '</td>' +
            '<td class="py-3.5 font-bold text-white"><div>₹' + (o.totalAmount || 0) + '</div><div class="text-[10px] text-slate-400 uppercase">' + (o.paymentMethod || 'COD') + '</div></td>' +
            '<td class="py-3.5">' + renderStatusBadge(o.orderStatus || o.status) + '</td>' +
            '<td class="py-3.5 text-right space-x-1.5">' +
              '<button onclick="alert(\\'Packed order ' + o.id + '\\')" class="px-2.5 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-[10px]">Pack</button>' +
              '<button onclick="alert(\\'Dispatched ' + o.id + '\\')" class="px-2.5 py-1 rounded-lg bg-accent-500 hover:bg-accent-600 text-white font-bold text-[10px]">Ship</button>' +
            '</td>' +
          '</tr>';
        }).join('');
      }

      // 4. Inventory Quick Preview & Full Table
      const dInvList = document.getElementById('dashboard-inventory-list');
      dInvList.innerHTML = appState.products.slice(0, 4).map(p => {
        return '<div class="flex items-center justify-between p-2.5 rounded-xl bg-slate-800/40 border border-slate-700/40">' +
          '<div class="truncate mr-2">' +
            '<p class="text-xs font-bold text-white truncate">' + p.name + '</p>' +
            '<p class="text-[10px] text-slate-400 font-mono">' + p.sku + '</p>' +
          '</div>' +
          '<span class="px-2 py-0.5 rounded-md bg-emerald-500/10 text-emerald-400 font-bold text-xs shrink-0">' + (p.stockCount || 0) + ' in stock</span>' +
        '</div>';
      }).join('');

      const fInvTable = document.getElementById('full-inventory-table');
      fInvTable.innerHTML = appState.products.map(p => {
        return '<tr class="hover:bg-slate-800/40 transition-colors">' +
          '<td class="py-3 font-bold text-white">' + p.name + '<div class="text-[10px] text-slate-400 font-mono">' + p.sku + '</div></td>' +
          '<td class="py-3 text-slate-300">' + (p.brandName || p.manufacturer || 'Cipla') + '</td>' +
          '<td class="py-3"><span class="px-2 py-0.5 rounded bg-slate-800 text-[10px] text-slate-300">' + (p.therapeuticCategory || 'Medicine') + '</span></td>' +
          '<td class="py-3 font-bold text-white">₹' + (p.discountedPrice || p.price || 0) + ' <span class="text-[10px] line-through text-slate-500 font-normal">₹' + (p.mrp || 0) + '</span></td>' +
          '<td class="py-3 font-bold text-emerald-400">' + (p.stockCount || 0) + ' units</td>' +
          '<td class="py-3 text-right space-x-1">' +
            '<button onclick="adjustStock(\\'' + p.sku + '\\', 10)" class="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-emerald-400 font-bold rounded text-xs">+10</button>' +
            '<button onclick="adjustStock(\\'' + p.sku + '\\', -5)" class="px-2 py-1 bg-slate-800 hover:bg-slate-700 text-rose-400 font-bold rounded text-xs">-5</button>' +
          '</td>' +
        '</tr>';
      }).join('');

      // 5. Products Grid
      const pGrid = document.getElementById('products-grid');
      pGrid.innerHTML = appState.products.map(p => {
        return '<div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-2">' +
          '<div class="flex items-center justify-between">' +
            '<span class="text-[10px] font-mono text-emerald-400 font-bold">' + p.sku + '</span>' +
            '<span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-700 text-slate-300 font-bold">' + (p.rxRequirement || 'OTC') + '</span>' +
          '</div>' +
          '<h3 class="text-xs font-bold text-white line-clamp-1">' + p.name + '</h3>' +
          '<div class="flex items-center justify-between pt-2 border-t border-slate-700/60">' +
            '<span class="text-sm font-black text-white">₹' + (p.discountedPrice || p.price || 0) + '</span>' +
            '<span class="text-xs text-slate-400 font-medium">' + (p.stockCount || 0) + ' in stock</span>' +
          '</div>' +
        '</div>';
      }).join('');
    }

    function renderStatusBadge(status) {
      const s = String(status || 'PLACED').toUpperCase();
      if (s.includes('DELIVER')) return '<span class="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px] font-bold">DELIVERED</span>';
      if (s.includes('TRANSIT') || s.includes('DISPATCH')) return '<span class="px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 text-[10px] font-bold">IN TRANSIT</span>';
      if (s.includes('PACK')) return '<span class="px-2 py-0.5 rounded bg-purple-500/10 text-purple-400 border border-purple-500/20 text-[10px] font-bold">PACKED</span>';
      if (s.includes('CANCEL')) return '<span class="px-2 py-0.5 rounded bg-rose-500/10 text-rose-400 border border-rose-500/20 text-[10px] font-bold">CANCELLED</span>';
      return '<span class="px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20 text-[10px] font-bold">PLACED</span>';
    }

    function adjustStock(sku, delta) {
      const item = appState.products.find(p => p.sku === sku);
      if (item) {
        item.stockCount = Math.max(0, (Number(item.stockCount) || 0) + delta);
        renderUI();
      }
    }

    function logout() {
      alert('Logged out from Seller Portal');
    }

    // Auto-load and poll every 5 seconds
    loadAllData();
    setInterval(loadAllData, 5000);
  </script>
</body>
</html>`;

const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  
  if (parsedUrl.pathname === '/health' || parsedUrl.pathname === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'healthy', app: 'CommerceOS Seller Portal' }));
    return;
  }

  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Cache-Control': 'no-cache'
  });
  res.end(HTML_CONTENT);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 CommerceOS High-Speed Seller Portal listening on http://localhost:${PORT} and http://0.0.0.0:${PORT}`);
});
