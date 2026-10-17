package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAccess;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Writes one {@link NpcAppearanceSource} onto a DragonMineZ {@code Character}, and says honestly
 * whether it can.
 *
 * <p>Two callers, one field table: the FULL-mode NPC renderer, which paints an NPC's look onto its
 * client-side player proxy every sync, and the server-side appearance steal, which borrows the same
 * look for a real player. Having them maintained separately is how the two render modes came to
 * disagree about hair once already, so the mapping lives here and both go through it.
 *
 * <p>Only the appearance fields are copied. Forms, stack forms, masteries, skills, stats and quest
 * history are the target's own business - a look can be lent to a fighter, its progression cannot -
 * and {@code armored} is left alone because the renderer sets that for its proxy, not the profile.
 */
public final class NpcAppearanceMapping {
    /** Why a write was refused, so a script or a log can say something more useful than "failed". */
    public enum Result {
        OK,
        /** DragonMineZ has no loaded data for this player yet - usually just "ask again next tick". */
        NO_DATA,
        /** There was no look to copy. */
        NO_SOURCE,
        INVALID_RACE,
        INVALID_CLASS,
        INVALID_COLOR,
        /** The hair code cannot be read; copying it would leave the target bald rather than styled. */
        INVALID_HAIR,
        /** This player is already borrowing a look; the first one has to be returned. */
        ALREADY_ACTIVE,
        /** No borrow is active for this player. */
        NOT_ACTIVE,
        /** DragonMineZ refused the write, or the stored snapshot turned out to be unreadable. */
        INTERNAL
    }

    private NpcAppearanceMapping() {}

    /**
     * Whether {@code source} can be written onto {@code player} right now, without touching either.
     *
     * <p>Validates against DragonMineZ's own loaded config, the same list its character-creation
     * packet checks ({@code RaceStatsConfig.getAllClasses()}), so a look that came from an older or
     * renamed config is refused instead of half-applied.
     */
    public static Result validate(ServerPlayer player, NpcAppearanceSource source) {
        if (source == null) {
            return Result.NO_SOURCE;
        }
        Result look = inspect(source);
        if (look != Result.OK) {
            return look;
        }
        if (player == null || DmzAccess.stats(player).isEmpty()) {
            return Result.NO_DATA;
        }
        try {
            if (ConfigManager.getLoadedRaces().isEmpty()) {
                return Result.NO_DATA;
            }
            if (!ConfigManager.isRaceLoaded(source.race())) {
                return Result.INVALID_RACE;
            }
            Collection<String> classes = ConfigManager.getRaceStats(source.race()).getAllClasses();
            if (classes.isEmpty()) {
                // A race with no classes listed means its stats config never loaded; refusing with
                // INVALID_CLASS would blame the look for a server that is still starting.
                return Result.NO_DATA;
            }
            if (!classes.contains(source.characterClass())) {
                return Result.INVALID_CLASS;
            }
            return Result.OK;
        } catch (RuntimeException unavailable) {
            return Result.NO_DATA;
        }
    }

    /**
     * The part of {@link #validate} that needs no loaded config: colours the wire can carry and a
     * hair code DragonMineZ can read.
     */
    public static Result inspect(NpcAppearanceSource source) {
        if (source == null) {
            return Result.NO_SOURCE;
        }
        if (!validColor(source.bodyColor()) || !validColor(source.bodyColor2())
                || !validColor(source.bodyColor3()) || !validColor(source.eye1Color())
                || !validColor(source.eye2Color()) || !validColor(source.hairColor())
                || !validColor(source.auraColor())) {
            return Result.INVALID_COLOR;
        }
        if (!NpcHairSet.validCode(source.hairCode())) {
            return Result.INVALID_HAIR;
        }
        return Result.OK;
    }

    /** Everything except the four hair slots, which the renderer wants to keep behind its own guard. */
    public static void applyAppearance(Character target, NpcAppearanceSource source) {
        // setRace is the only setter with a side effect of its own: it lowercases and forces the
        // gender back to male for races that have no female model. Set it first, then write the
        // gender only when the race actually offers the choice, which is DragonMineZ's own rule.
        target.setRace(source.race());
        if (target.canHaveGender()) {
            target.setGender(source.gender());
        }
        target.setCharacterClass(source.characterClass());
        target.setBodyType(source.bodyType());
        target.setEyesType(source.eyesType());
        target.setNoseType(source.noseType());
        target.setMouthType(source.mouthType());
        target.setTattooType(source.tattooType());
        target.setBoobScale(source.boobScale());
        target.setBodyColor(source.bodyColor());
        target.setBodyColor2(source.bodyColor2());
        target.setBodyColor3(source.bodyColor3());
        target.setEye1Color(source.eye1Color());
        target.setEye2Color(source.eye2Color());
        target.setHairColor(source.hairColor());
        target.setAuraColor(source.auraColor());
        target.setActiveHeadBone(source.activeHeadBone());
        target.setHasSaiyanTail(source.saiyanTail());
        target.setRenderHairBase(source.renderHairBase());
        // DragonMineZ reads the custom hair only when hairId is 0 (HairManager.getEffectiveHair);
        // any other value resolves a built-in preset instead. Character seeds hairId from the race
        // config's defaultHairType, so leaving it alone lets a server config silently override the
        // copied hair. Set it deliberately, every time.
        target.setHairId(source.hairId());
    }

    /** The four hair slots, deep-copied so the target owns them and cannot alias the source. */
    public static void applyHair(Character target, NpcAppearanceSource source) {
        target.setHairBase(source.hairSlot(0));
        target.setHairSSJ(source.hairSlot(1));
        target.setHairSSJ2(source.hairSlot(2));
        target.setHairSSJ3(source.hairSlot(3));
    }

    /** The whole look in one call. */
    public static void apply(Character target, NpcAppearanceSource source) {
        applyAppearance(target, source);
        applyHair(target, source);
        target.updateOozaruCache();
    }

    /**
     * Pushes a server-side appearance change to that player and everyone tracking them.
     *
     * <p>Both packets, because they carry different halves: {@code AppearanceSyncS2C} is the model
     * data every nearby client needs, {@code StatsSyncS2C} is what the local player's own HUD and
     * character screen read. DragonMineZ's own hair and tail commands send the pair; sending one
     * leaves another viewer seeing the old look.
     */
    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.refreshDimensions();
        NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(player), player);
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    /** The player's character, or null when DragonMineZ has nothing loaded for them. */
    public static Character character(ServerPlayer player) {
        return DmzAccess.stats(player).map(StatsData::getCharacter).orElse(null);
    }

    private static boolean validColor(String value) {
        return !NpcCombatProfile.canonicalizeHairColor(value).isEmpty();
    }
}
