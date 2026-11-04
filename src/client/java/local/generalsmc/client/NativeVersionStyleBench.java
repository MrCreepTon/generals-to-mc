package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Small real-client adapter probe; excluded from every player JAR. */
final class NativeVersionStyleBench {
 static java.util.concurrent.CompletableFuture<Boolean> selected(Screen screen,AbstractWidget source)throws Exception{
  var result=new java.util.concurrent.CompletableFuture<Boolean>();var f=screen.getClass().getDeclaredField("fragment");f.setAccessible(true);var fragment=f.get(screen);var r=fragment.getClass().getDeclaredField("root");r.setAccessible(true);Object root=r.get(fragment);if(root==null)throw new IllegalStateException("Modern UI root not attached");
  root.getClass().getMethod("post",Runnable.class).invoke(root,(Runnable)()->{try{var controls=root.getClass().getDeclaredField("controls");controls.setAccessible(true);var view=((java.util.Map<?,?>)controls.get(root)).get(source);result.complete((Boolean)view.getClass().getMethod("isSelected").invoke(view));}catch(Exception error){result.completeExceptionally(error);}});return result;
 }
    static int stage,age,menuStage=-1;static String lastFaction;
    static java.util.concurrent.CompletableFuture<Boolean> nativeSelected;
    static Screen screen(Minecraft mc){return NativeVersionScreenBench.current(mc);}
    static void check(boolean value,String label,List<String> checks){if(!value)throw new IllegalStateException(label);checks.add(label);}
    static Button button(Screen screen,String label){return screen.children().stream().filter(w->w instanceof Button b&&b.active&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("Missing style control: "+label));}
    static void input(Screen screen,String method,int a)throws Exception{NativeVersionScreenBench.key(screen,a);}
    static void click(Screen screen,Button button)throws Exception{NativeVersionScreenBench.click(screen,button);}
    static void next(int value){stage=value;age=0;}
    static boolean tick(Minecraft mc,List<String> checks){try{
        age++;if(age>300)throw new IllegalStateException("Style adapter timeout "+stage+": "+NativeNetworkClient.error);
        if(stage==0){NativeVersionScreenBench.show(mc,new NativeLobbyScreen(null));click(screen(mc),button(screen(mc),L.s("Создать комнату")));next(1);}
        else if(stage==1&&NativeNetworkClient.lobby!=null&&!NativeNetworkClient.lobby.preparing()&&NativeNetworkClient.lobby.members().stream().allMatch(NativeNetworkServer.Member::ready)&&age>5){
            NativeVersionMenusBench.navigation(mc,checks);
            String last=NativeProfiles.active().factions().getLast();lastFaction=last;var own=NativeNetworkClient.lobby.members().stream().filter(m->m.player().equals(mc.player.getUUID())).findFirst().orElseThrow();
            NativeNetworkClient.request("choose",j->{j.addProperty("faction",last);j.addProperty("color",own.color());j.addProperty("team",own.team());j.addProperty("start",own.start());});next(2);
        }else if(stage==2&&age>8&&NativeNetworkClient.lobby.members().stream().filter(m->m.player().equals(mc.player.getUUID())).anyMatch(m->m.faction().equals(lastFaction))){
            NativeVersionScreenBench.show(mc,new NativeLobbyScreen(null));
            for(int[] size:new int[][]{{320,240},{427,240},{960,240},{640,360}}){
                NativeVersionScreenBench.initialize(mc,screen(mc),size[0],size[1]);
                var widgets=screen(mc).children().stream().filter(w->w instanceof AbstractWidget a&&a.visible).map(w->(AbstractWidget)w).toList();
                for(var a:widgets)if(a.getX()<0||a.getY()<0||a.getRight()>size[0]||a.getBottom()>size[1])throw new IllegalStateException("Clipped control: "+a.getMessage().getString());
                for(int i=0;i<widgets.size();i++)for(int k=i+1;k<widgets.size();k++){var a=widgets.get(i);var b=widgets.get(k);if(a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY())throw new IllegalStateException("Overlapping adapter controls");}
            }
            check(true,"Styled lobby controls fit four compact and wide viewport sizes",checks);
            NativeVersionScreenBench.show(mc,new NativeLobbyScreen(null));next(3);
        }else if(stage==3&&age>5){
            String name=GeneralsSetupScreen.friendly(NativeProfiles.active().factions().getLast());click(screen(mc),button(screen(mc),name));
            check(screen(mc).getFocused() instanceof LobbyCell c&&c.option&&c.selected&&c.getMessage().getString().equals(name),"The current general opens selected and focused on its own dropdown page",checks);
            input(screen(mc),"keyPressed",com.mojang.blaze3d.platform.InputConstants.KEY_DOWN);
            check(screen(mc).getFocused() instanceof LobbyCell c&&c.getMessage().getString().equals(GeneralsSetupScreen.friendly("")),"Down wraps from the last general to Random across dropdown pages",checks);
            input(screen(mc),"keyPressed",com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
            check(screen(mc).children().stream().filter(w->w instanceof LobbyCell c&&c.popover).findAny().isEmpty(),"Escape dismisses the popover and restores the roster",checks);
            check(screen(mc).children().stream().filter(w->w instanceof MenuButton b&&b.kind==MenuButton.Kind.PRIMARY&&b.active).count()==1,"The editable lobby presents one enabled primary action",checks);
            NativeVersionMenusBench.shot(mc,"roster");click(screen(mc),button(screen(mc),L.s("Карта")));next(32);
        }else if(stage==32&&age>5){
            check(screen(mc) instanceof StartMapScreen map&&!map.resources,"Lobby opens its combined map in Starts mode",checks);NativeVersionMenusBench.shot(mc,"map-starts");click(screen(mc),button(screen(mc),L.s("Ресурсы")));next(33);
        }else if(stage==33&&age>5){
            check(screen(mc) instanceof StartMapScreen map&&map.resources,"Resources are one tab away even without placed starts",checks);NativeVersionMenusBench.shot(mc,"map-resources");screen(mc).onClose();click(screen(mc),button(screen(mc),L.s("Параметры")));NativeVersionMenusBench.bounds(mc);next(34);
        }else if(stage==34&&age>5){
            NativeVersionMenusBench.shot(mc,"match-settings");var cash=screen(mc).children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().startsWith(L.s("Стартовые деньги: "))).map(w->(Button)w).findFirst().orElseThrow();click(screen(mc),cash);click(screen(mc),button(screen(mc),"$20000"));next(30);
        }else if(stage==30&&age>5&&NativeNetworkClient.lobby.cash()==20000){
            if(!NativeNetworkClient.lobby.limitSuperweapons()){var limit=screen(mc).children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().startsWith(L.s("Лимит супероружия: "))).map(w->(Button)w).findFirst().orElseThrow();click(screen(mc),limit);}next(31);
        }else if(stage==31&&age>5&&NativeNetworkClient.lobby.limitSuperweapons()){
            check(!NativeNetworkClient.active(),"Cash and superweapon settings round-trip without starting the room",checks);click(screen(mc),button(screen(mc),L.s("Правила")));next(4);
        }else if(stage==4&&NativeNetworkClient.rules!=null&&age>5){
            check(screen(mc).children().stream().noneMatch(w->w instanceof MenuButton b&&b.kind==MenuButton.Kind.TAB),"Rules start with common choices and hide advanced category tabs",checks);
            NativeVersionMenusBench.shot(mc,"rules-basic");click(screen(mc),button(screen(mc),L.s("Дополнительно…")));next(40);
        }else if(stage==40&&age>5){
            if(NativeVersionScreenBench.actual(mc) instanceof MenuScreens.Host){
                if(nativeSelected==null){var tab=screen(mc).children().stream().filter(w->w instanceof MenuButton b&&b.kind==MenuButton.Kind.TAB&&b.selected).map(w->(AbstractWidget)w).findFirst().orElseThrow();NativeVersionScreenBench.shot(mc,"beta23-rules-selected.png");nativeSelected=selected(NativeVersionScreenBench.actual(mc),tab);return false;}
                if(!nativeSelected.isDone())return false;
                check(nativeSelected.join(),"Real Modern UI controls preserve the selected rules tab",checks);
            }
            check(screen(mc) instanceof WorldSettingsScreen&&screen(mc).children().stream().anyMatch(w->w instanceof MenuButton b&&b.kind==MenuButton.Kind.TAB&&b.selected),"Rule tabs expose the active category in the styled adapter",checks);
            next(5);
        }else if(stage==5){if(menuStage!=NativeVersionMenusBench.stage){menuStage=NativeVersionMenusBench.stage;age=0;}return NativeVersionMenusBench.tick(mc,checks); }
        return false;
    }catch(Exception e){throw new IllegalStateException("Version style probe",e);}}
}
