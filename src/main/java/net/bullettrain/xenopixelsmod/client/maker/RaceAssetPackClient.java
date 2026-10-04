package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.dmz.race.RaceAssetPack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Registers the generated race-catalog folder pack so TextureCounter sees authored body PNGs.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RaceAssetPackClient {
    private RaceAssetPackClient() {
    }

    public static java.util.concurrent.CompletableFuture<Void> reload() {
        return net.minecraft.client.Minecraft.getInstance().reloadResourcePacks().thenRun(() -> {
            com.dragonminez.client.util.TextureCounter.clearCache();
            com.dragonminez.client.render.layer.DMZSkinLayer.clearValidatedTexturesCache();
        });
    }

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path root = RaceAssetPack.packRoot();
        try {
            RaceAssetPack.ensureMcmeta(root);
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Could not write race catalog pack.mcmeta", e);
            return;
        }
        if (!Files.isDirectory(root)) {
            return;
        }
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    new net.minecraft.server.packs.PackLocationInfo(
                            RaceAssetPack.PACK_ID,
                            Component.literal("XenoPixels Race Catalogs"),
                            PackSource.BUILT_IN,
                            java.util.Optional.empty()),
                    new PathPackResources.PathResourcesSupplier(root),
                    PackType.CLIENT_RESOURCES,
                    new net.minecraft.server.packs.PackSelectionConfig(
                            true, Pack.Position.TOP, false));
            if (pack != null) {
                consumer.accept(pack);
            }
        });
    }
}
