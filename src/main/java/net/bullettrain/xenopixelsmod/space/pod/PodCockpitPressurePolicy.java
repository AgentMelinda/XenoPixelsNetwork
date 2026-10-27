package net.bullettrain.xenopixelsmod.space.pod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Keeps legacy ship cabins while making pod pressure a server-authoritative cockpit property. */
public final class PodCockpitPressurePolicy {
    private static final String POD_ID = "xenopixelsmod:space_pod_id";
    private static final String COCKPIT_ID = "xenopixelsmod:space_pod_cockpit";

    private PodCockpitPressurePolicy() {}

    /** Non-pod ships retain the existing enclosing-sublevel cabin behavior. */
    public static boolean pressurizedCabin(CompoundTag userData, PodCockpitCatalog.Snapshot catalog) {
        if (userData == null || !userData.contains(POD_ID)) return true;
        if (!userData.hasUUID(POD_ID)
                || !userData.contains(COCKPIT_ID, Tag.TAG_STRING)) return false;
        ResourceLocation cockpit = ResourceLocation.tryParse(userData.getString(COCKPIT_ID));
        if (cockpit == null || catalog == null) return false;
        PodCockpitDefinition definition = catalog.cockpits().get(cockpit);
        return definition != null && definition.pressurized();
    }
}
