package net.bullettrain.xenopixelsmod.mixin.compat.cnpcgecko;

import net.bullettrain.xenopixelsmod.compat.npc.NpcHairModelData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.goodbird.cnpcgeckoaddon.data.CustomModelData", remap = false)
public abstract class CustomModelDataMixin implements NpcHairModelData {
    @Unique private static final String XENO_ENABLED = "XenoDmzHairEnabled";
    @Unique private static final String XENO_CODE = "XenoDmzHairCode";
    @Unique private static final String XENO_COLOR = "XenoDmzHairColor";

    @Unique private boolean xenopixels$dmzHairEnabled;
    @Unique private String xenopixels$dmzHairCode = "";
    @Unique private String xenopixels$dmzHairColor = "";

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void xenopixels$writeHair(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag out = cir.getReturnValue() == null ? tag : cir.getReturnValue();
        out.putBoolean(XENO_ENABLED, xenopixels$dmzHairEnabled);
        out.putString(XENO_CODE, xenopixels$dmzHairCode);
        out.putString(XENO_COLOR, xenopixels$dmzHairColor);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void xenopixels$readHair(CompoundTag tag, CallbackInfo ci) {
        xenopixels$dmzHairEnabled = tag.getBoolean(XENO_ENABLED);
        xenopixels$dmzHairCode = tag.getString(XENO_CODE);
        xenopixels$dmzHairColor = tag.getString(XENO_COLOR);
    }

    @Override public boolean xenopixels$isDmzHairEnabled() { return xenopixels$dmzHairEnabled; }
    @Override public void xenopixels$setDmzHairEnabled(boolean enabled) { xenopixels$dmzHairEnabled = enabled; }
    @Override public String xenopixels$getDmzHairCode() { return xenopixels$dmzHairCode; }
    @Override public void xenopixels$setDmzHairCode(String code) { xenopixels$dmzHairCode = code == null ? "" : code.trim(); }
    @Override public String xenopixels$getDmzHairColor() { return xenopixels$dmzHairColor; }
    @Override public void xenopixels$setDmzHairColor(String color) { xenopixels$dmzHairColor = color == null ? "" : color.trim(); }
}
