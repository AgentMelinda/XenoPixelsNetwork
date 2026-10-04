package net.bullettrain.xenopixelsmod.compat.npc;

import java.util.List;

/**
 * Which combat brains an NPC may run.
 *
 * <p>In XenoPixels this is every version, and a save reads exactly what it stored - it changes
 * nothing. It exists as the one place the XenoNPCs public release swaps (its overlay allows V9 only;
 * owner 2026-09-29: "on the releasing one make so we only got combat brain v9"). Everything that
 * picks a version goes through it: {@code NpcCombatProfile.setBrainVersion}, the saved-profile read,
 * {@link NpcCombatBrainVersion#next()}/{@code nextNative()}, and the editor's version picker.
 */
public final class NpcBrainPolicy {
    private static final List<NpcCombatBrainVersion> CHOICES = List.of(NpcCombatBrainVersion.values());

    private NpcBrainPolicy() {
    }

    /** The versions the editor offers, in order. */
    public static List<NpcCombatBrainVersion> choices() {
        return CHOICES;
    }

    /** The version an NPC actually runs when {@code requested} is asked for (null: the default). */
    public static NpcCombatBrainVersion resolve(NpcCombatBrainVersion requested) {
        return requested == null ? NpcCombatBrainVersion.V1 : requested;
    }
}
