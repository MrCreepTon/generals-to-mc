package local.generalsmc.nativebridge;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Server-owned driving intents; mounting never transfers ownership of an army. */
final class NativeDriving {
    private final WorldCombat combat;
    private record Intent(UUID player,int actor,double x,double z,boolean enabled,boolean fire,double aimX,double aimY,double aimZ,int target,long lease,long until){}
    private final Map<Integer,Intent> intents=new HashMap<>();
    private long leaseSequence;
    NativeDriving(WorldCombat combat){this.combat=combat;}
    void receive(ServerPlayer player,JsonObject data){
        if(!(player.getVehicle() instanceof NativeActorEntity vehicle)||!combat.canControl(player,vehicle.nativeId))return;
        double forward=data.get("forward").getAsDouble(),side=data.get("side").getAsDouble(),yaw=data.get("yaw").getAsDouble();
        double ax=data.get("x").getAsDouble(),ay=data.get("y").getAsDouble(),az=data.get("z").getAsDouble();
        if(!Double.isFinite(forward)||!Double.isFinite(side)||!Double.isFinite(yaw)||!Double.isFinite(ax)||!Double.isFinite(ay)||!Double.isFinite(az)||Math.abs(forward)>1||Math.abs(side)>1||Math.abs(yaw)>1e8||Math.abs(ax)>3e7||Math.abs(ay)>3e7||Math.abs(az)>3e7)return;
        double angle=Math.toRadians(yaw),x=-Math.sin(angle)*forward+Math.cos(angle)*side,z=Math.cos(angle)*forward+Math.sin(angle)*side,length=Math.hypot(x,z);if(length>1){x/=length;z/=length;}
        boolean enabled=!data.has("enabled")||data.get("enabled").getAsBoolean();var old=intents.get(vehicle.nativeId);long lease=old!=null&&old.enabled()&&old.player().equals(player.getUUID())?old.lease():++leaseSequence;
        intents.put(vehicle.nativeId,new Intent(player.getUUID(),vehicle.nativeId,x,z,enabled,data.get("fire").getAsBoolean(),ax,ay,az,data.has("target")&&!data.get("target").getAsString().isBlank()?combat.drivingTarget(UUID.fromString(data.get("target").getAsString())):0,lease,player.level().getGameTime()+8));
    }
    void tick(){
        var iterator=intents.entrySet().iterator();while(iterator.hasNext()){
            var intent=iterator.next().getValue();var vehicle=combat.actor(intent.actor());var player=combat.serverPlayer(intent.player());
            boolean valid=vehicle!=null&&player!=null&&player.getVehicle()==vehicle&&combat.canControl(player,intent.actor())&&player.level().getGameTime()<=intent.until();
            combat.drive(intent.actor(),valid&&intent.enabled(),intent.x(),intent.z(),valid&&intent.enabled()&&intent.fire(),intent.aimX(),intent.aimY(),intent.aimZ(),intent.target(),intent.lease());if(!valid||!intent.enabled())iterator.remove();
        }
    }
}
