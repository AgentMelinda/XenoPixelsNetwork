# Angel trainer offerings evidence (PR-D5)

**Date:** 2026-10-03  
**Scope:** Evidence gate for angel players as constrained gods-form trainers. No new DMZ unlock
APIs. Cite existing Form Editor / metadata / purchase apply path before any wire.  
**Pinned stack:** Minecraft 1.21.1, NeoForge 21.1.248, DragonMineZ 2.1.3
(`libs/dragonminez-2.1.3.jar` per `gradle.properties`).

Cross-links: design §D0-b/c / KD17 / PR-D5 in
`docs/superpowers/specs/2026-10-03-hd-aura-tournament-makers-design.md`; plan Task 6 in
`docs/superpowers/plans/2026-10-03-tournament-roles-makers-phase2.md`.

## Method

1. Read `DmzFormMetadataRegistry.trainerOfferings` / `trainerOfferingEntries` and
   `DmzFormMetadata.TrainerRef`.
2. Read NPC skill-master registration (`DmzFormAutobind.ensureMaster`,
   `DmzFormEditorService.autobindNewForms` / `patchSkillsJson`).
3. Read interact open paths (`DmzFormTrainerEvents`, `DmzSkillMasterInteraction`) and
   purchase apply (`FormEditorNetwork.TrainerPurchasePacket.purchase`).
4. Verified gods group / skill formType shape from
   `src/main/resources/data/xenopixelsmod/dmz/races/saiyan/forms/xenopixels_gods_forms.json`
   and `DmzContentBootstrap` comments (`xenopixels_gods_forms` → skill `xenopixels_divinity`).

**Not claimed:** in-game angel interact / purchase runtime proof. This appendix is in-repo citation.

## How NPC trainers register offerings today

| Step | Class / method | Behaviour |
| --- | --- | --- |
| Store trainer on a form group | `DmzFormMetadata.customNpcTrainers` (`TrainerRef.uuid`, `skillMaster`, `offeredForms`, menu fields) | Persisted under config `xenopixelsmod/dmz-form-editor` via Form Studio save |
| Insert / promote skill master | `DmzFormAutobind.ensureMaster(metadata, trainerId, name, dimension)` | Any `UUID`; sets `skillMaster=true`, `masterLearningEnabled=true` |
| Bind new forms to masters | `DmzFormEditorService.autobindNewForms` → `DmzFormAutobind.bindNewForms` | Only designated `skillMaster` refs |
| Lookup offerings by trainer | `DmzFormMetadataRegistry.trainerOfferingEntries(UUID)` / `trainerOfferings(UUID)` | Match `TrainerRef.uuid` (case-insensitive); require `skillMaster \|\| masterLearningEnabled` |
| Open menu (NPC interact) | `DmzFormTrainerEvents.onEntityInteract` / `DmzSkillMasterInteraction.interact` | Requires `NpcCounterpartSync.isCustomNpc(trainer)` + non-empty offerings + skill-master predicate; then `FormEditorNetwork.sendTrainer` |
| Menu payload | `FormEditorNetwork.sendTrainer` → `DmzSkillMaster.menuFor(uuid, trainerOfferingEntries(uuid))` | Note: the `offerings` list argument to `sendTrainer` is unused; menu always rebuilds from registry by UUID. Pre-assembled menus use `sendTrainerMenu` |
| Purchase apply | `FormEditorNetwork.TrainerPurchasePacket.purchase` (~L227–255) | See Apply path below |
| Native DMZ masters (Beerus/Whis) | `DmzContentBootstrap` / `skills_patch.json` `skillOfferings` | Separate from CustomNPC UUID trainers; gods skill is already offered by beerus/whis in DMZ skills config |

## Can a player UUID be a trainer offerings key?

**YES** — cite `DmzFormMetadataRegistry.trainerOfferingEntries(UUID trainerId)` and
`trainerOfferings(UUID trainerId)` (`src/main/java/.../dmz/form/DmzFormMetadataRegistry.java`).

- The key is the UUID string on `DmzFormMetadata.TrainerRef.uuid`. There is **no** NPC-type check
  inside the registry.
- Unit coverage already treats an arbitrary UUID as a trainer key:
  `DmzTrainerPurchaseTest.onlyTheConfiguredCustomNpcUuidsAreOfferedTrainerDialogue`.
- `DmzFormAutobind.ensureMaster(..., UUID trainerId, ...)` likewise accepts any UUID.

A player UUID therefore **can** be stored and resolved as an offerings key using existing
Form Editor / metadata APIs — no invented DMZ unlock API for the key itself.

### End-to-end caveats (not a NO on the key question)

| Gate | Location | Impact on angel **players** as living trainers |
| --- | --- | --- |
| Interact requires CustomNPC | `DmzFormTrainerEvents` / purchase also calls `NpcCounterpartSync.isCustomNpc` | Player entities never pass; angel-as-trainer interact needs a Xeno-side branch (role `ANGEL` + `sendTrainerMenu`), not a new DMZ unlock API |
| Purchase requires CustomNPC | `TrainerPurchasePacket.purchase` | Same: allow `ServerPlayer` trainer when `PlayerRoleService.get == ANGEL`, still reuse evaluate → debit → setSkillLevel → sync |
| Gods group may be absent from form-editor metadata disk | Registry loads `config/.../dmz-form-editor` only | Wire must filter / ensure gods `DmzFormMetadata` (group `xenopixels_gods_forms`, formType `xenopixels_divinity`) rather than inventing `CombatSkills.grant` |

These are Xeno Form Editor / role gates to extend carefully — they do **not** flip the offerings-key answer to NO.

## formType / group string shape (gods)

From `xenopixels_gods_forms.json` + bootstrap:

| Field | Value |
| --- | --- |
| Group id | `xenopixels_gods_forms` |
| Skill / `DmzFormMetadata.formType` | `xenopixels_divinity` (must not contain `god` — DMZ remaps) |
| Per-form ids (examples) | `ssg`, `ssb`, `ssbe`, `ssrose`, … |
| Form requisite strings | `xenopixels_gods_forms.ssg`, etc. (not the skill formType) |

Trainer purchase matches and writes **`metadata.formType`** (skill id), e.g. `xenopixels_divinity`.
Angel filters must key on **group** `xenopixels_gods_forms` (D0-c), not on formType containing
the substring `gods_forms`.

## Apply path

Must remain `FormEditorNetwork.TrainerPurchasePacket.purchase`
(evaluate → removeTrainingPoints → setSkillLevel → sync):

1. Resolve living trainer entity + distance ≤ 8 blocks.
2. Select `DmzFormMetadata` whose `formType` matches the packet (from offerings).
3. `DmzTrainerPurchase.evaluate(metadata, race, skillLevel, trainingPoints)` — **decision only**.
4. If allowed: `removeTrainingPoints(cost)` → `setSkillLevel(formType, nextLevel)` →
   `updateTransformationSkillLimits(race)`.
5. Sync `StatsSyncS2C` + `ProgressionSyncS2C`; system message from decision.

**Forbidden for this PR:** copying debit into a new packet; treating `evaluate` as apply;
`CombatSkills.grant` for forms.

## Verdict

| Question | Answer |
| --- | --- |
| Player UUID as trainer offerings key without inventing DMZ unlock APIs? | **YES** |
| Wire angel → gods master allowed? | **YES**, with Xeno-side interact/purchase recognition of `PlayerRoleId.ANGEL` and offerings constrained to group `xenopixels_gods_forms` / skill `xenopixels_divinity`; reuse `sendTrainerMenu` + `TrainerPurchasePacket.purchase` apply steps |

Appendix status: **READY** for PR-D5 wire.
