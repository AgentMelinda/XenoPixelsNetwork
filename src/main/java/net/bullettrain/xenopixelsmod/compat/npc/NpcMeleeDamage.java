package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.init.MainDamageTypes;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Scales an NPC's direct (melee) attack damage to {@link NpcCombatProfile#meleeDamage()} --
 * DMZ's real {@code StatsData.getMeleeDamage()} formula ({@code 1.0 + strength * release}),
 * driven by {@code strength} the same way a real player's melee hit is. Before this, an NPC
 * with a combat profile still dealt whatever damage CustomNPCs' own vanilla attack-damage
 * attribute produced, entirely independent of the stats shown in the DMZ wand tab.
 *
 * <p>Ki attacks are untouched here -- {@link NpcKiAttackDispatcher} already computes their own
 * damage and bakes it into the projectile directly, and a ki projectile's damage source has a
 * direct entity distinct from its owner, which is how this is told apart from a real melee hit.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcMeleeDamage {
    /** Fraction of damage a guarding NPC still takes. */
    private static final double GUARD_DAMAGE_MULTIPLIER = 0.35;

    /** Alternates the punching hand per NPC, so a flurry does not throw the same arm twice. */
    private static final java.util.Map<java.util.UUID, Boolean> NEXT_IS_RIGHT =
            new java.util.concurrent.ConcurrentHashMap<>();

    private enum AnimationKind { DMZ, GECKO }

    private record MeleeAnimation(AnimationKind kind, String name) {}

    /** Script-selected animation used only when this NPC's real melee damage lands. */
    private static final java.util.Map<java.util.UUID, MeleeAnimation> MELEE_ANIMATIONS =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Next attack-page slot index for {@link NpcMeleeAnimCycle}. */
    private static final java.util.Map<java.util.UUID, Integer> SLOT_CURSOR =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** One-shot damage scale for brain specials (heavy hit). */
    private static final java.util.Map<java.util.UUID, Float> NEXT_SCALE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private NpcMeleeDamage() {
    }

    /** Selects a renderer-valid clip for subsequent real CustomNPCs melee hits. */
    public static boolean setAnimation(LivingEntity attacker, String animation) {
        if (attacker == null || animation == null || animation.isBlank()) {
            return false;
        }
        String name = animation.trim();
        if (NpcDmzAnim.canAnimate(attacker)) {
            String resolved = net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.resolve(name);
            if (resolved == null) {
                return false;
            }
            MELEE_ANIMATIONS.put(attacker.getUUID(), new MeleeAnimation(AnimationKind.DMZ, resolved));
            return true;
        }
        if (NpcGeckoAnim.canAnimate(attacker)) {
            MELEE_ANIMATIONS.put(attacker.getUUID(), new MeleeAnimation(AnimationKind.GECKO, name));
            return true;
        }
        return false;
    }

    public static void clearAnimation(java.util.UUID npcId) {
        if (npcId != null) {
            MELEE_ANIMATIONS.remove(npcId);
            SLOT_CURSOR.remove(npcId);
            NEXT_IS_RIGHT.remove(npcId);
            NEXT_SCALE.remove(npcId);
        }
    }

    /**
     * Deals a profile-scaled melee hit and plays the PUNCH clip. {@code scale} is a one-shot
     * multiplier (1 = a normal punch, 1.75 = Heavy Hit).
     *
     * <p>Refuses, and answers {@code false}, when the target is out of reach or behind cover. This
     * method had no distance test at all, so every caller could deal melee damage from any range:
     * an NPC would stand off and swing at nothing while its victim took hits. The gate lives here
     * rather than in each caller precisely because there are several of them - a brain, the clone
     * AI and scripts - and only one of them needs to forget for the behaviour to come back. Callers
     * that get {@code false} should fall through to a ranged choice; see
     * {@link NpcCombatRanges#withinMelee}.
     */
    public static boolean hit(LivingEntity attacker, LivingEntity target, float scale) {
        if (attacker == null || target == null || attacker.level().isClientSide()
                || !attacker.isAlive() || !target.isAlive() || attacker == target) {
            return false;
        }
        if (!NpcCombatRanges.withinMelee(attacker, target)) {
            return false;
        }
        NEXT_SCALE.put(attacker.getUUID(), scale <= 0.0f ? 1.0f : scale);
        try {
            onMeleeAttempt(attacker);
            boolean landed = target.hurt(attacker.damageSources().mobAttack(attacker), 1.0f);
            if (landed) {
                NpcCombatProfile profile = NpcCombatProfile.readCached(attacker);
                if (profile.npcMeleeKnockback > 0.0f) {
                    target.knockback(profile.npcMeleeKnockback,
                            attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
                }
                applyNpcMeleeEffect(target, profile);
            }
            return landed;
        } finally {
            NEXT_SCALE.remove(attacker.getUUID());
        }
    }

    /** Applies the profile's stored melee clip (or the default punches when it is blank). */
    public static void applyProfile(LivingEntity entity, NpcCombatProfile profile) {
        if (entity == null) {
            return;
        }
        String anim = profile == null || profile.meleeAnimation == null
                ? "" : profile.meleeAnimation.trim();
        if (anim.isBlank()) {
            MELEE_ANIMATIONS.remove(entity.getUUID());
            return;
        }
        setAnimation(entity, anim);
    }

    /**
     * Stores {@code animation} on the NPC's profile so it survives reload, then applies it.
     * Blank clears the selection.
     */
    public static boolean persist(LivingEntity entity, String animation) {
        if (entity == null || !NpcCombatProfile.hasProfile(entity)) {
            return false;
        }
        String value = animation == null ? "" : animation.trim();
        if (!value.isEmpty()) {
            String resolved = net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.resolve(value);
            if (resolved != null) {
                value = resolved;
            } else if (!NpcGeckoAnim.canAnimate(entity)) {
                return false;
            }
        }
        NpcCombatProfile profile = NpcCombatProfile.read(entity);
        profile.meleeAnimation = value;
        profile.write(entity);
        return true;
    }

    /**
     * Plays the selected clip, or the configured-generation fallback, when CustomNPCs commits a
     * native melee attempt. Called from the CustomNPCs attack path after its meleeAttack script
     * event and cancellation check, but before it invokes {@code hurt} on the target.
     */
    /**
     * Whether this NPC's attack is animated by us rather than by the vanilla arm swing.
     *
     * <p>The NPC mods' melee goal swings and then calls {@code doHurtTarget}, so without this the
     * vanilla swing plays over the top of whatever clip we start a moment later -- a DragonMineZ
     * punch on a Full-appearance NPC fighting a humanoid arm swing, which reads as the limbs
     * snapping. Asked from the swing itself, so the two never run together.
     *
     * <p>Deliberately cheap in the common case: almost every {@code swing} in a game is a player or
     * an ordinary mob, and those fail the first test.
     */
    public static boolean playsOwnAttackAnimation(LivingEntity attacker) {
        boolean nativeXenoNpc = attacker instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
        if (attacker == null
                || (!nativeXenoNpc && !NpcTypes.isNpc(attacker))
                // A native XenoNPC can use its default profile before it has been explicitly
                // saved. That profile carries the built-in ordered DMZ punches; compatibility
                // NPCs still need an authored profile before this hook changes their swing.
                || (!nativeXenoNpc && !NpcCombatProfile.hasProfile(attacker))) {
            return false;
        }
        if (nativeXenoNpc && NpcCombatProfile.MODEL_GECKOLIB.equals(
                NpcCombatProfile.normalizeModelKind(NpcCombatProfile.readCached(attacker).modelKind))) {
            // Native melee goals do not call onMeleeAttempt. Preserve their swing event for
            // ordinary GeckoLib attack clips; the controller ignores it during a scripted clip.
            return false;
        }
        MeleeAnimation selected = MELEE_ANIMATIONS.get(attacker.getUUID());
        if (selected != null) {
            return selected.kind() == AnimationKind.DMZ
                    ? NpcDmzAnim.canAnimate(attacker)
                    : NpcGeckoAnim.canAnimate(attacker);
        }
        NpcCombatProfile profile = NpcCombatProfile.read(attacker);
        if (NpcMeleeAnimCycle.hasEnabledClip(profile)
                && (NpcDmzAnim.canAnimate(attacker) || NpcGeckoAnim.canAnimate(attacker))) {
            return true;
        }
        // No configured clip: the built-in punch only plays on the Full DragonMineZ path.
        return NpcDmzAnim.canAnimate(attacker);
    }

    public static void onMeleeAttempt(LivingEntity attacker) {
        boolean nativeXenoNpc = attacker instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
        if (attacker == null || attacker.level().isClientSide
                || !attacker.isAlive()
                || (!nativeXenoNpc && !NpcCombatProfile.hasProfile(attacker))) {
            return;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(attacker);
        // An explicit script selection owns this hit. The attack-page cycle is a fallback;
        // otherwise meleeHit immediately replaces the script's jab with its default punch.
        MeleeAnimation selected = MELEE_ANIMATIONS.get(attacker.getUUID());
        if (selected != null) {
            boolean played = selected.kind() == AnimationKind.DMZ
                    ? NpcDmzAnim.play(attacker, selected.name())
                    : NpcGeckoAnim.play(attacker, selected.name());
            if (played) return;
        }
        NpcMeleeAnimCycle.Pick pick = NpcMeleeAnimCycle.next(profile,
                SLOT_CURSOR.getOrDefault(attacker.getUUID(), 0));
        if (!pick.clip().isBlank()) {
            SLOT_CURSOR.put(attacker.getUUID(), pick.nextCursor());
            if (playNamed(attacker, pick.clip())) {
                return;
            }
        }
        if (net.bullettrain.xenopixelsmod.anim.CombatStateAnim.hasCustom(attacker,
                net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot.PUNCH)
                && net.bullettrain.xenopixelsmod.anim.CombatStateAnim.play(attacker,
                net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot.PUNCH)) {
            return;
        }
        if (!NpcDmzAnim.canAnimate(attacker)) {
            // No swing of our own here. The NPC mods' melee goal already calls swing() immediately
            // before doHurtTarget, so an NPC with no clip of ours still animates -- and adding a
            // second swing in the same tick restarted the arm mid-stroke, which is precisely the
            // snapping this was meant to cure.
            NpcGeckoAnim.playAttack(attacker);
            return;
        }
        boolean right = NEXT_IS_RIGHT.merge(attacker.getUUID(), true, (old, ignored) -> !old);
        NpcDmzAnim.play(attacker, right
                ? net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.BODY_PUNCH_RIGHT
                : net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.BODY_PUNCH_LEFT);
    }

    private static boolean playNamed(LivingEntity attacker, String animation) {
        if (attacker == null || animation == null || animation.isBlank()) {
            return false;
        }
        String name = animation.trim();
        if (NpcDmzAnim.canAnimate(attacker)) {
            String resolved = net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.resolve(name);
            if (resolved != null && NpcDmzAnim.play(attacker, resolved)) {
                return true;
            }
        }
        return NpcGeckoAnim.canAnimate(attacker) && NpcGeckoAnim.play(attacker, name);
    }

    /**
     * HIGHEST, so the real damage is in place before DragonMineZ's {@code CombatEvent.onLivingHurt}
     * (HIGH) records the hit as the player's {@code dmz_raw_damage}; its LOWEST
     * {@code overrideVanillaArmorReduction} rebuilds the damage from that raw value less defense, so
     * an amount set any later is thrown away. 2026-09-30 owner: "he still cant hit me".
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onNpcMelee(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && event.getSource().getDirectEntity() == attacker
                && !MainDamageTypes.isStrikeAttackDamage(event.getSource())
                && (NpcCombatProfile.hasProfile(attacker)
                    || attacker instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity)) {
            NpcCombatProfile profile = NpcCombatProfile.read(attacker);
            double meleeDamage = NpcDmzStats.meleeDamage(attacker, profile);
            double staminaCost = Math.max(1.0, Math.ceil(meleeDamage
                    * ConfigManager.getCombatConfig().getStaminaConsumptionRatio()));
            // Tired makes a punch weaker, not meaningless. The cost rises with the damage while the
            // pool is set by RES, so any NPC built to hit hard cannot pay in full -- and the old
            // all-or-nothing spend then dropped the hit to a flat 1.0, which is why a character with
            // enormous strength still scratched.
            float dmzDamage = (float) (meleeDamage
                    * NpcResources.spendStaminaPartial(attacker, profile, staminaCost));
            float residual = Math.max(0f, event.getOriginalDamage() - 1.0f);
            float damage = switch (XenoServerConfig.normalizedNpcDamageMode()) {
                case "mynpc" -> event.getOriginalDamage();
                case "numeric" -> XenoServerConfig.npcNumericDamage;
                default -> dmzDamage;
            };
            if (XenoServerConfig.normalizedNpcDamageMode().equals("dmz")
                    && !XenoServerConfig.npcDmzStatsAuthoritative) {
                damage += residual;
            }
            if (profile.npcMeleeDamage > 0.0f) {
                damage = profile.npcMeleeDamage;
            }
            float scale = NEXT_SCALE.getOrDefault(attacker.getUUID(), 1.0f);
            event.setNewDamage(damage * scale);
            if (attacker instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) {
                LivingEntity target = event.getEntity();
                final float dealt = event.getNewDamage();
                var scriptEvent = net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.fireEvent(
                        npc, "meleeAttack", target instanceof net.minecraft.world.entity.player.Player
                                ? target : null, null, target, dealt,
                        n -> new xenoapi.npcs.api.event.NpcEvent.MeleeAttackEvent(n, target, dealt));
                if (scriptEvent != null) {
                    float scripted = scriptEvent.getDamage();
                    event.setNewDamage(scriptEvent.isCanceled() || !Float.isFinite(scripted)
                            ? 0.0f : Math.max(0.0f, scripted));
                }
            }
        }

        if (NpcCombatProfile.hasProfile(event.getEntity())) {
            NpcCombatProfile defender = NpcCombatProfile.read(event.getEntity());
            double defense = NpcStatMath.defense(defender.resistance,
                    NpcFormLookup.multiplier(defender, "DEF"), defender.releaseMultiplier());
            var combat = ConfigManager.getCombatConfig();
            double scale = Math.max(12.0, (defender.resistance + 20.0)
                    * combat.getDefenseReductionScale());
            double mitigated = NpcStatMath.mitigate(event.getNewDamage(), defense, scale,
                    combat.getBaseDamageReductionCap());
            // A guarding NPC takes the same kind of flat reduction a guarding player does.
            // Guard is this mod's own concept (NpcCombatMoves) -- CustomNPCs has no equivalent
            // state -- and is set either by a script or by the combat brain.
            if (NpcCombatMoves.isGuarding(event.getEntity())) {
                mitigated *= GUARD_DAMAGE_MULTIPLIER;
            }
            event.setNewDamage((float) mitigated);
            int resistance = NpcDamageCategory.resistanceFor(
                    event.getSource(), defender);
            if (resistance > 0) {
                event.setNewDamage(event.getNewDamage() * (1.0f - resistance / 100.0f));
            }
        }
    }

    private static void applyNpcMeleeEffect(LivingEntity target, NpcCombatProfile profile) {
        if (target == null || profile == null || profile.npcMeleeEffect == null
                || profile.npcMeleeEffect.isBlank()) {
            return;
        }
        if ("minecraft:fire".equals(profile.npcMeleeEffect)) {
            target.igniteForSeconds(Math.max(1, profile.npcMeleeEffectDuration / 20));
            return;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation
                .tryParse(profile.npcMeleeEffect);
        if (id == null) return;
        net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(effect ->
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,
                        profile.npcMeleeEffectDuration, profile.npcMeleeEffectAmplifier)));
    }
}
