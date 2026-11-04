package local.generalsmc.mixin;

import local.generalsmc.nativebridge.NativeActorEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class NativeArrowHitMixin {
    @Unique private int generalsArrowCount;
    @Inject(method="onHitEntity",at=@At("HEAD"))
    private void beforeNativeHit(EntityHitResult hit,CallbackInfo ci){generalsArrowCount=hit.getEntity() instanceof NativeActorEntity actor?actor.getArrowCount():-1;}
    @Inject(method="onHitEntity",at=@At("RETURN"))
    private void afterNativeHit(EntityHitResult hit,CallbackInfo ci){if(hit.getEntity() instanceof NativeActorEntity actor&&actor.getArrowCount()>generalsArrowCount)actor.stickArrow(hit.getLocation(),((AbstractArrow)(Object)this).getDeltaMovement());}
}
