package net.bullettrain.xenopixelsmod.space.pod;

import net.bullettrain.xenopixelsmod.compat.sable.SableLoadingTickets;
import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;

/** Fixed, compact P15 Classic Egg hull definition. This profile is immutable in pod v1. */
public final class ClassicEggPodHull {
    public static final int EXTENT = 3;
    private static final SubLevelLoadingTicketType<String> BUILD_TICKET = SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "space_pod_handoff"), Codec.STRING);
    private static final BlockState HULL = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState TRIM = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState CANOPY = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
    private static final String DATA_POD_ID = "xenopixelsmod:space_pod_id";
    private static final String DATA_SCHEMA_VERSION = "xenopixelsmod:space_pod_schema";
    private static final String DATA_HULL_ID = "xenopixelsmod:space_pod_hull";
    private static final String DATA_HULL_FIXED = "xenopixelsmod:space_pod_fixed_hull";
    private static final String DATA_COCKPIT_ID = "xenopixelsmod:space_pod_cockpit";
    private static final String DATA_THRUSTER_ID = "xenopixelsmod:space_pod_thruster_skirt";
    private static final String DATA_TRIM_ID = "xenopixelsmod:space_pod_trim";
    private static final String DATA_PRIMARY_ARGB = "xenopixelsmod:space_pod_primary_argb";
    private static final String DATA_SECONDARY_ARGB = "xenopixelsmod:space_pod_secondary_argb";
    private static final String DATA_ACCENT_ARGB = "xenopixelsmod:space_pod_accent_argb";
    private record PendingTicket(ServerLevel level, UUID bodyId, String key) {}
    private static final Map<MinecraftServer, Set<PendingTicket>> UNRESOLVED_TICKETS = new IdentityHashMap<>();

    private ClassicEggPodHull() {}

    /** Forces ticket-type registration during mod construction, before saved Sable tickets are decoded. */
    public static void bootstrap() {}

    /** Removes persisted handoff tickets that cannot have a live owner after startup or during shutdown. */
    public static void releaseUnownedHandoffTickets(MinecraftServer server, String phase) {
        Objects.requireNonNull(server, "server");
        int removed = 0;
        Set<PendingTicket> unresolved = UNRESOLVED_TICKETS.computeIfAbsent(server, ignored -> new LinkedHashSet<>());
        for (ServerLevel level : server.getAllLevels()) {
            var container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (var info : new java.util.ArrayList<>(container.getAllTickets().values())) {
                for (var ticket : new java.util.ArrayList<>(info.tickets())) {
                    if (!BUILD_TICKET.equals(SableLoadingTickets.type(ticket))) continue;
                    PendingTicket pending = new PendingTicket(level, SableLoadingTickets.subLevelId(ticket), String.valueOf(SableLoadingTickets.key(ticket)));
                    if (releaseTicket(pending)) removed++;
                    else unresolved.add(pending);
                }
            }
        }
        if (removed > 0) XenoPixelsMod.LOGGER.info("Released {} unowned space-pod handoff ticket(s) during {}", removed, phase);
        if (!unresolved.isEmpty())
            XenoPixelsMod.LOGGER.warn("{} space-pod handoff ticket(s) await their Sable body during {}; retrying on server ticks",
                    unresolved.size(), phase);
        if ("server shutdown".equals(phase)) UNRESOLVED_TICKETS.remove(server);
    }

    /** Retries ticket cleanup after Sable finishes restoring any bodies that were unavailable at ServerStarted. */
    public static void retryUnownedHandoffTickets(MinecraftServer server) {
        Set<PendingTicket> pending = UNRESOLVED_TICKETS.get(server);
        if (pending == null || pending.isEmpty() || server.getTickCount() % 20 != 0) return;
        int released = 0;
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            if (releaseTicket(iterator.next())) {
                iterator.remove();
                released++;
            }
        }
        if (released > 0) XenoPixelsMod.LOGGER.info("Released {} deferred space-pod handoff ticket(s)", released);
        if (pending.isEmpty()) UNRESOLVED_TICKETS.remove(server);
        else if (server.getTickCount() % 200 == 0)
            XenoPixelsMod.LOGGER.warn("{} orphaned space-pod handoff ticket(s) still await Sable bodies after {} ticks",
                    pending.size(), server.getTickCount());
    }

    private static boolean releaseTicket(PendingTicket pending) {
        var container = SubLevelContainer.getContainer(pending.level());
        if (container == null) return false;
        var candidate = container.getSubLevel(pending.bodyId());
        if (!(candidate instanceof ServerSubLevel body) || body.isRemoved()) return false;
        container.removeForceLoadTicket(body, BUILD_TICKET, pending.key());
        return true;
    }

    /** Builds the same fixed hull at a destination plot centre; callers restore final pose and motion after finish. */
    public static BuildJob start(ServerLevel destination, Vector3d plotCentre, UUID podId) {
        return start(destination, plotCentre, podId, SpacePodLoadout.classicEgg());
    }

    /** Builds the implemented Classic Egg shape while preserving the selected slot IDs and colors as stable metadata. */
    public static BuildJob start(ServerLevel destination, Vector3d plotCentre, UUID podId, SpacePodLoadout loadout) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(plotCentre, "plotCentre");
        Objects.requireNonNull(podId, "podId");
        Objects.requireNonNull(loadout, "loadout");
        ResourceLocation classicEggId = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "pod/hull/classic_egg");
        if (!classicEggId.equals(loadout.hull()))
            throw new IllegalArgumentException("only the Classic Egg voxel hull is implemented");
        AtomicBoolean ticketHeld = new AtomicBoolean();
        PlotBodyBuilder.Job delegate = PlotBodyBuilder.start(destination, new Vector3d(plotCentre), EXTENT, ClassicEggPodHull::blockAt)
                .withoutEviction()
                .onBodyAllocated(body -> {
                    var data = body.getUserDataTag();
                    if (data == null) data = new net.minecraft.nbt.CompoundTag();
                    data.putUUID(DATA_POD_ID, podId);
                    data.putInt(DATA_SCHEMA_VERSION, 1);
                    data.putString(DATA_HULL_ID, loadout.hull().toString());
                    data.putBoolean(DATA_HULL_FIXED, true);
                    data.putString(DATA_COCKPIT_ID, loadout.cockpit().toString());
                    data.putString(DATA_THRUSTER_ID, loadout.thrusterSkirt().toString());
                    data.putString(DATA_TRIM_ID, loadout.trim().toString());
                    data.putInt(DATA_PRIMARY_ARGB, loadout.primaryArgb());
                    data.putInt(DATA_SECONDARY_ARGB, loadout.secondaryArgb());
                    data.putInt(DATA_ACCENT_ARGB, loadout.accentArgb());
                    body.setUserDataTag(data);
                    // A freshly allocated pod has no rider chunk tickets yet. Keep it loaded through build and
                    // the dimension/rider handoff; the pod lifecycle owner releases this after arrival is confirmed.
                    SubLevelContainer.getContainer(destination).addForceLoadTicket(body, BUILD_TICKET, podId.toString());
                    ticketHeld.set(true);
                })
                .onCancellation(body -> {
                    if (ticketHeld.compareAndSet(true, false)) releaseBuildTicket(body, podId);
                });
        return new BuildJob(delegate, podId, ticketHeld);
    }

    /** Removes the temporary handoff ticket after the destination pod is safely owned by its lifecycle. */
    public static void releaseBuildTicket(ServerSubLevel body, UUID podId) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(podId, "podId");
        var data = body.getUserDataTag();
        if (data == null || !data.hasUUID(DATA_POD_ID) || !podId.equals(data.getUUID(DATA_POD_ID)))
            throw new IllegalStateException("cannot release pod handoff ticket: stable pod id missing or mismatched");
        SubLevelContainer.getContainer(body.getLevel()).removeForceLoadTicket(body, BUILD_TICKET, podId.toString());
    }

    /** Ticket-aware wrapper: failures and cancellation remove the ticket before PlotBodyBuilder tears the plot down. */
    public static final class BuildJob {
        private final PlotBodyBuilder.Job delegate;
        private final UUID podId;
        private final AtomicBoolean ticketHeld;

        private BuildJob(PlotBodyBuilder.Job delegate, UUID podId, AtomicBoolean ticketHeld) {
            this.delegate = delegate;
            this.podId = podId;
            this.ticketHeld = ticketHeld;
        }

        public ServerSubLevel body() { return delegate.body(); }
        public boolean done() { return delegate.done(); }
        public boolean failed() { return delegate.failed(); }
        public int installed() { return delegate.installed(); }
        PlotBodyBuilder.Job workJob() { return delegate; }
        boolean fillReady() { return delegate.fillReady(); }

        /** Called by the pod lifecycle after the destination rider has been confirmed. */
        public void releaseHandoffTicket() {
            ServerSubLevel body = delegate.body();
            if (ticketHeld.compareAndSet(true, false)) releaseBuildTicket(body, podId);
        }

        public boolean step(int voxelBudget) {
            try {
                return delegate.step(voxelBudget);
            } catch (RuntimeException | Error failure) {
                throw failure;
            }
        }

        public boolean step(int voxelBudget, double millisBudget) {
            try {
                return delegate.step(voxelBudget, millisBudget);
            } catch (RuntimeException | Error failure) {
                throw failure;
            }
        }

        public void cancel() {
            delegate.cancel();
        }
    }

    /** Returns a deterministic block state in hull-local coordinates; all unlisted voxels are air. */
    public static BlockState blockAt(int x, int y, int z) {
        if (y < -EXTENT || y > EXTENT) return Blocks.AIR.defaultBlockState();
        int radius = switch (y) {
            case -3, 3 -> 1;
            case -2, 2 -> 2;
            default -> 3;
        };
        int radialSquared = x * x + z * z;
        if (radialSquared > radius * radius) return Blocks.AIR.defaultBlockState();

        // The centre is a two-block-tall seat cavity. The front canopy closes its forward face.
        if (y >= -1 && y <= 1 && radialSquared <= Math.max(0, (radius - 1) * (radius - 1)))
            return Blocks.AIR.defaultBlockState();
        if (z == radius && Math.abs(x) <= 1 && (y == 0 || y == 1)) return CANOPY;
        if (y == -2 || y == 2) return TRIM;
        return HULL;
    }
}
