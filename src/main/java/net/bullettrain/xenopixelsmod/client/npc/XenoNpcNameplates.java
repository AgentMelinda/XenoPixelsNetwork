package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Hides the name tag of the stand-in player a FULL DMZ Xeno NPC is drawn through. The player
 * renderer put that name at player height, inside tall DMZ hair and without the title;
 * {@code XenoNpcRenderer} draws the NPC's own nameplate instead.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoNpcNameplates {
    private XenoNpcNameplates() {}

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (NpcFullDmzRenderer.isXenoNpcProxy(event.getEntity())) event.setCanRender(TriState.FALSE);
    }
}
