package local.generalsmc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
final class NativeDebugScreen extends CompatScreen {
 final Screen parent;String feedback="";Button fogButton;
 NativeDebugScreen(Screen parent){super(L.component(L.s("Debug меню")));this.parent=parent;}
 static boolean available(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.player.isCreative()&&mc.getSingleplayerServer()!=null&&!NativeNetworkClient.active()&&NativeNetworkClient.lobby==null&&!local.generalsmc.nativebridge.NativeNetworkServer.busy();}
 String blocked(){return !available()?L.s("Debug доступен только в свободной игре Creative. В схватке он отключён."):L.s("Сначала запустите свободную игру.");}
 Button action(String title,int row,java.util.function.IntSupplier run){var b=addRenderableWidget(MenuButton.styled(L.component(title),button->{if(available())feedback=run.getAsInt()>0?L.s("Выполнено"):L.s("Не удалось выполнить действие");}).bounds(width/2-155,48+row*20,310,18).build());b.active=available()&&NativeRtsController.hasLocalSession();if(!b.active)b.setTooltip(Tooltip.create(L.component(blocked())));return b;}
 String fogText(){var v=NativeWorldView.current;return L.s("Туман войны: ")+(v!=null&&v.fog.enabled()?L.s("Включено"):L.s("Выключено"));}
 @Override protected void init(){action(L.s("Добавить 10 000"),0,()->NativeRtsController.debug("DEBUG_MONEY 10000"));action(L.s("Подготовить супероружие"),1,()->NativeRtsController.debug("DEBUG_COOLDOWN 0"));action(L.s("Восстановить выбранные юниты"),2,()->NativeRtsController.debug("DEBUG_HEAL"));fogButton=action(fogText(),3,()->{var v=NativeWorldView.current;v.fog.enabled(!v.fog.enabled());rebuildWidgets();return 1;});if(fogButton instanceof MenuButton m){m.toggle=true;m.selected=NativeWorldView.current!=null&&NativeWorldView.current.fog.enabled();}action(L.s("Повысить ранг генерала"),4,()->NativeRtsController.debug("DEBUG_RANK"));action(L.s("Открыть все повышения"),5,()->NativeRtsController.debug("DEBUG_SCIENCES"));action(L.s("Следующая музыка"),6,()->NativeRtsController.debug("DEBUG_MUSIC"));addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(width/2-155,height-35,310,20).build());}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){MenuButton.panel(g,width,height);super.extractRenderState(g,mx,my,d);g.centeredText(font,title,width/2,15,UiPaint.ACCENT);g.centeredText(font,L.s("Только свободная игра Creative"),width/2,32,UiPaint.MUTED);g.centeredText(font,feedback,width/2,height-50,0xffbbddaa);}
 @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
