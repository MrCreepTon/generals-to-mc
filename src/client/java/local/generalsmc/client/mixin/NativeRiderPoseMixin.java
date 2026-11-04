package local.generalsmc.client.mixin;

import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passenger models and their camera share the native mesh's rendered frame. */
@Mixin(EntityRenderer.class)
abstract class NativeRiderPoseMixin {
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void riderPose(Entity rider,EntityRenderState state,float partial,CallbackInfo ci){
        var view=NativeWorldView.current;
        if(view==null)return;
        var position=view.riderPosition(rider);
        if(position!=null){state.x=position.x;state.y=position.y;state.z=position.z;}
    }
}
