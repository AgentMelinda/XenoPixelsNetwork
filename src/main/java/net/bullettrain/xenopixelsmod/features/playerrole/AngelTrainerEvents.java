package net.bullettrain.xenopixelsmod.features.playerrole;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Opens the gods trainer menu when a player right-clicks an angel player.
 *
 * <p>NPC skill masters stay on {@code DmzFormTrainerEvents}. This path is angel-only, offers
 * only {@link AngelTrainerGate#GODS_GROUP}, and reuses {@link FormEditorNetwork#sendTrainerMenu}
 * / {@code TrainerPurchasePacket.purchase} — no combat modifiers.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class AngelTrainerEvents {
    private AngelTrainerEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer buyer)) return;
        if (!(event.getTarget() instanceof ServerPlayer trainer)) return;
        if (buyer.getUUID().equals(trainer.getUUID())) return;
        if (!AngelTrainerGate.isAngelTrainer(trainer)) return;
        if (buyer.distanceToSqr(trainer) > 8.0 * 8.0) return;

        DmzTrainerMenu menu = AngelTrainerGate.menuFor(trainer.getUUID());
        if (menu == null || menu.entries().isEmpty()) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        FormEditorNetwork.sendTrainerMenu(buyer, trainer, menu);
    }
}
