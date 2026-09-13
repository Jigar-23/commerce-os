let state = {
  orders: [],
  products: [],
  riders: [],
  audit: [],
  storeSettings: { sellerApprovalRequired: false }
};
let currentTab = 'dashboard';
let searchQuery = '';
let currentViewOrderId = null;

// Cache snapshots to prevent unnecessary DOM re-renders/flickers
let lastOrdersSnapshot = '';
let lastProductsSnapshot = '';
let lastRidersSnapshot = '';
let lastOrderDetailsSnapshot = {};

function showToast(msg) {
  const t = document.getElementById('toast');
  if (!t) return;
  t.textContent = msg;
  t.style.display = 'block';
  setTimeout(() => { t.style.display = 'none'; }, 3000);
}

function setTab(tabId, skipPush) {
  currentTab = tabId;
  const tabs = ['dashboard', 'orders', 'inventory', 'cod', 'riders', 'products', 'audit', 'settings', 'order-detail'];
  tabs.forEach(t => {
    const pane = document.getElementById('pane-' + t);
    const btn = document.getElementById('btn-' + t);
    if (pane) {
      if (t === tabId) pane.classList.add('active');
      else pane.classList.remove('active');
    }
    if (btn) {
      if (t === tabId || (tabId === 'order-detail' && t === 'orders')) btn.classList.add('active');
      else btn.classList.remove('active');
    }
  });

  if (!skipPush) {
    if (tabId === 'order-detail' && currentViewOrderId) {
      history.pushState({ tab: 'order-detail', orderId: currentViewOrderId }, '', '/orders/' + encodeURIComponent(currentViewOrderId));
    } else if (tabId === 'dashboard') {
      history.pushState({ tab: 'dashboard' }, '', '/');
    } else {
      history.pushState({ tab: tabId }, '', '/' + tabId);
    }
  }

  if (tabId !== 'order-detail') {
    render(true);
  }
}

function openModal() {
  document.getElementById('item-modal').style.display = 'flex';
  document.getElementById('f-sku').value = 'SKU-' + Math.floor(1000 + Math.random() * 9000);
  if (document.getElementById('f-name')) document.getElementById('f-name').value = '';
  if (document.getElementById('f-brand')) document.getElementById('f-brand').value = '';
  if (document.getElementById('f-image')) document.getElementById('f-image').value = '';
}

function closeModal() {
  document.getElementById('item-modal').style.display = 'none';
}

function openRiderModal() {
  document.getElementById('rider-modal').style.display = 'flex';
}

function closeRiderModal() {
  document.getElementById('rider-modal').style.display = 'none';
}

let currentRxModalOrderId = null;

function openRxModal(imgUrl, orderId, customerName) {
  currentRxModalOrderId = orderId;
  const modal = document.getElementById('rx-modal');
  const img = document.getElementById('rx-modal-img');
  const title = document.getElementById('rx-modal-title');
  const meta = document.getElementById('rx-modal-meta');
  if (img) img.src = imgUrl;
  if (title) title.textContent = 'Doctor Prescription • Order ' + orderId;
  if (meta) meta.innerHTML = '<strong>Order:</strong> ' + orderId + '<br><strong>Patient:</strong> ' + (customerName || 'Customer') + '<br><strong>Clinical Review:</strong> Verify dosage, doctor signature/registration number, and compliance before dispensing.';
  if (modal) modal.style.display = 'flex';
}

function closeRxModal() {
  const modal = document.getElementById('rx-modal');
  if (modal) modal.style.display = 'none';
  currentRxModalOrderId = null;
}

async function handleApproveRxFromModal() {
  if (!currentRxModalOrderId) return;
  await verifyPrescription(currentRxModalOrderId, 'APPROVED');
  closeRxModal();
}

async function handleRejectRxFromModal() {
  if (!currentRxModalOrderId) return;
  await verifyPrescription(currentRxModalOrderId, 'REJECTED');
  closeRxModal();
}

async function verifyPrescription(orderId, decision) {
  try {
    const res = await fetch('/api/v1/orders/' + encodeURIComponent(orderId) + '/verify-prescription', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision, licenseNo: 'PHARM-LIC-2026-9912' })
    });
    if (res.ok) {
      showToast(decision === 'APPROVED' ? '✓ Prescription Approved & Order Accepted!' : '✕ Prescription Rejected & Order Cancelled');
      reloadCurrentOrderDetail();
      fetchData();
    } else {
      showToast('Failed to update prescription verification');
    }
  } catch (e) {
    showToast('Network error verifying prescription');
  }
}

async function handleTogglePriority() {
  const current = Boolean(state.storeSettings.sellerApprovalRequired);
  const nextVal = !current;
  try {
    const res = await fetch('/api/v1/seller/store/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sellerApprovalRequired: nextVal })
    });
    if (res.ok) {
      state.storeSettings.sellerApprovalRequired = nextVal;
      updatePriorityUi();
      showToast(nextVal ? '✓ Merchant Acceptance Priority Enabled!' : '⚡ Auto-Dispatch Dark Store Enabled!');
    }
  } catch (e) {
    showToast('Failed to update priority setting');
  }
}

function updatePriorityUi() {
  const isReq = Boolean(state.storeSettings.sellerApprovalRequired);
  const btn = document.getElementById('btn-toggle-priority');
  const text = document.getElementById('priority-status-text');
  const desc = document.getElementById('priority-desc');
  if (btn) {
    btn.textContent = isReq ? 'Turn OFF Priority' : 'Enable Priority';
    btn.className = isReq ? 'btn btn-purple' : 'btn btn-emerald';
  }
  if (text) {
    text.innerHTML = isReq
      ? '🔒 Mode: <strong>Merchant Priority Review (Riders Held Until You Accept)</strong>'
      : '⚡ Mode: <strong>Auto-Dispatch (Dark Store Express)</strong>';
    text.style.color = isReq ? '#A78BFA' : '#34D399';
  }
  if (desc) {
    desc.textContent = isReq
      ? 'Orders stay in PENDING state. Riders will NOT receive any job notification until you tap Accept.'
      : 'Orders are auto-accepted and broadcast to nearby riders immediately upon placement.';
  }
}

async function viewOrderDetails(orderId, skipPush, isSilentBackgroundSync) {
  currentViewOrderId = orderId;
  setTab('order-detail', skipPush);

  const pageId = document.getElementById('page-od-id');
  const pageBadge = document.getElementById('page-od-status-badge');
  const pageBody = document.getElementById('page-od-body');

  if (pageId) pageId.textContent = orderId;
  
  // Only show "Loading..." placeholder if not a silent background sync and not already rendered
  if (!isSilentBackgroundSync && pageBody && pageBody.getAttribute('data-loaded-order') !== orderId) {
    pageBody.innerHTML = '<div style="text-align: center; color: #64748B; padding: 48px;">Loading order ' + orderId + '...</div>';
  }

  try {
    const res = await fetch('/api/v1/orders/' + encodeURIComponent(orderId));
    if (!res.ok) {
      if (pageBody) pageBody.innerHTML = '<div style="text-align: center; color: #FB7185; padding: 48px;">Order not found (' + orderId + ')</div>';
      return;
    }
    const order = await res.json();
    const snapshotStr = JSON.stringify(order);

    // If data hasn't changed during background sync, skip DOM mutation to prevent any visual jitter
    if (isSilentBackgroundSync && lastOrderDetailsSnapshot[orderId] === snapshotStr) {
      return;
    }
    lastOrderDetailsSnapshot[orderId] = snapshotStr;

    if (pageBadge) pageBadge.innerHTML = renderStatus(order.orderStatus || order.status);

    const session = order.deliverySession || {};
    const rider = order.rider || (session.riderId ? {
      name: session.riderName || 'Partner',
      phone: session.riderPhone || '+91 98765 43210',
      vehicle: session.riderVehicle || 'Electric Vehicle',
      riderId: session.riderId
    } : null);

    const history = session.history || order.riderHistory || [];
    const checkpoints = order.trackingCheckpoints || [];

    const placedTime = order.createdAt ? new Date(order.createdAt).toLocaleTimeString() : 'Recorded';
    const acceptedTime = order.sellerApprovedAt ? new Date(order.sellerApprovedAt).toLocaleTimeString() : (order.status !== 'PLACED' ? 'Accepted' : 'Pending Action');
    
    const riderAssigned = history.find(h => h.state === 'ASSIGNED') || checkpoints.find(c => c.status === 'RIDER_ASSIGNED');
    const riderAssignedTime = riderAssigned ? new Date(riderAssigned.timestamp || riderAssigned.createdAt).toLocaleTimeString() : (rider ? 'Assigned' : 'Waiting Acceptance');

    const storeArrived = history.find(h => h.state === 'ARRIVED_PICKUP') || checkpoints.find(c => c.status === 'ARRIVED_PICKUP');
    const storeArrivedTime = storeArrived ? new Date(storeArrived.timestamp || storeArrived.createdAt).toLocaleTimeString() : 'En route to store';

    const dispatched = history.find(h => h.state === 'EN_ROUTE_CUSTOMER') || checkpoints.find(c => c.status === 'OUT_FOR_DELIVERY');
    const dispatchedTime = dispatched ? new Date(dispatched.timestamp || dispatched.createdAt).toLocaleTimeString() : 'Pending handoff';

    const custArrived = history.find(h => h.state === 'HANDOFF_STARTED' || h.state === 'ARRIVED_CUSTOMER') || checkpoints.find(c => c.status === 'ARRIVED_CUSTOMER');
    const custArrivedTime = custArrived ? new Date(custArrived.timestamp || custArrived.createdAt).toLocaleTimeString() : 'En route to customer';

    const delivered = history.find(h => h.state === 'DELIVERED') || checkpoints.find(c => c.status === 'DELIVERED') || (order.orderStatus === 'DELIVERED');
    const deliveredTime = delivered ? (delivered.timestamp ? new Date(delivered.timestamp).toLocaleTimeString() : 'Delivered') : 'Pending';

    const itemsHtml = (order.items || []).map(i => {
      return '<div style="display: flex; justify-content: space-between; align-items: center; background: #0F172A; padding: 12px 16px; border-radius: 12px; margin-bottom: 8px; border: 1px solid #1E293B;">' +
        '<div>' +
          '<div style="font-weight: 800; font-size: 14px; color: #FFFFFF;">' + (i.name || 'Medicine') + '</div>' +
          '<div style="font-size: 12px; color: #94A3B8; font-family: monospace; margin-top: 2px;">SKU: ' + (i.sku || 'SKU-001') + ' • ' + (i.packSize || 'Standard Pack') + '</div>' +
        '</div>' +
        '<div style="text-align: right;">' +
          '<div style="font-weight: 900; color: #34D399; font-size: 16px;">₹' + (i.total || (i.unitPrice * i.quantity) || i.price || 0) + '</div>' +
          '<div style="font-size: 12px; color: #64748B;">' + (i.quantity || 1) + ' × ₹' + (i.unitPrice || i.price || 0) + '</div>' +
        '</div>' +
      '</div>';
    }).join('');

    const addr = typeof order.deliveryAddress === 'object' && order.deliveryAddress ? (order.deliveryAddress.addressLine || order.deliveryAddress.formattedAddress || 'Rewari') : (order.deliveryAddress || 'Customer Location');

    let actButtons = '';
    const isRxPending = (order.orderStatus === 'PRESCRIPTION_VERIFICATION_PENDING' || order.status === 'PRESCRIPTION_VERIFICATION_PENDING' || (order.rxVerificationRequired && order.pharmacistVerification?.status !== 'APPROVED'));

    if (isRxPending) {
      actButtons += '<button class="btn btn-emerald" style="padding: 12px 20px; font-size: 14px; cursor: pointer;" onclick="verifyPrescription(\'' + order.id + '\', \'APPROVED\'); reloadCurrentOrderDetail();">✓ Verify Rx &amp; Accept Order</button>' +
        '<button class="btn btn-slate" style="color: #FB7185; padding: 12px 20px; font-size: 14px; cursor: pointer;" onclick="verifyPrescription(\'' + order.id + '\', \'REJECTED\'); reloadCurrentOrderDetail();">✕ Reject Prescription</button>';
    } else if (order.orderStatus === 'PLACED' || order.sellerApprovalStatus === 'PENDING') {
      actButtons += '<button class="btn btn-emerald" style="padding: 12px 20px; font-size: 14px; cursor: pointer;" onclick="updateStatus(\'' + order.id + '\', \'SELLER_ACCEPTED\'); reloadCurrentOrderDetail();">✓ Accept Order &amp; Broadcast to Riders</button>' +
        '<button class="btn btn-slate" style="color: #FB7185; padding: 12px 20px; font-size: 14px; cursor: pointer;" onclick="updateStatus(\'' + order.id + '\', \'CANCELLED\'); reloadCurrentOrderDetail();">Reject Order</button>';
    }
    if (order.orderStatus === 'SELLER_ACCEPTED') {
      actButtons += '<button class="btn btn-purple" style="padding: 12px 20px; font-size: 14px; cursor: pointer;" onclick="updateStatus(\'' + order.id + '\', \'PACKED\'); reloadCurrentOrderDetail();">📦 Mark Order Packed</button>';
    }
    if (order.orderStatus === 'PACKED') {
      actButtons += '<button class="btn btn-indigo" style="padding: 12px 20px; font-size: 14px; cursor: pointer;" onclick="updateStatus(\'' + order.id + '\', \'OUT_FOR_DELIVERY\'); reloadCurrentOrderDetail();">🚴 Dispatch Package to Rider</button>';
    }
    if (order.paymentMethod === 'COD' && order.paymentStatus !== 'COLLECTED') {
      actButtons += '<button class="btn btn-emerald" style="padding: 12px 20px; font-size: 14px; cursor: pointer;" onclick="reconcileCod(\'' + order.id + '\'); reloadCurrentOrderDetail();">💵 Collect &amp; Reconcile COD Cash</button>';
    }

    let riderSection = '<p style="font-size: 12px; color: #64748B; margin-top: 4px;">Broadcasting job offer to nearest active riders...</p>';
    if (rider) {
      riderSection = '<p style="font-size: 12px; color: #34D399; margin-top: 4px; font-weight: 700;">🚴 Rider: ' + rider.name + ' • 📞 ' + rider.phone + ' • Vehicle: ' + rider.vehicle + '</p>';
    }

    // Doctor Prescription Compliance Section
    let rxSection = '';
    const hasRx = order.rxVerificationRequired || order.prescriptionUrl || order.prescriptionImage || order.prescriptionId || (order.items || []).some(i => i.rxRequired) || order.status === 'PRESCRIPTION_VERIFICATION_PENDING' || order.orderStatus === 'PRESCRIPTION_VERIFICATION_PENDING';
    if (hasRx) {
      const rxImg = order.prescriptionUrl || order.prescriptionImage || (order.attachments && order.attachments[0]) || 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=600&auto=format&fit=crop&q=80';
      const rxStatus = order.pharmacistVerification?.status || (order.orderStatus === 'PRESCRIPTION_VERIFICATION_PENDING' ? 'PENDING' : 'APPROVED');
      const isApproved = rxStatus === 'APPROVED' || ['SELLER_ACCEPTED', 'PACKED', 'OUT_FOR_DELIVERY', 'DELIVERED'].includes(order.orderStatus || order.status);

      rxSection = 
        '<div style="background: #0F172A; border: 1.5px solid ' + (isApproved ? 'rgba(16, 185, 129, 0.5)' : 'rgba(239, 68, 68, 0.6)') + '; border-radius: 18px; padding: 22px;">' +
          '<div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 8px;">' +
            '<div style="display: flex; align-items: center; gap: 10px;">' +
              '<span style="background: ' + (isApproved ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)') + '; color: ' + (isApproved ? '#34D399' : '#F87171') + '; border: 1px solid ' + (isApproved ? 'rgba(16, 185, 129, 0.4)' : 'rgba(239, 68, 68, 0.4)') + '; padding: 3px 10px; border-radius: 8px; font-size: 11px; font-weight: 900;">Rx Medical Compliance</span>' +
              '<h4 style="font-size: 15px; font-weight: 900; color: #FFFFFF; margin: 0;">Doctor\'s Prescription Verification</h4>' +
            '</div>' +
            '<span style="font-size: 12px; font-weight: 800; color: ' + (isApproved ? '#34D399' : '#FBBF24') + ';">' + (isApproved ? '✓ VERIFIED &amp; APPROVED' : '⏳ AWAITING PHARMACIST REVIEW') + '</span>' +
          '</div>' +
          '<div style="display: flex; gap: 20px; flex-wrap: wrap; align-items: center;">' +
            '<div style="position: relative; cursor: pointer; border-radius: 14px; overflow: hidden; border: 2px solid #334155; width: 130px; height: 130px; flex-shrink: 0; background: #1E293B;" onclick="openRxModal(\'' + rxImg + '\', \'' + order.id + '\', \'' + (order.customerName || 'Customer') + '\')">' +
              '<img src="' + rxImg + '" style="width: 100%; height: 100%; object-fit: cover;" alt="Prescription" />' +
              '<div style="position: absolute; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 800; color: #FFF; text-align: center; padding: 4px;">🔍 Tap to Enlarge</div>' +
            '</div>' +
            '<div style="flex: 1; min-width: 240px; font-size: 13px; display: flex; flex-direction: column; gap: 6px;">' +
              '<p style="color: #CBD5E1; margin: 0;"><strong>Prescription ID:</strong> ' + (order.prescriptionId || ('RX-' + order.id.slice(-8).toUpperCase())) + '</p>' +
              '<p style="color: #94A3B8; margin: 0;">Patient: <strong style="color: #FFFFFF;">' + (order.customerName || 'Customer') + '</strong> (' + (order.customerPhone || '—') + ')</p>' +
              '<p style="color: #94A3B8; margin: 0;">Classification: <strong style="color: #F87171;">Schedule H / Prescription Required</strong></p>' +
              '<div style="display: flex; gap: 10px; margin-top: 6px; flex-wrap: wrap;">' +
                '<button class="btn btn-slate" style="font-size: 12px; padding: 8px 14px; cursor: pointer;" onclick="openRxModal(\'' + rxImg + '\', \'' + order.id + '\', \'' + (order.customerName || 'Customer') + '\')">👁️ View Full Prescription</button>' +
                (!isApproved ? '<button class="btn btn-emerald" style="font-size: 12px; padding: 8px 14px; cursor: pointer;" onclick="verifyPrescription(\'' + order.id + '\', \'APPROVED\')">✓ Approve Rx</button>' : '') +
              '</div>' +
            '</div>' +
          '</div>' +
        '</div>';
    }

    pageBody.setAttribute('data-loaded-order', orderId);
    pageBody.innerHTML = 
      '<div style="background: #0F172A; border: 1px solid #1E293B; border-radius: 18px; padding: 22px;">' +
        '<h4 style="font-size: 14px; font-weight: 900; color: #34D399; text-transform: uppercase; margin-bottom: 18px; letter-spacing: 0.5px;">' +
          '⏱️ Real-Time Order Lifecycle &amp; Delivery Partner Milestones' +
        '</h4>' +
        '<div style="display: flex; flex-direction: column; gap: 16px; position: relative; padding-left: 24px; border-left: 2px solid #334155;">' +
          
          '<div style="position: relative;">' +
            '<span style="position: absolute; left: -31px; top: 0; width: 14px; height: 14px; border-radius: 50%; background: #34D399; display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 900; color: #000;">✓</span>' +
            '<div style="display: flex; justify-content: space-between; align-items: baseline;">' +
              '<strong style="font-size: 13px; color: #FFFFFF;">1. Order Placed by Customer</strong>' +
              '<span style="font-size: 12px; font-family: monospace; color: #94A3B8;">' + placedTime + '</span>' +
            '</div>' +
            '<p style="font-size: 12px; color: #64748B; margin-top: 4px;">Customer: ' + (order.customerName || order.customerId || 'Customer') + ' • Payment: ' + order.paymentMethod + ' (₹' + order.totalAmount + ')</p>' +
          '</div>' +

          '<div style="position: relative;">' +
            '<span style="position: absolute; left: -31px; top: 0; width: 14px; height: 14px; border-radius: 50%; background: ' + (order.sellerApprovalStatus === 'PENDING' ? '#F59E0B' : '#34D399') + '; display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 900; color: #000;">' + (order.sellerApprovalStatus === 'PENDING' ? '!' : '✓') + '</span>' +
            '<div style="display: flex; justify-content: space-between; align-items: baseline;">' +
              '<strong style="font-size: 13px; color: #FFFFFF;">2. Merchant Acceptance</strong>' +
              '<span style="font-size: 12px; font-family: monospace; color: #94A3B8;">' + acceptedTime + '</span>' +
            '</div>' +
            '<p style="font-size: 12px; color: #64748B; margin-top: 4px;">' +
              (order.sellerApprovalStatus === 'PENDING' ? '⏳ Awaiting your acceptance. Rider dispatch is held.' : '✓ Store accepted order for fulfillment.') +
            '</p>' +
          '</div>' +

          '<div style="position: relative;">' +
            '<span style="position: absolute; left: -31px; top: 0; width: 14px; height: 14px; border-radius: 50%; background: ' + (rider ? '#34D399' : '#475569') + '; display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 900; color: #000;">' + (rider ? '✓' : '3') + '</span>' +
            '<div style="display: flex; justify-content: space-between; align-items: baseline;">' +
              '<strong style="font-size: 13px; color: #FFFFFF;">3. Delivery Partner Assigned</strong>' +
              '<span style="font-size: 12px; font-family: monospace; color: #94A3B8;">' + riderAssignedTime + '</span>' +
            '</div>' +
            riderSection +
          '</div>' +

          '<div style="position: relative;">' +
            '<span style="position: absolute; left: -31px; top: 0; width: 14px; height: 14px; border-radius: 50%; background: ' + (storeArrived ? '#34D399' : '#475569') + '; display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 900; color: #000;">' + (storeArrived ? '✓' : '4') + '</span>' +
            '<div style="display: flex; justify-content: space-between; align-items: baseline;">' +
              '<strong style="font-size: 13px; color: #FFFFFF;">4. Rider Arrived at Merchant Store</strong>' +
              '<span style="font-size: 12px; font-family: monospace; color: #94A3B8;">' + storeArrivedTime + '</span>' +
            '</div>' +
            '<p style="font-size: 12px; color: #64748B; margin-top: 4px;">Rider at store pickup point ready for package handoff.</p>' +
          '</div>' +

          '<div style="position: relative;">' +
            '<span style="position: absolute; left: -31px; top: 0; width: 14px; height: 14px; border-radius: 50%; background: ' + (dispatched ? '#34D399' : '#475569') + '; display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 900; color: #000;">' + (dispatched ? '✓' : '5') + '</span>' +
            '<div style="display: flex; justify-content: space-between; align-items: baseline;">' +
              '<strong style="font-size: 13px; color: #FFFFFF;">5. Package Picked Up &amp; Dispatched</strong>' +
              '<span style="font-size: 12px; font-family: monospace; color: #94A3B8;">' + dispatchedTime + '</span>' +
            '</div>' +
            '<p style="font-size: 12px; color: #64748B; margin-top: 4px;">Order handed over • En route to customer home.</p>' +
          '</div>' +

          '<div style="position: relative;">' +
            '<span style="position: absolute; left: -31px; top: 0; width: 14px; height: 14px; border-radius: 50%; background: ' + (custArrived ? '#34D399' : '#475569') + '; display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 900; color: #000;">' + (custArrived ? '✓' : '6') + '</span>' +
            '<div style="display: flex; justify-content: space-between; align-items: baseline;">' +
              '<strong style="font-size: 13px; color: #FFFFFF;">6. Rider Reached Customer Doorstep</strong>' +
              '<span style="font-size: 12px; font-family: monospace; color: #94A3B8;">' + custArrivedTime + '</span>' +
            '</div>' +
            '<p style="font-size: 12px; color: #64748B; margin-top: 4px;">Rider at doorstep • Verifying OTP handoff.</p>' +
          '</div>' +

          '<div style="position: relative;">' +
            '<span style="position: absolute; left: -31px; top: 0; width: 14px; height: 14px; border-radius: 50%; background: ' + (order.orderStatus === 'DELIVERED' ? '#34D399' : '#475569') + '; display: flex; align-items: center; justify-content: center; font-size: 9px; font-weight: 900; color: #000;">' + (order.orderStatus === 'DELIVERED' ? '✓' : '7') + '</span>' +
            '<div style="display: flex; justify-content: space-between; align-items: baseline;">' +
              '<strong style="font-size: 13px; color: #FFFFFF;">7. Delivered &amp; OTP Verified</strong>' +
              '<span style="font-size: 12px; font-family: monospace; color: #94A3B8;">' + deliveredTime + '</span>' +
            '</div>' +
            '<p style="font-size: 12px; color: #64748B; margin-top: 4px;">Order fulfilled successfully with secure OTP verification.</p>' +
          '</div>' +

        '</div>' +
      '</div>' +

      '<div style="background: #0F172A; border: 1px solid #1E293B; border-radius: 18px; padding: 22px;">' +
        '<h4 style="font-size: 14px; font-weight: 900; color: #34D399; text-transform: uppercase; margin-bottom: 16px;">' +
          '📍 Customer Identity &amp; Destination Coordinates' +
        '</h4>' +
        '<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; font-size: 13px;">' +
          '<div style="background: #1E293B; padding: 12px 14px; border-radius: 12px;">' +
            '<span style="color: #64748B; font-size: 11px; text-transform: uppercase; font-weight: 800;">Customer</span>' +
            '<div style="font-weight: 800; color: #FFFFFF; margin-top: 4px; font-size: 14px;">' + (order.customerName || order.customerId || 'Customer') + '</div>' +
            '<div style="color: #34D399; font-weight: 700; margin-top: 4px;">📞 ' + (order.customerPhone || '—') + '</div>' +
          '</div>' +
          '<div style="background: #1E293B; padding: 12px 14px; border-radius: 12px;">' +
            '<span style="color: #64748B; font-size: 11px; text-transform: uppercase; font-weight: 800;">Handoff Verification OTP</span>' +
            '<div style="font-size: 20px; font-weight: 900; font-family: monospace; color: #34D399; margin-top: 4px;">' +
              (order.deliveryOtp || '••••••') +
            '</div>' +
          '</div>' +
          '<div style="background: #1E293B; padding: 12px 14px; border-radius: 12px; grid-column: span 2;">' +
            '<span style="color: #64748B; font-size: 11px; text-transform: uppercase; font-weight: 800;">Delivery Address</span>' +
            '<div style="color: #E2E8F0; font-weight: 600; margin-top: 4px; font-size: 14px;">' + addr + '</div>' +
          '</div>' +
        '</div>' +
      '</div>' +

      (rxSection ? rxSection : '') +

      '<div style="background: #0F172A; border: 1px solid #1E293B; border-radius: 18px; padding: 22px;">' +
        '<h4 style="font-size: 14px; font-weight: 900; color: #34D399; text-transform: uppercase; margin-bottom: 16px;">' +
          '💰 Itemized Purchased Products &amp; Financial Breakdown' +
        '</h4>' +
        '<div style="margin-bottom: 16px;">' +
          itemsHtml +
        '</div>' +
        '<div style="background: #1E293B; border-radius: 14px; padding: 16px; font-size: 13px; display: flex; flex-direction: column; gap: 8px;">' +
          '<div style="display: flex; justify-content: space-between; color: #94A3B8;">' +
            '<span>Items Subtotal:</span>' +
            '<strong style="color: #FFFFFF;">₹' + Math.max(0, order.totalAmount - 30) + '</strong>' +
          '</div>' +
          '<div style="display: flex; justify-content: space-between; color: #94A3B8;">' +
            '<span>GST / Taxes (5%):</span>' +
            '<strong style="color: #FFFFFF;">₹' + Math.round(order.totalAmount * 0.05) + '</strong>' +
          '</div>' +
          '<div style="display: flex; justify-content: space-between; color: #94A3B8;">' +
            '<span>Express 10-Min Delivery Fee:</span>' +
            '<strong style="color: #FFFFFF;">₹25</strong>' +
          '</div>' +
          '<div style="display: flex; justify-content: space-between; color: #94A3B8;">' +
            '<span>Packaging &amp; Handling:</span>' +
            '<strong style="color: #FFFFFF;">₹5</strong>' +
          '</div>' +
          '<div style="border-top: 1px solid #334155; padding-top: 10px; margin-top: 6px; display: flex; justify-content: space-between; align-items: baseline; font-size: 16px; font-weight: 900;">' +
            '<span style="color: #FFFFFF;">Grand Total:</span>' +
            '<span style="color: #34D399; font-family: monospace; font-size: 22px;">₹' + order.totalAmount + '</span>' +
          '</div>' +
        '</div>' +
      '</div>' +

      '<div style="display: flex; gap: 12px; justify-content: flex-end; flex-wrap: wrap; margin-top: 8px;">' +
        actButtons +
      '</div>';

    const otherOrders = (state.orders || []).filter(o => (o.id || o.orderId) !== order.id).slice(0, 5);
    if (otherOrders.length > 0) {
      pageBody.innerHTML += 
        '<div style="background: #0F172A; border: 1px solid #1E293B; border-radius: 18px; padding: 20px; margin-top: 14px;">' +
          '<div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;">' +
            '<h4 style="font-size: 13px; font-weight: 800; color: #94A3B8; text-transform: uppercase;">Other Active Orders in Queue</h4>' +
            '<button class="btn btn-slate" onclick="setTab(\'orders\')" style="font-size: 12px; cursor: pointer;">View All ' + state.orders.length + ' Orders →</button>' +
          '</div>' +
          '<div style="display: flex; flex-direction: column; gap: 8px;">' +
            otherOrders.map(o => {
              const oId = o.id || o.orderId;
              return '<div style="display: flex; justify-content: space-between; align-items: center; background: #1E293B; padding: 10px 14px; border-radius: 10px; cursor: pointer;" onclick="viewOrderDetails(\'' + oId + '\')">' +
                '<span style="font-family: monospace; color: #34D399; font-weight: 800; font-size: 13px;">' + oId + '</span>' +
                '<span style="font-size: 12px; color: #E2E8F0;">' + (o.customerPhone || 'Customer') + '</span>' +
                '<span style="font-weight: 800; font-size: 13px;">₹' + (o.totalAmount || 0) + '</span>' +
                renderStatus(o.orderStatus || o.status) +
              '</div>';
            }).join('') +
          '</div>' +
        '</div>';
    }

  } catch (err) {
    if (pageBody && !isSilentBackgroundSync) {
      pageBody.innerHTML = '<div style="text-align: center; color: #FB7185; padding: 48px;">Error loading order details</div>';
    }
  }
}

function reloadCurrentOrderDetail() {
  if (currentViewOrderId) {
    viewOrderDetails(currentViewOrderId, true, true);
  }
}

async function handleSaveItem(e) {
  e.preventDefault();
  const imageUrl = (document.getElementById('f-image')?.value || '').trim();
  const item = {
    name: document.getElementById('f-name').value.trim(),
    sku: document.getElementById('f-sku').value.trim(),
    category: document.getElementById('f-cat').value,
    brandName: document.getElementById('f-brand').value.trim() || 'CommerceOS Partner',
    price: parseFloat(document.getElementById('f-price').value) || 10,
    mrp: parseFloat(document.getElementById('f-mrp').value) || 12,
    stockCount: parseInt(document.getElementById('f-stock').value) || 50,
    imageUrl: imageUrl,
    image_url: imageUrl,
    image: imageUrl
  };

  try {
    const res = await fetch('/api/v1/seller/inventory/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(item)
    });
    if (res.ok) {
      closeModal();
      showToast('Added SKU: ' + item.name);
      loadData(true);
    }
  } catch (err) {
    showToast('Failed to add SKU');
  }
}

async function updateStatus(orderId, status) {
  let endpoint = '/api/v1/orders/' + encodeURIComponent(orderId) + '/status';
  if (status === 'SELLER_ACCEPTED') endpoint = '/api/v1/orders/' + encodeURIComponent(orderId) + '/accept-by-seller';
  else if (status === 'PACKED') endpoint = '/api/v1/orders/' + encodeURIComponent(orderId) + '/pack';
  else if (status === 'OUT_FOR_DELIVERY') endpoint = '/api/v1/orders/' + encodeURIComponent(orderId) + '/ready-for-pickup';

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status })
    });
    if (res.ok) {
      showToast('Order transitioned to ' + status);
      loadData(true);
    }
  } catch (e) {
    showToast('Error updating order');
  }
}

async function adjustStock(sku, delta) {
  try {
    const res = await fetch('/api/v1/catalog/products/' + encodeURIComponent(sku) + '/stock', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ delta })
    });
    if (res.ok) {
      showToast('Stock adjusted: ' + (delta > 0 ? '+' : '') + delta);
      loadData(true);
    }
  } catch (e) {
    showToast('Error adjusting stock');
  }
}

async function reconcileCod(orderId) {
  try {
    const res = await fetch('/api/v1/orders/' + encodeURIComponent(orderId) + '/collect-cod', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ notes: 'Reconciled in portal' })
    });
    if (res.ok) {
      showToast('COD Reconciled for ' + orderId);
      loadData(true);
    }
  } catch (e) {
    showToast('Error reconciling COD');
  }
}

async function handleSaveRider(e) {
  e.preventDefault();
  const rider = {
    fullName: document.getElementById('rf-name').value.trim(),
    phone: document.getElementById('rf-phone').value.trim(),
    vehicleNumber: document.getElementById('rf-vehicle').value.trim().toUpperCase(),
    vehicleType: document.getElementById('rf-type').value,
    aadhaarNumber: document.getElementById('rf-aadhaar').value.trim()
  };

  try {
    const res = await fetch('/api/v1/seller/riders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(rider)
    });
    const data = await res.json();
    if (res.ok && (data.ok || data.rider)) {
      closeRiderModal();
      document.getElementById('rider-form').reset();
      showToast('✓ Enrolled Rider: ' + rider.fullName + ' (' + rider.phone + ')');
      loadData(true);
    } else {
      showToast(data.message || 'Failed to enroll rider');
    }
  } catch (err) {
    showToast('Failed to enroll rider');
  }
}

async function handleToggleRiderStatus(riderId, currentStatus) {
  const nextStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  try {
    const res = await fetch('/api/v1/seller/riders/' + encodeURIComponent(riderId) + '/status', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: nextStatus })
    });
    if (res.ok) {
      showToast('Rider status updated to ' + nextStatus);
      loadData(true);
    }
  } catch (e) {
    showToast('Failed to update rider status');
  }
}

async function loadData(forceRender) {
  try {
    const [ordRes, prodRes, auditRes, setRes, riderRes] = await Promise.all([
      fetch('/api/v1/orders/seller'),
      fetch('/api/v1/catalog/products'),
      fetch('/api/v1/orders/audit'),
      fetch('/api/v1/seller/store/settings'),
      fetch('/api/v1/seller/riders')
    ]);

    let dataChanged = Boolean(forceRender);

    if (ordRes.ok) {
      const orders = await ordRes.json();
      const newOrdersStr = JSON.stringify(orders);
      if (newOrdersStr !== lastOrdersSnapshot) {
        lastOrdersSnapshot = newOrdersStr;
        state.orders = orders;
        dataChanged = true;
      }
    }

    if (prodRes.ok) {
      const d = await prodRes.json();
      const products = d.content || d || [];
      const newProdStr = JSON.stringify(products);
      if (newProdStr !== lastProductsSnapshot) {
        lastProductsSnapshot = newProdStr;
        state.products = products;
        dataChanged = true;
      }
    }

    if (riderRes.ok) {
      const rd = await riderRes.json();
      const riders = rd.riders || rd || [];
      const newRidersStr = JSON.stringify(riders);
      if (newRidersStr !== lastRidersSnapshot) {
        lastRidersSnapshot = newRidersStr;
        state.riders = riders;
        dataChanged = true;
      }
    }

    if (auditRes.ok) {
      const a = await auditRes.json();
      state.audit = a.logs || a || [];
    }

    if (setRes.ok) {
      const s = await setRes.json();
      state.storeSettings = s.store || s || state.storeSettings;
      updatePriorityUi();
    }

    if (dataChanged) {
      render();
    }

    if (currentTab === 'order-detail' && currentViewOrderId) {
      reloadCurrentOrderDetail();
    }
  } catch (e) {
    console.warn('Data sync notice:', e);
  }
}

function renderStatus(st) {
  const s = String(st || 'PLACED').toUpperCase();
  if (s.includes('PRESCRIPTION') || s.includes('RX_VERIF') || s.includes('RX_PENDING')) {
    return '<span class="status-badge" style="background: rgba(239, 68, 68, 0.15); color: #F87171; border: 1px solid rgba(239, 68, 68, 0.3); font-weight: 800;">RX REVIEW</span>';
  }
  if (s.includes('DELIVER')) return '<span class="status-badge st-delivered">DELIVERED</span>';
  if (s.includes('TRANSIT') || s.includes('OUT_FOR')) return '<span class="status-badge st-transit">IN TRANSIT</span>';
  if (s.includes('PACK')) return '<span class="status-badge st-packed">PACKED</span>';
  if (s.includes('ACCEPT')) return '<span class="status-badge st-accepted">ACCEPTED</span>';
  if (s.includes('CANCEL')) return '<span class="status-badge st-cancelled">CANCELLED</span>';
  return '<span class="status-badge st-placed">PLACED</span>';
}

function render(force) {
  const q = searchQuery.toLowerCase().trim();
  const filteredOrders = q
    ? state.orders.filter(o => ((o.id || '') + ' ' + (o.customerPhone || '')).toLowerCase().includes(q))
    : state.orders;
  const filteredProducts = q
    ? state.products.filter(p => ((p.name || '') + ' ' + (p.sku || '')).toLowerCase().includes(q))
    : state.products;

  const activeOrders = state.orders.filter(o => !['DELIVERED', 'CANCELLED'].includes(o.orderStatus || o.status));
  const totalStock = state.products.reduce((acc, p) => acc + (Number(p.stockCount) || 0), 0);
  const pendingCod = state.orders
    .filter(o => (o.paymentMethod === 'COD' || o.payment_method === 'COD') && o.paymentStatus !== 'COLLECTED')
    .reduce((acc, o) => acc + (Number(o.totalAmount) || 0), 0);

  const kpiOrd = document.getElementById('kpi-orders');
  const badgeOrd = document.getElementById('badge-orders');
  const kpiStk = document.getElementById('kpi-stock');
  const badgeStk = document.getElementById('badge-stock');
  const kpiCodEl = document.getElementById('kpi-cod');
  const badgeCodEl = document.getElementById('badge-cod');

  if (kpiOrd) kpiOrd.textContent = activeOrders.length;
  if (badgeOrd) badgeOrd.textContent = activeOrders.length;
  if (kpiStk) kpiStk.textContent = totalStock;
  if (badgeStk) badgeStk.textContent = state.products.length;
  if (kpiCodEl) kpiCodEl.textContent = '₹' + pendingCod;
  if (badgeCodEl) badgeCodEl.textContent = '₹' + pendingCod;

  // 1. Dashboard Orders Table
  const dTable = document.getElementById('dash-orders-table');
  if (dTable) {
    if (filteredOrders.length === 0) {
      dTable.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #64748B; padding: 24px;">No active orders</td></tr>';
    } else {
      dTable.innerHTML = filteredOrders.slice(0, 5).map(o => {
        const items = (o.items || []).map(i => i.name || 'Medicine').join(', ') || 'Prescription Medicines';
        const id = o.id || o.orderId;
        return '<tr style="cursor: pointer;" onclick="viewOrderDetails(\'' + id + '\')">' +
          '<td style="font-family: monospace; color: #34D399; font-weight: 800; text-decoration: underline;">' + id + '</td>' +
          '<td style="max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">' + items + '</td>' +
          '<td style="font-weight: 800;">₹' + (o.totalAmount || 0) + '</td>' +
          '<td>' + renderStatus(o.orderStatus || o.status) + '</td>' +
          '<td style="text-align: right;"><button class="btn btn-slate" onclick="event.stopPropagation(); viewOrderDetails(\'' + id + '\')">View Details</button></td>' +
        '</tr>';
      }).join('');
    }
  }

  // 2. Full Orders Table
  const oTable = document.getElementById('orders-table');
  if (oTable) {
    if (filteredOrders.length === 0) {
      oTable.innerHTML = '<tr><td colspan="6" style="text-align: center; color: #64748B; padding: 32px;">No orders found.</td></tr>';
    } else {
      oTable.innerHTML = filteredOrders.map(o => {
        const id = o.id || o.orderId;
        const items = (o.items || []).map(i => '<span style="background: #1E293B; padding: 2px 6px; border-radius: 4px; font-size: 11px; margin-right: 4px;">' + (i.name || 'Item') + ' x' + (i.quantity || 1) + '</span>').join('');
        const addr = typeof o.deliveryAddress === 'object' && o.deliveryAddress ? (o.deliveryAddress.addressLine || o.deliveryAddress.city || 'Address') : (o.deliveryAddress || 'Delivery Address');
        const st = String(o.orderStatus || o.status || 'PLACED').toUpperCase();
        const hasRx = o.rxVerificationRequired || o.rxRequired || (o.items || []).some(i => i.rxRequired) || st.includes('PRESCRIPTION');
        const rxBadge = hasRx ? '<span style="background: rgba(239, 68, 68, 0.2); color: #F87171; border: 1px solid rgba(239, 68, 68, 0.4); padding: 2px 6px; border-radius: 4px; font-size: 10px; font-weight: 800; margin-left: 6px;">Rx</span>' : '';

        let act = '<button class="btn btn-slate" style="margin-right: 6px; cursor: pointer;" onclick="event.stopPropagation(); viewOrderDetails(\'' + id + '\')">Details</button>';
        if (st.includes('PRESCRIPTION') || st.includes('RX')) act += '<button class="btn btn-emerald" style="cursor: pointer;" onclick="event.stopPropagation(); viewOrderDetails(\'' + id + '\')">Verify Rx</button>';
        else if (st === 'PLACED' || st === 'PENDING') act += '<button class="btn btn-emerald" style="cursor: pointer;" onclick="event.stopPropagation(); updateStatus(\'' + id + '\', \'SELLER_ACCEPTED\')">Accept</button>';
        else if (st === 'SELLER_ACCEPTED') act += '<button class="btn btn-purple" style="cursor: pointer;" onclick="event.stopPropagation(); updateStatus(\'' + id + '\', \'PACKED\')">Pack</button>';
        else if (st === 'PACKED') act += '<button class="btn btn-indigo" style="cursor: pointer;" onclick="event.stopPropagation(); updateStatus(\'' + id + '\', \'OUT_FOR_DELIVERY\')">Dispatch</button>';
        else act += '<span style="color: #64748B; font-size: 12px;">' + st + '</span>';

        return '<tr style="cursor: pointer;" onclick="viewOrderDetails(\'' + id + '\')">' +
          '<td style="font-family: monospace; color: #34D399; font-weight: 800; text-decoration: underline;">' + id + rxBadge + '</td>' +
          '<td><div>' + (o.customerPhone || '+919988776655') + '</div><div style="font-size: 11px; color: #64748B;">' + addr + '</div></td>' +
          '<td>' + items + '</td>' +
          '<td style="font-weight: 800;">₹' + (o.totalAmount || 0) + '</td>' +
          '<td>' + renderStatus(st) + '</td>' +
          '<td style="text-align: right;">' + act + '</td>' +
        '</tr>';
      }).join('');
    }
  }

  // 3. Inventory Table
  const iTable = document.getElementById('inventory-table');
  if (iTable) {
    iTable.innerHTML = filteredProducts.map(p => {
      const img = p.imageUrl || p.image_url || p.image || '';
      const imgThumb = img ? '<img src="' + img + '" style="width: 36px; height: 36px; object-fit: cover; border-radius: 6px; border: 1px solid #334155; margin-right: 12px; flex-shrink: 0;" onerror="this.style.display=\'none\'">' : '<div style="width: 36px; height: 36px; border-radius: 6px; background: #1E293B; border: 1px solid #334155; display: flex; align-items: center; justify-content: center; margin-right: 12px; font-size: 16px; flex-shrink: 0;">💊</div>';
      return '<tr>' +
        '<td><div style="display: flex; align-items: center;">' + imgThumb + '<div><div style="font-weight: 800;">' + p.name + '</div><div style="font-family: monospace; font-size: 11px; color: #64748B;">' + p.sku + '</div></div></div></td>' +
        '<td style="color: #94A3B8;">' + (p.brandName || p.brand_name || p.manufacturer || 'Cipla') + '</td>' +
        '<td style="font-weight: 800;">₹' + (p.discountedPrice || p.discounted_price || p.price || 0) + '</td>' +
        '<td style="font-weight: 800; color: #34D399;">' + (p.stockCount || p.stock_count || 0) + ' units</td>' +
        '<td style="text-align: right; display: flex; justify-content: flex-end; gap: 6px;">' +
          '<button class="btn btn-slate" style="color: #34D399; cursor: pointer;" onclick="adjustStock(\'' + p.sku + '\', 10)">+10</button>' +
          '<button class="btn btn-slate" style="color: #FB7185; cursor: pointer;" onclick="adjustStock(\'' + p.sku + '\', -5)">-5</button>' +
        '</td>' +
      '</tr>';
    }).join('');
  }

  // 4. COD Table
  const cTable = document.getElementById('cod-table');
  if (cTable) {
    const codOrders = state.orders.filter(o => o.paymentMethod === 'COD' || o.payment_method === 'COD');
    if (codOrders.length === 0) {
      cTable.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #64748B; padding: 32px;">No COD orders</td></tr>';
    } else {
      cTable.innerHTML = codOrders.map(o => {
        const isCol = o.paymentStatus === 'COLLECTED';
        const id = o.id || o.orderId;
        return '<tr style="cursor: pointer;" onclick="viewOrderDetails(\'' + id + '\')">' +
          '<td style="font-family: monospace; color: #34D399; font-weight: 800; text-decoration: underline;">' + id + '</td>' +
          '<td>' + (o.customerPhone || '+919988776655') + '</td>' +
          '<td style="font-weight: 800;">₹' + (o.totalAmount || 0) + '</td>' +
          '<td>' + (isCol ? '<span class="status-badge st-delivered">RECONCILED</span>' : '<span class="status-badge st-placed">PENDING CASH</span>') + '</td>' +
          '<td style="text-align: right;">' + (isCol ? '<span style="color: #64748B; font-size: 11px;">Settled</span>' : '<button class="btn btn-emerald" style="cursor: pointer;" onclick="event.stopPropagation(); reconcileCod(\'' + id + '\')">Reconcile Cash</button>') + '</td>' +
        '</tr>';
      }).join('');
    }
  }

  // 5. Riders Table
  const rTable = document.getElementById('riders-table');
  const bRiders = document.getElementById('badge-riders');
  if (bRiders) bRiders.textContent = (state.riders || []).filter(r => r.status === 'ACTIVE').length;
  if (rTable) {
    const ridersList = (state.riders || []);
    if (ridersList.length === 0) {
      rTable.innerHTML = '<tr><td colspan="6" style="text-align: center; color: #64748B; padding: 32px;">No delivery partners enrolled. Click "+ Enroll New Rider" to add.</td></tr>';
    } else {
      rTable.innerHTML = ridersList.map(r => {
        const isActive = r.status === 'ACTIVE';
        const id = r.id || r.rider_id;
        return '<tr>' +
          '<td><div style="font-weight: 800; color: #FFFFFF;">' + (r.full_name || 'Delivery Partner') + '</div><div style="font-size: 11px; color: #64748B;">ID: ' + id + '</div></td>' +
          '<td style="font-weight: 700; color: #34D399;">📞 ' + r.phone + '</td>' +
          '<td><span style="background: #1E293B; border: 1px solid #334155; padding: 3px 8px; border-radius: 6px; font-family: monospace; font-weight: 800;">' + (r.vehicle_number || '—') + '</span></td>' +
          '<td><span style="font-family: monospace; color: #94A3B8; font-size: 11px;">' + (r.aadhaar_number ? ('XXXX-XXXX-' + r.aadhaar_number.slice(-4)) : 'Verified ✓') + '</span></td>' +
          '<td>' + (isActive ? '<span class="status-badge st-accepted">ACTIVE</span>' : '<span class="status-badge st-cancelled">INACTIVE</span>') + '</td>' +
          '<td style="text-align: right;">' +
            '<button class="btn ' + (isActive ? 'btn-slate' : 'btn-emerald') + '" style="padding: 4px 10px; font-size: 11px; cursor: pointer;" onclick="handleToggleRiderStatus(\'' + id + '\', \'' + r.status + '\')">' +
              (isActive ? 'Set Inactive' : 'Activate') +
            '</button>' +
          '</td>' +
        '</tr>';
      }).join('');
    }
  }

  // 6. Products Grid
  const pGrid = document.getElementById('products-grid');
  if (pGrid) {
    pGrid.innerHTML = filteredProducts.map(p => {
      return '<div class="card" style="display: flex; flex-direction: column; justify-content: space-between;">' +
        '<div>' +
          '<div style="font-family: monospace; font-size: 10px; color: #34D399; font-weight: 800;">' + p.sku + '</div>' +
          '<div style="font-weight: 800; font-size: 14px; margin: 4px 0;">' + p.name + '</div>' +
          '<div style="font-size: 11px; color: #64748B;">' + (p.brandName || 'Cipla') + '</div>' +
        '</div>' +
        '<div style="display: flex; justify-content: space-between; align-items: baseline; margin-top: 12px; border-top: 1px solid #1E293B; padding-top: 8px;">' +
          '<span style="font-size: 16px; font-weight: 900;">₹' + (p.discountedPrice || p.price || 0) + '</span>' +
          '<span style="font-size: 11px; color: #34D399; font-weight: 700;">' + (p.stockCount || 0) + ' in stock</span>' +
        '</div>' +
      '</div>';
    }).join('');
  }

  // 7. Audit Trail
  const aList = document.getElementById('audit-list');
  if (aList) {
    if (state.audit.length === 0) {
      aList.innerHTML = '<p style="color: #64748B; text-align: center; padding: 24px;">No compliance audit events logged.</p>';
    } else {
      aList.innerHTML = state.audit.map(a => {
        return '<div class="card" style="padding: 12px 16px; display: flex; justify-content: space-between; align-items: center;">' +
          '<div><span style="font-weight: 800;">' + (a.action || a.eventType || 'ORDER_STATE_TRANSITION') + '</span> <span style="font-size: 11px; color: #64748B;">by ' + (a.actor || 'SELLER') + '</span></div>' +
          '<span style="font-size: 11px; color: #64748B; font-family: monospace;">' + new Date(a.createdAt || Date.now()).toLocaleTimeString() + '</span>' +
        '</div>';
      }).join('');
    }
  }
}

window.addEventListener('popstate', (event) => {
  const path = window.location.pathname;
  const orderMatch = path.match(/\/orders\/(ord_[a-zA-Z0-9-]+)/);
  if (orderMatch) {
    viewOrderDetails(orderMatch[1], true);
  } else if (path.includes('/orders')) {
    setTab('orders', true);
  } else if (path.includes('/inventory')) {
    setTab('inventory', true);
  } else if (path.includes('/cod')) {
    setTab('cod', true);
  } else if (path.includes('/riders')) {
    setTab('riders', true);
  } else if (path.includes('/products')) {
    setTab('products', true);
  } else if (path.includes('/audit')) {
    setTab('audit', true);
  } else if (path.includes('/settings')) {
    setTab('settings', true);
  } else {
    setTab('dashboard', true);
  }
});

function initApp() {
  const searchEl = document.getElementById('quick-search');
  if (searchEl) {
    searchEl.addEventListener('input', (e) => {
      searchQuery = e.target.value;
      render(true);
    });
  }

  const initPath = window.location.pathname;
  const initOrderMatch = initPath.match(/\/orders\/(ord_[a-zA-Z0-9-]+)/);
  if (initOrderMatch) {
    viewOrderDetails(initOrderMatch[1], true);
  } else if (initPath.includes('/orders')) {
    setTab('orders', true);
  } else if (initPath.includes('/inventory')) {
    setTab('inventory', true);
  } else if (initPath.includes('/cod')) {
    setTab('cod', true);
  } else if (initPath.includes('/settings')) {
    setTab('settings', true);
  }

  loadData(true);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}
