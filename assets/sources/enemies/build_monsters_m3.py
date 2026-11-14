#!/usr/bin/env python3
"""
Hand-crafted 3/4 top-down pixel-art Monster & Boss suite for Spell Storm Milestone 3.

Matches Milestone 2 Abyssal Claw Fiend (melee_fiend) & Storm Archmage aesthetic:
- Facing RIGHT (+X) by default (engine flips horizontally on facingLeft)
- 135 deg top-left sunlight, cool indigo/plum shadows
- Crisp 1px #14101a outer outline, strict binary alpha [0, 255]

Produces:
- Editable pixel-art-studio JSON sources in assets/sources/enemies/
- Canonical Frame-0 PNGs in assets/textures/enemies/
- Extended 8-frame sheets in assets/textures/animations/
- Labeled nearest-neighbor contact sheet & animated GIFs in docs/previews/milestone3/
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
SRC_DIR = ROOT / "assets/sources/enemies"
TEX_ENEMY_DIR = ROOT / "assets/textures/enemies"
TEX_ANIM_DIR = ROOT / "assets/textures/animations"
PREVIEW_DIR = ROOT / "docs/previews/milestone3"


def hex_to_rgba(hex_col: str) -> tuple[int, int, int, int]:
    h = hex_col.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def render_grid(palette: dict[str, str], rows: list[str]) -> Image.Image:
    h = len(rows)
    w = len(rows[0])
    rgba = {k: hex_to_rgba(v) for k, v in palette.items()}
    rgba["."] = (0, 0, 0, 0)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    im.putdata([rgba[ch] for row in rows for ch in row])
    return im


def save_json_source(
    name: str,
    w: int,
    h: int,
    pivot: list[int],
    palette: dict[str, str],
    frames: list[tuple[str, int, list[str]]],
) -> Path:
    SRC_DIR.mkdir(parents=True, exist_ok=True)
    for fname, _, rows in frames:
        assert len(rows) == h, f"{name}/{fname}: expected {h} rows, got {len(rows)}"
        for r_idx, row in enumerate(rows):
            assert len(row) == w, f"{name}/{fname} row {r_idx}: expected {w} cols, got {len(row)}"
            unknown = set(row) - set(palette.keys()) - {"."}
            assert not unknown, f"{name}/{fname} row {r_idx}: unknown palette keys {unknown}"
    doc = {
        "version": 1,
        "name": name,
        "canvas": [w, h],
        "pivot": pivot,
        "palette": palette,
        "frames": [
            {"name": fname, "duration_ms": dur, "rows": rows}
            for fname, dur, rows in frames
        ],
    }
    path = SRC_DIR / f"{name}.json"
    path.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    return path


def export_monster_assets(
    name: str,
    w: int,
    h: int,
    pivot: list[int],
    palette: dict[str, str],
    frames: list[tuple[str, int, list[str]]],
) -> list[Image.Image]:
    TEX_ENEMY_DIR.mkdir(parents=True, exist_ok=True)
    TEX_ANIM_DIR.mkdir(parents=True, exist_ok=True)
    save_json_source(name, w, h, pivot, palette, frames)
    imgs = [render_grid(palette, rows) for _, _, rows in frames]
    imgs[0].save(TEX_ENEMY_DIR / f"{name}.png")
    sheet = Image.new("RGBA", (w * len(imgs), h), (0, 0, 0, 0))
    for i, im in enumerate(imgs):
        sheet.paste(im, (i * w, 0))
    sheet.save(TEX_ANIM_DIR / f"{name}_sheet.png")
    return imgs


# ===========================================================================
# 1. ABYSSAL VOID CULTIST (32x32, hand-authored 8-frame grid, baseline y=29)
# ===========================================================================
CULTIST_PALETTE = {
    "o": "#14101a",  # universal outer outline
    "i": "#1e1829",  # deep inner crease / cowl void
    "h": "#1c1624",  # deepest plum robe shadow
    "d": "#2e2238",  # dark plum robe
    "m": "#473354",  # mid plum robe
    "l": "#634973",  # top-left plum highlight
    "v": "#4a1238",  # deep violet-crimson shadow
    "c": "#7a1c54",  # violet-crimson mantle / grimoire cover
    "M": "#b32972",  # bright magenta-crimson cloth
    "p": "#e03c8a",  # vibrant magenta highlight / magic rim
    "g": "#9e631b",  # dark brass/gold clasp shadow
    "G": "#d99726",  # gold clasp & grimoire binding
    "Y": "#ffe066",  # bright gold highlight
    "b": "#b9c8d9",  # bone skull / parchment shadow
    "w": "#e8f1f8",  # bone skull / parchment highlight
    "e": "#ff9b26",  # void ember-amber spark
    "P": "#ffd6f5",  # bright pale magenta-white glow / eye corona
    "W": "#ffffff",  # white-hot eye & orb core
}

# 0         1         2         3
# 01234567890123456789012345678901
CULTIST_FRAMES: list[tuple[str, int, list[str]]] = [
    ("idle_0", 230, [
        "................................",  # 00
        "................................",  # 01
        "..........oooo..................",  # 02  arched cultist hood
        ".........olmmmo.................",  # 03
        "........ollmmmmo................",  # 04
        "........olmmmmddo...............",  # 05
        ".......oolmmmddhdo..............",  # 06
        ".......olmmmddihpmo.............",  # 07  crimson-edged cowl rim
        "......oolmmddihhhpo.............",  # 08
        "......olmmddihhhhco......ooo....",  # 09  deep shadowed cowl cavity
        "......olmmddihPiWPo.....oePpo...",  # 10  piercing glowing magenta-white eyes
        "......olmmddiihhhco.....opWPo...",  # 11  floating void energy orb
        ".....ooimmddibhhbho.....oppeo...",  # 12  horned skull-brooch bone horns
        ".....olmcMmcGwwwwmo......ooo....",  # 13  skull brow & gold collar pins
        "....oolmccMcGwiwGYmo............",  # 14  skull eye hollows & gold clasp
        "....olmmccMciGbGcmmo....omlmo...",  # 15  skull jaw + upturned casting palm
        "....olmmddvccMcMcmmooooomcMmo...",  # 16  right casting arm connected smoothly
        "...oolmdooYYYYYoMcmMMMMmcmmmo...",  # 17  brass-bound grimoire top + sleeve
        "...olmdo.oYwwwGoMcddoooooooo....",  # 18  white parchment edge + crimson stole
        "...olmo..oGcPcGoMcvddo..........",  # 19  glowing magenta rune on grimoire
        "..oolmo..oGcpcGoMcvdddo.........",  # 20
        "..olmdo..oGcccGoMcvdddo.........",  # 21
        "..olmdo..oGYGYGoMcvdmddo........",  # 22  gold grimoire bottom clasp
        "..oldho...oooooiMcvdmddo........",  # 23  tattered cloak tail left + flowing robe
        ".ooldho..olmmddccvvdmddho.......",  # 24
        ".olmdo..oolmmdccvcvdmddho.......",  # 25
        ".oldo...olmmddcvcvddmddhho......",  # 26  full flowing robe skirt
        ".oo....oolddhovcvdmdhdmddo......",  # 27  tattered hem points + dark slippers
        ".......ooihho.oiddmdhoihdo......",  # 28  three jagged hem/slipper tips
        ".........oooo..oooooo.oooo......",  # 29  grounded baseline y=29!
        "................................",  # 30
        "................................",  # 31
    ]),
    ("idle_1", 230, [
        "................................",  # 00
        "..........oooo..................",  # 01  hover +1px rise
        ".........olmmmo.................",  # 02
        "........ollmmmmo................",  # 03
        "........olmmmmddo...............",  # 04
        ".......oolmmmddhdo..............",  # 05
        ".......olmmmddihpmo.............",  # 06
        "......oolmmddihhhpo.............",  # 07
        "......olmmddihhhhco......ooo....",  # 08  orb rises & flares bright white-amber
        "......olmmddihWiWPo.....oeWPo...",  # 09
        "......olmmddiihhhco.....oPWWp...",  # 10
        ".....ooimmddibhhbho.....opPeo...",  # 11
        ".....olmcMmcGwwwwmo......ooo....",  # 12
        "....oolmccMcGwiwGYmo............",  # 13
        "....olmmccMciGbGcmmo....omlmo...",  # 14
        "....olmmddvccMcMcmmooooomcMmo...",  # 15
        "...oolmdooYYYYYoMcmMMMMmcmmmo...",  # 16
        "...olmdo.oYwwwGoMcddoooooooo....",  # 17
        "...olmo..oGcPcGoMcvddo..........",  # 18
        "..oolmo..oGcWcGoMcvdddo.........",  # 19  grimoire sigil pulses white-hot
        "..olmdo..oGcpcGoMcvdddo.........",  # 20
        "..olmdo..oGcccGoMcvdmddo........",  # 21
        "..oldho..oGYGYGoMcvdmddo........",  # 22
        ".ooldho...oooooiMcvdmddho.......",  # 23
        ".olmdo...olmmddccvvdmddho.......",  # 24
        ".oldo...oolmmdccvcvdmddhho......",  # 25
        ".oo.....olmmddcvcvdmdhdmdo......",  # 26
        ".......oolddhovcvdmdhihdho......",  # 27
        ".......ooihho.oiddmdo.oooo......",  # 28  hovering hem lifts cleanly
        ".........oooo..oooooo...........",  # 29  trailing tattered hem tips at y=29
        "................................",  # 30
        "................................",  # 31
    ]),
    ("glide_0", 115, [
        "................................",  # 00
        "................................",  # 01
        "...........oooo.................",  # 02  leans 1px forward
        "..........olmmmo................",  # 03
        ".........ollmmmmo...............",  # 04
        ".........olmmmmddo..............",  # 05
        "........oolmmmddhdo.............",  # 06
        "........olmmmddihpmo............",  # 07
        ".......oolmmddihhhpo............",  # 08
        ".......olmmddihhhhco.....ooo....",  # 09
        ".......olmmddihPiWPo....oePpo...",  # 10
        ".......olmmddiihhhco....opWPo...",  # 11
        "......ooimmddibhhbho....oppeo...",  # 12
        "......olmcMmcGwwwwmo.....ooo....",  # 13
        ".....oolmccMcGwiwGYmo...........",  # 14
        ".....olmmccMciGbGcmmo...omlmo...",  # 15
        "....oolmddvccMcMcmmooooomcMmo...",  # 16
        "...oolmdooYYYYYoMcmMMMMmcmmmo...",  # 17
        "..oolmdo.oYwwwGoMcddoooooooo....",  # 18
        "..olmdo..oGcPcGoMcvddo..........",  # 19
        ".oolmo...oGcpcGoMcvdddo.........",  # 20
        ".olmdo...oGcccGoMcvdddo.........",  # 21
        ".oldho...oGYGYGoMcvdmddo........",  # 22
        "ooldho....oooooiMcvdmddo........",  # 23  cloak streams strongly left
        "olmdo....olmmddccvvdmddho.......",  # 24
        "oldo....oolmmdccvcvdmddho.......",  # 25
        "ooo.....olmmddcvcvddmddhho......",  # 26
        ".......oolddhovcvdmdhdmddo......",  # 27
        ".......ooihho.oiddmdhoihdo......",  # 28
        ".........oooo..oooooo.oooo......",  # 29
        "................................",  # 30
        "................................",  # 31
    ]),
    ("glide_1", 115, [
        "................................",  # 00
        "...........oooo.................",  # 01  passing hover +1px
        "..........olmmmo................",  # 02
        ".........ollmmmmo...............",  # 03
        ".........olmmmmddo..............",  # 04
        "........oolmmmddhdo.............",  # 05
        "........olmmmddihpmo............",  # 06
        ".......oolmmddihhhpo............",  # 07
        ".......olmmddihhhhco.....ooo....",  # 08
        ".......olmmddihPiWPo....oePpo...",  # 09
        ".......olmmddiihhhco....opWPo...",  # 10
        "......ooimmddibhhbho....oppeo...",  # 11
        "......olmcMmcGwwwwmo.....ooo....",  # 12
        ".....oolmccMcGwiwGYmo...........",  # 13
        ".....olmmccMciGbGcmmo...omlmo...",  # 14
        "....oolmddvccMcMcmmooooomcMmo...",  # 15
        "...oolmdooYYYYYoMcmMMMMmcmmmo...",  # 16
        "..oolmdo.oYwwwGoMcddoooooooo....",  # 17
        "..olmdo..oGcPcGoMcvddo..........",  # 18
        "..olmo...oGcpcGoMcvdddo.........",  # 19
        ".oolmo...oGcccGoMcvdddo.........",  # 20
        ".olmdo...oGYGYGoMcvdmddo........",  # 21
        ".oldho....oooooiMcvdmddo........",  # 22
        "ooldho...olmmddccvvdmddho.......",  # 23
        "olmdo...oolmmdccvcvdmddho.......",  # 24
        "oldo....olmmddcvcvddmddhho......",  # 25
        "ooo....oolddhovcvdmdhdmddo......",  # 26
        ".......ooihho.oiddmdo.ihdo......",  # 27
        "........oooo...ooooo..oooo......",  # 28  sweeping robe glide lift
        "................................",  # 29
        "................................",  # 30
        "................................",  # 31
    ]),
    ("glide_2", 115, [
        "................................",  # 00
        "................................",  # 01
        "...........oooo.................",  # 02
        "..........olmmmo................",  # 03
        ".........ollmmmmo...............",  # 04
        ".........olmmmmddo..............",  # 05
        "........oolmmmddhdo.............",  # 06
        "........olmmmddihpmo............",  # 07
        ".......oolmmddihhhpo............",  # 08
        ".......olmmddihhhhco.....ooo....",  # 09
        ".......olmmddihPiWPo....oePpo...",  # 10
        ".......olmmddiihhhco....opWPo...",  # 11
        "......ooimmddibhhbho....oppeo...",  # 12
        "......olmcMmcGwwwwmo.....ooo....",  # 13
        ".....oolmccMcGwiwGYmo...........",  # 14
        ".....olmmccMciGbGcmmo...omlmo...",  # 15
        "....oolmddvccMcMcmmooooomcMmo...",  # 16
        "...oolmdooYYYYYoMcmMMMMmcmmmo...",  # 17
        "...olmdo.oYwwwGoMcddoooooooo....",  # 18
        "..oolmo..oGcPcGoMcvddo..........",  # 19
        "..olmo...oGcpcGoMcvdddo.........",  # 20
        ".oolmdo..oGcccGoMcvdddo.........",  # 21
        ".olmdo...oGYGYGoMcvdmddo........",  # 22
        ".oldho....oooooiMcvdmddo........",  # 23
        ".oldho...olmmddccvvdmddho.......",  # 24
        ".olmdo..oolmmdccvcvdmddho.......",  # 25
        ".oldho..olmmddcvcvddmddhho......",  # 26
        "..oo...oolddmdvcvdmdhdmddo......",  # 27
        ".......ooihdhooiddmdhoihdo......",  # 28  trailing robe sweep touches down
        ".........oooo..oooooo.oooo......",  # 29
        "................................",  # 30
        "................................",  # 31
    ]),
    ("glide_3", 115, [
        "................................",  # 00
        "..........oooo..................",  # 01  passing hover +1px
        ".........olmmmo.................",  # 02
        "........ollmmmmo................",  # 03
        "........olmmmmddo...............",  # 04
        ".......oolmmmddhdo..............",  # 05
        ".......olmmmddihpmo.............",  # 06
        "......oolmmddihhhpo.............",  # 07
        "......olmmddihhhhco......ooo....",  # 08
        "......olmmddihPiWPo.....oePpo...",  # 09
        "......olmmddiihhhco.....opWPo...",  # 10
        ".....ooimmddibhhbho.....oppeo...",  # 11
        ".....olmcMmcGwwwwmo......ooo....",  # 12
        "....oolmccMcGwiwGYmo............",  # 13
        "....olmmccMciGbGcmmo....omlmo...",  # 14
        "....olmmddvccMcMcmmooooomcMmo...",  # 15
        "...oolmdooYYYYYoMcmMMMMmcmmmo...",  # 16
        "...olmdo.oYwwwGoMcddoooooooo....",  # 17
        "...olmo..oGcPcGoMcvddo..........",  # 18
        "..oolmo..oGcpcGoMcvdddo.........",  # 19
        "..olmdo..oGcccGoMcvdddo.........",  # 20
        "..olmdo..oGYGYGoMcvdmddo........",  # 21
        "..oldho...oooooiMcvdmddo........",  # 22
        ".ooldho..olmmddccvvdmddho.......",  # 23
        ".olmdo..oolmmdccvcvdmddho.......",  # 24
        ".oldo...olmmddcvcvddmddhho......",  # 25
        ".oo....oolddhovcvdmdhdmddo......",  # 26
        ".......ooihho.oiddmdhoihdo......",  # 27
        "........oooo...oooooo.oooo......",  # 28
        "................................",  # 29
        "................................",  # 30
        "................................",  # 31
    ]),
    ("cast_0", 140, [
        "................................",  # 00  telegraph windup: orb swells, eyes flare
        ".........oooo...................",  # 01
        "........olmmmo........oo.oo.....",  # 02
        ".......ollmmmmo......oepWpeo....",  # 03  blazing swollen void orb!
        ".......olmmmmddo....opPWWWPpo...",  # 04
        "......oolmmmddhdo...oPWWWWWPpo..",  # 05
        "......olmmmddihpmo..opPWWWPpo...",  # 06
        ".....oolmmddihhhpo...oepWpeo....",  # 07
        ".....olmmddihhhhco....oo.oo.....",  # 08
        ".....olmmddihWWWPWo....olmo.....",  # 09  eyes blaze blinding white-magenta!
        ".....olmmddihWWWhco...omMmo.....",  # 10  casting arm raised back overhead
        "....ooimmddibhhbho...omcmo......",  # 11
        "....olmcMmcGwwwwmo..omcmo.......",  # 12
        "...oolmccMcGwiwGYmoomcmo........",  # 13
        "...olmmccMciGbGccmmmMmo.........",  # 14
        "...olmmddvccMcMcmmmmoo..........",  # 15
        "..oolmdooYYYYYoMcvdo............",  # 16
        "..olmdo.oYwwwGoMcvddo...........",  # 17  grimoire glows white-magenta!
        "..olmo..oGWPWGoMcvddo...........",  # 18
        ".oolmo..oGPWPGooMcvdddo.........",  # 19
        ".olmdo..oGWpWGo.Mcvdddo.........",  # 20
        ".olmdo..oGYGYGo.Mcvdmddo........",  # 21
        ".oldho...oooooi.Mcvdmddo........",  # 22
        "ooldho..olmmddccvvdmddho........",  # 23
        "olmdo..oolmmdccvcvdmddho........",  # 24
        "oldo...olmmddcvcvddmddhho.......",  # 25
        "oo....oolddhovcvdmdhdmddo.......",  # 26
        "......ooihho.oiddmdhoihdo.......",  # 27
        "........oooo..oooooo.oooo.......",  # 28
        "........oooo..oooooo.oooo.......",  # 29
        "................................",  # 30
        "................................",  # 31
    ]),
    ("cast_1", 170, [
        "................................",  # 00  release thrust forward!
        "................................",  # 01
        "...........oooo.................",  # 02
        "..........olmmmo................",  # 03
        ".........ollmmmmo...............",  # 04
        ".........olmmmmddo..............",  # 05
        "........oolmmmddhdo.............",  # 06
        "........olmmmddihpmo............",  # 07
        ".......oolmmddihhhpo......ooo...",  # 08
        ".......olmmddihhhhco.....oepPo..",  # 09
        ".......olmmddihWWWPo....oePWWpo.",  # 10  forward void blast eruption cleanly outlined!
        ".......olmmddihhWWco....oPWWWPpo",  # 11
        "......ooimmddibhhbho...oepWWWPeo",  # 12
        "......olmcMmcGwwwwmo...opPWWWPpo",  # 13
        ".....oolmccMcGwiwGYmo...oepWWpo.",  # 14
        ".....olmmccMciGbGcmmo..oolmlpoo.",  # 15  thrusting casting palm!
        "....oolmddvccMcMcmmooooomcMmo...",  # 16
        "...oolmdooYYYYYoMcmMMMMmcmmmo...",  # 17
        "..oolmdo.oYwwwGoMcddoooooooo....",  # 18
        "..olmdo..oGWPWGoMcvddo..........",  # 19
        ".oolmo...oGPpPGoMcvdddo.........",  # 20
        ".olmdo...oGcccGoMcvdddo.........",  # 21
        ".oldho...oGYGYGoMcvdmddo........",  # 22
        "ooldho....oooooiMcvdmddo........",  # 23
        "olmdo....olmmddccvvdmddho.......",  # 24
        "oldo....oolmmdccvcvdmddho.......",  # 25
        "ooo.....olmmddcvcvddmddhho......",  # 26
        ".......oolddhovcvdmdhdmddo......",  # 27
        ".......ooihho.oiddmdhoihdo......",  # 28
        ".........oooo..oooooo.oooo......",  # 29
        "................................",  # 30
        "................................",  # 31
    ]),
]


# ===========================================================================
# SHARED PIXEL-GRID CANVAS PAINTER FOR LARGER ARTICULATED MONSTERS (48x48, 64x64)
# ===========================================================================
class PixelCanvas:
    """Explicit integer-pixel grid painter with directional bevels and continuous 1px outline."""

    def __init__(self, w: int, h: int):
        self.w = w
        self.h = h
        self.grid: list[list[str]] = [["." for _ in range(w)] for _ in range(h)]

    def set(self, x: int, y: int, key: str) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            self.grid[y][x] = key

    def get(self, x: int, y: int) -> str:
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.grid[y][x]
        return "."

    def fill_poly(self, pts: list[tuple[float, float]], key: str) -> set[tuple[int, int]]:
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        min_x = max(0, int(math.floor(min(xs))))
        max_x = min(self.w - 1, int(math.ceil(max(xs))))
        min_y = max(0, int(math.floor(min(ys))))
        max_y = min(self.h - 1, int(math.ceil(max(ys))))
        filled: set[tuple[int, int]] = set()
        n = len(pts)
        for py in range(min_y, max_y + 1):
            cy = py + 0.5
            for px in range(min_x, max_x + 1):
                cx = px + 0.5
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = pts[i]
                    xj, yj = pts[j]
                    if ((yi > cy) != (yj > cy)) and (cx < (xj - xi) * (cy - yi) / (yj - yi + 1e-12) + xi):
                        inside = not inside
                    j = i
                if inside:
                    self.grid[py][px] = key
                    filled.add((px, py))
        return filled

    def fill_beveled_poly(
        self,
        pts: list[tuple[float, float]],
        ramp5: tuple[str, str, str, str, str],
        crease_sides: str = "",
        crease_ink: str = "i",
    ) -> set[tuple[int, int]]:
        """Sculpt an asymmetric faceted stone/armor polygon with crisp 135 deg bevels."""
        region = self.fill_poly(pts, ramp5[2])
        if not region:
            return region
        xs = [p[0] for p in region]
        ys = [p[1] for p in region]
        x0, x1 = min(xs), max(xs)
        y0, y1 = min(ys), max(ys)
        s0, s1, s2, s3, s4 = ramp5
        for x, y in region:
            has_t = (x, y - 1) in region
            has_l = (x - 1, y) in region
            has_r = (x + 1, y) in region
            has_b = (x, y + 1) in region
            if not has_t and not has_l:
                c = s4
            elif not has_t or not has_l:
                c = s4 if ((x - x0) + (y - y0)) < (x1 - x0 + y1 - y0) * 0.45 else s3
            elif not has_b and not has_r:
                c = s0
            elif not has_b or not has_r:
                c = s1
            else:
                nx = (x - x0) / max(1, x1 - x0)
                ny = (y - y0) / max(1, y1 - y0)
                diag = nx * 0.48 + ny * 0.52
                if diag < 0.30:
                    c = s3
                elif diag < 0.65:
                    c = s2
                elif diag < 0.88:
                    c = s1
                else:
                    c = s0
            self.set(x, y, c)
        if crease_sides:
            self.draw_crease_border(region, crease_ink, crease_sides)
        return region

    def draw_crease_border(self, pts: set[tuple[int, int]], ink: str, sides: str = "all") -> None:
        for x, y in pts:
            checks = []
            if "t" in sides or sides == "all":
                checks.append((x, y - 1) not in pts)
            if "b" in sides or sides == "all":
                checks.append((x, y + 1) not in pts)
            if "l" in sides or sides == "all":
                checks.append((x - 1, y) not in pts)
            if "r" in sides or sides == "all":
                checks.append((x + 1, y) not in pts)
            if any(checks):
                self.set(x, y, ink)

    def apply_outer_outline(self, outline_key: str = "o") -> None:
        """Apply crisp 4-neighbor outer 1px #14101a silhouette outline around all filled pixels."""
        to_outline: list[tuple[int, int]] = []
        for y in range(self.h):
            for x in range(self.w):
                if self.grid[y][x] != ".":
                    continue
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.w and 0 <= ny < self.h:
                        nb = self.grid[ny][nx]
                        if nb != "." and nb != outline_key:
                            to_outline.append((x, y))
                            break
        for x, y in to_outline:
            self.grid[y][x] = outline_key

    def rows(self) -> list[str]:
        return ["".join(r) for r in self.grid]


# ===========================================================================
# 2. RUNIC GRANITE GOLEM (48x48, 8 frames, stone boots grounded at y=45)
# ===========================================================================
GOLEM_PALETTE = {
    "o": "#14101a",  # universal outer silhouette outline
    "i": "#1e1829",  # deep inner stone seam / recess
    "s": "#282c38",  # runic_stone[0] deep slate shadow
    "d": "#3e4556",  # runic_stone[1] shaded granite
    "m": "#5a657a",  # runic_stone[2] mid granite
    "l": "#808fa6",  # runic_stone[3] light granite bevel
    "h": "#aebdcf",  # runic_stone[4] crisp top-left stone bevel highlight
    "v": "#163020",  # moss cast shadow / deep moss
    "k": "#224d2c",  # dark emerald moss
    "g": "#347539",  # mid emerald moss
    "G": "#52a349",  # sunlit moss
    "L": "#82cf63",  # bright moss catchlight
    "c": "#1c698c",  # arcane_crystal[0] deep cyan glow
    "C": "#33b5e5",  # arcane_crystal[1] vibrant cyan
    "E": "#8ff5ff",  # arcane_crystal[2] bright glow cyan
    "W": "#ffffff",  # arcane_crystal[3] white-hot rune core
}

STONE_RAMP = ("s", "d", "m", "l", "h")


def draw_organic_moss(
    cv: PixelCanvas,
    clumps: list[tuple[int, int, int]],
    drips: list[tuple[int, int, int]],
) -> None:
    """Paint layered organic emerald moss mounds with dripping vine tufts over granite."""
    moss_pts: set[tuple[int, int]] = set()
    for cx, cy, rx in clumps:
        for dy in range(-2, 2):
            for dx in range(-rx, rx + 1):
                if (dx * dx) / max(1.0, rx * rx) + (dy * dy) / 2.6 <= 1.0:
                    moss_pts.add((cx + dx, cy + dy))
    for vx, vy, length in drips:
        for step in range(length):
            moss_pts.add((vx, vy + step))

    for x, y in moss_pts:
        has_t = (x, y - 1) in moss_pts
        has_l = (x - 1, y) in moss_pts
        has_b = (x, y + 1) in moss_pts
        if not has_t and not has_l:
            cv.set(x, y, "L")
        elif not has_t:
            cv.set(x, y, "L" if (x + y) % 3 == 0 else "G")
        elif not has_b:
            cv.set(x, y, "v" if (x % 2 == 0) else "k")
        else:
            cv.set(x, y, "G" if (x + y) % 3 == 1 else "g")

    # Cast underside shadow directly onto stone below moss lip
    for x, y in moss_pts:
        if (x, y + 1) not in moss_pts and cv.get(x, y + 1) not in (".", "o", "i"):
            cv.set(x, y + 1, "v")
            if cv.get(x, y + 2) not in (".", "o", "i"):
                cv.set(x, y + 2, "i")


def draw_golem_gauntlet(
    cv: PixelCanvas,
    sx: int,
    sy: int,
    ex: int,
    ey: int,
    fx: int,
    fy: int,
    is_near: bool,
    core_bright: int,
    raised: bool = False,
) -> None:
    """Draw craggy articulated stone arm + oversized banded gauntlet hanging to knees (or raised)."""
    # Upper arm + elbow boulder
    upper = cv.fill_beveled_poly([
        (sx - 3, sy - 2),
        (sx + 4, sy - 2),
        (ex + 4, ey + 2),
        (ex - 4, ey + 2),
    ], STONE_RAMP)

    if raised:
        # Giant upturned fist raised overhead (staying safely at y>=2!)
        fist = cv.fill_beveled_poly([
            (fx - 5, fy),
            (fx + 5, fy),
            (fx + 6, fy + 6),
            (fx + 4, fy + 11),
            (fx - 4, fy + 11),
            (fx - 6, fy + 5),
        ], STONE_RAMP, crease_sides="b" if not is_near else "rb")
        # Banded iron-stone wrist strap
        for by in (fy + 7, fy + 8):
            for x in range(fx - 5, fx + 6):
                if (x, by) in fist:
                    cv.set(x, by, "i" if by == fy + 7 else ("h" if x < fx else "s"))
        # Blazing knuckle runes
        rc = "W" if core_bright >= 2 else "E"
        for kx, ky in [(fx - 2, fy + 3), (fx - 1, fy + 3), (fx + 1, fy + 3), (fx, fy + 5)]:
            if (kx, ky) in fist:
                cv.set(kx, ky, rc)
        return

    # Angled heavy forearm & oversized gauntlet reaching forward-down to knees
    # Tapering wider toward massive banded fist
    gauntlet = cv.fill_beveled_poly([
        (ex - 4, ey - 2),
        (ex + 4, ey - 2),
        (fx + 6, fy + 4),
        (fx + 4, fy + 10),
        (fx - 4, fy + 10),
        (fx - 6, fy + 4),
    ], STONE_RAMP, crease_sides="rb" if is_near else "")

    # Twin angled dark stone/iron gauntlet bands with rivets
    for off_y in (1, 5):
        for x in range(fx - 6, fx + 7):
            py = fy + off_y
            if (x, py) in gauntlet:
                cv.set(x, py, "i")
            if (x, py + 1) in gauntlet:
                cv.set(x, py + 1, "h" if (x - fx) in (-3, 2) else ("l" if x < fx else "s"))

    # Glowing cyan rune sigil etched in gauntlet plate
    rc_hi = "W" if core_bright >= 2 else ("E" if core_bright == 1 else "C")
    rc_lo = "E" if core_bright >= 1 else "c"
    for rx, ry, col in [(fx, fy + 3, rc_hi), (fx - 1, fy + 4, rc_lo), (fx + 1, fy + 4, rc_lo)]:
        if (rx, ry) in gauntlet:
            cv.set(rx, ry, col)

    # Chunky segmented stone fingers/knuckles at bottom
    for kx in (fx - 3, fx, fx + 3):
        if (kx, fy + 9) in gauntlet:
            cv.set(kx, fy + 9, "i")
        if (kx, fy + 8) in gauntlet:
            cv.set(kx, fy + 8, "s")


def build_golem_frame(
    fname: str,
    dur: int,
    body_dy: int = 0,
    torso_dx: int = 0,
    core_bright: int = 0,
    front_leg_dx: int = 0,
    front_leg_dy: int = 0,
    back_leg_dx: int = 0,
    back_leg_dy: int = 0,
    near_fist_off: tuple[int, int] = (0, 0),
    far_fist_off: tuple[int, int] = (0, 0),
    arms_raised: bool = False,
    slam_impact: bool = False,
) -> tuple[str, int, list[str]]:
    cv = PixelCanvas(48, 48)
    cx = 23 + torso_dx

    # 1. FAR SHOULDER CRAG & FAR GAUNTLET (behind torso, forward-right +X)
    if not arms_raised:
        draw_golem_gauntlet(
            cv,
            sx=cx + 10,
            sy=16 + body_dy,
            ex=cx + 13 + far_fist_off[0] // 2,
            ey=21 + body_dy + far_fist_off[1] // 2,
            fx=cx + 14 + far_fist_off[0],
            fy=28 + body_dy + far_fist_off[1],
            is_near=False,
            core_bright=core_bright,
            raised=False,
        )
        # Craggy far shoulder boulder sloping forward
        cv.fill_beveled_poly([
            (cx + 6, 10 + body_dy),
            (cx + 15, 11 + body_dy),
            (cx + 17, 17 + body_dy),
            (cx + 13, 21 + body_dy),
            (cx + 6, 19 + body_dy),
        ], STONE_RAMP)
        draw_organic_moss(cv, [(cx + 10, 10 + body_dy, 3), (cx + 14, 12 + body_dy, 2)], [(cx + 9, 12 + body_dy, 2), (cx + 14, 13 + body_dy, 3)])

    # 2. PILLAR LEGS & ANGLED HEAVY STONE BOOTS (grounded at y=45 outline!)
    # Back/left pillar leg
    blx = 16 + back_leg_dx
    bly = 45 + back_leg_dy
    cv.fill_beveled_poly([
        (blx - 2, 29 + body_dy),
        (blx + 6, 29 + body_dy),
        (blx + 5, bly - 4),
        (blx - 2, bly - 4),
    ], STONE_RAMP)
    cv.fill_beveled_poly([
        (blx - 3, bly - 4),
        (blx + 5, bly - 4),
        (blx + 7, bly),
        (blx - 3, bly),
    ], STONE_RAMP)

    # Front/right stepping pillar leg (facing +X)
    flx = 27 + front_leg_dx
    fly = 45 + front_leg_dy
    cv.fill_beveled_poly([
        (flx - 2, 29 + body_dy),
        (flx + 6, 29 + body_dy),
        (flx + 6, fly - 4),
        (flx - 1, fly - 4),
    ], STONE_RAMP)
    # Sculpted knee boulder + emerald moss tuft safely clear of far gauntlet
    cv.fill_beveled_poly([
        (flx - 1, 34 + body_dy),
        (flx + 5, 34 + body_dy),
        (flx + 5, 39 + body_dy),
        (flx, 39 + body_dy),
    ], STONE_RAMP, crease_sides="b")
    cv.set(flx + 1, 35 + body_dy, "G")
    cv.set(flx + 2, 35 + body_dy, "g")
    # Heavy stone boot angled forward-right
    cv.fill_beveled_poly([
        (flx - 2, fly - 4),
        (flx + 6, fly - 4),
        (flx + 9, fly),
        (flx - 2, fly),
    ], STONE_RAMP)

    # 3. BROAD MONOLITHIC V-TAPERED GRANITE TORSO
    # Tapered lower waist / pelvis
    cv.fill_beveled_poly([
        (cx - 8, 25 + body_dy),
        (cx + 8, 25 + body_dy),
        (cx + 7, 32 + body_dy),
        (cx - 7, 32 + body_dy),
    ], STONE_RAMP)
    for x in range(cx - 7, cx + 8):
        cv.set(x, 28 + body_dy, "i")
    cv.set(cx + 2, 28 + body_dy, "C" if core_bright == 0 else "E")
    cv.set(cx + 3, 28 + body_dy, "C" if core_bright == 0 else "E")

    # Massive upper chest boulder (wider top, tapering down toward waist!)
    chest = cv.fill_beveled_poly([
        (cx - 12, 12 + body_dy),
        (cx + 11, 13 + body_dy),
        (cx + 9, 26 + body_dy),
        (cx - 8, 26 + body_dy),
    ], STONE_RAMP)
    # Asymmetric granite plate cracks
    for rx, ry in [(cx - 5, 16), (cx - 4, 17), (cx - 4, 18), (cx - 3, 19), (cx - 3, 22), (cx - 2, 23), (cx + 7, 15), (cx + 6, 16)]:
        if (rx, ry + body_dy) in chest:
            cv.set(rx, ry + body_dy, "i")

    # Sunken Glowing Arcane Cyan Core (3/4 right-facing at cx+3, y=19)
    core_x = cx + 3
    core_y = 19 + body_dy
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            dist = abs(dx) + abs(dy)
            px, py = core_x + dx, core_y + dy
            if dist == 4:
                cv.set(px, py, "i")
            elif dist == 3:
                cv.set(px, py, "c" if core_bright == 0 else "C")
            elif dist == 2:
                cv.set(px, py, "C" if core_bright == 0 else "E")
            elif dist == 1:
                cv.set(px, py, "E" if core_bright == 0 else "W")
            elif dist == 0:
                cv.set(px, py, "W")
    if core_bright >= 1:
        for rx, ry in [(core_x - 4, core_y), (core_x + 4, core_y), (core_x, core_y - 4), (core_x, core_y + 4)]:
            cv.set(rx, ry, "C" if core_bright == 1 else "E")

    # 4. CRAGGY HEAD CREST & SUNKEN RUNE EYE VISOR (facing RIGHT +X)
    hx0, hy0 = cx - 4, 6 + body_dy
    cv.fill_beveled_poly([
        (hx0, hy0 + 1),
        (hx0 + 4, hy0 - 1),
        (hx0 + 10, hy0),
        (hx0 + 12, hy0 + 3),
        (hx0 + 11, hy0 + 9),
        (hx0 + 1, hy0 + 9),
    ], STONE_RAMP, crease_sides="b")
    # Recessed dark rune eye visor
    vy = hy0 + 4
    for x in range(cx + 1, cx + 8):
        cv.set(x, vy - 1, "i")
        cv.set(x, vy, "i")
        cv.set(x, vy + 1, "s")
    eye_hi = "W" if core_bright >= 1 else "E"
    eye_lo = "E" if core_bright >= 1 else "C"
    cv.set(cx + 2, vy, eye_lo)
    cv.set(cx + 3, vy, eye_hi)
    cv.set(cx + 5, vy, eye_hi)
    cv.set(cx + 6, vy, eye_lo)
    # Lush moss crown over head crag
    draw_organic_moss(cv, [(cx + 1, hy0, 4), (cx + 5, hy0 + 1, 2)], [(cx - 1, hy0 + 1, 2), (cx + 3, hy0 + 1, 3)])

    # 5. NEAR CRAGGY SHOULDER & NEAR OVERSIZED GAUNTLET (or TWO-FISTED WINDUP)
    if arms_raised:
        # Both huge fists heaved up overhead (keeping y >= 2 for full 1px outline!)
        draw_golem_gauntlet(cv, cx + 10, 14 + body_dy, cx + 11, 10 + body_dy, cx + 12, 2 + body_dy, False, core_bright, raised=True)
        cv.fill_beveled_poly([
            (cx - 17, 11 + body_dy),
            (cx - 12, 7 + body_dy),
            (cx - 5, 9 + body_dy),
            (cx - 5, 19 + body_dy),
            (cx - 16, 19 + body_dy),
        ], STONE_RAMP, crease_sides="rb")
        draw_organic_moss(cv, [(cx - 13, 8 + body_dy, 3), (cx - 8, 9 + body_dy, 2)], [(cx - 14, 10 + body_dy, 3)])
        draw_golem_gauntlet(cv, cx - 12, 13 + body_dy, cx - 12, 9 + body_dy, cx - 12, 2 + body_dy, True, core_bright, raised=True)
    else:
        # Towering craggy near shoulder sloping dynamically down-left
        cv.fill_beveled_poly([
            (cx - 17, 12 + body_dy),
            (cx - 12, 8 + body_dy),
            (cx - 5, 9 + body_dy),
            (cx - 4, 20 + body_dy),
            (cx - 16, 20 + body_dy),
        ], STONE_RAMP, crease_sides="rb")
        draw_organic_moss(cv, [(cx - 13, 9 + body_dy, 4), (cx - 8, 10 + body_dy, 2)], [(cx - 15, 11 + body_dy, 3), (cx - 10, 11 + body_dy, 3), (cx - 7, 12 + body_dy, 2)])
        draw_golem_gauntlet(
            cv,
            sx=cx - 11,
            sy=16 + body_dy,
            ex=cx - 12 + near_fist_off[0] // 2,
            ey=22 + body_dy + near_fist_off[1] // 2,
            fx=cx - 11 + near_fist_off[0],
            fy=28 + body_dy + near_fist_off[1],
            is_near=True,
            core_bright=core_bright,
            raised=False,
        )

    # 6. SEISMIC SLAM SHOCKWAVE BURST (Frame 7)
    if slam_impact:
        for sx in range(cx - 18, cx + 22):
            if abs(sx - (cx - 11)) <= 5 or abs(sx - (cx + 15)) <= 5:
                cv.set(sx, 44, "E")
                cv.set(sx, 43, "C")
        for rx, ry, col in [
            (cx - 17, 42, "W"), (cx - 15, 40, "E"), (cx - 9, 41, "W"),
            (cx + 11, 41, "W"), (cx + 15, 40, "W"), (cx + 18, 42, "E"),
            (cx + 21, 43, "C"), (cx + 19, 39, "E"), (cx - 19, 43, "C"),
        ]:
            cv.set(rx, ry, col)

    cv.apply_outer_outline("o")
    return (fname, dur, cv.rows())


GOLEM_FRAMES = [
    build_golem_frame("idle_0", 250, body_dy=0, core_bright=0),
    build_golem_frame("idle_1", 250, body_dy=1, core_bright=1, near_fist_off=(0, 1), far_fist_off=(0, 1)),
    build_golem_frame("stomp_0", 145, body_dy=1, torso_dx=1, core_bright=0, front_leg_dx=3, front_leg_dy=0, back_leg_dx=-3, back_leg_dy=-2, near_fist_off=(4, -1), far_fist_off=(-3, 0)),
    build_golem_frame("stomp_1", 130, body_dy=-1, torso_dx=0, core_bright=0, front_leg_dx=1, front_leg_dy=0, back_leg_dx=0, back_leg_dy=-4, near_fist_off=(1, -1), far_fist_off=(-1, -1)),
    build_golem_frame("stomp_2", 145, body_dy=1, torso_dx=1, core_bright=0, front_leg_dx=-3, front_leg_dy=-2, back_leg_dx=3, back_leg_dy=0, near_fist_off=(-4, 0), far_fist_off=(4, -1)),
    build_golem_frame("stomp_3", 130, body_dy=-1, torso_dx=0, core_bright=0, front_leg_dx=0, front_leg_dy=-4, back_leg_dx=1, back_leg_dy=0, near_fist_off=(-1, -1), far_fist_off=(1, -1)),
    build_golem_frame("slam_0", 170, body_dy=0, torso_dx=-1, core_bright=2, arms_raised=True),
    build_golem_frame("slam_1", 210, body_dy=4, torso_dx=2, core_bright=2, front_leg_dx=2, back_leg_dx=-2, near_fist_off=(1, 4), far_fist_off=(2, 4), slam_impact=True),
]


# ===========================================================================
# 3. OBSIDIAN MAGMA COLOSSUS — FINAL BOSS (64x64, 8 frames, greaves at y=60)
# ===========================================================================
BOSS_PALETTE = {
    "o": "#14101a",  # deep_void outer outline
    "i": "#1e1829",  # indigo inner crease
    "h": "#1c1624",  # obsidian deepest shadow
    "d": "#2e2238",  # obsidian dark armor
    "m": "#473354",  # obsidian mid armor
    "l": "#634973",  # obsidian rim highlight
    "k": "#4a1222",  # crimson deep carapace shadow
    "r": "#801b2e",  # crimson dark carapace
    "c": "#bf2633",  # crimson mid carapace
    "R": "#f25244",  # crimson light ridge
    "S": "#ff8a66",  # crimson top-left catchlight
    "a": "#282c38",  # chain iron dark
    "A": "#5a657a",  # chain iron mid
    "I": "#aebdcf",  # chain iron highlight / horn ridge
    "M": "#781722",  # deep molten ember
    "f": "#bf2626",  # dark lava fissure
    "O": "#f25c22",  # molten orange
    "g": "#ff9e24",  # bright furnace orange
    "y": "#ffe054",  # molten gold yellow
    "w": "#fffbe6",  # blinding white-gold furnace core
}

OBSIDIAN_RAMP = ("h", "d", "m", "l", "I")
CRIMSON_RAMP = ("k", "r", "c", "R", "S")
IRON_RAMP = ("a", "A", "A", "I", "I")


def draw_boss_halo(cv: PixelCanvas, cx: int, cy: int, flare: int) -> None:
    """Burning golden sigil ring halo floating behind the horned helm & shoulders."""
    r_in = 10.2 if flare < 2 else 9.6
    r_out = 12.6 if flare == 0 else (13.2 if flare == 1 else 14.0)
    for y in range(2, min(61, cy + 16)):
        for x in range(2, min(61, cx + 18)):
            dx = x - cx
            dy = y - cy
            dist = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            # 8 clean sun-sigil prongs radiating from ring
            spoke = max(0.0, math.cos(ang * 4.0)) ** 7
            outer = r_out + (2.8 if flare >= 2 else 1.8) * spoke
            if r_in <= dist <= outer and dy <= 6:
                if dist > outer - 0.9:
                    cv.set(x, y, "f" if flare == 0 else "O")
                elif dist > r_in + 1.0:
                    cv.set(x, y, "g" if flare == 0 else ("y" if flare == 1 else "w"))
                else:
                    cv.set(x, y, "y" if flare == 0 else "w")


def draw_boss_war_claw(
    cv: PixelCanvas,
    sx: int,
    sy: int,
    ex: int,
    ey: int,
    hx: int,
    hy: int,
    claw_dir: int,
    is_near: bool,
    flare: int,
    raised: bool = False,
) -> None:
    """Sculpted bent-elbow demon gauntlet + branching molten veins + 3 giant curved talons."""
    # Upper bicep & elbow armor
    bicep = cv.fill_beveled_poly([
        (sx - 3, sy - 2),
        (sx + 4, sy - 2),
        (ex + 4, ey + 2),
        (ex - 4, ey + 2),
    ], CRIMSON_RAMP)

    if raised:
        # Raised massive war-claw splayed outward/upward for Nova windup!
        gaunt = cv.fill_beveled_poly([
            (hx - 5, hy + 1),
            (hx + 5, hy + 1),
            (hx + 6, hy + 7),
            (ex + 4, ey + 3),
            (ex - 4, ey + 3),
            (hx - 6, hy + 7),
        ], CRIMSON_RAMP, crease_sides="rb" if is_near else "")
        # Molten magma veins snaking into palm
        vein = "w" if flare >= 2 else "y"
        for vx, vy in [(ex, ey), (hx, hy + 5), (hx - claw_dir, hy + 4), (hx + claw_dir, hy + 3)]:
            if (vx, vy) in gaunt or (vx, vy) in bicep:
                cv.set(vx, vy, vein)
                cv.set(vx, vy + 1, "g")
        # 3 sharp curved talons splaying up
        for tx_off in (-4, 0, 4):
            for s in range(5):
                px = hx + tx_off + (claw_dir if s >= 3 else 0)
                py = hy - s
                if py >= 2:
                    cv.set(px, py, "w" if s >= 3 else ("y" if s >= 1 else "g"))
        return

    # Dynamic angled forearm -> huge flared gauntlet fist
    gaunt = cv.fill_beveled_poly([
        (ex - 4, ey - 2),
        (ex + 4, ey - 2),
        (hx + 6, hy + 2),
        (hx + 4, hy + 8),
        (hx - 4, hy + 8),
        (hx - 6, hy + 2),
    ], CRIMSON_RAMP, crease_sides="rb" if is_near else "")

    # Obsidian wrist guard ridge & rivets
    for x in range(ex - 4, ex + 5):
        if (x, ey + 1) in gaunt:
            cv.set(x, ey + 1, "i")
        if (x, ey + 2) in gaunt:
            cv.set(x, ey + 2, "m" if x < ex else "d")

    # Continuous branching molten lava veins snaking down forearm into claw!
    v_hi = "w" if flare >= 2 else "y"
    v_mid = "y" if flare >= 1 else "g"
    v_lo = "g" if flare >= 1 else "O"
    # Connected diagonal fissure path along arm -> gauntlet -> talons
    vein_path = [
        (sx, sy + 2), (sx + claw_dir, sy + 3),
        ((sx + ex) // 2, (sy + ey) // 2), ((sx + ex) // 2, (sy + ey) // 2 + 1),
        (ex, ey), (ex, ey + 1), (ex + claw_dir, ey + 2),
        (hx - claw_dir, hy), (hx, hy + 1), (hx, hy + 2),
        (hx + claw_dir, hy + 3), (hx + claw_dir, hy + 4),
        (hx, hy + 5), (hx - claw_dir, hy + 5), (hx - claw_dir * 2, hy + 6),
        (hx + claw_dir * 2, hy + 5), (hx + claw_dir * 3, hy + 6),
    ]
    for idx, (vx, vy) in enumerate(vein_path):
        if (vx, vy) in gaunt or (vx, vy) in bicep:
            cv.set(vx, vy, v_hi if idx >= 10 else v_mid)
            if (vx - claw_dir, vy) in gaunt and cv.get(vx - claw_dir, vy) not in (v_hi, v_mid):
                cv.set(vx - claw_dir, vy, v_lo)

    # 3 huge curved obsidian/magma war-talons with crisp #1e1829 separation
    for idx, dx in enumerate((-3, 0, 3)):
        bx = hx + dx
        by = hy + 8 - (1 if idx == 2 else 0)
        for step in range(5):
            px = bx + int(claw_dir * (step * 0.8))
            py = by + step
            col = ("w" if flare >= 1 else "y") if step >= 3 else ("y" if step >= 1 else "g")
            cv.set(px, py, col)
            if step < 4:
                cv.set(px - claw_dir, py, "i" if step == 0 else "d")


def build_boss_frame(
    fname: str,
    dur: int,
    body_dy: int = 0,
    torso_dx: int = 0,
    flare: int = 0,
    front_leg_dx: int = 0,
    front_leg_dy: int = 0,
    back_leg_dx: int = 0,
    back_leg_dy: int = 0,
    near_claw_off: tuple[int, int] = (0, 0),
    far_claw_off: tuple[int, int] = (0, 0),
    arms_raised: bool = False,
    nova_unleash: bool = False,
) -> tuple[str, int, list[str]]:
    cv = PixelCanvas(64, 64)
    cx = 30 + torso_dx

    # 1. FLOATING GOLDEN SIGIL HALO RING BEHIND HEAD/HORNS
    draw_boss_halo(cv, cx - 1, 15 + body_dy, flare)

    # 2. FAR SPIKED WAR-PAULDRON & FAR WAR-CLAW (behind torso, forward +X)
    if arms_raised:
        draw_boss_war_claw(cv, cx + 13, 19 + body_dy, cx + 19, 17 + body_dy, cx + 22, 10 + body_dy, +1, False, flare, raised=True)
    else:
        draw_boss_war_claw(
            cv,
            sx=cx + 12,
            sy=21 + body_dy,
            ex=cx + 17 + far_claw_off[0] // 2,
            ey=28 + body_dy + far_claw_off[1] // 2,
            hx=cx + 19 + far_claw_off[0],
            hy=34 + body_dy + far_claw_off[1],
            claw_dir=+1,
            is_near=False,
            flare=flare,
            raised=False,
        )
    # Swept crimson-obsidian far pauldron + 2 curved obsidian spikes
    cv.fill_beveled_poly([
        (cx + 8, 16 + body_dy),
        (cx + 18, 17 + body_dy),
        (cx + 21, 24 + body_dy),
        (cx + 15, 27 + body_dy),
        (cx + 8, 25 + body_dy),
    ], CRIMSON_RAMP)
    for sx, sy, hgt in [(cx + 13, 17 + body_dy, 6), (cx + 18, 19 + body_dy, 5)]:
        cv.fill_beveled_poly([(sx - 2, sy + 1), (sx + 2, sy - hgt), (sx + 2, sy + 1)], OBSIDIAN_RAMP)
        cv.set(sx + 2, sy - hgt + 1, "w" if flare >= 2 else "I")

    # 3. WIDE PLANTED TITAN GREAVES (grounded at y=60 outline!)
    # Rear leg angled back-left
    blx = 19 + back_leg_dx
    bly = 60 + back_leg_dy
    cv.fill_beveled_poly([
        (blx - 2, 40 + body_dy),
        (blx + 8, 40 + body_dy),
        (blx + 6, bly - 5),
        (blx - 3, bly - 5),
    ], OBSIDIAN_RAMP)
    for y in range(47, bly - 5):
        cv.set(blx + 3, y, "g" if flare == 0 else "y")
    cv.fill_beveled_poly([
        (blx - 4, bly - 5),
        (blx + 6, bly - 5),
        (blx + 7, bly),
        (blx - 5, bly),
    ], CRIMSON_RAMP)

    # Front leg planted powerfully forward-right (+X) with crisp separation crease
    flx = 34 + front_leg_dx
    fly = 60 + front_leg_dy
    cv.fill_beveled_poly([
        (flx - 3, 40 + body_dy),
        (flx + 7, 40 + body_dy),
        (flx + 9, fly - 5),
        (flx - 1, fly - 5),
    ], OBSIDIAN_RAMP, crease_sides="l")
    # Spiked crimson knee guard
    cv.fill_beveled_poly([
        (flx - 2, 43 + body_dy),
        (flx + 7, 43 + body_dy),
        (flx + 8, 48 + body_dy),
        (flx - 1, 48 + body_dy),
    ], CRIMSON_RAMP, crease_sides="lb")
    cv.set(flx + 7, 43 + body_dy, "I")
    cv.set(flx + 8, 42 + body_dy, "w" if flare >= 2 else "I")
    # Molten shin fissure
    for y in range(49, fly - 4):
        cv.set(flx + 5, y, "y" if flare >= 1 else "g")
        cv.set(flx + 4, y, "O")
    # Heavy clawed armored war-boot grounded cleanly at y=59 (outline y=60!)
    boot_pts = cv.fill_beveled_poly([
        (flx - 2, fly - 5),
        (flx + 8, fly - 5),
        (flx + 12, fly),
        (flx - 2, fly),
    ], CRIMSON_RAMP, crease_sides="l")
    cv.set(flx + 9, fly - 2, "I")
    cv.set(flx + 10, fly - 1, "I")

    # 4. BROAD V-TAPERED OBSIDIAN CHEST & FURNACE RIBCAGE CORE
    cv.fill_beveled_poly([
        (cx - 14, 18 + body_dy),
        (cx + 13, 18 + body_dy),
        (cx + 9, 39 + body_dy),
        (cx - 9, 39 + body_dy),
    ], OBSIDIAN_RAMP)

    # Roaring Molten Furnace Ribcage Cavity (3/4 right-biased at cx+2)
    fcx = cx + 2
    for ry in range(22 + body_dy, 36 + body_dy):
        half_w = 5 if ry < 29 + body_dy else 4
        for rx in range(fcx - half_w, fcx + half_w + 1):
            dist = abs(rx - fcx) / max(1, half_w) + abs(ry - (28 + body_dy)) / 7.5
            if dist < 0.45 or (flare >= 2 and dist < 0.72):
                cv.set(rx, ry, "w")
            elif dist < 0.78:
                cv.set(rx, ry, "y")
            elif dist < 1.05:
                cv.set(rx, ry, "g" if flare == 0 else "y")
            else:
                cv.set(rx, ry, "O" if flare >= 1 else "f")

    # Curved Obsidian Rib Bars arcing across the glowing furnace!
    for rib_y in (23 + body_dy, 27 + body_dy, 31 + body_dy):
        for side in (-1, 1):
            for step in range(4):
                rx = fcx + side * (2 + step)
                ry = rib_y + (0 if step < 2 else 1)
                cv.set(rx, ry, "l" if side == -1 and step >= 2 else ("m" if side == -1 else "d"))
                cv.set(rx, ry + 1, "h")

    # 5. CURVED 3/4 CHAIN-BOUND IRON BELT & TASSETS
    for bx in range(cx - 9, cx + 10):
        # 3/4 belt dips +1px in center
        dip = 1 if abs(bx - cx) <= 4 else 0
        by = 37 + body_dy + dip
        cv.set(bx, by, "i")
        cv.set(bx, by + 3, "i")
        if (bx - cx) % 3 == 0:
            cv.set(bx, by + 1, "I")
            cv.set(bx, by + 2, "A")
        else:
            cv.set(bx, by + 1, "A")
            cv.set(bx, by + 2, "a")
    # Center molten skull belt clasp + hanging chain
    byc = 38 + body_dy
    cv.fill_beveled_poly([(fcx - 2, byc), (fcx + 2, byc), (fcx + 2, byc + 4), (fcx - 2, byc + 4)], IRON_RAMP)
    cv.set(fcx, byc + 2, "w" if flare >= 1 else "y")
    cv.set(fcx, byc + 5, "I")
    cv.set(fcx - 1, byc + 6, "A")
    cv.set(fcx, byc + 7, "a")
    # Crimson armored side tassets
    cv.fill_beveled_poly([(cx - 10, 40 + body_dy), (cx - 4, 41 + body_dy), (cx - 4, 46 + body_dy), (cx - 10, 45 + body_dy)], CRIMSON_RAMP)
    cv.fill_beveled_poly([(cx + 4, 41 + body_dy), (cx + 10, 40 + body_dy), (cx + 10, 45 + body_dy), (cx + 4, 46 + body_dy)], CRIMSON_RAMP)

    # 6. FIERCE 3/4 HORNED DEMON HELM & BOLD JAGGED OBSIDIAN CROWN-HORNS
    # Draw sculpted 3/4 demon helm face looking RIGHT (+X) first so horns spring cleanly from temples!
    helm = cv.fill_beveled_poly([
        (cx - 5, 11 + body_dy),
        (cx + 5, 10 + body_dy),
        (cx + 8, 14 + body_dy),
        (cx + 7, 20 + body_dy),
        (cx - 4, 20 + body_dy),
    ], OBSIDIAN_RAMP, crease_sides="b")
    # Heavy armored brow & blazing white-gold demon eyes
    ey = 15 + body_dy
    for x in range(cx, cx + 8):
        cv.set(x, ey, "h")
        cv.set(x, ey - 1, "l" if x < cx + 3 else "m")
    cv.set(cx + 2, ey, "y")
    cv.set(cx + 3, ey, "w")
    cv.set(cx + 5, ey, "w")
    cv.set(cx + 6, ey, "y")
    # Fanged mandible / molten grill
    for x in range(cx + 1, cx + 7):
        cv.set(x, ey + 2, "I" if x % 2 else "h")
        cv.set(x, ey + 3, "g" if flare >= 1 else "O")

    # Twin thick curved obsidian crown-horns WITH crisp 1px #14101a silhouette over halo & helm!
    horn_pts: set[tuple[int, int]] = set()
    horn_pixels: dict[tuple[int, int], str] = {}
    for side in (-1, 1):
        for t in range(14):
            frac = t / 13.0
            hx = cx + side * int(4 + frac * 8.8)
            hy = 13 + body_dy - int(math.sin(frac * 1.48) * 9.5)
            thick = 3 if t < 7 else (2 if t < 11 else 1)
            for dy_h in range(thick):
                pt = (hx, hy + dy_h)
                horn_pts.add(pt)
                col = "I" if t >= 11 else ("l" if dy_h == 0 and side == -1 else ("m" if dy_h == 0 else "d"))
                if dy_h == thick - 1 and thick > 1:
                    col = "h"
                horn_pixels[pt] = col
            if t == 7:
                bp = (hx + side, hy - 1)
                horn_pts.add(bp)
                horn_pixels[bp] = "m"
            if t in (2, 3) or (flare >= 2 and t in (9, 10)):
                pt = (hx, hy + (1 if thick > 1 else 0))
                horn_pts.add(pt)
                horn_pixels[pt] = "w" if flare >= 2 else "y"
    # Outline horns with #14101a where they cross the bright golden halo
    for hx, hy in horn_pts:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            nb = (hx + dx, hy + dy)
            if nb not in horn_pts and nb not in helm:
                cv.set(nb[0], nb[1], "o")
    for (hx, hy), col in horn_pixels.items():
        cv.set(hx, hy, col)

    # 7. NEAR SPIKED ASYMMETRIC WAR-PAULDRON & NEAR GIANT WAR-CLAW (foreground)
    cv.fill_beveled_poly([
        (cx - 21, 16 + body_dy),
        (cx - 14, 13 + body_dy),
        (cx - 6, 15 + body_dy),
        (cx - 6, 26 + body_dy),
        (cx - 19, 27 + body_dy),
    ], CRIMSON_RAMP, crease_sides="rb")
    for fx, fy in [(cx - 15, 19), (cx - 14, 20), (cx - 13, 21), (cx - 11, 21)]:
        cv.set(fx, fy + body_dy, "w" if flare >= 2 else "y")
        cv.set(fx - 1, fy + body_dy, "O")
    # 3 dramatic sweeping obsidian shoulder spikes curving up-left
    for sx, sy, hgt in [(cx - 19, 16 + body_dy, 6), (cx - 14, 14 + body_dy, 7), (cx - 9, 15 + body_dy, 5)]:
        cv.fill_beveled_poly([(sx - 2, sy + 1), (sx - 3, sy - hgt), (sx + 2, sy + 1)], OBSIDIAN_RAMP)
        cv.set(sx - 3, sy - hgt + 1, "w" if flare >= 2 else "I")

    if arms_raised:
        draw_boss_war_claw(cv, cx - 14, 19 + body_dy, cx - 20, 17 + body_dy, cx - 23, 9 + body_dy, -1, True, flare, raised=True)
    else:
        draw_boss_war_claw(
            cv,
            sx=cx - 14,
            sy=22 + body_dy,
            ex=cx - 17 + near_claw_off[0] // 2,
            ey=30 + body_dy + near_claw_off[1] // 2,
            hx=cx - 18 + near_claw_off[0],
            hy=36 + body_dy + near_claw_off[1],
            claw_dir=-1 if near_claw_off[0] <= 2 else +1,
            is_near=True,
            flare=flare,
            raised=False,
        )

    # 8. FRAME 6 SPARKS & FRAME 7 FIERY NOVA/CLEAVE SURGE FORWARD (staying x <= 62!)
    if flare >= 2 and arms_raised:
        for sx, sy, col in [
            (cx - 25, 5 + body_dy, "w"), (cx - 21, 3 + body_dy, "y"),
            (cx + 24, 6 + body_dy, "w"), (cx + 20, 3 + body_dy, "y"),
            (cx - 1, 2 + body_dy, "w"), (cx - 5, 3 + body_dy, "y"), (cx + 4, 3 + body_dy, "y"),
        ]:
            cv.set(sx, sy, col)

    if nova_unleash:
        # Sweeping fiery nova cleave arc erupting forward (+X) safely within x <= 61
        for ang_deg in range(-56, 58, 3):
            rad = math.radians(ang_deg)
            arc_r = 23.0 - abs(ang_deg) * 0.04
            for dr in (-2.0, -1.0, 0.0, 1.0, 1.8):
                px = int(round(cx + 6 + math.cos(rad) * (arc_r + dr)))
                py = int(round(32 + math.sin(rad) * (arc_r + dr)))
                if 2 <= px <= 61 and 4 <= py <= 58:
                    if abs(dr) < 0.6:
                        cv.set(px, py, "w")
                    elif abs(dr) < 1.4:
                        cv.set(px, py, "y")
                    elif dr > 0:
                        cv.set(px, py, "O")
                    else:
                        cv.set(px, py, "g")
        for sx, sy, col in [(54, 13, "y"), (59, 21, "w"), (60, 31, "w"), (59, 41, "y"), (54, 49, "O")]:
            cv.set(sx, sy, col)

    cv.apply_outer_outline("o")
    return (fname, dur, cv.rows())


BOSS_FRAMES = [
    build_boss_frame("idle_0", 240, body_dy=0, flare=0),
    build_boss_frame("idle_1", 240, body_dy=-1, flare=1, near_claw_off=(0, 1), far_claw_off=(0, -1)),
    build_boss_frame("stride_0", 145, body_dy=1, torso_dx=1, flare=0, front_leg_dx=3, front_leg_dy=0, back_leg_dx=-3, back_leg_dy=-2, near_claw_off=(-4, 1), far_claw_off=(4, -2)),
    build_boss_frame("stride_1", 130, body_dy=-1, torso_dx=0, flare=0, front_leg_dx=1, front_leg_dy=0, back_leg_dx=-1, back_leg_dy=-3, near_claw_off=(-1, 0), far_claw_off=(2, -1)),
    build_boss_frame("stride_2", 145, body_dy=1, torso_dx=1, flare=1, front_leg_dx=-1, front_leg_dy=-2, back_leg_dx=2, back_leg_dy=0, near_claw_off=(5, -1), far_claw_off=(-3, 1)),
    build_boss_frame("stride_3", 130, body_dy=-1, torso_dx=0, flare=0, front_leg_dx=0, front_leg_dy=-3, back_leg_dx=1, back_leg_dy=0, near_claw_off=(2, 0), far_claw_off=(0, 0)),
    build_boss_frame("nova_0", 170, body_dy=1, torso_dx=-1, flare=2, arms_raised=True),
    build_boss_frame("nova_1", 210, body_dy=2, torso_dx=2, flare=2, front_leg_dx=3, back_leg_dx=-2, near_claw_off=(10, -4), far_claw_off=(6, -6), nova_unleash=True),
]


# ===========================================================================
# 4. CONTACT SHEET & ANIMATED GIF PREVIEWS
# ===========================================================================
def nearest_zoom(im: Image.Image, scale: int) -> Image.Image:
    """Exact integer nearest-neighbor zoom without Pillow NEAREST resize bug."""
    w, h = im.size
    data = list(im.getdata())
    out_pixels = []
    for y in range(h):
        row = data[y * w:(y + 1) * w]
        zoomed_row = [px for px in row for _ in range(scale)]
        for _ in range(scale):
            out_pixels.extend(zoomed_row)
    out = Image.new("RGBA", (w * scale, h * scale))
    out.putdata(out_pixels)
    return out


def make_checker(w: int, h: int, light: bool = True, step: int = 12) -> Image.Image:
    c1 = (224, 228, 232, 255) if light else (32, 36, 48, 255)
    c2 = (192, 198, 206, 255) if light else (44, 50, 66, 255)
    im = Image.new("RGBA", (w, h), c1)
    draw = ImageDraw.Draw(im)
    for y in range(0, h, step):
        for x in range(0, w, step):
            if ((x // step) + (y // step)) % 2:
                draw.rectangle((x, y, x + step - 1, y + step - 1), fill=c2)
    return im


def save_clean_gif(frames_rgba: list[Image.Image], durations: list[int], out_path: Path) -> None:
    """Save crisp palette-accurate animated GIF over opaque dark checkerboard."""
    p_frames = [
        im.convert("RGB").convert("P", palette=Image.Palette.ADAPTIVE, colors=256)
        for im in frames_rgba
    ]
    p_frames[0].save(
        out_path,
        save_all=True,
        append_images=p_frames[1:],
        duration=durations,
        loop=0,
        disposal=2,
    )


def export_previews(
    cultist_imgs: list[Image.Image],
    golem_imgs: list[Image.Image],
    boss_imgs: list[Image.Image],
) -> None:
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    scale = 4
    specs = [
        ("ABYSSAL VOID CULTIST (32x32 @ 4x, baseline y=29)", cultist_imgs, CULTIST_FRAMES, 32, 29),
        ("RUNIC GRANITE GOLEM (48x48 @ 4x, baseline y=45)", golem_imgs, GOLEM_FRAMES, 48, 45),
        ("OBSIDIAN MAGMA COLOSSUS BOSS (64x64 @ 4x, baseline y=60)", boss_imgs, BOSS_FRAMES, 64, 60),
    ]

    pad_x = 12
    left_margin = 92
    max_cell = 64 * scale
    total_w = left_margin + 8 * (max_cell + pad_x) + 16
    header_h = 46

    section_heights = [36 + (sz * scale) * 2 + 24 for _, _, _, sz, _ in specs]
    total_h = header_h + sum(section_heights) + 20

    sheet = Image.new("RGBA", (total_w, total_h), (20, 23, 34, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((16, 14), "SPELL STORM MILESTONE 3 - MONSTER & BOSS SUITE (4x NEAREST | LIGHT & DARK CHECKERBOARDS)", fill=(232, 241, 248, 255))

    cur_y = header_h
    for title, imgs, meta, sz, baseline in specs:
        cell = sz * scale
        draw.text((16, cur_y + 4), title, fill=(255, 224, 102, 255))
        draw.text((16, cur_y + 28 + cell // 2), "LIGHT BG", fill=(185, 200, 217, 255))
        draw.text((16, cur_y + 28 + cell + 8 + cell // 2), "DARK BG", fill=(185, 200, 217, 255))

        col_step = cell + pad_x
        for f_idx, im in enumerate(imgs):
            x = left_margin + f_idx * col_step
            fname = meta[f_idx][0]
            draw.text((x, cur_y + 18), f"{f_idx}: {fname}", fill=(143, 211, 255, 255))
            zoomed = nearest_zoom(im, scale)

            for bg_idx, is_light in enumerate((True, False)):
                by = cur_y + 30 + bg_idx * (cell + 6)
                tile = make_checker(cell, cell, light=is_light, step=12)
                tile.alpha_composite(zoomed)
                td = ImageDraw.Draw(tile)
                b_py = baseline * scale
                td.line((0, b_py, 5, b_py), fill=(242, 82, 68, 255))
                td.line((cell - 6, b_py, cell - 1, b_py), fill=(242, 82, 68, 255))
                sheet.paste(tile, (x, by))
        cur_y += 36 + cell * 2 + 24

    sheet.save(PREVIEW_DIR / "monsters_m3_contact_sheet.png")

    gif_configs = [
        ("ranged_cultist", "ABYSSAL VOID CULTIST", cultist_imgs, CULTIST_FRAMES, 6),
        ("tank_golem", "RUNIC GRANITE GOLEM", golem_imgs, GOLEM_FRAMES, 5),
        ("boss_colossus", "OBSIDIAN MAGMA COLOSSUS", boss_imgs, BOSS_FRAMES, 4),
    ]
    seq = [0, 1, 0, 1, 2, 3, 4, 5, 2, 3, 4, 5, 6, 7, 7]
    for slug, label, imgs, meta, gscale in gif_configs:
        sz = imgs[0].width
        cell = sz * gscale
        gw = max(cell + 40, 260)
        gh = cell + 60
        gif_frames = []
        durs = []
        for f_idx in seq:
            bg = make_checker(gw, gh, light=False, step=12)
            gd = ImageDraw.Draw(bg)
            gd.rectangle((0, 0, gw, 26), fill=(20, 23, 34, 255))
            gd.rectangle((0, gh - 26, gw, gh), fill=(20, 23, 34, 255))
            gd.text((10, 7), f"{label} ({gscale}x)", fill=(255, 224, 102, 255))
            gd.text((10, gh - 19), f"FRAME {f_idx}: {meta[f_idx][0].upper()}", fill=(143, 211, 255, 255))
            zoomed = nearest_zoom(imgs[f_idx], gscale)
            bg.alpha_composite(zoomed, ((gw - cell) // 2, 28))
            gif_frames.append(bg)
            durs.append(meta[f_idx][1])
        save_clean_gif(gif_frames, durs, PREVIEW_DIR / f"{slug}_anim.gif")


def main() -> None:
    cultist_imgs = export_monster_assets("ranged_cultist", 32, 32, [16, 29], CULTIST_PALETTE, CULTIST_FRAMES)
    golem_imgs = export_monster_assets("tank_golem", 48, 48, [24, 45], GOLEM_PALETTE, GOLEM_FRAMES)
    boss_imgs = export_monster_assets("boss_colossus", 64, 64, [32, 60], BOSS_PALETTE, BOSS_FRAMES)
    export_previews(cultist_imgs, golem_imgs, boss_imgs)
    print("Built Milestone 3 monster & boss JSON sources, PNGs, contact sheet, and GIFs.")


if __name__ == "__main__":
    main()
