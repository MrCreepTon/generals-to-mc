package local.generalsmc.nativebridge;

import local.generalsmc.GeneralsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import java.nio.file.Path;
import java.util.*;
import java.io.IOException;

/** The server owns world edits and the frame order; peers retain original local HUDs. */
public final class NativeServerMatch implements AutoCloseable {
    public interface Transport {
        void launch(NativeMatchSpec spec);
        void origin(NativeMatchSpec.Position origin,String dimension);
        void frame(UUID player,int frame,String contents);
        void radar(UUID player,byte[] bytes);
        void progress(String message);
        void stopped(String reason);
        default void finished(List<Integer> winners){stopped("Match finished");}
    }
    private static final class Peer {
        int frame=-1,crcFrame=-1;long crc,lastSeen;boolean ready;int rateTick,rate;
        final List<String> orders=new ArrayList<>();
        WorldViewSubscription chunks;RadarSynchronizer radar;double x,z;int distance=8;boolean controlling=true;
    }
    private final ServerLevel level;private final NativeSession session;private final Transport transport;
    private NativeMatchSpec spec;private final Map<UUID,Peer> peers=new LinkedHashMap<>();
    private NativeSession.State state;private NativeMatchSpec.Position origin;
    private List<TerrainExporter.Region> bootRegions=List.of();private final List<TerrainExporter.Column> bootColumns=new ArrayList<>();
    private StartPreparation.Plan preparation;private boolean prepared;
    private int phase,bootCursor,tick,startedTick,nextFrame,initialFrame;private long bootRevision;
    private boolean closed;private TerrainSynchronizer terrain;private ResourceSynchronizer resources;private SiteSynchronizer sites;private WorldCombat combat;
    private static final String BOOT="bridge";
    private final Set<net.minecraft.world.level.ChunkPos> bootstrap=new HashSet<>();
    private final Set<Integer> established=new HashSet<>(),eliminated=new HashSet<>();
    private NativeTurnPacer pacing;
    private final boolean sharedLocal;
    public boolean sharedLocal(){return sharedLocal;}
    public NativeServerMatch(ServerLevel level,Path root,NativeProfiles.Profile profile,NativeMatchSpec spec,Transport transport)throws IOException{
        this.level=level;this.spec=spec;this.transport=transport;for(var a:spec.armies())if(!a.computer()){var peer=new Peer();peer.x=a.start().x();peer.z=a.start().z();peers.put(a.player(),peer);}
        var owner=level.getServer().getSingleplayerProfile();
        sharedLocal=!Boolean.getBoolean("generalsmc.forceNetworkPeer")&&!level.getServer().isDedicatedServer()&&owner!=null&&peers.size()==1&&peers.containsKey(owner.id());
        session=new NativeSession(root,sharedLocal,sharedLocal?1280:800,sharedLocal?720:600,NativeProfiles.lab(root,profile),sharedLocal?NativeSession.Mode.SHARED_AUTHORITY:NativeSession.Mode.AUTHORITY,2);
        for(var a:spec.armies()){var pos=new net.minecraft.world.level.ChunkPos(BlockPos.containing(a.start().x(),a.start().y(),a.start().z()));if(bootstrap.add(pos))ModernTickets.add(level,BOOT,pos,5,spec.id());}
    }
    public NativeMatchSpec specification(){return spec;}
    public NativeSession session(){return session;}
    public boolean running(){return phase>=3&&!closed;}
    public boolean contains(UUID player){return peers.containsKey(player);}
    public void prioritizeMob(UUID player,UUID mob){var peer=peers.get(player);var entity=level.getEntity(mob);if(peer!=null&&entity!=null&&Math.hypot(entity.getX()-peer.x,entity.getZ()-peer.z)<=192)WorldCombat.prioritize(level,mob);}
    public void ready(UUID player,int frame,long crc){var p=peers.get(player);if(p!=null&&!closed){p.ready=true;p.frame=frame;p.crcFrame=frame;p.crc=crc;p.lastSeen=tick;}}
    public void camera(UUID player,double x,double z,int distance,boolean controlling){var p=peers.get(player);if(p==null||!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>29999000||Math.abs(z)>29999000)return;p.x=x;p.z=z;p.distance=Math.clamp(distance,4,8);p.controlling=controlling;p.lastSeen=tick;}
    public void orders(UUID player,int frame,int crcFrame,long crc,List<String> orders)throws IOException{
        var peer=peers.get(player);if(peer==null||closed)return;if(frame<peer.frame||frame>nextFrame+1)throw new IOException(local.generalsmc.LocalText.token("Неверный номер кадра участника"));
        if(tick-peer.rateTick>=20){peer.rateTick=tick;peer.rate=0;}if(orders.size()>32||(peer.rate+=orders.size())>128||peer.orders.size()+orders.size()>256)throw new IOException(local.generalsmc.LocalText.token("Слишком много приказов"));
        int owner=spec.armies().stream().filter(a->player.equals(a.player())).findFirst().orElseThrow().nativePlayer();
        if(!orders.isEmpty()&&eliminated.contains(owner))throw new IOException(local.generalsmc.LocalText.token("Ваша армия выбыла. Можно наблюдать за схваткой."));
        for(String command:orders)validateRecord(command,owner);peer.orders.addAll(orders);
        if(crcFrame<0||crcFrame>frame)throw new IOException(local.generalsmc.LocalText.token("Неверный номер кадра участника"));
        peer.frame=frame;peer.crcFrame=crcFrame;peer.crc=crc;peer.lastSeen=tick;
    }
    private static void validateRecord(String command,int player)throws IOException{
        if(command==null||command.length()>16000||command.indexOf('\n')>=0||command.indexOf('\r')>=0)throw new IOException(local.generalsmc.LocalText.token("Неверный приказ"));
        var tokens=command.trim().split("\\s+");try{
            // The fixed upstream MessageStream enum ends at MSG_ENABLE_RETALIATION_MODE1097.
            int type=Integer.parseInt(tokens[0]),count=Integer.parseInt(tokens[1]);if(type<=1000||type>1097||count<0||count>255)throw new IOException(local.generalsmc.LocalText.token("Неверный тип приказа"));
            if(type==1097&&(count!=2||Integer.parseInt(tokens[2])!=0||Integer.parseInt(tokens[3])!=player||Integer.parseInt(tokens[4])!=2))throw new IOException(local.generalsmc.LocalText.token("Нельзя менять режим ответного огня чужой армии"));
            int at=2;for(int i=0;i<count;i++){int tag=Integer.parseInt(tokens[at++]),fields=switch(tag){case 6->3;case 7->2;case 8->4;case 0,1,2,3,4,5,9,10->1;default->throw new IOException(local.generalsmc.LocalText.token("Неверный аргумент приказа"));};for(int n=0;n<fields;n++){String token=tokens[at++];if(tag==1||tag==6){float value=Float.parseFloat(token);if(!Float.isFinite(value))throw new IOException(local.generalsmc.LocalText.token("Неверная координата приказа"));}else{int value=Integer.parseInt(token);if(tag==2&&(value<0||value>1)||tag==10&&(value<0||value>65535))throw new IOException(local.generalsmc.LocalText.token("Неверное значение аргумента"));}}}
            if(at!=tokens.length)throw new IOException(local.generalsmc.LocalText.token("Лишние аргументы приказа"));
        }catch(NumberFormatException|IndexOutOfBoundsException malformed){throw new IOException(local.generalsmc.LocalText.token("Повреждённый приказ"),malformed);}
    }
    public void tick(){
        if(closed)return;tick++;
        try{
            state=session.read();if(state==null){if(tick>1800)throw new IOException(local.generalsmc.LocalText.token("Движок не запустился"));return;}
            if(state.error()!=null&&!state.error().isEmpty())throw new IOException(state.error());if(state.lockstepError()!=null&&!state.lockstepError().isEmpty())throw new IOException(state.lockstepError());
            if(phase==0){
                if(state.maps()==null||state.maps().isEmpty())return;
                String preferred=spec.armies().size()>2?"twilight flame":"tournament desert";
                String map=spec.map().isBlank()?state.maps().stream().filter(m->m.toLowerCase(Locale.ROOT).contains(preferred)).findFirst().orElseThrow(()->new IOException(local.generalsmc.LocalText.token("В выбранной игре не найдена карта для этого числа армий"))):spec.map();
                if(!state.maps().contains(map))throw new IOException(local.generalsmc.LocalText.token("Карта отсутствует в выбранной игре"));
                spec=new NativeMatchSpec(spec.id(),map,spec.seed(),spec.cash(),spec.fingerprint(),spec.armies(),spec.superweaponLimit());spec.configure(session);transport.launch(spec);phase=1;transport.progress(local.generalsmc.LocalText.token("Подготовка мира…"));return;
            }
            if(phase==1){
                if(!state.inGame()||state.objects().stream().noneMatch(a->a.owner()==2&&a.structure()))return;
                var anchor=state.objects().stream().filter(a->a.owner()==2&&a.structure()).findFirst().orElseThrow();var first=spec.armies().getFirst().start();origin=new NativeMatchSpec.Position(first.x()-(int)Math.floor(anchor.x()/WorldScale.UNITS_PER_BLOCK),first.y(),first.z()-(int)Math.floor(anchor.y()/WorldScale.UNITS_PER_BLOCK));
                var regions=new LinkedHashSet<TerrainExporter.Region>();
                for(var a:spec.armies()){
                    int x=Math.floorDiv(a.start().x()-origin.x(),32)*32,z=Math.floorDiv(a.start().z()-origin.z(),32)*32;
                    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)regions.add(new TerrainExporter.Region(x+dx*32,z+dz*32,32,32,origin.x(),origin.y(),origin.z()));
                    if(!a.computer()){var p=peers.get(a.player());p.chunks=new WorldViewSubscription(level,a.player());p.chunks.focus(p.x,p.z,8,true);}
                }
                bootRegions=List.copyOf(regions);initialFrame=state.frame();nextFrame=initialFrame;transport.origin(origin,level.dimension().identifier().toString());phase=2;return;
            }
            if(phase==2){
                if(preparation!=null){if(!preparation.apply(level))return;preparation=null;bootCursor=0;bootColumns.clear();}
                if(tick>2400)throw new IOException(local.generalsmc.LocalText.token("Мир или участники не подготовились за 120 секунд"));
                if(bootCursor<bootRegions.size()){
                    var values=TerrainExporter.sample(level,bootRegions.get(bootCursor));if(values.size()==1024){bootColumns.addAll(values);bootCursor++;if(bootCursor%3==0)transport.progress(local.generalsmc.LocalText.token("Подготовка местности: ")+bootCursor+" / "+bootRegions.size());}return;
                }
                if(peers.values().stream().anyMatch(p->!p.ready))return;
                for(var p:peers.values())if(!sharedLocal&&(p.frame!=initialFrame||p.crc!=state.lockstepCrc()))throw new IOException(local.generalsmc.LocalText.token("Данные игры у участников различаются"));
                if(!prepared&&WorldRules.get(level).values().prepareStarts()){preparation=StartPreparation.plan(level,spec,state);prepared=true;transport.progress(local.generalsmc.LocalText.token("Подготовка стартовых площадок…"));return;}
                validateStarts();session.send("INIT_WORLD");
                for(int at=0;at<bootColumns.size();){int count=Math.min(1024,bootColumns.size()-at);String text;while((text=TerrainExporter.command(bootRevision+1,bootColumns.subList(at,at+count))).length()>60000)count/=2;session.send(text);bootRevision++;at+=count;}
                for(var a:spec.armies())session.send("REBASE_PLAYER "+a.nativePlayer()+" "+((a.start().x()+.5-origin.x())*WorldScale.UNITS_PER_BLOCK)+" "+((a.start().z()+.5-origin.z())*WorldScale.UNITS_PER_BLOCK)+" "+((a.start().y()-origin.y())*WorldScale.UNITS_PER_BLOCK));
                if(sharedLocal)session.send("BEGIN_SHARED_AUTHORITY");
                publishFrame();if(sharedLocal)session.beginDirectAuthorityWrites();pacing=new NativeTurnPacer(System.nanoTime());startedTick=tick;phase=3;transport.progress(local.generalsmc.LocalText.token("Схватка началась"));return;
            }
            if(phase>=3){
                if(updateVictory())return;
                if(combat==null&&state.frame()>initialFrame){
                    terrain=new TerrainSynchronizer(session,level,bootRegions,bootRevision);combat=new WorldCombat(session,level,origin.x(),origin.y(),origin.z());resources=new ResourceSynchronizer(session,level,origin.x(),origin.y(),origin.z());sites=new SiteSynchronizer(session,level,origin.x(),origin.y(),origin.z());
                    for(var e:peers.entrySet()){UUID id=e.getKey();e.getValue().radar=new RadarSynchronizer(level,origin.x(),origin.z(),bytes->transport.radar(id,bytes));}
                }
                for(var e:peers.entrySet()){
                    var p=e.getValue();if(tick-p.lastSeen>300||!sharedLocal&&state.frame()-p.frame>120)throw new IOException(local.generalsmc.LocalText.token("Один из участников не успевает за симуляцией"));
                    if(!sharedLocal&&p.crcFrame>=initialFrame&&p.crcFrame<=state.frame()){var crc=session.crcAt(p.crcFrame);if(crc.isPresent()&&crc.getAsLong()!=p.crc)throw new IOException(local.generalsmc.LocalText.token("Симуляции участников разошлись на кадре ")+p.crcFrame+" ("+crc.getAsLong()+" / "+p.crc+local.generalsmc.LocalText.token("). Схватка остановлена"));}
                    if(p.chunks!=null)p.chunks.focus(p.x,p.z,p.distance,p.controlling);if(p.radar!=null)p.radar.tick(p.x,p.z);
                }
                if(combat!=null){
                    combat.snapshot(state);sites.tick(state);var locations=new ArrayList<WorldResourceStore.Node>(resources.discovered());locations.addAll(sites.terrainSites());terrain.resourceSites(locations);var first=peers.values().iterator().next();terrain.focus(first.x-origin.x(),first.z-origin.z());terrain.focuses(peers.values().stream().map(p->new NativeMatchSpec.Position((int)Math.floor(p.x)-origin.x(),0,(int)Math.floor(p.z)-origin.z())).toList());terrain.tick(state);
                    var resourceFocus=new LinkedHashSet<NativeMatchSpec.Position>();for(var army:spec.armies())resourceFocus.add(army.start());for(var peer:peers.values())resourceFocus.add(new NativeMatchSpec.Position((int)peer.x,0,(int)peer.z));resources.focuses(List.copyOf(resourceFocus));resources.tick(state);
                }
                int slowest=peers.values().stream().mapToInt(p->p.frame).min().orElse(state.frame());
                int turns=sharedLocal?0:pacing.allowance(System.nanoTime(),state.frame(),slowest,nextFrame);
                for(int i=0;i<turns;i++){publishFrame();pacing.committed();}
            }
        }catch(Exception error){GeneralsMod.LOG.error("Native server match failed",error);transport.stopped(error.getMessage()==null?error.toString():error.getMessage());close();}
    }
    private void validateStarts()throws IOException{
        var columns=new HashMap<Long,TerrainExporter.Column>();for(var c:bootColumns)columns.put(((long)c.x()<<32)|(c.y()&0xffffffffL),c);
        for(var army:spec.armies()){
            var base=state.objects().stream().filter(a->a.owner()==army.nativePlayer()&&a.structure()).findFirst().orElseThrow(()->new IOException(local.generalsmc.LocalText.token("У стороны нет стартового здания")));
            double cosine=Math.abs(Math.cos(base.orientation())),sn=Math.abs(Math.sin(base.orientation()));int rx=(int)Math.ceil((cosine*base.geometryMajor()+sn*base.geometryMinor())/WorldScale.UNITS_PER_BLOCK),rz=(int)Math.ceil((sn*base.geometryMajor()+cosine*base.geometryMinor())/WorldScale.UNITS_PER_BLOCK),x=army.start().x()-origin.x(),z=army.start().z()-origin.z();var center=columns.get(((long)x<<32)|(z&0xffffffffL));
            if(center==null)throw new IOException(local.generalsmc.LocalText.token("Стартовая площадка не загрузилась"));
            for(int dx=-rx;dx<=rx;dx++)for(int dz=-rz;dz<=rz;dz++){var c=columns.get(((long)(x+dx)<<32)|((z+dz)&0xffffffffL));if(c==null||c.type()!=0||Math.abs(c.height()-center.height())>.01||c.clearance()<base.geometryHeight())throw new IOException(local.generalsmc.LocalText.token("Для базы ")+army.name()+local.generalsmc.LocalText.token(" нужна ровная свободная площадка. Перенесите стартовый маркер"));}
        }
    }
    private static String alliance(NativeMatchSpec.Army army){return army.team()>=0?"team:"+army.team():"army:"+army.nativePlayer();}
    private boolean updateVictory()throws IOException{
        if(state.armyStatus()==null)return false;
        for(var army:spec.armies())if(state.armyStatus().stream().anyMatch(s->s.player()==army.nativePlayer()&&s.buildings()>0))established.add(army.nativePlayer());
        if(!WorldRules.get(level).values().eliminationVictory()||established.size()!=spec.armies().size()||spec.armies().stream().map(NativeServerMatch::alliance).distinct().count()<2)return false;
        for(var army:spec.armies())if(!eliminated.contains(army.nativePlayer())&&state.armyStatus().stream().anyMatch(s->s.player()==army.nativePlayer()&&s.buildings()==0)){eliminated.add(army.nativePlayer());var peer=peers.get(army.player());if(peer!=null)peer.orders.clear();session.send("PLAYER_DEFEAT "+army.nativePlayer());}
        var remaining=spec.armies().stream().filter(a->!eliminated.contains(a.nativePlayer())).toList();
        if(remaining.stream().map(NativeServerMatch::alliance).distinct().count()<=1){var winners=remaining.isEmpty()?List.<Integer>of():spec.armies().stream().filter(a->alliance(a).equals(alliance(remaining.getFirst()))).map(NativeMatchSpec.Army::nativePlayer).toList();transport.finished(winners);close();return true;}
        return false;
    }
    private void publishFrame()throws IOException{
        var records=new ArrayList<String>();for(var army:spec.armies())if(!army.computer()){var peer=peers.get(army.player());if(!eliminated.contains(army.nativePlayer()))for(String order:peer.orders)records.add("C "+army.nativePlayer()+" "+order);peer.orders.clear();}
        int frame=nextFrame++;String text=session.commitFrame(frame,records);if(!sharedLocal)for(var player:peers.keySet())transport.frame(player,frame,text);
    }
    @Override public void close(){if(closed)return;closed=true;for(var pos:bootstrap)ModernTickets.remove(level,BOOT,pos,5,spec.id());bootstrap.clear();for(var p:peers.values()){if(p.chunks!=null)p.chunks.close();if(p.radar!=null)p.radar.close();}if(combat!=null)combat.close();if(terrain!=null)terrain.close();if(resources!=null)resources.close();if(sites!=null)sites.close();session.close();}
}
