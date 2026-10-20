package local.generalsmc.nativebridge;

import com.google.gson.JsonObject;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import java.io.IOException;
import java.util.*;

/** Saved marker operations. Chunk preparation is asynchronous and has a bounded lifetime. */
final class MarkerService {
    private static final TicketType<UUID> TICKET=TicketType.create("generalsmc_marker",UUID::compareTo);
    private static final Map<UUID,Pending> pending=new HashMap<>();private static int ticks;
    private static final class Pending {
        final ServerPlayer player;final ServerLevel world;final BlockPos pos;final ChunkPos chunk;final Runnable action;int deadline;boolean done;
        Pending(ServerPlayer p,BlockPos pos,Runnable action){player=p;world=p.serverLevel();this.pos=pos;chunk=new ChunkPos(pos);this.action=action;deadline=ticks+200;}
    }
    static boolean editable(ServerPlayer p){return NativeNetworkServer.ownsWorld(p)||p.isCreative();}
    private static BlockPos position(JsonObject data)throws IOException{if(!data.has("x")||!data.has("y")||!data.has("z"))throw new IOException("Invalid marker");return new BlockPos(data.get("x").getAsInt(),data.get("y").getAsInt(),data.get("z").getAsInt());}
    private static NativeNetworkServer.MapPoint point(WorldSiteStore store,BlockPos p,String kind){var text=store.annotation(p);return new NativeNetworkServer.MapPoint(p.getX(),p.getY(),p.getZ(),kind,text.name(),text.note());}
    static void index(ServerPlayer player,boolean resourcesOnly){
        var store=WorldSiteStore.get(player.serverLevel());var points=new ArrayList<NativeNetworkServer.MapPoint>();
        for(var site:store.sites())if(!resourcesOnly||site.cash()!=0&&site.cash()!=-2)points.add(point(store,site.pos(),site.kind().equals("resource")?"supply":"oil"));
        if(!resourcesOnly)for(var start:store.starts())points.add(point(store,start.pos(),"start"));
        NativeNetworkServer.send(player,resourcesOnly?"map_points":"marker_points",resourcesOnly?points:new NativeNetworkServer.MarkerIndex(points,editable(player)));
    }
    static boolean receive(ServerPlayer player,JsonObject data,String kind)throws IOException{
        if(kind.equals("map_points")||kind.equals("marker_points")){index(player,kind.equals("map_points"));return true;}
        if(!Set.of("marker_info","marker_save","marker_delete","marker_teleport").contains(kind))return false;
        var pos=position(data);var store=WorldSiteStore.get(player.serverLevel());if(!store.contains(pos))throw new IOException(local.generalsmc.LocalText.token("Маркер уже удалён. Обновите список."));
        if(kind.equals("marker_info")){
            var start=store.starts().stream().filter(s->s.pos().equals(pos)).findFirst().orElse(null);String type=start==null?store.sites().stream().filter(s->s.pos().equals(pos)).findFirst().orElseThrow().kind().equals("resource")?"supply":"oil":"start";
            NativeNetworkServer.send(player,"marker_info",new NativeNetworkServer.MarkerDetails(point(store,pos,type),start,editable(player)));return true;
        }
        if(!editable(player))throw new IOException(local.generalsmc.LocalText.token("Нужны права владельца, оператора или творческий режим."));
        if(kind.equals("marker_save")){
            String name=data.has("name")?data.get("name").getAsString():"",note=data.has("note")?data.get("note").getAsString():"";
            if(name.length()>64||note.length()>256)throw new IOException(local.generalsmc.LocalText.token("Слишком длинная подпись."));
            store.annotate(pos,name.replaceAll("[\\p{Cntrl}§]"," ").strip(),note.replaceAll("[\\p{Cntrl}§]"," ").strip());index(player,false);NativeNetworkServer.send(player,"marker_saved",local.generalsmc.LocalText.token("Сохранено"));return true;
        }
        if(kind.equals("marker_delete")&&(NativeNetworkServer.busy()||WorldCombat.busy()))throw new IOException(local.generalsmc.LocalText.token("Завершите схватку перед удалением маркера."));
        prepare(player,pos,()->{try{
            if(!WorldSiteStore.get(player.serverLevel()).contains(pos))throw new IOException(local.generalsmc.LocalText.token("Маркер уже удалён. Обновите список."));
            if(kind.equals("marker_teleport"))teleport(player,pos);
            else {
                if(NativeNetworkServer.busy()||WorldCombat.busy())throw new IOException(local.generalsmc.LocalText.token("Завершите схватку перед удалением маркера."));
                var world=player.serverLevel();var state=world.getBlockState(pos);if(state.is(SiteBlocks.START)||state.is(SiteBlocks.SUPPLY)||state.is(SiteBlocks.OIL))world.removeBlock(pos,false);
                WorldSiteStore.get(world).remove(pos);index(player,false);NativeNetworkServer.markersChanged();NativeNetworkServer.send(player,"marker_deleted",local.generalsmc.LocalText.token("Маркер удалён"));
            }
        }catch(Exception e){NativeNetworkServer.send(player,"error",e.getMessage());}});return true;
    }
    private static void prepare(ServerPlayer player,BlockPos pos,Runnable action)throws IOException{
        var previous=pending.get(player.getUUID());
        if(previous!=null){if(!previous.done)throw new IOException(local.generalsmc.LocalText.token("Дождитесь загрузки маркера."));previous.world.getChunkSource().removeRegionTicket(TICKET,previous.chunk,3,player.getUUID());pending.remove(player.getUUID());}
        var job=new Pending(player,pos,action);pending.put(player.getUUID(),job);
        job.world.getChunkSource().addRegionTicket(TICKET,job.chunk,3,player.getUUID());
        // The target came from SavedData, never from arbitrary client coordinates.
        job.world.getChunkSource().getChunkFuture(job.chunk.x,job.chunk.z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,true);
        NativeNetworkServer.send(player,"marker_loading",local.generalsmc.LocalText.token("Загрузка местности…"));
    }
    static void tick(){
        ticks++;var iterator=pending.entrySet().iterator();while(iterator.hasNext()){
            var entry=iterator.next();var job=entry.getValue();boolean connected=job.player.getServer().getPlayerList().getPlayer(entry.getKey())==job.player&&job.player.serverLevel()==job.world;
            if(!connected||ticks>=job.deadline){job.world.getChunkSource().removeRegionTicket(TICKET,job.chunk,3,entry.getKey());iterator.remove();if(connected&&!job.done)NativeNetworkServer.send(job.player,"error",local.generalsmc.LocalText.token("Не удалось загрузить местность. Повторите попытку."));continue;}
            if(!job.done&&job.world.getChunkSource().getChunkNow(job.chunk.x,job.chunk.z)!=null){job.done=true;job.deadline=ticks+40;if(editable(job.player))job.action.run();else NativeNetworkServer.send(job.player,"error",local.generalsmc.LocalText.token("Нужны права владельца, оператора или творческий режим."));}
        }
    }
    static void close(){for(var e:pending.entrySet())e.getValue().world.getChunkSource().removeRegionTicket(TICKET,e.getValue().chunk,3,e.getKey());pending.clear();ticks=0;}
    private static void teleport(ServerPlayer player,BlockPos pos)throws IOException{
        var world=player.serverLevel();for(int radius=1;radius<=16;radius++)for(int side=0;side<8;side++)for(int dy=0;dy<=4;dy++){
            double angle=side*Math.PI/4;var feet=pos.offset((int)Math.round(Math.cos(angle)*radius),dy,(int)Math.round(Math.sin(angle)*radius));
            if(!world.hasChunkAt(feet)||!world.getBlockState(feet.below()).isFaceSturdy(world,feet.below(),Direction.UP)||!world.getFluidState(feet).isEmpty()||!world.getFluidState(feet.above()).isEmpty())continue;
            if(world.getBlockState(feet).is(net.minecraft.tags.BlockTags.FIRE)||world.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK))continue;
            var target=new net.minecraft.world.phys.Vec3(feet.getX()+.5,feet.getY(),feet.getZ()+.5);if(!world.noCollision(player,player.getBoundingBox().move(target.subtract(player.position()))))continue;
            player.teleportTo(world,target.x,target.y,target.z,Set.of(),player.getYRot(),player.getXRot(),false);NativeNetworkServer.send(player,"marker_arrived",pos.toShortString());return;
        }throw new IOException(local.generalsmc.LocalText.token("Рядом с маркером нет безопасного места."));
    }
}
