package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.client.gui.FlightPlannerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only entry to open the Ballistic Guidance GUI. */
public final class ClientGuidance {
    private ClientGuidance() {
    }

    public static void open(BlockPos computerPos, int x, int y, int z,
                            String status, int pairedThrusters, int speedLevel,
                            int apexY, int cruiseY, int fleetChannel, int salvoIntervalTicks,
                            double gravitySi, double dragCoefficient,
                            BlockPos missileBase, BlockPos missileCenter, BlockPos missileNose,
                            double guidanceStopDistance) {
        Minecraft.getInstance().setScreen(new FlightPlannerScreen(new ClientScreens.GuidanceOpenData(
                computerPos, x, y, z, status, pairedThrusters, speedLevel, apexY, cruiseY,
                fleetChannel, salvoIntervalTicks, gravitySi, dragCoefficient,
                missileBase, missileCenter, missileNose, guidanceStopDistance)));
    }

    public static void open(BlockPos computerPos, int x, int y, int z,
                            String status, int pairedThrusters, int speedLevel,
                            int apexY, int cruiseY, int fleetChannel, int salvoIntervalTicks,
                            double gravitySi, double dragCoefficient) {
        open(computerPos, x, y, z, status, pairedThrusters, speedLevel, apexY, cruiseY,
                fleetChannel, salvoIntervalTicks, gravitySi, dragCoefficient,
                computerPos.below(), computerPos, computerPos.above(), 0.0);
    }

    public static void open(BlockPos computerPos, int x, int y, int z,
                            String status, int pairedThrusters, int speedLevel, int apexY) {
        open(computerPos, x, y, z, status, pairedThrusters, speedLevel, apexY, 0, 0, 10,
                9.80665, 0.00002);
    }

    public static void open(BlockPos computerPos, int x, int y, int z,
                            String status, int pairedThrusters, int speedLevel,
                            int apexY, int cruiseY) {
        open(computerPos, x, y, z, status, pairedThrusters, speedLevel, apexY, cruiseY, 0, 10,
                9.80665, 0.00002);
    }

    public static void open(BlockPos computerPos, int x, int y, int z,
                            String status, int pairedThrusters, int speedLevel,
                            int apexY, int cruiseY, int fleetChannel, int salvoIntervalTicks) {
        open(computerPos, x, y, z, status, pairedThrusters, speedLevel, apexY, cruiseY,
                fleetChannel, salvoIntervalTicks, 9.80665, 0.00002);
    }
}
