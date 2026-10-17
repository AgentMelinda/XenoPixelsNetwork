package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisher;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueDefinition;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime;

/** Actual positive-damage contacts, not speculative input or animation hand indices. */
@EventBusSubscriber(modid=XenoPixelsMod.MOD_ID)
public final class CombatComboCounter {
    public static final int WINDOW=80;
    public static final class Chain {
        private UUID victim;
        private int hits;
        private long last;
        private int rushAt;
        private boolean rushUsed;

        public void hit(UUID target,long now,float damage) {
            if(target==null || !Float.isFinite(damage) || damage<=0)return;
            if(!target.equals(victim) || now-last>=WINDOW){hits=0;rushAt=0;rushUsed=false;}
            victim=target;hits=Math.min(10000,hits+1);last=now;
        }
        public int hits(long now){return left(now)>0?hits:0;}
        public int left(long now){return hits==0?0:(int)Math.clamp(WINDOW-(now-last),0,WINDOW);}
        void armRush(ServerPlayer player, long now) {
            var settings=V3Config.get().randomRush();
            if(!settings.enabled() || READY.containsKey(player.getUUID()))return;
            if (rushAt == 0) rushAt = player.getRandom().nextIntBetweenInclusive(4, 7);
            if (!rushUsed && hits >= rushAt) {
                List<String> ids=V3TechniqueCatalog.all().stream()
                        .filter(t->!"archetype_placeholder".equals(t.animationStatus()))
                        .map(V3TechniqueDefinition::id).toList();
                String attack=SuperRushDeck.next(player.getUUID(),ids,player.getRandom());
                if(attack!=null){
                    READY.put(player.getUUID(),new Ready(UUID.randomUUID(),V3CombatServer.session(player),victim,
                        attack,SuperRushDeck.ultimate(player.getRandom()),new RushMashMeter(now,settings)));
                    rushUsed=true;
                }
            }
        }
    }
    private static final Map<UUID,Chain> CHAINS=new HashMap<>();
    private record Ready(UUID token, UUID session, UUID target, String attack, boolean ultimate, RushMashMeter meter) {}
    private static final Map<UUID,Ready> READY=new HashMap<>();
    private CombatComboCounter(){}
    public static int hits(ServerPlayer player,long now){var chain=CHAINS.get(player.getUUID());return chain==null?0:chain.hits(now);}
    public static int left(ServerPlayer player,long now){var chain=CHAINS.get(player.getUUID());return chain==null?0:chain.left(now);}
    public static int rushPromptTicks(ServerPlayer p,long now){Ready r=READY.get(p.getUUID());return r==null?0:r.meter.ticksLeft(now);}
    public static boolean rushUltimate(ServerPlayer p,long now){Ready r=READY.get(p.getUUID());return r!=null&&!r.meter.expired(now)&&r.ultimate;}
    public static UUID rushToken(ServerPlayer p){Ready r=READY.get(p.getUUID());return r==null?null:r.token;}
    public static int rushProgress(ServerPlayer p){Ready r=READY.get(p.getUUID());return r==null?0:r.meter.completed();}
    public static void cancelReady(ServerPlayer p){READY.remove(p.getUUID());}
    private static boolean valid(ServerPlayer p,Ready r,long now){
        var target=V3CombatServer.lockedTarget(p);
        return V3Config.get().randomRush().enabled() && r.session!=null && r.session.equals(V3CombatServer.session(p))
            && !r.meter.expired(now) && p.isAlive() && !p.isSpectator() && target!=null && r.target.equals(target.getUUID());
    }
    public static void mash(ServerPlayer p,UUID token,long now){
        Ready r=READY.get(p.getUUID());
        if(r==null||!r.token.equals(token)||!valid(p,r,now))return;
        if(r.meter.press(now)) { if(r.meter.full())tryReady(p,r); V3CombatServer.syncState(p); }
    }
    private static void tryReady(ServerPlayer p,Ready r){
        if(READY.get(p.getUUID())!=r || !valid(p,r,p.getServer().getTickCount()))return;
        var attack=V3TechniqueCatalog.find(r.attack);
        if(attack!=null && V3TechniqueRuntime.castRandomRush(p,attack,r.target,r.ultimate)){
            READY.remove(p.getUUID(),r);
            if(r.ultimate && r.session.equals(V3CombatServer.session(p)) && V3TechniqueRuntime.ownsRandomRush(p))
                SuperRushTransformation.transform(p);
        }
    }
    @SubscribeEvent public static void hit(LivingDamageEvent.Post event){
        if(!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity()==player
                || !(V3CombatServer.owns(player)||UltimateFinisher.owns(player)) || !(event.getNewDamage()>0))return;
        if(V3TechniqueRuntime.ownsRandomRush(player))return;
        long now=player.getServer().getTickCount();
        Chain chain=CHAINS.computeIfAbsent(player.getUUID(),id->new Chain());
        chain.hit(event.getEntity().getUUID(),now,event.getNewDamage());
        if(V3CombatServer.owns(player) && !V3TechniqueRuntime.owns(player) && !UltimateFinisher.owns(player))chain.armRush(player,now);
        V3CombatServer.syncState(player);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        long now=event.getServer().getTickCount();
        for(var entry:List.copyOf(READY.entrySet())){
            ServerPlayer p=event.getServer().getPlayerList().getPlayer(entry.getKey());Ready r=entry.getValue();
            if(p==null||!valid(p,r,now)) {READY.remove(entry.getKey(),r);if(p!=null)V3CombatServer.syncState(p);}
            else {if(r.meter.full()&&V3CombatServer.canStartTechnique(p))tryReady(p,r);if(now%4==0)V3CombatServer.syncState(p);}
        }
        for(var it=CHAINS.entrySet().iterator();it.hasNext();){
            var entry=it.next();ServerPlayer p=event.getServer().getPlayerList().getPlayer(entry.getKey());
            if(p==null||entry.getValue().left(now)==0){it.remove();if(p!=null)V3CombatServer.syncState(p);}
            else if(now%4==0)V3CombatServer.syncState(p);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){clear(event.getEntity().getUUID());}
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event){clear(event.getOriginal().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){clear(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){CHAINS.clear();READY.clear();SuperRushDeck.clearAll();SuperRushTransformation.clearAll();}
    private static void clear(UUID id){CHAINS.remove(id);READY.remove(id);SuperRushDeck.clear(id);SuperRushTransformation.clear(id);}
}
