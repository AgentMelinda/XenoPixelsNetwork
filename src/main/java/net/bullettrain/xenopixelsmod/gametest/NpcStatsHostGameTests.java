package net.bullettrain.xenopixelsmod.gametest;

import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzStats;
import net.bullettrain.xenopixelsmod.compat.npc.NpcStatsAttributeHost;
import net.bullettrain.xenopixelsmod.compat.npc.NpcStatsDataAccess;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;

/**
 * Runtime proof that an NPC's DragonMineZ stats resolve through the NPC itself.
 *
 * <p>The three host mixins sit in {@code xenopixelsmod.mixins.json}, and a unit test can only check
 * that the file names them - an unregistered mixin is silent, which is exactly how
 * {@code StatsDataNpcHostMixin} sat inert from 2026-09-30 while {@code NpcDmzStats#bindHost}
 * documented itself as "the call that arms it". These run against a real DMZ 2.2 server, so they
 * fail unless the mixin actually wove: without it {@code StatsData} does not implement
 * {@code NpcStatsDataAccess}, {@code Stats.applyToAttributes()} returns on its first line, and
 * {@code getFlatDefense()} throws on DMZ's unguarded {@code Player.getArmorValue()}.
 *
 * <p>Run with {@code ./gradlew runGameTestServer -PofflineMcMeta}.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcStatsHostGameTests {
    private NpcStatsHostGameTests() {}

    private static XenoNpcEntity npc(GameTestHelper helper, BlockPos at) {
        XenoNpcEntity npc = helper.spawn(ModEntities.XENO_NPC_HUMANOID.get(), at);
        npc.setNoAi(true);
        return npc;
    }

    private static ScriptNpc script(XenoNpcEntity npc) {
        return new ScriptNpc(npc, new HashMap<>(), new ScriptTimers());
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void hostMixinsWoveAndDefenseFollowsTheHost(GameTestHelper helper) {
        StatsData blob = new StatsData(null);
        helper.assertTrue(blob instanceof NpcStatsDataAccess,
                "StatsDataNpcHostMixin wove: an unapplied mixin never adds the interface it implements");
        helper.assertTrue(blob.getStats() instanceof NpcStatsAttributeHost,
                "StatsNpcHostMixin wove onto DMZ's Stats");

        boolean threw = false;
        try {
            blob.getFlatDefense();
        } catch (RuntimeException caught) {
            threw = true;
        }
        helper.assertTrue(!threw,
                "getFlatDefense() answers on a blob with no player: the @Redirect replaced DMZ's unguarded Player.getArmorValue()");

        Player host = helper.makeMockPlayer(GameType.SURVIVAL);
        host.setNoGravity(true);
        AttributeInstance armor = host.getAttribute(Attributes.ARMOR);
        helper.assertTrue(armor != null, "the host carries an ARMOR attribute for DMZ's defense read");
        ((NpcStatsDataAccess) blob).xenopixels$setNpcHost(host);
        ((NpcStatsDataAccess) blob).xenopixels$markLoaded();
        double boundArmor = blob.getFlatDefense();
        armor.setBaseValue(armor.getBaseValue() + 12.0);
        helper.assertTrue(blob.getFlatDefense() > boundArmor,
                "the defense read goes to the bound host, not to the null player field");
        host.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcStatsHost GameTest PASS: host mixins wove, defense follows the bound host");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void npcStatsAnswerForTheirOwnNpc(GameTestHelper helper) {
        XenoNpcEntity strong = npc(helper, new BlockPos(1, 1, 1));
        XenoNpcEntity weak = npc(helper, new BlockPos(2, 1, 1));
        NativeXenoScriptApi.INSTANCE.setStat(script(strong), "strength", 200);
        NativeXenoScriptApi.INSTANCE.setStat(script(weak), "strength", 4);

        StatsData strongStats = NpcDmzStats.stats(strong);
        StatsData weakStats = NpcDmzStats.stats(weak);
        helper.assertTrue(strongStats != null && weakStats != null && strongStats != weakStats,
                "each profiled NPC carries its own DragonMineZ blob");
        helper.assertTrue(((NpcStatsDataAccess) strongStats).xenopixels$getNpcHost() == strong,
                "the blob points at the entity carrying it, so its attribute reads are that entity's");
        helper.assertTrue(strongStats.getStats().getStrength() == 200 && weakStats.getStats().getStrength() == 4,
                "no stat leaks between neighbours: " + strongStats.getStats().getStrength()
                        + " and " + weakStats.getStats().getStrength());

        AttributeInstance strength = strong.getAttribute(MainAttributes.STRENGTH);
        helper.assertTrue(strength != null, "the NPC declares DMZ's STRENGTH attribute, so writes are not dropped");
        helper.assertTrue(Math.abs(strength.getBaseValue() - 200.0) < 1e-6,
                "StatsNpcHostMixin put the DMZ stat onto the NPC's own attribute: base " + strength.getBaseValue());
        helper.assertTrue(weak.getAttribute(MainAttributes.STRENGTH).getBaseValue() < 10.0,
                "the neighbour's attribute stayed its own");
        helper.assertTrue(strongStats.getMeleeDamage() > weakStats.getMeleeDamage(),
                "DMZ's own melee formula, run on each NPC's own stats");

        StatsData throughDmz = StatsProvider.get(StatsCapability.INSTANCE, strong).resolve().orElse(null);
        helper.assertTrue(throughDmz == strongStats,
                "DMZ's StatsProvider finds the NPC's blob, so DMZ code needs no XenoPixels call to do so");
        strong.discard();
        weak.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcStatsHost GameTest PASS: per-NPC stats, attribute writes, StatsProvider route");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aMobHostCanCarryDefenseToo(GameTestHelper helper) {
        XenoNpcEntity npc = npc(helper, new BlockPos(1, 1, 1));
        NativeXenoScriptApi.INSTANCE.setStat(script(npc), "resistance", 100);
        StatsData data = NpcDmzStats.stats(npc);
        helper.assertTrue(data != null, "the profiled NPC has DragonMineZ stats");
        double withoutArmor = data.getFlatDefense();
        double defenseWithoutArmor = data.getDefense();
        double maxDefenseWithoutArmor = data.getMaxDefense();
        double maxFlatWithoutArmor = data.getMaxFlatDefense();
        AttributeInstance armor = npc.getAttribute(Attributes.ARMOR);
        AttributeInstance toughness = npc.getAttribute(Attributes.ARMOR_TOUGHNESS);
        helper.assertTrue(armor != null && toughness != null,
                "an NPC declares the armor attributes DMZ's defense reads want; without them every "
                        + "armor contribution is dropped and no NPC can ever be tanked up");
        armor.setBaseValue(armor.getBaseValue() + 12.0);
        helper.assertTrue(data.getFlatDefense() > withoutArmor,
                "the NPC's own armor moves its own flat defense");
        // The other three reads share DMZ's unguarded Player.getArmorValue(), which NpcDmzStats
        // now allows for an NPC because the host redirect answers it - here, on a real mob host.
        helper.assertTrue(data.getMaxFlatDefense() > maxFlatWithoutArmor, "getMaxFlatDefense() answers an NPC blob");
        helper.assertTrue(data.getDefense() > defenseWithoutArmor, "getDefense() answers from the NPC's own armor");
        helper.assertTrue(data.getMaxDefense() > maxDefenseWithoutArmor, "getMaxDefense() answers too");
        npc.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcStatsHost GameTest PASS: mob host answers DMZ defense reads");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void readsNeverGrowAStatsBlob(GameTestHelper helper) {
        XenoNpcEntity bare = npc(helper, new BlockPos(1, 1, 1));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(NpcDmzStats.stats(bare) == null,
                "an NPC that was never given a profile has no DragonMineZ stats");
        helper.assertTrue(!bare.hasData(NpcDmzStats.NPC_DMZ_STATS.get()),
                "the read did not attach an empty blob the dispatcher would then read zeroes from");
        helper.assertTrue(!StatsProvider.get(StatsCapability.INSTANCE, bare).isPresent(),
                "DMZ's lookup reports the same answer");
        helper.assertTrue(!bare.hasData(NpcDmzStats.NPC_DMZ_STATS.get()),
                "and going through DMZ did not create one either");
        helper.assertTrue(NpcDmzStats.stats(player) == null,
                "a player never takes the NPC path, so DMZ's own player attachment stays the only source of truth");
        helper.assertTrue(!player.hasData(NpcDmzStats.NPC_DMZ_STATS.get()),
                "looking up a player left no NPC stats behind");
        player.discard();
        bare.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcStatsHost GameTest PASS: reads stay non-creating, players stay on their own path");
    }

    /**
     * A reloaded NPC answers with the stats it was saved with, on the entity that loaded.
     *
     * <p>An entity is a different object after a reload, so both halves of the blob have to be
     * recovered from NBT: the stat values, and the host pointer that makes DMZ's attribute reads and
     * writes land on the mob rather than on the entity that was discarded. The fingerprint tag is
     * copied too, because it survives the reload and therefore skips the profile apply that would
     * otherwise re-push the numbers - a reloaded NPC that only had its values in the blob and zeroes
     * on its attributes would read weaker to everything that asks the attribute.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void statsSurviveASaveAndRebindToTheNewEntity(GameTestHelper helper) {
        XenoNpcEntity saved = npc(helper, new BlockPos(1, 1, 1));
        NativeXenoScriptApi.INSTANCE.setStat(script(saved), "strength", 300);
        CompoundTag blob = saved.getData(NpcDmzStats.NPC_DMZ_STATS.get()).serializeNBT(helper.getLevel().registryAccess());
        helper.assertTrue(blob.getCompound("Stats").getInt("STR") == 300,
                "the saved attachment carries the NPC's own stat: " + blob.getCompound("Stats"));

        XenoNpcEntity reloaded = npc(helper, new BlockPos(1, 1, 1));
        reloaded.getPersistentData().merge(saved.getPersistentData().copy());
        NpcDmzStats.NpcStatsAttachment restored = new NpcDmzStats.NpcStatsAttachment();
        restored.deserializeNBT(helper.getLevel().registryAccess(), blob);
        reloaded.setData(NpcDmzStats.NPC_DMZ_STATS.get(), restored);
        saved.discard();

        StatsData data = NpcDmzStats.stats(reloaded);
        helper.assertTrue(data != null, "the loaded NPC has its DragonMineZ stats back");
        helper.assertTrue(data.getStats().getStrength() == 300,
                "the stat round-tripped through NBT: " + data.getStats().getStrength());
        helper.assertTrue(((NpcStatsDataAccess) data).xenopixels$getNpcHost() == reloaded,
                "the read rebound the host onto the loaded entity, not the discarded one");
        AttributeInstance strength = reloaded.getAttribute(MainAttributes.STRENGTH);
        helper.assertTrue(strength != null && Math.abs(strength.getBaseValue() - 300.0) < 1e-6,
                "a loaded NPC's attribute carries its saved stat, since the fingerprint means the "
                        + "profile never re-pushes it: base " + (strength == null ? "no attribute" : strength.getBaseValue()));
        reloaded.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcStatsHost GameTest PASS: stats reload onto the new entity and re-armed its attributes");
    }
}
