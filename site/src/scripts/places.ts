// The location picker: remembers the visitor's place, searches the app's city list (loaded on first
// open), and finds the nearest city to the device's position.
import { JAKARTA, type Place } from "../lib/day";
import type { CityData, CityRow } from "../lib/cities";

export interface PickerStrings {
  lang: string;
  locating: string;
  locationFailed: string;
  noResults: string;
  myLocation: string;
}

const KEY = "place:v1";

export function savedPlace(): Place {
  try {
    const p = JSON.parse(localStorage.getItem(KEY) ?? "null") as Place | null;
    if (p && typeof p.lat === "number" && typeof p.lng === "number" && typeof p.zone === "string") return p;
  } catch {}
  return JAKARTA;
}

function remember(p: Place) {
  try {
    localStorage.setItem(KEY, JSON.stringify(p));
  } catch {}
}

/** Lowercase, no diacritics, straight apostrophes: "Ta’if" and "taif" both match. */
const norm = (v: string) =>
  v.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[’‘`]/g, "'").toLowerCase().trim();

interface Entry {
  row: CityRow;
  keys: string[];
}

let data: Promise<{ raw: CityData; entries: Entry[] }> | undefined;
const load = () =>
  (data ??= fetch("/cities.json")
    .then((r) => r.json() as Promise<CityData>)
    .then((raw) => ({
      raw,
      entries: raw.cities.map((row) => ({ row, keys: [row[0], ...row[1].split("|").filter(Boolean)].map(norm) })),
    })));

function rank(keys: string[], q: string): number {
  let best = Infinity;
  keys.forEach((k, i) => {
    const alias = i > 0 ? 0.5 : 0;
    if (k.startsWith(q)) best = Math.min(best, 0 + alias);
    else if (k.split(/[\s\-']/).some((w) => w.startsWith(q))) best = Math.min(best, 1 + alias);
    else if (k.includes(q)) best = Math.min(best, 2 + alias);
  });
  return best;
}

export function initPicker(s: PickerStrings, onPick: (place: Place) => void) {
  const dialog = document.querySelector<HTMLDialogElement>("[data-place-dialog]");
  const input = document.querySelector<HTMLInputElement>("[data-place-input]");
  const list = document.querySelector<HTMLUListElement>("[data-place-results]");
  const status = document.querySelector<HTMLElement>("[data-place-status]");
  if (!dialog || !input || !list || !status) return;

  const countries = new Intl.DisplayNames([s.lang], { type: "region" });
  const detail = (raw: CityData, row: CityRow) => {
    const [cc = "", state = ""] = (raw.regions[row[2]] ?? "").split("|");
    const country = cc ? (countries.of(cc) ?? cc) : "";
    return [state, country].filter(Boolean).join(", ");
  };
  const toPlace = (raw: CityData, row: CityRow): Place => ({
    name: row[0],
    detail: detail(raw, row),
    lat: row[3],
    lng: row[4],
    zone: raw.zones[row[5]] ?? "UTC",
  });

  const choose = (p: Place) => {
    remember(p);
    onPick(p);
    dialog.close();
  };

  const render = async () => {
    const q = norm(input.value);
    list.replaceChildren();
    input.setAttribute("aria-expanded", "false");
    if (!q) return void (status.textContent = "");
    const { raw, entries } = await load();
    if (norm(input.value) !== q) return; // a newer keystroke has taken over
    const hits = entries
      .map((e, i) => ({ e, i, r: rank(e.keys, q) }))
      .filter((h) => h.r < Infinity)
      .sort((a, b) => a.r - b.r || a.i - b.i)
      .slice(0, 8);
    status.textContent = hits.length ? "" : s.noResults;
    input.setAttribute("aria-expanded", String(hits.length > 0));
    for (const { e } of hits) {
      const place = toPlace(raw, e.row);
      const li = document.createElement("li");
      li.setAttribute("role", "option");
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "flex w-full flex-col rounded-xl px-3 py-2.5 text-left hover:bg-paper focus-visible:bg-paper";
      btn.innerHTML = `<span class="font-semibold"></span><span class="text-sm text-ink-soft"></span>`;
      btn.children[0]!.textContent = place.name;
      btn.children[1]!.textContent = place.detail ?? "";
      btn.addEventListener("click", () => choose(place));
      li.append(btn);
      list.append(li);
    }
  };

  let debounce = 0;
  input.addEventListener("input", () => {
    clearTimeout(debounce);
    debounce = window.setTimeout(render, 90);
  });
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") list.querySelector("button")?.click();
    if (e.key === "ArrowDown") (list.querySelector("button") as HTMLButtonElement | null)?.focus(), e.preventDefault();
  });
  list.addEventListener("keydown", (e) => {
    const items = [...list.querySelectorAll("button")];
    const i = items.indexOf(document.activeElement as HTMLButtonElement);
    if (e.key === "ArrowDown") items[Math.min(i + 1, items.length - 1)]?.focus();
    else if (e.key === "ArrowUp") (i <= 0 ? input : items[i - 1])?.focus();
    else return;
    e.preventDefault();
  });

  document.querySelector("[data-place-locate]")?.addEventListener("click", () => {
    if (!("geolocation" in navigator)) return void (status.textContent = s.locationFailed);
    status.textContent = s.locating;
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        const { raw } = await load();
        // Nearest known city names the place; the device's own time zone is the reliable one.
        const cos = Math.cos((coords.latitude * Math.PI) / 180);
        let best: CityRow | undefined;
        let bestD = Infinity;
        for (const row of raw.cities) {
          const d = (row[3] - coords.latitude) ** 2 + ((row[4] - coords.longitude) * cos) ** 2;
          if (d < bestD) [best, bestD] = [row, d];
        }
        const near = best && Math.sqrt(bestD) * 111 < 60 ? toPlace(raw, best) : undefined;
        choose({
          name: near?.name ?? s.myLocation,
          ...(near?.detail ? { detail: near.detail } : {}),
          lat: Number(coords.latitude.toFixed(4)),
          lng: Number(coords.longitude.toFixed(4)),
          zone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        });
      },
      () => (status.textContent = s.locationFailed),
      { timeout: 10_000, maximumAge: 600_000 },
    );
  });

  document.querySelector("[data-place-close]")?.addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (e) => e.target === dialog && dialog.close()); // backdrop
  document.querySelector("[data-place-open]")?.addEventListener("click", () => {
    status.textContent = "";
    dialog.showModal();
    input.select();
    void load(); // warm the list while the visitor types
  });
}
