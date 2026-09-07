package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.compat.util.LazyOptional;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatLimiter;
import net.bullettrain.xenopixelsmod.combat.HakaiChannelSystem;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Runtime control for the Hakai erasure technique.
 * <pre>
 * /xenohakai status
 * /xenohakai toggle &lt;true|false&gt;
 * /xenohakai kicost &lt;0-1000&gt;
 * /xenohakai range &lt;1-64&gt;
 * /xenohakai cooldown &lt;40-2400&gt;
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class HakaiCommands {

    private HakaiCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenohakai")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("toggle")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setToggle(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("kicost")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .then(Commands.argument("cost", FloatArgumentType.floatArg(0.0f, 1000.0f))
                                .executes(ctx -> setKiCost(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "cost")))))
                .then(Commands.literal("range")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(1.0f, 64.0f))
                                .executes(ctx -> setRange(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "blocks")))))
                .then(Commands.literal("cooldown")
                        .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(40, 2400))
                                .executes(ctx -> setCooldown(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(Commands.literal("use")
                        .requires(src -> src.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> useHakai(ctx.getSource(), false))
                        .then(Commands.literal("force")
                                .requires(XenoPermissions.require(XenoPermissions.HAKAI_SET))
                                .executes(ctx -> useHakai(ctx.getSource(), true))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenohakai use [force] | toggle <true|false> | kicost <0-1000>"
                                    + " | range <1-64> | cooldown <40-2400>\n"
                                    + currentLine()), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int setToggle(CommandSourceStack source, boolean enabled) {
        XenoServerConfig.setHakaiEnabled(enabled);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Hakai: " + (enabled ? "enabled" : "disabled")), true);
        return 1;
    }

    private static int setKiCost(CommandSourceStack source, float cost) {
        XenoServerConfig.setHakaiKiCost(cost);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Hakai ki cost: " + XenoServerConfig.hakaiKiCost), true);
        return 1;
    }

    private static int setRange(CommandSourceStack source, double blocks) {
        XenoServerConfig.setHakaiMaxRange(blocks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Hakai range: " + XenoServerConfig.hakaiMaxRange + " blocks"), true);
        return 1;
    }

    private static int setCooldown(CommandSourceStack source, int ticks) {
        XenoServerConfig.setHakaiCooldownTicks(ticks);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                "Hakai cooldown: " + XenoServerConfig.hakaiCooldownTicks + " ticks"), true);
        return 1;
    }

    /**
     * Server-side Hakai that does not need the J key. Prints every gate so a miss is never silent.
     * {@code force} skips unlock, permission, and cooldown.
     */
    private static int useHakai(CommandSourceStack source, boolean force) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only"));
            return 0;
        }
        LivingEntity target = HakaiChannelSystem.findLookTarget(player, XenoServerConfig.hakaiMaxRange);
        StringBuilder report = new StringBuilder();
        line(report, "target", target != null
                ? target.getName().getString() + " #" + target.getId()
                : "NONE — look at a living entity");
        line(report, "enabled", XenoServerConfig.hakaiEnabled);
        boolean perm = XenoPermissions.hasPermission(source, XenoPermissions.HAKAI_USE);
        line(report, "permission", perm);
        boolean unlocked = CombatSkills.hakaiUnlocked(player);
        line(report, "unlocked", unlocked);
        boolean inRange = target != null && player.distanceTo(target) <= XenoServerConfig.hakaiMaxRange;
        line(report, "range", inRange);
        boolean ready = Bt3CombatLimiter.hakaiReady(player);
        line(report, "cooldown", ready);
        LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
        StatsData data = opt.orElse(null);
        Resources res = data != null ? data.getResources() : null;
        boolean ki = res != null && res.getCurrentEnergy() >= XenoServerConfig.hakaiKiCost;
        line(report, "ki", ki ? String.format("%.0f >= %.0f", res.getCurrentEnergy(), XenoServerConfig.hakaiKiCost)
                : "fail");
        boolean los = target != null && HakaiChannelSystem.hasLineOfSight(player, target);
        line(report, "los", los);

        source.sendSuccess(() -> Component.literal(report.toString().trim()), false);

        if (target == null) {
            source.sendFailure(Component.literal("Hakai: no look target"));
            return 0;
        }
        if (!XenoServerConfig.hakaiEnabled) {
            source.sendFailure(Component.literal("Hakai is disabled"));
            return 0;
        }
        if (!force && !perm) {
            source.sendFailure(Component.literal("No hakai.use permission"));
            return 0;
        }
        if (!force && !unlocked) {
            source.sendFailure(Component.literal("Not unlocked — /xenoskill unlock hakai"));
            return 0;
        }
        if (!inRange) {
            source.sendFailure(Component.literal("Too far"));
            return 0;
        }
        if (!force && !ready) {
            source.sendFailure(Component.literal("Cooling down"));
            return 0;
        }
        if (!force && !ki) {
            source.sendFailure(Component.literal("Not enough KI"));
            return 0;
        }
        HakaiChannelSystem.start(player, target, true, force);
        source.sendSuccess(() -> Component.literal("§dHakai channel started on "
                + target.getName().getString()), true);
        return 1;
    }

    private static void line(StringBuilder out, String name, boolean ok) {
        line(out, name, ok ? "pass" : "FAIL");
    }

    private static void line(StringBuilder out, String name, String value) {
        out.append("§7").append(name).append("=§")
                .append("FAIL".equals(value) || "NONE".equals(value) || value.startsWith("NONE") ? "c" : "a")
                .append(value).append("§r\n");
    }

    private static String currentLine() {
        return "hakai enabled=" + XenoServerConfig.hakaiEnabled
                + " kicost=" + XenoServerConfig.hakaiKiCost
                + " range=" + XenoServerConfig.hakaiMaxRange + " blocks"
                + " cooldown=" + XenoServerConfig.hakaiCooldownTicks + " ticks";
    }
}
