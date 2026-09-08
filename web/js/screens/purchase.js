import { registerRoute, navigate } from '../router.js';
import { getAll, get, saveDraftPurchase, postPurchase } from '../db.js';
import { Money, Qty, unitLabel } from '../money.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';

registerRoute('/warehouse/purchases', async (params, container) => {
  const purchases = (await getAll('purchases')).sort((a, b) => b.date - a.date || b.createdAt - a.createdAt);
  const root = fromHtml(`
    <div class="screen">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn"><svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg></button>
        <h2 class="screen-title" style="margin:0;">Закупки</h2>
        <button class="btn btn-primary btn-sm" id="new-btn" style="margin-left:auto;">+ Закупка</button>
      </div>
      <div class="stack gap-2" id="list"></div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#back-btn').addEventListener('click', () => navigate('/warehouse'));
  root.querySelector('#new-btn').addEventListener('click', () => navigate('/warehouse/purchases/new'));
  root.querySelector('#list').innerHTML = purchases.map((p) => `
    <div class="card" data-id="${p.id}" style="cursor:pointer;">
      <div class="row between">
        <div><div style="font-weight:700;">${escapeHtml(p.supplier)}</div><div class="muted small">${new Date(p.date).toLocaleDateString('ru-RU')}</div></div>
        <div class="text-right"><div style="font-weight:800;">${Money.format(p.totalKopecks)}</div><div class="muted small">${p.isPosted ? 'Проведена' : 'Черновик'}</div></div>
      </div>
    </div>`).join('') || '<div class="empty-state">Закупок пока нет</div>';
  root.querySelectorAll('[data-id]').forEach((c) => c.addEventListener('click', () => navigate(`/warehouse/purchases/${c.dataset.id}`)));
});

function lineRowHtml(line, items) {
  const options = items.map((i) => `<option value="${i.id}" ${line.inventoryItemId === i.id ? 'selected' : ''}>${escapeHtml(i.name)}</option>`).join('');
  return `
    <div class="card" data-line="${line.key}">
      <div class="row gap-2">
        <select class="input f-item">${'<option value="">Выберите позицию</option>' + options}</select>
        <button class="icon-btn f-remove"><svg viewBox="0 0 24 24" width="16" height="16"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2"/></svg></button>
      </div>
      <div class="row gap-2 mt-2">
        <input class="input f-count" placeholder="Кол-во упаковок" value="${line.packageCount ?? '1'}" />
        <input class="input f-units" placeholder="Ед. в упаковке" value="${line.unitsPerPackage ?? ''}" />
        <input class="input f-cost" placeholder="Цена упаковки, ₽" value="${line.packageCost ?? ''}" />
      </div>
    </div>`;
}

registerRoute('/warehouse/purchases/new', async (params, container) => renderPurchaseEditor(container, null));
registerRoute('/warehouse/purchases/:id', async (params, container) => renderPurchaseEditor(container, params.id));

let lineKeyCounter = 0;

async function renderPurchaseEditor(container, id) {
  const items = (await getAll('inventoryItems')).filter((i) => !i.isArchived).sort((a, b) => a.name.localeCompare(b.name, 'ru'));
  const purchase = id ? await get('purchases', id) : null;
  const existingLines = id ? (await getAll('purchaseLines', 'purchaseId', id)) : [];
  let lines = existingLines.length
    ? existingLines.map((l) => ({
        key: ++lineKeyCounter, inventoryItemId: l.inventoryItemId, packageCount: l.packageCount,
        unitsPerPackage: Qty.toBaseNumber(l.unitsPerPackageMilli), packageCost: (l.packageCostKopecks / 100).toFixed(2),
      }))
    : [{ key: ++lineKeyCounter, inventoryItemId: '', packageCount: 1, unitsPerPackage: '', packageCost: '' }];

  const root = fromHtml(`
    <div class="screen" style="max-width:640px;">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn"><svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg></button>
        <h2 class="screen-title" style="margin:0;">${id ? 'Закупка' : 'Новая закупка'}</h2>
      </div>
      <div class="field"><label>Поставщик</label><input class="input" id="f-supplier" value="${purchase ? escapeHtml(purchase.supplier) : ''}" /></div>
      <div class="field mt-3"><label>Номер накладной (необязательно)</label><input class="input" id="f-invoice" value="${purchase?.invoiceNumber ? escapeHtml(purchase.invoiceNumber) : ''}" /></div>
      <div class="field mt-3"><label>Комментарий</label><input class="input" id="f-comment" value="${purchase?.comment ? escapeHtml(purchase.comment) : ''}" /></div>
      <h3 class="mt-4 mb-2">Позиции</h3>
      <div id="lines" class="stack gap-2"></div>
      <button class="btn btn-ghost btn-sm mt-2" id="add-line">+ Строка закупки</button>
      <div class="row between mt-4"><strong>Итого</strong><strong id="total" style="font-size:18px;"></strong></div>
      ${purchase?.isPosted
        ? '<p class="muted small mt-3">Закупка уже проведена — остатки и себестоимость изменены, редактирование недоступно.</p>'
        : `<div class="row gap-2 mt-4">
             <button class="btn btn-outline" id="save-draft">Сохранить черновик</button>
             <button class="btn btn-primary" id="save-post">Провести закупку</button>
           </div>`}
      <div id="err" class="mt-3" style="color:var(--color-error);"></div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#back-btn').addEventListener('click', () => navigate('/warehouse/purchases'));
  const linesEl = root.querySelector('#lines');
  const totalEl = root.querySelector('#total');

  function readLineValues() {
    linesEl.querySelectorAll('[data-line]').forEach((card) => {
      const key = Number(card.dataset.line);
      const line = lines.find((l) => l.key === key);
      line.inventoryItemId = card.querySelector('.f-item').value;
      line.packageCount = card.querySelector('.f-count').value;
      line.unitsPerPackage = card.querySelector('.f-units').value;
      line.packageCost = card.querySelector('.f-cost').value;
    });
  }

  function computeTotal() {
    readLineValues();
    let total = 0;
    for (const l of lines) {
      const count = parseInt(l.packageCount, 10);
      const cost = Money.fromRubleString(l.packageCost);
      if (count > 0 && cost != null) total += cost * count;
    }
    totalEl.textContent = Money.format(total);
  }

  function renderLines() {
    linesEl.innerHTML = lines.map((l) => lineRowHtml(l, items)).join('');
    linesEl.querySelectorAll('[data-line]').forEach((card) => {
      const key = Number(card.dataset.line);
      card.querySelector('.f-remove').addEventListener('click', () => {
        lines = lines.filter((l) => l.key !== key);
        renderLines();
      });
      card.querySelectorAll('input,select').forEach((inp) => inp.addEventListener('input', computeTotal));
    });
    computeTotal();
  }
  renderLines();

  root.querySelector('#add-line').addEventListener('click', () => {
    readLineValues();
    lines.push({ key: ++lineKeyCounter, inventoryItemId: '', packageCount: 1, unitsPerPackage: '', packageCost: '' });
    renderLines();
  });

  function buildLineInputs() {
    readLineValues();
    const result = [];
    for (const l of lines) {
      if (!l.inventoryItemId) continue;
      const count = parseInt(l.packageCount, 10);
      const units = Qty.fromString(l.unitsPerPackage);
      const cost = Money.fromRubleString(l.packageCost);
      if (!(count > 0) || units == null || cost == null) return null;
      result.push({ inventoryItemId: l.inventoryItemId, packageCount: count, unitsPerPackageMilli: units, packageCostKopecks: cost });
    }
    return result.length ? result : null;
  }

  async function save(post) {
    const supplier = root.querySelector('#f-supplier').value.trim();
    const errEl = root.querySelector('#err');
    if (!supplier) { errEl.textContent = 'Укажите поставщика'; return; }
    const lineInputs = buildLineInputs();
    if (!lineInputs) { errEl.textContent = 'Заполните хотя бы одну строку закупки корректно'; return; }
    const now = Date.now();
    const purchaseId = await saveDraftPurchase({
      purchaseId: id, date: purchase ? purchase.date : now,
      invoiceNumber: root.querySelector('#f-invoice').value.trim(),
      supplier, comment: root.querySelector('#f-comment').value.trim(), lines: lineInputs, now,
    });
    if (post) await postPurchase(purchaseId, now);
    navigate('/warehouse/purchases');
  }

  const saveDraftBtn = root.querySelector('#save-draft');
  const savePostBtn = root.querySelector('#save-post');
  if (saveDraftBtn) saveDraftBtn.addEventListener('click', () => save(false));
  if (savePostBtn) savePostBtn.addEventListener('click', () => save(true));
}
