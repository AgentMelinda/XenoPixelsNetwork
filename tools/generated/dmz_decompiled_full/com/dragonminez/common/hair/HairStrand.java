package com.dragonminez.common.hair;

import com.dragonminez.client.util.ColorUtils;
import net.minecraft.nbt.CompoundTag;

public class HairStrand {
   public static final int MAX_CUBE_COUNT = 8;
   public static final int MAX_LENGTH = 50;
   private int length = 0;
   private float lengthScale = 1.0F;
   private float rotationX = 0.0F;
   private float rotationY = 0.0F;
   private float rotationZ = 0.0F;
   private float scaleX = 1.0F;
   private float scaleY = 1.0F;
   private float scaleZ = 1.0F;
   private float cubeWidth = 2.0F;
   private float cubeHeight = 2.0F;
   private float cubeDepth = 2.0F;
   private String color = null;
   private transient float[] rgbColor;
   private float curveX = 0.0F;
   private float curveY = 0.0F;
   private float curveZ = 0.0F;
   private int id = 0;

   public HairStrand() {
   }

   public HairStrand(int id) {
      this.id = id;
   }

   public float getLengthScale() {
      return this.lengthScale;
   }

   public void setLengthScale(float scale) {
      this.lengthScale = scale;
   }

   public int getLength() {
      return this.length;
   }

   public void setLength(int length) {
      this.length = Math.max(0, Math.min(50, length));
   }

   public void addCube() {
      if (this.length < 50) {
         this.length++;
      }
   }

   public void removeCube() {
      if (this.length > 0) {
         this.length--;
      }
   }

   public boolean isVisible() {
      return this.length > 0;
   }

   public int getCubeCount() {
      return this.length;
   }

   public float getStretchFactor() {
      return this.lengthScale;
   }

   public float getRotationX() {
      return this.rotationX;
   }

   public float getRotationY() {
      return this.rotationY;
   }

   public float getRotationZ() {
      return this.rotationZ;
   }

   public void setRotation(float x, float y, float z) {
      this.rotationX = x;
      this.rotationY = y;
      this.rotationZ = z;
   }

   public float getScaleX() {
      return this.scaleX;
   }

   public float getScaleY() {
      return this.scaleY;
   }

   public float getScaleZ() {
      return this.scaleZ;
   }

   public void setScale(float x, float y, float z) {
      this.scaleX = Math.max(0.1F, x);
      this.scaleY = Math.max(0.1F, y);
      this.scaleZ = Math.max(0.1F, z);
   }

   public float getCubeWidth() {
      return this.cubeWidth;
   }

   public float getCubeHeight() {
      return this.cubeHeight;
   }

   public float getCubeDepth() {
      return this.cubeDepth;
   }

   public float getCurveX() {
      return this.curveX;
   }

   public float getCurveY() {
      return this.curveY;
   }

   public float getCurveZ() {
      return this.curveZ;
   }

   public void setCurve(float x, float y, float z) {
      this.curveX = x;
      this.curveY = y;
      this.curveZ = z;
   }

   public String getColor() {
      return this.color;
   }

   public boolean hasCustomColor() {
      return this.color != null && !this.color.isEmpty();
   }

   public float[] getRgbColor() {
      if (this.rgbColor == null) {
         this.rgbColor = ColorUtils.hexToRgb(this.hasCustomColor() ? this.color : "#FFFFFF");
      }

      return this.rgbColor;
   }

   public void setColor(String color) {
      this.color = color;
      this.rgbColor = this.hasCustomColor() ? ColorUtils.hexToRgb(color) : null;
   }

   public int getId() {
      return this.id;
   }

   protected void setId(int id) {
      this.id = id;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      if (this.id != 0) {
         tag.putInt("i", this.id);
      }

      if (this.length != 0) {
         tag.putInt("l", this.length);
      }

      if (this.lengthScale != 1.0F) {
         tag.putFloat("ls", this.lengthScale);
      }

      if (this.rotationX != 0.0F) {
         tag.putFloat("rx", this.rotationX);
      }

      if (this.rotationY != 0.0F) {
         tag.putFloat("ry", this.rotationY);
      }

      if (this.rotationZ != 0.0F) {
         tag.putFloat("rz", this.rotationZ);
      }

      if (this.scaleX != 1.0F) {
         tag.putFloat("sx", this.scaleX);
      }

      if (this.scaleY != 1.0F) {
         tag.putFloat("sy", this.scaleY);
      }

      if (this.scaleZ != 1.0F) {
         tag.putFloat("sz", this.scaleZ);
      }

      if (this.cubeWidth != 2.0F) {
         tag.putFloat("cw", this.cubeWidth);
      }

      if (this.cubeHeight != 2.0F) {
         tag.putFloat("ch", this.cubeHeight);
      }

      if (this.cubeDepth != 2.0F) {
         tag.putFloat("cd", this.cubeDepth);
      }

      if (this.curveX != 0.0F) {
         tag.putFloat("cx", this.curveX);
      }

      if (this.curveY != 0.0F) {
         tag.putFloat("cy", this.curveY);
      }

      if (this.curveZ != 0.0F) {
         tag.putFloat("cz", this.curveZ);
      }

      if (this.color != null) {
         tag.putString("c", this.color);
      }

      return tag;
   }

   public void load(CompoundTag tag) {
      this.id = tag.contains("i") ? tag.getInt("i") : tag.getInt("Id");
      this.length = tag.contains("l") ? tag.getInt("l") : tag.getInt("Length");
      this.lengthScale = tag.contains("ls") ? tag.getFloat("ls") : (tag.contains("LengthScale") ? tag.getFloat("LengthScale") : 1.0F);
      this.rotationX = tag.contains("rx") ? tag.getFloat("rx") : tag.getFloat("RotX");
      this.rotationY = tag.contains("ry") ? tag.getFloat("ry") : tag.getFloat("RotY");
      this.rotationZ = tag.contains("rz") ? tag.getFloat("rz") : tag.getFloat("RotZ");
      this.scaleX = tag.contains("sx") ? tag.getFloat("sx") : (tag.contains("ScaleX") ? tag.getFloat("ScaleX") : 1.0F);
      this.scaleY = tag.contains("sy") ? tag.getFloat("sy") : (tag.contains("ScaleY") ? tag.getFloat("ScaleY") : 1.0F);
      this.scaleZ = tag.contains("sz") ? tag.getFloat("sz") : (tag.contains("ScaleZ") ? tag.getFloat("ScaleZ") : 1.0F);
      this.cubeWidth = tag.contains("cw") ? tag.getFloat("cw") : (tag.contains("CubeW") ? tag.getFloat("CubeW") : 2.0F);
      this.cubeHeight = tag.contains("ch") ? tag.getFloat("ch") : (tag.contains("CubeH") ? tag.getFloat("CubeH") : 2.0F);
      this.cubeDepth = tag.contains("cd") ? tag.getFloat("cd") : (tag.contains("CubeD") ? tag.getFloat("CubeD") : 2.0F);
      this.curveX = tag.contains("cx") ? tag.getFloat("cx") : tag.getFloat("CurveX");
      this.curveY = tag.contains("cy") ? tag.getFloat("cy") : tag.getFloat("CurveY");
      this.curveZ = tag.contains("cz") ? tag.getFloat("cz") : tag.getFloat("CurveZ");
      this.setColor(tag.contains("c") ? tag.getString("c") : (tag.contains("Color") ? tag.getString("Color") : null));
   }

   public HairStrand copy() {
      HairStrand copy = new HairStrand(this.id);
      copy.length = this.length;
      copy.lengthScale = this.lengthScale;
      copy.rotationX = this.rotationX;
      copy.rotationY = this.rotationY;
      copy.rotationZ = this.rotationZ;
      copy.scaleX = this.scaleX;
      copy.scaleY = this.scaleY;
      copy.scaleZ = this.scaleZ;
      copy.cubeWidth = this.cubeWidth;
      copy.cubeHeight = this.cubeHeight;
      copy.cubeDepth = this.cubeDepth;
      copy.curveX = this.curveX;
      copy.curveY = this.curveY;
      copy.curveZ = this.curveZ;
      copy.color = this.color;
      copy.rgbColor = this.rgbColor != null ? (float[])this.rgbColor.clone() : null;
      return copy;
   }
}
