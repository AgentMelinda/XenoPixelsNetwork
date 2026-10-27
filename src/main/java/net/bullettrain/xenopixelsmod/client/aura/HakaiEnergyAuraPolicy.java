package net.bullettrain.xenopixelsmod.client.aura;

/** The existing second-aura switch selects a native Sparking visual in the destroyer form. */
public final class HakaiEnergyAuraPolicy {
    public static final String FORM_GROUP = "xenopixels_hakaishin";
    private HakaiEnergyAuraPolicy() {}

    public static boolean active(boolean toggle, boolean transformed, String group) {
        return toggle && transformed && FORM_GROUP.equals(group);
    }

    /** Keep the Hakai idle visual hidden without swallowing DragonMineZ's ki charge aura. */
    public static boolean suppressAura(boolean chargingKi) {
        return !chargingKi;
    }
}
