package net.bullettrain.xenopixelsmod.client.gui;

import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.GuidanceControlPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.bullettrain.xenopixelsmod.client.screen.UnblurredScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Ballistic Guidance — target XYZ, speed, loft Y (climb peak), cruise Y (level flight).
 */
public class GuidanceScreen extends UnblurredScreen {
    private final BlockPos computerPos;
    private final int initialX, initialY, initialZ;
    private final String statusLine;
    private final int pairedThrusters;
    private final int initialSpeed;
    /** 0 = auto loft peak */
    private final int initialApexY;
    /** 0 = cruise at loft peak */
    private final int initialCruiseY;
    private final int initialFleetChannel;
    private final int initialSalvoInterval;
    private final double initialGravitySi;
    private final double initialDrag;

    private EditBox xyzBox;
    private EditBox speedBox;
    private EditBox apexBox;
    private EditBox cruiseBox;
    private EditBox fleetBox;
    private EditBox salvoIntervalBox;
    private EditBox gravityBox;
    private EditBox dragBox;

    public GuidanceScreen(BlockPos computerPos, int initialX, int initialY, int initialZ,
                          String statusLine, int pairedThrusters, int speedLevel,
                          int apexY, int cruiseY, int fleetChannel, int salvoIntervalTicks) {
        this(computerPos, initialX, initialY, initialZ, statusLine, pairedThrusters, speedLevel,
                apexY, cruiseY, fleetChannel, salvoIntervalTicks, 9.80665, 0.00002);
    }

    public GuidanceScreen(BlockPos computerPos, int initialX, int initialY, int initialZ,
                          String statusLine, int pairedThrusters, int speedLevel,
                          int apexY, int cruiseY, int fleetChannel, int salvoIntervalTicks,
                          double gravitySi, double dragCoefficient) {
        super(Component.literal("Ballistic Guidance"));
        this.computerPos = computerPos;
        this.initialX = initialX;
        this.initialY = initialY;
        this.initialZ = initialZ;
        this.statusLine = statusLine != null ? statusLine : "idle";
        this.pairedThrusters = pairedThrusters;
        this.initialSpeed = Math.max(1, Math.min(20, speedLevel));
        this.initialApexY = Math.max(0, apexY);
        this.initialCruiseY = Math.max(0, cruiseY);
        this.initialFleetChannel = Math.max(0, Math.min(9_999, fleetChannel));
        this.initialSalvoInterval = Math.max(1, Math.min(200, salvoIntervalTicks));
        this.initialGravitySi = Math.max(0.01, Math.min(100.0, gravitySi));
        this.initialDrag = Math.max(0.0, Math.min(0.01, dragCoefficient));
    }

    /** Back-compat open without cruise. */
    public GuidanceScreen(BlockPos computerPos, int initialX, int initialY, int initialZ,
                          String statusLine, int pairedThrusters, int speedLevel, int apexY) {
        this(computerPos, initialX, initialY, initialZ, statusLine, pairedThrusters, speedLevel,
                apexY, 0, 0, 10);
    }

    public GuidanceScreen(BlockPos computerPos, int initialX, int initialY, int initialZ,
                          String statusLine, int pairedThrusters, int speedLevel,
                          int apexY, int cruiseY) {
        this(computerPos, initialX, initialY, initialZ, statusLine, pairedThrusters, speedLevel,
                apexY, cruiseY, 0, 10);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = Math.max(2, this.height / 2 - 167);

        // Target XYZ
        this.xyzBox = new EditBox(this.font, cx - 110, top + 22, 220, 18, Component.literal("X Y Z"));
        this.xyzBox.setMaxLength(48);
        this.xyzBox.setValue(initialX + " " + initialY + " " + initialZ);
        this.xyzBox.setHint(Component.literal("x y z"));
        this.addRenderableWidget(this.xyzBox);

        this.addRenderableWidget(Button.builder(Component.literal("Calculate / Set"), btn -> onSet())
                .bounds(cx - 110, top + 44, 105, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Fill From Look"), btn -> fillFromLook())
                .bounds(cx + 5, top + 44, 105, 18).build());

        // Speed
        this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> nudgeSpeed(-1))
                .bounds(cx - 110, top + 78, 20, 18).build());
        this.speedBox = new EditBox(this.font, cx - 88, top + 78, 36, 18, Component.literal("Speed"));
        this.speedBox.setMaxLength(2);
        this.speedBox.setValue(Integer.toString(initialSpeed));
        this.addRenderableWidget(this.speedBox);
        this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> nudgeSpeed(1))
                .bounds(cx - 50, top + 78, 20, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Light"), btn -> setSpeedUi(3))
                .bounds(cx - 26, top + 78, 38, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Norm"), btn -> setSpeedUi(5))
                .bounds(cx + 14, top + 78, 36, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Heavy"), btn -> setSpeedUi(14))
                .bounds(cx + 52, top + 78, 40, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("MAX"), btn -> setSpeedUi(20))
                .bounds(cx + 94, top + 78, 32, 18).build());

        // Loft Y — climb peak
        this.apexBox = new EditBox(this.font, cx - 110, top + 112, 56, 18, Component.literal("Loft Y"));
        this.apexBox.setMaxLength(10);
        this.apexBox.setValue(initialApexY <= 0 ? "0" : Integer.toString(initialApexY));
        this.apexBox.setHint(Component.literal("0=auto"));
        this.addRenderableWidget(this.apexBox);
        this.addRenderableWidget(Button.builder(Component.literal("Auto"), btn -> {
            apexBox.setValue("0");
            onApplyApex();
        }).bounds(cx - 50, top + 112, 36, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+100"), btn -> nudgeApex(100))
                .bounds(cx - 10, top + 112, 40, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Set Loft"), btn -> onApplyApex())
                .bounds(cx + 34, top + 112, 52, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("=Cruise"), btn -> {
            try {
                int c = parseCruise();
                if (c > 0) {
                    apexBox.setValue(Integer.toString(c));
                    onApplyApex();
                }
            } catch (NumberFormatException ignored) {
            }
        }).bounds(cx + 90, top + 112, 46, 18).build());

        // Cruise Y — level flight after loft
        this.cruiseBox = new EditBox(this.font, cx - 110, top + 146, 56, 18, Component.literal("Cruise Y"));
        this.cruiseBox.setMaxLength(10);
        this.cruiseBox.setValue(initialCruiseY <= 0 ? "0" : Integer.toString(initialCruiseY));
        this.cruiseBox.setHint(Component.literal("0=loft"));
        this.addRenderableWidget(this.cruiseBox);
        this.addRenderableWidget(Button.builder(Component.literal("=Loft"), btn -> {
            cruiseBox.setValue("0");
            onApplyCruise();
        }).bounds(cx - 50, top + 146, 36, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+100"), btn -> nudgeCruise(100))
                .bounds(cx - 10, top + 146, 40, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Set Cruise"), btn -> onApplyCruise())
                .bounds(cx + 34, top + 146, 64, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Set Speed"), btn -> onApplySpeed())
                .bounds(cx - 110, top + 170, 105, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Apply All"), btn -> onApplyAll())
                .bounds(cx + 5, top + 170, 105, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Pair Nearby Thrusters"), btn -> onPair())
                .bounds(cx - 110, top + 194, 105, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Unpair All"), btn -> onUnpair())
                .bounds(cx + 5, top + 194, 105, 18).build());

        this.fleetBox = new EditBox(this.font, cx - 110, top + 218, 34, 18, Component.literal("Fleet channel"));
        this.fleetBox.setMaxLength(4);
        this.fleetBox.setValue(Integer.toString(initialFleetChannel));
        this.addRenderableWidget(this.fleetBox);
        this.salvoIntervalBox = new EditBox(this.font, cx - 70, top + 218, 34, 18, Component.literal("Salvo ticks"));
        this.salvoIntervalBox.setMaxLength(3);
        this.salvoIntervalBox.setValue(Integer.toString(initialSalvoInterval));
        this.addRenderableWidget(this.salvoIntervalBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set Fleet"), btn -> onSetFleet())
                .bounds(cx - 30, top + 218, 64, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Fleet Salvo"), btn -> onFleetSalvo())
                .bounds(cx + 38, top + 218, 72, 18).build());

        this.gravityBox = new EditBox(this.font, cx - 110, top + 242, 48, 18, Component.literal("Gravity m/s²"));
        this.gravityBox.setMaxLength(10);
        this.gravityBox.setValue(String.format(java.util.Locale.ROOT, "%.5f", initialGravitySi));
        this.addRenderableWidget(this.gravityBox);
        this.dragBox = new EditBox(this.font, cx - 58, top + 242, 60, 18, Component.literal("Drag /m"));
        this.dragBox.setMaxLength(12);
        this.dragBox.setValue(String.format(java.util.Locale.ROOT, "%.8f", initialDrag));
        this.addRenderableWidget(this.dragBox);
        this.addRenderableWidget(Button.builder(Component.literal("Apply Model"), btn -> onApplyPhysics())
                .bounds(cx + 6, top + 242, 62, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Earth"), btn -> setEarthModel())
                .bounds(cx + 72, top + 242, 38, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Launch"), btn -> onLaunch())
                .bounds(cx - 110, top + 266, 105, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Abort Flight"), btn -> onAbort())
                .bounds(cx + 5, top + 266, 105, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
                .bounds(cx - 50, top + 292, 100, 18).build());

        this.setInitialFocus(this.xyzBox);
    }

    private void nudgeSpeed(int delta) {
        int cur;
        try {
            cur = parseSpeed();
        } catch (NumberFormatException e) {
            cur = initialSpeed;
        }
        setSpeedUi(Math.max(1, Math.min(20, cur + delta)));
    }

    private void setSpeedUi(int level) {
        speedBox.setValue(Integer.toString(Math.max(1, Math.min(20, level))));
        onApplySpeed();
    }

    private void nudgeApex(int delta) {
        int cur = parseApex();
        if (cur <= 0 && minecraft != null && minecraft.player != null) {
            cur = (int) Math.floor(minecraft.player.getY()) + delta;
        } else if (cur <= 0) {
            cur = 100 + delta;
        } else {
            cur = saturatingAdd(cur, delta);
        }
        apexBox.setValue(Integer.toString(cur));
        onApplyApex();
    }

    private void nudgeCruise(int delta) {
        int cur = parseCruise();
        if (cur <= 0 && minecraft != null && minecraft.player != null) {
            cur = (int) Math.floor(minecraft.player.getY()) + delta;
        } else if (cur <= 0) {
            cur = 100 + delta;
        } else {
            cur = saturatingAdd(cur, delta);
        }
        cruiseBox.setValue(Integer.toString(cur));
        onApplyCruise();
    }

    private int parseSpeed() {
        int s = Integer.parseInt(speedBox.getValue().trim());
        if (s < 1 || s > 20) throw new NumberFormatException("speed 1-20");
        return s;
    }

    /** 0 = auto loft */
    private int parseApex() {
        String raw = apexBox.getValue().trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("auto")) return 0;
        int y = Integer.parseInt(raw);
        if (y < 0) return 0;
        return y;
    }

    /** 0 = same as loft */
    private int parseCruise() {
        String raw = cruiseBox.getValue().trim();
        if (raw.isEmpty() || raw.equalsIgnoreCase("auto") || raw.equalsIgnoreCase("loft")) return 0;
        int y = Integer.parseInt(raw);
        if (y < 0) return 0;
        return y;
    }

    private static int saturatingAdd(int value, int delta) {
        long result = (long) value + delta;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, result));
    }

    private static double accelFor(int level) {
        double t = level / 20.0;
        return Math.min(5.0, 0.12 + level * 0.10 + t * t * 0.80);
    }

    private static int burnFor(int level) {
        return Math.min(300, 24 + level * 11);
    }

    private static String weightLabel(int spd) {
        if (spd <= 4) return "light";
        if (spd <= 8) return "normal";
        if (spd <= 12) return "heavy";
        if (spd <= 16) return "very heavy";
        return "MAX";
    }

    private static int[] parseXyz(String raw) {
        if (raw == null || raw.isBlank()) throw new NumberFormatException("empty");
        String s = raw.trim().replace(',', ' ');
        String[] parts = s.split("\\s+");
        if (parts.length < 3) throw new NumberFormatException("need 3 numbers");
        return new int[]{
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
        };
    }

    private void onSet() {
        try {
            int[] p = parseXyz(xyzBox.getValue());
            pushHeightsQuiet();
            ModNetwork.sendToServer(GuidanceControlPacket.setTarget(computerPos, p[0], p[1], p[2]));
            toast("§aTarget set: " + p[0] + " " + p[1] + " " + p[2]);
        } catch (NumberFormatException e) {
            toast("§cInvalid — type like: 100 64 -200");
        }
    }

    private void onApplySpeed() {
        try {
            int s = parseSpeed();
            ModNetwork.sendToServer(GuidanceControlPacket.setSpeed(computerPos, s));
            toast("§6Speed " + s + "/20 §8(" + weightLabel(s) + ")");
        } catch (NumberFormatException e) {
            toast("§cSpeed must be 1–20");
        }
    }

    private void onApplyApex() {
        try {
            int y = parseApex();
            ModNetwork.sendToServer(GuidanceControlPacket.setApexY(computerPos, y));
            if (y <= 0) toast("§bLoft Y §fAUTO");
            else toast("§bLoft Y §f" + y + " §8(climb peak)");
        } catch (NumberFormatException e) {
            toast("§cLoft Y: world Y or 0 for auto");
        }
    }

    private void onApplyCruise() {
        try {
            int y = parseCruise();
            ModNetwork.sendToServer(GuidanceControlPacket.setCruiseY(computerPos, y));
            if (y <= 0) toast("§bCruise Y §f= loft");
            else toast("§bCruise Y §f" + y + " §8(level → target)");
        } catch (NumberFormatException e) {
            toast("§cCruise Y: world Y or 0 for =loft");
        }
    }

    private void onApplyAll() {
        onApplySpeed();
        onApplyApex();
        onApplyCruise();
        onApplyPhysics();
        onSet();
    }

    private void pushHeightsQuiet() {
        try {
            ModNetwork.sendToServer(GuidanceControlPacket.setSpeed(computerPos, parseSpeed()));
        } catch (NumberFormatException ignored) {
        }
        try {
            ModNetwork.sendToServer(GuidanceControlPacket.setApexY(computerPos, parseApex()));
        } catch (NumberFormatException ignored) {
        }
        try {
            ModNetwork.sendToServer(GuidanceControlPacket.setCruiseY(computerPos, parseCruise()));
        } catch (NumberFormatException ignored) {
        }
        try {
            ModNetwork.sendToServer(GuidanceControlPacket.setPhysics(
                    computerPos, parseGravity(), parseDrag()));
        } catch (NumberFormatException ignored) {
        }
    }

    private double parseGravity() {
        double gravity = Double.parseDouble(gravityBox.getValue().trim());
        if (!Double.isFinite(gravity) || gravity < 0.01 || gravity > 100.0) {
            throw new NumberFormatException("gravity 0.01-100");
        }
        return gravity;
    }

    private double parseDrag() {
        double drag = Double.parseDouble(dragBox.getValue().trim());
        if (!Double.isFinite(drag) || drag < 0.0 || drag > 0.01) {
            throw new NumberFormatException("drag 0-0.01");
        }
        return drag;
    }

    private void onApplyPhysics() {
        try {
            ModNetwork.sendToServer(GuidanceControlPacket.setPhysics(
                    computerPos, parseGravity(), parseDrag()));
            toast("§bBallistic model applied; set target to calculate solution");
        } catch (NumberFormatException e) {
            toast("§cGravity 0.01–100 m/s²; drag 0–0.01 /m");
        }
    }

    private void setEarthModel() {
        gravityBox.setValue("9.80665");
        dragBox.setValue("0.00002000");
        onApplyPhysics();
    }

    private void onPair() {
        ModNetwork.sendToServer(GuidanceControlPacket.pairNearby(computerPos));
        toast("§aPairing nearby thrusters…");
    }

    private void onUnpair() {
        ModNetwork.sendToServer(GuidanceControlPacket.clearPairs(computerPos));
    }

    private int parseFleetChannel() {
        int channel = Integer.parseInt(fleetBox.getValue().trim());
        if (channel < 0 || channel > 9_999) throw new NumberFormatException("channel 0-9999");
        return channel;
    }

    private int parseSalvoInterval() {
        int ticks = Integer.parseInt(salvoIntervalBox.getValue().trim());
        if (ticks < 1 || ticks > 200) throw new NumberFormatException("interval 1-200");
        return ticks;
    }

    private void onSetFleet() {
        try {
            int channel = parseFleetChannel();
            int interval = parseSalvoInterval();
            ModNetwork.sendToServer(GuidanceControlPacket.setFleet(computerPos, channel, interval));
            toast(channel == 0 ? "§7Standalone guidance" : "§aFleet " + channel + " §7· " + interval + "t stagger");
        } catch (NumberFormatException e) {
            toast("§cFleet channel 0–9999; stagger 1–200 ticks");
        }
    }

    private void onFleetSalvo() {
        try {
            int[] p = parseXyz(xyzBox.getValue());
            pushHeightsQuiet();
            ModNetwork.sendToServer(GuidanceControlPacket.setFleet(
                    computerPos, parseFleetChannel(), parseSalvoInterval()));
            ModNetwork.sendToServer(GuidanceControlPacket.setTarget(computerPos, p[0], p[1], p[2]));
            ModNetwork.sendToServer(GuidanceControlPacket.fleetSalvo(computerPos));
            this.onClose();
        } catch (NumberFormatException e) {
            toast("§cCheck XYZ, fleet channel, and stagger");
        }
    }

    private void onLaunch() {
        try {
            int[] p = parseXyz(xyzBox.getValue());
            int s = parseSpeed();
            int ay = parseApex();
            int cy = parseCruise();
            ModNetwork.sendToServer(GuidanceControlPacket.setSpeed(computerPos, s));
            ModNetwork.sendToServer(GuidanceControlPacket.setApexY(computerPos, ay));
            ModNetwork.sendToServer(GuidanceControlPacket.setCruiseY(computerPos, cy));
            ModNetwork.sendToServer(GuidanceControlPacket.setPhysics(
                    computerPos, parseGravity(), parseDrag()));
            ModNetwork.sendToServer(GuidanceControlPacket.setTarget(computerPos, p[0], p[1], p[2]));
            ModNetwork.sendToServer(GuidanceControlPacket.launch(computerPos));
            this.onClose();
        } catch (NumberFormatException e) {
            toast("§cCheck XYZ, Speed, Loft Y, Cruise Y, Gravity, and Drag");
        }
    }

    private void onAbort() {
        ModNetwork.sendToServer(GuidanceControlPacket.abort(computerPos));
    }

    private void fillFromLook() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        HitResult hit = this.minecraft.player.pick(256.0, 0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            BlockPos p = bhr.getBlockPos();
            xyzBox.setValue(p.getX() + " " + p.getY() + " " + p.getZ());
            toast("§aFilled: " + p.getX() + " " + p.getY() + " " + p.getZ());
        } else if (hit.getType() == HitResult.Type.MISS) {
            var loc = hit.getLocation();
            xyzBox.setValue((int) Math.floor(loc.x) + " " + (int) Math.floor(loc.y) + " "
                    + (int) Math.floor(loc.z));
            toast("§eFilled from look (air)");
        } else {
            toast("§cLook at a block first");
        }
    }

    private void toast(String msg) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal(msg), true);
        }
    }

    private int layoutTop() {
        return Math.max(2, this.height / 2 - 167);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int top = layoutTop();

        graphics.drawCenteredString(this.font, this.title, cx, top - 2, 0x55CCFF);
        graphics.drawCenteredString(this.font,
                "Computer @ " + computerPos.getX() + " " + computerPos.getY() + " " + computerPos.getZ(),
                cx, top + 10, 0x888888);

        graphics.drawString(this.font, "Target XYZ", cx - 110, top + 12, 0xA0A0A0);
        graphics.drawString(this.font, "§eSpeed 1–20", cx - 110, top + 68, 0xFFAA00);
        graphics.drawString(this.font, "§dLoft Y — climb peak (0=auto)", cx - 110, top + 102, 0xFF55FF);
        graphics.drawString(this.font, "§bCruise Y — level flight (0=same as loft)", cx - 110, top + 136, 0x55CCFF);
        graphics.drawString(this.font, "§aFleet channel / stagger ticks §8(0=standalone)",
                cx - 110, top + 208, 0x55FF77);
        graphics.drawString(this.font, "§bGravity m/s² / quadratic drag per metre",
                cx - 110, top + 232, 0x55CCFF);

        int spd;
        try {
            spd = Integer.parseInt(speedBox.getValue().trim());
            if (spd < 1 || spd > 20) spd = initialSpeed;
        } catch (Exception e) {
            spd = initialSpeed;
        }
        int loft;
        int cruise;
        try {
            loft = parseApex();
        } catch (Exception e) {
            loft = initialApexY;
        }
        try {
            cruise = parseCruise();
        } catch (Exception e) {
            cruise = initialCruiseY;
        }
        graphics.drawCenteredString(this.font, String.format(
                "§6%d/20 %s §7· a %.2f · burn %dt  §d| loft %s §b| cruise %s",
                spd, weightLabel(spd), accelFor(spd), burnFor(spd),
                loft <= 0 ? "AUTO" : Integer.toString(loft),
                cruise <= 0 ? "=loft" : Integer.toString(cruise)),
                cx, top + 190, 0xCCCCCC);

        var statusLines = this.font.split(Component.literal(
                "Paired: " + pairedThrusters + "  |  " + statusLine),
                Math.max(220, Math.min(440, this.width - 20)));
        for (int i = 0; i < Math.min(3, statusLines.size()); i++) {
            graphics.drawCenteredString(this.font, statusLines.get(i), cx, top + 314 + i * 10, 0xAAAAAA);
        }
        graphics.drawCenteredString(this.font,
                "Calculator: azimuth · elevation · required/available speed · ETA · miss",
                cx, top + 346, 0x666666);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
