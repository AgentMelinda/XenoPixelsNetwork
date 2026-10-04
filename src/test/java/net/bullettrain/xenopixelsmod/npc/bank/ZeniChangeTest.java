package net.bullettrain.xenopixelsmod.npc.bank;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ZeniChangeTest {
    @Test
    void everyAllowedAmountProducesExactChange() {
        for (long amount : new long[]{1, 24, 25, 226, 250, 1_234_567,
                NpcBankMoney.MAX_TRANSFER}) {
            long[] counts = ZeniChange.counts(amount);
            long recovered = 0L;
            for (int i = 0; i < counts.length; i++) {
                recovered += counts[i] * ZeniChange.DENOMINATIONS[i];
            }
            assertEquals(amount, recovered);
        }
    }

    @Test
    void usesTheRequestedCoinDenominations() {
        long[] counts = ZeniChange.counts(475);
        assertEquals(1, counts[5]); // 250
        assertEquals(1, counts[6]); // 200
        assertEquals(1, counts[9]); // 25
    }

    @Test
    void refusesNegativeOrOversizedPayout() {
        assertArrayEquals(new long[0], ZeniChange.counts(-1));
        assertArrayEquals(new long[0], ZeniChange.counts(NpcBankMoney.MAX_TRANSFER + 1));
    }

    @Test
    void fullInventoryRejectsPayoutWithoutChangingAnySourceStack() {
        ItemStack[] current = new ItemStack[36];
        Arrays.setAll(current, ignored -> new ItemStack(Items.STICK, 64));

        assertNull(ZeniCash.planInsert(current, List.of(new ItemStack(Items.DIAMOND, 1))));
        for (ItemStack slot : current) assertEquals(64, slot.getCount());
    }

    @Test
    void payoutStacksIntoExistingCashAndThenUsesAnEmptySlot() {
        ItemStack[] current = new ItemStack[36];
        Arrays.fill(current, ItemStack.EMPTY);
        current[0] = new ItemStack(Items.DIAMOND, 63);

        ItemStack[] planned = ZeniCash.planInsert(current, List.of(new ItemStack(Items.DIAMOND, 2)));

        assertEquals(63, current[0].getCount());
        assertEquals(64, planned[0].getCount());
        assertEquals(1, planned[1].getCount());
    }
}
