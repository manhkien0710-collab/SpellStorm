#!/usr/bin/env python3
"""
Hand-crafted Milestone 3 terrain tiles, 4-frame animated river water,
seamless terrain variants (8 frames), and terrain transition fringes (8 frames)
for Spell Storm.

PRESERVATION GUARANTEE:
Never writes or modifies M2 canonical grass.png, dirt.png, or stone.png.
"""

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
SRC_DIR = ROOT / "assets/sources/terrain"
TERRAIN_OUT = ROOT / "assets/textures/terrain"
ANIM_OUT = ROOT / "assets/textures/animations"
PREVIEW_DIR = ROOT / "docs/previews/milestone3"


def hex_to_rgba(hex_col: str) -> tuple[int, int, int, int]:
    h = hex_col.lstrip("#")
    r = int(h[0:2], 16)
    g = int(h[2:4], 16)
    b = int(h[4:6], 16)
    a = int(h[6:8], 16) if len(h) == 8 else 255
    return (r, g, b, a)


def save_grid_json(
    name: str,
    w: int,
    h: int,
    palette: dict[str, str],
    frames: list[tuple[str, int, list[str]]],
    pivot: list[int] | None = None,
) -> Path:
    SRC_DIR.mkdir(parents=True, exist_ok=True)
    for fname, _, rows in frames:
        assert len(rows) == h, f"{name}/{fname}: expected {h} rows, got {len(rows)}"
        for i, row in enumerate(rows):
            assert len(row) == w, f"{name}/{fname} row {i}: expected {w}, got {len(row)}"
            for ch in row:
                assert ch == "." or ch in palette, f"{name}/{fname} row {i}: unknown key {ch!r}"
    doc = {
        "version": 1,
        "name": name,
        "canvas": [w, h],
        "pivot": pivot if pivot is not None else [w // 2, h // 2],
        "palette": palette,
        "frames": [
            {"name": fname, "duration_ms": dur, "rows": rows}
            for fname, dur, rows in frames
        ],
    }
    path = SRC_DIR / f"{name}.json"
    path.write_text(json.dumps(doc, indent=2) + "\n")
    return path


def render_rows(palette: dict[str, str], rows: list[str]) -> Image.Image:
    h = len(rows)
    w = len(rows[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    rgba_map = {k: hex_to_rgba(v) for k, v in palette.items()}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = rgba_map[ch]
    return img


def render_sheet(images: list[Image.Image]) -> Image.Image:
    w, h = images[0].size
    sheet = Image.new("RGBA", (w * len(images), h), (0, 0, 0, 0))
    for i, im in enumerate(images):
        sheet.paste(im, (i * w, 0))
    return sheet


# ---------------------------------------------------------------------------
# 1. WATER (32x32) & WATER_SHEET (128x32, 4 looping frames)
# ---------------------------------------------------------------------------
WATER_PALETTE = {
    "D": "#142a4f",  # deep sapphire current trough
    "d": "#1d4375",  # dark cerulean wave shadow
    "w": "#285e99",  # calm primary river cerulean-sapphire
    "m": "#3c83bf",  # sunlit mid wave swell
    "c": "#72c5e8",  # bright aqua ripple crest
    "h": "#bdf2ff",  # crisp pale-aqua specular sparkle
}


def make_water_frame(frame_idx: int) -> list[str]:
    """
    Smooth flowing arcane cerulean-sapphire river water (32x32, toroidal wrap).
    Uses continuous sinusoidal current bands rather than isolated pill shapes,
    scrolling smoothly south (+8px/frame) over 4 looping frames.
    """
    W, H = 32, 32
    grid = [["w" for _ in range(W)] for _ in range(H)]

    # Base continuous undulating current wave bands (toroidal over 32x32)
    # Scrolls cleanly +8px south per frame (4 * 8 = 32 -> seamless 4-frame loop)
    dy_flow = (frame_idx * 8) % H

    for y in range(H):
        wy = (y - dy_flow) % H
        for x in range(W):
            a1 = math.sin(2.0 * math.pi * (x / 32.0) + math.sin(2.0 * math.pi * wy / 32.0) * 0.85)
            a2 = math.cos(2.0 * math.pi * (wy / 32.0) - 2.0 * math.pi * (x / 32.0))
            val = (
                0.50 * math.sin(2.0 * math.pi * (wy / 16.0) + 0.75 * math.sin(2.0 * math.pi * x / 32.0))
                + 0.35 * a1
                + 0.20 * a2
            )
            if val < -0.68:
                grid[y][x] = "D"
            elif val < -0.22:
                grid[y][x] = "d"
            elif val > 0.32:
                grid[y][x] = "m"
            else:
                grid[y][x] = "w"

    # Delicate, curved 1px aqua ripple crests hugging strictly the sunlit 'm' swells
    # Two alternating sparkle groups crossfade h <-> c smoothly as the wave flows south
    shimmer = frame_idx % 4
    group_a_hot = shimmer in (0, 1)
    group_b_hot = shimmer in (2, 3)

    # All anchors verified inside sunlit 'm' swell ridges of wy:
    # Swell 1 (top-left): x=2..13, y=1..5; Swell 2 (top-right): x=26..31, y=3..6;
    # Swell 3 (mid-left): x=5..16, y=16..20; Swell 4 (mid-right): x=14..24, y=20..23
    curved_ripples = [
        (4, 3, True, [(0, 0, "c"), (1, -1, "c"), (2, -1, "h"), (3, -1, "c"), (4, 0, "c")]),
        (28, 4, False, [(0, 0, "c"), (1, 0, "h"), (2, 0, "c")]),
        (7, 17, False, [(0, 0, "c"), (1, -1, "c"), (2, -1, "h"), (3, -1, "c"), (4, 0, "c")]),
        (16, 20, True, [(0, 0, "c"), (1, 0, "c"), (2, 1, "h"), (3, 1, "c"), (4, 1, "c")]),
    ]
    for rx, ry, is_group_a, pts in curved_ripples:
        hot = group_a_hot if is_group_a else group_b_hot
        for dx, dy, ch in pts:
            yy = (ry + dy + dy_flow) % H
            xx = (rx + dx) % W
            # Only draw crest glints on sunlit water ('m' or neighboring 'w'), never dark troughs
            if grid[yy][xx] in ("m", "w"):
                if ch == "h":
                    grid[yy][xx] = "h" if hot else "c"
                else:
                    grid[yy][xx] = "c"

    # Two tiny travelling specular pinpoints strictly centered in sunlit 'm' swells
    pinpoints = [
        (9, 4) if group_a_hot else (20, 21),
        (11, 18) if group_b_hot else (5, 2),
    ]
    for sx, sy in pinpoints:
        yy = (sy + dy_flow) % H
        xx = sx % W
        if grid[yy][xx] in ("m", "c"):
            grid[yy][xx] = "h"

    return ["".join(r) for r in grid]


# ---------------------------------------------------------------------------
# 2. SAND (32x32) — Sunlit golden-tan riverbank sand with dunes & pebbles
# ---------------------------------------------------------------------------
SAND_PALETTE = {
    "D": "#7a5b3c",  # deep pebble underside / dune trough
    "s": "#a88259",  # shaded dune slope
    "b": "#cc9f6e",  # calm golden-tan primary sand
    "l": "#e8c28b",  # sunlit dune crest & pebble catchlight
}


def make_sand_grid() -> list[str]:
    W, H = 32, 32
    grid = [["b" for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, ch: str) -> None:
        grid[y % H][x % W] = ch

    def stamp(ox: int, oy: int, rows: list[str]) -> None:
        for ry, r in enumerate(rows):
            for rx, ch in enumerate(r):
                if ch != ".":
                    put(ox + rx, oy + ry, ch)

    # Gentle thin 1px wind-carved curved dune ripples (organic broken arcs, no oval pills!)
    stamp(2, 4, [
        "..llllll........",
        "llssssssll......",
        "...ssss.........",
    ])
    stamp(17, 11, [
        "...lllllll......",
        ".llsssssssll....",
        "....ssss........",
    ])
    stamp(5, 20, [
        "..llllllll......",
        "llssssssssll....",
        "...sssss........",
    ])
    stamp(20, 27, [
        "..llllll........",
        "llssssssll......",
    ])

    # Subtle soft organic sand hollows
    stamp(13, 2, [".ss.", "sss."])
    stamp(27, 16, [".ss.", "sss."])
    stamp(14, 25, [".ss.", ".s.."])

    # Tiny smooth riverbank pebbles & fine warm sand flecks (asymmetric)
    stamp(9, 13, ["lb", "sD"])
    stamp(25, 5, [".l", "sD"])
    stamp(18, 17, ["lb", ".D"])
    stamp(7, 27, ["ls", ".D"])
    for fx, fy in [(14, 8), (22, 22), (4, 15), (29, 2)]:
        put(fx, fy, "l")
        put(fx + 1, fy + 1, "s")

    return ["".join(r) for r in grid]


# ---------------------------------------------------------------------------
# 3. BRIDGE_H (32x32) — Horizontal weathered oak-timber bridge (x-spanning)
# ---------------------------------------------------------------------------
BRIDGE_PALETTE = {
    "o": "#14101a",  # dark timber outer crevice / rail outline
    "d": "#142a4f",  # deep water cast shadow under bridge
    "u": "#1d4375",  # shaded river water along top/bottom bridge border
    "w": "#285e99",  # cerulean river water peeking at outer edges
    "k": "#2b1a14",  # dark plank crevice / rail shadow lip
    "s": "#472b1e",  # shaded weathered oak
    "b": "#6e442c",  # mid oak timber plank & rail
    "l": "#99633f",  # sunlit oak plank/rail highlight
    "h": "#bf926b",  # crisp sunlit rail edge bevel
    "m": "#3e4556",  # iron bolt shadow
    "i": "#5a657a",  # iron bolt mid
    "I": "#aebdcf",  # iron bolt glint
}


def make_bridge_h_grid() -> list[str]:
    W, H = 32, 32
    grid = [["b" for _ in range(W)] for _ in range(H)]

    # Water edges top (y=0) & bottom shadow (y=30..31)
    for x in range(W):
        grid[0][x] = "u" if (x % 7 in (0, 1, 5)) else "w"
        grid[1][x] = "o"
        grid[2][x] = "h" if (x % 6 not in (2,)) else "l"
        grid[3][x] = "b"
        grid[4][x] = "s"
        grid[5][x] = "k"
        grid[6][x] = "s"  # cast shadow onto top of vertical planks

    # Vertical planks across y=6..25, seamless x=0..31
    # Plank seams at x = 0, 6, 13, 19, 26 -> interior widths 5, 6, 5, 6, 5
    seams = {0, 6, 13, 19, 26}
    sun_edges = {1, 7, 14, 20, 27}
    shade_edges = {5, 12, 18, 25, 31}

    for y in range(6, 26):
        for x in range(W):
            if x in seams:
                grid[y][x] = "k"
            elif y == 6:
                grid[y][x] = "k" if x in shade_edges else "s"
            elif y == 7:
                grid[y][x] = "l" if x in sun_edges else ("s" if x in shade_edges else "b")
            elif x in sun_edges:
                grid[y][x] = "h" if (y in (9, 10, 17, 18, 23)) else "l"
            elif x in shade_edges:
                grid[y][x] = "s"
            else:
                grid[y][x] = "b"

    # Subtle weathered wood grain crevices & plank tonal variation
    grain_dashes = [
        (3, 10, 4), (9, 13, 5), (16, 8, 3), (22, 16, 4), (29, 11, 5),
        (4, 19, 4), (10, 21, 3), (15, 18, 4), (23, 9, 3), (28, 20, 3),
    ]
    for gx, gy, glen in grain_dashes:
        for dy in range(glen):
            grid[gy + dy][gx] = "s"
        grid[gy + 1][gx] = "k" if glen >= 4 else "s"

    # Small recessed plank nails near top & bottom rails
    for nx in (3, 10, 16, 23, 29):
        grid[8][nx] = "m"
        grid[24][nx] = "m"

    # Bottom horizontal timber rail (y=26..29) + deep water shadow below (y=30..31)
    for x in range(W):
        grid[26][x] = "k"
        grid[27][x] = "h" if (x % 5 != 3) else "l"
        grid[28][x] = "b"
        grid[29][x] = "o"
        grid[30][x] = "d"
        grid[31][x] = "d" if (x % 4 in (0, 1, 2)) else "u"

    # Iron bolt studs on top & bottom rails (aligned across x=0..31)
    for bx in (3, 15, 27):
        for ry in (2, 27):
            grid[ry][bx] = "I"
            grid[ry][bx + 1] = "i"
            grid[ry + 1][bx] = "i"
            grid[ry + 1][bx + 1] = "m"

    return ["".join(r) for r in grid]


# ---------------------------------------------------------------------------
# 4. BRIDGE_V (32x32) — Vertical weathered oak-timber bridge (y-spanning)
# ---------------------------------------------------------------------------
def make_bridge_v_grid() -> list[str]:
    W, H = 32, 32
    grid = [["b" for _ in range(W)] for _ in range(H)]

    # Left water & left guardrail (x=0..5)
    for y in range(H):
        grid[y][0] = "u" if (y % 7 in (0, 1, 5)) else "w"
        grid[y][1] = "o"
        grid[y][2] = "h" if (y % 6 != 2) else "l"
        grid[y][3] = "b"
        grid[y][4] = "s"
        grid[y][5] = "k"

    # Horizontal planks spanning x=6..25, seamless y=0..31
    # Plank seams at y = 0, 6, 13, 19, 26 -> heights 5, 6, 5, 6, 5
    seams = {0, 6, 13, 19, 26}
    sun_edges = {1, 7, 14, 20, 27}
    shade_edges = {5, 12, 18, 25, 31}

    for y in range(H):
        for x in range(6, 26):
            if y in seams:
                grid[y][x] = "k"
            elif x == 6:
                grid[y][x] = "k" if y in shade_edges else "s"
            elif x == 7:
                grid[y][x] = "l" if y in sun_edges else ("s" if y in shade_edges else "b")
            elif y in sun_edges:
                grid[y][x] = "h" if (x in (9, 10, 17, 18, 22)) else "l"
            elif y in shade_edges:
                grid[y][x] = "s"
            else:
                grid[y][x] = "b"

    # Subtle horizontal wood grain crevices
    grain_dashes = [
        (10, 3, 4), (14, 9, 5), (9, 16, 4), (17, 22, 4), (11, 29, 5),
        (19, 4, 3), (20, 10, 4), (16, 15, 3), (12, 23, 4), (20, 28, 3),
    ]
    for gx, gy, glen in grain_dashes:
        for dx in range(glen):
            grid[gy][gx + dx] = "s"
        grid[gy][gx + 1] = "k" if glen >= 4 else "s"

    # Plank nails near left & right guardrails
    for ny in (3, 10, 16, 23, 29):
        grid[ny][8] = "m"
        grid[ny][24] = "m"

    # Right guardrail (x=26..29) + deep water shadow right of bridge (x=30..31)
    for y in range(H):
        grid[y][26] = "k"
        grid[y][27] = "l" if (y % 5 != 3) else "b"
        grid[y][28] = "s"
        grid[y][29] = "o"
        grid[y][30] = "d"
        grid[y][31] = "d" if (y % 4 in (0, 1, 2)) else "u"

    # Iron bolt studs on left & right rails (aligned vertically y=0..31)
    for by in (3, 15, 27):
        for rx in (2, 27):
            grid[by][rx] = "I"
            grid[by][rx + 1] = "i"
            grid[by + 1][rx] = "i"
            grid[by + 1][rx + 1] = "m"

    return ["".join(r) for r in grid]


# ---------------------------------------------------------------------------
# 5. TERRAIN VARIANTS SHEET (256x32 = 8 frames of 32x32, alpha 255)
#    Must tile seamlessly next to M2 grass/dirt/stone and M3 sand!
# ---------------------------------------------------------------------------
VARIANT_PALETTE = {
    # Meadow grass
    "D": "#163020",
    "s": "#224d2c",
    "g": "#347539",
    "l": "#52a349",
    "h": "#82cf63",
    # Wildflowers & clover accents
    "w": "#e8f1f8",
    "Y": "#ffe066",
    "y": "#d99726",
    "b": "#5296e8",
    "B": "#8fd3ff",
    # Warm dirt
    "E": "#382419",
    "e": "#543826",
    "d": "#785238",
    "r": "#9c6f4f",
    "p": "#bf926b",
    # Runic stone
    "v": "#1e1829",
    "m": "#282c38",
    "S": "#3e4556",
    "M": "#5a657a",
    "L": "#808fa6",
    "H": "#aebdcf",
    # Sand
    "T": "#7a5b3c",
    "t": "#a88259",
    "a": "#cc9f6e",
    "A": "#e8c28b",
}


def make_grass_variant(kind: int) -> list[str]:
    W, H = 32, 32
    grid = [["g" for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, ch: str) -> None:
        # Keep outermost 1px rim pure 'g' except where subtle safe tufts sit inside [2..29]
        if 2 <= x <= 29 and 2 <= y <= 29:
            grid[y][x] = ch

    def stamp(ox: int, oy: int, rows: list[str]) -> None:
        for ry, r in enumerate(rows):
            for rx, ch in enumerate(r):
                if ch != ".":
                    put(ox + rx, oy + ry, ch)

    if kind == 0:
        # Grass Variant A: clover clusters + tiny white-gold wildflowers
        stamp(4, 5, ["..sss.", ".ssss.", "..ss.."])
        stamp(18, 17, ["..sss.", ".ssss.", "..ss.."])
        stamp(7, 21, [".ll.", "llll", ".sl."])
        stamp(21, 5, [".ll.", "lll.", ".s.."])
        # 3-leaf clover clusters
        for cx, cy in [(11, 10), (23, 23), (5, 16)]:
            stamp(cx, cy, [
                ".l.",
                "lsl",
                ".s.",
            ])
        # Tiny white-gold wildflowers
        for fx, fy in [(8, 8), (22, 11), (14, 24), (25, 25)]:
            stamp(fx, fy, [
                ".w.",
                "wYw",
                ".s.",
            ])
        put(16, 6, "h")
        put(26, 18, "l")
    elif kind == 1:
        # Grass Variant B: sunlit lush fern/blade tufts + small azure blossom
        stamp(12, 4, ["..ss.", ".ssss", "..ss."])
        stamp(4, 18, [".sss.", "sssss", ".ss.."])
        stamp(20, 22, ["..ss.", ".ssss", "..ss."])
        # Lush fern / tall blade tufts
        for tx, ty in [(6, 6), (20, 8), (14, 16), (24, 18), (9, 24)]:
            stamp(tx, ty, [
                "h.l.",
                "lll.",
                "slsl",
                ".ss.",
            ])
        # Small azure blossoms (matching Storm Archmage azure palette)
        for bx, by in [(16, 10), (7, 14), (21, 26)]:
            stamp(bx, by, [
                ".B.",
                "bYb",
                ".s.",
            ])
    else:
        # Grass Variant C: calm subtle moss patch + soft blades
        stamp(5, 6, [
            "..sss...",
            ".ssssss.",
            "ssss.ss.",
            ".sssss..",
            "..ss....",
        ])
        stamp(17, 17, [
            "..sss...",
            ".ssssss.",
            "sss.sss.",
            "..ssss..",
        ])
        stamp(15, 5, [".ll.", "lll.", ".s.."])
        stamp(7, 21, [".ll.", "lll.", ".s.."])
        stamp(24, 9, ["l.h", "lll", "s.s"])
        stamp(11, 14, [".l.", "ll.", ".s."])
        stamp(23, 25, [".l.", "ll.", "s.."])

    return ["".join(r) for r in grid]


def make_dirt_variant(kind: int) -> list[str]:
    W, H = 32, 32
    grid = [["d" for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, ch: str) -> None:
        if 2 <= x <= 29 and 2 <= y <= 29:
            grid[y][x] = ch

    def stamp(ox: int, oy: int, rows: list[str]) -> None:
        for ry, r in enumerate(rows):
            for rx, ch in enumerate(r):
                if ch != ".":
                    put(ox + rx, oy + ry, ch)

    if kind == 0:
        # Dirt Variant A: varied pebble cluster placement, calm fill
        stamp(4, 4, ["..ee.", ".eeee", "..ee."])
        stamp(18, 13, [".eee.", "eeeee", ".eee."])
        stamp(8, 22, ["..ee.", ".eeee", "..ee."])
        stamp(15, 5, [".rr.", "rrrr", ".rr."])
        stamp(4, 15, [".rr.", "rrr."])
        stamp(21, 22, [".rr.", "rrrr", "..r."])
        # Staggered pebbles
        stamp(11, 10, ["pr.", "rE."])
        stamp(24, 7, [".p", "rE"])
        stamp(15, 21, ["pr", "eE"])
        stamp(24, 25, ["p.", "eE"])
    elif kind == 1:
        # Dirt Variant B: worn boot/wagon rut accents & small grass tufts
        stamp(4, 7, [".ee.ee..", "eeeeeee.", "..e.ee.."])
        stamp(13, 18, ["..ee.ee.", ".eeeeeee", "..ee.e.."])
        stamp(6, 11, ["rr.rrr.."])
        stamp(15, 22, ["rrr.rr.."])
        # Sparse hardy weeds/tufts clinging to path
        stamp(22, 6, ["l.l", "sgs", ".e."])
        stamp(6, 23, [".l.", "sgs"])
        stamp(14, 12, ["pr", "eE"])
    else:
        # Dirt Variant C: fine sandy-earth speckle + small slate stone
        stamp(6, 5, [".eee.", "eeeee", "..ee."])
        stamp(18, 19, [".eee.", "eeee.", "..e.."])
        stamp(15, 8, [".rr.", "rrr."])
        stamp(7, 17, [".rr.", "rrrr"])
        # Fine dry sandy-earth dust flecks
        for sx, sy in [(11, 7), (24, 10), (14, 15), (26, 17), (10, 25), (21, 26), (5, 21)]:
            put(sx, sy, "a")
            put(sx + 1, sy, "r")
        # Embedded small slate stone
        stamp(19, 11, [
            ".LM.",
            "LMS.",
            ".Se.",
        ])
        stamp(8, 24, ["pr", "eE"])

    return ["".join(r) for r in grid]


def make_stone_variant_a() -> list[str]:
    """Matches exact slab course coordinates of M2 stone.png so edges interlock cleanly."""
    W, H = 32, 32
    grid = [["m" for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, ch: str) -> None:
        grid[y % H][x % W] = ch

    def draw_slab(x0: int, y0: int, w: int, h: int, tone: str = "normal") -> None:
        for dy in range(h):
            for dx in range(w):
                is_corner = (
                    (dx == 0 and dy == 0)
                    or (dx == w - 1 and dy == 0)
                    or (dx == 0 and dy == h - 1)
                    or (dx == w - 1 and dy == h - 1)
                )
                if is_corner:
                    put(x0 + dx, y0 + dy, "v")
                    continue
                if dy == 0 or dx == 0:
                    put(x0 + dx, y0 + dy, "H" if ((dx <= 2 and dy == 0) or (dy <= 2 and dx == 0)) else "L")
                elif dy == h - 1 or dx == w - 1:
                    put(x0 + dx, y0 + dy, "S")
                elif dx == 1 and dy == 1:
                    put(x0 + dx, y0 + dy, "L")
                else:
                    put(x0 + dx, y0 + dy, "M")
        if tone == "weathered_dark":
            for dy in range(h - 3, h - 1):
                for dx in range(3, w - 1):
                    put(x0 + dx, y0 + dy, "S")
            for dy in range(2, h - 1):
                put(x0 + w - 2, y0 + dy, "S")
        elif tone == "sunlit":
            for dx in range(1, min(6, w - 2)):
                put(x0 + dx, y0 + 1, "L")
            for dy in range(1, min(4, h - 2)):
                put(x0 + 1, y0 + dy, "L")

    draw_slab(1, 1, 16, 10, tone="sunlit")
    draw_slab(18, 1, 13, 10, tone="normal")
    draw_slab(24, 12, 15, 10, tone="weathered_dark")
    draw_slab(8, 12, 15, 10, tone="normal")
    draw_slab(29, 23, 17, 9, tone="normal")
    draw_slab(15, 23, 13, 9, tone="weathered_dark")

    for y in range(1, 11):
        put(0, y, "v")
        put(17, y, "v")
    for y in range(12, 22):
        put(7, y, "v")
        put(23, y, "v")
    for y in range(23, 32):
        put(14, y, "v")
        put(28, y, "v")

    # Prominent diagonal weathered crack across central & upper slab (subtle chiselled rim)
    for cx, cy in [
        (9, 4), (10, 5), (11, 6), (12, 7), (12, 8), (13, 9),
        (14, 13), (15, 14), (16, 15), (16, 16), (17, 17), (18, 18), (19, 19),
    ]:
        put(cx, cy, "v" if (cx + cy) % 2 == 0 else "S")
        if cy in (5, 15):
            put(cx + 1, cy, "L")

    # Extra lush mortar moss tufts
    moss_pts = [
        (16, 10, "s"), (17, 10, "g"), (18, 10, "s"),
        (16, 11, "g"), (17, 11, "l"), (18, 11, "g"),
        (17, 12, "s"),
        (6, 21, "s"), (7, 21, "g"), (8, 21, "l"), (7, 22, "g"), (8, 22, "s"),
        (22, 11, "s"), (23, 11, "g"), (23, 12, "s"),
        (14, 22, "g"), (15, 22, "l"), (14, 23, "s"),
        (28, 22, "g"), (29, 22, "l"), (28, 23, "s"), (29, 23, "s"),
    ]
    for mx, my, mch in moss_pts:
        put(mx, my, mch)

    return ["".join(r) for r in grid]


def make_sand_variant_a() -> list[str]:
    W, H = 32, 32
    grid = [["a" for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, ch: str) -> None:
        if 2 <= x <= 29 and 2 <= y <= 29:
            grid[y][x] = ch

    def stamp(ox: int, oy: int, rows: list[str]) -> None:
        for ry, r in enumerate(rows):
            for rx, ch in enumerate(r):
                if ch != ".":
                    put(ox + rx, oy + ry, ch)

    # Soft thin wind-carved dune ripples
    stamp(5, 6, ["..AAAAAA....", "AAttttttAA.."])
    stamp(16, 16, ["..AAAAAA....", "AAttttttA..."])
    stamp(4, 25, [".AAAAA...", "AtttttA.."])
    # Tiny cream seashell + smooth pebble cluster
    stamp(22, 7, [
        ".wA.",
        "wAt.",
        ".T..",
    ])
    stamp(11, 14, [
        "Aa.",
        "tT.",
        ".At",
        "..T",
    ])
    stamp(23, 24, [
        "Aa",
        "tT",
    ])

    return ["".join(r) for r in grid]


# ---------------------------------------------------------------------------
# 6. TERRAIN TRANSITIONS SHEET (256x32 = 8 frames of 32x32, binary alpha 0/255)
#    0..3: Grass Overhang Fringes (N, E, S, W) drawn atop DIRT/STONE/SAND
#    4..7: Shoreline Bank & Foam Fringes (N, E, S, W) drawn atop WATER
# ---------------------------------------------------------------------------
TRANSITION_PALETTE = {
    # Grass Overhangs
    "O": "#163020",  # deepest blade shadow / overhang lip
    "s": "#224d2c",  # dark shaded turf
    "g": "#347539",  # primary meadow turf
    "l": "#52a349",  # sunlit blade
    "h": "#82cf63",  # crisp blade tip catchlight
    # Shoreline Bank & Foam
    "T": "#7a5b3c",  # wet shaded sand bank lip
    "t": "#a88259",  # mid sand bank
    "a": "#cc9f6e",  # sunlit sand bank
    "A": "#e8c28b",  # sunlit dry bank crest
    "u": "#1d4375",  # deeper near-bank water shadow
    "q": "#3c83bf",  # shallow turquoise transition
    "c": "#72c5e8",  # bright shallow aqua
    "f": "#bdf2ff",  # bubbling white-aqua shoreline foam
}


def make_grass_overhang_north() -> list[str]:
    """Frame 0: GRASS at ty-1 (top), rounded turf lobes + blades & #163020 drop shadow y=0..5."""
    # Hand-crafted 32-wide top-edge grass overhang rows (wrapping cleanly x=0..31)
    top_rows = [
        "gggggggggggggggggggggggggsssssgg",  # y=0: matches grass.png bottom edge
        "gggllgggggggggggglllggggggggsggg",  # y=1: continuous meadow lip
        "gllgggssggggggllgggggggllgggggsg",  # y=2: undulating organic lobes
        "OOggOO.OggggsO.OOgggssO.OggggO..",  # y=3: shadow lip & overhanging clumps
        "..OO....OsOO.....OOlO....OssO...",  # y=4: rounded tuft bottoms + drop shadow
        ".........O........OO......OO....",  # y=5: trailing single shadow / blade tips
    ]
    return top_rows + ["." * 32 for _ in range(32 - len(top_rows))]


def make_grass_overhang_south() -> list[str]:
    """Frame 2: GRASS at ty+1 (bottom), sunlit rounded lobes & blades grow up y=26..31."""
    bottom_rows = [
        "......h..............h..........",  # y=26: sparse crisp sunlit blade tips
        "...l..l....ll.......ll....l.....",  # y=27: sunlit grass shoots
        "..llgll...llgl.....llgl..llg....",  # y=28: rounded sunlit tuft crowns
        "ggllgggll.ggggll.ggggggllggllg..",  # y=29: continuous feathered meadow rim
        "gggggggggggggggglgggggggggggglgg",  # y=30: lush meadow base
        "ggggggggggggggggggggggggggssgggg",  # y=31: matches grass.png top edge
    ]
    return ["." * 32 for _ in range(32 - len(bottom_rows))] + bottom_rows


def make_grass_overhang_west() -> list[str]:
    """Frame 3: GRASS at tx-1 (left), rounded turf lobes + #163020 shadow x=0..5."""
    cols_rows = [
        "gggggggggggggggggggggggggggggggg",  # x=0
        "ggllggggggggllgggggggllggggggggg",  # x=1
        "gllggssggggggggllggggggssggggllg",  # x=2
        "O.OOg.OOgsggggO.OOgsssO.OOggO.OO",  # x=3
        "..OO...O.OOssO...O.OO....O..O...",  # x=4
        "...........OO.....O.............",  # x=5
    ]
    grid = [["." for _ in range(32)] for _ in range(32)]
    for x, col in enumerate(cols_rows):
        for y, ch in enumerate(col):
            grid[y][x] = ch
    return ["".join(r) for r in grid]


def make_grass_overhang_east() -> list[str]:
    """Frame 1: GRASS at tx+1 (right), sunlit rounded grass lobes overhang left x=26..31."""
    cols_rows = [
        "gggggggggggggggggggggggggggggggg",  # x=31
        "ggggllggggggggllggggggllgggggggg",  # x=30
        "ggllggggllgggggllggggggllggggggg",  # x=29
        ".llggll.lgggllgg.llggll.lgggl...",  # x=28
        "..llg....ll.gll...llg....ll.....",  # x=27
        "...h.........h............h.....",  # x=26
    ]
    grid = [["." for _ in range(32)] for _ in range(32)]
    for dx, col in enumerate(cols_rows):
        x = 31 - dx
        for y, ch in enumerate(col):
            grid[y][x] = ch
    return ["".join(r) for r in grid]


def make_shore_bank_north() -> list[str]:
    """Frame 4: WATER borders land at ty-1: wavy sandy peninsula lobes + shallow turquoise + curved seafoam y=0..6."""
    top_rows = [
        "AAaaaAAaaaaaAAaaAAaaaaaAAaaaaAAa",  # y=0: soft sand shoreline flush with land
        "aAAaaaAAtttAAaaaAAtttAAaaaaAttTa",  # y=1: curved wet-sand coves (6-8px organic wavelets)
        "tTTtttqqqqqTTttTTqqqqqTTttqqqqTq",  # y=2: smooth shallow turquoise cradling lobes
        "qqffcccfqqqqqfffffqqqqqfffccqqqq",  # y=3: sweeping seafoam crest along coves
        "cc..cc....ccc.....cc.....cc.....",  # y=4: gentle outer shallow aqua drift
        ".......f.................f......",  # y=5: isolated seafoam bubble mote
        "................................",  # y=6
    ]
    return top_rows + ["." * 32 for _ in range(32 - len(top_rows))]


def make_shore_bank_south() -> list[str]:
    """Frame 6: WATER borders land at ty+1: sunlit wavy sandy lobes + shallow turquoise + smooth foam y=25..31."""
    bottom_rows = [
        "................................",  # y=25
        ".......f.................f......",  # y=26: isolated seafoam bubble mote
        "..cc.....cc.....ccc....cc.......",  # y=27: gentle outer shallow aqua drift
        "qqffcccfqqqqqfffffqqqqqfffccqqqq",  # y=28: sweeping seafoam crest along coves
        "qAAAqqqqqqqAAqqAAAqqqqqAAqqqqAAq",  # y=29: shallow turquoise meeting sunlit bank
        "aAAaaaAAaaAAAaaAAAaaAAaaaAAaAAAa",  # y=30: sunlit sandy bank rim facing top-left sun
        "AaaaaAAaaaaaAAaaAAaaaaaAAaaaaAAa",  # y=31: warm sandy shore base
    ]
    return ["." * 32 for _ in range(32 - len(bottom_rows))] + bottom_rows


def make_shore_bank_west() -> list[str]:
    """Frame 7: WATER borders land at tx-1: wavy shaded sandy lobes + shallow turquoise + smooth foam x=0..6."""
    cols_rows = [
        "AAaaaAAaaaaaAAaaAAaaaaaAAaaaaAAa",  # x=0
        "aAAaaaAAtttAAaaaAAtttAAaaaaAttTa",  # x=1
        "tTTtttqqqqqTTttTTqqqqqTTttqqqqTq",  # x=2
        "qqffcccfqqqqqfffffqqqqqfffccqqqq",  # x=3
        "cc..cc....ccc.....cc.....cc.....",  # x=4
        ".......f.................f......",  # x=5
    ]
    grid = [["." for _ in range(32)] for _ in range(32)]
    for x, col in enumerate(cols_rows):
        for y, ch in enumerate(col):
            grid[y][x] = ch
    return ["".join(r) for r in grid]


def make_shore_bank_east() -> list[str]:
    """Frame 5: WATER borders land at tx+1: sunlit wavy sandy lobes + shallow turquoise + smooth foam x=25..31."""
    cols_rows = [
        "AaaaaAAaaaaaAAaaAAaaaaaAAaaaaAAa",  # x=31
        "aAAaaaAAaaAAAaaAAAaaAAaaaAAaAAAa",  # x=30
        "qAAAqqqqqqqAAqqAAAqqqqqAAqqqqAAq",  # x=29
        "qqffcccfqqqqqfffffqqqqqfffccqqqq",  # x=28
        "..cc.....cc.....ccc....cc.......",  # x=27
        ".......f.................f......",  # x=26
    ]
    grid = [["." for _ in range(32)] for _ in range(32)]
    for dx, col in enumerate(cols_rows):
        x = 31 - dx
        for y, ch in enumerate(col):
            grid[y][x] = ch
    return ["".join(r) for r in grid]


def scale_nearest(im: Image.Image, scale: int) -> Image.Image:
    im = im.convert("RGBA")
    w, h = im.size
    raw = im.tobytes()
    row_stride = w * 4
    out = bytearray(w * scale * h * scale * 4)
    dst_stride = w * scale * 4
    for y in range(h):
        src_row = raw[y * row_stride : (y + 1) * row_stride]
        scaled_row = b"".join(src_row[i : i + 4] * scale for i in range(0, row_stride, 4))
        for sy in range(scale):
            pos = (y * scale + sy) * dst_stride
            out[pos : pos + dst_stride] = scaled_row
    return Image.frombytes("RGBA", (w * scale, h * scale), bytes(out))


def build_water_gif(water_frames: list[Image.Image], sand: Image.Image, shore_n: Image.Image, shore_w: Image.Image, out_path: Path) -> None:
    """6x nearest-neighbor 2x2 tiled water + bank loop GIF with shared global palette."""
    scale = 6
    base_sz = 64  # 2x2 tiles
    rgb_frames = []
    for wi, wimg in enumerate(water_frames):
        canvas = Image.new("RGBA", (base_sz, base_sz), (0, 0, 0, 255))
        for ty in range(2):
            for tx in range(2):
                canvas.paste(wimg, (tx * 32, ty * 32))
                if ty == 0:
                    canvas.alpha_composite(shore_n, (tx * 32, 0))
                if tx == 0:
                    canvas.alpha_composite(shore_w, (0, ty * 32))
        zoomed = scale_nearest(canvas, scale)
        rgb_frames.append(zoomed.convert("RGB"))

    strip = Image.new("RGB", (rgb_frames[0].width * len(rgb_frames), rgb_frames[0].height))
    for i, rf in enumerate(rgb_frames):
        strip.paste(rf, (i * rf.width, 0))
    master_pal = strip.quantize(colors=64, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    gif_frames = [rf.quantize(palette=master_pal, dither=Image.Dither.NONE) for rf in rgb_frames]

    out_path.parent.mkdir(parents=True, exist_ok=True)
    gif_frames[0].save(
        out_path,
        save_all=True,
        append_images=gif_frames[1:],
        duration=160,
        loop=0,
        disposal=2,
    )


def main() -> None:
    TERRAIN_OUT.mkdir(parents=True, exist_ok=True)
    ANIM_OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    # 1. Water (32x32 Frame 0 + 128x32 4-frame sheet)
    water_rows_list = [make_water_frame(i) for i in range(4)]
    save_grid_json(
        "water",
        32,
        32,
        WATER_PALETTE,
        [(f"water_{i}", 160, rows) for i, rows in enumerate(water_rows_list)],
        pivot=[16, 16],
    )
    water_imgs = [render_rows(WATER_PALETTE, rows) for rows in water_rows_list]
    water_imgs[0].save(TERRAIN_OUT / "water.png", "PNG")
    water_sheet = render_sheet(water_imgs)
    water_sheet.save(ANIM_OUT / "water_sheet.png", "PNG")

    # 2. Sand (32x32)
    sand_rows = make_sand_grid()
    save_grid_json("sand", 32, 32, SAND_PALETTE, [("sand", 100, sand_rows)], pivot=[16, 16])
    sand_img = render_rows(SAND_PALETTE, sand_rows)
    sand_img.save(TERRAIN_OUT / "sand.png", "PNG")

    # 3. Bridges (32x32 horizontal & vertical)
    bh_rows = make_bridge_h_grid()
    save_grid_json("bridge_h", 32, 32, BRIDGE_PALETTE, [("bridge_h", 100, bh_rows)], pivot=[16, 16])
    bh_img = render_rows(BRIDGE_PALETTE, bh_rows)
    bh_img.save(TERRAIN_OUT / "bridge_h.png", "PNG")

    bv_rows = make_bridge_v_grid()
    save_grid_json("bridge_v", 32, 32, BRIDGE_PALETTE, [("bridge_v", 100, bv_rows)], pivot=[16, 16])
    bv_img = render_rows(BRIDGE_PALETTE, bv_rows)
    bv_img.save(TERRAIN_OUT / "bridge_v.png", "PNG")

    # 4. Terrain Variants Sheet (256x32, 8 frames)
    variant_rows_list = [
        make_grass_variant(0),
        make_grass_variant(1),
        make_grass_variant(2),
        make_dirt_variant(0),
        make_dirt_variant(1),
        make_dirt_variant(2),
        make_stone_variant_a(),
        make_sand_variant_a(),
    ]
    variant_imgs = [render_rows(VARIANT_PALETTE, r) for r in variant_rows_list]
    variants_sheet = render_sheet(variant_imgs)
    variants_sheet.save(ANIM_OUT / "terrain_variants_sheet.png", "PNG")

    # 5. Terrain Transitions Sheet (256x32, 8 frames)
    transition_rows_list = [
        make_grass_overhang_north(),
        make_grass_overhang_east(),
        make_grass_overhang_south(),
        make_grass_overhang_west(),
        make_shore_bank_north(),
        make_shore_bank_east(),
        make_shore_bank_south(),
        make_shore_bank_west(),
    ]
    transition_imgs = [render_rows(TRANSITION_PALETTE, r) for r in transition_rows_list]
    transitions_sheet = render_sheet(transition_imgs)
    transitions_sheet.save(ANIM_OUT / "terrain_transitions_sheet.png", "PNG")

    # 6. Animated water GIF (6x 2x2 tiled water + shore loop)
    build_water_gif(
        water_imgs,
        sand_img,
        transition_imgs[4],
        transition_imgs[7],
        PREVIEW_DIR / "water_anim.gif",
    )

    print("Milestone 3 terrain tiles & sheets generated successfully.")


if __name__ == "__main__":
    main()
