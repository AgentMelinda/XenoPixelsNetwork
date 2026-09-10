package com.dragonminez.client.gui.quest;

import com.dragonminez.common.quest.Quest;
import com.dragonminez.common.quest.QuestPrerequisites;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.quest.Saga;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Generated;

public class QuestTreeLayoutHelper {
   public static final int NODE_SPACING_X = 55;
   public static final int NODE_SPACING_Y = 45;

   public static QuestTreeLayoutHelper.TreeLayout computeLayout(Saga saga) {
      if (saga != null && saga.getQuests() != null && !saga.getQuests().isEmpty()) {
         List<Quest> sagaQuests = saga.getQuests();
         List<QuestTreeLayoutHelper.NodePosition> nodes = new ArrayList<>();
         List<QuestTreeLayoutHelper.NodeConnection> connections = new ArrayList<>();
         Map<Integer, QuestTreeLayoutHelper.NodePosition> sagaNodeMap = new HashMap<>();
         int maxPixelX = 0;
         int maxPixelY = 0;
         int minPixelY = 0;

         for (int i = 0; i < sagaQuests.size(); i++) {
            Quest quest = sagaQuests.get(i);
            int row = 0;
            int pixelX = i * 55;
            int pixelY = row * 45;
            QuestTreeLayoutHelper.NodePosition node = new QuestTreeLayoutHelper.NodePosition(quest, i, row, pixelX, pixelY, false);
            nodes.add(node);
            sagaNodeMap.put(quest.getId(), node);
            maxPixelX = Math.max(maxPixelX, pixelX);
            maxPixelY = Math.max(maxPixelY, pixelY);
            minPixelY = Math.min(minPixelY, pixelY);
         }

         for (int i = 0; i < sagaQuests.size(); i++) {
            Quest child = sagaQuests.get(i);
            QuestTreeLayoutHelper.NodePosition to = sagaNodeMap.get(child.getId());
            if (to != null) {
               QuestTreeLayoutHelper.NodePosition from = findSagaParentNode(child, saga.getId(), sagaNodeMap, sagaQuests, i);
               if (from != null) {
                  connections.add(new QuestTreeLayoutHelper.NodeConnection(from, to));
               }
            }
         }

         Map<String, Quest> allQuests = QuestRegistry.getClientQuests();
         if (!allQuests.isEmpty()) {
            Map<String, QuestTreeLayoutHelper.NodePosition> sideNodeMap = new HashMap<>();
            Map<String, Integer> sideDirectionByQuestKey = new HashMap<>();
            Map<String, Integer> nextAnchorDirection = new HashMap<>();
            Set<String> occupied = new HashSet<>();

            for (QuestTreeLayoutHelper.NodePosition n : nodes) {
               occupied.add(n.getGridCol() + ":" + n.getGridRow());
            }

            List<Quest> sortedSide = new ArrayList<>();

            for (Quest q : allQuests.values()) {
               if (q.isSideQuest() && !q.isSecret() && belongsToSaga(q, saga.getId())) {
                  sortedSide.add(q);
               }
            }

            sortedSide.sort(Comparator.comparing(qx -> qx.getStringId() != null ? qx.getStringId() : ""));

            for (Quest sq : sortedSide) {
               QuestTreeLayoutHelper.NodePosition parent = findParentNode(sq, saga.getId(), sagaNodeMap, sideNodeMap);
               int attachCol = findAttachColumn(sq, saga.getId(), sagaNodeMap, sideNodeMap);
               if (attachCol < 0) {
                  attachCol = 0;
               }

               String anchorKey = parent != null ? questKey(parent.getQuest()) : "saga:" + attachCol;
               int direction;
               if (parent != null && parent.isSidequest()) {
                  direction = sideDirectionByQuestKey.getOrDefault(anchorKey, 1);
               } else {
                  direction = nextAnchorDirection.getOrDefault(anchorKey, 1);
                  nextAnchorDirection.put(anchorKey, -direction);
               }

               int col;
               int row;
               if (parent != null) {
                  col = parent.getGridCol() + 1;
                  row = parent.getGridRow() + direction;
               } else {
                  QuestTreeLayoutHelper.NodePosition sagaAnchor = findSagaNodeByColumn(sagaNodeMap, attachCol);
                  int baseRow = sagaAnchor != null ? sagaAnchor.getGridRow() : 0;
                  col = attachCol + 1;
                  row = baseRow + direction;
               }

               row = reserveClosestFreeRow(occupied, col, row, direction);
               int pixelX = col * 55;
               int pixelY = row * 45;
               QuestTreeLayoutHelper.NodePosition sideNode = new QuestTreeLayoutHelper.NodePosition(sq, col, row, pixelX, pixelY, true);
               nodes.add(sideNode);
               occupied.add(col + ":" + row);
               String key = questKey(sq);
               if (key != null) {
                  sideNodeMap.put(key, sideNode);
                  sideDirectionByQuestKey.put(key, direction);
               }

               maxPixelX = Math.max(maxPixelX, pixelX);
               maxPixelY = Math.max(maxPixelY, pixelY);
               minPixelY = Math.min(minPixelY, pixelY);
               if (parent != null) {
                  connections.add(new QuestTreeLayoutHelper.NodeConnection(parent, sideNode));
               }
            }

            List<Quest> secretSide = new ArrayList<>();

            for (Quest qx : allQuests.values()) {
               if (qx.isSideQuest() && qx.isSecret() && belongsToSaga(qx, saga.getId())) {
                  secretSide.add(qx);
               }
            }

            secretSide.sort(Comparator.comparing(qxx -> qxx.getStringId() != null ? qxx.getStringId() : ""));
            if (!secretSide.isEmpty()) {
               int lowestRow = 0;

               for (QuestTreeLayoutHelper.NodePosition n : nodes) {
                  lowestRow = Math.max(lowestRow, n.getGridRow());
               }

               int secretRow = lowestRow + 2;
               int secretCol = 0;

               for (Quest sq : secretSide) {
                  int rowx = reserveClosestFreeRow(occupied, secretCol, secretRow, 1);
                  int pixelXx = secretCol * 55;
                  int pixelYx = rowx * 45;
                  QuestTreeLayoutHelper.NodePosition secretNode = new QuestTreeLayoutHelper.NodePosition(sq, secretCol, rowx, pixelXx, pixelYx, true);
                  nodes.add(secretNode);
                  occupied.add(secretCol + ":" + rowx);
                  maxPixelX = Math.max(maxPixelX, pixelXx);
                  maxPixelY = Math.max(maxPixelY, pixelYx);
                  secretCol++;
               }
            }
         }

         int totalWidth = maxPixelX + 55;
         int totalHeight = maxPixelY - minPixelY + 45;
         if (minPixelY >= 0) {
            return new QuestTreeLayoutHelper.TreeLayout(nodes, connections, totalWidth, totalHeight);
         } else {
            int shift = -minPixelY;
            List<QuestTreeLayoutHelper.NodePosition> shiftedNodes = new ArrayList<>(nodes.size());

            for (QuestTreeLayoutHelper.NodePosition node : nodes) {
               shiftedNodes.add(
                  new QuestTreeLayoutHelper.NodePosition(
                     node.getQuest(), node.getGridCol(), node.getGridRow(), node.getPixelX(), node.getPixelY() + shift, node.isSidequest()
                  )
               );
            }

            List<QuestTreeLayoutHelper.NodeConnection> shiftedConnections = new ArrayList<>(connections.size());

            for (QuestTreeLayoutHelper.NodeConnection connection : connections) {
               QuestTreeLayoutHelper.NodePosition shiftedFrom = findShiftedNode(shiftedNodes, connection.getFrom());
               QuestTreeLayoutHelper.NodePosition shiftedTo = findShiftedNode(shiftedNodes, connection.getTo());
               if (shiftedFrom != null && shiftedTo != null) {
                  shiftedConnections.add(new QuestTreeLayoutHelper.NodeConnection(shiftedFrom, shiftedTo));
               }
            }

            return new QuestTreeLayoutHelper.TreeLayout(shiftedNodes, shiftedConnections, totalWidth, totalHeight);
         }
      } else {
         return new QuestTreeLayoutHelper.TreeLayout(Collections.emptyList(), Collections.emptyList(), 0, 0);
      }
   }

   private static QuestTreeLayoutHelper.NodePosition findShiftedNode(
      List<QuestTreeLayoutHelper.NodePosition> nodes, QuestTreeLayoutHelper.NodePosition original
   ) {
      for (QuestTreeLayoutHelper.NodePosition node : nodes) {
         if (node.getQuest() == original.getQuest()) {
            return node;
         }
      }

      return null;
   }

   private static QuestTreeLayoutHelper.NodePosition findSagaParentNode(
      Quest child, String sagaId, Map<Integer, QuestTreeLayoutHelper.NodePosition> sagaNodeMap, List<Quest> sagaQuests, int childIndex
   ) {
      if (child.getPrerequisites() != null && child.getPrerequisites().conditions() != null) {
         for (QuestPrerequisites.Condition cond : child.getPrerequisites().conditions()) {
            if (cond.getType() == QuestPrerequisites.ConditionType.SAGA_QUEST && sagaId.equals(cond.getSagaId()) && cond.getQuestId() != null) {
               QuestTreeLayoutHelper.NodePosition parent = sagaNodeMap.get(cond.getQuestId());
               if (parent != null) {
                  return parent;
               }
            }
         }
      }

      if (childIndex > 0) {
         Quest previous = sagaQuests.get(childIndex - 1);
         return sagaNodeMap.get(previous.getId());
      } else {
         return null;
      }
   }

   private static int findAttachColumn(
      Quest sidequest, String sagaId, Map<Integer, QuestTreeLayoutHelper.NodePosition> sagaNodeMap, Map<String, QuestTreeLayoutHelper.NodePosition> sideNodeMap
   ) {
      if (sidequest.getPrerequisites() == null) {
         return -1;
      } else {
         List<QuestPrerequisites.Condition> conditions = sidequest.getPrerequisites().conditions();
         if (conditions == null) {
            return -1;
         } else {
            for (QuestPrerequisites.Condition cond : conditions) {
               if (cond.getType() == QuestPrerequisites.ConditionType.SAGA_QUEST && sagaId.equals(cond.getSagaId())) {
                  Integer questId = cond.getQuestId();
                  if (questId != null) {
                     QuestTreeLayoutHelper.NodePosition sagaNode = sagaNodeMap.get(questId);
                     if (sagaNode != null) {
                        return sagaNode.getGridCol();
                     }
                  }
               }

               if (cond.getType() == QuestPrerequisites.ConditionType.QUEST) {
                  String refQuestId = cond.getRequiredQuestId();
                  if (refQuestId != null) {
                     QuestTreeLayoutHelper.NodePosition parentSide = sideNodeMap.get(refQuestId);
                     if (parentSide != null) {
                        return parentSide.getGridCol();
                     }
                  }
               }
            }

            return -1;
         }
      }
   }

   private static QuestTreeLayoutHelper.NodePosition findParentNode(
      Quest sidequest, String sagaId, Map<Integer, QuestTreeLayoutHelper.NodePosition> sagaNodeMap, Map<String, QuestTreeLayoutHelper.NodePosition> sideNodeMap
   ) {
      if (sidequest.getPrerequisites() == null) {
         return null;
      } else {
         List<QuestPrerequisites.Condition> conditions = sidequest.getPrerequisites().conditions();
         if (conditions == null) {
            return null;
         } else {
            for (QuestPrerequisites.Condition cond : conditions) {
               if (cond.getType() == QuestPrerequisites.ConditionType.QUEST) {
                  String refQuestId = cond.getRequiredQuestId();
                  if (refQuestId != null) {
                     QuestTreeLayoutHelper.NodePosition parentSide = sideNodeMap.get(refQuestId);
                     if (parentSide != null) {
                        return parentSide;
                     }
                  }
               }
            }

            for (QuestPrerequisites.Condition condx : conditions) {
               if (condx.getType() == QuestPrerequisites.ConditionType.SAGA_QUEST && sagaId.equals(condx.getSagaId())) {
                  Integer questId = condx.getQuestId();
                  if (questId != null) {
                     return sagaNodeMap.get(questId);
                  }
               }
            }

            return null;
         }
      }
   }

   public static boolean belongsToSaga(Quest sidequest, String sagaId) {
      Set<String> visited = new HashSet<>();
      Deque<Quest> queue = new ArrayDeque<>();
      queue.add(sidequest);

      while (!queue.isEmpty()) {
         Quest current = queue.poll();
         if (current.getPrerequisites() != null) {
            List<QuestPrerequisites.Condition> conditions = current.getPrerequisites().conditions();
            if (conditions != null) {
               for (QuestPrerequisites.Condition cond : conditions) {
                  if (cond.getType() == QuestPrerequisites.ConditionType.SAGA_QUEST && sagaId.equals(cond.getSagaId())) {
                     return true;
                  }

                  if (cond.getType() == QuestPrerequisites.ConditionType.QUEST) {
                     String refId = cond.getRequiredQuestId();
                     if (refId != null && visited.add(refId)) {
                        Quest parent = QuestRegistry.getClientQuest(refId);
                        if (parent != null && parent.isSideQuest()) {
                           queue.add(parent);
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private static QuestTreeLayoutHelper.NodePosition findSagaNodeByColumn(Map<Integer, QuestTreeLayoutHelper.NodePosition> sagaNodeMap, int column) {
      for (QuestTreeLayoutHelper.NodePosition node : sagaNodeMap.values()) {
         if (node.getGridCol() == column) {
            return node;
         }
      }

      return null;
   }

   private static int reserveClosestFreeRow(Set<String> occupied, int col, int startRow, int direction) {
      int row = startRow;

      while (occupied.contains(col + ":" + row)) {
         row += direction;
      }

      return row;
   }

   private static String questKey(Quest quest) {
      if (quest == null) {
         return null;
      } else if (quest.getStringId() != null) {
         return quest.getStringId();
      } else {
         return quest.getId() >= 0 ? String.valueOf(quest.getId()) : null;
      }
   }

   public static class NodeConnection {
      private final QuestTreeLayoutHelper.NodePosition from;
      private final QuestTreeLayoutHelper.NodePosition to;

      public NodeConnection(QuestTreeLayoutHelper.NodePosition from, QuestTreeLayoutHelper.NodePosition to) {
         this.from = from;
         this.to = to;
      }

      @Generated
      public QuestTreeLayoutHelper.NodePosition getFrom() {
         return this.from;
      }

      @Generated
      public QuestTreeLayoutHelper.NodePosition getTo() {
         return this.to;
      }
   }

   public static class NodePosition {
      private final Quest quest;
      private final int gridCol;
      private final int gridRow;
      private final int pixelX;
      private final int pixelY;
      private final boolean sidequest;

      public NodePosition(Quest quest, int gridCol, int gridRow, int pixelX, int pixelY, boolean sidequest) {
         this.quest = quest;
         this.gridCol = gridCol;
         this.gridRow = gridRow;
         this.pixelX = pixelX;
         this.pixelY = pixelY;
         this.sidequest = sidequest;
      }

      @Generated
      public Quest getQuest() {
         return this.quest;
      }

      @Generated
      public int getGridCol() {
         return this.gridCol;
      }

      @Generated
      public int getGridRow() {
         return this.gridRow;
      }

      @Generated
      public int getPixelX() {
         return this.pixelX;
      }

      @Generated
      public int getPixelY() {
         return this.pixelY;
      }

      @Generated
      public boolean isSidequest() {
         return this.sidequest;
      }
   }

   public static class TreeLayout {
      private final List<QuestTreeLayoutHelper.NodePosition> nodes;
      private final List<QuestTreeLayoutHelper.NodeConnection> connections;
      private final int totalWidth;
      private final int totalHeight;

      public TreeLayout(List<QuestTreeLayoutHelper.NodePosition> nodes, List<QuestTreeLayoutHelper.NodeConnection> connections, int totalWidth, int totalHeight) {
         this.nodes = nodes;
         this.connections = connections;
         this.totalWidth = totalWidth;
         this.totalHeight = totalHeight;
      }

      @Generated
      public List<QuestTreeLayoutHelper.NodePosition> getNodes() {
         return this.nodes;
      }

      @Generated
      public List<QuestTreeLayoutHelper.NodeConnection> getConnections() {
         return this.connections;
      }

      @Generated
      public int getTotalWidth() {
         return this.totalWidth;
      }

      @Generated
      public int getTotalHeight() {
         return this.totalHeight;
      }
   }
}
