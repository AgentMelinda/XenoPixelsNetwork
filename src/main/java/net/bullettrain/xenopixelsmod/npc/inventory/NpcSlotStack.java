package net.bullettrain.xenopixelsmod.npc.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * One slot's contents, held as the tag an {@link ItemStack} serialises to.
 *
 * <p><b>The tag is the storage, and the stack is a view of it.</b> That is not the obvious way
 * round, and there are two reasons for it.
 *
 * <p>The first is mechanical: since 1.20.5 an {@code ItemStack} cannot be written or read without a
 * {@link HolderLookup.Provider} — {@code save} and {@code parse} both demand one — and
 * {@code NpcCombatProfile.toTag()} has no registry access at all. It is called from packet
 * encoding, from snapshots and from tests. Holding live stacks in the profile would mean threading a
 * provider through every one of those call sites.
 *
 * <p>The second is the reason it is an improvement rather than a workaround. An item whose mod has
 * been uninstalled does not parse, and a slot holding a parsed stack would have nothing left to
 * write back — the item would be gone for good the first time the world loaded without that mod.
 * Keeping the tag means an unreadable slot is copied out exactly as it came in, so the sword comes
 * back when the mod does. That is the same property item ids give {@code NpcTrade}, without giving
 * up the enchantments and custom names an id cannot carry.
 *
 * <p>Immutable, and the tag is copied on the way in and on the way out, because a shared mutable
 * {@code CompoundTag} handed to two NPCs is a bug that shows up much later than it is caused.
 */
public record NpcSlotStack(CompoundTag tag) {

    /** An empty slot. */
    public static final NpcSlotStack EMPTY = new NpcSlotStack(null);

    /**
     * How large a single slot's tag may be.
     *
     * <p>A written book with a shulker box of them nested inside is the shape this bounds. Sixteen
     * kilobytes is far past any ordinary item and far short of anything that would trouble a save
     * or a packet, and the save policy enforces it before allocating.
     */
    public static final int MAX_TAG_BYTES = 16 * 1024;

    public NpcSlotStack {
        tag = tag == null || tag.isEmpty() ? null : tag.copy();
    }

    public boolean isEmpty() {
        return tag == null;
    }

    /** The tag to write, or null when the slot is empty. Always a copy. */
    public CompoundTag saved() {
        return tag == null ? null : tag.copy();
    }

    /**
     * The stack this slot holds.
     *
     * <p>{@link ItemStack#EMPTY} when the slot is empty <em>and</em> when its item cannot be
     * resolved — a mod removed since it was authored. The stored tag is untouched either way, so an
     * empty answer here never destroys what is saved.
     */
    public ItemStack stack(HolderLookup.Provider registries) {
        if (tag == null || registries == null) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY);
    }

    /** Whether this slot names something the running game can actually resolve. */
    public boolean resolvable(HolderLookup.Provider registries) {
        return !stack(registries).isEmpty();
    }

    /** A slot holding this stack, or {@link #EMPTY} when the stack is. */
    public static NpcSlotStack of(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty() || registries == null) {
            return EMPTY;
        }
        Tag saved = stack.save(registries);
        return saved instanceof CompoundTag compound ? new NpcSlotStack(compound) : EMPTY;
    }

    /** A slot read straight from a saved tag, without resolving anything. */
    public static NpcSlotStack load(CompoundTag tag) {
        return tag == null || tag.isEmpty() ? EMPTY : new NpcSlotStack(tag);
    }

    /** Whether a tag is small enough to accept off the wire. */
    public static boolean withinBounds(CompoundTag tag) {
        return tag == null || tagBytes(tag) <= MAX_TAG_BYTES;
    }

    /**
     * A tag's serialised size, <b>uncompressed</b>.
     *
     * <p>Measured rather than estimated from the key count: a single string field can carry a
     * megabyte, so counting entries would bound nothing.
     *
     * <p>Uncompressed on purpose. Measuring the gzipped size lets a tag full of repeated bytes slip
     * through at a fraction of its real weight and then expand to whatever it likes once parsed -
     * a client choosing an allocation, which is exactly what this bound exists to stop. What
     * matters is how much memory the thing takes when it is a live tag, not how small it was in
     * transit.
     */
    public static int tagBytes(CompoundTag tag) {
        if (tag == null) {
            return 0;
        }
        CountingOutput counter = new CountingOutput();
        try {
            net.minecraft.nbt.NbtIo.write(tag, counter);
        } catch (java.io.IOException e) {
            // Unwritable is not something to accept off the wire either.
            return Integer.MAX_VALUE;
        }
        return counter.count();
    }

    /**
     * Counts bytes without keeping them.
     *
     * <p>A {@code ByteArrayOutputStream} would allocate the very thing being measured, which for a
     * hostile tag is the allocation this is meant to refuse. Saturating at {@link Integer#MAX_VALUE}
     * so an enormous tag reports enormous rather than wrapping to a small number.
     */
    private static final class CountingOutput implements java.io.DataOutput {
        private long written;

        int count() {
            return written > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) written;
        }

        private void add(long bytes) {
            written += bytes;
        }

        @Override public void write(int b) { add(1); }
        @Override public void write(byte[] b) { add(b.length); }
        @Override public void write(byte[] b, int off, int len) { add(len); }
        @Override public void writeBoolean(boolean v) { add(1); }
        @Override public void writeByte(int v) { add(1); }
        @Override public void writeShort(int v) { add(2); }
        @Override public void writeChar(int v) { add(2); }
        @Override public void writeInt(int v) { add(4); }
        @Override public void writeLong(long v) { add(8); }
        @Override public void writeFloat(float v) { add(4); }
        @Override public void writeDouble(double v) { add(8); }
        @Override public void writeBytes(String s) { add(s.length()); }
        @Override public void writeChars(String s) { add(2L * s.length()); }

        @Override
        public void writeUTF(String s) {
            // Two length bytes plus the modified-UTF-8 body. Counted rather than assumed one byte
            // per character, because NBT keys and string values are where the weight actually is.
            long bytes = 2;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                bytes += c >= 0x0001 && c <= 0x007F ? 1 : c > 0x07FF ? 3 : 2;
            }
            add(bytes);
        }
    }
}
