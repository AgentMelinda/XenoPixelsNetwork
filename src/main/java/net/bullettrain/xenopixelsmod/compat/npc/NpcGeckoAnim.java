package net.bullettrain.xenopixelsmod.compat.npc;

import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncAnimation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import software.bernie.geckolib.animation.RawAnimation;

/** Uses CNPC Gecko Addon's public animation payload API when its custom model is active. */
public final class NpcGeckoAnim {
    private NpcGeckoAnim() {}

    public static boolean play(LivingEntity npc, String animation) {
        if (npc == null || animation == null || animation.isBlank()
                || !ModList.get().isLoaded("cnpcgeckoaddon")) {
            return false;
        }
        try {
            Object display = Class.forName("noppes.npcs.entity.EntityNPCInterface")
                    .getField("display").get(npc);
            if (!(display instanceof IDataDisplay addon) || !addon.hasCustomModel()) {
                return false;
            }
            RawAnimation raw = RawAnimation.begin().thenPlay(animation.trim());
            NetworkWrapper.sendAll(new PacketSyncAnimation(npc.getId(), raw));
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    /**
     * Plays the attack clip configured in the Gecko model editor.  Custom Gecko
     * surrogates do not consume CustomNPC's vanilla swing event, so calling
     * {@code LivingEntity.swing()} alone leaves the hands frozen.  The addon
     * already exposes the configured clip and its public animation packet; use
     * that path for ki casts as well as ordinary attacks.
     */
    public static boolean playAttack(LivingEntity npc) {
        if (npc == null || !ModList.get().isLoaded("cnpcgeckoaddon")) {
            return false;
        }
        try {
            Object display = Class.forName("noppes.npcs.entity.EntityNPCInterface")
                    .getField("display").get(npc);
            if (!(display instanceof IDataDisplay addon) || !addon.hasCustomModel()) {
                return false;
            }
            CustomModelData model = addon.getCustomModelData();
            String attack = model.getAttackAnim();
            if (attack == null || attack.isBlank()) {
                return false;
            }
            return play(npc, attack);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
