package net.bullettrain.xenopixelsmod.combat.v2;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.combat.v3.V3Motion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Completed finishers lower their caster toward safe ground without pinning controls. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class UltimateFinisherDescent {
    private record Return(ServerPlayer player, net.minecraft.world.level.Level level, double height,
                          long until, boolean flyActive, int flyMode, boolean abilityFlight) {}
    private static final Map<UUID, Return> RETURNS = new HashMap<>();
    private UltimateFinisherDescent() {}

    static double step(double current, double destination) {
        if (!Double.isFinite(current) || !Double.isFinite(destination)) return 0;
        return current <= destination ? 0 : -Math.min(current - destination, .35);
    }

    static void start(ServerPlayer player, double height, long now) {
        if (player == null || player.onGround()) return;
        double surface = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.blockPosition().getX(), player.blockPosition().getZ());
        // A surface above the caster can be a cave/overhang; collision checks below stop
        // descent on its actual floor instead of steering up toward that heightmap surface.
        height = surface < player.getY() ? surface : player.getY() - 64;
        if (step(player.getY(), height) == 0) return;
        var stats = V2Support.stats(player);
        var fly = stats == null ? null : stats.getSkills().getSkill("fly");
        RETURNS.put(player.getUUID(), new Return(player, player.level(), height, now + 200,
                fly != null && fly.isActive(), stats == null ? -1 : stats.getStatus().getFlightMode(),
                player.getAbilities().flying));
        Bt3CombatEvents.grantFallGrace(player, 220);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        for (var iterator = RETURNS.values().iterator(); iterator.hasNext();) {
            Return route = iterator.next(); ServerPlayer player = route.player();
            var stats = V2Support.stats(player);
            var fly = stats == null ? null : stats.getSkills().getSkill("fly");
            boolean flightChanged = (fly != null && fly.isActive()) != route.flyActive()
                    || (stats == null ? -1 : stats.getStatus().getFlightMode()) != route.flyMode()
                    || player.getAbilities().flying != route.abilityFlight();
            double dy = step(player.getY(), route.height());
            if (now >= route.until() || !player.isAlive() || player.isRemoved() || player.isSpectator()
                    || player.level() != route.level() || player.isPassenger() || player.onGround()
                    || dy == 0 || flightChanged || V3Motion.owns(player) || UltimateFinisher.owns(player)) {
                iterator.remove(); continue;
            }
            Vec3 movement = new Vec3(0, dy, 0);
            var box = player.getBoundingBox().expandTowards(movement);
            if (!player.level().getWorldBorder().isWithinBounds(box) || !player.level().noCollision(player, box)) {
                iterator.remove(); continue;
            }
            // Only Y is owned: preserve horizontal movement and lock-on facing during the return.
            player.connection.teleport(player.getX(), player.getY() + dy, player.getZ(),
                    player.getYRot(), player.getXRot());
            player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
            player.resetFallDistance();
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { RETURNS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) { RETURNS.remove(event.getOriginal().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { RETURNS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { RETURNS.clear(); }
}
