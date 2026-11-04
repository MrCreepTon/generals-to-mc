package local.generalsmc.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import java.util.function.Consumer;

/** Mod-owned confirmations share the theme; Minecraft's own dialogs remain untouched. */
class MenuConfirmScreen extends Screen {
    private final Consumer<Boolean> answer;private final Component detail;
    private int left,top,cardWidth,cardHeight,scroll,textHeight;private boolean answered;
    MenuConfirmScreen(Consumer<Boolean> answer,Component title,Component detail){super(title);this.answer=answer;this.detail=detail;}
    private void answer(boolean value){if(!answered){answered=true;answer.accept(value);}}
    @Override protected void init(){
        cardWidth=Math.min(360,width-24);int textWidth=cardWidth-32;textHeight=font.split(detail,textWidth).size()*font.lineHeight;
        cardHeight=Math.min(height-24,Math.max(122,textHeight+90));left=(width-cardWidth)/2;top=(height-cardHeight)/2;
        int buttonWidth=(cardWidth-40)/2,y=top+cardHeight-34;
        addRenderableWidget(MenuButton.styled(CommonComponents.GUI_YES,b->answer(true)).danger().bounds(left+16,y,buttonWidth,22).build());
        var cancel=addRenderableWidget(MenuButton.styled(CommonComponents.GUI_NO,b->answer(false)).bounds(left+24+buttonWidth,y,buttonWidth,22).build());setFocused(cancel);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        super.render(g,mx,my,delta);
        // Draw the card before its controls; Screen renders the world background separately.
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){
        super.renderBackground(g,mx,my,delta);UiPaint.panel(g,left,top,cardWidth,cardHeight);
        g.drawCenteredString(font,title,width/2,top+14,UiPaint.ACCENT);g.fill(left+16,top+32,left+cardWidth-16,top+33,UiPaint.BORDER);
        g.enableScissor(left+16,top+42,left+cardWidth-16,top+cardHeight-47);g.drawWordWrap(font,detail,left+16,top+43-scroll,cardWidth-32,UiPaint.TEXT);g.disableScissor();
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.clamp(scroll-(int)(v*font.lineHeight*2),0,Math.max(0,textHeight-(cardHeight-90)));return v!=0;}
    @Override public void onClose(){answer(false);}
    @Override public boolean isPauseScreen(){return false;}
}
