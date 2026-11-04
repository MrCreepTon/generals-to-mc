package local.generalsmc.client.mixin;

import local.generalsmc.nativebridge.NativeActorEntity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Minecraft.class)
abstract class NativeDrivingAttackMixin {
    private static boolean driving(){var p=Minecraft.getInstance().player;return p!=null&&p.getVehicle() instanceof NativeActorEntity;}
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void nativeFire(CallbackInfoReturnable<Boolean> result){if(driving())result.setReturnValue(false);}
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void nativeFireHeld(boolean held,CallbackInfo result){if(driving()){var mode=Minecraft.getInstance().gameMode;if(mode!=null)mode.stopDestroyBlock();result.cancel();}}
}
