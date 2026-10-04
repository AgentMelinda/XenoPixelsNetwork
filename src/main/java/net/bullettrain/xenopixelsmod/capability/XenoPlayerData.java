package net.bullettrain.xenopixelsmod.capability;

import net.bullettrain.xenopixelsmod.features.progression.ActiveQuest;
import net.bullettrain.xenopixelsmod.features.progression.QuestBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class XenoPlayerData {
    private float ki = 100f;
    private float maxKi = 100f;
    private float stamina = 100f;
    private float maxStamina = 100f;

    // --- Phase 3 progression ---
    /** Equipped Super Soul id (empty = none). */
    private String superSoulId = "";
    /** Combat skill levels: skillId -> 0..3 */
    private final Map<String, Integer> skillLevels = new HashMap<>();
    /** Points spent on skills (earned via quests / dummy milestones). */
    private int skillPoints = 0;

    /**
     * How each faction feels about this player, by faction id.
     *
     * <p>Sparse on purpose: a faction the player has never dealt with is absent rather than stored
     * at zero, so a pack adding ten factions does not add ten entries to every save. The default
     * comes from the faction definition, which is where a pack decides whether its NPCs start
     * suspicious or welcoming.
     */
    private final java.util.Map<String, Integer> factionStanding = new java.util.LinkedHashMap<>();

    /**
     * Transport destinations this player has discovered.
     *
     * <p>Server state, never the client's. A client that could name its own unlocked destinations
     * would be a client that could teleport anywhere a transporter lists.
     */
    private final java.util.Set<String> unlockedTransports = new java.util.LinkedHashSet<>();
    private int multiFormMastery;
    private UUID mentorUuid;
    private String mentorName = "";
    /**
     * Every quest this player is on, and every one they have finished.
     *
     * <p>Replaces the single {@code questId}/{@code questProgress}/{@code questTarget} trio. A
     * player could hold exactly one quest, which stopped making sense once a datapack or the world
     * store could define any number of them.
     */
    private final QuestBook quests = new QuestBook();
    /** Bounded values written by native scripts, preserved through death and player saves. */
    private CompoundTag scriptData = new CompoundTag();
    public CompoundTag scriptData() { return scriptData; }
    /** Native player timers (XenoAPI IPlayer.getTimers), kept apart from stored script data. */
    private CompoundTag scriptTimers = new CompoundTag();
    public CompoundTag scriptTimers() { return scriptTimers; }
    private net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument taotto =
            net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument.blank();

    public net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument taotto() {
        return taotto;
    }

    public void taotto(net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument document) {
        this.taotto = document == null
                ? net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument.blank()
                : document;
    }
    /** Shared dialogue ids the player has opened, for availability before/after gates. */
    private final Set<String> viewedDialogues = new LinkedHashSet<>();
    private static final int MAX_VIEWED_DIALOGUES = 4096;

    public boolean hasViewedDialogue(String id) {
        return id != null && viewedDialogues.contains(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public void recordViewedDialogue(String id) {
        if (id == null) return;
        String key = id.trim().toLowerCase(java.util.Locale.ROOT);
        if (key.isEmpty() || key.length() > 128 || key.startsWith("<")) return;
        if (!viewedDialogues.contains(key) && viewedDialogues.size() >= MAX_VIEWED_DIALOGUES) {
            viewedDialogues.remove(viewedDialogues.iterator().next());
        }
        viewedDialogues.add(key);
    }
    /** Forgets that the player opened a dialogue, so before/after gates read it as unseen. */
    public void forgetViewedDialogue(String id) {
        if (id == null) return;
        viewedDialogues.remove(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Resets NPC progress: quests, viewed dialogues, faction standing, discovered transport and
     * item-giver history (XenoAPI {@code IPlayer.clearData}). Bank vaults are kept: they hold the
     * player's items, and nothing about a reset should destroy those.
     */
    public void clearNpcProgress() {
        quests.clear();
        viewedDialogues.clear();
        factionStanding.clear();
        unlockedTransports.clear();
        itemGiverUses.clear();
    }

    /** Per-NPC Item Giver use, persisted with the player and copied on death. */
    private final Map<UUID, ItemGiverUse> itemGiverUses = new java.util.LinkedHashMap<>();
    private static final int MAX_ITEM_GIVER_USES = 4096;

    public record ItemGiverUse(long lastMillis, long lastDay, int nextSlot) {}

    public ItemGiverUse itemGiverUse(UUID npc) {
        return itemGiverUses.get(npc);
    }

    public void recordItemGiverUse(UUID npc, long millis, long day, int nextSlot) {
        if (npc == null) return;
        if (!itemGiverUses.containsKey(npc) && itemGiverUses.size() >= MAX_ITEM_GIVER_USES) {
            UUID oldest = itemGiverUses.keySet().iterator().next();
            itemGiverUses.remove(oldest);
        }
        itemGiverUses.put(npc, new ItemGiverUse(Math.max(0L, millis), Math.max(0L, day),
                Math.max(0, Math.min(8, nextSlot))));
    }

    /**
     * This player's holdings at each bank they have used, keyed by bank id.
     *
     * <p>Ordered so the save file is stable between runs - the same reason the faction standings
     * above use a {@code LinkedHashMap} rather than a plain one.
     *
     * <p>Not in any sync packet. The vault is shown through a container menu, which syncs its own
     * slots; nothing about a bank needs to reach the client any other way.
     */
    private final java.util.Map<String, net.bullettrain.xenopixelsmod.npc.bank.BankAccount>
            bankAccounts = new java.util.LinkedHashMap<>();
    /**
     * Things the active quest has already counted, so they cannot be counted twice.
     *
     * <p>Exists for {@code TALK_TO_NPC}: "speak to three masters" has to mean three masters, and
     * without this it would mean clicking one master three times. An objective that reads as one
     * thing and can be finished by another is worse than not having it.
     *
     * <p>Cleared whenever a quest starts or ends, so it never outlives the quest it belongs to, and
     * bounded because it is grown by player action.
     */

    private long dummyTotalDamage;
    private long dummySessionDamage;
    private int dummyHits;

    /**
     * Last values pushed to this player's client, and when.
     *
     * <p>Deliberately <b>not</b> serialised and not copied on respawn: they describe what the
     * client currently believes, not player state. A fresh connection knows nothing, so starting
     * from {@code NaN} forces the first tick to send, which is exactly right.
     *
     * <p>These lived as {@code @Unique} fields on a {@code Player} Mixin, which put them on every
     * player instance in the game including client-side ones. They belong with the data they
     * describe.
     */
    private transient float lastSyncedHp = Float.NaN;
    private transient float lastSyncedMaxHp = Float.NaN;
    private transient float lastSyncedKi = Float.NaN;
    private transient float lastSyncedMaxKi = Float.NaN;
    private transient float lastSyncedStm = Float.NaN;
    private transient float lastSyncedMaxStm = Float.NaN;
    private transient int lastSyncTick = -99999;

    public int getLastSyncTick() { return lastSyncTick; }

    /** True when any tracked value has drifted far enough from what the client was last told. */
    public boolean statsDifferFrom(float hp, float maxHp, float ki, float maxKi,
                                   float stm, float maxStm) {
        return drifted(hp, lastSyncedHp) || drifted(maxHp, lastSyncedMaxHp)
                || drifted(ki, lastSyncedKi) || drifted(maxKi, lastSyncedMaxKi)
                || drifted(stm, lastSyncedStm) || drifted(maxStm, lastSyncedMaxStm);
    }

    public void markSynced(float hp, float maxHp, float ki, float maxKi,
                           float stm, float maxStm, int tick) {
        lastSyncedHp = hp;
        lastSyncedMaxHp = maxHp;
        lastSyncedKi = ki;
        lastSyncedMaxKi = maxKi;
        lastSyncedStm = stm;
        lastSyncedMaxStm = maxStm;
        lastSyncTick = tick;
    }

    /** ~0.05 absolute — HUD-visible without flooding the pipe. NaN means "never sent". */
    private static boolean drifted(float now, float sent) {
        return Float.isNaN(sent) || Math.abs(now - sent) > 0.05f;
    }

    public float getKi() { return ki; }
    public void setKi(float ki) { this.ki = Math.max(0, Math.min(ki, maxKi)); }
    public float getMaxKi() { return maxKi; }
    public void setMaxKi(float maxKi) { this.maxKi = maxKi; }

    public float getStamina() { return stamina; }
    public void setStamina(float stamina) { this.stamina = Math.max(0, Math.min(stamina, maxStamina)); }
    public float getMaxStamina() { return maxStamina; }
    public void setMaxStamina(float maxStamina) { this.maxStamina = maxStamina; }

    public String getSuperSoulId() { return superSoulId == null ? "" : superSoulId; }
    public void setSuperSoulId(String id) { this.superSoulId = id == null ? "" : id; }

    /**
     * Shi Shin No Ken mastery, 0 to {@code CloneFormation.PERFECT_MASTERY}. Accrues while divided
     * and closes the power split it normally costs; at the cap the division is free.
     *
     * <p>A usage counter rather than one of the point-buy skill levels above, which are capped at
     * three and spent from skill points — neither of which can express a thousand-step ramp.
     */
    public int getMultiFormMastery() { return multiFormMastery; }

    public void setMultiFormMastery(int value) {
        this.multiFormMastery = Math.max(0, Math.min(
                net.bullettrain.xenopixelsmod.combat.clone.CloneFormation.PERFECT_MASTERY, value));
    }

    public void addMultiFormMastery(int amount) {
        if (amount > 0) setMultiFormMastery(multiFormMastery + Math.min(amount,
                net.bullettrain.xenopixelsmod.combat.clone.CloneFormation.PERFECT_MASTERY));
    }

    /**
     * This player's standing with {@code factionId}.
     *
     * <p>Falls back to the faction's own default, then to neutral, so an unknown faction reads as
     * "no opinion" rather than as hostile.
     */
    public int getFactionStanding(String factionId) {
        if (factionId == null || factionId.isBlank()) {
            return 0;
        }
        String key = factionId.trim().toLowerCase(java.util.Locale.ROOT);
        Integer stored = factionStanding.get(key);
        if (stored != null) {
            return stored;
        }
        var faction = net.bullettrain.xenopixelsmod.npc.faction.XenoFactions.get(key);
        return faction == null ? 0 : faction.defaultStanding();
    }

    /** Moves standing by {@code delta}, clamped, and answers the new value. */
    public int addFactionStanding(String factionId, int delta) {
        if (factionId == null || factionId.isBlank() || delta == 0) {
            return getFactionStanding(factionId);
        }
        String key = factionId.trim().toLowerCase(java.util.Locale.ROOT);
        int next = net.bullettrain.xenopixelsmod.npc.faction.XenoFaction
                .clampStanding(getFactionStanding(key) + delta);
        factionStanding.put(key, next);
        return next;
    }

    /** Every faction this player has a stored standing with. */
    /** Every destination this player has discovered. */
    public java.util.Set<String> unlockedTransports() {
        return java.util.Set.copyOf(unlockedTransports);
    }

    /** Records an arrival, so a VISITED destination stays reachable afterwards. */
    public void unlockTransport(String id) {
        if (id != null && !id.isBlank() && unlockedTransports.size() < MAX_UNLOCKED_TRANSPORTS) {
            unlockedTransports.add(id.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** A bound, because this grows for the life of a character and is written to disk. */
    public static final int MAX_UNLOCKED_TRANSPORTS = 512;

    /**
     * A bound on how many banks one player can hold an account at.
     *
     * <p>Same reasoning as {@link #MAX_UNLOCKED_TRANSPORTS}, with more weight behind it: an
     * account carries item stacks, so an unbounded map is an unbounded player file.
     */
    public static final int MAX_BANK_ACCOUNTS = 64;

    /**
     * This player's account at one bank, created on first use.
     *
     * <p>Returns null only when the id is blank or the cap is already reached - the caller's job
     * is then to refuse the interaction, not to invent an account that cannot be saved.
     */
    public net.bullettrain.xenopixelsmod.npc.bank.BankAccount bankAccount(String bankId) {
        if (bankId == null || bankId.isBlank()) {
            return null;
        }
        String key = bankId.trim().toLowerCase(java.util.Locale.ROOT);
        var existing = bankAccounts.get(key);
        if (existing != null) {
            return existing;
        }
        if (bankAccounts.size() >= MAX_BANK_ACCOUNTS) {
            return null;
        }
        var created = new net.bullettrain.xenopixelsmod.npc.bank.BankAccount(key);
        bankAccounts.put(key, created);
        return created;
    }

    /** Every account, for tests and for the save. */
    public java.util.Map<String, net.bullettrain.xenopixelsmod.npc.bank.BankAccount> bankAccounts() {
        return java.util.Collections.unmodifiableMap(bankAccounts);
    }

    public java.util.Map<String, Integer> factionStandings() {
        return java.util.Map.copyOf(factionStanding);
    }

    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int points) { this.skillPoints = Math.max(0, points); }
    public void addSkillPoints(int points) { if (points > 0) skillPoints += points; }

    public int getSkillLevel(String id) {
        if (id == null) return 0;
        return Math.max(0, skillLevels.getOrDefault(id.toLowerCase(), 0));
    }

    public void setSkillLevel(String id, int level) {
        if (id == null || id.isBlank()) return;
        skillLevels.put(id.toLowerCase(), Math.max(0, Math.min(3, level)));
    }

    public Map<String, Integer> getSkillLevels() {
        return Collections.unmodifiableMap(skillLevels);
    }

    public UUID getMentorUuid() { return mentorUuid; }
    public String getMentorName() { return mentorName == null ? "" : mentorName; }
    public void setMentor(UUID uuid, String name) {
        this.mentorUuid = uuid;
        this.mentorName = name == null ? "" : name;
    }
    public void clearMentor() {
        this.mentorUuid = null;
        this.mentorName = "";
    }

    /** Every quest this player is on, and every one they have finished. */
    public QuestBook quests() {
        return quests;
    }

    public long getDummyTotalDamage() { return dummyTotalDamage; }
    public long getDummySessionDamage() { return dummySessionDamage; }
    public int getDummyHits() { return dummyHits; }

    public void resetDummySession() {
        dummySessionDamage = 0;
        dummyHits = 0;
    }

    public void addDummyHit(float damage) {
        long d = Math.max(0, Math.round(damage));
        dummySessionDamage += d;
        dummyTotalDamage += d;
        dummyHits++;
    }

    public void copyFrom(XenoPlayerData other) {
        this.ki = other.ki;
        this.maxKi = other.maxKi;
        this.stamina = other.stamina;
        this.maxStamina = other.maxStamina;
        this.superSoulId = other.superSoulId;
        this.skillPoints = other.skillPoints;
        this.factionStanding.clear();
        this.factionStanding.putAll(other.factionStanding);
        setMultiFormMastery(other.multiFormMastery);
        this.skillLevels.clear();
        this.skillLevels.putAll(other.skillLevels);
        this.mentorUuid = other.mentorUuid;
        this.mentorName = other.mentorName;
        // Through NBT rather than field-by-field: the book owns its own shape, and a copy that
        // reached inside it would need updating every time that shape changed.
        CompoundTag carried = new CompoundTag();
        other.quests.saveTo(carried);
        this.quests.loadFrom(carried);
        this.scriptData = other.scriptData.copy();
        this.scriptTimers = other.scriptTimers.copy();
        this.viewedDialogues.clear();
        this.viewedDialogues.addAll(other.viewedDialogues);
        this.itemGiverUses.clear();
        this.itemGiverUses.putAll(other.itemGiverUses);
        this.dummyTotalDamage = other.dummyTotalDamage;
        this.dummySessionDamage = other.dummySessionDamage;
        this.dummyHits = other.dummyHits;
        // Through NBT rather than field-by-field, like the quest book above: dying must not empty
        // a player's vault, and a copy that reached inside an account would need updating every
        // time that shape changed.
        this.bankAccounts.clear();
        other.bankAccounts.forEach((id, account) -> this.bankAccounts.put(id,
                net.bullettrain.xenopixelsmod.npc.bank.BankAccount.load(id, account.save())));
        this.taotto = other.taotto.copy();
    }

    public void saveNBT(CompoundTag tag) {
        tag.putFloat("Ki", ki);
        tag.putFloat("MaxKi", maxKi);
        tag.putFloat("Stamina", stamina);
        tag.putFloat("MaxStamina", maxStamina);
        tag.putString("SuperSoul", getSuperSoulId());
        tag.putInt("SkillPoints", skillPoints);
        CompoundTag standings = new CompoundTag();
        factionStanding.forEach(standings::putInt);
        tag.put("FactionStanding", standings);
        tag.putInt("MultiFormMastery", multiFormMastery);
        CompoundTag skills = new CompoundTag();
        for (Map.Entry<String, Integer> e : skillLevels.entrySet()) {
            skills.putInt(e.getKey(), e.getValue());
        }
        tag.put("Skills", skills);
        if (mentorUuid != null) {
            tag.putUUID("Mentor", mentorUuid);
            tag.putString("MentorName", getMentorName());
        }
        quests.saveTo(tag);
        tag.put("XenoScriptData", scriptData.copy());
        tag.put("XenoScriptTimers", scriptTimers.copy());
        if (!viewedDialogues.isEmpty()) {
            ListTag viewed = new ListTag();
            for (String id : viewedDialogues) viewed.add(StringTag.valueOf(id));
            tag.put("ViewedDialogues", viewed);
        }
        if (!itemGiverUses.isEmpty()) {
            ListTag uses = new ListTag();
            itemGiverUses.forEach((npc, use) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Npc", npc);
                entry.putLong("Millis", use.lastMillis());
                entry.putLong("Day", use.lastDay());
                entry.putInt("Next", use.nextSlot());
                uses.add(entry);
            });
            tag.put("ItemGiverUses", uses);
        }
        if (!unlockedTransports.isEmpty()) {
            net.minecraft.nbt.ListTag unlocked = new net.minecraft.nbt.ListTag();
            for (String id : unlockedTransports) {
                unlocked.add(net.minecraft.nbt.StringTag.valueOf(id));
            }
            tag.put("UnlockedTransports", unlocked);
        }
        if (!bankAccounts.isEmpty()) {
            CompoundTag banks = new CompoundTag();
            bankAccounts.forEach((id, account) -> {
                // An account nobody has put anything into is not worth a line in the save; it is
                // recreated the moment they open that bank again.
                if (!account.isEmpty()) {
                    banks.put(id, account.save());
                }
            });
            if (!banks.isEmpty()) {
                tag.put("BankAccounts", banks);
            }
        }
        tag.putLong("DummyTotal", dummyTotalDamage);
        tag.putLong("DummySession", dummySessionDamage);
        tag.putInt("DummyHits", dummyHits);
        CompoundTag taottoTag = new CompoundTag();
        taotto.saveNbt(taottoTag);
        tag.put("Taotto", taottoTag);
    }

    public void loadNBT(CompoundTag tag) {
        // Guarded like every field below them. Unguarded, a tag without MaxKi read as 0, and setKi
        // then clamped ki to 0 as well - a player silently lost both pools instead of keeping the
        // constructor's 100f. saveNBT always writes them, so this only bit on a hand-edited or
        // foreign tag, but the asymmetry with SuperSoul and Skills directly below was not intended.
        ki = tag.contains("Ki") ? tag.getFloat("Ki") : ki;
        maxKi = tag.contains("MaxKi") ? tag.getFloat("MaxKi") : maxKi;
        stamina = tag.contains("Stamina") ? tag.getFloat("Stamina") : stamina;
        maxStamina = tag.contains("MaxStamina") ? tag.getFloat("MaxStamina") : maxStamina;
        superSoulId = tag.contains("SuperSoul") ? tag.getString("SuperSoul") : "";
        skillPoints = tag.getInt("SkillPoints");
        factionStanding.clear();
        if (tag.contains("FactionStanding")) {
            CompoundTag standings = tag.getCompound("FactionStanding");
            for (String key : standings.getAllKeys()) {
                factionStanding.put(key,
                        net.bullettrain.xenopixelsmod.npc.faction.XenoFaction
                                .clampStanding(standings.getInt(key)));
            }
        }
        setMultiFormMastery(tag.getInt("MultiFormMastery"));
        skillLevels.clear();
        if (tag.contains("Skills", Tag.TAG_COMPOUND)) {
            CompoundTag skills = tag.getCompound("Skills");
            for (String key : skills.getAllKeys()) {
                skillLevels.put(key, skills.getInt(key));
            }
        }
        if (tag.hasUUID("Mentor")) {
            mentorUuid = tag.getUUID("Mentor");
            mentorName = tag.getString("MentorName");
        } else {
            mentorUuid = null;
            mentorName = "";
        }
        quests.loadFrom(tag);
        scriptTimers = tag.getCompound("XenoScriptTimers").copy();
        scriptData = new CompoundTag();
        if (tag.contains("XenoScriptData", Tag.TAG_COMPOUND)) {
            CompoundTag loaded = tag.getCompound("XenoScriptData");
            for (String key : loaded.getAllKeys()) {
                if (scriptData.size() >= 64) break;
                if (key.length() <= 64 && (loaded.contains(key, Tag.TAG_STRING)
                        || loaded.contains(key, Tag.TAG_DOUBLE))) {
                    scriptData.put(key, loaded.get(key).copy());
                }
            }
        }
        viewedDialogues.clear();
        ListTag viewed = tag.getList("ViewedDialogues", Tag.TAG_STRING);
        for (int index = 0; index < viewed.size() && index < MAX_VIEWED_DIALOGUES; index++) {
            recordViewedDialogue(viewed.getString(index));
        }
        itemGiverUses.clear();
        ListTag uses = tag.getList("ItemGiverUses", Tag.TAG_COMPOUND);
        for (int index = 0; index < uses.size() && itemGiverUses.size() < MAX_ITEM_GIVER_USES;
                index++) {
            CompoundTag entry = uses.getCompound(index);
            if (entry.hasUUID("Npc")) recordItemGiverUse(entry.getUUID("Npc"),
                    entry.getLong("Millis"), entry.getLong("Day"), entry.getInt("Next"));
        }
        unlockedTransports.clear();
        if (tag.contains("UnlockedTransports")) {
            net.minecraft.nbt.ListTag unlocked = tag.getList("UnlockedTransports",
                    net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < Math.min(MAX_UNLOCKED_TRANSPORTS, unlocked.size()); i++) {
                unlockTransport(unlocked.getString(i));
            }
        }
        bankAccounts.clear();
        if (tag.contains("BankAccounts")) {
            CompoundTag banks = tag.getCompound("BankAccounts");
            for (String id : banks.getAllKeys()) {
                // Bounded on read as well as on write: a hand-edited or corrupted player file
                // claiming a thousand accounts must not become a thousand accounts in memory.
                if (bankAccounts.size() >= MAX_BANK_ACCOUNTS) {
                    break;
                }
                bankAccounts.put(id, net.bullettrain.xenopixelsmod.npc.bank.BankAccount.load(
                        id, banks.getCompound(id)));
            }
        }
        migrateLegacyQuest(tag);
        dummyTotalDamage = tag.getLong("DummyTotal");
        dummySessionDamage = tag.getLong("DummySession");
        dummyHits = tag.getInt("DummyHits");
        if (tag.contains("Taotto", Tag.TAG_COMPOUND)) {
            taotto = net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument.loadNbt(
                    tag.getCompound("Taotto"));
        }
    }

    /**
     * Folds a pre-book save into the book.
     *
     * <p>The old shape held one quest as three scalars plus a player-wide visited set. Dropping it
     * would silently lose whatever the player was part-way through, so it is read once here and the
     * old keys are never written again.
     */
    private void migrateLegacyQuest(CompoundTag tag) {
        if (!tag.contains("QuestId") || tag.getString("QuestId").isEmpty()) {
            return;
        }
        String id = tag.getString("QuestId");
        int target = tag.getInt("QuestTarget");
        if (target <= 0 || quests.isActive(id)) {
            return;
        }
        quests.start(id, target);
        ActiveQuest quest = quests.active(id);
        quest.addProgress(tag.getInt("QuestProgress"));
        ListTag visited = tag.getList("QuestVisited", Tag.TAG_STRING);
        for (int i = 0; i < visited.size(); i++) {
            // The old set belonged to whichever quest was running, which is this one.
            quest.markVisited(visited.getString(i));
        }
    }
}
