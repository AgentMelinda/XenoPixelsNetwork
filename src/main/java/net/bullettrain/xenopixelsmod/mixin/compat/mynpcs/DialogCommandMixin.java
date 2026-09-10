package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.compat.npc.CustomNpcQuestCommandCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Rewrites {@code {RefPlayer}} in a command run from a dialog option, the same way
 * {@link QuestCompletionCommandMixin} already does for a quest reward.
 *
 * <p><b>Target:</b> {@code espi.mynpcs.packets.server.SPacketDialogSelected#handle()}, the call to
 * {@code EspiUtilServer#runCommand(Entity, String, String, Player)}, argument 2 — the command.
 * <b>Written for:</b> Minecraft 1.21.1 / NeoForge 21.1.238, My NPCs 1.5.0. <b>Side:</b> server.
 *
 * <p><b>Why this exists.</b> Five My NPCs classes call {@code runCommand}, and the rewrite covered
 * exactly one of them — quest completion. A dialog option is the other route a server actually uses
 * to hand out points, and there {@code {RefPlayer}} reached the command untouched, where neither
 * vanilla's selector nor {@code XenoPointsCommands} could resolve it.
 *
 * <p>The remaining three callers — {@code DataScenes$SceneContainer} and the two scripted-block
 * wrappers, plus {@code NPCWrapper}'s script {@code executeCommand} — are deliberately left alone: a
 * script author writes the token they want directly, and guessing at their strings is how a rewrite
 * starts corrupting commands it was never meant to touch.
 *
 * <p><b>Guideline notes</b> (§18): {@code @ModifyArg} on the exact descriptor, index 2, so nothing
 * but the command string is touched. {@code @Pseudo} and {@code require = 0} because the target
 * belongs to another mod. My NPCs is not modified.
 */
@Pseudo
@Mixin(targets = "espi.mynpcs.packets.server.SPacketDialogSelected", remap = false)
public abstract class DialogCommandMixin {

    @ModifyArg(
            method = "handle()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lespi/mynpcs/EspiUtilServer;runCommand("
                            + "Lnet/minecraft/world/entity/Entity;"
                            + "Ljava/lang/String;Ljava/lang/String;"
                            + "Lnet/minecraft/world/entity/player/Player;)Ljava/lang/String;"
            ),
            index = 2,
            remap = false,
            require = 0
    )
    private String xenopixels$normalizeDialogCommand(String command) {
        return CustomNpcQuestCommandCompat.normalize(command);
    }
}
