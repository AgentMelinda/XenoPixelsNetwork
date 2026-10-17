package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Shared recoverable victim window for rush aftermath, finisher aftermath and dash chains. */
@EventBusSubscriber(modid=XenoPixelsMod.MOD_ID)
public final class CombatRecovery {
    private record Ticket(ServerPlayer victim, UUID token, RecoveryProgress progress,
                          RecoverySnapshot.Kind kind, Runnable release) {}
    private static final Map<UUID, Ticket> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> DASH_IMMUNE = new HashMap<>();
    private CombatRecovery() {}

    public static boolean begin(ServerPlayer victim, UUID token, long now, int ticks,
                                RecoverySnapshot.Kind kind, Runnable release) {
        if (ticks <= 0 || ACTIVE.containsKey(victim.getUUID()) || !victim.isAlive()) return false;
        ACTIVE.put(victim.getUUID(), new Ticket(victim, token, new RecoveryProgress(now, ticks), kind, release));
        V3CombatServer.syncState(victim);
        return true;
    }
    public static RecoverySnapshot snapshot(ServerPlayer victim, long now) {
        Ticket ticket = ACTIVE.get(victim.getUUID());
        return ticket == null ? null : new RecoverySnapshot(ticket.token(), ticket.progress().completed(now),
                ticket.progress().total(), ticket.kind());
    }
    public static boolean mash(ServerPlayer victim, UUID token, long now) {
        Ticket ticket = ACTIVE.get(victim.getUUID());
        if (ticket == null || !ticket.token().equals(token) || !ticket.progress().press(now)) return false;
        if (ticket.progress().remaining(now)==0) complete(ticket, now);
        else V3CombatServer.syncState(victim);
        return true;
    }
    public static boolean dashImmune(ServerPlayer victim, long now) {
        return now < DASH_IMMUNE.getOrDefault(victim.getUUID(), Long.MIN_VALUE);
    }
    public static void cancel(ServerPlayer victim, UUID token) {
        Ticket ticket=ACTIVE.get(victim.getUUID());
        if(ticket!=null && ticket.token().equals(token)) {
            ACTIVE.remove(victim.getUUID(),ticket);
            V3CombatServer.syncState(victim);
        }
    }
    private static void complete(Ticket ticket, long now) {
        if (!ACTIVE.remove(ticket.victim().getUUID(), ticket)) return;
        if (ticket.kind()==RecoverySnapshot.Kind.DASH) DASH_IMMUNE.put(ticket.victim().getUUID(), now+10);
        ticket.release().run();
        V3CombatServer.syncState(ticket.victim());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long now=event.getServer().getTickCount();
        for (Ticket ticket:List.copyOf(ACTIVE.values())) {
            if (!ticket.victim().isAlive() || ticket.victim().isRemoved() || ticket.progress().remaining(now)==0)
                complete(ticket,now);
            else if (now%2==0) V3CombatServer.syncState(ticket.victim());
        }
        DASH_IMMUNE.entrySet().removeIf(e -> now>=e.getValue());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        Ticket ticket=ACTIVE.remove(event.getEntity().getUUID());
        if(ticket!=null)ticket.release().run();
        DASH_IMMUNE.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        Ticket ticket=ACTIVE.remove(event.getOriginal().getUUID());
        if(ticket!=null)ticket.release().run();
        DASH_IMMUNE.remove(event.getOriginal().getUUID());
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Ticket ticket=ACTIVE.remove(event.getEntity().getUUID());
        if(ticket!=null)ticket.release().run();
        DASH_IMMUNE.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for(Ticket ticket:List.copyOf(ACTIVE.values()))complete(ticket,event.getServer().getTickCount());
        ACTIVE.clear();DASH_IMMUNE.clear();
    }
}
