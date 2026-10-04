package net.bullettrain.xenopixelsmod.aero.v2;

import net.minecraft.nbt.CompoundTag;

/** Per-host v2 extras persisted on the existing control-host block entities. */
public final class GuidanceV2State {
    public static final String TAG = "GuidanceV2";
    private GuidanceV2SurfaceMode surfaceMode = GuidanceV2SurfaceMode.THRUST_AND_FLAPS;

    public GuidanceV2SurfaceMode surfaceMode() {
        return surfaceMode;
    }

    public void setSurfaceMode(GuidanceV2SurfaceMode mode) {
        this.surfaceMode = mode == null ? GuidanceV2SurfaceMode.THRUST_AND_FLAPS : mode;
    }

    public void save(CompoundTag tag) {
        CompoundTag nested = new CompoundTag();
        nested.putString("SurfaceMode", surfaceMode.name());
        tag.put(TAG, nested);
    }

    public void load(CompoundTag tag) {
        if (tag == null || !tag.contains(TAG)) {
            surfaceMode = GuidanceV2SurfaceMode.THRUST_AND_FLAPS;
            return;
        }
        surfaceMode = GuidanceV2SurfaceMode.byName(tag.getCompound(TAG).getString("SurfaceMode"));
    }
}
