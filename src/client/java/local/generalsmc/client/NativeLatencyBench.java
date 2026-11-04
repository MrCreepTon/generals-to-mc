package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Measures real screen commands once the original NPC has started constructing. */
final class NativeLatencyBench {
    static int tick,stage,samples;static long sent;static Vec3 previous,goal;
    static final List<Long> millis=new ArrayList<>();
    static void tick(Minecraft mc,List<String> checks)throws Exception{
        tick++;var v=NativeWorldView.current;var unit=v.actor(NativeNetworkBench.dozer);if(unit==null)throw new IllegalStateException("Latency fixture lost its Dozer");
        if(stage==0&&tick%120==1&&samples<4){var p=v.origin.world(unit.x(),unit.y(),unit.z());goal=p.add(samples%2==0?5:-5,0,0);v.cameraX=goal.x;v.cameraZ=goal.z;v.session.select(unit.id());stage=1;tick=0;}
        else if(stage==1&&tick>30){var screen=(NativeWorldScreen)MenuScreens.active(mc.screen);var xy=screen.project(goal);previous=v.origin.world(unit.x(),unit.y(),unit.z());sent=System.nanoTime();screen.mouseMoved(xy[0],xy[1]);screen.mouseClicked(xy[0],xy[1],0);screen.mouseReleased(xy[0],xy[1],0);stage=2;}
        else if(stage==2){if(v.origin.world(unit.x(),unit.y(),unit.z()).distanceTo(previous)>.05){long value=(System.nanoTime()-sent)/1_000_000;millis.add(value);local.generalsmc.GeneralsMod.LOG.info("Repeated NPC LMB response {}: {} ms",samples,value);if(value>1000)throw new IllegalStateException("Repeated original NPC command took "+value+"ms");samples++;stage=0;if(samples==4)checks.add("Four further real LMB commands with an active original medium NPC respond in "+millis+" ms, each under one second");}else if(System.nanoTime()-sent>3_000_000_000L)throw new IllegalStateException("Original NPC order did not begin moving within3seconds");}
    }
}
