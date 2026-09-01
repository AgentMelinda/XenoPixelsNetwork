package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.dragonminez.common.stats.StatsData;
import com.mojang.authlib.GameProfile;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Supplies DMZ's player-typed HairRenderer with an exact snapshot of an NPC. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
final class NpcHairAnimator {
    record Input(AbstractClientPlayer player, StatsData stats, float physicsLod, float chargeProgress) {}

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        final SnapshotPlayer player;
        final StatsData stats;
        float charge;
        long lastUpdateNanos;
        long lastSeenGameTime;

        State(SnapshotPlayer player) {
            this.player = player;
            this.stats = new StatsData(player);
        }
    }

    private static final class SnapshotPlayer extends AbstractClientPlayer {
        SnapshotPlayer(ClientLevel level, GameProfile profile) {
            super(level, profile);
        }
    }

    private NpcHairAnimator() {}

    static Input input(LivingEntity owner) {
        if (owner == null || !(owner.level() instanceof ClientLevel level)) return null;
        State state = STATES.computeIfAbsent(owner.getUUID(), id ->
                new State(new SnapshotPlayer(level, new GameProfile(id, "NPC-" + owner.getId()))));
        sync(state.player, owner);

        Minecraft mc = Minecraft.getInstance();
        double distanceSqr = mc.getCameraEntity() == null ? 0.0 : owner.distanceToSqr(mc.getCameraEntity());
        float lod;
        if (distanceSqr <= 576.0) lod = 1.0f;
        else if (distanceSqr >= 2304.0) lod = 0.0f;
        else lod = (float) (1.0 - (distanceSqr - 576.0) / 1728.0);

        long now = System.nanoTime();
        float dt = state.lastUpdateNanos == 0L
                ? 1.0f : Mth.clamp((now - state.lastUpdateNanos) / 50_000_000.0f, 0.0f, 2.0f);
        state.lastUpdateNanos = now;
        boolean aura = NpcAuraClient.isActive(owner.getUUID());
        state.charge = Mth.clamp(state.charge + (aura ? dt * 0.25f : -dt * 0.15f), 0.0f, 1.0f);
        state.stats.getStatus().setPermanentAura(aura);
        state.stats.getStatus().setAuraActive(aura);
        state.lastSeenGameTime = owner.level().getGameTime();
        cleanup(state.lastSeenGameTime);
        return new Input(state.player, state.stats, lod, state.charge);
    }

    private static void sync(SnapshotPlayer to, LivingEntity from) {
        to.setPos(from.getX(), from.getY(), from.getZ());
        to.xo = from.xo;
        to.yo = from.yo;
        to.zo = from.zo;
        to.tickCount = from.tickCount;
        to.yHeadRotO = from.yHeadRotO;
        to.yHeadRot = from.yHeadRot;
        to.yBodyRotO = from.yBodyRotO;
        to.yBodyRot = from.yBodyRot;
        to.xRotO = from.xRotO;
        to.setXRot(from.getXRot());
        to.setYRot(from.getYRot());
        to.setSprinting(from.isSprinting());
        to.setSwimming(from.isSwimming());
    }

    private static void cleanup(long gameTime) {
        if ((gameTime & 127L) != 0L || STATES.size() < 16) return;
        STATES.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenGameTime > 200L);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }
}
