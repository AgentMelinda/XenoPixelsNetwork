package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.npc.CustomNpcQuestCommandCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "noppes.npcs.packets.server.SPacketQuestCompletionCheck", remap = false)
public abstract class QuestCompletionCommandMixin {
    @ModifyArg(
            method = "handle()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/NoppesUtilServer;runCommand(Lnet/minecraft/world/entity/Entity;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/entity/player/Player;)Ljava/lang/String;"
            ),
            index = 2
    )
    private String xenopixels$normalizeQuestCommand(String command) {
        return CustomNpcQuestCommandCompat.normalize(command);
    }
}
