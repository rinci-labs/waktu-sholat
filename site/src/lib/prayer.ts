// Kemenag prayer times, ported from the app's core/PrayerCalculator.kt (PrayTimes algorithm with
// per-prayer sun refinement, Kemenag angles 20°/18°, official rounding and ihtiyati margins).

/** Minutes after local midnight, in order: imsak, fajr, sunrise, dhuhr, asr, maghrib, isha. */
export type Times = readonly [number, number, number, number, number, number, number];

/** Indexes into [Times]. */
export const P = { Imsak: 0, Fajr: 1, Sunrise: 2, Dhuhr: 3, Asr: 4, Maghrib: 5, Isha: 6 } as const;
export type P = (typeof P)[keyof typeof P];

const RAD = Math.PI / 180;
const DEG = 180 / Math.PI;
const fix = (v: number, r: number): number => v - r * Math.floor(v / r);

export function kemenagTimes(year: number, month: number, day: number, lat: number, lng: number, tzHours: number): Times {
  const jDate = Math.floor(Date.UTC(year, month - 1, day) / 86_400_000) + 2440587.5;

  const sun = (jd: number) => {
    const d = jd - 2451545.0;
    const g = fix(357.529 + 0.98560028 * d, 360);
    const q = fix(280.459 + 0.98564736 * d, 360);
    const l = fix(q + 1.915 * Math.sin(g * RAD) + 0.02 * Math.sin(2 * g * RAD), 360);
    const e = 23.439 - 0.00000036 * d;
    const ra = fix((Math.atan2(Math.cos(e * RAD) * Math.sin(l * RAD), Math.cos(l * RAD)) * DEG) / 15, 24);
    return { decl: Math.asin(Math.sin(e * RAD) * Math.sin(l * RAD)) * DEG, eqt: q / 15 - ra };
  };
  const midDay = (portion: number) => fix(12 - sun(jDate + portion).eqt, 24);
  const angleTime = (alt: number, portion: number, ccw: boolean) => {
    const { decl } = sun(jDate + portion);
    const cosH =
      (Math.sin(alt * RAD) - Math.sin(decl * RAD) * Math.sin(lat * RAD)) / (Math.cos(decl * RAD) * Math.cos(lat * RAD));
    const t = (Math.acos(Math.min(1, Math.max(-1, cosH))) * DEG) / 15;
    return midDay(portion) + (ccw ? -t : t);
  };

  const asrDecl = sun(jDate + 13 / 24).decl;
  const asrAlt = Math.atan(1 / (1 + Math.tan(Math.abs(lat - asrDecl) * RAD))) * DEG;
  const shift = tzHours - lng / 15;
  const [fajr, sunrise, dhuhr, asr, maghrib, isha] = [
    angleTime(-20, 5 / 24, true),
    angleTime(-0.833, 6 / 24, true),
    midDay(12 / 24),
    angleTime(asrAlt, 13 / 24, false),
    angleTime(-0.833, 18 / 24, false),
    angleTime(-18, 18 / 24, false),
  ].map((h) => (h + shift) * 60) as [number, number, number, number, number, number];

  const ceil = (x: number) => Math.ceil(x - 1e-9);
  const round = (x: number) => Math.floor(x + 0.5);
  const wrap = (m: number) => ((m % 1440) + 1440) % 1440;
  return [
    wrap(ceil(fajr - 10) + 2),
    wrap(ceil(fajr) + 2),
    wrap(round(sunrise) - 3),
    wrap(ceil(dhuhr) + 3),
    wrap(ceil(asr) + 2),
    wrap(round(maghrib) + 3),
    wrap(ceil(isha) + 2),
  ];
}

export const fmt = (m: number): string =>
  `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
