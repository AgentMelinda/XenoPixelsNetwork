package net.bullettrain.xenopixelsmod.client.gui;

import net.bullettrain.xenopixelsmod.item.custom.TargetToolItem;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.TeleportShipPacket;
import net.bullettrain.xenopixelsmod.network.packet.SetTargetToolPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Simple XYZ input GUI.
 * - Set Target: persists coordinates on the held Target Tool through the server.
 * - Teleport Ship: sends a packet to the server to teleport the VS2 ship the player is standing on.
 */
public class TargetScreen extends Screen {
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;

    private double storedX;
    private double storedY;
    private double storedZ;
    private boolean hasStoredTarget = false;

    public TargetScreen() {
        super(Component.translatable("gui.xenopixelsmod.target_title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // X field
        this.xBox = new EditBox(this.font, centerX - 100, centerY - 40, 60, 20,
                Component.literal("X"));
        this.xBox.setMaxLength(12);
        BlockPos initial = initialTarget();
        this.xBox.setValue(Integer.toString(initial.getX()));
        this.addRenderableWidget(this.xBox);

        // Y field
        this.yBox = new EditBox(this.font, centerX - 30, centerY - 40, 60, 20,
                Component.literal("Y"));
        this.yBox.setMaxLength(12);
        this.yBox.setValue(Integer.toString(initial.getY()));
        this.addRenderableWidget(this.yBox);

        // Z field
        this.zBox = new EditBox(this.font, centerX + 40, centerY - 40, 60, 20,
                Component.literal("Z"));
        this.zBox.setMaxLength(12);
        this.zBox.setValue(Integer.toString(initial.getZ()));
        this.addRenderableWidget(this.zBox);

        // Set Target button
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.xenopixelsmod.set_target"),
                        btn -> onSetTarget())
                .bounds(centerX - 100, centerY, 95, 20)
                .build());

        // Teleport Ship button
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.xenopixelsmod.teleport_ship"),
                        btn -> onTeleportShip())
                .bounds(centerX + 5, centerY, 95, 20)
                .build());

        // Cancel / Close
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        btn -> this.onClose())
                .bounds(centerX - 50, centerY + 30, 100, 20)
                .build());
    }

    private BlockPos initialTarget() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return new BlockPos(0, 64, 0);
        }

        ItemStack mainHand = this.minecraft.player.getMainHandItem();
        ItemStack targetTool = mainHand.getItem() instanceof TargetToolItem
                ? mainHand : this.minecraft.player.getOffhandItem();
        if (targetTool.getItem() instanceof TargetToolItem) {
            BlockPos saved = TargetToolItem.getStoredTarget(targetTool);
            if (saved != null) {
                storedX = saved.getX();
                storedY = saved.getY();
                storedZ = saved.getZ();
                hasStoredTarget = true;
                return saved;
            }
        }
        return this.minecraft.player.blockPosition();
    }

    private void onSetTarget() {
        try {
            storedX = Double.parseDouble(xBox.getValue().trim());
            storedY = Double.parseDouble(yBox.getValue().trim());
            storedZ = Double.parseDouble(zBox.getValue().trim());
            if (!Double.isFinite(storedX) || !Double.isFinite(storedY) || !Double.isFinite(storedZ)) {
                throw new NumberFormatException("coordinates must be finite");
            }
            hasStoredTarget = true;

            BlockPos target = BlockPos.containing(storedX, storedY, storedZ);
            storedX = target.getX();
            storedY = target.getY();
            storedZ = target.getZ();
            ModNetwork.sendToServer(new SetTargetToolPacket(
                    target.getX(), target.getY(), target.getZ()));
        } catch (NumberFormatException e) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable("chat.xenopixelsmod.invalid_coords"),
                        true);
            }
        }
    }

    private void onTeleportShip() {
        try {
            double x = Double.parseDouble(xBox.getValue().trim());
            double y = Double.parseDouble(yBox.getValue().trim());
            double z = Double.parseDouble(zBox.getValue().trim());

            // Send packet to server – server will resolve the ship the player is on
            ModNetwork.sendToServer(new TeleportShipPacket(x, y, z));
            this.onClose();
        } catch (NumberFormatException e) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable("chat.xenopixelsmod.invalid_coords"),
                        true);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Title
        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 70, 0xFFFFFF);

        // Labels
        graphics.drawString(this.font, "X", centerX - 100, centerY - 52, 0xA0A0A0);
        graphics.drawString(this.font, "Y", centerX - 30, centerY - 52, 0xA0A0A0);
        graphics.drawString(this.font, "Z", centerX + 40, centerY - 52, 0xA0A0A0);

        if (hasStoredTarget) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.xenopixelsmod.stored_target",
                            (int) storedX,
                            (int) storedY,
                            (int) storedZ),
                    centerX, centerY + 55, 0x55FF55);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
