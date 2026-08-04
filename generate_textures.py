import os
import math
from PIL import Image, ImageDraw, ImageFilter, ImageOps

# Configuration
OUTPUT_DIR = "generated_textures"
RESOLUTION = 512

# Create output directory
if not os.path.exists(OUTPUT_DIR):
    os.makedirs(OUTPUT_DIR)

def create_base_noise(width, height, color_base, noise_amount=30):
    """Create a base with subtle noise"""
    img = Image.new('RGB', (width, height), color_base)
    draw = ImageDraw.Draw(img)
    for _ in range(noise_amount * 100):
        x = random.randint(0, width)
        y = random.randint(0, height)
        shade = random.randint(-20, 20)
        r, g, b = color_base
        r = max(0, min(255, r + shade))
        g = max(0, min(255, g + shade))
        b = max(0, min(255, b + shade))
        draw.point((x, y), fill=(r, g, b))
    return img

def draw_panel_lines(draw, width, height, grid_size, color_dark, color_light):
    """Draw industrial panel lines"""
    # Vertical lines
    for x in range(0, width, grid_size):
        offset = random.randint(-2, 2)
        draw.line([(x + offset, 0), (x + offset, height)], fill=color_dark, width=2)
        draw.line([(x + offset + 1, 0), (x + offset + 1, height)], fill=color_light, width=1)
    
    # Horizontal lines
    for y in range(0, height, grid_size):
        offset = random.randint(-2, 2)
        draw.line([(0, y + offset), (width, y + offset)], fill=color_dark, width=2)
        draw.line([(0, y + offset + 1), (width, y + offset + 1)], fill=color_light, width=1)

def draw_rivets(draw, width, height, grid_size, color_rivet, color_highlight):
    """Draw rivets at intersections"""
    for x in range(0, width, grid_size):
        for y in range(0, height, grid_size):
            if random.random() > 0.1: # Skip some for variety
                cx, cy = x + grid_size//2, y + grid_size//2
                # Rivet shadow
                draw.ellipse([(cx-4, cy-4), (cx+4, cy+4)], fill=color_rivet)
                # Rivet highlight
                draw.ellipse([(cx-2, cy-2), (cx+2, cy+2)], fill=color_highlight)

def generate_thruster_texture():
    print("Generating Thruster Texture...")
    img = Image.new('RGB', (RESOLUTION, RESOLUTION), (20, 20, 25))
    draw = ImageDraw.Draw(img)
    
    center = RESOLUTION // 2
    radius_outer = RESOLUTION // 2 - 10
    radius_inner = int(RESOLUTION * 0.35)
    
    # 1. Outer Housing (Dark Gunmetal)
    draw.ellipse([(center-radius_outer, center-radius_outer), 
                  (center+radius_outer, center+radius_outer)], 
                 fill=(60, 65, 70))
    
    # 2. Mechanical Details (Brass/Gold Accents)
    # Bolt circle
    bolt_radius = int(RESOLUTION * 0.42)
    num_bolts = 8
    for i in range(num_bolts):
        angle = (2 * math.pi * i) / num_bolts
        bx = center + int(bolt_radius * math.cos(angle))
        by = center + int(bolt_radius * math.sin(angle))
        draw.ellipse([(bx-6, by-6), (bx+6, by+6)], fill=(180, 140, 60))
        draw.ellipse([(bx-3, by-3), (bx+3, by+3)], fill=(220, 190, 100))

    # 3. Inner Nozzle (Grille)
    draw.ellipse([(center-radius_inner, center-radius_inner), 
                  (center+radius_inner, center+radius_inner)], 
                 fill=(30, 30, 35))
    
    # Radial Grille Lines
    for i in range(12):
        angle = (2 * math.pi * i) / 12
        x1 = center + int(radius_inner * 0.2 * math.cos(angle))
        y1 = center + int(radius_inner * 0.2 * math.sin(angle))
        x2 = center + int(radius_inner * 0.9 * math.cos(angle))
        y2 = center + int(radius_inner * 0.9 * math.sin(angle))
        draw.line([(x1, y1), (x2, y2)], fill=(50, 50, 55), width=3)

    # 4. Glowing Core (Plasma)
    # Gradient glow
    for r in range(radius_inner, int(radius_inner * 0.2), -5):
        intensity = int(255 * (1 - (r / radius_inner)))
        color = (255, min(255, intensity + 50), max(0, intensity - 100))
        draw.ellipse([(center-r, center-r), (center+r, center+r)], outline=color, width=2)
    
    # Bright center
    draw.ellipse([(center-20, center-20), (center+20, center+20)], fill=(255, 255, 200))
    
    # Save
    img.save(f"{OUTPUT_DIR}/thruster_emissive.png")
    # Create a non-emissive version for the base texture (darker core)
    img_base = img.copy()
    draw_base = ImageDraw.Draw(img_base)
    draw_base.ellipse([(center-20, center-20), (center+20, center+20)], fill=(50, 40, 30))
    img_base.save(f"{OUTPUT_DIR}/thruster_base.png")
    print("Thruster textures saved.")

def generate_armor_texture():
    print("Generating VS2 Armor Plating Texture...")
    img = Image.new('RGB', (RESOLUTION, RESOLUTION), (70, 75, 80))
    draw = ImageDraw.Draw(img)
    
    # Colors
    panel_dark = (40, 45, 50)
    panel_light = (90, 95, 100)
    rivet_dark = (50, 50, 55)
    rivet_light = (130, 130, 135)
    hazard_yellow = (200, 180, 50)
    hazard_black = (30, 30, 30)
    
    # 1. Base Noise
    # (Simplified for speed, using solid color + overlay later if needed)
    
    # 2. Panel Grid
    grid_size = 128
    draw_panel_lines(draw, RESOLUTION, RESOLUTION, grid_size, panel_dark, panel_light)
    
    # 3. Rivets
    draw_rivets(draw, RESOLUTION, RESOLUTION, grid_size, rivet_dark, rivet_light)
    
    # 4. Hazard Stripes (Corner detail)
    stripe_w = 40
    for i in range(-5, 10):
        x_start = i * stripe_w
        # Top Left Corner
        draw.polygon([
            (x_start, 0), (x_start + stripe_w, 0), 
            (0, x_start + stripe_w), (0, x_start)
        ], fill=hazard_yellow if i % 2 == 0 else hazard_black)
        
        # Bottom Right Corner
        offset = RESOLUTION
        draw.polygon([
            (offset + x_start, offset), (offset + x_start + stripe_w, offset),
            (offset, offset + x_start + stripe_w), (offset, offset + x_start)
        ], fill=hazard_yellow if i % 2 == 0 else hazard_black)

    # 5. Stenciled ID Number (Fake)
    draw.text((RESOLUTION-100, RESOLUTION-40), "VS2", fill=(150, 150, 150))
    
    img.save(f"{OUTPUT_DIR}/vs2_armor_plating.png")
    print("Armor texture saved.")

def generate_flap_texture():
    print("Generating Control Flap Texture...")
    img = Image.new('RGB', (RESOLUTION, RESOLUTION), (65, 70, 75))
    draw = ImageDraw.Draw(img)
    
    # Colors
    metal_dark = (45, 50, 55)
    metal_light = (85, 90, 95)
    hinge_brass = (160, 120, 50)
    hinge_shadow = (80, 60, 20)
    
    # 1. Main Surface (Aerodynamic shape simulation via texture)
    # Central panel
    margin = 40
    draw.rectangle([(margin, margin), (RESOLUTION-margin, RESOLUTION-margin)], fill=(60, 65, 70))
    
    # 2. Panel Lines on surface
    draw.line([(margin, RESOLUTION//2), (RESOLUTION-margin, RESOLUTION//2)], fill=metal_dark, width=3)
    draw.line([(margin, RESOLUTION//2+1), (RESOLUTION-margin, RESOLUTION//2+1)], fill=metal_light, width=1)
    
    # 3. Hinge Mechanism (Left side)
    hinge_x = margin - 10
    hinge_w = 30
    # Hinge blocks
    for i in range(4):
        y_pos = margin + (i * ((RESOLUTION - 2*margin) // 4)) + 10
        draw.rectangle([(hinge_x, y_pos), (hinge_x + hinge_w, y_pos + 40)], fill=hinge_brass)
        # Pin
        draw.rectangle([(hinge_x + 10, y_pos), (hinge_x + 20, y_pos + 40)], fill=hinge_shadow)
        # Bolts
        draw.ellipse([(hinge_x + 2, y_pos + 5), (hinge_x + 8, y_pos + 11)], fill=metal_light)
        draw.ellipse([(hinge_x + 22, y_pos + 5), (hinge_x + 28, y_pos + 11)], fill=metal_light)

    # 4. Directional Arrow
    arrow_pts = [
        (RESOLUTION - 60, RESOLUTION // 2),
        (RESOLUTION - 40, RESOLUTION // 2 - 20),
        (RESOLUTION - 40, RESOLUTION // 2 - 5),
        (RESOLUTION - 20, RESOLUTION // 2 - 5),
        (RESOLUTION - 20, RESOLUTION // 2 + 5),
        (RESOLUTION - 40, RESOLUTION // 2 + 5),
        (RESOLUTION - 40, RESOLUTION // 2 + 20)
    ]
    draw.polygon(arrow_pts, fill=(200, 50, 50))
    
    img.save(f"{OUTPUT_DIR}/ship_flap_side.png")
    print("Flap texture saved.")

# Need to import random for the functions above
import random

# Run Generation
generate_thruster_texture()
generate_armor_texture()
generate_flap_texture()

print(f"All textures generated successfully in '{OUTPUT_DIR}' folder!")
