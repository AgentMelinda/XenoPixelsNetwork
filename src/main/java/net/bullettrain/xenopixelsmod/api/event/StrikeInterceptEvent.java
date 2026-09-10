package net.bullettrain.xenopixelsmod.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired when a DragonMineZ technique slot is about to be diverted into a XenoPixels move.
 *
 * <p>Moves such as Hakai, Zanzoken and Shi Shin No Ken are registered in DragonMineZ's strike
 * registry so they can be equipped to a slot, but they are not melee strikes and must not go
 * through DMZ's strike lifecycle. XenoPixels intercepts the slot before DMZ resolves it and runs
 * its own move instead. This event fires at that interception point, on
 * {@code NeoForge.EVENT_BUS}, server side only.
 *
 * <p><b>Cancelling vetoes the interception, not the slot.</b> The XenoPixels move does not run and
 * DragonMineZ handles the slot normally, exactly as if XenoPixels had never claimed it.
 *
 * <p>This is a XenoPixels event rather than a DragonMineZ one because DMZ's own
 * {@code StrikeAttackCastEvent} and {@code StrikeAttackFireEvent} are plain, non-cancellable
 * events - listening to either would run the XenoPixels move <em>and</em> DMZ's strike.
 */
public class StrikeInterceptEvent extends Event implements ICancellableEvent {

	private final ServerPlayer player;
	private final int slotIndex;
	private final String techniqueId;

	public StrikeInterceptEvent(ServerPlayer player, int slotIndex, String techniqueId) {
		this.player = player;
		this.slotIndex = slotIndex;
		this.techniqueId = techniqueId;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	/** Which of the player's equipped technique slots was triggered. */
	public int getSlotIndex() {
		return slotIndex;
	}

	/** The XenoPixels technique id equipped in that slot, for example {@code hakai}. */
	public String getTechniqueId() {
		return techniqueId;
	}
}
