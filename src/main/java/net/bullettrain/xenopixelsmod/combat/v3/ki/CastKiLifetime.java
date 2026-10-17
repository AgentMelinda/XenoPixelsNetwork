package net.bullettrain.xenopixelsmod.combat.v3.ki;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import java.util.*;
import java.util.function.Supplier;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Tracks actual native projectiles (including barrage emitters and their pellets).
 * Visual-only explosion entities are deliberately excluded. The dispatch scope is
 * restored in finally, so unrelated projectiles never join a cinematic cast. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class CastKiLifetime {
    private record Key(UUID owner, UUID cast) {}
    private static final Map<Key, Active> CASTS = new LinkedHashMap<>();
    private static final Map<AbstractKiProjectile, Key> PROJECTILES = new IdentityHashMap<>();
    private static final ThreadLocal<Key> SPAWNING = new ThreadLocal<>();
    private static long generation;
    private static final class Active {
        final ServerPlayer player;
        final LivingEntity target;
        final Key key;
        final long generation = ++CastKiLifetime.generation;
        final String music;
        String prefix = "";
        long releaseAt = -1, spawnAt;
        String nativeId = "";
        boolean automatic;
        boolean capped;
        final KiLifetimeLimit lifetime=new KiLifetimeLimit(net.bullettrain.xenopixelsmod.combat.v3.V3Config.get().kiMaxLifetimeTicks());
        Active(ServerPlayer player, LivingEntity target, Key key, String music) {
            this.player = player; this.target = target; this.key = key; this.music = music;
        }
        void send(boolean end) {
            packet(end).send(player, target);
        }
        CastPresentation packet(boolean end) {
            return new CastPresentation(key.owner, key.cast, target.getUUID(), generation, end, prefix, music,
                    releaseAt < 0 ? 0 : (int)Math.clamp(player.server.getTickCount()-releaseAt, 0, 120000));
        }
    }
    private CastKiLifetime() {}
    public static void begin(ServerPlayer player, LivingEntity target, UUID cast, String type, boolean finisher) {
        Key key = new Key(player.getUUID(), cast);
        if (CASTS.containsKey(key)) return;
        Active active = new Active(player, target, key, music(type, finisher));
        CASTS.put(key, active); active.send(false);
    }
    public static String music(String type, boolean finisher) {
        if (finisher) return "xenopixelsmod:music.dbz_001";
        return "xenopixelsmod:music.dbz_" + switch (type == null ? "" : type) {
            case "beam" -> "007";
            case "volley" -> "011";
            case "radial", "hold" -> "019";
            case "ball", "giant_ball" -> "025";
            default -> "037";
        };
    }
    public static <T> T dispatch(ServerPlayer player, UUID cast, Supplier<T> action) {
        Key previous = SPAWNING.get(), key = new Key(player.getUUID(), cast);
        Set<AbstractKiProjectile> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(PROJECTILES.keySet()); SPAWNING.set(key);
        try {
            T result = action.get();
            if (Boolean.FALSE.equals(result)) for (var e : new HashMap<>(PROJECTILES).entrySet()) {
                if (key.equals(e.getValue()) && !before.contains(e.getKey())) {
                    e.getKey().discard(); PROJECTILES.remove(e.getKey());
                }
            }
            return result;
        }
        finally { if (previous == null) SPAWNING.remove(); else SPAWNING.set(previous); }
    }
    public static void pellet(AbstractKiProjectile parent, Runnable action) {
        Key key = PROJECTILES.get(parent), previous = SPAWNING.get();
        if (key != null) SPAWNING.set(key);
        try { action.run(); }
        finally { if (previous == null) SPAWNING.remove(); else SPAWNING.set(previous); }
    }
    public static boolean active(UUID owner, UUID cast) {
        Key key = new Key(owner, cast);
        Active castState=CASTS.get(key);if(castState!=null && castState.capped)return false;
        return PROJECTILES.entrySet().stream().anyMatch(e -> key.equals(e.getValue()) && !e.getKey().isRemoved())
                || V3KiShots.hasActiveCast(owner, cast);
    }
    public static void firePose(ServerPlayer player, UUID cast, String prefix) {
        Active active = CASTS.get(new Key(player.getUUID(), cast));
        if (active == null || prefix == null || prefix.isEmpty() || prefix.equals(active.prefix)) return;
        active.prefix = prefix; active.send(false);
    }
    public static void finish(UUID owner, UUID cast, boolean cancelProjectiles) {
        Key key = new Key(owner, cast);
        Active active = CASTS.remove(key);
        if (active != null) {
            active.send(true);
            if (!active.prefix.isEmpty() && CASTS.values().stream().noneMatch(a -> a.key.owner.equals(owner)))
                com.dragonminez.common.network.NetworkHandler.sendToTrackingEntityAndSelf(
                        new com.dragonminez.common.network.S2C.TriggerAnimationS2C(owner,
                                com.dragonminez.common.network.S2C.TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP,
                                0, -1, ""), active.player);
        }
        for (var e : new HashMap<>(PROJECTILES).entrySet()) if (key.equals(e.getValue())) {
            if (cancelProjectiles) e.getKey().discard();
            PROJECTILES.remove(e.getKey());
        }
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractKiProjectile projectile)
            // DMZ adds barrage pellets once in setup and again from onKiTick after the
            // cast scope ends. JoinLevel fires before duplicate-UUID rejection, so keep
            // the original scoped owner on that second event.
            {
                Key key=PROJECTILES.compute(projectile, (ignored, existing) -> existing != null ? existing : SPAWNING.get());
                Active cast=CASTS.get(key);if(cast!=null && cast.capped){event.setCanceled(true);projectile.discard();PROJECTILES.remove(projectile);}
            }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (var e : new HashMap<>(PROJECTILES).entrySet()) {
            AbstractKiProjectile projectile = e.getKey();
            if (projectile.isRemoved() || !(projectile.level() instanceof net.minecraft.server.level.ServerLevel level)
                    || level.getEntity(projectile.getUUID()) != projectile) { PROJECTILES.remove(projectile); continue; }
            if (e.getValue() != null) continue;
            // DMZ assigns technique id after setup adds the entity to the level.
            if (V3NativeKi.owns(projectile.getTechniqueId()) && projectile.getOwner() instanceof ServerPlayer player) {
                V3TechniqueDefinition technique = V3TechniqueCatalog.all().stream()
                        .filter(t -> V3NativeKi.nativeId(t.id()).equals(projectile.getTechniqueId())).findFirst().orElse(null);
                if (technique == null) { PROJECTILES.remove(projectile); continue; }
                long spawnAt = projectile.level().getGameTime() - projectile.tickCount;
                Key key = CASTS.values().stream().filter(a -> a.key.owner.equals(player.getUUID()) && (!a.automatic
                        || a.spawnAt == spawnAt && a.nativeId.equals(projectile.getTechniqueId()))).map(a -> a.key).findFirst().orElse(null);
                if (key == null) {
                    LivingEntity target = net.bullettrain.xenopixelsmod.combat.v3.V3Targeting.resolve(player);
                    if (target == null) target = player;
                    UUID cast = UUID.randomUUID(); begin(player, target, cast, technique.type(), false);
                    key = new Key(player.getUUID(), cast); CASTS.get(key).automatic = true;
                    CASTS.get(key).spawnAt = spawnAt; CASTS.get(key).nativeId = projectile.getTechniqueId();
                }
                PROJECTILES.put(projectile, key);
            } else if (projectile.tickCount > 2) PROJECTILES.remove(projectile);
        }
        for (var active : List.copyOf(CASTS.values())) {
            if (!active.player.isAlive() || active.player.isRemoved() || !active.target.isAlive() || active.target.isRemoved()
                    || active.player.server.getPlayerList().getPlayer(active.key.owner) != active.player
                    || active.player.level() != active.target.level()) {
                finish(active.key.owner, active.key.cast, true); continue;
            }
            long now=event.getServer().getTickCount();
            if(!active.capped && (V3KiShots.hasActiveCast(active.key.owner,active.key.cast)
                    || PROJECTILES.entrySet().stream().anyMatch(e -> active.key.equals(e.getValue()) && e.getKey().isFiring()))){
                active.lifetime.firing(now);active.releaseAt=active.lifetime.firstFire();
            }
            if(!active.capped && active.lifetime.expired(now)){
                active.capped=true;
                V3KiShots.cancelCast(active.key.owner,active.key.cast);
                for(var e:new HashMap<>(PROJECTILES).entrySet())if(active.key.equals(e.getValue())){
                    e.getKey().discard();PROJECTILES.remove(e.getKey());
                }
                active.send(true);
                if(!active.prefix.isEmpty())com.dragonminez.common.network.NetworkHandler.sendToTrackingEntityAndSelf(
                    new com.dragonminez.common.network.S2C.TriggerAnimationS2C(active.key.owner,
                        com.dragonminez.common.network.S2C.TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP,0,-1,""),active.player);
            }
            if (!active.capped && active.prefix.isEmpty()) for (var e : PROJECTILES.entrySet()) {
                if (active.key.equals(e.getValue()) && e.getKey().isFiring()) {
                    V3TechniqueDefinition t = V3TechniqueCatalog.all().stream()
                            .filter(row -> V3NativeKi.nativeId(row.id()).equals(e.getKey().getTechniqueId())).findFirst().orElse(null);
                    if (t != null) firePose(active.player, active.key.cast, V3NativeKi.authoredAnimationPrefix(t));
                    break;
                }
            }
            if (active.automatic && !active(active.key.owner, active.key.cast)) finish(active.key.owner, active.key.cast, false);
        }
    }
    @SubscribeEvent public static void tracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer) || !net.bullettrain.xenopixelsmod.api.network.AddonNetwork.isReady()) return;
        for (var active : CASTS.values()) if (active.player == event.getTarget())
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(observer, active.packet(false));
    }
    @SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (var key : List.copyOf(CASTS.keySet())) finish(key.owner, key.cast, true);
        PROJECTILES.clear(); SPAWNING.remove();
    }
}
