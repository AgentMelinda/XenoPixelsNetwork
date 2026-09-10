package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.npc.CustomNpcQuestCommandCompat;
import net.bullettrain.xenopixelsmod.compat.npc.NpcQuestCompletionSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "noppes.npcs.packets.server.SPacketQuestCompletionCheck", remap = false)
public abstract class QuestCompletionCommandMixin extends noppes.npcs.packets.PacketServerBasic {

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

    @Inject(method = "handle()V", at = @At("RETURN"))
    private void xenopixels$flushQuestCompletion(CallbackInfo ci) {
        NpcQuestCompletionSync.flush(player, "noppes.npcs.controllers.data.PlayerData");
    }
}
