package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcEditorPacket;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public final class XenoNpcWandItem extends Item {
    public XenoNpcWandItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            XenoNpcRole next = selectedRole(stack).next();
            setSelectedRole(stack, next);
            if (!level.isClientSide()) player.sendSystemMessage(Component.literal(
                    "Next Xeno NPC type: " + next.label()));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        // Right-click the air opens the Nearby NPCs screen; right-click a block places an NPC (useOn).
        if (level.isClientSide()) {
            ModNetwork.sendToServer(new net.bullettrain.xenopixelsmod.network.packet.XenoNpcNearbyPackets.Request());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Right-click a block: place an NPC of the selected type standing on it, facing you (op 2).
     * Sneak-right-click still cycles the type, as in the air.
     */
    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        if (player.isShiftKeyDown()) {
            XenoNpcRole next = selectedRole(stack).next();
            setSelectedRole(stack, next);
            if (!level.isClientSide()) player.sendSystemMessage(Component.literal("Next Xeno NPC type: " + next.label()));
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("You need operator permission to place Xeno NPCs."));
            return InteractionResult.FAIL;
        }
        XenoNpcRole role = selectedRole(stack);
        XenoNpcEntity npc = net.bullettrain.xenopixelsmod.npc.XenoNpcNearbyService.spawnAt(serverPlayer, role,
                net.bullettrain.xenopixelsmod.npc.XenoNpcNearbyService.spawnPositionOn(context.getClickedPos(),
                        context.getClickedFace()),
                net.bullettrain.xenopixelsmod.npc.XenoNpcNearbyService.facingPlacer(player.getYRot()));
        if (npc == null) {
            player.sendSystemMessage(Component.literal("Could not place a Xeno NPC there."));
            return InteractionResult.FAIL;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        // In practice this branch does not run for a Xeno NPC: vanilla calls the entity's
        // mobInteract first and returns early once it consumes, and XenoNpcEntity.mobInteract
        // always consumes. It is kept as the fallback for the case that ordering ever changes,
        // and because it is the only path that works on entities whose mobInteract passes.
        if (target instanceof XenoNpcEntity npc) {
            if (player.level().isClientSide()) {
                ModNetwork.sendToServer(new net.bullettrain.xenopixelsmod.network.packet.RequestXenoNpcEditorPacket(npc.getId()));
                return InteractionResult.SUCCESS;
            }
            if (!(player instanceof ServerPlayer serverPlayer) || !player.hasPermissions(2)) {
                if (!player.level().isClientSide()) {
                    player.sendSystemMessage(Component.literal("You need operator permission to edit Xeno NPCs."));
                }
                return InteractionResult.FAIL;
            }
            net.bullettrain.xenopixelsmod.compat.npc.NpcProfileWatchers.watch(serverPlayer, npc);
            // editorPayload carries the NPC's DMZ profile; npcData alone opened the editor on defaults.
            ModNetwork.sendToPlayer(serverPlayer, new OpenXenoNpcEditorPacket(npc.getId(),
                    net.bullettrain.xenopixelsmod.npc.XenoNpcData.editorPayload(npc, npc.npcData())));
            return InteractionResult.CONSUME;
        }
        if (player.level().isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer) || !player.hasPermissions(2)) return InteractionResult.FAIL;
        String sourceMod = legacySource(target);
        if (sourceMod.isEmpty()) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.literal("Import preview: " + target.getName().getString()
                    + " from " + sourceMod + ". Sneak-use again to create a separate Xeno NPC."));
            return InteractionResult.CONSUME;
        }
        ServerLevel level = serverPlayer.serverLevel();
        XenoNpcEntity imported = ModEntities.XENO_NPC_HUMANOID.get().create(level);
        if (imported == null) return InteractionResult.FAIL;
        imported.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        imported.npcData().setOwner(player.getUUID());
        imported.npcData().setHome(imported.getX(), imported.getY(), imported.getZ());
        imported.npcData().setDisplayName(target.getName().getString());
        imported.npcData().setImportSource(sourceMod, target.getUUID());
        imported.refreshNameplate();
        NpcCombatProfile.read(target).write(imported);
        level.addFreshEntity(imported);
        player.sendSystemMessage(Component.literal("Imported a new Xeno NPC; the original was not changed."));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Next type: " + selectedRole(stack).label()));
        tooltip.add(Component.literal("Use block: place an NPC of the next type on it"));
        tooltip.add(Component.literal("Use air: Nearby NPCs (edit, teleport, reset, freeze)"));
        tooltip.add(Component.literal("Sneak-use: choose the type"));
        tooltip.add(Component.literal("Use NPC: edit · Sneak-use legacy NPC: confirm import"));
    }

    /** The type chosen with sneak-use; the Nearby NPCs screen starts its Spawn type from it. */
    public static XenoNpcRole selectedRoleOf(ItemStack stack) {
        return selectedRole(stack);
    }

    private static XenoNpcRole selectedRole(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return XenoNpcWandRoleSelection.current(tag);
    }

    private static void setSelectedRole(ItemStack stack, XenoNpcRole role) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> XenoNpcWandRoleSelection.select(tag, role));
    }

    private static void clearSelectedRole(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> XenoNpcWandRoleSelection.consume(tag));
    }

    private static String legacySource(LivingEntity entity) {
        String name = entity.getClass().getName();
        if (name.startsWith("espi.mynpcs.")) return "mynpcs";
        if (name.startsWith("noppes.npcs.")) return "customnpcs";
        return "";
    }
}
