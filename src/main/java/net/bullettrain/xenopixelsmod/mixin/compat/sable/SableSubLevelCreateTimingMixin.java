package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.bullettrain.xenopixelsmod.space.physics.SableAllocationTiming;
import org.spongepowered.asm.mixin.Mixin;

import java.util.UUID;

/** Times the exact Sable createSubLevel stage only while PlotBodyBuilder's allocation trace is active. */
@Mixin(targets = "dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", remap = false)
public abstract class SableSubLevelCreateTimingMixin {
    @WrapMethod(method = "createSubLevel(IILdev/ryanhcode/sable/companion/math/Pose3d;Ljava/util/UUID;)Ldev/ryanhcode/sable/sublevel/SubLevel;",
            require = 1)
    private SubLevel xenopixels$timeCreateSubLevel(int x, int z, Pose3d pose, UUID id,
                                                   Operation<SubLevel> original) {
        if (!SableAllocationTiming.isActive()) return original.call(x, z, pose, id);
        long started = System.nanoTime();
        try { return original.call(x, z, pose, id); }
        finally { SableAllocationTiming.recordSubLevelCreate(System.nanoTime() - started); }
    }
}
