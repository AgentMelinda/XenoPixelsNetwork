package net.bullettrain.xenopixelsmod.mixin.common;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;
import java.util.function.BooleanSupplier;

/**
 * Low-frequency diagnostic for the backlog observed in Spark beneath ChunkMap.processUnloads.
 * It only samples existing O(1) collection sizes and never changes chunk scheduling or unload behavior.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkUnloadBacklogProbeMixin {
    @Shadow @Final ServerLevel level;
    @Shadow @Final LongSet toDrop;
    @Shadow @Final private Queue<Runnable> unloadQueue;
    @Shadow @Final private it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Unique private long xenopixels$lastUnloadProbeTick = Long.MIN_VALUE;
    @Unique private long xenopixels$chunkSaveStartNanos;
    @Unique private long xenopixels$chunkSaveCount;
    @Unique private long xenopixels$chunkSaveTotalNanos;
    @Unique private long xenopixels$chunkSaveMaxNanos;
    @Unique private long xenopixels$chunkSaveOver5Millis;
    @Unique private long xenopixels$chunkSaveOver20Millis;
    @Unique private final long[] xenopixels$topSaveNanos = new long[3];
    @Unique private final int[] xenopixels$topSaveX = new int[3];
    @Unique private final int[] xenopixels$topSaveZ = new int[3];
    @Unique private final long[] xenopixels$topSaveTick = new long[3];

    /** ChunkMap.save synchronously serializes dirty chunk palettes before queuing their disk write. */
    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), require = 1)
    private void xenopixels$startChunkSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        xenopixels$chunkSaveStartNanos = XenoSpaceConfig.perfProbeEnabled() ? System.nanoTime() : 0L;
    }

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("RETURN"), require = 1)
    private void xenopixels$recordChunkSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        long started = xenopixels$chunkSaveStartNanos;
        xenopixels$chunkSaveStartNanos = 0L;
        if (started == 0L || !cir.getReturnValueZ()) return;

        long elapsed = Math.max(0L, System.nanoTime() - started);
        long gameTick = level.getGameTime();
        ChunkPos pos = chunk.getPos();
        xenopixels$chunkSaveCount++;
        xenopixels$chunkSaveTotalNanos += elapsed;
        if (elapsed >= 5_000_000L) xenopixels$chunkSaveOver5Millis++;
        if (elapsed >= 20_000_000L) xenopixels$chunkSaveOver20Millis++;
        if (elapsed > xenopixels$chunkSaveMaxNanos) xenopixels$chunkSaveMaxNanos = elapsed;

        for (int i = 0; i < xenopixels$topSaveNanos.length; i++) {
            if (elapsed <= xenopixels$topSaveNanos[i]) continue;
            for (int shift = xenopixels$topSaveNanos.length - 1; shift > i; shift--) {
                xenopixels$topSaveNanos[shift] = xenopixels$topSaveNanos[shift - 1];
                xenopixels$topSaveX[shift] = xenopixels$topSaveX[shift - 1];
                xenopixels$topSaveZ[shift] = xenopixels$topSaveZ[shift - 1];
                xenopixels$topSaveTick[shift] = xenopixels$topSaveTick[shift - 1];
            }
            xenopixels$topSaveNanos[i] = elapsed;
            xenopixels$topSaveX[i] = pos.x;
            xenopixels$topSaveZ[i] = pos.z;
            xenopixels$topSaveTick[i] = gameTick;
            break;
        }
    }

    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void xenopixels$reportUnloadBacklog(BooleanSupplier hasTime, CallbackInfo ci) {
        if (!XenoSpaceConfig.perfProbeEnabled()) return;

        long tick = level.getGameTime();
        int pendingDrop = toDrop.size();
        int queuedUnload = unloadQueue.size();
        long interval = pendingDrop > 2_000 || queuedUnload > 2_000 ? 100L : 1_200L;
        if (xenopixels$lastUnloadProbeTick != Long.MIN_VALUE
                && tick - xenopixels$lastUnloadProbeTick < interval) return;
        if (pendingDrop == 0 && queuedUnload == 0 && tick % 1_200L != 0L) return;

        xenopixels$lastUnloadProbeTick = tick;
        XenoPixelsMod.LOGGER.info(
                "Space chunk-unload backlog: dimension={}, toDrop={}, unloadQueue={}, updatingHolders={}, "
                        + "successfulChunkSerializations={}, chunkSerializationTotalMs={}, chunkSerializationMaxMs={}, "
                        + "chunkSerializationsOver5ms={}, chunkSerializationsOver20ms={}, topChunkSerializations={}",
                level.dimension().location(), pendingDrop, queuedUnload, updatingChunkMap.size(),
                xenopixels$chunkSaveCount, millis(xenopixels$chunkSaveTotalNanos), millis(xenopixels$chunkSaveMaxNanos),
                xenopixels$chunkSaveOver5Millis, xenopixels$chunkSaveOver20Millis, xenopixels$topSaves());
        xenopixels$clearSaveMetrics();
    }

    @Unique
    private String xenopixels$topSaves() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < xenopixels$topSaveNanos.length; i++) {
            if (xenopixels$topSaveNanos[i] == 0L) continue;
            if (!result.isEmpty()) result.append(';');
            result.append(xenopixels$topSaveX[i]).append(',').append(xenopixels$topSaveZ[i])
                    .append('@').append(millis(xenopixels$topSaveNanos[i])).append("ms/t")
                    .append(xenopixels$topSaveTick[i]);
        }
        return result.isEmpty() ? "none" : result.toString();
    }

    @Unique
    private void xenopixels$clearSaveMetrics() {
        xenopixels$chunkSaveCount = 0L;
        xenopixels$chunkSaveTotalNanos = 0L;
        xenopixels$chunkSaveMaxNanos = 0L;
        xenopixels$chunkSaveOver5Millis = 0L;
        xenopixels$chunkSaveOver20Millis = 0L;
        java.util.Arrays.fill(xenopixels$topSaveNanos, 0L);
    }

    @Unique
    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
