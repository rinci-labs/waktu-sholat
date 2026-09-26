"""Generates the legacy launcher PNGs (API 24-25) and the Play Store icon.

Same artwork as the adaptive icon: the default theme's green sky gradient behind a white crescent
cradling a four-point star. Geometry is in the vector's 108-unit space (ic_launcher_foreground.xml);
the legacy icons scale it up to fill the tile, because an adaptive foreground is itself scaled down
by the launcher.
"""
import os
import numpy as np
from PIL import Image

TOP = np.array([11, 94, 69], dtype=float)
BOTTOM = np.array([30, 148, 112], dtype=float)

CANVAS = 108.0
OUTER = ((50.0, 53.0), 25.0)
INNER = ((60.0, 45.0), 21.0)
STAR = [(70, 29.5), (71.5, 34.5), (76.5, 36), (71.5, 37.5), (70, 42.5), (68.5, 37.5), (63.5, 36), (68.5, 34.5)]

SUPERSAMPLE = 4


def inside_polygon(xx, yy, poly):
    """Even-odd point-in-polygon test, vectorised over the grid."""
    inside = np.zeros_like(xx, dtype=bool)
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        crosses = ((y1 > yy) != (y2 > yy)) & (xx < (x2 - x1) * (yy - y1) / (y2 - y1 + 1e-12) + x1)
        inside ^= crosses
    return inside


def artwork_mask(size: int, zoom: float) -> np.ndarray:
    """Antialiased mask of crescent + star; `zoom` > 1 enlarges the art around the canvas centre."""
    s = size * SUPERSAMPLE
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    # Map pixels back into the 108-unit space.
    ux = (xx + 0.5) / s * CANVAS
    uy = (yy + 0.5) / s * CANVAS
    ux = (ux - CANVAS / 2) / zoom + CANVAS / 2
    uy = (uy - CANVAS / 2) / zoom + CANVAS / 2
    (ox, oy), orad = OUTER
    (ix, iy), irad = INNER
    crescent = ((ux - ox) ** 2 + (uy - oy) ** 2 <= orad ** 2) & ((ux - ix) ** 2 + (uy - iy) ** 2 > irad ** 2)
    mask = crescent | inside_polygon(ux, uy, STAR)
    return mask.reshape(size, SUPERSAMPLE, size, SUPERSAMPLE).mean(axis=(1, 3))


def write_icon(path: str, size: int, zoom: float, round_tile: bool) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    alpha = artwork_mask(size, zoom)
    t = np.linspace(0, 1, size)[:, None, None]
    bg = TOP * (1 - t) + BOTTOM * t
    rgb = bg * (1 - alpha[..., None]) + 255.0 * alpha[..., None]
    yy, xx = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2.0
    tile = ((xx - c) ** 2 + (yy - c) ** 2 <= c ** 2).astype(float) if round_tile else np.ones((size, size))
    rgba = np.dstack([rgb, 255.0 * tile])
    Image.fromarray(rgba.round().astype(np.uint8), "RGBA").save(path, optimize=True)
    print(f"wrote {path} ({size}x{size})")


def main() -> None:
    for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        write_icon(f"app/src/main/res/mipmap-{density}/ic_launcher.png", size, 1.45, False)
        write_icon(f"app/src/main/res/mipmap-{density}/ic_launcher_round.png", size, 1.3, True)
    write_icon("store/ic_launcher_play.png", 512, 1.45, False)


if __name__ == "__main__":
    main()
