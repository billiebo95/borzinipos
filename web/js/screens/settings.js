// Settings hub: appearance/theming, coffee-shop & payment settings, catalog/expense/archive
// management links, backup & restore, CSV export. Ported from the Android app's SettingsScreen.
import { registerRoute, navigate } from '../router.js';
import {
  getAll, get, put, clearAllStores, putAll,
  saveExpense, deleteExpense,
  setCategoryArchived, setProductArchived, setInventoryItemArchived,
} from '../db.js';
import { Money } from '../money.js';
import { RUSSIAN_TIMEZONES, resolvePeriod } from '../core.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';
import { currentSettings, updateSettings } from '../state.js';

const backIcon = '<svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg>';
const ACCENT_SWATCHES = ['4A1420', '1B4A3C', '1B3A5C', '5C3A1B', '3A1B5C', '1B1B1B'];
const ALL_STORES = [
  'categories', 'products', 'variants', 'recipeLines', 'inventoryItems',
  'purchases', 'purchaseLines', 'stockMovements', 'sales', 'saleItems',
  'saleItemDeductions', 'returns', 'returnItems', 'expenses', 'goals', 'settings',
];

function toCsv(rows, columns) {
  const escape = (v) => {
    const s = String(v ?? '');
    return /[",\n;]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  };
  const header = columns.map(([, label]) => escape(label)).join(';');
  const body = rows.map((r) => columns.map(([key]) => escape(typeof key === 'function' ? key(r) : r[key])).join(';')).join('\n');
  return '﻿' + header + '\n' + body;
}

function downloadTextFile(filename, mime, content) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

registerRoute('/settings', async (params, container) => {
  const settings = currentSettings();
  const root = fromHtml(`
    <div class="screen" style="max-width:600px;">
      <h2 class="screen-title">Настройки</h2>

      <div class="card">
        <h3 class="mb-3">Внешний вид</h3>
        <div class="field"><label>Тема</label>
          <select class="input" id="f-theme">
            <option value="LIGHT" ${settings.themeMode === 'LIGHT' ? 'selected' : ''}>Светлая</option>
            <option value="DARK" ${settings.themeMode === 'DARK' ? 'selected' : ''}>Тёмная</option>
            <option value="SYSTEM" ${settings.themeMode === 'SYSTEM' ? 'selected' : ''}>Как в системе</option>
          </select>
        </div>
        <div class="field mt-3"><label>Акцентный цвет</label>
          <div class="row gap-2" id="accent-swatches">
            ${ACCENT_SWATCHES.map((hex) => `<div data-hex="${hex}" style="width:32px;height:32px;border-radius:50%;background:#${hex};cursor:pointer;border:3px solid ${settings.accentColorHex.toUpperCase() === hex.toUpperCase() ? 'var(--color-text)' : 'transparent'};"></div>`).join('')}
          </div>
        </div>
        <div class="field mt-3"><label>Размер текста</label>
          <input type="range" id="f-textscale" min="0.85" max="1.3" step="0.05" value="${settings.textScale}" style="width:100%;" />
        </div>
      </div>

      <div class="card mt-3">
        <h3 class="mb-3">Кофейня</h3>
        <div class="field"><label>Название</label><input class="input" id="f-name" value="${escapeHtml(settings.coffeeShopName)}" /></div>
        <div class="field mt-3"><label>Часовой пояс</label>
          <select class="input" id="f-timezone">
            ${Object.keys(RUSSIAN_TIMEZONES).map((tz) => `<option value="${tz}" ${settings.timezoneId === tz ? 'selected' : ''}>${tz} (UTC+${RUSSIAN_TIMEZONES[tz]})</option>`).join('')}
          </select>
        </div>
        <div class="row between mt-3"><span>Разрешить уход остатка в минус</span>
          <label class="switch"><input type="checkbox" id="f-negative" ${settings.negativeStockAllowed ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
        </div>
        <div class="row between mt-3"><span>Демо-режим (демо-товары видны в кассе)</span>
          <label class="switch"><input type="checkbox" id="f-demo" ${settings.demoModeEnabled ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
        </div>
        <p class="muted small mt-2">Демо-товары и демо-остатки никогда не попадают в реальную статистику и отчёты, даже если демо-режим включён.</p>
      </div>

      <div class="card mt-3">
        <h3 class="mb-3">Приём оплаты</h3>
        <div class="row between"><span>Автоматически учитывать комиссию эквайринга</span>
          <label class="switch"><input type="checkbox" id="f-fee-enabled" ${settings.autoAcquiringFeeEnabled ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
        </div>
        <div class="field mt-3"><label>Процент комиссии (от безналичной выручки)</label>
          <input class="input" id="f-fee-percent" value="${settings.autoAcquiringFeePercent}" style="max-width:140px;" />
        </div>
        <p class="muted small mt-2">Если включено, комиссия считается автоматически и не даёт задвоить её ручной статьёй расходов «ACQUIRING_FEE».</p>
      </div>

      <div class="card mt-3">
        <h3 class="mb-3">Управление</h3>
        <div class="stack gap-2">
          <button class="btn btn-outline btn-block" id="link-catalog">Товары</button>
          <button class="btn btn-outline btn-block" id="link-categories">Категории</button>
          <button class="btn btn-outline btn-block" id="link-expenses">Расходы</button>
          <button class="btn btn-outline btn-block" id="link-archive">Архив</button>
        </div>
      </div>

      <div class="card mt-3">
        <h3 class="mb-3">Google Диск</h3>
        <p class="muted small">Синхронизация с Google Диском/Таблицами пока не реализована в веб-версии приложения — все данные хранятся только локально, в этом браузере. Не переносите ожидание синхронизации на эту версию, пока она явно не появится здесь.</p>
      </div>

      <div class="card mt-3">
        <h3 class="mb-3">Резервная копия</h3>
        <div class="stack gap-2">
          <button class="btn btn-outline btn-block" id="export-backup">Экспорт резервной копии (JSON)</button>
          <label class="btn btn-outline btn-block" style="text-align:center;">
            Восстановить из резервной копии
            <input type="file" accept="application/json" id="import-backup" class="hidden" />
          </label>
        </div>
        <h3 class="mt-4 mb-2">Экспорт CSV</h3>
        <div class="stack gap-2">
          <button class="btn btn-ghost btn-block" id="export-sales-csv">Чеки за текущий месяц</button>
          <button class="btn btn-ghost btn-block" id="export-inventory-csv">Текущие остатки склада</button>
          <button class="btn btn-ghost btn-block" id="export-expenses-csv">Расходы за текущий месяц</button>
        </div>
      </div>

      <div class="card mt-3 mb-4">
        <h3 class="mb-2">О приложении</h3>
        <p class="muted small">BORZINI — касса для кофейни. Веб-версия, работает офлайн, все данные хранятся локально в этом браузере (IndexedDB) и никуда не отправляются.</p>
      </div>
    </div>`);
  container.replaceChildren(root);

  root.querySelector('#f-theme').addEventListener('change', (e) => updateSettings({ themeMode: e.target.value }));
  root.querySelectorAll('#accent-swatches [data-hex]').forEach((sw) => sw.addEventListener('click', async () => {
    await updateSettings({ accentColorHex: sw.dataset.hex });
    root.querySelectorAll('#accent-swatches [data-hex]').forEach((s) => { s.style.borderColor = s === sw ? 'var(--color-text)' : 'transparent'; });
  }));
  root.querySelector('#f-textscale').addEventListener('change', (e) => updateSettings({ textScale: Number(e.target.value) }));
  root.querySelector('#f-name').addEventListener('change', (e) => updateSettings({ coffeeShopName: e.target.value.trim() || 'BORZINI' }));
  root.querySelector('#f-timezone').addEventListener('change', (e) => updateSettings({ timezoneId: e.target.value }));
  root.querySelector('#f-negative').addEventListener('change', (e) => updateSettings({ negativeStockAllowed: e.target.checked }));
  root.querySelector('#f-demo').addEventListener('change', (e) => updateSettings({ demoModeEnabled: e.target.checked }));
  root.querySelector('#f-fee-enabled').addEventListener('change', (e) => updateSettings({ autoAcquiringFeeEnabled: e.target.checked }));
  root.querySelector('#f-fee-percent').addEventListener('change', (e) => {
    const v = Number(String(e.target.value).replace(',', '.'));
    if (Number.isFinite(v) && v >= 0) updateSettings({ autoAcquiringFeePercent: v });
  });

  root.querySelector('#link-catalog').addEventListener('click', () => navigate('/catalog'));
  root.querySelector('#link-categories').addEventListener('click', () => navigate('/catalog/categories'));
  root.querySelector('#link-expenses').addEventListener('click', () => navigate('/settings/expenses'));
  root.querySelector('#link-archive').addEventListener('click', () => navigate('/settings/archive'));

  root.querySelector('#export-backup').addEventListener('click', async () => {
    const data = {};
    for (const store of ALL_STORES) data[store] = await getAll(store);
    const payload = { app: 'BORZINI', formatVersion: 1, exportedAt: Date.now(), data };
    const date = new Date().toISOString().slice(0, 10);
    downloadTextFile(`borzini-backup-${date}.json`, 'application/json', JSON.stringify(payload, null, 2));
    showToast('Резервная копия сохранена в загрузки браузера');
  });

  root.querySelector('#import-backup').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (!confirm('Восстановление полностью заменит текущие данные приложения этой резервной копией. Продолжить?')) { e.target.value = ''; return; }
    try {
      const text = await file.text();
      const parsed = JSON.parse(text);
      if (!parsed || typeof parsed !== 'object' || !parsed.data) throw new Error('bad format');
      await clearAllStores();
      for (const store of ALL_STORES) {
        const rows = parsed.data[store];
        if (Array.isArray(rows) && rows.length) await putAll(store, rows);
      }
      showToast('Резервная копия восстановлена. Перезагрузите страницу.');
    } catch (err) {
      showToast('Не удалось прочитать файл резервной копии — неверный формат');
    }
    e.target.value = '';
  });

  root.querySelector('#export-sales-csv').addEventListener('click', async () => {
    const { startMs, endMs } = resolvePeriod('THIS_MONTH', settings.timezoneId);
    const sales = (await getAll('sales')).filter((s) => s.createdAt >= startMs && s.createdAt < endMs).sort((a, b) => a.createdAt - b.createdAt);
    const csv = toCsv(sales, [
      ['receiptNumber', 'Чек №'],
      [(s) => new Date(s.createdAt).toLocaleString('ru-RU'), 'Дата и время'],
      [(s) => (s.paymentMethod === 'CASH' ? 'Наличные' : 'Безналичные'), 'Оплата'],
      [(s) => Money.toRubleNumber(s.totalKopecks).toFixed(2), 'Сумма, ₽'],
      [(s) => Money.toRubleNumber(s.cogsKopecks).toFixed(2), 'Себестоимость, ₽'],
      [(s) => (s.isFullyReturned ? 'Полный возврат' : s.isPartiallyReturned ? 'Частичный возврат' : ''), 'Возврат'],
    ]);
    downloadTextFile('borzini-receipts.csv', 'text/csv;charset=utf-8', csv);
  });

  root.querySelector('#export-inventory-csv').addEventListener('click', async () => {
    const items = (await getAll('inventoryItems')).filter((i) => !i.isArchived).sort((a, b) => a.name.localeCompare(b.name, 'ru'));
    const csv = toCsv(items, [
      ['name', 'Название'], ['category', 'Категория'],
      [(i) => (i.onHandMilli / 1000).toString(), 'Остаток'], ['baseUnit', 'Ед. изм.'],
      [(i) => (i.avgUnitCostMicro / 1e6).toFixed(4), 'Себестоимость за ед., ₽'],
    ]);
    downloadTextFile('borzini-inventory.csv', 'text/csv;charset=utf-8', csv);
  });

  root.querySelector('#export-expenses-csv').addEventListener('click', async () => {
    const { startMs, endMs } = resolvePeriod('THIS_MONTH', settings.timezoneId);
    const expenses = (await getAll('expenses')).filter((e) => e.date >= startMs && e.date < endMs).sort((a, b) => a.date - b.date);
    const csv = toCsv(expenses, [
      [(e) => new Date(e.date).toLocaleDateString('ru-RU'), 'Дата'], ['category', 'Категория'],
      [(e) => Money.toRubleNumber(e.amountKopecks).toFixed(2), 'Сумма, ₽'], [(e) => e.comment || '', 'Комментарий'],
    ]);
    downloadTextFile('borzini-expenses.csv', 'text/csv;charset=utf-8', csv);
  });
});

// ---------------------------------------------------------------------------------------------
// Expenses
// ---------------------------------------------------------------------------------------------
registerRoute('/settings/expenses', async (params, container) => {
  async function render() {
    const settings = currentSettings();
    const { startMs, endMs } = resolvePeriod('THIS_MONTH', settings.timezoneId);
    const expenses = (await getAll('expenses')).filter((e) => e.date >= startMs && e.date < endMs).sort((a, b) => b.date - a.date);
    const total = Money.sum(expenses.map((e) => e.amountKopecks));

    const root = fromHtml(`
      <div class="screen" style="max-width:520px;">
        <div class="row gap-3 mb-4">
          <button class="icon-btn" id="back-btn">${backIcon}</button>
          <h2 class="screen-title" style="margin:0;">Расходы (текущий месяц)</h2>
        </div>
        <div class="card mb-3">
          <div class="field"><label>Дата</label><input type="date" class="input" id="f-date" value="${new Date().toISOString().slice(0, 10)}" /></div>
          <div class="field mt-3"><label>Категория</label><input class="input" id="f-category" placeholder="Аренда, Зарплата, ..." /></div>
          <div class="field mt-3"><label>Сумма, ₽</label><input class="input" id="f-amount" /></div>
          <div class="field mt-3"><label>Комментарий (необязательно)</label><input class="input" id="f-comment" /></div>
          <button class="btn btn-primary btn-block mt-3" id="add-btn">Добавить расход</button>
          <div id="err" class="mt-2" style="color:var(--color-error);"></div>
        </div>
        <div class="row between mb-2"><strong>Итого за месяц</strong><strong>${Money.format(total)}</strong></div>
        <div class="stack gap-2" id="list"></div>
      </div>`);
    container.replaceChildren(root);
    root.querySelector('#back-btn').addEventListener('click', () => navigate('/settings'));
    root.querySelector('#list').innerHTML = expenses.map((e) => `
      <div class="row between card" data-id="${e.id}">
        <div><div style="font-weight:700;">${escapeHtml(e.category)}</div><div class="muted small">${new Date(e.date).toLocaleDateString('ru-RU')}${e.comment ? ` · ${escapeHtml(e.comment)}` : ''}</div></div>
        <div class="row gap-2"><strong>${Money.format(e.amountKopecks)}</strong><button class="icon-btn" data-delete="${e.id}">✕</button></div>
      </div>`).join('') || '<div class="empty-state">Расходов за этот месяц пока нет</div>';
    root.querySelectorAll('[data-delete]').forEach((btn) => btn.addEventListener('click', async () => {
      await deleteExpense(btn.dataset.delete);
      render();
    }));
    root.querySelector('#add-btn').addEventListener('click', async () => {
      const dateStr = root.querySelector('#f-date').value;
      const category = root.querySelector('#f-category').value.trim();
      const amount = Money.fromRubleString(root.querySelector('#f-amount').value);
      const comment = root.querySelector('#f-comment').value.trim();
      const errEl = root.querySelector('#err');
      if (!dateStr || !category || amount == null || amount <= 0) { errEl.textContent = 'Укажите дату, категорию и корректную сумму'; return; }
      await saveExpense({ id: null, date: new Date(dateStr).getTime(), category, amountKopecks: amount, comment, now: Date.now() });
      render();
    });
  }
  await render();
});

// ---------------------------------------------------------------------------------------------
// Archive
// ---------------------------------------------------------------------------------------------
registerRoute('/settings/archive', async (params, container) => {
  async function render() {
    const [categories, products, items] = await Promise.all([getAll('categories'), getAll('products'), getAll('inventoryItems')]);
    const archivedCategories = categories.filter((c) => c.isArchived);
    const archivedProducts = products.filter((p) => p.isArchived);
    const archivedItems = items.filter((i) => i.isArchived);

    function section(title, rows, onRestore) {
      return `
        <h3 class="mt-4 mb-2">${title}</h3>
        <div class="stack gap-2">
          ${rows.map((r) => `
            <div class="row between card" data-id="${r.id}">
              <span>${escapeHtml(r.name)}</span>
              <button class="btn btn-outline btn-sm" data-restore="${r.id}">Восстановить</button>
            </div>`).join('') || '<div class="empty-state small">Ничего нет в архиве</div>'}
        </div>`;
    }

    const root = fromHtml(`
      <div class="screen" style="max-width:520px;">
        <div class="row gap-3 mb-4">
          <button class="icon-btn" id="back-btn">${backIcon}</button>
          <h2 class="screen-title" style="margin:0;">Архив</h2>
        </div>
        <div id="categories-section">${section('Категории', archivedCategories)}</div>
        <div id="products-section">${section('Товары', archivedProducts)}</div>
        <div id="items-section">${section('Складские позиции', archivedItems)}</div>
      </div>`);
    container.replaceChildren(root);
    root.querySelector('#back-btn').addEventListener('click', () => navigate('/settings'));

    root.querySelector('#categories-section').querySelectorAll('[data-restore]').forEach((btn) => btn.addEventListener('click', async () => {
      await setCategoryArchived(btn.dataset.restore, false, Date.now());
      render();
    }));
    root.querySelector('#products-section').querySelectorAll('[data-restore]').forEach((btn) => btn.addEventListener('click', async () => {
      await setProductArchived(btn.dataset.restore, false, Date.now());
      render();
    }));
    root.querySelector('#items-section').querySelectorAll('[data-restore]').forEach((btn) => btn.addEventListener('click', async () => {
      await setInventoryItemArchived(btn.dataset.restore, false, Date.now());
      render();
    }));
  }
  await render();
});
