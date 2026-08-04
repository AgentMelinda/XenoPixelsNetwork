package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
import net.bullettrain.xenopixelsmod.features.progression.ProgressionEvents;
import net.bullettrain.xenopixelsmod.features.progression.SuperSoulCatalog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Phase 3 public commands:
 * <pre>
 * /xenotrain dummy|reset|stats
 * /xenosoul list|equip &lt;id&gt;|clear|status
 * /xenoskill list|unlock &lt;id&gt;|points
 * /xenoquest list|start &lt;id&gt;|status|abort
 * /xenomentor set &lt;player&gt;|clear|status
 * </pre>
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ProgressionCommands {
    private ProgressionCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
        PartyCommands.register(event.getDispatcher());
    }

    private static final SuggestionProvider<CommandSourceStack> SOUL_IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(SuperSoulCatalog.SOULS.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> SKILL_IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(CombatSkills.DEFS.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> QUEST_IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(ParallelQuests.QUESTS.keySet(), b);

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        // --- Training dummy ---
        d.register(Commands.literal("xenotrain")
                .requires(src -> src.hasPermission(0))
                .then(Commands.literal("dummy")
                        .executes(ctx -> spawnDummy(ctx.getSource())))
                .then(Commands.literal("shadow")
                        .executes(ctx -> spawnShadow(ctx.getSource(), 75))
                        .then(Commands.argument("percent",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(25, 100))
                                .executes(ctx -> spawnShadow(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "percent")))))
                .then(Commands.literal("dismiss")
                        .executes(ctx -> dismissShadow(ctx.getSource())))
                .then(Commands.literal("reset")
                        .executes(ctx -> resetDummy(ctx.getSource())))
                .then(Commands.literal("stats")
                        .executes(ctx -> dummyStats(ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenotrain <dummy|shadow [25-100]|dismiss|reset|stats>"), false);
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
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenosoul <list|equip <id>|clear|status>"), false);
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
                            "Usage: /xenoskill <list|unlock <id>|points>"), false);
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
                        .executes(ctx -> questAbort(ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoquest <list|start <id>|status|abort>"), false);
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
        Level level = p.level();
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            src.sendFailure(Component.literal("Failed to create dummy"));
            return 0;
        }
        stand.moveTo(p.getX() + p.getLookAngle().x * 2.5, p.getY(), p.getZ() + p.getLookAngle().z * 2.5,
                p.getYRot(), 0);
        stand.setInvulnerable(false);
        stand.setInvisible(false);
        stand.setNoGravity(false);
        stand.setShowArms(true);
        stand.setNoBasePlate(false);
        stand.setHealth(stand.getMaxHealth());
        ProgressionEvents.tagAsDummy(stand);
        level.addFreshEntity(stand);
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(XenoPlayerData::resetDummySession);
        src.sendSuccess(() -> Component.literal(
                "§eTraining Dummy spawned. Punch it — session damage shows on action bar. /xenotrain reset"), true);
        return 1;
    }

    private static int spawnShadow(CommandSourceStack src, int percent) {
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
                .spawnAttackingClone(p, percent);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "§dShadow Dummy clone @ " + percent + "% — fights you. /xenotrain dismiss"), true);
        return 1;
    }

    private static int dismissShadow(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        int n = net.bullettrain.xenopixelsmod.features.progression.ShadowDummyTraining.dismissNearby(p, 64);
        src.sendSuccess(() -> Component.literal("§7Dismissed " + n + " training shadow dummy(ies)"), false);
        return 1;
    }

    private static int resetDummy(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(XenoPlayerData::resetDummySession);
        src.sendSuccess(() -> Component.literal("§7Dummy session damage reset"), false);
        return 1;
    }

    private static int dummyStats(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data ->
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
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
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
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
            data.setSuperSoulId("");
            src.sendSuccess(() -> Component.literal("§7Super Soul cleared"), true);
            net.bullettrain.xenopixelsmod.effect.XenoStatusEffectSync.sync(p);
        });
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
            p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data ->
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

    private static int skillPoints(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data ->
                src.sendSuccess(() -> Component.literal(
                        "Skill points: " + data.getSkillPoints()
                                + " (earn via /xenoquest or dummy milestones)"), false));
        return 1;
    }

    private static int givePoints(CommandSourceStack src, ServerPlayer target, int amount) {
        target.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
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
        for (ParallelQuests.QuestDef q : ParallelQuests.QUESTS.values()) {
            sb.append("  §b").append(q.id()).append(" §f").append(q.title())
                    .append(" §7— ").append(q.desc()).append('\n');
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
        src.sendSuccess(() -> Component.literal(ParallelQuests.status(p)), false);
        return 1;
    }

    private static int questAbort(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
            data.clearQuest();
            src.sendSuccess(() -> Component.literal("§7Quest aborted"), false);
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
        student.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
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
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
            data.clearMentor();
            src.sendSuccess(() -> Component.literal("§7Mentor cleared"), false);
        });
        return 1;
    }

    private static int mentorStatus(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        p.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
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
