package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcData;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;

/**
 * A whole NPC, carried on an item stack.
 *
 * <p>Shared by the Cloner and the NPC Jar, which do the same two things — take an NPC onto a stack,
 * and write one back into the world — and differ only in whether the original survives. Keeping the
 * copy in one place means the two cannot drift into copying subtly different amounts of an NPC,
 * which is the bug nobody notices until a cloned trader has no stock.
 *
 * <h2>What is copied</h2>
 *
 * <p>Everything. {@link XenoNpcData#editorPayload} is the same payload the editor screen is opened
 * with, so anything the editor can change comes across. That is deliberate rather than a curated
 * subset: a subset is a list that silently falls behind every time a field is added.
 *
 * <p>Writing it back is {@code restoreFromTag} plus a profile write — the identical two halves
 * {@code XenoNpcRespawnHandler} uses to bring a dead NPC back whole. Restoring only one is the bug
 * that made a respawned NPC come back as a default one wearing its old name.
 */
public final class XenoNpcPayload {

    /** Where the NPC lives on the stack. One key, so a Cloner and a Jar read each other's. */
    private static final String PAYLOAD_KEY = "XenoNpcPayload";

    /** The NPC's name, kept alongside so a tooltip need not deserialise the whole thing. */
    private static final String LABEL_KEY = "XenoNpcPayloadName";

    /**
     * How large a stored payload may be.
     *
     * <p>A fully authored NPC — dialogue, eighteen trades, a long patrol, a full inventory — is a
     * few kilobytes. This is far past that and far short of anything that would trouble a save, and
     * it is checked <em>before</em> the tag is written, so an NPC that somehow grew enormous is
     * refused with a message rather than quietly producing an item nobody can use.
     */
    public static final int MAX_BYTES = 256 * 1024;

    private XenoNpcPayload() {
    }

    /** Whether this stack is carrying an NPC. */
    public static boolean present(ItemStack stack) {
        return read(stack) != null;
    }

    /** The NPC on this stack, or null. */
    @Nullable
    public static CompoundTag read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(PAYLOAD_KEY) ? tag.getCompound(PAYLOAD_KEY) : null;
    }

    /** The stored NPC's name, or "". */
    public static String label(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString(LABEL_KEY);
    }

    /**
     * Takes an NPC onto a stack.
     *
     * @return null on success, or the reason it was refused
     */
    @Nullable
    public static String store(ItemStack stack, XenoNpcEntity npc) {
        if (stack == null || npc == null) {
            return "Nothing to copy.";
        }
        CompoundTag payload = XenoNpcData.editorPayload(npc, npc.npcData());
        if (NpcSlotStack.tagBytes(payload) > MAX_BYTES) {
            return "That NPC is too large to carry.";
        }
        String name = npc.getName().getString();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.put(PAYLOAD_KEY, payload);
            tag.putString(LABEL_KEY, name);
        });
        return null;
    }

    /** Forgets whatever the stack was carrying. */
    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(PAYLOAD_KEY);
            tag.remove(LABEL_KEY);
        });
    }

    /**
     * Writes a carried NPC onto a fresh one.
     *
     * <p>Order matters: identity and profile go on first and the home is set <em>after</em>, so the
     * payload's own home cannot win. That is the difference between an NPC that stands where it was
     * put and one that immediately walks back to where it was copied from.
     *
     * @param keepOwner true to keep the stored owner (releasing the same NPC you captured), false to
     *                  hand it to whoever is placing it (a clone is a new NPC)
     */
    public static void apply(XenoNpcEntity npc, CompoundTag payload, Player placer,
                             double x, double y, double z, boolean keepOwner) {
        npc.npcData().restoreFromTag(payload.copy());

        NpcCombatProfile profile = NpcCombatProfile.fromTag(payload.getCompound("Profile"));
        profile.write(npc);
        net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour.apply(npc, profile);

        npc.npcData().setHome(x, y, z);
        if (!keepOwner) {
            npc.npcData().setOwner(placer.getUUID());
            // Provenance is about where an NPC came from, and a clone was made here. Blanking it
            // rather than carrying the original's keeps a later import migration honest. A released
            // NPC is the same one that was captured, so it keeps its own.
            npc.npcData().setImportSource("", null);
        }
        npc.refreshNameplate();
    }
}
