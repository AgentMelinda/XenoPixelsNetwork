package net.bullettrain.xenopixelsmod.compat.cosmonautics;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Cosmonautics 26.08.307 {@code SubLevelTemplate} saves section <em>indexes</em>
 * from the source dimension and writes them into the dest chunk array. Space
 * dimensions have more than overworld's 24 sections, so load does
 * {@code sections[126]} and crashes. Sitting players are also missed because
 * {@code getEntities} uses a tight world AABB and {@code TeleportEntity} treats
 * passengers as an afterthought.
 */
public final class CosmoWarpSections {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> DISCARD = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Object> MOVE_INFO = new ThreadLocal<>();
    private static final LevelChunkSection[] DISCARD_SLOT = new LevelChunkSection[1];

    private static final Method OLD_DIMENSION;
    private static final Method NEW_DIMENSION;

    static {
        Method oldDim = null;
        Method newDim = null;
        try {
            Class<?> type = Class.forName("dev.egg.registries.BlockEntityRegistry$MoveInfo");
            oldDim = type.getMethod("oldDimension");
            newDim = type.getMethod("newDimension");
        } catch (Throwable ignored) {
        }
        OLD_DIMENSION = oldDim;
        NEW_DIMENSION = newDim;
    }

    private CosmoWarpSections() {
    }

    public static void beginLoad(Object moveInfo) {
        MOVE_INFO.set(moveInfo);
        DISCARD.set(Boolean.FALSE);
    }

    public static void endLoad() {
        MOVE_INFO.remove();
        DISCARD.remove();
    }

    public static int remapSectionIndex(String key) {
        int srcIndex = Integer.parseInt(key);
        Object move = MOVE_INFO.get();
        if (move == null || OLD_DIMENSION == null) {
            return srcIndex;
        }
        try {
            ServerLevel src = (ServerLevel) OLD_DIMENSION.invoke(move);
            ServerLevel dest = (ServerLevel) NEW_DIMENSION.invoke(move);
            if (src == null || dest == null) {
                return clamp(srcIndex, dest);
            }
            int sectionY = src.getSectionYFromSectionIndex(srcIndex);
            int destIndex = dest.getSectionIndexFromSectionY(sectionY);
            if (destIndex < 0 || destIndex >= dest.getSectionsCount()) {
                DISCARD.set(Boolean.TRUE);
                return 0;
            }
            DISCARD.set(Boolean.FALSE);
            return destIndex;
        } catch (Throwable t) {
            LOGGER.warn("Cosmonautics warp section remap failed for key {}", key, t);
            DISCARD.set(Boolean.TRUE);
            return 0;
        }
    }

    public static LevelChunkSection[] sectionsOrDiscard(LevelChunk chunk) {
        if (Boolean.TRUE.equals(DISCARD.get())) {
            return DISCARD_SLOT;
        }
        return chunk.getSections();
    }

    public static List<Entity> collectWarpEntities(ServerLevel level, Entity except, AABB box) {
        List<Entity> found = new ArrayList<>(level.getEntities(except, box));
        AABB wide = box.inflate(16.0);
        SubLevel ship = null;
        try {
            ship = Sable.HELPER.getContaining(level, box.getCenter());
            if (ship == null) {
                for (SubLevel other : Sable.HELPER.getAllIntersecting(level,
                        new dev.ryanhcode.sable.companion.math.BoundingBox3d(wide))) {
                    ship = other;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        if (level.getServer() == null) {
            return found;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            if (!onShip(player, ship, wide)) {
                continue;
            }
            addUnique(found, player);
            Entity vehicle = player.getRootVehicle();
            if (vehicle != null && vehicle != player) {
                addUnique(found, vehicle);
            }
        }
        return found;
    }

    public static void logLoadFailure(Throwable error) {
        LOGGER.error("Cosmonautics SubLevelTemplate.load failed; warp continues without that hull copy", error);
    }

    private static boolean onShip(ServerPlayer player, SubLevel ship, AABB wide) {
        if (wide.contains(player.position())) {
            return true;
        }
        if (ship == null) {
            return false;
        }
        try {
            if (Sable.HELPER.getContaining(player) == ship) {
                return true;
            }
            if (Sable.HELPER.getTrackingSubLevel(player) == ship) {
                return true;
            }
            Entity vehicle = player.getVehicle();
            if (vehicle != null && Sable.HELPER.getContaining(vehicle) == ship) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void addUnique(List<Entity> list, Entity entity) {
        if (entity != null && !list.contains(entity)) {
            list.add(entity);
        }
    }

    private static int clamp(int srcIndex, ServerLevel dest) {
        if (dest == null) {
            return srcIndex;
        }
        if (srcIndex < 0 || srcIndex >= dest.getSectionsCount()) {
            DISCARD.set(Boolean.TRUE);
            return 0;
        }
        DISCARD.set(Boolean.FALSE);
        return srcIndex;
    }
}
