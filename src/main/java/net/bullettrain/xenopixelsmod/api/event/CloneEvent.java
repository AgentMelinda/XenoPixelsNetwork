package net.bullettrain.xenopixelsmod.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired for the multiform clone system, where a fighter splits into several bodies that share one
 * health pool. Posted on {@code NeoForge.EVENT_BUS}, server side only.
 */
public abstract class CloneEvent extends Event {

	private final ServerPlayer player;

	protected CloneEvent(ServerPlayer player) {
		this.player = player;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	/**
	 * Fired just before a fighter splits, after the body count has been resolved and found to be
	 * greater than one. Cancelling prevents the split entirely.
	 */
	public static class Split extends CloneEvent implements ICancellableEvent {

		private final int bodyCount;

		public Split(ServerPlayer player, int bodyCount) {
			super(player);
			this.bodyCount = bodyCount;
		}

		/**
		 * How many bodies the fighter will become, counting themselves. Always at least two, since
		 * a split into one body is rejected before this fires.
		 */
		public int getBodyCount() {
			return bodyCount;
		}
	}

	/**
	 * Fired when the clones start returning, once the recall has actually been accepted. Calling
	 * reunite on a fighter who is not split, or whose recall is already under way, does not fire
	 * this. Note the clones are still travelling back at this point - health is merged as each one
	 * arrives, not here.
	 */
	public static class Reunite extends CloneEvent {
		public Reunite(ServerPlayer player) {
			super(player);
		}
	}
}
