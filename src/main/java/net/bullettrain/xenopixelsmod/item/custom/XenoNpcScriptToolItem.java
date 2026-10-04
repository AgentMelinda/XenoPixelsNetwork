package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcScriptPacket;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The scripting tool: My NPCs opens its script GUI from the NPC editor; Xeno additionally ships a
 * dedicated wand so an operator can walk up to an NPC and press the script screen directly. Like
 * the editor wand it opens a screen rather than acting on the NPC, so it is deliberately not a
 * {@link XenoNpcTool}: the open is server-authoritative (op 2, range) exactly the way the editor
 * open is, and the tool does nothing to an NPC it cannot prove the player may edit.
 */
public final class XenoNpcScriptToolItem extends Item {
    public XenoNpcScriptToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // In air: the global script menu (Player / Forge / library), as MyNPCs' scripter. The
        // screen is only a view; every save is rechecked by the store write packet (level 4).
        if (level.isClientSide()) {
            if (player.hasPermissions(2)) {
                net.bullettrain.xenopixelsmod.client.ClientScreens.openScriptHub.run();
            }
        } else if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(
                    "You need operator permission to edit Xeno scripts."));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        // Same ordering note as the wand: XenoNpcEntity.mobInteract consumes first, so this is the
        // fallback if that ever changes. Only the server branch can do anything real.
        if (target instanceof XenoNpcEntity npc) {
            if (player.level().isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer) || !player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(
                        "You need operator permission to edit Xeno NPC scripts."));
                return InteractionResult.FAIL;
            }
            ModNetwork.sendToPlayer(serverPlayer, new OpenXenoNpcScriptPacket(npc.getId(), npc.scriptOpenPayload()));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                 TooltipFlag flag) {
        tooltip.add(Component.literal("Use on a Xeno NPC: open its script editor"));
        tooltip.add(Component.literal("Use in the air: Player, Forge and library scripts"));
        tooltip.add(Component.literal("Tabs, loaded scripts and hooks run on that NPC"));
    }
}
