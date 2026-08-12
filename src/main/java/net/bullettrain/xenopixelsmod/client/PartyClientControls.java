package net.bullettrain.xenopixelsmod.client;

import com.dragonminez.client.events.LockOnEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.LockOnCycle;
import net.bullettrain.xenopixelsmod.client.screen.XenoPartyScreen;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.PartyActionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Rebindable P party screen and H target-ping/assist controls. */
public final class PartyClientControls {
    public static final KeyMapping OPEN_PARTY = new KeyMapping("key.xenopixelsmod.party_screen",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.xenopixelsmod");
    public static final KeyMapping PING_TARGET = new KeyMapping("key.xenopixelsmod.party_ping",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.xenopixelsmod");

    private PartyClientControls() {}

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_PARTY);
            event.register(PING_TARGET);
        }
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            ClientParty.tickExpiry(mc.level.getGameTime());
            while (OPEN_PARTY.consumeClick()) {
                if (mc.screen == null) {
                    ModNetwork.sendToServer(new PartyActionPacket(PartyActionPacket.Action.REQUEST_SYNC));
                    mc.setScreen(new XenoPartyScreen(null));
                }
            }
            while (PING_TARGET.consumeClick()) pingOrAdopt(mc);
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientParty.clearAll();
        }
    }

    private static void pingOrAdopt(Minecraft mc) {
        ClientParty.Ping ping = ClientParty.ping(mc.level.getGameTime());
        Entity aimed = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (ping != null) {
            Entity marked = mc.level.getEntity(ping.targetEntityId());
            if (marked instanceof LivingEntity living && living.isAlive()
                    && marked.getUUID().equals(ping.targetId()) && LockOnCycle.lock(living)) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§bLocked party target: §f" + ping.targetName()), true);
            }
            return;
        }
        LivingEntity locked = LockOnEvent.getLockedTarget();
        LivingEntity target = locked != null && locked.isAlive() ? locked
                : aimed instanceof LivingEntity living && living.isAlive() ? living : null;
        if (target == null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7Lock on or aim at a living target to ping it"), true);
            return;
        }
        ModNetwork.sendToServer(new PartyActionPacket(PartyActionPacket.Action.PING, target.getId()));
    }
}
