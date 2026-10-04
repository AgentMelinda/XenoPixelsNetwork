package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTypes;
import net.bullettrain.xenopixelsmod.compat.npc.PlayerScriptsGate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * {@code /xenopixels playerscripts} — live player-script gate for My NPCs or
 * CustomNPCs, not the GUI button. {@link ServerStartedEvent} sets
 * {@code HasStart} and {@link PlayerScriptsGate#markServerStarted()}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerScriptsCommands {
    private PlayerScriptsCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenopixels")
                .then(Commands.literal("playerscripts")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("enable").executes(ctx -> enable(ctx.getSource())))));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PlayerScriptsGate.markServerStarted();
        if (setHasStart(true)) {
            XenoPixelsMod.LOGGER.info("NPC HasStart set true after server start");
        }
    }

    private static int status(CommandSourceStack source) {
        Snapshot snap = Snapshot.read();
        if (snap.missing()) {
            source.sendFailure(Component.literal("NPC player scripts are not loaded."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "HasStart=" + snap.hasStart
                        + " serverStarted=" + PlayerScriptsGate.serverStarted()
                        + " enabled=" + snap.enabled
                        + " getEnabled=" + snap.getEnabled
                        + " scripts=" + snap.scriptCount
                        + " EnableScripting=" + snap.enableScripting
                        + " language=" + snap.language
                        + " XenoPixels=" + snap.xenoPixels), false);
        source.sendSuccess(() -> Component.literal(
                "runtimeAllow=" + PlayerScriptsGate.allow(
                        snap.hasStart, snap.enabled, snap.getEnabled, false)), false);
        return 1;
    }

    private static int enable(CommandSourceStack source) {
        try {
            Object controller = controller();
            Object playerScripts = field(controller, "playerScripts");
            if (controller == null || playerScripts == null) {
                source.sendFailure(Component.literal("NPC player scripts are not loaded."));
                return 0;
            }
            playerScripts.getClass().getMethod("setEnabled", boolean.class).invoke(playerScripts, true);
            Method save = playerScripts.getClass().getMethod("save", CompoundTag.class);
            CompoundTag nbt = (CompoundTag) save.invoke(playerScripts, new CompoundTag());
            controller.getClass().getMethod("setPlayerScripts", CompoundTag.class).invoke(controller, nbt);
            setHasStart(true);
            PlayerScriptsGate.markServerStarted();
            source.sendSuccess(() -> Component.literal(
                    "Player scripts enabled and written. Relog, then /xenopixels playerscripts."), true);
            return 1;
        } catch (ReflectiveOperationException e) {
            source.sendFailure(Component.literal("Could not enable player scripts: " + e.getMessage()));
            return 0;
        }
    }

    private static boolean setHasStart(boolean value) {
        Class<?> type = NpcTypes.find("controllers.ScriptController");
        if (type == null) {
            return false;
        }
        try {
            Field hasStart = type.getField("HasStart");
            hasStart.setBoolean(null, value);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static Object controller() {
        Class<?> type = NpcTypes.find("controllers.ScriptController");
        if (type == null) {
            return null;
        }
        try {
            return type.getField("Instance").get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object field(Object owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getClass().getField(name).get(owner);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private record Snapshot(boolean hasStart, boolean enabled, boolean getEnabled, int scriptCount,
                            boolean enableScripting, String language, boolean xenoPixels) {
        static Snapshot read() {
            Object controller = controller();
            Object playerScripts = field(controller, "playerScripts");
            if (controller == null || playerScripts == null) {
                return new Snapshot(false, false, false, 0, false, "", false);
            }
            try {
                Class<?> scriptController = controller.getClass();
                boolean hasStart = scriptController.getField("HasStart").getBoolean(null);
                boolean enabled = enabledField(playerScripts);
                boolean getEnabled = (Boolean) playerScripts.getClass().getMethod("getEnabled").invoke(playerScripts);
                Object scripts = playerScripts.getClass().getMethod("getScripts").invoke(playerScripts);
                int scriptCount = scripts instanceof Collection<?> list ? list.size() : 0;
                String language = String.valueOf(
                        playerScripts.getClass().getMethod("getLanguage").invoke(playerScripts));
                boolean enableScripting = readEnableScripting();
                return new Snapshot(hasStart, enabled, getEnabled, scriptCount, enableScripting,
                        language, dataHasXenoPixels());
            } catch (ReflectiveOperationException e) {
                return new Snapshot(false, false, false, 0, false, "", false);
            }
        }

        boolean missing() {
            return controller() == null || field(controller(), "playerScripts") == null;
        }
    }

    private static boolean enabledField(Object playerScripts) {
        try {
            Field field = playerScripts.getClass().getDeclaredField("enabled");
            field.setAccessible(true);
            return field.getBoolean(playerScripts);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean readEnableScripting() {
        Class<?> type = NpcTypes.find("MyNpcs");
        if (type == null) {
            type = NpcTypes.find("CustomNpcs");
        }
        if (type == null) {
            return false;
        }
        try {
            return type.getField("EnableScripting").getBoolean(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean dataHasXenoPixels() {
        Class<?> type = NpcTypes.find("controllers.ScriptContainer");
        if (type == null) {
            return false;
        }
        try {
            Object data = type.getField("Data").get(null);
            return data instanceof Map<?, ?> map && map.containsKey("XenoPixels");
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
