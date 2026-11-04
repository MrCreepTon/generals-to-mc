package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Visual menu tour in the existing QA world; no installation or world copies. */
final class NativeVersionMenusBench {
    static int stage,age,replies,guideCapture;static boolean confirmed,leavingRoom;static Screen parent;
    static boolean recoveryPreview(){return stage==11;}
    static Screen current(Minecraft mc){return NativeVersionScreenBench.current(mc);}
    static void show(Minecraft mc,Screen screen){NativeVersionScreenBench.show(mc,screen);age=0;stage++;}
    static void shot(Minecraft mc,String name)throws Exception{NativeVersionScreenBench.shot(mc,"beta18-"+name+".png");}
    static void check(boolean value,String label,List<String> checks){if(!value)throw new IllegalStateException(label);checks.add(label);}
    static void resizeMain(Minecraft mc,List<String> checks){
        var menu=(GeneralsSetupScreen)current(mc);NativeVersionScreenBench.initialize(mc,menu,320,240);menu.mouseScrolled(160,120,0,-100);
        NativeVersionScreenBench.initialize(mc,menu,640,360);
        check(menu.children().stream().filter(w->w instanceof Button b&&b.visible).count()<=5,"The main menu exposes at most five actions before opening tools or settings",checks);
        check(menu.children().stream().anyMatch(w->w instanceof MenuButton b&&b.kind==MenuButton.Kind.PRIMARY&&b.visible),"Resizing a scrolled main menu restores its primary action without another scroll",checks);
        NativeVersionScreenBench.show(mc,menu);
    }
    static void navigation(Minecraft mc,List<String> checks)throws Exception{
        var original=current(mc);var menu=new GeneralsSetupScreen(null);NativeVersionScreenBench.show(mc,menu);resizeMain(mc,checks);
        NativeVersionStyleBench.click(menu,NativeVersionStyleBench.button(menu,L.s("Мир и маркеры")));bounds(mc);
        var tools=current(mc);check(tools.children().stream().anyMatch(w->w instanceof MenuButton b&&b.getMessage().getString().equals(L.s("Debug меню"))&&b.visible&&b.active==NativeDebugScreen.available()&&(b.active||b.currentTooltip!=null)),"World tools keep Debug visible and explain skirmish restrictions",checks);
        check(NativeVersionStyleBench.button(tools,L.s("Найти маркеры"))!=null&&NativeVersionStyleBench.button(tools,L.s("Правила"))!=null,"Markers and rules share a dedicated world tools screen",checks);
        NativeVersionScreenBench.key(tools,com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);check(current(mc)==menu,"Escape returns from world tools to the same main menu",checks);
        NativeVersionStyleBench.click(menu,NativeVersionStyleBench.button(menu,L.s("Настройки и помощь")));bounds(mc);var settings=current(mc);
        check(NativeVersionStyleBench.button(settings,L.s("Управление"))!=null&&NativeVersionStyleBench.button(settings,L.s("Быстрый старт"))!=null,"Controls and quick start are grouped with game settings",checks);
        NativeVersionScreenBench.key(settings,com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);check(current(mc)==menu,"Escape returns from settings to the same main menu",checks);NativeVersionScreenBench.show(mc,original);
    }
    static void freshSetup(Minecraft mc,List<String> checks)throws Exception{
        String previous=System.getProperty("generalsmc.nativeRoot");var profile=NativeProfiles.active();
        var empty=NativeProfiles.root().resolve("empty-profile-menu");if(java.nio.file.Files.exists(empty.resolve("profiles.json")))throw new IllegalStateException("Fresh menu fixture already has profiles");
        try{System.setProperty("generalsmc.nativeRoot",empty.toString());var menu=new GeneralsSetupScreen(null);NativeVersionScreenBench.initialize(mc,menu,320,240);
            check(menu.children().stream().filter(w->w instanceof MenuButton b&&b.kind==MenuButton.Kind.PRIMARY&&b.active&&b.visible).count()==1,"A fresh profile has one visible primary action to connect the game",checks);
            var guide=new GeneralsGuide(null);guide.step=3;NativeVersionScreenBench.initialize(mc,guide,640,360);
            check(guide.children().stream().anyMatch(w->w instanceof MenuButton b&&b.getMessage().getString().equals(L.s("Открыть схватку"))&&!b.active&&b.currentTooltip!=null),"The first-run guide explains why skirmish is unavailable before connecting a game",checks);
        }finally{if(previous==null)System.clearProperty("generalsmc.nativeRoot");else System.setProperty("generalsmc.nativeRoot",previous);GeneralsSetupScreen.useProfile(profile);}
    }
    static void bounds(Minecraft mc){
        Screen screen=current(mc);for(int[] size:new int[][]{{320,240},{640,360}}){NativeVersionScreenBench.initialize(mc,screen,size[0],size[1]);
            var widgets=screen.children().stream().filter(w->w instanceof AbstractWidget a&&a.visible).map(w->(AbstractWidget)w).toList();
            for(var w:widgets)if(w.getX()<0||w.getY()<0||w.getRight()>size[0]||w.getBottom()>size[1])throw new IllegalStateException("Clipped menu control in "+screen.getClass()+": "+w.getMessage().getString());
            for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);if(a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY())throw new IllegalStateException("Overlapping menu controls in "+screen.getClass()+": "+a.getMessage().getString()+" / "+b.getMessage().getString());}
        }NativeVersionScreenBench.show(mc,screen);
    }
    static boolean tick(Minecraft mc,List<String> checks){try{
        age++;
        if(stage==0&&NativeNetworkClient.lobby!=null){if(!leavingRoom){
            NativeVersionScreenBench.show(mc,new NativeLobbyScreen(null));var lobby=current(mc);NativeVersionStyleBench.click(lobby,NativeVersionStyleBench.button(lobby,L.s("Параметры")));bounds(mc);var options=current(mc);var leave=options.children().stream().filter(w->w instanceof MenuButton b&&b.kind==MenuButton.Kind.DANGER&&b.active).map(w->(Button)w).findFirst().orElseThrow();NativeVersionStyleBench.click(options,leave);
            check(current(mc) instanceof MenuConfirmScreen,"Closing a lobby first shows the themed confirmation",checks);NativeVersionScreenBench.key(current(mc),com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);check(current(mc)==lobby&&NativeNetworkClient.lobby!=null,"Cancelling lobby closure keeps the room and its screen",checks);
            NativeVersionStyleBench.click(lobby,NativeVersionStyleBench.button(lobby,L.s("Параметры")));options=current(mc);leave=options.children().stream().filter(w->w instanceof MenuButton b&&b.kind==MenuButton.Kind.DANGER&&b.active).map(w->(Button)w).findFirst().orElseThrow();NativeVersionStyleBench.click(options,leave);var dialog=current(mc);var yes=dialog.children().stream().filter(w->w instanceof Button).map(w->(Button)w).findFirst().orElseThrow();NativeVersionStyleBench.click(dialog,yes);check(current(mc) instanceof GeneralsSetupScreen,"Confirming lobby closure returns to a usable menu",checks);leavingRoom=true;
        }return false;}
        if(stage==0){if(leavingRoom){if(age<5)return false;check(current(mc).children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(L.s("Свободная игра"))).map(w->(Button)w).findFirst().orElseThrow().active,"The menu unlocks after asynchronous lobby closure without reopening it",checks);}parent=new GeneralsSetupScreen(null);show(mc,parent);bounds(mc);return false;}if(age<16)return false;
        switch(stage){
            case 1->{if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modernui")){if(!(NativeVersionScreenBench.actual(mc) instanceof MenuScreens.Host host)||host.nativeWidgetCount()==0){if(age>100)throw new IllegalStateException("Modern UI native widget tree did not initialize");return false;}check(true,"Real Modern UI Fragment renders its native retained controls",checks);}shot(mc,"main");check(current(mc).children().stream().filter(w->w instanceof AbstractWidget a&&a.visible&&w instanceof Button).allMatch(w->w instanceof MenuButton),"The built-in theme styles the main menu without requiring Modern UI",checks);resizeMain(mc,checks);NativeVersionStyleBench.click(current(mc),NativeVersionStyleBench.button(current(mc),L.s("Мир и маркеры")));bounds(mc);stage=19;age=0;}
            case 19->{shot(mc,"world-tools");current(mc).onClose();NativeVersionStyleBench.click(current(mc),NativeVersionStyleBench.button(current(mc),L.s("Настройки и помощь")));bounds(mc);stage=20;age=0;}
            case 20->{shot(mc,"settings-help");NativeVersionScreenBench.show(mc,new NativeProfilesScreen(parent));bounds(mc);stage=2;age=0;}
            case 2->{freshSetup(mc,checks);shot(mc,"profiles");NativeVersionStyleBench.click(current(mc),NativeVersionStyleBench.button(current(mc),"✎"));bounds(mc);age=0;stage++;}
            case 3->{shot(mc,"connect-advanced");var screen=(GeneralsSetupScreen.Connect)current(mc);String path=screen.game.getValue();NativeVersionStyleBench.click(screen,NativeVersionStyleBench.button(screen,"←"));check(!screen.advanced&&path.equals(screen.game.getValue()),"Switching connection views preserves entered folder paths",checks);bounds(mc);stage++;age=0;}
            case 4->{shot(mc,"connect");show(mc,new NativeLaunchScreen(parent,NativeProfiles.active()));bounds(mc);}
            case 5->{shot(mc,"free-play");var point=new NativeNetworkServer.MapPoint(24,64,24,"start","Village base","Hold the eastern approach");show(mc,new MarkerDetailsScreen(parent,new NativeNetworkServer.MarkerDetails(point,null,true)));bounds(mc);var s=(MarkerDetailsScreen)current(mc);s.setFocused(s.name);}
            case 6->{shot(mc,"marker");check(((MarkerDetailsScreen)current(mc)).name instanceof MenuField,"Marker editors retain native text fields and themed keyboard focus",checks);show(mc,new NativeStartScreen(parent,new WorldSiteStore.Start(new BlockPos(24,64,24),0,"FactionChina",1,1,0),true));bounds(mc);}
            case 7->{shot(mc,"army");var guide=new GeneralsGuide(parent);guide.step=2;show(mc,guide);bounds(mc);}
            case 8->{if(Boolean.getBoolean("generalsmc.captureGuide")&&guideCapture<4){var guide=(GeneralsGuide)current(mc);if(guide.step!=guideCapture){guide.step=guideCapture;NativeVersionScreenBench.show(mc,guide);age=0;return false;}NativeVersionScreenBench.shot(mc,"beta23-guide-"+System.getProperty("generalsmc.testLanguage")+"-"+guideCapture+".png");guideCapture++;if(guideCapture<4){age=0;return false;}}shot(mc,"guide");show(mc,new NativeDebugScreen(parent));bounds(mc);}
            case 9->{shot(mc,"tools");show(mc,new MenuConfirmScreen(yes->{replies++;confirmed=yes;NativeVersionScreenBench.show(mc,parent);},L.component(L.s("Удалить маркер?")),L.component("Village base")));bounds(mc);}
            case 10->{shot(mc,"confirm");NativeVersionStyleBench.input(current(mc),"keyPressed",com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);check(replies==1&&!confirmed&&current(mc)==parent,"Escape cancels themed confirmations and returns to their parent",checks);replies=0;var dialog=new MenuConfirmScreen(yes->{replies++;confirmed=yes;NativeVersionScreenBench.show(mc,parent);},L.component(L.s("Удалить маркер?")),L.component("Village base"));NativeVersionScreenBench.show(mc,dialog);var yes=dialog.children().stream().filter(w->w instanceof Button).map(w->(Button)w).findFirst().orElseThrow();NativeVersionStyleBench.click(dialog,yes);NativeVersionStyleBench.click(dialog,yes);check(replies==1&&confirmed,"A repeated confirmation click submits the action once",checks);show(mc,new GeneralsFailureScreen(L.s("Папка игры перемещена или удалена."),NativeProfiles.root()));bounds(mc);}
            case 11->{shot(mc,"recovery");show(mc,new NativeMatchResultScreen(new NativeNetworkServer.MatchResult(true,false,List.of("Commander"))));bounds(mc);}
            case 12->{shot(mc,"result");check(true,"Connection, markers, army editor, guide, tools, confirmations, recovery and result controls fit compact and wide screens",checks);show(mc,new GeneralsSetupScreen.Help(parent));bounds(mc);}
            case 13->{shot(mc,"controls");show(mc,new ProfileStatusScreen(parent,NativeProfiles.active(),true));bounds(mc);}
            case 14->{shot(mc,"missing-game");show(mc,new MarkerFinderScreen(parent));bounds(mc);}
            case 15->{shot(mc,"markers");check(true,"Controls help, missing-game recovery and marker lists share the built-in theme",checks);show(mc,new NativeLobbyScreen(parent));bounds(mc);}
            case 16->{shot(mc,"lobby-empty");check(true,"The empty skirmish menu fits its panel at compact and wide GUI sizes",checks);NativeVersionScreenBench.show(mc,parent);NativeLobbyScreen.open(parent);age=0;stage++;}
            case 17->{if(NativeNetworkClient.lobby==null||NativeNetworkClient.lobby.preparing()||NativeNetworkClient.lobby.members().stream().anyMatch(m->!m.ready())){if(age>250)throw new IllegalStateException("Solo room did not prepare");return false;}check(!NativeNetworkClient.active()&&NativeNetworkClient.lobby.members().stream().noneMatch(m->m.confirmed()),"Opening solo skirmish creates a room but never starts before Ready",checks);NativeNetworkClient.leave();age=0;stage++;}
            case 18->{if(NativeNetworkClient.lobby!=null)return false;NativeVersionScreenBench.show(mc,parent);return true;}
        }return false;
    }catch(Exception e){throw new IllegalStateException("Menu visual tour case "+stage,e);}}
}
