package net.bullettrain.xenopixelsmod.combat.v3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RushMashMeterTest {
    @Test void committedHoldTransferInheritsGravityOnlyForTheNewLease(){
        var lease=new V3Motion.Lease();var token=java.util.UUID.randomUUID();
        assertTrue(lease.acquire(V3Motion.Owner.CINEMATIC,token,true));
        assertFalse(lease.inheritGravity(V3Motion.Owner.CINEMATIC,java.util.UUID.randomUUID(),false));
        assertTrue(lease.inheritGravity(V3Motion.Owner.CINEMATIC,token,false));
        assertEquals(Boolean.FALSE,lease.release());
    }
    @Test void eightDistinctPressTicksFillAndPacketsCannotAccelerateSameTick(){
        var m=new RushMashMeter(100,new RandomRushSettings());
        for(int i=0;i<7;i++){assertTrue(m.press(100+i));assertFalse(m.press(100+i));assertFalse(m.full());}
        assertTrue(m.press(107));assertTrue(m.full());assertEquals(1000,m.completed());
    }
    @Test void expirationLosesAttemptAndNeverExtendsOnPress(){
        var m=new RushMashMeter(20,new RandomRushSettings());
        assertTrue(m.press(99));assertEquals(1,m.ticksLeft(99));assertTrue(m.expired(100));assertFalse(m.press(100));
    }
    @Test void optionalDeadlineAndMeterCanBeDisabledIndependently(){
        var s=new RandomRushSettings(true,false,false,80,1,false,false,0);
        var m=new RushMashMeter(20,s);assertFalse(m.expired(2000));assertTrue(m.press(2000));assertTrue(m.full());
    }
    @Test void settingsNormalizeAndPreserveIndependentFreezeToggles(){
        var s=new RandomRushSettings(true,true,true,0,2000,false,true,3000);
        assertEquals(1,s.windowTicks());assertEquals(1000,s.fillPerPress());assertEquals(1200,s.postFreezeTicks());
        assertFalse(s.freezeDuringAttack());assertTrue(s.freezeAfterAttack());
        assertEquals(23,s.with("windowTicks",23).windowTicks());assertFalse(s.with("enabled",false).enabled());
    }
}
