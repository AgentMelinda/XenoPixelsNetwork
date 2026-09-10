package net.bullettrain.xenopixelsmod.mixin.compat.create;

import com.simibubi.create.foundation.advancement.CreateAdvancement;
import net.minecraft.advancements.Advancement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CreateAdvancement.class, remap = false)
public interface CreateAdvancementAccessor {
    @Accessor("mcBuilder")
    Advancement.Builder xenopixels$minecraftBuilder();
}
