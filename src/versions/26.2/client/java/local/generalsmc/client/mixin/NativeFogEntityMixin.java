package local.generalsmc.client.mixin;

import local.generalsmc.client.NativeFog;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class NativeFogEntityMixin {
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private <E extends Entity> void generalsShroud(E entity,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> result){
        if(entity instanceof LivingEntity&&!NativeFog.visible(entity.getX(),entity.getZ()))result.setReturnValue(false);
    }
}
