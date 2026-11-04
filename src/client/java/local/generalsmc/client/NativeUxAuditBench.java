package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Focused newcomer journey in the existing owned QA world. Never shipped to players. */
final class NativeUxAuditBench {
    static int stage,age;static Screen parent;static StartMapScreen map;static MarkerFinderScreen finder;
    static CompletableFuture<?> work;static int[] pixels;static double center;static final BlockPos START=new BlockPos(0,64,0);
    static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("Newcomer UX case {}",value);}
    static void check(boolean ok,String text,List<String> checks){if(!ok)throw new IllegalStateException(text);checks.add(text);}
    static void click(Screen screen,String text){NativePolishBench.click(screen,NativePolishBench.button(screen,L.s(text)));}
    static void bounds(Screen screen){
        NativeSetupBench.bounds(screen);var widgets=screen.children().stream().filter(w->w instanceof AbstractWidget a&&a.visible).map(w->(AbstractWidget)w).toList();
        for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);if(a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY())throw new IllegalStateException("Overlapping controls: "+a.getMessage().getString()+" / "+b.getMessage().getString());}
    }
    static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"beta17-"+name+".png",mc.getMainRenderTarget(),c->{});}
    static boolean tick(Minecraft mc,List<String> checks)throws Exception{
        age++;if(age>300)throw new IllegalStateException("Newcomer UX timeout "+stage+": "+NativeNetworkClient.error);
        switch(stage){
            case 0->{
                parent=new GeneralsSetupScreen(null);mc.setScreen(parent);var empty=new GeneralsSetupScreen.Choices(parent,"Empty",List.of(),List.of(),i->{});empty.init(mc,320,120);check(empty.options.isEmpty(),"An empty choice list stays valid at the smallest viewport",checks);NativePolishBench.button(parent,L.s("Свободная игра"));NativePolishBench.button(parent,L.s("Схватка"));
                var guide=new GeneralsGuide(parent);guide.step=2;mc.setScreen(guide);click(guide,"Найти маркеры");check(MenuScreens.active(mc.screen) instanceof MarkerFinderScreen,"Guide prepares markers before launching the skirmish",checks);MenuScreens.active(mc.screen).onClose();check(MenuScreens.active(mc.screen)==guide,"Marker finder returns to the same guide step",checks);
                guide.step=3;mc.setScreen(guide);click(guide,"Открыть схватку");check(MenuScreens.active(mc.screen) instanceof NativeLobbyScreen,"Final guide step opens the skirmish lobby",checks);check(!NativePolishBench.button(MenuScreens.active(mc.screen),L.s("Присоединиться")).active,"An empty singleplayer world does not offer a dead-end Join action",checks);
                click(MenuScreens.active(mc.screen),"Создать комнату");next(1);
            }
            case 1->{if(NativeNetworkClient.lobby!=null&&!NativeNetworkClient.lobby.preparing()&&NativeNetworkClient.lobby.members().stream().allMatch(NativeNetworkServer.Member::ready)&&age>5){
                var own=NativePolishBench.own(mc);String last=NativeProfiles.active().factions().getLast();NativeNetworkClient.request("choose",j->{j.addProperty("faction",last);j.addProperty("color",own.color());j.addProperty("team",own.team());j.addProperty("start",own.start());});next(108);
            }}
            case 108->{if(NativePolishBench.own(mc).faction().equals(NativeProfiles.active().factions().getLast())&&age>5){
                var lobby=new NativeLobbyScreen(parent);mc.setScreen(lobby);
                for(int[] size:new int[][]{{320,240},{427,240},{960,240},{640,360}}){lobby.init(mc,size[0],size[1]);bounds(lobby);check(lobby.children().stream().filter(w->w instanceof LobbyCell).count()>=10,"Compact roster and fixed NPC action fit "+size[0]+"x"+size[1],checks);check(NativePolishBench.button(lobby,"+ NPC").active,"NPC action stays visible at "+size[0]+"x"+size[1],checks);}
                mc.options.guiScale().set(3);mc.resizeDisplay();mc.setScreen(new NativeLobbyScreen(parent));next(101);
            }}
            case 101->{if(age>5){shot(mc,"compact-lobby");
                var cell=MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof LobbyCell c&&c.dropdown&&c.getMessage().getString().equals(L.s("Случайный"))).map(w->(Button)w).findFirst().orElseGet(()->MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof LobbyCell c&&c.dropdown&&c.getX()>100).map(w->(Button)w).findFirst().orElseThrow());
                NativePolishBench.click(MenuScreens.active(mc.screen),cell);var popup=NativePolishBench.popup((NativeLobbyScreen)MenuScreens.active(mc.screen));check(!popup.isEmpty(),"Vanilla and Modern UI rosters both open an in-place choice list",checks);check(MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof AbstractWidget a&&!popup.contains(a)).map(w->(AbstractWidget)w).noneMatch(w->w.active||w.getTooltip()!=null),"The popup disables all controls behind it",checks);MenuScreens.active(mc.screen).keyPressed(256,0,0);check(NativePolishBench.popup((NativeLobbyScreen)MenuScreens.active(mc.screen)).isEmpty(),"Escape closes the choice list without leaving the lobby",checks);
                var own=NativePolishBench.own(mc);NativePolishBench.click(MenuScreens.active(mc.screen),NativePolishBench.button(MenuScreens.active(mc.screen),GeneralsSetupScreen.friendly(own.faction())));next(109);
            }}
            case 109->{if(age>5){shot(mc,"general-picker");var own=NativePolishBench.own(mc);var chosen=(Button)MenuScreens.active(mc.screen).getFocused();if(MenuButton.MODERN)check(chosen.getTooltip()==null,"Readable popup choices do not cover adjacent rows with redundant tooltips",checks);check(chosen instanceof LobbyCell c&&c.option&&c.selected&&chosen.getMessage().getString().equals(GeneralsSetupScreen.friendly(own.faction())),"Opening the general picker focuses the actual current choice on its own page",checks);MenuScreens.active(mc.screen).keyPressed(com.mojang.blaze3d.platform.InputConstants.KEY_DOWN,0,0);check(((Button)MenuScreens.active(mc.screen).getFocused()).getMessage().getString().equals(GeneralsSetupScreen.friendly("")),"Arrow navigation wraps across dropdown pages without focusing pagination controls",checks);MenuScreens.active(mc.screen).keyPressed(256,0,0);
                var stable=MenuScreens.active(mc.screen).children().getFirst();MenuScreens.active(mc.screen).mouseScrolled(0,0,0,1000);check(MenuScreens.active(mc.screen).children().getFirst()==stable,"Scrolling an already-reached roster edge does not rebuild the screen",checks);
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new NativeLobbyScreen(parent));click(MenuScreens.active(mc.screen),"+ NPC");next(2);
            }}
            case 2->{if(NativeNetworkClient.lobby.starts().stream().filter(s->s.difficulty()>=0).count()>=2&&age>4){
                map=new StartMapScreen(MenuScreens.active(mc.screen),NativeNetworkClient.lobby.starts(),-1,s->{});mc.setScreen(map);next(3);
            }}
            case 3->{if(map.terrain!=null&&age>5){
                pixels=map.terrain;center=map.cx;map.terrainAt=System.currentTimeMillis();int x=map.left+map.side/2,y=map.top+map.side/2;
                map.mouseClicked(x,y,0);for(int i=1;i<=12;i++)map.mouseDragged(x+i,y,0,1,0);map.mouseReleased(x+12,y,0);
                check(map.cx<center&&map.terrain==pixels,"Map dragging moves immediately without discarding the sampled terrain on every mouse event",checks);next(4);
            }}
            case 4->{if(age>5){
                check(map.terrain!=pixels&&Math.abs(map.terrainCx-map.cx)<.001,"Dragged terrain refreshes at its new map position",checks);shot(mc,"map");finder=new MarkerFinderScreen(parent);mc.setScreen(finder);next(5);
            }}
            case 5->{if(NativeNetworkClient.markers!=null&&age>4){
                var member=NativePolishBench.own(mc);NativeNetworkClient.request("choose",j->{j.addProperty("faction",member.faction());j.addProperty("color",member.color());j.addProperty("team",member.team());j.addProperty("start",0);});
                check(finder.displayed.equals(finder.points()),"Marker finder caches the correctly sorted visible list",checks);
                NativeNetworkClient.request("marker_info",j->{j.addProperty("x",0);j.addProperty("y",64);j.addProperty("z",0);});next(6);
            }}
            case 6->{if(MenuScreens.active(mc.screen) instanceof MarkerDetailsScreen details){
                details.name.setValue("Newcomer base");details.note.setValue("Saved with Back");click(details,"Назад");check(MenuScreens.active(mc.screen)==details&&details.saving,"Back waits for the server to acknowledge edited marker text",checks);next(7);
            }}
            case 7->{if(MenuScreens.active(mc.screen)==finder&&age>4){
                work=CompletableFuture.runAsync(()->{var store=WorldSiteStore.get(mc.getSingleplayerServer().getLevel(mc.level.dimension()));if(!store.annotation(START).note().equals("Saved with Back"))throw new IllegalStateException("Back discarded marker text");},mc.getSingleplayerServer());next(8);
            }}
            case 8->{if(work.isDone()){
                work.join();check(true,"Marker edits survive closing the menu and persist in server-owned world data",checks);NativeNetworkClient.request("marker_info",j->{j.addProperty("x",0);j.addProperty("y",64);j.addProperty("z",0);});next(9);
            }}
            case 9->{if(MenuScreens.active(mc.screen) instanceof MarkerDetailsScreen details){
                details.note.setValue("First submission");click(details,"Сохранить");details.note.setValue("Latest edit wins");details.onClose();next(10);
            }}
            case 10->{if(MenuScreens.active(mc.screen)==finder&&age>4){
                work=CompletableFuture.runAsync(()->{if(!WorldSiteStore.get(mc.getSingleplayerServer().getLevel(mc.level.dimension())).annotation(START).note().equals("Latest edit wins"))throw new IllegalStateException("An in-flight save lost the latest text");},mc.getSingleplayerServer());next(11);
            }}
            case 11->{if(work.isDone()){
                work.join();check(true,"Closing during an in-flight save also preserves edits typed after the first submission",checks);
                NativeNetworkClient.request("marker_info",j->{j.addProperty("x",0);j.addProperty("y",64);j.addProperty("z",0);});next(12);
            }}
            case 12->{if(MenuScreens.active(mc.screen) instanceof MarkerDetailsScreen details){
                click(details,"Параметры армии");var start=(NativeStartScreen)MenuScreens.active(mc.screen);bounds(start);var color=start.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().startsWith(L.s("Цвет: "))).map(w->(Button)w).findFirst().orElseThrow();NativePolishBench.click(start,color);check(MenuScreens.active(mc.screen) instanceof GeneralsSetupScreen.Choices,"A start marker exposes the NPC colour picker",checks);MenuScreens.active(mc.screen).onClose();start.onClose();details.note.setValue("Unsaved draft before deletion");click(details,"Удалить маркер…");var confirm=(Button)MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof Button).findFirst().orElseThrow();NativePolishBench.click(MenuScreens.active(mc.screen),confirm);next(13);
            }}
            case 13->{if(MenuScreens.active(mc.screen)==finder&&NativeNetworkClient.markers.points().stream().noneMatch(p->p.x()==0&&p.z()==0)&&age>5){
                check(NativePolishBench.own(mc).start()==-1,"Deleting an assigned start resets the player to automatic start selection",checks);
                check(NativeNetworkClient.error.isEmpty(),"Deleting a marker with draft text does not try to save the deleted marker",checks);
                var profile=NativeProfiles.active();var broken=new NativeProfiles.Profile(profile.id(),profile.name(),profile.game(),profile.base(),profile.mod(),false,List.of(),List.of(),profile.checkedAt(),profile.engineHash(),profile.factionNames(),false);
                mc.setScreen(new NativeLaunchScreen(parent,broken));bounds(MenuScreens.active(mc.screen));check(MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof Button).map(w->(Button)w).anyMatch(b->b.getMessage().getString().equals(L.s("Исправить подключение"))),"Incomplete cached game metadata offers repair instead of crashing the launch picker",checks);
                var old=ArmyPreferences.get(profile);ArmyPreferences.save(profile,new ArmyPreferences.Choice(old.faction(),999999,old.team(),old.start()));check(ArmyPreferences.get(profile).color()==-1,"A colour removed by an updated game profile falls back to Random",checks);ArmyPreferences.save(profile,old);
                var stale=new MarkerDetailsScreen(finder,new NativeNetworkServer.MarkerDetails(new NativeNetworkServer.MapPoint(0,64,0,"start","Stale marker",""),null,true));mc.setScreen(stale);stale.note.setValue("Draft retained after rejection");stale.onClose();next(17);
            }}
            case 17->{if(MenuScreens.active(mc.screen) instanceof MarkerDetailsScreen stale&&stale.saveFailed&&age>4){
                check(stale.note.getValue().equals("Draft retained after rejection")&&!NativeNetworkClient.error.isEmpty(),"A rejected marker save keeps the draft and shows the server error",checks);click(stale,"Не сохранять");check(NativeNetworkClient.error.isEmpty(),"Dismissing a rejected save clears its stale error from unrelated menus",checks);check(MenuScreens.active(mc.screen)==finder,"Discard lets the user leave an editor after a rejected save",checks);NativeNetworkClient.request("marker_points");mc.setScreen(new NativeLobbyScreen(parent));next(14);
            }}
            case 14->{if(age>5){shot(mc,"lobby");mc.setScreen(new WorldSettingsScreen(parent));next(15);}}
            case 15->{if(NativeNetworkClient.rules!=null&&age>5){
                click(MenuScreens.active(mc.screen),"Мобы");mc.options.guiScale().set(3);mc.resizeDisplay();bounds(MenuScreens.active(mc.screen));MenuScreens.active(mc.screen).mouseScrolled(50,100,0,-1000);bounds(MenuScreens.active(mc.screen));next(16);
            }}
            case 16->{if(age>5){
                shot(mc,"rules");check(MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof AbstractWidget a&&a.visible&&a.getY()>=80&&a.getBottom()<=MenuScreens.active(mc.screen).height-58).count()>=1,"Long rule labels wrap and the last mob options remain reachable on a compact screen",checks);
                var original=NativeNetworkClient.rules;NativeNetworkClient.rules=new NativeNetworkServer.RulesState(original.values(),false,original.resourcesLocked());mc.setScreen(new WorldSettingsScreen(parent));NativeNetworkClient.rules=new NativeNetworkServer.RulesState(original.values(),false,original.resourcesLocked());MenuScreens.active(mc.screen).init(mc,320,240);check(MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof AbstractWidget a&&a.visible&&a.getY()>=80&&a.getBottom()<=MenuScreens.active(mc.screen).height-58).map(w->(AbstractWidget)w).noneMatch(w->w.active),"Rules presented to a non-host have no active mutation controls",checks);NativeNetworkClient.rules=original;mc.options.guiScale().set(2);mc.resizeDisplay();next(18);
            }}
            case 18->{return NativeVersionMenusBench.tick(mc,checks);}
        }return false;
    }
}
