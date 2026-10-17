package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;

/** One bounded atlas, uploaded only on first use or resource reload. */
final class PlanetSkyTexture extends AbstractTexture {
    static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(
            "xenopixelsmod", "textures/space/planets_hd_v1.png");
    private static final int WIDTH = 2048, HEIGHT = 1024, MIP_LEVELS = 4;

    @Override public void load(ResourceManager resources) throws IOException {
        // Power-of-two upload dimensions allow the pinned vanilla mipmap generator to
        // populate every level, even when a resource pack supplies a non-power-of-two PNG.
        NativeImage[] levels = null;
        try (var stream = resources.getResourceOrThrow(LOCATION).open();
             var source = NativeImage.read(stream)) {
            NativeImage base = new NativeImage(WIDTH, HEIGHT, false);
            levels = new NativeImage[]{base};
            source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), base);
            levels = MipmapGenerator.generateMipLevels(levels, MIP_LEVELS);
            TextureUtil.prepareImage(getId(), MIP_LEVELS, WIDTH, HEIGHT);
            for (int i = 0; i < levels.length; i++) {
                var image = levels[i];
                image.upload(i, 0, 0, 0, 0, image.getWidth(), image.getHeight(),
                        true, true, true, false);
            }
            setFilter(true, true);
        } finally {
            if (levels != null) for (var image : levels) image.close();
        }
    }
}
