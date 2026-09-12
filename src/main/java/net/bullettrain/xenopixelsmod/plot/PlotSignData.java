package net.bullettrain.xenopixelsmod.plot;

/**
 * A parsed, validated plot listing written on a sign.
 *
 * <p>Bounds are stored normalized ({@code min <= max}) so the rendered size does not depend on
 * which corner was typed first. Y is absent for the same reason it is absent from
 * {@link PlotArea}: a plot is a 2-D column extent.</p>
 *
 * <p>{@code price} is the decimal exactly as typed. MMO Econ owns the unit scale through
 * {@code Money.fromDouble(double)}, so the syntax layer never assumes it.</p>
 *
 * @param minX  lower X bound, inclusive
 * @param minZ  lower Z bound, inclusive
 * @param maxX  upper X bound, inclusive
 * @param maxZ  upper Z bound, inclusive
 * @param price asking price, always &gt;= 0
 */
public record PlotSignData(int minX, int minZ, int maxX, int maxZ, double price) {

    public PlotSignData {
        if (minX > maxX) {
            int swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minZ > maxZ) {
            int swap = minZ;
            minZ = maxZ;
            maxZ = swap;
        }
        if (!(price >= 0.0) || Double.isInfinite(price)) {
            throw new IllegalArgumentException("price must be a finite value >= 0: " + price);
        }
    }

    /** Blocks wide on the X axis. */
    public int width() {
        return maxX - minX + 1;
    }

    /** Blocks long on the Z axis. */
    public int length() {
        return maxZ - minZ + 1;
    }
}