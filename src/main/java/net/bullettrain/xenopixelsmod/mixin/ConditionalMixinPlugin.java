package net.bullettrain.xenopixelsmod.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
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
        if (mixinClassName.contains(".compat.ballistix.")) {
            return isModLoaded("ballistix") && isModLoaded("voltaic");
        }
        if (mixinClassName.contains(".compat.voltaic.")) {
            return isModLoaded("voltaic");
        }
        if (mixinClassName.contains(".compat.vs2.")) {
            return isModLoaded("valkyrienskies");
        }
        if (mixinClassName.contains(".compat.create.")) {
            return isModLoaded("create");
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
            if (FMLLoader.getLoadingModList() == null) return false;
            return FMLLoader.getLoadingModList().getModFileById(modId) != null;
        } catch (Throwable t) {
            try {
                // Fallback: class present on classpath
                if ("ballistix".equals(modId)) {
                    Class.forName("ballistix.common.entity.EntityMissile", false,
                            ConditionalMixinPlugin.class.getClassLoader());
                    return true;
                }
                if ("voltaic".equals(modId)) {
                    Class.forName("voltaic.prefab.tile.GenericTile", false,
                            ConditionalMixinPlugin.class.getClassLoader());
                    return true;
                }
                if ("valkyrienskies".equals(modId)) {
                    Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt", false,
                            ConditionalMixinPlugin.class.getClassLoader());
                    return true;
                }
                if ("create".equals(modId)) {
                    Class.forName("com.simibubi.create.Create", false,
                            ConditionalMixinPlugin.class.getClassLoader());
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
    }
}
