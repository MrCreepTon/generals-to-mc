package local.generalsmc.client;

import local.generalsmc.*;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import io.netty.channel.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Two real production clients and a developing allied NPC, with ordered RTT/jitter injection. */
final class NativeVersionNetworkBench {
    static final int SECONDS=Integer.getInteger("generalsmc.stabilitySeconds",480);
    static final int[] RTT={0,100,300,0};
    static final boolean SOLO=Boolean.getBoolean("generalsmc.stabilitySolo");
    static final boolean PEER=Boolean.getBoolean("generalsmc.stabilityPeer");
    static int ticks,age,stage;static long began;static boolean done,peerLocated;
    static Path root;static Process peerProcess;static CompletableFuture<?> work;static CompletableFuture<Integer> listener;
    static final List<String> checks=new ArrayList<>();static final Motion motion=new Motion();
    static int rulesProbe;static boolean previousPreparation;
    static long peerResumeAt;static boolean peerReturned;
    static volatile int oneWay;static int phase=-1,phaseFrame;static long phaseAt;
    static final List<Map<String,Object>> phases=new ArrayList<>();
    static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("Stability network {} case {}",PEER?"peer":"host",stage);}
    static void tick(Minecraft mc){if(done){if(PEER&&root!=null&&Files.exists(root.resolve("peer-exit")))mc.stop();return;}ticks++;age++;try{
        if(root!=null&&Files.exists(root.resolve("stop-test")))throw new IllegalStateException("Stopped by diagnostic controller");
        if(ticks>20*(SECONDS+180))throw new IllegalStateException("Network stability timeout "+stage+" "+NativeNetworkClient.message);
        if(mc.gui.screen() instanceof GeneralsFailureScreen)throw new IllegalStateException("Engine or network failure: "+NativeNetworkClient.message);
        if(PEER){peer(mc);return;}if(stage==2)NativeVersionReuseBench.advanceOwnedWorld(mc,label->NativeVersionBench.click(mc,label));
        if(stage==0&&ticks>30){
            System.setProperty("generalsmc.testRoot",Path.of(System.getProperty("generalsmc.testRoot")).resolveSibling("network").toString());root=NativeVersionReuseBench.root(mc);
            for(String stale:List.of("peer-exit","peer-result.json","peer-progress.json","host-progress.json","stop-test"))Files.deleteIfExists(root.resolve(stale));
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);
            work=CompletableFuture.runAsync(()->{try{NativeVersionReuseBench.connect();}catch(Exception e){throw new CompletionException(e);}});next(1);
        }else if(stage==1&&work.isDone()){
            work.join();String name="GeneralsStabilityQA";
            if(!NativeVersionReuseBench.openExisting(mc,name))mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(1263217L,false,false),lookup->lookup.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());next(2);
        }else if(stage==2&&mc.player!=null&&mc.getSingleplayerServer()!=null){
            var server=mc.getSingleplayerServer();listener=CompletableFuture.supplyAsync(()->{try{
                var level=server.getLevel(GeneralsMod.ARENA);server.getPlayerList().getPlayer(mc.player.getUUID()).teleportTo(level,.5,64,.5,Set.of(),0,60,false);
                WorldRules.get(level).update(WorldRules.Values.defaults().toggle("eliminationVictory"));
                var store=WorldSiteStore.get(level);var starts=List.of(new BlockPos(0,64,0),new BlockPos(96,64,0),new BlockPos(96,64,96));
                for(int slot=0;slot<3;slot++){var pos=starts.get(slot);level.setBlock(pos,SiteBlocks.START.defaultBlockState(),3);store.putStart(pos,slot,slot==2?"FactionChina":"",0,slot==2?1:-1,slot);}
                for(var pos:List.of(new BlockPos(24,64,24),new BlockPos(120,64,24),new BlockPos(120,64,120)))level.setBlock(pos,SiteBlocks.SUPPLY.defaultBlockState(),3);
                int port;try(var socket=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
                server.setUsesAuthentication(false);server.getConnection().startTcpServerListener(java.net.InetAddress.getLoopbackAddress(),port);return port;
            }catch(Exception e){throw new CompletionException(e);}},server);next(3);
        }else if(stage==3&&listener.isDone()&&mc.level.dimension().equals(GeneralsMod.ARENA)&&age>40){if(SOLO){mc.gui.setScreen(new NativeLobbyScreen(null));NativeVersionBench.click(mc,"Создать комнату");next(5);}else{launchPeer(listener.join());next(4);}}
        else if(stage==4){
            if(!peerProcess.isAlive())throw new IllegalStateException("Second production client exited: "+root.resolve("peer.log"));
            if(!peerLocated)mc.getSingleplayerServer().execute(()->{for(var p:mc.getSingleplayerServer().getPlayerList().getPlayers())if(!p.getUUID().equals(mc.player.getUUID())){p.setGameMode(GameType.SURVIVAL);p.teleportTo(mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA),96.5,64,.5,Set.of(),0,60,false);peerLocated=true;}});
            if(peerLocated&&age>60){mc.gui.setScreen(new NativeLobbyScreen(null));NativeVersionBench.click(mc,"Создать комнату");next(5);}
        }else if(stage==5&&NativeNetworkClient.lobby!=null&&!NativeNetworkClient.lobby.preparing()){
            NativeNetworkClient.request("choose",j->{j.addProperty("faction","FactionAmerica");j.addProperty("color",0);j.addProperty("team",0);j.addProperty("start",0);});next(6);
        }else if(stage==6&&NativeNetworkClient.lobby!=null&&NativeNetworkClient.lobby.members().size()==(SOLO?1:2)&&NativeNetworkClient.lobby.members().stream().allMatch(NativeNetworkServer.Member::ready)&&NativeNetworkClient.lobby.members().stream().filter(m->m.player().equals(mc.player.getUUID())).allMatch(m->m.faction().equals("FactionAmerica")&&m.start()==0)&&(SOLO||NativeNetworkClient.lobby.members().stream().anyMatch(m->!m.player().equals(mc.player.getUUID())&&m.faction().equals("FactionChina")&&m.start()==1))){
            NativeVersionBench.click(mc,"Начать схватку");next(7);
        }else if(stage==7&&viewReady()){
            if(NativeWorldView.current.state.localPlayerIndex()!=2)throw new IllegalStateException("Wrong host army");
            if(NativeWorldView.current.state.objects().stream().noneMatch(a->a.owner()==(SOLO?3:4)&&a.structure()))throw new IllegalStateException("Allied NPC army missing");
            checks.add(SOLO?"Shared local authority starts the human army and an allied medium NPC":"Shared match starts with UUID-owned human armies and an allied medium NPC");
            if(!SOLO&&!NativeNetworkClient.armyName(3).equals("StabilityPeer"))throw new IllegalStateException("Remote army ownership label missing");
            if(!SOLO)checks.add("Remote ownership tooltip resolves the other human army to its actual Minecraft player name");
            motion.init(2);began=System.nanoTime();next(8);
        }else if(stage==8){
            if(!SOLO&&!peerProcess.isAlive())throw new IllegalStateException("Second client disconnected during soak");
            if(Files.exists(root.resolve("peer-result.json"))){var r=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("peer-result.json"))).getAsJsonObject();if(!r.get("result").getAsString().equals("PASS"))throw new IllegalStateException("Peer failed: "+r);}
            motion.tick(mc);if(age%100==0)writeProgress("host-progress.json");
            if((System.nanoTime()-began)/1e9>SECONDS+15&&(SOLO||Files.exists(root.resolve("peer-result.json")))){
                var v=NativeWorldView.current;long buildings=v.state.objects().stream().filter(a->a.owner()==(SOLO?3:4)&&a.structure()&&!a.template().endsWith("CommandCenter")&&(a.constructionPercent()<0||a.constructionPercent()>=1)).count();
                if(buildings<(SECONDS>=300?2:1))throw new IllegalStateException("NPC base stopped developing: "+buildings+" finished buildings");
                if(motion.samples.size()<Math.max(2,SECONDS/24))throw new IllegalStateException("Too few actual host LMB orders");
                checks.add("Host army keeps accepting actual LMB orders throughout the soak");checks.add("Original medium NPC completes "+buildings+" additional buildings during the shared match");
                if(!SOLO)checks.add("Peer completes sustained0/100/300/0ms RTT phases with jitter, bounded command responses and recovered simulation rate");else checks.add("Single-client shared-authority NPC match keeps responding throughout the soak");finish(mc,"PASS","");
            }
        }
    }catch(Throwable e){GeneralsMod.LOG.error("Stability network failed",e);finish(mc,"FAIL",e.toString());}}
    static boolean viewReady(){return NativeWorldView.current!=null&&NativeWorldView.current.state!=null&&NativeWorldView.current.state.frame()>100&&NativeWorldView.current.scene!=null;}
    static void peer(Minecraft mc)throws Exception{
        root=NativeProfiles.root();if(Files.exists(root.resolve("peer-exit"))){done=true;NativeNetworkClient.leave();mc.stop();return;}
        if(stage==0&&ticks>30){mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.framerateLimit().set(35);String address="127.0.0.1:"+Integer.getInteger("generalsmc.stabilityPort");ConnectScreen.startConnecting(mc.gui.screen(),mc,ServerAddress.parseString(address),new ServerData("Owned stability test",address,ServerData.Type.LAN),false,null);next(1);}
        else if(stage==1&&mc.player!=null&&mc.level.dimension().equals(GeneralsMod.ARENA)){
            if(NativeNetworkClient.lobby!=null&&!NativeNetworkClient.lobby.preparing()&&rulesProbe<3){
                if(rulesProbe==0){NativeNetworkClient.rules=null;NativeNetworkClient.request("rules_get");rulesProbe=1;}
                else if(rulesProbe==1&&NativeNetworkClient.rules!=null){if(NativeNetworkClient.rules.editable())throw new IllegalStateException("Guest rules screen is editable");previousPreparation=NativeNetworkClient.rules.values().prepareStarts();NativeNetworkClient.request("rules_change",j->j.addProperty("key","prepareStarts"));rulesProbe=2;}
                else if(rulesProbe==2&&!NativeNetworkClient.error.isBlank()){NativeNetworkClient.rules=null;NativeNetworkClient.request("rules_get");rulesProbe=3;}
                return;
            }
            if(rulesProbe==3){if(NativeNetworkClient.rules==null)return;if(NativeNetworkClient.rules.values().prepareStarts()!=previousPreparation)throw new IllegalStateException("Server accepted guest rule edit");checks.add("Guest sees read-only rules; forged rule-change packet is rejected and world settings stay unchanged");rulesProbe=4;}
            if(NativeNetworkClient.lobby==null){if(age%40==0)NativeNetworkClient.request("join");}
            else if(!NativeNetworkClient.lobby.preparing()){var own=NativeNetworkClient.lobby.members().stream().filter(m->m.player().equals(mc.player.getUUID())).findFirst().orElseThrow();if(own.faction().equals("FactionChina")&&own.start()==1&&own.team()==0&&own.color()==1)next(2);else if(age%20==0)NativeNetworkClient.request("choose",j->{j.addProperty("faction","FactionChina");j.addProperty("color",1);j.addProperty("team",0);j.addProperty("start",1);});}
        }else if(stage==2&&viewReady()){
            if(NativeWorldView.current.state.localPlayerIndex()!=3)throw new IllegalStateException("Wrong peer army");motion.init(3);installLatency(mc);began=System.nanoTime();next(3);
        }else if(stage==3){
            int current=Math.min(3,(int)((System.nanoTime()-began)/1_000_000_000L/Math.max(1,SECONDS/4)));
            if(current!=phase){if(phase>=0)finishPhase();phase=current;oneWay=RTT[phase]/2;phaseFrame=NativeWorldView.current.state.frame();phaseAt=System.nanoTime();GeneralsMod.LOG.info("Stability RTT phase {} ms",RTT[phase]);}
            if(!peerReturned&&phase==1&&motion.state==0){NativeRtsController.toggle();peerResumeAt=System.nanoTime()+2_000_000_000L;peerReturned=true;checks.add("Peer switches to Minecraft while the server and other army keep simulating");}
            if(peerResumeAt!=0){if(System.nanoTime()<peerResumeAt)return;NativeRtsController.toggle();peerResumeAt=0;checks.add("Peer restores RTS input after F8 during simulated network latency");}
            motion.tick(mc);if(age%100==0)writeProgress("peer-progress.json");
            if((System.nanoTime()-began)/1e9>SECONDS&&motion.state==0){finishPhase();finish(mc,"PASS","");}
        }
    }
    static void finishPhase(){double fps=(NativeWorldView.current.state.frame()-phaseFrame)/((System.nanoTime()-phaseAt)/1e9);var samples=motion.samples.stream().filter(s->((Number)s.get("phase")).intValue()==phase).toList();if(samples.isEmpty())throw new IllegalStateException("No orders measured in RTT phase "+phase);if(fps<27)throw new IllegalStateException("Simulation slows at RTT "+RTT[phase]+": "+fps);phases.add(Map.of("rtt",RTT[phase],"nativeFramesPerSecond",fps,"samples",samples));}
    static void installLatency(Minecraft mc)throws Exception{
        var connection=mc.getConnection().getConnection();Channel channel=null;for(var f:connection.getClass().getDeclaredFields())if(Channel.class.isAssignableFrom(f.getType())){f.setAccessible(true);channel=(Channel)f.get(connection);break;}if(channel==null)throw new IllegalStateException("TCP channel missing");
        var ch=channel;ch.eventLoop().submit(()->ch.pipeline().addBefore("packet_handler","generalsmc-private-stability",new ChannelDuplexHandler(){long incoming,outgoing;int seq;
            long delay(boolean in){int base=oneWay;long at=Math.max((in?incoming:outgoing)+1,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.max(0,base+((seq++%5)-2)*base/10)));if(in)incoming=at;else outgoing=at;return Math.max(0,at-System.nanoTime());}
            @Override public void channelRead(ChannelHandlerContext c,Object msg){c.executor().schedule(()->c.fireChannelRead(msg),delay(true),TimeUnit.NANOSECONDS);}
            @Override public void write(ChannelHandlerContext c,Object msg,ChannelPromise p){c.executor().schedule(()->c.writeAndFlush(msg,p),delay(false),TimeUnit.NANOSECONDS);}
        })).sync();
    }
    static void launchPeer(int port)throws Exception{
        var args=new ArrayList<>(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());args.removeIf(a->a.startsWith("-XX:StartFlightRecording="));if(Boolean.getBoolean("generalsmc.stabilityJfr"))args.add("-XX:StartFlightRecording=settings=profile,dumponexit=true,maxsize=128m,filename="+root.resolve("peer.jfr"));args.add("-Dgeneralsmc.stabilityPeer=true");args.add("-Dgeneralsmc.stabilityPort="+port);args.add("-Dgeneralsmc.nativeRoot="+root);args.add("-Dgeneralsmc.nativeFps=35");args.addAll(List.of("-cp",System.getProperty("java.class.path"),"net.fabricmc.loader.impl.launch.knot.KnotClient"));
        var dir=root.resolve("minecraft-peer");Files.createDirectories(dir);args.addAll(List.of("--username","StabilityPeer","--uuid",UUID.nameUUIDFromBytes("OfflinePlayer:StabilityPeer".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),"--gameDir",dir.toString(),"--width","960","--height","600","--assetsDir",Path.of(System.getProperty("user.home"),".gradle/caches/fabric-loom/assets").toString(),"--assetIndex","26.3-34"));
        var text=new StringBuilder();for(var arg:args)text.append('"').append(arg.replace("\\","\\\\").replace("\"","\\\"")).append("\"\n");var file=root.resolve("peer.args");Files.writeString(file,text.toString());
        peerProcess=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/javaw.exe").toString(),"@"+file).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(root.resolve("peer.log").toFile()).start();var owned=peerProcess;Runtime.getRuntime().addShutdownHook(new Thread(()->{try{if(!owned.waitFor(10,TimeUnit.SECONDS))owned.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();owned.destroyForcibly();}},"generalsmc-owned-stability-peer"));
    }
    static void writeProgress(String name)throws Exception{var v=NativeWorldView.current;Files.writeString(root.resolve(name),GeneralsMod.JSON.toJson(Map.of("frame",v.state.frame(),"seconds",(System.nanoTime()-began)/1e9,"samples",motion.samples,"phase",phase,"fps",Minecraft.getInstance().getFps(),"heapBytes",java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed())));}
    static void finish(Minecraft mc,String result,String error){done=true;try{var r=new LinkedHashMap<String,Object>();r.put("result",result);r.put("checks",checks);r.put("root",String.valueOf(root));r.put("stage",stage);r.put("seconds",began==0?0:(System.nanoTime()-began)/1e9);r.put("samples",motion.samples);r.put("phases",phases);r.put("error",error);r.put("scope",SOLO?"One real production client and shared authority with an allied NPC; repeated physical UI-event LMB orders":"Two real production clients, allied NPC development, repeated physical UI-event LMB orders, ordered injected RTT/jitter; no WAN or hostile-combat claim");if(PEER)Files.writeString(root.resolve("peer-result.json"),GeneralsMod.JSON.toJson(r));else{if(Files.exists(root.resolve("peer-result.json")))r.put("peer",com.google.gson.JsonParser.parseString(Files.readString(root.resolve("peer-result.json"))));Files.writeString(Path.of(System.getProperty("generalsmc.evidence"),"native-version-tests.json"),GeneralsMod.JSON.toJson(r));Files.writeString(root.resolve("peer-exit"),"stop");}}catch(Exception e){GeneralsMod.LOG.error("Stability report failed",e);}if(!PEER){NativeNetworkClient.leave();mc.stop();}}
    static final class Motion {
        int id,state,issuedFrame,issuedPhase,issuedRtt;final List<Map<String,Object>> trail=new ArrayList<>();Vec3 first,second,goal,previous;double facing;long issued,responded,translated,next;final List<Map<String,Object>> samples=new ArrayList<>();
        void init(int owner){var v=NativeWorldView.current;var a=v.state.objects().stream().filter(o->o.owner()==owner&&(o.template().contains("Dozer")||o.template().contains("Worker"))).findFirst().orElseThrow();id=a.id();first=v.origin.world(a.x(),a.y(),a.z());var base=v.state.objects().stream().filter(o->o.owner()==owner&&o.structure()).findFirst().orElseThrow();double dx=a.x()-base.x(),dz=a.y()-base.y(),len=Math.max(.01,Math.hypot(dx,dz));second=first.add(dx/len*12,0,dz/len*12);first=first.add(dx/len*5,0,dz/len*5);next=System.nanoTime()+2_000_000_000L;}
        void tick(Minecraft mc)throws Exception{var v=NativeWorldView.current;if(v==null||v.state==null)throw new IllegalStateException("Lost network view");var a=v.actor(id);if(a==null)throw new IllegalStateException("Command unit disappeared");var pos=v.origin.world(a.x(),a.y(),a.z());long now=System.nanoTime();
            if(state==0&&now>=next){goal=samples.size()%2==0?second:first;v.cameraX=goal.x;v.cameraZ=goal.z;v.session.select(id);next=now+1_000_000_000L;state=1;}
            else if(state==1&&now>=next){if(!(mc.gui.screen() instanceof NativeWorldScreen screen))throw new IllegalStateException("Lost RTS input screen");var xy=screen.project(goal);var event=new MouseButtonEvent(xy[0],xy[1],new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));previous=pos;facing=a.orientation();issued=now;issuedFrame=v.state.frame();issuedPhase=phase;issuedRtt=PEER?RTT[Math.max(0,phase)]:0;trail.clear();responded=translated=0;screen.mouseMoved(xy[0],xy[1]);screen.mouseClicked(event,false);screen.mouseReleased(event);state=2;}
            else if(state==2){trail.add(Map.of("millis",(now-issued)/1_000_000,"frame",v.state.frame(),"consumed",v.state.consumed(),"position",pos,"orientation",a.orientation(),"nativeGoal",List.of(a.goalX(),a.goalY(),a.goalZ()),"moving",a.groundMovement()));if(responded==0&&(pos.distanceTo(previous)>.03||Math.abs(Math.IEEEremainder(a.orientation()-facing,Math.PI*2))>.01))responded=now;if(translated==0&&pos.distanceTo(previous)>.03)translated=now;if(now-issued>7_000_000_000L){Files.writeString(root.resolve((PEER?"peer":"host")+"-last-motion.json"),GeneralsMod.JSON.toJson(Map.of("issuedFrame",issuedFrame,"goal",goal,"previous",previous,"trail",trail)));if(responded==0||responded-issued>TimeUnit.MILLISECONDS.toNanos(issuedRtt+1100))throw new IllegalStateException("Growing LMB response delay; see last-motion trace, observed "+(responded==0?"no response":((responded-issued)/1_000_000)+"ms"));if(pos.distanceTo(goal)>1.1||translated==0)throw new IllegalStateException("Order did not reach its destination: "+pos+" / "+goal);samples.add(Map.of("phase",issuedPhase,"atSeconds",(issued-began)/1e9,"responseMillis",(responded-issued)/1_000_000,"translationMillis",(translated-issued)/1_000_000));state=0;next=now+4_000_000_000L;}}
        }
    }
}
