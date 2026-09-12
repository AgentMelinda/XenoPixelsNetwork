package net.bullettrain.xenopixelsmod.compat.mynpcs.role;

import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMasterInteraction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.roles.RoleInterface;

/**
 * My NPCs twin of {@code compat.customnpcs.role.RoleDmzSkillMaster}.
 *
 * <p>My NPCs has no role-id normalization, so the sentinel reaches this class unchanged, but the
 * injection point is still {@code HEAD} of {@code DataAdvanced.setRole} so both trees stay
 * byte-for-byte parallel. Behaviour and stored data are identical to the CustomNPCs role.
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