package net.bullettrain.xenopixelsmod.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAttackInvoker {
    @Invoker("startAttack")
    boolean xenopixels$startAttack();

    @Invoker("continueAttack")
    void xenopixels$continueAttack(boolean leftClick);
}
