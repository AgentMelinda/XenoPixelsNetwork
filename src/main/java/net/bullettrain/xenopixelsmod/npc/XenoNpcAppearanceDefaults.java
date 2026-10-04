package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;

/**
 * What a freshly created NPC looks like before anyone edits it.
 *
 * <p>MyNPCs assigns a texture at creation so a new NPC is immediately presentable; a Xeno NPC used
 * to spawn with an empty texture field and fall back to a single shared skin. Writing a real value
 * per role means a new spawn is identifiable at a glance, the editor shows what it is, and it can be
 * changed like any other field - rather than being an invisible default baked into the renderer.
 *
 * <p>The skins are the vanilla wide player set, which is guaranteed present:
 * {@code textures/entity/player/wide/*.png}.
 */
public final class XenoNpcAppearanceDefaults {

    private static final String SKIN_ROOT = "minecraft:textures/entity/player/wide/";

    /** The stand-in a creature mimics until an operator picks something else. */
    private static final String DEFAULT_CREATURE = "minecraft:wolf";

    private XenoNpcAppearanceDefaults() {
    }

    /**
     * Writes the role's default look into {@code profile}, leaving anything already set alone.
     *
     * <p>Only blank fields are filled, so this is safe to call on an imported NPC that already
     * carries a look of its own.
     */
    public static void apply(NpcCombatProfile profile, XenoNpcRole role) {
        if (profile == null) {
            return;
        }
        XenoNpcRole resolved = role == null ? XenoNpcRole.HUMANOID : role;

        if (resolved.creature()) {
            // The creature role declares body_family "creature"; mimicking a real mob is what makes
            // that mean something, since every role otherwise renders as the same humanoid.
            if (isBlank(profile.modelKind) || NpcCombatProfile.MODEL_VANILLA
                    .equals(NpcCombatProfile.normalizeModelKind(profile.modelKind))) {
                profile.modelKind = NpcCombatProfile.MODEL_ENTITY;
            }
            if (isBlank(profile.modelId)) {
                profile.modelId = DEFAULT_CREATURE;
            }
            return;
        }

        if (isBlank(profile.modelKind)) {
            profile.modelKind = NpcCombatProfile.MODEL_VANILLA;
        }
        if (isBlank(profile.modelTexture)) {
            profile.modelTexture = SKIN_ROOT + skinFor(resolved) + ".png";
        }

        // A humanoid starts in FULL so its DragonMineZ appearance actually renders. Both
        // NpcFullDmzRenderer and NpcDmzAnim require FULL before the model and its parts are used,
        // and the shipped default of OFF meant a new NPC's body type, eyes and colours were
        // editable but inert - and its preview showed a plain humanoid.
        //
        // Only applied at creation. An existing NPC's stored mode is a real choice and is left
        // alone; the appearance screen's Mode row is how that changes.
        if (profile.appearance != null && profile.appearance.mode == NpcDmzAppearance.Mode.OFF) {
            profile.appearance.mode = NpcDmzAppearance.Mode.FULL;
        }
    }

    /** A distinct vanilla skin per role, so a crowd of new NPCs is not all the same person. */
    static String skinFor(XenoNpcRole role) {
        return switch (role) {
            case GUARD -> "efe";
            case TRADER -> "sunny";
            case QUEST -> "zuri";
            case COMPANION -> "ari";
            case TRANSPORTER -> "noor";
            default -> "steve";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
