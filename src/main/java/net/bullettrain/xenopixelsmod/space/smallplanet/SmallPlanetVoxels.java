package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure, seed-stable solid geology for a generated small planet. This is a voxel source for the Sable plot builder;
 * it creates no world state itself. Liquids and gravity-affected sand are intentionally avoided so a newly assembled
 * body stays stable while Sable initializes its collision and mass data.
 */
public final class SmallPlanetVoxels implements PlotBodyBuilder.Voxels {
    private static final BlockState[] ROCK_CRUST = {
            Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState()};
    private static final BlockState[] ROCK_ORES = {
            Blocks.COAL_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
            Blocks.GOLD_ORE.defaultBlockState(), Blocks.REDSTONE_ORE.defaultBlockState(), Blocks.LAPIS_ORE.defaultBlockState(),
            Blocks.DIAMOND_ORE.defaultBlockState()};
    private static final BlockState[] DEEP_ORES = {
            Blocks.DEEPSLATE_COAL_ORE.defaultBlockState(), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState(), Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState(), Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()};

    private final SmallPlanetField.Candidate body;
    private final int radius;

    public SmallPlanetVoxels(SmallPlanetField.Candidate body) {
        this.body = java.util.Objects.requireNonNull(body, "body");
        this.radius = body.radius();
    }

    public int extent() { return radius; }
    public PlotBodyBuilder.Voxels voxels() { return this; }

    @Override public BlockState at(int x, int y, int z) {
        int distanceSquared = x * x + y * y + z * z;
        int radiusSquared = radius * radius;
        if (distanceSquared > radiusSquared) return null;
        double distance = Math.sqrt(distanceSquared);
        double depth = radius - distance;

        if (depth > 2.0 && isOre(x, y, z, body.seed(), body.oreRichness(), distance, radius))
            return ore(x, y, z, distance, radius);
        if (distance < radius * 0.28) return Blocks.DEEPSLATE.defaultBlockState();
        if (depth <= 2.0) return crust(x, y, z, body.profile());
        return mantle(body.profile());
    }

    private static BlockState crust(int x, int y, int z, SmallPlanetField.Profile profile) {
        long noise = coordinateHash(x, y, z, profile.ordinal() * 0x9E3779B97F4A7C15L);
        return switch (profile) {
            case ROCK -> ROCK_CRUST[(int) Long.remainderUnsigned(noise, ROCK_CRUST.length)];
            case ICE -> (noise & 7) == 0 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
            case DESERT -> (noise & 3) == 0 ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
            case JUNGLE -> (noise & 3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
            case LAVA -> (noise & 3) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BASALT.defaultBlockState();
            case LUNAR -> (noise & 3) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
        };
    }

    private static BlockState mantle(SmallPlanetField.Profile profile) {
        return switch (profile) {
            case ROCK -> Blocks.STONE.defaultBlockState();
            case ICE -> Blocks.BLUE_ICE.defaultBlockState();
            case DESERT -> Blocks.SANDSTONE.defaultBlockState();
            case JUNGLE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case LAVA -> Blocks.BLACKSTONE.defaultBlockState();
            case LUNAR -> Blocks.DEEPSLATE.defaultBlockState();
        };
    }

    private static boolean isOre(int x, int y, int z, long seed, double richness, double distance, int radius) {
        if (richness <= 0 || distance < radius * 0.38) return false;
        // At richness 1 the deterministic interior chance is 4%; the outer crust and central core are protected.
        long hash = coordinateHash(x, y, z, seed ^ 0xD1B54A32D192ED03L);
        double roll = (hash >>> 11) * 0x1.0p-53;
        return roll < richness * 0.04;
    }

    private static BlockState ore(int x, int y, int z, double distance, int radius) {
        long hash = coordinateHash(x, y, z, 0x94D049BB133111EBL);
        BlockState[] table = distance < radius * 0.55 ? DEEP_ORES : ROCK_ORES;
        int index = (int) Long.remainderUnsigned(hash, table.length);
        return table[index];
    }

    private static long coordinateHash(int x, int y, int z, long salt) {
        long value = salt ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) y * 0xC2B2AE3D27D4EB4FL ^ (long) z * 0x165667B19E3779F9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
