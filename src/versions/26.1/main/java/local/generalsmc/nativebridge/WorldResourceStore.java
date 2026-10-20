package local.generalsmc.nativebridge;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Seeded resource sites belong to a Minecraft dimension, independently of a native match. */
public final class WorldResourceStore extends SavedData {
    public record Node(String key,int x,double y,int z,String template,int cash) {}
    private static final int CELL=128, CHUNKS=CELL/16;
    private final Map<String,Node> nodes=new LinkedHashMap<>();
    public List<Node> nodes(){return List.copyOf(nodes.values());}
    private final Set<Long> queuedZones=new LinkedHashSet<>();
    private final Map<String,Long> attempted=new HashMap<>();
    private NativeSession.ResourceTemplate template;
    private static final net.minecraft.world.level.saveddata.SavedDataType<WorldResourceStore> FACTORY=new net.minecraft.world.level.saveddata.SavedDataType<>(net.minecraft.resources.Identifier.withDefaultNamespace("generals_resources"),WorldResourceStore::new,CompoundTag.CODEC.xmap(WorldResourceStore::load,value->value.save(new CompoundTag(),null)),null);
    public static WorldResourceStore get(ServerLevel level){return LegacySavedData.get(level,FACTORY);}
    private static WorldResourceStore load(CompoundTag tag){
        var store=new WorldResourceStore();var entries=tag.getListOrEmpty("nodes");
        if(tag.getCompound("template").isPresent()){var t=tag.getCompoundOrEmpty("template");store.template=new NativeSession.ResourceTemplate(t.getStringOr("name",""),t.getFloatOr("major",0f),t.getFloatOr("minor",0f),t.getFloatOr("height",0f));}
        for(int i=0;i<entries.size();i++){if(!(entries.get(i) instanceof CompoundTag n))continue;var node=new Node(n.getStringOr("key",""),n.getIntOr("x",0),n.getDoubleOr("y",0d),n.getIntOr("z",0),n.getStringOr("template",""),n.getIntOr("cash",0));store.nodes.put(node.key,node);}
        return store;
    }
    public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        if(template!=null){var t=new CompoundTag();t.putString("name",template.name());t.putFloat("major",template.major());t.putFloat("minor",template.minor());t.putFloat("height",template.height());tag.put("template",t);}
        var entries=new ListTag();for(var node:nodes.values()){var n=new CompoundTag();n.putString("key",node.key);n.putInt("x",node.x);n.putDouble("y",node.y);n.putInt("z",node.z);n.putString("template",node.template);n.putInt("cash",node.cash);entries.add(n);}tag.put("nodes",entries);return tag;
    }
    public void remaining(String key,int cash){var old=nodes.get(key);if(old!=null&&cash>=0&&old.cash!=cash){nodes.put(key,new Node(key,old.x,old.y,old.z,old.template,cash));setDirty();}}
    public void configure(NativeSession.ResourceTemplate value){if(!Objects.equals(template,value)){template=value;setDirty();}}
    public void chunkLoaded(int chunkX,int chunkZ){
        int gx=Math.floorDiv(chunkX,CHUNKS),gz=Math.floorDiv(chunkZ,CHUNKS);
        // Also revisit neighbours: the last chunk arriving may complete a footprint across a zone edge.
        for(int x=gx-1;x<=gx+1;x++)for(int z=gz-1;z<=gz+1;z++)queuedZones.add(((long)x<<32)|(z&0xffffffffL));
    }
    public void generateLoaded(ServerLevel level){
        if(template==null||queuedZones.isEmpty()||!WorldRules.get(level).values().naturalResources())return;
        var it=queuedZones.iterator();long key=it.next();it.remove();int gx=(int)(key>>32),gz=(int)key;
        discover(level,gx*CELL,gz*CELL,gx*CELL+CELL-1,gz*CELL+CELL-1,template);
    }
    /** No blocks are replaced. A site is an original native structure at an existing clear surface. */
    public List<Node> discover(ServerLevel level,int minX,int minZ,int maxX,int maxZ,NativeSession.ResourceTemplate template){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Resource discovery belongs to the server thread");
        var result=new ArrayList<Node>();
        int sampleBudget=2;
        int rx=(int)Math.ceil(template.major()/WorldScale.UNITS_PER_BLOCK),rz=(int)Math.ceil(template.minor()/WorldScale.UNITS_PER_BLOCK);
        if(rx<1||rz<1||(2L*rx+1)*(2L*rz+1)>1024)return result;
        for(int gx=Math.floorDiv(minX,CELL);gx<=Math.floorDiv(maxX,CELL);gx++)for(int gz=Math.floorDiv(minZ,CELL);gz<=Math.floorDiv(maxZ,CELL);gz++){
            String key="s2_"+gx+"_"+gz;Node node=nodes.get(key);
            if(node==null){
                if(level.getGameTime()-attempted.getOrDefault(key,-1000L)<200)continue;
                attempted.put(key,level.getGameTime());
                var random=new Random(level.getSeed()^((long)gx*0x9e3779b97f4a7c15L)^((long)gz*0xc2b2ae3d27d4eb4fL));
                for(int attempt=0;attempt<16;attempt++){
                    int x=gx*CELL+48+random.nextInt(32),z=gz*CELL+48+random.nextInt(32);
                    // Retain older saved sites and their stock, without adding another beside them.
                    if(nodes.values().stream().anyMatch(n->Math.hypot(n.x-x,n.z-z)<96))continue;
                    boolean loaded=true;
                    for(int cx=Math.floorDiv(x-rx-1,16);cx<=Math.floorDiv(x+rx+1,16);cx++)for(int cz=Math.floorDiv(z-rz-1,16);cz<=Math.floorDiv(z+rz+1,16);cz++)if(level.getChunkSource().getChunkNow(cx,cz)==null)loaded=false;
                    if(!loaded)continue;
                    if(sampleBudget--<=0)break;
                    var columns=TerrainExporter.sample(level,new TerrainExporter.Region(0,0,rx*2+1,rz*2+1,x-rx,0,z-rz));
                    if(columns.size()!=(rx*2+1)*(rz*2+1))continue;
                    float height=columns.get(columns.size()/2).height();
                    if(columns.stream().anyMatch(c->c.type()!=0||Math.abs(c.height()-height)>.01||c.clearance()<template.height()))continue;
                    node=new Node(key,x,height/WorldScale.UNITS_PER_BLOCK,z,template.name(),-1);nodes.put(key,node);setDirty();break;
                }
            }

        }
        for(var node:nodes.values())if(node.x>=minX&&node.x<=maxX&&node.z>=minZ&&node.z<=maxZ)result.add(node);
        return List.copyOf(result);
    }
}
