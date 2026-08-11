package net.bullettrain.xenopixelsmod.data;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Optional built-in datapacks shipped inside the mod jar.
 *
 * <p><b>All of these are registered with {@code alwaysActive = false}</b>, so they exist in
 * {@code /datapack list available} and do nothing at all until an operator enables one. That is
 * deliberate: these override {@code minecraft:overworld}'s dimension type, and silently changing
 * a world's geometry on mod update would corrupt every existing save.
 *
 * <h2>Why a datapack rather than a mixin</h2>
 *
 * <p>World height is not one number. {@code LevelHeightAccessor} exposes {@code getMinBuildHeight},
 * {@code getMaxBuildHeight}, {@code getHeight} and {@code getSectionsCount}, and chunk storage
 * derives heightmap bit width and section count from the latter two. Overriding only the build
 * limits — which is what {@code fuck-sable}'s {@code world-height-override} does — clamps where
 * blocks may be placed while leaving storage on the old geometry, so you get a world you cannot
 * build in <em>and</em> the original heightmap mismatch. A {@code dimension_type} override moves
 * all four together, because they are all read from it.
 *
 * <h2>Enabling one</h2>
 *
 * <pre>
 * /datapack enable "file/xeno_max_overworld"
 * </pre>
 *
 * <p>Or add it to {@code level.dat}'s enabled-pack list before first load. It must be enabled at
 * world creation.
 *
 * <p><b>Changing the height of an existing world does not migrate it.</b> Chunks already saved
 * carry heightmaps and section counts for the old geometry; loading them under new geometry
 * produces {@code Ignoring heightmap data ... size does not match} and an
 * {@code ArrayIndexOutOfBoundsException} in {@code ThreadedLevelLightEngine.initializeLight}.
 * Enable one of these on a <em>new</em> world, or to force an existing world back to the geometry
 * it was actually created with.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModDatapacks {

    /**
     * Vanilla 1.18+ geometry, y -64 to 320.
     *
     * <p>Use when another mod has raised the overworld and your existing chunks were saved at the
     * standard height — this forces it back so those chunks load.
     */
    private static final String STANDARD_OVERWORLD = "xeno_standard_overworld";

    /**
     * Minecraft's hard maximum, y -2032 to 2032.
     *
     * <p>That is the limit the format allows: {@code min_y >= -2032} and
     * {@code min_y + height <= 2032}. It is 254 sections per chunk against vanilla's 24, so
     * expect a real cost in chunk memory, lighting and save size, and note that Distant Horizons
     * has to cover all of it too.
     */
    private static final String MAX_OVERWORLD = "xeno_max_overworld";

    /**
     * Sable mass properties for XenoPixels ship blocks.
     *
     * <p>Without this the blocks run on Sable's defaults, which is how the mod shipped before
     * these were written. It is optional rather than always-on because changing a hull's mass
     * changes its physics: draft, inertia and — with Waterworks installed — the pressure its
     * compartments see, which is a documented route to a compartment-failure explosion. It
     * should be possible to A/B a ship with and without it without rebuilding the jar.
     */
    private static final String SHIP_MASSES = "xeno_ship_masses";

    private ModDatapacks() {
    }

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        register(event, STANDARD_OVERWORLD, "XenoPixels: standard overworld (-64 to 320)");
        register(event, MAX_OVERWORLD, "XenoPixels: maximum overworld (-2032 to 2032)");
        register(event, SHIP_MASSES, "XenoPixels: ship block masses (Sable)");
    }

    private static void register(AddPackFindersEvent event, String folder, String displayName) {
        try {
            event.addPackFinders(
                    ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "datapacks/" + folder),
                    PackType.SERVER_DATA,
                    Component.literal(displayName),
                    PackSource.BUILT_IN,
                    // Optional: present in the pack list, off until explicitly enabled.
                    false,
                    Pack.Position.TOP);
        } catch (Throwable t) {
            // A missing or malformed built-in pack must never stop the mod loading.
            XenoPixelsMod.LOGGER.warn("Could not register built-in datapack {}", folder, t);
        }
    }
}
