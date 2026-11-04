package local.generalsmc.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Native text editing and selection, with the same focus treatment as menu buttons. */
final class MenuField extends EditBox {
    MenuField(Font font,int x,int y,int w,int h,Component label){super(font,x,y,w,h,label);setTextColor(UiPaint.TEXT);setTextColorUneditable(UiPaint.MUTED);}
    @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta){
        super.renderWidget(g,mx,my,delta);
        if(!isBordered())return;
        int x=getX(),y=getY(),w=getWidth(),h=getHeight(),edge=active&&isFocused()?UiPaint.FOCUS:UiPaint.BORDER,n=active&&isFocused()?2:1;
        g.fill(x,y,x+w,y+n,edge);g.fill(x,y+h-n,x+w,y+h,edge);g.fill(x,y+n,x+n,y+h-n,edge);g.fill(x+w-n,y+n,x+w,y+h-n,edge);
    }
}
