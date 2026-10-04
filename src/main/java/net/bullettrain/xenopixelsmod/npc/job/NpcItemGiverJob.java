package net.bullettrain.xenopixelsmod.npc.job;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack;
import net.bullettrain.xenopixelsmod.npc.lines.XenoNpcSpeech;
import net.bullettrain.xenopixelsmod.features.progression.QuestRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Gives authored items when a player enters the NPC's three-block area. */
public final class NpcItemGiverJob {
    private static final int CHECK_TICKS = 10;
    private static final Map<XenoNpcEntity, Set<UUID>> RECENT = new WeakHashMap<>();

    private NpcItemGiverJob() {}

    public static void tick(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (npc == null || npc.level().isClientSide()) return;
        if (profile == null || !profile.jobEnabled
                || XenoNpcJob.byId(profile.job) != XenoNpcJob.ITEM_GIVER) {
            RECENT.remove(npc);
            return;
        }
        if (npc.getTarget() != null || (npc.tickCount + npc.getId()) % CHECK_TICKS != 0) return;

        Set<UUID> recent = RECENT.computeIfAbsent(npc, ignored -> new HashSet<>());
        Set<UUID> nearby = new HashSet<>();
        for (ServerPlayer player : npc.level().getEntitiesOfClass(ServerPlayer.class,
                npc.getBoundingBox().inflate(10))) nearby.add(player.getUUID());
        recent.retainAll(nearby);
        for (ServerPlayer player : npc.level().getEntitiesOfClass(ServerPlayer.class,
                npc.getBoundingBox().inflate(3))) {
            if (!npc.hasLineOfSight(player) || !recent.add(player.getUUID())) continue;
            give(npc, profile, player);
        }
    }

    private static void give(XenoNpcEntity npc, NpcCombatProfile profile, ServerPlayer player) {
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return;
        if (!QuestRuntime.available(player, data, profile.itemGiverAvailability)) return;
        long now = System.currentTimeMillis();
        long day = npc.level().getGameTime() / 24000L;
        XenoPlayerData.ItemGiverUse previous = data.itemGiverUse(npc.getUUID());
        if (!ready(profile.itemGiverCooldownType, profile.itemGiverCooldown,
                previous, now, day)) return;

        List<Slot> available = new ArrayList<>();
        for (int index = 0; index < NpcCombatProfile.ITEM_GIVER_SLOTS; index++) {
            NpcSlotStack saved = profile.itemGiverItems[index];
            ItemStack stack = saved == null ? ItemStack.EMPTY
                    : saved.stack(npc.registryAccess());
            if (!stack.isEmpty()) available.add(new Slot(index, stack));
        }
        if (available.isEmpty()) return;
        List<Integer> availableSlots = available.stream().map(Slot::index).toList();
        Set<Integer> ownedSlots = new HashSet<>();
        for (Slot slot : available) if (hasItem(player, slot.stack())) ownedSlots.add(slot.index());
        List<Integer> selectedSlots = selectSlots(profile.itemGiverMethod, availableSlots,
                ownedSlots, previous == null ? 0 : previous.nextSlot(),
                npc.getRandom().nextInt(available.size()));
        List<Slot> selected = available.stream()
                .filter(slot -> selectedSlots.contains(slot.index())).toList();
        if (selected.isEmpty() || freeSlots(player) < selected.size()) return;

        // Empty-slot preflight matches MyNPCs. Server thread owns both check and insertion.
        for (Slot slot : selected) {
            if (!player.getInventory().add(slot.stack().copy())) return;
        }
        int nextSlot = profile.itemGiverMethod == 4
                ? (selected.get(0).index() + 1) % NpcCombatProfile.ITEM_GIVER_SLOTS : 0;
        data.recordItemGiverUse(npc.getUUID(), now, day, nextSlot);
        if (!profile.itemGiverLines.isEmpty()) {
            XenoNpcSpeech.say(npc, profile.itemGiverLines.get(
                    npc.getRandom().nextInt(profile.itemGiverLines.size())), player);
        }
    }

    public static boolean ready(int type, int seconds, XenoPlayerData.ItemGiverUse previous,
                                long now, long day) {
        if (previous == null) return true;
        return switch (type) {
            case 0 -> now >= previous.lastMillis()
                    && now - previous.lastMillis() >= Math.max(0, seconds) * 1000L;
            case 1 -> false;
            case 2 -> day > previous.lastDay();
            default -> false;
        };
    }

    public static List<Integer> selectSlots(int method, List<Integer> available,
                                            Set<Integer> owned, int nextSlot, int randomIndex) {
        if (available == null || available.isEmpty()) return List.of();
        Set<Integer> has = owned == null ? Set.of() : owned;
        return switch (method) {
            case 0 -> List.of(available.get(Math.floorMod(randomIndex, available.size())));
            case 1 -> List.copyOf(available);
            case 2 -> available.stream().filter(slot -> !has.contains(slot)).toList();
            case 3 -> available.stream().anyMatch(has::contains)
                    ? List.of() : List.copyOf(available);
            case 4 -> {
                int chosen = available.get(0);
                for (int slot : available) {
                    if (slot >= nextSlot) { chosen = slot; break; }
                }
                yield List.of(chosen);
            }
            default -> List.of();
        };
    }

    private static boolean hasItem(ServerPlayer player, ItemStack sample) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(sample.getItem())) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty() && stack.is(sample.getItem())) return true;
        }
        return false;
    }

    private static int freeSlots(ServerPlayer player) {
        int free = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.isEmpty()) free++;
        return free;
    }

    private record Slot(int index, ItemStack stack) {}
}
