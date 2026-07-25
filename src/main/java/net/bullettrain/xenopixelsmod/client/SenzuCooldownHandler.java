package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.item.ModsItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only senzu cooldown feedback (must not live in common packages). */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SenzuCooldownHandler {
    private static final int COOLDOWN_TICKS = 1800;

    private SenzuCooldownHandler() {}

    @SubscribeEvent
    public static void onInteractKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        InteractionHand hand = event.getHand();
        if (hand == null) return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != ModsItems.STRAWBERRY_SENZU.get()) return;

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            float percentLeft = player.getCooldowns().getCooldownPercent(stack.getItem(), 0.0f);
            int secondsLeft = Math.max(1, Math.round((percentLeft * COOLDOWN_TICKS) / 20.0f));

            if (XenoClientConfig.senzuCooldownMessages) {
                player.displayClientMessage(
                        Component.literal("You must wait " + secondsLeft
                                + " seconds before eating another Strawberry Senzu Bean!"),
                        false);
                player.displayClientMessage(
                        Component.literal("§cCooldown: " + secondsLeft + "s§r"),
                        true);
            }

            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
