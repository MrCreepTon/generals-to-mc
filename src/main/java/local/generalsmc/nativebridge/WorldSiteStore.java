package local.generalsmc.nativebridge;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Player-placed structures and army start markers are part of the Minecraft dimension. */
public final class WorldSiteStore extends SavedData {
    public record Site(String key,BlockPos pos,String template,float angle,String kind,int cash) {}
    public record Start(BlockPos pos,int slot,String faction,int team,int difficulty,int color) {public Start(BlockPos p,int s,String f,int t,int d){this(p,s,f,t,d,-1);}}
    private final Map<String,Site> sites=new LinkedHashMap<>();
    private final Map<String,Start> starts=new LinkedHashMap<>();
    public record Annotation(String name,String note){}
    private final Map<String,Annotation> annotations=new HashMap<>();
    public Annotation annotation(BlockPos pos){return annotations.getOrDefault(key(pos),new Annotation("",""));}
    public boolean contains(BlockPos pos){return sites.containsKey(key(pos))||starts.containsKey(key(pos));}
    public void annotate(BlockPos pos,String name,String note){if(!contains(pos))throw new IllegalArgumentException("Unknown marker");annotations.put(key(pos),new Annotation(name,note));setDirty();}
    private static final Factory<WorldSiteStore> FACTORY=new Factory<>(WorldSiteStore::new,(tag,lookup)->load(tag),null);
    public static WorldSiteStore get(ServerLevel level){return level.getDataStorage().computeIfAbsent(FACTORY,"generals_sites");}
    public static String key(BlockPos pos){return "block_"+pos.getX()+"_"+pos.getY()+"_"+pos.getZ();}
    private static WorldSiteStore load(CompoundTag tag){var data=new WorldSiteStore();var list=tag.getList("sites",10);for(int i=0;i<list.size();i++){var n=list.getCompound(i);var p=new BlockPos(n.getInt("x"),n.getInt("y"),n.getInt("z"));data.sites.put(key(p),new Site(key(p),p,n.getString("template"),n.getFloat("angle"),n.getString("kind"),n.contains("cash")?n.getInt("cash"):-1));}var saved=tag.getList("starts",10);for(int i=0;i<saved.size();i++){var n=saved.getCompound(i);var p=new BlockPos(n.getInt("x"),n.getInt("y"),n.getInt("z"));data.starts.put(key(p),new Start(p,n.getInt("slot"),n.getString("faction"),n.getInt("team"),n.getInt("difficulty"),n.contains("color")?n.getInt("color"):-1));}var labels=tag.getList("annotations",10);for(int i=0;i<labels.size();i++){var n=labels.getCompound(i);var key=n.getString("key");data.annotations.put(key,new Annotation(n.getString("name"),n.getString("note")));}return data;}
    public void put(BlockPos pos,String template,float angle,String kind){pos=pos.immutable();sites.put(key(pos),new Site(key(pos),pos,template,angle,kind,-1));setDirty();}
    public void resetResourceStocks(){boolean changed=sites.values().stream().anyMatch(s->(s.kind().equals("resource")||s.cash()==-2)&&s.cash()!=-1);if(changed){sites.replaceAll((key,s)->s.kind().equals("resource")||s.cash()==-2?new Site(s.key(),s.pos(),s.template(),s.angle(),s.kind(),-1):s);setDirty();}}
    public void remaining(String key,int cash){var old=sites.get(key);if(old!=null&&old.kind.equals("resource")&&cash>=0&&old.cash!=cash){sites.put(key,new Site(old.key,old.pos,old.template,old.angle,old.kind,cash));setDirty();}}
    public void retire(String key){var s=sites.get(key);if(s!=null&&s.kind.equals("structure")&&s.cash!=-2){sites.put(key,new Site(s.key,s.pos,s.template,s.angle,s.kind,-2));setDirty();}}
    public void remove(BlockPos pos){boolean changed=sites.remove(key(pos))!=null;changed|=starts.remove(key(pos))!=null;changed|=annotations.remove(key(pos))!=null;if(changed)setDirty();}
    public List<Site> sites(){return List.copyOf(sites.values());}
    public List<Start> starts(){return starts.values().stream().sorted(Comparator.comparingInt(Start::slot)).toList();}
    public void putStart(BlockPos pos,int slot,String faction,int team,int difficulty){putStart(pos,slot,faction,team,difficulty,-1);}
    public void putStart(BlockPos pos,int slot,String faction,int team,int difficulty,int color){if(slot<0||slot>7||team< -2||team>7||difficulty< -1||difficulty>2)throw new IllegalArgumentException("Invalid army start");starts.put(key(pos),new Start(pos.immutable(),slot,faction,team,difficulty,color));setDirty();}
    public void newStart(BlockPos pos){int slot=0;var used=starts.values().stream().map(Start::slot).toList();while(slot<8&&used.contains(slot))slot++;if(slot<8)putStart(pos,slot,"",slot,-1);}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){var list=new ListTag();for(var s:sites.values()){var n=new CompoundTag();n.putInt("x",s.pos.getX());n.putInt("y",s.pos.getY());n.putInt("z",s.pos.getZ());n.putString("template",s.template);n.putString("kind",s.kind);n.putInt("cash",s.cash);n.putFloat("angle",s.angle);list.add(n);}tag.put("sites",list);var saved=new ListTag();for(var s:starts.values()){var n=new CompoundTag();n.putInt("x",s.pos.getX());n.putInt("y",s.pos.getY());n.putInt("z",s.pos.getZ());n.putInt("slot",s.slot);n.putString("faction",s.faction);n.putInt("team",s.team);n.putInt("difficulty",s.difficulty);n.putInt("color",s.color);saved.add(n);}tag.put("starts",saved);var labels=new ListTag();for(var entry:annotations.entrySet()){var n=new CompoundTag();n.putString("key",entry.getKey());n.putString("name",entry.getValue().name());n.putString("note",entry.getValue().note());labels.add(n);}tag.put("annotations",labels);return tag;}
}
