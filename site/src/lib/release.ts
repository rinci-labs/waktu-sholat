// The latest GitHub release, read once per build so the page ships with real numbers. The browser
// refreshes it later (see scripts/live.ts), so a release published after the build still shows.
import { REPO } from "./links";

export interface Release {
  version: string;
  sizeBytes: number;
  publishedAt: string;
  sha256: string | null;
  url: string;
}

interface GhAsset {
  name: string;
  size: number;
  digest?: string | null;
  browser_download_url: string;
}
interface GhRelease {
  tag_name: string;
  published_at: string;
  assets: GhAsset[];
}

export const RELEASE_API = `https://api.github.com/repos/${REPO}/releases/latest`;

export function parseRelease(data: GhRelease): Release | null {
  const apk = data.assets.find((a) => a.name === "waktu-sholat.apk") ?? data.assets.find((a) => a.name.endsWith(".apk"));
  if (!apk) return null;
  return {
    version: data.tag_name.replace(/^v/, ""),
    sizeBytes: apk.size,
    publishedAt: data.published_at,
    sha256: apk.digest?.startsWith("sha256:") ? apk.digest.slice(7) : null,
    url: apk.browser_download_url,
  };
}

let cached: Promise<Release | null> | undefined;

/** Never fails the build: offline or rate-limited builds just render without release details. */
export function latestRelease(): Promise<Release | null> {
  cached ??= fetch(RELEASE_API, { headers: { Accept: "application/vnd.github+json" }, signal: AbortSignal.timeout(8000) })
    .then((r) => (r.ok ? (r.json() as Promise<GhRelease>) : null))
    .then((d) => (d ? parseRelease(d) : null))
    .catch(() => null);
  return cached;
}

export const formatSize = (bytes: number): string => `${Math.round(bytes / 1024)} KB`;

export const formatDate = (iso: string, lang: string): string =>
  new Date(iso).toLocaleDateString(lang === "id" ? "id-ID" : "en-GB", { day: "numeric", month: "long", year: "numeric" });
