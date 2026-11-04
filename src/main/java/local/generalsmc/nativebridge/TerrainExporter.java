package local.generalsmc.nativebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import java.util.*;

/** Minecraft surface, clearance and obstruction spans consumed by original native pathfinding. */
public final class TerrainExporter {
    public record Column(int x, int y, float height, int type,float clearance,float[] obstacles) {
        @Override public boolean equals(Object value){return value instanceof Column c&&x==c.x&&y==c.y&&Float.compare(height,c.height)==0&&type==c.type&&Float.compare(clearance,c.clearance)==0&&Arrays.equals(obstacles,c.obstacles);}
        @Override public int hashCode(){return 31*Objects.hash(x,y,height,type,clearance)+Arrays.hashCode(obstacles);}
    }
    public record Region(int x, int y, int width, int depth, int originX, int originY, int originZ) {}
    public static List<Column> sample(ServerLevel level, Region r) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Terrain sampling belongs to the server thread");
        if (r.width < 1 || r.depth < 1 || r.width * r.depth > 1024) throw new IllegalArgumentException("Terrain update must fit 1024 columns");
        try(var cache=WorldSurface.sample(level)){return sampleLoaded(level,r);}
    }
    private static List<Column> sampleLoaded(ServerLevel level,Region r){
        // Use the largest nearby moving body, including its rotation envelope. SAGE's
        // path diameter can be much narrower than the Minecraft environmental collision box.
        var area=new net.minecraft.world.phys.AABB(r.originX+r.x,level.getMinY(),r.originZ+r.y,r.originX+r.x+r.width,level.getMaxY(),r.originZ+r.y+r.depth).inflate(32);
        double body=level.getEntitiesOfClass(NativeActorEntity.class,area,a->a.army()&&!a.structure()).stream().mapToDouble(NativeActorEntity::hazardRadius).max().orElse(.3);
        int margin=Math.clamp((int)Math.ceil(body+.75),1,12);
        boolean[][] dangers=new boolean[r.width+margin*2][r.depth+margin*2];
        double[][] clearance = new double[r.width+margin*2][r.depth+margin*2];
        float[][][] obstacles=new float[r.width+margin*2][r.depth+margin*2][];
        double[][] heights = new double[r.width+margin*2][r.depth+margin*2];
        for (int x = -margin; x < r.width+margin; x++) for (int z = -margin; z < r.depth+margin; z++) {
            int wx = r.originX + r.x + x, wz = r.originZ + r.y + z;
            // Sampling must never synchronously generate a chunk on the server tick.
            if(level.getChunkSource().getChunkNow(Math.floorDiv(wx,16),Math.floorDiv(wz,16))==null){heights[x+margin][z+margin]=Double.NaN;continue;}
            double surface=WorldSurface.ground(level,wx,wz);
            heights[x+margin][z+margin] = surface;
            var floor=new BlockPos(wx,(int)Math.floor(surface-.001),wz);
            dangers[x+margin][z+margin]=level.getFluidState(floor).is(net.minecraft.tags.FluidTags.LAVA)||hazard(level.getBlockState(floor))||hazard(level.getBlockState(floor.above()))||hazard(level.getBlockState(floor.above(2)));
            if(x< -1||z< -1||x>r.width||z>r.depth)continue; // Outer border only supplies hazard clearance.
            double space=32;
            var spans=new ArrayList<Float>();
            int obstacleTop=level.getHeight(Heightmap.Types.MOTION_BLOCKING,wx,wz);
            for(int y=(int)Math.floor(surface);y<obstacleTop;y++){
                var p=new BlockPos(wx,y,wz);var shape=level.getBlockState(p).getCollisionShape(level,p);
                if(!shape.isEmpty()&&y+shape.max(Direction.Axis.Y)>surface+.001){
                    float lo=(float)((y+shape.min(Direction.Axis.Y)-r.originY)*WorldScale.UNITS_PER_BLOCK),hi=(float)((y+shape.max(Direction.Axis.Y)-r.originY)*WorldScale.UNITS_PER_BLOCK);
                    if(spans.isEmpty())space=Math.max(0,y+shape.min(Direction.Axis.Y)-surface);
                    if(!spans.isEmpty()&&(lo<=spans.get(spans.size()-1)+.01f||spans.size()>=128))spans.set(spans.size()-1,Math.max(hi,spans.get(spans.size()-1)));
                    else{spans.add(lo);spans.add(hi);}
                }
            }
            clearance[x+margin][z+margin]=space;
            float[] ranges=new float[spans.size()];for(int i=0;i<ranges.length;i++)ranges[i]=spans.get(i);obstacles[x+margin][z+margin]=ranges;
        }
        boolean anyDanger=false;for(var row:dangers)for(boolean danger:row)anyDanger|=danger;
        var result = new ArrayList<Column>();
        for (int x = 0; x < r.width; x++) for (int z = 0; z < r.depth; z++) {
            double height = heights[x+margin][z+margin];if(!Double.isFinite(height))continue;int type = 0;
            var pos = new BlockPos(r.originX+r.x+x,(int)Math.floor(height-.001),r.originZ+r.y+z);
            if(height<=level.getMinY()||clearance[x+margin][z+margin]<1.8)type=6;
            else if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)) type = 6;
            else if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) type = 1;
            // Native paths validate centre cells. Keep a body-width margin so a valid centreline
            // cannot drag a wide unit's Minecraft collision box through a neighbouring cactus/fire.
            if(anyDanger){scan:for(int dx=-margin;dx<=margin;dx++)for(int dz=-margin;dz<=margin;dz++)if(dangers[x+margin+dx][z+margin+dz]&&Math.abs(height-heights[x+margin+dx][z+margin+dz])<1.5){type=6;break scan;}}
            // Height belongs to a transition, not the whole column. A tall wall on
            // one side must not block the one-block exit on the other side.
            result.add(new Column(r.x+x, r.y+z, (float)((height-r.originY)*WorldScale.UNITS_PER_BLOCK), type,(float)(clearance[x+margin][z+margin]*WorldScale.UNITS_PER_BLOCK),obstacles[x+margin][z+margin]));
        }
        return List.copyOf(result);
    }
    private static boolean hazard(net.minecraft.world.level.block.state.BlockState s){return s.is(net.minecraft.world.level.block.Blocks.CACTUS)||s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)||s.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)||s.is(net.minecraft.world.level.block.Blocks.FIRE)||s.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)||s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)||s.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock&&s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT);}
    public static String command(long revision, List<Column> columns) {
        var text = new StringBuilder("TERRAIN3 ").append(revision).append(' ').append(columns.size());
        for (var c : columns){text.append(' ').append(c.x).append(' ').append(c.y).append(' ').append(c.height).append(' ').append(c.type).append(' ').append(c.clearance).append(' ').append(c.obstacles.length/2);for(float v:c.obstacles)text.append(' ').append(v);}
        return text.toString();
    }
}
