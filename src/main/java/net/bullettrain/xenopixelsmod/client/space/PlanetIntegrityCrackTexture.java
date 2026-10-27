package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

/** One shared, mipmapped transparent crack map; generated once per client resource load. */
final class PlanetIntegrityCrackTexture extends AbstractTexture {
    static final ResourceLocation SCARRED = ResourceLocation.fromNamespaceAndPath(
            "xenopixelsmod", "dynamic/planet_integrity_cracks_v1");
    static final ResourceLocation CRITICAL = ResourceLocation.fromNamespaceAndPath(
            "xenopixelsmod", "dynamic/planet_integrity_critical_cracks_v1");

    private final boolean critical;

    PlanetIntegrityCrackTexture(boolean critical) { this.critical = critical; }

    @Override public void load(ResourceManager resources) throws IOException {
        NativeImage[] levels = null;
        try {
            var base = new NativeImage(PlanetIntegrityCrackPattern.WIDTH, PlanetIntegrityCrackPattern.HEIGHT, true);
            levels = new NativeImage[]{base};
            base.fillRect(0, 0, PlanetIntegrityCrackPattern.WIDTH, PlanetIntegrityCrackPattern.HEIGHT, 0);
            int[] pixels = PlanetIntegrityCrackPattern.create(critical);
            for (int y = 0; y < PlanetIntegrityCrackPattern.HEIGHT; y++)
                for (int x = 0; x < PlanetIntegrityCrackPattern.WIDTH; x++) {
                    int pixel = pixels[y * PlanetIntegrityCrackPattern.WIDTH + x];
                    if (pixel != 0) base.setPixelRGBA(x, y, pixel);
                }
            levels = MipmapGenerator.generateMipLevels(levels, 4);
            TextureUtil.prepareImage(getId(), 4, PlanetIntegrityCrackPattern.WIDTH,
                    PlanetIntegrityCrackPattern.HEIGHT);
            for (int i = 0; i < levels.length; i++) {
                NativeImage level = levels[i];
                level.upload(i, 0, 0, 0, 0, level.getWidth(), level.getHeight(), true, false, true, false);
            }
            setFilter(true, true);
        } finally {
            if (levels != null) for (NativeImage level : levels) level.close();
        }
    }
}
