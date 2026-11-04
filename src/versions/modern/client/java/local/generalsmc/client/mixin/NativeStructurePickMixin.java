package local.generalsmc.client.mixin;

import local.generalsmc.nativebridge.NativeActorEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** A commander starts inside the army base: its broad proxy must not swallow block clicks. */
@Mixin(LocalPlayer.class)
public abstract class NativeStructurePickMixin {
    @Redirect(method="pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"))
    private static EntityHitResult generalsBlockPick(Entity viewer,Vec3 eye,Vec3 end,AABB area,Predicate<Entity> predicate,double distance){
        return ProjectileUtil.getEntityHitResult(viewer,eye,end,area,predicate.and(target->!(target instanceof NativeActorEntity actor&&actor.structure()&&actor.getBoundingBox().inflate(.001).contains(eye))),distance);
    }
    @Redirect(method="raycastHitResult",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/component/AttackRange;getClosesetHit(Lnet/minecraft/world/entity/Entity;FLjava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult generalsWeaponPick(net.minecraft.world.item.component.AttackRange range,Entity viewer,float partial,Predicate<Entity> predicate){var eye=viewer.getEyePosition(partial);return range.getClosesetHit(viewer,partial,predicate.and(target->!(target instanceof NativeActorEntity actor&&actor.structure()&&actor.getBoundingBox().inflate(.001).contains(eye))));}
}
