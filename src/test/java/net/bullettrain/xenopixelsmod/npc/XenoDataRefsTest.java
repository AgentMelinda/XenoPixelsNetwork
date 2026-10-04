package net.bullettrain.xenopixelsmod.npc;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The bug this guards: lines and dialogue never resolved, so no NPC ever spoke.
 *
 * <p>{@code SimpleJsonResourceReloadListener} strips its directory prefix when building keys, so a
 * loader over {@code npcs/lines} registers {@code data/ns/npcs/lines/default.json} as
 * {@code ns:default}. The role JSONs were written with the full path, so every lookup missed - and
 * nothing asserted the key shape, which is exactly why it went unnoticed.
 */
class XenoDataRefsTest {

    @Test
    void aFullPathRefReducesToTheKeyTheLoaderRegistered() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "default"),
                XenoDataRefs.resolve("xenopixelsmod:npcs/lines/default", "npcs/lines"));
        assertEquals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "default"),
                XenoDataRefs.resolve("xenopixelsmod:npcs/dialogue/default", "npcs/dialogue"));
    }

    @Test
    void aBareRefIsPassedThroughUnchanged() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "default"),
                XenoDataRefs.resolve("xenopixelsmod:default", "npcs/lines"));
    }

    @Test
    void bothSpellingsResolveToTheSameKey() {
        // The whole point of accepting both: the datapack did not have to be rewritten.
        assertEquals(XenoDataRefs.resolve("xenopixelsmod:npcs/lines/default", "npcs/lines"),
                XenoDataRefs.resolve("xenopixelsmod:default", "npcs/lines"));
    }

    @Test
    void nestedPathsBelowTheDirectorySurvive() {
        // A pack may organise into subfolders; only the loader's own prefix is removed.
        assertEquals(ResourceLocation.fromNamespaceAndPath("mypack", "villagers/elder"),
                XenoDataRefs.resolve("mypack:npcs/lines/villagers/elder", "npcs/lines"));
    }

    @Test
    void aPrefixThatIsNotTheLoadersIsLeftAlone() {
        // "npcs/dialogue" is not this loader's prefix, so it stays part of the key rather than
        // being half-stripped into something that resolves to the wrong file.
        assertEquals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "npcs/dialogue/default"),
                XenoDataRefs.resolve("xenopixelsmod:npcs/dialogue/default", "npcs/lines"));
    }

    @Test
    void aTrailingJsonExtensionIsDropped() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "default"),
                XenoDataRefs.resolve("xenopixelsmod:npcs/lines/default.json", "npcs/lines"));
    }

    @Test
    void caseAndPaddingDoNotDecideTheOutcome() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "default"),
                XenoDataRefs.resolve("  XenoPixelsMod:NPCS/Lines/Default  ", "npcs/lines"));
    }

    @Test
    void blankAndMalformedRefsAnswerNullRatherThanThrowing() {
        assertNull(XenoDataRefs.resolve(null, "npcs/lines"));
        assertNull(XenoDataRefs.resolve("", "npcs/lines"));
        assertNull(XenoDataRefs.resolve("   ", "npcs/lines"));
        // Nothing left after the prefix is removed.
        assertNull(XenoDataRefs.resolve("xenopixelsmod:npcs/lines/", "npcs/lines"));
        // Not a valid resource location at all.
        assertNull(XenoDataRefs.resolve("not a ref", "npcs/lines"));
    }

    @Test
    void theRefsShippedInTheRoleJsonsResolve() {
        // These exact strings are in src/main/resources/data/xenopixelsmod/xeno_npcs/roles/*.json.
        assertEquals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "default"),
                XenoDataRefs.resolve("xenopixelsmod:npcs/lines/default", "npcs/lines"));
        assertEquals(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "default"),
                XenoDataRefs.resolve("xenopixelsmod:npcs/dialogue/default", "npcs/dialogue"));
    }
}
