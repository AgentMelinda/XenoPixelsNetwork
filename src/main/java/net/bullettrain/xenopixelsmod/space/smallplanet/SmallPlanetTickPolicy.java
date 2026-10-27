package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Separates recovery of existing small planets from admission of new incarnations. */
public final class SmallPlanetTickPolicy {
    public record Decision(boolean retryAdoption, boolean runPass, boolean advanceExistingBuild,
                           boolean startNewBody) {}

    private SmallPlanetTickPolicy() {}

    public static Decision plan(boolean enabled, boolean adopted, boolean adoptionRetryDue, boolean passDue) {
        return new Decision(!adopted && adoptionRetryDue, enabled && passDue, enabled,
                enabled && adopted);
    }

    public static boolean mayRelease(boolean placementTestBody, boolean editedPinned, boolean placementPending) {
        return !placementTestBody && !editedPinned && !placementPending;
    }
}
