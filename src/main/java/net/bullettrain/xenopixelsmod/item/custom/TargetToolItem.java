package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.aero.AeroControlHost;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.block.custom.PilotSeatBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.aero.AeroLinkManager;
import net.bullettrain.xenopixelsmod.block.entity.PilotSeatBlockEntity;
import net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * Opens the XYZ target GUI on the client only. Also doubles as the seat/panel/thruster linker:
 * right-click a wing panel or thruster to link it to the nearest chair (or a chair you
 * previously selected). Sitting is not required. Clicking a thruster toggles its link;
 * shift does not change the tool's behavior.
 *
 * <p><b>Shift, not Ctrl.</b> Vanilla Minecraft syncs sneaking ({@link Player#isShiftKeyDown()})
 * to the server for exactly this kind of modifier-click, because it is continuous player state
 * the server already needs for other mechanics. There is no equivalent for Ctrl anywhere in
 * vanilla's client-to-server protocol — building one would mean a bespoke key listener plus a
 * custom packet just for this one modifier, which is worse than reusing the modifier that
 * already exists and already does the job.
 * <p>
 * Must not reference {@code net.minecraft.client.*} directly — that crashes dedicated
 * servers at item registration ({@code NoClassDefFoundError: Screen}).
 */
public class TargetToolItem extends Item {
    private static final String TAG_HAS_TARGET = "HasTarget";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_TARGET_SHIP = "TargetShipId";
    private static final String TAG_LINKED_CHAIR = "LinkedChair";
    /** How far from a panel/thruster the tool searches to find which chair it is linked to, for unlinking. */
    private static final int UNLINK_SEARCH_RADIUS = 48;

    public TargetToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            ClientScreens.openTargetTool.run();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockEntity clickedEntity = level.getBlockEntity(clickedPos);

        if (clickedState.getBlock() instanceof PilotSeatBlock) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (player != null && player.isShiftKeyDown()) {
                CustomData.update(DataComponents.CUSTOM_DATA, context.getItemInHand(),
                        tag -> tag.remove(TAG_LINKED_CHAIR));
                player.displayClientMessage(Component.literal(
                        "§7Chair forgotten — next panel/thruster click will pick the nearest chair again"), true);
                return InteractionResult.CONSUME;
            }
            setStoredChair(context.getItemInHand(), clickedPos);
            AeroControlHost host = resolveHostForChair(level, clickedPos);
            if (player != null) player.displayClientMessage(Component.literal(
                    "§bChair selected " + linkSummary(host)
                            + " §7— right-click a wing panel or thruster to link it"
                            + " (click a thruster to toggle its link, or shift+right-click"
                            + " this chair to forget it)"), true);
            return InteractionResult.CONSUME;
        }
        if (clickedState.getBlock() instanceof WingPanelBlock) {
            return handlePanelLink(context, level, player, clickedPos);
        }
        if (clickedEntity instanceof ShipThrusterBlockEntity thruster) {
            return handleThrusterLink(context, level, player, clickedPos, thruster);
        }

        if (!(clickedEntity instanceof ShipVlsGuidanceBlockEntity guidance)) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            ServerSubLevel ship = VsShipHelper.getLoadedShipAtFast(server, context.getClickedPos());
            if (ship == null) ship = VsShipHelper.getLoadedShipAt(server, context.getClickedPos());
            if (ship == null) return InteractionResult.PASS;
            var position = VsShipHelper.worldPosition(ship);
            long shipId = VsShipHelper.getShipId(ship);
            setStoredMovingTarget(context.getItemInHand(), shipId,
                    BlockPos.containing(position.x(), position.y(), position.z()));
            if (player != null) player.displayClientMessage(Component.literal(
                    "§bMoving Sable target designated: sub-level#" + shipId), true);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos target = getStoredTarget(context.getItemInHand());
        if (target == null) {
            if (player != null) player.displayClientMessage(Component.translatable(
                    "chat.xenopixelsmod.target_tool_empty"), true);
            return InteractionResult.CONSUME;
        }

        long movingShipId = getStoredMovingTarget(context.getItemInHand());
        if (movingShipId >= 0) guidance.setMovingTarget(movingShipId, target);
        else guidance.setTargetWorld(target.getX(), target.getY(), target.getZ());
        guidance.recomputeSolution();
        int fleetSynced = guidance.broadcastFleetTarget();
        if (player != null) {
            String rangeError = guidance.rangeLimitMessage();
            if (rangeError != null) {
                player.displayClientMessage(Component.literal("§c" + rangeError), true);
            } else {
                player.displayClientMessage(Component.translatable(
                        "chat.xenopixelsmod.target_applied", target.getX(), target.getY(), target.getZ())
                        .append(fleetSynced > 0 ? Component.literal(" §7| fleet synced §f" + fleetSynced)
                                : Component.empty()), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    // --- Chair / panel / thruster linker ---

    private InteractionResult handlePanelLink(UseOnContext context, Level level, @Nullable Player player,
                                              BlockPos panelPos) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player != null && player.isShiftKeyDown()) {
            AeroControlHost host = findHostWithLinkedPanel(level, panelPos);
            if (host != null && host.unlinkPanel(panelPos)) {
                player.displayClientMessage(Component.literal("§7Panel unlinked " + linkSummary(host)), true);
            } else {
                player.displayClientMessage(Component.literal("§7That panel wasn't linked to anything"), true);
            }
            return InteractionResult.CONSUME;
        }
        AeroControlHost host = resolveLinkHost(context.getItemInHand(), level, panelPos, player);
        if (host == null) return InteractionResult.CONSUME;
        boolean added = host.linkPanel(panelPos);
        if (player != null) player.displayClientMessage(Component.literal(
                (added ? "§bPanel linked " : "§7Panel was already linked ") + linkSummary(host)), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult handleThrusterLink(UseOnContext context, Level level, @Nullable Player player,
                                                 BlockPos thrusterPos, ShipThrusterBlockEntity thruster) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos owner = thruster.getPairedGuidance();
        AeroControlHost host = resolveLinkHost(context.getItemInHand(), level, thrusterPos, player);
        if (host == null) return InteractionResult.CONSUME;

        if (owner != null) {
            if (!owner.equals(host.hostPos())) {
                if (player != null) player.displayClientMessage(Component.literal(
                        "§cThat thruster is already linked to another control host"), true);
                return InteractionResult.CONSUME;
            }
            if (host.unpairOneThruster(thrusterPos)) {
                if (player != null) player.displayClientMessage(Component.literal(
                        "§7Thruster unlinked " + linkSummary(host)), true);
            } else if (player != null) {
                player.displayClientMessage(Component.literal(
                        "§cThat thruster is not registered with its control host"), true);
            }
            return InteractionResult.CONSUME;
        }

        boolean added = host.pairOneThruster(thrusterPos);
        if (player != null) player.displayClientMessage(Component.literal(added
                ? "§bThruster linked " + linkSummary(host)
                : "§cNot a compatible engine, or already linked"), true);
        return InteractionResult.CONSUME;
    }

    /** "(N panels, M thrusters linked)" — real, current counts, not a fire-and-forget message. */
    private static String linkSummary(@Nullable AeroControlHost host) {
        if (host == null) return "";
        int panels = host.getLinkedPanels().size();
        int thrusters = host.getPairedThrusters().size();
        return "§7(" + panels + " panel" + (panels == 1 ? "" : "s") + ", "
                + thrusters + " thruster" + (thrusters == 1 ? "" : "s") + " linked)";
    }

    /**
     * Host to link against: stored chair if the tool was armed, otherwise the nearest
     * seated-control chair / flight controller. Standing is enough — sitting is not required.
     */
    private static @Nullable AeroControlHost resolveLinkHost(ItemStack stack, Level level,
                                                             BlockPos near, @Nullable Player player) {
        BlockPos chairPos = getStoredChair(stack);
        AeroControlHost host = chairPos == null ? null : resolveHostForChair(level, chairPos);
        if (host == null) {
            chairPos = findNearestChair(level, near);
            host = chairPos == null ? null : resolveHostForChair(level, chairPos);
            if (host != null) setStoredChair(stack, chairPos);
        }
        if (host == null) {
            if (player != null) player.displayClientMessage(Component.literal(
                    "§cNo control chair nearby — place one on the hull, then click the panel again"), true);
            return null;
        }
        return host;
    }

    private static final int CHAIR_SEARCH_RADIUS = 32;

    private static @Nullable BlockPos findNearestChair(Level level, BlockPos origin) {
        BlockPos[] best = new BlockPos[1];
        double[] bestDist = {Double.MAX_VALUE};
        // Every pilot seat has a block entity, so the chunk block-entity walk finds them all
        // without touching the 274,625 positions a radius-32 cube sweep would have visited.
        AeroLinkManager.forEachNearbyBlockEntity(level, origin, CHAIR_SEARCH_RADIUS,
                (pos, blockEntity) -> {
                    if (!(blockEntity instanceof PilotSeatBlockEntity)) return;
                    double d = pos.distSqr(origin);
                    if (d < bestDist[0]) {
                        bestDist[0] = d;
                        best[0] = pos.immutable();
                    }
                });
        return best[0];
    }

    /**
     * The control host a stored chair position actually represents — the nearby flight
     * controller if the seat is bound to one (matching {@link XenoPilotSeatEntity}'s own
     * bound/standalone rule exactly, so the tool never links a panel to a host the seat itself
     * would not be flying), otherwise the seat block's own standalone host.
     */
    private static @Nullable AeroControlHost resolveHostForChair(Level level, BlockPos chairPos) {
        BlockPos controllerPos = XenoPilotSeatEntity.findController(level, chairPos);
        if (controllerPos != null
                && level.getBlockEntity(controllerPos) instanceof ShipVlsGuidanceBlockEntity guidance) {
            return guidance;
        }
        return level.getBlockEntity(chairPos) instanceof PilotSeatBlockEntity seat ? seat : null;
    }

    /**
     * Bounded search for whichever nearby host currently has this panel linked, for unlinking.
     * Panels have no reverse pointer to their own linking host (unlike thrusters, which store
     * their owner directly), so this scans a generous but bounded radius rather than the whole
     * loaded world — only run on an explicit shift+right-click, never per tick.
     */
    private static @Nullable AeroControlHost findHostWithLinkedPanel(Level level, BlockPos panelPos) {
        AeroControlHost[] found = new AeroControlHost[1];
        AeroLinkManager.forEachNearbyBlockEntity(level, panelPos, UNLINK_SEARCH_RADIUS,
                (pos, blockEntity) -> {
                    if (found[0] != null) return;
                    if (!(blockEntity instanceof AeroControlHost host)) return;
                    if (host.getLinkedPanels().contains(panelPos)) found[0] = host;
                });
        return found[0];
    }

    public static void setStoredChair(ItemStack stack, BlockPos chair) {
        if (stack == null || stack.isEmpty() || chair == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_LINKED_CHAIR, chair.asLong()));
    }

    public static @Nullable BlockPos getStoredChair(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_LINKED_CHAIR) ? BlockPos.of(tag.getLong(TAG_LINKED_CHAIR)) : null;
    }

    public static void setStoredTarget(ItemStack stack, BlockPos target) {
        if (stack == null || stack.isEmpty() || target == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean(TAG_HAS_TARGET, true);
            tag.putLong(TAG_TARGET, target.asLong());
            tag.remove(TAG_TARGET_SHIP);
        });
    }

    public static void setStoredMovingTarget(ItemStack stack, long shipId, BlockPos lastKnown) {
        if (stack == null || stack.isEmpty() || shipId < 0 || lastKnown == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean(TAG_HAS_TARGET, true);
            tag.putLong(TAG_TARGET, lastKnown.asLong());
            tag.putLong(TAG_TARGET_SHIP, shipId);
        });
    }

    public static long getStoredMovingTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1L;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_TARGET_SHIP) ? tag.getLong(TAG_TARGET_SHIP) : -1L;
    }

    public static BlockPos getStoredTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(TAG_HAS_TARGET) || !tag.contains(TAG_TARGET)) return null;
        return BlockPos.of(tag.getLong(TAG_TARGET));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockPos target = getStoredTarget(stack);
        if (target == null) {
            tooltip.add(Component.translatable("tooltip.xenopixelsmod.target_tool_empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            long moving = getStoredMovingTarget(stack);
            if (moving >= 0) tooltip.add(Component.literal("Moving Sable sub-level #" + moving)
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.xenopixelsmod.target_tool_target",
                            target.getX(), target.getY(), target.getZ())
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.xenopixelsmod.target_tool_apply")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
