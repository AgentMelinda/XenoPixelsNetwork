package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Hakai "sweep" for entities (owner 2026-10-16: the planet's top-to-bottom erasure "for players and Sable ships and
 * blocks"). Every quad the model emits is clipped against a horizontal plane at world Y {@code cutY}: what is above is
 * gone, polygons crossing it are cut exactly (interpolated position, colour, UV, overlay, light and normal), so the body
 * disappears with a clean edge instead of fading. Non-quad or normal-less buffers (lines, debug) pass through untouched.
 *
 * <p>Model vertices arrive in camera-rotated space; {@link HakaiFade#worldYFromCameraSpace} turns them into world Y, the
 * same conversion the classic height fade uses, and that map is linear so interpolating along an edge stays exact.
 *
 * <p>Clipping buffers a whole quad before re-emitting it, so a flush can land after the builder behind it was closed - the
 * proven case is {@code OutlineBufferSource}'s glow/target fan-out, handled in {@link #getBuffer} - and the deferred write
 * then throws {@code IllegalStateException: Not building!}. An exception escaping here unwinds
 * {@code EntityRenderDispatcher#render} mid-frame and the level renderer dies on {@code Pose stack not empty}, so the flush
 * is also fail-open: the first rejected write closes this clipper and every later quad for that buffer is dropped. That
 * builder is finished either way, so the vertex would not be drawn regardless - a missing sweep for one frame, never a crash.
 */
final class HakaiClipMultiBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float cutY, cameraY, qx, qy, qz, qw;
    private final float cameraX, cameraZ;
    /** When set, every vertex's world position is recorded here before clipping (HakaiSweepFx model fit). */
    private final HakaiSweepFx.Extent extent;

    HakaiClipMultiBufferSource(MultiBufferSource delegate, float cutY, float cameraY, float qx, float qy, float qz, float qw) {
        this.delegate = delegate;
        this.cutY = cutY; this.cameraY = cameraY;
        this.qx = qx; this.qy = qy; this.qz = qz; this.qw = qw;
        this.cameraX = 0f; this.cameraZ = 0f; this.extent = null;
    }

    /** Entity sweep: unrotated camera-relative vertices, measuring the model while cutting it. */
    HakaiClipMultiBufferSource(MultiBufferSource delegate, float cutY, float cameraX, float cameraY, float cameraZ,
                               HakaiSweepFx.Extent extent) {
        this.delegate = delegate;
        this.cutY = cutY; this.cameraY = cameraY;
        this.qx = 0f; this.qy = 0f; this.qz = 0f; this.qw = 1f;
        this.cameraX = cameraX; this.cameraZ = cameraZ; this.extent = extent;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        // A glowing or selected entity is drawn through OutlineBufferSource, which fans the body out together with an
        // EntityOutlineGenerator. That generator's builder is closed by endOutlineBatch while a clipped quad is still
        // buffered here, so the deferred write lands on a finished builder - the logged "Not building!" crash. Clip the
        // inner body consumer instead and leave the outline pass to vanilla, exactly as the classic fade does in
        // AlphaMultiBufferSource#fadeOutlineBodyOnly, so the sweep still cuts glowing bodies.
        MultiBufferSource body = delegate instanceof OutlineBufferSource outline && !renderType.isOutline()
                ? AlphaMultiBufferSource.innerBufferSource(outline)
                : null;
        VertexConsumer inner = body != null ? body.getBuffer(renderType) : delegate.getBuffer(renderType);
        // Last resort for wrapper chains we do not know (Iris, Sable, mods): a fan-out consumer's second member can be
        // closed at any time, and those nested classes implement VertexConsumer without extending VertexMultiConsumer,
        // so no instanceof test can name them. Draw such a buffer unclipped rather than risk the frame.
        if (inner.getClass().getName().startsWith("com.mojang.blaze3d.vertex.VertexMultiConsumer")) return inner;
        // Quads whose format ends in a normal (every entity format): the clipper flushes a quad on its 4th normal.
        boolean clippable = renderType.mode() == VertexFormat.Mode.QUADS
                && renderType.format().contains(com.mojang.blaze3d.vertex.VertexFormatElement.NORMAL);
        if (extent != null && net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.hakaiSweepDebug)
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP type {} clippable={} consumer={}", renderType, clippable,
                    inner.getClass().getSimpleName());
        return clippable ? new Clip(inner, renderType.toString()) : inner;
    }

    float worldY(float x, float y, float z) {
        return HakaiFade.worldYFromCameraSpace(x, y, z, cameraY, qx, qy, qz, qw);
    }

    private final class Clip implements VertexConsumer {
        private final VertexConsumer out;
        // Four buffered vertices: position, colour (r,g,b,a), uv, overlay (u,v), light (u,v), normal; plus which were set.
        private final float[] x = new float[4], y = new float[4], z = new float[4], u = new float[4], v = new float[4];
        private final float[] nx = new float[4], ny = new float[4], nz = new float[4];
        private final int[] r = new int[4], g = new int[4], b = new int[4], a = new int[4];
        private final int[] ou = new int[4], ov = new int[4], lu = new int[4], lv = new int[4];
        private boolean hasColor, hasUv, hasOverlay, hasLight, hasNormal;
        /** The delegate's builder was already ended by a shader-pack batch flush; stop writing, never propagate. */
        private boolean dead;
        private int count = -1;

        private final String typeName;
        private float typeMinY = Float.POSITIVE_INFINITY, typeMaxY = Float.NEGATIVE_INFINITY;
        private final boolean shadow;
        Clip(VertexConsumer out, String typeName) { this.out = out; this.typeName = typeName; this.shadow = typeName.contains("entity_shadow"); }

        @Override public VertexConsumer addVertex(float px, float py, float pz) {
            if (count == 3) flushQuad();
            count++;
            x[count] = px; y[count] = py; z[count] = pz;
            r[count] = g[count] = b[count] = a[count] = 255;
            return this;
        }

        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (count >= 0) { r[count] = red; g[count] = green; b[count] = blue; a[count] = alpha; hasColor = true; }
            return this;
        }

        @Override public VertexConsumer setUv(float pu, float pv) {
            if (count >= 0) { u[count] = pu; v[count] = pv; hasUv = true; }
            return this;
        }

        @Override public VertexConsumer setUv1(int pu, int pv) {
            if (count >= 0) { ou[count] = pu; ov[count] = pv; hasOverlay = true; }
            return this;
        }

        @Override public VertexConsumer setUv2(int pu, int pv) {
            if (count >= 0) { lu[count] = pu; lv[count] = pv; hasLight = true; }
            return this;
        }

        @Override public VertexConsumer setNormal(float px, float py, float pz) {
            if (count >= 0) { nx[count] = px; ny[count] = py; nz[count] = pz; hasNormal = true; }
            // The normal is the last element of every entity format; a full quad is complete after its 4th normal.
            if (count == 3) flushQuad();
            return this;
        }

        /** Sutherland-Hodgman against "worldY <= cutY", then re-emitted as one or two quads. */
        private void flushQuad() {
            if (count < 3) { count = -1; return; }
            count = -1;
            float[] d = new float[4];
            int above = 0;
            for (int i = 0; i < 4; i++) {
                float wy = worldY(x[i], y[i], z[i]);
                // The model only: the shadow is a flat quad wider than the mob and must not size the sweep.
                if (extent != null && !shadow) extent.add(x[i] + cameraX, wy, z[i] + cameraZ);
                if (wy < typeMinY) typeMinY = wy;
                if (wy > typeMaxY) { typeMaxY = wy;
                    if (extent != null && net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.hakaiSweepDebug)
                        net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP vtop type={} y={} minY={} cut={}", typeName.length() > 90 ? typeName.substring(0, 90) : typeName, wy, typeMinY, cutY);
                }
                d[i] = cutY - wy;
                if (d[i] < 0) above++;
            }
            if (above == 4) return;
            if (above == 0) { for (int i = 0; i < 4; i++) emit(i, i, 0); return; }
            int[] ia = new int[6], ib = new int[6];
            float[] t = new float[6];
            int n = 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                if (d[i] >= 0) { ia[n] = i; ib[n] = i; t[n] = 0; n++; }
                if ((d[i] >= 0) != (d[j] >= 0)) { ia[n] = i; ib[n] = j; t[n] = d[i] / (d[i] - d[j]); n++; }
            }
            if (extent != null && !shadow) {
                // The two points where this face crosses the cut plane: one edge of the cut part's cross-section (cap).
                float sx0 = 0, sy0 = 0, sz0 = 0;
                int found = 0;
                for (int k = 0; k < n; k++) {
                    if (ia[k] == ib[k]) continue;
                    float lx = x[ia[k]] + (x[ib[k]] - x[ia[k]]) * t[k];
                    float ly = y[ia[k]] + (y[ib[k]] - y[ia[k]]) * t[k];
                    float lz = z[ia[k]] + (z[ib[k]] - z[ia[k]]) * t[k];
                    float px = lx + cameraX, py = worldY(lx, ly, lz), pz = lz + cameraZ;
                    if (found == 0) { sx0 = px; sy0 = py; sz0 = pz; found = 1; }
                    else { extent.segment(sx0, sy0, sz0, px, py, pz); break; }
                }
            }
            if (n < 3) return;
            int[] order = n == 3 ? new int[]{0, 1, 2, 2} : n == 4 ? new int[]{0, 1, 2, 3} : new int[]{0, 1, 2, 3, 0, 3, 4, 4};
            for (int k : order) emit(ia[k], ib[k], t[k]);
        }

        private void emit(int i, int j, float t) {
            if (dead) return;
            try {
                out.addVertex(lerp(x[i], x[j], t), lerp(y[i], y[j], t), lerp(z[i], z[j], t));
                if (hasColor) out.setColor(lerpI(r[i], r[j], t), lerpI(g[i], g[j], t), lerpI(b[i], b[j], t), lerpI(a[i], a[j], t));
                if (hasUv) out.setUv(lerp(u[i], u[j], t), lerp(v[i], v[j], t));
                if (hasOverlay) out.setUv1(t < 0.5f ? ou[i] : ou[j], t < 0.5f ? ov[i] : ov[j]);
                if (hasLight) out.setUv2(t < 0.5f ? lu[i] : lu[j], t < 0.5f ? lv[i] : lv[j]);
                if (hasNormal) out.setNormal(lerp(nx[i], nx[j], t), lerp(ny[i], ny[j], t), lerp(nz[i], nz[j], t));
            } catch (IllegalStateException ended) {
                // The builder behind this consumer is closed; a swept-then-dropped quad beats a dead client.
                dead = true;
                if (net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.hakaiSweepDebug)
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP clipper closed type={} msg={}",
                            typeName.length() > 90 ? typeName.substring(0, 90) : typeName, ended.getMessage());
            }
        }

        private float lerp(float p, float q, float s) { return p + (q - p) * s; }
        private int lerpI(int p, int q, float s) { return Math.round(p + (q - p) * s); }
    }
}
