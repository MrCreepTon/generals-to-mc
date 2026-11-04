package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.util.*;

/** Minecraft owns setup, profile selection and match launch; original RTS HUD is untouched. */
public final class GeneralsSetupScreen extends CompatScreen {
    private final Screen parent;private final Section section;
    private enum Section {PLAY,WORLD,SETTINGS}
    private List<NativeProfiles.Profile> profiles=List.of();
    private NativeProfiles.Profile selected;
    private static Map<String,String> factionLabels=Map.of();
    private static boolean sourceFactionLabels;
    static void useProfile(NativeProfiles.Profile profile){StartAreaPreview.profile(profile);factionLabels=profile==null||profile.factionNames()==null?Map.of():profile.factionNames();sourceFactionLabels=profile!=null&&profile.modified();}
    private String error="";
    private boolean shownLocked,shownRunning,shownWorld;private int scroll,contentHeight,menuTop,menuBottom,menuWidth;private final List<AbstractWidget> body=new ArrayList<>();
    public GeneralsSetupScreen(Screen parent){this(parent,Section.PLAY);}
    private GeneralsSetupScreen(Screen parent,Section section){super(L.component(switch(section){case PLAY->L.s("Generals в Minecraft");case WORLD->L.s("Мир и маркеры");case SETTINGS->L.s("Настройки и помощь");}));this.parent=parent;this.section=section;}
    private Button button(String text,int x,int y,int w,Runnable action){var b=addRenderableWidget(MenuButton.styled(L.component(text),v->action.run()).bounds(x,y,w,20).build());body.add(b);return b;}
    @Override protected void init(){
        body.clear();
        try{var r=NativeProfiles.read();profiles=r.profiles();selected=NativeProfiles.active();}catch(Exception e){error=L.s("Не удалось прочитать подключённые игры. Откройте подключение заново.");}
        useProfile(selected);int x=width/2-155;boolean running=NativeRtsController.hasSession(),locked=running||NativeNetworkClient.lobby!=null,inWorld=minecraft.level!=null&&minecraft.player!=null;
        shownLocked=locked;shownRunning=running;shownWorld=inWorld;
        int y=switch(section){case PLAY->play(x,50-scroll,running,locked,inWorld);case WORLD->world(x,50-scroll,inWorld);case SETTINGS->settings(x,50-scroll,locked);};
        contentHeight=y+scroll-50;int cardHeight=Math.min(height-14,Math.max(180,contentHeight+115));menuTop=(height-cardHeight)/2;menuBottom=menuTop+cardHeight;menuWidth=Math.min(310,width-40);
        int oldScroll=scroll;scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-(cardHeight-115)));
        for(var widget:body){widget.setY(widget.getY()+menuTop+oldScroll-scroll);widget.visible=widget.getY()>=menuTop+48&&widget.getBottom()<=menuBottom-65;}
        if(section==Section.PLAY){
            addRenderableWidget(MenuButton.styled(L.component(L.s("Настройки и помощь")),b->minecraft.setScreen(new GeneralsSetupScreen(this,Section.SETTINGS))).bounds(x,menuBottom-29,152,20).build());
            addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(x+158,menuBottom-29,152,20).build());
        }else addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(x,menuBottom-29,310,20).build());
        for(var child:children())if(child instanceof AbstractWidget widget){int relative=widget.getX()-x;widget.setX((width-menuWidth)/2+relative*menuWidth/310);widget.setWidth(widget.getWidth()*menuWidth/310);}
    }
    private int play(int x,int y,boolean running,boolean locked,boolean inWorld){
        boolean stale=(NativeProfiles.needsRefresh(selected)||selected!=null&&!NativeProfiles.available(selected))&&!locked;
        if(stale){MenuButton.primary(button(NativeProfiles.available(selected)?L.s("Обновить данные игры"):L.s("Исправить подключение"),x,y,310,()->NativeProfilesScreen.refresh(this,selected)));y+=24;}
        if(running){
            MenuButton.primary(button(L.s("Вернуться к RTS"),x,y,310,NativeRtsController::resume));y+=24;
            MenuButton.danger(button(L.s("Завершить RTS…"),x,y,310,()->minecraft.setScreen(new MenuConfirmScreen(ok->{if(ok)NativeRtsController.stop();minecraft.setScreen(new GeneralsSetupScreen(parent));},L.component(L.s("Завершить матч?")),L.component(L.s("Юниты и постройки Generals пока не сохраняются. Мир Minecraft останется."))))));y+=24;
        }else if(selected!=null&&!stale){
            if(!inWorld){MenuButton.primary(button(L.s("Выбрать мир"),x,y,310,()->minecraft.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this))));y+=24;}
            else{
                MenuButton.primary(button(L.s("Схватка"),x,y,310,()->NativeLobbyScreen.open(this)));y+=24;
                MenuButton.access(button(L.s("Свободная игра"),x,y,310,()->minecraft.setScreen(new NativeLaunchScreen(this,selected))),minecraft.getSingleplayerServer()!=null&&!locked,locked?L.s("Сначала закройте комнату схватки."):L.s("Для своей армии на сервере откройте сетевую схватку."));y+=24;
            }
        }
        if(profiles.isEmpty()){MenuButton.primary(button(L.s("+ Подключить установленную игру"),x,y,310,()->minecraft.setScreen(new Connect(this)))).active=!locked;y+=24;}
        if(inWorld){button(L.s("Мир и маркеры"),x,y,310,()->minecraft.setScreen(new GeneralsSetupScreen(this,Section.WORLD)));y+=24;}
        return y;
    }
    private int world(int x,int y,boolean inWorld){
        if(!inWorld){MenuButton.primary(button(L.s("Выбрать мир"),x,y,310,()->minecraft.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this))));return y+24;}
        if(NativeNetworkClient.active()){button(L.s("Комната схватки"),x,y,310,()->minecraft.setScreen(new NativeLobbyScreen(this)));y+=24;}
        button(L.s("Найти маркеры"),x,y,310,()->minecraft.setScreen(new MarkerFinderScreen(this)));y+=24;
        button(L.s("Правила"),x,y,310,()->minecraft.setScreen(new WorldSettingsScreen(this)));y+=24;
        MenuButton.access(button(L.s("Debug меню"),x,y,310,()->minecraft.setScreen(new NativeDebugScreen(this))),NativeDebugScreen.available(),L.s("Debug доступен только в свободной игре Creative. В схватке он отключён."));return y+24;
    }
    private int settings(int x,int y,boolean locked){
        if(!profiles.isEmpty()){
            MenuButton.access(button(L.s("Игра: ")+(selected==null?L.s("выберите"):selected.name()),x,y,282,()->minecraft.setScreen(new NativeProfilesScreen(this))),!locked,L.s("Сначала завершите текущую RTS"));
            var add=button("+",x+286,y,24,()->minecraft.setScreen(new Connect(this)));MenuButton.access(add,!locked,L.s("Сначала завершите текущую RTS"));if(!locked)add.setTooltip(Tooltip.create(L.component(L.s("+ Добавить игру"))));y+=24;
        }else{MenuButton.primary(button(L.s("+ Подключить установленную игру"),x,y,310,()->minecraft.setScreen(new Connect(this)))).active=!locked;y+=24;}
        button(L.s("Управление"),x,y,310,()->minecraft.setScreen(new Help(this)));y+=24;
        button(L.s("Быстрый старт"),x,y,310,()->minecraft.setScreen(new GeneralsGuide(parent==null?this:parent)));return y+24;
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){int next=Math.clamp(scroll-(int)(v*24),0,Math.max(0,contentHeight-(menuBottom-menuTop-115)));if(next!=scroll){scroll=next;rebuildWidgets();}return v!=0;}
    static String friendly(String s){if(sourceFactionLabels&&factionLabels.containsKey(s)){String label=factionLabels.get(s);if(!label.isBlank())return switch(label){case "United States of America"->L.s("США");case "Peoples Republic of China"->L.s("Китай");case "Global Liberation Army"->L.s("ГЛА");case "Russian Federation"->L.s("Россия");case "European Continental Alliance"->L.s("Европейский альянс");default->label;};}return switch(s){
        case ""->L.s("Случайный генерал");case "FactionAmerica"->L.s("США");case "FactionChina"->L.s("Китай");case "FactionGLA"->L.s("ГЛА");
        case "FactionAmericaSuperWeaponGeneral"->L.s("США — генерал супероружия");
        case "FactionAmericaLaserGeneral"->L.s("США — лазерный генерал");
        case "FactionAmericaAirForceGeneral"->L.s("США — авиационный генерал");
        case "FactionChinaTankGeneral"->L.s("Китай — танковый генерал");
        case "FactionChinaNukeGeneral"->L.s("Китай — ядерный генерал");
        case "FactionChinaInfantryGeneral"->L.s("Китай — пехотный генерал");
        case "FactionGLAToxinGeneral"->L.s("ГЛА — токсинный генерал");
        case "FactionGLADemolitionGeneral"->L.s("ГЛА — генерал-подрывник");
        case "FactionGLAStealthGeneral"->L.s("ГЛА — скрытный генерал");
        default->factionLabels.getOrDefault(s,s.replaceFirst("^Faction","").replaceAll("([a-z])([A-Z])","$1 $2"));};}
    static String colorName(String value){return switch(value.toLowerCase(Locale.ROOT).replace("color:","")){case "gold"->L.s("Золотой");case "red"->L.s("Красный");case "blue"->L.s("Синий");case "skyblue"->L.s("Небесно-голубой");case "green"->L.s("Зелёный");case "orange"->L.s("Оранжевый");case "purple"->L.s("Фиолетовый");case "cyan"->L.s("Голубой");case "pink"->L.s("Розовый");case "yellow"->L.s("Жёлтый");default->value.replace("Color:","").replaceAll("([a-z])([A-Z])","$1 $2");};}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        UiPaint.panel(g,(width-menuWidth)/2-16,menuTop,menuWidth+32,menuBottom-menuTop);super.render(g,mx,my,delta);
        int left=(width-menuWidth)/2;g.drawString(font,title,left,menuTop+13,UiPaint.ACCENT,false);
        g.drawString(font,font.plainSubstrByWidth(selected==null?L.s("Сначала подключите Generals"):selected.name(),menuWidth),left,menuTop+28,UiPaint.MUTED,false);
        g.fill(left,menuTop+41,left+menuWidth,menuTop+42,UiPaint.BORDER);g.fill(left,menuBottom-59,left+menuWidth,menuBottom-58,UiPaint.BORDER);
        int bottom=menuBottom-53;
        String hint=section==Section.SETTINGS&&MenuScreens.supported()&&!MenuButton.MODERN?L.s("Modern UI добавляет сглаженный шрифт. Оформление уже включено."):section==Section.PLAY&&selected!=null&&minecraft.level!=null&&!NativeRtsController.hasSession()?L.s("Схватка — бой с NPC или друзьями. Свободная игра — армия без соперников."):"";
        int viewport=menuBottom-menuTop-115;
        if(contentHeight>viewport){int x=left+menuWidth+5,track=menuBottom-65-(menuTop+48),thumb=Math.max(12,track*viewport/contentHeight);int t=menuTop+48+(track-thumb)*scroll/Math.max(1,contentHeight-viewport);g.fill(x,menuTop+48,x+2,menuBottom-65,UiPaint.BORDER);g.fill(x,t,x+2,t+thumb,UiPaint.ACCENT);}
        g.enableScissor(left,bottom,left+menuWidth,menuBottom-32);g.drawWordWrap(font,L.component(error.isEmpty()?hint:error),left,bottom,menuWidth,error.isEmpty()?UiPaint.MUTED:0xffff8888);g.disableScissor();
    }
    @Override public void tick(){boolean running=NativeRtsController.hasSession(),locked=running||NativeNetworkClient.lobby!=null;if(running!=shownRunning||locked!=shownLocked||shownWorld!=(minecraft.level!=null&&minecraft.player!=null))rebuildWidgets();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    static final class Choices extends CompatScreen {
        final Screen parent;final List<String> labels;final List<Integer> colors;final java.util.function.IntConsumer choose;int page,rows;
        final List<Button> options=new ArrayList<>();
        Choices(Screen parent,String title,List<String> labels,List<Integer> colors,java.util.function.IntConsumer choose){super(L.component(title));this.parent=parent;this.labels=labels;this.colors=colors;this.choose=choose;}
        @Override protected void init(){options.clear();rows=Math.max(1,Math.min(8,(height-(parent instanceof GeneralsSetupScreen&&title.getString().equals(L.s("Выберите игру"))?140:110))/24));page=Math.clamp(page,0,Math.max(0,(labels.size()-1)/rows));int x=width/2-155;
            for(int i=page*rows;i<Math.min(labels.size(),(page+1)*rows);i++){int index=i;var b=addRenderableWidget(MenuButton.styled(L.component(labels.get(i)),w->{choose.accept(index);minecraft.setScreen(parent);}).bounds(x,48+(i-page*rows)*24,310,20).build());options.add(b);}
            if(labels.size()>rows){addRenderableWidget(MenuButton.styled(L.component("←"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x,height-64,80,20).build());addRenderableWidget(MenuButton.styled(L.component("→"),b->{page=Math.min((labels.size()-1)/rows,page+1);rebuildWidgets();}).bounds(x+230,height-64,80,20).build());}
            if(parent instanceof GeneralsSetupScreen&&title.getString().equals(L.s("Выберите игру")))addRenderableWidget(MenuButton.styled(L.component(L.s("+ Добавить игру")),b->minecraft.setScreen(new Connect(parent))).bounds(x,height-90,310,20).build());
            addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(x,height-35,310,20).build());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float d){MenuButton.panel(g,width,height);super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,20,0xfff0d68a);if(labels.size()>rows)g.drawCenteredString(font,(page+1)+" / "+((labels.size()+rows-1)/rows),width/2,height-59,0xffbbbbbb);for(int i=0;i<options.size()&&!colors.isEmpty();i++){var b=options.get(i);g.fill(b.getX()+9,b.getY()+5,b.getX()+19,b.getY()+15,0xff000000|colors.get(page*rows+i));}}
        @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
    static final class Help extends CompatScreen {
        final Screen parent;int scroll,contentHeight;Help(Screen p){super(L.component(L.s("Управление Generals")));parent=p;}
        @Override protected void init(){addRenderableWidget(MenuButton.styled(L.component(L.s("Изменить клавиши")),b->minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options))).bounds(width/2-155,height-58,310,20).build());addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(width/2-155,height-34,310,20).build());}
        @Override public void render(GuiGraphics g,int mx,int my,float d){MenuButton.panel(g,width,height,350);super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,16,0xfff0d68a);int w=Math.min(350,width-30),y=42-scroll;g.enableScissor(width/2-w/2,42,width/2+w/2,height-68);for(var row:RtsBindings.descriptions()){for(var line:font.split(row,w)){g.drawString(font,line,width/2-w/2,y,0xffdddddd);y+=font.lineHeight+3;}y+=4;}g.disableScissor();contentHeight=y+scroll-42;}
        @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.clamp(scroll-(int)(v*22),0,Math.max(0,contentHeight-(height-112)));return true;}
        @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
    static final class Connect extends CompatScreen {
        final Screen parent;final NativeProfiles.Profile editing;EditBox name,game,base,mod;Button cancel;boolean all,advanced;volatile boolean busy;volatile String status="";Thread worker;
        Connect(Screen p){this(p,null);}
        Connect(Screen p,NativeProfiles.Profile editing){super(L.component(L.s("Подключить Generals")));parent=p;this.editing=editing;advanced=editing!=null;all=editing!=null&&editing.allArchives();}
        EditBox field(String value,int y){var e=new MenuField(font,width/2-155,y,276,20,L.component(L.s("Папка")));e.setMaxLength(1024);e.setValue(value);addRenderableWidget(e);return e;}
        void browse(EditBox target,String label){try{String path=TinyFileDialogs.tinyfd_selectFolderDialog(label,NativeProfiles.browseStart(target.getValue()));if(path!=null){target.setValue(path);if(target==game){var pair=NativeProfiles.locate(path);if(!pair.zeroHour().isBlank())game.setValue(pair.zeroHour());if(!pair.generals().isBlank())base.setValue(pair.generals());if(editing==null)name.setValue(NativeProfiles.suggestName(game.getValue()));}}}catch(RuntimeException error){status=L.s("Не удалось открыть выбор папки. Вставьте путь в поле.");}}
        @Override protected void init(){
            String oldName=name==null?editing==null?"Zero Hour":editing.name():name.getValue(),oldGame=game==null?editing==null?NativeProfiles.discover().stream().findFirst().orElse(""):editing.game():game.getValue();
            String oldBase=base==null?editing==null?NativeProfiles.suggestBase(oldGame):editing.base():base.getValue(),oldMod=mod==null?editing==null?"":editing.mod():mod.getValue();
            name=field(oldName,43);game=field(oldGame,72);base=field(oldBase,101);mod=field(oldMod,130);
            for(var e:List.of(game,base,mod)){String label=e==game?"Zero Hour":e==base?"Generals":L.s("Мод");addRenderableWidget(MenuButton.styled(L.component("…"),b->browse(e,label)).bounds(width/2+125,e.getY(),30,20).build());}
            addRenderableWidget(MenuButton.styled(L.component(all?L.s("Все BIG из папки игры: да"):L.s("Все BIG из папки игры: нет")),b->{all=!all;if(b instanceof MenuButton m)m.selected=all;b.setMessage(L.component(all?L.s("Все BIG из папки игры: да"):L.s("Все BIG из папки игры: нет")));}).toggle(all).bounds(width/2-155,155,310,20).build());
            addRenderableWidget(MenuButton.styled(L.component(L.s("Подключить и проверить")),b->connect()).primary().bounds(width/2-155,height-35,200,20).build());
            cancel=addRenderableWidget(MenuButton.styled(L.component(busy?L.s("Отменить"):L.s("Назад")),b->onClose()).quiet().bounds(width/2+53,height-35,102,20).build());
            if(!advanced){
                name.visible=false;mod.visible=false;
                for(var child:children())if(child instanceof AbstractWidget widget&&widget!=game&&widget!=base&&widget!=cancel&&widget.getY()!=game.getY()&&widget.getY()!=base.getY()&&widget.getY()!=height-35)widget.visible=false;
                addRenderableWidget(MenuButton.styled(L.component(L.s("Параметры игры и модов…")),b->{advanced=true;rebuildWidgets();}).bounds(width/2-155,height-64,310,20).build());
            }else addRenderableWidget(MenuButton.styled(L.component("←"),b->{advanced=false;rebuildWidgets();}).bounds(width/2-155,15,22,20).build());
            editable();
        }
        private void editable(){for(var widget:children())if(widget instanceof AbstractWidget w)w.active=!busy;if(cancel!=null){cancel.active=true;cancel.setMessage(L.component(busy?L.s("Отменить"):L.s("Назад")));}}
        void connect(){if(busy)return;var pair=NativeProfiles.locate(game.getValue());if(!pair.zeroHour().isBlank())game.setValue(pair.zeroHour());if(base.getValue().isBlank()&&!pair.generals().isBlank())base.setValue(pair.generals());busy=true;editable();String g=game.getValue(),n=editing==null&&name.getValue().equals("Zero Hour")?NativeProfiles.suggestName(g):name.getValue(),b=base.getValue(),m=mod.getValue();boolean include=all;
            worker=Thread.ofVirtual().name("zhmc-connect-game").start(()->{try{if(editing==null)NativeProfiles.connect(n,g,b,m,include,s->status=s);else NativeProfiles.update(editing.id(),n,g,b,m,include,s->status=s);minecraft.execute(()->{if(MenuScreens.active(minecraft.screen)==this)minecraft.setScreen(parent);});}catch(Exception e){status=e.getMessage()==null?e.toString():e.getMessage();}finally{busy=false;minecraft.execute(this::editable);}});
        }
        @Override public void render(GuiGraphics g,int mx,int my,float delta){MenuButton.panel(g,width,height);super.render(g,mx,my,delta);g.drawCenteredString(font,advanced?L.component(L.s("Параметры игры и модов")):title,width/2,16,0xfff0d68a);int x=width/2-155;
            if(advanced){g.drawString(font,L.s("Название игры"),x,32,0xffbbbbbb);g.drawString(font,L.s("Папка Zero Hour"),x,61,0xffbbbbbb);g.drawString(font,L.s("Папка базовой Generals"),x,90,0xffbbbbbb);g.drawString(font,L.s("Папка мода — необязательно"),x,119,0xffbbbbbb);}
            else{g.drawString(font,L.s("Папка Zero Hour"),x,61,0xffbbbbbb);g.drawString(font,L.s("Папка базовой Generals"),x,90,0xffbbbbbb);}
            int top=advanced?181:132,bottom=advanced?height-43:height-77;
            g.enableScissor(x,top,x+310,Math.max(top+1,bottom));g.drawWordWrap(font,L.component(status.isEmpty()?(advanced?L.s("Для модификации укажите её папку. Оригинальные файлы не изменяются."):L.s("Выберите папку игры или сборки. Generals и Zero Hour ищутся рядом автоматически.")):status),x,top,310,0xffdddddd);g.disableScissor();
            if(my>=top&&my<bottom&&!status.isEmpty())g.setTooltipForNextFrame(font,font.split(L.component(status),300),mx,my);
        }
        @Override public void onClose(){if(busy){status=L.s("Отмена подключения…");if(worker!=null)worker.interrupt();}else minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
}
