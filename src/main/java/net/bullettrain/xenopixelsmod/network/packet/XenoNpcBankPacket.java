package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.bank.BankAccount;
import net.bullettrain.xenopixelsmod.npc.bank.BankDefinition;
import net.bullettrain.xenopixelsmod.npc.bank.Banks;
import net.bullettrain.xenopixelsmod.npc.bank.NpcBankMoney;
import net.bullettrain.xenopixelsmod.npc.bank.NpcBankService;
import net.bullettrain.xenopixelsmod.npc.bank.XenoNpcBankMenu;
import net.bullettrain.xenopixelsmod.npc.bank.ZeniCash;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

/**
 * C2S: "do this at the bank I am standing at".
 *
 * <p>The client names an action, a tab and an amount. It never names a bank, a cost, a slot count
 * or a balance — every one of those is read server-side from the NPC's own profile and the store,
 * so the worst a crafted packet can ask for is an action at a bank this NPC genuinely tells for.
 *
 * <p>Checked here, in the order {@code XenoNpcTravelPacket} established: the entity exists and is a
 * bank, the player is close enough to be talking to it, the bank resolves through the store, the
 * tab is one this bank offers, and the cost is actually paid before anything is recorded. A
 * failure answers the player, because a button that does nothing is indistinguishable from a
 * broken one.
 */
public record XenoNpcBankPacket(int entityId, int action, int tabIndex, long amount) {

    /** Open a tab. */
    public static final int ACTION_OPEN = 0;
    /** Buy a locked tab. */
    public static final int ACTION_UNLOCK = 1;
    /** Buy another row on a tab already held. */
    public static final int ACTION_UPGRADE = 2;
    /** Move money from the wallet into the vault. */
    public static final int ACTION_DEPOSIT = 3;
    /** Move money from the vault to the wallet, less the fee. */
    public static final int ACTION_WITHDRAW = 4;
    /** Convert all carried Zeni coins and notes into vault balance. */
    public static final int ACTION_DEPOSIT_CASH = 5;
    /** Pay vault balance out as physical Zeni, after the bank's withdrawal fee. */
    public static final int ACTION_WITHDRAW_CASH = 6;

    private static final int MAX_ACTION = ACTION_WITHDRAW_CASH;

    /**
     * How far a player may be from the NPC and still bank.
     *
     * <p>Shared with {@link XenoNpcBankMenu#stillValid}, which owns the number: walking away
     * closes the screen and this refuses the action, and both should happen at the same distance.
     * Without it a client could keep a stale entity id and bank from anywhere in the world.
     */
    private static final double MAX_DISTANCE_SQ = XenoNpcBankMenu.MAX_DISTANCE_SQ;

    public XenoNpcBankPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(action);
        buf.writeVarInt(tabIndex);
        buf.writeVarLong(amount);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (action < 0 || action > MAX_ACTION || tabIndex < 0
                    || tabIndex >= BankDefinition.MAX_TABS) {
                return;
            }
            Entity entity = player.level().getEntity(entityId);
            if (!(entity instanceof XenoNpcEntity npc) || npc.role() != XenoNpcRole.BANK) {
                return;
            }
            if (player.distanceToSqr(npc) > MAX_DISTANCE_SQ) {
                // A stale entity id kept from across the world is the shape of the abuse here.
                player.sendSystemMessage(Component.literal("You are too far away."));
                return;
            }

            NpcCombatProfile profile = NpcCombatProfile.read(npc);
            BankDefinition bank = Banks.get(profile.bankId);
            if (bank == null || bank.isEmpty() || tabIndex >= bank.tabCount()) {
                player.sendSystemMessage(Component.literal("That is not available here."));
                return;
            }
            BankAccount account = NpcBankService.accountFor(player, bank.id());
            if (account == null) {
                player.sendSystemMessage(Component.literal("You have accounts at too many banks."));
                return;
            }

            switch (action) {
                case ACTION_OPEN -> NpcBankService.open(player, npc, tabIndex);
                case ACTION_UNLOCK -> reopen(player, npc, tabIndex,
                        NpcBankService.unlock(player, bank, account, tabIndex));
                case ACTION_UPGRADE -> reopen(player, npc, tabIndex,
                        NpcBankService.upgrade(player, bank, account, tabIndex));
                case ACTION_DEPOSIT -> deposit(player, npc, account, tabIndex, amount);
                case ACTION_WITHDRAW -> withdraw(player, npc, bank, account, tabIndex, amount);
                case ACTION_DEPOSIT_CASH -> {
                    long deposited = ZeniCash.depositAll(player, account);
                    player.sendSystemMessage(Component.literal(deposited > 0L
                            ? "§bDeposited §f" + deposited + " Zeni cash"
                            : "No Zeni cash was deposited (maximum 1,000,000,000 per transfer)."));
                    if (deposited > 0L) NpcBankService.open(player, npc, tabIndex);
                }
                case ACTION_WITHDRAW_CASH -> {
                    long paid = ZeniCash.withdraw(player, account, amount, bank.withdrawFeePercent());
                    player.sendSystemMessage(Component.literal(paid > 0L
                            ? "§bWithdrew §f" + paid + " Zeni cash"
                            : "Cash withdrawal failed: check your balance, amount, and inventory space."));
                    if (paid > 0L) NpcBankService.open(player, npc, tabIndex);
                }
                default -> { }
            }
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Shows the result of a purchase.
     *
     * <p>A successful one reopens the menu, because the slot count it was built with has changed
     * and a menu's slots are fixed at construction — rebuilding is the honest way to show a bigger
     * vault. A refused one says why and leaves the open menu alone.
     */
    private static void reopen(ServerPlayer player, XenoNpcEntity npc, int tabIndex,
                               String refusal) {
        if (refusal != null) {
            player.sendSystemMessage(Component.literal(refusal));
            return;
        }
        NpcBankService.open(player, npc, tabIndex);
    }

    private static void deposit(ServerPlayer player, XenoNpcEntity npc, BankAccount account,
                                int tabIndex, long amount) {
        if (!NpcBankMoney.available()) {
            // No economy installed. The client should not have drawn the control at all, so this
            // is a crafted packet rather than a player pressing something.
            return;
        }
        if (!NpcBankMoney.deposit(player, account, amount)) {
            player.sendSystemMessage(Component.literal("You do not have that much."));
            return;
        }
        player.sendSystemMessage(Component.literal(
                "§bDeposited §f" + NpcBankMoney.format(Math.max(0L, amount))));
        NpcBankService.open(player, npc, tabIndex);
    }

    private static void withdraw(ServerPlayer player, XenoNpcEntity npc, BankDefinition bank,
                                 BankAccount account, int tabIndex, long amount) {
        if (!NpcBankMoney.available()) {
            return;
        }
        long paid = NpcBankMoney.withdraw(player, account, amount, bank.withdrawFeePercent());
        if (paid <= 0L) {
            player.sendSystemMessage(Component.literal("That withdrawal could not be made."));
            return;
        }
        player.sendSystemMessage(Component.literal(
                "§bWithdrew §f" + NpcBankMoney.format(paid)));
        NpcBankService.open(player, npc, tabIndex);
    }
}
