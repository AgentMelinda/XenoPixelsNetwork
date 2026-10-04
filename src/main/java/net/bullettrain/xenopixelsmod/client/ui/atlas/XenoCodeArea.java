package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A multi-line code editor drawn over a generated atlas panel: the counterpart of CustomNPCs'
 * {@code GuiTextArea} with code highlighting. Line-number gutter, caret and selection, undo/redo,
 * vertical scrollbar, JavaScript highlighting, bracket matching, auto-indent, Tab as spaces and the
 * clipboard. Read-only mode serves the Settings tab's console.
 *
 * <p>The text is one string; lines are recomputed on change. Positions are character offsets.
 */
public class XenoCodeArea extends AbstractWidget {
    private static final int LINE_H = 10;
    private static final int PAD = 6;
    private static final int SCROLLBAR_W = 5;
    private static final int MAX_UNDO = 200;
    private static final String INDENT = "    ";

    private static final int COLOR_TEXT = 0xFFE7EDF3;
    private static final int COLOR_GUTTER = 0xFF6F8AA3;
    private static final int COLOR_KEYWORD = 0xFFFFC14A;
    private static final int COLOR_STRING = 0xFF9BE58A;
    private static final int COLOR_COMMENT = 0xFF7A8B99;
    private static final int COLOR_NUMBER = 0xFF80D8FF;
    private static final int COLOR_SELECTION = 0x804A7FD0;
    private static final int COLOR_BRACKET = 0x60FFFFFF;
    private static final Set<String> KEYWORDS = Set.of(
            "var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
            "break", "continue", "switch", "case", "default", "new", "this", "typeof",
            "instanceof", "in", "of", "true", "false", "null", "undefined", "try", "catch",
            "finally", "throw", "delete", "void");

    private final String sprite;
    private final boolean readOnly;
    private final boolean gutter;
    private String text = "";
    private List<int[]> lines = List.of(new int[] {0, 0});
    private int cursor;
    private int anchor;
    private int scroll;
    private boolean draggingText;
    private boolean draggingBar;
    private long lastClickTime;
    private int blink;
    private final Deque<String[]> undo = new ArrayDeque<>();
    private final Deque<String[]> redo = new ArrayDeque<>();
    private Consumer<String> onChange = s -> {};

    public XenoCodeArea(int x, int y, String sprite, boolean readOnly, boolean gutter) {
        super(x, y, XenoAtlasSprites.get(sprite).width(), XenoAtlasSprites.get(sprite).height(),
                Component.empty());
        this.sprite = sprite;
        this.readOnly = readOnly;
        this.gutter = gutter;
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange == null ? s -> {} : onChange;
    }

    public String getText() {
        return text;
    }

    /** Replaces the text without an undo entry (loading, switching tabs). */
    public void setText(String value) {
        text = value == null ? "" : value.replace("\r\n", "\n").replace('\t', ' ');
        cursor = anchor = 0;
        scroll = 0;
        undo.clear();
        redo.clear();
        relayout();
    }

    /** Inserts at the caret, replacing any selection, as one undoable edit. */
    public void insert(String value) {
        if (readOnly || value == null) return;
        replaceSelection(value.replace("\r\n", "\n").replace("\t", INDENT));
    }

    // ---------------------------------------------------------------- geometry

    private Font font() {
        return Minecraft.getInstance().font;
    }

    private int gutterWidth() {
        return gutter ? font().width(String.valueOf(Math.max(1, lines.size()))) + 8 : 0;
    }

    private int textLeft() {
        return getX() + PAD + gutterWidth();
    }

    private int textTop() {
        return getY() + PAD;
    }

    private int textRight() {
        return getX() + width - PAD - SCROLLBAR_W - 1;
    }

    private int visibleLines() {
        return Math.max(1, (height - 2 * PAD) / LINE_H);
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - visibleLines());
    }

    private void relayout() {
        List<int[]> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                out.add(new int[] {start, i});
                start = i + 1;
            }
        }
        out.add(new int[] {start, text.length()});
        lines = out;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int lineOf(int offset) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (offset >= lines.get(i)[0]) return i;
        }
        return 0;
    }

    private String lineText(int line) {
        int[] l = lines.get(line);
        return text.substring(l[0], l[1]);
    }

    /** Character offset under a point, clamped to the text. */
    private int offsetAt(double mx, double my) {
        int line = scroll + (int) Math.floor((my - textTop()) / LINE_H);
        line = Math.max(0, Math.min(lines.size() - 1, line));
        String content = lineText(line);
        int x = (int) mx - textLeft();
        int col = 0;
        Font font = font();
        while (col < content.length()) {
            int mid = (font.width(content.substring(0, col)) + font.width(content.substring(0, col + 1))) / 2;
            if (x < mid) break;
            col++;
        }
        return lines.get(line)[0] + col;
    }

    private void ensureCursorVisible() {
        int line = lineOf(cursor);
        if (line < scroll) scroll = line;
        if (line >= scroll + visibleLines()) scroll = line - visibleLines() + 1;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    // ---------------------------------------------------------------- editing

    private boolean hasSelection() {
        return cursor != anchor;
    }

    private int selStart() {
        return Math.min(cursor, anchor);
    }

    private int selEnd() {
        return Math.max(cursor, anchor);
    }

    private String selectedText() {
        return text.substring(selStart(), selEnd());
    }

    private void pushUndo() {
        undo.push(new String[] {text, String.valueOf(cursor)});
        while (undo.size() > MAX_UNDO) undo.removeLast();
        redo.clear();
    }

    private void replaceSelection(String value) {
        pushUndo();
        int start = selStart();
        text = text.substring(0, start) + value + text.substring(selEnd());
        cursor = anchor = start + value.length();
        changed();
    }

    private void deleteRange(int from, int to) {
        if (from >= to) return;
        pushUndo();
        text = text.substring(0, from) + text.substring(to);
        cursor = anchor = from;
        changed();
    }

    private void changed() {
        relayout();
        ensureCursorVisible();
        onChange.accept(text);
    }

    private void undoOrRedo(Deque<String[]> from, Deque<String[]> to) {
        if (from.isEmpty()) return;
        to.push(new String[] {text, String.valueOf(cursor)});
        String[] state = from.pop();
        text = state[0];
        cursor = anchor = Math.min(text.length(), Integer.parseInt(state[1]));
        changed();
    }

    private void moveCursor(int to, boolean select) {
        cursor = Math.max(0, Math.min(text.length(), to));
        if (!select) anchor = cursor;
        blink = 0;
        ensureCursorVisible();
    }

    private int lineColumnMove(int deltaLines) {
        int line = lineOf(cursor);
        int column = font().width(text.substring(lines.get(line)[0], cursor));
        int target = Math.max(0, Math.min(lines.size() - 1, line + deltaLines));
        String content = lineText(target);
        int col = 0;
        while (col < content.length() && font().width(content.substring(0, col + 1)) <= column) col++;
        return lines.get(target)[0] + col;
    }

    private int wordBoundary(int from, int direction) {
        int i = from;
        if (direction < 0) {
            while (i > 0 && !Character.isLetterOrDigit(text.charAt(i - 1))) i--;
            while (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) i--;
        } else {
            while (i < text.length() && !Character.isLetterOrDigit(text.charAt(i))) i++;
            while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) i++;
        }
        return i;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isFocused()) return false;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = Screen.hasControlDown();
        Minecraft mc = Minecraft.getInstance();
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> { anchor = 0; cursor = text.length(); return true; }
                case GLFW.GLFW_KEY_C -> {
                    if (hasSelection()) mc.keyboardHandler.setClipboard(selectedText());
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (hasSelection() && !readOnly) {
                        mc.keyboardHandler.setClipboard(selectedText());
                        replaceSelection("");
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> { insert(mc.keyboardHandler.getClipboard()); return true; }
                case GLFW.GLFW_KEY_Z -> {
                    if (!readOnly) undoOrRedo(shift ? redo : undo, shift ? undo : redo);
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> { if (!readOnly) undoOrRedo(redo, undo); return true; }
                case GLFW.GLFW_KEY_LEFT -> { moveCursor(wordBoundary(cursor, -1), shift); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { moveCursor(wordBoundary(cursor, 1), shift); return true; }
                case GLFW.GLFW_KEY_HOME -> { moveCursor(0, shift); return true; }
                case GLFW.GLFW_KEY_END -> { moveCursor(text.length(), shift); return true; }
                default -> { }
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                moveCursor(hasSelection() && !shift ? selStart() : cursor - 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                moveCursor(hasSelection() && !shift ? selEnd() : cursor + 1, shift);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> { moveCursor(lineColumnMove(-1), shift); return true; }
            case GLFW.GLFW_KEY_DOWN -> { moveCursor(lineColumnMove(1), shift); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { moveCursor(lineColumnMove(-visibleLines()), shift); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { moveCursor(lineColumnMove(visibleLines()), shift); return true; }
            case GLFW.GLFW_KEY_HOME -> { moveCursor(lines.get(lineOf(cursor))[0], shift); return true; }
            case GLFW.GLFW_KEY_END -> { moveCursor(lines.get(lineOf(cursor))[1], shift); return true; }
            default -> { }
        }
        if (readOnly) return false;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) replaceSelection("");
                else deleteRange(Math.max(0, cursor - 1), cursor);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) replaceSelection("");
                else deleteRange(cursor, Math.min(text.length(), cursor + 1));
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                replaceSelection("\n" + autoIndent());
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                replaceSelection(INDENT);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** The current line's leading spaces, plus one level after an opening brace. */
    private String autoIndent() {
        String before = text.substring(lines.get(lineOf(cursor))[0], cursor);
        int n = 0;
        while (n < before.length() && before.charAt(n) == ' ') n++;
        String indent = before.substring(0, n);
        return before.stripTrailing().endsWith("{") ? indent + INDENT : indent;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!isFocused() || readOnly || c < 32 || c == 127) return false;
        if (c == '}') {
            // Closing brace on a whitespace-only line dedents one level.
            int start = lines.get(lineOf(cursor))[0];
            String before = text.substring(start, cursor);
            if (!hasSelection() && before.endsWith(INDENT) && before.isBlank()) {
                deleteRange(cursor - INDENT.length(), cursor);
            }
        }
        replaceSelection(String.valueOf(c));
        return true;
    }

    // ---------------------------------------------------------------- mouse

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMouseOver(mx, my) || button != 0) return false;
        setFocused(true);
        if (mx >= getX() + width - PAD - SCROLLBAR_W - 1) {
            draggingBar = true;
            scrollToMouse(my);
            return true;
        }
        long now = System.currentTimeMillis();
        int at = offsetAt(mx, my);
        if (now - lastClickTime < 250 && !Screen.hasShiftDown()) {
            anchor = wordBoundary(Math.min(text.length(), at + 1), -1);
            cursor = wordBoundary(at, 1);
        } else {
            moveCursor(at, Screen.hasShiftDown());
        }
        lastClickTime = now;
        draggingText = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBar) {
            scrollToMouse(my);
            return true;
        }
        if (draggingText) {
            cursor = offsetAt(mx, my);
            ensureCursorVisible();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingText = false;
        draggingBar = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!isMouseOver(mx, my)) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (sy > 0 ? -3 : 3)));
        return true;
    }

    private void scrollToMouse(double my) {
        double frac = (my - textTop()) / Math.max(1.0, height - 2.0 * PAD);
        scroll = Math.max(0, Math.min(maxScroll(), (int) Math.round(frac * maxScroll())));
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        XenoAtlasSprites.blit(g, sprite, getX(), getY());
        Font font = font();
        blink++;
        int visible = visibleLines();
        int left = textLeft();
        int right = textRight();
        scissor(g, getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2);
        int[] match = hasSelection() ? null : bracketMatch();
        for (int row = 0; row < visible && scroll + row < lines.size(); row++) {
            int line = scroll + row;
            int y = textTop() + row * LINE_H;
            int[] span = lines.get(line);
            if (gutter) {
                String number = String.valueOf(line + 1);
                g.drawString(font, number, getX() + PAD + gutterWidth() - 6 - font.width(number), y,
                        COLOR_GUTTER, false);
            }
            // selection
            if (hasSelection() && selEnd() >= span[0] && selStart() <= span[1]) {
                int a = Math.max(selStart(), span[0]) - span[0];
                int b = Math.min(selEnd(), span[1]) - span[0];
                String content = lineText(line);
                int x0 = left + font.width(content.substring(0, a));
                int x1 = left + font.width(content.substring(0, b)) + (selEnd() > span[1] ? 3 : 0);
                g.fill(x0, y - 1, Math.min(right, x1), y + LINE_H - 1, COLOR_SELECTION);
            }
            if (match != null) {
                for (int offset : match) {
                    if (offset >= span[0] && offset < span[1]) {
                        String content = lineText(line);
                        int col = offset - span[0];
                        int x0 = left + font.width(content.substring(0, col));
                        g.fill(x0, y - 1, x0 + font.width(content.substring(col, col + 1)),
                                y + LINE_H - 1, COLOR_BRACKET);
                    }
                }
            }
            drawHighlighted(g, font, lineText(line), left, y);
        }
        if (isFocused() && !readOnly && (blink / 10) % 2 == 0) {
            int line = lineOf(cursor);
            if (line >= scroll && line < scroll + visible) {
                int y = textTop() + (line - scroll) * LINE_H;
                int x = left + font.width(text.substring(lines.get(line)[0], cursor));
                g.fill(x, y - 1, x + 1, y + LINE_H - 1, 0xFFFFFFFF);
            }
        }
        g.disableScissor();
        // scrollbar
        if (maxScroll() > 0) {
            int trackX = getX() + width - PAD - SCROLLBAR_W;
            int trackTop = getY() + PAD;
            int trackH = height - 2 * PAD;
            int thumbH = Math.max(10, trackH * visible / lines.size());
            int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll();
            g.fill(trackX, trackTop, trackX + SCROLLBAR_W, trackTop + trackH, 0x40000000);
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, 0xC080B0E0);
        }
    }

    /**
     * {@code GuiGraphics.enableScissor} takes GUI coordinates and ignores the pose, but this widget
     * draws inside a scaled UI pose ({@code ScaledScreen.beginUiScale}). The corners are pushed
     * through the current pose first so the clip lands on the panel at any UI scale.
     */
    public static void scissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
        org.joml.Matrix4f pose = g.pose().last().pose();
        org.joml.Vector3f a = pose.transformPosition(x0, y0, 0, new org.joml.Vector3f());
        org.joml.Vector3f b = pose.transformPosition(x1, y1, 0, new org.joml.Vector3f());
        g.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
    }

    /** Offsets of the bracket at/before the caret and its partner, or null. */
    private int[] bracketMatch() {
        for (int at : new int[] {cursor - 1, cursor}) {
            if (at < 0 || at >= text.length()) continue;
            char c = text.charAt(at);
            int open = "([{".indexOf(c);
            int close = ")]}".indexOf(c);
            if (open < 0 && close < 0) continue;
            char o = open >= 0 ? c : "([{".charAt(close);
            char cl = open >= 0 ? ")]}".charAt(open) : c;
            int dir = open >= 0 ? 1 : -1;
            int depth = 0;
            for (int i = at; i >= 0 && i < text.length(); i += dir) {
                char ch = text.charAt(i);
                if (ch == o) depth += dir;
                else if (ch == cl) depth -= dir;
                if (depth == 0) return new int[] {at, i};
            }
        }
        return null;
    }

    /** Draws one line with JavaScript colouring: comments, strings, numbers, keywords. */
    private void drawHighlighted(GuiGraphics g, Font font, String line, int x, int y) {
        int i = 0;
        int n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            int end;
            int color;
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
                end = n;
                color = COLOR_COMMENT;
            } else if (c == '"' || c == '\'' || c == '`') {
                end = i + 1;
                while (end < n && line.charAt(end) != c) {
                    if (line.charAt(end) == '\\') end++;
                    end++;
                }
                end = Math.min(n, end + 1);
                color = COLOR_STRING;
            } else if (Character.isDigit(c)) {
                end = i + 1;
                while (end < n && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '.')) end++;
                color = COLOR_NUMBER;
            } else if (Character.isJavaIdentifierStart(c)) {
                end = i + 1;
                while (end < n && Character.isJavaIdentifierPart(line.charAt(end))) end++;
                color = KEYWORDS.contains(line.substring(i, end)) ? COLOR_KEYWORD : COLOR_TEXT;
            } else {
                end = i + 1;
                color = COLOR_TEXT;
            }
            String piece = line.substring(i, end);
            g.drawString(font, piece, x, y, color, false);
            x += font.width(piece);
            i = end;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal("Script editor"));
    }
}
