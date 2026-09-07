package net.bullettrain.xenopixelsmod.combat;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class GlowLeaseTest {
    @Test void restartThenCancelRestoresOriginalBaseline() {
        AtomicBoolean target = new AtomicBoolean();
        GlowLease first = new GlowLease(target::get, target::set, true);
        assertTrue(target.get());
        first.release();
        GlowLease second = new GlowLease(target::get, target::set, true);
        assertTrue(target.get());
        second.release();
        assertFalse(target.get());
        second.release();
        assertFalse(target.get());
    }
    @Test void retargetReleasesOldAndCompletionReleasesNew() {
        AtomicBoolean old = new AtomicBoolean(), next = new AtomicBoolean();
        GlowLease first = new GlowLease(old::get, old::set, true);
        first.release();
        GlowLease second = new GlowLease(next::get, next::set, true);
        assertFalse(old.get());
        assertTrue(next.get());
        second.release();
        assertFalse(next.get());
    }
    @Test void externalGlowAndDisabledFeatureAreNotOwned() {
        AtomicBoolean target = new AtomicBoolean(true);
        GlowLease lease = new GlowLease(target::get, target::set, true);
        lease.release();
        assertTrue(target.get());
        target.set(false);
        lease = new GlowLease(target::get, target::set, false);
        target.set(true);
        lease.release();
        assertTrue(target.get());
    }
}
