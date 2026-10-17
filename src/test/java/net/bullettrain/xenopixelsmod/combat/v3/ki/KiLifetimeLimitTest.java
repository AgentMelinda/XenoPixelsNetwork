package net.bullettrain.xenopixelsmod.combat.v3.ki;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class KiLifetimeLimitTest {
    @Test void chargingNeverExpiresAndAllPelletsShareTheFirstReleaseClock(){
        var l=new KiLifetimeLimit(37);assertFalse(l.expired(1000));assertEquals(-1,l.firstFire());
        l.firing(1200);assertFalse(l.expired(1236));l.firing(1230);assertEquals(1200,l.firstFire());
        assertTrue(l.expired(1237));assertTrue(l.expired(1199));
    }
    @Test void capsAreBoundedAndDoNotFollowLaterConfigEdits(){
        var one=new KiLifetimeLimit(0);one.firing(10);assertTrue(one.expired(11));
        var max=new KiLifetimeLimit(Integer.MAX_VALUE);max.firing(10);assertFalse(max.expired(1209));assertTrue(max.expired(1210));
    }
}
