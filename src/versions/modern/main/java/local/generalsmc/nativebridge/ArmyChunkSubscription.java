package local.generalsmc.nativebridge;

import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Occupied army chunks keep ticking, and a short route segment loads before the unit reaches it. */
final class ArmyChunkSubscription implements AutoCloseable {
    private static final String TYPE="bridge";
    private final ServerLevel level;private final int ox,oz;
    private final Map<ChunkPos,Long> loaded=new LinkedHashMap<>();
    private boolean closed;
    ArmyChunkSubscription(ServerLevel level,int x,int z){this.level=level;ox=x;oz=z;}
    private void area(Set<ChunkPos> desired,double x,double z){
        var pos=new ChunkPos((int)Math.floor((ox+x/WorldScale.UNITS_PER_BLOCK)/16),(int)Math.floor((oz+z/WorldScale.UNITS_PER_BLOCK)/16));
        // A level30 ticket ticks entities in the centre and its immediate neighbours.
        if(desired.stream().noneMatch(p->Math.abs(p.x-pos.x)<=1&&Math.abs(p.z-pos.z)<=1))desired.add(pos);
    }
    void tick(NativeSession.State state){
        if(closed||level.getGameTime()%5!=0)return;var desired=new LinkedHashSet<ChunkPos>();
        for(var a:state.objects())if(a.owner()>=2&&a.health()>0&&!a.contained()&&(a.externalKey()==null||a.externalKey().isEmpty()))area(desired,a.x(),a.y());
        for(var a:state.objects())if(a.owner()>=2&&a.health()>0&&!a.contained()&&a.groundMovement()&&(a.externalKey()==null||a.externalKey().isEmpty())){
            double dx=a.goalX()-a.x(),dz=a.goalY()-a.y(),length=Math.hypot(dx,dz),fraction=length<.01?0:Math.min(1,24*WorldScale.UNITS_PER_BLOCK/length);area(desired,a.x()+dx*fraction,a.y()+dz*fraction);
        }
        long now=level.getGameTime();int budget=4;
        for(var p:desired){if(loaded.containsKey(p))loaded.put(p,now);else if(budget-->0){ModernTickets.add(level,TYPE,p,3,p);loaded.put(p,now);}}
        var it=loaded.entrySet().iterator();while(it.hasNext()){var entry=it.next();if(!desired.contains(entry.getKey())&&now-entry.getValue()>40){ModernTickets.remove(level,TYPE,entry.getKey(),3,entry.getKey());it.remove();}}
    }
    @Override public void close(){closed=true;for(var pos:loaded.keySet())ModernTickets.remove(level,TYPE,pos,3,pos);loaded.clear();}
}
