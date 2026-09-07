package net.bullettrain.xenopixelsmod.client.combat;

import com.dragonminez.client.events.LockOnEvent;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.clone.CloneTargetTracker;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CloneTargetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Observes native Z-lock, Xeno cycling and scripted locks without changing DMZ technique aim. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class CloneLockSync {
    private static int lastTarget = -1;
    private static int ticksSinceSend;
    private static Object lastLevel;

    private CloneLockSync() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            reset();
            return;
        }
        if (lastLevel != mc.level) {
            reset();
            lastLevel = mc.level;
        }
        LivingEntity target = LockOnEvent.getLockedTarget();
        int id = mc.player.isAlive() && !mc.player.isSpectator() && target != null
                && target.isAlive() && !target.isRemoved() && target.level() == mc.level ? target.getId() : -1;
        if (id != lastTarget || id >= 0 && ++ticksSinceSend >= CloneTargetTracker.HEARTBEAT_TICKS) {
            ModNetwork.sendToServer(new CloneTargetPacket(id));
            lastTarget = id;
            ticksSinceSend = 0;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        lastTarget = -1;
        ticksSinceSend = 0;
        lastLevel = null;
    }
}
