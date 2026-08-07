package net.bullettrain.xenopixelsmod.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates optional compat mixins so they only apply when the target mod is present.
 * Uses {@link FMLLoader#getLoadingModList()} (available during mixin apply).
 */
public class ConditionalMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Package-based gates under mixin.compat.*
        if (mixinClassName.contains(".compat.sable.")) {
            return isModLoaded("sable");
        }
        if (mixinClassName.contains(".compat.create.")) {
            return isModLoaded("create");
        }
        if (mixinClassName.contains(".compat.xaero.")) {
            return isModLoaded("xaeroworldmap");
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
        if ("create".equals(modId)) {
            return isClassPresent("com.simibubi.create.Create");
        }
        if ("xaeroworldmap".equals(modId)) {
            return isClassPresent("xaero.map.gui.GuiMap");
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
