package local.generalsmc.nativebridge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.*;
import java.util.*;

/** Server-side adaptations of native disabling damage and actual ECM weapon pulses. */
public final class WorldTechnology implements AutoCloseable {
    private record Charge(double amount,long last,long stunnedUntil,boolean emp) {}
    private record Field(Vec3 center,double radius,long until) {}
    private static final Map<ServerLevel,WorldTechnology> ACTIVE=new IdentityHashMap<>();
    private final ServerLevel level;
    private final Map<UUID,Charge> charges=new HashMap<>();
    private final Map<Integer,Field> fields=new HashMap<>();
    private final Map<UUID,Long> deflected=new HashMap<>();
    WorldTechnology(ServerLevel level){this.level=level;}
    public static boolean stunned(Mob mob){if(!(mob.level() instanceof ServerLevel world))return false;var service=ACTIVE.get(world);if(service==null)return false;var charge=service.charges.get(mob.getUUID());return charge!=null&&charge.stunnedUntil>world.getGameTime()&&(charge.emp?WorldRules.get(world).values().emp().mobs():WorldRules.get(world).values().options().microwaveMobs());}
    void disable(Mob mob,double amount,double threshold,boolean emp){
        if(!(emp?WorldRules.get(level).values().emp().mobs():WorldRules.get(level).values().options().microwaveMobs())||amount<=0)return;
        ACTIVE.put(level,this);long now=level.getGameTime();var previous=charges.get(mob.getUUID());double accumulated=amount+(previous!=null&&previous.emp==emp&&now-previous.last<=20?previous.amount:0);
        long until=accumulated>=threshold?now+40:previous==null?0:previous.stunnedUntil;
        charges.put(mob.getUUID(),new Charge(Math.min(threshold,accumulated),now,Math.max(until,previous==null?0:previous.stunnedUntil),emp));
        if(until>now){mob.getNavigation().stop();mob.setTarget(null);mob.setDeltaMovement(Vec3.ZERO);}
    }
    void microwave(Vec3 center,double radius){
        if(!WorldRules.get(level).values().options().microwaveMobs()||!Double.isFinite(radius)||radius<=0)return;
        ACTIVE.put(level,this);long now=level.getGameTime();double reach=Math.min(32,radius);
        for(var mob:level.getEntitiesOfClass(Mob.class,new AABB(center,center).inflate(reach),m->m.isAlive()&&!(m instanceof NativeActorEntity))){
            if(mob.getBoundingBox().distanceToSqr(center)>reach*reach)continue;
            charges.put(mob.getUUID(),new Charge(0,now,Math.max(now+12,charges.containsKey(mob.getUUID())?charges.get(mob.getUUID()).stunnedUntil:0),false));mob.getNavigation().stop();mob.setTarget(null);
        }
    }
    void jam(int source,Vec3 center,double radius){if(WorldRules.get(level).values().options().ecmArrows()&&Double.isFinite(radius)&&radius>0&&(fields.containsKey(source)||fields.size()<32))fields.put(source,new Field(center,Math.min(32,radius),level.getGameTime()+20));}
    void tick(){
        long now=level.getGameTime();var options=WorldRules.get(level).values().options();
        charges.entrySet().removeIf(e->{if(!(e.getValue().emp?WorldRules.get(level).values().emp().mobs():options.microwaveMobs())||now>e.getValue().stunnedUntil&&now-e.getValue().last>20||!(level.getEntity(e.getKey()) instanceof Mob mob)||!mob.isAlive())return true;if(e.getValue().stunnedUntil>now){var velocity=mob.getDeltaMovement();mob.setDeltaMovement(0,velocity.y,0);mob.getNavigation().stop();}return false;});
        deflected.entrySet().removeIf(e->e.getValue()<=now);fields.values().removeIf(f->f.until<=now||!options.ecmArrows());
        for(var field:fields.values())for(var arrow:level.getEntitiesOfClass(AbstractArrow.class,new AABB(field.center,field.center).inflate(field.radius),a->a.isAlive()&&a.getOwner() instanceof Mob&&!deflected.containsKey(a.getUUID()))){
            var position=arrow.position();var velocity=arrow.getDeltaMovement();if(position.distanceToSqr(field.center)>field.radius*field.radius||velocity.lengthSqr()<.01||velocity.dot(field.center.subtract(position))<=0)continue;
            var away=position.subtract(field.center).multiply(1,0,1);if(away.lengthSqr()<.001)away=new Vec3(1,0,0);
            double side=(arrow.getUUID().getLeastSignificantBits()&1)==0?1:-1;var direction=away.normalize().add(-away.z*side*.15,0,away.x*side*.15).normalize();
            arrow.setDeltaMovement(direction.scale(Math.sqrt(velocity.x*velocity.x+velocity.z*velocity.z)).add(0,velocity.y,0));arrow.hurtMarked=true;
            deflected.put(arrow.getUUID(),now+40);level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,position.x,position.y,position.z,3,.08,.08,.08,.01);
        }
    }
    @Override public void close(){charges.clear();fields.clear();deflected.clear();ACTIVE.remove(level,this);}
}
