package local.generalsmc.nativebridge;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Vanilla's entity broad phase assumes small bodies; original bases can span many chunks. */
public final class StructureCollisions {
    private record Entry(AABB box,Set<Long> cells,int footprint,VoxelShape shape){}
    private static final class Index {
        final Map<NativeActorEntity,Entry> entries=new IdentityHashMap<>();
        final Map<Long,Set<NativeActorEntity>> cells=new HashMap<>();
    }
    private static final Map<Level,Index> worlds=new IdentityHashMap<>();
    private static Set<Long> cells(AABB box){var out=new HashSet<Long>();for(int x=(int)Math.floor(box.minX/16);x<=(int)Math.floor(box.maxX/16);x++)for(int z=(int)Math.floor(box.minZ/16);z<=(int)Math.floor(box.maxZ/16);z++)out.add(((long)x<<32)|(z&0xffffffffL));return out;}
    public static synchronized void update(NativeActorEntity actor){
        if(!actor.structure()||!actor.canBeCollidedWith()){remove(actor);return;}var index=worlds.computeIfAbsent(actor.level(),l->new Index());var old=index.entries.get(actor);var box=actor.getBoundingBox();if(old!=null&&old.box.equals(box)&&old.footprint==actor.footprintKey())return;
        if(old!=null)erase(index,actor,old);var entry=new Entry(box,cells(box),actor.footprintKey(),actor.structureShape());index.entries.put(actor,entry);for(long cell:entry.cells)index.cells.computeIfAbsent(cell,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(actor);
    }
    private static void erase(Index index,NativeActorEntity actor,Entry entry){index.entries.remove(actor);for(long cell:entry.cells){var set=index.cells.get(cell);set.remove(actor);if(set.isEmpty())index.cells.remove(cell);}}
    public static synchronized void remove(NativeActorEntity actor){var index=worlds.get(actor.level());if(index==null)return;var old=index.entries.get(actor);if(old!=null)erase(index,actor,old);if(index.entries.isEmpty())worlds.remove(actor.level());}
    public static synchronized List<VoxelShape> supplement(Level level,Entity mover,AABB area,List<VoxelShape> vanilla){
        var index=worlds.get(level);if(index==null||mover instanceof NativeActorEntity)return vanilla;var candidates=Collections.newSetFromMap(new IdentityHashMap<NativeActorEntity,Boolean>());for(long cell:cells(area)){var set=index.cells.get(cell);if(set!=null)candidates.addAll(set);}if(candidates.isEmpty())return vanilla;
        var broad=new HashSet<AABB>();for(var actor:candidates)broad.add(actor.getBoundingBox());var result=new ArrayList<VoxelShape>();for(var shape:vanilla)if(shape.isEmpty()||!broad.contains(shape.bounds()))result.add(shape);for(var actor:candidates)if(!actor.isRemoved()&&actor.canBeCollidedWith()&&actor!=mover&&(mover==null||!mover.isPassengerOfSameVehicle(actor))&&actor.getBoundingBox().intersects(area)){var entry=index.entries.get(actor);if(entry!=null&&Shapes.joinIsNotEmpty(entry.shape,Shapes.create(area),BooleanOp.AND)&&(mover==null||!Shapes.joinIsNotEmpty(entry.shape,Shapes.create(mover.getBoundingBox().deflate(1e-6)),BooleanOp.AND)))result.add(entry.shape);}return result;
    }
    public static synchronized net.minecraft.world.phys.Vec3 ray(NativeActorEntity actor,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end){var index=worlds.get(actor.level());var entry=index==null?null:index.entries.get(actor);if(entry==null)return null;var hit=entry.shape.clip(start,end,net.minecraft.core.BlockPos.ZERO);return hit==null?null:hit.getLocation();}
    public static synchronized boolean blocksPlacement(Level level,Entity except,VoxelShape shape){
        if(shape.isEmpty())return false;var index=worlds.get(level);if(index==null)return false;
        for(var actor:buildings(level,shape.bounds()))if(actor!=except&&(except==null||!except.isPassengerOfSameVehicle(actor))){var entry=index.entries.get(actor);if(entry!=null&&Shapes.joinIsNotEmpty(entry.shape,shape,BooleanOp.AND))return true;}return false;
    }
    public static synchronized boolean blocksNavigation(Level level,Entity mover,AABB area){
        if(mover instanceof NativeActorEntity)return false;var index=worlds.get(level);if(index==null)return false;
        for(long cell:cells(area)){var actors=index.cells.get(cell);if(actors==null)continue;for(var actor:actors){var box=actor.getBoundingBox();if(!actor.isRemoved()&&actor.canBeCollidedWith()&&box.intersects(area)&&Shapes.joinIsNotEmpty(index.entries.get(actor).shape,Shapes.create(area),BooleanOp.AND)&&!Shapes.joinIsNotEmpty(index.entries.get(actor).shape,Shapes.create(mover.getBoundingBox()),BooleanOp.AND))return true;}}
        return false;
    }
    public static synchronized Set<NativeActorEntity> buildings(Level level,AABB area){
        var found=Collections.newSetFromMap(new IdentityHashMap<NativeActorEntity,Boolean>());var index=worlds.get(level);if(index==null)return found;
        for(long cell:cells(area)){var actors=index.cells.get(cell);if(actors!=null)for(var actor:actors)if(!actor.isRemoved()&&actor.canBeCollidedWith()&&actor.getBoundingBox().intersects(area))found.add(actor);}return found;
    }
}
