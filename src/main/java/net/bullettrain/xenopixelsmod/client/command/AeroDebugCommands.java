package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.AeroHitRegions;
import net.bullettrain.xenopixelsmod.client.aero.AeroHitRegionOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Client-only developer commands for the Aero panel. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class AeroDebugCommands {

    private AeroDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenoaero")
                .then(Commands.literal("debug").executes(ctx -> {
                    boolean on = AeroHitRegionOverlay.toggle();
                    feedback("§bClick-region overlay " + (on ? "§aon" : "§7off"));
                    return 1;
                }))
                .then(Commands.literal("regions").executes(ctx -> {
                    var regions = AeroHitRegions.all();
                    if (regions.isEmpty()) {
                        feedback("§cNo click regions loaded — check the generated hitbox data");
                        return 0;
                    }
                    feedback("§bLoaded " + regions.size() + " click region(s):");
                    for (AeroHitRegions.Region region : regions) {
                        feedback(String.format("§7 %s §8→ §f%s", region.name(), region.action()));
                    }
                    return regions.size();
                })));
    }

    private static void feedback(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
