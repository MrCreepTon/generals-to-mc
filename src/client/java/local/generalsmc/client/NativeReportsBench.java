package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Reuses the owned QA world; real lobby packets and original render snapshots. */
final class NativeReportsBench {
 static int roster,age,stage,unit,building,base,samples,worker;static double minOffset=Double.POSITIVE_INFINITY,maxOffset=Double.NEGATIVE_INFINITY;
 static final List<String> checks=NativePolishBench.checks;
 static void check(boolean value,String text){NativePolishBench.check(value,text);}
 static void next(int s){stage=s;age=0;}
 static boolean roster(Minecraft mc)throws Exception{
  age++;if(age>240)throw new IllegalStateException("Ready/chat timeout "+roster);
  if(roster==0){if(Boolean.getBoolean("generalsmc.latestRegression")&&NativeLatestBench.work==null)NativeLatestBench.prepare(mc);if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modernui")){if(!NativeModernWidgetBench.ready(mc.screen))return false;check(mc.screen instanceof MenuScreens.Host,"Real Modern UI Fragment and retained native widgets are active");}check(!NativeNetworkClient.active()&&!NativeNetworkClient.lobby.preparing(),"File verification alone does not start the match");check(NativeNetworkClient.lobby.members().stream().noneMatch(NativeNetworkServer.Member::confirmed),"Players initially wait for manual Ready");NativeNetworkClient.request("start",j->{});roster=1;age=0;}
  else if(roster==1&&!NativeNetworkClient.error.isEmpty()){check(!NativeNetworkClient.active(),"Host cannot bypass manual Ready with a direct start packet");NativeNetworkClient.error="";if(mc.screen instanceof MenuScreens.Host)NativeModernWidgetBench.action(mc.screen,NativePolishBench.button(MenuScreens.active(mc.screen),L.s("Чат")),null);else NativeSetupBench.click(MenuScreens.active(mc.screen),"Чат");roster=11;age=0;}
  else if(roster==11&&MenuScreens.active(mc.screen) instanceof NativeLobbyChatScreen){if(mc.screen instanceof MenuScreens.Host&&!NativeModernWidgetBench.ready(mc.screen))return false;check(true,"Lobby chat opens through its actual menu button");var field=MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).findFirst().orElseThrow();if(mc.screen instanceof MenuScreens.Host){NativeModernWidgetBench.action(mc.screen,field,"Hello from the skirmish lobby");roster=12;}else{field.setValue("Hello from the skirmish lobby");MenuScreens.active(mc.screen).keyPressed(257,0,0);field.setValue("Keep my next draft");roster=2;}age=0;}
  else if(roster==12&&age>5){var field=MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).findFirst().orElseThrow();var send=NativePolishBench.button(MenuScreens.active(mc.screen),L.s("Отправить"));if(send.active&&field.getValue().equals("Hello from the skirmish lobby")){NativeModernWidgetBench.action(mc.screen,send,null);check(true,"Actual Modern UI EditText preserves rapid edits and its native Button sends authenticated room chat");roster=2;age=0;}}
  else if(roster==2&&age>10&&NativeNetworkClient.lobby.chat().stream().anyMatch(m->m.text().equals("Hello from the skirmish lobby"))){check(NativeNetworkClient.lobby.chat().getLast().name().equals(mc.player.getName().getString()),"Actual Minecraft server echoes room chat with the authenticated player name");if(!(mc.screen instanceof MenuScreens.Host))check(MenuScreens.active(mc.screen).children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).anyMatch(w->w.getValue().equals("Keep my next draft")),"Chat acknowledgement preserves a newer unsent draft");Screenshot.grab(mc.gameDirectory,"beta20-chat.png",mc.getMainRenderTarget(),c->{});MenuScreens.active(mc.screen).onClose();NativeNetworkClient.request("lobby_chat",j->j.addProperty("text","bad\nmessage"));roster=3;age=0;}
  else if(roster==3&&!NativeNetworkClient.error.isEmpty()&&age>15){check(NativeNetworkClient.lobby.chat().stream().noneMatch(m->m.text().contains("\n")),"Server rejects control characters in room chat");NativeNetworkClient.error="";var s=(NativeLobbyScreen)MenuScreens.active(mc.screen);var own=NativePolishBench.own(mc);NativePolishBench.click(s,NativePolishBench.button(s,GeneralsSetupScreen.friendly(own.faction())));check(!NativePolishBench.popup(s).isEmpty(),"General picker is open before a new room message");NativeNetworkClient.request("lobby_chat",j->j.addProperty("text","Keep this list open"));roster=4;age=0;}
  else if(roster==4&&NativeNetworkClient.lobby.chat().stream().anyMatch(m->m.text().equals("Keep this list open"))){check(!NativePolishBench.popup((NativeLobbyScreen)MenuScreens.active(mc.screen)).isEmpty(),"Actual incoming room chat preserves the open picker and keyboard focus");MenuScreens.active(mc.screen).keyPressed(256,0,0);check(!NativeDebugScreen.available(),"Creative Debug remains unavailable in a skirmish lobby");mc.setScreen(new GeneralsSetupScreen(null));NativeVersionMenusBench.navigation(mc,checks);mc.setScreen(new NativeLobbyScreen(null));roster=41;age=0;}
  else if(roster==41&&age>12){Screenshot.grab(mc.gameDirectory,"beta20-ready.png",mc.getMainRenderTarget(),c->{});return true;}
  return false;
 }
 static void begin(Minecraft mc)throws Exception{
  age=stage=0;var v=NativeWorldView.current;v.fog.enabled(false);v.cameraX=10;v.cameraZ=-10;v.zoom=26;v.cameraYaw=0;
  mc.getSingleplayerServer().execute(()->NativePolishBench.level(mc).setWeatherParameters(6000,0,false,false));
  base=v.state.objects().stream().filter(a->a.owner()==v.state.localPlayerIndex()&&a.structure()).findFirst().orElseThrow().id();
  NativePolishBench.spawn("ChinaTankBattleMaster",20,-8,0);NativePolishBench.spawn("AmericaVehicleDozer",20,5,0);
  check(v.state.inGame(),"Pressing Ready starts the actual NPC skirmish");
 }
 static boolean tick(Minecraft mc)throws Exception{
  if(Boolean.getBoolean("generalsmc.latestRegression")&&NativeLatestBench.began)return NativeLatestBench.tick(mc);
  age++;if(age>800)throw new IllegalStateException("Report render timeout "+stage);var v=NativeWorldView.current;
  switch(stage){
   case 0->{unit=NativePolishBench.find("ChinaTankBattleMaster");worker=NativePolishBench.find("AmericaVehicleDozer");if(unit>0&&worker>0&&age>15){v.session.send("TEST_SQUAD "+unit+" 1");var p=v.origin.nativePoint(new Vec3(25,64,5));v.session.send("TEST_CONSTRUCT "+worker+" AmericaPowerPlant "+p.x+" "+p.y+" "+p.z);next(10);}}
   case 10->{building=NativePolishBench.find("AmericaPowerPlant");var actor=v.actor(building);if(actor!=null&&actor.constructionPercent()>=50&&actor.constructionPercent()<100){check(v.sceneActors().contains(building),"Actual builder construction exports its scaffold halfway through construction");next(1);}}
   case 1->{if(age>30&&v.state.overheadImages().stream().anyMatch(b->b.id()==unit)){check(v.state.overheadImages().stream().filter(b->b.id()==unit).allMatch(b->b.tw()>0&&b.th()>0),"Original control-group font atlas is available to per-frame world UI");Screenshot.grab(mc.gameDirectory,"beta20-scaffold.png",mc.getMainRenderTarget(),c->{});next(2);}}
   case 2->{v.cameraX+=.15;if(age==25)Screenshot.grab(mc.gameDirectory,"beta20-scaffold-pan.png",mc.getMainRenderTarget(),c->{});if(age>50){v.zoom=60;v.cameraX=55;v.cameraZ=35;next(3);}}
   case 3->{if(age>35){check(v.sceneActors().contains(base),"Own command center remains exported at far zoom outside the former fixed 100-block scene");Screenshot.grab(mc.gameDirectory,"beta20-far-zoom.png",mc.getMainRenderTarget(),c->{});v.cameraYaw=90;next(4);}}
   case 4->{if(age>35){check(v.sceneActors().contains(base),"Camera rotation keeps own building geometry at far zoom");NativeRtsController.toggle();next(5);}}
   case 5->{if(age>15){check(!NativeWorldView.controlling(),"F8 returns to Minecraft while the army keeps running");var s=new SiteMarkerEntity(new BlockPos(0,64,0),SiteBlocks.SUPPLY.defaultBlockState());var o=new SiteMarkerEntity(new BlockPos(0,64,0),SiteBlocks.OIL.defaultBlockState());check(StartAreaPreview.radius(s)==9&&StartAreaPreview.radius(o)==4,"Supply and oil markers expose permanent clearance radii");if(Boolean.getBoolean("generalsmc.latestRegression")){NativeLatestBench.begin(mc);return false;}return true;}}
  }return false;
 }
}
