package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-29 owner: "a per radius where player looking at a multitarget hakai without the need to
 * lock on the target", "destroy big buildings and blocks", and Sable ship blocks "slowly fading
 * block by block". The old single-target Hakai stays behind {@code hakaiMode single}.
 */
class HakaiAreaRulesTest {
    private final XenoServerConfig.Data saved = XenoServerConfig.snapshot();

    @AfterEach
    void restore() {
        XenoServerConfig.apply(saved);
    }

    @Test
    void theOldSingleTargetHakaiStaysTheDefault() {
        assertEquals(HakaiAreaRules.Mode.SINGLE, HakaiAreaRules.parseMode(null));
        assertEquals(HakaiAreaRules.Mode.SINGLE, HakaiAreaRules.parseMode("nonsense"));
        assertEquals(HakaiAreaRules.Mode.AREA, HakaiAreaRules.parseMode(" Area "));
        assertEquals("single", new XenoServerConfig.Data().hakaiMode);
    }

    @Test
    void theSphereHoldsWhatIsInsideIt() {
        Vec3 c = new Vec3(0.5, 64.5, 0.5);
        assertTrue(HakaiAreaRules.inSphere(c, new Vec3(3.5, 64.5, 0.5), 3.0));
        assertFalse(HakaiAreaRules.inSphere(c, new Vec3(3.6, 64.5, 0.5), 3.0));
    }

    @Test
    void blocksGoFromTheTopDown() {
        List<BlockPos> order = HakaiAreaRules.sphereTopDown(new Vec3(0.5, 64.5, 0.5), 2.0, 10_000);
        assertFalse(order.isEmpty());
        for (int i = 1; i < order.size(); i++) {
            assertTrue(order.get(i - 1).getY() >= order.get(i).getY(), "top down at " + i);
        }
        assertEquals(66, order.get(0).getY());
        assertTrue(order.contains(new BlockPos(0, 64, 0)));
        assertFalse(order.contains(new BlockPos(3, 64, 0)), "outside the radius");
    }

    @Test
    void aHugeSphereStopsAtTheLimit() {
        assertEquals(100, HakaiAreaRules.sphereTopDown(Vec3.ZERO, 20.0, 100).size());
        assertEquals(0, HakaiAreaRules.sphereTopDown(Vec3.ZERO, 20.0, 0).size());
    }

    @Test
    void eachBlockCracksThroughEveryStage() {
        assertEquals(0, HakaiAreaRules.crackStage(0, 20));
        assertEquals(9, HakaiAreaRules.crackStage(20, 20));
        assertEquals(9, HakaiAreaRules.crackStage(99, 20));
        assertEquals(4, HakaiAreaRules.crackStage(10, 20));
        assertEquals(9, HakaiAreaRules.crackStage(0, 0), "no fade: straight to the last stage");
    }

    /** 2026-09-29 owner: "all blocks should be destructable beside bedrock". */
    @Test
    void everythingButAirAndBedrockCanBeErased() {
        assertFalse(HakaiAreaRules.erasable(0.0f, true, false, true), "air");
        assertFalse(HakaiAreaRules.erasable(-1.0f, false, true, true), "bedrock");
        assertTrue(HakaiAreaRules.erasable(-1.0f, false, false, true), "barriers, command blocks too");
        assertTrue(HakaiAreaRules.erasable(50.0f, false, false, true), "obsidian is not safe from a god");
        assertTrue(HakaiAreaRules.erasable(0.0f, false, false, true), "grass, flowers");
        assertTrue(new XenoServerConfig.Data().hakaiBlocksUnbreakable);
    }

    @Test
    void theOldRuleStillSparesEveryUnbreakableBlock() {
        assertFalse(HakaiAreaRules.erasable(-1.0f, false, false, false), "hakaiBlocksUnbreakable false");
        assertTrue(HakaiAreaRules.erasable(50.0f, false, false, false));
    }

    /** 2026-09-29 owner: "and the hakai fx radius or scale?" - the effect covers the sphere. */
    @Test
    void theAreaEffectIsSizedToTheSphere() {
        float r5 = net.bullettrain.xenopixelsmod.combat.fx.HakaiEffectRules.areaScale(5.0, 1.0f);
        float r10 = net.bullettrain.xenopixelsmod.combat.fx.HakaiEffectRules.areaScale(10.0, 1.0f);
        assertEquals(10.0f / 1.8f, r5, 1e-4, "the sphere's diameter, in authored body heights");
        assertEquals(2.0f * r5, r10, 1e-4);
        assertEquals(r5 * 0.5f,
                net.bullettrain.xenopixelsmod.combat.fx.HakaiEffectRules.areaScale(5.0, 0.5f), 1e-4);
        assertEquals(1.0f, new XenoServerConfig.Data().hakaiAreaFxScale, 1e-6);
        assertEquals(1000.0f / 1.8f,
                net.bullettrain.xenopixelsmod.combat.fx.HakaiEffectRules.areaScale(500.0, 1.0f), 1e-2,
                "a radius-500 Hakai's veil covers radius 500");
        assertEquals(HakaiBlockErasure.VEIL_INTERVAL, 10, "the veil re-plays while blocks are erased");
    }

    /** 2026-09-29 owner: "and not to destroy structures of dmz please". */
    @Test
    void dragonMineZStructuresAreSpared() {
        assertTrue(HakaiAreaRules.sparesStructure(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dragonminez", "kamilookout"), true));
        assertFalse(HakaiAreaRules.sparesStructure(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("village_plains"), true),
                "a vanilla village is fair game");
        assertFalse(HakaiAreaRules.sparesStructure(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dragonminez", "goku_house"), false),
                "hakaiSpareDmzStructures false");
        assertTrue(new XenoServerConfig.Data().hakaiSpareDmzStructures);
    }

    /**
     * 2026-09-29 owner: "i need a way to destroy building to their floor". The raze shape clears
     * every column around the look point from the roof down to the ground and keeps the ground.
     */
    @Test
    void razeClearsABuildingDownToTheGroundAndKeepsTheGround() {
        // Ground is y <= 63; a 3-tall house (y 64-66) with a gap (air) at y 65 in column 0,0.
        java.util.function.Predicate<BlockPos> ground = p -> p.getY() <= 63;
        java.util.function.Predicate<BlockPos> air = p -> p.getY() > 66
                || (p.getX() == 0 && p.getZ() == 0 && p.getY() == 65);
        List<BlockPos> order = HakaiAreaRules.razeColumns(new Vec3(0.5, 65.0, 0.5), 1.0, 80, 0,
                ground, air, 10_000);
        assertTrue(order.contains(new BlockPos(0, 66, 0)));
        assertTrue(order.contains(new BlockPos(0, 64, 0)), "down to the floor");
        assertFalse(order.contains(new BlockPos(0, 65, 0)), "air is skipped");
        assertFalse(order.contains(new BlockPos(0, 63, 0)), "the ground stays");
        assertTrue(order.contains(new BlockPos(1, 64, 0)), "neighbouring columns too");
        assertFalse(order.contains(new BlockPos(2, 64, 0)), "outside the radius");
        for (int i = 1; i < order.size(); i++) {
            assertTrue(order.get(i - 1).getY() >= order.get(i).getY(), "roof first");
        }
        assertEquals(HakaiAreaRules.Shape.SPHERE, HakaiAreaRules.parseShape("x"));
        assertEquals(HakaiAreaRules.Shape.RAZE, HakaiAreaRules.parseShape("Raze"));
        assertEquals("sphere", new XenoServerConfig.Data().hakaiBlockShape);
        assertEquals(48, new XenoServerConfig.Data().hakaiRazeHeight);
    }

    @Test
    void razeStopsAtTheLimit() {
        assertEquals(7, HakaiAreaRules.razeColumns(new Vec3(0.5, 65, 0.5), 3.0, 80, 0,
                p -> p.getY() <= 0, p -> false, 7).size());
    }

    /**
     * 2026-09-29 owner: "i cant hakai blocks on crosshair" - chat said "no blocks to erase (4096
     * found...)". The limit was taken before air was dropped, so a big sphere spent all 4096 on the
     * empty sky above the building (top down) and had nothing real left.
     */
    @Test
    void theLimitCountsRealBlocksNotTheAirAboveThem() {
        List<BlockPos> sphere = HakaiAreaRules.sphereTopDown(new Vec3(0.5, 64.5, 0.5), 20.0, Integer.MAX_VALUE);
        List<BlockPos> real = HakaiAreaRules.takeErasable(sphere, p -> p.getY() <= 64, 4096);
        assertEquals(4096, real.size(), "the budget goes on blocks that exist");
        assertTrue(real.stream().allMatch(p -> p.getY() <= 64));
        assertEquals(64, real.get(0).getY(), "still roof first");
        assertEquals(0, HakaiAreaRules.takeErasable(sphere, p -> false, 4096).size());
    }

    /**
     * 2026-09-29 owner: "why can i destroy the otherworld???? king yemma place and palace". Those are
     * placed builds in DMZ's own dimensions, not worldgen structures, so the structure check never
     * saw them. Whole dimensions are spared instead.
     */
    @Test
    void dragonMineZsSacredDimensionsAreSpared() {
        String list = new XenoServerConfig.Data().hakaiSparedDimensions;
        assertTrue(HakaiAreaRules.sparedDimension("dragonminez:otherworld", list), "King Yemma's palace");
        assertTrue(HakaiAreaRules.sparedDimension("dragonminez:time_chamber", list));
        assertTrue(HakaiAreaRules.sparedDimension("dragonminez:sacredkaiplanet", list));
        assertFalse(HakaiAreaRules.sparedDimension("dragonminez:namek", list), "Namek is open land");
        assertFalse(HakaiAreaRules.sparedDimension("minecraft:overworld", list));
        assertTrue(HakaiAreaRules.sparedDimension("minecraft:the_end", " minecraft:the_end , x:y"),
                "any list, spaces ignored");
        assertFalse(HakaiAreaRules.sparedDimension("dragonminez:otherworld", ""), "an empty list spares none");
    }

    /** 2026-09-29 owner: "no othercloud erase" - the Otherworld cloud is never erased, anywhere. */
    @Test
    void theOtherworldCloudIsNeverErased() {
        String list = new XenoServerConfig.Data().hakaiSparedBlocks;
        assertTrue(HakaiAreaRules.listed("dragonminez:otherworld_cloud", list));
        assertFalse(HakaiAreaRules.listed("minecraft:stone", list));
        assertTrue(HakaiAreaRules.listed("minecraft:diamond_block", "minecraft:diamond_block"),
                "any block id can be added");
    }

    @Test
    void theTargetCapKeepsTheNearest() {
        List<Double> near = HakaiAreaRules.capTargets(List.of(5.0, 1.0, 9.0, 2.0), d -> d, 2);
        assertEquals(List.of(1.0, 2.0), near);
    }

    @Test
    void theSettingsHaveSafeDefaultsAndClamps() {
        XenoServerConfig.Data d = new XenoServerConfig.Data();
        assertEquals(5.0, d.hakaiAreaRadius, 1e-9);
        assertEquals(16, d.hakaiAreaMaxTargets);
        assertTrue(d.hakaiBlocks);
        assertTrue(d.hakaiShips);
        assertEquals(4096, d.hakaiBlockLimit);
        assertEquals(48, d.hakaiBlocksPerTick);
        assertEquals(20, d.hakaiBlockFadeTicks);

        d.hakaiAreaRadius = 999;
        d.hakaiAreaMaxTargets = 0;
        d.hakaiBlockLimit = -5;
        d.hakaiBlocksPerTick = 9999;
        d.hakaiBlockFadeTicks = 500;
        d.hakaiMode = "area";
        XenoServerConfig.apply(d);
        assertEquals(999.0, XenoServerConfig.hakaiAreaRadius, 1e-9, "the 32 cap is gone (owner, 2026-09-29)");
        assertEquals(1, XenoServerConfig.hakaiAreaMaxTargets);
        assertEquals(0, XenoServerConfig.hakaiBlockLimit);
        assertEquals(512, XenoServerConfig.hakaiBlocksPerTick);
        assertEquals(100, XenoServerConfig.hakaiBlockFadeTicks);
        assertEquals("area", XenoServerConfig.snapshot().hakaiMode);
    }
}
