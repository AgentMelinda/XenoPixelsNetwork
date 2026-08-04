package net.bullettrain.xenopixelsmod.client.gui;

import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.BodyCalibrationPacket;
import net.bullettrain.xenopixelsmod.network.packet.FlightPlanRequestPacket;
import net.bullettrain.xenopixelsmod.network.packet.GuidanceControlPacket;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Accuracy-first, server-authoritative ballistic flight-plan editor. */
public final class FlightPlannerScreen extends Screen {
    private enum Tab { EASY, PLAN, AUTO_PLAN, CALCULATOR, BODY, PHASES, WAYPOINTS, ALTITUDE, ENGINE, FLEET, TELEMETRY }

    private final ClientScreens.GuidanceOpenData openData;
    private Tab tab = Tab.EASY;
    private BallisticFlightPlan.Settings settings = BallisticFlightPlan.Settings.defaults();
    private BallisticFlightPlan.Result result;
    private BlockPos currentTarget;
    private long selectedTargetShipId = -1L;
    private double guidanceStopDistance;
    private int easyCruiseY;
    private int revision;
    private int selectedWaypoint;
    private int draggedPhaseHandle = -1;
    private String notice = "Loading server plan...";
    private EditBox easyTargetBox, easyCruiseBox, targetBox, cutoffBox, apexBox, terminalBox, waypointX, waypointY, waypointZ, minimumYBox, ceilingYBox;
    private EditBox speedBox, engineApexBox, cruiseBox, gravityBox, dragBox, fleetBox, salvoBox, stopDistanceBox;
    private EditBox autoClearanceBox, autoCeilingBox, autoTerminalBox;
    private EditBox bodyBaseBox, bodyCenterBox, bodyNoseBox;
    private EditBox calculatorTargetBox, calculatorAngleBox;
    private EditBox calculatorCommandYBox, calculatorCommandAngleBox;
    private BlockPos bodyBase, bodyCenter, bodyNose;
    private int bodyMinX, bodyMinY, bodyMinZ, bodyMaxX, bodyMaxY, bodyMaxZ;
    private int selectedBodyAnchor;
    private final List<BlockPos> bodyVoxelSamples = new ArrayList<>();
    private double bodyYaw = Math.toRadians(-42.0);
    private double bodyPitch = Math.toRadians(24.0);
    private double bodyZoom = 1.0;
    private boolean bodyDragging;
    private boolean bodyDragMoved;
    private double bodyLastMouseX, bodyLastMouseY;
    private Button launchButton;
    private int panelX, panelY;
    private static final int PANEL_W = 430, PANEL_H = 260, GRAPH_X = 174, GRAPH_Y = 52, GRAPH_W = 238, GRAPH_H = 132;

    public FlightPlannerScreen(ClientScreens.GuidanceOpenData openData) {
        super(Component.literal("Ballistic Flight Planner"));
        this.openData = openData;
        this.currentTarget = new BlockPos(openData.x(), openData.y(), openData.z());
        this.guidanceStopDistance = openData.guidanceStopDistance();
        this.easyCruiseY = openData.cruiseY();
    }

    @Override protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        loadBodyVisualization();
        rebuildPlannerWidgets();
        request(FlightPlanRequestPacket.Action.LOAD);
    }

    private void loadBodyVisualization() {
        // Screen-open data comes from the synchronized server block entity. Do not
        // replace it with a possibly stale client BE snapshot when reopening the tab.
        bodyBase = openData.missileBase();
        bodyCenter = openData.missileCenter();
        bodyNose = openData.missileNose();
        bodyMinX = bodyCenter.getX() - 8; bodyMaxX = bodyCenter.getX() + 8;
        bodyMinY = bodyCenter.getY() - 8; bodyMaxY = bodyCenter.getY() + 8;
        bodyMinZ = bodyCenter.getZ() - 8; bodyMaxZ = bodyCenter.getZ() + 8;
        bodyVoxelSamples.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        try {
            var ship = VsShipHelper.getShipAt(minecraft.level, openData.computerPos());
            Object bounds = ship == null ? null : ship.getClass().getMethod("getShipAABB").invoke(ship);
            if (bounds != null) {
                bodyMinX = aabbInt(bounds, "minX");
                bodyMaxX = Math.max(bodyMinX, aabbInt(bounds, "maxX") - 1);
                bodyMinY = aabbInt(bounds, "minY");
                bodyMaxY = Math.max(bodyMinY, aabbInt(bounds, "maxY") - 1);
                bodyMinZ = aabbInt(bounds, "minZ");
                bodyMaxZ = Math.max(bodyMinZ, aabbInt(bounds, "maxZ") - 1);
            }
        } catch (Throwable ignored) {
        }

        long sx = Math.max(1L, (long) bodyMaxX - bodyMinX + 1L);
        long sy = Math.max(1L, (long) bodyMaxY - bodyMinY + 1L);
        long sz = Math.max(1L, (long) bodyMaxZ - bodyMinZ + 1L);
        // Bound the one-time hull sample and the number of per-frame projected voxels.
        double ratio = sx * (double) sy * sz / 20_000.0;
        int step = Math.max(1, (int) Math.ceil(Math.cbrt(Math.max(1.0, ratio))));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bodyMinX; x <= bodyMaxX; x += step) {
            for (int y = bodyMinY; y <= bodyMaxY; y += step) {
                for (int z = bodyMinZ; z <= bodyMaxZ; z += step) {
                    cursor.set(x, y, z);
                    if (!minecraft.level.hasChunkAt(cursor)
                            || minecraft.level.getBlockState(cursor).isAir()) continue;
                    if (bodyVoxelSamples.size() < 20_000) bodyVoxelSamples.add(cursor.immutable());
                }
            }
        }
        // Calibration markers are rendered separately below. Do not add an old/default
        // marker to the pick list when that position is air: doing so lets the UI select
        // a point that the authoritative server must reject as unoccupied.
    }

    private static int aabbInt(Object bounds, String component) throws ReflectiveOperationException {
        Object value = bounds.getClass().getMethod(component).invoke(bounds);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void rebuildPlannerWidgets() {
        clearWidgets();
        int x = panelX + 10;
        if (tab == Tab.EASY) {
            addRenderableWidget(Button.builder(Component.literal("EASY GUIDANCE"), b -> {})
                    .tooltip(Tooltip.create(Component.literal("The recommended guided launch workflow.")))
                    .bounds(x, panelY + 25, 146, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Advanced settings  >"), b -> switchTab(Tab.PLAN))
                    .tooltip(Tooltip.create(Component.literal("Open manual ballistic, body, engine and fleet controls.")))
                    .bounds(panelX + 266, panelY + 25, 146, 18).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("< Easy"), b -> switchTab(Tab.EASY))
                    .tooltip(Tooltip.create(Component.literal("Return to the recommended simple workflow.")))
                    .bounds(x, panelY + 25, 42, 18).build());
            x += 44;
            int advancedCount = Tab.values().length - 1;
            int tabWidth = (PANEL_W - 64 - (advancedCount - 1) * 2) / advancedCount;
            for (Tab value : Tab.values()) {
                if (value == Tab.EASY) continue;
                addRenderableWidget(Button.builder(Component.literal(tabName(value)), b -> switchTab(value))
                        .tooltip(Tooltip.create(Component.literal(tabHelp(value))))
                        .bounds(x, panelY + 25, tabWidth, 18).build());
                x += tabWidth + 2;
            }
        }
        switch (tab) {
            case EASY -> initEasy();
            case PLAN -> initPlan();
            case AUTO_PLAN -> initAutoPlan();
            case CALCULATOR -> initCalculator();
            case BODY -> initBody();
            case PHASES -> initPhases();
            case WAYPOINTS -> initWaypoints();
            case ALTITUDE -> initAltitude();
            case ENGINE -> initEngine();
            case FLEET -> initFleet();
            case TELEMETRY -> { }
        }
        int bottom = panelY + PANEL_H - 24;
        addRenderableWidget(Button.builder(Component.literal("Check Route"), b -> request(FlightPlanRequestPacket.Action.PREVIEW))
                .tooltip(Tooltip.create(Component.literal("Ask the server to simulate this route. This does not launch.")))
                .bounds(panelX + 174, bottom, 72, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> request(FlightPlanRequestPacket.Action.APPLY))
                .tooltip(Tooltip.create(Component.literal("Save this flight plan without launching.")))
                .bounds(panelX + 250, bottom, 58, 18).build());
        launchButton = addRenderableWidget(Button.builder(Component.literal("LAUNCH"), b -> launch())
                .tooltip(Tooltip.create(Component.literal("Save this plan and launch in one server-safe operation.")))
                .bounds(panelX + 312, bottom, 72, 18).build());
        launchButton.active = result != null && result.feasible()
                && settings.equals(result.settings());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .tooltip(Tooltip.create(Component.literal("Close guidance.")))
                .bounds(panelX + 388, bottom, 24, 18).build());
    }

    /** One-screen beginner workflow; advanced tabs remain available above. */
    private void initEasy() {
        easyTargetBox = box(panelX + 12, panelY + 72, 146,
                xyz(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ()));
        easyTargetBox.setResponder(value -> invalidatePreview("Target changed — build and check a new route."));
        addRenderableWidget(Button.builder(Component.literal(easyLabel(BallisticFlightPlan.TrajectoryProfile.AUTO, "Safe Auto")), b ->
                        setEasyStyle(BallisticFlightPlan.TrajectoryProfile.AUTO,
                                BallisticFlightPlan.ArcPreference.AUTO))
                .tooltip(Tooltip.create(Component.literal("Recommended. Guidance chooses a safe route for the target distance.")))
                .bounds(panelX + 12, panelY + 98, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal(easyLabel(BallisticFlightPlan.TrajectoryProfile.DIRECT, "Low / Fast")), b ->
                        setEasyStyle(BallisticFlightPlan.TrajectoryProfile.DIRECT,
                                BallisticFlightPlan.ArcPreference.LOW))
                .tooltip(Tooltip.create(Component.literal("A lower, faster route. Best when terrain is clear.")))
                .bounds(panelX + 88, panelY + 98, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal(easyLabel(BallisticFlightPlan.TrajectoryProfile.PARABOLIC, "High Arc")), b ->
                        setEasyStyle(BallisticFlightPlan.TrajectoryProfile.PARABOLIC,
                                BallisticFlightPlan.ArcPreference.HIGH))
                .tooltip(Tooltip.create(Component.literal("A high curved route for clearing terrain and obstacles.")))
                .bounds(panelX + 12, panelY + 120, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal(easyLabel(BallisticFlightPlan.TrajectoryProfile.TOP_ATTACK, "Top Attack")), b ->
                        setEasyStyle(BallisticFlightPlan.TrajectoryProfile.TOP_ATTACK,
                                BallisticFlightPlan.ArcPreference.HIGH))
                .tooltip(Tooltip.create(Component.literal("Approach above the target, then make a steep terminal dive.")))
                .bounds(panelX + 88, panelY + 120, 70, 18).build());
        easyCruiseBox = box(panelX + 12, panelY + 146, 70, Integer.toString(easyCruiseY));
        easyCruiseBox.setHint(Component.literal("Cruise Y"));
        addRenderableWidget(Button.builder(Component.literal("SET CRUISE Y"), b -> setEasyCruiseY())
                .tooltip(Tooltip.create(Component.literal(
                        "Desired world Y for the cruise phase. Use 0 to let the flight planner choose automatically.")))
                .bounds(panelX + 88, panelY + 146, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("3. BUILD + CHECK ROUTE"), b -> autoTuneForDistance())
                .tooltip(Tooltip.create(Component.literal("Automatically builds the route and immediately checks it on the server.")))
                .bounds(panelX + 12, panelY + 172, 146, 22).build());
        addRenderableWidget(Button.builder(Component.literal("4. FIND ENGINES (OPTIONAL)"), b -> {
                    ModNetwork.sendToServer(GuidanceControlPacket.pairNearby(openData.computerPos()));
                    notice = "Engine search requested. Launch when the checked route is ready.";
                }).tooltip(Tooltip.create(Component.literal("Pairs this mod's thrusters and supported engines from other mods.")))
                .bounds(panelX + 12, panelY + 200, 146, 20).build());
    }

    private void setEasyCruiseY() {
        try {
            easyCruiseY = Math.max(0, Integer.parseInt(easyCruiseBox.getValue().trim()));
            easyCruiseBox.setValue(Integer.toString(easyCruiseY));
            ModNetwork.sendToServer(GuidanceControlPacket.setCruiseY(openData.computerPos(), easyCruiseY));
            invalidatePreview(easyCruiseY == 0
                    ? "Easy cruise Y set to automatic — build and check the route again"
                    : "Easy cruise Y set to " + easyCruiseY + " — build and check the route again");
        } catch (NumberFormatException ex) {
            notice = "Cruise Y must be a whole number (0 = automatic)";
        }
    }

    private void setEasyStyle(BallisticFlightPlan.TrajectoryProfile profile,
                              BallisticFlightPlan.ArcPreference arc) {
        captureVisibleFields();
        BallisticFlightPlan.Settings defaults = BallisticFlightPlan.Settings.defaults();
        settings = new BallisticFlightPlan.Settings(true,
                BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE, arc, profile,
                true, settings.waypointLayerEnabled(), true,
                defaults.motorCutoffFraction(), defaults.apexFraction(), defaults.terminalFraction(),
                settings.minimumClearanceY(), settings.ceilingY(), 0.0,
                settings.angleCommandY(), settings.angleCommandDeg(), settings.waypoints());
        result = null;
        notice = "Selected " + easyStyleName(profile) + ". Press BUILD SAFE ROUTE.";
        rebuildPlannerWidgets();
    }

    private void initPlan() {
        targetBox = box(panelX + 12, panelY + 75, 146, xyz(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ()));
        addRenderableWidget(Button.builder(Component.literal(settings.autoEnabled() ? "AUTO: ON" : "AUTO: OFF"), b -> replace(!settings.autoEnabled(), settings.mode(), settings.arc(), settings.phaseLayerEnabled(), settings.waypointLayerEnabled(), settings.altitudeLayerEnabled())).bounds(panelX + 12, panelY + 101, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal(modeName()), b -> replace(settings.autoEnabled(), settings.mode() == BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE ? BallisticFlightPlan.FlightMode.PURE_BALLISTIC : BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE, settings.arc(), settings.phaseLayerEnabled(), settings.waypointLayerEnabled(), settings.altitudeLayerEnabled())).bounds(panelX + 12, panelY + 123, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal("ARC: " + arcName()), b -> replace(settings.autoEnabled(), settings.mode(), nextArc(settings.arc()), settings.phaseLayerEnabled(), settings.waypointLayerEnabled(), settings.altitudeLayerEnabled())).bounds(panelX + 12, panelY + 145, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal("PATH: " + profileName()), b -> {
                    captureVisibleFields();
                    settings = new BallisticFlightPlan.Settings(settings.autoEnabled(), settings.mode(), settings.arc(),
                            nextProfile(settings.profile()), settings.phaseLayerEnabled(), settings.waypointLayerEnabled(),
                            settings.altitudeLayerEnabled(), settings.motorCutoffFraction(), settings.apexFraction(),
                            settings.terminalFraction(), settings.minimumClearanceY(), settings.ceilingY(),
                            settings.desiredAngleDeg(), settings.angleCommandY(), settings.angleCommandDeg(),
                            settings.waypoints());
                    rebuildPlannerWidgets();
                }).bounds(panelX + 12, panelY + 167, 146, 18).build());
        if (selectedTargetShipId >= 0) {
            addRenderableWidget(Button.builder(Component.literal("MOVING SHIP #" + selectedTargetShipId), b -> {
                        selectedTargetShipId = -1L;
                        notice = "Switched to fixed-coordinate target";
                        rebuildPlannerWidgets();
                    })
                    .bounds(panelX + 12, panelY + 211, 146, 18).build());
        }
    }

    private void initAutoPlan() {
        addRenderableWidget(Button.builder(Component.literal("ARC: " + arcName()), b -> {
                    captureVisibleFields();
                    replace(settings.autoEnabled(), settings.mode(), nextArc(settings.arc()), true,
                            settings.waypointLayerEnabled(), true);
                }).bounds(panelX + 12, panelY + 66, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal("PATH: " + profileName()), b -> {
                    captureVisibleFields();
                    settings = new BallisticFlightPlan.Settings(true, settings.mode(), settings.arc(),
                            nextProfile(settings.profile()), true, settings.waypointLayerEnabled(), true,
                            settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(),
                            settings.minimumClearanceY(), settings.ceilingY(), settings.desiredAngleDeg(),
                            settings.angleCommandY(), settings.angleCommandDeg(),
                            settings.waypoints());
                    rebuildPlannerWidgets();
                }).bounds(panelX + 12, panelY + 88, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal(modeName()), b -> {
                    captureVisibleFields();
                    BallisticFlightPlan.FlightMode next = settings.mode()
                            == BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE
                            ? BallisticFlightPlan.FlightMode.PURE_BALLISTIC
                            : BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE;
                    replace(true, next, settings.arc(), true, settings.waypointLayerEnabled(), true);
                }).bounds(panelX + 12, panelY + 110, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal("BUILD FOR DISTANCE"), b -> autoTuneForDistance())
                .bounds(panelX + 12, panelY + 132, 146, 18).build());
        autoClearanceBox = box(panelX + 12, panelY + 165, 70, decimal(settings.minimumClearanceY()));
        autoCeilingBox = box(panelX + 88, panelY + 165, 70, decimal(settings.ceilingY()));
        autoTerminalBox = box(panelX + 12, panelY + 199, 70, decimal(settings.terminalFraction()));
        addRenderableWidget(Button.builder(Component.literal("Preview Auto"), b -> request(FlightPlanRequestPacket.Action.PREVIEW))
                .bounds(panelX + 88, panelY + 199, 70, 18).build());
    }

    private void initCalculator() {
        calculatorTargetBox = box(panelX + 12, panelY + 68, 146,
                xyz(currentTarget.getX(), currentTarget.getY(), currentTarget.getZ()));
        double shownAngle = settings.desiredAngleDeg() > 0.0 ? settings.desiredAngleDeg()
                : result != null ? result.elevationDeg() : 45.0;
        calculatorAngleBox = box(panelX + 12, panelY + 96, 70, decimal(shownAngle));
        addRenderableWidget(Button.builder(Component.literal(settings.desiredAngleDeg() > 0.0
                        ? "ANGLE LOCKED" : "USE ANGLE"), b -> lockCalculatorAngle())
                .bounds(panelX + 86, panelY + 96, 72, 18).build());
        addRenderableWidget(Button.builder(Component.literal("AUTO ANGLE: " + arcName()), b -> {
                    captureVisibleFields();
                    settings = withAngle(0.0, BallisticFlightPlan.FlightMode.PURE_BALLISTIC,
                            nextArc(settings.arc()));
                    rebuildPlannerWidgets();
                    request(FlightPlanRequestPacket.Action.PREVIEW);
                }).bounds(panelX + 12, panelY + 122, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal(modeName()), b -> {
                    captureVisibleFields();
                    BallisticFlightPlan.FlightMode next = settings.mode()
                            == BallisticFlightPlan.FlightMode.PURE_BALLISTIC
                            ? BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE
                            : BallisticFlightPlan.FlightMode.PURE_BALLISTIC;
                    settings = withAngle(settings.desiredAngleDeg(), next, settings.arc());
                    rebuildPlannerWidgets();
                }).bounds(panelX + 12, panelY + 148, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Calculate Angle / Power"), b ->
                        request(FlightPlanRequestPacket.Action.PREVIEW))
                .bounds(panelX + 12, panelY + 174, 146, 18).build());
        calculatorCommandYBox = box(panelX + 12, panelY + 209, 70,
                settings.angleCommandY() > 0.0 ? decimal(settings.angleCommandY()) : "0");
        calculatorCommandAngleBox = box(panelX + 88, panelY + 209, 70,
                settings.angleCommandY() > 0.0 ? decimal(settings.angleCommandDeg()) : "0");
    }

    private void lockCalculatorAngle() {
        try {
            // Preserve an altitude-pitch program typed alongside the launch-angle field.
            captureVisibleFields();
            currentTarget = parseBlockPos(calculatorTargetBox.getValue(), currentTarget);
            double angle = Math.max(1.0, Math.min(89.0,
                    Double.parseDouble(calculatorAngleBox.getValue().trim())));
            settings = withAngle(angle, BallisticFlightPlan.FlightMode.PURE_BALLISTIC, settings.arc());
            notice = String.format(Locale.ROOT, "Desired launch angle locked at %.2f°", angle);
            rebuildPlannerWidgets();
            request(FlightPlanRequestPacket.Action.PREVIEW);
        } catch (NumberFormatException ex) {
            notice = "Angle must be 1..89 degrees and target must be X Y Z";
        }
    }

    private BallisticFlightPlan.Settings withAngle(double angle,
                                                   BallisticFlightPlan.FlightMode mode,
                                                   BallisticFlightPlan.ArcPreference arc) {
        return new BallisticFlightPlan.Settings(settings.autoEnabled(), mode, arc, settings.profile(),
                settings.phaseLayerEnabled(),
                mode == BallisticFlightPlan.FlightMode.PURE_BALLISTIC
                        ? false : settings.waypointLayerEnabled(),
                mode == BallisticFlightPlan.FlightMode.PURE_BALLISTIC
                        ? false : settings.altitudeLayerEnabled(),
                settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(),
                settings.minimumClearanceY(), settings.ceilingY(), angle,
                settings.angleCommandY(), settings.angleCommandDeg(), settings.waypoints());
    }

    private void initPhases() {
        cutoffBox = labeledBox("Motor cutoff", settings.motorCutoffFraction(), panelY + 76);
        apexBox = labeledBox("Apex", settings.apexFraction(), panelY + 112);
        terminalBox = labeledBox("Terminal", settings.terminalFraction(), panelY + 148);
        addRenderableWidget(Button.builder(Component.literal(settings.phaseLayerEnabled() ? "PHASE LAYER: ON" : "PHASE LAYER: OFF"), b -> replace(settings.autoEnabled(), settings.mode(), settings.arc(), !settings.phaseLayerEnabled(), settings.waypointLayerEnabled(), settings.altitudeLayerEnabled())).bounds(panelX + 12, panelY + 174, 146, 18).build());
    }

    private void initBody() {
        bodyBaseBox = box(panelX + 12, panelY + 70, 146, xyz(bodyBase));
        bodyCenterBox = box(panelX + 12, panelY + 108, 146, xyz(bodyCenter));
        bodyNoseBox = box(panelX + 12, panelY + 146, 146, xyz(bodyNose));
        addRenderableWidget(Button.builder(Component.literal(anchorButton(0, "BASE")), b -> selectBodyAnchor(0))
                .bounds(panelX + 12, panelY + 168, 46, 18).build());
        addRenderableWidget(Button.builder(Component.literal(anchorButton(1, "CENTER")), b -> selectBodyAnchor(1))
                .bounds(panelX + 62, panelY + 168, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal(anchorButton(2, "NOSE")), b -> selectBodyAnchor(2))
                .bounds(panelX + 114, panelY + 168, 44, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> applyBodyCalibration())
                .bounds(panelX + 12, panelY + 194, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Defaults"), b -> resetBodyCalibration())
                .bounds(panelX + 64, panelY + 194, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("View"), b -> resetBodyView())
                .bounds(panelX + 120, panelY + 194, 38, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SWAP BASE / NOSE"), b -> swapBodyEnds())
                .tooltip(Tooltip.create(Component.literal(
                        "Use if the missile flies tail-first. Swaps the two ends and saves the corrected nose axis.")))
                .bounds(panelX + 12, panelY + 218, 146, 18).build());
    }

    private String anchorButton(int anchor, String name) {
        return selectedBodyAnchor == anchor ? ">" + name : name;
    }

    private void selectBodyAnchor(int anchor) {
        captureBodyFields();
        selectedBodyAnchor = Math.max(0, Math.min(2, anchor));
        rebuildPlannerWidgets();
    }

    private void applyBodyCalibration() {
        try {
            captureBodyFields();
            String invalid = localBodyCalibrationError();
            if (invalid != null) {
                notice = invalid;
                return;
            }
            ModNetwork.sendToServer(new BodyCalibrationPacket(openData.computerPos(),
                    bodyBase, bodyCenter, bodyNose));
            notice = "Body calibration sent; server is validating occupied ship blocks";
        } catch (NumberFormatException ex) {
            notice = "Body blocks must be ship-local X Y Z";
        }
    }

    private String localBodyCalibrationError() {
        if (bodyBase.equals(bodyCenter) || bodyBase.equals(bodyNose) || bodyCenter.equals(bodyNose)) {
            return "Choose three different occupied blocks";
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return "World is not available";
        BlockPos[] positions = {bodyBase, bodyCenter, bodyNose};
        String[] names = {"Base", "Center", "Nose"};
        for (int i = 0; i < positions.length; i++) {
            BlockPos position = positions[i];
            if (!minecraft.level.hasChunkAt(position)) return names[i] + " block is not loaded";
            if (minecraft.level.getBlockState(position).isAir()) return names[i] + " position is not occupied";
        }
        return null;
    }

    private void resetBodyCalibration() {
        bodyCenter = openData.computerPos();
        bodyBase = bodyCenter.below();
        bodyNose = bodyCenter.above();
        selectedBodyAnchor = 2;
        rebuildPlannerWidgets();
        notice = "Default local +Y nose selected; Apply Body to save";
    }

    private void resetBodyView() {
        bodyYaw = Math.toRadians(-42.0);
        bodyPitch = Math.toRadians(24.0);
        bodyZoom = 1.0;
        notice = "3D camera reset";
    }

    private void swapBodyEnds() {
        captureBodyFields();
        BlockPos oldBase = bodyBase;
        bodyBase = bodyNose;
        bodyNose = oldBase;
        selectedBodyAnchor = 2;
        rebuildPlannerWidgets();
        ModNetwork.sendToServer(new BodyCalibrationPacket(openData.computerPos(),
                bodyBase, bodyCenter, bodyNose));
        notice = "Base/nose swapped and saved — red NOSE end will face the route";
    }

    private void captureBodyFields() {
        if (bodyBaseBox == null) return;
        bodyBase = parseBlockPos(bodyBaseBox.getValue(), bodyBase);
        bodyCenter = parseBlockPos(bodyCenterBox.getValue(), bodyCenter);
        bodyNose = parseBlockPos(bodyNoseBox.getValue(), bodyNose);
    }

    private static BlockPos parseBlockPos(String value, BlockPos fallback) {
        String[] split = value.trim().split("[,\\s]+");
        if (split.length != 3) throw new NumberFormatException("XYZ required");
        return new BlockPos(Integer.parseInt(split[0]), Integer.parseInt(split[1]),
                Integer.parseInt(split[2]));
    }

    private void initWaypoints() {
        List<BallisticFlightPlan.Waypoint> points = settings.waypoints();
        if (!points.isEmpty()) selectedWaypoint = Math.min(selectedWaypoint, points.size() - 1);
        BallisticFlightPlan.Waypoint p = points.isEmpty() ? new BallisticFlightPlan.Waypoint(0, 256, 0, false) : points.get(selectedWaypoint);
        waypointX = labeledBox("Waypoint X", p.x(), panelY + 68);
        waypointY = labeledBox("Waypoint Y", p.y(), panelY + 100);
        waypointZ = labeledBox("Waypoint Z", p.z(), panelY + 132);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> selectWaypoint(-1)).bounds(panelX + 12, panelY + 162, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal((points.isEmpty() ? 0 : selectedWaypoint + 1) + "/" + points.size()), b -> {}).bounds(panelX + 44, panelY + 162, 50, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> selectWaypoint(1)).bounds(panelX + 98, panelY + 162, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> addWaypoint()).bounds(panelX + 130, panelY + 162, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeWaypoint()).bounds(panelX + 12, panelY + 184, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal(settings.waypointLayerEnabled() ? "LAYER ON" : "LAYER OFF"), b -> replace(settings.autoEnabled(), settings.mode(), settings.arc(), settings.phaseLayerEnabled(), !settings.waypointLayerEnabled(), settings.altitudeLayerEnabled())).bounds(panelX + 86, panelY + 184, 72, 18).build());
        addRenderableWidget(Button.builder(Component.literal(p.locked() ? "Unlock" : "Lock"), b -> toggleWaypointLock()).bounds(panelX + 12, panelY + 206, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Up"), b -> moveWaypoint(-1)).bounds(panelX + 68, panelY + 206, 42, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Down"), b -> moveWaypoint(1)).bounds(panelX + 114, panelY + 206, 44, 18).build());
        if (ModList.get().isLoaded("xaeroworldmap")) {
            addRenderableWidget(Button.builder(Component.literal("SELECT XAERO WAYPOINT"), b -> openXaeroWaypointPicker())
                    .tooltip(Tooltip.create(Component.literal(
                            "Choose an existing waypoint from Xaero's waypoint manager or select its map icon.")))
                    .bounds(panelX + GRAPH_X, panelY + 190, GRAPH_W, 18).build());
        }
    }

    private void openXaeroWaypointPicker() {
        captureVisibleFields();
        try {
            net.bullettrain.xenopixelsmod.compat.xaero.XaeroWaypointPicker.open(this);
        } catch (Throwable error) {
            notice = "Xaero World Map is installed but could not be opened";
        }
    }

    /** Called by the optional Xaero hook after selecting a managed Xaero waypoint. */
    public void acceptXaeroWaypoint(int x, int y, int z, boolean useY, String xaeroName) {
        ArrayList<BallisticFlightPlan.Waypoint> points = new ArrayList<>(settings.waypoints());
        if (points.isEmpty()) {
            if (points.size() >= BallisticFlightPlan.MAX_WAYPOINTS) return;
            double selectedY = useY ? y : Math.max(256, currentTarget.getY());
            points.add(new BallisticFlightPlan.Waypoint(x, selectedY, z, false));
            selectedWaypoint = 0;
        } else {
            selectedWaypoint = Math.max(0, Math.min(selectedWaypoint, points.size() - 1));
            BallisticFlightPlan.Waypoint old = points.get(selectedWaypoint);
            points.set(selectedWaypoint, new BallisticFlightPlan.Waypoint(
                    x, useY ? y : old.y(), z, old.locked()));
        }
        settings = copy(settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(),
                settings.minimumClearanceY(), settings.ceilingY(), points);
        String displayName = xaeroName == null || xaeroName.isBlank() ? "Xaero waypoint" : xaeroName;
        invalidatePreview(displayName + " selected for route point " + (selectedWaypoint + 1)
                + ": " + x + ", " + (useY ? y : Math.round(points.get(selectedWaypoint).y()))
                + ", " + z + " — check route again");
        rebuildPlannerWidgets();
    }

    public void rejectXaeroWaypoint(String reason) {
        notice = reason;
        rebuildPlannerWidgets();
    }

    private void initAltitude() {
        minimumYBox = labeledBox("Minimum Y", settings.minimumClearanceY(), panelY + 76);
        ceilingYBox = labeledBox("Ceiling Y (0=off)", settings.ceilingY(), panelY + 112);
        addRenderableWidget(Button.builder(Component.literal(settings.altitudeLayerEnabled() ? "ALTITUDE LAYER: ON" : "ALTITUDE LAYER: OFF"), b -> replace(settings.autoEnabled(), settings.mode(), settings.arc(), settings.phaseLayerEnabled(), settings.waypointLayerEnabled(), !settings.altitudeLayerEnabled())).bounds(panelX + 12, panelY + 146, 146, 18).build());
        stopDistanceBox = box(panelX + 12, panelY + 178, 70, decimal(guidanceStopDistance));
        addRenderableWidget(Button.builder(Component.literal("Set Stop"), b -> setGuidanceStopDistance())
                .tooltip(Tooltip.create(Component.literal(
                        "Stop thrust and automatic stabilization this many blocks from the target. 0 = automatic hull-safe distance.")))
                .bounds(panelX + 86, panelY + 178, 72, 18).build());
    }

    private void setGuidanceStopDistance() {
        try {
            double blocks = Math.max(0.0, Math.min(100_000.0,
                    Double.parseDouble(stopDistanceBox.getValue().trim())));
            guidanceStopDistance = blocks;
            ModNetwork.sendToServer(GuidanceControlPacket.setStopDistance(openData.computerPos(), blocks));
            notice = blocks <= 0.0 ? "Guidance stop distance set to automatic"
                    : String.format(Locale.ROOT, "Guidance will stop %.2f blocks from target", blocks);
        } catch (NumberFormatException ex) {
            notice = "Stop distance must be a number from 0 to 100000";
        }
    }

    private void initEngine() {
        speedBox = box(panelX + 12, panelY + 66, 70, Integer.toString(openData.speedLevel()));
        addRenderableWidget(Button.builder(Component.literal("Set Power"), b -> setEnginePower())
                .bounds(panelX + 86, panelY + 66, 72, 18).build());
        engineApexBox = box(panelX + 12, panelY + 92, 70, Integer.toString(openData.apexY()));
        addRenderableWidget(Button.builder(Component.literal("Set Apex"), b -> setEngineApex())
                .bounds(panelX + 86, panelY + 92, 72, 18).build());
        cruiseBox = box(panelX + 12, panelY + 118, 70, Integer.toString(openData.cruiseY()));
        addRenderableWidget(Button.builder(Component.literal("Set Cruise"), b -> setEngineCruise())
                .bounds(panelX + 86, panelY + 118, 72, 18).build());
        gravityBox = box(panelX + 12, panelY + 144, 70, decimal(openData.gravitySi()));
        dragBox = box(panelX + 86, panelY + 144, 72, decimal(openData.dragCoefficient()));
        addRenderableWidget(Button.builder(Component.literal("Apply Physics"), b -> applyPhysics())
                .bounds(panelX + 12, panelY + 170, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Pair Thrusters"), b -> {
                    ModNetwork.sendToServer(GuidanceControlPacket.pairNearby(openData.computerPos()));
                    notice = "Pairing nearby thrusters";
                }).bounds(panelX + 12, panelY + 196, 82, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Unpair"), b -> {
                    ModNetwork.sendToServer(GuidanceControlPacket.clearPairs(openData.computerPos()));
                    notice = "Thrusters unpaired";
                }).bounds(panelX + 98, panelY + 196, 60, 18).build());
        addRenderableWidget(Button.builder(Component.literal("ABORT FLIGHT"), b -> {
                    ModNetwork.sendToServer(GuidanceControlPacket.abort(openData.computerPos()));
                    notice = "Abort requested";
                }).bounds(panelX + GRAPH_X, panelY + 190, GRAPH_W, 18).build());
    }

    private void initFleet() {
        fleetBox = box(panelX + 12, panelY + 78, 146, Integer.toString(openData.fleetChannel()));
        salvoBox = box(panelX + 12, panelY + 116, 146, Integer.toString(openData.salvoIntervalTicks()));
        addRenderableWidget(Button.builder(Component.literal("Apply Fleet"), b -> applyFleet())
                .bounds(panelX + 12, panelY + 146, 146, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Launch Fleet Salvo"), b -> {
                    applyFleet();
                    ModNetwork.sendToServer(GuidanceControlPacket.fleetSalvo(openData.computerPos()));
                    notice = "Fleet salvo requested";
                }).bounds(panelX + 12, panelY + 172, 146, 18).build());
    }

    private void setEnginePower() {
        try {
            int value = Math.max(1, Math.min(20, Integer.parseInt(speedBox.getValue().trim())));
            speedBox.setValue(Integer.toString(value));
            ModNetwork.sendToServer(GuidanceControlPacket.setSpeed(openData.computerPos(), value));
            notice = "Engine power set to " + value;
        } catch (NumberFormatException ex) { notice = "Power must be 1..20"; }
    }

    private void setEngineApex() {
        try {
            int value = Math.max(0, Integer.parseInt(engineApexBox.getValue().trim()));
            ModNetwork.sendToServer(GuidanceControlPacket.setApexY(openData.computerPos(), value));
            notice = value == 0 ? "Legacy apex override disabled" : "Engine apex set to " + value;
        } catch (NumberFormatException ex) { notice = "Apex must be a whole world Y"; }
    }

    private void setEngineCruise() {
        try {
            int value = Math.max(0, Integer.parseInt(cruiseBox.getValue().trim()));
            ModNetwork.sendToServer(GuidanceControlPacket.setCruiseY(openData.computerPos(), value));
            notice = value == 0 ? "Legacy cruise override disabled" : "Engine cruise set to " + value;
        } catch (NumberFormatException ex) { notice = "Cruise must be a whole world Y"; }
    }

    private void applyPhysics() {
        try {
            double gravity = Double.parseDouble(gravityBox.getValue().trim());
            double drag = Double.parseDouble(dragBox.getValue().trim());
            ModNetwork.sendToServer(GuidanceControlPacket.setPhysics(openData.computerPos(), gravity, drag));
            notice = "Flight physics applied";
        } catch (NumberFormatException ex) { notice = "Gravity and drag must be numbers"; }
    }

    private void applyFleet() {
        try {
            int channel = Math.max(0, Math.min(9999, Integer.parseInt(fleetBox.getValue().trim())));
            int interval = Math.max(1, Math.min(200, Integer.parseInt(salvoBox.getValue().trim())));
            ModNetwork.sendToServer(GuidanceControlPacket.setFleet(openData.computerPos(), channel, interval));
            notice = "Fleet channel " + channel + ", interval " + interval + " ticks";
        } catch (NumberFormatException ex) { notice = "Fleet values must be whole numbers"; }
    }

    private EditBox labeledBox(String label, double value, int y) {
        EditBox box = box(panelX + 12, y + 11, 146, decimal(value));
        box.setHint(Component.literal(label));
        return box;
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setValue(value);
        box.setResponder(ignored -> invalidatePreview("Plan changed — check the route again before launch."));
        addRenderableWidget(box);
        return box;
    }

    private void invalidatePreview(String message) {
        result = null;
        notice = message;
        if (launchButton != null) launchButton.active = false;
    }

    private void switchTab(Tab next) { captureVisibleFields(); tab = next; rebuildPlannerWidgets(); }

    private void captureVisibleFields() {
        try {
            if (tab == Tab.BODY) captureBodyFields();
            if (tab == Tab.EASY && easyTargetBox != null) {
                currentTarget = parseBlockPos(easyTargetBox.getValue(), currentTarget);
                if (easyCruiseBox != null) {
                    easyCruiseY = Math.max(0, Integer.parseInt(easyCruiseBox.getValue().trim()));
                }
            }
            if (tab == Tab.CALCULATOR && calculatorTargetBox != null) {
                currentTarget = parseBlockPos(calculatorTargetBox.getValue(), currentTarget);
                if (calculatorCommandYBox != null && calculatorCommandAngleBox != null) {
                    double commandY = parse(calculatorCommandYBox);
                    double commandAngle = parse(calculatorCommandAngleBox);
                    if (commandY <= 0.0 || Math.abs(commandAngle) < 0.001) {
                        commandY = 0.0;
                        commandAngle = 0.0;
                    }
                    commandAngle = Math.max(-89.0, Math.min(89.0, commandAngle));
                    BallisticFlightPlan.FlightMode mode = commandY > 0.0
                            ? BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE : settings.mode();
                    settings = new BallisticFlightPlan.Settings(settings.autoEnabled(), mode, settings.arc(),
                            settings.profile(), settings.phaseLayerEnabled(),
                            commandY > 0.0 || settings.waypointLayerEnabled(), settings.altitudeLayerEnabled(),
                            settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(),
                            settings.minimumClearanceY(), settings.ceilingY(), settings.desiredAngleDeg(),
                            commandY, commandAngle, settings.waypoints());
                }
            }
            if (tab == Tab.AUTO_PLAN && autoClearanceBox != null) {
                settings = copy(settings.motorCutoffFraction(), settings.apexFraction(),
                        parse(autoTerminalBox), parse(autoClearanceBox), parse(autoCeilingBox),
                        settings.waypoints());
            }
            if (tab == Tab.PHASES && cutoffBox != null) settings = copy(parse(cutoffBox), parse(apexBox), parse(terminalBox), settings.minimumClearanceY(), settings.ceilingY(), settings.waypoints());
            if (tab == Tab.ALTITUDE && minimumYBox != null) settings = copy(settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(), parse(minimumYBox), parse(ceilingYBox), settings.waypoints());
            if (tab == Tab.WAYPOINTS && waypointX != null && !settings.waypoints().isEmpty()) {
                ArrayList<BallisticFlightPlan.Waypoint> points = new ArrayList<>(settings.waypoints());
                points.set(selectedWaypoint, new BallisticFlightPlan.Waypoint(parse(waypointX), parse(waypointY), parse(waypointZ), points.get(selectedWaypoint).locked()));
                settings = copy(settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(), settings.minimumClearanceY(), settings.ceilingY(), points);
            }
        } catch (NumberFormatException ex) { notice = "Invalid numeric field"; }
    }

    private void request(FlightPlanRequestPacket.Action action) {
        captureVisibleFields();
        if (action == FlightPlanRequestPacket.Action.APPLY
                || action == FlightPlanRequestPacket.Action.APPLY_AND_LAUNCH) {
            // The global Save/Launch controls must include Body-tab edits too.
            ModNetwork.sendToServer(new BodyCalibrationPacket(openData.computerPos(),
                    bodyBase, bodyCenter, bodyNose));
        }
        revision++;
        notice = action == FlightPlanRequestPacket.Action.LOAD ? "Loading..." : "Calculating on server...";
        ModNetwork.sendToServer(new FlightPlanRequestPacket(openData.computerPos(), revision, action, readTarget(),
                selectedTargetShipId, settings));
    }

    private void launch() {
        captureVisibleFields();
        BlockPos requestedTarget = readTarget();
        boolean samePlan = result != null && settings.equals(result.settings());
        boolean sameTarget = result != null && (selectedTargetShipId >= 0
                || requestedTarget.equals(BlockPos.containing(result.resolvedTarget())));
        if (result == null || !result.feasible() || !samePlan || !sameTarget) {
            notice = "Launch locked — build and check a feasible route first.";
            if (launchButton != null) launchButton.active = false;
            return;
        }
        request(FlightPlanRequestPacket.Action.APPLY_AND_LAUNCH);
        notice = "Applying plan and requesting atomic launch...";
    }

    private void autoTuneForDistance() {
        captureVisibleFields();
        if (tab == Tab.EASY) {
            ModNetwork.sendToServer(GuidanceControlPacket.setCruiseY(openData.computerPos(), easyCruiseY));
        }
        BallisticFlightPlan.TrajectoryProfile requestedEasyProfile = tab == Tab.EASY
                ? settings.profile() : BallisticFlightPlan.TrajectoryProfile.AUTO;
        double range = plannedRange();
        double baseY = result == null
                ? currentTarget.getY()
                : Math.max(result.launchPosition().y, result.resolvedTarget().y);
        BallisticFlightPlan.TrajectoryProfile profile;
        double cutoff, apex, terminal, corridor;
        boolean corridorPoints;
        if (range <= 750.0) {
            profile = BallisticFlightPlan.TrajectoryProfile.LOW_ARC;
            cutoff = 0.14; apex = 0.32; terminal = 0.64;
            corridor = baseY + 48.0;
            corridorPoints = false;
        } else if (range <= 5_000.0) {
            profile = BallisticFlightPlan.TrajectoryProfile.PARABOLIC;
            cutoff = 0.10; apex = 0.29; terminal = 0.74;
            corridor = baseY + Math.max(96.0, Math.sqrt(range) * 2.5);
            corridorPoints = false;
        } else if (range <= 50_000.0) {
            profile = BallisticFlightPlan.TrajectoryProfile.HIGH_LOFT;
            cutoff = 0.08; apex = 0.24; terminal = 0.82;
            corridor = baseY + Math.min(1_600.0, 192.0 + Math.sqrt(range) * 5.0);
            corridorPoints = true;
        } else {
            profile = BallisticFlightPlan.TrajectoryProfile.CRUISE;
            cutoff = 0.06; apex = 0.18; terminal = 0.88;
            corridor = baseY + Math.min(4_096.0, 384.0 + Math.sqrt(range) * 6.0);
            corridorPoints = true;
        }
        if (requestedEasyProfile != BallisticFlightPlan.TrajectoryProfile.AUTO) {
            profile = requestedEasyProfile;
        }
        settings = new BallisticFlightPlan.Settings(true,
                BallisticFlightPlan.FlightMode.GUIDED_BOOST_GLIDE, settings.arc(), profile,
                true, corridorPoints, true, cutoff, apex, terminal,
                Math.max(settings.minimumClearanceY(), corridor), settings.ceilingY(),
                settings.desiredAngleDeg(), settings.angleCommandY(), settings.angleCommandDeg(),
                settings.waypoints());
        notice = String.format(Locale.ROOT, "%s band · %.0f blocks · %s",
                distanceBand(range), range, profile.name().replace('_', ' '));
        rebuildPlannerWidgets();
        request(FlightPlanRequestPacket.Action.PREVIEW);
    }

    private double plannedRange() {
        if (result != null) {
            double dx = result.resolvedTarget().x - result.launchPosition().x;
            double dz = result.resolvedTarget().z - result.launchPosition().z;
            return Math.hypot(dx, dz);
        }
        return 0.0;
    }

    private static String distanceBand(double range) {
        if (range <= 750.0) return "CLOSE";
        if (range <= 5_000.0) return "REGIONAL";
        if (range <= 50_000.0) return "LONG RANGE";
        return "STRATEGIC";
    }

    public void acceptResult(BallisticFlightPlan.Result incoming) {
        if (incoming == null || incoming.revision() < revision) return;
        result = incoming;
        settings = incoming.settings();
        selectedTargetShipId = incoming.targetShipId();
        currentTarget = BlockPos.containing(incoming.resolvedTarget());
        if (tab == Tab.EASY) {
            if (incoming.feasible()) {
                notice = String.format(Locale.ROOT,
                        "Route ready — ETA %.1fs. Terminal guidance corrects the intercept. Press LAUNCH.",
                        incoming.etaSeconds());
            } else {
                String reason = incoming.warnings().isEmpty()
                        ? "Try Safe Auto, more engine power, or a higher route."
                        : incoming.warnings().get(0);
                notice = "Cannot launch this route — " + reason;
            }
        } else {
            notice = incoming.status();
        }
        rebuildPlannerWidgets();
    }

    private BlockPos readTarget() {
        if (tab == Tab.EASY && easyTargetBox != null) {
            try { return parseBlockPos(easyTargetBox.getValue(), currentTarget); }
            catch (NumberFormatException ignored) { notice = "Target must contain three whole numbers: X Y Z"; return currentTarget; }
        }
        if (tab == Tab.CALCULATOR && calculatorTargetBox != null) {
            try { return parseBlockPos(calculatorTargetBox.getValue(), currentTarget); }
            catch (NumberFormatException ignored) { notice = "Target must be X Y Z"; return currentTarget; }
        }
        if (targetBox == null) return currentTarget;
        String[] p = targetBox.getValue().trim().split("[,\\s]+");
        if (p.length == 3) try { return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])); } catch (NumberFormatException ignored) { notice = "Target must be X Y Z"; }
        return currentTarget;
    }

    private void replace(boolean auto, BallisticFlightPlan.FlightMode mode, BallisticFlightPlan.ArcPreference arc, boolean phases, boolean waypoints, boolean altitude) {
        captureVisibleFields();
        settings = new BallisticFlightPlan.Settings(auto, mode, arc, settings.profile(), phases, waypoints,
                altitude, settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(),
                settings.minimumClearanceY(), settings.ceilingY(), settings.desiredAngleDeg(),
                settings.angleCommandY(), settings.angleCommandDeg(), settings.waypoints());
        rebuildPlannerWidgets();
    }

    private BallisticFlightPlan.Settings copy(double cutoff, double apex, double terminal, double minY, double ceiling, List<BallisticFlightPlan.Waypoint> points) {
        return new BallisticFlightPlan.Settings(settings.autoEnabled(), settings.mode(), settings.arc(),
                settings.profile(), settings.phaseLayerEnabled(), settings.waypointLayerEnabled(),
                settings.altitudeLayerEnabled(), cutoff, apex, terminal, minY, ceiling,
                settings.desiredAngleDeg(), settings.angleCommandY(), settings.angleCommandDeg(), points);
    }

    private void addWaypoint() {
        captureVisibleFields();
        if (settings.waypoints().size() >= BallisticFlightPlan.MAX_WAYPOINTS) { notice = "Maximum 16 waypoints"; return; }
        ArrayList<BallisticFlightPlan.Waypoint> points = new ArrayList<>(settings.waypoints());
        BlockPos target = readTarget();
        points.add(new BallisticFlightPlan.Waypoint(target.getX(), Math.max(256, target.getY()), target.getZ(), false));
        settings = copy(settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(), settings.minimumClearanceY(), settings.ceilingY(), points);
        selectedWaypoint = points.size() - 1;
        rebuildPlannerWidgets();
    }

    private void removeWaypoint() {
        if (settings.waypoints().isEmpty()) return;
        ArrayList<BallisticFlightPlan.Waypoint> points = new ArrayList<>(settings.waypoints());
        points.remove(selectedWaypoint);
        selectedWaypoint = Math.max(0, selectedWaypoint - 1);
        settings = copy(settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(), settings.minimumClearanceY(), settings.ceilingY(), points);
        rebuildPlannerWidgets();
    }

    private void selectWaypoint(int direction) {
        captureVisibleFields();
        if (!settings.waypoints().isEmpty()) selectedWaypoint = Math.floorMod(selectedWaypoint + direction, settings.waypoints().size());
        rebuildPlannerWidgets();
    }

    private void toggleWaypointLock() {
        captureVisibleFields();
        if (settings.waypoints().isEmpty()) return;
        ArrayList<BallisticFlightPlan.Waypoint> points = new ArrayList<>(settings.waypoints());
        BallisticFlightPlan.Waypoint p = points.get(selectedWaypoint);
        points.set(selectedWaypoint, new BallisticFlightPlan.Waypoint(p.x(), p.y(), p.z(), !p.locked()));
        settings = copy(settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(), settings.minimumClearanceY(), settings.ceilingY(), points);
        rebuildPlannerWidgets();
    }

    private void moveWaypoint(int direction) {
        captureVisibleFields();
        int other = selectedWaypoint + direction;
        if (other < 0 || other >= settings.waypoints().size()) return;
        ArrayList<BallisticFlightPlan.Waypoint> points = new ArrayList<>(settings.waypoints());
        BallisticFlightPlan.Waypoint p = points.remove(selectedWaypoint);
        points.add(other, p);
        selectedWaypoint = other;
        settings = copy(settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction(), settings.minimumClearanceY(), settings.ceilingY(), points);
        rebuildPlannerWidgets();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xf0101720);
        g.renderOutline(panelX, panelY, PANEL_W, PANEL_H, 0xff5da9d6);
        int statusColor = result != null && !result.feasible() ? 0xffff7373 : 0xffd8edf8;
        String statusLine = font.plainSubstrByWidth("GUIDANCE — " + notice, PANEL_W - 24);
        g.drawCenteredString(font, statusLine, width / 2, panelY + 8, statusColor);
        super.render(g, mouseX, mouseY, partialTick);
        if (tab == Tab.EASY) {
            g.drawString(font, "STEP 1 — Enter target world coordinates (X Y Z)",
                    panelX + 12, panelY + 58, 0xffd7e9f2, false);
            g.drawString(font, "STEP 2 — Choose a route style", panelX + 12, panelY + 94, 0xffa8bbca, false);
        }
        if (tab == Tab.PLAN) g.drawString(font, "Target X Y Z", panelX + 12, panelY + 62, 0xffa8bbca, false);
        if (tab == Tab.BODY) drawBodyVisualizer(g);
        else drawGraph(g);
        drawDetails(g);
    }

    private void drawGraph(GuiGraphics g) {
        int gx = panelX + GRAPH_X, gy = panelY + GRAPH_Y;
        g.fill(gx, gy, gx + GRAPH_W, gy + GRAPH_H, 0xff071019);
        g.renderOutline(gx, gy, GRAPH_W, GRAPH_H, 0xff42667a);
        g.drawString(font, "SERVER TRAJECTORY", gx + 6, gy + 5, 0xff8ed6ff, false);
        if (result == null || result.samples().size() < 2) return;
        double minY = result.samples().stream().mapToDouble(BallisticFlightPlan.Sample::y).min().orElse(0);
        double maxY = result.samples().stream().mapToDouble(BallisticFlightPlan.Sample::y).max().orElse(minY + 1);
        double span = Math.max(1, maxY - minY);
        int lastX = gx + 2, lastY = gy + GRAPH_H - 20 - (int) ((result.samples().get(0).y() - minY) / span * (GRAPH_H - 30));
        for (BallisticFlightPlan.Sample sample : result.samples()) {
            int px = gx + 2 + (int) (sample.fraction() * (GRAPH_W - 4));
            int py = gy + GRAPH_H - 20 - (int) ((sample.y() - minY) / span * (GRAPH_H - 30));
            drawLine(g, lastX, lastY, px, py, sample.terrainKnown() ? 0xff62dcff : 0xffffb45d);
            lastX = px; lastY = py;
        }
        if (settings.phaseLayerEnabled()) {
            phaseMarker(g, gx, gy, settings.motorCutoffFraction(), 0xfff7d154);
            phaseMarker(g, gx, gy, settings.apexFraction(), 0xff7fe28a);
            phaseMarker(g, gx, gy, settings.terminalFraction(), 0xffff776e);
        }
        g.drawString(font, String.format(Locale.ROOT, "Y %.0f .. %.0f", minY, maxY), gx + 5, gy + GRAPH_H - 13, 0xff7893a3, false);
    }

    private void drawBodyVisualizer(GuiGraphics g) {
        int gx = panelX + GRAPH_X, gy = panelY + 52, gh = 164;
        g.fill(gx, gy, gx + GRAPH_W, gy + gh, 0xff061019);
        g.renderOutline(gx, gy, GRAPH_W, gh, 0xff42667a);
        g.enableScissor(gx + 1, gy + 1, gx + GRAPH_W - 1, gy + gh - 1);
        g.drawString(font, "3D SHIP BODY", gx + 6, gy + 5, 0xff8ed6ff, false);
        g.drawString(font, "drag rotate  ·  wheel zoom  ·  click block", gx + 6,
                gy + gh - 12, 0xff7893a3, false);

        double maxSpan = bodyMaxSpan();
        for (BlockPos voxel : bodyVoxelSamples) {
            int sx = projectBodyX(voxel, gx, GRAPH_W, maxSpan);
            int sy = projectBodyY(voxel, gy, gh, maxSpan);
            double depth = projectBodyDepth(voxel);
            int shade = Math.max(55, Math.min(145,
                    92 + (int) Math.round(depth / maxSpan * 70.0)));
            int color = 0xff000000 | (shade / 2 << 16) | (shade << 8) | Math.min(255, shade + 35);
            g.fill(sx, sy, sx + 2, sy + 2, color);
        }

        drawBodyAxisLine(g, bodyBase, bodyCenter, 0xffe8b84f, gx, gy, gh, maxSpan);
        drawBodyAxisLine(g, bodyCenter, bodyNose, 0xffff6868, gx, gy, gh, maxSpan);
        drawBodyMarker3d(g, bodyBase, 0xffe8b84f, gx, gy, gh, maxSpan);
        drawBodyMarker3d(g, bodyCenter, 0xff62dcff, gx, gy, gh, maxSpan);
        drawBodyMarker3d(g, bodyNose, 0xffff6868, gx, gy, gh, maxSpan);
        g.disableScissor();
    }

    private void drawBodyAxisLine(GuiGraphics g, BlockPos a, BlockPos b, int color,
                                  int gx, int gy, int gh, double maxSpan) {
        drawLine(g, projectBodyX(a, gx, GRAPH_W, maxSpan), projectBodyY(a, gy, gh, maxSpan),
                projectBodyX(b, gx, GRAPH_W, maxSpan), projectBodyY(b, gy, gh, maxSpan), color);
    }

    private void drawBodyMarker3d(GuiGraphics g, BlockPos p, int color,
                                  int gx, int gy, int gh, double maxSpan) {
        int x = projectBodyX(p, gx, GRAPH_W, maxSpan);
        int y = projectBodyY(p, gy, gh, maxSpan);
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xff071019);
        g.fill(x - 2, y - 2, x + 3, y + 3, color);
    }

    private double bodyMaxSpan() {
        return Math.max(1.0, Math.max(bodyMaxX - bodyMinX + 1.0,
                Math.max(bodyMaxY - bodyMinY + 1.0, bodyMaxZ - bodyMinZ + 1.0)));
    }

    private double bodyRelativeX(BlockPos p) { return p.getX() - (bodyMinX + bodyMaxX) * 0.5; }
    private double bodyRelativeY(BlockPos p) { return p.getY() - (bodyMinY + bodyMaxY) * 0.5; }
    private double bodyRelativeZ(BlockPos p) { return p.getZ() - (bodyMinZ + bodyMaxZ) * 0.5; }

    private int projectBodyX(BlockPos p, int gx, int gw, double maxSpan) {
        double rotatedX = Math.cos(bodyYaw) * bodyRelativeX(p) - Math.sin(bodyYaw) * bodyRelativeZ(p);
        double scale = Math.min(gw - 20.0, 142.0) * bodyZoom / maxSpan;
        return gx + gw / 2 + (int) Math.round(rotatedX * scale);
    }

    private int projectBodyY(BlockPos p, int gy, int gh, double maxSpan) {
        double depthYaw = Math.sin(bodyYaw) * bodyRelativeX(p) + Math.cos(bodyYaw) * bodyRelativeZ(p);
        double rotatedY = Math.cos(bodyPitch) * bodyRelativeY(p) - Math.sin(bodyPitch) * depthYaw;
        double scale = Math.min(GRAPH_W - 20.0, gh - 30.0) * bodyZoom / maxSpan;
        return gy + gh / 2 - (int) Math.round(rotatedY * scale);
    }

    private double projectBodyDepth(BlockPos p) {
        double depthYaw = Math.sin(bodyYaw) * bodyRelativeX(p) + Math.cos(bodyYaw) * bodyRelativeZ(p);
        return Math.sin(bodyPitch) * bodyRelativeY(p) + Math.cos(bodyPitch) * depthYaw;
    }

    private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void phaseMarker(GuiGraphics g, int gx, int gy, double fraction, int color) {
        int x = gx + 2 + (int) (fraction * (GRAPH_W - 4));
        g.vLine(x, gy + 14, gy + GRAPH_H - 19, color);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.BODY && button == 0 && insideBodyViewport(mouseX, mouseY)) {
            bodyDragging = true;
            bodyDragMoved = false;
            bodyLastMouseX = mouseX;
            bodyLastMouseY = mouseY;
            return true;
        }
        if (tab == Tab.PHASES && button == 0 && insideGraph(mouseX, mouseY)) {
            double f = graphFraction(mouseX);
            double[] handles = {settings.motorCutoffFraction(), settings.apexFraction(), settings.terminalFraction()};
            int best = 0;
            for (int i = 1; i < handles.length; i++) if (Math.abs(handles[i] - f) < Math.abs(handles[best] - f)) best = i;
            if (Math.abs(handles[best] - f) <= 0.06) { draggedPhaseHandle = best; updatePhaseHandle(f); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean insideBodyViewport(double mouseX, double mouseY) {
        int gx = panelX + GRAPH_X, gy = panelY + 52;
        return mouseX >= gx && mouseX <= gx + GRAPH_W && mouseY >= gy && mouseY <= gy + 164;
    }

    private void pickBodyVoxel(double mouseX, double mouseY) {
        int gx = panelX + GRAPH_X, gy = panelY + 52, gh = 164;
        double maxSpan = bodyMaxSpan();
        BlockPos best = null;
        double bestDistance = 9.0 * 9.0;
        double bestDepth = -Double.MAX_VALUE;
        for (BlockPos voxel : bodyVoxelSamples) {
            double dx = projectBodyX(voxel, gx, GRAPH_W, maxSpan) - mouseX;
            double dy = projectBodyY(voxel, gy, gh, maxSpan) - mouseY;
            double distance = dx * dx + dy * dy;
            double depth = projectBodyDepth(voxel);
            if (distance < bestDistance || (Math.abs(distance - bestDistance) < 0.5 && depth > bestDepth)) {
                best = voxel;
                bestDistance = distance;
                bestDepth = depth;
            }
        }
        if (best != null) setSelectedBodyAnchor(best);
        else notice = "No hull block under cursor; rotate or zoom closer";
    }

    private void setSelectedBodyAnchor(BlockPos value) {
        if (selectedBodyAnchor == 0) bodyBase = value;
        else if (selectedBodyAnchor == 1) bodyCenter = value;
        else bodyNose = value;
        if (bodyBaseBox != null) bodyBaseBox.setValue(xyz(bodyBase));
        if (bodyCenterBox != null) bodyCenterBox.setValue(xyz(bodyCenter));
        if (bodyNoseBox != null) bodyNoseBox.setValue(xyz(bodyNose));
        notice = "Selected " + (selectedBodyAnchor == 0 ? "base" : selectedBodyAnchor == 1 ? "center" : "nose")
                + " block " + xyz(value);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.BODY && bodyDragging && button == 0) {
            double dx = mouseX - bodyLastMouseX;
            double dy = mouseY - bodyLastMouseY;
            if (Math.abs(dx) + Math.abs(dy) > 0.5) bodyDragMoved = true;
            bodyYaw += dx * 0.012;
            bodyPitch = Math.max(Math.toRadians(-85), Math.min(Math.toRadians(85),
                    bodyPitch + dy * 0.012));
            bodyLastMouseX = mouseX;
            bodyLastMouseY = mouseY;
            return true;
        }
        if (draggedPhaseHandle >= 0) { updatePhaseHandle(graphFraction(mouseX)); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tab == Tab.BODY && bodyDragging && button == 0) {
            bodyDragging = false;
            if (!bodyDragMoved) pickBodyVoxel(mouseX, mouseY);
            return true;
        }
        if (draggedPhaseHandle >= 0) { draggedPhaseHandle = -1; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.BODY && insideBodyViewport(mouseX, mouseY)) {
            bodyZoom = Math.max(0.45, Math.min(4.0, bodyZoom * Math.pow(1.14, delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean insideGraph(double x, double y) {
        int gx = panelX + GRAPH_X, gy = panelY + GRAPH_Y;
        return x >= gx && x <= gx + GRAPH_W && y >= gy && y <= gy + GRAPH_H;
    }

    private double graphFraction(double x) {
        return Math.max(0.01, Math.min(0.99, (x - (panelX + GRAPH_X + 2)) / (GRAPH_W - 4.0)));
    }

    private void updatePhaseHandle(double fraction) {
        double cutoff = settings.motorCutoffFraction(), apex = settings.apexFraction(), terminal = settings.terminalFraction();
        if (draggedPhaseHandle == 0) cutoff = Math.min(fraction, apex - 0.01);
        else if (draggedPhaseHandle == 1) apex = Math.max(cutoff + 0.01, Math.min(fraction, terminal - 0.05));
        else terminal = Math.max(apex + 0.05, fraction);
        settings = copy(cutoff, apex, terminal, settings.minimumClearanceY(), settings.ceilingY(), settings.waypoints());
        if (cutoffBox != null) cutoffBox.setValue(decimal(settings.motorCutoffFraction()));
        if (apexBox != null) apexBox.setValue(decimal(settings.apexFraction()));
        if (terminalBox != null) terminalBox.setValue(decimal(settings.terminalFraction()));
    }

    private void drawDetails(GuiGraphics g) {
        if (tab == Tab.EASY) {
            int x = panelX + GRAPH_X + 5;
            int y = panelY + 190;
            g.drawString(font, "EASY GUIDANCE", x, y, 0xff8ed6ff, false);
            g.drawString(font, "Style: " + easyStyleName(settings.profile()), x, y + 13, 0xffa8c7d8, false);
            g.drawString(font, "Read the status below before launch", x, y + 26, 0xffffc078, false);
        }
        if (tab == Tab.TELEMETRY && result != null) {
            int x = panelX + 12, y = panelY + 66;
            String[] lines = {"Status: " + result.status(),
                    String.format(Locale.ROOT, "WORLD launch %.1f  %.1f  %.1f", result.launchPosition().x, result.launchPosition().y, result.launchPosition().z),
                    String.format(Locale.ROOT, "WORLD target %.1f  %.1f  %.1f", result.resolvedTarget().x, result.resolvedTarget().y, result.resolvedTarget().z),
                    String.format(Locale.ROOT, "Azimuth %.2f°  Elevation %.2f°", result.azimuthDeg(), result.elevationDeg()), String.format(Locale.ROOT, "Required %.1f m/s  Available %.1f m/s", result.requiredSpeedMps(), result.availableSpeedMps()), String.format(Locale.ROOT, "ETA %.2fs  Apex Y %.1f", result.etaSeconds(), result.apexY()), String.format(Locale.ROOT, "Impact %.1f m/s  Unguided arc error %.2f blocks", result.impactSpeedMps(), result.predictedMiss())};
            for (String line : lines) { g.drawString(font, line, x, y, 0xffc7dbe6, false); y += 16; }
            for (String warning : result.warnings()) { g.drawString(font, "! " + warning, x, y, 0xffffbd65, false); y += 13; }
        }
        if (tab == Tab.PHASES) { g.drawString(font, "Fractions are 0..1", panelX + 12, panelY + 62, 0xff829aa8, false); g.drawString(font, "Graph markers update on Preview", panelX + 12, panelY + 202, 0xff829aa8, false); }
        if (tab == Tab.CALCULATOR) {
            g.drawString(font, "Target X Y Z", panelX + 12, panelY + 56, 0xff829aa8, false);
            g.drawString(font, "Desired angle", panelX + 12, panelY + 85, 0xff829aa8, false);
            g.drawString(font, "Execute at world Y       Pitch ° (-89..89)",
                    panelX + 12, panelY + 198, 0xff829aa8, false);
            if (result != null) {
                int x = panelX + GRAPH_X + 5;
                int y = panelY + 190;
                g.drawString(font, String.format(Locale.ROOT, "%s angle %.2f° · az %.2f°",
                        settings.desiredAngleDeg() > 0.0 ? "LOCKED" : "NEEDED",
                        result.elevationDeg(), result.azimuthDeg()), x, y, 0xff8ed6ff, false);
                g.drawString(font, String.format(Locale.ROOT, "speed need %.1f · have %.1f m/s",
                        result.requiredSpeedMps(), result.availableSpeedMps()), x, y + 13,
                        result.requiredSpeedMps() <= result.availableSpeedMps() ? 0xff82e89a : 0xffff7373, false);
                g.drawString(font, String.format(Locale.ROOT, "ETA %.1fs · apex %.0f · raw arc error %.1f",
                        result.etaSeconds(), result.apexY(), result.predictedMiss()), x, y + 26,
                        0xffb8d7e8, false);
            }
        }
        if (tab == Tab.BODY) {
            g.drawString(font, "Base block", panelX + 12, panelY + 58, 0xffe8b84f, false);
            g.drawString(font, "Control center block", panelX + 12, panelY + 96, 0xff62dcff, false);
            g.drawString(font, "Nose block", panelX + 12, panelY + 134, 0xffff6868, false);
            g.drawString(font, "Select anchor, then click a hull voxel", panelX + 174,
                    panelY + 216, 0xff829aa8, false);
        }
        if (tab == Tab.AUTO_PLAN) {
            double range = plannedRange();
            g.drawString(font, "Absolute safety Y", panelX + 12, panelY + 154, 0xff829aa8, false);
            g.drawString(font, "Ceiling (0=off)", panelX + 88, panelY + 154, 0xff829aa8, false);
            g.drawString(font, "Terminal fraction", panelX + 12, panelY + 188, 0xff829aa8, false);
            g.drawString(font, String.format(Locale.ROOT, "%s · %.0f blocks", distanceBand(range), range),
                    panelX + GRAPH_X + 5, panelY + 190, 0xff8ed6ff, false);
            g.drawString(font, "AUTO chooses altitude, phases and corridor points", panelX + GRAPH_X + 5,
                    panelY + 204, 0xff829aa8, false);
        }
        if (tab == Tab.WAYPOINTS) g.drawString(font, "Up to 16 world-space control points", panelX + 12, panelY + 54, 0xff829aa8, false);
        if (tab == Tab.ALTITUDE) g.drawString(font, "Unknown terrain uses a conservative corridor", panelX + 12, panelY + 62, 0xff829aa8, false);
        if (tab == Tab.ENGINE) {
            g.drawString(font, "Power", panelX + 12, panelY + 55, 0xff829aa8, false);
            g.drawString(font, "Apex override", panelX + 12, panelY + 81, 0xff829aa8, false);
            g.drawString(font, "Cruise override", panelX + 12, panelY + 107, 0xff829aa8, false);
            g.drawString(font, "Gravity", panelX + 12, panelY + 133, 0xff829aa8, false);
            g.drawString(font, "Drag", panelX + 86, panelY + 133, 0xff829aa8, false);
        }
        if (tab == Tab.FLEET) {
            g.drawString(font, "Fleet channel (0 = standalone)", panelX + 12, panelY + 65, 0xff829aa8, false);
            g.drawString(font, "Salvo interval ticks", panelX + 12, panelY + 103, 0xff829aa8, false);
        }
    }

    private static BallisticFlightPlan.ArcPreference nextArc(BallisticFlightPlan.ArcPreference arc) { return switch (arc) { case AUTO -> BallisticFlightPlan.ArcPreference.HIGH; case HIGH -> BallisticFlightPlan.ArcPreference.LOW; case LOW -> BallisticFlightPlan.ArcPreference.AUTO; }; }
    private static BallisticFlightPlan.TrajectoryProfile nextProfile(BallisticFlightPlan.TrajectoryProfile profile) {
        BallisticFlightPlan.TrajectoryProfile[] values = BallisticFlightPlan.TrajectoryProfile.values();
        return values[(profile.ordinal() + 1) % values.length];
    }
    private String easyLabel(BallisticFlightPlan.TrajectoryProfile profile, String label) {
        return settings.profile() == profile ? "• " + label : label;
    }
    private String arcName() { return switch (settings.arc()) {
        case AUTO -> "AUTO CHOOSE";
        case LOW -> "LOW / FAST";
        case HIGH -> "HIGH";
    }; }
    private String profileName() { return easyStyleName(settings.profile()).toUpperCase(Locale.ROOT); }
    private String modeName() { return settings.mode() == BallisticFlightPlan.FlightMode.PURE_BALLISTIC ? "BALLISTIC (NO CORRECTION)" : "GUIDED (RECOMMENDED)"; }
    private static String easyStyleName(BallisticFlightPlan.TrajectoryProfile profile) {
        return switch (profile) {
            case DIRECT -> "Low / Fast";
            case PARABOLIC -> "High Arc";
            case TOP_ATTACK -> "Top Attack";
            default -> "Safe Automatic";
        };
    }
    private static String tabName(Tab tab) { return switch (tab) { case EASY -> "Easy"; case PLAN -> "Plan"; case AUTO_PLAN -> "Auto"; case CALCULATOR -> "Calc"; case BODY -> "Body"; case PHASES -> "Phase"; case WAYPOINTS -> "Points"; case ALTITUDE -> "Alt"; case ENGINE -> "Engine"; case FLEET -> "Fleet"; case TELEMETRY -> "Data"; }; }
    private static String tabHelp(Tab tab) { return switch (tab) {
        case EASY -> "Recommended beginner controls: target, route style, automatic setup and launch.";
        case PLAN -> "General flight mode, arc and trajectory shape.";
        case AUTO_PLAN -> "Automatic distance-based altitude, phase and waypoint planning.";
        case CALCULATOR -> "Ballistic launch angle, power and altitude-triggered pitch commands.";
        case BODY -> "Tell guidance which hull blocks are the missile base, center and nose.";
        case PHASES -> "Advanced motor cutoff, apex and terminal-guidance timing.";
        case WAYPOINTS -> "Advanced world-coordinate points the missile should fly through.";
        case ALTITUDE -> "Minimum safe altitude and maximum flight ceiling.";
        case ENGINE -> "Engine power, altitude overrides, gravity, drag and engine pairing.";
        case FLEET -> "Coordinate multiple guidance computers and staggered salvo launches.";
        case TELEMETRY -> "Server-calculated route coordinates, ETA, accuracy and warnings.";
    }; }
    private static String xyz(int x, int y, int z) { return x + " " + y + " " + z; }
    private static String xyz(BlockPos p) { return xyz(p.getX(), p.getY(), p.getZ()); }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private static double parse(EditBox box) { return Double.parseDouble(box.getValue().trim()); }
    @Override public boolean isPauseScreen() { return false; }
}
