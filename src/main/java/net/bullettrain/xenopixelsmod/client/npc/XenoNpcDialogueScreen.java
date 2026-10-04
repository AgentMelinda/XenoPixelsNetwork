package net.bullettrain.xenopixelsmod.client.npc;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasTextFit;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.client.npc.speech.BubbleText;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcDialoguePacket;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation screen an NPC opens when a player right-clicks it.
 *
 * <h2>Who decides what</h2>
 * The screen walks the dialogue tree locally - moving between text nodes is presentation, and
 * round-tripping it would add a packet per click for no benefit. Anything with a consequence is
 * different: a quest option and a command option are <em>reported</em> to the server, which
 * re-reads the dialogue itself and decides. A client saying "I picked the option that runs a
 * command" is a request, never an instruction.
 *
 * <p>Only actions without a runtime owner are disabled: quest offers use the server quest engine,
 * while role options still need the economy-role system.
 */
public final class XenoNpcDialogueScreen extends ScaledScreen {

    /** Drawn at the vanilla GUI Scale; see {@link NpcGuiScale}. */
    @Override
    protected float computeDynamicScale(float available) {
        return NpcGuiScale.dynamicScale(super.computeDynamicScale(available));
    }
    private static final String FRAME = "xeno_editor_panel";
    private static final String OPTION = "mynpcs_side_button";
    private static final String PRIMARY = "pill_button";

    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF8AA4B8;

    private static final int MAX_VISIBLE_OPTIONS = 5;

    private final int entityId;
    private final XenoDialogue dialogue;
    private final String npcName;

    private String nodeId;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private List<FormattedCharSequence> wrappedText = List.of();

    public XenoNpcDialogueScreen(int entityId, XenoDialogue dialogue, String npcName) {
        super(Component.literal("Dialogue"));
        this.entityId = entityId;
        this.dialogue = dialogue;
        this.npcName = npcName == null ? "" : npcName;
        this.nodeId = dialogue == null ? "" : dialogue.start();
    }

    @Override
    protected void init() {
        super.init();
        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, getUiWidth() - 8, getUiHeight() - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = (getUiWidth() - frameW) / 2;
        frameY = (getUiHeight() - frameH) / 2;
        rebuild();
    }

    private XenoDialogue.Node node() {
        return dialogue == null ? null : dialogue.nodes().get(nodeId);
    }

    private void rebuild() {
        clearWidgets();

        XenoDialogue.Node node = node();
        if (node == null) {
            wrappedText = List.of(Component.literal("...").getVisualOrderText());
            addRenderableWidget(new AtlasButton(frameX + 20, frameY + frameH - 34,
                    Component.literal("Close"), PRIMARY, b -> onClose()));
            return;
        }

        String resolved = XenoDialogueText.resolve(node.text(),
                minecraft != null && minecraft.player != null
                        ? minecraft.player.getName().getString() : "", npcName);
        wrappedText = font.split(BubbleText.styled(resolved), frameW - 48);

        int optionW = XenoAtlasSprites.get(OPTION).width();
        int optionH = XenoAtlasSprites.get(OPTION).height();
        int optionTextWidth = optionW - 8;
        java.util.List<AtlasTextFit.Measure> optionMeasures = node.options().stream()
                .limit(MAX_VISIBLE_OPTIONS)
                .map(option -> new AtlasTextFit.Measure(font.width(optionText(option)), optionTextWidth))
                .toList();
        float optionScale = AtlasTextFit.groupScale(optionMeasures, AtlasTextFit.MIN_SCALE);
        int x = frameX + (frameW - optionW) / 2;
        int y = frameY + 96;

        int shown = 0;
        for (XenoDialogue.Option option : node.options()) {
            if (shown >= MAX_VISIBLE_OPTIONS) {
                break;
            }
            AtlasButton button = new AtlasButton(x, y, optionText(option), OPTION,
                    b -> choose(option), optionScale);
            button.active = isUsable(option);
            addRenderableWidget(button);
            y += optionH + 2;
            shown++;
        }

        addRenderableWidget(new AtlasButton(frameX + 20, frameY + frameH - 34,
                Component.literal("Close"), PRIMARY, b -> onClose()));
    }

    private Component optionText(XenoDialogue.Option option) {
        return BubbleText.styled(XenoDialogueText.resolve(option.text(),
                minecraft != null && minecraft.player != null
                        ? minecraft.player.getName().getString() : "", npcName));
    }

    /** Whether choosing this option can currently do anything. */
    private static boolean isUsable(XenoDialogue.Option option) {
        return switch (option.type()) {
            // QUEST is live: the option hands its id to ParallelQuests, the same path
            // /xenoquest start takes. It needs an id to offer, though - an option naming no quest
            // could only report that it names no quest, which is not worth a click.
            case QUEST -> option.quest() != null && !option.quest().isBlank();
            // ROLE still needs a system that does not exist: there are no economy roles yet.
            // Disabled rather than silently inert, so the screen never pretends it works.
            case ROLE -> false;
            default -> true;
        };
    }

    private int serverOptionIndex(XenoDialogue.Option option) {
        if (option == null || node() == null) {
            return -1;
        }
        int sourceIndex = option.sourceIndex();
        return sourceIndex >= 0 ? sourceIndex : node().options().indexOf(option);
    }

    private void choose(XenoDialogue.Option option) {
        switch (option.type()) {
            case TEXT -> {
                String target = option.target();
                if (dialogue != null && dialogue.nodes().containsKey(target)) {
                    nodeId = target;
                    rebuild();
                } else {
                    // A dangling target would otherwise leave the player staring at the same node
                    // with no idea the click registered.
                    onClose();
                }
            }
            case QUEST -> {
                // Reported, not resolved here. The server re-reads the dialogue, checks the option
                // really exists at that index, and applies its own rules about already being on a
                // quest - the client is not trusted with any of that.
                ModNetwork.sendToServer(new XenoNpcDialoguePacket(entityId, nodeId,
                        serverOptionIndex(option)));
                onClose();
            }
            case COMMAND -> {
                // Reported, not executed. The server re-reads the dialogue and decides whether the
                // option exists and whether commands are enabled at all.
                ModNetwork.sendToServer(new XenoNpcDialoguePacket(entityId, nodeId,
                        serverOptionIndex(option)));
                onClose();
            }
            default -> onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);

        beginUiScale(graphics);
        AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                .render(graphics);

        graphics.drawString(font, npcName.isEmpty() ? "???" : npcName,
                frameX + 24, frameY + 16, GOLD, false);

        int y = frameY + 40;
        for (FormattedCharSequence line : wrappedText) {
            graphics.drawString(font, line, frameX + 24, y, LIGHT, false);
            y += 12;
        }

        XenoDialogue.Node node = node();
        if (node != null && node.options().size() > MAX_VISIBLE_OPTIONS) {
            graphics.drawString(font,
                    "+" + (node.options().size() - MAX_VISIBLE_OPTIONS) + " more options",
                    frameX + 24, frameY + frameH - 48, MUTED, false);
        }

        super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
        endUiScale(graphics);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Opens the screen for a dialogue the client has already resolved. */
    public static void open(int entityId, XenoDialogue dialogue, String npcName) {
        Minecraft.getInstance().setScreen(
                new XenoNpcDialogueScreen(entityId, dialogue, npcName));
    }
}
