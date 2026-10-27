package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.ChatFormatting;

import java.util.Locale;

/**
 * Player-facing success text for a finite-chart discovery. It explicitly says that the system was charted;
 * that success does not imply a physical body is active or that travel is available.
 */
final class DiscoveryAnnouncement {
    static final String SUCCESS_PREFIX = "Successful discovery: ";

    private DiscoveryAnnouncement() {}

    /**
     * @param how            how the cell was revealed, e.g. "detected" or "discovered by Name"
     * @param activationNote {@code SmallPlanetActivationPolicy.discoveryNote}, starting with "; "
     */
    static String charted(String systemName, String starClass, int systemPlanetSlots, int smallWorldSlots,
                          int unmappedSystemSlots, String cellId, String how, String activationNote) {
        return SUCCESS_PREFIX + systemName + " (" + starClass.toLowerCase(Locale.ROOT) + ", " + systemPlanetSlots
                + " system planet slots; up to " + smallWorldSlots + " small-world slots mapped"
                + (unmappedSystemSlots == 0 ? "" : "; " + unmappedSystemSlots + " additional slots not mapped yet")
                + ", cell " + cellId + "; " + how + ") was added to the chart" + activationNote + ".";
    }

    static ChatFormatting successColor() { return ChatFormatting.GREEN; }
}
