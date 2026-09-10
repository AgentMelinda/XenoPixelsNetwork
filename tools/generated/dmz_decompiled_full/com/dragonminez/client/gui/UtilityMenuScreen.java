package com.dragonminez.client.gui;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.gui.radial.AbstractRadialNode;
import com.dragonminez.client.gui.radial.FormPreview;
import com.dragonminez.client.gui.radial.IUtilityMenuSlotAdapter;
import com.dragonminez.client.gui.radial.ModelFormPreview;
import com.dragonminez.client.gui.radial.RadialLayoutStore;
import com.dragonminez.client.gui.radial.RadialNode;
import com.dragonminez.client.gui.radial.nodes.ActionsNode;
import com.dragonminez.client.gui.radial.nodes.DescendNode;
import com.dragonminez.client.gui.radial.nodes.EmptyNode;
import com.dragonminez.client.gui.radial.nodes.FormSelectNode;
import com.dragonminez.client.gui.radial.nodes.MoreFormsNode;
import com.dragonminez.client.gui.radial.nodes.MoreNode;
import com.dragonminez.client.gui.radial.nodes.MovementNode;
import com.dragonminez.client.gui.radial.nodes.ReleaseNode;
import com.dragonminez.client.gui.radial.nodes.StackSkillNode;
import com.dragonminez.client.gui.radial.nodes.SuperFormNode;
import com.dragonminez.client.gui.utilitymenu.IUtilityMenuSlot;
import com.dragonminez.client.render.layer.DMZSkinLayer;
import com.dragonminez.client.render.shader.UtilityMenuBlur;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.client.util.ScrollbarState;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class UtilityMenuScreen extends ScaledScreen {
   private static final List<IUtilityMenuSlot> ADDON_SLOTS = new ArrayList<>();
   private static long utilityMenuReopenBlockedUntilMs = 0L;
   private static final long ANIMATION_DURATION = 140L;
   private static final int SLOTS = 8;
   private static final float R_INNER = 40.0F;
   private static final float R_OUTER = 86.0F;
   private static final float CHILD_BAND = 42.0F;
   private static final float SECTOR_GAP_DEG = 3.0F;
   private static final float ICON_BASE = 16.0F;
   private static final float ICON_CHILD = 14.0F;
   private static final int MODEL_SCALE = 32;
   private static final int MODEL_Y_OFFSET = 26;
   private static final float ANIM_SPEED = 14.0F;
   private static final float[] PANEL = new float[]{0.11F, 0.11F, 0.13F, 0.6F};
   private static final float[] PANEL_HOVER = new float[]{0.2F, 0.52F, 0.96F, 0.9F};
   private static final float[] PANEL_INACTIVE = new float[]{0.09F, 0.09F, 0.1F, 0.42F};
   private static final float[] CHILD_PANEL = new float[]{0.1F, 0.12F, 0.11F, 0.64F};
   private static final float[] CHILD_HOVER = new float[]{0.22F, 0.46F, 0.3F, 0.86F};
   private final long openTime;
   private boolean closing = false;
   private long closeStartTime = -1L;
   private long lastFrameNanos = 0L;
   private float frameDt = 0.0F;
   private StatsData statsData;
   private final List<RadialNode> baseNodes = new ArrayList<>();
   private final List<RadialNode> chain = new ArrayList<>();
   private FormPreview currentPreview = null;
   private static final int PANEL_WIDTH = 130;
   private static final int PANEL_ROW_H = 13;
   private static final int PANEL_TITLE_H = 15;
   private static final int PANEL_PAD = 5;
   private static final int PANEL_SCREEN_MARGIN = 4;
   private static final float PANEL_CLEAR_GAP = 12.0F;
   private static final int PANEL_MAX_ROWS = 8;
   private List<RadialNode> panelOptions = null;
   private Component panelTitle = null;
   private boolean panelScrollable = false;
   private int panelScroll = 0;
   private final ScrollbarState panelBar = new ScrollbarState();
   private MoreNode openMore = null;
   private float panelAngleDeg = 0.0F;
   private int panelLevel = 0;
   private UtilityMenuScreen.Hover frozenHover = new UtilityMenuScreen.Hover();
   private int dragIndex = -1;
   private boolean dragging = false;
   private double dragStartY = 0.0;
   private double dragCurrentY = 0.0;
   private static final long DOUBLE_CLICK_MS = 300L;
   private RadialNode lastClickNode = null;
   private long lastClickMs = 0L;

   public UtilityMenuScreen() {
      super(Component.literal("Menu").withStyle(Style.EMPTY.withFont(DMZ_FONT)));
      this.openTime = System.currentTimeMillis();
   }

   protected void init() {
      super.init();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> this.statsData = data);
      }

      this.buildBaseNodes();
      UtilityMenuBlur.start();
   }

   public void removed() {
      super.removed();
      UtilityMenuBlur.stop();
   }

   private void buildBaseNodes() {
      this.baseNodes.clear();
      int[] addon = new int[]{0};
      this.baseNodes.add(new SuperFormNode());
      this.baseNodes.add(new MoreFormsNode());
      this.baseNodes.add(this.nextAddonOrEmpty(addon));
      this.baseNodes.add(new MovementNode());
      this.baseNodes.add(new DescendNode());
      this.baseNodes.add(new ActionsNode());
      this.baseNodes.add(this.nextAddonOrEmpty(addon));
      this.baseNodes.add(new StackSkillNode());
   }

   private RadialNode nextAddonOrEmpty(int[] addonIndex) {
      return (RadialNode)(addonIndex[0] < ADDON_SLOTS.size() ? new IUtilityMenuSlotAdapter(ADDON_SLOTS.get((int)(addonIndex[0]++))) : new EmptyNode());
   }

   public boolean isPauseScreen() {
      return false;
   }

   @Override
   protected float computeDynamicScale(float availableScale) {
      return availableScale * 0.6666667F * ConfigManager.getUserConfig().getUtilityMenuScaleMultiplier();
   }

   @Override
   protected float getMinUiScale() {
      return 0.25F;
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (this.statsData != null) {
         this.updateUiScale();
         long nowNanos = System.nanoTime();
         this.frameDt = this.lastFrameNanos == 0L ? 0.0F : Math.min(0.1F, (float)(nowNanos - this.lastFrameNanos) / 1.0E9F);
         this.lastFrameNanos = nowNanos;
         float openScale = this.computeOpenScale();
         double uiMouseX = this.toUiX((double)mouseX);
         double uiMouseY = this.toUiY((double)mouseY);
         this.beginUiScale(graphics);
         int w = this.getUiWidth();
         int h = this.getUiHeight();
         float cx = (float)w / 2.0F;
         float cy = (float)h / 2.0F;
         UtilityMenuScreen.Hover hover = this.closing
            ? new UtilityMenuScreen.Hover()
            : (this.panelOptions != null ? this.frozenHover : this.resolveHover(cx, cy, uiMouseX, uiMouseY, openScale));
         this.currentPreview = hover.deepest != null ? hover.deepest.preview(this.statsData) : null;
         this.drawBaseSectors(graphics, cx, cy, hover, openScale);

         for (RadialNode node : this.baseNodes) {
            if (this.isActiveSlot(node)) {
               this.drawChildSectors(graphics, cx, cy, node, baseCenter(this.baseNodes.indexOf(node)), 1, hover, false, openScale);
            }
         }

         int modelX = Math.round(cx);
         int modelY = Math.round(cy + 26.0F * openScale + 12.0F * openScale);
         ModelFormPreview.render(graphics, modelX, modelY, Math.max(1, Math.round(32.0F * openScale)), (float)modelX, (float)modelY, this.currentPreview);
         this.drawBaseFaces(graphics, cx, cy, openScale);

         for (RadialNode nodex : this.baseNodes) {
            if (this.isActiveSlot(nodex)) {
               this.drawChildSectors(graphics, cx, cy, nodex, baseCenter(this.baseNodes.indexOf(nodex)), 1, hover, true, openScale);
            }
         }

         if (this.panelOptions != null) {
            this.drawPanel(graphics, cx, cy, uiMouseX, uiMouseY);
         }

         super.render(graphics, (int)Math.round(uiMouseX), (int)Math.round(uiMouseY), partialTick);
         this.endUiScale(graphics);
      }
   }

   private static float radiusForLevel(int level) {
      return level <= 0 ? 63.0F : 86.0F + ((float)level - 0.5F) * 42.0F;
   }

   private int visiblePanelRows() {
      int total = this.panelOptions.size();
      return this.panelScrollable ? Math.min(total, 8) : total;
   }

   private int[] panelBounds(float cx, float cy) {
      int rows = this.visiblePanelRows();
      int height = 15 + rows * 13 + 5;
      double rad = Math.toRadians((double)this.panelAngleDeg);
      float dirX = (float)Math.cos(rad);
      float dirY = (float)Math.sin(rad);
      float clearRadius = radiusForLevel(this.panelLevel) + 25.2F + 12.0F;
      float panelCx = cx + dirX * clearRadius;
      float panelCy = cy + dirY * clearRadius;
      int px = Math.round(panelCx - 65.0F);
      int py = Math.round(panelCy - (float)height / 2.0F);
      int uiW = this.getUiWidth();
      int uiH = this.getUiHeight();
      int maxPx = uiW - 130 - 4;
      int maxPy = uiH - height - 4;
      px = Mth.clamp(px, 4, Math.max(4, maxPx));
      py = Mth.clamp(py, 4, Math.max(4, maxPy));
      return new int[]{px, py, 130, height};
   }

   private int rowIndexAt(int rowsTop, double uiY) {
      return (int)Math.floor((uiY - (double)rowsTop) / 13.0);
   }

   private void drawPanel(GuiGraphics graphics, float cx, float cy, double mouseX, double mouseY) {
      List<RadialNode> opts = this.panelOptions;
      int visibleRows = this.visiblePanelRows();
      int maxScroll = Math.max(0, opts.size() - visibleRows);
      this.panelScroll = Mth.clamp(this.panelScroll, 0, maxScroll);
      int start = this.panelScrollable ? this.panelScroll : 0;
      int[] b = this.panelBounds(cx, cy);
      int px = b[0];
      int py = b[1];
      int pw = b[2];
      int ph = b[3];
      graphics.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, -16777216);
      graphics.fill(px, py, px + pw, py + ph, -267119584);
      graphics.fill(px, py, px + pw, py + 15, 822083583);
      Style style = Style.EMPTY.withFont(DMZ_FONT);
      Component title = (this.panelTitle != null ? this.panelTitle : Component.translatable("gui.dragonminez.radial.options")).copy().withStyle(style);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, title, px + pw / 2, py + 4, 16777215, 0);
      int rowsTop = py + 15;
      int textWidth = pw - 12 - (maxScroll > 0 ? 4 : 0);

      for (int r = 0; r < visibleRows; r++) {
         int i = start + r;
         RadialNode node = opts.get(i);
         int ry = rowsTop + r * 13;
         boolean isDragged = !this.panelScrollable && this.dragging && i == this.dragIndex;
         int drawY = isDragged ? (int)Math.round(this.dragCurrentY - 6.5) : ry;
         boolean hovered = !this.dragging && mouseX >= (double)px && mouseX <= (double)(px + pw) && mouseY >= (double)ry && mouseY < (double)(ry + 13);
         if (hovered) {
            graphics.fill(px + 1, ry, px + pw - 1, ry + 13 - 1, 1426063360);
         }

         if (isDragged) {
            graphics.fill(px + 1, drawY, px + pw - 1, drawY + 13 - 1, 872415231);
         }

         if (node.active(this.statsData)) {
            this.drawRowBorder(graphics, px + 1, drawY, px + pw - 1, drawY + 13 - 1, -12853158);
         }

         int color = node.labelColor(this.statsData);
         String text = this.font.plainSubstrByWidth(node.label(this.statsData).getString(), textWidth);
         TextUtil.drawStringWithBorder(graphics, this.font, Component.literal(text).withStyle(style), px + 6, drawY + 3, color, 0);
      }

      if (this.panelScrollable) {
         int trackX = px + pw - 3;
         int trackH = visibleRows * 13;
         this.panelBar.update(trackX, 2, rowsTop, trackH, (float)maxScroll);
         if (maxScroll > 0) {
            graphics.fill(trackX, rowsTop, trackX + 2, rowsTop + trackH, 1090519039);
            int thumbH = Math.max(6, trackH * visibleRows / opts.size());
            int thumbY = rowsTop + (trackH - thumbH) * this.panelScroll / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, -1056964609);
         }
      } else {
         this.panelBar.clear();
      }

      if (!this.panelScrollable && this.dragging && this.dragIndex >= 0) {
         int target = Mth.clamp(this.rowIndexAt(rowsTop, mouseY), 0, opts.size());
         int lineY = rowsTop + target * 13;
         graphics.fill(px + 2, lineY - 1, px + pw - 2, lineY, -1);
      }
   }

   private void drawRowBorder(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
      graphics.fill(x0, y0, x1, y0 + 1, color);
      graphics.fill(x0, y1 - 1, x1, y1, color);
      graphics.fill(x0, y0, x0 + 1, y1, color);
      graphics.fill(x1 - 1, y0, x1, y1, color);
   }

   private void reorderOption(int from, int to) {
      if (this.openMore != null) {
         List<RadialNode> opts = this.openMore.options();
         if (from >= 0 && from < opts.size() && from != to) {
            RadialNode moved = opts.remove(from);
            if (to > from) {
               to--;
            }

            to = Mth.clamp(to, 0, opts.size());
            opts.add(to, moved);
            List<String> keys = new ArrayList<>();
            boolean orderable = true;

            for (RadialNode node : opts) {
               String key = node.orderKey();
               if (key.isEmpty()) {
                  orderable = false;
                  break;
               }

               keys.add(key);
            }

            String categoryKey = this.openMore.categoryKey();
            if (orderable) {
               RadialLayoutStore.setOrder(categoryKey, keys);
               RadialLayoutStore.save();
            }

            this.buildBaseNodes();
            MoreNode refreshed = this.findMoreNode(categoryKey);
            if (refreshed != null) {
               this.openMore = refreshed;
               this.panelOptions = refreshed.options();
               this.refreshFrozenHoverFor(refreshed);
            }
         }
      }
   }

   private void refreshFrozenHoverFor(MoreNode target) {
      UtilityMenuScreen.Hover rebuilt = new UtilityMenuScreen.Hover();

      for (RadialNode base : this.baseNodes) {
         if (this.findPathTo(base, target, rebuilt.path)) {
            rebuilt.path.add(0, base);
            rebuilt.deepest = target;
            break;
         }
      }

      this.frozenHover = rebuilt;
   }

   private boolean findPathTo(RadialNode current, RadialNode target, List<RadialNode> outPath) {
      if (current == target) {
         return true;
      } else {
         for (RadialNode child : current.children(this.statsData)) {
            if (this.findPathTo(child, target, outPath)) {
               outPath.add(0, child);
               return true;
            }
         }

         return false;
      }
   }

   private MoreNode findMoreNode(String categoryKey) {
      for (RadialNode base : this.baseNodes) {
         MoreNode found = this.findMoreNodeRec(base, categoryKey);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private MoreNode findMoreNodeRec(RadialNode node, String categoryKey) {
      for (RadialNode child : node.children(this.statsData)) {
         if (child instanceof MoreNode more && more.categoryKey().equals(categoryKey)) {
            return more;
         }

         MoreNode deep = this.findMoreNodeRec(child, categoryKey);
         if (deep != null) {
            return deep;
         }
      }

      return null;
   }

   private float computeOpenScale() {
      long ms = System.currentTimeMillis();
      if (this.closing) {
         float p = Math.min(1.0F, (float)(ms - this.closeStartTime) / 140.0F);
         return easeOut(Math.max(0.0F, 1.0F - p));
      } else {
         return easeOut(Math.min(1.0F, (float)(ms - this.openTime) / 140.0F));
      }
   }

   private boolean isActiveSlot(RadialNode node) {
      return node.visible(this.statsData) && node.interactive(this.statsData);
   }

   private void drawBaseSectors(GuiGraphics graphics, float cx, float cy, UtilityMenuScreen.Hover hover, float openScale) {
      for (int i = 0; i < this.baseNodes.size(); i++) {
         RadialNode node = this.baseNodes.get(i);
         boolean active = this.isActiveSlot(node);
         float highlight = 0.0F;
         float hoverAmt = 0.0F;
         float[] color = PANEL_INACTIVE;
         if (active) {
            this.updateAnim(node, hover);
            if (node instanceof AbstractRadialNode a) {
               highlight = a.animHighlight;
               hoverAmt = a.animScale;
            }

            color = lerpColor(PANEL, PANEL_HOVER, Math.max(highlight * 0.5F, hoverAmt));
         }

         float center = baseCenter(i);
         float rIn = (40.0F - 2.0F * hoverAmt) * openScale;
         float rOut = (86.0F + 4.0F * hoverAmt) * openScale;
         float half = 21.0F;
         this.fillSector(graphics, cx, cy, rIn, rOut, center - half, center + half, color);
      }
   }

   private void drawBaseFaces(GuiGraphics graphics, float cx, float cy, float openScale) {
      float radius = 63.0F * openScale;

      for (int i = 0; i < this.baseNodes.size(); i++) {
         RadialNode node = this.baseNodes.get(i);
         if (this.isActiveSlot(node)) {
            float hoverAmt = node instanceof AbstractRadialNode a ? a.animScale : 0.0F;
            this.drawFace(graphics, cx, cy, node, baseCenter(i), radius, 16.0F, hoverAmt, 1.0F, 21.0F);
         }
      }
   }

   private void drawChildSectors(
      GuiGraphics graphics,
      float cx,
      float cy,
      RadialNode parent,
      float parentCenter,
      int level,
      UtilityMenuScreen.Hover hover,
      boolean facesPass,
      float openScale
   ) {
      float expand = parent instanceof AbstractRadialNode a ? a.animExpand : 0.0F;
      if (!(expand < 0.01F)) {
         List<RadialNode> vis = this.visibleChildren(parent);
         int k = vis.size();
         if (k != 0) {
            float t = easeOut(expand);
            float ringInner = (86.0F + (float)(level - 1) * 42.0F) * openScale;
            float ringOuter = ringInner + 42.0F * openScale * t;
            float faceRadius = (ringInner + ringOuter) / 2.0F;
            float half = childDrawnHalfDeg(level);

            for (int j = 0; j < k; j++) {
               RadialNode child = vis.get(j);
               float center = parentCenter + ((float)j - (float)(k - 1) / 2.0F) * childArcDeg(level);
               if (!facesPass) {
                  this.updateAnim(child, hover);
                  float highlight = child instanceof AbstractRadialNode ax ? ax.animHighlight : 0.0F;
                  float hoverAmt = child instanceof AbstractRadialNode a2 ? a2.animScale : 0.0F;
                  float[] color = lerpColor(CHILD_PANEL, CHILD_HOVER, Math.max(highlight * 0.5F, hoverAmt));
                  color = new float[]{color[0], color[1], color[2], color[3] * t};
                  float grow = 4.0F * hoverAmt;
                  this.fillSector(graphics, cx, cy, ringInner, ringOuter + grow, center - half, center + half, color);
               } else if (t >= 0.4F) {
                  float hoverAmt = child instanceof AbstractRadialNode ax ? ax.animScale : 0.0F;
                  this.drawFace(graphics, cx, cy, child, center, faceRadius, 14.0F, hoverAmt, t, childDrawnHalfDeg(level));
               }

               this.drawChildSectors(graphics, cx, cy, child, center, level + 1, hover, facesPass, openScale);
            }
         }
      }
   }

   private void fillSector(GuiGraphics graphics, float cx, float cy, float rIn, float rOut, float startDeg, float endDeg, float[] color) {
      if (!(rOut <= rIn) && !(color[3] <= 0.01F)) {
         Matrix4f mat = graphics.pose().last().pose();
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableCull();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         Tesselator tess = Tesselator.getInstance();
         BufferBuilder buf = tess.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
         int steps = Math.max(2, (int)Math.ceil((double)Math.abs(endDeg - startDeg) / 5.0));
         float r = color[0];
         float g = color[1];
         float b = color[2];
         float a = color[3];

         for (int s = 0; s < steps; s++) {
            double a0 = Math.toRadians((double)(startDeg + (endDeg - startDeg) * (float)s / (float)steps));
            double a1 = Math.toRadians((double)(startDeg + (endDeg - startDeg) * (float)(s + 1) / (float)steps));
            float cos0 = (float)Math.cos(a0);
            float sin0 = (float)Math.sin(a0);
            float cos1 = (float)Math.cos(a1);
            float sin1 = (float)Math.sin(a1);
            float ix0 = cx + cos0 * rIn;
            float iy0 = cy + sin0 * rIn;
            float ox0 = cx + cos0 * rOut;
            float oy0 = cy + sin0 * rOut;
            float ix1 = cx + cos1 * rIn;
            float iy1 = cy + sin1 * rIn;
            float ox1 = cx + cos1 * rOut;
            float oy1 = cy + sin1 * rOut;
            buf.addVertex(mat, ix0, iy0, 0.0F).setColor(r, g, b, a);
            buf.addVertex(mat, ox0, oy0, 0.0F).setColor(r, g, b, a);
            buf.addVertex(mat, ox1, oy1, 0.0F).setColor(r, g, b, a);
            buf.addVertex(mat, ix0, iy0, 0.0F).setColor(r, g, b, a);
            buf.addVertex(mat, ox1, oy1, 0.0F).setColor(r, g, b, a);
            buf.addVertex(mat, ix1, iy1, 0.0F).setColor(r, g, b, a);
         }

         BufferUploader.drawWithShader(buf.buildOrThrow());
         RenderSystem.enableCull();
         RenderSystem.disableBlend();
      }
   }

   private void drawFace(
      GuiGraphics graphics, float cx, float cy, RadialNode node, float angleDeg, float radius, float iconBase, float hoverAmt, float alpha, float sectorHalfDeg
   ) {
      double rad = Math.toRadians((double)angleDeg);
      float x = (float)((double)cx + Math.cos(rad) * (double)radius);
      float y = (float)((double)cy + Math.sin(rad) * (double)radius);
      int maxWidth = Math.max(28, Math.round(2.0F * radius * (float)Math.sin(Math.toRadians((double)sectorHalfDeg))));
      String faceText = node.faceText(this.statsData);
      if (faceText != null) {
         this.drawFaceText(graphics, faceText, x, y - 3.0F, iconBase, hoverAmt, node.labelColor(this.statsData), alpha);
      } else {
         this.drawIcon(graphics, node.icon(this.statsData), x, y - 3.0F, iconBase, hoverAmt, node.iconTint(this.statsData), alpha);
      }

      this.drawLabel(graphics, node, x, y - 3.0F, iconBase, hoverAmt, alpha, maxWidth);
   }

   private void drawFaceText(GuiGraphics graphics, String text, float x, float y, float iconBase, float hoverAmt, int color, float alpha) {
      if (!(alpha < 0.4F)) {
         float scale = 1.5F + 0.25F * hoverAmt;
         Style style = Style.EMPTY.withFont(DMZ_FONT);
         Component line = Component.literal(text).withStyle(style);
         graphics.pose().pushPose();
         graphics.pose().translate(x, y, 0.0F);
         graphics.pose().scale(scale, scale, 1.0F);
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, line, 0, -9 / 2, color, 0);
         graphics.pose().popPose();
      }
   }

   private void drawIcon(GuiGraphics graphics, ResourceLocation icon, float x, float y, float iconBase, float hoverAmt, int tint, float alpha) {
      if (icon != null) {
         float size = iconBase + 4.0F * hoverAmt;
         float half = size / 2.0F;
         ResourceLocation safe = DMZSkinLayer.getSafeTexture(icon);
         float r = 1.0F;
         float g = 1.0F;
         float b = 1.0F;
         if (tint >= 0) {
            r = (float)(tint >> 16 & 0xFF) / 255.0F;
            g = (float)(tint >> 8 & 0xFF) / 255.0F;
            b = (float)(tint & 0xFF) / 255.0F;
         }

         RenderSystem.enableBlend();
         RenderSystem.setShaderColor(r, g, b, alpha);
         graphics.blit(safe, Math.round(x - half), Math.round(y - half), Math.round(size), Math.round(size), 0.0F, 0.0F, 18, 18, 18, 18);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.disableBlend();
      }
   }

   private void drawLabel(GuiGraphics graphics, RadialNode node, float x, float y, float iconBase, float hoverAmt, float alpha, int maxWidth) {
      if (!(alpha < 0.4F)) {
         Component label = node.label(this.statsData);
         if (label != null && !label.getString().isEmpty()) {
            int color = node.labelColor(this.statsData);
            Style style = Style.EMPTY.withFont(DMZ_FONT);
            List<String> lines = TextUtil.wrap(this.font, label.getString(), maxWidth, style);
            int lineHeight = 9;
            int startY = Math.round(y + (iconBase + 4.0F * hoverAmt) / 2.0F + 2.0F);
            int centerX = Math.round(x);

            for (int i = 0; i < lines.size(); i++) {
               Component line = Component.literal(lines.get(i)).withStyle(style);
               TextUtil.drawCenteredStringWithBorder(graphics, this.font, line, centerX, startY + i * lineHeight, color, 0);
            }
         }
      }
   }

   private void updateAnim(RadialNode node, UtilityMenuScreen.Hover hover) {
      if (node instanceof AbstractRadialNode a) {
         boolean hovered = node == hover.deepest;
         boolean inPath = hover.path.contains(node);
         boolean lit = hovered || inPath;
         a.animHighlight = this.approach(a.animHighlight, lit ? 1.0F : 0.0F);
         a.animScale = this.approach(a.animScale, hovered ? 1.0F : 0.0F);
         a.animExpand = this.approach(a.animExpand, lit && node.expandable(this.statsData) ? 1.0F : 0.0F);
      }
   }

   private List<RadialNode> visibleChildren(RadialNode node) {
      List<RadialNode> out = new ArrayList<>();

      for (RadialNode child : node.children(this.statsData)) {
         if (child.visible(this.statsData)) {
            out.add(child);
         }
      }

      return out;
   }

   private UtilityMenuScreen.Hover resolveHover(float cx, float cy, double mouseX, double mouseY, float openScale) {
      UtilityMenuScreen.Hover result = new UtilityMenuScreen.Hover();
      double dx = mouseX - (double)cx;
      double dy = mouseY - (double)cy;
      double d = Math.sqrt(dx * dx + dy * dy);
      double ang = Math.toDegrees(Math.atan2(dy, dx));
      float inner = 40.0F * openScale;
      float outer = 86.0F * openScale;
      float band = 42.0F * openScale;
      if (d < (double)inner) {
         this.chain.clear();
         return result;
      } else {
         int level = d < (double)outer ? 0 : (int)(1.0 + Math.floor((d - (double)outer) / (double)band));
         if (level == 0) {
            int slot = baseSlotAt(ang);
            RadialNode node = this.baseNodes.get(slot);
            this.chain.clear();
            if (!this.isActiveSlot(node)) {
               return result;
            } else {
               this.chain.add(node);
               result.deepest = node;
               result.path.add(node);
               result.deepestAngleDeg = baseCenter(slot);
               result.deepestLevel = 0;
               return result;
            }
         } else {
            if (this.chain.isEmpty()) {
               int slot = baseSlotAt(ang);
               RadialNode node = this.baseNodes.get(slot);
               if (!this.isActiveSlot(node)) {
                  return result;
               }

               this.chain.add(node);
            }

            while (this.chain.size() > level + 1) {
               this.chain.remove(this.chain.size() - 1);
            }

            RadialNode base = this.chain.get(0);
            result.path.add(base);
            result.deepest = base;
            result.deepestAngleDeg = baseCenter(this.baseNodes.indexOf(base));
            result.deepestLevel = 0;
            float center = baseCenter(this.baseNodes.indexOf(base));
            RadialNode current = base;

            for (int l = 1; l <= level && current.expandable(this.statsData); l++) {
               List<RadialNode> vis = this.visibleChildren(current);
               int k = vis.size();
               if (k == 0) {
                  break;
               }

               int idx;
               if (l == level) {
                  idx = this.pickChildByAngle(vis, center, ang, l);
                  if (idx < 0) {
                     break;
                  }

                  if (this.chain.size() > l) {
                     this.chain.set(l, vis.get(idx));
                  } else {
                     this.chain.add(vis.get(idx));
                  }
               } else {
                  RadialNode committed = l < this.chain.size() ? this.chain.get(l) : null;
                  idx = committed != null ? vis.indexOf(committed) : -1;
                  if (idx < 0) {
                     idx = this.pickChildByAngle(vis, center, ang, l);
                     if (idx < 0) {
                        break;
                     }

                     if (l < this.chain.size()) {
                        this.chain.set(l, vis.get(idx));
                     } else {
                        this.chain.add(vis.get(idx));
                     }
                  }
               }

               RadialNode child = vis.get(idx);
               center += ((float)idx - (float)(k - 1) / 2.0F) * childArcDeg(l);
               result.path.add(child);
               result.deepest = child;
               result.deepestAngleDeg = center;
               result.deepestLevel = l;
               current = child;
            }

            while (this.chain.size() > result.path.size()) {
               this.chain.remove(this.chain.size() - 1);
            }

            return result;
         }
      }
   }

   private int pickChildByAngle(List<RadialNode> children, float parentCenter, double ang, int level) {
      int k = children.size();
      float arc = childArcDeg(level);
      float best = arc / 2.0F;
      int bestIdx = -1;

      for (int j = 0; j < k; j++) {
         float center = parentCenter + ((float)j - (float)(k - 1) / 2.0F) * arc;
         float diff = (float)absAngleDiff(ang, (double)center);
         if (diff < best) {
            best = diff;
            bestIdx = j;
         }
      }

      return bestIdx;
   }

   private static float baseArcLength() {
      return 63.0F * (float)Math.toRadians(45.0);
   }

   private static float childArcDeg(int level) {
      float midRadius = 86.0F + (float)(level - 1) * 42.0F + 21.0F;
      float fullDeg = (float)Math.toDegrees((double)(1.25F * baseArcLength() / midRadius));
      return Math.min(fullDeg, 45.0F);
   }

   private static float childDrawnHalfDeg(int level) {
      return childArcDeg(level) / 2.0F - 1.5F;
   }

   private static int baseSlotAt(double angleDeg) {
      return Math.floorMod(Math.round((float)((angleDeg + 90.0) / 45.0)), 8);
   }

   private static float baseCenter(int index) {
      return -90.0F + (float)index * 45.0F;
   }

   private static double absAngleDiff(double a, double b) {
      double d = ((a - b) % 360.0 + 540.0) % 360.0 - 180.0;
      return Math.abs(d);
   }

   private float approach(float current, float target) {
      float step = Math.min(1.0F, this.frameDt * 14.0F);
      return current + (target - current) * step;
   }

   private static float easeOut(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private static float[] lerpColor(float[] a, float[] b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, a[3] + (b[3] - a[3]) * t};
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.closing && this.statsData != null) {
         double ux = this.toUiX(mouseX);
         double uy = this.toUiY(mouseY);
         float cx = (float)this.getUiWidth() / 2.0F;
         float cy = (float)this.getUiHeight() / 2.0F;
         if (this.panelOptions == null) {
            UtilityMenuScreen.Hover hover = this.resolveHover(cx, cy, ux, uy, this.computeOpenScale());
            RadialNode node = hover.deepest;
            if (node instanceof MoreNode more) {
               this.openPanel(more.options(), Component.translatable("gui.dragonminez.radial.options"), hover, false);
               this.openMore = more;
               return true;
            } else if (node instanceof ReleaseNode release) {
               if (release.active(this.statsData)) {
                  release.onSelect(this.statsData);
                  return true;
               } else {
                  this.openPanel(release.buildOptions(this.statsData), release.label(this.statsData), hover, true);
                  return true;
               }
            } else {
               if (node instanceof FormSelectNode form && form.interactive(this.statsData)) {
                  this.selectNode(form);
                  return true;
               }

               if (node != null && node.interactive(this.statsData) && !node.expandable(this.statsData)) {
                  node.onSelect(this.statsData);
                  return true;
               } else {
                  return true;
               }
            }
         } else {
            int[] b = this.panelBounds(cx, cy);
            boolean inside = ux >= (double)b[0] && ux <= (double)(b[0] + b[2]) && uy >= (double)b[1] && uy <= (double)(b[1] + b[3]);
            if (!inside) {
               this.closePanel();
               return true;
            } else {
               int rowsTop = b[1] + 15;
               if (!this.panelScrollable) {
                  int idx = this.rowIndexAt(rowsTop, uy);
                  if (idx >= 0 && idx < this.panelOptions.size()) {
                     this.dragIndex = idx;
                     this.dragStartY = uy;
                     this.dragCurrentY = uy;
                     this.dragging = false;
                  }

                  return true;
               } else if (this.panelBar.tryStartDrag(ux, uy)) {
                  this.panelScroll = Math.round(this.panelBar.scrollFor(uy));
                  return true;
               } else {
                  int rel = this.rowIndexAt(rowsTop, uy);
                  int i = this.panelScroll + rel;
                  if (rel >= 0 && rel < this.visiblePanelRows() && i < this.panelOptions.size()) {
                     this.selectNode(this.panelOptions.get(i));
                  }

                  return true;
               }
            }
         }
      } else {
         return true;
      }
   }

   private void openPanel(List<RadialNode> options, Component title, UtilityMenuScreen.Hover hover, boolean scrollable) {
      this.panelOptions = options;
      this.panelTitle = title;
      this.panelScrollable = scrollable;
      this.panelScroll = 0;
      this.panelAngleDeg = hover.deepestAngleDeg;
      this.panelLevel = hover.deepestLevel;
      this.frozenHover = hover;
   }

   private void selectNode(RadialNode node) {
      if (node != null && node.interactive(this.statsData)) {
         long now = System.currentTimeMillis();
         boolean doubleClick = node == this.lastClickNode && now - this.lastClickMs <= 300L;
         this.lastClickNode = node;
         this.lastClickMs = doubleClick ? 0L : now;
         if (doubleClick) {
            node.onDoubleSelect(this.statsData);
         } else {
            node.onSelect(this.statsData);
         }
      }
   }

   private void closePanel() {
      this.panelOptions = null;
      this.panelTitle = null;
      this.panelScrollable = false;
      this.panelScroll = 0;
      this.openMore = null;
      this.dragIndex = -1;
      this.dragging = false;
      this.panelBar.stopDrag();
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.panelBar.isDragging()) {
         this.panelScroll = Math.round(this.panelBar.scrollFor(this.toUiY(mouseY)));
         return true;
      } else if (this.panelOptions != null && !this.panelScrollable && this.dragIndex >= 0) {
         double uy = this.toUiY(mouseY);
         if (Math.abs(uy - this.dragStartY) > 3.0) {
            this.dragging = true;
         }

         this.dragCurrentY = uy;
         return true;
      } else {
         return true;
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.panelBar.isDragging()) {
         this.panelBar.stopDrag();
         return true;
      } else if (this.panelOptions != null && !this.panelScrollable && this.dragIndex >= 0) {
         double uy = this.toUiY(mouseY);
         if (!this.dragging) {
            this.selectNode(this.panelOptions.get(this.dragIndex));
         } else {
            int[] b = this.panelBounds((float)this.getUiWidth() / 2.0F, (float)this.getUiHeight() / 2.0F);
            int rowsTop = b[1] + 15;
            int target = Mth.clamp(this.rowIndexAt(rowsTop, uy), 0, this.panelOptions.size());
            this.reorderOption(this.dragIndex, target);
         }

         this.dragIndex = -1;
         this.dragging = false;
         return true;
      } else {
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.panelOptions != null && this.panelScrollable) {
         int maxScroll = Math.max(0, this.panelOptions.size() - this.visiblePanelRows());
         this.panelScroll = Mth.clamp(this.panelScroll - (int)Math.signum(scrollY), 0, maxScroll);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == KeyBinds.UTILITY_MENU.getKey().getValue()) {
         return true;
      } else if (keyCode == 256) {
         if (this.panelOptions != null) {
            this.closePanel();
            return true;
         } else {
            this.onClose();
            return true;
         }
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public void tick() {
      super.tick();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, mc.player).ifPresent(data -> this.statsData = data);
      }

      if (this.closing && System.currentTimeMillis() - this.closeStartTime >= 140L) {
         this.forceClose();
      }
   }

   public void onClose() {
      this.startClosingAnimation();
   }

   public void startClosingAnimation() {
      if (!this.closing) {
         this.closing = true;
         this.closeStartTime = System.currentTimeMillis();
      }
   }

   private void forceClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(null);
      }
   }

   public static boolean isUtilityMenuReopenBlocked() {
      return System.currentTimeMillis() < utilityMenuReopenBlockedUntilMs;
   }

   public static void initMenuSlots() {
   }

   public static void addMenuSlot(IUtilityMenuSlot menuSlot) {
      ADDON_SLOTS.add(menuSlot);
   }

   private static final class Hover {
      private final List<RadialNode> path = new ArrayList<>();
      private RadialNode deepest = null;
      private float deepestAngleDeg = 0.0F;
      private int deepestLevel = 0;
   }
}
