package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in, bounded capture requested by tools/watch_latency.py. No capture during ordinary play. */
final class NativePerformance {
    private static final class Samples {
        final double[] values=new double[16384];int size;
        void add(double value){if(size<values.length&&Double.isFinite(value))values[size++]=value;}
        Map<String,Object> summary(){var sorted=Arrays.copyOf(values,size);Arrays.sort(sorted);return size==0?Map.of("samples",0):Map.of("samples",size,"p50",sorted[(size-1)/2],"p95",sorted[(int)((size-1)*.95)],"max",sorted[size-1]);}
    }
    private record Pending(long token,long sent) {}
    private static NativeWorldView owner;
    private static String request="";
    private static long nextPoll,started,deadline,lastFrame;
    private static int firstNative,lastNative;
    private static boolean running;
    private static final Map<String,Samples> samples=new LinkedHashMap<>();
    private static final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private static int droppedOrders;
    private static void sample(String key,double value){samples.computeIfAbsent(key,k->new Samples()).add(value);}
    static void frame(NativeWorldView view,long now){
        if(owner!=view){owner=view;running=false;request="";nextPoll=0;pending.clear();}
        if(!running){
            if(now<nextPoll)return;nextPoll=now+1_000_000_000L;
            var file=view.session.directory.resolve("latency-request.json");
            try{
                if(!Files.isRegularFile(file)||Files.size(file)>512)return;
                var data=GeneralsMod.JSON.fromJson(Files.readString(file),com.google.gson.JsonObject.class);
                String id=data.get("id").getAsString();if(id.equals(request)||id.length()>80)return;
                double seconds=data.get("seconds").getAsDouble();if(!Double.isFinite(seconds))return;
                request=id;started=now;deadline=now+(long)(Math.clamp(seconds,5,60)*1e9);lastFrame=0;
                firstNative=lastNative=view.state.frame();samples.clear();pending.clear();droppedOrders=0;running=true;
            }catch(Exception error){GeneralsMod.LOG.debug("Latency capture request unavailable",error);return;}
        }
        if(lastFrame!=0)sample("frameIntervalMs",(now-lastFrame)/1e6);lastFrame=now;
        sample("sceneIngestMs",view.sceneUpdateNanos/1e6);sample("meshSubmitMs",view.drawNanos/1e6);sample("lightQueries",view.lightSamples);
        if(view.state.frame()!=lastNative){lastNative=view.state.frame();sample("nativeUpdateMs",view.state.engineUpdateMs());sample("nativeGuiMs",view.state.guiCaptureMs());sample("nativePublishMs",view.state.publishMs());}
        while(!pending.isEmpty()&&view.state.consumed()>=pending.peek().token())sample("localIpcAckMs",(now-pending.remove().sent())/1e6);
        if(now<deadline)return;
        running=false;
        var statistics=new LinkedHashMap<String,Object>();samples.forEach((name,values)->statistics.put(name,values.summary()));
        var report=new LinkedHashMap<String,Object>();report.put("id",request);report.put("seconds",(now-started)/1e9);report.put("nativeHz",(lastNative-firstNative)/((now-started)/1e9));report.put("statistics",statistics);
        report.put("unacknowledgedClicks",pending.size());report.put("droppedClickSamples",droppedOrders);report.put("lockstep",view.state.lockstep());
        var loader=net.fabricmc.loader.api.FabricLoader.getInstance();var versions=new LinkedHashMap<String,String>();
        for(String mod:List.of("minecraft","generalsmc","sodium","iris","modernui"))loader.getModContainer(mod).ifPresent(c->versions.put(mod,c.getMetadata().getVersion().getFriendlyString()));
        report.put("versions",versions);report.put("java",System.getProperty("java.version"));report.put("os",System.getProperty("os.name"));report.put("processors",Runtime.getRuntime().availableProcessors());report.put("maxHeapMiB",Runtime.getRuntime().maxMemory()/1048576);
        var target=view.session.directory.resolve("latency-client.json");String json=GeneralsMod.JSON.toJson(report);
        CompletableFuture.runAsync(()->{try{var temp=target.resolveSibling("latency-client.tmp");Files.writeString(temp,json);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception e){GeneralsMod.LOG.warn("Latency capture could not be saved",e);}});
    }
    static void order(NativeWorldView view,long before,long token){
        if(!running||owner!=view)return;long now=System.nanoTime();sample("clickHandlingMs",(now-before)/1e6);
        if(pending.size()<128)pending.add(new Pending(token,now));else droppedOrders++;
    }
    static void close(NativeWorldView view){if(owner==view){owner=null;running=false;pending.clear();samples.clear();}}
}
