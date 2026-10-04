package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a new NPC looks like, and the appearance-mode fix behind "body type does nothing".
 */
class XenoNpcAppearanceDefaultsTest {

    @Test
    void aNewHumanoidStartsInFullAppearanceMode() {
        // The shipped default is OFF, and both NpcFullDmzRenderer and NpcDmzAnim require FULL
        // before the DMZ model or its parts are used at all. A new NPC on OFF is why body type,
        // eyes and colours were editable but inert.
        NpcCombatProfile profile = new NpcCombatProfile();
        assertEquals(NpcDmzAppearance.Mode.OFF, profile.appearance.mode, "shipped default");

        XenoNpcAppearanceDefaults.apply(profile, XenoNpcRole.HUMANOID);
        assertEquals(NpcDmzAppearance.Mode.FULL, profile.appearance.mode);
    }

    @Test
    void anExistingChoiceOfOverlayIsNotOverwritten() {
        // Only OFF is promoted. Someone who deliberately chose OVERLAY keeps it.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.appearance.mode = NpcDmzAppearance.Mode.OVERLAY;

        XenoNpcAppearanceDefaults.apply(profile, XenoNpcRole.HUMANOID);
        assertEquals(NpcDmzAppearance.Mode.OVERLAY, profile.appearance.mode);
    }

    @Test
    void eachHumanoidRoleGetsItsOwnSkin() {
        for (XenoNpcRole role : new XenoNpcRole[]{XenoNpcRole.HUMANOID, XenoNpcRole.GUARD,
                XenoNpcRole.TRADER, XenoNpcRole.QUEST, XenoNpcRole.COMPANION}) {
            NpcCombatProfile profile = new NpcCombatProfile();
            XenoNpcAppearanceDefaults.apply(profile, role);

            assertEquals(NpcCombatProfile.MODEL_VANILLA, profile.modelKind, role.id());
            assertTrue(profile.modelTexture.startsWith("minecraft:textures/entity/player/wide/"),
                    role.id() + " -> " + profile.modelTexture);
            // The path that rendered every NPC as a checkerboard.
            assertTrue(profile.modelTexture.endsWith(".png"));
        }
    }

    @Test
    void theRolesDoNotAllShareOneSkin() {
        assertEquals("efe", XenoNpcAppearanceDefaults.skinFor(XenoNpcRole.GUARD));
        assertEquals("sunny", XenoNpcAppearanceDefaults.skinFor(XenoNpcRole.TRADER));
        assertEquals("steve", XenoNpcAppearanceDefaults.skinFor(XenoNpcRole.HUMANOID));
    }

    @Test
    void aCreatureMimicsAnEntityInsteadOfWearingASkin() {
        NpcCombatProfile profile = new NpcCombatProfile();
        XenoNpcAppearanceDefaults.apply(profile, XenoNpcRole.CREATURE);

        assertEquals(NpcCombatProfile.MODEL_ENTITY, profile.modelKind);
        assertEquals("minecraft:wolf", profile.modelId);
        // A creature is not a DMZ humanoid, so promoting it to FULL would be wrong.
        assertEquals(NpcDmzAppearance.Mode.OFF, profile.appearance.mode);
    }

    @Test
    void alreadySetFieldsAreLeftAlone() {
        // Safe to call on an imported NPC that already carries a look of its own.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.modelTexture = "mypack:textures/entity/custom.png";
        profile.modelKind = NpcCombatProfile.MODEL_GECKOLIB;
        profile.modelId = "mypack:ogre";

        XenoNpcAppearanceDefaults.apply(profile, XenoNpcRole.GUARD);

        assertEquals("mypack:textures/entity/custom.png", profile.modelTexture);
        assertEquals(NpcCombatProfile.MODEL_GECKOLIB, profile.modelKind);
        assertEquals("mypack:ogre", profile.modelId);
    }

    @Test
    void aNullProfileIsIgnoredRatherThanThrowing() {
        XenoNpcAppearanceDefaults.apply(null, XenoNpcRole.HUMANOID);
        // A null role falls back to humanoid rather than failing.
        NpcCombatProfile profile = new NpcCombatProfile();
        XenoNpcAppearanceDefaults.apply(profile, null);
        assertTrue(profile.modelTexture.endsWith("steve.png"));
    }
}
