package local.generalsmc.client.mixin;

import local.generalsmc.nativebridge.MobDisableState;
import local.generalsmc.client.MobDisableVisual;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LivingEntityRenderer.class)
public abstract class MobDisableTint {
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void generalsStatus(LivingEntity mob,LivingEntityRenderState state,float partial,CallbackInfo ci){((MobDisableVisual)state).generalsDisabled(mob instanceof MobDisableState s&&s.generalsDisabled());}
    @Inject(method="getModelTint",at=@At("RETURN"),cancellable=true)
    private void generalsBlue(LivingEntityRenderState state,CallbackInfoReturnable<Integer> result){if(((MobDisableVisual)state).generalsDisabled())result.setReturnValue(net.minecraft.util.ARGB.multiply(result.getReturnValue(),0xff285fff));}
}
