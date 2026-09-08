/**
 * Individual CustomNPCs NPC script: award DragonMineZ points for one normal quest.
 *
 * Paste this entire script into the quest-giver NPC's Script tab.
 * It does not use CustomNPCs command rewards.
 */
"use strict";

// Change these two values for this NPC.
var QUEST_ID = 1;
var POINT_REWARD = 100;

// true: reward after the quest is turned in.
// false: reward as soon as every quest objective is complete.
var PAY_ON_TURN_IN = true;

function tick(event) {
    var players = event.npc.getWorld().getAllPlayers();
    for (var i = 0; i < players.length; i++) {
        checkQuest(event, players[i]);
    }
}

function interact(event) {
    checkQuest(event, event.player);
}

function checkQuest(event, player) {
    if (player == null || typeof XenoPixels === "undefined") return;

    var data = player.getStoreddata();
    var keyBase = "xeno_quest_points_" + event.npc.getUUID() + "_" + QUEST_ID;
    var activeKey = keyBase + "_active";
    var paidKey = keyBase + "_paid";
    var active = player.hasActiveQuest(QUEST_ID);
    var finished = player.hasFinishedQuest(QUEST_ID);

    // Seeing the quest active marks a new attempt. This also re-arms repeatable quests after the
    // previous turn-in paid successfully.
    if (active && !data.has(activeKey)) {
        data.put(activeKey, 1);
        data.remove(paidKey);
    }

    var ready = PAY_ON_TURN_IN
            ? finished && !active
            : finished || objectivesComplete(event, player);

    if (ready && !data.has(paidKey)) {
        if (XenoPixels.addDmzPoints(player, POINT_REWARD)) {
            data.put(paidKey, 1);
            player.message("Quest reward: +" + POINT_REWARD + " DragonMineZ points.");
        } else {
            player.message("DragonMineZ points could not be awarded.");
        }
    }

    if (!active) {
        data.remove(activeKey);
    }
}

function objectivesComplete(event, player) {
    var quest = event.API.getQuests().get(QUEST_ID);
    if (quest == null) return false;

    var objectives = quest.getObjectives(player);
    if (objectives == null || objectives.length == 0) return false;

    for (var i = 0; i < objectives.length; i++) {
        if (!objectives[i].isCompleted()) return false;
    }
    return true;
}
