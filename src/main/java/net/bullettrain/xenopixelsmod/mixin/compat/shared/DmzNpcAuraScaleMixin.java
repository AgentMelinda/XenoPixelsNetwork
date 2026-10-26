package net.bullettrain.xenopixelsmod.mixin.compat.shared;

import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.client.render.effects.AuraRenderer.AuraLayer;
import com.dragonminez.common.stats.StatsData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Makes native DMZ aura and spark geometry follow the owning CustomNPC's display size. */
@Mixin(value = AuraRenderer.class, remap = false)
public abstract class DmzNpcAuraScaleMixin {
    private static final ThreadLocal<UUID> XENOPIXELS_AURA_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> XENOPIXELS_LIGHTNING_PLAYER = new ThreadLocal<>();

    @Shadow
    private static void applyAndDraw(VertexBuffer mesh, PoseStack pose, Matrix4f projection,
                                     ShaderInstance shader, ResourceLocation texture, float[] color,
                                     float alpha, float time, boolean mirrored, boolean firstPerson) {
        throw new AssertionError();
    }

    @Shadow
    private static void applyAndDraw(VertexBuffer mesh, PoseStack pose, Matrix4f projection,
                                     ShaderInstance shader, ResourceLocation texture, float[] color,
                                     float alpha, float time, boolean ground) {
        throw new AssertionError();
    }

    @ModifyReturnValue(method = "useAura3D(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("RETURN"), require = 1)
    private static boolean xenopixels$npcThreeD(boolean original, Player player) {
        NpcAuraResolver.Resolved resolved = NpcFullDmzRenderer.nativeAura(player.getUUID());
        return resolved == null ? original : resolved.layers().stream().anyMatch(NpcAuraResolver.Layer::threeD);
    }

    @ModifyExpressionValue(method = "executeAuraShaderDraw",
            at = @At(value = "FIELD", target = "Lcom/dragonminez/client/render/effects/AuraRenderer$CachedAuraData;use3D:Z"), require = 1)
    private static boolean xenopixels$npcLayerThreeD(boolean original, Player player,
            @org.spongepowered.asm.mixin.injection.Coerce Object data, AuraLayer layer) {
        NpcAuraResolver.Resolved resolved = NpcFullDmzRenderer.nativeAura(player.getUUID());
        if (resolved == null) return original;
        return resolved.layers().stream().filter(candidate -> candidate.index() == layer.layerId)
                .findFirst().map(NpcAuraResolver.Layer::threeD).orElse(false);
    }

    @ModifyReturnValue(
            method = "getModelScale(Lcom/dragonminez/common/stats/StatsData;)[F",
            at = @At("RETURN"), require = 1)
    private static float[] xenopixels$scaleNpcAura(float[] original, StatsData stats) {
        float factor = NpcFullDmzRenderer.auraFactor(stats);
        if (factor == 1.0f || original == null || original.length < 3) return original;
        return new float[]{original[0] * factor, original[1] * factor, original[2] * factor};
    }

    @ModifyReturnValue(
            method = "getAuraLayers(Lnet/minecraft/world/entity/player/Player;" +
                    "Lcom/dragonminez/common/stats/StatsData;FZ)Ljava/util/List;",
            at = @At("RETURN"), require = 1)
    private static List<AuraLayer> xenopixels$useNpcAuraLayers(
            List<AuraLayer> original, Player player, StatsData stats, float partialTick, boolean use3D) {
        NpcAuraResolver.Resolved resolved = NpcFullDmzRenderer.nativeAura(player.getUUID());
        if (resolved == null || resolved.layers().isEmpty()) return original;
        List<AuraLayer> layers = new ArrayList<>(resolved.layers().size());
        for (NpcAuraResolver.Layer layer : resolved.layers()) {
            int rgb = layer.rgb();
            AuraLayer nativeLayer = new AuraLayer(layer.type(), layer.index(), new float[]{
                    ((rgb >>> 16) & 0xFF) / 255.0f,
                    ((rgb >>> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f
            });
            if (layer.threeD()) nativeLayer.style = com.dragonminez.client.render.effects.AuraStyle.resolve(
                    com.dragonminez.common.config.FormConfig.Aura3DStyle.DEFAULT, nativeLayer.color);
            layers.add(nativeLayer);
        }
        return layers;
    }

    @Inject(
            method = "renderSparksImpl(Lnet/minecraft/world/entity/player/Player;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FZ)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void xenopixels$toggleNpcLightning(
            Player player, PoseStack pose, Matrix4f projection, float partialTick,
            boolean firstPerson, CallbackInfo ci) {
        NpcAuraResolver.Resolved resolved = NpcFullDmzRenderer.nativeAura(player.getUUID());
        if (resolved == null) return;
        if (!resolved.lightning()) {
            ci.cancel();
            return;
        }
        XENOPIXELS_LIGHTNING_PLAYER.set(player.getUUID());
    }

    /** Let an NPC form's explicit lightning override enable the native transformed sparks. */
    @ModifyExpressionValue(
            method = "renderSparksImpl(Lnet/minecraft/world/entity/player/Player;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FZ)V",
            at = @At(value = "INVOKE", target = "Lcom/dragonminez/client/render/effects/AuraFxState;hasLightning(Lcom/dragonminez/common/stats/StatsData;)Z"),
            require = 1)
    private static boolean xenopixels$useNpcLightningToggle(boolean original) {
        NpcAuraResolver.Resolved resolved =
                NpcFullDmzRenderer.nativeAura(XENOPIXELS_LIGHTNING_PLAYER.get());
        return resolved == null ? original : resolved.lightning();
    }

    @ModifyArg(
            method = "renderSparksImpl(Lnet/minecraft/world/entity/player/Player;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FZ)V",
            at = @At(value = "INVOKE", target =
                    "Lcom/dragonminez/client/util/ColorUtils;hexToRgb(Ljava/lang/String;)[F"),
            index = 0, require = 1)
    private static String xenopixels$useNpcLightningColor(
            String original) {
        NpcAuraResolver.Resolved resolved =
                NpcFullDmzRenderer.nativeAura(XENOPIXELS_LIGHTNING_PLAYER.get());
        return resolved == null ? original : String.format("#%06X", resolved.lightningRgb());
    }

    @Inject(
            method = "renderSparksImpl(Lnet/minecraft/world/entity/player/Player;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FZ)V",
            at = @At("RETURN"), require = 1)
    private static void xenopixels$clearNpcLightningPlayer(
            Player player, PoseStack pose, Matrix4f projection, float partialTick,
            boolean firstPerson, CallbackInfo ci) {
        XENOPIXELS_LIGHTNING_PLAYER.remove();
    }

    @ModifyVariable(
            method = "executeAuraShaderDraw", at = @At("HEAD"),
            argsOnly = true, ordinal = 0, require = 1)
    private static Player xenopixels$captureNpcAuraPlayer(Player player) {
        XENOPIXELS_AURA_PLAYER.set(player.getUUID());
        return player;
    }

    @Redirect(
            method = "executeAuraShaderDraw",
            at = @At(value = "INVOKE", target =
                    "Lcom/dragonminez/client/render/effects/AuraRenderer;applyAndDraw(" +
                    "Lcom/mojang/blaze3d/vertex/VertexBuffer;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;" +
                    "Lnet/minecraft/client/renderer/ShaderInstance;" +
                    "Lnet/minecraft/resources/ResourceLocation;[FFFZZ)V"),
            require = 1)
    private static void xenopixels$toggleNpcSparking(
            VertexBuffer mesh, PoseStack pose, Matrix4f projection,
            ShaderInstance shader, ResourceLocation texture, float[] color,
            float alpha, float time, boolean mirrored, boolean firstPerson) {
        NpcAuraResolver.Resolved resolved =
                NpcFullDmzRenderer.nativeAura(XENOPIXELS_AURA_PLAYER.get());
        boolean sparkingTexture = texture != null
                && texture.getPath().endsWith("/sparking_effects.png");
        if (resolved != null && sparkingTexture && !resolved.sparking()) return;
        applyAndDraw(mesh, pose, projection, shader, texture, color,
                alpha, time, mirrored, firstPerson);
    }

    @Redirect(
            method = "executeAuraShaderDraw",
            at = @At(value = "INVOKE", target =
                    "Lcom/dragonminez/client/render/effects/AuraRenderer;applyAndDraw(" +
                    "Lcom/mojang/blaze3d/vertex/VertexBuffer;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;" +
                    "Lnet/minecraft/client/renderer/ShaderInstance;" +
                    "Lnet/minecraft/resources/ResourceLocation;[FFFZ)V"),
            require = 1)
    private static void xenopixels$toggleNpcSparkingShort(
            VertexBuffer mesh, PoseStack pose, Matrix4f projection,
            ShaderInstance shader, ResourceLocation texture, float[] color,
            float alpha, float time, boolean ground) {
        NpcAuraResolver.Resolved resolved =
                NpcFullDmzRenderer.nativeAura(XENOPIXELS_AURA_PLAYER.get());
        boolean sparkingTexture = texture != null
                && texture.getPath().endsWith("/sparking_effects.png");
        if (resolved != null && sparkingTexture && !resolved.sparking()) return;
        applyAndDraw(mesh, pose, projection, shader, texture, color, alpha, time, ground);
    }

    @Inject(method = "executeAuraShaderDraw", at = @At("RETURN"), require = 1)
    private static void xenopixels$clearNpcAuraPlayer(CallbackInfo ci) {
        XENOPIXELS_AURA_PLAYER.remove();
    }
}
