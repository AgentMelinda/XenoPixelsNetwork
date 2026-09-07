package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfigKeys;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Readable front-end for every combat/skill knob. Writes the same
 * {@link XenoServerConfigKeys} map as {@code /xenoserver set}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoSkillConfigCommands {

    private XenoSkillConfigCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    XenoServerConfigKeys.suggest(builder.getRemaining()), builder);
    private static final SuggestionProvider<CommandSourceStack> SKILL_IDS =
            (ctx, b) -> SharedSuggestionProvider.suggest(CombatSkills.DEFS.keySet(), b);

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("xenoskillconfig")
                .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource(), ""))
                        .then(Commands.argument("prefix", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .executes(ctx -> list(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "prefix")))))
                .then(Commands.literal("get")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .executes(ctx -> get(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "key")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> set(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("skill")
                        .then(Commands.literal("list")
                                .executes(ctx -> skillList(ctx.getSource())))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(SKILL_IDS)
                                        .executes(ctx -> skillUnlock(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> skillUnlock(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(SKILL_IDS)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 3))
                                                .executes(ctx -> skillSet(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "level"),
                                                        ctx.getSource().getPlayerOrException()))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> skillSet(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                IntegerArgumentType.getInteger(ctx, "level"),
                                                                EntityArgument.getPlayer(ctx, "player"))))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoskillconfig list [prefix] | get <key> | set <key> <value>\n"
                                    + "       /xenoskillconfig skill list|unlock <id> [player]|set <id> <0-3> [player]"),
                            false);
                    return 1;
                }));
    }

    private static int list(CommandSourceStack source, String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (XenoServerConfigKeys.Key key : XenoServerConfigKeys.all()) {
            if (!p.isEmpty()
                    && !key.id.toLowerCase().contains(p)
                    && !key.help.toLowerCase().contains(p)) {
                continue;
            }
            sb.append("§e").append(key.id).append(" §f= ").append(key.get())
                    .append(" §7").append(key.help).append('\n');
            n++;
        }
        if (n == 0) {
            source.sendFailure(Component.literal("No keys matching '" + prefix + "'"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return n;
    }

    private static int get(CommandSourceStack source, String key) {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.get(key);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message), false);
        return 1;
    }

    private static int set(CommandSourceStack source, String key, String value) {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.set(key, value);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        XenoServerConfig.save();
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(result.message), true);
        return 1;
    }

    private static int skillList(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        StringBuilder sb = new StringBuilder("Combat skills:\n");
        for (CombatSkills.SkillDef def : CombatSkills.DEFS.values()) {
            int lv = p != null ? CombatSkills.level(p, def.id()) : 0;
            sb.append("  §e").append(def.id()).append(" §fLv.").append(lv)
                    .append('/').append(def.maxLevel())
                    .append(" §7").append(def.title()).append('\n');
        }
        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    private static int skillUnlock(CommandSourceStack source, String id, ServerPlayer target) {
        String err = CombatSkills.tryUnlock(target, id);
        if (err != null) {
            source.sendFailure(Component.literal(err));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Unlocked " + id + " for "
                + target.getGameProfile().getName()
                + " Lv." + CombatSkills.level(target, id)), true);
        return 1;
    }

    private static int skillSet(CommandSourceStack source, String id, int level, ServerPlayer target) {
        CombatSkills.SkillDef def = CombatSkills.DEFS.get(id == null ? "" : id.toLowerCase());
        if (def == null) {
            source.sendFailure(Component.literal("Unknown skill"));
            return 0;
        }
        XenoCapabilities.get(target).ifPresent(data -> data.setSkillLevel(def.id(), level));
        source.sendSuccess(() -> Component.literal(def.id() + " = Lv." + level + " for "
                + target.getGameProfile().getName()), true);
        return 1;
    }
}
