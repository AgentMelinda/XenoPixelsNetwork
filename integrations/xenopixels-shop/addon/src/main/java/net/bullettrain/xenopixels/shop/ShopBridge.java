package net.bullettrain.xenopixels.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.api.commerce.CommerceApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import xenoapi.npcs.api.NpcAPI;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.entity.IPlayer;
import xenoapi.npcs.api.item.IItemStack;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

final class ShopBridge {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    private ShopBridge() {}

    static void fetchCatalog(ServerPlayer player) {
        try {
            ShopServerConfig config = ShopServerConfig.load();
            request(config, "GET", "/api/v1/server/catalog", "").thenAccept(response -> {
                player.getServer().execute(() -> {
                    if (player.isRemoved()) return;
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        player.sendSystemMessage(Component.literal("Shop catalog is unavailable right now."));
                        return;
                    }
                    try {
                        List<ShopProduct> products = parseProducts(response.body());
                        AddonNetwork.sendToPlayer(player, new CatalogPacket(products, config.baseUrl()));
                    } catch (RuntimeException exception) {
                        player.sendSystemMessage(Component.literal("Shop catalog response was invalid."));
                    }
                });
            }).exceptionally(error -> {
                player.getServer().execute(() -> {
                    if (!player.isRemoved()) player.sendSystemMessage(Component.literal("Could not reach the shop service."));
                });
                return null;
            });
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Shop is not configured. Ask a server administrator."));
        }
    }

    static void fetchAccount(ServerPlayer player) {
        try {
            ShopServerConfig config = ShopServerConfig.load();
            String body = "{\"uuid\":\"" + player.getUUID() + "\"}";
            request(config, "POST", "/api/v1/server/account/orders", body).thenAccept(response ->
                    player.getServer().execute(() -> {
                        if (player.isRemoved()) return;
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            player.sendSystemMessage(Component.literal("Your shop account could not be loaded."));
                            return;
                        }
                        try {
                            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                            boolean linked = root.get("linked").getAsBoolean();
                            JsonArray rows = root.getAsJsonArray("orders");
                            if (rows == null || rows.size() > 20) throw new IllegalArgumentException("Invalid order list");
                            List<ShopOrder> orders = new ArrayList<>(rows.size());
                            for (JsonElement item : rows) {
                                JsonObject order = item.getAsJsonObject();
                                orders.add(new ShopOrder(bounded(order, "title", 120), bounded(order, "payment_method", 16),
                                        number(order, "amount"), bounded(order, "currency", 3), bounded(order, "status", 24),
                                        bounded(order, "created_at", 32)));
                            }
                            AddonNetwork.sendToPlayer(player, new AccountPacket(linked, List.copyOf(orders)));
                        } catch (RuntimeException exception) {
                            player.sendSystemMessage(Component.literal("Shop account data was invalid."));
                        }
                    })).exceptionally(error -> {
                player.getServer().execute(() -> {
                    if (!player.isRemoved()) player.sendSystemMessage(Component.literal("Could not reach the shop account service."));
                });
                return null;
            });
        } catch (Exception error) {
            player.sendSystemMessage(Component.literal("Shop is not configured. Ask a server administrator."));
        }
    }

    static void redeemLink(ServerPlayer player, String code) {
        if (!code.matches("[A-Fa-f0-9]{16}")) {
            player.sendSystemMessage(Component.literal("Link code must contain 16 letters or digits."));
            return;
        }
        try {
            ShopServerConfig config = ShopServerConfig.load();
            String body = "{\"code\":\"" + code.toUpperCase(java.util.Locale.ROOT) + "\",\"uuid\":\"" + player.getUUID() + "\"}";
            request(config, "POST", "/api/v1/server/link/redeem", body).thenAccept(response -> player.getServer().execute(() -> {
                if (player.isRemoved()) return;
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    player.sendSystemMessage(Component.literal("Your XenoPixels forum account is now linked."));
                } else {
                    player.sendSystemMessage(Component.literal("That link code is invalid, expired, or already used."));
                }
            })).exceptionally(error -> {
                player.getServer().execute(() -> {
                    if (!player.isRemoved()) player.sendSystemMessage(Component.literal("Could not reach the account-link service."));
                });
                return null;
            });
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Shop is not configured. Ask a server administrator."));
        }
    }

    static void claimDelivery(ServerPlayer player) {
        try {
            ShopServerConfig config = ShopServerConfig.load();
            String body = "{\"uuid\":\"" + player.getUUID() + "\"}";
            request(config, "POST", "/api/v1/server/deliveries/claim", body).thenAccept(response -> player.getServer().execute(() -> {
                if (player.isRemoved()) return;
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    player.sendSystemMessage(Component.literal("Could not check your shop deliveries."));
                    return;
                }
                try {
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonElement deliveryElement = root.get("delivery");
                    if (deliveryElement == null || deliveryElement.isJsonNull()) {
                        player.sendSystemMessage(Component.literal("You have no pending shop deliveries."));
                        return;
                    }
                    JsonObject delivery = deliveryElement.getAsJsonObject();
                    applyDelivery(player, config, delivery);
                } catch (RuntimeException exception) {
                    player.sendSystemMessage(Component.literal("Shop delivery data was invalid."));
                }
            })).exceptionally(error -> {
                player.getServer().execute(() -> {
                    if (!player.isRemoved()) player.sendSystemMessage(Component.literal("Could not reach the shop delivery service."));
                });
                return null;
            });
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Shop is not configured. Ask a server administrator."));
        }
    }

    static void purchase(ServerPlayer player, String slug, String currency) {
        if (!slug.matches("[a-z0-9_-]{1,80}") || !currency.matches("mmoecon|zeni_cash")) {
            player.sendSystemMessage(Component.literal("Invalid shop purchase request."));
            return;
        }
        try {
            ShopServerConfig config = ShopServerConfig.load();
            byte[] keyBytes = new byte[16];
            new SecureRandom().nextBytes(keyBytes);
            String purchaseKey = HexFormat.of().formatHex(keyBytes);
            String body = "{\"purchase_key\":\"" + purchaseKey + "\",\"uuid\":\"" + player.getUUID()
                    + "\",\"slug\":\"" + slug + "\",\"currency\":\"" + currency + "\"}";
            request(config, "POST", "/api/v1/server/game-purchases/prepare", body).thenAccept(response ->
                    player.getServer().execute(() -> {
                        if (player.isRemoved()) return;
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            player.sendSystemMessage(Component.literal("Purchase could not be prepared. Check that your forum and game accounts are linked."));
                            return;
                        }
                        long amount;
                        try {
                            JsonObject purchase = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("purchase");
                            if (!"prepared".equals(purchase.get("status").getAsString())) {
                                player.sendSystemMessage(Component.literal("This purchase is already being resolved. Please contact staff if it remains pending."));
                                return;
                            }
                            amount = purchase.get("amount").getAsLong();
                            if (amount < 1 || amount > 1_000_000_000L || !currency.equals(purchase.get("currency").getAsString())) throw new IllegalArgumentException();
                        } catch (RuntimeException invalid) {
                            player.sendSystemMessage(Component.literal("Shop returned an invalid price."));
                            return;
                        }
                        boolean debited = CommerceApi.debit(player, currency, amount);
                        String result = debited ? "debited" : "declined";
                        String finish = "{\"purchase_key\":\"" + purchaseKey + "\",\"result\":\"" + result + "\"}";
                        try {
                            request(config, "POST", "/api/v1/server/game-purchases/finish", finish).thenAccept(receipt ->
                                    player.getServer().execute(() -> {
                                        if (player.isRemoved()) return;
                                        if (debited && receipt.statusCode() >= 200 && receipt.statusCode() < 300) {
                                            player.sendSystemMessage(Component.literal("Purchase complete. Use /xshop claim if the reward does not arrive shortly."));
                                        } else if (!debited) {
                                            player.sendSystemMessage(Component.literal("Purchase declined: insufficient balance or currency provider unavailable."));
                                        } else {
                                            player.sendSystemMessage(Component.literal("Currency was charged but shop confirmation failed. Do not retry; contact staff for reconciliation."));
                                        }
                                    })).exceptionally(error -> {
                                player.getServer().execute(() -> {
                                    if (!player.isRemoved()) player.sendSystemMessage(Component.literal(debited
                                            ? "Currency may have been charged but confirmation timed out. Do not retry; contact staff."
                                            : "Shop could not record the declined purchase."));
                                });
                                return null;
                            });
                        } catch (Exception error) {
                            player.sendSystemMessage(Component.literal(debited
                                    ? "Currency was charged but shop confirmation failed. Do not retry; contact staff for reconciliation."
                                    : "Shop confirmation failed."));
                        }
                    })).exceptionally(error -> {
                player.getServer().execute(() -> {
                    if (!player.isRemoved()) player.sendSystemMessage(Component.literal("Could not reach the shop before charging your balance."));
                });
                return null;
            });
        } catch (Exception error) {
            player.sendSystemMessage(Component.literal("Shop is not configured. Ask a server administrator."));
        }
    }

    private static void applyDelivery(ServerPlayer player, ShopServerConfig config, JsonObject delivery) {
        int deliveryId = delivery.get("delivery_id").getAsInt();
        String claimToken = delivery.get("claim_token").getAsString();
        List<ItemStack> stacks = resolveRewards(delivery.getAsJsonArray("reward"));
        if (stacks == null || stacks.isEmpty() || !NpcAPI.IsAvailable()) {
            acknowledge(config, deliveryId, claimToken, false, "reward_unavailable", player);
            return;
        }
        IEntity<?> typed = NpcAPI.Instance().getIEntity(player);
        if (!(typed instanceof IPlayer<?> xenoPlayer)) {
            acknowledge(config, deliveryId, claimToken, false, "xenoapi_player_unavailable", player);
            return;
        }
        IItemStack[] apiItems = new IItemStack[stacks.size()];
        for (int index = 0; index < stacks.size(); index++) apiItems[index] = NpcAPI.Instance().getIItemStack(stacks.get(index));
        xenoPlayer.giveOrDropItems(apiItems);
        acknowledge(config, deliveryId, claimToken, true, "", player);
    }

    private static List<ItemStack> resolveRewards(JsonArray rewards) {
        if (rewards == null || rewards.size() < 1 || rewards.size() > 32) return null;
        List<ItemStack> result = new ArrayList<>();
        Map<String, List<ItemStack>> kits;
        try { kits = loadKits(); }
        catch (IOException exception) { return null; }
        for (JsonElement element : rewards) {
            if (!element.isJsonObject()) return null;
            JsonObject reward = element.getAsJsonObject();
            String type = reward.has("type") ? reward.get("type").getAsString() : "";
            if (type.equals("item")) {
                ItemStack stack = itemStack(reward.get("item").getAsString(), reward.get("count").getAsInt());
                if (stack == null) return null;
                result.add(stack);
            } else if (type.equals("kit")) {
                String kit = reward.get("kit").getAsString();
                List<ItemStack> contents = kits.get(kit);
                if (contents == null || result.size() + contents.size() > 64) return null;
                contents.forEach(stack -> result.add(stack.copy()));
            } else return null;
            if (result.size() > 64) return null;
        }
        return result;
    }

    private static Map<String, List<ItemStack>> loadKits() throws IOException {
        Path path = FMLPaths.CONFIGDIR.get().resolve("xenopixels-shop-kits.json");
        if (!Files.exists(path)) {
            Files.writeString(path, "{\n  \"welcome\": [{\"item\": \"minecraft:bread\", \"count\": 8}]\n}\n");
        }
        JsonObject root;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { root = JsonParser.parseReader(reader).getAsJsonObject(); }
        if (root.size() > 100) throw new IOException("Too many shop kits");
        Map<String, List<ItemStack>> kits = new java.util.HashMap<>();
        for (var entry : root.entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_-]{1,48}") || !entry.getValue().isJsonArray() || entry.getValue().getAsJsonArray().size() > 32) continue;
            List<ItemStack> stacks = new ArrayList<>();
            boolean valid = true;
            for (JsonElement item : entry.getValue().getAsJsonArray()) {
                if (!item.isJsonObject()) { valid = false; break; }
                JsonObject object = item.getAsJsonObject();
                if (!object.has("item") || !object.has("count")) { valid = false; break; }
                ItemStack stack = itemStack(object.get("item").getAsString(), object.get("count").getAsInt());
                if (stack == null) { valid = false; break; }
                stacks.add(stack);
            }
            if (valid && !stacks.isEmpty()) kits.put(entry.getKey(), List.copyOf(stacks));
        }
        return Map.copyOf(kits);
    }

    private static ItemStack itemStack(String id, int count) {
        if (id == null || id.length() > 128 || count < 1 || count > 64) return null;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        Item item = BuiltInRegistries.ITEM.get(key);
        if (item == Items.AIR) return null;
        return new ItemStack(item, count);
    }

    private static void acknowledge(ShopServerConfig config, int deliveryId, String token, boolean success,
                                    String errorCode, ServerPlayer player) {
        String body = "{\"delivery_id\":" + deliveryId + ",\"claim_token\":\"" + token
                + "\",\"success\":" + success + ",\"error_code\":\"" + errorCode + "\"}";
        try {
            request(config, "POST", "/api/v1/server/deliveries/ack", body).thenAccept(response -> player.getServer().execute(() -> {
                if (!player.isRemoved()) player.sendSystemMessage(Component.literal(success
                        ? "Shop reward delivered." : "Shop reward could not be delivered; it remains queued."));
            }));
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Reward was granted, but the delivery receipt could not be sent."));
        }
    }

    private static java.util.concurrent.CompletableFuture<HttpResponse<String>> request(
            ShopServerConfig config, String method, String path, String body) throws Exception {
        String timestamp = Long.toString(System.currentTimeMillis() / 1000L);
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes);
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        String payloadHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        String message = timestamp + "\n" + nonce + "\n" + method + "\n" + path + "\n" + payloadHash;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(config.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                .timeout(Duration.ofSeconds(8))
                .header("X-Xeno-Server", config.serverId())
                .header("X-Xeno-Timestamp", timestamp)
                .header("X-Xeno-Nonce", nonce)
                .header("X-Xeno-Signature", signature);
        if (method.equals("GET")) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static List<ShopProduct> parseProducts(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray rows = root.getAsJsonArray("products");
        if (rows == null || rows.size() > 100) throw new IllegalArgumentException("Invalid catalog size");
        List<ShopProduct> result = new ArrayList<>(rows.size());
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            String slug = bounded(row, "slug", 80);
            if (!slug.matches("[a-z0-9_-]{1,80}")) continue;
            result.add(new ShopProduct(slug, bounded(row, "title", 120), bounded(row, "description", 512),
                    number(row, "web_price_minor"), bounded(row, "web_currency", 3),
                    number(row, "mmoecon_price"), number(row, "zeni_cash_price")));
        }
        return List.copyOf(result);
    }

    private static String bounded(JsonObject object, String key, int max) {
        String value = object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long number(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) return -1L;
        try {
            long value = object.get(key).getAsLong();
            return value >= 0 && value <= 1_000_000_000L ? value : -1L;
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }
}
