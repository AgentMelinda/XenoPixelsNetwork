package net.bullettrain.xenopixelsmod.compat.yawp;

import de.z0rdak.yawp.api.permission.FlagPermissions;
import de.z0rdak.yawp.core.flag.FlagState;
import de.z0rdak.yawp.core.flag.RegionFlag;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets YAWP regions veto DragonMineZ ki griefing.
 *
 * <p>DragonMineZ funnels every ki-caused block break through
 * {@code MainGameRules.canKiGrief}, gated by the {@code allowKiGriefingPlayers},
 * {@code allowKiGriefingMobs} and {@code allowKiGriefingMasterStructures} gamerules (plus its
 * own WorldGuard compat). This adds YAWP to that chain, so a protected region stops ki blasts
 * from chewing through builds without having to disable ki griefing server-wide.
 *
 * <p>YAWP 1.21.1 (0.6.3-beta3) cannot host third-party flags: {@code FlagRegister} accepts
 * custom flags but nothing in the command, storage or evaluation path reads them - regions,
 * {@code /wp flag add} and {@link FlagPermissions} are all keyed to the {@link RegionFlag}
 * enum. So instead of inventing {@code ki-griefing-*} flags that no region could ever set,
 * ki griefing is mapped onto real YAWP flags, chosen per server in
 * {@code config/xenopixelsmod-server.json}.
 *
 * <p>This class touches YAWP types directly and must only be loaded when YAWP is present -
 * that is enforced by the mixin gate in {@code ConditionalMixinPlugin}.
 */
public final class YawpKiGriefing {

    /** Flag names already reported as unknown, so a typo warns once instead of every tick. */
    private static final Set<String> WARNED = new HashSet<>();

    /** Cached resolution of the configured flag names; rebuilt when the config lists change. */
    private static List<String> cachedPlayerNames;
    private static List<RegionFlag> cachedPlayerFlags = List.of();
    private static List<String> cachedMobNames;
    private static List<RegionFlag> cachedMobFlags = List.of();

    private YawpKiGriefing() {}

    /**
     * @param source the entity credited with the griefing - for ki projectiles DragonMineZ
     *               passes the owner, so this is usually the firing player. May be null.
     * @return {@code false} if a YAWP region denies ki griefing at {@code pos}.
     */
    public static boolean canGrief(Level level, BlockPos pos, Entity source) {
        if (!XenoServerConfig.yawpKiGriefingEnabled) {
            return true;
        }
        // Client-side prediction has no region data; let the server be the authority.
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }

        Player player = source instanceof Player p ? p : null;

        // Our own per-region flags take precedence: they are the explicit operator intent,
        // whereas the YAWP flag mapping below is an approximation chosen server-wide.
        // Unset regions fall through, so existing setups behave exactly as before.
        KiRegionFlags.State explicit = explicitRegionState(serverLevel, pos, player);
        if (explicit == KiRegionFlags.State.DENIED) {
            return false;
        }
        if (explicit == KiRegionFlags.State.ALLOWED) {
            return true;
        }

        List<RegionFlag> flags = player != null ? playerFlags() : mobFlags();
        if (flags.isEmpty()) {
            return true;
        }

        for (RegionFlag flag : flags) {
            FlagState state = player != null
                    // Player check: region owners and members keep their permissions.
                    ? FlagPermissions.checkPlayerFlagPermission(flag, pos, serverLevel.dimension(), player)
                    : FlagPermissions.checkFlagPermission(pos, flag, serverLevel.dimension());
            if (state == FlagState.DENIED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Our own {@code ki-griefing-players} / {@code ki-griefing-mobs} flag for the region at
     * {@code pos}, or {@link KiRegionFlags.State#DEFAULT} when none is set.
     *
     * <p>Region resolution goes through {@link YawpRegionLookup}, which is reflective and
     * degrades to "no region" if YAWP's API shape is one we cannot read — in that case this
     * returns DEFAULT and the configured flag mapping still applies.
     */
    private static KiRegionFlags.State explicitRegionState(ServerLevel level, BlockPos pos,
                                                           Player player) {
        String region = YawpRegionLookup.regionNameAt(level, pos).orElse(null);
        if (region == null) {
            return KiRegionFlags.State.DEFAULT;
        }
        String dimension = level.dimension().location().toString();

        // Masters take precedence: protecting a master's surroundings should not require
        // denying that player ki griefing across the whole region. Only consulted when the
        // flag is actually set, so the entity scan is skipped on the common path.
        if (KiRegionFlags.get(dimension, region, KiRegionFlags.Target.MASTERS)
                != KiRegionFlags.State.DEFAULT && nearMaster(level, pos)) {
            return KiRegionFlags.get(dimension, region, KiRegionFlags.Target.MASTERS);
        }

        return KiRegionFlags.get(dimension, region,
                player != null ? KiRegionFlags.Target.PLAYERS : KiRegionFlags.Target.MOBS);
    }

    /**
     * Whether a DMZ master stands within {@code masterKiGriefRadius} of the blast.
     *
     * <p>DMZ gates master-structure griefing behind its own gamerule, but exposes no "is this
     * block part of a master's site" query. Proximity to the master entity is the available
     * approximation, and it matches how a player perceives the protected area.
     */
    private static boolean nearMaster(ServerLevel level, BlockPos pos) {
        double radius = Math.max(0.0, XenoServerConfig.masterKiGriefRadius);
        if (radius <= 0.0) return false;
        try {
            AABB box = new AABB(pos).inflate(radius);
            for (Entity entity : level.getEntities((Entity) null, box,
                    e -> DmzMasterProtection.isDmzMaster(e))) {
                if (entity != null) return true;
            }
        } catch (Throwable ignored) {
            // Missing DMZ classes or a bad AABB must not break ki combat.
        }
        return false;
    }

    private static List<RegionFlag> playerFlags() {
        List<String> configured = XenoServerConfig.yawpPlayerKiFlags;
        if (configured != cachedPlayerNames) {
            cachedPlayerFlags = resolve(configured);
            cachedPlayerNames = configured;
        }
        return cachedPlayerFlags;
    }

    private static List<RegionFlag> mobFlags() {
        List<String> configured = XenoServerConfig.yawpMobKiFlags;
        if (configured != cachedMobNames) {
            cachedMobFlags = resolve(configured);
            cachedMobNames = configured;
        }
        return cachedMobFlags;
    }

    /**
     * Resolves configured flag names to YAWP flags. Unknown names are dropped with a warning
     * rather than throwing, so a YAWP update that renames a flag degrades to "not checked"
     * instead of breaking ki combat.
     */
    private static List<RegionFlag> resolve(List<String> names) {
        List<RegionFlag> resolved = new ArrayList<>();
        if (names == null) {
            return resolved;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            RegionFlag flag = RegionFlag.fromString(name.trim()).orElse(null);
            if (flag == null) {
                if (WARNED.add(name)) {
                    XenoPixelsMod.LOGGER.warn(
                            "Unknown YAWP flag '{}' in ki-griefing config; ignoring it. Valid flags: {}",
                            name, RegionFlag.getFlagNames());
                }
                continue;
            }
            resolved.add(flag);
        }
        return resolved;
    }
}
