package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestNpcTargetTest {
    @Test
    void recognizesOnlySupportedNpcFamiliesWithoutLoadingOptionalClasses() {
        assertEquals(QuestNpcTarget.Family.XENO,
                QuestNpcTarget.family("net.bullettrain.xenopixelsmod.npc.XenoNpcEntity", "xenopixelsmod:xeno_npc_humanoid"));
        assertEquals(QuestNpcTarget.Family.MYNPCS,
                QuestNpcTarget.family("espi.mynpcs.entity.EntityCustomNpc", "mynpcs:customnpc"));
        assertEquals(QuestNpcTarget.Family.CUSTOMNPCS,
                QuestNpcTarget.family("noppes.npcs.entity.EntityNPCInterface", "customnpcs:customnpc"));
        assertNull(QuestNpcTarget.family("net.minecraft.world.entity.monster.Zombie", "minecraft:zombie"));
        assertNull(QuestNpcTarget.family("espi.mynpcs.entity.EntityProjectile", "mynpcs:projectile"));
        assertNull(QuestNpcTarget.family("noppes.npcs.entity.EntityFakeLiving", "customnpcs:fakeliving"));
    }

    @Test
    void derivesTheOptionalNpcFamilyFromTypeIdsWhenTheirClassNamesAreWrapped() {
        assertEquals(QuestNpcTarget.Family.MYNPCS,
                QuestNpcTarget.family("some.wrapper.NpcProxy", "mynpcs:customnpc"));
        assertEquals(QuestNpcTarget.Family.CUSTOMNPCS,
                QuestNpcTarget.family("some.wrapper.NpcProxy", "customnpcs:customnpc"));
    }
}
