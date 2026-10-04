package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcData;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Copies a configured NPC and places it again somewhere else.
 *
 * <p>My NPCs' Mob Cloner, and the same shape: pick one up, put copies down. Configuring an NPC is
 * the expensive part — appearance, stats, dialogue, trades, a patrol route — and a village of eight
 * guards should be that work once rather than eight times.
 *
 * <p>The copy is the <b>whole</b> NPC: {@link XenoNpcData#editorPayload} is the same payload the
 * editor screen is opened with, so anything the editor can change comes across. That is deliberate
 * rather than a curated subset — a subset is a list that silently falls behind every time a field
 * is added, which is exactly how a clone ends up subtly different from its original.
 *
 * <p><b>Three things are not copied</b>, because copying them would be wrong rather than thorough:
 *
 * <ul>
 *   <li><b>Home.</b> It is reset to where the clone is placed. Carried over, every clone would walk
 *       back to the original's spot the moment the leash checked - which reads as the tool being
 *       broken.
 *   <li><b>Owner.</b> Set to whoever placed the clone, not whoever owned the original.
 *   <li><b>Provenance.</b> An NPC imported from My NPCs has a source id; its clone was made here,
 *       and saying otherwise would confuse a later migration.
 * </ul>
 *
 * <p>Built on {@link XenoNpcWandItem}'s shape, including its operator check. <b>Everything is
 * decided on the server</b>: the stack carries a payload, and the server re-checks the permission
 * and bounds it before creating anything.
 */
public final class XenoNpcClonerItem extends Item implements XenoNpcTool {

    public XenoNpcClonerItem(Properties properties) {
        super(properties);
    }

    /** Right-click an NPC to copy it. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof XenoNpcEntity npc)) {
            return InteractionResult.PASS;
        }
        return copy(stack, player, npc);
    }

    /** The NPC asks this before its own interaction consumes the click. */
    @Override
    public InteractionResult useOnNpc(ItemStack stack, Player player, XenoNpcEntity npc) {
        return copy(stack, player, npc);
    }

    /** Copies one NPC onto the stack. */
    public static InteractionResult copy(ItemStack stack, Player player, XenoNpcEntity npc) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(
                    "You need operator permission to clone Xeno NPCs."));
            return InteractionResult.FAIL;
        }

        String refusal = XenoNpcPayload.store(stack, npc);
        if (refusal != null) {
            player.sendSystemMessage(Component.literal("§7" + refusal));
            return InteractionResult.FAIL;
        }
        player.sendSystemMessage(Component.literal("§bCopied §f"
                + npc.getName().getString()
                + "§b — right-click a block to place one."));
        return InteractionResult.CONSUME;
    }

    /** Right-click a block to place a copy on top of it. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        CompoundTag payload = XenoNpcPayload.read(stack);
        if (payload == null) {
            player.sendSystemMessage(Component.literal(
                    "§7Right-click an NPC with this first to copy it."));
            return InteractionResult.CONSUME;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.CONSUME;
        }

        // On top of the clicked face, so clicking the ground puts the NPC on it rather than inside.
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        XenoNpcRole role = XenoNpcRole.byId(payload.getString("Role"));
        XenoNpcEntity clone = ModEntities.xenoNpcType(role).create(server);
        if (clone == null) {
            return InteractionResult.CONSUME;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        clone.moveTo(x, y, z, player.getYRot(), 0.0f);
        // keepOwner false: a clone is a new NPC, and it belongs to whoever placed it.
        XenoNpcPayload.apply(clone, payload, player, x, y, z, false);

        if (!server.addFreshEntity(clone)) {
            return InteractionResult.CONSUME;
        }
        player.sendSystemMessage(Component.literal("§bPlaced a copy of §f"
                + clone.getName().getString()));
        return InteractionResult.CONSUME;
    }

    /** Use in air: say what is held. Sneak-use in air: forget it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!player.hasPermissions(2)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            XenoNpcPayload.clear(stack);
            player.sendSystemMessage(Component.literal("§fCleared the copy."));
            return InteractionResultHolder.success(stack);
        }
        player.sendSystemMessage(Component.literal(XenoNpcPayload.present(stack)
                ? "§bHolding a copy of §f" + XenoNpcPayload.label(stack)
                : "§7Nothing copied yet."));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        String label = XenoNpcPayload.label(stack);
        tooltip.add(Component.literal(label.isEmpty() ? "Empty" : "Holding: " + label));
        tooltip.add(Component.literal("Use NPC: copy · Use block: place a copy"));
        tooltip.add(Component.literal("Sneak-use air: clear. The copy keeps everything"));
        tooltip.add(Component.literal("but its home, owner and origin."));
    }

}
