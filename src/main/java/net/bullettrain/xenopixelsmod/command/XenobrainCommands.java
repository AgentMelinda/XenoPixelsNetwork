package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrainVersion;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenobrainCommands {
    private XenobrainCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("xenobrain")
                .requires(XenoPermissions.require(XenoPermissions.XENOBRAIN_SET))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("on").executes(ctx -> setOn(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setOn(ctx.getSource(), false)))
                .then(Commands.literal("v1").executes(ctx -> setVersion(ctx.getSource(),
                        NpcCombatBrainVersion.V1)))
                .then(Commands.literal("v2").executes(ctx -> setVersion(ctx.getSource(),
                        NpcCombatBrainVersion.V2)))
                .then(Commands.literal("v3").executes(ctx -> setVersion(ctx.getSource(),
                        NpcCombatBrainVersion.V3)))
                .then(Commands.literal("v9").executes(ctx -> setVersion(ctx.getSource(),
                        NpcCombatBrainVersion.V9)));
    }

    static int status(CommandSourceStack source) {
        LivingEntity npc = lookedAtNpc(source);
        if (npc == null) {
            source.sendFailure(Component.literal("Look at a profiled NPC"));
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        source.sendSuccess(() -> Component.literal(
                "§eCombat Brain §f" + npc.getName().getString()
                        + " §7= §" + (profile.combatBrain ? "aON" : "cOFF")
                        + " §7" + profile.brainVersion.label()), false);
        return 1;
    }

    static int setOn(CommandSourceStack source, boolean on) {
        LivingEntity npc = lookedAtNpc(source);
        if (npc == null) {
            source.sendFailure(Component.literal("Look at a profiled NPC"));
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.combatBrain = on;
        profile.write(npc);
        source.sendSuccess(() -> Component.literal(
                "§eCombat Brain §f" + npc.getName().getString()
                        + " §7= §" + (on ? "aON" : "cOFF")
                        + " §7" + profile.brainVersion.label()), true);
        return 1;
    }

    static int setVersion(CommandSourceStack source, NpcCombatBrainVersion version) {
        LivingEntity npc = lookedAtNpc(source);
        if (npc == null) {
            source.sendFailure(Component.literal("Look at a profiled NPC"));
            return 0;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.setBrainVersion(version);
        profile.write(npc);
        source.sendSuccess(() -> Component.literal(
                "§eCombat Brain §f" + npc.getName().getString()
                        + " §7= §" + (profile.combatBrain ? "aON" : "cOFF")
                        + " §7" + profile.brainVersion.label()), true);
        return 1;
    }

    private static LivingEntity lookedAtNpc(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            Entity entity = source.getEntity();
            return entity instanceof LivingEntity living && NpcCombatProfile.hasProfile(living)
                    ? living : null;
        }
        LivingEntity hit = lookedAt(player);
        if (hit != null && NpcCombatProfile.hasProfile(hit)) {
            return hit;
        }
        return null;
    }

    private static LivingEntity lookedAt(ServerPlayer player) {
        double reach = 20.0;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                entity -> entity instanceof LivingEntity && entity.isAlive() && entity != player,
                reach * reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
