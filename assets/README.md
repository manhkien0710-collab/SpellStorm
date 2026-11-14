# Spell Storm Game Assets

This directory contains all runtime textures, sprite sheets, fonts, and editable pixel-art sources for the **Java 21 + LibGDX** edition of **Spell Storm**.

## Runtime Directory Layout

- `fonts/`: TrueType fonts (`DejaVuSans.ttf`, `DejaVuSans-Bold.ttf`, `DejaVuSerif-Bold.ttf`, and `DejaVu-LICENSE.txt`) rasterized dynamically at runtime via `gdx-freetype`.
- `textures/characters/`: Legacy/canonical `64x32` player walk textures (`wizard.png`, `mage_{down,up,left,right}.png`).
- `textures/enemies/`: Canonical static enemy textures (`melee_fiend.png`, `ranged_cultist.png`, `tank_golem.png`, `boss_colossus.png`).
- `textures/environment/`: Prop sprites (`bush.png`, `campfire.png`, `camp_tent.png`, `floor_tile.png`, `log.png`, `rock.png`, `rock_large.png`, `ruins.png`, `tree_oak.png`, `tree_pine.png`).
- `textures/pickups/`: Canonical collectible EXP gem icons (`gem_cyan.png`, `gem_emerald.png`, `gem_gold.png`).
- `textures/projectiles/`: Canonical `24x24` projectile icons (`spell_{fire,ice,lightning,wind,earth}.png`, `projectile_player.png`, `projectile_enemy.png`).
- `textures/terrain/`: Seamless `32x32` base terrain tiles (`grass.png`, `dirt.png`, `stone.png`, `water.png`, `sand.png`, `bridge_h.png`, `bridge_v.png`).
- `textures/animations/`: Extended multi-frame sprite sheets (8-frame player/enemy sheets, hurt/death sheet, 4-frame `campfire_sheet.png`, 4-frame `water_sheet.png`, `terrain_variants_sheet.png`, `terrain_transitions_sheet.png`, `gems_anim_sheet.png`).
- `textures/vfx/`: Elemental projectile flight sheets (`*_sheet.png`), detonation impact bursts (`vfx_*_impact.png`), `vfx_fire_cast.png`, and `catalyst_orb_sheet.png`.
- `textures/ui/`: HUD element rune medallions (`hud_elements_sheet.png`) and power-up badges (`powerup_icons_sheet.png`).

> **Contract Note:** `AssetRegistry.TextureId` requires the 6 canonical subfolders (`characters`, `enemies`, `environment`, `pickups`, `projectiles`, `terrain`) to contain strictly 36 `.png` files matching the headless `--headless` asset check (41 total runtime files including the 3 TTF fonts, `DejaVu-LICENSE.txt`, and this `assets/README.md`).

## Editable Sources & Regeneration

Editable pixel grids, palettes (`assets/sources/palette/arcane_storm_palette.json`), Pixelbolt `.fx.json` VFX recipes, and Python generation scripts live under `assets/sources/`:

```bash
python3 assets/sources/characters/build_player_sprites.py
python3 assets/sources/characters/build_char_m3.py
python3 assets/sources/enemies/build_monsters_m3.py
python3 assets/sources/terrain/build_terrain.py
python3 assets/sources/terrain/build_terrain_m3.py
python3 assets/sources/environment/build_environment.py
python3 assets/sources/environment/build_env_m3.py
python3 assets/sources/vfx/build_fire_vfx.py
python3 assets/sources/vfx/build_vfx_m3.py
python3 assets/sources/ui/build_ui_m3.py
```

See [docs/ART_STYLE_GUIDE.md](../docs/ART_STYLE_GUIDE.md) for palette rules, frame layouts, and optional external pixel-art CLI dependencies (`pixel-art-studio`, `pixelbolt`).
