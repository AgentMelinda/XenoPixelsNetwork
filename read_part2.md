Wait, looking at the file paths:



src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudOverlay.java

src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoHudView.java

Usually, "HUD" refers to the main overlay. Let's see if there's a distinction.

The search result for XenoHudOverlay.java shows:



java

124         if (player != null) {

125             ResourceLocation skin = player.getSkinTextureLocation();

126             if (skin != null) {

127                 drawPlayerBust(g, skin, ix, iy, iw, ih);

128             }

129         }

The search result for XenoHudView.java shows:



java

314     /\*\* Player face+bust drawn inside the portrait frame — LDLib has no dynamic-skin texture primitive, so

315      \*  this uses the same vanilla skin-blit approach as the legacy renderer, just clipped to the octagon frame. \*/

316     private void drawPortraitBust(GuiGraphics g, Minecraft mc) {

Both are relevant. I will provide a list of these files.



Based on my exploration of the project, here are the relevant files related to 'xenopixels' and the specific components handling player skin rendering in the HUD:



Files Related to 'xenopixels'

src/main/java/net/bullettrain/xenopixelsmod/ - Main source directory for the mod.

src/main/resources/assets/xenopixelsmod/ - Assets and textures for the mod.

src/main/resources/data/xenopixelsmod/ - Data files (recipes, items, etc.).

run/config/xenopixelsmod-hud.json - Configuration file specifically for the HUD.

Files Handling Player Skin Rendering in the HUD

The following files contain logic for rendering player skins within various HUD elements:



src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoHudView.java

Note: This file specifically handles drawing the player face and bust inside a portrait frame. It uses a vanilla skin-blit approach clipped to an octagon frame.

src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudOverlay.java

Note: Contains the drawPlayerBust method which renders the player's face and bust based on their skin texture location in the main HUD overlay.

src/main/java/net/bullettrain/xenopixelsmod/client/XenoPartyOverlay.java

Note: Handles rendering the face of players (likely party members) from their skin textures within a party-specific overlay.





The user is reporting an issue with a "xenopixels" HUD element where the player's skin is being cut off, specifically the left half is missing and only the right half is visible. I need to find the code responsible for rendering this HUD element and identify why it might be clipping or misaligned.



First, I'll start by exploring the codebase to locate "xenopixels" and any related HUD elements.



Explored 1 area

The XenoHudOverlay.java file seems like a primary candidate since it has a drawPlayerBust method which might be responsible for drawing the player's bust in the main HUD. The user mentioned "xenopixels gets cutoff showing only half body and face of the player skin from left to right only right part showing left part is missing".



I will examine src/main/java/net/bullettrain/xenopixelsmod/client/XenoHudOverlay.java first to see how it renders the player's bust.



Read

src\\main\\java\\net\\bullettrain\\xenopixelsmod\\client\\XenoHudOverlay.java

I've reviewed XenoHudOverlay.java. The method drawPortrait calls drawPlayerBust, which is likely where the issue resides.



java

&#x20;   private static void drawPortrait(GuiGraphics g, Minecraft mc, XenoHudSnapshot snap) {

&#x20;       int s = PORTRAIT;

&#x20;       int r = PORTRAIT\_R;



&#x20;       // Soft rounded blue frame

&#x20;       fillRoundedRect(g, -2, -2, s + 4, s + 4, r + 2, 0xFF0A2F5C);

&#x20;       fillRoundedRect(g, -1, -1, s + 2, s + 2, r + 1, 0xFF1E6BB8);

&#x20;       fillRoundedRect(g, 0, 0, s, s, r, 0xFF2F8FE0);

&#x20;       fillRoundedRect(g, 2, 2, s - 4, s - 4, r - 2, 0xFF163A68);



&#x20;       int ix = PORTRAIT\_PAD;

&#x20;       int iy = PORTRAIT\_PAD;

&#x20;       int iw = s - PORTRAIT\_PAD \* 2;

&#x20;       int ih = s - PORTRAIT\_PAD \* 2;

&#x20;       int ir = Math.max(4, r - 3);



&#x20;       // Sky / ground backdrop (rounded clip approx)

&#x20;       fillRoundedRect(g, ix, iy, iw, ih, ir, 0xFF6BB7E8);

&#x20;       // top sky half

&#x20;       g.fill(ix, iy, ix + iw, iy + ih / 2, 0xFF8FD0F5);

&#x20;       // ground

&#x20;       g.fill(ix, iy + ih \* 2 / 3, ix + iw, iy + ih, 0xFF4A8A45);

&#x20;       g.fill(ix, iy + ih / 2, ix + iw, iy + ih \* 2 / 3, 0xFF5FA35A);



&#x20;       LocalPlayer player = mc.player;

&#x20;       if (player != null) {

&#x20;           ResourceLocation skin = player.getSkinTextureLocation();

&#x20;           if (skin != null) {

&#x20;               drawPlayerBust(g, skin, ix, iy, iw, ih);

&#x20;           }

&#x20;       }



&#x20;       // Thin dark rim only (must NOT fill over the bust)

&#x20;       drawRoundedBorder(g, 0, 0, s, s, r, 0xFF0A2A55, 2);

&#x20;       // Soft top highlight strip (alpha)

&#x20;       g.fill(r, 0, s - r, 2, 0x55FFFFFF);



&#x20;       if (snap.transforming) {

&#x20;           drawTransformChargeBorder(g, -4, -4, s + 8, s + 8, r + 3, snap.transformChargePercent);

&#x20;       }

&#x20;   }

In drawPortrait, the coordinates for the bust are:

ix = PORTRAIT\_PAD;

iy = PORTRAIT\_PAD;

iw = s - PORTRAIT\_PAD \* 2;

ih = s - PORTRAIT\_PAD \* 2;



Where s = PORTRAIT = 68 and PORTRAIT\_PAD = 4.

So:

ix = 4

iy = 4

iw = 68 - 8 = 60

ih = 68 - 8 = 60



Now let's look at drawPlayerBust:



java

&#x20;   private static void drawPlayerBust(GuiGraphics g, ResourceLocation skin, int x, int y, int w, int h) {

&#x20;       RenderSystem.enableBlend();

&#x20;       RenderSystem.defaultBlendFunc();

&#x20;       RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

&#x20;       RenderSystem.setShaderTexture(0, skin);



&#x20;       // Face first (simpler 5-arg API is reliable)

&#x20;       int head = Math.max(16, Math.round(w \* 0.70f));

&#x20;       int headX = x + (w - head) / 2;

&#x20;       int headY = y + 3;

&#x20;       PlayerFaceRenderer.draw(g, skin, headX, headY, head);



&#x20;       // Upper body under chin

&#x20;       int bodyW = Math.round(head \* 1.05f);

&#x20;       int bodyH = Math.max(8, Math.round(h \* 0.36f));

&#x20;       int bodyX = x + (w - bodyW) / 2;

&#x20;       int bodyY = headY + head - Math.max(2, head / 10);

&#x20;       g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 20f, 8, 12, 64, 64);

&#x20;       g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 36f, 8, 12, 64, 64);



&#x20;       int armW = Math.max(4, bodyW / 4);

&#x20;       int armH = Math.round(bodyH \* 0.9f);

&#x20;       g.blit(skin, bodyX - armW + 2, bodyY + 1, armW, armH, 44f, 20f, 4, 12, 64, 64);

&#x20;       g.blit(skin, bodyX + bodyW - 2, bodyY + 1, armW, armH, 36f, 52f, 4, 12, 64, 64);



&#x20;       RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

&#x20;   }

The user says "showing only half body and face of the player skin from left to right only right part showing left part is missing". This suggests that either x or w might be wrong, or something else is clipping it.



Wait, I notice this in drawPortrait:



java

&#x20;       // Thin dark rim only (must NOT fill over the bust)

&#x20;       drawRoundedBorder(g, 0, 0, s, s, r, 0xFF0A2A55, 2);

The x and y for this border are 0, 0. But the portrait seems to be centered at something else?



Let's check how the HUD is drawn. In renderHud:



java

&#x20;       graphics.pose().pushPose();

&#x20;       graphics.pose().translate(XenoHudConfig.x, XenoHudConfig.y, 0);

&#x20;       graphics.pose().scale(XenoHudConfig.scale, XenoHudConfig.scale, 1f);



&#x20;       RenderSystem.enableBlend();

&#x20;       RenderSystem.defaultBlendFunc();

&#x20;       RenderSystem.setShaderColor(1f, 1f, 1f, 1f);



&#x20;       drawPortraitDropShadow(graphics);

&#x20;       drawPortrait(graphics, mc, snap);

&#x20;       // ...

The graphics.pose().translate moves the origin to XenoHudConfig.x, XenoHudConfig.y.

Then drawPortrait is called. Inside drawPortrait, it uses coordinates relative to this translated origin.



Wait, let's look at drawPortraitDropShadow:



java

&#x20;   private static void drawPortraitDropShadow(GuiGraphics g) {

&#x20;       int s = PORTRAIT;

&#x20;       for (int i = 5; i >= 1; i--) {

&#x20;           int a = 8 + i \* 10;

&#x20;           fillRoundedRect(g, 1 - i, 3 + i, s + 2 + i \* 2, s + 4 + i, PORTRAIT\_R + i, (a << 24));

&#x20;       }

&#x20;   }

It uses s = PORTRAIT = 68. It draws from around 1 to 69.



And drawPortrait:



java

&#x20;   private static void drawPortrait(GuiGraphics g, Minecraft mc, XenoHudSnapshot snap) {

&#x20;       int s = PORTRAIT; // 68

&#x20;       // ...

&#x20;       fillRoundedRect(g, -2, -2, s + 4, s + 4, r + 2, 0xFF0A2F5C);

&#x20;       // ...

&#x20;       drawRoundedBorder(g, 0, 0, s, s, r, 0xFF0A2A55, 2);

Wait, s is 68. So the portrait is roughly at (0,0) with width/height of 68.



But wait, if it's being cut off from the left, maybe the x coordinate passed to drawPlayerBust is wrong?

In drawPortrait:

ix = PORTRAIT\_PAD; (4)

iy = PORTRAIT\_PAD; (4)

iw = s - PORTRAIT\_PAD \* 2; (60)

ih = s - PORTRAIT\_PAD \* 2; (60)



So the bust is drawn at (4, 4) with width/height 60.

The portrait itself seems to be centered around (34, 34) if it's 68x68? No, if it starts at (-2, -2) and is 72x72 (approx), then the center is (-2 + 36, -2 + 36) = (34, 34).



If it's being cut off from the left, maybe there's a clipping happening?

Wait, I see:



java

&#x20;       // Thin dark rim only (must NOT fill over the bust)

&#x20;       drawRoundedBorder(g, 0, 0, s, s, r, 0xFF0A2A55, 2);

Let's look at XenoHudView.java as well, it might be relevant if LDLib is being used. But the user said "xenopixels gets cutoff", and I see both a legacy and an LDLib-backed renderer. If they are using the default one (legacy), then it's the code in XenoHudOverlay.



Wait, let me check drawPlayerBust again.



java

&#x20;   private static void drawPlayerBust(GuiGraphics g, ResourceLocation skin, int x, int y, int w, int h) {

&#x20;       // ...

&#x20;       int head = Math.max(16, Math.round(w \* 0.70f));

&#x20;       int headX = x + (w - head) / 2;

&#x20;       int headY = y + 3;

&#x20;       PlayerFaceRenderer.draw(g, skin, headX, headY, head);



&#x20;       // Upper body under chin

&#x20;       int bodyW = Math.round(head \* 1.05f);

&#x20;       int bodyH = Math.max(8, Math.round(h \* 0.36f));

&#x20;       int bodyX = x + (w - bodyW) / 2;

&#x20;       int bodyY = headY + head - Math.max(2, head / 10);

&#x20;       g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 20f, 8, 12, 64, 64);

&#x20;       // ...

If w is 60, then head is Math.round(60 \* 0.7) = 42.

headX = 4 + (60 - 42) / 2 = 4 + 18 / 2 = 4 + 9 = 13.



Wait, if the portrait is at x=4, and we draw a bust of width 42 starting at x=13, it should be visible.

But maybe there's some global clipping?



Actually, look at this:



java

&#x20;       // Thin dark rim only (must NOT fill over the bust)

&#x20;       drawRoundedBorder(g, 0, 0, s, s, r, 0xFF0A2A55, 2);

Is it possible that drawPortrait is being clipped by some other element?



Wait, I notice something. In XenoHudOverlay.java:

The class implements IGuiOverlay.

The render method calls renderHud(graphics, screenWidth, screenHeight, false).

renderHud translates the graphics context.



Let's look at src/main/java/net/bullettrain/xenopixelsmod/client/hud/XenoHudView.java. Maybe it also has issues? But I should first try to reproduce or understand why it might be cut off in XenoHudOverlay.



The user says "left part is missing".

If the left part of the skin is missing, but the right part is showing.

In drawPlayerBust:

g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 20f, 8, 12, 64, 64);



If bodyX is too large, the left part would be cut off.

bodyX = x + (w - bodyW) / 2;

With x=4, w=60, bodyW = Math.round(head \* 1.05f) where head = 42.

bodyW = Math.round(42 \* 1.05) = Math.round(44.1) = 44.

bodyX = 4 + (60 - 44) / 2 = 4 + 8 = 12.



If the bust is drawn at x=12, and the portrait starts at x=4, it should be visible.



Wait, I see something else in drawPortrait:



java

&#x20;       // Soft rounded blue frame

&#x20;       fillRoundedRect(g, -2, -2, s + 4, s + 4, r + 2, 0xFF0A2F5C);

&#x20;       fillRoundedRect(g, -1, -1, s + 2, s + 2, r + 1, 0xFF1E6BB8);

&#x20;       fillRoundedRect(g, 0, 0, s, s, r, 0xFF2F8FE0);

&#x20;       fillRoundedRect(g, 2, 2, s - 4, s - 4, r - 2, 0xFF163A68);

The portrait frame is around x=0, y=0 with size s=68.



Wait, I might have missed something. Is there any clipping in the renderHud method or before it?

graphics.pose().translate(XenoHudConfig.x, XenoHudConfig.y, 0);



If XenoHudConfig.x is small, maybe it's getting clipped by the edge of the screen? But the user says "every other element of hud works but that". So it's specifically the skin.



Let me check drawPlayerBust again.

Is it possible that w or h are too small?

iw = s - PORTRAIT\_PAD \* 2; (60)

ih = s - PORTRAIT\_PAD \* 2; (60)



Wait! I see something in drawPlayerBust:



java

&#x20;       // Upper body under chin

&#x20;       int bodyW = Math.round(head \* 1.05f);

&#x20;       int bodyH = Math.max(8, Math.round(h \* 0.36f));

&#x20;       int bodyX = x + (w - bodyW) / 2;

&#x20;       int bodyY = headY + head - Math.max(2, head / 10);

&#x20;       g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 20f, 8, 12, 64, 64);

If bodyW is larger than w, then bodyX would be negative or small.

head = Math.round(w \* 0.70f)

bodyW = Math.round(Math.round(w \* 0.70f) \* 1.05f)

If w=60, head=42, bodyW=44. w > bodyW is true.



Wait, what if the skin texture itself is being sampled incorrectly?

The blit call: g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 20f, 8, 12, 64, 64);

The u and v coordinates are 20f and 20f. This is the standard for player head/body in some versions or custom skins.



Wait, let me check the PlayerFaceRenderer.draw call:

PlayerFaceRenderer.draw(g, skin, headX, headY, head);

The head parameter



