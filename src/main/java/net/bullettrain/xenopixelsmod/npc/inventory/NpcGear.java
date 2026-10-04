package net.bullettrain.xenopixelsmod.npc.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * What an NPC wears and holds.
 *
 * <p>Six slots of full stacks — see {@link NpcSlotStack} for why they are stored as tags rather than
 * as live {@code ItemStack}s. A Sharpness V sword stays Sharpness V, and a named helmet keeps its
 * name, which an item id could never carry.
 *
 * <p>The reference editor's Inventory page draws a seventh, Projectile. It is not here because
 * nothing would fire it: there is no {@code RangedAttackMob}, no ranged goal, and
 * {@code aimAccuracy} sits in the profile with no consumer. A slot there would be a control with
 * nothing behind it.
 *
 * <p><b>Worn gear never drops.</b> Drops are authored on {@link NpcDropList}, which is a separate
 * nine-entry list with its own chances — the reference page has nine chance fields and only seven
 * slot buttons, so they were never one per slot. Every slot is therefore equipped with a drop
 * chance of zero, and a player cannot strip an NPC's authored appearance by killing it.
 */
public final class NpcGear {

    /** The slots the editor page offers, in the order it draws them. */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
    };

    /** What each slot is called on screen, in {@link #SLOTS} order. */
    public static final String[] LABELS = {
        "Head", "Chest", "Legs", "Feet", "Main Hand", "Off Hand",
    };

    /**
     * Resolves a stack to the worn slot used by this inventory, preserving DMZ's armor-item path
     * even when an item does not expose the generic equipment hook consistently.
     */
    public static EquipmentSlot equipmentSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EquipmentSlot.MAINHAND;
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getEquipmentSlot();
        }
        return stack.getEquipmentSlot();
    }

    /** NBT keys, one per slot, in {@link #SLOTS} order. */
    private static final String[] KEYS = {"Head", "Chest", "Legs", "Feet", "Main", "Off"};

    private static final String TAG_GEAR = "Gear";

    private final NpcSlotStack[] slots = new NpcSlotStack[SLOTS.length];

    public NpcGear() {
        Arrays.fill(slots, NpcSlotStack.EMPTY);
    }

    public int size() {
        return slots.length;
    }

    /** What one editor row holds. */
    public NpcSlotStack get(int index) {
        return index >= 0 && index < slots.length ? slots[index] : NpcSlotStack.EMPTY;
    }

    public NpcSlotStack get(EquipmentSlot slot) {
        return get(indexOf(slot));
    }

    public void set(int index, NpcSlotStack value) {
        if (index >= 0 && index < slots.length) {
            slots[index] = value == null ? NpcSlotStack.EMPTY : value;
        }
    }

    public void set(EquipmentSlot slot, NpcSlotStack value) {
        set(indexOf(slot), value);
    }

    /** What one slot holds right now, or {@link ItemStack#EMPTY}. */
    public ItemStack stack(int index, HolderLookup.Provider registries) {
        return get(index).stack(registries);
    }

    public void setStack(int index, ItemStack stack, HolderLookup.Provider registries) {
        set(index, NpcSlotStack.of(stack, registries));
    }

    public boolean isEmpty() {
        for (NpcSlotStack slot : slots) {
            if (!slot.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        Arrays.fill(slots, NpcSlotStack.EMPTY);
    }

    public void copyFrom(NpcGear other) {
        if (other == null) {
            clear();
            return;
        }
        System.arraycopy(other.slots, 0, slots, 0, slots.length);
    }

    /**
     * Puts this gear on the entity.
     *
     * <p>Every slot is written, including the empty ones: an operator who clears a field expects the
     * helmet to come off, and a slot whose mod was removed should not leave the NPC wearing a ghost
     * of it. The stored tag survives either way, so the item returns when its mod does.
     */
    public void applyTo(Mob mob) {
        if (mob == null) {
            return;
        }
        HolderLookup.Provider registries = mob.registryAccess();
        for (int i = 0; i < SLOTS.length; i++) {
            mob.setItemSlot(SLOTS[i], slots[i].stack(registries));
            mob.setDropChance(SLOTS[i], 0.0f);
        }
    }

    /** Written only when something is worn, so an ordinary NPC's tag is unchanged. */
    public void saveTo(CompoundTag tag) {
        if (isEmpty()) {
            return;
        }
        CompoundTag gear = new CompoundTag();
        for (int i = 0; i < slots.length; i++) {
            CompoundTag saved = slots[i].saved();
            if (saved != null) {
                gear.put(KEYS[i], saved);
            }
        }
        tag.put(TAG_GEAR, gear);
    }

    public void loadFrom(CompoundTag tag) {
        clear();
        if (tag == null || !tag.contains(TAG_GEAR)) {
            return;
        }
        CompoundTag gear = tag.getCompound(TAG_GEAR);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = NpcSlotStack.load(
                    gear.contains(KEYS[i]) ? gear.getCompound(KEYS[i]) : null);
        }
    }

    private static int indexOf(EquipmentSlot slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
