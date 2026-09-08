import { registerRoute, navigate } from '../router.js';
import { getAll, get, saveInventoryItem, setInventoryItemArchived, manualWriteOff, performInventoryCount } from '../db.js';
import { Qty, unitLabel } from '../money.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';
import { currentSettings } from '../state.js';

function fmtQty(milli) { return Qty.format(milli); }

registerRoute('/warehouse', async (params, container) => {
  const items = (await getAll('inventoryItems')).filter((i) => !i.isArchived).sort((a, b) => a.name.localeCompare(b.name, 'ru'));
  let query = '';
  let lowOnly = false;

  const root = fromHtml(`
    <div class="screen">
      <h2 class="screen-title">Склад</h2>
      <div class="row gap-2 mb-3">
        <button class="btn btn-outline btn-sm" id="purchases-link">Закупки</button>
        <button class="btn btn-primary btn-sm" id="new-item-btn" style="margin-left:auto;">+ Позиция</button>
      </div>
      <input class="input mb-2" id="wh-search" placeholder="Поиск по названию" />
      <div class="chip-row mb-3"><div class="chip" id="low-stock-chip">Только низкий остаток</div></div>
      <div id="wh-list" class="stack gap-2"></div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#purchases-link').addEventListener('click', () => navigate('/warehouse/purchases'));
  root.querySelector('#new-item-btn').addEventListener('click', () => navigate('/warehouse/new'));

  const listEl = root.querySelector('#wh-list');
  function render() {
    let filtered = items;
    if (query.trim()) filtered = filtered.filter((i) => i.name.toLowerCase().includes(query.trim().toLowerCase()));
    if (lowOnly) filtered = filtered.filter((i) => i.onHandMilli <= i.minAllowedMilli);
    if (!filtered.length) { listEl.innerHTML = '<div class="empty-state">Ничего не найдено</div>'; return; }
    listEl.innerHTML = filtered.map((i) => {
      const isLow = i.onHandMilli <= i.minAllowedMilli;
      return `
      <div class="card" data-id="${i.id}" style="cursor:pointer;">
        <div class="row between">
          <div><div style="font-weight:700;">${escapeHtml(i.name)}</div><div class="muted small">${escapeHtml(i.category)}</div></div>
          <div class="text-right">
            <div style="font-weight:800;${isLow ? 'color:var(--color-error);' : ''}">${fmtQty(i.onHandMilli)} ${unitLabel(i.baseUnit)}</div>
            <div class="muted small">~${(i.avgUnitCostMicro / 1e6).toFixed(2)} ₽/ед.</div>
          </div>
        </div>
      </div>`;
    }).join('');
    listEl.querySelectorAll('[data-id]').forEach((c) => c.addEventListener('click', () => navigate(`/warehouse/item/${c.dataset.id}`)));
  }
  root.querySelector('#wh-search').addEventListener('input', (e) => { query = e.target.value; render(); });
  root.querySelector('#low-stock-chip').addEventListener('click', (e) => { lowOnly = !lowOnly; e.target.classList.toggle('active', lowOnly); render(); });
  render();
});

function itemForm(item) {
  return `
    <div class="field"><label>Название</label><input class="input" id="f-name" value="${item ? escapeHtml(item.name) : ''}" /></div>
    <div class="field mt-3"><label>Категория (например, Упаковка)</label><input class="input" id="f-category" value="${item ? escapeHtml(item.category) : ''}" /></div>
    <div class="field mt-3"><label>Единица измерения</label>
      <select class="input" id="f-unit">
        <option value="PIECE" ${item?.baseUnit === 'PIECE' ? 'selected' : ''}>шт</option>
        <option value="GRAM" ${item?.baseUnit === 'GRAM' ? 'selected' : ''}>г</option>
        <option value="MILLILITRE" ${item?.baseUnit === 'MILLILITRE' ? 'selected' : ''}>мл</option>
      </select>
    </div>
    <div class="field mt-3"><label>Минимально допустимый остаток</label><input class="input" id="f-min" value="${item ? Qty.toBaseNumber(item.minAllowedMilli) : '0'}" /></div>
    <div class="field mt-3"><label>Поставщик (необязательно)</label><input class="input" id="f-supplier" value="${item?.supplier ? escapeHtml(item.supplier) : ''}" /></div>
  `;
}

registerRoute('/warehouse/new', async (params, container) => renderItemEditor(container, null));
registerRoute('/warehouse/item/:id', async (params, container) => renderItemEditor(container, params.id));

async function renderItemEditor(container, id) {
  const item = id ? await get('inventoryItems', id) : null;
  const root = fromHtml(`
    <div class="screen" style="max-width:520px;">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn"><svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg></button>
        <h2 class="screen-title" style="margin:0;">${id ? 'Складская позиция' : 'Новая позиция склада'}</h2>
      </div>
      <div class="card">${itemForm(item)}</div>
      ${item ? `
        <div class="card mt-3">
          <div>Текущий остаток: <strong>${fmtQty(item.onHandMilli)} ${unitLabel(item.baseUnit)}</strong></div>
          <div>Средняя себестоимость: <strong>${(item.avgUnitCostMicro / 1e6).toFixed(4)} ₽/ед.</strong></div>
          <div class="row gap-2 mt-3">
            <button class="btn btn-outline btn-sm" id="movements-btn">История движений</button>
            <button class="btn btn-outline btn-sm" id="count-btn">Инвентаризация</button>
          </div>
        </div>` : ''}
      <div class="row gap-2 mt-4">
        <button class="btn btn-primary" id="save-btn">Сохранить</button>
        ${id ? '<button class="btn btn-danger" id="archive-btn">В архив</button>' : ''}
      </div>
      <div id="err" class="mt-3" style="color:var(--color-error);"></div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#back-btn').addEventListener('click', () => history.back());
  if (item) {
    root.querySelector('#movements-btn').addEventListener('click', () => navigate(`/warehouse/item/${id}/movements`));
    root.querySelector('#count-btn').addEventListener('click', () => navigate(`/warehouse/item/${id}/count`));
    root.querySelector('#archive-btn').addEventListener('click', async () => {
      await setInventoryItemArchived(id, true, Date.now());
      navigate('/warehouse');
    });
  }
  root.querySelector('#save-btn').addEventListener('click', async () => {
    const name = root.querySelector('#f-name').value.trim();
    const category = root.querySelector('#f-category').value.trim();
    const baseUnit = root.querySelector('#f-unit').value;
    const minAllowed = Qty.fromString(root.querySelector('#f-min').value);
    const supplier = root.querySelector('#f-supplier').value.trim();
    if (!name || !category || minAllowed == null) {
      root.querySelector('#err').textContent = 'Заполните название, категорию и минимальный остаток';
      return;
    }
    await saveInventoryItem({ id, name, category, baseUnit, minAllowedMilli: minAllowed, supplier, now: Date.now() });
    navigate('/warehouse');
  });
}

registerRoute('/warehouse/item/:id/movements', async (params, container) => {
  const item = await get('inventoryItems', params.id);
  const movements = (await getAll('stockMovements', 'inventoryItemId', params.id)).sort((a, b) => b.createdAt - a.createdAt);
  const reasonLabel = (r) => ({
    PURCHASE_RECEIPT: 'Поступление (закупка)', SALE_DEDUCTION: 'Списание по продаже', MANUAL_WRITE_OFF: 'Ручное списание',
    INVENTORY_ADJUSTMENT: 'Инвентаризация', RETURN_RESTOCK: 'Возврат на склад', CORRECTION: 'Корректировка',
  }[r] || r);
  const root = fromHtml(`
    <div class="screen">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn"><svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg></button>
        <h2 class="screen-title" style="margin:0;">История движений: ${escapeHtml(item?.name || '')}</h2>
      </div>
      <div class="stack gap-2">${movements.map((m) => `
        <div class="row between card">
          <div><div>${reasonLabel(m.reason)}</div><div class="muted small">${new Date(m.createdAt).toLocaleString('ru-RU')}</div>${m.note ? `<div class="muted small">${escapeHtml(m.note)}</div>` : ''}</div>
          <div style="font-weight:800;color:${m.quantityDeltaMilli >= 0 ? 'var(--color-success)' : 'var(--color-error)'};">${m.quantityDeltaMilli >= 0 ? '+' : ''}${fmtQty(m.quantityDeltaMilli)}</div>
        </div>`).join('') || '<div class="empty-state">Движений нет</div>'}</div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#back-btn').addEventListener('click', () => history.back());
});

registerRoute('/warehouse/item/:id/count', async (params, container) => {
  const item = await get('inventoryItems', params.id);
  const root = fromHtml(`
    <div class="screen" style="max-width:480px;">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn"><svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg></button>
        <h2 class="screen-title" style="margin:0;">Инвентаризация</h2>
      </div>
      <p>${escapeHtml(item.name)}: числится <strong>${fmtQty(item.onHandMilli)} ${unitLabel(item.baseUnit)}</strong></p>
      <div class="field"><label>Фактическое количество</label><input class="input" id="f-counted" /></div>
      <div id="diff" class="mt-2 muted"></div>
      <div class="field mt-3"><label>Комментарий (необязательно)</label><input class="input" id="f-note" /></div>
      <button class="btn btn-primary mt-4" id="save-btn" disabled>Сохранить инвентаризацию</button>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#back-btn').addEventListener('click', () => history.back());
  const input = root.querySelector('#f-counted');
  const diffEl = root.querySelector('#diff');
  const saveBtn = root.querySelector('#save-btn');
  input.addEventListener('input', () => {
    const counted = Qty.fromString(input.value);
    if (counted == null) { diffEl.textContent = ''; saveBtn.disabled = true; return; }
    const diff = counted - item.onHandMilli;
    diffEl.textContent = `Разница: ${diff >= 0 ? '+' : ''}${fmtQty(diff)} ${unitLabel(item.baseUnit)}`;
    saveBtn.disabled = false;
  });
  saveBtn.addEventListener('click', async () => {
    const counted = Qty.fromString(input.value);
    if (counted == null) return;
    await performInventoryCount({ itemId: item.id, countedMilli: counted, note: root.querySelector('#f-note').value.trim() || null, now: Date.now() });
    navigate(`/warehouse/item/${item.id}`);
  });
});
