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
    private int scroll,contentHeight;
    private int lx,lw,tableW,mapX,mapW,tableTop,visibleRows,rowOffset;private final int[] cols=new int[6];
    private StartMapScreen preview;private final List<Button> popup=new ArrayList<>();private int popupLeft,popupTop,popupWidth,popupBottom,popupPage;
    private List<Pick> popupChoices=List.of();private Button popupOwner;
    private record Pick(String label,int chip,Runnable action){}private final List<AbstractWidget> rows=new ArrayList<>();
    public NativeLobbyScreen(Screen parent){super(L.component(L.s("Схватка Generals")));this.parent=parent;}
    private Button button(String text,int x,int y,int w,Runnable run){return addRenderableWidget(MenuButton.styled(L.component(text),b->run.run()).bounds(x,y,w,20).build());}
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
        rows.clear();try{profile=NativeProfiles.active();GeneralsSetupScreen.useProfile(profile);}catch(Exception ignored){}shown=minecraft.player==null?null:NativeNetworkClient.lobby;if(MenuButton.MODERN&&shown!=null&&profile!=null&&minecraft.player!=null){initModern();return;}int needed=0;if(shown!=null){boolean edit=!NativeNetworkClient.active()&&!shown.preparing(),manager=shown.leader().equals(minecraft.player.getUUID());for(var member:shown.members())needed+=edit&&member.player().equals(minecraft.player.getUUID())?71:51;for(var marker:shown.starts())if(marker.difficulty()>=0)needed+=edit&&manager?71:51;}scroll=Math.clamp(scroll,0,Math.max(0,needed-(height-170)));int w=Math.min(560,width-24),x=(width-w)/2,half=(w-6)/2,y=58-scroll;
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

    private boolean modern(){return MenuButton.MODERN&&shown!=null&&profile!=null&&minecraft.player!=null;}
    private int rosterCount(){return shown.members().size()+(int)shown.starts().stream().filter(s->s.difficulty()>=0).count();}
    private static String difficultyTitle(int d){var text=difficulty(d);return text.substring(0,1).toUpperCase(Locale.ROOT)+text.substring(1);}
    private int tint(int id){return id<0?0xff6d8090:profile.colors().stream().filter(c->c.id()==id).map(c->0xff000000|(int)c.color()).findFirst().orElse(0xff6d8090);}
    private String compactTeam(int id){return id==-2?"?":id==-1?"—":Integer.toString(id+1);}
    private String compactStart(int slot){return slot<0?L.s("Авто"):"#"+(slot+1);}
    private LobbyCell cell(String text,int col,int y,int chip,boolean enabled,boolean dropdown,Button.OnPress action){
        var b=addRenderableWidget(new LobbyCell(text,cols[col],y,cols[col+1]-cols[col]-3,23,dropdown,chip,false,action));b.active=enabled;return b;
    }
    private LobbyCell action(String text,int x,int y,int w,boolean primary,boolean enabled,Runnable action){
        var b=addRenderableWidget(new LobbyCell(text,x,y,w,24,false,-1,primary,a->action.run()));b.active=enabled;return b;
    }
    private List<Pick> factionChoices(java.util.function.Consumer<String> change){return factions().stream().map(f->new Pick(GeneralsSetupScreen.friendly(f),-1,()->change.accept(f))).toList();}
    private List<Pick> teamChoices(java.util.function.IntConsumer change){var list=new ArrayList<Pick>();for(int i=-2;i<8;i++){int id=i;list.add(new Pick(team(id),-1,()->change.accept(id)));}return list;}
    private List<Pick> colorChoices(int current,int npcSlot,java.util.function.IntConsumer change){
        var list=new ArrayList<Pick>();list.add(new Pick(L.s("Случайный цвет"),tint(-1),()->change.accept(-1)));
        for(var color:profile.colors()){
            boolean occupied=shown.members().stream().anyMatch(m->m.color()==color.id()&&(!m.player().equals(minecraft.player.getUUID())||npcSlot>=0))||shown.starts().stream().anyMatch(s->s.difficulty()>=0&&s.slot()!=npcSlot&&s.color()==color.id());
            if(!occupied||color.id()==current)list.add(new Pick(GeneralsSetupScreen.colorName(color.label()),tint(color.id()),()->change.accept(color.id())));
        }return list;
    }
    private void closePopup(){boolean focused=popup.contains(getFocused());for(var b:popup)removeWidget(b);popup.clear();if(focused)setFocused(popupOwner);}
    private int popupCapacity(){return Math.max(1,Math.min(8,(height-48)/23));}
    private void pick(Button owner,List<Pick> choices){closePopup();popupChoices=choices;popupOwner=owner;popupPage=0;showPopup();}
    private void showPopup(){
        closePopup();if(popupChoices.isEmpty())return;
        int cap=popupCapacity(),count=Math.min(cap,popupChoices.size()-popupPage);popupWidth=Math.min(width-24,Math.max(170,Math.min(300,popupChoices.stream().mapToInt(p->font.width(p.label())+34).max().orElse(170))));
        popupLeft=Math.clamp(popupOwner.getX(),12,width-popupWidth-12);int size=count*23+(popupChoices.size()>cap?24:0);
        popupTop=popupOwner.getBottom()+3;if(popupTop+size>height-12)popupTop=Math.max(12,popupOwner.getY()-size-3);popupBottom=popupTop+size;
        for(int i=0;i<count;i++){var choice=popupChoices.get(popupPage+i);var b=addRenderableWidget(new LobbyCell(choice.label(),popupLeft,popupTop+i*23,popupWidth,23,false,choice.chip(),false,w->{closePopup();choice.action().run();}));popup.add(b);}
        if(popupChoices.size()>cap){var up=action("↑",popupLeft,popupTop+count*23,popupWidth/2-2,false,popupPage>0,()->{popupPage=Math.max(0,popupPage-cap);showPopup();});popup.add(up);var down=action("↓",popupLeft+popupWidth/2+1,popupTop+count*23,popupWidth-popupWidth/2-1,false,popupPage+cap<popupChoices.size(),()->{popupPage=Math.min(popupChoices.size()-cap,popupPage+cap);showPopup();});popup.add(down);}
        setFocused(popup.getFirst());
    }
    private void initModern(){
        closePopup();lx=Math.max(12,(width-980)/2);lw=width-2*lx;mapW=lw>=570?Math.min(224,lw*27/100):0;tableW=lw-(mapW>0?mapW+18:0);mapX=lx+tableW+18;tableTop=71;
        int[] shares={0,23,38,73,85,100};for(int i=0;i<6;i++)cols[i]=lx+tableW*shares[i]/100;
        visibleRows=Math.max(1,(height-187)/26);rowOffset=Math.clamp(rowOffset,0,Math.max(0,rosterCount()+1-visibleRows));
        boolean edit=!NativeNetworkClient.active()&&!shown.preparing(),manager=shown.leader().equals(minecraft.player.getUUID());int index=0;
        for(var m:shown.members()){
            int y=tableTop+(index++-rowOffset)*26;if(y<tableTop||y>=tableTop+visibleRows*26)continue;boolean own=m.player().equals(minecraft.player.getUUID()),enabled=edit&&own;
            var name=cell(m.name(),0,y,m.ready()?0xff61dbaa:0xffdcac62,false,false,b->{});name.setTooltip(Tooltip.create(L.component(m.name()+(own?L.s(" • вы"):"")+"\n"+(m.ready()?L.s("Файлы проверены"):L.s("Проверка файлов…")))));
            cell(color(m.color()),1,y,tint(m.color()),enabled,true,b->pick(b,colorChoices(m.color(),-1,v->choose(m.faction(),v,m.team(),m.start()))));
            cell(GeneralsSetupScreen.friendly(m.faction()),2,y,-1,enabled,true,b->pick(b,factionChoices(f->choose(f,m.color(),m.team(),m.start()))));
            cell(compactTeam(m.team()),3,y,-1,enabled,true,b->pick(b,teamChoices(t->choose(m.faction(),m.color(),t,m.start()))));
            cell(compactStart(m.start()),4,y,-1,enabled,true,b->minecraft.setScreen(new StartMapScreen(this,shown.starts(),m.start(),slot->choose(m.faction(),m.color(),m.team(),slot))));
        }
        for(var s:shown.starts())if(s.difficulty()>=0){
            int y=tableTop+(index++-rowOffset)*26;if(y<tableTop||y>=tableTop+visibleRows*26)continue;boolean enabled=edit&&manager;
            cell("NPC · "+difficultyTitle(s.difficulty()),0,y,-1,enabled,true,b->{var choices=new ArrayList<Pick>();for(int i=0;i<3;i++){int d=i;choices.add(new Pick(difficultyTitle(d),-1,()->npc(s,s.faction(),s.color(),s.team(),d)));}choices.add(new Pick(L.s("Убрать NPC"),0xffec8181,()->NativeNetworkClient.request("npc_remove",j->j.addProperty("slot",s.slot()))));pick(b,choices);});
            cell(color(s.color()),1,y,tint(s.color()),enabled,true,b->pick(b,colorChoices(s.color(),s.slot(),v->npc(s,s.faction(),v,s.team(),s.difficulty()))));
            cell(GeneralsSetupScreen.friendly(s.faction()),2,y,-1,enabled,true,b->pick(b,factionChoices(f->npc(s,f,s.color(),s.team(),s.difficulty()))));
            cell(compactTeam(s.team()),3,y,-1,enabled,true,b->pick(b,teamChoices(t->npc(s,s.faction(),s.color(),t,s.difficulty()))));
            cell(compactStart(s.slot()),4,y,-1,enabled,true,b->minecraft.setScreen(new StartMapScreen(this,shown.starts(),s.slot(),slot->{if(slot>=0&&slot!=s.slot())NativeNetworkClient.request("npc_move",j->{j.addProperty("slot",s.slot());j.addProperty("destination",slot);});})));
        }
        int addY=tableTop+(index-rowOffset)*26;if(addY>=tableTop&&addY<tableTop+visibleRows*26&&manager)action(L.s("+ Добавить NPC"),lx,addY,tableW-3,false,edit,()->NativeNetworkClient.request("npc_add"));
        if(mapW>0){
            int own=shown.members().stream().filter(m->m.player().equals(minecraft.player.getUUID())).mapToInt(NativeNetworkServer.Member::start).findFirst().orElse(-1);
            preview=new StartMapScreen(this,shown.starts(),own,slot->{});preview.init(minecraft,width,height);preview.left=mapX;preview.top=tableTop;preview.side=Math.min(mapW,Math.max(52,height-225));
            action(L.s("Позиции"),mapX,tableTop+preview.side+8,mapW,false,true,()->minecraft.setScreen(new StartMapScreen(this,shown.starts(),own,slot->{if(edit)shown.members().stream().filter(m->m.player().equals(minecraft.player.getUUID())).findFirst().ifPresent(m->choose(m.faction(),m.color(),m.team(),slot));})));
        }else preview=null;
        int controls=height-99,third=(lw-16)/3;
        action(L.s("Правила"),lx,controls,third,false,true,()->minecraft.setScreen(new WorldSettingsScreen(this)));
        action(L.s("Ресурсы"),lx+third+8,controls,third,false,true,()->minecraft.setScreen(new StartMapScreen(this,shown.starts(),-1,slot->{},true)));
        action(mapW>0?L.s("Маркеры"):L.s("Позиции"),lx+2*(third+8),controls,lw-2*(third+8),false,true,()->minecraft.setScreen(mapW>0?new MarkerFinderScreen(this):new StartMapScreen(this,shown.starts(),-1,slot->{})));
        int secondary=Math.min(132,lw/4);
        var start=action(NativeNetworkClient.active()?L.s("Вернуться к армии"):L.s("Начать схватку"),lx,height-42,lw-2*(secondary+8),true,NativeNetworkClient.active()||manager&&edit&&shown.members().stream().allMatch(NativeNetworkServer.Member::ready),()->{if(NativeNetworkClient.active())NativeNetworkClient.resume();else NativeNetworkClient.request("start");});
        if(!manager&&!NativeNetworkClient.active())start.setTooltip(Tooltip.create(L.component(L.s("Матч запускает хост"))));
        action(L.s("Выйти"),lx+lw-2*secondary-8,height-42,secondary,false,true,NativeNetworkClient::leave);
        action(L.s("Назад"),lx+lw-secondary,height-42,secondary,false,true,this::onClose);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);if(modern())drawBoard(g,mx,my,d);}
    private void drawBoard(GuiGraphics g,int mx,int my,float d){
        g.fill(lx-8,11,lx+lw+8,height-11,0xf00b131d);g.fill(lx-8,11,lx+lw+8,12,0xff36758a);g.fill(lx-8,height-12,lx+lw+8,height-11,0xff234957);
        g.fill(lx,23,lx+3,42,0xff65d9f0);g.drawString(font,L.s("СХВАТКА"),lx+12,23,0xffe7f5fc,false);
        g.drawString(font,font.plainSubstrByWidth(profile.name(),lw-130),lx+12,38,0xff8da7b8,false);
        String count=L.s("Игроки")+": "+shown.members().size()+" · NPC: "+shown.starts().stream().filter(s->s.difficulty()>=0).count();g.drawString(font,count,lx+lw-font.width(count),29,0xff9fb6c4,false);
        String[] headings={L.s("Игроки"),L.s("Цвет"),L.s("Генерал"),L.s("Команда"),L.s("Старт")};for(int i=0;i<5;i++)g.drawString(font,font.plainSubstrByWidth(headings[i],cols[i+1]-cols[i]-5),cols[i]+4,57,0xff81adbf,false);
        g.fill(lx,tableTop-4,lx+tableW-3,tableTop-3,0xff285164);
        int populated=Math.min(visibleRows,Math.max(0,rosterCount()+1-rowOffset));for(int i=populated;i<visibleRows;i++){int y=tableTop+i*26;g.fill(lx,y,lx+tableW-3,y+23,0x50172530);g.drawString(font,"—",lx+9,y+7,0xff3e5868,false);}
        if(preview!=null){g.drawString(font,L.s("Карта"),mapX+3,57,0xff81adbf,false);preview.drawMap(g,mx,my,false);}
        if(rosterCount()+1>visibleRows){int total=rosterCount()+1,track=visibleRows*26;int bar=Math.max(12,track*visibleRows/total);int sy=tableTop+(track-bar)*rowOffset/Math.max(1,total-visibleRows);g.fill(lx+tableW-1,tableTop,lx+tableW+1,tableTop+track,0xff203845);g.fill(lx+tableW-1,sy,lx+tableW+1,sy+bar,0xff62bed6);}
        String status=NativeNetworkClient.error.isEmpty()?NativeNetworkClient.message:NativeNetworkClient.error;
        if(!status.isBlank())g.drawString(font,font.plainSubstrByWidth(status,lw-4),lx,height-62,NativeNetworkClient.error.isEmpty()?0xff8fa8b8:0xffff9b93,false);
    }
    private void renderModern(GuiGraphics g,int mx,int my,float d){
        super.render(g,mx,my,d);
        if(!popup.isEmpty()){g.fill(popupLeft-2,popupTop-2,popupLeft+popupWidth+2,popupTop,0xff75cde5);g.fill(popupLeft-2,popupTop,popupLeft,popupBottom+2,0xff3d7f94);g.fill(popupLeft+popupWidth,popupTop,popupLeft+popupWidth+2,popupBottom+2,0xff3d7f94);g.fill(popupLeft,popupBottom,popupLeft+popupWidth,popupBottom+2,0xff3d7f94);}
    }
    @Override public boolean mouseClicked(double x,double y,int b){if(modern()&&!popup.isEmpty()){if(x>=popupLeft&&x<=popupLeft+popupWidth&&y>=popupTop&&y<=popupBottom){if(b==0)for(var option:List.copyOf(popup))if(option.active&&option.isMouseOver(x,y)){option.playDownSound(minecraft.getSoundManager());((LobbyCell)option).activate();return true;}return true;}closePopup();}boolean handled=super.mouseClicked(x,y,b);if(!popup.isEmpty()&&!popup.contains(getFocused()))setFocused(popup.getFirst());return handled;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(modern()&&!popup.isEmpty()){
            if(key==com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE){closePopup();setFocused(popupOwner);return true;}
            if(key==com.mojang.blaze3d.platform.InputConstants.KEY_UP||key==com.mojang.blaze3d.platform.InputConstants.KEY_DOWN||key==com.mojang.blaze3d.platform.InputConstants.KEY_TAB){int step=key==com.mojang.blaze3d.platform.InputConstants.KEY_UP||key==com.mojang.blaze3d.platform.InputConstants.KEY_TAB&&(modifiers&1)!=0?-1:1;setFocused(popup.get(Math.floorMod(popup.indexOf(getFocused())+step,popup.size())));return true;}
            if(key==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN||key==com.mojang.blaze3d.platform.InputConstants.KEY_SPACE){if(getFocused() instanceof LobbyCell focused&&popup.contains(focused)&&focused.active){focused.playDownSound(minecraft.getSoundManager());focused.activate();}return true;}
        }return super.keyPressed(key,scan,modifiers);
    }

    static String difficulty(int value){return value==0?L.s("лёгкая"):value==1?L.s("средняя"):L.s("сложная");}
    @Override public void tick(){if(shown!=NativeNetworkClient.lobby)rebuildWidgets();}
    @Override public void render(GuiGraphics g,int mx,int my,float d){if(modern()){renderModern(g,mx,my,d);return;}int w=Math.min(560,width-24),x=(width-w)/2;g.fill(x-5,52,x+w+5,height-110,0xb0121c23);g.fill(x-5,51,x+w+5,52,0xff9f854c);MenuButton.panel(g,width,height);super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,14,0xfff0d68a);
        if(profile!=null)g.drawCenteredString(font,profile.name(),width/2,31,0xffbbbbbb);
        if(shown!=null&&profile!=null&&minecraft.player!=null){int y=58-scroll;g.enableScissor(x,56,x+w,height-112);boolean editing=!NativeNetworkClient.active()&&!shown.preparing(),manager=shown.leader().equals(minecraft.player.getUUID());for(var m:shown.members()){
            int tint=m.color()<0?0xffaaaaaa:profile.colors().stream().filter(c->c.id()==m.color()).map(c->0xff000000|(int)c.color()).findFirst().orElse(0xffaaaaaa);g.fill(x,y+1,x+8,y+9,tint);String name=m.name()+(m.player().equals(minecraft.player.getUUID())?L.s(" • вы"):"")+(shown.members().stream().filter(v->v.name().equals(m.name())).count()>1?" #"+m.player().toString().substring(0,4):"");g.drawString(font,font.plainSubstrByWidth(name+(m.ready()?L.s(" • файлы проверены"):L.s(" • проверка файлов…")),w-14),x+12,y,0xffeeeeee);y+=22;
            if(m.player().equals(minecraft.player.getUUID())&&editing)y+=49;else{g.drawString(font,font.plainSubstrByWidth(GeneralsSetupScreen.friendly(m.faction())+" • "+color(m.color())+L.s(" • команда ")+team(m.team()),w),x,y,0xffcccccc);y+=29;}}
            for(var s:shown.starts())if(s.difficulty()>=0){int tint=s.color()<0?0xffaaaaaa:profile.colors().stream().filter(c->c.id()==s.color()).map(c->0xff000000|(int)c.color()).findFirst().orElse(0xffaaaaaa);g.fill(x,y+1,x+8,y+9,tint);g.drawString(font,"NPC "+(s.slot()+1),x+12,y,0xffeec488);y+=22;if(manager&&editing)y+=49;else{g.drawString(font,font.plainSubstrByWidth(GeneralsSetupScreen.friendly(s.faction())+" • "+color(s.color())+" • "+difficulty(s.difficulty())+" • "+team(s.team()),w),x,y,0xffcccccc);y+=29;}}g.disableScissor();if(contentHeight>height-170){int sx=x+w+3;g.fill(sx,57,sx+2,height-113,0xff444444);int sy=57+(int)((height-184)*(double)scroll/Math.max(1,contentHeight-(height-170)));g.fill(sx,sy,sx+2,sy+10,0xfff0d68a);}}
        String text=NativeNetworkClient.error.isEmpty()?NativeNetworkClient.message:NativeNetworkClient.error;int top=shown==null?height-91:height-80;g.fill(x-2,top-2,x+w+2,top+19,0xe0181b20);g.enableScissor(x,top,x+w,top+20);g.drawWordWrap(font,L.component(text),x,top,w,NativeNetworkClient.error.isEmpty()?0xffcccccc:0xffff8888);g.disableScissor();if(my>=top&&my<top+20&&!text.isEmpty())g.renderTooltip(font,font.split(L.component(text),Math.min(400,w)),mx,my);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(modern()){if(!popup.isEmpty()){popupPage=Math.clamp(popupPage-(int)v,0,Math.max(0,popupChoices.size()-popupCapacity()));showPopup();return true;}rowOffset=Math.clamp(rowOffset-(int)v,0,Math.max(0,rosterCount()+1-visibleRows));rebuildWidgets();return true;}scroll=Math.clamp(scroll-(int)(v*23),0,Math.max(0,contentHeight-(height-170)));rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
