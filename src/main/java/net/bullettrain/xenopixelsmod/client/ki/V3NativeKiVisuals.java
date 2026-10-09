package net.bullettrain.xenopixelsmod.client.ki;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.ki.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3KiVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Unticked, unregistered render proxies: physics and damage remain exclusively server-owned. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class V3NativeKiVisuals {
    private static final int MAX_VISUALS = 256;
    private static final Map<UUID, Visual> VISUALS = new LinkedHashMap<>();
    private static ClientLevel level;
    private static long clock;
    private record Visual(Entity entity, UUID owner, long born, long expires) {}
    private V3NativeKiVisuals() {}

    private static final class Wave extends KiWaveEntity {
        private final float length;
        Wave(ClientLevel world, float length) { super(MainEntities.KI_WAVE.get(), world); this.length = length; }
        @Override public float getBeamLength() { return length; }
        @Override public float getFixedYaw() { return getYRot(); }
        @Override public float getFixedPitch() { return getXRot(); }
    }
    private static final class Laser extends KiLaserEntity {
        private final float length;
        Laser(ClientLevel world, float length) { super(MainEntities.KI_LASER.get(), world); this.length = length; }
        @Override public float getBeamLength() { return length; }
        @Override public float getFixedYaw() { return getYRot(); }
        @Override public float getFixedPitch() { return getXRot(); }
    }

    public static void apply(CombatV3KiVisualPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) { VISUALS.clear(); level = mc.level; clock = 0; }
        if (level == null || !level.dimension().location().equals(p.dimension())) return;
        if (p.phase() == CombatV3KiVisualPacket.Phase.CLEAR_OWNER) {
            VISUALS.values().removeIf(v -> v.owner.equals(p.owner())); return;
        }
        if (p.phase() == CombatV3KiVisualPacket.Phase.REMOVE) { VISUALS.remove(p.id()); return; }
        if (XenoServerClientState.get().effekseerKiAttacks) return;
        if (VISUALS.size() >= MAX_VISUALS && !VISUALS.containsKey(p.id()))
            VISUALS.remove(VISUALS.keySet().iterator().next());
        Visual old = VISUALS.get(p.id());
        long born = old == null ? clock : old.born;
        Entity proxy;
        if (p.phase() == CombatV3KiVisualPacket.Phase.IMPACT) {
            KiExplosionVisualEntity explosion = new KiExplosionVisualEntity(MainEntities.KI_EXPLOSION_VISUAL.get(), level);
            explosion.setupExplosion(p.core(), p.edge(), 0xFFFFFF, p.size());
            proxy = explosion;
        } else {
            AbstractKiProjectile ki = switch (p.kind()) {
                case WAVE -> new Wave(level, p.length());
                case LASER, BEAM -> new Laser(level, p.length());
                case DISK -> new KiDiskEntity(MainEntities.KI_DISC.get(), level);
                default -> new KiBlastEntity(MainEntities.KI_BLAST.get(), level);
            };
            ki.setColors(p.core(), p.edge(), 0xFFFFFF);
            ki.setKiRenderType(p.renderType());
            ki.setSize(p.size()); ki.setMaxLife(72000);
            ki.setFiring(p.phase() == CombatV3KiVisualPacket.Phase.FLIGHT);
            if (ki instanceof KiWaveEntity wave) { wave.setCastWave(1); wave.setCastSize(p.size() * 0.5f); }
            if (ki instanceof KiBlastEntity blast) blast.setCastTime(1);
            if (ki instanceof KiDiskEntity disk) disk.setCastTime(1);
            proxy = ki;
        }
        proxy.setPos(p.position());
        Vec3 d = p.forward().lengthSqr() > 1e-9 ? p.forward().normalize() : new Vec3(0, 0, 1);
        proxy.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
        proxy.setXRot((float)-Math.toDegrees(Math.asin(Math.clamp(d.y, -1, 1))));
        proxy.yRotO = proxy.getYRot(); proxy.xRotO = proxy.getXRot();
        // A charge can precede release by MAX_TICK plus the bounded held pause; cancellation removes it immediately.
        int life = switch (p.phase()) { case CHARGE -> 1400; case IMPACT -> 25; default -> 3; };
        VISUALS.put(p.id(), new Visual(proxy, p.owner(), born, clock + life));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.level == null || XenoServerClientState.get().effekseerKiAttacks) {
            VISUALS.clear(); level = mc.level; clock = 0; return;
        }
        if (!mc.isPaused()) { clock++; VISUALS.values().removeIf(v -> v.expires <= clock); }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != level || mc.player == null
                || XenoServerClientState.get().effekseerKiAttacks) return;
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Visual visual : VISUALS.values()) {
            Entity proxy = visual.entity;
            float length = proxy instanceof KiWaveEntity wave ? wave.getBeamLength()
                    : proxy instanceof KiLaserEntity laser ? laser.getBeamLength() : 0;
            Vec3 tip = proxy.position().add(Vec3.directionFromRotation(proxy.getXRot(), proxy.getYRot()).scale(length));
            if (proxy.position().distanceToSqr(camera) > 160 * 160 && tip.distanceToSqr(camera) > 160 * 160) continue;
            proxy.tickCount = (int)(clock - visual.born) + 1;
            // Native renderers enqueue their own shader work. DMZ applies the model-view at AFTER_LEVEL.
            PoseStack pose = new PoseStack();
            Vec3 at = proxy.position().subtract(camera);
            if (proxy instanceof KiBlastEntity) at = at.add(0, -proxy.getBbHeight() * 0.5, 0);
            pose.translate(at.x, at.y, at.z);
            mc.getEntityRenderDispatcher().getRenderer(proxy).render(proxy, proxy.getYRot(), partial,
                    pose, mc.renderBuffers().bufferSource(), 15728880);
        }
    }
}
