# SpellStorm Game Assets

This directory contains the runtime assets used by Spell Storm.

## Directory Layout
- `fonts/`: TrueType fonts for game HUD and modal UI text.
- `textures/characters/`: Player character sprites and sheets.
- `textures/enemies/`: Enemy sprites (Melee Fiend, Ranged Cultist, Tank Golem).
- `textures/projectiles/`: Spell missile and attack projectile sprites.
- `textures/pickups/`: Collectible experience gems.
- `textures/environment/`: Floor and arena environment textures.

## Asset Pipeline & Specifications
See [`docs/asset_specifications.md`](../docs/asset_specifications.md) for full dimensions, pivot points, color palettes, and pipeline specifications.

To regenerate the starter 2D pixel art assets:
```bash
python3 tools/generate_assets.py
```
