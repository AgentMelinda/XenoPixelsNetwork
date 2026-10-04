package net.bullettrain.xenopixelsmod.client.hud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidanceV2ArtLayoutTest {

    private static final Pattern REGION = Pattern.compile(
            "\"([a-z_]+)\":\\s*\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\)");

    private static Path repoRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && cursor != null; i++) {
            if (Files.isRegularFile(cursor.resolve("tools/guidance_v2_art/atlas.py"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("repo root not found from " + Path.of("").toAbsolutePath());
    }

    @Test
    void javaRegionsMatchPythonAtlas() throws Exception {
        Path atlasPy = repoRoot().resolve("tools/guidance_v2_art/atlas.py");
        String text = Files.readString(atlasPy, StandardCharsets.UTF_8);
        Map<String, int[]> python = new HashMap<>();
        Matcher matcher = REGION.matcher(text);
        while (matcher.find()) {
            python.put(matcher.group(1), new int[] {
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5))
            });
        }
        assertRegion("panel", GuidanceV2ArtLayout.PANEL, python);
        assertRegion("tab", GuidanceV2ArtLayout.TAB, python);
        assertRegion("tab_hot", GuidanceV2ArtLayout.TAB_HOT, python);
        assertRegion("bar_empty", GuidanceV2ArtLayout.BAR_EMPTY, python);
        assertRegion("bar_full", GuidanceV2ArtLayout.BAR_FULL, python);
        assertRegion("bar_flap", GuidanceV2ArtLayout.BAR_FLAP, python);
        assertRegion("mode_chip", GuidanceV2ArtLayout.MODE_CHIP, python);
        assertRegion("warn", GuidanceV2ArtLayout.WARN, python);
        assertRegion("edge", GuidanceV2ArtLayout.EDGE, python);
        assertEquals(1024, GuidanceV2ArtLayout.ATLAS);
        assertTrue(text.contains("ATLAS = 1024"));
    }

    @Test
    void generatedDestsAreForkSizedAndNamed() throws Exception {
        Path root = repoRoot();
        Path block = root.resolve("src/main/resources/assets/xenopixelsmod/textures/block");
        Path item = root.resolve("src/main/resources/assets/xenopixelsmod/textures/item");
        Path atlas = root.resolve("src/main/resources/assets/xenopixelsmod/textures/gui/guidance_v2_atlas.png");
        assertPng(atlas, 1024, 1024);
        assertTrue(atlas.getFileName().toString().startsWith("guidance_v2_"));

        List<String> blocks = List.of(
                "ship_vls_guidance_front_fork.png",
                "ship_vls_guidance_side_fork.png",
                "ship_vls_guidance_top_fork.png",
                "ship_vls_guidance_fork.png",
                "ship_thruster_front_fork.png",
                "ship_thruster_front_on_fork.png",
                "ship_thruster_back_fork.png",
                "ship_thruster_side_fork.png",
                "ship_thruster_fork.png",
                "ship_thruster_on_fork.png",
                "missile_tube_top_fork.png",
                "missile_tube_side_fork.png",
                "missile_tube_bottom_fork.png",
                "missile_tube_fork.png",
                "missile_chunk_loader_front_fork.png",
                "missile_chunk_loader_side_fork.png",
                "missile_chunk_loader_top_fork.png",
                "missile_chunk_loader_fork.png",
                "wing_panel_fork.png",
                "wing_flap_horizontal_fork.png",
                "wing_flap_vertical_fork.png",
                "copycat_wing_panel_fork.png",
                "copycat_wing_panel_lit_fork.png",
                "copycat_wing_flap_horizontal_fork.png",
                "copycat_wing_flap_horizontal_lit_fork.png",
                "copycat_wing_flap_vertical_fork.png",
                "copycat_wing_flap_vertical_lit_fork.png",
                "pilot_seat_frame_fork.png",
                "pilot_seat_cushion_fork.png");
        for (String name : blocks) {
            assertTrue(name.endsWith("_fork.png"), name);
            assertPng(block.resolve(name), 128, 128);
        }
        assertPng(block.resolve("flight_controller_fork.png"), 128, 128);
        assertPng(block.resolve("flight_controller_glowmask_fork.png"), 128, 128);
        assertTrue(block.resolve("flight_controller_fork.png").getFileName().toString().endsWith("_fork.png"));
        assertTrue(block.resolve("flight_controller_glowmask_fork.png").getFileName().toString().endsWith("_fork.png"));
        assertPng(item.resolve("target_tool_fork.png"), 128, 128);
        assertPng(item.resolve("panel_configurator_fork.png"), 128, 128);
    }

    @Test
    void pythonSelftestPasses() throws Exception {
        Path root = repoRoot();
        ProcessBuilder builder = new ProcessBuilder("python", "tools/gen_guidance_v2_art.py", "--check");
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertEquals(0, code, output);
        assertTrue(output.contains("selftest ok"), output);
    }

    private static void assertRegion(String name, GuidanceV2ArtLayout.Region region, Map<String, int[]> python) {
        int[] expected = python.get(name);
        assertTrue(expected != null, "python missing " + name);
        assertEquals(expected[0], region.u(), name + ".u");
        assertEquals(expected[1], region.v(), name + ".v");
        assertEquals(expected[2], region.w(), name + ".w");
        assertEquals(expected[3], region.h(), name + ".h");
    }

    private static void assertPng(Path path, int width, int height) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing " + path);
        byte[] data = Files.readAllBytes(path);
        assertTrue(data.length > 24, path + " too small");
        int w = ByteBuffer.wrap(data, 16, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        int h = ByteBuffer.wrap(data, 20, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        assertEquals(width, w, path.getFileName() + " width");
        assertEquals(height, h, path.getFileName() + " height");
    }
}
