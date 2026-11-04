package local.generalsmc.client;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Shared roster: identities and choices are owned by the Minecraft server. */
public final class NativeLobbyScreen extends CompatScreen {
    private final Screen parent;private NativeNetworkServer.Lobby shown;private NativeProfiles.Profile profile;
    private int lx,lw,tableW,mapX,mapW,tableTop,visibleRows,rowOffset;private final int[] cols=new int[6];
    private StartMapScreen preview;private final List<Button> popup=new ArrayList<>();private int popupLeft,popupTop,popupWidth,popupBottom,popupPage;
    private final Map<AbstractWidget,Boolean> popupBlocked=new IdentityHashMap<>();
    private final Map<AbstractWidget,Tooltip> popupTooltips=new IdentityHashMap<>();
    private List<Pick> popupChoices=List.of();private Button popupOwner;private int popupFocus;private final Map<Button,Integer> popupIndices=new LinkedHashMap<>();
    private record Pick(String label,int chip,boolean selected,Runnable action){Pick(String label,int chip,Runnable action){this(label,chip,false,action);}}
    public NativeLobbyScreen(Screen parent){super(L.component(L.s("Схватка Generals")));this.parent=parent;}
    static void open(Screen parent){var mc=net.minecraft.client.Minecraft.getInstance();boolean solo=mc.player!=null&&mc.getSingleplayerServer()!=null&&mc.getConnection()!=null&&mc.getConnection().getOnlinePlayers().size()<=1&&NativeNetworkClient.lobby==null&&!NativeRtsController.hasLocalSession();mc.gui.setScreen(new NativeLobbyScreen(parent));if(solo)NativeNetworkClient.request("create");}
    private Button button(String text,int x,int y,int w,Runnable run){return addRenderableWidget(MenuButton.styled(L.component(text),b->run.run()).bounds(x,y,w,20).build());}
    static String team(int value){return value==-2?L.s("Случайная"):value==-1?L.s("Без команды"):Integer.toString(value+1);}
    static int nextTeam(int value,int direction){return Math.floorMod(value+2+direction,10)-2;}
    static String position(WorldSiteStore.Start s){return s.pos().getX()+", "+s.pos().getY()+", "+s.pos().getZ();}
    private String color(int id){if(id<0)return L.s("Случайный");return profile.colors().stream().filter(c->c.id()==id).map(c->GeneralsSetupScreen.colorName(c.label())).findFirst().orElse("?");}
    private void choose(String faction,int color,int team,int start){NativeNetworkClient.request("choose",j->{j.addProperty("faction",faction);j.addProperty("color",color);j.addProperty("team",team);j.addProperty("start",start);});}
    private List<String> factions(){var list=new ArrayList<String>();list.add("");list.addAll(profile.factions());return list;}
    private void npc(WorldSiteStore.Start s,String faction,int color,int team,int difficulty){NativeNetworkClient.request("npc_change",j->{j.addProperty("slot",s.slot());j.addProperty("faction",faction);j.addProperty("color",color);j.addProperty("team",team);j.addProperty("difficulty",difficulty);});}
    @Override protected void init(){
        closePopup();preview=null;
        try{profile=NativeProfiles.active();GeneralsSetupScreen.useProfile(profile);}catch(Exception ignored){}
        shown=minecraft.player==null?null:NativeNetworkClient.lobby;
        if(table()){initTable();return;}
        int w=Math.min(460,width-24),x=(width-w)/2;
        boolean connected=profile!=null&&NativeProfiles.available(profile)&&minecraft.player!=null&&!NativeRtsController.hasLocalSession();
        var create=MenuButton.primary(button(L.s("Создать комнату"),x,65,w,()->NativeNetworkClient.request("create")));create.active=connected;if(!connected)create.setTooltip(Tooltip.create(L.component(minecraft.player==null?L.s("Сначала войдите в мир"):NativeRtsController.hasLocalSession()?L.s("Сначала завершите свободную игру"):L.s("Сначала подключите Generals"))));
        var join=button(L.s("Присоединиться"),x,91,w,()->NativeNetworkClient.request("join"));
        join.active=connected&&minecraft.getConnection()!=null&&minecraft.getConnection().getOnlinePlayers().size()>1;
        if(!join.active)join.setTooltip(Tooltip.create(L.component(connected?L.s("Для сетевой игры пригласите игроков в мир."):minecraft.player==null?L.s("Сначала войдите в мир"):NativeRtsController.hasLocalSession()?L.s("Сначала завершите свободную игру"):L.s("Сначала подключите Generals"))));
        button(L.s("Правила"),x,117,w,()->minecraft.gui.setScreen(new WorldSettingsScreen(this))).active=minecraft.player!=null;
        if(profile==null||!NativeProfiles.available(profile))button(L.s("Подключить игру"),x,143,w,()->minecraft.gui.setScreen(new NativeProfilesScreen(this))).active=!NativeRtsController.hasSession();
        button(L.s("К меню Generals"),x,height-32,w,this::onClose);
    }

    private String leaveLabel(){return NativeNetworkClient.active()?L.s("Завершить матч"):shown!=null&&shown.leader().equals(minecraft.player.getUUID())?L.s("Закрыть комнату"):L.s("Покинуть комнату");}
    private void leave(){if(shown!=null&&(NativeNetworkClient.active()||shown.leader().equals(minecraft.player.getUUID())))minecraft.gui.setScreen(new MenuConfirmScreen(ok->{if(ok){NativeNetworkClient.leave();minecraft.gui.setScreen(new GeneralsSetupScreen(parent));}else minecraft.gui.setScreen(this);},L.component(leaveLabel()+"?"),L.component(L.s("Комната закроется для всех участников. Мир Minecraft останется."))));else NativeNetworkClient.leave();}
    private void matchSettings(int cash,boolean limit){NativeNetworkClient.request("match_settings",j->{j.addProperty("cash",cash);j.addProperty("limit",limit);});}
    private void chooseStart(int slot){if(shown!=null&&!NativeNetworkClient.active()&&!shown.preparing())shown.members().stream().filter(m->m.player().equals(minecraft.player.getUUID())).findFirst().ifPresent(m->choose(m.faction(),m.color(),m.team(),slot));}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){if(!popup.isEmpty())return true;if(preview!=null&&preview.dragging)return preview.mouseDragged(x,y,b,dx,dy);return super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){if(!popup.isEmpty())return true;if(preview!=null&&preview.dragging)return preview.mouseReleased(x,y,b);return super.mouseReleased(x,y,b);}
    private boolean table(){return shown!=null&&profile!=null&&minecraft.player!=null;}
    private int rosterCount(){return shown.members().size()+(int)shown.starts().stream().filter(s->s.difficulty()>=0).count();}
    private static String difficultyTitle(int d){var text=difficulty(d);return text.substring(0,1).toUpperCase(Locale.ROOT)+text.substring(1);}
    private int tint(int id){return id<0?0xff6d8090:profile.colors().stream().filter(c->c.id()==id).map(c->0xff000000|(int)c.color()).findFirst().orElse(0xff6d8090);}
    private String compactTeam(int id){return id==-2?"?":id==-1?"—":Integer.toString(id+1);}
    private String compactStart(int slot){return slot<0?L.s("Авто"):"#"+(slot+1);}
    private LobbyCell cell(String text,int col,int y,int chip,boolean enabled,boolean dropdown,Button.OnPress action){
        var b=addRenderableWidget(new LobbyCell(text,cols[col],y,cols[col+1]-cols[col]-3,23,dropdown,chip,false,action));b.active=enabled;if(!enabled&&dropdown)b.setTooltip(Tooltip.create(L.component(editBlocked())));return b;
    }
    private LobbyCell action(String text,int x,int y,int w,boolean primary,boolean enabled,Runnable action){
        var b=addRenderableWidget(new LobbyCell(text,x,y,w,24,false,-1,primary,a->action.run()).action());b.active=enabled;if(!enabled)b.setTooltip(Tooltip.create(L.component(editBlocked())));return b;
    }
    private String editBlocked(){return NativeNetworkClient.active()?L.s("Меняется перед началом схватки"):shown!=null&&shown.preparing()?L.s("Проверка файлов…"):shown!=null&&!shown.leader().equals(minecraft.player.getUUID())?L.s("Настраивает только хозяин схватки"):L.s("Дождитесь готовности игроков");}
    private List<Pick> factionChoices(java.util.function.Consumer<String> change){return factions().stream().map(f->new Pick(GeneralsSetupScreen.friendly(f),-1,()->change.accept(f))).toList();}
    private List<Pick> teamChoices(int current,java.util.function.IntConsumer change){var list=new ArrayList<Pick>();for(int i=-2;i<8;i++){int id=i;list.add(new Pick(team(id),-1,id==current,()->change.accept(id)));}return list;}
    private List<Pick> colorChoices(int current,int npcSlot,java.util.function.IntConsumer change){
        var list=new ArrayList<Pick>();list.add(new Pick(L.s("Случайный цвет"),tint(-1),current<0,()->change.accept(-1)));
        for(var color:profile.colors()){
            boolean occupied=shown.members().stream().anyMatch(m->m.color()==color.id()&&(!m.player().equals(minecraft.player.getUUID())||npcSlot>=0))||shown.starts().stream().anyMatch(s->s.difficulty()>=0&&s.slot()!=npcSlot&&s.color()==color.id());
            if(!occupied||color.id()==current)list.add(new Pick(GeneralsSetupScreen.colorName(color.label()),tint(color.id()),color.id()==current,()->change.accept(color.id())));
        }return list;
    }
    private void closePopup(){boolean focused=popup.contains(getFocused());for(var b:popup)removeWidget(b);popup.clear();popupIndices.clear();popupBlocked.forEach((w,a)->w.active=a);popupBlocked.clear();popupTooltips.forEach(AbstractWidget::setTooltip);popupTooltips.clear();if(focused)setFocused(popupOwner);}
    private void focusPopup(){var target=popupIndices.entrySet().stream().filter(e->e.getValue()==popupFocus).map(Map.Entry::getKey).findFirst().orElseGet(()->popupIndices.keySet().stream().findFirst().orElse(null));if(target!=null){popupFocus=popupIndices.get(target);setFocused(target);}}
    private int popupCapacity(){return Math.max(1,Math.min(8,(height-48)/23));}
    private void pick(Button owner,List<Pick> choices){closePopup();popupChoices=choices.stream().map(p->new Pick(p.label(),p.chip(),p.selected()||p.label().equals(owner.getMessage().getString()),p.action())).toList();popupOwner=owner;int selected=0;for(int i=0;i<popupChoices.size();i++)if(popupChoices.get(i).selected()){selected=i;break;}popupFocus=selected;popupPage=selected/popupCapacity()*popupCapacity();showPopup();}
    private void showPopup(){
        closePopup();if(popupChoices.isEmpty())return;for(var child:children())if(child instanceof AbstractWidget w){popupBlocked.put(w,w.active);if(w instanceof LobbyCell c&&c.currentTooltip!=null){popupTooltips.put(w,c.currentTooltip);w.setTooltip(null);}w.active=false;}
        int cap=popupCapacity(),count=Math.min(cap,popupChoices.size()-popupPage);popupWidth=Math.min(width-24,Math.max(170,Math.min(300,popupChoices.stream().mapToInt(p->font.width(p.label())+34).max().orElse(170))));
        popupLeft=Math.clamp(popupOwner.getX(),12,width-popupWidth-12);int size=count*23+(popupChoices.size()>cap?24:0);
        popupTop=popupOwner.getBottom()+3;if(popupTop+size>height-12)popupTop=Math.max(12,popupOwner.getY()-size-3);popupBottom=popupTop+size;
        var backdrop=addRenderableWidget(new LobbyCell("",popupLeft-4,popupTop-4,popupWidth+8,size+8,false,-1,false,b->{}).popover());popup.add(backdrop);
        for(int i=0;i<count;i++){var choice=popupChoices.get(popupPage+i);var b=addRenderableWidget(new LobbyCell(choice.label(),popupLeft,popupTop+i*23,popupWidth,23,false,choice.chip(),false,w->{closePopup();choice.action().run();}).option(choice.selected()));popup.add(b);popupIndices.put(b,popupPage+i);}
        if(popupChoices.size()>cap){var up=action("↑",popupLeft,popupTop+count*23,popupWidth/2-2,false,popupPage>0,()->{popupPage=Math.max(0,popupPage-cap);showPopup();});popup.add(up);var down=action("↓",popupLeft+popupWidth/2+1,popupTop+count*23,popupWidth-popupWidth/2-1,false,popupPage+cap<popupChoices.size(),()->{popupPage=Math.min(popupChoices.size()-cap,popupPage+cap);showPopup();});popup.add(down);}
        focusPopup();
    }
    private void initTable(){
        closePopup();lx=Math.max(12,(width-980)/2);lw=width-2*lx;mapW=lw>=570&&height>=300?Math.min(224,lw*27/100):0;tableW=lw-(mapW>0?mapW+18:0);mapX=lx+tableW+18;tableTop=height<300?57:71;
        int[] shares={0,23,42,73,85,100};for(int i=0;i<6;i++)cols[i]=lx+tableW*shares[i]/100;
        visibleRows=Math.max(1,(height-130-tableTop+3)/26);rowOffset=Math.clamp(rowOffset,0,Math.max(0,rosterCount()-visibleRows));
        boolean edit=!NativeNetworkClient.active()&&!shown.preparing(),manager=shown.leader().equals(minecraft.player.getUUID());int index=0;
        for(var m:shown.members()){
            int y=tableTop+(index++-rowOffset)*26;if(y<tableTop||y>=tableTop+visibleRows*26)continue;boolean own=m.player().equals(minecraft.player.getUUID()),enabled=edit&&own;
            var name=cell((m.confirmed()?"✓ ":"")+m.name(),0,y,m.confirmed()?0xff61dbaa:0xffdcac62,false,false,b->{});name.setTooltip(Tooltip.create(L.component(m.name()+(own?L.s(" • вы"):"")+"\n"+(m.confirmed()?L.s("Готов"):m.ready()?L.s("Не готов"):L.s("Проверка файлов…")))));
            cell(color(m.color()),1,y,tint(m.color()),enabled,true,b->pick(b,colorChoices(m.color(),-1,v->choose(m.faction(),v,m.team(),m.start()))));
            cell(GeneralsSetupScreen.friendly(m.faction()),2,y,-1,enabled,true,b->pick(b,factionChoices(f->choose(f,m.color(),m.team(),m.start()))));
            cell(compactTeam(m.team()),3,y,-1,enabled,true,b->pick(b,teamChoices(m.team(),t->choose(m.faction(),m.color(),t,m.start()))));
            cell(compactStart(m.start()),4,y,-1,enabled,true,b->minecraft.gui.setScreen(new StartMapScreen(this,shown.starts(),m.start(),slot->choose(m.faction(),m.color(),m.team(),slot))));
        }
        for(var s:shown.starts())if(s.difficulty()>=0){
            int y=tableTop+(index++-rowOffset)*26;if(y<tableTop||y>=tableTop+visibleRows*26)continue;boolean enabled=edit&&manager;
            cell("NPC · "+difficultyTitle(s.difficulty()),0,y,-1,enabled,true,b->{var choices=new ArrayList<Pick>();for(int i=0;i<3;i++){int d=i;choices.add(new Pick(difficultyTitle(d),-1,d==s.difficulty(),()->npc(s,s.faction(),s.color(),s.team(),d)));}choices.add(new Pick(L.s("Убрать NPC"),0xffec8181,()->NativeNetworkClient.request("npc_remove",j->j.addProperty("slot",s.slot()))));pick(b,choices);});
            cell(color(s.color()),1,y,tint(s.color()),enabled,true,b->pick(b,colorChoices(s.color(),s.slot(),v->npc(s,s.faction(),v,s.team(),s.difficulty()))));
            cell(GeneralsSetupScreen.friendly(s.faction()),2,y,-1,enabled,true,b->pick(b,factionChoices(f->npc(s,f,s.color(),s.team(),s.difficulty()))));
            cell(compactTeam(s.team()),3,y,-1,enabled,true,b->pick(b,teamChoices(s.team(),t->npc(s,s.faction(),s.color(),t,s.difficulty()))));
            cell(compactStart(s.slot()),4,y,-1,enabled,true,b->minecraft.gui.setScreen(new StartMapScreen(this,shown.starts(),s.slot(),slot->{if(slot>=0&&slot!=s.slot())NativeNetworkClient.request("npc_move",j->{j.addProperty("slot",s.slot());j.addProperty("destination",slot);});})));
        }
        if(mapW>0){
            int own=shown.members().stream().filter(m->m.player().equals(minecraft.player.getUUID())).mapToInt(NativeNetworkServer.Member::start).findFirst().orElse(-1);
            preview=new StartMapScreen(this,shown.starts(),own,this::chooseStart);preview.init(width,height);preview.left=mapX;preview.top=tableTop;preview.side=Math.min(mapW,Math.max(52,height-255));
        }else preview=null;
        int controls=height-99,quarter=(lw-24)*22/100,optionsWidth=lw-24-quarter*3;
        var addNpc=action("+ NPC",lx,controls,quarter,false,manager&&edit&&rosterCount()<8,()->NativeNetworkClient.request("npc_add"));if(manager&&edit&&rosterCount()>=8)addNpc.setTooltip(Tooltip.create(L.component(L.s("Все места заняты"))));
        action(L.s("Карта"),lx+quarter+8,controls,quarter,false,true,()->minecraft.gui.setScreen(new StartMapScreen(this,shown.starts(),-1,this::chooseStart)));
        action(L.s("Параметры"),lx+2*(quarter+8),controls,optionsWidth,false,true,()->minecraft.gui.setScreen(new Options(this)));
        action(L.s("Чат"),lx+2*(quarter+8)+optionsWidth+8,controls,quarter,false,true,()->minecraft.gui.setScreen(new NativeLobbyChatScreen(this)));
        int secondary=Math.min(160,lw/3);
        var me=shown.members().stream().filter(m->m.player().equals(minecraft.player.getUUID())).findFirst().orElse(null);
        var start=action(NativeNetworkClient.active()?L.s("Вернуться к армии"):me!=null&&me.confirmed()?L.s("Не готов"):L.s("Готов"),lx,height-42,lw-secondary-8,me==null||!me.confirmed(),NativeNetworkClient.active()||edit&&me!=null&&me.ready(),()->{if(NativeNetworkClient.active())NativeNetworkClient.resume();else NativeNetworkClient.request("lobby_ready",j->j.addProperty("ready",!me.confirmed()));});
        if(!NativeNetworkClient.active())start.setTooltip(Tooltip.create(L.component(me!=null&&me.ready()?L.s("Схватка начнётся, когда все игроки нажмут «Готов». Изменение настроек сбрасывает готовность."):L.s("Дождитесь проверки файлов"))));
        action(L.s("Назад"),lx+lw-secondary,height-42,secondary,false,true,this::onClose);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float d){super.extractBackground(g,mx,my,d);if(table())drawBoard(g,mx,my,d);}
    private void drawBoard(GuiGraphicsExtractor g,int mx,int my,float d){
        UiPaint.panel(g,lx-8,11,lw+16,height-22);
        g.fill(lx,23,lx+3,42,UiPaint.ACCENT);g.text(font,L.s("СХВАТКА"),lx+12,23,0xffe7f5fc,false);
        if(height>=300)g.text(font,font.plainSubstrByWidth(profile.name(),lw-130),lx+12,38,0xff8da7b8,false);
        String count=L.s("Игроки")+": "+shown.members().size()+" · NPC: "+shown.starts().stream().filter(s->s.difficulty()>=0).count();g.text(font,count,lx+lw-font.width(count),29,0xff9fb6c4,false);
        String[] headings={L.s("Игроки"),L.s("Цвет"),L.s("Генерал"),L.s("Команда"),L.s("Старт")};for(int i=0;i<5;i++)g.text(font,font.plainSubstrByWidth(headings[i],cols[i+1]-cols[i]-5),cols[i]+4,tableTop-14,0xff81adbf,false);
        g.fill(lx,tableTop-4,lx+tableW-3,tableTop-3,0xff285164);
        int populated=Math.min(visibleRows,Math.max(0,rosterCount()-rowOffset));for(int i=populated;i<visibleRows;i++){int y=tableTop+i*26;g.fill(lx,y,lx+tableW-3,y+23,0x50172530);g.text(font,"—",lx+9,y+7,0xff3e5868,false);}
        if(preview!=null){g.text(font,L.s("Карта"),mapX+3,57,0xff81adbf,false);preview.drawMap(g,mx,my,false);}
        if(rosterCount()>visibleRows){int total=rosterCount(),track=visibleRows*26;int bar=Math.max(12,track*visibleRows/total);int sy=tableTop+(track-bar)*rowOffset/Math.max(1,total-visibleRows);g.fill(lx+tableW-1,tableTop,lx+tableW+1,tableTop+track,0xff203845);g.fill(lx+tableW-1,sy,lx+tableW+1,sy+bar,0xff62bed6);}
        g.text(font,font.plainSubstrByWidth(L.s("Стартовые деньги: ")+"$"+shown.cash()+"   •   "+L.s("Лимит супероружия: ")+(shown.limitSuperweapons()?"1":L.s("Нет")),lw),lx,height-123,UiPaint.MUTED,false);
        String status=NativeNetworkClient.error.isEmpty()?NativeNetworkClient.message:NativeNetworkClient.error;
        if(!status.isBlank())g.text(font,font.plainSubstrByWidth(status,lw-4),lx,height-62,NativeNetworkClient.error.isEmpty()?0xff8fa8b8:0xffff9b93,false);
    }
    private void renderTable(GuiGraphicsExtractor g,int mx,int my,float d){
        super.extractRenderState(g,mx,my,d);
    }
    @Override public boolean mouseClicked(double x,double y,int b){if(table()&&!popup.isEmpty()){if(x>=popupLeft&&x<=popupLeft+popupWidth&&y>=popupTop&&y<=popupBottom){if(b==0)for(var option:List.copyOf(popup))if(option.active&&option.isMouseOver(x,y)){option.playDownSound(minecraft.getSoundManager());((LobbyCell)option).activate();return true;}return true;}closePopup();return true;}if(preview!=null&&preview.inside(x,y))return preview.mouseClicked(x,y,b);boolean handled=super.mouseClicked(x,y,b);if(!popup.isEmpty()&&!popup.contains(getFocused()))focusPopup();return handled;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(table()&&!popup.isEmpty()){
            if(key==com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE){closePopup();setFocused(popupOwner);return true;}
            if(key==com.mojang.blaze3d.platform.InputConstants.KEY_UP||key==com.mojang.blaze3d.platform.InputConstants.KEY_DOWN){int step=key==com.mojang.blaze3d.platform.InputConstants.KEY_UP?-1:1;popupFocus=Math.floorMod(popupIndices.getOrDefault(getFocused(),popupFocus)+step,popupChoices.size());if(popupFocus<popupPage||popupFocus>=popupPage+popupCapacity()){popupPage=popupFocus/popupCapacity()*popupCapacity();showPopup();}else focusPopup();return true;}
            if(key==com.mojang.blaze3d.platform.InputConstants.KEY_TAB){int step=(modifiers&1)!=0?-1:1;var enabled=popup.stream().filter(option->option.active).toList();if(!enabled.isEmpty())setFocused(enabled.get(Math.floorMod(enabled.indexOf(getFocused())+step,enabled.size())));return true;}
            if(key==com.mojang.blaze3d.platform.InputConstants.KEY_RETURN||key==com.mojang.blaze3d.platform.InputConstants.KEY_SPACE){if(getFocused() instanceof LobbyCell focused&&popup.contains(focused)&&focused.active){focused.playDownSound(minecraft.getSoundManager());focused.activate();}return true;}
        }if(key==84&&table()){minecraft.gui.setScreen(new NativeLobbyChatScreen(this));return true;}return super.keyPressed(key,scan,modifiers);
    }

    static String difficulty(int value){return value==0?L.s("лёгкая"):value==1?L.s("средняя"):L.s("сложная");}
    private static boolean sameSetup(NativeNetworkServer.Lobby a,NativeNetworkServer.Lobby b){return a!=null&&b!=null&&Objects.equals(a.leader(),b.leader())&&Objects.equals(a.dimension(),b.dimension())&&Objects.equals(a.members(),b.members())&&Objects.equals(a.starts(),b.starts())&&Objects.equals(a.message(),b.message())&&a.preparing()==b.preparing()&&a.cash()==b.cash()&&a.limitSuperweapons()==b.limitSuperweapons();}
    @Override public void tick(){var next=NativeNetworkClient.lobby;if(shown!=next){if(sameSetup(shown,next))shown=next;else rebuildWidgets();}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
        if(table()){renderTable(g,mx,my,d);return;}
        MenuButton.panel(g,width,height,460);super.extractRenderState(g,mx,my,d);g.centeredText(font,title,width/2,14,0xfff0d68a);
        int w=Math.min(460,width-24),x=(width-w)/2;
        if(profile!=null)g.centeredText(font,font.plainSubstrByWidth(profile.name(),w),width/2,33,0xffbbbbbb);
        String text=NativeNetworkClient.error.isEmpty()?NativeNetworkClient.message:NativeNetworkClient.error;
        g.enableScissor(x,height-62,x+w,height-36);g.textWithWordWrap(font,L.component(text),x,height-62,w,NativeNetworkClient.error.isEmpty()?0xffcccccc:0xffff8888);g.disableScissor();
        if(my>=height-62&&my<height-36&&!text.isEmpty())g.setTooltipForNextFrame(font,font.split(L.component(text),w),mx,my);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(table()){if(!popup.isEmpty()){int next=Math.clamp(popupPage-(int)v,0,Math.max(0,popupChoices.size()-popupCapacity()));if(next!=popupPage){popupPage=next;showPopup();}return v!=0;}if(preview!=null&&preview.inside(x,y))return preview.mouseScrolled(x,y,h,v);int next=Math.clamp(rowOffset-(int)v,0,Math.max(0,rosterCount()-visibleRows));if(next!=rowOffset){rowOffset=next;rebuildWidgets();}return v!=0;}return super.mouseScrolled(x,y,h,v);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    private static final class Options extends CompatScreen {
        final NativeLobbyScreen parent;NativeNetworkServer.Lobby current;int top,bottom,w;
        Options(NativeLobbyScreen parent){super(L.component(L.s("Параметры схватки")));this.parent=parent;}
        @Override protected void init(){
            current=NativeNetworkClient.lobby;w=Math.min(310,width-44);int x=(width-w)/2;int cardHeight=Math.min(height-14,240);top=(height-cardHeight)/2;bottom=top+cardHeight;
            if(current!=null){boolean editable=minecraft.player!=null&&!NativeNetworkClient.active()&&!current.preparing()&&current.leader().equals(minecraft.player.getUUID());
                String reason=NativeNetworkClient.active()?L.s("Меняется перед началом схватки"):current.preparing()?L.s("Проверка файлов…"):L.s("Настраивает только хозяин схватки");
                var cash=addRenderableWidget(MenuButton.styled(L.component(L.s("Стартовые деньги: ")+"$"+current.cash()),b->{int[] amounts={1000,5000,10000,20000,50000};minecraft.gui.setScreen(new GeneralsSetupScreen.Choices(this,L.s("Стартовые деньги"),Arrays.stream(amounts).mapToObj(n->"$"+n).toList(),List.of(),i->parent.matchSettings(amounts[i],current.limitSuperweapons())));}).choice().bounds(x,top+50,w,24).build());MenuButton.access(cash,editable,reason);
                var limit=addRenderableWidget(MenuButton.styled(L.component(L.s("Лимит супероружия: ")+(current.limitSuperweapons()?"1":L.s("Нет"))),b->parent.matchSettings(current.cash(),!current.limitSuperweapons())).toggle(current.limitSuperweapons()).bounds(x,top+82,w,24).build());MenuButton.access(limit,editable,reason);
            }
            addRenderableWidget(MenuButton.styled(L.component(L.s("Правила")),b->minecraft.gui.setScreen(new WorldSettingsScreen(this))).bounds(x,top+114,w,24).build());
            if(current!=null)addRenderableWidget(MenuButton.styled(L.component(parent.leaveLabel()),b->{boolean prompt=NativeNetworkClient.active()||current.leader().equals(minecraft.player.getUUID());parent.leave();if(!prompt)minecraft.gui.setScreen(parent);}).danger().bounds(x,top+146,w,20).build());
            addRenderableWidget(MenuButton.styled(L.component(L.s("Назад")),b->onClose()).quiet().bounds(x,bottom-30,w,20).build());
        }
        @Override public void tick(){if(current!=NativeNetworkClient.lobby)rebuildWidgets();}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){UiPaint.panel(g,(width-w)/2-16,top,w+32,bottom-top);super.extractRenderState(g,mx,my,d);g.centeredText(font,title,width/2,top+13,UiPaint.ACCENT);g.centeredText(font,current!=null&&minecraft.player!=null&&current.leader().equals(minecraft.player.getUUID())?L.s("Сохраняется сразу"):L.s("Настраивает только хозяин схватки"),width/2,top+28,UiPaint.MUTED);g.fill((width-w)/2,top+40,(width+w)/2,top+41,UiPaint.BORDER);g.fill((width-w)/2,bottom-58,(width+w)/2,bottom-57,UiPaint.BORDER);if(!NativeNetworkClient.error.isBlank()){g.text(font,font.plainSubstrByWidth(L.decode(NativeNetworkClient.error),w),(width-w)/2,bottom-50,0xffff8888,false);if(my>=bottom-52&&my<bottom-39)g.setTooltipForNextFrame(font,L.component(NativeNetworkClient.error),mx,my);}}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
}
