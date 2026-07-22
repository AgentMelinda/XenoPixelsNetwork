package net.bullettrain.xenopixelsmod.client;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Cooldowns;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.common.stats.techniques.TechniqueData;
import com.dragonminez.common.stats.techniques.Techniques;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.util.LazyOptional;

import java.util.Locale;
import java.util.Map;

/**
 * XenoPixels KI technique hotbar — inherits equipped slots live from DMZ
 * {@link Techniques#getEquippedSlots()} (same source as TechniqueHotbarHUD).
 */
@OnlyIn(Dist.CLIENT)
public class XenoTechniqueHotbarOverlay implements IGuiOverlay {
    /** DMZ uses 8 technique slots (Alt 0-3, Ctrl 4-7). */
    private static final int TOTAL_SLOTS = 8;
    private static final int BAR_SLOTS = 4;
    private static final int SLOT_H = 18;
    private static final int SLOT_GAP = 3;
    private static final int PANEL_PAD = 6;
    private static final int BADGE_W = 14;
    private static final int MARGIN = 10;

    /** Same cooldown key prefix DMZ TechniqueHotbarHUD uses: {@code TechniqueData_} + id */
    private static final String CD_PREFIX = "TechniqueData_";

    private static final int[] CD_LAST_TICKS = new int[TOTAL_SLOTS];
    private static final long[] CD_LAST_MS = new long[TOTAL_SLOTS];
    private static final String[] CD_LAST_ID = new String[TOTAL_SLOTS];

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (!XenoClientConfig.techniqueHotbarEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) return;

        LocalPlayer player = mc.player;
        LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
        if (!opt.isPresent()) return;
        StatsData data = opt.orElse(null);
        if (data == null || !data.isDataLoaded()) return;

        Status status = data.getStatus();
        if (status == null || !status.isHasCreatedCharacter()) return;

        // Live DMZ technique state (equipped slots, charge, selection)
        Techniques techniques = data.getTechniques();
        if (techniques == null) return;

        String[] equipped = techniques.getEquippedSlots();
        if (equipped == null) equipped = new String[TOTAL_SLOTS];

        Map<String, TechniqueData> unlocked = techniques.getUnlockedTechniques();
        Cooldowns cds = data.getCooldowns();
        int selected = techniques.getSelectedSlot();
        String chargingId = techniques.getChargingTechniqueId();
        float chargePct = techniques.getTechniqueChargePercent();
        boolean charging = techniques.isTechniqueCharging() || techniques.isTechniqueChargeActive();

        if (charging) {
            // DMZ stores techniqueChargePercent as 0..200 (percent points), NOT 0..1
            drawChargeMeter(g, mc.font, screenWidth, screenHeight, chargePct, resolveName(unlocked, chargingId));
        }

        KeyMapping[] keys = KeyBinds.TECHNIQUE_SLOTS;
        if (keys == null || keys.length < TOTAL_SLOTS) return;

        // Same Alt/Ctrl bar selection as DMZ TechniqueHotbarHUD
        KeyModifier modAlt = keys[0].getKeyModifier();
        KeyModifier modCtrl = keys[4].getKeyModifier();
        boolean altDown = KeyBinds.isBarModifierActive(modAlt);
        boolean ctrlDown = KeyBinds.isBarModifierActive(modCtrl);

        int offset;
        if (ctrlDown && modCtrl != modAlt) {
            offset = 4;
        } else if (altDown) {
            offset = 0;
        } else if (ctrlDown) {
            offset = 4;
        } else {
            return;
        }

        Font font = mc.font;

        int maxNameW = 80;
        for (int i = 0; i < BAR_SLOTS; i++) {
            int idx = offset + i;
            String id = slotId(equipped, idx);
            TechniqueData td = resolveTechnique(unlocked, id);
            maxNameW = Math.max(maxNameW, font.width(displayName(td, id)));
        }

        int innerW = BADGE_W + 6 + maxNameW + 44;
        int panelW = PANEL_PAD * 2 + innerW;
        int panelH = PANEL_PAD * 2 + BAR_SLOTS * SLOT_H + (BAR_SLOTS - 1) * SLOT_GAP;
        int panelX = MARGIN;
        int panelY = screenHeight - MARGIN - panelH - 22;

        fillRounded(g, panelX - 2, panelY - 2, panelW + 4, panelH + 4, 0xCC050510);
        fillRounded(g, panelX, panelY, panelW, panelH, 0xEE0A1428);
        g.fill(panelX, panelY, panelX + 3, panelY + panelH, offset == 0 ? 0xFF42A5F5 : 0xFFFF7043);

        String barLabel = offset == 0 ? "KI · ALT" : "KI · CTRL";
        g.drawString(font, barLabel, panelX + PANEL_PAD + 4, panelY - 11, 0xFF90CAF9, true);

        // Equipped count from DMZ slots
        int filled = 0;
        for (String s : equipped) {
            if (s != null && !s.isEmpty()) filled++;
        }
        String countLabel = filled + "/" + TOTAL_SLOTS;
        g.drawString(font, countLabel, panelX + panelW - font.width(countLabel) - 4, panelY - 11, 0xFF78909C, false);

        for (int i = 0; i < BAR_SLOTS; i++) {
            int idx = offset + i;
            int rowY = panelY + PANEL_PAD + i * (SLOT_H + SLOT_GAP);
            int rowX = panelX + PANEL_PAD;

            // Direct inheritance from DMZ equippedSlots[idx]
            String techId = slotId(equipped, idx);
            TechniqueData td = resolveTechnique(unlocked, techId);
            String name = displayName(td, techId);
            boolean empty = techId.isEmpty();
            boolean known = td != null;

            int cdTicks = 0;
            if (!empty && cds != null) {
                // Match DMZ TechniqueHotbarHUD cooldown key exactly
                cdTicks = cds.getCooldown(CD_PREFIX + techId);
                if (cdTicks <= 0) {
                    // fallbacks some builds used
                    cdTicks = Math.max(cdTicks, cds.getCooldown(techId));
                }
            }
            float cdSec = interpolateCd(idx, techId, cdTicks);
            boolean onCd = cdSec > 0.05f;
            boolean isSel = selected == idx;
            boolean isChargingThis = charging && !techId.isEmpty() && techId.equals(chargingId);

            int bg;
            if (isChargingThis) {
                bg = 0xCC4A2808;
            } else if (isSel) {
                bg = 0xCC1E4A7A;
            } else if (empty) {
                bg = 0x66101828;
            } else if (onCd) {
                bg = 0xAA3A1820;
            } else {
                bg = 0xAA122038;
            }
            fillRounded(g, rowX, rowY, innerW, SLOT_H, bg);

            int outline = isChargingThis ? 0xFFFFB74D : (isSel ? 0xFF42A5F5 : 0);
            if (outline != 0) {
                g.renderOutline(rowX, rowY, innerW, SLOT_H, outline);
            }

            // Key badge from DMZ KeyBinds.TECHNIQUE_SLOTS[idx]
            String keyLabel = keyLabel(keys, idx);
            int bx = rowX + 2;
            int by = rowY + (SLOT_H - 12) / 2;
            g.fill(bx, by, bx + BADGE_W, by + 12, 0xEE000000);
            g.renderOutline(bx, by, BADGE_W, 12, isSel ? 0xFF64B5F6 : 0x88666688);
            int kw = font.width(keyLabel);
            g.drawString(font, keyLabel, bx + Math.max(0, (BADGE_W - kw) / 2), by + 2, 0xFFFFFFFF, false);

            // Slot index pip (DMZ slot number 1-8)
            String slotNum = String.valueOf(idx + 1);
            g.drawString(font, slotNum, bx + BADGE_W + 4, rowY + 5, 0xFF607D8B, false);

            int nameX = bx + BADGE_W + 14;
            int nameColor;
            if (empty) {
                nameColor = 0xFF556677;
            } else if (!known) {
                nameColor = 0xFFFFCC80; // equipped id present but data not resolved yet
            } else if (onCd) {
                nameColor = 0xFFFF8A80;
            } else if (isChargingThis) {
                nameColor = 0xFFFFE082;
            } else {
                nameColor = 0xFFE3F2FD;
            }
            g.drawString(font, name, nameX, rowY + 5, nameColor, false);

            if (onCd) {
                String cdsTxt = String.format(Locale.US, "%.1fs", cdSec);
                int cw = font.width(cdsTxt);
                g.drawString(font, cdsTxt, rowX + innerW - cw - 4, rowY + 5, 0xFFFFAB91, true);
            } else if (!empty && known) {
                g.fill(rowX + innerW - 8, rowY + SLOT_H / 2 - 2,
                        rowX + innerW - 4, rowY + SLOT_H / 2 + 2, 0xFF66BB6A);
            }
        }
    }

    /**
     * @param rawPercent DMZ {@code getTechniqueChargePercent()} — range 0..200 (already in %).
     */
    private static void drawChargeMeter(GuiGraphics g, Font font, int sw, int sh, float rawPercent, String techName) {
        // Normalize: DMZ uses 0-200 percent points (see Techniques.setTechniqueChargePercent)
        float pct = Math.max(0f, Math.min(200f, rawPercent));
        // If a build ever sends 0-1, scale up
        if (pct > 0f && pct <= 1.0001f) {
            pct *= 100f;
        }

        boolean over = pct > 100f;
        // 0-100% fills the main bar; overcharge sits on top as a pulse strip
        float fill01 = Math.min(1f, pct / 100f);
        float over01 = over ? Math.min(1f, (pct - 100f) / 100f) : 0f;

        int barW = 182;
        int barH = 12;
        int x = (sw - barW) / 2;
        // Just above the vanilla hotbar / item slots
        int y = sh - 66;

        int filled = Math.round(barW * fill01);

        g.fill(x - 3, y - 3, x + barW + 3, y + barH + 3, 0xDD050510);
        g.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xFF2A2A3A);
        g.fill(x, y, x + barW, y + barH, 0xFF12121C);

        if (filled > 0) {
            int c1 = over ? 0xFFE53935 : 0xFF1E88E5;
            int c2 = over ? 0xFFFF8A80 : 0xFF64B5F6;
            g.fill(x, y, x + filled, y + barH, c1);
            g.fill(x, y, x + filled, y + 3, c2);
            // leading edge
            if (filled < barW) {
                g.fill(x + filled - 2, y, x + filled, y + barH, c2);
            }
        }

        // Overcharge layer: left→right fill of a bright strip proportional to 100→200
        if (over01 > 0f) {
            int overW = Math.max(2, Math.round(barW * over01));
            g.fill(x, y + barH - 3, x + overW, y + barH, 0xFFFF1744);
            g.fill(x, y, x + overW, y + 2, 0xAAFFFFFF);
        }

        // Tick marks at 50% / 100%
        int mid = x + barW / 2;
        g.fill(mid, y, mid + 1, y + barH, 0x44FFFFFF);
        if (over) {
            g.fill(x + barW - 1, y, x + barW, y + barH, 0x88FF5252);
        }

        String label = over
                ? String.format(Locale.US, "OVERCHARGE %.0f%%", pct)
                : String.format(Locale.US, "CHARGING %.0f%%", pct);
        if (techName != null && !techName.isEmpty() && !techName.startsWith("—")) {
            label = techName + "  ·  " + label;
        }
        int tw = font.width(label);
        g.drawString(font, label, x + (barW - tw) / 2, y - 12, over ? 0xFFFF8A80 : 0xFFB3E5FC, true);
    }

    /** DMZ equippedSlots[i] — empty string means vacant. */
    private static String slotId(String[] equipped, int idx) {
        if (equipped == null || idx < 0 || idx >= equipped.length) return "";
        String s = equipped[idx];
        return s == null ? "" : s;
    }

    /**
     * Resolve TechniqueData the same way DMZ does: unlockedTechniques.get(equippedId).
     * Also tries suffix / case-insensitive match if the map key form differs slightly.
     */
    private static TechniqueData resolveTechnique(Map<String, TechniqueData> unlocked, String id) {
        if (id == null || id.isEmpty() || unlocked == null || unlocked.isEmpty()) return null;

        TechniqueData direct = unlocked.get(id);
        if (direct != null) return direct;

        // Some techniques store id with different casing / namespace
        for (Map.Entry<String, TechniqueData> e : unlocked.entrySet()) {
            if (e.getKey() == null) continue;
            if (e.getKey().equalsIgnoreCase(id)) return e.getValue();
            if (e.getKey().endsWith("." + id) || id.endsWith("." + e.getKey())) return e.getValue();
            TechniqueData td = e.getValue();
            if (td != null && id.equals(td.getId())) return td;
            if (td != null && id.equalsIgnoreCase(td.getName())) return td;
        }
        return null;
    }

    private static String resolveName(Map<String, TechniqueData> unlocked, String id) {
        return displayName(resolveTechnique(unlocked, id), id == null ? "" : id);
    }

    private static String displayName(TechniqueData td, String id) {
        if (td != null) {
            String n = td.getName();
            if (n != null && !n.isEmpty()) {
                if (n.contains(".")) {
                    String translated = Component.translatable(n).getString();
                    if (translated != null && !translated.equals(n)) return translated;
                    // try as-is literal if translation missing
                    int d = n.lastIndexOf('.');
                    return d >= 0 ? pretty(n.substring(d + 1)) : pretty(n);
                }
                return n;
            }
            String tid = td.getId();
            if (tid != null && !tid.isEmpty()) {
                String tr = Component.translatable(tid).getString();
                if (tr != null && !tr.equals(tid)) return tr;
            }
        }
        if (id == null || id.isEmpty()) return "— empty —";

        // Translate equipped id if it's a lang key
        if (id.contains(".")) {
            String tr = Component.translatable(id).getString();
            if (tr != null && !tr.equals(id)) return tr;
            int d = id.lastIndexOf('.');
            return pretty(id.substring(d + 1));
        }
        return pretty(id);
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isEmpty()) return "— empty —";
        String s = raw.replace('_', ' ').trim();
        if (s.isEmpty()) return "— empty —";
        // Capitalize words
        StringBuilder sb = new StringBuilder(s.length());
        boolean cap = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                sb.append(c);
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String keyLabel(KeyMapping[] slots, int idx) {
        try {
            String s = slots[idx].getKey().getDisplayName().getString();
            if (s == null || s.isEmpty()) return String.valueOf((idx % BAR_SLOTS) + 1);
            // Strip "Key." prefixes from some keyboards
            if (s.startsWith("key.keyboard.")) s = s.substring("key.keyboard.".length());
            if (s.length() > 3) s = s.substring(0, 3);
            return s.toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return String.valueOf((idx % BAR_SLOTS) + 1);
        }
    }

    private static float interpolateCd(int slot, String id, int ticks) {
        long now = System.currentTimeMillis();
        String safe = id == null ? "" : id;
        if (!safe.equals(CD_LAST_ID[slot]) || ticks != CD_LAST_TICKS[slot]) {
            CD_LAST_ID[slot] = safe;
            CD_LAST_TICKS[slot] = ticks;
            CD_LAST_MS[slot] = now;
        }
        if (ticks <= 0) return 0f;
        float elapsed = (now - CD_LAST_MS[slot]) / 1000f;
        return Math.max(0f, ticks / 20f - elapsed);
    }

    private static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }
}
