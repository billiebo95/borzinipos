import { registerRoute, navigate } from '../router.js';
import { getAll, getAllInRange } from '../db.js';
import { Money } from '../money.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { getCart, onCartChange, addToCart, setLineQuantity, removeCartLine, clearCart, cartTotalKopecks, cartItemCount } from '../state.js';
import { currentSettings } from '../state.js';

const POPULAR = '__popular__';

async function loadCatalog() {
  const [categories, products, variants] = await Promise.all([
    getAll('categories'), getAll('products'), getAll('variants'),
  ]);
  const activeCategories = categories.filter((c) => !c.isArchived).sort((a, b) => a.sortOrder - b.sortOrder);
  const variantsByProduct = new Map();
  variants.filter((v) => !v.isArchived).forEach((v) => {
    const list = variantsByProduct.get(v.productId) || [];
    list.push(v);
    variantsByProduct.set(v.productId, list);
  });
  const activeProducts = products
    .filter((p) => !p.isArchived && p.isAvailableForSale)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((p) => ({ product: p, variants: (variantsByProduct.get(p.id) || []).sort((a, b) => a.sortOrder - b.sortOrder) }));
  return { activeCategories, activeProducts };
}

async function computePopularIds() {
  const since = Date.now() - 30 * 86400000;
  const sales = await getAllInRange('sales', 'createdAt', since, Date.now() + 1);
  const saleIds = new Set(sales.map((s) => s.id));
  if (!saleIds.size) return new Set();
  const allItems = await getAll('saleItems');
  const tally = new Map();
  allItems.filter((i) => saleIds.has(i.saleId) && i.productId).forEach((i) => {
    tally.set(i.productId, (tally.get(i.productId) || 0) + i.quantity);
  });
  return new Set([...tally.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12).map(([id]) => id));
}

function displayPrice(variants) {
  if (!variants.length) return Money.ZERO;
  return Math.min(...variants.map((v) => v.priceKopecks));
}

function productCardHtml(entry, qtyInCart) {
  const { product, variants } = entry;
  return `
    <div class="product-card" data-product-id="${product.id}">
      ${qtyInCart ? `<div class="qty-badge">${qtyInCart}</div>` : ''}
      <div class="name">${escapeHtml(product.name)}</div>
      <div class="price">${Money.format(displayPrice(variants))}</div>
    </div>`;
}

function cartLineHtml(line) {
  return `
    <div class="cart-line" data-line-id="${line.lineId}">
      <div class="info">
        <div class="name">${escapeHtml(line.productName)}${line.variantName ? ` (${escapeHtml(line.variantName)})` : ''}</div>
        <div class="price">${Money.format(line.unitPriceKopecks)}</div>
      </div>
      <div class="qty-stepper">
        <button data-action="dec" data-line-id="${line.lineId}">−</button>
        <span>${line.quantity}</span>
        <button data-action="inc" data-line-id="${line.lineId}">+</button>
      </div>
      <button class="icon-btn" data-action="remove" data-line-id="${line.lineId}" aria-label="Удалить">
        <svg viewBox="0 0 24 24" width="18" height="18"><path d="M6 7h12l-1 13H7zM9 4h6l1 2H8zM9 10v7M12 10v7M15 10v7" stroke="currentColor" stroke-width="1.6" fill="none"/></svg>
      </button>
    </div>`;
}

function renderCartInto(container) {
  const lines = getCart();
  const total = cartTotalKopecks();
  container.innerHTML = `
    <div class="cart-panel">
      <div class="row between mb-2">
        <h3>Текущий заказ</h3>
        ${lines.length ? '<button class="btn-ghost btn btn-sm" data-action="clear-cart">Очистить</button>' : ''}
      </div>
      ${lines.length === 0
        ? '<div class="empty-state small">Добавьте товары из меню</div>'
        : `<div class="cart-lines">${lines.map(cartLineHtml).join('')}</div>
           <div class="row between mt-3" style="padding-top:8px;">
             <strong>Итого</strong><strong style="font-size:18px;">${Money.format(total)}</strong>
           </div>
           <button class="btn btn-primary btn-block mt-3" data-action="checkout">Перейти к оплате</button>`}
    </div>`;

  container.querySelectorAll('[data-action="inc"]').forEach((b) => b.addEventListener('click', () => {
    const l = lines.find((x) => x.lineId === b.dataset.lineId);
    setLineQuantity(b.dataset.lineId, l.quantity + 1);
  }));
  container.querySelectorAll('[data-action="dec"]').forEach((b) => b.addEventListener('click', () => {
    const l = lines.find((x) => x.lineId === b.dataset.lineId);
    setLineQuantity(b.dataset.lineId, l.quantity - 1);
  }));
  container.querySelectorAll('[data-action="remove"]').forEach((b) => b.addEventListener('click', () => removeCartLine(b.dataset.lineId)));
  const clearBtn = container.querySelector('[data-action="clear-cart"]');
  if (clearBtn) clearBtn.addEventListener('click', () => {
    if (confirm('Очистить текущий заказ?')) clearCart();
  });
  const checkoutBtn = container.querySelector('[data-action="checkout"]');
  if (checkoutBtn) checkoutBtn.addEventListener('click', () => navigate('/checkout'));
}

function openCartSheet() {
  const backdrop = fromHtml(`
    <div class="sheet-backdrop">
      <div class="sheet">
        <div class="sheet-handle"></div>
        <div class="cart-sheet-content"></div>
      </div>
    </div>`);
  document.body.appendChild(backdrop);
  renderCartInto(backdrop.querySelector('.cart-sheet-content'));
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) backdrop.remove(); });
  const unsub = onCartChange(() => {
    if (!document.body.contains(backdrop)) { unsub(); return; }
    renderCartInto(backdrop.querySelector('.cart-sheet-content'));
    if (getCart().length === 0) backdrop.remove();
  });
}

function openVariantPicker(product, variants) {
  const backdrop = fromHtml(`
    <div class="sheet-backdrop center">
      <div class="sheet">
        <h3 class="mb-2">${escapeHtml(product.name)}</h3>
        <div class="stack gap-2" id="variant-list"></div>
      </div>
    </div>`);
  const list = backdrop.querySelector('#variant-list');
  variants.forEach((v) => {
    const btn = fromHtml(`<button class="btn btn-outline btn-block">${escapeHtml(v.name || 'Стандарт')} — ${Money.format(v.priceKopecks)}</button>`);
    btn.addEventListener('click', () => {
      addToCart({ productId: product.id, productName: product.name, variantId: v.id, variantName: v.name, unitPriceKopecks: v.priceKopecks, isSimpleProduct: false, simpleInventoryItemId: null });
      backdrop.remove();
    });
    list.appendChild(btn);
  });
  document.body.appendChild(backdrop);
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) backdrop.remove(); });
}

registerRoute('/pos', async (params, container) => {
  const { activeCategories, activeProducts } = await loadCatalog();
  const popularIds = await computePopularIds();
  let selectedCategory = POPULAR;
  let query = '';

  const root = fromHtml(`
    <div class="screen">
      <div class="pos-layout">
        <div>
          <div class="search-input-wrap mb-3">
            <svg viewBox="0 0 24 24" width="18" height="18"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="2" fill="none"/><path d="M21 21l-4-4" stroke="currentColor" stroke-width="2"/></svg>
            <input class="input" id="pos-search" placeholder="Поиск по названию" />
          </div>
          <div class="chip-row mb-3" id="pos-chips"></div>
          <div class="product-grid" id="pos-grid"></div>
        </div>
        <div class="pos-cart-side" id="pos-cart-side"></div>
      </div>
    </div>
    `);
  container.replaceChildren(root);

  const chipsEl = root.querySelector('#pos-chips');
  const gridEl = root.querySelector('#pos-grid');
  const searchEl = root.querySelector('#pos-search');
  const cartSideEl = root.querySelector('#pos-cart-side');

  function renderChips() {
    const chips = [{ id: POPULAR, name: 'Популярное' }, ...activeCategories];
    chipsEl.innerHTML = chips.map((c) => `<div class="chip ${selectedCategory === c.id ? 'active' : ''}" data-cat="${c.id}">${escapeHtml(c.name)}</div>`).join('');
    chipsEl.querySelectorAll('.chip').forEach((chip) => chip.addEventListener('click', () => {
      selectedCategory = chip.dataset.cat;
      renderChips();
      renderGrid();
    }));
  }

  function renderGrid() {
    const cart = getCart();
    let filtered = activeProducts;
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      filtered = filtered.filter((e) => e.product.name.toLowerCase().includes(q));
    } else if (selectedCategory === POPULAR) {
      filtered = filtered.filter((e) => e.product.isPinnedPopular || popularIds.has(e.product.id));
    } else {
      filtered = filtered.filter((e) => e.product.categoryId === selectedCategory);
    }
    if (!filtered.length) {
      gridEl.innerHTML = '<div class="empty-state">Нет товаров в этой категории</div>';
      return;
    }
    gridEl.innerHTML = filtered.map((e) => {
      const qty = cart.filter((l) => l.productId === e.product.id).reduce((s, l) => s + l.quantity, 0);
      return productCardHtml(e, qty);
    }).join('');
    gridEl.querySelectorAll('.product-card').forEach((card) => card.addEventListener('click', () => {
      const entry = filtered.find((e) => e.product.id === card.dataset.productId);
      if (!entry.variants.length) return;
      if (entry.product.isSimpleProduct || entry.variants.length === 1) {
        const v = entry.variants[0];
        addToCart({ productId: entry.product.id, productName: entry.product.name, variantId: entry.product.isSimpleProduct ? null : v.id, variantName: entry.product.isSimpleProduct ? null : v.name, unitPriceKopecks: v.priceKopecks, isSimpleProduct: entry.product.isSimpleProduct, simpleInventoryItemId: entry.product.simpleInventoryItemId });
      } else {
        openVariantPicker(entry.product, entry.variants);
      }
    }));
  }

  searchEl.addEventListener('input', () => { query = searchEl.value; renderGrid(); });

  renderChips();
  renderGrid();
  renderCartInto(cartSideEl);

  // Mobile bottom bar
  let bottombar = document.querySelector('.cart-bottombar');
  function renderBottombar() {
    const count = cartItemCount();
    if (!count) { if (bottombar) bottombar.remove(); bottombar = null; return; }
    if (!bottombar) {
      bottombar = fromHtml('<div class="cart-bottombar"></div>');
      bottombar.addEventListener('click', openCartSheet);
      document.body.appendChild(bottombar);
    }
    bottombar.innerHTML = `
      <div><div class="count">${count} поз.</div><div class="total">${Money.format(cartTotalKopecks())}</div></div>
      <div class="cta">К оплате
        <svg viewBox="0 0 24 24" width="18" height="18"><path d="M9 6l6 6-6 6" stroke="white" stroke-width="2" fill="none"/></svg>
      </div>`;
  }
  renderBottombar();

  const unsub = onCartChange(() => {
    renderGrid();
    renderCartInto(cartSideEl);
    renderBottombar();
  });

  return () => { unsub(); if (bottombar) bottombar.remove(); };
});
