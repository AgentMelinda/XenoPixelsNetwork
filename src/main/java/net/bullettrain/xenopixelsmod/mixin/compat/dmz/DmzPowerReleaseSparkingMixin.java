package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.skills.Skills;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets Sparking actually raise the power-release ceiling.
 *
 * <p>DragonMineZ caps release in {@code TickHandler.chargePowerRelease} as
 * {@code 50 + potentialunlock_level * 5} — 115 at the skill's maximum of 13 — and then applies the
 * player's own {@code releaseLimit} with a <b>min</b>:
 *
 * <pre>{@code
 * int cap = 50 + level * 5;
 * if (releaseLimit > 0) cap = Math.min(cap, releaseLimit);
 * }</pre>
 *
 * <p>So {@code releaseLimit} is a throttle the player sets <i>below</i> their cap; raising it alone
 * does nothing, because the min keeps 115. The skill-derived half is what has to move, which is why
 * this redirects the level lookup rather than the limit.
 *
 * <p>Reports whatever level yields the configured Sparking ceiling — 225 is {@code 50 + 35 * 5}, so
 * level 35 — and never lowers a real level. It is deliberately paired with the {@code setReleaseLimit}
 * call in {@link Bt3SparkingSystem}: both halves of that min have to be lifted for the ceiling to
 * move, and both are restored when Sparking ends.
 *
 * <p><b>Side effect, on purpose.</b> DMZ reuses the same level for the ramp rate
 * ({@code 1 + min(10, level) * 0.1}), so release also climbs at the maximum rate while Sparking.
 * That is the intent of a burst state; it is called out here so it is not mistaken for a bug.
 */
@Mixin(targets = "com.dragonminez.server.events.players.TickHandler", remap = false)
public abstract class DmzPowerReleaseSparkingMixin {

    @Redirect(
            method = "chargePowerRelease(Lcom/dragonminez/common/stats/StatsData;IZ)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/dragonminez/common/stats/skills/Skills;getSkillLevel("
                            + "Ljava/lang/String;)I"),
            require = 0)
    private static int xenopixels$sparkingReleaseCeiling(Skills skills, String skillId,
                                                         StatsData data, int tick, boolean flag) {
        int level = skills == null ? 0 : skills.getSkillLevel(skillId);
        if (!"potentialunlock".equals(skillId) || data == null) return level;
        if (!(playerOf(data) instanceof ServerPlayer player)) return level;
        if (!Bt3SparkingSystem.isSparking(player)) return level;

        int target = XenoServerConfig.sparkingReleaseLimit;
        if (target <= 50) return level;
        // Invert DMZ's own formula so the ceiling lands exactly on the configured percentage.
        return Math.max(level, (target - 50) / 5);
    }

    private static net.minecraft.world.entity.player.Player playerOf(StatsData data) {
        try {
            return data.getResources() == null ? null : data.getResources().getPlayer();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
