package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.extras.ActionMode;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.compat.util.LazyOptional;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTransformSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class StackCommands {
    private StackCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Builds the {@code /stack} tree; split out from the event so tests can inspect it. */
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stack")
                .then(Commands.literal("player")
                        .then(Commands.literal("on")
                                .requires(XenoPermissions.require(XenoPermissions.STACK_SELF))
                                .then(stackArguments(false)))
                        .then(Commands.literal("toggle")
                                .requires(XenoPermissions.require(XenoPermissions.STACK_SELF))
                                .then(stackArguments(true)))
                        .then(Commands.literal("off")
                                .requires(XenoPermissions.require(XenoPermissions.STACK_SELF))
                                .executes(ctx -> playerOff(ctx.getSource(), null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(XenoPermissions.require(XenoPermissions.STACK_OTHERS))
                                        .executes(ctx -> playerOff(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("npc")
                        .requires(XenoPermissions.require(XenoPermissions.STACK_NPC))
                        .then(Commands.literal("on").then(npcStackArguments(false)))
                        .then(Commands.literal("toggle").then(npcStackArguments(true)))
                        .then(Commands.literal("off")
                                .then(Commands.argument("entity", EntityArgument.entity())
                                        .executes(ctx -> npcOff(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "entity"))))))
                .executes(ctx -> usage(ctx.getSource())));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> stackArguments(
            boolean toggle) {
        return Commands.argument("group", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        ConfigManager.getAllStackForms().keySet(), builder))
                .then(Commands.argument("form", StringArgumentType.word())
                        .executes(ctx -> playerOn(ctx.getSource(), null,
                                StringArgumentType.getString(ctx, "group"),
                                StringArgumentType.getString(ctx, "form"), toggle))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(XenoPermissions.require(XenoPermissions.STACK_OTHERS))
                                .executes(ctx -> playerOn(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "group"),
                                        StringArgumentType.getString(ctx, "form"), toggle))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> npcStackArguments(
            boolean toggle) {
        return Commands.argument("group", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        ConfigManager.getAllStackForms().keySet(), builder))
                .then(Commands.argument("form", StringArgumentType.word())
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(ctx -> npcOn(ctx.getSource(),
                                        EntityArgument.getEntity(ctx, "entity"),
                                        StringArgumentType.getString(ctx, "group"),
                                        StringArgumentType.getString(ctx, "form"), toggle))));
    }

    private static int playerOn(CommandSourceStack source, ServerPlayer target, String group,
                                String form, boolean toggle) throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : source.getPlayerOrException();
        StatsData data = stats(player);
        if (data == null) {
            source.sendFailure(Component.literal("No DragonMineZ stats on " + player.getGameProfile().getName()));
            return 0;
        }
        if (toggle && data.getCharacter().hasActiveStackForm()
                && group.equalsIgnoreCase(data.getCharacter().getActiveStackFormGroup())
                && form.equalsIgnoreCase(data.getCharacter().getActiveStackForm())) {
            return playerOff(source, player);
        }
        if (ConfigManager.getStackForm(group, form) == null
                || !TransformationsHelper.isSelectableStackForm(data, group, form)) {
            source.sendFailure(Component.literal("Stack form is missing or locked: " + group + "." + form));
            return 0;
        }
        data.getStatus().setSelectedAction(ActionMode.STACK);
        data.getCharacter().setSelectedStackFormGroup(group);
        data.getCharacter().setSelectedStackForm(form);
        data.getCharacter().recordPreviousStackForm();
        data.getCharacter().setActiveStackForm(group, form);
        player.refreshDimensions();
        sync(player);
        source.sendSuccess(() -> Component.literal("Stack " + group + "." + form + " ON for "
                + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int playerOff(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : source.getPlayerOrException();
        StatsData data = stats(player);
        if (data == null) {
            source.sendFailure(Component.literal("No DragonMineZ stats on " + player.getGameProfile().getName()));
            return 0;
        }
        data.getCharacter().clearActiveStackForm(player);
        data.getCharacter().clearPreviousStackFormRecord();
        player.removeEffect(MainEffects.STACK_TRANSFORMED);
        sync(player);
        source.sendSuccess(() -> Component.literal("Stack OFF for " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int npcOn(CommandSourceStack source, Entity raw, String group, String form, boolean toggle) {
        LivingEntity npc = customNpc(source, raw);
        if (npc == null) return 0;
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        if (toggle && group.equalsIgnoreCase(profile.stackGroup) && form.equalsIgnoreCase(profile.stackId)) {
            return npcOff(source, npc);
        }
        if (!NpcTransformSystem.startStack(npc, group, form, NpcTransformSystem.DEFAULT_TICKS)) {
            source.sendFailure(Component.literal("Unable to apply NPC stack form " + group + "." + form));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("NPC stack " + group + "." + form + " started"), true);
        return 1;
    }

    private static int npcOff(CommandSourceStack source, Entity raw) {
        LivingEntity npc = customNpc(source, raw);
        if (npc == null) return 0;
        if (!NpcTransformSystem.unstack(npc)) {
            source.sendFailure(Component.literal("Unable to clear NPC stack form"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("NPC stack OFF"), true);
        return 1;
    }

    private static LivingEntity customNpc(CommandSourceStack source, Entity raw) {
        if (!(raw instanceof LivingEntity living) || !NpcCounterpartSync.isCustomNpc(living)) {
            source.sendFailure(Component.literal("Target must be a CustomNPC or MyNPC entity"));
            return null;
        }
        return living;
    }

    private static StatsData stats(ServerPlayer player) {
        LazyOptional<StatsData> optional = StatsProvider.get(StatsCapability.INSTANCE, player);
        return optional.orElse(null);
    }

    private static void sync(ServerPlayer player) {
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    private static int usage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Usage: /stack player <on|off|toggle> ... or /stack npc <on|off|toggle> ..."), false);
        return 1;
    }
}
