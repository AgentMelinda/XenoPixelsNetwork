package net.bullettrain.xenopixelsmod.npc.inventory;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.List;

/**
 * Opens an NPC's slots, filled from what it is actually carrying.
 *
 * <p>Server-side, and the only way the menu is ever built: the containers are seeded here from the
 * profile and from Curios, and the menu writes them back when it closes. Nothing the client sends
 * decides any of it.
 */
public final class NpcInventoryService {

    private NpcInventoryService() {
    }

    /**
     * Shows one NPC's gear, drops and Curios to a player.
     *
     * <p>The caller has already checked permission and distance — this is the last step, not the
     * gate. It is separate from the packet so a command or another screen could open the same view
     * without going through the wire.
     */
    public static void open(ServerPlayer player, XenoNpcEntity npc) {
        if (player == null || npc == null) {
            return;
        }
        var registries = player.level().registryAccess();
        NpcCombatProfile profile = NpcCombatProfile.read(npc);

        SimpleContainer gear = new SimpleContainer(XenoNpcInventoryMenu.GEAR_SLOTS);
        for (int i = 0; i < XenoNpcInventoryMenu.GEAR_SLOTS; i++) {
            gear.setItem(i, profile.gear.stack(i, registries));
        }

        SimpleContainer drops = new SimpleContainer(XenoNpcInventoryMenu.DROP_SLOTS);
        for (int i = 0; i < XenoNpcInventoryMenu.DROP_SLOTS; i++) {
            drops.setItem(i, profile.drops.get(i).stack(registries));
        }

        // Asked of Curios rather than assumed: an NPC on a server without DragonMineZ has no slots
        // and gets none drawn, rather than two that go nowhere.
        List<String> curioSlots = NpcCurios.slots(npc);
        SimpleContainer curios = new SimpleContainer(Math.max(1, curioSlots.size()));
        for (int i = 0; i < curioSlots.size(); i++) {
            curios.setItem(i, NpcCurios.get(npc, curioSlots.get(i), 0));
        }

        int entityId = npc.getId();
        String name = npc.getName().getString();

        player.openMenu(new SimpleMenuProvider(
                (windowId, inventory, opener) -> new XenoNpcInventoryMenu(windowId, inventory, npc,
                        entityId, name, curioSlots, gear, drops, curios),
                Component.literal(name)),
                buf -> {
                    buf.writeVarInt(entityId);
                    buf.writeUtf(name, 64);
                    buf.writeVarInt(curioSlots.size());
                    for (String slot : curioSlots) {
                        buf.writeUtf(slot, 64);
                    }
                });
    }
}
