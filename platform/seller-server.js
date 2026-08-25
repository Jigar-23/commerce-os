const http = require('http');
const https = require('https');
const url = require('url');
const fs = require('fs');
const path = require('path');

const PORT = 3003;
const dbPath = path.join(__dirname, 'db.json');

const BACKEND_URL = process.env.BACKEND_URL || 'https://commerce-os-api.onrender.com';

function fetchFromGateway(gatewayPath) {
  return new Promise((resolve) => {
    const isHttps = BACKEND_URL.startsWith('https');
    const client = isHttps ? https : http;
    client.get(`${BACKEND_URL}${gatewayPath}`, { timeout: 4000 }, (res) => {
      let raw = '';
      res.on('data', chunk => raw += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(raw));
        } catch (e) {
          resolve(null);
        }
      });
    }).on('error', () => resolve(null));
  });
}

function postToGateway(gatewayPath, data = {}, method = 'POST') {
  return new Promise((resolve) => {
    const payload = JSON.stringify(data);
    const isHttps = BACKEND_URL.startsWith('https');
    const client = isHttps ? https : http;
    const req = client.request(`${BACKEND_URL}${gatewayPath}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload)
      },
      timeout: 4000
    }, (res) => {
      let raw = '';
      res.on('data', chunk => raw += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(raw));
        } catch (e) {
          resolve({ ok: res.statusCode >= 200 && res.statusCode < 300, raw });
        }
      });
    });
    req.on('error', (err) => resolve({ ok: false, error: err.message }));
    req.write(payload);
    req.end();
  });
}

function getDbData() {
  try {
    return JSON.parse(fs.readFileSync(dbPath, 'utf8'));
  } catch (e) {
    return { orders: [], products: [], auditLogs: [] };
  }
}

function saveDbData(data) {
  try {
    fs.writeFileSync(dbPath, JSON.stringify(data, null, 2), 'utf8');
  } catch (e) {
    console.error('Error saving db.json:', e);
  }
}

function parseJsonBody(req) {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        resolve(JSON.parse(body || '{}'));
      } catch (e) {
        resolve({});
      }
    });
  });
}

const HTML_CONTENT = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Commerce OS — Pharmacy Partner Merchant Portal</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background-color: #0B132B;
      color: #F1F5F9;
      height: 100vh;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    a, button { cursor: pointer; user-select: none; }
    
    /* App Bar */
    .header {
      height: 64px;
      background: rgba(15, 23, 42, 0.95);
      border-bottom: 1px solid #1E293B;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 24px;
      flex-shrink: 0;
      z-index: 40;
    }
    .brand-logo {
      width: 40px;
      height: 40px;
      border-radius: 12px;
      background: linear-gradient(135deg, #10B981, #059669);
      color: white;
      font-size: 20px;
      font-weight: 900;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
    }
    .brand-title { font-size: 18px; font-weight: 900; color: #FFFFFF; }
    .brand-title span { color: #34D399; }
    .badge-hub {
      font-size: 10px;
      font-weight: 800;
      background: rgba(16, 185, 129, 0.15);
      color: #34D399;
      padding: 2px 8px;
      border-radius: 6px;
      border: 1px solid rgba(16, 185, 129, 0.3);
      margin-left: 8px;
    }
    
    /* Layout */
    .app-body { display: flex; flex: 1; overflow: hidden; }
    
    /* Sidebar */
    .sidebar {
      width: 260px;
      background: rgba(15, 23, 42, 0.75);
      border-right: 1px solid #1E293B;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 16px;
      flex-shrink: 0;
      z-index: 30;
    }
    .nav-btn {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 14px;
      margin-bottom: 6px;
      border-radius: 12px;
      font-size: 13px;
      font-weight: 600;
      color: #94A3B8;
      background: transparent;
      border: 1px solid transparent;
      transition: all 0.15s ease;
      text-align: left;
    }
    .nav-btn:hover { background: #1E293B; color: #FFFFFF; }
    .nav-btn.active {
      background: rgba(16, 185, 129, 0.15);
      color: #34D399;
      border-color: rgba(16, 185, 129, 0.3);
      font-weight: 700;
    }
    .nav-icon { width: 18px; height: 18px; margin-right: 10px; display: inline-flex; align-items: center; justify-content: center; }
    .nav-badge {
      font-size: 11px;
      font-weight: 800;
      padding: 2px 8px;
      border-radius: 6px;
      background: #1E293B;
      color: #CBD5E1;
    }
    .nav-badge.warn { background: rgba(245, 158, 11, 0.2); color: #FBBF24; border: 1px solid rgba(245, 158, 11, 0.3); }

    /* Main Area */
    .main-content {
      flex: 1;
      overflow-y: auto;
      padding: 24px 32px;
      background: #0B132B;
    }
    .tab-pane { display: none; }
    .tab-pane.active { display: block; }
    
    /* Cards & Grids */
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }
    .card {
      background: #0F172A;
      border: 1px solid #1E293B;
      border-radius: 16px;
      padding: 20px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.2);
    }
    .card-title { font-size: 12px; font-weight: 700; color: #94A3B8; text-transform: uppercase; letter-spacing: 0.5px; }
    .card-val { font-size: 28px; font-weight: 900; color: #FFFFFF; margin-top: 8px; }
    .card-sub { font-size: 11px; color: #34D399; margin-top: 4px; font-weight: 600; }
    
    /* Tables */
    .table-container {
      background: #0F172A;
      border: 1px solid #1E293B;
      border-radius: 16px;
      overflow: hidden;
      margin-bottom: 24px;
    }
    .table-header {
      padding: 16px 20px;
      border-bottom: 1px solid #1E293B;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    table { width: 100%; border-collapse: collapse; font-size: 13px; text-align: left; }
    th { padding: 12px 16px; color: #64748B; font-weight: 700; border-bottom: 1px solid #1E293B; font-size: 11px; text-transform: uppercase; }
    td { padding: 14px 16px; border-bottom: 1px solid rgba(30, 41, 59, 0.6); color: #E2E8F0; }
    tr:hover td { background: rgba(30, 41, 59, 0.4); }
    
    /* Buttons & Inputs */
    .btn {
      padding: 8px 14px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 700;
      border: none;
      transition: all 0.15s ease;
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .btn-emerald { background: #059669; color: white; }
    .btn-emerald:hover { background: #10B981; }
    .btn-indigo { background: #4F46E5; color: white; }
    .btn-indigo:hover { background: #6366F1; }
    .btn-purple { background: #9333EA; color: white; }
    .btn-purple:hover { background: #A855F7; }
    .btn-slate { background: #1E293B; color: #CBD5E1; border: 1px solid #334155; }
    .btn-slate:hover { background: #334155; color: white; }
    .btn-rose { background: rgba(225, 29, 72, 0.2); color: #FB7185; border: 1px solid rgba(225, 29, 72, 0.3); }
    .btn-rose:hover { background: #E11D48; color: white; }
    .btn-amber { background: #D97706; color: white; }
    .btn-amber:hover { background: #F59E0B; }
    
    .input-box {
      background: #1E293B;
      border: 1px solid #334155;
      color: white;
      padding: 8px 12px;
      border-radius: 10px;
      font-size: 12px;
      outline: none;
    }
    .input-box:focus { border-color: #10B981; }
    
    /* Badges */
    .status-badge {
      font-size: 10px;
      font-weight: 800;
      padding: 3px 8px;
      border-radius: 6px;
      text-transform: uppercase;
      display: inline-block;
    }
    .st-placed { background: rgba(245, 158, 11, 0.15); color: #FBBF24; border: 1px solid rgba(245, 158, 11, 0.3); }
    .st-accepted { background: rgba(16, 185, 129, 0.15); color: #34D399; border: 1px solid rgba(16, 185, 129, 0.3); }
    .st-packed { background: rgba(168, 85, 247, 0.15); color: #C084FC; border: 1px solid rgba(168, 85, 247, 0.3); }
    .st-transit { background: rgba(59, 130, 246, 0.15); color: #60A5FA; border: 1px solid rgba(59, 130, 246, 0.3); }
    .st-delivered { background: rgba(16, 185, 129, 0.2); color: #10B981; border: 1px solid #10B981; }
    .st-cancelled { background: rgba(239, 68, 68, 0.15); color: #F87171; border: 1px solid rgba(239, 68, 68, 0.3); }

    /* Modal */
    #item-modal {
      position: fixed;
      top: 0; left: 0; width: 100vw; height: 100vh;
      background: rgba(3, 7, 18, 0.8);
      backdrop-filter: blur(4px);
      z-index: 999;
      display: none;
      align-items: center;
      justify-content: center;
    }
    .modal-card {
      background: #0F172A;
      border: 1px solid #334155;
      border-radius: 20px;
      width: 100%;
      max-width: 480px;
      padding: 24px;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
    }
    
    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .grid-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
    .form-group { margin-bottom: 12px; display: flex; flex-direction: column; gap: 4px; font-size: 11px; font-weight: 700; color: #94A3B8; }

    /* Toast Notification */
    #toast {
      position: fixed;
      bottom: 24px;
      right: 24px;
      background: #059669;
      color: white;
      padding: 12px 20px;
      border-radius: 12px;
      font-size: 13px;
      font-weight: 700;
      box-shadow: 0 10px 25px rgba(0,0,0,0.4);
      display: none;
      z-index: 1000;
      animation: fadeIn 0.2s ease-out;
    }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
  </style>
</head>
<body>

  <!-- APP HEADER -->
  <header class="header">
    <div style="display: flex; align-items: center; gap: 12px;">
      <div class="brand-logo">S</div>
      <div>
        <div style="display: flex; align-items: center;">
          <span class="brand-title">Commerce<span>OS</span></span>
          <span class="badge-hub">MERCHANT HUB</span>
        </div>
        <p style="font-size: 11px; color: #64748B;">Rewari Central Fulfillment Store (STORE_REWARI_01)</p>
      </div>
    </div>

    <div style="display: flex; align-items: center; gap: 16px;">
      <input id="quick-search" type="text" placeholder="Search Order ID, SKU, Customer..." class="input-box" style="width: 280px;">
      <div style="display: flex; align-items: center; gap: 6px; background: #1E293B; padding: 6px 12px; border-radius: 8px; font-size: 11px; color: #34D399; font-weight: 700;">
        <span style="width: 8px; height: 8px; border-radius: 50%; background: #10B981;"></span>
        <span>Gateway Connected</span>
      </div>
      <button class="btn btn-slate" onclick="loadData(true); showToast('✓ Refreshed latest store state');" style="cursor: pointer; font-weight: 700;">🔄 Refresh</button>
    </div>
  </header>

  <!-- APP BODY -->
  <div class="app-body">
    
    <!-- LEFT SIDEBAR -->
    <aside class="sidebar">
      <div>
        <button type="button" class="nav-btn active" id="btn-dashboard" onclick="setTab('dashboard')">
          <div style="display: flex; align-items: center;">
            <span class="nav-icon">📊</span>
            <span>Dashboard Overview</span>
          </div>
        </button>

        <button type="button" class="nav-btn" id="btn-orders" onclick="setTab('orders')">
          <div style="display: flex; align-items: center;">
            <span class="nav-icon">📦</span>
            <span>Orders & Dispatch</span>
          </div>
          <span id="badge-orders" class="nav-badge">0</span>
        </button>

        <button type="button" class="nav-btn" id="btn-inventory" onclick="setTab('inventory')">
          <div style="display: flex; align-items: center;">
            <span class="nav-icon">🏬</span>
            <span>Stock & Inventory</span>
          </div>
          <span id="badge-stock" class="nav-badge">0</span>
        </button>

        <button type="button" class="nav-btn" id="btn-cod" onclick="setTab('cod')">
          <div style="display: flex; align-items: center;">
            <span class="nav-icon">💰</span>
            <span>COD Cash Ledger</span>
          </div>
          <span id="badge-cod" class="nav-badge warn">₹0</span>
        </button>

        <button type="button" class="nav-btn" id="btn-products" onclick="setTab('products')">
          <div style="display: flex; align-items: center;">
            <span class="nav-icon">💊</span>
            <span>Catalog Products</span>
          </div>
        </button>

        <button type="button" class="nav-btn" id="btn-audit" onclick="setTab('audit')">
          <div style="display: flex; align-items: center;">
            <span class="nav-icon">🛡️</span>
            <span>Audit Trail</span>
          </div>
        </button>

        <button type="button" class="nav-btn" id="btn-settings" onclick="setTab('settings')">
          <div style="display: flex; align-items: center;">
            <span class="nav-icon">⚙️</span>
            <span>Store Settings</span>
          </div>
        </button>
      </div>

      <div style="background: #1E293B; padding: 12px; border-radius: 12px; font-size: 11px;">
        <p style="font-weight: 800; color: white;">Commerce OS Partner</p>
        <p style="color: #64748B; font-family: monospace;">seller_rewari_01</p>
      </div>
    </aside>

    <!-- MAIN SCROLLABLE CONTENT -->
    <main class="main-content">

      <!-- 1. TAB: DASHBOARD -->
      <section id="pane-dashboard" class="tab-pane active">
        <div class="kpi-grid">
          <div class="card">
            <p class="card-title">Live Active Orders</p>
            <p class="card-val" id="kpi-orders">0</p>
            <p class="card-sub">⚡ 10-Minute Express Fulfillment</p>
          </div>
          <div class="card">
            <p class="card-title">Inventory Stock Qty</p>
            <p class="card-val" id="kpi-stock">0</p>
            <p class="card-sub" style="color: #60A5FA;">Across registered SKUs</p>
          </div>
          <div class="card">
            <p class="card-title">Pending COD Cash</p>
            <p class="card-val" id="kpi-cod" style="color: #FBBF24;">₹0</p>
            <p class="card-sub" style="color: #FBBF24;">Awaiting rider settlement</p>
          </div>
          <div class="card">
            <p class="card-title">Cloud Infrastructure</p>
            <p class="card-val" style="color: #34D399;">99.9%</p>
            <p class="card-sub">Active Render Gateway</p>
          </div>
        </div>

        <div class="table-container">
          <div class="table-header">
            <span style="font-weight: 800; font-size: 14px;">Recent Order Activity</span>
            <button class="btn btn-emerald" onclick="setTab('orders')">View All Orders →</button>
          </div>
          <table>
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Medicines</th>
                <th>Total</th>
                <th>Status</th>
                <th style="text-align: right;">Action</th>
              </tr>
            </thead>
            <tbody id="dash-orders-table">
              <tr><td colspan="5" style="text-align: center; color: #64748B; padding: 24px;">Loading live orders...</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 2. TAB: ORDERS -->
      <section id="pane-orders" class="tab-pane">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
          <div>
            <h2 style="font-size: 18px; font-weight: 900;">Order Management & Fulfillment</h2>
            <p style="font-size: 12px; color: #64748B;">Review incoming orders, pack medicines, and dispatch to riders.</p>
          </div>
          <button class="btn btn-emerald" onclick="loadData()">Refresh Orders</button>
        </div>

        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Customer & Address</th>
                <th>Medicines</th>
                <th>Amount</th>
                <th>Status</th>
                <th style="text-align: right;">Fulfillment Action</th>
              </tr>
            </thead>
            <tbody id="orders-table">
              <tr><td colspan="6" style="text-align: center; color: #64748B; padding: 32px;">Loading orders...</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 3. TAB: INVENTORY -->
      <section id="pane-inventory" class="tab-pane">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
          <div>
            <h2 style="font-size: 18px; font-weight: 900;">Stock Inventory & Reorder Levels</h2>
            <p style="font-size: 12px; color: #64748B;">Monitor on-hand stock and make instant quantity adjustments.</p>
          </div>
          <button class="btn btn-emerald" onclick="openModal()">+ Add New Product / SKU</button>
        </div>

        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>SKU / Product Name</th>
                <th>Brand / Manufacturer</th>
                <th>Selling Price</th>
                <th>Current Stock</th>
                <th style="text-align: right;">Quick Stock Adjustment</th>
              </tr>
            </thead>
            <tbody id="inventory-table">
              <tr><td colspan="5" style="text-align: center; color: #64748B; padding: 32px;">Loading stock...</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 4. TAB: COD LEDGER -->
      <section id="pane-cod" class="tab-pane">
        <div style="margin-bottom: 16px;">
          <h2 style="font-size: 18px; font-weight: 900;">Cash on Delivery (COD) Reconciliation</h2>
          <p style="font-size: 12px; color: #64748B;">Track cash collected at customer doorstep and reconcile settlement.</p>
        </div>

        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Customer Phone</th>
                <th>COD Amount</th>
                <th>Payment State</th>
                <th style="text-align: right;">Reconciliation Action</th>
              </tr>
            </thead>
            <tbody id="cod-table">
              <tr><td colspan="5" style="text-align: center; color: #64748B; padding: 32px;">Loading COD ledger...</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 5. TAB: PRODUCTS -->
      <section id="pane-products" class="tab-pane">
        <div style="margin-bottom: 16px;">
          <h2 style="font-size: 18px; font-weight: 900;">Master Medicine Catalog</h2>
          <p style="font-size: 12px; color: #64748B;">All registered product templates and specifications.</p>
        </div>
        <div id="products-grid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px;"></div>
      </section>

      <!-- 6. TAB: AUDIT -->
      <section id="pane-audit" class="tab-pane">
        <div style="margin-bottom: 16px;">
          <h2 style="font-size: 18px; font-weight: 900;">Immutable Compliance Audit Trail</h2>
          <p style="font-size: 12px; color: #64748B;">Logged atomic events, inventory adjustments, and status transitions.</p>
        </div>
        <div id="audit-list" style="display: flex; flex-direction: column; gap: 8px;"></div>
      </section>

      <!-- 7. TAB: SETTINGS -->
      <section id="pane-settings" class="tab-pane">
        <div style="margin-bottom: 16px;">
          <h2 style="font-size: 18px; font-weight: 900;">Merchant Store Settings &amp; Dispatch Priority</h2>
          <p style="font-size: 12px; color: #64748B;">Control store acceptance priority, licensing, and gateway connections.</p>
        </div>
        <div class="card" style="max-width: 650px; display: flex; flex-direction: column; gap: 20px;">
          
          <!-- Dispatch Mode Priority Switch -->
          <div style="background: rgba(16, 185, 129, 0.08); border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 12px; padding: 16px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <div>
                <h4 style="font-size: 14px; font-weight: 800; color: #FFFFFF;">Merchant Acceptance Priority (Manual Review)</h4>
                <p style="font-size: 11px; color: #94A3B8; margin-top: 4px;" id="priority-desc">
                  When enabled, you have the priority to accept incoming orders. Delivery riders are NOT notified until you accept.
                </p>
              </div>
              <button id="btn-toggle-priority" class="btn btn-emerald" onclick="handleTogglePriority()" style="cursor: pointer; padding: 8px 16px; font-weight: 800;">
                Toggle Mode
              </button>
            </div>
            <div id="priority-status-text" style="font-size: 12px; font-weight: 800; color: #34D399; margin-top: 10px;">
              ⚡ Mode: Auto-Dispatch (Dark Store Express)
            </div>
          </div>

          <div class="form-group">
            <label>Store Hub Name</label>
            <input type="text" class="input-box" value="Rewari Central Fulfillment Store (STORE_REWARI_01)" disabled>
          </div>
          <div class="form-group">
            <label>Drug Retail License (Form 20/21)</label>
            <input type="text" class="input-box" value="DL-HR-REW-2026-98102" disabled style="color: #34D399; font-family: monospace;">
          </div>
          <div class="form-group">
            <label>Active Authoritative Gateway</label>
            <input type="text" class="input-box" value="https://commerce-os-api.onrender.com" disabled style="font-family: monospace;">
          </div>
        </div>
      </section>

      <!-- 8. TAB: DEDICATED FULL ORDER DETAILS PAGE -->
      <section id="pane-order-detail" class="tab-pane">
        <!-- Top Navigation Header -->
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; border-bottom: 1px solid #1E293B; padding-bottom: 16px;">
          <div style="display: flex; align-items: center; gap: 14px;">
            <button class="btn btn-slate" onclick="setTab('orders')" style="padding: 8px 16px; font-weight: 800; cursor: pointer; display: inline-flex; align-items: center; gap: 8px; font-size: 13px;">
              ← Back to Orders List
            </button>
            <span style="color: #475569; font-size: 16px;">/</span>
            <span style="background: rgba(16, 185, 129, 0.15); color: #34D399; font-family: monospace; font-size: 14px; font-weight: 800; padding: 6px 12px; border-radius: 8px; border: 1px solid rgba(16, 185, 129, 0.3);" id="page-od-id">
              ord_...
            </span>
            <span id="page-od-status-badge" class="status-badge st-accepted">ACCEPTED</span>
          </div>
          <button class="btn btn-emerald" onclick="reloadCurrentOrderDetail()" style="cursor: pointer; padding: 8px 14px; font-weight: 700;">
            🔄 Refresh Order
          </button>
        </div>

        <div id="page-od-body" style="display: flex; flex-direction: column; gap: 20px; max-width: 960px;">
          <!-- Loaded dynamically via viewOrderDetails() -->
          <div style="text-align: center; color: #64748B; padding: 48px;">Loading order details...</div>
        </div>
      </section>

    </main>
  </div>

  <!-- ADD ITEM MODAL -->
  <div id="item-modal">
    <div class="modal-card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; border-bottom: 1px solid #1E293B; padding-bottom: 12px;">
        <h3 style="font-size: 16px; font-weight: 900;">Add Product to Stock</h3>
        <button class="btn btn-slate" onclick="closeModal()">✕</button>
      </div>

      <form id="item-form" onsubmit="handleSaveItem(event)">
        <div class="grid-2">
          <div class="form-group">
            <label>Item Name *</label>
            <input type="text" id="f-name" class="input-box" required placeholder="e.g. Paracip 500mg">
          </div>
          <div class="form-group">
            <label>SKU Code *</label>
            <input type="text" id="f-sku" class="input-box" required placeholder="SKU-XXX">
          </div>
        </div>

        <div class="grid-2">
          <div class="form-group">
            <label>Category</label>
            <select id="f-cat" class="input-box">
              <option>Health & Pharmacy</option>
              <option>Grocery & Needs</option>
              <option>Personal Care</option>
            </select>
          </div>
          <div class="form-group">
            <label>Brand Name</label>
            <input type="text" id="f-brand" class="input-box" placeholder="e.g. Cipla">
          </div>
        </div>

        <div class="grid-3">
          <div class="form-group">
            <label>Price (₹) *</label>
            <input type="number" id="f-price" class="input-box" required value="20">
          </div>
          <div class="form-group">
            <label>MRP (₹) *</label>
            <input type="number" id="f-mrp" class="input-box" required value="25">
          </div>
          <div class="form-group">
            <label>Stock Qty *</label>
            <input type="number" id="f-stock" class="input-box" required value="50">
          </div>
        </div>

        <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px;">
          <button type="button" class="btn btn-slate" onclick="closeModal()">Cancel</button>
          <button type="submit" class="btn btn-emerald">Save to Stock</button>
        </div>
      </form>
    </div>
  </div>

  <!-- TOAST MESSAGE -->
  <div id="toast">Order state updated</div>

  <script src="/seller-client.js"></script>
</body>
</html>`;

const server = http.createServer(async (req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PATCH, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (pathname === '/seller-client.js') {
    try {
      const clientJs = fs.readFileSync(path.join(__dirname, 'seller-client.js'), 'utf8');
      res.writeHead(200, {
        'Content-Type': 'application/javascript; charset=utf-8',
        'Cache-Control': 'no-cache, no-store, must-revalidate'
      });
      res.end(clientJs);
    } catch (e) {
      res.writeHead(404);
      res.end('Not found');
    }
    return;
  }

  if (pathname === '/health' || pathname === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'healthy', app: 'CommerceOS Seller Portal' }));
    return;
  }

  // GET /api/v1/seller/store/settings
  if (pathname === '/api/v1/seller/store/settings' && req.method === 'GET') {
    const db = getDbData();
    let store = null;
    if (Array.isArray(db.stores)) {
      store = db.stores.find(s => s.id === 'STORE_REWARI_01' || s.storeId === 'STORE_REWARI_01');
    } else if (db.stores) {
      store = db.stores['STORE_REWARI_01'];
    }
    if (!store) {
      store = {
        storeId: 'STORE_REWARI_01',
        name: 'Rewari Central Fulfillment Store',
        sellerApprovalRequired: false
      };
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: true, store }));
    return;
  }

  // POST /api/v1/seller/store/settings
  if (pathname === '/api/v1/seller/store/settings' && (req.method === 'POST' || req.method === 'PATCH')) {
    const body = await parseJsonBody(req);
    const db = getDbData();
    let store = null;
    if (Array.isArray(db.stores)) {
      store = db.stores.find(s => s.id === 'STORE_REWARI_01' || s.storeId === 'STORE_REWARI_01');
      if (!store) {
        store = { id: 'STORE_REWARI_01', storeId: 'STORE_REWARI_01', name: 'Rewari Central Fulfillment Store' };
        db.stores.push(store);
      }
    } else {
      db.stores = db.stores || {};
      store = db.stores['STORE_REWARI_01'] = db.stores['STORE_REWARI_01'] || {
        storeId: 'STORE_REWARI_01',
        name: 'Rewari Central Fulfillment Store',
      };
    }
    if (body.sellerApprovalRequired !== undefined) {
      store.sellerApprovalRequired = Boolean(body.sellerApprovalRequired);
    }
    saveDbData(db);
    try {
      await Promise.allSettled([
        postToGateway('/api/v1/seller/store/settings', body, 'PATCH'),
        postToGateway('/api/v1/seller/store/settings', body, 'POST')
      ]);
    } catch (_) {}
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: true, store }));
    return;
  }

  // GET /api/v1/orders/seller
  if ((pathname === '/api/v1/orders/seller' || pathname === '/api/v1/orders') && req.method === 'GET') {
    const cloudOrders = await fetchFromGateway('/api/v1/orders/seller');
    if (Array.isArray(cloudOrders) && cloudOrders.length > 0) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(cloudOrders));
      return;
    }
    const db = getDbData();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(db.orders || []));
    return;
  }

  // GET /api/v1/orders/:id (Single order detail with deliverySession & rider history)
  const singleOrderMatch = pathname.match(/^\/api\/v1\/orders\/([^/]+)$/);
  if (singleOrderMatch && req.method === 'GET') {
    const id = decodeURIComponent(singleOrderMatch[1]);
    const cloudOrder = await fetchFromGateway(`/api/v1/orders/${encodeURIComponent(id)}`);
    if (cloudOrder && (cloudOrder.id || cloudOrder.orderId)) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(cloudOrder));
      return;
    }
    const db = getDbData();
    const order = (db.orders || []).find(o => o.id === id || o.orderId === id);
    if (order) {
      db.deliverySessions = db.deliverySessions || {};
      const session = db.deliverySessions[order.id] || db.deliverySessions['del_' + order.id] || Object.values(db.deliverySessions).find(s => s.orderId === order.id);
      const enriched = {
        ...order,
        deliverySession: session || null,
        rider: session?.riderId ? {
          riderId: session.riderId,
          name: session.riderName || 'Assigned Delivery Partner',
          phone: session.riderPhone || '+91 98765 43210',
          vehicle: session.riderVehicle || 'Electric Scooter'
        } : null,
        riderHistory: session?.history || []
      };
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(enriched));
      return;
    }
  }

  // GET /api/v1/catalog/products
  if (pathname === '/api/v1/catalog/products' && req.method === 'GET') {
    const db = getDbData();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ content: db.products || [], totalElements: (db.products || []).length }));
    return;
  }

  // GET /api/v1/orders/audit
  if (pathname === '/api/v1/orders/audit' && req.method === 'GET') {
    const db = getDbData();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ logs: db.auditLogs || [], total: (db.auditLogs || []).length }));
    return;
  }

  // POST /api/v1/seller/inventory/add
  if ((pathname === '/api/v1/seller/inventory/add' || pathname === '/api/v1/catalog/products') && req.method === 'POST') {
    const body = await parseJsonBody(req);
    const db = getDbData();
    const product = {
      id: body.id || 'prod_' + Math.floor(10000 + Math.random() * 90000),
      sku: body.sku || 'SKU-' + Math.floor(1000 + Math.random() * 9000),
      name: body.name || 'Untitled Item',
      brandName: body.brandName || 'CommerceOS Partner',
      manufacturer: body.manufacturer || 'Cipla',
      packSize: body.packSize || '1 unit',
      price: Number(body.price || 10),
      mrp: Number(body.mrp || 12),
      discountedPrice: Number(body.price || 10),
      inStock: true,
      stockCount: Number(body.stockCount || 50),
      therapeuticCategory: body.category || 'General',
      sellerId: 'seller_rewari_01'
    };
    db.products = db.products || [];
    db.products.unshift(product);
    saveDbData(db);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(product));
    return;
  }

  // POST /api/v1/orders/:id/accept-by-seller or /accept
  const acceptMatch = pathname.match(/^\/api\/v1\/orders\/([^/]+)\/(?:accept-by-seller|accept)$/);
  if (acceptMatch && req.method === 'POST') {
    const orderId = decodeURIComponent(acceptMatch[1]);
    try {
      await Promise.allSettled([
        postToGateway(`/api/v1/orders/${encodeURIComponent(orderId)}/accept`, {}, 'POST'),
        postToGateway(`/api/v1/orders/${encodeURIComponent(orderId)}/accept-by-seller`, {}, 'POST')
      ]);
    } catch (_) {}
    const db = getDbData();
    const order = (db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (order) {
      order.status = 'SELLER_ACCEPTED';
      order.orderStatus = 'SELLER_ACCEPTED';
      saveDbData(db);
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, orderId, status: 'SELLER_ACCEPTED' }));
    return;
  }

  // POST /api/v1/orders/:id/pack
  const packMatch = pathname.match(/^\/api\/v1\/orders\/([^/]+)\/pack$/);
  if (packMatch && req.method === 'POST') {
    const orderId = decodeURIComponent(packMatch[1]);
    try {
      await postToGateway(`/api/v1/orders/${encodeURIComponent(orderId)}/pack`, {}, 'POST');
    } catch (_) {}
    const db = getDbData();
    const order = (db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (order) {
      order.status = 'PACKED';
      order.orderStatus = 'PACKED';
      saveDbData(db);
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, orderId, status: 'PACKED' }));
    return;
  }

  // POST /api/v1/orders/:id/ready-for-pickup
  const readyMatch = pathname.match(/^\/api\/v1\/orders\/([^/]+)\/ready-for-pickup$/);
  if (readyMatch && req.method === 'POST') {
    const orderId = decodeURIComponent(readyMatch[1]);
    const db = getDbData();
    const order = (db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (order) {
      order.status = 'OUT_FOR_DELIVERY';
      order.orderStatus = 'OUT_FOR_DELIVERY';
      saveDbData(db);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, orderId, status: 'OUT_FOR_DELIVERY' }));
    } else {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'ORDER_NOT_FOUND' }));
    }
    return;
  }

  // POST /api/v1/orders/:id/collect-cod
  const codCollectMatch = pathname.match(/^\/api\/v1\/orders\/([^/]+)\/collect-cod$/);
  if (codCollectMatch && req.method === 'POST') {
    const orderId = decodeURIComponent(codCollectMatch[1]);
    const db = getDbData();
    const order = (db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (order) {
      order.paymentStatus = 'COLLECTED';
      order.codReconciled = true;
      saveDbData(db);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true, orderId, paymentStatus: 'COLLECTED' }));
    } else {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'ORDER_NOT_FOUND' }));
    }
    return;
  }

  // PATCH /api/v1/orders/:id/status
  const orderStatusMatch = pathname.match(/^\/api\/v1\/orders\/([^/]+)\/status$/);
  if (orderStatusMatch && (req.method === 'PATCH' || req.method === 'POST')) {
    const orderId = decodeURIComponent(orderStatusMatch[1]);
    const body = await parseJsonBody(req);
    const db = getDbData();
    const order = (db.orders || []).find(o => o.id === orderId || o.orderId === orderId);
    if (order) {
      const newStatus = body.status || body.targetStatus || order.status;
      order.status = newStatus;
      order.orderStatus = newStatus;
      saveDbData(db);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(order));
    } else {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'ORDER_NOT_FOUND' }));
    }
    return;
  }

  // PATCH /api/v1/catalog/products/:id/stock
  const stockMatch = pathname.match(/^\/api\/v1\/catalog\/products\/([^/]+)\/stock$/);
  if (stockMatch && req.method === 'PATCH') {
    const sku = decodeURIComponent(stockMatch[1]);
    const body = await parseJsonBody(req);
    const db = getDbData();
    const p = (db.products || []).find(prod => prod.sku === sku || prod.id === sku);
    if (p) {
      if (body.delta !== undefined) {
        p.stockCount = Math.max(0, (Number(p.stockCount) || 0) + Number(body.delta));
      } else if (body.stockCount !== undefined) {
        p.stockCount = Math.max(0, Number(body.stockCount));
      }
      p.inStock = p.stockCount > 0;
      saveDbData(db);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(p));
    } else {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'PRODUCT_NOT_FOUND' }));
    }
    return;
  }

  // HTML Web Portal
  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0',
    'Pragma': 'no-cache',
    'Expires': '0'
  });
  res.end(HTML_CONTENT);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 CommerceOS Clean Zero-CDN Seller Portal listening on http://localhost:${PORT} and http://0.0.0.0:${PORT}`);
});
