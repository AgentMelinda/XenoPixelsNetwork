package net.bullettrain.xenopixelsmod.client.aura;

import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsData;
import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAccess;
import net.bullettrain.xenopixelsmod.client.combat.aura.XenoAuraScaling;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.combat.fx.HakaiEffectRules;
import net.bullettrain.xenopixelsmod.fx.aura.AuraMotifTable;
import net.bullettrain.xenopixelsmod.fx.aura.AuraPalette;
import net.bullettrain.xenopixelsmod.fx.aura.AuraStyle;
import net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlan;
import net.bullettrain.xenopixelsmod.mixin.compat.dmz.DmzAuraLayersInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Camera;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays the generated HD aura on whoever DragonMineZ is showing an aura for, on this client
 * only: v1 {@code aura_in_}/{@code aura_out_}, v2 {@code aura2/}, v3 {@code aura3/} plus dense
 * v1, v4 {@code aura3/} plus cheaper {@code aura4/} plume. No packets; every client decides for itself
 * ({@code /xenoaura dmz|hd|both} and {@code v1|v2|v3|v4}). Each effect is bound to the entity
 * (it follows it every frame, upright) and re-sent every 10 ticks, cross-fading like Sparking.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class HdAuraClient {
    static final int PULSE_TICKS = 10;
    /** An aura not queued by DMZ for this many ticks has been switched off. */
    static final int FORGET_TICKS = 3;

    /**
     * The clock aura sightings are aged on: it advances by one per client tick, but only on a tick
     * that follows a rendered frame.
     *
     * <p>A sighting is DragonMineZ queueing an aura while it draws a player, so it happens per
     * frame, and it was aged in game ticks: three ticks without one meant the aura had been
     * switched off. On a client that drops below about seven frames a second, or freezes for a
     * moment and then runs its missed ticks in one go - a big modpack, a shader pack - three ticks
     * pass with no frame drawn between them, the aura was taken for off and started again from
     * nothing (2026-10-02 owner: "sometimes on client lag aura resets ... also with alot of mods").
     * Ticks with no frame in between no longer count.
     */
    private static long auraClock;
    private static volatile long frames;
    private static long framesAtLastTick;

    @SubscribeEvent
    public static void onRenderFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        frames++;
    }

    /** Whether the sighting clock moves on this tick: only if a frame was drawn since the last one. */
    static boolean clockAdvances(long framesNow, long framesAtLastTick) {
        return framesNow != framesAtLastTick;
    }

    private record Seen(WeakReference<AbstractClientPlayer> player, long tick) {}

    private static final Map<Integer, Seen> SEEN = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> LAST_PULSE = new ConcurrentHashMap<>();
    private static volatile boolean failed;
    private static long lastTickNanos;
    /** /xenoaura debug: logs every aura pulse (timing, size, colours) to find a blink. Not saved. */
    public static volatile boolean debug;
    private static final Map<Integer, Long> DEBUG_LAST_MS = new ConcurrentHashMap<>();

    private HdAuraClient() {
    }

    /** HD-only and working: DragonMineZ-style auras are hidden in favour of the HD one. */
    public static boolean replacesDmzAura() {
        return !style().dmz() && !failed;
    }

    public static AuraStyle style() {
        return AuraStyle.parse(XenoClientConfig.auraStyle);
    }

    /** DragonMineZ queued an aura for this player (or NPC render proxy) this frame. */
    public static void seen(AbstractClientPlayer player) {
        if (player == null || failed) return;
        Minecraft mc = Minecraft.getInstance();
        SEEN.put(player.getId(), new Seen(new WeakReference<>(player), auraClock));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            SEEN.clear();
            LAST_PULSE.clear();
            SecondAuraClientState.clear();
            RIM_PULSE.clear();
            AuraRimGlow.clearAll();
            return;
        }
        if (!style().hd() || failed) {
            AuraRimGlow.clearAll();
            return;
        }
        long now = level.getGameTime();
        long framesNow = frames;
        if (clockAdvances(framesNow, framesAtLastTick)) auraClock++;
        framesAtLastTick = framesNow;
        long nanos = System.nanoTime();
        if (HdAuraPlan.hitch(lastTickNanos, nanos)) {
            LAST_PULSE.clear();
            RIM_PULSE.clear();
        }
        lastTickNanos = nanos;
        // A player whose second aura is on has an aura every tick, DMZ's showing or not.
        for (AbstractClientPlayer player : level.players()) {
            if (SecondAuraClientState.on(player.getId())) {
                SEEN.put(player.getId(), new Seen(new WeakReference<>(player), auraClock));
            }
        }
        // Local player with DMZ aura active — mark in FP and TP so HD-only never depends only
        // on the queue mixin (created-world blank when second-aura was off).
        if (mc.player != null) {
            StatsData own = DmzAccess.stats(mc.player).orElse(null);
            if (own != null && own.getStatus() != null
                    && (own.getStatus().isAuraActive() || own.getStatus().isPermanentAura())) {
                SEEN.put(mc.player.getId(), new Seen(new WeakReference<>(mc.player), auraClock));
            }
        }
        try {
            tickRims(mc, level, now);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            AuraRimGlow.clearAll();
            XenoPixelsMod.LOGGER.warn("HD aura disabled for this session ({})", e.toString());
            return;
        }
        try {
            tickNpcs(mc, level, now);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            XenoPixelsMod.LOGGER.warn("HD aura disabled for this session ({})", e.toString());
            return;
        }
        LIVE_STRETCH.keySet().retainAll(SEEN.keySet());
        for (var entry : SEEN.entrySet()) {
            AbstractClientPlayer source = entry.getValue().player().get();
            StatsData stats = source == null ? null : DmzAccess.stats(source).orElse(null);
            if (stats != null) LIVE_STRETCH.put(entry.getKey(), kiAuraStretch(stats));
        }
        if (SEEN.isEmpty()) return;
        for (var it = SEEN.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            int id = entry.getKey();
            Seen seen = entry.getValue();
            if (auraClock - seen.tick() > FORGET_TICKS) {
                it.remove();
                LAST_PULSE.remove(id);
                continue;
            }
            if (!plays(level, id)) continue;
            Long last = LAST_PULSE.get(id);
            if (last != null && now - last < PULSE_TICKS) continue;
            LAST_PULSE.put(id, now);
            try {
                play(mc, level, id, seen.player().get());
            } catch (RuntimeException | LinkageError e) {
                failed = true;
                XenoPixelsMod.LOGGER.warn("HD aura disabled for this session ({}); DMZ's aura still draws in 'both'",
                        e.toString());
                return;
            }
        }
    }

    /**
     * NPCs whose aura is on (2026-10-02 owner: "should have the new aura animtion on npcs with a
     * both option"). A Full-appearance NPC drawn by DragonMineZ is already in SEEN through its
     * render stand-in; every other NPC - appearance off or overlay, GeckoLib and mimic models -
     * only had the shader column, so it is picked up here from the aura state the server sent.
     */
    private static void tickNpcs(Minecraft mc, ClientLevel level, long now) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity)
                    || entity instanceof net.minecraft.world.entity.player.Player) continue;
            List<net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver.Layer> layers =
                    net.bullettrain.xenopixelsmod.client.compat.npc.NpcAuraClient.hdLayers(entity.getUUID());
            if (layers == null) continue;
            int id = entity.getId();
            Seen seen = SEEN.get(id);
            if (seen != null && auraClock - seen.tick() <= FORGET_TICKS) continue;
            Long last = LAST_PULSE.get(id);
            if (last != null && now - last < PULSE_TICKS) continue;
            LAST_PULSE.put(id, now);
            List<HdAuraPlan.Layer> plan = new ArrayList<>();
            for (var layer : layers) {
                plan.add(new HdAuraPlan.Layer(Math.max(0, layer.index()), layer.rgb() & 0xFFFFFF, 1.0f));
            }
            plan.sort(Comparator.comparingInt(HdAuraPlan.Layer::id));
            emit(level, entity, plan, Set.of());
        }
        if (now % 200 == 0) LAST_PULSE.values().removeIf(tick -> now - tick > 200);
    }

    /**
     * Whether this entity's variant 2 aura is the copy that ignores scene depth: your own
     * character in third person, as DragonMineZ does for your own aura, so rocks, dust and other
     * particles do not cut holes in it. Everyone else's stays depth-tested, or it would show
     * through walls. /xenoaura overlay.
     */
    private static boolean overParticles(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return XenoClientConfig.auraOverParticles && entity == mc.player
                && !mc.options.getCameraType().isFirstPerson();
    }

    /**
     * You, in first person, with the first-person sighting switched on. DragonMineZ does not draw
     * your own model then, so its aura layer never reports you; your aura status is read instead
     * (the same test DMZ's own first-person aura uses: aura active or permanent). The world-space
     * HD flame is not played; this only stops the aura from being forgotten.
     */
    private static boolean ownFirstPerson(Minecraft mc) {
        return XenoClientConfig.auraFirstPerson && mc.player != null
                && mc.options.getCameraType().isFirstPerson();
    }

    /** Whether variant 2 (the spiked flame silhouette) is chosen, /xenoaura v2. */
    public static boolean variant2() {
        return "v2".equalsIgnoreCase(XenoClientConfig.auraVariant);
    }

    /** Variant 3: v2 silhouette plus v1 inner, punch-colour edges and form-coloured sparking. */
    public static boolean variant3() {
        return "v3".equalsIgnoreCase(XenoClientConfig.auraVariant);
    }

    /** Variant 4: v3 silhouette plus cheaper v1-style plume (no dense {@code aura_out_*}). */
    public static boolean variant4() {
        return "v4".equalsIgnoreCase(XenoClientConfig.auraVariant);
    }

    /**
     * How much taller and wider the ki aura stands than at rest: battle power, and the rise while
     * transforming or charging ki - the same curve DragonMineZ's own aura is stretched by
     * ({@link XenoAuraScaling}). {width, height}.
     */
    /** DragonMineZ's power release, 0 to 100 and past it when the limit is raised; 100 if unknown. */
    private static int release(StatsData stats) {
        try {
            return stats.getResources() == null ? 100 : stats.getResources().getPowerRelease();
        } catch (RuntimeException e) {
            return 100;
        }
    }

    /**
     * How large DragonMineZ draws this player's aura, against a player at rest: the form's model
     * scale, battle power, and the rise while transforming or charging ki, exactly as its own aura
     * gets them ({@code AuraRenderer.getAuraScale}, with {@link XenoAuraScaling} applied to it).
     * The HD aura is drawn at the same size. {width, height}.
     */
    private static float[] kiAuraStretch(StatsData stats) {
        float[] dims;
        float npcFactor = net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer.auraFactor(stats);
        if (!Float.isFinite(npcFactor) || npcFactor <= 0.0f) npcFactor = 1.0f;
        try {
            dims = HdAuraPlan.relativeToRest(DmzAuraLayersInvoker.xenopixels$auraScale(stats,
                    DmzAuraLayersInvoker.xenopixels$modelScale(stats)));
            // getModelScale already includes the NPC factor. Cap power/charge growth first,
            // then restore the owner's chosen size so the player cap cannot flatten large NPCs.
            dims = new float[] {dims[0] / npcFactor, dims[1] / npcFactor};
        } catch (RuntimeException | LinkageError e) {
            // DragonMineZ's sizing could not be asked: the curve alone, without the model scale.
            float[] scaled = XenoAuraScaling.apply(new float[] {1.0f, 1.0f, 1.0f}, stats);
            dims = scaled == null || scaled.length < 2 ? new float[] {1.0f, 1.0f}
                    : new float[] {scaled[0], scaled[1]};
        }
        return HdAuraPlan.capNpcStretch(dims[0], dims[1], XenoClientConfig.auraMaxHeight, npcFactor);
    }

    /** An NPC: use its actual display/profile scale and explicit aura scale exactly once. */
    private static void emit(ClientLevel level, Entity entity, List<HdAuraPlan.Layer> plan, Set<Integer> extras) {
        float scale = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAuraClient.auraRenderScale(
                (net.minecraft.world.entity.LivingEntity) entity);
        emit(level, entity, plan, extras, new float[] {scale, scale});
    }

    private static void emit(ClientLevel level, Entity entity, List<HdAuraPlan.Layer> plan, Set<Integer> extras,
                             float[] stretch) {
        // The stretch already carries the body: DMZ's model scale for a player, body height for an
        // NPC.
        float size = XenoClientConfig.auraSize;
        float[] raw = stretch;
        boolean silhouette = HdAuraPlan.playsSilhouette(XenoClientConfig.auraVariant);
        float[] shape = HdAuraPlan.shape(silhouette);
        stretch = new float[] {raw[0] * shape[0], raw[1] * shape[1]};
        // Phase 1 Motif: brightness only. NPCs / missing form → Motif(1,1).
        AuraMotifTable.Motif motif = AuraMotifTable.motif(AuraMotifTable.key(
                DmzAccess.race(entity), DmzAccess.activeFormGroup(entity), DmzAccess.activeForm(entity)));
        if (silhouette) {
            // One effect per aura, in its main colour, stretched with the ki aura.
            // No colour is folded into another here: DragonMineZ draws a form's extra aura and a
            // stack form as layers of their own, each a little larger, and so does this.
            // v2/v3/v4 silhouette: HdAuraPlan.plan(plan, Set.of(), true)
            String folder = (variant3() || variant4()) ? "aura3/aura3_" : "aura2/aura2_";
            for (HdAuraPlan.Aura aura : HdAuraPlan.plan(plan, Set.of(), true)) {
                float s = size * aura.scale();
                spawn(level, entity, folder + AuraPalette.hex(AuraPalette.nearest(aura.inner()))
                                + (overParticles(entity) ? "_nz" : ""),
                        s, stretch,
                        XenoClientConfig.auraBrightness * aura.alpha() * motif.outerBrightness(), 0.0f);
            }
            if (HdAuraPlan.playsV1Outer(XenoClientConfig.auraVariant)
                    || HdAuraPlan.playsLeanOuter(XenoClientConfig.auraVariant)
                    || HdAuraPlan.playsV1Inner(XenoClientConfig.auraVariant)) {
                // v3: dense v1 outer billow + inner. v4: cheaper aura4 plume + inner.
                float[] v1Shape = HdAuraPlan.shape(false);
                float[] v1Stretch = new float[] {raw[0] * v1Shape[0], raw[1] * v1Shape[1]};
                for (HdAuraPlan.Aura aura : HdAuraPlan.plan(plan, extras, XenoClientConfig.auraLayers)) {
                    float brightness = XenoClientConfig.auraBrightness * aura.alpha();
                    float s = size * aura.scale();
                    if (HdAuraPlan.playsV1Outer(XenoClientConfig.auraVariant)) {
                        spawn(level, entity, "aura/aura_out_" + AuraPalette.hex(AuraPalette.nearest(aura.outer())),
                                s, v1Stretch, brightness * motif.outerBrightness(), 0.0f);
                    } else if (HdAuraPlan.playsLeanOuter(XenoClientConfig.auraVariant)) {
                        spawn(level, entity, "aura4/aura4_" + AuraPalette.hex(AuraPalette.nearest(aura.outer()))
                                        + (overParticles(entity) ? "_nz" : ""),
                                s, v1Stretch, brightness * motif.outerBrightness(), 0.0f);
                    }
                    if (HdAuraPlan.playsV1Inner(XenoClientConfig.auraVariant)) {
                        spawn(level, entity, "aura/aura_in_" + AuraPalette.hex(AuraPalette.nearest(aura.inner())),
                                s, v1Stretch,
                                brightness * XenoClientConfig.auraInnerBrightness * motif.innerBrightness(),
                                0.0f);
                    }
                }
            }
            return;
        }
        for (HdAuraPlan.Aura aura : HdAuraPlan.plan(plan, extras, XenoClientConfig.auraLayers)) {
            float brightness = XenoClientConfig.auraBrightness * aura.alpha();
            float s = size * aura.scale();
            spawn(level, entity, "aura/aura_out_" + AuraPalette.hex(AuraPalette.nearest(aura.outer())),
                    s, stretch, brightness * motif.outerBrightness(), 0.0f);
            spawn(level, entity, "aura/aura_in_" + AuraPalette.hex(AuraPalette.nearest(aura.inner())),
                    s, stretch,
                    brightness * XenoClientConfig.auraInnerBrightness * motif.innerBrightness(), 0.0f);
        }
    }

    private static final Map<Integer, Long> RIM_PULSE = new ConcurrentHashMap<>();

    /**
     * Whether the HD aura plays on this entity. A player decides for themselves (their second aura
     * switch, {@code /secondaura}); with it off the aura plays only if this client asked for the
     * earlier behaviour ({@code /xenoaura follow on}). NPCs follow DragonMineZ's aura as before.
     * HD-only also plays for anyone recently {@link #seen} so canceling the DMZ sheet cannot leave
     * a blank body when second-aura is off.
     */
    public static boolean plays(ClientLevel level, int id) {
        if (level == null || !(level.getEntity(id) instanceof net.minecraft.world.entity.player.Player)) {
            return true;
        }
        if (SecondAuraClientState.on(id) || XenoClientConfig.auraFollowDmz) {
            return true;
        }
        return !style().dmz() && SEEN.containsKey(id);
    }

    /**
     * Players in a form get the outline glow in their aura colour (aura on or off), and while the
     * aura is off a thin flickering flame outline (aura_rim_*), re-sent every 10 ticks.
     */
    private static void tickRims(Minecraft mc, ClientLevel level, long now) {
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        for (AbstractClientPlayer player : level.players()) {
            int id = player.getId();
            Seen seen = SEEN.get(id);
            boolean auraOn = seen != null && auraClock - seen.tick() <= FORGET_TICKS && plays(level, id);
            StatsData stats = DmzAccess.stats(player).orElse(null);
            if (stats == null || stats.getCharacter() == null) continue;
            boolean inForm = stats.getCharacter().hasActiveForm() || stats.getCharacter().hasActiveStackForm();
            // In a form, or with the aura on: the body shines with the aura either way.
            if (!inForm && !auraOn) continue;
            float[] main = mainColour(player, stats);
            if (main == null) continue;
            keep.add(id);
            // The outline glow stays on for as long as the form does (charging turns the aura on,
            // and the glow disappearing then read as a bug); the small rim flames only show while
            // the aura is off, since the aura itself replaces them.
            AuraRimGlow.set(id, HdAuraPlan.shade(AuraPalette.rgb(main), HdAuraPlan.releaseShine(release(stats))));
            if (auraOn || !inForm) continue;
            Long last = RIM_PULSE.get(id);
            if (last != null && now - last < PULSE_TICKS) continue;
            RIM_PULSE.put(id, now);
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            spawn(level, player, "aura/aura_rim_" + AuraPalette.pair(main, null)[0],
                    HakaiEffectRules.bodyScale(player.getBbHeight()));
        }
        for (Integer id : java.util.List.copyOf(AuraRimGlow.ids())) {
            if (!keep.contains(id)) {
                AuraRimGlow.clear(id);
                RIM_PULSE.remove(id);
            }
        }
    }

    private static float[] mainColour(AbstractClientPlayer source, StatsData stats) {
        List<AuraRenderer.AuraLayer> layers = layers(source, stats);
        return layers.isEmpty() ? null : layers.get(0).color;
    }

    private static List<AuraRenderer.AuraLayer> layers(AbstractClientPlayer source, StatsData stats) {
        List<AuraRenderer.AuraLayer> layers = new ArrayList<>(
                DmzAuraLayersInvoker.xenopixels$auraLayers(source, stats, 1.0f));
        layers.removeIf(l -> l == null || l.color == null || l.color.length < 3);
        layers.sort(Comparator.comparingInt(l -> l.layerId));
        return layers;
    }

    private static void play(Minecraft mc, ClientLevel level, int id, AbstractClientPlayer source) {
        Entity entity = level.getEntity(id);
        if (entity == null || source == null) return;
        StatsData stats = DmzAccess.stats(source).orElse(null);
        if (stats == null) return;
        List<AuraRenderer.AuraLayer> layers = layers(source, stats);
        if (layers.isEmpty()) return;
        List<HdAuraPlan.Layer> plan = new ArrayList<>();
        for (AuraRenderer.AuraLayer layer : layers) {
            plan.add(new HdAuraPlan.Layer(layer.layerId, AuraPalette.rgb(layer.color), layer.alpha));
        }
        float size = HakaiEffectRules.bodyScale(entity.getBbHeight()) * XenoClientConfig.auraSize;
        if (debug) {
            long ms = System.currentTimeMillis();
            Long before = DEBUG_LAST_MS.put(id, ms);
            XenoPixelsMod.LOGGER.info("HD aura pulse id={} tick={} sinceLastMs={} fps={} bbHeight={} size={} layers={} pos={}",
                    id, level.getGameTime(), before == null ? -1 : ms - before, mc.getFps(),
                    entity.getBbHeight(), size, plan, entity.position());
        }
        emit(level, entity, plan, extraColours(stats), kiAuraStretch(stats));
    }

    /** The extra aura colours of the active form and stack form: outer flames, not auras of their own. */
    private static Set<Integer> extraColours(StatsData stats) {
        Set<Integer> out = new HashSet<>();
        var character = stats.getCharacter();
        if (character == null) return out;
        if (character.hasActiveForm()) addExtra(out, character.getActiveFormData());
        if (character.hasActiveStackForm()) addExtra(out, character.getActiveStackFormData());
        return out;
    }

    private static void addExtra(Set<Integer> out, FormConfig.FormData form) {
        if (form == null || !form.hasExtraAura()) return;
        float[] colour = form.getRgbExtraAuraColor();
        if (colour != null && colour.length >= 3) out.add(AuraPalette.rgb(colour));
    }

    private static void spawn(ClientLevel level, Entity entity, String path, float size) {
        spawn(level, entity, path, size, XenoClientConfig.auraBrightness);
    }

    /** The ki aura stretch of each entity an aura is playing on, refreshed every tick. */
    private static final Map<Integer, float[]> LIVE_STRETCH = new ConcurrentHashMap<>();
    private static volatile boolean liveFailed;
    /**
     * Live HD emitters that must be repositioned every render frame. Updated on
     * {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} (before AAA's end-of-level draw) and
     * again in PreDraw so fast flight does not leave the column a frame behind.
     */
    private static final List<LiveAura> LIVE_AURAS = new ArrayList<>();

    private record LiveAura(
            WeakReference<Entity> entity,
            mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter emitter,
            float size,
            float[] shape,
            float drop,
            boolean silhouette,
            float[] initial,
            boolean[] stopped) {
    }

    /**
     * Plays one aura effect of base size {@code size}, stretched by {width, height}.
     *
     * <p>Live (the default, /xenoaura live): the effect is resized every frame from the entity's
     * current stretch, so it grows with the ki aura height as the charge ramps instead of keeping
     * the size it was sent with until the next copy replaces it (2026-10-02 owner, twice: "let it
     * scale with kiaura height"). The effect is played through AAA's own client calls -
     * {@code EffectRegistry.tryLoad}, {@code EffectDefinition.playRouted}, a pre-draw callback -
     * which is what {@code ParticleEmitterInfo} does for a bound effect, plus the scale.
     * Otherwise, or if that path ever throws, the stretch is fixed at spawn as before.
     */
    /**
     * @param sink unused since the variants are laid onto DragonMineZ's box; where each one starts
     *             is {@link HdAuraPlan#drop}, applied by the live path
     */
    private static void spawn(ClientLevel level, Entity entity, String path, float size, float[] stretch,
                              float brightness, float sink) {
        if (!AuraPalette.visible(brightness)) return;
        ResourceLocation effect = ResourceLocation.fromNamespaceAndPath(
                XenoPixelsMod.MOD_ID, path + AuraPalette.brightnessSuffix(brightness));
        if (XenoClientConfig.auraLiveScale && !liveFailed) {
            try {
                spawnLive(entity, effect, size, stretch, sink);
                return;
            } catch (RuntimeException | LinkageError e) {
                liveFailed = true;
                XenoPixelsMod.LOGGER.warn("HD aura live scaling off for this session ({})", e.toString());
            }
        }
        ParticleEmitterInfo info = ParticleEmitterInfo.create(level, effect);
        info.bindOnEntity(entity);
        info.scale(size * stretch[0], size * stretch[1], size * stretch[0]);
        AAALevel.addParticle(level, info);
    }

    /**
     * Reposition live HD auras before AAA's end-of-level update/draw so fast flight does not
     * leave the column one frame behind the body (PreDraw alone runs after Effekseer update).
     */
    @SubscribeEvent
    public static void onRenderFollow(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera cam = event.getCamera();
        synchronized (LIVE_AURAS) {
            for (Iterator<LiveAura> it = LIVE_AURAS.iterator(); it.hasNext(); ) {
                LiveAura live = it.next();
                if (!applyLiveFollow(live, partial, cam)) it.remove();
            }
        }
    }

    private static void spawnLive(Entity entity, ResourceLocation effect, float size, float[] initial,
                                  float sink) {
        boolean silhouette = HdAuraPlan.silhouetteEffect(effect.getPath());
        float[] shape = HdAuraPlan.shape(silhouette);
        float drop = HdAuraPlan.drop(silhouette);
        int id = entity.getId();
        WeakReference<Entity> bound = new WeakReference<>(entity);
        mod.chloeprime.aaaparticles.api.client.EffectRegistry.tryLoad(effect).thenAccept(found ->
                found.ifPresent(definition -> definition.playRouted(
                        mod.chloeprime.aaaparticles.api.client.metadata.EffectRouting.QualityOptions.current())
                        .thenAccept(emitter -> {
                            boolean[] stopped = {false};
                            LiveAura live = new LiveAura(bound, emitter, size, shape, drop, silhouette, initial,
                                    stopped);
                            synchronized (LIVE_AURAS) {
                                LIVE_AURAS.add(live);
                            }
                            mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter.PreDrawCallback follow =
                                    (playing, ignoredPartial) -> {
                                        // Prefer the real render partial tick; AAA may pass frame-delta
                                        // shaped values depending on draw path.
                                        Minecraft view = Minecraft.getInstance();
                                        float partial = renderPartialTick(view, ignoredPartial);
                                        Camera cam = view.gameRenderer.getMainCamera();
                                        if (!applyLiveFollow(live, partial, cam)) {
                                            synchronized (LIVE_AURAS) {
                                                LIVE_AURAS.remove(live);
                                            }
                                        }
                                    };
                            follow.accept(emitter, renderPartialTick(Minecraft.getInstance(), 1.0f));
                            emitter.addPreDrawCallback(follow);
                        })))
                .exceptionally(error -> {
                    liveFailed = true;
                    XenoPixelsMod.LOGGER.warn("HD aura live scaling off for this session ({})", String.valueOf(error));
                    return null;
                });
    }

    /** @return false when the emitter should be dropped from {@link #LIVE_AURAS} */
    private static boolean applyLiveFollow(LiveAura live, float partial, Camera cam) {
        if (live.stopped[0]) return false;
        Entity target = live.entity.get();
        mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter playing = live.emitter;
        if (target == null || target.isRemoved() || playing == null || !playing.exists()) {
            if (!live.stopped[0]) {
                live.stopped[0] = true;
                if (playing != null) {
                    try {
                        playing.stop();
                    } catch (RuntimeException ignored) {
                        // Emitter may already be gone.
                    }
                }
            }
            return false;
        }
        float[] stretch = LIVE_STRETCH.get(target.getId());
        if (stretch == null && target instanceof net.minecraft.world.entity.LivingEntity npc
                && !(target instanceof net.minecraft.world.entity.player.Player)) {
            float scale = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAuraClient.auraRenderScale(npc);
            stretch = new float[] {scale, scale};
        }
        float[] k = stretch == null ? live.initial
                : new float[] {stretch[0] * live.shape[0], stretch[1] * live.shape[1]};
        Minecraft view = Minecraft.getInstance();
        if (HdAuraPlan.cameraSpaceInFirstPerson()
                && target == view.player
                && view.options.getCameraType().isFirstPerson()) {
            float fp = HdAuraPlan.firstPersonScaleFactor();
            float sy = live.size * k[1] * fp;
            float[] eye = HdAuraPlan.firstPersonEyeOffset();
            float nudge = HdAuraPlan.firstPersonEmitterCentreNudge(live.silhouette, sy);
            float[] local = {eye[0], eye[1] - nudge, eye[2]};
            float[] at = HdAuraPlan.firstPersonWorldPos(
                    cam.getPosition().x, cam.getPosition().y, cam.getPosition().z,
                    cam.getXRot(), cam.getYRot(), local);
            float[] rot = HdAuraPlan.firstPersonRotationRadians(cam.getXRot(), cam.getYRot());
            playing.setPosition(at[0], at[1], at[2]);
            playing.setRotation(rot[0], rot[1], rot[2]);
            playing.setScale(live.size * k[0] * fp, sy, live.size * k[0] * fp);
            return true;
        }
        // xo/yo/zo — same as Entity.getPosition / Camera / DMZ. Never xOld (lags on absMoveTo).
        float[] at = HdAuraPlan.entityRenderPos(
                target.xo, target.yo, target.zo, target.getX(), target.getY(), target.getZ(), partial);
        float dropY = live.drop * live.size * (k[1] / live.shape[1]);
        playing.setPosition(at[0], at[1] - dropY, at[2]);
        playing.setRotation(0.0f, 0.0f, 0.0f);
        playing.setScale(live.size * k[0], live.size * k[1], live.size * k[0]);
        return true;
    }

    private static float renderPartialTick(Minecraft mc, float fallback) {
        if (mc == null || mc.getTimer() == null) return fallback;
        try {
            return mc.getTimer().getGameTimeDeltaPartialTick(false);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static void spawn(ClientLevel level, Entity entity, String path, float size, float brightness) {
        ParticleEmitterInfo info = ParticleEmitterInfo.create(level, ResourceLocation.fromNamespaceAndPath(
                XenoPixelsMod.MOD_ID, path + AuraPalette.brightnessSuffix(brightness)));
        info.bindOnEntity(entity);
        info.scale(size);
        AAALevel.addParticle(level, info);
    }
}
