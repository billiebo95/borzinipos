import { registerRoute, navigate } from '../router.js';
import { getAll, getAllInRange } from '../db.js';
import { Money } from '../money.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { getCart, onCartChange, addToCart, setLineQuantity, removeCartLine, clearCart, cartTotalKopecks, cartItemCount } from '../state.js';

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
        <svg viewBox="0 0 24 24" width="17" height="17"><path d="M6 7h12l-1 13H7zM9 4h6l1 2H8zM9 10v7M12 10v7M15 10v7" stroke="currentColor" stroke-width="1.6" fill="none"/></svg>
      </button>
    </div>`;
}

function renderCartLinesInto(container) {
  const lines = getCart();
  container.innerHTML = lines.length
    ? `<div class="stack gap-2">${lines.map(cartLineHtml).join('')}</div>`
    : '<div class="empty-state">Добавьте товары из меню</div>';
  container.querySelectorAll('[data-action="inc"]').forEach((b) => b.addEventListener('click', () => {
    const l = lines.find((x) => x.lineId === b.dataset.lineId);
    setLineQuantity(b.dataset.lineId, l.quantity + 1);
  }));
  container.querySelectorAll('[data-action="dec"]').forEach((b) => b.addEventListener('click', () => {
    const l = lines.find((x) => x.lineId === b.dataset.lineId);
    setLineQuantity(b.dataset.lineId, l.quantity - 1);
  }));
  container.querySelectorAll('[data-action="remove"]').forEach((b) => b.addEventListener('click', () => removeCartLine(b.dataset.lineId)));
}

// Desktop side-panel cart (outside the mockup's mobile-only scope, kept from the existing app).
function renderCartSideInto(container) {
  const lines = getCart();
  const total = cartTotalKopecks();
  container.innerHTML = `
    <div class="row between mb-2">
      <h3 style="font-family:var(--font);font-weight:800;">Текущий заказ</h3>
      ${lines.length ? '<button class="btn btn-ghost" data-action="clear-cart">Очистить</button>' : ''}
    </div>
    <div class="cart-lines-side"></div>
    ${lines.length ? `
      <div class="row between mt-3" style="padding-top:8px;border-top:2px solid var(--color-divider);">
        <strong>Итого</strong><strong style="font-size:18px;">${Money.format(total)}</strong>
      </div>
      <button class="btn btn-primary btn-block mt-3" data-action="checkout">Перейти к оплате</button>` : ''}`;
  renderCartLinesInto(container.querySelector('.cart-lines-side'));
  const clearBtn = container.querySelector('[data-action="clear-cart"]');
  if (clearBtn) clearBtn.addEventListener('click', () => { if (confirm('Очистить текущий заказ?')) clearCart(); });
  const checkoutBtn = container.querySelector('[data-action="checkout"]');
  if (checkoutBtn) checkoutBtn.addEventListener('click', () => navigate('/checkout'));
}

function openVariantPicker(product, variants) {
  const backdrop = fromHtml(`
    <div class="sheet-backdrop">
      <div class="sheet">
        <h3 class="mb-3" style="font-family:var(--font);font-weight:800;font-size:16px;">${escapeHtml(product.name)}</h3>
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
          <input class="input mb-3" id="pos-search" placeholder="Поиск по названию" />
          <div class="chip-row mb-3" id="pos-chips"></div>
          <div class="product-grid" id="pos-grid"></div>
          <div class="empty-state hidden" id="pos-empty">Нет товаров в этой категории</div>
        </div>
        <div class="pos-cart-side" id="pos-cart-side"></div>
      </div>
    </div>
    `);
  container.replaceChildren(root);

  const chipsEl = root.querySelector('#pos-chips');
  const gridEl = root.querySelector('#pos-grid');
  const emptyEl = root.querySelector('#pos-empty');
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
    gridEl.classList.toggle('hidden', filtered.length === 0);
    emptyEl.classList.toggle('hidden', filtered.length > 0);
    if (!filtered.length) return;
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
  renderCartSideInto(cartSideEl);

  // Mobile bottom bar — tapping it opens the full-screen cart overlay (/cart).
  let bottombar = document.querySelector('.cart-bottombar');
  function renderBottombar() {
    const count = cartItemCount();
    if (!count) { if (bottombar) bottombar.remove(); bottombar = null; return; }
    if (!bottombar) {
      bottombar = fromHtml('<div class="cart-bottombar"></div>');
      bottombar.addEventListener('click', () => navigate('/cart'));
      document.body.appendChild(bottombar);
    }
    bottombar.innerHTML = `
      <div><div class="count">${count} поз.</div><div class="total">${Money.format(cartTotalKopecks())}</div></div>
      <div class="cta">К оплате
        <svg viewBox="0 0 24 24" width="18" height="18"><path d="M9 6l6 6-6 6" stroke="#fff" stroke-width="2.4" fill="none"/></svg>
      </div>`;
  }
  renderBottombar();

  const unsub = onCartChange(() => {
    renderGrid();
    renderCartSideInto(cartSideEl);
    renderBottombar();
  });

  return () => { unsub(); if (bottombar) bottombar.remove(); };
});

// Full-screen cart overlay — mirrors the design's isCartView (no topbar/bottom nav while it's open).
registerRoute('/cart', async (params, container) => {
  const root = fromHtml(`
    <div class="overlay-screen">
      <div class="overlay-header">
        <button class="back-btn" id="close-cart">
          <svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2.2" fill="none"/></svg>
        </button>
        <div class="overlay-title">Текущий заказ</div>
        <button class="btn btn-ghost hidden" id="clear-cart">Очистить</button>
      </div>
      <div class="overlay-body" id="cart-body"></div>
      <div class="overlay-footer">
        <div class="row between mb-3" style="font-family:var(--font);font-weight:700;font-size:14px;">
          <span>Итого</span><span style="font-size:19px;font-weight:800;" id="cart-total"></span>
        </div>
        <button class="btn btn-primary btn-block" id="go-checkout">Перейти к оплате</button>
      </div>
    </div>`);
  container.replaceChildren(root);

  const bodyEl = root.querySelector('#cart-body');
  const clearBtn = root.querySelector('#clear-cart');
  const totalEl = root.querySelector('#cart-total');
  const goCheckoutBtn = root.querySelector('#go-checkout');

  function render() {
    const lines = getCart();
    renderCartLinesInto(bodyEl);
    clearBtn.classList.toggle('hidden', lines.length === 0);
    totalEl.textContent = Money.format(cartTotalKopecks());
    goCheckoutBtn.disabled = lines.length === 0;
    if (lines.length === 0 && document.body.contains(root)) navigate('/pos');
  }

  root.querySelector('#close-cart').addEventListener('click', () => navigate('/pos'));
  clearBtn.addEventListener('click', () => { if (confirm('Очистить текущий заказ?')) clearCart(); });
  goCheckoutBtn.addEventListener('click', () => { if (getCart().length) navigate('/checkout'); });

  render();
  const unsub = onCartChange(render);
  return () => unsub();
});
