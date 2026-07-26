package net.bullettrain.xenopixelsmod.features;

import net.bullettrain.xenopixelsmod.config.XenoFeaturesConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Honest inventory of what the "Xenoverse feature pack" actually is.
 *
 * <p>A previous AI scaffold generated managers under {@code features.*}. Most of those
 * classes are <b>in-memory data stubs</b> — they do not listen to DMZ events, have no
 * player GUI, no persistence, and do not change combat. Only form registration + the
 * real combat/HUD/form-bootstrap systems are LIVE in this addon.
 */
public final class FeatureStatus {

    public enum Tier {
        /** Wired into DMZ / Forge and used in normal play. */
        LIVE,
        /** Code exists as a data API only — not hooked to gameplay. */
        STUB
    }

    public record Entry(
            String id,
            String title,
            Tier tier,
            String packagePath,
            String summary) {
    }

    private FeatureStatus() {
    }

    /** Full list for menus / logs. */
    public static List<Entry> all() {
        List<Entry> list = new ArrayList<>();

        // --- LIVE (real DMZ addon surface) ---
        list.add(new Entry(
                "dmz_forms",
                "Custom DMZ Forms",
                Tier.LIVE,
                "dmz.DmzContentBootstrap + data/.../forms",
                "Installs xenopixels_fan_ss / godforms / legendary into config/dragonminez. "
                        + "Real transformable forms. /dmzform add + masters. LIVE."));
        list.add(new Entry(
                "form_mult",
                "Form Multipliers",
                Tier.LIVE,
                "config.XenoFeaturesConfig + DmzContentBootstrap",
                "Scales form STR/PWR/DEF/… in installed DMZ JSON and the mod form catalog. LIVE."));
        list.add(new Entry(
                "form_registry",
                "Form Catalog (menu)",
                Tier.LIVE,
                "features.transformation.XenoFormRegistry",
                "Menu CUSTOM FORMS list with config-scaled stats. Mirrors DMZ form set. LIVE for UI."));
        list.add(new Entry(
                "bt3_combat",
                "BT3 / Sparking Combat",
                Tier.LIVE,
                "client.combat.Bt3CombatClient + network.Bt3CombatPacket",
                "Vanish, chase, backstep, combo, charge fist/kick, dragon dash on DMZ lock-on. LIVE."));
        list.add(new Entry(
                "ki_overcharge",
                "KI Overcharge",
                Tier.LIVE,
                "event.KiOverchargeHandler",
                "Scales DMZ KI projectiles when power release is high. LIVE."));
        list.add(new Entry(
                "xeno_hud",
                "XenoPixels HUD + Tech Hotbar",
                Tier.LIVE,
                "client.XenoHudOverlay + XenoTechniqueHotbarOverlay",
                "XV2-style HP/KI/STM HUD, technique slots from DMZ, party strip, cooldown HUD. LIVE."));
        list.add(new Entry(
                "dmz_hooks",
                "DMZ Event Hooks",
                Tier.LIVE,
                "event.DmzHooks",
                "Listens to DMZ TP/health events (safe no-op / extension points). LIVE wiring."));
        list.add(new Entry(
                "training_dummy",
                "Training Dummy + Damage Meter",
                Tier.LIVE,
                "features.progression + /xenotrain",
                "Armor-stand dummy, session/total damage meter, skill-point milestones. LIVE."));
        list.add(new Entry(
                "super_souls",
                "Super Souls",
                Tier.LIVE,
                "features.progression.SuperSoulCatalog + SuperSoulItem",
                "Equipable passives (damage, guard, sparking, ultimate). /xenosoul + items. LIVE."));
        list.add(new Entry(
                "combat_skills",
                "Combat Skill Tree (slim)",
                Tier.LIVE,
                "features.progression.CombatSkills + /xenoskill",
                "4 skills max Lv.3 (power/guard/sparking/ultimate) via skill points. LIVE."));
        list.add(new Entry(
                "parallel_quests",
                "Parallel Quest Lite",
                Tier.LIVE,
                "features.progression.ParallelQuests + /xenoquest",
                "kill_mobs / kill_players / dummy_session objectives + skill-point rewards. LIVE."));
        list.add(new Entry(
                "mentor",
                "Mentor Pairing",
                Tier.LIVE,
                "features.progression + /xenomentor",
                "Student–mentor link; nearby mentor gains sparking meter from student damage. LIVE."));

        // --- STUBS (Qwen scaffold — not gameplay) ---
        list.add(new Entry(
                "skill_tree",
                "Skill Tree System (full UI)",
                Tier.STUB,
                "features.skilltree",
                "Old in-memory node lists. Replaced in-play by slim CombatSkills; this package still STUB."));
        list.add(new Entry(
                "customization",
                "Character Customization",
                Tier.STUB,
                "features.customization",
                "Cosmetic registry + equip maps in memory. Does not change DMZ character creator / skins. STUB."));
        list.add(new Entry(
                "combo_training",
                "Combo Training Challenges",
                Tier.STUB,
                "features.combo.ComboTrainingSystem",
                "Star-rank timing challenges still stub. Live dummy is /xenotrain, not this class. STUB."));
        list.add(new Entry(
                "time",
                "Time Manipulation",
                Tier.STUB,
                "features.time",
                "Does not call ServerLevel setDayTime / weather. Data bag only. STUB."));
        list.add(new Entry(
                "teaching",
                "Teaching System (NPC)",
                Tier.STUB,
                "features.teaching",
                "No NPC teachers. Live mentor is player-to-player /xenomentor. STUB."));
        list.add(new Entry(
                "boss",
                "Boss Challenge Mode",
                Tier.STUB,
                "features.bosschallenge",
                "No dungeon dimension, no scanner items, no boss entities. STUB."));
        list.add(new Entry(
                "twin_art",
                "Twin Art / Dual-Wield",
                Tier.STUB,
                "features.twinart",
                "No Curios/weapon hooks. Pairing map only. STUB."));
        list.add(new Entry(
                "dojo",
                "Dojo Hub",
                Tier.STUB,
                "features.dojo",
                "No hub structure, no quest board UI. STUB."));
        list.add(new Entry(
                "move_fx",
                "Move Effects Manager",
                Tier.STUB,
                "features.moveeffects",
                "Does not spawn particles; live combat uses DmzAnimHelper instead. STUB."));
        list.add(new Entry(
                "transformation_mgr",
                "TransformationManager (generic)",
                Tier.STUB,
                "features.transformation.TransformationManager",
                "Generic form state map. Real transforms are DMZ form skills, not this class. "
                        + "XenoFormRegistry only feeds the menu catalog. Mostly STUB for combat."));

        return list;
    }

    public static List<Entry> live() {
        return all().stream().filter(e -> e.tier() == Tier.LIVE).toList();
    }

    public static List<Entry> stubs() {
        return all().stream().filter(e -> e.tier() == Tier.STUB).toList();
    }

    /** Whether a stub feature flag is currently enabled in config (still does nothing in-world). */
    public static boolean stubFlagOn(String id) {
        return switch (id) {
            case "skill_tree" -> XenoFeaturesConfig.skillTreeEnabled;
            case "customization" -> XenoFeaturesConfig.customizationEnabled;
            case "combo_training" -> XenoFeaturesConfig.comboTrainingEnabled;
            case "time" -> XenoFeaturesConfig.timeManipulationEnabled;
            case "teaching" -> XenoFeaturesConfig.teachingEnabled;
            case "boss" -> XenoFeaturesConfig.bossChallengeEnabled;
            case "twin_art" -> XenoFeaturesConfig.twinArtEnabled;
            case "dojo" -> XenoFeaturesConfig.dojoEnabled;
            case "move_fx" -> XenoFeaturesConfig.moveEffectsEnabled;
            default -> false;
        };
    }
}
