package net.bullettrain.xenopixelsmod.npc.inventory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every line in this mod that names a Curios class.
 *
 * <p>Deliberately the only one. Curios is optional and undeclared, so a server without it must never
 * load a class that mentions {@code top.theillusivec4}; keeping the imports in one place makes that
 * something you can check by looking rather than something you have to trust. {@link NpcCurios} is
 * the gate, and nothing else may call in here.
 *
 * <p>Every symbol used below was read from {@code curios-neoforge-9.5.1+1.21.1}:
 * {@code CuriosApi.getCuriosInventory}, {@code CuriosApi.getEntitySlots},
 * {@code ICuriosItemHandler.getStacksHandler}, {@code ICuriosItemHandler.setEquippedCurio},
 * {@code ICurioStacksHandler.getStacks} and {@code getSlots}. DragonMineZ reaches the same
 * inventory through its own {@code CuriosUtil.getStack(LivingEntity, String, int)}, which takes a
 * plain {@code LivingEntity} - so reading an NPC's curios is a supported shape rather than a stretch
 * of an API meant only for players.
 */
final class NpcCuriosImpl {

    private NpcCuriosImpl() {
    }

    static List<String> slots(LivingEntity npc) {
        // Asked per entity rather than per entity type: getEntitySlots(LivingEntity) is what
        // accounts for the data-driven entity bindings, which is exactly how an NPC gets any.
        List<String> out = new ArrayList<>(CuriosApi.getEntitySlots(npc).keySet());
        // Curios' own map is unordered; the screen draws these left to right, and slots that moved
        // between openings would be unusable.
        out.sort(String::compareTo);
        return List.copyOf(out);
    }

    static ItemStack get(LivingEntity npc, String slot, int index) {
        return handler(npc, slot)
                .filter(stacks -> index >= 0 && index < stacks.getSlots())
                .map(stacks -> stacks.getStacks().getStackInSlot(index))
                .orElse(ItemStack.EMPTY);
    }

    static boolean set(LivingEntity npc, String slot, int index, ItemStack stack) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(npc);
        if (inventory.isEmpty()) {
            return false;
        }
        Optional<ICurioStacksHandler> stacks = inventory.get().getStacksHandler(slot);
        if (stacks.isEmpty() || index < 0 || index >= stacks.get().getSlots()) {
            return false;
        }
        ItemStack next = stack == null ? ItemStack.EMPTY : stack;
        if (!next.isEmpty() && !stacks.get().getStacks().isItemValid(index, next)) {
            return false;
        }
        // setEquippedCurio rather than writing the handler directly: it is what fires the equip and
        // unequip hooks an item may rely on, and a scouter that never learns it was put on is a
        // subtler break than one that refuses to go on at all.
        inventory.get().setEquippedCurio(slot, index, next);
        return true;
    }

    static boolean isValid(LivingEntity npc, String slot, int index, ItemStack stack) {
        return handler(npc, slot)
                .filter(stacks -> index >= 0 && index < stacks.getSlots())
                .map(stacks -> stack == null || stack.isEmpty()
                        || stacks.getStacks().isItemValid(index, stack))
                .orElse(false);
    }

    static int size(LivingEntity npc, String slot) {
        return handler(npc, slot).map(ICurioStacksHandler::getSlots).orElse(0);
    }

    private static Optional<ICurioStacksHandler> handler(LivingEntity npc, String slot) {
        return CuriosApi.getCuriosInventory(npc).flatMap(i -> i.getStacksHandler(slot));
    }
}
