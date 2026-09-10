package net.bullettrain.xenopixelsmod.mixin.compat.create;

import com.simibubi.create.foundation.advancement.CreateAdvancement;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import net.bullettrain.xenopixelsmod.compat.create.CreateAdvancementItemResolver;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Defers Create's Registrate-backed advancement items until datagen supplies registry lookups. */
@Mixin(targets = "com.simibubi.create.foundation.advancement.CreateAdvancement$Builder", remap = false)
public abstract class CreateAdvancementBuilderMixin {
    @Shadow
    private boolean externalTrigger;

    @Shadow
    private int keyIndex;

    @Shadow
    private ItemStack icon;

    @Shadow
    private Function<HolderLookup.Provider, ItemStack> func;

    @Shadow
    @Final
    private CreateAdvancement this$0;

    @Unique
    private ResourceLocation xenopixels$deferredIcon;

    @Unique
    private final List<DeferredCriterion> xenopixels$deferredCriteria = new ArrayList<>();

    @Unique
    private Function<HolderLookup.Provider, ItemStack> xenopixels$baseIconFunction;

    @Unique
    private boolean xenopixels$resolverInstalled;

    @Unique
    private boolean xenopixels$criteriaBound;

    @Inject(
            method = "icon(Lcom/tterrag/registrate/util/entry/ItemProviderEntry;)Lcom/simibubi/create/foundation/advancement/CreateAdvancement$Builder;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void xenopixels$deferIcon(ItemProviderEntry<?, ?> entry,
                                     CallbackInfoReturnable<Object> cir) {
        xenopixels$deferredIcon = entry.getId();
        xenopixels$installResolver();
        cir.setReturnValue(this);
    }

    @Inject(
            method = "icon(Ljava/util/function/Function;)Lcom/simibubi/create/foundation/advancement/CreateAdvancement$Builder;",
            at = @At("RETURN"), require = 0)
    private void xenopixels$preserveDeferredResolver(
            Function<HolderLookup.Provider, ItemStack> function,
            CallbackInfoReturnable<Object> cir) {
        if (!xenopixels$deferredCriteria.isEmpty() || xenopixels$deferredIcon != null) {
            xenopixels$baseIconFunction = function;
            xenopixels$resolverInstalled = false;
            xenopixels$installResolver();
        }
    }

    @Inject(
            method = "whenIconCollected()Lcom/simibubi/create/foundation/advancement/CreateAdvancement$Builder;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void xenopixels$deferIconCriterion(CallbackInfoReturnable<Object> cir) {
        if (xenopixels$deferredIcon == null) {
            return;
        }
        xenopixels$addDeferredCriterion(xenopixels$deferredIcon);
        cir.setReturnValue(this);
    }

    @Inject(
            method = "whenItemCollected(Lcom/tterrag/registrate/util/entry/ItemProviderEntry;)Lcom/simibubi/create/foundation/advancement/CreateAdvancement$Builder;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void xenopixels$deferCollectedItem(ItemProviderEntry<?, ?> entry,
                                               CallbackInfoReturnable<Object> cir) {
        xenopixels$addDeferredCriterion(entry.getId());
        cir.setReturnValue(this);
    }

    @Unique
    private void xenopixels$addDeferredCriterion(ResourceLocation id) {
        xenopixels$deferredCriteria.add(new DeferredCriterion(Integer.toString(keyIndex++), id));
        externalTrigger = true;
        xenopixels$installResolver();
    }

    @Unique
    private void xenopixels$installResolver() {
        if (xenopixels$resolverInstalled) {
            return;
        }
        xenopixels$baseIconFunction = func;
        func = registries -> {
            xenopixels$bindCriteria(registries);
            if (xenopixels$deferredIcon != null) {
                return CreateAdvancementItemResolver.stack(registries, xenopixels$deferredIcon);
            }
            if (xenopixels$baseIconFunction != null) {
                return xenopixels$baseIconFunction.apply(registries);
            }
            return icon;
        };
        xenopixels$resolverInstalled = true;
    }

    @Unique
    private void xenopixels$bindCriteria(HolderLookup.Provider registries) {
        if (xenopixels$criteriaBound) {
            return;
        }
        var builder = ((CreateAdvancementAccessor) this$0).xenopixels$minecraftBuilder();
        for (DeferredCriterion deferred : xenopixels$deferredCriteria) {
            builder.addCriterion(deferred.key(),
                    CreateAdvancementItemResolver.collected(registries, deferred.itemId()));
        }
        xenopixels$criteriaBound = true;
    }

    @Unique
    private record DeferredCriterion(String key, ResourceLocation itemId) {
    }
}
