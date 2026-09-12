package net.bullettrain.xenopixelsmod.client.screen.neon;

import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig;

/**
 * Where a piece of the neon character screen actually lands once the elements editor has moved,
 * resized or hidden it.
 *
 * <p>One copy of this arithmetic, used by the live screen, by the editor's preview and by the
 * hit-tests. It was three copies and they disagreed: the preview scaled its sprites but reported
 * unscaled bounds, the {@code +} buttons took their row block's offset but not its scale, and the
 * labels inside a scaled block did not move with it. A piece you can see and a piece you can click
 * have to be the same rectangle, so both come from here.
 *
 * <p>No Minecraft types, so the maths is testable without a client.
 */
public final class NeonPartTransform {

    private NeonPartTransform() {
    }

    /** A laid-out rectangle in screen space. */
    public record Rect(float x, float y, float width, float height) {

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }

        public int left() {
            return (int) Math.floor(x);
        }

        public int top() {
            return (int) Math.floor(y);
        }

        public int right() {
            return (int) Math.ceil(x + width);
        }

        public int bottom() {
            return (int) Math.ceil(y + height);
        }

        /** The smallest rectangle covering both, for the editor's selection outline. */
        public Rect union(Rect other) {
            if (other == null) {
                return this;
            }
            float minX = Math.min(x, other.x);
            float minY = Math.min(y, other.y);
            float maxX = Math.max(x + width, other.x + other.width);
            float maxY = Math.max(y + height, other.y + other.height);
            return new Rect(minX, minY, maxX - minX, maxY - minY);
        }
    }

    /**
     * A group that moves and resizes as one: the stat rows, the statistics column, the summary box.
     *
     * <p>Given in the group's own unscaled screen coordinates, before its offset is applied. The
     * group's scale pivots on its centre, so resizing a group of seven rows grows it about the
     * middle row instead of walking it down the panel — and every label, value and button inside it
     * travels the same way, which is the part that was missing.
     */
    public record Block(int part, float x, float y, float width, float height) {

        float scale() {
            return XenoDmzNeonConfig.partScale(part);
        }

        float centreX() {
            return x + width / 2.0f;
        }

        float centreY() {
            return y + height / 2.0f;
        }

        /** A point in the group's unscaled space, in screen space. */
        public float mapX(float pointX) {
            return centreX() + (pointX - centreX()) * scale() + XenoDmzNeonConfig.partX(part);
        }

        public float mapY(float pointY) {
            return centreY() + (pointY - centreY()) * scale() + XenoDmzNeonConfig.partY(part);
        }

        /** A rectangle in the group's unscaled space, in screen space. */
        public Rect map(float rectX, float rectY, float rectWidth, float rectHeight) {
            return new Rect(mapX(rectX), mapY(rectY),
                    rectWidth * scale(), rectHeight * scale());
        }

        public boolean hidden() {
            return XenoDmzNeonConfig.partHidden[part];
        }
    }

    /**
     * A standalone sprite's rectangle, scaled about its own centre.
     *
     * <p>About the centre rather than the top-left because a part being resized in the editor should
     * stay where it was put; scaling from the corner walks it across the screen as it grows, which
     * reads as one slider moving two things.
     */
    public static Rect rect(int spriteWidth, int spriteHeight, int x, int y, int part) {
        float scale = XenoDmzNeonConfig.partScale(part);
        float width = spriteWidth * scale;
        float height = spriteHeight * scale;
        return new Rect(
                x + XenoDmzNeonConfig.partX(part) + (spriteWidth - width) / 2.0f,
                y + XenoDmzNeonConfig.partY(part) + (spriteHeight - height) / 2.0f,
                width, height);
    }

    /**
     * A sprite that sits inside a group and carries its own scale as well.
     *
     * <p>The two multiply, because they mean different things: the group's scale is how big the
     * group is, the part's is how big this piece is within it. A {@code +} button at part scale 1
     * inside a row block at scale 2 comes out twice as large and twice as far down the panel, which
     * is what resizing the group has to mean if the button is to stay on its row.
     */
    public static Rect rect(int spriteWidth, int spriteHeight, int x, int y, int part,
                            Block block) {
        Rect local = rect(spriteWidth, spriteHeight, x, y, part);
        return block.map(local.x(), local.y(), local.width(), local.height());
    }

    /** The factor text inside a group is drawn at: its own part's, times the group's. */
    public static float textScale(int part, Block block) {
        return XenoDmzNeonConfig.partScale(part) * block.scale();
    }

    public static boolean hidden(int part) {
        return XenoDmzNeonConfig.partHidden[part];
    }
}
