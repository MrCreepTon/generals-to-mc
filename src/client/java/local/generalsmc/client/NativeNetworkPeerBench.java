package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** A second actual Minecraft client, confined to an owned loopback test server. */
final class NativeNetworkPeerBench {
    static int ticks,age,stage,unit;static boolean done,markerTeleportRejected,rulesDenied,duplicateColorRejected,differentDataRejected;static java.util.concurrent.CompletableFuture<String[]> hashes;static String genuine;static Vec3 goal,markerPosition;
    static void tick(Minecraft mc){
        if(done||!Boolean.getBoolean("generalsmc.nativeNetworkPeer"))return;ticks++;age++;
        try{
            if(Files.exists(NativeProfiles.root().resolve("peer-exit"))){done=true;NativeNetworkClient.leave();mc.stop();return;}
            if(ticks>20*300)throw new IllegalStateException("Second client timed out case "+stage+": "+NativeNetworkClient.message);
            if(ticks>60&&stage==0&&(MenuScreens.active(mc.screen) instanceof TitleScreen||MenuScreens.active(mc.screen) instanceof AccessibilityOnboardingScreen)){
                mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(40);String address="127.0.0.1:"+Integer.getInteger("generalsmc.peerPort");
                ConnectScreen.startConnecting(MenuScreens.active(mc.screen),mc,ServerAddress.parseString(address),new ServerData("Private bridge oracle",address,ServerData.Type.LAN),false,null);next(1);
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.level.dimension().equals(GeneralsMod.ARENA)){
                if(!rulesDenied){NativeNetworkClient.request("rules_get");next(91);return;}
                if(NativeNetworkClient.lobby==null){if(age%40==0)NativeNetworkClient.request("join");}
                else if(!duplicateColorRejected){if(NativeNetworkClient.lobby.members().stream().noneMatch(m->!m.player().equals(mc.player.getUUID())&&m.color()==0))return;NativeNetworkClient.request("choose",j->{j.addProperty("faction","");j.addProperty("color",0);j.addProperty("team",1);j.addProperty("start",1);});next(94);}else choose();
            }else if(stage==91&&NativeNetworkClient.rules!=null){if(NativeNetworkClient.rules.editable()||!NativeNetworkClient.rules.values().blockDestruction())throw new IllegalStateException("Non-operator received editable rules or unexpected defaults");NativeNetworkClient.request("rules_change",j->j.addProperty("key","blockDestruction"));next(92);}
            else if(stage==92&&age>30){NativeNetworkClient.request("rules_get");next(93);}
            else if(stage==93&&age>30){if(NativeNetworkClient.rules.editable()||!NativeNetworkClient.rules.values().blockDestruction())throw new IllegalStateException("Unauthorized player changed world settings");rulesDenied=true;NativeNetworkClient.request("marker_points");next(97);}
            else if(stage==97&&age>10&&NativeNetworkClient.markers!=null){if(NativeNetworkClient.markers.teleportAllowed())throw new IllegalStateException("Survival non-operator can teleport to markers");markerPosition=mc.player.position();NativeNetworkClient.request("marker_teleport",j->{j.addProperty("x",0);j.addProperty("y",64);j.addProperty("z",0);});next(98);}
            else if(stage==98&&age>20){if(NativeNetworkClient.error.isBlank()||mc.player.position().distanceTo(markerPosition)>.05)throw new IllegalStateException("Forged non-operator marker teleport moved the player");markerTeleportRejected=true;next(1);}
            else if(stage==94&&age>30){var own=NativeNetworkClient.lobby.members().stream().filter(m->m.player().equals(mc.player.getUUID())).findFirst().orElseThrow();if(own.color()!=-1||NativeNetworkClient.error.isBlank())throw new IllegalStateException("Duplicate host color was accepted");duplicateColorRejected=true;hashes=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{var root=NativeProfiles.root();var p=NativeProfiles.active();String original=ProfileFingerprint.calculate(root,p);var clone=root.resolve("private/mismatch-"+UUID.randomUUID());var lab=NativeProfiles.lab(root,p);var copy=NativeProfiles.lab(clone,p);for(String folder:List.of("base","zh")){try(var files=Files.walk(lab.resolve(folder))){for(var file:files.toList()){var to=copy.resolve(lab.relativize(file));if(Files.isDirectory(file))Files.createDirectories(to);else Files.createLink(to,file);}}}Files.copy(lab.resolve("fingerprints.json"),copy.resolve("fingerprints.json"));Files.createDirectories(clone.resolve("runtime"));Files.copy(NativeProfiles.engine(root),clone.resolve("runtime/generalszh.exe"));String same=ProfileFingerprint.calculate(clone,p);if(!same.equals(original))throw new IllegalStateException("Identical mounted data at another path mismatched");var extra=copy.resolve("zh/Data/INI/BridgeMismatch.ini");Files.createDirectories(extra.getParent());Files.writeString(extra,"; private compatibility-check fixture\n");String changed=ProfileFingerprint.calculate(clone,p);if(changed.equals(original))throw new IllegalStateException("Changed INI payload did not affect fingerprint");return new String[]{original,changed};}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}});next(95);}
            else if(stage==95&&hashes.isDone()){var result=hashes.join();genuine=result[0];NativeNetworkClient.request("ready",j->j.addProperty("fingerprint",result[1]));next(96);}
            else if(stage==96&&age>30){var own=NativeNetworkClient.lobby.members().stream().filter(m->m.player().equals(mc.player.getUUID())).findFirst().orElseThrow();if(own.ready()||NativeNetworkClient.error.isBlank())throw new IllegalStateException("Actual differing INI data was not refused");differentDataRejected=true;NativeNetworkClient.request("ready",j->j.addProperty("fingerprint",genuine));choose();}
            else if(stage==2&&NativeWorldView.current!=null&&NativeWorldView.current.state!=null&&NativeWorldView.current.state.frame()>50){var v=NativeWorldView.current;if(v.state.localPlayerIndex()!=3)throw new IllegalStateException("Second human controls wrong army");var a=v.state.objects().stream().filter(o->o.owner()==3&&o.template().contains("Dozer")).findFirst().orElseThrow();unit=a.id();goal=NativeNetworkBench.outward(v,a);v.cameraX=goal.x;v.cameraZ=goal.z;v.session.select(unit);next(3);}
            else if(stage==3&&age>40){var screen=(NativeWorldScreen)MenuScreens.active(mc.screen);var xy=screen.project(goal);screen.mouseMoved(xy[0],xy[1]);screen.mouseClicked(xy[0],xy[1],0);screen.mouseReleased(xy[0],xy[1],0);next(4);}
            else if(stage==4&&age>140){var v=NativeWorldView.current;var a=v.state.objects().stream().filter(o->o.id()==unit).findFirst().orElseThrow();if(v.origin.world(a.x(),a.y(),a.z()).distanceTo(goal)>1.1)throw new IllegalStateException("Second player's LMB order failed");var foreign=v.state.objects().stream().filter(o->o.owner()==2&&o.template().contains("Dozer")).findFirst().orElseThrow();var p=v.origin.nativePoint(v.origin.world(foreign.x(),foreign.y(),foreign.z()).add(20,0,0));NativeNetworkClient.request("orders",j->{j.addProperty("frame",v.state.frame());j.addProperty("crc",v.state.lockstepCrc());j.add("orders",GeneralsMod.JSON.toJsonTree(List.of("1001 2 2 1 3 "+foreign.id(),"1068 1 6 "+p.x+" "+p.y+" "+p.z)));});next(5);}
            else if(stage==5&&age>120){var v=NativeWorldView.current;var report=new LinkedHashMap<String,Object>();report.put("result","PASS");report.put("frame",v.state.frame());report.put("player",v.state.localPlayerIndex());report.put("unit",unit);report.put("goal",goal);report.put("session",v.session.directory.toString());report.put("foreignOrderAttempted",true);report.put("worldRuleChangeRejected",rulesDenied);report.put("markerTeleportRejected",markerTeleportRejected);report.put("duplicateColorRejected",duplicateColorRejected);report.put("differentDataRejected",differentDataRejected);Files.writeString(NativeProfiles.root().resolve("peer-checkpoint.json"),GeneralsMod.JSON.toJson(report));next(6);}
            else if(stage==6&&Boolean.getBoolean("generalsmc.nativeFakeLatency")){NativeLagBench.tick(mc);}
            else if(stage==6&&!NativeNetworkClient.active()&&Files.exists(NativeProfiles.root().resolve("peer-coop")))next(1);
            if(MenuScreens.active(mc.screen) instanceof NativeMatchResultScreen screen)Files.writeString(NativeProfiles.root().resolve("peer-victory.json"),GeneralsMod.JSON.toJson(Map.of("won",screen.result.won(),"draw",screen.result.draw())));
            if(stage>=2&&MenuScreens.active(mc.screen) instanceof GeneralsFailureScreen)throw new IllegalStateException(NativeNetworkClient.message);
        }catch(Throwable error){done=true;GeneralsMod.LOG.error("Second Minecraft oracle failed",error);try{Files.writeString(NativeProfiles.root().resolve("peer-checkpoint.json"),GeneralsMod.JSON.toJson(Map.of("result","FAIL","error",error.toString(),"stage",stage)));}catch(Exception ignored){}NativeNetworkClient.leave();mc.stop();}
    }
    static void choose(){NativeNetworkClient.request("choose",j->{j.addProperty("faction","FactionChina");j.addProperty("color",1);j.addProperty("team",Files.exists(NativeProfiles.root().resolve("peer-coop"))?0:1);j.addProperty("start",1);});next(2);}
    static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("Second Minecraft network case {}",stage);}
}
