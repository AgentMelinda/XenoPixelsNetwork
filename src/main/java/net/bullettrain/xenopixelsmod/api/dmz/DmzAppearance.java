package net.bullettrain.xenopixelsmod.api.dmz;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAppearanceMapping;
import net.bullettrain.xenopixelsmod.compat.npc.PlayerAppearanceSteals;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Take a player's DragonMineZ look off them and put the exact same one back.
 *
 * <p>{@link DmzAccess} reads; this writes, and it writes only in the one situation where changing
 * a player's look is safe to hand out: paired with the snapshot that puts it back. Capture first,
 * change as much as you like, restore the snapshot, and the player has what they had - including
 * the forms, masteries and history you were never supposed to touch, because the snapshot carries
 * the whole {@code Character}, not a summary.
 *
 * <p>Call both on the server thread, and follow a successful {@link #restore} with
 * {@link DmzSync#syncStats(ServerPlayer)} the same way you would after any other mutation.
 */
public final class DmzAppearance {
    /** Marks a tag as one of ours, so a hand-written or truncated compound is refused, not half-read. */
    private static final String MARKER = "xenopixels:AppearanceSnapshot";
    private static final int MARKER_VALUE = 1;

    /**
     * Every key DragonMineZ's {@code Character.load} reads unconditionally, plus the four hair
     * compounds.
     *
     * <p>A missing key here is not a missing field - {@code load} assigns whatever the tag gives it,
     * and an absent string reads back as {@code ""}, so a partial snapshot silently blanks part of
     * the player's face. The hair compounds are on the list for the opposite reason: {@code load}
     * skips them when absent, so borrowed hair would stay put forever.
     */
    private static final String[] REQUIRED_KEYS = {
            "Race", "Gender", "Class", "HairId",
            "HairBase", "HairSSJ", "HairSSJ2", "HairSSJ3",
            "ActiveHeadBone", "BodyType", "EyesType", "NoseType", "MouthType", "TattooType",
            "BoobScale", "BodyColor", "BodyColor2", "BodyColor3", "HairColor",
            "Eye1Color", "Eye2Color", "AuraColor",
            "SelectedFormGroup", "CurrentFormGroup", "SelectedForm", "CurrentForm",
            "ActiveFormItemDurationTicks", "PreviousFormGroup", "PreviousForm",
            "HasPreviousFormRecord",
            "SelectedStackFormGroup", "CurrentStackFormGroup", "SelectedStackForm",
            "CurrentStackForm", "ActiveStackFormItemDurationTicks", "PreviousStackFormGroup",
            "PreviousStackForm", "HasPreviousStackFormRecord",
            "HasSaiyanTail", "RenderHairBase", "isArmored"
    };

    private DmzAppearance() {}

    /**
     * The player's whole DragonMineZ character as a tag, or {@code null} when their data is not
     * loaded (a player who just joined, or a non-player).
     */
    public static CompoundTag capture(ServerPlayer player) {
        Character source = NpcAppearanceMapping.character(player);
        return source == null ? null : snapshot(source);
    }

    /** The same snapshot taken from a {@code Character} already in hand. */
    public static CompoundTag snapshot(Character source) {
        if (source == null) {
            return null;
        }
        CompoundTag tag = source.save();
        tag.putInt(MARKER, MARKER_VALUE);
        return tag;
    }

    /**
     * Puts a captured look back on that player, exactly.
     *
     * @return {@code false} - with nothing touched - when the tag is not a complete snapshot or the
     *         player has no loaded DragonMineZ data
     */
    public static boolean restore(ServerPlayer player, CompoundTag snapshot) {
        return restore(NpcAppearanceMapping.character(player), snapshot);
    }

    /**
     * Puts a captured look back, exactly.
     *
     * <p>Fails closed: unless every key {@code Character.load} reads is present, nothing is touched
     * and this returns {@code false}, so a bad snapshot cannot leave a player half-restored. After
     * {@code true}, sync - see {@link NpcAppearanceMapping#sync(ServerPlayer)}.
     */
    public static boolean restore(Character target, CompoundTag snapshot) {
        if (target == null || !isSnapshot(snapshot)) {
            return false;
        }
        try {
            target.load(snapshot);
            // Include every 2.2 slot. Older saved snapshots have no SSJ4 and restore it as empty.
            for (var slot : com.dragonminez.common.hair.HairStyleSlot.values()) {
                target.setHairStyle(slot, hairSlot(snapshot, slot.getNbtKey()));
            }
            target.updateOozaruCache();
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** Whether this is a complete snapshot from {@link #capture}, and safe to hand to {@code load}. */
    public static boolean isSnapshot(CompoundTag tag) {
        if (tag == null || tag.getInt(MARKER) != MARKER_VALUE) {
            return false;
        }
        for (String key : REQUIRED_KEYS) {
            if (!tag.contains(key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Wears {@code npc}'s DragonMineZ look - race, gender, class, body shape, colours and hair - for
     * {@code ticks} game ticks, then gives the player's own look back automatically.
     *
     * <p>Nothing else moves: stats, skills, forms, masteries and quest progress stay the player's own,
     * and the borrow is remembered in the player's XenoPixels data, so a death, a relog or a server
     * restart in the middle of it still ends with their own face.
     *
     * <p>One borrow at a time - a second call while one is outstanding changes nothing. Use
     * {@link #capture}/{@link #restore} instead when the addon wants to own the timing.
     *
     * @return {@code false} when the player has no loaded DragonMineZ data, {@code npc} is not a
     *         Xeno NPC, the look does not fit this server's config, or a borrow is already running
     */
    public static boolean stealFromNpc(ServerPlayer player, Entity npc, long ticks) {
        return PlayerAppearanceSteals.start(player, npc, ticks) == NpcAppearanceMapping.Result.OK;
    }

    /** Gives back a borrowed look early. {@code false} when this player is not wearing one. */
    public static boolean restore(ServerPlayer player) {
        return PlayerAppearanceSteals.stop(player) == NpcAppearanceMapping.Result.OK;
    }

    /** Whether this player is currently wearing a borrowed look. */
    public static boolean isStolen(ServerPlayer player) {
        return PlayerAppearanceSteals.isActive(player);
    }

    /** Ticks left on the borrow, {@code 0} when there is none. */
    public static long stolenTicksLeft(ServerPlayer player) {
        return PlayerAppearanceSteals.remainingTicks(player);
    }

    private static CustomHair hairSlot(CompoundTag snapshot, String key) {
        CustomHair hair = new CustomHair();
        if (snapshot.contains(key)) {
            hair.load(snapshot.getCompound(key));
        }
        return hair;
    }
}
