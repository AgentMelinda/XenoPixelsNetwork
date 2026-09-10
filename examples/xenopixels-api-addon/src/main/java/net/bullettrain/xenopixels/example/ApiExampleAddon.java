package net.bullettrain.xenopixels.example;

import com.dragonminez.common.events.DMZEvent;
import com.mojang.logging.LogUtils;
import net.bullettrain.xenopixelsmod.api.XenoPixelsApi;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAccess;
import net.bullettrain.xenopixelsmod.api.dmz.DmzForms;
import net.bullettrain.xenopixelsmod.api.dmz.DmzSync;
import net.bullettrain.xenopixelsmod.api.event.CloneEvent;
import net.bullettrain.xenopixelsmod.api.event.RushEvent;
import net.bullettrain.xenopixelsmod.api.event.SparkingEvent;
import net.bullettrain.xenopixelsmod.api.event.StrikeInterceptEvent;
import net.bullettrain.xenopixelsmod.api.event.ZanzokenEvent;
import net.bullettrain.xenopixelsmod.api.registry.Bt3RushDefinition;
import net.bullettrain.xenopixelsmod.api.registry.RushRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(ApiExampleAddon.MOD_ID)
public final class ApiExampleAddon {
    public static final String MOD_ID = "xenopixels_api_example";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    public ApiExampleAddon() {
        RushRegistry.registerForm(
                Bt3RushDefinition.standard("api_example_rush", "api_example_rush"),
                RushRegistry.Precedence.BEFORE_BUILT_INS,
                "api example form");

        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onSparkingActivate);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onSparkingDeactivate);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onSparkingMeterChanged);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onRushStart);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onRushImpact);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onRushInterrupt);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onCloneSplit);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onCloneReunite);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onZanzokenDodge);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onStrikeIntercept);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onFormChange);
        NeoForge.EVENT_BUS.addListener(ApiExampleAddon::onRegisterCommands);

        LOGGER.info("XenoPixels API example loaded against API version {}", XenoPixelsApi.API_VERSION);
    }

    private static void onSparkingActivate(SparkingEvent.Activate event) {
        observed("sparking.activate", event.getPlayer().getScoreboardName());
    }

    private static void onSparkingDeactivate(SparkingEvent.Deactivate event) {
        observed("sparking.deactivate", event.getPlayer().getScoreboardName());
    }

    private static void onSparkingMeterChanged(SparkingEvent.MeterChanged event) {
        observed("sparking.meter", event.getPlayer().getScoreboardName());
    }

    private static void onRushStart(RushEvent.Start event) {
        observed("rush.start", event.getRushId());
    }

    private static void onRushImpact(RushEvent.Impact event) {
        observed("rush.impact", event.getRushId() + "#" + event.getIndex());
    }

    private static void onRushInterrupt(RushEvent.Interrupt event) {
        observed("rush.interrupt", event.getRushId());
    }

    private static void onCloneSplit(CloneEvent.Split event) {
        observed("clone.split", Integer.toString(event.getBodyCount()));
    }

    private static void onCloneReunite(CloneEvent.Reunite event) {
        observed("clone.reunite", event.getPlayer().getScoreboardName());
    }

    private static void onZanzokenDodge(ZanzokenEvent.Dodge event) {
        observed("zanzoken.dodge", Float.toString(event.getDamage()));
    }

    private static void onStrikeIntercept(StrikeInterceptEvent event) {
        observed("strike.intercept", event.getTechniqueId());
    }

    private static void onFormChange(DMZEvent.FormChangeEvent event) {
        observed("dmz.form_change", event.getOldGroup() + "/" + event.getOldForm()
                + " -> " + event.getNewGroup() + "/" + event.getNewForm());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenoapitest")
                .requires(source -> source.hasPermission(2))
                .executes(context -> report(context.getSource().getPlayerOrException()))
                .then(Commands.literal("sync_stats")
                        .executes(context -> sync(context.getSource().getPlayerOrException(), "stats")))
                .then(Commands.literal("sync_progression")
                        .executes(context -> sync(context.getSource().getPlayerOrException(), "progression")))
                .then(Commands.literal("sync_resources")
                        .executes(context -> sync(context.getSource().getPlayerOrException(), "resources"))));
    }

    private static int report(ServerPlayer player) {
        String race = DmzAccess.race(player);
        int formGroups = DmzForms.groupsForRace(race).size();
        player.sendSystemMessage(Component.literal("XenoPixels API v" + XenoPixelsApi.API_VERSION
                + ", ready=" + DmzAccess.isReady(player)
                + ", race=" + race
                + ", form=" + DmzAccess.activeFormGroup(player) + "/" + DmzAccess.activeForm(player)
                + ", formGroups=" + formGroups));
        COUNTS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                player.sendSystemMessage(Component.literal(entry.getKey() + "=" + entry.getValue().get())));
        return COUNTS.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    private static int sync(ServerPlayer player, String type) {
        switch (type) {
            case "stats" -> DmzSync.syncStats(player);
            case "progression" -> DmzSync.syncProgression(player);
            case "resources" -> DmzSync.syncResources(player);
            default -> throw new IllegalArgumentException("Unknown sync type: " + type);
        }
        player.sendSystemMessage(Component.literal("Sent DMZ " + type + " sync"));
        return 1;
    }

    private static void observed(String key, String detail) {
        int count = COUNTS.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        LOGGER.info("[xeno-api-example] {} count={} detail={}", key, count, detail);
    }
}
