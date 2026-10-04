package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the Nearby NPCs wand screen: the list and its actions. Every action rechecks
 * operator permission, the level and the {@link NpcNearbyList#RANGE} radius at the moment it runs,
 * because the client's list can be stale.
 */
public final class XenoNpcNearbyService {
    public enum Action { TP_TO, BRING, RESET, RESET_ALL, FREEZE_TOGGLE, SPAWN }

    /** Marks NPCs this screen froze, so unfreezing never wakes an NPC frozen some other way. */
    static final String FROZEN_TAG = "xenopixels:nearby_frozen";
    private static final double RANGE_SQR = NpcNearbyList.RANGE * NpcNearbyList.RANGE;

    private XenoNpcNearbyService() {}

    public static List<NpcNearbyList.Entry> list(ServerPlayer player) {
        List<NpcNearbyList.Entry> entries = new ArrayList<>();
        for (XenoNpcEntity npc : inRange(player)) {
            if (entries.size() >= NpcNearbyList.MAX_ENTRIES) break;
            entries.add(new NpcNearbyList.Entry(npc.getId(), npc.npcData().displayName(), npc.role().id(),
                    Math.sqrt(player.distanceToSqr(npc)), npc.npcData().revision(), frozen(npc)));
        }
        return NpcNearbyList.filterSort(entries, "");
    }

    private static List<XenoNpcEntity> inRange(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(XenoNpcEntity.class,
                player.getBoundingBox().inflate(NpcNearbyList.RANGE),
                npc -> npc.isAlive() && player.distanceToSqr(npc) <= RANGE_SQR);
    }

    static boolean frozen(XenoNpcEntity npc) {
        return npc.getPersistentData().getBoolean(FROZEN_TAG);
    }

    /** Runs one action for {@code player}; returns false when refused (and tells the player why). */
    public static boolean perform(ServerPlayer player, Action action, int entityId, String roleId) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("You need operator permission to manage Xeno NPCs."));
            return false;
        }
        switch (action) {
            case RESET_ALL -> {
                inRange(player).forEach(XenoNpcNearbyService::reset);
                return true;
            }
            case FREEZE_TOGGLE -> {
                freezeToggle(inRange(player));
                return true;
            }
            case SPAWN -> {
                return spawn(player, XenoNpcRole.byId(roleId)) != null;
            }
            default -> {
            }
        }
        XenoNpcEntity npc = target(player, entityId);
        if (npc == null) {
            player.sendSystemMessage(Component.literal("That NPC is gone or out of range."));
            return false;
        }
        switch (action) {
            case TP_TO -> player.teleportTo(npc.getX(), npc.getY(), npc.getZ());
            case BRING -> {
                npc.teleportTo(player.getX(), player.getY(), player.getZ());
                npc.getNavigation().stop();
                // Its new spot is its home, or the leash would walk it straight back.
                npc.npcData().setHome(npc.getX(), npc.getY(), npc.getZ());
            }
            case RESET -> reset(npc);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static XenoNpcEntity target(ServerPlayer player, int entityId) {
        return player.serverLevel().getEntity(entityId) instanceof XenoNpcEntity npc && npc.isAlive()
                && player.distanceToSqr(npc) <= RANGE_SQR ? npc : null;
    }

    /** Home, full health, no target, no path: the NPC as placed. */
    static void reset(XenoNpcEntity npc) {
        XenoNpcData data = npc.npcData();
        if (data.hasHome()) npc.teleportTo(data.homeX(), data.homeY(), data.homeZ());
        npc.setTarget(null);
        npc.getNavigation().stop();
        npc.setHealth(npc.getMaxHealth());
    }

    /** Freezes every unfrozen NPC in range; when all are frozen, unfreezes the ones this froze. */
    static void freezeToggle(List<XenoNpcEntity> npcs) {
        boolean anyAwake = npcs.stream().anyMatch(npc -> !frozen(npc));
        for (XenoNpcEntity npc : npcs) {
            if (anyAwake && !frozen(npc)) {
                npc.setNoAi(true);
                npc.getNavigation().stop();
                npc.getPersistentData().putBoolean(FROZEN_TAG, true);
            } else if (!anyAwake && frozen(npc)) {
                npc.setNoAi(false);
                npc.getPersistentData().remove(FROZEN_TAG);
            }
        }
    }

    /** Places a new NPC of {@code role} two blocks ahead of the player (the wand's old air-click). */
    public static XenoNpcEntity spawn(ServerPlayer player, XenoNpcRole role) {
        var position = player.position().add(player.getLookAngle().scale(2.0));
        return spawnAt(player, role, new net.minecraft.world.phys.Vec3(position.x, player.getY(), position.z),
                player.getYRot());
    }

    /** Where an NPC placed on a clicked block face stands: the block space in front of that face. */
    public static net.minecraft.world.phys.Vec3 spawnPositionOn(net.minecraft.core.BlockPos clicked,
                                                               net.minecraft.core.Direction face) {
        net.minecraft.core.BlockPos at = clicked.relative(face);
        return new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
    }

    /** Yaw that turns a placed NPC toward the player who placed it, in (-180, 180]. */
    public static float facingPlacer(float playerYaw) {
        float yaw = (playerYaw + 180.0f) % 360.0f;
        if (yaw > 180.0f) yaw -= 360.0f;
        if (yaw <= -180.0f) yaw += 360.0f;
        return yaw;
    }

    /** Places an NPC of {@code role} with its feet at {@code feet} (op 2, like every wand action). */
    public static XenoNpcEntity spawnAt(ServerPlayer player, XenoNpcRole role, net.minecraft.world.phys.Vec3 feet,
                                        float yaw) {
        if (!player.hasPermissions(2) || !(player.level() instanceof ServerLevel server)) return null;
        if (player.position().distanceToSqr(feet) > RANGE_SQR) return null;
        XenoNpcEntity npc = ModEntities.xenoNpcType(role).create(server);
        if (npc == null) return null;
        npc.moveTo(feet.x, feet.y, feet.z, yaw, 0.0f);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.npcData().setOwner(player.getUUID());
        // Anchor it where it was placed, the way CustomNPCs and MyNPCs do: this is where it
        // respawns, and where the leash pulls it back to.
        npc.npcData().setHome(npc.getX(), npc.getY(), npc.getZ());
        // Named for the role it was placed as, so a row of them can be told apart.
        npc.npcData().setDisplayName(role.label());
        npc.refreshNameplate();
        // Give it a look before it enters the world, the way MyNPCs does.
        NpcCombatProfile fresh = NpcCombatProfile.read(npc);
        XenoNpcAppearanceDefaults.apply(fresh, role);
        fresh.write(npc);
        return server.addFreshEntity(npc) ? npc : null;
    }
}
