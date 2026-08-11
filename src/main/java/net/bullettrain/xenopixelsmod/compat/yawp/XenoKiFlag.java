package net.bullettrain.xenopixelsmod.compat.yawp;

import de.z0rdak.yawp.core.flag.FlagMessage;
import de.z0rdak.yawp.core.flag.FlagState;
import de.z0rdak.yawp.core.flag.FlagType;
import de.z0rdak.yawp.core.flag.IFlag;
import org.jetbrains.annotations.NotNull;

/**
 * A ki-griefing flag presented through YAWP's command layer but stored by us.
 *
 * <p>YAWP's flags are an enum, so ours cannot be one of them. What it <i>does</i> have is an
 * interface — {@link IFlag} is entirely String-named, and its own javadoc calls the enum a
 * temporary implementation ("Mod:Name -&gt; ResourceLocation in the future"). Implementing the
 * interface lets our flags travel through the command path without ever becoming a
 * {@code RegionFlag}.
 *
 * <p><b>These instances are deliberately transient.</b> They are constructed on demand for a
 * single command invocation and never inserted into a region's flag map, so YAWP never
 * serializes them. That is the property that makes this safe: a YAWP save file cannot end up
 * containing a flag its own deserializer does not understand. Persistence is entirely
 * {@link KiRegionFlags}.
 *
 * <p>Touches YAWP types directly and must only load when YAWP is present — enforced by the
 * mixin gate in {@code ConditionalMixinPlugin}.
 */
public final class XenoKiFlag implements IFlag {

    public static final String PLAYERS = "ki-griefing-players";
    public static final String MOBS = "ki-griefing-mobs";
    public static final String MASTERS = "ki-griefing-masters";

    /** Every flag name we inject into YAWP's command layer. */
    public static final java.util.List<String> NAMES = java.util.List.of(PLAYERS, MOBS, MASTERS);

    private final String name;
    private final String dimension;
    private final String region;
    private FlagMessage message = FlagMessage.DEFAULT_FLAG_MSG;
    private boolean override;

    public XenoKiFlag(String name, String dimension, String region) {
        this.name = name;
        this.dimension = dimension;
        this.region = region;
    }

    public static boolean isOurs(String flagName) {
        return flagName != null && NAMES.contains(flagName);
    }

    /** Map a flag name onto the target it controls. */
    public static KiRegionFlags.Target targetOf(String flagName) {
        if (PLAYERS.equals(flagName)) return KiRegionFlags.Target.PLAYERS;
        if (MOBS.equals(flagName)) return KiRegionFlags.Target.MOBS;
        if (MASTERS.equals(flagName)) return KiRegionFlags.Target.MASTERS;
        return null;
    }

    public String dimension() {
        return dimension;
    }

    public String region() {
        return region;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FlagType getType() {
        // Closest match: these deny an action at a position, like YAWP's own block flags.
        return FlagType.BOOLEAN_FLAG;
    }

    @Override
    public boolean doesOverride() {
        return override;
    }

    @Override
    public void setOverride(boolean doesOverride) {
        this.override = doesOverride;
    }

    @Override
    public boolean isActive() {
        FlagState state = getState();
        return state == FlagState.ALLOWED || state == FlagState.DENIED;
    }

    @Override
    public FlagState getState() {
        return switch (KiRegionFlags.get(dimension, region, targetOf(name))) {
            case ALLOWED -> FlagState.ALLOWED;
            case DENIED -> FlagState.DENIED;
            default -> FlagState.UNDEFINED;
        };
    }

    @Override
    public void setState(FlagState state) {
        KiRegionFlags.State mapped = switch (state) {
            case ALLOWED -> KiRegionFlags.State.ALLOWED;
            case DENIED -> KiRegionFlags.State.DENIED;
            default -> KiRegionFlags.State.DEFAULT;
        };
        KiRegionFlags.set(dimension, region, targetOf(name), mapped);
    }

    @Override
    public FlagMessage getFlagMsg() {
        return message;
    }

    @Override
    public void setFlagMsg(FlagMessage msg) {
        this.message = msg;
    }

    @Override
    public int compareTo(@NotNull IFlag other) {
        return name.compareTo(other.getName());
    }
}
