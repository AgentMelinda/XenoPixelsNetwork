package net.bullettrain.xenopixelsmod.client.npc;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTextFit;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Browse the effective resource stack instead of guessing a mod's texture path. */
public final class XenoNpcTexturePickerScreen extends ScaledScreen {

    /** Drawn at the vanilla GUI Scale; see {@link NpcGuiScale}. */
    @Override
    protected float computeDynamicScale(float available) {
        return NpcGuiScale.dynamicScale(super.computeDynamicScale(available));
    }
    private static final String FRAME = "xeno_editor_panel";
    private static final int WHITE = 0xFFE7EDF3;
    private static final int MUTED = 0xFF8AA4B8;
    private static final int CYAN = 0xFF45D8FF;
    private static final int GOLD = 0xFFFFC14A;
    private static final List<String> CATEGORIES = List.of("All", "Entity", "Block", "Item", "GUI", "Other");
    private static final int ROW_H = 17;

    private final Screen parent;
    private final Consumer<String> onSelected;
    private final List<ResourceLocation> allTextures;
    private final List<String> namespaces;
    private List<ResourceLocation> visible = List.of();
    private ResourceLocation selected;
    private EditBox search;
    private int namespaceIndex;
    private int categoryIndex;
    private int scroll;
    private int x;
    private int y;
    private int w;
    private int h;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int previewX;
    private int previewW;
    private int previewImageW = 64;
    private int previewImageH = 64;

    public XenoNpcTexturePickerScreen(Screen parent, String initial, Consumer<String> onSelected) {
        super(Component.literal("Select NPC Texture"));
        this.parent = parent;
        this.onSelected = onSelected;
        Minecraft mc = Minecraft.getInstance();
        this.allTextures = NpcTextureCatalog.loadedTextures(mc.getResourceManager());
        List<String> found = new ArrayList<>();
        found.add("All namespaces");
        allTextures.stream().map(ResourceLocation::getNamespace).distinct().sorted().forEach(found::add);
        this.namespaces = List.copyOf(found);
        this.selected = ResourceLocation.tryParse(initial == null ? "" : initial.trim());
        if (selected != null) {
            int index = namespaces.indexOf(selected.getNamespace());
            if (index > 0) namespaceIndex = index;
            readPreviewSize();
        }
        filter();
    }

    @Override
    protected void init() {
        super.init();
        int[] size = XenoAtlasSprites.fittedSize(FRAME, getUiWidth() - 8, getUiHeight() - 8);
        w = size[0];
        h = size[1];
        x = (getUiWidth() - w) / 2;
        y = (getUiHeight() - h) / 2;
        listX = x + 16;
        listY = y + 78;
        listW = Math.max(105, (w * 2) / 3 - 24);
        listH = Math.max(ROW_H, h - 119);
        previewX = listX + listW + 12;
        previewW = Math.max(48, x + w - previewX - 16);

        search = new EditBox(font, listX, y + 29, Math.max(75, listW), 18,
                Component.literal("Search textures"));
        search.setMaxLength(128);
        search.setHint(Component.literal("Search namespace or path"));
        search.setResponder(ignored -> filter());
        addRenderableWidget(search);

        addRenderableWidget(new AtlasCycle(listX, y + 52, Component.literal("Namespace"),
                namespaces, namespaceIndex, (int index) -> {
                    namespaceIndex = index;
                    filter();
                }));
        addRenderableWidget(new AtlasCycle(listX + 120, y + 52, Component.literal("Category"),
                CATEGORIES, categoryIndex, (int index) -> {
                    categoryIndex = index;
                    filter();
                }));

        int footerY = y + h - 31;
        addRenderableWidget(new AtlasButton(x + 17, footerY, Component.literal("Select"),
                "pill_button", ignored -> select()));
        addRenderableWidget(new AtlasButton(x + 112, footerY, Component.literal("Clear"),
                "pill_button", ignored -> finish("")));
        addRenderableWidget(new AtlasButton(x + 207, footerY, Component.literal("Cancel"),
                "pill_button", ignored -> onClose()));
    }

    private void filter() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        String namespace = namespaceIndex == 0 ? "" : namespaces.get(namespaceIndex);
        String category = CATEGORIES.get(categoryIndex);
        visible = allTextures.stream()
                .filter(id -> namespace.isEmpty() || namespace.equals(id.getNamespace()))
                .filter(id -> category.equals("All") || category.equals(NpcTextureCatalog.category(id)))
                .filter(id -> query.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        scroll = 0;
    }

    private void select() {
        if (selected != null && allTextures.contains(selected)) {
            finish(selected.toString());
        }
    }

    private void finish(String id) {
        onSelected.accept(id);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        beginUiScale(graphics);
        AtlasPanel.fittedInto(FRAME, x, y, getUiWidth() - 8, getUiHeight() - 8).render(graphics);
        graphics.drawString(font, "Select NPC Texture", x + 17, y + 10, GOLD, false);
        graphics.drawString(font, visible.size() + " textures", x + w - 110, y + 10, MUTED, false);
        graphics.fill(listX, listY, listX + listW, listY + listH, 0x8005101B);
        graphics.fill(previewX - 5, listY, x + w - 15, listY + listH, 0x8005101B);

        int count = Math.max(1, listH / ROW_H);
        int end = Math.min(visible.size(), scroll + count);
        for (int index = scroll; index < end; index++) {
            ResourceLocation id = visible.get(index);
            int rowY = listY + (index - scroll) * ROW_H;
            if (id.equals(selected)) {
                graphics.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_H, 0xAA174868);
            }
            String label = AtlasTextFit.fit(id.toString(), listW - 9, 1.0f, font::width);
            graphics.drawString(font, label, listX + 4, rowY + 4,
                    id.equals(selected) ? CYAN : WHITE, false);
        }
        if (visible.isEmpty()) {
            graphics.drawString(font, "No matching textures", listX + 6, listY + 7, MUTED, false);
        }
        drawPreview(graphics);
        super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        endUiScale(graphics);
    }

    private void drawPreview(GuiGraphics graphics) {
        graphics.drawString(font, "Preview", previewX, listY + 5, CYAN, false);
        if (selected == null) return;
        int max = Math.min(96, Math.max(16, previewW - 12));
        float scale = Math.min(max / (float) previewImageW, max / (float) previewImageH);
        int drawW = Math.max(1, Math.round(previewImageW * scale));
        int drawH = Math.max(1, Math.round(previewImageH * scale));
        graphics.blit(selected, previewX + (previewW - drawW) / 2, listY + 21,
                drawW, drawH, 0f, 0f, previewImageW, previewImageH,
                previewImageW, previewImageH);
        String name = AtlasTextFit.fit(selected.getNamespace(), previewW - 8, 1.0f, font::width);
        graphics.drawString(font, name, previewX + 4, listY + 25 + max, GOLD, false);
        graphics.drawString(font, previewImageW + " x " + previewImageH,
                previewX + 4, listY + 38 + max, MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int uiX = (int) toUiX(mouseX);
        int uiY = (int) toUiY(mouseY);
        if (button == 0 && uiX >= listX && uiX < listX + listW
                && uiY >= listY && uiY < listY + listH) {
            int index = scroll + (uiY - listY) / ROW_H;
            if (index < visible.size()) {
                selected = visible.get(index);
                readPreviewSize();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (toUiX(mouseX) >= listX && toUiX(mouseX) < listX + listW
                && toUiY(mouseY) >= listY && toUiY(mouseY) < listY + listH) {
            int count = Math.max(1, listH / ROW_H);
            scroll = Math.max(0, Math.min(Math.max(0, visible.size() - count),
                    scroll + (scrollY < 0 ? 3 : -3)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void readPreviewSize() {
        previewImageW = 64;
        previewImageH = 64;
        if (minecraft == null || selected == null) return;
        minecraft.getResourceManager().getResource(selected).ifPresent(resource -> {
            try (DataInputStream in = new DataInputStream(resource.open())) {
                if (in.readLong() != 0x89504E470D0A1A0AL || in.readInt() != 13
                        || in.readInt() != 0x49484452) return;
                int imageW = in.readInt();
                int imageH = in.readInt();
                if (imageW > 0 && imageW <= 8192 && imageH > 0 && imageH <= 8192) {
                    previewImageW = imageW;
                    previewImageH = imageH;
                }
            } catch (Exception ignored) {
                // A malformed texture still remains in the list; the game renders its fallback.
            }
        });
    }
}
