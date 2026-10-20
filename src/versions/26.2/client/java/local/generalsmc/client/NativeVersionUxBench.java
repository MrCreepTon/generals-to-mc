package local.generalsmc.client;
import local.generalsmc.*;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.concurrent.*;
final class NativeVersionUxBench {
 static int stage,age;static CompletableFuture<?> work;static final BlockPos FAR=new BlockPos(8192,64,8192),SUPPLY=new BlockPos(24,64,24);
 static void next(int s){stage=s;age=0;GeneralsMod.LOG.info("Version UX case {}",s);}
 static void marker(String kind,BlockPos p){NativeNetworkClient.request(kind,j->{j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());});}
 static void click(Minecraft mc,String label){NativeVersionBench.click(mc,label);}
 static boolean tick(Minecraft mc,List<String> checks){age++;if(age>500)throw new IllegalStateException("Version UX timeout "+stage+": "+NativeNetworkClient.error);
  if(stage==0){work=CompletableFuture.runAsync(()->{var l=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);if(l.getChunkSource().getChunkNow(FAR.getX()>>4,FAR.getZ()>>4)!=null)throw new IllegalStateException("Remote chunk is already loaded");WorldSiteStore.get(l).putStart(FAR,2,"",2,-1);},mc.getSingleplayerServer());next(1);}
  else if(stage==1&&work.isDone()&&!NativeRtsController.hasSession()&&age>30){work.join();mc.gui.setScreen(new MarkerFinderScreen(null));marker("marker_info",SUPPLY);next(2);}
  else if(stage==2&&mc.gui.screen() instanceof MarkerDetailsScreen s){s.name.setValue("Village supplies");s.note.setValue("Reserve for defence");click(mc,"Сохранить");next(3);}
  else if(stage==3&&mc.gui.screen() instanceof MarkerDetailsScreen s&&!s.status.isEmpty()&&age>10){work=CompletableFuture.runAsync(()->{var l=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var store=WorldSiteStore.get(l);if(!store.annotation(SUPPLY).note().equals("Reserve for defence"))throw new IllegalStateException("Note did not save");try{var f=WorldSiteStore.class.getDeclaredField("FACTORY");f.setAccessible(true);var type=(net.minecraft.world.level.saveddata.SavedDataType<WorldSiteStore>)f.get(null);var tag=type.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,store).getOrThrow();var restored=type.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE,tag).getOrThrow();if(!restored.annotation(SUPPLY).equals(store.annotation(SUPPLY)))throw new IllegalStateException("SavedData codec lost marker metadata");}catch(ReflectiveOperationException e){throw new CompletionException(e);}},mc.getSingleplayerServer());click(mc,"Назад");next(4);}
  else if(stage==4&&work.isDone()){work.join();checks.add("Marker note editing travels through real Fabric requests and round-trips the version's SavedData codec");marker("marker_info",FAR);next(5);}
  else if(stage==5&&mc.gui.screen() instanceof MarkerDetailsScreen){click(mc,"Параметры армии");click(mc,L.s("Участник: ")+L.s("игрок"));var button=(net.minecraft.client.gui.components.Button)mc.gui.screen().children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(L.s("Участник: ")+L.s("NPC — лёгкий"))).findFirst().orElseThrow();var e=new net.minecraft.client.input.MouseButtonEvent(button.getX()+3,button.getY()+3,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT,0));mc.gui.screen().mouseClicked(e,false);mc.gui.screen().mouseReleased(e);click(mc,L.s("Участник: ")+L.s("игрок"));checks.add("Actual right mouse input cycles participant choices backwards");click(mc,"Сохранить");next(6);}
  else if(stage==6&&mc.gui.screen() instanceof MarkerDetailsScreen s&&s.details.start().difficulty()==0){checks.add("An unloaded saved army marker can be changed to an NPC in its details menu");click(mc,"Телепортироваться");next(7);}
  else if(stage==7&&mc.player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(FAR))<20&&age>5){checks.add("Modern chunk tickets load an unloaded marker and teleport to a safe position");click(mc,"Удалить маркер…");click(mc,net.minecraft.network.chat.CommonComponents.GUI_YES.getString());next(8);}
  else if(stage==8&&mc.gui.screen() instanceof MarkerFinderScreen&&NativeNetworkClient.markers.points().stream().noneMatch(p->p.x()==FAR.getX())){checks.add("Confirmed marker deletion immediately after teleport refreshes the finder");mc.gui.setScreen(new NativeLobbyScreen(null));next(9);}
  else if(stage==9&&age>10){click(mc,"Правила");if(!(mc.gui.screen() instanceof WorldSettingsScreen))throw new IllegalStateException("Lobby rules missing");checks.add("Skirmish rules remain one action from the lobby");return true;}return false;
 }
}

