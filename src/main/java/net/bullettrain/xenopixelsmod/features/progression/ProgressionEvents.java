package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Phase 3: dummy damage meter, Super Soul + skill damage mods, quests, mentor.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ProgressionEvents {
    public static final String DUMMY_TAG = "xenopixelsmod_training_dummy";

    private ProgressionEvents() {}

    public static boolean isTrainingDummy(Entity e) {
        return e != null && e.getPersistentData().getBoolean(DUMMY_TAG);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        float amount = event.getAmount();
        if (amount <= 0.01f) return;

        LivingEntity victim = event.getEntity();
        Entity src = event.getSource().getEntity();

        // --- Incoming Super Soul reduction ---
        if (victim instanceof ServerPlayer def) {
            float in = SuperSoulCatalog.inMult(def);
            if (in < 0.999f) {
                event.setAmount(amount * in);
                amount = event.getAmount();
            }
        }

        // --- Outgoing player damage (skills + soul) ---
        if (src instanceof ServerPlayer atk) {
            float mult = CombatSkills.powerMult(atk) * SuperSoulCatalog.outMult(atk);
            if (mult > 1.001f || mult < 0.999f) {
                event.setAmount(amount * mult);
                amount = event.getAmount();
            }

            // Sparking build with soul/skill
            if (XenoServerConfig.bt3SparkingEnabled) {
                float build = XenoServerConfig.sparkingBuildPerHit
                        * CombatSkills.sparkingBuildMult(atk)
                        * SuperSoulCatalog.sparkMult(atk);
                // Bt3SparkingSystem already adds base; add bonus only
                float bonus = build - XenoServerConfig.sparkingBuildPerHit;
                if (bonus > 0.1f) {
                    Bt3SparkingSystem.addMeter(atk, bonus);
                }
            }

            // Training dummy meter
            if (isTrainingDummy(victim) && XenoServerConfig.trainingDummyEnabled) {
                handleDummyHit(atk, amount);
                // Keep dummy alive
                if (victim.getHealth() - amount < 1f) {
                    event.setAmount(0f);
                    victim.setHealth(victim.getMaxHealth());
                }
            }

            // Mentor nearby: small assist credit
            if (XenoServerConfig.mentorEnabled) {
                mentorAssist(atk, amount);
            }
        }
    }

    private static void handleDummyHit(ServerPlayer atk, float amount) {
        atk.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
            data.addDummyHit(amount);
            int hits = data.getDummyHits();
            long session = data.getDummySessionDamage();

            // Throttle action-bar spam: every 5th hit (or first) — saves packet/chat churn in combos
            if (hits == 1 || hits % 5 == 0) {
                atk.displayClientMessage(Component.literal(
                        String.format("§eDummy §7| hit §f%.1f §7| session §f%d §7| hits §f%d §7| total §f%d",
                                amount, session, hits, data.getDummyTotalDamage())), true);
            }

            // Milestone skill points
            if (hits == 25 || hits == 100 || hits == 250) {
                data.addSkillPoints(1);
                atk.displayClientMessage(Component.literal(
                        "§a+1 Skill Point §7(dummy milestone " + hits + " hits)"), false);
            }

            // dummy_session quest: progress by damage dealt this hit
            if (XenoServerConfig.parallelQuestEnabled
                    && data.hasActiveQuest()
                    && "dummy_session".equals(data.getQuestId())) {
                int dmg = Math.max(1, Math.round(amount));
                if (data.addQuestProgress(dmg)) {
                    completeQuest(atk, data);
                } else if (hits % 5 == 0) {
                    atk.displayClientMessage(Component.literal(
                            "§bQuest §7" + data.getQuestProgress() + "/" + data.getQuestTarget()), true);
                }
            }
        });
    }

    private static void mentorAssist(ServerPlayer student, float damage) {
        student.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
            if (data.getMentorUuid() == null) return;
            ServerPlayer mentor = student.server.getPlayerList().getPlayer(data.getMentorUuid());
            if (mentor == null || mentor == student) return;
            if (mentor.distanceTo(student) > 32.0) return;
            // Mentor gains a sliver of sparking meter for nearby student damage
            Bt3SparkingSystem.addMeter(mentor, Math.min(2f, damage * 0.05f));
        });
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (!XenoServerConfig.parallelQuestEnabled) return;

        killer.getCapability(XenoCapabilities.XENO_DATA).ifPresent(data -> {
            if (!data.hasActiveQuest()) return;
            String q = data.getQuestId();
            boolean count = switch (q) {
                case "kill_mobs" -> !(event.getEntity() instanceof Player)
                        && !isTrainingDummy(event.getEntity());
                case "kill_players" -> event.getEntity() instanceof Player;
                case "dummy_hits" -> false; // handled via hits
                default -> !(event.getEntity() instanceof Player);
            };
            if (!count) return;
            if (data.addQuestProgress(1)) {
                completeQuest(killer, data);
            } else {
                killer.displayClientMessage(Component.literal(
                        "§bQuest §7" + data.getQuestProgress() + "/" + data.getQuestTarget()), true);
            }
        });
    }

    public static void completeQuest(ServerPlayer player, XenoPlayerData data) {
        String id = data.getQuestId();
        data.clearQuest();
        data.addSkillPoints(2);
        player.displayClientMessage(Component.literal(
                "§a§lQuest complete: §f" + id + " §7(+2 skill points)"), false);
        player.displayClientMessage(Component.literal(
                "§7Skill points: §f" + data.getSkillPoints() + " §7— /xenoskill unlock <power|guard|sparking|ultimate>"), false);
    }

    public static void tagAsDummy(Entity entity) {
        if (entity == null) return;
        CompoundTag tag = entity.getPersistentData();
        tag.putBoolean(DUMMY_TAG, true);
        entity.setCustomName(Component.literal("Training Dummy").withStyle(ChatFormatting.GOLD));
        entity.setCustomNameVisible(true);
        if (entity instanceof LivingEntity living) {
            living.setHealth(living.getMaxHealth());
        }
        if (entity instanceof ArmorStand stand) {
            stand.setInvisible(false);
            stand.setNoGravity(false);
        }
    }
}
