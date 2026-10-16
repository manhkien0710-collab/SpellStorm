#!/usr/bin/env python3
"""
Spell Storm - Asset Generation Tool
Generates cohesive, stylized 2D pixel art textures for SpellStorm:
- Player Wizard (idle & walk animation frames)
- Melee Fiend Enemy
- Ranged Cultist Enemy
- Tank Golem Enemy
- Elemental Projectiles (Fire, Ice, Lightning, Wind, Earth, Enemy Dark Bolt)
- Experience Gems (Cyan, Emerald, Gold)
- Environment Tiles (Flagstone Floor, Boundary Wall)
"""

import os
from PIL import Image, ImageDraw

def create_wizard():
    # 64x32 sprite sheet: 2 frames of 32x32
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for frame in range(2):
        ox = frame * 32
        bob = 1 if frame == 1 else 0

        # Wizard Robe (Deep blue & cyan trims)
        # Body / Robe flare
        robe_pts = [
            (ox + 16, 8 + bob),
            (ox + 23, 27 + bob),
            (ox + 9, 27 + bob)
        ]
        draw.polygon(robe_pts, fill=(45, 85, 195, 255))
        # Robe shadow / folds
        draw.polygon([(ox + 16, 12 + bob), (ox + 19, 27 + bob), (ox + 13, 27 + bob)], fill=(32, 60, 145, 255))

        # Golden belt / sash
        draw.rectangle([ox + 12, 18 + bob, ox + 20, 20 + bob], fill=(235, 185, 45, 255))
        draw.point((ox + 16, 19 + bob), fill=(255, 245, 140, 255))

        # Hood / Cowl
        draw.ellipse([ox + 10, 4 + bob, ox + 22, 16 + bob], fill=(30, 50, 125, 255))
        draw.ellipse([ox + 11, 6 + bob, ox + 21, 15 + bob], fill=(15, 25, 65, 255))

        # Arcane glowing eyes inside hood
        draw.point((ox + 14, 10 + bob), fill=(100, 230, 255, 255))
        draw.point((ox + 18, 10 + bob), fill=(100, 230, 255, 255))

        # Wizard Pointy Hat
        hat_pts = [
            (ox + 16, 1 + bob),
            (ox + 24, 7 + bob),
            (ox + 8, 7 + bob)
        ]
        draw.polygon(hat_pts, fill=(40, 75, 180, 255))
        draw.rectangle([ox + 7, 7 + bob, ox + 25, 8 + bob], fill=(235, 185, 45, 255)) # Hat brim

        # Magic Catalyst Focus Orb (Floating near hand)
        orb_x = ox + (26 if frame == 0 else 25)
        orb_y = 14 + (0 if frame == 0 else 2)
        draw.ellipse([orb_x - 3, orb_y - 3, orb_x + 3, orb_y + 3], fill=(80, 215, 255, 220))
        draw.ellipse([orb_x - 1, orb_y - 1, orb_x + 1, orb_y + 1], fill=(255, 255, 255, 255))

    return img

def create_melee_fiend():
    # 32x32: Fast, aggressive demonic crawler
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Horns
    draw.polygon([(10, 12), (5, 4), (12, 8)], fill=(110, 20, 25, 255))
    draw.polygon([(22, 12), (27, 4), (20, 8)], fill=(110, 20, 25, 255))
    draw.polygon([(6, 5), (8, 7), (6, 7)], fill=(215, 60, 60, 255))
    draw.polygon([(26, 5), (24, 7), (26, 7)], fill=(215, 60, 60, 255))

    # Diamond / Spiked carapace
    body_pts = [(16, 5), (26, 16), (16, 28), (6, 16)]
    draw.polygon(body_pts, fill=(205, 40, 48, 255))
    inner_pts = [(16, 8), (23, 16), (16, 25), (9, 16)]
    draw.polygon(inner_pts, fill=(130, 20, 28, 255))

    # Demonic Claws / Spikes
    draw.polygon([(5, 15), (1, 17), (6, 19)], fill=(180, 35, 40, 255))
    draw.polygon([(27, 15), (31, 17), (26, 19)], fill=(180, 35, 40, 255))

    # Glowing predatory eyes
    draw.polygon([(12, 14), (14, 13), (13, 16)], fill=(255, 220, 40, 255))
    draw.polygon([(20, 14), (18, 13), (19, 16)], fill=(255, 220, 40, 255))

    # Core ember heart
    draw.ellipse([14, 17, 18, 21], fill=(255, 120, 40, 255))
    draw.point((16, 19), fill=(255, 240, 180, 255))

    return img

def create_ranged_cultist():
    # 32x32: Occultist in purple mantle with arcane orb
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Shrouded Robe
    pts = [(16, 4), (26, 18), (22, 28), (10, 28), (6, 18)]
    draw.polygon(pts, fill=(125, 45, 185, 255))
    inner_pts = [(16, 8), (22, 18), (19, 26), (13, 26), (10, 18)]
    draw.polygon(inner_pts, fill=(75, 20, 115, 255))

    # Hexagonal cowl
    draw.polygon([(16, 6), (22, 10), (22, 18), (16, 22), (10, 18), (10, 10)], fill=(50, 15, 85, 255))

    # Arcane Eye Visor (Mystic Cyan Glow)
    draw.rectangle([13, 13, 19, 15], fill=(70, 235, 225, 255))
    draw.point((16, 14), fill=(240, 255, 255, 255))

    # Floating amethysts
    draw.polygon([(26, 10), (28, 8), (30, 10), (28, 12)], fill=(215, 110, 255, 255))
    draw.polygon([(4, 20), (6, 18), (8, 20), (6, 22)], fill=(215, 110, 255, 255))

    return img

def create_tank_golem():
    # 48x48: Massive stone & magma brute
    img = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Octagonal Stone Carapace
    outer_pts = [
        (16, 4), (32, 4), (44, 16), (44, 32),
        (32, 44), (16, 44), (4, 32), (4, 16)
    ]
    draw.polygon(outer_pts, fill=(65, 70, 80, 255))

    # Armor plating bevels
    mid_pts = [
        (18, 8), (30, 8), (40, 18), (40, 30),
        (30, 40), (18, 40), (8, 30), (8, 18)
    ]
    draw.polygon(mid_pts, fill=(90, 95, 110, 255))

    # Basalt armor plates
    draw.rectangle([12, 12, 22, 22], fill=(75, 80, 92, 255))
    draw.rectangle([26, 12, 36, 22], fill=(75, 80, 92, 255))
    draw.rectangle([12, 26, 22, 36], fill=(75, 80, 92, 255))
    draw.rectangle([26, 26, 36, 36], fill=(75, 80, 92, 255))

    # Molten Magma Fissures
    draw.line([(8, 24), (40, 24)], fill=(235, 110, 25, 255), width=2)
    draw.line([(24, 8), (24, 40)], fill=(235, 110, 25, 255), width=2)
    draw.line([(14, 14), (34, 34)], fill=(255, 160, 40, 255), width=1)
    draw.line([(34, 14), (14, 34)], fill=(255, 160, 40, 255), width=1)

    # Central Magma Core
    draw.ellipse([18, 18, 30, 30], fill=(245, 120, 20, 255))
    draw.ellipse([20, 20, 28, 28], fill=(255, 205, 60, 255))
    draw.ellipse([22, 22, 26, 26], fill=(255, 255, 210, 255))

    # Magma Eyes / Visor Slits
    draw.rectangle([16, 14, 21, 16], fill=(255, 230, 90, 255))
    draw.rectangle([27, 14, 32, 16], fill=(255, 230, 90, 255))

    return img

def create_projectile_fire():
    # 24x24 Firebolt
    img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Outer fire aura
    draw.ellipse([2, 2, 22, 22], fill=(255, 80, 20, 160))
    # Mid flame
    draw.ellipse([5, 5, 19, 19], fill=(255, 150, 30, 240))
    # Core
    draw.ellipse([8, 8, 16, 16], fill=(255, 235, 80, 255))
    # White-hot nucleus
    draw.ellipse([10, 10, 14, 14], fill=(255, 255, 255, 255))
    return img

def create_projectile_ice():
    # 24x24 Frost Shard
    img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    pts = [(12, 1), (21, 12), (12, 23), (3, 12)]
    draw.polygon(pts, fill=(80, 200, 255, 230))
    inner = [(12, 4), (18, 12), (12, 20), (6, 12)]
    draw.polygon(inner, fill=(180, 240, 255, 255))
    draw.line([(12, 2), (12, 22)], fill=(255, 255, 255, 255), width=1)
    draw.line([(4, 12), (20, 12)], fill=(255, 255, 255, 255), width=1)
    return img

def create_projectile_lightning():
    # 24x24 Spark Arc
    img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse([3, 3, 21, 21], fill=(255, 235, 60, 140))
    bolt = [(14, 2), (7, 11), (13, 11), (9, 22), (18, 10), (12, 10)]
    draw.polygon(bolt, fill=(255, 255, 120, 255))
    draw.ellipse([9, 9, 15, 15], fill=(255, 255, 255, 255))
    return img

def create_projectile_wind():
    # 24x24 Gale Burst
    img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse([3, 3, 21, 21], fill=(90, 245, 160, 140))
    draw.arc([4, 4, 20, 20], start=30, end=240, fill=(160, 255, 210, 255), width=3)
    draw.arc([7, 7, 17, 17], start=180, end=360, fill=(230, 255, 245, 255), width=2)
    draw.ellipse([10, 10, 14, 14], fill=(255, 255, 255, 255))
    return img

def create_projectile_earth():
    # 24x24 Boulder
    img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    pts = [(8, 2), (17, 3), (22, 10), (20, 19), (12, 22), (3, 17), (4, 8)]
    draw.polygon(pts, fill=(155, 105, 60, 255))
    inner = [(9, 5), (16, 6), (19, 11), (17, 17), (12, 19), (6, 15), (7, 9)]
    draw.polygon(inner, fill=(185, 130, 80, 255))
    draw.line([(10, 8), (15, 14), (13, 18)], fill=(90, 55, 30, 255), width=1)
    return img

def create_projectile_enemy():
    # 24x24 Shadow / Necrotic missile
    img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse([2, 2, 22, 22], fill=(160, 45, 200, 150))
    draw.ellipse([5, 5, 19, 19], fill=(120, 25, 165, 230))
    draw.ellipse([8, 8, 16, 16], fill=(230, 120, 255, 255))
    draw.ellipse([10, 10, 14, 14], fill=(255, 230, 255, 255))
    return img

def create_exp_gem(base_color, core_color):
    # 16x16 Faceted diamond EXP gem
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Diamond outer
    pts = [(8, 1), (14, 8), (8, 15), (2, 8)]
    draw.polygon(pts, fill=base_color)
    # Diamond facet
    facet = [(8, 3), (12, 8), (8, 13), (4, 8)]
    draw.polygon(facet, fill=core_color)
    # Highlight gleam
    draw.polygon([(8, 3), (10, 5), (8, 7), (6, 5)], fill=(255, 255, 255, 230))
    return img

def create_floor_tile():
    # 64x64 Arcane flagstone paving
    img = Image.new("RGBA", (64, 64), (24, 29, 41, 255))
    draw = ImageDraw.Draw(img)

    # Flagstone borders / grout lines
    draw.rectangle([0, 0, 63, 63], outline=(15, 18, 26, 255), width=2)
    # Stone texture bevel highlight
    draw.line([(2, 2), (61, 2)], fill=(38, 46, 64, 255), width=1)
    draw.line([(2, 2), (2, 61)], fill=(38, 46, 64, 255), width=1)
    draw.line([(2, 61), (61, 61)], fill=(18, 22, 31, 255), width=1)
    draw.line([(61, 2), (61, 61)], fill=(18, 22, 31, 255), width=1)

    # Subtle internal stone vein/fissure
    draw.line([(14, 12), (24, 16), (36, 15)], fill=(30, 36, 50, 255), width=1)
    draw.line([(42, 44), (52, 48)], fill=(30, 36, 50, 255), width=1)

    return img

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    textures_dir = os.path.join(root, "assets", "textures")

    assets = {
        os.path.join(textures_dir, "characters", "wizard.png"): create_wizard(),
        os.path.join(textures_dir, "enemies", "melee_fiend.png"): create_melee_fiend(),
        os.path.join(textures_dir, "enemies", "ranged_cultist.png"): create_ranged_cultist(),
        os.path.join(textures_dir, "enemies", "tank_golem.png"): create_tank_golem(),
        os.path.join(textures_dir, "projectiles", "spell_fire.png"): create_projectile_fire(),
        os.path.join(textures_dir, "projectiles", "spell_ice.png"): create_projectile_ice(),
        os.path.join(textures_dir, "projectiles", "spell_lightning.png"): create_projectile_lightning(),
        os.path.join(textures_dir, "projectiles", "spell_wind.png"): create_projectile_wind(),
        os.path.join(textures_dir, "projectiles", "spell_earth.png"): create_projectile_earth(),
        os.path.join(textures_dir, "projectiles", "projectile_enemy.png"): create_projectile_enemy(),
        os.path.join(textures_dir, "pickups", "gem_cyan.png"): create_exp_gem((50, 195, 255, 255), (180, 240, 255, 255)),
        os.path.join(textures_dir, "pickups", "gem_emerald.png"): create_exp_gem((45, 225, 135, 255), (180, 255, 215, 255)),
        os.path.join(textures_dir, "pickups", "gem_gold.png"): create_exp_gem((255, 205, 45, 255), (255, 250, 190, 255)),
        os.path.join(textures_dir, "environment", "floor_tile.png"): create_floor_tile(),
    }

    for path, img in assets.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        print(f"Generated: {path} ({img.size[0]}x{img.size[1]})")

    print("\nAll starter 2D art assets generated successfully.")

if __name__ == "__main__":
    main()
