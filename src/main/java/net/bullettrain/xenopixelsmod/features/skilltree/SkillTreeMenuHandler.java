package net.bullettrain.xenopixelsmod.features.skilltree;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;

/**
 * Skill tree menu / sync helper.
 *
 * Uses vanilla {@link CompoundTag} only — no third-party scoreboard sync libraries.
 */
public class SkillTreeMenuHandler {

    private final String playerName;
    private final GameProfile gameProfile;

    public SkillTreeMenuHandler(String playerName, GameProfile gameProfile) {
        this.playerName = Objects.requireNonNullElse(playerName, "unknown");
        this.gameProfile = gameProfile;
        XenoPixelsMod.LOGGER.debug("SkillTreeMenuHandler created for: {}", this.playerName);
    }

    public String getPlayerName() {
        return playerName;
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public String getSyncName() {
        return "skill_tree_data_" + playerName;
    }

    /**
     * Build NBT payload describing this player's skill tree for client sync / UI.
     */
    public CompoundTag syncData() {
        CompoundTag compound = new CompoundTag();
        SkillTreeManager.SkillTreeData data = SkillTreeManager.getPlayerData(playerName);

        compound.putString("PlayerName", playerName);
        compound.putBoolean("IsSkillTreeUnlocked", true);

        ListTag unlocked = new ListTag();
        for (String skillId : data.getUnlockedSkills()) {
            unlocked.add(StringTag.valueOf(skillId));
        }
        compound.put("UnlockedSkills", unlocked);

        CompoundTag playstyles = new CompoundTag();
        for (String style : SkillTreeManager.getPlaystyles()) {
            CompoundTag styleTag = new CompoundTag();
            styleTag.putString("name", style);
            styleTag.putBoolean("isUnlocked", SkillTreeManager.isPlaystyleUnlocked(playerName, style));
            styleTag.putInt("unlockedCount", getUnlockedCountForPlaystyle(style));
            playstyles.put(style, styleTag);
        }
        compound.put("PlayStyles", playstyles);

        return compound;
    }

    /**
     * JSON view of the same data (debug / web-style UIs).
     */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("playerName", playerName);
        root.addProperty("syncName", getSyncName());

        JsonObject playstyles = new JsonObject();
        for (String style : SkillTreeManager.getPlaystyles()) {
            JsonObject styleData = new JsonObject();
            styleData.addProperty("name", style);
            styleData.addProperty("isUnlocked", SkillTreeManager.isPlaystyleUnlocked(playerName, style));
            styleData.addProperty("unlockedCount", getUnlockedCountForPlaystyle(style));
            playstyles.add(style, styleData);
        }
        root.add("playstyles", playstyles);
        return root;
    }

    /**
     * Apply inbound sync tag (e.g. from a client request). Currently logs only.
     */
    public void onReceiveSync(CompoundTag tag) {
        XenoPixelsMod.LOGGER.debug(
                "SkillTreeMenuHandler: Received sync data for {} ({} keys)",
                playerName,
                tag != null ? tag.getAllKeys().size() : 0);
    }

    public int getUnlockedCountForPlaystyle(String playstyle) {
        SkillTreeManager.SkillTreeData data = SkillTreeManager.getPlayerData(playerName);
        return (int) SkillTreeManager.getNodesForPlaystyle(playstyle).stream()
                .map(SkillNode::getSkillId)
                .filter(data::isSkillUnlocked)
                .count();
    }

    public List<String> getUnlockedPlaystyles() {
        return SkillTreeManager.getPlaystyles().stream()
                .filter(style -> SkillTreeManager.isPlaystyleUnlocked(playerName, style))
                .toList();
    }
}
