package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.dragonminez.client.render.DMZPlayerRenderer;
import com.dragonminez.client.render.DMZRendererCache;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.ActionMode;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDisplayApply;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Renders a CustomNPC through DragonMineZ's actual player renderer in FULL mode. */
public final class NpcFullDmzRenderer {
    private static final Map<UUID, ProxyPlayer> WORLD_PROXIES = new ConcurrentHashMap<>();
    private static final Map<UUID, ProxyPlayer> PREVIEW_PROXIES = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_PREVIEW_ID = new AtomicInteger(-1_000_000);
    /** Extra model scale read by the NPC-only AuraRenderer mixin. Weak keys cannot retain proxies. */
    private static final Map<StatsData, Float> AURA_FACTORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Resolved NPC overrides consumed by DMZ's deferred native aura renderer. */
    private static final Map<UUID, NpcAuraResolver.Resolved> NATIVE_AURAS = new ConcurrentHashMap<>();
    /** Active only while a synthetic NPC proxy is inside DMZ's renderer. */
    private static final ThreadLocal<RenderContext> RENDER_CONTEXT = new ThreadLocal<>();

    private record RenderContext(StatsData stats, Character character,
                                 FormConfig.FormData activeForm,
                                 FormConfig.FormData activeStackForm,
                                 FormConfig.FormData targetForm,
                                 float transitionProgress, int eyebrowType,
                                 float[] tailColor) {}

    private record TransformSnapshot(FormConfig.FormData targetForm, float progress,
                                     boolean hasHold) {}

    private static final class ProxyPlayer extends AbstractClientPlayer {
        LivingEntity owner;
        String syncedHairCode;

        ProxyPlayer(ClientLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public ItemStack getItemBySlot(EquipmentSlot slot) {
            return owner == null ? super.getItemBySlot(slot) : owner.getItemBySlot(slot);
        }
    }

    private NpcFullDmzRenderer() {}

    public static boolean isFull(LivingEntity owner) {
        NpcAppearanceClient.State state = owner == null ? null : NpcAppearanceClient.get(owner.getUUID());
        if (state != null) return state.appearance().mode == NpcDmzAppearance.Mode.FULL;
        return owner != null && NpcCombatProfile.hasProfile(owner)
                && NpcCombatProfile.read(owner).appearance.mode == NpcDmzAppearance.Mode.FULL;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean render(LivingEntity owner, float entityYaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int packedLight) {
        if (!isFull(owner) || !(owner.level() instanceof ClientLevel level)) return false;
        NpcAppearanceClient.State state = NpcAppearanceClient.get(owner.getUUID());
        if (state == null) return false;

        ProxyPlayer proxy = worldProxy(owner, level);
        proxy.owner = owner;
        syncWorldEntity(proxy, owner);
        StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, proxy).orElse(null);
        if (stats == null) return false;
        syncCharacter(proxy, stats.getCharacter(), state);
        boolean aura = NpcAuraClient.isActive(owner.getUUID());
        stats.getStatus().setHasCreatedCharacter(true);
        stats.getStatus().setAlive(owner.isAlive());
        stats.getStatus().setAuraActive(aura);
        stats.getStatus().setPermanentAura(aura);
        NpcCombatProfile visual = visualProfile(state);
        NATIVE_AURAS.put(owner.getUUID(), NpcAuraResolver.resolve(visual));
        stats.getStatus().setForceHalo(visual.haloOn);
        TransformSnapshot transform = syncTransformHold(stats, owner, state, partialTick);
        FormConfig.FormData activeForm = resolveActiveForm(state);
        FormConfig.FormData activeStack = resolveActiveStackForm(visual);
        AURA_FACTORS.put(stats, worldAuraFactor(owner, state));

        DMZPlayerRenderer renderer = DMZRendererCache.getTPRenderer(proxy);
        if (renderer == null) return false;
        pose.pushPose();
        int eyebrow = NpcDmzAppearance.sanitizeEyebrowType(
                state.appearance().eyebrowsType, state.appearance().eyesType);
        RENDER_CONTEXT.set(new RenderContext(stats, stats.getCharacter(), activeForm, activeStack,
                transform.targetForm(),
                transform.hasHold() ? transform.progress() : Float.NaN, eyebrow,
                tailColor(state.appearance())));
        try {
            float npcScale = Math.max(0.05f, NpcDisplayApply.getSize(owner) / 5.0f);
            pose.scale(npcScale, npcScale, npcScale);
            renderer.render(proxy, entityYaw, partialTick, pose, buffers, packedLight);
        } finally {
            RENDER_CONTEXT.remove();
            pose.popPose();
        }
        return true;
    }

    /** Draws the current draft through DMZ's native model and optional GUI aura renderer. */
    public static boolean renderPreview(LivingEntity owner, GuiGraphics graphics, int x, int y,
                                        int scale, float yaw, float pitch, float partialTick,
                                        boolean showAura) {
        if (owner == null || graphics == null || !(owner.level() instanceof ClientLevel level)) return false;
        NpcAppearanceClient.State state = NpcAppearanceClient.get(owner.getUUID());
        if (state == null) return false;

        ProxyPlayer proxy = previewProxy(owner, level);
        proxy.owner = owner;
        syncPreviewEntity(proxy, owner);
        StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, proxy).orElse(null);
        if (stats == null) return false;
        syncCharacter(proxy, stats.getCharacter(), state);
        stats.getStatus().setHasCreatedCharacter(true);
        stats.getStatus().setAlive(true);
        stats.getStatus().setActionCharging(false);
        stats.getStatus().setAuraActive(false);
        stats.getStatus().setPermanentAura(false);
        stats.getStatus().setForceHalo(visualProfile(state).haloOn);
        NATIVE_AURAS.put(proxy.getUUID(), NpcAuraResolver.resolve(visualProfile(state)));
        AURA_FACTORS.put(stats, state.auraScale());

        float oldBody = proxy.yBodyRot;
        float oldBodyO = proxy.yBodyRotO;
        float oldYaw = proxy.getYRot();
        float oldYawO = proxy.yRotO;
        float oldPitch = proxy.getXRot();
        float oldPitchO = proxy.xRotO;
        float oldHead = proxy.yHeadRot;
        float oldHeadO = proxy.yHeadRotO;
        boolean oldPreviewMode = DMZSkinLayer.PREVIEW_MODE;

        proxy.yBodyRot = yaw;
        proxy.yBodyRotO = yaw;
        proxy.setYRot(yaw);
        proxy.yRotO = yaw;
        proxy.setXRot(pitch);
        proxy.xRotO = pitch;
        proxy.yHeadRot = yaw;
        proxy.yHeadRotO = yaw;

        Quaternionf poseRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf().rotateX(0.0f);
        poseRotation.mul(cameraRotation);
        Minecraft mc = Minecraft.getInstance();
        Matrix4f projection = new Matrix4f().ortho(0.0f, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), 0.0f, -10000.0f, 10000.0f);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 320.0);
        int eyebrow = NpcDmzAppearance.sanitizeEyebrowType(
                state.appearance().eyebrowsType, state.appearance().eyesType);
        NpcCombatProfile visual = visualProfile(state);
        RENDER_CONTEXT.set(new RenderContext(stats, stats.getCharacter(), resolveActiveForm(state),
                resolveActiveStackForm(visual), null, Float.NaN, eyebrow,
                tailColor(state.appearance())));
        DMZSkinLayer.PREVIEW_MODE = true;
        try {
            EntityPreviewRenderContext.renderEntityInInventory(graphics, x, y, scale,
                    new Vector3f(), poseRotation, cameraRotation, proxy);
            if (showAura) {
                stats.getStatus().setAuraActive(true);
                stats.getStatus().setPermanentAura(true);
                AuraRenderer.renderGuiAura(proxy, graphics.pose(), projection, x, y, scale, partialTick, true);
            }
        } finally {
            stats.getStatus().setAuraActive(false);
            stats.getStatus().setPermanentAura(false);
            DMZSkinLayer.PREVIEW_MODE = oldPreviewMode;
            RENDER_CONTEXT.remove();
            graphics.pose().popPose();
            proxy.yBodyRot = oldBody;
            proxy.yBodyRotO = oldBodyO;
            proxy.setYRot(oldYaw);
            proxy.yRotO = oldYawO;
            proxy.setXRot(oldPitch);
            proxy.xRotO = oldPitchO;
            proxy.yHeadRot = oldHead;
            proxy.yHeadRotO = oldHeadO;
        }
        return true;
    }

    /** Called only by the AuraRenderer mixin; ordinary DMZ players have no registered factor. */
    public static float auraFactor(StatsData stats) {
        if (stats == null) return 1.0f;
        Float factor = AURA_FACTORS.get(stats);
        return factor == null || !Float.isFinite(factor) ? 1.0f : Math.max(0.05f, factor);
    }

    /** Saved layer/effect choices for the synthetic player processed by AuraRenderer. */
    public static NpcAuraResolver.Resolved nativeAura(UUID playerId) {
        return playerId == null ? null : NATIVE_AURAS.get(playerId);
    }

    /** Called only while the synthetic NPC renderer is active. */
    public static int eyebrowOverride() {
        RenderContext context = RENDER_CONTEXT.get();
        return context == null ? -1 : context.eyebrowType();
    }

    /** Exact configured NPC target used by DMZ layers and its player animation predicate. */
    public static FormConfig.FormData transformTarget(StatsData stats) {
        RenderContext context = RENDER_CONTEXT.get();
        return context != null && context.stats() == stats ? context.targetForm() : null;
    }

    /** True only for the synthetic character currently being rendered through DMZ. */
    public static boolean isRenderingCharacter(Character character) {
        RenderContext context = RENDER_CONTEXT.get();
        return context != null && context.character() == character;
    }

    /** Exact group/form-resolved data used by DMZ model, skin, scale and hair layers. */
    public static FormConfig.FormData activeForm(Character character) {
        RenderContext context = RENDER_CONTEXT.get();
        return context != null && context.character() == character ? context.activeForm() : null;
    }

    /** Exact stack group/form-resolved data used by DMZ model, skin, scale and hair layers. */
    public static FormConfig.FormData activeStackForm(Character character) {
        RenderContext context = RENDER_CONTEXT.get();
        return context != null && context.character() == character ? context.activeStackForm() : null;
    }

    /** Null keeps DMZ's native race/body/form inheritance; non-null is the NPC override. */
    public static float[] tailColorOverride() {
        RenderContext context = RENDER_CONTEXT.get();
        return context == null ? null : context.tailColor();
    }

    /** DMZ keeps Cell/Frieza tails inside the race model rather than its Saiyan tail layer. */
    public static boolean isEmbeddedRaceTailBone(String boneName) {
        RenderContext context = RENDER_CONTEXT.get();
        if (context == null || context.character() == null || boneName == null) return false;
        String race = context.character().getRaceName();
        race = race == null ? "" : race.trim().toLowerCase(java.util.Locale.ROOT);
        String bone = boneName.toLowerCase(java.util.Locale.ROOT);
        if (race.equals("bioandroid") || race.equals("cell")) {
            return bone.equals("cola") || numberedTailBone(bone);
        }
        if (race.equals("frostdemon") || race.equals("frieza") || race.equals("arcosian")) {
            return numberedTailBone(bone);
        }
        return false;
    }

    /** Authoritative server hold fraction used in place of DMZ's mastery-based hair timer. */
    public static float hairTransitionFactor(float original) {
        RenderContext context = RENDER_CONTEXT.get();
        if (context == null || !Float.isFinite(context.transitionProgress())) return original;
        return Math.max(0.0f, Math.min(1.0f, context.transitionProgress()));
    }

    private static float worldAuraFactor(LivingEntity owner, NpcAppearanceClient.State state) {
        // AuraRenderer already includes DMZ's resolved race/form model scaling. Only add
        // CustomNPC Display size and the explicit NPC aura-scale setting here.
        float npcSize = Math.max(0.05f, NpcDisplayApply.getSize(owner) / 5.0f);
        return npcSize * state.auraScale();
    }

    private static TransformSnapshot syncTransformHold(StatsData stats, LivingEntity owner,
                                                       NpcAppearanceClient.State state,
                                                       float partialTick) {
        NpcTransformHairClient.Hold hold = NpcTransformHairClient.get(owner.getUUID());
        float progress = hold == null ? 0.0f
                : hold.progress(owner.level().getGameTime(), partialTick);
        stats.getResources().setActionCharge(Math.round(progress * 100.0f));
        boolean transforming = hold != null && hold.active()
                && !hold.toGroup().isBlank() && !hold.toForm().isBlank();
        stats.getStatus().setActionCharging(transforming);
        if (transforming) {
            stats.getStatus().setSelectedAction(hold.stack() ? ActionMode.STACK : ActionMode.FORM);
            stats.getCharacter().setSelectedFormGroup(hold.toGroup());
            stats.getCharacter().setSelectedForm(hold.toForm());
        }
        FormConfig.FormData target = hold == null ? null
                : (hold.stack()
                    ? NpcFormLookup.stackForm(hold.toGroup(), hold.toForm())
                    : NpcFormLookup.form(state.race(), hold.toGroup(), hold.toForm()));
        return new TransformSnapshot(target, progress, hold != null);
    }

    static void clearCache() {
        WORLD_PROXIES.clear();
        PREVIEW_PROXIES.clear();
        AURA_FACTORS.clear();
        NATIVE_AURAS.clear();
        RENDER_CONTEXT.remove();
    }

    private static ProxyPlayer worldProxy(LivingEntity owner, ClientLevel level) {
        return WORLD_PROXIES.compute(owner.getUUID(), (id, current) -> {
            if (current != null && current.level() == level) return current;
            return new ProxyPlayer(level, new GameProfile(id, owner.getName().getString()));
        });
    }

    private static ProxyPlayer previewProxy(LivingEntity owner, ClientLevel level) {
        return PREVIEW_PROXIES.compute(owner.getUUID(), (id, current) -> {
            if (current != null && current.level() == level) return current;
            UUID previewUuid = UUID.nameUUIDFromBytes(
                    ("xenopixels:dmz-preview:" + id).getBytes(StandardCharsets.UTF_8));
            ProxyPlayer created = new ProxyPlayer(level, new GameProfile(previewUuid, "NPC Preview"));
            created.setId(NEXT_PREVIEW_ID.getAndDecrement());
            return created;
        });
    }

    private static void syncWorldEntity(ProxyPlayer to, LivingEntity from) {
        // DMZ's deferred aura queue de-duplicates by entity id. Give every proxy its
        // owning NPC's id so multiple Full-mode NPCs keep independent aura/spark entries.
        to.setId(from.getId());
        to.setPos(from.getX(), from.getY(), from.getZ());
        to.xo = from.xo;
        to.yo = from.yo;
        to.zo = from.zo;
        to.tickCount = from.tickCount;
        to.yHeadRotO = from.yHeadRotO;
        to.yHeadRot = from.yHeadRot;
        to.yBodyRotO = from.yBodyRotO;
        to.yBodyRot = from.yBodyRot;
        to.xRotO = from.xRotO;
        to.setXRot(from.getXRot());
        to.setYRot(from.getYRot());
        to.setDeltaMovement(from.getDeltaMovement());
        to.setSprinting(from.isSprinting());
        to.setSwimming(from.isSwimming());
        to.setShiftKeyDown(from.isShiftKeyDown());
        to.setOnGround(from.onGround());
    }

    private static void syncPreviewEntity(ProxyPlayer to, LivingEntity from) {
        to.setPos(from.getX(), from.getY(), from.getZ());
        to.xo = from.getX();
        to.yo = from.getY();
        to.zo = from.getZ();
        to.tickCount = from.tickCount;
        to.setDeltaMovement(Vec3.ZERO);
        to.setSprinting(false);
        to.setSwimming(false);
        to.setShiftKeyDown(false);
        to.setOnGround(true);
    }

    private static void syncCharacter(ProxyPlayer proxy, Character character,
                                      NpcAppearanceClient.State state) {
        NpcDmzAppearance a = state.appearance();
        character.setRace(state.race().isBlank() ? "human" : state.race());
        character.setGender(a.gender);
        character.setCharacterClass(a.characterClass);
        character.setBodyType(a.bodyType);
        character.setEyesType(a.eyesType);
        character.setNoseType(a.noseType);
        character.setMouthType(a.mouthType);
        character.setTattooType(a.tattooType);
        character.setBoobScale(a.boobScale);
        character.setBodyColor(a.bodyColor);
        character.setBodyColor2(a.bodyColor2);
        character.setBodyColor3(a.bodyColor3);
        character.setEye1Color(a.eye1Color);
        character.setEye2Color(a.eye2Color);
        character.setHairColor(state.hairColor().isBlank() ? "#FFFFFF" : state.hairColor());
        character.setAuraColor(state.auraColor() == 0 ? "#FFFFFF" : NpcCombatProfile.formatHex(state.auraColor()));
        character.setActiveHeadBone(a.activeHeadBone);
        character.setHasSaiyanTail(a.saiyanTail);
        character.setRenderHairBase(a.renderHairBase);

        String hairCode = state.hairCode() == null ? "" : state.hairCode().trim();
        if (!Objects.equals(proxy.syncedHairCode, hairCode)) {
            CustomHair[] hair = NpcHairVis.parseSet(hairCode);
            character.setHairBase(copy(hair, 0));
            character.setHairSSJ(copy(hair, 1));
            character.setHairSSJ2(copy(hair, 2));
            character.setHairSSJ3(copy(hair, 3));
            proxy.syncedHairCode = hairCode;
        }
        if (state.formGroup().isBlank() || state.form().isBlank()) character.clearActiveForm();
        else character.setActiveForm(state.formGroup(), state.form());
        NpcCombatProfile visual = visualProfile(state);
        if (visual.stackGroup.isBlank() || visual.stackId.isBlank()) character.clearActiveStackForm();
        else character.setActiveStackForm(visual.stackGroup, visual.stackId);
        if (!visual.auraColorHex.isBlank()) character.setAuraColor(visual.auraColorHex);
    }

    static NpcCombatProfile visualProfile(NpcAppearanceClient.State state) {
        NpcCombatProfile profile = new NpcCombatProfile();
        if (state != null) {
            profile.applyVisualOptions(state.visualOptions());
            // visualOptions carries the editable aura styles, but the authoritative active
            // race/form live in the appearance packet's top-level fields.  Without copying
            // them here NpcAuraResolver always resolved the base Human aura even while the
            // proxy model itself was transformed.
            profile.raceId = state.race();
            profile.formGroup = state.formGroup();
            profile.formId = state.form();
            profile.auraColor = state.auraColor();
            if (profile.auraColorHex.isBlank() && state.auraColor() != 0) {
                profile.auraColorHex = NpcCombatProfile.formatHex(state.auraColor());
            }
            profile.auraScale = state.auraScale();
        }
        return profile;
    }

    private static FormConfig.FormData resolveActiveForm(NpcAppearanceClient.State state) {
        if (state == null || state.formGroup().isBlank() || state.form().isBlank()) return null;
        return NpcFormLookup.form(state.race(), state.formGroup(), state.form());
    }

    private static FormConfig.FormData resolveActiveStackForm(NpcCombatProfile visual) {
        if (visual == null || visual.stackGroup.isBlank() || visual.stackId.isBlank()) return null;
        return NpcFormLookup.stackForm(visual.stackGroup, visual.stackId);
    }

    private static float[] tailColor(NpcDmzAppearance appearance) {
        if (appearance == null || appearance.tailColor == null || appearance.tailColor.isBlank()) {
            return null;
        }
        java.util.OptionalInt parsed = NpcCombatProfile.parseHexColor(appearance.tailColor);
        if (parsed.isEmpty()) return null;
        int rgb = parsed.getAsInt();
        return new float[]{((rgb >>> 16) & 0xFF) / 255.0f,
                ((rgb >>> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f};
    }

    private static boolean numberedTailBone(String bone) {
        if (!bone.startsWith("tail") || bone.length() == 4) return false;
        for (int i = 4; i < bone.length(); i++) {
            if (!java.lang.Character.isDigit(bone.charAt(i))) return false;
        }
        return true;
    }

    private static CustomHair copy(CustomHair[] hair, int index) {
        if (hair == null || hair.length == 0 || hair[0] == null) return new CustomHair();
        CustomHair selected = index < hair.length && hair[index] != null ? hair[index] : hair[0];
        return selected == null ? new CustomHair() : selected.copy();
    }
}
