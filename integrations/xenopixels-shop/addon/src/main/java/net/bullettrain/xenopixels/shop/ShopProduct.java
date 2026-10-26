package net.bullettrain.xenopixels.shop;

public record ShopProduct(String slug, String title, String description, long webPriceMinor,
                          String webCurrency, long mmoEconPrice, long zeniCashPrice) {}
