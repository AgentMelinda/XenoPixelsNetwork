package net.bullettrain.xenopixelsmod.client;

public class XenoClientData {
    public static float health = 20f;
    public static float maxHealth = 20f;
    public static float ki = 100f;
    public static float maxKi = 100f;
    public static float stamina = 100f;
    public static float maxStamina = 100f;

    public static void update(float h, float mh, float k, float mk, float s, float ms) {
        health = h;
        maxHealth = mh;
        ki = k;
        maxKi = mk;
        stamina = s;
        maxStamina = ms;
    }
}