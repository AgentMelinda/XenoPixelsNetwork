package net.bullettrain.xenopixelsmod.command;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceFlightGuard;
import net.bullettrain.xenopixelsmod.space.SpaceHandoffPass;
import net.bullettrain.xenopixelsmod.space.SpaceVisualSync;
import net.bullettrain.xenopixelsmod.space.SpaceTickScheduler;
import net.bullettrain.xenopixelsmod.space.SpaceVoidSafety;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.bullettrain.xenopixelsmod.space.math.BodyLod;
import java.util.List;

/** Server-only chart administration. No packet or terrain mutation. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceCommands {
    private SpaceCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenospace").requires(source -> source.hasPermission(2))
                .then(Commands.literal("earth").executes(ctx -> message(ctx.getSource(),
                        net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.status(ctx.getSource().getServer())))
                        .then(pushCommand(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.BODY_ID)))
                .then(Commands.literal("moon").then(pushCommand(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.MOON_ID)))
                .then(Commands.literal("build")
                        .then(Commands.literal("deferfinalize")
                                .executes(ctx -> message(ctx.getSource(), "defer-per-batch-finalize="
                                        + XenoSpaceConfig.planetBuildDeferFinalize()
                                        + " (applies to builds started after changing it)"))
                                .then(Commands.literal("on").executes(ctx -> setBuildDeferFinalize(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> setBuildDeferFinalize(ctx.getSource(), false))))
                        .then(Commands.literal("budget")
                                .executes(ctx -> message(ctx.getSource(), "shared generated-body voxel budget="
                                        + (XenoSpaceConfig.generatedBodyBuildBudgetEnabled()
                                            ? XenoSpaceConfig.generatedBodyBuildVoxelsPerTick() + " voxels/tick" : "off")
                                        + "; millisecond limit="
                                        + (XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0
                                            ? XenoSpaceConfig.generatedBodyBuildMillisPerTick() + " ms best-effort" : "off")))
                                .then(Commands.literal("on").executes(ctx -> setBuildBudget(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> setBuildBudget(ctx.getSource(), false)))))
                .then(Commands.literal("planets").executes(ctx -> message(ctx.getSource(),
                                "physical=" + XenoSpaceConfig.physicalPlanets() + "\n"
                                        + net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.status(ctx.getSource().getServer())))
                        .then(Commands.literal("physical")
                                .then(physicalMode("all")).then(physicalMode("planets")).then(physicalMode("earth_moon")))
                        .then(Commands.literal("push")
                                .then(Commands.argument("body", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .then(Commands.argument("vx", DoubleArgumentType.doubleArg(-100, 100))
                                        .then(Commands.argument("vy", DoubleArgumentType.doubleArg(-100, 100))
                                        .then(Commands.argument("vz", DoubleArgumentType.doubleArg(-100, 100))
                                        .executes(ctx -> push(ctx.getSource(),
                                                net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "body").toString(),
                                                new org.joml.Vector3d(DoubleArgumentType.getDouble(ctx, "vx"),
                                                        DoubleArgumentType.getDouble(ctx, "vy"), DoubleArgumentType.getDouble(ctx, "vz"))))))))))
                .then(Commands.literal("smallplanets")
                        .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer
                                .status(ctx.getSource().getServer())))
                        .then(Commands.literal("on").executes(ctx -> setSmallPlanets(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setSmallPlanets(ctx.getSource(), false)))
                        .then(Commands.literal("preview")
                                .executes(ctx -> previewSmallPlanets(ctx.getSource(), XenoSpaceConfig.smallPlanetReleaseRange()))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(0,
                                                net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField.MAX_NEARBY_RANGE))
                                        .executes(ctx -> previewSmallPlanets(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "range")))))
                        .then(Commands.literal("field")
                                .then(Commands.literal("on").executes(ctx -> setSmallPlanetField(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> setSmallPlanetField(ctx.getSource(), false))))
                        .then(Commands.literal("incremental")
                                .then(Commands.literal("on").executes(ctx -> setSmallPlanetIncremental(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> setSmallPlanetIncremental(ctx.getSource(), false))))
                        .then(Commands.literal("gravity")
                                .then(Commands.literal("on").executes(ctx -> setSmallPlanetGravity(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> setSmallPlanetGravity(ctx.getSource(), false)))))
                .executes(ctx -> message(ctx.getSource(), SpaceChartService.status(ctx.getSource().getServer())))
                .then(Commands.literal("status").executes(ctx -> message(ctx.getSource(), SpaceChartService.status(ctx.getSource().getServer()))))
                .then(Commands.literal("physics-spike")
                        .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.physics.PhysicalPlanetSpike.status(ctx.getSource().getLevel())))
                        .then(Commands.literal("create").executes(ctx -> createSpike(ctx.getSource(), null))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> createSpike(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("hold").executes(ctx -> message(ctx.getSource(),
                                net.bullettrain.xenopixelsmod.space.physics.PhysicalPlanetSpike
                                        .setHold(ctx.getSource().getLevel())))))
                .then(Commands.literal("spike-radius")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> setSpikeRadius(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"))))
                        .executes(ctx -> message(ctx.getSource(),
                                "spike radius ceiling=" + net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.maxSpikeRadius()
                                        + " voxels (config space.maxSpikeRadius, 1..32)")))
                .then(Commands.literal("body").then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .executes(ctx -> bodyState(ctx.getSource(), bodyId(ctx)))
                        .then(Commands.literal("resume").executes(ctx -> changeBody(ctx.getSource(),bodyId(ctx),true,0,0,0)))
                        .then(Commands.literal("move")
                                .then(Commands.argument("x",DoubleArgumentType.doubleArg(-30_000_000,30_000_000))
                                        .then(Commands.argument("y",DoubleArgumentType.doubleArg(-30_000_000,30_000_000))
                                                .then(Commands.argument("z",DoubleArgumentType.doubleArg(-30_000_000,30_000_000))
                                                        .executes(ctx -> changeBody(ctx.getSource(),bodyId(ctx),false,
                                                                DoubleArgumentType.getDouble(ctx,"x"),DoubleArgumentType.getDouble(ctx,"y"),
                                                                DoubleArgumentType.getDouble(ctx,"z")))))))))
                .then(Commands.literal("list")
                        .then(Commands.literal("bodies").executes(ctx -> bodies(ctx.getSource())))
                        .then(Commands.literal("dimensions").executes(ctx -> dimensions(ctx.getSource())))
                        .then(Commands.literal("visuals").executes(ctx -> message(ctx.getSource(), SpaceVisualSync
                                .preview(SpaceChartService.state(ctx.getSource().getServer()).runtime.chart)))))
                .then(Commands.literal("universe")
                        .then(Commands.literal("status").executes(ctx -> message(ctx.getSource(), universeStatus(ctx.getSource()))))
                        .then(Commands.literal("list").executes(ctx -> message(ctx.getSource(), universeList(ctx.getSource()))))
                        .then(Commands.literal("growth").then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"off", "timer", "discovery", "both"}, b))
                                .executes(ctx -> {
                                    XenoSpaceConfig.setUniverseGrowth(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode"));
                                    net.bullettrain.xenopixelsmod.space.universe.UniverseService.syncAll(ctx.getSource().getServer());
                                    return message(ctx.getSource(), universeStatus(ctx.getSource()));
                                })))
                        .then(Commands.literal("logical")
                                .then(Commands.literal("status")
                                        .executes(ctx -> message(ctx.getSource(),
                                                logicalUniverseStatus(ctx.getSource()))))
                                .then(Commands.literal("on").executes(ctx -> {
                                    XenoSpaceConfig.setLogicalUniverseEnabled(true);
                                    return message(ctx.getSource(), "Full-width logical star-system generation enabled. Physical travel remains limited to mapped regions.");
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    XenoSpaceConfig.setLogicalUniverseEnabled(false);
                                    return message(ctx.getSource(), "Full-width logical star-system generation disabled.");
                                }))
                                .then(Commands.literal("reveal")
                                        .then(Commands.argument("x", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                                .then(Commands.argument("z", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                                        .requires(source -> source.hasPermission(2))
                                                        .executes(ctx -> message(ctx.getSource(),
                                                                net.bullettrain.xenopixelsmod.space.universe.UniverseService.revealLogical(
                                                                        ctx.getSource().getServer(),
                                                                        com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "x"),
                                                                        com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "z"))))))))
                        .then(Commands.literal("reveal").then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-228, 228))
                                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-228, 228))
                                        .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.universe.UniverseService.reveal(
                                                ctx.getSource().getServer(), new net.bullettrain.xenopixelsmod.space.universe.UniverseField.Cell(
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z")))
                                                ? "Revealed." : "Not revealed (empty cell, already known, or cap reached).")))))
                        .then(Commands.literal("warp").then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-228, 228))
                                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-228, 228))
                                        .executes(ctx -> universeWarp(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z")))))))
                .then(Commands.literal("hakai")
                        .then(Commands.literal("status").executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.status(ctx.getSource().getServer()))))
                        .then(Commands.literal("mode").then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"off", "erasable_only", "all"}, b))
                                .executes(ctx -> {
                                    XenoSpaceConfig.setHakaiPlanets(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode"));
                                    return message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.status(ctx.getSource().getServer()));
                                })))
                        .then(Commands.literal("erase").then(Commands.argument("body", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.adminErase(ctx.getSource().getServer(),
                                        net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.key(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "body")), ctx.getSource().getTextName())))))
                        .then(Commands.literal("restore").then(Commands.argument("body", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.restore(ctx.getSource().getServer(),
                                        net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.key(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "body")))))))
                        .then(Commands.literal("protect").then(Commands.argument("body", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.key(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "body"));
                                    return message(ctx.getSource(), (XenoSpaceConfig.setHakaiPlanetProtected(id, true) ? "protected " : "already protected: ") + id);
                                })))
                        .then(Commands.literal("unprotect").then(Commands.argument("body", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.key(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "body"));
                                    return message(ctx.getSource(), (XenoSpaceConfig.setHakaiPlanetProtected(id, false) ? "unprotected " : "was not protected: ") + id);
                                })))
                        .then(Commands.literal("restoreall").executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.restoreAll(ctx.getSource().getServer())))))
                .then(Commands.literal("dragonballs")
                        .then(Commands.literal("bench")
                            .then(Commands.literal("voxel").then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(4, 500))
                                    .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.bench(ctx.getSource().getServer(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.Form.VOXEL,
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), 2, 200_000, ctx.getSource().getPosition())))
                                    .then(Commands.argument("thickness", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 500))
                                            .then(Commands.argument("budget", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1_000, 10_000_000))
                                                    .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.bench(ctx.getSource().getServer(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.Form.VOXEL,
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "thickness"),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "budget"), ctx.getSource().getPosition())))))))
                            .then(Commands.literal("poly").then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(4, 500))
                                    .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.bench(ctx.getSource().getServer(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.Form.POLY,
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), 2, 200_000, ctx.getSource().getPosition())))
                                    .then(Commands.argument("thickness", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 500))
                                            .then(Commands.argument("budget", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1_000, 10_000_000))
                                                    .executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.bench(ctx.getSource().getServer(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.Form.POLY,
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "thickness"),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "budget"), ctx.getSource().getPosition())))))))
                            .then(Commands.literal("clear").executes(ctx -> { net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.clear(ctx.getSource().getServer()); return message(ctx.getSource(), "cleared"); }))
                            .then(Commands.literal("status").executes(ctx -> message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets.status()))))
                        .then(Commands.literal("status").executes(ctx -> message(ctx.getSource(),
                                net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.status(ctx.getSource().getServer()))))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.COUNT))
                                        .executes(ctx -> message(ctx.getSource(), "Recovery can duplicate a ball if its item remains in an offline inventory, container, or unloaded item entity. Repeat with `confirm` only if you accept that risk."))
                                        .then(Commands.literal("confirm").executes(ctx -> message(ctx.getSource(),
                                                net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.recoverHeld(ctx.getSource().getServer(),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "slot")))))))
                        .then(Commands.literal("scatter").executes(ctx -> {
                            net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.adminScatter(ctx.getSource().getServer());
                            return message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.status(ctx.getSource().getServer()));
                        })))
                .then(Commands.literal("orbits")
                        .then(Commands.literal("status").executes(ctx -> message(ctx.getSource(), orbitStatus())))
                        .then(Commands.literal("on").executes(ctx -> setOrbits(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setOrbits(ctx.getSource(), false)))
                        .then(Commands.literal("warp").then(Commands.argument("seconds",
                                        com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-1.0e9, 1.0e9))
                                .executes(ctx -> warpOrbits(ctx.getSource(),
                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "seconds")))))
                        .then(Commands.literal("resetwarp").executes(ctx -> {
                            XenoSpaceConfig.resetOrbitEpochOffset();
                            return message(ctx.getSource(), "Orbit epoch shift reset to 0. " + orbitStatus());
                        })))
                .then(Commands.literal("voiddamage")
                        .executes(ctx -> message(ctx.getSource(), SpaceVoidSafety.status(ctx.getSource().getServer(),
                                ctx.getSource().getLevel().dimension().location())))
                        .then(Commands.literal("on").executes(ctx -> setVoidDamage(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setVoidDamage(ctx.getSource(), false))))
                .then(Commands.literal("handoff")
                        .then(Commands.literal("status").executes(ctx -> message(ctx.getSource(),
                                SpaceHandoffPass.status(ctx.getSource().getServer()) + "\n" + SpaceFlightGuard.status())))
                        .then(Commands.literal("exit")
                                .then(Commands.argument("body", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .executes(ctx -> configureExit(ctx.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "body").toString(), null))
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                                                .executes(ctx -> configureExit(ctx.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "body").toString(),
                                                        DoubleArgumentType.getDouble(ctx, "y"))))))
                        .executes(ctx -> message(ctx.getSource(),
                                SpaceHandoffPass.status(ctx.getSource().getServer())
                                        + "\n" + SpaceFlightGuard.status()))
                        .then(Commands.literal("on").executes(ctx -> setHandoff(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setHandoff(ctx.getSource(), false)))
                        .then(Commands.literal("velocity")
                                .then(Commands.literal("carry").executes(ctx -> setCarryover(ctx.getSource(), true)))
                                .executes(ctx -> message(ctx.getSource(), "velocity carryover="
                                        + XenoSpaceConfig.velocityCarryover()))))
                .then(Commands.literal("sdb")
                        .then(Commands.literal("orbit")
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 7))
                                                .executes(ctx -> {
                                                    int slot = IntegerArgumentType.getInteger(ctx, "slot");
                                                    var server = ctx.getSource().getServer();
                                                    var saved = net.bullettrain.xenopixelsmod.space.dragonball.DragonBallSavedData.get(server);
                                                    if (!saved.clearManualOrbit(slot))
                                                        return message(ctx.getSource(), "SDB " + slot + " has no saved orbit override.");
                                                    net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetOrbitPass.refreshFromSaved(server);
                                                    return message(ctx.getSource(), "Cleared SDB " + slot + " orbit override; the authored chart track is active.");
                                                })))))
                .then(Commands.literal("travel")
                        .then(Commands.argument("body", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .executes(ctx -> travel(ctx.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "body").toString()))))
                .then(Commands.literal("validate").executes(ctx -> validate(ctx.getSource())))
                .then(Commands.literal("probe")
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                                                .executes(ctx -> probe(ctx.getSource(),
                                                        DoubleArgumentType.getDouble(ctx, "x"),
                                                        DoubleArgumentType.getDouble(ctx, "y"),
                                                        DoubleArgumentType.getDouble(ctx, "z")))))))
                .then(debugCommands())
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource()))
                        .then(Commands.literal("dimensions").executes(ctx -> {
                            return SpaceChartService.reloadDimensions(ctx.getSource().getServer())
                                    ? message(ctx.getSource(), SpaceChartService.status(ctx.getSource().getServer()))
                                    : failure(ctx.getSource(), "Dimension reload failed; previous chart retained");
                        })))
                .then(Commands.literal("perf").executes(ctx -> {
                    var runtime = SpaceChartService.state(ctx.getSource().getServer()).runtime;
                    message(ctx.getSource(), runtime.probe.report());
                    message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageService.report());
                    message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.dragonball.DragonBallBodyScan
                            .metricsReport(ctx.getSource().getServer()));
                    message(ctx.getSource(), runtime.activationMetrics.report(System.nanoTime()));
                    message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.physics.SpaceBuildActivationGate
                            .report(ctx.getSource().getServer()));
                    message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth
                            .status(ctx.getSource().getServer()));
                    message(ctx.getSource(), "scheduler budget="
                            + (XenoSpaceConfig.workPerPhysicsTick() <= 0 ? "unbudgeted"
                                    : XenoSpaceConfig.workPerPhysicsTick())
                            + " passes=" + SpaceTickScheduler.passCount());
                    long tick = ctx.getSource().getServer().overworld().getGameTime();
                    int bodyCap = XenoSpaceConfig.generatedBodyBuildVoxelsPerTick();
                    message(ctx.getSource(), bodyCap <= 0
                            ? "generated-body voxels this tick=unbudgeted (manager-local limits remain active)"
                            : "generated-body voxels this tick="
                                    + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.used(ctx.getSource().getServer(), tick)
                                    + "/" + bodyCap + " overrun=" + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget
                                            .overrun(ctx.getSource().getServer(), tick));
                    if (bodyCap > 0) {
                        message(ctx.getSource(), "generated-body shares chart="
                                + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.used(ctx.getSource().getServer(), tick,
                                        net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.CHART_PLANET)
                                + "/" + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.share(tick,
                                        net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.CHART_PLANET)
                                + " asteroids="
                                + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.used(ctx.getSource().getServer(), tick,
                                        net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.ASTEROID)
                                + "/" + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.share(tick,
                                        net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.ASTEROID)
                                + " small-planets="
                                + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.used(ctx.getSource().getServer(), tick,
                                        net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET)
                                + "/" + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.share(tick,
                                        net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET)
                                + " super-dragon-balls="
                                + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.used(ctx.getSource().getServer(), tick,
                                        net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL)
                                + "/" + net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.share(tick,
                                        net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL));
                    }
                    double buildStepCapMs = XenoSpaceConfig.generatedBodyBuildMillisPerTick();
                    message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.formatTimeReport(
                            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.usedMillis(ctx.getSource().getServer(), tick),
                            buildStepCapMs,
                            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.usedMillis(ctx.getSource().getServer(), tick,
                                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.CHART_PLANET),
                            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.usedMillis(ctx.getSource().getServer(), tick,
                                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.ASTEROID),
                            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.usedMillis(ctx.getSource().getServer(), tick,
                                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET),
                            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.usedMillis(ctx.getSource().getServer(), tick,
                                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL)));
                    message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPhysicalizer
                            .status(ctx.getSource().getServer()));
                    message(ctx.getSource(), net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer
                            .status(ctx.getSource().getServer()));
                    return message(ctx.getSource(), SpaceTickScheduler.probe().report());
                })
                        .then(Commands.literal("reset").executes(ctx -> {
                            SpaceChartService.state(ctx.getSource().getServer()).runtime.probe.reset();
                            SpaceTickScheduler.probe().reset();
                            return message(ctx.getSource(), "Space chart and scheduler samples reset");
                        }))));
    }

    /** Operator-only switches for reproducing proximity physicalization without a connected client. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> debugCommands() {
        return Commands.literal("debug").requires(source -> source.hasPermission(4))
                .then(Commands.literal("planet-near")
                        .then(Commands.argument("body", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .then(Commands.argument("near", BoolArgumentType.bool())
                                        .executes(ctx -> message(ctx.getSource(),
                                                net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.debugForceNear(
                                                        net.minecraft.commands.arguments.ResourceLocationArgument
                                                                .getId(ctx, "body").toString(),
                                                        BoolArgumentType.getBool(ctx, "near")))))))
                .then(Commands.literal("asteroid-viewer")
                        .then(Commands.literal("clear").executes(ctx -> message(ctx.getSource(),
                                net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPhysicalizer
                                        .debugViewer(0, 0, 0, false))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg(-30_000_000, 30_000_000))
                                                        .executes(ctx -> message(ctx.getSource(),
                                                                net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPhysicalizer
                                                                        .debugViewer(
                                                                                DoubleArgumentType.getDouble(ctx, "x"),
                                                                                DoubleArgumentType.getDouble(ctx, "y"),
                                                                                DoubleArgumentType.getDouble(ctx, "z"),
                                                                                true))))))));
    }

    private static int bodies(CommandSourceStack source) {
        var chart = SpaceChartService.state(source.getServer()).runtime.chart;
        message(source, "Space chart bodies=" + chart.size());
        for (int i = 0; i < chart.size(); i++) {
            var body = chart.body(i);
            message(source, body.id() + " kind=" + body.kind() + " dimension=" + body.dimensionId()
                    + " xyz=" + chart.x(i) + "," + chart.y(i) + "," + chart.z(i)
                    + " radius=" + body.radiusBlocks() + " exit=" + body.exitAltitude()
                    + " enterable=" + body.enterable() + " lod=" + chart.lodOf(i));
        }
        return chart.size();
    }
    private static String bodyId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx,"id").toString();
    }
    private static int changeBody(CommandSourceStack source,String id,boolean resume,double x,double y,double z) {
        var runtime=SpaceChartService.state(source.getServer()).runtime;
        try {
            if(resume) runtime.chart.resumeBody(id);else runtime.chart.moveBody(id,x,y,z);
            runtime.solved=runtime.chart.evaluate(source.getServer().overworld().getGameTime()/20.0+XenoSpaceConfig.orbitEpochOffsetSeconds());
            runtime.revision++;
            message(source,"Chart metadata changed. Physical Sable bodies are independent; moves are session-only.");
            return bodyState(source,id);
        } catch(IllegalArgumentException error) {return failure(source,error.getMessage());}
    }
    private static int bodyState(CommandSourceStack source,String id) {
        var chart=SpaceChartService.state(source.getServer()).runtime.chart;
        int slot=chart.slotOf(id);
        if(slot<0) return failure(source,"Unknown space body: "+id);
        var body=chart.body(slot);
        return message(source,id+" mode="+(chart.manuallyPositioned(slot)?"manual":"authored")
                +" position="+chart.x(slot)+","+chart.y(slot)+","+chart.z(slot)
                +" velocity="+chart.velocityX(slot)+","+chart.velocityY(slot)+","+chart.velocityZ(slot)
                +" dimension="+body.dimensionId()+" erasable="+body.erasable());
    }
    private static String universeStatus(CommandSourceStack source) {
        var level = net.bullettrain.xenopixelsmod.space.universe.UniverseService.voidsea(source.getServer());
        var data = level == null ? null : net.bullettrain.xenopixelsmod.space.universe.UniverseSavedData.get(level);
        int revealed = data == null ? 0 : data.size();
        boolean timerGrowth = net.bullettrain.xenopixelsmod.space.universe.UniverseGrowth.Mode
                .parse(XenoSpaceConfig.universeGrowth()).timer();
        String frontier = !timerGrowth ? "off" : data == null ? "unavailable"
                : data.size() >= XenoSpaceConfig.universeMaxRevealed() ? "capped"
                : !data.timerFrontierStarted() ? "queued"
                : data.timerFrontierReady() ? "ready"
                : "rebuilding=" + data.timerFrontierProcessed() + "/" + data.timerFrontierTotal();
        return "universe growth=" + XenoSpaceConfig.universeGrowth() + " revealed=" + revealed + "/" + XenoSpaceConfig.universeMaxRevealed()
                + " discoveriesPerDay=" + XenoSpaceConfig.universeDiscoveriesPerDay() + " warp=" + XenoSpaceConfig.universeWarp()
                + " timerFrontier=" + frontier
                + " cell=" + net.bullettrain.xenopixelsmod.space.universe.UniverseField.CELL_SIZE + " blocks";
    }

    private static String logicalUniverseStatus(CommandSourceStack source) {
        var level = net.bullettrain.xenopixelsmod.space.universe.UniverseService.voidsea(source.getServer());
        int logicalSaved = level == null ? 0
                : net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseSavedData.get(level).size();
        int finiteSaved = level == null ? 0
                : net.bullettrain.xenopixelsmod.space.universe.UniverseSavedData.get(level).size();
        return "logicalUniverseEnabled=" + XenoSpaceConfig.logicalUniverseEnabled()
                + " logicalSavedEntries=" + logicalSaved
                + " finiteAtlasEntries=" + finiteSaved
                + " newEntryCapPerStore=" + XenoSpaceConfig.universeMaxRevealed()
                + " (logical saved entries include the one-time legacy import; Atlas pages display the union)";
    }

    private static String universeList(CommandSourceStack source) {
        var level = net.bullettrain.xenopixelsmod.space.universe.UniverseService.voidsea(source.getServer());
        if (level == null) return "no Voidsea";
        long seed = net.bullettrain.xenopixelsmod.space.universe.UniverseService.fieldSeed(source.getServer());
        StringBuilder out = new StringBuilder("known systems:");
        int shown = 0;
        for (var entry : net.bullettrain.xenopixelsmod.space.universe.UniverseSavedData.get(level).entries().entrySet()) {
            if (++shown > 40) { out.append("\n ..."); break; }
            var system = net.bullettrain.xenopixelsmod.space.universe.UniverseField.systemAt(seed, entry.getKey());
            if (system == null) continue;
            out.append("\n ").append(entry.getKey().id()).append(' ').append(system.name()).append(" (")
                    .append(system.starClass().name().toLowerCase(java.util.Locale.ROOT)).append(", ").append(system.planetSlots())
                    .append(" planet slots) at ").append(Math.round(system.x())).append(", ").append(Math.round(system.z()))
                    .append(" [").append(entry.getValue().source().name().toLowerCase(java.util.Locale.ROOT)).append(" day ")
                    .append(entry.getValue().day()).append(']');
        }
        return out.toString();
    }

    /** Warp travel to a revealed system; the same rule as the Galaxy Atlas (UniverseService.warp). */
    private static int universeWarp(CommandSourceStack source, int x, int z) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return message(source, net.bullettrain.xenopixelsmod.space.universe.UniverseService.warp(source.getPlayerOrException(),
                new net.bullettrain.xenopixelsmod.space.universe.UniverseField.Cell(x, z)));
    }

    /** §20 live orbit switch: persisted and applied to the live chart immediately. */
    private static int setOrbits(CommandSourceStack source, boolean on) {
        XenoSpaceConfig.setRealOrbits(on);
        SpaceChartService.state(source.getServer()).runtime.chart.setRealOrbits(on);
        return message(source, (on ? "Real orbits on: bodies follow their Kepler orbits. "
                : "Real orbits off: bodies stay at their pinned chart positions. ") + orbitStatus());
    }

    /** §20 time warp: shifts the shared orbit epoch; refused unless orbitWarpAllowed is set in the space config. */
    private static int warpOrbits(CommandSourceStack source, double seconds) {
        if (!XenoSpaceConfig.orbitWarpAllowed())
            return message(source, "Orbit warp is disabled; set \"orbitWarpAllowed\": true in xenopixelsmod-space.json first.");
        double total = XenoSpaceConfig.addOrbitEpochOffset(seconds);
        return message(source, "Orbits warped by " + seconds + " s (total shift " + total + " s). " + orbitStatus());
    }

    private static String orbitStatus() {
        return "realOrbits=" + XenoSpaceConfig.realOrbits() + " warpAllowed=" + XenoSpaceConfig.orbitWarpAllowed()
                + " epochShift=" + XenoSpaceConfig.orbitEpochOffsetSeconds() + " s";
    }

    private static int setVoidDamage(CommandSourceStack source, boolean damageApplies) {
        XenoSpaceConfig.setVoidDamage(damageApplies);
        message(source, "Space void damage " + (damageApplies
                ? "on: falling out of a space dimension kills, as vanilla"
                : "off: falling out of a space dimension returns you to its handoff anchor"));
        return message(source, SpaceVoidSafety.status(source.getServer(),
                source.getLevel().dimension().location()));
    }
    private static int setHandoff(CommandSourceStack source, boolean active) {
        XenoSpaceConfig.setHandoffEnabled(active);
        message(source, "Altitude handoff " + (active
                ? "on: crossing a body's exit altitude moves you between its dimension and the Voidsea"
                : "off: altitude boundaries no longer move anyone"));
        if (!XenoSpaceConfig.enabled() && active) {
            message(source, "space.enabled is off: enable it in the config and run /xenospace reload; the handoff pass is registered");
        }
        return message(source, SpaceHandoffPass.status(source.getServer()));
    }

    private static int setSmallPlanets(CommandSourceStack source, boolean active) {
        XenoSpaceConfig.setSmallPlanetPhysical(active);
        resendSmallPlanetBindings(source);
        return message(source, net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer
                .status(source.getServer()));
    }

    private static int setBuildDeferFinalize(CommandSourceStack source, boolean active) {
        XenoSpaceConfig.setPlanetBuildDeferFinalize(active);
        return message(source, "defer-per-batch-finalize=" + active
                + "; applies to builds started after changing it. This reduces repeated Sable finalization work, "
                + "but does not prove a sub-8 ms build or remove the final batch cost.");
    }

    private static int setBuildBudget(CommandSourceStack source, boolean active) {
        XenoSpaceConfig.setGeneratedBodyBuildBudgetEnabled(active);
        return message(source, "shared generated-body voxel budget " + (active ? "enabled: "
                + XenoSpaceConfig.generatedBodyBuildVoxelsPerTick() + " voxels/tick" : "disabled")
                + "; positive millisecond limit remains best-effort because Sable allocation and callbacks are indivisible");
    }

    private static int setSmallPlanetField(CommandSourceStack source, boolean active) {
        XenoSpaceConfig.setSmallPlanetFieldEnabled(active);
        return message(source, "small-planet field " + (active ? "on" : "off")
                + "; distant candidates are deterministic and do not create Sable bodies");
    }

    private static int setSmallPlanetIncremental(CommandSourceStack source, boolean active) {
        XenoSpaceConfig.setSmallPlanetBuildIncrementalSections(active);
        return message(source, net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer
                .status(source.getServer()));
    }

    private static int setSmallPlanetGravity(CommandSourceStack source, boolean active) {
        XenoSpaceConfig.setSmallPlanetRadialGravity(active);
        resendSmallPlanetBindings(source);
        return message(source, "small-planet radial gravity " + (active ? "on" : "off")
                + "; it requires physical small planets and only affects walkable bodies in the Voidsea");
    }

    private static void resendSmallPlanetBindings(CommandSourceStack source) {
        var level = source.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST));
        if (level != null) net.bullettrain.xenopixelsmod.space.SpaceVisualSync.resendPlanetBindings(level);
    }

    private static int previewSmallPlanets(CommandSourceStack source, double range) {
        var position = source.getPosition();
        return message(source, net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer.preview(
                source.getServer(), position.x, position.y, position.z, range));
    }

    private static int setCarryover(CommandSourceStack source, boolean carry) {
        XenoSpaceConfig.setVelocityCarryover(carry);
        return message(source, "velocity carryover=" + carry
                + " (the inbound leg always arrives stationary; this is the climb out)");
    }

    private static int configureExit(CommandSourceStack source, String bodyId, Double y) {
        try { return message(source, SpaceHandoffPass.configureExit(source.getServer(), bodyId, y)); }
        catch (IllegalArgumentException e) { return failure(source, e.getMessage()); }
    }

    /** §12's secondary verb: an admin hop straight into a body's dimension at its surface anchor. */
    private static int travel(CommandSourceStack source, String bodyId) {
        var player = source.getPlayer();
        if (player == null) return failure(source, "travel moves a player; run it as one, not from the console");
        return message(source, SpaceHandoffPass.travel(source.getServer(), player, bodyId));
    }

    private static int probe(CommandSourceStack source, double x, double y, double z) {
        var state = SpaceChartService.state(source.getServer());
        var chart = state.runtime.chart;
        var result = chart.inspectAt(x, y, z);
        message(source, "Space math preview revision=" + state.runtime.revision + " at=" + x + "," + y + "," + z
                + " bodies=" + chart.size() + " proxies=" + result.proxies() + "/" + chart.proxyBudget());
        StringBuilder stages = new StringBuilder("Predicted stages (no terrain or forces):");
        for (BodyLod stage : BodyLod.values()) {
            stages.append(' ').append(stage.name()).append('=').append(result.stageCounts().get(stage.ordinal()));
        }
        message(source, stages.toString());
        return message(source, "Acceleration=" + result.ax() + "," + result.ay() + "," + result.az()
                + " contributors=" + result.contributors() + " rangeCulled=" + result.culled()
                + " (active chart unchanged; chart-space coordinates)");
    }
    private static int dimensions(CommandSourceStack source) {
        var state = SpaceChartService.state(source.getServer());
        source.getServer().levelKeys().stream().sorted(java.util.Comparator.comparing(key -> key.location().toString())).forEach(key -> {
            String id = key.location().toString();
            boolean charted = false;
            for (int i = 0; i < state.runtime.chart.size(); i++)
                charted |= id.equals(state.runtime.chart.body(i).dimensionId());
            message(source, id + " " + (charted ? "charted" : "inert")
                    + (state.sidecarDimensions.contains(id) ? " sidecar" : ""));
        });
        return source.getServer().levelKeys().size();
    }
    private static int validate(CommandSourceStack source) {
        try {
            List<String> errors = SpaceChartService.validate(source.getServer());
            message(source, "Space validation diagnostics=" + errors.size() + " (active chart unchanged)");
            errors.forEach(error -> message(source, error));
            return errors.isEmpty() ? 1 : 0;
        } catch (Exception e) { return failure(source, "Validation failed: " + e.getMessage()); }
    }
    /**
     * {@code push <vx> <vy> <vz>}: adds that velocity (blocks/s) to a managed planet in one impulse, standing in
     * for a ship's tow so the station-keeping return can be watched with {@code /xenospace earth}.
     */
    /** {@code /xenospace planets physical <mode>}: saves the mode and applies it live. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> physicalMode(String mode) {
        return Commands.literal(mode).executes(ctx -> {
            XenoSpaceConfig.setPhysicalPlanets(mode);
            return message(ctx.getSource(),
                    net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.applyMode(ctx.getSource().getServer()));
        });
    }
    private static int push(CommandSourceStack source, String bodyId, org.joml.Vector3d velocity) {
        var host = source.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST));
        if (host == null || !net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.push(host, bodyId, velocity))
            return failure(source, bodyId + " is not a loaded physical planet");
        return message(source, "pushed " + bodyId + " by " + velocity + " blocks/s");
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> pushCommand(String bodyId) {
        return Commands.literal("push")
                .then(Commands.argument("vx", DoubleArgumentType.doubleArg(-100, 100))
                .then(Commands.argument("vy", DoubleArgumentType.doubleArg(-100, 100))
                .then(Commands.argument("vz", DoubleArgumentType.doubleArg(-100, 100))
                .executes(ctx -> push(ctx.getSource(), bodyId, new org.joml.Vector3d(DoubleArgumentType.getDouble(ctx, "vx"),
                        DoubleArgumentType.getDouble(ctx, "vy"), DoubleArgumentType.getDouble(ctx, "vz")))))));
    }

    private static int reload(CommandSourceStack source) {
        var server = source.getServer();
        message(source, "Reloading selected server resources and space configuration");
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused, error) -> server.execute(() -> {
            if (error != null) { failure(source, "Resource reload failed; previous chart retained: " + error.getMessage()); return; }
            XenoSpaceConfig.load();
            // Cadence only: the live budget keeps its counters, so a reload mid-session never makes a
            // healthy pass look dead. The status line below echoes the new intervals.
            SpaceTickScheduler.applyIntervals();
            SpaceChartService.refresh(server);
            // A server tick may already have adopted the new resource set before this callback.
            // Rebuild from those definitions with the just-loaded config in either ordering.
            SpaceChartService.reloadDimensions(server);
            message(source, SpaceChartService.status(server));
        }));
        return 1;
    }
    private static int message(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false); return 1;
    }
    private static int failure(CommandSourceStack source, String text) {
        source.sendFailure(Component.literal(text)); return 0;
    }
    private static int createSpike(CommandSourceStack source, Integer radius) {
        try {
            return message(source, net.bullettrain.xenopixelsmod.space.physics.PhysicalPlanetSpike
                    .create(source.getLevel(), radius));
        } catch (IllegalStateException | IllegalArgumentException error) {
            return failure(source, error.getMessage());
        }
    }
    /**
     * The ceiling is a config value, not an argument to {@code create}, so an operator can raise it for a
     * bigger experiment without editing the file and reloading between attempts. Persisted immediately —
     * the next {@code create} reads it through {@link XenoSpaceConfig#spikeRadius(int)}.
     */
    private static int setSpikeRadius(CommandSourceStack source, int radius) {
        XenoSpaceConfig.setMaxSpikeRadius(radius);
        return message(source, "spike radius ceiling set to " + XenoSpaceConfig.maxSpikeRadius()
                + " voxels (123 at 3, ~2.1k at 8, ~17k at 16 — the assembly runs on the server thread)");
    }
}
