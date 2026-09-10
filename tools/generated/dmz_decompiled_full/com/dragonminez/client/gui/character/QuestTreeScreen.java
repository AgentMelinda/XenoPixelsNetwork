package com.dragonminez.client.gui.character;

import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.BaseMenuScreen;
import com.dragonminez.client.gui.quest.QuestTreeLayoutHelper;
import com.dragonminez.client.gui.quest.preview.QuestEnemyPreview;
import com.dragonminez.client.util.LocalizationUtil;
import com.dragonminez.client.util.ScrollbarState;
import com.dragonminez.client.util.TextUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.C2S.AcceptPartyInviteC2S;
import com.dragonminez.common.network.C2S.ClaimAllQuestRewardsC2S;
import com.dragonminez.common.network.C2S.ClaimQuestRewardC2S;
import com.dragonminez.common.network.C2S.InvitePartyMemberC2S;
import com.dragonminez.common.network.C2S.LeavePartyC2S;
import com.dragonminez.common.network.C2S.QuestActionC2S;
import com.dragonminez.common.network.C2S.RejectPartyInviteC2S;
import com.dragonminez.common.network.C2S.SetStoryDifficultyC2S;
import com.dragonminez.common.network.C2S.SetTrackedQuestC2S;
import com.dragonminez.common.quest.Difficulty;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestAvailabilityChecker;
import com.dragonminez.common.quest.QuestObjective;
import com.dragonminez.common.quest.QuestPrerequisites;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.QuestReward;
import com.dragonminez.common.quest.QuestTextFormatter;
import com.dragonminez.common.quest.Saga;
import com.dragonminez.common.quest.objectives.KillObjective;
import com.dragonminez.common.quest.rewards.GenericItemReward;
import com.dragonminez.common.quest.rewards.ItemReward;
import com.dragonminez.common.quest.rewards.TransformationReward;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class QuestTreeScreen extends BaseMenuScreen {
   private static final ResourceLocation QUEST_MENU = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/questmenu.png");
   private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/buttons/characterbuttons.png");
   private static final ResourceLocation EXCLAMATION_MARK = ResourceLocation.fromNamespaceAndPath(
      "dragonminez", "textures/gui/quest/exclamation_mark_quest.png"
   );
   private static final ResourceLocation REWARD_GENERIC_ICON = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/quest/reward_generic.png");
   private static final Style DMZ_STYLE = Style.EMPTY.withFont(DMZ_FONT);
   private static final int NODE_SIZE = 18;
   private StatsData statsData;
   private int tickCount = 0;
   private int pendingRefreshTicks = 0;
   private long lastRenderTime = 0L;
   private int hardModeHitX;
   private int hardModeHitY;
   private int hardModeHitW;
   private int hardModeHitH;
   private boolean hardModeToggleable = false;
   private boolean hardModeIndicatorShown = false;
   private TexturedTextButton actionButton;
   private TexturedTextButton claimAllButton;
   private TexturedTextButton partyPrimaryButton;
   private TexturedTextButton partySecondaryButton;
   private List<Component> actionButtonTooltip = List.of();
   private long lastClickTime = 0L;
   private String pendingStartCloseKey = null;
   private int pendingStartCloseTicks = 0;
   private int currentSagaIndex = 0;
   private final List<Saga> availableSagas = new ArrayList<>();
   private static String persistedSagaId = null;
   private static String persistedQuestKey = null;
   private QuestTreeLayoutHelper.TreeLayout currentLayout;
   private Quest selectedQuest = null;
   private final QuestEnemyPreview enemyPreview = new QuestEnemyPreview();
   private float panX = 0.0F;
   private float panY = 0.0F;
   private boolean isDraggingTree = false;
   private double dragStartX;
   private double dragStartY;
   private float dragStartPanX;
   private float dragStartPanY;
   private float targetPanX = 0.0F;
   private float targetPanY = 0.0F;
   private boolean isAnimatingPan = false;
   private long lastPanAnimNanos = 0L;
   private float zoom = 1.0F;
   private final List<QuestTreeScreen.NavigatorEntry> navigatorEntries = new ArrayList<>();
   private final Set<String> expandedSideBranches = new HashSet<>();
   private float targetNavScroll = 0.0F;
   private float currentNavScroll = 0.0F;
   private float navMaxScroll = 0.0F;
   private float targetDescScroll = 0.0F;
   private float currentDescScroll = 0.0F;
   private float descMaxScroll = 0.0F;
   private float targetObjScroll = 0.0F;
   private float currentObjScroll = 0.0F;
   private float objMaxScroll = 0.0F;
   private float targetRewardsScroll = 0.0F;
   private float currentRewardsScroll = 0.0F;
   private float rewardsMaxScroll = 0.0F;
   private float diffIntroScroll = 0.0F;
   private float diffIntroMaxScroll = 0.0F;
   private final float[] diffOptScroll = new float[3];
   private final float[] diffOptMaxScroll = new float[3];
   private final ScrollbarState navBar = new ScrollbarState();
   private final ScrollbarState descBar = new ScrollbarState();
   private final ScrollbarState objBar = new ScrollbarState();
   private final ScrollbarState rewardsBar = new ScrollbarState();
   private final ScrollbarState diffIntroBar = new ScrollbarState();
   private final ScrollbarState[] diffOptBars = new ScrollbarState[]{new ScrollbarState(), new ScrollbarState(), new ScrollbarState()};
   private List<String> frameObjLinesCache = null;
   private Quest frameObjLinesQuest = null;
   private int frameObjLinesWidth = Integer.MIN_VALUE;
   private List<String> frameTitleLines = null;
   private Quest frameTitleQuest = null;
   private int frameTitleWidth = Integer.MIN_VALUE;
   private final Map<Quest, QuestTreeScreen.NodeVisibility> nodeVisibilityCache = new HashMap<>();
   private final Map<Quest, QuestTreeScreen.QuestNodeStatus> nodeStatusCache = new HashMap<>();
   private Saga sideBranchCacheSaga = null;
   private Map<String, List<Quest>> sideBranchCache = null;
   private final List<QuestTreeScreen.NodeRender> nodeRenders = new ArrayList<>();
   private final List<QuestTreeScreen.ConnRender> connRenders = new ArrayList<>();
   private final List<QuestTreeScreen.RewardHitbox> rewardHitboxes = new ArrayList<>();
   private final Map<String, Long> sectionLastReveal = new HashMap<>();
   private final Map<String, Long> sectionAnimationStart = new HashMap<>();
   private long panelIntroStartMs = 0L;
   private boolean panelIntroActive = false;
   private float leftPanelRevealProgress = 0.0F;
   private float rightPanelRevealProgress = 0.0F;
   private boolean treePressStarted = false;
   private boolean treePressMoved = false;
   private double treePressStartX = 0.0;
   private double treePressStartY = 0.0;
   private boolean invitePopupOpen = false;
   private int invitePopupScroll = 0;
   private final List<QuestTreeScreen.PartyInviteEntry> inviteEntries = new ArrayList<>();
   private boolean confirmOverlayOpen = false;
   private QuestTreeScreen.PartyConfirmAction confirmAction = QuestTreeScreen.PartyConfirmAction.NONE;
   private Component confirmTitle = Component.empty();
   private Component confirmBody = Component.empty();
   private static final List<QuestTreeScreen.SagaCatalogEntry> SAGA_CATALOG = List.of(
      new QuestTreeScreen.SagaCatalogEntry("saiyan_saga", "Saiyan Saga", false),
      new QuestTreeScreen.SagaCatalogEntry("frieza_saga", "Frieza Saga", false),
      new QuestTreeScreen.SagaCatalogEntry("android_saga", "Cell Saga", false),
      new QuestTreeScreen.SagaCatalogEntry("future_saga", "Future Saga", false),
      new QuestTreeScreen.SagaCatalogEntry("buu_saga", "Buu Saga", false),
      new QuestTreeScreen.SagaCatalogEntry("movies_saga", "Movies Saga", false),
      new QuestTreeScreen.SagaCatalogEntry("daima_saga", "Daima Saga", true),
      new QuestTreeScreen.SagaCatalogEntry("gt_saga", "GT Saga", true),
      new QuestTreeScreen.SagaCatalogEntry("dball_saga", "DBall Saga", true),
      new QuestTreeScreen.SagaCatalogEntry("beerus_saga", "Beerus Saga", true),
      new QuestTreeScreen.SagaCatalogEntry("rof_saga", "RoF Saga", true),
      new QuestTreeScreen.SagaCatalogEntry("u7vsu6_saga", "U7vsU6 Saga", true)
   );
   private static final Map<String, Integer> SAGA_UI_ORDER = Map.ofEntries(
      Map.entry("saiyan_saga", 0),
      Map.entry("frieza_saga", 1),
      Map.entry("android_saga", 2),
      Map.entry("cell_saga", 2),
      Map.entry("future_saga", 3),
      Map.entry("buu_saga", 4),
      Map.entry("movies_saga", 5),
      Map.entry("daima_saga", 6),
      Map.entry("gt_saga", 7),
      Map.entry("dball_saga", 8),
      Map.entry("beerus_saga", 9),
      Map.entry("rof_saga", 10),
      Map.entry("u7vsu6_saga", 11)
   );
   private static final long RESUMMON_COOLDOWN_MS = 60000L;
   private static final Map<String, Long> resummonReadyAt = new HashMap<>();
   private static final Difficulty[] DIFFICULTY_OPTIONS = new Difficulty[]{Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};

   public QuestTreeScreen() {
      super(Component.translatable("gui.dragonminez.quest_tree.title"));
   }

   private boolean isReachableNavigatorQuest(Quest quest) {
      return this.getNodeVisibility(quest) == QuestTreeScreen.NodeVisibility.VISIBLE && this.getNodeStatus(quest) != QuestTreeScreen.QuestNodeStatus.LOCKED;
   }

   @Override
   protected void init() {
      super.init();
      this.startPanelIntroAnimation();
      this.updateStatsData();
      this.loadAvailableSagas();
      this.restorePersistedSagaIndex();
      this.rebuildLayout();
      this.restorePersistedQuestSelection();
      this.rebuildNavigatorEntries();
      this.scrollNavigatorToSelected();
      this.refreshButtons();
   }

   private void restorePersistedSagaIndex() {
      if (persistedSagaId != null && !this.availableSagas.isEmpty()) {
         for (int i = 0; i < this.availableSagas.size(); i++) {
            if (this.availableSagas.get(i).getId().equals(persistedSagaId)) {
               this.currentSagaIndex = i;
               return;
            }
         }
      }
   }

   private void restorePersistedQuestSelection() {
      if (persistedQuestKey != null && !this.availableSagas.isEmpty()) {
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         Quest found = this.findQuestByProgressKey(saga, persistedQuestKey);
         if (found != null) {
            this.selectedQuest = found;
            this.expandSideBranchTo(saga, found);
            QuestTreeLayoutHelper.NodePosition node = this.findNodeForQuest(found);
            if (node != null) {
               QuestTreeScreen.PanelRect tree = this.getTreePanelRect();
               this.panX = (float)tree.x + (float)tree.width / 2.0F - (float)node.getPixelX() * this.zoom - 9.0F * this.zoom;
               this.panY = (float)tree.y + (float)tree.height / 2.0F - (float)node.getPixelY() * this.zoom - 9.0F * this.zoom;
            }
         }
      }
   }

   private Quest findQuestByProgressKey(Saga saga, String key) {
      if (saga != null && key != null) {
         for (Quest q : saga.getQuests()) {
            if (this.questProgressKey(saga, q).equals(key)) {
               return q;
            }
         }

         for (List<Quest> branch : this.buildSideBranchesForSaga(saga).values()) {
            for (Quest qx : branch) {
               if (this.questProgressKey(saga, qx).equals(key)) {
                  return qx;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void expandSideBranchTo(Saga saga, Quest target) {
      if (saga != null && target != null && target.isSideQuest() && this.currentLayout != null) {
         Map<String, Quest> childKeyToParent = new HashMap<>();

         for (QuestTreeLayoutHelper.NodeConnection connection : this.currentLayout.getConnections()) {
            Quest to = connection.getTo().getQuest();
            if (to.isSideQuest()) {
               childKeyToParent.put(this.questProgressKey(saga, to), connection.getFrom().getQuest());
            }
         }

         Quest current = target;
         Set<String> guard = new HashSet<>();

         while (current != null && current.isSideQuest() && guard.add(this.questProgressKey(saga, current))) {
            Quest parent = childKeyToParent.get(this.questProgressKey(saga, current));
            if (parent == null) {
               break;
            }

            this.expandedSideBranches.add(this.questProgressKey(saga, parent));
            current = parent;
         }
      }
   }

   private void scrollNavigatorToSelected() {
      if (this.selectedQuest != null) {
         for (int i = 0; i < this.navigatorEntries.size(); i++) {
            QuestTreeScreen.NavigatorEntry entry = this.navigatorEntries.get(i);
            if (entry.quest() != null && this.sameQuestIdentity(entry.quest(), this.selectedQuest)) {
               this.targetNavScroll = Mth.clamp((float)(i * 13 - 40), 0.0F, this.navMaxScroll);
               this.currentNavScroll = this.targetNavScroll;
               return;
            }
         }
      }
   }

   private void persistSelection() {
      if (!this.availableSagas.isEmpty() && this.currentSagaIndex < this.availableSagas.size()) {
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         persistedSagaId = saga.getId();
         persistedQuestKey = this.selectedQuest != null ? this.questProgressKey(saga, this.selectedQuest) : null;
      } else {
         persistedSagaId = null;
         persistedQuestKey = null;
      }
   }

   private void loadAvailableSagas() {
      this.availableSagas.clear();
      if (this.statsData != null) {
         Map<String, Saga> allSagas = QuestRegistry.getClientSagas();
         if (!allSagas.isEmpty()) {
            this.availableSagas.addAll(allSagas.values());
            this.availableSagas.sort((s1, s2) -> {
               int o1 = SAGA_UI_ORDER.getOrDefault(s1.getId(), Integer.MAX_VALUE);
               int o2 = SAGA_UI_ORDER.getOrDefault(s2.getId(), Integer.MAX_VALUE);
               return o1 != o2 ? Integer.compare(o1, o2) : s1.getId().compareToIgnoreCase(s2.getId());
            });
            if (this.currentSagaIndex >= this.availableSagas.size()) {
               this.currentSagaIndex = Math.max(0, this.availableSagas.size() - 1);
            }
         }
      }
   }

   private void rebuildLayout() {
      if (!this.availableSagas.isEmpty() && this.currentSagaIndex < this.availableSagas.size()) {
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         this.currentLayout = QuestTreeLayoutHelper.computeLayout(saga);
         this.centerViewOnProgress();
      } else {
         this.currentLayout = null;
         this.selectedQuest = null;
      }
   }

   private void centerViewOnProgress() {
      if (this.currentLayout != null) {
         QuestTreeScreen.PanelRect tree = this.getTreePanelRect();
         this.zoom = 1.0F;
         QuestTreeLayoutHelper.NodePosition targetNode = null;
         if (this.statsData != null && !this.availableSagas.isEmpty()) {
            Saga saga = this.availableSagas.get(this.currentSagaIndex);
            PlayerQuestData pqd = this.statsData.getPlayerQuestData();

            for (Quest q : saga.getQuests()) {
               boolean completed = pqd.isQuestCompleted(PlayerQuestData.sagaQuestKey(saga.getId(), q.getId()));

               for (QuestTreeLayoutHelper.NodePosition node : this.currentLayout.getNodes()) {
                  if (node.getQuest().getId() == q.getId() && !node.isSidequest()) {
                     targetNode = node;
                     if (!completed) {
                        break;
                     }
                  }
               }

               if (!completed) {
                  break;
               }
            }

            if (targetNode == null && !this.currentLayout.getNodes().isEmpty()) {
               targetNode = this.currentLayout.getNodes().get(0);
            }
         }

         if (targetNode != null) {
            this.panX = (float)tree.x + (float)tree.width / 2.0F - (float)targetNode.getPixelX() - 9.0F;
            this.panY = (float)tree.y + (float)tree.height / 2.0F - (float)targetNode.getPixelY() - 9.0F;
         } else {
            this.panX = (float)(tree.x + 40);
            this.panY = (float)tree.y + (float)(tree.height - this.currentLayout.getTotalHeight()) / 2.0F;
         }
      }
   }

   private void slideToNode(QuestTreeLayoutHelper.NodePosition node) {
      QuestTreeScreen.PanelRect tree = this.getTreePanelRect();
      this.targetPanX = (float)tree.x + (float)tree.width / 2.0F - (float)node.getPixelX() * this.zoom - 9.0F * this.zoom;
      this.targetPanY = (float)tree.y + (float)tree.height / 2.0F - (float)node.getPixelY() * this.zoom - 9.0F * this.zoom;
      this.isAnimatingPan = true;
      this.lastPanAnimNanos = System.nanoTime();
   }

   private void invalidateNodeCaches() {
      this.nodeVisibilityCache.clear();
      this.nodeStatusCache.clear();
      this.sideBranchCacheSaga = null;
      this.sideBranchCache = null;
   }

   private void rebuildTreeRenderData() {
      this.nodeRenders.clear();
      this.connRenders.clear();
      if (this.currentLayout != null) {
         for (QuestTreeLayoutHelper.NodeConnection conn : this.currentLayout.getConnections()) {
            QuestTreeScreen.ConnRender cr = this.buildConnRender(conn);
            if (cr != null) {
               this.connRenders.add(cr);
            }
         }

         for (QuestTreeLayoutHelper.NodePosition node : this.currentLayout.getNodes()) {
            QuestTreeScreen.NodeRender nr = this.buildNodeRender(node);
            if (nr != null) {
               this.nodeRenders.add(nr);
            }
         }
      }
   }

   private QuestTreeScreen.ConnRender buildConnRender(QuestTreeLayoutHelper.NodeConnection conn) {
      QuestTreeScreen.NodeVisibility fromVis = this.getNodeVisibility(conn.getFrom().getQuest());
      QuestTreeScreen.NodeVisibility toVis = this.getNodeVisibility(conn.getTo().getQuest());
      if (fromVis != QuestTreeScreen.NodeVisibility.HIDDEN && toVis != QuestTreeScreen.NodeVisibility.HIDDEN) {
         int x1 = conn.getFrom().getPixelX() + 9;
         int y1 = conn.getFrom().getPixelY() + 9;
         int x2 = conn.getTo().getPixelX() + 9;
         int y2 = conn.getTo().getPixelY() + 9;
         int color = -12303292;
         if (this.statsData != null && !this.availableSagas.isEmpty()) {
            Saga saga = this.availableSagas.get(this.currentSagaIndex);
            PlayerQuestData pqd = this.statsData.getPlayerQuestData();
            if (this.isQuestCompleted(pqd, saga, conn.getFrom().getQuest()) && this.isQuestCompleted(pqd, saga, conn.getTo().getQuest())) {
               color = -16733696;
            }
         }

         if (fromVis == QuestTreeScreen.NodeVisibility.BLURRED || toVis == QuestTreeScreen.NodeVisibility.BLURRED) {
            color = 1430537284;
         }

         return new QuestTreeScreen.ConnRender(x1, y1, x2, y2, color);
      } else {
         return null;
      }
   }

   private QuestTreeScreen.NodeRender buildNodeRender(QuestTreeLayoutHelper.NodePosition node) {
      QuestTreeScreen.NodeVisibility vis = this.getNodeVisibility(node.getQuest());
      if (vis == QuestTreeScreen.NodeVisibility.HIDDEN) {
         return null;
      } else {
         boolean blurred = vis == QuestTreeScreen.NodeVisibility.BLURRED;
         QuestTreeScreen.QuestNodeStatus status = this.getNodeStatus(node.getQuest());
         int bgColor;
         int borderColor;
         if (blurred) {
            bgColor = 1431655765;
            borderColor = 1429418803;
         } else {
            switch (status) {
               case AVAILABLE:
                  bgColor = -13312;
                  borderColor = -3368704;
                  break;
               case ACTIVE:
                  bgColor = -13395457;
                  borderColor = -14522676;
                  break;
               case COMPLETED:
               case CLAIMABLE:
                  bgColor = -16724992;
                  borderColor = -16738048;
                  break;
               default:
                  bgColor = -11184811;
                  borderColor = -13421773;
            }
         }

         String icon;
         int iconColor;
         boolean bold;
         if (blurred) {
            icon = "?";
            iconColor = 1436129689;
            bold = false;
         } else {
            switch (status) {
               case AVAILABLE:
                  icon = "?";
                  iconColor = -1;
                  bold = false;
                  break;
               case ACTIVE:
                  icon = "!";
                  iconColor = -256;
                  bold = true;
                  break;
               case COMPLETED:
               case CLAIMABLE:
                  icon = "✓";
                  iconColor = -11141291;
                  bold = true;
                  break;
               default:
                  icon = "✕";
                  iconColor = -43691;
                  bold = true;
            }
         }

         Component iconComp = bold ? Component.literal(icon).withStyle(ChatFormatting.BOLD) : Component.literal(icon);
         int iconOffsetX = (18 - this.font.width(icon)) / 2;
         Component bottomLabel;
         int bottomLabelColor;
         int bottomLabelOffsetX;
         if (blurred) {
            String hiddenLabel = "???";
            bottomLabel = this.txt(hiddenLabel);
            bottomLabelColor = 1435011208;
            bottomLabelOffsetX = (18 - TextUtil.width(this.font, hiddenLabel, DMZ_STYLE)) / 2;
         } else if (node.isSidequest()) {
            bottomLabel = null;
            bottomLabelColor = 0;
            bottomLabelOffsetX = 0;
         } else {
            String questNum = String.valueOf(node.getQuest().getId());
            bottomLabel = this.txt(questNum);
            bottomLabelColor = -3355444;
            bottomLabelOffsetX = (18 - TextUtil.width(this.font, questNum, DMZ_STYLE)) / 2;
         }

         return new QuestTreeScreen.NodeRender(
            node.getQuest(),
            node.getPixelX(),
            node.getPixelY(),
            blurred,
            node.isSidequest(),
            status,
            bgColor,
            borderColor,
            iconComp,
            iconColor,
            iconOffsetX,
            bottomLabel,
            bottomLabelColor,
            bottomLabelOffsetX
         );
      }
   }

   private void rebuildNavigatorEntries() {
      this.invalidateNodeCaches();
      this.navigatorEntries.clear();
      Map<String, Saga> loadedSagas = new LinkedHashMap<>();

      for (Saga saga : this.availableSagas) {
         loadedSagas.put(saga.getId(), saga);
      }

      List<String> displayedSagaIds = new ArrayList<>();
      Saga currentSaga = this.availableSagas.isEmpty() ? null : this.availableSagas.get(this.currentSagaIndex);

      for (QuestTreeScreen.SagaCatalogEntry entry : SAGA_CATALOG) {
         Saga saga = loadedSagas.get(entry.id());
         if (saga == null && "android_saga".equals(entry.id())) {
            saga = loadedSagas.get("cell_saga");
         }

         if (saga != null) {
            this.navigatorEntries
               .add(new QuestTreeScreen.NavigatorEntry(QuestTreeScreen.NavEntryType.SAGA, 0, saga, null, saga.getId(), this.getSagaDisplayName(saga), false));
            displayedSagaIds.add(saga.getId());
            if (currentSaga != null && currentSaga.getId().equals(saga.getId())) {
               this.appendCurrentSagaQuestEntries(currentSaga);
            }
         } else {
            this.navigatorEntries
               .add(new QuestTreeScreen.NavigatorEntry(QuestTreeScreen.NavEntryType.SAGA, 0, null, null, entry.id(), entry.label(), entry.comingSoon()));
            displayedSagaIds.add(entry.id());
         }
      }

      for (Saga sagax : this.availableSagas) {
         if (!displayedSagaIds.contains(sagax.getId())) {
            this.navigatorEntries
               .add(new QuestTreeScreen.NavigatorEntry(QuestTreeScreen.NavEntryType.SAGA, 0, sagax, null, sagax.getId(), this.getSagaDisplayName(sagax), false));
            if (currentSaga != null && currentSaga.getId().equals(sagax.getId())) {
               this.appendCurrentSagaQuestEntries(currentSaga);
            }
         }
      }

      QuestTreeScreen.PanelRect left = this.getLeftPanelRect();
      int usableHeight = Math.max(32, left.height - 40 - this.getPartyFooterHeight());
      int totalNavHeight = this.navigatorEntries.size() * 13;
      this.navMaxScroll = (float)Math.max(0, totalNavHeight - usableHeight);
      this.targetNavScroll = Math.max(0.0F, Math.min(this.targetNavScroll, this.navMaxScroll));
      this.rebuildTreeRenderData();
   }

   private Map<String, List<Quest>> buildSideBranchesForSaga(Saga saga) {
      if (this.sideBranchCache != null && this.sideBranchCacheSaga == saga) {
         return this.sideBranchCache;
      } else {
         Map<String, List<Quest>> result = this.computeSideBranchesForSaga(saga);
         this.sideBranchCache = result;
         this.sideBranchCacheSaga = saga;
         return result;
      }
   }

   private Map<String, List<Quest>> computeSideBranchesForSaga(Saga saga) {
      Map<String, List<Quest>> byParent = new LinkedHashMap<>();
      if (this.currentLayout != null && saga != null) {
         Map<String, Quest> nodeByKey = new HashMap<>();

         for (QuestTreeLayoutHelper.NodePosition node : this.currentLayout.getNodes()) {
            nodeByKey.put(this.questProgressKey(saga, node.getQuest()), node.getQuest());
         }

         for (QuestTreeLayoutHelper.NodeConnection connection : this.currentLayout.getConnections()) {
            Quest to = connection.getTo().getQuest();
            if (to.isSideQuest()) {
               Quest from = connection.getFrom().getQuest();
               String parentKey = this.questProgressKey(saga, from);
               byParent.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(to);
            }
         }

         for (List<Quest> sideList : byParent.values()) {
            sideList.sort((a, b) -> {
               String aKey = a.getStringId() != null ? a.getStringId() : "";
               String bKey = b.getStringId() != null ? b.getStringId() : "";
               return aKey.compareTo(bKey);
            });
         }

         return byParent;
      } else {
         return byParent;
      }
   }

   private void addSideBranchEntries(Quest parentQuest, Map<String, List<Quest>> sideBranches, Saga saga, int depth) {
      List<Quest> children = sideBranches.get(this.questProgressKey(saga, parentQuest));
      if (children != null && !children.isEmpty()) {
         for (Quest child : children) {
            if (this.isReachableNavigatorQuest(child)) {
               this.navigatorEntries.add(new QuestTreeScreen.NavigatorEntry(QuestTreeScreen.NavEntryType.SIDE_QUEST, depth, saga, child, null, null, false));
               if (this.isSideBranchExpanded(saga, child)) {
                  this.addSideBranchEntries(child, sideBranches, saga, depth + 1);
               }
            }
         }
      }
   }

   private void appendCurrentSagaQuestEntries(Saga currentSaga) {
      Map<String, List<Quest>> sideBranches = this.buildSideBranchesForSaga(currentSaga);

      for (Quest mainQuest : currentSaga.getQuests()) {
         if (this.isReachableNavigatorQuest(mainQuest)) {
            this.navigatorEntries
               .add(new QuestTreeScreen.NavigatorEntry(QuestTreeScreen.NavEntryType.MAIN_QUEST, 1, currentSaga, mainQuest, null, null, false));
            if (this.isSideBranchExpanded(currentSaga, mainQuest)) {
               this.addSideBranchEntries(mainQuest, sideBranches, currentSaga, 2);
            }
         }
      }

      this.appendSecretSideQuestEntries(currentSaga);
   }

   private void appendSecretSideQuestEntries(Saga currentSaga) {
      if (currentSaga != null && this.statsData != null) {
         List<Quest> discovered = new ArrayList<>();

         for (Quest quest : QuestRegistry.getClientQuests().values()) {
            if (quest.isSideQuest()
               && quest.isSecret()
               && QuestTreeLayoutHelper.belongsToSaga(quest, currentSaga.getId())
               && this.isSecretQuestDiscovered(currentSaga, quest)) {
               discovered.add(quest);
            }
         }

         if (!discovered.isEmpty()) {
            discovered.sort(Comparator.comparing(q -> q.getStringId() != null ? q.getStringId() : ""));
            this.navigatorEntries
               .add(
                  new QuestTreeScreen.NavigatorEntry(
                     QuestTreeScreen.NavEntryType.SECRET_SECTION,
                     1,
                     currentSaga,
                     null,
                     null,
                     this.tr("gui.dragonminez.quest_tree.secret_sidequests", new Object[0]).getString(),
                     false
                  )
               );

            for (Quest questx : discovered) {
               this.navigatorEntries
                  .add(new QuestTreeScreen.NavigatorEntry(QuestTreeScreen.NavEntryType.SECRET_SIDE_QUEST, 2, currentSaga, questx, null, null, false));
            }
         }
      }
   }

   private String getSagaDisplayName(Saga saga) {
      if (saga == null) {
         return "?";
      } else if ("cell_saga".equalsIgnoreCase(saga.getId())) {
         return "Cell Saga";
      } else {
         QuestTreeScreen.SagaCatalogEntry entry = this.getSagaCatalogEntry(saga.getId());
         return entry != null ? entry.label() : this.tr(saga.getName(), new Object[0]).getString();
      }
   }

   private QuestTreeScreen.SagaCatalogEntry getSagaCatalogEntry(String sagaId) {
      if (sagaId != null && !sagaId.isBlank()) {
         for (QuestTreeScreen.SagaCatalogEntry entry : SAGA_CATALOG) {
            if (entry.id().equalsIgnoreCase(sagaId)) {
               return entry;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private boolean isSagaUnlockedByPreviousCompletion(Saga saga) {
      if (saga != null && saga.getRequirements() != null) {
         String previousSagaId = saga.getRequirements().previousSagaId();
         if (previousSagaId != null && !previousSagaId.isEmpty()) {
            if (this.statsData == null) {
               return false;
            } else {
               Map<String, Saga> allSagas = QuestRegistry.getClientSagas();
               Saga previousSaga = allSagas.get(previousSagaId);
               if (previousSaga == null) {
                  return true;
               } else {
                  PlayerQuestData pqd = this.statsData.getPlayerQuestData();

                  for (Quest q : previousSaga.getQuests()) {
                     if (!pqd.isQuestCompleted(PlayerQuestData.sagaQuestKey(previousSaga.getId(), q.getId()))) {
                        return false;
                     }
                  }

                  return true;
               }
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private Component getSagaLockTooltip(Saga saga) {
      if (saga != null && !this.isSagaUnlockedByPreviousCompletion(saga) && saga.getRequirements() != null) {
         String previousSagaId = saga.getRequirements().previousSagaId();
         if (previousSagaId != null && !previousSagaId.isBlank()) {
            Saga previousSaga = QuestRegistry.getClientSagas().get(previousSagaId);
            String previousSagaName = previousSaga != null ? this.getSagaDisplayName(previousSaga) : this.getSagaDisplayName(previousSagaId);
            return this.tr("gui.dragonminez.quest_tree.saga_locked.tooltip", new Object[]{previousSagaName});
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private String getSagaDisplayName(String sagaId) {
      QuestTreeScreen.SagaCatalogEntry entry = this.getSagaCatalogEntry(sagaId);
      return entry != null ? entry.label() : QuestTextFormatter.humanizeIdentifier(sagaId);
   }

   private boolean isSecretQuestDiscovered(Saga saga, Quest quest) {
      if (this.statsData != null && saga != null && quest != null) {
         PlayerQuestData pqd = this.statsData.getPlayerQuestData();
         String questKey = this.questProgressKey(saga, quest);
         if (pqd.isQuestCompleted(questKey)) {
            return true;
         } else {
            PlayerQuestData.QuestStatus status = pqd.getQuestStatus(questKey);
            return status == PlayerQuestData.QuestStatus.ACCEPTED || status == PlayerQuestData.QuestStatus.FAILED;
         }
      } else {
         return false;
      }
   }

   private boolean hasReachableSideBranch(Saga saga, Quest parentQuest) {
      Map<String, List<Quest>> sideBranches = this.buildSideBranchesForSaga(saga);
      return this.hasReachableSideBranch(saga, sideBranches, parentQuest);
   }

   private boolean hasReachableSideBranch(Saga saga, Map<String, List<Quest>> sideBranches, Quest parentQuest) {
      List<Quest> children = sideBranches.get(this.questProgressKey(saga, parentQuest));
      if (children != null && !children.isEmpty()) {
         for (Quest child : children) {
            if (this.isReachableNavigatorQuest(child) || this.hasReachableSideBranch(saga, sideBranches, child)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isSideBranchExpanded(Saga saga, Quest parentQuest) {
      return this.expandedSideBranches.contains(this.questProgressKey(saga, parentQuest));
   }

   private void toggleSideBranch(Saga saga, Quest parentQuest) {
      String key = this.questProgressKey(saga, parentQuest);
      if (!this.expandedSideBranches.remove(key)) {
         this.expandedSideBranches.add(key);
      }

      this.rebuildNavigatorEntries();
   }

   private void refreshButtons() {
      this.clearWidgets();
      this.actionButton = null;
      this.claimAllButton = null;
      this.partyPrimaryButton = null;
      this.partySecondaryButton = null;
      this.actionButtonTooltip = List.of();
      this.initNavigationButtons();
      this.initPartyButtons();
      this.initActionButton();
      this.initClaimAllButton();
   }

   private void initActionButton() {
      if (this.selectedQuest != null && this.statsData != null && !this.availableSagas.isEmpty()) {
         PlayerQuestData questData = this.statsData.getPlayerQuestData();
         Saga currentSaga = this.availableSagas.get(this.currentSagaIndex);
         String selectedKey = this.questProgressKey(currentSaga, this.selectedQuest);
         boolean isCompleted = this.isQuestCompleted(questData, currentSaga, this.selectedQuest);
         boolean canStart = this.canStartQuest(this.selectedQuest);
         boolean showDisabledStart = !canStart && this.getNodeStatus(this.selectedQuest) == QuestTreeScreen.QuestNodeStatus.AVAILABLE;
         boolean buttonActive = true;
         boolean isClaimAction = false;
         boolean isTrackAction = false;
         boolean isStartAction = false;
         boolean isResummonAction = false;
         List<Component> tooltipLines = List.of();
         Component buttonText;
         if (isCompleted) {
            boolean hasUnclaimedRewards = false;

            for (int i = 0; i < this.selectedQuest.getRewards().size(); i++) {
               if (this.selectedQuest.getRewards().get(i).isUnlockedFor(questData.getDifficulty())
                  && !this.isRewardClaimed(questData, currentSaga, this.selectedQuest, i)) {
                  hasUnclaimedRewards = true;
                  break;
               }
            }

            if (!hasUnclaimedRewards) {
               return;
            }

            isClaimAction = true;
            if (this.selectedQuest.getClaimMode() == Quest.ClaimMode.NPC_ONLY) {
               buttonText = this.tr("gui.dragonminez.quests.claim_from_npc", new Object[0]);
               buttonActive = false;
               tooltipLines = List.of(this.tr("gui.dragonminez.quests.claim_from_npc.tooltip", new Object[0]));
            } else {
               buttonText = this.tr("gui.dragonminez.quests.claim_rewards", new Object[0]);
            }
         } else if (!canStart && !showDisabledStart) {
            if (questData.getQuestStatus(selectedKey) == PlayerQuestData.QuestStatus.ACCEPTED
               && this.hasRemainingQuestSpawns(questData, selectedKey, this.selectedQuest)
               && this.isResummonReady(selectedKey)) {
               buttonText = this.tr("gui.dragonminez.quests.start", new Object[0]);
               buttonActive = true;
               isResummonAction = true;
            } else {
               if (questData.getQuestStatus(selectedKey) != PlayerQuestData.QuestStatus.ACCEPTED || selectedKey.equals(questData.getTrackedQuestId())) {
                  return;
               }

               buttonText = this.tr("gui.dragonminez.quests.track", new Object[0]);
               isTrackAction = true;
            }
         } else {
            buttonText = this.tr("gui.dragonminez.quests.start", new Object[0]);
            buttonActive = true;
            isStartAction = true;
         }

         if (isStartAction && !canStart) {
            tooltipLines = this.buildQuestBlockerTooltip(this.selectedQuest, currentSaga, true);
         } else if (!buttonActive && tooltipLines.isEmpty()) {
            tooltipLines = this.buildQuestBlockerTooltip(this.selectedQuest, currentSaga, true);
         } else if (isStartAction && buttonActive && questData.isInParty() && tooltipLines.isEmpty()) {
            tooltipLines = List.of(this.tr("gui.dragonminez.party.start_requirements_all", new Object[0]));
         }

         boolean finalIsClaimAction = isClaimAction;
         boolean finalIsTrackAction = isTrackAction;
         boolean finalIsResummonAction = isResummonAction;
         QuestTreeScreen.PanelRect right = this.getRightPanelRect();
         int buttonX = right.x + (right.width - 74) / 2;
         int buttonY = right.bottom() - 28;
         this.actionButton = new TexturedTextButton.Builder()
            .position(buttonX, buttonY)
            .size(74, 20)
            .texture(BUTTONS_TEXTURE)
            .textureCoords(0, 28, 0, 48)
            .textureSize(74, 20)
            .message(buttonText)
            .onPress(btn -> {
               long now = System.currentTimeMillis();
               if (now - this.lastClickTime >= 500L) {
                  this.lastClickTime = now;
                  if (finalIsClaimAction) {
                     NetworkHandler.sendToServer(new ClaimQuestRewardC2S(selectedKey));
                     btn.visible = false;
                     this.pendingRefreshTicks = 5;
                  } else if (finalIsTrackAction) {
                     NetworkHandler.sendToServer(new SetTrackedQuestC2S(selectedKey));
                     questData.setTrackedQuestId(selectedKey);
                     btn.visible = false;
                     this.pendingRefreshTicks = 5;
                  } else if (finalIsResummonAction) {
                     NetworkHandler.sendToServer(new QuestActionC2S(QuestActionC2S.ActionType.RESUMMON, selectedKey, ""));
                     this.startResummonCooldown(selectedKey);
                     btn.visible = false;
                     this.pendingRefreshTicks = 5;
                  } else {
                     NetworkHandler.sendToServer(new QuestActionC2S(QuestActionC2S.ActionType.START, selectedKey, ""));
                     this.startResummonCooldown(selectedKey);
                     btn.visible = false;
                     this.pendingRefreshTicks = 5;
                     if (this.questSpawnsQuestEnemy(this.selectedQuest)) {
                        this.pendingStartCloseKey = selectedKey;
                        this.pendingStartCloseTicks = 60;
                     }
                  }
               }
            })
            .build();
         this.actionButton.active = buttonActive;
         this.actionButtonTooltip = tooltipLines;
         this.addRenderableWidget(this.actionButton);
      }
   }

   private void initClaimAllButton() {
      if (this.statsData != null && !this.availableSagas.isEmpty()) {
         if (this.hasAnyClaimableRewards()) {
            QuestTreeScreen.PanelRect tree = this.getTreePanelRect();
            int buttonX = tree.x + (tree.width - 74) / 2;
            int buttonY = tree.y + 22;
            this.claimAllButton = new TexturedTextButton.Builder()
               .position(buttonX, buttonY)
               .size(74, 20)
               .texture(BUTTONS_TEXTURE)
               .textureCoords(0, 28, 0, 48)
               .textureSize(74, 20)
               .message(this.tr("gui.dragonminez.quests.claim_all", new Object[0]))
               .onPress(btn -> {
                  long now = System.currentTimeMillis();
                  if (now - this.lastClickTime >= 500L) {
                     this.lastClickTime = now;
                     NetworkHandler.sendToServer(new ClaimAllQuestRewardsC2S());
                     btn.visible = false;
                     this.pendingRefreshTicks = 5;
                  }
               })
               .build();
            this.addRenderableWidget(this.claimAllButton);
         }
      }
   }

   private boolean hasAnyClaimableRewards() {
      if (this.statsData == null) {
         return false;
      } else {
         PlayerQuestData questData = this.statsData.getPlayerQuestData();

         for (String questKey : questData.getCompletedQuestIds()) {
            Quest quest = QuestRegistry.getClientQuest(questKey);
            if (quest != null && quest.getClaimMode() != Quest.ClaimMode.NPC_ONLY) {
               for (int i = 0; i < quest.getRewards().size(); i++) {
                  if (quest.getRewards().get(i).isUnlockedFor(questData.getDifficulty()) && !questData.isRewardClaimed(questKey, i)) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private void syncClaimAllButtonPosition() {
      if (this.claimAllButton != null) {
         QuestTreeScreen.PanelRect tree = this.getTreePanelRect();
         this.claimAllButton.setX(tree.x + (tree.width - 74) / 2);
         this.claimAllButton.setY(tree.y + 22);
         this.claimAllButton.visible = !this.invitePopupOpen && !this.confirmOverlayOpen;
      }
   }

   private void initPartyButtons() {
      if (this.statsData != null) {
         PlayerQuestData questData = this.statsData.getPlayerQuestData();
         PlayerQuestData.PartyInviteData invite = this.getVisiblePartyInvite();
         boolean inParty = questData.isInParty();
         boolean isLeader = this.isLocalPartyLeader();
         boolean hasOtherPlayers = this.hasOtherOnlinePlayers();
         if (invite != null || inParty || hasOtherPlayers) {
            QuestTreeScreen.PanelRect footer = this.getPartyFooterRect();
            if (footer != null) {
               if (invite != null) {
                  this.partyPrimaryButton = this.buildPartyButton(
                     this.tr("quest.dmz.party.invite.accept", new Object[0]),
                     footer.x + (footer.width - 74) / 2,
                     footer.bottom() - 46,
                     btn -> {
                        Difficulty partyDifficulty = invite.getPartyDifficulty();
                        Difficulty ownDifficulty = questData.getDifficulty();
                        if (partyDifficulty.ordinal() < ownDifficulty.ordinal()) {
                           if (Minecraft.getInstance().player != null) {
                              Minecraft.getInstance()
                                 .player
                                 .displayClientMessage(this.tr("quest.dmz.party.invite.difficulty_too_low", new Object[0]).withStyle(ChatFormatting.RED), false);
                           }
                        } else if (partyDifficulty.ordinal() > ownDifficulty.ordinal()) {
                           this.requestConfirm(
                              QuestTreeScreen.PartyConfirmAction.ACCEPT_INVITE_DIFFICULTY,
                              this.tr("gui.dragonminez.party.confirm.difficulty.title", new Object[0]),
                              this.tr("gui.dragonminez.party.confirm.difficulty.body", new Object[]{this.difficultyLabel(partyDifficulty)})
                           );
                        } else if (questData.isInParty()) {
                           this.requestConfirm(
                              QuestTreeScreen.PartyConfirmAction.ACCEPT_INVITE,
                              this.tr("gui.dragonminez.party.confirm.title", new Object[0]),
                              this.tr("gui.dragonminez.party.confirm.leave_current", new Object[]{this.txt(this.resolveInviteName(invite))})
                           );
                        } else {
                           NetworkHandler.sendToServer(new AcceptPartyInviteC2S());
                           this.queuePartyRefresh();
                        }
                     }
                  );
                  this.partySecondaryButton = this.buildPartyButton(
                     this.tr("quest.dmz.party.invite.reject", new Object[0]), footer.x + (footer.width - 74) / 2, footer.bottom() - 20, btn -> {
                        NetworkHandler.sendToServer(new RejectPartyInviteC2S());
                        this.queuePartyRefresh();
                     }
                  );
               } else if (inParty) {
                  if (isLeader) {
                     this.partyPrimaryButton = this.buildPartyButton(
                        this.tr("gui.dragonminez.party.invite_players", new Object[0]),
                        footer.x + (footer.width - 74) / 2,
                        footer.bottom() - 46,
                        btn -> this.openInvitePopup()
                     );
                     this.partySecondaryButton = this.buildPartyButton(
                        this.tr("gui.dragonminez.party.disband", new Object[0]),
                        footer.x + (footer.width - 74) / 2,
                        footer.bottom() - 20,
                        btn -> this.requestConfirm(
                              QuestTreeScreen.PartyConfirmAction.LEAVE_PARTY,
                              this.tr("gui.dragonminez.party.disband", new Object[0]),
                              this.tr("gui.dragonminez.party.confirm.disband", new Object[0])
                           )
                     );
                  } else {
                     this.partyPrimaryButton = this.buildPartyButton(
                        this.tr("gui.dragonminez.party.leave", new Object[0]),
                        footer.x + (footer.width - 74) / 2,
                        footer.bottom() - 20,
                        btn -> this.requestConfirm(
                              QuestTreeScreen.PartyConfirmAction.LEAVE_PARTY,
                              this.tr("gui.dragonminez.party.leave", new Object[0]),
                              this.tr("gui.dragonminez.party.confirm.leave", new Object[0])
                           )
                     );
                  }
               } else {
                  this.partyPrimaryButton = this.buildPartyButton(
                     this.tr("gui.dragonminez.party.create", new Object[0]),
                     footer.x + (footer.width - 74) / 2,
                     footer.bottom() - 20,
                     btn -> this.openInvitePopup()
                  );
               }
            }
         }
      }
   }

   private TexturedTextButton buildPartyButton(Component label, int x, int y, OnPress onPress) {
      TexturedTextButton button = new TexturedTextButton.Builder()
         .position(x, y)
         .size(74, 20)
         .texture(BUTTONS_TEXTURE)
         .textureCoords(0, 28, 0, 48)
         .textureSize(74, 20)
         .message(label)
         .onPress(onPress)
         .build();
      this.addRenderableWidget(button);
      return button;
   }

   public static void clearResummonCooldowns() {
      resummonReadyAt.clear();
   }

   private boolean isResummonReady(String questKey) {
      Long readyAt = resummonReadyAt.get(questKey);
      return readyAt == null || System.currentTimeMillis() >= readyAt;
   }

   private void startResummonCooldown(String questKey) {
      resummonReadyAt.put(questKey, System.currentTimeMillis() + 60000L);
   }

   private boolean hasRemainingQuestSpawns(PlayerQuestData questData, String questKey, Quest quest) {
      if (quest != null && questData != null) {
         List<QuestObjective> objectives = quest.getObjectives();

         for (int i = 0; i < objectives.size(); i++) {
            Object progress = objectives.get(i);
            if (progress instanceof KillObjective) {
               KillObjective killObjective = (KillObjective)progress;
               if (killObjective.getSpawnMode() == KillObjective.SpawnMode.QUEST) {
                  int progressx = questData.getObjectiveProgress(questKey, i);
                  int required = quest.getObjectiveRequired(questData, questKey, i);
                  if (progressx < required) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean questSpawnsQuestEnemy(Quest quest) {
      if (quest == null) {
         return false;
      } else {
         for (QuestObjective objective : quest.getObjectives()) {
            if (objective instanceof KillObjective killObjective && killObjective.getSpawnMode() == KillObjective.SpawnMode.QUEST) {
               return true;
            }
         }

         return false;
      }
   }

   private void tickPendingStartClose() {
      if (this.pendingStartCloseKey != null) {
         if (this.statsData != null) {
            PlayerQuestData questData = this.statsData.getPlayerQuestData();
            if (questData.getQuestStatus(this.pendingStartCloseKey) == PlayerQuestData.QuestStatus.ACCEPTED) {
               this.pendingStartCloseKey = null;
               this.pendingStartCloseTicks = 0;
               this.onClose();
               return;
            }
         }

         if (--this.pendingStartCloseTicks <= 0) {
            this.pendingStartCloseKey = null;
         }
      }
   }

   private boolean canStartQuest(Quest quest) {
      if (this.statsData != null && !this.availableSagas.isEmpty() && quest != null) {
         Saga currentSaga = this.availableSagas.get(this.currentSagaIndex);
         PlayerQuestData questData = this.statsData.getPlayerQuestData();
         String questKey = this.questProgressKey(currentSaga, quest);
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return false;
         } else if (questData.isQuestCompleted(questKey)) {
            return false;
         } else {
            PlayerQuestData.QuestStatus status = questData.getQuestStatus(questKey);
            if (status == PlayerQuestData.QuestStatus.ACCEPTED) {
               return false;
            } else if (status == PlayerQuestData.QuestStatus.FAILED) {
               return QuestAvailabilityChecker.areStartRequirementsMet(quest, questKey, mc.player, this.statsData);
            } else {
               return this.getNodeStatus(quest) != QuestTreeScreen.QuestNodeStatus.AVAILABLE
                  ? false
                  : QuestAvailabilityChecker.areStartRequirementsMet(quest, questKey, mc.player, this.statsData);
            }
         }
      } else {
         return false;
      }
   }

   @Override
   public void tick() {
      super.tick();
      this.tickCount++;
      this.enemyPreview.clientTick();
      this.tickPendingStartClose();
      if (this.tickCount >= 10) {
         this.tickCount = 0;
         this.updateStatsData();
         this.rebuildNavigatorEntries();
         this.refreshButtons();
         if (this.invitePopupOpen) {
            this.rebuildInviteEntries();
            if (this.getVisiblePartyInvite() != null) {
               this.invitePopupOpen = false;
            }
         }
      }

      if (this.pendingRefreshTicks > 0) {
         this.pendingRefreshTicks--;
         if (this.pendingRefreshTicks == 0) {
            this.updateStatsData();
            this.rebuildNavigatorEntries();
            this.refreshButtons();
            if (this.invitePopupOpen) {
               this.rebuildInviteEntries();
            }
         }
      }
   }

   private void updateStatsData() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            this.statsData = data;
            if (!this.availableSagas.isEmpty()) {
               if (this.currentSagaIndex >= this.availableSagas.size()) {
                  this.currentSagaIndex = Math.max(0, this.availableSagas.size() - 1);
               }
            }
         });
      }
   }

   public void removed() {
      this.enemyPreview.clear();
      super.removed();
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      if (this.isNotAnimating()) {
         this.renderBackground(graphics, mouseX, mouseY, partialTick);
      }

      this.navBar.clear();
      this.descBar.clear();
      this.objBar.clear();
      this.rewardsBar.clear();
      this.diffIntroBar.clear();

      for (ScrollbarState b : this.diffOptBars) {
         b.clear();
      }

      long now = System.nanoTime();
      if (this.lastRenderTime == 0L) {
         this.lastRenderTime = now;
      }

      float dt = (float)(now - this.lastRenderTime) / 1.0E9F;
      this.lastRenderTime = now;
      dt = Math.min(dt, 0.1F);
      if (this.isAnimatingPan) {
         float alpha = (float)(1.0 - Math.exp((double)(-14.0F * dt)));
         this.panX = this.panX + (this.targetPanX - this.panX) * alpha;
         this.panY = this.panY + (this.targetPanY - this.panY) * alpha;
         if (Math.abs(this.targetPanX - this.panX) < 0.35F && Math.abs(this.targetPanY - this.panY) < 0.35F) {
            this.panX = this.targetPanX;
            this.panY = this.targetPanY;
            this.isAnimatingPan = false;
         }
      }

      int uiMouseX = (int)Math.round(this.toUiX((double)mouseX));
      int uiMouseY = (int)Math.round(this.toUiY((double)mouseY));
      this.updatePanelInteractionAnimations(uiMouseX, uiMouseY, dt);
      this.beginUiScale(graphics);
      this.applyZoom(graphics, partialTick);
      this.syncActionButtonPosition();
      this.syncClaimAllButtonPosition();
      this.syncPartyButtonPositions();
      this.rewardHitboxes.clear();
      this.frameObjLinesCache = null;
      this.frameTitleLines = null;
      this.renderTreeCanvas(graphics, uiMouseX, uiMouseY);
      this.renderEnemyPreview(graphics, uiMouseX, uiMouseY, dt);
      this.renderLeftNavigatorPanel(graphics, uiMouseX, uiMouseY, dt);
      this.renderRightDetailPanel(graphics, uiMouseX, uiMouseY, dt);
      super.render(graphics, uiMouseX, uiMouseY, partialTick);
      if (this.actionButton != null && this.actionButton.visible && this.actionButton.active) {
         this.renderActionButtonGlow(graphics);
      }

      if (this.invitePopupOpen) {
         this.renderInvitePopup(graphics, uiMouseX, uiMouseY);
      }

      if (this.confirmOverlayOpen) {
         this.renderConfirmOverlay(graphics, uiMouseX, uiMouseY);
      }

      boolean difficultySelectOpen = this.shouldShowDifficultySelect();
      if (difficultySelectOpen) {
         this.renderDifficultySelectOverlay(graphics, uiMouseX, uiMouseY);
      }

      if (!this.invitePopupOpen
         && !this.confirmOverlayOpen
         && !difficultySelectOpen
         && !this.renderHardModeTooltip(graphics, uiMouseX, uiMouseY)
         && !this.renderActionButtonTooltip(graphics, uiMouseX, uiMouseY)) {
         this.renderRewardTooltips(graphics, uiMouseX, uiMouseY);
      }

      this.endUiScale(graphics);
   }

   private void renderTreeCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
      this.hardModeIndicatorShown = false;
      if (this.currentLayout != null && !this.availableSagas.isEmpty()) {
         QuestTreeScreen.PanelRect tree = this.getTreePanelRect();
         graphics.enableScissor(
            this.toScreenCoord((double)tree.x),
            this.toScreenCoord((double)tree.y),
            this.toScreenCoord((double)tree.right()),
            this.toScreenCoord((double)tree.bottom())
         );
         this.renderBackgroundGrid(graphics, tree);
         Saga currentSaga = this.availableSagas.get(this.currentSagaIndex);
         TextUtil.drawCenteredStringWithBorder(
            graphics, this.font, this.txt(this.getSagaDisplayName(currentSaga)).withStyle(ChatFormatting.BOLD), tree.x + tree.width / 2, tree.y + 8, -10496
         );
         graphics.pose().pushPose();
         graphics.pose().scale(this.zoom, this.zoom, 1.0F);
         int zoomedMouseX = (int)((float)mouseX / this.zoom);
         int zoomedMouseY = (int)((float)mouseY / this.zoom);
         int panOffX = (int)(this.panX / this.zoom);
         int panOffY = (int)(this.panY / this.zoom);
         float viewRight = (float)this.getUiWidth() / this.zoom;
         float viewBottom = (float)this.getUiHeight() / this.zoom;
         this.renderConnections(graphics, panOffX, panOffY, viewRight, viewBottom);
         boolean overOverlay = this.getLeftPanelRect().contains((double)mouseX, (double)mouseY)
            || this.getRightPanelRect().contains((double)mouseX, (double)mouseY)
            || this.getChildAt((double)mouseX, (double)mouseY).isPresent();
         Quest hoveredQuest = null;

         for (QuestTreeScreen.NodeRender node : this.nodeRenders) {
            int x = node.pixelX() + panOffX;
            int y = node.pixelY() + panOffY;
            if (x + 18 + 18 >= 0 && !((float)(x - 18) > viewRight) && y + 18 + 20 >= 0 && !((float)(y - 18) > viewBottom)) {
               boolean isHovered = !overOverlay && zoomedMouseX >= x && zoomedMouseX <= x + 18 && zoomedMouseY >= y && zoomedMouseY <= y + 18;
               if (isHovered) {
                  hoveredQuest = node.quest();
               }

               this.renderNode(graphics, node, x, y, isHovered);
            }
         }

         graphics.pose().popPose();
         if (hoveredQuest != null) {
            this.renderNodeTooltip(graphics, hoveredQuest, mouseX, mouseY);
         }

         String zoomText = (int)(this.zoom * 100.0F) + "%";
         TextUtil.drawStringWithBorder(graphics, this.font, this.txt(zoomText), 6, tree.bottom() - 12, -7829368);
         this.renderHardModeIndicator(graphics, tree, mouseX, mouseY);
         graphics.disableScissor();
      } else {
         TextUtil.drawCenteredStringWithBorder(
            graphics, this.font, this.tr("gui.dragonminez.quest_tree.no_sagas", new Object[0]), this.getUiWidth() / 2, this.getUiHeight() / 2, -5592406
         );
      }
   }

   private void renderHardModeIndicator(GuiGraphics graphics, QuestTreeScreen.PanelRect tree, int mouseX, int mouseY) {
      Difficulty difficulty = this.statsData != null ? this.statsData.getPlayerQuestData().getDifficulty() : Difficulty.NORMAL;
      this.hardModeToggleable = false;
      this.hardModeIndicatorShown = true;
      Component label = this.tr("gui.dragonminez.quest_tree.difficulty.label", new Object[0]);
      Component state = this.difficultyLabel(difficulty);
      int labelWidth = this.font.width(label);
      int totalWidth = labelWidth + 3 + this.font.width(state);
      int x = tree.right() - totalWidth - 6;
      int y = tree.bottom() - 12;
      int stateX = x + labelWidth + 3;
      this.hardModeHitX = x;
      this.hardModeHitY = y - 1;
      this.hardModeHitW = totalWidth;
      this.hardModeHitH = 9 + 1;
      boolean hovered = mouseX >= this.hardModeHitX
         && mouseX <= this.hardModeHitX + this.hardModeHitW
         && mouseY >= this.hardModeHitY
         && mouseY <= this.hardModeHitY + this.hardModeHitH;
      boolean highlight = hovered && this.hardModeToggleable;
      int labelColor = hovered ? -1 : -5592406;
      int stateColor = this.difficultyColor(difficulty, highlight);
      TextUtil.drawStringWithBorder(graphics, this.font, label, x, y, labelColor);
      TextUtil.drawStringWithBorder(graphics, this.font, state, stateX, y, stateColor);
   }

   private boolean renderHardModeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
      if (!this.hardModeIndicatorShown || this.statsData == null) {
         return false;
      } else if (mouseX >= this.hardModeHitX
         && mouseX <= this.hardModeHitX + this.hardModeHitW
         && mouseY >= this.hardModeHitY
         && mouseY <= this.hardModeHitY + this.hardModeHitH) {
         Difficulty difficulty = this.statsData.getPlayerQuestData().getDifficulty();
         String hpMult = formatMultiplier(difficulty.hpMultiplier());
         String damageMult = formatMultiplier(difficulty.damageMultiplier());
         String tpMult = formatMultiplier(difficulty.tpMultiplier());
         String rewardMult = formatMultiplier(difficulty.questRewardMultiplier());
         Component title = this.tr("gui.dragonminez.quest_tree.difficulty.tooltip.title", new Object[0]).withStyle(ChatFormatting.BOLD);
         List<Component> desc = new ArrayList<>();
         desc.add(
            this.tr("gui.dragonminez.quest_tree.difficulty.tooltip.current", new Object[]{this.difficultyLabel(difficulty)}).withStyle(ChatFormatting.GRAY)
         );
         desc.add(this.tr("gui.dragonminez.quest_tree.difficulty.tooltip.desc", new Object[0]).withStyle(ChatFormatting.GRAY));
         desc.add(this.tr("gui.dragonminez.quest_tree.difficulty.tooltip.stats", new Object[]{hpMult, damageMult}).withStyle(ChatFormatting.AQUA));
         desc.add(this.tr("gui.dragonminez.quest_tree.difficulty.tooltip.rewards", new Object[]{tpMult, rewardMult}).withStyle(ChatFormatting.AQUA));
         desc.add(this.tr("gui.dragonminez.quest_tree.difficulty.tooltip.locked", new Object[0]).withStyle(ChatFormatting.DARK_GRAY));
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, null, 16777215);
         return true;
      } else {
         return false;
      }
   }

   private Component difficultyLabel(Difficulty difficulty) {
      return switch (difficulty) {
         case EASY -> this.tr("gui.dragonminez.quest_tree.difficulty.easy", new Object[0]);
         case NORMAL -> this.tr("gui.dragonminez.quest_tree.difficulty.normal", new Object[0]);
         case HARD -> this.tr("gui.dragonminez.quest_tree.difficulty.hard", new Object[0]);
      };
   }

   private int difficultyColor(Difficulty difficulty, boolean highlight) {
      return switch (difficulty) {
         case EASY -> highlight ? -7807608 : -10044570;
         case NORMAL -> highlight ? -120 : -1122987;
         case HARD -> highlight ? -32640 : -43691;
      };
   }

   private static String formatMultiplier(double value) {
      return value == Math.rint(value) && !Double.isInfinite(value) ? Long.toString((long)value) : Double.toString(value);
   }

   private void renderBackgroundGrid(GuiGraphics graphics, QuestTreeScreen.PanelRect tree) {
      int spacing = 20;
      int offsetX = (int)this.panX % spacing;
      int offsetY = (int)this.panY % spacing;

      for (int x = tree.x + offsetX; x < tree.right(); x += spacing) {
         graphics.fill(x, tree.y, x + 1, tree.bottom(), -14540220);
      }

      for (int y = tree.y + offsetY; y < tree.bottom(); y += spacing) {
         graphics.fill(tree.x, y, tree.right(), y + 1, -14540220);
      }
   }

   private void renderEnemyPreview(GuiGraphics graphics, int mouseX, int mouseY, float dt) {
      Difficulty previewDifficulty = Difficulty.NORMAL;
      int previewPartySize = 1;
      if (this.statsData != null) {
         PlayerQuestData questData = this.statsData.getPlayerQuestData();
         previewDifficulty = questData.getDifficulty();
         previewPartySize = Math.max(1, questData.getPartyMemberIds().size());
      }

      this.enemyPreview.setQuest(this.selectedQuest, previewDifficulty, previewPartySize);
      if (this.enemyPreview.isActive()) {
         QuestTreeScreen.PanelRect base = this.getBaseLeftPanelRect();
         float visibility = 1.0F - this.leftPanelRevealProgress;
         this.enemyPreview.render(graphics, this.font, base.x, base.y, base.width, base.height, mouseX, mouseY, dt, visibility);
      }
   }

   private void renderLeftNavigatorPanel(GuiGraphics graphics, int mouseX, int mouseY, float dt) {
      QuestTreeScreen.PanelRect panel = this.getLeftPanelRect();
      this.renderSidePanelBackground(graphics, panel, true, false, false);
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.quest_tree.title", new Object[0]).copy().withStyle(ChatFormatting.BOLD),
         panel.x + panel.width / 2,
         panel.y + 10,
         -10496
      );
      int listX = panel.x + 10;
      int listY = panel.y + 28;
      int listW = panel.width - 20;
      int listH = Math.max(32, panel.height - 38 - this.getPartyFooterHeight());
      int totalNavHeight = this.navigatorEntries.size() * 13;
      this.navMaxScroll = (float)Math.max(0, totalNavHeight - listH);
      this.targetNavScroll = Mth.clamp(this.targetNavScroll, 0.0F, this.navMaxScroll);
      this.currentNavScroll = this.currentNavScroll + (this.targetNavScroll - this.currentNavScroll) * (float)(1.0 - Math.exp((double)(-15.0F * dt)));
      QuestTreeScreen.NavigatorEntry hoveredEntry = null;
      graphics.enableScissor(
         this.toScreenCoord((double)listX),
         this.toScreenCoord((double)listY),
         this.toScreenCoord((double)(listX + listW)),
         this.toScreenCoord((double)(listY + listH))
      );
      graphics.pose().pushPose();
      graphics.pose().translate(0.0F, -this.currentNavScroll, 0.0F);

      for (int i = 0; i < this.navigatorEntries.size(); i++) {
         QuestTreeScreen.NavigatorEntry entry = this.navigatorEntries.get(i);
         int rowY = listY + i * 13;
         if ((float)(rowY + 13) >= (float)listY + this.currentNavScroll && (float)rowY <= (float)(listY + listH) + this.currentNavScroll) {
            boolean hovered = mouseX >= listX
               && mouseX <= listX + listW
               && (float)mouseY >= (float)rowY - this.currentNavScroll
               && (float)mouseY <= (float)(rowY + 13) - this.currentNavScroll;
            if (hovered) {
               hoveredEntry = entry;
            }

            this.renderNavigatorEntry(graphics, entry, listX, rowY, listW, hovered);
         }
      }

      graphics.pose().popPose();
      graphics.disableScissor();
      this.navBar.update(listX + listW - 3, 2, listY, listH, this.navMaxScroll);
      if (this.navMaxScroll > 0.0F) {
         int scrollBarX = listX + listW - 3;
         graphics.fill(scrollBarX, listY, scrollBarX + 2, listY + listH, -13421773);
         float scrollPercent = this.navMaxScroll == 0.0F ? 0.0F : this.currentNavScroll / this.navMaxScroll;
         int indicatorHeight = Math.max(10, (int)((float)listH / (float)totalNavHeight * (float)listH));
         int indicatorY = listY + (int)((float)(listH - indicatorHeight) * scrollPercent);
         graphics.fill(scrollBarX, indicatorY, scrollBarX + 2, indicatorY + indicatorHeight, -5592406);
      }

      if (hoveredEntry != null) {
         if (hoveredEntry.comingSoon()) {
            this.renderSimpleTooltip(graphics, List.of(this.txt("Coming soon... Follow development in Discord!")), mouseX, mouseY);
         } else if (hoveredEntry.type() == QuestTreeScreen.NavEntryType.SAGA && !hoveredEntry.isPlaceholderSaga()) {
            Component lockTooltip = this.getSagaLockTooltip(hoveredEntry.saga());
            if (lockTooltip != null) {
               this.renderSimpleTooltip(graphics, List.of(lockTooltip), mouseX, mouseY);
            }
         } else if (hoveredEntry.type() == QuestTreeScreen.NavEntryType.SECRET_SECTION) {
            this.renderSimpleTooltip(graphics, List.of(this.tr("gui.dragonminez.quest_tree.secret_sidequests.tooltip", new Object[0])), mouseX, mouseY);
         }
      }

      this.renderPartyFooter(graphics, panel);
   }

   private void renderNavigatorEntry(GuiGraphics graphics, QuestTreeScreen.NavigatorEntry entry, int x, int y, int rowWidth, boolean hovered) {
      int textY = y + 2;
      if (entry.type() == QuestTreeScreen.NavEntryType.SECRET_SECTION) {
         int color = hovered ? -8054 : -13227;
         String raw = "* " + entry.sagaLabel();
         String clipped = this.fitSingleLineEllipsis(raw, Math.max(24, rowWidth - 8));
         Component text = this.txt(clipped).withStyle(ChatFormatting.BOLD);
         TextUtil.drawStringWithBorder(graphics, this.font, text, x + entry.depth() * 10, textY, color);
      } else {
         int color;
         Component text;
         if (entry.type() == QuestTreeScreen.NavEntryType.SAGA) {
            if (entry.isPlaceholderSaga()) {
               color = hovered && entry.comingSoon() ? -5592406 : -10066330;
               String raw = "[L] " + entry.sagaLabel();
               String clipped = this.fitSingleLineEllipsis(raw, Math.max(24, rowWidth - 8));
               text = this.txt(clipped).withStyle(ChatFormatting.BOLD);
               TextUtil.drawStringWithBorder(graphics, this.font, text, x, textY, color);
               return;
            }

            boolean selectedSaga = !this.availableSagas.isEmpty() && entry.saga() == this.availableSagas.get(this.currentSagaIndex);
            boolean unlocked = this.isSagaUnlockedByPreviousCompletion(entry.saga());
            color = selectedSaga ? -13227 : (unlocked ? -1 : -7829368);
            if (hovered && unlocked) {
               color = -8054;
            }

            String prefix = selectedSaga ? "v " : (unlocked ? "> " : "[L] ");
            String raw = prefix + entry.sagaLabel();
            String clipped = this.fitSingleLineEllipsis(raw, Math.max(24, rowWidth - 8));
            text = this.txt(clipped).withStyle(ChatFormatting.BOLD);
         } else {
            Quest q = entry.quest();
            String branchPrefix = "";
            if ((entry.type() == QuestTreeScreen.NavEntryType.MAIN_QUEST || entry.type() == QuestTreeScreen.NavEntryType.SIDE_QUEST)
               && this.hasReachableSideBranch(entry.saga(), q)) {
               branchPrefix = this.isSideBranchExpanded(entry.saga(), q) ? "v " : "> ";
            }

            String label = q.isSideQuest()
               ? branchPrefix + "- " + LocalizationUtil.localizedOrReadableText(q.getTitle())
               : branchPrefix + q.getId() + ". " + LocalizationUtil.localizedOrReadableText(q.getTitle());
            int indent = entry.depth() * 10;
            String clipped = this.fitSingleLineEllipsis(label, Math.max(24, rowWidth - indent - 8));
            text = this.txt(clipped);
            QuestTreeScreen.QuestNodeStatus status = this.getNodeStatus(q);
            color = this.getStatusColor(status);
            if (this.sameQuestIdentity(this.selectedQuest, q)) {
               color = -1;
            }

            if (hovered) {
               color = -12176;
            }
         }

         int indentx = entry.depth() * 10;
         TextUtil.drawStringWithBorder(graphics, this.font, text, x + indentx, textY, color);
      }
   }

   private void renderPartyFooter(GuiGraphics graphics, QuestTreeScreen.PanelRect panel) {
      QuestTreeScreen.PanelRect footer = this.getPartyFooterRect();
      if (footer != null && this.statsData != null) {
         PlayerQuestData questData = this.statsData.getPlayerQuestData();
         PlayerQuestData.PartyInviteData invite = this.getVisiblePartyInvite();
         graphics.fill(footer.x, footer.y, footer.right(), footer.bottom(), 1427181858);
         graphics.renderOutline(footer.x, footer.y, footer.width, footer.height, -2008791962);
         TextUtil.drawStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.party.title", new Object[0]).copy().withStyle(ChatFormatting.BOLD),
            footer.x + 6,
            footer.y + 4,
            -10496
         );
         int textY = footer.y + 18;
         int textW = footer.width - 12;
         if (invite != null) {
            TextUtil.drawStringWithBorder(
               graphics,
               this.font,
               this.txt(
                  this.fitSingleLineEllipsis(this.tr("gui.dragonminez.party.invited_by", new Object[]{this.resolveInviteName(invite)}).getString(), textW)
               ),
               footer.x + 6,
               textY,
               -1
            );
            textY += 10;
            if (questData.isInParty()) {
               TextUtil.drawStringWithBorder(
                  graphics,
                  this.font,
                  this.txt(this.fitSingleLineEllipsis(this.tr("gui.dragonminez.party.invite_warning", new Object[0]).getString(), textW)),
                  footer.x + 6,
                  textY,
                  -21914
               );
            } else {
               TextUtil.drawStringWithBorder(
                  graphics,
                  this.font,
                  this.txt(this.fitSingleLineEllipsis(this.tr("gui.dragonminez.party.invite_open_quest", new Object[0]).getString(), textW)),
                  footer.x + 6,
                  textY,
                  -4466945
               );
            }
         } else if (questData.isInParty()) {
            String roleKey = this.isLocalPartyLeader() ? "gui.dragonminez.party.role.leader" : "gui.dragonminez.party.role.member";
            TextUtil.drawStringWithBorder(
               graphics, this.font, this.txt(this.fitSingleLineEllipsis(this.tr(roleKey, new Object[0]).getString(), textW)), footer.x + 6, textY, -1
            );
            textY += 10;
            TextUtil.drawStringWithBorder(
               graphics, this.font, this.txt(this.fitSingleLineEllipsis(this.buildPartyMemberSummary(), textW)), footer.x + 6, textY, -4466945
            );
         } else {
            if (this.hasOtherOnlinePlayers()) {
               TextUtil.drawStringWithBorder(
                  graphics,
                  this.font,
                  this.txt(this.fitSingleLineEllipsis(this.tr("gui.dragonminez.party.multiplayer_ready", new Object[0]).getString(), textW)),
                  footer.x + 6,
                  textY,
                  -1
               );
               textY += 10;
               TextUtil.drawStringWithBorder(
                  graphics,
                  this.font,
                  this.txt(this.fitSingleLineEllipsis(this.tr("gui.dragonminez.party.create_hint", new Object[0]).getString(), textW)),
                  footer.x + 6,
                  textY,
                  -4466945
               );
            }
         }
      }
   }

   private int getPartyFooterHeight() {
      if (this.statsData == null) {
         return 0;
      } else {
         PlayerQuestData questData = this.statsData.getPlayerQuestData();
         if (this.getVisiblePartyInvite() != null) {
            return 94;
         } else if (questData.isInParty()) {
            return this.isLocalPartyLeader() ? 94 : 70;
         } else {
            return this.hasOtherOnlinePlayers() ? 64 : 0;
         }
      }
   }

   private QuestTreeScreen.PanelRect getPartyFooterRect() {
      int footerHeight = this.getPartyFooterHeight();
      if (footerHeight <= 0) {
         return null;
      } else {
         QuestTreeScreen.PanelRect panel = this.getLeftPanelRect();
         return new QuestTreeScreen.PanelRect(panel.x + 8, panel.bottom() - footerHeight, panel.width - 16, footerHeight - 6);
      }
   }

   private void renderRightDetailPanel(GuiGraphics graphics, int mouseX, int mouseY, float dt) {
      if (!(this.rightPanelRevealProgress <= 0.001F) || this.selectedQuest != null) {
         QuestTreeScreen.PanelRect panel = this.getRightPanelRect();
         this.renderSidePanelBackground(graphics, panel, false, true, false);
         if (this.selectedQuest != null && this.statsData != null && !this.availableSagas.isEmpty()) {
            Saga saga = this.availableSagas.get(this.currentSagaIndex);
            String questKey = this.questProgressKey(saga, this.selectedQuest);
            QuestTreeScreen.QuestNodeStatus status = this.getNodeStatus(this.selectedQuest);
            int innerX = panel.x + 10;
            int innerY = panel.y + 10;
            int innerW = panel.width - 20;
            int innerH = panel.height - 40;
            QuestTreeScreen.DetailPanelLayout layout = this.computeDetailPanelLayout(innerW, innerH, questKey, saga);
            int rewardsY = innerY + layout.titleH();
            int descY = rewardsY + layout.rewardsH();
            int objectivesY = descY + layout.descH();
            this.renderTopSection(graphics, innerX, innerY, innerW, layout.titleH(), status);
            this.renderRewardsSection(graphics, innerX, rewardsY, innerW, layout.rewardsH(), questKey, mouseX, mouseY, dt);
            this.renderDescriptionSection(graphics, innerX, descY, innerW, layout.descH(), questKey, dt);
            this.renderObjectivesSection(graphics, innerX, objectivesY, innerW, layout.objectivesH(), saga, dt);
         }
      }
   }

   private QuestTreeScreen.DetailPanelLayout computeDetailPanelLayout(int width, int totalHeight, String questKey, Saga saga) {
      int lineHeight = this.getDetailLineHeight();
      boolean hasObjectiveContent = this.selectedQuest.hasStartRequirements() || !this.selectedQuest.getObjectives().isEmpty();
      int titleH = this.estimateTitleSectionHeight(width);
      int rewardsMin = this.selectedQuest.getRewards().isEmpty() ? 24 : Math.max(36, lineHeight + 24);
      int objectivesMin = hasObjectiveContent ? Math.max(36, lineHeight + 24) : 24;
      int descMin = Math.max(66, lineHeight * 3 + 26);
      int rewardsDesired = this.estimateRewardsSectionHeight(width, questKey);
      int objectivesDesired = this.estimateObjectivesSectionHeight(width, saga);
      int rewardsCap = Math.max(rewardsMin, (int)((float)totalHeight * 0.35F));
      int objectivesCap = Math.max(objectivesMin, (int)((float)totalHeight * 0.35F));
      int rewardsH = Math.min(rewardsCap, Math.max(rewardsMin, rewardsDesired));
      int objectivesH = Math.min(objectivesCap, Math.max(objectivesMin, objectivesDesired));
      int descH = totalHeight - titleH - rewardsH - objectivesH;
      if (descH < descMin) {
         int deficit = descMin - descH;
         int shrinkRewards = Math.max(0, rewardsH - rewardsMin);
         int shrinkObjectives = Math.max(0, objectivesH - objectivesMin);
         int takeFromRewards = Math.min(deficit, shrinkRewards);
         rewardsH -= takeFromRewards;
         deficit -= takeFromRewards;
         int takeFromObjectives = Math.min(deficit, shrinkObjectives);
         objectivesH -= takeFromObjectives;
      }

      descH = Math.max(40, totalHeight - titleH - rewardsH - objectivesH);
      return new QuestTreeScreen.DetailPanelLayout(titleH, rewardsH, descH, objectivesH);
   }

   private List<String> titleLines(int width) {
      if (this.frameTitleLines != null && this.frameTitleQuest == this.selectedQuest && this.frameTitleWidth == width) {
         return this.frameTitleLines;
      } else {
         String title = LocalizationUtil.localizedOrReadableText(this.selectedQuest.getTitle());
         List<String> lines = this.limitLinesWithEllipsis(this.wrapText(title, width - 10), 2, width - 10);
         this.frameTitleLines = lines;
         this.frameTitleQuest = this.selectedQuest;
         this.frameTitleWidth = width;
         return lines;
      }
   }

   private int estimateTitleSectionHeight(int width) {
      int lineHeight = this.getDetailLineHeight();
      return Math.max(32, 16 + this.titleLines(width).size() * lineHeight + 8);
   }

   private int estimateRewardsSectionHeight(int width, String questKey) {
      List<QuestTreeScreen.RewardBlock> blocks = this.buildRewardBlocks(width);
      if (blocks.isEmpty()) {
         return 28;
      } else {
         int content = 0;

         for (QuestTreeScreen.RewardBlock block : blocks) {
            content += block.height();
         }

         return 22 + content + 4;
      }
   }

   private int estimateObjectivesSectionHeight(int width, Saga saga) {
      if (!this.selectedQuest.hasStartRequirements() && this.selectedQuest.getObjectives().isEmpty()) {
         return 28;
      } else {
         List<String> objectiveLines = this.objectiveRenderLines(saga, width - 30);
         return 24 + objectiveLines.size() * this.getDetailLineHeight() + 6;
      }
   }

   private void renderTopSection(GuiGraphics graphics, int x, int y, int width, int height, QuestTreeScreen.QuestNodeStatus status) {
      graphics.fill(x, y, x + width, y + height, 1141969186);
      graphics.renderOutline(x, y, width, height, -2008791962);
      int lineHeight = this.getDetailLineHeight();
      List<String> wrappedTitle = this.titleLines(width);
      int titleStartY = y + 6;

      for (String line : wrappedTitle) {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(line).withStyle(ChatFormatting.BOLD), x + width / 2, titleStartY, -1);
         titleStartY += lineHeight;
      }

      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.txt(this.fitSingleLineEllipsis(this.getStatusText(status).getString(), width - 12)),
         x + width / 2,
         height >= 40 ? y + height - lineHeight - 4 : y + 20,
         this.getStatusColor(status)
      );
   }

   private void renderRewardsSection(GuiGraphics graphics, int x, int y, int width, int height, String questKey, int mouseX, int mouseY, float dt) {
      graphics.fill(x, y, x + width, y + height, 1141969186);
      graphics.renderOutline(x, y, width, height, -2008791962);
      TextUtil.drawStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.quests.rewards", new Object[0]).copy().withStyle(ChatFormatting.BOLD), x + 6, y + 4, -10496
      );
      List<QuestTreeScreen.RewardBlock> blocks = this.buildRewardBlocks(width);
      if (blocks.isEmpty()) {
         TextUtil.drawStringWithBorder(graphics, this.font, this.txt("-"), x + 8, y + 18, -6710887);
      } else {
         int iconSize = 16;
         int lineHeight = this.getDetailLineHeight();
         int originY = y + 18;
         int viewHeight = Math.max(iconSize, height - 22);
         int totalContentHeight = 0;

         for (QuestTreeScreen.RewardBlock block : blocks) {
            totalContentHeight += block.height();
         }

         this.rewardsMaxScroll = (float)Math.max(0, totalContentHeight - viewHeight);
         this.targetRewardsScroll = Mth.clamp(this.targetRewardsScroll, 0.0F, this.rewardsMaxScroll);
         this.currentRewardsScroll = this.currentRewardsScroll
            + (this.targetRewardsScroll - this.currentRewardsScroll) * (float)(1.0 - Math.exp((double)(-15.0F * dt)));
         String fullText = this.buildRewardsText(this.getDisplayRewards(this.selectedQuest));
         int revealedChars = this.resolveTypewriterText(questKey, "rewards", fullText).length();
         graphics.enableScissor(
            this.toScreenCoord((double)(x + 2)),
            this.toScreenCoord((double)originY),
            this.toScreenCoord((double)(x + width - 2)),
            this.toScreenCoord((double)(y + height - 2))
         );
         graphics.pose().pushPose();
         graphics.pose().translate(0.0F, -this.currentRewardsScroll, 0.0F);
         int blockTop = originY;
         int consumedChars = 0;

         for (QuestTreeScreen.RewardBlock block : blocks) {
            boolean blockVisible = (float)(blockTop + block.height()) >= (float)originY + this.currentRewardsScroll
               && (float)blockTop <= (float)(originY + viewHeight) + this.currentRewardsScroll;
            if (block.isHeader()) {
               if (blockVisible) {
                  TextUtil.drawStringWithBorder(graphics, this.font, block.header(), x + 8, blockTop + 2, block.headerColor());
               }

               blockTop += block.height();
            } else {
               QuestReward reward = block.reward();
               String desc = this.rewardDescription(reward).getString();
               int rowVisible = Math.max(0, revealedChars - consumedChars);
               consumedChars += desc.length() + 1;
               if (blockVisible) {
                  int iconX = x + 8;
                  ItemStack iconStack = this.rewardIconStack(reward);
                  boolean rewardIsItem = reward.getType() == QuestReward.RewardType.ITEM || reward.getType() == QuestReward.RewardType.GENERIC_ITEM;
                  ItemStack tooltipStack = rewardIsItem ? iconStack : null;
                  int textColor = block.locked() ? -8947849 : -3355444;
                  if (iconStack != null) {
                     graphics.renderItem(iconStack, iconX, blockTop);
                  } else {
                     graphics.blit(REWARD_GENERIC_ICON, iconX, blockTop, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
                  }

                  this.rewardHitboxes
                     .add(
                        new QuestTreeScreen.RewardHitbox(
                           iconX, (int)((float)blockTop - this.currentRewardsScroll), iconSize, tooltipStack, this.rewardDescription(reward)
                        )
                     );
                  int charsLeft = rowVisible;
                  int textY = blockTop;

                  for (String fullLine : block.lines()) {
                     String shownLine;
                     if (charsLeft >= fullLine.length()) {
                        shownLine = fullLine;
                        charsLeft -= fullLine.length();
                     } else {
                        shownLine = fullLine.substring(0, Math.max(0, charsLeft));
                        charsLeft = 0;
                     }

                     TextUtil.drawStringWithBorder(graphics, this.font, this.txt(shownLine), x + 28, textY, textColor);
                     textY += lineHeight;
                  }
               }

               blockTop += block.height();
            }
         }

         graphics.pose().popPose();
         graphics.disableScissor();
         this.rewardsBar.update(x + width - 6, 3, originY, viewHeight, this.rewardsMaxScroll);
         if (this.rewardsMaxScroll > 0.0F) {
            int scrollBarX = x + width - 6;
            graphics.fill(scrollBarX, originY, scrollBarX + 3, originY + viewHeight, -13421773);
            float scrollPercent = this.currentRewardsScroll / this.rewardsMaxScroll;
            int indicatorHeight = Math.max(10, (int)((float)viewHeight / (float)totalContentHeight * (float)viewHeight));
            int indicatorY = originY + (int)((float)(viewHeight - indicatorHeight) * scrollPercent);
            graphics.fill(scrollBarX, indicatorY, scrollBarX + 3, indicatorY + indicatorHeight, -5592406);
         }
      }
   }

   private List<QuestTreeScreen.RewardBlock> buildRewardBlocks(int width) {
      List<QuestTreeScreen.RewardBlock> blocks = new ArrayList<>();
      int textWidth = Math.max(20, width - 40);
      int iconSize = 16;
      int lineHeight = this.getDetailLineHeight();
      Difficulty difficulty = this.statsData != null ? this.statsData.getPlayerQuestData().getDifficulty() : Difficulty.NORMAL;
      Difficulty effective = difficulty != null ? difficulty : Difficulty.NORMAL;
      List<QuestTextFormatter.RewardGroup> groups = QuestTextFormatter.groupRewardsByDifficulty(this.selectedQuest.getRewards(), true);
      boolean tiered = QuestTextFormatter.hasRewardTiers(this.selectedQuest.getRewards());

      for (QuestTextFormatter.RewardGroup group : groups) {
         if (!group.rewards().isEmpty()) {
            if (tiered) {
               boolean groupLocked = !group.difficulties().contains(effective);
               Component header = QuestTextFormatter.describeRewardDifficulties(group.difficulties());
               int headerColor = QuestTextFormatter.rewardDifficultyColor(group.difficulties(), groupLocked);
               blocks.add(new QuestTreeScreen.RewardBlock(null, List.of(header.getString()), lineHeight + 4, header, groupLocked, headerColor));
            }

            for (QuestReward reward : group.rewards()) {
               List<String> lines = this.wrapText(this.rewardDescription(reward).getString(), textWidth);
               if (lines.isEmpty()) {
                  lines = List.of("");
               }

               int textBlockH = lines.size() * lineHeight;
               int blockH = Math.max(iconSize + 2, textBlockH) + 4;
               blocks.add(new QuestTreeScreen.RewardBlock(reward, lines, blockH, null, !reward.isUnlockedFor(effective), 0));
            }
         }
      }

      return blocks;
   }

   private List<QuestReward> getDisplayRewards(Quest quest) {
      List<QuestReward> shown = new ArrayList<>();
      if (quest == null) {
         return shown;
      } else {
         for (QuestTextFormatter.RewardGroup group : QuestTextFormatter.groupRewardsByDifficulty(quest.getRewards(), true)) {
            shown.addAll(group.rewards());
         }

         return shown;
      }
   }

   private Component rewardDescription(QuestReward reward) {
      if (reward instanceof TransformationReward transformation) {
         String group = transformation.getFormGroup();
         String form = transformation.getFormName();
         if (this.isStackFormGroup(group)) {
            return Component.translatable("race.dragonminez.stack.group." + group)
               .append(": ")
               .append(Component.translatable("race.dragonminez.stack.form." + group + "." + form));
         } else {
            String race = this.statsData != null ? this.statsData.getCharacter().getRaceName() : "";
            return Component.translatable("race.dragonminez." + race + ".form." + group + "." + form);
         }
      } else {
         double rewardMultiplier = this.statsData != null ? this.statsData.getPlayerQuestData().rewardMultiplierFor(reward) : 1.0;
         return reward.getDescription(rewardMultiplier);
      }
   }

   private boolean isStackFormGroup(String formGroup) {
      if (formGroup != null && !formGroup.isEmpty()) {
         FormConfig stackGroup = ConfigManager.getStackFormGroup(formGroup);
         return stackGroup != null && stackGroup.getFormType() != null
            ? ConfigManager.getSkillsConfig().getStackSkills().contains(stackGroup.getFormType().toLowerCase())
            : false;
      } else {
         return false;
      }
   }

   private ItemStack rewardIconStack(QuestReward reward) {
      switch (reward.getType()) {
         case ITEM:
            if (reward instanceof ItemReward itemReward) {
               Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemReward.getItemId()));
               return new ItemStack(item, Math.max(1, itemReward.getCount()));
            }

            return null;
         case GENERIC_ITEM:
            if (reward instanceof GenericItemReward genericItemReward) {
               return genericItemReward.getItemReward().getItemStack();
            }

            return null;
         case TPS:
            return new ItemStack((ItemLike)MainItems.RED_CAPSULE.get());
         case SKILL:
            return new ItemStack((ItemLike)MainItems.GETE_BLUE_CAPSULE.get());
         case COMMAND:
            return new ItemStack(Items.COMMAND_BLOCK);
         case KI_TECHNIQUE:
            return new ItemStack((ItemLike)MainItems.MERUS_LASER.get());
         case TRANSFORMATION:
            return new ItemStack((ItemLike)MainItems.MIGHT_TREE_FRUIT.get());
         default:
            return null;
      }
   }

   private void renderDescriptionSection(GuiGraphics graphics, int x, int y, int width, int height, String questKey, float dt) {
      graphics.fill(x, y, x + width, y + height, 1141969186);
      graphics.renderOutline(x, y, width, height, -2008791962);
      TextUtil.drawStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.quest_tree.description", new Object[0]).copy().withStyle(ChatFormatting.BOLD), x + 6, y + 4, -10496
      );
      String fullDescription = this.tr(this.selectedQuest.getDescription(), new Object[0]).getString();
      String visibleDescription = this.resolveTypewriterText(questKey, "desc", fullDescription);
      List<String> lines = this.wrapText(visibleDescription, width - 14);
      int lineHeight = 9 + 2;
      int viewHeight = height - 24;
      int totalContentHeight = lines.size() * lineHeight;
      this.descMaxScroll = (float)Math.max(0, totalContentHeight - viewHeight);
      this.targetDescScroll = Mth.clamp(this.targetDescScroll, 0.0F, this.descMaxScroll);
      this.currentDescScroll = this.currentDescScroll + (this.targetDescScroll - this.currentDescScroll) * (float)(1.0 - Math.exp((double)(-15.0F * dt)));
      graphics.enableScissor(
         this.toScreenCoord((double)(x + 4)),
         this.toScreenCoord((double)(y + 18)),
         this.toScreenCoord((double)(x + width - 6)),
         this.toScreenCoord((double)(y + height - 2))
      );
      graphics.pose().pushPose();
      graphics.pose().translate(0.0F, -this.currentDescScroll, 0.0F);
      int descOriginY = y + 18;

      for (int i = 0; i < lines.size(); i++) {
         float lineY = (float)(descOriginY + i * lineHeight);
         if (lineY + (float)lineHeight >= (float)descOriginY + this.currentDescScroll && lineY <= (float)(descOriginY + viewHeight) + this.currentDescScroll) {
            TextUtil.drawStringWithBorder(graphics, this.font, this.txt(lines.get(i)), x + 6, (int)lineY, -3355444);
         }
      }

      graphics.pose().popPose();
      this.descBar.update(x + width - 10, 3, descOriginY, viewHeight, this.descMaxScroll);
      if (this.descMaxScroll > 0.0F) {
         int scrollBarX = x + width - 10;
         graphics.fill(scrollBarX, descOriginY, scrollBarX + 3, descOriginY + viewHeight, -13421773);
         float scrollPercent = this.currentDescScroll / this.descMaxScroll;
         int indicatorHeight = Math.max(10, (int)((float)viewHeight / (float)totalContentHeight * (float)viewHeight));
         int indicatorY = descOriginY + (int)((float)(viewHeight - indicatorHeight) * scrollPercent);
         graphics.fill(scrollBarX, indicatorY, scrollBarX + 3, indicatorY + indicatorHeight, -5592406);
      }

      graphics.disableScissor();
   }

   private void renderObjectivesSection(GuiGraphics graphics, int x, int y, int width, int height, Saga saga, float dt) {
      graphics.fill(x, y, x + width, y + height, 1141969186);
      graphics.renderOutline(x, y, width, height, -2008791962);
      String sectionTitle = this.selectedQuest.hasStartRequirements()
         ? this.tr("gui.dragonminez.quests.objectives_requirements", new Object[0]).getString()
         : this.tr("gui.dragonminez.quests.objectives", new Object[0]).getString();
      TextUtil.drawStringWithBorder(
         graphics, this.font, this.txt(this.fitSingleLineEllipsis(sectionTitle, width - 14)).withStyle(ChatFormatting.BOLD), x + 6, y + 4, -10496
      );
      List<String> lines = this.objectiveRenderLines(saga, width - 30);
      if (lines.isEmpty()) {
         TextUtil.drawStringWithBorder(graphics, this.font, this.txt("-"), x + 8, y + 18, -6710887);
      } else {
         int lineHeight = this.getDetailLineHeight();
         int viewHeight = height - 24;
         int totalContentHeight = lines.size() * lineHeight;
         this.objMaxScroll = (float)Math.max(0, totalContentHeight - viewHeight);
         this.targetObjScroll = Mth.clamp(this.targetObjScroll, 0.0F, this.objMaxScroll);
         this.currentObjScroll = this.currentObjScroll + (this.targetObjScroll - this.currentObjScroll) * (float)(1.0 - Math.exp((double)(-15.0F * dt)));
         int drawY = y + 18;
         graphics.enableScissor(
            this.toScreenCoord((double)(x + 4)),
            this.toScreenCoord((double)(y + 18)),
            this.toScreenCoord((double)(x + width - 6)),
            this.toScreenCoord((double)(y + height - 2))
         );
         graphics.pose().pushPose();
         graphics.pose().translate(0.0F, -this.currentObjScroll, 0.0F);

         for (int i = 0; i < lines.size(); i++) {
            float lineY = (float)(drawY + i * lineHeight);
            if (lineY + (float)lineHeight >= (float)drawY + this.currentObjScroll && lineY <= (float)(drawY + viewHeight) + this.currentObjScroll) {
               this.drawObjectiveLineWithSymbolColors(graphics, lines.get(i), x + 8, (int)lineY);
            }
         }

         graphics.pose().popPose();
         graphics.disableScissor();
         int objContentY = y + 18;
         int objContentH = Math.max(8, height - 24);
         this.objBar.update(x + width - 4, 2, objContentY, objContentH, this.objMaxScroll);
         if (this.objMaxScroll > 0.0F) {
            int scrollBarX = x + width - 4;
            graphics.fill(scrollBarX, objContentY, scrollBarX + 2, objContentY + objContentH, -13421773);
            float scrollPercent = this.objMaxScroll == 0.0F ? 0.0F : this.currentObjScroll / this.objMaxScroll;
            int indicatorHeight = Math.max(10, (int)((float)viewHeight / (float)totalContentHeight * (float)objContentH));
            int indicatorY = objContentY + (int)((float)(objContentH - indicatorHeight) * scrollPercent);
            graphics.fill(scrollBarX, indicatorY, scrollBarX + 2, indicatorY + indicatorHeight, -5592406);
         }
      }
   }

   private List<String> objectiveRenderLines(Saga saga, int textWidth) {
      if (this.frameObjLinesCache != null && this.frameObjLinesQuest == this.selectedQuest && this.frameObjLinesWidth == textWidth) {
         return this.frameObjLinesCache;
      } else {
         List<String> lines = this.buildObjectiveRenderLines(saga, textWidth);
         this.frameObjLinesCache = lines;
         this.frameObjLinesQuest = this.selectedQuest;
         this.frameObjLinesWidth = textWidth;
         return lines;
      }
   }

   private List<String> buildObjectiveRenderLines(Saga saga, int textWidth) {
      List<String> lines = new ArrayList<>();
      if (this.statsData != null && this.selectedQuest != null) {
         if (this.selectedQuest.hasStartRequirements()) {
            lines.add(this.tr("gui.dragonminez.quests.requirements", new Object[0]).getString() + ":");
            this.appendRequirementLines(lines, this.selectedQuest.getStartRequirements(), this.questProgressKey(saga, this.selectedQuest), 0, textWidth);
            if (!this.selectedQuest.getObjectives().isEmpty()) {
               lines.add(this.tr("gui.dragonminez.quests.objectives", new Object[0]).getString() + ":");
            }
         }

         PlayerQuestData pqd = this.statsData.getPlayerQuestData();
         String questKey = this.questProgressKey(saga, this.selectedQuest);
         List<QuestObjective> objectives = this.selectedQuest.getObjectives();

         for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            int progress = pqd.getObjectiveProgress(questKey, i);
            boolean completed = progress >= this.selectedQuest.getObjectiveRequired(pqd, questKey, i);
            String marker = completed ? "✓ " : "✕ ";
            String baseText = this.getObjectiveText(pqd, questKey, objective, i, progress);
            marker = completed ? "+ " : "x ";
            List<String> wrapped = this.wrapText(baseText, Math.max(12, textWidth - TextUtil.width(this.font, marker, DMZ_STYLE)));
            if (wrapped.isEmpty()) {
               lines.add(marker);
            } else {
               lines.add(marker + wrapped.get(0));

               for (int j = 1; j < wrapped.size(); j++) {
                  lines.add("  " + wrapped.get(j));
               }
            }
         }

         return lines;
      } else {
         return lines;
      }
   }

   private String getObjectiveText(PlayerQuestData pqd, String questKey, QuestObjective objective, int objectiveIndex, int currentProgress) {
      String description = QuestTextFormatter.describeObjective(objective).getString();
      int required = this.selectedQuest != null ? this.selectedQuest.getObjectiveRequired(pqd, questKey, objectiveIndex) : objective.getRequired();
      return objective.getType() != QuestObjective.ObjectiveType.KILL
            && objective.getType() != QuestObjective.ObjectiveType.ITEM
            && objective.getType() != QuestObjective.ObjectiveType.SKILL
         ? description
         : description + " (" + currentProgress + "/" + required + ")";
   }

   private void appendRequirementLines(List<String> lines, QuestPrerequisites requirements, String questKey, int depth, int textWidth) {
      if (requirements != null && requirements.conditions() != null && !requirements.conditions().isEmpty()) {
         boolean showGroupLabel = depth > 0 || requirements.conditions().size() > 1;
         if (showGroupLabel) {
            String groupLabel = requirements.operator() == QuestPrerequisites.Operator.AND
               ? this.tr("gui.dragonminez.quests.requirements_all", new Object[0]).getString()
               : this.tr("gui.dragonminez.quests.requirements_any", new Object[0]).getString();
            this.addWrappedRequirementLine(lines, this.indent(depth), groupLabel, textWidth);
            depth++;
         }

         for (QuestPrerequisites.Condition condition : requirements.conditions()) {
            if (condition != null) {
               if (condition.isNestedGroup()) {
                  this.appendRequirementLines(lines, condition.getNested(), questKey, depth, textWidth);
               } else {
                  List<String> conditionLines = this.describeRequirementLines(condition, questKey);
                  if (!conditionLines.isEmpty()) {
                     String bulletPrefix = this.indent(depth) + "- ";
                     String continuationPrefix = this.indent(depth + 1);

                     for (int i = 0; i < conditionLines.size(); i++) {
                        this.addWrappedRequirementLine(lines, i == 0 ? bulletPrefix : continuationPrefix, conditionLines.get(i), textWidth);
                     }
                  }
               }
            }
         }
      }
   }

   private List<String> describeRequirementLines(QuestPrerequisites.Condition condition, String questKey) {
      List<String> lines = new ArrayList<>();
      if (condition != null && condition.getType() != null) {
         Minecraft mc = Minecraft.getInstance();
         lines.add(
            QuestTextFormatter.describeRequirement(condition, new QuestTextFormatter.RequirementContext(this.statsData, mc.player, questKey)).getString()
         );
         return lines;
      } else {
         return lines;
      }
   }

   private void addWrappedRequirementLine(List<String> lines, String prefix, String text, int textWidth) {
      int wrapWidth = Math.max(12, textWidth - TextUtil.width(this.font, prefix, DMZ_STYLE));
      List<String> wrapped = this.wrapText(text, wrapWidth);
      if (wrapped.isEmpty()) {
         lines.add(prefix.trim());
      } else {
         String continuationPrefix = this.indentFromPrefix(prefix);
         lines.add(prefix + wrapped.get(0));

         for (int i = 1; i < wrapped.size(); i++) {
            lines.add(continuationPrefix + wrapped.get(i));
         }
      }
   }

   private String indent(int depth) {
      return "  ".repeat(Math.max(0, depth));
   }

   private String indentFromPrefix(String prefix) {
      return prefix.endsWith("- ") ? prefix.substring(0, prefix.length() - 2) + "  " : prefix;
   }

   private void renderRewardTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
      for (QuestTreeScreen.RewardHitbox hitbox : this.rewardHitboxes) {
         if (hitbox.contains(mouseX, mouseY)) {
            if (hitbox.stack != null && !hitbox.stack.isEmpty()) {
               graphics.renderTooltip(this.font, hitbox.stack, mouseX, mouseY);
            } else {
               this.renderSimpleTooltip(graphics, List.of(hitbox.tooltip), mouseX, mouseY);
            }

            return;
         }
      }
   }

   private boolean renderActionButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
      if (this.actionButton != null && !this.actionButtonTooltip.isEmpty() && !this.actionButton.active && this.actionButton.visible) {
         if (!this.actionButton.isMouseOver((double)mouseX, (double)mouseY)) {
            return false;
         } else {
            this.renderSimpleTooltip(graphics, this.actionButtonTooltip, mouseX, mouseY);
            return true;
         }
      } else {
         return false;
      }
   }

   private void renderSimpleTooltip(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY) {
      if (lines != null && !lines.isEmpty()) {
         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), null, lines, null, 16777215);
      }
   }

   private List<Component> buildQuestBlockerTooltip(Quest quest, Saga saga, boolean includePartyNote) {
      List<Component> lines = new ArrayList<>();
      if (quest != null && this.statsData != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return lines;
         } else {
            Component availabilityFailure = QuestAvailabilityChecker.describeAvailabilityFailure(quest, this.statsData);
            if (availabilityFailure != null) {
               lines.add(availabilityFailure);
               return lines;
            } else if (saga == null && !quest.isSagaQuest()) {
               return lines;
            } else {
               String questKey = this.questProgressKey(saga, quest);
               Component startFailure = QuestAvailabilityChecker.describeStartRequirementFailure(quest, questKey, mc.player, this.statsData);
               if (startFailure != null) {
                  lines.add(startFailure);
                  if (includePartyNote && this.statsData.getPlayerQuestData().isInParty()) {
                     lines.add(this.tr("gui.dragonminez.party.start_requirements_all", new Object[0]));
                  }
               }

               return lines;
            }
         }
      } else {
         return lines;
      }
   }

   private void renderSidePanelBackground(GuiGraphics graphics, QuestTreeScreen.PanelRect panel, boolean flushLeft, boolean flushRight, boolean drawFrame) {
      if (drawFrame) {
         graphics.fill(panel.x, panel.y, panel.right(), panel.bottom(), -871428064);
      }

      int drawX = panel.x - (flushLeft ? 3 : 0);
      int drawW = panel.width + (flushLeft ? 3 : 0) + (flushRight ? 3 : 0);
      float srcPixelsPerDstPixel = panel.width <= 0 ? 1.0F : 282.0F / (float)panel.width;
      int srcBleed = Math.max(0, Math.round(3.0F * srcPixelsPerDstPixel));
      int srcU = 1 - (flushLeft ? srcBleed : 0);
      int srcW = 282 + (flushLeft ? srcBleed : 0) + (flushRight ? srcBleed : 0);
      int srcV = 1;
      int srcH = 426;
      if (srcU < 0) {
         srcW += srcU;
         srcU = 0;
      }

      srcW = Math.max(1, Math.min(srcW, 512 - srcU));
      srcV = Math.max(0, Math.min(srcV, 511));
      srcH = Math.max(1, Math.min(srcH, 512 - srcV));
      graphics.blit(QUEST_MENU, drawX, panel.y, drawW, panel.height, (float)srcU, (float)srcV, srcW, srcH, 512, 512);
      if (drawFrame) {
         graphics.fill(panel.x, panel.y, panel.right(), panel.y + 1, -1436917894);
         graphics.fill(panel.x, panel.bottom() - 1, panel.right(), panel.bottom(), -1436917894);
         if (!flushLeft) {
            graphics.fill(panel.x, panel.y, panel.x + 1, panel.bottom(), -1436917894);
         }

         if (!flushRight) {
            graphics.fill(panel.right() - 1, panel.y, panel.right(), panel.bottom(), -1436917894);
         }
      }
   }

   private String resolveTypewriterText(String questKey, String section, String fullText) {
      if (fullText != null && !fullText.isEmpty()) {
         String key = questKey + "#" + section;
         long now = System.currentTimeMillis();
         long lastReveal = this.sectionLastReveal.getOrDefault(key, 0L);
         boolean shouldAnimate = now - lastReveal >= 300000L;
         if (!shouldAnimate) {
            this.sectionAnimationStart.remove(key);
            return fullText;
         } else {
            long start = this.sectionAnimationStart.computeIfAbsent(key, ignored -> now);
            long elapsed = Math.max(0L, now - start);
            int visibleChars = (int)((float)elapsed / 1000.0F * 55.0F);
            if (visibleChars >= fullText.length()) {
               this.sectionLastReveal.put(key, now);
               this.sectionAnimationStart.remove(key);
               return fullText;
            } else {
               return fullText.substring(0, Math.max(0, visibleChars));
            }
         }
      } else {
         return "";
      }
   }

   private String buildRewardsText(List<QuestReward> rewards) {
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < rewards.size(); i++) {
         if (i > 0) {
            builder.append('\n');
         }

         builder.append(this.rewardDescription(rewards.get(i)).getString());
      }

      return builder.toString();
   }

   private void renderConnections(GuiGraphics graphics, int panOffX, int panOffY, float viewRight, float viewBottom) {
      for (QuestTreeScreen.ConnRender conn : this.connRenders) {
         int x1 = conn.baseX1() + panOffX;
         int y1 = conn.baseY1() + panOffY;
         int x2 = conn.baseX2() + panOffX;
         int y2 = conn.baseY2() + panOffY;
         if ((y1 == y2 || x1 == x2)
            && Math.max(x1, x2) >= 0
            && !((float)Math.min(x1, x2) > viewRight)
            && Math.max(y1, y2) >= 0
            && !((float)Math.min(y1, y2) > viewBottom)) {
            if (y1 == y2) {
               graphics.fill(Math.min(x1, x2) - 1, y1 - 1, Math.max(x1, x2) + 1, y1 + 1, conn.color());
            } else {
               graphics.fill(x1 - 1, Math.min(y1, y2) - 1, x1 + 1, Math.max(y1, y2) + 1, conn.color());
            }
         }
      }

      BufferBuilder buf = null;
      Matrix4f mat = graphics.pose().last().pose();
      boolean began = false;

      for (QuestTreeScreen.ConnRender connx : this.connRenders) {
         int x1 = connx.baseX1() + panOffX;
         int y1 = connx.baseY1() + panOffY;
         int x2 = connx.baseX2() + panOffX;
         int y2 = connx.baseY2() + panOffY;
         if (y1 != y2
            && x1 != x2
            && Math.max(x1, x2) >= 0
            && !((float)Math.min(x1, x2) > viewRight)
            && Math.max(y1, y2) >= 0
            && !((float)Math.min(y1, y2) > viewBottom)) {
            if (!began) {
               graphics.flush();
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               RenderSystem.disableCull();
               RenderSystem.setShader(GameRenderer::getPositionColorShader);
               buf = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
               began = true;
            }

            this.appendLineQuad(buf, mat, x1, y1, x2, y2, 2.0F, connx.color());
         }
      }

      if (began && buf != null) {
         BufferUploader.drawWithShader(buf.buildOrThrow());
         RenderSystem.enableCull();
         RenderSystem.disableBlend();
      }
   }

   private void appendLineQuad(BufferBuilder buf, Matrix4f mat, int x1, int y1, int x2, int y2, float thickness, int color) {
      float dx = (float)(x2 - x1);
      float dy = (float)(y2 - y1);
      float len = (float)Math.sqrt((double)(dx * dx + dy * dy));
      if (!(len < 1.0E-4F)) {
         float h = thickness / 2.0F;
         float nx = -dy / len * h;
         float ny = dx / len * h;
         float a = (float)(color >>> 24 & 0xFF) / 255.0F;
         float r = (float)(color >> 16 & 0xFF) / 255.0F;
         float g = (float)(color >> 8 & 0xFF) / 255.0F;
         float b = (float)(color & 0xFF) / 255.0F;
         buf.addVertex(mat, (float)x1 + nx, (float)y1 + ny, 0.0F).setColor(r, g, b, a);
         buf.addVertex(mat, (float)x2 + nx, (float)y2 + ny, 0.0F).setColor(r, g, b, a);
         buf.addVertex(mat, (float)x2 - nx, (float)y2 - ny, 0.0F).setColor(r, g, b, a);
         buf.addVertex(mat, (float)x1 - nx, (float)y1 - ny, 0.0F).setColor(r, g, b, a);
      }
   }

   private void renderNode(GuiGraphics graphics, QuestTreeScreen.NodeRender node, int x, int y, boolean isHovered) {
      boolean isBlurred = node.blurred();
      boolean isSelected = this.selectedQuest != null && this.sameQuestIdentity(this.selectedQuest, node.quest());
      if (isSelected && !isBlurred) {
         graphics.fill(x - 2, y - 2, x + 18 + 2, y + 18 + 2, -1426063361);
      }

      if (isHovered && !isBlurred) {
         graphics.fill(x - 1, y - 1, x + 18 + 1, y + 18 + 1, 1728053247);
      }

      graphics.fill(x, y, x + 18, y + 18, node.borderColor());
      graphics.fill(x + 1, y + 1, x + 18 - 1, y + 18 - 1, node.bgColor());
      int iconX = x + node.iconOffsetX();
      int iconY = y + 5;
      graphics.drawString(this.font, node.iconComp(), iconX, iconY, node.iconColor(), false);
      if (!isBlurred && node.sidequest()) {
         int badgeX = x - 4;
         int badgeY = y - 4;
         graphics.fill(badgeX, badgeY, badgeX + 8, badgeY + 8, -10074966);
         graphics.drawString(this.font, "S", badgeX + 1, badgeY, -1, false);
      } else if (node.bottomLabel() != null) {
         TextUtil.drawStringWithBorder(graphics, this.font, node.bottomLabel(), x + node.bottomLabelOffsetX(), y + 18 + 2, node.bottomLabelColor());
      }

      if (node.status() == QuestTreeScreen.QuestNodeStatus.CLAIMABLE && !isBlurred) {
         float pulse = (float)(Math.sin((double)System.currentTimeMillis() / 350.0) * 0.5 + 0.5);
         float alpha = 0.3F + pulse * 0.7F;
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
         int iconDrawX = x + 18 - 2;
         int iconDrawY = y - 15 + 4;
         graphics.blit(EXCLAMATION_MARK, iconDrawX, iconDrawY, 6, 15, 0.0F, 0.0F, 97, 250, 97, 250);
         graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   private void renderNodeTooltip(GuiGraphics graphics, Quest quest, int mouseX, int mouseY) {
      QuestTreeScreen.NodeVisibility vis = this.getNodeVisibility(quest);
      if (vis != QuestTreeScreen.NodeVisibility.HIDDEN) {
         List<Component> desc = new ArrayList<>();
         List<Component> extras = new ArrayList<>();
         Component title;
         int color;
         if (vis == QuestTreeScreen.NodeVisibility.BLURRED) {
            title = this.txt("Unknown Quest").withStyle(ChatFormatting.OBFUSCATED);
            desc.add(this.tr("gui.dragonminez.quest_tree.status.locked", new Object[0]));
            color = -7829368;
         } else {
            title = this.txt(LocalizationUtil.localizedOrReadableText(quest.getTitle())).withStyle(ChatFormatting.BOLD);
            QuestTreeScreen.QuestNodeStatus status = this.getNodeStatus(quest);
            if (status == QuestTreeScreen.QuestNodeStatus.CLAIMABLE && quest.getClaimMode() == Quest.ClaimMode.NPC_ONLY) {
               desc.add(this.tr("gui.dragonminez.quests.claim_from_npc", new Object[0]));
               desc.add(this.tr("gui.dragonminez.quests.claim_from_npc.tooltip", new Object[0]));
            } else {
               desc.add(this.getStatusText(status));
            }

            extras.addAll(this.buildQuestBlockerTooltip(quest, this.availableSagas.isEmpty() ? null : this.availableSagas.get(this.currentSagaIndex), false));
            color = this.getStatusColor(status);
         }

         TextUtil.renderAdvancedTooltip(graphics, this.font, mouseX, mouseY, this.getUiWidth(), this.getUiHeight(), title, desc, extras, color);
      }
   }

   private void renderActionButtonGlow(GuiGraphics graphics) {
      if (this.actionButton != null) {
         float pulse = (float)(Math.sin((double)System.currentTimeMillis() / 500.0) * 0.5 + 0.5);
         int btnX = this.actionButton.getX();
         int btnY = this.actionButton.getY();
         int btnW = this.actionButton.getWidth();
         int btnH = this.actionButton.getHeight();
         int glowAlpha = (int)(pulse * 45.0F);
         int glowColor = glowAlpha << 24 | 8965341;
         graphics.fill(btnX - 2, btnY - 2, btnX + btnW + 2, btnY + btnH + 2, glowColor);
      }
   }

   private boolean hasOtherOnlinePlayers() {
      Minecraft mc = Minecraft.getInstance();
      return mc.getConnection() != null && mc.getConnection().getOnlinePlayers().size() > 1;
   }

   private boolean isLocalPartyLeader() {
      return this.statsData != null && Minecraft.getInstance().player != null
         ? this.statsData.getPlayerQuestData().isPartyLeader(Minecraft.getInstance().player.getUUID())
         : false;
   }

   private PlayerQuestData.PartyInviteData getVisiblePartyInvite() {
      if (this.statsData == null) {
         return null;
      } else {
         PlayerQuestData.PartyInviteData invite = this.statsData.getPlayerQuestData().getPendingPartyInviteData();
         return invite != null && !invite.isExpired() ? invite : null;
      }
   }

   private String resolveInviteName(PlayerQuestData.PartyInviteData invite) {
      if (invite == null) {
         return "";
      } else {
         return invite.getInviterName() != null && !invite.getInviterName().isBlank()
            ? invite.getInviterName()
            : this.resolvePlayerName(invite.getInviterUUID());
      }
   }

   private String resolvePlayerName(UUID playerId) {
      if (playerId == null) {
         return "";
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && playerId.equals(mc.player.getUUID())) {
            return mc.player.getGameProfile().getName();
         } else {
            if (mc.getConnection() != null) {
               for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                  if (info.getProfile().getId().equals(playerId)) {
                     return info.getProfile().getName();
                  }
               }
            }

            return playerId.toString().substring(0, 8);
         }
      }
   }

   private String buildPartyMemberSummary() {
      if (this.statsData == null) {
         return "";
      } else {
         List<String> names = new ArrayList<>();

         for (UUID memberId : this.statsData.getPlayerQuestData().getPartyMemberIds()) {
            names.add(this.resolvePlayerName(memberId));
         }

         return this.tr("gui.dragonminez.party.members", new Object[]{String.join(", ", names)}).getString();
      }
   }

   private void syncPartyButtonPositions() {
      QuestTreeScreen.PanelRect footer = this.getPartyFooterRect();
      if (footer != null) {
         int centerX = footer.x + (footer.width - 74) / 2;
         if (this.partySecondaryButton != null) {
            this.partySecondaryButton.setX(centerX);
            this.partySecondaryButton.setY(footer.bottom() - 20);
         }

         if (this.partyPrimaryButton != null) {
            this.partyPrimaryButton.setX(centerX);
            this.partyPrimaryButton.setY(this.partySecondaryButton != null ? footer.bottom() - 46 : footer.bottom() - 20);
         }
      }
   }

   private void queuePartyRefresh() {
      this.pendingRefreshTicks = Math.max(this.pendingRefreshTicks, 5);
   }

   private void openInvitePopup() {
      if (this.getVisiblePartyInvite() == null) {
         this.invitePopupOpen = true;
         this.confirmOverlayOpen = false;
         this.invitePopupScroll = 0;
         this.rebuildInviteEntries();
      }
   }

   private void rebuildInviteEntries() {
      this.inviteEntries.clear();
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.player != null && this.statsData != null) {
         List<UUID> currentPartyMembers = this.statsData.getPlayerQuestData().getPartyMemberIds();

         for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            UUID playerId = info.getProfile().getId();
            if (playerId != null && !playerId.equals(mc.player.getUUID()) && !currentPartyMembers.contains(playerId)) {
               this.inviteEntries.add(new QuestTreeScreen.PartyInviteEntry(playerId, info.getProfile().getName()));
            }
         }

         this.inviteEntries.sort(Comparator.comparing(QuestTreeScreen.PartyInviteEntry::playerName, String.CASE_INSENSITIVE_ORDER));
         this.invitePopupScroll = Math.max(0, Math.min(this.invitePopupScroll, Math.max(0, this.inviteEntries.size() - this.getInvitePopupVisibleRows())));
      }
   }

   private void requestConfirm(QuestTreeScreen.PartyConfirmAction action, Component title, Component body) {
      this.confirmAction = action;
      this.confirmTitle = title;
      this.confirmBody = body;
      this.confirmOverlayOpen = true;
      this.invitePopupOpen = false;
   }

   private void closeTransientOverlay() {
      this.invitePopupOpen = false;
      this.confirmOverlayOpen = false;
      this.confirmAction = QuestTreeScreen.PartyConfirmAction.NONE;
      this.confirmTitle = Component.empty();
      this.confirmBody = Component.empty();
   }

   private void executeConfirmAction() {
      switch (this.confirmAction) {
         case NONE:
            this.closeTransientOverlay();
            return;
         case ACCEPT_INVITE:
            NetworkHandler.sendToServer(new AcceptPartyInviteC2S());
            break;
         case ACCEPT_INVITE_DIFFICULTY:
            NetworkHandler.sendToServer(new AcceptPartyInviteC2S(true));
            break;
         case LEAVE_PARTY:
            NetworkHandler.sendToServer(new LeavePartyC2S());
      }

      this.queuePartyRefresh();
      this.closeTransientOverlay();
   }

   private QuestTreeScreen.PanelRect getInvitePopupRect() {
      return new QuestTreeScreen.PanelRect((this.getUiWidth() - 188) / 2, (this.getUiHeight() - 148) / 2, 188, 148);
   }

   private QuestTreeScreen.PanelRect getConfirmRect() {
      return new QuestTreeScreen.PanelRect((this.getUiWidth() - 188) / 2, (this.getUiHeight() - 112) / 2, 188, 112);
   }

   private int getInvitePopupVisibleRows() {
      return 5;
   }

   private void renderInvitePopup(GuiGraphics graphics, int mouseX, int mouseY) {
      graphics.fill(0, 0, this.getUiWidth(), this.getUiHeight(), -1728053248);
      QuestTreeScreen.PanelRect popup = this.getInvitePopupRect();
      this.renderSidePanelBackground(graphics, popup, false, false, true);
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.party.invite_popup", new Object[0]).copy().withStyle(ChatFormatting.BOLD),
         popup.x + popup.width / 2,
         popup.y + 8,
         -10496
      );
      int listX = popup.x + 10;
      int listY = popup.y + 24;
      int listW = popup.width - 20;
      int visibleRows = this.getInvitePopupVisibleRows();
      if (this.inviteEntries.isEmpty()) {
         TextUtil.drawCenteredStringWithBorder(
            graphics,
            this.font,
            this.tr("gui.dragonminez.party.invite_none", new Object[0]),
            popup.x + popup.width / 2,
            popup.y + popup.height / 2 - 6,
            -5592406
         );
      } else {
         for (int i = 0; i < visibleRows && i + this.invitePopupScroll < this.inviteEntries.size(); i++) {
            QuestTreeScreen.PartyInviteEntry entry = this.inviteEntries.get(i + this.invitePopupScroll);
            int rowY = listY + i * 18;
            boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + 16;
            graphics.fill(listX, rowY, listX + listW, rowY + 16, hovered ? 1728053247 : 855638016);
            graphics.renderOutline(listX, rowY, listW, 16, hovered ? -3351041 : 1430537318);
            TextUtil.drawStringWithBorder(
               graphics, this.font, this.txt(this.fitSingleLineEllipsis(entry.playerName(), listW - 10)), listX + 5, rowY + 5, hovered ? -1 : -2300161
            );
         }
      }

      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.tr("gui.dragonminez.party.invite_hint", new Object[0]), popup.x + popup.width / 2, popup.bottom() - 12, -5592406
      );
   }

   private void renderConfirmOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
      graphics.fill(0, 0, this.getUiWidth(), this.getUiHeight(), -1442840576);
      QuestTreeScreen.PanelRect popup = this.getConfirmRect();
      this.renderSidePanelBackground(graphics, popup, false, false, true);
      TextUtil.drawCenteredStringWithBorder(
         graphics, this.font, this.confirmTitle.copy().withStyle(ChatFormatting.BOLD), popup.x + popup.width / 2, popup.y + 10, -10496
      );
      List<String> lines = this.wrapText(this.confirmBody.getString(), popup.width - 20);
      int bodyY = popup.y + 28;

      for (int i = 0; i < Math.min(3, lines.size()); i++) {
         TextUtil.drawCenteredStringWithBorder(graphics, this.font, this.txt(lines.get(i)), popup.x + popup.width / 2, bodyY, -2300161);
         bodyY += 10;
      }

      QuestTreeScreen.PanelRect yesRect = this.getConfirmYesRect();
      QuestTreeScreen.PanelRect noRect = this.getConfirmNoRect();
      this.renderModalButton(graphics, yesRect, this.tr("gui.dragonminez.party.confirm_yes", new Object[0]), yesRect.contains((double)mouseX, (double)mouseY));
      this.renderModalButton(graphics, noRect, this.tr("gui.dragonminez.party.confirm_no", new Object[0]), noRect.contains((double)mouseX, (double)mouseY));
   }

   private void renderModalButton(GuiGraphics graphics, QuestTreeScreen.PanelRect rect, Component label, boolean hovered) {
      int fill = hovered ? 1728053247 : 855638016;
      int outline = hovered ? -3351041 : -2008791962;
      graphics.fill(rect.x, rect.y, rect.right(), rect.bottom(), fill);
      graphics.renderOutline(rect.x, rect.y, rect.width, rect.height, outline);
      TextUtil.drawCenteredStringWithBorder(graphics, this.font, label, rect.x + rect.width / 2, rect.y + 6, -1);
   }

   private boolean shouldShowDifficultySelect() {
      if (this.statsData == null) {
         return false;
      } else {
         PlayerQuestData pqd = this.statsData.getPlayerQuestData();
         return pqd.isDifficultyChosen() ? false : !pqd.isInParty() || this.isLocalPartyLeader();
      }
   }

   private QuestTreeScreen.PanelRect getDifficultySelectRect() {
      int w = 220;
      int h = 196;
      return new QuestTreeScreen.PanelRect((this.getUiWidth() - w) / 2, (this.getUiHeight() - h) / 2, w, h);
   }

   private QuestTreeScreen.PanelRect getDifficultyIntroRect() {
      QuestTreeScreen.PanelRect popup = this.getDifficultySelectRect();
      return new QuestTreeScreen.PanelRect(popup.x + 10, popup.y + 16, popup.width - 20, 24);
   }

   private QuestTreeScreen.PanelRect getDifficultyOptionRect(int index) {
      QuestTreeScreen.PanelRect popup = this.getDifficultySelectRect();
      int optH = 42;
      int x = popup.x + 12;
      int y = popup.y + 46 + index * (optH + 5);
      return new QuestTreeScreen.PanelRect(x, y, popup.width - 24, optH);
   }

   private List<String> difficultyDescriptionLines(Difficulty d, int textWidth) {
      List<String> lines = new ArrayList<>();
      lines.addAll(
         this.wrapText(
            this.tr(
                  "gui.dragonminez.quest_tree.difficulty.tooltip.stats",
                  new Object[]{formatMultiplier(d.hpMultiplier()), formatMultiplier(d.damageMultiplier())}
               )
               .getString(),
            textWidth
         )
      );
      lines.addAll(
         this.wrapText(
            this.tr(
                  "gui.dragonminez.quest_tree.difficulty.tooltip.rewards",
                  new Object[]{formatMultiplier(d.tpMultiplier()), formatMultiplier(d.questRewardMultiplier())}
               )
               .getString(),
            textWidth
         )
      );
      return lines;
   }

   private void renderDifficultySelectOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
      graphics.fill(0, 0, this.getUiWidth(), this.getUiHeight(), -1442840576);
      QuestTreeScreen.PanelRect popup = this.getDifficultySelectRect();
      this.renderSidePanelBackground(graphics, popup, false, false, true);
      TextUtil.drawCenteredStringWithBorder(
         graphics,
         this.font,
         this.tr("gui.dragonminez.quest_tree.difficulty.select.title", new Object[0]).withStyle(ChatFormatting.BOLD),
         popup.x + popup.width / 2,
         popup.y + 4,
         -10496
      );
      QuestTreeScreen.PanelRect intro = this.getDifficultyIntroRect();
      List<String> introLines = this.wrapText(this.tr("gui.dragonminez.quest_tree.difficulty.select.intro", new Object[0]).getString(), intro.width - 6);
      this.diffIntroMaxScroll = this.scrollMax(introLines, intro.height);
      this.diffIntroScroll = Mth.clamp(this.diffIntroScroll, 0.0F, this.diffIntroMaxScroll);
      graphics.enableScissor(
         this.toScreenCoord((double)intro.x),
         this.toScreenCoord((double)intro.y),
         this.toScreenCoord((double)intro.right()),
         this.toScreenCoord((double)intro.bottom())
      );
      TextUtil.renderScrollableText(
         graphics,
         this.font,
         this.diffIntroBar,
         introLines,
         intro.x,
         intro.y,
         intro.width,
         intro.height,
         this.diffIntroScroll,
         this.diffIntroMaxScroll,
         -5592406,
         Style.EMPTY.withFont(DMZ_FONT)
      );
      graphics.disableScissor();

      for (int i = 0; i < DIFFICULTY_OPTIONS.length; i++) {
         Difficulty d = DIFFICULTY_OPTIONS[i];
         QuestTreeScreen.PanelRect rect = this.getDifficultyOptionRect(i);
         boolean hovered = rect.contains((double)mouseX, (double)mouseY);
         graphics.fill(rect.x, rect.y, rect.right(), rect.bottom(), hovered ? 1728053247 : 855638016);
         graphics.renderOutline(rect.x, rect.y, rect.width, rect.height, hovered ? -3351041 : 1430537318);
         TextUtil.drawStringWithBorder(
            graphics, this.font, this.difficultyLabel(d).copy().withStyle(ChatFormatting.BOLD), rect.x + 6, rect.y + 4, this.difficultyColor(d, hovered)
         );
         int textX = rect.x + 6;
         int textY = rect.y + 15;
         int textW = rect.width - 12;
         int textH = rect.bottom() - textY - 2;
         List<String> lines = this.difficultyDescriptionLines(d, textW - 6);
         this.diffOptMaxScroll[i] = this.scrollMax(lines, textH);
         this.diffOptScroll[i] = Mth.clamp(this.diffOptScroll[i], 0.0F, this.diffOptMaxScroll[i]);
         graphics.enableScissor(
            this.toScreenCoord((double)textX),
            this.toScreenCoord((double)textY),
            this.toScreenCoord((double)(textX + textW)),
            this.toScreenCoord((double)(textY + textH))
         );
         TextUtil.renderScrollableText(
            graphics,
            this.font,
            this.diffOptBars[i],
            lines,
            textX,
            textY,
            textW,
            textH,
            this.diffOptScroll[i],
            this.diffOptMaxScroll[i],
            -2300161,
            Style.EMPTY.withFont(DMZ_FONT)
         );
         graphics.disableScissor();
      }
   }

   private float scrollMax(List<String> lines, int viewHeight) {
      return (float)Math.max(0, lines.size() * (9 + 2) - viewHeight);
   }

   private boolean handleDifficultySelectClick(double uiMouseX, double uiMouseY, int button) {
      if (!this.shouldShowDifficultySelect()) {
         return false;
      } else if (button != 0) {
         return true;
      } else {
         for (int i = 0; i < DIFFICULTY_OPTIONS.length; i++) {
            if (this.getDifficultyOptionRect(i).contains(uiMouseX, uiMouseY)) {
               NetworkHandler.sendToServer(new SetStoryDifficultyC2S(DIFFICULTY_OPTIONS[i]));
               Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)MainSounds.PIP_MENU.get(), 1.0F));
               this.pendingRefreshTicks = 5;
               return true;
            }
         }

         return true;
      }
   }

   private QuestTreeScreen.PanelRect getConfirmYesRect() {
      QuestTreeScreen.PanelRect popup = this.getConfirmRect();
      return new QuestTreeScreen.PanelRect(popup.x + 18, popup.bottom() - 30, 66, 20);
   }

   private QuestTreeScreen.PanelRect getConfirmNoRect() {
      QuestTreeScreen.PanelRect popup = this.getConfirmRect();
      return new QuestTreeScreen.PanelRect(popup.right() - 84, popup.bottom() - 30, 66, 20);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode != 256 || !this.invitePopupOpen && !this.confirmOverlayOpen) {
         return super.keyPressed(keyCode, scanCode, modifiers);
      } else {
         this.closeTransientOverlay();
         return true;
      }
   }

   private boolean handleInvitePopupClick(double uiMouseX, double uiMouseY, int button) {
      if (!this.invitePopupOpen) {
         return false;
      } else if (button != 0) {
         return true;
      } else {
         QuestTreeScreen.PanelRect popup = this.getInvitePopupRect();
         if (!popup.contains(uiMouseX, uiMouseY)) {
            this.invitePopupOpen = false;
            return true;
         } else if (this.inviteEntries.isEmpty()) {
            return true;
         } else {
            int listX = popup.x + 10;
            int listY = popup.y + 24;
            int listW = popup.width - 20;
            int visibleRows = this.getInvitePopupVisibleRows();
            if (!(uiMouseX < (double)listX) && !(uiMouseX > (double)(listX + listW))) {
               for (int i = 0; i < visibleRows && i + this.invitePopupScroll < this.inviteEntries.size(); i++) {
                  int rowY = listY + i * 18;
                  if (uiMouseY >= (double)rowY && uiMouseY <= (double)(rowY + 16)) {
                     QuestTreeScreen.PartyInviteEntry entry = this.inviteEntries.get(i + this.invitePopupScroll);
                     NetworkHandler.sendToServer(new InvitePartyMemberC2S(entry.playerId()));
                     this.invitePopupOpen = false;
                     this.queuePartyRefresh();
                     return true;
                  }
               }

               return true;
            } else {
               return true;
            }
         }
      }
   }

   private boolean handleConfirmOverlayClick(double uiMouseX, double uiMouseY, int button) {
      if (!this.confirmOverlayOpen) {
         return false;
      } else if (button != 0) {
         return true;
      } else if (this.getConfirmYesRect().contains(uiMouseX, uiMouseY)) {
         this.executeConfirmAction();
         return true;
      } else if (!this.getConfirmNoRect().contains(uiMouseX, uiMouseY) && this.getConfirmRect().contains(uiMouseX, uiMouseY)) {
         return true;
      } else {
         this.closeTransientOverlay();
         return true;
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      double uiMouseX = this.toUiX(mouseX);
      double uiMouseY = this.toUiY(mouseY);
      if (button == 0 && this.tryStartScrollbarDrag(uiMouseX, uiMouseY)) {
         return true;
      } else if (this.handleDifficultySelectClick(uiMouseX, uiMouseY, button)) {
         return true;
      } else if (this.handleConfirmOverlayClick(uiMouseX, uiMouseY, button)) {
         return true;
      } else if (this.handleInvitePopupClick(uiMouseX, uiMouseY, button)) {
         return true;
      } else if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (button != 0) {
         return false;
      } else if (this.handleNavigatorClick(uiMouseX, uiMouseY)) {
         return true;
      } else if (this.handleEnemyPreviewClick(uiMouseX, uiMouseY)) {
         return true;
      } else if (!this.getLeftPanelRect().contains(uiMouseX, uiMouseY) && !this.getRightPanelRect().contains(uiMouseX, uiMouseY)) {
         QuestTreeScreen.PanelRect tree = this.getTreePanelRect();
         if (!tree.contains(uiMouseX, uiMouseY)) {
            return false;
         } else {
            int zoomedMouseX = (int)(uiMouseX / (double)this.zoom);
            int zoomedMouseY = (int)(uiMouseY / (double)this.zoom);
            if (this.currentLayout != null) {
               for (QuestTreeLayoutHelper.NodePosition node : this.currentLayout.getNodes()) {
                  if (this.isNodeHovered(node, zoomedMouseX, zoomedMouseY)) {
                     if (this.getNodeVisibility(node.getQuest()) == QuestTreeScreen.NodeVisibility.BLURRED) {
                        return true;
                     }

                     this.selectQuest(node.getQuest(), true);
                     this.slideToNode(node);
                     return true;
                  }
               }
            }

            this.isDraggingTree = true;
            this.isAnimatingPan = false;
            this.lastPanAnimNanos = 0L;
            this.dragStartX = uiMouseX;
            this.dragStartY = uiMouseY;
            this.dragStartPanX = this.panX;
            this.dragStartPanY = this.panY;
            this.treePressStarted = true;
            this.treePressMoved = false;
            this.treePressStartX = uiMouseX;
            this.treePressStartY = uiMouseY;
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean handleEnemyPreviewClick(double uiMouseX, double uiMouseY) {
      if (!this.enemyPreview.isActive() || !this.enemyPreview.hasMultipleTargets()) {
         return false;
      } else if (this.leftPanelRevealProgress > 0.4F) {
         return false;
      } else if (!this.enemyPreview.isHovering((int)uiMouseX, (int)uiMouseY)) {
         return false;
      } else {
         if (this.enemyPreview.advanceTarget()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)MainSounds.PIP_MENU.get(), 1.0F));
         }

         return true;
      }
   }

   private boolean handleNavigatorClick(double uiMouseX, double uiMouseY) {
      QuestTreeScreen.PanelRect panel = this.getLeftPanelRect();
      if (!panel.contains(uiMouseX, uiMouseY)) {
         return false;
      } else {
         int listX = panel.x + 10;
         int listY = panel.y + 28;
         int listW = panel.width - 20;
         int listH = Math.max(32, panel.height - 38 - this.getPartyFooterHeight());
         int index = (int)((uiMouseY - (double)listY + (double)this.currentNavScroll) / 13.0);
         if (index >= 0 && index < this.navigatorEntries.size() && !(uiMouseY < (double)listY) && !(uiMouseY > (double)(listY + listH))) {
            QuestTreeScreen.NavigatorEntry entry = this.navigatorEntries.get(index);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)MainSounds.PIP_MENU.get(), 1.0F));
            if (entry.type() != QuestTreeScreen.NavEntryType.SAGA) {
               if (entry.type() == QuestTreeScreen.NavEntryType.SECRET_SECTION) {
                  return true;
               } else {
                  if (entry.quest() != null) {
                     if ((entry.type() == QuestTreeScreen.NavEntryType.MAIN_QUEST || entry.type() == QuestTreeScreen.NavEntryType.SIDE_QUEST)
                        && this.hasReachableSideBranch(entry.saga(), entry.quest())) {
                        this.toggleSideBranch(entry.saga(), entry.quest());
                     }

                     this.selectQuest(entry.quest(), true);
                     QuestTreeLayoutHelper.NodePosition node = this.findNodeForQuest(entry.quest());
                     if (node != null) {
                        this.slideToNode(node);
                     }
                  }

                  return true;
               }
            } else if (entry.isPlaceholderSaga()) {
               return true;
            } else if (!this.isSagaUnlockedByPreviousCompletion(entry.saga())) {
               return true;
            } else {
               int newIndex = this.availableSagas.indexOf(entry.saga());
               if (newIndex >= 0 && newIndex != this.currentSagaIndex) {
                  this.currentSagaIndex = newIndex;
                  this.selectedQuest = null;
                  this.currentObjScroll = 0.0F;
                  this.rebuildLayout();
                  this.rebuildNavigatorEntries();
                  this.persistSelection();
                  this.refreshButtons();
               }

               return true;
            }
         } else {
            return true;
         }
      }
   }

   private boolean tryStartScrollbarDrag(double mx, double my) {
      if (this.navBar.tryStartDrag(mx, my)) {
         this.targetNavScroll = this.navBar.scrollFor(my);
         return true;
      } else if (this.descBar.tryStartDrag(mx, my)) {
         this.targetDescScroll = this.descBar.scrollFor(my);
         return true;
      } else if (this.objBar.tryStartDrag(mx, my)) {
         this.targetObjScroll = this.objBar.scrollFor(my);
         return true;
      } else if (this.rewardsBar.tryStartDrag(mx, my)) {
         this.targetRewardsScroll = this.rewardsBar.scrollFor(my);
         return true;
      } else if (this.diffIntroBar.tryStartDrag(mx, my)) {
         this.diffIntroScroll = this.diffIntroBar.scrollFor(my);
         return true;
      } else {
         for (int i = 0; i < this.diffOptBars.length; i++) {
            if (this.diffOptBars[i].tryStartDrag(mx, my)) {
               this.diffOptScroll[i] = this.diffOptBars[i].scrollFor(my);
               return true;
            }
         }

         return false;
      }
   }

   private boolean updateScrollbarDrag(double my) {
      if (this.navBar.isDragging()) {
         this.targetNavScroll = this.navBar.scrollFor(my);
         return true;
      } else if (this.descBar.isDragging()) {
         this.targetDescScroll = this.descBar.scrollFor(my);
         return true;
      } else if (this.objBar.isDragging()) {
         this.targetObjScroll = this.objBar.scrollFor(my);
         return true;
      } else if (this.rewardsBar.isDragging()) {
         this.targetRewardsScroll = this.rewardsBar.scrollFor(my);
         return true;
      } else if (this.diffIntroBar.isDragging()) {
         this.diffIntroScroll = this.diffIntroBar.scrollFor(my);
         return true;
      } else {
         for (int i = 0; i < this.diffOptBars.length; i++) {
            if (this.diffOptBars[i].isDragging()) {
               this.diffOptScroll[i] = this.diffOptBars[i].scrollFor(my);
               return true;
            }
         }

         return false;
      }
   }

   private boolean stopScrollbarDrag() {
      boolean any = this.navBar.isDragging()
         || this.descBar.isDragging()
         || this.objBar.isDragging()
         || this.rewardsBar.isDragging()
         || this.diffIntroBar.isDragging();

      for (ScrollbarState b : this.diffOptBars) {
         any |= b.isDragging();
      }

      this.navBar.stopDrag();
      this.descBar.stopDrag();
      this.objBar.stopDrag();
      this.rewardsBar.stopDrag();
      this.diffIntroBar.stopDrag();

      for (ScrollbarState b : this.diffOptBars) {
         b.stopDrag();
      }

      return any;
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.updateScrollbarDrag(this.toUiY(mouseY))) {
         return true;
      } else if (this.invitePopupOpen || this.confirmOverlayOpen) {
         return true;
      } else if (this.isDraggingTree && button == 0) {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         double dx = uiMouseX - this.treePressStartX;
         double dy = uiMouseY - this.treePressStartY;
         if (dx * dx + dy * dy > 16.0) {
            this.treePressMoved = true;
         }

         this.panX = this.dragStartPanX + (float)(uiMouseX - this.dragStartX);
         this.panY = this.dragStartPanY + (float)(uiMouseY - this.dragStartY);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.stopScrollbarDrag()) {
         return true;
      } else if (this.invitePopupOpen || this.confirmOverlayOpen) {
         return true;
      } else if (this.isDraggingTree && button == 0) {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         double dx = uiMouseX - this.treePressStartX;
         double dy = uiMouseY - this.treePressStartY;
         boolean moved = this.treePressMoved || dx * dx + dy * dy > 16.0;
         if (this.treePressStarted && !moved && this.selectedQuest != null) {
            this.selectedQuest = null;
            this.currentObjScroll = 0.0F;
            this.objMaxScroll = 0.0F;
            this.persistSelection();
            this.refreshButtons();
         }

         this.isDraggingTree = false;
         this.treePressStarted = false;
         this.treePressMoved = false;
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.shouldShowDifficultySelect()) {
         double dsX = this.toUiX(mouseX);
         double dsY = this.toUiY(mouseY);
         float step = (float)((9 + 2) * 2);
         int dir = (int)Math.signum(scrollY);
         if (this.getDifficultyIntroRect().contains(dsX, dsY)) {
            this.diffIntroScroll = Mth.clamp(this.diffIntroScroll - (float)dir * step, 0.0F, this.diffIntroMaxScroll);
            return true;
         } else {
            for (int i = 0; i < DIFFICULTY_OPTIONS.length; i++) {
               if (this.getDifficultyOptionRect(i).contains(dsX, dsY)) {
                  this.diffOptScroll[i] = Mth.clamp(this.diffOptScroll[i] - (float)dir * step, 0.0F, this.diffOptMaxScroll[i]);
                  return true;
               }
            }

            return true;
         }
      } else if (this.confirmOverlayOpen) {
         return true;
      } else if (this.invitePopupOpen) {
         int direction = (int)Math.signum(scrollY);
         int maxScroll = Math.max(0, this.inviteEntries.size() - this.getInvitePopupVisibleRows());
         this.invitePopupScroll = Math.max(0, Math.min(maxScroll, this.invitePopupScroll - direction));
         return true;
      } else {
         double uiMouseX = this.toUiX(mouseX);
         double uiMouseY = this.toUiY(mouseY);
         int scrollAmount = (int)Math.signum(scrollY);
         QuestTreeScreen.PanelRect left = this.getLeftPanelRect();
         if (left.contains(uiMouseX, uiMouseY)) {
            this.targetNavScroll = Mth.clamp(this.targetNavScroll - (float)(scrollAmount * 26), 0.0F, this.navMaxScroll);
            return true;
         } else {
            QuestTreeScreen.PanelRect rewardsRect = this.getRewardsSectionRect();
            if (rewardsRect != null && rewardsRect.contains(uiMouseX, uiMouseY)) {
               this.targetRewardsScroll = Mth.clamp(
                  this.targetRewardsScroll - (float)(scrollAmount * this.getDetailLineHeight() * 2), 0.0F, this.rewardsMaxScroll
               );
               return true;
            } else {
               QuestTreeScreen.PanelRect objectivesRect = this.getObjectivesSectionRect();
               if (objectivesRect != null && objectivesRect.contains(uiMouseX, uiMouseY)) {
                  this.targetObjScroll = Mth.clamp(this.targetObjScroll - (float)(scrollAmount * this.getDetailLineHeight() * 2), 0.0F, this.objMaxScroll);
                  return true;
               } else {
                  QuestTreeScreen.PanelRect descRect = this.getDescriptionSectionRect();
                  if (descRect != null && descRect.contains(uiMouseX, uiMouseY)) {
                     this.targetDescScroll = Mth.clamp(this.targetDescScroll - (float)(scrollAmount * (9 + 2) * 2), 0.0F, this.descMaxScroll);
                     return true;
                  } else if (this.getRightPanelRect().contains(uiMouseX, uiMouseY)) {
                     return true;
                  } else {
                     QuestTreeScreen.PanelRect tree = this.getTreePanelRect();
                     if (!tree.contains(uiMouseX, uiMouseY)) {
                        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
                     } else {
                        float oldZoom = this.zoom;
                        float zoomDelta = (float)scrollAmount * 0.1F;
                        this.zoom = Math.max(0.25F, Math.min(2.0F, this.zoom + zoomDelta));
                        if (this.zoom != oldZoom) {
                           float scale = this.zoom / oldZoom;
                           this.panX = (float)(uiMouseX - (double)scale * (uiMouseX - (double)this.panX));
                           this.panY = (float)(uiMouseY - (double)scale * (uiMouseY - (double)this.panY));
                        }

                        return true;
                     }
                  }
               }
            }
         }
      }
   }

   private void selectQuest(Quest quest, boolean playClickSound) {
      if (quest != null) {
         this.selectedQuest = quest;
         this.currentObjScroll = 0.0F;
         this.objMaxScroll = 0.0F;
         this.currentRewardsScroll = 0.0F;
         this.targetRewardsScroll = 0.0F;
         this.rewardsMaxScroll = 0.0F;
         this.resetTypewriterForSelectedQuest();
         this.persistSelection();
         this.refreshButtons();
         if (playClickSound) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)MainSounds.PIP_MENU.get(), 1.0F));
         }
      }
   }

   private QuestTreeScreen.PanelRect getObjectivesSectionRect() {
      if (this.selectedQuest != null && this.statsData != null && !this.availableSagas.isEmpty()) {
         QuestTreeScreen.PanelRect panel = this.getRightPanelRect();
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         String questKey = this.questProgressKey(saga, this.selectedQuest);
         int innerX = panel.x + 10;
         int innerY = panel.y + 10;
         int innerW = panel.width - 20;
         int innerH = panel.height - 40;
         QuestTreeScreen.DetailPanelLayout layout = this.computeDetailPanelLayout(innerW, innerH, questKey, saga);
         int rewardsY = innerY + layout.titleH();
         int descY = rewardsY + layout.rewardsH();
         int objectivesY = descY + layout.descH();
         return new QuestTreeScreen.PanelRect(innerX, objectivesY, innerW, layout.objectivesH());
      } else {
         return null;
      }
   }

   private QuestTreeScreen.PanelRect getRewardsSectionRect() {
      if (this.selectedQuest != null && this.statsData != null && !this.availableSagas.isEmpty()) {
         QuestTreeScreen.PanelRect panel = this.getRightPanelRect();
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         String questKey = this.questProgressKey(saga, this.selectedQuest);
         int innerX = panel.x + 10;
         int innerY = panel.y + 10;
         int innerW = panel.width - 20;
         int innerH = panel.height - 40;
         QuestTreeScreen.DetailPanelLayout layout = this.computeDetailPanelLayout(innerW, innerH, questKey, saga);
         return new QuestTreeScreen.PanelRect(innerX, innerY + layout.titleH(), innerW, layout.rewardsH());
      } else {
         return null;
      }
   }

   private QuestTreeScreen.PanelRect getDescriptionSectionRect() {
      if (this.selectedQuest != null && this.statsData != null && !this.availableSagas.isEmpty()) {
         QuestTreeScreen.PanelRect panel = this.getRightPanelRect();
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         String questKey = this.questProgressKey(saga, this.selectedQuest);
         int innerX = panel.x + 10;
         int innerY = panel.y + 10;
         int innerW = panel.width - 20;
         int innerH = panel.height - 40;
         QuestTreeScreen.DetailPanelLayout layout = this.computeDetailPanelLayout(innerW, innerH, questKey, saga);
         int rewardsY = innerY + layout.titleH();
         int descY = rewardsY + layout.rewardsH();
         return new QuestTreeScreen.PanelRect(innerX, descY, innerW, layout.descH());
      } else {
         return null;
      }
   }

   private void resetTypewriterForSelectedQuest() {
      if (this.selectedQuest != null && !this.availableSagas.isEmpty()) {
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         String key = this.questProgressKey(saga, this.selectedQuest);
         this.initializeTypewriterSection(key, "desc");
         this.initializeTypewriterSection(key, "rewards");
      }
   }

   private void initializeTypewriterSection(String questKey, String section) {
      String key = questKey + "#" + section;
      long now = System.currentTimeMillis();
      long lastReveal = this.sectionLastReveal.getOrDefault(key, 0L);
      if (now - lastReveal >= 300000L) {
         this.sectionAnimationStart.put(key, now);
      } else {
         this.sectionAnimationStart.remove(key);
      }
   }

   private QuestTreeLayoutHelper.NodePosition findNodeForQuest(Quest quest) {
      if (quest != null && this.currentLayout != null) {
         for (QuestTreeLayoutHelper.NodePosition node : this.currentLayout.getNodes()) {
            if (this.sameQuestIdentity(node.getQuest(), quest)) {
               return node;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private QuestTreeScreen.NodeVisibility getNodeVisibility(Quest quest) {
      QuestTreeScreen.NodeVisibility cached = this.nodeVisibilityCache.get(quest);
      if (cached != null) {
         return cached;
      } else {
         QuestTreeScreen.NodeVisibility computed = this.computeNodeVisibility(quest);
         this.nodeVisibilityCache.put(quest, computed);
         return computed;
      }
   }

   private QuestTreeScreen.NodeVisibility computeNodeVisibility(Quest quest) {
      if (this.statsData != null && !this.availableSagas.isEmpty()) {
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         PlayerQuestData pqd = this.statsData.getPlayerQuestData();
         if (this.isQuestCompleted(pqd, saga, quest)) {
            return QuestTreeScreen.NodeVisibility.VISIBLE;
         } else {
            String questKey = this.questProgressKey(saga, quest);
            PlayerQuestData.QuestStatus status = pqd.getQuestStatus(questKey);
            if (status == PlayerQuestData.QuestStatus.ACCEPTED || status == PlayerQuestData.QuestStatus.FAILED) {
               return QuestTreeScreen.NodeVisibility.VISIBLE;
            } else if (quest.isSagaQuest()) {
               int questIndex = this.findSagaQuestIndex(saga, quest);
               return questIndex >= 0 && QuestAvailabilityChecker.isSagaQuestAvailable(quest, saga, questIndex, this.statsData)
                  ? QuestTreeScreen.NodeVisibility.VISIBLE
                  : QuestTreeScreen.NodeVisibility.BLURRED;
            } else if (!quest.isSideQuest()) {
               return QuestTreeScreen.NodeVisibility.HIDDEN;
            } else if (!quest.isSecret()) {
               return QuestAvailabilityChecker.isAvailable(quest, this.statsData)
                  ? QuestTreeScreen.NodeVisibility.VISIBLE
                  : QuestTreeScreen.NodeVisibility.BLURRED;
            } else {
               return status != PlayerQuestData.QuestStatus.ACCEPTED && status != PlayerQuestData.QuestStatus.FAILED
                  ? QuestTreeScreen.NodeVisibility.HIDDEN
                  : QuestTreeScreen.NodeVisibility.VISIBLE;
            }
         }
      } else {
         return QuestTreeScreen.NodeVisibility.HIDDEN;
      }
   }

   private boolean isImmediateLockedSagaQuest(Saga saga, Quest quest, PlayerQuestData pqd) {
      int questIndex = this.findSagaQuestIndex(saga, quest);
      return questIndex < 0 ? false : this.findFirstLockedSagaQuestIndex(saga, pqd) == questIndex;
   }

   private int findFirstLockedSagaQuestIndex(Saga saga, PlayerQuestData pqd) {
      List<Quest> sagaQuests = saga.getQuests();

      for (int i = 0; i < sagaQuests.size(); i++) {
         Quest q = sagaQuests.get(i);
         if (!this.isQuestCompleted(pqd, saga, q)) {
            String qKey = this.questProgressKey(saga, q);
            PlayerQuestData.QuestStatus status = pqd.getQuestStatus(qKey);
            if (status != PlayerQuestData.QuestStatus.ACCEPTED
               && status != PlayerQuestData.QuestStatus.FAILED
               && !QuestAvailabilityChecker.isSagaQuestAvailable(q, saga, i, this.statsData)) {
               return i;
            }
         }
      }

      return -1;
   }

   private QuestTreeScreen.QuestNodeStatus getNodeStatus(Quest quest) {
      QuestTreeScreen.QuestNodeStatus cached = this.nodeStatusCache.get(quest);
      if (cached != null) {
         return cached;
      } else {
         QuestTreeScreen.QuestNodeStatus computed = this.computeNodeStatus(quest);
         this.nodeStatusCache.put(quest, computed);
         return computed;
      }
   }

   private QuestTreeScreen.QuestNodeStatus computeNodeStatus(Quest quest) {
      if (this.statsData != null && !this.availableSagas.isEmpty()) {
         Saga saga = this.availableSagas.get(this.currentSagaIndex);
         PlayerQuestData pqd = this.statsData.getPlayerQuestData();
         boolean isCompleted = this.isQuestCompleted(pqd, saga, quest);
         if (isCompleted) {
            for (int i = 0; i < quest.getRewards().size(); i++) {
               if (quest.getRewards().get(i).isUnlockedFor(pqd.getDifficulty()) && !this.isRewardClaimed(pqd, saga, quest, i)) {
                  return QuestTreeScreen.QuestNodeStatus.CLAIMABLE;
               }
            }

            return QuestTreeScreen.QuestNodeStatus.COMPLETED;
         } else {
            String questKey = this.questProgressKey(saga, quest);
            PlayerQuestData.QuestStatus status = pqd.getQuestStatus(questKey);
            if (status == PlayerQuestData.QuestStatus.ACCEPTED) {
               return QuestTreeScreen.QuestNodeStatus.ACTIVE;
            } else if (status == PlayerQuestData.QuestStatus.FAILED) {
               return QuestTreeScreen.QuestNodeStatus.AVAILABLE;
            } else if (quest.isSagaQuest()) {
               int questIndex = this.findSagaQuestIndex(saga, quest);
               return questIndex >= 0 && QuestAvailabilityChecker.isSagaQuestAvailable(quest, saga, questIndex, this.statsData)
                  ? QuestTreeScreen.QuestNodeStatus.AVAILABLE
                  : QuestTreeScreen.QuestNodeStatus.LOCKED;
            } else if (quest.isSideQuest()) {
               return QuestAvailabilityChecker.isAvailable(quest, this.statsData)
                  ? QuestTreeScreen.QuestNodeStatus.AVAILABLE
                  : QuestTreeScreen.QuestNodeStatus.LOCKED;
            } else {
               return QuestTreeScreen.QuestNodeStatus.LOCKED;
            }
         }
      } else {
         return QuestTreeScreen.QuestNodeStatus.LOCKED;
      }
   }

   private Component getStatusText(QuestTreeScreen.QuestNodeStatus status) {
      return switch (status) {
         case LOCKED -> this.tr("gui.dragonminez.quest_tree.status.locked", new Object[0]).withStyle(ChatFormatting.RED);
         case AVAILABLE -> this.tr("gui.dragonminez.quest_tree.status.available", new Object[0]).withStyle(ChatFormatting.GREEN);
         case ACTIVE -> this.tr("gui.dragonminez.quest_tree.status.active", new Object[0]).withStyle(ChatFormatting.AQUA);
         case COMPLETED -> this.tr("gui.dragonminez.quests.status.complete", new Object[0]).withStyle(ChatFormatting.DARK_GREEN);
         case CLAIMABLE -> this.tr("gui.dragonminez.quests.claim_rewards", new Object[0]).withStyle(ChatFormatting.GOLD);
      };
   }

   private int getStatusColor(QuestTreeScreen.QuestNodeStatus status) {
      return switch (status) {
         case LOCKED -> -7829368;
         case AVAILABLE -> -13312;
         case ACTIVE -> -13395457;
         case COMPLETED -> -16711936;
         case CLAIMABLE -> -22016;
      };
   }

   private boolean isNodeHovered(QuestTreeLayoutHelper.NodePosition node, int mouseX, int mouseY) {
      if (this.getNodeVisibility(node.getQuest()) == QuestTreeScreen.NodeVisibility.HIDDEN) {
         return false;
      } else {
         float zPanX = this.panX / this.zoom;
         float zPanY = this.panY / this.zoom;
         int x = (int)((float)node.getPixelX() + zPanX);
         int y = (int)((float)node.getPixelY() + zPanY);
         return mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18;
      }
   }

   private String questProgressKey(Saga saga, Quest quest) {
      return quest.isSideQuest() && quest.getStringId() != null ? quest.getStringId() : PlayerQuestData.sagaQuestKey(saga.getId(), quest.getId());
   }

   private boolean isQuestCompleted(PlayerQuestData pqd, Saga saga, Quest quest) {
      return pqd.isQuestCompleted(this.questProgressKey(saga, quest));
   }

   private boolean isRewardClaimed(PlayerQuestData pqd, Saga saga, Quest quest, int rewardIndex) {
      return pqd.isRewardClaimed(this.questProgressKey(saga, quest), rewardIndex);
   }

   private int findSagaQuestIndex(Saga saga, Quest quest) {
      if (saga != null && quest != null) {
         List<Quest> sagaQuests = saga.getQuests();

         for (int i = 0; i < sagaQuests.size(); i++) {
            if (this.sameQuestIdentity(sagaQuests.get(i), quest)) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private boolean sameQuestIdentity(Quest a, Quest b) {
      if (a == null || b == null) {
         return false;
      } else {
         return !a.isSideQuest() && !b.isSideQuest()
            ? a.getId() == b.getId()
            : a.isSideQuest() == b.isSideQuest() && a.getStringId() != null && a.getStringId().equals(b.getStringId());
      }
   }

   private List<String> wrapText(String text, int maxWidth) {
      return TextUtil.wrap(this.font, text, maxWidth, DMZ_STYLE);
   }

   private String fitSingleLineEllipsis(String text, int maxWidth) {
      if (text == null) {
         return "";
      } else if (TextUtil.width(this.font, text, DMZ_STYLE) <= maxWidth) {
         return text;
      } else {
         String ellipsis = "...";
         int ellipsisWidth = TextUtil.width(this.font, ellipsis, DMZ_STYLE);
         if (ellipsisWidth >= maxWidth) {
            return ellipsis;
         } else {
            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < text.length(); i++) {
               char c = text.charAt(i);
               String candidate = builder.toString() + c;
               if (TextUtil.width(this.font, candidate, DMZ_STYLE) + ellipsisWidth > maxWidth) {
                  break;
               }

               builder.append(c);
            }

            return builder + ellipsis;
         }
      }
   }

   private List<String> limitLinesWithEllipsis(List<String> lines, int maxLines, int maxWidth) {
      if (lines.size() <= maxLines) {
         return lines;
      } else {
         List<String> limited = new ArrayList<>();

         for (int i = 0; i < maxLines - 1; i++) {
            limited.add(lines.get(i));
         }

         StringBuilder last = new StringBuilder(lines.get(maxLines - 1));

         for (int i = maxLines; i < lines.size(); i++) {
            last.append(" ").append(lines.get(i));
         }

         limited.add(this.fitSingleLineEllipsis(last.toString(), maxWidth));
         return limited;
      }
   }

   private int getDetailLineHeight() {
      return Math.max(10, 9 + 1);
   }

   private void drawJustifiedTextBlock(GuiGraphics graphics, List<String> lines, int x, int y, int width, int maxLines, int lineHeight, int color) {
      int count = Math.min(maxLines, lines.size());

      for (int i = 0; i < count; i++) {
         boolean lastLine = i == count - 1;
         this.drawJustifiedLine(graphics, lines.get(i), x, y + i * lineHeight, width, color, lastLine);
      }
   }

   private void drawJustifiedLine(GuiGraphics graphics, String line, int x, int y, int width, int color, boolean isLastLine) {
      String trimmed = line == null ? "" : line.trim();
      if (!trimmed.isEmpty() && !isLastLine) {
         String[] words = trimmed.split(" ");
         if (words.length <= 1) {
            TextUtil.drawStringWithBorder(graphics, this.font, this.txt(trimmed), x, y, color);
         } else {
            int wordsWidth = 0;

            for (String word : words) {
               wordsWidth += TextUtil.width(this.font, word, DMZ_STYLE);
            }

            int spaces = words.length - 1;
            int baseSpace = TextUtil.width(this.font, " ", DMZ_STYLE);
            int totalBase = wordsWidth + spaces * baseSpace;
            if (totalBase < width && totalBase >= (int)((float)width * 0.75F)) {
               int extra = width - totalBase;
               int extraPerSpace = extra / spaces;
               int remainder = extra % spaces;
               int cursorX = x;

               for (int i = 0; i < words.length; i++) {
                  TextUtil.drawStringWithBorder(graphics, this.font, this.txt(words[i]), cursorX, y, color);
                  cursorX += TextUtil.width(this.font, words[i], DMZ_STYLE);
                  if (i < spaces) {
                     cursorX += baseSpace + extraPerSpace + (i < remainder ? 1 : 0);
                  }
               }
            } else {
               TextUtil.drawStringWithBorder(graphics, this.font, this.txt(trimmed), x, y, color);
            }
         }
      } else {
         TextUtil.drawStringWithBorder(graphics, this.font, this.txt(trimmed), x, y, color);
      }
   }

   private QuestTreeScreen.PanelRect getLeftPanelRect() {
      QuestTreeScreen.PanelRect base = this.getBaseLeftPanelRect();
      int xOffset = this.getPanelIntroOffsetX(true, base.width) + this.getLeftPanelRevealOffset(base.width);
      return new QuestTreeScreen.PanelRect(base.x + xOffset, base.y, base.width, base.height);
   }

   private QuestTreeScreen.PanelRect getRightPanelRect() {
      QuestTreeScreen.PanelRect base = this.getBaseRightPanelRect();
      int xOffset = this.getPanelIntroOffsetX(false, base.width) + this.getRightPanelRevealOffset(base.width);
      return new QuestTreeScreen.PanelRect(base.x + xOffset, base.y, base.width, base.height);
   }

   private QuestTreeScreen.PanelRect getBaseLeftPanelRect() {
      int third = this.getUiWidth() / 3;
      int width = Math.max(140, (int)((float)third * 0.9F));
      int height = Math.min(this.getUiHeight(), Math.max(120, (int)((float)this.getUiHeight() * 0.9F)));
      int x = 0;
      int y = Math.max(0, (this.getUiHeight() - height) / 2);
      return new QuestTreeScreen.PanelRect(x, y, width, height);
   }

   private QuestTreeScreen.PanelRect getBaseRightPanelRect() {
      int third = this.getUiWidth() / 3;
      int width = Math.max(140, (int)((float)third * 0.9F));
      int height = Math.min(this.getUiHeight(), Math.max(120, (int)((float)this.getUiHeight() * 0.9F)));
      int x = this.getUiWidth() - width;
      int y = Math.max(0, (this.getUiHeight() - height) / 2);
      return new QuestTreeScreen.PanelRect(x, y, width, height);
   }

   private void startPanelIntroAnimation() {
      this.panelIntroStartMs = System.currentTimeMillis();
      this.panelIntroActive = true;
   }

   private float getPanelIntroProgress() {
      if (!this.panelIntroActive) {
         return 1.0F;
      } else {
         long elapsed = System.currentTimeMillis() - this.panelIntroStartMs;
         if (elapsed >= 700L) {
            this.panelIntroActive = false;
            return 1.0F;
         } else {
            return Math.max(0.0F, Math.min(1.0F, (float)elapsed / 700.0F));
         }
      }
   }

   private int getPanelIntroOffsetX(boolean isLeftPanel, int panelWidth) {
      float t = this.getPanelIntroProgress();
      float eased = this.easeOutBack(t, 1.35F);
      float travel = (1.0F - eased) * (float)(panelWidth + 22);
      int offset = Math.round(travel);
      return isLeftPanel ? -offset : offset;
   }

   private int getLeftPanelRevealOffset(int panelWidth) {
      int hiddenTravel = Math.max(0, panelWidth - 24);
      float eased = this.easeInOutCubic(this.leftPanelRevealProgress);
      return -Math.round((1.0F - eased) * (float)hiddenTravel);
   }

   private int getRightPanelRevealOffset(int panelWidth) {
      float eased = this.easeInOutCubic(this.rightPanelRevealProgress);
      return Math.round((1.0F - eased) * (float)panelWidth);
   }

   private float easeOutBack(float t, float overshoot) {
      float shifted = t - 1.0F;
      float c3 = overshoot + 1.0F;
      return 1.0F + c3 * shifted * shifted * shifted + overshoot * shifted * shifted;
   }

   private void syncActionButtonPosition() {
      if (this.actionButton != null) {
         QuestTreeScreen.PanelRect right = this.getRightPanelRect();
         this.actionButton.setX(right.x + (right.width - 74) / 2);
         this.actionButton.setY(right.bottom() - 28);
         this.actionButton.visible = this.selectedQuest != null && this.rightPanelRevealProgress > 0.15F;
      }
   }

   private void updatePanelInteractionAnimations(int mouseX, int mouseY, float dt) {
      float speed = 10.0F * dt;
      boolean nearLeftEdge = mouseX <= 36;
      boolean overLeftPanel = this.getLeftPanelRect().contains((double)mouseX, (double)mouseY);
      boolean keepLeftOpen = this.invitePopupOpen || this.confirmOverlayOpen;
      float leftTarget = !nearLeftEdge && !overLeftPanel && !keepLeftOpen ? 0.0F : 1.0F;
      this.leftPanelRevealProgress = this.approach01(this.leftPanelRevealProgress, leftTarget, speed);
      float rightTarget = this.selectedQuest != null ? 1.0F : 0.0F;
      this.rightPanelRevealProgress = this.approach01(this.rightPanelRevealProgress, rightTarget, speed);
   }

   private float approach01(float current, float target, float step) {
      if (current < target) {
         return Math.min(target, current + step);
      } else {
         return current > target ? Math.max(target, current - step) : current;
      }
   }

   private float easeInOutCubic(float t) {
      if (t <= 0.0F) {
         return 0.0F;
      } else if (t >= 1.0F) {
         return 1.0F;
      } else {
         return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow((double)(-2.0F * t + 2.0F), 3.0) / 2.0F;
      }
   }

   private QuestTreeScreen.PanelRect getTreePanelRect() {
      return new QuestTreeScreen.PanelRect(0, 0, this.getUiWidth(), this.getUiHeight());
   }

   private void drawObjectiveLineWithSymbolColors(GuiGraphics graphics, String line, int x, int y) {
      int symbolIndex = -1;

      for (int i = 0; i < line.length(); i++) {
         char c = line.charAt(i);
         if (!Character.isWhitespace(c)) {
            symbolIndex = i;
            break;
         }
      }

      if (symbolIndex < 0) {
         TextUtil.drawStringWithBorder(graphics, this.font, this.txt(line), x, y, -3355444);
      } else {
         char symbol = line.charAt(symbolIndex);
         int symbolColor = this.symbolColor(symbol);
         if (symbolColor == -1) {
            TextUtil.drawStringWithBorder(graphics, this.font, this.txt(line), x, y, -3355444);
         } else {
            int symbolEnd = symbolIndex + 1;
            if (symbolEnd < line.length() && line.charAt(symbolEnd) == ' ') {
               symbolEnd++;
            }

            String prefix = line.substring(0, symbolIndex);
            String marker = line.substring(symbolIndex, symbolEnd);
            String rest = line.substring(symbolEnd);
            if (!prefix.isEmpty()) {
               TextUtil.drawStringWithBorder(graphics, this.font, this.txt(prefix), x, y, -3355444);
            }

            int markerX = x + TextUtil.width(this.font, prefix, DMZ_STYLE);
            this.drawPlainBoldStringWithBorder(graphics, marker, markerX, y, symbolColor);
            if (!rest.isEmpty()) {
               TextUtil.drawStringWithBorder(graphics, this.font, this.txt(rest), markerX + this.font.width(marker), y, -3355444);
            }
         }
      }
   }

   private int symbolColor(char symbol) {
      return switch (symbol) {
         case '!', '-' -> -256;
         case '+' -> -11141291;
         case 'X', 'x' -> -43691;
         case '✓' -> -11141291;
         case '✕' -> -43691;
         default -> -1;
      };
   }

   private void drawPlainBoldStringWithBorder(GuiGraphics graphics, String text, int x, int y, int textColor) {
      int borderColor = -16777216;
      Component marker = Component.literal(text).withStyle(ChatFormatting.BOLD);
      graphics.drawString(this.font, marker, x + 1, y, borderColor, false);
      graphics.drawString(this.font, marker, x - 1, y, borderColor, false);
      graphics.drawString(this.font, marker, x, y + 1, borderColor, false);
      graphics.drawString(this.font, marker, x, y - 1, borderColor, false);
      graphics.drawString(this.font, marker, x, y, textColor, false);
   }

   private static record ConnRender(int baseX1, int baseY1, int baseX2, int baseY2, int color) {
   }

   private static record DetailPanelLayout(int titleH, int rewardsH, int descH, int objectivesH) {
   }

   private static enum NavEntryType {
      SAGA,
      MAIN_QUEST,
      SIDE_QUEST,
      SECRET_SECTION,
      SECRET_SIDE_QUEST;
   }

   private static record NavigatorEntry(
      QuestTreeScreen.NavEntryType type, int depth, Saga saga, Quest quest, String sagaId, String sagaLabel, boolean comingSoon
   ) {
      boolean isPlaceholderSaga() {
         return this.type == QuestTreeScreen.NavEntryType.SAGA && this.saga == null && this.sagaId != null;
      }
   }

   private static record NodeRender(
      Quest quest,
      int pixelX,
      int pixelY,
      boolean blurred,
      boolean sidequest,
      QuestTreeScreen.QuestNodeStatus status,
      int bgColor,
      int borderColor,
      Component iconComp,
      int iconColor,
      int iconOffsetX,
      Component bottomLabel,
      int bottomLabelColor,
      int bottomLabelOffsetX
   ) {
   }

   private static enum NodeVisibility {
      VISIBLE,
      BLURRED,
      HIDDEN;
   }

   private static record PanelRect(int x, int y, int width, int height) {
      int right() {
         return this.x + this.width;
      }

      int bottom() {
         return this.y + this.height;
      }

      boolean contains(double px, double py) {
         return px >= (double)this.x && px <= (double)this.right() && py >= (double)this.y && py <= (double)this.bottom();
      }
   }

   private static enum PartyConfirmAction {
      NONE,
      ACCEPT_INVITE,
      ACCEPT_INVITE_DIFFICULTY,
      LEAVE_PARTY;
   }

   private static record PartyInviteEntry(UUID playerId, String playerName) {
   }

   private static enum QuestNodeStatus {
      LOCKED,
      AVAILABLE,
      ACTIVE,
      COMPLETED,
      CLAIMABLE;
   }

   private static record RewardBlock(QuestReward reward, List<String> lines, int height, Component header, boolean locked, int headerColor) {
      boolean isHeader() {
         return this.header != null;
      }
   }

   private static record RewardHitbox(int x, int y, int size, ItemStack stack, Component tooltip) {
      boolean contains(int mx, int my) {
         return mx >= this.x && mx <= this.x + this.size && my >= this.y && my <= this.y + this.size;
      }
   }

   private static record SagaCatalogEntry(String id, String label, boolean comingSoon) {
   }
}
