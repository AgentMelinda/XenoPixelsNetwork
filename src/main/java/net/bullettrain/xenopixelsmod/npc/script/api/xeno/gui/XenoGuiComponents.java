package net.bullettrain.xenopixelsmod.npc.script.api.xeno.gui;

import net.minecraft.nbt.*;
import xenoapi.npcs.api.gui.*;
import xenoapi.npcs.api.function.EventWrapper;
import xenoapi.npcs.api.function.gui.*;
import xenoapi.npcs.api.item.IItemStack;
import java.util.*;

/** Server-owned component identities; snapshots contain values only, never callback objects. */
public final class XenoGuiComponents {
    private XenoGuiComponents() {}
    public abstract static class Component implements ICustomGuiComponent {
        final XenoCustomGui gui;
        final UUID key = UUID.randomUUID();
        int id, x, y, width, height;
        boolean enabled = true, visible = true, hovered;
        String[] hover = new String[0];
        GuiComponentState enabledCondition, visibleCondition;
        final EventWrapper<GuiComponentAction<ICustomGuiComponent>> onHover = new EventWrapper<>(), onExit = new EventWrapper<>();
        Component(XenoCustomGui gui, int id, int x, int y, int width, int height) {
            this.gui = gui; this.id = id; setPos(x, y); setSize(width, height);
        }
        public int getID() { return id; }
        public ICustomGuiComponent setID(int value) { gui.check(); gui.checkId(value, this); id = value; return this; }
        public UUID getUniqueID() { return key; }
        public int getPosX() { return x; } public int getPosY() { return y; }
        public ICustomGuiComponent setPos(int x, int y) { gui.check(); XenoCustomGui.coordinate(x); XenoCustomGui.coordinate(y); this.x=x; this.y=y; return this; }
        public int getWidth() { return width; } public int getHeight() { return height; }
        public ICustomGuiComponent setSize(int w, int h) { gui.check(); XenoCustomGui.dimension(w); XenoCustomGui.dimension(h); width=w; height=h; return this; }
        public boolean hasHoverText() { return hover.length != 0; }
        public String[] getHoverText() { return hover.clone(); }
        public ICustomGuiComponent setHoverText(String text) { return setHoverText(new String[]{text}); }
        public ICustomGuiComponent setHoverText(String[] text) {
            gui.check(); Objects.requireNonNull(text); if(text.length>32) throw new IllegalArgumentException("At most 32 hover lines");
            String[] copy=text.clone(); int count=0; for(String line:copy) count+=XenoCustomGui.text(line).length();
            if(count>4096) throw new IllegalArgumentException("Hover text exceeds 4096 characters"); hover=copy; return this;
        }
        public boolean getEnabled() { return enabled && condition(enabledCondition); }
        public boolean getVisible() { return visible && condition(visibleCondition); }
        private boolean condition(GuiComponentState condition) {
            if(condition==null) return true;
            try { return gui.inScope(() -> condition.onChange(gui,this)); }
            catch(RuntimeException failure) { return false; }
        }
        public ICustomGuiComponent setEnabled(boolean value) { gui.check(); enabled=value; return this; }
        public ICustomGuiComponent setVisible(boolean value) { gui.check(); visible=value; return this; }
        public ICustomGuiComponent setEnabledCondition(GuiComponentState value) { gui.check(); enabledCondition=value; return this; }
        public ICustomGuiComponent setVisibleCondition(GuiComponentState value) { gui.check(); visibleCondition=value; return this; }
        public boolean getHovered() { return hovered; }
        public ICustomGuiComponent setHovered(boolean value) { gui.check(); hovered=value; return this; }
        public ICustomGuiComponent setOnHover(String id, GuiComponentAction<ICustomGuiComponent> callback) { gui.check(); onHover.add(XenoCustomGui.text(id),callback); return this; }
        public EventWrapper<GuiComponentAction<ICustomGuiComponent>> getOnHoverEvents() { return onHover; }
        public ICustomGuiComponent setOnHoverExit(String id, GuiComponentAction<ICustomGuiComponent> callback) { gui.check(); onExit.add(XenoCustomGui.text(id),callback); return this; }
        public EventWrapper<GuiComponentAction<ICustomGuiComponent>> getOnHoverExitEvents() { return onExit; }
        public int getType() { throw XenoGuiUnsupported.unsupported("getType numeric mapping (not specified by the official interface documentation)"); }
        abstract String kind();
        CompoundTag snapshot() {
            CompoundTag tag=new CompoundTag(); tag.putUUID("Key",key); tag.putInt("ID",id); tag.putString("Kind",kind());
            tag.putInt("X",x); tag.putInt("Y",y); tag.putInt("W",width); tag.putInt("H",height);
            tag.putBoolean("Enabled",getEnabled()); tag.putBoolean("Visible",getVisible());
            ListTag lines=new ListTag(); for(String line:hover) lines.add(StringTag.valueOf(line)); tag.put("Hover",lines);
            return tag;
        }
    }
    public static final class Button extends Component implements IButton {
        String label; int color=0xffffff, offsetX,offsetY,hoverOffset;
        Rect texture;
        final EventWrapper<GuiComponentAction<IButton>> presses=new EventWrapper<>();
        Button(XenoCustomGui gui,int id,String label,int x,int y,int w,int h) { super(gui,id,x,y,w,h); this.label=XenoCustomGui.text(label); }
        String kind() { return "button"; }
        public String getLabel() { return label; }
        public IButton setLabel(String value) { gui.check(); label=XenoCustomGui.text(value); return this; }
        public IButton appendLabel(String value,Object... args) { return setLabel(label+String.format(Locale.ROOT,value,args)); }
        public IButton setLabelOffset(int x,int y) { gui.check(); XenoCustomGui.coordinate(x); XenoCustomGui.coordinate(y); offsetX=x;offsetY=y;return this; }
        public int getLabelOffsetX() { return offsetX; } public int getLabelOffsetY() { return offsetY; }
        public ITexturedRect getTextureRect() { return texture; }
        public void setTextureRect(ITexturedRect rect) { gui.check(); if(rect!=null && (!(rect instanceof Rect r)||r.gui!=gui)) throw new IllegalArgumentException("Foreign texture rectangle"); texture=(Rect)rect; }
        public String getTexture() { return texture==null?"":texture.texture; }
        public boolean hasTexture() { return texture!=null&&!texture.texture.isEmpty(); }
        public IButton setTexture(String value) { gui.check(); if(texture==null) texture=new Rect(gui,id,value,0,0,width,height); else texture.setTexture(value); return this; }
        public int getTextureX() { return texture==null?0:texture.u; } public int getTextureY() { return texture==null?0:texture.v; }
        public IButton setTextureOffset(int u,int v) { gui.check(); if(texture==null) throw new IllegalStateException("Set a texture first"); texture.setTextureOffset(u,v);return this; }
        public int getTextureHoverOffset() { return hoverOffset; }
        public IButton setTextureHoverOffset(int value) { gui.check(); XenoCustomGui.coordinate(value); hoverOffset=value;return this; }
        public IItemStack getDisplayItem() { throw XenoGuiUnsupported.unsupported("button item rendering"); }
        public IButton setDisplayItem(IItemStack value) { throw XenoGuiUnsupported.unsupported("button item rendering"); }
        public int getColor() { return color; } public IButton setColor(int value) { gui.check();color=value;return this; }
        public IButton setOnPress(String id,GuiComponentAction<IButton> callback) { gui.check(); presses.add(XenoCustomGui.text(id),callback);return this; }
        public EventWrapper<GuiComponentAction<IButton>> getOnPressEvents() { return presses; }
        public IButton setOnHold(String id,GuiComponentHold<IButton> callback) { throw XenoGuiUnsupported.unsupported("button hold input"); }
        public EventWrapper<GuiComponentHold<IButton>> getOnHoldEvents() { throw XenoGuiUnsupported.unsupported("button hold input"); }
        CompoundTag snapshot() { var tag=super.snapshot();tag.putString("Text",label);tag.putInt("Color",color);tag.putInt("LabelOffsetX",offsetX);tag.putInt("LabelOffsetY",offsetY);tag.putInt("TextureHoverOffset",hoverOffset); if(texture!=null) texture.textureFields(tag);return tag; }
    }
    public static final class Label extends Component implements ILabel {
        String text; int color,alignment; float scale=1;
        Label(XenoCustomGui gui,int id,String text,int x,int y,int w,int h,int color) { super(gui,id,x,y,w,h);this.text=XenoCustomGui.text(text);this.color=color; }
        String kind() { return "label"; }
        public String getText() { return text; } public ILabel setText(String value) { gui.check();text=XenoCustomGui.text(value);return this; }
        public ILabel append(String value,Object... args) { return setText(text+String.format(Locale.ROOT,value,args)); }
        public int getColor() { return color; } public ILabel setColor(int value) { gui.check();color=value;return this; }
        public float getScale() { return scale; } public ILabel setScale(float value) { gui.check();XenoCustomGui.scale(value);scale=value;return this; }
        public boolean getCentered() { return alignment==1; } public ILabel setCentered(boolean value) { return setAlignment(value?1:0); }
        public int getAlignment() { return alignment; } public ILabel setAlignment(int value) { gui.check();if(value<0||value>2) throw new IllegalArgumentException("Alignment must be 0 left, 1 center or 2 right");alignment=value;return this; }
        CompoundTag snapshot() { var tag=super.snapshot();tag.putString("Text",text);tag.putInt("Color",color);tag.putFloat("Scale",scale);tag.putInt("Alignment",alignment);return tag; }
    }
    public static final class TextField extends Component implements ITextField {
        String text=""; int color=0xffffff,type; boolean focused,hideBackground; Double min,max;
        final EventWrapper<GuiComponentAction<ITextField>> changes=new EventWrapper<>(),lost=new EventWrapper<>();
        TextField(XenoCustomGui gui,int id,int x,int y,int w,int h) { super(gui,id,x,y,w,h); }
        String kind() { return "textfield"; }
        public String getText() { return text; } public ITextField setText(String value) { gui.check();text=XenoCustomGui.text(value);return this; }
        boolean accepts(String value) {
            if(value.length()>4096) return false;
            if(type==0) return true;
            if(value.isEmpty()||value.equals("-")||type==2&&(value.equals(".")||value.equals("-."))) return true;
            try { double number=type==1?Long.parseLong(value):Double.parseDouble(value);return Double.isFinite(number)&&(min==null||number>=min)&&(max==null||number<=max); }
            catch(NumberFormatException failure) { return false; }
        }
        public int getColor() { return color; } public ITextField setColor(int value) { gui.check();color=value;return this; }
        public ITextField setOnChange(String id,GuiComponentAction<ITextField> callback) { gui.check();changes.add(XenoCustomGui.text(id),callback);return this; }
        public EventWrapper<GuiComponentAction<ITextField>> getOnChangeEvents() { return changes; }
        public ITextField setOnFocusLost(String id,GuiComponentAction<ITextField> callback) { gui.check();lost.add(XenoCustomGui.text(id),callback);return this; }
        public EventWrapper<GuiComponentAction<ITextField>> getOnFocusLostEvents() { return lost; }
        public ITextField setFocused(boolean value) { gui.check();focused=value;return this; } public boolean getFocused() { return focused; }
        public ITextField setHideBackground(boolean value) { gui.check();hideBackground=value;return this; } public boolean getHideBackground() { return hideBackground; }
        public ITextField setCharacterType(int value) { gui.check();if(value<0||value>2) throw new IllegalArgumentException("Character type must be 0, 1 or 2");type=value;return this; } public int getCharacterType() { return type; }
        public int getInteger() { return Integer.parseInt(text); } public ITextField setInteger(int value) { return setText(Integer.toString(value)); }
        public float getFloat() { float value=Float.parseFloat(text);if(!Float.isFinite(value)) throw new IllegalStateException("Nonfinite field value");return value; }
        public ITextField setFloat(float value) { if(!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite field value");return setText(Float.toString(value)); }
        public long getLong() { return Long.parseLong(text); } public ITextField setLong(long value) { return setText(Long.toString(value)); }
        public ITextField setMinMax(Number low,Number high) { gui.check();double a=Objects.requireNonNull(low).doubleValue(),b=Objects.requireNonNull(high).doubleValue();if(!Double.isFinite(a)||!Double.isFinite(b)||a>b) throw new IllegalArgumentException("Invalid numeric bounds");min=a;max=b;return this; }
        public void insertText(String value) { setText(text+value); }
        CompoundTag snapshot() { var tag=super.snapshot();tag.putString("Text",text);tag.putInt("Color",color);tag.putBoolean("Focused",focused);tag.putBoolean("HideBackground",hideBackground);tag.putInt("CharacterType",type);if(min!=null) tag.putDouble("Min",min);if(max!=null) tag.putDouble("Max",max);return tag; }
    }
    public static final class Rect extends Component implements ITexturedRect {
        String texture; int u,v;float scale=1;
        Rect(XenoCustomGui gui,int id,String texture,int x,int y,int w,int h) { super(gui,id,x,y,w,h);this.texture=XenoCustomGui.texture(texture); }
        String kind() { return "rect"; }
        public String getTexture() { return texture; } public ITexturedRect setTexture(String value) { gui.check();texture=XenoCustomGui.texture(value);return this; }
        public float getScale() { return scale; } public ITexturedRect setScale(float value) { gui.check();XenoCustomGui.scale(value);scale=value;return this; }
        public int getTextureX() { return u; } public int getTextureY() { return v; }
        public ITexturedRect setTextureOffset(int x,int y) { gui.check();XenoCustomGui.coordinate(x);XenoCustomGui.coordinate(y);if(x<0||y<0) throw new IllegalArgumentException("Texture offsets cannot be negative");u=x;v=y;return this; }
        public ITexturedRect setRepeatingTexture(int w,int h,int border) { throw XenoGuiUnsupported.unsupported("repeating textures"); }
        void textureFields(CompoundTag tag) { tag.putString("Texture",texture);tag.putInt("U",u);tag.putInt("V",v);tag.putInt("TexW",256);tag.putInt("TexH",256);tag.putFloat("Scale",scale); }
        CompoundTag snapshot() { var tag=super.snapshot();textureFields(tag);return tag; }
    }
}
