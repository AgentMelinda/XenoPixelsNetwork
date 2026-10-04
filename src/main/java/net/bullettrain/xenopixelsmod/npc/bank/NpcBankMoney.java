package net.bullettrain.xenopixelsmod.npc.bank;

import net.bullettrain.xenopixelsmod.compat.mmoecon.MmoEconBridge;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional MMO Econ wallet bridge for NPC banks.
 *
 * <p>Physical Zeni is exchanged separately through {@link ZeniCash} and remains available without
 * MMO Econ. When {@link #available()} is false, only the optional wallet mode is hidden.
 *
 * <p>That containment is the point of the class. The NPC system must not come to depend on a mod
 * that may not be there, so this bridge is the only bank class that calls MMO Econ, and it
 * answers "no economy" rather than throwing when it is absent.
 *
 * <p><b>Wallet and vault are different places.</b> The wallet is the player's balance in the
 * economy; the vault is what they have deposited <em>at this bank</em>, which lives on
 * {@code BankAccount}. Depositing moves wallet to vault, withdrawing moves vault to wallet minus
 * the bank's fee. Nothing here creates or destroys money except the fee, which is destroyed
 * deliberately — a fee paid to nobody is a sink, and the alternative is inventing an owner for it.
 */
public final class NpcBankMoney {

    /** A deposit or withdrawal cannot exceed this in one go, so an overflow is unreachable. */
    public static final long MAX_TRANSFER = 1_000_000_000L;

    private NpcBankMoney() {
    }

    /** Whether an economy is present at all. Everything else here answers safely when false. */
    public static boolean available() {
        return MmoEconBridge.available();
    }

    /** The player's own balance, or zero when no economy is installed. */
    public static long wallet(ServerPlayer player) {
        if (player == null || !available()) {
            return 0L;
        }
        return MmoEconBridge.getBalance(player.getUUID());
    }

    /**
     * Moves money from the player's wallet into their vault at this bank.
     *
     * <p>Debits first and credits only on success. The other order would credit the vault from a
     * wallet that then turned out to be short — which is how an economy gets money printed into it.
     *
     * @return true when the money moved
     */
    public static boolean deposit(ServerPlayer player, BankAccount account, long amount) {
        if (player == null || account == null || !available()) {
            return false;
        }
        long wanted = bound(amount);
        if (wanted <= 0L || account.money() > Long.MAX_VALUE - wanted
                || !MmoEconBridge.hasFunds(player.getUUID(), wanted)) {
            return false;
        }
        if (!MmoEconBridge.withdraw(player.getUUID(), wanted)) {
            return false;
        }
        account.setMoney(account.money() + wanted);
        return true;
    }

    /**
     * Moves money from the vault back to the wallet, less the bank's fee.
     *
     * <p>The fee is taken off what arrives, not added to what leaves: a player asking for their
     * whole balance must not be refused for being a few short of a charge they cannot see.
     *
     * @return the amount that actually reached the wallet, or 0 when nothing moved
     */
    public static long withdraw(ServerPlayer player, BankAccount account, long amount,
                                int feePercent) {
        if (player == null || account == null || !available()) {
            return 0L;
        }
        long wanted = Math.min(bound(amount), account.money());
        if (wanted <= 0L) {
            return 0L;
        }
        long paid = wanted - fee(wanted, feePercent);
        if (paid <= 0L) {
            // The whole withdrawal would be eaten by the fee. Refusing leaves the player's money
            // where it is; going ahead would silently delete it.
            return 0L;
        }
        // The vault is debited first for the same reason deposit debits first.
        account.setMoney(account.money() - wanted);
        if (!MmoEconBridge.deposit(player.getUUID(), paid)) {
            // Could not pay out. Put it back rather than leaving the player short.
            account.setMoney(account.money() + wanted);
            return 0L;
        }
        return paid;
    }

    /**
     * The fee on one withdrawal, rounded down.
     *
     * <p>Rounding down rather than up so a fee can never exceed what it is charged on, and so a
     * 0% bank is genuinely free rather than free-plus-one.
     */
    public static long fee(long amount, int feePercent) {
        int percent = Math.max(0, Math.min(BankDefinition.MAX_WITHDRAW_FEE_PERCENT, feePercent));
        if (percent == 0 || amount <= 0L) {
            return 0L;
        }
        return amount * percent / 100L;
    }

    /** Formats an amount the way the rest of the mod formats money. */
    public static String format(long amount) {
        return MmoEconBridge.format(amount);
    }

    private static long bound(long amount) {
        return Math.max(0L, Math.min(MAX_TRANSFER, amount));
    }
}
