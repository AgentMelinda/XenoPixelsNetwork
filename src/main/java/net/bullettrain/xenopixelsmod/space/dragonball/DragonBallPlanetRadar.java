package net.bullettrain.xenopixelsmod.space.dragonball;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** Server-side current-position resolver for the planetary scanner path. */
public final class DragonBallPlanetRadar {
    private DragonBallPlanetRadar() {}

    /**
     * Resolves one slot using the explicitly selected mode. In planetary mode, the saved body UUID is authoritative:
     * a missing/unloaded/removed body yields an unavailable result and never falls back to the legacy scatter point.
 * The global mode guard must also allow planetary gameplay; no fallback to legacy scatter coordinates is permitted.
     */
    public static DragonBallRadarTargetResolver.Resolution resolve(
            MinecraftServer server, int slot, DragonBallRadarTargetResolver.Mode mode,
            DragonBallRadarTargetResolver.Position legacyPosition) {
        if (slot < 1 || slot > CosmicDragonBalls.COUNT)
            throw new IllegalArgumentException("SDB slot must be in [1, " + CosmicDragonBalls.COUNT + "]");
        if (server == null) {
            return DragonBallRadarTargetResolver.resolve(mode, null, legacyPosition, null);
        }
        if (mode != DragonBallRadarTargetResolver.Mode.PLANETARY_BODY)
            return DragonBallRadarTargetResolver.resolve(mode, null, legacyPosition, null);
        if (!DragonBallModeGuard.planetaryEnabled(server))
            return DragonBallRadarTargetResolver.resolve(mode, null, legacyPosition, null);
        DragonBallSavedData saved = DragonBallSavedData.get(server);
        if (!saved.isGameplayStateValid())
            return DragonBallRadarTargetResolver.resolve(mode, null, legacyPosition, null);
        UUID bodyId = saved.planetBodyUuid(slot);
        return DragonBallRadarTargetResolver.resolve(mode, bodyId, legacyPosition,
                id -> currentPosition(server, id));
    }

    /** Reads the live logical pose from the saved Sable incarnation, without consulting analytic chart coordinates. */
    private static DragonBallRadarTargetResolver.Position currentPosition(MinecraftServer server, UUID bodyId) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null) return null;
        var subLevel = SubLevelContainer.getContainer(level).getSubLevel(bodyId);
        if (!(subLevel instanceof ServerSubLevel body) || body.isRemoved()) return null;
        var position = body.logicalPose().position();
        return new DragonBallRadarTargetResolver.Position(level.dimension().location().toString(),
                position.x(), position.y(), position.z());
    }
}
