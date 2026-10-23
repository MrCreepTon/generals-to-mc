package local.generalsmc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
final class NativeDebugScreen extends CompatScreen {
    final Screen parent;String feedback="";
    NativeDebugScreen(Screen parent){super(L.component(L.s("Инструменты творчества")));this.parent=parent;}
    static boolean available(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.player.isCreative()&&mc.getSingleplayerServer()!=null&&!NativeNetworkClient.active()&&NativeNetworkClient.lobby==null;}
    void action(String title,int y,java.util.function.IntSupplier run){var b=addRenderableWidget(MenuButton.styled(L.component(title),button->{if(available())feedback=run.getAsInt()>0?L.s("Выполнено"):L.s("Не удалось выполнить действие");}).bounds(width/2-155,y,310,20).build());b.active=available()&&NativeRtsController.hasLocalSession();}
    @Override protected void init(){action(L.s("Добавить 10 000"),48,()->NativeRtsController.debug("DEBUG_MONEY 10000"));action(L.s("Подготовить супероружие"),72,()->NativeRtsController.debug("DEBUG_COOLDOWN 0"));action(L.s("Восстановить выбранные юниты"),96,()->NativeRtsController.debug("DEBUG_HEAL"));action(L.s("Туман войны: переключить"),120,()->{var v=NativeWorldView.current;v.fog.enabled(!v.fog.enabled());return 1;});action(L.s("Следующая музыка"),144,()->NativeRtsController.debug("DEBUG_MUSIC"));addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).bounds(width/2-155,height-35,310,20).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){MenuButton.panel(g,width,height);super.extractRenderState(g,mx,my,d);g.centeredText(font,title,width/2,15,0xfff0d68a);g.centeredText(font,L.s("Только локальный творческий мир"),width/2,32,0xffbbbbbb);g.centeredText(font,feedback,width/2,height-52,0xffbbddaa);}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
