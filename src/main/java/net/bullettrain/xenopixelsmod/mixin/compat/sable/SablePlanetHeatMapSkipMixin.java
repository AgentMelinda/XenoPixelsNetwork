package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.HeatMapPropagationState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFragmentBudget;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPhysicalizer;
import net.bullettrain.xenopixelsmod.space.asteroid.SableHeatMapFractureAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/**
 * Sable 2.0.3 SubLevelHeatMapManager.tick runs up to sub_level_splitting_heatmap_steps flood-fill steps per tick
 * (isSolidAt -> getBlockState) to split bodies that came apart. A planet shell cannot be broken, so it never needs
 * splitting, yet during a batched build the half-built shell is flood-filled every tick: the S11 batch test sampled
 * ~620 ms of it inside >100 ms ticks, and it split off the 6,269-voxel orphan fragments. Skipped only for managed
 * planet bodies and bodies under a batched planet build. Switch: XenoSpaceConfig.planetSkipSplitHeatMap (off until
 * proven at runtime).
 *
 * <p>Once the skip engages for a manager it stays engaged ({@link #xenopixels$skipping}, a per-body latch: a frozen
 * solidCount would defeat split()'s intact-body shortcut if the skip ever lapsed). A planet reloaded from storage
 * is fed through ServerLevelPlot.load before its managed tag is set, so its manager may already hold split
 * bookkeeping when the skip first engages; that backlog can never drain without tick(), so it is cleared then
 * (Qwen CC-2c).
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager", remap = false)
public abstract class SablePlanetHeatMapSkipMixin implements SableHeatMapFractureAccess {
    @Shadow @Final private ServerSubLevel subLevel;
    @Shadow @Final private Long2IntOpenHashMap subLevelSplits;
    @Shadow @Final private ObjectList<BlockPos> floodfill;
    @Shadow @Final private ObjectList<BlockPos> removed;
    @Shadow @Final private ObjectList<BlockPos> newStarts;
    @Shadow @Final private IntArrayList splitIndexMap;
    @Shadow private HeatMapPropagationState state;
    @Shadow private boolean initialized;
    @Shadow private boolean splitComplete;
    @Shadow private int solidCount;

    @Unique private boolean xenopixels$skipping;
    @Unique private boolean xenopixels$fractureHeatMapActive;
    @Unique private boolean xenopixels$fractureHeatMapRebuilding;
    @Unique private boolean xenopixels$fractureHeatMapReplayCall;
    @Unique private boolean xenopixels$asteroidSplitDeferred;
    @Unique private Set<UUID> xenopixels$subLevelsBeforeSplit;

    @Invoker("heatMapRemove")
    protected abstract void xenopixels$invokeHeatMapRemove(BlockPos pos);

    @Override
    public void xenopixels$beginFractureHeatMapRebuild() {
        xenopixels$fractureHeatMapActive = false;
        xenopixels$fractureHeatMapRebuilding = true;
        initialized = false;
        splitComplete = false;
        solidCount = 0;
        state = HeatMapPropagationState.FILLING;
        subLevelSplits.clear();
        subLevelSplits.trim();
        floodfill.clear();
        removed.clear();
        newStarts.clear();
        splitIndexMap.clear();
    }

    @Override
    public void xenopixels$removeFractureHeatMapBlock(BlockPos hostBlock) {
        if (!xenopixels$fractureHeatMapRebuilding) throw new IllegalStateException("fracture heat-map rebuild is not active");
        xenopixels$invokeHeatMapRemove(hostBlock);
    }

    @Override
    public void xenopixels$addFractureHeatMapBlock(BlockPos hostBlock) {
        if (!xenopixels$fractureHeatMapRebuilding) throw new IllegalStateException("fracture heat-map rebuild is not active");
        xenopixels$fractureHeatMapReplayCall = true;
        try {
            ((dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager) (Object) this).onSolidAdded(hostBlock);
        } finally {
            xenopixels$fractureHeatMapReplayCall = false;
        }
    }

    @Override
    public void xenopixels$finishFractureHeatMapRebuild() {
        if (!xenopixels$fractureHeatMapRebuilding) throw new IllegalStateException("fracture heat-map rebuild is not active");
        xenopixels$fractureHeatMapRebuilding = false;
        xenopixels$fractureHeatMapActive = true;
        xenopixels$skipping = false;
    }

    @Override
    public void xenopixels$abortFractureHeatMapRebuild() {
        xenopixels$fractureHeatMapRebuilding = false;
        xenopixels$fractureHeatMapActive = false;
        xenopixels$skipping = true;
        initialized = false;
        splitComplete = false;
        solidCount = 0;
        state = HeatMapPropagationState.FILLING;
        subLevelSplits.clear();
        subLevelSplits.trim();
        floodfill.clear();
        removed.clear();
        newStarts.clear();
        splitIndexMap.clear();
    }

    @Override
    public boolean xenopixels$fractureHeatMapReady(int expectedSolidCount) {
        return xenopixels$fractureHeatMapActive && initialized && splitComplete
                && state == HeatMapPropagationState.CLEARING && solidCount == expectedSolidCount
                && floodfill.isEmpty() && removed.isEmpty() && newStarts.isEmpty() && subLevelSplits.isEmpty();
    }

    @Unique
    private boolean xenopixels$skip() {
        if (xenopixels$fractureHeatMapRebuilding) return true;
        if (xenopixels$fractureHeatMapActive && XenoSpaceConfig.asteroidFracture()) return false;
        if (xenopixels$fractureHeatMapActive) xenopixels$fractureHeatMapActive = false;
        if (xenopixels$skipping) return true;
        if (!PhysicalEarth.skipsSplitHeatMap(subLevel)) return false;
        xenopixels$skipping = true;
        subLevelSplits.clear();
        subLevelSplits.trim();
        floodfill.clear();
        removed.clear();
        newStarts.clear();
        splitIndexMap.clear();
        return true;
    }

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$skipPlanetShells(CallbackInfo ci) {
        if (xenopixels$skip()) ci.cancel();
    }

    /**
     * With tick() cancelled nothing ever clears subLevelSplits, so onSolidAdded would park every planet voxel there
     * (up to ~478k entries for the Sun; Qwen BZ-1). Skip the bookkeeping for the same bodies.
     */
    @Inject(method = {"onSolidAdded(Lnet/minecraft/core/BlockPos;)V", "onSolidRemoved(Lnet/minecraft/core/BlockPos;)V"},
            at = @At("HEAD"), cancellable = true, require = 2)
    private void xenopixels$skipPlanetBookkeeping(BlockPos pos, CallbackInfo ci) {
        if (xenopixels$fractureHeatMapRebuilding && XenoSpaceConfig.asteroidFracture()) {
            if (xenopixels$fractureHeatMapReplayCall) return;
            AsteroidPhysicalizer.markFractureHeatMapDirty(subLevel);
            ci.cancel();
            return;
        }
        if (xenopixels$skip()) ci.cancel();
    }

    /** Skip the entire manager tick while a previously deferred split still exceeds its hard caps. */
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$pauseDeferredAsteroidSplit(CallbackInfo ci) {
        if (ci.isCancelled()) return;
        if (!xenopixels$asteroidSplitDeferred) return;
        int componentCount = xenopixels$pendingComponentCount();
        int pendingChildren = AsteroidFragmentBudget.predictedChildren(componentCount,
                subLevelSplits.size(), solidCount);
        if (!AsteroidPhysicalizer.maySplitAsteroid(subLevel, pendingChildren)) ci.cancel();
        else xenopixels$asteroidSplitDeferred = false;
    }

    @Inject(method = "split()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$captureAsteroidSplitChildren(CallbackInfo ci) {
        int componentCount = xenopixels$pendingComponentCount();
        int pendingChildren = AsteroidFragmentBudget.predictedChildren(componentCount,
                subLevelSplits.size(), solidCount);
        if (!AsteroidPhysicalizer.maySplitAsteroid(subLevel, pendingChildren)) {
            xenopixels$asteroidSplitDeferred = true;
            xenopixels$subLevelsBeforeSplit = null;
            ci.cancel();
            return;
        }
        xenopixels$asteroidSplitDeferred = false;
        if (!AsteroidPhysicalizer.isAsteroidFamilyBody(subLevel)) {
            xenopixels$subLevelsBeforeSplit = null;
            return;
        }
        var container = SubLevelContainer.getContainer(subLevel.getLevel());
        Set<UUID> existing = new HashSet<>();
        for (SubLevel body : container.getAllSubLevels()) existing.add(body.getUniqueId());
        xenopixels$subLevelsBeforeSplit = existing;
    }

    @Inject(method = "split()V", at = @At("RETURN"), require = 1)
    private void xenopixels$tagAsteroidSplitChildren(CallbackInfo ci) {
        Set<UUID> existing = xenopixels$subLevelsBeforeSplit;
        xenopixels$subLevelsBeforeSplit = null;
        if (existing != null) AsteroidPhysicalizer.tagNewAsteroidFragments(subLevel, existing);
    }

    @Unique
    private int xenopixels$pendingComponentCount() {
        if (subLevelSplits.isEmpty()) return 0;
        Set<Integer> labels = new HashSet<>();
        for (long packed : subLevelSplits.keySet()) {
            int index = subLevelSplits.get(packed);
            if (index < 0 || index >= splitIndexMap.size()) return -1;
            labels.add(splitIndexMap.getInt(index));
        }
        return labels.size();
    }
}
