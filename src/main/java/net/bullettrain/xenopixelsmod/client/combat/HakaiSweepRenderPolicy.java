package net.bullettrain.xenopixelsmod.client.combat;

import net.minecraft.world.phys.Vec3;

/** Pure render-boundary policy for the entity Hakai sweep. */
final class HakaiSweepRenderPolicy {
    private HakaiSweepRenderPolicy() {}

    /** Keep the rim visible at progress 1 while the server holds the fully erased body for removal. */
    static boolean shouldRenderRing(float progress) {
        return Float.isFinite(progress) && progress > 0.0f && progress <= 1.0f;
    }

    /** A 0/0 clear packet follows a completed erase immediately before the server removes the mob. */
    static boolean shouldHoldEndRing(float projectedProgress) {
        return Float.isFinite(projectedProgress) && projectedProgress >= 1.0f;
    }

    /** A duplicate terminal clear must not erase an endpoint ring captured by the first clear. */
    static boolean shouldKeepExistingEndRing(boolean exists, double now, double expiresAt) {
        return exists && Double.isFinite(now) && Double.isFinite(expiresAt) && now < expiresAt;
    }

    /** How far ahead of the server, in ticks of the line's own slope, the client's projection may run before a
     * correction is obeyed instead of re-anchored. Covers packet latency plus the one-tick lead on screen. */
    private static final float REANCHOR_LEAD_TICKS = 4.0f;

    /**
     * Re-anchor a correction line at the value already on screen instead of at the packet's value. The packet's value is
     * used when nothing is on screen, when the server has run past the projection, or when the body is being restored
     * (negative rate). Otherwise the projection is kept for at most one packet interval plus latency of lead, so a
     * monotonic erase can never have its cut snapped back upwards by a correction - the multiplayer staircase.
     *
     * @param sentRate    slope of the line currently on screen
     * @param projected   that line's value at this frame
     * @param packetValue the server's authoritative value at its own tick
     * @param fresh       true when no line is on screen for this entity yet
     */
    static float reanchorProgress(float sentRate, float projected, float packetValue, boolean fresh) {
        if (fresh || sentRate < 0.0f || packetValue >= projected) return packetValue;
        return projected - packetValue <= Math.max(0.02f, sentRate * REANCHOR_LEAD_TICKS) ? projected : packetValue;
    }

    /**
     * Smooth a model-width change without allowing the moving cut ring to collapse below the mob's stable minimum.
     * The caller keeps this value per entity and prunes it with the entity lifecycle.
     */
    static float stableRingRadius(float previous, float measured, float minimum) {
        float floor = Float.isFinite(minimum) && minimum > 0.0f ? minimum : 0.15f;
        float target = Float.isFinite(measured) && measured > 0.0f ? Math.max(floor, measured) : floor;
        if (!Float.isFinite(previous) || previous <= 0.0f) return target;
        return Math.max(floor, previous + (target - previous) * 0.25f);
    }

    /**
     * Keep the annulus outside the mob's measured horizontal half-width so the mob cannot depth-occlude its near side.
     * The broader outer fade also keeps the rim legible when viewed at a shallow camera angle.
     */
    static float[] visibleRingBand(float radius) {
        float base = Float.isFinite(radius) && radius > 0.0f ? Math.max(0.15f, radius) : 0.15f;
        return new float[]{base * 1.05f, base * 1.65f};
    }

    /**
     * Fit the cut to the measured world-space model bounds, rebased by the entity's translation since measurement.
     * Measurements are one render frame old; preserving their old absolute Y makes a moving model outrun its cut plane.
     *
     * @return {@code bottom, top, centreX, centreZ, halfWidth}
     */
    static float[] fittedWorldBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                     double measuredEntityY,
                                     Vec3 renderPosition,
                                     float entityWidth, float entityHeight) {
        float fallbackHeight = Math.max(0.1f, entityHeight);
        float fallbackHalfWidth = Math.max(0.15f, entityWidth * 0.5f);
        float bottom = (float) renderPosition.y;
        float top = bottom + fallbackHeight;
        float halfWidth = fallbackHalfWidth;
        if (Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
                && Double.isFinite(measuredEntityY) && maxY > minY) {
            float yDelta = (float) (renderPosition.y - measuredEntityY);
            bottom = minY + yDelta;
            top = Math.max(maxY + yDelta, bottom + 0.1f);
            top = Math.min(top, bottom + fallbackHeight * 1.6f + 0.6f);
            float maxHalfWidth = Math.max(1.5f, fallbackHalfWidth * 1.5f);
            halfWidth = Math.min(maxHalfWidth,
                    Math.max(0.15f, Math.max(maxX - minX, maxZ - minZ) * 0.5f));
        }
        return new float[]{bottom, top, (float) renderPosition.x, (float) renderPosition.z, halfWidth};
    }

    /** Returns centroid X/Z, outer radius, and projected area for a closed contour stored as x/z pairs. */
    static float[] fittedRing(float[] xz) {
        if (xz == null || xz.length < 8 || (xz.length & 1) != 0) return null;
        int count = xz.length / 2;
        if (xz[0] == xz[xz.length - 2] && xz[1] == xz[xz.length - 1]) count--;
        if (count < 3) return null;
        double centerX = 0, centerZ = 0;
        for (int i = 0; i < count; i++) {
            if (!Float.isFinite(xz[2 * i]) || !Float.isFinite(xz[2 * i + 1])) return null;
            centerX += xz[2 * i]; centerZ += xz[2 * i + 1];
        }
        centerX /= count; centerZ /= count;
        double twiceArea = 0;
        float radius = 0;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            double x0 = xz[2 * i] - xz[0], z0 = xz[2 * i + 1] - xz[1];
            double x1 = xz[2 * j] - xz[0], z1 = xz[2 * j + 1] - xz[1];
            twiceArea += x0 * z1 - x1 * z0;
            radius = Math.max(radius, (float) Math.hypot(xz[2 * i] - centerX, xz[2 * i + 1] - centerZ));
        }
        double area = Math.abs(twiceArea) * 0.5;
        if (!(radius > 0) || !(area > 0) || !Double.isFinite(area)) return null;
        return new float[]{(float) centerX, (float) centerZ, radius, (float) area};
    }

    /** Move a retained cut contour vertically onto the current sweep plane. */
    static float contourYOffset(float currentCutY, float previousContourY) {
        return Float.isFinite(currentCutY) && Float.isFinite(previousContourY) ? currentCutY - previousContourY : 0.0f;
    }
}
