package net.bullettrain.xenopixelsmod.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.XenoCooldownHudOverlay;
import net.bullettrain.xenopixelsmod.client.XenoHudSnapshot;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.SparkingChargeClientState;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.hud.XenoBt3HudAtlas.Sprite;
import net.bullettrain.xenopixelsmod.client.pad.XenoPadInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;

/**
 * The Budokai Tenkaichi renderer, selected with {@code /xenohud renderer bt3}.
 *
 * <p>Draws the clean UI bundle's own art — nameplate, portrait ring, the three bars, the four
 * ability shells, the prompt plate and arrow — packed into {@link XenoBt3HudAtlas}, with live data
 * layered into it. Nothing is re-derived here: health, ki, stamina, form and transform progress
 * come from {@link XenoHudSnapshot}; the Max Power staging comes from {@link SparkingKiBar} and so
 * from the server's own charge packet; the combat rail is {@link XenoCooldownHudOverlay}'s chips;
 * the follow-up prompt is the same window {@code Bt3CombatClient} checks before it sends the rush.
 * This view owns placement and motion, and nothing else.
 *
 * <p>Bars are filled by clipping the lit art rather than by tinting rectangles, which is what keeps
 * the gloss, the slanted end caps and the segment shapes the bundle drew. The unlit part of each
 * bar is the same sprite drawn dark, so an empty bar is still recognisably the bar it belongs to.
 *
 * <p>A separate class rather than a mode inside {@link XenoModernHudView}: the two renderers share
 * no art, no layout and no fill technique, and the branches needed to fold them together would
 * outnumber the lines they saved.
 */
public final class XenoBt3HudView {

    /** The ki bar's resting colour, taken from the art so a plain bar is untinted. */
    private static final int KI_NORMAL = 0xFFFFFFFF;
    /** Unlit bar: the lit sprite, dimmed. Keeps the bar's shape without competing with the fill. */
    private static final int SHELL = 0x99303A4A;
    /** The health a hit just took, still shown behind the bar. */
    private static final int HP_GHOST = 0xCCB03A3A;
    /** Below this fraction the health bar pulses. */
    private static final float HP_CRITICAL = 0.25f;
    /** A rail slot whose move is off, in config or on the server. */
    private static final int SLOT_DISABLED = 0x66FFFFFF;
    /** Rail label colours. */
    private static final int SLOT_LABEL = 0xFFE3E8F0;
    private static final int SLOT_LABEL_DIM = 0xFF7A8494;

    private static final Sprite[] SHELLS = {
            XenoBt3HudAtlas.TECHNIQUE_PURPLE,
            XenoBt3HudAtlas.TECHNIQUE_BLUE,
            XenoBt3HudAtlas.TECHNIQUE_GREEN,
            XenoBt3HudAtlas.TECHNIQUE_ORANGE,
    };

    private final Bt3HudState motion = new Bt3HudState();

    private XenoHudSnapshot snapshot;
    private int boundsX;
    private int boundsY;
    private float scale = 1f;
    private boolean editorMode;
    private long lastNanos;
    private int lastPlayerId = Integer.MIN_VALUE;

    /** Formatted readouts, rebuilt only when the tick-cached snapshot changes. */
    private XenoHudSnapshot formatted;
    private String hpText = "";
    private String kiText = "";
    private String stmText = "";

    public void setSnapshot(XenoHudSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void setBounds(int x, int y, float scale) {
        this.boundsX = x;
        this.boundsY = y;
        this.scale = scale <= 0f ? 1f : scale;
    }

    public void setEditorMode(boolean editorMode) {
        this.editorMode = editorMode;
    }

    public void render(GuiGraphics graphics) {
        XenoHudSnapshot snap = snapshot;
        if (snap == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        boolean reduced = XenoClientConfig.hudReducedMotion;
        List<XenoCooldownHudOverlay.Chip> chips = XenoCooldownHudOverlay.chipsForUnified(editorMode);
        boolean promptLive = editorMode || Bt3CombatClient.cinematicFollowupLive();

        advanceMotion(mc, snap, promptLive, reduced);

        boolean prompt = motion.prompt01() > 0.01f;
        XenoHudConfig.reportUnifiedSize(XenoBt3HudLayout.PANEL_WIDTH,
                XenoBt3HudLayout.panelHeight(chips.size(), prompt));

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(boundsX, boundsY, 0);
        pose.scale(scale, scale, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        drawNameplate(graphics, font, snap);
        drawHealth(graphics, reduced);
        drawKi(graphics, snap);
        drawStamina(graphics);
        drawPortrait(graphics, mc, snap);
        drawBarText(graphics, font, snap);
        drawForm(graphics, font, snap);
        if (!chips.isEmpty()) drawRail(graphics, font, chips);
        if (prompt) drawPrompt(graphics, font, chips.size());

        if (editorMode) {
            graphics.renderOutline(0, 0, XenoBt3HudLayout.PANEL_WIDTH,
                    XenoBt3HudLayout.panelHeight(chips.size(), prompt), 0xFF42A5F5);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /**
     * Step the eased values.
     *
     * <p>Keyed off the player's entity id so a respawn or a dimension change restarts from the new
     * player's real values rather than sweeping down from the old one's.
     */
    private void advanceMotion(Minecraft mc, XenoHudSnapshot snap, boolean promptLive,
                               boolean reduced) {
        int id = mc.player == null ? Integer.MIN_VALUE : mc.player.getId();
        if (id != lastPlayerId) {
            lastPlayerId = id;
            motion.reset();
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : (float) ((now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        motion.advance(dt, fraction(snap.curHp, snap.maxHp), fraction(snap.curKi, snap.maxKi),
                fraction(snap.curStm, snap.maxStm),
                SparkingKiBar.charging() ? SparkingChargeClientState.litSegments() : 0,
                promptLive, reduced);
    }

    private void drawNameplate(GuiGraphics g, Font font, XenoHudSnapshot snap) {
        int x = XenoBt3HudLayout.NAMEPLATE_X;
        int y = XenoBt3HudLayout.NAMEPLATE_Y;
        blit(g, XenoBt3HudAtlas.NAMEPLATE, x, y);

        XenoBt3HudAtlas.Well well = XenoBt3HudAtlas.NAMEPLATE_WELL;
        int wellX = x + well.x();
        int textY = y + well.y() + (well.height() - font.lineHeight) / 2;

        String name = snap.name == null ? "" : snap.name;
        // The well is the only room the plate has; a long name is cut to it rather than allowed to
        // run out over the chevrons at either end.
        name = font.plainSubstrByWidth(name, well.width() - 44);
        drawPart(g, font, XenoHudConfig.Part.NAME, name, wellX, textY);

        String level = "Lv. " + Math.max(0, snap.level);
        float levelW = partWidth(font, XenoHudConfig.Part.LEVEL, level);
        drawPart(g, font, XenoHudConfig.Part.LEVEL, level,
                x + well.x() + well.width() - levelW, textY);
    }

    private void drawHealth(GuiGraphics g, boolean reduced) {
        if (XenoHudConfig.hidden(XenoHudConfig.Part.HP)) return;
        Sprite bar = XenoBt3HudAtlas.HP_BAR;
        int x = XenoBt3HudLayout.BAR_X + XenoHudConfig.partX(XenoHudConfig.Part.HP);
        int y = XenoBt3HudLayout.HP_Y + XenoHudConfig.partY(XenoHudConfig.Part.HP);

        blitTinted(g, bar, x, y, SHELL);
        float ghost = motion.hpGhost();
        if (ghost > motion.hp()) {
            blitClippedTinted(g, bar, x, y,
                    XenoBt3HudAtlas.HP_TRACK_X + XenoBt3HudLayout.hpFillWidth(ghost), HP_GHOST);
        }
        int hpWidth = XenoBt3HudAtlas.HP_TRACK_X + XenoBt3HudLayout.hpFillWidth(motion.hp());
        int formTint = FormHudTint.current();
        if (formTint != 0) {
            // A form in its own colour (Hakaishin's purple), over the neutral copy of the art.
            blitClippedNeutralTinted(g, bar, x, y, hpWidth, formTint);
        } else {
            blitClipped(g, bar, x, y, hpWidth);
        }

        if (motion.hp() < HP_CRITICAL && !reduced) {
            // Continuous motion is reserved for states that want the player to look; this is one.
            int alpha = 60 + Math.round(70 * AnimUtil.pulse01(760L));
            HudDraw.borderRect(g, x - 1, y - 1, bar.width() + 2, bar.height() + 2,
                    (alpha << 24) | 0x00FF3B30, 1);
        }
    }

    /**
     * Ki, including the whole Max Power story.
     *
     * <p>Three states, and all three are the server's: the resting bar, the red-to-gold conversion
     * while a committed charge runs, and the gold drain while Sparking is up. The charge stage
     * draws the bar red and then overdraws the segments the server says are converted in gold, so
     * the sequence cannot get ahead of the charge or finish without it.
     */
    private void drawKi(GuiGraphics g, XenoHudSnapshot snap) {
        if (XenoHudConfig.hidden(XenoHudConfig.Part.KI)) return;
        Sprite bar = XenoBt3HudAtlas.KI_BAR;
        int x = XenoBt3HudLayout.BAR_X + XenoHudConfig.partX(XenoHudConfig.Part.KI);
        int y = XenoBt3HudLayout.KI_Y + XenoHudConfig.partY(XenoHudConfig.Part.KI);

        blitTinted(g, bar, x, y, SHELL);

        if (SparkingKiBar.charging()) {
            int lit = SparkingChargeClientState.litSegments();
            blitTinted(g, bar, x, y, SparkingKiBar.CHARGE_RED);
            if (lit > 0) {
                blitClippedTinted(g, bar, x, y,
                        XenoBt3HudLayout.kiClipWidth(lit / (float) SparkingKiBar.segmentCount()),
                        SparkingKiBar.FILL);
            }
            float flash = motion.segmentFlash01();
            if (flash > 0f && lit > 0 && lit <= XenoBt3HudAtlas.kiSegmentCount()) {
                // One short emissive sweep over the segment that just converted. Segments already
                // converted stay steady, so the bar reads as a sequence rather than a loop.
                int from = XenoBt3HudAtlas.kiSegmentStart(lit - 1);
                int to = XenoBt3HudLayout.kiClipWidth(lit / (float) SparkingKiBar.segmentCount());
                int alpha = Math.round(200 * flash);
                HudDraw.fillRect(g, x + from, y, Math.max(1, to - from), bar.height(),
                        (alpha << 24) | (SparkingKiBar.HIGHLIGHT & 0x00FFFFFF));
            }
            return;
        }

        boolean sparking = snap.sparkingActive || SparkingKiBar.active();
        int formTint = FormHudTint.current();
        if (!sparking && formTint != 0) {
            // Sparking's gold keeps priority: it is the timer. Otherwise the form's colour.
            blitClippedNeutralTinted(g, bar, x, y, XenoBt3HudLayout.kiClipWidth(motion.ki()), formTint);
            return;
        }
        blitClippedTinted(g, bar, x, y, XenoBt3HudLayout.kiClipWidth(motion.ki()),
                sparking ? SparkingKiBar.FILL : KI_NORMAL);
    }

    private void drawStamina(GuiGraphics g) {
        if (XenoHudConfig.hidden(XenoHudConfig.Part.STM)) return;
        Sprite bar = XenoBt3HudAtlas.STAMINA_BAR;
        int x = XenoBt3HudLayout.BAR_X + XenoHudConfig.partX(XenoHudConfig.Part.STM);
        int y = XenoBt3HudLayout.STM_Y + XenoHudConfig.partY(XenoHudConfig.Part.STM);
        blitTinted(g, bar, x, y, SHELL);
        blitClipped(g, bar, x, y, XenoBt3HudLayout.staminaClipWidth(motion.stamina()));
    }

    /**
     * The character in the ring.
     *
     * <p>The portrait is drawn into the square the generator measured as fully covered by the ring
     * art and the ring is drawn over it, so no corner of a square portrait escapes a round frame.
     * The bundle ships no mask for this ring, which is why the well is a measured square rather
     * than the ring's full bore.
     */
    private void drawPortrait(GuiGraphics g, Minecraft mc, XenoHudSnapshot snap) {
        int x = XenoBt3HudLayout.PORTRAIT_X + XenoHudConfig.partX(XenoHudConfig.Part.PORTRAIT);
        int y = XenoBt3HudLayout.PORTRAIT_Y + XenoHudConfig.partY(XenoHudConfig.Part.PORTRAIT);
        Sprite ring = XenoBt3HudAtlas.PORTRAIT_RING;

        if (!XenoHudConfig.hidden(XenoHudConfig.Part.PORTRAIT)
                && mc.player instanceof AbstractClientPlayer player) {
            XenoBt3HudAtlas.Well well = XenoBt3HudAtlas.PORTRAIT_WELL;
            int px = x + well.x();
            int py = y + well.y();
            if (XenoHudConfig.portraitMode == XenoHudConfig.PortraitMode.CHARACTER) {
                CharacterPortraitCache.draw(g, player, px, py, well.width(), well.height());
            } else {
                var skin = player.getSkin().texture();
                g.blit(skin, px, py, well.width(), well.height(), 8f, 8f, 8, 8, 64, 64);
                g.blit(skin, px, py, well.width(), well.height(), 40f, 8f, 8, 8, 64, 64);
            }
        }

        blit(g, ring, x, y);

        if (snap.transforming) {
            HudDraw.transformChargeArc(g, x + ring.width() / 2, y + ring.height() / 2,
                    ring.width() / 2 - 1, ring.height() / 2 - 1, 3, snap.transformChargePercent);
        }
    }

    private void drawBarText(GuiGraphics g, Font font, XenoHudSnapshot snap) {
        if (!XenoClientConfig.hudBarNumbers) return;
        if (snap != formatted) {
            formatted = snap;
            hpText = HudNumbers.formatPair(snap.curHp, snap.maxHp);
            kiText = HudNumbers.formatPair(snap.curKi, snap.maxKi);
            stmText = HudNumbers.formatPair(snap.curStm, snap.maxStm);
        }
        drawBarValue(g, font, XenoHudConfig.Part.HP_TEXT, hpText,
                XenoBt3HudAtlas.HP_BAR, XenoBt3HudLayout.HP_Y);
        drawBarValue(g, font, XenoHudConfig.Part.KI_TEXT, kiText,
                XenoBt3HudAtlas.KI_BAR, XenoBt3HudLayout.KI_Y);
        drawBarValue(g, font, XenoHudConfig.Part.STM_TEXT, stmText,
                XenoBt3HudAtlas.STAMINA_BAR, XenoBt3HudLayout.STM_Y);
    }

    /** A readout right-aligned inside its bar, clear of the slanted end cap. */
    private void drawBarValue(GuiGraphics g, Font font, int part, String text, Sprite bar, int barY) {
        if (text == null || text.isEmpty()) return;
        int right = XenoBt3HudLayout.BAR_X + bar.width() - 8 + XenoHudConfig.partX(part);
        float glyphH = font.lineHeight * XenoHudConfig.partScale(part);
        float y = barY + (bar.height() - glyphH) / 2f + XenoHudConfig.partY(part);
        drawPart(g, font, part, text, right - partWidth(font, part, text), y);
    }

    private void drawForm(GuiGraphics g, Font font, XenoHudSnapshot snap) {
        int y = XenoBt3HudLayout.FORM_Y;
        if (!snap.activeForm.isBlank()) {
            drawPart(g, font, XenoHudConfig.Part.FORM, snap.activeForm,
                    XenoBt3HudLayout.BAR_X + XenoHudConfig.partX(XenoHudConfig.Part.FORM),
                    y + XenoHudConfig.partY(XenoHudConfig.Part.FORM));
        }
        if (snap.releaseText != null) {
            String text = snap.releaseText;
            float w = partWidth(font, XenoHudConfig.Part.RELEASE_TEXT, text);
            drawPart(g, font, XenoHudConfig.Part.RELEASE_TEXT, text,
                    XenoBt3HudLayout.PANEL_WIDTH - 6 - w
                            + XenoHudConfig.partX(XenoHudConfig.Part.RELEASE_TEXT),
                    y + XenoHudConfig.partY(XenoHudConfig.Part.RELEASE_TEXT));
        }
        String sparking = snap.sparkingActive ? "SPARKING" : snap.sparking >= 99f ? "READY" : "";
        if (!sparking.isEmpty()) {
            drawPart(g, font, XenoHudConfig.Part.SPARKING, sparking,
                    XenoBt3HudLayout.BAR_X + 96 + XenoHudConfig.partX(XenoHudConfig.Part.SPARKING),
                    y + XenoHudConfig.partY(XenoHudConfig.Part.SPARKING));
        }
    }

    /**
     * The combat rail.
     *
     * <p>Each chip is drawn in the ability shell nearest its own accent colour, with its meter
     * across the shell's foot and its key label under it. The chips themselves, and the rules for
     * whether the rail shows at all, are {@link XenoCooldownHudOverlay}'s — this reuses both rather
     * than re-deciding either, so the cooldown HUD's content settings still apply here.
     */
    private void drawRail(GuiGraphics g, Font font, List<XenoCooldownHudOverlay.Chip> chips) {
        for (int i = 0; i < chips.size(); i++) {
            XenoCooldownHudOverlay.Chip chip = chips.get(i);
            Sprite shell = SHELLS[XenoBt3HudLayout.shellForAccent(chip.accent())];
            int x = XenoBt3HudLayout.slotX(i);
            int y = XenoBt3HudLayout.slotY(i);

            if (!chip.enabled()) {
                blitTinted(g, shell, x, y, SLOT_DISABLED);
            } else {
                blit(g, shell, x, y);
            }

            if (chip.busy() && chip.fraction() > 0f) {
                int w = Math.round((shell.width() - 6) * Math.min(1f, chip.fraction()));
                if (w > 0) {
                    HudDraw.fillRect(g, x + 3, y + shell.height() - 5, w, 3, chip.accent());
                }
            }
            if (chip.meterMode() == XenoCooldownHudOverlay.MeterMode.COMBO && chip.comboStep() > 0) {
                String step = String.valueOf(chip.comboStep());
                g.drawString(font, step, x + shell.width() - 4 - font.width(step), y + 2,
                        chip.accent(), true);
            }

            String label = chip.shortName() == null ? "" : chip.shortName();
            g.drawString(font, label, x + (shell.width() - font.width(label)) / 2,
                    y + shell.height() + 1, chip.enabled() ? SLOT_LABEL : SLOT_LABEL_DIM, true);
            String key = chip.key() == null ? "" : chip.key();
            if (!key.isEmpty() && !chip.timeText().isEmpty()) key = chip.timeText();
            if (!key.isEmpty()) {
                g.drawString(font, key, x + (shell.width() - font.width(key)) / 2,
                        y - 9, SLOT_LABEL_DIM, true);
            }
        }
    }

    /**
     * The cinematic follow-up prompt.
     *
     * <p>Drawn only while {@link Bt3CombatClient#cinematicFollowupLive()} — the same window the
     * input path checks before it sends the rush — so it cannot survive a timeout, the target
     * dying, the next combo beat, the rush starting, or the server turning cinematic rush off. The
     * fade out is short enough that the prompt is gone within about a tick of the window closing.
     */
    private void drawPrompt(GuiGraphics g, Font font, int chips) {
        float opacity = Math.min(1f, motion.prompt01());
        int alpha = Math.round(255 * opacity);
        if (alpha <= 2) return;
        int y = XenoBt3HudLayout.promptY(chips);

        Sprite arrow = XenoBt3HudAtlas.PROMPT_ARROW;
        Sprite plate = XenoBt3HudAtlas.PROMPT_PLATE;
        blitTinted(g, arrow, XenoBt3HudLayout.PROMPT_ARROW_X, y, (alpha << 24) | 0x00FFFFFF);
        int plateY = y + XenoBt3HudLayout.PROMPT_PLATE_INSET_Y;
        blitTinted(g, plate, XenoBt3HudLayout.PROMPT_PLATE_X, plateY, (alpha << 24) | 0x00FFFFFF);

        String text = "X X X → " + followupKey();
        int textY = plateY + (plate.height() - font.lineHeight) / 2;
        g.drawString(font, text,
                XenoBt3HudLayout.PROMPT_PLATE_X + (plate.width() - font.width(text)) / 2, textY,
                (alpha << 24) | 0x00FFD54F, true);
    }

    /**
     * What to press for the follow-up.
     *
     * <p>"A" on a controller, matching both the pad's own face button and the actionbar message
     * this prompt replaces; the dragon-dash binding's own label otherwise, so a rebound key is
     * reported as the key it now is.
     */
    private static String followupKey() {
        if (XenoPadInput.controllerActive()) return "A";
        return XenoCooldownHudOverlay.keyLabel(Bt3CombatClient.DRAGON_DASH);
    }

    // --- atlas blits ---------------------------------------------------------------------

    private static void blit(GuiGraphics g, Sprite s, int x, int y) {
        g.blit(XenoBt3HudAtlas.TEXTURE, x, y, s.width(), s.height(), s.u(), s.v(),
                s.width(), s.height(), XenoBt3HudAtlas.ATLAS_WIDTH, XenoBt3HudAtlas.ATLAS_HEIGHT);
    }

    /** The left {@code width} pixels of a sprite, for a bar fill. */
    private static void blitClipped(GuiGraphics g, Sprite s, int x, int y, int width) {
        int w = Math.min(s.width(), width);
        if (w <= 0) return;
        g.blit(XenoBt3HudAtlas.TEXTURE, x, y, w, s.height(), s.u(), s.v(), w, s.height(),
                XenoBt3HudAtlas.ATLAS_WIDTH, XenoBt3HudAtlas.ATLAS_HEIGHT);
    }

    private static void blitTinted(GuiGraphics g, Sprite s, int x, int y, int argb) {
        setColor(g, argb);
        blit(g, s, x, y);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void blitClippedTinted(GuiGraphics g, Sprite s, int x, int y, int width,
                                          int argb) {
        setColor(g, argb);
        blitClipped(g, s, x, y, width);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** A bar fill from the greyscale atlas copy, tinted: any colour comes out clean. */
    private static void blitClippedNeutralTinted(GuiGraphics g, Sprite s, int x, int y, int width,
                                                 int argb) {
        int w = Math.min(s.width(), width);
        if (w <= 0) return;
        setColor(g, argb);
        g.blit(XenoBt3HudAtlas.NEUTRAL_TEXTURE, x, y, w, s.height(), s.u(), s.v(), w, s.height(),
                XenoBt3HudAtlas.ATLAS_WIDTH, XenoBt3HudAtlas.ATLAS_HEIGHT);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void setColor(GuiGraphics g, int argb) {
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    // --- text ----------------------------------------------------------------------------

    /** One element's text in its own colour, scale, weight and font, as the other views draw it. */
    private static void drawPart(GuiGraphics g, Font font, int part, String text,
                                 float x, float y) {
        if (XenoHudConfig.hidden(part) || text == null || text.isEmpty()) return;
        Style style = Style.EMPTY.withFont(XenoHudConfig.partFontLocation(part))
                .withBold(XenoHudConfig.partBold[part]);
        Component line = Component.literal(text).setStyle(style);
        float partScale = XenoHudConfig.partScale(part);
        if (partScale == 1.0f) {
            g.drawString(font, line, Math.round(x), Math.round(y),
                    XenoHudConfig.partColor(part), true);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(partScale, partScale, 1f);
        g.drawString(font, line, 0, 0, XenoHudConfig.partColor(part), true);
        g.pose().popPose();
    }

    private static float partWidth(Font font, int part, String text) {
        if (text == null || text.isEmpty()) return 0f;
        Style style = Style.EMPTY.withFont(XenoHudConfig.partFontLocation(part))
                .withBold(XenoHudConfig.partBold[part]);
        return font.width(Component.literal(text).setStyle(style)) * XenoHudConfig.partScale(part);
    }

    private static float fraction(double current, double max) {
        if (max <= 0 || !Double.isFinite(max) || !Double.isFinite(current)) return 0f;
        return (float) Math.max(0.0, Math.min(1.0, current / max));
    }
}
