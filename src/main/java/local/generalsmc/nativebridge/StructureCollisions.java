package local.generalsmc.nativebridge;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Vanilla's entity broad phase assumes small bodies; original bases can span many chunks. */
public final class StructureCollisions {
    private record Entry(AABB box,Set<Long> cells){}
    private static final class Index {
        final Map<NativeActorEntity,Entry> entries=new IdentityHashMap<>();
        final Map<Long,Set<NativeActorEntity>> cells=new HashMap<>();
    }
    private static final Map<Level,Index> worlds=new IdentityHashMap<>();
    private static Set<Long> cells(AABB box){var out=new HashSet<Long>();for(int x=(int)Math.floor(box.minX/16);x<=(int)Math.floor(box.maxX/16);x++)for(int z=(int)Math.floor(box.minZ/16);z<=(int)Math.floor(box.maxZ/16);z++)out.add(((long)x<<32)|(z&0xffffffffL));return out;}
    public static synchronized void update(NativeActorEntity actor){
        if(!actor.canBeCollidedWith()){remove(actor);return;}var index=worlds.computeIfAbsent(actor.level(),l->new Index());var old=index.entries.get(actor);var box=actor.getBoundingBox();if(old!=null&&old.box.equals(box))return;
        if(old!=null)erase(index,actor,old);var entry=new Entry(box,cells(box));index.entries.put(actor,entry);for(long cell:entry.cells)index.cells.computeIfAbsent(cell,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(actor);
    }
    private static void erase(Index index,NativeActorEntity actor,Entry entry){index.entries.remove(actor);for(long cell:entry.cells){var set=index.cells.get(cell);set.remove(actor);if(set.isEmpty())index.cells.remove(cell);}}
    public static synchronized void remove(NativeActorEntity actor){var index=worlds.get(actor.level());if(index==null)return;var old=index.entries.get(actor);if(old!=null)erase(index,actor,old);if(index.entries.isEmpty())worlds.remove(actor.level());}
    public static synchronized List<VoxelShape> supplement(Level level,Entity mover,AABB area,List<VoxelShape> vanilla){
        var index=worlds.get(level);if(index==null||mover instanceof NativeActorEntity)return vanilla;var candidates=Collections.newSetFromMap(new IdentityHashMap<NativeActorEntity,Boolean>());for(long cell:cells(area)){var set=index.cells.get(cell);if(set!=null)candidates.addAll(set);}if(candidates.isEmpty())return vanilla;
        var result=new ArrayList<>(vanilla);for(var actor:candidates)if(!actor.isRemoved()&&actor.canBeCollidedWith()&&actor!=mover&&!mover.isPassengerOfSameVehicle(actor)&&actor.getBoundingBox().intersects(area))result.add(Shapes.create(actor.getBoundingBox()));return result;
    }
    public static synchronized boolean blocksNavigation(Level level,Entity mover,AABB area){
        if(mover instanceof NativeActorEntity)return false;var index=worlds.get(level);if(index==null)return false;
        for(long cell:cells(area)){var actors=index.cells.get(cell);if(actors==null)continue;for(var actor:actors){var box=actor.getBoundingBox();if(!actor.isRemoved()&&actor.canBeCollidedWith()&&box.intersects(area)&&!box.intersects(mover.getBoundingBox()))return true;}}
        return false;
    }
    public static synchronized Set<NativeActorEntity> buildings(Level level,AABB area){
        var found=Collections.newSetFromMap(new IdentityHashMap<NativeActorEntity,Boolean>());var index=worlds.get(level);if(index==null)return found;
        for(long cell:cells(area)){var actors=index.cells.get(cell);if(actors!=null)for(var actor:actors)if(!actor.isRemoved()&&actor.canBeCollidedWith()&&actor.getBoundingBox().intersects(area))found.add(actor);}return found;
    }
}
