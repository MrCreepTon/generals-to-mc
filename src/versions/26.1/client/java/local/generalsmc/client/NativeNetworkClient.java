package local.generalsmc.client;

import com.google.gson.*;
import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import local.generalsmc.net.NativePackets;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Original local UI/renderer, with simulation frames committed by the Minecraft server. */
public final class NativeNetworkClient {
    public static NativeNetworkServer.Lobby lobby;
    public static String message="",error="";
    public static NativeNetworkServer.RulesState rules;
    public static List<NativeNetworkServer.MapPoint> mapPoints=List.of();
    public static NativeNetworkServer.MarkerIndex markers;
    private static NativeMatchSpec spec;private static NativeSession session;private static NativeAudio audio;
    private static NativeWorldView.Origin origin;private static String dimension="";private static boolean configured,ready,controlling,sharedAuthority;
    private static long deadline,hashRun;private static int ticks;private static byte[] radar;
    private record Parts(int count,byte[][] chunks){}
    private static final Map<Integer,Parts> frames=new HashMap<>();
    private NativeNetworkClient(){}
    public static boolean active(){return session!=null||spec!=null;}
    public static boolean controlling(){return active()&&controlling;}
    public static void resume(){if(session==null)return;try{controlling=true;session.send("MODE 1");Minecraft.getInstance().setScreen(new NativeWorldScreen(session));}catch(Exception error){fail(error);}}
    public static void install(){
        ClientPlayNetworking.registerGlobalReceiver(NativePackets.Status.TYPE,(packet,context)->context.client().execute(()->{try{status(JsonParser.parseString(packet.json()).getAsJsonObject());}catch(Exception error){fail(error);}}));
        ClientPlayNetworking.registerGlobalReceiver(NativePackets.Frame.TYPE,(packet,context)->context.client().execute(()->{try{frame(packet);}catch(Exception error){fail(error);}}));
        ClientPlayNetworking.registerGlobalReceiver(NativePackets.File.TYPE,(packet,context)->context.client().execute(()->{if(spec!=null&&packet.match().equals(spec.id())&&packet.name().equals("radar.bin")){try{var decoded=local.generalsmc.net.NativePayloadCompression.decode(packet.bytes(),65572);if(decoded.length!=65572)throw new IOException("Invalid radar payload");radar=decoded;}catch(IOException error){fail(error);}}}));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{close();lobby=null;rules=null;mapPoints=List.of();markers=null;});
    }
    public static void request(String kind,java.util.function.Consumer<JsonObject> fill){
        if(!ClientPlayNetworking.canSend(NativePackets.Control.TYPE)){message=L.s("На сервере отсутствует совместимая версия мода");return;}
        if(!kind.equals("camera")&&!kind.equals("orders")&&!kind.equals("ready")&&!kind.equals("rules_get")&&!kind.equals("mob_priority"))error="";var value=new JsonObject();value.addProperty("kind",kind);if(spec!=null)value.addProperty("match",spec.id().toString());fill.accept(value);String text=value.toString();if(text.getBytes(StandardCharsets.UTF_8).length>29000){fail(new IOException(L.s("Слишком большой пакет приказов")));return;}ClientPlayNetworking.send(new NativePackets.Control(text));
    }
    public static void request(String kind){request(kind,j->{});}
    private static void status(JsonObject data)throws Exception{
        String kind=data.get("kind").getAsString();var value=data.get("value");var mc=Minecraft.getInstance();
        switch(kind){
            case "marker_info"->{if(mc.screen instanceof MarkerFinderScreen)mc.setScreen(new MarkerDetailsScreen(mc.screen,GeneralsMod.JSON.fromJson(value,NativeNetworkServer.MarkerDetails.class)));else if(mc.screen instanceof MarkerDetailsScreen s)s.refresh(GeneralsMod.JSON.fromJson(value,NativeNetworkServer.MarkerDetails.class));}
            case "marker_saved"->{if(mc.screen instanceof MarkerDetailsScreen s)s.saved();}
            case "marker_deleted"->{if(mc.screen instanceof MarkerDetailsScreen s)s.onClose();}
            case "marker_loading"->{if(mc.screen instanceof MarkerDetailsScreen s)s.status=L.decode(value.getAsString());else if(mc.screen instanceof MarkerFinderScreen s)s.status=L.decode(value.getAsString());}
            case "marker_points"->markers=GeneralsMod.JSON.fromJson(value,NativeNetworkServer.MarkerIndex.class);
            case "marker_arrived"->{if(mc.screen instanceof MarkerFinderScreen s)s.arrived(value.getAsString());else if(mc.screen instanceof MarkerDetailsScreen s)s.status=L.s("Вы у маркера: ")+value.getAsString();}
            case "map_points"->mapPoints=List.copyOf(Arrays.asList(GeneralsMod.JSON.fromJson(value,NativeNetworkServer.MapPoint[].class)));
            case "rules"->rules=GeneralsMod.JSON.fromJson(value,NativeNetworkServer.RulesState.class);
            case "lobby"->{lobby=GeneralsMod.JSON.fromJson(value,NativeNetworkServer.Lobby.class);message=L.decode(lobby.message());}
            case "fingerprint"->{long run=++hashRun;String expected=value.getAsString();message=L.s("Проверка файлов Generals…");Thread.ofVirtual().name("zhmc-match-files").start(()->{try{var p=NativeProfiles.active();if(p==null)throw new IOException(L.s("Подключите установленную Generals"));String hash=ProfileFingerprint.calculate(NativeProfiles.root(),p);mc.execute(()->{if(run!=hashRun)return;request("ready",j->j.addProperty("fingerprint",hash));message=hash.equals(expected)?L.s("Игровые данные совпадают"):L.s("Файлы Generals отличаются от сервера");});}catch(Exception error){mc.execute(()->message=error.getMessage());}});}
            case "launch"->{if(NativeRtsController.hasLocalSession()||session!=null)throw new IOException(L.s("Сначала завершите локальную RTS"));spec=GeneralsMod.JSON.fromJson(value,NativeMatchSpec.class);var army=spec.armies().stream().filter(a->mc.player.getUUID().equals(a.player())).findFirst().orElseThrow();var p=NativeProfiles.active();if(p==null)throw new IOException(L.s("Подключите игру"));session=NativeNetworkServer.sharedAuthoritySession(spec.id(),mc.player.getUUID());sharedAuthority=session!=null;if(!sharedAuthority)session=new NativeSession(NativeProfiles.root(),true,mc.getWindow().getWidth(),mc.getWindow().getHeight(),NativeProfiles.lab(NativeProfiles.root(),p),NativeSession.Mode.PEER,army.nativePlayer());audio=new NativeAudio();configured=sharedAuthority;ready=false;controlling=true;deadline=System.currentTimeMillis()+120000;frames.clear();message=L.s("Запуск оригинального движка…");}
            case "origin"->{origin=GeneralsMod.JSON.fromJson(value.getAsJsonObject().get("origin"),NativeWorldView.Origin.class);dimension=value.getAsJsonObject().get("dimension").getAsString();}
            case "progress"->message=L.decode(value.getAsString());
            case "error"->{error=message=L.decode(value.getAsString());if(mc.player!=null&&mc.screen==null)mc.player.sendSystemMessage(L.component(message));}
            case "stopped"->{message=L.decode(value.getAsString());close();lobby=null;if(mc.screen instanceof NativeWorldScreen)mc.setScreen(new NativeLobbyScreen(null));}
            case "finished"->{var result=GeneralsMod.JSON.fromJson(value,NativeNetworkServer.MatchResult.class);close();lobby=null;error="";message=L.s("Схватка завершена");mc.setScreen(new NativeMatchResultScreen(result));}
            case "start_info"->mc.setScreen(new NativeStartScreen(mc.screen,GeneralsMod.JSON.fromJson(value,WorldSiteStore.Start.class)));
            case "start_saved"->{message=L.decode(value.getAsString());if(mc.screen instanceof NativeStartScreen s){s.onClose();if(mc.screen instanceof MarkerDetailsScreen marker)marker.request("marker_info",j->{});}}
            default->{}
        }
    }
    private static void frame(NativePackets.Frame packet)throws IOException{
        if(session==null||!packet.match().equals(spec.id()))return;
        if(packet.frame()<0||packet.parts()<1||packet.parts()>88||packet.part()<0||packet.part()>=packet.parts()||packet.bytes().length>48000||frames.size()>128)throw new IOException(L.s("Повреждённый сетевой кадр"));
        var parts=frames.computeIfAbsent(packet.frame(),n->new Parts(packet.parts(),new byte[packet.parts()][]));if(parts.count!=packet.parts())throw new IOException(L.s("Размер кадра изменился"));var old=parts.chunks[packet.part()];if(old!=null&&!Arrays.equals(old,packet.bytes()))throw new IOException(L.s("Сетевой кадр изменился"));parts.chunks[packet.part()]=packet.bytes();
        if(Arrays.stream(parts.chunks).allMatch(Objects::nonNull)){var buffer=new java.io.ByteArrayOutputStream();for(var chunk:parts.chunks)buffer.writeBytes(chunk);session.acceptFrame(packet.frame(),new String(local.generalsmc.net.NativePayloadCompression.decode(buffer.toByteArray(),4*1024*1024),StandardCharsets.UTF_8));frames.remove(packet.frame());}
    }
    public static void tick(Minecraft mc){
        if(session==null)return;ticks++;
        try{
            if(mc.level==null||mc.player==null)throw new IOException(L.s("Соединение с миром закрыто"));
            var state=session.read();if(state==null){if(System.currentTimeMillis()>deadline)throw new IOException(L.s("Движок не запустился"));return;}
            if(!state.error().isEmpty()||state.lockstepError()!=null&&!state.lockstepError().isEmpty())throw new IOException(state.error()+state.lockstepError());
            if(!configured&&state.maps()!=null&&!state.maps().isEmpty()){spec.configure(session);configured=true;}
            if(configured&&!ready&&state.inGame()&&!state.objects().isEmpty()&&state.lockstepCrcFrame()>=0){ready=true;request("ready",j->{j.addProperty("frame",state.frame());j.addProperty("crc",state.lockstepCrc());});}
            if(ready&&origin!=null&&NativeWorldView.current==null){var own=spec.armies().stream().filter(a->a.player()!=null&&a.player().equals(mc.player.getUUID())).findFirst().orElseThrow();new NativeWorldView(session,origin,(own.start().x()-origin.x())*WorldScale.UNITS_PER_BLOCK,(own.start().z()-origin.z())*WorldScale.UNITS_PER_BLOCK);mc.setScreen(new NativeWorldScreen(session));}
            if(audio!=null)audio.tick(mc,session);
            if(radar!=null){var temp=session.directory.resolve("radar-network.tmp");Files.write(temp,radar);try{Files.move(temp,session.directory.resolve("radar.bin"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);radar=null;}catch(AccessDeniedException busy){}}
            if(ready){
                if(!sharedAuthority){var outgoing=session.drainOrders().stream().filter(record->!record.startsWith("1095 ")&&(!record.startsWith("1097 ")||record.startsWith("1097 2 0 "+state.localPlayerIndex()+" "))).toList();{var batch=new ArrayList<String>();int length=0;for(var order:outgoing){if(length+order.length()>12000&&!batch.isEmpty()){orders(state,batch);batch.clear();length=0;}batch.add(order);length+=order.length();}orders(state,batch);}}
                var v=NativeWorldView.current;if(v!=null){v.tick(state);if(ticks%4==0)request("camera",j->{j.addProperty("x",controlling?v.cameraX:mc.player.getX());j.addProperty("z",controlling?v.cameraZ:mc.player.getZ());j.addProperty("distance",mc.options.renderDistance().get());j.addProperty("controlling",controlling);});}
                if(!dimension.isEmpty()&&!mc.level.dimension().identifier().toString().equals(dimension))throw new IOException(L.s("Вы покинули измерение схватки"));
            }else if(System.currentTimeMillis()>deadline)throw new IOException(L.s("Не удалось подготовить схватку"));
        }catch(Exception error){fail(error);}
    }
    private static void orders(NativeSession.State state,List<String> values){request("orders",j->{j.addProperty("frame",state.frame());j.addProperty("crc",state.lockstepCrc());j.addProperty("crcFrame",state.lockstepCrcFrame());j.add("orders",GeneralsMod.JSON.toJsonTree(values));});}
    static void pumpOrders(){
        // Flush actual original input on rendered frames. Heartbeats and world
        // synchronization keep their tick cadence; input need not wait50ms.
        if(!ready||sharedAuthority||session==null||NativeWorldView.current==null)return;
        try{var state=NativeWorldView.current.state;if(state==null)return;var batch=new ArrayList<String>();int bytes=0;
            for(var order:session.drainOrders()){if(order.startsWith("1095 ")||order.startsWith("1097 ")&&!order.startsWith("1097 2 0 "+state.localPlayerIndex()+" "))continue;if(bytes+order.length()>12000&&!batch.isEmpty()){orders(state,batch);batch.clear();bytes=0;}batch.add(order);bytes+=order.length();}if(!batch.isEmpty())orders(state,batch);
        }catch(Exception error){fail(error);}
    }
    public static void toggle(){if(session==null)return;var mc=Minecraft.getInstance();controlling=!controlling;try{session.send("MODE "+(controlling?1:0));mc.setScreen(controlling?new NativeWorldScreen(session):null);}catch(Exception error){fail(error);}}
    public static int leave(){request("leave");close();return 1;}
    static void fail(Exception error){message=error.getMessage()==null?error.toString():error.getMessage();GeneralsMod.LOG.error("Native Minecraft peer stopped",error);var logs=session==null?NativeProfiles.root():session.directory;if(spec!=null)request("failed",j->j.addProperty("message",message));close();var mc=Minecraft.getInstance();if(mc.level!=null)mc.setScreen(new GeneralsFailureScreen(message,logs));}
    private static void close(){hashRun++;if(NativeWorldView.current!=null&&session!=null&&NativeWorldView.current.session==session)NativeWorldView.current.close();if(session!=null)session.close();session=null;spec=null;origin=null;dimension="";audio=null;radar=null;frames.clear();configured=ready=controlling=sharedAuthority=false;}
}
