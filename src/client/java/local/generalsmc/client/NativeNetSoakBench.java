package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.*;

/** Focused real Fabric/NPC soak, with two controlled packet-delivery outages. Never shipped. */
final class NativeNetSoakBench {
    static int ticks,samples,stage;static long sent;static Vec3 from,goal;
    static boolean installed,done;static volatile long holdUntil;static int maxHeld;
    static final java.util.concurrent.atomic.AtomicInteger maxLead=new java.util.concurrent.atomic.AtomicInteger();
    static final List<Long> latency=new ArrayList<>();
    static final ArrayDeque<Runnable> held=new ArrayDeque<>();
    static NativeServerMatch match;
    static boolean tick(Minecraft mc,List<String> checks)throws Exception{
        ticks++;var view=NativeWorldView.current;
        if(!installed){installed=true;mc.getSingleplayerServer().execute(()->{try{
            var f=NativeNetworkServer.class.getDeclaredField("match");f.setAccessible(true);match=(NativeServerMatch)f.get(null);
            var field=NativeServerMatch.class.getDeclaredField("transport");field.setAccessible(true);var original=(NativeServerMatch.Transport)field.get(match);
            field.set(match,new NativeServerMatch.Transport(){
                public void launch(NativeMatchSpec s){original.launch(s);}public void origin(NativeMatchSpec.Position o,String d){original.origin(o,d);}
                public void frame(UUID player,int frame,String text){if(System.nanoTime()<holdUntil){held.add(()->original.frame(player,frame,text));maxHeld=Math.max(maxHeld,held.size());}else original.frame(player,frame,text);}
                public void radar(UUID p,byte[] b){original.radar(p,b);}public void progress(String s){original.progress(s);}public void stopped(String s){original.stopped(s);}public void finished(List<Integer> winners){original.finished(winners);}
            });
        }catch(Exception error){throw new RuntimeException(error);}});}
        if(ticks==200||ticks==700)holdUntil=System.nanoTime()+1_200_000_000L;
        int frame=view.state.frame();mc.getSingleplayerServer().execute(()->{try{
            if(match!=null){var s=match.session().read();if(s!=null)maxLead.accumulateAndGet(s.frame()-frame,Math::max);}
            if(System.nanoTime()>=holdUntil)while(!held.isEmpty())held.remove().run();
        }catch(Exception error){throw new RuntimeException(error);}});
        var unit=view.actor(NativeNetworkBench.dozer);if(unit==null)throw new IllegalStateException("Soak lost its real Dozer");
        if(stage==0&&ticks%100==1&&ticks>40&&System.nanoTime()>holdUntil+2_000_000_000L){from=view.origin.world(unit.x(),unit.y(),unit.z());goal=from.add(samples%2==0?4:-4,0,0);view.cameraX=goal.x;view.cameraZ=goal.z;view.session.select(unit.id());stage=1;}
        else if(stage==1&&ticks%100==30){var screen=(NativeWorldScreen)mc.screen;var xy=screen.project(goal);from=view.origin.world(unit.x(),unit.y(),unit.z());sent=System.nanoTime();screen.mouseMoved(xy[0],xy[1]);screen.mouseClicked(xy[0],xy[1],0);screen.mouseReleased(xy[0],xy[1],0);stage=2;}
        else if(stage==2){if(view.origin.world(unit.x(),unit.y(),unit.z()).distanceTo(from)>.05){long ms=(System.nanoTime()-sent)/1_000_000;latency.add(ms);samples++;stage=0;if(ms>750)throw new IllegalStateException("Steady NPC command latency exceeds750ms: "+ms);}else if(System.nanoTime()-sent>2_000_000_000L)throw new IllegalStateException("Soak movement did not respond");}
        if(ticks>=1600&&stage==0){
            var sorted=latency.stream().sorted().toList();long median=sorted.get(sorted.size()/2),p95=sorted.get((int)Math.ceil(sorted.size()*.95)-1);if(median>350||p95>500)throw new IllegalStateException("Steady latency regression: median="+median+" p95="+p95+" samples="+latency);
            if(samples<10||maxHeld>NativeTurnPacer.MAX_LEAD+1||maxLead.get()>7)throw new IllegalStateException("Unbounded queue: lead="+maxLead+" held="+maxHeld+" samples="+samples);
            checks.add("Focused80second actual Fabric/NPC soak survived two1.2second frame-delivery outages; at most"+maxHeld+" delayed committed turns and observed server/client lead"+maxLead.get());
            checks.add("Actual LMB-to-original-movement samples across the soak: "+latency+" ms; median"+median+"ms, p95"+p95+"ms, without accumulating delay after packet stalls");done=true;return true;
        }
        return false;
    }
}
