package net.bullettrain.xenopixelsmod.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired for XenoCombat v2 and v3 grabs and throws. Posted on {@code NeoForge.EVENT_BUS}, server
 * side only, while the matching rewritten combat controller owns the action.
 */
public abstract class GrabEvent extends Event {
	private final ServerPlayer grabber;
	private final LivingEntity victim;

	protected GrabEvent(ServerPlayer grabber, LivingEntity victim) {
		this.grabber = grabber;
		this.victim = victim;
	}

	/** The fighter doing the grabbing. */
	public ServerPlayer getGrabber() {
		return grabber;
	}

	/** Whoever was caught. A player, an NPC or a mob. */
	public LivingEntity getVictim() {
		return victim;
	}

	/**
	 * The grab's startup has ended with the victim still in reach, and the hold is about to begin.
	 * Cancel to make the grab whiff; the grabber still pays its cost and cooldown.
	 */
	public static class Connect extends GrabEvent implements ICancellableEvent {
		public Connect(ServerPlayer grabber, LivingEntity victim) {
			super(grabber, victim);
		}
	}

	/** The hold has ended and the victim has been thrown. */
	public static class Throw extends GrabEvent {
		private final float damage;

		public Throw(ServerPlayer grabber, LivingEntity victim, float damage) {
			super(grabber, victim);
			this.damage = damage;
		}

		/** The damage dealt by the throw. */
		public float getDamage() {
			return damage;
		}
	}

	/** The victim broke free inside the tech window. */
	public static class Tech extends GrabEvent {
		public Tech(ServerPlayer grabber, LivingEntity victim) {
			super(grabber, victim);
		}
	}
}
