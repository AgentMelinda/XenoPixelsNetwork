package net.bullettrain.xenopixelsmod.compat.customnpcs.role;

import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMasterInteraction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleInterface;

/**
 * CustomNPCs role that turns an NPC into a DragonMineZ skill master.
 *
 * <p>Constructed only from the {@code DataAdvanced.setRole} injection when the raw role id equals
 * {@link DmzSkillMaster#SENTINEL_ROLE_ID}. The role itself is stateless: the menu title, body text
 * and offered forms all live in {@link net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadata},
 * keyed by the NPC's UUID, so the editor and the in-game menu can never disagree.
 *
 * <p>{@link #getType()} returns the sentinel so the NPC round-trips through CustomNPCs' own
 * {@code putInt("Role", role.getType())} / {@code setRole(tag.getInt("Role"))} save path. If this
 * mod is later removed the raw id degrades to {@code RoleInterface.NONE} rather than aliasing a
 * native role, because the native chain normalizes every id into {@code 0..7}.
 */
public class RoleDmzSkillMaster extends RoleInterface {

    public RoleDmzSkillMaster(EntityNPCInterface npc) {
        super(npc);
    }

    /** No role-owned state: all master data lives in the XenoPixels metadata registry. */
    @Override
    public CompoundTag save(CompoundTag tag) {
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
    }

    @Override
    public void interact(Player player) {
        DmzSkillMasterInteraction.interact(npc, player);
    }

    @Override
    public int getType() {
        return DmzSkillMaster.SENTINEL_ROLE_ID;
    }
}