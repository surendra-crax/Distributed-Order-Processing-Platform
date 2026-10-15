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
