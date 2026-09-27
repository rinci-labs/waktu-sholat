// Keeps the static page current: the sky follows the time at the chosen place (Jakarta until the
// visitor picks another), the schedule card and day strip tick every minute, and release details
// stay fresh.
import { fmt, P } from "../lib/prayer";
import { at, celestial, dayAt, dayGradient, nextPrayer, periodOf, type Day, type Place } from "../lib/day";
import { RELEASE_API, formatDate, formatSize, parseRelease, type Release } from "../lib/release";
import { initPicker, savedPlace, type PickerStrings } from "./places";

export interface ClientStrings extends PickerStrings {
  prayers: string[];
  weekdays: string[];
  months: string[];
  inTime: string;
  hours: string;
  hoursOnly: string;
  minutes: string;
  lessMinute: string;
}

const $ = <T extends Element = HTMLElement>(sel: string, root: ParentNode = document) => root.querySelector<T>(sel);
const $$ = <T extends Element = HTMLElement>(sel: string, root: ParentNode = document) => [...root.querySelectorAll<T>(sel)];
const fill = (template: string, values: Record<string, string | number>) =>
  template.replace(/\{(\w+)\}/g, (_, k: string) => String(values[k] ?? ""));

const s = JSON.parse($("#strings")?.textContent ?? "{}") as ClientStrings;
const sky = $("[data-sky]");
const body = $("[data-sky-body]");
let place: Place = savedPlace();

function paintSky(day: Day) {
  if (!sky || !body) return;
  sky.dataset.period = periodOf(day);
  const pos = celestial(day);
  const q = Math.min(Math.max(pos.progress, 0), 1);
  body.style.setProperty("--q", q.toFixed(3));
  body.style.setProperty("--arc", Math.sin(Math.PI * q).toFixed(3));
  body.toggleAttribute("data-moon", pos.moon);
}

function countdown(minutes: number): string {
  if (minutes < 1) return s.lessMinute;
  const [h, m] = [Math.floor(minutes / 60), minutes % 60];
  const span = h === 0 ? fill(s.minutes, { m }) : m === 0 ? fill(s.hoursOnly, { h }) : fill(s.hours, { h, m });
  return fill(s.inTime, { t: span });
}

function renderCard(day: Day) {
  const card = $("[data-today]");
  if (!card) return;
  const next = nextPrayer(day);
  const passed = [P.Fajr, P.Sunrise, P.Dhuhr, P.Asr, P.Maghrib, P.Isha].filter((i) => day.times[i] <= day.minute);
  const last = passed.at(-1);
  const from = last === undefined ? day.times[P.Isha] - 1440 : day.times[last];
  const to = day.minute + next.inMinutes;

  $("[data-date]", card)!.textContent = `${s.weekdays[day.weekday]}, ${day.day} ${s.months[day.month - 1]} ${day.year}`;
  $("[data-next-name]", card)!.textContent = s.prayers[next.index] ?? "";
  $("[data-next-time]", card)!.textContent = fmt(next.at);
  $("[data-countdown]", card)!.textContent = countdown(next.inMinutes);
  $("[data-progress]", card)!.style.transform = `scaleX(${Math.min(Math.max((day.minute - from) / (to - from), 0), 1).toFixed(4)})`;
  for (const row of $$("[data-row]", card)) {
    const i = Number(row.dataset.row);
    row.dataset.state = i === next.index ? "next" : day.times[i]! <= day.minute ? "past" : "later";
    $("[data-time]", row)!.textContent = fmt(day.times[i]!);
  }
}

function renderStrip(day: Day) {
  const strip = $("[data-strip]");
  if (strip) strip.style.background = dayGradient(day.times);
  const now = $("[data-now]");
  if (now) now.style.left = at(day.minute);
  const nowTime = $("[data-now-time]");
  if (nowTime) nowTime.textContent = fmt(day.minute);
  for (const mark of $$("[data-mark]")) {
    const m = day.times[Number(mark.dataset.mark)]!;
    if (mark.classList.contains("day-mark")) mark.style.setProperty("--at", at(m));
    $("[data-time]", mark)!.textContent = fmt(m);
  }
}

function tick() {
  const day = dayAt(place);
  for (const el of $$("[data-place-name]")) el.textContent = place.name;
  paintSky(day);
  renderCard(day);
  renderStrip(day);
}

// Re-render on each minute boundary, and right away when the tab comes back.
let timer = 0;
function schedule() {
  clearTimeout(timer);
  tick();
  timer = window.setTimeout(schedule, 60_000 - (Date.now() % 60_000) + 50);
}
document.addEventListener("visibilitychange", () => document.visibilityState === "visible" && schedule());
schedule();

initPicker(s, (p) => {
  place = p;
  schedule();
});

// Header: transparent over the sky, solid once the page has scrolled.
const nav = $("[data-nav]");
if (nav) {
  let queued = false;
  const update = () => {
    queued = false;
    nav.toggleAttribute("data-scrolled", window.scrollY > 24);
  };
  window.addEventListener("scroll", () => !queued && (queued = requestAnimationFrame(update) > 0), { passive: true });
  update();
}

// Release details: the build already has them; refresh in case a newer release came out since.
function renderRelease(r: Release) {
  const set = (key: string, value: string) => $$(`[data-release="${key}"]`).forEach((el) => (el.textContent = value));
  set("version", r.version);
  set("size", formatSize(r.sizeBytes));
  set("date", formatDate(r.publishedAt, s.lang));
  if (r.sha256) set("sha", r.sha256);
  set("tag", `v${r.version}`);
}

const CACHE = "release:v1";
(async () => {
  try {
    const cached = sessionStorage.getItem(CACHE);
    if (cached) return renderRelease(JSON.parse(cached) as Release);
  } catch {}
  try {
    const res = await fetch(RELEASE_API, { headers: { Accept: "application/vnd.github+json" } });
    if (!res.ok) return;
    const r = parseRelease(await res.json());
    if (!r) return;
    renderRelease(r);
    try {
      sessionStorage.setItem(CACHE, JSON.stringify(r));
    } catch {}
  } catch {}
})();
