package net.bullettrain.xenopixelsmod.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

@AutoRegisterCapability
public class XenoPlayerData {
    private float ki = 100f;
    private float maxKi = 100f;
    private float stamina = 100f;
    private float maxStamina = 100f;

    public float getKi() { return ki; }
    public void setKi(float ki) { this.ki = Math.max(0, Math.min(ki, maxKi)); }
    public float getMaxKi() { return maxKi; }
    public void setMaxKi(float maxKi) { this.maxKi = maxKi; }

    public float getStamina() { return stamina; }
    public void setStamina(float stamina) { this.stamina = Math.max(0, Math.min(stamina, maxStamina)); }
    public float getMaxStamina() { return maxStamina; }
    public void setMaxStamina(float maxStamina) { this.maxStamina = maxStamina; }

    public void copyFrom(XenoPlayerData other) {
        this.ki = other.ki;
        this.maxKi = other.maxKi;
        this.stamina = other.stamina;
        this.maxStamina = other.maxStamina;
    }

    public void saveNBT(CompoundTag tag) {
        tag.putFloat("Ki", ki);
        tag.putFloat("MaxKi", maxKi);
        tag.putFloat("Stamina", stamina);
        tag.putFloat("MaxStamina", maxStamina);
    }

    public void loadNBT(CompoundTag tag) {
        ki = tag.getFloat("Ki");
        maxKi = tag.getFloat("MaxKi");
        stamina = tag.getFloat("Stamina");
        maxStamina = tag.getFloat("MaxStamina");
    }
}