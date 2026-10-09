# Xeno NPC parity status — 2026-09-24

This document tracks code-level and test evidence for parity with MyNPCs 1.5.0. A passing test or build is not proof of in-game behavior.

## Quest role and dialogue workflow

- **Source behavior:** The Xeno Quest role is passive/non-combat; it does not automatically assign quests. Quest offers are authored as `QUEST` choices in an NPC-owned or shared dialogue, displayed through Xeno's speech-bubble interaction. NPC hand-in remains controlled by the quest's configured completer.
- **Advanced authoring:** Advanced > Dialogs supports NPC-owned dialogue and assigned shared-dialog slots, with an entry to the existing Global > Dialogs store flow for creating/editing shared dialogue. The shared authoring path uses the existing world-store `DIALOGS` schema and store write/revision validation.
- **Option identity:** Quest availability filtering retains each option's canonical server index. The dialogue action packet resolves the current server-owned dialogue and rechecks the selected option and quest availability rather than treating the client dialogue as authoritative.
- **Tests:** `AdvancedDialogueAuthoringTest` checks the existing DIALOGS world-store schema and shared-slot assignment; `XenoNpcRoleBehaviourTest` checks passive role guidance and authoring access; `QuestDialogueFilterTest`, `XenoDialogueQuestOptionTest`, `DialogueBubbleTest`, and `OpenXenoNpcDialoguePacketTest` cover filtered source indexes, server-side re-resolution/availability checks, and wire round-tripping. `QuestLogServerTest` covers configured NPC hand-in. These establish source/unit coverage only.
- **Advanced control audit:** `Job Enabled` remains disabled because jobs run from the selected `Job` field and the profile/save schema has no separate enabled key. `NPC Interact Lines` remains disabled because there is no NPC-to-NPC line runtime consumer. No other nearby unsupported control was enabled.
- **Fresh runtime verification:** Not verified in this work session. A fresh client/server exercise is still required to create a shared dialogue from Advanced, assign it to a Quest-role NPC, accept a quest from the bubble, and complete an NPC hand-in; also test a hidden earlier option followed by a visible quest option.

## Remaining MyNPCs differences

No claim of full 1:1 parity is made here. Features requiring unsupported server schema/runtime behavior remain out of scope until verified against the pinned MyNPCs reference and Xeno server implementation.
