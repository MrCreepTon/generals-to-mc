package local.generalsmc.client;
import io.netty.channel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
/** Test-only ordered packet delay on an actual TCP client, excluded from the player JAR. */
final class NativeLagBench {
 static final int[] RTT={0,100,300,0};static volatile int oneWay;static int phase,age,step,startFrame,samples;static long began,command,changed;static Vec3 start,goal;static final List<Object> results=new ArrayList<>();static final List<Long> latency=new ArrayList<>();static boolean installed,done;
 static void install(Minecraft mc)throws Exception{
  var connection=mc.getConnection().getConnection();Channel channel=null;for(var f:connection.getClass().getDeclaredFields())if(Channel.class.isAssignableFrom(f.getType())){f.setAccessible(true);channel=(Channel)f.get(connection);break;}if(channel==null)throw new IllegalStateException("TCP channel missing");
  var ch=channel;ch.eventLoop().submit(()->ch.pipeline().addBefore("packet_handler","zhmc-private-latency",new ChannelDuplexHandler(){long incoming,outgoing;int sequence;
   long due(boolean in){int base=oneWay;int jitter=base==0?0:((sequence++%5)-2)*base/10;long now=System.nanoTime(),prior=in?incoming:outgoing;long at=Math.max(prior+1,now+TimeUnit.MILLISECONDS.toNanos(Math.max(0,base+jitter)));if(in)incoming=at;else outgoing=at;return Math.max(0,at-now);}
   @Override public void channelRead(ChannelHandlerContext c,Object msg){c.executor().schedule(()->c.fireChannelRead(msg),due(true),TimeUnit.NANOSECONDS);}
   @Override public void write(ChannelHandlerContext c,Object msg,ChannelPromise promise){c.executor().schedule(()->c.writeAndFlush(msg,promise),due(false),TimeUnit.NANOSECONDS);}
  })).sync();installed=true;
 }
 static void tick(Minecraft mc)throws Exception{
  if(done)return;if(!installed){install(mc);begin();}age++;var v=NativeWorldView.current;if(v==null||v.state==null)throw new IllegalStateException("Lost peer during injected latency");
  var a=v.state.objects().stream().filter(o->o.id()==NativeNetworkPeerBench.unit).findFirst().orElseThrow();var pos=v.origin.world(a.x(),a.y(),a.z());
  if(step==0&&age>60){start=pos;goal=NativeNetworkBench.outward(v,a);v.cameraX=goal.x;v.cameraZ=goal.z;v.session.select(a.id());step=1;age=0;}
  else if(step==1&&age>10){var screen=(NativeWorldScreen)mc.screen;var xy=screen.project(goal);command=System.nanoTime();screen.mouseMoved(xy[0],xy[1]);screen.mouseClicked(xy[0],xy[1],0);screen.mouseReleased(xy[0],xy[1],0);step=2;changed=0;age=0;}
  else if(step==2){if(changed==0&&pos.distanceTo(start)>.03){changed=System.nanoTime();latency.add((changed-command)/1_000_000);}if(age>100){if(changed==0||pos.distanceTo(goal)>1.1)throw new IllegalStateException("Actual LMB order failed under RTT "+RTT[phase]);samples++;if(samples<2){step=0;age=0;}else{double fps=(v.state.frame()-startFrame)/((System.nanoTime()-began)/1e9);results.add(Map.of("injectedRttMillis",RTT[phase],"jitterPercent",20,"movementResponseMillis",List.copyOf(latency),"nativeFramesPerSecond",fps,"frame",v.state.frame()));Files.writeString(NativeProfilesRoot.root().resolve("peer-latency-progress.json"),local.generalsmc.GeneralsMod.JSON.toJson(results));if(fps<27)throw new IllegalStateException("Native simulation slows under RTT "+RTT[phase]+": "+fps+" frames/s");if(latency.stream().anyMatch(t->t>RTT[phase]+650))throw new IllegalStateException("Growing input lag "+latency);if(++phase==RTT.length){done=true;var checkpoint=NativeProfilesRoot.root().resolve("peer-checkpoint.json");var finalState=com.google.gson.JsonParser.parseString(Files.readString(checkpoint)).getAsJsonObject();finalState.add("goal",local.generalsmc.GeneralsMod.JSON.toJsonTree(goal));Files.writeString(checkpoint,finalState.toString());Files.writeString(NativeProfilesRoot.root().resolve("peer-latency.json"),local.generalsmc.GeneralsMod.JSON.toJson(Map.of("result","PASS","phases",results)));}else begin();}}}
 }
 static void begin(){oneWay=RTT[phase]/2;age=step=samples=0;began=System.nanoTime();startFrame=NativeWorldView.current.state.frame();latency.clear();}
 static final class NativeProfilesRoot {static Path root(){return local.generalsmc.nativebridge.NativeProfiles.root();}}
}
