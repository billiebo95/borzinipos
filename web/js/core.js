// Business logic ported from the Android app's `core` module (see /core/src/main/kotlin/...).
// Same rules, same guarantees (no binary-float money/quantity errors, no div-by-zero, historical
// snapshots never recomputed) - just re-expressed in JS with integer/BigInt arithmetic instead
// of Kotlin's Long/BigDecimal.
import { Money, Qty } from './money.js';

// ---- Unit cost: integer micro-rubles per base unit (rubles * 1e6), BigInt math internally ----
export const UnitCost = {
  ZERO: 0,

  /** packageCost in kopecks for ONE package; unitsPerPackage in milli-units. */
  fromPackagePurchase(packageCostKopecks, packageCount, unitsPerPackageMilli) {
    if (packageCount <= 0) throw new Error('packageCount must be positive');
    if (unitsPerPackageMilli <= 0) throw new Error('unitsPerPackage must be positive');
    const numerator = BigInt(packageCostKopecks) * 10000000n;
    const denominator = BigInt(packageCount) * BigInt(unitsPerPackageMilli);
    return Number(bigIntDivRoundHalfUp(numerator, denominator));
  },

  /** Cost (in kopecks) of `quantityMilli` base units at this unit cost, rounded half-up. */
  costOf(unitCostMicro, quantityMilli) {
    const numerator = BigInt(unitCostMicro) * BigInt(quantityMilli);
    return Number(bigIntDivRoundHalfUp(numerator, 10000000n));
  },
};

function bigIntDivRoundHalfUp(numerator, denominator) {
  const sign = (numerator < 0n) !== (denominator < 0n) ? -1n : 1n;
  const absN = numerator < 0n ? -numerator : numerator;
  const absD = denominator < 0n ? -denominator : denominator;
  return sign * ((absN + absD / 2n) / absD);
}

/**
 * newAvgCostMicro = (Q1*C1 + Q2*C2) / (Q1+Q2) - see docs/PWA_NOTES or the Kotlin core module for
 * the dimensional-analysis derivation of why the milli/micro scales cancel out exactly like this.
 * A non-positive current quantity (stock was at/below zero, e.g. under the "allow negative stock"
 * setting) discards the stale average instead of dividing by it.
 */
export function weightedAverageCost(currentQtyMilli, currentAvgCostMicro, incomingQtyMilli, incomingCostMicro) {
  if (incomingQtyMilli <= 0) throw new Error('incomingQuantity must be positive for a purchase');
  if (currentQtyMilli <= 0) return incomingCostMicro;
  const q1 = BigInt(currentQtyMilli), c1 = BigInt(currentAvgCostMicro);
  const q2 = BigInt(incomingQtyMilli), c2 = BigInt(incomingCostMicro);
  const totalValue = q1 * c1 + q2 * c2;
  const totalQty = q1 + q2;
  return Number(bigIntDivRoundHalfUp(totalValue, totalQty));
}

/** lines: [{ quantityPerPortionMilli, unitCostMicro }]. Each line rounds to the nearest kopeck first, then sums. */
export function recipeCostPerPortion(lines) {
  return Money.sum(lines.map((l) => UnitCost.costOf(l.unitCostMicro, l.quantityPerPortionMilli)));
}

export function marginPercent(priceKopecks, costKopecks) {
  if (priceKopecks === 0) return null;
  const grossProfit = priceKopecks - costKopecks;
  // one decimal place, half-up, all integer math: percent*10 = grossProfit*1000/price
  const numerator = BigInt(grossProfit) * 1000n;
  const tenths = bigIntDivRoundHalfUp(numerator, BigInt(priceKopecks));
  return Number(tenths) / 10;
}

// ---- Cash payment ----
export function evaluateCashPayment(totalKopecks, receivedKopecks) {
  if (receivedKopecks < totalKopecks) {
    return { ok: false, shortfall: totalKopecks - receivedKopecks };
  }
  return { ok: true, change: receivedKopecks - totalKopecks };
}

// ---- Inventory math ----
export function deductStock(onHandMilli, requiredMilli, allowNegative) {
  const remaining = onHandMilli - requiredMilli;
  if (remaining < 0 && !allowNegative) {
    return { ok: false, available: onHandMilli, required: requiredMilli };
  }
  return { ok: true, newOnHandMilli: remaining };
}

export function inventoryCountDifference(onHandMilli, countedMilli) {
  return countedMilli - onHandMilli;
}

// ---- Returns ----
export function validateReturn(soldQty, alreadyReturnedQty, requestedQty) {
  if (requestedQty <= 0) return { ok: false, reason: 'invalid' };
  const remaining = soldQty - alreadyReturnedQty;
  if (requestedQty > remaining) return { ok: false, reason: 'exceeds', maxAllowed: remaining };
  return { ok: true };
}

/** Estimated acquiring fee (kopecks) on a cashless-sales base, for a percent with at most one
 * decimal place (e.g. 1.8). Integer math only: percent*10 ("tenths") avoids float drift. */
export function computeAcquiringFee(baseKopecks, percent) {
  const tenths = Math.round(percent * 10);
  const product = baseKopecks * tenths;
  const sign = product < 0 ? -1 : 1;
  const abs = Math.abs(product);
  return sign * Math.floor((abs + 500) / 1000);
}

// ---- Financial aggregation ----
// sales: [{ totalKopecks, cogsKopecks, paymentMethod }]; returns: [{ refundedKopecks, paymentMethod, restockedCogsKopecks }]
// expenses: [{ amountKopecks, category }]; autoAcquiringFeeKopecks: number|null
export function aggregateFinancials(sales, returns, expenses, autoAcquiringFeeKopecks) {
  const salesBeforeReturns = Money.sum(sales.map((s) => s.totalKopecks));
  const cashSales = Money.sum(sales.filter((s) => s.paymentMethod === 'CASH').map((s) => s.totalKopecks));
  const cashlessSales = Money.sum(sales.filter((s) => s.paymentMethod === 'CASHLESS').map((s) => s.totalKopecks));

  const returnsTotal = Money.sum(returns.map((r) => r.refundedKopecks));
  const returnsCash = Money.sum(returns.filter((r) => r.paymentMethod === 'CASH').map((r) => r.refundedKopecks));
  const returnsCashless = Money.sum(returns.filter((r) => r.paymentMethod === 'CASHLESS').map((r) => r.refundedKopecks));

  const revenueAfterReturns = salesBeforeReturns - returnsTotal;
  const cogsFromSales = Money.sum(sales.map((s) => s.cogsKopecks));
  const cogsRestocked = Money.sum(returns.map((r) => r.restockedCogsKopecks || 0));
  const costOfGoodsSold = cogsFromSales - cogsRestocked;
  const grossProfit = revenueAfterReturns - costOfGoodsSold;

  const manualExpenseTotal = Money.sum(
    expenses.filter((e) => autoAcquiringFeeKopecks == null || e.category !== 'ACQUIRING_FEE').map((e) => e.amountKopecks),
  );
  const operatingExpenses = manualExpenseTotal + (autoAcquiringFeeKopecks || 0);
  const netProfit = grossProfit - operatingExpenses;

  const averageCheck = sales.length === 0 ? 0 : Math.round(salesBeforeReturns / sales.length);

  return {
    salesBeforeReturns, cashSales, cashlessSales,
    returnsTotal, returnsCash, returnsCashless,
    revenueAfterReturns, costOfGoodsSold, grossProfit,
    operatingExpenses, netProfit,
    completedReceiptCount: sales.length, averageCheck,
  };
}

// ---- Business calendar: Russian timezones are all fixed-offset (no DST since 2014) ----
export const RUSSIAN_TIMEZONES = {
  'Europe/Kaliningrad': 2, 'Europe/Moscow': 3, 'Europe/Samara': 4,
  'Asia/Yekaterinburg': 5, 'Asia/Omsk': 6, 'Asia/Novosibirsk': 7,
  'Asia/Krasnoyarsk': 7, 'Asia/Irkutsk': 8, 'Asia/Yakutsk': 9,
  'Asia/Vladivostok': 10, 'Asia/Magadan': 11, 'Asia/Kamchatka': 12,
};

function offsetMs(tz) {
  const hours = RUSSIAN_TIMEZONES[tz] ?? 3;
  return hours * 3600 * 1000;
}

/** "YYYY-MM-DD" for the given UTC instant (ms) as seen in timezone tz. */
export function localDateString(ms, tz) {
  const shifted = new Date(ms + offsetMs(tz));
  const y = shifted.getUTCFullYear();
  const m = String(shifted.getUTCMonth() + 1).padStart(2, '0');
  const d = String(shifted.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function todayDateString(tz, now = Date.now()) {
  return localDateString(now, tz);
}

function dateStringToUtcMidnight(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

/** Local midnight (dateStr, in tz) as a UTC instant in ms. */
export function dayStartMs(dateStr, tz) {
  return dateStringToUtcMidnight(dateStr) - offsetMs(tz);
}

export function addDays(dateStr, days) {
  const ms = dateStringToUtcMidnight(dateStr) + days * 86400000;
  const d = new Date(ms);
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
}

/** Monday..Sunday date strings for the week containing dateStr. */
export function weekDates(dateStr) {
  const dow = new Date(dateStringToUtcMidnight(dateStr)).getUTCDay(); // 0=Sun..6=Sat
  const diffToMonday = (dow + 6) % 7;
  const monday = addDays(dateStr, -diffToMonday);
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
}

export function dayRange(dateStr, tz) {
  const start = dayStartMs(dateStr, tz);
  return { startMs: start, endMs: start + 86400000 };
}

export function weekRange(dateStr, tz) {
  const days = weekDates(dateStr);
  return { startMs: dayStartMs(days[0], tz), endMs: dayStartMs(days[6], tz) + 86400000 };
}

export function monthRange(yearMonth /* "YYYY-MM" */, tz) {
  const [y, m] = yearMonth.split('-').map(Number);
  const startDateStr = `${y}-${String(m).padStart(2, '0')}-01`;
  const nextMonth = m === 12 ? `${y + 1}-01-01` : `${y}-${String(m + 1).padStart(2, '0')}-01`;
  return { startMs: dayStartMs(startDateStr, tz), endMs: dayStartMs(nextMonth, tz) };
}

export function resolvePeriod(preset, tz, now = Date.now(), customStart = null, customEnd = null) {
  const today = todayDateString(tz, now);
  switch (preset) {
    case 'TODAY': return dayRange(today, tz);
    case 'YESTERDAY': return dayRange(addDays(today, -1), tz);
    case 'THIS_WEEK': return weekRange(today, tz);
    case 'THIS_MONTH': return monthRange(today.slice(0, 7), tz);
    case 'CUSTOM': {
      const start = dayStartMs(customStart, tz);
      const end = dayStartMs(addDays(customEnd, 1), tz);
      return { startMs: start, endMs: end };
    }
    default: return dayRange(today, tz);
  }
}
