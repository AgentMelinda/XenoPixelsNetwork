package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.v2.V2Keys;
import net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.lwjgl.glfw.GLFW;

/** Victim-only server recovery meter; exact native red atlas plate from tools/atlas-panels. */
@EventBusSubscriber(modid=XenoPixelsMod.MOD_ID,value=Dist.CLIENT)
public final class CombatRecoveryHud {
    private CombatRecoveryHud(){}
    private static boolean playerActive(){
        Minecraft mc=Minecraft.getInstance();
        return mc.player!=null && mc.screen==null && mc.player.isAlive() && !mc.player.isSpectator();
    }
    private static boolean active(){
        Minecraft mc=Minecraft.getInstance();
        return playerActive() && V3ClientState.recovery()!=null;
    }
    public static void render(GuiGraphics gui, DeltaTracker delta){
        Minecraft mc=Minecraft.getInstance();
        if(!playerActive() || mc.options.hideGui)return;
        if(V3ClientState.rushPromptTicks()>0){
            int x=(gui.guiWidth()-120)/2,y=gui.guiHeight()/2-30;
            XenoAtlasSprites.blit(gui,"combat_prompt_rush_w120",XenoAtlasSprites.Theme.BLUE,x,y);
            String label=V3ClientState.rushUltimate()?"ULTIMATE RUSH":"SUPER RUSH";
            gui.pose().pushPose();gui.pose().translate(x+60,y+5,0);gui.pose().scale(.75f,.75f,1);
            gui.drawString(mc.font,label,-mc.font.width(label)/2,0,0xFFFFFFFF,true);gui.pose().popPose();
            XenoAtlasSprites.blit(gui,"combat_prompt_light_w120",XenoAtlasSprites.Theme.BLUE,x,y+23);
            gui.fill(x+5,y+27,x+115,y+31,0xFF081E38);
            int fill=Math.round(110*V3ClientState.rushProgress());
            if(fill>0){gui.fill(x+5,y+27,x+5+fill,y+31,0xFF279BFA);gui.fill(x+5,y+27,x+5+fill,y+28,0xFF9DE3FF);}
            String prompt="MASH SPACE"+(V3ClientState.config().randomRush().deadlineEnabled()?"  "+String.format(java.util.Locale.ROOT,"%.1fs",V3ClientState.rushPromptTicks()/20f):"");
            gui.drawString(mc.font,prompt,x+60-mc.font.width(prompt)/2,y+33,0xFFCCEBFF,false);
        }
        if(!active())return;
        int x=(gui.guiWidth()-120)/2,y=gui.guiHeight()/2+(V3ClientState.rushPromptTicks()>0?24:36);
        XenoAtlasSprites.blit(gui,"combat_prompt_alert_w120",XenoAtlasSprites.Theme.RED,x,y);
        String label="RECOVER — MASH KEYS";
        gui.pose().pushPose();
        gui.pose().translate(x+60,y+7,0);gui.pose().scale(.65f,.65f,1);
        gui.drawString(mc.font,label,-mc.font.width(label)/2,0,0xFFFFDDDD,false);
        gui.pose().popPose();
        int width=Math.round(96*V3ClientState.recovery().progress());
        gui.fill(x+12,y+20,x+108,y+26,0xFF26090D);
        if(width>0){
            gui.fill(x+12,y+20,x+12+width,y+26,0xFFD52B42);
            gui.fill(x+12,y+20,x+12+width,y+22,0xFFFF7384);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void key(InputEvent.Key event){
        if(playerActive() && V3ClientState.rushPromptTicks()>0 && event.getAction()==GLFW.GLFW_PRESS && event.getKey()==GLFW.GLFW_KEY_SPACE){
            V3ClientState.sendRushMash();return;
        }
        if(!active() || event.getAction()!=GLFW.GLFW_PRESS)return;
        if(event.getKey()==GLFW.GLFW_KEY_F){V3ClientState.sendRecovery();return;}
        Minecraft mc=Minecraft.getInstance();
        KeyMapping[] keys={mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,
                mc.options.keyJump,mc.options.keyShift,V2Keys.LIGHT,V2Keys.HEAVY,V2Keys.GUARD,
                V2Keys.VANISH,Bt3CombatClient.DRAGON_DASH};
        for(KeyMapping key:keys)if(key!=null && key.matches(event.getKey(),event.getScanCode())){
            V3ClientState.sendRecovery();return;
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event){
        if(!active())return;
        if(event.getAction()==GLFW.GLFW_PRESS && (event.getButton()==GLFW.GLFW_MOUSE_BUTTON_LEFT
                || event.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT))V3ClientState.sendRecovery();
        event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void movement(MovementInputUpdateEvent event){
        if(!active() || event.getEntity()!=Minecraft.getInstance().player)return;
        event.getInput().forwardImpulse=0;event.getInput().leftImpulse=0;
        event.getInput().jumping=false;event.getInput().shiftKeyDown=false;
    }
}
