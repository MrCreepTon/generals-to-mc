package local.generalsmc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Explicit action hierarchy, stable hit targets with or without Modern UI. */
class MenuButton extends Button.Plain {
 net.minecraft.client.gui.components.Tooltip currentTooltip;
 @Override public void setTooltip(net.minecraft.client.gui.components.Tooltip value){currentTooltip=value;super.setTooltip(value);}
 static final boolean MODERN=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modernui")||Boolean.getBoolean("generalsmc.testModernUi");
 enum Kind { SECONDARY,PRIMARY,QUIET,DANGER,TAB,FIELD }
 int glyph;Kind kind=Kind.SECONDARY;boolean selected,toggle,disclosure,leftAligned,stepper;
 private float hover;private long animationAt,pressedUntil,layoutUntil;private Font layoutFont;private Component layoutMessage;
 boolean captionClipped;private int layoutAvailable=-1;private String fitted="";int fittedWidth;
 protected MenuButton(int x,int y,int w,int h,Component text,OnPress action,CreateNarration narration){super(x,y,w,h,text,action,narration);}
 static Builder styled(Component text,OnPress action){return new Builder(text,action);}
 static Button access(Button b,boolean allowed,String reason){b.active=allowed;b.setTooltip(allowed?null:net.minecraft.client.gui.components.Tooltip.create(L.component(reason)));return b;}
 static Button primary(Button b){if(b instanceof MenuButton m)m.kind=Kind.PRIMARY;return b;}
 static Button quiet(Button b){if(b instanceof MenuButton m)m.kind=Kind.QUIET;return b;}
 static Button danger(Button b){if(b instanceof MenuButton m)m.kind=Kind.DANGER;return b;}
 static final class Builder {
  final Component text;final OnPress action;int x,y,w=150,h=20;Kind kind=Kind.SECONDARY;boolean selected,toggle,disclosure,leftAligned,stepper;
  Builder(Component text,OnPress action){this.text=text;this.action=action;}
  Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
  Builder width(int w){this.w=w;return this;}Builder pos(int x,int y){this.x=x;this.y=y;return this;}
  Builder row(){leftAligned=true;kind=Kind.FIELD;return this;}Builder choice(){row();disclosure=true;return this;}
  Builder primary(){kind=Kind.PRIMARY;return this;}Builder quiet(){kind=Kind.QUIET;return this;}Builder danger(){kind=Kind.DANGER;return this;}
  Builder selected(boolean value){kind=Kind.TAB;selected=value;return this;}Builder toggle(boolean value){toggle=true;selected=value;return this;}
  Button build(){var b=new MenuButton(x,y,w,h,text,action,DEFAULT_NARRATION);b.kind=kind;b.selected=selected;b.toggle=toggle;b.leftAligned=leftAligned;b.disclosure=disclosure;b.glyph=switch(text.getString()){case "↻"->1;case "✎"->2;case "×"->3;case "←"->4;case "→"->5;case "+"->6;default->0;};return b;}
 }
 void pressFeedback(){pressedUntil=System.nanoTime()+90_000_000L;}
 @Override public void onPress(net.minecraft.client.input.InputWithModifiers input){pressFeedback();super.onPress(input);}
 boolean pressed(){return System.nanoTime()<pressedUntil;}
 float hover(boolean target){long now=System.nanoTime();double dt=animationAt==0?0:Math.min(.2,(now-animationAt)/1e9);animationAt=now;hover+=( (target?1:0)-hover)*(float)(1-Math.exp(-dt/0.075));return hover;}
 String fitted(Font font,int available){
  available=Math.max(0,available);long now=System.nanoTime();
  if(layoutFont!=font||layoutMessage!=getMessage()||layoutAvailable!=available||now>=layoutUntil){
   layoutFont=font;layoutMessage=getMessage();layoutAvailable=available;layoutUntil=now+500_000_000L;String label=layoutMessage.getString();
   captionClipped=font.width(label)>available;fitted=!captionClipped?label:available<font.width("…")?"":font.plainSubstrByWidth(label,Math.max(0,available-font.width("…")))+"…";fittedWidth=font.width(fitted);
  }return fitted;
 }
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  boolean over=active&&isHovered(),focus=active&&isFocused();int x=getX(),y=getY(),w=getWidth(),h=getHeight();
  int ink=UiPaint.button(g,x,y,w,h,kind,active,selected,focus,hover(over),pressed());
  var font=Minecraft.getInstance().font;String text=fitted(font,w-(toggle?48:disclosure||stepper?34:18));
  int tx=toggle||leftAligned?x+9:x+(w-fittedWidth)/2,ty=y+(h-font.lineHeight)/2+(pressed()&&active?1:0);
  if(glyph!=0)UiPaint.icon(g,x+w/2-4,y+h/2-4,glyph,ink);else g.text(font,text,tx,ty,ink,false);
  if(disclosure)UiPaint.arrow(g,x+w-12,y+h/2,UiPaint.MUTED);
  if(stepper)g.text(font,"‹ ›",x+w-24,y+(h-font.lineHeight)/2,UiPaint.MUTED,false);
  if(toggle)UiPaint.toggle(g,x+w-31,y+(h-11)/2,selected,active);
 }
 static void panel(GuiGraphicsExtractor g,int width,int height){panel(g,width,height,310);}
 static void panel(GuiGraphicsExtractor g,int width,int height,int contentWidth){panel(g,width,height,contentWidth,40,height-43);}
 static void panel(GuiGraphicsExtractor g,int width,int height,int contentWidth,int header,int footer){int w=Math.min(width-8,contentWidth+32),x=(width-w)/2;UiPaint.panel(g,x,7,w,height-14);g.fill(x+12,header,x+w-12,header+1,UiPaint.BORDER);g.fill(x+12,footer,x+w-12,footer+1,UiPaint.BORDER);}
}
