package net.bullettrain.xenopixels.shop;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;

final class AccountScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private final AccountPacket account;
    private int page;

    AccountScreen(AccountPacket account) {
        super(Component.literal("XenoPixels Shop Account"));
        this.account = account;
    }

    @Override
    protected void init() {
        clearWidgets();
        int pages = Math.max(1, (account.orders().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("‹"), button -> { page--; init(); })
                .bounds(width / 2 - 204, height / 2 + 96, 36, 24).build());
        if (page + 1 < pages) addRenderableWidget(Button.builder(Component.literal("›"), button -> { page++; init(); })
                .bounds(width / 2 - 162, height / 2 + 96, 36, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Open shop"), button -> openShop())
                .bounds(width / 2 + 22, height / 2 + 94, 104, 28).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 + 134, height / 2 + 94, 72, 28).build());
    }

    private void openShop() {
        try { Util.getPlatform().openUri(URI.create("https://shop.xenopixelsnetwork.com")); }
        catch (IllegalArgumentException ignored) { }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 210;
        int top = height / 2 - 132;
        graphics.fill(left, top, left + 420, top + 274, 0xF20E111B);
        graphics.fill(left, top, left + 420, top + 38, 0xFF201D38);
        graphics.drawCenteredString(font, title, width / 2, top + 13, 0xFFFFFFFF);
        graphics.drawString(font, account.linked() ? "Forum account: linked" : "Forum account: not linked", left + 14, top + 48,
                account.linked() ? 0xFF63E5FF : 0xFFFFC66D);
        if (!account.linked()) graphics.drawString(font, "Sign in to the forum, open its Shop link, then use /xshop link CODE.", left + 14, top + 65, 0xFFD5D9E6);
        int first = page * PAGE_SIZE;
        int last = Math.min(account.orders().size(), first + PAGE_SIZE);
        if (first == last) {
            graphics.drawCenteredString(font, account.linked() ? "No shop orders yet." : "Link your forum account to view orders.", width / 2, top + 120, 0xFFD5D9E6);
        } else {
            for (int i = first; i < last; i++) {
                ShopOrder order = account.orders().get(i);
                int y = top + 73 + (i - first) * 18;
                String amount = order.method().equals("mmoecon") || order.method().equals("zeni_cash")
                        ? Long.toString(order.amount()) : String.format("%.2f", order.amount() / 100.0);
                graphics.drawString(font, order.title(), left + 14, y, 0xFFFFFFFF);
                graphics.drawString(font, order.status() + " · " + order.method() + " · " + amount + " " + order.currency(), left + 172, y, 0xFF9EA8C4);
            }
        }
        int pages = Math.max(1, (account.orders().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawString(font, "Page " + (page + 1) + " / " + pages, left + 14, top + 242, 0xFF9EA8C4);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
