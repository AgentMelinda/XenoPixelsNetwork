package net.bullettrain.xenopixelsmod.plot;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Enforces {@link PlotFlags} on the blocks and entities inside a claimed plot.
 *
 * <p><b>{@link PlotFlags} is authoritative for the five behaviours it names.</b> YAWP is the
 * block-protection backstop, not a second opinion: a flag is never mirrored into a YAWP flag, so
 * the two can never disagree about whether an action is permitted. With YAWP present its own
 * protection applies on top and can only be more restrictive, never less.</p>
 *
 * <p>Every path funnels through {@link #allowed(PlotArea, UUID, int)}, which is a pure predicate:
 * no plot or no actor allows the action, the owner always allows it, and otherwise the flag
 * decides. Keeping the decision in one testable method is what makes the event plumbing below
 * thin and the flag semantics verifiable without a running game.</p>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlotProtection {

    /** How often the entry check runs, in ticks. Entering is not a per-tick decision. */
    private static final int ENTRY_CHECK_INTERVAL = 10;

    private PlotProtection() {
    }

    /**
     * The one permission decision. A missing plot or actor is allowed so an unclaimed area and a
     * non-player source (a piston, a falling block) are never silently blocked.
     */
    public static boolean allowed(@Nullable PlotArea plot, @Nullable UUID actor, int flag) {
        if (plot == null || actor == null) {
            return true;
        }
        if (plot.ownedBy(actor)) {
            return true;
        }
        return PlotFlags.has(plot.flags(), flag);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && deny(level, event.getPos(), player, PlotFlags.ALLOW_BUILD, "build")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (denies(level, event.getPos(), event.getEntity(), PlotFlags.ALLOW_BUILD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // A container is gated by its own flag so a plot can allow doors but not chests.
        boolean container = level.getBlockEntity(event.getPos()) instanceof Container;
        int flag = container ? PlotFlags.ALLOW_CONTAINERS : PlotFlags.ALLOW_INTERACT;
        if (denies(level, event.getPos(), event.getEntity(), flag)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof Player victim) || victim.getUUID().equals(attacker.getUUID())) {
            return;
        }
        if (denies(attacker.serverLevel(), victim.blockPosition(), attacker, PlotFlags.ALLOW_PVP)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % ENTRY_CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PlotArea plot = plotAt(level, player.blockPosition());
        if (allowed(plot, player.getUUID(), PlotFlags.ALLOW_ENTRY)) {
            return;
        }
        pushOut(player, level, plot);
        player.displayClientMessage(Component.literal("§cThis plot is private."), true);
    }

    /**
     * @return true when the action is denied, reporting the denial to the player. Combines the
     *         decision with the message so no caller can cancel without explaining why.
     */
    private static boolean deny(ServerLevel level, BlockPos pos, Player player, int flag, String label) {
        if (!denies(level, pos, player, flag)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.tickCount % 20 == 0) {
            serverPlayer.displayClientMessage(
                    Component.literal("§cYou cannot " + label + " here."), true);
        }
        return true;
    }

    private static boolean denies(ServerLevel level, BlockPos pos, Player player, int flag) {
        return !allowed(plotAt(level, pos), player.getUUID(), flag);
    }

    @Nullable
    private static PlotArea plotAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        ResourceLocation dimension = level.dimension().location();
        return PlotManager.get(level.getServer()).at(dimension, pos.getX(), pos.getZ());
    }

    /**
     * Moves a non-owner out of a private plot along the shortest axis, so denying entry does not
     * strand the player at the far side of the plot.
     */
    private static void pushOut(ServerPlayer player, Level level, PlotArea plot) {
        double x = player.getX();
        double z = player.getZ();
        double toMinX = Math.abs(x - plot.minX());
        double toMaxX = Math.abs(x - (plot.maxX() + 1.0D));
        double toMinZ = Math.abs(z - plot.minZ());
        double toMaxZ = Math.abs(z - (plot.maxZ() + 1.0D));
        double best = Math.min(Math.min(toMinX, toMaxX), Math.min(toMinZ, toMaxZ));
        double nx = x;
        double nz = z;
        if (best == toMinX) {
            nx = plot.minX() - 0.5D;
        } else if (best == toMaxX) {
            nx = plot.maxX() + 1.5D;
        } else if (best == toMinZ) {
            nz = plot.minZ() - 0.5D;
        } else {
            nz = plot.maxZ() + 1.5D;
        }
        player.teleportTo(player.serverLevel(), nx, player.getY(), nz,
                player.getYRot(), player.getXRot());
    }
}