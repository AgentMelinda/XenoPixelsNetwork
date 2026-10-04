package net.bullettrain.xenopixelsmod.npc.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * One thing an NPC may leave behind.
 *
 * <p>A full stack, like the gear slots, so a drop can be an enchanted sword or a written book rather
 * than only a plain item. Held as a tag for the reasons in {@link NpcSlotStack}.
 *
 * <p>The chance is a percentage rather than a 0-1 fraction because that is what the reference
 * editor's field shows and what an operator types into it.
 */
public record NpcDrop(NpcSlotStack item, float chance) {

    /** A drop nobody has configured: nothing, and certain — so filling in the item is enough. */
    public static final float DEFAULT_CHANCE = 100.0f;

    public NpcDrop {
        item = item == null ? NpcSlotStack.EMPTY : item;
        // Outside 0-100 is not a meaning anybody intended. Clamping rather than rejecting keeps a
        // fat-fingered 1000 from silently disabling a drop somebody thought they had made certain.
        chance = Float.isNaN(chance) ? 0.0f : Math.max(0.0f, Math.min(100.0f, chance));
    }

    public static NpcDrop empty() {
        return new NpcDrop(NpcSlotStack.EMPTY, DEFAULT_CHANCE);
    }

    /** Whether this row names something to drop at all. */
    public boolean usable() {
        return !item.isEmpty() && chance > 0.0f;
    }

    /** What this row drops, or {@link ItemStack#EMPTY} if its item cannot be resolved. */
    public ItemStack stack(HolderLookup.Provider registries) {
        return item.stack(registries);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag saved = item.saved();
        if (saved != null) {
            tag.put("I", saved);
        }
        tag.putFloat("P", chance);
        return tag;
    }

    public static NpcDrop load(CompoundTag tag) {
        if (tag == null) {
            return empty();
        }
        return new NpcDrop(
                NpcSlotStack.load(tag.contains("I") ? tag.getCompound("I") : null),
                tag.contains("P") ? tag.getFloat("P") : DEFAULT_CHANCE);
    }
}
