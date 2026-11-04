package local.generalsmc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
/** Compact fields and lightweight menu rows share the same control states. */
final class LobbyCell extends MenuButton {
 final boolean dropdown,primary;final int chip;Tooltip currentTooltip;boolean option,popover;
 private final OnPress activate;
 LobbyCell(String text,int x,int y,int w,int h,boolean dropdown,int chip,boolean primary,OnPress action){super(x,y,w,h,L.component(text),action,DEFAULT_NARRATION);this.dropdown=dropdown;this.chip=chip;this.primary=primary;this.activate=action;kind=primary?Kind.PRIMARY:Kind.FIELD;setTooltip(Tooltip.create(L.component(text)));}
 LobbyCell action(){if(!primary)kind=Kind.SECONDARY;return this;}
 LobbyCell option(boolean selected){option=true;this.selected=selected;return this;}
 LobbyCell popover(){popover=true;active=false;setTooltip(null);return this;}
 @Override public void setTooltip(Tooltip value){super.setTooltip(value);currentTooltip=value;}
 void activate(){if(active){pressFeedback();activate.onPress(this);}}
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  int x=getX(),y=getY(),w=getWidth(),h=getHeight();
  if(popover){UiPaint.panel(g,x,y,w,h);return;}
  boolean focus=active&&isFocused();float amount=hover(active&&isHovered());int ink=UiPaint.TEXT;
  if(option){if(selected||focus||amount>.01f)UiPaint.rounded(g,x+2,y+1,w-4,h-2,focus?0xff3b5060:selected?0xff253d4d:UiPaint.mix(0x00263848,0xff2a3d4e,amount));if(focus){g.fill(x+2,y+4,x+4,y+h-4,UiPaint.FOCUS);} }
  else ink=UiPaint.button(g,x,y,w,h,kind,active,selected,focus,amount,pressed());
  var font=Minecraft.getInstance().font;int inset=9;
  if(chip!=-1){UiPaint.rounded(g,x+8,y+h/2-5,10,10,0xffd1dce2);UiPaint.rounded(g,x+9,y+h/2-4,8,8,chip);inset=24;}
  String text=fitted(font,w-inset-(dropdown||option?18:8));if(option&&captionClipped!=(currentTooltip!=null))setTooltip(captionClipped?Tooltip.create(getMessage()):null);int tx=primary?x+(w-fittedWidth)/2:x+inset;
  g.text(font,text,tx,y+(h-font.lineHeight)/2+(pressed()&&active?1:0),ink,false);
  if(dropdown&&active)UiPaint.arrow(g,x+w-12,y+h/2,UiPaint.MUTED);
  if(option&&selected)UiPaint.check(g,x+w-17,y+h/2-4,UiPaint.ACCENT);
 }
}
