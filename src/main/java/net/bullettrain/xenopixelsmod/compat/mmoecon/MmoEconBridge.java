package net.bullettrain.xenopixelsmod.compat.mmoecon;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional reflection bridge for MMO Econ ({@code mmoecon}) balances.
 *
 * <p>MMO Econ is server-side only and is a {@code runtimeOnly} entry, never a compile dependency.
 * Every member below is resolved reflectively and every public entry point first checks the
 * {@code mmoecon} mod id, so XenoPixels compiles and loads with MMO Econ absent and reports
 * {@link #available()} false instead of throwing.</p>
 *
 * <p>All signatures were read from MMO Econ 1.1.0 with {@code javap}; they are recorded in
 * {@code docs/shops-and-plots.md}. This bridge calls only the static balance accessors and
 * {@code Money}; it never touches MMO Econ's own event handlers, which MMO Econ registers itself.</p>
 */
public final class MmoEconBridge {

    private static final String BALANCE_MANAGER = "com.casp3rnz.mmoecon.PlayerBalanceManager";
    private static final String MONEY = "com.casp3rnz.mmoecon.Money";

    private MmoEconBridge() {
    }

    /** True when MMO Econ is on the server and its balance manager resolves. */
    public static boolean available() {
        return ModList.get().isLoaded("mmoecon") && balanceManager() != null;
    }

    /** Current balance in MMO Econ's smallest unit, or {@code 0} when the integration is absent. */
    public static long getBalance(UUID playerId) {
        Class<?> manager = balanceManager();
        if (manager == null || playerId == null) {
            return 0L;
        }
        Object value = invokeStatic(manager, "getBalance", playerId);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** True when the account exists and holds at least {@code amount}. */
    public static boolean hasFunds(UUID playerId, long amount) {
        Class<?> manager = balanceManager();
        if (manager == null || playerId == null) {
            return false;
        }
        Object value = invokeStatic(manager, "hasFunds", playerId, amount);
        return value instanceof Boolean held && held;
    }

    /**
     * Debits {@code amount}. Returns false when the integration is absent or the balance is short.
     *
     * <p>{@code subtractBalance} is declared {@code void}, so its reflective invoke returns
     * {@code null} on success — a null result cannot be read as failure the way a value-returning
     * call's can. {@link #invokeStaticVoid} therefore reports whether the call completed instead.</p>
     */
    public static boolean withdraw(UUID playerId, long amount) {
        if (amount < 0L || !hasFunds(playerId, amount)) {
            return false;
        }
        return invokeStaticVoid(balanceManager(), "subtractBalance", playerId, amount);
    }

    /**
     * Moves {@code amount} from {@code from} to {@code to} in one operation.
     *
     * <p>Used to settle a plot sale. Returns false when the integration is absent, so a caller
     * must treat false as "not paid" rather than retrying.</p>
     */
    public static boolean transfer(UUID from, UUID to, long amount) {
        if (from == null || to == null || amount < 0L) {
            return false;
        }
        Object value = invokeStatic(balanceManager(), "transfer", from, to, amount);
        return value instanceof Boolean moved && moved;
    }

    /** Convenience for the price-on-a-sign form: converts then transfers. */
    public static boolean transferPrice(UUID from, UUID to, double price) {
        return transfer(from, to, toUnits(price));
    }

    /** Converts a sign's decimal price to MMO Econ's smallest unit. */
    public static long toUnits(double price) {
        Class<?> money = money();
        if (money == null) {
            return Math.round(price);
        }
        Object value = invokeStatic(money, "fromDouble", price);
        return value instanceof Number number ? number.longValue() : Math.round(price);
    }

    /** Renders {@code amount} with MMO Econ's own formatting, or a plain fallback when absent. */
    public static String format(long amount) {
        Class<?> money = money();
        if (money == null) {
            return Long.toString(amount);
        }
        Object value = invokeStatic(money, "format", amount);
        return value instanceof String text ? text : Long.toString(amount);
    }

    @Nullable
    private static Class<?> balanceManager() {
        return load(BALANCE_MANAGER);
    }

    @Nullable
    private static Class<?> money() {
        return load(MONEY);
    }

    @Nullable
    private static Class<?> load(String name) {
        if (!ModList.get().isLoaded("mmoecon")) {
            return null;
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    @Nullable
    private static Object invokeStatic(@Nullable Class<?> type, String name, Object... args) {
        if (type == null) {
            return null;
        }
        Method method = findMethod(type, name, args.length);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            XenoPixelsMod.LOGGER.error("MMO Econ bridge failed calling {}", name, exception);
            return null;
        }
    }

    /**
     * Invokes a {@code void} static method, reporting whether the call completed.
     *
     * <p>Package-private so the void contract can be exercised against a local fixture without a
     * live MMO Econ. Only a missing method or a thrown exception is a failure.</p>
     */
    static boolean invokeStaticVoid(@Nullable Class<?> type, String name, Object... args) {
        if (type == null) {
            return false;
        }
        Method method = findMethod(type, name, args.length);
        if (method == null) {
            return false;
        }
        try {
            method.invoke(null, args);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            XenoPixelsMod.LOGGER.error("MMO Econ bridge failed calling {}", name, exception);
            return false;
        }
    }

    @Nullable
    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }
}