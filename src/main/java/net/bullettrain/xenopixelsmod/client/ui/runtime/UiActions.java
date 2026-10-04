package net.bullettrain.xenopixelsmod.client.ui.runtime;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimPlayer;
import net.bullettrain.xenopixelsmod.ui.UiDocument;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UiActions {
    private UiActions() {
    }

    public static void fire(String action) {
        if (action == null || action.isBlank()) return;
        if (action.startsWith("open_document:")) {
            open(action.substring("open_document:".length()).trim());
        } else if (action.startsWith("play_clip:")) {
            playClip(action.substring("play_clip:".length()).trim());
        } else if (action.startsWith("master_menu:")) {
            open(action.substring("master_menu:".length()).trim());
        } else if (action.startsWith("dmz_page:")) {
            openAssigned(action.substring("dmz_page:".length()).trim());
        } else if ("close".equals(action)) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> minecraft.setScreen(null));
        }
    }

    private static void openAssigned(String pageId) {
        var page = net.bullettrain.xenopixelsmod.ui.DmzMenuPage.parse(pageId);
        String documentId = page == null ? null : UiRuntime.assignments().documentId(page);
        if (documentId != null) {
            open(documentId);
        }
    }

    private static void playClip(String name) {
        try {
            XenoAnimClip clip = XenoAnimClip.load(name);
            XenoAnimPlayer.play(clip, false);
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Could not play UI animation {}: {}", name, e.toString());
        }
    }

    public static void open(String id) {
        UiDocument document = UiRuntime.document(id);
        if (document == null) {
            return;
        }
        XenoPixelsMod.LOGGER.info("UI document screen {}", id);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new UiDocumentScreen(document)));
    }
}
