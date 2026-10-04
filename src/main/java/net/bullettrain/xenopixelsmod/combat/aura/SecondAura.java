package net.bullettrain.xenopixelsmod.combat.aura;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SecondAuraStatePacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A player's second aura switch (2026-10-02 owner): on, the HD aura plays on them all the time,
 * whether or not DragonMineZ's own aura is showing; off, it plays only while DragonMineZ's aura
 * shows (a client can turn that off too, /xenoaura follow off). It is the player's own setting,
 * kept with the player through death, and everyone tracking them is told.
 *
 * <p>{@code /secondaura} toggles it, {@code /secondaura on|off} sets it. The Ki Actions entry in
 * DragonMineZ's X menu sends the same command.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SecondAura {
    static final String KEY = "XenoSecondAura";

    private SecondAura() {
    }

    public static boolean isOn(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(KEY);
    }

    public static void set(ServerPlayer player, boolean on) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(KEY, on);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        ModNetwork.sendToTrackingAndSelf(player, new SecondAuraStatePacket(player.getId(), isOn(player)));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("secondaura")
                .executes(ctx -> run(ctx.getSource(), null))
                .then(Commands.literal("on").executes(ctx -> run(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> run(ctx.getSource(), false))));
    }

    private static int run(CommandSourceStack source, Boolean wanted) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a player has a second aura."));
            return 0;
        }
        boolean on = wanted == null ? !isOn(player) : wanted;
        set(player, on);
        source.sendSuccess(() -> Component.literal("Second aura: " + (on ? "on" : "off")), false);
        return 1;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    /** A respawned player is a new entity with a new id. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer tracker)) return;
        if (!(event.getTarget() instanceof ServerPlayer target) || !isOn(target)) return;
        ModNetwork.sendToPlayer(tracker, new SecondAuraStatePacket(target.getId(), true));
    }
}
