#!/usr/bin/env python3
"""
Hand-crafted seamless 32x32 terrain tiles for Spell Storm Milestone 2.
Generates editable pixel-art-studio JSON sources under assets/sources/terrain/
and exports binary-alpha 32x32 PNGs to assets/textures/terrain/.
"""

import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
SRC_DIR = ROOT / "assets/sources/terrain"
OUT_DIR = ROOT / "assets/textures/terrain"
PREVIEW_DIR = ROOT / "docs/previews/milestone2"

GRASS_PALETTE = {
    "D": "#163020",  # deepest tuft root shadow
    "s": "#224d2c",  # shadow turf & deep meadow hollows
    "g": "#347539",  # calm primary meadow turf
    "l": "#52a349",  # sunlit turf swell & grass blades
    "h": "#82cf63",  # crisp blade tip catchlight (used very sparingly)
}

def make_grass_grid() -> list[str]:
    W, H = 32, 32
    grid = [["g" for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, ch: str) -> None:
        grid[y % H][x % W] = ch

    def stamp(ox: int, oy: int, rows: list[str]) -> None:
        for dy, r in enumerate(rows):
            for dx, ch in enumerate(r):
                if ch != ".":
                    put(ox + dx, oy + dy, ch)

    # Organic, ragged moss/turf patches (calm, feathered into the g meadow floor)
    stamp(24, 28, [
        "..ss..s...",
        ".ssssss...",
        "ssss.ss...",
        ".sssss....",
        "..ss......",
    ])
    stamp(3, 10, [
        "..sss.....",
        ".ssss.s...",
        "ssssss....",
        ".sss......",
    ])
    stamp(14, 20, [
        "...ss.....",
        ".ssssss...",
        "sss.ss....",
        "..ss......",
    ])

    # Gentle sunlit blade clusters (restrained, feathered so no spotted repeat)
    stamp(10, 3, [
        ".l..l.",
        "lll.l.",
        ".lll..",
    ])
    stamp(25, 15, [
        "..l...",
        ".lll..",
        "ll.ll.",
    ])
    stamp(2, 22, [
        ".l.l..",
        "lll...",
        ".ll...",
    ])

    # Subtle directional grass-blade strokes woven across the turf
    blade_strokes = [
        (2, 4, [("l", 0, 0), ("l", 0, 1), ("s", 1, 1)]),
        (19, 4, [("l", 0, 0), ("s", 0, 1)]),
        (29, 8, [("l", 0, 0), ("l", 1, -1), ("s", 0, 1)]),
        (13, 14, [("l", 0, 0), ("l", 0, 1), ("s", -1, 1)]),
        (21, 16, [("l", 0, 0), ("s", 0, 1)]),
        (8, 28, [("l", 0, 0), ("l", 1, -1), ("s", 0, 1)]),
        (21, 27, [("l", 0, 0), ("l", 0, 1)]),
        (16, 11, [("s", 0, 0), ("l", 0, -1)]),
        (28, 2, [("s", 0, 0), ("l", 1, -1)]),
    ]
    for bx, by, pts in blade_strokes:
        for ch, dx, dy in pts:
            put(bx + dx, by + dy, ch)

    # Intentional multi-blade grass tufts (soft 's' root, 'l' blades, rare 'h' tip)
    stamp(14, 5, [
        "h..l.",
        "l.ll.",
        "slsl.",
        ".ss..",
    ])
    stamp(27, 17, [
        ".l.h",
        "ll.l",
        "slls",
        ".ss.",
    ])
    stamp(5, 24, [
        "l.l.",
        "lll.",
        "slsl",
        ".Ds.",
    ])
    stamp(19, 10, [
        ".l..",
        "ll.l",
        "s.ss",
    ])

    return ["".join(row) for row in grid]


DIRT_PALETTE = {
    "D": "#382419",  # deep pebble underside shadow
    "s": "#543826",  # compacted earth / soft rut
    "d": "#785238",  # primary warm packed dirt
    "l": "#9c6f4f",  # sunlit dry earth ridge
    "h": "#bf926b",  # pebble top-left highlight
}

def make_dirt_grid() -> list[str]:
    W, H = 32, 32
    grid = [["d" for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, ch: str) -> None:
        grid[y % H][x % W] = ch

    def stamp(ox: int, oy: int, rows: list[str]) -> None:
        for dy, r in enumerate(rows):
            for dx, ch in enumerate(r):
                if ch != ".":
                    put(ox + dx, oy + dy, ch)

    # Irregular, feathered compacted soil patches (NO oval saucers!)
    stamp(2, 1, [
        "..ss.s....",
        ".ssssss...",
        "sss.ssss..",
        "..ssss.s..",
        "...ss.....",
    ])
    stamp(17, 11, [
        "..sss.....",
        ".ssss.ss..",
        "sssssss...",
        ".sssss....",
        "..ss..s...",
    ])
    stamp(6, 22, [
        "...ss.s...",
        ".ssssss...",
        "sss.sss...",
        "..ssss....",
    ])
    stamp(26, 28, [
        ".ss..s....",
        "ssssss....",
        ".sss.s....",
        "..ss......",
    ])

    # Sunlit dry earth ridges with broken organic silhouettes
    stamp(15, 3, [
        ".ll.ll....",
        "lllllll...",
        ".lll.ll...",
        "..ll......",
    ])
    stamp(2, 14, [
        "..ll..l...",
        ".llllll...",
        "lll.lll...",
        ".lll......",
    ])
    stamp(21, 19, [
        ".ll.l.....",
        "llllll....",
        ".ll.lll...",
        "..ll......",
    ])
    stamp(13, 27, [
        "..lll.....",
        ".ll.lll...",
        "..llll....",
    ])

    # Subtle packed wagon/foot track scuffs (broken 2-4px streaks)
    for sx, sy in [(22, 8), (11, 16), (27, 15), (4, 26), (19, 30)]:
        put(sx, sy, "s")
        put(sx + 1, sy, "s")
        put(sx + 3, sy, "s")

    # Small varied pebble clusters (staggered asymmetrically, calm contrast)
    stamp(10, 8, [
        "hl...",
        "ls...",
        ".D.l.",
        "...s.",
    ])
    stamp(25, 13, [
        "hll",
        "lsD",
    ])
    stamp(16, 22, [
        ".hl.",
        "hlD.",
        "sD..",
    ])
    stamp(4, 29, [
        "hl",
        "sD",
    ])

    return ["".join(row) for row in grid]


STONE_PALETTE = {
    "v": "#1e1829",  # deep indigo mortar crevice
    "m": "#282c38",  # mortar shadow
    "s": "#3e4556",  # shaded stone bevel / dark slab / rune groove
    "b": "#5a657a",  # primary slate flagstone face
    "l": "#808fa6",  # top-left sunlit beveled rim
    "h": "#aebdcf",  # top-left crisp corner glint
    "k": "#224d2c",  # moss shadow in mortar
    "g": "#347539",  # moss mid
    "L": "#52a349",  # moss catchlight
}

def make_stone_grid() -> list[str]:
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
                    if (dx <= 2 and dy == 0) or (dy <= 2 and dx == 0):
                        put(x0 + dx, y0 + dy, "h")
                    else:
                        put(x0 + dx, y0 + dy, "l")
                elif dy == h - 1 or dx == w - 1:
                    put(x0 + dx, y0 + dy, "s")
                elif dx == 1 and dy == 1:
                    put(x0 + dx, y0 + dy, "l")
                else:
                    put(x0 + dx, y0 + dy, "b")

        if tone == "weathered_dark":
            for dy in range(h - 3, h - 1):
                for dx in range(3, w - 1):
                    put(x0 + dx, y0 + dy, "s")
            for dy in range(2, h - 1):
                put(x0 + w - 2, y0 + dy, "s")
        elif tone == "sunlit":
            for dx in range(1, min(6, w - 2)):
                put(x0 + dx, y0 + 1, "l")
            for dy in range(1, min(4, h - 2)):
                put(x0 + 1, y0 + dy, "l")

    draw_slab(1, 1, 16, 10, tone="normal")
    draw_slab(18, 1, 13, 10, tone="weathered_dark")
    draw_slab(24, 12, 15, 10, tone="sunlit")
    draw_slab(8, 12, 15, 10, tone="normal")
    draw_slab(29, 23, 17, 9, tone="weathered_dark")
    draw_slab(15, 23, 13, 9, tone="normal")

    for y in range(1, 11):
        put(0, y, "v")
        put(17, y, "v")
    for y in range(12, 22):
        put(7, y, "v")
        put(23, y, "v")
    for y in range(23, 32):
        put(14, y, "v")
        put(28, y, "v")

    # Subtle weathered rune carving on Slab D
    for rx, ry in [
        (15, 14),
        (14, 15), (16, 15),
        (13, 16), (17, 16),
        (14, 17), (16, 17),
        (15, 18),
    ]:
        put(rx, ry, "s")
    put(15, 15, "l")
    put(16, 16, "l")

    # Natural hairline stone fractures
    for cx, cy in [(5, 7), (6, 8), (7, 8), (8, 9)]:
        put(cx, cy, "s")
    for cx, cy in [(22, 25), (21, 26), (21, 27)]:
        put(cx, cy, "s")

    # Two asymmetric mortar moss clusters
    for mx, my, ch in [
        (16, 10, "k"), (17, 10, "g"), (18, 10, "k"),
        (16, 11, "g"), (17, 11, "L"), (18, 11, "g"),
        (17, 12, "k"),
    ]:
        put(mx, my, ch)

    for mx, my, ch in [
        (28, 22, "g"), (29, 22, "L"),
        (28, 23, "k"), (29, 23, "k"),
    ]:
        put(mx, my, ch)

    return ["".join(row) for row in grid]


def save_grid_source(name: str, w: int, h: int, palette: dict[str, str], rows: list[str]) -> Path:
    SRC_DIR.mkdir(parents=True, exist_ok=True)
    doc = {
        "version": 1,
        "name": name,
        "canvas": [w, h],
        "pivot": [w // 2, h // 2],
        "palette": palette,
        "frames": [
            {
                "name": name,
                "duration_ms": 100,
                "rows": rows,
            }
        ],
    }
    path = SRC_DIR / f"{name}.json"
    path.write_text(json.dumps(doc, indent=2) + "\n")
    return path


def render_grid_png(palette: dict[str, str], rows: list[str], out_path: Path) -> Image.Image:
    h = len(rows)
    w = len(rows[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            hex_col = palette[ch].lstrip("#")
            r = int(hex_col[0:2], 16)
            g = int(hex_col[2:4], 16)
            b = int(hex_col[4:6], 16)
            a = int(hex_col[6:8], 16) if len(hex_col) == 8 else 255
            px[x, y] = (r, g, b, a)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_path, "PNG")
    return img


def main() -> None:
    tiles = [
        ("grass", GRASS_PALETTE, make_grass_grid()),
        ("dirt", DIRT_PALETTE, make_dirt_grid()),
        ("stone", STONE_PALETTE, make_stone_grid()),
    ]
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    imgs = []
    for name, pal, rows in tiles:
        src = save_grid_source(name, 32, 32, pal, rows)
        out = OUT_DIR / f"{name}.png"
        im = render_grid_png(pal, rows, out)
        imgs.append((name, im))
        print(f"Wrote {src} -> {out} ({im.size})")

    tile_sz = 32 * 3
    strip = Image.new("RGBA", (tile_sz * 3 * 3 + 32, tile_sz * 3), (20, 16, 26, 255))
    for idx, (name, im) in enumerate(imgs):
        zoomed = im.resize((tile_sz, tile_sz), Image.Resampling.NEAREST)
        ox = idx * (tile_sz * 3 + 16)
        for ty in range(3):
            for tx in range(3):
                strip.paste(zoomed, (ox + tx * tile_sz, ty * tile_sz))
    check_path = SRC_DIR / "_tiling_check_3x3.png"
    strip.save(check_path)


if __name__ == "__main__":
    main()
