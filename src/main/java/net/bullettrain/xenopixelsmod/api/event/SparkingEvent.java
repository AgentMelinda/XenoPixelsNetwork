package net.bullettrain.xenopixelsmod.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired for the BT3 Sparking meter and buff. Posted on {@code NeoForge.EVENT_BUS}, server side only.
 */
public abstract class SparkingEvent extends Event {

	private final ServerPlayer player;

	protected SparkingEvent(ServerPlayer player) {
		this.player = player;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	/**
	 * Fired just before Sparking turns on, after the skill, cooldown and meter checks have all
	 * passed. Cancelling stops the activation: no buff, no speed modifier, and the meter is not
	 * refunded, because it was already spent by the time this fires.
	 *
	 * <p>Fired from the single private funnel both activation routes go through, so it catches the
	 * meter route and the ki-charge route alike.
	 */
	public static class Activate extends SparkingEvent implements ICancellableEvent {

		private int durationTicks;

		public Activate(ServerPlayer player, int durationTicks) {
			super(player);
			this.durationTicks = durationTicks;
		}

		/** How long Sparking will last, in ticks. */
		public int getDurationTicks() {
			return durationTicks;
		}

		/**
		 * Changes how long Sparking lasts. Clamped to at least one tick.
		 *
		 * <p>The ceiling is left to the caller on purpose: ki-charge Sparking is ended by the ki
		 * bar draining rather than by this number, and it passes a deliberately large value for
		 * that reason.
		 */
		public void setDurationTicks(int durationTicks) {
			this.durationTicks = Math.max(1, durationTicks);
		}
	}

	/**
	 * Fired when Sparking ends, whatever ended it - expiry, ki running out, death or logout.
	 * Only fired when the player really was sparking, never speculatively. Not cancellable: by the
	 * time this runs the state has already been torn down.
	 */
	public static class Deactivate extends SparkingEvent {
		public Deactivate(ServerPlayer player) {
			super(player);
		}
	}

	/**
	 * Fired when the Sparking meter changes. The meter runs from 0 to 100 and fills from combat.
	 * Not cancellable.
	 */
	public static class MeterChanged extends SparkingEvent {

		private final float previous;
		private final float current;

		public MeterChanged(ServerPlayer player, float previous, float current) {
			super(player);
			this.previous = previous;
			this.current = current;
		}

		public float getPrevious() {
			return previous;
		}

		public float getCurrent() {
			return current;
		}

		/** True once the meter has reached full and Sparking can be activated. */
		public boolean isFull() {
			return current >= 100f;
		}
	}
}
