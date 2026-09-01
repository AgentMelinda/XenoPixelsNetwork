// Stepwise SSJ1 -> SSJ2 -> SSJ3 and back down. Values are read per NPC.
var GROUP = "supersaiyan";
var FORMS = ["supersaiyanmastered", "supersaiyan2", "supersaiyan3"];

function init(event) {
    var data = event.npc.getStoreddata();
    if (!data.has("xeno_ssj_level")) data.put("xeno_ssj_level", 0);
    for (var i = 0; i < FORMS.length; i++)
        XenoPixels.setMastery(event.npc, GROUP, FORMS[i], 100);
}

function ascend(event) {
    var data = event.npc.getStoreddata();
    var level = Math.min(FORMS.length, Number(data.get("xeno_ssj_level")) + 1);
    if (level > 0 && XenoPixels.ascend(event.npc, GROUP, FORMS[level - 1], 40))
        data.put("xeno_ssj_level", level);
}

function descend(event) {
    var data = event.npc.getStoreddata();
    var level = Math.max(0, Number(data.get("xeno_ssj_level")) - 1);
    if (level == 0) XenoPixels.descend(event.npc, 20);
    else XenoPixels.ascend(event.npc, GROUP, FORMS[level - 1], 20);
    data.put("xeno_ssj_level", level);
}
