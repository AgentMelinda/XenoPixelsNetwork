package net.bullettrain.xenopixelsmod.hair;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairLegacyMigrator;
import com.dragonminez.common.hair.HairStrand;
import net.minecraft.nbt.CompoundTag;

/** Bridges legacy maker controls and DMZ 2.2's segment geometry without dropping native fields. */
final class HairStrandCompatibility {
    private HairStrandCompatibility() {}

    static void importNative(HairStrand source, HairStrandModel model) {
        model.length(source.getSegments());
        model.lengthScale(1f);
        model.rotationX(source.getRotationX());
        model.rotationY(source.getRotationY());
        model.rotationZ(source.getRotationZ());
        model.scaleX(1f);
        model.scaleY(1f);
        model.scaleZ(1f);
        model.cubeWidth(source.getWidth());
        model.cubeHeight(source.getLength() / decaySum(source.getSegments()));
        model.cubeDepth(source.getDepth());
        int joints = Math.max(1, source.getSegments() - 1);
        model.curveX(source.getBendX() / joints);
        model.curveY(source.getBendY() / joints);
        model.curveZ(source.getBendZ() / joints);
        model.color(source.getColor());
        model.nativeData(source.save());
    }

    static HairStrand toNative(HairStrandModel model, CustomHair.HairFace face, int index) {
        CompoundTag data = model.nativeData();
        if (data == null) {
            HairStrand migrated = HairLegacyMigrator.migrateStrand(face, index, legacyTag(model));
            // CustomHair.load performs this after all pre-v7 loads, including legacy migration.
            migrated.convertAbsoluteSegmentScales();
            return migrated;
        }
        HairStrand strand = new HairStrand();
        strand.load(data);
        HairStrandModel baseline = new HairStrandModel(model.id());
        importNative(strand, baseline);
        // Change only fields exposed by the control that changed. In particular, keep taper,
        // length ratio, root offsets, twist, joint style, tip color and segment overrides.
        strand.setSegments(model.length());
        if (model.rotationX() != baseline.rotationX() || model.rotationY() != baseline.rotationY()
                || model.rotationZ() != baseline.rotationZ()) {
            strand.setRotation(model.rotationX(), model.rotationY(), model.rotationZ());
        }
        if (model.cubeWidth() != baseline.cubeWidth() || model.scaleX() != 1f) {
            strand.setWidth(model.cubeWidth() * model.scaleX());
        }
        if (model.cubeDepth() != baseline.cubeDepth() || model.scaleZ() != 1f) {
            strand.setDepth(model.cubeDepth() * model.scaleZ());
        }
        if (model.cubeHeight() != baseline.cubeHeight() || model.lengthScale() != 1f || model.scaleY() != 1f) {
            strand.setLength(strand.getLength() * model.cubeHeight() / baseline.cubeHeight()
                    * model.lengthScale() * model.scaleY());
        }
        int joints = Math.max(1, model.length() - 1);
        strand.setBend(model.curveX() == baseline.curveX() ? strand.getBendX() : model.curveX() * joints,
                model.curveY() == baseline.curveY() ? strand.getBendY() : model.curveY() * joints,
                model.curveZ() == baseline.curveZ() ? strand.getBendZ() : model.curveZ() * joints);
        if (!java.util.Objects.equals(model.color(), baseline.color())) strand.setColor(model.color());
        return strand;
    }

    private static float decaySum(int segments) {
        return segments <= 0 ? 1f : (float) ((1.0 - Math.pow(0.85f, segments)) / (1.0 - 0.85f));
    }

    private static CompoundTag legacyTag(HairStrandModel model) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("l", model.length());
        tag.putFloat("ls", model.lengthScale());
        tag.putFloat("rx", model.rotationX());
        tag.putFloat("ry", model.rotationY());
        tag.putFloat("rz", model.rotationZ());
        tag.putFloat("sx", model.scaleX());
        tag.putFloat("sy", model.scaleY());
        tag.putFloat("sz", model.scaleZ());
        tag.putFloat("cw", model.cubeWidth());
        tag.putFloat("ch", model.cubeHeight());
        tag.putFloat("cd", model.cubeDepth());
        tag.putFloat("cx", model.curveX());
        tag.putFloat("cy", model.curveY());
        tag.putFloat("cz", model.curveZ());
        if (model.color() != null) tag.putString("c", model.color());
        return tag;
    }
}
