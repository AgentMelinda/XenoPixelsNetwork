package net.bullettrain.xenopixelsmod.dmz.race;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 64×64 layered-body placeholder. Front islands match the Minecraft skin layout DMZ overlays use.
 */
public final class RaceBodyPng {
    public static final int SIZE = 64;

    private RaceBodyPng() {
    }

    public static void writePlaceholder(Path png, int argb) throws IOException {
        Files.createDirectories(png.getParent());
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, SIZE, SIZE);
            Color fill = new Color(argb, true);
            g.setColor(fill);
            // Head front 8×8 at (8,8)
            g.fillRect(8, 8, 8, 8);
            // Torso front 8×12 at (20,20)
            g.fillRect(20, 20, 8, 12);
            // Right arm 4×12 at (44,20), left arm 4×12 at (36,52)
            g.fillRect(44, 20, 4, 12);
            g.fillRect(36, 52, 4, 12);
            // Right leg 4×12 at (4,20), left leg 4×12 at (20,52)
            g.fillRect(4, 20, 4, 12);
            g.fillRect(20, 52, 4, 12);
            Color shade = fill.darker();
            g.setColor(new Color(shade.getRed(), shade.getGreen(), shade.getBlue(), fill.getAlpha()));
            g.fillRect(8, 8, 8, 2);
            g.fillRect(20, 20, 8, 2);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "PNG", png.toFile());
    }

    public static int readSize(Path png) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        if (image == null) {
            return 0;
        }
        return image.getWidth();
    }

    public static int parseHex(String hex, int fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        String raw = hex.trim();
        if (raw.startsWith("#")) {
            raw = raw.substring(1);
        }
        try {
            if (raw.length() == 6) {
                return 0xFF000000 | Integer.parseInt(raw, 16);
            }
            if (raw.length() == 8) {
                return (int) Long.parseLong(raw, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }
}
