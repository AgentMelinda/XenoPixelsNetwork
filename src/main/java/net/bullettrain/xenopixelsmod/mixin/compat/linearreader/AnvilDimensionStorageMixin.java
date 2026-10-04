package net.bullettrain.xenopixelsmod.mixin.compat.linearreader;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.bullettrain.xenopixelsmod.compat.linearreader.LinearConversionPolicy;
import net.bullettrain.xenopixelsmod.compat.linearreader.LinearStorageState;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Applied after LinearReader's priority-1000 overwrites. Vanilla Anvil IO for disabled new regions. */
@Mixin(value = RegionFileStorage.class, priority = 900)
public abstract class AnvilDimensionStorageMixin {
    @Shadow @Final private Path folder;
    @Shadow @Final private boolean sync;
    @Shadow @Final private RegionStorageInfo info;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<RegionFile> regionCache;
    @Unique private IOException xeno$closeFailure;

    @Unique
    private synchronized boolean xeno$anvil(ChunkPos pos) {
        long key = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
        if (LinearStorageState.isLinearOpen(this, key)) return false;
        Path path = LinearConversionPolicy.regionPath(folder, pos.getRegionX(), pos.getRegionZ());
        if (LinearConversionPolicy.retainAnvil(path)) return true;
        if (LinearConversionPolicy.allowsConversion(path)) return false;
        // Existing linear data must retain the linear reader/writer even when conversion is off.
        return !Files.exists(folder.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".linear"));
    }

    @Unique
    private synchronized RegionFile xeno$region(ChunkPos pos) throws IOException {
        long key = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
        RegionFile cached = regionCache.getAndMoveToFirst(key);
        if (cached != null) return cached;
        if (regionCache.size() >= 256) regionCache.removeLast().close();
        FileUtil.createDirectoriesSafe(folder);
        Path path = LinearConversionPolicy.regionPath(folder, pos.getRegionX(), pos.getRegionZ());
        LinearConversionPolicy.openedAnvil(path);
        RegionFile region = new RegionFile(info, path, folder, sync);
        regionCache.putAndMoveToFirst(key, region);
        return region;
    }

    @Inject(method = "getRegionFile", at = @At("HEAD"), cancellable = true, require = 1)
    private void xeno$get(ChunkPos pos, CallbackInfoReturnable<RegionFile> cir) throws IOException {
        if (xeno$anvil(pos)) cir.setReturnValue(xeno$region(pos));
    }

    @Inject(method = "read", at = @At("HEAD"), cancellable = true, require = 1)
    private void xeno$read(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) throws IOException {
        if (!xeno$anvil(pos)) return;
        try (var input = xeno$region(pos).getChunkDataInputStream(pos)) {
            cir.setReturnValue(input == null ? null : NbtIo.read(input));
        }
    }

    @Inject(method = "write", at = @At("HEAD"), cancellable = true, require = 1)
    private void xeno$write(ChunkPos pos, CompoundTag tag, CallbackInfo ci) throws IOException {
        if (!xeno$anvil(pos)) return;
        RegionFile region = xeno$region(pos);
        if (tag == null) region.clear(pos);
        else try (var output = region.getChunkDataOutputStream(pos)) { NbtIo.write(tag, output); }
        ci.cancel();
    }

    @Inject(method = "scanChunk", at = @At("HEAD"), cancellable = true, require = 1)
    private void xeno$scan(ChunkPos pos, StreamTagVisitor visitor, CallbackInfo ci) throws IOException {
        if (!xeno$anvil(pos)) return;
        try (var input = xeno$region(pos).getChunkDataInputStream(pos)) {
            if (input != null) NbtIo.parse(input, visitor, NbtAccounter.unlimitedHeap());
        }
        ci.cancel();
    }

    @Inject(method = "flush", at = @At("HEAD"), require = 1)
    private synchronized void xeno$flush(CallbackInfo ci) throws IOException {
        for (RegionFile region : regionCache.values()) {
            // LinearReader flushes only its own linear cache.
            if (!region.getClass().getName().equals("com.bugfunbug.linearreader.linear.LinearBackedRegionFile")) region.flush();
        }
    }

    @Inject(method = "close", at = @At("HEAD"), require = 1)
    private synchronized void xeno$closeAnvil(CallbackInfo ci) {
        xeno$closeFailure = null;
        // LinearReader clears regionCache without closing Anvil handles. Finish them first,
        // then allow its normal linear close to run even if one Anvil close failed.
        for (RegionFile region : regionCache.values()) {
            if (region.getClass().getName().equals("com.bugfunbug.linearreader.linear.LinearBackedRegionFile")) continue;
            try { region.close(); }
            catch (IOException exception) {
                if (xeno$closeFailure == null) xeno$closeFailure = exception;
                else xeno$closeFailure.addSuppressed(exception);
            }
        }
    }

    @Inject(method = "close", at = @At("RETURN"), require = 1)
    private void xeno$closeResult(CallbackInfo ci) throws IOException {
        if (xeno$closeFailure != null) throw xeno$closeFailure;
    }
}
