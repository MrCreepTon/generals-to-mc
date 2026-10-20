package local.generalsmc.client.mixin;
import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class RtsHandMixin {
    @Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true)
    private void nativeRtsHand(CallbackInfo ci){if(NativeWorldView.controlling())ci.cancel();}
}
