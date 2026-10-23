package local.generalsmc.mixin;
import local.generalsmc.nativebridge.WorldEmp;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Mob.class)
public abstract class EmpMobMixin {
 @Inject(method="isEffectiveAi",at=@At("HEAD"),cancellable=true)
 private void generalsEmpStun(CallbackInfoReturnable<Boolean> result){if(WorldEmp.stunned((Mob)(Object)this))result.setReturnValue(false);}
}
