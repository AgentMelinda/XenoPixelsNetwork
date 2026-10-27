package net.bullettrain.xenopixelsmod.space.dragonball;

import java.util.UUID;
import java.util.function.Function;

/**
 * Pure source-selection seam for the scanner while the legacy collectible loop and planetary SDBs coexist.
 * The caller must choose a mode explicitly; a planetary lookup never falls back to a legacy scatter coordinate.
 */
public final class DragonBallRadarTargetResolver {
    public enum Mode { LEGACY_SCATTER, PLANETARY_BODY }
    public enum Source { LEGACY_SCATTER, PLANETARY_BODY, NONE }
    public enum UnavailableReason {
        NONE,
        MODE_UNSPECIFIED,
        LEGACY_POSITION_UNAVAILABLE,
        PLANET_BODY_ID_UNAVAILABLE,
        PLANET_BODY_UNAVAILABLE,
        PLANET_BODY_POSITION_INVALID
    }

    /** Dimension-qualified world position. Non-finite coordinates and blank dimensions are rejected at resolution. */
    public record Position(String dimension, double x, double y, double z) {
        public boolean isValid() {
            return dimension != null && !dimension.isBlank()
                    && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }

    public record Resolution(Source source, Position position, UnavailableReason reason) {
        public Resolution {
            if (source == null || reason == null) throw new IllegalArgumentException("source and reason are required");
            if ((source == Source.NONE) != (position == null))
                throw new IllegalArgumentException("only an unavailable target may omit its position");
            if ((source == Source.NONE) != (reason != UnavailableReason.NONE))
                throw new IllegalArgumentException("available targets need NONE reason; unavailable targets need a reason");
        }

        static Resolution available(Source source, Position position) {
            return new Resolution(source, position, UnavailableReason.NONE);
        }

        static Resolution unavailable(UnavailableReason reason) {
            return new Resolution(Source.NONE, null, reason);
        }
    }

    private DragonBallRadarTargetResolver() {}

    /**
     * Resolve the scanner source. {@code bodyPositionLookup} is called only for planetary mode with a saved body UUID.
     * A missing UUID/body or invalid body position is surfaced as unavailable and cannot fall through to scatter data.
     */
    public static Resolution resolve(Mode mode, UUID savedPlanetBodyId, Position legacyPosition,
                                     Function<UUID, Position> bodyPositionLookup) {
        if (mode == null) return Resolution.unavailable(UnavailableReason.MODE_UNSPECIFIED);
        if (mode == Mode.LEGACY_SCATTER) {
            return legacyPosition != null && legacyPosition.isValid()
                    ? Resolution.available(Source.LEGACY_SCATTER, legacyPosition)
                    : Resolution.unavailable(UnavailableReason.LEGACY_POSITION_UNAVAILABLE);
        }
        if (savedPlanetBodyId == null)
            return Resolution.unavailable(UnavailableReason.PLANET_BODY_ID_UNAVAILABLE);
        if (bodyPositionLookup == null)
            return Resolution.unavailable(UnavailableReason.PLANET_BODY_UNAVAILABLE);
        Position bodyPosition = bodyPositionLookup.apply(savedPlanetBodyId);
        if (bodyPosition == null)
            return Resolution.unavailable(UnavailableReason.PLANET_BODY_UNAVAILABLE);
        if (!bodyPosition.isValid())
            return Resolution.unavailable(UnavailableReason.PLANET_BODY_POSITION_INVALID);
        return Resolution.available(Source.PLANETARY_BODY, bodyPosition);
    }
}
