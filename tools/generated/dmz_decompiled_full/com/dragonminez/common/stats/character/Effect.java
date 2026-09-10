package com.dragonminez.common.stats.character;

import lombok.Generated;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class Effect {
   private final String name;
   private double power;
   private int duration;

   public Effect(String name, double power, int duration) {
      this.name = name;
      this.power = power;
      this.duration = duration;
   }

   public boolean isPermanent() {
      return this.duration == -1;
   }

   public void tick() {
      if (this.duration > 0) {
         this.duration--;
      }
   }

   public boolean isExpired() {
      return this.duration == 0;
   }

   public CompoundTag save() {
      CompoundTag nbt = new CompoundTag();
      nbt.putString("Name", this.name);
      nbt.putDouble("Power", this.power);
      nbt.putInt("Duration", this.duration);
      return nbt;
   }

   public static Effect load(CompoundTag nbt) {
      String name = nbt.getString("Name");
      double power = nbt.getDouble("Power");
      int duration = nbt.getInt("Duration");
      return new Effect(name, power, duration);
   }

   public void toBytes(FriendlyByteBuf buf) {
      buf.writeUtf(this.name);
      buf.writeDouble(this.power);
      buf.writeInt(this.duration);
   }

   public static Effect fromBytes(FriendlyByteBuf buf) {
      String name = buf.readUtf();
      double power = buf.readDouble();
      int duration = buf.readInt();
      return new Effect(name, power, duration);
   }

   public Effect copy() {
      return new Effect(this.name, this.power, this.duration);
   }

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public double getPower() {
      return this.power;
   }

   @Generated
   public int getDuration() {
      return this.duration;
   }

   @Generated
   public void setPower(double power) {
      this.power = power;
   }

   @Generated
   public void setDuration(int duration) {
      this.duration = duration;
   }
}
