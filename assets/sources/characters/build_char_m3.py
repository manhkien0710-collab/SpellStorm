#!/usr/bin/env python3
"""
Hand-authored Milestone 3 Character & Catalyst Orb pixel-art builder for Spell Storm.

Preserves all Milestone 2 assets untouched and generates:
- assets/sources/characters/player_hurt_death.json (6 frames of 32x32, binary alpha)
- assets/textures/animations/player_hurt_death_sheet.png (192x32, strict binary alpha [0, 255])
- assets/sources/vfx/catalyst_orb.json (6 frames of 16x16, smooth translucent alpha halo)
- assets/textures/vfx/catalyst_orb_sheet.png (96x16 RGBA)
- docs/previews/milestone3/char_m3_contact_sheet.png (6x nearest-neighbor)
- docs/previews/milestone3/char_m3_anim.gif (6x nearest-neighbor showcase)
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
CHAR_SRC_DIR = ROOT / "assets/sources/characters"
VFX_SRC_DIR = ROOT / "assets/sources/vfx"
TEX_ANIM_DIR = ROOT / "assets/textures/animations"
TEX_VFX_DIR = ROOT / "assets/textures/vfx"
PREVIEW_DIR = ROOT / "docs/previews/milestone3"

# Exact master palette from arcane_storm_palette.json (matches build_player_sprites.py)
CHAR_PALETTE = {
    "o": "#14101a",  # outline.deep_void
    "k": "#1e1829",  # outline.indigo_line
    "p": "#2c223d",  # outline.plum_shadow
    "D": "#172047",  # arcane_robe[0] deep shadow
    "S": "#243b7a",  # arcane_robe[1] shadow
    "B": "#3662b8",  # arcane_robe[2] base sapphire
    "L": "#5296e8",  # arcane_robe[3] light
    "H": "#8fd3ff",  # arcane_robe[4] highlight
    "g": "#6b3e12",  # gold_trim[0] deep shadow
    "n": "#9e631b",  # gold_trim[1] shadow
    "G": "#d99726",  # gold_trim[2] base gold
    "Y": "#ffe066",  # gold_trim[3] bright gold
    "W": "#fff7b8",  # gold_trim[4] glint
    "a": "#8f563b",  # skin_beard[0] shadow skin
    "s": "#c9845b",  # skin_beard[1] mid skin
    "f": "#f2b88c",  # skin_beard[2] light skin
    "q": "#b9c8d9",  # skin_beard[3] beard shadow
    "w": "#e8f1f8",  # skin_beard[4] beard white
    "t": "#2b1a14",  # wood_staff[0] dark bark/boot
    "m": "#472b1e",  # wood_staff[1] shadow bark/boot
    "r": "#6e442c",  # wood_staff[2] mid bark/boot
    "u": "#99633f",  # wood_staff[3] light bark/boot
    "c": "#1c698c",  # arcane_crystal[0] deep cyan
    "C": "#33b5e5",  # arcane_crystal[1] cyan
    "E": "#8ff5ff",  # arcane_crystal[2] bright glow cyan / eyes
    "X": "#ffffff",  # arcane_crystal[3] white-hot core / glint
}

HURT_DEATH_META = [
    ("hurt_0", 110, "0: hurt_0 (Hit Recoil)"),
    ("hurt_1", 150, "1: hurt_1 (Stagger)"),
    ("death_0", 140, "2: death_0 (Collapse Start)"),
    ("death_1", 160, "3: death_1 (Kneeling Fold)"),
    ("death_2", 180, "4: death_2 (Arcane Dissolve)"),
    ("death_3", 240, "5: death_3 (Remnants)"),
]

# Ruler (32 cols):
# 0         1         2         3
# 01234567890123456789012345678901
HURT_DEATH_FRAMES = [
    # 0: hurt_0 — Hit-Recoil (head & hat snap back-right, bright flash ramp, clear squint eyes, flared cloak, continuous 1px #14101a outline)
    [
        "................................",  # 00
        "..................ooooo.........",  # 01
        ".................oXHHLBo........",  # 02
        "................oXHLLBBo........",  # 03
        "...............oXHLLBSo.........",  # 04
        "..............oXHLLBBo..........",  # 05
        "....o........oXHLLBBBo..........",  # 06
        "...oXo......oXHLLBBBBSo.........",  # 07
        "..oXEXo....oXHLLBBBBBSo.........",  # 08
        "...oECo....oHLppWWYpBSo.........",  # 09
        "....oo.....oHLppWXWpBSo.........",  # 10
        "...ooo....oHHLLBBBBBBBSo........",  # 11
        "..oruro..oHHLLBppppppBBSSo......",  # 12
        "...omoo.oHLLBppkkkkkkppBSo......",  # 13
        "...oroo..oLkkffssffssko.........",  # 14
        "...omoo...okfkkffkkffko.........",  # 15
        "...orooo.oHHkwwqqqqwkSSo........",  # 16
        "...ofsoooHHLLBWYwwYWLLSSo.......",  # 17
        "....omooHHLLLLLpWWpLLLLSSfo.....",  # 18
        "....orooHHLLLLLpBBpLLLLSSao.....",  # 19
        "....omooHHLLmmnWYWmmLLLLSSo.....",  # 20
        "....orooHHLLLLBBBBLLLLLLSSSo....",  # 21
        "....omooHHLLLLBBBBBLLLLLLSSSo...",  # 22
        "....orooHHLLLLBBBBBBLLLLLLSSSo..",  # 23
        "....omooHHLLLLLBBBBBBLLLLLLSSSo.",  # 24
        "....orooWWHLLLBBBBBBBBLLLLLSSWGo",  # 25
        ".....oo.oopLLLLBBBBBBBBBLLBppoo.",  # 26
        ".........oouroo.......oormoo....",  # 27
        "........oouroo.........oormoo...",  # 28
        "........ooooo...........ooooo...",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 1: hurt_1 — Recovery / Stagger (hunched forward 1px, wide braced boots, normal palette, bright cyan crystal flicker)
    [
        "................................",  # 00
        "................................",  # 01
        "................................",  # 02
        "................oooo............",  # 03
        "...............oHLBSo...........",  # 04
        "....o.X.o.....ooHLBBo...........",  # 05
        "...oXEXEXo...oHLLBSo............",  # 06
        "....oEXXo...oHLBBBSo............",  # 07
        "....oECo...oHLBBBBSSo...........",  # 08
        ".....oo...oHLBBBBBSSo...........",  # 09
        "....ooo...oLLBBBBBSSo...........",  # 10
        "...oruro..oLkpGGYkpSo...........",  # 11
        "....omoo.oHLkpGWGkpSSo..........",  # 12
        "....oroo.oHLBBBBBBBBSSo.........",  # 13
        "....omoooHLBBppppppBBSSo........",  # 14
        "....oroo.oLBpkkEkkEkpBSo........",  # 15
        "....ofsoo.oksawwqqsko.ooo.......",  # 16
        "....omoooLLBkwwqqwkSSo..........",  # 17
        "....orooLLBBGYwwYGSSSo..........",  # 18
        "....omooLBBBBpGGpBBBSSfo........",  # 19
        "....orooLBBBBpSSpBBBSSao........",  # 20
        "....omooLBBmmnGYGmmBBSSo........",  # 21
        "....orooLBBBBSSSSBBBSSSo........",  # 22
        "....omooLLBBBSSBBBBSSSo.........",  # 23
        "....orooLLBBBSSBBBBSSSo.........",  # 24
        "....omooGLLBBSSSSBBSSGo.........",  # 25
        ".....oooopBBBBSSSSBppo..........",  # 26
        "........oouroo....oormoo........",  # 27
        "........oouroo....oormoo........",  # 28
        "........ooooo......ooooo........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 2: death_0 — Death Collapse Start (dropping to knees, hat tilting forward-down, 1px-core staff clattering sideways)
    [
        "................................",  # 00
        "................................",  # 01
        "................................",  # 02
        "................................",  # 03
        "................................",  # 04
        "................................",  # 05
        "................................",  # 06
        "..............oooo..............",  # 07
        ".............oHLBSo.............",  # 08
        "............oHLLBBo.............",  # 09
        "............oHLBBBSo............",  # 10
        "...........oHLBBBBSSo...........",  # 11
        "..........oHLBBBBBSSo...........",  # 12
        "....oo....oLkpGGYkpSSo..........",  # 13
        "...oEXo...oLkpGWGkpSSo..........",  # 14
        "..oECCo..oHLBBBBBBBBSSo.........",  # 15
        "...ooo..oHLBBppppppBBBSSo.......",  # 16
        "...ouo...oLBppkkkkppBBSo........",  # 17
        "..ooro....okkwwqqwkkSSo.........",  # 18
        "...oroo..oLLBGYwwYGSSSo.........",  # 19
        "....orfoooLBBBpGGpBBBSSfo.......",  # 20
        ".....omoo.LBBBpSSpBBBSSao.......",  # 21
        "......orooLBBmmGYGmmBBSSo.......",  # 22
        ".......omooLBBBSSSSBBBSSSo......",  # 23
        "........orooLLBBSSBBBBSSSo......",  # 24
        ".........ooLLBBBSSBBBBSSSo......",  # 25
        "........oGLLBBBSSSSBBSSGo.......",  # 26
        ".........oopBBBBSSSSBppo........",  # 27
        ".........oouroo...oormoo........",  # 28
        ".........ooooo.....ooooo........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 3: death_1 — Kneeling Fold (recognizable slumped wizard hat with wide overhanging brim, hunched shoulders & pooled cloak at y=27..29)
    [
        "................................",  # 00
        "................................",  # 01
        "................................",  # 02
        "................................",  # 03
        "................................",  # 04
        "................................",  # 05
        "................................",  # 06
        "................................",  # 07
        "................................",  # 08
        "................................",  # 09
        "................................",  # 10
        "................................",  # 11
        "................................",  # 12
        ".............oooo...............",  # 13
        "...........ooHLBSo..............",  # 14
        "..........oHLLBBBSo.............",  # 15
        "..........oHLBBBBSSo............",  # 16
        "...........oHLBBBBSSo...........",  # 17
        "..........oHLppGGYppSo..........",  # 18
        ".........oHLBppGWGppBSo.........",  # 19
        "........oHHLLBBBBBBBBSSo........",  # 20
        ".......oHLLBppppppppBBSSo.......",  # 21
        "........oooLBSBBBBSSSSBSoo......",  # 22
        "..oo....oLLBSBBBBSSSSBBBSSo.....",  # 23
        ".oECo..oLLBBSBBBBSSSSBBBBSSo....",  # 24
        ".oCCo.oLLBBSSBBBBSSSSBBBBSSo....",  # 25
        "..ooooooruBBSSBBBBBBBBBBBSSGo...",  # 26
        "...oouroooGLLBBBBBBBBBBSSGppo...",  # 27
        "....oooooomroSSSSSSSSSSormoo....",  # 28
        "...........oooooooooooooooo.....",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 4: death_2 — Arcane Dissolution (hat & hunched mantle actively breaking apart upward into sapphire/cyan wisps & motes; staff flat on ground)
    [
        "................................",  # 00
        "................................",  # 01
        "................................",  # 02
        "................................",  # 03
        "................................",  # 04
        "................c...............",  # 05
        "...............cXo..............",  # 06
        "..........o.....E...............",  # 07
        ".........oHo.........c..........",  # 08
        "..........o....oEo..cEo.........",  # 09
        ".............o..o....c..........",  # 10
        "............oCEo................",  # 11
        "........o....oo......o..........",  # 12
        ".......oHo....oHo...oLo.........",  # 13
        "........o....oHLBo...o..........",  # 14
        "......o.....oHLLSo..oCo.........",  # 15
        ".....oLo.....oLBo....o..........",  # 16
        "......o.....oHLBSo..............",  # 17
        "..........ooLppGpo......o.......",  # 18
        ".........oHLLpWGpBSo...oHo......",  # 19
        "........oHHLLBBBBSSo....o.......",  # 20
        "........ooLBppppppBSo...........",  # 21
        ".........oLBSBBBBSSSoo..........",  # 22
        "........oLLBSBBBBSSSBSso........",  # 23
        "..o....oLLBBSBBBBSSSSBBSo.......",  # 24
        ".oXo..oLLBBSSBBBBSSSSBBSSo......",  # 25
        "oEXCo.oGLBBBSSBBBBBBBBSSGo......",  # 26
        "oECooorrmuurrmuurrmuooSSGpo.....",  # 27
        ".oooooorrmuurrmuurmoooormo......",  # 28
        ".....ooooooooooooooo.ooooo......",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 5: death_3 — Dispersing Arcane Remnants (final ascending diamond motes & fallen crystal glint at y=27..29)
    [
        "................................",  # 00
        "................................",  # 01
        "................................",  # 02
        "................S...............",  # 03
        "...............SLo..............",  # 04
        "................S...............",  # 05
        "...........c.........c..........",  # 06
        "..........cEo.......cXo.........",  # 07
        "...........c.........c..........",  # 08
        "..............c.................",  # 09
        ".............cEc.......S........",  # 10
        "............cEXEc.....SHo.......",  # 11
        ".............cEc.......S........",  # 12
        "..............c.................",  # 13
        ".......c.............c..........",  # 14
        "......cHo...........cEo.........",  # 15
        ".......c.............c..........",  # 16
        ".............S..................",  # 17
        "............SLEo................",  # 18
        ".............S.......S..........",  # 19
        "....................SLo.........",  # 20
        "...........c.........S..........",  # 21
        "..........cEo...................",  # 22
        "...........c.........o..........",  # 23
        "......o.............oHo.........",  # 24
        ".....oXo.............o..........",  # 25
        "....oXEXo....ooo................",  # 26
        "....oECXCo..oGYGo.....o.........",  # 27
        ".....oCCooorrmmmo....oSo........",  # 28
        "......oooooooooo......o.........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
]

# Catalyst Orb color roles (neutral white-hot / cool silver-white for clean SpriteBatch tinting)
ORB_PALETTE_CORE = {
    "X": (255, 255, 255, 255),  # #ffffff white-hot core
    "W": (242, 246, 255, 255),  # #f2f6ff cool silver-white highlight
    "S": (216, 228, 255, 255),  # #d8e4ff silver-white mid/bevel
    "B": (192, 208, 242, 255),  # #c0d0f2 subtle crystal shadow bevel
}

# Hand-authored 16x16 core crystal frames (0..5 crisp faceted diamond -> octagram -> 45-deg star -> diamond cycle)
# Keys here are opaque crystal pixels; halo ring, orbiting motes & translucent aura are layered deterministically.
ORB_CRYSTAL_GRIDS = [
    # 0: 0 deg upright faceted diamond + crisp cardinal star rays
    [
        "................",  # 00
        "................",  # 01
        "................",  # 02
        "................",  # 03
        "........W.......",  # 04
        "........X.......",  # 05
        ".......XWX......",  # 06
        "......XXWWS.....",  # 07
        "....WXWXWWBSW...",  # 08
        "......SWWSB.....",  # 09
        ".......SBS......",  # 10
        "........S.......",  # 11
        "........S.......",  # 12
        "................",  # 13
        "................",  # 14
        "................",  # 15
    ],
    # 1: cardinal rays retract, crisp faceted diamond begins octagram glint
    [
        "................",  # 00
        "................",  # 01
        "................",  # 02
        "................",  # 03
        "................",  # 04
        "........W.......",  # 05
        ".......XWX......",  # 06
        "......XXWWS.....",  # 07
        ".....WXWWXBS....",  # 08
        "......SWWSB.....",  # 09
        ".......SBS......",  # 10
        "........S.......",  # 11
        "................",  # 12
        "................",  # 13
        "................",  # 14
        "................",  # 15
    ],
    # 2: 8-point faceted octagram transition (cardinal + emerging diagonal vertices)
    [
        "................",  # 00
        "................",  # 01
        "................",  # 02
        "................",  # 03
        "................",  # 04
        "........W.......",  # 05
        "......WXWXS.....",  # 06
        "......XXWWS.....",  # 07
        ".....WXWWXBS....",  # 08
        "......SWWSB.....",  # 09
        "......SWBSB.....",  # 10
        "........S.......",  # 11
        "................",  # 12
        "................",  # 13
        "................",  # 14
        "................",  # 15
    ],
    # 3: 45 deg rotated faceted square crystal with soft diagonal star sparkle (no boxy corner ears)
    [
        "................",  # 00
        "................",  # 01
        "................",  # 02
        "................",  # 03
        "................",  # 04
        "................",  # 05
        "......XXWWX.....",  # 06
        "......XWXWS.....",  # 07
        "......WWXWB.....",  # 08
        "......SWSSB.....",  # 09
        "......SSBSB.....",  # 10
        "................",  # 11
        "................",  # 12
        "................",  # 13
        "................",  # 14
        "................",  # 15
    ],
    # 4: 8-point octagram transition returning (rotated specular gleam)
    [
        "................",  # 00
        "................",  # 01
        "................",  # 02
        "................",  # 03
        "................",  # 04
        "........W.......",  # 05
        "......SXWXW.....",  # 06
        "......WWXXS.....",  # 07
        ".....SWXWWBW....",  # 08
        "......SWWSB.....",  # 09
        "......SBSWS.....",  # 10
        "........S.......",  # 11
        "................",  # 12
        "................",  # 13
        "................",  # 14
        "................",  # 15
    ],
    # 5: upright faceted diamond with side-facet gleam leading back to f0
    [
        "................",  # 00
        "................",  # 01
        "................",  # 02
        "................",  # 03
        "................",  # 04
        "........X.......",  # 05
        ".......XXW......",  # 06
        "......XWWWS.....",  # 07
        ".....WWXXWBS....",  # 08
        "......SWSSB.....",  # 09
        ".......SBS......",  # 10
        "........S.......",  # 11
        "................",  # 12
        "................",  # 13
        "................",  # 14
        "................",  # 15
    ],
]

# Orbiting rune mote positions (6 frames, smooth clockwise circle at r~6.0 around (8,8), 180 deg apart)
# Each tuple: ((x1, y1), (tx1, ty1), (x2, y2), (tx2, ty2)) where tx,ty is 1px trailing spark
MOTE_ORBIT = [
    ((14, 6), (13, 4), (2, 10), (3, 12)),
    ((12, 13), (14, 11), (4, 3), (2, 5)),
    ((5, 14), (8, 14), (11, 2), (8, 2)),
    ((2, 10), (3, 12), (14, 6), (13, 4)),
    ((4, 3), (2, 5), (12, 13), (14, 11)),
    ((11, 2), (8, 2), (5, 14), (8, 14)),
]

# Pulsing halo ring radii and intensity across frames 0..5 (kept inside r=4.8 so it never flattens at 16x16 edges)
RING_PARAMS = [
    (4.2, 0.56),  # f0: tight bright ring
    (4.5, 0.50),  # f1: expanding
    (4.8, 0.44),  # f2: peak breath
    (4.9, 0.52),  # f3: octagram pulse peak
    (4.6, 0.48),  # f4: contracting
    (4.3, 0.52),  # f5: returning toward f0
]


def hex_to_rgba(h: str) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    if len(h) == 6:
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4, 6))


def rgba_to_hex(col: tuple[int, int, int, int]) -> str:
    r, g, b, a = col
    if a == 255:
        return f"#{r:02x}{g:02x}{b:02x}"
    return f"#{r:02x}{g:02x}{b:02x}{a:02x}"


def validate_char_grids() -> None:
    assert len(HURT_DEATH_FRAMES) == 6, "Must have 6 frames for player_hurt_death"
    for idx, rows in enumerate(HURT_DEATH_FRAMES):
        assert len(rows) == 32, f"frame {idx} must have 32 rows"
        for r_idx, row in enumerate(rows):
            assert len(row) == 32, f"frame {idx} row {r_idx} must have 32 cols, got {len(row)}"
            unknown = set(row) - set(CHAR_PALETTE.keys()) - {"."}
            assert not unknown, f"frame {idx} row {r_idx} unknown keys: {unknown}"


def render_char_frames() -> list[Image.Image]:
    rgba_map = {k: hex_to_rgba(v) for k, v in CHAR_PALETTE.items()}
    rgba_map["."] = (0, 0, 0, 0)
    frames: list[Image.Image] = []
    for rows in HURT_DEATH_FRAMES:
        im = Image.new("RGBA", (32, 32))
        im.putdata([rgba_map[ch] for row in rows for ch in row])
        frames.append(im)
    return frames


def build_catalyst_orb_frame(idx: int) -> Image.Image:
    """Build one 16x16 neutral white/silver-white catalyst orb frame with quantized smooth aura."""
    rows = ORB_CRYSTAL_GRIDS[idx]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows)

    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()

    # 1) Subtle radial outer aura + pulsing halo ring centered at (8, 8)
    ring_r, ring_strength = RING_PARAMS[idx]
    cx, cy = 8.0, 8.0

    # Quantized clean RGBA palette colors for aura/halo so catalyst_orb.json stays concise (~12 keys)
    for y in range(16):
        for x in range(16):
            dx = x - cx
            dy = y - cy
            dist = math.hypot(dx, dy)
            if dist > 7.35:
                continue  # keep edge clear to prevent square box clipping

            # Core radial aura glow
            core_glow = max(0.0, 1.0 - dist / 6.8) ** 1.85 * 0.42

            # Pulsing halo ring (crisp 1px-feel pixel ring with soft falloff)
            ring_delta = abs(dist - ring_r)
            ring_glow = max(0.0, 1.0 - ring_delta / 1.15) ** 1.45 * ring_strength

            alpha_f = max(core_glow, min(0.68, core_glow * 0.55 + ring_glow))
            # Quantize to clean discrete tiers for crisp pixel-art aesthetic and compact JSON palette
            if alpha_f >= 0.46:
                px[x, y] = (242, 246, 255, 144)  # #f2f6ff90
            elif alpha_f >= 0.31:
                px[x, y] = (242, 246, 255, 104)  # #f2f6ff68
            elif alpha_f >= 0.19:
                px[x, y] = (216, 228, 255, 72)   # #d8e4ff48
            elif alpha_f >= 0.10:
                px[x, y] = (216, 228, 255, 40)   # #d8e4ff28
            elif alpha_f >= 0.045:
                px[x, y] = (255, 255, 255, 20)   # #ffffff14

    # 2) Two orbiting rune motes + 1px motion trails (and subtle diagonal sparkle tips on octagram frame 3)
    if idx == 3:
        for sx, sy in ((5, 5), (11, 5), (5, 11), (11, 11)):
            px[sx, sy] = (242, 246, 255, 184)  # #f2f6ffb8 translucent star glint
    (m1x, m1y), (t1x, t1y), (m2x, m2y), (t2x, t2y) = MOTE_ORBIT[idx]
    px[t1x, t1y] = (216, 228, 255, 184)  # #d8e4ffb8
    px[t2x, t2y] = (216, 228, 255, 144)  # #d8e4ff90
    px[m1x, m1y] = (255, 255, 255, 255)  # #ffffff
    px[m2x, m2y] = (242, 246, 255, 240)  # #f2f6fff0

    # 3) Faceted rotating diamond/octagram crystal core (crisp opaque pixels on top)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = ORB_PALETTE_CORE[ch]

    return im


def nearest_scale(im: Image.Image, scale: int) -> Image.Image:
    """Exact integer nearest-neighbor scale without relying on Pillow resize backends."""
    if scale == 1:
        return im.copy()
    rgba_im = im.convert("RGBA")
    w, h = rgba_im.size
    raw = rgba_im.tobytes()
    stride = w * 4
    rows_out: list[bytes] = []
    for y in range(h):
        row = raw[y * stride:(y + 1) * stride]
        scaled_row = b"".join(row[x * 4:(x + 1) * 4] * scale for x in range(w))
        rows_out.extend([scaled_row] * scale)
    return Image.frombytes("RGBA", (w * scale, h * scale), b"".join(rows_out))


def iter_rgba_pixels(im: Image.Image) -> list[tuple[int, int, int, int]]:
    raw = im.convert("RGBA").tobytes()
    return [(raw[i], raw[i + 1], raw[i + 2], raw[i + 3]) for i in range(0, len(raw), 4)]


def write_char_json() -> None:
    CHAR_SRC_DIR.mkdir(parents=True, exist_ok=True)
    doc = {
        "version": 1,
        "name": "player_hurt_death",
        "canvas": [32, 32],
        "pivot": [16, 29],
        "palette": CHAR_PALETTE,
        "frames": [
            {
                "name": HURT_DEATH_META[i][0],
                "duration_ms": HURT_DEATH_META[i][1],
                "rows": HURT_DEATH_FRAMES[i],
            }
            for i in range(6)
        ],
    }
    out = CHAR_SRC_DIR / "player_hurt_death.json"
    out.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")


def write_orb_json(orb_frames: list[Image.Image]) -> None:
    VFX_SRC_DIR.mkdir(parents=True, exist_ok=True)
    # Build deterministic key mapping matching pixel_art.py schema
    preferred_keys = [
        ((255, 255, 255, 255), "X"),
        ((242, 246, 255, 255), "W"),
        ((216, 228, 255, 255), "S"),
        ((192, 208, 242, 255), "B"),
        ((242, 246, 255, 240), "m"),
        ((242, 246, 255, 184), "v"),
        ((216, 228, 255, 184), "t"),
        ((242, 246, 255, 144), "r"),
        ((216, 228, 255, 144), "u"),
        ((242, 246, 255, 104), "h"),
        ((216, 228, 255, 72), "a"),
        ((216, 228, 255, 40), "g"),
        ((255, 255, 255, 20), "f"),
    ]
    used_colors = set()
    for im in orb_frames:
        for p in iter_rgba_pixels(im):
            if p[3] > 0:
                used_colors.add(p)

    col_to_key: dict[tuple[int, int, int, int], str] = {}
    fallback_keys = iter("1234567890abcdefghijklmnopqrstuvwxyz")
    for col, key in preferred_keys:
        if col in used_colors:
            col_to_key[col] = key
    for col in sorted(used_colors):
        if col not in col_to_key:
            k = next(fallback_keys)
            while k in col_to_key.values():
                k = next(fallback_keys)
            col_to_key[col] = k

    palette_json = {k: rgba_to_hex(col) for col, k in col_to_key.items()}

    json_frames = []
    for i, im in enumerate(orb_frames):
        px = im.load()
        rows = [
            "".join("." if px[x, y][3] == 0 else col_to_key[px[x, y]] for x in range(16))
            for y in range(16)
        ]
        json_frames.append(
            {
                "name": f"orb_{i}",
                "duration_ms": 95,
                "rows": rows,
            }
        )

    doc = {
        "version": 1,
        "name": "catalyst_orb",
        "canvas": [16, 16],
        "pivot": [8, 8],
        "palette": palette_json,
        "frames": json_frames,
    }
    out = VFX_SRC_DIR / "catalyst_orb.json"
    out.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")


def assemble_strip(frames: list[Image.Image]) -> Image.Image:
    w, h = frames[0].size
    strip = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, im in enumerate(frames):
        strip.paste(im, (i * w, 0))
    return strip


def tint_image(im: Image.Image, rgb: tuple[int, int, int]) -> Image.Image:
    """Exact simulation of LibGDX SpriteBatch.setColor(r, g, b, 1) multiplication."""
    out = Image.new("RGBA", im.size)
    tr, tg, tb = rgb
    src = iter_rgba_pixels(im)
    out.putdata([
        (
            (r * tr) // 255,
            (g * tg) // 255,
            (b * tb) // 255,
            a,
        )
        for (r, g, b, a) in src
    ])
    return out


def make_checker(w: int, h: int, light: bool = True, step: int = 12) -> Image.Image:
    c1 = (224, 228, 232, 255) if light else (32, 36, 48, 255)
    c2 = (192, 198, 206, 255) if light else (44, 50, 66, 255)
    im = Image.new("RGBA", (w, h), c1)
    draw = ImageDraw.Draw(im)
    for y in range(0, h, step):
        for x in range(0, w, step):
            if ((x // step) + (y // step)) % 2:
                draw.rectangle((x, y, min(x + step - 1, w - 1), min(y + step - 1, h - 1)), fill=c2)
    return im


def make_arena_slate(w: int, h: int) -> Image.Image:
    im = Image.new("RGBA", (w, h), (26, 30, 40, 255))
    draw = ImageDraw.Draw(im)
    for gx in range(24, w, 24):
        draw.line((gx, 0, gx, h - 1), fill=(35, 41, 54, 255))
    for gy in range(24, h, 24):
        draw.line((0, gy, w - 1, gy), fill=(35, 41, 54, 255))
    return im


def load_reference_player_down() -> Image.Image | None:
    ref_path = CHAR_SRC_DIR / "player_down.json"
    if not ref_path.exists():
        return None
    doc = json.loads(ref_path.read_text(encoding="utf-8"))
    pal = {k: hex_to_rgba(v) for k, v in doc["palette"].items()}
    pal["."] = (0, 0, 0, 0)
    rows = doc["frames"][0]["rows"]
    im = Image.new("RGBA", (32, 32))
    im.putdata([pal[ch] for row in rows for ch in row])
    return im


ELEMENT_TINTS = [
    ("NEUTRAL", None),
    ("FIRE", (255, 110, 30)),
    ("ICE", (100, 210, 255)),
    ("LIGHTNING", (255, 236, 78)),
    ("WIND", (125, 255, 185)),
    ("EARTH", (196, 138, 82)),
    ("ARCANE", (180, 95, 255)),
]


def export_contact_sheet(
    char_frames: list[Image.Image],
    orb_frames: list[Image.Image],
    ref_idle: Image.Image | None,
) -> Image.Image:
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    font = ImageFont.load_default()
    scale = 6

    char_cell = 32 * scale  # 192
    orb_cell = 16 * scale   # 96
    pad_x = 12
    left_margin = 120
    header_h = 56

    # Section 1: Player Hurt & Death (7 cols if ref included, else 6)
    char_items = ([("REF: idle_0", ref_idle)] if ref_idle else []) + [
        (HURT_DEATH_META[i][2], char_frames[i]) for i in range(6)
    ]

    section1_w = left_margin + len(char_items) * (char_cell + pad_x) + 20
    section2_w = left_margin + 6 * (orb_cell + pad_x) + 280
    total_w = max(section1_w, section2_w)

    sec1_y = header_h
    sec1_h = 40 + char_cell * 2 + 12

    sec2_y = sec1_y + sec1_h + 28
    # Show neutral on Light & Dark checkerboards + 6 tinted element rows on Dark Arena Slate
    orb_row_h = orb_cell + 14
    sec2_h = 30 + len(ELEMENT_TINTS) * (orb_cell + 18) + (orb_cell + 18)
    total_h = sec2_y + sec2_h + 20

    sheet = Image.new("RGBA", (total_w, total_h), (18, 21, 30, 255))
    draw = ImageDraw.Draw(sheet)

    draw.text((16, 14), "SPELL STORM MILESTONE 3 - CHARACTER & CATALYST ORB SUITE (6x NEAREST)", fill=(255, 240, 180, 255), font=font)
    draw.text((16, 32), "player_hurt_death_sheet.png (192x32, binary alpha [0,255]) | catalyst_orb_sheet.png (96x16, smooth aura, tint-ready)", fill=(165, 195, 230, 255), font=font)

    # Section 1: Player Hurt & Death
    draw.text((16, sec1_y), "1. STORM ARCHMAGE: HURT (0..1) -> DEATH DISSOLUTION (2..5) | y=29 BASELINE", fill=(143, 211, 255, 255), font=font)
    row_top = sec1_y + 38
    draw.text((16, row_top + char_cell // 2 - 6), "LIGHT BG", fill=(185, 200, 217, 255), font=font)
    draw.text((16, row_top + char_cell + 8 + char_cell // 2 - 6), "DARK BG", fill=(185, 200, 217, 255), font=font)

    for idx, (label, im) in enumerate(char_items):
        x = left_margin + idx * (char_cell + pad_x)
        title_col = (143, 211, 255, 255) if label.startswith("REF") else (255, 224, 102, 255)
        draw.text((x, row_top - 16), label, fill=title_col, font=font)
        up = nearest_scale(im, scale)

        light_bg = make_checker(char_cell, char_cell, light=True)
        light_bg.alpha_composite(up)
        # Draw y=29 baseline guide marker at left/right border ticks
        d_l = ImageDraw.Draw(light_bg)
        d_l.line((0, 29 * scale + scale - 1, 8, 29 * scale + scale - 1), fill=(255, 90, 90, 200))
        d_l.line((char_cell - 9, 29 * scale + scale - 1, char_cell - 1, 29 * scale + scale - 1), fill=(255, 90, 90, 200))
        sheet.paste(light_bg, (x, row_top))

        dark_bg = make_checker(char_cell, char_cell, light=False)
        dark_bg.alpha_composite(up)
        d_d = ImageDraw.Draw(dark_bg)
        d_d.line((0, 29 * scale + scale - 1, 8, 29 * scale + scale - 1), fill=(255, 90, 90, 200))
        d_d.line((char_cell - 9, 29 * scale + scale - 1, char_cell - 1, 29 * scale + scale - 1), fill=(255, 90, 90, 200))
        sheet.paste(dark_bg, (x, row_top + char_cell + 8))

    # Section 2: Catalyst Orb Neutral + Tinted Elements
    draw.text((16, sec2_y), "2. ARCANE CATALYST ORB (96x16, 6x6=96px CELLS) — NEUTRAL LIGHT/DARK & SPRITEBATCH ELEMENT TINTS", fill=(143, 211, 255, 255), font=font)
    cur_y = sec2_y + 24

    for f_idx in range(6):
        x = left_margin + f_idx * (orb_cell + pad_x)
        draw.text((x + 28, cur_y), f"f{f_idx}", fill=(255, 224, 102, 255), font=font)
    cur_y += 16

    # Neutral on Light Checkerboard
    draw.text((16, cur_y + orb_cell // 2 - 6), "NEUTRAL LIGHT", fill=(185, 200, 217, 255), font=font)
    for f_idx, fr in enumerate(orb_frames):
        x = left_margin + f_idx * (orb_cell + pad_x)
        bg = make_checker(orb_cell, orb_cell, light=True, step=12)
        bg.alpha_composite(nearest_scale(fr, scale))
        sheet.paste(bg, (x, cur_y))
    cur_y += orb_cell + 12

    # Neutral on Dark Checkerboard
    draw.text((16, cur_y + orb_cell // 2 - 6), "NEUTRAL DARK", fill=(185, 200, 217, 255), font=font)
    for f_idx, fr in enumerate(orb_frames):
        x = left_margin + f_idx * (orb_cell + pad_x)
        bg = make_checker(orb_cell, orb_cell, light=False, step=12)
        bg.alpha_composite(nearest_scale(fr, scale))
        sheet.paste(bg, (x, cur_y))
    cur_y += orb_cell + 16

    # Tinted samples on Dark Arena Slate (2 columns of 3 elements? No, single clean stack, or 2 side-by-side groups!)
    # Putting 2 side-by-side groups of 3 elements keeps aspect ratio super compact & crisp!
    col2_offset = left_margin + 6 * (orb_cell + pad_x) + 120
    # Wait: let's check total_w so 2 groups fit neatly inside section1_w!
    # section1_w = 120 + 7 * 204 + 20 = 1568!
    # Group 1 ends at 120 + 6 * 108 = 768.
    # Group 2 starts at 900, ends at 900 + 6 * 108 = 1548 <= 1568! PERFECT!
    tints_only = ELEMENT_TINTS[1:]  # 6 elements
    left_tints = tints_only[:3]
    right_tints = tints_only[3:]

    # Recompute exact canvas crop so there is no dead space below
    needed_h = cur_y + 3 * (orb_cell + 14) + 20
    final_sheet = Image.new("RGBA", (total_w, needed_h), (18, 21, 30, 255))
    final_sheet.paste(sheet.crop((0, 0, total_w, min(sheet.height, needed_h))), (0, 0))
    draw = ImageDraw.Draw(final_sheet)

    for row_i in range(3):
        ry = cur_y + row_i * (orb_cell + 14)
        for grp_idx, (label, rgb) in enumerate((left_tints[row_i], right_tints[row_i])):
            gx_label = 16 if grp_idx == 0 else (col2_offset - 104)
            gx_start = left_margin if grp_idx == 0 else col2_offset
            draw.text((gx_label, ry + orb_cell // 2 - 10), label, fill=rgb + (255,), font=font)
            draw.rectangle((gx_label, ry + orb_cell // 2 + 4, gx_label + 16, ry + orb_cell // 2 + 16), fill=rgb + (255,), outline=(220, 230, 245, 255))
            for f_idx, fr in enumerate(orb_frames):
                x = gx_start + f_idx * (orb_cell + pad_x)
                tinted = tint_image(fr, rgb)
                bg = make_arena_slate(orb_cell, orb_cell)
                bg.alpha_composite(nearest_scale(tinted, scale))
                final_sheet.paste(bg, (x, ry))

    out_path = PREVIEW_DIR / "char_m3_contact_sheet.png"
    final_sheet.save(out_path)
    return final_sheet


def export_anim_gif(
    char_frames: list[Image.Image],
    orb_frames: list[Image.Image],
    ref_idle: Image.Image | None,
) -> None:
    """Create 6x nearest-neighbor animated GIF showcasing Hurt->Death and orbiting tinted Catalyst Orbs."""
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    scale = 6
    # Native stage: 120x52 (left stage 0..56 for Hurt->Death, right stage 60..120 for Orbiting Orbs)
    stage_w, stage_h = 120, 52
    font = ImageFont.load_default()

    # Sequence of native steps:
    # Left shows: (ref_idle if any) x3 -> hurt_0 -> hurt_1 -> hurt_0 -> hurt_1 -> death_0 -> death_1 -> death_2 -> death_3 x3
    seq: list[tuple[int, str, Image.Image]] = []
    if ref_idle is not None:
        seq.extend([
            (200, "IDLE", ref_idle),
            (200, "IDLE", ref_idle),
        ])
    seq.extend([
        (110, "HURT 0: RECOIL", char_frames[0]),
        (150, "HURT 1: STAGGER", char_frames[1]),
        (150, "DEATH 0: COLLAPSE", char_frames[2]),
        (180, "DEATH 1: FOLD", char_frames[3]),
        (200, "DEATH 2: DISSOLVE", char_frames[4]),
        (260, "DEATH 3: REMNANTS", char_frames[5]),
        (360, "DEATH 3: REMNANTS", char_frames[5]),
    ])

    # Expand into smooth 95ms ticks for orb orbit while stepping left pose according to cumulative duration
    orbit_tints = [
        ("FIRE", (255, 110, 30)),
        ("ICE", (100, 210, 255)),
        ("LIGHTNING", (255, 236, 78)),
        ("WIND", (125, 255, 185)),
        ("EARTH", (196, 138, 82)),
        ("ARCANE", (180, 95, 255)),
    ]

    gif_frames: list[Image.Image] = []
    durations: list[int] = []

    # 24 smooth ticks at 95ms (2.28s total loop): cycles through all 6 elements smoothly & completes Hurt->Death
    total_ticks = 24
    # Map tick 0..23 cleanly to Hurt->Death beats
    pose_schedule = (
        [("IDLE", ref_idle)] * 3 if ref_idle is not None else []
    ) + [
        ("HURT 0: RECOIL", char_frames[0]),
        ("HURT 0: RECOIL", char_frames[0]),
        ("HURT 1: STAGGER", char_frames[1]),
        ("HURT 1: STAGGER", char_frames[1]),
        ("DEATH 0: COLLAPSE", char_frames[2]),
        ("DEATH 0: COLLAPSE", char_frames[2]),
        ("DEATH 1: FOLD", char_frames[3]),
        ("DEATH 1: FOLD", char_frames[3]),
        ("DEATH 2: DISSOLVE", char_frames[4]),
        ("DEATH 2: DISSOLVE", char_frames[4]),
        ("DEATH 2: DISSOLVE", char_frames[4]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
        ("DEATH 3: REMNANTS", char_frames[5]),
    ]
    pose_schedule = pose_schedule[:total_ticks]

    for tick in range(total_ticks):
        canvas_native = Image.new("RGBA", (stage_w, stage_h), (24, 28, 38, 255))
        d_n = ImageDraw.Draw(canvas_native)
        for gx in range(0, stage_w, 16):
            d_n.line((gx, 0, gx, stage_h - 1), fill=(31, 37, 50, 255))
        for gy in range(0, stage_h, 16):
            d_n.line((0, gy, stage_w - 1, gy), fill=(31, 37, 50, 255))
        d_n.line((58, 6, 58, stage_h - 6), fill=(48, 56, 74, 255))

        # Ground shadows (left wizard at x=28, y=40; right wizard at x=90, y=40)
        d_n.ellipse((20, 38, 36, 43), fill=(16, 19, 27, 255))
        d_n.ellipse((82, 38, 98, 43), fill=(16, 19, 27, 255))

        label_left, char_im = pose_schedule[tick]
        canvas_native.alpha_composite(char_im, (12, 11))

        # Right side: intact wizard with orbiting tinted catalyst orb
        if ref_idle is not None:
            canvas_native.alpha_composite(ref_idle, (74, 11))

        # Active element cycles every 4 ticks across the 6 elements
        elem_name, elem_rgb = orbit_tints[(tick // 4) % len(orbit_tints)]
        orb_angle = (tick / total_ticks) * math.tau * 2.0
        ox = int(round(90 + math.cos(orb_angle) * 16.0)) - 8
        oy = int(round(27 + math.sin(orb_angle) * 11.0)) - 8
        orb_sprite = tint_image(orb_frames[tick % 6], elem_rgb)
        canvas_native.alpha_composite(orb_sprite, (ox, oy))

        up = nearest_scale(canvas_native, scale)
        d_up = ImageDraw.Draw(up)
        d_up.rectangle((0, 0, stage_w * scale, 26), fill=(16, 19, 28, 255))
        d_up.text((14, 7), f"HURT & DEATH: {label_left}", fill=(255, 224, 102, 255), font=font)
        d_up.text((58 * scale + 14, 7), f"CATALYST ORB [{elem_name}]", fill=elem_rgb + (255,), font=font)

        gif_frames.append(up.convert("P", palette=Image.Palette.ADAPTIVE, colors=255))
        durations.append(95)

    out_gif = PREVIEW_DIR / "char_m3_anim.gif"
    gif_frames[0].save(
        out_gif,
        save_all=True,
        append_images=gif_frames[1:],
        duration=durations,
        loop=0,
        disposal=2,
    )


def main() -> None:
    validate_char_grids()
    char_frames = render_char_frames()
    orb_frames = [build_catalyst_orb_frame(i) for i in range(6)]

    write_char_json()
    write_orb_json(orb_frames)

    TEX_ANIM_DIR.mkdir(parents=True, exist_ok=True)
    TEX_VFX_DIR.mkdir(parents=True, exist_ok=True)

    char_sheet = assemble_strip(char_frames)
    char_sheet_path = TEX_ANIM_DIR / "player_hurt_death_sheet.png"
    char_sheet.save(char_sheet_path)

    orb_sheet = assemble_strip(orb_frames)
    orb_sheet_path = TEX_VFX_DIR / "catalyst_orb_sheet.png"
    orb_sheet.save(orb_sheet_path)

    ref_idle = load_reference_player_down()
    export_contact_sheet(char_frames, orb_frames, ref_idle)
    export_anim_gif(char_frames, orb_frames, ref_idle)

    print(f"OK {char_sheet_path}: {char_sheet.size[0]}x{char_sheet.size[1]} {char_sheet.mode}")
    print(f"OK {orb_sheet_path}: {orb_sheet.size[0]}x{orb_sheet.size[1]} {orb_sheet.mode}")


if __name__ == "__main__":
    main()
