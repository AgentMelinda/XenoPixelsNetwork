package net.bullettrain.xenopixelsmod.features.playerrole;

import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadata;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Constrains angel players to teach only the gods form group (D0-c).
 *
 * <p>Offerings keying reuses {@link DmzFormMetadataRegistry} UUID matching; purchase apply must
 * still go through {@code FormEditorNetwork.TrainerPurchasePacket.purchase} (never
 * {@code CombatSkills.grant} for forms).
 */
public final class AngelTrainerGate {
    /** DMZ form group id angels may offer (D0-c). */
    public static final String GODS_GROUP = "xenopixels_gods_forms";
    /**
     * Skill / {@link DmzFormMetadata#formType} for that group. Must not contain {@code god}
     * (DMZ remaps); verified in {@code xenopixels_gods_forms.json}.
     */
    public static final String GODS_FORM_TYPE = "xenopixels_divinity";

    private AngelTrainerGate() {
    }

    /** True when {@code player} may act as a living gods trainer. */
    public static boolean isAngelTrainer(ServerPlayer player) {
        return player != null && PlayerRoleService.get(player) == PlayerRoleId.ANGEL;
    }

    /**
     * Skill formTypes an angel may offer. Empty for non-angels.
     *
     * <p>Prefers live form-editor metadata whose {@code group} is {@link #GODS_GROUP}; if none is
     * loaded, falls back to the bundled skill id {@link #GODS_FORM_TYPE}.
     */
    public static List<String> offeringsFor(PlayerRoleId role) {
        if (role != PlayerRoleId.ANGEL) return List.of();
        List<String> result = new ArrayList<>();
        for (DmzFormMetadataRegistry.TrainerOffering offering : godsOfferingsFromRegistry()) {
            String formType = offering.metadata().formType;
            if (formType == null || formType.isBlank()) continue;
            if (!containsIgnoreCase(result, formType)) result.add(formType);
        }
        if (result.isEmpty()) result.add(GODS_FORM_TYPE);
        return List.copyOf(result);
    }

    /** Registry (or builtin) offerings an angel may teach. Empty for non-angels. */
    public static List<DmzFormMetadataRegistry.TrainerOffering> offeringEntriesFor(PlayerRoleId role) {
        if (role != PlayerRoleId.ANGEL) return List.of();
        List<DmzFormMetadataRegistry.TrainerOffering> fromRegistry = godsOfferingsFromRegistry();
        if (!fromRegistry.isEmpty()) return List.copyOf(fromRegistry);
        return List.of(new DmzFormMetadataRegistry.TrainerOffering(DmzFormKind.NORMAL, builtinGodsMetadata()));
    }

    /** True when {@code formType} is among the angel gods offerings. */
    public static boolean allowsFormType(PlayerRoleId role, String formType) {
        if (role != PlayerRoleId.ANGEL || formType == null || formType.isBlank()) return false;
        for (String offered : offeringsFor(role)) {
            if (formType.equalsIgnoreCase(offered)) return true;
        }
        return false;
    }

    /**
     * Resolves teachable metadata for one purchase formType when the trainer is an angel.
     * Returns {@code null} when the type is outside the gods gate.
     */
    public static DmzFormMetadata resolveOffering(String formType) {
        if (formType == null || formType.isBlank()) return null;
        for (DmzFormMetadataRegistry.TrainerOffering offering : offeringEntriesFor(PlayerRoleId.ANGEL)) {
            DmzFormMetadata metadata = offering.metadata();
            if (metadata != null && formType.equalsIgnoreCase(metadata.formType)) {
                return metadata;
            }
        }
        return null;
    }

    /** Menu payload for interacting with an angel player trainer. */
    public static DmzTrainerMenu menuFor(UUID trainerId) {
        List<DmzFormMetadataRegistry.TrainerOffering> offerings = offeringEntriesFor(PlayerRoleId.ANGEL);
        if (offerings.isEmpty()) return DmzTrainerMenu.EMPTY;
        DmzTrainerMenu menu = DmzSkillMaster.menuFor(trainerId, offerings);
        if (menu.entries().isEmpty()) {
            // Builtin / metadata without per-form rows still exposes the skill formType once.
            DmzFormMetadata metadata = offerings.get(0).metadata();
            String label = metadata.groupNames != null
                    ? metadata.groupNames.getOrDefault("en_us", GODS_GROUP) : GODS_GROUP;
            return new DmzTrainerMenu(
                    "Gods Master",
                    metadata.groupNames == null ? Map.of("en_us", GODS_GROUP) : metadata.groupNames,
                    Map.of("en_us", "Learn the gods forms."),
                    List.of(new DmzTrainerMenu.Entry(
                            DmzFormKind.NORMAL.name(),
                            metadata.race == null ? "saiyan" : metadata.race,
                            GODS_GROUP,
                            metadata.formType == null ? GODS_FORM_TYPE : metadata.formType,
                            GODS_FORM_TYPE,
                            label)));
        }
        return menu;
    }

    private static List<DmzFormMetadataRegistry.TrainerOffering> godsOfferingsFromRegistry() {
        List<DmzFormMetadataRegistry.TrainerOffering> result = new ArrayList<>();
        for (DmzFormMetadataRegistry.TrainerOffering offering : DmzFormMetadataRegistry.all()) {
            if (offering == null || offering.metadata() == null) continue;
            if (!isGodsGroup(offering.metadata())) continue;
            result.add(offering);
        }
        return result;
    }

    static boolean isGodsGroup(DmzFormMetadata metadata) {
        return metadata != null && metadata.group != null
                && GODS_GROUP.equalsIgnoreCase(metadata.group.trim());
    }

    /**
     * In-memory gods metadata when form-editor disk has no entry yet. Prices match
     * {@code form_skill_prices.json} / {@code xenopixels_divinity}.
     */
    static DmzFormMetadata builtinGodsMetadata() {
        DmzFormMetadata metadata = new DmzFormMetadata();
        metadata.race = "saiyan";
        metadata.group = GODS_GROUP;
        metadata.formType = GODS_FORM_TYPE;
        metadata.masterLearningEnabled = true;
        metadata.buyFromMaster = true;
        metadata.skillCosts = new ArrayList<>(List.of(
                150000, 175000, 200000, 190000, 240000,
                260000, 320000, 300000, 340000, 340000));
        metadata.groupNames = new LinkedHashMap<>();
        metadata.groupNames.put("en_us", "XenoPixels Gods Forms");
        metadata.forms = new LinkedHashMap<>();
        for (String formId : List.of(
                "ssg", "ssb", "ssbe", "ssrose", "ssrose_evolution",
                "ui_sign", "ui", "ue", "ssb3", "ssrose3")) {
            DmzFormMetadata.FormEntry entry = new DmzFormMetadata.FormEntry();
            entry.names = new LinkedHashMap<>();
            entry.names.put("en_us", formId);
            metadata.forms.put(formId, entry);
        }
        return metadata;
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        String needle = candidate.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).equals(needle)) return true;
        }
        return false;
    }
}
