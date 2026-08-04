import os
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

# Configuration
OUTPUT_DIR = "generated_textures"
RESOLUTION = 64  # High res (64x64)
os.makedirs(OUTPUT_DIR, exist_ok=True)

def create_gradient(draw, color_start, color_end, vertical=True):
    width, height = draw.im.size
    for i in range(height if vertical else width):
        ratio = i / (height if vertical else width)
        r = int(color_start[0] * (1 - ratio) + color_end[0] * ratio)
        g = int(color_start[1] * (1 - ratio) + color_end[1] * ratio)
        b = int(color_start[2] * (1 - ratio) + color_end[2] * ratio)
        if vertical:
            draw.line([(0, i), (width, i)], fill=(r, g, b))
        else:
            draw.line([(i, 0), (i, height)], fill=(r, g, b))

def generate_thruster_texture():
    size = (RESOLUTION, RESOLUTION)
    img = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. Outer Casing (Dark Gunmetal with subtle gradient)
    casing_start = (40, 45, 50)
    casing_end = (20, 25, 30)
    create_gradient(draw, casing_start, casing_end, vertical=True)
    
    # Add mechanical details (bolts/corners)
    draw.rectangle([4, 4, 10, 10], fill=(60, 65, 70))
    draw.rectangle([54, 4, 60, 10], fill=(60, 65, 70))
    draw.rectangle([4, 54, 10, 60], fill=(60, 65, 70))
    draw.rectangle([54, 54, 60, 60], fill=(60, 65, 70))

    # 2. Inner Ring (Gold/Brass accent)
    ring_center = RESOLUTION // 2
    ring_radius = 24
    draw.ellipse([ring_center - ring_radius, ring_center - ring_radius, 
                  ring_center + ring_radius, ring_center + ring_radius], 
                 outline=(180, 140, 60), width=3)

    # 3. Plasma Core (Glowing Blue/Cyan Gradient)
    core_radius = 18
    # Create a radial-like effect using concentric circles
    for r in range(core_radius, 0, -1):
        ratio = r / core_radius
        # Bright center to darker edge
        r_col = int(100 + (50 * (1-ratio)))
        g_col = int(200 + (55 * (1-ratio)))
        b_col = int(255)
        alpha = int(255 * (ratio ** 1.5))
        draw.ellipse([ring_center - r, ring_center - r, 
                      ring_center + r, ring_center + r], 
                     fill=(r_col, g_col, b_col, alpha))

    # 4. Emissive Highlight (White hot center)
    draw.ellipse([ring_center - 6, ring_center - 6, 
                  ring_center + 6, ring_center + 6], 
                 fill=(240, 250, 255, 200))

    # Save
    img.save(f"{OUTPUT_DIR}/thruster_jet.png")
    print("Generated: thruster_jet.png")

def generate_vs2_chip_texture():
    size = (RESOLUTION, RESOLUTION)
    img = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Background: Dark obsidian/glass base
    draw.rectangle([2, 2, 62, 62], fill=(10, 12, 15))
    
    # Circuitry Lines (Neon Purple/Blue)
    circuit_color = (138, 43, 226) # BlueViolet
    glow_color = (100, 149, 237) # CornflowerBlue
    
    # Draw random tech lines
    for i in range(0, 64, 8):
        if i % 16 == 0:
            draw.line([(4, i), (60, i)], fill=circuit_color, width=1)
        else:
            draw.line([(i, 4), (i, 60)], fill=glow_color, width=1)

    # Central VS2 Logo Area (Holographic Square)
    center_sq = [20, 20, 44, 44]
    # Gradient fill for hologram
    for y in range(20, 44):
        ratio = (y - 20) / 24
        r = int(100 + 100 * ratio)
        g = int(50 + 100 * ratio)
        b = int(200 + 55 * ratio)
        draw.line([(20, y), (44, y)], fill=(r, g, b, 180))

    # Gold Contacts at bottom
    for x in range(24, 40, 4):
        draw.rectangle([x, 56, x+2, 60], fill=(218, 165, 32)) # Goldenrod

    # Border Glow
    draw.rectangle([2, 2, 62, 62], outline=(100, 200, 255), width=1)

    img.save(f"{OUTPUT_DIR}/vs2_control_chip.png")
    print("Generated: vs2_control_chip.png")

def generate_vs2_armor_plating_texture():
    size = (RESOLUTION, RESOLUTION)
    img = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Base: Sleek White/Light Grey Composite
    draw.rectangle([0, 0, 64, 64], fill=(220, 225, 230))

    # Diagonal Racing Stripes (Dynamic look)
    stripe_color = (40, 50, 65) # Dark Blue-Grey
    accent_color = (255, 70, 70) # Red Accent
    
    # Main Stripe
    draw.polygon([(10, 0), (30, 0), (64, 34), (64, 54)], fill=stripe_color)
    # Accent Line next to it
    draw.polygon([(32, 0), (36, 0), (64, 28), (64, 32)], fill=accent_color)

    # Mechanical Vents (Bottom right)
    for i in range(4):
        y_pos = 40 + (i * 5)
        draw.rectangle([45, y_pos, 60, y_pos+2], fill=(30, 30, 30))

    # Bolts
    draw.ellipse([(5, 5), (9, 9)], fill=(150, 150, 150))
    draw.ellipse([(55, 55), (59, 59)], fill=(150, 150, 150))

    img.save(f"{OUTPUT_DIR}/vs2_armor_plating.png")
    print("Generated: vs2_armor_plating.png")

if __name__ == "__main__":
    try:
        generate_thruster_texture()
        generate_vs2_chip_texture()
        generate_vs2_armor_plating_texture()
        print(f"\nSuccess! Textures saved to '{OUTPUT_DIR}' folder.")
        print("Move these files to: src/main/resources/assets/xenopixels/textures/item/")
    except ImportError:
        print("Error: Pillow library not found. Please install it using: pip install Pillow")
