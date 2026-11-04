package local.generalsmc.nativebridge;

import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;

/** Persistent Minecraft sites attach to the original SupplyWarehouseDockUpdate economy. */
public final class ResourceSynchronizer implements AutoCloseable {
    private final NativeSession session;
    private final ServerLevel level;
    private final int originX,originY,originZ;
    private final Map<String,Integer> cash=new HashMap<>();
    private CompletableFuture<List<WorldResourceStore.Node>> pending;
    private int cooldown;
    private List<WorldResourceStore.Node> discovered=List.of();
    public List<WorldResourceStore.Node> discovered(){return discovered;}
    private volatile int focusX,focusZ;
    private List<NativeMatchSpec.Position> focuses=List.of();private int focusCursor;
    public void focus(double x,double z){focusX=(int)Math.floor(x);focusZ=(int)Math.floor(z);}
    public void focuses(List<NativeMatchSpec.Position> positions){focuses=List.copyOf(positions);}
    private java.util.function.Predicate<WorldResourceStore.Node> visible=n->true;
    public void visibility(java.util.function.Predicate<WorldResourceStore.Node> value){visible=value;}
    public long deferredVisible;
    private volatile boolean closed;
    public ResourceSynchronizer(NativeSession session,ServerLevel level,int x,int y,int z){this.session=session;this.level=level;originX=x;originY=y;originZ=z;level.getServer().execute(()->{if(!closed){WorldResourceStore.get(level).resetStocks();WorldSiteStore.get(level).resetResourceStocks();}});}
    public void tick(NativeSession.State state) throws IOException {
        if(closed||!state.inGame()||state.resourceTemplate()==null||state.terrainColumns()<4096)return;
        var changes=new HashMap<String,Integer>();
        var registered=new HashSet<String>();
        if(state.resources()!=null)for(var resource:state.resources()){
            registered.add(resource.key());
            if(resource.cash()>=0&&!Objects.equals(cash.get(resource.key()),resource.cash())){cash.put(resource.key(),resource.cash());changes.put(resource.key(),resource.cash());}
        }
        if(!changes.isEmpty())level.getServer().execute(()->{if(closed)return;var store=WorldResourceStore.get(level);changes.forEach((key,value)->{store.remaining(key,value);WorldSiteStore.get(level).remaining(key,value);});});
        if(pending!=null&&pending.isDone()){
            discovered=pending.join();
            var wanted=new HashSet<String>();for(var node:discovered)wanted.add(node.key());
            if(state.resources()!=null)for(var old:state.resources())if(old.key().startsWith("block_")&&!wanted.contains(old.key()))session.send("RESOURCE_REMOVE "+old.key());
            for(var node:discovered)if(!registered.contains(node.key())){session.send("RESOURCE "+node.key()+" "+node.template()+" "+(node.x()-originX)*WorldScale.UNITS_PER_BLOCK+" "+(node.z()-originZ)*WorldScale.UNITS_PER_BLOCK+" "+(node.y()-originY)*WorldScale.UNITS_PER_BLOCK+" "+node.cash());}
            pending=null;cooldown=20;
        }
        if(pending!=null||cooldown-->0)return;
        if(!focuses.isEmpty()){var p=focuses.get(Math.floorMod(focusCursor++,focuses.size()));focusX=p.x();focusZ=p.z();}
        pending=CompletableFuture.supplyAsync(()->{
            var store=WorldResourceStore.get(level);store.configure(state.resourceTemplate());
            var result=new ArrayList<WorldResourceStore.Node>();
            if(WorldRules.get(level).values().naturalResources()){
                if(state.pagedWorld())result.addAll(store.discover(level,focusX-96,focusZ-96,focusX+96,focusZ+96,state.resourceTemplate()));
                else result.addAll(store.discover(level,originX,originZ,originX+WorldScale.blocksForCells(state.terrainExtentX()),originZ+WorldScale.blocksForCells(state.terrainExtentY()),state.resourceTemplate()));
            }
            for(var marker:WorldSiteStore.get(level).sites())if(marker.kind().equals("resource"))result.add(new WorldResourceStore.Node(marker.key(),marker.pos().getX(),marker.pos().getY(),marker.pos().getZ(),marker.template(),marker.cash()));
            return List.copyOf(result);
        },level.getServer());
    }
    @Override public void close(){closed=true;if(pending!=null)pending.cancel(false);}
}
