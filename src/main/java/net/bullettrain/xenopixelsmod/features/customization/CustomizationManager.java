package net.bullettrain.xenopixelsmod.features.customization;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 2: Character Customization System
 *
 * Inspired by Xenoverse's character creation system, this provides:
 * - Face customization for NPCs in the DMZ world (keyed by character/player id)
 * - Genetic inheritance system (features pass to children/pets)
 * - Unique facial tattoos/markings as unlockable cosmetics
 * - Headgear/accessory slots with compatibility checking
 *
 * DMZ-compatible: pure data layer keyed by string character ids (player UUID or
 * NPC id). No fabricated third-party cosmetic APIs; integrates by id only.
 */
public final class CustomizationManager {

    private CustomizationManager() {
    }

    /** Character customization data storage (characterId -> data). */
    public static final Map<String, CharacterData> CUSTOMIZATION_DATA = new ConcurrentHashMap<>();

    /** All available cosmetic items. */
    private static final List<CosmeticItem> AVAILABLE_COSMETICS = Lists.newArrayList();

    /**
     * Initialize the customization system with all available cosmetics.
     */
    public static void init() {
        AVAILABLE_COSMETICS.clear();
        registerCosmetics();
        XenoPixelsMod.LOGGER.info(
                ">>> Feature 2 [Character Customization] initialized: {} cosmetic items",
                AVAILABLE_COSMETICS.size());
    }

    /**
     * Register all available cosmetic items.
     */
    private static void registerCosmetics() {
        for (int i = 1; i <= 20; i++) {
            AVAILABLE_COSMETICS.add(new CosmeticItem(
                    "face_tattoo_" + i,
                    "Face Tattoo " + i,
                    "Unique face tattoo marking",
                    Category.FACE_TATTOO));
        }

        for (int i = 1; i <= 15; i++) {
            AVAILABLE_COSMETICS.add(new CosmeticItem(
                    "face_marking_" + i,
                    "Facial Marking " + i,
                    "Distinctive facial marking",
                    Category.FACE_MARKING));
        }

        for (int i = 1; i <= 30; i++) {
            AVAILABLE_COSMETICS.add(new CosmeticItem(
                    "headgear_" + i,
                    "Headgear " + i,
                    "Protective headgear piece",
                    Category.HEADGEAR));
        }

        for (int i = 1; i <= 25; i++) {
            AVAILABLE_COSMETICS.add(new CosmeticItem(
                    "accessory_" + i,
                    "Accessory " + i,
                    "Style accessory piece",
                    Category.ACCESSORY));
        }

        for (int i = 1; i <= 50; i++) {
            AVAILABLE_COSMETICS.add(new CosmeticItem(
                    "hair_style_" + i,
                    "Hair Style " + i,
                    "Distinctive hairstyle",
                    Category.HAIR));
        }

        for (int i = 1; i <= 10; i++) {
            AVAILABLE_COSMETICS.add(new CosmeticItem(
                    "eye_color_" + i,
                    "Eye Color Variant " + i,
                    "Alternative eye appearance",
                    Category.EYE_COLOR));
        }
    }

    public static List<CosmeticItem> getAvailableCosmetics() {
        return new ArrayList<>(AVAILABLE_COSMETICS);
    }

    public static Optional<CosmeticItem> getCosmetic(String cosmeticId) {
        if (cosmeticId == null) {
            return Optional.empty();
        }
        return AVAILABLE_COSMETICS.stream()
                .filter(c -> c.getId().equals(cosmeticId))
                .findFirst();
    }

    public static List<CosmeticItem> getCosmeticsByCategory(Category category) {
        return AVAILABLE_COSMETICS.stream()
                .filter(c -> c.getCategory() == category)
                .toList();
    }

    /**
     * Get all cosmetics whose id matches a regex pattern (e.g. {@code face_tattoo_.*}).
     */
    public static List<CosmeticItem> getCosmeticsByPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return List.of();
        }
        return AVAILABLE_COSMETICS.stream()
                .filter(c -> c.getId().matches(pattern))
                .toList();
    }

    public static CharacterData createCharacterData(String characterId, String name) {
        CharacterData data = new CharacterData(characterId, name);
        CUSTOMIZATION_DATA.put(characterId, data);
        XenoPixelsMod.LOGGER.info("Customization: Created data for character '{}'", name);
        return data;
    }

    public static CharacterData getOrCreateCharacterData(String characterId, String name) {
        return CUSTOMIZATION_DATA.computeIfAbsent(characterId, k -> new CharacterData(k, name));
    }

    public static CharacterData getCharacterData(String characterId) {
        return CUSTOMIZATION_DATA.get(characterId);
    }

    /**
     * Add a cosmetic to a character's inventory (unlock count).
     */
    public static boolean addCosmetic(String characterId, String cosmeticId, int count) {
        if (count <= 0) {
            return false;
        }
        CharacterData data = getCharacterData(characterId);
        if (data == null) {
            return false;
        }

        CosmeticItem item = getCosmetic(cosmeticId).orElse(null);
        if (item == null) {
            return false;
        }

        data.addToInventory(item.getId(), count);
        XenoPixelsMod.LOGGER.info(
                "Customization: Added {} x {} to character '{}'",
                count, cosmeticId, characterId);
        return true;
    }

    /**
     * Snapshot of owned cosmetic id -> count for a character.
     */
    public static Map<String, Integer> getInventory(String characterId) {
        CharacterData data = getCharacterData(characterId);
        return data != null ? new HashMap<>(data.getInventory()) : new HashMap<>();
    }

    /**
     * Remove {@code count} of a cosmetic from inventory. Unequips if count hits zero.
     */
    public static boolean removeCosmetic(String characterId, String cosmeticId, int count) {
        if (count <= 0) {
            return false;
        }
        CharacterData data = getCharacterData(characterId);
        if (data == null) {
            return false;
        }

        CosmeticItem item = getCosmetic(cosmeticId).orElse(null);
        if (item == null) {
            return false;
        }

        boolean removed = data.removeFromInventory(item.getId(), count);
        if (removed) {
            XenoPixelsMod.LOGGER.info(
                    "Customization: Removed {} x {} from character '{}'",
                    count, cosmeticId, characterId);
        }
        return removed;
    }

    // -------------------------------------------------------------------------
    // Equip APIs
    // -------------------------------------------------------------------------

    /**
     * Whether {@code cosmetic} may be equipped in {@code slot}.
     * One cosmetic category maps 1:1 to one equip slot.
     */
    public static boolean isCompatible(EquipSlot slot, CosmeticItem cosmetic) {
        if (slot == null || cosmetic == null) {
            return false;
        }
        return slot.getCategory() == cosmetic.getCategory();
    }

    /**
     * Equip a cosmetic the character owns. Replaces any item already in that slot.
     *
     * @return true if equipped successfully
     */
    public static boolean equipCosmetic(String characterId, String cosmeticId) {
        CharacterData data = getCharacterData(characterId);
        if (data == null) {
            return false;
        }

        CosmeticItem item = getCosmetic(cosmeticId).orElse(null);
        if (item == null) {
            return false;
        }

        if (data.getOwnedCount(cosmeticId) <= 0) {
            XenoPixelsMod.LOGGER.debug(
                    "Customization: Cannot equip '{}' — not in inventory for '{}'",
                    cosmeticId, characterId);
            return false;
        }

        EquipSlot slot = EquipSlot.fromCategory(item.getCategory());
        if (!isCompatible(slot, item)) {
            return false;
        }

        data.setEquipped(slot, cosmeticId);
        XenoPixelsMod.LOGGER.info(
                "Customization: Equipped '{}' in slot {} for '{}'",
                cosmeticId, slot, characterId);
        return true;
    }

    /**
     * Unequip whatever is in {@code slot} for the character.
     *
     * @return true if something was unequipped
     */
    public static boolean unequipCosmetic(String characterId, EquipSlot slot) {
        CharacterData data = getCharacterData(characterId);
        if (data == null || slot == null) {
            return false;
        }

        String previous = data.clearEquipped(slot);
        if (previous != null) {
            XenoPixelsMod.LOGGER.info(
                    "Customization: Unequipped '{}' from slot {} for '{}'",
                    previous, slot, characterId);
            return true;
        }
        return false;
    }

    /**
     * Get currently equipped cosmetic id for a slot, or empty.
     */
    public static Optional<String> getEquipped(String characterId, EquipSlot slot) {
        CharacterData data = getCharacterData(characterId);
        if (data == null || slot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(data.getEquipped(slot));
    }

    /**
     * Snapshot of all equipped slots for a character.
     */
    public static Map<EquipSlot, String> getAllEquipped(String characterId) {
        CharacterData data = getCharacterData(characterId);
        if (data == null) {
            return Map.of();
        }
        return data.getEquippedSnapshot();
    }

    // -------------------------------------------------------------------------
    // Genetic APIs
    // -------------------------------------------------------------------------

    /**
     * Set or replace a genetic trait on a character.
     */
    public static boolean setGeneticTrait(
            String characterId,
            String traitName,
            String value,
            GeneticTraitModifiers modifiers) {
        CharacterData data = getCharacterData(characterId);
        if (data == null || traitName == null || traitName.isEmpty()) {
            return false;
        }

        GeneticTraitModifiers mods = modifiers != null ? modifiers : GeneticTraitModifiers.NONE;
        data.getGeneticTraits().put(traitName, new GeneticTraitInfo(value, mods));
        XenoPixelsMod.LOGGER.info(
                "Customization: Set genetic trait '{}'={} on '{}'",
                traitName, value, characterId);
        return true;
    }

    /**
     * Remove a genetic trait from a character.
     */
    public static boolean removeGeneticTrait(String characterId, String traitName) {
        CharacterData data = getCharacterData(characterId);
        if (data == null || traitName == null) {
            return false;
        }
        return data.getGeneticTraits().remove(traitName);
    }

    public static GeneticTraits getGeneticTraits(String characterId) {
        CharacterData data = getCharacterData(characterId);
        return data != null ? data.getGeneticTraits() : new GeneticTraits();
    }

    /**
     * Inherit genetic traits from parent into child (copy missing traits; keep child's existing).
     * Suitable for NPC offspring / pet breeding hooks keyed by DMZ entity ids.
     *
     * @return number of traits newly applied to the child
     */
    public static int inheritGenetics(String parentId, String childId) {
        CharacterData parent = getCharacterData(parentId);
        CharacterData child = getCharacterData(childId);
        if (parent == null || child == null) {
            return 0;
        }

        int applied = 0;
        GeneticTraits parentTraits = parent.getGeneticTraits();
        GeneticTraits childTraits = child.getGeneticTraits();

        for (String traitName : parentTraits.getTraits()) {
            if (childTraits.hasTrait(traitName)) {
                continue;
            }
            GeneticTraitInfo info = parentTraits.getInfo(traitName);
            if (info != null) {
                childTraits.put(traitName, info.copy());
                applied++;
            }
        }

        if (applied > 0) {
            XenoPixelsMod.LOGGER.info(
                    "Customization: Inherited {} genetic trait(s) from '{}' to '{}'",
                    applied, parentId, childId);
        }
        return applied;
    }

    /**
     * Copy face cosmetics (equipped slots that are genetic-facing) from parent to child inventory + equip.
     * Hair / eye / face marking / face tattoo are treated as inheritable appearance.
     */
    public static int inheritAppearance(String parentId, String childId) {
        CharacterData parent = getCharacterData(parentId);
        CharacterData child = getCharacterData(childId);
        if (parent == null || child == null) {
            return 0;
        }

        int applied = 0;
        for (EquipSlot slot : EquipSlot.INHERITABLE) {
            String cosmeticId = parent.getEquipped(slot);
            if (cosmeticId == null) {
                continue;
            }
            child.addToInventory(cosmeticId, 1);
            child.setEquipped(slot, cosmeticId);
            applied++;
        }

        if (applied > 0) {
            XenoPixelsMod.LOGGER.info(
                    "Customization: Inherited {} appearance slot(s) from '{}' to '{}'",
                    applied, parentId, childId);
        }
        return applied;
    }

    public static void clearAllData() {
        CUSTOMIZATION_DATA.clear();
        XenoPixelsMod.LOGGER.info("Customization: All character data cleared");
    }

    /**
     * Export customization data for debugging/testing.
     */
    public static JsonObject exportData() {
        JsonObject root = new JsonObject();
        JsonArray characters = new JsonArray();

        CUSTOMIZATION_DATA.forEach((id, data) -> {
            JsonObject charObj = new JsonObject();
            charObj.addProperty("id", id);
            charObj.addProperty("name", data.getName());
            charObj.add("inventory", exportInventory(data.getInventory()));
            charObj.add("equipped", exportEquipped(data.getEquippedSnapshot()));

            if (data.hasGeneticTraits()) {
                charObj.add("geneticTraits", exportGeneticTraits(data.getGeneticTraits()));
            }

            characters.add(charObj);
        });

        root.add("characters", characters);
        root.addProperty("totalCharacters", CUSTOMIZATION_DATA.size());
        return root;
    }

    private static JsonArray exportInventory(Map<String, Integer> inventory) {
        JsonArray array = new JsonArray();

        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.getKey());
            item.addProperty("count", entry.getValue());

            CosmeticItem cosmetic = getCosmetic(entry.getKey()).orElse(null);
            if (cosmetic != null) {
                item.addProperty("name", cosmetic.getName());
                item.add("category", cosmetic.getCategory().toJson());
            }

            array.add(item);
        }

        return array;
    }

    private static JsonObject exportEquipped(Map<EquipSlot, String> equipped) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<EquipSlot, String> entry : equipped.entrySet()) {
            obj.addProperty(entry.getKey().name(), entry.getValue());
        }
        return obj;
    }

    private static JsonArray exportGeneticTraits(GeneticTraits traits) {
        JsonArray array = new JsonArray();

        for (String traitName : traits.getTraits()) {
            JsonObject trait = new JsonObject();
            trait.addProperty("name", traitName);

            GeneticTraitInfo info = traits.getInfo(traitName);
            if (info != null) {
                trait.addProperty("value", info.getValue());
                trait.add("modifiers", exportModifiers(info.getModifiers()));
            }

            array.add(trait);
        }

        return array;
    }

    private static JsonArray exportModifiers(GeneticTraitModifiers modifiers) {
        JsonArray array = new JsonArray();
        if (modifiers == null) {
            return array;
        }

        if (modifiers.getPowerLevel() != 0) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "powerLevel");
            obj.addProperty("value", modifiers.getPowerLevel());
            array.add(obj);
        }

        if (modifiers.getKiControl() != 0) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "kiControl");
            obj.addProperty("value", modifiers.getKiControl());
            array.add(obj);
        }

        if (modifiers.getDamageReduction() != 0) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "damageReduction");
            obj.addProperty("value", modifiers.getDamageReduction());
            array.add(obj);
        }

        return array;
    }

    // =========================================================================
    // Nested types
    // =========================================================================

    /**
     * Cosmetic category (also defines which equip slot a cosmetic belongs to).
     */
    public enum Category {
        FACE_TATTOO,
        FACE_MARKING,
        HEADGEAR,
        ACCESSORY,
        HAIR,
        EYE_COLOR;

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", name());
            json.addProperty("ordinal", ordinal());
            return json;
        }
    }

    /**
     * Equipment slots for cosmetics. Each maps to exactly one {@link Category}.
     */
    public enum EquipSlot {
        FACE_TATTOO(Category.FACE_TATTOO),
        FACE_MARKING(Category.FACE_MARKING),
        HEADGEAR(Category.HEADGEAR),
        ACCESSORY(Category.ACCESSORY),
        HAIR(Category.HAIR),
        EYE_COLOR(Category.EYE_COLOR);

        /** Slots that can pass from parent to child via {@link #inheritAppearance}. */
        public static final List<EquipSlot> INHERITABLE = List.of(
                FACE_TATTOO, FACE_MARKING, HAIR, EYE_COLOR);

        private final Category category;

        EquipSlot(Category category) {
            this.category = category;
        }

        public Category getCategory() {
            return category;
        }

        public static EquipSlot fromCategory(Category category) {
            for (EquipSlot slot : values()) {
                if (slot.category == category) {
                    return slot;
                }
            }
            throw new IllegalArgumentException("No equip slot for category: " + category);
        }
    }

    /**
     * Per-character customization state (inventory, equipped loadout, genetics).
     */
    public static final class CharacterData {
        private final String characterId;
        private final String name;
        private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
        private final Map<EquipSlot, String> equipped = new ConcurrentHashMap<>();
        private final GeneticTraits geneticTraits = new GeneticTraits();

        public CharacterData(String characterId, String name) {
            this.characterId = Objects.requireNonNull(characterId, "characterId");
            this.name = name != null ? name : characterId;
        }

        public String getCharacterId() {
            return characterId;
        }

        public String getName() {
            return name;
        }

        /**
         * Live inventory map (cosmeticId -> owned count). Prefer manager APIs for mutations.
         */
        public Map<String, Integer> getInventory() {
            return inventory;
        }

        public int getOwnedCount(String cosmeticId) {
            return inventory.getOrDefault(cosmeticId, 0);
        }

        public void addToInventory(String cosmeticId, int count) {
            if (cosmeticId == null || count <= 0) {
                return;
            }
            inventory.merge(cosmeticId, count, Integer::sum);
        }

        /**
         * @return true if at least one unit was removed
         */
        public boolean removeFromInventory(String cosmeticId, int count) {
            if (cosmeticId == null || count <= 0) {
                return false;
            }
            Integer current = inventory.get(cosmeticId);
            if (current == null || current <= 0) {
                return false;
            }

            if (current <= count) {
                inventory.remove(cosmeticId);
                // Unequip if this cosmetic was worn
                equipped.entrySet().removeIf(e -> cosmeticId.equals(e.getValue()));
            } else {
                inventory.put(cosmeticId, current - count);
            }
            return true;
        }

        public String getEquipped(EquipSlot slot) {
            return equipped.get(slot);
        }

        public void setEquipped(EquipSlot slot, String cosmeticId) {
            if (slot == null) {
                return;
            }
            if (cosmeticId == null) {
                equipped.remove(slot);
            } else {
                equipped.put(slot, cosmeticId);
            }
        }

        public String clearEquipped(EquipSlot slot) {
            return equipped.remove(slot);
        }

        public Map<EquipSlot, String> getEquippedSnapshot() {
            Map<EquipSlot, String> copy = new EnumMap<>(EquipSlot.class);
            copy.putAll(equipped);
            return Collections.unmodifiableMap(copy);
        }

        public GeneticTraits getGeneticTraits() {
            return geneticTraits;
        }

        public boolean hasGeneticTraits() {
            return !geneticTraits.isEmpty();
        }
    }

    /**
     * Collection of named genetic traits for a character.
     */
    public static final class GeneticTraits {
        private final Map<String, GeneticTraitInfo> traits = new ConcurrentHashMap<>();

        public Set<String> getTraits() {
            return Collections.unmodifiableSet(traits.keySet());
        }

        public GeneticTraitInfo getInfo(String traitName) {
            return traits.get(traitName);
        }

        public boolean hasTrait(String traitName) {
            return traits.containsKey(traitName);
        }

        public void put(String traitName, GeneticTraitInfo info) {
            if (traitName == null || info == null) {
                return;
            }
            traits.put(traitName, info);
        }

        public boolean remove(String traitName) {
            return traits.remove(traitName) != null;
        }

        public boolean isEmpty() {
            return traits.isEmpty();
        }

        public int size() {
            return traits.size();
        }
    }

    /**
     * Single genetic trait payload (string value + combat-ish modifiers).
     */
    public static final class GeneticTraitInfo {
        private final String value;
        private final GeneticTraitModifiers modifiers;

        public GeneticTraitInfo(String value, GeneticTraitModifiers modifiers) {
            this.value = value != null ? value : "";
            this.modifiers = modifiers != null ? modifiers : GeneticTraitModifiers.NONE;
        }

        public String getValue() {
            return value;
        }

        public GeneticTraitModifiers getModifiers() {
            return modifiers;
        }

        public GeneticTraitInfo copy() {
            return new GeneticTraitInfo(value, modifiers);
        }
    }

    /**
     * Flat numeric modifiers attached to a genetic trait.
     * Values are additive offsets for systems that choose to read them (e.g. skill/power hooks).
     */
    public static final class GeneticTraitModifiers {
        public static final GeneticTraitModifiers NONE = new GeneticTraitModifiers(0, 0, 0);

        private final double powerLevel;
        private final double kiControl;
        private final double damageReduction;

        public GeneticTraitModifiers(double powerLevel, double kiControl, double damageReduction) {
            this.powerLevel = powerLevel;
            this.kiControl = kiControl;
            this.damageReduction = damageReduction;
        }

        public double getPowerLevel() {
            return powerLevel;
        }

        public double getKiControl() {
            return kiControl;
        }

        public double getDamageReduction() {
            return damageReduction;
        }
    }
}
