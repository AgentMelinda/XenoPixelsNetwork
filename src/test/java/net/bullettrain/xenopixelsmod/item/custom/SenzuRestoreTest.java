package net.bullettrain.xenopixelsmod.item.custom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-09-29 owner: the strawberry senzu restores health, stamina and ki to full, not only health. */
class SenzuRestoreTest {
    private static final class Pools implements SenzuRestore.Pools {
        float health = 3, maxHealth = 40, ki = 10, maxKi = 15000, stamina = 2, maxStamina = 16000;
        boolean dmz = true;

        @Override public float maxHealth() { return maxHealth; }
        @Override public void setHealth(float v) { health = v; }
        @Override public boolean hasDmzStats() { return dmz; }
        @Override public float maxKi() { return maxKi; }
        @Override public void setKi(float v) { ki = v; }
        @Override public float maxStamina() { return maxStamina; }
        @Override public void setStamina(float v) { stamina = v; }
    }

    @Test
    void everyPoolGoesBackToFull() {
        Pools p = new Pools();
        assertTrue(SenzuRestore.fill(p));
        assertEquals(40, p.health);
        assertEquals(15000, p.ki);
        assertEquals(16000, p.stamina);
    }

    @Test
    void withoutDmzStatsOnlyHealthIsRestored() {
        Pools p = new Pools();
        p.dmz = false;
        assertFalse(SenzuRestore.fill(p), "nothing to sync to the DMZ HUD");
        assertEquals(40, p.health);
        assertEquals(10, p.ki);
        assertEquals(2, p.stamina);
    }
}
