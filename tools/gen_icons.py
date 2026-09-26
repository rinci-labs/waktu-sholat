"""Generates the launcher PNGs from the same crescent geometry as ic_launcher_foreground.xml.

The adaptive icon draws the crescent inside a 108-unit canvas with an outer radius of 30, so the
legacy raster icons scale that same shape to fill the tile (an adaptive foreground is scaled down
by the launcher, which is why the vector looks smaller than the PNG).
"""
import os
import numpy as np
from PIL import Image, ImageDraw

BRAND = (27, 127, 90, 255)
WHITE = (255, 255, 255, 255)

# Geometry in the vector's 108-unit space.
CANVAS = 108.0
OUTER_CENTER = (54.0, 54.0)
OUTER_RADIUS = 30.0
INNER_CENTER = (54.0 + 9.0, 54.0 - 7.0)
INNER_RADIUS = 25.5

SUPERSAMPLE = 4


def crescent_mask(size: int, fill_fraction: float) -> np.ndarray:
    """Boolean crescent mask, normalized so the shape spans `fill_fraction` of the tile."""
    s = size * SUPERSAMPLE
    yy, xx = np.mgrid[0:s, 0:s]
    cx, cy = (s - 1) / 2.0, (s - 1) / 2.0
    scale = fill_fraction * s / (OUTER_RADIUS * 2.0)
    ox, oy = OUTER_CENTER
    ix, iy = INNER_CENTER
    outer = (xx - (cx + (ox - CANVAS / 2) * scale)) ** 2 + (yy - (cy + (oy - CANVAS / 2) * scale)) ** 2 \
        <= (OUTER_RADIUS * scale) ** 2
    inner = (xx - (cx + (ix - CANVAS / 2) * scale)) ** 2 + (yy - (cy + (iy - CANVAS / 2) * scale)) ** 2 \
        <= (INNER_RADIUS * scale) ** 2
    mask = outer & ~inner
    return mask.reshape(size, SUPERSAMPLE, size, SUPERSAMPLE).mean(axis=(1, 3))


def write_icon(path: str, size: int, fill_fraction: float, round_tile: bool) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    alpha = crescent_mask(size, fill_fraction)
    yy, xx = np.mgrid[0:size, 0:size]
    tile = ((xx - (size - 1) / 2.0) ** 2 + (yy - (size - 1) / 2.0) ** 2
            <= ((size - 1) / 2.0) ** 2).astype(float) if round_tile else np.ones((size, size))

    rgba = np.zeros((size, size, 4), dtype=float)
    rgba[..., 0] = BRAND[0] * (1 - alpha) + WHITE[0] * alpha
    rgba[..., 1] = BRAND[1] * (1 - alpha) + WHITE[1] * alpha
    rgba[..., 2] = BRAND[2] * (1 - alpha) + WHITE[2] * alpha
    rgba[..., 3] = 255.0 * (tile * (1 - alpha) + alpha)
    Image.fromarray(rgba.round().astype(np.uint8), "RGBA").save(path)
    print(f"wrote {path} ({size}x{size}, crescent {fill_fraction:.0%} of tile)")


def main() -> None:
    for density, size in [
        ("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192),
    ]:
        # Legacy icons are used as-is by older launchers, so the crescent fills most of the tile.
        write_icon(f"app/src/main/res/mipmap-{density}/ic_launcher.png", size, 0.66, False)
        write_icon(f"app/src/main/res/mipmap-{density}/ic_launcher_round.png", size, 0.58, True)
    write_icon("app/src/main/res/mipmap-xxxhdpi/ic_launcher_play.png", 512, 0.66, False)


if __name__ == "__main__":
    main()
