package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;
import noppes.npcs.api.IPos;
import noppes.npcs.packets.Packets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes CustomNPCs' script sound API honour the volume it was given.
 *
 * <p>{@code WorldWrapper.playSoundAt(IPos, String, float, float)} hard-codes a 16-block send
 * radius:
 *
 * <pre>
 *   bipush 16
 *   invokestatic Packets.sendNearby(Level, BlockPos, int, CustomPacketPayload)
 * </pre>
 *
 * and {@code Packets.sendNearby} passes that straight to
 * {@code PacketDistributor.sendToPlayersNear}. So a script sound never reaches anyone past 16
 * blocks no matter how loud it is — which is what makes NPC script sounds read as "too far away
 * to be heard" from a very short distance.
 *
 * <p>Vanilla's own rule is that a volume above 1 extends audible range proportionally
 * ({@code ServerLevel.playSeededSound} uses {@code volume > 1 ? 16 * volume : 16}). This applies
 * that same rule to the packet radius, so the volume argument finally means something.
 *
 * <p>A {@link Redirect} with the enclosing method's arguments appended is used rather than
 * {@code ModifyArg} plus a captured field: the radius constant carries no reference back to the
 * volume, and a mixin instance field would be shared state on a class the server calls from
 * script threads.
 */
@Mixin(targets = "noppes.npcs.api.wrapper.WorldWrapper", remap = false)
public abstract class WorldWrapperSoundRangeMixin {
    @Redirect(
            method = "playSoundAt(Lnoppes/npcs/api/IPos;Ljava/lang/String;FF)V",
            at = @At(value = "INVOKE", target =
                    "Lnoppes/npcs/packets/Packets;sendNearby(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;ILnet/minecraft/network/protocol/common/"
                    + "custom/CustomPacketPayload;)V"),
            require = 0)
    private <MSG extends CustomPacketPayload> void xenopixels$widenSoundRange(
            Level level, BlockPos pos, int radius, MSG payload,
            IPos scriptPos, String sound, float volume, float pitch) {
        Packets.sendNearby(level, pos, NpcScriptSound.radiusFor(radius, volume), payload);
    }
}
