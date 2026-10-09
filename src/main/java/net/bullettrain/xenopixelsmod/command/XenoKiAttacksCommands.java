package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.combat.v3.ki.KiAttackVisualMode;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /xenokiattacks dmz|aaa|neweffects} — Strike Ki presentation backend.
 *
 * <p>{@code dmz} uses real DragonMineZ ki projectiles (hitboxes + colors). {@code aaa} and
 * {@code neweffects} use the owned HD/Effekseer path ({@code neweffects} aliases {@code aaa}
 * until a distinct third renderer exists).
 */
@EventBusSubscriber(modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class XenoKiAttacksCommands {
    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"dmz", "aaa", "neweffects", "status"}, builder);

    private XenoKiAttacksCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenokiattacks")
                .requires(XenoPermissions.require(XenoPermissions.XENOBARRAGE_SET))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(MODE_SUGGEST)
                        .executes(ctx -> setOrStatus(ctx.getSource(), StringArgumentType.getString(ctx, "mode"))))
                .executes(ctx -> status(ctx.getSource())));
    }

    private static int setOrStatus(CommandSourceStack source, String raw) {
        if ("status".equalsIgnoreCase(raw)) return status(source);
        KiAttackVisualMode mode;
        try {
            mode = KiAttackVisualMode.parse(raw);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        XenoServerConfig.kiAttackVisualMode = mode.commandName();
        XenoServerConfig.effekseerKiAttacks = mode.usesOwnedHdVisuals();
        XenoServerConfig.save();
        source.sendSuccess(() -> Component.literal("Strike Ki attacks: " + describe(mode)), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        KiAttackVisualMode mode = XenoServerConfig.kiAttackVisual();
        source.sendSuccess(() -> Component.literal("Strike Ki attacks: " + describe(mode)), false);
        return 1;
    }

    private static String describe(KiAttackVisualMode mode) {
        return switch (mode) {
            case DMZ -> "dmz (DragonMineZ projectiles + hitboxes + colors)";
            case AAA -> "aaa (AAA Particles / Effekseer HD)";
            case NEWEFFECTS -> "neweffects (owned V3KiShots HD; aliases aaa)";
        };
    }
}
