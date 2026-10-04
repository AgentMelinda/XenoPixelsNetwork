package net.bullettrain.xenopixelsmod.api.dmz;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pushes DragonMineZ state to clients after you have changed it on the server.
 *
 * <p>DragonMineZ state does not replicate on its own. Change it server side and the server is
 * right while every client still shows the old numbers, which reads as a bug that only some
 * players can see. This is the single easiest thing to forget when writing an addon, so the three
 * useful calls are gathered here with a note on which to pick.
 *
 * <p>Call these <em>after</em> your mutation, on the server thread.
 */
public final class DmzSync {

	private DmzSync() {}

	/**
	 * Sends the player's full stats to that player and to everyone tracking them.
	 *
	 * <p>The safe default. Use it after changing anything you are unsure about, or several things
	 * at once.
	 */
	public static void syncStats(ServerPlayer player) {
		if (player == null) return;
		NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
	}

	/**
	 * Sends progression - stats, bonus stats, skills, techniques and quests.
	 *
	 * <p>Use after changing skills, techniques or quest state.
	 */
	public static void syncProgression(ServerPlayer player) {
		if (player == null) return;
		NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(player), player);
	}

	/**
	 * Sends resources and status - health, ki, stamina, power release, action state.
	 *
	 * <p>The cheapest of the three, so it suits anything that changes every tick.
	 *
	 * <p>XenoPixels merges this packet on the client without running {@code StatsData.load},
	 * because DragonMineZ 2.1.3 throws when {@code PlayerQuestData} is absent. It still cannot
	 * populate a client that has never received {@link #syncStats(ServerPlayer)}.
	 */
	public static void syncResources(ServerPlayer player) {
		if (player == null) return;
		NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
	}
}
