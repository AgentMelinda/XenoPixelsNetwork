package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.FormConfig;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Mirrors DMZ's form movement, turbo, and attack-speed modifiers on profiled NPCs. */
public final class NpcFormAttributeSync {
    private static final ResourceLocation FORM_SPEED = id("npc_form_speed");
    private static final ResourceLocation TURBO_SPEED = id("npc_turbo_speed");
    private static final ResourceLocation FORM_ATTACK_SPEED = id("npc_form_attack_speed");

    private NpcFormAttributeSync() {}

    public static void apply(LivingEntity npc, NpcCombatProfile profile) {
        if (npc == null || profile == null || npc.level().isClientSide()) return;
        FormConfig.FormData normal = NpcFormLookup.activeForm(profile);
        FormConfig.FormData stack = NpcFormLookup.activeStackForm(profile);

        // DMZ applies normal-form movement speed only when it is a positive bonus.
        double formSpeed = normal == null ? 0.0 : Math.max(0.0, normal.getSpeedMultiplier() - 1.0);
        set(npc.getAttribute(Attributes.MOVEMENT_SPEED), FORM_SPEED, formSpeed);

        // DMZ calls this its turbo bonus: an active/permanent aura adds 30% movement speed.
        double turbo = NpcAuraFx.effectiveOn(npc, profile) ? 0.3 : 0.0;
        set(npc.getAttribute(Attributes.MOVEMENT_SPEED), TURBO_SPEED, turbo);

        double attackMultiplier = 1.0;
        if (normal != null) attackMultiplier *= normal.getAttackSpeed();
        if (stack != null) attackMultiplier *= stack.getAttackSpeed();
        set(npc.getAttribute(Attributes.ATTACK_SPEED), FORM_ATTACK_SPEED, attackMultiplier - 1.0);
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) return;
        AttributeModifier existing = attribute.getModifier(id);
        double current = existing == null ? 0.0 : existing.amount();
        if (Math.abs(current - amount) <= 1.0e-9) return;
        attribute.removeModifier(id);
        if (Math.abs(amount) > 1.0e-9) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, path);
    }
}
