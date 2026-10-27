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

    /** Ticks the charge aura is kept after the status flag last read true, so a flickering flag cannot tear down the emitter. */
    public static final int CHARGE_GRACE_TICKS = 6;

    /** Same as {@link #suppressAura(boolean)} but tolerates brief drops of the charging flag. */
    public static boolean suppressAura(boolean chargingKi, long ticksSinceCharging) {
        return !chargingKi && (ticksSinceCharging < 0 || ticksSinceCharging > CHARGE_GRACE_TICKS);
    }
}
