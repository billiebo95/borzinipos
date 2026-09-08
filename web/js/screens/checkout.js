import { registerRoute, navigate } from '../router.js';
import { completeSale } from '../db.js';
import { Money } from '../money.js';
import { fromHtml } from '../dom.js';
import { showToast } from '../toast.js';
import { getCart, cartTotalKopecks, clearCart, checkoutKey, currentSettings } from '../state.js';
import { evaluateCashPayment } from '../core.js';

registerRoute('/checkout', async (params, container) => {
  const total = cartTotalKopecks();
  let paymentMethod = 'CASH';
  let receivedText = '';
  let submitting = false;

  const root = fromHtml(`
    <div class="screen" style="max-width:520px;">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn">
          <svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg>
        </button>
        <h2 class="screen-title" style="margin:0;">Оплата</h2>
      </div>
      <div class="card">
        <div class="muted small">Сумма заказа</div>
        <div style="font-size:32px;font-weight:800;color:var(--color-primary);">${Money.format(total)}</div>
      </div>
      <div class="row gap-2 mt-4">
        <div class="chip active" data-method="CASH">Наличные</div>
        <div class="chip" data-method="CASHLESS">Безналичные</div>
      </div>
      <div id="cash-section" class="mt-4"></div>
      <div id="cashless-section" class="mt-4 hidden">
        <p class="muted">Подтвердите, что оплата получена (через терминал или другим способом).</p>
      </div>
      <button class="btn btn-primary btn-block mt-4" id="submit-btn">Завершить оплату</button>
    </div>
  `);
  container.replaceChildren(root);

  const cashSection = root.querySelector('#cash-section');
  const cashlessSection = root.querySelector('#cashless-section');
  const submitBtn = root.querySelector('#submit-btn');

  root.querySelector('#back-btn').addEventListener('click', () => history.back());

  function renderCash() {
    cashSection.innerHTML = `
      <div class="field">
        <label>Получено от клиента, ₽</label>
        <input class="input" id="received-input" inputmode="decimal" value="${receivedText}" placeholder="0" />
      </div>
      <div class="row gap-2 wrap mt-3">
        ${[500, 1000, 2000, 5000].map((a) => `<button class="btn btn-outline btn-sm" data-amount="${a}">${a} ₽</button>`).join('')}
        <button class="btn btn-outline btn-sm" id="exact-btn">Без сдачи</button>
      </div>
      <div id="change-display" class="mt-3"></div>
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
      changeDisplay.innerHTML = '';
      submitBtn.disabled = true;
      return;
    }
    const result = evaluateCashPayment(total, received);
    if (result.ok) {
      changeDisplay.innerHTML = `<div style="font-size:20px;font-weight:800;">Сдача: ${Money.format(result.change)}</div>`;
      submitBtn.disabled = submitting;
    } else {
      changeDisplay.innerHTML = `<div class="badge badge-error">Не хватает ${Money.format(result.shortfall)}</div>`;
      submitBtn.disabled = true;
    }
  }

  root.querySelectorAll('[data-method]').forEach((chip) => chip.addEventListener('click', () => {
    paymentMethod = chip.dataset.method;
    root.querySelectorAll('[data-method]').forEach((c) => c.classList.toggle('active', c.dataset.method === paymentMethod));
    cashSection.classList.toggle('hidden', paymentMethod !== 'CASH');
    cashlessSection.classList.toggle('hidden', paymentMethod !== 'CASHLESS');
    submitBtn.disabled = paymentMethod === 'CASH' ? receivedKopecks() == null : false;
    if (paymentMethod === 'CASH') updateChangeAndButton();
  }));

  renderCash();

  submitBtn.addEventListener('click', async () => {
    if (submitting) return;
    submitting = true;
    submitBtn.disabled = true;
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
      navigate(`/receipts/${result.sale.id}`);
      return;
    }
    submitting = false;
    submitBtn.disabled = false;
    submitBtn.textContent = 'Завершить оплату';
    if (result.status === 'insufficient_stock') {
      showToast('Недостаточно на складе: ' + result.shortages.map((s) => s.name).join(', '));
    } else if (result.status === 'insufficient_cash') {
      showToast('Недостаточно наличных: не хватает ' + Money.format(result.shortfall));
    } else {
      showToast('Не удалось завершить продажу');
    }
  });
});
