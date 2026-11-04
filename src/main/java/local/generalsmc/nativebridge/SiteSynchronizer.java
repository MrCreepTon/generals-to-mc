package local.generalsmc.nativebridge;

import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;

/** Idempotent, server-world placement; the native build assistant validates each original footprint. */
public final class SiteSynchronizer implements AutoCloseable {
    private final NativeSession session;private final ServerLevel level;private final int ox,oy,oz;
    private final Set<net.minecraft.core.BlockPos> relit=new HashSet<>();
    private void relight(net.minecraft.core.BlockPos pos){if(level.getChunkSource().getChunkNow(Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16))!=null&&relit.add(pos))level.getChunkSource().getLightEngine().checkBlock(pos);}
    private CompletableFuture<List<WorldSiteStore.Site>> pending;private int cooldown;private volatile boolean closed;
    private List<WorldSiteStore.Site> sites=List.of();private final Map<String,Integer> observed=new HashMap<>();
    public SiteSynchronizer(NativeSession session,ServerLevel level,int x,int y,int z){this.session=session;this.level=level;ox=x;oy=y;oz=z;}
    public List<WorldResourceStore.Node> terrainSites(){return sites.stream().filter(s->s.kind().equals("structure")&&s.cash()!=-2).map(s->new WorldResourceStore.Node(s.key(),s.pos().getX(),s.pos().getY(),s.pos().getZ(),s.template(),-1)).toList();}
    public void tick(NativeSession.State state)throws IOException{
        if(closed||!state.inGame())return;
        if(pending!=null&&pending.isDone()){
            sites=pending.join();pending=null;var wanted=new HashSet<String>();
            for(var s:sites)if(s.kind().equals("structure")&&s.cash()!=-2){wanted.add(s.key());session.send("SITE "+s.key()+" "+s.template()+" "+((s.pos().getX()+.5-ox)*WorldScale.UNITS_PER_BLOCK)+" "+((s.pos().getZ()+.5-oz)*WorldScale.UNITS_PER_BLOCK)+" "+((s.pos().getY()-oy)*WorldScale.UNITS_PER_BLOCK)+" "+s.angle());}
            if(state.sites()!=null)for(var s:state.sites())if(!wanted.contains(s.key()))session.send("SITE_REMOVE "+s.key());
        }
        if(state.sites()!=null)for(var site:state.sites())if(state.objects().stream().anyMatch(a->a.id()==site.id()&&a.health()>0))observed.put(site.key(),site.id());
        if(pending==null&&cooldown--<=0){cooldown=20;var snapshot=Map.copyOf(observed);pending=CompletableFuture.supplyAsync(()->{if(closed)return List.of();var store=WorldSiteStore.get(level);for(var start:store.starts())relight(start.pos());for(var marker:store.sites())relight(marker.pos());for(var e:snapshot.entrySet())if(state.objects().stream().noneMatch(a->a.id()==e.getValue()&&a.health()>0))store.retire(e.getKey());return store.sites();},level.getServer());}
    }
    @Override public void close(){closed=true;if(pending!=null)pending.cancel(false);}
}
