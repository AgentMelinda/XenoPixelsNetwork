package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.space.SpaceSnapshot;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.io.IOException;

/**
 * Far-distance chart projections. These visual-only fallback discs complement nearby/replicated Sable
 * bodies, which retain their full physics and HD sphere rendering. Geometry is a tiny fixed set of cached quads.
 */
@EventBusSubscriber(modid=XenoPixelsMod.MOD_ID, value=Dist.CLIENT)
public final class OrbitPlanetProxies {
    private static final ResourceLocation ORBIT=ResourceLocation.fromNamespaceAndPath("xenospace_spike","orbit");
    private static final ResourceLocation VOIDSEA=net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST;
    private record Body(String id,double x,double y,double z,float radius,int style) {
        Body(SpaceSnapshot.Body body) {
            this(body.id(),body.x(),body.y(),body.z(),(float)body.radius(),body.style());
        }
    }
    private static final Body[] EMPTY=new Body[0];
    private static Body[] bodies=EMPTY;
    private static int revision=-1;
    /**
     * The terrain frustum's far plane is the client render distance, and a chart body is deliberately
     * further away than that — charted Earth sits 1 000 blocks out in an 8-chunk world — so culling this
     * sky layer with {@code RenderLevelStageEvent#getFrustum()} discarded every planet. Measured
     * 2026-10-14: a full dev-client run drew nothing at all. Distance is the right test, with room for the
     * whole PROXY stage the server's LOD ladder selects.
     */
    private static final double DRAW_REACH=100_000.0;
    static final double PHYSICAL_HANDOFF_DISTANCE_SQR=512.0*512.0;
    private static double distanceSquared(Vec3 camera,Body body) {
        double dx=body.x-camera.x(),dy=body.y-camera.y(),dz=body.z-camera.z();
        return dx*dx+dy*dy+dz*dz;
    }
    /** One static quad per proxy atlas cell, so a body's {@code style} picks a buffer rather than a build. */
    private static final VertexBuffer[] BUFFERS=new VertexBuffer[CelestialBody.MAX_VISUAL_STYLES];
    private static final Matrix4f MODEL=new Matrix4f();
    private static boolean ready;
    private OrbitPlanetProxies() {}
    public static void apply(SpaceSnapshot snapshot) {
        if(snapshot.revision()<revision)return;
        boolean report=snapshot.revision()!=revision || snapshot.bodies().size()!=bodies.length;
        var candidate=new Body[snapshot.bodies().size()];
        for(int i=0;i<candidate.length;i++)candidate[i]=new Body(snapshot.bodies().get(i));
        bodies=candidate;revision=snapshot.revision();
        if(report)XenoPixelsMod.LOGGER.info("Space visual snapshot applied: revision={}, planets={}",revision,bodies.length);
    }
    /**
     * Chart position and radius of one body in the latest snapshot, or null when the server is not
     * currently sending it. Used by the HD Sun, which draws from the same server decision as the proxies.
     */
    static double[] find(String id) {
        for(Body body:bodies) if(body.id.equals(id)) return new double[]{body.x,body.y,body.z,body.radius};
        return null;
    }
    /** Every body in the latest snapshot as (id, {x, y, z, radius}); the array is reused per call. */
    static void forEach(java.util.function.BiConsumer<String,double[]> action) {
        double[] row=new double[4];
        for(Body body:bodies) {row[0]=body.x;row[1]=body.y;row[2]=body.z;row[3]=body.radius;action.accept(body.id,row);}
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) {clearState();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {clearState();}
    private static void clearState() {bodies=EMPTY;revision=-1;}

    static void closeBuffers() {
        for(int i=0;i<BUFFERS.length;i++) if(BUFFERS[i]!=null) {BUFFERS[i].close();BUFFERS[i]=null;}
        ready=false;
    }
    // Unsubscribed by owner decision, 2026-10-13 ("planets must be actual Sable bodies"), and
    // Kept only as an inert compatibility class for already-registered snapshot packets and old cleanup hooks.
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(!SpacePlanetMode.byName(XenoClientConfig.spacePlanetMode).drawsProxies()) return;
        if(!XenoClientConfig.spacePlanetProjections) return;
        boolean pack=IrisShaderState.packInUse();
        var wanted=pack?RenderLevelStageEvent.Stage.AFTER_LEVEL:RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if(event.getStage()!=wanted || (pack&&IrisShaderState.renderingShadows())) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null) return;
        var dimension=mc.level.dimension().location();
        if(!VOIDSEA.equals(dimension) && !ORBIT.equals(dimension)) return;
        var camera=event.getCamera().getPosition();
        boolean visible=false;
        for(var body:bodies) {
            double d2=distanceSquared(camera,body);
            if(d2<=DRAW_REACH*DRAW_REACH && d2>PHYSICAL_HANDOFF_DISTANCE_SQR) {visible=true;break;}
        }
        if(!visible) return;
        if(pack) mc.getMainRenderTarget().bindWrite(false);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthClamp=GL11.glIsEnabled(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int depthFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int texture=RenderSystem.getShaderTexture(0);
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        var shader=RenderSystem.getShader();var color=RenderSystem.getShaderColor();
        float r=color[0],g=color[1],b=color[2],a=color[3];
        try {
            ensureBuffers();
            FogRenderer.setupNoFog();RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);
            GL11.glEnable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
            RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0,PlanetSkyTexture.LOCATION);
            for(var body:bodies) {
                double d2=distanceSquared(camera,body);
                if(d2>DRAW_REACH*DRAW_REACH || d2<=PHYSICAL_HANDOFF_DISTANCE_SQR) continue;
                MODEL.set(event.getModelViewMatrix()).translate((float)(body.x-camera.x),
                        (float)(body.y-camera.y),(float)(body.z-camera.z));
                float radius=body.radius;
                MODEL.m00(radius).m01(0).m02(0).m10(0).m11(radius).m12(0).m20(0).m21(0).m22(radius);
                BUFFERS[body.style].bind();BUFFERS[body.style].drawWithShader(MODEL,event.getProjectionMatrix(),GameRenderer.getPositionTexColorShader());
            }
        } finally {
            VertexBuffer.unbind();RenderSystem.depthMask(write);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(depthClamp)GL11.glEnable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);else GL11.glDisable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.blendFuncSeparate(src,dst,srcA,dstA);RenderSystem.setShaderTexture(0,texture);
            RenderSystem.setShaderColor(r,g,b,a);if(shader!=null)RenderSystem.setShader(()->shader);
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
        }
    }
    private static void ensureBuffers() {
        if (ready) return;
        try {
            // Load the atlas here instead of leaving it to the next resource reload, so the very first
            // frame that draws a proxy samples populated texture levels. The 2026-10-14 capture showed the
            // proxies drawing black before this call existed; whether the texture manager would have
            // loaded the queued entry on the first getByName is not verified.
            var sky = new PlanetSkyTexture();
            sky.load(Minecraft.getInstance().getResourceManager());
            Minecraft.getInstance().getTextureManager().register(PlanetSkyTexture.LOCATION, sky);
            // Two explicit triangles rather than a 4-vertex quad. VertexBuffer discards the build mode and
            // draws the array itself, so a 4-vertex buffer only covers the quad if that draw is a strip; the
            // 2026-10-14 capture showed a black wedge where charted Earth should have been. This layout
            // covers the whole quad either way (the extra triangles are degenerate or duplicates).
            for(int slot=0;slot<BUFFERS.length;slot++) {
                float u0=slot/(float)BUFFERS.length,u1=(slot+1)/(float)BUFFERS.length;
                var builder=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
                builder.addVertex(-1,-1,0).setUv(u0,1).setColor(0xffffffff);
                builder.addVertex(1,-1,0).setUv(u1,1).setColor(0xffffffff);
                builder.addVertex(1,1,0).setUv(u1,0).setColor(0xffffffff);
                builder.addVertex(-1,-1,0).setUv(u0,1).setColor(0xffffffff);
                builder.addVertex(1,1,0).setUv(u1,0).setColor(0xffffffff);
                builder.addVertex(-1,1,0).setUv(u0,0).setColor(0xffffffff);
                var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);BUFFERS[slot]=buffer;
                buffer.bind();buffer.upload(builder.buildOrThrow());
            }
            ready=true;
            XenoPixelsMod.LOGGER.info("Distant planet projection resources initialized: {} cached styles",
                    BUFFERS.length);
        } catch(RuntimeException|Error|IOException failure) {closeBuffers();throw new RuntimeException(failure);}
        finally {VertexBuffer.unbind();}
    }
}
