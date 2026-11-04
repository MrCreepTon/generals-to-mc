package local.generalsmc.mixin;

import local.generalsmc.nativebridge.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Broad building boxes select candidates; rays must hit their rotated solid footprint. */
@Mixin(ProjectileUtil.class)
abstract class NativeStructureRayMixin {
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",at=@At("RETURN"),cancellable=true)
    private static void projectile(Level level,Entity viewer,Vec3 start,Vec3 end,AABB area,Predicate<Entity> predicate,float margin,CallbackInfoReturnable<EntityHitResult> result){
        if(result.getReturnValue()==null||!(result.getReturnValue().getEntity() instanceof NativeActorEntity actor)||!actor.structure())return;
        var other=ProjectileUtil.getEntityHitResult(level,viewer,start,end,area,predicate.and(e->!(e instanceof NativeActorEntity a&&a.structure())),margin);
        result.setReturnValue(precise(level,viewer,start,end,area,predicate,other,Double.MAX_VALUE,false));
    }
    @Inject(method="getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",at=@At("RETURN"),cancellable=true)
    private static void pick(Entity viewer,Vec3 start,Vec3 end,AABB area,Predicate<Entity> predicate,double distance,CallbackInfoReturnable<EntityHitResult> result){
        if(result.getReturnValue()==null||!(result.getReturnValue().getEntity() instanceof NativeActorEntity actor)||!actor.structure())return;
        var other=ProjectileUtil.getEntityHitResult(viewer,start,end,area,predicate.and(e->!(e instanceof NativeActorEntity a&&a.structure())),distance);
        result.setReturnValue(precise(viewer.level(),viewer,start,end,area,predicate,other,distance,true));
    }
    private static EntityHitResult precise(Level level,Entity viewer,Vec3 start,Vec3 end,AABB area,Predicate<Entity> predicate,EntityHitResult other,double distance,boolean picking){
        double nearest=other==null?distance:start.distanceToSqr(other.getLocation());var result=other;
        for(var actor:StructureCollisions.buildings(level,area))if(actor!=viewer&&predicate.test(actor)&&(!picking||actor.getRootVehicle()!=viewer.getRootVehicle())){var hit=StructureCollisions.ray(actor,start,end);if(hit!=null&&start.distanceToSqr(hit)<nearest){nearest=start.distanceToSqr(hit);result=new EntityHitResult(actor,hit);}}
        return result;
    }
}
