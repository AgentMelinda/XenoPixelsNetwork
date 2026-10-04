package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTechniqueLevels;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

final class XenoNpcSavePolicy {
    /**
     * The most keys a save may carry: every editable key and nothing else.
     *
     * <p>Derived, never hand-maintained. This was written as a literal 96 with a margin over the
     * whitelist, and then the whitelist grew to 98 while the number did not - so the cap stopped
     * bounding the thing it exists to bound, and a save carrying every editable key would have
     * been refused. The margin <em>was</em> the bug: a second number to remember to change.
     *
     * <p>Declared after {@code EDITABLE_KEYS} on purpose; a static initialiser reading a field
     * declared below it would see null.
     */
    private static final int MAX_KEYS;
    /**
     * What a fully populated profile's tag looks like, used to type-check an incoming key.
     *
     * <p>Built from a profile with every <em>conditional</em> key populated, not a bare default.
     * {@code NpcLines} is written only when an NPC has lines of its own, so a default profile omits
     * it — and a missing shape entry is rejected outright at the type check below. The effect would
     * have been a save that passed the whitelist and was then refused for "wrong tag type", which
     * reads as a corrupt payload rather than as a missing sample.
     */
    private static final CompoundTag PROFILE_SHAPE = buildShape();

    /**
     * The shape sample itself, so a test can check the whitelist against the real one.
     *
     * <p>There used to be a second, hand-maintained copy of {@link #buildShape} in
     * {@code XenoNpcSavePolicyTest}. Every conditionally-written key therefore had to be
     * remembered in two places, and three separate times it was remembered in only one - NpcLines,
     * Trades, then BardSound - each time shipping a whitelisted key that the type check then
     * rejected. One copy, checked against itself.
     */
    static CompoundTag profileShape() {
        return PROFILE_SHAPE;
    }

    private static CompoundTag buildShape() {
        NpcCombatProfile sample = new NpcCombatProfile();
        // Every key below is written only when something is assigned. A whitelisted key missing
        // here passes the whitelist and is then refused at the type check, which reads to an
        // operator as a save that silently does nothing.
        sample.bardSound = "sample";
        sample.path.add(new net.bullettrain.xenopixelsmod.npc.path.NpcPath.Point(0, 0, 0));
        // Written only when something is assigned, so the shape sample must assign one - a
        // whitelisted key missing from the shape is rejected at the type check.
        sample.dialogSlots.set(0, "sample", "sample");
        sample.transportNetwork = "sample";
        sample.bankId = "sample";
        sample.sceneId = "sample";
        // Also conditional: an NPC with no script writes nothing, so the shape needs one assigned.
        sample.scriptId = "sample";
        sample.scripts.setTabs(java.util.List.of(
                new net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.Tab("sample",
                        java.util.List.of())));
        // SceneTrigger is written only alongside a scene id, so the sample needs both - the trap
        // that bit NpcLines, Trades and BardSound in turn.
        sample.sceneTrigger =
                net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger.INTERACT;
        sample.job = net.bullettrain.xenopixelsmod.npc.XenoNpcJob.ITEM_GIVER.id();
        sample.followerName = "sample";
        sample.trades.add(new net.bullettrain.xenopixelsmod.npc.trade.NpcTrade(
                "minecraft:emerald", 1, "", 1, "minecraft:bread", 1, 16));
        // Gear, drops and the loot mode are all written only when set, so the sample must set one
        // of each. This is the trap that bit NpcLines, Trades and BardSound in turn.
        //
        // Built from a raw tag rather than from an ItemStack: serialising a stack needs a
        // HolderLookup.Provider and this runs in a static initialiser with no level in sight. The
        // shape only has to carry the right NBT *type* per key, and a compound is a compound.
        net.minecraft.nbt.CompoundTag sampleItem = new net.minecraft.nbt.CompoundTag();
        sampleItem.putString("id", "minecraft:iron_helmet");
        sampleItem.putInt("count", 1);
        sample.itemGiverItems[0] = net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack
                .load(sampleItem);
        sample.gear.set(0, net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack
                .load(sampleItem));
        sample.drops.add(new net.bullettrain.xenopixelsmod.npc.inventory.NpcDrop(
                net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack.load(sampleItem),
                50.0f));
        sample.drops.setLootMode(
                net.bullettrain.xenopixelsmod.npc.inventory.NpcLootMode.AUTO_PICKUP);
        sample.drops.setMinExp(1);
        sample.drops.setMaxExp(2);
        for (net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category category
                : net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.values()) {
            sample.setLines(category, java.util.List.of("sample"));
        }
        return sample.toTag();
    }
    private static final Set<String> EDITABLE_KEYS = Set.of(
            "Authoritative", "Knockable", "Punchable", "PinNativeCombat", "Race",
            "KiSenseLockOn",
            "Strength", "StrikePower", "Resistance", "Vitality", "KiPower", "Energy",
            // MaxHealthOverride joins them now that NpcVitalitySync reads it: above zero it wins
            // over the vitality calculation outright. It was added with its consumer, not before.
            "MaxHealthOverride",
            "NpcMeleeProps", "NpcRangedProps", "NpcProjectileProps", "NpcResistanceProps",
            "AuraOn", "AuraColorHex", "SelectedFormGroup", "SelectedForm",
            "SelectedStackFormGroup", "SelectedStackForm", "AuraRocks", "AuraSparking",
            "AuraLightning", "AuraGroundRing", "AggroMultiplier", "AimAccuracy",
            "KiWeaponOn", "KiWeaponType", "FormPower", "BaseSize", "KiChargePercent",
            "AuraScale", "HairEnabled", "HairCode", "HairCodeChunks", "HairColor", "HairStyleId",
            "DmzAppearance", "ModelKind", "ModelId", "ModelAnimation", "ModelTexture", "ModelTint",
            "SkinPlayer",
            "ModelGlowing", "ImmuneToFire", "BurnsInSun", "CanDrown", "NoFallDamage",
            "PotionImmune", "CobwebAffected", "Visible", "HitboxScale", "BossBar",
            // NightTexture is editable again because it finally has a runtime consumer:
            // NpcCombatProfile.textureFor is read by both XenoNpcRenderer and XenoNpcGeoModel, so
            // an NPC really does change skin after dusk. It was correctly excluded while the field
            // was written by the editor and read by nothing.
            // LinkedNpcs joins it for the same reason: XenoNpcLinkPropagation now copies an
            // accepted save onto every NPC in the list, one hop, skipping locked and unloaded
            // targets. Before that it was a list nothing walked.
            // MarkIcon and MarkColor complete the set: the six glyphs are generated into the atlas
            // and NpcMarkRenderer draws them above the head, tinted. They were stored-but-unread
            // before that, which is exactly what this list is meant to keep out.
            "BossBarColor", "CreatureType", "MeleeAnimation", "NightTexture", "LinkedNpcs",
            "MarkIcon", "MarkColor",
            "SoundLiving", "SoundAngry",
            "SoundHurt", "SoundDeath", "SoundStep", "SoundHasPitch", "CombatBrain",
            "BrainVersion", "BrainStrike", "BrainCharge", "BrainFlyingFist", "BrainHeavyHit",
            "BrainBoneCrusher", "BrainKiBlast", "BrainKiWave", "BrainKiDisk", "BrainKiNamed",
            "BrainFly", "CanUseFlight", "BrainLand", "BrainAscend", "BrainDisengage", "BrainVanish",
            "BrainZanzoken", "BrainChase", "BrainDeflectBlast", "BrainDeflectWave",
            "BrainSpecialCooldown", "BrainChances", "BrainModifiers",
            // Whether the Xeno layer on top of the DragonMineZ port may fire - the BT3 combo
            // choreography and the repositioning moves. NpcCombatProfile.allowXenoSpecial reads it
            // and NpcCombatMoves asks before every reposition, so the key goes in with its
            // consumers rather than ahead of them.
            "BrainXenoSpecials",
            // AI controls are admitted only with their goal, targeting or riding consumers.
            "AiCanSwim", "AiAvoidsWater", "AiDoorInteract", "AiLeapAtTarget", "AiReturnToStart",
            "AiOnFoundEnemy", "AiShelterFrom", "AiMustSeeTarget", "AiAttackInvisible",
            "AiMountControl",
            // Stats > regen. XenoNpcBehaviour.tickRegen heals against both, so they go in with
            // their consumer rather than ahead of it.
            "HealthRegen", "CombatRegen",
            // The ordered attack slots. Consumed by NpcMeleeAnimCycle, which walks them in order
            // on consecutive hits - the consumer predates the editor rows by a long way; what was
            // missing was any way to author them.
            "MeleeAnimSlots",
            // The speech and dialogue bubble palettes. Read by SpeechBubbleRenderer and
            // DialogueBubbleRenderer, which is why they are here rather than stored-and-unused.
            "BubblePalette", "OptionPalette",
            // Default bubble outline; SpeechBubbleRenderer.shapeOf reads it through the visual
            // options, and a script's per-line shape overrides it.
            "BubbleShape",
            // Trader matching switches; NpcTradeOffers.build reads both through getOffers.
            "TradeIgnoreDamage", "TradeIgnoreNbt",
            // Display > Cape / Overlay / Showing Layers; NpcCapeLayer, NpcOverlayLayer and
            // XenoNpcRenderer.showOuterLayers read them through the visual options.
            "DisplayCape", "DisplayOverlay", "DisplayOverlayGlow", "DisplayOuterLayers",
            // This NPC's own conversation. Consumed by XenoNpcEntity.dialogueFor, which prefers it
            // over the dialogue the NPC's role names, and by XenoNpcDialoguePacket, which resolves
            // through the same method before acting on a chosen option. Both predate this key -
            // the whitelist entry is the last step, as it always is.
            // The bubble fields join once their consumers exist: SpeechBubbleRenderer reads the
            // height, XenoNpcSpeech the duration, SpeechBubbleLayout the line cap, and
            // XenoNpcSpeech.linesFor the lines. None was whitelisted before it was read.
            "BubbleHeight", "BubbleDurationTicks", "BubbleMaxLines", "NpcLines",
            // Gates only the unprompted RANDOM/WORLD lines; XenoNpcSpeech.ambient reads it.
            "AmbientLines",
            // Trades join now that XenoNpcEntity implements Merchant and actually offers them;
            // the Trader page's eighteen slots were disabled placeholders until this existed.
            "Trades",
            // Twelve references into the shared dialogue library. The editor's slot grid writes
            // them and XenoNpcEntity resolves them, so the key goes in with its consumer.
            "DialogSlots",
            // Which shared network this NPC serves. The destinations themselves live in the world
            // store now, so a save carries the reference and never the list.
            "TransportNetwork",
            // Which shared bank this NPC tells for. The tabs and their costs live in the world
            // store, so a save carries the reference and never the definition.
            "BankId", "SceneId",
            // Which script runs for this NPC. The text lives in the world store's scripts folder
            // and XenoScriptRunner resolves this id through XenoNpcScripts, so the save carries
            // the reference and never the source.
            "ScriptId",
            // The tabbed container behind the script screen. NpcScriptHost reads all three: the
            // tabs to build instances, the switch to run them at all, the language to pick the
            // engine. Checked by NpcScriptContainer.reject below.
            "ScriptTabs", "ScriptsEnabled", "ScriptLanguage",
            // What makes the scene play. XenoNpcSceneTriggers reads it from four call sites, so it
            // goes in with its consumer rather than ahead of it.
            "SceneTrigger",
            // My NPCs' second axis, independent of the role. Written as an id, never an ordinal.
            "Job", "JobEnabled",
            // The Bard job's own fields. NpcBardJob reads every one of them, so they go in with
            // their consumer rather than ahead of it.
            "BardSound", "BardJukebox", "BardLoops", "BardOnDistance", "BardHasOffDistance",
            "BardOffDistance",
            "HealerRange", "HealerType", "HealerSpeed", "HealerEffects",
            "FollowerName", "ItemGiverItems", "ItemGiverMethod",
            "ItemGiverCooldownType", "ItemGiverCooldown", "ItemGiverLines",
            "ItemGiverAvailability",
            // The patrol route, and the Guard job that walks it. NpcPathWalker and NpcGuardJob
            // read every one of these, so they go in with their consumers rather than ahead.
            "Path", "GuardAnimals", "GuardMonsters", "GuardCreepers",
            // DMZ skills, selected techniques and their per-NPC upgrade levels are consumed by
            // the native Xeno NPC renderer and combat dispatchers. Flight also has dedicated
            // fields because it controls the entity's navigation type.
            "DmzSkills", "Techniques", "TechniqueLevels", "FlySkillOn", "FlySkillLevel",
            "Masteries", "StackMasteries",
            // Hold your spot, and wave at people. StayHomeStrollGoal and NpcSocialBehaviour read
            // them, so they go in with their consumers rather than ahead of them.
            "StayHome", "SocialGestures",
            // The Inventory page. NpcGear.applyTo puts the six slots on the entity and
            // XenoNpcEntity reads the drop list and the experience range when it dies, so these go
            // in with their consumers rather than ahead of them. Projectile is deliberately absent:
            // nothing fires one, so there is no field to whitelist.
            "Gear", "NpcDrops", "LootMode", "MinExp", "MaxExp",
            "Dialogue"
    );

    static {
        MAX_KEYS = EDITABLE_KEYS.size();
    }

    private XenoNpcSavePolicy() {
    }

    static Set<String> editableKeys() {
        return EDITABLE_KEYS;
    }

    static Validation validate(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return Validation.accept();
        }
        if (tag.size() > MAX_KEYS) {
            return Validation.reject("too many profile fields");
        }
        for (String key : tag.getAllKeys()) {
            if (!EDITABLE_KEYS.contains(key)) {
                return Validation.reject("field is not editor-owned: " + key);
            }
            if (key.equals("SkinPlayer")) {
                if (!tag.contains(key, Tag.TAG_STRING)
                        || !tag.getString(key).matches("[A-Za-z0-9_]{0,16}")) {
                    return Validation.reject("invalid player skin name");
                }
                continue;
            }
            if (key.equals("FollowerName")) {
                if (!tag.contains(key, Tag.TAG_STRING)
                        || tag.getString(key).length() > 64) {
                    return Validation.reject("invalid follower name");
                }
                continue;
            }
            if (key.equals("ItemGiverItems")) {
                Validation items = validateItemGiverItems(tag);
                if (!items.accepted()) return items;
                continue;
            }
            if (key.equals("ItemGiverLines")) {
                Validation lines = validateItemGiverLines(tag);
                if (!lines.accepted()) return lines;
                continue;
            }
            if (key.equals("ItemGiverAvailability")) {
                Validation availability = validateItemGiverAvailability(tag);
                if (!availability.accepted()) return availability;
                continue;
            }
            if (key.equals("ItemGiverMethod") || key.equals("ItemGiverCooldownType")
                    || key.equals("ItemGiverCooldown")) {
                if (!tag.contains(key, Tag.TAG_INT)) return Validation.reject("wrong tag type for " + key);
                int value = tag.getInt(key);
                int maximum = key.equals("ItemGiverMethod") ? 4
                        : key.equals("ItemGiverCooldownType") ? 2 : 86400;
                if (value < 0 || value > maximum) return Validation.reject("invalid " + key);
                continue;
            }
            if (key.equals("ScriptTabs")) {
                String invalid = net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.reject(tag);
                if (invalid != null) return Validation.reject(invalid);
                continue;
            }
            if (key.equals("ScriptLanguage")) {
                if (!tag.contains(key, Tag.TAG_STRING) || tag.getString(key).length() > 32) {
                    return Validation.reject("invalid " + key);
                }
                continue;
            }
            if (key.equals("SceneId") || key.equals("ScriptId")) {
                // Both are ids into the world store, so both are checked as ids rather than as
                // free text: an id that could not become a filename could never be resolved, and
                // catching it here is what stops a save that looks fine and finds nothing.
                if (!tag.contains(key, Tag.TAG_STRING)) {
                    return Validation.reject("wrong tag type for " + key);
                }
                String id = tag.getString(key);
                if (!id.isEmpty() && net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths
                        .reject(id) != null) {
                    return Validation.reject("invalid "
                            + (key.equals("SceneId") ? "scene" : "script") + " id");
                }
                continue;
            }
            if (key.equals("MeleeAnimSlots")) {
                Validation slots = validateMeleeAnimSlots(tag);
                if (!slots.accepted()) {
                    return slots;
                }
                continue;
            }
            if (key.equals("Dialogue")) {
                Validation dialogue = validateDialogue(tag);
                if (!dialogue.accepted()) {
                    return dialogue;
                }
                continue;
            }
            if (key.equals("DialogSlots")) {
                Validation slots = validateDialogSlots(tag);
                if (!slots.accepted()) {
                    return slots;
                }
                continue;
            }
            if (key.equals("Trades")) {
                Validation trades = validateTrades(tag);
                if (!trades.accepted()) {
                    return trades;
                }
                continue;
            }
            if (key.equals("Gear")) {
                Validation gear = validateGear(tag);
                if (!gear.accepted()) {
                    return gear;
                }
                continue;
            }
            if (key.equals("NpcDrops")) {
                Validation drops = validateDrops(tag);
                if (!drops.accepted()) {
                    return drops;
                }
                continue;
            }
            if (key.equals("HairCodeChunks")) {
                Validation chunks = validateHairCodeChunks(tag);
                if (!chunks.accepted()) {
                    return chunks;
                }
                continue;
            }
            if (key.equals("HealerEffects")) {
                Validation effects = validateHealerEffects(tag);
                if (!effects.accepted()) return effects;
                continue;
            }
            if (key.equals("HealerRange") || key.equals("HealerType")
                    || key.equals("HealerSpeed")) {
                if (!tag.contains(key, Tag.TAG_INT)) return Validation.reject("wrong tag type for " + key);
                int value = tag.getInt(key);
                int min = key.equals("HealerSpeed") ? 10 : 0;
                int max = key.equals("HealerRange") ? 64 : key.equals("HealerType") ? 2 : 1200;
                if (value < min || value > max) return Validation.reject("invalid " + key);
                continue;
            }
            if (key.equals("DmzSkills")) {
                Validation skills = validateDmzSkills(tag);
                if (!skills.accepted()) {
                    return skills;
                }
                continue;
            }
            if (key.equals("Techniques")) {
                Validation techniques = validateTechniques(tag);
                if (!techniques.accepted()) {
                    return techniques;
                }
                continue;
            }
            if (key.equals("TechniqueLevels")) {
                Validation levels = validateTechniqueLevels(tag);
                if (!levels.accepted()) {
                    return levels;
                }
                continue;
            }
            if (key.equals("Masteries") || key.equals("StackMasteries")) {
                Validation masteries = validateMasteries(tag, key);
                if (!masteries.accepted()) {
                    return masteries;
                }
                continue;
            }
            Tag expected = PROFILE_SHAPE.get(key);
            Tag received = tag.get(key);
            if (expected == null || received == null || expected.getId() != received.getId()) {
                return Validation.reject("wrong tag type for " + key);
            }
        }
        return Validation.accept();
    }

    private static Validation validateHealerEffects(CompoundTag tag) {
        if (!tag.contains("HealerEffects", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for HealerEffects");
        }
        ListTag effects = (ListTag) tag.get("HealerEffects");
        if (effects.size() > NpcCombatProfile.MAX_HEALER_EFFECTS
                || (!effects.isEmpty() && effects.getElementType() != Tag.TAG_COMPOUND)) {
            return Validation.reject("invalid healer effects list");
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Tag raw : effects) {
            if (!(raw instanceof CompoundTag entry)
                    || !entry.contains("Id", Tag.TAG_STRING)
                    || !entry.contains("Amplifier", Tag.TAG_INT)
                    || entry.size() != 2) return Validation.reject("invalid healer effect entry");
            String id = entry.getString("Id");
            int amp = entry.getInt("Amplifier");
            if (id.length() > 128 || net.minecraft.resources.ResourceLocation.tryParse(id) == null
                    || !ids.add(id) || amp < 0 || amp > 9) {
                return Validation.reject("invalid healer effect entry");
            }
        }
        return Validation.accept();
    }

    static CompoundTag merge(CompoundTag current, CompoundTag incoming) {
        CompoundTag merged = current.copy();
        if (incoming.contains("HairCode")) {
            merged.remove("HairCodeChunks");
        }
        for (String key : incoming.getAllKeys()) {
            merged.put(key, incoming.get(key).copy());
        }
        return merged;
    }

    /**
     * Bounds the attack-slot list.
     *
     * <p>The generic check below only compares tag ids, so a list key would otherwise accept a list
     * of any length - the profile's reader clamps it, but accepting an unbounded list into a save
     * payload is worth refusing outright rather than relying on the far end to trim.
     */
    private static Validation validateMeleeAnimSlots(CompoundTag tag) {
        if (!tag.contains("MeleeAnimSlots", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for MeleeAnimSlots");
        }
        ListTag slots = tag.getList("MeleeAnimSlots", Tag.TAG_COMPOUND);
        if (slots.size() > NpcCombatProfile.MELEE_SLOT_COUNT) {
            return Validation.reject("too many attack slots");
        }
        return Validation.accept();
    }

    private static Validation validateDmzSkills(CompoundTag tag) {
        if (!tag.contains("DmzSkills", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for DmzSkills");
        }
        ListTag skills = tag.getList("DmzSkills", Tag.TAG_COMPOUND);
        if (skills.size() > 256) {
            return Validation.reject("too many DMZ skills");
        }
        for (int i = 0; i < skills.size(); i++) {
            CompoundTag skill = skills.getCompound(i);
            String id = skill.getString("Id");
            if (id.isBlank() || id.length() > 128
                    || !skill.contains("On", Tag.TAG_BYTE)
                    || !skill.contains("Level", Tag.TAG_INT)
                    || skill.getInt("Level") < 1 || skill.getInt("Level") > 1000) {
                return Validation.reject("invalid DMZ skill entry");
            }
        }
        return Validation.accept();
    }

    private static Validation validateTechniques(CompoundTag tag) {
        if (!tag.contains("Techniques", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for Techniques");
        }
        ListTag techniques = tag.getList("Techniques", Tag.TAG_STRING);
        if (techniques.size() > 256) {
            return Validation.reject("too many DMZ techniques");
        }
        for (int i = 0; i < techniques.size(); i++) {
            String id = techniques.getString(i);
            if (id.isBlank() || id.length() > 128) {
                return Validation.reject("invalid DMZ technique id");
            }
        }
        return Validation.accept();
    }

    private static Validation validateTechniqueLevels(CompoundTag tag) {
        if (!tag.contains("TechniqueLevels", Tag.TAG_COMPOUND)) {
            return Validation.reject("wrong tag type for TechniqueLevels");
        }
        CompoundTag levels = tag.getCompound("TechniqueLevels");
        if (levels.size() > 256) {
            return Validation.reject("too many DMZ technique levels");
        }
        for (String id : levels.getAllKeys()) {
            if (id.isBlank() || id.length() > 128
                    || !levels.contains(id, Tag.TAG_COMPOUND)) {
                return Validation.reject("invalid DMZ technique level entry");
            }
            CompoundTag entry = levels.getCompound(id);
            if (entry.size() > 2 || !entry.contains("Damage", Tag.TAG_INT)
                    || !entry.contains("Cooldown", Tag.TAG_INT)
                    || entry.getInt("Damage") < 0
                    || entry.getInt("Damage") > NpcTechniqueLevels.MAX_EFFECTIVE_LEVEL
                    || entry.getInt("Cooldown") < 0
                    || entry.getInt("Cooldown") > NpcTechniqueLevels.MAX_EFFECTIVE_LEVEL) {
                return Validation.reject("invalid DMZ technique upgrade level");
            }
        }
        return Validation.accept();
    }

    private static Validation validateMasteries(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return Validation.reject("wrong tag type for " + key);
        }
        CompoundTag masteries = tag.getCompound(key);
        if (masteries.size() > 512) {
            return Validation.reject("too many form masteries");
        }
        for (String formKey : masteries.getAllKeys()) {
            if (formKey.isBlank() || formKey.length() > 128
                    || !masteries.contains(formKey, Tag.TAG_DOUBLE)) {
                return Validation.reject("invalid form mastery entry");
            }
            double value = masteries.getDouble(formKey);
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0e9) {
                return Validation.reject("form mastery is outside the accepted range");
            }
        }
        return Validation.accept();
    }

    /**
     * Bounds a dialogue.
     *
     * <p>The generic check below only compares tag ids, so a compound key would otherwise accept a
     * conversation of any depth and size. {@code XenoDialogueNbt} clamps everything on read, but
     * accepting an unbounded payload and relying on the far end to trim is the wrong order: this
     * arrives over the network from whoever has the editor open.
     *
     * <p>The counts match the ones the open packet will carry. A dialogue that passed here but was
     * too large to send would save and then never open, which is a worse failure than a refusal
     * that says so.
     */
    private static Validation validateDialogue(CompoundTag tag) {
        if (!tag.contains("Dialogue", Tag.TAG_COMPOUND)) {
            return Validation.reject("wrong tag type for Dialogue");
        }
        CompoundTag dialogue = tag.getCompound("Dialogue");
        if (dialogue.getString("Start").length() > XenoDialogueNbt.MAX_NODE_ID) {
            return Validation.reject("dialogue start id is too long");
        }
        ListTag nodes = dialogue.getList("Nodes", Tag.TAG_COMPOUND);
        if (nodes.size() > XenoDialogueNbt.MAX_NODES) {
            return Validation.reject("too many dialogue lines");
        }
        for (int i = 0; i < nodes.size(); i++) {
            CompoundTag node = nodes.getCompound(i);
            if (node.getString("Id").length() > XenoDialogueNbt.MAX_NODE_ID
                    || node.getString("Text").length() > XenoDialogueNbt.MAX_TEXT) {
                return Validation.reject("dialogue line is too long");
            }
            ListTag options = node.getList("Options", Tag.TAG_COMPOUND);
            if (options.size() > XenoDialogueNbt.MAX_OPTIONS) {
                return Validation.reject("too many dialogue answers");
            }
            for (int o = 0; o < options.size(); o++) {
                CompoundTag option = options.getCompound(o);
                if (option.getString("Text").length() > XenoDialogueNbt.MAX_OPTION_TEXT
                        || option.getString("Target").length() > XenoDialogueNbt.MAX_NODE_ID
                        || option.getString("Quest").length() > XenoDialogueNbt.MAX_QUEST_ID
                        || option.getString("Command").length() > XenoDialogueNbt.MAX_COMMAND) {
                    return Validation.reject("dialogue answer is too long");
                }
            }
        }
        return Validation.accept();
    }

    private static Validation validateDialogSlots(CompoundTag tag) {
        if (!tag.contains("DialogSlots", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for DialogSlots");
        }
        ListTag slots = tag.getList("DialogSlots", Tag.TAG_COMPOUND);
        if (slots.size() > net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots.MAX_SLOTS) {
            return Validation.reject("too many dialog slots");
        }
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            String group = slot.getString("G");
            String id = slot.getString("I");
            if (group.isEmpty() && id.isEmpty()) {
                continue;
            }
            if (net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths.rejectGroup(group)
                    != null || net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths.reject(id)
                    != null) {
                return Validation.reject("invalid dialog slot reference");
            }
        }
        return Validation.accept();
    }

    private static Validation validateTrades(CompoundTag tag) {
        if (!tag.contains("Trades", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for Trades");
        }
        ListTag trades = tag.getList("Trades", Tag.TAG_COMPOUND);
        if (trades.size() > net.bullettrain.xenopixelsmod.npc.trade.NpcTradeList.MAX_TRADES) {
            return Validation.reject("too many trades");
        }
        for (int i = 0; i < trades.size(); i++) {
            CompoundTag trade = trades.getCompound(i);
            if (trade.getString("A").length() > 128 || trade.getString("B").length() > 128
                    || trade.getString("R").length() > 128) {
                return Validation.reject("trade item id is too long");
            }
        }
        return Validation.accept();
    }

    /**
     * Advanced &gt; Inventory: the six worn slots.
     *
     * <p>Each holds a serialised {@code ItemStack}. Whether it names an item this server can resolve
     * is deliberately <em>not</em> checked: a server missing the mod that supplies it must keep the
     * tag so the slot fills back in when the mod returns, which is the same reason trades hold ids.
     * What is checked is size, because an unbounded tag off the wire is an allocation a client gets
     * to choose.
     */
    private static Validation validateItemGiverAvailability(CompoundTag tag) {
        if (!tag.contains("ItemGiverAvailability", Tag.TAG_STRING)) {
            return Validation.reject("wrong tag type for ItemGiverAvailability");
        }
        String value = tag.getString("ItemGiverAvailability");
        if (value.length() > 8192) return Validation.reject("item giver availability is too large");
        try {
            com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(value);
            if (!json.isJsonObject()) return Validation.reject("invalid item giver availability");
            var gate = net.bullettrain.xenopixelsmod.features.progression.QuestAvailability
                    .fromJson(json);
            if (!gate.toJson().equals(json) || gate.minLevel() > 1000
                    || !java.util.Set.of("always", "day", "night").contains(gate.daytime())) {
                return Validation.reject("invalid item giver availability");
            }
            for (var quest : gate.quests()) {
                if (quest.questId().length() > 128) return Validation.reject("quest gate id too long");
            }
            for (var dialog : gate.dialogs()) {
                if (dialog.dialogId().length() > 128) return Validation.reject("dialog gate id too long");
            }
            for (var faction : gate.factions()) {
                if (faction.factionId().length() > 128) return Validation.reject("faction gate id too long");
            }
            for (var score : gate.scores()) {
                if (score.objective().length() > 64 || Math.abs((long) score.value()) > 1_000_000L) {
                    return Validation.reject("invalid scoreboard gate");
                }
            }
            return Validation.accept();
        } catch (RuntimeException exception) {
            return Validation.reject("invalid item giver availability");
        }
    }

    private static Validation validateItemGiverLines(CompoundTag tag) {
        if (!tag.contains("ItemGiverLines", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for ItemGiverLines");
        }
        ListTag lines = (ListTag) tag.get("ItemGiverLines");
        if (lines.size() > 16 || (!lines.isEmpty() && lines.getElementType() != Tag.TAG_STRING)) {
            return Validation.reject("invalid item giver lines");
        }
        for (Tag raw : lines) {
            if (!(raw instanceof net.minecraft.nbt.StringTag line)
                    || line.getAsString().length() > 256) {
                return Validation.reject("invalid item giver line");
            }
        }
        return Validation.accept();
    }

    private static Validation validateItemGiverItems(CompoundTag tag) {
        if (!tag.contains("ItemGiverItems", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for ItemGiverItems");
        }
        ListTag items = (ListTag) tag.get("ItemGiverItems");
        if (items.size() > NpcCombatProfile.ITEM_GIVER_SLOTS
                || (!items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND)) {
            return Validation.reject("invalid item giver slots");
        }
        boolean[] seen = new boolean[NpcCombatProfile.ITEM_GIVER_SLOTS];
        for (Tag raw : items) {
            if (!(raw instanceof CompoundTag entry) || entry.size() != 2
                    || !entry.contains("Slot", Tag.TAG_INT)
                    || !entry.contains("Item", Tag.TAG_COMPOUND)) {
                return Validation.reject("invalid item giver slot");
            }
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= seen.length || seen[slot]
                    || !net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack
                    .withinBounds(entry.getCompound("Item"))) {
                return Validation.reject("invalid item giver slot");
            }
            seen[slot] = true;
        }
        return Validation.accept();
    }

    private static Validation validateGear(CompoundTag tag) {
        if (!tag.contains("Gear", Tag.TAG_COMPOUND)) {
            return Validation.reject("wrong tag type for Gear");
        }
        CompoundTag gear = tag.getCompound("Gear");
        if (gear.size() > net.bullettrain.xenopixelsmod.npc.inventory.NpcGear.SLOTS.length) {
            return Validation.reject("too many gear slots");
        }
        for (String slot : gear.getAllKeys()) {
            if (!gear.contains(slot, Tag.TAG_COMPOUND)) {
                return Validation.reject("wrong tag type for gear slot " + slot);
            }
            if (!net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack
                    .withinBounds(gear.getCompound(slot))) {
                return Validation.reject("gear item is too large");
            }
        }
        return Validation.accept();
    }

    /** Advanced &gt; Inventory: the nine drop rows. */
    private static Validation validateDrops(CompoundTag tag) {
        if (!tag.contains("NpcDrops", Tag.TAG_LIST)) {
            return Validation.reject("wrong tag type for NpcDrops");
        }
        ListTag drops = tag.getList("NpcDrops", Tag.TAG_COMPOUND);
        if (drops.size() > net.bullettrain.xenopixelsmod.npc.inventory.NpcDropList.MAX_DROPS) {
            return Validation.reject("too many drops");
        }
        for (int i = 0; i < drops.size(); i++) {
            CompoundTag drop = drops.getCompound(i);
            if (!drop.contains("I")) {
                continue;
            }
            if (!drop.contains("I", Tag.TAG_COMPOUND)) {
                return Validation.reject("wrong tag type for drop item");
            }
            if (!net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack
                    .withinBounds(drop.getCompound("I"))) {
                return Validation.reject("drop item is too large");
            }
        }
        return Validation.accept();
    }

    private static Validation validateHairCodeChunks(CompoundTag tag) {
        if (!tag.contains("HairCode", Tag.TAG_STRING)
                || !tag.contains("HairCodeChunks", Tag.TAG_LIST)) {
            return Validation.reject("HairCodeChunks requires HairCode");
        }
        ListTag chunks = tag.getList("HairCodeChunks", Tag.TAG_STRING);
        if (chunks.isEmpty() || chunks.size() > 4) {
            return Validation.reject("invalid HairCodeChunks count");
        }
        int length = 0;
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.getString(index);
            if (chunk.length() > NpcCombatProfile.HAIR_CODE_CHUNK) {
                return Validation.reject("HairCodeChunks entry is too large");
            }
            length += chunk.length();
        }
        if (length > NpcCombatProfile.HAIR_CODE_CHUNK * 4) {
            return Validation.reject("HairCodeChunks total is too large");
        }
        return Validation.accept();
    }

    public record Validation(boolean accepted, String reason) {
        static Validation accept() {
            return new Validation(true, "");
        }

        static Validation reject(String reason) {
            return new Validation(false, reason);
        }
    }
}
