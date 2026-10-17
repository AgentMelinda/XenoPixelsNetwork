package net.bullettrain.xenopixelsmod.combat.v3;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class CombatComboCounterTest {
    @Test void superRushUsesAFullShuffleBagAndDoesNotRepeatAcrossRefill() {
        UUID player=UUID.randomUUID();var random=net.minecraft.util.RandomSource.create(42);
        var catalog=java.util.List.of("a","b","c");
        var first=java.util.List.of(SuperRushDeck.next(player,catalog,random),
                SuperRushDeck.next(player,catalog,random),SuperRushDeck.next(player,catalog,random));
        assertEquals(3,new java.util.HashSet<>(first).size());
        String previous=first.getLast();
        assertNotEquals(previous,SuperRushDeck.next(player,catalog,random));
        SuperRushDeck.clear(player);
    }
    @Test void ultimateRushIsThreeOfFourRolls() {
        assertTrue(SuperRushDeck.isUltimateRoll(0));assertTrue(SuperRushDeck.isUltimateRoll(1));
        assertTrue(SuperRushDeck.isUltimateRoll(2));assertFalse(SuperRushDeck.isUltimateRoll(3));
    }
    @Test void countsConfirmedHitsAcrossDashFollowUpsAndIgnoresRefusedDamage(){
        var chain=new CombatComboCounter.Chain();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        chain.hit(a,0,1);chain.hit(a,8,1);chain.hit(a,16,0);
        assertEquals(2,chain.hits(16));chain.hit(a,24,2);assertEquals(3,chain.hits(24));
        chain.hit(b,25,1);assertEquals(1,chain.hits(25));assertEquals(0,chain.hits(105));
        chain.hit(b,105,1);assertEquals(1,chain.hits(105),"hit at the expired boundary starts a new chain");
    }
}
