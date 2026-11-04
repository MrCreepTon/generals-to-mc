package local.generalsmc.mixin;

import local.generalsmc.nativebridge.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla assumes small entity centres. Source buildings may span several chunks. */
@Mixin(ServerExplosion.class)
public abstract class StructureExplosionMixin {
    @Shadow @Final private net.minecraft.world.level.ExplosionDamageCalculator damageCalculator;
    @Inject(method="hurtEntities",at=@At("HEAD"))
    private void generalsmc$buildings(CallbackInfo ci){
        var blast=(ServerExplosion)(Object)this;
        StructureExplosions.hurt(blast,damageCalculator);
    }
    @Redirect(method="hurtEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean generalsmc$once(Entity entity,net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source,float amount){
        if(entity instanceof NativeActorEntity actor&&actor.structure())return false;
        return entity.hurtServer(level,source,amount);
    }
}
