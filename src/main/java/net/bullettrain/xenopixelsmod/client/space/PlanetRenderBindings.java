package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.PhysicalPlanetBindings;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Small, GL-free binding state helper so dimension replacement and duplicate UUIDs are testable. */
final class PlanetRenderBindings {
    /** Canonical chart identity for the disposable orbit-dimension Earth physics spike. */
    static final String ORBIT_EARTH_ID = "xenopixelsmod:earth";

    private PlanetRenderBindings() {}

    /** Prefer an exact live-body centre for far render layers; use the chart centre until that body is present. */
    static void setProjectionCenter(org.joml.Vector3d destination,double chartX,double chartY,double chartZ,
                                    org.joml.Vector3dc liveCenter) {
        if(liveCenter==null) destination.set(chartX,chartY,chartZ);
        else destination.set(liveCenter);
    }

    static void bindSingle(Map<String, UUID> target, String key, UUID bodyId) {
        target.clear();
        if (bodyId != null) target.put(key, bodyId);
    }

    /** Bind the orbit spike as chart Earth so far-proxy pose lookup and HD handoff share one key. */
    static void bindOrbitEarth(Map<String, UUID> target, UUID bodyId) {
        bindSingle(target, ORBIT_EARTH_ID, bodyId);
    }

    static void bindAll(Map<String, UUID> target, PhysicalPlanetBindings packet) {
        target.clear();
        Set<UUID> seenBodies = new HashSet<>();
        for (PhysicalPlanetBindings.Entry entry : packet.entries())
            if (seenBodies.add(entry.uuid())) target.put(entry.bodyId(), entry.uuid());
    }

    /** True only when this stable body key's exact Sable UUID is currently live in the receiving level. */
    static boolean hasLiveBody(Map<String, UUID> bindings, String bodyId, Predicate<UUID> isLive) {
        UUID uuid = bindings.get(bodyId);
        return uuid != null && isLive.test(uuid);
    }
}
