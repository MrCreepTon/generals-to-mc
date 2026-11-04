package local.generalsmc.nativebridge;

import net.minecraft.core.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.*;

/** Outdoor floors and accessible porches; collision above the floor stays an obstacle. */
public final class WorldSurface {
    private WorldSurface() {}
    private static final ThreadLocal<SampleCache> SAMPLE=new ThreadLocal<>();
    static final class SampleCache implements AutoCloseable {
        final Level level;final SampleCache parent;final java.util.Map<Long,Double> tops=new java.util.HashMap<>();
        SampleCache(Level level){this.level=level;parent=SAMPLE.get();SAMPLE.set(this);}
        @Override public void close(){if(parent==null)SAMPLE.remove();else SAMPLE.set(parent);}
    }
    static SampleCache sample(Level level){return new SampleCache(level);}
    private static boolean obstacle(Block block){return block instanceof CactusBlock||block instanceof FenceBlock||block instanceof WallBlock||block instanceof DoorBlock||block instanceof FenceGateBlock;}
    private static double top(Level level,int x,int z){
        var cache=SAMPLE.get();if(cache!=null&&cache.level==level){long key=((long)x<<32)|(z&0xffffffffL);var saved=cache.tops.get(key);if(saved!=null)return saved;double value=readTop(level,x,z);cache.tops.put(key,value);return value;}
        return readTop(level,x,z);
    }
    private static double readTop(Level level,int x,int z){
        if(level instanceof net.minecraft.server.level.ServerLevel server){
            if(server.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16))==null)return Double.NaN;
        }else if(!level.hasChunkAt(new BlockPos(x,level.getMinY(),z)))return Double.NaN;
        for(int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z)-1;y>=level.getMinY();--y){var p=new BlockPos(x,y,z);var state=level.getBlockState(p);
            if(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||obstacle(state.getBlock()))continue;
            if(!level.getFluidState(p).isEmpty())return y+1;
            var shape=state.getCollisionShape(level,p);if(!shape.isEmpty())return y+shape.max(Direction.Axis.Y);
        }return level.getMinY();
    }
    public static double ground(Level level,int x,int z){
        double roof=top(level,x,z);if(!Double.isFinite(roof))return roof;
        // Broad roofs, terraces and bridge decks are usable surfaces in their own right.
        // Keep the lower outdoor route only under narrow roofs and overhangs.
        int width=1,deckDepth=1;
        for(var direction:Direction.Plane.HORIZONTAL)for(int d=1;d<=8;d++){
            double neighbor=top(level,x+direction.getStepX()*d,z+direction.getStepZ()*d);
            if(!Double.isFinite(neighbor)||Math.abs(neighbor-roof)>1.001)break;
            if(direction.getStepX()!=0)width++;else deckDepth++;
        }
        if(width>=9&&deckDepth>=9)return roof;
        var cap=level.getBlockState(new BlockPos(x,(int)Math.floor(roof-.001),z));
        // Natural mountains keep their highest surface. Only built roofs can expose a reachable floor below.
        boolean built=cap.is(BlockTags.PLANKS)||cap.is(BlockTags.STAIRS)||cap.is(BlockTags.SLABS)||cap.is(Blocks.COBBLESTONE)||cap.is(Blocks.MOSSY_COBBLESTONE)||cap.is(Blocks.BRICKS);
        // A thin bridge can be stone too. Require real headroom below its deck; a thick cave roof
        // is not a shortcut to a buried floor. The lower route must connect to outdoor neighbours.
        boolean thinBridge=false;
        for(int depth=1;depth<=2&&!thinBridge;depth++){
            int below=(int)Math.floor(roof-.001)-depth;
            if(level.getBlockState(new BlockPos(x,below,z)).getCollisionShape(level,new BlockPos(x,below,z)).isEmpty()&&level.getBlockState(new BlockPos(x,below-1,z)).getCollisionShape(level,new BlockPos(x,below-1,z)).isEmpty())thinBridge=true;
        }
        if(!built&&!thinBridge)return roof;
        double reference=roof;
        for(int d:new int[]{2,4,8,16})for(var direction:Direction.Plane.HORIZONTAL){double neighbor=top(level,x+direction.getStepX()*d,z+direction.getStepZ()*d);if(Double.isFinite(neighbor))reference=Math.min(reference,neighbor);}
        if(roof<=reference+1.001)return roof;
        for(int y=(int)Math.floor(reference+1);y>=Math.floor(reference-1);--y){var p=new BlockPos(x,y,z);var state=level.getBlockState(p);if(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||obstacle(state.getBlock()))continue;
            var shape=state.getCollisionShape(level,p);if(shape.isEmpty())continue;double floor=y+shape.max(Direction.Axis.Y);
            if(floor>=reference-1.001&&floor<=reference+1.001)return floor;
        }
        return reference; // Solid walls become zero-clearance cells, rather than raising adjacent porch cells to the roof.
    }
}
