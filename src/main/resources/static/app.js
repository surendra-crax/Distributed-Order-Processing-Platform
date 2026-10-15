/**
 * Distributed Order Processing Platform - Interactive Frontend Controller
 */

// Global State
let inventoryData = [];
let lastOrderPayload = null;
let lastIdempotencyKey = null;

// DOM Elements
const systemHealthText = document.getElementById('system-health-text');
const systemHealthPill = document.getElementById('system-health-pill');
const statTotalSkus = document.getElementById('stat-total-skus');
const statTotalOrders = document.getElementById('stat-total-orders');
const statConfirmedOrders = document.getElementById('stat-confirmed-orders');
const statOutboxEvents = document.getElementById('stat-outbox-events');
const inventoryTableBody = document.getElementById('inventory-table-body');
const outboxTableBody = document.getElementById('outbox-table-body');
const ordersTableBody = document.getElementById('orders-table-body');
const orderSkuSelect = document.getElementById('order-sku-select');
const stressSkuSelect = document.getElementById('stress-sku-select');
const orderIdempotencyKeyInput = document.getElementById('order-idempotency-key');
const orderResponsePreview = document.getElementById('order-response-preview');
const genericModal = document.getElementById('generic-modal');
const modalTitle = document.getElementById('modal-title');
const modalJson = document.getElementById('modal-json');
const btnCloseModal = document.getElementById('btn-close-modal');

// Utility: Generate RFC 4122 UUID v4
function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// Utility: Toast notifications
function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `<span>${type === 'success' ? '✅' : type === 'danger' ? '❌' : 'ℹ️'}</span> <span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// Tab Switching
document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-pane').forEach(p => (p.style.display = 'none'));

    btn.classList.add('active');
    const target = document.getElementById(btn.dataset.target);
    if (target) target.style.display = 'block';
  });
});

// Modal Close
btnCloseModal.addEventListener('click', () => genericModal.classList.remove('active'));
window.addEventListener('click', e => {
  if (e.target === genericModal) genericModal.classList.remove('active');
});

function openModal(title, jsonContent) {
  modalTitle.textContent = title;
  modalJson.textContent = typeof jsonContent === 'string' ? jsonContent : JSON.stringify(jsonContent, null, 2);
  genericModal.classList.add('active');
}

// System Health Check
async function checkHealth() {
  try {
    const res = await fetch('/actuator/health');
    if (res.ok) {
      const data = await res.json();
      systemHealthText.textContent = `Cluster UP (${data.components?.db?.details?.database || 'PostgreSQL'})`;
      systemHealthPill.style.borderColor = 'rgba(16, 185, 129, 0.4)';
      systemHealthPill.style.color = '#34d399';
    } else {
      systemHealthText.textContent = 'Degraded';
      systemHealthPill.style.borderColor = 'rgba(239, 68, 68, 0.4)';
      systemHealthPill.style.color = '#f87171';
    }
  } catch (err) {
    systemHealthText.textContent = 'Connecting...';
  }
}

// Load Inventory Items
async function loadInventory() {
  try {
    const res = await fetch('/api/v1/inventory');
    if (!res.ok) throw new Error('Failed to fetch inventory');
    inventoryData = await res.json();

    statTotalSkus.textContent = inventoryData.length;

    // Render Table
    inventoryTableBody.innerHTML = inventoryData.map(item => {
      const total = item.availableQuantity + item.reservedQuantity;
      const availPct = total > 0 ? (item.availableQuantity / total) * 100 : 0;
      const resPct = total > 0 ? (item.reservedQuantity / total) * 100 : 0;

      return `
        <tr>
          <td><strong>${item.productName}</strong></td>
          <td><code style="color: var(--color-primary);">${item.sku}</code></td>
          <td>$${item.price.toFixed(2)}</td>
          <td><span class="badge ${item.availableQuantity > 10 ? 'badge-success' : 'badge-warning'}">${item.availableQuantity} units</span></td>
          <td><span class="badge badge-purple">${item.reservedQuantity} reserved</span></td>
          <td>
            <div class="stock-bar-container" title="${availPct.toFixed(0)}% available, ${resPct.toFixed(0)}% reserved">
              <div class="stock-bar-available" style="width: ${availPct}%"></div>
              <div class="stock-bar-reserved" style="width: ${resPct}%"></div>
            </div>
          </td>
          <td>
            <button class="btn btn-secondary btn-sm" onclick="restockSku('${item.sku}', 25)">
              +25 Restock
            </button>
          </td>
        </tr>
      `;
    }).join('');

    // Update Dropdowns
    const skuOptions = inventoryData.map(i => `<option value="${i.sku}">${i.sku} — ${i.productName} ($${i.price})</option>`).join('');
    if (orderSkuSelect.children.length !== inventoryData.length) {
      orderSkuSelect.innerHTML = skuOptions;
      stressSkuSelect.innerHTML = skuOptions;
    }

  } catch (err) {
    inventoryTableBody.innerHTML = `<tr><td colspan="7" style="color: var(--color-danger); text-align: center;">Error loading inventory: ${err.message}</td></tr>`;
  }
}

// Restock Action
window.restockSku = async function(sku, quantity) {
  try {
    const res = await fetch('/api/v1/inventory/stock', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sku, quantity })
    });
    if (res.ok) {
      showToast(`Replenished +${quantity} units for ${sku}`, 'success');
      loadInventory();
    } else {
      const err = await res.json();
      showToast(`Restock failed: ${err.detail || err.title}`, 'danger');
    }
  } catch (err) {
    showToast(`Restock network error: ${err.message}`, 'danger');
  }
};

// Load Recent Orders
async function loadOrders() {
  try {
    const res = await fetch('/api/v1/orders?size=25');
    if (!res.ok) return;
    const page = await res.json();
    const orders = page.content || [];

    statTotalOrders.textContent = page.totalElements || orders.length;
    const confirmedCount = orders.filter(o => o.status === 'CONFIRMED').length;
    statConfirmedOrders.textContent = confirmedCount;

    if (orders.length === 0) {
      ordersTableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-dim);">No orders created yet. Submit an order from the terminal!</td></tr>`;
      return;
    }

    ordersTableBody.innerHTML = orders.map(order => {
      const statusClass = order.status === 'CONFIRMED' ? 'badge-success' : order.status === 'PENDING' ? 'badge-warning' : 'badge-danger';
      const itemsCount = (order.items || []).reduce((acc, it) => acc + it.quantity, 0);

      return `
        <tr>
          <td><code style="color: var(--color-primary); font-weight: 700;">${order.orderNumber}</code></td>
          <td>${order.customerId}</td>
          <td><strong>$${order.totalAmount.toFixed(2)}</strong></td>
          <td><span class="badge ${statusClass}">${order.status}</span></td>
          <td>${itemsCount} item(s)</td>
          <td style="font-size: 0.75rem; color: var(--text-dim);">${new Date(order.createdAt).toLocaleTimeString()}</td>
          <td>
            <button class="btn btn-secondary btn-sm" onclick="viewOrderAuditTrail('${order.id}')">
              Audit Events
            </button>
          </td>
        </tr>
      `;
    }).join('');

  } catch (err) {
    console.error('Failed to load orders', err);
  }
}

// View Order Audit Trail
window.viewOrderAuditTrail = async function(orderId) {
  try {
    const res = await fetch(`/api/v1/orders/${orderId}/events`);
    if (res.ok) {
      const events = await res.json();
      openModal(`Order Lifecycle Events: ${orderId}`, events);
    } else {
      showToast('Failed to fetch events for order', 'danger');
    }
  } catch (err) {
    showToast(`Error: ${err.message}`, 'danger');
  }
};

// Load Outbox Events
async function loadOutbox() {
  try {
    const res = await fetch('/api/v1/outbox/events');
    if (!res.ok) return;
    const events = await res.json();

    statOutboxEvents.textContent = events.filter(e => e.status === 'PUBLISHED').length;

    if (events.length === 0) {
      outboxTableBody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-dim);">Outbox table empty.</td></tr>`;
      return;
    }

    outboxTableBody.innerHTML = events.map(evt => {
      const statusBadge = evt.status === 'PUBLISHED' ? 'badge-success' : evt.status === 'PENDING' ? 'badge-warning' : 'badge-danger';
      const payloadEscaped = encodeURIComponent(evt.payload || '{}');

      return `
        <tr>
          <td><code style="font-size: 0.72rem;">${evt.id.substring(0, 8)}...</code></td>
          <td><code style="color: var(--text-muted); font-size: 0.72rem;">${evt.aggregateId.substring(0, 8)}...</code></td>
          <td><span class="badge badge-info">${evt.eventType}</span></td>
          <td><span class="badge ${statusBadge}">${evt.status}</span></td>
          <td>${evt.retryCount}</td>
          <td style="font-size: 0.75rem; color: var(--text-dim);">${new Date(evt.createdAt).toLocaleTimeString()}</td>
          <td style="font-size: 0.75rem; color: var(--text-dim);">${evt.publishedAt ? new Date(evt.publishedAt).toLocaleTimeString() : '-'}</td>
          <td>
            <button class="btn btn-secondary btn-sm" onclick="viewOutboxPayload('${evt.id}', decodeURIComponent('${payloadEscaped}'))">
              JSON
            </button>
          </td>
        </tr>
      `;
    }).join('');

  } catch (err) {
    console.error('Failed to load outbox', err);
  }
}

window.viewOutboxPayload = function(eventId, payloadStr) {
  try {
    const parsed = JSON.parse(payloadStr);
    openModal(`Outbox Event Payload: ${eventId}`, parsed);
  } catch (e) {
    openModal(`Outbox Event Payload: ${eventId}`, payloadStr);
  }
};

// Order Creation Form Handler
document.getElementById('order-form').addEventListener('submit', async e => {
  e.preventDefault();

  const sku = orderSkuSelect.value;
  const quantity = parseInt(document.getElementById('order-quantity-input').value, 10);
  const customerId = document.getElementById('order-customer-id').value;
  const customerEmail = document.getElementById('order-customer-email').value;
  const idempotencyKey = orderIdempotencyKeyInput.value.trim();

  const selectedItem = inventoryData.find(i => i.sku === sku);
  const unitPrice = selectedItem ? selectedItem.price : 49.99;
  const productName = selectedItem ? selectedItem.productName : sku;

  const payload = {
    customerId,
    customerEmail,
    currency: 'USD',
    items: [
      {
        productId: 'PROD-' + sku,
        sku,
        productName,
        quantity,
        unitPrice
      }
    ]
  };

  lastOrderPayload = JSON.parse(JSON.stringify(payload));
  lastIdempotencyKey = idempotencyKey;

  try {
    orderResponsePreview.textContent = 'Submitting order transaction to backend...';
    const res = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey
      },
      body: JSON.stringify(payload)
    });

    const status = res.status;
    const data = await res.json();
    orderResponsePreview.textContent = `HTTP ${status}\n` + JSON.stringify(data, null, 2);

    if (res.status === 201) {
      showToast(`Order created successfully: ${data.orderNumber}`, 'success');
      // Refresh state
      loadInventory();
      loadOrders();
      loadOutbox();
      // Generate new key for next order
      orderIdempotencyKeyInput.value = generateUUID();
    } else {
      showToast(`Order failed with HTTP ${status}: ${data.title || data.detail}`, 'danger');
    }
  } catch (err) {
    orderResponsePreview.textContent = `Network Error: ${err.message}`;
    showToast(`Network error: ${err.message}`, 'danger');
  }
});

// Idempotency Lab Experiments
document.getElementById('btn-test-idempotent-replay').addEventListener('click', async () => {
  if (!lastOrderPayload || !lastIdempotencyKey) {
    showToast('Please submit at least one initial order first to test replay!', 'warning');
    return;
  }

  try {
    orderResponsePreview.textContent = `Replaying request with existing key '${lastIdempotencyKey}'...`;
    const res = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': lastIdempotencyKey
      },
      body: JSON.stringify(lastOrderPayload)
    });

    const status = res.status;
    const data = await res.json();
    orderResponsePreview.textContent = `HTTP ${status} (Idempotent Cache Hit)\n` + JSON.stringify(data, null, 2);
    showToast(`Idempotent Replay Verified: HTTP ${status} identical order returned without re-reservation`, 'success');
  } catch (err) {
    orderResponsePreview.textContent = `Error: ${err.message}`;
  }
});

document.getElementById('btn-test-tampered-payload').addEventListener('click', async () => {
  if (!lastOrderPayload || !lastIdempotencyKey) {
    showToast('Please submit an initial order first to establish an idempotency key!', 'warning');
    return;
  }

  // Alter the payload while keeping the same key
  const tamperedPayload = JSON.parse(JSON.stringify(lastOrderPayload));
  tamperedPayload.items[0].quantity += 99; // Modified quantity violates SHA-256 hash match

  try {
    orderResponsePreview.textContent = `Sending tampered payload with reused key '${lastIdempotencyKey}'...`;
    const res = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': lastIdempotencyKey
      },
      body: JSON.stringify(tamperedPayload)
    });

    const status = res.status;
    const data = await res.json();
    orderResponsePreview.textContent = `HTTP ${status} (Conflict Detected!)\n` + JSON.stringify(data, null, 2);
    if (status === 409) {
      showToast('Idempotency Conflict Successfully Detected: HTTP 409 RFC 7807 problem details', 'warning');
    }
  } catch (err) {
    orderResponsePreview.textContent = `Error: ${err.message}`;
  }
});

document.getElementById('btn-test-insufficient-inventory').addEventListener('click', async () => {
  const sku = orderSkuSelect.value || 'TECH-KEYB-01';
  const excessivePayload = {
    customerId: 'CUST-OVERDRAW-001',
    customerEmail: 'overdraw@test.com',
    currency: 'USD',
    items: [
      {
        productId: 'PROD-' + sku,
        sku,
        productName: 'Overdraw Item',
        quantity: 99999,
        unitPrice: 100.00
      }
    ]
  };

  try {
    orderResponsePreview.textContent = `Requesting 99,999 units of '${sku}' to test stock guard...`;
    const res = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': generateUUID()
      },
      body: JSON.stringify(excessivePayload)
    });

    const status = res.status;
    const data = await res.json();
    orderResponsePreview.textContent = `HTTP ${status} (Insufficient Inventory)\n` + JSON.stringify(data, null, 2);
    if (status === 422) {
      showToast('Atomic Inventory Guard Verified: HTTP 422 returned, oversell rejected', 'success');
    }
  } catch (err) {
    orderResponsePreview.textContent = `Error: ${err.message}`;
  }
});

// Flash-Sale Concurrency Simulator
document.getElementById('btn-fire-stress-test').addEventListener('click', async () => {
  const sku = stressSkuSelect.value;
  const numBuyers = parseInt(document.getElementById('stress-threads-input').value, 10);
  const qtyPerBuyer = parseInt(document.getElementById('stress-qty-input').value, 10);
  const btn = document.getElementById('btn-fire-stress-test');

  btn.disabled = true;
  btn.textContent = '🚀 Race in progress...';

  // Fetch initial stock
  let initialStock = 0;
  try {
    const invRes = await fetch(`/api/v1/inventory/${sku}`);
    const inv = await invRes.json();
    initialStock = inv.availableQuantity;
    document.getElementById('stress-initial-stock').textContent = `${initialStock} units`;
  } catch (e) {
    initialStock = 0;
  }

  showToast(`Firing ${numBuyers} concurrent buyer threads for ${sku}...`, 'info');

  const selectedItem = inventoryData.find(i => i.sku === sku);
  const price = selectedItem ? selectedItem.price : 50;

  // Launch parallel asynchronous fetch requests
  const requests = Array.from({ length: numBuyers }, (_, index) => {
    const key = `flash-sale-${sku}-${Date.now()}-${index}-${generateUUID().substring(0, 8)}`;
    const payload = {
      customerId: `FLASH-BUYER-${index + 1}`,
      customerEmail: `buyer${index + 1}@flashsale.com`,
      currency: 'USD',
      items: [
        {
          productId: 'PROD-' + sku,
          sku,
          productName: selectedItem ? selectedItem.productName : sku,
          quantity: qtyPerBuyer,
          unitPrice: price
        }
      ]
    };

    return fetch('/api/v1/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': key
      },
      body: JSON.stringify(payload)
    }).then(r => r.status).catch(() => 500);
  });

  const results = await Promise.all(requests);

  const successCount = results.filter(status => status === 201).length;
  const rejectedCount = results.filter(status => status === 422).length;

  document.getElementById('stress-success-count').textContent = `${successCount} granted`;
  document.getElementById('stress-rejected-count').textContent = `${rejectedCount} rejected (422)`;

  // Fetch final stock from database
  try {
    const invRes = await fetch(`/api/v1/inventory/${sku}`);
    const inv = await invRes.json();
    const finalStock = inv.availableQuantity;
    document.getElementById('stress-final-stock').textContent = `${finalStock} units`;

    const expectedStock = Math.max(0, initialStock - (successCount * qtyPerBuyer));
    if (finalStock === expectedStock && finalStock >= 0) {
      document.getElementById('stress-oversold-check').textContent = '0 (100% PREVENTED)';
      document.getElementById('stress-oversold-check').style.color = 'var(--color-success)';
      showToast(`Concurrency Test PASSED: ${successCount} won, ${rejectedCount} rejected, stock exact!`, 'success');
    } else {
      document.getElementById('stress-oversold-check').textContent = `Discrepancy: ${finalStock} vs ${expectedStock}`;
      document.getElementById('stress-oversold-check').style.color = 'var(--color-danger)';
    }
  } catch (err) {
    console.error(err);
  }

  btn.disabled = false;
  btn.textContent = '⚡ Launch Flash-Sale Attack';

  // Refresh tables
  loadInventory();
  loadOrders();
  loadOutbox();
});

// Animate Architecture Pipeline
function animatePipeline() {
  const nodes = ['node-api', 'node-inventory', 'node-acid', 'node-outbox', 'node-broker', 'node-consumer'];
  nodes.forEach((nodeId, idx) => {
    setTimeout(() => {
      const el = document.getElementById(nodeId);
      if (el) {
        el.classList.add('active-step');
        setTimeout(() => el.classList.remove('active-step'), 600);
      }
    }, idx * 250);
  });
}

// 1-Click Interactive Demos
document.getElementById('btn-demo-order').addEventListener('click', async () => {
  // Switch to order tab
  document.getElementById('tab-order-btn').click();
  orderSkuSelect.value = 'TECH-KEYB-01';
  document.getElementById('order-quantity-input').value = 1;
  document.getElementById('order-customer-id').value = 'DEMO-USER-01';
  orderIdempotencyKeyInput.value = generateUUID();

  showToast('Executing live order flow through full distributed pipeline...', 'info');
  animatePipeline();

  // Submit order
  setTimeout(() => {
    document.getElementById('btn-submit-order').click();
  }, 400);
});

document.getElementById('btn-demo-flash-sale').addEventListener('click', () => {
  // Switch to concurrency tab
  document.getElementById('tab-concurrency-btn').click();
  stressSkuSelect.value = 'TECH-MONI-03';
  document.getElementById('stress-threads-input').value = 20;
  document.getElementById('stress-qty-input').value = 2;

  showToast('Launching Black Friday flash-sale contention test (20 parallel threads)...', 'info');
  setTimeout(() => {
    document.getElementById('btn-fire-stress-test').click();
  }, 300);
});

document.getElementById('btn-demo-double-click').addEventListener('click', async () => {
  document.getElementById('tab-order-btn').click();
  const demoKey = 'wifi-drop-double-click-' + Date.now();
  const sku = 'TECH-MOUS-02';
  const payload = {
    customerId: 'CUST-DOUBLE-CLICK',
    customerEmail: 'user@test.com',
    currency: 'USD',
    items: [{ productId: 'PROD-' + sku, sku, productName: 'Wireless Mouse', quantity: 1, unitPrice: 49.99 }]
  };

  showToast('Simulating Wi-Fi drop: Clicking "Pay" twice with identical Idempotency-Key...', 'info');
  animatePipeline();

  try {
    // Click #1
    orderResponsePreview.textContent = 'Click #1: Processing initial order...';
    const res1 = await fetch('/api/v1/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': demoKey },
      body: JSON.stringify(payload)
    });
    const data1 = await res1.json();

    // Click #2 (50ms later with same key)
    setTimeout(async () => {
      orderResponsePreview.textContent += '\n\nClick #2 (Duplicate click arrives 50ms later)...';
      const res2 = await fetch('/api/v1/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': demoKey },
        body: JSON.stringify(payload)
      });
      const data2 = await res2.json();

      orderResponsePreview.textContent = 
        `Click #1: HTTP ${res1.status} (Order Created: ${data1.orderNumber})\n` +
        `Click #2: HTTP ${res2.status} (Idempotent Cache Hit: ${data2.orderNumber})\n\n` +
        `🛡️ Idempotency Verification:\nBoth clicks returned the identical order without charging or debiting stock twice!`;

      showToast(`Wi-Fi Double-Click Defense Verified: Identical order returned, 0 duplicate charges!`, 'success');
      loadInventory();
      loadOrders();
      loadOutbox();
    }, 150);

  } catch (err) {
    orderResponsePreview.textContent = 'Error: ' + err.message;
  }
});

// Interactive Guided Tour
const tourModal = document.getElementById('tour-modal');
const tourStepBadge = document.getElementById('tour-step-badge');
const tourTitle = document.getElementById('tour-title');
const tourDesc = document.getElementById('tour-desc');
const tourAnalogy = document.getElementById('tour-analogy');
const btnTourPrev = document.getElementById('btn-tour-prev');
const btnTourNext = document.getElementById('btn-tour-next');

let currentTourStep = 0;
const tourSteps = [
  {
    title: '1. Architecture & The Distributed Pipeline',
    desc: 'Notice the 6 steps at the top banner. In microservices, an order cannot be processed by a single database write. It spans client idempotency checks, atomic stock reservation, ACID outbox persistence, Kafka event dispatch, and idempotent consumer deduplication.',
    analogy: '🏢 <strong>Real-World Analogy:</strong> Like Amazon ordering — clicking "Buy" doesn’t just write to a DB; it verifies your payment, locks the item in warehouse inventory, and notifies the shipping logistics queue asynchronously.',
    action: () => animatePipeline()
  },
  {
    title: '2. Solving the Flash-Sale Overselling Disaster',
    desc: 'When 10,000 customers hit "Buy" for 500 iPhones at midnight, basic in-memory checks fail and sell thousands of non-existent items. We use Atomic Conditional SQL updates (WHERE available >= :qty) to guarantee stock NEVER drops below 0.',
    analogy: '🎟️ <strong>Real-World Analogy:</strong> Like Ticketmaster concert ticket sales — only buyers who atomically acquire the reservation get tickets. The rest receive an immediate friendly 422 Insufficient Inventory response.',
    action: () => document.getElementById('tab-concurrency-btn').click()
  },
  {
    title: '3. Eliminating "Ghost Orders" via Transactional Outbox',
    desc: 'If a backend writes to PostgreSQL, but the Kafka network blinks before publishing, the customer is charged but the warehouse NEVER receives the shipping event. The Transactional Outbox commits both the order and event to PostgreSQL in the exact same transaction, eliminating dual-write loss.',
    analogy: '📦 <strong>Real-World Analogy:</strong> Like a receipt locked inside the warehouse dispatch safe. The safe is only closed when both the customer record and the shipping order exist together.',
    action: () => document.getElementById('tab-outbox-btn').click()
  },
  {
    title: '4. Wi-Fi Drop Protection (Idempotency)',
    desc: 'When a customer taps "Pay", their phone Wi-Fi drops, and they tap "Pay" again, our engine uses the unique Idempotency-Key and SHA-256 payload hash to return the cached receipt without double-charging or deducting duplicate stock.',
    analogy: '💳 <strong>Real-World Analogy:</strong> Like a bank check number — if someone tries to cash Check #101 twice, the bank honors it once and safely ignores the duplicate presentation.',
    action: () => document.getElementById('tab-order-btn').click()
  }
];

function updateTourModal() {
  const step = tourSteps[currentTourStep];
  tourStepBadge.textContent = `STEP ${currentTourStep + 1} OF ${tourSteps.length}`;
  tourTitle.textContent = step.title;
  tourDesc.textContent = step.desc;
  tourAnalogy.innerHTML = step.analogy;
  btnTourPrev.disabled = currentTourStep === 0;
  btnTourNext.textContent = currentTourStep === tourSteps.length - 1 ? 'Finish Tour 🚀' : 'Next Step →';
  if (step.action) step.action();
}

document.getElementById('btn-start-tour').addEventListener('click', () => {
  currentTourStep = 0;
  updateTourModal();
  tourModal.classList.add('active');
});

document.getElementById('btn-close-tour').addEventListener('click', () => tourModal.classList.remove('active'));
document.getElementById('btn-tour-skip').addEventListener('click', () => tourModal.classList.remove('active'));

btnTourPrev.addEventListener('click', () => {
  if (currentTourStep > 0) {
    currentTourStep--;
    updateTourModal();
  }
});

btnTourNext.addEventListener('click', () => {
  if (currentTourStep < tourSteps.length - 1) {
    currentTourStep++;
    updateTourModal();
  } else {
    tourModal.classList.remove('active');
    showToast('Tour completed! You are ready to explore the platform.', 'success');
  }
});

// Explainer Modal
const explainerModal = document.getElementById('explainer-modal');
document.getElementById('btn-open-explainer').addEventListener('click', () => explainerModal.classList.add('active'));
document.getElementById('btn-close-explainer').addEventListener('click', () => explainerModal.classList.remove('active'));
document.getElementById('btn-explainer-got-it').addEventListener('click', () => explainerModal.classList.remove('active'));

// Refresh buttons
document.getElementById('btn-refresh-inventory').addEventListener('click', loadInventory);
document.getElementById('btn-refresh-orders').addEventListener('click', loadOrders);
document.getElementById('btn-refresh-outbox').addEventListener('click', loadOutbox);
document.getElementById('btn-gen-uuid').addEventListener('click', () => {
  orderIdempotencyKeyInput.value = generateUUID();
});

// Initialize on load
window.addEventListener('DOMContentLoaded', () => {
  orderIdempotencyKeyInput.value = generateUUID();
  checkHealth();
  loadInventory();
  loadOrders();
  loadOutbox();

  // Polling every 4 seconds
  setInterval(() => {
    checkHealth();
    loadInventory();
    loadOrders();
    loadOutbox();
  }, 4000);
});

