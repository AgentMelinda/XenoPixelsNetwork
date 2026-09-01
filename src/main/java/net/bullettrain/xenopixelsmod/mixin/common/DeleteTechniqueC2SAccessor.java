package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.network.C2S.DeleteTechniqueC2S;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DeleteTechniqueC2S.class, remap = false)
public interface DeleteTechniqueC2SAccessor {
    @Accessor("techniqueId")
    String xenopixels$techniqueId();
}
