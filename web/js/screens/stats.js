// Statistics: period KPIs, top products, category breakdown, Mon-Sun revenue chart, goals.
// Ported from the Android app's StatsScreen / WeekChart.
import { registerRoute } from '../router.js';
import { getAll, getAllInRange, setGoal } from '../db.js';
// (getAll pulls the full products/categories/saleItems tables once per KPI render, which is fine
// at this app's expected scale - a coffee shop's total product/category/receipt-item counts.)
import { Money } from '../money.js';
import { aggregateFinancials, computeAcquiringFee, resolvePeriod, todayDateString, weekDates, dayRange } from '../core.js';
import { escapeHtml, fromHtml } from '../dom.js';
import { showToast } from '../toast.js';
import { currentSettings } from '../state.js';

const PERIOD_LABELS = { TODAY: 'Сегодня', YESTERDAY: 'Вчера', THIS_WEEK: 'Эта неделя', THIS_MONTH: 'Этот месяц', CUSTOM: 'Свой период' };

async function loadPeriodData(startMs, endMs) {
  const [sales, returns, expenses] = await Promise.all([
    getAllInRange('sales', 'createdAt', startMs, endMs),
    getAllInRange('returns', 'createdAt', startMs, endMs),
    getAllInRange('expenses', 'date', startMs, endMs),
  ]);
  return { sales, returns, expenses };
}

registerRoute('/stats', async (params, container) => {
  const settings = currentSettings();
  const tz = settings.timezoneId;
  let preset = 'TODAY';
  let customStart = todayDateString(tz);
  let customEnd = todayDateString(tz);

  const root = fromHtml(`
    <div class="screen stack gap-3">
      <div class="screen-title" style="margin:0;">Статистика</div>
      <div class="chip-row" id="period-chips">
        ${Object.entries(PERIOD_LABELS).map(([k, label]) => `<div class="chip ${k === preset ? 'active' : ''}" data-preset="${k}">${label}</div>`).join('')}
      </div>
      <div id="custom-range" class="row gap-2 hidden">
        <input type="date" class="input" id="custom-start" value="${customStart}" />
        <input type="date" class="input" id="custom-end" value="${customEnd}" />
        <button class="btn btn-outline btn-sm" id="apply-custom">Применить</button>
      </div>

      <div id="kpi-grid" class="kpi-grid"></div>

      <div class="sub-card">
        <div class="sub-card-heading">Неделя (Пн–Вс)</div>
        <div id="week-chart"></div>
      </div>

      <div class="sub-card">
        <div class="sub-card-heading">Топ товаров</div>
        <div id="top-products" class="stack"></div>
      </div>

      <div class="sub-card">
        <div class="sub-card-heading">По категориям</div>
        <div id="by-category" class="stack"></div>
      </div>

      <div class="sub-card">
        <div class="sub-card-heading">Цели</div>
        <div id="goals"></div>
      </div>
    </div>`);
  container.replaceChildren(root);

  const kpiGrid = root.querySelector('#kpi-grid');
  const customRangeEl = root.querySelector('#custom-range');
  const chipsEl = root.querySelector('#period-chips');

  async function renderKpis() {
    const { startMs, endMs } = resolvePeriod(preset, tz, Date.now(), customStart, customEnd);
    const { sales, returns, expenses } = await loadPeriodData(startMs, endMs);
    const cashlessSales = Money.sum(sales.filter((s) => s.paymentMethod === 'CASHLESS').map((s) => s.totalKopecks));
    const autoFee = settings.autoAcquiringFeeEnabled ? computeAcquiringFee(cashlessSales, settings.autoAcquiringFeePercent) : null;
    const f = aggregateFinancials(sales, returns, expenses, autoFee);

    const tiles = [
      ['Выручка', Money.format(f.revenueAfterReturns), true],
      ['Валовая прибыль', Money.format(f.grossProfit), true],
      ['Чистая прибыль', Money.format(f.netProfit), true],
      ['Себестоимость', Money.format(f.costOfGoodsSold), false],
      ['Расходы', Money.format(f.operatingExpenses), false],
      ['Возвраты', Money.format(f.returnsTotal), false],
      ['Чеков', String(f.completedReceiptCount), false],
      ['Средний чек', Money.format(f.averageCheck), false],
      ['Наличные / Безнал.', `${Money.format(f.cashSales)} / ${Money.format(f.cashlessSales)}`, false],
    ];
    kpiGrid.innerHTML = tiles.map(([label, value, emph]) => `
      <div class="kpi-tile ${emph ? 'emph' : ''}"><div class="label">${label}</div><div class="value">${value}</div></div>`).join('');

    // Top products & category breakdown, computed over the same period's sale items.
    const saleIds = new Set(sales.map((s) => s.id));
    const [allSaleItems, products, categories] = await Promise.all([getAll('saleItems'), getAll('products'), getAll('categories')]);
    const relevantItems = allSaleItems.filter((i) => saleIds.has(i.saleId));
    const productById = new Map(products.map((p) => [p.id, p]));
    const categoryById = new Map(categories.map((c) => [c.id, c]));

    const revenueByProduct = new Map();
    const revenueByCategory = new Map();
    for (const item of relevantItems) {
      const netQty = item.quantity - item.returnedQuantity;
      if (netQty <= 0) continue;
      const revenue = item.unitPriceKopecks * netQty;
      const key = item.productId || item.productNameSnapshot;
      revenueByProduct.set(key, { name: item.productNameSnapshot, revenue: (revenueByProduct.get(key)?.revenue || 0) + revenue });
      const category = item.productId ? productById.get(item.productId)?.categoryId : null;
      const catName = category ? (categoryById.get(category)?.name || '—') : '—';
      revenueByCategory.set(catName, (revenueByCategory.get(catName) || 0) + revenue);
    }
    const topProducts = [...revenueByProduct.values()].sort((a, b) => b.revenue - a.revenue).slice(0, 8);
    const topProductsEl = root.querySelector('#top-products');
    topProductsEl.innerHTML = topProducts.length
      ? topProducts.map((p) => `<div class="receipt-line"><span>${escapeHtml(p.name)}</span><strong>${Money.format(p.revenue)}</strong></div>`).join('')
      : '<div class="empty-state small" style="padding:0;">Нет продаж за период</div>';

    const byCategory = [...revenueByCategory.entries()].sort((a, b) => b[1] - a[1]);
    const maxCategoryRevenue = byCategory.length ? byCategory[0][1] : 0;
    const byCategoryEl = root.querySelector('#by-category');
    byCategoryEl.innerHTML = byCategory.length
      ? byCategory.map(([name, revenue]) => `
          <div class="mt-2">
            <div class="row between"><span>${escapeHtml(name)}</span><strong>${Money.format(revenue)}</strong></div>
            <div class="progress mt-1"><div style="width:${maxCategoryRevenue ? Math.round((revenue / maxCategoryRevenue) * 100) : 0}%;"></div></div>
          </div>`).join('')
      : '<div class="empty-state small" style="padding:0;">Нет данных за период</div>';
  }

  async function renderWeekChart() {
    const today = todayDateString(tz);
    const days = weekDates(today);
    const dayNames = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
    const revenues = [];
    for (const d of days) {
      const { startMs, endMs } = dayRange(d, tz);
      const { sales, returns } = await loadPeriodData(startMs, endMs);
      const f = aggregateFinancials(sales, returns, [], null);
      revenues.push(f.revenueAfterReturns);
    }
    const max = Math.max(1, ...revenues);
    const chartEl = root.querySelector('#week-chart');
    chartEl.innerHTML = `
      <div class="week-chart-row">
        ${revenues.map((r, i) => `
          <div class="week-chart-col">
            <div class="week-chart-bar ${days[i] === today ? 'today' : ''}" style="height:${Math.max(4, Math.round((r / max) * 90))}px;"></div>
            <span class="muted small">${dayNames[i]}</span>
          </div>`).join('')}
      </div>
      <div class="muted small mt-2">Итого за неделю: ${Money.format(Money.sum(revenues))}</div>`;
  }

  async function renderGoals() {
    const goals = await getAll('goals');
    const today = todayDateString(tz);
    const monthKey = today.slice(0, 7);
    const dailyGoal = goals.find((g) => g.id === `DAILY|${today}`);
    const monthlyGoal = goals.find((g) => g.id === `MONTHLY|${monthKey}`);

    const { startMs: dayStart, endMs: dayEnd } = dayRange(today, tz);
    const { startMs: monthStart, endMs: monthEnd } = resolvePeriod('THIS_MONTH', tz);
    const [todayData, monthData] = await Promise.all([loadPeriodData(dayStart, dayEnd), loadPeriodData(monthStart, monthEnd)]);
    const todayRevenue = aggregateFinancials(todayData.sales, todayData.returns, [], null).revenueAfterReturns;
    const monthRevenue = aggregateFinancials(monthData.sales, monthData.returns, [], null).revenueAfterReturns;

    function goalRow(label, goal, actual, type, periodKey) {
      const target = goal?.targetKopecks || 0;
      const pct = target > 0 ? Math.min(100, Math.round((actual / target) * 100)) : 0;
      return `
        <div class="mt-3" style="padding-bottom:10px;border-bottom:1px solid var(--color-divider);">
          <div class="row between"><span>${label}</span><span class="muted small">${Money.format(actual)}${target ? ` из ${Money.format(target)}` : ''}</span></div>
          ${target ? `<div class="progress mt-1"><div style="width:${pct}%;"></div></div>` : ''}
          <div class="row gap-2 mt-2">
            <input class="input" data-goal-input="${type}|${periodKey}" placeholder="Цель, ₽" value="${target ? (target / 100).toFixed(2) : ''}" style="max-width:160px;" />
            <button class="btn btn-ghost" data-goal-save="${type}|${periodKey}" style="border:1px solid var(--color-border);">Сохранить</button>
          </div>
        </div>`;
    }

    const goalsEl = root.querySelector('#goals');
    goalsEl.innerHTML = goalRow('Дневная цель', dailyGoal, todayRevenue, 'DAILY', today) + goalRow('Месячная цель', monthlyGoal, monthRevenue, 'MONTHLY', monthKey);
    goalsEl.querySelectorAll('[data-goal-save]').forEach((btn) => btn.addEventListener('click', async () => {
      const [type, periodKey] = btn.dataset.goalSave.split('|');
      const input = goalsEl.querySelector(`[data-goal-input="${type}|${periodKey}"]`);
      const target = Money.fromRubleString(input.value);
      if (target == null || target < 0) { showToast('Укажите корректную сумму цели'); return; }
      await setGoal(type, periodKey, target, Date.now());
      showToast('Цель сохранена');
      renderGoals();
    }));
  }

  chipsEl.querySelectorAll('[data-preset]').forEach((chip) => chip.addEventListener('click', () => {
    preset = chip.dataset.preset;
    chipsEl.querySelectorAll('[data-preset]').forEach((c) => c.classList.toggle('active', c.dataset.preset === preset));
    customRangeEl.classList.toggle('hidden', preset !== 'CUSTOM');
    if (preset !== 'CUSTOM') renderKpis();
  }));
  root.querySelector('#apply-custom').addEventListener('click', () => {
    customStart = root.querySelector('#custom-start').value || customStart;
    customEnd = root.querySelector('#custom-end').value || customEnd;
    renderKpis();
  });

  await Promise.all([renderKpis(), renderWeekChart(), renderGoals()]);
});
