package net.bullettrain.xenopixelsmod.api.dmz;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAppearanceMapping;
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

	/**
	 * Sends the appearance half of the state - race, gender, body, hair, colours, active form.
	 *
	 * <p>DragonMineZ keeps this in its own packet, so {@link #syncStats} does not carry it: change a
	 * look and sync only stats, and the player's own client updates while everyone else keeps
	 * rendering the old body. DragonMineZ's own hair and tail commands send both packets for exactly
	 * that reason, so this call sends both too and you only have to remember one of them.
	 *
	 * <p>It also refreshes the player's size, which is what makes a race or form change take effect
	 * on the hitbox rather than only on the model.
	 */
	public static void syncAppearance(ServerPlayer player) {
		net.bullettrain.xenopixelsmod.compat.npc.NpcAppearanceMapping.sync(player);
	}
}
