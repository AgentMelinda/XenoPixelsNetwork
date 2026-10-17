package net.bullettrain.xenopixelsmod.space.universe;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfiniteUniversePageProtocolTest {
    @Test
    void incrementalPageMatchesSynchronousPageForDifferentBudgets() {
        long seed = 0x6A09E667F3BCC909L;
        var expected = InfiniteUniverseField.page(seed, Long.MAX_VALUE - 63, Long.MIN_VALUE, 64, 64);

        for (int budget : new int[]{1, 7, 511, 512, 4_096}) {
            var job = InfiniteUniverseField.pageJob(seed, Long.MAX_VALUE - 63, Long.MIN_VALUE, 64, 64);
            while (!job.complete()) {
                int processed = job.step(budget);
                assertTrue(processed > 0 && processed <= budget);
            }
            assertEquals(4_096, job.processedCells());
            assertEquals(expected, job.result());
        }

        var queuedPage = InfiniteUniverseField.pageJob(seed, 0, 0, 64, 64);
        int ticks = 0;
        while (!queuedPage.complete()) {
            assertTrue(queuedPage.step(512) <= 512);
            ticks++;
        }
        assertEquals(8, ticks, "the production 512-cell page budget should finish a maximum page in eight slices");
        assertEquals(InfiniteUniversePageSnapshot.occupancy(seed, false,
                        InfiniteUniverseField.page(seed, 0, 0, 64, 64)),
                InfiniteUniversePageSnapshot.occupancy(seed, false, queuedPage));

        var occupancyOnly = InfiniteUniverseField.occupancyPageJob(seed, 0, 0, 64, 64);
        while (!occupancyOnly.complete()) occupancyOnly.step(512);
        assertEquals(InfiniteUniversePageSnapshot.occupancy(seed, false,
                        InfiniteUniverseField.page(seed, 0, 0, 64, 64)),
                InfiniteUniversePageSnapshot.occupancy(seed, false, occupancyOnly));
        assertThrows(IllegalStateException.class, occupancyOnly::result,
                "server occupancy jobs should not retain system detail lists");

        InfiniteUniverseField.Cell revealedCell = null;
        for (int x = 0; x < 64 && revealedCell == null; x++) {
            for (int z = 0; z < 64; z++) {
                var cell = new InfiniteUniverseField.Cell(x, z);
                if (InfiniteUniverseField.systemAt(seed, cell) != null) { revealedCell = cell; break; }
            }
        }
        assertNotNull(revealedCell, "the deterministic test window should contain at least one system");
        var withDiscovery = InfiniteUniverseField.occupancyPageJob(seed, 0, 0, 64, 64, revealedCell::equals);
        while (!withDiscovery.complete()) withDiscovery.step(512);
        assertEquals(InfiniteUniversePageSnapshot.occupancy(seed, false,
                        InfiniteUniverseField.page(seed, 0, 0, 64, 64), withDiscovery.revealedBits()),
                InfiniteUniversePageSnapshot.occupancy(seed, false, withDiscovery));
    }

    @Test
    void occupancyOnlyGenerationMatchesDetailedSystemsAtLegacyAndFullWidthCoordinates() {
        long seed = 0xBB67AE8584CAA73BL;
        long[] coordinates = {-229, -228, -1, 0, 1, 228, 229, Long.MIN_VALUE, Long.MIN_VALUE + 1,
                Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long x : coordinates) {
            for (long z : coordinates) {
                var cell = new InfiniteUniverseField.Cell(x, z);
                assertEquals(InfiniteUniverseField.systemAt(seed, cell) != null,
                        InfiniteUniverseField.isSystemPresent(seed, cell), cell.id());
            }
        }
    }

    @Test
    void pageRequestRoundTripsFullWidthOriginAndRejectsUnboundedWindows() {
        var request = new InfiniteUniversePageRequest(Long.MIN_VALUE, Long.MAX_VALUE - 63, 64, 64);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        InfiniteUniversePageRequest.encode(request, buffer);
        assertEquals(request, InfiniteUniversePageRequest.decode(buffer));

        assertThrows(IllegalArgumentException.class,
                () -> new InfiniteUniversePageRequest(0, 0, 65, 64));
        assertThrows(IllegalArgumentException.class,
                () -> new InfiniteUniversePageRequest(Long.MAX_VALUE, 0, 2, 1));

        var atMinimum = InfiniteUniversePageRequest.viewportAt(Long.MIN_VALUE, Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, atMinimum.originX());
        assertEquals(Long.MIN_VALUE, atMinimum.originZ());
        var atMaximum = InfiniteUniversePageRequest.viewportAt(Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE - 63, atMaximum.originX());
        assertEquals(Long.MAX_VALUE - 63, atMaximum.originZ());
    }

    @Test
    void occupancySnapshotRoundTripsAndDefensivelyCopiesBits() {
        var page = InfiniteUniverseField.page(42L, -32, 65, 32, 32);
        assertFalse(page.systems().isEmpty(), "test window must contain at least one generated system");
        var revealedCell = page.systems().get(0).cell();
        byte[] revealed = new byte[(page.queriedCells() + 7) >>> 3];
        int revealedIndex = Math.toIntExact(revealedCell.x() - page.originX()) * page.height()
                + Math.toIntExact(revealedCell.z() - page.originZ());
        revealed[revealedIndex >>> 3] |= (byte) (1 << (revealedIndex & 7));
        var packet = InfiniteUniversePageSnapshot.occupancy(42L, false, page, revealed);
        var originalBits = packet.occupied();
        var originalRevealed = packet.revealed();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        InfiniteUniversePageSnapshot.encode(packet, buffer);
        var decoded = InfiniteUniversePageSnapshot.decode(buffer);

        assertEquals(packet, decoded);
        assertEquals(128, decoded.occupied().length);
        for (int dx = 0; dx < page.width(); dx++) {
            for (int dz = 0; dz < page.height(); dz++) {
                int index = dx * page.height() + dz;
                assertEquals(InfiniteUniverseField.systemAt(42L,
                                new InfiniteUniverseField.Cell(page.originX() + dx, page.originZ() + dz)) != null,
                        decoded.hasSystemAt(index));
            }
        }
        byte[] mutated = decoded.occupied();
        mutated[0] ^= (byte) 0xff;
        assertArrayEquals(originalBits, decoded.occupied());
        assertTrue(decoded.isRevealedAt(revealedIndex));
        byte[] mutatedRevealed = decoded.revealed();
        mutatedRevealed[revealedIndex >>> 3] ^= (byte) (1 << (revealedIndex & 7));
        assertArrayEquals(originalRevealed, decoded.revealed());

        var maxPage = InfiniteUniverseField.page(42L, 0, 0, 64, 64);
        assertEquals(512, InfiniteUniversePageSnapshot.occupancy(42L, false, maxPage).occupied().length);
    }

    @Test
    void logicalRevealDeltaRoundTripsAndUpdatesOnlyTheMatchingOccupiedCell() {
        long seed = 8675309L;
        var page = InfiniteUniverseField.page(seed, 0, 0, 64, 64);
        assertFalse(page.systems().isEmpty());
        var system = page.systems().getFirst();
        var snapshot = InfiniteUniversePageSnapshot.occupancy(seed, false, page);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var delta = new InfiniteUniverseCellReveal(seed, system.cell().x(), system.cell().z());
        InfiniteUniverseCellReveal.encode(delta, buffer);
        assertEquals(delta, InfiniteUniverseCellReveal.decode(buffer));

        var updated = snapshot.withRevealedCell(system.cell().x(), system.cell().z());
        int index = Math.toIntExact(system.cell().x() - page.originX()) * page.height()
                + Math.toIntExact(system.cell().z() - page.originZ());
        assertFalse(snapshot.isRevealedAt(index), "delta must not mutate the old immutable page");
        assertTrue(updated.isRevealedAt(index));
        assertSame(updated, updated.withRevealedCell(system.cell().x(), system.cell().z()));
        assertSame(updated, updated.withRevealedCell(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void revealDuringPageBuildSurvivesDeltaBeforeSnapshotOrdering() {
        long seed = 8675309L;
        var job = InfiniteUniverseField.occupancyPageJob(seed, 0, 0, 64, 64);
        assertEquals(512, job.step(512)); // Cell 0:0 has already been scanned when its reveal arrives.

        assertTrue(job.markRevealed(0, 0));
        InfiniteUniverseField.Cell notYetScanned = null;
        for (int x = 8; x < 64 && notYetScanned == null; x++) {
            for (int z = 0; z < 64; z++) {
                var cell = new InfiniteUniverseField.Cell(x, z);
                if (InfiniteUniverseField.isSystemPresent(seed, cell)) { notYetScanned = cell; break; }
            }
        }
        assertNotNull(notYetScanned, "the unscanned half of the deterministic window must contain a system");
        assertTrue(job.markRevealed(notYetScanned.x(), notYetScanned.z()));
        InfiniteUniverseField.Cell emptyCell = null;
        for (int x = 0; x < 64 && emptyCell == null; x++) {
            for (int z = 0; z < 64; z++) {
                var cell = new InfiniteUniverseField.Cell(x, z);
                if (!InfiniteUniverseField.isSystemPresent(seed, cell)) { emptyCell = cell; break; }
            }
        }
        assertNotNull(emptyCell, "the deterministic window must contain at least one empty cell");
        assertFalse(job.markRevealed(emptyCell.x(), emptyCell.z()),
                "a stale reveal for an empty deterministic cell must not create a star");
        while (!job.complete()) job.step(512);

        var completed = InfiniteUniversePageSnapshot.occupancy(seed, false, job);
        assertTrue(completed.hasSystemAt(0));
        assertTrue(completed.isRevealedAt(0),
                "the final page snapshot must include a reveal whose delta could arrive before that snapshot");
        int pendingIndex = Math.toIntExact(notYetScanned.x()) * 64 + Math.toIntExact(notYetScanned.z());
        assertTrue(completed.hasSystemAt(pendingIndex));
        assertTrue(completed.isRevealedAt(pendingIndex),
                "pre-setting a future page cell must survive its later deterministic occupancy pass");
    }

    @Test
    void snapshotRejectsTrailingBitsAndMalformedPayloadLength() {
        assertThrows(IllegalArgumentException.class, () -> new InfiniteUniversePageSnapshot(
                1L, true, false, 0, 0, 1, 1, new byte[]{(byte) 0x80}, new byte[]{0}));
        assertThrows(IllegalArgumentException.class, () -> new InfiniteUniversePageSnapshot(
                1L, true, false, 0, 0, 1, 1, new byte[]{0}, new byte[]{1}),
                "a discovery bit cannot exist without the corresponding occupied system");

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeLong(1L);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeLong(0L);
        buffer.writeLong(0L);
        buffer.writeVarInt(1);
        buffer.writeVarInt(1);
        buffer.writeVarInt(2);
        var malformed = assertThrows(IllegalArgumentException.class,
                () -> InfiniteUniversePageSnapshot.decode(buffer));
        assertTrue(malformed.getMessage().contains("size=1x1: expected 1 bytes, got 2"));

        var badWindow = new FriendlyByteBuf(Unpooled.buffer());
        badWindow.writeLong(1L);
        badWindow.writeBoolean(true);
        badWindow.writeBoolean(false);
        badWindow.writeLong(10L);
        badWindow.writeLong(-20L);
        badWindow.writeVarInt(65);
        badWindow.writeVarInt(64);
        var invalidWindow = assertThrows(IllegalArgumentException.class,
                () -> InfiniteUniversePageSnapshot.decode(badWindow));
        assertTrue(invalidWindow.getMessage().contains("invalid logical page window at 10,-20 size=65x64"));

        var badDiscoveryLength = new FriendlyByteBuf(Unpooled.buffer());
        badDiscoveryLength.writeLong(1L);
        badDiscoveryLength.writeBoolean(true);
        badDiscoveryLength.writeBoolean(false);
        badDiscoveryLength.writeLong(0L);
        badDiscoveryLength.writeLong(0L);
        badDiscoveryLength.writeVarInt(1);
        badDiscoveryLength.writeVarInt(1);
        badDiscoveryLength.writeVarInt(1);
        badDiscoveryLength.writeByte(1);
        badDiscoveryLength.writeVarInt(2);
        var invalidDiscoveryLength = assertThrows(IllegalArgumentException.class,
                () -> InfiniteUniversePageSnapshot.decode(badDiscoveryLength));
        assertTrue(invalidDiscoveryLength.getMessage().contains("invalid logical page discovery payload"));
    }

    @Test
    void unavailableFactoryProducesAnEmptyUnavailableWindow() {
        var request = InfiniteUniversePageRequest.viewportAt(-64, 128);
        var snapshot = InfiniteUniversePageSnapshot.unavailable(73L, request);

        assertFalse(snapshot.available());
        assertFalse(snapshot.warpAllowed());
        assertEquals(request.originX(), snapshot.originX());
        assertEquals(request.originZ(), snapshot.originZ());
        assertEquals(512, snapshot.occupied().length);
        assertEquals(512, snapshot.revealed().length);
        for (byte value : snapshot.occupied()) assertEquals(0, value);
        for (byte value : snapshot.revealed()) assertEquals(0, value);
    }

    @Test
    void rapidPageChangesCoalesceWithoutRestartingTheActiveJob() {
        var active = InfiniteUniversePageRequest.viewportAt(0, 0);
        var pageB = InfiniteUniversePageRequest.viewportAt(64, 0);
        var pageC = InfiniteUniversePageRequest.viewportAt(128, 0);

        var followUp = InfiniteUniversePageQueuePolicy.followUp(active, null, pageB);
        assertEquals(pageB, followUp);
        followUp = InfiniteUniversePageQueuePolicy.followUp(active, followUp, pageC);
        assertEquals(pageC, followUp, "latest viewport replaces only the queued follow-up");
        followUp = InfiniteUniversePageQueuePolicy.followUp(active, followUp, active);
        assertNull(followUp, "panning back to the active page cancels the redundant follow-up");
    }
}
