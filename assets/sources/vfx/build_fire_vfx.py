#!/usr/bin/env python3
"""Build and refine the Spell Storm Milestone 2 Fire Spell VFX suite from Pixelbolt sources."""

from __future__ import annotations

import math
import subprocess
import tempfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = Path(__file__).resolve().parents[3]
VFX_SRC_DIR = REPO_ROOT / "assets/sources/vfx"
PROJECTILE_OUT = REPO_ROOT / "assets/textures/projectiles/spell_fire.png"
SHEET_OUT = REPO_ROOT / "assets/textures/vfx/spell_fire_sheet.png"
IMPACT_OUT = REPO_ROOT / "assets/textures/vfx/vfx_fire_impact.png"
CAST_OUT = REPO_ROOT / "assets/textures/vfx/vfx_fire_cast.png"
PREVIEW_DIR = REPO_ROOT / "docs/previews/milestone2"
CONTACT_SHEET_OUT = PREVIEW_DIR / "vfx_fire_contact_sheet.png"
SHOWCASE_GIF_OUT = PREVIEW_DIR / "vfx_fire_showcase.gif"

PIXELBOLT_CMD = ["npx", "-y", "pixelbolt@0.16.0"]

# Fire palette luminance weights for radial translucent glow emission
FIRE_HEAT = {
    (255, 251, 230): 1.00,  # #fffbe6 white-hot core
    (255, 224, 84): 0.88,   # #ffe054 solar gold
    (255, 158, 36): 0.72,   # #ff9e24 blazing orange
    (242, 92, 34): 0.54,    # #f25c22 fiery red-orange
    (191, 38, 38): 0.34,    # #bf2626 crimson
    (120, 23, 34): 0.14,    # #781722 deep crimson wake
    (61, 12, 24): 0.04,     # #3d0c18 ember dark
}


def run_pixelbolt(fx_path: Path, work_dir: Path, name: str, anim_name: str, frame_count: int) -> list[Path]:
    recipe_path = work_dir / f"{name}.recipe.json"
    out_dir = work_dir / f"{name}_out"
    subprocess.run(
        [*PIXELBOLT_CMD, "compile", str(fx_path), "--out", str(recipe_path)],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    subprocess.run(
        [*PIXELBOLT_CMD, "render", str(recipe_path), "--out", str(out_dir), "--force"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    frame_paths = [out_dir / "frames" / f"{anim_name}-{i}.png" for i in range(frame_count)]
    for fp in frame_paths:
        if not fp.exists():
            raise FileNotFoundError(f"Expected Pixelbolt frame not found: {fp}")
    return frame_paths


def add_translucent_fire_aura(
    frame: Image.Image,
    radius: float = 2.6,
    max_alpha: int = 86,
    warm_shift: float = 1.0,
) -> Image.Image:
    """Apply a smooth radial translucent amber/gold aura around fiery pixels only.

    Opaque/semi-opaque source pixels remain untouched so the crisp pixel silhouette
    and white-hot cores stay razor sharp; only transparent pixels receive soft glow.
    Charcoal smoke pixels are excluded from emitting glow.
    """
    w, h = frame.size
    src = frame.convert("RGBA")
    px = src.load()
    out = src.copy()
    out_px = out.load()

    # Collect fiery emitter pixels
    emitters: list[tuple[int, int, float]] = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            heat = FIRE_HEAT.get((r, g, b))
            if heat is None:
                # Non-fire (e.g. charcoal smoke has low r-b contrast); skip cool greys
                if r > b + 35 and a >= 200:
                    heat = max(0.0, min(1.0, (r * 0.4 + g * 0.6) / 255.0))
                else:
                    heat = 0.0
            if heat > 0.05:
                emitters.append((x, y, heat * (a / 255.0)))

    if not emitters:
        return out

    r_sq = radius * radius
    for y in range(h):
        for x in range(w):
            sr, sg, sb, sa = px[x, y]
            if sa > 0:
                continue
            acc = 0.0
            peak = 0.0
            for ex, ey, heat in emitters:
                dx = x - ex
                dy = y - ey
                d2 = dx * dx + dy * dy
                if d2 <= r_sq:
                    falloff = (1.0 - math.sqrt(d2) / radius) ** 1.65
                    contrib = heat * falloff
                    acc += contrib * 0.42
                    if contrib > peak:
                        peak = contrib
            strength = min(1.0, peak * 0.78 + acc * 0.35) * warm_shift
            alpha = int(round(strength * max_alpha))
            if alpha >= 6:
                # Blend aura hue from warm orange (#ff8e24) at outer edge to solar gold (#ffd44d) near hot cores
                t = min(1.0, strength * 1.15)
                gr = 255
                gg = int(round(138 + 80 * t))
                gb = int(round(32 + 52 * t))
                out_px[x, y] = (gr, gg, gb, min(max_alpha, alpha))
    return out


def refine_frames(
    frame_paths: list[Path],
    frame_w: int,
    frame_h: int,
    aura_radius: float,
    aura_alpha: int,
) -> list[Image.Image]:
    frames: list[Image.Image] = []
    for fp in frame_paths:
        im = Image.open(fp).convert("RGBA")
        if im.size != (frame_w, frame_h):
            raise ValueError(f"Expected {fp} size {(frame_w, frame_h)}, got {im.size}")
        refined = add_translucent_fire_aura(im, radius=aura_radius, max_alpha=aura_alpha)
        frames.append(refined)
    return frames


def polish_canonical_projectile(frame: Image.Image) -> Image.Image:
    """Polish frame 1 into the canonical 24x24 right-facing blazing fireball comet."""
    # Start from frame 1 (cleanest aerodynamic wake) and ensure tip & core read sharply at 1x
    img = frame.copy()
    px = img.load()
    # Strengthen crisp bright leading edge inside the comet head (core at x=14..16, y=12)
    # and add subtle trailing ember spark accents if needed while preserving soft aura
    core_overrides = {
        (14, 12): (255, 251, 230, 255),
        (15, 12): (255, 251, 230, 255),
        (16, 12): (255, 251, 230, 255),
        (15, 11): (255, 251, 230, 255),
        (15, 13): (255, 251, 230, 255),
        (17, 12): (255, 224, 84, 255),
        (18, 12): (255, 158, 36, 255),
        (19, 12): (242, 92, 34, 255),
        (20, 12): (191, 38, 38, 255),
    }
    for (x, y), color in core_overrides.items():
        px[x, y] = color
    return img


def assemble_strip(frames: list[Image.Image]) -> Image.Image:
    w, h = frames[0].size
    strip = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.paste(fr, (i * w, 0))
    return strip


def draw_checkerboard(draw: ImageDraw.ImageDraw, x0: int, y0: int, w: int, h: int, cell: int = 12) -> None:
    c1 = (58, 62, 72, 255)
    c2 = (44, 47, 56, 255)
    for y in range(y0, y0 + h, cell):
        for x in range(x0, x0 + w, cell):
            col = c1 if ((x - x0) // cell + (y - y0) // cell) % 2 == 0 else c2
            draw.rectangle([x, y, min(x + cell - 1, x0 + w - 1), min(y + cell - 1, y0 + h - 1)], fill=col)


def draw_arena_slate(draw: ImageDraw.ImageDraw, x0: int, y0: int, w: int, h: int) -> None:
    # Dark runic arena slate backdrop matching Spell Storm's palette (#1b2029 / #222733)
    draw.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=(27, 32, 41, 255))
    # Subtle 48px tile grid lines
    for gx in range(x0 + 48, x0 + w, 48):
        draw.line([gx, y0, gx, y0 + h - 1], fill=(35, 41, 52, 255), width=1)
    for gy in range(y0 + 48, y0 + h, 48):
        draw.line([x0, gy, x0 + w - 1, gy], fill=(35, 41, 52, 255), width=1)


def build_contact_sheet(
    canon: Image.Image,
    cast_frames: list[Image.Image],
    flight_frames: list[Image.Image],
    impact_frames: list[Image.Image],
) -> Image.Image:
    scale = 6
    font = ImageFont.load_default()
    pad_x = 24
    gap = 12
    row_gap = 28

    # Each section shows rows over Dark Arena Slate AND Checkerboard
    sections = [
        ("1. Canonical Projectile: spell_fire.png (24x24, core x=14..16 y=12, 0 deg RIGHT)", [canon], 24, 24),
        ("2. Staff Muzzle Cast Burst: vfx_fire_cast.png (96x24, 4 frames of 24x24)", cast_frames, 24, 24),
        ("3. Fireball Flight Loop: spell_fire_sheet.png (144x24, 6 frames of 24x24)", flight_frames, 24, 24),
        ("4. On-Hit Impact Detonation: vfx_fire_impact.png (192x32, 6 frames of 32x32)", impact_frames, 32, 32),
    ]

    max_frames = max(len(s[1]) for s in sections)
    max_fw = max(s[2] for s in sections) * scale
    sheet_w = max(1240, pad_x * 2 + max_frames * (max_fw + gap) + 120)

    # Calculate height
    header_h = 74
    total_h = header_h
    for _, _, _, fh in sections:
        box_h = fh * scale
        total_h += 24 + (box_h + 18) * 2 + row_gap
    total_h += 16

    canvas = Image.new("RGBA", (sheet_w, total_h), (18, 20, 28, 255))
    draw = ImageDraw.Draw(canvas)

    # Title + Palette Swatches
    draw.text((pad_x, 16), "SPELL STORM - MILESTONE 2: FIRE SPELL VFX PROTOTYPE (6x Nearest-Neighbor)", fill=(255, 245, 214, 255), font=font)
    draw.text((pad_x, 34), "7-Step Ramp + Translucent Amber Aura | Dark Arena Slate & Checkerboard Comparison", fill=(168, 178, 198, 255), font=font)

    swatches = [
        ("#fffbe6", (255, 251, 230, 255)),
        ("#ffe054", (255, 224, 84, 255)),
        ("#ff9e24", (255, 158, 36, 255)),
        ("#f25c22", (242, 92, 34, 255)),
        ("#bf2626", (191, 38, 38, 255)),
        ("#781722", (120, 23, 34, 255)),
        ("#3d0c18", (61, 12, 24, 255)),
    ]
    sx = pad_x
    for label, col in swatches:
        draw.rectangle([sx, 50, sx + 14, 64], fill=col, outline=(220, 224, 235, 255))
        draw.text((sx + 18, 52), label, fill=(210, 216, 228, 255), font=font)
        sx += 82

    cur_y = header_h
    for title, frames, fw, fh in sections:
        draw.text((pad_x, cur_y), title, fill=(255, 216, 128, 255), font=font)
        cur_y += 20
        box_w = fw * scale
        box_h = fh * scale

        for bg_label, bg_fn in [("SLATE", draw_arena_slate), ("CHECK", draw_checkerboard)]:
            draw.text((pad_x, cur_y + box_h // 2 - 6), bg_label, fill=(150, 160, 180, 255), font=font)
            bx = pad_x + 56
            for idx, fr in enumerate(frames):
                bg_fn(draw, bx, cur_y, box_w, box_h)
                draw.rectangle([bx, cur_y, bx + box_w - 1, cur_y + box_h - 1], outline=(70, 78, 96, 255))
                up = fr.resize((box_w, box_h), Image.Resampling.NEAREST)
                canvas.alpha_composite(up, (bx, cur_y))
                if bg_label == "SLATE":
                    draw.text((bx + 4, cur_y + box_h + 3), f"f{idx}", fill=(180, 190, 210, 255), font=font)
                bx += box_w + gap
            cur_y += box_h + 18
        cur_y += row_gap - 6

    return canvas


def build_showcase_gif(
    cast_frames: list[Image.Image],
    flight_frames: list[Image.Image],
    impact_frames: list[Image.Image],
) -> None:
    """Create a 6x nearest-neighbor showcase GIF: Cast -> Flight -> Impact Detonation."""
    stage_w, stage_h = 128, 44
    scale = 6
    gif_frames: list[Image.Image] = []
    durations: list[int] = []

    def make_bg() -> Image.Image:
        bg = Image.new("RGBA", (stage_w, stage_h), (24, 28, 37, 255))
        d = ImageDraw.Draw(bg)
        # Subtle ground grid & horizon tile lines
        for x in range(0, stage_w, 16):
            d.line([x, 0, x, stage_h - 1], fill=(30, 35, 46, 255))
        for y in range(0, stage_h, 16):
            d.line([0, y, stage_w - 1, y], fill=(30, 35, 46, 255))
        # Wizard arcane staff crystal tip on left (x=12, y=22)
        d.line([8, 28, 12, 22], fill=(110, 68, 44, 255))
        d.rectangle([11, 20, 13, 22], fill=(51, 181, 229, 255))
        d.point((12, 21), fill=(255, 255, 255, 255))
        # Target fiend core rune on right (x=104, y=22)
        d.ellipse([100, 18, 108, 26], outline=(128, 27, 46, 255))
        d.ellipse([102, 20, 106, 24], fill=(71, 51, 84, 255))
        return bg

    def push_frame(comp: Image.Image, ms: int) -> None:
        up = comp.resize((stage_w * scale, stage_h * scale), Image.Resampling.NEAREST)
        gif_frames.append(up.convert("P", palette=Image.Palette.ADAPTIVE, colors=255))
        durations.append(ms)

    # Brief anticipation hold
    push_frame(make_bg(), 180)

    # Phase 1: Muzzle Cast Burst + fireball launch
    for ci in range(4):
        frame = make_bg()
        frame.alpha_composite(cast_frames[ci], (10, 10))
        if ci >= 1:
            fx = 10 + (ci - 1) * 7
            frame.alpha_composite(flight_frames[ci % len(flight_frames)], (fx, 10))
        push_frame(frame, 60)

    # Phase 2: Fireball Flight across the arena (x=28 -> x=88)
    flight_steps = 10
    for step in range(flight_steps):
        frame = make_bg()
        fx = 28 + int(round((88 - 28) * (step / (flight_steps - 1))))
        ff = flight_frames[(step + 2) % len(flight_frames)]
        frame.alpha_composite(ff, (fx, 10))
        push_frame(frame, 55)

    # Phase 3: Impact Detonation centered at target (x=104, y=22 -> top-left 88, 6)
    impact_durations = [55, 65, 75, 85, 95, 110]
    for ii in range(6):
        frame = make_bg()
        frame.alpha_composite(impact_frames[ii], (88, 6))
        push_frame(frame, impact_durations[ii])

    # Clean settle pause
    push_frame(make_bg(), 320)

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    gif_frames[0].save(
        SHOWCASE_GIF_OUT,
        save_all=True,
        append_images=gif_frames[1:],
        duration=durations,
        loop=0,
        disposal=2,
    )


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="pixelbolt_fire_") as tmp:
        work_dir = Path(tmp)
        flight_raw = run_pixelbolt(VFX_SRC_DIR / "fire_flight.fx.json", work_dir, "fire_flight", "flight", 6)
        impact_raw = run_pixelbolt(VFX_SRC_DIR / "fire_impact.fx.json", work_dir, "fire_impact", "impact", 6)
        cast_raw = run_pixelbolt(VFX_SRC_DIR / "fire_cast.fx.json", work_dir, "fire_cast", "cast", 4)

        flight_frames = refine_frames(flight_raw, 24, 24, aura_radius=2.6, aura_alpha=88)
        impact_frames = refine_frames(impact_raw, 32, 32, aura_radius=2.4, aura_alpha=82)
        cast_frames = refine_frames(cast_raw, 24, 24, aura_radius=2.4, aura_alpha=84)

        canon_projectile = polish_canonical_projectile(flight_frames[1])

        PROJECTILE_OUT.parent.mkdir(parents=True, exist_ok=True)
        SHEET_OUT.parent.mkdir(parents=True, exist_ok=True)
        PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

        canon_projectile.save(PROJECTILE_OUT)
        assemble_strip(flight_frames).save(SHEET_OUT)
        assemble_strip(impact_frames).save(IMPACT_OUT)
        assemble_strip(cast_frames).save(CAST_OUT)

        contact = build_contact_sheet(canon_projectile, cast_frames, flight_frames, impact_frames)
        contact.save(CONTACT_SHEET_OUT)
        build_showcase_gif(cast_frames, flight_frames, impact_frames)

    # Verify exact dimensions
    checks = [
        (PROJECTILE_OUT, (24, 24)),
        (SHEET_OUT, (144, 24)),
        (IMPACT_OUT, (192, 32)),
        (CAST_OUT, (96, 24)),
    ]
    for path, exp in checks:
        with Image.open(path) as im:
            assert im.size == exp, f"{path}: expected {exp}, got {im.size}"
            print(f"OK {path.relative_to(REPO_ROOT)}: {im.size[0]}x{im.size[1]} mode={im.mode}")
    print(f"OK {CONTACT_SHEET_OUT.relative_to(REPO_ROOT)}: {contact.size[0]}x{contact.size[1]}")
    print(f"OK {SHOWCASE_GIF_OUT.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
