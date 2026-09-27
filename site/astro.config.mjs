import { defineConfig } from "astro/config";
import tailwindcss from "@tailwindcss/vite";

// Set SITE_URL in Cloudflare Pages to override the production domain.
export default defineConfig({
  site: process.env.SITE_URL || "https://waktu-sholat.rafaar.com",
  trailingSlash: "always",
  build: { inlineStylesheets: "always" },
  vite: { plugins: [tailwindcss()] },
});
