# Spell Storm — 2D Art Asset Pipeline & Specifications

## 1. Directory Structure

All runtime game assets must reside in the `assets/` directory. CMake automatically synchronizes this directory into the build output directory (`$<TARGET_FILE_DIR:SpellStorm>/assets`) on each build.

```text
assets/
├── fonts/
│   ├── DejaVu-LICENSE.txt
│   ├── DejaVuSans.ttf          (Body font)
│   ├── DejaVuSans-Bold.ttf     (Header & UI badge bold font)
│   └── DejaVuSerif-Bold.ttf    (Title font)
└── textures/
    ├── characters/
    │   └── wizard.png          (Player wizard sprite sheet, 64x32: 2 frames of 32x32)
    ├── enemies/
    │   ├── melee_fiend.png     (Melee enemy sprite, 32x32)
    │   ├── ranged_cultist.png  (Ranged cultist sprite, 32x32)
    │   └── tank_golem.png      (Armored tank golem sprite, 48x48)
    ├── projectiles/
    │   ├── spell_fire.png      (Firebolt missile, 24x24)
    │   ├── spell_ice.png       (Frost shard missile, 24x24)
    │   ├── spell_lightning.png (Spark arc missile, 24x24)
    │   ├── spell_wind.png      (Gale burst missile, 24x24)
    │   ├── spell_earth.png     (Boulder missile, 24x24)
    │   └── projectile_enemy.png(Corrupted enemy bolt, 24x24)
    ├── pickups/
    │   ├── gem_cyan.png        (Melee EXP gem, 16x16)
    │   ├── gem_emerald.png     (Ranged EXP gem, 16x16)
    │   └── gem_gold.png        (Tank EXP gem, 16x16)
    └── environment/
        └── floor_tile.png      (Flagstone arena floor tile, 64x64)
```

---

## 2. Technical Asset Specifications

### File Formats & Color Space
- **Image Format**: PNG-32 (RGBA with full 8-bit alpha channel).
- **Bit Depth**: 8 bits per channel (32 bits per pixel).
- **Color Mode**: sRGB.
- **Filtering**: Point / Nearest-neighbor sampling recommended for pixel-art assets to maintain crisp rendering without blurring.

### Entity Visual Dimensions & Pivot Origins

| Asset | Target Dimensions | Origin / Pivot | Visual Radius | Collision Radius | Frames / Sheet |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `wizard.png` | 64x32 (2x 32x32) | Center `(16, 16)` | ~20 px | **20.0 px** (Exact) | 2 frames (Idle / Stride) |
| `melee_fiend.png` | 32x32 | Center `(16, 16)` | ~16 px | **16.0 px** (Exact) | 1 frame (rotates to velocity) |
| `ranged_cultist.png`| 32x32 | Center `(16, 16)` | ~15 px | **15.0 px** (Exact) | 1 frame |
| `tank_golem.png` | 48x48 | Center `(24, 24)` | ~26 px | **26.0 px** (Exact) | 1 frame |
| `spell_*.png` | 24x24 | Center `(12, 12)` | ~8-14 px | **7.0 - 14.0 px** | 1 frame (rotates to direction) |
| `gem_*.png` | 16x16 | Center `(8, 8)` | ~8 px | **8.0 px** (Exact) | 1 frame (vertical bobbing) |
| `floor_tile.png` | 64x64 | Top-Left `(0, 0)` | 70x70 px cell | N/A (Arena Bounds) | Seamless repeating flagstone |

> [!IMPORTANT]
> Visual dimensions and sprite scaling must remain strictly decoupled from gameplay collision dimensions.
> The collision radius in `Entity::radius()` governs physics and damage overlap. Sprites are centered and uniformly scaled (`scale.x == scale.y`) to fit within the collision envelope without distortion.

---

## 3. Style & Palette Guide

To preserve visual coherence across all entities:
- **Player Wizard**: Sapphire blues (`#2d55c3`), gold sash (`#ebb92d`), deep cowl shadow, cyan focus orb (`#50d7ff`).
- **Melee Fiend**: Crimson carapace (`#cd2830`), blood horns, sulfur yellow eyes (`#ffdc28`).
- **Ranged Cultist**: Arcane violet robe (`#7d2db9`), dark shroud, glowing cyan mystic visor (`#46ebe1`).
- **Tank Golem**: Weathered basalt gray armor (`#414650`), molten magma core and fissures (`#f57814`, `#ffcf3c`).
- **EXP Gems**: Cyan (`#32c3ff`), Emerald (`#2de187`), Royal Amber Gold (`#ffcd2d`).
- **Arena Floor**: Dark slate flagstones (`#181d29` to `#262e40`) with crisp mortar seams.

---

## 4. Resource Ownership & Lifetime Model

1. **Owner**: `ss::ResourceManager` is the sole owner of all `sf::Texture` and `sf::Font` instances.
2. **Lifetime Guarantee**: `ResourceManager` is instantiated in `main()` and outlives the `GameSession`, states, entities, and render windows. Pointers and references returned by `ResourceManager` remain valid indefinitely until program termination.
3. **Graceful Fallback**: If an image or font fails to load or is deleted, `ResourceManager::getTexture()` returns `nullptr`. Entities and rendering systems automatically detect this and fall back to procedural geometric drawing (`DrawUtils`) with zero crashes.
4. **No Repeated Loads**: Textures and fonts are cached in associative containers (`std::map<TextureId, std::unique_ptr<sf::Texture>>`). Requesting an asset loads it once into memory.
5. **Path Resolution**: The loader inspects candidate paths in sequence:
   - `assets/<subdir>/<filename>` (Working directory relative)
   - `../assets/<subdir>/<filename>` (Build subdirectory relative)
   - `../../assets/<subdir>/<filename>` (Nested test build relative)
   - Compile-time macro `SPELLSTORM_ASSET_DIR` (Absolute fallback)

---

## 5. Licensing & Provenance

- **Fonts**: DejaVu font family (Free / Open Source font license, see `assets/fonts/DejaVu-LICENSE.txt`).
- **Textures**: 100% project-authored for SpellStorm via `tools/generate_assets.py`. Released under CC0 / MIT license terms with zero proprietary or third-party copyright claims.
