package net.bullettrain.xenopixelsmod.combat.v3.ki;
/** Captured cast lifetime: charging never starts the expiration clock. */
public final class KiLifetimeLimit {
    private final int cap;
    private long firstFire=-1;
    public KiLifetimeLimit(int cap){this.cap=Math.clamp(cap,1,1200);}
    public void firing(long now){if(firstFire<0)firstFire=now;}
    public boolean expired(long now){return firstFire>=0 && (now<firstFire || now-firstFire>=cap);}
    public long firstFire(){return firstFire;}
}
