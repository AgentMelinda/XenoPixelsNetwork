package net.bullettrain.xenopixelsmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-owned social-party policy, separate from combat balance configuration. */
public final class XenoPartyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-party.json");

    /** Disband after this many seconds without party activity. Zero disables expiry. */
    public static int idleExpirySeconds = 30 * 60;
    /**
     * Xeno's UI/HUD target size. DMZ may expose a different server-wide cap.
     *
     * <p>Hard-capped at {@link PartySyncPacket#MAX_MEMBERS}, because the sync packet truncates
     * silently above that and a party larger than the wire format would simply lose members off
     * the roster with no error.
     */
    public static int maxMembers = 5;
    public static boolean friendlyFireDefault = false;
    /**
     * When false, CNPC {@code @dp} quest rewards stay on the player who turned the quest in.
     * Leaders can still enable sharing per party. Default off so a strong player cannot farm
     * for a weak friend.
     */
    public static boolean questShareDefault = false;
    public static int pingDurationTicks = 20 * 8;
    public static double pingRange = 96.0;

    private XenoPartyConfig() {
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data d = GSON.fromJson(reader, Data.class);
            if (d == null) return;
            idleExpirySeconds = Math.max(0, d.idleExpirySeconds);
            maxMembers = Math.max(2, Math.min(PartySyncPacket.MAX_MEMBERS, d.maxMembers));
            friendlyFireDefault = d.friendlyFireDefault;
            questShareDefault = d.questShareDefault;
            pingDurationTicks = Math.max(20, Math.min(20 * 60, d.pingDurationTicks));
            pingRange = Math.max(8.0, Math.min(512.0, d.pingRange));
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load XenoParty config", e);
        }
    }

    public static void save() {
        Data d = new Data();
        d.idleExpirySeconds = idleExpirySeconds;
        d.maxMembers = maxMembers;
        d.friendlyFireDefault = friendlyFireDefault;
        d.questShareDefault = questShareDefault;
        d.pingDurationTicks = pingDurationTicks;
        d.pingRange = pingRange;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(d, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save XenoParty config", e);
        }
    }

    private static final class Data {
        int idleExpirySeconds = 30 * 60;
        int maxMembers = 5;
        boolean friendlyFireDefault;
        boolean questShareDefault;
        int pingDurationTicks = 20 * 8;
        double pingRange = 96.0;
    }
}
