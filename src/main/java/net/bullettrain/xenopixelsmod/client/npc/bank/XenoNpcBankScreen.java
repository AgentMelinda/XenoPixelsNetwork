package net.bullettrain.xenopixelsmod.client.npc.bank;

import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTabStrip;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcBankPacket;
import net.bullettrain.xenopixelsmod.npc.bank.XenoNpcBankMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * The bank vault, in the same blue frame as the NPC editor.
 *
 * <p>Two panels side by side, both blitted at their shipped size: the 176x222
 * {@code mynpcs_small_panel} carries the slot grid and the player's inventory at vanilla's own
 * geometry, and {@code panel_tall} beside it carries the tab strip and the buttons. Nothing is
 * stretched and nothing new was generated — the reason a six-row grid is drawn even when one row
 * is unlocked is that 176x222 <em>is</em> a six-row container, so one sprite covers every state.
 *
 * <p>Locked slots are drawn, dimmed and labelled with what they would cost. Showing them is the
 * point: a vault that simply looked small would give a player no reason to think it could grow.
 *
 * <p>Cash exchange always works against the bank vault. Wallet exchange appears only when the
 * optional economy is installed; the mode button keeps both sets of controls in the same panel.
 */
public class XenoNpcBankScreen extends AbstractContainerScreen<XenoNpcBankMenu> {

    private static final String MAIN_PANEL = "mynpcs_small_panel";
    private static final String SIDE_PANEL = "panel_tall";
    private static final String SLOT = "icon_slot_sm";
    private static final String BUTTON = "mynpcs_button_row_w128";

    /** Gap between the two panels. Enough to read as two frames rather than one seam. */
    private static final int PANEL_GAP = 4;

    private static final int SIDE_INSET = 6;
    private static final int LOCKED_TINT = 0xA0101820;

    private final int sidePanelX;
    private EditBox amountBox;
    private boolean cashMode = true;
    private AtlasButton modeButton;
    private AtlasButton depositButton;
    private AtlasButton withdrawButton;

    public XenoNpcBankScreen(XenoNpcBankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = XenoAtlasSprites.get(MAIN_PANEL).width() + PANEL_GAP
                + XenoAtlasSprites.get(SIDE_PANEL).width();
        this.imageHeight = XenoAtlasSprites.get(MAIN_PANEL).height();
        this.sidePanelX = XenoAtlasSprites.get(MAIN_PANEL).width() + PANEL_GAP;
        // The labels sit on the left panel, which is only part of the total width; without this
        // they centre themselves against the whole composite and drift over the side panel.
        this.titleLabelY = 6;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + sidePanelX + SIDE_INSET;
        int y = topPos + 34;
        int step = AtlasButton.nativeHeight(BUTTON) + 4;

        if (menu.tabCount() > 1) {
            List<Component> labels = new ArrayList<>();
            for (int tab = 0; tab < menu.tabCount(); tab++) {
                labels.add(Component.literal("" + (tab + 1)));
            }
            addRenderableWidget(new AtlasTabStrip(x, topPos + 6, labels, menu.tabIndex(),
                    this::selectTab, XenoAtlasSprites.get(SIDE_PANEL).width() - SIDE_INSET * 2,
                    -1));
        }

        if (menu.unlockedSlots() == 0) {
            addRenderableWidget(new AtlasButton(x, y, Component.literal("Unlock"), BUTTON,
                    button -> send(XenoNpcBankPacket.ACTION_UNLOCK, 0L)));
            y += step;
        } else if (menu.hasLockedSlots()) {
            addRenderableWidget(new AtlasButton(x, y, Component.literal("Upgrade"), BUTTON,
                    button -> send(XenoNpcBankPacket.ACTION_UPGRADE, 0L)));
            y += step;
        }

        if (menu.moneyAvailable()) {
            modeButton = new AtlasButton(x, y, Component.literal("Zeni Cash"), BUTTON,
                    button -> {
                        cashMode = !cashMode;
                        updateMoneyLabels();
                    });
            addRenderableWidget(modeButton);
            y += step;
        }

        amountBox = new EditBox(font, x, y, AtlasButton.nativeWidth(BUTTON), 16,
                Component.literal("Amount"));
        amountBox.setValue("0");
        amountBox.setFilter(XenoNpcBankScreen::isDigits);
        addRenderableWidget(amountBox);
        y += 20;

        depositButton = new AtlasButton(x, y, Component.literal("Deposit cash"), BUTTON,
                button -> send(cashMode ? XenoNpcBankPacket.ACTION_DEPOSIT_CASH
                        : XenoNpcBankPacket.ACTION_DEPOSIT, cashMode ? 0L : amount()));
        addRenderableWidget(depositButton);
        y += step;
        withdrawButton = new AtlasButton(x, y, Component.literal("Withdraw cash"), BUTTON,
                button -> send(cashMode ? XenoNpcBankPacket.ACTION_WITHDRAW_CASH
                        : XenoNpcBankPacket.ACTION_WITHDRAW, amount()));
        addRenderableWidget(withdrawButton);
        updateMoneyLabels();
    }

    private void updateMoneyLabels() {
        if (modeButton != null) modeButton.setMessage(Component.literal(cashMode ? "Zeni Cash" : "MMO Econ"));
        if (depositButton != null) depositButton.setMessage(Component.literal(cashMode ? "Deposit cash" : "Deposit wallet"));
        if (withdrawButton != null) withdrawButton.setMessage(Component.literal(cashMode ? "Withdraw cash" : "Withdraw wallet"));
    }

    /** Digits only, and an empty box while it is being retyped. */
    private static boolean isDigits(String text) {
        if (text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        // Long enough to overflow is refused at the keystroke rather than silently wrapping.
        return text.length() <= 18;
    }

    private long amount() {
        if (amountBox == null || amountBox.getValue().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(amountBox.getValue());
        } catch (NumberFormatException exception) {
            // The filter should make this unreachable; answering zero beats throwing in a click.
            return 0L;
        }
    }

    /**
     * Asks the server to reopen on another tab.
     *
     * <p>Named with the tab the strip chose, not the one the menu is on - {@link #send} always
     * carries the current tab, which is right for every other action and wrong for this one.
     */
    private void selectTab(int tab) {
        if (tab != menu.tabIndex()) {
            ModNetwork.sendToServer(new XenoNpcBankPacket(menu.npcEntityId(),
                    XenoNpcBankPacket.ACTION_OPEN, tab, 0L));
        }
    }

    private void send(int action, long value) {
        ModNetwork.sendToServer(new XenoNpcBankPacket(menu.npcEntityId(), action,
                menu.tabIndex(), value));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        XenoAtlasSprites.blit(graphics, MAIN_PANEL, leftPos, topPos);
        XenoAtlasSprites.blit(graphics, SIDE_PANEL, leftPos + sidePanelX, topPos);

        // Slot frames are drawn from the menu's own slot list, so the art can never disagree with
        // where the clickable slots actually are.
        for (Slot slot : menu.slots) {
            XenoAtlasSprites.blit(graphics, SLOT, leftPos + slot.x - 2, topPos + slot.y - 2);
        }
        for (int index = menu.unlockedSlots(); index < XenoNpcBankMenu.VAULT_SLOTS; index++) {
            Slot slot = menu.slots.get(index);
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                    leftPos + slot.x + 17, topPos + slot.y + 17, LOCKED_TINT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, menu.bankName(), titleLabelX, titleLabelY, 0xFFE0F0FF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                0xFFB8CEE0, false);

        int x = sidePanelX + SIDE_INSET;
        graphics.drawString(font, "Tab " + (menu.tabIndex() + 1) + "/" + menu.tabCount(),
                x, 20, 0xFFB8CEE0, false);
        int y = imageHeight - 62;
        if (menu.moneyAvailable()) {
            graphics.drawString(font, "Wallet " + format(menu.walletMoney()), x, y,
                    0xFFE0F0FF, false);
            y += 12;
        }
        graphics.drawString(font, "Vault " + format(menu.vaultMoney()), x, y,
                0xFFE0F0FF, false);
        graphics.drawString(font, "Fee " + menu.withdrawFeePercent() + "%", x, y + 12,
                0xFF8FA6B8, false);
    }

    /**
     * Money, as text, without asking the server.
     *
     * <p>Deliberately not {@code NpcBankMoney.format}: that reaches through the economy bridge,
     * which is a server-side mod. This is a display of a number the server already decided.
     */
    private static String format(long amount) {
        return Long.toString(amount);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
