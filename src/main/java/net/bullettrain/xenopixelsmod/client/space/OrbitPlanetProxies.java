package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.space.SpaceSnapshot;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
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
    private static final org.joml.Vector3d PROJECTION_CENTER=new org.joml.Vector3d();
    private static final org.joml.Vector3d LIVE_BODY_CENTER=new org.joml.Vector3d();
    private static double distanceSquared(Vec3 camera,double x,double y,double z) {
        double dx=x-camera.x(),dy=y-camera.y(),dz=z-camera.z();
        return dx*dx+dy*dy+dz*dz;
    }
    private static void projectionCenter(Body body,SubLevelContainer container,float partialTick,
                                         org.joml.Vector3d destination) {
        boolean live=PhysicalPlanetRenderer.liveBodyCenter(body.id,container,partialTick,LIVE_BODY_CENTER);
        PlanetRenderBindings.setProjectionCenter(destination,body.x,body.y,body.z,
                live?LIVE_BODY_CENTER:null);
    }
    /** One static quad per proxy atlas cell, so a body's {@code style} picks a buffer rather than a build. */
    private static final VertexBuffer[] BUFFERS=new VertexBuffer[CelestialBody.MAX_VISUAL_STYLES];
    /**
     * SDB and per-body HD maps are equirectangular, so their far proxy must remain a sphere rather than
     * a flat UV quad.
     */
    private static VertexBuffer SPHERE_BUFFER;
    /**
     * What each body's projection last actually bound, for the on-demand identity check. At most one entry per
     * snapshot body (pruned on every snapshot); written only when the selection changes, which is also the
     * only time it logs.
     */
    private static final java.util.Map<String,PlanetProjectionTexture.Rendered> RENDERED=new java.util.HashMap<>();
    /** Per-body HD source map, resolved once per id rather than per frame; pruned with {@link #RENDERED}. */
    private static final java.util.Map<String,net.minecraft.resources.ResourceLocation> SOURCE_MAPS=new java.util.HashMap<>();
    /**
     * Report pass of each body's last far draw, pruned with {@link #RENDERED}. Counted per AFTER_LEVEL stage before
     * any early return, unlike {@link #frame}, so a pass that draws nothing still ages every record.
     */
    private static final java.util.Map<String,Long> DRAWN_AT=new java.util.HashMap<>();
    private static long reportPass;
    private static net.minecraft.resources.ResourceLocation sourceMap(String id) {
        return SOURCE_MAPS.computeIfAbsent(id,key->PlanetProjectionTexture.forProjection(key,true));
    }
    /** Downsampled per-body far maps; the near HD maps stay owned by {@link PhysicalPlanetRenderer}. */
    private static final PlanetFarProjectionCache FAR_MAPS=new PlanetFarProjectionCache(PlanetFarProjectionCache.MAX_TEXTURES);
    private static long frame;
    private static final Matrix4f MODEL=new Matrix4f();
    private static boolean ready;
    private OrbitPlanetProxies() {}
    public static void apply(SpaceSnapshot snapshot) {
        if(snapshot.revision()<revision)return;
        boolean report=snapshot.revision()!=revision || snapshot.bodies().size()!=bodies.length;
        var candidate=new Body[snapshot.bodies().size()];
        for(int i=0;i<candidate.length;i++)candidate[i]=new Body(snapshot.bodies().get(i));
        bodies=candidate;revision=snapshot.revision();
        var current=new java.util.HashSet<String>();
        for(Body body:candidate) current.add(body.id);
        RENDERED.keySet().retainAll(current);
        SOURCE_MAPS.keySet().retainAll(current);
        DRAWN_AT.keySet().retainAll(current);
        if(projectionsEnabled()) {
            var slots=new java.util.ArrayList<Integer>();
            for(Body body:candidate) {
                int slot=net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity.slotFromPlanetKey(body.id);
                if(slot>0) slots.add(slot);
            }
            if(!slots.isEmpty())
                DragonBallPlanetClient.prewarmProjectionTextures(slots.stream().mapToInt(Integer::intValue).toArray());
        }
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
    /** Whether this exact id currently has an enabled far proxy at the given camera position. */
    static boolean wouldDraw(String id,Vec3 camera,float partialTick) {
        if(!projectionsEnabled() || !inProjectionDimension()) return false;
        var level=Minecraft.getInstance().level;
        var container=level==null?null:SubLevelContainer.getContainer(level);
        for(Body body:bodies) if(body.id.equals(id)) {
            projectionCenter(body,container,partialTick,PROJECTION_CENTER);
            double d2=distanceSquared(camera,PROJECTION_CENTER.x,PROJECTION_CENTER.y,PROJECTION_CENTER.z);
            return d2<=DRAW_REACH*DRAW_REACH && d2>PHYSICAL_HANDOFF_DISTANCE_SQR;
        }
        return false;
    }
    private static boolean projectionsEnabled() {
        return SpacePlanetMode.byName(XenoClientConfig.spacePlanetMode).drawsProxies()
                && XenoClientConfig.spacePlanetProjections;
    }
    private static boolean inProjectionDimension() {
        var level=Minecraft.getInstance().level;
        if(level==null) return false;
        return SpacePlanetMode.byName(XenoClientConfig.spacePlanetMode)
                .drawsProxiesIn(level.dimension().location().toString());
    }
    /** The centre a far projection of this id draws at (live body pose when replicated), or false if absent. */
    static boolean drawCenter(String id,float partialTick,org.joml.Vector3d destination) {
        var level=Minecraft.getInstance().level;
        var container=level==null?null:SubLevelContainer.getContainer(level);
        for(Body body:bodies) if(body.id.equals(id)) {projectionCenter(body,container,partialTick,destination);return true;}
        return false;
    }
    /** Ids and style cells of the latest snapshot, for the on-demand projection identity check. */
    static java.util.List<PlanetProjectionTexture.Projected> projectedBodies() {
        var list=new java.util.ArrayList<PlanetProjectionTexture.Projected>(bodies.length);
        for(Body body:bodies) list.add(new PlanetProjectionTexture.Projected(body.id,body.style));
        return list;
    }
    /** Every body in the latest snapshot as (id, {x, y, z, radius}); the array is reused per call. */
    static void forEach(java.util.function.BiConsumer<String,double[]> action) {
        double[] row=new double[4];
        for(Body body:bodies) {row[0]=body.x;row[1]=body.y;row[2]=body.z;row[3]=body.radius;action.accept(body.id,row);}
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) {clearState();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {clearState();}
    private static void clearState() {bodies=EMPTY;revision=-1;RENDERED.clear();SOURCE_MAPS.clear();DRAWN_AT.clear();}
    /** Snapshot of the last bound selection per body, for the identity check. */
    static java.util.Map<String,PlanetProjectionTexture.Rendered> renderedSelections() {return java.util.Map.copyOf(RENDERED);}
    /** Report pass of each body's last far draw; compare with {@link #reportPass()}. */
    static java.util.Map<String,Long> drawnPasses() {return java.util.Map.copyOf(DRAWN_AT);}
    static long reportPass() {return reportPass;}
    private static void record(String id,PlanetProjectionTexture.Path path,net.minecraft.resources.ResourceLocation texture) {
        DRAWN_AT.put(id,reportPass);
        var previous=RENDERED.get(id);
        if(previous!=null && previous.path()==path && previous.texture().equals(texture)) return;
        RENDERED.put(id,new PlanetProjectionTexture.Rendered(path,texture));
        boolean own=path==PlanetProjectionTexture.Path.SDB_SLOT || PlanetProjectionTexture.belongsTo(id,texture);
        if(own) XenoPixelsMod.LOGGER.info("Far planet projection: body={}, path={}, texture={}",id,path,texture);
        else XenoPixelsMod.LOGGER.warn("Far planet projection: body={}, path={}, texture={} is not this body's own map",id,path,texture);
    }

    /**
     * This body's own far map, or null when every cached far map was already drawn this frame (the caller
     * then draws the legacy style strip for this body rather than evicting a visible one).
     */
    private static net.minecraft.resources.ResourceLocation farMap(String id) {
        var source=sourceMap(id);
        var textures=Minecraft.getInstance().getTextureManager();
        return FAR_MAPS.acquire(source,frame,
                key->textures.register(key,new PlanetFarProjectionTexture(source,PlanetSurfaceTexture.fallbackFor(source))),
                textures::release);
    }

    static void closeBuffers() {
        FAR_MAPS.clear(Minecraft.getInstance().getTextureManager()::release);
        for(int i=0;i<BUFFERS.length;i++) if(BUFFERS[i]!=null) {BUFFERS[i].close();BUFFERS[i]=null;}
        if(SPHERE_BUFFER!=null) {SPHERE_BUFFER.close();SPHERE_BUFFER=null;}
        ready=false;
    }
    // Far chart projections complement real Sable bodies. When a matching body is replicated in this level,
    // projectionCenter() uses its interpolated pose so the distant fallback cannot appear at a stale chart centre.
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL) reportPass++;
        if(!projectionsEnabled()) return;
        boolean pack=IrisShaderState.packInUse();
        var wanted=pack?RenderLevelStageEvent.Stage.AFTER_LEVEL:RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if(event.getStage()!=wanted || (pack&&IrisShaderState.renderingShadows())) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || !inProjectionDimension()) return;
        var camera=event.getCamera().getPosition();
        float partialTick=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var container=SubLevelContainer.getContainer(mc.level);
        boolean visible=false;
        for(var body:bodies) {
            projectionCenter(body,container,partialTick,PROJECTION_CENTER);
            double d2=distanceSquared(camera,PROJECTION_CENTER.x,PROJECTION_CENTER.y,PROJECTION_CENTER.z);
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
            frame++;
            net.minecraft.resources.ResourceLocation farMap;
            for(var body:bodies) {
                projectionCenter(body,container,partialTick,PROJECTION_CENTER);
                double d2=distanceSquared(camera,PROJECTION_CENTER.x,PROJECTION_CENTER.y,PROJECTION_CENTER.z);
                if(d2>DRAW_REACH*DRAW_REACH || d2<=PHYSICAL_HANDOFF_DISTANCE_SQR) continue;
                RenderSystem.setShaderColor(1,1,1,1);
                var stellar = StellarStageClient.appearance(body.id, mc.level.getGameTime());
                float radius=(float)(body.radius * stellar.radiusScale());
                var integrityStage = PlanetIntegrityClient.stage(body.id);
                if (integrityStage == net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules.Stage.DESTROYED) continue;
                var integrityTint = PlanetIntegritySurfaceTint.forStage(integrityStage);
                int sdbSlot=net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity
                        .slotFromPlanetKey(body.id);
                double burn=PlanetHakaiClient.progress(body.id,partialTick);
                boolean burning=burn>=0;
                boolean liveBody=PhysicalPlanetRenderer.hasLiveBodyBinding(body.id,container);
                if(!PlanetHakaiRenderPolicy.drawProjection(true,burning,liveBody)) continue;
                if(PlanetHakaiRenderPolicy.drawBurningProjection(true,burning,liveBody)) {
                    var surface=sdbSlot>0?DragonBallPlanetClient.textureForSlot(sdbSlot)
                            :PlanetSurfaceTexture.forBody(body.id);
                    PhysicalPlanetRenderer.ensureTexture(surface);
                    RenderSystem.setShaderTexture(0,surface);
                    record(body.id,PlanetProjectionTexture.Path.BURNING_MAP,surface);
                    var burnModel=new Matrix4f(event.getModelViewMatrix())
                            .translate((float)(PROJECTION_CENTER.x-camera.x),
                                    (float)(PROJECTION_CENTER.y-camera.y),(float)(PROJECTION_CENTER.z-camera.z))
                            .scale(radius);
                    PlanetHakaiClient.drawBurning(event,body.id,burnModel,new org.joml.Quaternionf(),burn);
                    continue;
                }
                RenderSystem.setShaderColor((float)stellar.red() * integrityTint.red(),
                        (float)stellar.green() * integrityTint.green(),
                        (float)stellar.blue() * integrityTint.blue(), 1.0f);
                MODEL.set(event.getModelViewMatrix()).translate((float)(PROJECTION_CENTER.x-camera.x),
                        (float)(PROJECTION_CENTER.y-camera.y),(float)(PROJECTION_CENTER.z-camera.z));
                MODEL.m00(radius).m01(0).m02(0).m10(0).m11(radius).m12(0).m20(0).m21(0).m22(radius);
                if(sdbSlot>0) {
                    var slotTexture=DragonBallPlanetClient.projectionTextureForSlot(sdbSlot);
                    RenderSystem.setShaderTexture(0,slotTexture);
                    record(body.id,PlanetProjectionTexture.Path.SDB_SLOT,slotTexture);
                    ensureSphereBuffer();
                    MODEL.set(event.getModelViewMatrix()).translate((float)(PROJECTION_CENTER.x-camera.x),
                            (float)(PROJECTION_CENTER.y-camera.y),(float)(PROJECTION_CENTER.z-camera.z)).scale(radius);
                    drawSphere(event,SphereMesh.cullBackFaces(d2,radius));
                    PlanetIntegrityCrackOverlay.draw(event, body.id, integrityStage, MODEL, SPHERE_BUFFER);
                } else if(XenoClientConfig.spacePlanetProjectionMaps && (farMap=farMap(body.id))!=null) {
                    RenderSystem.setShaderTexture(0,farMap);
                    record(body.id,PlanetProjectionTexture.Path.BODY_MAP,sourceMap(body.id));
                    ensureSphereBuffer();
                    MODEL.set(event.getModelViewMatrix()).translate((float)(PROJECTION_CENTER.x-camera.x),
                            (float)(PROJECTION_CENTER.y-camera.y),(float)(PROJECTION_CENTER.z-camera.z)).scale(radius);
                    drawSphere(event,SphereMesh.cullBackFaces(d2,radius));
                    PlanetIntegrityCrackOverlay.draw(event, body.id, integrityStage, MODEL, SPHERE_BUFFER);
                } else {
                    RenderSystem.setShaderTexture(0,PlanetSkyTexture.LOCATION);
                    record(body.id,PlanetProjectionTexture.Path.STYLE_STRIP,PlanetSkyTexture.LOCATION);
                    BUFFERS[body.style].bind();
                    BUFFERS[body.style].drawWithShader(MODEL,event.getProjectionMatrix(),GameRenderer.getPositionTexColorShader());
                }
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
    /**
     * Draws the closed far sphere. Projections run with depth writes off, so from OUTSIDE without culling the far
     * hemisphere's quads overwrite the near ones in emission order and the map reads as a patchwork; the quads
     * are CCW from outside ({@link SphereMesh}), so back-face culling keeps the front. From INSIDE (a datapack
     * radius larger than the 512-block handoff) every view ray meets the surface once, so nothing overdraws and
     * the visible faces are the back faces: culling stays off there, as before this fix.
     */
    private static void drawSphere(RenderLevelStageEvent event,boolean cullBackFaces) {
        if(cullBackFaces) RenderSystem.enableCull();
        try {
            SPHERE_BUFFER.bind();
            SPHERE_BUFFER.drawWithShader(MODEL,event.getProjectionMatrix(),GameRenderer.getPositionTexColorShader());
        } finally {
            // The flat style-strip quad that may follow is two-sided by design.
            RenderSystem.disableCull();
        }
    }

    /** Pure geometry of the far projection sphere, shared by the buffer build and its winding test. */
    static final class SphereMesh {
        static final int LONGITUDE_STEPS=24, LATITUDE_STEPS=12;
        private SphereMesh() {}

        /** Back-face culling is right only when the camera is outside the sphere (centre distance > radius). */
        static boolean cullBackFaces(double centreDistanceSquared,double radius) {
            return centreDistanceSquared>radius*radius;
        }

        /** Grid corners of quad (lon, lat) in emission order. */
        static int[][] quadCorners(int lon,int lat) {
            return new int[][]{{lon,lat},{lon,lat+1},{lon+1,lat+1},{lon+1,lat}};
        }

        /** Unit-sphere position of grid corner (lon, lat). */
        static float[] position(int lon,int lat) {
            double latitude=Math.PI/2.0-lat*Math.PI/LATITUDE_STEPS;
            double longitude=lon*Math.PI*2.0/LONGITUDE_STEPS-Math.PI;
            return new float[]{(float)(Math.cos(latitude)*Math.sin(longitude)),(float)Math.sin(latitude),
                    (float)(Math.cos(latitude)*Math.cos(longitude))};
        }
    }

    private static void ensureSphereBuffer() {
        if(SPHERE_BUFFER!=null) return;
        final int longitudeSteps=SphereMesh.LONGITUDE_STEPS,latitudeSteps=SphereMesh.LATITUDE_STEPS;
        final double lx=-0.45,ly=0.6,lz=0.66,lightLength=Math.sqrt(lx*lx+ly*ly+lz*lz);
        var builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(int lat=0;lat<latitudeSteps;lat++) for(int lon=0;lon<longitudeSteps;lon++) {
            for(int[] corner:SphereMesh.quadCorners(lon,lat)) {
                float[] p=SphereMesh.position(corner[0],corner[1]);
                float x=p[0],y=p[1],z=p[2];
                double light=0.45+0.55*Math.max(0,(x*lx+y*ly+z*lz)/lightLength);
                int shade=(int)Math.round(255*Math.min(1,light));
                builder.addVertex(x,y,z).setUv((float)corner[0]/longitudeSteps,
                        (float)corner[1]/latitudeSteps).setColor(shade,shade,shade,255);
            }
        }
        var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {buffer.bind();buffer.upload(builder.buildOrThrow());SPHERE_BUFFER=buffer;}
        catch(RuntimeException|Error failure) {buffer.close();throw failure;}
        finally {VertexBuffer.unbind();}
    }
}
