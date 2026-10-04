package net.bullettrain.xenopixelsmod.hair;

/**
 * Lab-aligned hair strand fields (PR-D7a/b). Mirrors {@code HairStrand} in
 * {@code tools/dmz-hair-builder-site/lib/hair-model.ts}.
 *
 * <p><b>No</b> {@code connected} or {@code movable} boolean — parenting is geometry-only.
 */
public final class HairStrandModel {
    private final int id;
    private int length;
    private float lengthScale = 1f;
    private float rotationX;
    private float rotationY;
    private float rotationZ;
    private float scaleX = 1f;
    private float scaleY = 1f;
    private float scaleZ = 1f;
    private float cubeWidth = 2f;
    private float cubeHeight = 2f;
    private float cubeDepth = 2f;
    private float curveX;
    private float curveY;
    private float curveZ;
    private String color;

    public HairStrandModel(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** Visible for editing UI when length &gt; 0 (lab {@code isStrandVisible}). */
    public boolean visible() {
        return length > 0;
    }

    public int length() {
        return length;
    }

    public void length(int length) {
        this.length = Math.max(0, length);
    }

    public float lengthScale() {
        return lengthScale;
    }

    public void lengthScale(float lengthScale) {
        this.lengthScale = lengthScale;
    }

    public float rotationX() {
        return rotationX;
    }

    public void rotationX(float rotationX) {
        this.rotationX = rotationX;
    }

    public float rotationY() {
        return rotationY;
    }

    public void rotationY(float rotationY) {
        this.rotationY = rotationY;
    }

    public float rotationZ() {
        return rotationZ;
    }

    public void rotationZ(float rotationZ) {
        this.rotationZ = rotationZ;
    }

    public float scaleX() {
        return scaleX;
    }

    public void scaleX(float scaleX) {
        this.scaleX = scaleX;
    }

    public float scaleY() {
        return scaleY;
    }

    public void scaleY(float scaleY) {
        this.scaleY = scaleY;
    }

    public float scaleZ() {
        return scaleZ;
    }

    public void scaleZ(float scaleZ) {
        this.scaleZ = scaleZ;
    }

    public float cubeWidth() {
        return cubeWidth;
    }

    public void cubeWidth(float cubeWidth) {
        this.cubeWidth = cubeWidth;
    }

    public float cubeHeight() {
        return cubeHeight;
    }

    public void cubeHeight(float cubeHeight) {
        this.cubeHeight = cubeHeight;
    }

    public float cubeDepth() {
        return cubeDepth;
    }

    public void cubeDepth(float cubeDepth) {
        this.cubeDepth = cubeDepth;
    }

    public float curveX() {
        return curveX;
    }

    public void curveX(float curveX) {
        this.curveX = curveX;
    }

    public float curveY() {
        return curveY;
    }

    public void curveY(float curveY) {
        this.curveY = curveY;
    }

    public float curveZ() {
        return curveZ;
    }

    public void curveZ(float curveZ) {
        this.curveZ = curveZ;
    }

    /** Optional per-strand override; {@code null} means use global colour. */
    public String color() {
        return color;
    }

    public void color(String color) {
        this.color = color == null || color.isBlank() ? null : color.trim();
    }

    public HairStrandModel copy() {
        HairStrandModel copy = new HairStrandModel(id);
        copy.length = length;
        copy.lengthScale = lengthScale;
        copy.rotationX = rotationX;
        copy.rotationY = rotationY;
        copy.rotationZ = rotationZ;
        copy.scaleX = scaleX;
        copy.scaleY = scaleY;
        copy.scaleZ = scaleZ;
        copy.cubeWidth = cubeWidth;
        copy.cubeHeight = cubeHeight;
        copy.cubeDepth = cubeDepth;
        copy.curveX = curveX;
        copy.curveY = curveY;
        copy.curveZ = curveZ;
        copy.color = color;
        return copy;
    }
}
