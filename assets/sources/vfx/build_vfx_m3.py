#!/usr/bin/env python3
"""
Spell Storm Milestone 3 VFX Pipeline: Pixelbolt Compiler + Post-Process Polish + Previews.

Builds ONLY Milestone 3 VFX deliverables (NEVER modifies Milestone 2 fire_* files):
- 6 Canonical 24x24 projectiles (assets/textures/projectiles/):
  * spell_ice.png, spell_lightning.png, spell_wind.png, spell_earth.png
  * projectile_player.png, projectile_enemy.png
- 6 Flight animation sheets 144x24 = 6x 24x24 (assets/textures/vfx/):
  * spell_ice_sheet.png, spell_lightning_sheet.png, spell_wind_sheet.png, spell_earth_sheet.png
  * projectile_player_sheet.png, projectile_enemy_sheet.png
- 5 Elemental impact sheets 192x32 = 6x 32x32 (assets/textures/vfx/):
  * vfx_ice_impact.png, vfx_lightning_impact.png, vfx_wind_impact.png
  * vfx_earth_impact.png, vfx_arcane_impact.png
- Previews (docs/previews/milestone3/):
  * vfx_m3_contact_sheet.png
  * vfx_all_elements_showcase.gif
"""

from __future__ import annotations

import math
import subprocess
import tempfile
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[3]
SRC_VFX = REPO_ROOT / "assets/sources/vfx"
PROJ_DIR = REPO_ROOT / "assets/textures/projectiles"
VFX_DIR = REPO_ROOT / "assets/textures/vfx"
PREVIEW_DIR = REPO_ROOT / "docs/previews/milestone3"


def hex_to_rgb(hex_str: str) -> tuple[int, int, int]:
    h = hex_str.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def compile_and_render_fx(fx_name: str, expected_frames: int = 6) -> list[Image.Image]:
    """Compile and render an .fx.json recipe via pixelbolt into temporary directory."""
    fx_path = SRC_VFX / f"{fx_name}.fx.json"
    if not fx_path.exists():
        raise FileNotFoundError(f"Missing recipe: {fx_path}")
    with tempfile.TemporaryDirectory(prefix=f"pb_{fx_name}_") as tmp_str:
        tmp = Path(tmp_str)
        recipe_out = tmp / f"{fx_name}.recipe.json"
        render_dir = tmp / "render"
        c_res = subprocess.run(
            ["npx", "-y", "pixelbolt@0.16.0", "compile", str(fx_path), "--out", str(recipe_out)],
            capture_output=True,
            text=True,
            check=False,
        )
        if c_res.returncode != 0:
            raise RuntimeError(f"pixelbolt compile failed for {fx_name}:\n{c_res.stdout}\n{c_res.stderr}")
        r_res = subprocess.run(
            ["npx", "-y", "pixelbolt@0.16.0", "render", str(recipe_out), "--out", str(render_dir), "--force"],
            capture_output=True,
            text=True,
            check=False,
        )
        if r_res.returncode != 0:
            raise RuntimeError(f"pixelbolt render failed for {fx_name}:\n{r_res.stdout}\n{r_res.stderr}")

        frames_dir = render_dir / "frames"
        frame_files = sorted(frames_dir.glob("*.png"), key=lambda p: int(p.stem.rsplit("-", 1)[-1]))
        if len(frame_files) != expected_frames:
            raise RuntimeError(f"{fx_name}: expected {expected_frames} frames, found {len(frame_files)}: {frame_files}")
        loaded: list[Image.Image] = []
        for fpath in frame_files:
            with Image.open(fpath) as im:
                loaded.append(im.convert("RGBA"))
        return loaded


def add_translucent_aura(
    frame: Image.Image,
    glow_rgb: tuple[int, int, int],
    radius: float = 2.6,
    max_alpha: int = 86,
    min_emissive_luma: int = 95,
    exclude_cols_edge: bool = False,
) -> Image.Image:
    """
    Match Milestone 2 Fire VFX aura signature:
    Paint smooth translucent elemental glow ONLY onto alpha==0 pixels surrounding
    bright/emissive pixels (preserving crisp dark outlines/smoke/void cores).
    """
    w, h = frame.size
    arr = np.asarray(frame, dtype=np.uint8).copy()
    a = arr[:, :, 3]
    luma = 0.299 * arr[:, :, 0] + 0.587 * arr[:, :, 1] + 0.114 * arr[:, :, 2]
    emissive = (a >= 180) & (luma >= min_emissive_luma)
    ey, ex = np.nonzero(emissive)
    if len(ex) == 0:
        return frame.copy()

    yy, xx = np.mgrid[0:h, 0:w]
    best_alpha = np.zeros((h, w), dtype=np.float32)

    # Compute exact minimum Euclidean distance to closest emissive pixel
    dy = yy[:, :, None].astype(np.float32) - ey[None, None, :].astype(np.float32)
    dx = xx[:, :, None].astype(np.float32) - ex[None, None, :].astype(np.float32)
    dist = np.sqrt(dx * dx + dy * dy).min(axis=2)

    mask = (a == 0) & (dist > 0.0) & (dist <= radius)
    if exclude_cols_edge:
        mask[:, 0] = False
        mask[:, w - 1] = False
    best_alpha[mask] = max_alpha * np.power(1.0 - (dist[mask] / radius), 1.65)

    out_a = best_alpha.clip(0, 255).astype(np.uint8)
    arr[mask, 0] = glow_rgb[0]
    arr[mask, 1] = glow_rgb[1]
    arr[mask, 2] = glow_rgb[2]
    arr[mask, 3] = out_a[mask]
    return Image.fromarray(arr, "RGBA")


def clamp_outer_columns(frame: Image.Image) -> Image.Image:
    """Ensure col 0 and col W-1 are completely alpha==0 for canonical BBox safety."""
    arr = np.asarray(frame, dtype=np.uint8).copy()
    arr[:, 0, :] = 0
    arr[:, -1, :] = 0
    return Image.fromarray(arr, "RGBA")


def draw_crisp_earth_crag_head(frame: Image.Image, frame_idx: int) -> Image.Image:
    """
    Stamp a tumbling, faceted 6-frame jagged crag meteor head around (15, 12)
    split by glowing magma-amber fissures over Pixelbolt magma wake & splinters.
    """
    w, h = frame.size
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = canvas.load()

    c_dark = (20, 16, 26, 255)      # #14101a chipped edge ink
    c_crust = (54, 31, 24, 255)     # #361f18 dark basalt
    c_mid = (110, 68, 39, 255)      # #6e4427 mid crag brown
    c_lit = (184, 125, 59, 255)     # #b87d3b sunlit rock ridge
    c_amber = (245, 217, 150, 255)  # #f5d996 glowing magma fissure
    c_white = (255, 247, 214, 255)  # #fff7d6 white-hot fissure core

    cx, cy = 15.0, 12.0
    rot = frame_idx * (math.pi / 3.0)  # 60 deg per step -> full 360 deg over 6 frames!
    # Angular faceted radius modulation creates rocky jagged crag lobes
    lobe_angles = [0.1, 1.15, 2.2, 3.1, 4.3, 5.3]
    lobe_amps = [1.18, 0.86, 1.14, 0.90, 1.20, 0.88]

    rock_mask = np.zeros((h, w), dtype=bool)
    for y in range(4, 21):
        for x in range(8, 22):
            dx = x - cx
            dy = y - cy
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx) - rot
            # Smooth-stepped polygonal crag envelope centered at r~4.8
            mod = 0.0
            for la, amp in zip(lobe_angles, lobe_amps):
                diff = math.cos(ang - la)
                if diff > 0.3:
                    mod = max(mod, (amp - 1.0) * (diff ** 2))
            limit = 4.75 + 3.2 * mod
            if r <= limit:
                rock_mask[y, x] = True

    # Draw 1px crisp dark outline around crag rock
    for y in range(4, 21):
        for x in range(8, 22):
            if rock_mask[y, x]:
                is_edge = not (
                    rock_mask[y - 1, x] and rock_mask[y + 1, x] and rock_mask[y, x - 1] and rock_mask[y, x + 1]
                )
                if is_edge:
                    px[x, y] = c_crust if (x - cx) < -1.5 else c_dark
                else:
                    dx = x - cx
                    dy = y - cy
                    # 135 deg top-left rock facet lighting
                    light = (-dx * 0.65 - dy * 0.75)
                    ang = math.atan2(dy, dx) - rot
                    # Twin crossing jagged molten fissure veins rotating with rock
                    v1 = abs(math.sin(ang * 2.0 + 0.35) * math.hypot(dx, dy))
                    v2 = abs(math.cos(ang * 1.0 - 0.5) * math.hypot(dx, dy) * 0.55)
                    r = math.hypot(dx, dy)
                    if r < 1.15:
                        px[x, y] = c_white
                    elif v1 < 0.52 or (r < 3.4 and v2 < 0.45):
                        px[x, y] = c_white if r < 2.1 else c_amber
                    elif light > 1.0:
                        px[x, y] = c_lit
                    elif light > -1.2:
                        px[x, y] = c_mid
                    else:
                        px[x, y] = c_crust

    out = Image.alpha_composite(frame, canvas)
    return clamp_outer_columns(out)


def draw_crisp_void_eye_head(frame: Image.Image, frame_idx: int) -> Image.Image:
    """
    Stamp a menacing Abyssal Void Eye Orb around (15, 12) with glaring slit pupil
    over Pixelbolt writhing shadow tendrils.
    """
    w, h = frame.size
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = canvas.load()

    c_abyss = (43, 13, 41, 255)      # #2b0d29 void outline & pupil slit
    c_plum = (122, 28, 84, 255)      # #7a1c54 iris outer shadow
    c_mag = (224, 60, 138, 255)      # #e03c8a glowing magenta iris
    c_glint = (255, 214, 245, 255)   # #ffd6f5 glaring pink eye specular

    cx, cy = 15.0, 12.0
    # Subtle sinister squint/pulse across 6-frame loop
    pupil_w = 0.75 if frame_idx in (2, 3) else 1.05
    pupil_h = 3.2 if frame_idx in (2, 3) else 2.7

    for y in range(6, 19):
        for x in range(9, 22):
            dx = (x - cx) * 0.94  # slightly horizontal demonic eye orb
            dy = y - cy
            r = math.hypot(dx, dy)
            if r <= 4.7:
                if r >= 3.85:
                    px[x, y] = c_abyss
                elif r >= 3.05:
                    px[x, y] = c_plum
                else:
                    # Sinister narrow vertical cat-slit void pupil
                    if abs(x - cx) <= pupil_w and abs(dy) <= pupil_h:
                        px[x, y] = c_abyss
                    else:
                        # Iris & blazing top-left catchlight
                        if dx < -0.5 and dy < -0.3 and r < 2.7:
                            px[x, y] = c_glint
                        elif abs(x - (cx + 1.5)) <= 0.6 and abs(y - (cy + 1.0)) <= 0.6:
                            px[x, y] = c_glint
                        else:
                            px[x, y] = c_mag

    out = Image.alpha_composite(frame, canvas)
    return clamp_outer_columns(out)


def polish_spear_projectile(
    frame: Image.Image,
    core_rgb: tuple[int, int, int],
    mid_rgb: tuple[int, int, int],
) -> Image.Image:
    """Sharpen white-hot forward core around (14..16, 12) and leading tip (17..19, 12)."""
    arr = np.asarray(frame, dtype=np.uint8).copy()
    for x in (14, 15, 16):
        arr[11:14, x, :3] = core_rgb
        arr[11:14, x, 3] = 255
    for x in (17, 18, 19):
        arr[12, x, :3] = core_rgb if x <= 18 else mid_rgb
        arr[12, x, 3] = 255
    return clamp_outer_columns(Image.fromarray(arr, "RGBA"))


def assemble_strip(frames: list[Image.Image]) -> Image.Image:
    w, h = frames[0].size
    strip = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.paste(fr, (i * w, 0))
    return strip


def verify_projectile_invariants(path: Path) -> tuple[tuple[int, int, int, int], tuple[float, float]]:
    """Enforce AssetBBoxTest invariants cleanly across all 6 canonical 24x24 projectiles."""
    with Image.open(path) as opened:
        im = opened.convert("RGBA")
    assert im.size == (24, 24), f"{path}: expected 24x24, got {im.size}"
    px = im.load()
    for y in range(24):
        assert px[0, y][3] == 0, f"{path}: col 0 alpha={px[0, y][3]} != 0 at y={y}"
        assert px[23, y][3] == 0, f"{path}: col 23 alpha={px[23, y][3]} != 0 at y={y}"
    xs = [x for y in range(24) for x in range(24) if px[x, y][3] > 0]
    ys = [y for y in range(24) for x in range(24) if px[x, y][3] > 0]
    w = max(xs) - min(xs) + 1
    h = max(ys) - min(ys) + 1
    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    assert w >= 12 and h >= 12, f"{path}: bbox too small {w}x{h}"
    assert abs(cx - 12.0) <= 2.5 and abs(cy - 12.0) <= 2.5, f"{path}: bbox center ({cx:.2f}, {cy:.2f}) out of bounds"
    return (min(xs), min(ys), max(xs), max(ys)), (cx, cy)


def upscale_nearest(im: Image.Image, scale: int) -> Image.Image:
    arr = np.asarray(im.convert("RGBA"), dtype=np.uint8)
    scaled = np.repeat(np.repeat(arr, scale, axis=0), scale, axis=1)
    return Image.fromarray(scaled, "RGBA")


def draw_checker(draw: ImageDraw.ImageDraw, x0: int, y0: int, w: int, h: int, dark: bool = True, cell: int = 12) -> None:
    c1, c2 = ((58, 62, 72, 255), (44, 47, 56, 255)) if dark else ((220, 224, 232, 255), (192, 198, 210, 255))
    for y in range(y0, y0 + h, cell):
        for x in range(x0, x0 + w, cell):
            col = c1 if ((x - x0) // cell + (y - y0) // cell) % 2 == 0 else c2
            draw.rectangle([x, y, min(x + cell - 1, x0 + w - 1), min(y + cell - 1, y0 + h - 1)], fill=col)


def build_contact_sheet(
    proj_rows: list[tuple[str, Image.Image, list[Image.Image]]],
    impact_rows: list[tuple[str, list[Image.Image]]],
) -> Image.Image:
    scale = 5
    p_box = 24 * scale   # 120x120
    i_box = 32 * scale   # 160x160
    font = ImageFont.load_default()
    pad_x = 24
    gap = 8

    # 1 canon + 6 flight frames = 7 tiles on SLATE + 1 canon on LIGHT CHECKER for silhouette clarity
    w_proj = pad_x * 2 + 8 * (p_box + gap)
    w_imp = pad_x * 2 + 6 * (i_box + gap)
    sheet_w = max(w_proj, w_imp)
    sheet_h = 68 + len(proj_rows) * (24 + p_box + 22) + 32 + len(impact_rows) * (24 + i_box + 22) + 20

    canvas = Image.new("RGBA", (sheet_w, sheet_h), (18, 20, 28, 255))
    draw = ImageDraw.Draw(canvas)
    draw.text((pad_x, 14), "SPELL STORM - MILESTONE 3: SPELL PROJECTILES, FLIGHT LOOPS & IMPACT DETONATIONS (5x Nearest-Neighbor)", fill=(255, 245, 214, 255), font=font)
    draw.text((pad_x, 34), "White-Hot Plasma Cores | Translucent Elemental Auras (Alpha 0..255) | Right-Oriented 0 deg | Matched to M2 Fire Bar", fill=(168, 178, 198, 255), font=font)

    cur_y = 66
    for label, canon, frames in proj_rows:
        draw.text((pad_x, cur_y), label, fill=(143, 245, 255, 255), font=font)
        cur_y += 20
        tiles = [("CANON 24", canon, False)] + [(f"f{i}", fr, False) for i, fr in enumerate(frames)] + [("CHECKER", canon, True)]
        bx = pad_x
        for tag, im, is_checker in tiles:
            if is_checker:
                draw_checker(draw, bx, cur_y, p_box, p_box, dark=True)
            else:
                draw.rectangle([bx, cur_y, bx + p_box - 1, cur_y + p_box - 1], fill=(27, 32, 41, 255))
            draw.rectangle([bx, cur_y, bx + p_box - 1, cur_y + p_box - 1], outline=(72, 80, 98, 255))
            canvas.alpha_composite(upscale_nearest(im, scale), (bx, cur_y))
            draw.text((bx + 4, cur_y + p_box + 3), tag, fill=(190, 200, 218, 255), font=font)
            bx += p_box + gap
        cur_y += p_box + 24

    cur_y += 14
    draw.text((pad_x, cur_y), "=== ELEMENTAL IMPACT DETONATION SHEETS (192x32 = 6x 32x32) ===", fill=(255, 226, 89, 255), font=font)
    cur_y += 26
    for label, frames in impact_rows:
        draw.text((pad_x, cur_y), label, fill=(143, 245, 255, 255), font=font)
        cur_y += 20
        bx = pad_x
        for i, fr in enumerate(frames):
            if i == 5:
                draw_checker(draw, bx, cur_y, i_box, i_box, dark=True)
            else:
                draw.rectangle([bx, cur_y, bx + i_box - 1, cur_y + i_box - 1], fill=(27, 32, 41, 255))
            draw.rectangle([bx, cur_y, bx + i_box - 1, cur_y + i_box - 1], outline=(72, 80, 98, 255))
            canvas.alpha_composite(upscale_nearest(fr, scale), (bx, cur_y))
            draw.text((bx + 4, cur_y + i_box + 3), f"hit f{i}", fill=(190, 200, 218, 255), font=font)
            bx += i_box + gap
        cur_y += i_box + 24

    return canvas


def load_strip_frames(path: Path, frame_w: int, frame_h: int) -> list[Image.Image]:
    with Image.open(path) as im:
        rgba = im.convert("RGBA")
    n = rgba.size[0] // frame_w
    return [rgba.crop((i * frame_w, 0, (i + 1) * frame_w, frame_h)) for i in range(n)]


def build_all_elements_gif(
    flight_by_name: dict[str, list[Image.Image]],
    impact_by_name: dict[str, list[Image.Image]],
    out_path: Path,
) -> None:
    """
    Create animated showcase GIF at 5x scale displaying all 7 Spell Storm elements side-by-side
    (M2 Fire + Ice + Lightning + Wind + Earth + Player Arcane + Enemy Void).
    """
    fire_flight = load_strip_frames(VFX_DIR / "spell_fire_sheet.png", 24, 24)
    fire_impact = load_strip_frames(VFX_DIR / "vfx_fire_impact.png", 32, 32)

    elements = [
        ("FIRE (M2)", fire_flight, fire_impact),
        ("ICE", flight_by_name["ice"], impact_by_name["ice"]),
        ("LIGHTNING", flight_by_name["lightning"], impact_by_name["lightning"]),
        ("WIND", flight_by_name["wind"], impact_by_name["wind"]),
        ("EARTH", flight_by_name["earth"], impact_by_name["earth"]),
        ("ARCANE", flight_by_name["arcane"], impact_by_name["arcane"]),
        ("VOID", flight_by_name["void"], None),
    ]

    scale = 5
    col_w = 32 * scale + 16  # 176px per element column
    pad_x = 18
    sheet_w = pad_x * 2 + len(elements) * col_w
    sheet_h = 56 + 24 * scale + 28 + 32 * scale + 28
    font = ImageFont.load_default()

    # Run 12 frames total (2 full 6-frame cycles) for smooth continuous showcase
    gif_frames: list[Image.Image] = []
    for tick in range(12):
        f_idx = tick % 6
        i_idx = tick % 6
        bg = Image.new("RGBA", (sheet_w, sheet_h), (18, 20, 28, 255))
        draw = ImageDraw.Draw(bg)
        draw.text((pad_x, 12), "SPELL STORM - ALL ELEMENTS VFX SHOWCASE (M2 FIRE + MILESTONE 3)", fill=(255, 245, 214, 255), font=font)
        draw.text((pad_x, 30), "Top Row: 24x24 Flight Loop | Bottom Row: 32x32 Impact Detonation (5x Nearest)", fill=(168, 178, 198, 255), font=font)

        for col_i, (title, fl_frames, im_frames) in enumerate(elements):
            cx = pad_x + col_i * col_w + col_w // 2
            draw.text((cx - len(title) * 3, 52), title, fill=(143, 245, 255, 255), font=font)
            # Flight tile (120x120 centered)
            fy = 70
            fx = cx - (24 * scale) // 2
            draw.rectangle([fx, fy, fx + 24 * scale - 1, fy + 24 * scale - 1], fill=(27, 32, 41, 255), outline=(72, 80, 98, 255))
            bg.alpha_composite(upscale_nearest(fl_frames[f_idx], scale), (fx, fy))

            # Impact tile (160x160 centered)
            iy = fy + 24 * scale + 18
            ix = cx - (32 * scale) // 2
            draw.rectangle([ix, iy, ix + 32 * scale - 1, iy + 32 * scale - 1], fill=(27, 32, 41, 255), outline=(72, 80, 98, 255))
            if im_frames is not None:
                bg.alpha_composite(upscale_nearest(im_frames[i_idx], scale), (ix, iy))
            else:
                draw.text((ix + 24, iy + 72), "ENEMY PROJ ONLY", fill=(180, 120, 160, 255), font=font)

        rgb = bg.convert("RGB")
        gif_frames.append(rgb.convert("P", palette=Image.Palette.ADAPTIVE, colors=255))

    gif_frames[0].save(
        out_path,
        save_all=True,
        append_images=gif_frames[1:],
        duration=75,
        loop=0,
    )


def main() -> None:
    PROJ_DIR.mkdir(parents=True, exist_ok=True)
    VFX_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    # 1. Process 6 Flights + Canonical Projectiles
    flight_specs = [
        ("ice", "ice_flight", "spell_ice.png", "spell_ice_sheet.png", "#6ce5ff", "#ffffff", "#e8ffff", 95),
        ("lightning", "lightning_flight", "spell_lightning.png", "spell_lightning_sheet.png", "#c884ff", "#ffffff", "#ffe954", 100),
        ("wind", "wind_flight", "spell_wind.png", "spell_wind_sheet.png", "#7bf2a8", "#f0fff4", "#7bf2a8", 95),
        ("earth", "earth_flight", "spell_earth.png", "spell_earth_sheet.png", "#f5d996", "#fff7d6", "#f5d996", 160),
        ("arcane", "arcane_flight", "projectile_player.png", "projectile_player_sheet.png", "#8ff5ff", "#ffffff", "#8ff5ff", 95),
        ("void", "void_flight", "projectile_enemy.png", "projectile_enemy_sheet.png", "#e03c8a", "#ffd6f5", "#e03c8a", 95),
    ]

    proj_rows: list[tuple[str, Image.Image, list[Image.Image]]] = []
    flight_by_name: dict[str, list[Image.Image]] = {}

    for key, fx_name, canon_name, sheet_name, aura_hex, core_hex, mid_hex, min_luma in flight_specs:
        raw_frames = compile_and_render_fx(fx_name, expected_frames=6)
        aura_rgb = hex_to_rgb(aura_hex)
        core_rgb = hex_to_rgb(core_hex)
        mid_rgb = hex_to_rgb(mid_hex)

        processed: list[Image.Image] = []
        for idx, fr in enumerate(raw_frames):
            cur = fr
            if key == "earth":
                cur = draw_crisp_earth_crag_head(cur, idx)
            elif key == "void":
                cur = draw_crisp_void_eye_head(cur, idx)
            elif key in ("ice", "lightning"):
                cur = polish_spear_projectile(cur, core_rgb, mid_rgb)
            else:
                cur = clamp_outer_columns(cur)
            cur = add_translucent_aura(
                cur,
                glow_rgb=aura_rgb,
                radius=2.5,
                max_alpha=84,
                min_emissive_luma=min_luma,
                exclude_cols_edge=True,
            )
            processed.append(cur)

        canon = processed[0]
        if key == "earth":
            canon = draw_crisp_earth_crag_head(raw_frames[0], 0)
            canon = add_translucent_aura(canon, aura_rgb, radius=2.5, max_alpha=84, min_emissive_luma=160, exclude_cols_edge=True)
        elif key == "void":
            canon = draw_crisp_void_eye_head(raw_frames[0], 0)
            canon = add_translucent_aura(canon, aura_rgb, radius=2.5, max_alpha=84, min_emissive_luma=95, exclude_cols_edge=True)

        canon_path = PROJ_DIR / canon_name
        sheet_path = VFX_DIR / sheet_name
        canon.save(canon_path)
        sheet = assemble_strip(processed)
        assert sheet.size == (144, 24), f"{sheet_path}: bad size {sheet.size}"
        sheet.save(sheet_path)

        bbox, center = verify_projectile_invariants(canon_path)
        print(f"OK {canon_name:24s} -> 24x24, bbox={bbox}, center=({center[0]:.2f},{center[1]:.2f}) | {sheet_name} -> {sheet.size}")
        proj_rows.append((f"{key.upper()}: {canon_name} (24x24) + {sheet_name} (144x24)", canon, processed))
        flight_by_name[key] = processed

    # 2. Process 5 Impacts (192x32)
    impact_specs = [
        ("ice", "ice_impact", "vfx_ice_impact.png", "#6ce5ff", 95),
        ("lightning", "lightning_impact", "vfx_lightning_impact.png", "#c884ff", 95),
        ("wind", "wind_impact", "vfx_wind_impact.png", "#7bf2a8", 90),
        ("earth", "earth_impact", "vfx_earth_impact.png", "#f5d996", 155),
        ("arcane", "arcane_impact", "vfx_arcane_impact.png", "#8ff5ff", 95),
    ]

    impact_rows: list[tuple[str, list[Image.Image]]] = []
    impact_by_name: dict[str, list[Image.Image]] = {}

    for key, fx_name, out_name, aura_hex, min_luma in impact_specs:
        raw_frames = compile_and_render_fx(fx_name, expected_frames=6)
        aura_rgb = hex_to_rgb(aura_hex)
        processed = [
            add_translucent_aura(fr, glow_rgb=aura_rgb, radius=2.5, max_alpha=82, min_emissive_luma=min_luma, exclude_cols_edge=False)
            for fr in raw_frames
        ]
        out_path = VFX_DIR / out_name
        sheet = assemble_strip(processed)
        assert sheet.size == (192, 32), f"{out_path}: bad size {sheet.size}"
        sheet.save(out_path)
        print(f"OK {out_name:24s} -> {sheet.size}")
        impact_rows.append((f"{key.upper()} IMPACT: {out_name} (192x32 = 6x 32x32)", processed))
        impact_by_name[key] = processed

    # 3. Generate Contact Sheet & All-Elements Showcase GIF
    contact = build_contact_sheet(proj_rows, impact_rows)
    contact_path = PREVIEW_DIR / "vfx_m3_contact_sheet.png"
    contact.save(contact_path)
    print(f"OK contact sheet: {contact_path.relative_to(REPO_ROOT)} ({contact.size[0]}x{contact.size[1]})")

    gif_path = PREVIEW_DIR / "vfx_all_elements_showcase.gif"
    build_all_elements_gif(flight_by_name, impact_by_name, gif_path)
    print(f"OK showcase gif:  {gif_path.relative_to(REPO_ROOT)} ({gif_path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
