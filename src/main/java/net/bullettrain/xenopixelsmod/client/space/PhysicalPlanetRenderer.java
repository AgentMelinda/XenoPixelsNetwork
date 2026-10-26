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
import org.lwjgl.opengl.GL14;
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
    private static final String ORBIT_EARTH_ID=PlanetRenderBindings.ORBIT_EARTH_ID;
    private static final ResourceLocation VOIDSEA=net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST;

    /**
     * The drawn sphere's radius, measured off the body's own plot box instead of a constant: plot-local
     * inclusive bounds enclose the outer voxel corners without rotation-dependent growth.
     */
    private static final org.joml.Vector3d CENTER=new org.joml.Vector3d();
    private static final Matrix4f MODEL=new Matrix4f();
    private static final Quaternionf ROTATION=new Quaternionf();
    /** Chart id → Sable UUID; {@link #LEGACY} holds the single-UUID orbit-spike binding. */
    private static final Map<String,UUID> BINDINGS=new LinkedHashMap<>();
    private static final java.util.Set<String> REPORTED=new java.util.HashSet<>();
    private static final Set<ResourceLocation> REGISTERED=new HashSet<>();
    /** Texture each bound body last drew with, for the on-demand identity check; written only on change. */
    private static final Map<String,ResourceLocation> NEAR_BOUND=new java.util.HashMap<>();
    private static final double MAX_DRAW=1_000_000.0;
    private static VertexBuffer surface;
    private PhysicalPlanetRenderer() {}

    /** Orbit-spike / legacy binding: one body drawn with Earth's map. */
    public static void bind(UUID id) {
        // A single-body packet describes the entire current non-host binding set. Retaining the
        // previous Voidsea set here leaves stale planets in the renderer after a dimension change.
        PlanetRenderBindings.bindOrbitEarth(BINDINGS,id);
        REPORTED.remove(ORBIT_EARTH_ID);
        NEAR_BOUND.keySet().retainAll(BINDINGS.keySet());
        XenoPixelsMod.LOGGER.info("Physical planet surface binding: {}",id);
    }
    public static void bindAll(PhysicalPlanetBindings packet) {
        PlanetRenderBindings.bindAll(BINDINGS,packet);
        REPORTED.clear();
        NEAR_BOUND.keySet().retainAll(BINDINGS.keySet());
        net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetGravity.bindClient(packet);
        XenoPixelsMod.LOGGER.info("Physical planet surface bindings: {}",BINDINGS);
    }
    /** Whether this exact body id has a non-removed replicated Sable incarnation in the client's current level. */
    static boolean hasLiveBodyBinding(String bodyId,SubLevelContainer container) {
        if(container==null) return false;
        return PlanetRenderBindings.hasLiveBody(BINDINGS,bodyId,uuid -> {
            var body=container.getSubLevel(uuid);
            return body instanceof ClientSubLevel live && !live.isRemoved();
        });
    }
    /** Resolve the exact bound body's render-space centre for both the HD surface and its far projection. */
    static boolean liveBodyCenter(String bodyId,SubLevelContainer container,float partialTick,
                                  org.joml.Vector3d destination) {
        if(container==null) return false;
        UUID uuid=BINDINGS.get(bodyId);
        if(uuid==null) return false;
        var candidate=container.getSubLevel(uuid);
        if(!(candidate instanceof ClientSubLevel body)||body.isRemoved()) return false;
        var pose=body.renderPose(partialTick);
        var box=body.getPlot().getBoundingBox();
        pose.transformPosition(destination.set((box.minX()+box.maxX()+1)/2.0,
                (box.minY()+box.maxY()+1)/2.0,(box.minZ()+box.maxZ()+1)/2.0));
        return true;
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) {BINDINGS.clear();REPORTED.clear();NEAR_BOUND.clear();net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetGravity.clearClient();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {BINDINGS.clear();REPORTED.clear();NEAR_BOUND.clear();net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetGravity.clearClient();}
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
        }
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean depthClamp=GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
        int depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean hasDamagedSurface = drawBodies && BINDINGS.keySet().stream()
                .anyMatch(id -> PlanetIntegritySurfaceTint.hasCrackOverlay(PlanetIntegrityClient.stage(id)));
        int blendSrc = hasDamagedSurface ? GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) : 0;
        int blendDst = hasDamagedSurface ? GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) : 0;
        int blendSrcAlpha = hasDamagedSurface ? GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA) : 0;
        int blendDstAlpha = hasDamagedSurface ? GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) : 0;
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
                int sdbSlot=net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity
                        .slotFromPlanetKey(entry.getKey());
                var location=sdbSlot>0
                        ?null
                        :ORBIT_EARTH_ID.equals(entry.getKey())?PlanetSurfaceTexture.EARTH:PlanetSurfaceTexture.forBody(entry.getKey());
                draw(event,entry.getKey(),location,sdbSlot,CENTER.x,CENTER.y,CENTER.z,pose.orientation(),
                        (float) net.bullettrain.xenopixelsmod.space.physics.PlanetBodyGeometry.hullRadius(box),pack);
            }
        } finally {
            VertexBuffer.unbind();RenderSystem.depthMask(write);
            RenderSystem.depthFunc(depthFunction);
            if (hasDamagedSurface) RenderSystem.blendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha);
            if(depthClamp)GL11.glEnable(GL32.GL_DEPTH_CLAMP);else GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0,texture);RenderSystem.setShaderColor(r,g,b,a);
            if(shader!=null)RenderSystem.setShader(()->shader);
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private static void draw(RenderLevelStageEvent event,String id,ResourceLocation location,int sdbSlot,
                             double x,double y,double z,Quaternionfc orientation,float radius,boolean pack) {
        var camera=event.getCamera().getPosition();
        // The bail guards float precision in the camera-relative translation.
        if(!(radius>0)||camera.distanceToSqr(x,y,z)>MAX_DRAW*MAX_DRAW) return;
        // Hand off only when the enabled projection pass actually contains this body's live position.
        // If it is disabled or its chart LOD omitted a moved body, keep the real replicated sphere visible.
        double burn=PlanetHakaiClient.progress(id,event.getPartialTick().getGameTimeDeltaPartialTick(false));
        var integrityStage = PlanetIntegrityClient.stage(id);
        if (integrityStage == net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules.Stage.DESTROYED) return;
        boolean projectionVisible=OrbitPlanetProxies.wouldDraw(id,camera,
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if(!PlanetHakaiRenderPolicy.drawPhysical(projectionVisible,burn>=0,true)) return;
        if(sdbSlot>0) location=DragonBallPlanetClient.textureForSlot(sdbSlot);
        ensureTexture(location);
        RenderSystem.setShaderTexture(0,location);
        if(!location.equals(NEAR_BOUND.get(id))) NEAR_BOUND.put(id,location);
        MODEL.set(event.getModelViewMatrix()).translate((float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z))
                .rotate(ROTATION.set(orientation)).scale(radius);
        // Planet Hakai: a burning body is cut away from the top down instead of drawn whole.
        RenderSystem.setShaderColor(1,1,1,1);
        if(burn>=0){VertexBuffer.unbind();PlanetHakaiClient.drawBurning(event,id,MODEL,orientation,burn);return;}
        var stellar = StellarStageClient.appearance(id, Minecraft.getInstance().level.getGameTime());
        var integrityTint = PlanetIntegritySurfaceTint.forStage(integrityStage);
        RenderSystem.setShaderColor((float)stellar.red() * integrityTint.red(),
                (float)stellar.green() * integrityTint.green(),
                (float)stellar.blue() * integrityTint.blue(), 1.0f);
        surface.bind();surface.drawWithShader(MODEL,event.getProjectionMatrix(),GameRenderer.getPositionTexColorShader());
        if (PlanetIntegritySurfaceTint.hasCrackOverlay(integrityStage)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            PlanetIntegrityCrackOverlay.draw(event, id, integrityStage, MODEL, surface);
            RenderSystem.disableBlend();
        }
        if(REPORTED.add(id))
            XenoPixelsMod.LOGGER.info("Physical HD planet rendered: body={}, centre=({}, {}, {}), drawn radius={}, map={}, shaderPack={}",
                    id,x,y,z,radius,location,pack);
    }
    private static void draw(RenderLevelStageEvent event,String id,ResourceLocation location,int sdbSlot,
                             double x,double y,double z,org.joml.Quaterniondc orientation,float radius,boolean pack) {
        draw(event,id,location,sdbSlot,x,y,z,ROTATION.set(orientation),radius,pack);
    }

    /** Every bound body with the texture it last drew with (null if not drawn since binding). */
    static java.util.List<PlanetTextureChecks.Observed> nearObserved() {
        var list=new java.util.ArrayList<PlanetTextureChecks.Observed>(BINDINGS.size());
        for(String id:BINDINGS.keySet()) list.add(new PlanetTextureChecks.Observed(id,NEAR_BOUND.get(id)));
        return list;
    }
    /**
     * The texture that belongs to a bound body, matching render(): its HD map, or for an SDB its slot texture.
     * An SDB that has not drawn yet answers null rather than generating its texture just for the report.
     */
    static ResourceLocation expectedNear(String id) {
        int slot=net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity.slotFromPlanetKey(id);
        if(slot>0) return NEAR_BOUND.containsKey(id)?DragonBallPlanetClient.textureForSlot(slot):null;
        return ORBIT_EARTH_ID.equals(id)?PlanetSurfaceTexture.EARTH:PlanetSurfaceTexture.forBody(id);
    }
    /** Live render centre of a bound body, or false when it has no live incarnation in this level. */
    static boolean nearCenter(String id,float partialTick,org.joml.Vector3d destination) {
        var level=Minecraft.getInstance().level;
        return level!=null && liveBodyCenter(id,SubLevelContainer.getContainer(level),partialTick,destination);
    }
    /** Whether a bound body currently has a live replicated Sable incarnation in this level. */
    static boolean nearLive(String id) {
        var level=Minecraft.getInstance().level;
        return level!=null && hasLiveBodyBinding(id,SubLevelContainer.getContainer(level));
    }

    static void ensureTexture(ResourceLocation location) {
        if(DragonBallPlanetClient.ownsTexture(location)) return;
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
