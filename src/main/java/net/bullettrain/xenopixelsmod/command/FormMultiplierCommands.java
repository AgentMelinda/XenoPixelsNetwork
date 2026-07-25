package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Public-server form power scales — overall and per combat stat (STR/PWR/DEF/…).
 *
 * <pre>
 * /xenoform set global 2
 * /xenoform set ssb 3
 * /xenoform set global str 5
 * /xenoform set ssb pwr 10
 * /xenoform get ssb str
 * /xenoform list
 * /xenoform clear ssb str
 * </pre>
 *
 * Tab-completes forms and stats. Range 0–1,000,000.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class FormMultiplierCommands {
    private static final SuggestionProvider<CommandSourceStack> FORM_OR_GLOBAL =
            FormMultiplierCommands::suggestFormsOrGlobal;
    private static final SuggestionProvider<CommandSourceStack> STAT_SUGGESTIONS =
            FormMultiplierCommands::suggestStats;
    private static final SuggestionProvider<CommandSourceStack> CLEAR_SUGGESTIONS =
            FormMultiplierCommands::suggestClearTargets;
    private static final SuggestionProvider<CommandSourceStack> CLEAR_STAT_SUGGESTIONS =
            FormMultiplierCommands::suggestStats;

    private FormMultiplierCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var multArg = Commands.argument("multiplier", FloatArgumentType.floatArg(
                XenoServerConfig.FORM_STAT_MULT_MIN,
                XenoServerConfig.FORM_STAT_MULT_MAX));

        // set <form|global> <mult>
        // set <form|global> <stat> <mult>
        var setBranch = Commands.literal("set")
                .requires(XenoPermissions.require(XenoPermissions.XENOFORM_SET))
                .then(Commands.argument("form", StringArgumentType.string())
                        .suggests(FORM_OR_GLOBAL)
                        .then(multArg
                                .executes(ctx -> setOverall(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "form"),
                                        FloatArgumentType.getFloat(ctx, "multiplier"))))
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests(STAT_SUGGESTIONS)
                                .then(Commands.argument("multiplier", FloatArgumentType.floatArg(
                                                XenoServerConfig.FORM_STAT_MULT_MIN,
                                                XenoServerConfig.FORM_STAT_MULT_MAX))
                                        .executes(ctx -> setStat(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "form"),
                                                StringArgumentType.getString(ctx, "stat"),
                                                FloatArgumentType.getFloat(ctx, "multiplier"))))));

        // get <form|global>
        // get <form|global> <stat>
        var getBranch = Commands.literal("get")
                .requires(XenoPermissions.require(XenoPermissions.XENOFORM_STATUS))
                .then(Commands.argument("form", StringArgumentType.string())
                        .suggests(FORM_OR_GLOBAL)
                        .executes(ctx -> getOverall(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "form")))
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests(STAT_SUGGESTIONS)
                                .executes(ctx -> getStat(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "form"),
                                        StringArgumentType.getString(ctx, "stat")))))
                .executes(ctx -> status(ctx.getSource()));

        // clear all | clear <form> | clear <form> <stat> | clear global <stat>
        var clearBranch = Commands.literal("clear")
                .requires(XenoPermissions.require(XenoPermissions.XENOFORM_SET))
                .then(Commands.argument("form", StringArgumentType.string())
                        .suggests(CLEAR_SUGGESTIONS)
                        .executes(ctx -> clearOverall(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "form")))
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests(CLEAR_STAT_SUGGESTIONS)
                                .executes(ctx -> clearStat(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "form"),
                                        StringArgumentType.getString(ctx, "stat")))));

        dispatcher.register(Commands.literal("xenoform")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOFORM_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("list")
                        .requires(XenoPermissions.require(XenoPermissions.XENOFORM_STATUS))
                        .executes(ctx -> listOverrides(ctx.getSource())))
                .then(Commands.literal("stats")
                        .requires(XenoPermissions.require(XenoPermissions.XENOFORM_STATUS))
                        .executes(ctx -> listStats(ctx.getSource())))
                .then(getBranch)
                .then(setBranch)
                .then(clearBranch)
                .executes(ctx -> help(ctx.getSource())));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                        "Form power scales (bonus only: 1+(formMult-1)×scale)\n"
                                + "  /xenoform set global <mult>           — all forms overall\n"
                                + "  /xenoform set <form> <mult>           — one form overall\n"
                                + "  /xenoform set global <stat> <mult>    — one stat all forms (str/pwr/def…)\n"
                                + "  /xenoform set <form> <stat> <mult>    — one stat on one form\n"
                                + "  /xenoform get <form|global> [stat]\n"
                                + "  /xenoform list | stats | clear <form|all> [stat]\n"
                                + "Tab-complete forms + stats. Range 0–1000000. Global: "
                                + formatMult(XenoServerConfig.formStatMultiplier)),
                false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        int forms = XenoServerConfig.formPerFormMultipliers.size();
        int stats = XenoServerConfig.formPerStatMultipliers.size();
        int formStats = 0;
        for (Map<String, Float> m : XenoServerConfig.formPerFormStatMultipliers.values()) {
            formStats += m.size();
        }
        int fs = formStats;
        source.sendSuccess(() -> Component.literal(
                        "Form power — global overall: " + formatMult(XenoServerConfig.formStatMultiplier)
                                + "\n  per-form overall: " + forms
                                + "  |  global per-stat: " + stats
                                + "  |  form×stat: " + fs
                                + "\nStats: str skp stm def vit pwr ene speed"
                                + "\nExample: /xenoform set ssb str 5"),
                false);
        return 1;
    }

    private static int listStats(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                        "Combat stats you can scale:\n"
                                + "  str  — strength / melee\n"
                                + "  pwr  — power (ki damage)\n"
                                + "  def  — defense\n"
                                + "  skp  — strike / skill power\n"
                                + "  stm  — stamina\n"
                                + "  vit  — vitality / HP\n"
                                + "  ene  — energy pool\n"
                                + "  speed — move / flight\n"
                                + "Aliases: power→pwr, strength→str, defense→def, stamina→stm, flight→speed"),
                false);
        return 1;
    }

    private static int listOverrides(CommandSourceStack source) {
        StringBuilder sb = new StringBuilder();
        sb.append("Global overall: ").append(formatMult(XenoServerConfig.formStatMultiplier)).append('\n');

        if (!XenoServerConfig.formPerStatMultipliers.isEmpty()) {
            sb.append("Global per-stat:\n");
            List<String> keys = new ArrayList<>(XenoServerConfig.formPerStatMultipliers.keySet());
            keys.sort(String::compareToIgnoreCase);
            for (String k : keys) {
                sb.append("  ").append(k).append(" = ")
                        .append(formatMult(XenoServerConfig.formPerStatMultipliers.get(k))).append('\n');
            }
        }

        if (!XenoServerConfig.formPerFormMultipliers.isEmpty()) {
            sb.append("Per-form overall:\n");
            List<String> keys = new ArrayList<>(XenoServerConfig.formPerFormMultipliers.keySet());
            keys.sort(String::compareToIgnoreCase);
            int n = 0;
            for (String k : keys) {
                if (n++ >= 30) {
                    sb.append("  … +").append(keys.size() - 30).append(" more\n");
                    break;
                }
                sb.append("  ").append(k).append(" = ")
                        .append(formatMult(XenoServerConfig.formPerFormMultipliers.get(k))).append('\n');
            }
        }

        if (!XenoServerConfig.formPerFormStatMultipliers.isEmpty()) {
            sb.append("Per-form per-stat:\n");
            int n = 0;
            List<String> forms = new ArrayList<>(XenoServerConfig.formPerFormStatMultipliers.keySet());
            forms.sort(String::compareToIgnoreCase);
            outer:
            for (String form : forms) {
                Map<String, Float> inner = XenoServerConfig.formPerFormStatMultipliers.get(form);
                if (inner == null) continue;
                List<String> stats = new ArrayList<>(inner.keySet());
                stats.sort(String::compareToIgnoreCase);
                for (String st : stats) {
                    if (n++ >= 40) {
                        sb.append("  … more\n");
                        break outer;
                    }
                    sb.append("  ").append(form).append('.').append(st).append(" = ")
                            .append(formatMult(inner.get(st))).append('\n');
                }
            }
        }

        if (sb.toString().equals("Global overall: " + formatMult(XenoServerConfig.formStatMultiplier) + "\n")) {
            sb.append("No overrides yet. Try: /xenoform set global str 2");
        }
        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    private static int getOverall(CommandSourceStack source, String formArg) {
        if (isGlobalToken(formArg)) {
            source.sendSuccess(() -> Component.literal(
                            "global overall = " + formatMult(XenoServerConfig.formStatMultiplier)
                                    + "\n  (use /xenoform get global str for per-stat)"),
                    false);
            return 1;
        }
        float scale = XenoServerConfig.formScaleFor(formArg);
        source.sendSuccess(() -> Component.literal(
                        formArg + " overall effective = " + formatMult(scale)
                                + "\n  example form STR 5.7 → "
                                + formatMult((float) XenoServerConfig.scaleFormMultiplier(5.7, formArg, "str"))),
                false);
        return 1;
    }

    private static int getStat(CommandSourceStack source, String formArg, String statArg) {
        String stat = XenoServerConfig.normalizeStatKey(statArg);
        if (!XenoServerConfig.isKnownFormStat(stat)) {
            source.sendFailure(Component.literal("Unknown stat '" + statArg + "'. /xenoform stats"));
            return 0;
        }
        String form = isGlobalToken(formArg) ? null : formArg;
        float scale = XenoServerConfig.formScaleFor(form, stat);
        source.sendSuccess(() -> Component.literal(
                        (form == null ? "global" : formArg) + " " + stat + " effective scale = "
                                + formatMult(scale)
                                + "\n  example form " + stat.toUpperCase(Locale.ROOT) + " 5.7 → "
                                + formatMult((float) XenoServerConfig.scaleFormMultiplier(5.7, form, stat))),
                false);
        return 1;
    }

    private static int setOverall(CommandSourceStack source, String formArg, float value) {
        float clamped = XenoServerConfig.clampFormStatMultiplier(value);
        if (isGlobalToken(formArg)) {
            XenoServerConfig.setFormStatMultiplier(clamped);
            DmzHudCommands.broadcast();
            source.sendSuccess(() -> Component.literal(
                            "Global form overall scale = " + formatMult(clamped) + " (saved + synced)"),
                    true);
            return 1;
        }
        String key = XenoServerConfig.normalizeFormKey(formArg);
        XenoServerConfig.setPerFormMultiplier(key, clamped);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                        "Form " + key + " overall scale = " + formatMult(clamped) + " (saved + synced)"),
                true);
        XenoPixelsMod.LOGGER.info("Form {} overall scale {} by {}", key, clamped, source.getTextName());
        return 1;
    }

    private static int setStat(CommandSourceStack source, String formArg, String statArg, float value) {
        String stat = XenoServerConfig.normalizeStatKey(statArg);
        if (!XenoServerConfig.isKnownFormStat(stat)) {
            source.sendFailure(Component.literal("Unknown stat '" + statArg + "'. /xenoform stats"));
            return 0;
        }
        float clamped = XenoServerConfig.clampFormStatMultiplier(value);
        if (isGlobalToken(formArg)) {
            XenoServerConfig.setPerStatMultiplier(stat, clamped);
            DmzHudCommands.broadcast();
            source.sendSuccess(() -> Component.literal(
                            "Global " + stat + " scale = " + formatMult(clamped)
                                    + " (all forms, saved + synced)"),
                    true);
            return 1;
        }
        String key = XenoServerConfig.normalizeFormKey(formArg);
        XenoServerConfig.setPerFormStatMultiplier(key, stat, clamped);
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(
                        key + " " + stat + " scale = " + formatMult(clamped) + " (saved + synced)"),
                true);
        XenoPixelsMod.LOGGER.info("Form {} stat {} scale {} by {}", key, stat, clamped, source.getTextName());
        return 1;
    }

    private static int clearOverall(CommandSourceStack source, String formArg) {
        if ("all".equalsIgnoreCase(formArg.trim()) || "*".equals(formArg.trim())) {
            XenoServerConfig.clearAllPerFormMultipliers();
            DmzHudCommands.broadcast();
            source.sendSuccess(() -> Component.literal(
                            "Cleared all form/stat overrides. Global overall remains "
                                    + formatMult(XenoServerConfig.formStatMultiplier)),
                    true);
            return 1;
        }
        if (isGlobalToken(formArg)) {
            source.sendFailure(Component.literal(
                    "Use /xenoform set global <mult> to change global overall, or /xenoform clear global <stat>"));
            return 0;
        }
        String key = XenoServerConfig.normalizeFormKey(formArg);
        boolean ok = XenoServerConfig.clearPerFormMultiplier(key);
        if (!ok) {
            for (String stored : new ArrayList<>(XenoServerConfig.formPerFormMultipliers.keySet())) {
                if (stored.endsWith("." + key) || key.endsWith("." + stored)) {
                    ok = XenoServerConfig.clearPerFormMultiplier(stored);
                    if (ok) {
                        key = stored;
                        break;
                    }
                }
            }
        }
        // also clear form×stat for this form
        Map<String, Float> inner = XenoServerConfig.formPerFormStatMultipliers.remove(key);
        if (inner != null && !inner.isEmpty()) {
            ok = true;
            XenoServerConfig.save();
        }
        if (!ok) {
            source.sendFailure(Component.literal("No overall override for '" + formArg + "'. /xenoform list"));
            return 0;
        }
        DmzHudCommands.broadcast();
        String cleared = key;
        source.sendSuccess(() -> Component.literal("Cleared overall (+ form×stat) for " + cleared), true);
        return 1;
    }

    private static int clearStat(CommandSourceStack source, String formArg, String statArg) {
        String stat = XenoServerConfig.normalizeStatKey(statArg);
        if (!XenoServerConfig.isKnownFormStat(stat)) {
            source.sendFailure(Component.literal("Unknown stat '" + statArg + "'. /xenoform stats"));
            return 0;
        }
        boolean ok;
        if (isGlobalToken(formArg)) {
            ok = XenoServerConfig.clearPerStatMultiplier(stat);
            if (!ok) {
                source.sendFailure(Component.literal("No global " + stat + " override"));
                return 0;
            }
            DmzHudCommands.broadcast();
            source.sendSuccess(() -> Component.literal("Cleared global " + stat + " scale"), true);
            return 1;
        }
        String key = XenoServerConfig.normalizeFormKey(formArg);
        ok = XenoServerConfig.clearPerFormStatMultiplier(key, stat);
        if (!ok) {
            source.sendFailure(Component.literal("No " + key + " " + stat + " override"));
            return 0;
        }
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal("Cleared " + key + " " + stat + " scale"), true);
        return 1;
    }

    private static boolean isGlobalToken(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return "global".equals(t) || "*".equals(t) || "default".equals(t) || "allforms".equals(t);
    }

    // --- suggestions ----------------------------------------------------------

    private static CompletableFuture<Suggestions> suggestFormsOrGlobal(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> opts = new ArrayList<>();
        opts.add("global");
        opts.addAll(collectFormIds());
        return SharedSuggestionProvider.suggest(opts, builder);
    }

    private static CompletableFuture<Suggestions> suggestStats(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.asList(XenoServerConfig.FORM_STAT_KEYS), builder);
    }

    private static CompletableFuture<Suggestions> suggestClearTargets(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> opts = new ArrayList<>();
        opts.add("all");
        opts.add("global");
        opts.addAll(XenoServerConfig.formPerFormMultipliers.keySet());
        opts.addAll(XenoServerConfig.formPerFormStatMultipliers.keySet());
        opts.addAll(collectFormIds());
        return SharedSuggestionProvider.suggest(opts, builder);
    }

    public static List<String> collectFormIds() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            Map<String, Map<String, FormConfig>> all = ConfigManager.getAllForms();
            if (all != null) {
                for (Map<String, FormConfig> raceForms : all.values()) {
                    if (raceForms == null) continue;
                    for (FormConfig group : raceForms.values()) {
                        if (group == null) continue;
                        String g = group.getGroupName();
                        if (g == null || g.isBlank()) continue;
                        Map<String, FormConfig.FormData> forms = group.getForms();
                        if (forms == null) continue;
                        for (String formId : forms.keySet()) {
                            if (formId == null || formId.isBlank()) continue;
                            ids.add(g + "." + formId);
                            ids.add(formId);
                        }
                    }
                }
            }
            Map<String, FormConfig> stacks = ConfigManager.getAllStackForms();
            if (stacks != null) {
                for (FormConfig group : stacks.values()) {
                    if (group == null) continue;
                    String g = group.getGroupName();
                    if (g == null || g.isBlank()) continue;
                    Map<String, FormConfig.FormData> forms = group.getForms();
                    if (forms == null) continue;
                    for (String formId : forms.keySet()) {
                        if (formId == null || formId.isBlank()) continue;
                        ids.add(g + "." + formId);
                        ids.add(formId);
                    }
                }
            }
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.debug("Form id suggestions unavailable: {}", t.toString());
        }
        ids.addAll(XenoServerConfig.formPerFormMultipliers.keySet());
        ids.addAll(XenoServerConfig.formPerFormStatMultipliers.keySet());
        List<String> list = new ArrayList<>(ids);
        list.sort(String::compareToIgnoreCase);
        return list;
    }

    private static String formatMult(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return "1";
        if (Math.abs(v - Math.round(v)) < 0.0001f) {
            return String.valueOf(Math.round(v));
        }
        if (Math.abs(v) >= 1000f) {
            return String.format(Locale.US, "%.1f", v);
        }
        return String.format(Locale.US, "%.3f", v);
    }
}
