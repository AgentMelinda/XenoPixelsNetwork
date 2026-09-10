package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.compat.npc.clone.NpcCloneConverter;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a CustomNPCs clone file be pasted straight into My NPCs' clone folder.
 *
 * <p>The two formats are all but identical — the same SNBT, ~190 keys of which 170 match — so a
 * CustomNPCs clone already lists in the cloner. It just cannot be spawned, because its {@code id} is
 * {@code customnpcs:customnpc} and that entity type does not exist. Converting the tag on the way
 * out of the controller fixes exactly that, and does it for every route into the clone at once.
 *
 * <p>The file on disk is never rewritten. Someone who drops a folder of clones in keeps their
 * originals, and can still take them back to CustomNPCs; {@code /xenopixels importclones} is there
 * for a permanent conversion.
 *
 * <p>{@code require = 0}: My NPCs updates often, and a renamed method here should cost the paste-in
 * convenience, not the ability to load the mod.
 */
@Pseudo
@Mixin(targets = "espi.mynpcs.controllers.ServerCloneController", remap = false)
public abstract class ServerCloneImportMixin {

    @Inject(method = "getCloneData", at = @At("RETURN"), cancellable = true, require = 0)
    private void xenopixels$convertCustomNpcsClone(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag clone = cir.getReturnValue();
        if (NpcCloneConverter.isCustomNpcsClone(clone)) {
            cir.setReturnValue(NpcCloneConverter.convert(clone));
        }
    }
}
