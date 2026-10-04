package net.bullettrain.xenopixelsmod.npc.inventory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * An NPC's Curios slots, when Curios is installed.
 *
 * <p><b>Everything that names a Curios class lives in {@link NpcCuriosImpl}, not here.</b> This
 * class checks whether the mod is present and only then touches that one, so a server without
 * Curios never loads a class that mentions it.
 *
 * <p><b>In practice Curios cannot be absent, and the gate stays anyway.</b> Curios is on this mod's
 * compile classpath but is not declared in {@code neoforge.mods.toml} — which looks like an
 * undeclared optional dependency, the shape that crashes startup when a class reference escapes a
 * gate. It is not one: {@code xenopixelsmod} hard-requires {@code dragonminez 2.1.3+}, and DMZ in
 * turn hard-requires {@code curios 9.5.1+}. Both were measured by launching a dedicated server
 * without each — FML refuses at pre-load either way, so the configuration this gate defends against
 * is currently unreachable.
 *
 * <p>It is kept because it costs a boolean and the chain is somebody else's to change: a future DMZ
 * that drops its Curios requirement would make the unreachable case reachable, and the failure then
 * is a crash at class load rather than a missing row.
 *
 * <h2>How the slots get there</h2>
 *
 * <p>Read out of the DragonMineZ jar rather than invented. DMZ declares two slots —
 * {@code data/dragonminez/curios/slots/head_tech.json} and {@code .../weights.json} — and binds them
 * in {@code data/dragonminez/curios/entities/dmzslots.json} to <b>{@code minecraft:player} only</b>.
 * That is why a Xeno NPC has none by default: nothing ever gave it any.
 *
 * <p>So this mod ships {@code data/xenopixelsmod/curios/entities/xeno_npcs.json}, binding twelve
 * slot ids to all eight NPC entity types: DMZ's two, and the ten ordinary ones this mod has always
 * given a MyNPCs or CustomNPCs NPC through {@code .../entities/npcs.json}. Reusing existing ids
 * rather than minting our own is the point twice over - DMZ's scouters and weights drop straight
 * in, and a native Xeno NPC ends up with the same slots as the ones this mod already decorates.
 *
 * <h2>What weights do on an NPC</h2>
 *
 * <p>They render. They do not train it. DMZ's training runs off {@code TrainingRewardC2S} and
 * {@code TrainingAnimationC2S} — client-to-server packets sent by a player — and there is no NPC
 * path into any of it. An NPC in weights looks the part and gains nothing, which the editor page
 * says rather than leaving somebody to wonder.
 */
public final class NpcCurios {

    /** Curios' own mod id. */
    public static final String MOD_ID = "curios";

    /**
     * DragonMineZ's own two slot ids.
     *
     * <p>Read out of its jar: {@code data/dragonminez/curios/slots/head_tech.json} and
     * {@code .../weights.json}, bound by {@code .../entities/dmzslots.json} to
     * {@code minecraft:player} alone - which is why a Xeno NPC had none until this mod bound them
     * itself. Scouters, pothalas and the anti-ki cloak go in the first; the turtle shell, workout
     * weights and Piccolo cape go in the second.
     */
    public static final List<String> DMZ_SLOTS = List.of("head_tech", "weights");

    /**
     * The ten ordinary Curios slots this mod already gives a MyNPCs or CustomNPCs NPC.
     *
     * <p>Not a new list. {@code NpcCuriosInventory.SLOT_IDS} has offered exactly these to those
     * NPCs for as long as that integration has existed, through
     * {@code data/xenopixelsmod/curios/entities/npcs.json}. A native Xeno NPC getting a different
     * set would have been the odd one out in its own mod.
     */
    public static final List<String> STANDARD_SLOTS = List.of(
            "curio", "head", "necklace", "back", "body", "bracelet",
            "hands", "ring", "belt", "charm");

    /**
     * Every slot a Xeno NPC is offered: the standard ten, then DragonMineZ's two.
     *
     * <p>Named here so the entity-binding data file and this code cannot drift apart. They are only
     * ever <em>offered</em> - a slot whose declaring mod is absent resolves to nothing, which is why
     * every read goes through {@link #slots} and asks Curios rather than trusting this list.
     */
    public static final List<String> ALL_SLOTS =
            java.util.stream.Stream.concat(STANDARD_SLOTS.stream(), DMZ_SLOTS.stream()).toList();

    private static Boolean present;

    private NpcCurios() {
    }

    /**
     * Whether Curios is installed.
     *
     * <p>Cached, because it cannot change while the game is running and this is asked once per
     * screen open. Null-safe on {@code ModList} for the same reason {@code XenoPadInput} is: it is
     * not populated in a unit test.
     */
    public static boolean installed() {
        if (present == null) {
            ModList list = ModList.get();
            present = list != null && list.isLoaded(MOD_ID);
        }
        return present;
    }

    /**
     * The Curios slots this NPC actually has, or empty.
     *
     * <p>Asked of Curios rather than assumed from {@link #ALL_SLOTS}, so an NPC on a server without
     * DragonMineZ gets the standard ten and not the two DMZ declares, and one on a server where
     * some other mod has added slots to these entity types gets those too. The screen draws what
     * comes back; it never draws a fixed list.
     */
    public static List<String> slots(LivingEntity npc) {
        if (npc == null || !installed()) {
            return List.of();
        }
        try {
            return NpcCuriosImpl.slots(npc);
        } catch (Throwable ignored) {
            // A Curios version whose API moved must degrade to "this NPC has no curios slots", not
            // take the screen down with it.
            return List.of();
        }
    }

    /** What this NPC is wearing in one slot, or {@link ItemStack#EMPTY}. */
    public static ItemStack get(LivingEntity npc, String slot, int index) {
        if (npc == null || slot == null || !installed()) {
            return ItemStack.EMPTY;
        }
        try {
            return NpcCuriosImpl.get(npc, slot, index);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Puts something in one of this NPC's Curios slots.
     *
     * @return whether it went in; false when Curios is absent or the slot does not exist
     */
    public static boolean set(LivingEntity npc, String slot, int index, ItemStack stack) {
        if (npc == null || slot == null || !installed()) {
            return false;
        }
        try {
            return NpcCuriosImpl.set(npc, slot, index, stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Whether the item is accepted by this exact entity slot. Empty stacks are valid clears. */
    public static boolean isValid(LivingEntity npc, String slot, int index, ItemStack stack) {
        if (npc == null || slot == null || !installed()) {
            return false;
        }
        try {
            return NpcCuriosImpl.isValid(npc, slot, index, stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** How many slots of one kind this NPC has. Zero when it has none. */
    public static int size(LivingEntity npc, String slot) {
        if (npc == null || slot == null || !installed()) {
            return 0;
        }
        try {
            return NpcCuriosImpl.size(npc, slot);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** For tests, which have no {@code ModList}. */
    static void overridePresence(Boolean value) {
        present = value;
    }
}
