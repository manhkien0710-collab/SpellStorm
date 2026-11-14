#!/usr/bin/env python3
"""
Hand-crafted Milestone 3 Environment Props, Arena Courtyard Floor Tile,
and M3 Contact Sheet + Biome Transitions Preview for Spell Storm.

PRESERVATION GUARANTEE:
Never writes or modifies M2 canonical tree_oak.png, rock.png, bush.png,
campfire.png, campfire_sheet.png, grass.png, dirt.png, or stone.png.
"""

import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
SRC_DIR = ROOT / "assets/sources/environment"
ENV_OUT = ROOT / "assets/textures/environment"
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


def save_grid_json(
    name: str,
    w: int,
    h: int,
    palette: dict[str, str],
    rows: list[str],
    pivot: list[int] | None = None,
) -> Path:
    SRC_DIR.mkdir(parents=True, exist_ok=True)
    assert len(rows) == h, f"{name}: expected {h} rows, got {len(rows)}"
    for i, r in enumerate(rows):
        assert len(r) == w, f"{name} row {i}: expected {w} cols, got {len(r)} ({r})"
        for ch in r:
            assert ch == "." or ch in palette, f"{name} row {i}: unknown key {ch!r}"
    doc = {
        "version": 1,
        "name": name,
        "canvas": [w, h],
        "pivot": pivot if pivot is not None else [w // 2, h],
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


# ---------------------------------------------------------------------------
# 1. FLOOR_TILE (64x64, alpha 255) — Ancient carved temple courtyard stone slabs
# ---------------------------------------------------------------------------
FLOOR_PALETTE = {
    "v": "#1e1829",  # deep indigo mortar crevice
    "m": "#282c38",  # mortar & carved rune channel
    "s": "#3e4556",  # shaded stone bevel
    "b": "#5a657a",  # primary slate temple stone
    "l": "#808fa6",  # sunlit top-left bevel
    "h": "#aebdcf",  # crisp top-left glint
    "k": "#224d2c",  # moss shadow
    "g": "#347539",  # moss mid
    "L": "#52a349",  # moss sunlit catchlight
    "c": "#1c698c",  # faint ancient arcane rune inlay shadow
    "C": "#33b5e5",  # faint ancient rune sparkle
}


def make_floor_tile_grid() -> list[str]:
    W, H = 64, 64
    grid = [["m" for _ in range(W)] for _ in range(H)]

    def put(x: int, y: int, ch: str) -> None:
        if 0 <= x < W and 0 <= y < H:
            grid[y][x] = ch

    def draw_bevel_slab(x0: int, y0: int, w: int, h: int, tone: str = "normal") -> None:
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
                elif dy == 0 or dx == 0:
                    put(x0 + dx, y0 + dy, "h" if (dx <= 3 and dy == 0) or (dy <= 3 and dx == 0) else "l")
                elif dy == h - 1 or dx == w - 1:
                    put(x0 + dx, y0 + dy, "s")
                elif dx == 1 and dy == 1:
                    put(x0 + dx, y0 + dy, "l")
                else:
                    put(x0 + dx, y0 + dy, "b")
        if tone == "dark":
            for dy in range(h - 3, h - 1):
                for dx in range(2, w - 1):
                    put(x0 + dx, y0 + dy, "s")
            for dy in range(2, h - 1):
                put(x0 + w - 2, y0 + dy, "s")
        elif tone == "light":
            for dx in range(1, min(7, w - 2)):
                put(x0 + dx, y0 + 1, "l")
            for dy in range(1, min(6, h - 2)):
                put(x0 + 1, y0 + dy, "l")

    # Outer courtyard border ring of 12 dressed stone slabs framing central 38x38 altar slab
    # Top row (y=1..11)
    draw_bevel_slab(1, 1, 20, 11, "light")
    draw_bevel_slab(22, 1, 20, 11, "normal")
    draw_bevel_slab(43, 1, 20, 11, "dark")
    # Bottom row (y=52..62)
    draw_bevel_slab(1, 52, 20, 11, "normal")
    draw_bevel_slab(22, 52, 20, 11, "dark")
    draw_bevel_slab(43, 52, 20, 11, "dark")
    # Left border slabs (y=13..50)
    draw_bevel_slab(1, 13, 11, 18, "light")
    draw_bevel_slab(1, 32, 11, 19, "normal")
    # Right border slabs (y=13..50)
    draw_bevel_slab(52, 13, 11, 19, "normal")
    draw_bevel_slab(52, 33, 11, 18, "dark")

    # Central ancient temple courtyard dais slab (x=13..50, y=13..50)
    draw_bevel_slab(13, 13, 38, 38, "normal")

    # Carved inner square rune border (inset 4px inside central dais: x=17..46, y=17..46)
    for i in range(17, 47):
        put(i, 17, "s")
        put(i, 46, "l" if i in range(18, 46) else "s")
        put(17, i, "s")
        put(46, i, "l" if i in range(18, 46) else "s")
    for i in range(18, 46):
        put(i, 18, "m")
        put(i, 45, "m")
        put(18, i, "m")
        put(45, i, "m")

    # Etched corner diamond notches on the rune frame
    for cx, cy in [(20, 20), (43, 20), (20, 43), (43, 43)]:
        put(cx, cy, "c")
        put(cx - 1, cy, "s")
        put(cx + 1, cy, "s")
        put(cx, cy - 1, "s")
        put(cx, cy + 1, "s")

    # Central carved arcane compass/diamond sigil (centered at 31.5, 31.5 -> x=24..39, y=24..39)
    for d in range(8):
        pts = [
            (31 - d, 24 + d), (32 + d, 24 + d),
            (31 - d, 39 - d), (32 + d, 39 - d),
        ]
        for px, py in pts:
            put(px, py, "m")
            put(px, py + 1, "l" if py < 32 else "s")
    # Inner carved ring crosshairs
    for d in range(26, 38):
        if d not in (31, 32):
            put(d, 31, "s")
            put(d, 32, "s")
            put(31, d, "s")
            put(32, d, "s")
    # Subtle glowing arcane center jewel inlay (restrained so combat spells still pop!)
    for px, py, ch in [
        (31, 30, "c"), (32, 30, "c"),
        (30, 31, "c"), (31, 31, "C"), (32, 31, "C"), (33, 31, "c"),
        (30, 32, "c"), (31, 32, "C"), (32, 32, "c"), (33, 32, "s"),
        (31, 33, "s"), (32, 33, "s"),
    ]:
        put(px, py, ch)

    # Natural weathered hairline fractures across border & dais stones
    cracks = [
        (7, 4), (8, 5), (9, 5), (10, 6),
        (51, 56), (50, 57), (49, 58), (48, 58),
        (24, 15), (25, 16), (40, 48), (41, 49), (42, 50),
    ]
    for cx, cy in cracks:
        put(cx, cy, "v" if (cx + cy) % 2 == 0 else "s")

    # Corner mortar moss tufts
    moss_clusters = [
        (11, 11, "k"), (12, 11, "g"), (13, 11, "k"),
        (11, 12, "g"), (12, 12, "L"), (13, 12, "g"), (12, 13, "k"),
        (50, 11, "k"), (51, 11, "g"), (52, 11, "k"), (51, 12, "L"), (52, 12, "g"),
        (11, 50, "k"), (12, 50, "g"), (12, 51, "L"), (13, 51, "g"), (12, 52, "k"),
        (50, 51, "g"), (51, 51, "L"), (51, 52, "g"), (52, 52, "k"),
        (42, 6, "k"), (43, 6, "g"), (42, 7, "L"),
    ]
    for mx, my, ch in moss_clusters:
        put(mx, my, ch)

    return ["".join(r) for r in grid]


# ---------------------------------------------------------------------------
# 2. TREE_PINE (24x48, binary alpha 0/255, anchor 12,48)
# ---------------------------------------------------------------------------
PINE_PALETTE = {
    "o": "#14101a",  # 1px outer silhouette outline
    "i": "#19222d",  # deep indigo bough underside shadow
    "v": "#163020",  # deepest pine needle shadow
    "d": "#224d2c",  # dark conifer foliage
    "g": "#347539",  # mid evergreen needle mass
    "l": "#52a349",  # sunlit left-top bough fronds
    "h": "#82cf63",  # crisp needle tip highlight
    "H": "#b8ed87",  # rare crown tip catchlight
    "k": "#2b1a14",  # dark bark shadow
    "w": "#472b1e",  # shaded bark
    "u": "#6e442c",  # mid bark
    "U": "#99633f",  # sunlit left bark ridge
}

TREE_PINE_ROWS = [
    # 012345678901234567890123 (24 cols x 48 rows)
    "........................",  # 00
    "...........oo...........",  # 01
    "..........oHho..........",  # 02
    ".........oHllgo.........",  # 03
    ".........ohlggo.........",  # 04
    "........oHlggddo........",  # 05
    "........ohllgddo........",  # 06
    ".......oHlgggdvvo.......",  # 07
    "......oHlgglggdvvo......",  # 08
    "......ohlgllggdvio......",  # 09
    ".....ooggllggddviio.....",  # 10
    ".....oHlgggvvddioo......",  # 11
    "....oHllgdviivvvdo......",  # 12
    "....ohlgllggdvddggo.....",  # 13
    "...oHlglhllggdddgddo....",  # 14
    "...ohlgggglggdddgdvo....",  # 15
    "..oHllgggglggddvvddvo...",  # 16
    "..ohllggddgddvvivvdio...",  # 17
    "..oogddvvviivvviiiioo...",  # 18
    "...oHllggviigggdddvo....",  # 19
    "..oHhllgllgglggddgdvo...",  # 20
    ".oHllgglhllgllgdddgdvo..",  # 21
    ".ohllggllggggggdddgddvo.",  # 22
    "oHllgggggllggggddvvddvo.",  # 23
    "ohllgddgglgggddddvvddio.",  # 24
    ".ooggdvvviiivvvvviviiio.",  # 25
    "..oHlgdviiiiiggddviiio..",  # 26
    ".oHllgglllgggggddddggo..",  # 27
    ".ohllglhllglgggdddgddvo.",  # 28
    "oHllggllggllgggdddgddvvo",  # 29
    "ohllgggggggggggddddvvddo",  # 30
    "olgggddgglgggddddvvddvio",  # 31
    "oggdddvvviiidddvvviiiio.",  # 32
    ".ogdviigllggddviivvdggo.",  # 33
    "oHlgdgglhlggdddgddddvvo.",  # 34
    "ohlgdggllggddddvvddvvio.",  # 35
    ".ooggdvviiivvvvvviiioo..",  # 36
    "...oovviioookkkooiio....",  # 37
    ".....oooo..oUukko.......",  # 38
    "...........oUuwko.......",  # 39
    "...........oUuwko.......",  # 40
    "...........oUuwko.......",  # 41
    "..........oUUuwkko......",  # 42
    "..........oUuuwkko......",  # 43
    ".........oUUuuwkkko.....",  # 44
    ".........oUukwookko.....",  # 45
    "..........ooo..ooo......",  # 46
    "........................",  # 47
]


# ---------------------------------------------------------------------------
# 3. ROCK_LARGE (32x32, binary alpha 0/255, anchor 16,32)
# ---------------------------------------------------------------------------
ROCK_LARGE_PALETTE = {
    "o": "#14101a",  # outer 1px outline
    "s": "#282c38",  # deep crevice / base shadow
    "d": "#3e4556",  # shaded right/lower rock facet
    "m": "#5a657a",  # primary slate granite body
    "l": "#808fa6",  # sunlit top-left stone face
    "h": "#aebdcf",  # crisp top-left ridge glint
    "k": "#224d2c",  # moss shadow lip
    "g": "#347539",  # moss mid
    "L": "#52a349",  # sunlit moss
    "H": "#82cf63",  # bright moss catchlight
    "c": "#1c698c",  # arcane rune crack glow shadow
    "C": "#33b5e5",  # arcane rune crack cyan
    "E": "#8ff5ff",  # bright rune core spark
}

ROCK_LARGE_ROWS = [
    # 01234567890123456789012345678901 (32 cols x 32 rows)
    "................................",  # 00
    "................................",  # 01
    "................................",  # 02
    "................................",  # 03
    ".........oooooo.................",  # 04
    "........ohhhllmo................",  # 05
    ".......ohHlHLLmmo...............",  # 06
    "......ohLLHHgLgddo..............",  # 07
    ".....ohhlLggkgmddo...ooooo......",  # 08
    ".....ohllmgkgmddso..ohhhllmo....",  # 09
    "....ohllmmkkmmmdso.ohhllmmmdo...",  # 10
    "....ohllmmmmmdddsoohllmmmdddo...",  # 11
    "...oohllmmmddddssmmmllmmmdddo...",  # 12
    "..oHhllmmmmdddssmmlmmmmddddsdo..",  # 13
    "..oHllmmmmddddsmllmmCCmddddsdo..",  # 14
    "..ohllmmmmmddsmmllcCEECmddssddo.",  # 15
    "..olllmmmmddddmmllcCEcmdddssddo.",  # 16
    ".oohllmmmdddddmmsdCCcmddddssddo.",  # 17
    ".ohllmmmmddddddmsCECcddddssdddo.",  # 18
    ".ohllmmmdddddddmsCCcmdddsssdddo.",  # 19
    ".olmkkmmddddddddsdccdddssssddso.",  # 20
    ".okLLgkmmdddddddddddddsssssddso.",  # 21
    ".okHLgkkmmdddddddddddsssssddsoo.",  # 22
    ".ookggkkmdddddddddddssssddggko..",  # 23
    "..ookkkmddddddddddsssssddgLHko..",  # 24
    "..oolmmmdddddssssssssssddkgkko..",  # 25
    "..oommddddssssssssssssssddkkso..",  # 26
    "..oossssssdssssssssssssssssooo..",  # 27
    "...oosssssdssssssssssssssso.....",  # 28
    "....oossssssssssssssssssoo......",  # 29
    ".....oooooooooooooooooooo.......",  # 30
    "................................",  # 31
]


# ---------------------------------------------------------------------------
# 4. LOG (28x18, binary alpha 0/255, anchor 14,18)
# ---------------------------------------------------------------------------
LOG_PALETTE = {
    "o": "#14101a",  # outer outline
    "i": "#1e1829",  # deep hollow core darkness
    "k": "#2b1a14",  # dark bark crevice
    "w": "#472b1e",  # shaded oak bark
    "u": "#6e442c",  # mid oak bark
    "U": "#99633f",  # sunlit oak bark & outer ring
    "B": "#bf926b",  # heartwood mid ring
    "R": "#e8c28b",  # sunlit heartwood cut face
    "v": "#163020",  # moss shadow
    "d": "#224d2c",  # dark moss
    "g": "#347539",  # mid moss
    "l": "#52a349",  # sunlit moss
    "h": "#82cf63",  # moss catchlight
    "f": "#9e631b",  # bracket fungi shadow
    "F": "#d99726",  # warm amber bracket fungi
    "Y": "#ffe066",  # fungi top-left rim highlight
}

LOG_ROWS = [
    # 0123456789012345678901234567 (28 cols x 18 rows)
    "............................",  # 00
    "............................",  # 01
    "............................",  # 02
    ".........ooooooo............",  # 03
    "...oooooooohhhlllooooooo....",  # 04
    "..oRRRRBUoohllllgggguUUUo...",  # 05
    ".oRBBiiBBUoUUllgggddduUBRo..",  # 06
    ".oRBiiiiiBUuUlggddvukwUBBko.",  # 07
    ".oRBiiiiikBUugddvvuuwkRBUwo.",  # 08
    ".oRBUiiikwBUukdvwukYFFukBwo.",  # 09
    ".oUBBUkkwuuukwwuuukFfukwko..",  # 10
    "..oUUBuuwkkkwwuYFFuwkkwko...",  # 11
    "..oowuuuuwwwuuukFfukwwkko...",  # 12
    "...oowwwkkkkwwukkkkwwkoo....",  # 13
    "....ooowwkkkkkkwwkkooo......",  # 14
    "......ooooooooooooo.........",  # 15
    "............................",  # 16
    "............................",  # 17
]


# ---------------------------------------------------------------------------
# 5. RUINS (32x48, binary alpha 0/255, anchor 16,48)
# ---------------------------------------------------------------------------
RUINS_PALETTE = {
    "o": "#14101a",  # outer 1px outline
    "s": "#282c38",  # deep stone crevice / shadow
    "d": "#3e4556",  # shaded right stone facet
    "m": "#5a657a",  # primary temple limestone/granite
    "l": "#808fa6",  # sunlit left stone fluting
    "h": "#aebdcf",  # crisp top-left fracture glint
    "v": "#163020",  # vine/moss deep shadow
    "k": "#224d2c",  # vine/moss dark
    "g": "#347539",  # vine/moss mid
    "L": "#52a349",  # vine/moss sunlit leaf
    "H": "#82cf63",  # vine/moss catchlight
    "c": "#1c698c",  # carved sigil cyan glow border
    "C": "#33b5e5",  # luminous cyan carved rune
    "E": "#8ff5ff",  # bright sigil glow core
    "X": "#ffffff",  # pinpoint sigil heart
}

RUINS_ROWS = [
    # 01234567890123456789012345678901 (32 cols x 48 rows)
    "................................",  # 00
    "................................",  # 01
    "................................",  # 02
    "................................",  # 03
    ".........ooooo..................",  # 04
    "........ohhhllmooo..............",  # 05
    "........ohllmmmlmmdoo...........",  # 06
    ".......oohllmmmlmmmddoo.........",  # 07
    ".......ohhlmdmmlmdmmddo.........",  # 08
    ".......ohllmdmmlmdmmdddo........",  # 09
    ".......ohllmdmmlmdmmdddo........",  # 10
    "......oohhlmdmmlmdmmddsvo.......",  # 11
    "......ohhllmdmmlmdmddHLLko......",  # 12
    "......oohsssdsssdssddLgko.......",  # 13
    ".......ohlmdmmdmlmdmkggvo.......",  # 14
    ".......ohlmdmmdmlmdkgko.........",  # 15
    ".......ohlmdmmkmlmdmddo.........",  # 16
    ".......ohLgkmmkmlmdmddo.........",  # 17
    "......oHLLgkgkdmlmdmddo.........",  # 18
    ".......okgvvkgdmlccmddo.........",  # 19
    ".......ohmdkdkdcCECmddo.........",  # 20
    ".......ohlmdmmdcCXCmddo.........",  # 21
    ".......ohlmdmmccCECcddo.........",  # 22
    ".......ohlmdmmcCEECcddo.........",  # 23
    ".......ohlmdmmcCXECcddo.........",  # 24
    ".......ohlmdmmmcCCcmddo.........",  # 25
    ".......ohlmdmmdmcCmdddo.........",  # 26
    ".......ohlmdmmdmlmdmgkdo........",  # 27
    ".......ohlmdmmdmlmdLgko.........",  # 28
    ".......ohlmdmmdmLHLLgvo.........",  # 29
    ".......ohlmdmmdmkgkmddo.........",  # 30
    ".......ohlmdmmdmlmdmddo.........",  # 31
    ".......ohlmdmmdmlmdmddo.........",  # 32
    "......oohlmdmmdmlmdmddoo........",  # 33
    "......ohhlmdmmdmlmdmddso........",  # 34
    ".....oohllmdmmdmlmdmddsoo.......",  # 35
    ".....ohhhllllllllmddddsso.......",  # 36
    ".....ohllmmmmmmmmmmdddssso......",  # 37
    ".....ohlmmmdmmdmmmmddddsso......",  # 38
    "....oohssskkggksssdssssssso.....",  # 39
    "....ohhhllLHLgllllmddddssso.....",  # 40
    "...oohllllggkmmmmmmdddddssso....",  # 41
    "...ohlmmmmmmmmmddddddsssssso....",  # 42
    "...ohlmmmmmdmmdddddkgLgkssso....",  # 43
    "...ohssmmmmddddssskgLLgkssso....",  # 44
    "....oosssssssssssskkggkssso.....",  # 45
    ".....ooooooooooooooooooooo......",  # 46
    "................................",  # 47
]


# ---------------------------------------------------------------------------
# 6. CAMP_TENT (40x36, binary alpha 0/255, anchor 20,36)
# ---------------------------------------------------------------------------
TENT_PALETTE = {
    "o": "#14101a",  # outer 1px outline
    "i": "#1e1829",  # deep tent interior shadow
    "D": "#172047",  # sapphire canvas deep shadow
    "S": "#243b7a",  # sapphire canvas shaded right slope / fold
    "B": "#3662b8",  # primary Storm Archmage sapphire canvas
    "L": "#5296e8",  # sunlit left canvas slope
    "H": "#8fd3ff",  # crisp sunlit ridge & crease catchlight
    "g": "#6b3e12",  # gold trim shadow
    "n": "#9e631b",  # warm lantern floor glow / gold mid
    "G": "#d99726",  # rich gold stitched trim
    "Y": "#ffe066",  # bright gold trim highlight
    "W": "#fff7b8",  # gold finial glint
    "k": "#2b1a14",  # dark timber / stake shadow
    "w": "#472b1e",  # shaded timber pole
    "u": "#6e442c",  # mid timber pole & taut rope
    "U": "#99633f",  # sunlit timber pole
    "R": "#bf926b",  # sunlit guy rope
    "r": "#bf2626",  # outer lantern crimson glow
    "f": "#f25c22",  # warm lantern orange
    "a": "#ff9e24",  # bright amber lantern aura
    "y": "#ffe054",  # hot yellow lantern glass
    "c": "#fffbe6",  # white-hot lantern wick core
}

CAMP_TENT_ROWS = [
    # 0123456789012345678901234567890123456789 (40 cols x 36 rows)
    "........................................",  # 00
    "........................................",  # 01
    "...............ooo......................",  # 02
    "..............oWYGoo....................",  # 03
    "..............oUukooooooooo.............",  # 04
    ".............ooGGooUUUUuukWYo...........",  # 05
    "............oHLLBBBBBBBBSSGGo...........",  # 06
    "...........oHLBBBBBBBBSBBSSSDoo.........",  # 07
    "..........oHLLBBBBBBBBSSBBBSSDDoo.......",  # 08
    ".........oHLLBBBBBBBBSBBBBSSSDDDDo......",  # 09
    "........oHLLBBBBBBBBBSBBBBBSSSDDDDo.....",  # 10
    ".......oHLLBBBBBBBBGYGYGBBBSSSDDDDDo....",  # 11
    "......oHLLBBBBBBBBoGYiiYGoBSSSDDDDDDo...",  # 12
    ".....oHLLBBBBBBBBoGYiiiiYGoSSSDDDDDDSo..",  # 13
    ".....oHLBBBBBBBBBoGYiWWiYGoSSSDDDDDDSo..",  # 14
    "....oHLBBBBBBBBBoGiiiycyiiGoSSSDDDDDDSo.",  # 15
    "....oLBBBBBBBBBBoGiiycccyiiGoSSDDDDDDSo.",  # 16
    "...oHLBBBBBBBBBBoGiayycyyaiGoSSSDDDDDDSo",  # 17
    "...oLBBBBBBBBBBBoGiaffaaaaffiGoSSDDDDDSo",  # 18
    "..oHLBBBBBBBBBBBoGiiaffrrffaiGoSSDDDDDSo",  # 19
    "..oLBBBBBBBBBBBBoGiiaannnnaaiGoSSDDDDSSo",  # 20
    "..oLBBBBBBBBBBBBoGiiaafnnfaaiGoSSDDDDSSo",  # 21
    ".oHLBBBBBBBBGYGoGiiiaafnnfaaiGGYGoDDDSo.",  # 22
    ".oLBBBBBBBBBBoYGoiiiafnnnnfaaiiGYooDDDSo",  # 23
    ".oLBBBBBBBBBBoGooiiiannnnnnaaaiGoSDDDSo.",  # 24
    ".oRuBBBBBBBBBoGiiaffnnaaaannfaioSSDDSuRo",  # 25
    "oRkoBBBBBBBBBoGiiaffnaayyyaanfioSSSDSoRo",  # 26
    "oUkoLLLLBBBBBoGiifnnaafaaanfnnioSSSoooUo",  # 27
    "oo..oHLLLBBBBoGiifnngnnnnngnnfioSSo..oUo",  # 28
    "....oHLLLSSSoGiiinnnggggggggnnnioSo..oko",  # 29
    "....oHLLSSSSoGiikkkkkkkkkkkkkkioSSo...oo",  # 30
    "....oDDSSSSSoooooiiiiiiiiiiiiiioSSo.....",  # 31
    ".....oooooooooooooooooooooooooooo.......",  # 32
    "........................................",  # 33
    "........................................",  # 34
    "........................................",  # 35
]


# ---------------------------------------------------------------------------
# Contact Sheet & Biome Transitions Preview Builders
# ---------------------------------------------------------------------------
def make_checkerboard(w: int, h: int, light: bool = False, cell: int = 8) -> Image.Image:
    c1 = (232, 236, 244, 255) if light else (28, 24, 38, 255)
    c2 = (212, 218, 228, 255) if light else (40, 34, 54, 255)
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = c1 if ((x // cell) + (y // cell)) % 2 == 0 else c2
    return img


def build_contact_sheet(
    water: Image.Image,
    sand: Image.Image,
    bridge_h: Image.Image,
    bridge_v: Image.Image,
    floor_tile: Image.Image,
    tree_pine: Image.Image,
    rock_large: Image.Image,
    log_img: Image.Image,
    ruins: Image.Image,
    camp_tent: Image.Image,
    water_sheet: Image.Image,
    variants_sheet: Image.Image,
    transitions_sheet: Image.Image,
) -> Image.Image:
    """Labeled 4x/2x nearest-neighbor contact sheet on dark & light checkerboards."""
    W, H = 1560, 960
    sheet = Image.new("RGBA", (W, H), (22, 18, 30, 255))
    draw = ImageDraw.Draw(sheet)

    draw.text(
        (20, 14),
        "SPELL STORM M3 - ENVIRONMENT, BIOME TRANSITIONS & PROPS CONTACT SHEET (Nearest-Neighbor)",
        fill=(232, 241, 248, 255),
    )

    # Row 1: 4 Canonical Terrain Tiles (2x2 tiled @ 2x) + Arena Floor Tile (2x)
    draw.text((20, 40), "1. CANONICAL TERRAIN TILES (2x2 seamless @ 2x) & COURTYARD FLOOR SLAB (64x64 @ 2x):", fill=(143, 211, 255, 255))
    for i, (label, tile) in enumerate([
        ("water.png (32x32)", water),
        ("sand.png (32x32)", sand),
        ("bridge_h.png (32x32)", bridge_h),
        ("bridge_v.png (32x32)", bridge_v),
    ]):
        ox = 20 + i * 165
        oy = 62
        t2 = Image.new("RGBA", (64, 64))
        for ty in range(2):
            for tx in range(2):
                t2.paste(tile, (tx * 32, ty * 32))
        z = scale_nearest(t2, 2)
        sheet.paste(z, (ox, oy))
        draw.rectangle([ox - 1, oy - 1, ox + 128, oy + 128], outline=(90, 101, 122, 255))
        draw.text((ox, oy + 134), label, fill=(232, 241, 248, 255))

    # Floor tile (64x64 @ 2x = 128x128)
    fx, fy = 20 + 4 * 165, 62
    sheet.paste(scale_nearest(floor_tile, 2), (fx, fy))
    draw.rectangle([fx - 1, fy - 1, fx + 128, fy + 128], outline=(90, 101, 122, 255))
    draw.text((fx, fy + 134), "floor_tile.png (64x64)", fill=(232, 241, 248, 255))

    # Water 4-frame loop preview (3x)
    draw.text((fx + 160, 40), "water_sheet.png (128x32 = 4 frames @ 3x):", fill=(143, 211, 255, 255))
    for wi in range(4):
        wfr = water_sheet.crop((wi * 32, 0, (wi + 1) * 32, 32))
        wx = fx + 160 + wi * 108
        sheet.paste(scale_nearest(wfr, 3), (wx, 62))
        draw.rectangle([wx - 1, 61, wx + 96, 62 + 96], outline=(90, 101, 122, 255))
        draw.text((wx + 24, 164), f"F{wi}", fill=(255, 224, 102, 255))

    # Row 2: 5 New Props @ 4x on Dark & Light Checkerboards
    draw.text((20, 226), "2. MILESTONE 3 ENVIRONMENT PROPS (4x zoom on Dark & Light Checkerboards):", fill=(143, 211, 255, 255))
    props_list = [
        ("tree_pine (24x48)", tree_pine),
        ("rock_large (32x32)", rock_large),
        ("log (28x18)", log_img),
        ("ruins (32x48)", ruins),
        ("camp_tent (40x36)", camp_tent),
    ]
    cx = 20
    for label, spr in props_list:
        sw, sh = spr.size
        zw, zh = sw * 4, sh * 4
        zspr = scale_nearest(spr, 4)
        pad_w = zw + 12
        box_h = 204

        dark_bg = make_checkerboard(pad_w, box_h, light=False, cell=8)
        dark_bg.alpha_composite(zspr, (6, box_h - 6 - zh))
        sheet.paste(dark_bg, (cx, 248))

        light_bg = make_checkerboard(pad_w, box_h, light=True, cell=8)
        light_bg.alpha_composite(zspr, (6, box_h - 6 - zh))
        sheet.paste(light_bg, (cx + pad_w + 8, 248))

        draw.text((cx, 458), label, fill=(232, 241, 248, 255))
        cx += pad_w * 2 + 26

    # Row 3: Terrain Variants Sheet (8 frames @ 3x)
    draw.text((20, 494), "3. terrain_variants_sheet.png (256x32 = 8 variants @ 3x zoom):", fill=(143, 211, 255, 255))
    var_labels = [
        "0:GrassA", "1:GrassB", "2:GrassC", "3:DirtA",
        "4:DirtB", "5:DirtC", "6:StoneA", "7:SandA",
    ]
    for vi, vlabel in enumerate(var_labels):
        vfr = variants_sheet.crop((vi * 32, 0, (vi + 1) * 32, 32))
        vx = 20 + vi * 116
        vy = 516
        sheet.paste(scale_nearest(vfr, 3), (vx, vy))
        draw.rectangle([vx - 1, vy - 1, vx + 96, vy + 96], outline=(90, 101, 122, 255))
        draw.text((vx + 8, vy + 102), vlabel, fill=(232, 241, 248, 255))

    # Row 4: Terrain Transitions Sheet (8 frames @ 3x over dark checker & live base)
    draw.text((20, 650), "4. terrain_transitions_sheet.png (256x32 = Grass Overhangs 0..3 & Shoreline Banks 4..7 @ 3x):", fill=(143, 211, 255, 255))
    tr_labels = [
        "0:Grass N", "1:Grass E", "2:Grass S", "3:Grass W",
        "4:Shore N", "5:Shore E", "6:Shore S", "7:Shore W",
    ]
    dirt = Image.open(TERRAIN_OUT / "dirt.png").convert("RGBA")
    for ti, tlabel in enumerate(tr_labels):
        tfr = transitions_sheet.crop((ti * 32, 0, (ti + 1) * 32, 32))
        tx = 20 + ti * 116
        ty = 672
        # Top half: isolated on checkerboard; bottom row below: composited over dirt/water
        iso_bg = make_checkerboard(96, 96, light=False, cell=8)
        iso_bg.alpha_composite(scale_nearest(tfr, 3))
        sheet.paste(iso_bg, (tx, ty))
        draw.rectangle([tx - 1, ty - 1, tx + 96, ty + 96], outline=(90, 101, 122, 255))

        comp = (dirt if ti < 4 else water).copy()
        comp.alpha_composite(tfr)
        sheet.paste(scale_nearest(comp, 3), (tx, ty + 104))
        draw.rectangle([tx - 1, ty + 103, tx + 96, ty + 200], outline=(90, 101, 122, 255))
        draw.text((tx + 6, ty + 206), tlabel, fill=(255, 224, 102, 255))

    return sheet


def build_biome_transitions_preview(
    water: Image.Image,
    sand: Image.Image,
    bridge_h: Image.Image,
    bridge_v: Image.Image,
    floor_tile: Image.Image,
    tree_pine: Image.Image,
    rock_large: Image.Image,
    log_img: Image.Image,
    ruins: Image.Image,
    camp_tent: Image.Image,
    variants_sheet: Image.Image,
    transitions_sheet: Image.Image,
) -> Image.Image:
    """
    3x composite 10x8 biome transition map proving variants + terrain_transitions_sheet
    eliminate hard rectangular 32px borders across grass, dirt, stone, sand, water,
    bridges, and both M2 + M3 props.
    """
    grass = Image.open(TERRAIN_OUT / "grass.png").convert("RGBA")
    dirt = Image.open(TERRAIN_OUT / "dirt.png").convert("RGBA")
    stone = Image.open(TERRAIN_OUT / "stone.png").convert("RGBA")
    tree_oak = Image.open(ENV_OUT / "tree_oak.png").convert("RGBA")
    rock = Image.open(ENV_OUT / "rock.png").convert("RGBA")
    bush = Image.open(ENV_OUT / "bush.png").convert("RGBA")
    campfire = Image.open(ENV_OUT / "campfire.png").convert("RGBA")

    vframes = [variants_sheet.crop((i * 32, 0, (i + 1) * 32, 32)) for i in range(8)]
    tframes = [transitions_sheet.crop((i * 32, 0, (i + 1) * 32, 32)) for i in range(8)]

    # 10x8 map (W=10, H=8 -> 320x256 native -> 960x768 at 3x)
    # G=grass, D=dirt, S=stone, A=sand, W=water, H=bridge_h, V=bridge_v
    layout = [
        "GGAAWWAAAG",
        "GGAAWWDDDG",
        "GAAWWWHDDG",
        "GAAWWADDDG",
        "GGAAWWAGSS",
        "GGDDWWAGSS",
        "GGDDWWDDGG",
        "GGGDVVDDGG",
    ]
    W_TILES, H_TILES = 10, 8
    T = 32
    scene = Image.new("RGBA", (W_TILES * T, H_TILES * T), (0, 0, 0, 255))

    def tile_at(tx: int, ty: int) -> str:
        if 0 <= tx < W_TILES and 0 <= ty < H_TILES:
            return layout[ty][tx]
        return layout[max(0, min(H_TILES - 1, ty))][max(0, min(W_TILES - 1, tx))]

    # Pass 1: Deterministic base & variant selection per (tx, ty)
    for ty in range(H_TILES):
        for tx in range(W_TILES):
            ch = layout[ty][tx]
            h = (tx * 73856093 ^ ty * 19349663) & 0xFF
            if ch == "G":
                # Mix M2 grass (~50%) + variants A/B/C
                if h % 7 < 3:
                    tile_img = grass
                elif h % 7 == 3:
                    tile_img = vframes[0]
                elif h % 7 < 6:
                    tile_img = vframes[1]
                else:
                    tile_img = vframes[2]
            elif ch == "D":
                if h % 5 < 2:
                    tile_img = dirt
                else:
                    tile_img = vframes[3 + (h % 3)]
            elif ch == "S":
                tile_img = vframes[6] if (h % 3 == 0) else stone
            elif ch == "A":
                tile_img = vframes[7] if (h % 4 == 0) else sand
            elif ch == "W":
                tile_img = water
            elif ch == "H":
                tile_img = bridge_h
            elif ch == "V":
                tile_img = bridge_v
            else:
                tile_img = grass
            scene.paste(tile_img, (tx * T, ty * T))

    # Optional courtyard floor slab inset in the stone ruins corner (tx=8..9, ty=4..5 -> 64x64)
    scene.paste(floor_tile, (8 * T, 4 * T))

    # Pass 2: Terrain Transition Fringes (exact TileMap.drawTerrain overlay algorithm)
    # 0..3: Grass overhangs drawn atop D/S/A bordering G
    # 4..7: Shoreline bank & foam drawn atop W bordering non-water land (G/D/S/A)
    land_for_shore = {"G", "D", "S", "A"}
    for ty in range(H_TILES):
        for tx in range(W_TILES):
            ch = layout[ty][tx]
            if ch in ("D", "S", "A"):
                if ty > 0 and tile_at(tx, ty - 1) == "G":
                    scene.alpha_composite(tframes[0], (tx * T, ty * T))  # North
                if tx < W_TILES - 1 and tile_at(tx + 1, ty) == "G":
                    scene.alpha_composite(tframes[1], (tx * T, ty * T))  # East
                if ty < H_TILES - 1 and tile_at(tx, ty + 1) == "G":
                    scene.alpha_composite(tframes[2], (tx * T, ty * T))  # South
                if tx > 0 and tile_at(tx - 1, ty) == "G":
                    scene.alpha_composite(tframes[3], (tx * T, ty * T))  # West
            elif ch == "W":
                if ty > 0 and tile_at(tx, ty - 1) in land_for_shore:
                    scene.alpha_composite(tframes[4], (tx * T, ty * T))  # North bank
                if tx < W_TILES - 1 and tile_at(tx + 1, ty) in land_for_shore:
                    scene.alpha_composite(tframes[5], (tx * T, ty * T))  # East bank
                if ty < H_TILES - 1 and tile_at(tx, ty + 1) in land_for_shore:
                    scene.alpha_composite(tframes[6], (tx * T, ty * T))  # South bank
                if tx > 0 and tile_at(tx - 1, ty) in land_for_shore:
                    scene.alpha_composite(tframes[7], (tx * T, ty * T))  # West bank

    # Pass 3: Grounded drop shadows + Y-sorted M2 & M3 props
    props = [
        (24, 52, tree_pine, 10, 4),
        (60, 48, rock, 9, 4),
        (288, 52, tree_oak, 13, 5),
        (228, 60, camp_tent, 17, 6),
        (254, 104, campfire, 9, 4),
        (224, 114, log_img, 12, 4),
        (288, 132, ruins, 13, 5),
        (304, 178, rock_large, 13, 5),
        (36, 170, bush, 8, 3),
        (46, 224, tree_pine, 10, 4),
        (286, 232, tree_oak, 13, 5),
        (206, 212, bush, 8, 3),
        (82, 238, rock_large, 13, 5),
    ]
    props.sort(key=lambda p: p[1])

    shadow_layer = Image.new("RGBA", scene.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)
    for ax, ay, _, rx, ry in props:
        sd.ellipse([ax - rx, ay - ry - 1, ax + rx, ay + ry - 1], fill=(20, 16, 26, 95))
    scene = Image.alpha_composite(scene, shadow_layer)

    for ax, ay, spr, _, _ in props:
        sw, sh = spr.size
        scene.alpha_composite(spr, (ax - sw // 2, ay - sh))

    return scale_nearest(scene, 3)


def main() -> None:
    ENV_OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    floor_rows = make_floor_tile_grid()
    save_grid_json("floor_tile", 64, 64, FLOOR_PALETTE, floor_rows, pivot=[32, 32])
    floor_img = render_rows(FLOOR_PALETTE, floor_rows)
    floor_img.save(ENV_OUT / "floor_tile.png", "PNG")

    save_grid_json("tree_pine", 24, 48, PINE_PALETTE, TREE_PINE_ROWS, pivot=[12, 48])
    pine_img = render_rows(PINE_PALETTE, TREE_PINE_ROWS)
    pine_img.save(ENV_OUT / "tree_pine.png", "PNG")

    save_grid_json("rock_large", 32, 32, ROCK_LARGE_PALETTE, ROCK_LARGE_ROWS, pivot=[16, 32])
    rock_large_img = render_rows(ROCK_LARGE_PALETTE, ROCK_LARGE_ROWS)
    rock_large_img.save(ENV_OUT / "rock_large.png", "PNG")

    save_grid_json("log", 28, 18, LOG_PALETTE, LOG_ROWS, pivot=[14, 18])
    log_img = render_rows(LOG_PALETTE, LOG_ROWS)
    log_img.save(ENV_OUT / "log.png", "PNG")

    save_grid_json("ruins", 32, 48, RUINS_PALETTE, RUINS_ROWS, pivot=[16, 48])
    ruins_img = render_rows(RUINS_PALETTE, RUINS_ROWS)
    ruins_img.save(ENV_OUT / "ruins.png", "PNG")

    save_grid_json("camp_tent", 40, 36, TENT_PALETTE, CAMP_TENT_ROWS, pivot=[20, 36])
    tent_img = render_rows(TENT_PALETTE, CAMP_TENT_ROWS)
    tent_img.save(ENV_OUT / "camp_tent.png", "PNG")

    # Load M3 terrain & sheets to render M3 contact sheet + biome preview
    water = Image.open(TERRAIN_OUT / "water.png").convert("RGBA")
    sand = Image.open(TERRAIN_OUT / "sand.png").convert("RGBA")
    bridge_h = Image.open(TERRAIN_OUT / "bridge_h.png").convert("RGBA")
    bridge_v = Image.open(TERRAIN_OUT / "bridge_v.png").convert("RGBA")
    water_sheet = Image.open(ANIM_OUT / "water_sheet.png").convert("RGBA")
    variants_sheet = Image.open(ANIM_OUT / "terrain_variants_sheet.png").convert("RGBA")
    transitions_sheet = Image.open(ANIM_OUT / "terrain_transitions_sheet.png").convert("RGBA")

    contact = build_contact_sheet(
        water, sand, bridge_h, bridge_v,
        floor_img, pine_img, rock_large_img, log_img, ruins_img, tent_img,
        water_sheet, variants_sheet, transitions_sheet,
    )
    contact.save(PREVIEW_DIR / "environment_m3_contact_sheet.png", "PNG")

    biome = build_biome_transitions_preview(
        water, sand, bridge_h, bridge_v,
        floor_img, pine_img, rock_large_img, log_img, ruins_img, tent_img,
        variants_sheet, transitions_sheet,
    )
    biome.save(PREVIEW_DIR / "biome_transitions_preview.png", "PNG")

    print("Milestone 3 environment props & previews generated successfully.")


if __name__ == "__main__":
    main()
