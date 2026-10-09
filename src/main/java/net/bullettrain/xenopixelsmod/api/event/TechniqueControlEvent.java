package net.bullettrain.xenopixelsmod.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Server-side admission of a V3 technique that controls a victim without dealing damage.
 * Posted on {@code NeoForge.EVENT_BUS} after target/permission checks and before cooldown,
 * Ki payment, freezing, or movement. Cancel to refuse the entire cast with no cost and no
 * native fallback. Damage-only protection listeners should also handle this event when
 * protecting entities from paralysis and other non-damaging control techniques.
 * All three properties are non-null and describe the server-approved action.
 */
public final class TechniqueControlEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final LivingEntity target;
    private final String techniqueId;

    public TechniqueControlEvent(ServerPlayer player, LivingEntity target, String techniqueId) {
        this.player = java.util.Objects.requireNonNull(player);
        this.target = java.util.Objects.requireNonNull(target);
        this.techniqueId = java.util.Objects.requireNonNull(techniqueId);
    }
    public ServerPlayer getPlayer() { return player; }
    public LivingEntity getTarget() { return target; }
    public String getTechniqueId() { return techniqueId; }
}
