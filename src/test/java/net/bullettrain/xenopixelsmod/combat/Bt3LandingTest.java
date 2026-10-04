package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 2026-09-30 owner: fix at the source that NPC vanish, chase and kick-launch landed through
 * Bt3CombatPacket - a network packet class, which the XenoNPCs release ships only as a stub, so there
 * the moves quietly did nothing. The geometry now lives in Bt3Landing; the packet keeps its old
 * methods as forwards for the player code.
 */
class Bt3LandingTest {

    @Test
    void theLandingMathIsUnchanged() {
        for (int side = -1; side <= 1; side++) {
            Vec3 a = Bt3Landing.vanishPoint(0, 0, 3, 64, 4, 37.0f, side, 1.5, 1.0, 2.0);
            Vec3 b = Bt3CombatPacket.vanishPoint(0, 0, 3, 64, 4, 37.0f, side, 1.5, 1.0, 2.0);
            assertEquals(b, a, "vanish side " + side);
        }
        for (int bias = -1; bias <= 1; bias++) {
            assertEquals(Bt3CombatPacket.kickHitRange(0.6f, bias), Bt3Landing.kickHitRange(0.6f, bias), 1e-12);
            assertEquals(Bt3CombatPacket.kickTargetLaunch(new Vec3(1, 0, 0), 0.6f, bias),
                    Bt3Landing.kickTargetLaunch(new Vec3(1, 0, 0), 0.6f, bias));
        }
    }

    @Test
    void npcMovesNoLongerLandThroughTheNetworkPacket() throws Exception {
        for (String f : new String[]{"NpcCombatMoves.java", "NpcChargeMoves.java"}) {
            String src = Files.readString(RepoRoot.of("src", "main", "java", "net", "bullettrain",
                    "xenopixelsmod", "compat", "npc", f));
            String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
            assertFalse(code.contains("Bt3CombatPacket."), f + " still calls the packet class");
        }
    }
}
