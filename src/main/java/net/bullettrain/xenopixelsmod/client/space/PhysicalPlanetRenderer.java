package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.PhysicalPlanetBindings;
import net.bullettrain.xenopixelsmod.space.math.PlanetSurfaceMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HD 3D surfaces attached only to actual replicated Sable planet bodies. Chart-position fallback globes are retired.
 *
 * <p>Without a shader pack the spheres draw at {@code AFTER_ENTITIES} with depth clamp, so a planet beyond
 * the terrain far plane survives while nearer terrain still occludes it. With an Iris pack active, a planet
 * written at clamped depth 1.0 is treated as sky by the pack's composite passes and painted over, so the
 * spheres instead draw at {@code AFTER_LEVEL} into the re-bound main target, the way this mod's NPC auras
 * survive packs, and never during the shadow pass.
 */
@EventBusSubscriber(modid=XenoPixelsMod.MOD_ID,value=Dist.CLIENT)
public final class PhysicalPlanetRenderer {
    private static final String LEGACY="legacy";
    private static final ResourceLocation VOIDSEA=net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST;

    /**
     * The drawn sphere's radius, measured off the body's own plot box instead of a constant: plot-local
     * inclusive bounds enclose the outer voxel corners without rotation-dependent growth.
     */
    private static float hullRadius(dev.ryanhcode.sable.companion.math.BoundingBox3ic box) {
        if (box == null) return 0;
        double centres=Math.max(box.maxX()-box.minX(),Math.max(box.maxY()-box.minY(),box.maxZ()-box.minZ()))/2.0;
        return (float)(centres+Math.sqrt(3)/2.0);
    }
    private static final org.joml.Vector3d CENTER=new org.joml.Vector3d();
    private static final Matrix4f MODEL=new Matrix4f();
    private static final Quaternionf ROTATION=new Quaternionf();
    /** Chart id → Sable UUID; {@link #LEGACY} holds the single-UUID orbit-spike binding. */
    private static final Map<String,UUID> BINDINGS=new LinkedHashMap<>();
    private static final java.util.Set<String> REPORTED=new java.util.HashSet<>();
    private static final Set<ResourceLocation> REGISTERED=new HashSet<>();
    private static final double MAX_DRAW=1_000_000.0;
    private static VertexBuffer surface;
    private PhysicalPlanetRenderer() {}

    /** Orbit-spike / legacy binding: one body drawn with Earth's map. */
    public static void bind(UUID id) {
        BINDINGS.remove(LEGACY);
        if(id!=null) BINDINGS.put(LEGACY,id);
        REPORTED.remove(LEGACY);
        XenoPixelsMod.LOGGER.info("Physical planet surface binding: {}",id);
    }
    public static void bindAll(PhysicalPlanetBindings packet) {
        BINDINGS.clear();REPORTED.clear();
        for(var entry:packet.entries()) BINDINGS.put(entry.bodyId(),entry.uuid());
        net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetGravity.bindClient(packet);
        XenoPixelsMod.LOGGER.info("Physical planet surface bindings: {}",BINDINGS);
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) {BINDINGS.clear();REPORTED.clear();net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetGravity.clearClient();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {BINDINGS.clear();REPORTED.clear();net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetGravity.clearClient();}
    static void closeBuffers() {if(surface!=null){surface.close();surface=null;}REGISTERED.clear();REPORTED.clear();}

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        boolean drawBodies = SpacePlanetMode.byName(net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.spacePlanetMode)
                .drawsBodies();
        boolean pack=IrisShaderState.packInUse();
        var wanted=pack?RenderLevelStageEvent.Stage.AFTER_LEVEL:RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if(event.getStage()!=wanted) return;
        if(pack && IrisShaderState.renderingShadows()) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null) return;
        boolean voidsea=VOIDSEA.equals(mc.level.dimension().location());
        if(BINDINGS.isEmpty() && !voidsea) return;
        if(pack) {
            mc.getMainRenderTarget().bindWrite(false);
            // The pack drew its overworld sky here; put space back behind everything first.
            if(voidsea) SpaceSkyEffects.drawOverShaderPackSky(event.getModelViewMatrix(),event.getProjectionMatrix());
        }
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean depthClamp=GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
        int depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int texture=RenderSystem.getShaderTexture(0);
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        var shader=RenderSystem.getShader();var color=RenderSystem.getShaderColor();
        float r=color[0],g=color[1],b=color[2],a=color[3];
        try {
            if(drawBodies) {
                ensureBuffer();FogRenderer.setupNoFog();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
                // Keep the actual sphere and world projection. Only its far depth is clamped: LEQUAL
                // admits it over clear depth 1 while every nearer terrain fragment still occludes it.
                GL11.glEnable(GL32.GL_DEPTH_CLAMP);RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.enableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            }
            float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
            var container=SubLevelContainer.getContainer(mc.level);
            if(container==null) return;
            for(var entry:BINDINGS.entrySet()) {
                var candidate=container.getSubLevel(entry.getValue());
                if(!(candidate instanceof ClientSubLevel body)||body.isRemoved()) continue;
                if(!drawBodies) continue;
                var pose=body.renderPose(partial);
                var box=body.getPlot().getBoundingBox();
                pose.transformPosition(CENTER.set((box.minX()+box.maxX()+1)/2.0,
                        (box.minY()+box.maxY()+1)/2.0,(box.minZ()+box.maxZ()+1)/2.0));
                var location=LEGACY.equals(entry.getKey())?PlanetSurfaceTexture.EARTH:PlanetSurfaceTexture.forBody(entry.getKey());
                draw(event,entry.getKey(),location,CENTER.x,CENTER.y,CENTER.z,pose.orientation(),hullRadius(box),pack);
            }
        } finally {
            VertexBuffer.unbind();RenderSystem.depthMask(write);
            RenderSystem.depthFunc(depthFunction);
            if(depthClamp)GL11.glEnable(GL32.GL_DEPTH_CLAMP);else GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0,texture);RenderSystem.setShaderColor(r,g,b,a);
            if(shader!=null)RenderSystem.setShader(()->shader);
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private static void draw(RenderLevelStageEvent event,String id,ResourceLocation location,double x,double y,double z,
                             Quaternionfc orientation,float radius,boolean pack) {
        var camera=event.getCamera().getPosition();
        // The bail guards float precision in the camera-relative translation.
        if(!(radius>0)||camera.distanceToSqr(x,y,z)>MAX_DRAW*MAX_DRAW) return;
        // Hand far chart bodies to the cached lightweight disc renderer. Their Sable bodies keep simulating;
        // this only avoids drawing a full HD sphere when it is a tiny distant point anyway.
        if(!LEGACY.equals(id) && camera.distanceToSqr(x,y,z)>OrbitPlanetProxies.PHYSICAL_HANDOFF_DISTANCE_SQR) return;
        ensureTexture(location);
        RenderSystem.setShaderTexture(0,location);
        MODEL.set(event.getModelViewMatrix()).translate((float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z))
                .rotate(ROTATION.set(orientation)).scale(radius);
        // Planet Hakai: a burning body is cut away from the top down instead of drawn whole.
        double burn=PlanetHakaiClient.progress(LEGACY.equals(id)?"xenopixelsmod:earth":id,event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if(burn>=0){VertexBuffer.unbind();PlanetHakaiClient.drawBurning(event,MODEL,orientation,burn);return;}
        surface.bind();surface.drawWithShader(MODEL,event.getProjectionMatrix(),GameRenderer.getPositionTexColorShader());
        if(REPORTED.add(id))
            XenoPixelsMod.LOGGER.info("Physical HD planet rendered: body={}, centre=({}, {}, {}), drawn radius={}, map={}, shaderPack={}",
                    id,x,y,z,radius,location,pack);
    }
    private static void draw(RenderLevelStageEvent event,String id,ResourceLocation location,double x,double y,double z,
                             org.joml.Quaterniondc orientation,float radius,boolean pack) {
        draw(event,id,location,x,y,z,ROTATION.set(orientation),radius,pack);
    }

    private static void ensureTexture(ResourceLocation location) {
        if(REGISTERED.add(location))
            Minecraft.getInstance().getTextureManager().register(location,
                    new PlanetSurfaceTexture(location,PlanetSurfaceTexture.fallbackFor(location)));
    }
    private static void ensureBuffer() {
        if(surface!=null)return;
        var mesh=PlanetSurfaceMesh.create();
        var builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(int i=0;i<mesh.uv().length/2;i++)builder.addVertex(mesh.positions()[i*3],mesh.positions()[i*3+1],mesh.positions()[i*3+2])
                .setUv(mesh.uv()[i*2],mesh.uv()[i*2+1]).setColor(0xffffffff);
        var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {buffer.bind();buffer.upload(builder.buildOrThrow());surface=buffer;}
        catch(RuntimeException|Error error){buffer.close();throw error;}
        finally {VertexBuffer.unbind();}
    }
}
