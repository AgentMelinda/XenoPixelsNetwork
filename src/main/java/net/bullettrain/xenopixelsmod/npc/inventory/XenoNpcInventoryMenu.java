package net.bullettrain.xenopixelsmod.npc.inventory;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.bank.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An NPC's gear, its drops, and its Curios — as slots you drag into.
 *
 * <p>Replaces the typed item ids the Inventory page used to carry. An id cannot hold an enchantment
 * or a custom name, so a Sharpness V sword typed into a field arrived as a plain one; real slots are
 * what My NPCs' own {@code GuiNPCInv} gives you, and they are what this is.
 *
 * <p><b>Two containers, not one.</b> Worn gear and drops are different things — the reference page
 * has seven slot buttons and nine drop chances, which were never one per slot — so they are laid out
 * as two groups with the nine chances belonging to the drops.
 *
 * <p><b>The containers are the edit buffer.</b> Nothing is written to the NPC until the menu closes:
 * {@link #removed} is where the profile is updated, once, on the server. Writing through on every
 * click would mean a half-arranged inventory being saved, synced and possibly respawned into.
 *
 * <p>Curios slots are only present when Curios is installed <em>and</em> the NPC actually has
 * some — {@link NpcCurios#slots} asks Curios rather than assuming, so a server without DragonMineZ
 * shows none rather than showing two that go nowhere.
 */
public class XenoNpcInventoryMenu extends AbstractContainerMenu {

    /** Six worn slots; see {@link NpcGear} for why Projectile is not among them. */
    public static final int GEAR_SLOTS = NpcGear.SLOTS.length;

    /** Nine drop rows, matching the reference page's nine drop chances. */
    public static final int DROP_SLOTS = NpcDropList.MAX_DROPS;

    /** How far a player may be from the NPC and still have it open. Matches the bank's reach. */
    public static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    /**
     * Where everything sits.
     *
     * <p>Held in the menu rather than the screen because vanilla fixes slot positions at
     * construction and the screen draws its sockets from {@code menu.slots} - so one of them has to
     * own the geometry, and it has to be this one.
     *
     * <p><b>Two panels.</b> The main panel is 176 wide, which fits eight slots at an 18-pixel pitch;
     * the worn row needs six and the drop row nine, both of which fit. The Curios row does not - an
     * NPC with all twelve slots needs 224 pixels and ran clean off the frame. Rather than shrink or
     * stretch anything, the Curios grid moves onto the second panel beside it, which is the shape
     * {@code XenoNpcBankScreen} already uses for exactly this reason.
     */
    private static final int SLOT_PITCH = 18;

    /** The main panel's own width, from the sprite. Slots past this are off the frame. */
    public static final int MAIN_PANEL_WIDTH = 176;

    /** Gap between the two panels: enough to read as two frames rather than one seam. */
    public static final int PANEL_GAP = 4;

    /** How many Curios slots fit on one row of the side panel, which is 141 wide. */
    public static final int CURIOS_PER_ROW = 6;

    private static final int GEAR_X = 8;
    private static final int GEAR_Y = 30;
    private static final int DROP_X = 8;
    private static final int DROP_Y = 64;
    private static final int CURIO_X = MAIN_PANEL_WIDTH + PANEL_GAP + 8;
    private static final int CURIO_Y = 30;
    private static final int PLAYER_X = 8;
    private static final int PLAYER_Y = 140;
    private static final int HOTBAR_Y = 198;

    private final Container gear;
    private final Container drops;
    private final Container curios;
    private final int npcEntityId;
    private final String npcName;
    private final List<String> curioSlots;

    /** Server side only; null on the client, where authority is not ours. */
    @Nullable
    private final XenoNpcEntity npc;

    /**
     * A worn slot that only accepts what belongs in it.
     *
     * <p>Armour goes where armour goes, so a helmet cannot be dropped into the boots slot and
     * silently render nowhere. The hands take anything, as they do on a player.
     */
    private static final class GearSlot extends Slot {
        private final EquipmentSlot equipment;

        GearSlot(Container container, int index, int x, int y, EquipmentSlot equipment) {
            super(container, index, x, y);
            this.equipment = equipment;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) {
                return true;
            }
            if (equipment == EquipmentSlot.MAINHAND || equipment == EquipmentSlot.OFFHAND) {
                return true;
            }
            return NpcGear.equipmentSlot(stack) == equipment;
        }

        @Override
        public int getMaxStackSize() {
            // One helmet, not sixty-four. Nothing reads a count here, and a stack of them in the
            // slot would render as one while looking like a supply.
            return equipment == EquipmentSlot.MAINHAND || equipment == EquipmentSlot.OFFHAND
                    ? super.getMaxStackSize() : 1;
        }
    }

    /** A Curios slot validated against the server's real entity and slot definition. */
    private static final class CurioSlot extends Slot {
        @Nullable
        private final LivingEntity npc;
        private final String identifier;

        private CurioSlot(Container container, int index, int x, int y,
                          @Nullable LivingEntity npc, String identifier) {
            super(container, index, x, y);
            this.npc = npc;
            this.identifier = identifier;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // The client menu deliberately has no entity reference. It lets the click travel; the
            // server menu checks the live Curios slot and sends invalid placements back to the
            // player through normal menu synchronization.
            return npc == null || NpcCurios.isValid(npc, identifier, 0, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Client side: everything comes off the wire and the containers start empty. */
    public XenoNpcInventoryMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(windowId, inventory, null, buf.readVarInt(), buf.readUtf(64),
                readSlots(buf), null, null, null);
    }

    private static List<String> readSlots(RegistryFriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(16, buf.readVarInt()));
        List<String> out = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(buf.readUtf(64));
        }
        return List.copyOf(out);
    }

    /** Server side. */
    public XenoNpcInventoryMenu(int windowId, Inventory inventory, @Nullable XenoNpcEntity npc,
                                int npcEntityId, String npcName, List<String> curioSlots,
                                @Nullable Container gear, @Nullable Container drops,
                                @Nullable Container curios) {
        super(ModMenus.NPC_INVENTORY.get(), windowId);
        this.npc = npc;
        this.npcEntityId = npcEntityId;
        this.npcName = npcName;
        this.curioSlots = curioSlots == null ? List.of() : List.copyOf(curioSlots);
        this.gear = gear != null ? gear : new SimpleContainer(GEAR_SLOTS);
        this.drops = drops != null ? drops : new SimpleContainer(DROP_SLOTS);
        this.curios = curios != null ? curios
                : new SimpleContainer(Math.max(1, this.curioSlots.size()));

        for (int i = 0; i < GEAR_SLOTS; i++) {
            addSlot(new GearSlot(this.gear, i, GEAR_X + i * SLOT_PITCH, GEAR_Y, NpcGear.SLOTS[i]));
        }
        for (int i = 0; i < DROP_SLOTS; i++) {
            addSlot(new Slot(this.drops, i, DROP_X + i * SLOT_PITCH, DROP_Y));
        }
        for (int i = 0; i < this.curioSlots.size(); i++) {
            // Wrapped rather than run in one line: twelve in a row is wider than either panel, and
            // a slot drawn outside the frame is still clickable, which is worse than ugly.
            int column = i % CURIOS_PER_ROW;
            int row = i / CURIOS_PER_ROW;
            addSlot(new CurioSlot(this.curios, i,
                    CURIO_X + column * SLOT_PITCH, CURIO_Y + row * SLOT_PITCH,
                    npc, this.curioSlots.get(i)));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        PLAYER_X + column * SLOT_PITCH, PLAYER_Y + row * SLOT_PITCH));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_X + column * SLOT_PITCH, HOTBAR_Y));
        }
    }

    public int npcEntityId() {
        return npcEntityId;
    }

    public String npcName() {
        return npcName;
    }

    /** The Curios slot ids this NPC has, in the order they are drawn. Empty when it has none. */
    public List<String> curioSlots() {
        return curioSlots;
    }

    public Container gearContainer() {
        return gear;
    }

    public Container dropContainer() {
        return drops;
    }

    public Container curioContainer() {
        return curios;
    }

    /** Where the NPC's own slots end and the player's begin. */
    public int npcSlotCount() {
        return GEAR_SLOTS + DROP_SLOTS + curioSlots.size();
    }

    @Override
    public boolean stillValid(Player player) {
        // The NPC is the container: walking away closes it, the way walking away from a chest does.
        // On the client npc is null and the server's answer is the one that counts.
        if (npc == null) {
            return true;
        }
        return npc.isAlive() && npc.distanceToSqr(player) <= MAX_DISTANCE_SQ;
    }

    /**
     * Writes the arrangement back to the NPC.
     *
     * <p>Once, on close, on the server. The containers are the edit buffer while the screen is open;
     * writing through on every click would sync and persist a half-arranged inventory, and an NPC
     * that died mid-edit would respawn into it.
     *
     * <p>Only the drop <em>items</em> are written here. Their chances, the experience range and the
     * loot mode stay on the editor screen, because they are numbers rather than slots — so this
     * preserves whatever those already are rather than resetting them.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (npc == null || player.level().isClientSide()) {
            return;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        var registries = npc.registryAccess();

        for (int i = 0; i < GEAR_SLOTS; i++) {
            profile.gear.setStack(i, gear.getItem(i), registries);
        }
        for (int i = 0; i < DROP_SLOTS; i++) {
            NpcDrop existing = profile.drops.get(i);
            profile.drops.set(i, new NpcDrop(
                    NpcSlotStack.of(drops.getItem(i), registries), existing.chance()));
        }
        profile.write(npc);

        // Curios are stored by Curios itself, on the entity, not in our profile - so they are
        // pushed straight through rather than saved with the rest. An NPC whose Curios slots went
        // away (the mod removed) simply has nothing to push.
        for (int i = 0; i < curioSlots.size(); i++) {
            ItemStack stack = curios.getItem(i).copy();
            if (!NpcCurios.set(npc, curioSlots.get(i), 0, stack) && !stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
        npc.npcData().markEdited();
    }

    /**
     * Shift-click routing.
     *
     * <p>From the NPC's slots to the player, and from the player into the NPC's.
     *
     * <p><b>Into one gear slot, never a range.</b> Scanning slots 0..5 with
     * {@code moveItemStackTo} let the off-hand pick up whatever the main hand could not take, so
     * shift-clicking clothing put the same kind of thing in <em>both</em> the NPC's hands. Armour
     * now goes to the one slot it belongs in and a hand-held item goes to the main hand only; when
     * that slot is taken, the item falls through to the drop row rather than spilling sideways into
     * a slot nobody chose.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int npcEnd = npcSlotCount();
        int playerEnd = slots.size();

        if (index < npcEnd) {
            if (!moveItemStackTo(stack, npcEnd, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = gearSlotFor(stack);
            boolean placed = target >= 0 && moveItemStackTo(stack, target, target + 1, false);
            if (!placed && !moveItemStackTo(stack, GEAR_SLOTS, npcEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /**
     * The one worn slot a shift-clicked item belongs in, or -1 for none.
     *
     * <p>Armour answers its own slot. Everything else answers the main hand and <b>never the off
     * hand</b>: an item has one obvious place to go, and letting a second slot accept the leftovers
     * is what put clothing in both of an NPC's hands. The off hand stays reachable by dragging,
     * which is a deliberate act rather than an overflow.
     *
     * <p>An occupied target answers -1 so the caller can fall through, rather than silently
     * replacing something the operator had already put there.
     */
    private int gearSlotFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        EquipmentSlot wanted = NpcGear.equipmentSlot(stack);
        for (int i = 0; i < GEAR_SLOTS; i++) {
            if (NpcGear.SLOTS[i] != wanted) {
                continue;
            }
            // MAINHAND is what getEquipmentSlot answers for anything that is not armour, so this
            // covers both "a helmet goes on the head" and "everything else goes in the hand".
            return gear.getItem(i).isEmpty() ? i : -1;
        }
        return -1;
    }
}
