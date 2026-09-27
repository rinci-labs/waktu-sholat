# Waktu Sholat website

Static landing page for [waktu-sholat.rafaar.com](https://waktu-sholat.rafaar.com). `public/` is the
deploy root; the built files are committed, so it deploys with or without a build step.

```sh
npm install
npm run build   # renders src/index.html into public/ and builds public/assets/app.css
npm run dev     # same, then rebuilds the CSS on change
npm run preview # build, then serve public/ at http://localhost:4173
```

`npm run build` fills in the site URL (set `SITE_URL` to override the default,
`https://waktu-sholat.rafaar.com`) and the app version read from `../app/build.gradle.kts`, and
writes `sitemap.xml` and `robots.txt`.

## Cloudflare Pages

| Setting | Value |
|---|---|
| Root directory | `site` |
| Build command | `npm run build` |
| Build output directory | `public` |
| Environment variable (optional) | `SITE_URL=https://waktu-sholat.rafaar.com` |

Then add the custom domain `waktu-sholat.rafaar.com` under the project's Custom domains.

## Download button

It links to `releases/latest/download/waktu-sholat.apk`, a stable asset name the release workflow
uploads on every tag; the page also reads the latest release from the GitHub API to show its
version, size, date and SHA-256.

## Share image

`public/og-image.png` (1200x630) is rendered from `src/og.html`:

```sh
chrome --headless=new --hide-scrollbars --allow-file-access-from-files --window-size=1200,630 \
  --screenshot=public/og-image.png "file://$PWD/src/og.html"
```
