# Fix `InGameHudMixin.java` — protected field access compile error

## Context

The mixin `src/main/java/net/bullettrain/tutorialmod/mixin/client/InGameHudMixin.java` injects into `net.minecraft.client.gui.Gui` to render `XenoHudOverlay`.  
Build fails with:

```
error: minecraft has protected access in Gui
    forgeGui.minecraft.getWindow().getGuiScaledWidth(),
          ^
```

## Root cause

- `Gui.minecraft` is declared `protected Minecraft minecraft;` in `net.minecraft.client.gui.Gui`.
- The mixin class lives in `net.bullettrain.tutorialmod.mixin.client`, a different package from `Gui` (`net.minecraft.client.gui`).
- From the compiler's perspective, the mixin is **not** a subclass of `Gui`, so `forgeGui.minecraft` (where `forgeGui` is typed as `ForgeGui`) violates Java access rules.
- Even though Mixin/ASM will merge the class at bytecode level, the **Java compiler** enforces access control before Mixin transforms the code.

## Fix

Replace the two lines that access the protected field via the local variable with the client-safe singleton `Minecraft.getInstance()`.

| File | Change |
|---|---|
| `src/main/java/net/bullettrain/tutorialmod/mixin/client/InGameHudMixin.java` | Replace `forgeGui.minecraft.getWindow().getGuiScaledWidth()` → `Minecraft.getInstance().getWindow().getGuiScaledWidth()` |
| same | Replace `forgeGui.minecraft.getWindow().getGuiScaledHeight()` → `Minecraft.getInstance().getWindow().getGuiScaledHeight()` |
| same | Add `import net.minecraft.client.Minecraft;` |

Resulting method body:

```java
@Inject(method = "render", at = @At("TAIL"))
private void renderXenoHud(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
    if ((Object) this instanceof ForgeGui forgeGui) {
        OVERLAY.render(forgeGui, graphics, partialTick,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }
}
```

## Notes

- `Minecraft.getInstance()` is the canonical accessor in 1.20.1 and is already used in `XenoHudOverlay.java`.
- The injected code runs on the client only (`Gui.render` is client-side), so `Minecraft.getInstance()` is safe.
- Alternative optimization (out of scope for this fix): capture `mouseX` / `mouseY` from the `render` method signature to avoid the singleton lookup. The target `Gui.render` receives `(GuiGraphics, float partialTick, int mouseX, int mouseY)` where `mouseX`/`mouseY` map to scaled GUI dimensions. If you choose to do this later, update the inject method parameters to `(GuiGraphics, float, int, int, CallbackInfo)` and pass them directly to `OVERLAY.render`.

## Validation

1. `./gradlew compileJava` — the protected-access error must disappear.
2. `./gradlew runClient` — start a world, verify the Xeno HUD overlay renders without crash.
