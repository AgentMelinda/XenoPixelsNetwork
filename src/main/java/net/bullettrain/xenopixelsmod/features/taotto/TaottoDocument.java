package net.bullettrain.xenopixelsmod.features.taotto;

import net.minecraft.nbt.CompoundTag;

/**
 * Per-pixel tattoo canvas plus UV placement on one body-part island.
 *
 * <p>Bakes onto a transparent 64×64 overlay. DMZ {@code tattooType} is unchanged; this overlay is
 * additive.
 */
public final class TaottoDocument {
    public static final int DEFAULT_SIZE = 32;
    public static final float MIN_SCALE = 0.125f;
    public static final float MAX_SCALE = 4f;
    public static final int MAX_SIZE = 64;

    private final int size;
    private final int[] pixels;
    private TaottoBodyPart part = TaottoBodyPart.TORSO;
    private float offsetU;
    private float offsetV;
    private float scale = 1f;

    private TaottoDocument(int size) {
        this.size = Math.max(8, Math.min(MAX_SIZE, size));
        this.pixels = new int[this.size * this.size];
    }

    public static TaottoDocument blank() {
        TaottoDocument document = new TaottoDocument(DEFAULT_SIZE);
        document.fitPaint();
        return document;
    }

    public static TaottoDocument ofSize(int size) {
        return new TaottoDocument(size);
    }

    public int size() {
        return size;
    }

    public TaottoBodyPart part() {
        return part;
    }

    public void part(TaottoBodyPart part) {
        this.part = part == null ? TaottoBodyPart.TORSO : part;
        clampOffset();
    }

    public float offsetU() {
        return offsetU;
    }

    public float offsetV() {
        return offsetV;
    }

    public void offsetU(float offsetU) {
        this.offsetU = offsetU;
        clampOffset();
    }

    public void offsetV(float offsetV) {
        this.offsetV = offsetV;
        clampOffset();
    }

    public float scale() {
        return scale;
    }

    public void scale(float scale) {
        PaintBounds bounds = paintBounds();
        float centerX = (bounds.minX() + bounds.maxX()) / 2f;
        float centerY = (bounds.minY() + bounds.maxY()) / 2f;
        float previous = this.scale;
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Float.isFinite(scale) ? scale : 1f));
        if (hasPaint()) {
            offsetU += centerX * (previous - this.scale);
            offsetV += centerY * (previous - this.scale);
        } else {
            offsetU = offsetV = 0f;
        }
        clampOffset();
    }

    /** Fit the painted area, ignoring empty canvas margins, and center it on the part. */
    public void fitPaint() {
        PaintBounds bounds = paintBounds();
        TaottoBodyPart.UvIsland island = part.front();
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                Math.min(island.w() / (float) bounds.width(), island.h() / (float) bounds.height())));
        offsetU = (island.w() - bounds.width() * scale) / 2f - bounds.minX() * scale;
        offsetV = (island.h() - bounds.height() * scale) / 2f - bounds.minY() * scale;
        clampOffset();
    }

    public PaintBounds paintBounds() {
        int minX = size, minY = size, maxX = 0, maxY = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((pixels[y * size + x] >>> 24) == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + 1);
                maxY = Math.max(maxY, y + 1);
            }
        }
        return minX == size ? new PaintBounds(0, 0, size, size)
                : new PaintBounds(minX, minY, maxX, maxY);
    }

    public record PaintBounds(int minX, int minY, int maxX, int maxY) {
        public int width() { return maxX - minX; }
        public int height() { return maxY - minY; }
    }

    public void dragBy(float du, float dv) {
        offsetU += du;
        offsetV += dv;
        clampOffset();
    }

    public int pixel(int x, int y) {
        if (!inBounds(x, y)) {
            return 0;
        }
        return pixels[y * size + x];
    }

    public void setPixel(int x, int y, int argb) {
        if (!inBounds(x, y)) {
            return;
        }
        pixels[y * size + x] = argb;
    }

    public void clearPixel(int x, int y) {
        setPixel(x, y, 0);
    }

    public void clear() {
        java.util.Arrays.fill(pixels, 0);
    }

    public boolean hasPaint() {
        return paintedCount() > 0;
    }

    public int paintedCount() {
        int n = 0;
        for (int px : pixels) {
            if (((px >>> 24) & 0xFF) > 0) {
                n++;
            }
        }
        return n;
    }

    public int[] bakeOverlay(int skinSize) {
        int dim = Math.max(16, Math.min(256, skinSize));
        int[] overlay = new int[dim * dim];
        TaottoBodyPart.UvIsland island = part.front();
        float s = scale;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = pixels[y * size + x];
                if (((argb >>> 24) & 0xFF) == 0) {
                    continue;
                }
                int destW = Math.max(1, Math.round(s));
                int destH = Math.max(1, Math.round(s));
                int baseX = island.u() + (int) Math.floor(offsetU + x * s);
                int baseY = island.v() + (int) Math.floor(offsetV + y * s);
                for (int oy = 0; oy < destH; oy++) {
                    for (int ox = 0; ox < destW; ox++) {
                        int dx = baseX + ox;
                        int dy = baseY + oy;
                        if (dx < island.u() || dy < island.v()
                                || dx >= island.u() + island.w() || dy >= island.v() + island.h()
                                || dx >= dim || dy >= dim) {
                            continue;
                        }
                        overlay[dy * dim + dx] = argb;
                    }
                }
            }
        }
        return overlay;
    }

    public void saveNbt(CompoundTag tag) {
        tag.putInt("Size", size);
        tag.putIntArray("Pixels", pixels);
        tag.putString("Part", part.name());
        tag.putFloat("U", offsetU);
        tag.putFloat("V", offsetV);
        tag.putFloat("Scale", scale);
    }

    public static TaottoDocument loadNbt(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return blank();
        }
        int size = tag.contains("Size") ? tag.getInt("Size") : DEFAULT_SIZE;
        TaottoDocument doc = new TaottoDocument(size);
        int[] stored = tag.getIntArray("Pixels");
        int n = Math.min(stored.length, doc.pixels.length);
        System.arraycopy(stored, 0, doc.pixels, 0, n);
        doc.part(TaottoBodyPart.fromName(tag.getString("Part")));
        doc.offsetU = tag.getFloat("U");
        doc.offsetV = tag.getFloat("V");
        float storedScale = tag.contains("Scale") ? tag.getFloat("Scale") : 1f;
        doc.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Float.isFinite(storedScale) ? storedScale : 1f));
        doc.clampOffset();
        return doc;
    }

    public TaottoDocument copy() {
        TaottoDocument copy = new TaottoDocument(size);
        System.arraycopy(pixels, 0, copy.pixels, 0, pixels.length);
        copy.part = part;
        copy.offsetU = offsetU;
        copy.offsetV = offsetV;
        copy.scale = scale;
        return copy;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size;
    }

    private void clampOffset() {
        TaottoBodyPart.UvIsland island = part.front();
        PaintBounds bounds = paintBounds();
        // If paint fits, keep it inside the part. If oversized, allow panning across it.
        float left = -bounds.minX() * scale;
        float right = island.w() - bounds.maxX() * scale;
        float top = -bounds.minY() * scale;
        float bottom = island.h() - bounds.maxY() * scale;
        if (!Float.isFinite(offsetU)) offsetU = 0f;
        if (!Float.isFinite(offsetV)) offsetV = 0f;
        offsetU = Math.max(Math.min(left, right), Math.min(Math.max(left, right), offsetU));
        offsetV = Math.max(Math.min(top, bottom), Math.min(Math.max(top, bottom), offsetV));
    }
}
