package local.generalsmc.nativebridge;

import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Camera chunk tickets and vanilla chunk subscriptions without moving the commander. */
public final class WorldViewSubscription implements AutoCloseable {
    private static final TicketType<UUID> TYPE=TicketType.create("generalsmc_camera",UUID::compareTo);
    private static final Map<UUID,WorldViewSubscription> ACTIVE=new java.util.concurrent.ConcurrentHashMap<>();
    private final ServerLevel level;
    private final UUID commander;
    private volatile ChunkPos requested;
    private volatile int radius;
    private ChunkPos loaded;
    private int loadedRadius;
    public WorldViewSubscription(ServerLevel level,UUID commander){this.level=level;this.commander=commander;var old=ACTIVE.put(commander,this);if(old!=null)old.close();}
    public void focus(double x,double z,int distance,boolean controlling){requested=controlling?new ChunkPos((int)Math.floor(x/16),(int)Math.floor(z/16)):null;radius=Math.max(2,Math.min(8,distance))+1;}
    public static ChunkPos center(ServerPlayer player){var service=ACTIVE.get(player.getUUID());return service!=null&&service.level==player.level()?service.requested:null;}
    public static net.minecraft.world.phys.Vec3 trackingPosition(ServerPlayer player){var focus=center(player);return focus==null?player.position():new net.minecraft.world.phys.Vec3(focus.getMiddleBlockX(),player.getY(),focus.getMiddleBlockZ());}
    public static double despawnDistanceSquared(net.minecraft.world.entity.Entity observer,net.minecraft.world.entity.Entity mob){
        double body=observer.distanceToSqr(mob);
        if(!(observer instanceof ServerPlayer player))return body;
        var focus=center(player);if(focus==null)return body;
        double dx=mob.getX()-focus.getMiddleBlockX(),dz=mob.getZ()-focus.getMiddleBlockZ();
        return Math.min(body,dx*dx+dz*dz);
    }
    public static void tick(ServerLevel level){for(var service:ACTIVE.values())if(service.level==level)service.tick();}
    private void tick(){var next=requested;int size=radius;if(Objects.equals(next,loaded)&&size==loadedRadius)return;if(next!=null)level.getChunkSource().addRegionTicket(TYPE,next,size,commander);if(loaded!=null)level.getChunkSource().removeRegionTicket(TYPE,loaded,loadedRadius,commander);loaded=next;loadedRadius=size;}
    @Override public void close(){requested=null;level.getServer().execute(()->{tick();ACTIVE.remove(commander,this);});}
}
