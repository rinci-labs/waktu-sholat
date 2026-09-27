// Renders the page for deployment: fills in the site URL and app version, and writes the
// sitemap and robots.txt. Set SITE_URL (e.g. in Cloudflare Pages) when using a custom domain.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const siteUrl = (process.env.SITE_URL || "https://waktu-sholat.rafaar.com").replace(/\/+$/, "");

// The app's default versionName, from the Android build in the same repository.
let version = "";
try {
  const gradle = readFileSync(join(root, "..", "app", "build.gradle.kts"), "utf8");
  version = gradle.match(/gradleProperty\("versionName"\)\.getOrElse\("([^"]+)"\)/)?.[1] ?? "";
} catch {
  // Building the site outside the monorepo: leave the version out.
}

const html = readFileSync(join(root, "src", "index.html"), "utf8")
  .replaceAll("%SITE_URL%", siteUrl)
  .replaceAll("%VERSION%", version);
writeFileSync(join(root, "public", "index.html"), html);

const today = new Date().toISOString().slice(0, 10);
writeFileSync(
  join(root, "public", "sitemap.xml"),
  `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>${siteUrl}/</loc>
    <lastmod>${today}</lastmod>
    <changefreq>weekly</changefreq>
  </url>
</urlset>
`,
);
writeFileSync(join(root, "public", "robots.txt"), `User-agent: *\nAllow: /\n\nSitemap: ${siteUrl}/sitemap.xml\n`);

console.log(`Rendered for ${siteUrl}${version ? ` (app ${version})` : ""}`);
