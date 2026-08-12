package net.bullettrain.xenopixelsmod.features.party;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.PartyCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Commands, lifecycle, sync, expiry, and authoritative party-friendly-fire protection. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PartyEvents {
    private PartyEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(PartyCommands.build());
        event.getDispatcher().register(PartyCommands.chatAlias());
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PartyManager.onLogin(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PartyManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PartyManager.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDirectAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)
                || !(event.getTarget() instanceof ServerPlayer victim)) return;
        if (PartyManager.sameParty(attacker, victim)
                && !com.dragonminez.common.quest.PartyManager.isPartyPvpEnabled(attacker)) {
            event.setCanceled(true);
            attacker.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7Party friendly fire is disabled"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        // Cheapest discriminator first: most damage on a server involves nobody in a party, and
        // this fires at HIGHEST priority for every hit a player takes.
        if (PartyManager.partyOf(victim) == null) return;
        ServerPlayer attacker = ownerPlayer(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (attacker == null || !PartyManager.sameParty(attacker, victim)) return;
        if (!com.dragonminez.common.quest.PartyManager.isPartyPvpEnabled(attacker)) event.setCanceled(true);
    }

    private static ServerPlayer ownerPlayer(Entity source, Entity direct) {
        if (source instanceof ServerPlayer player) return player;
        if (direct instanceof ServerPlayer player) return player;
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        if (source instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        return null;
    }
}
