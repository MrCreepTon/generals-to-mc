package local.generalsmc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Optional styling; vanilla widgets remain the fallback without Modern UI. */
class MenuButton extends Button.Plain {
 static final boolean MODERN=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modernui")||Boolean.getBoolean("generalsmc.testModernUi");
 private float hover;
 protected MenuButton(int x,int y,int w,int h,Component text,OnPress action,CreateNarration narration){super(x,y,w,h,text,action,narration);if(MODERN&&Minecraft.getInstance().font.width(text)>w-16)setTooltip(net.minecraft.client.gui.components.Tooltip.create(text));}
 static Builder styled(Component text,OnPress action){return new Builder(text,action);}
 static final class Builder {
  final Component text;final OnPress action;int x,y,w=150,h=20;
  Builder(Component text,OnPress action){this.text=text;this.action=action;}
  Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
  Builder width(int w){this.w=w;return this;}Builder pos(int x,int y){this.x=x;this.y=y;return this;}
  Button build(){return MODERN?new MenuButton(x,y,w,h,text,action,DEFAULT_NARRATION):Button.builder(text,action).bounds(x,y,w,h).build();}
 }
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  if(!MODERN){super.extractContents(g,mx,my,delta);return;}
  hover+=( (isHoveredOrFocused()&&active?1:0)-hover)*.3f;
  int x=getX(),y=getY(),w=getWidth(),h=getHeight();int shade=active?36+(int)(hover*18):24;int color=0xf0000000|shade<<16|(shade+6)<<8|(shade+10);
  g.fill(x+3,y,x+w-3,y+h,color);g.fill(x+1,y+1,x+w-1,y+h-1,color);g.fill(x,y+3,x+w,y+h-3,color);
  if(active&&isHoveredOrFocused())g.fill(x+3,y+4,x+5,y+h-4,0xff6cd6ef);
  var font=Minecraft.getInstance().font;String label=getMessage().getString();if(font.width(label)>w-16)label=font.plainSubstrByWidth(label,Math.max(0,w-16-font.width("…")))+"…";
  g.centeredText(font,label,x+w/2,y+(h-font.lineHeight)/2,active?0xffe8f1f6:0xff737b84);
 }
 static void panel(GuiGraphicsExtractor g,int width,int height){if(!MODERN)return;int w=Math.min(width-16,680),x=(width-w)/2;g.fill(x,7,x+w,height-7,0xeb101c27);g.fill(x,7,x+w,9,0xff438b9f);}
}
