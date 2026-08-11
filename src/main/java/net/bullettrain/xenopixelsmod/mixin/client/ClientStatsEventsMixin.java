package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.ClientStatsEvents;
import com.dragonminez.client.util.KeyBinds;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adds configurable hold-to-fire without modifying DragonMineZ sources. */
@Mixin(value = ClientStatsEvents.class, remap = false)
public abstract class ClientStatsEventsMixin {
    private static int xenopixelsmod$kiBlastRepeatTicks;

    @ModifyExpressionValue(
            method = "lambda$onClientTick$3",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/dragonminez/client/events/ClientStatsEvents;wasRightClickDown:Z",
                    opcode = Opcodes.GETSTATIC,
                    ordinal = 0
            )
    )
    private static boolean xenopixelsmod$repeatKiBlastWhileHeld(boolean wasRightClickDown) {
        var config = XenoServerClientState.get();
        Minecraft minecraft = Minecraft.getInstance();
        boolean chordHeld = minecraft.player != null && minecraft.screen == null
                && KeyBinds.SECOND_FUNCTION_KEY.isDown() && minecraft.options.keyUse.isDown();

        if (!config.kiBlastHoldToFire || !chordHeld) {
            xenopixelsmod$kiBlastRepeatTicks = 0;
            return wasRightClickDown;
        }

        if (xenopixelsmod$kiBlastRepeatTicks > 0) xenopixelsmod$kiBlastRepeatTicks--;
        if (xenopixelsmod$kiBlastRepeatTicks <= 0) {
            xenopixelsmod$kiBlastRepeatTicks = Math.max(1, config.kiBlastCooldownTicks);
            return false;
        }
        return wasRightClickDown;
    }
}
