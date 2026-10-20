package local.generalsmc.client.mixin;
import local.generalsmc.client.GeneralsClient;
import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Hud.class)
public class RtsHudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void hideVanilla(CallbackInfo ci) {
        if (NativeWorldView.controlling()) ci.cancel();
    }
}

