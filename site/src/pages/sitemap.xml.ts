import type { APIRoute } from "astro";

// Two pages, each listing the other as its alternate language.
export const GET: APIRoute = ({ site }) => {
  const id = new URL("/", site).href;
  const en = new URL("/en/", site).href;
  const alt = `<xhtml:link rel="alternate" hreflang="id" href="${id}"/><xhtml:link rel="alternate" hreflang="en" href="${en}"/><xhtml:link rel="alternate" hreflang="x-default" href="${id}"/>`;
  const lastmod = new Date().toISOString().slice(0, 10);
  const urls = [id, en].map((loc) => `<url><loc>${loc}</loc><lastmod>${lastmod}</lastmod>${alt}</url>`).join("");
  return new Response(
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">${urls}</urlset>\n`,
    { headers: { "Content-Type": "application/xml; charset=utf-8" } },
  );
};
