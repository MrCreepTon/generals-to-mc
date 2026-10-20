package local.generalsmc.client;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Shared roster: identities and choices are owned by the Minecraft server. */
public final class NativeLobbyScreen extends Screen {
    private final Screen parent;private NativeNetworkServer.Lobby shown;private NativeProfiles.Profile profile;
    private int scroll,contentHeight;private final List<AbstractWidget> rows=new ArrayList<>();
    public NativeLobbyScreen(Screen parent){super(L.component(L.s("Схватка Generals")));this.parent=parent;}
    private Button button(String text,int x,int y,int w,Runnable run){return addRenderableWidget(Button.builder(L.component(text),b->run.run()).bounds(x,y,w,20).build());}
    private void cycle(String text,int x,int y,int w,java.util.function.IntConsumer run,boolean enabled){var b=addRenderableWidget(new CycleButton(text,x,y,w,run));b.active=enabled;rows.add(b);}
    static String team(int value){return value==-2?L.s("Случайная"):value==-1?L.s("Без команды"):Integer.toString(value+1);}
    static int nextTeam(int value,int direction){return Math.floorMod(value+2+direction,10)-2;}
    static String position(WorldSiteStore.Start s){return s.pos().getX()+", "+s.pos().getY()+", "+s.pos().getZ();}
    private String color(int id){if(id<0)return L.s("Случайный");return profile.colors().stream().filter(c->c.id()==id).map(c->GeneralsSetupScreen.colorName(c.label())).findFirst().orElse("?");}
    private void choose(String faction,int color,int team,int start){NativeNetworkClient.request("choose",j->{j.addProperty("faction",faction);j.addProperty("color",color);j.addProperty("team",team);j.addProperty("start",start);});}
    private List<String> factions(){var list=new ArrayList<String>();list.add("");list.addAll(profile.factions());return list;}
    private void faction(String current,java.util.function.Consumer<String> changed){var list=factions();minecraft.setScreen(new GeneralsSetupScreen.Choices(this,L.s("Сторона"),list.stream().map(GeneralsSetupScreen::friendly).toList(),List.of(),i->changed.accept(list.get(i))));}
    private void color(int current,java.util.function.IntConsumer changed,int npcSlot){var list=new ArrayList<NativeSession.TeamColor>();list.add(new NativeSession.TeamColor(-1,L.s("Случайный"),0xaaaaaa));list.addAll(profile.colors());var labels=list.stream().map(c->(c.id()<0?L.s("Случайный цвет"):GeneralsSetupScreen.colorName(c.label()))+(c.id()>=0&&(shown.members().stream().anyMatch(m->m.color()==c.id()&&(!m.player().equals(minecraft.player.getUUID())||npcSlot>=0))||shown.starts().stream().anyMatch(s->s.difficulty()>=0&&s.slot()!=npcSlot&&s.color()==c.id()))?L.s(" • занят"):"")).toList();minecraft.setScreen(new GeneralsSetupScreen.Choices(this,L.s("Цвет армии"),labels,list.stream().map(c->(int)c.color()).toList(),i->changed.accept(list.get(i).id())));}
    private void npc(WorldSiteStore.Start s,String faction,int color,int team,int difficulty){NativeNetworkClient.request("npc_change",j->{j.addProperty("slot",s.slot());j.addProperty("faction",faction);j.addProperty("color",color);j.addProperty("team",team);j.addProperty("difficulty",difficulty);});}
    @Override protected void init(){
        rows.clear();try{profile=NativeProfiles.active();GeneralsSetupScreen.useProfile(profile);}catch(Exception ignored){}shown=minecraft.player==null?null:NativeNetworkClient.lobby;int needed=0;if(shown!=null){boolean edit=!NativeNetworkClient.active()&&!shown.preparing(),manager=shown.leader().equals(minecraft.player.getUUID());for(var member:shown.members())needed+=edit&&member.player().equals(minecraft.player.getUUID())?71:51;for(var marker:shown.starts())if(marker.difficulty()>=0)needed+=edit&&manager?71:51;}scroll=Math.clamp(scroll,0,Math.max(0,needed-(height-170)));int w=Math.min(560,width-24),x=(width-w)/2,half=(w-6)/2,y=58-scroll;
        if(shown==null){button(L.s("Создать комнату"),x,65,w,()->NativeNetworkClient.request("create")).active=profile!=null&&minecraft.player!=null&&!NativeRtsController.hasLocalSession();button(L.s("Присоединиться"),x,90,w,()->NativeNetworkClient.request("join")).active=profile!=null&&minecraft.player!=null&&!NativeRtsController.hasLocalSession();button(L.s("Правила"),x,115,w,()->minecraft.setScreen(new WorldSettingsScreen(this))).active=minecraft.player!=null;}
        else if(profile!=null&&minecraft.player!=null){
            boolean manager=shown.leader().equals(minecraft.player.getUUID()),editing=!NativeNetworkClient.active()&&!shown.preparing();
            for(var member:shown.members()){
                boolean own=member.player().equals(minecraft.player.getUUID());y+=22;
                if(own&&editing){
                    cycle(GeneralsSetupScreen.friendly(member.faction()),x,y,half,d->faction(member.faction(),f->choose(f,member.color(),member.team(),member.start())),true);
                    cycle(L.s("Цвет: ")+color(member.color()),x+half+6,y,half,d->color(member.color(),v->choose(member.faction(),v,member.team(),member.start()),-1),true);y+=23;
                    cycle(L.s("Команда: ")+team(member.team()),x,y,half,d->choose(member.faction(),member.color(),nextTeam(member.team(),d),member.start()),true);
                    var selected=shown.starts().stream().filter(s->s.slot()==member.start()).findFirst().orElse(null);
                    cycle(L.s("Старт: ")+(selected==null?L.s("Автовыбор"):position(selected)),x+half+6,y,half,d->minecraft.setScreen(new StartMapScreen(this,shown.starts(),member.start(),slot->choose(member.faction(),member.color(),member.team(),slot))),true);y+=26;
                }else y+=29;
            }
            for(var s:shown.starts())if(s.difficulty()>=0){
                if(manager&&editing){
                    cycle(L.s("Сложность: ")+difficulty(s.difficulty()),x+w-240,y,148,d->npc(s,s.faction(),s.color(),s.team(),Math.floorMod(s.difficulty()+d,3)),true);
                    cycle(L.s("Убрать NPC"),x+w-88,y,88,d->NativeNetworkClient.request("npc_remove",j->j.addProperty("slot",s.slot())),true);
                }
                y+=22;
                if(manager&&editing){
                    cycle(GeneralsSetupScreen.friendly(s.faction()),x,y,half,d->faction(s.faction(),f->npc(s,f,s.color(),s.team(),s.difficulty())),true);
                    cycle(L.s("Цвет: ")+color(s.color()),x+half+6,y,half,d->color(s.color(),v->npc(s,s.faction(),v,s.team(),s.difficulty()),s.slot()),true);y+=23;
                    cycle(L.s("Команда: ")+team(s.team()),x,y,half,d->npc(s,s.faction(),s.color(),nextTeam(s.team(),d),s.difficulty()),true);
                    cycle(L.s("Старт: ")+position(s),x+half+6,y,half,d->minecraft.setScreen(new StartMapScreen(this,shown.starts(),s.slot(),slot->{if(slot>=0&&slot!=s.slot())NativeNetworkClient.request("npc_move",j->{j.addProperty("slot",s.slot());j.addProperty("destination",slot);});})),true);y+=26;
                }else y+=29;
            }
            contentHeight=y+scroll-58;
            if(manager){button(L.s("+ Добавить NPC"),x,height-105,(w-12)/4,()->NativeNetworkClient.request("npc_add")).active=editing;}
            button(L.s("Позиции"),x+(w-12)/4+4,height-105,(w-12)/4,()->minecraft.setScreen(new StartMapScreen(this,shown.starts(),-1,slot->{})));
            button(L.s("Ресурсы"),x+2*((w-12)/4+4),height-105,(w-12)/4,()->minecraft.setScreen(new StartMapScreen(this,shown.starts(),-1,slot->{},true)));
            button(L.s("Правила"),x+3*((w-12)/4+4),height-105,(w-12)/4,()->minecraft.setScreen(new WorldSettingsScreen(this)));
            var start=button(NativeNetworkClient.active()?L.s("Вернуться к армии"):L.s("Начать схватку"),x,height-58,Math.max(100,w-106),()->{if(NativeNetworkClient.active())NativeNetworkClient.resume();else NativeNetworkClient.request("start");});
            start.active=NativeNetworkClient.active()||manager&&editing&&shown.members().stream().allMatch(NativeNetworkServer.Member::ready);
            start.setTooltip(Tooltip.create(L.component(L.s("Общий старт запускает создатель комнаты после проверки файлов всех игроков. Вход в текущий матч пока недоступен."))));
            button(L.s("Выйти"),x+w-100,height-58,100,NativeNetworkClient::leave);
        }
        for(var row:rows)row.visible=row.getY()>=57&&row.getBottom()<=height-113;
        button(L.s("Назад"),x,height-32,w,this::onClose);
    }
    static String difficulty(int value){return value==0?L.s("лёгкая"):value==1?L.s("средняя"):L.s("сложная");}
    @Override public void tick(){if(shown!=NativeNetworkClient.lobby)rebuildWidgets();}
    @Override public void render(GuiGraphics g,int mx,int my,float d){int w=Math.min(560,width-24),x=(width-w)/2;g.fill(x-5,52,x+w+5,height-110,0xb0121c23);g.fill(x-5,51,x+w+5,52,0xff9f854c);super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,14,0xfff0d68a);
        if(profile!=null)g.drawCenteredString(font,profile.name(),width/2,31,0xffbbbbbb);
        if(shown!=null&&profile!=null&&minecraft.player!=null){int y=58-scroll;g.enableScissor(x,56,x+w,height-112);boolean editing=!NativeNetworkClient.active()&&!shown.preparing(),manager=shown.leader().equals(minecraft.player.getUUID());for(var m:shown.members()){
            int tint=m.color()<0?0xffaaaaaa:profile.colors().stream().filter(c->c.id()==m.color()).map(c->0xff000000|(int)c.color()).findFirst().orElse(0xffaaaaaa);g.fill(x,y+1,x+8,y+9,tint);String name=m.name()+(m.player().equals(minecraft.player.getUUID())?L.s(" • вы"):"")+(shown.members().stream().filter(v->v.name().equals(m.name())).count()>1?" #"+m.player().toString().substring(0,4):"");g.drawString(font,font.plainSubstrByWidth(name+(m.ready()?L.s(" • файлы проверены"):L.s(" • проверка файлов…")),w-14),x+12,y,0xffeeeeee);y+=22;
            if(m.player().equals(minecraft.player.getUUID())&&editing)y+=49;else{g.drawString(font,font.plainSubstrByWidth(GeneralsSetupScreen.friendly(m.faction())+" • "+color(m.color())+L.s(" • команда ")+team(m.team()),w),x,y,0xffcccccc);y+=29;}}
            for(var s:shown.starts())if(s.difficulty()>=0){int tint=s.color()<0?0xffaaaaaa:profile.colors().stream().filter(c->c.id()==s.color()).map(c->0xff000000|(int)c.color()).findFirst().orElse(0xffaaaaaa);g.fill(x,y+1,x+8,y+9,tint);g.drawString(font,"NPC "+(s.slot()+1),x+12,y,0xffeec488);y+=22;if(manager&&editing)y+=49;else{g.drawString(font,font.plainSubstrByWidth(GeneralsSetupScreen.friendly(s.faction())+" • "+color(s.color())+" • "+difficulty(s.difficulty())+" • "+team(s.team()),w),x,y,0xffcccccc);y+=29;}}g.disableScissor();if(contentHeight>height-170){int sx=x+w+3;g.fill(sx,57,sx+2,height-113,0xff444444);int sy=57+(int)((height-184)*(double)scroll/Math.max(1,contentHeight-(height-170)));g.fill(sx,sy,sx+2,sy+10,0xfff0d68a);}}
        String text=NativeNetworkClient.error.isEmpty()?NativeNetworkClient.message:NativeNetworkClient.error;int top=shown==null?height-91:height-80;g.fill(x-2,top-2,x+w+2,top+19,0xe0181b20);g.enableScissor(x,top,x+w,top+20);g.drawWordWrap(font,L.component(text),x,top,w,NativeNetworkClient.error.isEmpty()?0xffcccccc:0xffff8888);g.disableScissor();if(my>=top&&my<top+20&&!text.isEmpty())g.renderTooltip(font,font.split(L.component(text),Math.min(400,w)),mx,my);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.clamp(scroll-(int)(v*23),0,Math.max(0,contentHeight-(height-170)));rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
