package net.bullettrain.xenopixelsmod.gametest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.FreezeOwnership;
import net.bullettrain.xenopixelsmod.combat.FreezeOwnership.Reason;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The NPC-stays-frozen-after-a-rush bug, against a real entity in a real server level.
 *
 * <p>{@code FreezeOwnershipTest} covers the lease arithmetic with no Minecraft bootstrap, which is
 * the right place for it, but it cannot see the two facts the whole fix rests on: that vanilla
 * persists {@code NoAI} into the entity's save data, and that our repair stamp survives the same
 * round trip. Those are asserted here by actually saving and reloading an NPC.
 *
 * <p>Run with {@code ./gradlew runGameTestServer -PofflineMcMeta}.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FreezeOwnershipGameTests {
    private FreezeOwnershipGameTests() {}

    private static XenoNpcEntity spawn(GameTestHelper helper) {
        return helper.spawn(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 1, 1));
    }

    /** The normal path: a rush freezes its victim, and finishing the rush wakes it again. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void freezeIsReleasedWhenTheTechniqueEnds(GameTestHelper helper) {
        XenoNpcEntity npc = spawn(helper);
        helper.assertFalse(npc.isNoAi(), "a freshly spawned NPC starts awake");

        FreezeOwnership.acquire(npc, Reason.TECHNIQUE);
        helper.assertTrue(npc.isNoAi(), "acquiring the freeze sets NoAI");
        helper.assertTrue(FreezeOwnership.isFrozen(npc.getUUID()), "the hold is tracked");

        FreezeOwnership.release(npc, Reason.TECHNIQUE);
        helper.assertFalse(npc.isNoAi(), "releasing the freeze wakes the NPC — this is the bug");
        helper.assertFalse(FreezeOwnership.isFrozen(npc.getUUID()), "tracking is dropped on release");
        helper.succeed();
    }

    /**
     * The failure that made this permanent rather than transient: an owner that stops running — a
     * chunk unload mid-cast, a crash, or a cast torn down out of order — used to leave {@code NoAI}
     * set with nothing left to clear it, and vanilla saved it, so the NPC came back frozen on every
     * subsequent load.
     *
     * <p>{@code forget} drops our tracking without restoring, which is exactly the state a JVM restart
     * leaves behind: the stamp is in the save, the owner is gone. Reloading must then repair it.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aLeakedFreezeIsRepairedFromTheSaveData(GameTestHelper helper) {
        XenoNpcEntity npc = spawn(helper);
        FreezeOwnership.acquire(npc, Reason.TECHNIQUE);
        helper.assertTrue(npc.isNoAi(), "frozen while the technique holds");

        // Simulate the owner vanishing without releasing.
        FreezeOwnership.forget(npc.getUUID());
        helper.assertFalse(FreezeOwnership.isFrozen(npc.getUUID()), "tracking is gone");
        helper.assertTrue(npc.isNoAi(), "the flag is still set — this is the leaked state");

        // Round-trip through the same NBT a chunk save would write.
        CompoundTag saved = npc.saveWithoutId(new CompoundTag());
        XenoNpcEntity reloaded = spawn(helper);
        reloaded.load(saved);

        helper.assertTrue(reloaded.isNoAi(),
                "NoAI is persisted by vanilla, so the reloaded NPC is still frozen");
        helper.assertTrue(FreezeOwnership.repairOnLoad(reloaded),
                "the repair stamp must survive the save round trip, or leaked freezes are permanent");
        helper.assertFalse(reloaded.isNoAi(), "the reloaded NPC is awake after repair");
        helper.assertFalse(FreezeOwnership.repairOnLoad(reloaded),
                "repair is one-shot and must not fire again once the stamp is consumed");

        npc.discard();
        reloaded.discard();
        helper.succeed();
    }

    /** An NPC a map author or an operator parked must not be woken by a combat freeze ending. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void anAlreadyFrozenNpcGoesBackToFrozen(GameTestHelper helper) {
        XenoNpcEntity npc = spawn(helper);
        npc.setNoAi(true);

        FreezeOwnership.acquire(npc, Reason.CINEMATIC);
        helper.assertTrue(npc.isNoAi(), "still frozen");
        FreezeOwnership.release(npc, Reason.CINEMATIC);
        helper.assertTrue(npc.isNoAi(),
                "the pre-freeze value was true, so releasing must not wake an NPC we did not freeze");
        helper.succeed();
    }

    /** Overlapping holders: the first to leave must not wake a victim the second is still holding. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void overlappingHoldersKeepTheFreezeUntilTheLastOneLeaves(GameTestHelper helper) {
        XenoNpcEntity npc = spawn(helper);
        FreezeOwnership.acquire(npc, Reason.TECHNIQUE);
        FreezeOwnership.acquire(npc, Reason.CINEMATIC);

        FreezeOwnership.release(npc, Reason.TECHNIQUE);
        helper.assertTrue(npc.isNoAi(), "the cinematic still holds, so the NPC stays frozen");
        helper.assertTrue(FreezeOwnership.isHeld(npc.getUUID(), Reason.CINEMATIC), "cinematic still tracked");

        FreezeOwnership.release(npc, Reason.CINEMATIC);
        helper.assertFalse(npc.isNoAi(), "the last holder leaving wakes the NPC");
        helper.assertFalse(FreezeOwnership.isFrozen(npc.getUUID()), "nothing is tracked any more");
        helper.succeed();
    }

    /** Teardown on unload or death restores unconditionally, whatever combination was held. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void releaseAllRestoresRegardlessOfHolders(GameTestHelper helper) {
        XenoNpcEntity npc = spawn(helper);
        FreezeOwnership.acquire(npc, Reason.TECHNIQUE);
        FreezeOwnership.acquire(npc, Reason.FINISHER);

        FreezeOwnership.releaseAll(npc);
        helper.assertFalse(npc.isNoAi(), "teardown wakes the NPC");
        helper.assertFalse(FreezeOwnership.isFrozen(npc.getUUID()), "teardown drops tracking");
        // Idempotent: teardown fires from several events, so a second call must be harmless.
        FreezeOwnership.releaseAll(npc);
        helper.assertFalse(npc.isNoAi(), "a second teardown must not change anything");
        helper.succeed();
    }
}
