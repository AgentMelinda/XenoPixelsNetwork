package net.bullettrain.xenopixelsmod.features;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoFeaturesConfig;
import net.bullettrain.xenopixelsmod.features.customization.CustomizationManager;
import net.bullettrain.xenopixelsmod.features.skilltree.SkillTreeManager;
import net.bullettrain.xenopixelsmod.features.transformation.XenoFormRegistry;

/**
 * Boots feature packages according to {@link XenoFeaturesConfig}.
 *
 * <p>Most feature managers are in-memory stubs (OFF by default). Form catalog + config
 * multipliers are the main LIVE surface besides combat/HUD.
 */
public final class FeatureManager {
    private static volatile boolean booted;

    private FeatureManager() {
    }

    /** Load config then init enabled feature modules. Safe to call more than once. */
    public static void bootstrap() {
        XenoFeaturesConfig.load();
        if (booted) {
            // Config may have changed; re-register form catalog if enabled
            if (XenoFeaturesConfig.transformationEnabled && XenoFeaturesConfig.registerCustomForms) {
                XenoFormRegistry.registerAll();
            }
            return;
        }
        booted = true;

        if (XenoFeaturesConfig.skillTreeEnabled) {
            SkillTreeManager.init();
        }
        if (XenoFeaturesConfig.customizationEnabled) {
            CustomizationManager.init();
        }
        if (XenoFeaturesConfig.transformationEnabled && XenoFeaturesConfig.registerCustomForms) {
            XenoFormRegistry.registerAll();
        }

        // Remaining packages are pure data stubs with no dedicated init() — log inventory
        int live = 0;
        int stub = 0;
        for (FeatureStatus.Entry e : FeatureStatus.all()) {
            if (e.tier() == FeatureStatus.Tier.LIVE) {
                live++;
            } else {
                stub++;
            }
        }
        XenoPixelsMod.LOGGER.info(
                "FeatureManager: boot complete ({} LIVE, {} STUB entries). Stubs stay off unless enabled in xenopixelsmod-features.json.",
                live, stub);
    }
}
