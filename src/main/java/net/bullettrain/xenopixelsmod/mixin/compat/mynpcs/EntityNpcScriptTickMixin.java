package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptTickCompat;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "espi.mynpcs.entity.EntityNPCInterface", remap = false)
public abstract class EntityNpcScriptTickMixin {
    @Redirect(
            method = "tick()V",
            at = @At(value = "INVOKE",
                    target = "Lespi/mynpcs/EventHooks;onNPCTick(Lespi/mynpcs/entity/EntityNPCInterface;)V"))
    private void xenopixels$replaceTenTickHook(@Coerce Object npc) {
        if (((Entity) npc).tickCount % interval() == 0) {
            run(npc);
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void xenopixels$runFastScriptTicks(CallbackInfo ci) {
        Entity npc = (Entity) (Object) this;
        if (npc.tickCount % 10 != 0 && npc.tickCount % interval() == 0) {
            run(npc);
        }
    }

    private static void run(Object npc) {
        NpcScriptTickCompat.run(npc, "espi.mynpcs.EventHooks",
                "espi.mynpcs.entity.EntityNPCInterface");
    }

    private static int interval() {
        return Math.max(1, Math.min(20, XenoServerConfig.npcScriptTickInterval));
    }
}
