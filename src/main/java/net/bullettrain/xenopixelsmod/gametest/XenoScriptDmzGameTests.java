package net.bullettrain.xenopixelsmod.gametest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;

/**
 * 2026-09-28 owner report: DMZ properties set from a script "don't change". These run the script
 * calls on a real native NPC in a real server level and check the values hold 40 ticks later.
 * Run with ./gradlew runGameTestServer -PofflineMcMeta.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class XenoScriptDmzGameTests {
    private XenoScriptDmzGameTests() {}

    private static XenoNpcEntity spawn(GameTestHelper helper) {
        XenoNpcEntity npc = helper.spawn(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 1, 1));
        npc.setNoAi(true);
        return npc;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void scriptStatAndAuraEditsHold(GameTestHelper helper) {
        XenoNpcEntity npc = spawn(helper);
        ScriptNpc script = new ScriptNpc(npc, new HashMap<>(), new ScriptTimers());
        float healthBefore = npc.getMaxHealth();
        helper.assertTrue(NativeXenoScriptApi.INSTANCE.setStat(script, "vitality", 400), "setStat accepted");
        helper.assertTrue(NativeXenoScriptApi.INSTANCE.setAura(script, true), "setAura accepted");
        helper.runAfterDelay(40, () -> {
            NpcCombatProfile profile = NpcCombatProfile.read(npc);
            helper.assertTrue(profile.vitality == 400, "vitality kept: " + profile.vitality);
            helper.assertTrue(profile.auraOn, "aura kept on");
            helper.assertTrue(npc.getMaxHealth() > healthBefore,
                    "max health follows vitality: before " + healthBefore + ", after " + npc.getMaxHealth());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void scriptNpcprofileCommandHolds(GameTestHelper helper) {
        XenoNpcEntity npc = spawn(helper);
        ScriptNpc script = new ScriptNpc(npc, new HashMap<>(), new ScriptTimers());
        script.executeCommand("xenopixels npcprofile charge 250");
        helper.runAfterDelay(40, () -> {
            int charge = NpcCombatProfile.read(npc).kiChargePercent;
            helper.assertTrue(charge == 250, "npcprofile charge applied from a script: " + charge);
            helper.succeed();
        });
    }
}
