import { registerRoute, navigate } from '../router.js';
import { getAllInRange, getAll, get, processReturn } from '../db.js';
import { Money } from '../money.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';
import { currentSettings } from '../state.js';
import { resolvePeriod } from '../core.js';

function fmtDateTime(ms) {
  return new Date(ms).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}

registerRoute('/receipts', async (params, container) => {
  const settings = currentSettings();
  const { startMs, endMs } = resolvePeriod('THIS_MONTH', settings.timezoneId);
  const allSales = (await getAllInRange('sales', 'createdAt', startMs, endMs))
    .filter((s) => settings.demoModeEnabled || !s.isDemo)
    .sort((a, b) => b.createdAt - a.createdAt);
  const refundedBySale = new Map();
  (await getAll('returns')).forEach((r) => refundedBySale.set(r.saleId, (refundedBySale.get(r.saleId) || 0) + r.refundedKopecks));

  let query = '';
  let paymentFilter = null;

  const root = fromHtml(`
    <div class="screen stack gap-3" style="max-width:600px;">
      <button class="inline-back" id="back-btn">← Настройки</button>
      <div class="screen-title" style="margin:0;">Чеки (текущий месяц)</div>
      <input class="input" id="receipt-search" placeholder="Поиск по номеру чека" />
      <div class="chip-row">
        <div class="chip active" data-filter="">Все</div>
        <div class="chip" data-filter="CASH">Наличные</div>
        <div class="chip" data-filter="CASHLESS">Безналичные</div>
      </div>
      <div id="receipt-list"></div>
    </div>
  `);
  container.replaceChildren(root);
  root.querySelector('#back-btn').addEventListener('click', () => navigate('/settings'));
  const listEl = root.querySelector('#receipt-list');

  function render() {
    let filtered = allSales;
    if (query.trim()) filtered = filtered.filter((s) => String(s.receiptNumber).includes(query.trim()));
    if (paymentFilter) filtered = filtered.filter((s) => s.paymentMethod === paymentFilter);
    if (!filtered.length) { listEl.innerHTML = '<div class="empty-state">Чеков не найдено</div>'; return; }
    listEl.innerHTML = filtered.map((s) => `
      <div class="list-row" data-id="${s.id}">
        <div>
          <div class="name">Чек №${s.receiptNumber}</div>
          <div class="sub">${fmtDateTime(s.createdAt)}</div>
        </div>
        <div class="text-right">
          <div class="name">${Money.format(s.totalKopecks - (refundedBySale.get(s.id) || 0))}</div>
          ${s.isFullyReturned ? '<div class="badge badge-danger">Возврат</div>' : s.isPartiallyReturned ? '<div class="badge badge-danger">Частичный возврат</div>' : ''}
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

function returnFormHtml() {
  return `
    <div class="sub-card mt-3" id="return-form">
      <div class="sub-card-heading">Возврат</div>
      <div id="return-lines" class="stack gap-2"></div>
      <div class="field mt-2"><label>Причина возврата</label><input class="input" id="return-reason" /></div>
      <button class="btn btn-outline btn-block mt-2" id="restock-toggle" style="text-align:left;">☐ Вернуть ингредиенты на склад</button>
      <p class="muted small">По умолчанию ингредиенты приготовленного напитка на склад не возвращаются.</p>
      <button class="btn btn-primary btn-block" id="confirm-return">Оформить возврат</button>
    </div>`;
}

registerRoute('/receipts/:id', async (params, container) => {
  let returnOpen = false;

  async function load() {
    const sale = await get('sales', params.id);
    const items = await getAll('saleItems', 'saleId', params.id);
    const returns = sale ? await getAll('returns', 'saleId', params.id) : [];
    const refundedKopecks = returns.reduce((s, r) => s + r.refundedKopecks, 0);
    return { sale, items, refundedKopecks };
  }

  async function render() {
    const { sale, items, refundedKopecks } = await load();
    if (!sale) { container.innerHTML = '<div class="empty-state">Чек не найден</div>'; return; }
    const canOpenReturn = !sale.isFullyReturned && !returnOpen;

    const root = fromHtml(`
      <div class="screen stack gap-3" style="max-width:560px;">
        <button class="inline-back" id="back-btn">← Чеки</button>
        <div class="overlay-title" style="font-size:18px;">Чек №${sale.receiptNumber}</div>
        <div class="muted small">${sale.paymentMethod === 'CASH' ? 'Наличные' : 'Безналичные'}</div>
        <div class="amount-card">
          <div id="items-list" class="stack" style="gap:2px;"></div>
          <div class="receipt-divider"></div>
          <div class="receipt-line"><span>Себестоимость</span><span>${Money.format(sale.cogsKopecks)}</span></div>
          <div class="receipt-total-row"><span>Итого</span><span>${Money.format(sale.totalKopecks)}</span></div>
          ${refundedKopecks ? `<div class="receipt-change-row"><span>Возвращено</span><span>${Money.format(refundedKopecks)}</span></div>` : ''}
        </div>
        <div id="return-form-slot"></div>
        ${canOpenReturn ? '<button class="btn btn-ghost" id="open-return" style="align-self:flex-start;">Оформить возврат</button>' : ''}
      </div>
    `);
    container.replaceChildren(root);
    root.querySelector('#back-btn').addEventListener('click', () => navigate('/receipts'));
    root.querySelector('#items-list').innerHTML = items.map((i) => `
      <div class="receipt-line">
        <div>
          <div>${escapeHtml(i.productNameSnapshot)}${i.variantNameSnapshot ? ` (${escapeHtml(i.variantNameSnapshot)})` : ''} × ${i.quantity}</div>
          ${i.returnedQuantity > 0 ? `<div class="badge badge-danger">Возвращено: ${i.returnedQuantity}</div>` : ''}
        </div>
        <div>${Money.format(i.lineTotalKopecks)}</div>
      </div>`).join('');

    const openReturnBtn = root.querySelector('#open-return');
    if (openReturnBtn) openReturnBtn.addEventListener('click', () => { returnOpen = true; render(); });

    if (returnOpen) {
      const slot = root.querySelector('#return-form-slot');
      slot.innerHTML = returnFormHtml();
      const quantities = new Map(items.map((i) => [i.id, 0]));
      const linesEl = slot.querySelector('#return-lines');
      items.forEach((item) => {
        const maxReturnable = item.quantity - item.returnedQuantity;
        if (maxReturnable <= 0) return;
        const row = fromHtml(`
          <div class="cart-line">
            <div class="info name">${escapeHtml(item.productNameSnapshot)}</div>
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
      let restock = false;
      const restockBtn = slot.querySelector('#restock-toggle');
      restockBtn.addEventListener('click', () => {
        restock = !restock;
        restockBtn.textContent = (restock ? '☑' : '☐') + ' Вернуть ингредиенты на склад';
      });
      slot.querySelector('#confirm-return').addEventListener('click', async () => {
        const reason = slot.querySelector('#return-reason').value.trim() || 'Не указана';
        const lines = [...quantities.entries()].filter(([, q]) => q > 0).map(([saleItemId, quantityToReturn]) => ({ saleItemId, quantityToReturn }));
        if (!lines.length) { showToast('Не выбрано ни одной позиции для возврата'); return; }
        const result = await processReturn({ saleId: sale.id, lines, reason, restockIngredients: restock, now: Date.now(), isDemo: sale.isDemo });
        if (result.status === 'ok') showToast('Возврат оформлен: ' + Money.format(result.refundedKopecks));
        else if (result.status === 'exceeds') showToast(`Нельзя вернуть больше, чем было продано (максимум ${result.maxAllowed})`);
        else showToast('Не выбрано ни одной позиции для возврата');
        returnOpen = false;
        render();
      });
    }
  }

  await render();
});
