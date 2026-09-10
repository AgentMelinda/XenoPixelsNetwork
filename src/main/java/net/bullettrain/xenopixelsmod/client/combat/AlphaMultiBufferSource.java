package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

final class AlphaMultiBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float alpha;
    private static final Map<RenderType, RenderType> TRANSLUCENT_TYPES = new IdentityHashMap<>();

    AlphaMultiBufferSource(MultiBufferSource delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return new AlphaVertexConsumer(delegate.getBuffer(translucent(renderType)), alpha);
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
        return original;
    }

    static boolean supportsTranslucentRemap(VertexFormat format) {
        return format == DefaultVertexFormat.NEW_ENTITY;
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
