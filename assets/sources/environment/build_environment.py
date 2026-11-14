#!/usr/bin/env python3
"""
Hand-crafted 3/4 top-down environment props & 4-frame campfire animation
for Spell Storm Milestone 2.

Saves editable pixel-art-studio JSON sources under assets/sources/environment/
and exports binary-alpha PNGs + milestone previews.
"""

import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
SRC_DIR = ROOT / "assets/sources/environment"
ENV_OUT = ROOT / "assets/textures/environment"
ANIM_OUT = ROOT / "assets/textures/animations"
TERRAIN_OUT = ROOT / "assets/textures/terrain"
PREVIEW_DIR = ROOT / "docs/previews/milestone2"


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
            assert len(row) == w, f"{name}/{fname} row {i}: expected {w} chars, got {len(row)} ({row})"
            for ch in row:
                assert ch == "." or ch in palette, f"{name}/{fname} row {i}: unknown key {ch!r}"
    doc = {
        "version": 1,
        "name": name,
        "canvas": [w, h],
        "pivot": pivot if pivot is not None else [w // 2, h],
        "palette": palette,
        "frames": [
            {"name": fname, "duration_ms": dur, "rows": rows}
            for fname, dur, rows in frames
        ],
    }
    path = SRC_DIR / f"{name}.json"
    path.write_text(json.dumps(doc, indent=2) + "\n")
    return path


def render_rows_to_image(palette: dict[str, str], rows: list[str]) -> Image.Image:
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


def render_sheet(palette: dict[str, str], frame_rows_list: list[list[str]]) -> Image.Image:
    frames = [render_rows_to_image(palette, rows) for rows in frame_rows_list]
    w, h = frames[0].size
    sheet = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        sheet.paste(fr, (i * w, 0))
    return sheet


# ---------------------------------------------------------------------------
# 1. TREE_OAK (32x48) — Lush ancient oak with flared roots & sculpted clusters
# ---------------------------------------------------------------------------
TREE_PALETTE = {
    "o": "#14101a",  # outer silhouette ink
    "i": "#19222d",  # deep indigo-green canopy underside shadow
    "v": "#163020",  # deepest foliage shadow
    "d": "#224d2c",  # dark canopy foliage
    "g": "#347539",  # mid oak leaf mass
    "l": "#52a349",  # sunlit leaf cluster
    "h": "#82cf63",  # leaf-dither highlight
    "H": "#b8ed87",  # crisp crown catchlight
    "k": "#2b1a14",  # dark bark / cast shadow on trunk
    "w": "#472b1e",  # shaded bark
    "u": "#6e442c",  # mid oak bark
    "U": "#99633f",  # sunlit left bark ridge
}

TREE_OAK_ROWS = [
    # 01234567890123456789012345678901 (32 cols x 48 rows)
    "...........oooooooo.............",  # 00
    ".........ooHHhhllggoo...........",  # 01
    "........oHHhhlllhllggo..........",  # 02
    ".......oHhllhhhllllgdgo.........",  # 03
    "......oHhllhHhlggllgddgo........",  # 04
    ".....oHlllllhllggggggddo........",  # 05
    ".....oHllggllggdgggddgdvo.......",  # 06
    "....oohllgggdggdddgddgddoo......",  # 07
    "...oHHhllggddggggllhlggdggo.....",  # 08
    "..oHHhlllggddgllhllhllggddgo....",  # 09
    ".oHhllllggddgdlhlHhlhlggdddgo...",  # 10
    ".oHllhhllgdddglllhlllgggddddo...",  # 11
    "oHhllhhllgdvdgggggggggdddgddgo..",  # 12
    "oHlllgglgddvdglllhhllgggddggdo..",  # 13
    "oHllggggdddvhllHHhhlhllggddgdo..",  # 14
    "olgggdddvdvdhlHHhhllhllggddgdo..",  # 15
    "olggdddvdvvdllhllllllggggdddvo..",  # 16
    ".ogdddgllhllgggllgggddggdddvo...",  # 17
    ".ogdggllHHhllggggggdddgdddvo....",  # 18
    ".oggdllhhllhllggggddgddddvvo....",  # 19
    "oHllgdllllllgggddgggggdddggoo...",  # 20
    "olllgddggggggddgllhllgggddggdo..",  # 21
    "oggdddgdvvddvdgllhllggggddgdvo..",  # 22
    ".oggdddvvviiivggggggggdddgdvvo..",  # 23
    ".ovgddvviiiiivddggggddddvvvvo...",  # 24
    "..ovvvvviiiiivddddddvdvviiio....",  # 25
    "...ooooiiiiiiookkkddvviiiiio....",  # 26
    ".......oooooookwkkkkviiiiio.....",  # 27
    ".............oUukwkkvooooo......",  # 28
    ".............oUuwkkko...........",  # 29
    ".............oUuwkko............",  # 30
    ".............oUuwkko............",  # 31
    ".............oUuwkko............",  # 32
    ".............oUuwkko............",  # 33
    ".............oUuwkko............",  # 34
    ".............oUuwkko............",  # 35
    "............oUUuwkko............",  # 36
    "............oUuuwkkoo...........",  # 37
    "...........oUUuuwkkko...........",  # 38
    "...........oUuuwwkkko...........",  # 39
    "..........oUUuuwuwkkko..........",  # 40
    ".........oUUuuwuuuwkkko.........",  # 41
    "........oUUuwwoowuuwkkko........",  # 42
    ".......oUuwwoo.oUuwookkko.......",  # 43
    "......oUUuwo...oUuk..owkko......",  # 44
    "......oUukko...oUko..oowkko.....",  # 45
    ".......oooo.....oo....oooo......",  # 46
    "................................",  # 47
]


# ---------------------------------------------------------------------------
# 2. ROCK (24x24) — Faceted mossy granite boulder with top-left chisels
# ---------------------------------------------------------------------------
ROCK_PALETTE = {
    "o": "#14101a",  # outer 1px outline
    "s": "#282c38",  # deep crevice / grounded base shadow
    "d": "#3e4556",  # shaded right/lower rock facet
    "m": "#5a657a",  # primary slate granite body
    "l": "#808fa6",  # sunlit top-left rock plane
    "h": "#aebdcf",  # crisp top-left chisel edge
    "k": "#224d2c",  # moss shadow lip
    "g": "#347539",  # moss mid
    "L": "#52a349",  # moss sunlit edge
    "H": "#82cf63",  # moss top-left highlight
}

ROCK_ROWS = [
    # 012345678901234567890123 (24 cols x 24 rows)
    "........................",  # 00
    "........................",  # 01
    "........................",  # 02
    "........................",  # 03
    "........................",  # 04
    "........................",  # 05
    "........................",  # 06
    "........ooooooo.........",  # 07
    "......ooHHhhllloo.......",  # 08
    ".....oHhLLglllmmdo......",  # 09
    "....oHhLLgHgllmmmdo.....",  # 10
    "....oHlLgggkllmmdddo....",  # 11
    "...oHllgkkkmhmmdmdddo...",  # 12
    "...ohllmmmmhmmddddsdo...",  # 13
    "..oHllmmmmmmhmddddssdo..",  # 14
    "..ohllmmmmdddmdddssddo..",  # 15
    "..olllmmdddsdmddssdddo..",  # 16
    "..olmkmddsssdmdssddddo..",  # 17
    "..okgkmddsssdsssssdddo..",  # 18
    "..okggmddsssssssssddso..",  # 19
    "..ookkmddssssssssddsoo..",  # 20
    "...oosssssssdssssssso...",  # 21
    "....oooooooooooooooo....",  # 22
    "........................",  # 23
]


# ---------------------------------------------------------------------------
# 3. BUSH (20x20) — Dense layered briar bush with warm berry accents
# ---------------------------------------------------------------------------
BUSH_PALETTE = {
    "o": "#14101a",  # outer outline
    "v": "#163020",  # deepest underside foliage shadow
    "d": "#224d2c",  # shadow leaf cluster
    "g": "#347539",  # mid briar leaf body
    "l": "#52a349",  # sunlit leaf cluster
    "h": "#82cf63",  # crisp leaf highlight
    "H": "#b8ed87",  # bright top-left leaf glint
    "r": "#bf2633",  # berry shadow
    "b": "#f25244",  # warm crimson berry
    "y": "#ffe066",  # warm golden berry highlight
}

BUSH_ROWS = [
    # 01234567890123456789 (20 cols x 20 rows)
    "....................",  # 00
    "....................",  # 01
    "......ooooooo.......",  # 02
    "....ooHHhhllgoo.....",  # 03
    "...oHhlllllllggo....",  # 04
    "..oHhlllybllllggo...",  # 05
    "..oHllggbrggggddo...",  # 06
    ".oohllggdgggddgdvo..",  # 07
    "oHHhllggllhhllgdggo.",  # 08
    "oHlllggllhlllybggdo.",  # 09
    "ollllgglgggggdrgddo.",  # 10
    "olgyblgggggdggggddo.",  # 11
    "oggbrggddgggggdgddo.",  # 12
    ".ogdddgglhllgddgddo.",  # 13
    ".ovgddgllhllgddggvo.",  # 14
    "..ovgddgbybggddvvo..",  # 15
    "..ovvvddgrgddvvvvo..",  # 16
    "...oovvvvvvvvvvoo...",  # 17
    "....oooooooooooo....",  # 18
    "....................",  # 19
]


# ---------------------------------------------------------------------------
# 4. CAMPFIRE (24x24) & CAMPFIRE_SHEET (96x24, 4 frames)
#    Charred stone fire ring + crossed glowing oak logs + 4-frame looping flame
# ---------------------------------------------------------------------------
CAMPFIRE_PALETTE = {
    "o": "#14101a",  # stone ring & log tip outline
    "S": "#282c38",  # stone deep shadow & ash
    "D": "#3e4556",  # shaded ring stone
    "M": "#5a657a",  # mid ring stone
    "L": "#808fa6",  # sunlit top-left stone bevel
    "w": "#2b1a14",  # dark charred oak bark
    "W": "#472b1e",  # shaded oak log
    "u": "#6e442c",  # mid oak log
    "U": "#99633f",  # lit oak log ridge
    "e": "#3d0c18",  # deep ember bed
    "R": "#781722",  # glowing red ember
    "r": "#bf2626",  # outer flame crimson
    "f": "#f25c22",  # vibrant orange flame
    "a": "#ff9e24",  # amber flame body
    "y": "#ffe054",  # hot yellow flame core
    "c": "#fffbe6",  # white-hot flame heart
}

# Solid, rounded 3/4 charred stone ring with crossed logs resting across the rim
BASE_RING_AND_LOGS = [
    # 012345678901234567890123
    "........................",  # 00
    "........................",  # 01
    "........................",  # 02
    "........................",  # 03
    "........................",  # 04
    "........................",  # 05
    "........................",  # 06
    "........................",  # 07
    "........................",  # 08
    "........................",  # 09
    "........................",  # 10
    "........................",  # 11
    "........................",  # 12
    "........................",  # 13
    "......oooooooooooo......",  # 14
    "....ooLMDoLMDoDDSDoo....",  # 15
    "..ooUuuweRRRRRRewuuwoo..",  # 16
    ".oUUuwoRReRRRRReRwuwwDo.",  # 17
    ".oLMDooRRRRRRRRRReoDDSo.",  # 18
    ".oMDSowwReRRRRRewwoMDSo.",  # 19
    "..ooLoUuwRReeeRowUuwwo..",  # 20
    "...oooLMDoLMDoLMDoooo...",  # 21
    ".....ooooooooooooo......",  # 22
    "........................",  # 23
]


def overlay_campfire_frame(
    embers: list[tuple[int, int, str]],
    flame_rows: dict[int, str],
) -> list[str]:
    grid = [list(r) for r in BASE_RING_AND_LOGS]
    for ex, ey, ch in embers:
        grid[ey][ex] = ch
    for y, row_str in flame_rows.items():
        for x, ch in enumerate(row_str):
            if ch != ".":
                grid[y][x] = ch
    return ["".join(r) for r in grid]


CAMPFIRE_F0 = overlay_campfire_frame(
    embers=[
        (7, 17, "f"), (8, 18, "a"), (9, 18, "f"), (14, 18, "a"), (15, 17, "f"),
        (10, 19, "f"), (13, 19, "f"),
    ],
    flame_rows={
        3:  "..........y.............",
        5:  ".............f..........",
        6:  ".........r..............",
        7:  ".........rf.............",
        8:  "........rrfa....r.......",
        9:  "........rfay...rf.......",
        10: ".......rrfayr..fa.......",
        11: ".......rfayca.rfa.......",
        12: "......rrfayccafayr......",
        13: "......rfayycccyfar......",
        14: "......rfaycccccyfr......",
        15: "......RfaycccccyfR......",
        16: "......RffaycccyffR......",
        17: ".......RffayyyffR.......",
    },
)

CAMPFIRE_F1 = overlay_campfire_frame(
    embers=[
        (6, 17, "a"), (7, 17, "y"), (8, 18, "f"), (14, 18, "f"), (15, 17, "a"),
        (11, 19, "a"), (12, 19, "f"),
    ],
    flame_rows={
        1:  "...........f............",
        3:  ".............a..........",
        6:  "............r...........",
        7:  "...........rf...........",
        8:  "..........rrfa..........",
        9:  ".......r..rfay...r......",
        10: "......rf..rfayr.rf......",
        11: "......rfarffaycrfa......",
        12: "......rfayycccyyar......",
        13: "......rfayyccccyfr......",
        14: "......rfaycccccyfr......",
        15: "......RfaycccccyfR......",
        16: "......RffaycccyffR......",
        17: ".......RffayyyffR.......",
    },
)

CAMPFIRE_F2 = overlay_campfire_frame(
    embers=[
        (7, 17, "f"), (8, 18, "y"), (14, 18, "y"), (15, 17, "a"), (16, 17, "f"),
        (10, 19, "a"), (13, 19, "a"),
    ],
    flame_rows={
        2:  "..............f.........",
        5:  ".........y..............",
        7:  ".......r......r.........",
        8:  ".......rf....rfa........",
        9:  ".......rfa...rfay.......",
        10: ".......rfay.rrfay.......",
        11: "......rrfayrafayfr......",
        12: "......rfaycccyyfar......",
        13: "......rfaycccccyfr......",
        14: "......rfaycccccyfr......",
        15: "......RfaycccccyfR......",
        16: "......RffaycccyffR......",
        17: ".......RffayyyffR.......",
    },
)

CAMPFIRE_F3 = overlay_campfire_frame(
    embers=[
        (7, 17, "a"), (8, 18, "f"), (13, 18, "f"), (14, 18, "f"), (15, 17, "r"),
        (10, 19, "f"), (12, 19, "a"),
    ],
    flame_rows={
        4:  "........y...............",
        5:  "............y...........",
        6:  "..........r.............",
        7:  ".........rf.............",
        8:  "........rfa.............",
        9:  "........rfay....r.......",
        10: ".......rrfayc..rf.......",
        11: ".......rfayccarfa.......",
        12: "......rrfaycccyfar......",
        13: "......rfayycccyyfr......",
        14: "......rfaycccccyfr......",
        15: "......RfaycccccyfR......",
        16: "......RffaycccyffR......",
        17: ".......RffayyyffR.......",
    },
)


def make_checkerboard(w: int, h: int, light: bool = False, cell: int = 8) -> Image.Image:
    c1 = (232, 236, 244, 255) if light else (28, 24, 38, 255)
    c2 = (212, 218, 228, 255) if light else (40, 34, 54, 255)
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = c1 if ((x // cell) + (y // cell)) % 2 == 0 else c2
    return img


def build_scene_diorama(
    grass: Image.Image,
    dirt: Image.Image,
    stone: Image.Image,
    tree_oak: Image.Image,
    rock: Image.Image,
    bush: Image.Image,
    campfire: Image.Image,
) -> Image.Image:
    """
    Render a 6x6 tile (192x192 native -> 768x768 at 4x nearest-neighbor) diorama:
    Grass meadow + winding dirt campsite trail + ancient stone plaza + props
    with grounded elliptical shadows and Y-sorted 3/4 depth.
    """
    T = 32
    W, H = 6 * T, 6 * T
    layout = [
        "GGGGGG",
        "GGDDGG",
        "GGDDSS",
        "GDDDSS",
        "GGDDDG",
        "GGGGGG",
    ]
    tile_map = {"G": grass, "D": dirt, "S": stone}
    scene = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    for ty, row in enumerate(layout):
        for tx, ch in enumerate(row):
            scene.paste(tile_map[ch], (tx * T, ty * T))

    shadow_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)

    props = [
        (26, 54, tree_oak, 13, 5),
        (166, 56, tree_oak, 13, 5),
        (56, 70, bush, 8, 3),
        (124, 62, rock, 9, 4),
        (152, 108, campfire, 9, 4),
        (18, 128, rock, 9, 4),
        (40, 168, tree_oak, 13, 5),
        (114, 152, bush, 8, 3),
        (170, 174, bush, 8, 3),
        (74, 180, rock, 9, 4),
    ]
    props.sort(key=lambda p: p[1])

    for ax, ay, spr, rx, ry in props:
        sd.ellipse([ax - rx, ay - ry - 1, ax + rx, ay + ry - 1], fill=(20, 16, 26, 95))
    scene = Image.alpha_composite(scene, shadow_layer)

    for ax, ay, spr, _, _ in props:
        sw, sh = spr.size
        scene.alpha_composite(spr, (ax - sw // 2, ay - sh))

    return scene.resize((W * 4, H * 4), Image.Resampling.NEAREST)


def build_contact_sheet(
    grass: Image.Image,
    dirt: Image.Image,
    stone: Image.Image,
    tree_oak: Image.Image,
    rock: Image.Image,
    bush: Image.Image,
    campfire_frames: list[Image.Image],
) -> Image.Image:
    """Labeled 4x contact sheet on dark & light checkerboards + 2x2 tile repeats."""
    W, H = 1080, 640
    sheet = Image.new("RGBA", (W, H), (22, 18, 30, 255))
    draw = ImageDraw.Draw(sheet)

    draw.text(
        (20, 14),
        "SPELL STORM M2 - ENVIRONMENT & TERRAIN CONTACT SHEET (4x Nearest-Neighbor)",
        fill=(232, 241, 248, 255),
    )

    draw.text((20, 42), "TERRAIN TILES (32x32 shown as 2x2 seamless @ 2x zoom):", fill=(143, 211, 255, 255))
    for i, (label, tile) in enumerate([("grass.png (32x32)", grass), ("dirt.png (32x32)", dirt), ("stone.png (32x32)", stone)]):
        ox = 20 + i * 170
        oy = 66
        t2 = Image.new("RGBA", (64, 64))
        for ty in range(2):
            for tx in range(2):
                t2.paste(tile, (tx * 32, ty * 32))
        z = t2.resize((128, 128), Image.Resampling.NEAREST)
        sheet.paste(z, (ox, oy))
        draw.rectangle([ox - 1, oy - 1, ox + 128, oy + 128], outline=(90, 101, 122, 255))
        draw.text((ox, oy + 134), label, fill=(232, 241, 248, 255))

    draw.text((20, 224), "ENVIRONMENT PROPS (4x zoom on Dark & Light Checkerboards):", fill=(143, 211, 255, 255))
    props_list = [
        ("tree_oak (32x48)", tree_oak),
        ("rock (24x24)", rock),
        ("bush (20x20)", bush),
        ("campfire (24x24)", campfire_frames[0]),
    ]
    cx = 20
    for label, spr in props_list:
        sw, sh = spr.size
        zw, zh = sw * 4, sh * 4
        zspr = spr.resize((zw, zh), Image.Resampling.NEAREST)

        dark_bg = make_checkerboard(zw + 12, 200, light=False, cell=8)
        dark_bg.alpha_composite(zspr, (6, 194 - zh))
        sheet.paste(dark_bg, (cx, 246))

        light_bg = make_checkerboard(zw + 12, 200, light=True, cell=8)
        light_bg.alpha_composite(zspr, (6, 194 - zh))
        sheet.paste(light_bg, (cx + zw + 18, 246))

        draw.text((cx, 452), label, fill=(232, 241, 248, 255))
        cx += (zw + 12) * 2 + 24

    draw.text((20, 482), "CAMPFIRE ANIMATION SHEET (96x24 = 4x 24x24 @ 4x zoom):", fill=(143, 211, 255, 255))
    for i, fr in enumerate(campfire_frames):
        ox = 20 + i * 128
        oy = 504
        bg = make_checkerboard(108, 108, light=False, cell=8)
        zfr = fr.resize((96, 96), Image.Resampling.NEAREST)
        bg.alpha_composite(zfr, (6, 6))
        sheet.paste(bg, (ox, oy))
        draw.rectangle([ox - 1, oy - 1, ox + 108, oy + 108], outline=(90, 101, 122, 255))
        draw.text((ox + 34, oy + 114), f"Frame {i}", fill=(255, 224, 102, 255))

    return sheet


def build_campfire_gif(frames: list[Image.Image], out_path: Path) -> None:
    """Export 6x nearest-neighbor animated campfire GIF on dark campsite ground."""
    scale = 6
    sz = 24 * scale
    bg = make_checkerboard(sz + 24, sz + 24, light=False, cell=12)
    rgb_frames = []
    for fr in frames:
        canvas = bg.copy()
        zfr = fr.resize((sz, sz), Image.Resampling.NEAREST)
        canvas.alpha_composite(zfr, (12, 12))
        rgb_frames.append(canvas.convert("RGB"))

    # Build one shared exact global palette across all frames so GIF colors never scramble
    strip = Image.new("RGB", (rgb_frames[0].width * len(rgb_frames), rgb_frames[0].height))
    for i, rf in enumerate(rgb_frames):
        strip.paste(rf, (i * rf.width, 0))
    master_pal = strip.quantize(colors=64, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)

    gif_frames = [
        rf.quantize(palette=master_pal, dither=Image.Dither.NONE) for rf in rgb_frames
    ]
    out_path.parent.mkdir(parents=True, exist_ok=True)
    gif_frames[0].save(
        out_path,
        save_all=True,
        append_images=gif_frames[1:],
        duration=135,
        loop=0,
        disposal=2,
    )


def main() -> None:
    ENV_OUT.mkdir(parents=True, exist_ok=True)
    ANIM_OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    save_grid_json("tree_oak", 32, 48, TREE_PALETTE, [("tree_oak", 100, TREE_OAK_ROWS)], pivot=[16, 48])
    tree_img = render_rows_to_image(TREE_PALETTE, TREE_OAK_ROWS)
    tree_img.save(ENV_OUT / "tree_oak.png", "PNG")

    save_grid_json("rock", 24, 24, ROCK_PALETTE, [("rock", 100, ROCK_ROWS)], pivot=[12, 24])
    rock_img = render_rows_to_image(ROCK_PALETTE, ROCK_ROWS)
    rock_img.save(ENV_OUT / "rock.png", "PNG")

    save_grid_json("bush", 20, 20, BUSH_PALETTE, [("bush", 100, BUSH_ROWS)], pivot=[10, 20])
    bush_img = render_rows_to_image(BUSH_PALETTE, BUSH_ROWS)
    bush_img.save(ENV_OUT / "bush.png", "PNG")

    cf_frames_rows = [CAMPFIRE_F0, CAMPFIRE_F1, CAMPFIRE_F2, CAMPFIRE_F3]
    save_grid_json(
        "campfire",
        24,
        24,
        CAMPFIRE_PALETTE,
        [(f"campfire_{i}", 135, rows) for i, rows in enumerate(cf_frames_rows)],
        pivot=[12, 24],
    )
    cf_imgs = [render_rows_to_image(CAMPFIRE_PALETTE, rows) for rows in cf_frames_rows]
    cf_imgs[0].save(ENV_OUT / "campfire.png", "PNG")

    cf_sheet = render_sheet(CAMPFIRE_PALETTE, cf_frames_rows)
    cf_sheet.save(ANIM_OUT / "campfire_sheet.png", "PNG")

    grass = Image.open(TERRAIN_OUT / "grass.png").convert("RGBA")
    dirt = Image.open(TERRAIN_OUT / "dirt.png").convert("RGBA")
    stone = Image.open(TERRAIN_OUT / "stone.png").convert("RGBA")

    scene = build_scene_diorama(grass, dirt, stone, tree_img, rock_img, bush_img, cf_imgs[0])
    scene.save(PREVIEW_DIR / "environment_scene_preview.png", "PNG")

    sheet = build_contact_sheet(grass, dirt, stone, tree_img, rock_img, bush_img, cf_imgs)
    sheet.save(PREVIEW_DIR / "environment_contact_sheet.png", "PNG")

    build_campfire_gif(cf_imgs, PREVIEW_DIR / "campfire_anim.gif")
    print("All environment assets and previews generated successfully.")


if __name__ == "__main__":
    main()
