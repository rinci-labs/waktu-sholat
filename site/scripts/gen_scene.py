"""Generates the landing hero landscape SVG (flat, layered) used by site/src/index.html."""
import math

W, H = 1440, 420
horizon = H


def ridge(base, amp, waves, phase, flat=None):
    pts = []
    for i in range(0, 145):
        x = W * i / 144
        a = (i / 144) * waves * 2 * math.pi + phase
        y = base - amp * (0.6 * math.sin(a) + 0.4 * math.sin(a * 0.53 + 1.3))
        if flat:
            cx, half, level = flat
            d = abs(x - cx)
            if d < half:
                t = (1 - d / half) ** 2
                y = y * (1 - t) + level * t
        pts.append((x, y))
    return pts


def path(pts):
    return f"M0 {H + 1}" + ''.join(f"L{x:.1f} {y:.1f}" for x, y in pts) + f"L{W} {H + 1}Z"


def y_at(pts, x):
    i = min(int(x / W * 144), 143)
    x0, y0 = pts[i]; x1, y1 = pts[i + 1]
    return y0 + (y1 - y0) * (x - x0) / (x1 - x0)


far = ridge(horizon - 250, 26, 1.4, 0.9)
mid_level = horizon - 150
mid = ridge(horizon - 150, 20, 1.0, 4.3, flat=(W * 0.5, 260, mid_level))
near = ridge(horizon - 50, 16, 1.7, 2.2)


def mosque(cx, base, k):
    X = lambda v: f"{cx + v * k:.1f}"; Y = lambda v: f"{base - v * k:.1f}"
    out = []
    out.append(f'<rect x="{X(-34)}" y="{Y(22)}" width="{68 * k:.1f}" height="{22 * k + 20:.1f}"/>')
    out.append(f'<rect x="{X(-22)}" y="{Y(27)}" width="{44 * k:.1f}" height="{6 * k:.1f}"/>')
    out.append(f'<path d="M{X(-22)} {Y(27)}C{X(-25)} {Y(47)} {X(-9)} {Y(54)} {X(0)} {Y(61)}C{X(9)} {Y(54)} {X(25)} {Y(47)} {X(22)} {Y(27)}Z"/>')
    out.append(f'<rect x="{X(-0.7)}" y="{Y(68)}" width="{1.4 * k:.1f}" height="{8 * k:.1f}"/>')
    r1 = 3.2 * k; r2 = 2.7 * k; c1 = (cx, base - 71 * k); c2 = (cx + 1.4 * k, base - 71.8 * k)
    out.append(f'<path fill-rule="evenodd" d="M{c1[0] - r1:.1f} {c1[1]:.1f}a{r1:.1f} {r1:.1f} 0 1 0 {2 * r1:.1f} 0a{r1:.1f} {r1:.1f} 0 1 0 {-2 * r1:.1f} 0ZM{c2[0] - r2:.1f} {c2[1]:.1f}a{r2:.1f} {r2:.1f} 0 1 1 {2 * r2:.1f} 0a{r2:.1f} {r2:.1f} 0 1 1 {-2 * r2:.1f} 0Z"/>')
    for s in (-1, 1):
        mx = 46 * s
        out.append(f'<rect x="{X(mx - 2.6)}" y="{Y(66)}" width="{5.2 * k:.1f}" height="{66 * k + 20:.1f}"/>')
        out.append(f'<rect x="{X(mx - 4.4)}" y="{Y(50)}" width="{8.8 * k:.1f}" height="{2.5 * k:.1f}"/>')
        out.append(f'<path d="M{X(mx - 3.4)} {Y(66)}L{X(mx)} {Y(76)}L{X(mx + 3.4)} {Y(66)}Z"/>')
        # Side wings with small domes.
        out.append(f'<rect x="{X(mx * 0.78 - 9)}" y="{Y(14)}" width="{18 * k:.1f}" height="{14 * k + 20:.1f}"/>')
        out.append(f'<path d="M{X(mx * 0.78 - 9)} {Y(14)}C{X(mx * 0.78 - 9)} {Y(24)} {X(mx * 0.78 - 3)} {Y(27)} {X(mx * 0.78)} {Y(30)}C{X(mx * 0.78 + 3)} {Y(27)} {X(mx * 0.78 + 9)} {Y(24)} {X(mx * 0.78 + 9)} {Y(14)}Z"/>')
    wins = []
    for i in (-1, 0, 1):
        wins.append(f'<rect x="{X(i * 14 - 3)}" y="{Y(16)}" width="{6 * k:.1f}" height="{10 * k:.1f}" rx="{3 * k:.1f}"/>')
    for s in (-1, 1):
        wins.append(f'<rect x="{X(s * 46 - 1.2)}" y="{Y(60)}" width="{2.4 * k:.1f}" height="{6 * k:.1f}" rx="{1.2 * k:.1f}"/>')
    return ''.join(out), ''.join(wins)


def palm(x, base, h, lean=0.0):
    """A date palm: a gently curved trunk and seven drooping fronds."""
    tx, ty = x + lean * h * 0.35, base - h
    w = h * 0.045
    trunk = (f'<path d="M{x - w:.1f} {base + 4:.1f}Q{x + lean * h * 0.3 - w:.1f} {base - h * 0.55:.1f} {tx - w * 0.6:.1f} {ty:.1f}'
             f'L{tx + w * 0.6:.1f} {ty:.1f}Q{x + lean * h * 0.3 + w:.1f} {base - h * 0.55:.1f} {x + w:.1f} {base + 4:.1f}Z"/>')
    fronds = []
    for ang in (-165, -140, -115, -90, -65, -40, -15, 10):
        a = math.radians(ang)
        L = h * (0.55 if abs(ang + 85) < 40 else 0.62)
        ex, ey = tx + math.cos(a) * L, ty + math.sin(a) * L * 0.55 + L * 0.35
        cx1, cy1 = tx + math.cos(a) * L * 0.5, ty + math.sin(a) * L * 0.9
        nx, ny = -math.sin(a) * h * 0.032, math.cos(a) * h * 0.032
        fronds.append(f'<path d="M{tx:.1f} {ty:.1f}Q{cx1 + nx:.1f} {cy1 + ny:.1f} {ex:.1f} {ey:.1f}Q{cx1 - nx:.1f} {cy1 - ny:.1f} {tx:.1f} {ty:.1f}Z"/>')
    return trunk + ''.join(fronds)


def far_city(pts):
    """A distant town on the far ridge: small domes and minarets."""
    out = []
    for cx, kind, s in ((250, 'dome', 0.8), (290, 'minaret', 0.9), (330, 'dome', 0.6), (1080, 'minaret', 0.8), (1120, 'dome', 0.9), (1165, 'dome', 0.6), (1200, 'minaret', 0.7)):
        base = y_at(pts, cx) + 6
        if kind == 'dome':
            r = 16 * s
            out.append(f'<rect x="{cx - r:.1f}" y="{base - 14 * s:.1f}" width="{2 * r:.1f}" height="{14 * s + 8:.1f}"/>')
            out.append(f'<path d="M{cx - r:.1f} {base - 14 * s:.1f}a{r:.1f} {r:.1f} 0 0 1 {2 * r:.1f} 0Z"/>')
        else:
            out.append(f'<rect x="{cx - 3 * s:.1f}" y="{base - 60 * s:.1f}" width="{6 * s:.1f}" height="{60 * s + 8:.1f}"/>')
            out.append(f'<path d="M{cx - 4 * s:.1f} {base - 60 * s:.1f}L{cx:.1f} {base - 72 * s:.1f}L{cx + 4 * s:.1f} {base - 60 * s:.1f}Z"/>')
    return ''.join(out)


m, wins = mosque(W * 0.5, mid_level + 4, 2.2)
mid_palms = palm(W * 0.5 - 205, y_at(mid, W * 0.5 - 205), 110, -0.2) + palm(W * 0.5 + 215, y_at(mid, W * 0.5 + 215), 95, 0.25) + palm(W * 0.5 + 250, y_at(mid, W * 0.5 + 250), 70, 0.1)
near_palms = palm(90, y_at(near, 90), 190, 0.15) + palm(170, y_at(near, 170), 140, -0.1) + palm(1330, y_at(near, 1330), 175, -0.2) + palm(1395, y_at(near, 1395), 120, 0.05)

print(f'''<svg class="landscape absolute bottom-0 left-0 w-full" style="height:var(--land)" viewBox="0 0 {W} {H}" preserveAspectRatio="xMidYMax slice">
        <defs><linearGradient id="far" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--hill-far-a)"/><stop offset="1" stop-color="var(--hill-far-b)"/></linearGradient></defs>
        <g class="far-city">{far_city(far)}</g>
        <path fill="url(#far)" d="{path(far)}"/>
        <g class="hill-mid"><path d="{path(mid)}"/>{m}{mid_palms}</g>
        <g class="window">{wins}</g>
        <g class="hill-near"><path d="{path(near)}"/>{near_palms}</g>
      </svg>''')
