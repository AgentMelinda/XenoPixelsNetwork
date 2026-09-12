package net.bullettrain.xenopixelsmod.dmz.form;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Pure rules for the DMZ skill-master role: the role id sentinel and the "is this NPC a
 * skill-giving master" predicate.
 *
 * <p>Kept free of Minecraft and NPC-mod types so the id arithmetic and the module predicate are
 * exercised directly by tests. The mixins and the interaction path both call into here, so they
 * cannot disagree about what counts as a skill master.
 *
 * <h2>Why the sentinel needs a HEAD injection</h2>
 *
 * <p>CustomNPCs' {@code DataAdvanced.setRole(int)} pre-normalizes its argument:
 *
 * <pre>{@code if (id >= 8) id -= 2; id %= 8; }</pre>
 *
 * <p>Every input therefore collapses into {@code 0..7}, which are exactly the seven native
 * CustomNPCs roles. No sentinel can survive that arithmetic — see
 * {@link #customNpcsNormalized(int)}. The XenoPixels role is only reachable because the mixin
 * intercepts {@code setRole} at {@code HEAD}, matches the raw argument, and returns before the
 * native chain (and its normalization) runs. MyNPCs has no normalization and a flat {@code 0..11}
 * chain, but uses the same HEAD interception so both trees stay identical.
 */
public final class DmzSkillMaster {
    /**
     * Role id written to NBT under the {@code "Role"} key for a DMZ skill-master NPC.
     *
     * <p>Above every native raw id in both mods (CustomNPCs uses {@code 0..9}, MyNPCs
     * {@code 0..11}) and above {@code RoleType.MAXSIZE}, so it can never be confused with a native
     * role on load. An NPC carrying it while XenoPixels is absent degrades to
     * {@code RoleInterface.NONE} rather than corrupting — see {@link #resolveUnknownId(int)}.
     */
    public static final int SENTINEL_ROLE_ID = 9001;

    /** Highest raw role id CustomNPCs' {@code setRole} chain can distinguish before normalizing. */
    public static final int CUSTOMNPCS_MAX_NATIVE_RAW = 9;
    /** Highest role id MyNPCs' {@code setRole} chain distinguishes. */
    public static final int MYNPCS_MAX_NATIVE_RAW = 11;

    /** {@code RoleInterface.NONE}'s type in both mods; the safe fallback for an unknown id. */
    public static final int ROLE_NONE = 0;

    /** Native role ids present in both mods, as {@code RoleType} constant names. */
    public static final List<String> NATIVE_ROLE_NAMES = List.of(
            "NONE", "TRADER", "FOLLOWER", "BANK", "TRANSPORTER",
            "MAILMAN", "COMPANION", "DIALOG");

    /** Translation key for the selector entry, resolved from the mod's own lang files. */
    public static final String SELECTOR_LABEL_KEY = "role.dragonminez.skill_master";

    private DmzSkillMaster() {
    }

    /** True when {@code roleId} is the XenoPixels skill-master sentinel. */
    public static boolean isSentinel(int roleId) {
        return roleId == SENTINEL_ROLE_ID;
    }

    /**
     * The selector position the XenoPixels entry occupies in a picker with {@code nativeLength}
     * stock entries.
     *
     * <p>Both mods build that picker as a fixed {@code GuiButtonBiDirectional}. The extra entry is
     * appended, so its position is one past the last native one. This is a <em>widget position</em>,
     * not a role id: the button carries the position while the value handed to
     * {@code DataAdvanced.setRole} is {@link #SENTINEL_ROLE_ID}.
     *
     * <p>The length is a parameter rather than a constant because the two mods ship different
     * stock lists — CustomNPCs has eight entries, MyNPCs eleven. Reading it from the widget the
     * mod itself built keeps this rule correct if either mod adds a role.
     */
    public static int appendedSelectorIndex(int nativeLength) {
        return nativeLength;
    }

    /** True when {@code selectorIndex} addresses the appended XenoPixels entry. */
    public static boolean isSelectorIndex(int selectorIndex, int nativeLength) {
        return nativeLength > 0 && selectorIndex == appendedSelectorIndex(nativeLength);
    }

    /**
     * Maps a selector position back to the role id written to NBT.
     *
     * <p>Only the appended position is translated; native positions pass through unchanged so the
     * picker keeps behaving exactly like the stock one. The sentinel that comes out of here is what
     * {@code DataAdvancedRoleMixin} matches at {@code HEAD} of {@code setRole}, before CustomNPCs'
     * {@code if (id >= 8) id -= 2; id %= 8;} would collapse it onto a native role — see
     * {@link #customNpcsNormalized(int)}.
     */
    public static int roleIdForSelector(int selectorIndex, int nativeLength) {
        return isSelectorIndex(selectorIndex, nativeLength) ? SENTINEL_ROLE_ID : selectorIndex;
    }

    /**
     * The selector position that should be displayed for a stored role id.
     *
     * <p>A skill master stores {@link #SENTINEL_ROLE_ID}, which is far outside the native range, so
     * the appended position is shown instead. Every other id keeps the position {@code nativeValue}
     * that the mod itself computed, and a value outside the array degrades to {@link #ROLE_NONE}
     * rather than leaving the widget on an entry it cannot render.
     */
    public static int selectorIndex(int roleId, int nativeValue, int nativeLength) {
        if (isSentinel(roleId)) return appendedSelectorIndex(nativeLength);
        if (nativeValue < 0 || nativeValue >= nativeLength) return ROLE_NONE;
        return nativeValue;
    }

    /**
     * Picker position to show for a live NPC, given whatever the host constructor was about to
     * pass as {@code value}.
     *
     * <p>CustomNPCs passes {@code role.getType()} (the stored id). MyNPCs looks that id up in
     * stock {@code ROLE_TYPE_IDS} first and passes the resulting <em>index</em>, which is {@code 0}
     * when the sentinel is absent from that table. Using the constructor value as a role id on
     * MyNPCs therefore leaves a skill master showing as None. The stored type is the only value
     * both mods agree on, so it wins when it is the sentinel; otherwise the constructor value is
     * kept so native roles still render as the host computed them.
     */
    public static int shownSelectorIndex(int storedRoleId, int constructorValue, int nativeLength) {
        if (isSentinel(storedRoleId)) return appendedSelectorIndex(nativeLength);
        if (constructorValue >= 0 && constructorValue < nativeLength) return constructorValue;
        return selectorIndex(storedRoleId, constructorValue, nativeLength);
    }

    /**
     * Mirrors CustomNPCs' own normalization so a test can prove the sentinel would alias a native
     * role if it ever reached the native chain. Result is always {@code 0..7}.
     */
    public static int customNpcsNormalized(int roleId) {
        int id = roleId;
        if (id >= 8) id -= 2;
        return id % 8;
    }

    /**
     * Resolves a raw NBT role id to a native role id, degrading anything XenoPixels owns or does
     * not recognize to {@link #ROLE_NONE}.
     *
     * <p>Used when the mod that saved an NPC is absent or the id is out of range: a saved
     * skill-master must load as "no role", never as an arbitrary native role.
     */
    public static int resolveUnknownId(int roleId) {
        if (isSentinel(roleId)) return ROLE_NONE;
        if (roleId < ROLE_NONE || roleId > MYNPCS_MAX_NATIVE_RAW) return ROLE_NONE;
        return roleId;
    }

    /**
     * The "skill-giving module is on" rule.
     *
     * <p>An NPC is only a functioning skill master when the group it is listed in actually offers
     * something: master learning enabled, a named form type, and at least one skill cost level.
     * A designation with none of those is inert by design rather than an error.
     */
    public static boolean moduleEnabled(DmzFormMetadata metadata) {
        if (metadata == null) return false;
        if (!metadata.masterLearningEnabled) return false;
        if (metadata.formType == null || metadata.formType.isBlank()) return false;
        return true;
    }

    /** True when {@code trainerId} is designated a skill master in {@code metadata}. */
    public static boolean designated(DmzFormMetadata metadata, UUID trainerId) {
        return trainerRef(metadata, trainerId) != null;
    }

    /**
     * The full predicate shared by the interaction path and the editor: the NPC is designated a
     * skill master <em>and</em> the group's skill-giving module is on.
     */
    public static boolean isSkillMaster(DmzFormMetadata metadata, UUID trainerId) {
        DmzFormMetadata.TrainerRef ref = trainerRef(metadata, trainerId);
        if (ref == null) return false;
        return ref.skillMaster || moduleEnabled(metadata);
    }

    /** True when any loaded group makes {@code trainerId} a functioning skill master. */
    public static boolean isSkillMasterAnywhere(List<DmzFormMetadata> metadata, UUID trainerId) {
        if (metadata == null || trainerId == null) return false;
        for (DmzFormMetadata entry : metadata) {
            if (isSkillMaster(entry, trainerId)) return true;
        }
        return false;
    }

    /**
     * Menu title for one trainer, falling back to the group's own name and finally to
     * {@code fallback}.
     *
     * <p>Resolution order is the caller's locale, then {@code en_us}, then the group name, then the
     * supplied fallback — the same chain used for form labels.
     */
    public static String menuTitle(DmzFormMetadata metadata, UUID trainerId, String locale,
                                   String fallback) {
        DmzFormMetadata.TrainerRef ref = trainerRef(metadata, trainerId);
        if (ref != null && ref.menuTitle != null && !ref.menuTitle.isBlank()) {
            return ref.menuTitle;
        }
        if (metadata != null && metadata.groupNames != null) {
            String localized = localized(metadata.groupNames, locale);
            if (localized != null) return localized;
        }
        return fallback == null ? "" : fallback;
    }

    /** Locale-resolved body text for one trainer, or an empty string when none is configured. */
    public static String menuBody(DmzFormMetadata metadata, UUID trainerId, String locale) {
        DmzFormMetadata.TrainerRef ref = trainerRef(metadata, trainerId);
        if (ref == null || ref.menuBody == null || ref.menuBody.isEmpty()) return "";
        String localized = localized(ref.menuBody, locale);
        return localized == null ? "" : localized;
    }

    /**
     * The form ids this trainer explicitly offers, or every form in the group when the trainer
     * lists none. An empty result means the group has no forms at all.
     */
    public static List<String> offeredForms(DmzFormMetadata metadata, UUID trainerId) {
        if (metadata == null || metadata.forms == null) return List.of();
        DmzFormMetadata.TrainerRef ref = trainerRef(metadata, trainerId);
        if (ref == null || ref.offeredForms == null || ref.offeredForms.isEmpty()) {
            return List.copyOf(metadata.forms.keySet());
        }
        return List.copyOf(ref.offeredForms);
    }

    /**
     * Builds the menu payload for one trainer from every group that lists it.
     *
     * <p>Pure rules only: this decides which title, body and offered forms the interaction will
     * show, but it does not resolve a locale. The client owns that, so the raw maps travel intact
     * and the same payload renders correctly for two players reading different languages.
     *
     * <p>Title, group names and body come from the first offering that supplies them, so a trainer
     * listed in several groups still produces one coherent menu. Entries are de-duplicated on the
     * group/form pair, because two groups may offer the same form id.
     */
    public static DmzTrainerMenu menuFor(UUID trainerId,
                                         List<DmzFormMetadataRegistry.TrainerOffering> offerings) {
        if (trainerId == null || offerings == null || offerings.isEmpty()) {
            return DmzTrainerMenu.EMPTY;
        }
        String title = "";
        Map<String, String> groupNames = Map.of();
        Map<String, String> body = Map.of();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.ArrayList<DmzTrainerMenu.Entry> entries = new java.util.ArrayList<>();
        for (DmzFormMetadataRegistry.TrainerOffering offering : offerings) {
            if (offering == null || offering.metadata() == null) continue;
            DmzFormMetadata metadata = offering.metadata();
            DmzFormMetadata.TrainerRef ref = trainerRef(metadata, trainerId);
            if (title.isEmpty() && ref != null && ref.menuTitle != null && !ref.menuTitle.isBlank()) {
                title = ref.menuTitle;
            }
            if (groupNames.isEmpty() && metadata.groupNames != null && !metadata.groupNames.isEmpty()) {
                groupNames = metadata.groupNames;
            }
            if (body.isEmpty() && ref != null && ref.menuBody != null && !ref.menuBody.isEmpty()) {
                body = ref.menuBody;
            }
            for (String form : offeredForms(metadata, trainerId)) {
                if (form == null || form.isBlank()) continue;
                if (!seen.add(offering.kind() + ":" + metadata.group + ":" + form)) continue;
                entries.add(new DmzTrainerMenu.Entry(offering.kind().name(), metadata.race,
                        metadata.group, metadata.formType, form,
                        DmzFormMetadataRegistry.displayName(offering.kind(), metadata.race,
                                metadata.group, form, "en_us")));
                if (entries.size() >= DmzTrainerMenu.MAX_ENTRIES) break;
            }
            if (entries.size() >= DmzTrainerMenu.MAX_ENTRIES) break;
        }
        return new DmzTrainerMenu(title, groupNames, body, entries);
    }

    /** Looks up one trainer record, tolerating a null id and null entries. */
    public static DmzFormMetadata.TrainerRef trainerRef(DmzFormMetadata metadata, UUID trainerId) {
        if (metadata == null || trainerId == null || metadata.customNpcTrainers == null) return null;
        String expected = trainerId.toString();
        for (DmzFormMetadata.TrainerRef trainer : metadata.customNpcTrainers) {
            if (trainer != null && expected.equalsIgnoreCase(trainer.uuid)) return trainer;
        }
        return null;
    }

    /** Bounds and normalizes one menu-body map to the locales and lengths the editor accepts. */
    public static Map<String, String> sanitizeBody(Map<String, String> body) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        if (body == null) return result;
        int kept = 0;
        for (Map.Entry<String, String> entry : body.entrySet()) {
            if (kept >= MAX_MENU_LOCALES) break;
            String locale = normalizeLocale(entry.getKey());
            String value = entry.getValue();
            if (locale.isEmpty() || value == null || value.isBlank()) continue;
            result.put(locale, clamp(value, MAX_MENU_BODY_LENGTH));
            kept++;
        }
        return result;
    }

    /** Locale entries one menu body may carry. */
    public static final int MAX_MENU_LOCALES = 16;
    /** Characters one menu body entry may carry. */
    public static final int MAX_MENU_BODY_LENGTH = 512;
    /** Characters a menu title may carry. */
    public static final int MAX_MENU_TITLE_LENGTH = 64;

    private static String localized(Map<String, String> names, String locale) {
        if (names == null || names.isEmpty()) return null;
        String value = names.get(normalizeLocale(locale));
        if (value == null || value.isBlank()) value = names.get("en_us");
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizeLocale(String locale) {
        return locale == null ? "" : locale.toLowerCase(Locale.ROOT).replace('-', '_').trim();
    }

    private static String clamp(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}