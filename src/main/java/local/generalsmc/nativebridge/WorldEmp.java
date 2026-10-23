package local.generalsmc.nativebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Temporary suppression. Never replaces circuit blocks or changes a player's inventory. */
public final class WorldEmp implements AutoCloseable {
    private static final Map<ServerLevel,WorldEmp> ACTIVE=new IdentityHashMap<>();
    private record Zone(Vec3 center,double radius,long until){}
    private record Stun(long until){}
    private final ServerLevel level;
    private final List<Zone> zones=new ArrayList<>();
    private final Map<UUID,Stun> stunned=new HashMap<>();
    private static final class Scan {final Vec3 center;final double radius;final Iterator<BlockPos> positions;boolean repeat;Scan(Vec3 c,double r,Iterator<BlockPos> p){center=c;radius=r;positions=p;}}
    private final ArrayDeque<Scan> updates=new ArrayDeque<>();
    public WorldEmp(ServerLevel level){this.level=level;}
    public static boolean suppressed(BlockGetter world,BlockPos pos){
        if(!(world instanceof ServerLevel level))return false;
        var emp=ACTIVE.get(level);if(emp==null)return false;
        if(!WorldRules.get(level).values().emp().redstone())return false;
        for(var zone:emp.zones)if(zone.until>level.getGameTime()&&pos.distToCenterSqr(zone.center)<zone.radius*zone.radius)return true;
        return false;
    }
    public static boolean stunned(Mob mob){if(!(mob.level() instanceof ServerLevel level))return false;var emp=ACTIVE.get(level);if(emp==null||!WorldRules.get(level).values().emp().mobs())return false;var stun=emp.stunned.get(mob.getUUID());return stun!=null&&stun.until>level.getGameTime();}
    public void pulse(Vec3 center,double radius,int duration){
        if(!Double.isFinite(radius)||radius<=0||duration<=0)return;
        ACTIVE.put(level,this);radius=Math.min(32,radius);long until=level.getGameTime()+Math.clamp(duration,1,20*120);
        var rules=WorldRules.get(level).values().emp();
        if(rules.mobs())for(var mob:level.getEntitiesOfClass(Mob.class,new AABB(center,center).inflate(radius),m->m.isAlive()&&!(m instanceof NativeActorEntity))){
            if(mob.getBoundingBox().getCenter().distanceToSqr(center)>radius*radius)continue;
            var old=stunned.get(mob.getUUID());stunned.put(mob.getUUID(),new Stun(Math.max(until,old==null?0:old.until)));
            mob.getNavigation().stop();mob.setTarget(null);mob.setDeltaMovement(Vec3.ZERO);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,center.x,center.y+.4,center.z,24,radius*.25,.3,radius*.25,.08);
        if(rules.redstone()){
            var zone=new Zone(center,radius,until);
            // Keep overlapping impulses bounded; extending an identical zone preserves its expiry updates.
            boolean existing=zones.stream().anyMatch(z->z.center.distanceToSqr(zone.center)<.01&&z.radius==zone.radius);
            if(!existing&&(zones.size()>=64||updates.size()>=64))return;
            zones.removeIf(z->z.center.distanceToSqr(zone.center)<.01&&z.radius==zone.radius);zones.add(zone);schedule(zone,false);
        }
    }
    private void schedule(Zone z,boolean restore){
        for(var scan:updates)if(scan.center.distanceToSqr(z.center)<.01&&scan.radius==z.radius){scan.repeat|=restore;return;}
        int r=(int)Math.ceil(z.radius);var p=BlockPos.containing(z.center);
        updates.add(new Scan(z.center,z.radius,BlockPos.betweenClosed(p.offset(-r,-r,-r),p.offset(r,r,r)).iterator()));
    }
    public void tick(){
        long now=level.getGameTime();var rules=WorldRules.get(level).values().emp();
        stunned.entrySet().removeIf(e->{var entity=level.getEntity(e.getKey());if(!(entity instanceof Mob mob))return now>=e.getValue().until;
            if(now<e.getValue().until&&rules.mobs()){var velocity=mob.getDeltaMovement();mob.setDeltaMovement(0,velocity.y,0);if(now%10==0)level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,mob.getX(),mob.getY()+mob.getBbHeight()*.6,mob.getZ(),2,.2,.2,.2,.02);return false;}return true;});
        zones.removeIf(z->{if(now<z.until&&rules.redstone())return false;schedule(z,true);return true;});
        for(int budget=4096;budget>0&&!updates.isEmpty();budget--){
            var scan=updates.peek();var iter=scan.positions;if(!iter.hasNext()){updates.remove();if(scan.repeat)schedule(new Zone(scan.center,scan.radius,now),false);continue;}
            var pos=iter.next();if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            if(state.isSignalSource()||state.is(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE)||state.is(net.minecraft.world.level.block.Blocks.REDSTONE_LAMP))level.updateNeighborsAt(pos,state.getBlock());
        }
    }
    @Override public void close(){
        for(var zone:zones)schedule(zone,true);zones.clear();stunned.clear();
        // Circuit queries stop being suppressed immediately; drain remaining notifications on world ticks.
        if(updates.isEmpty())ACTIVE.remove(level);
    }
    public static void stopServer(net.minecraft.server.MinecraftServer server){ACTIVE.keySet().removeIf(level->level.getServer()==server);}
    public static void worldTick(ServerLevel level){var emp=ACTIVE.get(level);if(emp!=null){emp.tick();if(emp.zones.isEmpty()&&emp.stunned.isEmpty()&&emp.updates.isEmpty())ACTIVE.remove(level);}}
}
