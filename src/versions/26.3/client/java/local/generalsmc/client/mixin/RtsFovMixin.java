package local.generalsmc.client.mixin;
import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Camera.class)
abstract class RtsFovMixin {
 @Inject(method="calculateFov",at=@At("HEAD"),cancellable=true)
 private void tacticalFov(float partial,CallbackInfoReturnable<Float> ci){if(NativeWorldView.controlling())ci.setReturnValue(60f);}
 @Inject(method="extractRenderState",at=@At("TAIL"))
 private void tacticalState(CameraRenderState state,DeltaTracker delta,CallbackInfo ci){if(NativeWorldView.controlling()){state.entityRenderState.bob=0;state.entityRenderState.hurtTime=0;state.entityRenderState.deathTime=0;}}
}
