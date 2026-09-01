package net.bullettrain.xenopixelsmod.compat.npc;

/** Xeno fields mixed into CNPC Gecko Addon's {@code CustomModelData}. */
public interface NpcHairModelData {
    boolean xenopixels$isDmzHairEnabled();
    void xenopixels$setDmzHairEnabled(boolean enabled);
    String xenopixels$getDmzHairCode();
    void xenopixels$setDmzHairCode(String code);
    String xenopixels$getDmzHairColor();
    void xenopixels$setDmzHairColor(String color);
}
