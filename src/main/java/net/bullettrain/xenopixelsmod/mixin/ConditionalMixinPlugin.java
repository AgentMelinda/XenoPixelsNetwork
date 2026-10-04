package net.bullettrain.xenopixelsmod.mixin;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates optional compat mixins so they only apply when the target mod is present.
 * Uses {@link FMLLoader#getLoadingModList()} (available during mixin apply).
 */
public class ConditionalMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Permission API the ki-griefing compat compiles against; absent on older YAWP builds. */
    private static final String YAWP_API_CLASS = "de.z0rdak.yawp.api.permission.FlagPermissions";

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("Loaded mixin config plugin for {} (customnpcs={}, mynpcs={})",
                mixinPackage, isModLoaded("customnpcs"), isModLoaded("mynpcs"));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.linearreader.")) {
            var mods = FMLLoader.getLoadingModList();
            var file = mods == null ? null : mods.getModFileById("linearreader");
            boolean supported = file != null && file.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("linearreader") && mod.getVersion().toString().equals("1.3.0"));
            if (file != null && !supported) LOGGER.error("LinearReader dimension policy requires verified version 1.3.0; integration skipped");
            return supported;
        }
        // Package-based gates under mixin.compat.*
        if (mixinClassName.contains(".compat.sable.")) {
            // Contraption collider is a Create type; skip apply if Create is absent.
            if (mixinClassName.contains("Contraption")) {
                return isModLoaded("sable") && isModLoaded("create");
            }
            return isModLoaded("sable");
        }
        if (mixinClassName.contains(".compat.zfastnoise.")) {
            return isModLoaded("zfastnoise");
        }
        if (mixinClassName.contains(".compat.simulatedcoasters.")) {
            return isModLoaded("simulatedcoasters");
        }
        if (mixinClassName.contains(".compat.createpropulsion.")) {
            return isModLoaded("createpropulsion");
        }
        if (mixinClassName.contains(".compat.controlify.")) {
            return isModLoaded("controlify");
        }
        if (mixinClassName.contains(".compat.cosmonautics.")) {
            return isModLoaded("rocketnautics");
        }
        if (mixinClassName.contains(".compat.create.")) {
            return isModLoaded("create");
        }
        if (mixinClassName.contains(".compat.xaero.")) {
            return isModLoaded("xaeroworldmap");
        }
        if (mixinClassName.contains(".compat.aerostar.")) {
            return isModLoaded("aerostarcomp");
        }
        if (mixinClassName.contains(".compat.shared.")) {
            return SharedNpcMixinPolicy.shouldApply(mixinClassName,
                    isModLoaded("customnpcs"), isModLoaded("mynpcs"));
        }
        if (mixinClassName.contains(".compat.cnpcgecko.")) {
            return isModLoaded("customnpcs") && isModLoaded("cnpcgeckoaddon");
        }
        if (mixinClassName.contains(".compat.dmz.")) {
            return isModLoaded("dragonminez");
        }
        if (mixinClassName.contains(".compat.customnpcs.")) {
            return isModLoaded("customnpcs");
        }
        if (mixinClassName.contains(".compat.mynpcs.")) {
            return isModLoaded("mynpcs");
        }
        if (mixinClassName.contains(".compat.yawp.")) {
            // Not just "is YAWP installed" — the ki-griefing hook calls the 0.6.3 permission
            // API, and older builds on the 0.6 line ship a different surface. Requiring the
            // class itself means an unsupported YAWP silently skips the compat hook instead
            // of booting fine and then throwing NoClassDefFoundError on the first ki blast.
            return isModLoaded("yawp") && isClassPresent(YAWP_API_CLASS);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    public static boolean isModLoaded(String modId) {
        try {
            if (FMLLoader.getLoadingModList() != null
                    && FMLLoader.getLoadingModList().getModFileById(modId) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // Classpath fallbacks (mixin apply / weird loaders)
        if ("sable".equals(modId)) {
            return isClassPresent("dev.ryanhcode.sable.api.SubLevelHelper");
        }
        if ("zfastnoise".equals(modId)) {
            return isClassPresent("org.codeberg.zenxarch.fastnoise.FastNoiseMod");
        }
        if ("create".equals(modId)) {
            return isClassPresent("com.simibubi.create.Create");
        }
        if ("simulatedcoasters".equals(modId)) {
            return isClassPresent("dev.silvergold.simulatedcoasters.track.cart.CoasterCartPlotScan");
        }
        if ("createpropulsion".equals(modId)) {
            return isClassPresent(
                    "dev.propulsionteam.propulsionsimulated.particles.plasma.PlasmaParticle");
        }
        if ("controlify".equals(modId)) {
            return isClassPresent("dev.isxander.controlify.api.ControlifyApi");
        }
        if ("rocketnautics".equals(modId)) {
            return isClassPresent("dev.egg.SubLevelTemplate");
        }
        if ("xaeroworldmap".equals(modId)) {
            return isClassPresent("xaero.map.gui.GuiMap");
        }
        if ("yawp".equals(modId)) {
            return isClassPresent(YAWP_API_CLASS);
        }
        if ("aerostarcomp".equals(modId)) {
            return isClassPresent("com.bega.aerostarcomp.physics.OrbitGravitySystem");
        }
        if ("customnpcs".equals(modId)) {
            return isClassPresent("noppes.npcs.CustomNpcs");
        }
        if ("cnpcgeckoaddon".equals(modId)) {
            return isClassPresent("com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon");
        }
        if ("mynpcs".equals(modId)) {
            return isClassPresent("espi.mynpcs.MyNpcs");
        }
        return false;
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, ConditionalMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
