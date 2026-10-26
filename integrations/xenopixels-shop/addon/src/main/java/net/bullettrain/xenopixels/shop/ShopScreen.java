package net.bullettrain.xenopixels.shop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;

import java.net.URI;
import java.util.List;

final class ShopScreen extends Screen {
    private final List<ShopProduct> products;
    private final String shopUrl;
    private int page;
    private int selected;
    private static final int PAGE_SIZE = 5;

    ShopScreen(List<ShopProduct> products, String shopUrl) {
        super(Component.literal("XenoPixels Shop"));
        this.products = products;
        this.shopUrl = shopUrl;
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = width / 2 - 210;
        int top = height / 2 - 132;
        int pages = Math.max(1, (products.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (!products.isEmpty()) selected = Math.max(0, Math.min(selected, products.size() - 1));
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE;
        int count = Math.min(PAGE_SIZE, products.size() - start);
        for (int row = 0; row < count; row++) {
            int index = start + row;
            addRenderableWidget(Button.builder(Component.literal(products.get(index).title()), button -> {
                selected = index;
                init();
            }).bounds(left + 14, top + 50 + row * 34, 244, 28).build());
        }
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> { page--; init(); })
                .bounds(left + 14, top + 238, 34, 24).build());
        addRenderableWidget(Button.builder(Component.literal("›"), button -> { page++; init(); })
                .bounds(left + 54, top + 238, 34, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Account & orders"), button -> fetchAccount())
                .bounds(left + 94, top + 236, 104, 28).build());
        addRenderableWidget(Button.builder(Component.literal("Open web shop"), button -> openWebsite())
                .bounds(left + 204, top + 236, 108, 28).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + 318, top + 236, 92, 28).build());
        if (!products.isEmpty()) {
            ShopProduct product = products.get(selected);
            if (product.mmoEconPrice() > 0) {
                addRenderableWidget(Button.builder(Component.literal("Buy with MMO Econ"), button -> buy(product, "mmoecon"))
                        .bounds(left + 268, top + 183, 138, 22).build());
            }
            if (product.zeniCashPrice() > 0) {
                addRenderableWidget(Button.builder(Component.literal("Buy with Zeni"), button -> buy(product, "zeni_cash"))
                        .bounds(left + 268, top + 207, 138, 22).build());
            }
        }
    }

    private void buy(ShopProduct product, String currency) {
        try {
            AddonNetwork.sendToServer(new PurchasePacket(product.slug(), currency));
        } catch (RuntimeException exception) {
            // Packet registration or connection can be unavailable while a screen is open.
        }
    }

    private void fetchAccount() {
        try { AddonNetwork.sendToServer(new AccountRequestPacket()); }
        catch (RuntimeException exception) { }
    }

    private void openWebsite() {
        try {
            URI uri = URI.create(shopUrl);
            if ("https".equalsIgnoreCase(uri.getScheme()) && "shop.xenopixelsnetwork.com".equalsIgnoreCase(uri.getHost())) {
                Util.getPlatform().openUri(uri);
            }
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed shop URLs supplied by an incompatible server.
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 210;
        int top = height / 2 - 132;
        graphics.fill(left, top, left + 420, top + 274, 0xF20E111B);
        graphics.fill(left, top, left + 420, top + 38, 0xFF201D38);
        graphics.drawCenteredString(font, title, width / 2, top + 13, 0xFFFFFFFF);
        graphics.drawString(font, "Browse rewards · checkout on the website", left + 14, top + 38, 0xFF9EA8C4);
        if (products.isEmpty()) {
            graphics.drawCenteredString(font, "The shop catalog is empty.", width / 2, top + 112, 0xFFFFFFFF);
        } else if (selected >= 0 && selected < products.size()) {
            ShopProduct product = products.get(selected);
            graphics.fill(left + 268, top + 48, left + 406, top + 218, 0xFF171B29);
            graphics.drawString(font, Component.literal(product.title()), left + 278, top + 58, 0xFF63E5FF);
            String description = product.description();
            int line = 0;
            for (int offset = 0; offset < description.length() && line < 7; offset += 20, line++) {
                graphics.drawString(font, description.substring(offset, Math.min(description.length(), offset + 20)),
                        left + 278, top + 80 + line * 12, 0xFFD5D9E6);
            }
            int priceY = top + 156;
            if (product.webPriceMinor() >= 0) graphics.drawString(font, "Web: " + product.webCurrency() + " " + String.format("%.2f", product.webPriceMinor() / 100.0), left + 278, priceY, 0xFFFFFFFF);
            if (product.mmoEconPrice() > 0) graphics.drawString(font, "MMO Econ: " + product.mmoEconPrice(), left + 278, priceY + 12, 0xFF9EA8C4);
            if (product.zeniCashPrice() > 0) graphics.drawString(font, "Zeni: " + product.zeniCashPrice(), left + 278, priceY + 24, 0xFF9EA8C4);
        }
        int pages = Math.max(1, (products.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawString(font, "Page " + (page + 1) + " / " + pages, left + 14, top + 220, 0xFF9EA8C4);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
