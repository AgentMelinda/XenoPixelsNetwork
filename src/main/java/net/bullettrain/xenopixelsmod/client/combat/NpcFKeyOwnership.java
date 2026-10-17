package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** F over an NPC belongs to recovery, not unrelated queued flight/swap actions. Rush mash uses SPACE. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class NpcFKeyOwnership {
    private NpcFKeyOwnership() {}

    static void drain(KeyMapping[] mappings, int key, int scanCode) {
        for (KeyMapping mapping : mappings) {
            if (!mapping.matches(key, scanCode)) continue;
            mapping.setDown(false);
            while (mapping.consumeClick()) { }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || event.getKey() != GLFW.GLFW_KEY_F) return;
        boolean prompt = net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.rushPromptTicks() > 0
                || net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.recovery() != null;
        var entity = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (!prompt && !(entity instanceof XenoNpcEntity)
                && !net.bullettrain.xenopixelsmod.compat.npc.NpcTypes.isNpc(entity)) return;
        // InputEvent.Key cannot be canceled. Drain mappings before normal-priority handlers,
        // while the independent raw F event still reaches the recovery HUD listener.
        drain(mc.options.keyMappings, event.getKey(), event.getScanCode());
    }
}
