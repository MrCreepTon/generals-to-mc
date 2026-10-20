package local.generalsmc.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;

/** A recoverable native/mod error should not send a new player looking for a terminal. */
final class GeneralsFailureScreen extends CompatScreen {
    private final String detail;private final Path logs;private int scroll;
    GeneralsFailureScreen(String detail,Path logs){super(L.component(L.s("Не удалось запустить Generals")));this.detail=detail==null?L.s("Игра завершила работу"):detail;this.logs=logs;}
    @Override protected void init(){int x=width/2-155,y=height-78;addRenderableWidget(Button.builder(L.component(L.s("Выбрать другую игру / профиль")),b->minecraft.gui.setScreen(new GeneralsSetupScreen(null))).bounds(x,y,310,20).build());addRenderableWidget(Button.builder(L.component(L.s("Открыть журналы")),b->PlatformDialogs.open(logs.toUri())).bounds(x,y+25,151,20).build());addRenderableWidget(Button.builder(L.component(L.s("В Minecraft")),b->onClose()).bounds(x+159,y+25,151,20).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,22,0xffffbb88);g.textWithWordWrap(font,L.component(L.s("Minecraft продолжает работать. Проверьте подключённую игру или выберите другой профиль.")),width/2-155,50,310,0xffdddddd);g.enableScissor(width/2-155,83,width/2+155,height-87);g.textWithWordWrap(font,L.component(detail),width/2-155,85-scroll,310,0xffff9999);g.disableScissor();}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.clamp(scroll-(int)(v*16),0,Math.max(0,font.split(L.component(detail),310).size()*font.lineHeight-(height-174)));return true;}
    @Override public void onClose(){minecraft.gui.setScreen(null);}@Override public boolean isPauseScreen(){return false;}
}
