package local.generalsmc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;

/** Table control, with native focus, narration and keyboard activation. */
final class LobbyCell extends MenuButton {
    final boolean dropdown,primary;final int chip;
    private float hover;private final OnPress activate;
    LobbyCell(String text,int x,int y,int w,int h,boolean dropdown,int chip,boolean primary,OnPress action){
        super(x,y,w,h,L.component(text),action,DEFAULT_NARRATION);this.dropdown=dropdown;this.chip=chip;this.primary=primary;this.activate=action;
        setTooltip(Tooltip.create(L.component(text)));
    }
    void activate(){activate.onPress(this);}
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){
        boolean focused=active&&isHoveredOrFocused();hover+=( (focused?1:0)-hover)*.3f;
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        int edge=focused?0xff76e9ff:primary?0xff41bcd9:0xff244653;
        int fill=primary?0xff123e4f:focused?0xff193744:0xf010202b;
        g.fill(x+2,y,x+w-2,y+h,edge);g.fill(x,y+2,x+w,y+h-2,edge);
        g.fill(x+1,y+2,x+w-1,y+h-2,fill);g.fill(x+2,y+1,x+w-2,y+h-1,fill);
        if(primary)g.fill(x+3,y+h-3,x+w-3,y+h-2,0xff3a8093);
        if(hover>.01)g.fill(x+2,y+h-2,x+w-2,y+h-1,((int)(hover*180)<<24)|0x75dcf5);
        var font=Minecraft.getInstance().font;int inset=7;
        if(chip!=-1){g.fill(x+6,y+h/2-4,x+14,y+h/2+4,chip);inset=19;}
        String text=getMessage().getString();int available=w-inset-(dropdown?15:6);
        if(font.width(text)>available)text=font.plainSubstrByWidth(text,Math.max(0,available-font.width("…")))+"…";
        int tx=primary?x+(w-font.width(text))/2:x+inset;
        g.drawString(font,text,tx,y+(h-font.lineHeight)/2,active?0xffe8f3f7:0xffa3b4bf,false);
        if(dropdown&&active){int ax=x+w-10,ay=y+h/2;g.fill(ax-2,ay-1,ax+3,ay,0xff7ecce1);g.fill(ax-1,ay,ax+2,ay+1,0xff7ecce1);g.fill(ax,ay+1,ax+1,ay+2,0xff7ecce1);}
    }
}
