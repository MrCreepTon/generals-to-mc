package local.generalsmc.client.mixin;
import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
public class RtsHandMixin {
    @Inject(method="submitHandsWithItems",at=@At("HEAD"),cancellable=true)
    private void nativeRtsHand(CallbackInfo ci){if(NativeWorldView.controlling())ci.cancel();}
}
