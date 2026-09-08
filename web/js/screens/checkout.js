import { registerRoute, navigate } from '../router.js';
import { completeSale, get, getAll } from '../db.js';
import { Money } from '../money.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';
import { getCart, cartTotalKopecks, clearCart, checkoutKey, currentSettings } from '../state.js';
import { evaluateCashPayment } from '../core.js';

registerRoute('/checkout', async (params, container) => {
  const total = cartTotalKopecks();
  let paymentMethod = 'CASH';
  let receivedText = '';
  let submitting = false;

  const root = fromHtml(`
    <div class="overlay-screen">
      <div class="overlay-header">
        <button class="back-btn" id="back-btn">
          <svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2.2" fill="none"/></svg>
        </button>
        <div class="overlay-title">Оплата</div>
      </div>
      <div class="overlay-body">
        <div class="amount-card">
          <div class="muted small">Сумма заказа</div>
          <div style="font-family:var(--font);font-weight:800;font-size:30px;color:var(--color-accent-text);">${Money.format(total)}</div>
        </div>

        <div class="method-row mt-4">
          <div class="chip active" data-method="CASH">Наличные</div>
          <div class="chip" data-method="CASHLESS">Безналичные</div>
        </div>

        <div id="cash-section"></div>
        <div id="cashless-section" class="hidden">
          <p class="muted small">Подтвердите, что оплата получена через терминал или другим способом.</p>
        </div>
      </div>
      <div class="overlay-footer">
        <button class="btn btn-primary btn-block" id="submit-btn" disabled style="opacity:.45;">Завершить оплату</button>
      </div>
    </div>
  `);
  container.replaceChildren(root);

  const cashSection = root.querySelector('#cash-section');
  const cashlessSection = root.querySelector('#cashless-section');
  const submitBtn = root.querySelector('#submit-btn');

  root.querySelector('#back-btn').addEventListener('click', () => navigate('/cart'));

  function renderCash() {
    cashSection.innerHTML = `
      <div class="field">
        <label>Получено от клиента, ₽</label>
        <input class="input" id="received-input" inputmode="decimal" value="${receivedText}" placeholder="0" />
      </div>
      <div class="quick-row mt-3">
        ${[500, 1000, 2000, 5000].map((a) => `<button class="chip" data-amount="${a}">${a} ₽</button>`).join('')}
        <button class="chip" id="exact-btn">Без сдачи</button>
      </div>
      <div id="change-display" style="font-family:var(--font);font-weight:800;font-size:17px;"></div>
    `;
    const input = cashSection.querySelector('#received-input');
    input.addEventListener('input', () => { receivedText = input.value; updateChangeAndButton(); });
    cashSection.querySelectorAll('[data-amount]').forEach((b) => b.addEventListener('click', () => {
      receivedText = b.dataset.amount;
      input.value = receivedText;
      updateChangeAndButton();
    }));
    cashSection.querySelector('#exact-btn').addEventListener('click', () => {
      receivedText = (total / 100).toString().replace('.', ',');
      input.value = receivedText;
      updateChangeAndButton();
    });
    updateChangeAndButton();
  }

  function receivedKopecks() {
    const normalized = receivedText.trim().replace(',', '.');
    if (!normalized) return null;
    const value = Number(normalized);
    return Number.isFinite(value) ? Math.round(value * 100) : null;
  }

  function updateChangeAndButton() {
    const changeDisplay = cashSection.querySelector('#change-display');
    const received = receivedKopecks();
    if (received == null) {
      changeDisplay.textContent = '';
      changeDisplay.style.color = 'var(--color-text)';
      setSubmitEnabled(false);
      return;
    }
    const result = evaluateCashPayment(total, received);
    if (result.ok) {
      changeDisplay.textContent = 'Сдача: ' + Money.format(result.change);
      changeDisplay.style.color = 'var(--color-text)';
      setSubmitEnabled(!submitting);
    } else {
      changeDisplay.textContent = 'Не хватает ' + Money.format(result.shortfall);
      changeDisplay.style.color = 'var(--color-accent-text)';
      setSubmitEnabled(false);
    }
  }

  function setSubmitEnabled(enabled) {
    submitBtn.disabled = !enabled;
    submitBtn.style.opacity = enabled ? '1' : '.45';
  }

  root.querySelectorAll('[data-method]').forEach((chip) => chip.addEventListener('click', () => {
    paymentMethod = chip.dataset.method;
    root.querySelectorAll('[data-method]').forEach((c) => c.classList.toggle('active', c.dataset.method === paymentMethod));
    cashSection.classList.toggle('hidden', paymentMethod !== 'CASH');
    cashlessSection.classList.toggle('hidden', paymentMethod !== 'CASHLESS');
    if (paymentMethod === 'CASH') updateChangeAndButton();
    else setSubmitEnabled(!submitting);
  }));

  renderCash();

  submitBtn.addEventListener('click', async () => {
    if (submitting) return;
    submitting = true;
    setSubmitEnabled(false);
    submitBtn.textContent = 'Обрабатываем…';

    const settings = currentSettings();
    const lines = getCart().map((l) => ({
      productId: l.productId, productName: l.productName, variantId: l.variantId, variantName: l.variantName,
      unitPriceKopecks: l.unitPriceKopecks, quantity: l.quantity, isSimpleProduct: l.isSimpleProduct, simpleInventoryItemId: l.simpleInventoryItemId,
    }));

    const result = await completeSale({
      idempotencyKey: checkoutKey(),
      lines,
      paymentMethod,
      cashReceivedKopecks: paymentMethod === 'CASH' ? receivedKopecks() : null,
      allowNegative: !!settings.negativeStockAllowed,
      now: Date.now(),
      isDemo: !!settings.demoModeEnabled,
    });

    if (result.status === 'ok') {
      clearCart();
      navigate(`/checkout/success/${result.sale.id}`);
      return;
    }
    submitting = false;
    submitBtn.textContent = 'Завершить оплату';
    if (paymentMethod === 'CASH') updateChangeAndButton(); else setSubmitEnabled(true);
    if (result.status === 'insufficient_stock') {
      showToast('Недостаточно на складе: ' + result.shortages.map((s) => s.name).join(', '));
    } else if (result.status === 'insufficient_cash') {
      showToast('Недостаточно наличных: не хватает ' + Money.format(result.shortfall));
    } else {
      showToast('Не удалось завершить продажу');
    }
  });
});

// Post-payment confirmation — a distinct overlay from the receipt-history detail view
// (no return flow here, just "paid, here's what for" and a way back into a new order).
registerRoute('/checkout/success/:id', async (params, container) => {
  const sale = await get('sales', params.id);
  if (!sale) { navigate('/pos'); return; }
  const items = await getAll('saleItems', 'saleId', params.id);

  const root = fromHtml(`
    <div class="overlay-screen">
      <div class="overlay-body" style="display:flex;flex-direction:column;align-items:center;padding:48px 20px;gap:10px;">
        <div class="check-circle">
          <svg viewBox="0 0 24 24" width="30" height="30"><path d="M5 13l4 4 10-10" stroke="#fff" stroke-width="2.6" fill="none"/></svg>
        </div>
        <div style="font-family:var(--font);font-weight:800;font-size:20px;">Оплата принята</div>
        <div class="muted small" style="margin-bottom:18px;">${sale.paymentMethod === 'CASH' ? 'Наличными' : 'Безналичный расчёт'}</div>

        <div class="amount-card" style="width:100%;display:flex;flex-direction:column;gap:8px;margin-bottom:24px;">
          ${items.map((it) => `
            <div class="receipt-line"><span>${escapeHtml(it.productNameSnapshot)} × ${it.quantity}</span><span>${Money.format(it.lineTotalKopecks)}</span></div>`).join('')}
          <div class="receipt-divider"></div>
          <div class="receipt-total-row"><span>Итого</span><span>${Money.format(sale.totalKopecks)}</span></div>
          ${sale.paymentMethod === 'CASH' ? `
            <div class="receipt-change-row"><span>Сдача</span><span>${Money.format(sale.changeGivenKopecks || 0)}</span></div>` : ''}
        </div>

        <button class="btn btn-primary btn-block" id="new-order">Новый заказ</button>
      </div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#new-order').addEventListener('click', () => navigate('/pos'));
});
