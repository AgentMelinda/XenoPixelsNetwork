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
        return new TaottoDocument(DEFAULT_SIZE);
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
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Float.isFinite(scale) ? scale : 1f));
        clampOffset();
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
                int baseX = island.u() + Math.round(offsetU + x * s);
                int baseY = island.v() + Math.round(offsetV + y * s);
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
        doc.scale(tag.contains("Scale") ? tag.getFloat("Scale") : 1f);
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
        float paintedW = size * scale;
        float paintedH = size * scale;
        float maxU = Math.max(0f, island.w() - Math.min(paintedW, island.w()));
        float maxV = Math.max(0f, island.h() - Math.min(paintedH, island.h()));
        if (!Float.isFinite(offsetU)) offsetU = 0f;
        if (!Float.isFinite(offsetV)) offsetV = 0f;
        if (offsetU < 0f) {
            offsetU = 0f;
        }
        if (offsetV < 0f) {
            offsetV = 0f;
        }
        if (offsetU > maxU) {
            offsetU = maxU;
        }
        if (offsetV > maxV) {
            offsetV = maxV;
        }
    }
}
