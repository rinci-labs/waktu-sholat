// The page's model of a day at one place: its prayer times, which part of the day it is, where the
// sun or moon sits, and the 24-hour colour strip. Shared by the build (first paint) and the browser.
import { kemenagTimes, P, type Times } from "./prayer";

export const PERIODS = ["subuh", "pagi", "siang", "sore", "senja", "malam"] as const;
export type Period = (typeof PERIODS)[number];

export interface Place {
  name: string;
  /** Region and country, shown under the name in the picker. */
  detail?: string;
  lat: number;
  lng: number;
  /** IANA time zone. */
  zone: string;
}

export const JAKARTA: Place = { name: "Jakarta", detail: "DKI Jakarta", lat: -6.2088, lng: 106.8456, zone: "Asia/Jakarta" };

/** Sky colours per period (top, bottom), identical to the app's Period enum. */
export const SKY: Record<Period, readonly [string, string]> = {
  subuh: ["#1b2350", "#8e4a6b"],
  pagi: ["#1f5faf", "#4a98da"],
  siang: ["#155e9c", "#3b8fcf"],
  sore: ["#9a4a1e", "#d08236"],
  senja: ["#2b1650", "#a8465f"],
  malam: ["#070b1c", "#1b2752"],
};

export interface Day {
  year: number;
  month: number;
  day: number;
  weekday: number;
  /** Minutes after local midnight at the place. */
  minute: number;
  times: Times;
  tomorrow: Times;
}

const clocks = new Map<string, Intl.DateTimeFormat>();

/** Wall clock at [zone] for an instant, and that zone's UTC offset in hours at the time. */
function wallClock(zone: string, at: Date) {
  let f = clocks.get(zone);
  if (!f) {
    f = new Intl.DateTimeFormat("en-US", {
      timeZone: zone, hourCycle: "h23", year: "numeric", month: "numeric", day: "numeric", hour: "numeric", minute: "numeric",
    });
    clocks.set(zone, f);
  }
  const parts: Record<string, number> = {};
  for (const p of f.formatToParts(at)) if (p.type !== "literal") parts[p.type] = Number(p.value);
  const { year = 1970, month = 1, day = 1, hour = 0, minute = 0 } = parts;
  const wall = Date.UTC(year, month - 1, day, hour, minute);
  const offset = Math.round((wall - Math.floor(at.getTime() / 60_000) * 60_000) / 60_000) / 60;
  return { year, month, day, minute: hour * 60 + minute, offset, weekday: new Date(wall).getUTCDay() };
}

export function dayAt(place: Place, at: Date = new Date()): Day {
  const now = wallClock(place.zone, at);
  const next = wallClock(place.zone, new Date(at.getTime() + 86_400_000));
  const calc = (c: typeof now) => kemenagTimes(c.year, c.month, c.day, place.lat, place.lng, c.offset);
  return { year: now.year, month: now.month, day: now.day, weekday: now.weekday, minute: now.minute, times: calc(now), tomorrow: calc(next) };
}

/** Bounded by the prayers themselves, as in the app: the sky turns to dusk exactly at Maghrib. */
export function periodOf({ times: t, minute: m }: Day): Period {
  if (m < t[P.Fajr]) return "malam";
  if (m < t[P.Sunrise]) return "subuh";
  if (m < t[P.Dhuhr]) return "pagi";
  if (m < t[P.Asr]) return "siang";
  if (m < t[P.Maghrib]) return "sore";
  if (m < t[P.Isha]) return "senja";
  return "malam";
}

/** The prayer shown as "next", skipping imsak; after Isha it is tomorrow's Fajr. */
export function nextPrayer({ times, tomorrow, minute }: Day): { index: P; at: number; inMinutes: number } {
  for (const i of [P.Fajr, P.Sunrise, P.Dhuhr, P.Asr, P.Maghrib, P.Isha]) {
    if (times[i] > minute) return { index: i, at: times[i], inMinutes: times[i] - minute };
  }
  return { index: P.Fajr, at: tomorrow[P.Fajr], inMinutes: tomorrow[P.Fajr] + 1440 - minute };
}

/** Where the body is on its arc, 0 at the eastern horizon and 1 at the western one (app's Celestial). */
export function celestial({ times: t, tomorrow, minute: m }: Day): { progress: number; moon: boolean } {
  const [sunrise, sunset] = [t[P.Sunrise], t[P.Maghrib]];
  if (m >= t[P.Fajr] && m < sunset) return { progress: (m - sunrise) / (sunset - sunrise), moon: false };
  const after = m < t[P.Fajr];
  const fajr = (after ? t[P.Fajr] : tomorrow[P.Fajr]) + 1440;
  return { progress: ((after ? m + 1440 : m) - sunset) / (fajr - sunset), moon: true };
}

const pct = (m: number) => `${((m / 1440) * 100).toFixed(2)}%`;

/** 24 hours as a horizontal gradient in the colours of each part of the day. */
export function dayGradient(t: Times): string {
  const n = SKY.malam, s = SKY.subuh, p = SKY.pagi, d = SKY.siang, a = SKY.sore, j = SKY.senja;
  const stops: [string, number][] = [
    [n[0], 0],
    [n[1], t[P.Fajr] - 20],
    [s[0], t[P.Fajr]],
    [s[1], t[P.Sunrise] - 10],
    [p[1], t[P.Sunrise] + 20],
    [p[0], (t[P.Sunrise] + t[P.Dhuhr]) / 2],
    [d[1], t[P.Dhuhr]],
    [d[0], t[P.Asr] - 10],
    [a[1], t[P.Asr] + 20],
    [a[0], t[P.Maghrib] - 5],
    [j[1], t[P.Maghrib]],
    [j[0], t[P.Isha] - 10],
    [n[1], t[P.Isha] + 10],
    [n[0], 1440],
  ];
  return `linear-gradient(90deg,${stops.map(([c, m]) => `${c} ${pct(m)}`).join(",")})`;
}

export const at = pct;
