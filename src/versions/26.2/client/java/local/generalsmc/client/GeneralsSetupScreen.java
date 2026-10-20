package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Minecraft owns setup, profile selection and match launch; original RTS HUD is untouched. */
public final class GeneralsSetupScreen extends CompatScreen {
    private final Screen parent;
    private List<NativeProfiles.Profile> profiles=List.of();
    private NativeProfiles.Profile selected;
    private static Map<String,String> factionLabels=Map.of();
    private static boolean sourceFactionLabels;
    static void useProfile(NativeProfiles.Profile profile){factionLabels=profile==null||profile.factionNames()==null?Map.of():profile.factionNames();sourceFactionLabels=profile!=null&&profile.modified();}
    private String error="";
    private int scroll,contentHeight;private final List<AbstractWidget> body=new ArrayList<>();
    public GeneralsSetupScreen(Screen parent){super(L.component(L.s("Generals в Minecraft")));this.parent=parent;}
    private Button button(String text,int x,int y,int w,Runnable action){var b=addRenderableWidget(Button.builder(L.component(text),v->action.run()).bounds(x,y,w,20).build());body.add(b);return b;}
    @Override protected void init(){
        body.clear();
        try{var r=NativeProfiles.read();profiles=r.profiles();selected=NativeProfiles.active();}catch(Exception e){error=L.s("Не удалось прочитать подключённые игры. Откройте подключение заново.");}
        useProfile(selected);int x=width/2-155,y=50-scroll;boolean running=NativeRtsController.hasSession(),profileLocked=running||NativeNetworkClient.lobby!=null;
        boolean stale=(NativeProfiles.needsRefresh(selected)||selected!=null&&!NativeProfiles.available(selected))&&!profileLocked;
        if(stale){button(NativeProfiles.available(selected)?L.s("Обновить данные игры"):L.s("Исправить подключение"),x,y,310,()->NativeProfilesScreen.refresh(this,selected));y+=24;}
        if(selected!=null||running){
            var play=button(running?L.s("Вернуться к RTS"):L.s("Начать RTS здесь"),x,y,310,()->{minecraft.gui.setScreen(null);if(running){if(NativeNetworkClient.active())NativeNetworkClient.resume();else if(!NativeRtsController.controlling())NativeRtsController.toggle();}else minecraft.gui.setScreen(new NativeLaunchScreen(this,selected));});
            play.active=!stale&&minecraft.level!=null&&(running||minecraft.getSingleplayerServer()!=null)&&(!profileLocked||running);if(!play.active)play.setTooltip(Tooltip.create(L.component(stale?L.s("Обновите данные игры, чтобы загрузить её фракции."):minecraft.level==null?L.s("Войдите в одиночный мир. Затем: меню паузы → Generals."):L.s("Для своей армии на сервере откройте сетевую схватку."))));y+=24;
            if(running){button(L.s("Завершить RTS…"),x,y,310,()->minecraft.gui.setScreen(new ConfirmScreen(ok->{if(ok)NativeRtsController.stop();minecraft.gui.setScreen(new GeneralsSetupScreen(parent));},L.component(L.s("Завершить матч?")),L.component(L.s("Юниты и постройки Generals пока не сохраняются. Мир Minecraft останется.")))));y+=24;}
            else{button(L.s("Сетевая схватка / NPC"),x,y,310,()->minecraft.gui.setScreen(new NativeLobbyScreen(this))).active=!stale&&minecraft.level!=null;y+=24;}
            if(NativeNetworkClient.active()){button(L.s("Комната схватки"),x,y,310,()->minecraft.gui.setScreen(new NativeLobbyScreen(this)));y+=24;}
        }
        if(minecraft.level!=null){button(L.s("Мир и взаимодействия"),x,y,310,()->minecraft.gui.setScreen(new WorldSettingsScreen(this)));y+=28;}
        if(minecraft.level!=null){button(L.s("Найти маркеры"),x,y,310,()->minecraft.gui.setScreen(new MarkerFinderScreen(this)));y+=24;}
        if(!profiles.isEmpty()){button(L.s("Игра: ")+(selected==null?L.s("выберите"):selected.name()),x,y,282,()->minecraft.gui.setScreen(new NativeProfilesScreen(this))).active=!profileLocked;var addGame=button("+",x+286,y,24,()->minecraft.gui.setScreen(new Connect(this)));addGame.active=!profileLocked;addGame.setTooltip(Tooltip.create(L.component(L.s("+ Добавить игру"))));y+=24;}
        if(profiles.isEmpty())button(L.s("+ Подключить установленную игру"),x,y,310,()->minecraft.gui.setScreen(new Connect(this))).active=!profileLocked;y+=24;
        if(NativeDebugScreen.available()){button(L.s("Инструменты творчества"),x,y,310,()->minecraft.gui.setScreen(new NativeDebugScreen(this)));y+=24;}
        contentHeight=y+scroll-50;for(var widget:body)widget.visible=widget.getY()>=48&&widget.getY()+20<=height-65;
        addRenderableWidget(Button.builder(L.component(L.s("Управление")),b->minecraft.gui.setScreen(new Help(this))).bounds(x,height-29,100,20).build());
        addRenderableWidget(Button.builder(L.component(L.s("Быстрый старт")),b->minecraft.gui.setScreen(new GeneralsGuide(this))).bounds(x+107,height-29,100,20).build());
        addRenderableWidget(Button.builder(L.component(L.s("Готово")),b->onClose()).bounds(x+214,height-29,96,20).build());
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.clamp(scroll-(int)(v*24),0,Math.max(0,contentHeight-(height-115)));rebuildWidgets();return true;}
    static String friendly(String s){if(sourceFactionLabels&&factionLabels.containsKey(s)){String label=factionLabels.get(s);if(!label.isBlank())return switch(label){case "United States of America"->L.s("США");case "Peoples Republic of China"->L.s("Китай");case "Global Liberation Army"->L.s("ГЛА");case "Russian Federation"->L.s("Россия");case "European Continental Alliance"->L.s("Европейский альянс");default->label;};}return switch(s){
        case ""->L.s("Случайная сторона");case "FactionAmerica"->L.s("США");case "FactionChina"->L.s("Китай");case "FactionGLA"->L.s("ГЛА");
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
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);g.centeredText(font,title,width/2,16,0xfff0d68a);
        
        int bottom=height-53;
        String hint="";
        if(contentHeight>height-115){int x=width/2+159;g.fill(x,48,x+2,height-65,0xff444444);int t=48+(int)((height-123)*(double)scroll/Math.max(1,contentHeight-(height-115)));g.fill(x,t,x+2,t+8,0xfff0d68a);}
        g.enableScissor(0,bottom,width,height-32);g.textWithWordWrap(font,L.component(error.isEmpty()?hint:error),width/2-155,bottom,310,error.isEmpty()?0xffbbbbbb:0xffff8888);g.disableScissor();
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    static final class Choices extends CompatScreen {
        final Screen parent;final List<String> labels;final List<Integer> colors;final java.util.function.IntConsumer choose;int page,rows;
        final List<Button> options=new ArrayList<>();
        Choices(Screen parent,String title,List<String> labels,List<Integer> colors,java.util.function.IntConsumer choose){super(L.component(title));this.parent=parent;this.labels=labels;this.colors=colors;this.choose=choose;}
        @Override protected void init(){options.clear();rows=Math.max(1,Math.min(8,(height-(parent instanceof GeneralsSetupScreen&&title.getString().equals(L.s("Выберите игру"))?140:110))/24));page=Math.min(page,(labels.size()-1)/rows);int x=width/2-155;
            for(int i=page*rows;i<Math.min(labels.size(),(page+1)*rows);i++){int index=i;var b=addRenderableWidget(Button.builder(L.component(labels.get(i)),w->{choose.accept(index);minecraft.gui.setScreen(parent);}).bounds(x,48+(i-page*rows)*24,310,20).build());options.add(b);}
            if(labels.size()>rows){addRenderableWidget(Button.builder(L.component("←"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x,height-64,80,20).build());addRenderableWidget(Button.builder(L.component("→"),b->{page=Math.min((labels.size()-1)/rows,page+1);rebuildWidgets();}).bounds(x+230,height-64,80,20).build());}
            if(parent instanceof GeneralsSetupScreen&&title.getString().equals(L.s("Выберите игру")))addRenderableWidget(Button.builder(L.component(L.s("+ Добавить игру")),b->minecraft.gui.setScreen(new Connect(parent))).bounds(x,height-90,310,20).build());
            addRenderableWidget(Button.builder(L.component(L.s("Назад")),b->onClose()).bounds(x,height-35,310,20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){super.extractRenderState(g,mx,my,d);g.centeredText(font,title,width/2,20,0xfff0d68a);if(labels.size()>rows)g.centeredText(font,(page+1)+" / "+((labels.size()+rows-1)/rows),width/2,height-59,0xffbbbbbb);for(int i=0;i<options.size()&&!colors.isEmpty();i++){var b=options.get(i);g.fill(b.getX()+9,b.getY()+5,b.getX()+19,b.getY()+15,0xff000000|colors.get(page*rows+i));}}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
    static final class Help extends CompatScreen {
        final Screen parent;int scroll,contentHeight;Help(Screen p){super(L.component(L.s("Управление Generals")));parent=p;}
        @Override protected void init(){addRenderableWidget(Button.builder(L.component(L.s("Изменить клавиши")),b->minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options))).bounds(width/2-155,height-58,310,20).build());addRenderableWidget(Button.builder(L.component(L.s("Назад")),b->onClose()).bounds(width/2-155,height-34,310,20).build());}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){super.extractRenderState(g,mx,my,d);g.centeredText(font,title,width/2,16,0xfff0d68a);int w=Math.min(350,width-30),y=42-scroll;g.enableScissor(width/2-w/2,42,width/2+w/2,height-68);for(var row:RtsBindings.descriptions()){for(var line:font.split(row,w)){g.text(font,line,width/2-w/2,y,0xffdddddd);y+=font.lineHeight+3;}y+=4;}g.disableScissor();contentHeight=y+scroll-42;}
        @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.clamp(scroll-(int)(v*22),0,Math.max(0,contentHeight-(height-112)));return true;}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
    static final class Connect extends CompatScreen {
        final Screen parent;final NativeProfiles.Profile editing;EditBox name,game,base,mod;Button cancel;boolean all,advanced;volatile boolean busy;volatile String status="";Thread worker;
        Connect(Screen p){this(p,null);}
        Connect(Screen p,NativeProfiles.Profile editing){super(L.component(L.s("Подключить Generals")));parent=p;this.editing=editing;advanced=editing!=null;all=editing!=null&&editing.allArchives();}
        EditBox field(String value,int y){var e=new EditBox(font,width/2-155,y,276,20,L.component(L.s("Папка")));e.setMaxLength(1024);e.setValue(value);addRenderableWidget(e);return e;}
        void browse(EditBox target,String label){PlatformDialogs.folder(label,target.getValue(),path->{target.setValue(path);if(target==game){base.setValue(NativeProfiles.suggestBase(path));if(editing==null)name.setValue(NativeProfiles.suggestName(path));}});}
        @Override protected void init(){
            String oldName=name==null?editing==null?"Zero Hour":editing.name():name.getValue(),oldGame=game==null?editing==null?NativeProfiles.discover().stream().findFirst().orElse(""):editing.game():game.getValue();
            String oldBase=base==null?editing==null?NativeProfiles.suggestBase(oldGame):editing.base():base.getValue(),oldMod=mod==null?editing==null?"":editing.mod():mod.getValue();
            name=field(oldName,43);game=field(oldGame,72);base=field(oldBase,101);mod=field(oldMod,130);
            for(var e:List.of(game,base,mod)){String label=e==game?"Zero Hour":e==base?"Generals":L.s("Мод");addRenderableWidget(Button.builder(L.component("…"),b->browse(e,label)).bounds(width/2+125,e.getY(),30,20).build());}
            addRenderableWidget(Button.builder(L.component(all?L.s("Все BIG из папки игры: да"):L.s("Все BIG из папки игры: нет")),b->{all=!all;b.setMessage(L.component(all?L.s("Все BIG из папки игры: да"):L.s("Все BIG из папки игры: нет")));}).bounds(width/2-155,155,310,20).build());
            addRenderableWidget(Button.builder(L.component(L.s("Подключить и проверить")),b->connect()).bounds(width/2-155,height-35,200,20).build());
            cancel=addRenderableWidget(Button.builder(L.component(busy?L.s("Отменить"):L.s("Назад")),b->onClose()).bounds(width/2+53,height-35,102,20).build());
            if(!advanced){
                name.visible=false;base.visible=false;mod.visible=false;
                for(var child:children())if(child instanceof AbstractWidget widget&&widget!=game&&widget!=cancel&&widget.getY()!=game.getY()&&widget.getY()!=height-35)widget.visible=false;
                addRenderableWidget(Button.builder(L.component(L.s("Параметры игры и модов…")),b->{advanced=true;rebuildWidgets();}).bounds(width/2-155,height-64,310,20).build());
            }else addRenderableWidget(Button.builder(L.component("←"),b->{advanced=false;rebuildWidgets();}).bounds(width/2-155,15,22,20).build());
            editable();
        }
        private void editable(){for(var widget:children())if(widget instanceof AbstractWidget w)w.active=!busy;if(cancel!=null){cancel.active=true;cancel.setMessage(L.component(busy?L.s("Отменить"):L.s("Назад")));}}
        void connect(){if(busy)return;busy=true;editable();String g=game.getValue(),n=editing==null&&name.getValue().equals("Zero Hour")?NativeProfiles.suggestName(g):name.getValue(),b=base.getValue(),m=mod.getValue();boolean include=all;
            worker=Thread.ofVirtual().name("zhmc-connect-game").start(()->{try{if(editing==null)NativeProfiles.connect(n,g,b,m,include,s->status=s);else NativeProfiles.update(editing.id(),n,g,b,m,include,s->status=s);minecraft.execute(()->{if(minecraft.gui.screen()==this)minecraft.gui.setScreen(parent);});}catch(Exception e){status=e.getMessage()==null?e.toString():e.getMessage();}finally{busy=false;minecraft.execute(this::editable);}});
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);g.centeredText(font,advanced?L.component(L.s("Параметры игры и модов")):title,width/2,16,0xfff0d68a);int x=width/2-155;
            if(advanced){g.text(font,L.s("Название игры"),x,32,0xffbbbbbb);g.text(font,L.s("Папка Zero Hour"),x,61,0xffbbbbbb);g.text(font,L.s("Папка базовой Generals"),x,90,0xffbbbbbb);g.text(font,L.s("Папка мода — необязательно"),x,119,0xffbbbbbb);}
            else{g.textWithWordWrap(font,L.component(L.s("Выберите установленную Zero Hour. Папка Generals определяется автоматически.")),x,40,310,0xffdddddd);}
            int top=advanced?181:109,bottom=advanced?height-43:height-77;
            g.enableScissor(x,top,x+310,Math.max(top+1,bottom));g.textWithWordWrap(font,L.component(status.isEmpty()?(advanced?L.s("Для модификации укажите её папку. Оригинальные файлы не изменяются."):L.s("Игра найдена? Нажмите «Подключить и проверить». Все компоненты движка уже включены в мод.")):status),x,top,310,0xffdddddd);g.disableScissor();
            if(my>=top&&my<bottom&&!status.isEmpty())g.setTooltipForNextFrame(font,font.split(L.component(status),300),mx,my);
        }
        @Override public void onClose(){if(busy){status=L.s("Отмена подключения…");if(worker!=null)worker.interrupt();}else minecraft.gui.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
}
