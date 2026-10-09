package net.bullettrain.xenopixelsmod.client.npc.gui;

import net.bullettrain.xenopixelsmod.client.screen.UnblurredScreen;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.gui.XenoGuiNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authored widgets; only bounded user input is sent back to the owning session. */
@OnlyIn(Dist.CLIENT)
public final class XenoCustomGuiScreen extends UnblurredScreen {
    private static final UUID ROOT = new UUID(0, 0);
    private final UUID session;
    private final List<CompoundTag> nodes = new ArrayList<>();
    private final Map<UUID, EditBox> fields = new HashMap<>();
    private final Map<UUID, String> serverText = new HashMap<>();
    private int revision, canvasWidth, canvasHeight, left, top;
    private int reportedWidth = -1, reportedHeight = -1;
    private boolean pause, escape, closing, applying;
    private String background = "";
    private long sequence;
    private UUID hovered, focused;

    public XenoCustomGuiScreen(CompoundTag snapshot) {
        super(Component.literal(snapshot.getString("Name")));
        session = snapshot.getUUID("Session");
        read(snapshot);
    }

    public UUID session() { return session; }
    public void serverClosing() { closing = true; }

    private void read(CompoundTag snapshot) {
        revision = snapshot.getInt("Revision");
        canvasWidth = Math.max(1, Math.min(4096, snapshot.getInt("Width")));
        canvasHeight = Math.max(1, Math.min(4096, snapshot.getInt("Height")));
        pause = snapshot.getBoolean("Pause");
        escape = snapshot.getBoolean("Escape");
        background = snapshot.getString("Background");
        nodes.clear();
        var list = snapshot.getList("Nodes", Tag.TAG_COMPOUND);
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < Math.min(256, list.size()); i++) {
            CompoundTag node = list.getCompound(i);
            if (node.hasUUID("Key") && seen.add(node.getUUID("Key"))) nodes.add(node.copy());
        }
    }

    public void applySnapshot(CompoundTag snapshot) {
        if (!snapshot.hasUUID("Session") || !session.equals(snapshot.getUUID("Session"))
                || snapshot.getInt("Revision") < revision) return;
        read(snapshot);
        if (minecraft != null) rebuild();
    }

    @Override protected void init() {
        rebuild();
        if (reportedWidth != width || reportedHeight != height) {
            reportedWidth = width;
            reportedHeight = height;
            send(ROOT, "resize", "");
        }
    }

    private void rebuild() {
        applying = true;
        try {
            clearWidgets();
            left = (width - canvasWidth) / 2;
            top = (height - canvasHeight) / 2;
            Set<UUID> retained = new HashSet<>();
            for (CompoundTag node : nodes) {
                UUID key = node.getUUID("Key");
                int x = left + position(node, "X"), y = top + position(node, "Y");
                int w = size(node, "W"), h = size(node, "H");
                if (node.getString("Kind").equals("textfield")) {
                    EditBox box = fields.get(key);
                    boolean fresh = box == null;
                    if (fresh) {
                        box = new EditBox(font, x, y, w, h, Component.literal("Text input"));
                        box.setMaxLength(4096);
                        fields.put(key, box);
                    }
                    retained.add(key);
                    box.setX(x); box.setY(y); box.setWidth(w); box.setHeight(h);
                    box.setBordered(!node.getBoolean("HideBackground"));
                    box.setTextColor(node.getInt("Color"));
                    String incoming = bounded(node.getString("Text"), 4096);
                    if (fresh || !incoming.equals(serverText.get(key))) {
                        if (!incoming.equals(box.getValue())) box.setValue(incoming);
                    }
                    serverText.put(key, incoming);
                    box.active = node.getBoolean("Enabled");
                    box.visible = node.getBoolean("Visible");
                    int characterType = node.getInt("CharacterType");
                    box.setFilter(value -> validText(characterType, value));
                    box.setResponder(value -> { if (!applying) send(key, "text", value); });
                    addRenderableWidget(box);
                    if (fresh && node.getBoolean("Focused")) {
                        setFocused(box); box.setFocused(true); focused = key;
                    } else if (key.equals(focused) && box.visible && box.active) {
                        setFocused(box); box.setFocused(true);
                    }
                } else if (node.getString("Kind").equals("button")) {
                    Button button = Button.builder(Component.literal(bounded(node.getString("Text"), 1024)),
                            ignored -> send(key, "button", "")).bounds(x, y, w, h).build();
                    if (!node.getString("Texture").isEmpty()) {
                        button = new TextureButton(node, x, y, w, h, key);
                    }
                    button.active = node.getBoolean("Enabled");
                    button.visible = node.getBoolean("Visible");
                    addRenderableWidget(button);
                }
            }
            fields.keySet().retainAll(retained);
            serverText.keySet().retainAll(retained);
            if (focused != null && !retained.contains(focused)) focused = null;
        } finally { applying = false; }
    }

    @Override public void tick() {
        UUID now = null;
        for (var entry : fields.entrySet()) {
            EditBox box = entry.getValue();
            if (box.isFocused() && box.visible && box.active) { now = entry.getKey(); break; }
        }
        if (!java.util.Objects.equals(now, focused)) {
            if (focused != null) send(focused, "blur", "");
            focused = now;
            if (now != null) send(now, "focus", "");
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.enableScissor(Math.max(0, left), Math.max(0, top),
                Math.min(width, left + canvasWidth), Math.min(height, top + canvasHeight));
        try {
            graphics.fill(left, top, left + canvasWidth, top + canvasHeight, 0xDD15231B);
            drawTexture(graphics, background, left, top, canvasWidth, canvasHeight,
                    0, 0, canvasWidth, canvasHeight);
            for (CompoundTag node : nodes) {
                if (!node.getBoolean("Visible")) continue;
                int x = left + position(node, "X"), y = top + position(node, "Y");
                if (node.getString("Kind").equals("label")) {
                    graphics.drawString(font, bounded(node.getString("Text"), 1024), x, y, node.getInt("Color"), false);
                } else if (node.getString("Kind").equals("rect")) {
                    drawTexture(graphics, node.getString("Texture"), x, y, size(node, "W"), size(node, "H"),
                            position(node, "U"), position(node, "V"), size(node, "TexW"), size(node, "TexH"));
                }
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally { graphics.disableScissor(); }
        CompoundTag hit = hit(mouseX, mouseY);
        UUID next = hit == null ? null : hit.getUUID("Key");
        if (!java.util.Objects.equals(hovered, next)) {
            if (hovered != null) send(hovered, "exit", "");
            hovered = next;
            if (next != null) send(next, "hover", "");
        }
        if (hit != null) {
            var hover = hit.getList("Hover", Tag.TAG_STRING);
            List<net.minecraft.util.FormattedCharSequence> tooltip = new ArrayList<>();
            for (int i = 0; i < Math.min(16, hover.size()); i++)
                tooltip.add(Component.literal(bounded(hover.getString(i), 1024)).getVisualOrderText());
            if (!tooltip.isEmpty()) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private CompoundTag hit(double x, double y) {
        if (!insideCanvas(x, y)) return null;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            CompoundTag n = nodes.get(i);
            int nx = left + position(n, "X"), ny = top + position(n, "Y");
            if (n.getBoolean("Visible") && n.getBoolean("Enabled")
                    && x >= nx && x < nx + size(n, "W") && y >= ny && y < ny + size(n, "H")) return n;
        }
        return null;
    }

    private boolean insideCanvas(double x, double y) {
        return x >= Math.max(0, left) && x < Math.min(width, left + canvasWidth)
                && y >= Math.max(0, top) && y < Math.min(height, top + canvasHeight);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        return insideCanvas(x, y) && super.mouseClicked(x, y, button);
    }
    @Override public boolean shouldCloseOnEsc() { return escape; }
    @Override public boolean isPauseScreen() { return pause; }
    @Override public void onClose() { closeOnce(); super.onClose(); }
    @Override public void removed() { closeOnce(); }
    private void closeOnce() { if (!closing) { send(ROOT, "close", ""); closing = true; } }
    private void send(UUID component, String action, String value) {
        if (!closing) XenoGuiNetwork.sendInput(session, revision, ++sequence, component, action, value, width, height);
    }
    private static int size(CompoundTag node, String key) { return Math.max(1, Math.min(4096, node.getInt(key))); }
    private static int position(CompoundTag node, String key) { return Math.max(-8192, Math.min(8192, node.getInt(key))); }
    private static String bounded(String value, int max) { return value.length() > max ? value.substring(0, max) : value; }
    static boolean validText(int type, String value) {
        if (value.length() > 4096) return false;
        return switch (type) {
            case 1, 4 -> value.matches("[-+]?\\d*");
            case 2 -> value.matches("(?:0[xX])?[0-9a-fA-F]*");
            case 3 -> value.matches("[-+]?(?:\\d*(?:\\.\\d*)?)(?:[eE][-+]?\\d*)?");
            default -> true;
        };
    }
    private static void drawTexture(GuiGraphics graphics, String id, int x, int y, int w, int h,
                                    int u, int v, int tw, int th) {
        ResourceLocation texture = ResourceLocation.tryParse(id);
        if (texture != null) graphics.blit(texture, x, y, (float) u, (float) v, w, h, tw, th);
    }

    private final class TextureButton extends Button {
        private final CompoundTag node;
        TextureButton(CompoundTag node, int x, int y, int w, int h, UUID key) {
            super(x, y, w, h, Component.literal(bounded(node.getString("Text"), 1024)),
                    ignored -> send(key, "button", ""), DEFAULT_NARRATION);
            this.node = node;
        }
        @Override protected void renderWidget(GuiGraphics graphics, int x, int y, float partialTick) {
            drawTexture(graphics, node.getString("Texture"), getX(), getY(), getWidth(), getHeight(),
                    position(node, "U"), position(node, "V") + (isHovered() && active ? getHeight() : 0),
                    size(node, "TexW"), size(node, "TexH"));
            graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2,
                    getY() + (getHeight() - font.lineHeight) / 2, active ? node.getInt("Color") : 0xFFA0A0A0);
        }
    }
}
