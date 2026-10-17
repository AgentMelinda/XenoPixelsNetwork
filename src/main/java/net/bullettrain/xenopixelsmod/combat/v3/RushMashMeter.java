package net.bullettrain.xenopixelsmod.combat.v3;

/** Pure server meter: at most one press per tick; expiry uses the original ready deadline. */
public final class RushMashMeter {
    public static final int TOTAL=1000;
    private final long started;
    private final RandomRushSettings settings;
    private long lastPress=Long.MIN_VALUE;
    private int completed;
    public RushMashMeter(long started, RandomRushSettings settings){this.started=started;this.settings=settings;}
    public boolean expired(long now){return now<started || settings.deadlineEnabled() && now-started>=settings.windowTicks();}
    public boolean press(long now){
        if(expired(now)||now<=lastPress||full())return false;
        lastPress=now;completed=Math.min(TOTAL,completed+(settings.meterEnabled()?settings.fillPerPress():TOTAL));return true;
    }
    public boolean full(){return completed==TOTAL;}
    public int completed(){return completed;}
    public int ticksLeft(long now){return expired(now)?0:settings.deadlineEnabled()?(int)(settings.windowTicks()-(now-started)):1200;}
}
