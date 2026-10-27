package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;

/**
 * A 512x256 copy of one body's HD equirectangular map for far projections, which cover only a few hundred
 * pixels. Same source and fallback colour as {@link PlanetSurfaceTexture}; the near HD texture is untouched.
 */
final class PlanetFarProjectionTexture extends AbstractTexture {
    static final int WIDTH = 512, HEIGHT = 256, MIP_LEVELS = 4;
    /** RGBA bytes of the base level plus its mip chain (each level a quarter of the previous). */
    static final long BYTES = bytesWithMips(WIDTH, HEIGHT, MIP_LEVELS);

    private final ResourceLocation source;
    /** ABGR, as {@link NativeImage} stores pixels. */
    private final int fallbackAbgr;
    /** True once {@link #load} decoded {@link #source}; false while unloaded or drawing the flat fallback. */
    private volatile boolean decoded;

    PlanetFarProjectionTexture(ResourceLocation source, int fallbackAbgr) {
        this.source = source;
        this.fallbackAbgr = fallbackAbgr;
    }

    /** The near HD map this far copy was built from, for the identity check. */
    ResourceLocation source() { return source; }
    boolean decoded() { return decoded; }

    static long bytesWithMips(int width, int height, int mipLevels) {
        long total = 0;
        for (int level = 0; level <= mipLevels; level++)
            total += 4L * Math.max(1, width >> level) * Math.max(1, height >> level);
        return total;
    }

    @Override public void load(ResourceManager resources) throws IOException {
        NativeImage[] levels = null;
        try {
            var base = new NativeImage(WIDTH, HEIGHT, false);
            levels = new NativeImage[]{base};
            var resource = resources.getResource(source);
            if (resource.isPresent()) {
                try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
                    image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), base);
                }
                decoded = true;
            } else {
                decoded = false;
                base.fillRect(0, 0, WIDTH, HEIGHT, fallbackAbgr);
            }
            levels = MipmapGenerator.generateMipLevels(levels, MIP_LEVELS);
            TextureUtil.prepareImage(getId(), MIP_LEVELS, WIDTH, HEIGHT);
            for (int i = 0; i < levels.length; i++) {
                var level = levels[i];
                level.upload(i, 0, 0, 0, 0, level.getWidth(), level.getHeight(), true, false, true, false);
            }
            setFilter(true, true);
        } finally {
            if (levels != null) for (var level : levels) level.close();
        }
    }
}
