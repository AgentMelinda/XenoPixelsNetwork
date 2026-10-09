package net.bullettrain.xenopixelsmod.fx.effek;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Verifies the native wave visual join and AAA request; it does not render particles. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KiImpactGameTests {
    private KiImpactGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void waveExplosionUsesActualImpactHeight(GameTestHelper helper) {
        var saved = XenoServerConfig.snapshot();
        List<EffekSender.Request> requests = new ArrayList<>();
        XenoEffects.resetForTest();
        XenoEffects.useSender((level, request) -> requests.add(request));
        XenoServerConfig.effekseerEnabled = true;
        XenoServerConfig.effekseerKiImpacts = true;
        KiWaveEntity wave = new KiWaveEntity(MainEntities.KI_WAVE.get(), helper.getLevel());
        wave.setSize(0.1f);
        wave.setBlockDestructionEnabled(false);
        Vec3 impact = helper.absoluteVec(new Vec3(2, 6, 2));
        try {
            // The pinned native private method is the shared termination path of Kamehameha waves.
            var explode = KiWaveEntity.class.getDeclaredMethod("explodeAndDie", Vec3.class);
            explode.setAccessible(true);
            explode.invoke(wave, impact);
            helper.assertTrue(requests.size() == 1 && requests.getFirst().pos().equals(impact),
                    "AAA request anchors at actual wave impact, not the native -0.5 Y visual");

            requests.clear();
            KiExplosionVisualEntity ordinary = new KiExplosionVisualEntity(MainEntities.KI_EXPLOSION_VISUAL.get(), helper.getLevel());
            Vec3 ordinaryPosition = impact.add(5, 3, 0);
            ordinary.setPos(ordinaryPosition);
            ordinary.setupExplosion(0xFFFFFF, 0xFFFFFF, 1);
            helper.getLevel().addFreshEntity(ordinary);
            helper.assertTrue(requests.size() == 1 && requests.getFirst().pos().equals(ordinaryPosition),
                    "Non-wave explosion visuals retain their native anchor");

            XenoServerConfig.effekseerKiImpacts = false;
            KiExplosionVisualEntity fallback = new KiExplosionVisualEntity(MainEntities.KI_EXPLOSION_VISUAL.get(), helper.getLevel());
            fallback.setPos(impact.add(0, -0.5, 0));
            fallback.getPersistentData().putBoolean(KiImpactRules.WAVE_VISUAL, true);
            helper.assertTrue(helper.getLevel().addFreshEntity(fallback), "Native visual remains when AAA ki impacts are off");
            helper.assertTrue(fallback.getY() == impact.y - 0.5, "Native fallback position is unchanged");
            fallback.discard();
            helper.succeed();
            XenoPixelsMod.LOGGER.info("KiImpact GameTest PASS: wave AAA origin={}, ordinary/fallback anchors preserved", impact);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Pinned wave explosion test entrypoint failed", e);
        } finally {
            wave.discard();
            XenoEffects.resetForTest();
            XenoServerConfig.apply(saved);
        }
    }
}
