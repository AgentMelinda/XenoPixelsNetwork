package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bullettrain.xenopixelsmod.compat.npc.NpcPartyReward;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Re-runs an NPC command for every online party member when the reward uses {@code @dp}. */
@Pseudo
@Mixin(targets = "espi.mynpcs.EspiUtilServer", remap = false)
public abstract class NpcPartyRewardCommandMixin {
    @WrapMethod(method = "runCommand", require = 0)
    private static String xenopixels$partyReward(Entity executor, String name, String command,
                                                 Player player, Operation<String> original) {
        return NpcPartyReward.runForParty(player, command, member ->
                original.call(executor, name, command, member));
    }
}
