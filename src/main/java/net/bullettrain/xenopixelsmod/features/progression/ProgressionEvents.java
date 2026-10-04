package net.bullettrain.xenopixelsmod.features.progression;

import net.neoforged.fml.common.EventBusSubscriber;

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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Phase 3: dummy damage meter, Super Soul + skill damage mods, quests, mentor.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ProgressionEvents {
    public static final String DUMMY_TAG = "xenopixelsmod_training_dummy";

    private ProgressionEvents() {}

    public static boolean isTrainingDummy(Entity e) {
        return e != null && e.getPersistentData().getBoolean(DUMMY_TAG);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        float amount = event.getNewDamage();
        if (amount <= 0.01f) return;

        LivingEntity victim = event.getEntity();
        Entity src = event.getSource().getEntity();

        // --- Incoming Super Soul reduction ---
        if (victim instanceof ServerPlayer def) {
            float in = SuperSoulCatalog.inMult(def);
            if (in < 0.999f) {
                event.setNewDamage(amount * in);
                amount = event.getNewDamage();
            }
        }

        // --- Outgoing player damage (skills + soul) ---
        if (src instanceof ServerPlayer atk) {
            float mult = CombatSkills.powerMult(atk) * SuperSoulCatalog.outMult(atk);
            if (mult > 1.001f || mult < 0.999f) {
                event.setNewDamage(amount * mult);
                amount = event.getNewDamage();
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
                    event.setNewDamage(0f);
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
        XenoCapabilities.get(atk).ifPresent(data -> {
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
            // Off by default (2026-10-02 owner): every /xenotrain spawn restarts the hit count, so
            // the milestones could be earned again with each new dummy.
            if (net.bullettrain.xenopixelsmod.config.XenoServerConfig.trainingDummySkillPoints
                    && (hits == 25 || hits == 100 || hits == 250)) {
                data.addSkillPoints(1);
                atk.displayClientMessage(Component.literal(
                        "§a+1 Skill Point §7(dummy milestone " + hits + " hits)"), false);
            }

            // A dummy hit advances a quest that asked for dummy work. Which quest that is comes
            // from the quest's own goal now - this used to test the id against "dummy_session",
            // so a pack could not define a second dummy quest at all.
            if (XenoServerConfig.parallelQuestEnabled) {
                for (ActiveQuest quest : data.quests().actives()) {
                    if (quest.ready()) {
                        continue;
                    }
                    QuestObjective.Goal goal = ParallelQuests.goalFor(quest.id());
                    int step = goal == null ? 0 : goal.dummyProgress(amount);
                    if (step <= 0) {
                        continue;
                    }
                    if (quest.addProgress(step)) {
                        finish(atk, data, quest);
                    } else if (hits % 5 == 0) {
                        atk.displayClientMessage(Component.literal(
                                "§bQuest §7" + quest.progress() + "/" + quest.target()), true);
                    }
                }
            }
        });
    }

    private static void mentorAssist(ServerPlayer student, float damage) {
        XenoCapabilities.get(student).ifPresent(data -> {
            if (data.getMentorUuid() == null) return;
            ServerPlayer mentor = student.server.getPlayerList().getPlayer(data.getMentorUuid());
            if (mentor == null || mentor == student) return;
            if (mentor.distanceTo(student) > 32.0) return;
            // Mentor gains a sliver of sparking meter for nearby student damage
            Bt3SparkingSystem.addMeter(mentor, Math.min(2f, damage * 0.05f));
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || event.getEntity().tickCount % 20 != 0) return;
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !XenoServerConfig.parallelQuestEnabled) return;
        XenoCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest quest : data.quests().actives()) {
                if (quest.ready()) continue;
                if (!QuestRuntime.onPresence(player, quest)) continue;
                if (quest.allStepsDone(ParallelQuests.stepsFor(quest.id()).size(),
                        targetsOf(quest.id()))) {
                    finish(player, data, quest);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer player
                ? player : event.getSource().getDirectEntity() instanceof ServerPlayer directPlayer
                        ? directPlayer : null;
        if (killer == null) return;
        if (!XenoServerConfig.parallelQuestEnabled) return;

        XenoCapabilities.get(killer).ifPresent(data -> {
            // Every active quest whose goal matches, not just one: with several running, advancing
            // only the first would leave the rest stuck with no visible reason.
            //
            // Asked of the quest rather than switched on its id. The old switch had a default
            // branch that counted any non-player death, so a quest whose id it did not recognise -
            // every quest a datapack could define - silently became "kill anything".
            for (ActiveQuest quest : data.quests().actives()) {
                if (quest.ready()) {
                    continue;
                }
                if (!QuestRuntime.onKill(killer, data, quest, event.getEntity(),
                        isTrainingDummy(event.getEntity()))) {
                    continue;
                }
                if (quest.allStepsDone(ParallelQuests.stepsFor(quest.id()).size(),
                        targetsOf(quest.id())) || quest.progress() >= quest.target()) {
                    finish(killer, data, quest);
                } else {
                    killer.displayClientMessage(Component.literal(
                            "§bQuest §7" + quest.progress() + "/" + quest.target()), true);
                }
            }
        });
    }

    /**
     * Advances a {@code TALK_TO_NPC} quest, counted once per interaction.
     *
     * <p>Called from the NPC rather than from an event: there is no interaction event that says
     * <em>which</em> Xeno NPC was clicked and that also fires after the click has been accepted, and
     * counting a click the NPC then ignored would advance a quest for talking to nobody.
     *
     * <p>Quietly does nothing when the player is on no quest, on one that asked for something
     * else, or on one that has already counted this NPC. Every caller is an interaction, and most
     * interactions are not quest progress.
     */
    public static void onDialogOption(ServerPlayer player, String optionId) {
        onDialogViewed(player, optionId);
    }

    /** Counts a dialogue node when the server opens or navigates to it for a player. */
    public static void onDialogViewed(ServerPlayer player, String nodeId) {
        if (player == null || !XenoServerConfig.parallelQuestEnabled) return;
        XenoCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest quest : data.quests().actives()) {
                if (quest.ready()) continue;
                if (!QuestRuntime.onDialog(quest, nodeId)) continue;
                if (quest.allStepsDone(ParallelQuests.stepsFor(quest.id()).size(),
                        targetsOf(quest.id()))) {
                    finish(player, data, quest);
                }
            }
        });
    }

    public static void onTalkedToNpc(ServerPlayer player, java.util.UUID npcId, String npcName,
                                     String roleId) {
        if (player == null || !XenoServerConfig.parallelQuestEnabled) {
            return;
        }
        XenoCapabilities.get(player).ifPresent(data -> {
            for (ActiveQuest quest : data.quests().actives()) {
                if (quest.ready()) {
                    continue;
                }
                if (!QuestRuntime.onTalk(quest, npcName, roleId,
                        npcId == null ? "" : npcId.toString())) {
                    continue;
                }
                if (quest.allStepsDone(ParallelQuests.stepsFor(quest.id()).size(),
                        targetsOf(quest.id())) || quest.progress() >= quest.target()) {
                    finish(player, data, quest);
                } else {
                    player.displayClientMessage(Component.literal(
                            "§bQuest §7" + quest.progress() + "/" + quest.target()), true);
                }
            }
        });
    }

    /**
     * A quest has reached its target.
     *
     * <p>An INSTANT quest pays out here, exactly as every quest used to. An NPC-mode quest is held
     * open and marked ready instead: it is handed in by talking to its completer, and only then
     * pays. That is the one behavioural change in this work, and it is opt-in per quest.
     */
    private static void finish(ServerPlayer player, XenoPlayerData data, ActiveQuest quest) {
        ParallelQuests.QuestDef def = ParallelQuests.definition(quest.id());
        if (def != null && def.completionMode() == QuestCompletionMode.NPC) {
            quest.markReady();
            player.displayClientMessage(Component.literal(
                    "§bQuest ready: §f" + def.title() + " §7- complete with "
                            + def.completerNpc()), false);
            // Otherwise the log keeps showing it as in progress: a full bar that has not paid out
            // reads as a bug rather than as a quest waiting to be handed in.
            QuestSync.push(player);
            return;
        }
        completeQuest(player, data, quest.id());
    }

    /**
     * Hands in any ready quest this NPC is the completer for.
     *
     * <p>Called from the NPC's own interaction, before it says anything: an NPC that answered a
     * finished quest with an ambient line would look like it had not noticed.
     *
     * <p>Every check is server-side. The player does not say which quest - the server looks at what
     * they are actually holding, whether it is actually ready, and whether this NPC is actually its
     * completer.
     *
     * @return true when something was handed in, so the caller does not also speak
     */
    public static boolean tryHandIn(ServerPlayer player, String npcName, String roleId) {
        return tryHandIn(player, npcName, roleId, null);
    }

    public static boolean tryHandIn(ServerPlayer player, String npcName, String roleId,
                                    net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) {
        if (player == null || !XenoServerConfig.parallelQuestEnabled) {
            return false;
        }
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        for (ActiveQuest quest : data.quests().actives()) {
            if (!quest.ready()) {
                continue;
            }
            ParallelQuests.QuestDef def = ParallelQuests.definition(quest.id());
            if (def == null || def.completionMode() != QuestCompletionMode.NPC) {
                continue;
            }
            String completer = def.completerNpc();
            if (completer.isEmpty()
                    || !(completer.equalsIgnoreCase(npcName) || completer.equalsIgnoreCase(roleId))) {
                continue;
            }
            speakCompletion(player, npc, def);
            completeQuest(player, data, quest.id());
            return true;
        }
        return false;
    }

    /**
     * Ends one quest and pays what it is worth.
     *
     * <p>Takes the id explicitly. It used to read "the" active quest off the player, which only
     * worked while a player could hold exactly one.
     *
     * <p>The reward comes from the quest's own definition; {@code QuestReward.DEFAULT} is still the
     * long-standing two skill points, so a quest that declares nothing pays what it always did.
     */
    public static void completeQuest(ServerPlayer player, XenoPlayerData data, String id) {
        ParallelQuests.QuestDef def = ParallelQuests.definition(id);
        ActiveQuest activeQuest = data.quests().active(id);
        QuestReward reward = rewardToPay(def, activeQuest);
        QuestRuntime.takeItems(player, def);
        data.quests().complete(id, player.level().getGameTime());

        if (reward.skillPoints() > 0) {
            data.addSkillPoints(reward.skillPoints());
        }
        String paid = reward.grant(player, activeQuest);
        if (def != null && !def.mail().isEmpty()) {
            new QuestReward(0, 0, def.mail().items(), java.util.List.of()).grant(player);
        }

        String spoken = completionLine(def);
        net.bullettrain.xenopixelsmod.network.ModNetwork.sendToPlayer(player,
                new net.bullettrain.xenopixelsmod.network.packet.QuestCompletionPopupPacket(
                        def == null ? id : def.title(),
                        spoken.isBlank() ? (def == null ? "Quest complete" : def.desc()) : spoken,
                        paid,
                        def == null ? "blue" : def.completionPalette(),
                        def == null ? "rounded" : def.completionFrame()));
        if (!spoken.isEmpty()) {
            player.displayClientMessage(Component.literal("§f" + spoken), false);
        }
        player.displayClientMessage(Component.literal(
                "§a§lQuest complete: §f" + id + " §7(" + paid + ")"), false);
        // Only when this quest actually paid points. Printing the running total after every
        // completion read as a skill-point reward on quests whose toggle was off.
        if (reward.skillPoints() > 0) {
            player.displayClientMessage(Component.literal(
                    "§a+" + reward.skillPoints() + " XenoSkill points §7(total §f"
                            + data.getSkillPoints()
                            + "§7) - /xenoskill unlock <power|guard|sparking|ultimate>"), false);
        }
        // The log is now wrong on the client until it is told. A finished quest that stays on
        // screen reads as a quest that did not register. Faction points the reward just paid ride
        // the same push.
        QuestSync.push(player);
        QuestHunts.onQuestEnded(player, id);
        if (def != null && !def.nextQuest().isBlank()) {
            ParallelQuests.start(player, def.nextQuest());
        }
    }

    /**
     * Finishes {@code quest} when every objective is met, exactly as a kill or a visit would: an
     * INSTANT quest pays out, an NPC-mode one is marked ready. For script-set progress.
     */
    public static void finishIfDone(ServerPlayer player, XenoPlayerData data, ActiveQuest quest) {
        if (player == null || data == null || quest == null || quest.ready()) return;
        if (quest.allStepsDone(ParallelQuests.stepsFor(quest.id()).size(), targetsOf(quest.id()))) {
            finish(player, data, quest);
        } else {
            QuestSync.push(player);
        }
    }

    private static int[] targetsOf(String questId) {
        java.util.List<QuestStep> steps = ParallelQuests.stepsFor(questId);
        int[] required = new int[Math.max(1, steps.size())];
        for (int i = 0; i < steps.size(); i++) {
            required[i] = steps.get(i).target();
        }
        if (steps.isEmpty()) {
            ParallelQuests.QuestDef def = ParallelQuests.definition(questId);
            required[0] = def == null ? 1 : def.target();
        }
        return required;
    }

    /**
     * The reward a completion actually pays: what the quest declared, with the random pick applied
     * and the origin policy on top.
     *
     * <p>Package-private rather than private so {@code QuestRewardToggleTest} can drive the whole
     * decision - declared reward, random pick, origin policy - without a server. Nothing outside
     * this package calls it; every completion still arrives through {@link #completeQuest}.
     */
    static QuestReward rewardToPay(ParallelQuests.QuestDef def, ActiveQuest activeQuest) {
        QuestReward declared = def == null ? null : def.reward();
        if (def == null || !def.randomReward() || declared.items().size() <= 1) {
            return QuestRewardOriginPolicy.forCompletion(declared,
                    activeQuest != null && activeQuest.xenoNpcGiver());
        }
        QuestReward.ItemGrant picked = declared.items().get(
                java.util.concurrent.ThreadLocalRandom.current().nextInt(declared.items().size()));
        QuestReward selected = new QuestReward(declared.skillPoints(), declared.experience(),
                java.util.List.of(picked), declared.commands(), declared.factionPoints());
        return QuestRewardOriginPolicy.forCompletion(selected,
                activeQuest != null && activeQuest.xenoNpcGiver());
    }

    private static String completionLine(ParallelQuests.QuestDef def) {
        if (def == null) return "";
        String line = def.completeText();
        String mail = def.mail().spoken();
        if (mail.isEmpty()) return line;
        return line.isEmpty() ? mail : line + " " + mail;
    }

    private static void speakCompletion(ServerPlayer player,
                                        net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc,
                                        ParallelQuests.QuestDef def) {
        if (player == null || npc == null || def == null || def.completeText().isBlank()) {
            return;
        }
        var nodes = new java.util.LinkedHashMap<String,
                net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue.Node>();
        nodes.put("done", new net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue.Node(
                completionLine(def), java.util.List.of(
                new net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue.Option(
                        "Done", net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue.OptionType.QUIT,
                        "", "", ""))));
        net.bullettrain.xenopixelsmod.network.ModNetwork.sendToPlayer(player,
                new net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket(
                        npc.getId(), "quest_complete", npc.getName().getString(),
                        new net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue("done", nodes)));
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
