// IndexedDB data layer - the browser equivalent of the Android app's Room database. Every
// business transaction that must be atomic (complete a sale, post a purchase, process a return)
// runs inside ONE IndexedDB transaction spanning every store it touches, so a crash mid-way
// rolls back everything, exactly like the Room `withTransaction` blocks in the Kotlin version.
import { UnitCost, weightedAverageCost, recipeCostPerPortion, evaluateCashPayment, deductStock,
  inventoryCountDifference, validateReturn } from './core.js';
import { Money } from './money.js';

const DB_NAME = 'borzini';
const DB_VERSION = 1;

function uuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}
export { uuid };

let dbPromise = null;
export function openDb() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      const mk = (name, keyPath, indices = []) => {
        if (db.objectStoreNames.contains(name)) return;
        const store = db.createObjectStore(name, keyPath);
        indices.forEach(([idxName, keyPathOrArr, opts]) => store.createIndex(idxName, keyPathOrArr, opts));
      };
      mk('categories', { keyPath: 'id' });
      mk('products', { keyPath: 'id' }, [['categoryId', 'categoryId']]);
      mk('variants', { keyPath: 'id' }, [['productId', 'productId']]);
      mk('recipeLines', { keyPath: 'id' }, [['variantId', 'variantId'], ['inventoryItemId', 'inventoryItemId']]);
      mk('inventoryItems', { keyPath: 'id' });
      mk('purchases', { keyPath: 'id' });
      mk('purchaseLines', { keyPath: 'id' }, [['purchaseId', 'purchaseId']]);
      mk('stockMovements', { keyPath: 'id' }, [['inventoryItemId', 'inventoryItemId'], ['createdAt', 'createdAt']]);
      mk('sales', { keyPath: 'id' }, [['createdAt', 'createdAt'], ['receiptNumber', 'receiptNumber']]);
      mk('saleItems', { keyPath: 'id' }, [['saleId', 'saleId']]);
      mk('saleItemDeductions', { keyPath: 'id' }, [['saleItemId', 'saleItemId']]);
      mk('returns', { keyPath: 'id' }, [['saleId', 'saleId'], ['createdAt', 'createdAt']]);
      mk('returnItems', { keyPath: 'id' }, [['returnId', 'returnId'], ['saleItemId', 'saleItemId']]);
      mk('expenses', { keyPath: 'id' }, [['date', 'date']]);
      mk('goals', { keyPath: 'id' });
      mk('syncQueue', { keyPath: 'id', autoIncrement: true }, [['status', 'status']]);
      mk('settings', { keyPath: 'key' });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

function reqToPromise(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function txDone(tx) {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error('transaction aborted'));
  });
}

export async function getAll(storeName, indexName = null, query = null) {
  const db = await openDb();
  const tx = db.transaction(storeName, 'readonly');
  const store = indexName ? tx.objectStore(storeName).index(indexName) : tx.objectStore(storeName);
  return reqToPromise(store.getAll(query));
}

/** All rows whose indexName value is in [lowerInclusive, upperExclusive). */
export async function getAllInRange(storeName, indexName, lowerInclusive, upperExclusive) {
  const db = await openDb();
  const tx = db.transaction(storeName, 'readonly');
  const range = IDBKeyRange.bound(lowerInclusive, upperExclusive, false, true);
  return reqToPromise(tx.objectStore(storeName).index(indexName).getAll(range));
}

export async function get(storeName, id) {
  const db = await openDb();
  const tx = db.transaction(storeName, 'readonly');
  return reqToPromise(tx.objectStore(storeName).get(id));
}

export async function put(storeName, value) {
  const db = await openDb();
  const tx = db.transaction(storeName, 'readwrite');
  tx.objectStore(storeName).put(value);
  await txDone(tx);
  return value;
}

export async function putAll(storeName, values) {
  const db = await openDb();
  const tx = db.transaction(storeName, 'readwrite');
  const store = tx.objectStore(storeName);
  values.forEach((v) => store.put(v));
  await txDone(tx);
}

export async function remove(storeName, id) {
  const db = await openDb();
  const tx = db.transaction(storeName, 'readwrite');
  tx.objectStore(storeName).delete(id);
  await txDone(tx);
}

export async function clearAllStores() {
  const db = await openDb();
  const names = Array.from(db.objectStoreNames);
  const tx = db.transaction(names, 'readwrite');
  names.forEach((n) => tx.objectStore(n).clear());
  await txDone(tx);
}

// ---------------------------------------------------------------------------------------------
// Settings (single row)
// ---------------------------------------------------------------------------------------------
const DEFAULT_SETTINGS = {
  key: 'app',
  themeMode: 'LIGHT',
  accentColorHex: '4A1420',
  textScale: 1.0,
  timezoneId: 'Europe/Moscow',
  negativeStockAllowed: false,
  demoModeEnabled: false,
  coffeeShopName: 'BORZINI',
  autoAcquiringFeeEnabled: false,
  autoAcquiringFeePercent: 1.8,
  googleAccountEmail: null,
  driveFolderId: null,
  spreadsheetId: null,
  lastSyncEpochMillis: null,
};

export async function getSettings() {
  const s = await get('settings', 'app');
  return s ? { ...DEFAULT_SETTINGS, ...s } : { ...DEFAULT_SETTINGS };
}

export async function saveSettings(partial) {
  const current = await getSettings();
  const updated = { ...current, ...partial, key: 'app' };
  await put('settings', updated);
  return updated;
}

// ---------------------------------------------------------------------------------------------
// Sync queue helper - every mutating repository call below enqueues a row in the SAME transaction
// ---------------------------------------------------------------------------------------------
function enqueueSync(tx, entityType, entityId, operation, payload) {
  tx.objectStore('syncQueue').put({
    entityType, entityId, operation, payload, createdAt: Date.now(), attempts: 0, lastError: null, status: 'PENDING',
  });
}

// ---------------------------------------------------------------------------------------------
// Catalog: categories, products, variants, recipes
// ---------------------------------------------------------------------------------------------
export async function saveCategory({ id, name, now }) {
  const db = await openDb();
  const tx = db.transaction(['categories', 'syncQueue'], 'readwrite');
  const store = tx.objectStore('categories');
  const existing = id ? await reqToPromise(store.get(id)) : null;
  const categoryId = id || uuid();
  let sortOrder = existing ? existing.sortOrder : 0;
  if (!existing) {
    const all = await reqToPromise(store.getAll());
    sortOrder = all.length ? Math.max(...all.map((c) => c.sortOrder)) + 1 : 0;
  }
  const row = { id: categoryId, name, sortOrder, isArchived: existing ? existing.isArchived : false, updatedAt: now };
  store.put(row);
  enqueueSync(tx, 'CATEGORY', categoryId, 'UPSERT', row);
  await txDone(tx);
  return categoryId;
}

export async function setCategoryArchived(id, archived, now) {
  const db = await openDb();
  const tx = db.transaction(['categories', 'syncQueue'], 'readwrite');
  const store = tx.objectStore('categories');
  const existing = await reqToPromise(store.get(id));
  if (!existing) { await txDone(tx); return; }
  const row = { ...existing, isArchived: archived, updatedAt: now };
  store.put(row);
  enqueueSync(tx, 'CATEGORY', id, 'UPSERT', row);
  await txDone(tx);
}

/** variants: [{ id?, name, priceKopecks, recipe: [{ inventoryItemId, quantityPerPortionMilli }] }] */
export async function saveProductWithVariants({ productId, categoryId, name, description, isSimpleProduct, simpleInventoryItemId, variants, now, isDemo = false }) {
  const db = await openDb();
  const tx = db.transaction(['products', 'variants', 'recipeLines', 'syncQueue'], 'readwrite');
  const products = tx.objectStore('products');
  const variantsStore = tx.objectStore('variants');
  const recipeStore = tx.objectStore('recipeLines');

  const existing = productId ? await reqToPromise(products.get(productId)) : null;
  const id = productId || uuid();
  let sortOrder = existing ? existing.sortOrder : 0;
  if (!existing) {
    const all = await reqToPromise(products.getAll());
    sortOrder = all.length ? Math.max(...all.map((p) => p.sortOrder)) + 1 : 0;
  }
  const productRow = {
    id, categoryId, name, description: description || null, photoUri: null,
    isSimpleProduct, simpleInventoryItemId: simpleInventoryItemId || null,
    isPinnedPopular: existing ? existing.isPinnedPopular : false,
    isAvailableForSale: existing ? existing.isAvailableForSale : true,
    isArchived: existing ? existing.isArchived : false,
    sortOrder, isDemo: isDemo || (existing ? existing.isDemo : false),
    createdAt: existing ? existing.createdAt : now, updatedAt: now,
  };
  products.put(productRow);

  // A variant row (name + price) is created for EVERY product, simple or recipe-based - the POS
  // screen always sells "a variant" (see pos.js: it reads price off entry.variants[0]). What
  // differs for a simple product is that its stock deduction bypasses recipeLines entirely and
  // goes straight through simpleInventoryItemId (see completeSale in this file), so no recipe
  // rows are written for it.
  const variantRows = [];
  for (const v of variants) {
    const variantId = v.id || uuid();
    const variantRow = { id: variantId, productId: id, name: v.name || null, priceKopecks: v.priceKopecks, sortOrder: v.sortOrder || 0, isArchived: false, updatedAt: now };
    variantsStore.put(variantRow);
    variantRows.push({ ...variantRow, recipe: v.recipe || [] });
    if (!isSimpleProduct) {
      const existingLines = await reqToPromise(recipeStore.index('variantId').getAll(variantId));
      existingLines.forEach((l) => recipeStore.delete(l.id));
      (v.recipe || []).forEach((r, idx) => {
        recipeStore.put({ id: uuid(), variantId, inventoryItemId: r.inventoryItemId, quantityPerPortionMilli: r.quantityPerPortionMilli, sortOrder: idx });
      });
    }
  }

  enqueueSync(tx, 'PRODUCT', id, 'UPSERT', { ...productRow, variants: variantRows });
  await txDone(tx);
  return id;
}

export async function setProductArchived(id, archived, now) {
  const db = await openDb();
  const tx = db.transaction(['products', 'syncQueue'], 'readwrite');
  const store = tx.objectStore('products');
  const existing = await reqToPromise(store.get(id));
  if (!existing) { await txDone(tx); return; }
  const row = { ...existing, isArchived: archived, updatedAt: now };
  store.put(row);
  enqueueSync(tx, 'PRODUCT', id, 'UPSERT', row);
  await txDone(tx);
}

export async function setProductPinned(id, pinned, now) {
  const existing = await get('products', id);
  if (!existing) return;
  await put('products', { ...existing, isPinnedPopular: pinned, updatedAt: now });
}

export async function currentCostForVariant(variantId) {
  const lines = await getAll('recipeLines', 'variantId', variantId);
  if (!lines.length) return 0;
  const items = await getAll('inventoryItems');
  const byId = new Map(items.map((i) => [i.id, i]));
  const coreLines = lines.map((l) => {
    const item = byId.get(l.inventoryItemId);
    return item ? { quantityPerPortionMilli: l.quantityPerPortionMilli, unitCostMicro: item.avgUnitCostMicro } : null;
  }).filter(Boolean);
  return recipeCostPerPortion(coreLines);
}

// ---------------------------------------------------------------------------------------------
// Inventory
// ---------------------------------------------------------------------------------------------
export async function saveInventoryItem({ id, name, category, baseUnit, minAllowedMilli, supplier, now, isDemo = false }) {
  const db = await openDb();
  const tx = db.transaction(['inventoryItems', 'syncQueue'], 'readwrite');
  const store = tx.objectStore('inventoryItems');
  const existing = id ? await reqToPromise(store.get(id)) : null;
  const itemId = id || uuid();
  const row = {
    id: itemId, name, category, baseUnit,
    onHandMilli: existing ? existing.onHandMilli : 0,
    avgUnitCostMicro: existing ? existing.avgUnitCostMicro : 0,
    minAllowedMilli, supplier: supplier || null,
    isArchived: existing ? existing.isArchived : false,
    sortOrder: existing ? existing.sortOrder : 0,
    isDemo: isDemo || (existing ? existing.isDemo : false),
    updatedAt: now,
  };
  store.put(row);
  enqueueSync(tx, 'INVENTORY_ITEM', itemId, 'UPSERT', row);
  await txDone(tx);
  return itemId;
}

export async function setInventoryItemArchived(id, archived, now) {
  const existing = await get('inventoryItems', id);
  if (!existing) return;
  const row = { ...existing, isArchived: archived, updatedAt: now };
  await put('inventoryItems', row);
}

export async function manualWriteOff({ itemId, quantityMilli, note, allowNegative, now, isDemo = false }) {
  const db = await openDb();
  const tx = db.transaction(['inventoryItems', 'stockMovements', 'syncQueue'], 'readwrite');
  const items = tx.objectStore('inventoryItems');
  const item = await reqToPromise(items.get(itemId));
  if (!item) { await txDone(tx); return { ok: false, available: 0, required: quantityMilli }; }
  const result = deductStock(item.onHandMilli, quantityMilli, allowNegative);
  if (!result.ok) { await txDone(tx); return result; }
  const updated = { ...item, onHandMilli: result.newOnHandMilli, updatedAt: now };
  items.put(updated);
  const movement = { id: uuid(), inventoryItemId: itemId, reason: 'MANUAL_WRITE_OFF', quantityDeltaMilli: -quantityMilli, unitCostAtMovementMicro: item.avgUnitCostMicro, note: note || null, isDemo, createdAt: now };
  tx.objectStore('stockMovements').put(movement);
  enqueueSync(tx, 'INVENTORY_ITEM', itemId, 'UPSERT', updated);
  enqueueSync(tx, 'STOCK_MOVEMENT', movement.id, 'UPSERT', movement);
  await txDone(tx);
  return { ok: true };
}

export async function performInventoryCount({ itemId, countedMilli, note, now, isDemo = false }) {
  const db = await openDb();
  const tx = db.transaction(['inventoryItems', 'stockMovements', 'syncQueue'], 'readwrite');
  const items = tx.objectStore('inventoryItems');
  const item = await reqToPromise(items.get(itemId));
  if (!item) { await txDone(tx); return; }
  const diff = inventoryCountDifference(item.onHandMilli, countedMilli);
  if (diff === 0) { await txDone(tx); return; }
  const updated = { ...item, onHandMilli: countedMilli, updatedAt: now };
  items.put(updated);
  const movement = { id: uuid(), inventoryItemId: itemId, reason: 'INVENTORY_ADJUSTMENT', quantityDeltaMilli: diff, unitCostAtMovementMicro: item.avgUnitCostMicro, note: note || null, isDemo, createdAt: now };
  tx.objectStore('stockMovements').put(movement);
  enqueueSync(tx, 'INVENTORY_ITEM', itemId, 'UPSERT', updated);
  enqueueSync(tx, 'STOCK_MOVEMENT', movement.id, 'UPSERT', movement);
  await txDone(tx);
}

// ---------------------------------------------------------------------------------------------
// Purchases
// ---------------------------------------------------------------------------------------------
/** lines: [{ inventoryItemId, packageCount, unitsPerPackageMilli, packageCostKopecks }] */
export async function saveDraftPurchase({ purchaseId, date, invoiceNumber, supplier, comment, lines, now, isDemo = false }) {
  const db = await openDb();
  const tx = db.transaction(['purchases', 'purchaseLines'], 'readwrite');
  const purchases = tx.objectStore('purchases');
  const existing = purchaseId ? await reqToPromise(purchases.get(purchaseId)) : null;
  if (existing && existing.isPosted) throw new Error('Cannot edit a purchase that was already posted');
  const id = purchaseId || uuid();
  const total = Money.sum(lines.map((l) => l.packageCostKopecks * l.packageCount));
  purchases.put({
    id, date, invoiceNumber: invoiceNumber || null, supplier, totalKopecks: total,
    comment: comment || null, isPosted: false, isDemo, createdAt: existing ? existing.createdAt : now,
  });
  const lineStore = tx.objectStore('purchaseLines');
  const existingLines = await reqToPromise(lineStore.index('purchaseId').getAll(id));
  existingLines.forEach((l) => lineStore.delete(l.id));
  lines.forEach((l) => {
    lineStore.put({
      id: uuid(), purchaseId: id, inventoryItemId: l.inventoryItemId,
      packageCount: l.packageCount, unitsPerPackageMilli: l.unitsPerPackageMilli,
      packageCostKopecks: l.packageCostKopecks, totalCostKopecks: l.packageCostKopecks * l.packageCount,
      totalBaseUnitsMilli: l.unitsPerPackageMilli * l.packageCount,
    });
  });
  await txDone(tx);
  return id;
}

export async function postPurchase(purchaseId, now) {
  const db = await openDb();
  const tx = db.transaction(['purchases', 'purchaseLines', 'inventoryItems', 'stockMovements', 'syncQueue'], 'readwrite');
  const purchases = tx.objectStore('purchases');
  const purchase = await reqToPromise(purchases.get(purchaseId));
  if (!purchase || purchase.isPosted) { await txDone(tx); return false; }

  const lines = await reqToPromise(tx.objectStore('purchaseLines').index('purchaseId').getAll(purchaseId));
  const items = tx.objectStore('inventoryItems');
  for (const line of lines) {
    const item = await reqToPromise(items.get(line.inventoryItemId));
    if (!item) continue;
    const incomingUnitCost = UnitCost.fromPackagePurchase(line.packageCostKopecks, line.packageCount, line.unitsPerPackageMilli);
    const newAvg = weightedAverageCost(item.onHandMilli, item.avgUnitCostMicro, line.totalBaseUnitsMilli, incomingUnitCost);
    const newOnHand = item.onHandMilli + line.totalBaseUnitsMilli;
    const updated = { ...item, onHandMilli: newOnHand, avgUnitCostMicro: newAvg, updatedAt: now };
    items.put(updated);

    const movement = {
      id: uuid(), inventoryItemId: item.id, reason: 'PURCHASE_RECEIPT', quantityDeltaMilli: line.totalBaseUnitsMilli,
      unitCostAtMovementMicro: incomingUnitCost, relatedPurchaseId: purchaseId, isDemo: purchase.isDemo, createdAt: now,
    };
    tx.objectStore('stockMovements').put(movement);
    enqueueSync(tx, 'INVENTORY_ITEM', item.id, 'UPSERT', updated);
    enqueueSync(tx, 'STOCK_MOVEMENT', movement.id, 'UPSERT', movement);
  }

  const postedPurchase = { ...purchase, isPosted: true, postedAt: now };
  purchases.put(postedPurchase);
  enqueueSync(tx, 'PURCHASE', purchaseId, 'UPSERT', { ...postedPurchase, lineCount: lines.length });
  await txDone(tx);
  return true;
}

// ---------------------------------------------------------------------------------------------
// Sales - the one operation the whole app exists for. Idempotent: `id` IS the checkout's
// idempotency key, so retrying with the same id is a safe no-op (checked first, before any
// stock is touched), matching SaleRepository.completeSale in the Android app exactly.
// ---------------------------------------------------------------------------------------------
/** lines: [{ productId, productName, variantId, variantName, unitPriceKopecks, quantity, isSimpleProduct, simpleInventoryItemId }] */
export async function completeSale({ idempotencyKey, lines, paymentMethod, cashReceivedKopecks, allowNegative, now, isDemo = false }) {
  const db = await openDb();
  const tx = db.transaction(['sales', 'saleItems', 'saleItemDeductions', 'inventoryItems', 'recipeLines', 'stockMovements', 'syncQueue'], 'readwrite');
  const salesStore = tx.objectStore('sales');

  const existing = await reqToPromise(salesStore.get(idempotencyKey));
  if (existing) {
    await txDone(tx);
    return { status: 'ok', sale: existing, change: existing.changeGivenKopecks || 0 };
  }

  const allSales = await reqToPromise(salesStore.getAll());
  const receiptNumber = allSales.length ? Math.max(...allSales.map((s) => s.receiptNumber)) + 1 : 1;

  const itemsStore = tx.objectStore('inventoryItems');
  const recipeStore = tx.objectStore('recipeLines');
  const itemCache = new Map();
  const getItem = async (id) => {
    if (itemCache.has(id)) return itemCache.get(id);
    const item = await reqToPromise(itemsStore.get(id));
    itemCache.set(id, item);
    return item;
  };

  let totalKopecks = 0;
  let cogsKopecks = 0;
  const saleItemRows = [];
  const deductionRows = [];
  const shortages = [];

  for (const line of lines) {
    const saleItemId = uuid();
    const lineTotal = line.unitPriceKopecks * line.quantity;
    totalKopecks += lineTotal;

    let requiredByItem = [];
    if (line.isSimpleProduct) {
      const item = await getItem(line.simpleInventoryItemId);
      if (item) requiredByItem = [{ item, requiredMilli: line.quantity * 1000 }];
    } else {
      const recipe = await reqToPromise(recipeStore.index('variantId').getAll(line.variantId));
      for (const r of recipe) {
        const item = await getItem(r.inventoryItemId);
        if (item) requiredByItem.push({ item, requiredMilli: r.quantityPerPortionMilli * line.quantity });
      }
    }

    let lineCogs = 0;
    for (const { item, requiredMilli } of requiredByItem) {
      const result = deductStock(item.onHandMilli, requiredMilli, allowNegative);
      if (!result.ok) {
        shortages.push({ name: item.name, available: result.available, required: result.required });
        continue;
      }
      const updated = { ...item, onHandMilli: result.newOnHandMilli, updatedAt: now };
      itemsStore.put(updated);
      itemCache.set(item.id, updated);
      const lineCost = UnitCost.costOf(item.avgUnitCostMicro, requiredMilli);
      lineCogs += lineCost;
      deductionRows.push({
        id: uuid(), saleItemId, inventoryItemId: item.id, inventoryItemNameSnapshot: item.name,
        quantityDeductedMilli: requiredMilli, unitCostAtSaleMicro: item.avgUnitCostMicro, lineCostKopecks: lineCost,
      });
    }
    cogsKopecks += lineCogs;
    saleItemRows.push({
      id: saleItemId, saleId: idempotencyKey, productId: line.productId || null, variantId: line.variantId || null,
      productNameSnapshot: line.productName, variantNameSnapshot: line.variantName || null,
      unitPriceKopecks: line.unitPriceKopecks, quantity: line.quantity, lineTotalKopecks: lineTotal,
      lineCogsKopecks: lineCogs, returnedQuantity: 0,
    });
  }

  if (shortages.length) {
    tx.abort();
    return { status: 'insufficient_stock', shortages };
  }

  let changeGivenKopecks = null;
  if (paymentMethod === 'CASH') {
    const evalResult = evaluateCashPayment(totalKopecks, cashReceivedKopecks);
    if (!evalResult.ok) {
      tx.abort();
      return { status: 'insufficient_cash', shortfall: evalResult.shortfall };
    }
    changeGivenKopecks = evalResult.change;
  }

  const sale = {
    id: idempotencyKey, receiptNumber, createdAt: now, paymentMethod, totalKopecks,
    cashReceivedKopecks: paymentMethod === 'CASH' ? cashReceivedKopecks : null,
    changeGivenKopecks, cogsKopecks, isFullyReturned: false, isPartiallyReturned: false, isDemo,
  };
  salesStore.put(sale);
  saleItemRows.forEach((r) => tx.objectStore('saleItems').put(r));
  deductionRows.forEach((r) => tx.objectStore('saleItemDeductions').put(r));
  deductionRows.forEach((d) => {
    tx.objectStore('stockMovements').put({
      id: uuid(), inventoryItemId: d.inventoryItemId, reason: 'SALE_DEDUCTION', quantityDeltaMilli: -d.quantityDeductedMilli,
      unitCostAtMovementMicro: d.unitCostAtSaleMicro, relatedSaleId: idempotencyKey, isDemo, createdAt: now,
    });
  });
  itemCache.forEach((item) => { if (item) enqueueSync(tx, 'INVENTORY_ITEM', item.id, 'UPSERT', item); });
  enqueueSync(tx, 'SALE', idempotencyKey, 'UPSERT', { ...sale, items: saleItemRows });

  await txDone(tx);
  return { status: 'ok', sale, change: changeGivenKopecks || 0 };
}

// ---------------------------------------------------------------------------------------------
// Returns
// ---------------------------------------------------------------------------------------------
/** lines: [{ saleItemId, quantityToReturn }] */
export async function processReturn({ saleId, lines, reason, restockIngredients, now, isDemo = false }) {
  const db = await openDb();
  const tx = db.transaction(['sales', 'saleItems', 'saleItemDeductions', 'returns', 'returnItems', 'inventoryItems', 'stockMovements', 'syncQueue'], 'readwrite');
  const saleItemsStore = tx.objectStore('saleItems');
  const returnItemsStore = tx.objectStore('returnItems');

  const returnId = uuid();
  let totalRefund = 0;
  const returnItemRows = [];

  for (const line of lines) {
    if (line.quantityToReturn <= 0) continue;
    const saleItem = await reqToPromise(saleItemsStore.get(line.saleItemId));
    if (!saleItem) continue;
    const allReturnItems = await reqToPromise(returnItemsStore.index('saleItemId').getAll(line.saleItemId));
    const alreadyReturned = allReturnItems.reduce((sum, r) => sum + r.quantityReturned, 0);
    const validation = validateReturn(saleItem.quantity, alreadyReturned, line.quantityToReturn);
    if (!validation.ok) {
      tx.abort();
      return { status: 'exceeds', saleItemId: line.saleItemId, maxAllowed: validation.maxAllowed ?? 0 };
    }

    const refundedLine = saleItem.unitPriceKopecks * line.quantityToReturn;
    totalRefund += refundedLine;

    let restockedCogs = 0;
    if (restockIngredients) {
      const deductions = await reqToPromise(tx.objectStore('saleItemDeductions').index('saleItemId').getAll(line.saleItemId));
      const ratioNum = line.quantityToReturn, ratioDen = saleItem.quantity;
      for (const d of deductions) {
        const restockMilli = Math.round((d.quantityDeductedMilli * ratioNum) / ratioDen);
        if (restockMilli <= 0) continue;
        const item = await reqToPromise(tx.objectStore('inventoryItems').get(d.inventoryItemId));
        if (!item) continue;
        const updated = { ...item, onHandMilli: item.onHandMilli + restockMilli, updatedAt: now };
        tx.objectStore('inventoryItems').put(updated);
        tx.objectStore('stockMovements').put({
          id: uuid(), inventoryItemId: item.id, reason: 'RETURN_RESTOCK', quantityDeltaMilli: restockMilli,
          unitCostAtMovementMicro: item.avgUnitCostMicro, relatedReturnId: returnId, isDemo, createdAt: now,
        });
        enqueueSync(tx, 'INVENTORY_ITEM', item.id, 'UPSERT', updated);
        restockedCogs += UnitCost.costOf(d.unitCostAtSaleMicro, restockMilli);
      }
    }

    saleItemsStore.put({ ...saleItem, returnedQuantity: alreadyReturned + line.quantityToReturn });
    returnItemRows.push({ id: uuid(), returnId, saleItemId: line.saleItemId, quantityReturned: line.quantityToReturn, refundedLineKopecks: refundedLine, restockedCogsKopecks: restockedCogs });
  }

  if (!returnItemRows.length) { await txDone(tx); return { status: 'nothing' }; }

  tx.objectStore('returns').put({ id: returnId, saleId, createdAt: now, reason, refundedKopecks: totalRefund, restockPolicy: restockIngredients ? 'RESTOCK_INGREDIENTS' : 'DO_NOT_RESTOCK_INGREDIENTS', isDemo });
  returnItemRows.forEach((r) => returnItemsStore.put(r));

  const allItems = await reqToPromise(saleItemsStore.index('saleId').getAll(saleId));
  const allFully = allItems.every((it) => it.returnedQuantity >= it.quantity);
  const anyReturned = allItems.some((it) => it.returnedQuantity > 0);
  const sale = await reqToPromise(tx.objectStore('sales').get(saleId));
  if (sale) tx.objectStore('sales').put({ ...sale, isFullyReturned: allFully, isPartiallyReturned: anyReturned && !allFully });

  enqueueSync(tx, 'RETURN', returnId, 'UPSERT', { id: returnId, saleId, createdAt: now, reason, refundedKopecks: totalRefund });
  await txDone(tx);
  return { status: 'ok', returnId, refundedKopecks: totalRefund };
}

// ---------------------------------------------------------------------------------------------
// Expenses & goals
// ---------------------------------------------------------------------------------------------
export async function saveExpense({ id, date, category, amountKopecks, comment, now, isDemo = false }) {
  const db = await openDb();
  const tx = db.transaction(['expenses', 'syncQueue'], 'readwrite');
  const expenseId = id || uuid();
  const row = { id: expenseId, date, category, amountKopecks, comment: comment || null, isDemo, createdAt: now };
  tx.objectStore('expenses').put(row);
  enqueueSync(tx, 'EXPENSE', expenseId, 'UPSERT', row);
  await txDone(tx);
  return expenseId;
}

export async function deleteExpense(id) {
  const db = await openDb();
  const tx = db.transaction(['expenses', 'syncQueue'], 'readwrite');
  tx.objectStore('expenses').delete(id);
  enqueueSync(tx, 'EXPENSE', id, 'DELETE', { id });
  await txDone(tx);
}

export async function setGoal(type, periodKey, targetKopecks, now) {
  const id = `${type}|${periodKey}`;
  const row = { id, type, periodKey, targetKopecks, updatedAt: now };
  await put('goals', row);
  const db = await openDb();
  const tx = db.transaction(['syncQueue'], 'readwrite');
  enqueueSync(tx, 'GOAL', id, 'UPSERT', row);
  await txDone(tx);
}

// ---------------------------------------------------------------------------------------------
// First-run + demo seeding
// ---------------------------------------------------------------------------------------------
export async function seedDefaultCategoriesIfEmpty() {
  const existing = await getAll('categories');
  if (existing.length) return;
  const now = Date.now();
  for (const name of ['Горячий кофе', 'Холодный кофе', 'Фреш', 'Тоники', 'Прочее']) {
    await saveCategory({ id: null, name, now });
  }
}

export async function seedDemoDataIfNeeded() {
  const products = await getAll('products');
  if (products.some((p) => p.isDemo)) return;
  const now = Date.now();
  const categories = await getAll('categories');
  let tonicsId = categories.find((c) => c.name === 'Тоники')?.id;
  if (!tonicsId) tonicsId = await saveCategory({ id: null, name: 'Тоники', now });

  const mk = (name, baseUnit, min) => saveInventoryItem({ id: null, name: `${name} (демо)`, category: 'Демо-упаковка', baseUnit, minAllowedMilli: Math.round(min * 1000), now, isDemo: true });
  const cupId = await mk('Стакан 400мл', 'PIECE', 20);
  const lidId = await mk('Крышка', 'PIECE', 20);
  const juiceId = await mk('Гранатовый сок', 'MILLILITRE', 1000);
  const tonicId = await mk('Тоник', 'MILLILITRE', 1000);
  const iceId = await mk('Лёд', 'GRAM', 1000);
  const strawId = await mk('Трубочка', 'PIECE', 20);

  const stock = [[cupId, 50], [lidId, 50], [juiceId, 5000], [tonicId, 10000], [iceId, 5000], [strawId, 50]];
  for (const [id, qty] of stock) {
    await performInventoryCount({ itemId: id, countedMilli: qty * 1000, note: 'Начальный остаток (демо)', now, isDemo: true });
  }

  await saveProductWithVariants({
    productId: null, categoryId: tonicsId, name: 'Гранатовый тоник (демо)',
    description: 'Демонстрационный товар - показывает, как работает рецептура',
    isSimpleProduct: false, simpleInventoryItemId: null,
    variants: [{
      name: '400 мл', priceKopecks: 25000,
      recipe: [
        { inventoryItemId: cupId, quantityPerPortionMilli: 1000 },
        { inventoryItemId: lidId, quantityPerPortionMilli: 1000 },
        { inventoryItemId: juiceId, quantityPerPortionMilli: 100000 },
        { inventoryItemId: tonicId, quantityPerPortionMilli: 200000 },
        { inventoryItemId: iceId, quantityPerPortionMilli: 100000 },
        { inventoryItemId: strawId, quantityPerPortionMilli: 1000 },
      ],
    }],
    now, isDemo: true,
  });
}
