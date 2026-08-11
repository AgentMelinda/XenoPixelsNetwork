package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

/**
 * Common-safe hooks for opening client screens.
 * Dedicated servers must never load {@code net.minecraft.client.*}.
 */
public final class ClientScreens {
    public static Runnable openTargetTool = () -> {
    };

    public static Consumer<GuidanceOpenData> openGuidance = data -> {
    };

    public static Consumer<BallisticFlightPlan.Result> receiveFlightPlan = result -> {
    };

    /** Authoritative Aero controller state pushed by the server; the GUI renders only this. */
    public static Consumer<AeroStateSnapshot> receiveAeroState = state -> {
    };

    private ClientScreens() {
    }

    public record GuidanceOpenData(
            BlockPos computerPos,
            int x, int y, int z,
            String status,
            int pairedThrusters,
            int speedLevel,
            int apexY,
            int cruiseY,
            int fleetChannel,
            int salvoIntervalTicks,
            double gravitySi,
            double dragCoefficient,
            BlockPos missileBase,
            BlockPos missileCenter,
            BlockPos missileNose,
            double guidanceStopDistance
    ) {
        public GuidanceOpenData(BlockPos computerPos, int x, int y, int z, String status) {
            this(computerPos, x, y, z, status, 0, 5, 0, 0, 0, 10, 9.80665, 0.00002,
                    computerPos.below(), computerPos, computerPos.above(), 0.0);
        }

        public GuidanceOpenData(BlockPos computerPos, int x, int y, int z, String status,
                                int pairedThrusters, int speedLevel) {
            this(computerPos, x, y, z, status, pairedThrusters, speedLevel, 0, 0, 0, 10, 9.80665, 0.00002,
                    computerPos.below(), computerPos, computerPos.above(), 0.0);
        }

        public GuidanceOpenData(BlockPos computerPos, int x, int y, int z, String status,
                                int pairedThrusters, int speedLevel, int apexY) {
            this(computerPos, x, y, z, status, pairedThrusters, speedLevel, apexY, 0, 0, 10, 9.80665, 0.00002,
                    computerPos.below(), computerPos, computerPos.above(), 0.0);
        }

        public GuidanceOpenData(BlockPos computerPos, int x, int y, int z, String status,
                                int pairedThrusters, int speedLevel, int apexY, int cruiseY) {
            this(computerPos, x, y, z, status, pairedThrusters, speedLevel, apexY, cruiseY, 0, 10, 9.80665, 0.00002,
                    computerPos.below(), computerPos, computerPos.above(), 0.0);
        }

        public GuidanceOpenData(BlockPos computerPos, int x, int y, int z, String status,
                                int pairedThrusters, int speedLevel, int apexY, int cruiseY,
                                int fleetChannel, int salvoIntervalTicks) {
            this(computerPos, x, y, z, status, pairedThrusters, speedLevel, apexY, cruiseY,
                    fleetChannel, salvoIntervalTicks, 9.80665, 0.00002,
                    computerPos.below(), computerPos, computerPos.above(), 0.0);
        }
    }
}
