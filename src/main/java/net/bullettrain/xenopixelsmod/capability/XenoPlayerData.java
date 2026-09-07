package net.bullettrain.xenopixelsmod.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private int multiFormMastery;
    private UUID mentorUuid;
    private String mentorName = "";
    private String questId = "";
    private int questProgress;
    private int questTarget;
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

    public String getQuestId() { return questId == null ? "" : questId; }
    public int getQuestProgress() { return questProgress; }
    public int getQuestTarget() { return questTarget; }
    public boolean hasActiveQuest() { return questId != null && !questId.isEmpty() && questTarget > 0; }

    public void startQuest(String id, int target) {
        this.questId = id == null ? "" : id;
        this.questProgress = 0;
        this.questTarget = Math.max(1, target);
    }

    public void clearQuest() {
        this.questId = "";
        this.questProgress = 0;
        this.questTarget = 0;
    }

    /** @return true if quest completed this tick */
    public boolean addQuestProgress(int amount) {
        if (!hasActiveQuest()) return false;
        questProgress = Math.min(questTarget, questProgress + Math.max(0, amount));
        return questProgress >= questTarget;
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
        setMultiFormMastery(other.multiFormMastery);
        this.skillLevels.clear();
        this.skillLevels.putAll(other.skillLevels);
        this.mentorUuid = other.mentorUuid;
        this.mentorName = other.mentorName;
        this.questId = other.questId;
        this.questProgress = other.questProgress;
        this.questTarget = other.questTarget;
        this.dummyTotalDamage = other.dummyTotalDamage;
        this.dummySessionDamage = other.dummySessionDamage;
        this.dummyHits = other.dummyHits;
    }

    public void saveNBT(CompoundTag tag) {
        tag.putFloat("Ki", ki);
        tag.putFloat("MaxKi", maxKi);
        tag.putFloat("Stamina", stamina);
        tag.putFloat("MaxStamina", maxStamina);
        tag.putString("SuperSoul", getSuperSoulId());
        tag.putInt("SkillPoints", skillPoints);
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
        tag.putString("QuestId", getQuestId());
        tag.putInt("QuestProgress", questProgress);
        tag.putInt("QuestTarget", questTarget);
        tag.putLong("DummyTotal", dummyTotalDamage);
        tag.putLong("DummySession", dummySessionDamage);
        tag.putInt("DummyHits", dummyHits);
    }

    public void loadNBT(CompoundTag tag) {
        ki = tag.getFloat("Ki");
        maxKi = tag.getFloat("MaxKi");
        stamina = tag.getFloat("Stamina");
        maxStamina = tag.getFloat("MaxStamina");
        superSoulId = tag.contains("SuperSoul") ? tag.getString("SuperSoul") : "";
        skillPoints = tag.getInt("SkillPoints");
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
        questId = tag.getString("QuestId");
        questProgress = tag.getInt("QuestProgress");
        questTarget = tag.getInt("QuestTarget");
        dummyTotalDamage = tag.getLong("DummyTotal");
        dummySessionDamage = tag.getLong("DummySession");
        dummyHits = tag.getInt("DummyHits");
    }
}
