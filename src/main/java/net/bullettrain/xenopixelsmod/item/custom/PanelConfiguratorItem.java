package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.block.custom.PanelRole;
import net.bullettrain.xenopixelsmod.block.custom.WingFlapHorizontalBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingFlapVerticalBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.entity.WingPanelBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Panel configurator: right-click a {@link WingPanelBlock} to cycle through its role and
 * deflection sign as one flat sequence — {@code NONE → FLAP+ → FLAP- → PITCH+ → PITCH- → ROLL+ →
 * ROLL- → YAW+ → YAW- → BRAKE+ → BRAKE- → NONE}. Works while standing — linking and role
 * assignment are build-time tools, not cockpit actions.
 *
 * <p>Every role gets a +/- counterpart this way, not just {@code ROLE=ROLL} (which also
 * auto-mirrors by hull position in {@link net.bullettrain.xenopixelsmod.aero.control.AeroFlightCore}
 * — the sign here still applies on top of that, as an override). No modifier key is needed; a
 * plain right-click always advances one step. {@link PanelRole}/{@link WingPanelBlock#INVERT} are
 * still two separate blockstate properties underneath — this only changes how one click steps
 * through their combined states.
 *
 * <p>Purely a role assignment. The panel's real aerodynamic contribution — Sable's own per-block
 * lift/drag pass — is identical regardless of role; this only decides what the panel visually
 * does in response to {@link net.bullettrain.xenopixelsmod.aero.control.AeroFlightCore}.
 *
 * <p>Also corrects {@code AXIS} to whatever the new role needs — see
 * {@link PanelRole#needsHorizontalMount()} — since a flap/elevator/aileron mounted on its edge
 * cannot tilt no matter what angle it is told to hold, the same way a real vertical fin cannot
 * become a horizontal stabilizer by turning it; a builder should not have to know that and get it
 * right by hand every time.
 *
 * <p>Sneak-right-click a panel instead of a plain click to print its actual computed swing
 * instead of cycling role — see {@link #swingCorner}.
 */
public class PanelConfiguratorItem extends Item {

    public PanelConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WingPanelBlock)) return InteractionResult.PASS;

        // Sneak-click: report the renderer's actual swing direction instead of cycling role. This
        // replicates WingPanelBlockEntityRenderer's own transform, call for call, against a real
        // JOML Matrix4f — same math, same composition order — so what it prints is exactly what
        // that renderer's PoseStack produces, not what anyone (including past edits to this class)
        // guessed it should produce. Read-only: does not need the level to be server-side, and
        // does not touch the block.
        if (player != null && player.isShiftKeyDown()) {
            if (level.isClientSide) {
                Direction.Axis axis = state.getValue(WingPanelBlock.AXIS);
                Vector4f zero = swingCorner(axis, 0.0);
                Vector4f test = swingCorner(axis, 30.0);
                double dx = test.x() - zero.x();
                double dy = test.y() - zero.y();
                double dz = test.z() - zero.z();
                player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,
                        "§bSwing @30°: §fΔx=%.3f Δy=%.3f Δz=%.3f §7(local block space, |dominant| tells the real axis)",
                        dx, dy, dz)), false);
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        PanelRole role = state.getValue(WingPanelBlock.ROLE);
        boolean invert = state.getValue(WingPanelBlock.INVERT);
        PanelRole nextRole;
        boolean nextInvert;
        if (role == PanelRole.NONE) {
            // First non-NONE entry, always "+".
            nextRole = PanelRole.values()[1];
            nextInvert = false;
        } else if (!invert) {
            // role+ -> role-, same role.
            nextRole = role;
            nextInvert = true;
        } else {
            // role- -> next role's "+", or back to NONE after the last role.
            PanelRole[] all = PanelRole.values();
            int idx = role.ordinal() + 1;
            nextRole = idx < all.length ? all[idx] : PanelRole.NONE;
            nextInvert = false;
        }

        // A role assignment carries a mounting requirement with it (see
        // PanelRole#needsHorizontalMount) — a flap/elevator/aileron cannot tilt up-down on
        // anything but a flat, AXIS=Y panel, no matter what the renderer does with the angle, so
        // this corrects the axis right here instead of leaving a builder to rediscover that the
        // hard way. YAW is the opposite: it is supposed to swing, so an axis that is already
        // vertical (X or Z) is left alone — only a flat one gets pushed off Y, arbitrarily to Z,
        // since either vertical orientation swings correctly and there is no "wrong" one to avoid.
        //
        // WingFlapHorizontalBlock/WingFlapVerticalBlock exist specifically to make that mounting
        // permanent and unambiguous — overriding AXIS here would fight the entire point of
        // reaching for one of those instead of a plain WingPanelBlock, so they are left alone.
        Direction.Axis axis = state.getValue(WingPanelBlock.AXIS);
        Direction.Axis nextAxis = axis;
        boolean fixedMount = state.getBlock() instanceof WingFlapHorizontalBlock
                || state.getBlock() instanceof WingFlapVerticalBlock;
        if (!fixedMount && nextRole.needsHorizontalMount() && axis != Direction.Axis.Y) {
            nextAxis = Direction.Axis.Y;
        } else if (!fixedMount && nextRole == PanelRole.YAW && axis == Direction.Axis.Y) {
            nextAxis = Direction.Axis.Z;
        }

        level.setBlock(pos, state.setValue(WingPanelBlock.ROLE, nextRole)
                .setValue(WingPanelBlock.INVERT, nextInvert)
                .setValue(WingPanelBlock.AXIS, nextAxis), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof WingPanelBlockEntity panel) {
            panel.setTargetDeflectDeg(0.0);
        }
        // A dispenser or any other automated use has no player behind it.
        if (player != null) {
            String message = "§bPanel role: §f" + label(nextRole, nextInvert);
            if (nextAxis != axis) {
                message += " §7(mount switched to " + nextAxis.getSerializedName().toUpperCase(java.util.Locale.ROOT)
                        + " — " + (nextRole.needsHorizontalMount() ? "needs to tilt up/down" : "rudders swing, not tilt") + ")";
            }
            player.displayClientMessage(Component.literal(message), true);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Replays {@code WingPanelBlockEntityRenderer.render}'s own pose-stack sequence against a
     * plain JOML matrix, call-for-call in the same order, and returns where the panel's far
     * corner (the free edge, opposite the hinge) ends up in block-local space for a given
     * deflection angle. Deliberately duplicates the renderer's transform rather than calling into
     * it, since the renderer needs a live {@code BakedModel}/{@code MultiBufferSource} this item
     * does not have — this only needs the matrix, which is exactly what {@code Matrix4f.translate}
     * / {@code .rotateX} build the same way {@code PoseStack.translate}/{@code .mulPose} do.
     */
    private static Vector4f swingCorner(Direction.Axis axis, double degrees) {
        Matrix4f m = new Matrix4f();
        m.translate(0.5f, 0.5f, 0.5f);
        switch (axis) {
            case Z -> m.rotateX((float) Math.toRadians(90));
            case X -> {
                m.rotateY((float) Math.toRadians(90));
                m.rotateX((float) Math.toRadians(90));
            }
            default -> {
            }
        }
        m.translate(0f, 0f, -0.5f);
        m.rotateX((float) Math.toRadians(degrees));
        m.translate(-0.5f, -0.5f, 0f);
        // Far corner: full X, thin-panel Y, and the free (non-hinge) edge in Z.
        return m.transform(new Vector4f(1.0f, 0.59375f, 1.0f, 1.0f));
    }

    /** "NONE" for no role, otherwise the role name with its sign, e.g. "ROLL+"/"ROLL-". */
    private static String label(PanelRole role, boolean invert) {
        if (role == PanelRole.NONE) return role.getSerializedName().toUpperCase(java.util.Locale.ROOT);
        return role.getSerializedName().toUpperCase(java.util.Locale.ROOT) + (invert ? "-" : "+");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click a wing panel to cycle role and direction")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("NONE → FLAP+ → FLAP- → PITCH+ → PITCH- → ROLL+ → ROLL- → YAW+ → YAW- → BRAKE+ → BRAKE-")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Works while standing. Link panels with the ship target tool.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Auto-corrects mount axis per role. Sneak-click: print swing debug.")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
