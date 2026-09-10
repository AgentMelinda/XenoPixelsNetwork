package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets an NPC run its own commands on a server that has command blocks switched off.
 *
 * <p><b>Target:</b> {@code espi.mynpcs.EspiUtilServer#runCommand}, the call to
 * {@code MinecraftServer#isCommandBlockEnabled()}. <b>Written for:</b> Minecraft 1.21.1 /
 * NeoForge 21.1.238, My NPCs 1.5.0, MixinExtras 0.5.3. <b>Side:</b> server.
 *
 * <p><b>The bug.</b> A quest reward of {@code xenopoints add 5000 @dp} did nothing at all. The cause
 * is not the token — {@code XenoPointsCommands} resolves {@code @dp} correctly — but this, at the
 * very top of the method that runs every NPC command:
 *
 * <pre>
 * if (!level.getServer().isCommandBlockEnabled()) {
 *     CommonUtil.NotifyOPs(server, "Cant run commands if CommandBlocks are disabled");
 *     return "Cant run commands if CommandBlocks are disabled";   // returns here
 * }
 * command = command.replace("@dp", player.getName().getString()); // never reached
 * </pre>
 *
 * <p>It bails before the substitution and before dispatch, so with {@code enable-command-block=false}
 * no NPC command of any kind runs. CustomNPCs' {@code NoppesUtilServer.runCommand} carries the
 * byte-for-byte identical guard, so this was never a difference between the two mods.
 *
 * <p><b>The fix, and its scope.</b> Only this one call is answered differently, and only while
 * {@code npcCommandsIgnoreCommandBlockSetting} is on. A real command block in the world still reads
 * the server's own setting and still does nothing — the server owner's choice about command blocks
 * is untouched, and what changes is whether an NPC is bound by it.
 *
 * <p><b>Off by default.</b> My NPCs runs NPC commands at permission level 2, or level 4 when its own
 * {@code NpcUseOpCommands} is enabled, so this is not a free switch: it is the operator's decision.
 * The alternative with no mod involved is {@code enable-command-block=true}.
 *
 * <p><b>Guideline notes</b> (§18): {@code @ModifyExpressionValue} is the narrowest injector that can
 * answer one call differently without touching the branch around it. The body is one boolean field
 * read, and with the setting off it returns the original value, so a server that has not opted in
 * behaves exactly as before. {@code @Pseudo} and {@code require = 0} because the target belongs to
 * another mod: if My NPCs moves this check the gate is simply lost rather than the game failing to
 * start. My NPCs is not modified.
 */
@Pseudo
@Mixin(targets = "espi.mynpcs.EspiUtilServer", remap = false)
public abstract class NpcCommandBlockGateMixin {

    @ModifyExpressionValue(
            method = "runCommand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;isCommandBlockEnabled()Z"
            ),
            remap = false,
            require = 0
    )
    private static boolean xenopixels$allowNpcCommands(boolean original) {
        return original || XenoServerConfig.npcCommandsIgnoreCommandBlockSetting;
    }
}
