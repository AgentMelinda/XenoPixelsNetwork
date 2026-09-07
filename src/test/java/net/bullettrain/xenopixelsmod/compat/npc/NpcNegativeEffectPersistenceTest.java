package net.bullettrain.xenopixelsmod.compat.npc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class NpcNegativeEffectPersistenceTest {
    @Test void vanillaEffectNbtRoundTripPreservesHiddenEffectsAndFlags() {
        CompoundTag data = new CompoundTag();
        MobEffectInstance hidden = new MobEffectInstance(MobEffects.POISON, 600, 0);
        MobEffectInstance effect = new MobEffectInstance(MobEffects.POISON, 120, 2, true, false, false, hidden);
        NpcNegativeEffectPersistence.capture(data, List.of(effect, new MobEffectInstance(MobEffects.REGENERATION, 80)));
        var saved = NpcNegativeEffectPersistence.consume(data);
        assertEquals(1, saved.size());
        MobEffectInstance restored = MobEffectInstance.load(saved.getCompound(0));
        assertNotNull(restored);
        assertEquals(effect.save(), restored.save());
        assertTrue(NpcNegativeEffectPersistence.consume(data).isEmpty());
    }
    @Test void cureOrExpiryRefreshRemovesStaleTransfer() {
        CompoundTag data = new CompoundTag();
        NpcNegativeEffectPersistence.capture(data, List.of(new MobEffectInstance(MobEffects.WEAKNESS, 1)));
        NpcNegativeEffectPersistence.capture(data, List.of());
        assertTrue(NpcNegativeEffectPersistence.consume(data).isEmpty());
    }
    @Test void infiniteEffectsSurviveTransferExactlyOnce() {
        CompoundTag data = new CompoundTag();
        NpcNegativeEffectPersistence.capture(data, List.of(new MobEffectInstance(MobEffects.WEAKNESS, -1, 3)));
        var effect = MobEffectInstance.load(NpcNegativeEffectPersistence.consume(data).getCompound(0));
        assertNotNull(effect);
        assertTrue(effect.isInfiniteDuration());
        assertEquals(3, effect.getAmplifier());
        assertTrue(NpcNegativeEffectPersistence.consume(data).isEmpty());
    }
}
