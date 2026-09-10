package com.dragonminez.common.hair;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3f;

public class CustomHair {
   private static final int VERSION = 5;
   public static final int FRONT_STRANDS = 4;
   public static final int SIDE_STRANDS = 16;
   private static final Map<CustomHair.HairFace, Vector3f[]> BASE_POSITIONS = new EnumMap<>(CustomHair.HairFace.class);
   private final Map<CustomHair.HairFace, HairStrand[]> strandsByFace = new EnumMap<>(CustomHair.HairFace.class);
   private String globalColor = "#000000";
   private String name = "Custom";
   private int version = 5;

   public CustomHair() {
      this.initializeStrands();
   }

   private void initializeStrands() {
      int idCounter = 0;

      for (CustomHair.HairFace face : CustomHair.HairFace.values()) {
         HairStrand[] strands = new HairStrand[face.maxStrands];

         for (int i = 0; i < face.maxStrands; i++) {
            int staticId = face.ordinal() * 100 + i;
            strands[i] = new HairStrand(staticId);
            Vector3f rot = getBaseRotation(face);
            strands[i].setRotation(rot.x, rot.y, rot.z);
         }

         this.strandsByFace.put(face, strands);
      }
   }

   public static Vector3f getStrandBasePosition(CustomHair.HairFace face, int index) {
      Vector3f[] cached = BASE_POSITIONS.get(face);
      return cached != null && index >= 0 && index < cached.length ? cached[index] : new Vector3f(0.0F, 0.0F, 0.0F);
   }

   private static Vector3f computeStrandBasePosition(CustomHair.HairFace face, int index) {
      int row = index / face.cols;
      int col = index % face.cols;
      float[] positions = new float[]{-3.0F, -1.0F, 1.0F, 3.0F};
      float[] yOffsets = new float[]{0.0F, -1.5F, -3.0F, -4.5F};
      float gridX = positions[col % 4];
      float gridZ = positions[row % 4];
      float rowYOffset = yOffsets[row % 4];
      switch (face) {
         case FRONT:
            return new Vector3f(gridX, 7.25F, -4.0F);
         case BACK:
            return new Vector3f(gridX, 7.25F + rowYOffset, 4.0F);
         case LEFT:
            return new Vector3f(-3.95F, 7.25F + rowYOffset, gridX);
         case RIGHT:
            return new Vector3f(3.95F, 7.25F + rowYOffset, -gridX);
         case TOP:
            return new Vector3f(gridX, 7.85F, gridZ);
         default:
            return new Vector3f(0.0F, 0.0F, 0.0F);
      }
   }

   public static Vector3f getBaseRotation(CustomHair.HairFace face) {
      switch (face) {
         case FRONT:
            return new Vector3f(-90.0F, 0.0F, 0.0F);
         case BACK:
            return new Vector3f(90.0F, 0.0F, 0.0F);
         case LEFT:
            return new Vector3f(0.0F, 0.0F, 90.0F);
         case RIGHT:
            return new Vector3f(0.0F, 0.0F, -90.0F);
         case TOP:
            return new Vector3f(0.0F, 0.0F, 0.0F);
         default:
            return new Vector3f(0.0F, 0.0F, 0.0F);
      }
   }

   public HairStrand[] getStrands(CustomHair.HairFace face) {
      return this.strandsByFace.get(face);
   }

   public HairStrand getStrand(CustomHair.HairFace face, int index) {
      HairStrand[] strands = this.strandsByFace.get(face);
      return strands != null && index >= 0 && index < strands.length ? strands[index] : null;
   }

   public int getVisibleStrandCount() {
      int count = 0;

      for (HairStrand[] strands : this.strandsByFace.values()) {
         for (HairStrand strand : strands) {
            if (strand.isVisible()) {
               count++;
            }
         }
      }

      return count;
   }

   public int getTotalCubeCount() {
      int count = 0;

      for (HairStrand[] strands : this.strandsByFace.values()) {
         for (HairStrand strand : strands) {
            count += strand.getLength();
         }
      }

      return count;
   }

   public boolean isEmpty() {
      return this.getVisibleStrandCount() == 0;
   }

   public String getGlobalColor() {
      return this.globalColor;
   }

   public void setGlobalColor(String color) {
      this.globalColor = color;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public void clear() {
      for (CustomHair.HairFace face : CustomHair.HairFace.values()) {
         HairStrand[] strands = this.strandsByFace.get(face);

         for (int i = 0; i < strands.length; i++) {
            int staticId = face.ordinal() * 100 + i;
            strands[i] = new HairStrand(staticId);
            Vector3f rot = getBaseRotation(face);
            strands[i].setRotation(rot.x, rot.y, rot.z);
         }
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("v", 5);
      if (this.name != null && !this.name.isEmpty()) {
         tag.putString("n", this.name);
      }

      tag.putString("gc", this.globalColor);
      String[] faceKeys = new String[]{"F", "B", "L", "R", "T"};
      int faceIndex = 0;

      for (CustomHair.HairFace face : CustomHair.HairFace.values()) {
         ListTag strandsList = new ListTag();

         for (HairStrand strand : this.strandsByFace.get(face)) {
            if (strand.isVisible()) {
               strandsList.add(strand.save());
            }
         }

         if (!strandsList.isEmpty()) {
            tag.put(faceKeys[faceIndex], strandsList);
         }

         faceIndex++;
      }

      return tag;
   }

   public void load(CompoundTag tag) {
      int loadedVersion = tag.contains("v") ? tag.getInt("v") : (tag.contains("Version") ? tag.getInt("Version") : 1);
      this.version = loadedVersion;
      this.name = tag.contains("n") ? tag.getString("n") : tag.getString("Name");
      this.globalColor = tag.contains("gc") ? tag.getString("gc") : tag.getString("GlobalColor");
      if (this.globalColor == null || this.globalColor.isEmpty()) {
         this.globalColor = "#000000";
      }

      String[] shortKeys = new String[]{"F", "B", "L", "R", "T"};
      CustomHair.HairFace[] faces = CustomHair.HairFace.values();

      for (int f = 0; f < faces.length; f++) {
         CustomHair.HairFace face = faces[f];
         String shortKey = shortKeys[f];
         String longKey = face.name();
         String keyToUse = tag.contains(shortKey) ? shortKey : (tag.contains(longKey) ? longKey : null);
         if (keyToUse != null) {
            ListTag strandsList = tag.getList(keyToUse, 10);
            HairStrand[] strands = this.strandsByFace.get(face);

            for (int i = 0; i < strandsList.size(); i++) {
               CompoundTag strandTag = strandsList.getCompound(i);
               int idInTag = strandTag.contains("i") ? strandTag.getInt("i") : strandTag.getInt("Id");
               int targetIndex = i;
               if (loadedVersion >= 2) {
                  int calculatedIndex = idInTag - face.ordinal() * 100;
                  if (calculatedIndex >= 0 && calculatedIndex < strands.length) {
                     targetIndex = calculatedIndex;
                  }
               }

               if (targetIndex < strands.length) {
                  strands[targetIndex].load(strandTag);
                  int staticId = face.ordinal() * 100 + targetIndex;
                  strands[targetIndex].setId(staticId);
               }
            }
         }
      }

      this.version = 5;
   }

   public CustomHair copy() {
      CustomHair copy = new CustomHair();
      copy.version = this.version;
      copy.name = this.name;
      copy.globalColor = this.globalColor;

      for (CustomHair.HairFace face : CustomHair.HairFace.values()) {
         HairStrand[] sourceStrands = this.strandsByFace.get(face);
         HairStrand[] destStrands = copy.strandsByFace.get(face);

         for (int i = 0; i < sourceStrands.length; i++) {
            destStrands[i] = sourceStrands[i].copy();
         }
      }

      return copy;
   }

   public void writeToBuffer(FriendlyByteBuf buf) {
      buf.writeNbt(this.save());
   }

   public static CustomHair readFromBuffer(FriendlyByteBuf buf) {
      CompoundTag tag = buf.readNbt();
      if (tag == null) {
         return new CustomHair();
      } else {
         CustomHair hair = new CustomHair();
         hair.load(tag);
         return hair;
      }
   }

   static {
      for (CustomHair.HairFace face : CustomHair.HairFace.values()) {
         Vector3f[] positions = new Vector3f[face.maxStrands];

         for (int i = 0; i < face.maxStrands; i++) {
            positions[i] = computeStrandBasePosition(face, i);
         }

         BASE_POSITIONS.put(face, positions);
      }
   }

   public static enum HairFace {
      FRONT(4, 1, 4),
      BACK(16, 4, 4),
      LEFT(16, 4, 4),
      RIGHT(16, 4, 4),
      TOP(16, 4, 4);

      public final int maxStrands;
      public final int rows;
      public final int cols;

      private HairFace(int maxStrands, int rows, int cols) {
         this.maxStrands = maxStrands;
         this.rows = rows;
         this.cols = cols;
      }
   }
}
