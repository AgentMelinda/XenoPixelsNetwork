package net.bullettrain.xenopixelsmod.compat.npc;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Adds player-like Curios (equip + cosmetic) slots to an NPC inventory menu.
 *
 * <p>Slot count is fixed so the server and client always agree, even when the capability is
 * missing on one side. Identifiers match {@code data/xenopixelsmod/curios/entities/npcs.json}.
 */
public final class NpcCuriosInventory {
    /** Native CNPC/MyNPCs {@code ContainerNPCInv} slot count before this overlay. */
    public static final int ORIGINAL_SLOT_COUNT = 52;

    /**
     * Must match {@code data/xenopixelsmod/curios/entities/npcs.json}.
     * Layout is 4 columns × 5 rows (equip|cosmetic pairs) so 10 types fit above y=113.
     */
    public static final String[] SLOT_IDS = {
            "head", "necklace", "back", "body", "bracelet",
            "curio", "hands", "ring", "belt", "charm"
    };

    public static final int START_X = 108;
    public static final int START_Y = 8;
    public static final int SLOT_SIZE = 18;
    public static final int COL_GAP = 2;

    private static final IItemHandler EMPTY = new EmptyHandler();

    private NpcCuriosInventory() {
    }

    public static int extraSlotCount() {
        return SLOT_IDS.length * 2;
    }

    public static int slotX(int index) {
        int type = index / 2;
        int cosmetic = index % 2;
        int bank = type / 5;
        int col = bank * 2 + cosmetic;
        return START_X + col * (SLOT_SIZE + COL_GAP);
    }

    public static int slotY(int index) {
        int type = index / 2;
        int row = type % 5;
        return START_Y + row * SLOT_SIZE;
    }

    public static void addSlots(LivingEntity npc, Consumer<Slot> sink) {
        if (sink == null) return;
        ICuriosItemHandler curios = handler(npc);
        for (int i = 0; i < SLOT_IDS.length; i++) {
            String id = SLOT_IDS[i];
            ICurioStacksHandler stacks = curios == null ? null : curios.getStacksHandler(id).orElse(null);
            IItemHandler equip = stacks == null ? EMPTY : stacks.getStacks();
            IItemHandler cosmetic = stacks != null && stacks.hasCosmetic()
                    ? stacks.getCosmeticStacks() : EMPTY;
            int base = i * 2;
            sink.accept(new SlotItemHandler(equip, 0, slotX(base), slotY(base)));
            sink.accept(new SlotItemHandler(cosmetic, 0, slotX(base + 1), slotY(base + 1)));
        }
    }

    private static ICuriosItemHandler handler(LivingEntity npc) {
        if (npc == null || !ModList.get().isLoaded("curios")) return null;
        Optional<ICuriosItemHandler> opt = CuriosApi.getCuriosInventory(npc);
        return opt == null ? null : opt.orElse(null);
    }

    /** One locked slot so a missing capability still occupies a menu index. */
    private static final class EmptyHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
