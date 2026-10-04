package net.bullettrain.xenopixelsmod.client.maker;

import com.mojang.blaze3d.platform.NativeImage;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bakes Taotto overlays into 64×64 dynamic textures for {@code gatherTattooLayers}.
 */
public final class TaottoClientOverlays {
    private static final Map<UUID, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, TaottoDocument> DOCUMENTS = new ConcurrentHashMap<>();
    private static final ResourceLocation PREVIEW = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "dynamic/taotto/editor_preview");
    private static boolean hasPreview;
    private static UUID previewPlayer;
    private static final float[] WHITE = new float[]{1f, 1f, 1f};

    private TaottoClientOverlays() {
    }

    public static float[] white() {
        return WHITE;
    }

    public static void accept(UUID playerId, CompoundTag tag) {
        if (playerId == null) {
            return;
        }
        TaottoDocument document = TaottoDocument.loadNbt(tag);
        DOCUMENTS.put(playerId, document.copy());
        if (!document.hasPaint()) {
            release(playerId);
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                XenoPixelsMod.MOD_ID, "dynamic/taotto/" + playerId.toString().replace("-", ""));
        bake(id, document);
        TEXTURES.put(playerId, id);
    }

    private static void bake(ResourceLocation id, TaottoDocument document) {
        Minecraft mc = Minecraft.getInstance();
        int[] overlay = document.bakeOverlay(64);
        NativeImage image = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                image.setPixelRGBA(x, y, argbToAbgr(overlay[y * 64 + x]));
            }
        }
        mc.getTextureManager().register(id, new DynamicTexture(image));
    }

    public static TaottoDocument document(UUID playerId) {
        TaottoDocument document = playerId == null ? null : DOCUMENTS.get(playerId);
        return document == null ? TaottoDocument.blank() : document.copy();
    }

    public static void updatePreview(TaottoDocument document) {
        bake(PREVIEW, document);
        hasPreview = true;
    }

    /** Scope the draft texture to the editor draw; other/world renders use server sync. */
    public static void withPreview(UUID playerId, Runnable draw) {
        UUID previous = previewPlayer;
        previewPlayer = playerId;
        try { draw.run(); } finally { previewPlayer = previous; }
    }

    public static void clearPreview() {
        if (hasPreview) Minecraft.getInstance().getTextureManager().release(PREVIEW);
        hasPreview = false;
        previewPlayer = null;
    }

    public static void clearAll() {
        for (UUID id : java.util.List.copyOf(TEXTURES.keySet())) release(id);
        DOCUMENTS.clear();
        clearPreview();
    }

    public static ResourceLocation texture(UUID playerId) {
        if (hasPreview && playerId != null && playerId.equals(previewPlayer)) return PREVIEW;
        return playerId == null ? null : TEXTURES.get(playerId);
    }

    public static void release(UUID playerId) {
        ResourceLocation id = TEXTURES.remove(playerId);
        Minecraft mc = Minecraft.getInstance();
        if (id != null && mc != null && mc.getTextureManager() != null) {
            mc.getTextureManager().release(id);
        }
    }

    static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16);
    }
}
