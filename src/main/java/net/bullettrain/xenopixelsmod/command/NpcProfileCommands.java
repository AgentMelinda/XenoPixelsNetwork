package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Script-facing entry point for the My NPCs / CustomNPCs race-stats-ki-attack bridge (see
 * {@code compat.npc}). Both mods' {@code ICustomNpc.executeCommand(String)} runs a command with
 * the NPC entity itself as {@link CommandSourceStack#getEntity()} — the same mechanism a command
 * block uses — so a script can call e.g. {@code npc.executeCommand("xenopixels npcprofile
 * kiattack kiblast")} to act as that NPC, with no need to hook into either mod's script-engine
 * bindings directly.
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
                                        .executes(NpcProfileCommands::kiAttack)))));
    }

    private static int setProfile(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        Entity entity = ctx.getSource().getEntity();
        if (entity == null) {
            ctx.getSource().sendFailure(Component.literal("npcprofile set must be run as an entity (e.g. an NPC script)"));
            return 0;
        }
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.raceId = StringArgumentType.getString(ctx, "raceId");
        profile.strength = IntegerArgumentType.getInteger(ctx, "strength");
        profile.strikePower = IntegerArgumentType.getInteger(ctx, "strikePower");
        profile.resistance = IntegerArgumentType.getInteger(ctx, "resistance");
        profile.vitality = IntegerArgumentType.getInteger(ctx, "vitality");
        profile.kiPower = IntegerArgumentType.getInteger(ctx, "kiPower");
        profile.energy = IntegerArgumentType.getInteger(ctx, "energy");
        profile.write(entity);
        ctx.getSource().sendSuccess(
                () -> Component.literal("NPC combat profile set for " + entity.getName().getString()
                        + " (race=" + profile.raceId + ")"),
                false);
        return 1;
    }

    private static int kiAttack(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        Entity entity = ctx.getSource().getEntity();
        if (!(entity instanceof LivingEntity caster)) {
            ctx.getSource().sendFailure(Component.literal("npcprofile kiattack must be run as a living entity (e.g. an NPC script)"));
            return 0;
        }
        String blastType = StringArgumentType.getString(ctx, "blastType");
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        if (!NpcKiAttackDispatcher.fire(blastType, caster, profile)) {
            ctx.getSource().sendFailure(Component.literal("Unknown ki attack type: " + blastType));
            return 0;
        }
        return 1;
    }
}
