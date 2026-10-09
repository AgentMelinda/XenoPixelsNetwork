package net.bullettrain.xenopixelsmod.client.npc.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

/** Client bridge; callers dispatch incoming packets onto the client thread. */
@OnlyIn(Dist.CLIENT)
public final class XenoGuiClient {
    private XenoGuiClient() {}

    public static void snapshot(CompoundTag snapshot) {
        if (snapshot == null || !snapshot.hasUUID("Session")) return;
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof XenoCustomGuiScreen current) {
            if (current.session().equals(snapshot.getUUID("Session"))) {
                current.applySnapshot(snapshot);
                return;
            }
            current.serverClosing();
        }
        client.setScreen(new XenoCustomGuiScreen(snapshot));
    }

    public static void close(UUID session) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof XenoCustomGuiScreen screen && screen.session().equals(session)) {
            screen.serverClosing();
            client.setScreen(null);
        }
    }
}
