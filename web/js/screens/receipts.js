import { registerRoute, navigate } from '../router.js';
import { getAllInRange, getAll, get, processReturn } from '../db.js';
import { Money } from '../money.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';
import { currentSettings } from '../state.js';
import { resolvePeriod } from '../core.js';

function fmtDateTime(ms) {
  return new Date(ms).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

registerRoute('/receipts', async (params, container) => {
  const settings = currentSettings();
  const { startMs, endMs } = resolvePeriod('THIS_MONTH', settings.timezoneId);
  const allSales = (await getAllInRange('sales', 'createdAt', startMs, endMs))
    .filter((s) => settings.demoModeEnabled || !s.isDemo)
    .sort((a, b) => b.createdAt - a.createdAt);

  let query = '';
  let paymentFilter = null;

  const root = fromHtml(`
    <div class="screen">
      <h2 class="screen-title">Чеки (текущий месяц)</h2>
      <input class="input mb-3" id="receipt-search" placeholder="Поиск по номеру чека" />
      <div class="row gap-2 mb-3">
        <div class="chip active" data-filter="">Все</div>
        <div class="chip" data-filter="CASH">Наличные</div>
        <div class="chip" data-filter="CASHLESS">Безналичные</div>
      </div>
      <div id="receipt-list" class="stack"></div>
    </div>
  `);
  container.replaceChildren(root);
  const listEl = root.querySelector('#receipt-list');

  function render() {
    let filtered = allSales;
    if (query.trim()) filtered = filtered.filter((s) => String(s.receiptNumber).includes(query.trim()));
    if (paymentFilter) filtered = filtered.filter((s) => s.paymentMethod === paymentFilter);
    if (!filtered.length) { listEl.innerHTML = '<div class="empty-state">Чеков не найдено</div>'; return; }
    listEl.innerHTML = filtered.map((s) => `
      <div class="card" data-id="${s.id}" style="cursor:pointer;">
        <div class="row between">
          <div>
            <div style="font-weight:700;">Чек №${s.receiptNumber}</div>
            <div class="muted small">${fmtDateTime(s.createdAt)}</div>
          </div>
          <div class="text-right">
            <div style="font-weight:800;">${Money.format(s.totalKopecks)}</div>
            ${s.isFullyReturned ? '<span class="badge badge-error">Возврат</span>' : s.isPartiallyReturned ? '<span class="badge badge-error">Частичный возврат</span>' : ''}
          </div>
        </div>
      </div>`).join('');
    listEl.querySelectorAll('[data-id]').forEach((c) => c.addEventListener('click', () => navigate(`/receipts/${c.dataset.id}`)));
  }

  root.querySelector('#receipt-search').addEventListener('input', (e) => { query = e.target.value; render(); });
  root.querySelectorAll('[data-filter]').forEach((chip) => chip.addEventListener('click', () => {
    paymentFilter = chip.dataset.filter || null;
    root.querySelectorAll('[data-filter]').forEach((c) => c.classList.toggle('active', c === chip));
    render();
  }));

  render();
});

function returnDialog(saleItems, onConfirm) {
  const quantities = new Map(saleItems.map((i) => [i.id, 0]));
  const backdrop = fromHtml(`
    <div class="sheet-backdrop">
      <div class="sheet">
        <div class="sheet-handle"></div>
        <h3 class="mb-3">Возврат</h3>
        <div id="return-lines" class="stack gap-2"></div>
        <div class="field mt-3"><label>Причина возврата</label><input class="input" id="return-reason" /></div>
        <div class="row between mt-3">
          <span>Вернуть ингредиенты на склад</span>
          <label class="switch"><input type="checkbox" id="restock-toggle" /><span class="track"><span class="thumb"></span></span></label>
        </div>
        <p class="muted small mt-2">По умолчанию ингредиенты приготовленного напитка на склад не возвращаются.</p>
        <button class="btn btn-primary btn-block mt-4" id="confirm-return">Оформить возврат</button>
      </div>
    </div>`);
  const linesEl = backdrop.querySelector('#return-lines');
  saleItems.forEach((item) => {
    const maxReturnable = item.quantity - item.returnedQuantity;
    if (maxReturnable <= 0) return;
    const row = fromHtml(`
      <div class="row between">
        <span class="flex-1">${escapeHtml(item.productNameSnapshot)}</span>
        <div class="qty-stepper">
          <button data-dec>−</button><span data-qty>0</span><button data-inc>+</button>
        </div>
      </div>`);
    const qtySpan = row.querySelector('[data-qty]');
    row.querySelector('[data-dec]').addEventListener('click', () => {
      const v = Math.max(0, quantities.get(item.id) - 1);
      quantities.set(item.id, v); qtySpan.textContent = v;
    });
    row.querySelector('[data-inc]').addEventListener('click', () => {
      const v = Math.min(maxReturnable, quantities.get(item.id) + 1);
      quantities.set(item.id, v); qtySpan.textContent = v;
    });
    linesEl.appendChild(row);
  });
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) backdrop.remove(); });
  backdrop.querySelector('#confirm-return').addEventListener('click', () => {
    const reason = backdrop.querySelector('#return-reason').value.trim() || 'Не указана';
    const restock = backdrop.querySelector('#restock-toggle').checked;
    const lines = [...quantities.entries()].filter(([, q]) => q > 0).map(([saleItemId, quantityToReturn]) => ({ saleItemId, quantityToReturn }));
    backdrop.remove();
    onConfirm(lines, reason, restock);
  });
  document.body.appendChild(backdrop);
}

registerRoute('/receipts/:id', async (params, container) => {
  async function load() {
    const sale = await get('sales', params.id);
    const items = (await getAll('saleItems', 'saleId', params.id));
    return { sale, items };
  }

  async function render() {
    const { sale, items } = await load();
    if (!sale) { container.innerHTML = '<div class="empty-state">Чек не найден</div>'; return; }
    const root = fromHtml(`
      <div class="screen" style="max-width:560px;">
        <div class="row gap-3 mb-4">
          <button class="icon-btn" id="back-btn"><svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg></button>
          <h2 class="screen-title" style="margin:0;">Чек №${sale.receiptNumber}</h2>
        </div>
        <div class="card">
          <div class="muted mb-2">${sale.paymentMethod === 'CASH' ? 'Наличные' : 'Безналичные'}</div>
          <div id="items-list" class="stack gap-2"></div>
          <div class="row between mt-3" style="border-top:1px solid var(--color-border);padding-top:8px;">
            <span class="muted">Себестоимость</span><span>${Money.format(sale.cogsKopecks)}</span>
          </div>
          <div class="row between mt-2">
            <strong>Итого</strong><strong style="font-size:18px;">${Money.format(sale.totalKopecks)}</strong>
          </div>
        </div>
        <button class="btn btn-primary mt-4" id="return-btn">Оформить возврат</button>
      </div>
    `);
    container.replaceChildren(root);
    root.querySelector('#back-btn').addEventListener('click', () => navigate('/receipts'));
    root.querySelector('#items-list').innerHTML = items.map((i) => `
      <div class="row between">
        <div>
          <div>${escapeHtml(i.productNameSnapshot)}${i.variantNameSnapshot ? ` (${escapeHtml(i.variantNameSnapshot)})` : ''} × ${i.quantity}</div>
          ${i.returnedQuantity > 0 ? `<div class="badge badge-error">Возвращено: ${i.returnedQuantity}</div>` : ''}
        </div>
        <div>${Money.format(i.lineTotalKopecks)}</div>
      </div>`).join('');
    root.querySelector('#return-btn').addEventListener('click', () => {
      returnDialog(items, async (lines, reason, restock) => {
        if (!lines.length) { showToast('Не выбрано ни одной позиции для возврата'); return; }
        const settings = currentSettings();
        const result = await processReturn({ saleId: sale.id, lines, reason, restockIngredients: restock, now: Date.now(), isDemo: sale.isDemo });
        if (result.status === 'ok') showToast('Возврат оформлен: ' + Money.format(result.refundedKopecks));
        else if (result.status === 'exceeds') showToast(`Нельзя вернуть больше, чем было продано (максимум ${result.maxAllowed})`);
        else showToast('Не выбрано ни одной позиции для возврата');
        render();
      });
    });
  }

  await render();
});
