package net.bullettrain.xenopixelsmod.compat.mmoecon;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the {@code void}-return contract behind {@link MmoEconBridge#withdraw}.
 *
 * <p>MMO Econ's {@code PlayerBalanceManager.subtractBalance} is declared {@code void}. A reflective
 * invoke of a void method returns {@code null} on success, so the old
 * {@code invokeStatic(...) != null} check read every successful debit as a failure. These tests
 * pin the replacement behaviour against a local fixture, so they need no live MMO Econ and no
 * game bootstrap.</p>
 *
 * <p>Only the package-private {@link MmoEconBridge#invokeStaticVoid} is exercised. The public
 * {@code withdraw} reaches {@code ModList.get()}, which a plain unit test cannot satisfy.</p>
 */
class MmoEconBridgeTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /**
     * Stand-in for {@code PlayerBalanceManager}: the same shapes, no mod required.
     *
     * <p>The class and its methods are {@code public} on purpose. {@link MmoEconBridge} resolves
     * methods through {@link Class#getMethods()}, which reports public members only — a
     * package-private fixture would be invisible to the very lookup under test.</p>
     */
    public static final class Fixture {

        public static UUID lastPlayer;
        public static long lastAmount;
        public static int calls;

        public static void subtractBalance(UUID player, long amount) {
            lastPlayer = player;
            lastAmount = amount;
            calls++;
        }

        public static boolean hasFunds(UUID player, long amount) {
            return false;
        }
    }

    @Test
    void voidMethodReportsSuccessAndPassesArguments() {
        Fixture.calls = 0;
        boolean called = MmoEconBridge.invokeStaticVoid(Fixture.class, "subtractBalance", PLAYER, 250L);
        assertTrue(called, "a void method that returns normally must report success");
        assertEquals(1, Fixture.calls);
        assertEquals(PLAYER, Fixture.lastPlayer);
        assertEquals(250L, Fixture.lastAmount);
    }

    @Test
    void falseReturningMethodStillCountsAsSuccess() {
        // invokeStaticVoid reports "the call completed", not "the result was truthy" — so a method
        // whose result is false is still a completed call, which is what the void case needs.
        assertTrue(MmoEconBridge.invokeStaticVoid(Fixture.class, "hasFunds", PLAYER, 1L));
    }

    @Test
    void missingMethodIsFailure() {
        assertFalse(MmoEconBridge.invokeStaticVoid(Fixture.class, "notAMethod"));
    }

    @Test
    void wrongArityIsFailure() {
        assertFalse(MmoEconBridge.invokeStaticVoid(Fixture.class, "subtractBalance", PLAYER));
    }

    @Test
    void nullTypeIsFailure() {
        assertFalse(MmoEconBridge.invokeStaticVoid(null, "subtractBalance", PLAYER, 1L));
    }
}