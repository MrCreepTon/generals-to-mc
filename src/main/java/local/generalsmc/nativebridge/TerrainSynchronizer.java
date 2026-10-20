package local.generalsmc.nativebridge;

import net.minecraft.server.level.ServerLevel;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded surface polling shared by manual access and the real-client oracle.
 * One sample and one unacknowledged update at a time; unchanged columns are not sent.
 */
public final class TerrainSynchronizer implements AutoCloseable {
    private final NativeSession session;
    private final ServerLevel level;
    private final List<TerrainExporter.Region> regions;
    private final Map<Long, TerrainExporter.Column> previous = new HashMap<>();
    private CompletableFuture<List<TerrainExporter.Column>> pending;
    private int cursor, cooldown, rounds;
    private double focusX,focusY;
    private List<NativeMatchSpec.Position> focuses=List.of();
    public void focuses(List<NativeMatchSpec.Position> value){focuses=List.copyOf(value);}
    private List<WorldResourceStore.Node> resourceSites=List.of();
    public void resourceSites(List<WorldResourceStore.Node> value){resourceSites=List.copyOf(value);}
    public void focus(double x,double y){focusX=x;focusY=y;}
    private long revision;
    private boolean closed;

    public TerrainSynchronizer(NativeSession session, ServerLevel level, List<TerrainExporter.Region> regions, long revision) {
        if (regions.isEmpty() || regions.size() > 256) throw new IllegalArgumentException("Surface scope must contain 1 to 256 tiles");
        this.session = session; this.level = level; this.regions = List.copyOf(regions); this.revision = revision;
        for (var region : regions) if (region.width() < 1 || region.depth() < 1 || (long)region.width()*region.depth() > 1024)
            throw new IllegalArgumentException("Surface tile exceeds 1024 columns");
    }

    /** Called on the Minecraft client tick; world reads execute on the owning server. */
    public void tick(NativeSession.State state) throws IOException {
        if (closed || !state.inGame()) return;
        if (pending != null && pending.isDone()) {
            var columns = pending.join(); pending = null;
            var changes = new ArrayList<TerrainExporter.Column>();
            for (var column : columns) if (!column.equals(previous.get(key(column)))) changes.add(column);
            if (!changes.isEmpty()) {
                for(int offset=0;offset<changes.size();){
                    int count=Math.min(1024,changes.size()-offset);String text;
                    while((text=TerrainExporter.command(revision+1,changes.subList(offset,offset+count))).length()>60000&&count>1)count/=2;
                    session.send(text);revision++;offset+=count;
                }
                for (var column : changes) previous.put(key(column), column);
            }
            cooldown = 2;
        }
        // Wait until the engine has applied the prior update before scheduling another.
        if (pending != null || state.terrainRevision() < revision || cooldown-- > 0) return;
        var region = regions.get(cursor); cursor = (cursor + 1) % regions.size();
        // Keep camera and local units current while progressively covering the native map.
        if (++rounds > 4 && rounds % 2 == 0) {
            var interests=new ArrayList<double[]>();interests.add(new double[]{focusX,focusY});
            for(var point:focuses)interests.add(new double[]{point.x(),point.z()});
            for(var actor:state.objects())if(actor.owner()>=2&&!actor.contained()&&(actor.externalKey()==null||actor.externalKey().isEmpty()))interests.add(new double[]{actor.x()/WorldScale.UNITS_PER_BLOCK,actor.y()/WorldScale.UNITS_PER_BLOCK});
            var interested=new ArrayList<TerrainExporter.Region>();
            for(var candidate:regions)for(var point:interests)if(point[0]>=candidate.x()-8&&point[0]<candidate.x()+candidate.width()+8&&point[1]>=candidate.y()-8&&point[1]<candidate.y()+candidate.depth()+8){interested.add(candidate);break;}
            if(!interested.isEmpty())region=interested.get((rounds/2)%interested.size());
        }
        if(state.pagedWorld()&&rounds>4){
            var template=regions.getFirst();var active=new LinkedHashSet<TerrainExporter.Region>();
            addArea(active,template,focusX,focusY);
            for(var point:focuses)addArea(active,template,point.x(),point.z());
            for(var actor:state.objects())if(actor.owner()>=2&&!actor.contained()&&(actor.externalKey()==null||actor.externalKey().isEmpty())){
                addArea(active,template,actor.x()/WorldScale.UNITS_PER_BLOCK,actor.y()/WorldScale.UNITS_PER_BLOCK);
                if(actor.groundMovement()&&(actor.goalX()!=0||actor.goalY()!=0)){
                    double x=actor.x()/WorldScale.UNITS_PER_BLOCK,z=actor.y()/WorldScale.UNITS_PER_BLOCK,gx=actor.goalX()/WorldScale.UNITS_PER_BLOCK,gz=actor.goalY()/WorldScale.UNITS_PER_BLOCK;
                    double length=Math.hypot(gx-x,gz-z);int steps=Math.min(16,(int)Math.ceil(length/24));
                    // Export the intervening route, not just two disconnected islands of known cells.
                    for(int i=1;i<=steps;i++){double t=(double)i/steps;addArea(active,template,x+(gx-x)*t,z+(gz-z)*t);}
                }
            }
            // Original SupplyDock placement needs its actual host footprint before the camera visits it.
            // Samples remain bounded and never load chunks; include only tiles near discovered sites.
            for(var site:resourceSites)for(int dx:new int[]{-16,16})for(int dz:new int[]{-16,16}){
                int x=Math.floorDiv(site.x()-template.originX()+dx,32)*32,z=Math.floorDiv(site.z()-template.originZ()+dz,32)*32;
                if(!previous.containsKey(((long)(x+16)<<32)|((z+16)&0xffffffffL)))active.add(new TerrainExporter.Region(x,z,32,32,template.originX(),template.originY(),template.originZ()));
            }
            var tiles=new ArrayList<>(active);
            var unknown=tiles.stream().filter(t->!previous.containsKey(((long)(t.x()+16)<<32)|((t.y()+16)&0xffffffffL))).toList();
            region=unknown.isEmpty()?tiles.get((rounds-5)%tiles.size()):unknown.get((rounds-5)%unknown.size());
        }
        var sampleRegion=region;
        pending = CompletableFuture.supplyAsync(() -> TerrainExporter.sample(level, sampleRegion), level.getServer());
    }

    private static void addArea(Set<TerrainExporter.Region> active,TerrainExporter.Region template,double x,double z){
        int tileX=Math.floorDiv((int)Math.floor(x),32)*32,tileZ=Math.floorDiv((int)Math.floor(z),32)*32;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)active.add(new TerrainExporter.Region(tileX+32*dx,tileZ+32*dz,32,32,template.originX(),template.originY(),template.originZ()));
    }
    private static long key(TerrainExporter.Column c) { return ((long)c.x() << 32) | (c.y() & 0xffffffffL); }
    public long revision() { return revision; }
    @Override public void close() { closed = true; if (pending != null) pending.cancel(false); previous.clear(); }
}
