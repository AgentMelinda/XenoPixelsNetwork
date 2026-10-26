package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

/**
 * "Reconstructible planets" (S11 CL-061/CL-062, switch XenoSpaceConfig.planetEphemeral, default off). On-demand chart
 * planets are rebuilt from the chart, so Sable 2.0.3 SubLevelHoldingChunkMap never stores them:
 * <ul>
 * <li>S1: saveAll's per-member moveAndSaveSubLevel(ServerSubLevel, ChunkPos, List) is skipped for an ephemeral planet
 *     itself (the Sun's re-serialization was the 255 ms autosave tick).</li>
 * <li>S2': the UUID list handed to SubLevelSerializer.toData, which is persisted as loading_dependencies, drops ephemeral
 *     planets at all three call sites (processUnload, moveAndSaveSubLevel, moveToUnloaded), so a stored ship never
 *     names a planet that will not be stored (a missing dependency drops the ship at load, Qwen Q-045). Sable's in-memory
 *     dependency chain, force-load closure and grouping are untouched (Codex C-045).</li>
 * </ul>
 * <p>It also refreshes Sable's cached holding-chunk snapshot after the shared move/save helper writes its serialized
 * data. Sable 2.0.3's same-host-chunk path overwrites the storage slot but leaves the in-memory HoldingSubLevel stale;
 * saveAll later writes that stale cache back over the current slot. Reusing the SubLevelData argument avoids a second
 * full-planet serialization and keeps the final saved slot consistent with the body just serialized.
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap", remap = false)
public abstract class SablePlanetEphemeralSaveMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Object2ObjectMap<UUID, HoldingSubLevel> allHoldingSubLevels;

    @Redirect(method = "saveAll()V", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunkMap;moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;)V"),
            require = 1)
    private void xenopixels$skipEphemeralPlanetSave(@org.spongepowered.asm.mixin.injection.Coerce Object self, ServerSubLevel body, ChunkPos chunk, List<UUID> dependencies) {
        if (PhysicalEarth.isEphemeralPlanet(body)) {
            // saveAll calls loadedHoldingChunks.get(chunk).markKeepLoaded() right after this call (offsets 350-370) and
            // expects the holding chunk moveAndSaveSubLevel would have created: create it empty instead of storing the
            // planet (getOrLoadHoldingChunk(pos, true) makes and registers an empty chunk when none is stored, offsets
            // 232-261). Skipping without it crashed the S11 E1 autosave with a NullPointerException.
            getOrLoadHoldingChunk(chunk, true);
            return;
        }
        moveAndSaveSubLevel(body, chunk, dependencies);
    }

    @Shadow private void moveAndSaveSubLevel(ServerSubLevel body, ChunkPos chunk, List<UUID> dependencies) {}

    @Shadow private dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk getOrLoadHoldingChunk(ChunkPos chunk, boolean create) { return null; }

    @Inject(method = "moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Lnet/minecraft/world/level/ChunkPos;)Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;",
            at = @At("RETURN"), require = 1)
    private void xenopixels$refreshSavedHoldingSnapshot(ServerSubLevel body, SubLevelData data,
                                                        GlobalSavedSubLevelPointer oldPointer, ChunkPos hostChunk,
                                                        CallbackInfoReturnable<GlobalSavedSubLevelPointer> cir) {
        GlobalSavedSubLevelPointer savedPointer = cir.getReturnValue();
        // Sable also invokes this shared save helper for empty/null placeholders during shutdown.
        // The handler is only relevant to a real body whose cached edited-planet snapshot is stale.
        if (body == null || savedPointer == null || data == null) return;
        var userData = body.getUserDataTag();
        if (userData == null || !userData.contains(SmallPlanetPhysicalizer.TAG)
                || !userData.getCompound(SmallPlanetPhysicalizer.TAG).getBoolean("touched")) return;
        SubLevelHoldingChunk holdingChunk = getOrLoadHoldingChunk(hostChunk, false);
        if (holdingChunk == null) return;
        for (HoldingSubLevel cached : holdingChunk.getLoadedHoldingSubLevels()) {
            if (cached.data().uuid().equals(body.getUniqueId())) {
                HoldingSubLevel refreshed = new HoldingSubLevel(data, savedPointer);
                holdingChunk.acceptHoldingSubLevel(refreshed);
                // Sable also indexes this object in the map used by getHoldingSubLevel/saveAll.
                allHoldingSubLevels.put(body.getUniqueId(), refreshed);
                return;
            }
        }
    }

    @ModifyArg(method = {"processUnload", "moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;)V", "moveToUnloaded"},
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelSerializer;toData(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ljava/util/List;)Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;"),
            index = 1, require = 3)
    private List<UUID> xenopixels$dropEphemeralDependencies(List<UUID> dependencies) {
        return PhysicalEarth.withoutEphemeralPlanets(level, dependencies);
    }

    /**
     * Guard (Codex C-048): processUnload and moveToUnloaded serialize bodies without moveAndSaveSubLevel. An ephemeral
     * planet is force-loaded, so its ticket closure should keep it off those paths (C-049); if one is ever serialized
     * anyway, say so loudly instead of silently storing it.
     */
    @ModifyArg(method = {"processUnload", "moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;)V", "moveToUnloaded"},
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelSerializer;toData(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ljava/util/List;)Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;"),
            index = 0, require = 3)
    private ServerSubLevel xenopixels$guardEphemeralSerialization(ServerSubLevel body) {
        if (PhysicalEarth.isEphemeralPlanet(body))
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn("Ephemeral planet {} is being serialized by Sable outside saveAll; it will be stored",
                    body.getUniqueId());
        return body;
    }
}
