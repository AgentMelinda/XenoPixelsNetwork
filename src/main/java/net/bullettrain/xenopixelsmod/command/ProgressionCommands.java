package net.bullettrain.xenopixelsmod.command;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.features.progression.SuperSoulCatalog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Phase 3 public commands:
 * <pre>
 * /xenotrain dummy|dummytrain [25-100]|reset|stats
 * /xenosoul list|equip &lt;id&gt;|clear|status
 * /xenoskill list|unlock &lt;id&gt;|points
 * /xenoquest list|start &lt;id&gt;|status|abort
 * /xenomentor set &lt;player&gt;|clear|status
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ProgressionCommands {
    private ProgressionCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static final SuggestionProvider<CommandSourceStack> SOUL_IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(SuperSoulCatalog.SOULS.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> SKILL_IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(CombatSkills.DEFS.keySet(), b);
    // Built-ins and pack-defined quests together. Suggesting only the built-ins would have made a
    // datapack quest look unavailable while being perfectly startable.
    private static final SuggestionProvider<CommandSourceStack> QUEST_IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(ParallelQuests.ids(), b);

    /**
     * Only the quests this player is actually on.
     *
     * <p>Suggesting every known id would offer dozens that abort would then refuse.
     */
    private static final SuggestionProvider<CommandSourceStack> ACTIVE_QUEST_IDS = (ctx, b) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return SharedSuggestionProvider.suggest(java.util.List.of(), b);
        }
        return SharedSuggestionProvider.suggest(
                XenoCapabilities.get(player)
                        .map(data -> data.quests().actives().stream()
                                .map(net.bullettrain.xenopixelsmod.features.progression.ActiveQuest::id)
                                .toList())
                        .orElse(java.util.List.of()),
                b);
    };

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        // --- Training dummy ---
        d.register(Commands.literal("xenotrain")
                .requires(src -> src.hasPermission(0))
                .then(Commands.literal("dummy")
                        .executes(ctx -> spawnDummy(ctx.getSource())))
                .then(Commands.literal("shadow")
                        .executes(ctx -> spawnTrainingNpc(ctx.getSource(), 75, false))
                        .then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(25, 100))
                                .executes(ctx -> spawnTrainingNpc(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "percent"), false))))
                .then(Commands.literal("dummytrain")
                        .executes(ctx -> spawnTrainingNpc(ctx.getSource(), 75, true))
                        .then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(25, 100))
                                .executes(ctx -> spawnTrainingNpc(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "percent"), true))))
                // The standing copies these two were before they got the V9 combat brain.
                .then(Commands.literal("classic")
                        .then(Commands.literal("shadow")
                                .executes(ctx -> spawnShadow(ctx.getSource(), 75))
                                .then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(25, 100))
                                        .executes(ctx -> spawnShadow(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "percent")))))
                        .then(Commands.literal("dummytrain")
                                .executes(ctx -> spawnShadow(ctx.getSource(), 75, true))
                                .then(Commands.argument("percent", com.mojang.brigadier.arguments.IntegerArgumentType.integer(25, 100))
                                        .executes(ctx -> spawnShadow(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "percent"), true)))))
                .then(Commands.literal("dismiss")
                        .executes(ctx -> dismissShadow(ctx.getSource(), false))
                        .then(Commands.literal("all")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> dismissShadow(ctx.getSource(), true))))
                .then(Commands.literal("reset")
                        .executes(ctx -> resetDummy(ctx.getSource())))
                .then(Commands.literal("stats")
                        .executes(ctx -> dummyStats(ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenotrain <dummy|shadow [25-100]|dummytrain [25-100]|classic shadow|dummytrain [25-100]|dismiss [all]|reset|stats>"), false);
                    return 1;
                }));

        // --- Super Souls ---
        d.register(Commands.literal("xenosoul")
                .requires(src -> src.hasPermission(0))
                .then(Commands.literal("list")
                        .executes(ctx -> soulList(ctx.getSource())))
                .then(Commands.literal("equip")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SOUL_IDS)
                                .executes(ctx -> soulEquip(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> soulClear(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> soulStatus(ctx.getSource())))
                .then(Commands.literal("give")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SOUL_IDS)
                                .executes(ctx -> soulGive(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        ctx.getSource().getPlayer()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> soulGive(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenosoul <list|equip <id>|give <id> [player]|clear|status>"), false);
                    return 1;
                }));

        // --- Skill tree ---
        d.register(Commands.literal("xenoskill")
                .requires(src -> src.hasPermission(0))
                .then(Commands.literal("list")
                        .executes(ctx -> skillList(ctx.getSource())))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SKILL_IDS)
                                .executes(ctx -> skillUnlock(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("grant")
                        .requires(XenoPermissions.require(XenoPermissions.SKILL_EXCLUSIVE_GRANT))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SKILL_IDS)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> skillGrant(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("revoke")
                        .requires(XenoPermissions.require(XenoPermissions.SKILL_EXCLUSIVE_GRANT))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(SKILL_IDS)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> skillRevoke(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("points")
                        .executes(ctx -> skillPoints(ctx.getSource())))
                .then(Commands.literal("givepoints")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> givePoints(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoskill <list|unlock <id>|grant <id> <player>|revoke <id> <player>|points>"), false);
                    return 1;
                }));

        // --- Parallel quests ---
        d.register(Commands.literal("xenoquest")
                .requires(src -> src.hasPermission(0))
                .then(Commands.literal("list")
                        .executes(ctx -> questList(ctx.getSource())))
                .then(Commands.literal("start")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(QUEST_IDS)
                                .executes(ctx -> questStart(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("status")
                        .executes(ctx -> questStatus(ctx.getSource())))
                .then(Commands.literal("abort")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(ACTIVE_QUEST_IDS)
                                .executes(ctx -> questAbort(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("log")
                        .executes(ctx -> questLog(ctx.getSource())))
                .then(Commands.literal("progress")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("amount",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 32767))
                                                .executes(ctx -> questProgress(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                                                .getInteger(ctx, "amount")))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoquest <list|start <id>|status|abort <id>|log>"), false);
                    return 1;
                }));

        // --- Mentor ---
        d.register(Commands.literal("xenomentor")
                .requires(src -> src.hasPermission(0))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> mentorSet(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> mentorClear(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> mentorStatus(ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenomentor <set <player>|clear|status>"), false);
                    return 1;
                }));
    }

    // --- train ---
    private static int spawnDummy(CommandSourceStack src) {
        if (!XenoServerConfig.trainingDummyEnabled) {
            src.sendFailure(Component.literal("Training dummy is disabled on this server"));
            return 0;
        }
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(Component.literal("Players only"));
            return 0;
        }
        String err = net.bullettrain.xenopixelsmod.features.progression.TrainingDummySpawner.spawn(p);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "§eTraining Dummy (your ghost clone) spawned. Punch it — session damage on the action bar. /xenotrain dismiss"), true);
        return 1;
    }

    /** A V9-brain training NPC: melee only for dummytrain, melee and ki blasts for shadow. */
    private static int spawnTrainingNpc(CommandSourceStack src, int percent, boolean meleeOnly) {
        if (!XenoServerConfig.trainingDummyEnabled) {
            src.sendFailure(Component.literal("Training dummy is disabled on this server"));
            return 0;
        }
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(Component.literal("Players only"));
            return 0;
        }
        String err = net.bullettrain.xenopixelsmod.features.progression.TrainingNpc.spawn(p, percent,
                meleeOnly ? net.bullettrain.xenopixelsmod.features.progression.TrainingNpc.Kind.MELEE
                        : net.bullettrain.xenopixelsmod.features.progression.TrainingNpc.Kind.KI);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int spawnShadow(CommandSourceStack src, int percent) {
        return spawnShadow(src, percent, false);
    }

    private static int spawnShadow(CommandSourceStack src, int percent, boolean meleeOnly) {
        if (!XenoServerConfig.trainingDummyEnabled) {
            src.sendFailure(Component.literal("Training dummy is disabled on this server"));
            return 0;
        }
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(Component.literal("Players only"));
            return 0;
        }
        String err = net.bullettrain.xenopixelsmod.features.progression.ShadowDummyTraining
                .spawnAttackingClone(p, percent, meleeOnly);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(meleeOnly
                ? "§dTraining Fighter @ " + percent + "% — melee only. /xenotrain dismiss"
                : "§dShadow Dummy clone @ " + percent + "% — fights you. /xenotrain dismiss"), true);
        return 1;
    }

    private static int dismissShadow(CommandSourceStack src, boolean everyone) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        int n = net.bullettrain.xenopixelsmod.features.progression.TrainingDummySpawner.dismiss(p, 64, everyone);
        src.sendSuccess(() -> Component.literal("§7Dismissed " + n + " training dummy(ies)"), false);
        return 1;
    }

    private static int resetDummy(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        XenoCapabilities.get(p).ifPresent(XenoPlayerData::resetDummySession);
        src.sendSuccess(() -> Component.literal("§7Dummy session damage reset"), false);
        return 1;
    }

    private static int dummyStats(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        XenoCapabilities.get(p).ifPresent(data ->
                src.sendSuccess(() -> Component.literal(
                        "Dummy — session: " + data.getDummySessionDamage()
                                + " | hits: " + data.getDummyHits()
                                + " | lifetime: " + data.getDummyTotalDamage()), false));
        return 1;
    }

    // --- souls ---
    private static int soulList(CommandSourceStack src) {
        StringBuilder sb = new StringBuilder("Super Souls:\n");
        for (SuperSoulCatalog.SoulDef s : SuperSoulCatalog.SOULS.values()) {
            sb.append("  §d").append(s.id()).append(" §f").append(s.title())
                    .append(" §7— ").append(s.desc()).append('\n');
        }
        sb.append("Equip: /xenosoul equip <id>  or right-click Super Soul item");
        src.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    private static int soulEquip(CommandSourceStack src, String id) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        SuperSoulCatalog.SoulDef def = SuperSoulCatalog.get(id);
        if (def == null) {
            src.sendFailure(Component.literal("Unknown soul. /xenosoul list"));
            return 0;
        }
        XenoCapabilities.get(p).ifPresent(data -> {
            data.setSuperSoulId(def.id());
            src.sendSuccess(() -> Component.literal(
                    "§dEquipped: §f" + def.title() + " §7(" + def.desc() + ")"), true);
            net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.sync(p);
        });
        return 1;
    }

    private static int soulClear(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        XenoCapabilities.get(p).ifPresent(data -> {
            data.setSuperSoulId("");
            src.sendSuccess(() -> Component.literal("§7Super Soul cleared"), true);
            net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.sync(p);
        });
        return 1;
    }

    private static int soulGive(CommandSourceStack src, String id, ServerPlayer target) {
        if (target == null) {
            src.sendFailure(Component.literal("Players only"));
            return 0;
        }
        SuperSoulCatalog.SoulDef def = SuperSoulCatalog.get(id);
        if (def == null) {
            src.sendFailure(Component.literal("Unknown soul. /xenosoul list"));
            return 0;
        }
        net.minecraft.world.item.Item item = switch (def.id()) {
            case "warrior" -> net.bullettrain.xenopixelsmod.item.ModsItems.SUPER_SOUL_WARRIOR.get();
            case "iron" -> net.bullettrain.xenopixelsmod.item.ModsItems.SUPER_SOUL_IRON.get();
            case "spark" -> net.bullettrain.xenopixelsmod.item.ModsItems.SUPER_SOUL_SPARK.get();
            case "finisher" -> net.bullettrain.xenopixelsmod.item.ModsItems.SUPER_SOUL_FINISHER.get();
            case "balanced" -> net.bullettrain.xenopixelsmod.item.ModsItems.SUPER_SOUL_BALANCED.get();
            default -> null;
        };
        if (item == null) {
            src.sendFailure(Component.literal("Soul item is not registered"));
            return 0;
        }
        if (!target.addItem(new net.minecraft.world.item.ItemStack(item))) {
            target.drop(new net.minecraft.world.item.ItemStack(item), false);
        }
        src.sendSuccess(() -> Component.literal("Gave Super Soul: " + def.title()
                + " to " + target.getGameProfile().getName()), true);
        return 1;
    }

    private static int soulStatus(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        SuperSoulCatalog.SoulDef def = SuperSoulCatalog.equipped(p);
        if (def == null) {
            src.sendSuccess(() -> Component.literal("No Super Soul equipped"), false);
        } else {
            src.sendSuccess(() -> Component.literal(
                    "Equipped: " + def.title() + " — " + def.desc()), false);
        }
        return 1;
    }

    // --- skills ---
    private static int skillList(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        StringBuilder sb = new StringBuilder("Combat skills (max 3):\n");
        for (CombatSkills.SkillDef def : CombatSkills.DEFS.values()) {
            int lv = p != null ? CombatSkills.level(p, def.id()) : 0;
            sb.append("  §e").append(def.id()).append(" §fLv.").append(lv)
                    .append(" §7— ").append(def.title()).append(": ").append(def.desc()).append('\n');
        }
        if (p != null) {
            XenoCapabilities.get(p).ifPresent(data ->
                    sb.append("Skill points: §a").append(data.getSkillPoints()));
        }
        src.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    private static int skillUnlock(CommandSourceStack src, String id) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        String err = CombatSkills.tryUnlock(p, id);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        int lv = CombatSkills.level(p, id);
        CombatSkills.SkillDef def = CombatSkills.DEFS.get(id.toLowerCase());
        src.sendSuccess(() -> Component.literal(
                "§aUnlocked §f" + (def != null ? def.title() : id) + " §aLv." + lv), true);
        net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.sync(p);
        return 1;
    }

    private static int skillGrant(CommandSourceStack src, String id, ServerPlayer target) {
        String err = CombatSkills.grant(target, id);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Granted " + id + " to "
                + target.getGameProfile().getName()), true);
        return 1;
    }

    private static int skillRevoke(CommandSourceStack src, String id, ServerPlayer target) {
        String err = CombatSkills.revoke(target, id);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Revoked " + id + " from "
                + target.getGameProfile().getName()), true);
        return 1;
    }

    private static int skillPoints(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        XenoCapabilities.get(p).ifPresent(data ->
                src.sendSuccess(() -> Component.literal(
                        "Skill points: " + data.getSkillPoints()
                                + " (earn via /xenoquest)"), false));
        return 1;
    }

    private static int givePoints(CommandSourceStack src, ServerPlayer target, int amount) {
        XenoCapabilities.get(target).ifPresent(data -> {
            data.addSkillPoints(amount);
            src.sendSuccess(() -> Component.literal(
                    "Gave " + amount + " skill point(s) to " + target.getGameProfile().getName()), true);
            target.displayClientMessage(Component.literal("§a+" + amount + " skill point(s)"), false);
        });
        return 1;
    }

    // --- quests ---
    private static int questList(CommandSourceStack src) {
        StringBuilder sb = new StringBuilder("Parallel Quests:\n");
        for (String id : ParallelQuests.ids()) {
            ParallelQuests.QuestDef q = ParallelQuests.definition(id);
            if (q == null) {
                continue;
            }
            sb.append("  §b").append(q.id()).append(" §f").append(q.title())
                    .append(" §7— ").append(q.desc())
                    .append(" §8[").append(q.goal().type().name().toLowerCase(java.util.Locale.ROOT))
                    .append(q.goal().parameter().isBlank()
                            ? "" : " " + q.goal().parameter())
                    .append(" x").append(q.target()).append(']')
                    .append('\n');
        }
        // A pack quest that failed to load is worse than one that does not exist: the id is in the
        // dialogue that offers it, and the player would be told "unknown quest" with no clue why.
        var questErrors = net.bullettrain.xenopixelsmod.features.progression.XenoQuests.loadErrors();
        if (!questErrors.isEmpty()) {
            sb.append("§cFailed to load ").append(questErrors.size()).append(" quest(s):\n");
            for (String error : questErrors) {
                sb.append("  §c").append(error).append('\n');
            }
        }
        ServerPlayer player = src.getPlayer();
        if (player != null) {
            var npc = net.bullettrain.xenopixelsmod.compat.npc.NpcCnpcQuests.active(player);
            if (!npc.isEmpty()) {
                sb.append("CustomNPCs active:\n");
                for (var e : npc) {
                    sb.append("  §d").append(net.bullettrain.xenopixelsmod.compat.npc.NpcCnpcQuests.formatListLine(e))
                            .append('\n');
                }
            }
        }
        src.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    private static int questStart(CommandSourceStack src, String id) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        if (!XenoServerConfig.parallelQuestEnabled) {
            src.sendFailure(Component.literal("Quests disabled on this server"));
            return 0;
        }
        String err = ParallelQuests.start(p, id);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int questStatus(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        StringBuilder sb = new StringBuilder(ParallelQuests.status(p));
        var npc = net.bullettrain.xenopixelsmod.compat.npc.NpcCnpcQuests.active(p);
        for (var e : npc) {
            sb.append('\n').append("CNPC: ").append(
                    net.bullettrain.xenopixelsmod.compat.npc.NpcCnpcQuests.formatListLine(e));
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    /**
     * Drops one quest by id.
     *
     * <p>Takes an id now that a player can hold several. Without one it would have to guess which
     * to drop, and guessing wrong silently discards progress.
     */
    private static int questAbort(CommandSourceStack src, String id) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        XenoCapabilities.get(p).ifPresent(data -> {
            if (!data.quests().isActive(id)) {
                // Said out loud rather than passing silently, which would look like it worked.
                src.sendFailure(Component.literal("You are not on that quest."));
                return;
            }
            data.quests().abandon(id);
            net.bullettrain.xenopixelsmod.features.progression.QuestHunts.onQuestEnded(p, id);
            net.bullettrain.xenopixelsmod.features.progression.QuestSync.push(p);
            src.sendSuccess(() -> Component.literal("§7Quest aborted: §f" + id), false);
        });
        return 1;
    }

    private static int questProgress(CommandSourceStack src, ServerPlayer player, String id, int amount) {
        var data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return 0;
        var quest = data.quests().actives().stream()
                .filter(active -> active.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        if (quest == null) {
            src.sendFailure(Component.literal("That player is not on that quest."));
            return 0;
        }
        net.bullettrain.xenopixelsmod.features.progression.QuestRuntime.onManual(quest, amount);
        net.bullettrain.xenopixelsmod.features.progression.QuestSync.push(player);
        src.sendSuccess(() -> Component.literal("Manual progress recorded for " + id), false);
        return 1;
    }

    /** Quests this player has finished, which is what gates a master. */
    private static int questLog(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        XenoCapabilities.get(p).ifPresent(data -> {
            var done = data.quests().completed();
            if (done.isEmpty()) {
                src.sendSuccess(() -> Component.literal("No quests completed yet."), false);
                return;
            }
            StringBuilder sb = new StringBuilder("Completed quests:");
            for (String id : done) {
                sb.append("\n  §a").append(id);
            }
            src.sendSuccess(() -> Component.literal(sb.toString()), false);
        });
        return 1;
    }

    // --- mentor ---
    private static int mentorSet(CommandSourceStack src, ServerPlayer mentor) {
        ServerPlayer student = src.getPlayer();
        if (student == null) return 0;
        if (!XenoServerConfig.mentorEnabled) {
            src.sendFailure(Component.literal("Mentor system disabled"));
            return 0;
        }
        if (mentor.getUUID().equals(student.getUUID())) {
            src.sendFailure(Component.literal("You cannot mentor yourself"));
            return 0;
        }
        XenoCapabilities.get(student).ifPresent(data -> {
            data.setMentor(mentor.getUUID(), mentor.getGameProfile().getName());
            src.sendSuccess(() -> Component.literal(
                    "§aMentor set to §f" + mentor.getGameProfile().getName()
                            + " §7(they gain sparking meter when you fight nearby)"), true);
            mentor.displayClientMessage(Component.literal(
                    "§aYou are now mentoring §f" + student.getGameProfile().getName()), false);
        });
        return 1;
    }

    private static int mentorClear(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        XenoCapabilities.get(p).ifPresent(data -> {
            data.clearMentor();
            src.sendSuccess(() -> Component.literal("§7Mentor cleared"), false);
        });
        return 1;
    }

    private static int mentorStatus(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        XenoCapabilities.get(p).ifPresent(data -> {
            if (data.getMentorUuid() == null) {
                src.sendSuccess(() -> Component.literal("No mentor. /xenomentor set <player>"), false);
            } else {
                src.sendSuccess(() -> Component.literal(
                        "Mentor: " + data.getMentorName()), false);
            }
        });
        return 1;
    }
}
