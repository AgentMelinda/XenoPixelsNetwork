package net.bullettrain.xenopixelsmod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local client preferences for UI and combat feel.
 * Written to {@code config/xenopixelsmod-client.json}.
 * Combat still requires matching server flags.
 *
 * <p>Not {@code @OnlyIn(CLIENT)}: client mixins reference this class, and Forge would
 * strip it on dedicated servers causing {@code ClassMetadataNotFoundException}.
 * File I/O still only runs from client setup.
 */
public final class XenoClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-client.json");

    // --- UI ---
    public static boolean xenoHudEnabled = true;
    /** Custom Alt/Ctrl KI technique slot bar + charge meter (replaces DMZ techniquehud). */
    public static boolean techniqueHotbarEnabled = true;
    public static boolean titleScreenButton = true;
    public static boolean pauseScreenButton = true;
    public static boolean xenoMenuEnabled = true;
    public static boolean contentScreensEnabled = true;
    public static boolean joinServerButton = true;
    public static boolean hudEditEnabled = true;
    public static boolean senzuCooldownMessages = true;
    /** XV2-style party/team HP+KI mini bars for nearby scoreboard-team allies. */
    public static boolean partyHudEnabled = true;
    /** Compact BT3 cooldown chip strip (vanish / chase / combo / charge). */
    public static boolean cooldownHudEnabled = true;

    // --- Combat client (prediction / input) ---
    public static boolean bt3CombatClient = true;
    public static boolean bt3ComboClient = true;
    public static boolean bt3VanishClient = true;
    public static boolean bt3ChaseDashClient = true;
    public static boolean bt3BackstepClient = true;
    public static boolean bt3ChargeAttackClient = true;
    public static boolean bt3DragonDashClient = true;
    public static boolean bt3GuardClient = true;
    public static boolean bt3SuperCounterClient = true;
    public static boolean bt3KiBlastCancelClient = true;
    public static boolean bt3ZBurstClient = true;
    public static boolean bt3LockCycleClient = true;
    public static boolean bt3CombatSfx = true;
    public static boolean bt3ChargeGlow = true;
    /** Client afterimage trails for vanish-style moves. */
    public static boolean bt3Afterimage = true;
    /** Local DMZ charge/punch/kick animations. */
    public static boolean bt3CombatAnims = true;
    /** Delayed 2nd/3rd punch-kick chain anims. */
    public static boolean bt3KickChainAnims = true;
    /** Crit/spark particles on charge release and impacts. */
    public static boolean bt3CombatParticles = true;
    /**
     * Camera shake on impact. Opt-out because unrequested camera motion is an accessibility
     * problem, not a taste one; turning it off leaves the flash and the world particles, so no
     * information is lost with it.
     */
    public static boolean bt3ScreenShake = true;
    /** Scales the shake. 0 is equivalent to disabling it; above 1 exaggerates it. */
    public static float bt3ScreenShakeStrength = 1.0f;
    /** The tinted vignette flash on impact, on its own switch so it survives disabling shake. */
    public static boolean bt3ImpactFlash = true;
    /** Hide Alt/Ctrl technique hotbar while chat/command screen is open. */
    public static boolean techniqueHotbarHideInChat = true;

    private XenoClientConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            apply(data);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load client config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save client config", e);
        }
    }

    public static Data snapshot() {
        Data d = new Data();
        d.xenoHudEnabled = xenoHudEnabled;
        d.techniqueHotbarEnabled = techniqueHotbarEnabled;
        d.titleScreenButton = titleScreenButton;
        d.pauseScreenButton = pauseScreenButton;
        d.xenoMenuEnabled = xenoMenuEnabled;
        d.contentScreensEnabled = contentScreensEnabled;
        d.joinServerButton = joinServerButton;
        d.hudEditEnabled = hudEditEnabled;
        d.senzuCooldownMessages = senzuCooldownMessages;
        d.partyHudEnabled = partyHudEnabled;
        d.cooldownHudEnabled = cooldownHudEnabled;
        d.bt3CombatClient = bt3CombatClient;
        d.bt3ComboClient = bt3ComboClient;
        d.bt3VanishClient = bt3VanishClient;
        d.bt3ChaseDashClient = bt3ChaseDashClient;
        d.bt3BackstepClient = bt3BackstepClient;
        d.bt3ChargeAttackClient = bt3ChargeAttackClient;
        d.bt3DragonDashClient = bt3DragonDashClient;
        d.bt3GuardClient = bt3GuardClient;
        d.bt3SuperCounterClient = bt3SuperCounterClient;
        d.bt3KiBlastCancelClient = bt3KiBlastCancelClient;
        d.bt3ZBurstClient = bt3ZBurstClient;
        d.bt3LockCycleClient = bt3LockCycleClient;
        d.bt3CombatSfx = bt3CombatSfx;
        d.bt3ChargeGlow = bt3ChargeGlow;
        d.bt3Afterimage = bt3Afterimage;
        d.bt3CombatAnims = bt3CombatAnims;
        d.bt3KickChainAnims = bt3KickChainAnims;
        d.bt3CombatParticles = bt3CombatParticles;
        d.bt3ScreenShake = bt3ScreenShake;
        d.bt3ScreenShakeStrength = bt3ScreenShakeStrength;
        d.bt3ImpactFlash = bt3ImpactFlash;
        d.techniqueHotbarHideInChat = techniqueHotbarHideInChat;
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        xenoHudEnabled = d.xenoHudEnabled;
        techniqueHotbarEnabled = d.techniqueHotbarEnabled;
        titleScreenButton = d.titleScreenButton;
        pauseScreenButton = d.pauseScreenButton;
        xenoMenuEnabled = d.xenoMenuEnabled;
        contentScreensEnabled = d.contentScreensEnabled;
        joinServerButton = d.joinServerButton;
        hudEditEnabled = d.hudEditEnabled;
        senzuCooldownMessages = d.senzuCooldownMessages;
        partyHudEnabled = d.partyHudEnabled;
        cooldownHudEnabled = d.cooldownHudEnabled;
        bt3CombatClient = d.bt3CombatClient;
        bt3ComboClient = d.bt3ComboClient;
        bt3VanishClient = d.bt3VanishClient;
        bt3ChaseDashClient = d.bt3ChaseDashClient;
        bt3BackstepClient = d.bt3BackstepClient;
        bt3ChargeAttackClient = d.bt3ChargeAttackClient;
        bt3DragonDashClient = d.bt3DragonDashClient;
        bt3GuardClient = d.bt3GuardClient;
        bt3SuperCounterClient = d.bt3SuperCounterClient;
        bt3KiBlastCancelClient = d.bt3KiBlastCancelClient;
        bt3ZBurstClient = d.bt3ZBurstClient;
        bt3LockCycleClient = d.bt3LockCycleClient;
        bt3CombatSfx = d.bt3CombatSfx;
        bt3ChargeGlow = d.bt3ChargeGlow;
        bt3Afterimage = d.bt3Afterimage;
        bt3CombatAnims = d.bt3CombatAnims;
        bt3KickChainAnims = d.bt3KickChainAnims;
        bt3CombatParticles = d.bt3CombatParticles;
        bt3ScreenShake = d.bt3ScreenShake;
        bt3ScreenShakeStrength = d.bt3ScreenShakeStrength;
        bt3ImpactFlash = d.bt3ImpactFlash;
        techniqueHotbarHideInChat = d.techniqueHotbarHideInChat;
    }

    public static class Data {
        public boolean xenoHudEnabled = true;
        public boolean techniqueHotbarEnabled = true;
        public boolean titleScreenButton = true;
        public boolean pauseScreenButton = true;
        public boolean xenoMenuEnabled = true;
        public boolean contentScreensEnabled = true;
        public boolean joinServerButton = true;
        public boolean hudEditEnabled = true;
        public boolean senzuCooldownMessages = true;
        public boolean partyHudEnabled = true;
        public boolean cooldownHudEnabled = true;
        public boolean bt3CombatClient = true;
        public boolean bt3ComboClient = true;
        public boolean bt3VanishClient = true;
        public boolean bt3ChaseDashClient = true;
        public boolean bt3BackstepClient = true;
        public boolean bt3ChargeAttackClient = true;
        public boolean bt3DragonDashClient = true;
        public boolean bt3GuardClient = true;
        public boolean bt3SuperCounterClient = true;
        public boolean bt3KiBlastCancelClient = true;
        public boolean bt3ZBurstClient = true;
        public boolean bt3LockCycleClient = true;
        public boolean bt3CombatSfx = true;
        public boolean bt3ChargeGlow = true;
        public boolean bt3Afterimage = true;
        public boolean bt3CombatAnims = true;
        public boolean bt3KickChainAnims = true;
        public boolean bt3CombatParticles = true;
        public boolean bt3ScreenShake = true;
        public float bt3ScreenShakeStrength = 1.0f;
        public boolean bt3ImpactFlash = true;
        public boolean techniqueHotbarHideInChat = true;
    }
}
