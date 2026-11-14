#!/usr/bin/env python3
"""
Hand-authored 16x16 pixel-art builder for Spell Storm Milestone 3 Pickups & UI Icons.

Produces (strict binary alpha [0, 255], crisp #14101a outlines, 135 deg top-left lighting):
- Canonical 16x16 XP gems (Frame 0 of each tier):
  * assets/textures/pickups/gem_cyan.png
  * assets/textures/pickups/gem_emerald.png
  * assets/textures/pickups/gem_gold.png
- Extended 192x16 (12x 16x16) animated XP gem sheet:
  * assets/textures/animations/gems_anim_sheet.png
- HUD Elemental Medallions (96x16 = 6x 16x16):
  * assets/textures/ui/hud_elements_sheet.png
- Power-Up Icons (48x16 = 3x 16x16):
  * assets/textures/ui/powerup_icons_sheet.png
- Labeled 6x nearest-neighbor contact sheet (dark arena slate + light checkerboard):
  * docs/previews/milestone3/pickups_ui_contact_sheet.png
"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[3]
_DEFAULT_PIXEL_ART = REPO_ROOT / ".agents/skills/pixel-art-studio/scripts/pixel_art.py"
PIXEL_ART_PY = Path(os.environ.get("PIXEL_ART_PY", str(_DEFAULT_PIXEL_ART)))

PICKUPS_DIR = REPO_ROOT / "assets/textures/pickups"
ANIM_DIR = REPO_ROOT / "assets/textures/animations"
UI_DIR = REPO_ROOT / "assets/textures/ui"
PREVIEW_DIR = REPO_ROOT / "docs/previews/milestone3"

# Unified arcane outline & shadow anchors from arcane_storm_palette.json
BASE_PALETTE: dict[str, str] = {
    ".": "#00000000",  # transparent
    "o": "#14101a",    # outline.deep_void
    "k": "#1e1829",    # outline.indigo_line / cast shadow edge
    "p": "#2c223d",    # outline.plum_shadow / cast shadow core

    # Cyan Mana Crystal & Piercing Surge ramp
    "c": "#113b70",    # deep sapphire facet
    "C": "#1c79c4",    # mid azure facet
    "a": "#33c9f5",    # vibrant mana cyan
    "A": "#9cf6ff",    # icy cyan highlight
    "W": "#ffffff",    # pure white specular

    # Emerald Mana Crystal ramp (high green ratio for AssetBBoxTest)
    "e": "#0c3b20",    # deep forest shadow
    "E": "#147539",    # rich emerald shadow
    "m": "#25b856",    # vibrant emerald mid
    "M": "#6ef084",    # bright mint highlight
    "n": "#ccffd4",    # pale mint specular

    # Solar Gold Mana Crystal & Overdrive Crown ramp
    "g": "#5e2d0c",    # burnt amber shadow
    "G": "#a65b17",    # bronze gold mid-shadow
    "y": "#e6a022",    # solar gold body
    "Y": "#ffe259",    # bright sun gold
    "S": "#fffbe0",    # warm ivory specular

    # Bezel & slate tones for HUD medallions
    "d": "#1a1f2e",    # neutral dark steel shadow
    "s": "#3c455c",    # mid slate bezel
    "l": "#687799",    # lit steel bezel top-left
    "h": "#9fb1d6",    # bright metallic glint
    "u": "#262d40",    # recessed empty socket pattern
    "U": "#151926",    # recessed empty socket dark

    # Fire emblem + Elemental Burst
    "r": "#781722",    # deep crimson slate
    "R": "#bf2626",    # crimson
    "f": "#f25c22",    # fiery orange-red
    "F": "#ff9e24",    # blazing orange
    "j": "#ffe054",    # solar yellow
    "J": "#fffbe6",    # white-hot flame core
    "1": "#2b0d17",    # dark crimson medallion recess

    # Ice emblem
    "i": "#10203d",    # deep frost navy recess
    "I": "#1b396b",    # frost blue inner shade
    "b": "#2489d6",    # glacial blue
    "B": "#6ce5ff",    # ice cyan
    "q": "#e8ffff",    # frost white-cyan

    # Lightning emblem
    "v": "#1d1338",    # deep violet recess
    "V": "#4b2382",    # electric violet inner accent
    "z": "#8c47d9",    # mid electric violet
    "Z": "#c884ff",    # bright violet corona
    "t": "#f5a623",    # warm amber bolt shadow
    "T": "#ffe954",    # electric gold bolt

    # Wind emblem
    "w": "#0d261e",    # deep forest slate recess
    "x": "#134d3b",    # deep emerald teal
    "X": "#2ca86d",    # emerald wind body
    "L": "#7bf2a8",    # bright gale mint
    "Q": "#f0fff4",    # white-mint wind crest

    # Earth emblem
    "2": "#24150f",    # deep umber recess
    "3": "#361f18",    # dark basalt
    "4": "#6e4427",    # crag brown
    "5": "#b87d3b",    # lit sandstone
    "6": "#f5d996",    # sunlit amber ridge
    "7": "#fff7d6",    # magma-white crest

    # Arcane Overdrive (violet-gold crown + crystal)
    "8": "#451b7a",    # deep royal violet
    "9": "#8a2be2",    # vivid arcane violet
    "0": "#d16bff",    # bright magenta-violet
    "!": "#ffd6ff",    # pale violet core glint
}


def hex_to_rgba(hex_str: str) -> tuple[int, int, int, int]:
    h = hex_str.lstrip("#")
    if len(h) == 6:
        return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)
    if len(h) == 8:
        return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16))
    raise ValueError(f"Invalid hex color: {hex_str}")


RGBA_MAP = {k: hex_to_rgba(v) for k, v in BASE_PALETTE.items()}


# =============================================================================
# 1. XP GEMS (16x16, 4 frames each)
#    Invariants: col 0 & col 15 strictly transparent, crisp #14101a outline,
#    stepped facets, 135-deg top-left highlight, detached cast shadow at y=14,
#    travelling diagonal glint across crystal facets in frames 1..3.
# =============================================================================

# Tier 1: Sharp faceted lozenge/diamond Cyan Mana Crystal
GEM_CYAN_FRAMES = [
    # Frame 0 (Canonical)
    [
        "................",  # 00
        ".......oo.......",  # 01
        "......oWAo......",  # 02
        ".....oWAAao.....",  # 03
        "....oWAAaCCo....",  # 04
        "...oAAaaWaCco...",  # 05
        "..oWAAaWWaCcco..",  # 06
        "..oAAaaAAaaCCo..",  # 07
        "...oAaaaCCcco...",  # 08
        "....oCaaCCco....",  # 09
        ".....oCCCco.....",  # 10
        "......occo......",  # 11
        ".......oo.......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 1 (Glint enters top-left & crown ridge)
    [
        "................",  # 00
        ".......oo.......",  # 01
        "......oWWo......",  # 02
        ".....oWWWAo.....",  # 03
        "....oWWWAaCo....",  # 04
        "...oAAWWWaCco...",  # 05
        "..oWAAaWWaCcco..",  # 06
        "..oAAaaAAaaCCo..",  # 07
        "...oAaaaCCcco...",  # 08
        "....oCaaCCco....",  # 09
        ".....oCCCco.....",  # 10
        "......occo......",  # 11
        ".......oo.......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 2 (Peak diagonal flash across crystal table)
    [
        "................",  # 00
        ".......oo.......",  # 01
        "......oWAo......",  # 02
        ".....oWAAao.....",  # 03
        "....oWAAWWCo....",  # 04
        "...oAAWWWWCco...",  # 05
        "..oWAAWWWWWCco..",  # 06
        "..oAAaWWWWaCCo..",  # 07
        "...oAaAWWWcco...",  # 08
        "....oCaaWWco....",  # 09
        ".....oCCCco.....",  # 10
        "......occo......",  # 11
        ".......oo.......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 3 (Specular sheen exits lower-right bevel)
    [
        "................",  # 00
        ".......oo.......",  # 01
        "......oWAo......",  # 02
        ".....oWAAao.....",  # 03
        "....oWAAaCCo....",  # 04
        "...oAAaaWaCco...",  # 05
        "..oWAAaWWaCcco..",  # 06
        "..oAAaaAAAWWCo..",  # 07
        "...oAaaaCWWWo...",  # 08
        "....oCaaAWWo....",  # 09
        ".....oCCWWo.....",  # 10
        "......oWAo......",  # 11
        ".......oo.......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
]

# Tier 2: Hexagonal Emerald-Cut Mana Crystal (high green purity)
GEM_EMERALD_FRAMES = [
    # Frame 4 (Canonical)
    [
        "................",  # 00
        ".....oooooo.....",  # 01
        "....onMMmmEo....",  # 02
        "...oMmnMMmEEo...",  # 03
        "..onMmMMMMmEEo..",  # 04
        "..oMMmnnnMmEEo..",  # 05
        "..oMmmMnnMMEEo..",  # 06
        "..oMmmMMMMMEeo..",  # 07
        "..oEMmmmmmEEeo..",  # 08
        "...oEMmmmEEeo...",  # 09
        "....oEMmEEeo....",  # 10
        ".....oEEeeo.....",  # 11
        "......oooo......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 5 (Glint enters top-left bevel)
    [
        "................",  # 00
        ".....oooooo.....",  # 01
        "....onnnMmEo....",  # 02
        "...onnnMMmEEo...",  # 03
        "..onnnMMMMmEEo..",  # 04
        "..oMMnnnnMmEEo..",  # 05
        "..oMmmMnnMMEEo..",  # 06
        "..oMmmMMMMMEeo..",  # 07
        "..oEMmmmmmEEeo..",  # 08
        "...oEMmmmEEeo...",  # 09
        "....oEMmEEeo....",  # 10
        ".....oEEeeo.....",  # 11
        "......oooo......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 6 (Peak diagonal glint crosses emerald table)
    [
        "................",  # 00
        ".....oooooo.....",  # 01
        "....onMMmmEo....",  # 02
        "...oMmnMMnEEo...",  # 03
        "..onMmMMnnMEEo..",  # 04
        "..oMMmnnnnMEEo..",  # 05
        "..oMmnnnnnMEEo..",  # 06
        "..oMmmMnnnMEeo..",  # 07
        "..oEMmmmnnnEeo..",  # 08
        "...oEMmmnnEeo...",  # 09
        "....oEMmEEeo....",  # 10
        ".....oEEeeo.....",  # 11
        "......oooo......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 7 (Glint exits bottom-right facet)
    [
        "................",  # 00
        ".....oooooo.....",  # 01
        "....onMMmmEo....",  # 02
        "...oMmnMMmEEo...",  # 03
        "..onMmMMMMmEEo..",  # 04
        "..oMMmnnnMmEEo..",  # 05
        "..oMmmMnnMMEEo..",  # 06
        "..oMmmMMMMMnn...",  # 07  -> wait, keep outline!
        "..oEMmmmmmEEeo..",  # 08
        "...oEMmmmEEeo...",  # 09
        "....oEMmEEeo....",  # 10
        ".....oEEeeo.....",  # 11
        "......oooo......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
]
# Keep crisp right-hand #14101a border on Emerald Frame 7 row 07
GEM_EMERALD_FRAMES[3][7] = "..oMmmMMMMnnEo.."
GEM_EMERALD_FRAMES[3][8] = "..oEMmmmmMnnEo.."
GEM_EMERALD_FRAMES[3][9] = "...oEMmmMnneo..."
GEM_EMERALD_FRAMES[3][10] = "....oEMmMneo...."

# Tier 3: Wide Royal Star-Cut / Octagram Solar Gold Crystal
GEM_GOLD_FRAMES = [
    # Frame 8 (Canonical)
    [
        "................",  # 00
        "......oooo......",  # 01
        ".....oSYYYo.....",  # 02
        "....oSYYyyGo....",  # 03
        "...oSYYSSyGGo...",  # 04
        "..oSYySSYyGGgo..",  # 05
        ".oSSySWWSyGGggo.",  # 06
        ".oYYyyWWyyGGggo.",  # 07
        "..oYyyYYyyGggo..",  # 08
        "...oGyyyyGGgo...",  # 09
        "....oGGGgggo....",  # 10
        ".....oGGggo.....",  # 11
        "......oooo......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 9 (Upper-left flare glint)
    [
        "................",  # 00
        "......oooo......",  # 01
        ".....oSSSYo.....",  # 02
        "....oSSSWyGo....",  # 03
        "...oSWWSSyGGo...",  # 04
        "..oSYWSSYyGGgo..",  # 05
        ".oSSySWWSyGGggo.",  # 06
        ".oYYyyWWyyGGggo.",  # 07
        "..oYyyYYyyGggo..",  # 08
        "...oGyyyyGGgo...",  # 09
        "....oGGGgggo....",  # 10
        ".....oGGggo.....",  # 11
        "......oooo......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 10 (Center star facet peak flash)
    [
        "................",  # 00
        "......oooo......",  # 01
        ".....oSYYYo.....",  # 02
        "....oSYYWyGo....",  # 03
        "...oSYYWWyGGo...",  # 04
        "..oSYyWWWWGGgo..",  # 05
        ".oSSyWWWWWSGggo.",  # 06
        ".oYYySWWWWSGggo.",  # 07
        "..oYyySWWSGggo..",  # 08
        "...oGyySSGGgo...",  # 09
        "....oGGGgggo....",  # 10
        ".....oGGggo.....",  # 11
        "......oooo......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
    # Frame 11 (Lower-right gleam exit)
    [
        "................",  # 00
        "......oooo......",  # 01
        ".....oSYYYo.....",  # 02
        "....oSYYyyGo....",  # 03
        "...oSYYSSyGGo...",  # 04
        "..oSYySSYyGGgo..",  # 05
        ".oSSySWWSyGGggo.",  # 06
        ".oYYyyWWyySSggo.",  # 07
        "..oYyyYYySWWgo..",  # 08
        "...oGyyySWWgo...",  # 09
        "....oGGSWWgo....",  # 10
        ".....oGSggo.....",  # 11
        "......oooo......",  # 12
        "................",  # 13
        "....okppppko....",  # 14
        "................",  # 15
    ],
]


# =============================================================================
# 2. HUD ELEMENT RUNE MEDALLIONS (96x16 = 6x 16x16)
#    0: FIRE, 1: ICE, 2: LIGHTNING, 3: WIND, 4: EARTH, 5: EMPTY_SOCKET
#    Consistent chamfered steel octagonal bezel with 1px #14101a border,
#    135-deg metallic highlight (h/l), and deeply recessed high-contrast emblem.
# =============================================================================

HUD_ELEMENT_FRAMES = [
    # 0: FIRE (Blazing flame emblem on dark crimson slate)
    # 0123456789012345
    [
        "....oooooooo....",  # 00
        "..oohhhhhhlloo..",  # 01
        ".ohl1111r111rso.",  # 02
        ".ol111rFF1111do.",  # 03
        "oh111rFJFf1111do",  # 04
        "ol111rFJJf1r11do",  # 05
        "ol11rFFJJfRFr1do",  # 06
        "ol11RfFJjfFFr1do",  # 07
        "ol11RfjjJjFfr1do",  # 08
        "ol11rfjJJjffR1do",  # 09
        "ol111RfJJfRR11do",  # 10
        "os1111RffR1111do",  # 11
        ".os1111rr1111do.",  # 12
        ".osd11111111ddo.",  # 13
        "..ooddddddddoo..",  # 14
        "....oooooooo....",  # 15
    ],
    # 1: ICE (Sharp 6-point crystalline snowflake on deep frost navy)
    # 0123456789012345
    [
        "....oooooooo....",  # 00
        "..oohhhhhhlloo..",  # 01
        ".ohlIIiiqiiIIdo.",  # 02
        ".olIiiiBWBiiiIdo",  # 03
        "ohIiBqiBWBiiqBdo",  # 04
        "olIiiBqWWWqBiiIo",  # 05
        "olIiiiqWWWqiiiIo",  # 06
        "olIiqBWWqWWBqiIo",  # 07
        "olIiiiqWWWqiiiIo",  # 08
        "olIiiBqWWWqBiiIo",  # 09
        "osIiBqiBWBiiqBdo",  # 10
        ".osIiiiBWBiiiIdo",  # 11
        ".osdIIiiqiiIIdo.",  # 12
        ".osdIIIIIIIIddo.",  # 13
        "..ooddddddddoo..",  # 14
        "....oooooooo....",  # 15
    ],
    # 2: LIGHTNING (Jagged golden thunderbolt on deep violet slate)
    # 0123456789012345
    [
        "....oooooooo....",  # 00
        "..oohhhhhhlloo..",  # 01
        ".ohlvvvvvvvvVdo.",  # 02
        ".olvvvvTTWvvvdo.",  # 03
        "ohvvvvTTWWvvzvdo",  # 04
        "olvvvTTWWvvvVvdo",  # 05
        "olvvTTWWtvvvvvdo",  # 06
        "olvvTWWWWTTvvvdo",  # 07
        "olvvvvZWWTTtvvdo",  # 08
        "olvvvvvWWTtvvvdo",  # 09
        "olvzvWWttvvvvvdo",  # 10
        "osvvWTtvvvvvvvdo",  # 11
        ".osvTtvvvvvvvdo.",  # 12
        ".osdVVvvvvvvddo.",  # 13
        "..ooddddddddoo..",  # 14
        "....oooooooo....",  # 15
    ],
    # 3: WIND (Crisp emerald cyclone S-spiral on deep forest slate)
    # 0123456789012345
    [
        "....oooooooo....",  # 00
        "..oohhhhhhlloo..",  # 01
        ".ohlwwwwwwwwxdo.",  # 02
        ".olwwwLLQQLLwdo.",  # 03
        "ohwwLLQwwwwLxwdo",  # 04
        "olwLQwwXLLwwwxdo",  # 05
        "olwLwwLQQLLwwxdo",  # 06
        "olwLwwLQwxLwwxdo",  # 07
        "olwwXwwLLXwXwxdo",  # 08
        "olwwwXXwwXXwwxdo",  # 09
        "olwwwwXXXXLwwxdo",  # 10
        "oswwXwwwwLLwwxdo",  # 11
        ".oswwXXXXwwwwdo.",  # 12
        ".osdxwwwwwwwddo.",  # 13
        "..ooddddddddoo..",  # 14
        "....oooooooo....",  # 15
    ],
    # 4: EARTH (Jagged twin-crag stone spire split by glowing amber fissure)
    # 0123456789012345
    [
        "....oooooooo....",  # 00
        "..oohhhhhhlloo..",  # 01
        ".ohl222222222do.",  # 02
        ".ol2222542222do.",  # 03
        "oh222256422222do",  # 04
        "ol222567432222do",  # 05
        "ol225674432542do",  # 06
        "ol225644325642do",  # 07
        "ol256443356432do",  # 08
        "ol256743544332do",  # 09
        "ol554764434332do",  # 10
        "os544374333332do",  # 11
        ".os4334333332do.",  # 12
        ".osd33333333ddo.",  # 13
        "..ooddddddddoo..",  # 14
        "....oooooooo....",  # 15
    ],
    # 5: EMPTY_SOCKET (Engraved empty arcane bezel socket)
    # 0123456789012345
    [
        "....oooooooo....",  # 00
        "..oolllllllsso..",  # 01
        ".olsuuuuuuuusdo.",  # 02
        ".olsuUUUUUUUsdo.",  # 03
        "olsuUUUuuUUUUsdo",  # 04
        "olsuUUuUUuUUUsdo",  # 05
        "olsuUuUuuUuUUsdo",  # 06
        "olsuUuuUUuuUUsdo",  # 07
        "olsuUuuUUuuUUsdo",  # 08
        "olsuUuUuuUuUUsdo",  # 09
        "olsuUUuUUuUUUsdo",  # 10
        "ossuUUUuuUUUUsdo",  # 11
        ".ossuUUUUUUUsdo.",  # 12
        ".ossdsssssssddo.",  # 13
        "..ooddddddddoo..",  # 14
        "....oooooooo....",  # 15
    ],
]
# Verify exact 16-char rows in HUD ICE rows 04..10 ("oh..." wait: row 04 = 2+12+2 = 16; row 03 = ".olIiiiBWBiiiIdo" -> check col 15):
HUD_ELEMENT_FRAMES[1][3] = ".olIiiiBWBiiIdo."
HUD_ELEMENT_FRAMES[1][11] = ".osIiiiBWBiiIdo."


# =============================================================================
# 3. POWER-UP ICONS (48x16 = 3x 16x16)
#    0: ARCANE_OVERDRIVE (violet-gold royal crown + glowing amethyst surge orb)
#    1: PIERCING_SURGE (twin barbed cyan-sapphire spears launching up-right)
#    2: ELEMENTAL_BURST (fiery triple-nova explosion emblem)
# =============================================================================

POWERUP_ICON_FRAMES = [
    # 0: ARCANE_OVERDRIVE (3-spired royal gold crown with luminous amethyst core)
    # 0123456789012345
    [
        ".......oo.......",  # 00
        "..oo..oSSo..oo..",  # 01
        ".oSWo.oYYo.oSYo.",  # 02
        ".oSYooSYYSooYGo.",  # 03
        ".oSYYoY!!YoYYGo.",  # 04
        ".oSSYy!WW!yYGGoo",  # 05  -> trim right col to keep 16!
        "..oSYy0WW0yYGo..",  # 06
        "..oYYy9009yYGo..",  # 07
        "..oYYy8998yGGo..",  # 08
        "..oYGy8888yGgo..",  # 09
        "..oSSYYYYYYGGo..",  # 10
        "..oYYYYYGGGGgo..",  # 11
        "...oGGGGggggo...",  # 12
        "....oooooooo....",  # 13
        "................",  # 14
        "................",  # 15
    ],
    # 1: PIERCING_SURGE (Twin barbed cyan-white arcane spears thrusting top-right)
    # 0123456789012345
    [
        ".....oooo.......",  # 00
        "....oWWWWo......",  # 01
        "...oWAAAAo......",  # 02
        "...oWAAaCoooo...",  # 03
        "....oWCCoWWWWo..",  # 04
        "....oACooWAAAAo.",  # 05
        "...oACCo.oWAAaCo",  # 06
        "..oACCo...oWCCo.",  # 07
        ".oACCo....oACo..",  # 08
        "oWCCo....oACCo..",  # 09
        "oCCo....oACCo...",  # 10
        "oCo....oACCo....",  # 11
        ".o....oWCCo.....",  # 12
        "......oCCo......",  # 13
        "......oCo.......",  # 14
        ".......o........",  # 15
    ],
    # 2: ELEMENTAL_BURST (Fiery triple-nova explosion emblem: main blast + 2 mini bursts)
    # 0123456789012345
    [
        "..........oo....",  # 00
        "....oo...oJjo...",  # 01
        "...oJjo.oJWJfo..",  # 02
        ".oooFjo..oJfo...",  # 03
        ".oJFjjjoooo.....",  # 04
        "..oJFJWWJfo.....",  # 05
        ".oojjWWWWjfo....",  # 06
        "oJFJWWWWWWFjo...",  # 07
        ".oojWWWWWWffo...",  # 08
        "..oFJWWWWfjRooo.",  # 09
        "..oRfJWWjFRoJjo.",  # 10
        ".oRfFjJjfRRoJWfo",  # 11
        ".oooRfFjRoooJfo.",  # 12
        "....oRfo....oo..",  # 13
        ".....oo.........",  # 14
        "................",  # 15
    ],
]
POWERUP_ICON_FRAMES[0][5] = ".oSSYy!WW!yYGGo."


def render_grid(rows: list[str], label: str) -> Image.Image:
    if len(rows) != 16:
        raise ValueError(f"{label}: expected 16 rows, got {len(rows)}")
    pixels: list[tuple[int, int, int, int]] = []
    for r_idx, row in enumerate(rows):
        if len(row) != 16:
            raise ValueError(f"{label} row {r_idx}: expected 16 chars, got {len(row)} ({row!r})")
        for ch in row:
            if ch not in RGBA_MAP:
                raise ValueError(f"{label} row {r_idx}: unknown palette key {ch!r}")
            pixels.append(RGBA_MAP[ch])
    im = Image.new("RGBA", (16, 16))
    im.putdata(pixels)
    return im


def assemble_strip(frames: list[Image.Image]) -> Image.Image:
    w, h = frames[0].size
    strip = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.paste(fr, (i * w, 0))
    return strip


def draw_checker(draw: ImageDraw.ImageDraw, x0: int, y0: int, w: int, h: int, dark: bool = True, cell: int = 12) -> None:
    c1, c2 = ((56, 60, 72, 255), (42, 46, 56, 255)) if dark else ((220, 224, 232, 255), (192, 198, 210, 255))
    for y in range(y0, y0 + h, cell):
        for x in range(x0, x0 + w, cell):
            col = c1 if ((x - x0) // cell + (y - y0) // cell) % 2 == 0 else c2
            draw.rectangle([x, y, min(x + cell - 1, x0 + w - 1), min(y + cell - 1, y0 + h - 1)], fill=col)


def draw_slate(draw: ImageDraw.ImageDraw, x0: int, y0: int, w: int, h: int) -> None:
    draw.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(27, 32, 41, 255))


def upscale_nearest(im: Image.Image, scale: int) -> Image.Image:
    """Integer nearest-neighbor upscale using numpy."""
    arr = np.asarray(im.convert("RGBA"), dtype=np.uint8)
    scaled = np.repeat(np.repeat(arr, scale, axis=0), scale, axis=1)
    return Image.fromarray(scaled, "RGBA")


def build_contact_sheet(
    gems_frames: list[Image.Image],
    hud_frames: list[Image.Image],
    powerup_frames: list[Image.Image],
) -> Image.Image:
    scale = 6
    box = 16 * scale  # 96x96
    font = ImageFont.load_default()
    pad_x = 24
    gap = 12

    rows = [
        ("1a. XP Gem: Cyan Mana Crystal (gems_anim_sheet f0..3; f0 -> gem_cyan.png)", gems_frames[0:4], ["f0 (canon)", "f1 glint", "f2 peak", "f3 trail"]),
        ("1b. XP Gem: Emerald Mana Crystal (gems_anim_sheet f4..7; f4 -> gem_emerald.png)", gems_frames[4:8], ["f4 (canon)", "f5 glint", "f6 peak", "f7 trail"]),
        ("1c. XP Gem: Solar Gold Mana Crystal (gems_anim_sheet f8..11; f8 -> gem_gold.png)", gems_frames[8:12], ["f8 (canon)", "f9 glint", "f10 peak", "f11 trail"]),
        ("2. HUD Element Rune Medallions: hud_elements_sheet.png (96x16 = 6x 16x16)", hud_frames, ["0:FIRE", "1:ICE", "2:LIGHTNING", "3:WIND", "4:EARTH", "5:EMPTY"]),
        ("3. Power-Up Icons: powerup_icons_sheet.png (48x16 = 3x 16x16)", powerup_frames, ["0:OVERDRIVE", "1:PIERCING", "2:ELEM_BURST"]),
    ]

    max_cols = max(len(r[1]) for r in rows)
    sheet_w = pad_x * 2 + 64 + max_cols * (box + gap)
    header_h = 64
    row_block_h = 22 + (box + 18) * 2 + 18
    sheet_h = header_h + len(rows) * row_block_h + 16

    canvas = Image.new("RGBA", (sheet_w, sheet_h), (18, 20, 28, 255))
    draw = ImageDraw.Draw(canvas)
    draw.text((pad_x, 14), "SPELL STORM - MILESTONE 3: XP GEMS, HUD MEDALLIONS & POWER-UP ICONS (6x Nearest-Neighbor)", fill=(255, 245, 214, 255), font=font)
    draw.text((pad_x, 34), "Strict Binary Alpha [0, 255] | #14101a Crisp Ink Outlines | 135 deg Top-Left Lighting", fill=(168, 178, 198, 255), font=font)

    cur_y = header_h
    for title, imgs, labels in rows:
        draw.text((pad_x, cur_y), title, fill=(143, 245, 255, 255), font=font)
        cur_y += 20
        for bg_name, is_light in [("SLATE", False), ("LIGHT", True)]:
            draw.text((pad_x, cur_y + box // 2 - 6), bg_name, fill=(160, 170, 190, 255), font=font)
            bx = pad_x + 58
            for im, lbl in zip(imgs, labels):
                if is_light:
                    draw_checker(draw, bx, cur_y, box, box, dark=False)
                else:
                    draw_slate(draw, bx, cur_y, box, box)
                draw.rectangle([bx, cur_y, bx + box - 1, cur_y + box - 1], outline=(72, 80, 98, 255))
                up = upscale_nearest(im, scale)
                canvas.alpha_composite(up, (bx, cur_y))
                if not is_light:
                    draw.text((bx + 2, cur_y + box + 3), lbl, fill=(195, 205, 222, 255), font=font)
                bx += box + gap
            cur_y += box + 18
        cur_y += 14

    return canvas


def verify_gem_invariants(path: Path, is_emerald: bool = False) -> None:
    with Image.open(path) as opened:
        im = opened.convert("RGBA")
    assert im.size == (16, 16), f"{path}: expected 16x16, got {im.size}"
    px = im.load()
    for y in range(16):
        assert px[0, y][3] == 0, f"{path}: col 0 not transparent at y={y}"
        assert px[15, y][3] == 0, f"{path}: col 15 not transparent at y={y}"
    xs = [x for y in range(16) for x in range(16) if px[x, y][3] > 0]
    ys = [y for y in range(16) for x in range(16) if px[x, y][3] > 0]
    w = max(xs) - min(xs) + 1
    h = max(ys) - min(ys) + 1
    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    assert w >= 8 and h >= 8, f"{path}: bbox too small {w}x{h}"
    assert abs(cx - 8.0) <= 2.0 and abs(cy - 8.0) <= 2.0, f"{path}: center ({cx}, {cy}) out of range"
    if is_emerald:
        vis = [px[x, y] for y in range(16) for x in range(16) if px[x, y][3] > 64]
        avg_r = sum(p[0] for p in vis) / len(vis)
        avg_g = sum(p[1] for p in vis) / len(vis)
        avg_b = sum(p[2] for p in vis) / len(vis)
        assert avg_g > avg_r * 1.5, f"{path}: emerald green {avg_g:.1f} <= 1.5 * red {avg_r:.1f}"
        assert avg_g > avg_b * 1.2, f"{path}: emerald green {avg_g:.1f} <= 1.2 * blue {avg_b:.1f}"
        print(f"Emerald color check: avgR={avg_r:.1f}, avgG={avg_g:.1f}, avgB={avg_b:.1f}")


def inspect_png(path: Path, expected_w: int, expected_h: int) -> None:
    if not PIXEL_ART_PY.exists():
        print(f"SKIP inspect ({PIXEL_ART_PY} not found; set PIXEL_ART_PY to run pixel_art.py): {path.relative_to(REPO_ROOT)}")
        return
    cmd = [
        sys.executable,
        str(PIXEL_ART_PY),
        "inspect",
        str(path),
        "--size",
        str(expected_w),
        str(expected_h),
        "--binary-alpha",
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=False)
    print(f"INSPECT {path.relative_to(REPO_ROOT)} -> exit={res.returncode}")
    print(res.stdout.strip())
    if res.returncode != 0:
        raise RuntimeError(f"pixel_art.py inspect failed for {path}: {res.stderr}\n{res.stdout}")


def main() -> None:
    PICKUPS_DIR.mkdir(parents=True, exist_ok=True)
    ANIM_DIR.mkdir(parents=True, exist_ok=True)
    UI_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    cyan_imgs = [render_grid(g, f"gem_cyan_{i}") for i, g in enumerate(GEM_CYAN_FRAMES)]
    emerald_imgs = [render_grid(g, f"gem_emerald_{i}") for i, g in enumerate(GEM_EMERALD_FRAMES)]
    gold_imgs = [render_grid(g, f"gem_gold_{i}") for i, g in enumerate(GEM_GOLD_FRAMES)]
    all_gem_imgs = cyan_imgs + emerald_imgs + gold_imgs

    hud_imgs = [render_grid(g, f"hud_element_{i}") for i, g in enumerate(HUD_ELEMENT_FRAMES)]
    powerup_imgs = [render_grid(g, f"powerup_icon_{i}") for i, g in enumerate(POWERUP_ICON_FRAMES)]

    gem_cyan_out = PICKUPS_DIR / "gem_cyan.png"
    gem_emerald_out = PICKUPS_DIR / "gem_emerald.png"
    gem_gold_out = PICKUPS_DIR / "gem_gold.png"
    gems_sheet_out = ANIM_DIR / "gems_anim_sheet.png"
    hud_sheet_out = UI_DIR / "hud_elements_sheet.png"
    powerup_sheet_out = UI_DIR / "powerup_icons_sheet.png"
    contact_out = PREVIEW_DIR / "pickups_ui_contact_sheet.png"

    cyan_imgs[0].save(gem_cyan_out)
    emerald_imgs[0].save(gem_emerald_out)
    gold_imgs[0].save(gem_gold_out)
    assemble_strip(all_gem_imgs).save(gems_sheet_out)
    assemble_strip(hud_imgs).save(hud_sheet_out)
    assemble_strip(powerup_imgs).save(powerup_sheet_out)

    contact = build_contact_sheet(all_gem_imgs, hud_imgs, powerup_imgs)
    contact.save(contact_out)

    verify_gem_invariants(gem_cyan_out, is_emerald=False)
    verify_gem_invariants(gem_emerald_out, is_emerald=True)
    verify_gem_invariants(gem_gold_out, is_emerald=False)

    inspect_png(gem_cyan_out, 16, 16)
    inspect_png(gem_emerald_out, 16, 16)
    inspect_png(gem_gold_out, 16, 16)
    inspect_png(gems_sheet_out, 192, 16)
    inspect_png(hud_sheet_out, 96, 16)
    inspect_png(powerup_sheet_out, 48, 16)
    print(f"OK contact sheet: {contact_out.relative_to(REPO_ROOT)} ({contact.size[0]}x{contact.size[1]})")


if __name__ == "__main__":
    main()
