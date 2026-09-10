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

    private NpcMeleeDamage() {
    }

    /** Selects a renderer-valid clip for subsequent real CustomNPCs melee hits. */
    public static boolean setAnimation(LivingEntity attacker, String animation) {
        if (attacker == null || animation == null || animation.isBlank()) {
            return false;
        }
        String name = animation.trim();
        if (NpcDmzAnim.canAnimate(attacker)) {
            if (!net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog.isPlayable(name)) {
                return false;
            }
            MELEE_ANIMATIONS.put(attacker.getUUID(), new MeleeAnimation(AnimationKind.DMZ, name));
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
            NEXT_IS_RIGHT.remove(npcId);
        }
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
        if (attacker == null || !NpcTypes.isNpc(attacker) || !NpcCombatProfile.hasProfile(attacker)) {
            return false;
        }
        MeleeAnimation selected = MELEE_ANIMATIONS.get(attacker.getUUID());
        if (selected != null) {
            return selected.kind() == AnimationKind.DMZ
                    ? NpcDmzAnim.canAnimate(attacker)
                    : NpcGeckoAnim.canAnimate(attacker);
        }
        // No configured clip: the built-in punch only plays on the Full DragonMineZ path.
        return NpcDmzAnim.canAnimate(attacker);
    }

    public static void onMeleeAttempt(LivingEntity attacker) {
        if (attacker == null || attacker.level().isClientSide
                || !attacker.isAlive() || !NpcCombatProfile.hasProfile(attacker)) {
            return;
        }
        MeleeAnimation selected = MELEE_ANIMATIONS.get(attacker.getUUID());
        if (selected != null) {
            boolean played = selected.kind() == AnimationKind.DMZ
                    ? NpcDmzAnim.play(attacker, selected.name())
                    : NpcGeckoAnim.play(attacker, selected.name());
            if (played) {
                return;
            }
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

    @SubscribeEvent
    public static void onNpcMelee(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && event.getSource().getDirectEntity() == attacker
                && !MainDamageTypes.isStrikeAttackDamage(event.getSource())
                && NpcCombatProfile.hasProfile(attacker)) {
            NpcCombatProfile profile = NpcCombatProfile.read(attacker);
            double staminaCost = Math.max(1.0, Math.ceil(profile.meleeDamage()
                    * ConfigManager.getCombatConfig().getStaminaConsumptionRatio()));
            float damage = NpcResources.spendStamina(attacker, profile, staminaCost)
                    ? profile.meleeDamage() : 1.0f;
            float residual = XenoServerConfig.npcDmzStatsAuthoritative
                    ? 0.0f : Math.max(0f, event.getOriginalDamage() - 1.0f);
            event.setNewDamage(damage + residual);
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
        }
    }
}
