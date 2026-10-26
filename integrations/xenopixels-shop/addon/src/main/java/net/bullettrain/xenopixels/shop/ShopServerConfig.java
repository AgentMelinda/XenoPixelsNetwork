package net.bullettrain.xenopixels.shop;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

record ShopServerConfig(String baseUrl, String serverId, String secret) {
    static ShopServerConfig load() throws IOException {
        Path path = FMLPaths.CONFIGDIR.get().resolve("xenopixels-shop.properties");
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Properties defaults = new Properties();
            defaults.setProperty("base_url", "https://xenopixelsnetwork.com");
            defaults.setProperty("server_id", "main");
            defaults.setProperty("server_secret", "CHANGE_THIS_TO_A_RANDOM_32_BYTE_SECRET");
            try (var output = Files.newOutputStream(path)) {
                defaults.store(output, "Server-only XenoPixels Shop API configuration");
            }
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) { values.load(input); }
        String baseUrl = values.getProperty("base_url", "").trim().replaceAll("/+$", "");
        String serverId = values.getProperty("server_id", "").trim();
        String secret = values.getProperty("server_secret", "").trim();
        if (!baseUrl.startsWith("https://") || !serverId.matches("[A-Za-z0-9_-]{1,64}") || secret.length() < 32
                || secret.startsWith("CHANGE_")) throw new IOException("Shop API configuration is missing or invalid.");
        return new ShopServerConfig(baseUrl, serverId, secret);
    }
}
