#!/usr/bin/env python3
"""
Spell Storm - Concept Art Asset Extraction & Packaging Pipeline
Extracts, isolates, normalizes, and packages individual game assets
directly from the user-provided concept sheet:
- Player: Blue-hooded fantasy mage (directional: down, up, left, right)
- Enemies: Green goblin (Melee), Purple cultist (Ranged), Stone golem (Tank)
- Projectiles: Blue magic orb, Red enemy missile, Elemental variants
- EXP Pickups: Cyan, Emerald, Gold crystals
- Terrain Tiles: Grass, Dirt, Stone, Water, Sand, Wooden Bridges
- Decorative Environment Objects: Trees (Oak, Pine), Rocks, Bushes, Logs, Ruins, Tents, Campfires
"""

import os
import sys
from collections import deque
import numpy as np
from PIL import Image

def isolate_sprite(crop, bg_color=(12, 20, 31), threshold=36, antialias_range=16):
    """
    Flood-fill from boundary inward to isolate transparent sprites cleanly
    without damaging dark pupils, interior shading, or character outlines.
    """
    arr = np.array(crop.convert('RGBA'))
    h, w = arr.shape[:2]
    visited = np.zeros((h, w), dtype=bool)
    q = deque()

    # Enqueue canvas borders
    for x in range(w):
        q.append((0, x))
        q.append((h - 1, x))
    for y in range(h):
        q.append((y, 0))
        q.append((y, w - 1))

    bg = np.array(bg_color, dtype=float)

    while q:
        cy, cx = q.popleft()
        if visited[cy, cx]:
            continue
        visited[cy, cx] = True

        pix = arr[cy, cx, :3].astype(float)
        dist = np.linalg.norm(pix - bg)

        if dist < threshold:
            arr[cy, cx, 3] = 0
            for dy, dx in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                ny, nx = cy + dy, cx + dx
                if 0 <= ny < h and 0 <= nx < w and not visited[ny, nx]:
                    q.append((ny, nx))
        elif dist < threshold + antialias_range:
            factor = (dist - threshold) / float(antialias_range)
            arr[cy, cx, 3] = int(255 * factor)

    return Image.fromarray(arr)

def fit_to_canvas(img, target_w, target_h, anchor='center'):
    """
    Trims transparent boundary, resizes cleanly to fit target canvas,
    and positions with the requested anchor (center or bottom).
    """
    bbox = img.getbbox()
    if bbox:
        trimmed = img.crop(bbox)
    else:
        trimmed = img

    tw, th = trimmed.size
    scale = min(target_w / tw, target_h / th)
    new_w = max(1, int(tw * scale))
    new_h = max(1, int(th * scale))
    trimmed = trimmed.resize((new_w, new_h), Image.Resampling.LANCZOS)
    tw, th = trimmed.size

    canvas = Image.new('RGBA', (target_w, target_h), (0, 0, 0, 0))
    if anchor == 'bottom':
        ox = (target_w - tw) // 2
        oy = target_h - th
    else:
        ox = (target_w - tw) // 2
        oy = (target_h - th) // 2
    canvas.paste(trimmed, (ox, oy), trimmed)
    return canvas

def make_tile(crop, target_size=32):
    """
    Normalizes a terrain tile to target_size x target_size.
    """
    return crop.resize((target_size, target_size), Image.Resampling.LANCZOS)

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    concept_sheet_candidates = [
        "/home/re4ler/.gemini/antigravity/brain/3ab8f9f5-e045-4b16-80cf-045fbe2889ff/.user_uploaded/media_1792141421919_67f34b26.jpg",
        os.path.join(root, "concept_sheet.jpg"),
    ]
    if len(sys.argv) > 1:
        concept_sheet_candidates.insert(0, sys.argv[1])

    sheet_path = None
    for p in concept_sheet_candidates:
        if os.path.isfile(p):
            sheet_path = p
            break

    if not sheet_path:
        print("[Error] Concept sheet reference image not found!")
        sys.exit(1)

    print(f"[AssetPipeline] Loading concept sheet from: {sheet_path}")
    sheet = Image.open(sheet_path)

    textures_dir = os.path.join(root, "assets", "textures")
    os.makedirs(textures_dir, exist_ok=True)

    # ---------------------------------------------------------
    # 1. PLAYER: Blue-hooded fantasy mage
    # ---------------------------------------------------------
    # Directional poses in Section 1:
    # Down:  Row 1 (15, 137, 52, 184), Row 2 (15, 197, 52, 244)
    # Left:  Row 1 (65, 137, 98, 184), Row 2 (65, 197, 98, 244)
    # Up:    Row 1 (115, 137, 148, 184), Row 2 (115, 197, 148, 244)
    # Right: Row 1 (165, 137, 200, 184), Row 2 (165, 197, 200, 244)
    down_f0 = fit_to_canvas(isolate_sprite(sheet.crop((15, 137, 52, 184))), 32, 32, 'bottom')
    down_f1 = fit_to_canvas(isolate_sprite(sheet.crop((15, 197, 52, 244))), 32, 32, 'bottom')

    left_f0 = fit_to_canvas(isolate_sprite(sheet.crop((65, 137, 98, 184))), 32, 32, 'bottom')
    left_f1 = fit_to_canvas(isolate_sprite(sheet.crop((65, 197, 98, 244))), 32, 32, 'bottom')

    up_f0 = fit_to_canvas(isolate_sprite(sheet.crop((115, 137, 148, 184))), 32, 32, 'bottom')
    up_f1 = fit_to_canvas(isolate_sprite(sheet.crop((115, 197, 148, 244))), 32, 32, 'bottom')

    right_f0 = fit_to_canvas(isolate_sprite(sheet.crop((165, 137, 200, 184))), 32, 32, 'bottom')
    right_f1 = fit_to_canvas(isolate_sprite(sheet.crop((165, 197, 200, 244))), 32, 32, 'bottom')

    # Standard 64x32 wizard.png (Frame 0: idle, Frame 1: walk) for backwards compatibility
    wizard_sheet = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    wizard_sheet.paste(down_f0, (0, 0))
    wizard_sheet.paste(down_f1, (32, 0))

    def make_dir_sheet(f0, f1):
        s = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
        s.paste(f0, (0, 0))
        s.paste(f1, (32, 0))
        return s

    sheet_down = make_dir_sheet(down_f0, down_f1)
    sheet_left = make_dir_sheet(left_f0, left_f1)
    sheet_up = make_dir_sheet(up_f0, up_f1)
    sheet_right = make_dir_sheet(right_f0, right_f1)

    # ---------------------------------------------------------
    # 2. ENEMIES
    # ---------------------------------------------------------
    # Melee Goblin (32x32): x ~ 237 to 263, y ~ 62 to 103
    goblin = fit_to_canvas(isolate_sprite(sheet.crop((237, 62, 263, 103))), 32, 32, 'bottom')

    # Ranged Cultist (32x32): x ~ 235 to 258, y ~ 133 to 175
    cultist = fit_to_canvas(isolate_sprite(sheet.crop((235, 133, 258, 175))), 32, 32, 'bottom')

    # Tank Golem (48x48): x ~ 228 to 276, y ~ 201 to 257
    tank = fit_to_canvas(isolate_sprite(sheet.crop((228, 201, 276, 257))), 48, 48, 'bottom')

    # ---------------------------------------------------------
    # 3. PROJECTILES
    # ---------------------------------------------------------
    # Player blue magic projectile: (495, 65, 525, 95)
    proj_player = fit_to_canvas(isolate_sprite(sheet.crop((495, 65, 525, 95))), 24, 24, 'center')

    # Enemy red projectile: (495, 155, 525, 180)
    proj_enemy = fit_to_canvas(isolate_sprite(sheet.crop((495, 155, 525, 180))), 24, 24, 'center')

    # Elemental spell variants
    proj_fire = fit_to_canvas(isolate_sprite(sheet.crop((495, 155, 525, 180))), 24, 24, 'center')
    proj_ice = fit_to_canvas(isolate_sprite(sheet.crop((495, 65, 525, 95))), 24, 24, 'center')
    proj_lightning = fit_to_canvas(isolate_sprite(sheet.crop((530, 65, 560, 95))), 24, 24, 'center')
    proj_wind = fit_to_canvas(isolate_sprite(sheet.crop((565, 65, 595, 95))), 24, 24, 'center')
    proj_earth = fit_to_canvas(isolate_sprite(sheet.crop((600, 65, 630, 95))), 24, 24, 'center')

    # ---------------------------------------------------------
    # 4. EXP / PICKUPS
    # ---------------------------------------------------------
    # Emerald gem (670, 50, 695, 75)
    gem_emerald = fit_to_canvas(isolate_sprite(sheet.crop((670, 50, 695, 75))), 16, 16, 'center')
    # Cyan gem (705, 50, 730, 75)
    gem_cyan = fit_to_canvas(isolate_sprite(sheet.crop((705, 50, 730, 75))), 16, 16, 'center')
    # Gold gem (705, 90, 730, 115)
    gem_gold = fit_to_canvas(isolate_sprite(sheet.crop((705, 90, 730, 115))), 16, 16, 'center')

    # ---------------------------------------------------------
    # 5. TERRAIN TILES (32x32) - Pure borderless textures
    # ---------------------------------------------------------
    tile_grass = make_tile(sheet.crop((818, 44, 834, 60)), 32)
    tile_dirt = make_tile(sheet.crop((852, 44, 868, 60)), 32)
    tile_stone = make_tile(sheet.crop((885, 44, 901, 60)), 32)
    tile_water = make_tile(sheet.crop((920, 44, 936, 60)), 32)
    tile_sand = make_tile(sheet.crop((954, 44, 970, 60)), 32)

    # Bridges: horizontal and vertical wooden planks extracted from concept sheet bridge
    b_bridge = sheet.crop((365, 665, 435, 735))
    b_rot = b_bridge.rotate(35, resample=Image.Resampling.BICUBIC)
    tile_bridge_h = make_tile(b_rot.crop((20, 13, 50, 36)), 32)
    tile_bridge_v = tile_bridge_h.rotate(90)

    # ---------------------------------------------------------
    # 6. DECORATIVE ENVIRONMENT OBJECTS
    # ---------------------------------------------------------
    # Oak Tree: (814, 210, 846, 242)
    tree_oak = fit_to_canvas(isolate_sprite(sheet.crop((814, 208, 848, 244))), 32, 48, 'bottom')

    # Pine Tree: (858, 210, 882, 242)
    tree_pine = fit_to_canvas(isolate_sprite(sheet.crop((858, 208, 882, 244))), 24, 48, 'bottom')

    # Rock (24x24): (893, 210, 915, 242)
    rock = fit_to_canvas(isolate_sprite(sheet.crop((893, 214, 915, 240))), 24, 24, 'bottom')

    # Large Rock / Boulder (32x32): (890, 210, 920, 242)
    rock_large = fit_to_canvas(isolate_sprite(sheet.crop((890, 210, 920, 242))), 32, 32, 'bottom')

    # Bush (20x20): (924, 218, 944, 238)
    bush = fit_to_canvas(isolate_sprite(sheet.crop((924, 218, 944, 238))), 20, 20, 'bottom')

    # Log (28x18): (948, 220, 972, 238)
    log = fit_to_canvas(isolate_sprite(sheet.crop((948, 220, 972, 238))), 28, 18, 'bottom')

    # Ancient Ruins Pillar / Arch: (974, 208, 998, 244)
    ruins = fit_to_canvas(isolate_sprite(sheet.crop((974, 208, 998, 244))), 32, 48, 'bottom')

    # Camp Tent: (820, 255, 846, 282)
    tent = fit_to_canvas(isolate_sprite(sheet.crop((820, 254, 848, 282))), 40, 36, 'bottom')

    # Campfire: (888, 255, 910, 280)
    campfire = fit_to_canvas(isolate_sprite(sheet.crop((888, 255, 910, 280))), 24, 24, 'bottom')

    # ---------------------------------------------------------
    # Save all assets into project asset hierarchy
    # ---------------------------------------------------------
    assets = {
        # Characters
        os.path.join(textures_dir, "characters", "wizard.png"): wizard_sheet,
        os.path.join(textures_dir, "characters", "mage_down.png"): sheet_down,
        os.path.join(textures_dir, "characters", "mage_left.png"): sheet_left,
        os.path.join(textures_dir, "characters", "mage_up.png"): sheet_up,
        os.path.join(textures_dir, "characters", "mage_right.png"): sheet_right,
        # Enemies
        os.path.join(textures_dir, "enemies", "melee_fiend.png"): goblin,
        os.path.join(textures_dir, "enemies", "ranged_cultist.png"): cultist,
        os.path.join(textures_dir, "enemies", "tank_golem.png"): tank,
        # Projectiles
        os.path.join(textures_dir, "projectiles", "projectile_player.png"): proj_player,
        os.path.join(textures_dir, "projectiles", "projectile_enemy.png"): proj_enemy,
        os.path.join(textures_dir, "projectiles", "spell_fire.png"): proj_fire,
        os.path.join(textures_dir, "projectiles", "spell_ice.png"): proj_ice,
        os.path.join(textures_dir, "projectiles", "spell_lightning.png"): proj_lightning,
        os.path.join(textures_dir, "projectiles", "spell_wind.png"): proj_wind,
        os.path.join(textures_dir, "projectiles", "spell_earth.png"): proj_earth,
        # Pickups
        os.path.join(textures_dir, "pickups", "gem_cyan.png"): gem_cyan,
        os.path.join(textures_dir, "pickups", "gem_emerald.png"): gem_emerald,
        os.path.join(textures_dir, "pickups", "gem_gold.png"): gem_gold,
        # Terrain
        os.path.join(textures_dir, "terrain", "grass.png"): tile_grass,
        os.path.join(textures_dir, "terrain", "dirt.png"): tile_dirt,
        os.path.join(textures_dir, "terrain", "stone.png"): tile_stone,
        os.path.join(textures_dir, "terrain", "water.png"): tile_water,
        os.path.join(textures_dir, "terrain", "sand.png"): tile_sand,
        os.path.join(textures_dir, "terrain", "bridge_h.png"): tile_bridge_h,
        os.path.join(textures_dir, "terrain", "bridge_v.png"): tile_bridge_v,
        # Environment
        os.path.join(textures_dir, "environment", "tree_oak.png"): tree_oak,
        os.path.join(textures_dir, "environment", "tree_pine.png"): tree_pine,
        os.path.join(textures_dir, "environment", "rock.png"): rock,
        os.path.join(textures_dir, "environment", "rock_large.png"): rock_large,
        os.path.join(textures_dir, "environment", "bush.png"): bush,
        os.path.join(textures_dir, "environment", "log.png"): log,
        os.path.join(textures_dir, "environment", "ruins.png"): ruins,
        os.path.join(textures_dir, "environment", "camp_tent.png"): tent,
        os.path.join(textures_dir, "environment", "campfire.png"): campfire,
    }

    print("\nWriting extracted assets to disk...")
    for path, img in assets.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        print(f"  [OK] Saved: {os.path.relpath(path, root)} ({img.size[0]}x{img.size[1]})")

    print("\nAll assets extracted, isolated, and saved successfully!")

if __name__ == "__main__":
    main()
