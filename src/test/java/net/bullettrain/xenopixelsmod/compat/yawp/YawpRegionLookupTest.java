package net.bullettrain.xenopixelsmod.compat.yawp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YawpRegionLookupTest {
    private static final ResourceKey<Level> OTHERWORLD = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.parse("dragonminez:otherworld"));
    private static final BlockPos TARGET = new BlockPos(20, 40, 60);

    @Test
    void forwardsOtherworldAndTargetToNativeResolverAndKeepsDimensionalResult() throws Exception {
        assertEquals(Optional.of("dragonminez:otherworld"),
                YawpRegionLookup.regionNameAt(finder("dimensional"), OTHERWORLD, TARGET));
    }

    @Test
    void preservesGlobalFallbackAndLocalRegionNames() throws Exception {
        assertEquals(Optional.of("global"),
                YawpRegionLookup.regionNameAt(finder("global"), OTHERWORLD, TARGET));
        assertEquals(Optional.of("palace"),
                YawpRegionLookup.regionNameAt(finder("local"), OTHERWORLD, TARGET));
    }

    @Test
    void noActiveRegionOrBlankNameIsNotAnExplicitKiRegion() throws Exception {
        assertTrue(YawpRegionLookup.regionNameAt(finder("none"), OTHERWORLD, TARGET).isEmpty());
        assertTrue(YawpRegionLookup.regionNameAt(finder("blank"), OTHERWORLD, TARGET).isEmpty());
    }

    @Test
    void unsetLocalDimensionDoesNotReadFlagsFromOverworld() {
        KiRegionFlags.set("minecraft:overworld", "palace", KiRegionFlags.Target.PLAYERS, KiRegionFlags.State.DENIED);
        try {
            assertEquals(KiRegionFlags.State.DEFAULT,
                    KiRegionFlags.get("dragonminez:otherworld", "palace", KiRegionFlags.Target.PLAYERS));
            KiRegionFlags.set("dragonminez:otherworld", "dragonminez:otherworld",
                    KiRegionFlags.Target.PLAYERS, KiRegionFlags.State.DENIED);
            assertEquals(KiRegionFlags.State.DENIED, KiRegionFlags.get("dragonminez:otherworld",
                    "dragonminez:otherworld", KiRegionFlags.Target.PLAYERS));
        } finally {
            KiRegionFlags.set("minecraft:overworld", "palace", KiRegionFlags.Target.PLAYERS, KiRegionFlags.State.DEFAULT);
            KiRegionFlags.set("dragonminez:otherworld", "dragonminez:otherworld",
                    KiRegionFlags.Target.PLAYERS, KiRegionFlags.State.DEFAULT);
        }
    }

    private static Method finder(String name) throws Exception {
        return Resolver.class.getMethod(name, BlockPos.class, ResourceKey.class);
    }

    public record Region(String name) {
        public String getName() { return name; }
    }

    /** Stand-in for the native resolver result; no local-region API is involved. */
    public static final class Resolver {
        public static Region dimensional(BlockPos pos, ResourceKey<Level> dimension) {
            assertEquals(TARGET, pos);
            assertEquals(OTHERWORLD, dimension);
            return new Region(dimension.location().toString());
        }
        public static Region global(BlockPos pos, ResourceKey<Level> dimension) { return new Region("global"); }
        public static Region local(BlockPos pos, ResourceKey<Level> dimension) { return new Region("palace"); }
        public static Region none(BlockPos pos, ResourceKey<Level> dimension) { return null; }
        public static Region blank(BlockPos pos, ResourceKey<Level> dimension) { return new Region(" "); }
    }
}
