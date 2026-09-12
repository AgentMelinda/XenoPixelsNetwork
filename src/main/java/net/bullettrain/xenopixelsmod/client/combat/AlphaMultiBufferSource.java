package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class AlphaMultiBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float alpha;
    private final HeightFade height;
    private static final Map<RenderType, RenderType> TRANSLUCENT_TYPES = new IdentityHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    AlphaMultiBufferSource(MultiBufferSource delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        this.height = null;
    }

    AlphaMultiBufferSource(MultiBufferSource delegate, HeightFade height) {
        this.delegate = delegate;
        this.alpha = Math.max(0.0f, Math.min(1.0f, height.uniformFallback));
        this.height = height;
    }

    boolean wrapsOutline() {
        return delegate instanceof OutlineBufferSource;
    }

    private VertexConsumer fadeConsumer(VertexConsumer inner) {
        return height != null ? new HeightVertexConsumer(inner, height)
                : new AlphaVertexConsumer(inner, alpha);
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        try {
            if (delegate instanceof OutlineBufferSource outline) {
                return fadeOutlineBodyOnly(outline, renderType);
            }
            return fadeConsumer(delegate.getBuffer(translucent(renderType)));
        } catch (IllegalStateException e) {
            return delegate.getBuffer(renderType);
        }
    }

    /**
     * Fade a glowing body without touching the outline generator.
     *
     * <p>Joining {@code EntityOutlineGenerator} with a remapped translucent body is what
     * threw {@code IllegalStateException: Not building!} in the 2026-09-12 crash reports.
     * Outline-only types stay unwrapped. The body is taken from the inner
     * {@code bufferSource} as translucent; if that field cannot be read, the original
     * cutout path is left alone (solid, with a one-time warning).
     */
    private VertexConsumer fadeOutlineBodyOnly(OutlineBufferSource outline, RenderType renderType) {
        if (renderType.isOutline()) {
            return outline.getBuffer(renderType);
        }
        MultiBufferSource inner = innerBufferSource(outline);
        if (inner == null) {
            warnRemapSkippedName("OutlineBufferSource.bufferSource");
            return outline.getBuffer(renderType);
        }
        return fadeConsumer(inner.getBuffer(translucent(renderType)));
    }

    /**
     * Start only the outline generator. {@code outline.getBuffer} on an {@code isOutline()}
     * type never touches the inner body {@code bufferSource}.
     */
    static VertexConsumer outlineGenerator(OutlineBufferSource outline, RenderType renderType) {
        if (outline == null || renderType == null) {
            return null;
        }
        Optional<RenderType> outlineType = renderType.outline();
        if (outlineType.isEmpty()) {
            return null;
        }
        return outline.getBuffer(outlineType.get());
    }

    /**
     * {@link OutlineBufferSource}'s package-private {@code bufferSource} is the real body
     * builder. Cached after the first lookup. A miss falls back to the cutout join body.
     */
    static MultiBufferSource innerBufferSource(OutlineBufferSource outline) {
        if (outline == null) {
            return null;
        }
        try {
            Field field = outlineBufferSourceField();
            if (field == null) {
                return null;
            }
            Object value = field.get(outline);
            return value instanceof MultiBufferSource source ? source : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static volatile Field BUFFER_SOURCE_FIELD;
    private static volatile boolean BUFFER_SOURCE_FIELD_RESOLVED;

    private static Field outlineBufferSourceField() {
        if (BUFFER_SOURCE_FIELD_RESOLVED) {
            return BUFFER_SOURCE_FIELD;
        }
        synchronized (AlphaMultiBufferSource.class) {
            if (!BUFFER_SOURCE_FIELD_RESOLVED) {
                try {
                    BUFFER_SOURCE_FIELD = field(OutlineBufferSource.class, "bufferSource");
                } catch (NoSuchFieldException e) {
                    BUFFER_SOURCE_FIELD = null;
                    warnRemapSkippedName("OutlineBufferSource.bufferSource");
                }
                BUFFER_SOURCE_FIELD_RESOLVED = true;
            }
        }
        return BUFFER_SOURCE_FIELD;
    }

    private static RenderType translucent(RenderType original) {
        synchronized (TRANSLUCENT_TYPES) {
            return TRANSLUCENT_TYPES.computeIfAbsent(original, AlphaMultiBufferSource::createTranslucent);
        }
    }

    private static RenderType createTranslucent(RenderType original) {
        if (!supportsTranslucentRemap(original.format())) {
            return original;
        }
        try {
            Object state = field(original.getClass(), "state").get(original);
            Object textureState = field(state.getClass(), "textureState").get(state);
            Field textureField = field(textureState.getClass(), "texture");
            Object value = textureField.get(textureState);
            if (value instanceof Optional<?> optional && optional.orElse(null) instanceof ResourceLocation texture) {
                return RenderType.entityTranslucent(texture, true);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        warnRemapSkipped(original);
        return original;
    }

    /**
     * A remap that cannot resolve the texture returns the original type, and a cutout type
     * discards fractional alpha — so the fade silently does nothing. Say so once per type instead
     * of leaving an invisible no-op behind. The identity cache above already keeps this off the
     * per-frame path.
     */
    private static void warnRemapSkipped(RenderType original) {
        warnRemapSkippedName(original.getClass().getName());
    }

    private static void warnRemapSkippedName(String key) {
        if (WARNED.add(key)) {
            XenoPixelsMod.LOGGER.warn(
                    "Hakai/Zanzoken fade: could not build a translucent {} — the body will draw "
                            + "at full opacity on this path.", key);
        }
    }

    static boolean supportsTranslucentRemap(VertexFormat format) {
        return format == DefaultVertexFormat.NEW_ENTITY;
    }

    /**
     * Scale the alpha byte of a packed ARGB colour. {@link VertexConsumer}'s packed
     * {@code addVertex} default calls {@link #setColor(int)}; {@code BufferBuilder} also writes
     * packed RGBA directly, so this is applied before the colour reaches the builder.
     */
    static int scalePacked(int packed, float alpha) {
        int a = FastColor.ARGB32.alpha(packed);
        return FastColor.ARGB32.color(Math.round(a * Math.max(0.0f, Math.min(1.0f, alpha))), packed);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int vertexAlpha) {
            delegate.setColor(red, green, blue, Math.round(vertexAlpha * alpha));
            return this;
        }

        /**
         * {@code ModelPart.Cube#compile} uses packed {@code addVertex} which defaults to
         * {@link #setColor(int)}. Scale the packed ARGB here so a BufferBuilder override of
         * packed {@code addVertex} is not required when this wrap is in front.
         */
        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(scalePacked(color, alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    /**
     * Per-vertex Hakai wipe. {@code addVertex} stores camera-space xyz; {@code setColor}
     * rotates that offset by the camera pose and scales alpha from world Y. Zanzoken is
     * a single multiplier on top.
     */
    record HeightFade(float progress, float feetY, float bbHeight,
                      float cameraX, float cameraY, float cameraZ,
                      float qx, float qy, float qz, float qw,
                      float minAlpha, float curve, float speed, float band,
                      float zanzoken, float uniformFallback) {
        float alphaForVertex(float cx, float cy, float cz) {
            float worldY = HakaiFade.worldYFromCameraSpace(cx, cy, cz, cameraY, qx, qy, qz, qw);
            float wipe = HakaiFade.alphaAtWorldY(progress, worldY, feetY, bbHeight,
                    minAlpha, curve, speed, band, uniformFallback);
            return Math.max(0.0f, Math.min(1.0f, wipe * Math.max(0.0f, Math.min(1.0f, zanzoken))));
        }
    }

    private static final class HeightVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final HeightFade fade;
        private float lastX = Float.NaN;
        private float lastY = Float.NaN;
        private float lastZ = Float.NaN;

        HeightVertexConsumer(VertexConsumer delegate, HeightFade fade) {
            this.delegate = delegate;
            this.fade = fade;
        }

        private float currentAlpha() {
            if (Float.isNaN(lastY)) return fade.uniformFallback;
            return fade.alphaForVertex(lastX, lastY, lastZ);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            lastX = x;
            lastY = y;
            lastZ = z;
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int vertexAlpha) {
            delegate.setColor(red, green, blue, Math.round(vertexAlpha * currentAlpha()));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(AlphaMultiBufferSource.scalePacked(color, currentAlpha()));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
