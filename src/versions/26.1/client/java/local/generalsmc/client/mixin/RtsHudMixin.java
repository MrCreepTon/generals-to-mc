package local.generalsmc.client.mixin;
import local.generalsmc.client.GeneralsClient;
import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public class RtsHudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void hideVanilla(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (NativeWorldView.controlling()) ci.cancel();
    }
    @Inject(method="extractCrosshair",at=@At("HEAD"),cancellable=true)
    private void vehicleCrosshair(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci){if(local.generalsmc.client.NativeDrivingHud.draw(graphics))ci.cancel();}
    @Inject(method="extractVehicleHealth",at=@At("HEAD"),cancellable=true)
    private void vehicleHealth(CallbackInfo ci){if(local.generalsmc.client.NativeDrivingHud.mounted())ci.cancel();}

}
