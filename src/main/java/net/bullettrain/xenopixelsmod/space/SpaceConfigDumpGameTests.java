package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.command.SpaceConfigCommands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Headless server proof for the effective-config command registration and live chart value. */
@GameTestHolder("xenopixels_space_config")
@PrefixGameTestTemplate(false)
public final class SpaceConfigDumpGameTests {
    private SpaceConfigDumpGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "space_config_dump")
    public static void registeredCommandDumpsTheLiveEarthExit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var root = dispatcher.getRoot().getChild("xenospace");
        helper.assertTrue(root != null, "the xenospace root command is registered");
        var dump = root.getChild("config") == null ? null : root.getChild("config").getChild("dump");
        helper.assertTrue(dump != null, "/xenospace config dump is registered on the server");

        var chart = SpaceChartService.state(server).runtime.chart;
        int earthSlot = chart.slotOf("xenopixelsmod:earth");
        helper.assertTrue(earthSlot >= 0, "Earth is present in the live space chart");
        String output = SpaceConfigCommands.dump(server, "xenopixelsmod:earth");
        helper.assertTrue(output != null, "the command resolver returns Earth's effective config");
        String exitLine = output.lines().filter(line -> line.stripLeading().startsWith("exit_altitude = "))
                .findFirst().orElse("");
        helper.assertTrue(!exitLine.isEmpty(), "the dump includes exit_altitude");
        String value = exitLine.substring(exitLine.indexOf('=') + 1, exitLine.indexOf("  [")).trim();
        helper.assertTrue(Math.abs(Double.parseDouble(value) - chart.exitAltitude(earthSlot)) < 1.0E-9,
                "the reported exit altitude matches the live chart value");
        helper.succeed();
    }
}
