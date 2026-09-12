package net.bullettrain.xenopixelsmod.client.anim.studio;

import java.util.TreeSet;

/**
 * Everything one bone is animated by: four channels that keep their own, independent key times.
 */
public final class AnimBoneTrack {
    public final AnimChannel rotation = new AnimChannel(AnimChannel.Kind.ROTATION);
    public final AnimChannel position = new AnimChannel(AnimChannel.Kind.POSITION);
    public final AnimChannel scale = new AnimChannel(AnimChannel.Kind.SCALE);
    public final AnimChannel visibility = new AnimChannel(AnimChannel.Kind.VISIBILITY);

    public AnimChannel channel(AnimChannel.Kind kind) {
        return switch (kind) {
            case POSITION -> position;
            case SCALE -> scale;
            case VISIBILITY -> visibility;
            default -> rotation;
        };
    }

    public boolean isEmpty() {
        return rotation.isEmpty() && position.isEmpty() && scale.isEmpty() && visibility.isEmpty();
    }

    /** Every time any channel of this bone is keyed at. */
    public TreeSet<Double> times() {
        TreeSet<Double> all = new TreeSet<>();
        all.addAll(rotation.times());
        all.addAll(position.times());
        all.addAll(scale.times());
        all.addAll(visibility.times());
        return all;
    }

    public boolean hasKeyAt(double seconds) {
        return rotation.has(seconds) || position.has(seconds)
                || scale.has(seconds) || visibility.has(seconds);
    }

    /** Removes every channel key at {@code seconds}; true when anything was there. */
    public boolean removeKeysAt(double seconds) {
        boolean removed = rotation.remove(seconds);
        removed |= position.remove(seconds);
        removed |= scale.remove(seconds);
        removed |= visibility.remove(seconds);
        return removed;
    }

    public Double lastTime() {
        Double last = null;
        for (AnimChannel channel : all()) {
            Double time = channel.lastTime();
            if (time != null && (last == null || time > last)) last = time;
        }
        return last;
    }

    /**
     * The pose this bone holds at {@code seconds}.
     *
     * <p>{@code usePosition} and {@code useScale} are set only when the channel actually has keys,
     * so a rotation-only track leaves DragonMineZ's own translation and scaling alone rather than
     * flattening them to origin and 1.
     */
    public AnimBonePose poseAt(double seconds) {
        AnimBonePose pose = new AnimBonePose();
        float[] rot = rotation.valueAt(seconds);
        pose.rotX = rot[0];
        pose.rotY = rot[1];
        pose.rotZ = rot[2];
        if (!position.isEmpty()) {
            float[] pos = position.valueAt(seconds);
            pose.setPosition(pos[0], pos[1], pos[2]);
        }
        if (!scale.isEmpty()) {
            float[] sc = scale.valueAt(seconds);
            pose.setScale(sc[0], sc[1], sc[2]);
        }
        if (!visibility.isEmpty()) {
            pose.setVisible(visibility.valueAt(seconds)[0] >= 0.5f);
        }
        return pose;
    }

    /** Writes every channel this pose owns at {@code seconds}. */
    public void key(double seconds, AnimBonePose pose, String easing) {
        if (pose == null) return;
        rotation.put(seconds, new AnimKey(pose.rotX, pose.rotY, pose.rotZ, easing));
        if (pose.hasPosition()) {
            position.put(seconds, new AnimKey(pose.posX, pose.posY, pose.posZ, easing));
        }
        if (pose.hasScale()) {
            scale.put(seconds, new AnimKey(pose.scaleX, pose.scaleY, pose.scaleZ, easing));
        }
        if (pose.hasVisibility()) {
            visibility.put(seconds, new AnimKey(pose.visible ? 1f : 0f, 0f, 0f, easing));
        }
    }

    public AnimBoneTrack copy() {
        AnimBoneTrack out = new AnimBoneTrack();
        copyInto(rotation, out.rotation);
        copyInto(position, out.position);
        copyInto(scale, out.scale);
        copyInto(visibility, out.visibility);
        return out;
    }

    private AnimChannel[] all() {
        return new AnimChannel[] {rotation, position, scale, visibility};
    }

    private static void copyInto(AnimChannel from, AnimChannel to) {
        to.clear();
        for (var entry : from.entries()) to.put(entry.getKey(), entry.getValue().copy());
    }
}
