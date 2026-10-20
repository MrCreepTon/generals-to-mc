package local.generalsmc.nativebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import java.nio.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Moving radar window sampled from loaded Minecraft chunks on their owning thread. */
public final class RadarSynchronizer implements AutoCloseable {
    private final NativeSession session;
    private final java.util.function.Consumer<byte[]> receiver;
    private final ServerLevel level;
    private final int originX,originZ;
    private final Map<Long,Integer> explored=new LinkedHashMap<>(16384,.75f,true);
    private CompletableFuture<byte[]> pending;
    private byte[] ready;
    private byte[] previousPixels;
    private int sampledX=Integer.MIN_VALUE,sampledZ=Integer.MIN_VALUE;
    private long revision;
    private int cooldown;
    private boolean closed;
    public RadarSynchronizer(NativeSession session,ServerLevel level,int originX,int originZ){this(session,level,originX,originZ,null);}
    public RadarSynchronizer(ServerLevel level,int originX,int originZ,java.util.function.Consumer<byte[]> receiver){this(null,level,originX,originZ,receiver);}
    private RadarSynchronizer(NativeSession session,ServerLevel level,int originX,int originZ,java.util.function.Consumer<byte[]> receiver){this.session=session;this.level=level;this.originX=originX;this.originZ=originZ;this.receiver=receiver;}
    public void tick(double cameraX,double cameraZ) throws IOException {
        if(closed)return;
        int minX=Math.floorDiv((int)Math.floor(cameraX),16)*16-64,minZ=Math.floorDiv((int)Math.floor(cameraZ),16)*16-64;
        if(minX!=sampledX||minZ!=sampledZ)cooldown=0;
        if(pending!=null&&pending.isDone()){ready=pending.join();pending=null;}
        if(ready!=null&&previousPixels!=null&&Arrays.equals(previousPixels,0,28,ready,0,28)&&Arrays.equals(previousPixels,36,previousPixels.length,ready,36,ready.length)){ready=null;cooldown=20;}
        if(ready!=null){
            if(receiver!=null){receiver.accept(ready);previousPixels=ready;ready=null;cooldown=20;}
            else{
            Path temp=session.directory.resolve("radar.tmp");Files.write(temp,ready);
            try{Files.move(temp,session.directory.resolve("radar.bin"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);previousPixels=ready;ready=null;cooldown=20;}
            catch(AccessDeniedException busy){return;}
            }
        }
        if(pending!=null||cooldown-->0)return;
        sampledX=minX;sampledZ=minZ;
        long next=++revision;
        pending=CompletableFuture.supplyAsync(()->sample(minX,minZ,next),level.getServer());
    }
    private byte[] sample(int minX,int minZ,long revision){
        var data=ByteBuffer.allocate(36+128*128*4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(0x524d485a).putInt(128).putInt(128).putFloat((minX-originX)*(float)WorldScale.UNITS_PER_BLOCK).putFloat((minZ-originZ)*(float)WorldScale.UNITS_PER_BLOCK).putFloat((float)(128*WorldScale.UNITS_PER_BLOCK)).putFloat((float)(128*WorldScale.UNITS_PER_BLOCK)).putLong(revision);
        var pos=new BlockPos.MutableBlockPos();
        for(int z=0;z<128;z++)for(int x=0;x<128;x++){
            int wx=minX+x,wz=minZ+z;long key=((long)wx<<32)|(wz&0xffffffffL);
            int color=explored.getOrDefault(key,0xff000000);
            var chunk=level.getChunkSource().getChunkNow(Math.floorDiv(wx,16),Math.floorDiv(wz,16));
            if(chunk!=null){
                int top=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,wx&15,wz&15);
                pos.set(wx,top,wz);color=0xff000000|level.getBlockState(pos).getMapColor(level,pos).col;
                // Keep real map colours, with quieter terrain behind the original team-colour blips.
                int red=(color>>16)&255,green=(color>>8)&255,blue=color&255;
                color=0xff000000|((red*3/4)<<16)|((green*3/4)<<8)|(blue*3/4);
                explored.put(key,color);
            }
            data.putInt(color);
        }
        while(explored.size()>262144)explored.remove(explored.keySet().iterator().next());
        return data.array();
    }
    @Override public void close(){closed=true;if(pending!=null)pending.cancel(false);}
}
