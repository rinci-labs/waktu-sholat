// The hero landscape, drawn at build time: the same layered hills, mosque, palms and distant town as
// the app's SkyView, emitted as static SVG markup so the browser has nothing to compute.

export const W = 1440;
export const H = 420;
const STEPS = 144;

type Pt = readonly [number, number];
const n = (v: number) => v.toFixed(1);

function ridge(base: number, amp: number, waves: number, phase: number, flat?: { cx: number; half: number; level: number }): Pt[] {
  return Array.from({ length: STEPS + 1 }, (_, i) => {
    const x = (W * i) / STEPS;
    const a = (i / STEPS) * waves * 2 * Math.PI + phase;
    let y = base - amp * (0.6 * Math.sin(a) + 0.4 * Math.sin(a * 0.53 + 1.3));
    if (flat) {
      const d = Math.abs(x - flat.cx);
      if (d < flat.half) {
        const t = (1 - d / flat.half) ** 2;
        y = y * (1 - t) + flat.level * t;
      }
    }
    return [x, y] as const;
  });
}

const outline = (pts: Pt[]) => `M0 ${H + 1}${pts.map(([x, y]) => `L${n(x)} ${n(y)}`).join("")}L${W} ${H + 1}Z`;

function yAt(pts: Pt[], x: number): number {
  const i = Math.min(Math.floor((x / W) * STEPS), STEPS - 1);
  const [x0, y0] = pts[i]!;
  const [x1, y1] = pts[i + 1]!;
  return y0 + ((y1 - y0) * (x - x0)) / (x1 - x0);
}

function mosque(cx: number, base: number, k: number): { body: string; windows: string } {
  const X = (v: number) => n(cx + v * k);
  const Y = (v: number) => n(base - v * k);
  const rect = (x: number, y: number, w: number, h: number, extra = "") =>
    `<rect x="${X(x)}" y="${Y(y)}" width="${n(w * k)}" height="${n(h)}"${extra}/>`;
  const out: string[] = [
    rect(-34, 22, 68, 22 * k + 20),
    rect(-22, 27, 44, 6 * k),
    `<path d="M${X(-22)} ${Y(27)}C${X(-25)} ${Y(47)} ${X(-9)} ${Y(54)} ${X(0)} ${Y(61)}C${X(9)} ${Y(54)} ${X(25)} ${Y(47)} ${X(22)} ${Y(27)}Z"/>`,
    rect(-0.7, 68, 1.4, 8 * k),
  ];
  // Crescent finial: a disc with a smaller offset disc cut out.
  const [r1, r2] = [3.2 * k, 2.7 * k];
  const [c1x, c1y, c2x, c2y] = [cx, base - 71 * k, cx + 1.4 * k, base - 71.8 * k];
  out.push(
    `<path fill-rule="evenodd" d="M${n(c1x - r1)} ${n(c1y)}a${n(r1)} ${n(r1)} 0 1 0 ${n(2 * r1)} 0a${n(r1)} ${n(r1)} 0 1 0 ${n(-2 * r1)} 0ZM${n(c2x - r2)} ${n(c2y)}a${n(r2)} ${n(r2)} 0 1 1 ${n(2 * r2)} 0a${n(r2)} ${n(r2)} 0 1 1 ${n(-2 * r2)} 0Z"/>`,
  );
  for (const s of [-1, 1]) {
    const mx = 46 * s;
    const wx = mx * 0.78;
    out.push(
      rect(mx - 2.6, 66, 5.2, 66 * k + 20),
      rect(mx - 4.4, 50, 8.8, 2.5 * k),
      `<path d="M${X(mx - 3.4)} ${Y(66)}L${X(mx)} ${Y(76)}L${X(mx + 3.4)} ${Y(66)}Z"/>`,
      rect(wx - 9, 14, 18, 14 * k + 20),
      `<path d="M${X(wx - 9)} ${Y(14)}C${X(wx - 9)} ${Y(24)} ${X(wx - 3)} ${Y(27)} ${X(wx)} ${Y(30)}C${X(wx + 3)} ${Y(27)} ${X(wx + 9)} ${Y(24)} ${X(wx + 9)} ${Y(14)}Z"/>`,
    );
  }
  const windows = [
    ...[-1, 0, 1].map((i) => rect(i * 14 - 3, 16, 6, 10 * k, ` rx="${n(3 * k)}"`)),
    ...[-1, 1].map((s) => rect(s * 46 - 1.2, 60, 2.4, 6 * k, ` rx="${n(1.2 * k)}"`)),
  ];
  return { body: out.join(""), windows: windows.join("") };
}

/** A date palm: a gently curved trunk and eight drooping fronds. */
function palm(x: number, base: number, h: number, lean = 0): string {
  const [tx, ty] = [x + lean * h * 0.35, base - h];
  const w = h * 0.045;
  const bend = x + lean * h * 0.3;
  let out =
    `<path d="M${n(x - w)} ${n(base + 4)}Q${n(bend - w)} ${n(base - h * 0.55)} ${n(tx - w * 0.6)} ${n(ty)}` +
    `L${n(tx + w * 0.6)} ${n(ty)}Q${n(bend + w)} ${n(base - h * 0.55)} ${n(x + w)} ${n(base + 4)}Z"/>`;
  for (const deg of [-165, -140, -115, -90, -65, -40, -15, 10]) {
    const a = (deg * Math.PI) / 180;
    const len = h * (Math.abs(deg + 85) < 40 ? 0.55 : 0.62);
    const [cos, sin] = [Math.cos(a), Math.sin(a)];
    const [ex, ey] = [tx + cos * len, ty + sin * len * 0.55 + len * 0.35];
    const [qx, qy] = [tx + cos * len * 0.5, ty + sin * len * 0.9];
    const [nx, ny] = [-sin * h * 0.032, cos * h * 0.032];
    out += `<path d="M${n(tx)} ${n(ty)}Q${n(qx + nx)} ${n(qy + ny)} ${n(ex)} ${n(ey)}Q${n(qx - nx)} ${n(qy - ny)} ${n(tx)} ${n(ty)}Z"/>`;
  }
  return out;
}

/** A distant town on the far ridge: small domes and minarets. */
function farTown(pts: Pt[]): string {
  const town: [number, "dome" | "minaret", number][] = [
    [250, "dome", 0.8], [290, "minaret", 0.9], [330, "dome", 0.6],
    [1080, "minaret", 0.8], [1120, "dome", 0.9], [1165, "dome", 0.6], [1200, "minaret", 0.7],
  ];
  return town
    .map(([cx, kind, s]) => {
      const base = yAt(pts, cx) + 6;
      if (kind === "dome") {
        const r = 16 * s;
        return `<rect x="${n(cx - r)}" y="${n(base - 14 * s)}" width="${n(2 * r)}" height="${n(14 * s + 8)}"/><path d="M${n(cx - r)} ${n(base - 14 * s)}a${n(r)} ${n(r)} 0 0 1 ${n(2 * r)} 0Z"/>`;
      }
      return `<rect x="${n(cx - 3 * s)}" y="${n(base - 60 * s)}" width="${n(6 * s)}" height="${n(60 * s + 8)}"/><path d="M${n(cx - 4 * s)} ${n(base - 60 * s)}L${n(cx)} ${n(base - 72 * s)}L${n(cx + 4 * s)} ${n(base - 60 * s)}Z"/>`;
    })
    .join("");
}

/** Deterministic stars (same sky on every build), denser near the top. */
export function stars(count = 70): string {
  let seed = 7;
  const rand = () => ((seed = (seed * 16807) % 2147483647) - 1) / 2147483646;
  return Array.from({ length: count }, () => {
    const [x, y, r] = [rand() * 100, rand() ** 1.6 * 62, 0.6 + rand() * 1.1];
    const style = `--d:${(2 + rand() * 3).toFixed(1)}s;--delay:${(-rand() * 4).toFixed(1)}s`;
    return `<circle cx="${x.toFixed(2)}%" cy="${y.toFixed(2)}%" r="${r.toFixed(2)}" style="${style}"/>`;
  }).join("");
}

export function landscape() {
  const far = ridge(H - 250, 26, 1.4, 0.9);
  const midLevel = H - 150;
  const mid = ridge(midLevel, 20, 1.0, 4.3, { cx: W / 2, half: 260, level: midLevel });
  const near = ridge(H - 50, 16, 1.7, 2.2);
  const m = mosque(W / 2, midLevel + 4, 2.2);
  const onMid = (x: number, h: number, lean: number) => palm(x, yAt(mid, x), h, lean);
  const onNear = (x: number, h: number, lean: number) => palm(x, yAt(near, x), h, lean);
  return {
    town: farTown(far),
    far: outline(far),
    mid: `<path d="${outline(mid)}"/>${m.body}${onMid(W / 2 - 205, 110, -0.2)}${onMid(W / 2 + 215, 95, 0.25)}${onMid(W / 2 + 250, 70, 0.1)}`,
    windows: m.windows,
    near: `<path d="${outline(near)}"/>${onNear(90, 190, 0.15)}${onNear(170, 140, -0.1)}${onNear(1330, 175, -0.2)}${onNear(1395, 120, 0.05)}`,
  };
}
