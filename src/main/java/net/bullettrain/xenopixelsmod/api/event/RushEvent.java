package net.bullettrain.xenopixelsmod.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired for BT3 cinematic rushes - the scripted multi-hit sequences that run after a combo is
 * armed. Posted on {@code NeoForge.EVENT_BUS}, server side only.
 *
 * <p>The rush is identified by its string id rather than by its definition object, so that
 * subscribers keep compiling if the definition type moves.
 */
public abstract class RushEvent extends Event {

	private final ServerPlayer player;
	private final LivingEntity target;
	private final String rushId;

	protected RushEvent(ServerPlayer player, LivingEntity target, String rushId) {
		this.player = player;
		this.target = target;
		this.rushId = rushId;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	/**
	 * The entity being rushed. May be null on {@link Interrupt} only: a rush is often interrupted
	 * precisely because the target died or left, so it can no longer be resolved.
	 */
	public LivingEntity getTarget() {
		return target;
	}

	/** The id of the rush definition that resolved for this player's race and active form. */
	public String getRushId() {
		return rushId;
	}

	/**
	 * Fired just before a rush begins, after the follow-up gate, range and config checks have all
	 * passed and the definition has resolved. Cancelling prevents the rush: no sequence is started
	 * and no packet is sent to tracking clients.
	 */
	public static class Start extends RushEvent implements ICancellableEvent {
		public Start(ServerPlayer player, LivingEntity target, String rushId) {
			super(player, target, rushId);
		}
	}

	/**
	 * Fired for each blow of a rush that actually connects, after the target has taken the damage.
	 * A blow the target resisted does not fire this. Not cancellable - the damage is already dealt
	 * by the time it runs.
	 */
	public static class Impact extends RushEvent {

		private final int index;
		private final float damage;
		private final boolean finisher;

		public Impact(ServerPlayer player, LivingEntity target, String rushId,
				int index, float damage, boolean finisher) {
			super(player, target, rushId);
			this.index = index;
			this.damage = damage;
			this.finisher = finisher;
		}

		/** Zero-based position of this blow within the rush. */
		public int getIndex() {
			return index;
		}

		/** The damage this blow was dealt with, before the target's own reductions. */
		public float getDamage() {
			return damage;
		}

		/** True for the last blow, which carries the heavier knockback and damage scale. */
		public boolean isFinisher() {
			return finisher;
		}
	}

	/**
	 * Fired when a rush ends early - the player died, went spectator, the target was lost, or the
	 * feature was switched off mid-sequence. A rush that ran to completion does not fire this.
	 */
	public static class Interrupt extends RushEvent {
		public Interrupt(ServerPlayer player, LivingEntity target, String rushId) {
			super(player, target, rushId);
		}
	}
}
