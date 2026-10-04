package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.compat.capabilities.Capability;
import com.dragonminez.compat.util.LazyOptional;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzStats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Resolves DragonMineZ stats on CustomNPC / MyNPC living entities from the XenoPixels
 * {@code NPC_DMZ_STATS} attachment instead of the player-only {@code PLAYER_STATS} factory.
 */
@Mixin(value = StatsProvider.class, remap = false)
public abstract class StatsProviderNpcGetMixin {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xenopixels$npcStats(Capability<?> cap, Entity entity,
                                            CallbackInfoReturnable<LazyOptional<StatsData>> cir) {
        if (entity instanceof Player || entity == null) {
            return;
        }
        if (cap != StatsCapability.INSTANCE) {
            return;
        }
        LazyOptional<StatsData> npc = NpcDmzStats.optional(entity);
        if (npc != null && npc.isPresent()) {
            cir.setReturnValue(npc);
        }
    }
}
