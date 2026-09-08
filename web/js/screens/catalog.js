// Product catalog: categories, products, size variants and their ingredient recipes.
// Ported from the Android app's CategoryManagerScreen / ProductEditorScreen.
import { registerRoute, navigate } from '../router.js';
import {
  getAll, get, put, uuid,
  saveCategory, setCategoryArchived,
  saveProductWithVariants, setProductArchived, setProductPinned,
} from '../db.js';
import { Money, Qty, unitLabel } from '../money.js';
import { UnitCost, recipeCostPerPortion, marginPercent } from '../core.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';

const backIcon = '<svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg>';
const removeIcon = '<svg viewBox="0 0 24 24" width="16" height="16"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2"/></svg>';

async function loadActiveCategories() {
  return (await getAll('categories')).filter((c) => !c.isArchived).sort((a, b) => a.sortOrder - b.sortOrder);
}

// ---------------------------------------------------------------------------------------------
// Product list
// ---------------------------------------------------------------------------------------------
registerRoute('/catalog', async (params, container) => {
  const [products, categories] = await Promise.all([getAll('products'), getAll('categories')]);
  const categoryById = new Map(categories.map((c) => [c.id, c]));
  let query = '';

  const root = fromHtml(`
    <div class="screen">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn">${backIcon}</button>
        <h2 class="screen-title" style="margin:0;">Товары</h2>
        <button class="btn btn-outline btn-sm" id="categories-btn" style="margin-left:auto;">Категории</button>
        <button class="btn btn-primary btn-sm" id="new-btn">+ Товар</button>
      </div>
      <input class="input mb-3" id="search" placeholder="Поиск по названию" />
      <div id="list" class="stack gap-2"></div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#back-btn').addEventListener('click', () => navigate('/settings'));
  root.querySelector('#categories-btn').addEventListener('click', () => navigate('/catalog/categories'));
  root.querySelector('#new-btn').addEventListener('click', () => navigate('/catalog/new'));

  const listEl = root.querySelector('#list');
  function render() {
    let filtered = products.filter((p) => !p.isArchived);
    if (query.trim()) filtered = filtered.filter((p) => p.name.toLowerCase().includes(query.trim().toLowerCase()));
    filtered = filtered.sort((a, b) => a.sortOrder - b.sortOrder);
    if (!filtered.length) { listEl.innerHTML = '<div class="empty-state">Товаров не найдено</div>'; return; }
    listEl.innerHTML = filtered.map((p) => `
      <div class="card" data-id="${p.id}" style="cursor:pointer;">
        <div class="row between">
          <div>
            <div style="font-weight:700;">${escapeHtml(p.name)}${p.isPinnedPopular ? ' ★' : ''}</div>
            <div class="muted small">${escapeHtml(categoryById.get(p.categoryId)?.name || '—')}</div>
          </div>
          <div>
            ${!p.isAvailableForSale ? '<span class="badge badge-muted">Скрыт</span>' : ''}
            ${p.isSimpleProduct ? '<span class="badge badge-muted">Простой</span>' : ''}
          </div>
        </div>
      </div>`).join('');
    listEl.querySelectorAll('[data-id]').forEach((c) => c.addEventListener('click', () => navigate(`/catalog/${c.dataset.id}`)));
  }
  root.querySelector('#search').addEventListener('input', (e) => { query = e.target.value; render(); });
  render();
});

// ---------------------------------------------------------------------------------------------
// Category manager
// ---------------------------------------------------------------------------------------------
registerRoute('/catalog/categories', async (params, container) => {
  async function load() { return (await getAll('categories')).filter((c) => !c.isArchived).sort((a, b) => a.sortOrder - b.sortOrder); }

  async function render() {
    const categories = await load();
    const root = fromHtml(`
      <div class="screen" style="max-width:480px;">
        <div class="row gap-3 mb-4">
          <button class="icon-btn" id="back-btn">${backIcon}</button>
          <h2 class="screen-title" style="margin:0;">Категории</h2>
        </div>
        <div id="list" class="stack gap-2 mb-4"></div>
        <div class="row gap-2">
          <input class="input" id="new-name" placeholder="Новая категория" />
          <button class="btn btn-primary" id="add-btn">Добавить</button>
        </div>
      </div>`);
    container.replaceChildren(root);
    root.querySelector('#back-btn').addEventListener('click', () => navigate('/catalog'));
    root.querySelector('#list').innerHTML = categories.map((c) => `
      <div class="row between card" data-id="${c.id}">
        <span>${escapeHtml(c.name)}</span>
        <button class="icon-btn" data-archive="${c.id}">${removeIcon}</button>
      </div>`).join('') || '<div class="empty-state">Категорий пока нет</div>';
    root.querySelectorAll('[data-archive]').forEach((btn) => btn.addEventListener('click', async () => {
      if (!confirm('Отправить категорию в архив? Товары останутся, но категория перестанет отображаться.')) return;
      await setCategoryArchived(btn.dataset.archive, true, Date.now());
      render();
    }));
    root.querySelector('#add-btn').addEventListener('click', async () => {
      const input = root.querySelector('#new-name');
      const name = input.value.trim();
      if (!name) return;
      await saveCategory({ id: null, name, now: Date.now() });
      render();
    });
  }
  await render();
});

// ---------------------------------------------------------------------------------------------
// Product editor
// ---------------------------------------------------------------------------------------------
let keyCounter = 0;

function recipeLineHtml(line, items) {
  const options = items.map((i) => `<option value="${i.id}" ${line.inventoryItemId === i.id ? 'selected' : ''}>${escapeHtml(i.name)}</option>`).join('');
  return `
    <div class="row gap-2 mt-2" data-recipe-line="${line.key}">
      <select class="input f-item">${'<option value="">Ингредиент</option>' + options}</select>
      <input class="input f-qty" placeholder="Кол-во на порцию" value="${line.qtyText ?? ''}" style="max-width:140px;" />
      <button class="icon-btn f-remove">${removeIcon}</button>
    </div>`;
}

function variantCardHtml(variant, items) {
  return `
    <div class="card" data-variant="${variant.key}">
      <div class="row gap-2">
        <input class="input f-name" placeholder="Название размера (напр. 400 мл)" value="${escapeHtml(variant.name || '')}" />
        <input class="input f-price" placeholder="Цена, ₽" value="${variant.priceText ?? ''}" style="max-width:120px;" />
        <button class="icon-btn f-remove-variant">${removeIcon}</button>
      </div>
      <div class="muted small mt-2">Рецептура</div>
      <div class="recipe-lines"></div>
      <button class="btn btn-ghost btn-sm mt-2 f-add-line">+ Ингредиент</button>
      <div class="row between mt-2" style="border-top:1px solid var(--color-border);padding-top:8px;">
        <span class="muted small">Себестоимость / маржа</span>
        <span class="f-cost-preview small" style="font-weight:700;"></span>
      </div>
    </div>`;
}

registerRoute('/catalog/new', async (params, container) => renderProductEditor(container, null));
registerRoute('/catalog/:id', async (params, container) => renderProductEditor(container, params.id));

async function renderProductEditor(container, id) {
  const [categories, items] = await Promise.all([
    loadActiveCategories(),
    (async () => (await getAll('inventoryItems')).filter((i) => !i.isArchived).sort((a, b) => a.name.localeCompare(b.name, 'ru')))(),
  ]);
  const product = id ? await get('products', id) : null;
  const existingVariants = id ? (await getAll('variants', 'productId', id)).filter((v) => !v.isArchived).sort((a, b) => a.sortOrder - b.sortOrder) : [];
  const existingRecipeByVariant = new Map();
  for (const v of existingVariants) {
    existingRecipeByVariant.set(v.id, (await getAll('recipeLines', 'variantId', v.id)).sort((a, b) => a.sortOrder - b.sortOrder));
  }

  let isSimpleProduct = product ? product.isSimpleProduct : false;
  let simpleInventoryItemId = product ? product.simpleInventoryItemId : '';

  let variants = existingVariants.length
    ? existingVariants.map((v) => ({
        key: ++keyCounter, id: v.id, name: v.name || '', priceText: (v.priceKopecks / 100).toFixed(2),
        recipe: (existingRecipeByVariant.get(v.id) || []).map((r) => ({
          key: ++keyCounter, inventoryItemId: r.inventoryItemId, qtyText: Qty.format(r.quantityPerPortionMilli),
        })),
      }))
    : [{ key: ++keyCounter, id: null, name: '', priceText: '', recipe: [] }];

  const root = fromHtml(`
    <div class="screen" style="max-width:640px;">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn">${backIcon}</button>
        <h2 class="screen-title" style="margin:0;">${id ? 'Товар' : 'Новый товар'}</h2>
      </div>
      <div class="card">
        <div class="field"><label>Название</label><input class="input" id="f-name" value="${product ? escapeHtml(product.name) : ''}" /></div>
        <div class="field mt-3"><label>Категория</label>
          <select class="input" id="f-category">${categories.map((c) => `<option value="${c.id}" ${product?.categoryId === c.id ? 'selected' : ''}>${escapeHtml(c.name)}</option>`).join('')}</select>
        </div>
        <div class="field mt-3"><label>Описание (необязательно)</label><input class="input" id="f-description" value="${product?.description ? escapeHtml(product.description) : ''}" /></div>
        <div class="row between mt-3">
          <span>Простой товар (без рецептуры, списывается со склада напрямую)</span>
          <label class="switch"><input type="checkbox" id="f-simple" ${isSimpleProduct ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
        </div>
      </div>

      <div id="simple-section" class="card mt-3 ${isSimpleProduct ? '' : 'hidden'}">
        <div class="field"><label>Складская позиция</label>
          <select class="input" id="f-simple-item">${'<option value="">Выберите позицию</option>' + items.map((i) => `<option value="${i.id}" ${simpleInventoryItemId === i.id ? 'selected' : ''}>${escapeHtml(i.name)} (${unitLabel(i.baseUnit)})</option>`).join('')}</select>
        </div>
        <div class="field mt-3"><label>Цена, ₽</label><input class="input" id="f-simple-price" value="${variants[0]?.priceText ?? ''}" /></div>
      </div>

      <div id="variants-section" class="mt-3 ${isSimpleProduct ? 'hidden' : ''}">
        <h3 class="mb-2">Размеры и рецептура</h3>
        <div id="variants-list" class="stack gap-2"></div>
        <button class="btn btn-ghost btn-sm mt-2" id="add-variant">+ Размер</button>
      </div>

      ${product ? `
        <div class="card mt-3">
          <div class="row between"><span>Показывать в кассе</span>
            <label class="switch"><input type="checkbox" id="f-available" ${product.isAvailableForSale ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
          </div>
          <div class="row between mt-3"><span>Закрепить в «Популярное»</span>
            <label class="switch"><input type="checkbox" id="f-pinned" ${product.isPinnedPopular ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
          </div>
        </div>` : ''}

      <div class="row gap-2 mt-4">
        <button class="btn btn-primary" id="save-btn">Сохранить</button>
        ${id ? '<button class="btn btn-danger" id="archive-btn">В архив</button>' : ''}
      </div>
      <div id="err" class="mt-3" style="color:var(--color-accent-text);"></div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#back-btn').addEventListener('click', () => navigate('/catalog'));

  const simpleSection = root.querySelector('#simple-section');
  const variantsSection = root.querySelector('#variants-section');
  const variantsList = root.querySelector('#variants-list');

  root.querySelector('#f-simple').addEventListener('change', (e) => {
    isSimpleProduct = e.target.checked;
    simpleSection.classList.toggle('hidden', !isSimpleProduct);
    variantsSection.classList.toggle('hidden', isSimpleProduct);
  });

  const availableToggle = root.querySelector('#f-available');
  const pinnedToggle = root.querySelector('#f-pinned');
  if (availableToggle) availableToggle.addEventListener('change', async (e) => {
    const existing = await get('products', id);
    if (existing) await put('products', { ...existing, isAvailableForSale: e.target.checked, updatedAt: Date.now() });
  });
  if (pinnedToggle) pinnedToggle.addEventListener('change', (e) => setProductPinned(id, e.target.checked, Date.now()));

  function readVariantValues() {
    variantsList.querySelectorAll('[data-variant]').forEach((card) => {
      const key = Number(card.dataset.variant);
      const variant = variants.find((v) => v.key === key);
      variant.name = card.querySelector('.f-name').value;
      variant.priceText = card.querySelector('.f-price').value;
      card.querySelectorAll('[data-recipe-line]').forEach((row) => {
        const rkey = Number(row.dataset.recipeLine);
        const line = variant.recipe.find((r) => r.key === rkey);
        line.inventoryItemId = row.querySelector('.f-item').value;
        line.qtyText = row.querySelector('.f-qty').value;
      });
    });
  }

  function itemBaseUnit(itemId) {
    return items.find((i) => i.id === itemId)?.baseUnit;
  }

  function costPreviewFor(variant) {
    const lines = variant.recipe
      .filter((r) => r.inventoryItemId)
      .map((r) => {
        const item = items.find((i) => i.id === r.inventoryItemId);
        const qty = Qty.fromString(r.qtyText);
        if (!item || qty == null) return null;
        return { quantityPerPortionMilli: qty, unitCostMicro: item.avgUnitCostMicro };
      })
      .filter(Boolean);
    if (!lines.length) return '—';
    const cost = recipeCostPerPortion(lines);
    const price = Money.fromRubleString(variant.priceText);
    const margin = price != null ? marginPercent(price, cost) : null;
    return Money.format(cost) + (margin != null ? ` · маржа ${margin}%` : '');
  }

  function renderVariants() {
    variantsList.innerHTML = variants.map((v) => variantCardHtml(v, items)).join('');
    variantsList.querySelectorAll('[data-variant]').forEach((card) => {
      const key = Number(card.dataset.variant);
      const variant = variants.find((v) => v.key === key);
      const recipeEl = card.querySelector('.recipe-lines');
      function renderRecipe() {
        recipeEl.innerHTML = variant.recipe.map((r) => recipeLineHtml(r, items)).join('');
        recipeEl.querySelectorAll('[data-recipe-line]').forEach((row) => {
          const rkey = Number(row.dataset.recipeLine);
          row.querySelector('.f-remove').addEventListener('click', () => {
            readVariantValues();
            variant.recipe = variant.recipe.filter((r) => r.key !== rkey);
            renderRecipe();
            updatePreview();
          });
          row.querySelectorAll('input,select').forEach((inp) => inp.addEventListener('input', () => { readVariantValues(); updatePreview(); }));
        });
      }
      function updatePreview() {
        card.querySelector('.f-cost-preview').textContent = costPreviewFor(variant);
      }
      renderRecipe();
      updatePreview();
      card.querySelector('.f-add-line').addEventListener('click', () => {
        readVariantValues();
        variant.recipe.push({ key: ++keyCounter, inventoryItemId: '', qtyText: '' });
        renderRecipe();
        updatePreview();
      });
      card.querySelector('.f-remove-variant').addEventListener('click', () => {
        readVariantValues();
        variants = variants.filter((v) => v.key !== key);
        renderVariants();
      });
      card.querySelector('.f-name').addEventListener('input', () => readVariantValues());
      card.querySelector('.f-price').addEventListener('input', () => { readVariantValues(); updatePreview(); });
    });
  }
  renderVariants();

  root.querySelector('#add-variant').addEventListener('click', () => {
    readVariantValues();
    variants.push({ key: ++keyCounter, id: null, name: '', priceText: '', recipe: [] });
    renderVariants();
  });

  if (id) {
    root.querySelector('#archive-btn').addEventListener('click', async () => {
      if (!confirm('Отправить товар в архив?')) return;
      await setProductArchived(id, true, Date.now());
      navigate('/catalog');
    });
  }

  root.querySelector('#save-btn').addEventListener('click', async () => {
    const errEl = root.querySelector('#err');
    const name = root.querySelector('#f-name').value.trim();
    const categoryId = root.querySelector('#f-category').value;
    const description = root.querySelector('#f-description').value.trim();
    if (!name || !categoryId) { errEl.textContent = 'Укажите название и категорию'; return; }

    let variantInputs;
    let simpleItemId = null;
    if (isSimpleProduct) {
      simpleItemId = root.querySelector('#f-simple-item').value;
      const price = Money.fromRubleString(root.querySelector('#f-simple-price').value);
      if (!simpleItemId || price == null) { errEl.textContent = 'Выберите складскую позицию и укажите цену'; return; }
      variantInputs = [{ id: variants[0]?.id || null, name: null, priceKopecks: price, recipe: [] }];
    } else {
      readVariantValues();
      variantInputs = [];
      for (const v of variants) {
        const price = Money.fromRubleString(v.priceText);
        if (price == null) { errEl.textContent = 'Укажите корректную цену для каждого размера'; return; }
        const recipe = [];
        for (const r of v.recipe) {
          if (!r.inventoryItemId) continue;
          const qty = Qty.fromString(r.qtyText);
          if (qty == null || qty <= 0) { errEl.textContent = 'Укажите корректное количество для каждого ингредиента'; return; }
          recipe.push({ inventoryItemId: r.inventoryItemId, quantityPerPortionMilli: qty });
        }
        if (!recipe.length) { errEl.textContent = 'Добавьте хотя бы один ингредиент в рецептуру'; return; }
        variantInputs.push({ id: v.id, name: v.name.trim() || null, priceKopecks: price, recipe });
      }
      if (!variantInputs.length) { errEl.textContent = 'Добавьте хотя бы один размер'; return; }
    }

    await saveProductWithVariants({
      productId: id, categoryId, name, description,
      isSimpleProduct, simpleInventoryItemId: isSimpleProduct ? simpleItemId : null,
      variants: variantInputs, now: Date.now(),
    });
    showToast('Товар сохранён');
    navigate('/catalog');
  });
}
