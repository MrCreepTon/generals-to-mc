package local.generalsmc.client;

import local.generalsmc.nativebridge.NativeProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import java.nio.file.*;
import java.util.Properties;

/** Optional four-step introduction, also available from the Generals menu. */
final class GeneralsGuide extends CompatScreen {
    final Screen parent;int step;
    GeneralsGuide(Screen parent){super(L.component(L.s("Быстрый старт")));this.parent=parent;}
    static void open(Screen parent){var setup=new GeneralsSetupScreen(parent);Minecraft.getInstance().setScreen(seen()||NativeRtsController.hasSession()?setup:new GeneralsGuide(setup));}
    static boolean seen(){try{var p=new Properties();try(var in=Files.newInputStream(NativeProfiles.root().resolve("ui.properties"))){p.load(in);}return Boolean.parseBoolean(p.getProperty("guideSeen","false"));}catch(Exception ignored){return false;}}
    static void remember(){try{var root=NativeProfiles.root();Files.createDirectories(root);var file=root.resolve("ui.properties");var temp=Files.createTempFile(root,"ui-",".tmp");try{Files.writeString(temp,"guideSeen=true\n");Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}}catch(Exception e){local.generalsmc.GeneralsMod.LOG.debug("Guide preference could not be saved",e);}}
    @Override protected void init(){int x=width/2-155;
        String action=switch(step){case 0->L.s("Подключить игру");case 1->minecraft.level==null?L.s("Выбрать мир"):L.s("Открыть меню");case 2->L.s("Открыть схватку");default->L.s("Найти маркеры");};
        var button=addRenderableWidget(Button.builder(L.component(action),b->{
            if(step==0)minecraft.setScreen(new GeneralsSetupScreen.Connect(this));
            else if(step==1){remember();minecraft.setScreen(minecraft.level==null?new SelectWorldScreen(this):parent);}
            else if(step==2){remember();minecraft.setScreen(new NativeLobbyScreen(parent));}
            else{remember();minecraft.setScreen(new MarkerFinderScreen(parent));}
        }).bounds(x,height-87,310,20).build());
        button.active=step<2||minecraft.level!=null;try{if(step==2)button.active&=NativeProfiles.available(NativeProfiles.active());}catch(Exception e){button.active=false;}
        addRenderableWidget(Button.builder(L.component(L.s("Назад")),b->{step--;rebuildWidgets();}).bounds(x,height-59,100,20).build()).active=step>0;
        addRenderableWidget(Button.builder(L.component(step==3?L.s("Готово"):L.s("Далее")),b->{if(step==3)onClose();else{step++;rebuildWidgets();}}).bounds(x+108,height-59,202,20).build());
        addRenderableWidget(Button.builder(L.component(L.s("Закрыть гайд")),b->onClose()).bounds(x,height-33,310,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){super.extractRenderState(g,mx,my,d);int x=width/2-155;g.centeredText(font,title,width/2,16,0xfff0d68a);g.centeredText(font,(step+1)+" / 4",width/2,36,0xffbbbbbb);
        String heading=switch(step){case 0->L.s("1. Подключите Generals");case 1->L.s("2. Откройте мир");case 2->L.s("3. Начните схватку");default->L.s("4. Настройте карту");};
        String text=switch(step){case 0->L.s("Выберите папку Zero Hour или установленного мода. Файлы игры останутся на месте.");case 1->L.s("Войдите в одиночный мир или на сервер с этим модом. Кнопка Generals есть в меню паузы.");case 2->L.s("Добавьте NPC или пригласите игроков. Выберите сторону и цвет, затем начните схватку.");default->L.s("Маркеры стартов, складов и нефти находятся во вкладке Generals творческого инвентаря. После старта %s переключает Minecraft и RTS.",GeneralsClient.nativeToggle.getTranslatedKeyMessage().getString());};
        g.text(font,heading,x,60,0xfff0d68a);g.enableScissor(x,77,x+310,height-94);g.textWithWordWrap(font,L.component(text),x,78,310,0xffeeeeee);g.disableScissor();
    }
    @Override public void onClose(){remember();minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
