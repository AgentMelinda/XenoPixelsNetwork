package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcTechniqueLevelsTest {
    @Test
    void levelsPersistWithCanonicalTechniqueIds() {
        NpcTechniqueLevels levels = new NpcTechniqueLevels();
        levels.set("KAMEHAMEHA", 4, 7);

        NpcTechniqueLevels loaded = new NpcTechniqueLevels();
        loaded.load(levels.save());

        assertEquals(4, loaded.damageLevel("kamehameha"));
        assertEquals(7, loaded.cooldownLevel("Kamehameha"));
    }

    @Test
    void levelsClampToDragonMineZsEffectiveUpgradeRange() {
        NpcTechniqueLevels levels = new NpcTechniqueLevels();
        levels.set("kamehameha", -4, NpcTechniqueLevels.MAX_EFFECTIVE_LEVEL + 20);

        assertEquals(0, levels.damageLevel("kamehameha"));
        assertEquals(NpcTechniqueLevels.MAX_EFFECTIVE_LEVEL,
                levels.cooldownLevel("kamehameha"));
    }

    @Test
    void malformedLevelsAreClampedWhenLoaded() {
        CompoundTag saved = new CompoundTag();
        CompoundTag values = new CompoundTag();
        values.putInt("Damage", 999);
        values.putInt("Cooldown", -10);
        saved.put("final_flash", values);

        NpcTechniqueLevels loaded = new NpcTechniqueLevels();
        loaded.load(saved);

        assertEquals(NpcTechniqueLevels.MAX_EFFECTIVE_LEVEL,
                loaded.damageLevel("final_flash"));
        assertEquals(0, loaded.cooldownLevel("final_flash"));
    }
}
