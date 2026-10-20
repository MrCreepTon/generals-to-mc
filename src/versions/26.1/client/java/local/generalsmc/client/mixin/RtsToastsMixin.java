package local.generalsmc.client.mixin;
import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public final class RtsToastsMixin {
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void generalsToasts(CallbackInfo ci){var s=Minecraft.getInstance().screen;if(NativeWorldView.controlling()||(s!=null&&s.getClass().getPackageName().equals("local.generalsmc.client")))ci.cancel();}
}
