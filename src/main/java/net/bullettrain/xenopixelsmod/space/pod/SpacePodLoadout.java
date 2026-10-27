package net.bullettrain.xenopixelsmod.space.pod;

import net.bullettrain.xenopixelsmod.fx.aura.AuraPalette;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable slot and paint selection persisted with a pod body. Model/preview rendering is supplied by later P15 slices. */
public record SpacePodLoadout(ResourceLocation hull, ResourceLocation cockpit, ResourceLocation thrusterSkirt,
                              ResourceLocation trim, int primaryArgb, int secondaryArgb, int accentArgb) {
    public SpacePodLoadout {
        hull = requireSlot(hull, "hull", "hull");
        cockpit = requireSlot(cockpit, "cockpit", "cockpit");
        thrusterSkirt = requireSlot(thrusterSkirt, "thruster", "thrusterSkirt");
        trim = requireSlot(trim, "trim", "trim");
        primaryArgb = paletteArgb(primaryArgb);
        secondaryArgb = paletteArgb(secondaryArgb);
        accentArgb = paletteArgb(accentArgb);
    }

    /** Initial built-in profile; only the Classic Egg physical voxel hull is implemented in pod v1. */
    public static SpacePodLoadout classicEgg() {
        return new SpacePodLoadout(id("pod/hull/classic_egg"), id("pod/cockpit/bubble"),
                id("pod/thruster/triple_jet"), id("pod/trim/none"),
                0xFFFFFF, 0xB8C5D6, 0x4ED7FF);
    }

    private static ResourceLocation requireSlot(ResourceLocation id, String slotType, String fieldName) {
        Objects.requireNonNull(id, fieldName);
        String prefix = "pod/" + slotType + "/";
        if (!id.getPath().startsWith(prefix) || id.getPath().length() == prefix.length())
            throw new IllegalArgumentException("pod " + fieldName + " id must use the " + prefix + " path");
        return id;
    }

    private static int paletteArgb(int requestedRgb) {
        return 0xFF000000 | AuraPalette.nearest(requestedRgb & 0xFFFFFF);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("xenopixelsmod", path);
    }
}
