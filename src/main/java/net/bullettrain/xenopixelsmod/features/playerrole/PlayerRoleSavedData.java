package net.bullettrain.xenopixelsmod.features.playerrole;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Overworld SavedData mapping player UUID → {@link PlayerRoleId}.
 *
 * <p>Missing / empty entries read as {@link PlayerRoleId#NONE}. Setting {@code NONE} removes the
 * entry. Pattern mirrors {@link net.bullettrain.xenopixelsmod.block.entity.FleetChannelSavedData}.
 */
public final class PlayerRoleSavedData extends SavedData {
    public static final String FILE_NAME = "xenopixels_player_roles";
    private static final String ROLES_KEY = "Roles";
    private static final String UUID_KEY = "UUID";
    private static final String ROLE_KEY = "Role";

    private final Map<UUID, PlayerRoleId> roles = new HashMap<>();

    public static PlayerRoleSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(PlayerRoleSavedData::new, PlayerRoleSavedData::load), FILE_NAME);
    }

    public static PlayerRoleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerRoleSavedData result = new PlayerRoleSavedData();
        if (tag == null) return result;
        ListTag list = tag.getList(ROLES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID(UUID_KEY)) continue;
            PlayerRoleId role = PlayerRoleId.parse(entry.getString(ROLE_KEY));
            if (role == PlayerRoleId.NONE) continue;
            result.roles.put(entry.getUUID(UUID_KEY), role);
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerRoleId> value : roles.entrySet()) {
            if (value.getValue() == null || value.getValue() == PlayerRoleId.NONE) continue;
            CompoundTag entry = new CompoundTag();
            entry.putUUID(UUID_KEY, value.getKey());
            entry.putString(ROLE_KEY, value.getValue().id());
            list.add(entry);
        }
        tag.put(ROLES_KEY, list);
        return tag;
    }

    public PlayerRoleId roleOf(UUID id) {
        if (id == null) return PlayerRoleId.NONE;
        PlayerRoleId role = roles.get(id);
        return role == null ? PlayerRoleId.NONE : role;
    }

    /** Sets the role and marks dirty. {@link PlayerRoleId#NONE} clears the entry. */
    public void setRole(UUID id, PlayerRoleId role) {
        if (id == null) return;
        PlayerRoleId next = role == null ? PlayerRoleId.NONE : role;
        if (next == PlayerRoleId.NONE) {
            if (roles.remove(id) != null) setDirty();
            return;
        }
        PlayerRoleId previous = roles.put(id, next);
        if (previous != next) setDirty();
    }
}
