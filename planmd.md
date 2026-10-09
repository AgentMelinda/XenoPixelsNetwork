
  # XenoNPC Quest Parity and Bubble Layout

  ## Summary

  Extend native XenoNPC quests and the MyNPCs importer, connect imported quests to imported dialogues, and correct
  bubble placement using Image #2 as the reference. Remove the sample content assigned when an NPC receives the Quest
  role.

  ## Implementation

  - Add native quest objectives and progress tracking for MyNPCs item, dialogue choice, kill, location, area kill,
    and manual quests. Support multiple targets, repeat schedules, NPC or instant completion, rewards, and next quest
    links. Preserve existing quest saves and definitions.

  - Extend /xenonpcimport all to map MyNPCs quest and dialogue IDs together. Import only behavior the native runtime
    can execute; report every unsupported or unresolved field. Keep existing Xeno entries unchanged on ID conflicts.

  - Make imported dialogue quest actions offer or complete the mapped native quest through server validation. Add
    global quest authoring so operators can create quests and link them to NPC dialogue choices.

  - Place dialogue bubbles around chest or waist height as in Image #2, with readable text. Keep each speaker’s
    bubbles grouped, prevent nearby conversations from overlapping, and make choice hit areas follow their rendered
    positions. Correct color formatting and use yellow/orange for clickable choices while retaining the palette
    picker and colored text.

  - Remove the Quest role’s automatic sample dialogue and dangling default quest reference. Assigning the role alone
    will create no quest or conversation; explicitly assigned content will still work.

  ## Verification

  - Add focused tests for quest progress, repeat and reward rules, import ID mapping, dialogue quest actions, colored
    text, bubble placement, and choice hit areas.

  - Run the focused tests, ./gradlew test, and the repository’s distribution and API build checks. Use the supplied
    screenshots for visual acceptance; do not control the game or capture new in-game screenshots.

  ## Assumptions

  - Image #2 specifies the lower bubble position, while its empty text is an issue to fix.
  - Existing player quest progress and authored NPC assignments must remain intact.
  - Imported commands, mail, or other source behavior will be enabled only after their exact MyNPCs 1.5.0 semantics
    and native execution path are verified; omissions will appear in the import report.


  Worked for 3m 53s · 12:54 PM


› Implement the plan.


■ {"detail":"Bad Request"}


› Implement the plan.


• I’ll implement the approved plan in stages: quest data and runtime first, then importer and dialogue links, then the
  bubble layout and Quest role defaults. I’ll keep the dirty working tree intact and verify changes with focused tests
  and builds.

• Ran git status --short | Measure-Object -Line | Select-Object -ExpandProperty Lines
  └ 679

• Ran Get-Content src/main/java/net/bullettrain/xenopixelsmod/features/progression/QuestBook.java | Select-Object
  │ -First 260; Get-Content src/main/java/net/bullettrain/xenopixelsmod/features/progression/ProgressionEvents.java |
  │ Select-Object -First 320
  └ package net.bullettrain.xenopixelsmod.features.progression;

    import net.minecraft.nbt.CompoundTag;
    +465 lines (ctrl+t to view transcript)

• Ran Get-Content src/main/java/net/bullettrain/xenopixelsmod/features/progression/XenoQuests.java | Select-Object
  │ -First 260; Get-Content src/main/java/net/bullettrain/xenopixelsmod/npc/dialog/XenoDialogueRuntime.java |
  │ Select-Object -First 300
  └ package net.bullettrain.xenopixelsmod.features.progression;

    import com.google.gson.Gson;
    +196 lines (ctrl+t to view transcript)

• Ran jar tf run/mods/mynpcs-neoforge-1.5.0.jar | rg 'quests/Quest|EnumQuestRepeat|DialogOption|QuestType' |
  │ Select-Object -First 90
  └ espi/mynpcs/api/constants/QuestType.class
    espi/mynpcs/api/handler/data/IDialogOption.class
    espi/mynpcs/client/gui/global/SubGuiNpcDialogOption.class
    +20 lines (ctrl+t to view transcript)

• Ran rg -n 'Quest|QUEST' src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorScreen.java |
  │ Select-Object -First 100; rg -n 'case QUEST|OptionType.QUEST|option.quest' src/main/java/net/bullettrain/
  │ xenopixelsmod | Select-Object -First 80
  └ 351:    private static final int SLOT_CLEAR_REQUEST = 18;
    356:        return XenoAtlasSprites.rowWithin(SLOT_CLEAR_REQUEST);
    623:            case GLOBAL_QUESTS -> unavailableListRows("Quests");
    +26 lines (ctrl+t to view transcript)

• Ran javap -classpath run/mods/mynpcs-neoforge-1.5.0.jar -private espi.mynpcs.api.constants.QuestType
  │ espi.mynpcs.constants.EnumQuestRepeat espi.mynpcs.controllers.data.DialogOption espi.mynpcs.quests.QuestDialog
  │ espi.mynpcs.quests.QuestKill
  └ Compiled from "QuestType.java"
    public class espi.mynpcs.api.constants.QuestType {
      public static final int ITEM;
    +66 lines (ctrl+t to view transcript)

• Ran Get-Content src/main/java/net/bullettrain/xenopixelsmod/network/packet/XenoNpcDialoguePacket.java | Select-Object
  │ -First 180; Get-Content src/main/java/net/bullettrain/xenopixelsmod/npc/importer/NpcImportCommands.java |
  │ Select-Object -First 160
  └ package net.bullettrain.xenopixelsmod.network.packet;

    import com.dragonminez.compat.network.NetworkEvent;
    +135 lines (ctrl+t to view transcript)

• Ran $p='run/saves/New World (9)/mynpcs/quests'; Get-ChildItem -LiteralPath $p -Recurse -Filter *.json |
  │ ForEach-Object { $s=Get-Content -LiteralPath $_.FullName -Raw; $m=[regex]::Match($s,'"Type"\s*:\s*(-?\d+)');
  │ [PSCustomObject]@{File=$_.Name; Group=$_.Directory.Name; Type=if($m.Success){$m.Groups[1].Value}else{'?'};
  │ … +1 line
  └
    File    Group                                    Type Length
    ----    -----                                    ---- ------
    +14 lines (ctrl+t to view transcript)

• Ran $p='run/saves/New World (9)/mynpcs/quests'; Get-ChildItem -LiteralPath $p -Recurse -Filter *.json |
  │ ForEach-Object { $s=Get-Content -LiteralPath $_.FullName -Raw; $t=[regex]::Matches($s,'"Slot"\s*:\s*"([^"]+)"\s*,
  │ \s*"Value"\s*:\s*(-?\d+)'); $r=[regex]::Match($s,'"QuestRepeat"\s*:\s*(-?\d+)');
  │ … +3 lines
  └
    File    Targets Repeat DialogLen
    ----    ------- ------ ---------
    +14 lines (ctrl+t to view transcript)

• Ran $f=Get-ChildItem -LiteralPath 'run/saves/New World (9)/mynpcs/quests' -Recurse -Filter 1.json | Select-Object
  │ -First 1; $s=Get-Content -LiteralPath $f.FullName -Raw; $m=[regex]::Match($s,'"QuestDialogs"\s*:\s*\[(.*?)\]',
  │ [System.Text.RegularExpressions.RegexOptions]::Singleline); $m.Value
  └ "QuestDialogs": [
            {
                "Value": 1,
    +3 lines (ctrl+t to view transcript)

• Ran rg -n 'bubbleHeight|HEIGHT_ABOVE_ENTITY' src/main/java/net/bullettrain/xenopixelsmod/compat/npc/
  │ NpcCombatProfile.java src/main/java/net/bullettrain/xenopixelsmod/client/npc/XenoNpcEditorScreen.java src/main/
  │ java/net/bullettrain/xenopixelsmod/client/npc/speech/SpeechBubbleRenderer.java | Select-Object -First 90
  └ src/main/java/net/bullettrain/xenopixelsmod/client/npc/speech/SpeechBubbleRenderer.java:59:    private static final
    float HEIGHT_ABOVE_ENTITY = 0.7f;
    src/main/java/net/bullettrain/xenopixelsmod/client/npc/speech/SpeechBubbleRenderer.java:177:            return
    +11 lines (ctrl+t to view transcript)

• Ran Get-Content src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcCombatProfile.java | Select-Object -Skip
  │ 217 -First 14; Get-Content src/main/java/net/bullettrain/xenopixelsmod/compat/npc/NpcCombatProfile.java |
  │ Select-Object -Skip 735 -First 20
  └     private static final String TAG_BARD_HAS_OFF = "BardHasOffDistance";
        private static final String TAG_BARD_OFF = "BardOffDistance";
        private static final String TAG_JOB = "Job";
    +31 lines (ctrl+t to view transcript)

• Ran javap -classpath run/mods/mynpcs-neoforge-1.5.0.jar -c -private espi.mynpcs.controllers.data.Dialog |
  │ Select-String -Pattern 'quest|Quest' -Context 4,6 | Select-Object -First 65
  └
        public java.lang.String title;

    +142 lines (ctrl+t to view transcript)

• Edited src\main\resources\data\xenopixelsmod\xeno_npcs\roles\quest.json (+0 -2)
    11    "refs": {
    12 -    "dialogue": "xenopixelsmod:npcs/dialogue/default",
    13 -    "quest": "xenopixelsmod:npcs/quests/default",
    12      "lines": "xenopixelsmod:npcs/lines/default"

• Ran javap -classpath run/mods/mynpcs-neoforge-1.5.0.jar -c -private espi.mynpcs.constants.EnumQuestRepeat |
  │ Select-Object -Last 100
  └
      public static final espi.mynpcs.constants.EnumQuestRepeat RLWEEKLY;

    +97 lines (ctrl+t to view transcript)

• The repository confirms a narrow importer gap: all 13 quest files in the available MyNPCs world use kill objectives,
  but the current conversion drops their repeat schedules and dialogue quest links. I’m adding those executable pieces
  first, then I’ll address the broader quest types against the exact MyNPCs jar.

• Edited 5 files (+86 -8)
  └ src\main\java\net\bullettrain\xenopixelsmod\features\progression\ParallelQuests.java (+16 -1)
     42                             String category, String logText, String completeText,
     43 -                           QuestCompletionMode completionMode, String completerNpc) {
     43 +                           QuestCompletionMode completionMode, String completerNpc,
     44 +                           QuestRepeat repeat) {
     45          public QuestDef {
        ⋮
     51              completerNpc = completerNpc == null ? "" : completerNpc.trim();
     52 +            repeat = repeat == null ? QuestRepeat.REPEATABLE : repeat;
     53              // A quest that asks to be handed in but names nobody would sit at full progress
        ⋮
     59
     60 +        public QuestDef(String id, String title, String desc, int target,
     61 +                        QuestObjective.Goal goal, QuestReward reward,
     62 +                        String category, String logText, String completeText,
     63 +                        QuestCompletionMode completionMode, String completerNpc) {
     64 +            this(id, title, desc, target, goal, reward, category, logText, completeText,
     65 +                    completionMode, completerNpc, QuestRepeat.REPEATABLE);
     66 +        }
     67 +
     68          /** A quest that pays the long-standing default and counts any hostile kill. */
        ⋮
    136          if (data == null) return "No player data";
    137 +        if (data.quests().hasCompleted(def.id()) && !def.repeat().canRestart(
    138 +                data.quests().completedAt(def.id()), data.quests().completedAtReal(def.id()),
    139 +                player.level().getGameTime(), System.currentTimeMillis())) {
    140 +            return "That quest cannot be repeated yet";
    141 +        }
    142          String refusal = data.quests().start(def.id(), def.target());

  └ src\main\java\net\bullettrain\xenopixelsmod\features\progression\QuestBook.java (+17 -1)
     37      private final Map<String, Long> completed = new LinkedHashMap<>();
     38 +    private final Map<String, Long> completedReal = new LinkedHashMap<>();
     39
        ⋮
     87      public void complete(String id, long gameTime) {
     88 +        complete(id, gameTime, System.currentTimeMillis());
     89 +    }
     90 +
     91 +    public void complete(String id, long gameTime, long epochMillis) {
     92          String wanted = key(id);
        ⋮
     99          completed.put(wanted, gameTime);
    100 +        completedReal.put(wanted, epochMillis);
    101          Iterator<String> oldest = completed.keySet().iterator();
    102          while (completed.size() > MAX_COMPLETED && oldest.hasNext()) {
     97 -            oldest.next();
    103 +            completedReal.remove(oldest.next());
    104              oldest.remove();
        ⋮
    116
    117 +    public long completedAtReal(String id) {
    118 +        return completedReal.getOrDefault(key(id), 0L);
    119 +    }
    120 +
    121      /** Completed ids, oldest first. */
        ⋮
    135          tag.put("CompletedQuests", done);
    136 +        CompoundTag real = new CompoundTag();
    137 +        completedReal.forEach(real::putLong);
    138 +        tag.put("CompletedQuestsReal", real);
    139      }
        ⋮
    144          completed.clear();
    145 +        completedReal.clear();
    146          if (tag == null) {
        ⋮
    156          CompoundTag done = tag.getCompound("CompletedQuests");
    157 +        CompoundTag real = tag.getCompound("CompletedQuestsReal");
    158          List<String> keys = new ArrayList<>(done.getAllKeys());
        ⋮
    160              completed.put(keys.get(i), done.getLong(keys.get(i)));
    161 +            if (real.contains(keys.get(i))) completedReal.put(keys.get(i), real.getLong(keys.get(i)));
    162          }

  └ src\main\java\net\bullettrain\xenopixelsmod\features\progression\QuestRepeat.java (+39 -0)
     1 +package net.bullettrain.xenopixelsmod.features.progression;
     2 +
     3 +import java.util.Locale;
     4 +
     5 +/** Repeat rules stored with a native quest definition. */
     6 +public enum QuestRepeat {
     7 +    NONE, REPEATABLE, MCDAILY, MCWEEKLY, RLDAILY, RLWEEKLY;
     8 +
     9 +    public static QuestRepeat parse(String value) {
    10 +        if (value == null || value.isBlank()) return REPEATABLE;
    11 +        try {
    12 +            return valueOf(value.trim().toUpperCase(Locale.ROOT));
    13 +        } catch (IllegalArgumentException exception) {
    14 +            throw new IllegalArgumentException("unknown quest repeat rule '" + value + "'");
    15 +        }
    16 +    }
    17 +
    18 +    /** MyNPCs 1.5.0 EnumQuestRepeat ordinal order, verified against the installed jar. */
    19 +    public static QuestRepeat fromMyNpcs(int ordinal) {
    20 +        if (ordinal < 0 || ordinal >= values().length) {
    21 +            throw new IllegalArgumentException("unknown MyNPCs repeat rule " + ordinal);
    22 +        }
    23 +        return values()[ordinal];
    24 +    }
    25 +
    26 +    public boolean canRestart(long lastGameTime, long lastEpochMillis,
    27 +                              long nowGameTime, long nowEpochMillis) {
    28 +        return switch (this) {
    29 +            case NONE -> false;
    30 +            case REPEATABLE -> true;
    31 +            case MCDAILY -> nowGameTime / 24000L > lastGameTime / 24000L;
    32 +            case MCWEEKLY -> nowGameTime / 168000L > lastGameTime / 168000L;
    33 +            case RLDAILY -> lastEpochMillis > 0 &&
    34 +                    Math.floorDiv(nowEpochMillis, 86_400_000L) > Math.floorDiv(lastEpochMillis, 86_400_000L);
    35 +            case RLWEEKLY -> lastEpochMillis > 0 &&
    36 +                    Math.floorDiv(nowEpochMillis, 604_800_000L) > Math.floorDiv(lastEpochMillis, 604_800_000L);
    37 +        };
    38 +    }
    39 +}

  └ src\main\java\net\bullettrain\xenopixelsmod\features\progression\XenoQuests.java (+3 -1)
    161                  root.has("completion") ? root.get("completion").getAsString() : null);
    162 +        QuestRepeat repeat = QuestRepeat.parse(root.has("repeat")
    163 +                ? root.get("repeat").getAsString() : null);
    164
        ⋮
    166                  QuestReward.fromJson(root.get("reward")),
    165 -                category, logText, completeText, completion, completerNpc);
    167 +                category, logText, completeText, completion, completerNpc, repeat);
    168      }

  └ src\main\java\net\bullettrain\xenopixelsmod\npc\importer\QuestImport.java (+11 -5)
      5  import net.bullettrain.xenopixelsmod.features.progression.QuestReward;
      6 +import net.bullettrain.xenopixelsmod.features.progression.QuestRepeat;
      7  import net.bullettrain.xenopixelsmod.features.progression.ParallelQuests;
        ⋮
     32          root.addProperty("completer_npc", quest.completerNpc());
     33 +        root.addProperty("repeat", quest.repeat().name());
     34          com.google.gson.JsonObject reward = new com.google.gson.JsonObject();
        ⋮
     86          int exp = Math.max(0, Math.min(32767, source.getInt("RewardExp")));
     87 +        QuestRepeat repeat;
     88 +        try {
     89 +            repeat = QuestRepeat.fromMyNpcs(source.getInt("QuestRepeat"));
     90 +        } catch (IllegalArgumentException exception) {
     91 +            report.failed("quests", group + "/" + slot, exception.getMessage());
     92 +            return null;
     93 +        }
     94          QuestReward reward = new QuestReward(0, exp, List.of(), List.of(), List.of());
        ⋮
    107                      + " item and mail rewards were not converted; Xeno item reward mapping is not verified");
     99 -        }
    100 -        if (source.contains("QuestRepeat")) {
    101 -            report.note("quest " + group + "/" + slot
    102 -                    + " repeat schedule was not converted; Xeno quests do not yet support repeat rules");
    108          }
        ⋮
    128                  Math.max(1, Math.min(32767, target)), new QuestObjective.Goal(objective, parameter),
    124 -                reward, group, description, source.getString("CompleteText"), mode, completer);
    129 +                reward, group, description, source.getString("CompleteText"), mode, completer,
    130 +                repeat);
    131          return new Imported(id, quest);

■ {"detail":"Bad Request"}


› continue


• Edited 3 files (+37 -9)
  └ src\main\java\net\bullettrain\xenopixelsmod\npc\importer\DialogTreeImport.java (+25 -7)
     27                                       NpcImportReport report, String group) {
     28 +        return convert(source, report, group, "mynpcs");
     29 +    }
     30 +
     31 +    public static Conversion convert(Map<Integer, CompoundTag> source,
     32 +                                     NpcImportReport report, String group, String sourceMod) {
     33          if (source == null || source.isEmpty()) return new Conversion(List.of(), Map.of());
        ⋮
     44              Set<Integer> emitted = new LinkedHashSet<>();
     40 -            collect(root, source, nodeIds, nodes, emitted, report, group);
     45 +            collect(root, source, nodeIds, nodes, emitted, report, group, sourceMod);
     46              if (!nodes.isEmpty()) {
        ⋮
     62                                  Map<Integer, String> nodeIds, Map<String, XenoDialogue.Node> nodes,
     58 -                                Set<Integer> emitted, NpcImportReport report, String group) {
     63 +                                Set<Integer> emitted, NpcImportReport report, String group,
     64 +                                String sourceMod) {
     65          if (emitted.contains(slot)) return;
        ⋮
     78                      + " has a source command; it was omitted for server-command safety");
     73 -        }
     74 -        if (raw.getInt("DialogQuest") >= 0) {
     75 -            report.note("dialog " + group + "/" + slot
     76 -                    + " has a source quest action; it was omitted because source quest-link semantics are not
         verified");
     79          }
        ⋮
    112                              targetId, "", ""));
    111 -                    collect(target, source, nodeIds, nodes, emitted, report, group);
    113 +                    collect(target, source, nodeIds, nodes, emitted, report, group, sourceMod);
    114                  }
        ⋮
    121                  + " has options beyond Xeno's 16-option limit");
    122 +        if (raw.contains("DialogQuest") && raw.getInt("DialogQuest") >= 0) {
    123 +            int sourceQuest = raw.getInt("DialogQuest");
    124 +            String quest = report.quest(sourceMod, sourceQuest);
    125 +            if (quest == null) {
    126 +                report.note("dialog " + group + "/" + slot
    127 +                        + " references quest slot " + sourceQuest + " which was not imported");
    128 +            } else if (options.size() >= 16) {
    129 +                report.note("dialog " + group + "/" + slot
    130 +                        + " cannot show quest " + quest + ": option limit reached");
    131 +            } else {
    132 +                options.add(new XenoDialogue.Option("Accept quest", XenoDialogue.OptionType.QUEST,
    133 +                        "", quest, ""));
    134 +                report.note("dialog " + group + "/" + slot
    135 +                        + " source quest action became an explicit Accept quest choice");
    136 +            }
    137 +        }
    138          nodes.put(nodeIds.get(slot), new XenoDialogue.Node(text, List.copyOf(options)));

  └ src\main\java\net\bullettrain\xenopixelsmod\npc\importer\NpcImportReport.java (+9 -0)
    31      private final java.util.Map<String, String> factionRefs = new java.util.LinkedHashMap<>();
    32 +    private final java.util.Map<String, String> questRefs = new java.util.LinkedHashMap<>();
    33
       ⋮
    67
    68 +    public void mapQuest(String mod, int sourceSlot, String targetId) {
    69 +        if (mod != null && targetId != null) questRefs.put(mod + ":" + sourceSlot, targetId);
    70 +    }
    71 +
    72 +    public String quest(String mod, int sourceSlot) {
    73 +        return questRefs.get(mod + ":" + sourceSlot);
    74 +    }
    75 +
    76      /**

  └ src\main\java\net\bullettrain\xenopixelsmod\npc\importer\NpcImportService.java (+3 -2)
     62              if (libraries) {
     63 +                importGrouped(root, "quests", store, dryRun, report);
     64                  importGrouped(root, "dialogs", store, dryRun, report);
     64 -                importGrouped(root, "quests", store, dryRun, report);
     65              }
        ⋮
    145                  DialogTreeImport.Conversion conversion = DialogTreeImport.convert(
    146 -                        group.getValue(), report, group.getKey());
    146 +                        group.getValue(), report, group.getKey(), root.mod());
    147                  for (Map.Entry<Integer, DialogTreeImport.Ref> mapping : conversion.sourceSlots().entrySet()) {
        ⋮
    163                  if (quest == null) continue;
    164 +                report.mapQuest(root.mod(), slot, id);
    165                  CompoundTag tag = new CompoundTag();

■ {"detail":"Bad Request"}

• Model changed to gpt-6-luna high


› continue


• Edited src\main\java\net\bullettrain\xenopixelsmod\client\npc\dialog\DialogueBubbleRenderer.java (+65 -34)
     49
     50 -    /** World units above the head. Clear of a mark, which sits at 1.05. */
     51 -    private static final float HEIGHT_ABOVE_ENTITY = 1.35f;
     50 +    /** The dialogue tail anchor as a fraction of the NPC's body height. */
     51 +    private static final float HEIGHT_ABOVE_ENTITY = 0.62f;
     52
        ⋮
    259          List<XenoDialogue.Option> options = session.options();
    260 -        boolean sideChoices = options.size() >= 3;
    260 +        boolean rowChoices = options.size() >= 3;
    261          XenoAtlasSprites.Sprite lineSprite = XenoAtlasSprites.get(
        ⋮
    264          float optionStackHeight = 0.0f;
    265 -        int widestOption = 0;
    265          for (int i = 0; i < options.size(); i++) {
    267 -            if (i > 0) optionStackHeight += DialogueBubbleLayout.GAP_BETWEEN;
    266              XenoAtlasSprites.Sprite sprite = optionSprite(session.resolve(options.get(i).text()), optionTheme)
         ;
    269 -            optionStackHeight += sprite.height();
    270 -            widestOption = Math.max(widestOption, sprite.width());
    267 +            if (rowChoices) {
    268 +                if (i % 3 == 0) optionStackHeight += sprite.height() + DialogueBubbleLayout.GAP_BETWEEN;
    269 +            } else {
    270 +                if (i > 0) optionStackHeight += DialogueBubbleLayout.GAP_BETWEEN;
    271 +                optionStackHeight += sprite.height();
    272 +            }
    273          }
        ⋮
    277          Vec3 pos = entity.getPosition(partialTick);
    276 -        float sideX = 0.0f;
    277 -        if (sideChoices) {
    278 -            WorldToScreenCache.ScreenPoint head = WorldToScreenCache.project(
    279 -                    new Vec3(pos.x, pos.y + npc.getBbHeight() + HEIGHT_ABOVE_ENTITY, pos.z),
    280 -                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    281 -            int direction = head != null && head.x() > mc.getWindow().getGuiScaledWidth() / 2.0f
    282 -                    ? -1 : 1;
    283 -            sideX = direction * (lineSprite.width() / 2.0f + widestOption / 2.0f + 10.0f);
    284 -        }
    278          MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ⋮
    292              poseStack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
    300 -            // The line and all choices must finish above the NPC, even with several options.
    301 -            poseStack.translate(0.0,
    302 -                    npc.getBbHeight() + HEIGHT_ABOVE_ENTITY
    303 -                            + (sideChoices ? 0.0f : optionStackHeight * BUBBLE_SCALE),
    304 -                    0.0);
    293 +            // Keep the line close to the speaker. Choices flow below it instead of pushing the
    294 +            // entire stack above the head or drifting to one side of the NPC.
    295 +            poseStack.translate(0.0, npc.getBbHeight() * HEIGHT_ABOVE_ENTITY, 0.0);
    296              poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ⋮
    300              float cursorY = drawLineBubble(poseStack, buffers, session.text(), lineTheme);
    310 -            cursorY += sideChoices ? -lineSprite.height() : DialogueBubbleLayout.GAP_BELOW_LINE;
    301 +            cursorY += DialogueBubbleLayout.GAP_BELOW_LINE;
    302
    303              for (int i = 0; i < options.size(); i++) {
    313 -                if (i > 0) {
    314 -                    cursorY += DialogueBubbleLayout.GAP_BETWEEN;
    315 -                }
    304                  // The sprite is resolved once and shared: the box has to describe the art that was
        ⋮
    307                  XenoAtlasSprites.Sprite sprite = optionSprite(optionText, optionTheme);
    320 -                drawOption(poseStack, buffers, optionText, cursorY, sprite, sideX);
    308 +                float optionX = rowChoices ? rowOffsetX(options, i, mc.font, optionTheme) : 0.0f;
    309 +                float optionY = rowChoices
    310 +                        ? rowOffsetY(options, i, mc.font, optionTheme)
    311 +                        : cursorY;
    312 +                drawOption(poseStack, buffers, optionText, optionY, sprite, optionX);
    313                  found.add(hitFor(i, pos, npc, cursorY, sprite,
    322 -                        sideChoices ? 0.0f : optionStackHeight, sideX));
    323 -                cursorY += sprite.height();
    314 +                        0.0f, optionX, optionY));
    315 +                if (!rowChoices) {
    316 +                    cursorY += sprite.height();
    317 +                    if (i + 1 < options.size()) cursorY += DialogueBubbleLayout.GAP_BETWEEN;
    318 +                }
    319              }
        ⋮
    329
    330 +    private static float rowOffsetX(List<XenoDialogue.Option> options, int index,
    331 +                                    net.minecraft.client.gui.Font font,
    332 +                                    XenoAtlasSprites.Theme theme) {
    333 +        int rowStart = index / 3 * 3;
    334 +        int rowCount = Math.min(3, options.size() - rowStart);
    335 +        float total = 0;
    336 +        float[] widths = new float[rowCount];
    337 +        for (int column = 0; column < rowCount; column++) {
    338 +            String text = DialogueBubbleSession.active().resolve(options.get(rowStart + column).text());
    339 +            XenoAtlasSprites.Sprite sprite = optionSprite(text, theme);
    340 +            widths[column] = sprite.width();
    341 +            total += widths[column];
    342 +            if (column > 0) total += DialogueBubbleLayout.GAP_BETWEEN;
    343 +        }
    344 +        float left = -total / 2.0f;
    345 +        for (int column = 0; column < index % 3; column++) left += widths[column] + DialogueBubbleLayout.GAP_B
         ETWEEN;
    346 +        return left + widths[index % 3] / 2.0f;
    347 +    }
    348 +
    349 +    private static float rowOffsetY(List<XenoDialogue.Option> options, int index,
    350 +                                    net.minecraft.client.gui.Font font,
    351 +                                    XenoAtlasSprites.Theme theme) {
    352 +        float y = DialogueBubbleLayout.GAP_BELOW_LINE;
    353 +        int row = index / 3;
    354 +        for (int current = 0; current < row; current++) {
    355 +            int start = current * 3;
    356 +            int end = Math.min(options.size(), start + 3);
    357 +            int height = 0;
    358 +            for (int i = start; i < end; i++) {
    359 +                String text = DialogueBubbleSession.active().resolve(options.get(i).text());
    360 +                height = Math.max(height, optionSprite(text, theme).height());
    361 +            }
    362 +            y += height + DialogueBubbleLayout.GAP_BETWEEN;
    363 +        }
    364 +        return y;
    365 +    }
    366 +
    367      /**
        ⋮
    446       */
    415 -    private static OptionHit hitFor(int index, Vec3 base, LivingEntity npc, float localTop,
    447 +    private static OptionHit hitFor(int index, Vec3 base, LivingEntity npc, float ignoredStackHeight,
    448                                      XenoAtlasSprites.Sprite sprite, float optionStackHeight,
    417 -                                    float centreX) {
    418 -        double anchorY = base.y + npc.getBbHeight() + HEIGHT_ABOVE_ENTITY
    419 -                + optionStackHeight * BUBBLE_SCALE;
    449 +                                    float centreX, float actualTop) {
    450 +        double anchorY = base.y + npc.getBbHeight() * HEIGHT_ABOVE_ENTITY;
    451          // Local Y grows downward (the pose stack negates it), so the top edge is the smaller
    452          // local offset and therefore the higher world position.
    422 -        double worldTop = anchorY - localTop * BUBBLE_SCALE;
    423 -        double worldBottom = anchorY - (localTop + sprite.height()) * BUBBLE_SCALE;
    453 +        double worldTop = anchorY - actualTop * BUBBLE_SCALE;
    454 +        double worldBottom = anchorY - (actualTop + sprite.height()) * BUBBLE_SCALE;
    455          Vector3f side = new Vector3f(centreX * BUBBLE_SCALE, 0.0f, 0.0f)


› Implement the plan.


• Ran $f=Get-ChildItem -LiteralPath 'run/saves/New World (9)/mynpcs/quests' -Recurse -Filter 1.json | Select-Object
  │ -First 1; $s=Get-Content -LiteralPath $f.FullName -Raw; foreach($key in
  │ @('Rewards','QuestFactionPoints','QuestCommand','RandomReward','QuestRepeat','QuestCompletion'))
  │ … +2 lines
  └ Rewards: "Rewards": {
            "NpcMiscInv": [
            ]
    +13 lines (ctrl+t to view transcript)

■ {"detail":"Bad Request"}
