package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * A lightweight, XenoPixels-owned combat profile for non-player NPCs (My NPCs / CustomNPCs
 * entities). DragonMineZ's real race/stats system ({@code com.dragonminez.common.stats.*}) has
 * no construction path for a non-{@link net.minecraft.world.entity.player.Player} entity —
 * {@code StatsProvider}/{@code StatsData} are hard-typed to {@code Player} throughout — so this
 * is a separate, parallel data holder rather than a real DMZ {@code StatsData} instance. The
 * stat field names mirror DMZ's real {@code com.dragonminez.common.stats.character.Stats}
 * fields ({@code strength}, {@code strikePower}, {@code resistance}, {@code vitality},
 * {@code kiPower}, {@code energy}) so the numbers mean the same thing a player's DMZ stats would.
 *
 * <p>Stored directly in the NPC entity's vanilla persistent NBT — no capability/attachment
 * registration needed.
 */
public final class NpcCombatProfile {
    private static final String NBT_KEY = "xenopixels:npc_combat_profile";

    private static final String TAG_RACE = "Race";
    private static final String TAG_STRENGTH = "Strength";
    private static final String TAG_STRIKE_POWER = "StrikePower";
    private static final String TAG_RESISTANCE = "Resistance";
    private static final String TAG_VITALITY = "Vitality";
    private static final String TAG_KI_POWER = "KiPower";
    private static final String TAG_ENERGY = "Energy";

    public String raceId = "human";
    public int strength;
    public int strikePower;
    public int resistance;
    public int vitality;
    public int kiPower;
    public int energy;

    public NpcCombatProfile() {}

    public static NpcCombatProfile read(Entity entity) {
        NpcCombatProfile profile = new NpcCombatProfile();
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(NBT_KEY)) {
            return profile;
        }
        CompoundTag tag = root.getCompound(NBT_KEY);
        profile.raceId = tag.contains(TAG_RACE) ? tag.getString(TAG_RACE) : profile.raceId;
        profile.strength = tag.getInt(TAG_STRENGTH);
        profile.strikePower = tag.getInt(TAG_STRIKE_POWER);
        profile.resistance = tag.getInt(TAG_RESISTANCE);
        profile.vitality = tag.getInt(TAG_VITALITY);
        profile.kiPower = tag.getInt(TAG_KI_POWER);
        profile.energy = tag.getInt(TAG_ENERGY);
        return profile;
    }

    public void write(Entity entity) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RACE, raceId);
        tag.putInt(TAG_STRENGTH, strength);
        tag.putInt(TAG_STRIKE_POWER, strikePower);
        tag.putInt(TAG_RESISTANCE, resistance);
        tag.putInt(TAG_VITALITY, vitality);
        tag.putInt(TAG_KI_POWER, kiPower);
        tag.putInt(TAG_ENERGY, energy);
        entity.getPersistentData().put(NBT_KEY, tag);
    }
}
