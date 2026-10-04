package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcSkillSet;
import net.bullettrain.xenopixelsmod.features.progression.QuestAvailability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoNpcSavePolicyTest {
    @Test
    void animationAssetSurvivesEditorSave() {
        CompoundTag payload = new CompoundTag();
        payload.putString("ModelAnimation", "mypack:animations/ogre_combat.animation.json");
        assertTrue(XenoNpcSavePolicy.validate(payload).accepted());
        NpcCombatProfile read = NpcCombatProfile.fromTag(
                XenoNpcSavePolicy.merge(new NpcCombatProfile().toTag(), payload));
        assertEquals("mypack:animations/ogre_combat.animation.json", read.modelAnimation);
    }

    @Test
    void itemGiverAvailabilitySurvivesServerMergeAndRejectsOversizedGates() {
        QuestAvailability gate = new QuestAvailability(
                List.of(new QuestAvailability.QuestGate("story:start",
                        QuestAvailability.QuestState.COMPLETED)),
                List.of(new QuestAvailability.DialogGate("village/greeting",
                        QuestAvailability.DialogState.AFTER)), "night",
                List.of(new QuestAvailability.FactionGate("allies",
                        QuestAvailability.Stance.FRIENDLY, true)),
                List.of(new QuestAvailability.ScoreGate("reputation",
                        QuestAvailability.Compare.BIGGER, 5)), 12);
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.job = "item_giver";
        CompoundTag payload = new CompoundTag();
        payload.putString("ItemGiverAvailability", gate.toJson().toString());
        assertTrue(XenoNpcSavePolicy.validate(payload).accepted());
        NpcCombatProfile read = NpcCombatProfile.fromTag(
                XenoNpcSavePolicy.merge(profile.toTag(), payload));
        assertEquals(gate, read.itemGiverAvailability);

        payload.putString("ItemGiverAvailability", "not json");
        assertFalse(XenoNpcSavePolicy.validate(payload).accepted());
        payload.putString("ItemGiverAvailability", "x".repeat(8193));
        assertFalse(XenoNpcSavePolicy.validate(payload).accepted());
        payload.putString("ItemGiverAvailability", "{\"daytime\":\"always\",\"min_level\":0,"
                + "\"quests\":[{\"id\":\"one\",\"state\":\"after\"},{\"id\":\"two\",\"state\":\"after\"},"
                + "{\"id\":\"three\",\"state\":\"after\"},{\"id\":\"four\",\"state\":\"after\"},"
                + "{\"id\":\"five\",\"state\":\"after\"}],\"dialogs\":[],\"factions\":[],\"scores\":[]}");
        assertFalse(XenoNpcSavePolicy.validate(payload).accepted());
    }
    @Test
    void emptyListsClearLastDialogSlotAndTrade() {
        NpcCombatProfile assigned = new NpcCombatProfile();
        assigned.dialogSlots.set(0, "village", "hello");
        assigned.trades.add(new net.bullettrain.xenopixelsmod.npc.trade.NpcTrade(
                "minecraft:emerald", 1, "", 1, "minecraft:stone", 1, 16));
        CompoundTag empty = new CompoundTag();
        empty.put("DialogSlots", new ListTag());
        empty.put("Trades", new ListTag());
        assertTrue(XenoNpcSavePolicy.validate(empty).accepted());
        NpcCombatProfile cleared = NpcCombatProfile.fromTag(
                XenoNpcSavePolicy.merge(assigned.toTag(), empty));
        assertTrue(cleared.dialogSlots.isEmpty());
        assertTrue(cleared.trades.isEmpty());
    }

    @Test
    void rejectsOversizedDialogAndTradeLists() {
        ListTag slots = new ListTag();
        for (int i = 0; i <= net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots.MAX_SLOTS;
                i++) {
            slots.add(new CompoundTag());
        }
        CompoundTag dialogPayload = new CompoundTag();
        dialogPayload.put("DialogSlots", slots);
        assertFalse(XenoNpcSavePolicy.validate(dialogPayload).accepted());

        ListTag trades = new ListTag();
        for (int i = 0; i <= net.bullettrain.xenopixelsmod.npc.trade.NpcTradeList.MAX_TRADES;
                i++) {
            trades.add(new CompoundTag());
        }
        CompoundTag tradePayload = new CompoundTag();
        tradePayload.put("Trades", trades);
        assertFalse(XenoNpcSavePolicy.validate(tradePayload).accepted());
    }

    @Test
    void emptyReferencePayloadClearsAssignedBankTransportAndScene() {
        NpcCombatProfile assigned = new NpcCombatProfile();
        assigned.bankId = "capital";
        assigned.transportNetwork = "rail";
        assigned.sceneId = "arrival";
        CompoundTag empty = new CompoundTag();
        empty.putString("BankId", "");
        empty.putString("TransportNetwork", "");
        empty.putString("SceneId", "");
        assertTrue(XenoNpcSavePolicy.validate(empty).accepted());
        NpcCombatProfile cleared = NpcCombatProfile.fromTag(
                XenoNpcSavePolicy.merge(assigned.toTag(), empty));
        assertEquals("", cleared.bankId);
        assertEquals("", cleared.transportNetwork);
        assertEquals("", cleared.sceneId);
    }

    @Test
    void acceptsAnEditorOwnedFieldWithTheExpectedType() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Vitality", 9001);
        assertTrue(XenoNpcSavePolicy.validate(tag).accepted());
    }

    @Test
    void jobEnabledUsesTheServerBooleanSchema() {
        CompoundTag off = new CompoundTag();
        off.putBoolean("JobEnabled", false);
        assertTrue(XenoNpcSavePolicy.validate(off).accepted());
        assertFalse(NpcCombatProfile.fromTag(XenoNpcSavePolicy.merge(
                new NpcCombatProfile().toTag(), off)).jobEnabled);

        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("JobEnabled", "false");
        assertFalse(XenoNpcSavePolicy.validate(wrongType).accepted());
    }

    @Test
    void healerEffectsAreBoundedAndTypeChecked() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.healerEffects.put("minecraft:regeneration", 2);
        CompoundTag payload = new CompoundTag();
        payload.put("HealerEffects", profile.toTag().get("HealerEffects").copy());
        assertTrue(XenoNpcSavePolicy.validate(payload).accepted());

        ListTag tooMany = new ListTag();
        for (int i = 0; i <= NpcCombatProfile.MAX_HEALER_EFFECTS; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", "minecraft:effect_" + i);
            entry.putInt("Amplifier", 0);
            tooMany.add(entry);
        }
        payload.put("HealerEffects", tooMany);
        assertFalse(XenoNpcSavePolicy.validate(payload).accepted());

        ListTag invalid = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("Id", "minecraft:regeneration");
        entry.putInt("Amplifier", 999);
        invalid.add(entry);
        payload.put("HealerEffects", invalid);
        assertFalse(XenoNpcSavePolicy.validate(payload).accepted());

        payload.put("HealerEffects", profile.toTag().get("HealerEffects").copy());
        payload.putInt("HealerSpeed", 0);
        assertFalse(XenoNpcSavePolicy.validate(payload).accepted());

        payload.remove("HealerSpeed");
        ListTag wrongElementType = new ListTag();
        wrongElementType.add(StringTag.valueOf("minecraft:regeneration"));
        payload.put("HealerEffects", wrongElementType);
        assertFalse(XenoNpcSavePolicy.validate(payload).accepted());
    }

    @Test
    void followerNameCanBeSavedAndClearedButIsBounded() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.followerName = "Goku";
        CompoundTag replace = new CompoundTag();
        replace.putString("FollowerName", "");
        assertTrue(XenoNpcSavePolicy.validate(replace).accepted());
        assertEquals("", NpcCombatProfile.fromTag(XenoNpcSavePolicy.merge(
                profile.toTag(), replace)).followerName);
        replace.putString("FollowerName", "x".repeat(65));
        assertFalse(XenoNpcSavePolicy.validate(replace).accepted());
        replace.putInt("FollowerName", 1);
        assertFalse(XenoNpcSavePolicy.validate(replace).accepted());
    }

    @Test
    void itemGiverSlotsCanBeClearedAndRejectDuplicatesOrOversizedItems() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.job = "item_giver";
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:stone");
        item.putInt("count", 1);
        profile.itemGiverItems[0] = net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack
                .load(item);
        CompoundTag change = new CompoundTag();
        change.put("ItemGiverItems", new ListTag());
        assertTrue(XenoNpcSavePolicy.validate(change).accepted());
        assertTrue(NpcCombatProfile.fromTag(XenoNpcSavePolicy.merge(
                profile.toTag(), change)).itemGiverItems[0].isEmpty());

        CompoundTag slot = new CompoundTag();
        slot.putInt("Slot", 0);
        slot.put("Item", item);
        ListTag duplicate = new ListTag();
        duplicate.add(slot);
        duplicate.add(slot.copy());
        change.put("ItemGiverItems", duplicate);
        assertFalse(XenoNpcSavePolicy.validate(change).accepted());

        item.putString("Huge", "x".repeat(17_000));
        ListTag huge = new ListTag();
        huge.add(slot);
        change.put("ItemGiverItems", huge);
        assertFalse(XenoNpcSavePolicy.validate(change).accepted());

        ListTag lines = new ListTag();
        lines.add(StringTag.valueOf("Have these items {player}"));
        change = new CompoundTag();
        change.put("ItemGiverLines", lines);
        assertTrue(XenoNpcSavePolicy.validate(change).accepted());
        lines.add(StringTag.valueOf("x".repeat(257)));
        assertFalse(XenoNpcSavePolicy.validate(change).accepted());
    }

    @Test
    void playerSkinNamesUseMinecraftAccountSyntax() {
        CompoundTag valid = new CompoundTag();
        valid.putString("SkinPlayer", "Steve_123");
        assertTrue(XenoNpcSavePolicy.validate(valid).accepted());

        valid.putString("SkinPlayer", "a name with spaces");
        assertFalse(XenoNpcSavePolicy.validate(valid).accepted());

        valid.putString("SkinPlayer", "0123456789abcdefg");
        assertFalse(XenoNpcSavePolicy.validate(valid).accepted());
    }

    @Test
    void acceptsXenoNpcDmzSkillsTechniquesFlightAndMasteries() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.skills.set("fly", true, 3);
        profile.flySkillOn = true;
        profile.flySkillLevel = 3;
        profile.addTechnique("kamehameha");
        profile.techniqueLevels.set("kamehameha", 4, 7);
        profile.masteries.setMastery("saiyan", "super_saiyan", 25.0, 100.0);
        profile.knockable = false;
        profile.punchable = false;
        profile.skills.set(NpcSkillSet.KI_SENSE, true, 1);
        profile.kiSenseLockOnRetaliator = true;

        CompoundTag saved = profile.toTag();
        CompoundTag dmzFields = new CompoundTag();
        for (String key : List.of("DmzSkills", "Techniques", "TechniqueLevels", "FlySkillOn",
                "FlySkillLevel", "Masteries", "StackMasteries", "Knockable", "Punchable",
                "KiSenseLockOn")) {
            assertTrue(saved.contains(key), "profile serialization omitted " + key);
            dmzFields.put(key, saved.get(key).copy());
        }

        XenoNpcSavePolicy.Validation result = XenoNpcSavePolicy.validate(dmzFields);
        assertTrue(result.accepted(), result.reason());
    }

    @Test
    void rejectsUnknownProfileKeys() {
        CompoundTag tag = new CompoundTag();
        tag.putString("InjectedField", "do not merge me");
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
    }

    @Test
    void rejectsAnAllowedKeyWithTheWrongTagType() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Vitality", "not an integer");
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
    }

    @Test
    void lockStateCannotRideInsideAnOrdinarySave() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("EditingLocked", false);
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
    }

    @Test
    void everyOnceDisabledFieldIsWritableAgainNowThatEachHasAConsumer() {
        // The four the whitelist originally excluded. Each came back only when something started
        // reading it: textureFor for the night skin, XenoNpcLinkPropagation for the links, and
        // NpcMarkRenderer plus the generated atlas glyphs for the mark. EditingLocked is the
        // exception and stays out - it has its own packet, which is the better route.
        CompoundTag tag = new CompoundTag();
        tag.putString("NightTexture", "xenopixelsmod:textures/entity/night.png");
        tag.putString("MarkIcon", "skull");
        tag.putInt("MarkColor", 0xFF8800);
        assertTrue(XenoNpcSavePolicy.validate(tag).accepted());

        CompoundTag locked = new CompoundTag();
        locked.putBoolean("EditingLocked", true);
        assertFalse(XenoNpcSavePolicy.validate(locked).accepted(),
                "the lock still travels by its own packet, not inside a save");
    }

    @Test
    void storedOnlyFieldsWithoutRuntimeBackingAreNotEditorWritable() {
        // The rule, not the membership: a key the profile does not have, or one sent with the
        // wrong tag type, is refused whatever it is called. All four of the originally excluded
        // fields have since earned their way back, so what is pinned here is the mechanism.
        CompoundTag unknown = new CompoundTag();
        unknown.putString("NotAProfileField", "value");
        assertFalse(XenoNpcSavePolicy.validate(unknown).accepted());

        CompoundTag wrongType = new CompoundTag();
        wrongType.putInt("NightTexture", 7);
        assertFalse(XenoNpcSavePolicy.validate(wrongType).accepted(),
                "a real key sent as the wrong type is still refused");
    }

    @Test
    void nightTextureBecameWritableOnlyAfterItGainedAConsumer() {
        CompoundTag tag = new CompoundTag();
        tag.putString("NightTexture", "xenopixelsmod:textures/entity/night.png");
        assertTrue(XenoNpcSavePolicy.validate(tag).accepted(),
                "NightTexture is consumed by NpcCombatProfile.textureFor and should now save");
    }

    @Test
    void everyAllowedKeyExistsInTheSerializedProfileShape() {
        // Against the policy's OWN shape, not a second sample built here. Many whitelisted keys
        // are written only when something is assigned, so a hand-maintained copy had to be kept
        // in step with buildShape by memory - and three times was not: NpcLines, then Trades, then
        // BardSound each shipped whitelisted but absent from one of the two, which the type check
        // then rejected as a corrupt payload. A test that builds its own sample cannot catch a
        // mistake in the sample it is checking against.
        CompoundTag profile = XenoNpcSavePolicy.profileShape();
        for (String key : XenoNpcSavePolicy.editableKeys()) {
            if (key.equals("HairCodeChunks")) {
                continue;
            }
            assertTrue(profile.contains(key),
                    key + " is whitelisted but absent from PROFILE_SHAPE, so a save carrying it "
                            + "is rejected at the type check");
        }
    }

    @Test
    void acceptsBoundedHairCodeChunksOnlyWithTheHairCodeMarker() {
        CompoundTag tag = new CompoundTag();
        tag.putString("HairCode", "");
        ListTag chunks = new ListTag();
        chunks.add(StringTag.valueOf("a".repeat(NpcCombatProfile.HAIR_CODE_CHUNK)));
        chunks.add(StringTag.valueOf("tail"));
        tag.put("HairCodeChunks", chunks);

        assertTrue(XenoNpcSavePolicy.validate(tag).accepted());

        tag.remove("HairCode");
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
    }

    @Test
    void replacingAChunkedHairCodeWithAShortCodeClearsStaleChunks() {
        CompoundTag current = new NpcCombatProfile().toTag();
        NpcCombatProfile.writeHairCode(current,
                "a".repeat(NpcCombatProfile.HAIR_CODE_CHUNK + 1));
        CompoundTag incoming = new CompoundTag();
        NpcCombatProfile.writeHairCode(incoming, "short");

        CompoundTag merged = XenoNpcSavePolicy.merge(current, incoming);

        assertEquals("short", NpcCombatProfile.readHairCode(merged));
        assertFalse(merged.contains("HairCodeChunks"));
    }
}
