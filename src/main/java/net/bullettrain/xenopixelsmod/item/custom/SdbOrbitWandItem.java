package net.bullettrain.xenopixelsmod.item.custom;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.item.ModsItems;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallOrbitWandMath;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetAssignments;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetDefinition;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetOrbitPass;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallModeGuard;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallSavedData;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Operator tool for selecting a physical SDB and persisting a new circular orbit through the player's position. */
@EventBusSubscriber(modid = "xenopixelsmod")
public final class SdbOrbitWandItem extends Item {
    private static final double SELECT_REACH = 512.0;
    private static final double PLANE_TOLERANCE = 32.0;
    private static final Map<UUID, Selection> SELECTED = new HashMap<>();

    private record Selection(MinecraftServer server, int slot, UUID bodyId) {}

    public SdbOrbitWandItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) interact(level, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide()) interact(level, context.getPlayer());
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void interact(Level level, Player player) {
        if (player == null) return;
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("Operator permission is required to edit SDB orbits."));
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !PhysicalEarth.HOST.equals(level.dimension().location())) {
            player.sendSystemMessage(Component.literal("Use the SDB orbit wand in the Voidsea."));
            return;
        }
        if (!DragonBallModeGuard.planetaryEnabled(serverLevel.getServer())) {
            player.sendSystemMessage(Component.literal("The SDB orbit wand is available only in planetary mode."));
            return;
        }
        Selection selected = SELECTED.get(player.getUUID());
        if (player.isShiftKeyDown() && selected != null && selected.server() == level.getServer()) {
            place(serverLevel, player, selected);
            return;
        }
        select(serverLevel, player);
    }

    private static void select(ServerLevel level, Player player) {
        DragonBallSavedData saved = DragonBallSavedData.get(level.getServer());
        if (!saved.isGameplayStateValid()) {
            player.sendSystemMessage(Component.literal("Super Dragon Ball data is unavailable: " + saved.invalidReason()));
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        var container = SubLevelContainer.getContainer(level);
        int bestSlot = 0;
        UUID bestId = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++) {
            UUID id = saved.planetBodyUuid(slot);
            if (id == null || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) continue;
            var center = body.logicalPose().position();
            double distance = DragonBallOrbitWandMath.raySphereDistance(
                    eye.x, eye.y, eye.z, look.x, look.y, look.z,
                    center.x(), center.y(), center.z(),
                    DragonBallPlanetAssignments.PROTOTYPE_BODY_RADIUS_BLOCKS, SELECT_REACH);
            if (distance >= 0.0 && distance < bestDistance) {
                bestDistance = distance;
                bestSlot = slot;
                bestId = id;
            }
        }
        if (bestId == null) {
            player.sendSystemMessage(Component.literal("Aim at a loaded physical Super Dragon Ball within 512 blocks."));
            return;
        }
        SELECTED.put(player.getUUID(), new Selection(level.getServer(), bestSlot, bestId));
        DragonBallSavedData.ManualOrbit manual = saved.manualOrbit(bestSlot);
        player.sendSystemMessage(Component.literal("Selected Super Dragon Ball " + bestSlot + (manual == null
                ? ". Sneak-right-click at the desired orbit position to save a new track."
                : ". It has a saved orbit override; sneak-right-click to move it again.")));
    }

    private static void place(ServerLevel level, Player player, Selection selected) {
        DragonBallSavedData saved = DragonBallSavedData.get(level.getServer());
        if (!saved.isGameplayStateValid()) {
            player.sendSystemMessage(Component.literal("Super Dragon Ball data is unavailable: " + saved.invalidReason()));
            return;
        }
        if (!selected.bodyId().equals(saved.planetBodyUuid(selected.slot()))) {
            SELECTED.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("That SDB incarnation changed; select the physical ball again."));
            return;
        }
        var state = SpaceChartService.state(level.getServer());
        DragonBallPlanetDefinition authored = state.dragonBallPlanets.get(selected.slot());
        if (authored == null) {
            player.sendSystemMessage(Component.literal("This SDB has no currently supported chart assignment."));
            return;
        }
        ChartRegistry chart = state.runtime.chart;
        int hostSlot = chart.slotOf(authored.hostChartId());
        if (hostSlot < 0) {
            player.sendSystemMessage(Component.literal("The SDB host is not available in the current chart."));
            return;
        }
        double hostY = chart.y(hostSlot);
        if (Math.abs(player.getY() - hostY) > PLANE_TOLERANCE) {
            player.sendSystemMessage(Component.literal("Move within " + (int) PLANE_TOLERANCE
                    + " blocks of the host's orbital plane before setting the orbit."));
            return;
        }
        double offsetX = player.getX() - chart.x(hostSlot);
        double offsetZ = player.getZ() - chart.z(hostSlot);
        double hostRadius = chart.body(hostSlot).radiusBlocks();
        double minimum = hostRadius + DragonBallPlanetAssignments.PROTOTYPE_BODY_RADIUS_BLOCKS;
        if (!(Math.hypot(offsetX, offsetZ) > minimum)) {
            player.sendSystemMessage(Component.literal("The new orbit must clear the host's surface and SDB radius ("
                    + (int) Math.ceil(minimum) + "+ blocks from host center)."));
            return;
        }
        try {
            double assignedPeriod = chart.body(hostSlot).mu() > 0.0 ? 0.0 : authored.orbitPeriodSeconds();
            var placement = DragonBallOrbitWandMath.fromOffset(offsetX, offsetZ,
                    net.bullettrain.xenopixelsmod.space.SpaceOrbitClock.seconds(level.getServer()),
                    chart.body(hostSlot).mu(), assignedPeriod);
            DragonBallPlanetDefinition effective = DragonBallPlanetAssignments.effective(authored,
                    new DragonBallSavedData.ManualOrbit(placement.radiusBlocks(), placement.phaseRadians(),
                            placement.periodSeconds()), hostRadius,
                    DragonBallPlanetAssignments.PROTOTYPE_BODY_RADIUS_BLOCKS);
            if (effective == authored) {
                player.sendSystemMessage(Component.literal("The saved orbit would overlap its host; no change was made."));
                return;
            }
            saved.setManualOrbit(selected.slot(), placement.radiusBlocks(), placement.phaseRadians(),
                    placement.periodSeconds());
            DragonBallPlanetOrbitPass.refreshFromSaved(level.getServer());
            player.sendSystemMessage(Component.literal("Saved SDB " + selected.slot() + " orbit: radius "
                    + Math.round(placement.radiusBlocks()) + " blocks, period "
                    + Math.round(placement.periodSeconds()) + " s. The physics controller will guide it onto this track."));
        } catch (IllegalArgumentException invalid) {
            player.sendSystemMessage(Component.literal("Orbit was not saved: " + invalid.getMessage()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SELECTED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SELECTED.entrySet().removeIf(entry -> entry.getValue().server() == event.getServer());
    }
}
