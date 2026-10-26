package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;

/**
 * One opaque, bounded HD equirectangular map, loaded once and on resource reload. A missing map is drawn as a
 * flat fallback colour instead of failing, so a body whose art has not shipped yet is still visible.
 */
final class PlanetSurfaceTexture extends AbstractTexture {
    static final ResourceLocation EARTH=ResourceLocation.fromNamespaceAndPath("xenopixelsmod","textures/space/earth_surface_hd_v1.png");
    static final ResourceLocation MOON=ResourceLocation.fromNamespaceAndPath("xenopixelsmod","textures/space/moon_surface_hd_v1.png");
    static final ResourceLocation SUN=ResourceLocation.fromNamespaceAndPath("xenopixelsmod","textures/space/sun_surface_hd_v1.png");
    /** Kept for the orbit-spike binding, which has always drawn Earth's map. */
    static final ResourceLocation LOCATION=EARTH;

    private final ResourceLocation source;
    /** ABGR, as {@link NativeImage} stores pixels. */
    private final int fallbackAbgr;
    /** True once {@link #load} decoded {@link #source}; false while unloaded or drawing the flat fallback. */
    private volatile boolean decoded;

    PlanetSurfaceTexture(ResourceLocation source,int fallbackAbgr) {
        this.source=source;
        this.fallbackAbgr=fallbackAbgr;
    }

    /**
     * The map for a charted body: Earth, Moon and Sol keep their historical files; every other body reads
     * {@code textures/space/<id path>_surface_hd_v1.png}, so a datapack body gets art by dropping one file.
     */
    static ResourceLocation forBody(String bodyId) {
        if(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.MOON_ID.equals(bodyId))return MOON;
        if("xenopixelsmod:sol".equals(bodyId))return SUN;
        if(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.BODY_ID.equals(bodyId))return EARTH;
        var id=ResourceLocation.tryParse(bodyId);
        if(id==null)return EARTH;
        if("xenopixelsmod".equals(id.getNamespace()) && id.getPath().startsWith("sdb/planet/"))
            return ResourceLocation.fromNamespaceAndPath("xenopixelsmod","textures/space/sdb_planet_surface_hd_v1.png");
        String path=id.getPath().replace('/','_');
        return ResourceLocation.fromNamespaceAndPath("xenopixelsmod","textures/space/"+path+"_surface_hd_v1.png");
    }
    /** ABGR flat colour for a missing map: fixed for the known three, a stable hash tint otherwise. */
    static int fallbackFor(ResourceLocation location) {
        if(location.equals(MOON))return 0xFF8A8A8A;
        if(location.equals(SUN))return 0xFF30C8FF;
        if(location.equals(EARTH))return 0xFFB06A2A;
        int hash=location.hashCode();
        return 0xFF000000|((0x60+(hash&0x7F))<<16)|((0x60+((hash>>8)&0x7F))<<8)|(0x60+((hash>>16)&0x7F));
    }

    /** The map this texture was built from, for the identity check. */
    ResourceLocation source() { return source; }
    boolean decoded() { return decoded; }

    @Override public void load(ResourceManager resources) throws IOException {
        NativeImage[] levels=null;
        try {
            var base=new NativeImage(2048,1024,false);levels=new NativeImage[]{base};
            var resource=resources.getResource(source);
            if(resource.isPresent()) {
                try(var stream=resource.get().open();var image=NativeImage.read(stream)) {
                    image.resizeSubRectTo(0,0,image.getWidth(),image.getHeight(),base);
                }
                decoded=true;
            } else {
                decoded=false;
                base.fillRect(0,0,2048,1024,fallbackAbgr);
                XenoPixelsMod.LOGGER.warn("Planet surface map {} is missing; drawing a flat fallback colour",source);
            }
            levels=MipmapGenerator.generateMipLevels(levels,4);
            TextureUtil.prepareImage(getId(),4,2048,1024);
            for(int i=0;i<levels.length;i++) {
                var level=levels[i];
                level.upload(i,0,0,0,0,level.getWidth(),level.getHeight(),true,false,true,false);
            }
            setFilter(true,true);
        } finally {if(levels!=null)for(var level:levels)level.close();}
    }
}
