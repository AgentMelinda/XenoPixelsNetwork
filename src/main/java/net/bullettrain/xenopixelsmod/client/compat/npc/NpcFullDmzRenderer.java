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
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcSkillSet;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDisplayApply;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final AtomicBoolean ANIMATION_DELIVERY_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean RENDER_FAILURE_LOGGED = new AtomicBoolean();
    private static final Map<UUID, Integer> LAST_PROXY_IDENTITY = new ConcurrentHashMap<>();

    private record RenderContext(StatsData stats, Character character,
                                 FormConfig.FormData activeForm,
                                 FormConfig.FormData activeStackForm,
                                 FormConfig.FormData targetForm,
                                 float transitionProgress, int eyebrowType,
                                 float[] tailColor) {}

    private record TransformSnapshot(FormConfig.FormData targetForm, float progress,
                                     boolean hasHold) {}

    /**
     * True for the stand-in player a native Xeno NPC is drawn through. Its name would otherwise be
     * drawn by the player renderer at player height, over tall DMZ hair; the Xeno NPC renderer
     * draws the real nameplate (name, title, raised) instead.
     */
    public static boolean isXenoNpcProxy(net.minecraft.world.entity.Entity entity) {
        return entity instanceof ProxyPlayer proxy
                && proxy.owner instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
    }

    private static final class ProxyPlayer extends AbstractClientPlayer {
        LivingEntity owner;
        String syncedHairCode;
        String syncedSkinName = "";
        int lastCopyStatsTick = Integer.MIN_VALUE;

        @Override
        public net.minecraft.client.resources.PlayerSkin getSkin() {
            return owner instanceof AbstractClientPlayer player ? player.getSkin() : super.getSkin();
        }

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

    /** Combat hair-yaw swap is local-player mash only; never apply it to a Full NPC proxy. */
    public static boolean isNpcProxy(Object player) {
        return player instanceof ProxyPlayer;
    }

    /**
     * DMZ {@code processGhostAuras} looks up the shared CustomNPC entity id and evicts
     * {@code AURA_CACHE} when that entity is not a {@code Player}. Hand back the
     * synthetic proxy so a Full aura-on NPC keeps its fade while the camera orbits.
     */
    public static Player auraPlayerForEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return null;
        }
        if (!isFull(living) || !NpcAuraClient.isActive(living.getUUID())) {
            return null;
        }
        return WORLD_PROXIES.get(living.getUUID());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean render(LivingEntity owner, float entityYaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int packedLight) {
        return renderSafely(() -> renderUnsafe(owner, entityYaw, partialTick, pose, buffers, packedLight));
    }

    @FunctionalInterface
    interface RenderOperation {
        boolean render();
    }

    static boolean renderSafely(RenderOperation operation) {
        if (operation == null) {
            return false;
        }
        try {
            return operation.render();
        } catch (RuntimeException | LinkageError failure) {
            // Optional NPC/DMZ state is untrusted input. Returning false lets the host renderer
            // continue instead of cancelling it after a malformed hair/form payload.
            if (RENDER_FAILURE_LOGGED.compareAndSet(false, true)) {
                XenoPixelsMod.LOGGER.warn("XenoPixels NPC compatibility render failed; using host renderer", failure);
            }
            return false;
        }
    }

    private static boolean renderUnsafe(LivingEntity owner, float entityYaw, float partialTick,
                                        PoseStack pose, MultiBufferSource buffers, int packedLight) {
        if (!isFull(owner) || !(owner.level() instanceof ClientLevel level)) return false;
        NpcAppearanceClient.State state = NpcAppearanceClient.get(owner.getUUID());
        if (state == null) return false;

        ProxyPlayer proxy = worldProxy(owner, level, state);
        proxy.owner = owner;
        syncWorldEntity(proxy, owner);
        playPendingAnimation(proxy, owner);
        StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, proxy).orElse(null);
        if (stats == null) return false;
        syncCharacter(proxy, stats.getCharacter(), state);
        boolean aura = NpcAuraClient.isActive(owner.getUUID());
        stats.getStatus().setHasCreatedCharacter(true);
        stats.getStatus().setAlive(owner.isAlive());
        stats.getStatus().setAuraActive(aura);
        stats.getStatus().setPermanentAura(aura);
        NpcCombatProfile visual = visualProfile(state);
        boolean flyingNow = liveFlight(owner, visual);
        applyFlyPose(proxy, owner, flyingNow, partialTick);
        NATIVE_AURAS.put(owner.getUUID(), NpcAuraResolver.resolve(visual));
        stats.getStatus().setForceHalo(visual.haloOn);
        stats.getSkills().removeAllSkills();
        syncKiWeapon(stats, visual);
        syncFlySkill(stats, visual, flyingNow);
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
            // CustomNPCs stores its Display size on its own object, which NpcDisplayApply reads by
            // reflection. A native Xeno NPC has no such object, so getSize answers 0 and the old
            // max(0.05, 0/5) shrank it to a twentieth of its size. Size 0 means "unset", not
            // "tiny": it renders at 1.0, which is also what DMZ's own size 5 works out to.
            float npcScale = NpcDisplayApply.sizeScale(owner);
            pose.scale(npcScale, npcScale, npcScale);
            MultiBufferSource faded = net.bullettrain.xenopixelsmod.client.combat.CombatBodyFade.wrapHakai(
                    buffers, owner, partialTick);
            net.bullettrain.xenopixelsmod.client.combat.CombatBodyFade.begin(owner);
            try {
                float renderYaw = flyingNow ? proxy.yBodyRot : entityYaw;
                renderer.render(proxy, renderYaw, partialTick, pose, faded, packedLight);
            } finally {
                net.bullettrain.xenopixelsmod.client.combat.CombatBodyFade.end();
            }
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

        ProxyPlayer proxy = previewProxy(owner, level, state);
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
        NpcCombatProfile visual = visualProfile(state);
        stats.getStatus().setForceHalo(visual.haloOn);
        stats.getSkills().removeAllSkills();
        syncKiWeapon(stats, visual);
        syncFlySkill(stats, visual, visual.flySkillOn);
        NATIVE_AURAS.put(proxy.getUUID(), NpcAuraResolver.resolve(visual));
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

    private static void syncKiWeapon(StatsData stats, NpcCombatProfile visual) {
        stats.getSkills().registerDefaultSkill("kimanipulation", 1);
        stats.getSkills().setSkillActive("kimanipulation", visual.kiWeaponOn);
        stats.getStatus().setKiWeaponType(
                NpcCombatProfile.canonicalKiWeaponType(visual.kiWeaponType));
    }

    /**
     * Mirrors {@link #syncKiWeapon}'s contract for DMZ's fly skill. {@code Skills.setSkillActive}
     * silently no-ops when the skill was never registered on this Skills instance, and a
     * synthetic proxy never went through DMZ's login registration, so registerDefaultSkill has
     * to come first. It is idempotent -- an existing entry keeps its level and active flag and
     * only has maxLevel rewritten.
     *
     * <p>What this buys: {@code DMZPlayerRenderer.render} applies DMZ's flight pitch/roll pose
     * when {@code FlySkillEvent.isFlyingFast} is true, which for a non-local player is exactly
     * "fly skill active, flight mode != 1, and moving faster than 0.55 blocks/tick".
     */
    private static void syncFlySkill(StatsData stats, NpcCombatProfile visual, boolean flying) {
        // Fly keeps its own dedicated fields as well as living in the skill map, because it
        // also drives CustomNPCs' navigator through NpcFlightBridge. The ACTIVE flag follows the
        // live flight state, not the authored toggle: DMZ reads it for the flight pose, so an
        // NPC that has landed with Fly still enabled stands like a player who released F.
        applySkill(stats, NpcSkillSet.FLY, flying,
                NpcCombatProfile.clampFlySkillLevel(visual.flySkillLevel));
        for (java.util.Map.Entry<String, NpcSkillSet.Entry> entry : visual.skills.entries().entrySet()) {
            if (NpcSkillSet.FLY.equals(entry.getKey())) {
                continue; // handled above, and the dedicated fields win
            }
            if (NpcCombatProfile.KI_WEAPON_SKILL.equals(entry.getKey())) {
                continue; // syncKiWeapon: the KI Weapon toggle alone decides whether it shows
            }
            applySkill(stats, entry.getKey(), entry.getValue().active(), entry.getValue().level());
        }
    }

    /**
     * Registers a DMZ skill before touching it. {@code Skills.setSkillActive} looks the skill up
     * in an internal map and silently no-ops when it is absent, and a synthetic NPC proxy never
     * went through DMZ's login-time registration. {@code registerDefaultSkill} is idempotent
     * -- an existing entry keeps its level and active flag and only has maxLevel rewritten.
     */
    private static void applySkill(StatsData stats, String id, boolean active, int level) {
        // Fly is the one id this mod owns outright -- it also drives NPC navigation, so it must
        // survive even when DMZ's skills config has no entry for it. Every other id has to come
        // from DMZ's own config: registering an unknown one would leave a phantom entry in the
        // synthetic proxy's skill map that DMZ can never resolve.
        if (!NpcSkillSet.FLY.equals(id) && !NpcSkillSet.isKnown(id)) {
            return;
        }
        int maxLevel = Math.max(1, NpcSkillSet.maxLevelOf(id));
        stats.getSkills().registerDefaultSkill(id, maxLevel);
        stats.getSkills().setSkillLevel(id, Math.max(1, Math.min(maxLevel, level)));
        stats.getSkills().setSkillActive(id, active);
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

    /**
     * Whether a bone is a tail, on any race.
     *
     * <p>Race-agnostic on purpose. This used to be a list — Cell and Frieza tails by race name,
     * the Saiyan tail by the single bone name {@code tailenrolled} — and the result was that any
     * race not on the list, or any tail bone named slightly differently, silently ignored the
     * NPC's tail colour. Matching the bone rather than the race removes that whole class of bug
     * instead of adding one more entry to a list.
     *
     * <p>Deliberately exact rather than a prefix test: {@code tailcoat} is not a tail.
     */
    public static boolean isTailBone(String boneName) {
        if (boneName == null) return false;
        String bone = boneName.trim().toLowerCase(java.util.Locale.ROOT);
        return bone.equals("tail") || bone.equals("cola") || bone.equals("tailenrolled")
                || numberedTailBone(bone);
    }

    /** DMZ keeps Cell/Frieza tails inside the race model rather than its Saiyan tail layer. */
    public static boolean isEmbeddedRaceTailBone(String boneName) {
        RenderContext context = RENDER_CONTEXT.get();
        if (context == null || context.character() == null) return false;
        return isTailBone(boneName);
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
        // Shared helper: a native Xeno NPC has no CustomNPCs display, so getSize answers 0 and
        // the old max(0.05, 0/5) shrank the aura to a twentieth of its size.
        return NpcDisplayApply.sizeScale(owner, NpcAppearanceClient.renderProfile(owner)) * state.auraScale();
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

    public static void clearCache() {
        WORLD_PROXIES.clear();
        for (UUID id : java.util.List.copyOf(COPY_PROXIES.keySet())) forgetCopy(id);
        PREVIEW_PROXIES.clear();
        AURA_FACTORS.clear();
        NATIVE_AURAS.clear();
        LAST_PROXY_IDENTITY.clear();
        RENDER_CONTEXT.remove();
    }

    public static void forgetWorld(java.util.UUID id) {
        if (id != null) {
            WORLD_PROXIES.remove(id);
            NATIVE_AURAS.remove(id);
            LAST_PROXY_IDENTITY.remove(id);
        }
    }

    private static ProxyPlayer worldProxy(LivingEntity owner, ClientLevel level,
                                          NpcAppearanceClient.State state) {
        String skinName = skinName(state);
        return WORLD_PROXIES.compute(owner.getUUID(), (id, current) -> {
            // GameProfile is fixed at construction; rebuild when the scripted skin changes.
            if (current != null && current.level() == level
                    && Objects.equals(current.syncedSkinName, skinName)) return current;
            GameProfile skin = skinProfile(state);
            ProxyPlayer created = new ProxyPlayer(level,
                    skin != null ? skin : new GameProfile(id, owner.getName().getString()));
            created.syncedSkinName = skinName;
            return created;
        });
    }

    private static ProxyPlayer previewProxy(LivingEntity owner, ClientLevel level,
                                            NpcAppearanceClient.State state) {
        String skinName = skinName(state);
        return PREVIEW_PROXIES.compute(owner.getUUID(), (id, current) -> {
            if (current != null && current.level() == level
                    && Objects.equals(current.syncedSkinName, skinName)) return current;
            GameProfile skin = skinProfile(state);
            if (skin == null) {
                UUID previewUuid = UUID.nameUUIDFromBytes(
                        ("xenopixels:dmz-preview:" + id).getBytes(StandardCharsets.UTF_8));
                skin = new GameProfile(previewUuid, "NPC Preview");
            }
            ProxyPlayer created = new ProxyPlayer(level, skin);
            created.syncedSkinName = skinName;
            created.setId(NEXT_PREVIEW_ID.getAndDecrement());
            return created;
        });
    }

    /** Scripted player-skin name, blank when the NPC keeps its own profile. */
    private static String skinName(NpcAppearanceClient.State state) {
        String name = state == null ? null : state.skinPlayer();
        return name == null ? "" : name.trim();
    }

    /** GameProfile for the scripted skin, or null to keep the NPC's own profile. */
    private static GameProfile skinProfile(NpcAppearanceClient.State state) {
        String name = skinName(state);
        if (name.isBlank()) {
            return null;
        }
        UUID uuid = null;
        String uuidText = state.skinUuid();
        if (uuidText != null && !uuidText.isBlank()) {
            try {
                uuid = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (uuid == null) {
            // Deterministic placeholder until the server resolves the real UUID.
            uuid = UUID.nameUUIDFromBytes(
                    ("xenopixels:npc-skin:" + name).getBytes(StandardCharsets.UTF_8));
        }
        return new GameProfile(uuid, name);
    }

    /**
     * Hands the proxy any clip the server queued for this NPC.
     *
     * <p>Exactly once per delivery. DragonMineZ reads {@code dragonminez$currentMeleeAnim}, calls
     * {@code forceAnimationReset()} and clears it, so re-setting the same clip every frame would
     * restart the animation every frame and the NPC would never get past its first pose - which
     * is precisely the bug that made the player's own mash flicker.
     */
    private static void playPendingAnimation(AbstractClientPlayer proxy, LivingEntity owner) {
        NpcAnimationClient.Pending pending = NpcAnimationClient.peek(owner.getUUID());
        boolean queued = pending != null;
        if (pending == null) {
            int identity = System.identityHashCode(proxy);
            Integer last = LAST_PROXY_IDENTITY.get(owner.getUUID());
            if (last != null && last == identity) {
                return;
            }
            pending = NpcAnimationClient.activeHold(owner.getUUID());
            if (pending == null) {
                LAST_PROXY_IDENTITY.put(owner.getUUID(), identity);
                return;
            }
        }
        try {
            // Declared as AbstractClientPlayer rather than ProxyPlayer on purpose: DragonMineZ adds
            // IPlayerAnimatable by mixin, so javac can prove a final class does not implement it and
            // rejects the instanceof outright.
            if (proxy instanceof com.dragonminez.client.animation.IPlayerAnimatable animatable) {
                NpcAnimationClient.apply(proxy, animatable, pending);
                if (pending.hold() && !pending.stop()) {
                    net.bullettrain.xenopixelsmod.client.combat.ScriptAnimSpeedClient.put(
                            owner.getUUID(), pending.speed());
                }
                if (queued) {
                    NpcAnimationClient.consume(owner.getUUID(), pending);
                    NpcAnimationClient.xeno$traceOnce("drained by the Full renderer",
                            pending.animation());
                }
                LAST_PROXY_IDENTITY.put(owner.getUUID(), System.identityHashCode(proxy));
            } else if (ANIMATION_DELIVERY_FAILURE_LOGGED.compareAndSet(false, true)) {
                XenoPixelsMod.LOGGER.warn(
                        "Full DMZ NPC proxy does not implement IPlayerAnimatable; retaining {} for retry",
                        pending.animation());
            }
        } catch (Throwable failure) {
            if (ANIMATION_DELIVERY_FAILURE_LOGGED.compareAndSet(false, true)) {
                XenoPixelsMod.LOGGER.warn(
                        "Could not deliver Full DMZ NPC animation {}; retaining it for retry",
                        pending.animation(), failure);
            }
        }
    }

    /**
     * Renders {@code copy} — a Shi Shin No Ken body or a Zanzoken image — wearing {@code owner}'s
     * real DragonMineZ appearance.
     *
     * <p>Far simpler than the NPC path above, and for one reason: an NPC has no DragonMineZ
     * character, so {@code syncCharacter} has to invent one field by field. A copy's owner is a
     * player who already has the real thing, so the proxy is handed the owner's whole
     * {@code StatsData} and inherits hair, body type, race parts, colours, form and aura state in
     * a single call.
     *
     * <p>The proxy is keyed on the <em>copy's</em> UUID, not the owner's, and takes the copy's
     * entity id in {@link #syncWorldEntity}. That is what makes four bodies four identities:
     * DragonMineZ's deferred aura queue de-duplicates by entity id and GeckoLib keys animation
     * state per instance, so sharing one identity would collapse them into a single aura and a
     * single frozen pose — which is exactly what happened when copies were drawn by re-rendering
     * the owner.
     *
     * <p>Deliberately sets no {@link #RENDER_CONTEXT} and writes no entry to {@link #NATIVE_AURAS}
     * or {@link #AURA_FACTORS}: those override appearance for NPCs that have no real data, and a
     * copy has real data, so the native player path should apply.
     *
     * @return true when the copy was drawn; false means the caller should fall back
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean renderPlayerCopy(Player owner, LivingEntity copy, float entityYaw,
                                           float partialTick, PoseStack pose,
                                           MultiBufferSource buffers, int packedLight) {
        if (owner == null || copy == null || !(copy.level() instanceof ClientLevel level)) return false;

        StatsData ownerStats = StatsProvider.get(StatsCapability.INSTANCE, owner).orElse(null);
        if (ownerStats == null) return false;

        ProxyPlayer proxy = copyProxy(owner, copy, level);
        proxy.owner = owner;

        StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, proxy).orElse(null);
        if (stats == null) return false;
        // Whole-stats copy rather than field-by-field. Throttled because doing it per copy per
        // frame is pure waste — appearance changes on transform, not on every frame.
        if (copyStatsRefreshDue(copy.tickCount, proxy.lastCopyStatsTick)) {
            stats.copyFrom(ownerStats);
            proxy.lastCopyStatsTick = copy.tickCount;
        }
        stats.getStatus().setAlive(copy.isAlive());
        // Aura is the exception to the throttle above. Appearance changes on transform, which is why
        // a second between refreshes is fine for it -- but an aura goes on and off constantly, and at
        // a twenty-tick refresh a multi-form copy kept blazing for up to a full second after its
        // owner powered down. On screen that reads as the copies having taken the owner's aura.
        copyAuraState(stats.getStatus(), ownerStats.getStatus());

        syncWorldEntity(proxy, copy);
        syncArmor(proxy, owner);
        playPendingAnimation(proxy, copy);

        DMZPlayerRenderer renderer = DMZRendererCache.getTPRenderer(proxy);
        if (renderer == null) return false;
        try {
            renderer.render(proxy, entityYaw, partialTick, copyRenderPose(pose), buffers, packedLight);
        } catch (Throwable t) {
            // Isolating the stack also protects fallback rendering after an unbalanced failure.
            return false;
        }
        return true;
    }

    static PoseStack copyRenderPose(PoseStack source) {
        PoseStack isolated = new PoseStack();
        isolated.mulPose(source.last().pose());
        isolated.last().normal().set(source.last().normal());
        return isolated;
    }

    /**
     * The four flags that decide whether an aura is drawn at all, refreshed every frame.
     *
     * <p>Four setters rather than another {@code copyFrom}: the whole-stats copy is what the
     * throttle exists to avoid, and these are the only fields that change fast enough to be seen
     * lagging.
     */
    private static void copyAuraState(com.dragonminez.common.stats.character.Status to,
                                      com.dragonminez.common.stats.character.Status from) {
        to.setAuraActive(from.isAuraActive());
        to.setPermanentAura(from.isPermanentAura());
        to.setActionCharging(from.isActionCharging());
        to.setChargingKi(from.isChargingKi());
    }

    /** How often a copy's appearance is re-read from its owner. */
    private static final int COPY_STATS_REFRESH_TICKS = 20;

    static boolean copyStatsRefreshDue(int tick, int previous) {
        return previous == Integer.MIN_VALUE || tick < previous
                || (long) tick - previous >= COPY_STATS_REFRESH_TICKS;
    }

    /** Proxies for player copies, keyed on the copy so each body is its own identity. */
    private static final Map<UUID, ProxyPlayer> COPY_PROXIES = new ConcurrentHashMap<>();

    private static ProxyPlayer copyProxy(Player owner, LivingEntity copy, ClientLevel level) {
        return COPY_PROXIES.compute(copy.getUUID(), (id, current) -> {
            if (current != null && current.level() == level) return current;
            if (current != null) evictCopyRenderer(id);
            // Render UUID belongs to the copy; getSkin delegates to the actual skin owner.
            GameProfile profile = new GameProfile(id, owner.getGameProfile().getName());
            profile.getProperties().putAll(owner.getGameProfile().getProperties());
            ProxyPlayer fresh = new ProxyPlayer(level, profile);
            // Stamp the id at construction, not only in syncWorldEntity. A proxy is cached the
            // moment it is built, and renderPlayerCopy can return before syncWorldEntity runs, which
            // would leave a cached proxy carrying whatever Entity's own counter handed it. DMZ's
            // aura queue de-duplicates by entity id, so a proxy holding an id that collides with a
            // real player's suppresses that player's aura for the frame -- and the first-person aura
            // is drawn after the third-person pass, so the owner is the one who loses it.
            fresh.setId(copy.getId());
            return fresh;
        });
    }

    /** Drops a copy's proxy once that body is gone. */
    public static void forgetCopy(UUID copyId) {
        if (copyId != null && COPY_PROXIES.remove(copyId) != null) evictCopyRenderer(copyId);
    }

    private static final AtomicBoolean COPY_EVICTION_FAILURE_LOGGED = new AtomicBoolean();

    private static void evictCopyRenderer(UUID id) {
        // DMZ 2.1.3 has no per-identity eviction API. Never clear unrelated player renderers.
        try {
            var field = DMZRendererCache.class.getDeclaredField("TP_RENDERERS");
            field.setAccessible(true);
            if (field.get(null) instanceof Map<?, ?> cache) cache.remove(id);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (COPY_EVICTION_FAILURE_LOGGED.compareAndSet(false, true))
                XenoPixelsMod.LOGGER.warn("Could not evict DMZ copy renderer; reload clears the dependency cache", failure);
        }
    }

    private static void syncWorldEntity(ProxyPlayer to, LivingEntity from) {
        // DMZ's deferred aura queue de-duplicates by entity id. Give every proxy its
        // owning NPC's id so multiple Full-mode NPCs keep independent aura/spark entries.
        to.setId(from.getId());
        to.setPos(from.getX(), from.getY(), from.getZ());
        to.xo = from.xo;
        to.yo = from.yo;
        to.zo = from.zo;
        // DMZ's remote fly-clip choice reads getX() - xOld (PlayerGeoAnimatableMixin
        // #resolveFlyAnimation, checked with javap against the pinned 2.1.3 jar), not x - xo. The
        // proxy is never ticked, so xOld stayed at the world spawn it was built at and the
        // "movement" DMZ measured was spawn -> NPC: FLY_LEFT/RIGHT/BACK depending on where the
        // NPC happened to be. That was the sideways, W+A / W+D look while chasing.
        to.xOld = from.xo;
        to.yOld = from.yo;
        to.zOld = from.zo;
        to.tickCount = from.tickCount;
        to.yHeadRotO = from.yHeadRotO;
        to.yHeadRot = from.yHeadRot;
        to.yBodyRotO = from.yBodyRotO;
        to.yBodyRot = from.yBodyRot;
        to.xRotO = from.xRotO;
        to.setXRot(from.getXRot());
        to.setYRot(from.getYRot());
        to.setDeltaMovement(from.getDeltaMovement());
        to.yRotO = from.yRotO;
        to.setSprinting(from.isSprinting());
        to.setSwimming(from.isSwimming());
        to.setShiftKeyDown(from.isShiftKeyDown());
        to.setOnGround(from.onGround());
        // Swing state, or the proxy never animates an attack. DragonMineZ decides whether to play
        // an attack clip with exactly `attackAnim > 0 || swinging || swingTime > 0`
        // (PlayerGeoAnimatableMixin#attackPredicate); all three are permanently zero on a freshly
        // built proxy, so a Full-mode NPC landed hits with its arms hanging still. The NPC's own
        // client entity has the live values already - vanilla broadcasts swing() and
        // updateSwingTime() runs in its tick - they were simply never read across.
        to.attackAnim = from.attackAnim;
        to.oAttackAnim = from.oAttackAnim;
        to.swinging = from.swinging;
        to.swingTime = from.swingTime;
        to.swingingArm = from.swingingArm;
        syncArmor(to, from);
    }

    /**
     * Live flight rather than the authored skill toggle. The combat brain clears gravity while it
     * owns the NPC's flight (claimFlight/land) and that flag is entity-synced to the client, so
     * the pose ends the moment the NPC touches down even with Fly left enabled in the editor -
     * the same contract as a player who lands while still in flight mode.
     */
    private static boolean liveFlight(LivingEntity owner, NpcCombatProfile visual) {
        return visual != null && visual.flySkillOn && owner.isNoGravity() && !owner.onGround();
    }

    static void applyFlyPose(AbstractClientPlayer to, LivingEntity from, boolean flySkillOn) {
        applyFlyPose(to, from, flySkillOn, 1.0f);
    }

    /**
     * Per-owner smoothed flight yaw and pitch, advanced every frame. The travel direction only
     * changes once per tick (x - xo is a per-tick delta), and the proxy's O-fields were set equal
     * to the new values, so the body stepped 20 times a second and flipped between "face travel"
     * and "face target" whenever speed crossed the fly epsilon. Weak keys: an unloaded NPC's
     * entry goes with it.
     */
    private static final Map<LivingEntity, FlyState> FLY_YAW = new java.util.WeakHashMap<>();
    /** Degrees per second the rendered flight body may turn. */
    private static final float FLY_TURN_RATE = 540.0f;

    private static final class FlyState {
        float yaw;
        float pitch;
        long lastNanos;
    }

    static void applyFlyPose(AbstractClientPlayer to, LivingEntity from, boolean flySkillOn,
                             float partialTick) {
        if (to == null || from == null) {
            return;
        }
        if (!flySkillOn) {
            FLY_YAW.remove(from);
            return;
        }
        NpcFlyPose.Snapshot snap = NpcFlyPose.forProxy(true,
                from.getX() - from.xo, from.getY() - from.yo, from.getZ() - from.zo,
                from.getDeltaMovement().x, from.getDeltaMovement().y, from.getDeltaMovement().z,
                from.yBodyRot, from.getXRot(), from.yRotO);
        // Moving: face the travel vector, so DMZ picks FLY_FRONT. Hovering: face where the
        // server turned the body (the target), interpolated like any other entity.
        float wantYaw = snap.travelLocked() ? snap.yBodyRot()
                : net.minecraft.util.Mth.rotLerp(partialTick, from.yBodyRotO, from.yBodyRot);
        float wantPitch = snap.travelLocked() ? snap.xRot() : 0.0f;
        long now = System.nanoTime();
        FlyState state = FLY_YAW.get(from);
        if (state == null) {
            state = new FlyState();
            state.yaw = wantYaw;
            state.pitch = wantPitch;
            state.lastNanos = now;
            FLY_YAW.put(from, state);
        }
        float seconds = Math.min(0.1f, Math.max(0.0f, (now - state.lastNanos) / 1.0e9f));
        state.lastNanos = now;
        float yaw = NpcFlyPose.slew(state.yaw, wantYaw, FLY_TURN_RATE * seconds);
        float pitch = NpcFlyPose.slew(state.pitch, wantPitch, FLY_TURN_RATE * 0.5f * seconds);
        state.yaw = yaw;
        state.pitch = pitch;
        to.yBodyRot = yaw;
        to.yBodyRotO = yaw;
        to.setYRot(yaw);
        to.yRotO = yaw;
        to.setXRot(pitch);
        to.xRotO = pitch;
        to.yHeadRot = yaw;
        to.yHeadRotO = yaw;
        if (snap.travelLocked()) {
            double[] forward = NpcFlyPose.frontClipDelta(yaw,
                    from.getX() - from.xo, from.getZ() - from.zo,
                    from.getDeltaMovement().x, from.getDeltaMovement().z);
            to.xOld = to.getX() - forward[0];
            to.zOld = to.getZ() - forward[1];
        }
    }

    /** Deliberately no swing state: the appearance preview should stand still and pose. */
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
        syncArmor(to, from);
    }

    /**
     * DMZ's armor layer reads {@code AbstractClientPlayer.getInventory().armor} directly
     * rather than {@code getItemBySlot}, so {@link ProxyPlayer}'s read-only override is
     * never consulted for rendering. Mirror the NPC's real armor into the proxy's own
     * inventory every sync so equipped items actually show.
     */
    private static void syncArmor(ProxyPlayer to, LivingEntity from) {
        to.setItemSlot(EquipmentSlot.HEAD, from.getItemBySlot(EquipmentSlot.HEAD));
        to.setItemSlot(EquipmentSlot.CHEST, from.getItemBySlot(EquipmentSlot.CHEST));
        to.setItemSlot(EquipmentSlot.LEGS, from.getItemBySlot(EquipmentSlot.LEGS));
        to.setItemSlot(EquipmentSlot.FEET, from.getItemBySlot(EquipmentSlot.FEET));
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
        character.setArmored(true);

        // DragonMineZ reads the custom hair only when hairId is 0 (HairManager.getEffectiveHair);
        // any other value resolves a built-in preset instead. Character's constructor seeds hairId
        // from the race config's defaultHairType, so leaving it alone let a server config silently
        // override the builder's hair code. Set it deliberately, every time.
        character.setHairId(Math.max(0, state.hairStyleId()));

        String hairCode = state.hairCode() == null ? "" : state.hairCode().trim();
        String hairKey = state.hairStyleId() + "|" + hairCode;
        if (!Objects.equals(proxy.syncedHairCode, hairKey)) {
            // Same resolver the OVERLAY layers use, so the two modes cannot drift apart again.
            CustomHair[] hair = NpcHairVis.resolveSet(NpcHairVis.spec(proxy.owner));
            character.setHairBase(copy(hair, 0));
            character.setHairSSJ(copy(hair, 1));
            character.setHairSSJ2(copy(hair, 2));
            character.setHairSSJ3(copy(hair, 3));
            proxy.syncedHairCode = hairKey;
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
        // Inheriting is its own flag, not "the string is empty". That is what lets the editor keep
        // a chosen colour while the race colour is in use, so switching back restores it.
        if (appearance == null || appearance.tailUseRaceColor
                || appearance.tailColor == null || appearance.tailColor.isBlank()) {
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
