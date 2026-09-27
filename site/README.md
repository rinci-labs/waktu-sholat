# Waktu Sholat website

Landing page for [waktu-sholat.rafaar.com](https://waktu-sholat.rafaar.com), built with
[Astro](https://astro.build) and Tailwind CSS v4. Fully static: `pnpm run build` writes `dist/`.

```sh
pnpm install
pnpm dev          # http://localhost:4321, reloads on change
pnpm check      # type-check .astro and .ts files (strictest)
pnpm build      # static site into dist/
pnpm preview  # build, then serve dist/ at http://localhost:4321
```

The build fetches the latest GitHub release once, so the download section ships with the real
version, size and checksum; the browser refreshes it later in case a newer release came out.

## Layout

| Path | Contents |
|---|---|
| `src/pages/` | `/` (Indonesian), `/en/`, `sitemap.xml`, `robots.txt`, `og/` (share image source) |
| `src/components/` | One component per section; `Landing.astro` composes the page for a language |
| `src/lib/` | `prayer.ts` (Kemenag calculator ported from the app), `day.ts` (periods, sun position, day strip), `scene.ts` (landscape SVG, generated at build time), `release.ts` |
| `src/scripts/` | Browser code: `live.ts` (sky, schedule and day strip every minute), `places.ts` (location picker) |
| `src/i18n.ts` | All copy, ID and EN |
| `public/cities.json` | Every city the app knows, loaded when the location picker opens |

## City data

`public/cities.json` is generated from the app's `data/cities.csv` and
`app/src/main/assets/world_cities.tsv`, and committed so the site builds from `site/` alone.
After changing either source:

```sh
pnpm run sync-cities
```

## Cloudflare Pages

| Setting | Value |
|---|---|
| Framework preset | Astro |
| Root directory | `site` |
| Build command | `pnpm run build` |
| Build output directory | `dist` |
| Environment variable (optional) | `SITE_URL=https://waktu-sholat.rafaar.com` |

Then add the custom domain `waktu-sholat.rafaar.com` under the project's Custom domains.
`public/_headers` sets security headers and long caching for `/_astro/*`.

## Share image

`public/og-image.png` (1200x630) is a screenshot of `/og/`:

```sh
pnpm preview   # in one terminal
chrome --headless=new --hide-scrollbars --window-size=1200,630 --virtual-time-budget=5000 \
  --screenshot=public/og-image.png http://localhost:4321/og/
```
