package local.generalsmc.nativebridge;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
/** Modern ticket types have no owner key: retain overlapping subscriptions explicitly. */
public final class ModernTickets {
 public static void register(){}
 private static final TicketType TYPE=net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE,net.minecraft.resources.Identifier.fromNamespaceAndPath("generalsmc","world_views"),new TicketType(Long.MAX_VALUE,TicketType.FLAG_LOADING|TicketType.FLAG_SIMULATION|TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
 private record Key(ServerLevel level,ChunkPos pos,int radius){}
 private static final Map<Key,Integer> references=new HashMap<>();
 static {net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->references.keySet().removeIf(key->key.level.getServer()==server));}
 static void add(ServerLevel world,Object role,ChunkPos pos,int radius,Object owner){var key=new Key(world,pos,radius);int count=references.getOrDefault(key,0);references.put(key,count+1);if(count==0)world.getChunkSource().addTicketWithRadius(TYPE,pos,radius);}
 static void remove(ServerLevel world,Object role,ChunkPos pos,int radius,Object owner){var key=new Key(world,pos,radius);int count=references.getOrDefault(key,0);if(count<=1){if(references.remove(key)!=null)world.getChunkSource().removeTicketWithRadius(TYPE,pos,radius);}else references.put(key,count-1);}
}
