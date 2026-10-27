package net.bullettrain.xenopixelsmod.client.flight;

import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SpacePodBoostInputPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Sends only changed or periodic held-boost input; boost expires server-side if a keepalive is lost. */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class SpacePodBoostInput {
    private static final int KEEPALIVE_TICKS = 4;
    /** Ctrl boost is a real, remappable key entry; it does not rely on vanilla's Shift sneak binding. */
    public static final KeyMapping BOOST = new KeyMapping("key.xenopixelsmod.space_pod_boost",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.xenopixelsmod");
    private static boolean sentBoost;
    private static int ticksSinceSend = KEEPALIVE_TICKS;

    private SpacePodBoostInput() {}

    @EventBusSubscriber(modid = "xenopixelsmod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKey(RegisterKeyMappingsEvent event) { event.register(BOOST); }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            reset();
            return;
        }
        ticksSinceSend++;
        boolean boost = minecraft.screen == null && minecraft.player.getVehicle() == null
                && BOOST.isDown();
        if (boost == sentBoost && (!boost || ticksSinceSend < KEEPALIVE_TICKS)) return;
        ModNetwork.sendToServer(new SpacePodBoostInputPacket(boost));
        sentBoost = boost;
        ticksSinceSend = 0;
    }

    private static void reset() {
        if (sentBoost && Minecraft.getInstance().getConnection() != null)
            ModNetwork.sendToServer(new SpacePodBoostInputPacket(false));
        sentBoost = false;
        ticksSinceSend = KEEPALIVE_TICKS;
    }
}
