#!/usr/bin/env python3
"""
Hand-authored 32x32 pixel-art sprite builder for the Storm Archmage (player).

Produces:
- Editable pixel_art.py grid JSON sources in assets/sources/characters/
- Canonical 64x32 (2-frame opposing stride) walk sheets in assets/textures/characters/
- Extended 256x32 (8-frame idle/walk/cast) sheets in assets/textures/animations/
- 6x nearest-neighbor contact sheet & animated GIF previews in docs/previews/milestone2/
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
SRC_DIR = ROOT / "assets/sources/characters"
TEX_CHAR_DIR = ROOT / "assets/textures/characters"
TEX_ANIM_DIR = ROOT / "assets/textures/animations"
PREVIEW_DIR = ROOT / "docs/previews/milestone2"

# Exact colors from arcane_storm_palette.json
PALETTE = {
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
    "X": "#ffffff",  # arcane_crystal[3] white-hot core / eye glint
}

FRAME_NAMES = [
    ("idle_0", 220),
    ("idle_1", 220),
    ("walk_0", 120),
    ("walk_1", 120),
    ("walk_2", 120),
    ("walk_3", 120),
    ("cast_0", 110),
    ("cast_1", 150),
]

# Ruler for 32x32 grids:
# 0         1         2         3
# 01234567890123456789012345678901

DOWN_FRAMES = [
    # 0: idle_0 (calm stance, staff in right hand on viewer's left, boots centered at y=29)
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo............",  # 05
        "............oHLBBBSo............",  # 06
        ".....o.....oHLBBBBSo............",  # 07
        "....oXo...oHLBBBBBSSo...........",  # 08
        "....oECo..oLLBBBBBSSo...........",  # 09
        "....oCCo..oLkpGGYkpSo...........",  # 10
        ".....oo...oLkpGWGkpSo...........",  # 11
        "....ooo..oHLBBBBBBBSSo..........",  # 12
        "...orurooHLBBppppppBBSSo........",  # 13
        "....omooLLBppkkkkkkppBSSo.......",  # 14
        "....oroo.okEXkkEXko.ooo.........",  # 15
        "....omoo..oksawwqsko............",  # 16
        "....oroo.oLLkwwqqkSSo...........",  # 17
        "....ofsoooLBBGYwwYGSSSo.........",  # 18
        "....omooLLBBBpGGpBBBSSfo........",  # 19
        "....orooLBBBBpSSpBBBSSao........",  # 20
        "....omooLBBmmnGYGmmBBSSo........",  # 21
        "....orooLBBBBSSSSBBBSSSo........",  # 22
        "....omooLBBBBSSBBBBSSSo.........",  # 23
        "....orooLBBBBSSBBBBSSSo.........",  # 24
        "....omooLBBBBSSBBBBSSSo.........",  # 25
        "....orooGLLBBSSSSBBSSGo.........",  # 26
        "....omooopBBBBSSSSBppo..........",  # 27
        ".....oo...oouro..ormoo..........",  # 28
        "..........ooooo..ooooo..........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 1: idle_1 (crystal floats +1px & flares, subtle collar/robe breath)
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo............",  # 05
        ".....o......oHLBBBSo............",  # 06
        "....oXo....oHLBBBBSo............",  # 07
        "...oEXXo..oHLBBBBBSSo...........",  # 08
        "....oECo..oLLBBBBBSSo...........",  # 09
        ".....oo...oLkpGGYkpSo...........",  # 10
        "..........oLkpGWGkpSo...........",  # 11
        "....ooo..oHLBBBBBBBSSo..........",  # 12
        "...orurooHLBBppppppBBSSo........",  # 13
        "....omooLLBppkkkkkkppBSSo.......",  # 14
        "....oroo.okEXkkEXko.ooo.........",  # 15
        "....omoo..oksawwqsko............",  # 16
        "....oroo.oLLkwwqqkSSo...........",  # 17
        "....ofsoooLBBGYwwYGSSSo.........",  # 18
        "....omooLLBBBpGGpBBBSSfo........",  # 19
        "....orooLBBBBpSSpBBBSSao........",  # 20
        "....omooLBBmmnGYGmmBBSSo........",  # 21
        "....orooLLBBBSSSSBBBSSSo........",  # 22
        "....omooLLBBBSSBBBBSSSo.........",  # 23
        "....orooLLBBBSSBBBBSSSo.........",  # 24
        "....omooLBBBBSSBBBBSSSo.........",  # 25
        "....orooGLLBBSSSSBBSSGo.........",  # 26
        "....omooopBBBBSSSSBppo..........",  # 27
        ".....oo...oouro..ormoo..........",  # 28
        "..........ooooo..ooooo..........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 2: walk_0 (left stride forward on viewer's left y=29, right foot tucked back y=28, staff steady)
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo............",  # 05
        "............oHLBBBSo............",  # 06
        ".....o.....oHLBBBBSo............",  # 07
        "....oXo...oHLBBBBBSSo...........",  # 08
        "....oECo..oLLBBBBBSSo...........",  # 09
        "....oCCo..oLkpGGYkpSo...........",  # 10
        ".....oo...oLkpGWGkpSo...........",  # 11
        "....ooo..oHLBBBBBBBSSo..........",  # 12
        "...orurooHLBBppppppBBSSo........",  # 13
        "....omooLLBppkkkkkkppBSSo.......",  # 14
        "....oroo.okEXkkEXko.ooo.........",  # 15
        "....omoo..oksawwqsko............",  # 16
        "....oroo.oLLkwwqqkSSo...........",  # 17
        "....ofsoooLBBGYwwYGSSSo.........",  # 18
        "....omooLLBBBpGGpBBBSSo.........",  # 19
        "....orooLBBBBpSSpBBBSSfo........",  # 20
        "....omooLBBmmnGYGmmBBSSo........",  # 21
        "....orooLBBBBSSSSBBBSSSo........",  # 22
        "....omooLBBBBSSBBBBSSSo.........",  # 23
        "....orooLLBBBSSBBBBSSSo.........",  # 24
        "....omooLLBBBSSBBBBSSSo.........",  # 25
        "....orooGLLBBSSSSBBSSGo.........",  # 26
        "....omooopBBBBSSSSrmoo..........",  # 27
        ".....oo..oouroo..oooo...........",  # 28
        ".........ooooo..................",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 3: walk_1 (passing stride A, +1px upward torso bounce, planted left boot y=29)
    [
        "................................",  # 00
        ".................oooo...........",  # 01
        "................oHLBSo..........",  # 02
        "..............ooHLBBo...........",  # 03
        ".............oHLLBSo............",  # 04
        "............oHLBBBSo............",  # 05
        ".....o.....oHLBBBBSo............",  # 06
        "....oXo...oHLBBBBBSSo...........",  # 07
        "....oECo..oLLBBBBBSSo...........",  # 08
        "....oCCo..oLkpGGYkpSo...........",  # 09
        ".....oo...oLkpGWGkpSo...........",  # 10
        "....ooo..oHLBBBBBBBSSo..........",  # 11
        "...orurooHLBBppppppBBSSo........",  # 12
        "....omooLLBppkkkkkkppBSSo.......",  # 13
        "....oroo.okEXkkEXko.ooo.........",  # 14
        "....omoo..oksawwqsko............",  # 15
        "....oroo.oLLkwwqqkSSo...........",  # 16
        "....ofsoooLBBGYwwYGSSSo.........",  # 17
        "....omooLLBBBpGGpBBBSSfo........",  # 18
        "....orooLBBBBpSSpBBBSSao........",  # 19
        "....omooLBBmmnGYGmmBBSSo........",  # 20
        "....orooLBBBBSSSSBBBSSSo........",  # 21
        "....omooLBBBBSSBBBBSSSo.........",  # 22
        "....orooLBBBBSSBBBBSSSo.........",  # 23
        "....omooLBBBBSSBBBBSSSo.........",  # 24
        "....orooGLLBBSSSSBBSSGo.........",  # 25
        "....omooopBBBBSSSSBppo..........",  # 26
        ".....oo...oouro..ormo...........",  # 27
        "..........oouro..oooo...........",  # 28
        "..........ooooo.................",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 4: walk_2 (opposing right stride forward y=29, left foot tucked back y=28)
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo............",  # 05
        "............oHLBBBSo............",  # 06
        ".....o.....oHLBBBBSo............",  # 07
        "....oXo...oHLBBBBBSSo...........",  # 08
        "....oECo..oLLBBBBBSSo...........",  # 09
        "....oCCo..oLkpGGYkpSo...........",  # 10
        ".....oo...oLkpGWGkpSo...........",  # 11
        "....ooo..oHLBBBBBBBSSo..........",  # 12
        "...orurooHLBBppppppBBSSo........",  # 13
        "....omooLLBppkkkkkkppBSSo.......",  # 14
        "....oroo.okEXkkEXko.ooo.........",  # 15
        "....omoo..oksawwqsko............",  # 16
        "....oroo.oLLkwwqqkSSo...........",  # 17
        "....ofsoooLBBGYwwYGSSSo.........",  # 18
        "....omooLLBBBpGGpBBBSSo.........",  # 19
        "....orooLBBBBpSSpBBBSSfo........",  # 20
        "....omooLBBmmnGYGmmBBSSo........",  # 21
        "....orooLBBBBSSSSBBBSSSo........",  # 22
        "....omooLBBBBSSBBBBSSSo.........",  # 23
        "....orooLBBBBSSBBBBSSSo.........",  # 24
        "....omooLBBBBSSBBBBSSSo.........",  # 25
        "....orooGLLBBSSSSBBSSGo.........",  # 26
        "....omoo.oouroSSSSBppo..........",  # 27
        ".....oo..ooooo..oormoo..........",  # 28
        "................ooooo...........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 5: walk_3 (passing stride B, +1px bounce, right boot planted y=29)
    [
        "................................",  # 00
        ".................oooo...........",  # 01
        "................oHLBSo..........",  # 02
        "..............ooHLBBo...........",  # 03
        ".............oHLLBSo............",  # 04
        "............oHLBBBSo............",  # 05
        ".....o.....oHLBBBBSo............",  # 06
        "....oXo...oHLBBBBBSSo...........",  # 07
        "....oECo..oLLBBBBBSSo...........",  # 08
        "....oCCo..oLkpGGYkpSo...........",  # 09
        ".....oo...oLkpGWGkpSo...........",  # 10
        "....ooo..oHLBBBBBBBSSo..........",  # 11
        "...orurooHLBBppppppBBSSo........",  # 12
        "....omooLLBppkkkkkkppBSSo.......",  # 13
        "....oroo.okEXkkEXko.ooo.........",  # 14
        "....omoo..oksawwqsko............",  # 15
        "....oroo.oLLkwwqqkSSo...........",  # 16
        "....ofsoooLBBGYwwYGSSSo.........",  # 17
        "....omooLLBBBpGGpBBBSSfo........",  # 18
        "....orooLBBBBpSSpBBBSSao........",  # 19
        "....omooLBBmmnGYGmmBBSSo........",  # 20
        "....orooLBBBBSSSSBBBSSSo........",  # 21
        "....omooLBBBBSSBBBBSSSo.........",  # 22
        "....orooLBBBBSSBBBBSSSo.........",  # 23
        "....omooLBBBBSSBBBBSSSo.........",  # 24
        "....orooGLLBBSSSSBBSSGo.........",  # 25
        "....omooopBBBBSSSSBppo..........",  # 26
        ".....oo...oouro..ormo...........",  # 27
        "..........oooo...ormo...........",  # 28
        ".................oooo...........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 6: cast_0 (windup: brim lifts, crystal charges bright, left hand raises arcane spark)
    [
        "................................",  # 00
        ".................oooo...........",  # 01
        "................oHLBSo..........",  # 02
        "..............ooHLBBo...........",  # 03
        ".............oHLLBSo............",  # 04
        "....o.......oHLBBBSo............",  # 05
        "...oXo.....oHLBBBBSo............",  # 06
        "..oEXXo...oHLBBBBBSSo...........",  # 07
        "...oECo...oLLBBBBBSSo...........",  # 08
        "....oo....oLkpGGYkpSo...........",  # 09
        "...ooo....oLkpGWGkpSo...........",  # 10
        "..oruro..oHLBBBBBBBSSo..........",  # 11
        "...omoo.oHLBBppppppBBSSo........",  # 12
        "...oroo.oLBppkkkkkkppBSo........",  # 13
        "...omoo..okXXkkXXko.ooo.........",  # 14
        "...oroo...oksawwqsko............",  # 15
        "...ofso..oLLkwwqqkSSo..oo.......",  # 16
        "...omooooLBBGYwwYGSSSooEXo......",  # 17
        "....omooLLBBBpGGpBBBSSfECo......",  # 18
        "....orooLBBBBpSSpBBBBSSoo.......",  # 19
        "....omooLBBmmnGYGmmBBBSSo.......",  # 20
        "....orooLBBBBSSSSBBBBSSSo.......",  # 21
        "....omooLBBBBSSBBBBBBSSSo.......",  # 22
        "....orooLBBBBSSBBBBBBSSSo.......",  # 23
        "....omooLBBBBSSBBBBBBSSSo.......",  # 24
        "....orooGLLBBSSSSBBBBSSGo.......",  # 25
        ".....oooopBBBBSSSSBBppo.........",  # 26
        ".........oouro....ormoo.........",  # 27
        ".........oouro....ormoo.........",  # 28
        ".........ooooo....ooooo.........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 7: cast_1 (release: staff thrusts forward-outward, radiant crystal flare, cloak billows wide)
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        "..o.X.o......oHLLBSo............",  # 05
        ".oXEXEXo....oHLBBBSo............",  # 06
        "..oEXXo....oHLBBBBSo............",  # 07
        "...oECo...oHLBBBBBSSo...........",  # 08
        "....oo....oLLBBBBBSSo...........",  # 09
        "...ooo....oLkpGGYkpSo...........",  # 10
        "..oruro..oHLkpGWGkpSSo..........",  # 11
        "...omoo.oHLBBBBBBBBSSSo.........",  # 12
        "...oroo.oLBBpppppppBBSSo........",  # 13
        "...omoo..okEXkkEXko.ooo.........",  # 14
        "...ofso...oksawwqsko............",  # 15
        "...omooooLLBkwwqqkSSo...........",  # 16
        "....omooLLBBGYwwYGSSSo..........",  # 17
        "....orooLBBBBpGGpBBBSSo.........",  # 18
        "....omooLBBBBpSSpBBBBSSfoo......",  # 19
        "....orooLBBmmnGYGmmBBBSSXEo.....",  # 20
        "....omooLLBBBSSSSBBBBSSSoo......",  # 21
        "....orooLLBBBBSSBBBBBBSSSo......",  # 22
        ".....oo.oLLBBBSSBBBBBBSSSo......",  # 23
        "........oLLBBBSSBBBBBBSSSo......",  # 24
        "........oGLLBBSSSSBBBBSSGo......",  # 25
        "........oopBBBBSSSSBBBppo.......",  # 26
        ".........oouro.....ormo.........",  # 27
        ".........oouro.....ormo.........",  # 28
        ".........ooooo.....oooo.........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
]

# UP (back view): organic draped cape folds (no vertical black stripes!),
# continuous hat band, staff in right hand on viewer's RIGHT (x=24..27).
UP_FRAMES = [
    # 0: idle_0
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo............",  # 05
        "............oHLBBBSo............",  # 06
        "............oHLBBBBSo.....o.....",  # 07
        "...........oHLBBBBBSSo...oXo....",  # 08
        "...........oLLBBBBBSSo..oECo....",  # 09
        "...........oLpppppppSo..oCCo....",  # 10
        "...........oLpppppppSo...oo.....",  # 11
        "..........oHLBBBBBBBSSo..ooo....",  # 12
        "........ooHLBBBBBBBBBBSSoruro...",  # 13
        ".......oHLLBBBBBBBBBBBBSoomo....",  # 14
        "........oooLLLLBBBBSSSSo.oro....",  # 15
        "..........oLLBBBBBBSSSo..omo....",  # 16
        ".........oLLBSBBBBSSBBSo.oro....",  # 17
        "........oLLBBSBBBBSSBBSoooao....",  # 18
        "........oLBBBSBBBBSSBBBSooso....",  # 19
        "........oLBBBSBBBBSSBBBSoomo....",  # 20
        "........oLBBBSBBBBSSBBSSoooro...",  # 21
        "........oLLBBSBBBBSSBBSSo.omo...",  # 22
        "........oLLBBSBBBSSSBBSSo.oro...",  # 23
        "........oLLBBSSBBSSSBBSSo.omo...",  # 24
        "........oLLBBSSBBSSSBBSSo.oro...",  # 25
        "........oGLLBSSSSSSSBBSGo.omo...",  # 26
        ".........oopBSSSSSSppoo....oo...",  # 27
        "..........oouro..ormo...........",  # 28
        "..........ooooo..oooo...........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 1: idle_1
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo............",  # 05
        "............oHLBBBSo......o.....",  # 06
        "............oHLBBBBSo....oXo....",  # 07
        "...........oHLBBBBBSSo..oEXXo...",  # 08
        "...........oLLBBBBBSSo..oECo....",  # 09
        "...........oLpppppppSo...oo.....",  # 10
        "...........oLpppppppSo..........",  # 11
        "..........oHLBBBBBBBSSo..ooo....",  # 12
        "........ooHLBBBBBBBBBBSSoruro...",  # 13
        ".......oHLLBBBBBBBBBBBBSoomo....",  # 14
        "........oooLLLLBBBBSSSSo.oro....",  # 15
        "..........oLLBBBBBBSSSo..omo....",  # 16
        ".........oLLBSBBBBSSBBSo.oro....",  # 17
        "........oLLBBSBBBBSSBBSoooao....",  # 18
        "........oLBBBSBBBBSSBBBSooso....",  # 19
        "........oLBBBSBBBBSSBBBSoomo....",  # 20
        "........oLBBBSBBBBSSBBSSoooro...",  # 21
        "........oLLBBSBBBBSSBBSSo.omo...",  # 22
        "........oLLBBSBBBSSSBBSSo.oro...",  # 23
        "........oLLBBSSBBSSSBBSSo.omo...",  # 24
        "........oLLBBSSBBSSSBBSSo.oro...",  # 25
        "........oGLLBSSSSSSSBBSGo.omo...",  # 26
        ".........oopBSSSSSSppoo....oo...",  # 27
        "..........oouro..ormo...........",  # 28
        "..........ooooo..oooo...........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 2: walk_0 (left-foot forward/planted y=29, right-foot lifted back y=28, straight staff)
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo............",  # 05
        "............oHLBBBSo............",  # 06
        "............oHLBBBBSo.....o.....",  # 07
        "...........oHLBBBBBSSo...oXo....",  # 08
        "...........oLLBBBBBSSo..oECo....",  # 09
        "...........oLpppppppSo..oCCo....",  # 10
        "...........oLpppppppSo...oo.....",  # 11
        "..........oHLBBBBBBBSSo..ooo....",  # 12
        "........ooHLBBBBBBBBBBSSoruro...",  # 13
        ".......oHLLBBBBBBBBBBBBSoomo....",  # 14
        "........oooLLLLBBBBSSSSo.oro....",  # 15
        "..........oLLBBBBBBSSSo..omo....",  # 16
        ".........oLLBSBBBBSSBBSo.oro....",  # 17
        "........oLLBBSBBBBSSBBSoooao....",  # 18
        "........oLBBBSBBBBSSBBBSooso....",  # 19
        "........oLBBBSBBBBSSBBBSoomo....",  # 20
        "........oLBBBSBBBBSSBBSSoooro...",  # 21
        "........oLLBBSBBBBSSBBSSo.omo...",  # 22
        "........oLLBBSBBBSSSBBSSo.oro...",  # 23
        "........oLLBBSSBBSSSBBSSo.omo...",  # 24
        "........oLLBBSSBBSSSBBSSo.oro...",  # 25
        "........oGLLBSSSSSSSBBSGo.omo...",  # 26
        "........oopBSSSSSSormoo....oo...",  # 27
        ".........oouroo..oooo...........",  # 28
        ".........ooooo..................",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 3: walk_1 (passing A, +1px bounce, left boot planted y=29)
    [
        "................................",  # 00
        ".................oooo...........",  # 01
        "................oHLBSo..........",  # 02
        "..............ooHLBBo...........",  # 03
        ".............oHLLBSo............",  # 04
        "............oHLBBBSo............",  # 05
        "............oHLBBBBSo.....o.....",  # 06
        "...........oHLBBBBBSSo...oXo....",  # 07
        "...........oLLBBBBBSSo..oECo....",  # 08
        "...........oLpppppppSo..oCCo....",  # 09
        "...........oLpppppppSo...oo.....",  # 10
        "..........oHLBBBBBBBSSo..ooo....",  # 11
        "........ooHLBBBBBBBBBBSSoruro...",  # 12
        ".......oHLLBBBBBBBBBBBBSoomo....",  # 13
        "........oooLLLLBBBBSSSSo.oro....",  # 14
        "..........oLLBBBBBBSSSo..omo....",  # 15
        ".........oLLBSBBBBSSBBSo.oro....",  # 16
        "........oLLBBSBBBBSSBBSoooao....",  # 17
        "........oLBBBSBBBBSSBBBSooso....",  # 18
        "........oLBBBSBBBBSSBBBSoomo....",  # 19
        "........oLBBBSBBBBSSBBSSoooro...",  # 20
        "........oLLBBSBBBBSSBBSSo.omo...",  # 21
        "........oLLBBSBBBSSSBBSSo.oro...",  # 22
        "........oLLBBSSBBSSSBBSSo.omo...",  # 23
        "........oLLBBSSBBSSSBBSSo.oro...",  # 24
        "........oGLLBSSSSSSSBBSGo.omo...",  # 25
        ".........oopBSSSSSSppoo....oo...",  # 26
        "..........oouro..ormo...........",  # 27
        "..........oouro..oooo...........",  # 28
        "..........ooooo.................",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 4: walk_2 (opposing right-foot forward/planted y=29, left-foot lifted back y=28, straight staff)
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo............",  # 05
        "............oHLBBBSo............",  # 06
        "............oHLBBBBSo.....o.....",  # 07
        "...........oHLBBBBBSSo...oXo....",  # 08
        "...........oLLBBBBBSSo..oECo....",  # 09
        "...........oLpppppppSo..oCCo....",  # 10
        "...........oLpppppppSo...oo.....",  # 11
        "..........oHLBBBBBBBSSo..ooo....",  # 12
        "........ooHLBBBBBBBBBBSSoruro...",  # 13
        ".......oHLLBBBBBBBBBBBBSoomo....",  # 14
        "........oooLLLLBBBBSSSSo.oro....",  # 15
        "..........oLLBBBBBBSSSo..omo....",  # 16
        ".........oLLBSBBBBSSBBSo.oro....",  # 17
        "........oLLBBSBBBBSSBBSoooao....",  # 18
        "........oLBBBSBBBBSSBBBSooso....",  # 19
        "........oLBBBSBBBBSSBBBSoomo....",  # 20
        "........oLBBBSBBBBSSBBSSoooro...",  # 21
        "........oLLBBSBBBBSSBBSSo.omo...",  # 22
        "........oLLBBSBBBSSSBBSSo.oro...",  # 23
        "........oLLBBSSBBSSSBBSSo.omo...",  # 24
        "........oLLBBSSBBSSSBBSSo.oro...",  # 25
        "........oGLLBSSSSSSSBBSGo.omo...",  # 26
        ".........oouroSSSSSSppoo...oo...",  # 27
        ".........oooo...oormoo..........",  # 28
        "................ooooo...........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 5: walk_3 (passing B, +1px bounce, right boot planted y=29)
    [
        "................................",  # 00
        ".................oooo...........",  # 01
        "................oHLBSo..........",  # 02
        "..............ooHLBBo...........",  # 03
        ".............oHLLBSo............",  # 04
        "............oHLBBBSo............",  # 05
        "............oHLBBBBSo.....o.....",  # 06
        "...........oHLBBBBBSSo...oXo....",  # 07
        "...........oLLBBBBBSSo..oECo....",  # 08
        "...........oLpppppppSo..oCCo....",  # 09
        "...........oLpppppppSo...oo.....",  # 10
        "..........oHLBBBBBBBSSo..ooo....",  # 11
        "........ooHLBBBBBBBBBBSSoruro...",  # 12
        ".......oHLLBBBBBBBBBBBBSoomo....",  # 13
        "........oooLLLLBBBBSSSSo.oro....",  # 14
        "..........oLLBBBBBBSSSo..omo....",  # 15
        ".........oLLBSBBBBSSBBSo.oro....",  # 16
        "........oLLBBSBBBBSSBBSoooao....",  # 17
        "........oLBBBSBBBBSSBBBSooso....",  # 18
        "........oLBBBSBBBBSSBBBSoomo....",  # 19
        "........oLBBBSBBBBSSBBSSoooro...",  # 20
        "........oLLBBSBBBBSSBBSSo.omo...",  # 21
        "........oLLBBSBBBSSSBBSSo.oro...",  # 22
        "........oLLBBSSBBSSSBBSSo.omo...",  # 23
        "........oLLBBSSBBSSSBBSSo.oro...",  # 24
        "........oGLLBSSSSSSSBBSGo.omo...",  # 25
        ".........oopBSSSSSSppoo....oo...",  # 26
        "..........oouro..ormo...........",  # 27
        "..........oooo...ormo...........",  # 28
        ".................oooo...........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 6: cast_0 (windup: staff raises right, crystal charges, left sleeve lifts with arcane spark)
    [
        "................................",  # 00
        ".................oooo...........",  # 01
        "................oHLBSo..........",  # 02
        "..............ooHLBBo...........",  # 03
        ".............oHLLBSo............",  # 04
        "............oHLBBBSo......o.....",  # 05
        "............oHLBBBBSo....oXo....",  # 06
        "...........oHLBBBBBSSo..oEXXo...",  # 07
        "...........oLLBBBBBSSo...oECo...",  # 08
        "...........oLpppppppSo....oo....",  # 09
        "...........oLpppppppSo...ooo....",  # 10
        "..........oHLBBBBBBBSSo.oruro...",  # 11
        "........ooHLBBBBBBBBBBSSo.omo...",  # 12
        ".......oHLLBBBBBBBBBBBBSo.oro...",  # 13
        "........oooLLLLBBBBSSSSo..omo...",  # 14
        "..........oLLBBBBBBSSSo...oro...",  # 15
        "....oo...oLLBSBBBBSSBBSo.oaso...",  # 16
        "...oEXo.oLLBBSBBBBSSBBBSooomo...",  # 17
        "...oECfoLLBBBSBBBBSSBBBSo.oro...",  # 18
        "....oooLLBBBBSBBBBSSBBBSo.omo...",  # 19
        ".......oLLBBBSBBBBSSBBBSo..oo...",  # 20
        ".......oLLBBBSBBBBSSBBBSo.......",  # 21
        ".......oLLBBBSBBBSSSBBBSo.......",  # 22
        ".......oLLBBBSSBBSSSBBBSo.......",  # 23
        ".......oLLBBBSSBBSSSBBBSo.......",  # 24
        ".......oGLLBBSSSSSSSBBSGo.......",  # 25
        "........oopBSSSSSSSBppoo........",  # 26
        ".........oouro....ormo..........",  # 27
        ".........oouro....ormo..........",  # 28
        ".........ooooo....oooo..........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 7: cast_1 (release: staff thrusts right with crystal flare, cape billows left)
    [
        "................................",  # 00
        "................................",  # 01
        ".................oooo...........",  # 02
        "................oHLBSo..........",  # 03
        "..............ooHLBBo...........",  # 04
        ".............oHLLBSo......o.X.o.",  # 05
        "............oHLBBBSo.....oXEXEXo",  # 06
        "............oHLBBBBSo.....oEXXo.",  # 07
        "...........oHLBBBBBSSo....oECo..",  # 08
        "...........oLLBBBBBSSo.....oo...",  # 09
        "...........oLpppppppSo....ooo...",  # 10
        "..........oHLpppppppSSo..oruro..",  # 11
        "........ooHLBBBBBBBBBBSSo.omo...",  # 12
        ".......oHLLBBBBBBBBBBBBSo.oro...",  # 13
        "........oooLLLLBBBBSSSSo..omo...",  # 14
        "..........oLLBBBBBBSSSo...oro...",  # 15
        ".........oLLBSBBBBSSBBSo..oaso..",  # 16
        "........oLLBBSBBBBSSBBBSo.omo...",  # 17
        ".......oLLBBBSBBBBSSBBBSo.oro...",  # 18
        "......oLLBBBBSBBBBSSBBBSo.omo...",  # 19
        "......oLLBBBBSBBBBSSBBBSo..oo...",  # 20
        "......oLLBBBBSBBBBSSBBBSo.......",  # 21
        ".....oLLBBBBBSBBBSSSBBBSo.......",  # 22
        ".....oLLBBBBBSSBBSSSBBBSo.......",  # 23
        ".....oLLBBBBBSSBBSSSBBBSo.......",  # 24
        ".....oGLLBBBBSSSSSSSBBSGo.......",  # 25
        "......oopBBBBSSSSSSBBppoo.......",  # 26
        ".........oouro....ormo..........",  # 27
        ".........oouro....ormo..........",  # 28
        ".........ooooo....oooo..........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
]

# RIGHT (facing +X): foreground sapphire sleeve reaches smoothly across torso (y=17..19)
# into right hand gripping staff (x=22..24), crisp leather boots in walk strides!
RIGHT_FRAMES = [
    # 0: idle_0
    [
        "................................",  # 00
        "................................",  # 01
        "..........oooo..................",  # 02
        ".........oHLBSo.................",  # 03
        "..........oHLBBo................",  # 04
        "...........oHLBBo...............",  # 05
        "...........oHLBBSo..............",  # 06
        "..........oHLBBBBSo......o......",  # 07
        "..........oLLBBBBSo.....oXo.....",  # 08
        ".........oHLBBBBBSSo...oECo.....",  # 09
        ".........oLpppGGYppSo..oCCo.....",  # 10
        ".........oLpppGWGppSo...oo......",  # 11
        "........oHLBBBBBBBBSSo..ooo.....",  # 12
        ".......oHLLBppppppBBBSSoruro....",  # 13
        "........oooLLkpppppkSSSoomo.....",  # 14
        "..........oLLkkEXsskoooo.ro.....",  # 15
        ".........oLLBkksswwwo....mo.....",  # 16
        "........oLLBBBGkwwqqo...oro.....",  # 17
        ".......oLLBBBBGYwwLBBSoosfo.....",  # 18
        "......oLLBBBBBBpGGkBBBoomoo.....",  # 19
        "......oLLBBBBBBpSSBBBoo.ro......",  # 20
        "......oLBBBBBBmGYGmmBo..mo......",  # 21
        ".......oLLBBBBSSSSBBSo..ro......",  # 22
        ".......oLLBBBBSSBBBSSo..mo......",  # 23
        ".......oLLBBBBSSBBBSSo..ro......",  # 24
        ".......oLLBBBBSSBBBSSo..mo......",  # 25
        "........oGLLBBSSSSBSo...ro......",  # 26
        ".........oopBSSSSppo....mo......",  # 27
        "..........oouroormo......oo.....",  # 28
        "..........ooooooooo.............",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 1: idle_1
    [
        "................................",  # 00
        "................................",  # 01
        "..........oooo..................",  # 02
        ".........oHLBSo.................",  # 03
        "..........oHLBBo................",  # 04
        "...........oHLBBo...............",  # 05
        "...........oHLBBSo.......o......",  # 06
        "..........oHLBBBBSo.....oXo.....",  # 07
        "..........oLLBBBBSo....oEXXo....",  # 08
        ".........oHLBBBBBSSo...oECo.....",  # 09
        ".........oLpppGGYppSo...oo......",  # 10
        ".........oLpppGWGppSo...........",  # 11
        "........oHLBBBBBBBBSSo..ooo.....",  # 12
        ".......oHLLBppppppBBBSSoruro....",  # 13
        "........oooLLkpppppkSSSoomo.....",  # 14
        "..........oLLkkEXsskoooo.ro.....",  # 15
        ".........oLLBkksswwwo....mo.....",  # 16
        "........oLLBBBGkwwqqo...oro.....",  # 17
        ".......oLLBBBBGYwwLBBSoosfo.....",  # 18
        "......oLLBBBBBBpGGkBBBoomoo.....",  # 19
        "......oLLBBBBBBpSSBBBoo.ro......",  # 20
        "......oLBBBBBBmGYGmmBo..mo......",  # 21
        ".......oLLBBBBSSSSBBSo..ro......",  # 22
        ".......oLLBBBBSSBBBSSo..mo......",  # 23
        ".......oLLBBBBSSBBBSSo..ro......",  # 24
        ".......oLLBBBBSSBBBSSo..mo......",  # 25
        "........oGLLBBSSSSBSo...ro......",  # 26
        ".........oopBSSSSppo....mo......",  # 27
        "..........oouroormo......oo.....",  # 28
        "..........ooooooooo.............",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 2: walk_0 (front/right leg planted forward y=29, rear/left leg striding back y=28)
    [
        "................................",  # 00
        "................................",  # 01
        "..........oooo..................",  # 02
        ".........oHLBSo.................",  # 03
        "..........oHLBBo................",  # 04
        "...........oHLBBo...............",  # 05
        "...........oHLBBSo..............",  # 06
        "..........oHLBBBBSo......o......",  # 07
        "..........oLLBBBBSo.....oXo.....",  # 08
        ".........oHLBBBBBSSo...oECo.....",  # 09
        ".........oLpppGGYppSo..oCCo.....",  # 10
        ".........oLpppGWGppSo...oo......",  # 11
        "........oHLBBBBBBBBSSo..ooo.....",  # 12
        ".......oHLLBppppppBBBSSoruro....",  # 13
        "........oooLLkpppppkSSSoomo.....",  # 14
        "..........oLLkkEXsskoooo.ro.....",  # 15
        ".........oLLBkksswwwo....mo.....",  # 16
        "........oLLBBBGkwwqqo...oro.....",  # 17
        ".......oLLBBBBGYwwLBBSoosfo.....",  # 18
        "......oLLBBBBBBpGGkBBBoomoo.....",  # 19
        "......oLLBBBBBBpSSBBBoo.ro......",  # 20
        "......oLBBBBBBmGYGmmBo..mo......",  # 21
        "......oLLBBBBBSSSSBBSo..ro......",  # 22
        "......oLLBBBBBSSBBBSSo..mo......",  # 23
        ".....oLLBBBBBBSSBBBSSo..ro......",  # 24
        ".....oLLBBBBBBSSBBBSSo..mo......",  # 25
        "......oGLLBBBBSSSSBSo...ro......",  # 26
        ".......oopuooBSSSSpo....mo......",  # 27
        "........oooo..oormoo.....oo.....",  # 28
        "..............ooooo.............",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 3: walk_1 (passing A, +1px bounce, front boot planted y=29)
    [
        "................................",  # 00
        "..........oooo..................",  # 01
        ".........oHLBSo.................",  # 02
        "..........oHLBBo................",  # 03
        "...........oHLBBo...............",  # 04
        "...........oHLBBSo..............",  # 05
        "..........oHLBBBBSo......o......",  # 06
        "..........oLLBBBBSo.....oXo.....",  # 07
        ".........oHLBBBBBSSo...oECo.....",  # 08
        ".........oLpppGGYppSo..oCCo.....",  # 09
        ".........oLpppGWGppSo...oo......",  # 10
        "........oHLBBBBBBBBSSo..ooo.....",  # 11
        ".......oHLLBppppppBBBSSoruro....",  # 12
        "........oooLLkpppppkSSSoomo.....",  # 13
        "..........oLLkkEXsskoooo.ro.....",  # 14
        ".........oLLBkksswwwo....mo.....",  # 15
        "........oLLBBBGkwwqqo...oro.....",  # 16
        ".......oLLBBBBGYwwLBBSoosfo.....",  # 17
        "......oLLBBBBBBpGGkBBBoomoo.....",  # 18
        "......oLLBBBBBBpSSBBBoo.ro......",  # 19
        "......oLBBBBBBmGYGmmBo..mo......",  # 20
        ".......oLLBBBBSSSSBBSo..ro......",  # 21
        ".......oLLBBBBSSBBBSSo..mo......",  # 22
        ".......oLLBBBBSSBBBSSo..ro......",  # 23
        ".......oLLBBBBSSBBBSSo..mo......",  # 24
        "........oGLLBBSSSSBSo...ro......",  # 25
        ".........oopBSSSSppo....mo......",  # 26
        "..........oouroormo......oo.....",  # 27
        "..........ooooormoo.............",  # 28
        "..............ooooo.............",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 4: walk_2 (opposing stride: rear leg planted under left/back y=29, front leg kicked forward/lifted y=28)
    [
        "................................",  # 00
        "................................",  # 01
        "..........oooo..................",  # 02
        ".........oHLBSo.................",  # 03
        "..........oHLBBo................",  # 04
        "...........oHLBBo...............",  # 05
        "...........oHLBBSo..............",  # 06
        "..........oHLBBBBSo......o......",  # 07
        "..........oLLBBBBSo.....oXo.....",  # 08
        ".........oHLBBBBBSSo...oECo.....",  # 09
        ".........oLpppGGYppSo..oCCo.....",  # 10
        ".........oLpppGWGppSo...oo......",  # 11
        "........oHLBBBBBBBBSSo..ooo.....",  # 12
        ".......oHLLBppppppBBBSSoruro....",  # 13
        "........oooLLkpppppkSSSoomo.....",  # 14
        "..........oLLkkEXsskoooo.ro.....",  # 15
        ".........oLLBkksswwwo....mo.....",  # 16
        "........oLLBBBGkwwqqo...oro.....",  # 17
        ".......oLLBBBBGYwwLBBSoosfo.....",  # 18
        "......oLLBBBBBBpGGkBBBoomoo.....",  # 19
        "......oLLBBBBBBpSSBBBoo.ro......",  # 20
        "......oLBBBBBBmGYGmmBo..mo......",  # 21
        ".......oLLBBBBSSSSBBSo..ro......",  # 22
        ".......oLLBBBBSSBBBSSo..mo......",  # 23
        ".......oLLBBBBSSBBBBSSo.ro......",  # 24
        ".......oLLBBBBSSBBBBSSo.mo......",  # 25
        "........oGLLBBSSSSBBSGo.ro......",  # 26
        ".........oouroppBormoo..mo......",  # 27
        ".........oouro....oooo...oo.....",  # 28
        ".........ooooo..................",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 5: walk_3 (passing B, +1px bounce, rear boot planted y=29)
    [
        "................................",  # 00
        "..........oooo..................",  # 01
        ".........oHLBSo.................",  # 02
        "..........oHLBBo................",  # 03
        "...........oHLBBo...............",  # 04
        "...........oHLBBSo..............",  # 05
        "..........oHLBBBBSo......o......",  # 06
        "..........oLLBBBBSo.....oXo.....",  # 07
        ".........oHLBBBBBSSo...oECo.....",  # 08
        ".........oLpppGGYppSo..oCCo.....",  # 09
        ".........oLpppGWGppSo...oo......",  # 10
        "........oHLBBBBBBBBSSo..ooo.....",  # 11
        ".......oHLLBppppppBBBSSoruro....",  # 12
        "........oooLLkpppppkSSSoomo.....",  # 13
        "..........oLLkkEXsskoooo.ro.....",  # 14
        ".........oLLBkksswwwo....mo.....",  # 15
        "........oLLBBBGkwwqqo...oro.....",  # 16
        ".......oLLBBBBGYwwLBBSoosfo.....",  # 17
        "......oLLBBBBBBpGGkBBBoomoo.....",  # 18
        "......oLLBBBBBBpSSBBBoo.ro......",  # 19
        "......oLBBBBBBmGYGmmBo..mo......",  # 20
        ".......oLLBBBBSSSSBBSo..ro......",  # 21
        ".......oLLBBBBSSBBBSSo..mo......",  # 22
        ".......oLLBBBBSSBBBSSo..ro......",  # 23
        ".......oLLBBBBSSBBBSSo..mo......",  # 24
        "........oGLLBBSSSSBSo...ro......",  # 25
        ".........oopBSSSSppo....mo......",  # 26
        "..........oouroormo......oo.....",  # 27
        "..........oourooooo.............",  # 28
        "..........ooooo.................",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 6: cast_0 (windup: brim tilts up, foreground sleeve lifts staff, crystal charges)
    [
        "................................",  # 00
        "..........oooo..................",  # 01
        ".........oHLBSo.................",  # 02
        "..........oHLBBo................",  # 03
        "...........oHLBBo...............",  # 04
        "...........oHLBBSo.......o......",  # 05
        "..........oHLBBBBSo.....oXo.....",  # 06
        "..........oLLBBBBSo....oEXXo....",  # 07
        ".........oHLBBBBBSSo....oECo....",  # 08
        ".........oLpppGGYppSo....oo.....",  # 09
        ".........oLpppGWGppSo...ooo.....",  # 10
        "........oHLBBBBBBBBSSo.oruro....",  # 11
        ".......oHLLBppppppBBBSSo.omo....",  # 12
        "........oooLLkpppppkSSSo.oro....",  # 13
        "..........oLLkkXXsskooo..omo....",  # 14
        ".........oLLBkksswwwo....oro....",  # 15
        "........oLLBBBGkwwqqoo..osfo....",  # 16
        ".......oLLBBBBGYwwLBBSooomoo....",  # 17
        "......oLLBBBBBBpGGkBBBSoooo.....",  # 18
        "......oLLBBBBBBpSSBBBo..omo.....",  # 19
        "......oLBBBBBBmGYGmmBo...oo.....",  # 20
        ".......oLLBBBBSSSSBBSo..........",  # 21
        ".......oLLBBBBSSBBBSSo..........",  # 22
        ".......oLLBBBBSSBBBSSo..........",  # 23
        ".......oLLBBBBSSBBBSSo..........",  # 24
        ".......oGLLBBBSSSSBSo...........",  # 25
        "........oopBBBSSSSpo............",  # 26
        ".........oouro...ormo...........",  # 27
        ".........oouro...ormo...........",  # 28
        ".........ooooo...oooo...........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 7: cast_1 (release: foreground sleeve extends staff boldly right, radiant flare, cape billows left)
    [
        "................................",  # 00
        "................................",  # 01
        "..........oooo..................",  # 02
        ".........oHLBSo.................",  # 03
        "..........oHLBBo................",  # 04
        "...........oHLBBo........o.X.o..",  # 05
        "...........oHLBBSo......oXEXEXo.",  # 06
        "..........oHLBBBBSo......oEXXo..",  # 07
        "..........oLLBBBBSSo.....oECo...",  # 08
        ".........oHLBBBBBBSSo......oo...",  # 09
        ".........oLpppGGYppSo.....ooo...",  # 10
        "........oHLpppGWGppSSo...oruro..",  # 11
        ".......oHLLBBBBBBBBSSSo...omo...",  # 12
        "........oooLLkpppppkSSSo..oro...",  # 13
        "..........oLLkkEXsskoooo..omo...",  # 14
        ".........oLLBkksswwwo....osfo...",  # 15
        "........oLLBBBGkwwqqoLBBoomoo...",  # 16
        ".......oLLBBBBGYwwLBBBBSo.oro...",  # 17
        "......oLLBBBBBBpGGkBBBSo..omo...",  # 18
        ".....oLLBBBBBBBpSSBBBo.....oo...",  # 19
        "....oLLBBBBBBBmGYGmmBo..........",  # 20
        "....oLLBBBBBBBSSSSBBSo..........",  # 21
        "...oLLBBBBBBBBSSBBBSSo..........",  # 22
        "...oLLBBBBBBBBSSBBBSSo..........",  # 23
        "...oLLBBBBBBBBSSBBBSSo..........",  # 24
        "....oGLLBBBBBBSSSSBSo...........",  # 25
        ".....oopBBBBBBSSSSpo............",  # 26
        ".........oouro....ormo..........",  # 27
        ".........oouro....ormo..........",  # 28
        ".........ooooo....oooo..........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
]

# LEFT (facing -X): mirrored silhouette proportions, consistent RIGHT-HAND staff
# reaching forward on the left (x=5..10), connected cleanly by far sleeve/hand!
# Near left arm rests at side (x=15..17). Zero detached pixels!
LEFT_FRAMES = [
    # 0: idle_0
    [
        "................................",  # 00
        "................................",  # 01
        "..................oooo..........",  # 02
        ".................oHLBSo.........",  # 03
        "................oHLBBo..........",  # 04
        "...............oHLBBo...........",  # 05
        "..............oHLBBSo...........",  # 06
        "......o......oHLBBBSo...........",  # 07
        ".....oXo....oHLBBBBSSo..........",  # 08
        ".....oECo..oHLBBBBBSSo..........",  # 09
        ".....oCCo..oLppGGYpppSo.........",  # 10
        "......oo...oLppGWGpppSo.........",  # 11
        ".....ooo..oHLBBBBBBBBSSo........",  # 12
        "....orurooHLBBBppppppBBSSo......",  # 13
        ".....omooHLkpppppkSSSoooo.......",  # 14
        ".....or.ooookssXEkkSSo..........",  # 15
        ".....om....owwwsskkBSo..........",  # 16
        ".....oro...oqqwwkGBBSSo.........",  # 17
        ".....ofsoooSSwwYGLLBBBSo........",  # 18
        ".....oomooBBBGpLLBBBBBSo........",  # 19
        "......or.ooBBBpLLBBBBBSSo.......",  # 20
        "......om..oBmmGYGmmBBBBSo.......",  # 21
        "......or..oSBBSSSSLLBBSo........",  # 22
        "......om..oSSBBBSSLLBBSo........",  # 23
        "......or..oSSBBBSSLLBBSo........",  # 24
        "......om..oSSBBBSSLLBBSo........",  # 25
        "......or...oSBSSSSLLBGo.........",  # 26
        "......om....oppSSSSpoo..........",  # 27
        ".....oo......omroouroo..........",  # 28
        ".............ooooooooo..........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 1: idle_1
    [
        "................................",  # 00
        "................................",  # 01
        "..................oooo..........",  # 02
        ".................oHLBSo.........",  # 03
        "................oHLBBo..........",  # 04
        "...............oHLBBo...........",  # 05
        "......o.......oHLBBSo...........",  # 06
        ".....oXo.....oHLBBBSo...........",  # 07
        "....oEXXo...oHLBBBBSSo..........",  # 08
        ".....oECo..oHLBBBBBSSo..........",  # 09
        "......oo...oLppGGYpppSo.........",  # 10
        "...........oLppGWGpppSo.........",  # 11
        ".....ooo..oHLBBBBBBBBSSo........",  # 12
        "....orurooHLBBBppppppBBSSo......",  # 13
        ".....omooHLkpppppkSSSoooo.......",  # 14
        ".....or.ooookssXEkkSSo..........",  # 15
        ".....om....owwwsskkBSo..........",  # 16
        ".....oro...oqqwwkGBBSSo.........",  # 17
        ".....ofsoooSSwwYGLLBBBSo........",  # 18
        ".....oomooBBBGpLLBBBBBSo........",  # 19
        "......or.ooBBBpLLBBBBBSSo.......",  # 20
        "......om..oBmmGYGmmBBBBSo.......",  # 21
        "......or..oSBBSSSSLLBBSo........",  # 22
        "......om..oSSBBBSSLLBBSo........",  # 23
        "......or..oSSBBBSSLLBBSo........",  # 24
        "......om..oSSBBBSSLLBBSo........",  # 25
        "......or...oSBSSSSLLBGo.........",  # 26
        "......om....oppSSSSpoo..........",  # 27
        ".....oo......omroouroo..........",  # 28
        ".............ooooooooo..........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 2: walk_0 (front/left leg planted forward-left y=29, rear leg back y=28)
    [
        "................................",  # 00
        "................................",  # 01
        "..................oooo..........",  # 02
        ".................oHLBSo.........",  # 03
        "................oHLBBo..........",  # 04
        "...............oHLBBo...........",  # 05
        "..............oHLBBSo...........",  # 06
        "......o......oHLBBBSo...........",  # 07
        ".....oXo....oHLBBBBSSo..........",  # 08
        ".....oECo..oHLBBBBBSSo..........",  # 09
        ".....oCCo..oLppGGYpppSo.........",  # 10
        "......oo...oLppGWGpppSo.........",  # 11
        ".....ooo..oHLBBBBBBBBSSo........",  # 12
        "....orurooHLBBBppppppBBSSo......",  # 13
        ".....omooHLkpppppkSSSoooo.......",  # 14
        ".....or.ooookssXEkkSSo..........",  # 15
        ".....om....owwwsskkBSo..........",  # 16
        ".....oro...oqqwwkGBBSSo.........",  # 17
        ".....ofsoooSSwwYGLLBBBSo........",  # 18
        ".....oomooBBBGpLLBBBBBSo........",  # 19
        "......or.ooBBBpLLBBBBBSSo.......",  # 20
        "......om..oBmmGYGmmBBBBSo.......",  # 21
        "......or..oSBBSSSSLLBBBSo.......",  # 22
        "......om..oSSBBBSSLLBBBSo.......",  # 23
        "......or..oSSBBBSSLLBBBBSo......",  # 24
        "......om..oSSBBBSSLLBBBBSo......",  # 25
        "......or...oSBSSSSLLBBBGo.......",  # 26
        "......om....opSSSSBooruo........",  # 27
        ".....oo.....oomroo..oooo........",  # 28
        ".............ooooo..............",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 3: walk_1 (passing A, +1px bounce, front boot planted y=29)
    [
        "................................",  # 00
        "..................oooo..........",  # 01
        ".................oHLBSo.........",  # 02
        "................oHLBBo..........",  # 03
        "...............oHLBBo...........",  # 04
        "..............oHLBBSo...........",  # 05
        "......o......oHLBBBSo...........",  # 06
        ".....oXo....oHLBBBBSSo..........",  # 07
        ".....oECo..oHLBBBBBSSo..........",  # 08
        ".....oCCo..oLppGGYpppSo.........",  # 09
        "......oo...oLppGWGpppSo.........",  # 10
        ".....ooo..oHLBBBBBBBBSSo........",  # 11
        "....orurooHLBBBppppppBBSSo......",  # 12
        ".....omooHLkpppppkSSSoooo.......",  # 13
        ".....or.ooookssXEkkSSo..........",  # 14
        ".....om....owwwsskkBSo..........",  # 15
        ".....oro...oqqwwkGBBSSo.........",  # 16
        ".....ofsoooSSwwYGLLBBBSo........",  # 17
        ".....oomooBBBGpLLBBBBBSo........",  # 18
        "......or.ooBBBpLLBBBBBSSo.......",  # 19
        "......om..oBmmGYGmmBBBBSo.......",  # 20
        "......or..oSBBSSSSLLBBSo........",  # 21
        "......om..oSSBBBSSLLBBSo........",  # 22
        "......or..oSSBBBSSLLBBSo........",  # 23
        "......om..oSSBBBSSLLBBSo........",  # 24
        "......or...oSBSSSSLLBGo.........",  # 25
        "......om....oppSSSSpoo..........",  # 26
        ".....oo......omroouroo..........",  # 27
        ".............oomrooooo..........",  # 28
        ".............ooooo..............",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 4: walk_2 (opposing stride: rear leg planted under right/back y=29, front leg kicked/lifted y=28)
    [
        "................................",  # 00
        "................................",  # 01
        "..................oooo..........",  # 02
        ".................oHLBSo.........",  # 03
        "................oHLBBo..........",  # 04
        "...............oHLBBo...........",  # 05
        "..............oHLBBSo...........",  # 06
        "......o......oHLBBBSo...........",  # 07
        ".....oXo....oHLBBBBSSo..........",  # 08
        ".....oECo..oHLBBBBBSSo..........",  # 09
        ".....oCCo..oLppGGYpppSo.........",  # 10
        "......oo...oLppGWGpppSo.........",  # 11
        ".....ooo..oHLBBBBBBBBSSo........",  # 12
        "....orurooHLBBBppppppBBSSo......",  # 13
        ".....omooHLkpppppkSSSoooo.......",  # 14
        ".....or.ooookssXEkkSSo..........",  # 15
        ".....om....owwwsskkBSo..........",  # 16
        ".....oro...oqqwwkGBBSSo.........",  # 17
        ".....ofsoooSSwwYGLLBBBSo........",  # 18
        ".....oomooBBBGpLLBBBBBSo........",  # 19
        "......or.ooBBBpLLBBBBBSSo.......",  # 20
        "......om..oBmmGYGmmBBBBSo.......",  # 21
        "......or..oSBBSSSSLLBBSo........",  # 22
        "......om..oSSBBBSSLLBBSo........",  # 23
        "......or.oSSSBBBSSLLBBSo........",  # 24
        "......om.oSSSBBBSSLLBBSo........",  # 25
        "......or..oGSBSSSSLLBGo.........",  # 26
        "......om..oomroSppouroo.........",  # 27
        ".....oo...oooo....ouroo.........",  # 28
        "..................ooooo.........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 5: walk_3 (passing B, +1px bounce, rear boot planted y=29)
    [
        "................................",  # 00
        "..................oooo..........",  # 01
        ".................oHLBSo.........",  # 02
        "................oHLBBo..........",  # 03
        "...............oHLBBo...........",  # 04
        "..............oHLBBSo...........",  # 05
        "......o......oHLBBBSo...........",  # 06
        ".....oXo....oHLBBBBSSo..........",  # 07
        ".....oECo..oHLBBBBBSSo..........",  # 08
        ".....oCCo..oLppGGYpppSo.........",  # 09
        "......oo...oLppGWGpppSo.........",  # 10
        ".....ooo..oHLBBBBBBBBSSo........",  # 11
        "....orurooHLBBBppppppBBSSo......",  # 12
        ".....omooHLkpppppkSSSoooo.......",  # 13
        ".....or.ooookssXEkkSSo..........",  # 14
        ".....om....owwwsskkBSo..........",  # 15
        ".....oro...oqqwwkGBBSSo.........",  # 16
        ".....ofsoooSSwwYGLLBBBSo........",  # 17
        ".....oomooBBBGpLLBBBBBSo........",  # 18
        "......or.ooBBBpLLBBBBBSSo.......",  # 19
        "......om..oBmmGYGmmBBBBSo.......",  # 20
        "......or..oSBBSSSSLLBBSo........",  # 21
        "......om..oSSBBBSSLLBBSo........",  # 22
        "......or..oSSBBBSSLLBBSo........",  # 23
        "......om..oSSBBBSSLLBBSo........",  # 24
        "......or...oSBSSSSLLBGo.........",  # 25
        "......om....oppSSSSpoo..........",  # 26
        ".....oo......omroouroo..........",  # 27
        ".............ooooouroo..........",  # 28
        ".................ooooo..........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 6: cast_0 (windup: brim lifts, far sleeve connects cleanly to hand raising staff)
    [
        "................................",  # 00
        "..................oooo..........",  # 01
        ".................oHLBSo.........",  # 02
        "................oHLBBo..........",  # 03
        "...............oHLBBo...........",  # 04
        "......o.......oHLBBSo...........",  # 05
        ".....oXo.....oHLBBBSo...........",  # 06
        "....oEXXo...oHLBBBBSSo..........",  # 07
        "....oECo...oHLBBBBBSSo..........",  # 08
        ".....oo....oLppGGYpppSo.........",  # 09
        "....ooo....oLppGWGpppSo.........",  # 10
        "...oruro..oHLBBBBBBBBSSo........",  # 11
        "....omo..oHLBBBppppppBBSSo......",  # 12
        "....oro...oHLkpppppkSSSooo......",  # 13
        "....omo....ookssXXkkSSo.........",  # 14
        "....oro.....owwwsskkBSo.........",  # 15
        "....ofso...ooqqwwkGBBSSo........",  # 16
        "....oomooooSSwwYGLLBBBSo........",  # 17
        ".....orooBBBBGpLLBBBBBSo........",  # 18
        ".....omo..oBBBpLLBBBBBSSo.......",  # 19
        "......oo..oBmmGYGmmBBBBSo.......",  # 20
        "..........oSBBSSSSLLBBSo........",  # 21
        "..........oSSBBBSSLLBBSo........",  # 22
        "..........oSSBBBSSLLBBSo........",  # 23
        "..........oSSBBBSSLLBBSo........",  # 24
        "...........oSBSSSSLLBBGo........",  # 25
        "...........oopSSSSBppoo.........",  # 26
        "..........omro....ouroo.........",  # 27
        "..........omro....ouroo.........",  # 28
        "..........oooo....ooooo.........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
    # 7: cast_1 (release: far sleeve & hand thrust staff boldly left, radiant crystal burst, cape billows right)
    [
        "................................",  # 00
        "................................",  # 01
        "..................oooo..........",  # 02
        ".................oHLBSo.........",  # 03
        "................oHLBBo..........",  # 04
        "..o.X.o........oHLBBo...........",  # 05
        ".oXEXEXo......oHLBBSo...........",  # 06
        "..oEXXo......oHLBBBSo...........",  # 07
        "..oECo......oHLBBBBSSo..........",  # 08
        "...oo......oHLBBBBBBSSo.........",  # 09
        "..ooo......oLppGGYpppSo.........",  # 10
        ".oruro....oHLppGWGpppSSo........",  # 11
        "..omo....oHLBBBBBBBBSSSo........",  # 12
        "..oro.....oHLkpppppkSSSooo......",  # 13
        "..omo.....ooookssEXkkSSo........",  # 14
        "..ofso......owwwsskkBSo.........",  # 15
        "..oomooBBBooqqwwkGBBSSo.........",  # 16
        "...orooBBBBSSwwYGLLBBBSo........",  # 17
        "...omooBBBBBGpLLBBBBBBSo........",  # 18
        "....oo....oBBBpLLBBBBBSSo.......",  # 19
        "..........oBmmGYGmmBBBBSSo......",  # 20
        "..........oSBBSSSSLLBBBBSSSo....",  # 21
        "..........oSSBBBSSLLBBBBBSSSo...",  # 22
        "..........oSSBBBSSLLBBBBBSSSo...",  # 23
        "..........oSSBBBSSLLBBBBBSSSo...",  # 24
        "...........oSBSSSSLLBBBBBBGo....",  # 25
        "...........oopSSSSBBBBBBppo.....",  # 26
        "..........omro.....ouroo........",  # 27
        "..........omro.....ouroo........",  # 28
        "..........oooo.....ooooo........",  # 29
        "................................",  # 30
        "................................",  # 31
    ],
]

DIRECTIONS = {
    "down": DOWN_FRAMES,
    "up": UP_FRAMES,
    "left": LEFT_FRAMES,
    "right": RIGHT_FRAMES,
}


def validate_grids():
    for direction, frames in DIRECTIONS.items():
        assert len(frames) == 8, f"{direction} must have 8 frames"
        for idx, rows in enumerate(frames):
            assert len(rows) == 32, f"{direction}[{idx}] must have 32 rows, got {len(rows)}"
            for r_idx, row in enumerate(rows):
                assert len(row) == 32, f"{direction}[{idx}] row {r_idx} must have 32 cols, got {len(row)}"
                unknown = set(row) - set(PALETTE.keys()) - {"."}
                assert not unknown, f"{direction}[{idx}] row {r_idx} unknown keys {unknown}"


def hex_to_rgba(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


RGBA_MAP = {k: hex_to_rgba(v) for k, v in PALETTE.items()}
RGBA_MAP["."] = (0, 0, 0, 0)


def render_frame(rows):
    im = Image.new("RGBA", (32, 32))
    im.putdata([RGBA_MAP[ch] for row in rows for ch in row])
    return im


def build_json_sources():
    SRC_DIR.mkdir(parents=True, exist_ok=True)
    for direction, frames in DIRECTIONS.items():
        doc = {
            "version": 1,
            "name": f"player_{direction}",
            "canvas": [32, 32],
            "pivot": [16, 29],
            "palette": PALETTE,
            "frames": [
                {
                    "name": FRAME_NAMES[i][0],
                    "duration_ms": FRAME_NAMES[i][1],
                    "rows": frames[i],
                }
                for i in range(8)
            ],
        }
        out = SRC_DIR / f"player_{direction}.json"
        out.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")


def export_sheets():
    TEX_CHAR_DIR.mkdir(parents=True, exist_ok=True)
    TEX_ANIM_DIR.mkdir(parents=True, exist_ok=True)

    rendered = {}
    for direction, frames in DIRECTIONS.items():
        imgs = [render_frame(rows) for rows in frames]
        rendered[direction] = imgs

        # 256x32 extended animation sheet (8x1)
        sheet_8 = Image.new("RGBA", (256, 32), (0, 0, 0, 0))
        for i, im in enumerate(imgs):
            sheet_8.paste(im, (i * 32, 0))
        sheet_8.save(TEX_ANIM_DIR / f"player_{direction}_sheet.png")

        # 64x32 canonical walk sheet using Walk frame 0 (index 2) + Walk frame 2 (index 4)
        sheet_2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        sheet_2.paste(imgs[2], (0, 0))
        sheet_2.paste(imgs[4], (32, 0))
        sheet_2.save(TEX_CHAR_DIR / f"mage_{direction}.png")
        if direction == "down":
            sheet_2.save(TEX_CHAR_DIR / "wizard.png")

    return rendered


def make_checker(w, h, light=True):
    c1 = (224, 228, 232, 255) if light else (32, 36, 48, 255)
    c2 = (192, 198, 206, 255) if light else (44, 50, 66, 255)
    im = Image.new("RGBA", (w, h), c1)
    draw = ImageDraw.Draw(im)
    step = 12
    for y in range(0, h, step):
        for x in range(0, w, step):
            if ((x // step) + (y // step)) % 2:
                draw.rectangle((x, y, x + step - 1, y + step - 1), fill=c2)
    return im


def export_previews(rendered):
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    scale = 6
    cell = 32 * scale  # 192
    pad_x = 12
    pad_y = 14
    label_h = 26
    left_margin = 90
    header_h = 44

    dirs = ["down", "up", "left", "right"]
    cols = 8
    total_w = left_margin + cols * (cell + pad_x) + 16
    row_pair_h = label_h + cell * 2 + 12 + pad_y
    total_h = header_h + len(dirs) * row_pair_h + 16

    sheet = Image.new("RGBA", (total_w, total_h), (20, 23, 34, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((16, 14), "STORM ARCHMAGE — 32x32 SPRITE SUITE (6x NEAREST) | LIGHT & DARK CHECKERBOARDS", fill=(232, 241, 248, 255))

    for d_idx, d in enumerate(dirs):
        base_y = header_h + d_idx * row_pair_h
        draw.text((16, base_y + label_h + cell - 6), d.upper(), fill=(143, 211, 255, 255))
        draw.text((16, base_y + label_h + 12), "LIGHT BG", fill=(185, 200, 217, 255))
        draw.text((16, base_y + label_h + cell + 18), "DARK BG", fill=(185, 200, 217, 255))
        for f_idx, im in enumerate(rendered[d]):
            x = left_margin + f_idx * (cell + pad_x)
            name = FRAME_NAMES[f_idx][0]
            draw.text((x, base_y + 4), f"{f_idx}: {name}", fill=(255, 224, 102, 255))
            zoomed = im.resize((cell, cell), Image.Resampling.NEAREST)

            light_bg = make_checker(cell, cell, light=True)
            light_bg.alpha_composite(zoomed)
            sheet.paste(light_bg, (x, base_y + label_h))

            dark_bg = make_checker(cell, cell, light=False)
            dark_bg.alpha_composite(zoomed)
            sheet.paste(dark_bg, (x, base_y + label_h + cell + 6))

    sheet.save(PREVIEW_DIR / "player_contact_sheet.png")

    # Animated 6x GIF: 4 directions side-by-side playing idle -> walk (2 cycles) -> cast
    seq = [0, 1, 0, 1, 2, 3, 4, 5, 2, 3, 4, 5, 6, 7, 7]
    gif_w = 4 * cell + 5 * 16
    gif_h = cell + 64
    gif_frames = []
    durations = []
    for f_idx in seq:
        bg = make_checker(gif_w, gif_h, light=False)
        draw_g = ImageDraw.Draw(bg)
        draw_g.rectangle((0, 0, gif_w, 28), fill=(20, 23, 34, 255))
        draw_g.rectangle((0, gif_h - 32, gif_w, gif_h), fill=(20, 23, 34, 255))
        action_name = FRAME_NAMES[f_idx][0].upper()
        draw_g.text((16, 8), f"STORM ARCHMAGE (6x) — {action_name}", fill=(255, 224, 102, 255))
        for d_idx, d in enumerate(dirs):
            x = 16 + d_idx * (cell + 16)
            zoomed = rendered[d][f_idx].resize((cell, cell), Image.Resampling.NEAREST)
            bg.alpha_composite(zoomed, (x, 30))
            draw_g.text((x + cell // 2 - 14, gif_h - 22), d.upper(), fill=(143, 211, 255, 255))
        gif_frames.append(bg.convert("P", palette=Image.Palette.ADAPTIVE))
        durations.append(FRAME_NAMES[f_idx][1])

    gif_frames[0].save(
        PREVIEW_DIR / "player_anim.gif",
        save_all=True,
        append_images=gif_frames[1:],
        duration=durations,
        loop=0,
        disposal=2,
    )


def main():
    validate_grids()
    build_json_sources()
    rendered = export_sheets()
    export_previews(rendered)
    print("Successfully built Storm Archmage sources, PNG sheets, and previews.")


if __name__ == "__main__":
    main()
