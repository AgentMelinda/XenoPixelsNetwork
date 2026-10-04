package net.bullettrain.xenopixelsmod.client.gui;

import net.bullettrain.xenopixelsmod.aero.AeroAutopilotMode;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.aero.AeroSubsystem;
import net.bullettrain.xenopixelsmod.aero.ControllerMode;
import net.bullettrain.xenopixelsmod.aero.v2.GuidanceV2Bus;
import net.bullettrain.xenopixelsmod.aero.v2.GuidanceV2SurfaceMode;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.client.guidance.GuidanceV2Client;
import net.bullettrain.xenopixelsmod.client.hud.GuidanceV2ArtLayout;
import net.bullettrain.xenopixelsmod.client.hud.HudDraw;
import net.bullettrain.xenopixelsmod.client.screen.UnblurredScreen;
import net.bullettrain.xenopixelsmod.network.GuidanceV2Network;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.AeroControlPacket;
import net.bullettrain.xenopixelsmod.network.packet.BodyCalibrationPacket;
import net.bullettrain.xenopixelsmod.network.packet.GuidanceControlPacket;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Colorful v2 computer. Opened only when {@link GuidanceV2Client#isV2()} is true.
 * Does not restyle {@link FlightPlannerScreen}.
 */
public final class FlightPlannerV2Screen extends UnblurredScreen {
    private enum Tab { FLIGHT, SURFACES, MODE, NAV, BODY }

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 248;
    private static final int TAB_W = 76;
    private static final int TAB_STEP = 80;
    private static final int BODY_VIEW_X = 168;
    private static final int BODY_VIEW_W = 236;
    private static final int BODY_VIEW_Y = 52;
    private static final int BODY_VIEW_H = 148;
    private static final int COLOR_BG = 0xE6080B12;
    private static final int COLOR_EDGE = 0xFF3DE0FF;
    private static final int COLOR_EDGE_2 = 0xFFFF7AD9;
    private static final int COLOR_CYAN = 0xFF5CE1FF;
    private static final int COLOR_AMBER = 0xFFFFC14A;
    private static final int COLOR_MAGENTA = 0xFFFF6AD5;
    private static final int COLOR_TEXT = 0xFFE8F6FF;
    private static final int COLOR_DIM = 0xFF8AA4B8;

    private final ClientScreens.GuidanceOpenData openData;
    private Tab tab = Tab.FLIGHT;
    private AeroStateSnapshot aeroState;
    private String notice = "Guidance v2";
    private BlockPos currentTarget;
    private int easyApexY;
    private int easyCruiseY;
    private int aeroPollTicks;
    private EditBox targetBox;
    private EditBox guideBox;
    private EditBox cruiseBox;
    private EditBox bodyBaseBox;
    private EditBox bodyCenterBox;
    private EditBox bodyNoseBox;
    private BlockPos bodyBase;
    private BlockPos bodyCenter;
    private BlockPos bodyNose;
    private int selectedBodyAnchor = 2;
    private int bodyMinX, bodyMinY, bodyMinZ, bodyMaxX, bodyMaxY, bodyMaxZ;
    private final List<BlockPos> bodyVoxelSamples = new ArrayList<>();
    private double bodyYaw = Math.toRadians(-42.0);
    private double bodyPitch = Math.toRadians(24.0);
    private double bodyZoom = 1.0;
    private boolean bodyDragging;
    private boolean bodyDragMoved;
    private double bodyLastMouseX, bodyLastMouseY;
    private int panelX;
    private int panelY;

    public FlightPlannerV2Screen(ClientScreens.GuidanceOpenData openData) {
        super(Component.literal("Guidance v2"));
        this.openData = openData;
        this.currentTarget = new BlockPos(openData.x(), openData.y(), openData.z());
        this.easyApexY = openData.apexY();
        this.easyCruiseY = openData.cruiseY();
        this.bodyBase = openData.missileBase();
        this.bodyCenter = openData.missileCenter();
        this.bodyNose = openData.missileNose();
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        GuidanceV2Network.requestHostFromClient(openData.computerPos());
        ModNetwork.sendToServer(AeroControlPacket.requestState(openData.computerPos()));
        loadBodyVisualization();
        rebuild();
    }

    public void acceptAeroState(AeroStateSnapshot state) {
        if (state == null || !state.controllerPos().equals(openData.computerPos())) {
            return;
        }
        boolean rebuild = aeroState == null
                || aeroState.mode() != state.mode()
                || aeroState.flightEngaged() != state.flightEngaged();
        aeroState = state;
        notice = state.status();
        if (rebuild) {
            rebuild();
        }
    }

    /** Missile is the default until the server says otherwise. ENGAGE is flight-only. */
    static boolean isFlightUi(@Nullable AeroStateSnapshot state) {
        return state != null && state.mode() == ControllerMode.FLIGHT;
    }

    static List<String> tabNames() {
        List<String> names = new ArrayList<>();
        for (Tab value : Tab.values()) {
            names.add(value.name());
        }
        return names;
    }

    static @Nullable String distinctBodyError(BlockPos base, BlockPos center, BlockPos nose) {
        if (base == null || center == null || nose == null
                || base.equals(center) || base.equals(nose) || center.equals(nose)) {
            return "Choose three different occupied blocks";
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (++aeroPollTicks >= 10) {
            aeroPollTicks = 0;
            ModNetwork.sendToServer(AeroControlPacket.requestState(openData.computerPos()));
            GuidanceV2Network.requestHostFromClient(openData.computerPos());
        }
    }

    private void rebuild() {
        clearWidgets();
        int x = panelX + 10;
        for (Tab value : Tab.values()) {
            boolean selected = tab == value;
            addRenderableWidget(Button.builder(Component.literal(value.name()), b -> {
                tab = value;
                rebuild();
            }).bounds(x, panelY + 28, TAB_W, 18).build()).active = !selected;
            x += TAB_STEP;
        }
        switch (tab) {
            case FLIGHT -> initFlight();
            case SURFACES -> {
            }
            case MODE -> initMode();
            case NAV -> initNav();
            case BODY -> initBody();
        }
        if (!isFlightUi(aeroState)) {
            addRenderableWidget(Button.builder(Component.literal("LAUNCH"), b -> launch())
                    .bounds(panelX + 262, panelY + PANEL_H - 26, 54, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("STOP"), b ->
                        ModNetwork.sendToServer(AeroControlPacket.emergencyStop(openData.computerPos())))
                .bounds(panelX + 320, panelY + PANEL_H - 26, 50, 18).build());
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(panelX + 376, panelY + PANEL_H - 26, 28, 18).build());
    }

    private void initFlight() {
        if (!isFlightUi(aeroState)) {
            initMissileFlight();
            return;
        }
        boolean engaged = aeroState != null && aeroState.flightEngaged();
        addRenderableWidget(Button.builder(Component.literal(engaged ? "DISENGAGE" : "ENGAGE"), b ->
                        ModNetwork.sendToServer(AeroControlPacket.toggle(openData.computerPos(),
                                AeroSubsystem.FLIGHT, !engaged)))
                .bounds(panelX + 14, panelY + 58, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("THR −"), b -> stepThrottle(-0.1))
                .bounds(panelX + 110, panelY + 58, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("THR +"), b -> stepThrottle(0.1))
                .bounds(panelX + 166, panelY + 58, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("FLP −"), b -> stepFlap(-0.25))
                .bounds(panelX + 224, panelY + 58, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("FLP +"), b -> stepFlap(0.25))
                .bounds(panelX + 280, panelY + 58, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("AUTO FLAP"), b ->
                        ModNetwork.sendToServer(AeroControlPacket.toggleAutoFlap(openData.computerPos())))
                .bounds(panelX + 14, panelY + 84, 88, 18).build());
        addRenderableWidget(Button.builder(Component.literal("PAIR ENGINES"), b ->
                        ModNetwork.sendToServer(AeroControlPacket.link(openData.computerPos(),
                                net.bullettrain.xenopixelsmod.aero.AeroAction.Link.Op.PAIR_NEARBY)))
                .bounds(panelX + 110, panelY + 84, 120, 18).build());
        addRenderableWidget(Button.builder(Component.literal("MANUAL"), b -> selectAutopilot(AeroAutopilotMode.MANUAL))
                .bounds(panelX + 14, panelY + 108, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("TARGET"), b -> selectAutopilot(AeroAutopilotMode.TARGET))
                .bounds(panelX + 90, panelY + 108, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("ROUTE"), b -> selectAutopilot(AeroAutopilotMode.ROUTE))
                .bounds(panelX + 166, panelY + 108, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("TO MISSILE"), b ->
                        setControllerMode(ControllerMode.MISSILE))
                .bounds(panelX + 242, panelY + 108, 88, 18).build());
    }

    private void initMissileFlight() {
        targetBox = new EditBox(font, panelX + 14, panelY + 58, 160, 18, Component.literal("Target"));
        targetBox.setValue(currentTarget.getX() + " " + currentTarget.getY() + " " + currentTarget.getZ());
        addRenderableWidget(targetBox);
        addRenderableWidget(Button.builder(Component.literal("SET TARGET"), b -> applyTarget())
                .bounds(panelX + 182, panelY + 58, 90, 18).build());
        guideBox = new EditBox(font, panelX + 14, panelY + 80, 70, 18, Component.literal("Guide Y"));
        guideBox.setValue(Integer.toString(easyApexY));
        addRenderableWidget(guideBox);
        addRenderableWidget(Button.builder(Component.literal("SET GUIDE Y"), b -> applyGuideY())
                .bounds(panelX + 90, panelY + 80, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal("LAUNCH"), b -> launch())
                .bounds(panelX + 14, panelY + 104, 88, 22).build());
        addRenderableWidget(Button.builder(Component.literal("ABORT"), b -> abortLaunch())
                .bounds(panelX + 110, panelY + 104, 70, 22).build());
        addRenderableWidget(Button.builder(Component.literal("PAIR ENGINES"), b -> {
                    ModNetwork.sendToServer(GuidanceControlPacket.pairNearby(openData.computerPos()));
                    notice = "Engine search requested — redstone or LAUNCH fires";
                })
                .bounds(panelX + 188, panelY + 104, 120, 22).build());
        addRenderableWidget(Button.builder(Component.literal("TO FLIGHT MODE"), b ->
                        setControllerMode(ControllerMode.FLIGHT))
                .bounds(panelX + 14, panelY + 132, 140, 18).build());
    }

    private void initMode() {
        boolean flight = isFlightUi(aeroState);
        addRenderableWidget(Button.builder(Component.literal(flight ? "MISSILE" : "• MISSILE"),
                b -> setControllerMode(ControllerMode.MISSILE))
                .bounds(panelX + 14, panelY + 58, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(flight ? "• FLIGHT" : "FLIGHT"),
                b -> setControllerMode(ControllerMode.FLIGHT))
                .bounds(panelX + 122, panelY + 58, 100, 20).build());
        if (!flight) {
            return;
        }
        GuidanceV2SurfaceMode current = GuidanceV2Client.surfaceMode(openData.computerPos());
        addRenderableWidget(Button.builder(Component.literal(
                        current == GuidanceV2SurfaceMode.THRUST_AND_FLAPS ? "• THRUST + FLAPS" : "THRUST + FLAPS"),
                b -> setMode(GuidanceV2SurfaceMode.THRUST_AND_FLAPS))
                .bounds(panelX + 14, panelY + 92, 180, 22).build());
        addRenderableWidget(Button.builder(Component.literal(
                        current.flapsOnly() ? "• FLAPS ONLY" : "FLAPS ONLY"),
                b -> setMode(GuidanceV2SurfaceMode.FLAPS_ONLY))
                .bounds(panelX + 14, panelY + 120, 180, 22).build());
    }

    private void initNav() {
        targetBox = new EditBox(font, panelX + 14, panelY + 64, 160, 18, Component.literal("Target"));
        targetBox.setValue(currentTarget.getX() + " " + currentTarget.getY() + " " + currentTarget.getZ());
        addRenderableWidget(targetBox);
        guideBox = new EditBox(font, panelX + 14, panelY + 90, 70, 18, Component.literal("Guide Y"));
        guideBox.setValue(Integer.toString(easyApexY));
        addRenderableWidget(guideBox);
        addRenderableWidget(Button.builder(Component.literal("SET GUIDE Y"), b -> applyGuideY())
                .bounds(panelX + 90, panelY + 90, 100, 18).build());
        cruiseBox = new EditBox(font, panelX + 14, panelY + 116, 70, 18, Component.literal("Cruise Y"));
        cruiseBox.setValue(Integer.toString(easyCruiseY));
        addRenderableWidget(cruiseBox);
        addRenderableWidget(Button.builder(Component.literal("SET TARGET"), b -> applyTarget())
                .bounds(panelX + 182, panelY + 64, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SET CRUISE"), b -> applyCruise())
                .bounds(panelX + 90, panelY + 116, 90, 18).build());
    }

    private void initBody() {
        bodyBaseBox = box(panelX + 12, panelY + 70, 146, xyz(bodyBase));
        bodyCenterBox = box(panelX + 12, panelY + 100, 146, xyz(bodyCenter));
        bodyNoseBox = box(panelX + 12, panelY + 130, 146, xyz(bodyNose));
        addRenderableWidget(Button.builder(Component.literal(anchorButton(0, "BASE")), b -> selectBodyAnchor(0))
                .bounds(panelX + 12, panelY + 152, 46, 16).build());
        addRenderableWidget(Button.builder(Component.literal(anchorButton(1, "CENTER")), b -> selectBodyAnchor(1))
                .bounds(panelX + 60, panelY + 152, 52, 16).build());
        addRenderableWidget(Button.builder(Component.literal(anchorButton(2, "NOSE")), b -> selectBodyAnchor(2))
                .bounds(panelX + 114, panelY + 152, 44, 16).build());
        addRenderableWidget(Button.builder(Component.literal("APPLY"), b -> applyBodyCalibration())
                .bounds(panelX + 12, panelY + 172, 46, 16).build());
        addRenderableWidget(Button.builder(Component.literal("DEFAULTS"), b -> resetBodyCalibration())
                .bounds(panelX + 60, panelY + 172, 52, 16).build());
        addRenderableWidget(Button.builder(Component.literal("SWAP"), b -> swapBodyEnds())
                .bounds(panelX + 114, panelY + 172, 44, 16).build());
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox edit = new EditBox(font, x, y, w, 16, Component.literal("xyz"));
        edit.setValue(value);
        addRenderableWidget(edit);
        return edit;
    }

    private String anchorButton(int anchor, String name) {
        return selectedBodyAnchor == anchor ? ">" + name : name;
    }

    private void selectBodyAnchor(int anchor) {
        captureBodyFields();
        selectedBodyAnchor = Math.max(0, Math.min(2, anchor));
        rebuild();
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
        String distinct = distinctBodyError(bodyBase, bodyCenter, bodyNose);
        if (distinct != null) {
            return distinct;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return "World is not available";
        }
        BlockPos[] positions = {bodyBase, bodyCenter, bodyNose};
        String[] names = {"Base", "Center", "Nose"};
        for (int i = 0; i < positions.length; i++) {
            BlockPos position = positions[i];
            if (!minecraft.level.hasChunkAt(position)) {
                return names[i] + " block is not loaded";
            }
            if (minecraft.level.getBlockState(position).isAir()) {
                return names[i] + " position is not occupied";
            }
        }
        return null;
    }

    private void resetBodyCalibration() {
        bodyCenter = openData.computerPos();
        bodyBase = bodyCenter.below();
        bodyNose = bodyCenter.above();
        selectedBodyAnchor = 2;
        rebuild();
        notice = "Default local +Y nose selected; APPLY to save";
    }

    private void swapBodyEnds() {
        captureBodyFields();
        BlockPos oldBase = bodyBase;
        bodyBase = bodyNose;
        bodyNose = oldBase;
        selectedBodyAnchor = 2;
        rebuild();
        ModNetwork.sendToServer(new BodyCalibrationPacket(openData.computerPos(),
                bodyBase, bodyCenter, bodyNose));
        notice = "Base/nose swapped and saved — red NOSE end will face the route";
    }

    private void captureBodyFields() {
        if (bodyBaseBox == null) {
            return;
        }
        try {
            bodyBase = parseBlockPos(bodyBaseBox.getValue(), bodyBase);
            bodyCenter = parseBlockPos(bodyCenterBox.getValue(), bodyCenter);
            bodyNose = parseBlockPos(bodyNoseBox.getValue(), bodyNose);
        } catch (NumberFormatException ignored) {
        }
    }

    static BlockPos parseBlockPos(String value, BlockPos fallback) {
        String[] split = value.trim().split("[,\\s]+");
        if (split.length != 3) {
            throw new NumberFormatException("XYZ required");
        }
        return new BlockPos(Integer.parseInt(split[0]), Integer.parseInt(split[1]),
                Integer.parseInt(split[2]));
    }

    private static String xyz(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private void loadBodyVisualization() {
        bodyBase = openData.missileBase();
        bodyCenter = openData.missileCenter();
        bodyNose = openData.missileNose();
        bodyMinX = bodyCenter.getX() - 8;
        bodyMaxX = bodyCenter.getX() + 8;
        bodyMinY = bodyCenter.getY() - 8;
        bodyMaxY = bodyCenter.getY() + 8;
        bodyMinZ = bodyCenter.getZ() - 8;
        bodyMaxZ = bodyCenter.getZ() + 8;
        bodyVoxelSamples.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
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
        double ratio = sx * (double) sy * sz / 20_000.0;
        int step = Math.max(1, (int) Math.ceil(Math.cbrt(Math.max(1.0, ratio))));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bodyMinX; x <= bodyMaxX; x += step) {
            for (int y = bodyMinY; y <= bodyMaxY; y += step) {
                for (int z = bodyMinZ; z <= bodyMaxZ; z += step) {
                    cursor.set(x, y, z);
                    if (!minecraft.level.hasChunkAt(cursor)
                            || minecraft.level.getBlockState(cursor).isAir()) {
                        continue;
                    }
                    if (bodyVoxelSamples.size() < 20_000) {
                        bodyVoxelSamples.add(cursor.immutable());
                    }
                }
            }
        }
    }

    private static int aabbInt(Object bounds, String component) throws ReflectiveOperationException {
        Object value = bounds.getClass().getMethod(component).invoke(bounds);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void setControllerMode(ControllerMode mode) {
        ModNetwork.sendToServer(AeroControlPacket.setMode(openData.computerPos(), mode));
        notice = mode == ControllerMode.FLIGHT
                ? "Flight mode requested — ENGAGE after it arms"
                : "Missile mode requested — set target, then LAUNCH";
    }

    private void launch() {
        applyTarget();
        applyGuideY();
        applyCruise();
        ModNetwork.sendToServer(GuidanceControlPacket.launch(openData.computerPos()));
        notice = "Launch requested";
    }

    private void abortLaunch() {
        ModNetwork.sendToServer(GuidanceControlPacket.abort(openData.computerPos()));
        notice = "Abort requested";
    }

    private void setMode(GuidanceV2SurfaceMode mode) {
        GuidanceV2Network.setSurfaceModeFromClient(openData.computerPos(), mode);
        notice = mode.flapsOnly()
                ? "FLAPS ONLY — thrusters stay at zero"
                : "THRUST + FLAPS — mixer and surfaces together";
        rebuild();
    }

    private void stepThrottle(double delta) {
        if (GuidanceV2Client.surfaceMode(openData.computerPos()).flapsOnly()) {
            notice = "Throttle ignored in FLAPS ONLY";
            return;
        }
        double current = aeroState == null ? 0.0 : aeroState.throttle();
        double next = Math.max(0.0, Math.min(1.0, current + delta));
        ModNetwork.sendToServer(AeroControlPacket.setThrottle(openData.computerPos(), next));
        notice = String.format(Locale.ROOT, "Throttle %.0f%% requested", next * 100.0);
    }

    private void stepFlap(double delta) {
        double current = aeroState == null ? 0.0 : aeroState.flapTarget();
        double next = Math.max(0.0, Math.min(1.0, current + delta));
        ModNetwork.sendToServer(AeroControlPacket.setFlap(openData.computerPos(), next));
        notice = String.format(Locale.ROOT, "Flaps %.0f%% requested", next * 100.0);
    }

    private void selectAutopilot(AeroAutopilotMode mode) {
        applyTarget();
        ModNetwork.sendToServer(AeroControlPacket.setAutopilot(openData.computerPos(), mode));
        if (GuidanceV2Client.surfaceMode(openData.computerPos()).flapsOnly()) {
            notice = "Autopilot is surfaces-only — no boost or brake thrusters";
        } else {
            notice = "Autopilot " + mode.name().toLowerCase(Locale.ROOT);
        }
    }

    private void applyTarget() {
        if (targetBox == null) {
            return;
        }
        String[] parts = targetBox.getValue().trim().split("[ ,]+");
        if (parts.length < 3) {
            notice = "Target needs X Y Z";
            return;
        }
        try {
            currentTarget = new BlockPos(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            ModNetwork.sendToServer(GuidanceControlPacket.setTarget(openData.computerPos(),
                    currentTarget.getX(), currentTarget.getY(), currentTarget.getZ()));
            notice = "Target set";
        } catch (NumberFormatException ignored) {
            notice = "Target needs integer X Y Z";
        }
    }

    private void applyGuideY() {
        if (guideBox == null) {
            return;
        }
        Integer parsed = parseWorldY(guideBox.getValue());
        if (parsed == null) {
            notice = "Guide Y must be a whole number (0 = auto)";
            return;
        }
        easyApexY = parsed;
        guideBox.setValue(Integer.toString(easyApexY));
        ModNetwork.sendToServer(GuidanceControlPacket.setApexY(openData.computerPos(), easyApexY));
        notice = easyApexY == 0
                ? "Guide Y AUTO — loft from range, then steer"
                : "Guide Y " + easyApexY + " — climb here, then start guidance";
    }

    private void applyCruise() {
        if (cruiseBox == null) {
            return;
        }
        Integer parsed = parseWorldY(cruiseBox.getValue());
        if (parsed == null) {
            notice = "Cruise Y must be a whole number (0 = same as guide Y)";
            return;
        }
        easyCruiseY = parsed;
        cruiseBox.setValue(Integer.toString(easyCruiseY));
        ModNetwork.sendToServer(GuidanceControlPacket.setCruiseY(openData.computerPos(), easyCruiseY));
        notice = easyCruiseY == 0
                ? "Cruise Y = guide Y"
                : "Cruise Y " + easyCruiseY;
    }

    /** 0 = auto. Null if the field is not a whole number. */
    static Integer parseWorldY(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        HudDraw.fillRect(graphics, panelX, panelY, PANEL_W, PANEL_H, COLOR_BG);
        GuidanceV2ArtLayout.blitNineSlice(graphics, GuidanceV2ArtLayout.PANEL,
                panelX, panelY, PANEL_W, PANEL_H, GuidanceV2ArtLayout.PANEL_CORNER);
        int tabX = panelX + 10;
        for (Tab value : Tab.values()) {
            GuidanceV2ArtLayout.blitBar(graphics, tab == value ? GuidanceV2ArtLayout.TAB_HOT : GuidanceV2ArtLayout.TAB,
                    tabX, panelY + 26, TAB_W, 20, 1f);
            tabX += TAB_STEP;
        }
        boolean flightUi = isFlightUi(aeroState);
        graphics.drawString(font, flightUi ? "GUIDANCE v2  FLIGHT" : "GUIDANCE v2  MISSILE",
                panelX + 12, panelY + 10, COLOR_CYAN, false);
        String host = openData.status() == null ? "" : openData.status();
        graphics.drawString(font, host, panelX + 200, panelY + 10, COLOR_AMBER, false);

        GuidanceV2Bus bus = GuidanceV2Client.bus();
        GuidanceV2SurfaceMode mode = GuidanceV2Client.surfaceMode(openData.computerPos());
        if (tab == Tab.FLIGHT && flightUi) {
            double thr = bus != null ? bus.appliedThrottle() : (aeroState == null ? 0.0 : aeroState.throttle());
            double flap = aeroState == null ? 0.0 : aeroState.flap();
            String authority = mode.flapsOnly() ? "AUTH  FLAPS" : "AUTH  THR + FLAPS";
            graphics.drawString(font, authority, panelX + 14, panelY + 132, COLOR_MAGENTA, false);
            GuidanceV2ArtLayout.blitBar(graphics, GuidanceV2ArtLayout.BAR_EMPTY,
                    panelX + 14, panelY + 146, 300, 14, 1f);
            GuidanceV2ArtLayout.blitBar(graphics, GuidanceV2ArtLayout.BAR_FULL,
                    panelX + 14, panelY + 146, 300, 14, (float) thr);
            GuidanceV2ArtLayout.blitBar(graphics, GuidanceV2ArtLayout.BAR_EMPTY,
                    panelX + 14, panelY + 162, 300, 14, 1f);
            GuidanceV2ArtLayout.blitBar(graphics, GuidanceV2ArtLayout.BAR_FLAP,
                    panelX + 14, panelY + 162, 300, 14, (float) flap);
            graphics.drawString(font, String.format(Locale.ROOT, "THR %3.0f%%   FLP %3.0f%%",
                    thr * 100.0, flap * 100.0), panelX + 14, panelY + 178, COLOR_TEXT, false);
            if (aeroState != null) {
                graphics.drawString(font, String.format(Locale.ROOT, "ATT  Y %.0f  P %.0f  R %.0f",
                        aeroState.yawDeg(), aeroState.pitchDeg(), aeroState.rollDeg()),
                        panelX + 14, panelY + 190, COLOR_DIM, false);
            }
        } else if (tab == Tab.FLIGHT) {
            graphics.drawString(font, "Guide Y: climb to this world Y, then steer (0 = auto).",
                    panelX + 14, panelY + 156, COLOR_AMBER, false);
            graphics.drawString(font, "1. Target  2. Guide Y  3. Pair  4. LAUNCH. Shift-click aborts.",
                    panelX + 14, panelY + 170, COLOR_TEXT, false);
            graphics.drawString(font, String.format(Locale.ROOT, "Target  %d  %d  %d   Guide Y  %s",
                    currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(),
                    easyApexY <= 0 ? "AUTO" : Integer.toString(easyApexY)),
                    panelX + 14, panelY + 186, COLOR_AMBER, false);
        } else if (tab == Tab.SURFACES) {
            int count = bus == null ? 0 : bus.linkedSurfaces();
            graphics.drawString(font, "Linked surfaces: " + count, panelX + 14, panelY + 64, COLOR_TEXT, false);
            graphics.drawString(font, "Pitch  " + flag(bus != null && bus.hasPitch()),
                    panelX + 14, panelY + 84, COLOR_CYAN, false);
            graphics.drawString(font, "Roll   " + flag(bus != null && bus.hasRoll()),
                    panelX + 14, panelY + 98, COLOR_CYAN, false);
            graphics.drawString(font, "Yaw    " + flag(bus != null && bus.hasYaw()),
                    panelX + 14, panelY + 112, COLOR_CYAN, false);
            graphics.drawString(font, "Brake  " + flag(bus != null && bus.hasBrake()),
                    panelX + 14, panelY + 126, COLOR_AMBER, false);
            graphics.drawString(font, "Facing: /xenoguidance flap facing <dir>",
                    panelX + 14, panelY + 150, COLOR_DIM, false);
            graphics.drawString(font, "Role + facing + orient: (fork) panel configurator",
                    panelX + 14, panelY + 164, COLOR_DIM, false);
            if (bus != null && bus.missingAttitudeSurfaces()) {
                graphics.drawString(font, "Link pitch / roll / yaw flaps",
                        panelX + 14, panelY + 184, COLOR_MAGENTA, false);
            }
        } else if (tab == Tab.MODE) {
            GuidanceV2ArtLayout.blit(graphics, GuidanceV2ArtLayout.MODE_CHIP, panelX + 230, panelY + 58);
            if (!flightUi) {
                graphics.drawString(font, "Missile mode: set XYZ and Guide Y, then LAUNCH.",
                        panelX + 14, panelY + 88, COLOR_TEXT, false);
                graphics.drawString(font, "Switch to FLIGHT to sit and fly with throttle and flaps.",
                        panelX + 14, panelY + 104, COLOR_DIM, false);
            } else {
                graphics.drawString(font, mode.flapsOnly()
                                ? "Surfaces only. VectorMixer and thruster force stay zero."
                                : "Thrusters mix with control surfaces.",
                        panelX + 14, panelY + 154, COLOR_TEXT, false);
                if (mode.flapsOnly() && bus != null && bus.cannotBleedSpeed()) {
                    graphics.drawString(font, "No brake surfaces — craft cannot bleed speed",
                            panelX + 14, panelY + 174, COLOR_AMBER, false);
                }
            }
        } else if (tab == Tab.NAV) {
            graphics.drawString(font, "Target XYZ", panelX + 14, panelY + 52, COLOR_CYAN, false);
            graphics.drawString(font, "Guide Y — climb here, then start guidance (0 = auto)",
                    panelX + 198, panelY + 94, COLOR_AMBER, false);
            graphics.drawString(font, "Cruise Y — level flight after loft (0 = same as guide)",
                    panelX + 198, panelY + 120, COLOR_DIM, false);
        } else if (tab == Tab.BODY) {
            graphics.drawString(font, "Base", panelX + 12, panelY + 58, COLOR_AMBER, false);
            graphics.drawString(font, "Center", panelX + 12, panelY + 88, COLOR_CYAN, false);
            graphics.drawString(font, "Nose", panelX + 12, panelY + 118, COLOR_MAGENTA, false);
            drawBodyVisualizer(graphics);
        }

        if (notice != null && !notice.isBlank()) {
            GuidanceV2ArtLayout.blit(graphics, GuidanceV2ArtLayout.WARN, panelX + 12, panelY + PANEL_H - 48);
        }
        graphics.drawString(font, notice == null ? "" : notice, panelX + 12, panelY + PANEL_H - 44, COLOR_AMBER, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String flag(boolean on) {
        return on ? "LINKED" : "MISSING";
    }

    private void drawBodyVisualizer(GuiGraphics g) {
        int gx = panelX + BODY_VIEW_X;
        int gy = panelY + BODY_VIEW_Y;
        int gw = BODY_VIEW_W;
        int gh = BODY_VIEW_H;
        HudDraw.fillRect(g, gx, gy, gw, gh, 0xE0061019);
        g.renderOutline(gx, gy, gw, gh, COLOR_EDGE);
        g.enableScissor(gx + 1, gy + 1, gx + gw - 1, gy + gh - 1);
        g.drawString(font, "3D HULL", gx + 6, gy + 5, COLOR_CYAN, false);
        g.drawString(font, "drag · wheel · click", gx + 6, gy + gh - 12, COLOR_DIM, false);

        double maxSpan = bodyMaxSpan();
        for (BlockPos voxel : bodyVoxelSamples) {
            int sx = projectBodyX(voxel, gx, gw, maxSpan);
            int sy = projectBodyY(voxel, gy, gh, maxSpan);
            double depth = projectBodyDepth(voxel);
            int shade = Math.max(55, Math.min(145, 92 + (int) Math.round(depth / maxSpan * 70.0)));
            int color = 0xff000000 | (shade / 2 << 16) | (shade << 8) | Math.min(255, shade + 35);
            g.fill(sx, sy, sx + 2, sy + 2, color);
        }

        drawBodyAxisLine(g, bodyBase, bodyCenter, COLOR_AMBER, gx, gy, gw, gh, maxSpan);
        drawBodyAxisLine(g, bodyCenter, bodyNose, COLOR_MAGENTA, gx, gy, gw, gh, maxSpan);
        drawBodyMarker3d(g, bodyBase, COLOR_AMBER, gx, gy, gw, gh, maxSpan);
        drawBodyMarker3d(g, bodyCenter, COLOR_CYAN, gx, gy, gw, gh, maxSpan);
        drawBodyMarker3d(g, bodyNose, COLOR_MAGENTA, gx, gy, gw, gh, maxSpan);
        g.disableScissor();
    }

    private void drawBodyAxisLine(GuiGraphics g, BlockPos a, BlockPos b, int color,
                                  int gx, int gy, int gw, int gh, double maxSpan) {
        drawLine(g, projectBodyX(a, gx, gw, maxSpan), projectBodyY(a, gy, gh, maxSpan),
                projectBodyX(b, gx, gw, maxSpan), projectBodyY(b, gy, gh, maxSpan), color);
    }

    private void drawBodyMarker3d(GuiGraphics g, BlockPos p, int color,
                                  int gx, int gy, int gw, int gh, double maxSpan) {
        int x = projectBodyX(p, gx, gw, maxSpan);
        int y = projectBodyY(p, gy, gh, maxSpan);
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xff071019);
        g.fill(x - 2, y - 2, x + 3, y + 3, color);
    }

    private double bodyMaxSpan() {
        return Math.max(1.0, Math.max(bodyMaxX - bodyMinX + 1.0,
                Math.max(bodyMaxY - bodyMinY + 1.0, bodyMaxZ - bodyMinZ + 1.0)));
    }

    private double bodyRelativeX(BlockPos p) {
        return p.getX() - (bodyMinX + bodyMaxX) * 0.5;
    }

    private double bodyRelativeY(BlockPos p) {
        return p.getY() - (bodyMinY + bodyMaxY) * 0.5;
    }

    private double bodyRelativeZ(BlockPos p) {
        return p.getZ() - (bodyMinZ + bodyMaxZ) * 0.5;
    }

    private int projectBodyX(BlockPos p, int gx, int gw, double maxSpan) {
        double rotatedX = Math.cos(bodyYaw) * bodyRelativeX(p) - Math.sin(bodyYaw) * bodyRelativeZ(p);
        double scale = Math.min(gw - 20.0, 142.0) * bodyZoom / maxSpan;
        return gx + gw / 2 + (int) Math.round(rotatedX * scale);
    }

    private int projectBodyY(BlockPos p, int gy, int gh, double maxSpan) {
        double depthYaw = Math.sin(bodyYaw) * bodyRelativeX(p) + Math.cos(bodyYaw) * bodyRelativeZ(p);
        double rotatedY = Math.cos(bodyPitch) * bodyRelativeY(p) - Math.sin(bodyPitch) * depthYaw;
        double scale = Math.min(BODY_VIEW_W - 20.0, gh - 30.0) * bodyZoom / maxSpan;
        return gy + gh / 2 - (int) Math.round(rotatedY * scale);
    }

    private double projectBodyDepth(BlockPos p) {
        double depthYaw = Math.sin(bodyYaw) * bodyRelativeX(p) + Math.cos(bodyYaw) * bodyRelativeZ(p);
        return Math.sin(bodyPitch) * bodyRelativeY(p) + Math.cos(bodyPitch) * depthYaw;
    }

    private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    private boolean insideBodyViewport(double mouseX, double mouseY) {
        int gx = panelX + BODY_VIEW_X;
        int gy = panelY + BODY_VIEW_Y;
        return mouseX >= gx && mouseX <= gx + BODY_VIEW_W
                && mouseY >= gy && mouseY <= gy + BODY_VIEW_H;
    }

    private void pickBodyVoxel(double mouseX, double mouseY) {
        int gx = panelX + BODY_VIEW_X;
        int gy = panelY + BODY_VIEW_Y;
        double maxSpan = bodyMaxSpan();
        BlockPos best = null;
        double bestDistance = 9.0 * 9.0;
        double bestDepth = -Double.MAX_VALUE;
        for (BlockPos voxel : bodyVoxelSamples) {
            double dx = projectBodyX(voxel, gx, BODY_VIEW_W, maxSpan) - mouseX;
            double dy = projectBodyY(voxel, gy, BODY_VIEW_H, maxSpan) - mouseY;
            double distance = dx * dx + dy * dy;
            double depth = projectBodyDepth(voxel);
            if (distance < bestDistance || (Math.abs(distance - bestDistance) < 0.5 && depth > bestDepth)) {
                best = voxel;
                bestDistance = distance;
                bestDepth = depth;
            }
        }
        if (best != null) {
            setSelectedBodyAnchor(best);
        } else {
            notice = "No hull block under cursor; rotate or zoom closer";
        }
    }

    private void setSelectedBodyAnchor(BlockPos value) {
        if (selectedBodyAnchor == 0) {
            bodyBase = value;
        } else if (selectedBodyAnchor == 1) {
            bodyCenter = value;
        } else {
            bodyNose = value;
        }
        if (bodyBaseBox != null) {
            bodyBaseBox.setValue(xyz(bodyBase));
        }
        if (bodyCenterBox != null) {
            bodyCenterBox.setValue(xyz(bodyCenter));
        }
        if (bodyNoseBox != null) {
            bodyNoseBox.setValue(xyz(bodyNose));
        }
        notice = "Selected " + (selectedBodyAnchor == 0 ? "base" : selectedBodyAnchor == 1 ? "center" : "nose")
                + " block " + xyz(value);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.BODY && button == 0 && insideBodyViewport(mouseX, mouseY)) {
            bodyDragging = true;
            bodyDragMoved = false;
            bodyLastMouseX = mouseX;
            bodyLastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.BODY && bodyDragging && button == 0) {
            double dx = mouseX - bodyLastMouseX;
            double dy = mouseY - bodyLastMouseY;
            if (Math.abs(dx) + Math.abs(dy) > 0.5) {
                bodyDragMoved = true;
            }
            bodyYaw += dx * 0.012;
            bodyPitch = Math.max(Math.toRadians(-85), Math.min(Math.toRadians(85),
                    bodyPitch + dy * 0.012));
            bodyLastMouseX = mouseX;
            bodyLastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tab == Tab.BODY && bodyDragging && button == 0) {
            bodyDragging = false;
            if (!bodyDragMoved) {
                pickBodyVoxel(mouseX, mouseY);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.BODY && insideBodyViewport(mouseX, mouseY)) {
            bodyZoom = Math.max(0.45, Math.min(4.0, bodyZoom * Math.pow(1.14, scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
