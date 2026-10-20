package local.generalsmc.mixin;

import local.generalsmc.nativebridge.WorldViewSubscription;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve vanilla despawn rules, measuring the active RTS observer as well as the player body. */
@Mixin(Mob.class)
public abstract class CameraMobDespawnMixin {
    @Redirect(method="checkDespawn",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"))
    private double generalsCameraDespawn(Entity observer,Entity mob){return WorldViewSubscription.despawnDistanceSquared(observer,mob);}
}
