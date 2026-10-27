package net.bullettrain.xenopixelsmod.gametest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.compat.npc.NpcSprintSkill;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live checks for imported movement data, separate from DMZ's stat authority/fingerprint. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcImportedMovementGameTests {
    private NpcImportedMovementGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void importedBaseSurvivesFingerprintGateAndRetainsFormModifier(GameTestHelper helper) {
        var npc = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(2, 3, 2));
        var profile = new NpcCombatProfile();
        profile.importedMoveSpeed = 7;
        profile.write(npc);
        NpcCounterpartSync.apply(npc, profile, false);
        var attribute = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        var modifier = new AttributeModifier(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "import_movement_probe"),
                0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attribute.addTransientModifier(modifier);
        attribute.setBaseValue(0.26);
        // Same profile fingerprint as the first call: movement must still reapply.
        NpcCounterpartSync.apply(npc, profile, false);
        double expected = (double) (7 / 20.0f);
        helper.assertTrue(attribute.getBaseValue() == expected, "source speed /20 must become the real base attribute");
        helper.assertTrue(attribute.getModifier(modifier.id()) != null, "form modifier remains attached");
        helper.assertTrue(Math.abs(attribute.getValue() - expected * 1.5) < 1e-8, "form still multiplies imported base speed");
        profile.authoritative = false;
        attribute.setBaseValue(0.26);
        NpcCounterpartSync.apply(npc, profile, false);
        helper.assertTrue(attribute.getBaseValue() == expected, "AI speed survives disabled DMZ authority too");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void importedSprintPermissionRemovesAnExistingSprintBoost(GameTestHelper helper) {
        var npc = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(2, 3, 2));
        var profile = new NpcCombatProfile();
        profile.skills.setLevel("sprint", 2);
        profile.skills.setActive("sprint", true);
        NpcSprintSkill.apply(npc, profile, true);
        helper.assertTrue(npc.isSprinting(), "preexisting profiles may sprint");
        double boosted = npc.getAttributeValue(Attributes.MOVEMENT_SPEED);
        profile.aiCanSprint = false;
        NpcSprintSkill.apply(npc, profile, true);
        helper.assertTrue(!npc.isSprinting(), "source CanSprint=false prevents sprinting");
        helper.assertTrue(npc.getAttributeValue(Attributes.MOVEMENT_SPEED) < boosted, "old sprint modifier is removed");
        helper.succeed();
    }
}
