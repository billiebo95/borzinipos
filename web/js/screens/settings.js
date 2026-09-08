// Settings hub: a flat list of rows (matching the Modernist design) that each drop into their own
// focused subscreen. Ported from the Android app's SettingsScreen, split up rather than one big form.
import { registerRoute, navigate } from '../router.js';
import {
  getAll, clearAllStores, putAll,
  saveExpense, deleteExpense,
  setCategoryArchived, setProductArchived, setInventoryItemArchived,
} from '../db.js';
import { Money } from '../money.js';
import { RUSSIAN_TIMEZONES, resolvePeriod } from '../core.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';
import { currentSettings, updateSettings } from '../state.js';

const backIcon = '<svg viewBox="0 0 24 24" width="20" height="20"><path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" fill="none"/></svg>';
const chevron = '<span class="chevron">›</span>';
const ALL_STORES = [
  'categories', 'products', 'variants', 'recipeLines', 'inventoryItems',
  'purchases', 'purchaseLines', 'stockMovements', 'sales', 'saleItems',
  'saleItemDeductions', 'returns', 'returnItems', 'expenses', 'goals', 'settings',
];

const SETTINGS_ROWS = [
  { name: 'Товары', route: '/catalog' },
  { name: 'Категории', route: '/catalog/categories' },
  { name: 'Архив', route: '/settings/archive' },
  { name: 'Цели', route: '/stats' },
  { name: 'Расходы', route: '/settings/expenses' },
  { name: 'Размер текста', route: '/settings/appearance' },
  { name: 'Параметры кофейни', route: '/settings/coffeeshop' },
  { name: 'Правила остатков', route: '/settings/stock-rules' },
  { name: 'Google-аккаунт и синхронизация', route: '/settings/google' },
  { name: 'Резервное копирование и восстановление', route: '/settings/backup' },
  { name: 'Экспорт данных', route: '/settings/export' },
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

function isDarkNow(settings) {
  if (settings.themeMode === 'DARK') return true;
  if (settings.themeMode === 'LIGHT') return false;
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function subScreen(title, bodyHtml, backRoute = '/settings') {
  const root = fromHtml(`
    <div class="screen" style="max-width:600px;">
      <div class="row gap-3 mb-4">
        <button class="icon-btn" id="back-btn">${backIcon}</button>
        <h2 class="screen-title" style="margin:0;">${title}</h2>
      </div>
      ${bodyHtml}
    </div>`);
  root.querySelector('#back-btn').addEventListener('click', () => navigate(backRoute));
  return root;
}

registerRoute('/settings', async (params, container) => {
  const settings = currentSettings();

  const root = fromHtml(`
    <div class="screen stack" style="max-width:600px;">
      <div class="screen-title" style="margin:0 0 6px;">Настройки</div>

      <div class="list-row" style="cursor:default;">
        <div class="name">Тёмная тема</div>
        <label class="switch"><input type="checkbox" id="f-theme-switch" ${isDarkNow(settings) ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
      </div>
      <div class="list-row" id="row-receipts"><div class="name">История чеков</div>${chevron}</div>
      ${SETTINGS_ROWS.map((r, i) => `<div class="list-row" data-row="${i}"><div class="name">${escapeHtml(r.name)}</div>${chevron}</div>`).join('')}

      <p class="muted small mt-4">BORZINI — касса для кофейни. Работает офлайн, все данные хранятся локально в этом браузере (IndexedDB) и никуда не отправляются.</p>
    </div>`);
  container.replaceChildren(root);

  root.querySelector('#f-theme-switch').addEventListener('change', (e) => {
    updateSettings({ themeMode: e.target.checked ? 'DARK' : 'LIGHT' });
  });
  root.querySelector('#row-receipts').addEventListener('click', () => navigate('/receipts'));
  root.querySelectorAll('[data-row]').forEach((row) => row.addEventListener('click', () => {
    navigate(SETTINGS_ROWS[Number(row.dataset.row)].route);
  }));
});

// ---------------------------------------------------------------------------------------------
// Appearance ("Размер текста" row): theme mode incl. system, text scale.
// ---------------------------------------------------------------------------------------------
registerRoute('/settings/appearance', async (params, container) => {
  const settings = currentSettings();
  const root = subScreen('Внешний вид', `
    <div class="card">
      <div class="field"><label>Тема</label>
        <select class="input" id="f-theme">
          <option value="LIGHT" ${settings.themeMode === 'LIGHT' ? 'selected' : ''}>Светлая</option>
          <option value="DARK" ${settings.themeMode === 'DARK' ? 'selected' : ''}>Тёмная</option>
          <option value="SYSTEM" ${settings.themeMode === 'SYSTEM' ? 'selected' : ''}>Как в системе</option>
        </select>
      </div>
      <div class="field mt-3"><label>Размер текста</label>
        <input type="range" id="f-textscale" min="0.85" max="1.3" step="0.05" value="${settings.textScale}" />
      </div>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#f-theme').addEventListener('change', (e) => updateSettings({ themeMode: e.target.value }));
  root.querySelector('#f-textscale').addEventListener('change', (e) => updateSettings({ textScale: Number(e.target.value) }));
});

// ---------------------------------------------------------------------------------------------
// Coffee shop parameters
// ---------------------------------------------------------------------------------------------
registerRoute('/settings/coffeeshop', async (params, container) => {
  const settings = currentSettings();
  const root = subScreen('Параметры кофейни', `
    <div class="card">
      <div class="field"><label>Название</label><input class="input" id="f-name" value="${escapeHtml(settings.coffeeShopName)}" /></div>
      <div class="field mt-3"><label>Часовой пояс</label>
        <select class="input" id="f-timezone">
          ${Object.keys(RUSSIAN_TIMEZONES).map((tz) => `<option value="${tz}" ${settings.timezoneId === tz ? 'selected' : ''}>${tz} (UTC+${RUSSIAN_TIMEZONES[tz]})</option>`).join('')}
        </select>
      </div>
      <div class="row between mt-3"><span>Демо-режим (демо-товары видны в кассе)</span>
        <label class="switch"><input type="checkbox" id="f-demo" ${settings.demoModeEnabled ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
      </div>
      <p class="muted small mt-2">Демо-товары и демо-остатки никогда не попадают в реальную статистику и отчёты, даже если демо-режим включён.</p>
    </div>
    <div class="card mt-3">
      <div class="row between"><span>Автоматически учитывать комиссию эквайринга</span>
        <label class="switch"><input type="checkbox" id="f-fee-enabled" ${settings.autoAcquiringFeeEnabled ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
      </div>
      <div class="field mt-3"><label>Процент комиссии (от безналичной выручки)</label>
        <input class="input" id="f-fee-percent" value="${settings.autoAcquiringFeePercent}" style="max-width:140px;" />
      </div>
      <p class="muted small mt-2">Если включено, комиссия считается автоматически и не даёт задвоить её ручной статьёй расходов «ACQUIRING_FEE».</p>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#f-name').addEventListener('change', (e) => updateSettings({ coffeeShopName: e.target.value.trim() || 'BORZINI' }));
  root.querySelector('#f-timezone').addEventListener('change', (e) => updateSettings({ timezoneId: e.target.value }));
  root.querySelector('#f-demo').addEventListener('change', (e) => updateSettings({ demoModeEnabled: e.target.checked }));
  root.querySelector('#f-fee-enabled').addEventListener('change', (e) => updateSettings({ autoAcquiringFeeEnabled: e.target.checked }));
  root.querySelector('#f-fee-percent').addEventListener('change', (e) => {
    const v = Number(String(e.target.value).replace(',', '.'));
    if (Number.isFinite(v) && v >= 0) updateSettings({ autoAcquiringFeePercent: v });
  });
});

// ---------------------------------------------------------------------------------------------
// Stock rules
// ---------------------------------------------------------------------------------------------
registerRoute('/settings/stock-rules', async (params, container) => {
  const settings = currentSettings();
  const root = subScreen('Правила остатков', `
    <div class="card">
      <div class="row between"><span>Разрешить уход остатка в минус</span>
        <label class="switch"><input type="checkbox" id="f-negative" ${settings.negativeStockAllowed ? 'checked' : ''} /><span class="track"><span class="thumb"></span></span></label>
      </div>
      <p class="muted small mt-2">Если выключено, продажа блокируется, когда на складе не хватает ингредиентов или товара.</p>
    </div>`);
  container.replaceChildren(root);
  root.querySelector('#f-negative').addEventListener('change', (e) => updateSettings({ negativeStockAllowed: e.target.checked }));
});

// ---------------------------------------------------------------------------------------------
// Google account & sync
// ---------------------------------------------------------------------------------------------
registerRoute('/settings/google', async (params, container) => {
  const root = subScreen('Google-аккаунт и синхронизация', `
    <div class="card">
      <p class="muted small">Синхронизация с Google Диском/Таблицами пока не реализована в веб-версии приложения — все данные хранятся только локально, в этом браузере. Не переносите ожидание синхронизации на эту версию, пока она явно не появится здесь.</p>
    </div>`);
  container.replaceChildren(root);
});

// ---------------------------------------------------------------------------------------------
// Backup & restore
// ---------------------------------------------------------------------------------------------
registerRoute('/settings/backup', async (params, container) => {
  const root = subScreen('Резервное копирование и восстановление', `
    <div class="card stack gap-2">
      <button class="btn btn-outline btn-block" id="export-backup">Экспорт резервной копии (JSON)</button>
      <label class="btn btn-outline btn-block" style="text-align:center;">
        Восстановить из резервной копии
        <input type="file" accept="application/json" id="import-backup" class="hidden" />
      </label>
    </div>`);
  container.replaceChildren(root);
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
});

// ---------------------------------------------------------------------------------------------
// CSV export
// ---------------------------------------------------------------------------------------------
registerRoute('/settings/export', async (params, container) => {
  const root = subScreen('Экспорт данных', `
    <div class="card stack gap-2">
      <button class="btn btn-outline btn-block" id="export-sales-csv">Чеки за текущий месяц</button>
      <button class="btn btn-outline btn-block" id="export-inventory-csv">Текущие остатки склада</button>
      <button class="btn btn-outline btn-block" id="export-expenses-csv">Расходы за текущий месяц</button>
    </div>`);
  container.replaceChildren(root);
  const settings = currentSettings();

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

    const root = subScreen('Расходы (текущий месяц)', `
      <div class="card mb-3">
        <div class="field"><label>Дата</label><input type="date" class="input" id="f-date" value="${new Date().toISOString().slice(0, 10)}" /></div>
        <div class="field mt-3"><label>Категория</label><input class="input" id="f-category" placeholder="Аренда, Зарплата, ..." /></div>
        <div class="field mt-3"><label>Сумма, ₽</label><input class="input" id="f-amount" /></div>
        <div class="field mt-3"><label>Комментарий (необязательно)</label><input class="input" id="f-comment" /></div>
        <button class="btn btn-primary btn-block mt-3" id="add-btn">Добавить расход</button>
        <div id="err" class="mt-2" style="color:var(--color-accent-text);"></div>
      </div>
      <div class="row between mb-2"><strong>Итого за месяц</strong><strong>${Money.format(total)}</strong></div>
      <div id="list"></div>`);
    container.replaceChildren(root);
    root.querySelector('#list').innerHTML = expenses.map((e) => `
      <div class="list-row" style="cursor:default;" data-id="${e.id}">
        <div><div class="name">${escapeHtml(e.category)}</div><div class="sub">${new Date(e.date).toLocaleDateString('ru-RU')}${e.comment ? ` · ${escapeHtml(e.comment)}` : ''}</div></div>
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

    function section(title, rows) {
      return `
        <div class="sub-card-heading mt-4 mb-2">${title}</div>
        <div class="stack gap-2">
          ${rows.map((r) => `
            <div class="row between card" data-id="${r.id}">
              <span>${escapeHtml(r.name)}</span>
              <button class="btn btn-outline btn-sm" data-restore="${r.id}">Восстановить</button>
            </div>`).join('') || '<div class="empty-state small">Ничего нет в архиве</div>'}
        </div>`;
    }

    const root = subScreen('Архив', `
      <div id="categories-section">${section('Категории', archivedCategories)}</div>
      <div id="products-section">${section('Товары', archivedProducts)}</div>
      <div id="items-section">${section('Складские позиции', archivedItems)}</div>`);
    container.replaceChildren(root);

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
