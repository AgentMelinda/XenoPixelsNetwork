package net.bullettrain.xenopixelsmod.client.npc.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;

import java.util.ArrayList;
import java.util.List;

/** The active quest journal shown beside the inventory. */
public final class QuestLogPanel {

    static final int PANEL_WIDTH = 248;
    static final int PANEL_HEIGHT = 166;
    private static final int COLUMN_CATEGORIES = 66;
    private static final int COLUMN_QUESTS = 78;
    static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int LIST_TOP = PADDING + 14;
    private static final int VISIBLE_ROWS = (PANEL_HEIGHT - LIST_TOP - 6) / LINE_HEIGHT;

    private static String selectedCategory = "";
    private static String selectedQuest = "";
    private static int categoryOffset;
    private static int questOffset;
    private static int page;

    private QuestLogPanel() {
    }

    public static void render(GuiGraphics graphics, InventoryScreen inventory,
                              int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int left = QuestLogLayout.panelLeft(inventory.getGuiLeft());
        int top = inventory.getGuiTop();

        // Match the generated Xeno inventory/faction panel at native dimensions. Keeping this on
        // the inventory screen also avoids Minecraft's modal background blur entirely.
        XenoAtlasSprites.blit(graphics, "xeno_inventory_panel", left, top);
        graphics.drawString(minecraft.font, Component.literal("Quest Log"),
                left + PADDING, top + PADDING, 0xFF35D7FF, false);

        List<ClientQuests.Entry> quests = ClientQuests.all();
        if (!ClientQuests.isSynced()) {
            graphics.drawString(minecraft.font, Component.literal("Loading quests..."),
                    left + PADDING, top + LIST_TOP, 0xFF8FA3B4, false);
            return;
        }
        if (quests.isEmpty()) {
            selectedCategory = "";
            selectedQuest = "";
            categoryOffset = 0;
            questOffset = 0;
            graphics.drawString(minecraft.font, Component.literal("You have no active quests."),
                    left + PADDING, top + LIST_TOP, 0xFF8FA3B4, false);
            return;
        }

        List<String> categories = QuestLogLayout.categories(quests);
        if (!categories.contains(selectedCategory)) {
            selectedCategory = categories.get(0);
            selectedQuest = "";
            categoryOffset = 0;
            questOffset = 0;
            page = 0;
        }
        categoryOffset = QuestLogLayout.scrollOffset(categoryOffset, 0,
                categories.size(), VISIBLE_ROWS);
        List<String> visibleCategories = QuestLogLayout.visibleSlice(
                categories, categoryOffset, VISIBLE_ROWS);
        int categoryWidth = COLUMN_CATEGORIES - PADDING;
        int y = top + LIST_TOP;
        for (String category : visibleCategories) {
            boolean active = category.equals(selectedCategory);
            String label = QuestLogLayout.clip(category, categoryWidth, minecraft.font::width);
            graphics.drawString(minecraft.font, Component.literal(label),
                    left + PADDING, y, active ? 0xFFFFC928 : 0xFF8FA3B4, false);
            if (category.equals(selectedCategory) && rowHovered(
                    mouseX, mouseY, left + PADDING, y, categoryWidth, top)) {
                tooltip(graphics, minecraft, category, mouseX, mouseY);
            }
            y += LINE_HEIGHT;
        }

        List<ClientQuests.Entry> inCategory = QuestLogLayout.inCategory(quests, selectedCategory);
        if (inCategory.stream().noneMatch(quest -> quest.id().equals(selectedQuest))) {
            selectedQuest = inCategory.isEmpty() ? "" : inCategory.get(0).id();
            page = 0;
        }
        questOffset = QuestLogLayout.scrollOffset(questOffset, 0,
                inCategory.size(), VISIBLE_ROWS);
        List<ClientQuests.Entry> visibleQuests = QuestLogLayout.visibleSlice(
                inCategory, questOffset, VISIBLE_ROWS);
        int questWidth = COLUMN_QUESTS - 4;
        y = top + LIST_TOP;
        for (ClientQuests.Entry quest : visibleQuests) {
            boolean active = quest.id().equals(selectedQuest);
            String label = QuestLogLayout.clip(quest.title(), questWidth, minecraft.font::width);
            graphics.drawString(minecraft.font, Component.literal(label),
                    left + COLUMN_CATEGORIES, y, active ? 0xFFFFFFFF : 0xFF8FA3B4, false);
            if (rowHovered(mouseX, mouseY, left + COLUMN_CATEGORIES, y,
                    questWidth, top)) {
                tooltip(graphics, minecraft, quest.title(), mouseX, mouseY);
            }
            y += LINE_HEIGHT;
        }

        ClientQuests.Entry selected = null;
        for (ClientQuests.Entry quest : inCategory) {
            if (quest.id().equals(selectedQuest)) {
                selected = quest;
                break;
            }
        }
        if (selected == null) {
            return;
        }

        int detailX = left + COLUMN_CATEGORIES + COLUMN_QUESTS;
        int detailWidth = PANEL_WIDTH - COLUMN_CATEGORIES - COLUMN_QUESTS - PADDING * 2;
        int detailY = top + PADDING + 14;
        List<String> titleLines = wrap(minecraft, selected.title(), detailWidth);
        if (titleLines.size() > 6) {
            List<String> shortened = new ArrayList<>(titleLines.subList(0, 5));
            shortened.add(QuestLogLayout.clip(titleLines.get(5), detailWidth - 3,
                    minecraft.font::width) + "...");
            titleLines = shortened;
        }
        for (String line : titleLines) {
            graphics.drawString(minecraft.font, Component.literal(line),
                    detailX, detailY, 0xFFFFC928, false);
            detailY += LINE_HEIGHT;
        }
        graphics.drawString(minecraft.font, Component.literal("Objectives"),
                detailX, detailY, 0xFF35D7FF, false);
        detailY += LINE_HEIGHT;
        graphics.drawString(minecraft.font,
                Component.literal(QuestLogLayout.clip(
                        selected.progress() + " / " + selected.target(),
                        detailWidth, minecraft.font::width)),
                detailX, detailY, selected.ready() ? 0xFF7CE08A : 0xFFD8E4EE, false);
        detailY += LINE_HEIGHT + 2;

        List<String> wrapped = wrap(minecraft, selected.logText(), detailWidth);
        int footer = LINE_HEIGHT * 4;
        int bodyLines = Math.max(1, (PANEL_HEIGHT - (detailY - top) - footer) / LINE_HEIGHT);
        List<List<String>> pages = QuestLogLayout.paginate(wrapped, bodyLines);
        page = Math.max(0, Math.min(pages.size() - 1, page));
        for (String line : pages.get(page)) {
            graphics.drawString(minecraft.font, Component.literal(line),
                    detailX, detailY, 0xFFD8E4EE, false);
            detailY += LINE_HEIGHT;
        }
        if (pages.size() > 1) {
            graphics.drawString(minecraft.font,
                    Component.literal("Page " + (page + 1) + " / " + pages.size()),
                    detailX, top + PANEL_HEIGHT - LINE_HEIGHT * 4, 0xFF688195, false);
        }
        if (selected.ready() && !selected.completerNpc().isEmpty()) {
            List<String> completionHint = wrap(minecraft,
                    "Complete with " + selected.completerNpc(), detailWidth);
            int completeY = top + PANEL_HEIGHT - LINE_HEIGHT * 3;
            for (int i = 0; i < Math.min(2, completionHint.size()); i++) {
                String line = completionHint.get(i);
                graphics.drawString(minecraft.font, Component.literal(line),
                        detailX, completeY + i * LINE_HEIGHT, 0xFF7CE08A, false);
            }
        }
    }

    /** Handles a click inside the panel and shields the underlying screen from it. */
    public static boolean click(InventoryScreen inventory, double mouseX, double mouseY) {
        int left = QuestLogLayout.panelLeft(inventory.getGuiLeft());
        int top = inventory.getGuiTop();
        if (!inside(left, top, mouseX, mouseY)) {
            return false;
        }
        if (mouseY < top + LIST_TOP || mouseY >= top + LIST_TOP + VISIBLE_ROWS * LINE_HEIGHT) {
            if (mouseX >= left + COLUMN_CATEGORIES + COLUMN_QUESTS) {
                page++;
            }
            return true;
        }
        int row = (int) ((mouseY - (top + LIST_TOP)) / LINE_HEIGHT);
        List<ClientQuests.Entry> quests = ClientQuests.all();
        if (!ClientQuests.isSynced() || quests.isEmpty()) {
            return true;
        }
        List<String> categories = QuestLogLayout.categories(quests);
        if (mouseX >= left + PADDING && mouseX < left + COLUMN_CATEGORIES) {
            int index = categoryOffset + row;
            if (index < categories.size()) {
                selectedCategory = categories.get(index);
                selectedQuest = "";
                questOffset = 0;
                page = 0;
            }
            return true;
        }
        if (mouseX >= left + COLUMN_CATEGORIES
                && mouseX < left + COLUMN_CATEGORIES + COLUMN_QUESTS) {
            List<ClientQuests.Entry> inCategory =
                    QuestLogLayout.inCategory(quests, selectedCategory);
            int index = questOffset + row;
            if (index < inCategory.size()) {
                selectedQuest = inCategory.get(index).id();
                page = 0;
            }
            return true;
        }
        if (mouseX >= left + COLUMN_CATEGORIES + COLUMN_QUESTS) {
            page++;
        }
        return true;
    }

    /** Routes wheel input to a list viewport or to the selected journal pages. */
    public static boolean scroll(InventoryScreen inventory, double mouseX, double mouseY,
                                 double scrollY) {
        int left = QuestLogLayout.panelLeft(inventory.getGuiLeft());
        int top = inventory.getGuiTop();
        if (!inside(left, top, mouseX, mouseY)) return false;
        if (mouseY >= top + LIST_TOP && mouseY < top + LIST_TOP + VISIBLE_ROWS * LINE_HEIGHT) {
            int direction = scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0;
            List<ClientQuests.Entry> quests = ClientQuests.all();
            if (mouseX < left + COLUMN_CATEGORIES) {
                categoryOffset = QuestLogLayout.scrollOffset(categoryOffset, scrollY,
                        QuestLogLayout.categories(quests).size(), VISIBLE_ROWS);
            } else if (mouseX < left + COLUMN_CATEGORIES + COLUMN_QUESTS) {
                questOffset = QuestLogLayout.scrollOffset(questOffset, scrollY,
                        QuestLogLayout.inCategory(quests, selectedCategory).size(), VISIBLE_ROWS);
            } else {
                page = Math.max(0, page + direction);
            }
        } else if (mouseX >= left + COLUMN_CATEGORIES + COLUMN_QUESTS) {
            page = Math.max(0, page + (scrollY < 0 ? 1 : scrollY > 0 ? -1 : 0));
        }
        return true;
    }

    public static boolean contains(InventoryScreen inventory, double mouseX, double mouseY) {
        return inside(QuestLogLayout.panelLeft(inventory.getGuiLeft()), inventory.getGuiTop(),
                mouseX, mouseY);
    }

    private static boolean inside(int left, int top, double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + PANEL_WIDTH
                && mouseY >= top && mouseY < top + PANEL_HEIGHT;
    }

    private static boolean rowHovered(double mouseX, double mouseY, int x, int y,
                                      int width, int top) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < top + LIST_TOP + VISIBLE_ROWS * LINE_HEIGHT;
    }

    private static void tooltip(GuiGraphics graphics, Minecraft minecraft, String text,
                                int mouseX, int mouseY) {
        if (text != null && !text.isBlank()) {
            graphics.renderTooltip(minecraft.font, Component.literal(text), mouseX, mouseY);
        }
    }

    private static List<String> wrap(Minecraft minecraft, String text, int width) {
        List<String> lines = new ArrayList<>();
        for (FormattedCharSequence line
                : minecraft.font.split(Component.literal(text == null ? "" : text), width)) {
            StringBuilder value = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                value.appendCodePoint(codePoint);
                return true;
            });
            lines.add(value.toString());
        }
        return lines;
    }
}
