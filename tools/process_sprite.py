#!/usr/bin/env python3
import sys
import os
import cv2
import numpy as np
from PIL import Image

def process_sprite(input_path, output_path, target_size=(32, 32), make_two_frames=False):
    # 1. Load image
    img = cv2.imread(input_path)
    if img is None:
        raise ValueError(f"Could not load {input_path}")
    
    h, w = img.shape[:2]
    # Convert BGR to RGB
    img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    
    # 2. Segment white background using floodFill from all 4 corners
    # Create mask with 1-pixel border for floodFill
    mask = np.zeros((h + 2, w + 2), dtype=np.uint8)
    # Binary threshold for near-white background
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    _, white_thresh = cv2.threshold(gray, 248, 255, cv2.THRESH_BINARY)
    
    # FloodFill from edges
    flood_mask = np.zeros((h + 2, w + 2), dtype=np.uint8)
    for pt in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 0), (w // 2, h - 1), (0, h // 2), (w - 1, h // 2)]:
        if gray[pt[1], pt[0]] > 230:
            cv2.floodFill(white_thresh, flood_mask, pt, 0, 10, 10, 8 | (255 << 8) | cv2.FLOODFILL_MASK_ONLY)
    
    # Non-background is where floodFill did NOT reach
    # Anything filled in flood_mask is background
    bg_mask = (flood_mask[1:-1, 1:-1] == 255) | (gray >= 252)
    # Clean up minor edge fringe
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    bg_mask = cv2.dilate(bg_mask.astype(np.uint8), kernel, iterations=1).astype(bool)
    
    alpha = np.where(bg_mask, 0, 255).astype(np.uint8)
    
    # Find bounding box of foreground
    coords = cv2.findNonZero(alpha)
    if coords is None:
        raise ValueError("No foreground detected in image")
    x, y, bw, bh = cv2.boundingRect(coords)
    
    # Add slight 2% margin around bounding box
    pad = int(max(bw, bh) * 0.02)
    x0 = max(0, x - pad)
    y0 = max(0, y - pad)
    x1 = min(w, x + bw + pad)
    y1 = min(h, y + bh + pad)
    
    cropped_rgb = img_rgb[y0:y1, x0:x1]
    cropped_alpha = alpha[y0:y1, x0:x1]
    
    # Combine RGBA
    rgba = np.dstack([cropped_rgb, cropped_alpha])
    pil_img = Image.fromarray(rgba, 'RGBA')
    
    # Fit into square canvas maintaining aspect ratio
    cw, ch = pil_img.size
    max_dim = max(cw, ch)
    square_img = Image.new('RGBA', (max_dim, max_dim), (0, 0, 0, 0))
    offset_x = (max_dim - cw) // 2
    offset_y = (max_dim - ch) // 2
    square_img.paste(pil_img, (offset_x, offset_y), pil_img)
    
    tw, th = target_size
    frame1 = square_img.resize((tw, th), Image.Resampling.LANCZOS)
    
    if make_two_frames:
        # Create a second animation frame: subtle vertical stride/bob (+1 pixel shift) and foot nudge
        frame2 = Image.new('RGBA', (tw, th), (0, 0, 0, 0))
        # Paste with 1-pixel vertical dip
        frame2.paste(frame1, (0, 1), frame1)
        
        # Combine into 2-frame sprite sheet of size (tw * 2, th)
        sheet = Image.new('RGBA', (tw * 2, th), (0, 0, 0, 0))
        sheet.paste(frame1, (0, 0), frame1)
        sheet.paste(frame2, (tw, 0), frame2)
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        sheet.save(output_path, 'PNG')
        print(f"Saved 2-frame sheet to {output_path}: {sheet.size}")
    else:
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        frame1.save(output_path, 'PNG')
        print(f"Saved sprite to {output_path}: {frame1.size}")

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: process_sprite.py <input_jpg> <output_png> [width] [height] [--two-frames]")
        sys.exit(1)
    inp = sys.argv[1]
    out = sys.argv[2]
    w = int(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3].isdigit() else 32
    h = int(sys.argv[4]) if len(sys.argv) > 4 and sys.argv[4].isdigit() else 32
    two_frames = "--two-frames" in sys.argv
    process_sprite(inp, out, (w, h), two_frames)
