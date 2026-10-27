package net.bullettrain.xenopixelsmod.npc.dialog;

import com.dragonminez.common.dialogue.DialogueService;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.storage.StorageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

/**
 * Server-only native DMZ quest presentation. The caller must first resolve the option from the
 * server's own NPC/script dialogue and check its availability. The client supplies no native id.
 * DMZ retains its quest availability, alignment, acceptance, turn-in, rewards and player storage.
 */
public final class DmzNpcQuestBridge {
    private static final Method OPEN = resolve();
    private DmzNpcQuestBridge() {}

    private static Method resolve() {
        String version = net.neoforged.fml.ModList.get().getModContainerById("dragonminez")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
        if (!(version.equals("2.2") || version.startsWith("2.2."))) return null;
        try {
            // Exact 2.2 javap: this method collects NPCQuestOptions server-side, computes native
            // disposition with the real host, and sends OpenQuestNPCDialogueS2C to this player.
            Method method = DialogueService.class.getDeclaredMethod("openQuestScreen",
                    ServerPlayer.class, StatsData.class, String.class, int.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException error) {
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                    "DragonMineZ native NPC quest contract unavailable", error);
            return null;
        }
    }

    public static void open(ServerPlayer player, Entity host, String npcId) {
        if (host == null || !DmzNpcQuestPolicy.mayOpen(player.isAlive(), host.isAlive(),
                player.level() == host.level(), StorageManager.isLoadPending(player),
                player.distanceToSqr(host), npcId)) {
            player.sendSystemMessage(Component.literal("That DMZ quest giver is unavailable or out of reach."));
            return;
        }
        StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (OPEN == null || stats == null) {
            player.sendSystemMessage(Component.literal("Native DMZ quests require the supported DragonMineZ 2.2 quest service."));
            return;
        }
        try {
            OPEN.invoke(null, player, stats, npcId, host.getId());
        } catch (ReflectiveOperationException | RuntimeException error) {
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn("Cannot open native DMZ quests", error);
            player.sendSystemMessage(Component.literal("The native DMZ quest menu could not be opened."));
        }
    }
}
