package net.bullettrain.xenopixelsmod.dmz.form;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DmzFormTrainerEvents {
    private DmzFormTrainerEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity trainer)
                || !NpcCounterpartSync.isCustomNpc(trainer)) {
            return;
        }
        var offerings = DmzFormMetadataRegistry.trainerOfferings(trainer.getUUID());
        if (offerings.isEmpty()) return;
        // DmzSkillMasterInteraction.interact already requires this. Without the same check here,
        // any NPC merely listed as a trainer would swallow the right-click and open the menu, so
        // the two entry points would disagree about what counts as a skill master.
        if (!DmzSkillMaster.isSkillMasterAnywhere(offerings, trainer.getUUID())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        FormEditorNetwork.sendTrainer(player, trainer, offerings);
    }
}
