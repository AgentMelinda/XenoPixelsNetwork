package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainParticles;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcTransformHoldPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import org.joml.Vector3f;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hold-G analogue for CustomNPCs using real {@link FormConfig.FormData} only:
 * form aura, {@code dragonminez:transform_on}, optional lightnings, optional gecko
 * {@code getTransformationAnimation()}, and a client hair morph through
 * {@code HairRenderer} (no {@code StatsData} / {@code DMZHairLayer}).
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcTransformSystem {
    public static final int DEFAULT_TICKS = 40;

    public enum Fail {
        OK,
        NO_FORM,
        NO_MASTERY
    }

    private record Hold(boolean stack, String group, String form, long untilGameTime, int duration,
                        long startGameTime, int restoreSize, boolean lightnings,
                        int auraColor) {}

    private static final double SYNC_RANGE_SQ = 128.0 * 128.0;

    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

    private NpcTransformSystem() {}

    public static void cancel(UUID npcId) {
        if (npcId != null) {
            HOLDS.remove(npcId);
        }
    }

    public static void cancel(LivingEntity npc) {
        if (npc == null) {
            return;
        }
        HOLDS.remove(npc.getUUID());
        syncHold(npc);
    }

    public static void syncHold(LivingEntity npc) {
        if (npc == null || npc.level().isClientSide() || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        NpcTransformHoldPacket packet = packet(npc);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(npc) <= SYNC_RANGE_SQ) {
                ModNetwork.sendToPlayer(viewer, packet);
            }
        }
    }

    public static void syncHold(ServerPlayer viewer, LivingEntity npc) {
        if (viewer == null || npc == null || HOLDS.get(npc.getUUID()) == null) {
            return;
        }
        ModNetwork.sendToPlayer(viewer, packet(npc));
    }

    private static NpcTransformHoldPacket packet(LivingEntity npc) {
        Hold hold = HOLDS.get(npc.getUUID());
        if (hold == null) {
            return NpcTransformHoldPacket.cancel(npc.getUUID());
        }
        return new NpcTransformHoldPacket(npc.getUUID(), hold.stack(), hold.group(), hold.form(),
                hold.duration(), hold.startGameTime());
    }

    public static Fail canStart(LivingEntity npc, String group, String form) {
        if (npc == null || group == null || form == null) {
            return Fail.NO_FORM;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        FormConfig.FormData data = NpcFormLookup.form(profile.raceId, group, form);
        if (data == null) {
            return Fail.NO_FORM;
        }
        return Fail.OK;
    }

    public static boolean start(LivingEntity npc, String group, String form, int ticks) {
        return start(npc, group, form, ticks, false);
    }

    public static boolean startStack(LivingEntity npc, String group, String form, int ticks) {
        return start(npc, group, form, ticks, true);
    }

    private static boolean start(LivingEntity npc, String group, String form, int ticks, boolean stack) {
        if (npc == null || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        FormConfig.FormData pending = stack
                ? NpcFormLookup.stackForm(group, form)
                : NpcFormLookup.form(profile.raceId, group, form);
        if (pending == null) return false;
        if (stack) {
            FormConfig.FormData normal = NpcFormLookup.activeForm(profile);
            if (!NpcFormLookup.compatible(profile, normal, profile.formGroup, pending, group)) return false;
            double max = NpcFormLookup.maxMastery(pending);
            profile.stackMasteries.setMastery(group, form, max, max);
            profile.selectedStackGroup = group;
            profile.selectedStackId = form;
        } else {
            NpcFormLookup.grantMastery(profile, group, form);
            profile.selectedFormGroup = group;
            profile.selectedFormId = form;
        }
        profile.write(npc);
        int restore = profile.baseSize > 0 ? profile.baseSize : NpcDisplayApply.getSize(npc);
        if (profile.baseSize <= 0 && restore > 0) {
            profile.baseSize = restore;
            profile.write(npc);
        }
        // Keep the currently committed form until the hold completes.  Writing
        // the target here changes Gecko/DMZ appearance immediately and made the
        // transformation animation appear to be skipped.
        if (pending != null) {
            String anim = pending.getTransformationAnimation();
            if (anim != null && !anim.isBlank()) {
                NpcGeckoAnim.play(npc, anim);
            }
        }
        playDmz(level, npc, "transform_on");

        int duration = ticks <= 0 ? DEFAULT_TICKS : ticks;
        long start = level.getGameTime();
        NpcAuraStyle style = profile.auraStyle(stack, group, form, false);
        boolean bolts = profile.auraLightning && resolveLightning(pending, style);
        int transformAuraColor = resolveTransformAuraColor(profile, pending, style);
        HOLDS.put(npc.getUUID(), new Hold(stack, group, form, start + duration, duration, start,
                restore, bolts, transformAuraColor));
        // The hold is part of effective aura visibility.  Install it before
        // syncing so a base-aura-off NPC does not wait for the periodic refresh.
        NpcAuraFx.sync(npc);
        NpcFormAttributeSync.apply(npc, profile);
        syncHold(npc);
        burst(level, npc, 0.4f);
        dmzBurst(level, npc, bolts, transformAuraColor);
        if (npc.onGround()) {
            npc.setDeltaMovement(npc.getDeltaMovement().add(0.0, 0.42, 0.0));
            npc.hasImpulse = true;
            npc.hurtMarked = true;
        }
        return true;
    }

    public static boolean descend(LivingEntity npc) {
        if (npc == null) {
            return false;
        }
        HOLDS.remove(npc.getUUID());
        syncHold(npc);
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        profile.formGroup = "";
        profile.formId = "";
        profile.stackGroup = "";
        profile.stackId = "";
        profile.formPower = 1.0;
        profile.write(npc);
        NpcAuraFx.sync(npc);
        if (npc.level() instanceof ServerLevel level) {
            playDmz(level, npc, "transform_off");
        }
        if (profile.baseSize > 0) {
            NpcDisplayApply.setSize(npc, profile.baseSize);
        }
        return true;
    }

    public static boolean unstack(LivingEntity npc) {
        if (npc == null) return false;
        Hold hold = HOLDS.get(npc.getUUID());
        if (hold != null && hold.stack()) HOLDS.remove(npc.getUUID());
        syncHold(npc);
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        boolean changed = !profile.stackGroup.isBlank() || !profile.stackId.isBlank();
        profile.stackGroup = "";
        profile.stackId = "";
        profile.write(npc);
        NpcAuraFx.sync(npc);
        if (changed && npc.level() instanceof ServerLevel level) playDmz(level, npc, "transform_off");
        return changed;
    }

    public static boolean isHolding(UUID id) { return id != null && HOLDS.containsKey(id); }

    /** Descends one configured prerequisite (SSJ3 -> SSJ2 -> SSJ1 -> base). */
    public static boolean descendOne(LivingEntity npc, int ticks) {
        if (npc == null) return false;
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        if (profile.formGroup == null || profile.formGroup.isBlank() || profile.formId == null || profile.formId.isBlank()) {
            return false;
        }
        FormConfig.FormData active = NpcFormLookup.form(profile.raceId, profile.formGroup, profile.formId);
        String requisite = active == null ? null : active.getFormRequisite();
        if (requisite == null || requisite.isBlank()) {
            return descend(npc);
        }
        String[] parts = requisite.split("\\.", 2);
        if (parts.length != 2) return descend(npc);
        return start(npc, parts[0], parts[1], ticks);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (HOLDS.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Hold> entry = it.next();
            LivingEntity npc = find(event, entry.getKey());
            if (npc == null || !npc.isAlive()) {
                it.remove();
                continue;
            }
            if (!(npc.level() instanceof ServerLevel level)) {
                continue;
            }
            Hold hold = entry.getValue();
            if (now % 4 == 0) {
                swirl(level, npc, hold.lightnings(), hold.auraColor());
                NpcCombatProfile profile = NpcCombatProfile.read(npc);
                if (profile.auraRocks && npc.onGround()) {
                    NpcKiGroundFx.pulse(level, npc);
                }
            }
            if (level.getGameTime() >= hold.untilGameTime()) {
                it.remove();
                commit(npc, hold);
            }
        }
    }

    private static void commit(LivingEntity npc, Hold hold) {
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        FormConfig.FormData data;
        if (hold.stack()) {
            profile.stackGroup = hold.group();
            profile.stackId = hold.form();
            data = NpcFormLookup.stackForm(hold.group(), hold.form());
        } else {
            data = NpcFormLookup.form(profile.raceId, hold.group(), hold.form());
            FormConfig.FormData activeStack = NpcFormLookup.activeStackForm(profile);
            if (!NpcFormLookup.compatible(profile, data, hold.group(), activeStack, profile.stackGroup)) {
                profile.stackGroup = "";
                profile.stackId = "";
            }
            profile.formGroup = hold.group();
            profile.formId = hold.form();
            profile.formPower = NpcFormLookup.power(data);
        }
        profile.write(npc);
        NpcAuraFx.setActive(npc, true);
        if (npc.level() instanceof ServerLevel level) {
            burst(level, npc, 0.7f);
            dmzBurst(level, npc, hold.lightnings(), hold.auraColor());
            playDmz(level, npc, "transform_on");
        }
    }

    static int resolveTransformAuraColor(NpcCombatProfile profile, FormConfig.FormData target) {
        return resolveTransformAuraColor(profile, target, null);
    }

    static int resolveTransformAuraColor(NpcCombatProfile profile, FormConfig.FormData target,
                                         NpcAuraStyle style) {
        if (style != null && style.enabled && !style.primaryColor.isBlank()) {
            return NpcCombatProfile.parseHexColor(style.primaryColor).orElse(0xFFFFFF);
        }
        java.util.OptionalInt formColor = NpcFormLookup.auraRgb(target);
        if (formColor.isPresent()) return formColor.getAsInt() & 0xFFFFFF;
        return NpcAuraResolver.baseRgb(profile);
    }

    private static boolean resolveLightning(FormConfig.FormData target, NpcAuraStyle style) {
        if (style != null && style.enabled && style.lightningConfigured) return style.lightningEnabled;
        return target != null && Boolean.TRUE.equals(target.getHasLightnings());
    }

    private static void swirl(ServerLevel level, LivingEntity npc, boolean lightning, int auraColor) {
        double x = npc.getX();
        double y = npc.getY() + npc.getBbHeight() * 0.45;
        double z = npc.getZ();
        coloredAura(level, npc, x, y, z, 10, 0.45, 0.55, 0.45, auraColor);
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        if (profile.auraRocks) particle(level, MainParticles.DUST.get(), x, npc.getY() + 0.1, z, 8, 0.5, 0.15, 0.5, 0.04);
        if (lightning) {
            particle(level, MainParticles.KI_LIGHTNING.get(), x, y + 0.2, z, 8, 0.4, 0.6, 0.4, 0.01);
        }
    }

    private static void dmzBurst(ServerLevel level, LivingEntity npc, boolean lightning,
                                 int auraColor) {
        double x = npc.getX();
        double y = npc.getY() + 1.0;
        double z = npc.getZ();
        particle(level, MainParticles.KI_FLASH.get(), x, y, z, 1, 0, 0, 0, 0);
        particle(level, MainParticles.KI_SHEDDING.get(), x, y, z, 16, 0.5, 0.4, 0.5, 0.05);
        coloredAura(level, npc, x, y, z, 18, 0.6, 0.5, 0.6, auraColor);
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        if (profile.auraRocks) particle(level, MainParticles.DUST.get(), x, npc.getY() + 0.05, z, 14, 0.7, 0.08, 0.7, 0.06);
        if (profile.auraRocks && npc.onGround()) {
            NpcKiGroundFx.pulse(level, npc);
        }
        if (lightning) {
            particle(level, MainParticles.KI_LIGHTNING.get(), x, y, z, 12, 0.5, 0.7, 0.5, 0.02);
        }
    }

    /**
     * DMZ's AuraParticle reads its three speed parameters as RGB. A normal batched
     * server particle packet randomizes those values and produces dark/black flecks.
     * Position each particle here and send count=0 with speed=1 so the client receives
     * the exact color channels expected by DMZ's provider.
     */
    private static void coloredAura(ServerLevel level, LivingEntity npc,
                                    double x, double y, double z, int count,
                                    double spreadX, double spreadY, double spreadZ,
                                    int color) {
        if (MainParticles.AURA.get() == null || count <= 0) return;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        double npcScale = Math.max(0.05, NpcDisplayApply.getSize(npc) / 5.0);
        for (int i = 0; i < count; i++) {
            double px = x + npc.getRandom().nextGaussian() * spreadX * npcScale;
            double py = y + npc.getRandom().nextGaussian() * spreadY * npcScale;
            double pz = z + npc.getRandom().nextGaussian() * spreadZ * npcScale;
            level.sendParticles(MainParticles.AURA.get(), px, py, pz, 0, r, g, b, 1.0);
        }
    }

    static void particle(ServerLevel level, SimpleParticleType type,
                         double x, double y, double z, int count,
                         double dx, double dy, double dz, double speed) {
        if (type == null) {
            return;
        }
        level.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
    }

    private static void burst(ServerLevel level, LivingEntity npc, float knock) {
        // Vanilla FLASH is a SimpleParticleType: fixed sprite, no size/color argument, so it
        // never grew with a scaled-up NPC and whatever tint it picked up in-context read as
        // black instead of white. DustParticleOptions carries an explicit color and size, so
        // this both forces white and scales with the NPC's own display size (5 = CNPC default).
        float npcScale = (float) Math.max(0.05, NpcDisplayApply.getSize(npc) / 5.0);
        level.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 3.0f * npcScale),
                npc.getX(), npc.getY() + 1.0, npc.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD,
                npc.getX(), npc.getY() + 0.2, npc.getZ(), 36, 0.5, 0.2, 0.5, 0.03);
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.5f, 1.35f);
        AABB box = npc.getBoundingBox().inflate(4.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                ent -> ent != npc && ent.isAlive())) {
            Vec3 flat = new Vec3(e.getX() - npc.getX(), 0, e.getZ() - npc.getZ());
            if (flat.lengthSqr() < 1.0e-4) {
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().add(flat.normalize().scale(knock).add(0, 0.15, 0)));
            e.hurtMarked = true;
            e.hasImpulse = true;
        }
    }

    private static LivingEntity find(ServerTickEvent.Post event, UUID id) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static void playDmz(ServerLevel level, LivingEntity npc, String sound) {
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath("dragonminez", sound));
        if (event == null) {
            return;
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                event, SoundSource.NEUTRAL, 0.9f, 1.0f);
    }
}
