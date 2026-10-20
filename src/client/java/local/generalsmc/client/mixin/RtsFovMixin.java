package local.generalsmc.client.mixin;
import local.generalsmc.client.GeneralsClient;
import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
@Mixin(GameRenderer.class)
public class RtsFovMixin {
    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void fixedFov(Camera camera, float partial, boolean useSetting, CallbackInfoReturnable<Float> ci) {
        if (NativeWorldView.controlling()) ci.setReturnValue(60f);
    }
    @Inject(method = {"bobView", "bobHurt"}, at = @At("HEAD"), cancellable = true)
    private void stableTacticalView(PoseStack pose,float partial,CallbackInfo ci){
        if(NativeWorldView.controlling())ci.cancel();
    }
}
