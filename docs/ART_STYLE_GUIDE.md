# Spell Storm — Unified Art Style Guide (`ART_STYLE_GUIDE.md`)

## 1. Core Art Direction & Visual Identity
Spell Storm is a dark-vibrant **3/4 Top-Down Fantasy Action RPG** rendered on a crisp pixel grid (`TextureFilter.Nearest`). Every asset—characters, monsters, terrain, decorative props, and magic VFX—must read effortlessly in frantic bullet-heaven combat:
- **Ground planes** stay grounded in mid-low saturation emerald turf, warm earth, and cool slate stone so projectiles and spells pop instantly.
- **Characters and enemies** feature bold silhouettes, readable headgear/weapons/horns, warm top-left highlights, and deep violet-indigo outlines (`#14101a`).
- **Magic spells and impacts** burn brightest with hot white-yellow cores (`#fffbe6`, `#ffe054`), vibrant elemental bodies, and smooth translucent/additive glow blooms.

---

## 2. Perspective, Camera & Rendering Rules
- **Perspective**: **3/4 Top-Down RPG** (~70% front elevation, ~30% top surface).
  - Characters and enemies stand upright facing the camera (plus directional walk poses) above elliptical ground drop shadows.
  - Trees, rocks, bushes, ruins, and campfires use bottom-center anchors (`originX = width / 2, originY = height`) so bases align cleanly with circular collision bounds while canopies overlap behind entities naturally.
- **Native Resolution & Scaling**:
  - Logical viewport: `1280x720` `FitViewport` over a `32x32` px world tile grid.
  - Zero mixels: all sprites, props, and tiles are drawn on a consistent 1x1 native pixel grid using `TextureFilter.Nearest`.
  - Never blur or bilinear-filter pixel sprites.

---

## 3. Master Palette & Color Discipline (`arcane_storm_palette.json`)
- **Outline & Deep Shadow**: `#14101a` (universal outer silhouette ink, shifted deep violet-black rather than pure `#000000`), `#1e1829` (inner crease shadow), `#2c223d` (deep plum occlusion).
- **Hue-Shifted Material Ramps**:
  - **Arcane Wizard Robes**: `#172047` -> `#243b7a` -> `#3662b8` -> `#5296e8` -> `#8fd3ff`
  - **Wizard Gold Trim & Buckle**: `#6b3e12` -> `#9e631b` -> `#d99726` -> `#ffe066` -> `#fff7b8`
  - **Arcane Staff Crystal**: `#1c698c` -> `#33b5e5` -> `#8ff5ff` -> `#ffffff`
  - **Abyssal Fiend Hide & Crimson Carapace**: `#1c1624` -> `#2e2238` -> `#473354` | `#4a1222` -> `#801b2e` -> `#bf2633` -> `#f25244` -> `#ff8a66` + Ember Glow `#d9571e` -> `#ff9b26` -> `#fff3a1`
  - **Meadow Grass**: `#163020` -> `#224d2c` -> `#347539` -> `#52a349` -> `#82cf63` -> `#b8ed87`
  - **Warm Dirt & Bark**: `#2b1a14` -> `#472b1e` -> `#6e442c` -> `#99633f` | `#382419` -> `#543826` -> `#785238` -> `#9c6f4f` -> `#bf926b`
  - **Runic Stone**: `#282c38` -> `#3e4556` -> `#5a657a` -> `#808fa6` -> `#aebdcf`
  - **Fire Spell & Explosion**: `#3d0c18` -> `#781722` -> `#bf2626` -> `#f25c22` -> `#ff9e24` -> `#ffe054` -> `#fffbe6`
- **Guideline**: Keep individual sprite palettes concise and cohesive (`~10-20` intentional ramp shades; extra ramp shades allowed where artistically justified for smooth highlights or glow).

---

## 4. Lighting, Outlines & Alpha Discipline
- **Global Light Direction**: **Top-Left (`135°`)**.
  - Top/left surfaces catch bright ramp highlights (`-1` or `-2` toward warm light).
  - Bottom/right planes step into cool indigo/violet shadow ramps.
- **Outlines**:
  - **Characters, Monsters, Props, Pickups**: Crisp `1px` dark selective outline (`#14101a` outer rim; dark material ramp shade inside overlapping limbs/folds).
  - **Terrain Tiles (`grass`, `dirt`, `stone`, `sand`, `water`)**: **No outer outline** so tiles stitch seamlessly edge-to-edge across `3x3`+ neighborhoods.
- **Alpha Channels**:
  - **Characters, Enemies, Terrain, Props**: **Strict Binary Alpha (`alpha = 0` or `255`)**—no muddy semi-transparent anti-aliased fringes around sprites.
  - **Spells, Fire, Explosions & Magic VFX**: Smooth translucent alpha (`0..255`) on outer glows/embers and **additive blending (`GL_SRC_ALPHA, GL_ONE`)** in `GameScreen` are explicitly supported and encouraged for radiant combat impact.

---

## 5. Proportions, Origins & Hitbox Alignment
- **Player (`32x32` frame)**:
  - Pointed wizard hat + glowing arcane crystal staff + sapphire mantle.
  - Centered horizontally (`x = 16`), feet grounded on row `y = 29` (`3px` bottom clearance).
  - Origin: `(16, 16)`. Walk/Idle/Cast frames never shift the center of mass vertically or horizontally across frames (`0px` playback jitter).
- **Melee Fiend (`32x32` frame)**:
  - Hunched clawed demon facing **right (`+X`)** by default (engine flips horizontally via `facingLeft`).
  - Centered horizontally (`x = 16`), feet grounded at `y = 29`.
- **Environment Props**:
  - `tree_oak.png` (`32x48`, origin `(16, 48)`), `rock.png` (`24x24`, origin `(12, 24)`), `bush.png` (`20x20`, origin `(10, 20)`), `campfire.png` (`24x24`, origin `(12, 24)`).
  - Grounded pixels touch `y = height - 2` to `height - 1` so props sit solidly on terrain without floating.
- **Projectiles (`24x24` frame)**:
  - Oriented **right (`0°`)** with core centered at `(12, 12)` so `GameScreen` angle rotation aligns the blazing tip directly along velocity.

---

## 6. Animation Frame Conventions & File Layout
- **Legacy Canonical Contracts (`AssetRegistry.TextureId`)**:
  - `assets/textures/{characters,enemies,environment,pickups,projectiles,terrain}` remain at their exact filenames and exact `AssetLoadingTest` dimensions (`64x32` 2-frame player walk sheets, `32x32` melee fiend, `32x32` terrain tiles, `24x24` projectile, etc.). **Do not add extra `.png` files directly inside those 6 folders.**
- **Extended Animation & VFX Sheets**:
  - Placed in sibling folders `assets/textures/animations/`, `assets/textures/vfx/`, and `assets/textures/ui/`:
    - `animations/player_{down,up,left,right}_sheet.png`: `256x32` (`8` frames of `32x32`: `0..1` Idle, `2..5` Walk, `6..7` Cast)
    - `animations/melee_fiend_sheet.png`: `256x32` (`8` frames of `32x32`: `0..1` Idle, `2..5` Run/Prowl, `6..7` Claw Lunge Attack)
    - `animations/campfire_sheet.png`: `96x24` (`4` frames of `24x24` flickering flame loop)
    - `vfx/spell_fire_sheet.png`: `144x24` (`6` frames of `24x24` looping comet flame)
    - `vfx/vfx_fire_impact.png`: `192x32` (`6` frames of `32x32` detonation burst)
    - `vfx/vfx_fire_cast.png`: `96x24` (`4` frames of `24x24` muzzle cast flare)
- **Editable Sources**: Every asset has a reproducible source script or JSON grid under `assets/sources/`.

---

## 7. Milestone 3–5 Additions & Reproducible Asset Tooling

### Additional Sheets (`TextureManager.CompleteSheetId`, 22 Sheets)
- **Characters & Catalyst**: `animations/player_hurt_death_sheet.png` (`192x32`, 6f of `32x32`), `vfx/catalyst_orb_sheet.png` (`96x16`, 6f of `16x16` neutral white/silver luminance tinted dynamically to active spell color).
- **Monsters & Boss (`8` frames each: `0..1` idle, `2..5` move, `6..7` telegraph/attack)**: `animations/ranged_cultist_sheet.png` (`256x32`), `animations/tank_golem_sheet.png` (`384x48`), `animations/boss_colossus_sheet.png` (`512x64`).
- **World & Transitions**: `animations/water_sheet.png` (`128x32`, 4f), `animations/terrain_variants_sheet.png` (`256x32`, 8f), `animations/terrain_transitions_sheet.png` (`256x32`, 8f cardinal Grass N/E/S/W overhangs & Water Shore N/E/S/W fringes).
- **Spells, Impacts, Gems & HUD Icons**: `vfx/spell_{ice,lightning,wind,earth}_sheet.png`, `vfx/projectile_{player,enemy}_sheet.png`, `vfx/vfx_{ice,lightning,wind,earth,arcane}_impact.png`, `animations/gems_anim_sheet.png`, `ui/hud_elements_sheet.png`, `ui/powerup_icons_sheet.png`.

### External Art Skills (Not Vendored)
Rather than vendoring third-party tool repositories inside Git, the build scripts under `assets/sources/` rely only on Python 3 + Pillow/NumPy and optionally integrate two external MIT-licensed toolchains:
1. **Pixelbolt (`pixelbolt@0.16.0`)** — Used to compile/render `.fx.json` spell VFX recipes cleanly via Node.js:
   ```bash
   npx -y pixelbolt@0.16.0 --help
   ```
2. **Pixel Art Studio (`pixel_art.py`)** — Optional sprite/palette inspector invoked by `assets/sources/ui/build_ui_m3.py` if present at `.agents/skills/pixel-art-studio/scripts/pixel_art.py` or pointed to by `PIXEL_ART_PY=/path/to/pixel_art.py` (automatically skipped when not installed).
