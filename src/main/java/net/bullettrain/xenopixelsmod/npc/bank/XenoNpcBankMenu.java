package net.bullettrain.xenopixelsmod.npc.bank;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * A player's vault at one bank, one tab at a time.
 *
 * <p>The grid is <b>always six rows</b> and some of it is locked, rather than being a grid that
 * changes size. That is the reference's own model — its server data is {@code MaxSlots} plus
 * {@code UnlockedSlots} — and it has two practical consequences: a player can see what they have
 * not bought yet, and one 176x222 panel sprite covers every state, so nothing is ever stretched.
 *
 * <p>Switching tab reopens the menu server-side rather than rebuilding slots in place. Slot lists
 * are fixed at construction in vanilla's model, and a menu that quietly re-pointed its slots at a
 * different container is the shape of bug that loses items.
 *
 * <p><b>Authority.</b> Which slots are unlocked is decided server-side and sent to the client for
 * display. The lock is enforced by {@link LockedSlot} on both sides, but the server's copy is the
 * one that counts: a client that lied about its unlocked count would still be talking to a menu
 * whose own slots refuse the interaction.
 */
public class XenoNpcBankMenu extends AbstractContainerMenu {

    /** Six rows of nine: the tallest grid, and the one the panel art is sized for. */
    public static final int VAULT_SLOTS = BankTab.MAX_SLOTS;

    /**
     * How far a player may be from the NPC and still bank.
     *
     * <p>The same reach a conversation is held at. Held here rather than in the packet so the
     * dependency runs network to domain and not back: the menu closes at this distance and the
     * packet refuses at it, and one number decides both.
     */
    public static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    private static final int SLOTS_PER_ROW = BankTab.SLOTS_PER_ROW;
    private static final int SLOT_PITCH = 18;
    private static final int VAULT_X = 8;
    private static final int VAULT_Y = 18;
    private static final int PLAYER_X = 8;
    private static final int PLAYER_Y = 140;
    private static final int HOTBAR_Y = 198;

    private final Container vault;
    private final int npcEntityId;
    private final String bankId;
    private final String bankName;
    private final int tabIndex;
    private final int tabCount;
    private final int unlockedSlots;
    private final boolean moneyAvailable;
    private final long vaultMoney;
    private final long walletMoney;
    private final int withdrawFeePercent;

    /** Server side only; null on the client, where distance is not ours to check. */
    @Nullable
    private final XenoNpcEntity npc;

    /**
     * A slot the player has not bought yet.
     *
     * <p>Both {@code mayPlace} and {@code mayPickup} refuse, which between them cover every route
     * in: clicking, shift-clicking, dragging, double-click gather, and the hotbar swap keys. It
     * stays {@code isActive}, because the point is that the player can <em>see</em> what unlocking
     * would give them.
     */
    private static final class LockedSlot extends Slot {
        LockedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    /** Client side: everything comes off the wire, and the container starts empty. */
    public XenoNpcBankMenu(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(windowId, inventory, new SimpleContainer(VAULT_SLOTS), null,
                buf.readVarInt(),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarInt());
    }

    /** Server side. */
    public XenoNpcBankMenu(int windowId, Inventory inventory, Container vault,
                           @Nullable XenoNpcEntity npc, int npcEntityId, String bankId,
                           String bankName, int tabIndex, int tabCount, int unlockedSlots,
                           boolean moneyAvailable, long vaultMoney, long walletMoney,
                           int withdrawFeePercent) {
        super(ModMenus.NPC_BANK.get(), windowId);
        this.vault = vault;
        this.npc = npc;
        this.npcEntityId = npcEntityId;
        this.bankId = bankId;
        this.bankName = bankName;
        this.tabIndex = tabIndex;
        this.tabCount = tabCount;
        // Clamped here as well as where it is decided: this constructor is also reached from the
        // wire, and a slot count outside the grid would put slots where no art is.
        this.unlockedSlots = Math.max(0, Math.min(VAULT_SLOTS, unlockedSlots));
        this.moneyAvailable = moneyAvailable;
        this.vaultMoney = Math.max(0L, vaultMoney);
        this.walletMoney = Math.max(0L, walletMoney);
        this.withdrawFeePercent = Math.max(0,
                Math.min(BankDefinition.MAX_WITHDRAW_FEE_PERCENT, withdrawFeePercent));

        for (int row = 0; row < VAULT_SLOTS / SLOTS_PER_ROW; row++) {
            for (int column = 0; column < SLOTS_PER_ROW; column++) {
                int index = row * SLOTS_PER_ROW + column;
                int x = VAULT_X + column * SLOT_PITCH;
                int y = VAULT_Y + row * SLOT_PITCH;
                addSlot(index < this.unlockedSlots
                        ? new Slot(vault, index, x, y)
                        : new LockedSlot(vault, index, x, y));
            }
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

    public Container vault() {
        return vault;
    }

    public int npcEntityId() {
        return npcEntityId;
    }

    public String bankId() {
        return bankId;
    }

    public String bankName() {
        return bankName;
    }

    public int tabIndex() {
        return tabIndex;
    }

    public int tabCount() {
        return tabCount;
    }

    public int unlockedSlots() {
        return unlockedSlots;
    }

    /** Whether an economy is present at all. False means no money row is drawn - not a dead one. */
    public boolean moneyAvailable() {
        return moneyAvailable;
    }

    public long vaultMoney() {
        return vaultMoney;
    }

    public long walletMoney() {
        return walletMoney;
    }

    public int withdrawFeePercent() {
        return withdrawFeePercent;
    }

    /** Whether this tab can still grow. */
    public boolean hasLockedSlots() {
        return unlockedSlots < VAULT_SLOTS;
    }

    @Override
    public boolean stillValid(Player player) {
        // The NPC is the vault: walking away from the teller closes it, the same way walking away
        // from a chest does. On the client npc is null and the server's answer is authoritative.
        if (npc == null) {
            return true;
        }
        return npc.isAlive() && npc.distanceToSqr(player) <= MAX_DISTANCE_SQ;
    }

    /**
     * Shift-click routing.
     *
     * <p>Only ever moves into the <em>unlocked</em> part of the vault, so a shift-click cannot put
     * a stack somewhere the player could not then take it out of. That is the failure this method
     * exists to prevent, not merely a convenience.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = VAULT_SLOTS;
        int playerEnd = slots.size();

        if (index < VAULT_SLOTS) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, unlockedSlots, false)) {
            return ItemStack.EMPTY;
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
}
