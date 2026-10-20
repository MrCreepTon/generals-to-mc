package local.generalsmc.nativebridge;

import com.google.gson.*;
import local.generalsmc.GeneralsMod;
import local.generalsmc.net.NativePackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Authenticated Minecraft connection is the only transport; no Generals online services. */
public final class NativeNetworkServer {
    public record Member(UUID player,String name,String faction,int color,int team,int start,boolean ready){}
    public record Lobby(UUID leader,String dimension,List<Member> members,List<WorldSiteStore.Start> starts,String message,boolean preparing){}
    public record RulesState(WorldRules.Values values,boolean editable,boolean resourcesLocked){}
    public record MatchResult(boolean won,boolean draw,List<String> winners){}
    public record MapPoint(int x,int y,int z,String kind,String name,String note){public MapPoint(int x,int y,int z,String kind){this(x,y,z,kind,"","");}}
    public record MarkerDetails(MapPoint marker,WorldSiteStore.Start start,boolean editable){}
    public record MarkerIndex(List<MapPoint> points,boolean teleportAllowed){}
    static boolean ownsWorld(ServerPlayer p){return p.hasPermissions(2)||p.getUUID().equals(p.getServer().getSingleplayerProfile()==null?null:p.getServer().getSingleplayerProfile().getId());}
    private static void sendRules(ServerPlayer p){send(p,"rules",new RulesState(WorldRules.get(p.serverLevel()).values(),ownsWorld(p),busy()||WorldCombat.busy()));}
    private static final Map<UUID,Member> members=new LinkedHashMap<>();
    private static MinecraftServer server;private static ServerLevel level;private static UUID leader;
    private static NativeProfiles.Profile profile;private static String fingerprint="",message="";
    private static volatile NativeServerMatch match;
    public static NativeSession sharedAuthoritySession(UUID id,UUID player){var current=match;return current!=null&&current.sharedLocal()&&current.specification().id().equals(id)&&current.contains(player)?current.session():null;}private static boolean preparing;private static long generation;
    private static final Map<UUID,Integer> requests=new HashMap<>();private static int ticks;
    private NativeNetworkServer(){}
    public static boolean busy(){return match!=null||preparing;}
    public static void openStart(ServerPlayer p,net.minecraft.core.BlockPos pos){if(p.distanceToSqr(pos.getCenter())<=64)WorldSiteStore.get(p.serverLevel()).starts().stream().filter(s->s.pos().equals(pos)).findFirst().ifPresent(s->send(p,"start_info",s));}
    public static void install(){
        NativePackets.register();
        ServerPlayNetworking.registerGlobalReceiver(NativePackets.Control.TYPE,(packet,context)->context.server().execute(()->{
            try{receive(context.player(),JsonParser.parseString(packet.json()).getAsJsonObject());}
            catch(Exception error){send(context.player(),"error",error.getMessage()==null?local.generalsmc.LocalText.token("Неверный запрос"):error.getMessage());GeneralsMod.LOG.debug("Native network request rejected",error);}
        }));
        ServerLifecycleEvents.SERVER_STARTED.register(s->{reset();server=s;});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->reset());
        ServerTickEvents.END_SERVER_TICK.register(s->{MarkerService.tick();ticks++;if(ticks%20==0)requests.clear();if(match!=null)match.tick();});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,s)->{var id=handler.player.getUUID();if(members.containsKey(id)){if(match!=null||id.equals(leader))stop(local.generalsmc.LocalText.token("Участник покинул схватку"));else{members.remove(id);broadcastLobby();}}});
    }
    private static void reset(){MarkerService.close();generation++;if(match!=null)match.close();match=null;server=null;level=null;leader=null;profile=null;fingerprint="";preparing=false;members.clear();requests.clear();ticks=0;message="";}
    static void send(ServerPlayer player,String kind,Object value){if(player!=null&&ServerPlayNetworking.canSend(player,NativePackets.Status.TYPE)){var data=new JsonObject();data.addProperty("kind",kind);data.add("value",GeneralsMod.JSON.toJsonTree(value));ServerPlayNetworking.send(player,new NativePackets.Status(data.toString()));}}
    private static void all(String kind,Object value){for(var id:members.keySet())send(server.getPlayerList().getPlayer(id),kind,value);}
    private static void broadcastLobby(){if(server!=null&&level!=null)all("lobby",new Lobby(leader,level.dimension().location().toString(),List.copyOf(members.values()),WorldSiteStore.get(level).starts(),message,preparing));}
    private static String str(JsonObject o,String key){return o.has(key)?o.get(key).getAsString():"";}
    private static int num(JsonObject o,String key,int fallback){return o.has(key)?o.get(key).getAsInt():fallback;}
    private static void receive(ServerPlayer p,JsonObject data)throws Exception{
        if(requests.merge(p.getUUID(),1,Integer::sum)>80)throw new IOException(local.generalsmc.LocalText.token("Слишком много запросов"));String kind=str(data,"kind");
        if(kind.equals("rules_get")){sendRules(p);return;}
        // Reliable packets may already be in flight when a match ends. They
        // belong to that UUID, never to the next lobby or a fresh simulation.
        if(Set.of("orders","camera","ready","failed").contains(kind)&&data.has("match")&&(match==null||!match.specification().id().toString().equals(str(data,"match"))))return;
        if(kind.equals("leave")&&!members.containsKey(p.getUUID()))return;
        if(MarkerService.receive(p,data,kind))return;
        if(kind.equals("mob_priority")){UUID id=UUID.fromString(str(data,"mob"));if(match!=null)match.prioritizeMob(p.getUUID(),id);else if(ownsWorld(p))WorldCombat.prioritize(p.serverLevel(),id);return;}
        if(kind.equals("rules_change")){
            if(!ownsWorld(p))throw new IOException(local.generalsmc.LocalText.token("Правила меняет владелец мира или оператор сервера"));
            String key=str(data,"key");if(key.equals("naturalResources")&&(busy()||WorldCombat.busy()))throw new IOException(local.generalsmc.LocalText.token("Расположение ресурсов меняется перед началом схватки"));
            var rules=WorldRules.get(p.serverLevel());rules.update(rules.values().toggle(key));
            for(var observer:p.getServer().getPlayerList().getPlayers())if(observer.serverLevel()==p.serverLevel())sendRules(observer);return;
        }
        if(kind.equals("create")){
            if(leader!=null||busy()||WorldCombat.busy())throw new IOException(local.generalsmc.LocalText.token("Сначала завершите текущую RTS"));
            if(!p.hasPermissions(2)&&!p.getUUID().equals(p.getServer().getSingleplayerProfile()==null?null:p.getServer().getSingleplayerProfile().getId()))throw new IOException(local.generalsmc.LocalText.token("Создать схватку может владелец мира или оператор"));
            profile=NativeProfiles.active();if(profile==null)throw new IOException(local.generalsmc.LocalText.token("На сервере не подключена Generals. Откройте меню подключения игры"));
            leader=p.getUUID();level=p.serverLevel();message=local.generalsmc.LocalText.token("Проверка игровых данных сервера…");join(p,data);preparing=true;broadcastLobby();
            long run=++generation;var chosen=profile;
            CompletableFuture.supplyAsync(()->{try{return ProfileFingerprint.calculate(NativeProfiles.root(),chosen);}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}}).whenComplete((hash,error)->p.getServer().execute(()->{
                if(run!=generation||leader==null)return;preparing=false;if(error!=null){stop(local.generalsmc.LocalText.token("Не удалось проверить данные сервера: ")+error.getCause().getMessage());return;}fingerprint=hash;message=local.generalsmc.LocalText.token("Выберите стороны и стартовые позиции");all("fingerprint",hash);broadcastLobby();
            }));return;
        }
        if(kind.equals("join")){if(leader==null||match!=null)throw new IOException(local.generalsmc.LocalText.token("Открытой комнаты нет"));join(p,data);if(!fingerprint.isEmpty())send(p,"fingerprint",fingerprint);broadcastLobby();return;}
        if(kind.startsWith("npc_")){npc(p,data,kind);return;}
        if(kind.equals("edit_start")||kind.equals("marker_start")){editStart(p,data);return;}
        if(kind.equals("start_info")){var pos=new net.minecraft.core.BlockPos(num(data,"x",0),num(data,"y",0),num(data,"z",0));if(p.distanceToSqr(pos.getCenter())>64)throw new IOException(local.generalsmc.LocalText.token("Подойдите к маркеру"));var start=WorldSiteStore.get(p.serverLevel()).starts().stream().filter(s->s.pos().equals(pos)).findFirst().orElseThrow();send(p,"start_info",start);return;}
        if(!members.containsKey(p.getUUID()))throw new IOException(local.generalsmc.LocalText.token("Сначала войдите в комнату"));
        switch(kind){
            case "ready"->{if(match==null){var old=members.get(p.getUUID());boolean ready=!fingerprint.isEmpty()&&fingerprint.equals(str(data,"fingerprint"));members.put(p.getUUID(),new Member(old.player,old.name,old.faction,old.color,old.team,old.start,ready));if(!ready)send(p,"error",local.generalsmc.LocalText.token("Версия движка или файлы Generals не совпадают с сервером"));broadcastLobby();}else if(match.specification().id().toString().equals(str(data,"match")))match.ready(p.getUUID(),num(data,"frame",0),data.get("crc").getAsLong());}
            case "choose"->{if(match!=null||preparing)throw new IOException(local.generalsmc.LocalText.token("Подготовка уже началась"));var old=members.get(p.getUUID());String faction=str(data,"faction");if(!faction.isEmpty()&&!profile.factions().contains(faction))throw new IOException(local.generalsmc.LocalText.token("Сторона отсутствует в игре сервера"));int color=num(data,"color",0),team=num(data,"team",-1),start=num(data,"start",-1);if(team< -2||team>7||start< -1||start>7||(color!=-1&&profile.colors().stream().noneMatch(c->c.id()==color)))throw new IOException(local.generalsmc.LocalText.token("Неверная команда или цвет"));if(color>=0&&(members.values().stream().anyMatch(m->!m.player.equals(p.getUUID())&&m.color==color)||WorldSiteStore.get(level).starts().stream().anyMatch(s->s.difficulty()>=0&&s.color()==color)))throw new IOException(local.generalsmc.LocalText.token("Этот цвет уже занят. Выберите другой или случайный."));
            if(start>=0&&(members.values().stream().anyMatch(m->!m.player.equals(p.getUUID())&&m.start==start)||WorldSiteStore.get(level).starts().stream().noneMatch(s->s.slot()==start&&s.difficulty()<0)))throw new IOException(local.generalsmc.LocalText.token("Старт занят или назначен NPC"));
            members.put(p.getUUID(),new Member(old.player,old.name,faction,color,team,start,old.ready));broadcastLobby();}
            case "start"->{if(!p.getUUID().equals(leader)||match!=null||preparing)throw new IOException(local.generalsmc.LocalText.token("Схватку запускает создатель комнаты"));start();}
            case "leave"->{if(match!=null||p.getUUID().equals(leader))stop(local.generalsmc.LocalText.token("Схватка завершена участником"));else{members.remove(p.getUUID());send(p,"stopped",local.generalsmc.LocalText.token("Вы покинули комнату"));broadcastLobby();}}
            case "orders"->{if(match==null||!match.specification().id().toString().equals(str(data,"match")))return;var list=new ArrayList<String>();for(var entry:data.getAsJsonArray("orders"))list.add(entry.getAsString());match.orders(p.getUUID(),num(data,"frame",0),num(data,"crcFrame",num(data,"frame",0)),data.get("crc").getAsLong(),list);}
            case "camera"->{if(match!=null&&match.specification().id().toString().equals(str(data,"match"))){if(p.serverLevel()!=level)throw new IOException(local.generalsmc.LocalText.token("Вернитесь в измерение схватки"));match.camera(p.getUUID(),data.get("x").getAsDouble(),data.get("z").getAsDouble(),num(data,"distance",8),data.get("controlling").getAsBoolean());}}
            case "failed"->{if(match!=null)stop(p.getGameProfile().getName()+": "+str(data,"message"));}
            default->throw new IOException(local.generalsmc.LocalText.token("Неизвестный запрос"));
        }
    }
    static void markersChanged(){broadcastLobby();}
    private static void join(ServerPlayer p,JsonObject data)throws IOException{
        if(p.serverLevel()!=level||members.size()>=8&&!members.containsKey(p.getUUID()))throw new IOException(local.generalsmc.LocalText.token("Войдите в измерение комнаты; максимум 8 участников"));
        String faction=str(data,"faction");if(!profile.factions().contains(faction))faction="";int color=-1;
        members.put(p.getUUID(),new Member(p.getUUID(),p.getGameProfile().getName(),faction,color,members.size(),-1,false));
    }
    private static void start()throws IOException{
        if(WorldCombat.busy())throw new IOException(local.generalsmc.LocalText.token("Сначала завершите локальную RTS"));
        if(members.values().stream().anyMatch(m->!m.ready))throw new IOException(local.generalsmc.LocalText.token("Дождитесь проверки данных всех участников"));int seed=new Random().nextInt(1,Integer.MAX_VALUE);var random=new Random(seed);var usedColors=new HashSet<Integer>();for(var m:members.values())if(m.color>=0&&!usedColors.add(m.color))throw new IOException(local.generalsmc.LocalText.token("Этот цвет уже занят. Выберите другой или случайный."));for(var s:WorldSiteStore.get(level).starts())if(s.difficulty()>=0&&s.color()>=0&&!usedColors.add(s.color()))throw new IOException(local.generalsmc.LocalText.token("Этот цвет уже занят. Выберите другой или случайный."));var armies=new ArrayList<NativeMatchSpec.Army>();var starts=WorldSiteStore.get(level).starts();var used=new HashSet<Integer>();
        for(var member:members.values()){
            var player=server.getPlayerList().getPlayer(member.player);if(player==null||player.serverLevel()!=level)throw new IOException(local.generalsmc.LocalText.token("Участник покинул измерение"));
            var chosen=member.start<0?starts.stream().filter(s->s.difficulty()<0&&!used.contains(s.slot())&&members.values().stream().noneMatch(m->m.start==s.slot())).findFirst().orElse(null):starts.stream().filter(s->s.slot()==member.start&&s.difficulty()<0&&!used.contains(s.slot())).findFirst().orElseThrow(()->new IOException(local.generalsmc.LocalText.token("Старт занят или назначен NPC")));
            var pos=chosen==null?player.blockPosition():chosen.pos();if(chosen!=null)used.add(chosen.slot());
            armies.add(new NativeMatchSpec.Army(member.player,member.name,armies.size(),randomFaction(member.faction,random),randomColor(member.color,usedColors,random),member.team==-2?random.nextInt(8):member.team,-1,new NativeMatchSpec.Position(pos.getX(),pos.getY(),pos.getZ())));
        }
        for(var s:starts)if(s.difficulty()>=0){if(armies.size()>=8)throw new IOException(local.generalsmc.LocalText.token("Всего допускается 8 армий"));String faction=randomFaction(s.faction(),random);if(!profile.factions().contains(faction))throw new IOException(local.generalsmc.LocalText.token("Сторона стартового маркера отсутствует в игре"));int color=randomColor(s.color(),usedColors,random);armies.add(new NativeMatchSpec.Army(null,"NPC "+(s.slot()+1),armies.size(),faction,color,s.team()==-2?random.nextInt(8):s.team(),s.difficulty(),new NativeMatchSpec.Position(s.pos().getX(),s.pos().getY(),s.pos().getZ())));}
        if(armies.size()<2)throw new IOException(local.generalsmc.LocalText.token("Нужен второй игрок или стартовый маркер NPC"));
        for(int i=0;i<armies.size();i++)for(int j=0;j<i;j++){var a=armies.get(i).start();var b=armies.get(j).start();if(Math.hypot(a.x()-b.x(),a.z()-b.z())<40)throw new IOException(local.generalsmc.LocalText.token("Разнесите стартовые позиции хотя бы на 40 блоков"));}
        var spec=new NativeMatchSpec(UUID.randomUUID(),"",seed,10000,fingerprint,List.copyOf(armies));
        preparing=true;match=new NativeServerMatch(level,NativeProfiles.root(),profile,spec,new NativeServerMatch.Transport(){
            public void launch(NativeMatchSpec spec){all("launch",spec);}
            public void origin(NativeMatchSpec.Position origin,String dimension){var value=new JsonObject();value.add("origin",GeneralsMod.JSON.toJsonTree(origin));value.addProperty("dimension",dimension);all("origin",value);}
            public void frame(UUID player,int frame,String contents){var target=server.getPlayerList().getPlayer(player);if(target==null)return;byte[] bytes=local.generalsmc.net.NativePayloadCompression.encode(contents.getBytes(StandardCharsets.UTF_8));int count=(bytes.length+47999)/48000;for(int part=0;part<count;part++)ServerPlayNetworking.send(target,new NativePackets.Frame(spec.id(),frame,part,count,Arrays.copyOfRange(bytes,part*48000,Math.min(bytes.length,(part+1)*48000))));}
            public void radar(UUID player,byte[] bytes){var target=server.getPlayerList().getPlayer(player);if(target!=null)ServerPlayNetworking.send(target,new NativePackets.File(spec.id(),"radar.bin",local.generalsmc.net.NativePayloadCompression.encode(bytes)));}
            public void progress(String text){message=text;all("progress",text);}
            public void stopped(String text){stop(text);}
            public void finished(List<Integer> winners){finishMatch(winners);}
        });broadcastLobby();
    }
    private static String randomFaction(String selected,Random random){return selected.isEmpty()?profile.factions().get(random.nextInt(profile.factions().size())):selected;}
    private static int randomColor(int selected,Set<Integer> used,Random random)throws IOException{if(selected>=0)return selected;var free=profile.colors().stream().filter(c->!used.contains(c.id())).toList();if(free.isEmpty())throw new IOException(local.generalsmc.LocalText.token("Не осталось свободных цветов"));int result=free.get(random.nextInt(free.size())).id();used.add(result);return result;}
    private static void npc(ServerPlayer p,JsonObject data,String kind)throws IOException{
        if(!p.getUUID().equals(leader)||busy()||preparing)throw new IOException(local.generalsmc.LocalText.token("Настраивает NPC создатель комнаты до старта"));
        var store=WorldSiteStore.get(level);int slot=num(data,"slot",-1);var start=store.starts().stream().filter(s->s.slot()==slot).findFirst().orElse(null);
        if(kind.equals("npc_add")){
            if(members.size()+store.starts().stream().filter(s->s.difficulty()>=0).count()>=8)throw new IOException(local.generalsmc.LocalText.token("Всего допускается 8 армий"));
            start=store.starts().stream().filter(s->s.difficulty()<0&&members.values().stream().noneMatch(m->m.start==s.slot()||m.start<0&&server.getPlayerList().getPlayer(m.player)!=null&&server.getPlayerList().getPlayer(m.player).position().distanceTo(s.pos().getCenter())<40)).findFirst().orElse(null);
            if(start==null){
                if(store.starts().size()>=8)throw new IOException(local.generalsmc.LocalText.token("Все стартовые позиции заняты"));
                net.minecraft.core.BlockPos position=null;
                for(int distance:new int[]{64,80,96,112}){for(int i=0;i<8;i++){int x=p.getBlockX()+(int)Math.round(Math.cos(i*Math.PI/4)*distance),z=p.getBlockZ()+(int)Math.round(Math.sin(i*Math.PI/4)*distance);if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null)continue;double y=WorldSurface.ground(level,x,z);if(!Double.isFinite(y)||store.starts().stream().anyMatch(s->Math.hypot(s.pos().getX()-x,s.pos().getZ()-z)<40))continue;boolean clear=true;for(int dx=-8;dx<=8&&clear;dx+=2)for(int dz=-8;dz<=8;dz+=2){if(level.getChunkSource().getChunkNow((x+dx)>>4,(z+dz)>>4)==null){clear=false;break;}double ground=WorldSurface.ground(level,x+dx,z+dz);if(!Double.isFinite(ground)||Math.abs(ground-y)>.5){clear=false;break;}for(int h=0;h<4;h++){var q=new net.minecraft.core.BlockPos(x+dx,(int)Math.ceil(y)+h,z+dz);if(!level.getBlockState(q).canBeReplaced()){clear=false;break;}}}if(clear){position=new net.minecraft.core.BlockPos(x,(int)Math.ceil(y),z);break;}}if(position!=null)break;}
                if(position==null)throw new IOException(local.generalsmc.LocalText.token("Поставьте свободный маркер старта на ровной площадке не ближе 40 блоков от других стартов. Маркеры находятся во вкладке Generals творческого инвентаря."));
                level.setBlock(position,SiteBlocks.START.defaultBlockState(),3);var pos=position;start=store.starts().stream().filter(s->s.pos().equals(pos)).findFirst().orElseThrow();
            }
            if(members.size()+store.starts().stream().filter(s->s.difficulty()>=0).count()>=8)throw new IOException(local.generalsmc.LocalText.token("Всего допускается 8 армий"));
            store.putStart(start.pos(),start.slot(),"",members.get(leader).team()==0?1:0,1,-1);
        }else{
            if(start==null||start.difficulty()<0)throw new IOException(local.generalsmc.LocalText.token("NPC не найден"));
            if(kind.equals("npc_move")){int destination=num(data,"destination",-1);var target=store.starts().stream().filter(s->s.slot()==destination&&s.difficulty()<0&&members.values().stream().noneMatch(m->m.start==destination)).findFirst().orElseThrow(()->new IOException(local.generalsmc.LocalText.token("Старт занят или назначен NPC")));store.putStart(target.pos(),target.slot(),start.faction(),start.team(),start.difficulty(),start.color());store.putStart(start.pos(),start.slot(),"",-1,-1,-1);broadcastLobby();return;}

            if(kind.equals("npc_remove"))store.putStart(start.pos(),start.slot(),"",-1,-1,-1);
            else {String faction=str(data,"faction");int color=num(data,"color",-1),team=num(data,"team",-1),difficulty=num(data,"difficulty",1);if(!faction.isEmpty()&&!profile.factions().contains(faction)||color!=-1&&profile.colors().stream().noneMatch(c->c.id()==color)||team< -2||team>7||difficulty<0||difficulty>2)throw new IOException(local.generalsmc.LocalText.token("Неверная настройка NPC"));int ownSlot=start.slot();if(color>=0&&(members.values().stream().anyMatch(m->m.color==color)||store.starts().stream().anyMatch(s->s.slot()!=ownSlot&&s.difficulty()>=0&&s.color()==color)))throw new IOException(local.generalsmc.LocalText.token("Этот цвет уже занят. Выберите другой или случайный."));store.putStart(start.pos(),ownSlot,faction,team,difficulty,color);}
        }message=local.generalsmc.LocalText.token("Настройки NPC сохранены");broadcastLobby();
    }
    private static void editStart(ServerPlayer p,JsonObject data)throws IOException{
        if(leader!=null&&!p.getUUID().equals(leader))throw new IOException(local.generalsmc.LocalText.token("Настраивает NPC создатель комнаты до старта"));
        if(busy()||!p.mayBuild())throw new IOException(local.generalsmc.LocalText.token("Редактирование доступно до схватки игрокам с правом строительства"));
        var pos=new net.minecraft.core.BlockPos(num(data,"x",0),num(data,"y",0),num(data,"z",0));if(str(data,"kind").equals("marker_start")?!MarkerService.editable(p)||WorldSiteStore.get(p.serverLevel()).starts().stream().noneMatch(v->v.pos().equals(pos)):p.distanceToSqr(pos.getCenter())>64||!p.serverLevel().getBlockState(pos).is(SiteBlocks.START))throw new IOException(local.generalsmc.LocalText.token("Подойдите к стартовому маркеру"));
        var store=WorldSiteStore.get(p.serverLevel());int slot=num(data,"slot",0);if(store.starts().stream().anyMatch(s->s.slot()==slot&&!s.pos().equals(pos)))throw new IOException(local.generalsmc.LocalText.token("Этот номер стартовой позиции уже занят"));String faction=str(data,"faction");if(!faction.isEmpty()&&(profile==null?faction.length()>96:!profile.factions().contains(faction)))throw new IOException(local.generalsmc.LocalText.token("Неверная сторона"));
        store.putStart(pos,slot,faction,num(data,"team",-1),num(data,"difficulty",-1),num(data,"color",-1));send(p,"start_saved",local.generalsmc.LocalText.token("Стартовая позиция сохранена"));broadcastLobby();
    }
    private static void stop(String reason){if(server==null)return;all("stopped",reason);if(match!=null)match.close();match=null;generation++;preparing=false;members.clear();leader=null;level=null;message="";}
    private static void finishMatch(List<Integer> winners){
        if(server==null||match==null)return;var armies=match.specification().armies();var names=armies.stream().filter(a->winners.contains(a.nativePlayer())).map(NativeMatchSpec.Army::name).toList();
        for(var member:members.values()){boolean won=armies.stream().anyMatch(a->member.player().equals(a.player())&&winners.contains(a.nativePlayer()));send(server.getPlayerList().getPlayer(member.player()),"finished",new MatchResult(won,winners.isEmpty(),names));}
        match.close();match=null;generation++;preparing=false;members.clear();leader=null;level=null;message="";
    }
}
