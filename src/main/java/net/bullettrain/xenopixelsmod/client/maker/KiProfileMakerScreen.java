package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch;
import net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi;
import net.bullettrain.xenopixelsmod.combat.v3.ki.XenoKiProfile;
import net.bullettrain.xenopixelsmod.combat.v3.ki.XenoKiProfileCatalog;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueCatalog;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ki Profiles Maker — two-column layout: technique list | profile fields, with a fixed footer.
 *
 * <p>SP: no OP. Remote MP: OP required ({@link MakerAccess}).
 */
public final class KiProfileMakerScreen extends ScaledScreen {
    private static final String FRAME = "xeno_editor_panel";
    private static final String BANNER = "banner_top";
    private static final String TOOL = "mynpcs_button_row"; // 64×22 compact
    private static final String TOOL_WIDE = "mynpcs_button_row_w96";
    private static final XenoAtlasSprites.Theme CHROME = XenoAtlasSprites.Theme.GOLD;
    private static final int GOLD = 0xFFFFC14A;
    private static final int ROW = 18;
    private static final int LABEL_W = 70;
    private static final int FOOTER_H = 36;
    private static final int PAD = 20;

    private final Screen parent;
    private final InlineColorPicker colorPicker = new InlineColorPicker();

    private int frameX, frameY, frameW, frameH;
    private int bannerX, bannerY, bannerW, bannerH;
    private int listX, listY, listW, listH;
    private int editX, editY, editW;
    private int contentBottom;

    private EditBox filterBox;
    private EditBox interiorBox;
    private EditBox exteriorBox;
    private EditBox sizeBox;
    private EditBox speedBox;
    private EditBox damageBox;
    private EditBox armorBox;
    private EditBox offsetXBox;
    private EditBox offsetYBox;
    private EditBox offsetZBox;
    private EditBox renderTypeBox;
    private EditBox trailBox;
    private EditBox impactBox;
    private EditBox chargeBox;

    private List<V3TechniqueDefinition> techniques = List.of();
    private List<V3TechniqueDefinition> filtered = List.of();
    private int listScroll;
    private int selected = -1;
    private boolean centerMuzzle;
    private boolean effectsPage;
    private String status = "";
    private boolean dirty;
    private AtlasButton muzzleButton;

    public KiProfileMakerScreen(Screen parent) {
        super(Component.literal("Ki Profiles"));
        this.parent = parent;
    }

    @Override
    protected int getMinGuiWidth() {
        return 780;
    }

    @Override
    protected int getMinGuiHeight() {
        return 460;
    }

    @Override
    protected void init() {
        super.init();
        colorPicker.close();
        if (!MakerAccess.canOpenCosmeticMaker()) {
            status = MakerAccess.denyCosmetic().getString();
        }
        XenoKiProfileCatalog.ensureLoaded();
        techniques = V3TechniqueCatalog.all().stream()
                .filter(t -> t.kiTechnique() != null && !t.kiTechnique().isBlank())
                .sorted(Comparator.comparing(V3TechniqueDefinition::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, getUiWidth() - 8, getUiHeight() - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = (getUiWidth() - frameW) / 2;
        frameY = (getUiHeight() - frameH) / 2;
        bannerW = XenoAtlasSprites.get(BANNER).width();
        bannerH = XenoAtlasSprites.get(BANNER).height();
        bannerX = frameX + (frameW - bannerW) / 2;
        bannerY = frameY + 10;

        contentBottom = frameY + frameH - FOOTER_H - 8;
        listX = frameX + PAD;
        listY = bannerY + bannerH + 28;
        // Left ~38%, right gets the rest — never overlap.
        listW = Math.max(180, Math.min(260, (frameW - PAD * 3) * 38 / 100));
        listH = Math.max(80, contentBottom - listY);
        editX = listX + listW + PAD;
        editY = listY;
        editW = Math.max(200, frameX + frameW - editX - PAD);

        clearWidgets();
        String keepFilter = filterBox != null ? filterBox.getValue() : "";
        filterBox = new EditBox(font, listX, listY - 18, listW, 14, Component.literal("Filter"));
        filterBox.setMaxLength(64);
        filterBox.setValue(keepFilter);
        filterBox.setResponder(s -> {
            listScroll = 0;
            rebuildFilter();
        });
        addRenderableWidget(filterBox);

        int fieldRight = editX + editW;
        int boxX = editX + LABEL_W;
        int boxW = Math.max(72, fieldRight - boxX - ColorSwatch.W - 6);
        int toolW = AtlasButton.nativeWidth(TOOL);
        int toolH = AtlasButton.nativeHeight(TOOL);
        int y = editY;
        if (effectsPage) {
            renderTypeBox = addField(boxX, y, boxW); y += ROW;
            trailBox = addField(boxX, y, boxW); y += ROW;
            impactBox = addField(boxX, y, boxW); y += ROW;
            chargeBox = addField(boxX, y, boxW);
        } else {
            interiorBox = addField(boxX, y, boxW);
            addRenderableWidget(new ColorSwatch(boxX + boxW + 4, y - 1,
                    () -> interiorBox.getValue(),
                    () -> openColorPicker(interiorBox),
                    () -> colorPicker.isOpenFor(interiorBox)));
            y += ROW;
            exteriorBox = addField(boxX, y, boxW);
            addRenderableWidget(new ColorSwatch(boxX + boxW + 4, y - 1,
                    () -> exteriorBox.getValue(),
                    () -> openColorPicker(exteriorBox),
                    () -> colorPicker.isOpenFor(exteriorBox)));
            y += ROW;
            sizeBox = addField(boxX, y, boxW); y += ROW;
            speedBox = addField(boxX, y, boxW); y += ROW;
            damageBox = addField(boxX, y, boxW); y += ROW;
            armorBox = addField(boxX, y, boxW); y += ROW;
            offsetXBox = addField(boxX, y, boxW); y += ROW;
            offsetYBox = addField(boxX, y, boxW); y += ROW;
            offsetZBox = addField(boxX, y, boxW); y += ROW + 4;
            int muzzleY = Math.min(y, contentBottom - toolH - 4);
            muzzleButton = new AtlasButton(editX, muzzleY, toolW, toolH,
                    Component.literal(centerMuzzle ? "Muzzle: ON" : "Muzzle: off"), TOOL, b -> {
                centerMuzzle = !centerMuzzle;
                dirty = true;
                status = centerMuzzle ? "centerMuzzle ON" : "centerMuzzle OFF";
                if (muzzleButton != null) {
                    muzzleButton.setMessage(Component.literal(centerMuzzle ? "Muzzle: ON" : "Muzzle: off"));
                }
            });
            addRenderableWidget(muzzleButton);
        }

        addRenderableWidget(new AtlasButton(editX + toolW + 8, contentBottom - toolH - 4,
                toolW, toolH, Component.literal(effectsPage ? "Stats" : "Effects"), TOOL, b -> {
            if (dirty) {
                status = "Save this page before switching.";
                return;
            }
            effectsPage = !effectsPage;
            if (minecraft != null) minecraft.setScreen(this);
        }));

        int btnY = frameY + frameH - toolH - 12;
        int wideW = AtlasButton.nativeWidth(TOOL_WIDE);
        addRenderableWidget(new AtlasButton(editX, btnY, wideW, toolH, Component.literal("Save"),
                TOOL_WIDE, b -> saveSelected()));
        addRenderableWidget(new AtlasButton(editX + wideW + 6, btnY, wideW, toolH, Component.literal("Reload"),
                TOOL_WIDE, b -> reloadCatalog()));
        addRenderableWidget(new AtlasButton(frameX + frameW - toolW - PAD, btnY, toolW, toolH,
                Component.literal("Back"), TOOL, b -> onClose()));

        rebuildFilter();
        if (selected >= 0 && selected < filtered.size()) {
            loadSelectedIntoFields();
        } else {
            clearFields();
        }
    }

    private EditBox addField(int x, int y, int width) {
        EditBox box = new EditBox(font, x, y, width, 14, Component.empty());
        box.setMaxLength(32);
        box.setResponder(s -> dirty = true);
        addRenderableWidget(box);
        return box;
    }

    private void openColorPicker(EditBox box) {
        // Park the picker in the right column below the field, clamped to the frame.
        int anchorX = Math.min(box.getX() + box.getWidth() + ColorSwatch.W + 8, frameX + frameW - 220);
        int anchorY = Math.min(box.getY() + 18, contentBottom - 160);
        colorPicker.open(box, anchorX, anchorY, getUiWidth(), getUiHeight(), box.getValue(), hex -> {
            box.setValue(hex);
            dirty = true;
        });
    }

    private void rebuildFilter() {
        String q = filterBox == null ? "" : filterBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filtered = techniques;
        } else {
            List<V3TechniqueDefinition> next = new ArrayList<>();
            for (V3TechniqueDefinition t : techniques) {
                String hay = (t.name() + " " + t.id() + " " + t.kiTechnique() + " " + t.type())
                        .toLowerCase(Locale.ROOT);
                if (hay.contains(q)) next.add(t);
            }
            filtered = next;
        }
        if (selected >= filtered.size()) selected = filtered.isEmpty() ? -1 : 0;
    }

    private void loadSelectedIntoFields() {
        if (selected < 0 || selected >= filtered.size()) {
            clearFields();
            return;
        }
        V3TechniqueDefinition t = filtered.get(selected);
        XenoKiProfile p = XenoKiProfileCatalog.resolve(t);
        set(interiorBox, hex(p.colorInterior()));
        set(exteriorBox, hex(p.colorExterior() != null ? p.colorExterior() : p.colorInterior()));
        set(sizeBox, num(p.size()));
        set(speedBox, num(p.speed()));
        set(damageBox, num(p.damageMultiplier()));
        set(armorBox, p.armorPenetration() == null ? "" : Integer.toString(p.armorPenetration()));
        set(offsetXBox, num(p.castOffsetX()));
        set(offsetYBox, num(p.castOffsetY()));
        set(offsetZBox, num(p.castOffsetZ()));
        set(renderTypeBox, p.renderType() == null ? "" : Integer.toString(p.renderType()));
        set(trailBox, p.fxTrail());
        set(impactBox, p.fxImpact());
        set(chargeBox, p.fxCharge());
        centerMuzzle = p.centerMuzzle();
        dirty = false;
        status = t.name() + " → " + t.kiTechnique();
    }

    private void clearFields() {
        set(interiorBox, "");
        set(exteriorBox, "");
        set(sizeBox, "");
        set(speedBox, "");
        set(damageBox, "");
        set(armorBox, "");
        set(offsetXBox, "");
        set(offsetYBox, "");
        set(offsetZBox, "");
        set(renderTypeBox, "");
        set(trailBox, "");
        set(impactBox, "");
        set(chargeBox, "");
        centerMuzzle = false;
        dirty = false;
    }

    private static void set(EditBox box, String value) {
        if (box != null) box.setValue(value == null ? "" : value);
    }

    private static String hex(Integer rgb) {
        if (rgb == null) return "";
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static String num(Float v) {
        return v == null ? "" : String.format(Locale.ROOT, "%.3f", v);
    }

    private void saveSelected() {
        if (!MakerAccess.canOpenCosmeticMaker()) {
            status = MakerAccess.denyCosmetic().getString();
            return;
        }
        if (selected < 0 || selected >= filtered.size()) {
            status = "Select a technique first.";
            return;
        }
        V3TechniqueDefinition t = filtered.get(selected);
        XenoKiProfile current = XenoKiProfileCatalog.resolve(t);
        if (effectsPage && (!validFx(trailBox.getValue()) || !validFx(impactBox.getValue())
                || !validFx(chargeBox.getValue()))) {
            status = "Use a bundled ki_*.efkefc effect name.";
            return;
        }
        Integer renderType = effectsPage ? parseInt(renderTypeBox.getValue()) : current.renderType();
        if (effectsPage && renderType != null && (renderType < 0 || renderType > 9)) {
            status = "Render type must be 0–9.";
            return;
        }
        XenoKiProfile profile = effectsPage
                ? new XenoKiProfile(t.id(), current.nativeId(), current.colorInterior(),
                        current.colorExterior(), current.colorOutline(), current.size(), current.speed(),
                        current.damageMultiplier(), current.armorPenetration(), current.castOffsetX(),
                        current.castOffsetY(), current.castOffsetZ(), renderType,
                        current.centerMuzzle(), blank(trailBox.getValue()), blank(impactBox.getValue()),
                        blank(chargeBox.getValue()))
                : new XenoKiProfile(t.id(), current.nativeId(), parseRgb(interiorBox.getValue()),
                        parseRgb(exteriorBox.getValue()), current.colorOutline(), parseFloat(sizeBox.getValue()),
                        parseFloat(speedBox.getValue()), parseFloat(damageBox.getValue()),
                        parseInt(armorBox.getValue()), parseFloat(offsetXBox.getValue()),
                        parseFloat(offsetYBox.getValue()), parseFloat(offsetZBox.getValue()),
                        current.renderType(), centerMuzzle, current.fxTrail(), current.fxImpact(),
                        current.fxCharge());
        Runnable apply = () -> {
            XenoKiProfileCatalog.put(profile);
            XenoKiProfileCatalog.save();
            V3NativeKi.refreshOwned();
        };
        Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
        if (mc != null && mc.hasSingleplayerServer()) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    apply.run();
                    ServerPlayer sp = mc.player == null ? null
                            : server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp != null) {
                        sp.sendSystemMessage(Component.literal("Saved Ki profile: " + t.name()));
                    }
                });
                dirty = false;
                status = "Saved (SP): " + t.name();
                return;
            }
        }
        if (mc != null && mc.player != null && mc.player.hasPermissions(2)) {
            apply.run();
            dirty = false;
            status = "Saved: " + t.name();
            mc.player.connection.sendCommand("xenokiprofile reload");
        } else {
            status = MakerAccess.denyCosmetic().getString();
        }
    }

    private void reloadCatalog() {
        colorPicker.close();
        Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
        if (mc != null && mc.hasSingleplayerServer()) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    XenoKiProfileCatalog.load();
                    V3NativeKi.refreshOwned();
                });
                status = "Reloaded profiles (SP)";
                loadSelectedIntoFields();
                return;
            }
        }
        if (mc != null && mc.player != null && mc.player.hasPermissions(2)) {
            mc.player.connection.sendCommand("xenokiprofile reload");
            status = "Requested /xenokiprofile reload";
        } else {
            XenoKiProfileCatalog.load();
            status = "Local catalog reloaded";
            loadSelectedIntoFields();
        }
    }

    private static Integer parseRgb(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        try {
            return Integer.parseUnsignedInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String blank(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static boolean validFx(String raw) {
        String asset = blank(raw);
        return asset == null || asset.matches("ki_[a-z0-9_]+")
                && KiProfileMakerScreen.class.getResource(
                        "/assets/xenopixelsmod/effeks/ki/" + asset + ".efkefc") != null;
    }

    private static Float parseFloat(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            float v = Float.parseFloat(raw.trim());
            return Float.isFinite(v) ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double uiX = toUiX(mouseX);
        double uiY = toUiY(mouseY);
        if (colorPicker.isOpen() && colorPicker.mouseClicked(uiX, uiY, button)) {
            return true;
        }
        if (uiX >= listX && uiX <= listX + listW && uiY >= listY && uiY <= listY + listH) {
            int row = (int) ((uiY - listY) / ROW) + listScroll;
            if (row >= 0 && row < filtered.size()) {
                if (dirty && selected != row) {
                    status = "Unsaved changes — Save first or reselect after Reload";
                }
                selected = row;
                colorPicker.close();
                loadSelectedIntoFields();
                if (muzzleButton != null) {
                    muzzleButton.setMessage(Component.literal(centerMuzzle ? "Muzzle: ON" : "Muzzle: off"));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (colorPicker.isOpen() && colorPicker.mouseReleased(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (colorPicker.isOpen()) return true;
        double uiX = toUiX(mouseX);
        double uiY = toUiY(mouseY);
        if (uiX >= listX && uiX <= listX + listW && uiY >= listY && uiY <= listY + listH) {
            int visible = Math.max(1, listH / ROW);
            int maxScroll = Math.max(0, filtered.size() - visible);
            listScroll = (int) Math.max(0, Math.min(maxScroll, listScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (colorPicker.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (colorPicker.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);
        XenoAtlasSprites.Theme previous = XenoAtlasSprites.theme();
        XenoAtlasSprites.setTheme(CHROME);
        try {
            beginUiScale(graphics);
            AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                    .render(graphics);
            XenoAtlasSprites.blit(graphics, BANNER, CHROME, bannerX, bannerY);
            graphics.drawCenteredString(font, "KI PROFILES",
                    bannerX + bannerW / 2, bannerY + bannerH / 2 - 4, GOLD);

            graphics.drawString(font, "Techniques (" + filtered.size() + ")", listX, listY - 30, GOLD, false);
            graphics.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, 0x88000000);

            int visible = Math.max(1, listH / ROW);
            for (int i = 0; i < visible; i++) {
                int idx = i + listScroll;
                if (idx >= filtered.size()) break;
                V3TechniqueDefinition t = filtered.get(idx);
                int y = listY + i * ROW;
                int bg = idx == selected ? 0xAA4070A0 : ((i & 1) == 0 ? 0x44000000 : 0x33000000);
                graphics.fill(listX, y, listX + listW, y + ROW - 1, bg);
                String label = t.name();
                if (font.width(label) > listW - 8) {
                    label = font.plainSubstrByWidth(label, listW - 12) + "…";
                }
                graphics.drawString(font, label, listX + 4, y + 5, 0xFFE8E8E8, false);
            }

            graphics.drawString(font, effectsPage ? "Effects (bundled ki_ names)" : "Profile",
                    editX, editY - 14, GOLD, false);
            int ly = editY;
            String[] labels = effectsPage
                    ? new String[] {"Render", "Trail FX", "Impact FX", "Charge FX"}
                    : new String[] {"Interior", "Exterior", "Size", "Speed", "Damage ×", "Armor pen",
                            "Offset X", "Offset Y", "Offset Z"};
            for (String lab : labels) {
                graphics.drawString(font, lab, editX, ly + 3, 0xFFB0B0B0, false);
                ly += ROW;
            }

            // Footer separator
            graphics.fill(frameX + 12, contentBottom + 2, frameX + frameW - 12, contentBottom + 3, 0x44FFC14A);

            if (status != null && !status.isEmpty()) {
                graphics.drawString(font, status, listX, frameY + frameH - 12, 0xFF8AC7FF, false);
            }
            if (dirty) {
                graphics.drawString(font, "*", editX + editW - 10, editY - 14, 0xFFFF6666, false);
            }

            int uiMx = (int) toUiX(mouseX);
            int uiMy = (int) toUiY(mouseY);
            super.render(graphics, uiMx, uiMy, partialTick);
            colorPicker.render(graphics, uiMx, uiMy, partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
    }

    @Override
    public void onClose() {
        colorPicker.close();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
