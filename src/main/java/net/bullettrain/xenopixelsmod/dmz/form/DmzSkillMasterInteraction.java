package net.bullettrain.xenopixelsmod.dmz.form;

import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * Single server-side entry point for opening the DMZ skill-master menu from an NPC.
 *
 * <p>Shared by the role's {@code interact(Player)} and by any other caller so both agree on
 * exactly when a menu opens. Distinct from {@link DmzFormTrainerEvents}, which preserves the
 * existing "listed as a trainer" behaviour for every designated NPC; this path additionally
 * requires the group's skill-giving module to be on, which is what makes an NPC a skill master
 * rather than merely a trainer.
 */
public final class DmzSkillMasterInteraction {
    private DmzSkillMasterInteraction() {
    }

    /**
     * Opens the master menu for {@code player} when {@code trainer} is a functioning skill master.
     *
     * <p>Returns {@code true} only when a menu was actually sent. Guards every input so a client
     * side call, a non-NPC target, or an NPC whose group offers nothing is a silent no-op rather
     * than an exception on the interaction path.
     */
    public static boolean interact(LivingEntity trainer, Player player) {
        if (trainer == null || !(player instanceof ServerPlayer serverPlayer)) return false;
        if (serverPlayer.level().isClientSide()) return false;
        UUID trainerId = trainer.getUUID();
        if (trainerId == null) return false;
        List<DmzFormMetadata> offerings = DmzFormMetadataRegistry.trainerOfferings(trainerId);
        if (offerings.isEmpty()) return false;
        if (!DmzSkillMaster.isSkillMasterAnywhere(offerings, trainerId)) return false;
        FormEditorNetwork.sendTrainer(serverPlayer, trainer, offerings);
        return true;
    }

    /** True when {@code trainer} would open the master menu for a player right now. */
    public static boolean isActiveSkillMaster(LivingEntity trainer) {
        if (trainer == null) return false;
        UUID trainerId = trainer.getUUID();
        if (trainerId == null) return false;
        return DmzSkillMaster.isSkillMasterAnywhere(
                DmzFormMetadataRegistry.trainerOfferings(trainerId), trainerId);
    }
}