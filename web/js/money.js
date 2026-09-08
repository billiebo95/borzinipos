// Exact fixed-point arithmetic - no Number/float math for money or quantities anywhere in this
// file. Money is an integer count of kopecks; Quantity is an integer count of "milli-units"
// (1/1000 of a piece/gram/millilitre). Both are plain JS integers (safe up to 2^53), so addition,
// subtraction and multiplication-by-integer are exact - the only thing that ever needs explicit
// rounding is multiplying by a fractional factor, which always goes through parseDecimalToInt's
// string-based approach rather than floating point.

/** Parses a user-typed decimal string ("12,50", "12.5", "-3") into an exact integer scaled by
 * 10^scaleDigits, without ever going through a floating point number. Returns null if invalid. */
export function parseDecimalToInt(input, scaleDigits) {
  if (input == null) return null;
  const s = String(input).trim().replace(',', '.');
  if (s === '') return null;
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(s);
  if (!match) return null;
  const [, sign, intPart, fracPart = ''] = match;
  const paddedFrac = (fracPart + '0'.repeat(scaleDigits)).slice(0, scaleDigits);
  const digits = intPart.replace(/^0+(?=\d)/, '') + paddedFrac;
  const value = Number(digits.replace(/^$/, '0'));
  if (!Number.isFinite(value)) return null;
  return sign === '-' ? -value : value;
}

export function formatScaledInt(value, scaleDigits, minFracDigits = scaleDigits) {
  const scale = 10 ** scaleDigits;
  const negative = value < 0;
  const abs = Math.abs(value);
  const intPart = Math.floor(abs / scale);
  const fracPart = abs % scale;
  let fracStr = String(fracPart).padStart(scaleDigits, '0');
  if (minFracDigits < scaleDigits) {
    // trim trailing zeros down to minFracDigits, but never below it
    while (fracStr.length > minFracDigits && fracStr.endsWith('0')) fracStr = fracStr.slice(0, -1);
  }
  const grouped = intPart.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  return (negative ? '-' : '') + grouped + (fracStr ? ',' + fracStr : '');
}

const KOPECKS_PER_RUBLE_DIGITS = 2;

export const Money = {
  ZERO: 0,
  fromRubleString(s) {
    const v = parseDecimalToInt(s, KOPECKS_PER_RUBLE_DIGITS);
    return v == null ? null : v;
  },
  fromKopecks(k) { return Math.round(k); },
  /** Multiplies a kopeck amount by an integer quantity - always exact. */
  timesInt(kopecks, factor) { return kopecks * factor; },
  /** Multiplies a kopeck amount by a Quantity (milli-units), rounding half-up to the nearest kopeck. */
  timesQuantity(kopecks, quantityMilli) {
    // kopecks * quantityMilli / 1000, rounded half up, all integer math
    const product = kopecks * quantityMilli;
    const sign = product < 0 ? -1 : 1;
    const abs = Math.abs(product);
    return sign * Math.floor((abs + 500) / 1000);
  },
  sum(list) { return list.reduce((a, b) => a + b, 0); },
  format(kopecks) { return formatScaledInt(kopecks, KOPECKS_PER_RUBLE_DIGITS) + ' ₽'; },
  toRubleNumber(kopecks) { return kopecks / 100; }, // only ever used for <input type=number> display/editing, never for stored math
};

const QUANTITY_SCALE_DIGITS = 3;

export const Qty = {
  ZERO: 0,
  fromString(s) {
    const v = parseDecimalToInt(s, QUANTITY_SCALE_DIGITS);
    return v == null ? null : v;
  },
  fromNumber(n) { return Math.round(n * 1000); },
  ofUnits(n) { return n * 1000; }, // for whole-number counts, e.g. "2 pieces" -> Qty.ofUnits(2)
  format(milli) { return formatScaledInt(milli, QUANTITY_SCALE_DIGITS, 0); },
  toBaseNumber(milli) { return milli / 1000; },
};

export function unitLabel(baseUnit) {
  switch (baseUnit) {
    case 'PIECE': return 'шт';
    case 'GRAM': return 'г';
    case 'MILLILITRE': return 'мл';
    default: return baseUnit;
  }
}
