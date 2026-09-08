// App-wide in-memory state: current cart (never persisted - losing an unpaid, in-progress order
// on a crash is acceptable, see CartHolder.kt for the same reasoning on Android) and a small
// pub/sub settings cache so screens re-render when settings change without a manual reload.
import { getSettings, saveSettings, uuid } from './db.js';

// ---- Cart ----
const cartListeners = new Set();
let cartLines = []; // { lineId, productId, productName, variantId, variantName, unitPriceKopecks, quantity, isSimpleProduct, simpleInventoryItemId }
let checkoutIdempotencyKey = null;

export function getCart() { return cartLines; }

export function onCartChange(fn) { cartListeners.add(fn); return () => cartListeners.delete(fn); }

function notifyCart() { cartListeners.forEach((fn) => fn(cartLines)); }

export function addToCart(line) {
  const idx = cartLines.findIndex((l) => l.productId === line.productId && l.variantId === line.variantId);
  if (idx >= 0) {
    cartLines = cartLines.map((l, i) => (i === idx ? { ...l, quantity: l.quantity + 1 } : l));
  } else {
    cartLines = [...cartLines, { ...line, lineId: uuid(), quantity: 1 }];
  }
  notifyCart();
}

export function setLineQuantity(lineId, quantity) {
  if (quantity <= 0) {
    cartLines = cartLines.filter((l) => l.lineId !== lineId);
  } else {
    cartLines = cartLines.map((l) => (l.lineId === lineId ? { ...l, quantity } : l));
  }
  notifyCart();
}

export function removeCartLine(lineId) {
  cartLines = cartLines.filter((l) => l.lineId !== lineId);
  notifyCart();
}

export function clearCart() {
  cartLines = [];
  checkoutIdempotencyKey = null;
  notifyCart();
}

export function cartTotalKopecks() {
  return cartLines.reduce((sum, l) => sum + l.unitPriceKopecks * l.quantity, 0);
}

export function cartItemCount() {
  return cartLines.reduce((sum, l) => sum + l.quantity, 0);
}

/** Same idempotency key reused across retries (double-tap, reload mid-checkout) until a fresh order starts. */
export function checkoutKey() {
  if (!checkoutIdempotencyKey) checkoutIdempotencyKey = uuid();
  return checkoutIdempotencyKey;
}

// ---- Settings cache ----
let settingsCache = null;
const settingsListeners = new Set();

export async function loadSettings() {
  settingsCache = await getSettings();
  settingsListeners.forEach((fn) => fn(settingsCache));
  return settingsCache;
}

export function currentSettings() { return settingsCache; }

export function onSettingsChange(fn) { settingsListeners.add(fn); return () => settingsListeners.delete(fn); }

export async function updateSettings(partial) {
  settingsCache = await saveSettings(partial);
  settingsListeners.forEach((fn) => fn(settingsCache));
  return settingsCache;
}
