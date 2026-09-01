package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraFx;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDisplayApply;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTransformSystem;
import net.bullettrain.xenopixelsmod.compat.npc.PredefinedTechniqueLookup;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.OptionalInt;

/**
 * Script-facing entry point for the My NPCs / CustomNPCs race-stats-ki-attack bridge (see
 * {@code compat.npc}).
 *
 * <p>CustomNPCs (Nashorn) does <em>not</em> bind a global {@code npc}. It {@code eval}s the
 * tab as top-level JS, then {@code invokeFunction}s {@code init}/{@code tick}/{@code meleeAttack}/…
 * with an {@code NpcEvent} whose public field is {@code event.npc} ({@code ICustomNpc}).
 * {@code ICustomNpc.executeCommand(String)} then runs the command with that NPC as
 * {@link CommandSourceStack#getEntity()} (command blocks must be enabled). Example:
 * <pre>{@code
 * function init(event) {
 *     event.npc.executeCommand("xenopixels npcprofile set human 10 20 10 10 20 10");
 *     event.npc.executeCommand("xenopixels npcprofile color FF3300");
 *     event.npc.executeCommand("xenopixels npcprofile aura color FFAA00");
 *     event.npc.executeCommand("xenopixels npcprofile aura on");
 *     event.npc.executeCommand("xenopixels npcprofile form saiyan ssj");
 *     event.npc.executeCommand("xenopixels npcprofile charge 100");
 *     event.npc.executeCommand("xenopixels npcprofile tech add kamehameha");
 *     event.npc.executeCommand("xenopixels npcprofile skin player Notch");
 * }
 * function damaged(event) {
 *     event.npc.executeCommand("xenopixels npcprofile kiattack kiblast");
 * }
 * }</pre>
 *
 * <p>HEX accepts {@code FF00AA}, {@code 0xFF00AA}, or quoted {@code "#FF00AA"}.
 * {@code aura on} draws DragonMineZ's real aura shader mesh ({@code kakarot_aura}).
 * DMZ's player layer cannot attach to CustomNPCs, so the same shader/texture is
 * drawn on the NPC from the client.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcProfileCommands {
    private NpcProfileCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenopixels")
                .then(Commands.literal("npcprofile")
                        .then(Commands.literal("set")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .then(Commands.argument("raceId", StringArgumentType.word())
                                        .then(Commands.argument("strength", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("strikePower", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("resistance", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("vitality", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("kiPower", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("energy", IntegerArgumentType.integer(0))
                                                .executes(NpcProfileCommands::setProfile)))))))))
                        .then(Commands.literal("kiattack")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_KIATTACK))
                                .then(Commands.argument("blastType", StringArgumentType.word())
                                        .executes(NpcProfileCommands::kiAttack)
                                        .then(Commands.literal("color")
                                                .then(Commands.argument("hex", StringArgumentType.word())
                                                        .executes(NpcProfileCommands::kiAttack)))
                                        .then(Commands.argument("durationTicks", IntegerArgumentType.integer(1))
                                                .executes(NpcProfileCommands::kiAttack)
                                                .then(Commands.literal("color")
                                                        .then(Commands.argument("hex", StringArgumentType.word())
                                                                .executes(NpcProfileCommands::kiAttack)))
                                                .then(Commands.argument("lookAt", EntityArgument.entity())
                                                        .executes(NpcProfileCommands::kiAttack)
                                                        .then(Commands.literal("color")
                                                                .then(Commands.argument("hex", StringArgumentType.word())
                                                                        .executes(NpcProfileCommands::kiAttack)))
                                                        .then(Commands.argument("hex", StringArgumentType.word())
                                                                .executes(NpcProfileCommands::kiAttack))))))
                        .then(Commands.literal("color")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .then(Commands.argument("hex", StringArgumentType.word())
                                        .executes(NpcProfileCommands::setKiColor)))
                        .then(Commands.literal("aura")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .executes(NpcProfileCommands::auraStatus)
                                .then(Commands.literal("on").executes(ctx -> setAura(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setAura(ctx, false)))
                                .then(Commands.literal("toggle").executes(NpcProfileCommands::toggleAura))
                                .then(Commands.literal("color")
                                        .then(Commands.argument("hex", StringArgumentType.word())
                                                .executes(NpcProfileCommands::setAuraColor)))
                                .then(Commands.literal("scale")
                                        .then(Commands.argument("factor", FloatArgumentType.floatArg(0.25f, 10.0f))
                                                .executes(NpcProfileCommands::setAuraScale))))
                        .then(Commands.literal("form")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .then(Commands.argument("group", StringArgumentType.word())
                                        .then(Commands.argument("form", StringArgumentType.word())
                                                .executes(NpcProfileCommands::setForm))))
                        .then(Commands.literal("transform")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .then(Commands.argument("group", StringArgumentType.word())
                                        .then(Commands.argument("form", StringArgumentType.word())
                                                .executes(NpcProfileCommands::transform)
                                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200))
                                                        .executes(NpcProfileCommands::transform)))))
                        .then(Commands.literal("descend")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .executes(NpcProfileCommands::descend))
                        .then(Commands.literal("charge")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_KIATTACK))
                                .then(Commands.argument("percent", IntegerArgumentType.integer(1, 1000))
                                        .executes(NpcProfileCommands::setCharge)))
                        .then(Commands.literal("mastery")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .then(Commands.argument("group", StringArgumentType.word())
                                        .then(Commands.argument("form", StringArgumentType.word())
                                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                        .executes(NpcProfileCommands::setMastery)))))
                        .then(Commands.literal("tech")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_KIATTACK))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(NpcProfileCommands::techAdd)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(NpcProfileCommands::techRemove)))
                                .then(Commands.literal("fire")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(NpcProfileCommands::techFire)
                                                .then(Commands.argument("lookAt", EntityArgument.entity())
                                                        .executes(NpcProfileCommands::techFire))))
                                .then(Commands.literal("list").executes(NpcProfileCommands::techList)))
                        .then(Commands.literal("skin")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(NpcProfileCommands::setSkinPlayer)))
                                .then(Commands.literal("url")
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(NpcProfileCommands::setSkinUrl))))
                        .then(Commands.literal("hair")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .executes(NpcProfileCommands::hairStatus)
                                .then(Commands.literal("on").executes(ctx -> setHairEnabled(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setHairEnabled(ctx, false)))
                                .then(Commands.literal("code")
                                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                                .executes(NpcProfileCommands::setHairCode)))
                                .then(Commands.literal("color")
                                        .then(Commands.literal("clear")
                                                .executes(ctx -> setHairColor(ctx, "")))
                                        .then(Commands.argument("hairHex", StringArgumentType.word())
                                                .executes(ctx -> setHairColor(ctx,
                                                        StringArgumentType.getString(ctx, "hairHex"))))))
                        .then(Commands.literal("ai")
                                .requires(XenoPermissions.require(XenoPermissions.NPCPROFILE_SET))
                                .then(Commands.literal("enable").executes(NpcProfileCommands::enableAi))))
                .then(Commands.literal("npcsay")
                        .requires(XenoPermissions.require(XenoPermissions.NPCSAY_TOGGLE))
                        .executes(ctx -> npcsayStatus(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> setNpcSay(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setNpcSay(ctx.getSource(), false)))
                        .then(Commands.literal("toggle").executes(ctx ->
                                setNpcSay(ctx.getSource(), !XenoServerConfig.npcSayEnabled)))
                        .then(Commands.literal("status").executes(ctx -> npcsayStatus(ctx.getSource())))));
    }

    private static int setProfile(CommandContext<CommandSourceStack> ctx) {
        Entity entity = ctx.getSource().getEntity();
        if (entity == null) {
            npcCommandReply(ctx.getSource(), "npcprofile set must be run as an entity (e.g. an NPC script)", false);
            return 0;
        }
        NpcCombatProfile existing = NpcCombatProfile.read(entity);
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.raceId = StringArgumentType.getString(ctx, "raceId");
        profile.strength = IntegerArgumentType.getInteger(ctx, "strength");
        profile.strikePower = IntegerArgumentType.getInteger(ctx, "strikePower");
        profile.resistance = IntegerArgumentType.getInteger(ctx, "resistance");
        profile.vitality = IntegerArgumentType.getInteger(ctx, "vitality");
        profile.kiPower = IntegerArgumentType.getInteger(ctx, "kiPower");
        profile.energy = IntegerArgumentType.getInteger(ctx, "energy");
        profile.kiColor = existing.kiColor;
        profile.auraOn = existing.auraOn;
        profile.auraColor = existing.auraColor;
        profile.formGroup = existing.formGroup;
        profile.formId = existing.formId;
        profile.techniques.addAll(existing.techniques);
        profile.skinPlayer = existing.skinPlayer;
        profile.skinUrl = existing.skinUrl;
        profile.formPower = existing.formPower;
        profile.baseSize = existing.baseSize;
        profile.masteries.copyFrom(existing.masteries);
        profile.kiChargePercent = existing.kiChargePercent;
        profile.auraScale = existing.auraScale;
        profile.hairEnabled = existing.hairEnabled;
        profile.hairCode = existing.hairCode;
        profile.hairColor = existing.hairColor;
        profile.write(entity);
        npcCommandReply(ctx.getSource(), "NPC combat profile set for " + entity.getName().getString()
                + " (race=" + profile.raceId + ")", true);
        return 1;
    }

    /**
     * Shared kiattack executor. Optional brigadier args ({@code durationTicks}, {@code lookAt},
     * {@code hex}) are read when present on this node.
     */
    private static int kiAttack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity caster = livingCaster(ctx);
        if (caster == null) {
            return 0;
        }
        int durationTicks = optionalInt(ctx, "durationTicks", NpcKiAttackDispatcher.NO_DURATION_OVERRIDE);
        Entity lookAt = optionalEntity(ctx, "lookAt");
        OptionalInt hex = optionalHex(ctx);
        if (hex == null) {
            return 0;
        }
        LivingEntity aimAt = resolveAim(caster, lookAt);
        return fireOne(ctx, caster, durationTicks, aimAt, hex.orElse(0));
    }

    private static int setKiColor(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile color");
        if (entity == null) {
            return 0;
        }
        OptionalInt hex = requiredHex(ctx);
        if (hex == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.kiColor = hex.orElse(0);
        profile.write(entity);
        npcCommandReply(ctx.getSource(), "NPC ki attack color set to " + NpcCombatProfile.formatHex(profile.kiColor), true);
        return 1;
    }

    private static int setAura(CommandContext<CommandSourceStack> ctx, boolean on) {
        Entity entity = profileEntity(ctx, "npcprofile aura");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.auraOn = on;
        profile.write(entity);
        if (entity instanceof LivingEntity living) {
            NpcAuraFx.setActive(living, on);
        }
        npcCommandReply(ctx.getSource(), "NPC aura " + (on ? "ON" : "OFF")
                + " (" + NpcCombatProfile.formatHex(NpcAuraFx.resolveAuraRgb(profile)) + ")", true);
        return 1;
    }

    private static int toggleAura(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile aura");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        return setAura(ctx, !profile.auraOn);
    }

    private static int setAuraScale(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile aura scale");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.auraScale = NpcCombatProfile.clampAuraScale(FloatArgumentType.getFloat(ctx, "factor"));
        profile.write(entity);
        NpcAuraFx.sync(entity);
        npcCommandReply(ctx.getSource(), "NPC aura scale " + profile.auraScale, true);
        return 1;
    }

    private static int setAuraColor(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile aura color");
        if (entity == null) {
            return 0;
        }
        OptionalInt hex = requiredHex(ctx);
        if (hex == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.auraColor = hex.orElse(0);
        profile.write(entity);
        NpcAuraFx.sync(entity);
        npcCommandReply(ctx.getSource(), "NPC aura color set to " + NpcCombatProfile.formatHex(profile.auraColor), true);
        return 1;
    }

    private static int auraStatus(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile aura");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        npcCommandReply(ctx.getSource(), "NPC aura " + (profile.auraOn ? "ON" : "OFF")
                + " color=" + NpcCombatProfile.formatHex(NpcAuraFx.resolveAuraRgb(profile))
                + " scale=" + profile.auraScale
                + " ki=" + (profile.kiColor == 0 ? "default" : NpcCombatProfile.formatHex(profile.kiColor)), true);
        return 1;
    }

    private static int setForm(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile form");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.formGroup = StringArgumentType.getString(ctx, "group");
        profile.formId = StringArgumentType.getString(ctx, "form");
        var data = NpcFormLookup.form(profile.raceId, profile.formGroup, profile.formId);
        profile.formPower = NpcFormLookup.power(data);
        profile.write(entity);
        NpcAuraFx.sync(entity);
        npcCommandReply(ctx.getSource(), "NPC form set to " + profile.formGroup + "/" + profile.formId
                + " aura=" + NpcCombatProfile.formatHex(NpcAuraFx.resolveAuraRgb(profile)), true);
        return 1;
    }

    private static int transform(CommandContext<CommandSourceStack> ctx) {
        LivingEntity caster = livingCaster(ctx);
        if (caster == null) {
            return 0;
        }
        String group = StringArgumentType.getString(ctx, "group");
        String form = StringArgumentType.getString(ctx, "form");
        int ticks = optionalInt(ctx, "ticks", NpcTransformSystem.DEFAULT_TICKS);
        NpcTransformSystem.Fail fail = NpcTransformSystem.canStart(caster, group, form);
        if (fail == NpcTransformSystem.Fail.NO_FORM) {
            npcCommandReply(ctx.getSource(), "Unknown DMZ form " + group + "/" + form
                    + " for this NPC race. Check ConfigManager form keys.", false);
            return 0;
        }
        if (fail == NpcTransformSystem.Fail.NO_MASTERY) {
            npcCommandReply(ctx.getSource(), "Set form mastery first: xenopixels npcprofile mastery "
                    + group + " " + form + " 100", false);
            return 0;
        }
        if (!NpcTransformSystem.start(caster, group, form, ticks)) {
            npcCommandReply(ctx.getSource(), "Could not start transform", false);
            return 0;
        }
        npcCommandReply(ctx.getSource(), "Transforming to " + group + "/" + form
                + " (" + ticks + " ticks)", true);
        return 1;
    }

    private static int descend(CommandContext<CommandSourceStack> ctx) {
        LivingEntity caster = livingCaster(ctx);
        if (caster == null) {
            return 0;
        }
        NpcTransformSystem.descend(caster);
        npcCommandReply(ctx.getSource(), "NPC descended to base", true);
        return 1;
    }

    private static int setMastery(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile mastery");
        if (entity == null) {
            return 0;
        }
        String group = StringArgumentType.getString(ctx, "group");
        String form = StringArgumentType.getString(ctx, "form");
        double percent = DoubleArgumentType.getDouble(ctx, "percent");
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        var data = NpcFormLookup.form(profile.raceId, group, form);
        double max = NpcFormLookup.maxMastery(data);
        double value = max * (percent / 100.0);
        profile.masteries.setMastery(group, form, value, max);
        profile.write(entity);
        npcCommandReply(ctx.getSource(), "NPC mastery " + group + "/" + form + " = "
                + String.format("%.1f", percent) + "%", true);
        return 1;
    }

    private static int setCharge(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile charge");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.kiChargePercent = IntegerArgumentType.getInteger(ctx, "percent");
        profile.write(entity);
        npcCommandReply(ctx.getSource(), "NPC ki charge " + profile.kiChargePercent + "%", true);
        return 1;
    }

    private static int techAdd(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile tech add");
        if (entity == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(java.util.Locale.ROOT);
        if (!"kiblast".equals(id) && !"kiwave".equals(id) && !PredefinedTechniqueLookup.isKnown(id)) {
            npcCommandReply(ctx.getSource(), "Unknown technique id: " + id, false);
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        if (!profile.addTechnique(id)) {
            npcCommandReply(ctx.getSource(), "Technique already on loadout: " + id, false);
            return 0;
        }
        profile.write(entity);
        npcCommandReply(ctx.getSource(), "Added technique " + id, true);
        return 1;
    }

    private static int techRemove(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile tech remove");
        if (entity == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        if (!profile.removeTechnique(id)) {
            npcCommandReply(ctx.getSource(), "Technique not on loadout: " + id, false);
            return 0;
        }
        profile.write(entity);
        npcCommandReply(ctx.getSource(), "Removed technique " + id, true);
        return 1;
    }

    private static int techFire(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        LivingEntity caster = livingCaster(ctx);
        if (caster == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        NpcCombatProfile profile = NpcCombatProfile.read(caster);
        boolean known = profile.techniques.stream().anyMatch(t -> t.equalsIgnoreCase(id))
                || "kiblast".equalsIgnoreCase(id) || "kiwave".equalsIgnoreCase(id)
                || PredefinedTechniqueLookup.isKnown(id);
        if (!known) {
            npcCommandReply(ctx.getSource(), "Technique not on this NPC: " + id, false);
            return 0;
        }
        Entity lookAt = optionalEntity(ctx, "lookAt");
        LivingEntity aimAt = resolveAim(caster, lookAt);
        if (aimAt != null) {
            NpcKiAim.applyPose(caster, aimAt);
        }
        if (!NpcKiAttackDispatcher.fire(id, caster, profile, NpcKiAttackDispatcher.NO_DURATION_OVERRIDE, aimAt)) {
            npcCommandReply(ctx.getSource(), "Unknown ki attack type: " + id, false);
            return 0;
        }
        return 1;
    }

    private static int techList(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile tech list");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        String list = profile.techniques.isEmpty() ? "(none)" : String.join(", ", profile.techniques);
        npcCommandReply(ctx.getSource(), "NPC techniques: " + list, true);
        return 1;
    }

    private static int setSkinPlayer(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile skin player");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.skinPlayer = StringArgumentType.getString(ctx, "name");
        profile.write(entity);
        boolean applied = entity instanceof LivingEntity living
                && NpcDisplayApply.applySkin(living, profile.skinPlayer, profile.skinUrl);
        npcCommandReply(ctx.getSource(), "NPC skin player set to " + profile.skinPlayer
                + (applied ? "" : " (stored; CNPC display apply skipped)"), true);
        return 1;
    }

    private static int setSkinUrl(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile skin url");
        if (entity == null) {
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.skinUrl = StringArgumentType.getString(ctx, "url");
        profile.write(entity);
        boolean applied = entity instanceof LivingEntity living
                && NpcDisplayApply.applySkin(living, profile.skinPlayer, profile.skinUrl);
        npcCommandReply(ctx.getSource(), "NPC skin url set"
                + (applied ? "" : " (stored; CNPC display apply skipped)"), true);
        return 1;
    }

    private static int setHairEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        Entity entity = profileEntity(ctx, "npcprofile hair");
        if (entity == null) return 0;
        return hairResult(ctx, NpcHairBridge.setEnabled(entity, enabled),
                "DMZ hair " + (enabled ? "enabled" : "disabled"));
    }

    private static int setHairCode(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile hair code");
        if (entity == null) return 0;
        return hairResult(ctx, NpcHairBridge.setCode(entity,
                StringArgumentType.getString(ctx, "code")), "DMZ hair code saved");
    }

    private static int setHairColor(CommandContext<CommandSourceStack> ctx, String color) {
        Entity entity = profileEntity(ctx, "npcprofile hair color");
        if (entity == null) return 0;
        return hairResult(ctx, NpcHairBridge.setColor(entity, color),
                color.isEmpty() ? "DMZ hair color override cleared" : "DMZ hair color saved");
    }

    private static int hairStatus(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile hair");
        if (entity == null) return 0;
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        npcCommandReply(ctx.getSource(), "DMZ hair=" + profile.hairEnabled
                + ", code=" + (profile.hairCode == null || profile.hairCode.isBlank() ? "(empty)" : "set")
                + ", color=" + (profile.hairColor == null || profile.hairColor.isBlank()
                ? "encoded hair colors" : profile.hairColor), true);
        return 1;
    }

    private static int hairResult(CommandContext<CommandSourceStack> ctx,
                                  NpcHairBridge.Result result, String success) {
        if (result == NpcHairBridge.Result.OK) {
            npcCommandReply(ctx.getSource(), success, true);
            return 1;
        }
        String error = switch (result) {
            case ADDON_MISSING -> "CNPC Gecko Addon is not loaded";
            case CUSTOM_MODEL_REQUIRED -> "This NPC must use a CNPC Gecko custom model";
            case INVALID_CODE -> "Invalid DMZ hair code/full-set code";
            case INVALID_COLOR -> "Invalid hair color (use #RRGGBB or clear)";
            default -> "DMZ hair update failed";
        };
        npcCommandReply(ctx.getSource(), error, false);
        return 0;
    }

    private static int enableAi(CommandContext<CommandSourceStack> ctx) {
        Entity entity = profileEntity(ctx, "npcprofile ai enable");
        if (entity == null) return 0;
        if (!NpcCombatProfile.hasProfile(entity)) {
            npcCommandReply(ctx.getSource(), "Attach a combat profile before enabling profile AI", false);
            return 0;
        }
        net.bullettrain.xenopixelsmod.compat.npc.NpcProfileLifecycle.repairAi(entity);
        npcCommandReply(ctx.getSource(), "Profile NPC AI enabled", true);
        return 1;
    }

    private static LivingEntity livingCaster(CommandContext<CommandSourceStack> ctx) {
        Entity entity = ctx.getSource().getEntity();
        if (!(entity instanceof LivingEntity caster)) {
            npcCommandReply(ctx.getSource(), "npcprofile kiattack must be run as a living entity (e.g. an NPC script)", false);
            return null;
        }
        return caster;
    }

    private static Entity profileEntity(CommandContext<CommandSourceStack> ctx, String label) {
        Entity entity = ctx.getSource().getEntity();
        if (entity == null) {
            npcCommandReply(ctx.getSource(), label + " must be run as an entity (e.g. an NPC script)", false);
            return null;
        }
        return entity;
    }

    /** {@code null} means the hex argument was present and invalid (already replied). */
    private static OptionalInt requiredHex(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "hex");
        OptionalInt parsed = NpcCombatProfile.parseHexColor(raw);
        if (parsed.isEmpty()) {
            npcCommandReply(ctx.getSource(), "Invalid HEX color: " + raw + " (use FF00AA, #FF00AA, or 0xFF00AA)", false);
            return null;
        }
        return parsed;
    }

    /** Empty = no hex arg. {@code null} = present but invalid. */
    private static OptionalInt optionalHex(CommandContext<CommandSourceStack> ctx) {
        try {
            StringArgumentType.getString(ctx, "hex");
        } catch (IllegalArgumentException missing) {
            return OptionalInt.empty();
        }
        OptionalInt parsed = requiredHex(ctx);
        return parsed == null ? null : parsed;
    }

    private static int optionalInt(CommandContext<CommandSourceStack> ctx, String name, int fallback) {
        try {
            return IntegerArgumentType.getInteger(ctx, name);
        } catch (IllegalArgumentException missing) {
            return fallback;
        }
    }

    private static Entity optionalEntity(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        try {
            return EntityArgument.getEntity(ctx, name);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    /**
     * Prefer the command's {@code lookAt} entity, then the mob's attack target, then whoever
     * last hurt this caster — all vanilla {@link LivingEntity}/{@link Mob} APIs.
     */
    static LivingEntity resolveAim(LivingEntity caster, Entity lookAt) {
        if (lookAt instanceof LivingEntity living && living.isAlive() && living != caster) {
            return living;
        }
        LivingEntity locked = NpcKiAim.lockedTarget(caster);
        if (locked != null) {
            return locked;
        }
        if (caster instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive() && target != caster) {
                return target;
            }
        }
        LivingEntity hurtBy = caster.getLastHurtByMob();
        if (hurtBy != null && hurtBy.isAlive() && hurtBy != caster) {
            return hurtBy;
        }
        return null;
    }

    private static int fireOne(CommandContext<CommandSourceStack> ctx,
                                LivingEntity caster, int durationTicks, LivingEntity aimAt, int colorOverride) {
        if (aimAt != null) {
            NpcKiAim.applyPose(caster, aimAt);
        }
        String blastType = StringArgumentType.getString(ctx, "blastType");
        NpcCombatProfile profile = NpcCombatProfile.read(caster);
        if (!NpcKiAttackDispatcher.fire(blastType, caster, profile, durationTicks, aimAt, colorOverride)) {
            npcCommandReply(ctx.getSource(), "Unknown ki attack type: " + blastType, false);
            return 0;
        }
        return 1;
    }

    /**
     * CustomNPCs {@code executeCommand} wraps a CommandSource whose {@code sendFailure}
     * always {@code NotifyOPs}. Skip replies when say/admin chat is muted and the runner
     * is not a player.
     */
    private static void npcCommandReply(CommandSourceStack source, String message, boolean ok) {
        boolean fromNpc = source.getEntity() != null && !(source.getEntity() instanceof ServerPlayer);
        if (fromNpc && !XenoServerConfig.npcSayEnabled) {
            return;
        }
        if (ok) {
            source.sendSuccess(() -> Component.literal(message), false);
        } else {
            source.sendFailure(Component.literal(message));
        }
    }

    private static int setNpcSay(CommandSourceStack source, boolean enabled) {
        XenoServerConfig.npcSayEnabled = enabled;
        XenoServerConfig.save();
        source.sendSuccess(
                () -> Component.literal("NPC say / executeCommand OP chat: " + (enabled ? "ON" : "OFF")),
                true);
        return 1;
    }

    private static int npcsayStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("NPC say / executeCommand OP chat is "
                        + (XenoServerConfig.npcSayEnabled ? "ON" : "OFF")
                        + "  (/xenopixels npcsay on|off|toggle)"),
                false);
        return 1;
    }
}
