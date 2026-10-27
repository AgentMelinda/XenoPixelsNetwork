package net.bullettrain.xenopixelsmod.api.commerce;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Server-only currency access for trusted addons.
 *
 * <p>Calls must run on the logical server thread. Providers must be atomic and return {@code false}
 * on any unavailable or failed operation. This API does not make a client request authoritative.</p>
 */
public final class CommerceApi {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private static final ConcurrentHashMap<String, CommerceCurrencyProvider> PROVIDERS = new ConcurrentHashMap<>();

    private CommerceApi() {}

    public static void register(CommerceCurrencyProvider provider) {
        if (provider == null || provider.id() == null || !ID.matcher(provider.id()).matches()
                || provider.displayName() == null || provider.displayName().isBlank()) {
            throw new IllegalArgumentException("Invalid commerce currency provider");
        }
        if (PROVIDERS.putIfAbsent(provider.id(), provider) != null) {
            throw new IllegalArgumentException("Duplicate commerce currency id: " + provider.id());
        }
    }

    public static List<String> currencyIds() {
        return PROVIDERS.keySet().stream().sorted().toList();
    }

    public static Optional<CommerceCurrencyProvider> provider(String id) {
        if (id == null || !ID.matcher(id).matches()) return Optional.empty();
        CommerceCurrencyProvider provider = PROVIDERS.get(id);
        return provider != null && provider.available() ? Optional.of(provider) : Optional.empty();
    }

    public static boolean debit(ServerPlayer player, String currencyId, long amount) {
        if (!validContext(player, amount)) return false;
        return provider(currencyId).map(provider -> provider.debit(player, amount)).orElse(false);
    }

    public static boolean credit(ServerPlayer player, String currencyId, long amount) {
        if (!validContext(player, amount)) return false;
        return provider(currencyId).map(provider -> provider.credit(player, amount)).orElse(false);
    }

    private static boolean validContext(ServerPlayer player, long amount) {
        return player != null && player.getServer() != null && player.getServer().isSameThread()
                && amount > 0 && amount <= 1_000_000_000L;
    }
}
